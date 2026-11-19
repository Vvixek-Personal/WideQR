package com.example

import android.util.Size
import com.example.camera.PhysicalCameraSelector
import com.example.scanner.QRDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QrOptimizationTest {

    @Test
    fun `analysis resolution chooses lowest practical 4_3 resolution over 1280x960`() {
        val supportedYuv = listOf(
            2576 to 1932,
            1920 to 1440,
            1280 to 960,
            960 to 720,
            800 to 600,
            640 to 480
        )
        val preview = Size(1280, 960)
        val chosen = PhysicalCameraSelector.chooseAnalysisSize(supportedYuv, preview)

        assertEquals(Size(960, 720), chosen)
        val pixelReduction = 1.0 - ((chosen.width * chosen.height).toDouble() / (1280.0 * 960.0))
        assertTrue(pixelReduction >= 0.43)
    }

    @Test
    fun `analysis resolution falls back to 800x600 when 960x720 is absent`() {
        val supportedYuv = listOf(
            2576 to 1932,
            1280 to 960,
            800 to 600,
            640 to 480
        )
        val chosen = PhysicalCameraSelector.chooseAnalysisSize(supportedYuv, Size(1280, 960))
        assertEquals(Size(800, 600), chosen)
    }

    @Test
    fun `decoder pause and resume control frame acceptance immediately`() {
        val decoder = QRDecoder()
        try {
            assertTrue(decoder.canAcceptFrame())
            decoder.pause()
            assertFalse(decoder.canAcceptFrame())
            decoder.resume()
            assertTrue(decoder.canAcceptFrame())
        } finally {
            decoder.close()
        }
    }
}
