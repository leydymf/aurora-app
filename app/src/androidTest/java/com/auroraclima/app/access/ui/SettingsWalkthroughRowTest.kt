package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ajustes ofrece una sola ayuda para usar la app (v0.3.4): «Ver la guía de Aurora»,
 * que la pide una vez (la empieza el panel). Ya no quedan «Ver el tutorial otra vez»
 * ni «Ver el recorrido por la app».
 */
@RunWith(AndroidJUnit4::class)
class SettingsWalkthroughRowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    @Test
    fun unaSolaFilaPideLaGuia() {
        var replays = 0
        session.setContent(composeRule) {
            AuroraV2Theme(V2Voice.Interior) {
                SettingsScreen(
                    onBack = {},
                    onChangeCode = {},
                    onChangePassword = {},
                    onReplayWalkthrough = { replays++ },
                    onOpenPrivacy = {},
                    onSignOut = {},
                    onDeleteAccount = {}
                )
            }
        }
        val row = composeRule.activity.getString(R.string.access_settings_walkthrough)
        composeRule.onAllNodesWithText(row).assertCountEquals(1)
        listOf("Ver el tutorial otra vez", "Ver el recorrido por la app").forEach {
            composeRule.onAllNodesWithText(it).assertCountEquals(0)
        }

        composeRule.onNodeWithText(row).performClick()

        assertEquals(1, replays)
    }
}
