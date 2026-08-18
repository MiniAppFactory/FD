package com.miniappfactory.frontlinedefender.audio

import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.audio.HapticsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 14 — muzik gecis kurali ve dokunsal desen sozlesmesi.
 *
 * Bu testler ROBOLECTRIC KULLANMAZ ve kullanmamalidir: `AudioManager` ornegi
 * yaratmak `SoundPool` + `MediaPlayer` ister, oysa buradaki sorular saf veri
 * sorulari — hangi olay hangi parcaya gecirir, hangi olay ne kadar sert
 * titresir. Kurallarin Android'siz dogrulanabilmesi bilincli bir tasarim
 * karari (bkz. `AudioManager.comboEffectFor`, `GameScreen.musicSceneFor`).
 */
class FeedbackContractTest {

    // =====================================================================
    // MUZIK PARCALARI
    //
    // Savas/menu GECIS KURALI artik burada degil: muzik ses efektinden
    // degil `GameState`ten surulur ve esleme `MusicSceneTest` icinde
    // kilitlenir. Burada yalnizca parcalarin kendisi denetlenir.
    // =====================================================================

    @Test
    fun `her muzik parcasinin ayri kaynagi var`() {
        val resIds = AudioManager.MusicTrack.entries.map { it.res }
        assertEquals(
            "iki parca ayni dosyayi gosteriyorsa savas-menu gecisi duyulmaz",
            resIds.size,
            resIds.distinct().size
        )
        resIds.forEach { assertTrue("gecersiz kaynak id", it != 0) }
    }

    @Test
    fun `muzik parcalari ses efekti dosyalarini paylasmaz`() {
        val sfxRes = AudioManager.SoundEffect.entries.map { it.res }.toSet()
        AudioManager.MusicTrack.entries.forEach { track ->
            assertTrue(
                "${track.name} bir SFX dosyasini gosteriyor",
                track.res !in sfxRes
            )
        }
    }
    // =====================================================================
    // ZINCIR (COMBO) RISER'LARI
    // =====================================================================

    @Test
    fun `her zincir kademesi kendi ornegini calar`() {
        // Asil derdi bu: once dort kademe de TOWER_UPGRADE caliyordu, yani
        // tirmanma sesle hic desteklenmiyordu.
        val used = (1..4).map { AudioManager.comboEffectFor(it) }
        assertEquals("dort kademe dort AYRI ornek olmali", 4, used.distinct().size)
        assertEquals(
            listOf(
                AudioManager.SoundEffect.COMBO_UP_1,
                AudioManager.SoundEffect.COMBO_UP_2,
                AudioManager.SoundEffect.COMBO_UP_3,
                AudioManager.SoundEffect.COMBO_UP_4,
            ),
            used
        )
        assertEquals(
            "riser ornekleri ayni dosyayi paylasmamali",
            4,
            used.map { it.res }.distinct().size
        )
    }

    @Test
    fun `kademe araligi disinda kirpilir sarmaz`() {
        // Modulo alinsaydi 5. kademe EN HAFIF sese donerdi ve tirmanma
        // coker giderdi — sessizce yanlis bir oynanis hissi.
        assertEquals(AudioManager.SoundEffect.COMBO_UP_4, AudioManager.comboEffectFor(5))
        assertEquals(AudioManager.SoundEffect.COMBO_UP_4, AudioManager.comboEffectFor(99))
        assertEquals(AudioManager.SoundEffect.COMBO_UP_1, AudioManager.comboEffectFor(1))
        assertEquals("gecersiz kademe en alt basamaga dusmeli", AudioManager.SoundEffect.COMBO_UP_1, AudioManager.comboEffectFor(0))
        assertEquals(AudioManager.SoundEffect.COMBO_UP_1, AudioManager.comboEffectFor(-3))
    }

    @Test
    fun `zincir kademelerinde mix seviyesi artar`() {
        val gains = (1..4).map { AudioManager.comboEffectFor(it).gain }
        gains.zipWithNext().forEach { (lower, higher) ->
            assertTrue("ust kademe daha geride kalamaz: $gains", higher >= lower)
        }
        assertTrue("en ust kademe en alttan belirgin sekilde onde olmali", gains.last() > gains.first())
        gains.forEach { assertTrue("mix seviyesi 0..1 disinda: $it", it in 0f..1f) }
    }

