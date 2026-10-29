package com.auroraclima.app.access.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraclima.app.R
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.access.domain.repository.WalkthroughRepository
import com.auroraclima.app.access.domain.usecase.ValidateSecretCodeUseCase
import com.auroraclima.app.core.security.SearchGateResult
import com.auroraclima.app.core.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La guía de Aurora (v0.3.4; RF-10, RF-11): una sola experiencia que reemplaza al
 * tutorial y al recorrido por Inicio. Vive en la entrada del panel y muere con ella.
 *
 * Se decide una vez al crearse, consumiendo la marca sellada: con
 * [WalkthroughMark.FROM_START] (se deja al crear el código de acceso) empieza en
 * «Conoce Aurora»; con [WalkthroughMark.TO_FINISH] (la deja la parte 3 antes de la
 * salida real) muestra solo «¡Listo!». Un fallo de lectura equivale a ninguna marca:
 * la guía es una ayuda, nunca un error visible (§3). Desde Ajustes se repite completa
 * ([onReplay]).
 *
 * **Reanudación tras la salida real (parte 3).** La nube no se intercepta: tocarla es
 * la salida rápida de siempre (RF-16), que saca el panel de la pila y cancela este
 * ViewModel en el mismo cuadro. Por eso la marca [WalkthroughMark.TO_FINISH] se arma
 * al LLEGAR al paso de la nube, en una escritura que no se cancela, y se desarma si se
 * deja ese paso por otro camino («Atrás», «Después», «Saltar», el gesto atrás).
 * En la siguiente entrada con el código de acceso, el panel crea un ViewModel nuevo
 * —después de la transición de entrada—, que espera a que la llave de la cuenta vuelva
 * (la nube cierra también la sesión del proveedor y el reingreso silencioso termina
 * después de abrir Inicio), consume la marca y muestra «¡Listo!» una sola vez. Si la sesión se cierra en ese paso por inactividad o segundo plano también
 * se muestra; es inofensivo y evita guardar el paso en cada avance (RNF-04).
 *
 * La parte 1 es la práctica de entrada de siempre: la barra evalúa lo tecleado con la
 * MISMA puerta que la fachada ([ValidateSecretCodeUseCase]), solo da por logrado el
 * ingreso con el código de acceso real y no abre nada. Lo tecleado vive en el estado
 * mientras se escribe, se borra al dejar la parte 1 y muere con el ViewModel.
 *
 * Cualquier cierre de sesión (la nube, inactividad, segundo plano) cancela en el acto la
 * evaluación en curso y borra lo tecleado: corre dentro de la limpieza de
 * [SessionManager], antes del pop del grafo, así que un cálculo que termine tarde no
 * cambia nada (RF-16).
 */
