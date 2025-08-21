import kotlinx.coroutines.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object RowParallel {
    fun apply(gray: Mat, kernel: Mat): Mat {
        val h = gray.rows(); val w = gray.cols()
        val k = kernel.rows(); val off = k / 2
        val dst = Mat.zeros(h, w, gray.type())

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val prevThreads = Core.getNumThreads()
        try {
            runBlocking {
                (0 until cores).map { t ->
                    launch(Dispatchers.Default) {
                        val chunk = (h + cores - 1) / cores
                        val y0 = t * chunk
                        val y1 = minOf(h, y0 + chunk)
                        if (y0 >= y1) return@launch

                        val srcY0 = maxOf(0, y0 - off)
                        val srcY1 = minOf(h, y1 + off)

                        val srcROI = gray.rowRange(srcY0, srcY1).colRange(0, w)
                        val tmp = Mat()
                        Imgproc.filter2D(
                            srcROI, tmp, -1, kernel,
                            Point(-1.0, -1.0), 0.0, Core.BORDER_CONSTANT
                        )

                        val from = tmp.rowRange(y0 - srcY0, y0 - srcY0 + (y1 - y0)).colRange(0, w)
                        from.copyTo(dst.rowRange(y0, y1).colRange(0, w))
                    }
                }.joinAll()
            }
        } finally {
            Core.setNumThreads(prevThreads)
        }
        return dst
    }
}
