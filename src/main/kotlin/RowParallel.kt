import kotlinx.coroutines.*
import org.opencv.core.Mat

object RowParallel {
    fun apply(gray: Mat, kernel: Mat): Mat {
        val h = gray.rows()
        val w = gray.cols()
        val dst = Mat.zeros(h, w, gray.type())

        val k = kernel.rows()
        val off = k / 2
        if (h == 0 || w == 0) return dst
        if (h < k || w < k) return dst

        val K = ConvolutionUtils.cacheKernel(kernel)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        runBlocking {
            val workers = cores
            val chunk = (h + workers - 1) / workers

            (0 until workers).map { t ->
                launch(Dispatchers.Default) {
                    val y0 = t * chunk
                    val y1 = minOf(h, y0 + chunk)
                    if (y0 >= y1) return@launch

                    val workStart = maxOf(off, y0)
                    val workEnd = minOf(h - off, y1)
                    if (workStart >= workEnd) return@launch

                    val rows = Array(k) { ByteArray(w) }
                    var curY = workStart
                    val firstBase = curY - off
                    var i = 0
                    while (i < k) {
                        gray.get(firstBase + i, 0, rows[i])
                        i++
                    }
                    val out = ByteArray(w)

                    while (curY < workEnd) {
                        ConvolutionUtils.run {
                            runBlocking { convolveRowInto(rows, K, off, w, out, cores = 1) }
                        }
                        dst.put(curY, 0, out)

                        if (curY + 1 < workEnd) {
                            val dropped = rows[0]
                            var s = 0
                            while (s < k - 1) {
                                rows[s] = rows[s + 1]
                                s++
                            }
                            rows[k - 1] = dropped
                            val nextSrcY = curY + off + 1
                            gray.get(nextSrcY, 0, rows[k - 1])
                        }
                        curY++
                    }
                }
            }.joinAll()
        }

        return dst
    }
}
