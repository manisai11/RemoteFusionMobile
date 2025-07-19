package com.manisai.remotefusionmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manisai.remotefusionmobile.ui.theme.RemoteFusionMobileTheme

class RemoteControlActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RemoteFusionMobileTheme {
                RemoteControlScreen()
            }
        }
    }

    // Attempt to connect when the screen is shown
    override fun onResume() {
        super.onResume()
        MainCore.connect()
    }

    // Disconnect when the screen is closed to save resources
    override fun onDestroy() {
        super.onDestroy()
        MainCore.disconnect()
    }
}

@Composable
fun RemoteControlScreen() {
    // Observe the connection state and status message from MainCore
    val connectionState by MainCore.connectionState.collectAsStateWithLifecycle()
    val statusMessage by MainCore.statusMessage.collectAsStateWithLifecycle()

    var keyboardInput by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Status and Connection Control ---
        Text(text = statusMessage, style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = {
                if (connectionState == ConnectionState.CONNECTED) {
                    MainCore.disconnect()
                } else {
                    MainCore.connect()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when (connectionState) {
                    ConnectionState.CONNECTED -> "Disconnect"
                    ConnectionState.CONNECTING -> "Connecting..."
                    else -> "Connect"
                }
            )
        }

        // --- Keyboard Input ---
        OutlinedTextField(
            value = keyboardInput,
            onValueChange = { newText ->
                // Logic to detect what was added or removed
                val old = keyboardInput.text
                val new = newText.text
                keyboardInput = newText

                if (new.length > old.length) {
                    // Text was added
                    MainCore.sendKeyboardInput(new.substring(old.length))
                } else if (new.length < old.length) {
                    // Backspace was pressed
                    MainCore.sendKeyboardInput("\b")
                }
            },
            label = { Text("Keyboard") },
            modifier = Modifier.fillMaxWidth(),
            enabled = (connectionState == ConnectionState.CONNECTED)
        )

        // --- Virtual Touchpad ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Takes up remaining space
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { MainCore.sendLeftClick() },
                        onDoubleTap = { MainCore.sendLeftClick(); MainCore.sendLeftClick() },
                        onLongPress = { MainCore.sendRightClick() }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        MainCore.sendMouseMove(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            Text(
                "Touchpad Area",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}