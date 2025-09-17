@file:Suppress("SameParameterValue")

package testutil

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.io.path.createTempDirectory
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import filters.filterList
import kotlin.math.exp
import conv.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MainTest {

    private val MAINCLASS = System.getProperty("app.main.class", "MainKt")

    @BeforeAll
    fun loadOpenCv() {
        try {
            val clazz = Class.forName("nu.pattern.OpenCV")
            val loadShared = clazz.getMethod("loadShared")
            val loadLocally = clazz.getMethod("loadLocally")
            try { loadShared.invoke(null) } catch (_: Throwable) { loadLocally.invoke(null) }
        } catch (_: Throwable) {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
        }
        File("src/main/resources/img").mkdirs()
        File("src/main/resources/img/out").mkdirs()
    }

    private fun invokeMain(vararg args: String): Pair<String, String> {
        val cls = Class.forName(MAINCLASS)
        val m = cls.getMethod("main", Array<String>::class.java)
        val outBak = System.out
        val errBak = System.err
        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()
        System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
        System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))
        try { m.invoke(null, args as Any) } finally {
            System.setOut(outBak); System.setErr(errBak)
        }
        return outBuf.toString(Charsets.UTF_8) to errBuf.toString(Charsets.UTF_8)
    }

    private fun invokeMainWithInput(input: String, vararg args: String): Pair<String, String> {
        val cls = Class.forName(MAINCLASS)
        val m = cls.getMethod("main", Array<String>::class.java)
        val inBak = System.`in`
        val outBak = System.out
        val errBak = System.err
        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()
        System.setIn(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
        System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
        System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))
        try { m.invoke(null, args as Any) } finally {
            System.setIn(inBak); System.setOut(outBak); System.setErr(errBak)
        }
        return outBuf.toString(Charsets.UTF_8) to errBuf.toString(Charsets.UTF_8)
    }

    private fun makeGrayPng(dir: File, name: String = "in.png", w: Int = 64, h: Int = 64): File {
        val img = Mat(h, w, CvType.CV_8U)
        val row = ByteArray(w) { i -> (i * 255 / (w - 1)).toByte() }
        for (y in 0 until h) img.put(y, 0, row)
        val f = File(dir, name)
        assertTrue(Imgcodecs.imwrite(f.absolutePath, img))
        return f
    }

    private fun makeColor(dir: File, name: String, w: Int = 32, h: Int = 32): File {
        val img = Mat(h, w, CvType.CV_8UC3, Scalar(10.0, 20.0, 30.0))
        val f = File(dir, name)
        assertTrue(Imgcodecs.imwrite(f.absolutePath, img))
        return f
    }

    private fun ensureDir(path: String) = File(path).apply { mkdirs() }

    private fun expectedOutNonInteractive(defaultDir: File, input: File, mode: String): File {
        val base = input.nameWithoutExtension
        val defName = filterList.first().first
        val ext = ".${input.extension.lowercase().ifEmpty { "png" }}"
        return File(defaultDir, "${base}_${mode}_${defName}$ext")
    }

    private fun readBytes(m: Mat): ByteArray {
        val n = m.rows() * m.cols() * m.channels()
        val buf = ByteArray(n)
        m.get(0, 0, buf)
        return buf
    }

    private fun avg(bytes: ByteArray): Double =
        bytes.fold(0L) { acc, b -> acc + (b.toInt() and 0xFF) }.toDouble() / bytes.size

    private fun ensureDefaultImgNamedImg1(): File {
        val defaultDir = ensureDir("src/main/resources/img")
        val f = File(defaultDir, "img1.png")
        if (!f.exists()) {
            val tmp = createTempDirectory("img1_src").toFile()
            val src = makeGrayPng(tmp, "img1.png", 32, 24)
            src.copyTo(f, overwrite = true)
        }
        return f
    }

    @Test
    fun `interactive pipe askDir reasks on file and deny-create then ok`() {
        val base = createTempDirectory("pipe_edges").toFile()
        val fileInsteadOfDir = File(base, "not_dir.txt").apply { writeText("x") }
        val inDir = File(base, "IN").apply { mkdirs() }
        val outValid = File(base, "OUT").apply { mkdirs() }
        makeColor(inDir, "p.png", 32, 32)
        val outNonExist = File(base, "TO_CREATE").absolutePath
        val feed = buildString {
            append("y\n")
            append(fileInsteadOfDir.absolutePath).append('\n')
            append(inDir.absolutePath).append('\n')
            append(outNonExist).append('\n')
            append("n\n")
            append(outValid.absolutePath).append('\n')
            append("4\n")
            append("11\n")
            append("2\n")
            append("2\n")
            append("3\n")
            append("xxx\n")
            append("1\n")
        }
        val (stdout, _) = invokeMainWithInput(feed)
        assertContains(stdout, "Готово.")
        assertTrue(File(outValid, "p_out.png").exists())
    }


    @Test
    fun `row mode creates output png in default dir`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val tmp = createTempDirectory("main_row_in").toFile()
        val input = makeGrayPng(tmp, "img.png", 48, 32)
        val expected = expectedOutNonInteractive(defaultDir, input, "row")
        if (expected.exists()) expected.delete()
        val (stdout, _) = invokeMain("row", input.absolutePath, "7")
        assertContains(stdout, "=== MODE: row ===")
        assertTrue(expected.exists())
        val mat = Imgcodecs.imread(expected.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
        assertTrue(!mat.empty())
        assertEquals(32, mat.rows())
        assertEquals(48, mat.cols())
        expected.delete()
    }

    @Test
    fun `interactive not-pipe chooseImage list garbage then default index and grid`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val outDir = ensureDir("src/main/resources/img/out")
        val a = makeColor(defaultDir, "aaa_first.png", 24, 24)
        val t = makeColor(defaultDir, "zzz_last.png", 24, 24)
        val defName = filterList.first().first
        val expected = File(outDir, "aaa_first_grid_${defName}.png")
        if (expected.exists()) expected.delete()
        val feed = buildString {
            append("n\n")
            append("abracadabra\n")
            append("\n")
            append("4\n")
            append("7\n")
            append("\n")
        }
        val (stdout, _) = invokeMainWithInput(feed)
        assertContains(stdout, "Готово")
        a.delete()
        File(defaultDir, "zzz_last.png").delete()
        expected.delete()
        t.delete()
    }

    @Test
    fun `interactive chooseMode empty defaults to row`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val outDir = ensureDir("src/main/resources/img/out")
        val input = makeColor(defaultDir, "defmode.png", 20, 20)
        val defName = filterList.first().first
        val expected = File(outDir, "defmode_row_${defName}.png")
        if (expected.exists()) expected.delete()
        val feed = buildString {
            append("n\n")
            append(input.absolutePath).append('\n')
            append("\n")
            append("\n")
        }
        val (stdout, _) = invokeMainWithInput(feed)
        assertContains(stdout, "Режим: row")
        assertTrue(expected.exists())
        input.delete()
        expected.delete()
    }

    @Test
    fun `interactive chooseFilter invalid then valid`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val outDir = ensureDir("src/main/resources/img/out")
        val input = makeColor(defaultDir, "filter_retry.png", 28, 28)
        val defName = filterList.first().first
        val expected = File(outDir, "filter_retry_row_${defName}.png")
        if (expected.exists()) expected.delete()
        val feed = buildString {
            append("n\n")
            append(input.absolutePath).append('\n')
            append("row\n")
            append("foobar\n")
            append("1\n")
        }
        val (stdout, _) = invokeMainWithInput(feed)
        assertContains(stdout, "Готово")
        assertTrue(expected.exists())
        input.delete()
        expected.delete()
    }

    @Test
    fun `grid mode runs with provided block size`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val tmp = createTempDirectory("main_grid_in").toFile()
        val input = makeGrayPng(tmp, "img.png", 96, 64)
        val expected = expectedOutNonInteractive(defaultDir, input, "grid")
        if (expected.exists()) expected.delete()
        val (stdout, _) = invokeMain("grid", input.absolutePath, "13")
        assertContains(stdout, "=== MODE: grid ===")
        assertTrue(expected.exists())
        val mat = Imgcodecs.imread(expected.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
        assertTrue(!mat.empty())
        expected.delete()
    }

    @Test
    fun `unknown mode computes like row but keeps its own label and filename`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val tmp = createTempDirectory("main_fallback_in").toFile()
        val input = makeGrayPng(tmp, "x.png", 40, 24)
        val expectedRow = expectedOutNonInteractive(defaultDir, input, "row")
        val expectedUnknown = expectedOutNonInteractive(defaultDir, input, "???")
        if (expectedRow.exists()) expectedRow.delete()
        if (expectedUnknown.exists()) expectedUnknown.delete()
        val (outRow, _) = invokeMain("row", input.absolutePath, "9")
        assertContains(outRow, "=== MODE: row ===")
        assertTrue(expectedRow.exists())
        val (outUnknown, _) = invokeMain("???", input.absolutePath, "9")
        assertContains(outUnknown, "=== MODE: ??? ===")
        assertTrue(expectedUnknown.exists())
        val mr = Imgcodecs.imread(expectedRow.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
        val mu = Imgcodecs.imread(expectedUnknown.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
        assertTrue(!mr.empty() && !mu.empty())
        assertEquals(mr.rows(), mu.rows())
        assertEquals(mr.cols(), mu.cols())
        assertTrue(readBytes(mr).contentEquals(readBytes(mu)))
        expectedRow.delete()
        expectedUnknown.delete()
    }

    @Test
    fun `missing file prints NoFoto and does not create output`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val ghost = File("/definitely/not/existing/file.png")
        val expected = expectedOutNonInteractive(defaultDir, ghost, "row")
        if (expected.exists()) expected.delete()
        val (stdout, _) = invokeMain("row", ghost.absolutePath, "5")
        assertContains(stdout, "NoFoto :O")
        assertTrue(!expected.exists())
    }

    @Test
    fun `pipe processes directory and prints total time`() {
        val inDir = createTempDirectory("pipe_in").toFile()
        val outDir = createTempDirectory("pipe_out").toFile()
        makeColor(inDir, "a.png", 64, 64)
        makeColor(inDir, "b.jpg", 80, 60)
        val feed = "1\n"
        val (stdout, _) = invokeMainWithInput(
            feed,
            "pipe",
            inDir.absolutePath,
            outDir.absolutePath,
            "row",
            "16",
            "1",
            "1",
            "4"
        )
        assertContains(stdout, "МегаТатальноеВремя")
        assertTrue(File(outDir, "a_out.png").exists())
        assertTrue(File(outDir, "b_out.jpg").exists())
    }

    @Test
    fun `pipe with unknown mode produces same pixels as row`() {
        val inDir = createTempDirectory("pipe_in2").toFile()
        val outRow = createTempDirectory("pipe_out_row").toFile()
        val outUnk = createTempDirectory("pipe_out_unk").toFile()
        makeColor(inDir, "z.png", 48, 36)
        val feed = "1\n"
        invokeMainWithInput(feed, "pipe", inDir.absolutePath, outRow.absolutePath, "row", "8", "1", "1", "2")
        invokeMainWithInput(feed, "pipe", inDir.absolutePath, outUnk.absolutePath, "???", "8", "1", "1", "2")
        val a = Imgcodecs.imread(File(outRow, "z_out.png").absolutePath, Imgcodecs.IMREAD_UNCHANGED)
        val b = Imgcodecs.imread(File(outUnk, "z_out.png").absolutePath, Imgcodecs.IMREAD_UNCHANGED)
        assertTrue(!a.empty() && !b.empty())
        assertEquals(a.rows(), b.rows())
        assertEquals(a.cols(), b.cols())
        assertTrue(readBytes(a).contentEquals(readBytes(b)))
    }

    @Test
    fun `interactive single image explicit choices go to out subdir`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val outDir = ensureDir("src/main/resources/img/out")
        val input = makeColor(defaultDir, "zzz_interactive.png", 32, 32)
        val feed = buildString {
            append("n\n")
            append(input.absolutePath).append('\n')
            append("row\n")
            append("\n")
        }
        val (stdout, _) = invokeMainWithInput(feed)
        assertContains(stdout, "Готово")
        val defName = filterList.first().first
        val expected = File(outDir, "zzz_interactive_row_${defName}.png")
        assertTrue(expected.exists())
        input.delete()
        expected.delete()
    }

    @Test
    fun `non interactive preserves lowercase extension for JPG`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val tmp = createTempDirectory("ext_in").toFile()
        val input = makeColor(tmp, "cRaZy.JpG", 20, 20)
        val expected = File(defaultDir, "${input.nameWithoutExtension}_row_${filterList.first().first}.jpg")
        if (expected.exists()) expected.delete()
        val (stdout, _) = invokeMain("row", input.absolutePath, "7")
        assertContains(stdout, "=== MODE: row ===")
        assertTrue(expected.exists())
        expected.delete()
    }

    @Test
    fun `seq col pix produce same output as row on identity`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val tmp = createTempDirectory("modes_in").toFile()
        val input = makeGrayPng(tmp, "modes.png", 64, 48)
        val defName = filterList.first().first
        fun expected(mode: String) = File(defaultDir, "${input.nameWithoutExtension}_${mode}_${defName}.png")
        listOf("row","seq","col","pix").forEach { m -> expected(m).delete() }
        invokeMain("row", input.absolutePath, "7")
        val row = Imgcodecs.imread(expected("row").absolutePath, Imgcodecs.IMREAD_UNCHANGED)
        assertTrue(!row.empty())
        for (mode in listOf("seq","col","pix")) {
            val (out, _) = invokeMain(mode, input.absolutePath, "7")
            assertContains(out, "=== MODE: $mode ===")
            val m = Imgcodecs.imread(expected(mode).absolutePath, Imgcodecs.IMREAD_UNCHANGED)
            assertTrue(!m.empty())
            assertEquals(row.rows(), m.rows()); assertEquals(row.cols(), m.cols())
            assertTrue(readBytes(row).contentEquals(readBytes(m)))
        }
        defaultDir.listFiles()?.forEach { file ->
            if (file.name in listOf("modes_col_identity.png","modes_pix_identity.png","modes_row_identity.png","modes_seq_identity.png",)) file.delete()
            }

    }

    @Test
    fun `interactive chooseFilter with bias increases brightness`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val input = makeColor(defaultDir, "bias_src.png", 32, 32)
        val idx = filterList.indexOfFirst { it.second.bias != 0.0 }
        assumeTrue(idx >= 0)
        val humanIndex = idx + 1
        val name = filterList[idx].first
        val feed = buildString {
            append("n\n")
            append(input.absolutePath).append('\n')
            append("row\n")
            append("$humanIndex\n")
        }
        val (stdout, _) = invokeMainWithInput(feed)
        assertContains(stdout, "Готово")
        val out = File(File(defaultDir, "out"), "bias_src_row_${name}.png")
        assertTrue(out.exists())
        val (stdout2, _) = invokeMain("row", input.absolutePath, "7")
        assertContains(stdout2, "=== MODE: row ===")

        val identity = File(defaultDir, "bias_src_row_${filterList.first().first}.png")
        assertTrue(identity.exists())
        val a = Imgcodecs.imread(out.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
        val b = Imgcodecs.imread(identity.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
        val avgA = avg(readBytes(a))
        val avgB = avg(readBytes(b))
        assertTrue(avgA > avgB + 20.0)
        input.delete()
        identity.delete()
        out.delete()
    }

    @Test
    fun `interactive chooseMode fallback to row on garbage input`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val input = makeColor(defaultDir, "fallback_mode.png", 20, 20)
        val feed = buildString {
            append("n\n")
            append(input.absolutePath).append('\n')
            append("ololo\n")
            append("\n")
        }
        val (stdout, _) = invokeMainWithInput(feed)
        assertContains(stdout, "Режим: row")
        val expected = File(File(defaultDir, "out"), "fallback_mode_row_${filterList.first().first}.png")
        assertTrue(expected.exists())
        input.delete()
        expected.delete()
    }
    @Test
    fun `non interactive grid ignores bad block size and still works`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val tmp = createTempDirectory("grid_bad").toFile()
        val input = makeGrayPng(tmp, "g.png", 64, 64)
        val expected = File(defaultDir, "g_grid_${filterList.first().first}.png")
        if (expected.exists()) expected.delete()
        val (stdout, _) = invokeMain("grid", input.absolutePath, "NaN")
        assertContains(stdout, "=== MODE: grid ===")
        assertTrue(expected.exists())
        expected.delete()
    }

    @Test
    fun `interactive save failure prints cross`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val outDir = ensureDir("src/main/resources/img/out")
        val input = makeColor(defaultDir, "conflict.png", 18, 18)
        val defName = filterList.first().first
        val badPath = File(outDir, "conflict_row_${defName}.png")
        if (badPath.exists()) badPath.delete()
        badPath.mkdirs()
        val feed = buildString {
            append("n\n")
            append(input.absolutePath).append('\n')
            append("row\n")
            append("\n")
        }
        val (stdout, _) = invokeMainWithInput(feed, "-i")
        assertContains(stdout, "❌ Не удалось сохранить результат.")
        badPath.deleteRecursively()
        input.delete()
    }

    @Test
    fun `interactive grid prints custom block`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val outDir = ensureDir("src/main/resources/img/out")
        val input = makeColor(defaultDir, "grid31.png", 30, 30)
        val expected = File(outDir, "grid31_grid_${filterList.first().first}.png")
        expected.delete()
        val feed = buildString {
            append("n\n")
            append(input.absolutePath).append('\n')
            append("grid\n")
            append("31\n")
            append("\n")
        }
        val (stdout, _) = invokeMainWithInput(feed, "-i")
        assertContains(stdout, "Режим: grid (block=31)")
        assertTrue(expected.exists())
        input.delete()
        expected.delete()
    }

    @Test
    fun `interactive chooseImage index out of range then default`() {
        val defaultDir = ensureDir("src/main/resources/img")
        val outDir = ensureDir("src/main/resources/img/out")

        val first = makeColor(defaultDir, "0000000__idx0_FIRST.png", 20, 20)
        val other = makeColor(defaultDir, "zzzz__idx1_LAST.png", 20, 20)

        val defName = filterList.first().first
        val expected = File(outDir, "${first.nameWithoutExtension}_row_${defName}.png")
        if (expected.exists()) expected.delete()

        val feed = buildString {
            append("n\n")
            append("999\n")
            append("\n")
            append("row\n")
            append("\n")
        }
        invokeMainWithInput(feed, "-i")

        assertTrue(expected.exists())

        first.delete()
        other.delete()
        expected.delete()
    }
}


