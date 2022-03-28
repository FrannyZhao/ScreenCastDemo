package com.franny.screencastdemo.media

interface ScreenRecordCallback {
    fun sendScreenRecordData(bytes: ByteArray)
}