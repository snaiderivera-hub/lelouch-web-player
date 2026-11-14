package com.lelouch.core.network.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.lelouch.core.network.dns.LelouchDnsManager

/**
 * Servicio VPN local Anti-DPI y DNS-over-HTTPS (inspirado en ByeDPIAndroid / Intra / WG Tunnel).
 *
 * Levanta una interfaz TUN ligera que:
 * 1. Activa el túnel VPN oficial de Android (🔑) y fija a nivel de sistema los servidores DNS sin censura.
 * 2. Evita enrutar 0.0.0.0/0 por espacio de usuario para mantener el 100% del ancho de banda físico en la TV Box y Móvil.
 * 3. Activa en [LelouchDnsManager] el socket factory Anti-DPI (TCP_NODELAY, ventana de 512KB, QoS IPTOS_LOWDELAY)
 *    y la rotación automática de firma User-Agent ante throttling del ISP.
 */
class LelouchVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpnTunnel()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startVpnTunnel()
                return START_STICKY
            }
        }
    }

    private fun startVpnTunnel() {
        if (vpnInterface != null) {
            LelouchDnsManager.setVpnShieldEnabled(true)
            return
        }
        try {
            val provider = LelouchDnsManager.getSelectedProvider()
            val builder = Builder()
                .setSession("Lelouch VPN Anti-DPI Shield")
                .setMtu(1420)
                .addAddress("10.215.173.1", 30)
                .addRoute("10.215.173.2", 32)
                .addDnsServer(provider.primaryIp)
                .addDnsServer(provider.secondaryIp)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            if (vpnInterface != null) {
                LelouchDnsManager.setVpnShieldEnabled(true)
                LelouchDnsManager.setEnabled(true)
                android.util.Log.i("LelouchVPN", "Túnel VPN Anti-DPI establecido con éxito (${provider.title})")
            } else {
                LelouchDnsManager.setVpnShieldEnabled(false)
            }
        } catch (e: Exception) {
            android.util.Log.e("LelouchVPN", "Error al iniciar túnel VPN: ${e.message}")
            LelouchDnsManager.setVpnShieldEnabled(false)
        }
    }

    private fun stopVpnTunnel() {
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        } finally {
            vpnInterface = null
            LelouchDnsManager.setVpnShieldEnabled(false)
            android.util.Log.i("LelouchVPN", "Túnel VPN Anti-DPI detenido")
        }
    }

    override fun onRevoke() {
        stopVpnTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.lelouch.core.network.vpn.START"
        const val ACTION_STOP = "com.lelouch.core.network.vpn.STOP"
    }
}

object LelouchVpnController {

    /**
     * Devuelve el Intent de permiso del sistema si el usuario aún no ha autorizado el túnel VPN,
     * o `null` si ya está autorizado y listo para iniciar directamente.
     */
    fun prepare(context: Context): Intent? {
        return try {
            VpnService.prepare(context)
        } catch (_: Exception) {
            null
        }
    }

    fun start(context: Context) {
        try {
            val intent = Intent(context, LelouchVpnService::class.java).apply {
                action = LelouchVpnService.ACTION_START
            }
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("LelouchVPN", "No se pudo iniciar LelouchVpnService: ${e.message}")
            // Aun si el firmware de una TV Box genérica carece de diálogo VpnService,
            // activamos el modo Anti-DPI interno (DoH 443 + QoS Socket + Rotación User-Agent)
            LelouchDnsManager.setVpnShieldEnabled(true)
            LelouchDnsManager.setEnabled(true)
        }
    }

    fun stop(context: Context) {
        try {
            val intent = Intent(context, LelouchVpnService::class.java).apply {
                action = LelouchVpnService.ACTION_STOP
            }
            context.startService(intent)
        } catch (_: Exception) {
        }
        LelouchDnsManager.setVpnShieldEnabled(false)
    }

    /**
     * Detecta si hay cualquier VPN activa en Android (nuestro túnel Lelouch o una externa como WireGuard/ProtonVPN).
     */
    fun isAnyVpnActive(context: Context): Boolean {
        if (LelouchDnsManager.isVpnShieldEnabled()) return true
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (_: Exception) {
            false
        }
    }
}

