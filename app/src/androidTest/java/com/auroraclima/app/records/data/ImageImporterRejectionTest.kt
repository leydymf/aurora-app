package com.auroraclima.app.records.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Complemento instrumentado de ImageImporterTest (RF-33): el rechazo de un
 * origen que NO es una imagen solo es observable en Android real — el
 * BitmapFactory de Robolectric degrada los bytes indecodificables a un bitmap
 * falso de 100×100 en vez de fallar.
 *
 * Ejecutar con `./gradlew connectedDebugAndroidTest` en un emulador.
 */
@RunWith(AndroidJUnit4::class)
class ImageImporterRejectionTest {
    @Test
    fun nonImageSourceIsRejected() {
        val importer = ImageImporter()
        val garbage = "esto no es una imagen".toByteArray()

        assertThrows(IOException::class.java) {
            importer.sanitize({ ByteArrayInputStream(garbage) }, ByteArrayOutputStream())
        }
    }

    @Test
    fun emptySourceIsRejected() {
        val importer = ImageImporter()

        assertThrows(IOException::class.java) {
            importer.sanitize({ ByteArrayInputStream(ByteArray(0)) }, ByteArrayOutputStream())
        }
    }
}