@HiltViewModel
class WalkthroughViewModel @Inject constructor(
    private val repository: WalkthroughRepository,
    private val validateSecretCode: ValidateSecretCodeUseCase,
    sessionManager: SessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(WalkthroughUiState())
    val uiState: StateFlow<WalkthroughUiState> = _uiState.asStateFlow()

    private var practiceCheckJob: Job? = null
    private var advanceJob: Job? = null
    private val markJob: Job

    init {
        addCloseable(sessionManager.registerCleaner { discardPractice() })
        markJob = viewModelScope.launch {
            if (!awaitReadableMark()) return@launch
            // Si mientras se esperaba la llave ya empezó un repaso desde Ajustes, la
            // marca no lo pisa: queda para la siguiente entrada.
            if (_uiState.value.step != null) return@launch
            when (repository.consume().getOrNull()) {
                WalkthroughMark.FROM_START -> _uiState.update { it.copy(step = WalkthroughStep.OVERVIEW) }
                WalkthroughMark.TO_FINISH -> _uiState.update {
                    it.copy(
                        step = WalkthroughStep.DONE,
                        reachedEarly = false
                    )
                }
                null -> Unit
            }
        }
    }

    /**
     * Si hay una marca, espera a poder abrirla: la llave es la de la cuenta y, tras la
     * nube, falta hasta que termina el reingreso silencioso (lo normal, 1–2 s después de
     * Inicio). Sin marca, sale enseguida. Si el plazo vence (sin red), la marca no se
     * toca y queda para la próxima entrada.
     */
    private suspend fun awaitReadableMark(): Boolean {
        if (!repository.hasMark()) return false
        var waited = 0L
        while (!repository.canOpen()) {
            if (waited >= KEY_WAIT_MILLIS) return false
            delay(KEY_POLL_MILLIS)
            waited += KEY_POLL_MILLIS
        }
        return true
    }

    /** «Empezar», en «Conoce Aurora». */
    fun onStart() {
        if (_uiState.value.step == WalkthroughStep.OVERVIEW) moveTo(WalkthroughStep.TYPE_IN)
    }

    /** «Ahora no», en «Conoce Aurora»: a Inicio sin guía. */
    fun onNotNow() = close()

    /** «Siguiente» de una tarjeta. */
    fun onNext() {
        _uiState.value.step?.next?.let(::moveTo)
    }

    /** «Atrás» de una tarjeta. */
    fun onBack() {
        _uiState.value.step?.previous?.let(::moveTo)
    }

    /** «Saltar» (cualquier tarjeta) y «Después» (parte 3): a «¡Listo!» con «Para recordar:». */
    fun onFinishEarly() {
        if (_uiState.value.step?.isCard != true) return
        moveTo(WalkthroughStep.DONE)
        _uiState.update { it.copy(reachedEarly = true) }
    }

    /** «Ir a mi espacio», en «¡Listo!». */
    fun onFinish() = close()

    /**
     * El gesto atrás: equivale a «Atrás»; en «Conoce Aurora», a «Ahora no»; en la
     * primera tarjeta (sin «Atrás»), vuelve a «Conoce Aurora»; en «¡Listo!», a «Ir a
     * mi espacio».
     */
    fun onBackGesture() {
        when (_uiState.value.step) {
            null -> Unit
            WalkthroughStep.OVERVIEW -> onNotNow()
            WalkthroughStep.DONE -> onFinish()
            WalkthroughStep.TYPE_IN -> moveTo(WalkthroughStep.OVERVIEW)
            else -> onBack()
        }
    }

    /** Volver a verla desde Ajustes: completa desde «Conoce Aurora», sin tocar la marca. */
    fun onReplay() {
        markJob.cancel()
        if (_uiState.value.step == WalkthroughStep.CLOUD) disarmFinish()
        cancelPractice()
        _uiState.value = WalkthroughUiState(step = WalkthroughStep.OVERVIEW)
    }

    /**
     * Evaluación en vivo, como la barra real: solo la última tecla importa y se evalúa
     * tras una pausa ([PRACTICE_DEBOUNCE_MILLIS]), así que escribir rápido no lanza un
     * cálculo con el Keystore por tecla; por debajo de la longitud mínima ni se intenta.
     * Al coincidir, la confirmación se ve un momento ([CONFIRM_MILLIS]) y la guía pasa
     * sola a Inicio, si sigue en la parte 1. Logrado, queda fijo: editar el texto no lo
     * quita.
     */
    fun onPracticeInputChange(value: String) {
        val current = _uiState.value
        if (current.step != WalkthroughStep.TYPE_IN) return
        if (current.practiceStatus == PracticeStatus.MATCHED) {
            _uiState.update { it.copy(practiceInput = value) }
            return
        }
        _uiState.update { it.copy(practiceInput = value, practiceStatus = PracticeStatus.TYPING) }
        practiceCheckJob?.cancel()
        if (value.trim().length < ValidateSecretCodeUseCase.MIN_CODE_LENGTH) return
        practiceCheckJob = viewModelScope.launch {
            delay(PRACTICE_DEBOUNCE_MILLIS)
            checkPractice(value)
        }
    }

    /**
     * «Buscar» en el teclado de la práctica: evalúa en el acto lo que hay en la barra,
     * sin esperar la pausa (que se cancela). Con la barra vacía no hace nada; por debajo
     * de la longitud mínima no puede ser el código, así que «no coincide» sin calcular.
     */
    fun onPracticeSubmit() {
        val current = _uiState.value
        if (current.step != WalkthroughStep.TYPE_IN || current.practiceStatus == PracticeStatus.MATCHED) return
        val value = current.practiceInput
        if (value.isBlank()) return
        practiceCheckJob?.cancel()
        if (value.trim().length < ValidateSecretCodeUseCase.MIN_CODE_LENGTH) {
            _uiState.update { it.copy(practiceStatus = PracticeStatus.MISMATCH) }
            return
        }
        practiceCheckJob = viewModelScope.launch { checkPractice(value) }
    }

    /**
     * La MISMA puerta de la fachada; el hash corre en el carril propio del Keystore,
     * nunca en el hilo principal. Al coincidir, la guía sigue sola.
     */
    private suspend fun checkPractice(value: String) {
        val matched = validateSecretCode.inspect(value) == SearchGateResult.OpenAccess
        // OpenActivation no ocurre con un código de acceso creado; tampoco cuenta.
        val status = if (matched) PracticeStatus.MATCHED else PracticeStatus.MISMATCH
        _uiState.update { it.copy(practiceStatus = status) }
        if (matched) advanceAfterConfirmation()
    }

    private fun advanceAfterConfirmation() {
        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(CONFIRM_MILLIS)
            if (_uiState.value.step == WalkthroughStep.TYPE_IN) moveTo(WalkthroughStep.GUIDE)
        }
    }

    private fun moveTo(step: WalkthroughStep) {
        val from = _uiState.value.step
        if (step == WalkthroughStep.CLOUD && from != WalkthroughStep.CLOUD) armFinish()
        if (from == WalkthroughStep.CLOUD && step != WalkthroughStep.CLOUD) disarmFinish()
        // Lo tecleado es el código de acceso real, sin máscara: no sale de la parte 1.
        if (step != WalkthroughStep.TYPE_IN) cancelPractice()
        _uiState.update {
            if (step == WalkthroughStep.TYPE_IN) {
                it.copy(step = step)
            } else {
                // La práctica vuelve a empezar si se regresa a la parte 1: con la barra
                // vacía no debe quedar el aviso de no coincidir ni la confirmación.
                it.copy(step = step, practiceInput = "", practiceStatus = PracticeStatus.TYPING)
            }
        }
    }

    private fun close() {
        if (_uiState.value.step == WalkthroughStep.CLOUD) disarmFinish()
        cancelPractice()
        _uiState.value = WalkthroughUiState()
    }

    /**
     * Deja la marca antes de que la nube pueda tocarse. `UNDISPATCHED` la arranca en
     * este mismo instante, sin esperar turno en ningún despachador (una corrutina
     * cancelada antes de empezar no corre nunca), y `NonCancellable` la deja terminar
     * aunque la salida real cancele el ViewModel enseguida. El repositorio hace las
     * escrituras de a una, así que un desarme inmediato no se le adelanta.
     */
    private fun armFinish() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { repository.mark(WalkthroughMark.TO_FINISH) }
        }
    }

    /** Retira la marca: se dejó el paso de la nube sin salir. */
    private fun disarmFinish() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { repository.consume() }
        }
    }

    private fun cancelPractice() {
        practiceCheckJob?.cancel()
        advanceJob?.cancel()
    }

    /** Limpieza del cierre de sesión: nada en curso y nada de lo tecleado en memoria. */
    private fun discardPractice() {
        cancelPractice()
        _uiState.update { it.copy(practiceInput = "", practiceStatus = PracticeStatus.TYPING) }
    }

    companion object {
        /** Cuánto se ve la confirmación de la parte 1 antes de pasar a Inicio. */
        const val CONFIRM_MILLIS = 1_500L

        /** Pausa tras la última tecla antes de evaluar la práctica, la misma de la fachada. */
        const val PRACTICE_DEBOUNCE_MILLIS = 400L

        /** Cuánto se espera, como mucho, a la llave de la cuenta para leer la marca. */
        const val KEY_WAIT_MILLIS = 10_000L
        private const val KEY_POLL_MILLIS = 250L
    }
}

