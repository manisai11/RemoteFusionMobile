//[MOBILE TO PC] DISCOVERS AVAILABLE DEVICES AND SHOWS

package com.manisai.remotefusionmobile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import kotlin.coroutines.coroutineContext

class DeviceDiscoveryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var refreshButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ArrayAdapter<DiscoveredDevice>

    private val discoveredDevices = mutableListOf<DiscoveredDevice>()

    private var listeningJob: Job? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_discovery)

        listView = findViewById(R.id.device_list_view)
        refreshButton = findViewById(R.id.refresh_button)
        progressBar = findViewById(R.id.progress_bar)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, discoveredDevices)
        listView.adapter = adapter

        Toast.makeText(this, "Discovery Started", Toast.LENGTH_SHORT).show()

        refreshButton.setOnClickListener {
            Log.d("DISCOVERY", "Refresh clicked")
            restartDiscovery()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = discoveredDevices[position]

            // Set the IP and Port in MainCore from the selected object
            MainCore.deviceIp = selectedDevice.ipAddress
            MainCore.devicePort = selectedDevice.port

            // Start the RemoteControlActivity
            val intent = Intent(this, RemoteControlActivity::class.java)
            startActivity(intent)
        }

        restartDiscovery()
    }

    private fun restartDiscovery() {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            discoveredDevices.clear()
            adapter.notifyDataSetChanged()
        }

        listeningJob?.cancel()
        listeningJob = coroutineScope.launch {
            listenForBroadcasts()
        }

        coroutineScope.launch {
            delay(10000)
            listeningJob?.cancel()
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@DeviceDiscoveryActivity, "Discovery Finished", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun listenForBroadcasts() {
        val buffer = ByteArray(1024)
        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(54665))
            }

            while (coroutineContext.isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val message = String(packet.data, 0, packet.length)
                val senderIp = packet.address.hostAddress ?: continue

                Log.d("UDP_RECEIVER", "Received: $message from $senderIp")

                // New logic to parse "REMOTE_FUSION_PC|NAME=...|PORT=..."
                if (message.startsWith("REMOTE_FUSION_PC")) {
                    try {
                        val parts = message.split('|')
                        val name = parts[1].substringAfter("NAME=").trim()
                        val port = parts[2].substringAfter("PORT=").trim()

                        val newDevice = DiscoveredDevice(
                            name = name,
                            ipAddress = senderIp,
                            port = port
                        )

                        withContext(Dispatchers.Main) {
                            if (!discoveredDevices.contains(newDevice)) {
                                discoveredDevices.add(newDevice)
                                adapter.notifyDataSetChanged()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("UDP_PARSER", "Failed to parse message: $message", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UDP_RECEIVER", "Error: ${e.message}", e)
        } finally {
            socket?.close()
            Log.d("UDP_RECEIVER", "Socket closed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listeningJob?.cancel()
        coroutineScope.cancel()
        Log.d("DISCOVERY", "onDestroy: discovery cancelled")
    }
}