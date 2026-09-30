package com.miniappfactory.frontlinedefender.game.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * =============================================================================
 * CEPHANELIK KART BANDLARI — SABLONA HIZA KURALLARI (saf JVM)
 * =============================================================================
 *
 * NEDEN VAR: kalkan satiri bandi UpgradeCard ve PrestigeCard'da elle 0,605 /
 * 0,315 olarak yazilmisti (merkez 0,645); sablonun ikinci metin yuvasinin
 * merkezi 0,6252. Ayni hatanin bolum kartlarindaki hali 2026-09-02'de cihazda
 * bulunup duzeltilmisti — Cephanelik'te ise iki ayri kopyada kalmisti.
 *
 * Bu test SAYIYI dondurmaz, KURAL yazar: bandlar sablonun yuvalarina oturmali.
 *
 * ⚠ AYRAC DEGERLERI BURADA BILEREK KENDI KOPYAMIZLA DURUYOR. `ShopCardBands`
 * bandlarini `DIVIDER_*` sabitlerinden kuruyor; teste ayni sabitleri okutmak
 * "bandlar ayraclara oturuyor mu" sorusunu totoloji yapardi. Asagidaki uc sayi
 * SABLON DOSYASINDAN olculen gercektir (`ui_card_shop.webp` 560x747, piksel
 * satirlari 376 / 436 / 498). Sanat yeniden uretilirse ONCE burasi, sonra
 * `ShopCardBands` guncellenir; biri digerini sessizce izleyemez.
 *
 * SINIR: bu test yalnizca SABITLERIN dogrulugunu kanitlar. Metnin gercek
 * genisligi / cihazdaki yazi konumu Robolectric'te (graphicsMode LEGACY)
 * OLCULEMEZ; onlar icin cihaz karesi gerekir.
 */
class ShopCardBandsTest {

    // Sablondan olculen ayrac cizgileri (y / h).
    private val dividerTop = 0.5033f
    private val dividerMid = 0.5837f
    private val dividerBottom = 0.6667f

    /** Piksel-yuvarlama payi: 1 piksel / 747 = 0,0013; 0,005 yaklasik 4 piksel. */
    private val tolerance = 0.005f

    private val named = listOf(
        "WINDOW" to ShopCardBands.WINDOW,
        "TITLE" to ShopCardBands.TITLE,
        "EFFECT" to ShopCardBands.EFFECT,
        "SHIELD" to ShopCardBands.SHIELD,
        "BUTTON" to ShopCardBands.BUTTON
    )

    @Test
    fun shieldBandCentreIsTheTemplateSlotCentre() {
        val slotCentre = (dividerMid + dividerBottom) / 2f // 0,6252
        assertEquals(
            "kalkan bandi merkezi ${ShopCardBands.SHIELD.centreY}, sablon yuvasi $slotCentre. " +
                "Eski elle deger 0,645 idi (~4,4 dp asagida).",
            slotCentre, ShopCardBands.SHIELD.centreY, tolerance
        )
    }

    @Test
    fun effectBandCentreIsTheTemplateSlotCentre() {
        val slotCentre = (dividerTop + dividerMid) / 2f // 0,5435
        assertEquals(slotCentre, ShopCardBands.EFFECT.centreY, tolerance)
    }

    @Test
    fun effectAndShieldBandsSitBetweenTheTemplateDividers() {
        val effect = ShopCardBands.EFFECT
        val shield = ShopCardBands.SHIELD

        assertTrue("EFFECT ust kenari ${effect.y0} < ayrac $dividerTop", effect.y0 >= dividerTop - tolerance)
        assertTrue("EFFECT alt kenari ${effect.y1} > ayrac $dividerMid", effect.y1 <= dividerMid + tolerance)
        assertTrue("SHIELD ust kenari ${shield.y0} < ayrac $dividerMid", shield.y0 >= dividerMid - tolerance)
        assertTrue("SHIELD alt kenari ${shield.y1} > ayrac $dividerBottom", shield.y1 <= dividerBottom + tolerance)

        // Eski EFFECT bandi 0,478..0,608'di: iki ayraci da KESIYORDU. Bir daha
        // yuvadan tasan (yuvanin 1,5 kati) bir band kabul edilmez.
        val slot = dividerMid - dividerTop
        assertTrue("EFFECT yuksekligi ${effect.height} yuvanin ($slot) 1,5 kati", effect.height <= slot * 1.5f)
    }

