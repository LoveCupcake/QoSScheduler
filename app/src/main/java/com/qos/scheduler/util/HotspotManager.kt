package com.qos.scheduler.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import java.net.NetworkInterface

/**
 * Utility class to detect hotspot state and subnet
 */
class HotspotManager(private val context: Context) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    /**
     * Check if WiFi hotspot is currently enabled
     */
    fun isHotspotEnabled(): Boolean {
        return try {
            // Try to detect hotspot by checking for ap0 or wlan0 interface with tethering
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val netInterface = interfaces.nextElement()
                val name = netInterface.name.lowercase()
                
                // Common hotspot interface names
                if (name.startsWith("ap") || name == "wlan0" || name.startsWith("swlan")) {
                    // Check if interface has IP addresses assigned (indicating active hotspot)
                    val addresses = netInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        val ip = addr.hostAddress ?: continue
                        
                        // Hotspot typically uses these subnets
                        if (ip.startsWith("192.168.43.") || 
                            ip.startsWith("192.168.49.") ||
                            ip.startsWith("172.") ||
                            ip.startsWith("10.")) {
                            // Verify it's not VPN tunnel (10.0.0.x)
                            if (!ip.startsWith("10.0.0.")) {
                                return true
                            }
                        }
                    }
                }
            }
            
            // Fallback: Try reflection method for older Android versions
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
                method.isAccessible = true
                return method.invoke(wifiManager) as? Boolean ?: false
            }
            
            false
        } catch (e: Exception) {
            // If we can't determine, assume false (safer)
            false
        }
    }

    /**
     * Get the hotspot subnet prefix (e.g., "192.168.43", "172.25.42")
     * Returns null if hotspot is not enabled
     */
    fun getHotspotSubnet(): String? {
        if (!isHotspotEnabled()) return null
        
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val netInterface = interfaces.nextElement()
                val name = netInterface.name.lowercase()
                
                // Look for hotspot interface
                if (name.startsWith("ap") || name == "wlan0" || name.startsWith("swlan")) {
                    val addresses = netInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        val ip = addr.hostAddress ?: continue
                        
                        // Skip IPv6 and VPN tunnel
                        if (ip.contains(":") || ip.startsWith("10.0.0.")) continue
                        
                        // Extract subnet (first 3 octets for IPv4)
                        if (ip.startsWith("192.168.") || 
                            ip.startsWith("172.") || 
                            ip.startsWith("10.")) {
                            val parts = ip.split(".")
                            if (parts.size == 4) {
                                return "${parts[0]}.${parts[1]}.${parts[2]}"
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the hotspot gateway IP (typically ends with .1)
     */
    fun getHotspotGatewayIp(): String? {
        val subnet = getHotspotSubnet() ?: return null
        return "$subnet.1"
    }

    /**
     * Check if an IP address belongs to the hotspot subnet
     */
    fun isInHotspotSubnet(ipAddress: String): Boolean {
        val subnet = getHotspotSubnet() ?: return false
        
        // For IPv4
        if (!ipAddress.contains(":")) {
            return ipAddress.startsWith("$subnet.")
        }
        
        // For IPv6, we need to check if it's in the hotspot's IPv6 range
        // Most hotspots don't assign IPv6, but if they do, it's usually link-local
        // For now, we'll be conservative and only accept IPv4 from hotspot subnet
        return false
    }
}
