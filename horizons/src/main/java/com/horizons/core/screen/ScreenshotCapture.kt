package com.horizons.core.screen

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
import kotlin.math.max

/**
 * One-shot screen capture. Output is a JPEG (q=85) downscaled to ≤1024px on the
 * longest side to avoid NPU OOM when the image is passed to an on-device VLM.
 *
 * Lifecycle:
 *  1. Activity: `startActivityForResult(capture.prepareConsentIntent(), REQ)`
 *  2. Activity onActivityResult: `capture.onConsentResult(resultCode, data)`,
 *     then `ScreenCaptureService.start(ctx, resultCode, data)` (FGS up before
 *     MediaProjection is acquired — API 34+ mandate).
 *  3. Anywhere: `capture.captureToFile()` to grab one frame as JPEG.
 */
class ScreenshotCapture(private val context: Context) {

    private val projectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    @Volatile private var consentResultCode: Int = Activity.RESULT_CANCELED
    @Volatile private var consentData: Intent? = null

    fun prepareConsentIntent(): Intent = projectionManager.createScreenCaptureIntent()

    fun onConsentResult(resultCode: Int, data: Intent?): Boolean {
        consentResultCode = resultCode
        consentData = data
        return resultCode == Activity.RESULT_OK && data != null
    }

    /**
     * Grabs one frame, writes JPEG (q=85, ≤1024px longest side) to
     * filesDir/screenshots/snap_<epochMillis>.jpg, prunes older snapshots (keep latest 10),
     * returns the file.
     */
    suspend fun captureToFile(): Result<File> = withContext(Dispatchers.IO) {
        val data = consentData
        if (consentResultCode != Activity.RESULT_OK || data == null) {
            return@withContext Result.failure(IllegalStateException("No MediaProjection consent"))
        }

        val projection: MediaProjection = try {
            projectionManager.getMediaProjection(consentResultCode, data)
                ?: return@withContext Result.failure(IllegalStateException("getMediaProjection returned null"))
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

        val projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {}
        }
        projection.registerCallback(projectionCallback, handler)

        var virtualDisplay: VirtualDisplay? = null
        try {
            virtualDisplay = projection.createVirtualDisplay(
                "horizons-screen-ask",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler
            ) ?: return@withContext Result.failure(IllegalStateException("createVirtualDisplay returned null"))

            val bitmap = withTimeoutOrNull(3_000L) {
                awaitFirstFrame(reader, handler, width, height)
            } ?: return@withContext Result.failure(IllegalStateException("Timed out waiting for frame"))

            val scaled = downscale(bitmap, maxDim = 1024)
            bitmap.recycle()

            val outFile = writeJpeg(scaled)
            scaled.recycle()
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
        reader: ImageReader, handler: Handler, width: Int, height: Int
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

    private fun downscale(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return src
        val scale = maxDim.toFloat() / longest
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun writeJpeg(bitmap: Bitmap): File {
        val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "snap_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.flush()
        }
        return file
    }

    private fun pruneOldSnapshots(dir: File?, keep: Int) {
        if (dir == null || !dir.isDirectory) return
        val snaps = dir.listFiles { f -> f.isFile && f.name.startsWith("snap_") } ?: return
        if (snaps.size <= keep) return
        snaps.sortedByDescending { it.lastModified() }.drop(keep).forEach { runCatching { it.delete() } }
    }

    private companion object { const val TAG = "ScreenshotCapture" }
}
