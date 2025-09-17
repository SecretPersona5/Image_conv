package testutil

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.opencv.core.CvType
import org.opencv.core.Mat
import conv.*


class FiltersParityTest : OpenCVTestBase() {

    private fun sampleGray(h: Int = 16, w: Int = 20): Mat =
        matU8(h, w) { r, c -> (r * 7 + c * 11) % 256 }

    @Test
    fun `Sequential with box 5x5 matches manual on center pixel`() {
        val img = sampleGray(9, 9)
        val K = kernelBox(5)
        val out = Sequential.apply(img, K)

        val vals = IntArray(25)
        var k = 0
        for (dy in -2..2) for (dx in -2..2) {
            vals[k++] = img.get(4 + dy, 4 + dx)[0].toInt()
        }
        val expected = (vals.sum() / 25.0).toInt().coerceIn(0, 255)

        assertEquals(expected, out.get(4, 4)[0].toInt())
        assertEquals(CvType.CV_8UC1, out.type())
    }

    @Test
    fun `Sequential returns zeros for too-small image`() {
        val tiny = matU8(2, 2) { _, _ -> 255 }
        val K = kernelBox(5)
        val out = Sequential.apply(tiny, K)
        assertEquals(0, out.get(0, 0)[0].toInt())
    }

    @Test
    fun `RowParallel equals Sequential`() {
        val img = sampleGray()
        val K = kernelBox(5)
        val s = Sequential.apply(img, K)
        val r = RowParallel.apply(img, K)
        assertTrue(matsEqual(s, r))
    }

    @Test
    fun `ColParallel equals Sequential`() {
        val img = sampleGray()
        val K = kernelBox(5)
        val s = Sequential.apply(img, K)
        val c = ColParallel.apply(img, K)
        assertTrue(matsEqual(s, c))
    }

    @Test
    fun `PixelParallel equals Sequential`() {
        val img = sampleGray()
        val K = kernelBox(5)
        val s = Sequential.apply(img, K)
        val p = PixelParallel.apply(img, K)
        assertTrue(matsEqual(s, p))
    }

    @Test
    fun `GridParallel equals Sequential for various block sizes`() {
        val img = sampleGray(31, 37)
        val K = kernelBox(5)
        val s = Sequential.apply(img, K)

        val g1 = GridParallel.apply(img, K, blockSize = 8, xWorkers = 1)
        val g2 = GridParallel.apply(img, K, blockSize = 16, xWorkers = 4)

        assertTrue(matsEqual(s, g1))
        assertTrue(matsEqual(s, g2))
    }

    @Test
    fun `Non-square kernel throws (Sequential, GridParallel)`() {
        val img = sampleGray()
        val bad = Mat(3, 5, CvType.CV_32F)
        assertThrows(IllegalArgumentException::class.java) { Sequential.apply(img, bad) }
        assertThrows(IllegalArgumentException::class.java) { GridParallel.apply(img, bad) }
    }
}

