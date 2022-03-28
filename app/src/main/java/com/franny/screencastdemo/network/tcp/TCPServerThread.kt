package com.franny.screencastdemo.network.tcp

import com.franny.screencastdemo.network.utils.ByteTools
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.*

class TCPServerThread : Thread() {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var dataInputStream: BufferedInputStream? = null
    private var dataOutputStream: DataOutputStream? = null
    private var timer_size: Long = 0
    private var isRunning = false

    private val timer = Timer()
    private val timerTask: TimerTask = object : TimerTask() {
        override fun run() {
            if (isRunning && timer_size != 0L) {
                Timber.i("receive speed:" + timer_size / 1024 + "kb/s")
                timer_size = 0
            }
        }
    }

    private val tcpCallbacks = mutableSetOf<TCPCallback>()

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

    private fun notifyAllOnFrame(data: ByteArray) {
        tcpCallbacks.forEach {
            it.onFrame(data)
        }
    }

    fun close() {
        isRunning = false
    }

    override fun run() {
        super.run()
        try {
            if (serverSocket == null) {
                serverSocket = ServerSocket(TCPConstant.PORT)
                serverSocket?.soTimeout = TCPConstant.TIME_OUT
                serverSocket?.reuseAddress = true
            }
            clientSocket = serverSocket?.accept()
            clientSocket?.reuseAddress = true;

            timer.schedule(timerTask, 0, 1000)
            notifyAllOnConnect(clientSocket?.inetAddress?.hostAddress)
            dataInputStream = BufferedInputStream(clientSocket?.getInputStream())
            dataOutputStream = DataOutputStream(clientSocket?.getOutputStream())
            isRunning = true
            while (isRunning) {
                val readsize = dataInputStream?.available() ?: -1
                var ret = 0
                if (readsize < 4) {
                    try {
                        sleep(10)
                    } catch (e: InterruptedException) {
                        Timber.e("connect error: ${e.stackTraceToString()}")
                    }
                    continue
                }
                val tmpArray = ByteArray(4)
                do {
                    ret += dataInputStream?.read(tmpArray, ret, 4 - ret) ?: 0
                } while (ret < 4 && isRunning)
                parseTeacherMessage(tmpArray)
            }
        } catch (timeOutException: SocketTimeoutException) {
            Timber.e("connect timeout: ${timeOutException.stackTraceToString()}")
            notifyAllOnDisconnect()
        } catch (e: Exception) {
            Timber.e("connect error: ${e.stackTraceToString()}")
        } finally {
            notifyAllOnDisconnect()
            timer.cancel()
            try {
                dataInputStream?.close()
                dataOutputStream?.close()
                clientSocket?.shutdownOutput()
            } catch (e: Exception) {
                Timber.w("connect error: ${e.stackTraceToString()}")
            }
            try {
                clientSocket?.close()
                serverSocket?.close()
            } catch (e: Exception) {
                Timber.w("connect error: ${e.stackTraceToString()}")
            }
        }
    }

    /**
     * 数据解析
     *
     * @param data
     */
    private fun parseTeacherMessage(data: ByteArray) {
        if (!isRunning) {
            return
        }
        val size: Int = ByteTools.bytesToInt(data) //数据包大小
        timer_size += size.toLong()
        val tmpArray = ByteArray(size)
        var ret = 0
        try {
            do {
                ret += dataInputStream?.read(tmpArray, ret, size - ret) ?: 0
            } while (ret < size && isRunning)

            if (ret > 0) {
                val head = tmpArray[0]
                if (head == TCPMessageHeader.ACTION_STOP_CAST_CONTROL) {
                    close()
                } else if (head == TCPMessageHeader.ACTION_FRAME_DATA) {
                    val frameData = ByteArray(tmpArray.size - 1)
                    System.arraycopy(tmpArray, 1, frameData, 0, frameData.size)
                    notifyAllOnFrame(frameData)
                }
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
        if (clientSocket == null) {
            return
        }
        if (clientSocket?.isConnected == false) {
            return
        }
        val content = ByteArray(data.size + 4)
        System.arraycopy(ByteTools.intToBytes(data.size), 0, content, 0, 4)
        System.arraycopy(data, 0, content, 4, data.size)
        Thread {
            try {
                dataOutputStream?.write(content, 0, content.size)
                dataOutputStream?.flush()
                Timber.i("tcp send data size " + data.size)
            } catch (e: IOException) {
                Timber.e("connect error: ${e.stackTraceToString()}")
            }
        }.start()
    }
}