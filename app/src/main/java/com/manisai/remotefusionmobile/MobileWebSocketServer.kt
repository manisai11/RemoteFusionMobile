// In file: MobileWebSocketServer.kt

package com.manisai.remotefusionmobile

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

/**
 * This is the actual WebSocket Server, powered by the library we added.
 * It handles all the complex protocol details for us.
 */
class MyWebSocketServer(
    port: Int,
    private val onLog: (String) -> Unit,
    private val onAction: (String) -> Unit
) : WebSocketServer(InetSocketAddress(port)) {

    override fun onStart() {
        onLog("📡 WebSocket Server started on port $port")
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        onLog("🔌 Client connected: ${conn?.remoteSocketAddress?.address?.hostAddress}")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        onLog("🔌 Client disconnected.")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if (message == null) return
        onLog("📥 Received: $message")
        onAction(message) // Pass the message to our manager to handle it
        conn?.send("✅ Acknowledged: $message") // Send a reply to the client
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        onLog("❗ Server error: ${ex?.message}")
    }
}


/**
 * This is our controller class. MainActivity will use this to start, stop,
 * and communicate with the server.
 */
class MobileWebSocketManager {

    private var server: MyWebSocketServer? = null
    private val port = 33700
    private var touchService: TouchAccessibilityService? = null

    fun setTouchService(service: TouchAccessibilityService) {
        this.touchService = service
    }

    fun startServer(onLog: (String) -> Unit) {
        // Stop any existing server before starting a new one
        server?.stop()
        try {
            server = MyWebSocketServer(port, onLog) { message ->
                handleClientMessage(message, onLog)
            }
            server?.start() // This starts the server on a new background thread
        } catch (e: Exception) {
            onLog("❗ Failed to start server: ${e.message}")
        }
    }

    private fun handleClientMessage(message: String, onLog: (String) -> Unit) {
        if (message.startsWith("tap:")) {
            val parts = message.removePrefix("tap:").split(",")
            if (parts.size == 2) {
                val x = parts[0].toIntOrNull()
                val y = parts[1].toIntOrNull()
                if (x != null && y != null) {
                    if (touchService != null) {
                        onLog("👆 Executing tap at ($x, $y)")
                        touchService?.simulateTap(x, y)
                    } else {
                        onLog("❌ Touch Service is not connected. Cannot tap.")
                    }
                } else {
                    onLog("❌ Invalid coordinates in message: $message")
                }
            }
        }
    }

    fun stopServer() {
        server?.stop(1000) // 1 second timeout
        Log.d("MobileWebSocketManager", "Server stopped.")
    }
}