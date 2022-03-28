package com.franny.screencastdemo.network.tcp

import com.franny.screencastdemo.network.utils.ByteTools
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.Socket

class TCPClientThread constructor(
    private val ip: String
) : Thread() {
    private var inputStream: BufferedInputStream? = null
    private var outputStream: BufferedOutputStream? = null
    private var isRunning = false
    private var socket: Socket? = null

    private val tcpCallbacks = mutableListOf<TCPCallback>()

    init {
        Timber.d("init $this")
    }

    fun addTCPCallback(tcpCallback: TCPCallback) {
        tcpCallbacks.add(tcpCallback)
    }

    fun clearTCPCallback() {
        tcpCallbacks.clear()
    }

    private fun notifyAllOnConnect(ip: String?) {
        Timber.d("notifyAllOnConnect")
        tcpCallbacks.forEach {
            it.onConnect(ip)
        }
    }

    private fun notifyAllOnDisconnect() {
        tcpCallbacks.forEach {
            it.onDisconnect()
        }
    }

    private fun notifyAllProcessMoveAction(action: Int, x: Int, y: Int) {
        tcpCallbacks.forEach {
            it.processMoveAction(action, x, y)
        }
    }

    override fun run() {
        super.run()
        try {
            Timber.i("connecting " + ip + ":" + TCPConstant.PORT)
            socket = Socket(ip, TCPConstant.PORT)
            Timber.i("connect success " + ip + ":" + TCPConstant.PORT)
            inputStream = BufferedInputStream(socket?.getInputStream())
            outputStream = BufferedOutputStream(socket?.getOutputStream())
            notifyAllOnConnect(ip)
            isRunning = true
            while (isRunning) {
                val readSize = inputStream?.available() ?: -1
                var ret = 0
                if (readSize < 4) {
                    try {
                        sleep(10)
                    } catch (e: InterruptedException) {
                        Timber.e("connect error: ${e.stackTraceToString()}")
                    }
                    continue
                }
                val tmpArray = ByteArray(4)
                do {
                    ret += inputStream?.read(tmpArray, ret, 4 - ret) ?: 0
                } while (ret < 4 && isRunning)
                parseInput(tmpArray)
            }
        } catch (e: IOException) {
            Timber.e("connect error: ${e.stackTraceToString()}")
        } finally {
            notifyAllOnDisconnect()
            try {
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: IOException) {
                Timber.e("connect error: ${e.stackTraceToString()}")
            }
        }
    }

    private fun parseInput(data: ByteArray) {
        val size: Int = ByteTools.bytesToInt(data)
        val tmpArray = ByteArray(size)
        var ret = 0
        try {
            do {
                ret += inputStream?.read(tmpArray, ret, size - ret) ?: 0
            } while (ret < size)
            when (tmpArray[0]) {
                TCPMessageHeader.ACTION_STOP_CAST_CONTROL -> close()
                TCPMessageHeader.ACTION_SCREEN_MOTION -> {
                    val actionBytes = ByteArray(4)
                    val xBytes = ByteArray(4)
                    val yBytes = ByteArray(4)
                    System.arraycopy(tmpArray, 1, actionBytes, 0, 4)
                    System.arraycopy(tmpArray, 5, xBytes, 0, 4)
                    System.arraycopy(tmpArray, 9, yBytes, 0, 4)
                    val action: Int = ByteTools.bytesToInt(actionBytes)
                    val x: Int = ByteTools.bytesToInt(xBytes)
                    val y: Int = ByteTools.bytesToInt(yBytes)
                    notifyAllProcessMoveAction(action, x, y)
                }
                else -> {}
            }
        } catch (e: FileNotFoundException) {
            Timber.e("connect error: ${e.stackTraceToString()}")
        } catch (e: IOException) {
            Timber.e("connect error: ${e.stackTraceToString()}")
        } catch (e: Exception) {
            Timber.e("connect error: ${e.stackTraceToString()}")
        }
    }

    fun send(data: ByteArray) {
        Timber.i("tcp send data size ${data.size}")
        val content = ByteArray(data.size + 4)
        System.arraycopy(ByteTools.intToBytes(data.size), 0, content, 0, 4)
        System.arraycopy(data, 0, content, 4, data.size)
        Thread {
            try {
                outputStream?.write(content, 0, content.size)
                outputStream?.flush()
            } catch (e: Exception) {
                Timber.e("connect error: ${e.stackTraceToString()}")
                notifyAllOnDisconnect()
                try {
                    inputStream?.close()
                    outputStream?.close()
                    socket?.close()
                } catch (e: IOException) {
                    Timber.e("connect error: ${e.stackTraceToString()}")
                }
            }
        }.start()
    }

    fun close() {
        isRunning = false
    }
}