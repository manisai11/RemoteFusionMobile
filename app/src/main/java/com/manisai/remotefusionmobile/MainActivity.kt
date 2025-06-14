package com.manisai.remotefusionmobile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manisai.remotefusionmobile.ui.theme.RemoteFusionMobileTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.concurrent.TimeUnit

// --- Constants from your original code ---
private const val DEFAULT_WEBSOCKET_PORT = 8765
private const val READ_TIMEOUT_SECONDS = 3L
private const val DEBOUNCE_DELAY_MS = 400L
private const val NORMAL_CLOSURE_STATUS = 1000
private const val DEFAULT_PC_IP = "192.168.218.208"
private const val TOUCHPAD_DEBUG_TAG = "TouchpadInput"
private const val MOUSE_UPDATE_INTERVAL = 2L
private const val VELOCITY_MULTIPLIER = 1.2f
private const val SMOOTHING_FACTOR = 0.85f
private const val TAP_DEBOUNCE_MS = 220L

enum class ConnectionState {
    IDLE, CONNECTING, CONNECTED, ERROR
}

class MainActivity : ComponentActivity() {
    // --- SERVER: For accepting connections from PC (for remote touch) ---
    private lateinit var mobileWebSocketManager: MobileWebSocketManager
    private val serverLogs = mutableStateListOf<String>() // For showing server logs in UI

    // --- CLIENT: For connecting to the PC (for touchpad/keyboard) ---
    private var webSocket: WebSocket? = null
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
    private val connectionStatus = mutableStateOf("Not Connected")
    private val currentConnectionState = mutableStateOf(ConnectionState.IDLE)
    private val serverIpAddress = mutableStateOf(DEFAULT_PC_IP)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. SETUP SERVER: Initialize the manager and start the server
        mobileWebSocketManager = MobileWebSocketManager()
        mobileWebSocketManager.startServer { logMessage ->
            // This runs on a background thread, so we update UI on the main thread
            runOnUiThread {
                Log.d("MobileServer", logMessage)
                serverLogs.add(0, logMessage) // Add to top of list for UI
                if (serverLogs.size > 100) serverLogs.removeAt(serverLogs.lastIndex) // Keep list from growing
            }
        }

