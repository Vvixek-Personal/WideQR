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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.camera.CameraSessionState
import com.example.models.CameraDiscoveryReport
import com.example.models.CameraHardwareInfo
import com.example.models.VerificationStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

@Composable
fun ScannerScreen(
    viewModel: WideQRViewModel,
    onResultReady: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
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
    var showScanPulse by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !hasRequestedPermissionOnce) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else if (hasCameraPermission) {
            viewModel.refreshCameraDiscovery()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scanDetectedEvents.collect {
            showScanPulse = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(160L)
            showScanPulse = false
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

    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (canStreamUltraWide) Color(0xFF070A0E) else MaterialTheme.colorScheme.background
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Middle layer: Camera2 TextureView OR honest non-camera state
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

                // Subtle QR viewfinder outline
                ScannerReticleOverlay(
                    isScanning = sessionState is CameraSessionState.Streaming && !isGalleryDecoding,
                    scanSuccessPulse = showScanPulse,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Honest permission or hardware limitation state
                ScannerUnavailableBody(
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
                    onScanGallery = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 72.dp, bottom = 96.dp, start = 24.dp, end = 24.dp)
                )
            }

            if (isGalleryDecoding) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Decoding QR from image…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // 2. Top minimal bar: WideQR title + verification status chip + Diagnostics & Settings
            ScannerTopBar(
                report = discoveryReport,
                onOverlayDark = canStreamUltraWide,
                onOpenDiagnostics = onOpenDiagnostics,
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // 3. Bottom minimal lens & action bar
            ScannerBottomBar(
                report = discoveryReport,
                sessionState = sessionState,
                onOverlayDark = canStreamUltraWide,
                onScanGallery = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onToggleFlash = { viewModel.toggleTorch() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun ScannerTopBar(
    report: CameraDiscoveryReport,
    onOverlayDark: Boolean,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (onOverlayDark) {
        Color(0xB30D131A)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    }
    val contentColor = if (onOverlayDark) Color(0xFFF8FAFC) else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.title_scanner),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                VerificationStatusBadge(
                    status = report.status,
                    badgeText = report.statusBadgeText,
                    onDark = onOverlayDark
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("diagnostics_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.cd_open_diagnostics)
                )
            }

            FilledTonalIconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_open_settings)
                )
            }
        }
    }
}

@Composable
fun VerificationStatusBadge(
    status: VerificationStatus,
    badgeText: String,
    onDark: Boolean = false
) {
    val (icon, tint) = when (status) {
        VerificationStatus.VERIFIED_ULTRA_WIDE ->
            Icons.Filled.CheckCircle to if (onDark) Color(0xFF2DD4BF) else MaterialTheme.colorScheme.primary
        VerificationStatus.LIKELY_ULTRA_WIDE ->
            Icons.Filled.CheckCircle to if (onDark) Color(0xFF5EEAD4) else MaterialTheme.colorScheme.primary
        VerificationStatus.WIDE_CAMERA_DETECTED_UNVERIFIED ->
            Icons.Filled.Info to if (onDark) Color(0xFFFBBF24) else MaterialTheme.colorScheme.tertiary
        VerificationStatus.ULTRA_WIDE_NOT_ACCESSIBLE,
        VerificationStatus.NO_ULTRA_WIDE_CAMERA_FOUND ->
            Icons.Filled.WarningAmber to if (onDark) Color(0xFFFBBF24) else MaterialTheme.colorScheme.tertiary
        VerificationStatus.CAMERA_ACCESS_ERROR ->
            Icons.Filled.ErrorOutline to if (onDark) Color(0xFFF87171) else MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScannerBottomBar(
    report: CameraDiscoveryReport,
    sessionState: CameraSessionState,
    onOverlayDark: Boolean,
    onScanGallery: () -> Unit,
    onToggleFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (onOverlayDark) {
        Color(0xCC0D131A)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val primaryText = if (onOverlayDark) Color(0xFFF8FAFC) else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (onOverlayDark) Color(0xFFCBD5E1) else MaterialTheme.colorScheme.onSurfaceVariant

    val selected = report.selectedUltraWide
    val isStreaming = sessionState is CameraSessionState.Streaming
    val isTorchOn = (sessionState as? CameraSessionState.Streaming)?.isTorchEnabled == true
    val showFlashButton = selected?.hasFlashUnit == true && isStreaming

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                Text(
                    text = if (selected != null && report.status.isUsableForScanner) {
                        "Ultra-wide camera"
                    } else {
                        report.statusHeadline
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildHonestLensSubtitle(selected, report),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showFlashButton) {
                    IconButton(
                        onClick = onToggleFlash,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isTorchOn) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                } else {
                                    Color.White.copy(alpha = 0.1f)
                                }
                            )
                            .testTag("flash_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = stringResource(
                                if (isTorchOn) R.string.cd_flash_on else R.string.cd_flash_off
                            ),
                            tint = if (isTorchOn) Color(0xFF2DD4BF) else primaryText
                        )
                    }
                }

                IconButton(
                    onClick = onScanGallery,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (onOverlayDark) Color.White.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .testTag("gallery_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = stringResource(R.string.cd_scan_gallery),
                        tint = primaryText
                    )
                }
            }
        }
    }
}

