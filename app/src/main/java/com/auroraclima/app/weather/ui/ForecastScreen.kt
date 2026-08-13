package com.auroraclima.app.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.FacadeTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.weather.domain.model.WeatherCondition

/** Pronóstico extendido de la ciudad seleccionada (RF-04, mockup v2-03). */
@Composable
fun ForecastRoute(onBack: () -> Unit, onOpenPlaces: () -> Unit) {
    val viewModel: ForecastViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    // Voz de FACHADA del tema V2: tipografía neutra, jamás Marcellus (F1, RNF-05).
    AuroraV2Theme(V2Voice.Facade) {
        ForecastScreen(state = state, onBack = onBack, onOpenPlaces = onOpenPlaces)
    }
}

@Composable
fun ForecastScreen(
    state: ForecastUiState,
    onBack: () -> Unit,
    onOpenPlaces: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            ForecastHeader(cityName = state.cityName, onBack = onBack)
            Spacer(modifier = Modifier.height(16.dp))
            if (state.days.isNotEmpty()) {
                val spans = remember(state.days) { temperatureSpans(state.days) }
                SoftCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
                    state.days.forEachIndexed { index, day ->
                        ForecastDayRow(day = day, span = spans[index])
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        // «Hoy» desde el pronóstico es volver a P01; «Lugares» vuelve y abre la
        // búsqueda (D4) — ambas las decide la navegación raíz.
        FacadeTabBar(
            selectedTab = FacadeTab.Forecast,
            onOpenToday = onBack,
            onOpenForecast = {},
            onOpenPlaces = onOpenPlaces
        )
    }
}

@Composable
private fun ForecastHeader(cityName: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = V2Palette.cardSurface) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_weather_back),
                    contentDescription = stringResource(R.string.weather_forecast_back),
                    tint = V2Palette.inkPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = cityName,
                style = FacadeTypeVoice.bodyStrong,
                color = V2Palette.inkPrimary
            )
            Text(
                text = stringResource(R.string.weather_forecast_title),
                style = FacadeTypeVoice.caption,
                color = V2Palette.inkSecondary
            )
        }
    }
}

/** Fila del mockup v2-03: día, icono, mínima, barra de temperatura y máxima. */
@Composable
private fun ForecastDayRow(day: ForecastDayUiState, span: TemperatureSpan, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (day.isToday) stringResource(R.string.weather_forecast_today) else day.dayLabel,
            style = if (day.isToday) FacadeTypeVoice.bodyStrong else FacadeTypeVoice.body,
            color = V2Palette.inkPrimary,
            modifier = Modifier.width(58.dp)
        )
        Image(
            painter = painterResource(day.condition.iconRes()),
            contentDescription = stringResource(day.condition.labelRes()),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = day.minTemperature,
            style = FacadeTypeVoice.body,
            color = V2Palette.inkSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        TemperatureBar(span = span, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = day.maxTemperature,
            style = FacadeTypeVoice.bodyStrong,
            color = V2Palette.inkPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
    }
}

/**
 * Barra de temperatura de la fila (v2-03): el degradado frío→cálido cubre el
 * riel completo — el eje térmico de la semana — y cada día revela solo su tramo,
 * de la mínima a la máxima. Es refuerzo visual: las cifras van siempre a los
 * lados (regla 3 del anexo; contraste documentado en README §3.6).
 */
@Composable
private fun TemperatureBar(span: TemperatureSpan, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(6.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = V2Palette.divider, cornerRadius = radius)
        // Tramo degenerado (mín = máx): al menos un punto del ancho de la barra.
        val minWidth = size.height
        var left = span.startFraction * size.width
        var right = span.endFraction * size.width
        if (right - left < minWidth) {
            left = ((left + right - minWidth) / 2f).coerceIn(0f, size.width - minWidth)
            right = left + minWidth
        }
        clipRect(left = left, right = right) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(V2Palette.temperatureCold, V2Palette.temperatureWarm),
                    startX = 0f,
                    endX = size.width
                ),
                cornerRadius = radius
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ForecastScreenPreview() {
    AuroraV2Theme(V2Voice.Facade) {
        ForecastScreen(
            state = ForecastUiState(
                cityName = "Bucaramanga",
                days = listOf(
                    ForecastDayUiState(true, "lun 6", WeatherCondition.PARTLY_CLOUDY, "55 %", "27°", "19°", 27, 19),
                    ForecastDayUiState(false, "mar 7", WeatherCondition.RAIN, "80 %", "25°", "18°", 25, 18),
                    ForecastDayUiState(false, "mié 8", WeatherCondition.OVERCAST, "70 %", "26°", "19°", 26, 19),
                    ForecastDayUiState(false, "jue 9", WeatherCondition.CLEAR, "40 %", "29°", "20°", 29, 20),
                    ForecastDayUiState(false, "vie 10", WeatherCondition.RAIN, "20 %", "24°", "18°", 24, 18)
                )
            ),
            onBack = {},
            onOpenPlaces = {}
        )
    }
}
