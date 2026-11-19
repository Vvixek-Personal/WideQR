package com.example.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.example.models.CameraDiscoveryReport
import com.example.models.VerificationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraRepository(context: Context) {

    private val discovery = CameraDiscovery(context)
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val _discoveryReport = MutableStateFlow(
        CameraDiscoveryReport(
            status = VerificationStatus.CAMERA_ACCESS_ERROR,
            selectedUltraWide = null,
            referenceMainCamera = null,
            allCameras = emptyList(),
            statusHeadline = "Inspecting lenses",
            statusBadgeText = "Checking",
            statusExplanation = "Querying Camera2 hardware capabilities."
        )
    )
    val discoveryReport: StateFlow<CameraDiscoveryReport> = _discoveryReport.asStateFlow()

    private val _unavailableCameraIds = MutableStateFlow<Set<String>>(emptySet())
    val unavailableCameraIds: StateFlow<Set<String>> = _unavailableCameraIds.asStateFlow()

    private var availabilityCallback: CameraManager.AvailabilityCallback? = null

    fun refreshHardwareDiscovery(): CameraDiscoveryReport {
        val report = discovery.discover()
        _discoveryReport.value = report
        return report
    }

    fun startAvailabilityMonitoring() {
        val manager = cameraManager ?: return
        if (availabilityCallback != null) return

        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                _unavailableCameraIds.value = _unavailableCameraIds.value - cameraId
            }

            override fun onCameraUnavailable(cameraId: String) {
                _unavailableCameraIds.value = _unavailableCameraIds.value + cameraId
            }
        }
        availabilityCallback = callback
        manager.registerAvailabilityCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun stopAvailabilityMonitoring() {
        val manager = cameraManager ?: return
        availabilityCallback?.let {
            try {
                manager.unregisterAvailabilityCallback(it)
            } catch (_: Exception) {
            }
        }
        availabilityCallback = null
    }
}
