package com.auroraclima.app.access.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.access.data.SealedWalkthroughRepository
import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash
import com.auroraclima.app.access.domain.repository.SecretCodeRepository
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import com.auroraclima.app.access.domain.usecase.ValidateSecretCodeUseCase
import com.auroraclima.app.core.security.KeystorePayloadCipher
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reanudación de la guía tras la salida real, con el almacenamiento y el cifrado
 * reales (AES-GCM del Keystore, archivo `view_meta`): al llegar a la nube se deja la
 * marca sellada; la salida cancela el ViewModel en el mismo instante y la marca queda
 * igual; la siguiente entrada muestra «¡Listo!» y la de después ya no (v0.3.4).
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughResumeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = SealedWalkthroughRepository(context, KeystorePayloadCipher())

    @Before
    fun setUp() = runBlocking { repository.clear() }

    @After
    fun tearDown() = runBlocking { repository.clear() }

    @Test
    fun trasLaSalidaRealListoApareceUnaSolaVez() {
        runBlocking { repository.mark(WalkthroughMark.FROM_START).getOrThrow() }
        val store = ViewModelStore()
        val first = viewModelIn(store)
        awaitStep(first, WalkthroughStep.OVERVIEW)

        onMain {
            first.onStart()
            while (first.uiState.value.step != WalkthroughStep.CLOUD) first.onNext()
            // La nube: el panel sale de la pila y su ViewModel se limpia en el mismo cuadro.
            store.clear()
        }

        val second = viewModelIn(ViewModelStore())
        awaitStep(second, WalkthroughStep.DONE)
        assertFalse(second.uiState.value.reachedEarly)

        val third = viewModelIn(ViewModelStore())
        SystemClock.sleep(SETTLE_MILLIS)
        assertNull("«¡Listo!» sale una sola vez", third.uiState.value.step)
    }

    @Test
    fun loGuardadoNoSeLeeEnClaro() {
        runBlocking { repository.mark(WalkthroughMark.TO_FINISH).getOrThrow() }
        val xml = File(context.applicationInfo.dataDir, "shared_prefs/view_meta.xml").readText()

        listOf("finish", "guide", "walk", "listo", "true").forEach {
            assertFalse("«$it» en claro", xml.contains(it, ignoreCase = true))
        }
        assertEquals(WalkthroughMark.TO_FINISH, runBlocking { repository.consume().getOrThrow() })
    }

    private fun viewModelIn(store: ViewModelStore): WalkthroughViewModel {
        lateinit var viewModel: WalkthroughViewModel
        onMain {
            viewModel = ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        WalkthroughViewModel(repository, ValidateSecretCodeUseCase(NoCodes, PlainHasher)) as T
                }
            )[WalkthroughViewModel::class.java]
        }
        return viewModel
    }

    private fun awaitStep(viewModel: WalkthroughViewModel, expected: WalkthroughStep) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (viewModel.uiState.value.step != expected && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(POLL_MILLIS)
        }
        assertEquals(expected, viewModel.uiState.value.step)
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private object NoCodes : SecretCodeRepository {
        override suspend fun storedCode(slot: CodeSlot): StoredCodeHash? = null

        override suspend fun saveCode(slot: CodeSlot, value: StoredCodeHash) = Unit
    }

    private object PlainHasher : SecretCodeHasher {
        override fun newSalt(): ByteArray = ByteArray(16)

        override fun hash(salt: ByteArray, code: String): ByteArray = code.toByteArray()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
        const val SETTLE_MILLIS = 500L
    }
}
