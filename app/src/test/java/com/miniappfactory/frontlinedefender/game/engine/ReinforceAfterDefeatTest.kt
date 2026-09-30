package com.miniappfactory.frontlinedefender.game.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DEVAM'IN MOTOR ETKISI: `GameEngine.reinforceAfterDefeat`.
 *
 * Bu metot yenilgiden sonra "coin karsiligi devam"in TEK motor ayagidir ve eskiden hicbir testi
 * yoktu: ekonomi tarafi (`CampaignProgressImpl.continueAfterDefeat`) bedeli YALNIZCA bu metot
 * `> 0` dondururse keser; yani buradaki sozlesme bedel/etki "tam bir kez" kuralinin yarisidir.
 *
 * KILITLENEN SOZLESME
 *  - DEFEAT disinda hicbir sey degismez ve 0 doner (ikinci dokunus / yanlis durum bedava ve zararsiz),
 *  - DEFEAT'te: us cani `min(istenen, maxLives)` olur, saha temizlenir, SAME dalga yeniden kurulur ve
 *    PREPARATION'a doner, `battleEpoch` ARTMAZ (artsaydi savasa kapsamli devam sayaci sifirlanirdi),
 *  - gecersiz girdi (<= 0) hicbir sey degistirmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land-xxhdpi")
class ReinforceAfterDefeatTest {

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

    /** Kule kurmadan oynanir: dusmanlar sizar ve savas DEFEAT'e duser. */
    private fun driveToDefeat() {
        engine.startNewGame(1)
        engine.startNextWaveNow()
        var steps = 0
        while (engine.gameState.value != GameState.DEFEAT && steps < MAX_STEPS) {
            if (engine.gameState.value == GameState.PREPARATION) engine.startNextWaveNow()
            engine.tick(0.1f)
            steps++
        }
        assertEquals(
            "senaryo anlamli degil: kulesiz oyun $MAX_STEPS adimda DEFEAT'e dusmedi",
            GameState.DEFEAT,
            engine.gameState.value
        )
    }

    @Test
    fun refusesOutsideDefeatAndChangesNothing() {
        engine.startNewGame(1)
        val stateBefore = engine.gameState.value
        val livesBefore = engine.lives.value
        val waveBefore = engine.currentWaveIndex.value

        assertEquals("PREPARATION'da devam 0 donmeli", 0, engine.reinforceAfterDefeat(5))

        assertEquals(stateBefore, engine.gameState.value)
        assertEquals(livesBefore, engine.lives.value)
        assertEquals(waveBefore, engine.currentWaveIndex.value)
    }

    @Test
    fun inDefeatRestoresLivesClearsTheFieldAndReArmsTheSameWave() {
        driveToDefeat()
        val epochBefore = engine.battleEpoch.value
        val waveBefore = engine.currentWaveIndex.value
        val scoreBefore = engine.score.value
        val goldBefore = engine.gold.value
        assertEquals("DEFEAT'te us cani 0 olmali", 0, engine.lives.value)

        val applied = engine.reinforceAfterDefeat(5)

        assertEquals("istenen 5 can, maxLives uzerinde degil", minOf(5, engine.maxLives), applied)
        assertEquals(applied, engine.lives.value)
        assertEquals(GameState.PREPARATION, engine.gameState.value)
        assertTrue("saha temizlenmeli (dusman)", engine.enemies.isEmpty())
        assertTrue("saha temizlenmeli (mermi)", engine.projectiles.isEmpty())
        assertEquals("AYNI dalga yeniden kurulmali", waveBefore, engine.currentWaveIndex.value)
        assertEquals(
            "battleEpoch ARTMAMALI: artsaydi savasa kapsamli devam sayaci sifirlanir, sinirsiz devam acilirdi",
            epochBefore,
            engine.battleEpoch.value
        )
        assertEquals("skor korunmali", scoreBefore, engine.score.value)
        assertEquals("Tedarik korunmali", goldBefore, engine.gold.value)
        assertTrue("hazirlik sayaci yeniden kurulmali", engine.preparationTimer.value > 0f)
    }

    @Test
    fun aSecondCallIsANoOpBecauseTheStateIsNoLongerDefeat() {
        driveToDefeat()
        assertTrue(engine.reinforceAfterDefeat(5) > 0)
        val livesAfterFirst = engine.lives.value

        assertEquals("cift dokunus: ikinci cagri 0 donmeli", 0, engine.reinforceAfterDefeat(5))

        assertEquals(livesAfterFirst, engine.lives.value)
        assertEquals(GameState.PREPARATION, engine.gameState.value)
    }

    @Test
    fun restoredLivesAreCappedByMaxLives() {
        driveToDefeat()

        val applied = engine.reinforceAfterDefeat(10_000)

        assertEquals(engine.maxLives, applied)
        assertEquals(engine.maxLives, engine.lives.value)
    }

    @Test
    fun nonPositiveInputIsIgnoredEvenInDefeat() {
        driveToDefeat()

        assertEquals(0, engine.reinforceAfterDefeat(0))
        assertEquals(0, engine.reinforceAfterDefeat(-3))

        assertEquals("gecersiz girdi durumu degistirmemeli", GameState.DEFEAT, engine.gameState.value)
        assertEquals(0, engine.lives.value)
    }

    private companion object {
        const val MAX_STEPS = 60_000
    }
}
