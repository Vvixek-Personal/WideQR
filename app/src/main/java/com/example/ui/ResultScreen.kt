package com.example.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.links.LinkInspector
import com.example.links.LinkOpenOutcome
import com.example.links.SafeLinkHandler
import com.example.models.ContactData
import com.example.models.QrContentType
import com.example.models.RedirectInspectionState
import com.example.models.ScanResult
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.upi.UPIIntentHandler
import com.example.upi.UpiLaunchOutcome
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: WideQRViewModel,
    onBackToScanner: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val scanResult by viewModel.latestScanResult.collectAsStateWithLifecycle()
    val redirectState by viewModel.redirectState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resumeScanning()
        }
    }

    fun copyText(label: String, value: String, sensitive: Boolean = false) {
        val copied = SecureClipboard.copy(
            context = context,
            label = label,
            value = value,
            sensitive = sensitive,
            onAutoCleared = if (sensitive) {
                {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.sensitive_clip_cleared)
                        )
                    }
                }
            } else {
                null
            }
        )
        if (copied) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.copied_to_clipboard))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = scanResult?.contentType?.label ?: stringResource(R.string.title_result),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToScanner,
                        modifier = Modifier.testTag("result_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val result = scanResult
        if (result == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("No scan result available.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onBackToScanner) {
                        Text(stringResource(R.string.action_rescan))
                    }
                }
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SourceMetadataBadge(
                    icon = iconForContentType(result.contentType),
                    typeLabel = result.contentType.label,
                    sourceLabel = result.sourceLabel
                )

                when (result) {
                    is ScanResult.UpiResult -> UpiResultSection(
                        result = result,
                        onOpenUpiApp = { UPIIntentHandler.openInUpiApp(context, result.upiData.rawUri) },
                        onCopyUpiId = { id -> copyText("UPI ID", id, sensitive = true) },
                        onCopyPaymentLink = { link -> copyText("UPI Payment Link", link, sensitive = true) },
                        onCopyDetails = { details -> copyText("UPI Details", details, sensitive = true) }
                    )

                    is ScanResult.UrlResult -> UrlResultSection(
                        result = result,
                        redirectState = redirectState,
                        onInspectRedirects = { viewModel.inspectRedirects(result.linkInfo.originalUrl) },
                        onOpenLink = { targetUrl ->
                            when (val outcome = SafeLinkHandler.openWebLink(context, targetUrl)) {
                                LinkOpenOutcome.Opened -> Unit
                                is LinkOpenOutcome.Failed -> {
                                    scope.launch { snackbarHostState.showSnackbar(outcome.message) }
                                }
                            }
                        },
                        onCopyLink = { url -> copyText("URL", url) }
                    )

                    is ScanResult.WifiResult -> WifiResultSection(
                        result = result,
                        onCopyPassword = { pw -> copyText("Wi-Fi Password", pw, sensitive = true) },
                        onCopyCredentials = { creds -> copyText("Wi-Fi Credentials", creds, sensitive = true) }
                    )

                    is ScanResult.ContactResult -> ContactResultSection(
                        result = result,
                        onSaveContact = { contact ->
                            launchSaveContactIntent(context, contact) { err ->
                                scope.launch { snackbarHostState.showSnackbar(err) }
                            }
                        },
                        onCopyDetails = { details -> copyText("Contact", details) }
                    )

                    is ScanResult.PhoneResult -> PhoneResultSection(
                        result = result,
                        onDial = { number ->
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                scope.launch { snackbarHostState.showSnackbar("No dialer app available.") }
                            }
                        },
                        onCopyNumber = { number -> copyText("Phone Number", number) }
                    )

                    is ScanResult.EmailResult -> EmailResultSection(
                        result = result,
                        onOpenEmail = {
                            try {
                                val uri = Uri.Builder()
                                    .scheme("mailto")
                                    .opaquePart(result.emailData.address)
                                    .build()
                                val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                                    result.emailData.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                                    result.emailData.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, "Open Email App").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: ActivityNotFoundException) {
                                scope.launch { snackbarHostState.showSnackbar("No email app installed.") }
                            }
                        },
                        onCopyAddress = { addr -> copyText("Email", addr) }
                    )

                    is ScanResult.SmsResult -> SmsResultSection(
                        result = result,
                        onOpenSms = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_SENDTO,
                                    Uri.parse("smsto:${result.smsData.phoneNumber}")
                                ).apply {
                                    result.smsData.message?.let { putExtra("sms_body", it) }
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                scope.launch { snackbarHostState.showSnackbar("No messaging app available.") }
                            }
                        },
                        onCopyDetails = { details -> copyText("SMS", details) }
                    )

                    is ScanResult.PlainTextResult -> PlainTextResultSection(
                        result = result,
                        onCopyText = { text -> copyText("QR Text", text) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onBackToScanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rescan_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_rescan))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SourceMetadataBadge(
    icon: ImageVector,
    typeLabel: String,
    sourceLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(text = typeLabel, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Decoded locally · $sourceLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultField(
    label: String,
    value: String,
    monospace: Boolean = false,
    emphasize: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = value,
                style = when {
                    emphasize -> MaterialTheme.typography.headlineSmall
                    monospace -> MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                    else -> MaterialTheme.typography.bodyLarge
                },
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun StructuredSectionContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        content()
    }
}

@Composable
private fun UpiResultSection(
    result: ScanResult.UpiResult,
    onOpenUpiApp: () -> UpiLaunchOutcome,
    onCopyUpiId: (String) -> Unit,
    onCopyPaymentLink: (String) -> Unit,
    onCopyDetails: (String) -> Unit
) {
    val upi = result.upiData
    var launchError by remember(upi.rawUri) { mutableStateOf<String?>(null) }

    fun attemptOpenUpiApp() {
        when (val outcome = onOpenUpiApp()) {
            UpiLaunchOutcome.Launched -> launchError = null
            is UpiLaunchOutcome.Failed -> launchError = outcome.message
        }
    }

    StructuredSectionContainer {
        ResultField(
            label = stringResource(R.string.label_pay_to),
            value = upi.payeeName ?: "Payee name not specified"
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ResultField(
            label = stringResource(R.string.label_upi_id),
            value = upi.payeeAddress ?: "(Missing UPI ID)",
            monospace = true
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ResultField(
            label = stringResource(R.string.label_amount),
            value = upi.formattedAmount ?: stringResource(R.string.amount_entered_in_app),
            emphasize = upi.isAmountSpecified
        )
        upi.note?.let { note ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField(label = stringResource(R.string.label_note), value = note)
        }
        upi.merchantCode?.let { mc ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField(label = stringResource(R.string.label_merchant_code), value = mc, monospace = true)
        }
        upi.transactionRef?.let { tr ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField(label = stringResource(R.string.label_reference), value = tr, monospace = true)
        }
    }

    Text(
        text = stringResource(R.string.upi_security_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    launchError?.let { errorMsg ->
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("upi_launch_error_banner")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { attemptOpenUpiApp() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("retry_upi_app_button")
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                    OutlinedButton(
                        onClick = { onCopyPaymentLink(upi.rawUri) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_upi_link_error_button")
                    ) {
                        Text(stringResource(R.string.action_copy_payment_link))
                    }
                }
            }
        }
    }

    Button(
        onClick = { attemptOpenUpiApp() },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("open_upi_app_button")
    ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.action_open_upi_app))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        upi.payeeAddress?.let { vpa ->
            OutlinedButton(
                onClick = { onCopyUpiId(vpa) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("copy_upi_id_button")
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_copy_upi_id))
            }
        }
        OutlinedButton(
            onClick = { onCopyPaymentLink(upi.rawUri) },
            modifier = Modifier
                .weight(1f)
                .testTag("copy_payment_link_button")
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_copy_payment_link))
        }
    }

    OutlinedButton(
        onClick = {
            val details = buildString {
                upi.payeeName?.let { appendLine("Payee: $it") }
                upi.payeeAddress?.let { appendLine("UPI ID: $it") }
                upi.formattedAmount?.let { appendLine("Amount: $it") }
                upi.note?.let { appendLine("Note: $it") }
            }.trim()
            onCopyDetails(details.ifEmpty { upi.rawUri })
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("copy_upi_details_button")
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.action_copy_details))
    }
}

