package com.miniappfactory.frontlinedefender.modifiers

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.BattleTelemetry
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import com.miniappfactory.frontlinedefender.game.ui.BuildRejectionStrip
import com.miniappfactory.frontlinedefender.game.ui.HUDOverlay
import com.miniappfactory.frontlinedefender.game.ui.TowerBuildBar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ===========================================================================
 * BOLUM DEGISTIRICILERI — **OYUNCU SEBEBI GORUYOR MU?**
 * ===========================================================================
 *
 * Kabul kurali: *"reddedilen bir insa sessiz olmamali"* ve *"sebebi AYIRT
 * EDILEBILIR olmali"*. Motorun dogru reddetmesi bunun YARISIDIR; ikinci yarisi
 * ekranda okunabilir bir sebebin olmasidir.
 *
 * Burada gercek composable cizilir ve GERCEK dokunus yapilir:
 *  · kadro disi kart "KADRO DISI" etiketi tasir — bolum kilidinin "Lv N"
 *    etiketinden FARKLI bir metin,
 *  · tavan dolunca karta basmak SESSIZ kalmaz, ret seridi acilir,
 *  · tavan ve insa penceresi HUD'da SUREKLI gorunur, yani oyuncu kurali
 *    reddedilmeden once ogrenir.
 *
 * Ekran geometrisi gercek oynanis geometrisi (yatay, 740x360 dp): insa
 * cubugunun dort karti dar dikey ekranda sikisir ve olcum yaniltir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land-xxhdpi")
class LevelModifierUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private object NoTelemetry : BattleTelemetry {
        override fun noteTowerBuilt(towerTypeName: String) {}
        override fun noteTowerUpgraded() {}
        override fun noteTowerSold() {}
        override fun noteSellTrackingActive() {}
        override fun notePrepTimerSkipped() {}
        override fun noteGameSpeed(speed: Float) {}
    }

    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        audio = AudioManager(ctx)
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(ctx), audio)
        engine.updateMapDimensions(2220f, 1080f, 96f)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    private fun selectFreePad() {
        val occupied = engine.towers.map { it.buildSpotId }.toSet()
        engine.selectBuildSpot(engine.scaledBuildSpots.first { it.id !in occupied })
    }

    /** Kadro disi kartin etiketi bolum kilidininkinden FARKLI bir cumle olmali. */
    @Test
    fun `kadro disi kart kendi sebebini yazar`() {
        engine.startNewGame(15) // Gatling bu harekatta yok
        selectFreePad()
        composeRule.setContent { TowerBuildBar(gameEngine = engine, telemetry = NoTelemetry) }

        composeRule.onNodeWithTag("build_card_machine_gun").assertIsDisplayed()
        // `useUnmergedTree`: kart Row'u tiklanabilir oldugu icin cocuklarinin
        // semantigini birlestiriyor; sebep satirini kendi dugumu olarak okumak
        // icin birlesmemis agac gerekir.
        composeRule.onNodeWithTag("build_locked_machine_gun", useUnmergedTree = true)
            .assertTextEquals("OFF ROSTER")
        // Kadroda OLAN kule sebep satiri tasimaz (fiyatini gosterir).
        composeRule.onNodeWithTag("build_locked_cannon", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    /** Tavan dolunca kart hâlâ dokunusa CEVAP verir: ret seridi acilir. */
    @Test
    fun `tavan dolunca karta basmak ret seridini acar`() {
        engine.startNewGame(19)
        val cap = GameConfig.levelSpec(19).maxTowers!!
        repeat(cap) {
            selectFreePad()
            engine.buildTower(TowerType.MACHINE_GUN)
        }
        assertEquals(cap, engine.towerCount.value)
        selectFreePad()

        composeRule.setContent {
            TowerBuildBar(gameEngine = engine, telemetry = NoTelemetry)
            BuildRejectionStrip(gameEngine = engine)
        }

        composeRule.onNodeWithTag("build_rejection_caption").assertDoesNotExist()
        composeRule.onNodeWithTag("build_card_machine_gun").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("build_rejection_caption").assertIsDisplayed()
    }

    /** Tavan HUD'da SUREKLI okunur: oyuncu kalan hakkini reddedilmeden bilir. */
    @Test
    fun `HUD tavan rozeti kalan mevzi hakkini gosterir`() {
        engine.startNewGame(19)
        selectFreePad()
        engine.buildTower(TowerType.MACHINE_GUN)

        composeRule.setContent {
            HUDOverlay(gameEngine = engine, onOpenPauseMenu = {}, telemetry = NoTelemetry)
        }
        val cap = GameConfig.levelSpec(19).maxTowers
        composeRule.onNodeWithTag("emplacement_cap_text").assertTextEquals("EMP 1/$cap")
    }

    /** Insa penceresi rozeti dalga baslayinca PLAN'dan LOCKED'a doner. */
    @Test
    fun `HUD insa penceresi rozeti dalga ile durum degistirir`() {
        engine.startNewGame(24)
        composeRule.setContent {
            HUDOverlay(gameEngine = engine, onOpenPauseMenu = {}, telemetry = NoTelemetry)
        }
        composeRule.onNodeWithTag("build_window_text").assertTextEquals("PLAN")

        engine.startNextWaveNow()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("build_window_text").assertTextEquals("LOCKED")
    }

    /** Degistiricisiz bolumde HUD birebir eskisi gibi: hicbir rozet eklenmez. */
    @Test
    fun `degistiricisiz bolumde HUD rozetleri hic cizilmez`() {
        engine.startNewGame(20)
        composeRule.setContent {
            HUDOverlay(gameEngine = engine, onOpenPauseMenu = {}, telemetry = NoTelemetry)
        }
        composeRule.onNodeWithTag("emplacement_cap_text").assertDoesNotExist()
        composeRule.onNodeWithTag("build_window_text").assertDoesNotExist()
    }
}
