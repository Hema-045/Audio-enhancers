package com.example.ai_hearing_assistant.processors

import com.example.ai_hearing_assistant.dsp.Denoiser

class PassThroughProcessor : Denoiser {

    override fun initialize(
        sampleRate: Int,
        frameSize: Int
    ) {
        // Nothing to initialize.
    }

    override fun process(
        samples: ShortArray,
        length: Int
    ) {
        // Do absolutely nothing.
        // Audio passes through unchanged.
    }

    override fun release() {
        // Nothing to release.
    }
}