/**
 * [step] es el paso a la vista (null, sin guía). [reachedEarly]: «¡Listo!» llegó por
 * «Saltar» o «Después», sin la salida real (su texto es «Para recordar:»).
 * [practiceInput] y [practiceStatus] son la barra de la parte 1.
 */
data class WalkthroughUiState(
    val step: WalkthroughStep? = null,
    val reachedEarly: Boolean = false,
    val practiceInput: String = "",
    val practiceStatus: PracticeStatus = PracticeStatus.TYPING
) {
    // Sin lo tecleado: es el código de acceso real, en claro (mismo criterio que
    // JournalExportUiState). Nada lo registra hoy; esto evita que un log futuro lo haga.
    override fun toString(): String =
        "WalkthroughUiState(step=$step, reachedEarly=$reachedEarly, practiceStatus=$practiceStatus)"
}

/** Resultado en vivo de la barra de práctica. */
enum class PracticeStatus {
    /** Aún no hay texto suficiente para evaluar. */
    TYPING,

    /** El texto no es el código de acceso. */
    MISMATCH,

    /** Ingreso de práctica logrado: queda fijo aunque el texto cambie. */
    MATCHED
}

/**
 * Los pasos de la guía en su orden: «Conoce Aurora», la parte 1 (Entrar), los seis de
 * la parte 2 (Tu espacio) en el orden pedido, la parte 3 (Salir) y «¡Listo!».
 *
 * Los nombres de las constantes llegan LEGIBLES al DEX del release (R8 los conserva
 * porque `name` y `valueOf` son reflexión): por eso son sosos (RNF-05, H-8).
 */
