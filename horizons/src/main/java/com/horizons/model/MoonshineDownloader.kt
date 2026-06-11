package com.horizons.model

import android.content.Context
import java.io.File

/**
 * Fetches the sherpa-onnx packaging of Moonshine base (int8, English).
 *
 * Why the sherpa package and not onnx-community: the hand-rolled ORT
 * decode loop against onnx-community's merged decoder never worked on
 * device (fake empty past_key_values, name-guessing, no KV cache) and
 * the int8 variant crashed ORT-Android's CPU EP at load (ConvInteger
 * ORT_NOT_IMPLEMENTED). sherpa-onnx ships its own full ORT build where
 * int8 works, plus a correct cached/uncached decode implementation.
 *
 * Archive contents: preprocess.onnx, encode.int8.onnx,
 * uncached_decode.int8.onnx, cached_decode.int8.onnx, tokens.txt.
 */
object MoonshineDownloader {
    const val MODEL_DIR_NAME = "moonshine-sherpa"
    private const val ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-moonshine-base-en-int8.tar.bz2" // ~250 MB

    data class Progress(val fileIndex: Int, val fileCount: Int, val currentFile: String, val fraction: Float?)

    suspend fun download(
        context: Context, hfToken: String? = null, onProgress: (Progress) -> Unit = {}
    ): Result<File> =
        VoiceModelFetcher.fetchAndExtract(context, ARCHIVE_URL, MODEL_DIR_NAME) { p ->
            onProgress(Progress(1, 1, "${p.phase}: ${p.detail}", p.fraction))
        }

    /** Picks "name.int8.onnx" if present, falling back to "name.onnx" —
     *  insulates the engine from int8-vs-fp32 archive naming. */
    fun pickOnnx(dir: File, base: String): File {
        val int8 = File(dir, "$base.int8.onnx")
        return if (int8.isFile) int8 else File(dir, "$base.onnx")
    }

    fun installedDir(context: Context): File? {
        val dir = File(VoiceModelFetcher.modelsRoot(context), MODEL_DIR_NAME)
        val complete = File(dir, "tokens.txt").isFile &&
            pickOnnx(dir, "preprocess").isFile &&
            pickOnnx(dir, "encode").isFile &&
            pickOnnx(dir, "uncached_decode").isFile &&
            pickOnnx(dir, "cached_decode").isFile
        return if (complete) dir else null
    }
}
