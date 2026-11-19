package com.example.links

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface LinkOpenOutcome {
    data object Opened : LinkOpenOutcome
    data class Failed(val message: String) : LinkOpenOutcome
}

object SafeLinkHandler {

    fun openWebLink(context: Context, url: String): LinkOpenOutcome {
        val trimmed = url.trim()
        val scheme = LinkInspector.extractScheme(trimmed)
        if (scheme != "http" && scheme != "https") {
            return LinkOpenOutcome.Failed("Blocked opening unsupported or unsafe scheme ($scheme:).")
        }

        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            return LinkOpenOutcome.Failed("Malformed web URL.")
        }

        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val chooser = Intent.createChooser(viewIntent, "Open Link With").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            LinkOpenOutcome.Opened
        } catch (_: ActivityNotFoundException) {
            LinkOpenOutcome.Failed("No web browser app was found to open this link.")
        } catch (_: Exception) {
            LinkOpenOutcome.Failed("Could not open link.")
        }
    }
}
