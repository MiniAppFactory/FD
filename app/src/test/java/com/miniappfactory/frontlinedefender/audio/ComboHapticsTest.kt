package com.miniappfactory.frontlinedefender.audio

import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.audio.HapticsFeedback
import com.miniappfactory.frontlinedefender.game.audio.HapticsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 14 — ZINCIR KADEMESI: SES VE DOKUNSAL MERDIVEN.
 *
 * ---------------------------------------------------------------------------
 * BU TESTLERIN VAR OLABILMESI TASARIM KARARININ SONUCU
 * ---------------------------------------------------------------------------
 * Motor dokunsal geri bildirimi bir `StateFlow` ile yayinsaydi buradaki
 * sorularin hicbiri saf JUnit'te sorulamazdi. `HapticsFeedback` saf Kotlin bir
 * arayuz oldugu icin sahte bir uygulama yeterli.
 *
 * Ayrica akis reddinin BIRINCI gerekcesi de burada kanitlaniyor:
 * `MutableStateFlow` ayni degeri tekrar yaymaz, yani arka arkaya iki kademe-2
 * tirmanisi TEK olay olurdu. Dogrudan cagri deseninde ikisi de teslim edilir.
 */
class ComboHapticsTest {

    /** Motorun gordugu tek yuzeyin sahte uygulamasi. */
    private class FakeHaptics : HapticsFeedback {
        val comboTiers = mutableListOf<Int>()
        var baseHits = 0
        override fun onComboTierUp(tier: Int) { comboTiers += tier }
        override fun onBaseHit() { baseHits++ }
    }

    // =====================================================================
    // DIKISIN KENDISI
    // =====================================================================

    @Test
    fun `kademe 3 tam olarak bir dokunsal olay uretir`() {
        val fake = FakeHaptics()

        fake.onComboTierUp(3)

        assertEquals("kademe 3 tek bir olay olmali", listOf(3), fake.comboTiers)
        assertEquals("zincir tirmanisi us hasari tetiklememeli", 0, fake.baseHits)
    }

    @Test
    fun `arka arkaya ayni kademe IKI kez teslim edilir`() {
        // AKIS REDDININ GEREKCESI. `MutableStateFlow` ayni degeri tekrar
        // yaymadigi icin bu senaryoda ikinci tirmanis DUSERDI: oyuncu iki
        // kademe atlar, tek titresim hisseder.
        val fake = FakeHaptics()

        fake.onComboTierUp(2)
        fake.onComboTierUp(2)

        assertEquals(listOf(2, 2), fake.comboTiers)
    }

    @Test
    fun `us hasari ile zincir ayri olaylardir`() {
        val fake = FakeHaptics()

        fake.onComboTierUp(1)
        fake.onBaseHit()

        assertEquals(listOf(1), fake.comboTiers)
        assertEquals(1, fake.baseHits)
    }

    // =====================================================================
    // DOKUNSAL MERDIVEN
    // =====================================================================

    @Test
    fun `her kademe ayri bir dokunsal basamak`() {
        val cues = (1..4).map { HapticsManager.comboCueFor(it) }
        assertEquals("dort kademe dort AYRI basamak olmali", 4, cues.distinct().size)
        assertEquals(
            listOf(
                HapticsManager.Cue.COMBO_1,
                HapticsManager.Cue.COMBO_2,
                HapticsManager.Cue.COMBO_3,
                HapticsManager.Cue.COMBO_4,
            ),
            cues
        )
    }

    @Test
    fun `dokunsal siddet kademeyle ARTAR`() {
        val cues = (1..4).map { HapticsManager.comboCueFor(it) }
        cues.zipWithNext().forEach { (lower, higher) ->
            assertTrue(
                "genlik gerilemiş: ${lower.name}=${lower.amplitude} -> ${higher.name}=${higher.amplitude}",
                higher.amplitude > lower.amplitude
            )
            assertTrue(
                "sure gerilemiş: ${lower.name} -> ${higher.name}",
                higher.durationMs > lower.durationMs
            )
        }
    }

    @Test
    fun `kademe araligi disinda kirpilir sarmaz`() {
        // Modulo alinsaydi 5. kademe EN HAFIF darbeye donerdi: tirmanma tam
        // zirvede cokerdi ve bunu kimse fark etmezdi.
        assertEquals(HapticsManager.Cue.COMBO_4, HapticsManager.comboCueFor(5))
        assertEquals(HapticsManager.Cue.COMBO_4, HapticsManager.comboCueFor(99))
        assertEquals(HapticsManager.Cue.COMBO_1, HapticsManager.comboCueFor(1))
        assertEquals(HapticsManager.Cue.COMBO_1, HapticsManager.comboCueFor(0))
        assertEquals(HapticsManager.Cue.COMBO_1, HapticsManager.comboCueFor(-7))
    }

    @Test
    fun `zincir basamaklari arayuz cue'larini PAYLASMAZ`() {
        // Zincir bir OYNANIS olayi, "kule kuruldu" bir arayuz onayi. Ayni cue
        // paylasilsaydi buton hissini ayarlamak zincir merdivenini sessizce
        // bozardi.
        val comboCues = (1..4).map { HapticsManager.comboCueFor(it) }.toSet()
        val uiCues = setOf(
            HapticsManager.Cue.TAP,
            HapticsManager.Cue.PREVIEW,
            HapticsManager.Cue.BUILD,
            HapticsManager.Cue.UPGRADE,
            HapticsManager.Cue.SELL,
            HapticsManager.Cue.ARM,
            HapticsManager.Cue.CONFIRM,
            HapticsManager.Cue.REJECT,
        )
        assertEquals(
            "zincir basamaklari arayuz cue'lariyla cakisiyor",
            emptySet<HapticsManager.Cue>(),
            comboCues intersect uiCues
        )
    }

    // =====================================================================
    // SES VE DOKUNSAL AYNI MERDIVENI CIKAR
    // =====================================================================

    @Test
    fun `ses ve dokunsal merdivenler ayni uzunlukta`() {
        // Uc kanal (gorsel, ses, dokunsal) ayni karede ve ayni yonde
        // tirmanmali; biri dort basamak digeri uc olsaydi ust kademelerde
        // kanallar birbirinden kopardi.
        (1..4).forEach { tier ->
            val fx = AudioManager.comboEffectFor(tier)
            val cue = HapticsManager.comboCueFor(tier)
            assertTrue("kademe $tier icin ses ornegi yok", fx.res != 0)
            assertTrue("kademe $tier icin dokunsal basamak yok", cue.amplitude > 0)
        }
        assertEquals(
            "ses basamak sayisi",
            4,
            (1..4).map { AudioManager.comboEffectFor(it) }.distinct().size
        )
        assertEquals(
            "dokunsal basamak sayisi",
            4,
            (1..4).map { HapticsManager.comboCueFor(it) }.distinct().size
        )
    }

    @Test
    fun `ses ve dokunsal ayni yonde tirmanir`() {
        val gains = (1..4).map { AudioManager.comboEffectFor(it).gain }
        val amps = (1..4).map { HapticsManager.comboCueFor(it).amplitude }
        assertEquals("ses en ust kademede en onde olmali", gains.max(), gains.last())
        assertEquals("dokunsal en ust kademede en sert olmali", amps.max(), amps.last())
    }
}
