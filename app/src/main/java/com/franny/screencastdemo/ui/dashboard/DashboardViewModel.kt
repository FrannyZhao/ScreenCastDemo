package com.franny.screencastdemo.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.franny.screencastdemo.media.ScreenRecordCallback
import com.franny.screencastdemo.media.ScreenRecorder
import com.franny.screencastdemo.network.tcp.TCPCallback
import com.franny.screencastdemo.network.tcp.TCPClientThread
import com.franny.screencastdemo.network.tcp.TCPMessageHeader
import com.franny.screencastdemo.network.tcp.TCPServerThread
import com.franny.screencastdemo.network.udp.UDPCallback
import com.franny.screencastdemo.network.udp.UDPConstant
import com.franny.screencastdemo.network.udp.UDPMessageHeader
import com.franny.screencastdemo.network.udp.UDPReceiveThread
import com.franny.screencastdemo.network.udp.UDPSendThread
import com.franny.screencastdemo.network.utils.ByteTools
import com.franny.screencastdemo.network.utils.getLocalIPAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class DashboardViewModel : ViewModel() {
    private val _localIP = MutableLiveData<String?>()
    val localIP: LiveData<String?> = _localIP

    private val _remoteDeviceIPMap = mutableMapOf<String, Long>()
    private val _remoteDeviceIPs = MutableLiveData(mutableSetOf<String>())
    val remoteDeviceIPs: LiveData<MutableSet<String>> = _remoteDeviceIPs

    private val _log = MutableLiveData<String>()
    val log: LiveData<String> = _log

    private val _connectedIP = MutableLiveData<String?>()
    val connectedIP: LiveData<String?> = _connectedIP

    var isSender = false

    private var udpSendThread: UDPSendThread? = null
    private var udpReceiveThread: UDPReceiveThread? = null

    private var tcpClientThread: TCPClientThread? = null
    var tcpServerThread: TCPServerThread? = null

    var localScreenWidth: Int = 0
    var localScreenHeight: Int = 0
    var remoteScreenWidth: Int = 0
    var remoteScreenHeight: Int = 0
    var screenCastCallback: TCPCallback? = null

    private val udpCallback = object : UDPCallback {
        override fun onReceive(ip: String, data: ByteArray) {
            val cmdType = data[0]
            Timber.i("cmd type: $cmdType, from: $ip")
            when (cmdType) {
                UDPMessageHeader.ACTION_REMOTE_DEVICE_IP -> {
                    _remoteDeviceIPMap[ip] = System.currentTimeMillis()
                    _remoteDeviceIPs.postValue(_remoteDeviceIPMap.keys)
                    _log.postValue("udp: receive remote device ip from $ip")
                }
                UDPMessageHeader.ACTION_REQUEST_CAST_CONTROL -> {
                    isSender = false

                    val remoteScreenWidthBytes = ByteArray(4)
                    System.arraycopy(data, 1, remoteScreenWidthBytes, 0, 4)
                    remoteScreenWidth = ByteTools.bytesToInt(remoteScreenWidthBytes)
                    val remoteScreenHeightBytes = ByteArray(4)
                    System.arraycopy(data, 5, remoteScreenHeightBytes, 0, 4)
                    remoteScreenHeight = ByteTools.bytesToInt(remoteScreenHeightBytes)

                    if (tcpServerThread == null) {
                        tcpServerThread = TCPServerThread()
                    }
                    tcpServerThread?.addTCPCallback(tcpCallback)
                    tcpServerThread?.start()

                    val head = byteArrayOf(UDPMessageHeader.ACTION_ACCEPT_CAST_CONTROL)
                    val localScreenWidthBytes = ByteTools.intToBytes(localScreenWidth)
                    val localScreenHeightBytes = ByteTools.intToBytes(localScreenHeight)
                    val newData = ByteArray(head.size + localScreenWidthBytes.size + localScreenHeightBytes.size)
                    System.arraycopy(head, 0, newData, 0, head.size)
                    System.arraycopy(localScreenWidthBytes, 0, newData, head.size, localScreenHeightBytes.size)
                    System.arraycopy(localScreenHeightBytes, 0, newData, head.size + localScreenWidthBytes.size, localScreenHeightBytes.size)

                    udpSendThread?.setTargetIPAndData(ip, newData)
                    _log.postValue("udp: receive ACTION_REQUEST_CAST_CONTROL from $ip, remoteScreen ($remoteScreenWidth, $remoteScreenHeight)")
                }
                UDPMessageHeader.ACTION_ACCEPT_CAST_CONTROL -> {
                    val remoteScreenWidthBytes = ByteArray(4)
                    System.arraycopy(data, 1, remoteScreenWidthBytes, 0, 4)
                    remoteScreenWidth = ByteTools.bytesToInt(remoteScreenWidthBytes)
                    val remoteScreenHeightBytes = ByteArray(4)
                    System.arraycopy(data, 5, remoteScreenHeightBytes, 0, 4)
                    remoteScreenHeight = ByteTools.bytesToInt(remoteScreenHeightBytes)

                    tcpClientThread = TCPClientThread(ip)
                    tcpClientThread?.addTCPCallback(tcpCallback)
                    tcpClientThread?.start()
                    _log.postValue("udp: receive ACTION_ACCEPT_CAST_CONTROL from $ip, remoteScreen ($remoteScreenWidth, $remoteScreenHeight)")
                }
                UDPMessageHeader.ACTION_STOP_CAST_CONTROL -> {
                    tcpClientThread?.close()
                    tcpClientThread = null
                    tcpServerThread?.close()
                    tcpServerThread = null
                    _log.postValue("udp: receive ACTION_STOP_CAST_CONTROL from $ip")
                }
            }
        }
    }

    val screenRecordCallback = object : ScreenRecordCallback {
        override fun sendScreenRecordData(frameData: ByteArray) {
            val head = byteArrayOf(TCPMessageHeader.ACTION_FRAME_DATA)
            val newData = ByteArray(frameData.size + head.size)
            System.arraycopy(head, 0, newData, 0, head.size)
            System.arraycopy(frameData, 0, newData, head.size, frameData.size)
            tcpClientThread?.send(newData)
        }
    }

    private val tcpCallback = object : TCPCallback {
        override fun onConnect(ip: String?) {
            ip?.let {
                Timber.d("connect ip $ip")
                _connectedIP.postValue(ip)
                udpSendThread?.stopSend()
                udpSendThread = null
            }
            screenCastCallback?.onConnect(ip)
            _log.postValue("tcp: connect ip $ip")
        }

        override fun onDisconnect() {
            Timber.d("disconnect")
            _connectedIP.postValue(null)
            ScreenRecorder.INSTANCE.stopRecord()
            tcpClientThread?.close()
            tcpClientThread = null
            tcpServerThread?.close()
            tcpServerThread = null
            if (udpSendThread == null) {
                udpSendThread = UDPSendThread(
                    UDPConstant.BORADCAST_IP,
                    byteArrayOf(UDPMessageHeader.ACTION_REMOTE_DEVICE_IP)
                )
                udpSendThread?.start()
            }
            screenCastCallback?.onDisconnect()
            _log.postValue("tcp: disconnect")
        }

        override fun onFrame(data: ByteArray) {
            screenCastCallback?.onFrame(data)
        }

        override fun processMoveAction(action: Int, x: Int, y: Int) {
            screenCastCallback?.processMoveAction(action, x, y)
        }
    }

    fun requestCastAndControl(targetIP: String) {
        isSender = true
        val head = byteArrayOf(UDPMessageHeader.ACTION_REQUEST_CAST_CONTROL)
        val localScreenWidthBytes = ByteTools.intToBytes(localScreenWidth)
        val localScreenHeightBytes = ByteTools.intToBytes(localScreenHeight)
        val data = ByteArray(head.size + localScreenWidthBytes.size + localScreenHeightBytes.size)
        System.arraycopy(head, 0, data, 0, head.size)
        System.arraycopy(localScreenWidthBytes, 0, data, head.size, localScreenHeightBytes.size)
        System.arraycopy(localScreenHeightBytes, 0, data, head.size + localScreenWidthBytes.size, localScreenHeightBytes.size)
        udpSendThread?.setTargetIPAndData(targetIP, data)
    }

    fun stopCastAndControl() {
        val data = byteArrayOf(TCPMessageHeader.ACTION_STOP_CAST_CONTROL)
        tcpServerThread?.send(data)
        tcpClientThread?.send(data)
        tcpCallback.onDisconnect()
    }

    override fun onCleared() {
        tcpClientThread?.close()
        tcpServerThread?.close()
        tcpClientThread?.clearTCPCallback()
        tcpServerThread?.clearTCPCallback()
        udpSendThread?.stopSend()
        udpReceiveThread?.stopReceive()
        ScreenRecorder.INSTANCE.stopRecord()
        super.onCleared()
    }

    init {
        _localIP.value = getLocalIPAddress()
        _localIP.value?.let { localIP ->
            udpReceiveThread = UDPReceiveThread(localIP, udpCallback)
            udpReceiveThread?.start()
            if (udpSendThread == null) {
                udpSendThread = UDPSendThread(
                    UDPConstant.BORADCAST_IP,
                    byteArrayOf(UDPMessageHeader.ACTION_REMOTE_DEVICE_IP)
                )
                udpSendThread?.start()
            }
            viewModelScope.launch(Dispatchers.IO) {
                // 清理超时还没有收到IP消息的设备
                while (isActive) {
                    delay(UDPConstant.OFFLINE_TIMEOUT)
                    val currentTime = System.currentTimeMillis()
                    _remoteDeviceIPMap.entries.removeIf {
                        it.key != connectedIP.value &&
                                currentTime - it.value > UDPConstant.OFFLINE_TIMEOUT
                    }
                    _remoteDeviceIPs.postValue(_remoteDeviceIPMap.keys)
                }
            }
        }
    }



}