package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Clipboard helper that marks payment / credential values as sensitive
 * (Android 13+ hides clip previews and blocks clipboard-read notifications)
 * and clears them after a short delay so UPI IDs and Wi-Fi passwords are not
 * left sitting in the shared clipboard indefinitely.
 */
object SecureClipboard {

    /** How long a sensitive value stays on the clipboard before auto-clearing. */
    const val SENSITIVE_CLEAR_DELAY_MS = 60_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingClear: Runnable? = null

    /**
     * Copies [value] to the clipboard.
     *
     * @param sensitive true for UPI IDs, payment links, Wi-Fi passwords/credentials.
     * @param onAutoCleared invoked on the main thread when the sensitive value was cleared.
     */
    fun copy(
        context: Context,
        label: String,
        value: String,
        sensitive: Boolean,
        onAutoCleared: (() -> Unit)? = null
    ): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false

        val clip = ClipData.newPlainText(label, value)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(EXTRA_IS_SENSITIVE, true)
            }
        }
        return try {
            clipboard.setPrimaryClip(clip)
            if (sensitive) {
                scheduleClear(clipboard, value, onAutoCleared)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun scheduleClear(
        clipboard: ClipboardManager,
        value: String,
        onAutoCleared: (() -> Unit)?
    ) {
        pendingClear?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingClear = null
            try {
                // Only clear if the clipboard still holds our sensitive value,
                // so we never wipe something the user copied afterwards.
                val current = clipboard.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(null)
                    ?.toString()
                if (current == value) {
                    val empty = ClipData.newPlainText("", "")
                    clipboard.setPrimaryClip(empty)
                }
                onAutoCleared?.invoke()
            } catch (_: Exception) {
                onAutoCleared?.invoke()
            }
        }
        pendingClear = runnable
        mainHandler.postDelayed(runnable, SENSITIVE_CLEAR_DELAY_MS)
    }

    /** True when the platform supports the sensitive-clip flag (Android 13+). */
    val supportsSensitiveFlag: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
}
