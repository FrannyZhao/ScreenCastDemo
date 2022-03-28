package com.franny.screencastdemo.network.tcp

object TCPMessageHeader {
    const val HEAD_LENGTH = 8
    const val ACTION_STOP_CAST_CONTROL: Byte = 1
    const val ACTION_FRAME_DATA: Byte = 2
    const val ACTION_SCREEN_MOTION: Byte = 3
}