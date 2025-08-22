import kotlinx.coroutines.*
import org.opencv.core.Mat

object GridParallel {
    fun apply(gray: Mat, kernel: Mat, blockSize: Int = 256, xWorkers: Int = Runtime.getRuntime().availableProcessors()): Mat {
        val h = gray.rows()
        val w = gray.cols()
        val dst = Mat.zeros(h, w, gray.type())

        val k = kernel.rows()
        require(k == kernel.cols()) { "Kernel must be square" }
        val off = k / 2

        if (h == 0 || w == 0) return dst
        if (h < k || w < k) return dst

        val K = ConvolutionUtils.cacheKernel(kernel)

        val outerCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val outerDispatcher = Dispatchers.Default.limitedParallelism(outerCores)

        runBlocking {
            val jobs = mutableListOf<Job>()

            var by = 0
            while (by < h) {
                val bh = minOf(blockSize, h - by)

                val blockY0 = by
                val blockH  = bh

                jobs += launch(outerDispatcher) {
                    val yStart = maxOf(off, blockY0)
                    val yEnd   = minOf(h - off, blockY0 + blockH)
                    if (yStart >= yEnd) return@launch

                    val rows = Array(k) { ByteArray(w) }
                    val base0 = yStart - off
                    for (i in 0 until k) gray.get(base0 + i, 0, rows[i])

                    val rowBuf = ByteArray(w)

                    var y = yStart
                    while (y < yEnd) {
                        val left = off
                        val right = w - off
                        val span = right - left
                        val workers = minOf(xWorkers.coerceAtLeast(1), span.coerceAtLeast(1))
                        val chunk = (span + workers - 1) / workers

                        coroutineScope {
                            repeat(workers) { wi ->
                                val xs = left + wi * chunk
                                val xe = minOf(right, xs + chunk)
                                if (xs < xe) {
                                    launch(Dispatchers.Default) {
                                        var x = xs
                                        while (x < xe) {
                                            rowBuf[x] = ConvolutionUtils.convolvePixel(rows, K, off, x)
                                            x++
                                        }
                                    }
                                }
                            }
                        }

                        dst.put(y, 0, rowBuf)

                        if (y + 1 < yEnd) {
                            val dropped = rows[0]
                            for (i in 0 until k - 1) rows[i] = rows[i + 1]
                            rows[k - 1] = dropped
                            val nextSrcY = y + off + 1
                            gray.get(nextSrcY, 0, rows[k - 1])
                        }
                        y++
                    }
                }
                by += bh
            }
            jobs.joinAll()
        }
        return dst
    }
}
