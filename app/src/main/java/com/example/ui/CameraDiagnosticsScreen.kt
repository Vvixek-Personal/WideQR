package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.camera.CameraSessionState
import com.example.models.CameraHardwareInfo
import com.example.models.LensType
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraDiagnosticsScreen(
    viewModel: WideQRViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val report by viewModel.discoveryReport.collectAsStateWithLifecycle()
    val testingCamera by viewModel.diagnosticTestingCamera.collectAsStateWithLifecycle()
    val diagnosticSession by viewModel.diagnosticSessionState.collectAsStateWithLifecycle()
    val unavailableIds by viewModel.unavailableCameraIds.collectAsStateWithLifecycle()

    val textureViewRef = remember { mutableStateOf<TextureView?>(null) }

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        viewModel.refreshCameraDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDiagnosticPreview()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_diagnostics)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopDiagnosticPreview()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("diagnostics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshCameraDiscovery() },
                        modifier = Modifier.testTag("refresh_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh_hardware)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.diagnostics_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Overall Verification Report
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VerificationStatusBadge(
                            status = report.status,
                            badgeText = report.statusBadgeText
                        )
                        Text(
                            text = report.statusHeadline,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = report.statusExplanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        report.selectedUltraWide?.let { uw ->
                            Text(
                                text = "Selected scanner lens: ID ${uw.displayId} (${uw.accessMethod.description})",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        report.referenceMainCamera?.let { main ->
                            Text(
                                text = "Reference 1x main lens: ID ${main.displayId}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Live Diagnostic Test Preview Panel (Phase 1 hardware test!)
                testingCamera?.let { cam ->
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Live Test: Camera ${cam.displayId}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                OutlinedButton(onClick = { viewModel.stopDiagnosticPreview() }) {
                                    Icon(Icons.Filled.VideocamOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_stop_preview))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        TextureView(ctx).also { tv ->
                                            textureViewRef.value = tv
                                            viewModel.startDiagnosticPreview(cam, tv)
                                        }
                                    },
                                    update = { tv ->
                                        textureViewRef.value = tv
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            when (val st = diagnosticSession) {
                                CameraSessionState.Opening -> Text(
                                    text = "Opening Camera2 session…",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                is CameraSessionState.Streaming -> Text(
                                    text = "Streaming ${st.previewSize.width}x${st.previewSize.height} · ${cam.lensType.displayName}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                is CameraSessionState.Error -> Text(
                                    text = st.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                CameraSessionState.Idle -> Unit
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Discovered Sensors (${report.allCameras.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(report.allCameras, key = { it.displayId }) { camera ->
                    CameraHardwareCard(
                        camera = camera,
                        isCurrentlyUnavailable = unavailableIds.contains(camera.openCameraId),
                        hasCameraPermission = hasCameraPermission,
                        isBeingTested = testingCamera?.displayId == camera.displayId,
                        onTestPreview = {
                            val existingTv = textureViewRef.value
                            if (existingTv != null) {
                                viewModel.startDiagnosticPreview(camera, existingTv)
                            } else {
                                viewModel.selectDiagnosticCamera(camera)
                            }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun CameraHardwareCard(
    camera: CameraHardwareInfo,
    isCurrentlyUnavailable: Boolean,
    hasCameraPermission: Boolean,
    isBeingTested: Boolean,
    onTestPreview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (camera.lensType == LensType.ULTRA_WIDE) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                },
                RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ID ${camera.displayId} · ${camera.lensFacingLabel}",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                Text(
                    text = camera.lensType.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (camera.lensType == LensType.ULTRA_WIDE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = camera.hardwareLevelLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Text(
            text = camera.classificationReason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        DiagnosticRow(
            "Focal lengths",
            if (camera.focalLengthsMm.isEmpty()) "Unavailable"
            else camera.focalLengthsMm.joinToString { String.format(Locale.US, "%.2fmm", it) }
        )
        DiagnosticRow(
            "Sensor size",
            if (camera.sensorPhysicalWidthMm != null && camera.sensorPhysicalHeightMm != null) {
                String.format(
                    Locale.US,
                    "%.2f × %.2f mm",
                    camera.sensorPhysicalWidthMm,
                    camera.sensorPhysicalHeightMm
                )
            } else {
                "Unavailable"
            }
        )
        DiagnosticRow(
            "Calculated FOV",
            if (camera.horizontalFovDeg != null && camera.diagonalFovDeg != null) {
                String.format(
                    Locale.US,
                    "%.1f° H · %.1f° D",
                    camera.horizontalFovDeg,
                    camera.diagonalFovDeg
                )
            } else {
                "Insufficient metadata"
            }
        )
        camera.estimated35mmEquivalentMm?.let { eq ->
            DiagnosticRow(
                "35mm eq. (est.)",
                String.format(Locale.US, "~%.1fmm", eq)
            )
        }
        DiagnosticRow(
            "Max resolution",
            camera.maxYuvSize?.let { (w, h) ->
                val mp = (w * h) / 1_000_000f
                String.format(Locale.US, "%dx%d (~%.1f MP)", w, h, mp)
            } ?: "None"
        )
        DiagnosticRow(
            "Autofocus",
            when {
                camera.supportsAutofocus -> "Supported"
                camera.isFixedFocus -> "Fixed-focus"
                else -> "Unavailable"
            }
        )
        DiagnosticRow("Flash unit", if (camera.hasFlashUnit) "Yes" else "No")
        DiagnosticRow("Access", camera.accessMethod.description)
        if (camera.underlyingPhysicalIds.isNotEmpty()) {
            DiagnosticRow("Physical IDs", camera.underlyingPhysicalIds.joinToString(", "))
        }
        if (isCurrentlyUnavailable) {
            DiagnosticRow("Availability", "Currently busy / unavailable")
        }

        if (camera.canOpenForPreview && hasCameraPermission && !isBeingTested) {
            Button(
                onClick = onTestPreview,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_test_preview))
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}
