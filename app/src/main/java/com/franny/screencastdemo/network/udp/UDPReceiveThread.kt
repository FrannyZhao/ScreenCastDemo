package com.franny.screencastdemo.network.udp

import android.text.TextUtils
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket

class UDPReceiveThread constructor(
    private val nativeIP: String,
    private val udpCallback: UDPCallback?
): Thread() {
    private var isRunning = false
    private var datagramSocket: DatagramSocket? = null
    private val data = ByteArray(1024)
    private val datagramPacket = DatagramPacket(data, data.size)

    init {
        try {
            datagramSocket = DatagramSocket(UDPConstant.PORT)
            datagramSocket?.reuseAddress = true
        } catch (e: Exception) {
            Timber.w("init DatagramSocket fail: ${e.stackTraceToString()}")
        }
    }

    fun stopReceive() {
        Timber.i("stop receive data")
        isRunning = false
        datagramSocket?.disconnect()
        datagramSocket?.close()
    }

    override fun run() {
        super.run()
        isRunning = true
        try {
            Timber.i("start receive data")
            while (isRunning) {
                datagramSocket!!.receive(datagramPacket)
                val remoteIP = datagramPacket.address.toString().substring(1)
                if (!TextUtils.isEmpty(nativeIP)) {
                    if (nativeIP == remoteIP) {
                        // 若udp包的ip地址是本机的ip地址的话，丢掉这个包(不处理)
                        continue
                    }
                }
                Timber.i("receive data from ip: $remoteIP")
                val length = datagramPacket.length
                val receiveData = ByteArray(length)
                System.arraycopy(datagramPacket.data, 0, receiveData, 0, length)
                udpCallback?.onReceive(remoteIP, receiveData)
            }
        } catch (e: Exception) {
            Timber.w("receive failed: ${e.stackTraceToString()}")
        } finally {
            if (datagramSocket != null) {
                datagramSocket!!.disconnect()
                datagramSocket!!.close()
            }
        }
    }
}