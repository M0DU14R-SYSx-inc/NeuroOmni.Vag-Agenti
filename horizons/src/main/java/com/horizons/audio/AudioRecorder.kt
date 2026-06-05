package com.horizons.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Push-to-talk PCM16 capture for Moonshine STT.
 *
 * Records mono 16 kHz PCM16 via [AudioRecord] using the
 * [MediaRecorder.AudioSource.VOICE_RECOGNITION] source, accumulates the audio
 * into a single [ShortArray], and hands it back on [stop]. The output shape
 * matches [com.horizons.model.MoonshineSttEngine.transcribe]'s
 * `pcm16: ShortArray, sampleRate: Int = 16000` contract.
 *
 * Thread-safety:
 *  - Public lifecycle methods ([start], [stop]) are guarded by a `@Synchronized`
 *    monitor on the recorder instance so concurrent callers cannot double-start
 *    or race a stop against a start.
 *  - The read loop runs on [Dispatchers.IO] inside a private [SupervisorJob]
 *    scope and signals termination via an [AtomicBoolean] flag.
 *  - The accumulator buffer is only mutated from the IO read loop; [stop]
 *    joins that job before snapshotting it, so no external locking is needed
 *    on the buffer itself.
 */
class AudioRecorder(private val context: Context) {

    private val running = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var readerJob: Job? = null
    private var scope: CoroutineScope? = null

    // Mutated only on the IO reader coroutine.
    private val accumulator = ArrayList<ShortArray>()
    private var accumulatedSize: Int = 0

    private var readBufferSize: Int = 0

    fun isRecording(): Boolean = running.get()

    @Synchronized
    suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        if (running.get()) {
            return@withContext Result.failure(IllegalStateException("AudioRecorder already started"))
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext Result.failure(
                SecurityException("RECORD_AUDIO permission not granted; cannot construct AudioRecord")
            )
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            return@withContext Result.failure(
                IllegalStateException("AudioRecord.getMinBufferSize returned $minBuf for ${SAMPLE_RATE}Hz mono PCM16")
            )
        }
        val bufferSizeBytes = minBuf * 2 // doubled per spec

        val rec = try {
            @Suppress("MissingPermission")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeBytes
            )
        } catch (t: Throwable) {
            return@withContext Result.failure(
                IllegalStateException("Failed to construct AudioRecord: ${t.message}", t)
            )
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            return@withContext Result.failure(
                IllegalStateException("AudioRecord failed to initialize (state=${rec.state})")
            )
        }

        accumulator.clear()
        accumulatedSize = 0
        readBufferSize = bufferSizeBytes / 2 // shorts per read

        try {
            rec.startRecording()
        } catch (t: Throwable) {
            runCatching { rec.release() }
            return@withContext Result.failure(
                IllegalStateException("AudioRecord.startRecording failed: ${t.message}", t)
            )
        }

        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            runCatching { rec.stop() }
            runCatching { rec.release() }
            return@withContext Result.failure(
                IllegalStateException("AudioRecord did not enter RECORDING state (got ${rec.recordingState})")
            )
        }

        recorder = rec
        running.set(true)

        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = ioScope
        readerJob = ioScope.launch {
            val buf = ShortArray(readBufferSize)
            try {
                while (running.get()) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        accumulator.add(buf.copyOf(n))
                        accumulatedSize += n
                    } else if (n < 0) {
                        Log.w(TAG, "AudioRecord.read returned error $n; stopping loop")
                        running.set(false)
                        break
                    }
                    // n == 0: spurious, just loop.
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Reader loop crashed", t)
                running.set(false)
            }
        }

        Result.success(Unit)
    }

    @Synchronized
    suspend fun stop(): Result<ShortArray> {
        if (!running.get() && recorder == null) {
            return Result.failure(IllegalStateException("AudioRecorder not started"))
        }

        running.set(false)
        readerJob?.join()
        readerJob = null
        scope = null

        val rec = recorder
        recorder = null

        return withContext(Dispatchers.IO) {
            try {
                if (rec != null) {
                    runCatching {
                        if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
                    }
                    runCatching { rec.release() }
                }

                val out = ShortArray(accumulatedSize)
                var offset = 0
                for (chunk in accumulator) {
                    System.arraycopy(chunk, 0, out, offset, chunk.size)
                    offset += chunk.size
                }
                accumulator.clear()
                accumulatedSize = 0
                Result.success(out)
            } catch (t: Throwable) {
                runCatching { rec?.release() }
                accumulator.clear()
                accumulatedSize = 0
                Result.failure(t)
            }
        }
    }

    private companion object {
        const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
