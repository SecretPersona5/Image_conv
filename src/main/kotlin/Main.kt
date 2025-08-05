import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.core.CvType
import java.awt.image.ImageConsumer
import java.io.InputStream
import java.io.File

fun main() {
    nu.pattern.OpenCV.loadLocally()

    val t0  = System.nanoTime()

    val inputPath = "src/main/resources/img/img2.jpg"
    val outputPath = "src/main/resources/img/output2.png"
    val imageName = File(inputPath).name

    val input = Imgcodecs.imread(inputPath)
    if (input.empty()) {
        println("NoFoto :O")
        return
    }

    val t1 = System.nanoTime()

    val gray = Mat()
    Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY)

    val t2 = System.nanoTime()

    val kernel = Mat(3, 3, CvType.CV_32F)
    kernel.put(0, 0,
        1.0/9, 1.0/9, 1.0/9,
        1.0/9,  1.0/9, 1.0/9,
        1.0/9, 1.0/9, 1.0/9
    )

    val result = Mat()
    Imgproc.filter2D(gray, result, -1, kernel)
    val t3 = System.nanoTime()

    val success = Imgcodecs.imwrite(outputPath, result)

    val t4 = System.nanoTime()

    val tLoad = (t1 - t0) / 1_000_000
    val tGray = (t2 - t1) / 1_000_000
    val tFilter = (t3 - t2) / 1_000_000
    val tSave = (t4 - t3) / 1_000_000
    val tTotal = (t4 - t0) / 1_000_000

    TimeLogger.log(imageName, tLoad, tGray, tFilter, tSave, tTotal)

    println("=== ВРЕМЯ ===")
    println("Загрузка         : $tLoad ms")
    println("В градации серого: $tGray ms")
    println("Свёртка          : $tFilter ms")
    println("Сохранение       : $tSave ms")
    println("Общее время      : $tTotal ms")

    if (success) {
        println("✅ Готово: output.png")
    } else {
        println("❌ Не удалось сохранить результат.")
    }
}
