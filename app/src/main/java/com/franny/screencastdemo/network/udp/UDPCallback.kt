package com.franny.screencastdemo.network.udp

interface UDPCallback {
    fun onReceive(ip: String, data: ByteArray)
}