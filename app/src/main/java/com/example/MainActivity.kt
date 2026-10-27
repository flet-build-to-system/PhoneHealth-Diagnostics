package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.DiagnosticViewModel
import com.example.ui.screens.AudioTestScreen
import com.example.ui.screens.BatteryHealthScreen
import com.example.ui.screens.FullScanScreen
import com.example.ui.screens.HapticsTorchScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReportDetailScreen
import com.example.ui.screens.ReportHistoryScreen
import com.example.ui.screens.ScreenTestScreen
import com.example.ui.screens.SensorsTestScreen
import com.example.ui.theme.DiagnosticDarkBg
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DiagnosticViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

                BackHandler(enabled = currentScreen != AppScreen.HOME) {
                    viewModel.navigateBack()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DiagnosticDarkBg
                ) {
                    Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                        when (screen) {
                            AppScreen.HOME -> HomeScreen(
                                viewModel = viewModel,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                            AppScreen.FULL_SCAN -> FullScanScreen(
                                viewModel = viewModel,
                                onFinish = { viewModel.navigateTo(AppScreen.REPORT_DETAIL) },
                                onBack = { viewModel.navigateBack() }
                            )
                            AppScreen.SCREEN_TEST -> ScreenTestScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )
                            AppScreen.BATTERY_TEST -> BatteryHealthScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )
                            AppScreen.AUDIO_TEST -> AudioTestScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )
                            AppScreen.SENSORS_TEST -> SensorsTestScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )
                            AppScreen.HAPTICS_TORCH_TEST -> HapticsTorchScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )
                            AppScreen.REPORT_DETAIL -> ReportDetailScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() },
                                onNavigateToHistory = { viewModel.navigateTo(AppScreen.HISTORY) }
                            )
                            AppScreen.HISTORY -> ReportHistoryScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
