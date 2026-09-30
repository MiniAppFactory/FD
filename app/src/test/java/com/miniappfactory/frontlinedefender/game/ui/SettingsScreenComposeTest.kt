package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AYARLAR EKRANI — GERCEKTEN CIZILEN HALI.
 *
 * Reklamlar oyundan tamamen kaldirildi (2026-09-30). Ayarlar ekraninin artik
 * `adHost` parametresi ve "Reklam ayarlari" (UMP/gizlilik secenekleri) satiri
 * YOK; bu dosya kalan satirlarin (ses, kapat, surum) hala cizildigini ve
 * eski reklam satirinin GERI GELMEDIGINI kanitlar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    // =================================================================================
    // 1. Reklam/gizlilik satiri artik HIC cizilmez
    // =================================================================================

    @Test
    fun settingsScreenExposesNoPrivacyOptionsRow() {
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {}
            )
        }

        // Ekran gercekten cizildi (bos bir agac "yok" iddiasini bos yere
        // gecirirdi): ses satiri var, reklam satiri yok.
        composeRule.onNodeWithTag("settings_sound_switch").assertExists()
        composeRule.onNodeWithTag("settings_privacy_options_row").assertDoesNotExist()
        composeRule.onNodeWithTag(
            "settings_privacy_options_row", useUnmergedTree = true
        ).assertDoesNotExist()
    }

    // =================================================================================
    // 2. Ses ac/kapa: TASINDI ama davranis ayni
    // =================================================================================

    @Test
    fun soundRowReflectsStateAndReportsChanges() {
        val changes = mutableListOf<Boolean>()

        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = { changes += it },
                onDismiss = {}
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
                onDismiss = {}
            )
        }

        composeRule.onNodeWithTag("settings_sound_switch").performScrollTo().assertIsOff()
    }

    // =================================================================================
    // 3. Kapatma ve surum bilgisi
    // =================================================================================

    @Test
    fun closeButtonDismissesTheScreen() {
        var dismissed = 0

        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = { dismissed++ }
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
