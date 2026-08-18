package com.miniappfactory.frontlinedefender.game.ui

import android.app.Activity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.miniappfactory.frontlinedefender.game.ads.AdHost
import com.miniappfactory.frontlinedefender.game.ads.NoOpAdHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AYARLAR EKRANI — GERCEKTEN CIZILEN HALI.
 *
 * `SettingsPrivacyGateTest` kararin dogrulugunu kanitliyor; bu test kararin
 * EKRANA ULASTIGINI kanitliyor. Ikisi ayri sorulardir ve bugun duzeltilen hata
 * tam olarak ikincisiydi: `privacyOptionsRequired` dogru hesaplaniyordu, dogru
 * okunuyordu, ama **hicbir composable'a ulasmiyordu**.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeAdHost(
        override val privacyOptionsRequired: Boolean,
        private val delegate: NoOpAdHost = NoOpAdHost()
    ) : AdHost by delegate {
        var privacyOptionsShown: Int = 0
            private set

        override fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit) {
            privacyOptionsShown++
            onDismissed()
        }
    }

    // =================================================================================
    // 1. UMP satiri: gerektiginde GORUNUR ve dokununca host'a ULASIR
    // =================================================================================

    @Test
    fun privacyOptionsRowIsRenderedAndReachesTheAdHost() {
        val host = FakeAdHost(privacyOptionsRequired = true)

        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                adHost = host
            )
        }

        composeRule.onNodeWithTag("settings_privacy_options_row")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals("dokunma UMP formuna ulasmali", 1, host.privacyOptionsShown)
    }

    // =================================================================================
    // 2. Gerekmiyorsa satir HIC cizilmez
    // =================================================================================

    @Test
    fun privacyOptionsRowIsAbsentWhenUmpDoesNotRequireIt() {
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                adHost = FakeAdHost(privacyOptionsRequired = false)
            )
        }

        composeRule.onNodeWithTag("settings_privacy_options_row").assertDoesNotExist()
    }

    // =================================================================================
    // 3. Ses ac/kapa: TASINDI ama davranis ayni
    // =================================================================================

    @Test
    fun soundRowReflectsStateAndReportsChanges() {
        val changes = mutableListOf<Boolean>()

        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = { changes += it },
                onDismiss = {},
                adHost = NoOpAdHost()
            )
        }

        // Satirin TAMAMI tek bir ac/kapa ogesi; anahtarin ustune isabet
        // ettirmek gerekmiyor.
        composeRule.onNodeWithTag("settings_sound_switch")
            .performScrollTo()
            .assertIsOn()
            .performClick()

        assertEquals(listOf(false), changes)
    }

    @Test
    fun soundRowShowsOffStateWhenSoundIsDisabled() {
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = false,
                onSoundEnabledChange = {},
                onDismiss = {},
                adHost = NoOpAdHost()
            )
        }

        composeRule.onNodeWithTag("settings_sound_switch").performScrollTo().assertIsOff()
    }

    // =================================================================================
    // 4. Kapatma ve surum bilgisi
    // =================================================================================

    @Test
    fun closeButtonDismissesTheScreen() {
        var dismissed = 0

        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = { dismissed++ },
                adHost = NoOpAdHost()
            )
        }

        composeRule.onNodeWithTag("settings_close_button").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun versionRowShowsNameAndCode() {
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                adHost = NoOpAdHost(),
                versionName = "9.9.9",
                versionCode = 42
            )
        }

        composeRule.onNodeWithTag("settings_version")
            .performScrollTo()
            .assertTextContains("9.9.9", substring = true)
        composeRule.onNodeWithTag("settings_version")
            .assertTextContains("42", substring = true)
    }
}
