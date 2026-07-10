package com.auroraclima.app.content.ui

import com.auroraclima.app.content.domain.model.Agency
import com.auroraclima.app.content.domain.model.ContactLine
import com.auroraclima.app.content.domain.model.Route
import com.auroraclima.app.content.domain.model.Topic
import com.auroraclima.app.content.domain.repository.ContentRepository

/** Fuente falsa para los ViewModels del módulo de información (guía §5: fake, no mock). */
internal class FakeContentRepository : ContentRepository {
    var topicsResult: Result<List<Topic>> = Result.success(emptyList())
    var routesResult: Result<List<Route>> = Result.success(emptyList())
    var linesResult: Result<List<ContactLine>> = Result.success(emptyList())
    var topicsCalls = 0
        private set
    var routesCalls = 0
        private set

    override suspend fun getTopics(): Result<List<Topic>> {
        topicsCalls++
        return topicsResult
    }

    override suspend fun getRoutes(): Result<List<Route>> {
        routesCalls++
        return routesResult
    }

    override suspend fun getContactLines(): Result<List<ContactLine>> = linesResult
}

internal fun topic(
    id: String,
    order: Int = 1,
    title: String = "Tema $id",
    summary: String = "Resumen $id",
    signals: List<String> = listOf("señal"),
    examples: List<String> = listOf("ejemplo")
) = Topic(id = id, order = order, title = title, summary = summary, signals = signals, examples = examples)

internal fun agency(
    id: String,
    name: String = "Entidad $id",
    role: String = "Qué hace $id",
    whenToUse: String = "Cuándo acudir $id",
    phones: List<String> = listOf("123"),
    address: String? = null,
    hours: String? = null
) = Agency(id = id, name = name, role = role, whenToUse = whenToUse, phones = phones, address = address, hours = hours)

internal fun route(
    id: String,
    order: Int = 1,
    title: String = "Sector $id",
    summary: String = "Resumen $id",
    agencies: List<Agency> = listOf(agency("a-$id"))
) = Route(id = id, order = order, title = title, summary = summary, agencies = agencies)
