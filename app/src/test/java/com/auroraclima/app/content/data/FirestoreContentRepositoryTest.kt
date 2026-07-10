package com.auroraclima.app.content.data

import com.auroraclima.app.content.data.remote.AgencyDto
import com.auroraclima.app.content.data.remote.ContentRemoteSource
import com.auroraclima.app.content.data.remote.LineDto
import com.auroraclima.app.content.data.remote.RouteDto
import com.auroraclima.app.content.data.remote.TopicDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El repositorio filtra esquemas no soportados (ARQUITECTURA §6), omite
 * documentos incompletos y referencias huérfanas (RF-42) y traduce todo
 * fallo a [Result]: ninguna excepción cruza hacia la UI (§3).
 */
class FirestoreContentRepositoryTest {
    private val remote = FakeContentRemoteSource()
    private val repository = FirestoreContentRepository(remote)

    @Test
    fun `los temas llegan mapeados y ordenados por orden`() = runTest {
        // RF-21: el contenido se presenta completo y en el orden publicado.
        remote.topics = listOf(
            topicDto(id = "b", order = 2),
            topicDto(id = "a", order = 1, signals = listOf("señal"), examples = listOf("ejemplo"))
        )

        val topics = repository.getTopics().getOrThrow()

        assertEquals(listOf("a", "b"), topics.map { it.id })
        assertEquals(listOf("señal"), topics.first().signals)
        assertEquals(listOf("ejemplo"), topics.first().examples)
    }

    @Test
    fun `un documento con esquema futuro se ignora sin romper el resto`() = runTest {
        // ARQUITECTURA §6: el panel podrá publicar esquemas nuevos sin tumbar la app.
        remote.topics = listOf(
            topicDto(id = "actual", order = 1, schemaVersion = 1),
            topicDto(id = "futuro", order = 2, schemaVersion = 2),
            topicDto(id = "sin-version", order = 3, schemaVersion = null)
        )

        assertEquals(listOf("actual"), repository.getTopics().getOrThrow().map { it.id })
    }

    @Test
    fun `un documento incompleto se omite`() = runTest {
        remote.topics = listOf(
            topicDto(id = "completo", order = 1),
            topicDto(id = "sin-titulo", order = 2, title = null)
        )

        assertEquals(listOf("completo"), repository.getTopics().getOrThrow().map { it.id })
    }

    @Test
    fun `las rutas resuelven sus entidades por id y en su orden`() = runTest {
        // RF-22: cada sector muestra sus entidades con contacto.
        remote.agencies = listOf(agencyDto(id = "e1"), agencyDto(id = "e2"))
        remote.routes = listOf(
            routeDto(id = "r2", order = 2, agencyIds = listOf("e2")),
            routeDto(id = "r1", order = 1, agencyIds = listOf("e2", "e1"))
        )

        val routes = repository.getRoutes().getOrThrow()

        assertEquals(listOf("r1", "r2"), routes.map { it.id })
        assertEquals(listOf("e2", "e1"), routes.first().agencies.map { it.id })
    }

    @Test
    fun `una referencia huerfana se omite sin romper la ruta`() = runTest {
        // RF-42: eliminar una entidad no rompe las rutas que la referenciaban.
        remote.agencies = listOf(agencyDto(id = "e1"))
        remote.routes = listOf(routeDto(id = "r1", order = 1, agencyIds = listOf("e1", "borrada")))

        val routes = repository.getRoutes().getOrThrow()

        assertEquals(listOf("e1"), routes.single().agencies.map { it.id })
    }

    @Test
    fun `una entidad con esquema futuro no se resuelve en la ruta`() = runTest {
        remote.agencies = listOf(agencyDto(id = "e1", schemaVersion = 2))
        remote.routes = listOf(routeDto(id = "r1", order = 1, agencyIds = listOf("e1")))

        assertTrue(repository.getRoutes().getOrThrow().single().agencies.isEmpty())
    }

    @Test
    fun `las lineas llegan mapeadas y ordenadas`() = runTest {
        // RF-23: se muestran las líneas vigentes con su número listo para marcar.
        remote.lines = listOf(
            lineDto(id = "l2", order = 2, number = "123"),
            lineDto(id = "l1", order = 1, number = "155")
        )

        val lines = repository.getContactLines().getOrThrow()

        assertEquals(listOf("155", "123"), lines.map { it.number })
    }

