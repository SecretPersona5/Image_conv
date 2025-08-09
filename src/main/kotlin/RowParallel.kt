import kotlinx.coroutines.*
import org.opencv.core.*
import kotlin.math.roundToInt

object RowParallel {
    fun apply(gray: Mat, kernel: Mat): Mat {
        val h = gray.rows()
        val w = gray.cols()
        val result = Mat.zeros(h, w, gray.type())
        val kSize = kernel.rows()
        val off   = kSize / 2
        val cores = Runtime.getRuntime().availableProcessors()
        val K = Array(kSize) { DoubleArray(kSize) }

        for (i in 0 until kSize) for (j in 0 until kSize) {
            K[i][j] = kernel.get(i, j)[0]
        }

        runBlocking {
            (0 until cores).map { t ->
                launch(Dispatchers.Default) {
                    val chunk = (h - 2*off + cores - 1) / cores
                    val y0 = off + t * chunk
                    val y1 = minOf(h - off, y0 + chunk)
                    val rows = Array(kSize) { ByteArray(w) }
                    var head = 0

                    for (i in 0 until kSize) {
                        gray.get(y0 + i - off, 0, rows[i])
                    }

                    val out = ByteArray(w)

                    for (y in y0 until y1) {
                        for (x in 0 until off) out[x] = 0
                        for (x in w - off until w) out[x] = 0

                        for (x in off until w - off) {
                            var sum = 0.0
                            for (i in 0 until kSize) {
                                val row = rows[(head + i) % kSize]
                                val Ki  = K[i]
                                var xx = x - off
                                var j = 0
                                while (j < kSize) {
                                    sum += (row[xx].toInt() and 0xFF) * Ki[j]
                                    xx++; j++
                                }
                            }
                            out[x] = sum.coerceIn(0.0, 255.0).roundToInt().toByte()
                        }

                        result.put(y, 0, out)

                        if (y + 1 < y1) {
                            val newSrcY = y + 1 + off
                            gray.get(newSrcY, 0, rows[head])
                            head = (head + 1) % kSize
                        }
                    }
                }
            }
                .joinAll()
        }
        return result
    }
}