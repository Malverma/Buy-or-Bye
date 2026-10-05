package com.buyorbye.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buyorbye.app.ui.BuyOrByeTheme
import com.buyorbye.app.ui.ConfirmScreen
import com.buyorbye.app.ui.HistoryScreen
import com.buyorbye.app.ui.MainViewModel
import com.buyorbye.app.ui.ScanScreen
import com.buyorbye.app.ui.Screen
import com.buyorbye.app.ui.SettingsScreen
import com.buyorbye.app.ui.VerdictScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuyOrByeTheme { App() }
        }
    }
}

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    BackHandler(enabled = screen != Screen.Scan) { vm.back() }
    // Surface supplies the default content color so plain Text/Icon are readable on dark.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (screen) {
            Screen.Scan -> ScanScreen(vm)
            Screen.Confirm -> ConfirmScreen(vm)
            Screen.Verdict -> VerdictScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
            Screen.History -> HistoryScreen(vm)
        }
    }
}
