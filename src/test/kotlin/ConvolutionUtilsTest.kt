package testutil

import conv.*
import org.junit.jupiter.api.*
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConvolutionUtilsTest {
    @BeforeAll
    fun loadOpenCv() {
        try { nu.pattern.OpenCV.loadShared() } catch (_: Throwable) { nu.pattern.OpenCV.loadLocally() }
    }

    @Test
    fun `cache kernel rejects non square`() {
        val m = Mat(3, 4, CvType.CV_32F)
        assertFailsWith<IllegalArgumentException> { ConvolutionUtils.cacheKernel(m) }
    }

    @Test
    fun `convolve pixel clamps and rounds`() {
        val rows = arrayOf(
            ByteArray(5) { 127.toByte() },
            ByteArray(5) { 127.toByte() },
            ByteArray(5) { 127.toByte() }
        )
        val K = arrayOf(
            doubleArrayOf(10.0,10.0,10.0),
            doubleArrayOf(10.0,10.0,10.0),
            doubleArrayOf(10.0,10.0,10.0)
        )
        val b = ConvolutionUtils.convolvePixel(rows, K, 1, 2)
        assertEquals(255.toByte(), b)
    }

    @Test
    fun `convolve row into border and early return`() = runBlocking {
        val w = 2
        val off = 1
        val rows = Array(3) { ByteArray(w) { 7 } }
        val K = Array(3) { DoubleArray(3) }
        val out = ByteArray(w) { 5 }
        ConvolutionUtils.convolveRowInto(rows, K, off, w, out, 1)
        assertContentEquals(byteArrayOf(0,0), out)
    }

    @Test
    fun `convolve row into single Vs parallel equal`() = runBlocking {
        val w = 32
        val off = 1
        val rows = Array(3) { i -> ByteArray(w) { (it + i).toByte() } }
        val K = arrayOf(
            doubleArrayOf(0.0,1.0,0.0),
            doubleArrayOf(1.0,2.0,1.0),
            doubleArrayOf(0.0,1.0,0.0)
        )
        val a = ByteArray(w)
        val b = ByteArray(w)
        ConvolutionUtils.convolveRowInto(rows, K, off, w, a, 1)
        ConvolutionUtils.convolveRowInto(rows, K, off, w, b, 4)
        assertContentEquals(a, b)
        assertTrue(a.first() == 0.toByte() && a.last() == 0.toByte())
    }
}