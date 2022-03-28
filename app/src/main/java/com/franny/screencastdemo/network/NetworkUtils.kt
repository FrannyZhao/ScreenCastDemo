package com.franny.screencastdemo.network

import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

fun getLocalIPAddress(): String? {
    val netInterfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
    var ip: InetAddress?
    while (netInterfaces.hasMoreElements()) {
        val ni = netInterfaces.nextElement()
        val address = ni.inetAddresses
        while (address.hasMoreElements()) {
            ip = address.nextElement()
            if (ip != null && ip.isSiteLocalAddress
                && !ip.isLoopbackAddress
                && ip.hostAddress.indexOf(":") == -1
                && ip.hostAddress?.contains("192.") == true
            ) {
                return ip.hostAddress
            }
        }
    }
    return null
}