@Composable
private fun UrlResultSection(
    result: ScanResult.UrlResult,
    redirectState: RedirectInspectionState,
    onInspectRedirects: () -> Unit,
    onOpenLink: (String) -> Unit,
    onCopyLink: (String) -> Unit
) {
    val link = result.linkInfo
    var pendingConfirmUrl by remember { mutableStateOf<String?>(null) }

    val completedRedirect = redirectState as? RedirectInspectionState.Completed
    val targetUrlToOpen = completedRedirect?.let {
        if (it.blockedDangerousScheme == null &&
            (LinkInspector.extractScheme(it.finalUrl) in setOf("http", "https"))
        ) {
            it.finalUrl
        } else {
            link.originalUrl
        }
    } ?: link.originalUrl

    val isDangerousOrigin = LinkInspector.isDangerousScheme(link.originalUrl)
    val allSuspiciousReasons = buildList {
        addAll(link.suspiciousReasons)
        completedRedirect?.suspiciousReasons?.forEach { reason ->
            if (reason !in this) add(reason)
        }
    }
    val requiresExtraConfirm = link.isSuspicious ||
        (completedRedirect != null && (!completedRedirect.finalIsHttps || completedRedirect.suspiciousReasons.isNotEmpty()))

    StructuredSectionContainer {
        ResultField(
            label = stringResource(R.string.label_qr_link),
            value = link.originalUrl,
            monospace = true
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ResultField(label = stringResource(R.string.label_domain), value = link.host, monospace = true)
            }
            ResultField(
                label = stringResource(R.string.label_protocol),
                value = if (link.scheme.isNotEmpty()) link.scheme.uppercase() else "UNKNOWN"
            )
        }

        if (completedRedirect != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField(
                label = stringResource(R.string.label_destination),
                value = completedRedirect.finalUrl,
                monospace = true
            )
            Text(
                text = completedRedirect.statusNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (completedRedirect.hops.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "REDIRECT CHAIN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    completedRedirect.hops.forEachIndexed { idx, hop ->
                        Text(
                            text = "${idx + 1}. [${hop.statusCode} ${hop.method}] ${hop.url}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (allSuspiciousReasons.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Review before opening",
                        style = MaterialTheme.typography.titleSmall
                    )
                    allSuspiciousReasons.forEach { reason ->
                        Text(text = "• $reason", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    when (redirectState) {
        RedirectInspectionState.Idle -> {
            if (!isDangerousOrigin) {
                OutlinedButton(
                    onClick = onInspectRedirects,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspect_redirects_button")
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_inspect_link))
                }
                Text(
                    text = stringResource(R.string.redirect_disclosure),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        RedirectInspectionState.Loading -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Checking redirect headers…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        is RedirectInspectionState.Failed -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = redirectState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        is RedirectInspectionState.Completed -> Unit
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!isDangerousOrigin) {
            Button(
                onClick = {
                    if (requiresExtraConfirm) {
                        pendingConfirmUrl = targetUrlToOpen
                    } else {
                        onOpenLink(targetUrlToOpen)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("open_link_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_open_link))
            }
        }

        OutlinedButton(
            onClick = { onCopyLink(targetUrlToOpen) },
            modifier = Modifier
                .weight(1f)
                .testTag("copy_link_button")
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_copy_link))
        }
    }

    pendingConfirmUrl?.let { urlToConfirm ->
        AlertDialog(
            onDismissRequest = { pendingConfirmUrl = null },
            title = { Text(stringResource(R.string.suspicious_link_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Destination: $urlToConfirm",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                    if (allSuspiciousReasons.isNotEmpty()) {
                        Text(
                            text = allSuspiciousReasons.joinToString("\n") { "• $it" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmUrl = null
                        onOpenLink(urlToConfirm)
                    }
                ) {
                    Text(stringResource(R.string.suspicious_link_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmUrl = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun WifiResultSection(
    result: ScanResult.WifiResult,
    onCopyPassword: (String) -> Unit,
    onCopyCredentials: (String) -> Unit
) {
    val wifi = result.wifiData
    var showPassword by remember { mutableStateOf(false) }

    StructuredSectionContainer {
        ResultField(label = stringResource(R.string.label_wifi_ssid), value = wifi.ssid)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ResultField(
            label = stringResource(R.string.label_wifi_security),
            value = if (wifi.isHidden) "${wifi.securityType} (Hidden)" else wifi.securityType
        )
        wifi.password?.let { pw ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ResultField(
                        label = stringResource(R.string.label_wifi_password),
                        value = if (showPassword) pw else "•".repeat(pw.length.coerceIn(6, 16)),
                        monospace = true
                    )
                }
                IconButton(
                    onClick = { showPassword = !showPassword },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        wifi.password?.let { pw ->
            Button(
                onClick = { onCopyPassword(pw) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_copy_password))
            }
        }
        OutlinedButton(
            onClick = {
                val creds = buildString {
                    appendLine("Network: ${wifi.ssid}")
                    appendLine("Security: ${wifi.securityType}")
                    wifi.password?.let { appendLine("Password: $it") }
                }.trim()
                onCopyCredentials(creds)
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_copy_credentials))
        }
    }
}

@Composable
private fun ContactResultSection(
    result: ScanResult.ContactResult,
    onSaveContact: (ContactData) -> Unit,
    onCopyDetails: (String) -> Unit
) {
    val c = result.contactData
    var showConfirmSave by remember { mutableStateOf(false) }

    StructuredSectionContainer {
        c.name?.let { ResultField("NAME", it) }
        c.organization?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField("ORGANIZATION", it)
        }
        c.title?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField("TITLE", it)
        }
        if (c.phones.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField("PHONE", c.phones.joinToString("\n"), monospace = true)
        }
        if (c.emails.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField("EMAIL", c.emails.joinToString("\n"), monospace = true)
        }
        c.address?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField("ADDRESS", it)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = { showConfirmSave = true },
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.action_save_contact))
        }
        OutlinedButton(
            onClick = { onCopyDetails(c.rawPayload) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_copy_details))
        }
    }

    if (showConfirmSave) {
        AlertDialog(
            onDismissRequest = { showConfirmSave = false },
            title = { Text(stringResource(R.string.save_contact_confirm_title)) },
            text = { Text(stringResource(R.string.save_contact_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSave = false
                        onSaveContact(c)
                    }
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSave = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun PhoneResultSection(
    result: ScanResult.PhoneResult,
    onDial: (String) -> Unit,
    onCopyNumber: (String) -> Unit
) {
    StructuredSectionContainer {
        ResultField(
            label = stringResource(R.string.label_phone_number),
            value = result.phoneNumber,
            monospace = true,
            emphasize = true
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = { onDial(result.phoneNumber) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_call))
        }
        OutlinedButton(
            onClick = { onCopyNumber(result.phoneNumber) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_copy_number))
        }
    }
}

@Composable
private fun EmailResultSection(
    result: ScanResult.EmailResult,
    onOpenEmail: () -> Unit,
    onCopyAddress: (String) -> Unit
) {
    val email = result.emailData
    StructuredSectionContainer {
        ResultField(
            label = stringResource(R.string.label_email_address),
            value = email.address,
            monospace = true
        )
        email.subject?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField(label = stringResource(R.string.label_subject), value = it)
        }
        email.body?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField(label = stringResource(R.string.label_message), value = it)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(onClick = onOpenEmail, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_open_email))
        }
        OutlinedButton(onClick = { onCopyAddress(email.address) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_copy))
        }
    }
}

@Composable
private fun SmsResultSection(
    result: ScanResult.SmsResult,
    onOpenSms: () -> Unit,
    onCopyDetails: (String) -> Unit
) {
    val sms = result.smsData
    StructuredSectionContainer {
        ResultField(
            label = stringResource(R.string.label_phone_number),
            value = sms.phoneNumber,
            monospace = true
        )
        sms.message?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ResultField(label = stringResource(R.string.label_message), value = it)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(onClick = onOpenSms, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_open_sms))
        }
        OutlinedButton(
            onClick = { onCopyDetails(result.rawValue) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_copy))
        }
    }
}

@Composable
private fun PlainTextResultSection(
    result: ScanResult.PlainTextResult,
    onCopyText: (String) -> Unit
) {
    StructuredSectionContainer {
        ResultField(label = stringResource(R.string.label_raw_payload), value = result.text)
    }

    Button(
        onClick = { onCopyText(result.text) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("copy_text_button")
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.action_copy))
    }
}

private fun iconForContentType(type: QrContentType): ImageVector = when (type) {
    QrContentType.UPI_PAYMENT -> Icons.Filled.AccountBalanceWallet
    QrContentType.URL -> Icons.Filled.Link
    QrContentType.WIFI -> Icons.Filled.Wifi
    QrContentType.PHONE -> Icons.Filled.Call
    QrContentType.EMAIL -> Icons.Filled.Email
    QrContentType.CONTACT -> Icons.Filled.Person
    QrContentType.SMS -> Icons.Filled.Sms
    QrContentType.PLAIN_TEXT -> Icons.Filled.TextFields
}

private fun launchSaveContactIntent(
    context: Context,
    contact: ContactData,
    onError: (String) -> Unit
) {
    try {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            contact.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
            contact.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
            contact.title?.let { putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
            contact.phones.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            contact.emails.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
            contact.address?.let { putExtra(ContactsContract.Intents.Insert.POSTAL, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("Could not open Contacts app.")
    }
}
