package dev.rinalarm.spike.s3

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

const val RATE = 16000
const val FRAME = RATE / 50 // 20 ms

object Audio {
    fun frameDb(pcm: ShortArray, from: Int, len: Int): Double {
        var sum = 0.0
        for (i in from until minOf(from + len, pcm.size)) sum += pcm[i].toDouble() * pcm[i]
        val rms = sqrt(sum / len.coerceAtLeast(1)) / 32768.0
        return 20 * log10(rms.coerceAtLeast(1e-6))
    }

    /** Speech start/end sample indices by an energy VAD (noise floor = 10th-percentile frame), or null. */
    fun speechBounds(pcm: ShortArray): Pair<Int, Int>? {
        val n = pcm.size / FRAME
        if (n < 5) return null
        val db = DoubleArray(n) { frameDb(pcm, it * FRAME, FRAME) }
        val floor = db.sorted()[n / 10]
        val thr = maxOf(floor + 12, -55.0)
        val voiced = BooleanArray(n) { db[it] > thr }
        // Start = first run of 3 voiced frames (60 ms); end = last voiced frame.
        val start = (0 until n - 2).firstOrNull { voiced[it] && voiced[it + 1] && voiced[it + 2] } ?: return null
        val end = (n - 1 downTo start).first { voiced[it] }
        return start * FRAME to (end + 1) * FRAME
    }

    fun peakDb(pcm: ShortArray): Double =
        20 * log10((pcm.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0).coerceAtLeast(1) / 32768.0)

    /** Scales so the peak sits at [peakDb] dBFS (gain only, no compression). */
    fun normalize(pcm: ShortArray, peakDb: Double): ShortArray {
        val peak = pcm.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
        if (peak == 0) return pcm
        val g = 32767.0 * Math.pow(10.0, peakDb / 20) / peak
        return ShortArray(pcm.size) { (pcm[it] * g).toInt().coerceIn(-32768, 32767).toShort() }
    }

    fun writeWav(file: File, pcm: ShortArray) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort(it) }
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2)
            .putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(pcm.size * 2)
        file.parentFile?.mkdirs()
        file.outputStream().use { it.write(h.array()); it.write(data.array()) }
    }

    /** Reads 16 kHz mono 16-bit PCM WAV, walking chunks so WAVs from other tools also load. */
    fun readWav(file: File): ShortArray {
        RandomAccessFile(file, "r").use { f ->
            val b = ByteArray(f.length().toInt()); f.readFully(b)
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            var pos = 12
            while (pos + 8 <= b.size) {
                val id = String(b, pos, 4); val size = bb.getInt(pos + 4)
                if (id == "fmt ") {
                    val ch = bb.getShort(pos + 10).toInt(); val rate = bb.getInt(pos + 12); val bits = bb.getShort(pos + 22).toInt()
                    require(ch == 1 && rate == RATE && bits == 16) { "${file.name}: need 16 kHz mono 16-bit, got $rate Hz ${ch}ch ${bits}bit" }
                }
                if (id == "data") {
                    val len = minOf(size, b.size - pos - 8) / 2
                    return ShortArray(len) { bb.getShort(pos + 8 + it * 2) }
                }
                pos += 8 + size + (size and 1)
            }
            error("${file.name}: no data chunk")
        }
    }

    fun toBytes(pcm: ShortArray, from: Int, to: Int): ByteArray {
        val bb = ByteBuffer.allocate((to - from) * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in from until to) bb.putShort(pcm[i])
        return bb.array()
    }
}
