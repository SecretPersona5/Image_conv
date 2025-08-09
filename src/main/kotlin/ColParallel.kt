import org.opencv.core.Core
import org.opencv.core.Mat

object ColParallel {
    fun apply(gray: Mat, kernel: Mat): Mat {
        val grayT   = Mat()
        val kernelT = Mat()
        val resT    = Mat()
        val res     = Mat()

        Core.transpose(gray, grayT)
        Core.transpose(kernel, kernelT)

        val tmp = RowParallel.apply(grayT, kernelT)

        Core.transpose(tmp, res)
        return res
    }
}
