package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.camera.CameraSessionState
import com.example.models.CameraDiscoveryReport
import com.example.models.CameraHardwareInfo
import com.example.models.VerificationStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

internal object ScannerPalette {
    val Background = Color(0xFF080A0A)
    val Surface = Color(0xFF141817)
    val SurfaceFloating = Color(0xDE141817)
    val Border = Color(0xFF252C29)
    val BorderLight = Color(0x28F5F7F2)
    val Accent = Color(0xFFD5FF62)
    val AccentContainer = Color(0x26D5FF62)
    val TextPrimary = Color(0xFFF5F7F2)
    val TextSecondary = Color(0xFF929A91)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
}

@Composable
internal fun ScannerTopHeader(
    isStreaming: Boolean,
    isOpening: Boolean,
    hasError: Boolean,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorColor = when {
        hasError -> ScannerPalette.Error
        isStreaming -> ScannerPalette.Accent
        isOpening -> ScannerPalette.Warning
        else -> ScannerPalette.TextSecondary
    }
    val indicatorLabel = when {
        hasError -> "OFFLINE"
        isStreaming -> "LIVE"
        isOpening -> "INIT"
        else -> "IDLE"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            color = ScannerPalette.SurfaceFloating,
            contentColor = ScannerPalette.TextPrimary,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ScannerPalette.Border)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.title_scanner),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ScannerPalette.TextPrimary
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(ScannerPalette.Border)
                )
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                    Text(
                        text = indicatorLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = if (isStreaming) ScannerPalette.Accent else ScannerPalette.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScannerHeaderIconButton(
                onClick = onOpenDiagnostics,
                contentDescription = stringResource(R.string.cd_open_diagnostics),
                modifier = Modifier.testTag("diagnostics_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            ScannerHeaderIconButton(
                onClick = onOpenSettings,
                contentDescription = stringResource(R.string.cd_open_settings),
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ScannerHeaderIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.size(48.dp),
        color = ScannerPalette.SurfaceFloating,
        contentColor = ScannerPalette.TextPrimary,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ScannerPalette.Border)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .semantics { this.contentDescription = contentDescription }
        ) {
            icon()
        }
    }
}

