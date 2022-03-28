package com.franny.screencastdemo.network.udp

import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UDPSendThread constructor(
    private var targetIP: String,
    private var data: ByteArray
) : Thread() {
    private var isRunning = false
    private var datagramSocket: DatagramSocket? = null

    fun stopSend() {
        isRunning = false
    }

    fun setTargetIPAndData(newIP: String, newData: ByteArray) {
        targetIP = newIP
        data = newData
    }

    init {
        try {
            datagramSocket = DatagramSocket()
        } catch (e: Exception) {
            Timber.w("init DatagramSocket fail: ${e.stackTraceToString()}")
        }
    }

    override fun run() {
        super.run()
        isRunning = true
        while (isRunning) {
            try {
                val local = InetAddress.getByName(targetIP)
                val datagramPacket = DatagramPacket(data, data.size, local, UDPConstant.PORT)
                datagramSocket?.send(datagramPacket)
                Timber.i("udp send to ip: " + targetIP + ", dataSize: " + data.size)
                sleep(UDPConstant.SEND_INTERVAL.toLong())
            } catch (e: Exception) {
                Timber.w("send failed: ${e.stackTraceToString()}")
            }
        }
    }
}