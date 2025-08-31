import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

data class ImgJob(
    val name: String,
    val ext: String,
    val gray: Mat,
    val tLoadMs: Long,
    val tGrayMs: Long
)

data class OutJob(
    val name: String,
    val ext: String,
    val result: Mat,
    val tLoadMs: Long,
    val tGrayMs: Long,
    val tFilterMs: Long
)

object Pipeline {

    fun run(
        kernel: Mat,
        dirIn: String,
        dirOut: String,
        mode: String = "row",
        gridSize: Int = 128,
        convWorkers: Int = Runtime.getRuntime().availableProcessors(),
        saveWorkers: Int = 1,
        capacity: Int = 8
    ) = run(kernel, 0.0, dirIn, dirOut, mode, gridSize, convWorkers, saveWorkers, capacity)

    fun run(
        kernel: Mat,
        bias: Double,
        dirIn: String,
        dirOut: String,
        mode: String = "row",  // seq|row|col|grid|pix
        gridSize: Int = 128,
        convWorkers: Int = Runtime.getRuntime().availableProcessors(),
        saveWorkers: Int = 1,
        capacity: Int = 8
    ) = runBlocking {
        val inDir = File(dirIn)
        val outDir = File(dirOut)
        outDir.mkdirs()

        val files = inDir.listFiles { f -> f.isFile && !f.name.startsWith(".") }
            ?.sortedBy { it.name } ?: emptyList()

        if (files.isEmpty()) {
            println("⚠️ Нет файлов в $dirIn")
            return@runBlocking
        }

        val toConv  = Channel<ImgJob>(capacity)
        val toWrite = Channel<OutJob>(capacity)

        val reader = launch(Dispatchers.IO) {
            for (f in files) {
                val t0 = System.nanoTime()
                val bgr = Imgcodecs.imread(f.absolutePath)
                if (bgr.empty()) continue
                val t1 = System.nanoTime()

                val gray = Mat()
                Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
                bgr.release()
                val t2 = System.nanoTime()

                val base = f.nameWithoutExtension
                val ext = f.extension.lowercase().let { if (it.isNotEmpty()) ".$it" else ".png" }

                toConv.send(
                    ImgJob(
                        name = base,
                        ext = ext,
                        gray = gray,
                        tLoadMs = (t1 - t0) / 1_000_000,
                        tGrayMs = (t2 - t1) / 1_000_000
                    )
                )
            }
            toConv.close()
        }

        val convPool = (0 until convWorkers).map {
            launch(Dispatchers.Default) {
                for (job in toConv) {
                    val tf0 = System.nanoTime()
                    val result = when (mode) {
                        "seq" -> Sequential.apply(job.gray, kernel)
                        "row" -> RowParallel.apply(job.gray, kernel)
                        "col" -> ColParallel.apply(job.gray, kernel)
                        "grid"-> GridParallel.apply(job.gray, kernel, gridSize)
                        "pix" -> PixelParallel.apply(job.gray, kernel)
                        else  -> RowParallel.apply(job.gray, kernel)
                    }
                    if (bias != 0.0) {
                        Core.add(result, Scalar.all(bias), result)
                    }
                    val tf1 = System.nanoTime()

                    job.gray.release()

                    toWrite.send(
                        OutJob(
                            name = job.name,
                            ext = job.ext,
                            result = result,
                            tLoadMs = job.tLoadMs,
                            tGrayMs = job.tGrayMs,
                            tFilterMs = (tf1 - tf0) / 1_000_000
                        )
                    )
                }
            }
        }

        val writers = (0 until saveWorkers).map {
            launch(Dispatchers.IO) {
                for (oj in toWrite) {
                    val tw0 = System.nanoTime()
                    val outPath = File(outDir, oj.name + "_out" + oj.ext).absolutePath
                    val ok = Imgcodecs.imwrite(outPath, oj.result)
                    val tSave  = (System.nanoTime() - tw0) / 1_000_000
                    val tTotal = oj.tLoadMs + oj.tGrayMs + oj.tFilterMs + tSave

                    TimeLogger.log(oj.name + oj.ext, oj.tLoadMs, oj.tGrayMs, oj.tFilterMs, tSave, tTotal)

                    println("=== FILE: ${oj.name}${oj.ext} ===")
                    println("Загрузка         : ${oj.tLoadMs} ms")
                    println("В градации серого: ${oj.tGrayMs} ms")
                    println("Свёртка          : ${oj.tFilterMs} ms")
                    println("Сохранение       : ${tSave} ms")
                    println("Общее время      : ${tTotal} ms")
                    if (ok) {
                        println("✅ Готово: $outPath")
                    } else {
                        println("❌ Не удалось сохранить результат.")
                    }

                    oj.result.release()
                }
            }
        }

        reader.join()
        convPool.joinAll()
        toWrite.close()
        writers.joinAll()
    }
}
