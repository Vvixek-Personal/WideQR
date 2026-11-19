package com.example

import android.hardware.camera2.CameraMetadata
import com.example.camera.CameraCharacteristicsInspector
import com.example.camera.RawCameraSnapshot
import com.example.models.LensAccessMethod
import com.example.models.LensType
import com.example.models.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CameraCharacteristicsInspectorTest {

    private fun snapshot(
        displayId: String,
        openId: String = displayId,
        physicalId: String? = null,
        parentLogicalId: String? = null,
        inPublicList: Boolean = true,
        isLogical: Boolean = false,
        underlyingIds: List<String> = emptyList(),
        facing: Int = CameraMetadata.LENS_FACING_BACK,
        focalLengths: List<Float>,
        sensorW: Float?,
        sensorH: Float?,
        backwardCompatible: Boolean = true,
        previewSizes: List<Pair<Int, Int>> = listOf(1920 to 1080, 1280 to 960),
        yuvSizes: List<Pair<Int, Int>> = listOf(4000 to 3000, 1280 to 960),
        afModes: List<Int> = listOf(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE),
        minFocusDist: Float? = 10f
    ) = RawCameraSnapshot(
        displayId = displayId,
        openCameraId = openId,
        physicalCameraId = physicalId,
        parentLogicalCameraId = parentLogicalId,
        isInPublicCameraIdList = inPublicList,
        isLogicalMultiCamera = isLogical,
        underlyingPhysicalIds = underlyingIds,
        lensFacing = facing,
        focalLengthsMm = focalLengths,
        sensorPhysicalWidthMm = sensorW,
        sensorPhysicalHeightMm = sensorH,
        sensorOrientationDeg = 90,
        maxDigitalZoom = 4f,
        hardwareLevel = CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
        isBackwardCompatible = backwardCompatible,
        previewSizes = previewSizes,
        yuvSizes = yuvSizes,
        afModes = afModes,
        minimumFocusDistance = minFocusDist,
        hasFlashUnit = facing == CameraMetadata.LENS_FACING_BACK,
        supportsDistortionCorrection = false,
        supportsPhysicalSubStream = true
    )

    @Test
    fun `standalone physical ultra-wide camera is verified and selected`() {
        val main = snapshot("0", focalLengths = listOf(4.73f), sensorW = 6.4f, sensorH = 4.8f)
        val front = snapshot("1", facing = CameraMetadata.LENS_FACING_FRONT, focalLengths = listOf(3.0f), sensorW = 3.6f, sensorH = 2.7f)
        val ultraWide = snapshot(
            "2",
            focalLengths = listOf(1.67f),
            sensorW = 3.6f,
            sensorH = 2.7f,
            yuvSizes = listOf(2576 to 1932, 1280 to 960),
            afModes = listOf(CameraMetadata.CONTROL_AF_MODE_OFF),
            minFocusDist = 0f
        )

        val report = CameraCharacteristicsInspector.evaluateCameras(listOf(main, front, ultraWide))

        assertEquals(VerificationStatus.VERIFIED_ULTRA_WIDE, report.status)
        assertNotNull(report.selectedUltraWide)
        assertEquals("2", report.selectedUltraWide?.openCameraId)
        assertEquals(LensAccessMethod.DIRECT_CAMERA_ID, report.selectedUltraWide?.accessMethod)
        assertEquals(LensType.ULTRA_WIDE, report.selectedUltraWide?.lensType)
        assertEquals(true, report.selectedUltraWide?.isFixedFocus)
        assertEquals("0", report.referenceMainCamera?.openCameraId)
    }

    @Test
    fun `logical multi-camera exposes physical ultra-wide sub-camera`() {
        val logical = snapshot(
            displayId = "0",
            isLogical = true,
            underlyingIds = listOf("2", "3"),
            focalLengths = listOf(1.8f, 4.7f),
            sensorW = 6.4f,
            sensorH = 4.8f
        )
        val physMain = snapshot(
            displayId = "0 -> physical 2",
            openId = "0",
            physicalId = "2",
            parentLogicalId = "0",
            inPublicList = false,
            focalLengths = listOf(4.7f),
            sensorW = 6.4f,
            sensorH = 4.8f
        )
        val physUltraWide = snapshot(
            displayId = "0 -> physical 3",
            openId = "0",
            physicalId = "3",
            parentLogicalId = "0",
            inPublicList = false,
            focalLengths = listOf(1.8f),
            sensorW = 4.5f,
            sensorH = 3.4f
        )

        val report = CameraCharacteristicsInspector.evaluateCameras(listOf(logical, physMain, physUltraWide))

        assertEquals(VerificationStatus.VERIFIED_ULTRA_WIDE, report.status)
        assertEquals("0", report.selectedUltraWide?.openCameraId)
        assertEquals("3", report.selectedUltraWide?.physicalCameraId)
        assertEquals(LensAccessMethod.LOGICAL_PHYSICAL_STREAM, report.selectedUltraWide?.accessMethod)
    }

    @Test
    fun `restricted firmware exposing only main and macro does not fake ultra-wide`() {
        val main = snapshot("0", focalLengths = listOf(4.7f), sensorW = 6.4f, sensorH = 4.8f)
        val front = snapshot("1", facing = CameraMetadata.LENS_FACING_FRONT, focalLengths = listOf(3.0f), sensorW = 3.6f, sensorH = 2.7f)
        // 2MP macro sensor with short physical focal length (2.2mm) on tiny sensor (2.2x1.65mm -> ~53 deg HFOV, NOT ultra-wide!)
        val macro = snapshot(
            "2",
            focalLengths = listOf(2.2f),
            sensorW = 2.2f,
            sensorH = 1.65f,
            yuvSizes = listOf(1600 to 1200)
        )

        val report = CameraCharacteristicsInspector.evaluateCameras(listOf(main, front, macro))

        assertEquals(VerificationStatus.NO_ULTRA_WIDE_CAMERA_FOUND, report.status)
        assertNull(report.selectedUltraWide)
    }

    @Test
    fun `non-backward-compatible ultra-wide reports not accessible`() {
        val main = snapshot("0", focalLengths = listOf(4.7f), sensorW = 6.4f, sensorH = 4.8f)
        val blockedUw = snapshot(
            "2",
            focalLengths = listOf(1.6f),
            sensorW = 3.6f,
            sensorH = 2.7f,
            backwardCompatible = false
        )

        val report = CameraCharacteristicsInspector.evaluateCameras(listOf(main, blockedUw))

        assertEquals(VerificationStatus.ULTRA_WIDE_NOT_ACCESSIBLE, report.status)
        assertNull(report.selectedUltraWide)
    }
}
