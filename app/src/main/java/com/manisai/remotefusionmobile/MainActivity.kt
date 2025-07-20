package com.manisai.remotefusionmobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Switch
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val serverManager = MobileWebSocketManager()
    private lateinit var logView: TextView
    private lateinit var switchServerStatus: Switch

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.textLog)
        switchServerStatus = findViewById(R.id.switchServerStatus)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)

        // --- MODIFIED: Server Management Logic with Coroutines ---
        switchServerStatus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Launch on a background thread to prevent freezing the UI
                lifecycleScope.launch(Dispatchers.IO) {
                    serverManager.startServer { message ->
                        // The log function already uses runOnUiThread, so it's safe to call
                        log(message)
                    }
                }
            } else {
                // Also run stopServer on a background thread
                lifecycleScope.launch(Dispatchers.IO) {
                    serverManager.stopServer()
                }
            }
        }

        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Update the switch's state to match the server's actual state
        // without triggering the listener, then set the listener.
        switchServerStatus.isChecked = serverManager.isServerRunning()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure server is stopped cleanly when the activity is destroyed
        if (serverManager.isServerRunning()) {
            lifecycleScope.launch(Dispatchers.IO) {
                serverManager.stopServer()
            }
        }
        log("🔴 Activity destroyed. Server stopped.")
    }

    private fun log(message: String) {
        // This function is safe because runOnUiThread posts the action
        // to the main thread's queue.
        runOnUiThread {
            logView.append("$message\n")
            // Auto-scroll to the bottom
            val layout = logView.layout
            if (layout != null) {
                val scrollAmount = layout.getLineTop(logView.lineCount) - logView.height
                if (scrollAmount > 0) {
                    logView.scrollTo(0, scrollAmount)
                } else {
                    logView.scrollTo(0, 0)
                }
            }
        }
    }
}