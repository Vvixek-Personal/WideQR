package com.example.camera

import android.hardware.camera2.CameraMetadata
import android.util.Size
import com.example.models.CameraHardwareInfo
import kotlin.math.abs

object PhysicalCameraSelector {

    /**
     * Selects a balanced preview resolution for smooth performance on 4 GB RAM devices
     * while preserving clarity for small QR codes. Targets ~720p-1080p matching aspect ratio.
     */
    fun choosePreviewSize(
        supportedSizes: List<Pair<Int, Int>>,
        targetAspectRatio: Float = 4f / 3f
    ): Size {
        if (supportedSizes.isEmpty()) return Size(1280, 960)

        val candidates = supportedSizes.filter { (w, h) ->
            w in 640..1920 && h in 480..1440
        }.ifEmpty { supportedSizes }

        val best = candidates.minByOrNull { (w, h) ->
            val ratio = w.toFloat() / h.toFloat().coerceAtLeast(1f)
            val aspectPenalty = abs(ratio - targetAspectRatio) * 10f
            val targetPixels = 1280f * 960f
            val sizePenalty = abs((w * h) - targetPixels) / targetPixels
            aspectPenalty + sizePenalty
        } ?: supportedSizes.first()

        return Size(best.first, best.second)
    }

    /**
     * Selects YUV_420_888 analysis resolution: sharp enough for dense UPI QR codes
     * through an ultra-wide lens (~1280x960 or 1280x720), capped for low RAM/CPU overhead.
     */
    fun chooseAnalysisSize(
        supportedYuvSizes: List<Pair<Int, Int>>,
        previewSize: Size
    ): Size {
        if (supportedYuvSizes.isEmpty()) return previewSize

        val previewAspect = previewSize.width.toFloat() / previewSize.height.toFloat().coerceAtLeast(1f)
        val candidates = supportedYuvSizes.filter { (w, h) ->
            w in 640..1600 && h in 480..1200
        }.ifEmpty { supportedYuvSizes }

        val best = candidates.minByOrNull { (w, h) ->
            val ratio = w.toFloat() / h.toFloat().coerceAtLeast(1f)
            val aspectPenalty = abs(ratio - previewAspect) * 10f
            val targetPixels = 1280f * 960f
            val sizePenalty = abs((w * h) - targetPixels) / targetPixels
            aspectPenalty + sizePenalty
        } ?: supportedYuvSizes.first()

        return Size(best.first, best.second)
    }

    fun chooseAfMode(cameraInfo: CameraHardwareInfo): Int {
        return when {
            cameraInfo.afModes.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE) ->
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            cameraInfo.afModes.contains(CameraMetadata.CONTROL_AF_MODE_AUTO) ->
                CameraMetadata.CONTROL_AF_MODE_AUTO
            cameraInfo.afModes.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO) ->
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            else -> CameraMetadata.CONTROL_AF_MODE_OFF
        }
    }
}
