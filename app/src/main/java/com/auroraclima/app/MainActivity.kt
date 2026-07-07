package com.auroraclima.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.auroraclima.app.core.ui.theme.AuroraTheme
import com.auroraclima.app.weather.ui.WeatherScreen
import com.auroraclima.app.weather.ui.WeatherViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val weatherViewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuroraTheme {
                val state by weatherViewModel.uiState.collectAsState()
                WeatherScreen(
                    state = state,
                    onSearchQueryChange = weatherViewModel::onSearchQueryChange
                )
            }
        }
    }
}
