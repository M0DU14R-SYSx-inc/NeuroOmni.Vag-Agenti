package com.horizons.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * One-shot screen capture for screen-ask. Feeds the VLM (NexaVlmEngine.generateStream)
 * via the resulting file path.
 *
 * Lifecycle:
 *  1. Activity: `startActivityForResult(capture.prepareConsentIntent(), REQ)`
 *  2. Activity onActivityResult: `capture.onConsentResult(resultCode, data)`,
 *     then `ScreenCaptureService.start(ctx, resultCode, data)` (FGS up before
 *     MediaProjection is acquired — API 34+ mandate).
 *  3. Anywhere: `capture.captureToFile()` to grab one frame as PNG.
 */
class ScreenshotCapture(private val context: Context) {

    private val projectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    @Volatile private var consentResultCode: Int = Activity.RESULT_CANCELED
    @Volatile private var consentData: Intent? = null

    /** Intent the Activity launches via the consent ActivityResultLauncher. */
    fun prepareConsentIntent(): Intent = projectionManager.createScreenCaptureIntent()

    /** Store the consent for reuse. Returns true if granted. */
    fun onConsentResult(resultCode: Int, data: Intent?): Boolean {
        consentResultCode = resultCode
        consentData = data
        return resultCode == Activity.RESULT_OK && data != null
    }

    /**
     * Grabs one frame, writes PNG to filesDir/screenshots/snap_<epochMillis>.png,
     * prunes older snapshots (keep latest 10), returns the file.
     */
    suspend fun captureToFile(): Result<File> = withContext(Dispatchers.IO) {
        val data = consentData
        if (consentResultCode != Activity.RESULT_OK || data == null) {
            return@withContext Result.failure(IllegalStateException("No MediaProjection consent — call prepareConsentIntent()/onConsentResult() first"))
        }

        // FGS must be started by Activity before this. We still acquire here.
        val projection: MediaProjection = try {
            projectionManager.getMediaProjection(consentResultCode, data)
                ?: return@withContext Result.failure(IllegalStateException("MediaProjectionManager.getMediaProjection returned null"))
        } catch (t: Throwable) {
            return@withContext Result.failure(t)
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.currentWindowMetrics.bounds
        val width = bounds.width()
        val height = bounds.height()
        val density = context.resources.displayMetrics.densityDpi
            .takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val handlerThread = HandlerThread("ScreenshotCapture").apply { start() }
        val handler = Handler(handlerThread.looper)

        // Required on API 34+: register a callback before creating the VirtualDisplay.
        val projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() { /* no-op; we tear down explicitly below */ }
        }
        projection.registerCallback(projectionCallback, handler)

        var virtualDisplay: VirtualDisplay? = null
        try {
            virtualDisplay = projection.createVirtualDisplay(
                "horizons-screen-ask",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            ) ?: return@withContext Result.failure(IllegalStateException("createVirtualDisplay returned null"))

            val bitmap = withTimeoutOrNull(3_000L) {
                awaitFirstFrame(reader, handler, width, height)
            } ?: return@withContext Result.failure(IllegalStateException("Timed out waiting for frame"))

            val outFile = writePng(bitmap)
            bitmap.recycle()
            pruneOldSnapshots(outFile.parentFile, keep = 10)
            Result.success(outFile)
        } catch (t: Throwable) {
            Log.e(TAG, "captureToFile failed", t)
            Result.failure(t)
        } finally {
            runCatching { virtualDisplay?.release() }
            runCatching { reader.close() }
            runCatching { projection.unregisterCallback(projectionCallback) }
            runCatching { projection.stop() }
            handlerThread.quitSafely()
        }
    }

    private suspend fun awaitFirstFrame(
        reader: ImageReader,
        handler: Handler,
        width: Int,
        height: Int
    ): Bitmap = suspendCancellableCoroutine { cont ->
        val listener = object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(r: ImageReader) {
                val image = try { r.acquireLatestImage() } catch (t: Throwable) { null }
                if (image == null) return
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bmpWidth = width + rowPadding / pixelStride
                    val bitmap = Bitmap.createBitmap(bmpWidth, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    val cropped = if (bmpWidth != width) {
                        Bitmap.createBitmap(bitmap, 0, 0, width, height).also { bitmap.recycle() }
                    } else bitmap
                    r.setOnImageAvailableListener(null, null)
                    if (cont.isActive) cont.resume(cropped)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.cancel(t)
                } finally {
                    runCatching { image.close() }
                }
            }
        }
        reader.setOnImageAvailableListener(listener, handler)
        cont.invokeOnCancellation { runCatching { reader.setOnImageAvailableListener(null, null) } }
    }

    private fun writePng(bitmap: Bitmap): File {
        val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "snap_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return file
    }

    private fun pruneOldSnapshots(dir: File?, keep: Int) {
        if (dir == null || !dir.isDirectory) return
        val pngs = dir.listFiles { f -> f.isFile && f.name.startsWith("snap_") && f.name.endsWith(".png") }
            ?: return
        if (pngs.size <= keep) return
        pngs.sortedByDescending { it.lastModified() }
            .drop(keep)
            .forEach { runCatching { it.delete() } }
    }

    private companion object { const val TAG = "ScreenshotCapture" }
}
