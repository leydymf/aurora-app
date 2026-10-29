package com.auroraclima.app.access.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.access.domain.repository.WalkthroughRepository
import com.auroraclima.app.access.domain.usecase.FakeSecretCodeHasher
import com.auroraclima.app.access.domain.usecase.FakeSecretCodeRepository
import com.auroraclima.app.access.domain.usecase.FakeWalkthroughRepository
import com.auroraclima.app.access.domain.usecase.ValidateSecretCodeUseCase
import com.auroraclima.app.access.domain.usecase.seed
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.MonotonicClock
import com.auroraclima.app.core.security.SessionManager
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * La guía de Aurora (v0.3.4): sale una sola vez por sí sola, recorre sus pasos en el
 * orden pedido y cada control y el gesto atrás hacen lo descrito.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalkthroughViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hasher = FakeSecretCodeHasher()
    private val codes = FakeSecretCodeRepository()

    // Sus temporizadores van en un planificador aparte que nunca avanza: aquí la sesión
    // solo se cierra cuando la prueba toca la nube (advanceUntilIdle no la caduca).
    private val sessionManager = SessionManager(
        scope = CoroutineScope(StandardTestDispatcher()),
        monotonicClock = MonotonicClock { 0L },
        boundCleaners = emptySet()
    ).apply { startSession() }

    private fun TestScope.viewModel(
        repository: FakeWalkthroughRepository = FakeWalkthroughRepository(WalkthroughMark.FROM_START)
    ): WalkthroughViewModel = WalkthroughViewModel(
        repository,
        ValidateSecretCodeUseCase(codes, hasher),
        sessionManager
    ).also { advanceUntilIdle() }

    private suspend fun seedUserCode(code: String = "amanecer26") {
        codes.seed(CodeSlot.USER, code, hasher)
    }

    private val step get() = WalkthroughStep.entries

    /** Teclea en la práctica y deja pasar la pausa: la evaluación queda hecha. */
    private fun TestScope.typePractice(viewModel: WalkthroughViewModel, value: String) {
        viewModel.onPracticeInputChange(value)
        advanceTimeBy(WalkthroughViewModel.PRACTICE_DEBOUNCE_MILLIS)
        runCurrent()
    }

    @Test
    fun `el orden de los pasos es el pedido`() {
        assertEquals(
            listOf(
                WalkthroughStep.OVERVIEW,
                WalkthroughStep.TYPE_IN,
                WalkthroughStep.GUIDE,
                WalkthroughStep.JOURNAL,
                WalkthroughStep.START,
                WalkthroughStep.SAVED,
                WalkthroughStep.SHORTCUTS,
                WalkthroughStep.SETTINGS,
                WalkthroughStep.CLOUD,
                WalkthroughStep.DONE
            ),
            step
        )
        // Una sola numeración: las ocho tarjetas de seguido; las pantallas no cuentan.
        assertEquals(8, WalkthroughStep.cardCount)
        assertEquals((1..8).toList(), step.filter { it.isCard }.map { it.number })
        assertEquals(0, WalkthroughStep.OVERVIEW.number)
        assertEquals(0, WalkthroughStep.DONE.number)
    }

    @Test
    fun `la portada anuncia tantos pasos como tarjetas tiene la guia`() {
        // «8 pasos cortos…» va literal en el texto (con un %d lint pide plurales): si
        // cambia el número de tarjetas, esta prueba obliga a cambiar también el texto.
        val strings = sequenceOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml"))
            .first { it.exists() }
            .readText()
        val intro = Regex("""<string name="access_walk_intro_sub">(\d+) pasos""").find(strings)
        assertEquals(WalkthroughStep.cardCount, intro?.groupValues?.get(1)?.toInt())
    }

    @Test
    fun `la marca de primera vez abre Conoce Aurora y se consume una vez`() = runTest {
        val repository = FakeWalkthroughRepository(WalkthroughMark.FROM_START)
        val viewModel = viewModel(repository)

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        assertEquals(1, repository.consumeCalls)
        assertNull(repository.stored)
        assertNull(viewModel(repository).uiState.value.step)
    }

    @Test
    fun `la marca de cierre abre solo Listo con el texto de la salida`() = runTest {
        val viewModel = viewModel(FakeWalkthroughRepository(WalkthroughMark.TO_FINISH))

        assertEquals(WalkthroughStep.DONE, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.reachedEarly)
    }

    @Test
    fun `sin marca o con un fallo de lectura no aparece`() = runTest {
        assertNull(viewModel(FakeWalkthroughRepository(null)).uiState.value.step)
        val failing = FakeWalkthroughRepository(WalkthroughMark.FROM_START).apply { failReads = true }
        assertNull(viewModel(failing).uiState.value.step)
    }

    @Test
    fun `Empezar y Siguiente recorren todo hasta la nube`() = runTest {
        val viewModel = viewModel()
        viewModel.onStart()
        val visited = mutableListOf(viewModel.uiState.value.step)
        repeat(10) {
            viewModel.onNext()
            visited += viewModel.uiState.value.step
        }

        assertEquals(step.subList(1, 9), visited.distinct())
        // En la parte 3 no hay «Siguiente»: la acción es tocar la nube.
        assertEquals(WalkthroughStep.CLOUD, viewModel.uiState.value.step)
        assertNull(WalkthroughStep.CLOUD.next)
    }

    @Test
    fun `Atras vuelve un paso y no existe en la primera tarjeta`() = runTest {
        val viewModel = viewModel()
        viewModel.onStart()
        assertNull(WalkthroughStep.TYPE_IN.previous)
        viewModel.onBack()
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)

        viewModel.onNext()
        viewModel.onNext()
        assertEquals(WalkthroughStep.JOURNAL, viewModel.uiState.value.step)
        viewModel.onBack()
        assertEquals(WalkthroughStep.GUIDE, viewModel.uiState.value.step)
        viewModel.onBack()
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)
    }

    @Test
    fun `Ahora no lleva a Inicio sin guia`() = runTest {
        val viewModel = viewModel()
        viewModel.onNotNow()

        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun `Saltar guia desde cualquier tarjeta lleva a Listo con Para recordar`() = runTest {
        step.filter { it.isCard }.forEach { card ->
            val viewModel = viewModel()
            viewModel.onStart()
            while (viewModel.uiState.value.step != card) viewModel.onNext()

            viewModel.onFinishEarly()

            assertEquals("desde $card", WalkthroughStep.DONE, viewModel.uiState.value.step)
            assertTrue(viewModel.uiState.value.reachedEarly)
        }
    }

    @Test
    fun `Saltar guia no hace nada fuera de las tarjetas`() = runTest {
        val viewModel = viewModel()
        viewModel.onFinishEarly()

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
    }

    @Test
    fun `Ir a mi espacio cierra la guia`() = runTest {
        val viewModel = viewModel(FakeWalkthroughRepository(WalkthroughMark.TO_FINISH))
        viewModel.onFinish()

        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun `el gesto atras equivale a Atras y en Conoce Aurora a Ahora no`() = runTest {
        val viewModel = viewModel()
        viewModel.onStart()
        viewModel.onNext()
        viewModel.onNext()
        viewModel.onBackGesture()
        assertEquals(WalkthroughStep.GUIDE, viewModel.uiState.value.step)
        viewModel.onBackGesture()
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)
        // En la primera tarjeta no hay «Atrás»: el gesto vuelve a Conoce Aurora.
        viewModel.onBackGesture()
        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        viewModel.onBackGesture()
        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun `el gesto atras en Listo lleva a mi espacio`() = runTest {
        val viewModel = viewModel(FakeWalkthroughRepository(WalkthroughMark.TO_FINISH))
        viewModel.onBackGesture()

        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun `volver a verla desde Ajustes empieza en Conoce Aurora sin tocar la marca`() = runTest {
        val repository = FakeWalkthroughRepository(null)
        val viewModel = viewModel(repository)
        viewModel.onReplay()

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.reachedEarly)
        assertEquals(0, repository.markCalls)
    }

    @Test
    fun `la practica solo se logra con el codigo de acceso real`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()

        viewModel.onPracticeInputChange("otracosa1")
        advanceUntilIdle()
        assertEquals(PracticeStatus.MISMATCH, viewModel.uiState.value.practiceStatus)
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)

        typePractice(viewModel, "amanecer26")
        assertEquals(PracticeStatus.MATCHED, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `al coincidir confirma un momento y pasa sola a Inicio`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        typePractice(viewModel, "amanecer26")

        advanceTimeBy(WalkthroughViewModel.CONFIRM_MILLIS - 1)
        runCurrent()
        assertEquals("la confirmación sigue a la vista", WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)
        advanceTimeBy(2)
        runCurrent()

        assertEquals(WalkthroughStep.GUIDE, viewModel.uiState.value.step)
        // Lo tecleado es el código real: no sale de la parte 1.
        assertEquals("", viewModel.uiState.value.practiceInput)
    }

    @Test
    fun `si se sale de la parte 1 durante la confirmacion no avanza sola`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        typePractice(viewModel, "amanecer26")

        viewModel.onBackGesture()
        advanceUntilIdle()

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        assertEquals("", viewModel.uiState.value.practiceInput)
    }

    @Test
    fun `Siguiente en la parte 1 avanza sin escribir el codigo`() = runTest {
        val viewModel = viewModel()
        viewModel.onStart()
        viewModel.onNext()

        assertEquals(WalkthroughStep.GUIDE, viewModel.uiState.value.step)
    }

    @Test
    fun `la barra no escucha fuera de la parte 1`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onPracticeInputChange("amanecer26")
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.practiceInput)
        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `repetir desde Ajustes limpia la practica`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        typePractice(viewModel, "amanecer26")

        viewModel.onReplay()
        advanceUntilIdle()

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
        assertEquals("", viewModel.uiState.value.practiceInput)
    }

    private fun TestScope.toCloud(repository: FakeWalkthroughRepository): WalkthroughViewModel {
        val viewModel = viewModel(repository)
        viewModel.onStart()
        while (viewModel.uiState.value.step != WalkthroughStep.CLOUD) viewModel.onNext()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `al llegar a la nube se arma la marca de cierre`() = runTest {
        val repository = FakeWalkthroughRepository(WalkthroughMark.FROM_START)
        toCloud(repository)

        assertEquals(WalkthroughMark.TO_FINISH, repository.stored)
        assertEquals(listOf(WalkthroughMark.TO_FINISH), repository.history)
    }

    @Test
    fun `dejar la nube sin salir desarma la marca`() = runTest {
        val leave = listOf<(WalkthroughViewModel) -> Unit>(
            { it.onBack() },
            { it.onFinishEarly() },
            { it.onBackGesture() }
        )
        leave.forEachIndexed { index, action ->
            val repository = FakeWalkthroughRepository(WalkthroughMark.FROM_START)
            val viewModel = toCloud(repository)

            action(viewModel)
            advanceUntilIdle()

            assertNull("salida $index", repository.stored)
            // Y la siguiente entrada no muestra «¡Listo!».
            assertNull("salida $index", viewModel(repository).uiState.value.step)
        }
    }

    @Test
    fun `tras la salida real la siguiente entrada muestra Listo una sola vez`() = runTest {
        val repository = FakeWalkthroughRepository(WalkthroughMark.FROM_START)
        toCloud(repository)
        // La nube: el panel sale de la pila y su ViewModel muere sin más.

        val next = viewModel(repository)
        assertEquals(WalkthroughStep.DONE, next.uiState.value.step)
        assertFalse("texto de la salida real", next.uiState.value.reachedEarly)

        assertNull(viewModel(repository).uiState.value.step)
    }

    @Test
    fun `la marca se escribe aunque la salida cancele el ViewModel enseguida`() = runTest {
        val slow = SlowMark()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WalkthroughViewModel(slow, ValidateSecretCodeUseCase(codes, hasher), sessionManager) as T
            }
        )[WalkthroughViewModel::class.java]
        advanceUntilIdle()
        viewModel.onReplay()
        viewModel.onStart()
        while (viewModel.uiState.value.step != WalkthroughStep.CLOUD) viewModel.onNext()

        // La nube cierra la sesión en el mismo cuadro: el ViewModel se limpia antes de
        // que la escritura termine.
        store.clear()
        advanceUntilIdle()

        assertEquals(WalkthroughMark.TO_FINISH, slow.stored)
    }

    /** Una marca que tarda en escribirse, para ver que la escritura no se cancela. */
    private class SlowMark : WalkthroughRepository {
        var stored: WalkthroughMark? = null

        override suspend fun mark(mark: WalkthroughMark): Result<Unit> {
            delay(200)
            stored = mark
            return Result.success(Unit)
        }

        override suspend fun hasMark(): Boolean = stored != null

        override suspend fun canOpen(): Boolean = true

        override suspend fun consume(): Result<WalkthroughMark?> = Result.success(stored.also { stored = null })
    }

    @Test
    fun `tras la nube espera a la llave de la cuenta y despues muestra Listo`() = runTest {
        // La nube cierra también la sesión: la llave vuelve con el reingreso silencioso.
        val repository = FakeWalkthroughRepository(WalkthroughMark.TO_FINISH).apply { ready = false }
        val viewModel = WalkthroughViewModel(repository, ValidateSecretCodeUseCase(codes, hasher), sessionManager)
        advanceTimeBy(1_000)
        runCurrent()
        assertNull("sin llave aún no hay guía", viewModel.uiState.value.step)
        assertEquals("y la marca sigue ahí", WalkthroughMark.TO_FINISH, repository.stored)

        repository.ready = true
        advanceTimeBy(500)
        runCurrent()

        assertEquals(WalkthroughStep.DONE, viewModel.uiState.value.step)
        assertNull(repository.stored)
    }

    @Test
    fun `si la llave no llega en el plazo la marca queda para la siguiente entrada`() = runTest {
        val repository = FakeWalkthroughRepository(WalkthroughMark.TO_FINISH).apply { ready = false }
        val viewModel = viewModel(repository)

        assertNull(viewModel.uiState.value.step)
        assertEquals(WalkthroughMark.TO_FINISH, repository.stored)

        repository.ready = true
        assertEquals(WalkthroughStep.DONE, viewModel(repository).uiState.value.step)
    }

    @Test
    fun `sin marca no espera a la llave`() = runTest {
        val repository = FakeWalkthroughRepository(null).apply { ready = false }
        val viewModel = WalkthroughViewModel(repository, ValidateSecretCodeUseCase(codes, hasher), sessionManager)
        runCurrent()

        assertNull(viewModel.uiState.value.step)
        assertEquals("ni siquiera intenta leerla", 0, repository.consumeCalls)
    }

    @Test
    fun `un repaso pedido mientras se espera la llave no lo pisa Listo`() = runTest {
        val repository = FakeWalkthroughRepository(WalkthroughMark.TO_FINISH).apply { ready = false }
        val viewModel = WalkthroughViewModel(repository, ValidateSecretCodeUseCase(codes, hasher), sessionManager)
        runCurrent()
        viewModel.onReplay()
        repository.ready = true
        advanceUntilIdle()

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        assertEquals("la marca queda para la siguiente entrada", WalkthroughMark.TO_FINISH, repository.stored)
    }

    @Test
    fun `volver a la parte 1 limpia el resultado de la practica`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        viewModel.onPracticeInputChange("otracosa1")
        advanceUntilIdle()
        viewModel.onNext()
        viewModel.onBack()

        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
        assertEquals("", viewModel.uiState.value.practiceInput)
    }

    @Test
    fun `el estado no muestra lo tecleado al imprimirse`() {
        val state = WalkthroughUiState(step = WalkthroughStep.TYPE_IN, practiceInput = "amanecer26")
        assertFalse(state.toString().contains("amanecer26"))
    }

    @Test
    fun `escribir rapido en la practica hace un solo calculo con lo ultimo`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        hasher.hashCallCount = 0

        // Cada tecla llega antes de que pase la pausa: ninguna se evalúa sola.
        "amanecer26".indices.forEach { end ->
            viewModel.onPracticeInputChange("amanecer26".substring(0, end + 1))
            advanceTimeBy(WalkthroughViewModel.PRACTICE_DEBOUNCE_MILLIS / 4)
            runCurrent()
        }
        assertEquals("nada durante el tecleo", 0, hasher.hashCallCount)
        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)

        advanceTimeBy(WalkthroughViewModel.PRACTICE_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals("un solo cálculo, con lo último tecleado", 1, hasher.hashCallCount)
        assertEquals(PracticeStatus.MATCHED, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `una tecla nueva cancela el calculo en curso de la practica`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        val slowHash = CompletableDeferred<Unit>()
        hasher.hold = slowHash
        typePractice(viewModel, "amanecer2")
        assertEquals(1, hasher.inFlight)

        viewModel.onPracticeInputChange("amanecer26")
        runCurrent()
        assertEquals("el cálculo anterior no queda acumulado", 0, hasher.inFlight)
        hasher.hold = null
        slowHash.complete(Unit)
        advanceTimeBy(WalkthroughViewModel.PRACTICE_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(PracticeStatus.MATCHED, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `por debajo de la longitud minima la practica no calcula`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        hasher.hashCallCount = 0

        typePractice(viewModel, "amane")
        advanceUntilIdle()

        assertEquals(0, hasher.hashCallCount)
        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `salir con la nube durante un calculo de la practica lo cancela y no avanza`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        val slowHash = CompletableDeferred<Unit>()
        hasher.hold = slowHash
        typePractice(viewModel, "amanecer26")
        assertEquals("el cálculo está en curso", 1, hasher.inFlight)

        // El cálculo termina justo después del cierre, antes de que corra nada más:
        // solo una cancelación hecha en el acto, dentro de la limpieza, lo descarta.
        sessionManager.closeNow()
        slowHash.complete(Unit)
        advanceUntilIdle()

        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)
        assertEquals("lo tecleado no queda en memoria", "", viewModel.uiState.value.practiceInput)
    }

    @Test
    fun `Buscar valida en el acto sin esperar la pausa`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        hasher.hashCallCount = 0

        viewModel.onPracticeInputChange("amanecer26")
        viewModel.onPracticeSubmit()
        runCurrent()

        assertEquals(PracticeStatus.MATCHED, viewModel.uiState.value.practiceStatus)
        assertEquals("un solo cálculo: la pausa pendiente se canceló", 1, hasher.hashCallCount)
        advanceTimeBy(WalkthroughViewModel.CONFIRM_MILLIS + 1)
        runCurrent()
        assertEquals("la guía sigue sola, como con la pausa", WalkthroughStep.GUIDE, viewModel.uiState.value.step)
        assertEquals(1, hasher.hashCallCount)
    }

    @Test
    fun `Buscar con otro codigo no coincide en el acto`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()

        viewModel.onPracticeInputChange("otracosa1")
        viewModel.onPracticeSubmit()
        runCurrent()

        assertEquals(PracticeStatus.MISMATCH, viewModel.uiState.value.practiceStatus)
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)
    }

    @Test
    fun `Buscar con menos de la longitud minima no coincide sin calcular`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        hasher.hashCallCount = 0

        viewModel.onPracticeInputChange("ama")
        viewModel.onPracticeSubmit()
        advanceUntilIdle()

        assertEquals(PracticeStatus.MISMATCH, viewModel.uiState.value.practiceStatus)
        assertEquals(0, hasher.hashCallCount)
    }

    @Test
    fun `Buscar con la barra vacia no hace nada`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        hasher.hashCallCount = 0

        viewModel.onPracticeSubmit()
        viewModel.onPracticeInputChange("   ")
        viewModel.onPracticeSubmit()
        advanceUntilIdle()

        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
        assertEquals(0, hasher.hashCallCount)
    }

    @Test
    fun `volver a escribir tras no coincidir quita el aviso`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        viewModel.onPracticeInputChange("otracosa1")
        viewModel.onPracticeSubmit()
        runCurrent()
        assertEquals(PracticeStatus.MISMATCH, viewModel.uiState.value.practiceStatus)

        viewModel.onPracticeInputChange("otracosa")

        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `Buscar no escucha fuera de la parte 1 ni tras coincidir`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onPracticeSubmit()
        advanceUntilIdle()
        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)

        viewModel.onStart()
        typePractice(viewModel, "amanecer26")
        hasher.hashCallCount = 0
        viewModel.onPracticeSubmit()
        runCurrent()

        assertEquals(PracticeStatus.MATCHED, viewModel.uiState.value.practiceStatus)
        assertEquals(0, hasher.hashCallCount)
    }

    @Test
    fun `la pausa sin coincidir tambien avisa`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()

        typePractice(viewModel, "otracosa1")

        assertEquals(PracticeStatus.MISMATCH, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `salir con la nube durante el calculo de Buscar lo cancela y no deja nada`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        val slowHash = CompletableDeferred<Unit>()
        hasher.hold = slowHash
        viewModel.onPracticeInputChange("amanecer26")
        viewModel.onPracticeSubmit()
        runCurrent()
        assertEquals("el cálculo de «Buscar» está en curso", 1, hasher.inFlight)

        sessionManager.closeNow()
        slowHash.complete(Unit)
        advanceUntilIdle()

        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)
        assertEquals("lo tecleado no queda en memoria", "", viewModel.uiState.value.practiceInput)
    }
}
