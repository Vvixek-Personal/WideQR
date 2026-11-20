package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.camera.CameraSessionState
import com.example.models.CameraDiscoveryReport
import com.example.models.VerificationStatus
import com.example.ui.theme.InstrumentAccent
import com.example.ui.theme.InstrumentBackground
import com.example.ui.theme.InstrumentBorder
import com.example.ui.theme.InstrumentError
import com.example.ui.theme.InstrumentScrim
import com.example.ui.theme.InstrumentSurface
import com.example.ui.theme.InstrumentSurfaceTranslucent
import com.example.ui.theme.InstrumentTextPrimary
import com.example.ui.theme.InstrumentTextSecondary
import com.example.ui.theme.InstrumentWarning
import com.example.ui.theme.JetBrainsMonoFontFamily

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
        hasError -> InstrumentError
        isStreaming -> InstrumentAccent
        isOpening -> InstrumentWarning
        else -> InstrumentTextSecondary
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
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.title_scanner),
                style = MaterialTheme.typography.titleMedium.copy(
                    letterSpacing = (-0.2).sp,
                    shadow = Shadow(
                        color = Color(0x99000000),
                        offset = Offset(0f, 1f),
                        blurRadius = 6f
                    )
                ),
                fontWeight = FontWeight.SemiBold,
                color = InstrumentTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Surface(
                color = InstrumentScrim,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, InstrumentBorder)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                    Text(
                        text = indicatorLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = if (isStreaming) InstrumentAccent else InstrumentTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScannerHeaderAction(
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

            ScannerHeaderAction(
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
private fun ScannerHeaderAction(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.size(48.dp),
        color = InstrumentSurfaceTranslucent,
        contentColor = InstrumentTextPrimary,
        shape = CircleShape,
        border = BorderStroke(1.dp, InstrumentBorder)
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
    isInitializing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sweepProgressState: State<Float> = if (isScanning) {
        val infiniteTransition = rememberInfiniteTransition(label = "scan_line")
        infiniteTransition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
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
        val showInstruction = maxHeight >= 156.dp
        val instructionAllowance = if (showInstruction) 44.dp else 0.dp
        val maxSquareHeight = (maxHeight - instructionAllowance).coerceAtLeast(88.dp)
        val reticleSize = minOf(236.dp, maxWidth * 0.66f, maxSquareHeight)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(reticleSize)
                    .semantics { contentDescription = reticleDescription }
                    .testTag("scanner_reticle")
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val cornerRadius = 14.dp.toPx().coerceAtMost(w * 0.16f)
                        val legLength = 26.dp.toPx().coerceAtMost(w * 0.24f)
                        val strokeWidth = 2.75.dp.toPx()
                        val contrastStrokeWidth = 4.5.dp.toPx()
                        val inset = 14.dp.toPx().coerceAtMost(w * 0.12f)

                        val cornerPath = Path().apply {
                            // Top-left
                            moveTo(0f, legLength)
                            lineTo(0f, cornerRadius)
                            quadraticTo(0f, 0f, cornerRadius, 0f)
                            lineTo(legLength, 0f)

                            // Top-right
                            moveTo(w - legLength, 0f)
                            lineTo(w - cornerRadius, 0f)
                            quadraticTo(w, 0f, w, cornerRadius)
                            lineTo(w, legLength)

                            // Bottom-right
                            moveTo(w, h - legLength)
                            lineTo(w, h - cornerRadius)
                            quadraticTo(w, h, w - cornerRadius, h)
                            lineTo(w - legLength, h)

                            // Bottom-left
                            moveTo(legLength, h)
                            lineTo(cornerRadius, h)
                            quadraticTo(0f, h, 0f, h - cornerRadius)
                            lineTo(0f, h - legLength)
                        }

                        val bracketContrastStroke = Stroke(
                            width = contrastStrokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        val bracketStroke = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        val scanBrush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                InstrumentAccent.copy(alpha = 0.88f),
                                InstrumentAccent.copy(alpha = 0.88f),
                                Color.Transparent
                            ),
                            startX = inset,
                            endX = w - inset
                        )
                        val lineWidthPx = 1.5.dp.toPx()

                        onDrawBehind {
                            drawPath(
                                path = cornerPath,
                                color = InstrumentBackground.copy(alpha = 0.42f),
                                style = bracketContrastStroke
                            )
                            drawPath(
                                path = cornerPath,
                                color = InstrumentAccent.copy(alpha = 0.94f),
                                style = bracketStroke
                            )

                            if (isScanning) {
                                val y = h * sweepProgressState.value
                                drawLine(
                                    brush = scanBrush,
                                    start = Offset(inset, y),
                                    end = Offset(w - inset, y),
                                    strokeWidth = lineWidthPx,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isInitializing) {
                    Surface(
                        color = InstrumentScrim,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InstrumentBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.75.dp,
                                color = InstrumentAccent
                            )
                            Text(
                                text = stringResource(R.string.scanner_status_opening),
                                style = MaterialTheme.typography.labelSmall,
                                color = InstrumentTextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (showInstruction) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = InstrumentScrim,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, InstrumentBorder)
                ) {
                    Text(
                        text = stringResource(R.string.scanner_instruction),
                        style = MaterialTheme.typography.labelSmall,
                        color = InstrumentTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScannerHardwarePill(
    report: CameraDiscoveryReport,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 460.dp),
        color = InstrumentSurfaceTranslucent,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, InstrumentBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.scanner_lens_label),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = InstrumentTextSecondary,
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(10.dp)
                    .background(InstrumentBorder)
            )
            VerificationStatusBadge(
                status = report.status,
                badgeText = report.statusBadgeText,
                onDark = true
            )
        }
    }
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
        isGalleryDecoding || sessionState is CameraSessionState.Opening -> InstrumentWarning
        isStreaming -> InstrumentAccent
        else -> InstrumentTextSecondary
    }

    Row(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Gallery button (48dp touch target)
        val galleryCd = stringResource(R.string.cd_scan_gallery)
        Surface(
            modifier = Modifier
                .size(48.dp)
                .testTag("gallery_import_button"),
            color = InstrumentSurfaceTranslucent,
            contentColor = if (isGalleryDecoding) InstrumentTextSecondary else InstrumentTextPrimary,
            shape = CircleShape,
            border = BorderStroke(1.dp, InstrumentBorder)
        ) {
            IconButton(
                onClick = onScanGallery,
                enabled = !isGalleryDecoding,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = galleryCd }
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Center: Minimal readable status indicator
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 12.dp),
            color = InstrumentSurfaceTranslucent,
            shape = RoundedCornerShape(50),
            border = BorderStroke(
                width = 1.dp,
                color = if (isStreaming && !isGalleryDecoding) {
                    InstrumentAccent.copy(alpha = 0.35f)
                } else {
                    InstrumentBorder
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = InstrumentTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: Flashlight button (only shown when hardware supports flash & streaming)
        if (showFlashButton) {
            val flashCd = stringResource(
                if (isTorchOn) R.string.cd_flash_on else R.string.cd_flash_off
            )
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .testTag("flash_toggle_button"),
                color = if (isTorchOn) InstrumentAccent else InstrumentSurfaceTranslucent,
                contentColor = if (isTorchOn) InstrumentBackground else InstrumentTextPrimary,
                shape = CircleShape,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isTorchOn) InstrumentAccent else InstrumentBorder
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
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
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
    modifier: Modifier = Modifier,
    fallbackAllowed: Boolean = false,
    onEnableFallback: () -> Unit = {}
) {
    val isNoUltraWide = hasCameraPermission &&
        discoveryReport.status == VerificationStatus.NO_ULTRA_WIDE_CAMERA_FOUND &&
        sessionState !is CameraSessionState.Error

    val isInaccessible = hasCameraPermission &&
        discoveryReport.status == VerificationStatus.ULTRA_WIDE_NOT_ACCESSIBLE &&
        sessionState !is CameraSessionState.Error

    val isErrorState = hasCameraPermission && !isNoUltraWide && !isInaccessible

    val stateLabel = when {
        !hasCameraPermission && isPermanentlyDenied -> stringResource(R.string.scanner_badge_permission_blocked)
        !hasCameraPermission -> stringResource(R.string.scanner_badge_permission)
        isNoUltraWide -> stringResource(R.string.scanner_badge_no_ultrawide)
        isInaccessible -> stringResource(R.string.scanner_badge_unavailable)
        else -> stringResource(R.string.scanner_badge_error)
    }

    val stateTint = when {
        !hasCameraPermission -> InstrumentAccent
        isNoUltraWide || isInaccessible -> InstrumentWarning
        else -> InstrumentError
    }

    val icon = when {
        !hasCameraPermission && isPermanentlyDenied -> Icons.Filled.Settings
        !hasCameraPermission -> Icons.Filled.CameraAlt
        isNoUltraWide -> Icons.Filled.VideocamOff
        isInaccessible -> Icons.Filled.WarningAmber
        else -> Icons.Filled.ErrorOutline
    }

    val headline = when {
        !hasCameraPermission && isPermanentlyDenied -> stringResource(R.string.scanner_permission_blocked_title)
        !hasCameraPermission -> stringResource(R.string.permission_title)
        else -> discoveryReport.statusHeadline
    }

    val detail = when {
        !hasCameraPermission && isPermanentlyDenied -> stringResource(R.string.permission_denied_permanently)
        !hasCameraPermission -> stringResource(R.string.permission_rationale)
        else -> (sessionState as? CameraSessionState.Error)?.message ?: discoveryReport.statusExplanation
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(16.dp))
                .background(InstrumentSurface)
                .border(1.dp, InstrumentBorder, RoundedCornerShape(16.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(stateTint.copy(alpha = 0.12f))
                        .border(1.dp, stateTint.copy(alpha = 0.30f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = stateTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = stateTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = InstrumentTextPrimary
            )

            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = InstrumentTextSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Single primary action per recovery state
            if (!hasCameraPermission) {
                Button(
                    onClick = if (isPermanentlyDenied) onOpenAppSettings else onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("camera_permission_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InstrumentAccent,
                        contentColor = InstrumentBackground
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InstrumentAccent,
                        contentColor = InstrumentBackground
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_inspect_hardware),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Discoverability for the opt-in 1x fallback. Only offered when the
            // ultra-wide is genuinely unusable — never as a silent substitution.
            if (hasCameraPermission && !fallbackAllowed && (isNoUltraWide || isInaccessible)) {
                TextButton(
                    onClick = onEnableFallback,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("enable_main_camera_fallback_button"),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = InstrumentWarning
                    )
                ) {
                    Text(
                        text = stringResource(R.string.action_enable_main_camera_fallback),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Quiet secondary gallery fallback
            TextButton(
                onClick = onScanGallery,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("fallback_gallery_button"),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = InstrumentTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = InstrumentTextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.scanner_action_gallery_fallback),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
internal fun ScannerGalleryDecodingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(InstrumentBackground.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = InstrumentSurface,
            border = BorderStroke(1.dp, InstrumentBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = InstrumentAccent
                )
                Text(
                    text = stringResource(R.string.scanner_decoding_gallery),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InstrumentTextPrimary
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
            Icons.Filled.CheckCircle to if (onDark) InstrumentAccent else MaterialTheme.colorScheme.primary
        VerificationStatus.LIKELY_ULTRA_WIDE ->
            Icons.Filled.CheckCircle to if (onDark) InstrumentAccent else MaterialTheme.colorScheme.primary
        VerificationStatus.WIDE_CAMERA_DETECTED_UNVERIFIED ->
            Icons.Filled.Info to if (onDark) InstrumentWarning else MaterialTheme.colorScheme.tertiary
        VerificationStatus.ULTRA_WIDE_NOT_ACCESSIBLE,
        VerificationStatus.NO_ULTRA_WIDE_CAMERA_FOUND ->
            Icons.Filled.WarningAmber to if (onDark) InstrumentWarning else MaterialTheme.colorScheme.tertiary
        VerificationStatus.CAMERA_ACCESS_ERROR ->
            Icons.Filled.ErrorOutline to if (onDark) InstrumentError else MaterialTheme.colorScheme.error
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
            modifier = Modifier.size(13.dp)
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
