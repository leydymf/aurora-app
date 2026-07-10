package com.auroraclima.app.content.ui

import androidx.lifecycle.SavedStateHandle
import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TopicDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeContentRepository()

    private fun viewModel(topicId: String) = TopicDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(TOPIC_ID_ARG to topicId)),
        repository = repository
    )

    @Test
    fun `el tema pedido se encuentra por su id`() = runTest {
        repository.topicsResult = Result.success(listOf(topic("t01"), topic("t02", order = 2)))

        val viewModel = viewModel("t02")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("t02", state.topic?.id)
        assertNull(state.errorRes)
    }

    @Test
    fun `un id inexistente muestra el mensaje generico`() = runTest {
        repository.topicsResult = Result.success(listOf(topic("t01")))

        val viewModel = viewModel("desconocido")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.topic)
        assertEquals(R.string.content_error_generic, state.errorRes)
    }

    @Test
    fun `un fallo del repositorio muestra el mensaje generico`() = runTest {
        repository.topicsResult = Result.failure(RuntimeException("detalle interno"))

        val viewModel = viewModel("t01")
        advanceUntilIdle()

        assertEquals(R.string.content_error_generic, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `reintentar recarga el tema`() = runTest {
        repository.topicsResult = Result.failure(RuntimeException("sin red"))
        val viewModel = viewModel("t01")
        advanceUntilIdle()

        repository.topicsResult = Result.success(listOf(topic("t01")))
        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals("t01", viewModel.uiState.value.topic?.id)
        assertNull(viewModel.uiState.value.errorRes)
    }
}
