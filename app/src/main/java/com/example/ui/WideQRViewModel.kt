package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.CameraPreviewController
import com.example.camera.CameraRepository
import com.example.camera.CameraSessionState
import com.example.links.RedirectResolver
import com.example.models.CameraDiscoveryReport
import com.example.models.CameraHardwareInfo
import com.example.models.RedirectInspectionState
import com.example.models.ScanResult
import com.example.scanner.QRDecoder
import com.example.scanner.ScanResultProcessor
import com.example.settings.ThemePreferencesRepository
import com.example.ui.theme.AppThemeMode
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

    val themeMode: StateFlow<AppThemeMode> = themePreferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppThemeMode.SYSTEM
    )

    val discoveryReport: StateFlow<CameraDiscoveryReport> = cameraRepository.discoveryReport
    val unavailableCameraIds: StateFlow<Set<String>> = cameraRepository.unavailableCameraIds

    private val scannerCameraController = CameraPreviewController(appContext) { image, rotation, onDone ->
        qrDecoder.analyzeCameraImage(
            image = image,
            rotationDegrees = rotation,
            onDone = onDone,
            onQrDetected = { rawPayload ->
                viewModelScope.launch {
                    val lensName = discoveryReport.value.selectedUltraWide?.let {
                        "Ultra-wide camera (ID: ${it.displayId})"
                    } ?: "Ultra-wide camera"
                    val result = ScanResultProcessor.process(rawPayload, lensName)
                    _latestScanResult.value = result
                    _redirectState.value = RedirectInspectionState.Idle
                    _scanDetectedEvents.emit(result)
                }
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

    init {
        cameraRepository.startAvailabilityMonitoring()
    }

    fun refreshCameraDiscovery(): CameraDiscoveryReport {
        return cameraRepository.refreshHardwareDiscovery()
    }

    fun startScannerPreview(textureView: TextureView) {
        val report = cameraRepository.refreshHardwareDiscovery()
        val selected = report.selectedUltraWide
        if (report.status.isUsableForScanner && selected != null) {
            qrDecoder.resume()
            scannerCameraController.startCamera(selected, textureView)
        } else {
            scannerCameraController.closeCamera()
        }
    }

    fun stopScannerPreview() {
        scannerCameraController.closeCamera()
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
