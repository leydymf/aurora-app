package com.auroraclima.app.access.ui

import com.auroraclima.app.core.security.SessionManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * «Seguridad en Aurora» escribe sus cifras en el texto (v0.3.4): si cambian los plazos
 * de [SessionManager], estas pruebas obligan a cambiar también la frase.
 */
class SafetyNotesTextTest {
    private val idleBody: String = run {
        val strings = sequenceOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml"))
            .first { it.exists() }
            .readText()
        requireNotNull(Regex("""<string name="access_notes_idle_body">([^<]*)</string>""").find(strings))
            .groupValues[1]
    }

    private fun minutes(pattern: String): Int? = Regex(pattern).find(idleBody)?.groupValues?.get(1)?.toInt()

    @Test
    fun `los minutos sin uso son los del plazo de inactividad`() {
        assertEquals(
            (SessionManager.INACTIVITY_TIMEOUT_MS / 60_000).toInt(),
            minutes("""A los (\d+) minutos sin uso""")
        )
    }

    @Test
    fun `la espera al tomar una foto o elegir un archivo es la de la ida y vuelta al sistema`() {
        assertEquals(
            (SessionManager.SYSTEM_RETURN_GRACE_MS / 60_000).toInt(),
            minutes("""te espera hasta (\d+) minutos""")
        )
    }
}
