package com.example.ai_hearing_assistant.dsp

import android.util.Log
import com.audx.android.Audx

class AudxProcessor {

    companion object {
        private const val TAG = "AudxProcessor"
    }

    private val audx = Audx.Builder()
        .inputRate(16000)
        .resampleQuality(Audx.AUDX_RESAMPLER_QUALITY_VOIP)
        .build()

    /**
     * Process one frame of audio.
     *
     * @param input PCM16 input samples
     * @param output PCM16 processed samples
     */
    fun process(input: ShortArray, output: ShortArray) {
        try {
            audx.process(input, output) { vadProbability ->
                Log.d(TAG, "VAD = $vadProbability")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudX processing failed", e)

            // If processing fails, just pass the original audio through
            System.arraycopy(input, 0, output, 0, input.size)
        }
    }

    /**
     * Release native resources.
     */
    fun release() {
        audx.close()
    }
}