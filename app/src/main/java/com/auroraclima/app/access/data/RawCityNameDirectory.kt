package com.auroraclima.app.access.data

import android.content.Context
import com.auroraclima.app.R
import com.auroraclima.app.access.domain.repository.CityNameDirectory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Directorio de ciudades empaquetado en `res/raw` (RF-08 criterio 2): lista
 * curada — municipios de Santander, capitales y ciudades mayores de Colombia,
 * y ciudades del mundo con nombre común en español. Funciona sin red y la
 * consulta nunca sale del dispositivo. Un índice de ciudades es un recurso
 * esperable en una app de clima, así que no delata nada (RNF-05). Cobertura
 * curada, no exhaustiva: la limitación queda documentada en ARQUITECTURA.md.
 */
@Singleton
class RawCityNameDirectory @Inject constructor(
    @ApplicationContext private val context: Context
) : CityNameDirectory {
    private val cities: CityNameSet by lazy {
        context.resources.openRawResource(R.raw.city_index)
            .bufferedReader()
            .useLines { lines -> CityNameSet(lines.toList().asSequence()) }
    }

    override suspend fun isRealCity(name: String): Boolean = withContext(Dispatchers.IO) {
        name in cities
    }
}
