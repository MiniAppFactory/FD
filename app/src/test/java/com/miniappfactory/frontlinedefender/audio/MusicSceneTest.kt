package com.miniappfactory.frontlinedefender.audio

import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.ui.musicSceneFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Faz 14 — MUZIK SAHNESI, OYUN DURUMUNDAN SURULUR.
 *
 * ---------------------------------------------------------------------------
 * DUZELTILEN HATA
 * ---------------------------------------------------------------------------
 * Gecis once `AudioManager.playSound` icinden cikarsaniyordu:
 * WAVE_START -> BATTLE, VICTORY/DEFEAT -> MENU. Acilis sessiz DEGILDI
 * (`AudioManager.init` zaten MENU ile basliyor) ama sinyal yanlisti ve bir
 * durumu HIC kapsamiyordu: **savasi bitirmeden cikan oyuncu.** Duraklama
 * menusunden bolum secmeye donuldugunde ne VICTORY ne DEFEAT calar, yani
 * savas muzigi harita ekraninda calmaya devam ederdi. Ayni sekilde "yeniden
 * basla" sonrasi hazirlik asamasi savas muzigiyle aciliyordu.
 *
 * Artik tek surucu `GameState` ve esleme burada kilitleniyor.
 */
class MusicSceneTest {

    @Test
    fun `dalga surerken savas muzigi calar`() {
        assertEquals(
            AudioManager.MusicTrack.BATTLE,
            musicSceneFor(GameState.WAVE_RUNNING)
        )
    }

    @Test
    fun `menu ve harita ekranlarinda savas muzigi CALMAZ`() {
        // Asil regresyon: bu iki durum eskiden hic tetiklenmiyordu.
        listOf(GameState.MAIN_MENU, GameState.LEVEL_SELECT).forEach { state ->
            assertEquals(
                "$state icin sakin parcaya donulmeli",
                AudioManager.MusicTrack.MENU,
                musicSceneFor(state)
            )
        }
    }

    @Test
    fun `hazirlik asamasi sakin parca calar`() {
        // Kule yerlestirirken sakin ton, ilk dalgayla savas tonu devralir:
        // dalga baslangicini DUYULUR bir olay yapar.
        assertEquals(
            AudioManager.MusicTrack.MENU,
            musicSceneFor(GameState.PREPARATION)
        )
    }

    @Test
    fun `savas sonucu sakin parcaya doner`() {
        assertEquals(AudioManager.MusicTrack.MENU, musicSceneFor(GameState.VICTORY))
        assertEquals(AudioManager.MusicTrack.MENU, musicSceneFor(GameState.DEFEAT))
    }

    @Test
    fun `duraklama parcayi DEGISTIRMEZ`() {
        // `null` = "dokunma". Duraklamada menu parcasina gecmek hos gorunur ama
        // devam edildiginde savas parcasi BASTAN baslardi; duraklama bir sahne
        // degisimi degil, ayni sahnenin askiya alinmasidir.
        assertNull(
            "duraklama muzigi bastan baslatmamali",
            musicSceneFor(GameState.PAUSED)
        )
    }

    @Test
    fun `PAUSED disinda her durum bir parca secer`() {
        // Yeni bir GameState eklenirse burada yakalanir: eslenmemis bir durum
        // muzigin o ekranda yanlis kalmasi demektir.
        GameState.entries.filter { it != GameState.PAUSED }.forEach { state ->
            assertNotNull("$state icin muzik sahnesi tanimli degil", musicSceneFor(state))
        }
    }

    @Test
    fun `yalnizca dalga surerken savas parcasi calar`() {
        val battleStates = GameState.entries.filter {
            musicSceneFor(it) == AudioManager.MusicTrack.BATTLE
        }
        assertEquals(
            "savas parcasi yalnizca WAVE_RUNNING'e ait olmali",
            listOf(GameState.WAVE_RUNNING),
            battleStates
        )
    }
}
