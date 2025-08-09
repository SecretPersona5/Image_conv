import kotlinx.coroutines.*
import org.opencv.core.*
import kotlin.math.roundToInt

object PixelParallel {
    fun apply(gray: Mat, kernel: Mat): Mat {
        val h = gray.rows()
        val w = gray.cols()
        val result = Mat.zeros(h, w, gray.type())

        val k = kernel.rows()
        val off = k / 2
        val cores = Runtime.getRuntime().availableProcessors()

        // Кэш ядра чтобы не дергать kernel.get(i,j) в горячем цикле
        val K = Array(k) { DoubleArray(k) }
        for (i in 0 until k) for (j in 0 until k) {
            K[i][j] = kernel.get(i, j)[0]
        }

        runBlocking {
            for (y in off until h - off) {

                val rows = Array(k) { ByteArray(w) }
                for (i in 0 until k) rows[i] = ByteArray(w)

                for (i in 0 until k) {
                    gray.get(y + i - off, 0, rows[i])
                }

                val out = ByteArray(w)
                for (x in 0 until off) out[x] = 0
                for (x in w - off until w) out[x] = 0

                coroutineScope {
                    repeat(cores) { t ->
                        launch(Dispatchers.Default) {
                            var x = off + t
                            while (x < w - off) {
                                var sum = 0.0
                                for (i in 0 until k) {
                                    val row = rows[i]
                                    val Ki  = K[i]
                                    var j = 0
                                    var xx = x - off
                                    while (j < k) {
                                        sum += (row[xx].toInt() and 0xFF) * Ki[j]
                                        j++; xx++
                                    }
                                }
                                out[x] = sum.coerceIn(0.0, 255.0).roundToInt().toByte()
                                x += cores
                            }
                        }
                    }
                }

                result.put(y, 0, out)
            }
        }

        return result
    }
}
