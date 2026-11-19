package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.camera.CameraSessionState
import com.example.ui.theme.InstrumentAccent
import com.example.ui.theme.InstrumentBackground
import com.example.ui.theme.InstrumentBorder
import com.example.ui.theme.InstrumentSurface
import com.example.ui.theme.InstrumentTextPrimary

@Composable
fun ScannerScreen(
    viewModel: WideQRViewModel,
    onResultReady: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current

    val discoveryReport by viewModel.discoveryReport.collectAsStateWithLifecycle()
    val sessionState by viewModel.scannerSessionState.collectAsStateWithLifecycle()
    val isGalleryDecoding by viewModel.isGalleryDecoding.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasRequestedPermissionOnce by remember { mutableStateOf(false) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }
    var isLifecycleResumed by remember { mutableStateOf(true) }

    val textureViewRef = remember { mutableStateOf<TextureView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedPermissionOnce = true
        hasCameraPermission = granted
        if (!granted) {
            val activity = context.findActivity()
            isPermanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA
                )
        } else {
            isPermanentlyDenied = false
            textureViewRef.value?.let { tv ->
                viewModel.startScannerPreview(tv)
            } ?: viewModel.refreshCameraDiscovery()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.decodeGalleryImage(uri)
        }
    }

    val launchGalleryPicker: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !hasRequestedPermissionOnce) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else if (hasCameraPermission) {
            viewModel.refreshCameraDiscovery()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scanDetectedEvents.collect {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onResultReady()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(configuration.orientation) {
        viewModel.refreshPreviewOrientation()
    }

    DisposableEffect(view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevLightStatus = controller?.isAppearanceLightStatusBars
        val prevLightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (prevLightStatus != null) {
                controller.isAppearanceLightStatusBars = prevLightStatus
            }
            if (prevLightNav != null) {
                controller.isAppearanceLightNavigationBars = prevLightNav
            }
        }
    }

    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isLifecycleResumed = true
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    hasCameraPermission = granted
                    if (granted) {
                        isPermanentlyDenied = false
                        textureViewRef.value?.let { tv ->
                            viewModel.startScannerPreview(tv)
                        } ?: viewModel.refreshCameraDiscovery()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isLifecycleResumed = false
                    viewModel.stopScannerPreview()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopScannerPreview()
        }
    }

    val canStreamUltraWide = hasCameraPermission &&
        discoveryReport.status.isUsableForScanner &&
        sessionState !is CameraSessionState.Error

    val isStreaming = canStreamUltraWide && sessionState is CameraSessionState.Streaming
    val isOpening = canStreamUltraWide && sessionState is CameraSessionState.Opening
    val hasErrorState = hasCameraPermission &&
        (!discoveryReport.status.isUsableForScanner || sessionState is CameraSessionState.Error)

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
            ) { data ->
                Surface(
                    color = InstrumentSurface,
                    contentColor = InstrumentTextPrimary,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, InstrumentBorder)
                ) {
                    Snackbar(
                        snackbarData = data,
                        containerColor = InstrumentSurface,
                        contentColor = InstrumentTextPrimary,
                        actionColor = InstrumentAccent
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = InstrumentBackground
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Full-bleed live physical ultra-wide Camera2 TextureView
            if (canStreamUltraWide) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv ->
                            textureViewRef.value = tv
                            viewModel.startScannerPreview(tv)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. Adaptive HUD column using safeDrawing insets and weighted center area
            //    (no fixed top/bottom offsets so reticle and controls adapt to any screen or font scale)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScannerTopHeader(
                    isStreaming = isStreaming,
                    isOpening = isOpening,
                    hasError = hasErrorState,
                    onOpenDiagnostics = onOpenDiagnostics,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (canStreamUltraWide) {
                        ScannerReticleOverlay(
                            isScanning = isStreaming && !isGalleryDecoding && isLifecycleResumed,
                            isInitializing = isOpening,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ScannerRecoveryPanel(
                            hasCameraPermission = hasCameraPermission,
                            isPermanentlyDenied = isPermanentlyDenied,
                            discoveryReport = discoveryReport,
                            sessionState = sessionState,
                            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            onOpenAppSettings = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            },
                            onOpenDiagnostics = onOpenDiagnostics,
                            onScanGallery = launchGalleryPicker,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canStreamUltraWide) {
                        ScannerHardwarePill(report = discoveryReport)
                    }

                    ScannerBottomToolbar(
                        report = discoveryReport,
                        sessionState = sessionState,
                        hasCameraPermission = hasCameraPermission,
                        isGalleryDecoding = isGalleryDecoding,
                        onScanGallery = launchGalleryPicker,
                        onToggleFlash = { viewModel.toggleTorch() }
                    )
                }
            }

            if (isGalleryDecoding) {
                ScannerGalleryDecodingOverlay(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
