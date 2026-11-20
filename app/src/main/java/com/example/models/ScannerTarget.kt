package com.example.models

/**
 * Which physical lens the scanner is currently driving.
 *
 * [MainCameraFallback] is only ever used when the user explicitly opted in
 * (Settings → "Allow 1x main camera fallback") *and* the ultra-wide lens is not
 * usable on this device. The UI must always label it clearly.
 */
sealed interface ScannerTarget {
    data object Unavailable : ScannerTarget

    data class UltraWide(
        val camera: CameraHardwareInfo,
        val lensLabel: String
    ) : ScannerTarget

    data class MainCameraFallback(
        val camera: CameraHardwareInfo,
        val lensLabel: String = "Main camera (1x fallback)"
    ) : ScannerTarget

    val isFallback: Boolean
        get() = this is MainCameraFallback

    val cameraInfo: CameraHardwareInfo?
        get() = when (this) {
            is UltraWide -> camera
            is MainCameraFallback -> camera
            Unavailable -> null
        }
}
