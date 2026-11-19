package com.example.camera

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.SizeF
import com.example.models.CameraDiscoveryReport
import com.example.models.CameraHardwareInfo
import com.example.models.LensAccessMethod
import com.example.models.LensType
import com.example.models.VerificationStatus
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.hypot

data class RawCameraSnapshot(
    val displayId: String,
    val openCameraId: String,
    val physicalCameraId: String?,
    val parentLogicalCameraId: String?,
    val isInPublicCameraIdList: Boolean,
    val isLogicalMultiCamera: Boolean,
    val underlyingPhysicalIds: List<String>,
    val lensFacing: Int,
    val focalLengthsMm: List<Float>,
    val sensorPhysicalWidthMm: Float?,
    val sensorPhysicalHeightMm: Float?,
    val sensorOrientationDeg: Int,
    val maxDigitalZoom: Float?,
    val hardwareLevel: Int?,
    val isBackwardCompatible: Boolean,
    val previewSizes: List<Pair<Int, Int>>,
    val yuvSizes: List<Pair<Int, Int>>,
    val afModes: List<Int>,
    val minimumFocusDistance: Float?,
    val hasFlashUnit: Boolean,
    val supportsDistortionCorrection: Boolean,
    val supportsPhysicalSubStream: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
)

object CameraCharacteristicsInspector {

    private const val FULL_FRAME_DIAGONAL_MM = 43.266615f

