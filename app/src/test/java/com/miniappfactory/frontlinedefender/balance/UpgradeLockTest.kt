package com.miniappfactory.frontlinedefender.balance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YUKSELTME KILIDI — "oynamadan maks" yolunun kapali kaldigini kanitlar.
 *
 * ## Neden var
 * Kilit, gercek insan oynanisindan geldi: L4'te 323 Tedarik iki MAKSIMUM
 * Gatling'i (250) finanse ediyordu ve oyuncu ilk dalga baslamadan tam gucune
 * ulasabiliyordu.
 *
 * ## Neden BU testler
 * Kilidin ilk hali `gameState == PREPARATION` diye soruyordu ve cihazda
 * ANINDA delindi: *"reklam izleyince 1. dalgada da yukseltmeye izin
 * veriyor."* Reklam akisi oyunu DURAKLATIYOR (`BoosterRail` teklif oncesi,
 * `applyBoosterAd` sonuc okunurken), yani durum PREPARATION olmaktan cikip
 * PAUSED oluyor ve kosul sessizce false donuyordu.
 *
 * Bu yuzden buradaki testler yalnizca "kilit var mi" demiyor; kilidin
 * DURAKLAMAYLA delinemedigini ayrica olcuyor. Kural, sayi degil davranis
 * olarak yaziliyor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpgradeLockTest {

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
    fun tearDown() = audio.release()

    private fun buildFirstTower() {
        engine.selectBuildSpot(engine.scaledBuildSpots.first())
        assertTrue(
            "kurulum: kule kurulamadi",
            engine.buildTower(GameConfig.TowerType.MACHINE_GUN)
        )
        engine.selectTower(engine.towers.first())
    }

    @Test
    fun `ilk hazirlik fazinda yukseltme kapali`() {
        engine.startNewGame(4)
        buildFirstTower()
        assertTrue("kilit acik olmaliydi", engine.upgradeLockedUntilFirstWave)
        assertFalse("hazirlik fazinda yukseltme gecti", engine.upgradeSelectedTower())
    }

    /**
     * ⚠ REGRESYON KILIDI — cihazda gorulen delik.
     *
     * Reklam akisi oyunu duraklatir. Kilit "hangi fazdayiz" diye sorarsa,
     * duraklama onu deler. Bu test tam olarak o yolu yurur.
     */
    @Test
    fun `duraklatmak kilidi DELMEZ`() {
        engine.startNewGame(4)
        buildFirstTower()

        // Reklam akisinin yaptigi sey: oyunu duraklat.
        engine.togglePause()
        assertTrue("kurulum: oyun duraklamadi", engine.gameState.value == GameState.PAUSED)

        assertTrue(
            "DURAKLATINCA KILIT ACILDI — kural faza degil olaya baglanmali",
            engine.upgradeLockedUntilFirstWave
        )
        engine.selectTower(engine.towers.first())
        assertFalse(
            "duraklatilmis hazirlik fazinda yukseltme gecti",
            engine.upgradeSelectedTower()
        )
    }

    @Test
    fun `ilk dalga baslayinca kilit acilir ve bir daha kapanmaz`() {
        engine.startNewGame(4)
        buildFirstTower()

        engine.startNextWaveNow()
        assertFalse("dalga basladi ama kilit hala acik", engine.upgradeLockedUntilFirstWave)

        // Dalga SIRASINDA duraklatmak da kilidi geri getirmemeli: aksi halde
        // dalga icinde kazanilan Tedarik olu paraya donerdi
        // (LevelModifierEngineTest'in savundugu ilke).
        engine.togglePause()
        assertFalse("dalga icinde duraklayinca kilit geri geldi", engine.upgradeLockedUntilFirstWave)
    }

    @Test
    fun `yeni savas kilidi yeniden kurar`() {
        engine.startNewGame(4)
        engine.startNextWaveNow()
        assertFalse(engine.upgradeLockedUntilFirstWave)

        // "Tekrar dene" ve "sonraki bolum" yollarinin ikisi de buradan gecer.
        engine.startNewGame(4)
        assertTrue("yeni savasta kilit yeniden kurulmadi", engine.upgradeLockedUntilFirstWave)
    }
}
