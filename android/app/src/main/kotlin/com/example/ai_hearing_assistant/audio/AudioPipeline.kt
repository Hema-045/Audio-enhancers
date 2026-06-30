package com.example.ai_hearing_assistant.audio

import com.example.ai_hearing_assistant.dsp.Denoiser
import com.example.ai_hearing_assistant.processors.PassThroughProcessor

class AudioPipeline(
    sampleRate: Int,
    frameSize: Int
) {

    private val denoiser: Denoiser = PassThroughProcessor()

    init {
        denoiser.initialize(sampleRate, frameSize)
    }

    fun process(
        samples: ShortArray,
        length: Int
    ) {
        denoiser.process(samples, length)
    }

    fun release() {
        denoiser.release()
    }
}