    fun extractSnapshot(
        cameraManager: CameraManager,
        cameraId: String,
        isInPublicList: Boolean,
        parentLogicalCameraId: String? = null,
        parentFlashAvailable: Boolean = false
    ): RawCameraSnapshot? {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val isBackwardCompatible = capabilities.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
            )
            val isLogicalMultiCamera = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
            } else {
                false
            }
            val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isLogicalMultiCamera) {
                try {
                    chars.physicalCameraIds.toList().sorted()
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                ?: CameraMetadata.LENS_FACING_BACK
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.toList()
                ?.filter { it > 0f && it.isFinite() }
                ?: emptyList()
            val physicalSize: SizeF? = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val sensorWidth = physicalSize?.width?.takeIf { it > 0f && it.isFinite() }
            val sensorHeight = physicalSize?.height?.takeIf { it > 0f && it.isFinite() }
            val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                ?.takeIf { it.isFinite() }
            val hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)

            val streamConfigMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSizes = streamConfigMap
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.map { it.width to it.height }
                ?.sortedByDescending { it.first * it.second }
                ?: emptyList()
            val yuvSizes = streamConfigMap
                ?.getOutputSizes(ImageFormat.YUV_420_888)
                ?.map { it.width to it.height }
                ?.sortedByDescending { it.first * it.second }
                ?: emptyList()

            val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList() ?: emptyList()
            val minFocusDist = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                ?.takeIf { it.isFinite() }
            val hasFlash = (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) ||
                parentFlashAvailable

            val distortionModes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                chars.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES) ?: intArrayOf()
            } else {
                intArrayOf()
            }
            val supportsDistortionCorrection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                distortionModes.any {
                    it == CameraMetadata.DISTORTION_CORRECTION_MODE_HIGH_QUALITY ||
                        it == CameraMetadata.DISTORTION_CORRECTION_MODE_FAST
                }
            } else {
                false
            }

            val displayId = if (parentLogicalCameraId != null) {
                "$parentLogicalCameraId -> physical $cameraId"
            } else {
                cameraId
            }

            RawCameraSnapshot(
                displayId = displayId,
                openCameraId = parentLogicalCameraId ?: cameraId,
                physicalCameraId = if (parentLogicalCameraId != null) cameraId else null,
                parentLogicalCameraId = parentLogicalCameraId,
                isInPublicCameraIdList = isInPublicList,
                isLogicalMultiCamera = isLogicalMultiCamera,
                underlyingPhysicalIds = physicalIds,
                lensFacing = lensFacing,
                focalLengthsMm = focalLengths,
                sensorPhysicalWidthMm = sensorWidth,
                sensorPhysicalHeightMm = sensorHeight,
                sensorOrientationDeg = sensorOrientation,
                maxDigitalZoom = maxZoom,
                hardwareLevel = hardwareLevel,
                isBackwardCompatible = isBackwardCompatible,
                previewSizes = previewSizes,
                yuvSizes = yuvSizes,
                afModes = afModes,
                minimumFocusDistance = minFocusDist,
                hasFlashUnit = hasFlash,
                supportsDistortionCorrection = supportsDistortionCorrection
            )
        } catch (_: Exception) {
            null
        }
    }

    fun evaluateCameras(snapshots: List<RawCameraSnapshot>): CameraDiscoveryReport {
        if (snapshots.isEmpty()) {
            return CameraDiscoveryReport(
                status = VerificationStatus.CAMERA_ACCESS_ERROR,
                selectedUltraWide = null,
                referenceMainCamera = null,
                allCameras = emptyList(),
                statusHeadline = "No cameras reported",
                statusBadgeText = "Camera error",
                statusExplanation = "Android CameraManager returned no accessible camera devices."
            )
        }

        // First compute optical metrics for every snapshot
        val preliminary = snapshots.map { snap -> buildPreliminaryInfo(snap) }

        // Identify reference rear main (1x) camera for relative optical comparison
        val referenceMain = findReferenceMainCamera(preliminary)

        // Classify each camera with relative optical comparison to referenceMain
        val classified = preliminary.map { info ->
            classifyWithReference(info, referenceMain)
        }

        val updatedMain = classified.firstOrNull { it.displayId == referenceMain?.displayId }
            ?: classified.firstOrNull { it.lensType == LensType.MAIN }

        // Find accessible ultra-wide candidates
        val accessibleUltraWideCandidates = classified.filter {
            it.lensType == LensType.ULTRA_WIDE && it.canOpenForPreview
        }.sortedWith(
            compareByDescending<CameraHardwareInfo> {
                it.accessMethod == LensAccessMethod.DIRECT_CAMERA_ID
            }.thenByDescending {
                it.horizontalFovDeg ?: 0f
            }.thenBy {
                it.primaryFocalLengthMm ?: Float.MAX_VALUE
            }
        )

        val inaccessibleUltraWideCandidates = classified.filter {
            it.lensType == LensType.ULTRA_WIDE && !it.canOpenForPreview
        }

        // Also check if a logical camera exposes multiple focal lengths without openable physical IDs
        val unverifiedWideLogical = classified.firstOrNull {
            it.lensFacing == CameraMetadata.LENS_FACING_BACK &&
                it.isLogicalMultiCamera &&
                it.focalLengthsMm.size > 1 &&
                it.underlyingPhysicalIds.isEmpty()
        }

        val selected = accessibleUltraWideCandidates.firstOrNull()

        return when {
            selected != null -> {
                val hasStrongOpticalVerification =
                    selected.horizontalFovDeg != null &&
                        selected.horizontalFovDeg >= 88f &&
                        (updatedMain?.horizontalFovDeg == null ||
                            selected.horizontalFovDeg / updatedMain.horizontalFovDeg >= 1.22f)

                val hasLikelyOpticalVerification =
                    (selected.horizontalFovDeg != null && selected.horizontalFovDeg >= 80f) ||
                        (selected.primaryFocalLengthMm != null &&
                            updatedMain?.primaryFocalLengthMm != null &&
                            selected.primaryFocalLengthMm / updatedMain.primaryFocalLengthMm <= 0.68f)

                val status = when {
                    hasStrongOpticalVerification -> VerificationStatus.VERIFIED_ULTRA_WIDE
                    hasLikelyOpticalVerification -> VerificationStatus.LIKELY_ULTRA_WIDE
                    else -> VerificationStatus.WIDE_CAMERA_DETECTED_UNVERIFIED
                }

                val badge = when (status) {
                    VerificationStatus.VERIFIED_ULTRA_WIDE -> "Hardware verified"
                    VerificationStatus.LIKELY_ULTRA_WIDE -> "Likely ultra-wide"
                    VerificationStatus.WIDE_CAMERA_DETECTED_UNVERIFIED -> "Unverified wide lens"
                    else -> ""
                }

                val ratioNote = selected.relativeToMainRatio?.let { ratio ->
                    if (ratio in 0.35f..0.75f) {
                        val approxLabel = String.format(java.util.Locale.US, "%.1fx", ratio)
                        " Estimated optical width: ~$approxLabel relative to main camera."
                    } else {
                        ""
                    }
                } ?: ""

                val methodNote = when (selected.accessMethod) {
                    LensAccessMethod.DIRECT_CAMERA_ID ->
                        "Opened directly via Camera2 ID ${selected.openCameraId}."
                    LensAccessMethod.LOGICAL_PHYSICAL_STREAM ->
                        "Streamed from physical sensor ${selected.physicalCameraId} inside logical camera ${selected.openCameraId}."
                    LensAccessMethod.NOT_ACCESSIBLE -> ""
                }

                CameraDiscoveryReport(
                    status = status,
                    selectedUltraWide = selected,
                    referenceMainCamera = updatedMain,
                    allCameras = classified,
                    statusHeadline = "Ultra-wide camera",
                    statusBadgeText = badge,
                    statusExplanation = "$methodNote$ratioNote"
                )
            }

            inaccessibleUltraWideCandidates.isNotEmpty() -> {
                CameraDiscoveryReport(
                    status = VerificationStatus.ULTRA_WIDE_NOT_ACCESSIBLE,
                    selectedUltraWide = null,
                    referenceMainCamera = updatedMain,
                    allCameras = classified,
                    statusHeadline = "Ultra-wide not accessible",
                    statusBadgeText = "Not accessible",
                    statusExplanation = "An ultra-wide sensor was found in camera metadata, but the device does not expose a backward-compatible video/preview stream for third-party apps. WideQR will not fall back to the blurry main camera."
                )
            }

            unverifiedWideLogical != null -> {
                CameraDiscoveryReport(
                    status = VerificationStatus.ULTRA_WIDE_NOT_ACCESSIBLE,
                    selectedUltraWide = null,
                    referenceMainCamera = updatedMain,
                    allCameras = classified,
                    statusHeadline = "Ultra-wide not exposed",
                    statusBadgeText = "Not accessible",
                    statusExplanation = "Logical camera ${unverifiedWideLogical.openCameraId} advertises multiple focal lengths (${unverifiedWideLogical.focalLengthsMm.joinToString { "${it}mm" }}), but does not expose individual physical camera IDs to third-party apps."
                )
            }

            else -> {
                val rearCount = classified.count { it.lensFacing == CameraMetadata.LENS_FACING_BACK }
                val explanation = if (rearCount <= 1) {
                    "Only $rearCount rear camera was exposed by Android Camera2 APIs on this device. Some phones restrict auxiliary ultra-wide sensors to the preinstalled stock camera app."
                } else {
                    "Found $rearCount rear camera entries, but none have optical focal length or field-of-view characteristics matching an ultra-wide lens."
                }
                CameraDiscoveryReport(
                    status = VerificationStatus.NO_ULTRA_WIDE_CAMERA_FOUND,
                    selectedUltraWide = null,
                    referenceMainCamera = updatedMain,
                    allCameras = classified,
                    statusHeadline = "No ultra-wide camera found",
                    statusBadgeText = "No ultra-wide",
                    statusExplanation = explanation
                )
            }
        }
    }

    private fun buildPreliminaryInfo(snap: RawCameraSnapshot): CameraHardwareInfo {
        val focal = snap.focalLengthsMm.minOrNull()
        val w = snap.sensorPhysicalWidthMm
        val h = snap.sensorPhysicalHeightMm

        val hFov: Float?
        val vFov: Float?
        val dFov: Float?
        val eq35: Float?

        if (focal != null && focal > 0f && w != null && h != null && w > 0f && h > 0f) {
            hFov = (2.0 * atan(w.toDouble() / (2.0 * focal.toDouble())) * 180.0 / PI).toFloat()
            vFov = (2.0 * atan(h.toDouble() / (2.0 * focal.toDouble())) * 180.0 / PI).toFloat()
            val diag = hypot(w.toDouble(), h.toDouble())
            dFov = (2.0 * atan(diag / (2.0 * focal.toDouble())) * 180.0 / PI).toFloat()
            eq35 = (focal.toDouble() * (FULL_FRAME_DIAGONAL_MM / diag)).toFloat()
        } else {
            hFov = null
            vFov = null
            dFov = null
            eq35 = null
        }

        val accessMethod = when {
            snap.physicalCameraId != null && snap.parentLogicalCameraId != null -> {
                // Physical sub-camera inside logical multi-camera (requires API 28+ physical stream)
                if (snap.supportsPhysicalSubStream &&
                    (snap.previewSizes.isNotEmpty() || snap.yuvSizes.isNotEmpty())
                ) {
                    LensAccessMethod.LOGICAL_PHYSICAL_STREAM
                } else {
                    LensAccessMethod.NOT_ACCESSIBLE
                }
            }
            snap.isInPublicCameraIdList && snap.isBackwardCompatible -> LensAccessMethod.DIRECT_CAMERA_ID
            else -> LensAccessMethod.NOT_ACCESSIBLE
        }

        val supportsAf = snap.afModes.any {
            it == CameraMetadata.CONTROL_AF_MODE_AUTO ||
                it == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE ||
                it == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO ||
                it == CameraMetadata.CONTROL_AF_MODE_MACRO
        }
        val isFixedFocus = !supportsAf || (snap.minimumFocusDistance != null && snap.minimumFocusDistance == 0f)

        return CameraHardwareInfo(
            displayId = snap.displayId,
            openCameraId = snap.openCameraId,
            physicalCameraId = snap.physicalCameraId,
            parentLogicalCameraId = snap.parentLogicalCameraId,
            isLogicalMultiCamera = snap.isLogicalMultiCamera,
            underlyingPhysicalIds = snap.underlyingPhysicalIds,
            lensFacing = snap.lensFacing,
            lensFacingLabel = facingLabel(snap.lensFacing),
            focalLengthsMm = snap.focalLengthsMm,
            primaryFocalLengthMm = focal,
            sensorPhysicalWidthMm = w,
            sensorPhysicalHeightMm = h,
            horizontalFovDeg = hFov,
            verticalFovDeg = vFov,
            diagonalFovDeg = dFov,
            estimated35mmEquivalentMm = eq35,
            relativeToMainRatio = null,
            sensorOrientationDeg = snap.sensorOrientationDeg,
            maxDigitalZoom = snap.maxDigitalZoom,
            hardwareLevelLabel = hardwareLevelLabel(snap.hardwareLevel),
            isBackwardCompatible = snap.isBackwardCompatible ||
                accessMethod == LensAccessMethod.LOGICAL_PHYSICAL_STREAM,
            maxYuvSize = snap.yuvSizes.firstOrNull() ?: snap.previewSizes.firstOrNull(),
            previewSizes = snap.previewSizes,
            yuvSizes = snap.yuvSizes,
            afModes = snap.afModes,
            supportsAutofocus = supportsAf,
            isFixedFocus = isFixedFocus,
            hasFlashUnit = snap.hasFlashUnit,
            minimumFocusDistance = snap.minimumFocusDistance,
            supportsDistortionCorrection = snap.supportsDistortionCorrection,
            lensType = LensType.UNKNOWN,
            accessMethod = accessMethod,
            classificationReason = ""
        )
    }

    private fun findReferenceMainCamera(cameras: List<CameraHardwareInfo>): CameraHardwareInfo? {
        val rearCandidates = cameras.filter {
            it.lensFacing == CameraMetadata.LENS_FACING_BACK && it.canOpenForPreview
        }
        if (rearCandidates.isEmpty()) return null

        // Prefer a camera whose 35mm-eq falls in typical standard wide-angle range (21mm..34mm)
        // or horizontal FOV between 55°..79°, breaking ties by highest megapixel count
        val standardMain = rearCandidates.filter { info ->
            val eq = info.estimated35mmEquivalentMm
            val fov = info.horizontalFovDeg
            (eq != null && eq in 21f..34f) || (fov != null && fov in 55f..79f)
        }.maxByOrNull { info ->
            val mp = info.megapixelEstimate ?: 0f
            val directBonus = if (info.accessMethod == LensAccessMethod.DIRECT_CAMERA_ID) 100f else 0f
            mp + directBonus
        }

        return standardMain ?: rearCandidates.firstOrNull { it.openCameraId == "0" }
            ?: rearCandidates.maxByOrNull { it.megapixelEstimate ?: 0f }
    }

    private fun classifyWithReference(
        info: CameraHardwareInfo,
        main: CameraHardwareInfo?
    ): CameraHardwareInfo {
        if (info.lensFacing == CameraMetadata.LENS_FACING_FRONT) {
            return info.copy(
                lensType = LensType.FRONT,
                classificationReason = "Front-facing sensor"
            )
        }
        if (info.lensFacing != CameraMetadata.LENS_FACING_BACK) {
            return info.copy(
                lensType = LensType.UNKNOWN,
                classificationReason = "External or non-rear camera"
            )
        }

        // Ignore tiny depth/macro auxiliary sensors (< 2.5 MP) if they aren't demonstrably ultra-wide FOV
        val mp = info.megapixelEstimate
        val eq35 = info.estimated35mmEquivalentMm
        val hFov = info.horizontalFovDeg
        val dFov = info.diagonalFovDeg

        val ratioToMain = when {
            eq35 != null && main?.estimated35mmEquivalentMm != null && main.estimated35mmEquivalentMm > 0f ->
                eq35 / main.estimated35mmEquivalentMm
            hFov != null && main?.horizontalFovDeg != null && hFov > 0f ->
                main.horizontalFovDeg / hFov
            info.primaryFocalLengthMm != null && main?.primaryFocalLengthMm != null && main.primaryFocalLengthMm > 0f ->
                info.primaryFocalLengthMm / main.primaryFocalLengthMm
            else -> null
        }

        // A logical multi-camera wrapper whose own focal lengths include multiple lenses
        // should be treated as MAIN (1x default stream) unless it represents a single wide lens
        val isLogicalComposite = info.isLogicalMultiCamera && info.underlyingPhysicalIds.size > 1

        if (main != null && info.displayId == main.displayId) {
            return info.copy(
                lensType = LensType.MAIN,
                relativeToMainRatio = 1.0f,
                classificationReason = buildReason("Identified as primary 1x reference camera", hFov, eq35)
            )
        }

        val isUltraWideByFov = (hFov != null && hFov >= 82f) ||
            (dFov != null && dFov >= 94f) ||
            (eq35 != null && eq35 <= 20.5f)

        val isUltraWideByRelativeFocal = !isLogicalComposite &&
            hFov == null &&
            ratioToMain != null &&
            ratioToMain <= 0.65f &&
            (mp == null || mp >= 2.0f)

        return when {
            !isLogicalComposite && isUltraWideByFov && (mp == null || mp >= 1.5f) -> {
                info.copy(
                    lensType = LensType.ULTRA_WIDE,
                    relativeToMainRatio = ratioToMain,
                    classificationReason = buildReason(
                        "Wide optical field of view confirms ultra-wide lens",
                        hFov,
                        eq35
                    )
                )
            }

            isUltraWideByRelativeFocal -> {
                info.copy(
                    lensType = LensType.ULTRA_WIDE,
                    relativeToMainRatio = ratioToMain,
                    classificationReason = "Focal length is significantly shorter than main camera (sensor physical size missing)"
                )
            }

            (hFov != null && hFov <= 46f) || (eq35 != null && eq35 >= 45f) -> {
                info.copy(
                    lensType = LensType.TELEPHOTO,
                    relativeToMainRatio = ratioToMain,
                    classificationReason = buildReason("Narrow optical field of view indicates telephoto", hFov, eq35)
                )
            }

            isLogicalComposite -> {
                info.copy(
                    lensType = LensType.MAIN,
                    relativeToMainRatio = 1.0f,
                    classificationReason = "Logical multi-camera composite (${info.underlyingPhysicalIds.size} physical sensors)"
                )
            }

            else -> {
                val note = if (mp != null && mp < 2.5f) {
                    "Low-resolution auxiliary sensor (${String.format(java.util.Locale.US, "%.1f", mp)} MP, likely macro or depth)"
                } else {
                    buildReason("Standard FOV or insufficient optical difference from main camera", hFov, eq35)
                }
                info.copy(
                    lensType = LensType.UNKNOWN,
                    relativeToMainRatio = ratioToMain,
                    classificationReason = note
                )
            }
        }
    }

    private fun buildReason(prefix: String, hFov: Float?, eq35: Float?): String {
        val parts = mutableListOf(prefix)
        if (hFov != null) {
            parts.add("HFOV ${String.format(java.util.Locale.US, "%.0f", hFov)}°")
        }
        if (eq35 != null) {
            parts.add("~${String.format(java.util.Locale.US, "%.0f", eq35)}mm eq. est.")
        }
        return parts.joinToString(" · ")
    }

    private fun facingLabel(facing: Int): String = when (facing) {
        CameraMetadata.LENS_FACING_BACK -> "Rear"
        CameraMetadata.LENS_FACING_FRONT -> "Front"
        CameraMetadata.LENS_FACING_EXTERNAL -> "External"
        else -> "Unknown"
    }

    private fun hardwareLevelLabel(level: Int?): String = when (level) {
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }
}
