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
     * Selects the lowest practical YUV_420_888 analysis resolution that still reliably resolves
     * dense UPI QR codes through an ultra-wide lens (~960x720 or 800x600), cutting per-frame
     * ML Kit pixel work by ~44-61% compared to 1280x960 on 4 GB RAM devices.
     */
    fun chooseAnalysisSize(
        supportedYuvSizes: List<Pair<Int, Int>>,
        previewSize: Size
    ): Size {
        if (supportedYuvSizes.isEmpty()) return previewSize

        val previewAspect = previewSize.width.toFloat() / previewSize.height.toFloat().coerceAtLeast(1f)
        val candidates = supportedYuvSizes.filter { (w, h) ->
            w in 640..1280 && h in 480..768
        }.ifEmpty {
            supportedYuvSizes.filter { (w, h) -> w >= 640 && h >= 480 }.ifEmpty { supportedYuvSizes }
        }

        val best = candidates.minByOrNull { (w, h) ->
            val ratio = w.toFloat() / h.toFloat().coerceAtLeast(1f)
            val aspectPenalty = abs(ratio - previewAspect) * 10f
            val targetPixels = 960f * 720f
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
