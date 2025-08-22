import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

fun main(args: Array<String>) {
    nu.pattern.OpenCV.loadLocally()

    val mode = args.getOrNull(0) ?: "grid"
    val inputPath = args.getOrNull(1) ?: "src/main/resources/img/img3.jpg"
    val gridSize = args.getOrNull(2)?.toIntOrNull() ?: 128

    val t0  = System.nanoTime()

    val outputPath = "src/main/resources/img/output3.png"
    val imageName  = File(inputPath).name

    val input = Imgcodecs.imread(inputPath)
    if (input.empty()) {
        println("NoFoto :O")
        return
    }
    val t1 = System.nanoTime()

    val gray = Mat()
    Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY)
    val t2 = System.nanoTime()

    val kernel = Mat(3, 3, CvType.CV_32F).apply {
        put(0, 0,
            1.0/9, 1.0/9, 1.0/9,
            1.0/9, 1.0/9, 1.0/9,
            1.0/9, 1.0/9, 1.0/9
        )
    }

    val result = when (mode) {
        "seq" -> Sequential.apply(gray, kernel)
        "row" -> RowParallel.apply(gray, kernel)
        "grid" -> GridParallel.apply(gray, kernel, gridSize)
        "pix" -> PixelParallel.apply(gray, kernel)
        else  -> ColParallel.apply(gray, kernel)
    }
    val t3 = System.nanoTime()

    val success = Imgcodecs.imwrite(outputPath, result)
    val t4 = System.nanoTime()

    val tLoad   = (t1 - t0) / 1_000_000
    val tGray   = (t2 - t1) / 1_000_000
    val tFilter = (t3 - t2) / 1_000_000
    val tSave   = (t4 - t3) / 1_000_000
    val tTotal  = (t4 - t0) / 1_000_000

    TimeLogger.log(imageName, tLoad, tGray, tFilter, tSave, tTotal)

    println("=== MODE: $mode ===")
    println("Загрузка         : $tLoad ms")
    println("В градации серого: $tGray ms")
    println("Свёртка          : $tFilter ms")
    println("Сохранение       : $tSave ms")
    println("Общее время      : $tTotal ms")

    if (success) {
        println("✅ Готово: $outputPath")
    } else {
        println("❌ Не удалось сохранить результат.")
    }
}
