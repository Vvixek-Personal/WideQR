package com.example.upi

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build

sealed interface UpiLaunchOutcome {
    data object Launched : UpiLaunchOutcome
    data class Failed(val message: String) : UpiLaunchOutcome
}

object UPIIntentHandler {

    const val NO_UPI_APP_MESSAGE = "No compatible UPI payment app found."

    fun buildNormalizedPayUri(rawUpiUri: String): Uri? {
        val trimmed = rawUpiUri.trim()
        if (trimmed.isEmpty()) return null
        val parsed = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            return null
        }

        if (!parsed.scheme.equals("upi", ignoreCase = true) ||
            !parsed.host.equals("pay", ignoreCase = true)
        ) {
            return null
        }

        // Preserve query parameters verbatim; only normalize uppercase scheme/host if needed
        return if (parsed.scheme == "upi" && parsed.host == "pay") {
            parsed
        } else {
            val rest = trimmed.substring("upi://pay".length)
            Uri.parse("upi://pay$rest")
        }
    }

    fun openInUpiApp(context: Context, rawUpiUri: String): UpiLaunchOutcome {
        val uri = buildNormalizedPayUri(rawUpiUri)
            ?: return UpiLaunchOutcome.Failed("Invalid UPI payment URI. Expected upi://pay format.")

        val intent = Intent(Intent.ACTION_VIEW, uri)
        val activity = context.findActivity()
        val launchContext = activity ?: context
        if (activity == null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val matches = queryHandlers(context.packageManager, intent)

        return try {
            when {
                // Multiple visible UPI apps: explicitly show Android's app chooser
                matches.size > 1 -> {
                    val chooser = Intent.createChooser(intent, "Open UPI App").apply {
                        if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    launchContext.startActivity(chooser)
                }
                // Single visible app or empty query (due to Android 11+ visibility quirks):
                // Delegate directly to Android's generic intent resolution so 1 app opens
                // directly, multiple hidden apps show the system resolver, and 0 apps throws
                // ActivityNotFoundException cleanly.
                else -> {
                    launchContext.startActivity(intent)
                }
            }
            UpiLaunchOutcome.Launched
        } catch (_: ActivityNotFoundException) {
            UpiLaunchOutcome.Failed(NO_UPI_APP_MESSAGE)
        } catch (_: SecurityException) {
            UpiLaunchOutcome.Failed("Permission denied while opening the UPI payment app.")
        } catch (_: Exception) {
            UpiLaunchOutcome.Failed(NO_UPI_APP_MESSAGE)
        }
    }

    @Suppress("DEPRECATION")
    private fun queryHandlers(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        } catch (_: Exception) {
            emptyList()
        }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
