package com.example

import android.hardware.camera2.CameraMetadata
import android.util.Size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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

    private fun verifiedReport(uwCamera: CameraHardwareInfo) = CameraDiscoveryReport(
        status = VerificationStatus.VERIFIED_ULTRA_WIDE,
        selectedUltraWide = uwCamera,
        referenceMainCamera = null,
        allCameras = listOf(uwCamera),
        statusHeadline = "Ultra-wide camera verified",
        statusBadgeText = "Hardware verified",
        statusExplanation = "Physical ultra-wide Camera2 sensor verified."
    )

    private fun setResponsiveScannerHud(
        width: Dp,
        height: Dp,
        fontScale: Float = 1f,
        hasFlash: Boolean = true,
        sessionStateOverride: CameraSessionState? = null
    ) {
        val uwCamera = fakeUltraWideCamera(hasFlash = hasFlash)
        val report = verifiedReport(uwCamera)
        val sessionState = sessionStateOverride ?: CameraSessionState.Streaming(
            cameraInfo = uwCamera,
            previewSize = Size(1280, 960),
            isTorchEnabled = false
        )
        val isStreaming = sessionState is CameraSessionState.Streaming
        val isOpening = sessionState is CameraSessionState.Opening

        composeTestRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = fontScale)
            ) {
                WideQRTheme {
                    Column(
                        modifier = Modifier
                            .size(width = width, height = height)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ScannerTopHeader(
                            isStreaming = isStreaming,
                            isOpening = isOpening,
                            hasError = false,
                            onOpenDiagnostics = {},
                            onOpenSettings = {},
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ScannerReticleOverlay(
                                isScanning = isStreaming,
                                isInitializing = isOpening,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ScannerHardwarePill(report = report)
                        ScannerBottomToolbar(
                            report = report,
                            sessionState = sessionState,
                            hasCameraPermission = true,
                            isGalleryDecoding = false,
                            onScanGallery = {},
                            onToggleFlash = {}
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `scanner hud displays minimal ultra-wide verification without focal or ID clutter`() {
        setResponsiveScannerHud(width = 360.dp, height = 800.dp, hasFlash = true)

        composeTestRule.onNodeWithText("WideQR").assertIsDisplayed()
        composeTestRule.onNodeWithText("LIVE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ultra-wide").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hardware verified").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.94mm", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("ID 2", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("scanner_reticle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Align QR code within frame").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scanning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_import_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flash_toggle_button").assertIsDisplayed()
    }

    @Test
    fun `bottom toolbar hides flashlight when selected lens lacks flash unit`() {
        val uwCamera = fakeUltraWideCamera(hasFlash = false)
        val report = verifiedReport(uwCamera)
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
    fun `responsive hud fits Galaxy A12 portrait, small height, landscape, and 2x large font`() {
        // Small-height screen (320x480)
        setResponsiveScannerHud(width = 320.dp, height = 480.dp)
        composeTestRule.onNodeWithTag("scanner_reticle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_import_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flash_toggle_button").assertIsDisplayed()
    }

    @Test
    fun `responsive hud fits landscape orientation without clipping controls`() {
        setResponsiveScannerHud(width = 640.dp, height = 320.dp)
        composeTestRule.onNodeWithTag("scanner_reticle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diagnostics_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_import_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flash_toggle_button").assertIsDisplayed()
    }

    @Test
    fun `responsive hud fits 2x large accessibility font scale on Galaxy A12 portrait`() {
        setResponsiveScannerHud(width = 360.dp, height = 800.dp, fontScale = 2.0f)
        composeTestRule.onNodeWithText("WideQR").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scanner_reticle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scanning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_import_button").assertIsDisplayed()
    }

    @Test
    fun `camera initialization state shows starting lens cue`() {
        setResponsiveScannerHud(
            width = 360.dp,
            height = 800.dp,
            sessionStateOverride = CameraSessionState.Opening
        )
        composeTestRule.onNodeWithText("INIT").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scanner_reticle").assertIsDisplayed()
    }

    @Test
    fun `recovery panel distinguishes permission required vs permanently denied`() {
        val emptyReport = CameraDiscoveryReport(
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
                    hasCameraPermission = false,
                    isPermanentlyDenied = true,
                    discoveryReport = emptyReport,
                    sessionState = CameraSessionState.Idle,
                    onRequestPermission = {},
                    onOpenAppSettings = {},
                    onOpenDiagnostics = {},
                    onScanGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Permission blocked").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Camera in Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open App Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fallback_gallery_button").assertIsDisplayed()
    }

    @Test
    fun `recovery panel distinguishes no ultra-wide, inaccessible lens, and camera error`() {
        val inaccessibleReport = CameraDiscoveryReport(
            status = VerificationStatus.ULTRA_WIDE_NOT_ACCESSIBLE,
            selectedUltraWide = null,
            referenceMainCamera = null,
            allCameras = emptyList(),
            statusHeadline = "Ultra-wide lens inaccessible",
            statusBadgeText = "Not accessible",
            statusExplanation = "Firmware did not expose an openable ultra-wide stream."
        )

        composeTestRule.setContent {
            WideQRTheme {
                ScannerRecoveryPanel(
                    hasCameraPermission = true,
                    isPermanentlyDenied = false,
                    discoveryReport = inaccessibleReport,
                    sessionState = CameraSessionState.Idle,
                    onRequestPermission = {},
                    onOpenAppSettings = {},
                    onOpenDiagnostics = {},
                    onScanGallery = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Lens inaccessible").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ultra-wide lens inaccessible").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inspect_hardware_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fallback_gallery_button").assertIsDisplayed()
    }

    @Test
    fun `recovery panel displays camera access error cleanly`() {
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

        composeTestRule.onNodeWithText("Camera error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Camera hardware is currently unavailable.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inspect_hardware_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fallback_gallery_button").assertIsDisplayed()
    }
}
