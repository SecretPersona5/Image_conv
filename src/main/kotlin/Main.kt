import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

import filters.filterList
import filters.toCvKernel

private data class SelectedFilter(val index: Int, val name: String, val kernel: Mat, val bias: Double)

private fun listImages(dir: String): List<File> =
    File(dir).listFiles { f ->
        f.isFile && f.name.matches(Regex("(?i).*\\.(png|jpe?g|bmp|tif|tiff)$"))
    }?.sortedBy { it.name } ?: emptyList()

private fun ask(prompt: String, default: String? = null): String {
    print(if (default == null) "$prompt: " else "$prompt [$default]: ")
    val s = readLine()?.trim().orEmpty()
    return if (s.isEmpty() && default != null) default else s
}

private fun askInt(prompt: String, default: Int): Int =
    ask(prompt, default.toString()).toIntOrNull() ?: default

private fun askYesNo(prompt: String, defaultYes: Boolean = false): Boolean {
    val def = if (defaultYes) "Y/n" else "y/N"
    while (true) {
        val s = ask(prompt, def).lowercase()
        if (s.isBlank()) return defaultYes
        if (s in listOf("y", "yes", "д", "да")) return true
        if (s in listOf("n", "no", "н", "нет")) return false
        println("Введите y/yes или n/no.")
    }
}

private fun askDir(prompt: String, default: String): String {
    while (true) {
        val p = ask(prompt, default)
        val f = File(p)
        if (f.exists() && f.isDirectory) return f.absolutePath
        if (!f.exists()) {
            if (askYesNo("Папки '$p' нет. Создать?", defaultYes = true)) {
                f.mkdirs(); return f.absolutePath
            }
        }
        println("Нужна существующая папка (или согласие на создание).")
    }
}

private fun chooseImage(defaultDir: String): String {
    val imgs = listImages(defaultDir)
    if (imgs.isNotEmpty()) {
        println("Найдены изображения в $defaultDir:")
        imgs.forEachIndexed { i, f -> println("  [$i] ${f.name}") }
        println("Введите номер или полный путь (Enter — 0):")
        while (true) {
            val s = readLine()?.trim().orEmpty()
            if (s.isEmpty()) return imgs[0].absolutePath
            val idx = s.toIntOrNull()
            if (idx != null && idx in imgs.indices) return imgs[idx].absolutePath
            val f = File(s)
            if (f.isFile) return f.absolutePath
            println("Не понял ввод. Попробуй ещё раз.")
        }
    } else {
        println("⚠️ В $defaultDir изображений не найдено.")
        while (true) {
            val p = ask("Укажи путь к изображению (png/jpg/bmp/tif)")
            val f = File(p)
            if (f.isFile) return f.absolutePath
            println("Файл не найден. Повтори.")
        }
    }
}

private fun chooseMode(): String {
    println("Режимы свёртки:")
    println("  [1] seq")
    println("  [2] row")
    println("  [3] col")
    println("  [4] grid")
    println("  [5] pix")
    val s = ask("Выбери режим", "row").lowercase()
    return when (s) {
        "1", "seq" -> "seq"
        "2", "", "row" -> "row"
        "3", "col" -> "col"
        "4", "grid" -> "grid"
        "5", "pix" -> "pix"
        else -> "row"
    }
}

private fun chooseFilter(): SelectedFilter {
    println("Доступные фильтры:")
    filterList.forEachIndexed { i, (name, _) ->
        println("  [${i + 1}] $name")
    }
    val def = 1
    while (true) {
        val s = ask("Выбери фильтр", def.toString()).trim()
        val idx = s.toIntOrNull()
        if (idx != null && idx in 1..filterList.size) {
            val (name, f) = filterList[idx - 1]
            return SelectedFilter(idx - 1, name, f.toCvKernel(), f.bias)
        }
        println("Введи число 1..${filterList.size}.")
    }
}

