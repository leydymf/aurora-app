package com.auroraclima.app.weather.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.theme.AuroraTheme
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.weather.domain.model.WeatherCondition

/** Pronóstico extendido de la ciudad seleccionada (RF-04, mockup P03). */
@Composable
fun ForecastRoute(onBack: () -> Unit) {
    val viewModel: ForecastViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    ForecastScreen(state = state, onBack = onBack)
}

@Composable
fun ForecastScreen(state: ForecastUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Mist, Sky)))
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        ForecastHeader(cityName = state.cityName, onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        if (state.days.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    state.days.forEachIndexed { index, day ->
                        if (index > 0) {
                            HorizontalDivider(color = Mist)
                        }
                        ForecastDayRow(day = day)
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastHeader(cityName: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.weather_forecast_back),
                tint = Night
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = stringResource(R.string.weather_forecast_title),
                color = Night,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = cityName,
                color = Steel,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ForecastDayRow(day: ForecastDayUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (day.isToday) stringResource(R.string.weather_forecast_today) else day.dayLabel,
            color = Night,
            fontSize = 15.sp,
            fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Image(
            painter = painterResource(day.condition.iconRes()),
            contentDescription = stringResource(day.condition.labelRes()),
            modifier = Modifier.size(26.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_weather_drop),
                contentDescription = stringResource(R.string.weather_rain_label),
                tint = Steel,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = day.rainProbability,
                color = Steel,
                fontSize = 13.sp
            )
        }
        Text(
            text = day.maxTemperature,
            color = Night,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = " / ${day.minTemperature}",
            color = Steel,
            fontSize = 15.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForecastScreenPreview() {
    AuroraTheme {
        ForecastScreen(
            state = ForecastUiState(
                cityName = "Bucaramanga",
                days = listOf(
                    ForecastDayUiState(true, "lun 6", WeatherCondition.PARTLY_CLOUDY, "55 %", "28°", "19°"),
                    ForecastDayUiState(false, "mar 7", WeatherCondition.RAIN, "80 %", "26°", "18°"),
                    ForecastDayUiState(false, "mié 8", WeatherCondition.RAIN, "70 %", "25°", "18°"),
                    ForecastDayUiState(false, "jue 9", WeatherCondition.PARTLY_CLOUDY, "40 %", "27°", "19°"),
                    ForecastDayUiState(false, "vie 10", WeatherCondition.CLEAR, "20 %", "29°", "19°")
                )
            ),
            onBack = {}
        )
    }
}
