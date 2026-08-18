package com.miniappfactory.frontlinedefender.game.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.BattleTelemetry
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Faz 15 — **DIKIS GERCEKTEN VAR MI?**
 *
 * `MissionTelemetryTest` ekonomi tarafinin dogru saydigini kanitliyor. Bu dosya
 * bir kat asagisini sorar ve tam olarak Faz 14'te KACIRILAN seyi olcer:
 * **savas ekranindaki dugmeler ekonomiye haber veriyor mu?**
 *
 * O hata sinifi bu projede iki kez yasandi: kod eksiksiz yazilmis, testler
 * yesil, ama iki katman arasinda hicbir cagri yok. `battleMissionDeltas` saf ve
 * testliydi; onu besleyecek `note*` cagrilarini KIMSE yapmiyordu. Derleyici de
 * susuyordu cunku eksik olan sey bir cagri, bir tip degildi.
 *
 * Bugun dikis derleyici zorunlu (`telemetry` parametresinin varsayilani yok),
 * ama "parametre geciliyor" ile "dugmeye basinca sayac artiyor" ayni sey
 * degildir — arada `if (engine.buildTower(...))` gibi kosullar var. Bu yuzden
 * burada GERCEK COMPOSABLE cizilir ve GERCEK DUGMEYE basilir.
 *
 * Ayrica alt cekmecelerin olculen yuksekliginin `GameConfig`teki sabitlerle
 * ayni oldugunu dogrular — `GameCanvas`in "ortulen secim hayaleti" o sabitleri
 * capa olarak kullaniyor.
 */
