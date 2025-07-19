package com.manisai.remotefusionmobile
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class PcDiscoveryReceiver(
    private val onDeviceFound: (ip: String, port: Int) -> Unit
) {
    private var job: Job? = null

    fun startListening(port: Int = 54545) {
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket(port, InetAddress.getByName("0.0.0.0"))
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val message = String(packet.data, 0, packet.length)
                    if (message.startsWith("REMOTE_FUSION_PC:PORT=")) {
                        val ip = packet.address.hostAddress
                        val portString = message.substringAfter("PORT=").trim()
                        val parsedPort = portString.toIntOrNull() ?: continue
                        onDeviceFound(ip, parsedPort)
                    }
                }

                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopListening() {
        job?.cancel()
    }
}