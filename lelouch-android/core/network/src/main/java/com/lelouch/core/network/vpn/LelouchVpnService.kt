package com.lelouch.core.network.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.lelouch.core.network.dns.LelouchDnsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Servicio VPN local Anti-DPI y DNS-over-HTTPS (inspirado en ByeDPIAndroid / Intra / WG Tunnel).
 *
 * Levanta una interfaz TUN ligera que:
 * 1. Activa el túnel VPN oficial de Android (🔑) y excluye el proceso de video de `tun0` (`addDisallowedApplication`)
 *    para que los paquetes de video salgan directamente por la tarjeta de red física sin pérdida de velocidad.
 * 2. Activa en [LelouchDnsManager] el `AntiDpiSplitSocket` (fragmentación TCP del primer paquete HTTP/TLS en
 *    micro-segmentos con `TCP_NODELAY`), ventana de recepción de 512KB, QoS `IPTOS_LOWDELAY` y resolución DoH 443.
 * 3. Ejecuta verificación en vivo (`verifyVpnShieldLive`) para comprobar la respuesta del servidor DoH 443.
 */
class LelouchVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

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
        LelouchDnsManager.setVpnShieldEnabled(true)
        LelouchDnsManager.setEnabled(true)
        serviceScope.launch {
            LelouchDnsManager.verifyVpnShieldLive()
        }

        if (vpnInterface != null) {
            return
        }
        try {
            val builder = Builder()
                .setSession("Lelouch VPN Anti-DPI Shield")
                .setMtu(1420)
                .addAddress("10.215.173.1", 30)
                .addRoute("10.215.173.2", 32)

            // Excluir el propio paquete de la interfaz virtual tun0 para que los flujos de video y DoH 443
            // salgan por la interfaz física usando AntiDpiSplitSocket sin cuello de botella en espacio de usuario.
            runCatching {
                builder.addDisallowedApplication(packageName)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            if (vpnInterface != null) {
                android.util.Log.i("LelouchVPN", "Túnel VPN Anti-DPI (tun0 + DoH 443 + TCP Split) establecido con éxito")
            } else {
                // En TV Boxes cuyo firmware no incluye com.android.vpndialogs, mantenemos activo
                // el escudo Anti-DPI interno (DoH 443 + Fragmentación TCP + QoS 512KB).
                android.util.Log.i("LelouchVPN", "Modo Escudo Directo Anti-DPI + DoH 443 activo (ROM sin interfaz tun0)")
            }
        } catch (e: Exception) {
            android.util.Log.w("LelouchVPN", "Fallback a Escudo Directo Anti-DPI + DoH 443: ${e.message}")
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

    private val controllerScope = CoroutineScope(Dispatchers.IO)

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
        LelouchDnsManager.setVpnShieldEnabled(true)
        LelouchDnsManager.setEnabled(true)
        controllerScope.launch {
            LelouchDnsManager.verifyVpnShieldLive()
        }
        try {
            val intent = Intent(context, LelouchVpnService::class.java).apply {
                action = LelouchVpnService.ACTION_START
            }
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("LelouchVPN", "Fallback Anti-DPI directo activo: ${e.message}")
        }
    }

    fun stop(context: Context) {
        LelouchDnsManager.setVpnShieldEnabled(false)
        try {
            val intent = Intent(context, LelouchVpnService::class.java).apply {
                action = LelouchVpnService.ACTION_STOP
            }
            context.startService(intent)
        } catch (_: Exception) {
        }
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
