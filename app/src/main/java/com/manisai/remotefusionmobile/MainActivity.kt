package com.manisai.remotefusionmobile

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val serverManager = MobileWebSocketManager()
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.textLog)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnDiscover = findViewById<Button>(R.id.btnDiscover)

        btnStart.setOnClickListener {
            serverManager.startServer { log(it) }
        }

        btnStop.setOnClickListener {
            serverManager.stopServer()
            log("🛑 Server stopped.")
        }

        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnDiscover.setOnClickListener {
            val intent = Intent(this, DeviceDiscoveryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun log(message: String) {
        runOnUiThread {
            logView.append("$message\n")
        }
    }

    override fun onResume() {
        super.onResume()
        val service = TouchAccessibilityService()
        serverManager.setTouchService(service)
    }
}