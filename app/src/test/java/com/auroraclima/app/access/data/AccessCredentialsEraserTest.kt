package com.auroraclima.app.access.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El eraser de access (RF-20) borra AMBAS ranuras — la clave de la usuaria no
 * vuelve a abrir nada y el estado queda como instalación limpia. En esta JVM el
 * Keystore no existe (mismo límite que P-13): que las prefs queden vacías igual
 * prueba además el mejor esfuerzo por paso — un Keystore que falla no impide
 * borrar los hashes.
 */
@RunWith(RobolectricTestRunner::class)
class AccessCredentialsEraserTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = LocalSecretCodeRepository(context)
    private val eraser = AccessCredentialsEraser(KeystoreSecretCodeHasher(), repository)

    @Test
    fun `borra los hashes de ambas ranuras aunque el keystore falle`() = runTest {
        repository.saveCode(CodeSlot.ACTIVATION, StoredCodeHash(ByteArray(16) { 1 }, ByteArray(32) { 2 }))
        repository.saveCode(CodeSlot.USER, StoredCodeHash(ByteArray(16) { 3 }, ByteArray(32) { 4 }))

        eraser.erase()

        assertNull(repository.storedCode(CodeSlot.ACTIVATION))
        assertNull(repository.storedCode(CodeSlot.USER))
    }
}
