package com.manisai.remotefusionmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manisai.remotefusionmobile.ui.theme.RemoteFusionMobileTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class RemoteControlActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RemoteFusionMobileTheme {
                RemoteControlScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MainCore.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        MainCore.disconnect()
    }
}

@Composable
fun RemoteControlScreen() {
    val connectionState by MainCore.connectionState.collectAsStateWithLifecycle()
    val statusMessage by MainCore.statusMessage.collectAsStateWithLifecycle()
    var keyboardInput by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Status and Connection Control ---
        Text(text = statusMessage, style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { if (connectionState == ConnectionState.CONNECTED) MainCore.disconnect() else MainCore.connect() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text( when (connectionState) {
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
                val old = keyboardInput.text
                val new = newText.text
                keyboardInput = newText

                if (new.length > old.length) MainCore.sendKeyboardInput(new.substring(old.length))
                else if (new.length < old.length) MainCore.sendKeyboardInput("\b")
            },
            label = { Text("Keyboard") },
            modifier = Modifier.fillMaxWidth(),
            enabled = (connectionState == ConnectionState.CONNECTED)
        )

        // --- Touch and Scroll Area ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // Takes up available vertical space
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Touchpad Area
            Box(
                modifier = Modifier
                    .weight(0.85f) // Takes up most of the horizontal space
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { MainCore.sendLeftClick() },
                            onLongPress = { MainCore.sendRightClick() }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            MainCore.sendMouseMove(dragAmount.x, dragAmount.y)
                        }
                    }
            )

            // Vertical Scroll Controller
            ScrollController(
                modifier = Modifier
                    .weight(0.15f) // Takes up remaining space
                    .fillMaxHeight(),
                enabled = (connectionState == ConnectionState.CONNECTED)
            )
        }

        // --- Click Buttons Area ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { MainCore.sendLeftClick() }, modifier = Modifier.weight(1f), enabled = (connectionState == ConnectionState.CONNECTED)) { Text("Left Click") }
            Button(onClick = { MainCore.sendRightClick() }, modifier = Modifier.weight(1f), enabled = (connectionState == ConnectionState.CONNECTED)) { Text("Right Click") }
        }
    }
}



@Composable
fun ScrollController(modifier: Modifier = Modifier, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    var isScrollingUp by remember { mutableStateOf(false) }
    var isScrollingDown by remember { mutableStateOf(false) }

    LaunchedEffect(isScrollingUp, isScrollingDown) {
        while (isScrollingUp || isScrollingDown) {
            if (enabled) {
                val direction = when {
                    isScrollingUp -> 1
                    isScrollingDown -> -1
                    else -> 0
                }
                MainCore.sendMouseScroll(direction)
            }
            delay(100L) // adjust delay for scroll speed
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .fillMaxHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scroll Up Button
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            isScrollingUp = true
                            tryAwaitRelease()
                            isScrollingUp = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text("↑", style = MaterialTheme.typography.headlineLarge)
        }

        // Scroll Down Button
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            isScrollingDown = true
                            tryAwaitRelease()
                            isScrollingDown = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text("↓", style = MaterialTheme.typography.headlineLarge)
        }
    }
}