    /**
     * Dikey siraya dizili bandlar birbirinin ustune binmez.
     *
     * TEK ISTISNA: BASLIK bandinin alt kenari (0,508) EFFECT'in ust kenarina
     * (0,5033) 0,0047 h (~1 dp) girer. Bu, basligin ORTALAMA kutusudur (metin
     * kutunun ortasinda durur, kenarina degil) ve degeri cihazda dogrulanmis
     * bolum kartlariyla ayni; degistirilmedi. Sinir 0,005 ile kilitli: daha
     * fazla binerse burasi kirilir.
     */
    @Test
    fun verticalBandsDoNotOverlap() {
        val stack = listOf("WINDOW", "TITLE", "EFFECT", "SHIELD", "BUTTON")
            .map { name -> name to named.first { it.first == name }.second }

        stack.zipWithNext().forEach { (upper, lower) ->
            val overlap = upper.second.y1 - lower.second.y0
            val allowed = if (upper.first == "TITLE" && lower.first == "EFFECT") tolerance else 0.0001f
            assertTrue(
                "${upper.first} (alt ${upper.second.y1}) ile ${lower.first} (ust ${lower.second.y0}) " +
                    "$overlap kadar ust uste biniyor (izin $allowed)",
                overlap <= allowed
            )
        }

        // Numara dairesi (h kesri: boyut GENISLIGIN kesri, kare) basligin ustunde biter.
        val circleBottom = ShopCardBands.NUMBER_TOP + ShopCardBands.NUMBER_SIZE * ShopCardBands.ASPECT
        assertTrue(
            "numara dairesi alt $circleBottom, baslik ust ${ShopCardBands.TITLE.y0}",
            circleBottom <= ShopCardBands.TITLE.y0
        )
    }

    @Test
    fun dotsRowFitsBetweenTheShieldSlotAndTheButtonBand() {
        val cardHeight = ShopCardBands.CardWidth / ShopCardBands.ASPECT
        val dotsBottom = ShopCardBands.DOTS_TOP + ShopCardBands.DotFilled / cardHeight

        assertTrue(
            "noktalar ${ShopCardBands.DOTS_TOP} -> kalkan yuvasi ${ShopCardBands.SHIELD.y1} icine giriyor",
            ShopCardBands.DOTS_TOP >= ShopCardBands.SHIELD.y1
        )
        assertTrue(
            "noktalarin alti $dotsBottom, buton bandi ${ShopCardBands.BUTTON.y0} ustunde bitmeli",
            dotsBottom <= ShopCardBands.BUTTON.y0
        )
    }

    @Test
    fun everyBandAndTheNumberCircleLieInsideTheCard() {
        named.forEach { (name, b) ->
            assertTrue("$name: 0 <= x0 < x1 <= 1 degil (${b.x0}, ${b.x1})", b.x0 >= 0f && b.x0 < b.x1 && b.x1 <= 1f)
            assertTrue("$name: 0 <= y0 < y1 <= 1 degil (${b.y0}, ${b.y1})", b.y0 >= 0f && b.y0 < b.y1 && b.y1 <= 1f)
        }

        val circleRight = ShopCardBands.NUMBER_START + ShopCardBands.NUMBER_SIZE
        val circleBottom = ShopCardBands.NUMBER_TOP + ShopCardBands.NUMBER_SIZE * ShopCardBands.ASPECT
        assertTrue("numara dairesi sag $circleRight", ShopCardBands.NUMBER_START >= 0f && circleRight <= 1f)
        assertTrue("numara dairesi alt $circleBottom", ShopCardBands.NUMBER_TOP >= 0f && circleBottom <= 1f)
        assertTrue(
            "DOTS_TOP ${ShopCardBands.DOTS_TOP} kart disinda",
            ShopCardBands.DOTS_TOP > 0f && ShopCardBands.DOTS_TOP < 1f
        )
    }

    /**
     * Genislik-oncelikli boyutlandirma: 168 dp genislik SECILIR, yukseklik
     * sablonun 560x747 oranindan turer (~224 dp). 740x360 dp'de dikey butce
     * `ShopScreenRenderTest`'te gercek yerlesimle olculur.
     */
    @Test
    fun cardIsBuiltWidthFirstFromTheTemplateAspect() {
        assertEquals(560f / 747f, ShopCardBands.ASPECT, 1e-6f)
        val height = (ShopCardBands.CardWidth / ShopCardBands.ASPECT).value
        assertEquals(224f, height, 0.5f)
    }

    /** Uc kart ayni cerceveden geldigi icin bandlar kart basina kopyalanamaz — tek nesne. */
    @Test
    fun textBandsShareTheSameStartAndEndInset() {
        // Crosshair ve kalkan satirlari ayni sol/sag icbosluga sahip olmali: ikonlar
        // sablonda ayni x'te (0,243) ve metin ayni hizadan baslar.
        assertEquals(ShopCardBands.EFFECT.start, ShopCardBands.SHIELD.start, 1e-6f)
        assertEquals(ShopCardBands.EFFECT.end, ShopCardBands.SHIELD.end, 1e-6f)
    }
}
