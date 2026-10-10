package com.auroraclima.app.weather.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.security.SecureTextEntry
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.FacadeTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.weather.domain.model.City
import com.auroraclima.app.weather.domain.model.WeatherCondition

/**
 * Pantalla principal de clima (mockups v2-01 y v2-02) — RF-01, RF-02, RF-03.
 * Solo pinta estado y emite eventos; sin lógica.
 */
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    onSearchQueryChange: (String) -> Unit,
    onCitySelected: (City) -> Unit,
    onSearchCancel: () -> Unit,
    onOpenForecast: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
    ) {
        // El cielo degradado se ve solo con el clima; la búsqueda va sobre fondo
        // plano (v2-02). Cambia el MODIFICADOR y no el árbol, para que la barra
        // conserve identidad de composición (ver nota siguiente).
        val skyBackground = if (state.isSearchActive) {
            Modifier.background(V2Palette.screenBackground)
        } else {
            Modifier.background(Brush.verticalGradient(listOf(V2Palette.skyTop, V2Palette.skyBottom)))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(skyBackground)
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            // La barra vive en UNA sola posición de la composición: si cambiara de rama
            // al activarse la búsqueda, Compose recrearía el TextField y el foco (y el
            // teclado) se perderían tras la primera letra tecleada.
            WeatherSearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                isActive = state.isSearchActive,
                onBack = onSearchCancel,
                onClear = { onSearchQueryChange("") },
                modifier = Modifier.fillMaxWidth()
            )
            if (!state.isSearchActive && state.weather != null) {
                CurrentConditionsSummary(weather = state.weather)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        when {
            state.isSearchActive && state.searchResults.isNotEmpty() ->
                CitySearchResults(
                    results = state.searchResults,
                    onCitySelected = onCitySelected,
                    modifier = Modifier.weight(1f)
                )
            state.isSearchActive && state.showNoResults ->
                NoResultsContent(modifier = Modifier.weight(1f))
            state.isSearchActive ->
                Spacer(modifier = Modifier.weight(1f))
            state.weather != null ->
                WeatherDetailsCards(weather = state.weather, modifier = Modifier.weight(1f))
            else ->
                WeatherEmptyContent(modifier = Modifier.weight(1f))
        }
        if (!state.isSearchActive) {
            FacadeTabBar(
                selectedTab = FacadeTab.Today,
                onOpenToday = {},
                onOpenForecast = onOpenForecast
            )
        }
    }
}

/** Bloque protagonista de P01: ciudad, temperatura y condición en blanco sobre el cielo. */
@Composable
private fun CurrentConditionsSummary(weather: WeatherCardUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = weather.cityName,
            style = FacadeTypeVoice.bodyStrong,
            color = V2Palette.onSky
        )
        Text(
            text = weather.temperature,
            style = FacadeTypeVoice.displayTemperature,
            color = V2Palette.onSky
        )
        Text(
            text = stringResource(weather.condition.labelRes()),
            style = FacadeTypeVoice.body,
            color = V2Palette.onSky
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.weather_max_min, weather.maxTemperature, weather.minTemperature),
            style = FacadeTypeVoice.caption,
            color = V2Palette.onSky.copy(alpha = 0.8f)
        )
        // La hora de actualización no está en el mockup, pero RF-03.2 exige
        // mostrarla cuando el dato viene de la caché sin conexión.
        Text(
            text = stringResource(R.string.weather_updated_at, weather.updatedAtTime),
            style = FacadeTypeVoice.caption,
            color = V2Palette.onSky.copy(alpha = 0.65f)
        )
    }
}

/**
 * Barra de búsqueda de ciudades y, a la vez, puerta del código (RF-12). El cromo
 * activo (flecha de regreso y borrar, v2-02) vive en los SLOTS del TextField:
 * el campo nunca cambia de nodo, así que el foco y el teclado sobreviven a la
 * activación de la búsqueda. Sobre el cielo la píldora blanca se recorta sola
 * (9.7:1); sobre el fondo plano de la búsqueda activa daba 1.12:1, así que en ese
 * estado lleva un contorno gris neutro de 1 dp (WCAG §1.4.11, ≥ 3:1). Solo el
 * MODIFICADOR cambia; el nodo es el mismo.
 */
@Composable
private fun WeatherSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isActive: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outline = if (isActive) {
        Modifier.border(width = 1.dp, color = V2Palette.controlOutline, shape = V2Corner.pill)
    } else {
        Modifier
    }
    SecureTextEntry {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = modifier.then(outline),
            textStyle = FacadeTypeVoice.body,
            placeholder = {
                Text(
                    text = stringResource(R.string.weather_search_hint),
                    style = FacadeTypeVoice.body,
                    color = V2Palette.inkSecondary
                )
            },
            leadingIcon = {
                if (isActive) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_weather_back),
                            contentDescription = stringResource(R.string.weather_search_cancel),
                            tint = V2Palette.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_weather_search),
                        contentDescription = null,
                        tint = V2Palette.inkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            trailingIcon = {
                if (isActive) {
                    IconButton(onClick = onClear) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(V2Palette.divider, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_weather_close),
                                contentDescription = stringResource(R.string.weather_search_clear),
                                tint = V2Palette.inkSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_weather_pin),
                        contentDescription = null,
                        tint = V2Palette.inkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            singleLine = true,
            // Sin autocorrección porque es plausible en una búsqueda de nombres
            // propios de ciudades, y punto. NO protege de que el teclado aprenda:
            // se midió el EditorInfo con `dumpsys input_method` y esta bandera solo
            // RETIRA `TYPE_TEXT_FLAG_AUTO_CORRECT`; esta barra enviaba `inputType=0x1`
            // pelado, sin nada que hable de aprendizaje. De que no aprenda se encarga
            // `SecureTextEntry`, que envuelve esta barra (H-7, P-94).
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search
            ),
            shape = V2Corner.pill,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = V2Palette.cardSurface,
                unfocusedContainerColor = V2Palette.cardSurface,
                focusedTextColor = V2Palette.inkPrimary,
                unfocusedTextColor = V2Palette.inkPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = V2Palette.inkPrimary
            )
        )
    }
}

