package com.horizons.model

import android.content.Context
import java.io.File

/**
 * Fetches the sherpa-onnx packaging of Kokoro v1.0 multi-lang (53 voices).
 *
 * Why the sherpa package and not onnx-community per-voice .bin files:
 * sherpa-onnx's OfflineTts does the phonemization itself (espeak-ng data
 * ships inside the archive), which is the piece the previous engine never
 * had — speak() was a stub waiting on an espeak-ng JNI port. sherpa's
 * voices.bin holds ALL voices in one file; a voice is selected at
 * generate() time by integer speaker id, so switching is instant.
 *
 * Archive contents: model.onnx, voices.bin, tokens.txt, espeak-ng-data/,
 * lexicon-us-en.txt, lexicon-zh.txt, dict/.
 */
object KokoroDownloader {
    const val MODEL_DIR_NAME = "kokoro-sherpa-v1_0"
    const val DEFAULT_VOICE = "am_adam"
    private const val ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
            "kokoro-multi-lang-v1_0.tar.bz2" // ~349 MB

    /** Kokoro v1.0 voices in sherpa speaker-id order (alphabetical — the
     *  list index IS the sid passed to OfflineTts.generate). Verified
     *  against the sherpa docs: am_adam=11, zf_xiaobei=45, zm_yunjian=49.
     *  Naming: <lang><gender>_<name>; a=American, b=British, e=Spanish,
     *  f=French, h=Hindi, i=Italian, j=Japanese, p=Portuguese, z=Mandarin. */
    val ALL_VOICES = listOf(
        // 0-10: American female
        "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica",
        "af_kore", "af_nicole", "af_nova", "af_river", "af_sarah", "af_sky",
        // 11-19: American male
        "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam",
        "am_michael", "am_onyx", "am_puck", "am_santa",
        // 20-27: British
        "bf_alice", "bf_emma", "bf_isabella", "bf_lily",
        "bm_daniel", "bm_fable", "bm_george", "bm_lewis",
        // 28-30: Spanish, French
        "ef_dora", "em_alex", "ff_siwis",
        // 31-36: Hindi, Italian
        "hf_alpha", "hf_beta", "hm_omega", "hm_psi", "if_sara", "im_nicola",
        // 37-44: Japanese, Portuguese
        "jf_alpha", "jf_gongitsune", "jf_nezumi", "jf_tebukuro", "jm_kumo",
        "pf_dora", "pm_alex", "pm_santa",
        // 45-52: Mandarin
        "zf_xiaobei", "zf_xiaoni", "zf_xiaoxiao", "zf_xiaoyi",
        "zm_yunjian", "zm_yunxi", "zm_yunxia", "zm_yunyang",
    )

    /** Speaker id for OfflineTts.generate(). Unknown names fall back to
     *  DEFAULT_VOICE rather than throwing — a stale persisted pick from
     *  the old 55-voice list must not brick TTS. */
    fun sidOf(voice: String): Int =
        ALL_VOICES.indexOf(voice).takeIf { it >= 0 }
            ?: ALL_VOICES.indexOf(DEFAULT_VOICE)

    data class Progress(val fileIndex: Int, val fileCount: Int, val currentFile: String, val fraction: Float?)

    suspend fun download(
        context: Context, hfToken: String? = null, onProgress: (Progress) -> Unit = {}
    ): Result<File> =
        VoiceModelFetcher.fetchAndExtract(context, ARCHIVE_URL, MODEL_DIR_NAME) { p ->
            onProgress(Progress(1, 1, "${p.phase}: ${p.detail}", p.fraction))
        }

    fun installedDir(context: Context): File? {
        val dir = File(VoiceModelFetcher.modelsRoot(context), MODEL_DIR_NAME)
        val complete = File(dir, "model.onnx").isFile &&
            File(dir, "voices.bin").isFile &&
            File(dir, "tokens.txt").isFile &&
            File(dir, "espeak-ng-data").isDirectory
        return if (complete) dir else null
    }
}
