package conv

import org.opencv.core.Mat
import kotlin.math.roundToInt
import kotlinx.coroutines.*

object ConvolutionUtils {
    fun cacheKernel(kernel: Mat): Array<DoubleArray> {
        val k = kernel.rows()
        require(k == kernel.cols()) { "Kernel must be square" }
        return Array(k) { i ->
            DoubleArray(k) { j -> kernel.get(i, j)[0] }
        }
    }

    fun convolvePixel(rows: Array<ByteArray>, K: Array<DoubleArray>, off: Int, x: Int): Byte {
        val k = K.size
        var sum = 0.0
        var i = 0
        while (i < k) {
            val row = rows[i]
            val Ki = K[i]
            var j = 0
            var xx = x - off
            while (j < k) {
                sum += (row[xx].toInt() and 0xFF) * Ki[j]
                j++; xx++
            }
            i++
        }
        return sum.coerceIn(0.0, 255.0).roundToInt().toByte()
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun convolveRowInto(
        rows: Array<ByteArray>,
        K: Array<DoubleArray>,
        off: Int,
        w: Int,
        out: ByteArray,
        cores: Int = 1
    ) {

        for (x in 0 until off) out[x] = 0
        for (x in (w - off) until w) out[x] = 0
        if (w <= 2 * off) return

        if (cores <= 1) {
            var x = off
            while (x < w - off) {
                out[x] = convolvePixel(rows, K, off, x)
                x++
            }
        } else {
            val tasks = mutableListOf<Job>()
            val chunk = ((w - 2 * off) + cores - 1) / cores
            var x0 = off
            repeat(cores) {
                val start = x0
                val end = minOf(w - off, start + chunk)
                x0 = end
                if (start < end) {
                    tasks += GlobalScope.launch(Dispatchers.Default) {
                        var x = start
                        while (x < end) {
                            out[x] = convolvePixel(rows, K, off, x)
                            x++
                        }
                    }
                }
            }
            tasks.joinAll()
        }
    }
}
