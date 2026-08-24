package com.jarvisquest.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Everything downstream (VAD, STT) is built around this sample rate/format. */
const val AUDIO_SAMPLE_RATE_HZ = 16_000
const val AUDIO_FRAME_MS = 20
const val AUDIO_FRAME_SAMPLES = AUDIO_SAMPLE_RATE_HZ * AUDIO_FRAME_MS / 1000 // 320 samples

sealed class AudioServiceError : Exception() {
    object PermissionDenied : AudioServiceError()
    object DeviceUnavailable : AudioServiceError()
    data class InitializationFailed(override val message: String) : AudioServiceError()
}

/**
 * Real microphone capture. Wraps [AudioRecord] directly (no third-party
 * audio dependency needed) and streams fixed-size 20 ms PCM16 mono frames,
 * which is the frame size [com.jarvisquest.app.audio.EnergyBasedVad] and
 * the STT layer both expect.
 *
 * This class does exactly one job: get real samples off the microphone.
 * It has no opinion about speech vs. silence — that's the VAD's job — and
 * no opinion about permissions being pre-granted — the caller must request
 * RECORD_AUDIO before calling [captureFrames].
 */
class AudioService(private val context: Context) {

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Cold [Flow] of 20 ms PCM16 mono frames at [AUDIO_SAMPLE_RATE_HZ].
     * Collecting it starts the microphone; cancelling collection stops and
     * releases it. Throws (via flow exception) an [AudioServiceError] if
     * the permission is missing or the device has no usable input.
     */
    fun captureFrames(): Flow<ShortArray> = callbackFlow {
        if (!hasRecordAudioPermission()) {
            close(AudioServiceError.PermissionDenied)
            return@callbackFlow
        }

        val minBufferBytes = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferBytes == AudioRecord.ERROR || minBufferBytes == AudioRecord.ERROR_BAD_VALUE) {
            close(AudioServiceError.DeviceUnavailable)
            return@callbackFlow
        }

        // A few frames of headroom above the OS minimum reduces the chance
        // of overruns when the collector briefly falls behind (e.g. while
        // the UI thread is composing a recomposition).
        val bufferBytes = minBufferBytes.coerceAtLeast(AUDIO_FRAME_SAMPLES * 2 * 4)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                AUDIO_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
        } catch (e: SecurityException) {
            close(AudioServiceError.PermissionDenied)
            return@callbackFlow
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            close(AudioServiceError.InitializationFailed("AudioRecord failed to initialize (state=${recorder.state})"))
            return@callbackFlow
        }

        recorder.startRecording()
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.release()
            close(AudioServiceError.InitializationFailed("AudioRecord did not enter RECORDING state"))
            return@callbackFlow
        }

        val readThread = Thread({
            val frame = ShortArray(AUDIO_FRAME_SAMPLES)
            while (!isClosedForSend) {
                val read = recorder.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) {
                    // Negative return values are AudioRecord.ERROR_* codes.
                    continue
                }
                val toSend = if (read == frame.size) frame.copyOf() else frame.copyOf(read)
                trySend(toSend)
            }
        }, "AudioService-Capture")
        readThread.priority = Thread.MAX_PRIORITY
        readThread.start()

        awaitClose {
            try {
                recorder.stop()
            } catch (_: IllegalStateException) {
                // Already stopped — fine, we're tearing down anyway.
            }
            recorder.release()
        }
    }
}