@Composable
internal fun ScannerReticleOverlay(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val sweepProgressState: State<Float> = if (isScanning) {
        val infiniteTransition = rememberInfiniteTransition(label = "scan_line")
        infiniteTransition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2100, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sweep"
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }

    val reticleDescription = stringResource(R.string.cd_viewfinder_frame)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val maxAvailableForSquare = (maxHeight - 52.dp).coerceAtLeast(100.dp)
        val reticleSize = minOf(244.dp, maxWidth * 0.66f, maxAvailableForSquare)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Read sweepProgressState inside Canvas draw block so only the Canvas redraws,
            // preventing full-screen recompositions on every animation tick.
            Canvas(
                modifier = Modifier
                    .size(reticleSize)
                    .semantics { contentDescription = reticleDescription }
                    .testTag("scanner_reticle")
            ) {
                val strokePx = 3.dp.toPx()
                val cornerLen = 28.dp.toPx().coerceAtMost(size.width * 0.22f)
                val radius = 16.dp.toPx()
                val w = size.width
                val h = size.height

                // Hairline framing outline
                drawRoundRect(
                    color = ScannerPalette.BorderLight,
                    topLeft = Offset.Zero,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 1.dp.toPx())
                )

                val bracketColor = ScannerPalette.Accent

                // Top-left corner bracket
                drawLine(bracketColor, Offset(0f, cornerLen), Offset(0f, radius), strokePx, StrokeCap.Round)
                drawArc(
                    color = bracketColor,
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                drawLine(bracketColor, Offset(radius, 0f), Offset(cornerLen, 0f), strokePx, StrokeCap.Round)

                // Top-right corner bracket
                drawLine(bracketColor, Offset(w - cornerLen, 0f), Offset(w - radius, 0f), strokePx, StrokeCap.Round)
                drawArc(
                    color = bracketColor,
                    startAngle = 270f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w - radius * 2f, 0f),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                drawLine(bracketColor, Offset(w, radius), Offset(w, cornerLen), strokePx, StrokeCap.Round)

                // Bottom-left corner bracket
                drawLine(bracketColor, Offset(0f, h - cornerLen), Offset(0f, h - radius), strokePx, StrokeCap.Round)
                drawArc(
                    color = bracketColor,
                    startAngle = 90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(0f, h - radius * 2f),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                drawLine(bracketColor, Offset(radius, h), Offset(cornerLen, h), strokePx, StrokeCap.Round)

                // Bottom-right corner bracket
                drawLine(bracketColor, Offset(w - cornerLen, h), Offset(w - radius, h), strokePx, StrokeCap.Round)
                drawArc(
                    color = bracketColor,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w - radius * 2f, h - radius * 2f),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                drawLine(bracketColor, Offset(w, h - radius), Offset(w, h - cornerLen), strokePx, StrokeCap.Round)

                if (isScanning) {
                    val y = h * sweepProgressState.value
                    val inset = 16.dp.toPx()
                    drawLine(
                        color = ScannerPalette.Accent.copy(alpha = 0.22f),
                        start = Offset(inset, y),
                        end = Offset(w - inset, y),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = ScannerPalette.Accent.copy(alpha = 0.85f),
                        start = Offset(inset, y),
                        end = Offset(w - inset, y),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                color = ScannerPalette.SurfaceFloating,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ScannerPalette.Border)
            ) {
                Text(
                    text = stringResource(R.string.scanner_instruction),
                    style = MaterialTheme.typography.labelMedium,
                    color = ScannerPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
internal fun ScannerHardwarePill(
    report: CameraDiscoveryReport,
    modifier: Modifier = Modifier
) {
    val selected = report.selectedUltraWide
    val metadataLine = buildCompactLensMetadata(selected, report)

    Surface(
        modifier = modifier.widthIn(max = 520.dp),
        color = ScannerPalette.SurfaceFloating,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ScannerPalette.Border)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VerificationStatusBadge(
                status = report.status,
                badgeText = report.statusBadgeText,
                onDark = true
            )
            if (metadataLine.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(12.dp)
                        .background(ScannerPalette.Border)
                )
                Text(
                    text = metadataLine,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = ScannerPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildCompactLensMetadata(
    selected: CameraHardwareInfo?,
    report: CameraDiscoveryReport
): String {
    if (selected == null || !report.status.isUsableForScanner) {
        return ""
    }
    val parts = mutableListOf(
        "Ultra-wide · ID ${selected.displayId}"
    )
    selected.primaryFocalLengthMm?.let { f ->
        parts.add(String.format(Locale.US, "%.2fmm", f))
    }
    selected.horizontalFovDeg?.let { fov ->
        parts.add(String.format(Locale.US, "~%.0f°", fov))
    }
    return parts.joinToString(" · ")
}

@Composable
internal fun ScannerBottomToolbar(
    report: CameraDiscoveryReport,
    sessionState: CameraSessionState,
    hasCameraPermission: Boolean,
    isGalleryDecoding: Boolean,
    onScanGallery: () -> Unit,
    onToggleFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = report.selectedUltraWide
    val isStreaming = sessionState is CameraSessionState.Streaming
    val isTorchOn = (sessionState as? CameraSessionState.Streaming)?.isTorchEnabled == true
    val showFlashButton = selected?.hasFlashUnit == true && isStreaming

    val statusText = when {
        isGalleryDecoding -> stringResource(R.string.scanner_status_decoding)
        !hasCameraPermission || !report.status.isUsableForScanner || sessionState is CameraSessionState.Error ->
            stringResource(R.string.scanner_status_standby)
        sessionState is CameraSessionState.Opening -> stringResource(R.string.scanner_status_opening)
        isStreaming -> stringResource(R.string.scanner_status_scanning)
        else -> stringResource(R.string.scanner_status_standby)
    }

    val statusDotColor = when {
        isGalleryDecoding || sessionState is CameraSessionState.Opening -> ScannerPalette.Warning
        isStreaming -> ScannerPalette.Accent
        else -> ScannerPalette.TextSecondary
    }

    Row(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Gallery action
        val galleryLabel = stringResource(R.string.cd_scan_gallery)
        Surface(
            modifier = Modifier
                .size(52.dp)
                .testTag("gallery_import_button"),
            color = ScannerPalette.SurfaceFloating,
            contentColor = ScannerPalette.TextPrimary,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ScannerPalette.Border)
        ) {
            IconButton(
                onClick = onScanGallery,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = galleryLabel }
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Center: Minimal scanning status pill
        Surface(
            color = ScannerPalette.SurfaceFloating,
            shape = RoundedCornerShape(50),
            border = BorderStroke(
                width = 1.dp,
                color = if (isStreaming && !isGalleryDecoding) {
                    ScannerPalette.Accent.copy(alpha = 0.35f)
                } else {
                    ScannerPalette.Border
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = ScannerPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: Flashlight action (only shown when supported & streaming; spacer keeps pill centered)
        if (showFlashButton) {
            val flashCd = stringResource(
                if (isTorchOn) R.string.cd_flash_on else R.string.cd_flash_off
            )
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .testTag("flash_toggle_button"),
                color = if (isTorchOn) ScannerPalette.AccentContainer else ScannerPalette.SurfaceFloating,
                contentColor = if (isTorchOn) ScannerPalette.Accent else ScannerPalette.TextPrimary,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isTorchOn) ScannerPalette.Accent else ScannerPalette.Border
                )
            ) {
                IconButton(
                    onClick = onToggleFlash,
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = flashCd }
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.size(52.dp))
        }
    }
}

@Composable
internal fun ScannerRecoveryPanel(
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
    val isErrorState = hasCameraPermission && (
        sessionState is CameraSessionState.Error ||
            discoveryReport.status == VerificationStatus.CAMERA_ACCESS_ERROR
        )

    val badgeText = when {
        !hasCameraPermission -> stringResource(R.string.scanner_badge_permission)
        isErrorState -> stringResource(R.string.scanner_badge_error)
        else -> stringResource(R.string.scanner_badge_unavailable)
    }

    val statusTint = when {
        !hasCameraPermission -> ScannerPalette.Accent
        isErrorState -> ScannerPalette.Error
        else -> ScannerPalette.Warning
    }

    val icon = when {
        !hasCameraPermission -> Icons.Filled.CameraAlt
        isErrorState -> Icons.Filled.ErrorOutline
        else -> Icons.Filled.VideocamOff
    }

    val title = if (!hasCameraPermission) {
        stringResource(R.string.permission_title)
    } else {
        discoveryReport.statusHeadline
    }

    val description = if (!hasCameraPermission) {
        if (isPermanentlyDenied) {
            stringResource(R.string.permission_denied_permanently)
        } else {
            stringResource(R.string.permission_rationale)
        }
    } else {
        (sessionState as? CameraSessionState.Error)?.message ?: discoveryReport.statusExplanation
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(22.dp))
                .background(ScannerPalette.Surface)
                .border(1.dp, ScannerPalette.Border, RoundedCornerShape(22.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusTint.copy(alpha = 0.12f))
                        .border(1.dp, statusTint.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Surface(
                    color = statusTint.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusTint.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = statusTint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = ScannerPalette.TextPrimary
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = ScannerPalette.TextSecondary
            )

            if (hasCameraPermission && discoveryReport.allCameras.isNotEmpty()) {
                Surface(
                    color = ScannerPalette.Background,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ScannerPalette.Border)
                ) {
                    Text(
                        text = "Detected Camera2 entries: ${discoveryReport.allCameras.size} · Status: ${discoveryReport.statusBadgeText}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = ScannerPalette.TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (!hasCameraPermission) {
                Button(
                    onClick = if (isPermanentlyDenied) onOpenAppSettings else onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("camera_permission_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScannerPalette.Accent,
                        contentColor = ScannerPalette.Background
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (isPermanentlyDenied) R.string.action_open_app_settings
                            else R.string.action_allow_camera
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("inspect_hardware_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScannerPalette.Accent,
                        contentColor = ScannerPalette.Background
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_inspect_hardware),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedButton(
                onClick = onScanGallery,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("fallback_gallery_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ScannerPalette.Border),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ScannerPalette.TextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
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

@Composable
internal fun ScannerGalleryDecodingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(ScannerPalette.Background.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ScannerPalette.Surface,
            border = BorderStroke(1.dp, ScannerPalette.Border)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = ScannerPalette.Accent
                )
                Text(
                    text = stringResource(R.string.scanner_decoding_gallery),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScannerPalette.TextPrimary
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
            Icons.Filled.CheckCircle to if (onDark) ScannerPalette.Accent else MaterialTheme.colorScheme.primary
        VerificationStatus.LIKELY_ULTRA_WIDE ->
            Icons.Filled.CheckCircle to if (onDark) ScannerPalette.Accent else MaterialTheme.colorScheme.primary
        VerificationStatus.WIDE_CAMERA_DETECTED_UNVERIFIED ->
            Icons.Filled.Info to if (onDark) ScannerPalette.Warning else MaterialTheme.colorScheme.tertiary
        VerificationStatus.ULTRA_WIDE_NOT_ACCESSIBLE,
        VerificationStatus.NO_ULTRA_WIDE_CAMERA_FOUND ->
            Icons.Filled.WarningAmber to if (onDark) ScannerPalette.Warning else MaterialTheme.colorScheme.tertiary
        VerificationStatus.CAMERA_ACCESS_ERROR ->
            Icons.Filled.ErrorOutline to if (onDark) ScannerPalette.Error else MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
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
