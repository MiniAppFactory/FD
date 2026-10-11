package com.miniappfactory.frontlinedefender.game.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HARITA HAZIR DEGILKEN HAZIRLIK SURESI KORUNUR (FD-018).
 *
 * Soguk savasa giriste harita arkaplani (boyali pad yuvalari + yol) asenkron
 * geldigi icin, hazir DEGILKEN:
 *   - PREPARATION sayaci AKMAZ (oyuncu haritayi gormeden 10 sn tukenmesin),
 *   - erken dalga (otomatik ya da elle START) BASLAMAZ.
 * `setMapReady(true)` olunca normal akis devam eder.
 *
 * `mapReady` varsayilani `true` oldugu icin bu bayragi hic set etmeyen diger
 * motor testleri (PREPARATION'dan dalgaya gecenler) ETKILENMEZ — bu testin
 * kendisi varsayilani once `false` yaparak gate'i dogrular.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land-xxhdpi")
class MapReadyGateTest {

    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        audio = AudioManager(ctx)
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(ctx), audio)
        engine.updateMapDimensions(2220f, 1080f, 96f)
        engine.startNewGame(1)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    @Test
    fun preparationTimerFreezesWhileMapNotReady() {
        assertEquals(GameState.PREPARATION, engine.gameState.value)
        val before = engine.preparationTimer.value

        engine.setMapReady(false)
        repeat(20) { engine.tick(1.0f) } // normalde 20 sn -> dalga baslardi

        assertEquals("harita hazir degilken sayac DONMALI", before, engine.preparationTimer.value, 0.0001f)
        assertEquals("erken dalga BASLAMAMALI", GameState.PREPARATION, engine.gameState.value)
    }

    @Test
    fun preparationResumesWhenMapBecomesReady() {
        engine.setMapReady(false)
        engine.tick(2.0f)
        val frozen = engine.preparationTimer.value

        engine.setMapReady(true)
        engine.tick(2.0f)

        assertEquals(
            "harita hazir olunca sayac akmali (~2 sn dusmeli)",
            frozen - 2.0f,
            engine.preparationTimer.value,
            0.01f
        )
    }

    @Test
    fun manualStartIsBlockedWhileMapNotReady() {
        engine.setMapReady(false)
        engine.startNextWaveNow()
        assertEquals("harita hazir degilken elle START dalga baslatmaz", GameState.PREPARATION, engine.gameState.value)

        engine.setMapReady(true)
        engine.startNextWaveNow()
        assertEquals("harita hazir olunca START dalgayi baslatir", GameState.WAVE_RUNNING, engine.gameState.value)
    }
}
