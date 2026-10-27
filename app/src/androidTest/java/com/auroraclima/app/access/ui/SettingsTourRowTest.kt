package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ajustes ofrece volver a ver el recorrido por Inicio (v0.3.3), justo después de la
 * fila del tutorial, y la fila pide el recorrido una vez (lo empieza el panel).
 */
@RunWith(AndroidJUnit4::class)
class SettingsTourRowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    @Test
    fun laFilaDelRecorridoVaTrasLaDelTutorialYLoPide() {
        var replays = 0
        var guides = 0
        session.setContent(composeRule) {
            AuroraV2Theme(V2Voice.Interior) {
                SettingsScreen(
                    onBack = {},
                    onChangeCode = {},
                    onChangePassword = {},
                    onReopenGuide = { guides++ },
                    onReplayTour = { replays++ },
                    onOpenPrivacy = {},
                    onSignOut = {},
                    onDeleteAccount = {}
                )
            }
        }
        val guide = composeRule.onNodeWithText(composeRule.activity.getString(R.string.access_settings_guide))
        val tour = composeRule.onNodeWithText(composeRule.activity.getString(R.string.access_settings_tour))
        assertTrue(
            "«Ver el recorrido por la app» va justo debajo de «Ver el tutorial otra vez»",
            tour.fetchSemanticsNode().boundsInRoot.top >= guide.fetchSemanticsNode().boundsInRoot.bottom
        )

        tour.performClick()

        assertEquals(1, replays)
        assertEquals(0, guides)
    }
}
