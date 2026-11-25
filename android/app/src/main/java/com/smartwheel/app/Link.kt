package com.smartwheel.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/** Отправка состояния на ПК по UDP ~100 раз в секунду и замер задержки по эху. */
class Link {
    @Volatile private var socket: DatagramSocket? = null
    @Volatile var sent = 0L
    @Volatile var rttMs = -1f
    @Volatile var error: String? = null
    @Volatile var value = 0f

    val running: Boolean get() = socket != null

    fun start(host: String, port: Int) {
        stop()
        sent = 0; rttMs = -1f; error = null
        val s = DatagramSocket()
        // Метка приоритета трафика (WMM): роутеры с поддержкой QoS отправляют такие пакеты вне очереди
        try { s.trafficClass = 0xB8 } catch (_: Exception) {}
        socket = s

        thread(isDaemon = true, name = "sw-send") {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                val addr = InetAddress.getByName(host)
                val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                var seq = 0
                var next = System.nanoTime()
                while (socket === s) {
                    buf.clear()
                    buf.put('S'.code.toByte()).put('W'.code.toByte()).put(1).put(0)
                    buf.putInt(seq++)
                    buf.putInt((System.nanoTime() / 1000).toInt())
                    buf.putFloat(value)
                    s.send(DatagramPacket(buf.array(), 16, addr, port))
                    sent++
                    next += 10_000_000L // 10 мс = 100 Гц
                    val wait = next - System.nanoTime()
                    if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                    else next = System.nanoTime()
                }
            } catch (e: Exception) {
                if (socket === s) { error = e.message ?: e.toString(); stop() }
            }
        }

        thread(isDaemon = true, name = "sw-recv") {
            val rb = ByteArray(64)
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                while (socket === s) {
                    val p = DatagramPacket(rb, rb.size)
                    s.receive(p)
                    if (p.length >= 16) {
                        val sentUs = ByteBuffer.wrap(rb, 0, p.length).order(ByteOrder.LITTLE_ENDIAN).getInt(8)
                        val nowUs = (System.nanoTime() / 1000).toInt()
                        rttMs = (nowUs - sentUs) / 1000f
                    }
                }
            } catch (e: Exception) {
                if (socket === s) { error = e.message ?: e.toString(); stop() }
            }
        }
    }

    fun stop() {
        val s = socket
        socket = null
        s?.close()
    }
}
