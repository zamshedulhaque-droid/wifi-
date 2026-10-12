package com.example.wificall

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Receiver side: registers this device as "ReceiverN" on the local network.
 * Caller side: discovers which "ReceiverN" services are online.
 * Uses Android NSD (mDNS) - works on plain Wi-Fi and Hotspot, no internet needed.
 */
class NsdHelper(private val context: Context) {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun registerReceiver(slot: Int, port: Int, displayName: String?) {
        acquireMulticastLock()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Receiver$slot"
            serviceType = Constants.SERVICE_TYPE
            setPort(port)
            if (!displayName.isNullOrBlank()) {
                try {
                    setAttribute(Constants.NSD_ATTR_DISPLAY_NAME, displayName)
                } catch (e: Exception) {
                    Log.w("NsdHelper", "Could not set display name attribute", e)
                }
            }
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d("NsdHelper", "Registered as ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdHelper", "Registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun unregister() {
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (e: Exception) { /* ignore */ }
        }
        registrationListener = null
        releaseMulticastLock()
    }

    fun startDiscovery(
        onFound: (slot: Int, host: String, port: Int, displayName: String?) -> Unit,
        onLost: (slot: Int) -> Unit
    ) {
        acquireMulticastLock()
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceName.startsWith("Receiver")) return
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val slot = info.serviceName.removePrefix("Receiver").toIntOrNull() ?: return
                        val host = info.host?.hostAddress ?: return
                        val nameBytes = info.attributes?.get(Constants.NSD_ATTR_DISPLAY_NAME)
                        val displayName = nameBytes?.let { String(it, Charsets.UTF_8) }
                        onFound(slot, host, info.port, displayName)
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                val slot = service.serviceName.removePrefix("Receiver").toIntOrNull() ?: return
                onLost(slot)
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsdManager.discoverServices(Constants.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (e: Exception) { /* ignore */ }
        }
        discoveryListener = null
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("wificall_lock").apply {
                setReferenceCounted(true)
                acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }
}