private fun buildHonestLensSubtitle(
    selected: CameraHardwareInfo?,
    report: CameraDiscoveryReport
): String {
    if (selected == null || !report.status.isUsableForScanner) {
        return report.statusBadgeText
    }
    val parts = mutableListOf(
        report.statusBadgeText,
        "ID: ${selected.displayId}"
    )
    selected.primaryFocalLengthMm?.let { f ->
        parts.add(String.format(java.util.Locale.US, "%.2fmm", f))
    }
    selected.horizontalFovDeg?.let { fov ->
        parts.add(String.format(java.util.Locale.US, "~%.0f° HFOV", fov))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ScannerReticleOverlay(
    isScanning: Boolean,
    scanSuccessPulse: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_line")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )

    val frameColor = if (scanSuccessPulse) Color(0xFF2DD4BF) else Color.White.copy(alpha = 0.9f)
    val guideText = if (scanSuccessPulse) "QR detected" else "Align QR code inside frame"

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                modifier = Modifier
                    .size(240.dp)
                    .testTag("scanner_reticle")
            ) {
                val strokePx = if (scanSuccessPulse) 4.dp.toPx() else 3.dp.toPx()
                val cornerLen = 30.dp.toPx()
                val radius = 16.dp.toPx()
                val w = size.width
                val h = size.height

                // Subtle dim guide box
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset.Zero,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Top-left corner
                drawLine(frameColor, Offset(0f, cornerLen), Offset(0f, radius), strokePx, StrokeCap.Round)
                drawLine(frameColor, Offset(radius, 0f), Offset(cornerLen, 0f), strokePx, StrokeCap.Round)

                // Top-right corner
                drawLine(frameColor, Offset(w - cornerLen, 0f), Offset(w - radius, 0f), strokePx, StrokeCap.Round)
                drawLine(frameColor, Offset(w, radius), Offset(w, cornerLen), strokePx, StrokeCap.Round)

                // Bottom-left corner
                drawLine(frameColor, Offset(0f, h - cornerLen), Offset(0f, h - radius), strokePx, StrokeCap.Round)
                drawLine(frameColor, Offset(radius, h), Offset(cornerLen, h), strokePx, StrokeCap.Round)

                // Bottom-right corner
                drawLine(frameColor, Offset(w - cornerLen, h), Offset(w - radius, h), strokePx, StrokeCap.Round)
                drawLine(frameColor, Offset(w, h - cornerLen), Offset(w, h - radius), strokePx, StrokeCap.Round)

                if (isScanning && !scanSuccessPulse) {
                    val y = h * sweepProgress
                    drawLine(
                        color = Color(0xFF2DD4BF).copy(alpha = 0.7f),
                        start = Offset(18.dp.toPx(), y),
                        end = Offset(w - 18.dp.toPx(), y),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = Color(0x990B0F14),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = guideText,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ScannerUnavailableBody(
    hasCameraPermission: Boolean,
    isPermanentlyDenied: Boolean,
    discoveryReport: CameraDiscoveryReport,
    sessionState: CameraSessionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onScanGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasCameraPermission) {
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (isPermanentlyDenied) {
                        stringResource(R.string.permission_denied_permanently)
                    } else {
                        stringResource(R.string.permission_rationale)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = if (isPermanentlyDenied) onOpenAppSettings else onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("camera_permission_button")
                ) {
                    Text(
                        stringResource(
                            if (isPermanentlyDenied) R.string.action_open_app_settings
                            else R.string.action_allow_camera
                        )
                    )
                }
            } else {
                val errorMessage = (sessionState as? CameraSessionState.Error)?.message
                Text(
                    text = discoveryReport.statusHeadline,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = errorMessage ?: discoveryReport.statusExplanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspect_hardware_button")
                ) {
                    Text(stringResource(R.string.action_inspect_hardware))
                }
            }

            OutlinedButton(
                onClick = onScanGallery,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fallback_gallery_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_scan_gallery))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
