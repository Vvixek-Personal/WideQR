package com.example.ui

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.view.TextureView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.CameraPreviewController
import com.example.camera.CameraRepository
import com.example.camera.CameraSessionState
import com.example.history.ScanHistoryEntity
import com.example.history.ScanHistoryRepository
import com.example.links.RedirectResolver
import com.example.models.CameraDiscoveryReport
import com.example.models.CameraHardwareInfo
import com.example.models.RedirectInspectionState
import com.example.models.ScanResult
import com.example.models.ScannerTarget
import com.example.scanner.QRDecoder
import com.example.scanner.ScanResultProcessor
import com.example.settings.ThemePreferencesRepository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WideQRViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val cameraRepository = CameraRepository(appContext)
    private val themePreferences = ThemePreferencesRepository(appContext)
    private val redirectResolver = RedirectResolver()
    private val qrDecoder = QRDecoder()
    private val scanHistoryRepository = ScanHistoryRepository(appContext)

    val themeMode: StateFlow<AppThemeMode> = themePreferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppThemeMode.SYSTEM
    )

    // Eagerly collected: startScannerPreview() reads the cached value synchronously.
    val allowMainCameraFallback: StateFlow<Boolean> =
        themePreferences.allowMainCameraFallback.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val saveScanHistory: StateFlow<Boolean> = themePreferences.saveScanHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val scanProductBarcodes: StateFlow<Boolean> = themePreferences.scanProductBarcodes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val scanHistory: Flow<List<ScanHistoryEntity>>
        get() = scanHistoryRepository.history

    val discoveryReport: StateFlow<CameraDiscoveryReport> = cameraRepository.discoveryReport
    val unavailableCameraIds: StateFlow<Set<String>> = cameraRepository.unavailableCameraIds

    private val scannerCameraController = CameraPreviewController(appContext) { image, rotation, onDone ->
        qrDecoder.analyzeCameraImage(
            image = image,
            rotationDegrees = rotation,
            onDone = onDone,
            onQrDetected = { rawPayload ->
                val lensName = when (val target = scannerTarget.value) {
                    is ScannerTarget.UltraWide ->
                        "Ultra-wide camera (ID: ${target.camera.displayId})"
                    is ScannerTarget.MainCameraFallback ->
                        "Main camera 1x fallback (ID: ${target.camera.displayId})"
                    ScannerTarget.Unavailable -> "Camera"
                }
                val result = ScanResultProcessor.process(rawPayload, lensName)
                _latestScanResult.value = result
                _redirectState.value = RedirectInspectionState.Idle
                _scanDetectedEvents.tryEmit(result)
                recordHistoryIfEnabled(result)
            }
        )
    }

    private val diagnosticPreviewController = CameraPreviewController(appContext, onImageFrame = null)

    val scannerSessionState: StateFlow<CameraSessionState> = scannerCameraController.sessionState
    val diagnosticSessionState: StateFlow<CameraSessionState> = diagnosticPreviewController.sessionState

    private val _diagnosticTestingCamera = MutableStateFlow<CameraHardwareInfo?>(null)
    val diagnosticTestingCamera: StateFlow<CameraHardwareInfo?> = _diagnosticTestingCamera.asStateFlow()

    private val _latestScanResult = MutableStateFlow<ScanResult?>(null)
    val latestScanResult: StateFlow<ScanResult?> = _latestScanResult.asStateFlow()

    private val _redirectState = MutableStateFlow<RedirectInspectionState>(RedirectInspectionState.Idle)
    val redirectState: StateFlow<RedirectInspectionState> = _redirectState.asStateFlow()

    private val _isGalleryDecoding = MutableStateFlow(false)
    val isGalleryDecoding: StateFlow<Boolean> = _isGalleryDecoding.asStateFlow()

    private val _scanDetectedEvents = MutableSharedFlow<ScanResult>(extraBufferCapacity = 1)
    val scanDetectedEvents: SharedFlow<ScanResult> = _scanDetectedEvents.asSharedFlow()

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    private var attachedTextureView: TextureView? = null
    private var lastHandledFallback: Boolean? = null

    private val _scannerTarget = MutableStateFlow<ScannerTarget>(ScannerTarget.Unavailable)
    val scannerTarget: StateFlow<ScannerTarget> = _scannerTarget.asStateFlow()

    init {
        cameraRepository.startAvailabilityMonitoring()
        viewModelScope.launch {
            // Keep the ML Kit client in the mode the user selected.
            themePreferences.scanProductBarcodes.collect { enabled ->
                qrDecoder.setProductBarcodesEnabled(enabled)
            }
        }
    }

    fun refreshCameraDiscovery(): CameraDiscoveryReport {
        val report = cameraRepository.refreshHardwareDiscovery()
        // Resolve the lens target up front so the UI can decide whether to
        // attach a preview surface at all (needed before any TextureView exists).
        _scannerTarget.value = resolveScannerTarget(report)
        return report
    }

    fun startScannerPreview(textureView: TextureView) {
        attachedTextureView = textureView
        val report = cameraRepository.refreshHardwareDiscovery()
        val target = resolveScannerTarget(report)
        _scannerTarget.value = target

        val camera = target.cameraInfo
        if (camera != null) {
            qrDecoder.warmUp()
            qrDecoder.resume()
            scannerCameraController.startCamera(camera, textureView)
        } else {
            scannerCameraController.closeCamera()
        }
    }

    /**
     * Ultra-wide first. The 1x main camera is used only when the user turned on
     * "Allow 1x main camera fallback" *and* the ultra-wide lens is unusable.
     */
    private fun resolveScannerTarget(
        report: CameraDiscoveryReport
    ): ScannerTarget {
        val selected = report.selectedUltraWide
        if (report.status.isUsableForScanner && selected != null) {
            return ScannerTarget.UltraWide(
                camera = selected,
                lensLabel = "Ultra-wide camera"
            )
        }
        // Use the eagerly-cached setting; the DataStore-backed flow resolves in
        // well under a frame, and blocking here keeps the TextureView start path
        // free of coroutine dispatch races.
        val fallbackAllowed = allowMainCameraFallback.value
        if (!fallbackAllowed) return ScannerTarget.Unavailable

        val main = report.referenceMainCamera
            ?.takeIf { it.canOpenForPreview && it.lensFacing == CameraCharacteristics.LENS_FACING_BACK }
            ?: report.allCameras.firstOrNull {
                it.lensFacing == CameraCharacteristics.LENS_FACING_BACK && it.canOpenForPreview
            }
            ?: return ScannerTarget.Unavailable

        return ScannerTarget.MainCameraFallback(camera = main)
    }

    fun stopScannerPreview() {
        scannerCameraController.closeCamera()
        attachedTextureView = null
    }

    /**
     * Reacts to the "Allow 1x main camera fallback" preference changing while the
     * scanner is visible: re-resolves the lens and restarts the preview only when
     * the effective camera actually changes.
     */
    fun onFallbackPreferenceChanged() {
        val enabled = allowMainCameraFallback.value
        if (lastHandledFallback == enabled) return
        lastHandledFallback = enabled

        val report = cameraRepository.refreshHardwareDiscovery()
        val previous = _scannerTarget.value
        val updated = resolveScannerTarget(report)
        _scannerTarget.value = updated

        if (previous.cameraInfo?.openCameraId == updated.cameraInfo?.openCameraId) return

        val tv = attachedTextureView
        val camera = updated.cameraInfo
        if (tv != null && camera != null) {
            qrDecoder.resume()
            scannerCameraController.startCamera(camera, tv)
        } else {
            scannerCameraController.closeCamera()
        }
    }

    fun refreshPreviewOrientation() {
        scannerCameraController.refreshOrientation()
        diagnosticPreviewController.refreshOrientation()
    }

    fun toggleTorch() {
        val current = scannerSessionState.value
        if (current is CameraSessionState.Streaming) {
            scannerCameraController.setTorchEnabled(!current.isTorchEnabled)
        }
    }

    fun resumeScanning() {
        _redirectState.value = RedirectInspectionState.Idle
        qrDecoder.resume()
    }

    /**
     * Tap-to-meter. Always meters exposure (AE region); autofocus is only attempted
     * on lenses that actually report AF support, since ultra-wides are often fixed-focus.
     */
    fun focusOnTap(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        // Silent, like a native camera: the preview brightening is the feedback.
        scannerCameraController.focusOnTap(x, y, viewWidth, viewHeight)
    }

    /**
     * Text handed to us through the share sheet (ACTION_SEND, text/plain).
     * Parsed synchronously with the same pipeline as a camera scan, then shown
     * on the result screen. No camera event is emitted so navigation happens once.
     */
    fun acceptSharedText(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        val result = ScanResultProcessor.process(cleaned, "Shared from another app")
        _latestScanResult.value = result
        _redirectState.value = RedirectInspectionState.Idle
        recordHistoryIfEnabled(result)
    }

    private fun recordHistoryIfEnabled(result: ScanResult) {
        if (!saveScanHistory.value) return
        viewModelScope.launch {
            scanHistoryRepository.record(
                rawValue = result.rawValue,
                contentTypeLabel = result.contentType.label,
                sourceLabel = result.sourceLabel
            )
        }
    }

    fun clearScanHistory() {
        viewModelScope.launch {
            scanHistoryRepository.clearAll()
            emitMessage(appContext.getString(com.example.R.string.settings_history_cleared))
        }
    }

    fun setAllowMainCameraFallback(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setAllowMainCameraFallback(enabled)
        }
    }

    fun setSaveScanHistory(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setSaveScanHistory(enabled)
        }
    }

    fun setScanProductBarcodes(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setScanProductBarcodes(enabled)
        }
    }

    fun decodeGalleryImage(uri: Uri) {
        viewModelScope.launch {
            _isGalleryDecoding.value = true
            qrDecoder.pause()
            val decoded = qrDecoder.decodeFromUri(appContext, uri)
            _isGalleryDecoding.value = false

            decoded.fold(
                onSuccess = { raw ->
                    if (raw != null) {
                        val result = ScanResultProcessor.process(raw, "Gallery image")
                        _latestScanResult.value = result
                        _redirectState.value = RedirectInspectionState.Idle
                        _scanDetectedEvents.emit(result)
                        recordHistoryIfEnabled(result)
                    } else {
                        qrDecoder.resume()
                        _uiMessages.emit("Could not find a readable QR code in that image.")
                    }
                },
                onFailure = {
                    qrDecoder.resume()
                    _uiMessages.emit("Could not open the selected image.")
                }
            )
        }
    }

    fun inspectRedirects(url: String) {
        viewModelScope.launch {
            _redirectState.value = RedirectInspectionState.Loading
            _redirectState.value = redirectResolver.inspectRedirects(url)
        }
    }

    fun selectDiagnosticCamera(cameraInfo: CameraHardwareInfo) {
        scannerCameraController.closeCamera()
        _diagnosticTestingCamera.value = cameraInfo
    }

    fun startDiagnosticPreview(cameraInfo: CameraHardwareInfo, textureView: TextureView) {
        // Ensure scanner camera isn't holding the hardware simultaneously
        scannerCameraController.closeCamera()
        _diagnosticTestingCamera.value = cameraInfo
        diagnosticPreviewController.startCamera(cameraInfo, textureView)
    }

    fun stopDiagnosticPreview() {
        diagnosticPreviewController.closeCamera()
        _diagnosticTestingCamera.value = null
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun emitMessage(message: String) {
        viewModelScope.launch {
            _uiMessages.emit(message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        scannerCameraController.closeCamera()
        diagnosticPreviewController.closeCamera()
        cameraRepository.stopAvailabilityMonitoring()
        qrDecoder.close()
    }
}
