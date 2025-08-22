import kotlinx.coroutines.*
import org.opencv.core.Mat

object PixelParallel {
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
            for (y in off until h - off) {
                val rows = Array(k) { ByteArray(w) }
                val base = y - off
                var i = 0
                while (i < k) {
                    gray.get(base + i, 0, rows[i])
                    i++
                }

                val out = ByteArray(w)
                ConvolutionUtils.convolveRowInto(rows, K, off, w, out, cores)
                dst.put(y, 0, out)
            }
        }
        return dst
    }
}