/** Resultados de la búsqueda (v2-02): una sola tarjeta con divisores finos. */
@Composable
private fun CitySearchResults(results: List<City>, onCitySelected: (City) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.weather_search_results),
            style = FacadeTypeVoice.label,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        SoftCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            results.forEachIndexed { index, city ->
                if (index > 0) {
                    HorizontalDivider(
                        color = V2Palette.divider,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                CityResultItem(city = city, onClick = { onCitySelected(city) })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CityResultItem(city: City, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_weather_pin),
            contentDescription = null,
            tint = V2Palette.inkSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = city.name,
                style = FacadeTypeVoice.bodyStrong,
                color = V2Palette.inkPrimary
            )
            Text(
                text = listOfNotNull(city.region, city.country).joinToString(", "),
                style = FacadeTypeVoice.caption,
                color = V2Palette.inkSecondary
            )
        }
    }
}

/**
 * Estado «sin resultados» — EL MISMO composable para una búsqueda fallida y
 * para un código incorrecto (RF-13): el ViewModel converge ambos caminos en
 * `showNoResults`, así que aquí no existe (ni puede existir) una segunda rama.
 */
@Composable
private fun NoResultsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Surface(shape = CircleShape, color = V2Palette.cardSurface) {
            Box(modifier = Modifier.padding(12.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_weather_search),
                    contentDescription = null,
                    tint = V2Palette.inkSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.weather_no_results_title),
            style = FacadeTypeVoice.bodyStrong,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.weather_no_results_hint),
            style = FacadeTypeVoice.caption,
            color = V2Palette.inkSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** Tarjetas de datos de P01: humedad y viento lado a lado, y el aviso de lluvia. */
@Composable
private fun WeatherDetailsCards(weather: WeatherCardUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeatherDataCard(
                iconRes = R.drawable.ic_weather_drop,
                label = stringResource(R.string.weather_humidity_label),
                value = weather.humidity,
                modifier = Modifier.weight(1f)
            )
            WeatherDataCard(
                iconRes = R.drawable.ic_weather_wind,
                label = stringResource(R.string.weather_wind_label),
                value = weather.wind,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        RainNoticeCard(rainProbability = weather.rainProbability)
    }
}

@Composable
private fun WeatherDataCard(@DrawableRes iconRes: Int, label: String, value: String, modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = V2Palette.inkSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = FacadeTypeVoice.caption,
                color = V2Palette.inkSecondary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = FacadeTypeVoice.value,
            color = V2Palette.inkPrimary
        )
    }
}

/**
 * Aviso de lluvia de P01. El mockup detalla la franja horaria («desde las
 * 3 p. m.»), un dato que el UiState no tiene: se degrada a la probabilidad
 * del día, que sí existe (decisión de alcance S1, 2026-08-11).
 */
@Composable
private fun RainNoticeCard(rainProbability: String, modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_weather_rain),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.weather_rain_label),
                    style = FacadeTypeVoice.bodyStrong,
                    color = V2Palette.inkPrimary
                )
                Text(
                    text = stringResource(R.string.weather_rain_notice, rainProbability),
                    style = FacadeTypeVoice.caption,
                    color = V2Palette.inkSecondary
                )
            }
        }
    }
}

@Composable
private fun WeatherEmptyContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_weather_cloud_sun),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.weather_empty_message),
            style = FacadeTypeVoice.body,
            color = V2Palette.inkSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WeatherScreenPreview() {
    AuroraV2Theme(V2Voice.Facade) {
        WeatherScreen(
            state = WeatherUiState(
                headerDate = "lun 6 jul",
                weather = WeatherCardUiState(
                    cityName = "Bucaramanga",
                    temperature = "24°",
                    condition = WeatherCondition.PARTLY_CLOUDY,
                    maxTemperature = "28°",
                    minTemperature = "19°",
                    updatedAtTime = "9:28 a. m.",
                    humidity = "76 %",
                    wind = "9 km/h",
                    rainProbability = "55 %"
                )
            ),
            onSearchQueryChange = {},
            onCitySelected = {},
            onSearchCancel = {},
            onOpenForecast = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WeatherScreenNoResultsPreview() {
    AuroraV2Theme(V2Voice.Facade) {
        WeatherScreen(
            state = WeatherUiState(
                searchQuery = "Bucaramnga",
                isSearchActive = true,
                showNoResults = true
            ),
            onSearchQueryChange = {},
            onCitySelected = {},
            onSearchCancel = {},
            onOpenForecast = {}
        )
    }
}
