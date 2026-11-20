package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import com.example.BuildConfig
import com.example.models.CameraHardwareInfo
import com.example.models.LensAccessMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

sealed interface CameraSessionState {
    data object Idle : CameraSessionState
    data object Opening : CameraSessionState
    data class Streaming(
        val cameraInfo: CameraHardwareInfo,
        val previewSize: Size,
        val isTorchEnabled: Boolean
    ) : CameraSessionState

    data class Error(
        val message: String,
        val isAccessLimitation: Boolean = false
    ) : CameraSessionState
}

class CameraPreviewController(
    private val context: Context,
    private val onImageFrame: ((image: Image, rotationDegrees: Int, onDone: () -> Unit) -> Unit)? = null
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var analysisThread: HandlerThread? = null
    private var analysisHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null

    private var currentCamera: CameraHardwareInfo? = null
    private var currentTextureView: TextureView? = null
    private var currentPreviewSize: Size = Size(1280, 960)
    @Volatile
    private var cachedImageRotationDegrees: Int = 0
    private var torchEnabled: Boolean = false
    private val isClosing = AtomicBoolean(false)

    private val _sessionState = MutableStateFlow<CameraSessionState>(CameraSessionState.Idle)
    val sessionState: StateFlow<CameraSessionState> = _sessionState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startCamera(
        cameraInfo: CameraHardwareInfo,
        textureView: TextureView
    ) {
        val manager = cameraManager ?: run {
            _sessionState.value = CameraSessionState.Error("Camera service is unavailable.")
            return
        }

        if (!cameraInfo.canOpenForPreview) {
            _sessionState.value = CameraSessionState.Error(
                message = "Selected lens (${cameraInfo.displayId}) is not openable through Camera2.",
                isAccessLimitation = true
            )
            return
        }

        closeCamera()
        isClosing.set(false)
        currentCamera = cameraInfo
        currentTextureView = textureView
        torchEnabled = false
        _sessionState.value = CameraSessionState.Opening

        startBackgroundThread()

        if (textureView.isAvailable) {
            openCameraDevice(manager, cameraInfo, textureView)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    if (!isClosing.get() && currentCamera?.displayId == cameraInfo.displayId) {
                        openCameraDevice(manager, cameraInfo, textureView)
                    }
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    configureTextureTransform(textureView, currentPreviewSize, cameraInfo)
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCameraDevice(
        manager: CameraManager,
        cameraInfo: CameraHardwareInfo,
        textureView: TextureView
    ) {
        val handler = backgroundHandler ?: return
        try {
            manager.openCamera(
                cameraInfo.openCameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (isClosing.get()) {
                            camera.close()
                            return
                        }
                        cameraDevice = camera
                        createCameraPreviewSession(camera, cameraInfo, textureView)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                        _sessionState.value = CameraSessionState.Error(
                            "Camera was disconnected."
                        )
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        val (msg, limitation) = mapCameraError(error)
                        _sessionState.value = CameraSessionState.Error(msg, limitation)
                    }
                },
                handler
            )
        } catch (e: SecurityException) {
            _sessionState.value = CameraSessionState.Error(
                "Camera permission is required to scan QR codes."
            )
        } catch (e: CameraAccessException) {
            logDebug("CameraAccessException opening ${cameraInfo.openCameraId}: ${e.message}")
            _sessionState.value = CameraSessionState.Error(
                "Camera hardware is currently unavailable.",
                isAccessLimitation = true
            )
        } catch (e: IllegalArgumentException) {
            logDebug("IllegalArgumentException opening ${cameraInfo.openCameraId}: ${e.message}")
            _sessionState.value = CameraSessionState.Error(
                "Ultra-wide camera ID is not accepted by this device's Camera2 driver.",
                isAccessLimitation = true
            )
        }
    }

    private fun createCameraPreviewSession(
        camera: CameraDevice,
        cameraInfo: CameraHardwareInfo,
        textureView: TextureView
    ) {
        val handler = backgroundHandler ?: return
        val surfaceTexture = textureView.surfaceTexture ?: run {
            _sessionState.value = CameraSessionState.Error("Preview surface is not ready.")
            return
        }

        // Cache sensor metering geometry for tap-to-meter (AE/AF regions).
        try {
            val chars = cameraManager?.getCameraCharacteristics(cameraInfo.openCameraId)
            activeArraySize = chars?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            maxAeRegions = chars?.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 1
            maxAfRegions = chars?.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
        } catch (_: Exception) {
            activeArraySize = null
            maxAeRegions = 1
            maxAfRegions = 0
        }

        val previewSize = PhysicalCameraSelector.choosePreviewSize(cameraInfo.previewSizes)
        currentPreviewSize = previewSize
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        textureView.post {
            configureTextureTransform(textureView, previewSize, cameraInfo)
        }

        val pSurface = Surface(surfaceTexture)
        previewSurface = pSurface
        val surfaces = mutableListOf(pSurface)

        var analysisSurface: Surface? = null
        if (onImageFrame != null) {
            cachedImageRotationDegrees = computeImageRotationDegrees(cameraInfo)
            val yuvSize = PhysicalCameraSelector.chooseAnalysisSize(cameraInfo.yuvSizes, previewSize)
            val reader = ImageReader.newInstance(
                yuvSize.width,
                yuvSize.height,
                ImageFormat.YUV_420_888,
                3
            )
            reader.setOnImageAvailableListener({ r ->
                val image = try {
                    r.acquireLatestImage()
                } catch (_: IllegalStateException) {
                    null
                }
                if (image != null) {
                    onImageFrame.invoke(image, cachedImageRotationDegrees) {
                        try {
                            image.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }, analysisHandler ?: handler)
            imageReader = reader
            analysisSurface = reader.surface
            surfaces.add(reader.surface)
        }

        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(pSurface)
                analysisSurface?.let { addTarget(it) }
                set(
                    CaptureRequest.CONTROL_MODE,
                    CameraMetadata.CONTROL_MODE_AUTO
                )
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    PhysicalCameraSelector.chooseAfMode(cameraInfo)
                )
                set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cameraInfo.supportsDistortionCorrection) {
                    set(
                        CaptureRequest.DISTORTION_CORRECTION_MODE,
                        CameraMetadata.DISTORTION_CORRECTION_MODE_FAST
                    )
                }
            }
            previewRequestBuilder = builder

            val sessionCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (isClosing.get() || cameraDevice == null) {
                        session.close()
                        return
                    }
                    captureSession = session
                    try {
                        session.setRepeatingRequest(builder.build(), null, handler)
                        _sessionState.value = CameraSessionState.Streaming(
                            cameraInfo = cameraInfo,
                            previewSize = previewSize,
                            isTorchEnabled = torchEnabled
                        )
                    } catch (e: CameraAccessException) {
                        logDebug("setRepeatingRequest failed: ${e.message}")
                        _sessionState.value = CameraSessionState.Error(
                            "Could not start camera preview stream."
                        )
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    val isPhysicalSub = cameraInfo.accessMethod == LensAccessMethod.LOGICAL_PHYSICAL_STREAM
                    val msg = if (isPhysicalSub) {
                        "Device rejected physical ultra-wide stream configuration inside logical camera ${cameraInfo.openCameraId}."
                    } else {
                        "Failed to configure camera preview session."
                    }
                    _sessionState.value = CameraSessionState.Error(
                        message = msg,
                        isAccessLimitation = isPhysicalSub
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfigs = surfaces.map { surface ->
                    OutputConfiguration(surface).apply {
                        if (cameraInfo.accessMethod == LensAccessMethod.LOGICAL_PHYSICAL_STREAM &&
                            cameraInfo.physicalCameraId != null
                        ) {
                            setPhysicalCameraId(cameraInfo.physicalCameraId)
                        }
                    }
                }
                val executor = Executor { command -> handler.post(command) }
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    executor,
                    sessionCallback
                )
                camera.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                camera.createCaptureSession(surfaces, sessionCallback, handler)
            }
        } catch (e: Exception) {
            logDebug("Failed creating capture session: ${e.message}")
            _sessionState.value = CameraSessionState.Error(
                "Ultra-wide camera stream could not be initialized.",
                isAccessLimitation = true
            )
        }
    }

    /**
     * Tap-to-meter: maps a TextureView tap to a sensor metering region.
     * Always sets AE (exposure) regions — this is the only meaningful control on
     * fixed-focus ultra-wide lenses (CONTROL_AF_MODE_OFF). AF regions + trigger are
     * only applied when the lens reports autofocus support and AF regions > 0.
     * Returns true when a metering request was applied.
     */
    fun focusOnTap(x: Float, y: Float, viewWidth: Int, viewHeight: Int): Boolean {
        val info = currentCamera ?: return false
        val session = captureSession ?: return false
        val builder = previewRequestBuilder ?: return false
        val handler = backgroundHandler ?: return false
        val array = activeArraySize ?: return false
        if (viewWidth <= 0 || viewHeight <= 0 || array.width() <= 0 || array.height() <= 0) {
            return false
        }
        return try {
            val sensorX = (array.left + (x / viewWidth.toFloat()) * array.width()).toInt()
            val sensorY = (array.top + (y / viewHeight.toFloat()) * array.height()).toInt()
            val half = maxOf(array.width(), array.height()) / 10
            val region = Rect(
                (sensorX - half).coerceIn(array.left, array.right),
                (sensorY - half).coerceIn(array.top, array.bottom),
                (sensorX + half).coerceIn(array.left, array.right),
                (sensorY + half).coerceIn(array.top, array.bottom)
            )
            if (region.width() <= 0 || region.height() <= 0) return false

            val metering = MeteringRectangle(region, MeteringRectangle.METERING_WEIGHT_MAX)
            if (maxAeRegions > 0) {
                builder.set(
                    CaptureRequest.CONTROL_AE_REGIONS,
                    arrayOf(metering)
                )
            }
            // AF regions only apply to a camera opened as a standalone device.
            // Logical/physical streams would need physical-camera keys, and
            // fixed-focus ultra-wides report no AF support at all.
            val canFocus = info.supportsAutofocus &&
                !info.isFixedFocus &&
                info.physicalCameraId == null &&
                maxAfRegions > 0
            if (canFocus) {
                builder.set(
                    CaptureRequest.CONTROL_AF_REGIONS,
                    arrayOf(metering)
                )
                builder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START
                )
            }
            session.setRepeatingRequest(builder.build(), null, handler)

            if (canFocus) {
                // Release the AF lock so continuous focus resumes for scanning.
                val release = Runnable {
                    try {
                        val current = previewRequestBuilder ?: return@Runnable
                        current.set(
                            CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_IDLE
                        )
                        captureSession?.setRepeatingRequest(current.build(), null, handler)
                    } catch (_: Exception) {
                    }
                }
                handler.postDelayed(release, AF_RELEASE_DELAY_MS)
            }
            true
        } catch (e: Exception) {
            logDebug("Tap-to-meter failed: ${e.message}")
            false
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        val info = currentCamera ?: return
        if (!info.hasFlashUnit) return
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val handler = backgroundHandler ?: return

        torchEnabled = enabled
        try {
            builder.set(
                CaptureRequest.FLASH_MODE,
                if (enabled) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF
            )
            session.setRepeatingRequest(builder.build(), null, handler)
            val current = _sessionState.value
            if (current is CameraSessionState.Streaming) {
                _sessionState.value = current.copy(isTorchEnabled = enabled)
            }
        } catch (e: Exception) {
            logDebug("Torch toggle failed: ${e.message}")
        }
    }

    fun refreshOrientation() {
        val tv = currentTextureView ?: return
        val info = currentCamera ?: return
        cachedImageRotationDegrees = computeImageRotationDegrees(info)
        tv.post {
            configureTextureTransform(tv, currentPreviewSize, info)
        }
    }

    fun closeCamera() {
        isClosing.set(true)
        try {
            if (torchEnabled) {
                previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
                previewRequestBuilder?.let { captureSession?.setRepeatingRequest(it.build(), null, null) }
            }
        } catch (_: Exception) {
        }
        torchEnabled = false

        try {
            captureSession?.close()
        } catch (_: Exception) {
        }
        captureSession = null
        previewRequestBuilder = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {
        }
        cameraDevice = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            previewSurface?.release()
        } catch (_: Exception) {
        }
        previewSurface = null

        currentCamera = null
        currentTextureView = null
        stopBackgroundThread()
        _sessionState.value = CameraSessionState.Idle
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            val thread = HandlerThread("WideQRCameraThread").apply { start() }
            backgroundThread = thread
            backgroundHandler = Handler(thread.looper)
        }
        if (onImageFrame != null && analysisThread == null) {
            val aThread = HandlerThread("WideQRAnalysisThread").apply { start() }
            analysisThread = aThread
            analysisHandler = Handler(aThread.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.let { thread ->
            thread.quitSafely()
            try {
                thread.join(350)
            } catch (_: InterruptedException) {
            }
        }
        backgroundThread = null
        backgroundHandler = null

        analysisThread?.let { thread ->
            thread.quitSafely()
            try {
                thread.join(350)
            } catch (_: InterruptedException) {
            }
        }
        analysisThread = null
        analysisHandler = null
    }

    private fun computeImageRotationDegrees(cameraInfo: CameraHardwareInfo): Int {
        val displayRotation = getDisplayRotationDegrees()
        return if (cameraInfo.lensFacing == CameraMetadata.LENS_FACING_FRONT) {
            (cameraInfo.sensorOrientationDeg + displayRotation) % 360
        } else {
            (cameraInfo.sensorOrientationDeg - displayRotation + 360) % 360
        }
    }

    @Suppress("DEPRECATION")
    private fun getDisplayRotationDegrees(): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.rotation ?: Surface.ROTATION_0
            } catch (_: UnsupportedOperationException) {
                wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        } else {
            wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    @Suppress("DEPRECATION")
    private fun configureTextureTransform(
        textureView: TextureView,
        previewSize: Size,
        cameraInfo: CameraHardwareInfo
    ) {
        val viewWidth = textureView.width
        val viewHeight = textureView.height
        if (viewWidth == 0 || viewHeight == 0) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.rotation ?: Surface.ROTATION_0
            } catch (_: UnsupportedOperationException) {
                wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        } else {
            wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }

        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        // Center-crop preview cleanly without distorting aspect ratio
        val sensorIsRotated = cameraInfo.sensorOrientationDeg == 90 || cameraInfo.sensorOrientationDeg == 270
        val bufferW = if (sensorIsRotated && (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)) {
            previewSize.height.toFloat()
        } else {
            previewSize.width.toFloat()
        }
        val bufferH = if (sensorIsRotated && (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)) {
            previewSize.width.toFloat()
        } else {
            previewSize.height.toFloat()
        }

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90f * (rotation - 2), centerX, centerY)
        } else {
            val scaleX = viewWidth.toFloat() / bufferW
            val scaleY = viewHeight.toFloat() / bufferH
            val scale = maxOf(scaleX, scaleY)
            matrix.setScale(
                (bufferW * scale) / viewWidth.toFloat(),
                (bufferH * scale) / viewHeight.toFloat(),
                centerX,
                centerY
            )
            if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180f, centerX, centerY)
            }
        }
        textureView.setTransform(matrix)
    }

    private fun mapCameraError(errorCode: Int): Pair<String, Boolean> = when (errorCode) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE ->
            "Camera is occupied by another application. Close the other app and try again." to false
        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE ->
            "Maximum number of active cameras reached. Close other camera apps and retry." to false
        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED ->
            "Camera access is disabled by device policy." to true
        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE ->
            "Camera device encountered a hardware initialization error." to true
        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE ->
            "Android camera service encountered an error. Restarting the camera may resolve it." to false
        else ->
            "Camera failed to initialize (code $errorCode)." to false
    }

    private fun logDebug(msg: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("WideQRCamera", msg)
        }
    }

    private companion object {
        /** Time to hold an AF lock after a tap before returning to continuous AF. */
        const val AF_RELEASE_DELAY_MS = 2_500L
    }
}
