package com.horizons.model

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Moonshine on-device STT using ONNX Runtime Android.
 *
 * Model: onnx-community/moonshine-base-ONNX (int8). Encoder takes raw float32
 * audio @ 16kHz directly; decoder is a greedy seq2seq with input_ids +
 * encoder_hidden_states.
 *
 * EP: CPU only — addCpu() is FORCED EXCLUSION to keep ORT from grabbing
 * NNAPI/HTP which would steal NPU from Nexa OmniNeural-4B.
 */
class MoonshineSttEngine(
    private val context: Context,
    private val modelDir: String
) {
    private var env: OrtEnvironment? = null
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null

    // Detokenizer state
    private var idToToken: Array<String?>? = null
    private var bosId: Int = 1
    private var eosId: Int = 2

    val isLoaded: Boolean get() = encoder != null && decoder != null

    suspend fun load() = withContext(Dispatchers.Default) {
        // FP32 variants — Snapdragon 8 Elite handles them fine (~237 MB total
        // download, ~108 MB in-memory). The int8 variants use ConvInteger ops
        // that ORT-Android's CPU EP doesn't implement (ORT_NOT_IMPLEMENTED at
        // load time). FP32 uses standard Conv, broadly supported.
        val encoderFile = File(modelDir, "onnx/encoder_model.onnx")
        val decoderFile = File(modelDir, "onnx/decoder_model_merged.onnx")
        require(encoderFile.isFile) { "Moonshine encoder missing at $encoderFile" }
        require(decoderFile.isFile) { "Moonshine decoder missing at $decoderFile" }

        env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            // ORT-Android 1.22.0: addCpu() has no boolean arg. CPU EP is
            // registered by default; we keep it explicit so the FORCED
            // EXCLUSION discipline (don't addNnapi — steals NPU from Nexa)
            // is intentional, not accidental.
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
            setMemoryPatternOptimization(true)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        encoder = env!!.createSession(encoderFile.absolutePath, opts)
        decoder = env!!.createSession(decoderFile.absolutePath, opts)

        loadTokenizer()

        Log.i(TAG, "Moonshine loaded: encoder=${encoderFile.length()}B decoder=${decoderFile.length()}B " +
            "encIn=${encoder!!.inputInfo.keys} encOut=${encoder!!.outputInfo.keys} " +
            "decIn=${decoder!!.inputInfo.keys} decOut=${decoder!!.outputInfo.keys}")
    }

    private fun loadTokenizer() {
        val tokenizerFile = File(modelDir, "tokenizer.json")
        val cfgFile = File(modelDir, "tokenizer_config.json")
        if (!tokenizerFile.isFile) {
            Log.w(TAG, "tokenizer.json missing — detokenization will return raw ids")
            return
        }
        val root = Json.parseToJsonElement(tokenizerFile.readText()).jsonObject
        val model = root["model"]?.jsonObject
        val vocabEl = model?.get("vocab")

        val pairs = mutableListOf<Pair<String, Int>>()
        if (vocabEl is JsonObject) {
            // BPE: { "tok": id }
            for ((tok, idEl) in vocabEl) {
                val id = idEl.jsonPrimitive.intOrNull ?: continue
                pairs += tok to id
            }
        } else if (vocabEl != null) {
            // Unigram: [["tok", score], ...]
            vocabEl.jsonArray.forEachIndexed { i, entry ->
                val tok = entry.jsonArray[0].jsonPrimitive.contentOrNull ?: return@forEachIndexed
                pairs += tok to i
            }
        }
        // Also pull added_tokens
        root["added_tokens"]?.jsonArray?.forEach { added ->
            val id = added.jsonObject["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
            val content = added.jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            pairs += content to id
        }

        val maxId = (pairs.maxOfOrNull { it.second } ?: -1) + 1
        if (maxId <= 0) return
        val arr = arrayOfNulls<String>(maxId)
        for ((tok, id) in pairs) if (id in 0 until maxId) arr[id] = tok
        idToToken = arr

        if (cfgFile.isFile) {
            runCatching {
                val cfg = Json.parseToJsonElement(cfgFile.readText()).jsonObject
                cfg["bos_token_id"]?.jsonPrimitive?.intOrNull?.let { bosId = it }
                cfg["eos_token_id"]?.jsonPrimitive?.intOrNull?.let { eosId = it }
                cfg["decoder_start_token_id"]?.jsonPrimitive?.intOrNull?.let { bosId = it }
            }
        }
        // generation_config.json overrides
        val genFile = File(modelDir, "generation_config.json")
        if (genFile.isFile) {
            runCatching {
                val gen = Json.parseToJsonElement(genFile.readText()).jsonObject
                gen["bos_token_id"]?.jsonPrimitive?.intOrNull?.let { bosId = it }
                gen["eos_token_id"]?.jsonPrimitive?.intOrNull?.let { eosId = it }
                gen["decoder_start_token_id"]?.jsonPrimitive?.intOrNull?.let { bosId = it }
            }
        }
    }

    suspend fun transcribe(pcm16: ShortArray, sampleRate: Int = 16000): String =
        withContext(Dispatchers.Default) {
            if (!isLoaded) return@withContext "[moonshine error: not loaded]"
            try {
                val enc = encoder!!
                val dec = decoder!!
                val ortEnv = env!!

                // 1. PCM16 -> float32 [-1,1]
                val audio = FloatArray(pcm16.size) { pcm16[it].toFloat() / 32768f }

                // 2. Encoder forward
                val encInName = enc.inputInfo.keys.first()
                val encOutName = enc.outputInfo.keys.first()
                val audioShape = longArrayOf(1L, audio.size.toLong())

                var hiddenFloats: FloatArray = FloatArray(0)
                var hiddenShape: LongArray = LongArray(0)
                OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(audio), audioShape).use { audioTensor ->
                    enc.run(mapOf(encInName to audioTensor)).use { encResult ->
                        val encOut = encResult.get(0) as OnnxTensor
                        hiddenShape = encOut.info.shape
                        // flatten
                        val buf = encOut.floatBuffer
                        hiddenFloats = FloatArray(buf.remaining()).also { buf.get(it) }
                    }
                }

                // 3. Identify decoder input names
                val decInputs = dec.inputInfo.keys
                val idsName = decInputs.firstOrNull { it.contains("input_ids") } ?: "input_ids"
                val hiddenName = decInputs.firstOrNull {
                    it.contains("encoder_hidden_states") || it.contains("encoder_outputs")
                } ?: "encoder_hidden_states"
                val useCacheName = decInputs.firstOrNull { it == "use_cache_branch" }
                val decOutName = dec.outputInfo.keys.firstOrNull { it.contains("logits") }
                    ?: dec.outputInfo.keys.first()

                // 4. Greedy decode
                val tokens = mutableListOf<Int>(bosId)
                val maxLen = 256
                val hiddenTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(hiddenFloats), hiddenShape)
                try {
                    while (tokens.size < maxLen) {
                        // List<Int> has no toLongArray(); map to Long explicitly.
                        val seq = LongArray(tokens.size) { tokens[it].toLong() }
                        val idsBuf = LongBuffer.wrap(seq)
                        val idsShape = longArrayOf(1L, seq.size.toLong())
                        OnnxTensor.createTensor(ortEnv, idsBuf, idsShape).use { idsTensor ->
                            val feeds = HashMap<String, OnnxTensor>()
                            feeds[idsName] = idsTensor
                            feeds[hiddenName] = hiddenTensor
                            val extraTensors = mutableListOf<OnnxTensor>()
                            val cacheTensor: OnnxTensor? = if (useCacheName != null) {
                                val bb = java.nio.ByteBuffer.allocateDirect(1)
                                    .order(java.nio.ByteOrder.nativeOrder())
                                bb.put(0, 0.toByte())
                                OnnxTensor.createTensor(
                                    ortEnv, bb, longArrayOf(1L),
                                    ai.onnxruntime.OnnxJavaType.BOOL
                                ).also { feeds[useCacheName] = it }
                            } else null
                            // Merged decoder needs past_key_values inputs even when use_cache_branch=false.
                            // Feed empty float tensors of correct rank-4 shape [batch, heads, 0, head_dim].
                            for (name in decInputs) {
                                if (name in feeds) continue
                                if (!name.startsWith("past_key_values")) continue
                                val emptyShape = longArrayOf(1L, 1L, 0L, 1L)
                                val empty = OnnxTensor.createTensor(
                                    ortEnv, FloatBuffer.allocate(0), emptyShape
                                )
                                feeds[name] = empty
                                extraTensors += empty
                            }
                            try {
                                dec.run(feeds).use { decResult ->
                                val logitsTensor = decResult.get(decOutName).get() as OnnxTensor
                                val shape = logitsTensor.info.shape // [1, T, V]
                                val vocab = shape[shape.size - 1].toInt()
                                val t = shape[shape.size - 2].toInt()
                                val flat = FloatArray(logitsTensor.floatBuffer.remaining())
                                    .also { logitsTensor.floatBuffer.get(it) }
                                // last-timestep argmax
                                val base = (t - 1) * vocab
                                var bestId = 0
                                var bestV = Float.NEGATIVE_INFINITY
                                for (i in 0 until vocab) {
                                    val v = flat[base + i]
                                    if (v > bestV) { bestV = v; bestId = i }
                                }
                                tokens += bestId
                                }
                            } finally {
                                cacheTensor?.close()
                                extraTensors.forEach { runCatching { it.close() } }
                            }
                        }
                        if (tokens.last() == eosId) break
                    }
                } finally {
                    hiddenTensor.close()
                }

                // 5. Detokenize (drop BOS, stop at EOS)
                val out = tokens.drop(1).takeWhile { it != eosId }
                return@withContext detokenize(out)
            } catch (e: Throwable) {
                Log.e(TAG, "transcribe failed", e)
                return@withContext "[moonshine error: ${e.javaClass.simpleName}: ${e.message}]"
            }
        }

    private fun detokenize(ids: List<Int>): String {
        val table = idToToken ?: return ids.joinToString(",")
        val sb = StringBuilder()
        for (id in ids) {
            // table is Array<String?>?, table[id] is String? (nullable).
            // Operator precedence: `if/else` binds tighter than `?:`, so the
            // original `if (...) table[id] else null ?: continue` ended up as
            // `if (...) table[id] else (null ?: continue)` which left tok as
            // String? and broke .startsWith calls below. Pull the null-check
            // out into its own statement.
            val raw = if (id in table.indices) table[id] else null
            val tok: String = raw ?: continue
            // Skip special tokens like <s>, </s>, <pad>, <|...|>
            if (tok.startsWith("<") && tok.endsWith(">")) continue
            if (tok.startsWith("<|") && tok.endsWith("|>")) continue
            // SentencePiece-style space marker
            val piece = tok
                .replace("▁", " ")  // ▁ -> space (SentencePiece)
                .replace("Ġ", " ")  // Ġ -> space (GPT-2 BPE)
                .replace("Ċ", "\n") // Ċ -> newline
            sb.append(piece)
        }
        return sb.toString().trim()
    }

    fun release() {
        runCatching { encoder?.close() }
        runCatching { decoder?.close() }
        encoder = null; decoder = null
        idToToken = null
    }

    private companion object { const val TAG = "MoonshineStt" }
}
