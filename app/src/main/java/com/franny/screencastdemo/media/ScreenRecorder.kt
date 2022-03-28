package com.franny.screencastdemo.media

import android.content.Context
import android.media.projection.MediaProjection

class ScreenRecorder private constructor() {
    private var encoder: MediaEncoder = MediaEncoder()

    fun startRecord(context: Context, mediaProjection: MediaProjection) {
        encoder.setContext(context)
            .setMediaProjection(mediaProjection)
            .setVideoBit(BIT)
            .setVideoFPS(FPS)
        encoder.start()
    }

    fun setCallback(callback: ScreenRecordCallback) {
        encoder.setCallback(callback)
    }

    fun stopRecord() {
        encoder.stopScreen()
    }

    companion object {
        val INSTANCE = ScreenRecorder()
        private const val BIT = 3000000
        private const val FPS = 8
    }
}