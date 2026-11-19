package com.example.models

enum class LensType(val displayName: String) {
    MAIN("Main camera"),
    ULTRA_WIDE("Ultra-wide camera"),
    TELEPHOTO("Telephoto camera"),
    FRONT("Front camera"),
    UNKNOWN("Secondary / unknown camera")
}

enum class VerificationStatus {
    VERIFIED_ULTRA_WIDE,
    LIKELY_ULTRA_WIDE,
    WIDE_CAMERA_DETECTED_UNVERIFIED,
    ULTRA_WIDE_NOT_ACCESSIBLE,
    NO_ULTRA_WIDE_CAMERA_FOUND,
    CAMERA_ACCESS_ERROR;

    val isUsableForScanner: Boolean
        get() = this == VERIFIED_ULTRA_WIDE ||
            this == LIKELY_ULTRA_WIDE ||
            this == WIDE_CAMERA_DETECTED_UNVERIFIED
}

enum class LensAccessMethod(val description: String) {
    DIRECT_CAMERA_ID("Direct physical/standalone Camera2 ID"),
    LOGICAL_PHYSICAL_STREAM("Physical stream inside logical multi-camera"),
    NOT_ACCESSIBLE("Not openable via public Camera2 API")
}

data class CameraHardwareInfo(
    val displayId: String,
    val openCameraId: String,
    val physicalCameraId: String?,
    val parentLogicalCameraId: String?,
    val isLogicalMultiCamera: Boolean,
    val underlyingPhysicalIds: List<String>,
    val lensFacing: Int,
    val lensFacingLabel: String,
    val focalLengthsMm: List<Float>,
    val primaryFocalLengthMm: Float?,
    val sensorPhysicalWidthMm: Float?,
    val sensorPhysicalHeightMm: Float?,
    val horizontalFovDeg: Float?,
    val verticalFovDeg: Float?,
    val diagonalFovDeg: Float?,
    val estimated35mmEquivalentMm: Float?,
    val relativeToMainRatio: Float?,
    val sensorOrientationDeg: Int,
    val maxDigitalZoom: Float?,
    val hardwareLevelLabel: String,
    val isBackwardCompatible: Boolean,
    val maxYuvSize: Pair<Int, Int>?,
    val previewSizes: List<Pair<Int, Int>>,
    val yuvSizes: List<Pair<Int, Int>>,
    val afModes: List<Int>,
    val supportsAutofocus: Boolean,
    val isFixedFocus: Boolean,
    val hasFlashUnit: Boolean,
    val minimumFocusDistance: Float?,
    val supportsDistortionCorrection: Boolean,
    val lensType: LensType,
    val accessMethod: LensAccessMethod,
    val classificationReason: String
) {
    val canOpenForPreview: Boolean
        get() = accessMethod != LensAccessMethod.NOT_ACCESSIBLE && isBackwardCompatible

    val megapixelEstimate: Float?
        get() = maxYuvSize?.let { (w, h) -> (w.toFloat() * h.toFloat()) / 1_000_000f }
}

data class CameraDiscoveryReport(
    val status: VerificationStatus,
    val selectedUltraWide: CameraHardwareInfo?,
    val referenceMainCamera: CameraHardwareInfo?,
    val allCameras: List<CameraHardwareInfo>,
    val statusHeadline: String,
    val statusBadgeText: String,
    val statusExplanation: String,
    val errorDetail: String? = null
)
