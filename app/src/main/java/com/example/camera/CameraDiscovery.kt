package com.example.camera

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import com.example.models.CameraDiscoveryReport
import com.example.models.VerificationStatus

class CameraDiscovery(private val context: Context) {

    fun discover(): CameraDiscoveryReport {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return CameraDiscoveryReport(
                status = VerificationStatus.CAMERA_ACCESS_ERROR,
                selectedUltraWide = null,
                referenceMainCamera = null,
                allCameras = emptyList(),
                statusHeadline = "Camera service unavailable",
                statusBadgeText = "Camera error",
                statusExplanation = "Android CameraManager service is not available on this device."
            )

        return try {
            val publicIds = cameraManager.cameraIdList.toList()
            val publicIdSet = publicIds.toSet()
            val snapshots = mutableListOf<RawCameraSnapshot>()
            val seenPhysicalIds = mutableSetOf<String>()

            for (id in publicIds) {
                val snap = CameraCharacteristicsInspector.extractSnapshot(
                    cameraManager = cameraManager,
                    cameraId = id,
                    isInPublicList = true
                )
                if (snap != null) {
                    snapshots.add(snap)

                    // Inspect physical sub-cameras inside logical multi-cameras (API 28+)
                    // only for physical IDs not already listed as standalone public IDs
                    for (physicalId in snap.underlyingPhysicalIds) {
                        if (!publicIdSet.contains(physicalId) && seenPhysicalIds.add("$id:$physicalId")) {
                            val physicalSnap = CameraCharacteristicsInspector.extractSnapshot(
                                cameraManager = cameraManager,
                                cameraId = physicalId,
                                isInPublicList = false,
                                parentLogicalCameraId = id,
                                parentFlashAvailable = snap.hasFlashUnit
                            )
                            if (physicalSnap != null) {
                                snapshots.add(physicalSnap)
                            }
                        }
                    }
                }
            }

            CameraCharacteristicsInspector.evaluateCameras(snapshots)
        } catch (e: SecurityException) {
            CameraDiscoveryReport(
                status = VerificationStatus.CAMERA_ACCESS_ERROR,
                selectedUltraWide = null,
                referenceMainCamera = null,
                allCameras = emptyList(),
                statusHeadline = "Camera permission required",
                statusBadgeText = "Permission needed",
                statusExplanation = "Camera permission is required to inspect camera hardware and open the ultra-wide lens.",
                errorDetail = e.message
            )
        } catch (e: CameraAccessException) {
            CameraDiscoveryReport(
                status = VerificationStatus.CAMERA_ACCESS_ERROR,
                selectedUltraWide = null,
                referenceMainCamera = null,
                allCameras = emptyList(),
                statusHeadline = "Camera hardware error",
                statusBadgeText = "Access error",
                statusExplanation = "Failed to query camera hardware characteristics from Android Camera2.",
                errorDetail = e.message
            )
        } catch (e: Exception) {
            CameraDiscoveryReport(
                status = VerificationStatus.CAMERA_ACCESS_ERROR,
                selectedUltraWide = null,
                referenceMainCamera = null,
                allCameras = emptyList(),
                statusHeadline = "Camera inspection failed",
                statusBadgeText = "Access error",
                statusExplanation = "An unexpected error occurred while inspecting device camera lenses.",
                errorDetail = e.message
            )
        }
    }
}
