package com.miniappfactory.frontlinedefender.audio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.miniappfactory.frontlinedefender.game.ui.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Faz 14 — muzik ve titresim anahtarlari GERCEKTEN CIZILIYOR VE ULASIYOR MU.
 *
 * Neden ayri bir test: "oyuncu kapatabilmeli" kabul kuralinin en sinsi
 * basarisizlik bicimi, yoneticide dogru bir `isMusicEnabled` alani olmasi ama
 * hicbir anahtarin ona baglanmamasidir. Motor tarafi (`FeedbackPrefsTest`)
 * kalicilki kanitliyor; bu test kararin EKRANA ULASTIGINI kanitliyor.
 *
 * `SettingsScreen` `GameEngine` almadigi icin burada `AudioManager` ya da
 * `HapticsManager` kurmaya gerek yok — deger + geri cagirma sozlesmesi test
 * edilebilir olmasinin tam sebebi bu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsFeedbackRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `muzik anahtari cizilir ve durumu yansitir`() {
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                musicEnabled = true,
                onMusicEnabledChange = {},
                hapticsEnabled = false,
                onHapticsEnabledChange = {}
            )
        }

        composeRule.onNodeWithTag("settings_music_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOn()

        // Kapali haptik anahtari KAPALI cizilmeli: iki satir birbirinin
        // durumunu okumamali.
        composeRule.onNodeWithTag("settings_haptics_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
    }

    @Test
    fun `muzik anahtarina dokunmak geri cagirmaya ulasir`() {
        val seen = mutableListOf<Boolean>()
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                musicEnabled = true,
                onMusicEnabledChange = { seen += it },
                hapticsEnabled = true,
                onHapticsEnabledChange = {}
            )
        }

        composeRule.onNodeWithTag("settings_music_switch").performScrollTo().performClick()

        assertEquals("muzik anahtari geri cagirmaya ulasmali", listOf(false), seen)
    }

    @Test
    fun `titresim anahtarina dokunmak geri cagirmaya ulasir`() {
        val seen = mutableListOf<Boolean>()
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                musicEnabled = true,
                onMusicEnabledChange = {},
                hapticsEnabled = false,
                onHapticsEnabledChange = { seen += it }
            )
        }

        composeRule.onNodeWithTag("settings_haptics_switch").performScrollTo().performClick()

        assertEquals("titresim anahtari geri cagirmaya ulasmali", listOf(true), seen)
    }

    @Test
    fun `anahtarlar birbirinden bagimsiz tetiklenir`() {
        val music = mutableListOf<Boolean>()
        val haptics = mutableListOf<Boolean>()
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                musicEnabled = true,
                onMusicEnabledChange = { music += it },
                hapticsEnabled = true,
                onHapticsEnabledChange = { haptics += it }
            )
        }

        composeRule.onNodeWithTag("settings_music_switch").performScrollTo().performClick()

        assertEquals(listOf(false), music)
        assertEquals("muzige dokunmak titresimi tetiklememeli", emptyList<Boolean>(), haptics)
    }
// =====================================================================
    // SIFIRLAMA SATIRLARI
    //
    // `SaveManager.resetHints/resetTutorial/resetProgress` yazilmisti ama
    // `main/` icinde HIC cagri yeri yoktu: oyuncu ogreticiyi tekrar
    // goremiyordu, tek yol adb idi. Asagidakiler satirlarin CIZILDIGINI ve
    // dogru lambdaya ULASTIGINI kilitler.
    // =====================================================================

    @Test
    fun `zararsiz sifirlamalar tek dokunusla calisir`() {
        var hints = 0
        var tutorial = 0
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                onResetHints = { hints++ },
                onResetTutorial = { tutorial++ }
            )
        }

        composeRule.onNodeWithTag("settings_reset_hints_row").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_reset_tutorial_row").performScrollTo().performClick()

        // Zararsiz islemler onay ISTEMEZ: her seyi onaya baglamak onayi
        // anlamsizlastirir ve asil tehlikeli olanda refleksle "evet" dedirtir.
        assertEquals(1, hints)
        assertEquals(1, tutorial)
    }

    @Test
    fun `ilerleme sifirlama TEK dokunusla calismaz`() {
        var resets = 0
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                onResetProgress = { resets++ }
            )
        }

        composeRule.onNodeWithTag("settings_reset_progress_row").performScrollTo().performClick()

        assertEquals("ilk dokunus yalnizca onay istemeli", 0, resets)
        composeRule.onNodeWithTag("settings_reset_cancel_row").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `ikinci dokunus ilerlemeyi sifirlar`() {
        var resets = 0
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                onResetProgress = { resets++ }
            )
        }

        composeRule.onNodeWithTag("settings_reset_progress_row").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_reset_progress_row").performScrollTo().performClick()

        assertEquals(1, resets)
        // Sonuc SESSIZ olmamali: oyuncu dokunup hicbir sey olmadigini sanmasin.
        composeRule.onNodeWithTag("settings_reset_done").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `vazgecmek sifirlamayi iptal eder`() {
        var resets = 0
        composeRule.setContent {
            SettingsScreen(
                soundEnabled = true,
                onSoundEnabledChange = {},
                onDismiss = {},
                onResetProgress = { resets++ }
            )
        }

        composeRule.onNodeWithTag("settings_reset_progress_row").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_reset_cancel_row").performScrollTo().performClick()
        // Vazgectikten SONRA tek dokunus yine uygulamamali: onay durumu
        // gercekten sifirlanmis olmali.
        composeRule.onNodeWithTag("settings_reset_progress_row").performScrollTo().performClick()

        assertEquals("vazgecme sonrasi tek dokunus silmemeli", 0, resets)
    }
}