enum class WalkthroughStep(val part: Int, @StringRes val titleRes: Int, @StringRes val bodyRes: Int) {
    OVERVIEW(0, R.string.access_walk_intro_title, R.string.access_walk_intro_sub),
    TYPE_IN(1, R.string.access_walk_enter_title, R.string.access_walk_enter_body),
    GUIDE(2, R.string.access_walk_guide_title, R.string.access_walk_guide_body),
    JOURNAL(2, R.string.access_walk_journal_title, R.string.access_walk_journal_body),
    START(2, R.string.access_walk_start_title, R.string.access_walk_start_body),
    SAVED(2, R.string.access_walk_saved_title, R.string.access_walk_saved_body),
    SHORTCUTS(2, R.string.access_walk_shortcuts_title, R.string.access_walk_shortcuts_body),
    SETTINGS(2, R.string.access_walk_settings_title, R.string.access_walk_settings_body),
    CLOUD(3, R.string.access_walk_cloud_title, R.string.access_walk_cloud_body),
    DONE(4, R.string.access_walk_done_title, R.string.access_walk_done_back);

    /** Las tarjetas son las partes 1 a 3; «Conoce Aurora» y «¡Listo!» son pantallas completas. */
    val isCard: Boolean get() = part in CARD_PARTS

    /** La tarjeta anterior para «Atrás»; null donde no hay «Atrás» (la primera tarjeta y las pantallas). */
    val previous: WalkthroughStep?
        get() = if (isCard && this != TYPE_IN) entries[ordinal - 1] else null

    /**
     * La tarjeta siguiente para «Siguiente»; null donde no hay «Siguiente» (en la parte 3
     * la acción es tocar la nube, y las pantallas tienen sus propios botones).
     */
    val next: WalkthroughStep?
        get() = if (isCard && this != CLOUD) entries[ordinal + 1] else null

    /** Posición dentro de la parte 2 («N de 6»), desde 1; 0 fuera de ella. */
    val numberInSpace: Int get() = if (part == SPACE_PART) ordinal - GUIDE.ordinal + 1 else 0

    companion object {
        const val SPACE_PART = 2
        private val CARD_PARTS = 1..3

        /** Cuántos pasos tiene la parte 2. */
        val spaceCount: Int get() = entries.count { it.part == SPACE_PART }
    }
}
