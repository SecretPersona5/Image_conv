package testutil

import nu.pattern.OpenCV
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import conv.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class OpenCVTestBase {
    @BeforeAll
    fun loadOpenCV() {
        OpenCV.loadLocally()
    }

    protected fun matU8(rows: Int, cols: Int, fill: (r: Int, c: Int) -> Int): Mat {
        val m = Mat(rows, cols, CvType.CV_8UC1)
        val buf = ByteArray(rows * cols)
        var idx = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                buf[idx++] = fill(r, c).coerceIn(0, 255).toByte()
            }
        }
        m.put(0, 0, buf)
        return m
    }

    protected fun kernelBox(size: Int): Mat {
        require(size % 2 == 1) { "Kernel must have odd size" }
        val k = Mat(size, size, CvType.CV_32F)
        val v = 1.0 / (size * size)
        val arr = FloatArray(size * size) { v.toFloat() }
        k.put(0, 0, arr)
        return k
    }

    protected fun kernelIdentity(size: Int): Mat {
        require(size % 2 == 1)
        val k = Mat(size, size, CvType.CV_32F)
        val arr = FloatArray(size * size) { 0f }
        val mid = size / 2 * size + size / 2
        arr[mid] = 1f
        k.put(0, 0, arr)
        return k
    }

    protected fun matsEqual(a: Mat, b: Mat): Boolean {
        if (a.rows() != b.rows() || a.cols() != b.cols() || a.type() != b.type()) return false
        val diff = Mat()
        Core.absdiff(a, b, diff)
        val cnt = Core.countNonZero(diff)
        return cnt == 0
    }
}
