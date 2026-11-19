package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: WideQRViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            WideQRTheme(themeMode = themeMode) {
                val navController = rememberNavController()

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
}
