package testutil

import org.junit.jupiter.api.*
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.assertTrue
import filters.Filter
import filters.toCvKernel
import conv.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PipelineTest {
    @BeforeAll
    fun init() {
        try { nu.pattern.OpenCV.loadShared() } catch (_: Throwable) { nu.pattern.OpenCV.loadLocally() }
        File("src/main/resources/img").mkdirs()
    }

    private fun captureStdout(block: () -> Unit): String {
        val orig = System.out
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos, true, "UTF-8"))
        try { block() } finally {
            System.out.flush()
            System.setOut(orig)
        }
        return baos.toString("UTF-8")
    }

    @Test
    fun `empty Input Directory warns And Exits`() {
        val inDir = createTempDirectory().toFile()
        val outDir = createTempDirectory().toFile()
        val kernel = Filter(arrayOf(
            doubleArrayOf(0.0,0.0,0.0),
            doubleArrayOf(0.0,1.0,0.0),
            doubleArrayOf(0.0,0.0,0.0)
        )).toCvKernel()
        val out = captureStdout {
            Pipeline.run(kernel, inDir.absolutePath, outDir.absolutePath, "row", 8, 1, 1, 1)
        }
        assertTrue(out.contains("Нет файлов"))
    }

    @Test
    fun `processes Png And Jpg preserves Extension and Writes Log`() {
        val inDir = Files.createTempDirectory("in").toFile()
        val outDir = Files.createTempDirectory("out").toFile()
        val a = Mat(16,16, CvType.CV_8UC3, Scalar(10.0,10.0,10.0))
        val b = Mat(16,16, CvType.CV_8UC3, Scalar(20.0,20.0,20.0))
        val aPath = File(inDir, "a.png").absolutePath
        val bPath = File(inDir, "b.jpg").absolutePath
        Imgcodecs.imwrite(aPath, a)
        Imgcodecs.imwrite(bPath, b)
        val kernel = Filter(arrayOf(
            doubleArrayOf(0.0,0.0,0.0),
            doubleArrayOf(0.0,1.0,0.0),
            doubleArrayOf(0.0,0.0,0.0)
        )).toCvKernel()
        Pipeline.run(kernel, inDir.absolutePath, outDir.absolutePath, "row", 8, 1, 1, 2)
        val outPng = File(outDir, "a_out.png")
        val outJpg = File(outDir, "b_out.jpg")
        assertTrue(outPng.isFile)
        assertTrue(outJpg.isFile)
        val log = File("src/main/resources/img/timing_log.csv")
        assertTrue(log.exists())
    }

    @Test
    fun `bias Is Applied and Grid Mode Works`() {
        val inDir = Files.createTempDirectory("in2").toFile()
        val outDir = Files.createTempDirectory("out2").toFile()
        val img = Mat(16,16, CvType.CV_8UC3, Scalar(10.0,10.0,10.0))
        val inPath = File(inDir, "x.png").absolutePath
        Imgcodecs.imwrite(inPath, img)
        val kernel = Filter(arrayOf(
            doubleArrayOf(0.0,0.0,0.0),
            doubleArrayOf(0.0,1.0,0.0),
            doubleArrayOf(0.0,0.0,0.0)
        )).toCvKernel()
        Pipeline.run(kernel, 50.0, inDir.absolutePath, outDir.absolutePath, "grid", 5, 1, 1, 1)
        val out = Imgcodecs.imread(File(outDir, "x_out.png").absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
        val v = ByteArray(1)
        out.get(8,8,v)
        assertTrue((v[0].toInt() and 0xFF) >= 60)
    }
}