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
