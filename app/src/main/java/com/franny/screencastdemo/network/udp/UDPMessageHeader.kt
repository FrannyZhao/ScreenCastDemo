package com.franny.screencastdemo.network.udp

object UDPMessageHeader {
    const val ACTION_REMOTE_DEVICE_IP: Byte = 1
    const val ACTION_REQUEST_CAST_CONTROL: Byte = 2
    const val ACTION_ACCEPT_CAST_CONTROL: Byte = 3
    const val ACTION_STOP_CAST_CONTROL: Byte = 4
}