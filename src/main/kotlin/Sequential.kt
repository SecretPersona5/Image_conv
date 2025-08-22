import kotlinx.coroutines.runBlocking
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object Sequential {
    fun apply(srcGray: Mat, kernel: Mat): Mat {
        val gray = if (srcGray.type() == CvType.CV_8UC1) srcGray else {
            val tmp = Mat()
            Imgproc.cvtColor(srcGray, tmp, Imgproc.COLOR_BGR2GRAY)
            tmp
        }

        val h = gray.rows()
        val w = gray.cols()
        val k = kernel.rows()
        require(k == kernel.cols()) { "Kernel must be square" }
        val off = k / 2

        val dst = Mat.zeros(h, w, CvType.CV_8UC1)
        if (h == 0 || w == 0) return dst
        if (h < k || w < k) return dst


        val K = ConvolutionUtils.cacheKernel(kernel)


        val rows = Array(k) { ByteArray(w) }
        val out = ByteArray(w)

        var y = off
        var base = y - off
        var i = 0
        while (i < k) {
            gray.get(base + i, 0, rows[i])
            i++
        }

                runBlocking {
            ConvolutionUtils.convolveRowInto(rows, K, off, w, out, cores = 1)
        }
        dst.put(y, 0, out)
        y++

        while (y < h - off) {
            val dropped = rows[0]
            var s = 0
            while (s < k - 1) {
                rows[s] = rows[s + 1]
                s++
            }
            rows[k - 1] = dropped
            val nextSrcY = y + off
            gray.get(nextSrcY, 0, rows[k - 1])

            runBlocking {
                ConvolutionUtils.convolveRowInto(rows, K, off, w, out, cores = 1)
            }
            dst.put(y, 0, out)
            y++
        }
        return dst
    }
}
