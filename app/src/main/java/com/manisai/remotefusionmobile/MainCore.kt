package com.manisai.remotefusionmobile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    IDLE, CONNECTING, CONNECTED, ERROR
}

object MainCore {
    // --- Public Properties to be set BEFORE starting the remote control screen ---
    var deviceIp: String? = null
    var devicePort: String? = null

    // --- Default values if nothing is provided ---
    private const val DEFAULT_IP = "192.168.0.1" // A generic default
    private const val DEFAULT_PORT = 8765

    // --- State for the UI to observe ---
    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Not Connected")
    val statusMessage = _statusMessage.asStateFlow()

    // --- Internal Networking components ---
    private var webSocket: WebSocket? = null
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    // --- Public Functions for the UI to call ---

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING || _connectionState.value == ConnectionState.CONNECTED) {
            return
        }

        val ipToUse = deviceIp ?: DEFAULT_IP
        val portToUse = devicePort?.toIntOrNull() ?: DEFAULT_PORT
        val url = "ws://$ipToUse:$portToUse"

        _connectionState.value = ConnectionState.CONNECTING
        _statusMessage.value = "Connecting to $url..."

        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(request, createWebSocketListener())
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.IDLE
        _statusMessage.value = "Disconnected"
    }

    fun sendKeyboardInput(text: String) {
        val command = if (text == "\b") "type:\b" else "type:$text"
        webSocket?.send(command)
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        webSocket?.send("mouse_move:%.3f,%.3f".format(dx, dy))
    }

    fun sendLeftClick() {
        webSocket?.send("mouse_lclick")
    }

    fun sendRightClick() {
        webSocket?.send("mouse_rclick")
    }

    // --- Private WebSocket Listener ---
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
                _statusMessage.value = "✅ Connected"
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.ERROR
                _statusMessage.value = "❌ Error: ${t.message}"
                webSocket = null
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.IDLE
                _statusMessage.value = "🔌 Disconnected"
                webSocket = null
            }
        }
    }
}