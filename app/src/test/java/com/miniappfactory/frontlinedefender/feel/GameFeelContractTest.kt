package com.miniappfactory.frontlinedefender.feel

import com.miniappfactory.frontlinedefender.game.engine.GameFeel
import com.miniappfactory.frontlinedefender.game.model.EffectType
import com.miniappfactory.frontlinedefender.game.model.HIT_FLASH_DURATION_SECONDS
import com.miniappfactory.frontlinedefender.game.model.VisualEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OYUN HISSI SOZLESMESI — docs/FUN_AUDIT.md 4. madde.
 *
 * Burasi sayilarin "guzel" olup olmadigini olcmez; oynanisi bozacak
 * degerlerin sessizce girmesini engeller. Hit stop bir SIMULASYON DURDURMA
 * mekanizmasi: sinirsiz birakilirsa "teknik olarak dogru ama oyun takiliyor"
 * durumu uretir.
 */
class GameFeelContractTest {

    /**
     * 80 ms ustu donma artik "agir vurus" degil, "kare dustu" olarak okunur.
     * 60 FPS'te 0.08 sn ~ 5 kare.
     */
    @Test
    fun hitStopCeilingStaysInsideReadableRange() {
        assertTrue(
            "Hit stop tavani ${GameFeel.HIT_STOP_MAX_SECONDS} sn - 80 ms uzeri " +
                "oyuncuya donma/jank olarak okunur",
            GameFeel.HIT_STOP_MAX_SECONDS <= 0.08f
        )
        assertTrue(
            "Tavan en az bir kare (16.6 ms) olmali, yoksa hit stop hic gorunmez",
            GameFeel.HIT_STOP_MAX_SECONDS >= 1f / 60f
        )
    }

    @Test
    fun everyHitStopTriggerIsUnderTheCeiling() {
        val triggers = mapOf(
            "boss" to GameFeel.HIT_STOP_BOSS_KILL,
            "tank" to GameFeel.HIT_STOP_TANK_KILL,
            "vehicle" to GameFeel.HIT_STOP_VEHICLE_KILL,
            "baseLeak" to GameFeel.HIT_STOP_BASE_LEAK,
            "comboTier" to GameFeel.HIT_STOP_COMBO_TIER
        )
        triggers.forEach { (name, value) ->
            assertTrue("$name donmasi pozitif olmali", value > 0f)
            assertTrue(
                "$name donmasi ($value sn) tavani ${GameFeel.HIT_STOP_MAX_SECONDS} asamaz",
                value <= GameFeel.HIT_STOP_MAX_SECONDS
            )
        }
    }

    /**
     * AGIRLIK SIRALAMASI: boss > tank > zirhli arac. Sira bozulursa kucuk
     * hedef buyuk hedeften daha "agir" hissettirir, yani geri bildirim
     * oynanisla celisir.
     */
    @Test
    fun heavierTargetsFreezeLonger() {
        assertTrue(
            "Komuta tanki en agir geri bildirimi almali",
            GameFeel.HIT_STOP_BOSS_KILL > GameFeel.HIT_STOP_TANK_KILL
        )
        assertTrue(
            "Tank zirhli aractan agir olmali",
            GameFeel.HIT_STOP_TANK_KILL > GameFeel.HIT_STOP_VEHICLE_KILL
        )
    }

    /**
     * Zincir kademesi bir ODUL anidir; oynanisi kesmemeli. En hafif donma
     * onun olmali, cunku ust uste gelme ihtimali en yuksek olan odur.
     */
    @Test
    fun comboTierUpIsTheLightestFreeze() {
        assertTrue(
            GameFeel.HIT_STOP_COMBO_TIER <= GameFeel.HIT_STOP_VEHICLE_KILL
        )
    }

    /**
     * Efekt butcesi: ust sinir yoksa kare basina cizim maliyeti dusman
     * sayisiyla dogrusal buyur. Sinir cok dusukse de yeni efektler eskileri
     * daha gorunurken dusurur (gorsel kirpilma).
     */
    @Test
    fun visualEffectBudgetIsBoundedButGenerous() {
        assertTrue(
            "Efekt tavani makul bir ust sinirda olmali",
            GameFeel.MAX_VISUAL_EFFECTS in 32..256
        )
    }

    // -------------------------------------------------- isabet parlamasi

