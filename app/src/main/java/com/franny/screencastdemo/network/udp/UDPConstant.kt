package com.franny.screencastdemo.network.udp

object UDPConstant {
    const val PORT = 6790
    const val BORADCAST_IP = "255.255.255.255"
    const val SEND_INTERVAL = 5000 // 单位毫秒
    const val OFFLINE_TIMEOUT = SEND_INTERVAL * 3L // 多久没有收到UDP消息就下线
}