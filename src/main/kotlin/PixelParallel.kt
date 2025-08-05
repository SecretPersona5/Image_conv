import kotlinx.coroutines.*
import org.opencv.core.*

fun applyConvolutionPerPixel(gray: Mat, kernel: Mat): Mat {
    val height = gray.rows()
    val width = gray.cols()
    val result = Mat.zeros(height, width, gray.type())

    val kSize = kernel.rows()
    val kOffset = kSize / 2

    runBlocking {
        val jobs = mutableListOf<Job>()

        val numThreads = Runtime.getRuntime().availableProcessors()
        val chunkSize = (height - 2 * kOffset) / numThreads

        for (t in 0 until numThreads) {
            val startY = kOffset + t * chunkSize
            val endY = if (t == numThreads - 1) height - kOffset else startY + chunkSize

            val job = launch(Dispatchers.Default) {
                for (y in startY until endY) {
                    for (x in kOffset until width - kOffset) {
                        var sum = 0.0
                        for (ky in -kOffset..kOffset) {
                            for (kx in -kOffset..kOffset) {
                                val pixel = gray.get(y + ky, x + kx)[0]
                                val weight = kernel.get(ky + kOffset, kx + kOffset)[0]
                                sum += pixel * weight
                            }
                        }
                        result.put(y, x, sum.coerceIn(0.0, 255.0))
                    }
                }
            }

            jobs.add(job)
        }

        jobs.joinAll()
    }

    return result
}
