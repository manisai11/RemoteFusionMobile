// In file: MobileWebSocketManager.kt
package com.manisai.remotefusionmobile

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Inet4Address
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

// --- Constants ---
const val DISCOVERY_BROADCAST_PORT = 48888
const val DISCOVERY_MESSAGE_PREFIX = "RF_MOBILE_SERVER_DISCOVERY:"

/**
 * The WebSocketServer implementation. No changes needed here.
 */
class MyWebSocketServer(
    port: Int,
    private val onLog: (String) -> Unit,
    private val onAction: (String) -> Unit
) : WebSocketServer(InetSocketAddress(port)) {

    override fun onStart() {
        Log.d("MyWebSocketServer", "Server started on port $port")
        onLog("📡 WebSocket Server started on port $port")
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        val clientIp = conn?.remoteSocketAddress?.address?.hostAddress ?: "Unknown"
        Log.d("MyWebSocketServer", "Client connected: $clientIp")
        onLog("🔌 Client connected: $clientIp")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        val clientIp = conn?.remoteSocketAddress?.address?.hostAddress ?: "Unknown"
        Log.d("MyWebSocketServer", "Client disconnected: $clientIp")
        onLog("🔌 Client disconnected: $clientIp (Code: $code, Reason: $reason)")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if (message == null) return
        // Do not log every message to avoid flooding the console
        // Log.d("MyWebSocketServer", "Received: $message")
        onAction(message)
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        Log.e("MyWebSocketServer", "Server error", ex)
        onLog("❗ Server error: ${ex?.message}")
    }
}

/**
 * The controller class. Modified to use coroutines for network operations.
 */
class MobileWebSocketManager {

    private var server: MyWebSocketServer? = null
    private val webSocketPort = 33700
    @Volatile private var _isServerRunning = false

    private val broadcastExecutor = Executors.newSingleThreadScheduledExecutor()
    private var broadcastTask: ScheduledFuture<*>? = null
    private var discoverySocket: DatagramSocket? = null
    private var currentLogCallback: ((String) -> Unit)? = null

    fun isServerRunning(): Boolean {
        return _isServerRunning
    }

    /**
     * MODIFIED: Marked as a 'suspend' function to be called from a coroutine.
     */
    suspend fun startServer(onLog: (String) -> Unit) {
        // WebSocketImpl.DEBUG = true // Uncomment for deep library debugging
        this.currentLogCallback = onLog
        if (_isServerRunning) {
            onLog("Server is already running.")
            return
        }

        try {
            val localIp = getMobileIpAddress() // This is now a suspend function
            if (localIp == null) {
                onLog("❗ Failed to get device IP. Check Wi-Fi connection.")
                return
            }

            withContext(Dispatchers.IO) {
                // Stop any previous instance
                server?.stop(1000)
            }

            server = MyWebSocketServer(webSocketPort, onLog) { message ->
                handleClientMessage(message, onLog)
            }
            server?.start() // This starts the server on its own background thread

            _isServerRunning = true
            onLog("WebSocket server starting on ${localIp}:${webSocketPort}")
            startDiscoveryBroadcast(localIp, webSocketPort, onLog)

        } catch (e: Exception) {
            onLog("❗ Failed to start server: ${e.message}")
            Log.e("MobileWebSocketManager", "Error starting server", e)
            _isServerRunning = false
        }
    }

    private fun handleClientMessage(message: String, onLog: (String) -> Unit) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")
            val data = json.getJSONObject("data")
            val touchServiceInstance = TouchAccessibilityService.getInstance()

            if (touchServiceInstance == null) {
                onLog("❌ Accessibility Service not active.")
                return
            }

            when (type) {
                "TOUCH" -> {
                    val x = data.getInt("x")
                    val y = data.getInt("y")
                    val action = data.getString("action")
                    if (action != "move") onLog("👆 Executing $action at ($x, $y)")
                    touchServiceInstance.simulatePointerEvent(x, y, action)
                }
                "KEY" -> {
                    val keyCode = data.getInt("key_code")
                    onLog("⌨️ Executing key code: $keyCode")
                    touchServiceInstance.pressKey(keyCode)
                }
                "TEXT" -> {
                    val text = data.getString("text")
                    onLog("🔤 Typing text: $text")
                    touchServiceInstance.inputText(text)
                }
                else -> onLog("❓ Unknown command type: '$type'")
            }

        } catch (e: Exception) {
            onLog("❌ Error parsing message: ${e.message}")
        }
    }

    /**
     * MODIFIED: Marked as a 'suspend' function.
     */
    suspend fun stopServer() {
        if (!_isServerRunning) return
        withContext(Dispatchers.IO) {
            server?.stop(1000)
            stopDiscoveryBroadcast()
        }
        _isServerRunning = false
        currentLogCallback?.invoke("🛑 Server stopped.")
        Log.d("MobileWebSocketManager", "Server stopped.")
    }

    /**
     * MODIFIED: This is a network call, so it must be a suspend function
     * running on the IO dispatcher to avoid blocking the main thread.
     */
    private suspend fun getMobileIpAddress(): String? = withContext(Dispatchers.IO) {
        try {
            for (intf in NetworkInterface.getNetworkInterfaces()) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address) {
                        return@withContext addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("MobileWebSocketManager", "Error getting IP: ${ex.message}", ex)
        }
        return@withContext null
    }

    private fun startDiscoveryBroadcast(ipAddress: String, port: Int, onLog: (String) -> Unit) {
        val message = "$DISCOVERY_MESSAGE_PREFIX$ipAddress:$port"
        val data = message.toByteArray(Charsets.UTF_8)
        onLog("Starting discovery broadcast...")
        Log.d("MobileWebSocketManager", "Starting discovery broadcast: '$message'")

        broadcastTask?.cancel(true)
        broadcastTask = broadcastExecutor.scheduleAtFixedRate({
            try {
                if (discoverySocket == null || discoverySocket!!.isClosed) {
                    discoverySocket = DatagramSocket()
                    discoverySocket?.broadcast = true
                }
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(data, data.size, broadcastAddress, DISCOVERY_BROADCAST_PORT)
                discoverySocket?.send(packet)
            } catch (e: Exception) {
                onLog("Error sending broadcast: ${e.message}")
                discoverySocket?.close()
                discoverySocket = null
            }
        }, 0, 3, TimeUnit.SECONDS)
    }

    private fun stopDiscoveryBroadcast() {
        broadcastTask?.cancel(true)
        discoverySocket?.close()
        discoverySocket = null
        Log.d("MobileWebSocketManager", "Stopped discovery broadcast.")
    }
}