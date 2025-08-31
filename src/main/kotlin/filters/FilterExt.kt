package filters

import org.opencv.core.CvType
import org.opencv.core.Mat

fun Filter.toCvKernel(): Mat {
    val r = kernel.size
    require(r > 0 && r % 2 == 1) { "Kernel rows must be odd" }
    val c = kernel[0].size
    require(c > 0 && c % 2 == 1 && c == r) { "Kernel must be square odd (got ${r}x${c})" }

    val m = Mat(r, c, CvType.CV_32F)
    val flat = FloatArray(r * c)
    var k = 0
    for (y in 0 until r) for (x in 0 until c) {
        flat[k++] = (kernel[y][x] * factor).toFloat()
    }
    m.put(0, 0, flat)
    return m
}
