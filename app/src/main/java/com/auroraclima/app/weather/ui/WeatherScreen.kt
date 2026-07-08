package com.auroraclima.app.weather.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.theme.AuroraTheme
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.weather.domain.model.City
import com.auroraclima.app.weather.domain.model.WeatherCondition

/**
 * Pantalla principal de clima (mockups P01 y P02) — RF-01, RF-02, RF-03.
 * Solo pinta estado y emite eventos; sin lógica.
 */
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    onSearchQueryChange: (String) -> Unit,
    onCitySelected: (City) -> Unit,
    onSearchCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Mist, Sky)))
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        if (state.isSearchActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherSearchBar(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSearchCancel) {
                    Text(
                        text = stringResource(R.string.weather_search_cancel),
                        color = Night.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            WeatherHeader(headerDate = state.headerDate)
            Spacer(modifier = Modifier.height(20.dp))
            WeatherSearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth()
            )
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
                WeatherCard(weather = state.weather, modifier = Modifier.weight(1f))
            else ->
                WeatherEmptyContent(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeatherHeader(headerDate: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = Night,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = headerDate,
            color = Night.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun WeatherSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = stringResource(R.string.weather_search_hint),
                color = Steel
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = Steel
            )
        },
        singleLine = true,
        // Sin autocorrección: evita que el IME aprenda o sugiera lo tecleado aquí,
        // y es plausible en una búsqueda de nombres propios de ciudades.
        keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            imeAction = ImeAction.Search
        ),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun CitySearchResults(results: List<City>, onCitySelected: (City) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results, key = { it.id }) { city ->
            CityResultItem(city = city, onClick = { onCitySelected(city) })
        }
    }
}

@Composable
private fun CityResultItem(city: City, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = city.name,
                    color = Night,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOfNotNull(city.region, city.country).joinToString(", "),
                    color = Steel,
                    fontSize = 13.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Steel
            )
        }
    }
}

@Composable
private fun NoResultsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.padding(12.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Steel,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.weather_no_results_title),
            color = Night,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.weather_no_results_hint),
            color = Night.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WeatherCard(weather: WeatherCardUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Image(
            painter = painterResource(weather.condition.iconRes()),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
        Text(
            text = weather.temperature,
            color = Night,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            text = stringResource(weather.condition.labelRes()),
            color = Night,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = weather.cityName,
            color = Night,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.weather_max_min, weather.maxTemperature, weather.minTemperature),
            color = Night.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Text(
            text = stringResource(R.string.weather_updated_at, weather.updatedAtTime),
            color = Night.copy(alpha = 0.55f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WeatherDetailChip(
                label = stringResource(R.string.weather_humidity_label),
                value = weather.humidity,
                modifier = Modifier.weight(1f)
            )
            WeatherDetailChip(
                label = stringResource(R.string.weather_wind_label),
                value = weather.wind,
                modifier = Modifier.weight(1f)
            )
            WeatherDetailChip(
                label = stringResource(R.string.weather_rain_label),
                value = weather.rainProbability,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WeatherDetailChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                color = Steel,
                fontSize = 12.sp
            )
            Text(
                text = value,
                color = Night,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WeatherEmptyContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
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
            color = Night.copy(alpha = 0.7f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WeatherScreenPreview() {
    AuroraTheme {
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
            onSearchCancel = {}
        )
    }
}
