package com.example.vpnsample

import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DnsProcessor {

    @Volatile
    private var running = true

    private const val BLOCKED_DOMAIN = "instagram.com"

    fun stop() {
        running = false
    }

    fun run(vpnInterface: ParcelFileDescriptor, service: VpnService) {
        val input = FileInputStream(vpnInterface.fileDescriptor)
        val output = FileOutputStream(vpnInterface.fileDescriptor)

        val packet = ByteArray(32767)

        while (running) {
            try {
                val length = input.read(packet)
                if (length <= 0) continue
                if (!isDnsPacket(packet, length)) continue

                val domain = extractDomain(packet) ?: continue

                val response = if (domain.contains(BLOCKED_DOMAIN, true)) {
                    buildBlockedDnsResponse(packet, length)
                } else {
                    forwardAndBuildResponse(packet, length, service)
                }

                if (response != null) {
                    output.write(response)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---------- DNS detection ----------

    private fun isDnsPacket(packet: ByteArray, length: Int): Boolean {
        if (length < 28) return false

        val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return false

        val destPort =
            ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
                    (packet[ipHeaderLength + 3].toInt() and 0xFF)

        return destPort == 53
    }

    // ---------- Domain parsing ----------

    private fun extractDomain(packet: ByteArray): String? {
        val sb = StringBuilder()

        val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
        var i = ipHeaderLength + 8 + 12

        while (packet[i].toInt() != 0) {
            val len = packet[i++].toInt()
            repeat(len) {
                sb.append(packet[i++].toInt().toChar())
            }
            sb.append(".")
        }

        return sb.toString().trimEnd('.')
    }

    // ---------- Forward DNS + rebuild packet ----------

    private fun forwardAndBuildResponse(
        request: ByteArray,
        length: Int,
        service: VpnService
    ): ByteArray? {

        val ipHeaderLen = (request[0].toInt() and 0x0F) * 4
        val dnsOffset = ipHeaderLen + 8
        val dnsLength = length - dnsOffset

        val socket = DatagramSocket()
        socket.soTimeout = 3000
        service.protect(socket)

        val dnsServer = InetAddress.getByName("8.8.8.8")

        val req = DatagramPacket(request, dnsOffset, dnsLength, dnsServer, 53)
        socket.send(req)

        val buf = ByteArray(2048)
        val resp = DatagramPacket(buf, buf.size)
        socket.receive(resp)
        socket.close()

        val dnsResponse = buf.copyOf(resp.length)

        return buildIpUdpPacket(request, dnsResponse)
    }

    // ---------- Build valid IP + UDP packet ----------

    private fun buildIpUdpPacket(original: ByteArray, dns: ByteArray): ByteArray {

        val ipHeaderLen = (original[0].toInt() and 0x0F) * 4

        val srcIp = original.copyOfRange(12, 16)
        val dstIp = original.copyOfRange(16, 20)

        val srcPort = original.copyOfRange(ipHeaderLen, ipHeaderLen + 2)
        val dstPort = original.copyOfRange(ipHeaderLen + 2, ipHeaderLen + 4)

        val totalLen = 20 + 8 + dns.size

        val buffer = ByteBuffer.allocate(totalLen)
        buffer.order(ByteOrder.BIG_ENDIAN)

        // IP header
        buffer.put(0x45.toByte())
        buffer.put(0)
        buffer.putShort(totalLen.toShort())
        buffer.putInt(0)
        buffer.put(64.toByte())
        buffer.put(17.toByte())
        buffer.putShort(0)
        buffer.put(dstIp) // swap
        buffer.put(srcIp)

        // UDP header
        buffer.put(dstPort)
        buffer.put(srcPort)
        buffer.putShort((8 + dns.size).toShort())
        buffer.putShort(0)

        buffer.put(dns)

        val packet = buffer.array()

        // IP checksum
        val checksum = ipChecksum(packet, 0, 20)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = checksum.toByte()

        return packet
    }

    private fun ipChecksum(buf: ByteArray, offset: Int, len: Int): Int {
        var sum = 0
        var i = offset

        while (i < len) {
            val word =
                ((buf[i].toInt() and 0xFF) shl 8) or
                        (buf[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }

        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return sum.inv() and 0xFFFF
    }

    // ---------- Block response ----------

    private fun buildBlockedDnsResponse(
        request: ByteArray,
        length: Int
    ): ByteArray {

        val ipHeaderLen = (request[0].toInt() and 0x0F) * 4
        val dnsOffset = ipHeaderLen + 8

        val dns = request.copyOfRange(dnsOffset, length)
        dns[2] = 0x81.toByte()
        dns[3] = 0x83.toByte()

        return buildIpUdpPacket(request, dns)
    }
}