    /**
     * `hitFlashTimerSeconds` motorda yazilip renderer'da okunuyor. Sure iki
     * dosyada da ciplak `0.12f` olarak duruyordu; artik TEK sabit.
     */
    @Test
    fun hitFlashDurationIsShortEnoughToReadAsAnImpact() {
        assertTrue(
            "Parlama en az bir kare surmeli",
            HIT_FLASH_DURATION_SECONDS >= 1f / 60f
        )
        assertTrue(
            "Parlama 0.25 sn'yi gecerse dusman surekli beyaz gorunur",
            HIT_FLASH_DURATION_SECONDS <= 0.25f
        )
    }

    // ---------------------------------------------------- efekt veri modeli

    @Test
    fun comboBurstEffectTypeExists() {
        assertTrue(EffectType.entries.contains(EffectType.COMBO_BURST))
    }

    @Test
    fun airStrikeRunEffectTypeExists() {
        assertTrue(EffectType.entries.contains(EffectType.AIR_STRIKE_RUN))
    }

    // --------------------------------------------------- hava taarruzu penceresi

    /**
     * Zincir penceresi bir SOZLESME: hasar tek karede uygulanir, yalnizca
     * GORSEL yayilir. Pencere buyudukce en uzaktaki hedefin can barinin
     * dusmesi ile ustundeki patlama arasindaki fark buyur ve olay "gecikmis
     * kontrol" olarak okunur — teknik olarak dogru, hissen bozuk.
     */
    @Test
    fun airStrikeChainWindowIsShortEnoughToReadAsOneEvent() {
        assertTrue(
            "Zincir penceresi (${GameFeel.AIR_STRIKE_RUN_SECONDS} sn) 0,6 sn'yi gecerse " +
                "hasar ile patlama gorunur sekilde ayrisir",
            GameFeel.AIR_STRIKE_RUN_SECONDS <= 0.6f
        )
        assertTrue(
            "Pencere en az birkac kare olmali, yoksa SIRA olusmaz ve tek puf gorunur",
            GameFeel.AIR_STRIKE_RUN_SECONDS >= 4f / 60f
        )
    }

    /**
     * Ekran flasi bir VURGU isaretidir, savas alanini gizleyen perde degil.
     * Uzun ya da opak bir flas, oyuncunun tam da o an okumasi gereken sahneyi
     * (kimin oldugu, kimin usse yaklastigi) siler.
     */
    @Test
    fun screenFlashStaysShortAndTransparentEnoughToSeeThrough() {
        assertTrue(
            "Flas suresi ${GameFeel.AIR_STRIKE_FLASH_SECONDS} sn - 0,25 sn ustu " +
                "'ekran beyazladi' olarak okunur",
            GameFeel.AIR_STRIKE_FLASH_SECONDS <= 0.25f
        )
        assertTrue(
            "Flas en az bir kare surmeli",
            GameFeel.AIR_STRIKE_FLASH_SECONDS >= 1f / 60f
        )
        assertTrue(
            "Flas tepe saydamligi ${GameFeel.AIR_STRIKE_FLASH_PEAK_ALPHA} - 0,35 ustu " +
                "sahneyi orter",
            GameFeel.AIR_STRIKE_FLASH_PEAK_ALPHA in 0.05f..0.35f
        )
    }

    /**
     * Yuzen hasar sayilari okunacak kadar yasamali ama ekranda birikmemeli.
     * Ustten sinir da gorseldir: 20'den fazla sayi ust uste biner.
     */
    @Test
    fun airStrikeDamageNumbersAreReadableAndBounded() {
        assertTrue(
            "Hasar sayisi okunacak kadar kalmali",
            GameFeel.AIR_STRIKE_DAMAGE_TEXT_SECONDS >= 0.5f
        )
        assertTrue(
            "Sayi patlamadan SONRA cikmali ama gecikme fark edilmemeli",
            GameFeel.AIR_STRIKE_DAMAGE_TEXT_LAG_SECONDS in 0f..0.15f
        )
        assertTrue(
            "Yazi tavani efekt butcesinin bir parcasi olmali",
            GameFeel.AIR_STRIKE_MAX_DAMAGE_TEXTS in 6..GameFeel.MAX_VISUAL_EFFECTS / 3
        )
    }

    /**
     * `tier` alani varsayilan 0: zincir bilmeyen her mevcut cagiran
     * (kule insasi, satis, us hasari) BIREBIR eski davranisini korur.
     */
    @Test
    fun visualEffectTierDefaultsToNeutral() {
        val fx = VisualEffect(type = EffectType.SMOKE_PUFF, posX = 0f, posY = 0f, maxAgeSeconds = 1f)
        assertEquals(0, fx.tier)
    }
}
