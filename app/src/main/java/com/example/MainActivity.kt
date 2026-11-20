package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.CameraDiagnosticsScreen
import com.example.ui.ResultScreen
import com.example.ui.ScannerScreen
import com.example.ui.SettingsScreen
import com.example.ui.WideQRViewModel
import com.example.ui.theme.WideQRTheme
import kotlinx.serialization.Serializable

@Serializable
object ScannerRoute

@Serializable
object ResultRoute

@Serializable
object DiagnosticsRoute

@Serializable
object SettingsRoute

class MainActivity : ComponentActivity() {

    /** Text shared into WideQR, captured before Compose is ready. */
    private var pendingSharedText: String? = null

    /** Installed by the composable tree; consumed by share intents. */
    private var sharedTextHandler: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingSharedText = extractSharedText(intent)

        setContent {
            val viewModel: WideQRViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            WideQRTheme(themeMode = themeMode) {
                val navController = rememberNavController()

                // Wire the share-intent bridge once the nav graph exists so a share
                // can jump straight to the result screen.
                DisposableEffect(navController) {
                    sharedTextHandler = { text ->
                        viewModel.acceptSharedText(text)
                        navController.navigate(ResultRoute) { launchSingleTop = true }
                    }
                    deliverPendingSharedText()
                    onDispose { sharedTextHandler = null }
                }

                NavHost(
                    navController = navController,
                    startDestination = ScannerRoute
                ) {
                    composable<ScannerRoute> {
                        ScannerScreen(
                            viewModel = viewModel,
                            onResultReady = {
                                navController.navigate(ResultRoute) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenDiagnostics = {
                                navController.navigate(DiagnosticsRoute) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenSettings = {
                                navController.navigate(SettingsRoute) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable<ResultRoute> {
                        ResultScreen(
                            viewModel = viewModel,
                            onBackToScanner = {
                                viewModel.resumeScanning()
                                navController.popBackStack()
                            }
                        )
                    }

                    composable<DiagnosticsRoute> {
                        CameraDiagnosticsScreen(
                            viewModel = viewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onOpenDiagnostics = {
                                navController.navigate(DiagnosticsRoute) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val shared = extractSharedText(intent) ?: return
        pendingSharedText = shared
        deliverPendingSharedText()
    }

    /**
     * Delivers shared text once Compose has installed a handler. Text that arrives
     * before composition (cold start from the share sheet) stays pending here and
     * is flushed by the DisposableEffect that installs the handler.
     */
    private fun deliverPendingSharedText() {
        val handler = sharedTextHandler ?: return
        val shared = pendingSharedText ?: return
        pendingSharedText = null
        handler(shared)
    }

    companion object {
        fun extractSharedText(intent: Intent?): String? {
            if (intent?.action != Intent.ACTION_SEND) return null
            if (intent.type?.startsWith("text/") != true) return null
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            return text.trim().takeIf { it.isNotEmpty() }
        }
    }
}
