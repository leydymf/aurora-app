package com.auroraclima.app.access.ui

import android.content.Context
import androidx.compose.runtime.Composable
import com.auroraclima.app.HOME_TAB
import com.auroraclima.app.InPanelRoute
import com.auroraclima.app.InnerTabsLayout
import com.auroraclima.app.R
import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.access.domain.repository.WalkthroughRepository
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import com.auroraclima.app.access.domain.usecase.ValidateSecretCodeUseCase
import com.auroraclima.app.core.ui.v2.SpotlightTargets

/**
 * El Inicio con su barra de pestañas y la guía encima, dentro del grafo seguro real:
 * la maquetación de la app (`InnerTabsLayout`) en la pestaña Inicio, sin Hilt. La ruta
 * declara el inset de la barra, como el panel, así que la nube queda donde queda en la
 * app. [app] recibe cada toque que llegue a la app de abajo (para probar que no llega).
 */
@Composable
internal fun WalkthroughHarness(
    state: WalkthroughUiState,
    actions: WalkthroughActions = idleActions(),
    motion: WalkthroughMotion = WalkthroughMotion.forMotion(reduceMotion = true),
    targets: SpotlightTargets,
    app: (String) -> Unit = {}
) {
    InPanelRoute {
        WalkthroughLayer(state = state, actions = actions, motion = motion, targets = targets) {
            InnerTabsLayout(selectedTab = HOME_TAB, onSelectTab = { app("pestaña $it") }) {
                PanelScreen(
                    onStartGuidance = { app("empezar") },
                    onOpenHistory = { app("resultados") },
                    onOpenSettings = { app("ajustes") },
                    onCall = { app("llamar") },
                    onSaveNote = { app("anotar") },
                    onLearn = { app("informarme") }
                )
            }
        }
    }
}

/** Acciones que no hacen nada: la prueba fija el paso. */
internal fun idleActions() = WalkthroughActions({}, {}, {}, {}, {}, {}, {}, {})

/** El ViewModel real con una marca y sin código de acceso guardado (la práctica no coincide). */
internal fun walkthroughViewModel(mark: WalkthroughMark?): WalkthroughViewModel =
    WalkthroughViewModel(MemoryMark(mark), ValidateSecretCodeUseCase(NoCodes, PlainHasher))

/** Lo que el ViewModel expone, como acciones para la capa. */
internal fun WalkthroughViewModel.asActions() = WalkthroughActions(
    onStart = ::onStart,
    onNotNow = ::onNotNow,
    onNext = ::onNext,
    onBack = ::onBack,
    onFinishEarly = ::onFinishEarly,
    onFinish = ::onFinish,
    onBackGesture = ::onBackGesture,
    onPracticeInputChange = ::onPracticeInputChange
)

/** Etiqueta de parte y título de un paso, como los muestra la tarjeta. */
internal fun stepTexts(context: Context, step: WalkthroughStep): Pair<String, String> {
    val tag = when (step.part) {
        1 -> context.getString(R.string.access_walk_tag_enter)
        2 -> context.getString(R.string.access_walk_tag_space, step.numberInSpace, WalkthroughStep.spaceCount)
        3 -> context.getString(R.string.access_walk_tag_exit)
        else -> ""
    }
    return tag to context.getString(step.titleRes)
}

private class MemoryMark(private var stored: WalkthroughMark?) : WalkthroughRepository {
    override suspend fun mark(mark: WalkthroughMark): Result<Unit> {
        stored = mark
        return Result.success(Unit)
    }

    override suspend fun consume(): Result<WalkthroughMark?> {
        val was = stored
        stored = null
        return Result.success(was)
    }
}

private object NoCodes : SecretCodeRepository {
    override suspend fun storedCode(slot: CodeSlot): StoredCodeHash? = null

    override suspend fun saveCode(slot: CodeSlot, value: StoredCodeHash) = Unit
}

private object PlainHasher : SecretCodeHasher {
    override fun newSalt(): ByteArray = ByteArray(16)

    override fun hash(salt: ByteArray, code: String): ByteArray = code.toByteArray()
}
