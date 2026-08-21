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
     * ⚠ UST SINIR 0,6 -> 1,5 sn (2026-08-19), CUNKU SEBEBI ORTADAN KALKTI.
     *
     * Eski gerekce aynen suydu: "hasar tek karede uygulanir, yalnizca GORSEL
     * yayilir; pencere buyudukce can barinin dusmesi ile patlamanin inmesi
     * ayrisir". Dogru bir gerekceydi — ama dayandigi PREMIS artik yok: hasar da
     * ucusa yayildi, her hedef kendi bombasi indiginde vuruluyor
     * (`GameEngine.agePendingStrikes`). Ayrisma diye bir sey kalmadi.
     *
     * Bu ayrimi yazili birakiyorum cunku bu depoda "test hatanin kendisini
     * savunuyor" sinifi defalarca cikti ve disaridan bu degisiklik ona benzer.
     * Fark: sinir gevsetilmedi, sinirin OLCTUGU SEY degisti. Onceki premiste
     * 0,6 dogru sayiydi; premis dusunce sinirin de dusmesi gerekirdi.
     *
     * UST SINIR YINE DE VAR ama NE KORUDUGU 21 Agustos'ta bir kez daha
     * degisti; guncel gerekce ve sayi testin GOVDESINDE yaziyor.
     *
     * Buraya sayi YAZILMIYOR: ayni deger iki yerde durursa biri bayatlar ve
     * bu depoda tam o kalip defalarca cikti (actLabelRes, BOSS_LEVEL_IDS,
     * L19 pad sayisi, guclendirici cipi...).
     *
     * ALT SINIR DEGISMEDI: birkac kareden kisa bir pencere SIRA olusturmaz,
     * tek puf gorunur — kullanicinin "bir sey gelmedi sanki" dedigi durum.
     */
    @Test
    fun airStrikeChainWindowReadsAsOneRunWithoutMakingThePlayerWait() {
        // ⚠ TAVAN 1,5 -> 2,2 sn (2026-08-21). 1,5'i ben tahminle koymustum
        // ("odeme ile sonuc arasina bekleme girmesin") ve cihaz onu curuttu:
        // 1,10 sn'de oyuncu ucagi HALA goremiyordu. Yani gercek risk
        // "beklemek" degil, GORMEMEKTI — bir guclendiriciye odeme yapip
        // hicbir sey olmadigini sanmak.
        //
        // Tavan tamamen kalkmadi: 2,2 sn'yi asan bir kosu, oyuncunun bir
        // sonraki karari (kule kurma/yukseltme) ile arasina gercek bir olu
        // zaman koyar. Sinir hala var, yalnizca DOGRU yerde.
        assertTrue(
            "Zincir penceresi (${GameFeel.AIR_STRIKE_RUN_SECONDS} sn) 2,2 sn'yi gecerse " +
                "oyuncunun bir sonraki karariyla arasina olu zaman girer",
            GameFeel.AIR_STRIKE_RUN_SECONDS <= 2.2f
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
