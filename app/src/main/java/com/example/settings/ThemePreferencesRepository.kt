package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wideQrPrefs: DataStore<Preferences> by preferencesDataStore(name = "wideqr_settings")

class ThemePreferencesRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val fallbackKey = booleanPreferencesKey("allow_main_camera_fallback")
    private val historyKey = booleanPreferencesKey("save_scan_history")
    private val barcodesKey = booleanPreferencesKey("scan_product_barcodes")

    val themeMode: Flow<AppThemeMode> = context.wideQrPrefs.data.map { prefs ->
        when (prefs[themeKey]) {
            AppThemeMode.LIGHT.name -> AppThemeMode.LIGHT
            AppThemeMode.DARK.name -> AppThemeMode.DARK
            else -> AppThemeMode.SYSTEM
        }
    }

    /** Opt-in 1x main-camera fallback when the ultra-wide lens is not exposed. Off by default. */
    val allowMainCameraFallback: Flow<Boolean> =
        context.wideQrPrefs.data.map { prefs -> prefs[fallbackKey] ?: false }

    /** Opt-in local scan history (Room). Off by default to preserve the no-history privacy promise. */
    val saveScanHistory: Flow<Boolean> =
        context.wideQrPrefs.data.map { prefs -> prefs[historyKey] ?: false }

    /** Opt-in product/1D barcode formats. Off by default to keep QR-only latency on 4 GB RAM devices. */
    val scanProductBarcodes: Flow<Boolean> =
        context.wideQrPrefs.data.map { prefs -> prefs[barcodesKey] ?: false }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.wideQrPrefs.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    suspend fun setAllowMainCameraFallback(enabled: Boolean) {
        context.wideQrPrefs.edit { prefs ->
            prefs[fallbackKey] = enabled
        }
    }

    suspend fun setSaveScanHistory(enabled: Boolean) {
        context.wideQrPrefs.edit { prefs ->
            prefs[historyKey] = enabled
        }
    }

    suspend fun setScanProductBarcodes(enabled: Boolean) {
        context.wideQrPrefs.edit { prefs ->
            prefs[barcodesKey] = enabled
        }
    }
}
