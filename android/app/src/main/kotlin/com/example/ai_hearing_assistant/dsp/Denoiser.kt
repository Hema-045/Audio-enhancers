package com.example.ai_hearing_assistant.dsp

interface Denoiser {

    fun initialize(
        sampleRate: Int,
        frameSize: Int
    )

    fun process(
        samples: ShortArray,
        length: Int
    )

    fun release()
}