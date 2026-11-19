package com.example

import android.hardware.camera2.CameraMetadata
import android.util.Size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.camera.CameraSessionState
import com.example.models.CameraDiscoveryReport
import com.example.models.CameraHardwareInfo
import com.example.models.LensAccessMethod
import com.example.models.LensType
import com.example.models.VerificationStatus
import com.example.ui.ScannerBottomToolbar
import com.example.ui.ScannerHardwarePill
import com.example.ui.ScannerRecoveryPanel
import com.example.ui.ScannerReticleOverlay
import com.example.ui.ScannerTopHeader
import com.example.ui.theme.WideQRTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScannerHudComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fakeUltraWideCamera(hasFlash: Boolean): CameraHardwareInfo = CameraHardwareInfo(
        displayId = "2",
        openCameraId = "2",
        physicalCameraId = null,
        parentLogicalCameraId = null,
        isLogicalMultiCamera = false,
        underlyingPhysicalIds = emptyList(),
        lensFacing = CameraMetadata.LENS_FACING_BACK,
        lensFacingLabel = "Back",
        focalLengthsMm = listOf(1.94f),
        primaryFocalLengthMm = 1.94f,
        sensorPhysicalWidthMm = 4.5f,
        sensorPhysicalHeightMm = 3.4f,
        horizontalFovDeg = 118f,
        verticalFovDeg = 95f,
        diagonalFovDeg = 123f,
        estimated35mmEquivalentMm = 14f,
        relativeToMainRatio = 0.54f,
        sensorOrientationDeg = 90,
        maxDigitalZoom = 4f,
        hardwareLevelLabel = "LIMITED",
        isBackwardCompatible = true,
        maxYuvSize = 2576 to 1932,
        previewSizes = listOf(1280 to 960),
        yuvSizes = listOf(960 to 720),
        afModes = listOf(CameraMetadata.CONTROL_AF_MODE_OFF),
        supportsAutofocus = false,
        isFixedFocus = true,
        hasFlashUnit = hasFlash,
        minimumFocusDistance = 0f,
        supportsDistortionCorrection = false,
        lensType = LensType.ULTRA_WIDE,
        accessMethod = LensAccessMethod.DIRECT_CAMERA_ID,
        classificationReason = "Verified ultra-wide"
    )

    @Test
    fun `scanner hud shows real lens metadata, reticle, and flash when supported`() {
        val uwCamera = fakeUltraWideCamera(hasFlash = true)
        val report = CameraDiscoveryReport(
            status = VerificationStatus.VERIFIED_ULTRA_WIDE,
            selectedUltraWide = uwCamera,
            referenceMainCamera = null,
            allCameras = listOf(uwCamera),
            statusHeadline = "Ultra-wide camera verified",
            statusBadgeText = "Hardware verified",
            statusExplanation = "Physical ultra-wide Camera2 sensor verified."
        )
        val streaming = CameraSessionState.Streaming(
            cameraInfo = uwCamera,
            previewSize = Size(1280, 960),
            isTorchEnabled = false
        )

        composeTestRule.setContent {
            WideQRTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScannerTopHeader(
                        isStreaming = true,
                        isOpening = false,
                        hasError = false,
                        onOpenDiagnostics = {},
                        onOpenSettings = {}
                    )
                    ScannerReticleOverlay(
                        isScanning = true,
                        modifier = Modifier.weight(1f)
                    )
                    ScannerHardwarePill(report = report)
                    ScannerBottomToolbar(
                        report = report,
                        sessionState = streaming,
                        hasCameraPermission = true,
                        isGalleryDecoding = false,
                        onScanGallery = {},
                        onToggleFlash = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Hardware verified").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ultra-wide · ID 2 · 1.94mm · ~118°").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scanner_reticle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Align QR code within frame").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scanning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_import_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flash_toggle_button").assertIsDisplayed()
    }

    @Test
    fun `bottom toolbar hides flashlight when selected lens lacks flash unit`() {
        val uwCamera = fakeUltraWideCamera(hasFlash = false)
        val report = CameraDiscoveryReport(
            status = VerificationStatus.VERIFIED_ULTRA_WIDE,
            selectedUltraWide = uwCamera,
            referenceMainCamera = null,
            allCameras = listOf(uwCamera),
            statusHeadline = "Ultra-wide camera verified",
            statusBadgeText = "Hardware verified",
            statusExplanation = "Physical ultra-wide Camera2 sensor verified."
        )
        val streaming = CameraSessionState.Streaming(
            cameraInfo = uwCamera,
            previewSize = Size(1280, 960),
            isTorchEnabled = false
        )

        composeTestRule.setContent {
            WideQRTheme {
                ScannerBottomToolbar(
                    report = report,
                    sessionState = streaming,
                    hasCameraPermission = true,
                    isGalleryDecoding = false,
                    onScanGallery = {},
                    onToggleFlash = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("gallery_import_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flash_toggle_button").assertDoesNotExist()
    }

    @Test
    fun `compact viewport reticle fits without clipping instruction pill`() {
        composeTestRule.setContent {
            WideQRTheme {
                ScannerReticleOverlay(
                    isScanning = true,
                    modifier = Modifier.size(width = 320.dp, height = 220.dp)
                )
            }
        }

        composeTestRule.onNodeWithTag("scanner_reticle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Align QR code within frame").assertIsDisplayed()
    }

    @Test
    fun `recovery panel displays error state and hardware diagnostics CTA`() {
        val report = CameraDiscoveryReport(
            status = VerificationStatus.NO_ULTRA_WIDE_CAMERA_FOUND,
            selectedUltraWide = null,
            referenceMainCamera = null,
            allCameras = emptyList(),
            statusHeadline = "No ultra-wide camera found",
            statusBadgeText = "No ultra-wide found",
            statusExplanation = "Device did not report a secondary ultra-wide sensor."
        )

        composeTestRule.setContent {
            WideQRTheme {
                ScannerRecoveryPanel(
                    hasCameraPermission = true,
                    isPermanentlyDenied = false,
                    discoveryReport = report,
                    sessionState = CameraSessionState.Error("Camera hardware is currently unavailable."),
                    onRequestPermission = {},
                    onOpenAppSettings = {},
                    onOpenDiagnostics = {},
                    onScanGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No ultra-wide camera found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Camera hardware is currently unavailable.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inspect_hardware_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fallback_gallery_button").assertIsDisplayed()
    }
}