    // RF-26 — consulta sin conexión. El contrato de la fuente remota (contract de
    // `ContentRemoteSource` + caché persistente de Firestore) es: sin red, `get()`
    // sirve lo ya visto desde la caché, y lanza si la caché está vacía (primera
    // apertura sin red). El fake reproduce ambos lados del contrato.

    @Test
    fun `sin red con cache el contenido completo sigue disponible`() = runTest {
        // RF-26 criterio 1: tipos, rutas y líneas disponibles sin conexión —
        // la fuente responde desde la caché persistente con lo ya visto.
        remote.topics = listOf(topicDto(id = "t1", order = 1))
        remote.agencies = listOf(agencyDto(id = "e1"))
        remote.routes = listOf(routeDto(id = "r1", order = 1, agencyIds = listOf("e1")))
        remote.lines = listOf(lineDto(id = "l1", order = 1))

        assertEquals(listOf("t1"), repository.getTopics().getOrThrow().map { it.id })
        assertEquals(listOf("r1"), repository.getRoutes().getOrThrow().map { it.id })
        assertEquals(listOf("l1"), repository.getContactLines().getOrThrow().map { it.id })
    }

    @Test
    fun `un fallo de la fuente remota es un Result de fallo`() = runTest {
        // §3: la excepción no cruza; RF-25: la UI decidirá mostrar lo último cargado.
        // Es también la primera apertura sin red de RF-26 (caché vacía): cada
        // consulta falla como Result y la UI muestra el genérico con reintento.
        remote.nextError = IOException("sin red")

        assertTrue(repository.getTopics().isFailure)
        remote.nextError = IOException("sin red")
        assertTrue(repository.getRoutes().isFailure)
        remote.nextError = IOException("sin red")
        assertTrue(repository.getContactLines().isFailure)
    }

    @Test
    fun `la cancelacion si se propaga`() = runTest {
        remote.nextError = CancellationException("cancelada")

        var propagated = false
        try {
            repository.getTopics()
        } catch (expected: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }
}

private class FakeContentRemoteSource : ContentRemoteSource {
    var nextError: Exception? = null
    var topics = emptyList<TopicDto>()
    var routes = emptyList<RouteDto>()
    var agencies = emptyList<AgencyDto>()
    var lines = emptyList<LineDto>()

    override suspend fun fetchTopics(): List<TopicDto> = failOrReturn(topics)

    override suspend fun fetchRoutes(): List<RouteDto> = failOrReturn(routes)

    override suspend fun fetchAgencies(): List<AgencyDto> = failOrReturn(agencies)

    override suspend fun fetchLines(): List<LineDto> = failOrReturn(lines)

    private fun <T> failOrReturn(value: T): T {
        nextError?.let {
            nextError = null
            throw it
        }
        return value
    }
}

private fun topicDto(
    id: String,
    order: Long? = 1,
    schemaVersion: Long? = 1,
    title: String? = "Tema $id",
    summary: String? = "Resumen",
    signals: List<String> = emptyList(),
    examples: List<String> = emptyList()
) = TopicDto(id, schemaVersion, order, title, summary, signals, examples)

private fun routeDto(
    id: String,
    order: Long? = 1,
    schemaVersion: Long? = 1,
    title: String? = "Sector $id",
    summary: String? = "Resumen",
    agencyIds: List<String> = emptyList()
) = RouteDto(id, schemaVersion, order, title, summary, agencyIds)

private fun agencyDto(
    id: String,
    schemaVersion: Long? = 1,
    name: String? = "Entidad $id",
    role: String? = "Qué hace",
    whenToUse: String? = "Cuándo acudir",
    phones: List<String> = emptyList()
) = AgencyDto(id, schemaVersion, name, role, whenToUse, phones, null, null)

private fun lineDto(
    id: String,
    order: Long? = 1,
    schemaVersion: Long? = 1,
    name: String? = "Línea $id",
    number: String? = "155",
    description: String? = "Descripción"
) = LineDto(id, schemaVersion, order, name, number, description, null)