    @Test
    fun `jingle nitelikli sesler pitch varyasyonu kullanmaz`() {
        // Ayni ses saniyede birkac kez calmiyorsa varyasyon YAPAY duyulur;
        // tirmanma basamaklarinda ise varyasyon merdiveni tamamen bozar.
        listOf(
            AudioManager.SoundEffect.WAVE_CLEARED,
            AudioManager.SoundEffect.COMBO_UP_1,
            AudioManager.SoundEffect.COMBO_UP_2,
            AudioManager.SoundEffect.COMBO_UP_3,
            AudioManager.SoundEffect.COMBO_UP_4,
        ).forEach {
            assertTrue("${it.name} pitch varyasyonu kapali olmali", !it.pitchVary)
        }
    }

    @Test
    fun `dalga temizleme sesi coin sesinden ayridir`() {
        // Oldurme basina coin sesi saniyede birkac kez calar; dalga sonu TEK
        // ve daha buyuk bir olay. Ayni dosyayi paylasirlarsa oyuncu ikisini
        // kulakla ayiramaz — duzeltilen hata tam olarak buydu.
        assertTrue(
            "dalga temizleme ve coin ayni ornegi kullanamaz",
            AudioManager.SoundEffect.WAVE_CLEARED.res != AudioManager.SoundEffect.COIN_EARNED.res
        )
        assertTrue(
            "dalga temizleme ve kule insasi ayni ornegi kullanamaz",
            AudioManager.SoundEffect.WAVE_CLEARED.res != AudioManager.SoundEffect.TOWER_BUILD.res
        )
    }

    // =====================================================================
    // DOKUNSAL DESENLER
    // =====================================================================

    @Test
    fun `genlik degerleri gecerli araliktadir`() {
        HapticsManager.Cue.entries.forEach { cue ->
            assertTrue(
                "${cue.name} genligi 1..255 disinda: ${cue.amplitude}",
                cue.amplitude in 1..255
            )
        }
    }

    @Test
    fun `sureler oynanabilir araliktadir`() {
        HapticsManager.Cue.entries.forEach { cue ->
            // Alt sinir: 5 ms altinda cogu titresim motoru hicbir sey uretmez.
            // Ust sinir: 80 ms ustu darbe "takildi" hissi verir ve ardarda
            // gelen olaylarda birbirine girer.
            assertTrue(
                "${cue.name} suresi 5..80 ms disinda: ${cue.durationMs}",
                cue.durationMs in 5L..80L
            )
        }
    }

    @Test
    fun `dokunsal hiyerarsi korunur`() {
        val tap = HapticsManager.Cue.TAP
        val build = HapticsManager.Cue.BUILD
        val confirm = HapticsManager.Cue.CONFIRM
        val baseHit = HapticsManager.Cue.BASE_HIT

        // Siradan dokunus EN HAFIF olmali; degilse HUD'da gezinmek yorucu olur.
        HapticsManager.Cue.entries.forEach { cue ->
            assertTrue(
                "${cue.name} siradan dokunustan hafif olamaz",
                cue.amplitude >= tap.amplitude
            )
        }

        // Bedeli olan kararlar bedelsiz dokunustan AGIR hissetmeli.
        assertTrue("kule insasi siradan dokunustan agir olmali", build.amplitude > tap.amplitude)
        assertTrue("guclendirici onayi insadan agir olmali", confirm.amplitude > build.amplitude)

        // Us hasari en sert olay: oyuncunun kacirmamasi gereken tek bildirim.
        assertEquals(
            "us hasari en yuksek genlikli olay olmali",
            baseHit.amplitude,
            HapticsManager.Cue.entries.maxOf { it.amplitude }
        )
    }

    @Test
    fun `cift darbe yalnizca olumsuz olaylarda`() {
        val doublePulsed = HapticsManager.Cue.entries.filter { it.doublePulse }.toSet()
        assertEquals(
            "cift darbe bir UYARI dilidir; olumlu olaylara verilirse anlami kaybolur",
            setOf(HapticsManager.Cue.REJECT, HapticsManager.Cue.BASE_HIT),
            doublePulsed
        )
    }

    @Test
    fun `her olayin bir sistem sabiti var`() {
        // Izin verilmeyen (varsayilan) yolda kullanilan sabit; 0 gecerli bir
        // deger oldugu icin (LONG_PRESS) yalnizca negatif olmadigi denetlenir.
        HapticsManager.Cue.entries.forEach { cue ->
            assertTrue("${cue.name} gecersiz sistem sabiti", cue.viewConstant >= 0)
        }
        assertNotNull(HapticsManager.Cue.CONFIRM)
    }
}
