import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


object Sequential {

    fun apply(srcGray: Mat, kernel: Mat): Mat {
        val gray = if (srcGray.type() == CvType.CV_8UC1) srcGray else {
            val tmp = Mat()
            Imgproc.cvtColor(srcGray, tmp, Imgproc.COLOR_BGR2GRAY)
            tmp
        }

        val kH = kernel.rows()
        val kW = kernel.cols()
        val off = kH / 2

        val h = gray.rows()
        val w = gray.cols()

        val src = ByteArray(h * w)
        gray.get(0, 0, src)

        val kArr = FloatArray(kH * kW)
        kernel.get(0, 0, kArr)

        val dst = ByteArray(h * w)

        for (y in 0 until h) {
            val yBase = y * w
            for (x in 0 until w) {
                var sum = 0.0
                var ki = 0
                for (ky in -off..off) {
                    val sy = clamp(y + ky, 0, h - 1)
                    val syBase = sy * w
                    for (kx in -off..off) {
                        val sx = clamp(x + kx, 0, w - 1)
                        val pix = src[syBase + sx].toInt() and 0xFF
                        val kv = kArr[ki++]
                        sum += pix * kv
                    }
                }
                val v = clamp(sum.roundToInt(), 0, 255)
                dst[yBase + x] = v.toByte()
            }
        }

        val out = Mat(h, w, CvType.CV_8UC1)
        out.put(0, 0, dst)
        return out
    }

    private fun clamp(x: Int, lo: Int, hi: Int) = max(lo, min(hi, x))
}