@RunWith(RobolectricTestRunner::class)
// EKRAN GEOMETRISI ZORUNLU. Oyun `sensorLandscape`; Robolectric'in varsayilani
// ise dar bir DIKEY ekran ve orada insa cubugunun dort karti sikisip cekmeceyi
// 63 dp yerine 99 dp'ye cikariyor (olculdu). Cekmece yuksekligi `GameCanvas`in
// secim hayaletinin capasi oldugu icin olcum gercek oynanis geometrisinde
// yapilmali: 2220x1080 / xxhdpi = 740x360 dp (Galaxy S8, PERFORMANCE_REPORT).
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land-xxhdpi")
class MissionTelemetryWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Ne bildirildiyse aynen kaydeder; hicbir ekonomi kurali calistirmaz. */
    private class Recorder : BattleTelemetry {
        val built = mutableListOf<String>()
        var upgrades = 0
        var sells = 0
        var sellTrackingActive = 0
        var prepSkips = 0
        val speeds = mutableListOf<Float>()

        override fun noteTowerBuilt(towerTypeName: String) { built += towerTypeName }
        override fun noteTowerUpgraded() { upgrades++ }
        override fun noteTowerSold() { sells++ }
        override fun noteSellTrackingActive() { sellTrackingActive++ }
        override fun notePrepTimerSkipped() { prepSkips++ }
        override fun noteGameSpeed(speed: Float) { speeds += speed }
    }

    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine
    private val recorder = Recorder()

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        audio = AudioManager(ctx)
        // Robolectric SoundPool golgesi olcumu ilgilendirmiyor; ses kapali.
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(ctx), audio)
        engine.updateMapDimensions(2220f, 1080f, 96f)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    // =================================================================================
    // TowerBuildBar -> noteTowerBuilt
    // =================================================================================

    /**
     * `d_v_build15` ve `d_s_all_towers` gorevlerinin TEK besleyicisi: insa
     * kartina dokunmak.
     */
    @Test
    fun tappingABuildCardReportsTheTowerToTelemetry() {
        engine.startNewGame(LATE_LEVEL)
        engine.selectBuildSpot(engine.scaledBuildSpots.first())

        composeRule.setContent { TowerBuildBar(gameEngine = engine, telemetry = recorder) }

        composeRule.onNodeWithTag("build_card_machine_gun").performClick()
        composeRule.waitForIdle()

        assertEquals(
            "insa kartina basildi ama ekonomiye HIC haber gitmedi",
            listOf(GameConfig.TowerType.MACHINE_GUN.name),
            recorder.built,
        )
    }

    /**
     * Bildirilen ad `GameConfig.TowerType` adi olmali: `distinctTowerTypes`
     * bu dizeleri bir kumede sayiyor, yani ad tutarsizligi "tum tipleri kur"
     * gorevini sessizce imkansiz yapardi.
     */
    @Test
    fun everyUnlockedTowerTypeReportsItsEnumName() {
        engine.startNewGame(LATE_LEVEL)
        engine.selectBuildSpot(engine.scaledBuildSpots.first())

        // `setContent` test basina BIR KEZ cagrilabilir; pad secimi akis
        // uzerinden degistigi icin cubuk kendini zaten yeniden besteler.
        composeRule.setContent { TowerBuildBar(gameEngine = engine, telemetry = recorder) }

        GameConfig.TowerType.values().forEachIndexed { index, type ->
            engine.selectBuildSpot(engine.scaledBuildSpots[index])
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("build_card_${type.name.lowercase()}").performClick()
            composeRule.waitForIdle()
        }

        assertEquals(
            "bildirilen adlar enum adlariyla ayni degil",
            GameConfig.TowerType.values().map { it.name }.toSet(),
            recorder.built.toSet(),
        )
    }

    /**
     * **REDDEDILEN INSA SAYILMAZ.** Kilitli kule kartina basmak gorevi
     * ilerletmemeli; aksi halde bolum 1'de kilitli kartlara basarak
     * `d_v_build15` bedavaya doldurulurdu.
     */
    @Test
    fun tappingALockedBuildCardReportsNothing() {
        engine.startNewGame(1)
        engine.selectBuildSpot(engine.scaledBuildSpots.first())

        val locked = GameConfig.TowerType.values()
            .first { !GameConfig.isTowerUnlocked(it, 1) }

        composeRule.setContent { TowerBuildBar(gameEngine = engine, telemetry = recorder) }
        composeRule.onNodeWithTag("build_card_${locked.name.lowercase()}").performClick()
        composeRule.waitForIdle()

        assertTrue(
            "kilitli kule kurulmus gibi sayildi: ${recorder.built}",
            recorder.built.isEmpty(),
        )
    }

    // =================================================================================
    // SelectedTowerInspector -> noteTowerUpgraded / noteTowerSold
    // =================================================================================

    /** `d_v_upg30`in tek besleyicisi: yukseltme dugmesi. */
    @Test
    fun tappingUpgradeReportsToTelemetry() {
        engine.startNewGame(LATE_LEVEL)
        engine.selectBuildSpot(engine.scaledBuildSpots.first())
        assertTrue("kurulum: kule kurulamadi", engine.buildTower(GameConfig.TowerType.MACHINE_GUN))
        engine.selectTower(engine.towers.first())

        composeRule.setContent {
            SelectedTowerInspector(gameEngine = engine, telemetry = recorder)
        }
        composeRule.onNodeWithTag("upgrade_button").performClick()
        composeRule.waitForIdle()

        assertEquals("yukseltme dugmesi ekonomiye haber vermedi", 1, recorder.upgrades)
    }

    /**
     * `d_s_no_sell`in bozulma yolu: satis dugmesi. Bildirilmezse kule satan
     * oyuncu "satmadan temizle" odulunu (120 coin) haksiz alirdi.
     */
    @Test
    fun tappingSellReportsToTelemetry() {
        engine.startNewGame(LATE_LEVEL)
        engine.selectBuildSpot(engine.scaledBuildSpots.first())
        assertTrue("kurulum: kule kurulamadi", engine.buildTower(GameConfig.TowerType.MACHINE_GUN))
        engine.selectTower(engine.towers.first())

        composeRule.setContent {
            SelectedTowerInspector(gameEngine = engine, telemetry = recorder)
        }
        composeRule.onNodeWithTag("sell_button").performClick()
        composeRule.waitForIdle()

        assertEquals("satis dugmesi ekonomiye haber vermedi", 1, recorder.sells)
    }

    // =================================================================================
    // HUDOverlay -> notePrepTimerSkipped / noteGameSpeed
    // =================================================================================

    /** `d_p_skip3`un tek besleyicisi: DALGA BASLAT dugmesi. */
    @Test
    fun startingTheWaveEarlyReportsAPrepTimerSkip() {
        engine.startNewGame(LATE_LEVEL)
        assertEquals(
            "kurulum: bolum hazirlik fazinda baslamali",
            GameState.PREPARATION,
            engine.gameState.value,
        )

        composeRule.setContent {
            HUDOverlay(gameEngine = engine, onOpenPauseMenu = {}, telemetry = recorder)
        }
        composeRule.onNodeWithTag("start_wave_button").performClick()
        composeRule.waitForIdle()

        assertEquals("DALGA BASLAT ekonomiye haber vermedi", 1, recorder.prepSkips)
        assertEquals(GameState.WAVE_RUNNING, engine.gameState.value)
    }

    /** `d_s_double_speed`in tek besleyicisi: hiz dugmesi. */
    @Test
    fun togglingSpeedReportsTheNewSpeed() {
        engine.startNewGame(LATE_LEVEL)

        composeRule.setContent {
            HUDOverlay(gameEngine = engine, onOpenPauseMenu = {}, telemetry = recorder)
        }
        composeRule.onNodeWithTag("speed_toggle_button").performClick()
        composeRule.waitForIdle()

        assertEquals(
            "hiz dugmesi ekonomiye YENI degeri bildirmeli (eski degeri degil)",
            listOf(2f),
            recorder.speeds,
        )

        composeRule.onNodeWithTag("speed_toggle_button").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(2f, 1f), recorder.speeds)
    }

    // =================================================================================
    // Cekmece yuksekligi <-> GameConfig sabiti
    // =================================================================================

    /**
     * `GameCanvas`in "ortulen secim hayaleti" cekmecenin ust kenarini capa
     * olarak kullaniyor ve capayi `GameConfig.BUILD_DRAWER_HEIGHT_DP` /
     * `INSPECTOR_DRAWER_HEIGHT_DP` sabitlerinden hesapliyor. Sabitler
     * cekmecelerden OLCULMUSTU ama cekmeceler onlara bagli DEGILDI: ic
     * bosluklarda yapilan bir degisiklik hayaletin capasini sessizce
     * kaydirirdi.
     *
     * Cekmeceler artik sabiti `defaultMinSize` ile okuyor; bu test de gercek
     * olculen yuksekligin sabitle AYNI oldugunu dogruluyor, yani sapma
     * (buyume yonunde de) kirmiziya duser.
     */
    @Test
    fun buildDrawerHeightMatchesTheConstantGameCanvasAnchorsTo() {
        engine.startNewGame(LATE_LEVEL)
        engine.selectBuildSpot(engine.scaledBuildSpots.first())

        composeRule.setContent { TowerBuildBar(gameEngine = engine, telemetry = recorder) }
        composeRule.onNodeWithTag("build_drawer").assertIsDisplayed()

        val bounds = composeRule.onNodeWithTag("build_drawer").getBoundsInRoot()
        val measured = bounds.bottom - bounds.top
        assertEquals(
            "TowerBuildBar yuksekligi GameConfig.BUILD_DRAWER_HEIGHT_DP ile ayrisiyor — " +
                "GameCanvas'in secim hayaleti kayar",
            GameConfig.BUILD_DRAWER_HEIGHT_DP,
            measured.value,
            DRAWER_TOLERANCE_DP,
        )
    }

    @Test
    fun inspectorDrawerHeightMatchesTheConstantGameCanvasAnchorsTo() {
        engine.startNewGame(LATE_LEVEL)
        engine.selectBuildSpot(engine.scaledBuildSpots.first())
        assertTrue(engine.buildTower(GameConfig.TowerType.MACHINE_GUN))
        engine.selectTower(engine.towers.first())

        composeRule.setContent {
            SelectedTowerInspector(gameEngine = engine, telemetry = recorder)
        }
        composeRule.onNodeWithTag("inspector_drawer").assertIsDisplayed()

        val bounds = composeRule.onNodeWithTag("inspector_drawer").getBoundsInRoot()
        val measured = bounds.bottom - bounds.top
        assertEquals(
            "SelectedTowerInspector yuksekligi GameConfig.INSPECTOR_DRAWER_HEIGHT_DP ile " +
                "ayrisiyor — GameCanvas'in secim hayaleti kayar",
            GameConfig.INSPECTOR_DRAWER_HEIGHT_DP,
            measured.value,
            DRAWER_TOLERANCE_DP,
        )
    }

    private companion object {
        /**
         * Dort kule tipinin de acik oldugu ve Tedarik'in dolu oldugu bolum
         * (GDD F: Railgun L5, Frost Field L8).
         */
        const val LATE_LEVEL = 20

        /**
         * 1 dp tolerans: Compose olcumu piksele yuvarliyor ve sabitler
         * cihazdan OLCULMUS tam sayilar. Tolerans buyutulmemeli — hayaletin
         * capasi bu sayiya bagli.
         */
        const val DRAWER_TOLERANCE_DP = 1.0f
    }
}
