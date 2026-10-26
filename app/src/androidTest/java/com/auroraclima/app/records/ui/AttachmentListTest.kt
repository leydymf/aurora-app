package com.auroraclima.app.records.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los adjuntos se muestran con su nombre genérico y ese mismo texto es la descripción
 * para lectores de pantalla (H-6). El nombre del archivo original no existe en ningún
 * modelo, así que no hay de dónde mostrarlo.
 */
@RunWith(AndroidJUnit4::class)
class AttachmentListTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun millis(day: String) =
        LocalDate.parse(day).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val attachments = listOf(
        JournalAttachment(1, AttachmentKind.IMAGE, createdAtEpochMillis = millis("2026-10-12")),
        JournalAttachment(2, AttachmentKind.AUDIO, createdAtEpochMillis = millis("2026-10-13")),
        JournalAttachment(3, AttachmentKind.DOCUMENT, createdAtEpochMillis = millis("2026-10-14")),
        JournalAttachment(4, AttachmentKind.IMAGE, createdAtEpochMillis = millis("2026-10-15"))
    )

    private fun show(onOpen: ((JournalAttachment) -> Unit)?, onRemove: ((JournalAttachment) -> Unit)?) {
        composeRule.setContent {
            AuroraV2Theme(V2Voice.Interior) {
                AttachmentList(attachments = attachments, onOpen = onOpen, onRemove = onRemove)
            }
        }
    }

    @Test
    fun cadaAdjuntoSeAnunciaConSuNombreGenerico() {
        show(onOpen = {}, onRemove = null)

        composeRule.onNodeWithContentDescription("Imagen 1 · 12 oct").assertExists()
        composeRule.onNodeWithContentDescription("Audio 1 · 13 oct").assertExists()
        composeRule.onNodeWithContentDescription("Documento 1 · 14 oct").assertExists()
        composeRule.onNodeWithContentDescription("Imagen 2 · 15 oct").assertExists()
    }

    @Test
    fun enElEditorCadaFilaSeQuitaPorSuNombre() {
        val removed = mutableListOf<Long>()
        show(onOpen = null, onRemove = { removed += it.id })

        composeRule.onNodeWithContentDescription("Quitar Imagen 2 · 15 oct").assertHasClickAction().performClick()

        assertEquals(listOf(4L), removed)
    }

    @Test
    fun elAudioNoAbreVisorPeroSiSeAnuncia() {
        val opened = mutableListOf<Long>()
        show(onOpen = { opened += it.id }, onRemove = null)

        composeRule.onNodeWithContentDescription("Audio 1 · 13 oct").performClick()
        composeRule.onNodeWithContentDescription("Imagen 1 · 12 oct").performClick()

        assertEquals("el audio no abre visor en el MVP", listOf(1L), opened)
    }

    @Test
    fun noHayRastroDeNombresDeArchivo() {
        show(onOpen = {}, onRemove = null)

        composeRule.onAllNodes(hasContentDescription(".pdf", substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription(".jpg", substring = true)).assertCountEquals(0)
        composeRule.activity.getString(R.string.journal_media_name, "Imagen", 1, "12 oct").let {
            assertEquals("Imagen 1 · 12 oct", it)
        }
    }
}