fun main(args: Array<String>) {
    nu.pattern.OpenCV.loadLocally()

    val arg0 = args.getOrNull(0)
    val isInteractive = arg0 == null || arg0 in listOf("-i", "--interactive", "i", "interactive")

    if (arg0 == "pipe") {
        val inputDir = args.getOrNull(1) ?: "src/main/resources/img"
        val outputDir = args.getOrNull(2) ?: "src/main/resources/img/out"
        val convMode = args.getOrNull(3) ?: "row"
        val gridSize = args.getOrNull(4)?.toIntOrNull() ?: 128
        val convWorkers = args.getOrNull(5)?.toIntOrNull() ?: Runtime.getRuntime().availableProcessors()
        val saveWorkers = args.getOrNull(6)?.toIntOrNull() ?: 1
        val capacity = args.getOrNull(7)?.toIntOrNull() ?: 8

        val selected = chooseFilter()

        val t0 = System.nanoTime()
        Pipeline.run(
            selected.kernel, selected.bias,
            inputDir, outputDir,
            convMode, gridSize, convWorkers, saveWorkers, capacity
        )
        val totalMs = (System.nanoTime() - t0) / 1_000_000
        println("МегаТатальноеВремя     : $totalMs ms")
        return
    }

    val defaultDir = "src/main/resources/img"
    val outDirDefault = "$defaultDir/out"

    if (isInteractive) {
        val doPipe = askYesNo("Хотите обработать все изображения в папке (pipe)?", defaultYes = false)
        if (doPipe) {
            val inputDir = askDir("Входная папка", defaultDir)
            val outputDir = askDir("Выходная папка", outDirDefault)
            val convMode = chooseMode()
            val gridSize = if (convMode == "grid") askInt("Размер блока grid", 128) else 128
            val convWorkers = askInt("Сколько потоков свёртки (convWorkers)", Runtime.getRuntime().availableProcessors())
            val saveWorkers = askInt("Сколько потоков сохранения (saveWorkers)", 1)
            val capacity = askInt("Размер каналов (capacity)", 8)

            val selected = chooseFilter()
            println("Фильтр: ${selected.name}")

            val t0 = System.nanoTime()
            Pipeline.run(
                selected.kernel, selected.bias,
                inputDir, outputDir,
                convMode, gridSize, convWorkers, saveWorkers, capacity
            )
            val totalMs = (System.nanoTime() - t0) / 1_000_000
            println("Готово. МегаТатальноеВремя: $totalMs ms")
            return
        }
    }

    val outDir = outDirDefault
    val inputPath: String
    val mode: String
    var gridSize = 128
    val outputPath: String
    val selected: SelectedFilter

    if (isInteractive) {
        inputPath = chooseImage(defaultDir)
        mode = chooseMode()
        if (mode == "grid") gridSize = askInt("Размер блока grid", 128)
        selected = chooseFilter()

        File(outDir).mkdirs()
        val base = File(inputPath).nameWithoutExtension
        val ext = File(inputPath).extension.lowercase().let { if (it.isNotEmpty()) ".$it" else ".png" }
        outputPath = File(outDir, "${base}_${mode}_${selected.name}$ext").absolutePath
        println("Вход:  $inputPath")
        println("Режим: $mode${if (mode == "grid") " (block=$gridSize)" else ""}")
        println("Фильтр: ${selected.name}")
        println("Выход: $outputPath")
    } else {
        mode = arg0 ?: "row"
        inputPath = args.getOrNull(1) ?: "$defaultDir/img1.png"
        gridSize = args.getOrNull(2)?.toIntOrNull() ?: 128

        val (defName, defFilter) = filterList.first()
        selected = SelectedFilter(0, defName, defFilter.toCvKernel(), defFilter.bias)

        val base = File(inputPath).nameWithoutExtension
        val ext = File(inputPath).extension.lowercase().let { if (it.isNotEmpty()) ".$it" else ".png" }
        outputPath = "$defaultDir/${base}_${mode}_${defName}$ext"
    }

    val t0 = System.nanoTime()
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
    val result = when (mode) {
        "seq" -> Sequential.apply(gray, selected.kernel)
        "row" -> RowParallel.apply(gray, selected.kernel)
        "col" -> ColParallel.apply(gray, selected.kernel)
        "grid"-> GridParallel.apply(gray, selected.kernel, gridSize)
        "pix" -> PixelParallel.apply(gray, selected.kernel)
        else  -> RowParallel.apply(gray, selected.kernel)
    }
    if (selected.bias != 0.0) {
        Core.add(result, Scalar.all(selected.bias), result)
    }

    val t3 = System.nanoTime()
    File(outDir).mkdirs()
    val success = Imgcodecs.imwrite(outputPath, result)
    val t4 = System.nanoTime()
    val tLoad = (t1 - t0) / 1_000_000
    val tGray = (t2 - t1) / 1_000_000
    val tFilter = (t3 - t2) / 1_000_000
    val tSave = (t4 - t3) / 1_000_000
    val tTotal = (t4 - t0) / 1_000_000
    TimeLogger.log(imageName, tLoad, tGray, tFilter, tSave, tTotal)
    println("=== MODE: $mode ===")
    println("Загрузка         : $tLoad ms")
    println("В градации серого: $tGray ms")
    println("Свёртка          : $tFilter ms")
    println("Сохранение       : $tSave ms")
    println("Общее время      : $tTotal ms")
    if (success) println("✅ Готово: $outputPath") else println("❌ Не удалось сохранить результат.")
}
