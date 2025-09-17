// ./gradlew run --args='bench src/main/resources/img out/benchmarks --modes=row,seq,pix,grid,col --filters=all --tag=try1 --repeats=1 --cleanup'
package Benchmark

import filters.filterList
import filters.toCvKernel
import org.opencv.core.Mat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private fun parseCsvList(arg: String?) =
    arg?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

private fun hasFlag(args: Array<String>, name: String): Boolean =
    args.any { it == name || it == "$name=true" }

fun main(args: Array<String>) {
    try { Class.forName("nu.pattern.OpenCV").getMethod("loadLocally").invoke(null) }
    catch (_: Throwable) { System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME) }

    val i0 = if (args.firstOrNull() == "bench") 1 else 0

    val inDir  = File(args.getOrNull(i0) ?: "src/main/resources/img").also {
        require(it.isDirectory) { "Input dir not found: ${it.absolutePath}" }
    }

    val outRoot = File(args.getOrNull(i0 + 1) ?: "out/benchmarks").apply { mkdirs() }

    val cleanup = hasFlag(args, "--cleanup") || hasFlag(args, "--rm")

    val modes = parseCsvList(args.find { it.startsWith("--modes") }?.substringAfter('='))
        .ifEmpty { listOf("seq","row","col","grid","pix") }

    val filterSel = args.find { it.startsWith("--filters") }?.substringAfter('=') ?: "1"
    val filterIdxs: List<Int> = when (filterSel) {
        "all" -> filterList.indices.map { it + 1 }
        else  -> filterSel.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..filterList.size }
    }

    val grids = parseCsvList(args.find { it.startsWith("--grid") }?.substringAfter('=')).mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(128) }
    val convs = parseCsvList(args.find { it.startsWith("--conv") }?.substringAfter('=')).mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(Runtime.getRuntime().availableProcessors()) }
    val saves = parseCsvList(args.find { it.startsWith("--save") }?.substringAfter('=')).mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(1) }
    val caps  = parseCsvList(args.find { it.startsWith("--cap") }?.substringAfter('=')).mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(8) }
    val reps  = args.find { it.startsWith("--repeats") }?.substringAfter('=')?.toIntOrNull() ?: 3
    val tag   = args.find { it.startsWith("--tag") }?.substringAfter('=') ?: ""

    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val benchDir = File("build/bench").apply { mkdirs() }
    val outCsv = File(benchDir, "pipeline_runs.csv").apply {
        if (!exists() || length() == 0L) {
            writeText("timestamp,tag,mode,filter,grid,convWorkers,saveWorkers,capacity,repeats,images,total_ms_avg,total_ms_min,total_ms_max,status\n")
        }
    }

    val imagesCount = inDir.listFiles { f -> f.isFile && !f.name.startsWith(".") }?.size ?: 0
    println("== BENCH == in=${inDir.absolutePath} out=${outRoot.absolutePath} cleanup=$cleanup")

    for (mode in modes) for (fi in filterIdxs) for (grid in grids) for (conv in convs) for (save in saves) for (cap in caps) {
        val (fname, f) = filterList[fi - 1]
        val kernel: Mat = f.toCvKernel()
        val totals = mutableListOf<Long>()
        try {
            repeat(reps) { r ->
                val runTag = listOfNotNull(tag.takeIf { it.isNotEmpty() }, mode, fname, "g$grid","c$conv","s$save","p$cap","r${r+1}").joinToString("-")
                val comboOut = File(outRoot, runTag).apply { mkdirs() }

                val t0 = System.nanoTime()
                Pipeline.run(kernel, inDir.absolutePath, comboOut.absolutePath, mode, grid, conv, save, cap)
                val totalMs = (System.nanoTime() - t0) / 1_000_000
                totals += totalMs

                if (cleanup) {
                    if (comboOut.canonicalPath.startsWith(outRoot.canonicalPath)) {
                        comboOut.deleteRecursively()
                    }
                }
            }
            val avg = totals.average()
            val min = totals.minOrNull() ?: 0
            val max = totals.maxOrNull() ?: 0
            outCsv.appendText("$stamp,$tag,$mode,$fname,$grid,$conv,$save,$cap,$reps,$imagesCount,${"%.1f".format(avg)},$min,$max,OK\n")
            println("[ok] $mode/$fname g=$grid c=$conv s=$save p=$cap -> avg=${"%.1f".format(avg)} ms")
        } catch (e: Throwable) {
            System.err.println("[ERROR] $mode/$fname g=$grid c=$conv s=$save p=$cap: ${e.message}")
            outCsv.appendText("$stamp,$tag,$mode,$fname,$grid,$conv,$save,$cap,$reps,$imagesCount,,, ,FAIL\n")
        } finally {
            kernel.release()
        }
    }

    println("Summary CSV: ${outCsv.absolutePath}")
}
