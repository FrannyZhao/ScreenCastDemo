package com.franny.screencastdemo.network.tcp

interface TCPCallback {
    fun onConnect(ip: String?)
    fun onDisconnect()
    fun onFrame(data: ByteArray)
    fun processMoveAction(action: Int, x: Int, y: Int)
}