        setContent {
            RemoteFusionMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val status by connectionStatus
                    val connState by currentConnectionState
                    val currentIp by serverIpAddress

                    // MainScreen now wraps your UI and adds server logs
                    MainScreen(
                        statusMessage = status,
                        connectionState = connState,
                        ipAddress = currentIp,
                        onIpAddressChange = { serverIpAddress.value = it },
                        onConnectClicked = { ip ->
                            if (connState == ConnectionState.CONNECTED) {
                                disconnectFromServer()
                            } else if (connState == ConnectionState.IDLE || connState == ConnectionState.ERROR) {
                                connectToServer(ip)
                            }
                        },
                        sendKeyboardString = { text -> sendKeyboardInput(text) },
                        onMouseMove = { dx, dy -> sendMouseMove(dx, dy) },
                        onMouseLeftClick = { sendMouseLeftClick() },
                        onMouseRightClick = { sendMouseRightClick() },
                        serverLogs = serverLogs // Pass server logs to the UI
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 2. CONNECT TOUCH SERVICE: Each time the app is resumed, check for the service
        val serviceInstance = TouchAccessibilityService.instance
        if (serviceInstance != null) {
            mobileWebSocketManager.setTouchService(serviceInstance)
            Log.d("MainActivity", "✅ Touch Accessibility Service is connected.")
        } else {
            Log.w("MainActivity", "⚠️ Touch Service NOT enabled. Remote taps won't work.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 3. CLEANUP: Stop both the client and server connections
        webSocket?.close(NORMAL_CLOSURE_STATUS, "Activity destroyed")
        mobileWebSocketManager.stopServer() // Important: Releases the port
    }


    // --- All your original client-side functions are preserved below ---

    private fun connectToServer(ip: String) {
        if (currentConnectionState.value == ConnectionState.CONNECTING) return
        webSocket?.close(NORMAL_CLOSURE_STATUS, "New connection requested")
        webSocket = null
        currentConnectionState.value = ConnectionState.CONNECTING
        connectionStatus.value = "Connecting to ws://$ip:$DEFAULT_WEBSOCKET_PORT..."
        val request = Request.Builder().url("ws://$ip:$DEFAULT_WEBSOCKET_PORT").build()
        httpClient.newWebSocket(request, createWebSocketListener())
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                this@MainActivity.webSocket = ws
                runOnUiThread {
                    currentConnectionState.value = ConnectionState.CONNECTED
                    connectionStatus.value = "✅ Connected to PC!"
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                runOnUiThread { connectionStatus.value = "💬 PC says: $text" }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread {
                    currentConnectionState.value = ConnectionState.ERROR
                    connectionStatus.value = "❌ Connection failed: ${t.message}"
                    Log.e("WebSocket", "Connection Failure: ", t)
                    webSocket = null
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(NORMAL_CLOSURE_STATUS, null)
                runOnUiThread {
                    if (currentConnectionState.value != ConnectionState.CONNECTING) {
                        connectionStatus.value = "🔌 Connection closing: $reason"
                    }
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                runOnUiThread {
                    if (currentConnectionState.value != ConnectionState.ERROR &&
                        currentConnectionState.value != ConnectionState.CONNECTING) {
                        currentConnectionState.value = ConnectionState.IDLE
                        connectionStatus.value = "🔌 Disconnected."
                    }
                    if (this@MainActivity.webSocket == ws) {
                        this@MainActivity.webSocket = null
                    }
                }
            }
        }
    }

    private fun disconnectFromServer() {
        webSocket?.close(NORMAL_CLOSURE_STATUS, "User disconnected")
        webSocket = null
        currentConnectionState.value = ConnectionState.IDLE
        connectionStatus.value = "🔌 Disconnected by user."
    }

    private fun sendRawWebSocketMessage(data: String) {
        if (currentConnectionState.value == ConnectionState.CONNECTED) {
            webSocket?.send(data)
        }
    }

    private fun sendKeyboardInput(data: String) {
        if (data == "\b") {
            sendRawWebSocketMessage("type:\b")
        } else {
            sendRawWebSocketMessage("type:$data")
        }
    }

    private fun sendMouseMove(dx: Float, dy: Float) {
        val formattedDx = "%.3f".format(dx)
        val formattedDy = "%.3f".format(dy)
        sendRawWebSocketMessage("mouse_move:$formattedDx,$formattedDy")
    }

    private fun sendMouseLeftClick() {
        sendRawWebSocketMessage("mouse_lclick")
    }

    private fun sendMouseRightClick() {
        sendRawWebSocketMessage("mouse_rclick")
    }
}


// --- UI Composables ---

@Composable
fun MainScreen(
    statusMessage: String,
    connectionState: ConnectionState,
    ipAddress: String,
    onIpAddressChange: (String) -> Unit,
    onConnectClicked: (ip: String) -> Unit,
    sendKeyboardString: (String) -> Unit,
    onMouseMove: (dx: Float, dy: Float) -> Unit,
    onMouseLeftClick: () -> Unit,
    onMouseRightClick: () -> Unit,
    serverLogs: List<String>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            WebSocketUI(
                statusMessage,
                connectionState,
                ipAddress,
                onIpAddressChange,
                onConnectClicked,
                sendKeyboardString,
                onMouseMove,
                onMouseLeftClick,
                onMouseRightClick
            )
        }
        item {
            ServerLogUI(serverLogs)
        }
    }
}

@Composable
fun WebSocketUI(
    statusMessage: String,
    connectionState: ConnectionState,
    ipAddress: String,
    onIpAddressChange: (String) -> Unit,
    onConnectClicked: (ip: String) -> Unit,
    sendKeyboardString: (String) -> Unit,
    onMouseMove: (dx: Float, dy: Float) -> Unit,
    onMouseLeftClick: () -> Unit,
    onMouseRightClick: () -> Unit
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var lastSentText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val viewConfiguration = LocalViewConfiguration.current
    val touchSlop = viewConfiguration.touchSlop
    val haptic = LocalHapticFeedback.current
    var lastVelocity by remember { mutableStateOf(Offset.Zero) }
    var lastUpdateTime by remember { mutableStateOf(0L) }
    var lastPosition by remember { mutableStateOf(Offset.Zero) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = ipAddress,
            onValueChange = onIpAddressChange,
            label = { Text("Server IP Address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = connectionState == ConnectionState.IDLE || connectionState == ConnectionState.ERROR
        )

        Button(
            onClick = { onConnectClicked(ipAddress) },
            enabled = connectionState == ConnectionState.IDLE ||
                    connectionState == ConnectionState.CONNECTED ||
                    connectionState == ConnectionState.ERROR,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when (connectionState) {
                    ConnectionState.IDLE -> "Connect to PC"
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.CONNECTED -> "Disconnect"
                    ConnectionState.ERROR -> "Retry Connection"
                }
            )
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { newInput ->
                inputText = newInput
                debounceJob?.cancel()
                debounceJob = coroutineScope.launch {
                    delay(DEBOUNCE_DELAY_MS)
                    val currentText = newInput.text
                    val previousText = lastSentText
                    if (connectionState == ConnectionState.CONNECTED && currentText != previousText) {
                        val commonPrefixLength = previousText.commonPrefixWith(currentText).length
                        if (previousText.length > commonPrefixLength) {
                            repeat(previousText.length - commonPrefixLength) { sendKeyboardString("\b") }
                        }
                        if (currentText.length > commonPrefixLength) {
                            sendKeyboardString(currentText.substring(commonPrefixLength))
                        }
                        lastSentText = currentText
                    } else if (connectionState == ConnectionState.CONNECTED &&
                        currentText.isEmpty() && previousText.isNotEmpty()) {
                        repeat(previousText.length) { sendKeyboardString("\b") }
                        lastSentText = ""
                    }
                }
            },
            label = { Text("Live Keyboard Input") },
            modifier = Modifier.fillMaxWidth(),
            enabled = connectionState == ConnectionState.CONNECTED
        )

        if (connectionState == ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Virtual Touchpad",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.88f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                var isDragging = false
                                var pointerCount = 0

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val currentTime = System.nanoTime()

                                    event.changes.forEach { change ->
                                        if (change.pressed) {
                                            val currentPosition = change.position

                                            if (!change.previousPressed) {
                                                pointerCount++
                                                if (pointerCount == 1) {
                                                    lastPosition = currentPosition
                                                    isDragging = false
                                                }
                                            } else if (isDragging && pointerCount == 1) {
                                                val deltaPosition = currentPosition - lastPosition
                                                val deltaTime = (currentTime - lastUpdateTime) / 1_000_000.0f

                                                if (deltaTime > 0) {
                                                    val instantVelocity = Offset(deltaPosition.x / deltaTime, deltaPosition.y / deltaTime)
                                                    lastVelocity = Offset(
                                                        SMOOTHING_FACTOR * lastVelocity.x + (1 - SMOOTHING_FACTOR) * instantVelocity.x,
                                                        SMOOTHING_FACTOR * lastVelocity.y + (1 - SMOOTHING_FACTOR) * instantVelocity.y
                                                    )
                                                    val speed = lastVelocity.getDistance()
                                                    val accelerationFactor = if (speed > 0.5f) 1.0f + (speed - 0.5f) * VELOCITY_MULTIPLIER else 1.0f
                                                    val dx = deltaPosition.x * accelerationFactor
                                                    val dy = deltaPosition.y * accelerationFactor
                                                    onMouseMove(dx, dy)
                                                    lastPosition = currentPosition
                                                    lastUpdateTime = currentTime
                                                }
                                            } else if (!isDragging && pointerCount == 1) {
                                                val totalDelta = (currentPosition - lastPosition).getDistance()
                                                if (totalDelta > touchSlop) {
                                                    isDragging = true
                                                    lastPosition = currentPosition
                                                    lastUpdateTime = currentTime
                                                }
                                            }
                                        } else if (change.previousPressed) {
                                            pointerCount--
                                            if (pointerCount == 0) {
                                                if (!isDragging) {
                                                    val tapTime = System.currentTimeMillis()
                                                    if (tapTime - lastTapTime > TAP_DEBOUNCE_MS) {
                                                        onMouseLeftClick()
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        lastTapTime = tapTime
                                                    }
                                                }
                                                isDragging = false
                                                lastVelocity = Offset.Zero
                                            }
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        }
                ) {
                    Text(
                        "Move:Drag | LClick:Tap | RClick:2-Finger Tap",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.12f)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                onMouseLeftClick()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("LClick", style = MaterialTheme.typography.labelSmall)
                    }
                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                onMouseRightClick()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("RClick", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text(
            text = "Status: $statusMessage",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ServerLogUI(serverLogs: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Mobile Server Logs", style = MaterialTheme.typography.titleMedium)
        Text("Accepting remote taps from PC on port 33530", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 250.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))
        ) {
            if (serverLogs.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Waiting for server activity...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    items(serverLogs) { log ->
                        Text(
                            text = log,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}


@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    thickness: Dp = 1.dp
) {
    Box(
        modifier
            .fillMaxHeight()
            .width(thickness)
            .background(color = color)
    )
}