package com.miniappfactory.frontlinedefender.visual

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.ui.FriendlyPlate
import com.miniappfactory.frontlinedefender.game.ui.FxOutline
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * docs/VISUAL_AUDIT.md P0-1 (dost/dusman renk ayrimi) ve P0-5 (namlu alevi +
 * tracer kontrasti) icin SAYISAL kabul testi.
 *
 * Denetimin kendi yontemi burada birebir tekrarlanir:
 *  - WCAG bagil parlaklik (sRGB -> lineer, 0,2126/0,7152/0,0722)
 *  - kontrast orani (L1 + 0,05) / (L2 + 0,05)
 *  - esik 3,0 (WCAG 1.4.11, metin disi grafik nesne)
 *
 * Bu test bir REGRESYON KILIDIDIR: plaka rengi ton araligindan cikarilirsa ya
 * da bir bant esigi dusurulurse burasi kirilir. Denetim bir kez yazilip
 * unutulan bir belge olmasin diye sayilar teste tasindi.
 */
class FriendlyPlateContrastTest {

    // ---------------------------------------------------------------------
    // Denetimden gelen OLCULMUS zemin parlakliklari (VISUAL_AUDIT 3.1).
    // Her biyomda hem YOL hem ZEMIN kontrol edilir: kule cimde durur ama namlu
    // alevi ve tracer yolun uzerinden de gecer.
    // ---------------------------------------------------------------------
    private data class BiomeGround(val name: String, val road: Double, val ground: Double)

    private val biomes = listOf(
        BiomeGround("ORIGINAL", 0.2833, 0.1594),
        BiomeGround("NIGHT", 0.2522, 0.0853),
        BiomeGround("WINTER", 0.3169, 0.3771),
        BiomeGround("DESERT", 0.2963, 0.2724),
        BiomeGround("AUTUMN", 0.2799, 0.1296)
    )

    private companion object {
        const val WCAG_THRESHOLD = 3.0

        /**
         * Ayni haritadaki iki build spot'un olculmus EN KISA mesafesi
         * (MAP_05, LevelGeometry.kt). Iki komsu plakanin cakismamasi icin
         * gecerli ust sinir budur.
         */
        const val MIN_BUILD_SPOT_DISTANCE_REF_PX = 147.0f

        /** Kademe-3 taban yayinin yaricap orani (GameCanvas.drawTower). */
        const val TIER3_ARC_RADIUS_FRAC = 0.50f
    }

    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrast(a: Double, b: Double): Double =
        (max(a, b) + 0.05) / (min(a, b) + 0.05)

    /** HSL tonu, derece. */
    private fun hueDeg(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt() / 255.0
        val g = ((argb shr 8) and 0xFF).toInt() / 255.0
        val b = (argb and 0xFF).toInt() / 255.0
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val d = mx - mn
        if (d < 1e-9) return 0.0
        val h = when (mx) {
            r -> 60.0 * (((g - b) / d) % 6.0)
            g -> 60.0 * (((b - r) / d) + 2.0)
            else -> 60.0 * (((r - g) / d) + 4.0)
        }
        return (h + 360.0) % 360.0
    }

    // =====================================================================
    // P0-1  DOST TABAN PLAKASI
    // =====================================================================

    /**
     * KABUL KRITERI: plaka tonu 180-220 derece.
     *
     * Dusman ton bandi 35,8-60,2 derece (VISUAL_AUDIT 3.5). Bu aralik o bandin
     * cok uzaginda kalir, yani plaka hicbir kosulda "dusman rengi" ailesine
     * dusmez.
     */
    @Test
    fun plateHueIsInCoolBlueBand() {
        listOf(
            "guverte" to FriendlyPlate.DECK_ARGB,
            "parlak bant" to FriendlyPlate.RIM_ARGB
        ).forEach { (label, argb) ->
            val h = hueDeg(argb)
            assertTrue(
                "plaka $label tonu $h derece, 180-220 kabul araligi disinda",
                h in 180.0..220.0
            )
        }
    }

    /** Plaka tonu dusman ton bandindan (35,8-60,2) en az 100 derece uzak olmali. */
    @Test
    fun plateHueIsFarFromEnemyHueBand() {
        listOf(FriendlyPlate.DECK_ARGB, FriendlyPlate.RIM_ARGB).forEach { argb ->
            val h = hueDeg(argb)
            val dist = min(abs(h - 35.8), abs(h - 60.2))
            assertTrue("plaka tonu $h dusman bandina $dist derece mesafede", dist >= 100.0)
        }
    }

    /**
     * KABUL KRITERI: bes biyomun HEPSINDE gorunur.
     *
     * Tek renk bunu yapamaz - bu yuzden plaka iki bantli. Her biyomda
     * BANTLARDAN EN AZ BIRI esigi gecmeli.
     */
    @Test
    fun plateIsVisibleInEveryBiome() {
        val deck = luminance(FriendlyPlate.DECK_ARGB)
        val rim = luminance(FriendlyPlate.RIM_ARGB)
        biomes.forEach { b ->
            listOf("zemin" to b.ground, "yol" to b.road).forEach { (surfaceName, bg) ->
                val cDeck = contrast(deck, bg)
                val cRim = contrast(rim, bg)
                assertTrue(
                    "${b.name}/$surfaceName: plaka gorunmuyor (guverte $cDeck, " +
                        "parlak bant $cRim, esik $WCAG_THRESHOLD)",
                    max(cDeck, cRim) >= WCAG_THRESHOLD
                )
            }
        }
    }

    /**
     * Plaka; secim halkasini, kademe-3 yayini ve KOMSU build spot'u bozmamali.
     *
     * Dort kule sabiti ayri ayri dogrulanir - ileride farkli genislikte bir
     * kule eklenirse bu test kirilir.
     */
    @Test
    fun plateGeometryDoesNotCollide() {
        GameConfig.TOWER_SPRITES.forEach { (type, spec) ->
            val plateR = spec.widthRefPx * FriendlyPlate.RADIUS_FRAC
            val selectionR = spec.widthRefPx * FriendlyPlate.SELECTION_RADIUS_FRAC
            val tierArcR = spec.widthRefPx * TIER3_ARC_RADIUS_FRAC

            assertTrue(
                "$type: secim halkasi ($selectionR) plakanin ($plateR) icinden geciyor",
                selectionR > plateR + FriendlyPlate.EDGE_REF_PX
            )
            assertTrue(
                "$type: kademe-3 yayi ($tierArcR) plaka bantlarinin uzerine dusuyor",
                tierArcR < plateR - FriendlyPlate.EDGE_REF_PX - FriendlyPlate.RIM_REF_PX
            )
            assertTrue(
                "$type: iki komsu plaka cakisiyor (2 x $plateR >= $MIN_BUILD_SPOT_DISTANCE_REF_PX)",
                2f * plateR < MIN_BUILD_SPOT_DISTANCE_REF_PX
            )
            // Komsu BOS build pad sprite'i plakanin altinda kalmamali - yoksa
            // "buraya kule dikilebilir" isareti kaybolur.
            val padHalf = GameConfig.FX_BUILD_PAD_REF_PX * 1.08f / 2f
            assertTrue(
                "$type: plaka komsu build pad'i ortuyor",
                plateR + padHalf < MIN_BUILD_SPOT_DISTANCE_REF_PX
            )
        }
    }

    /** Plakanin gorunur yakasi olmali: iki bant birlikte en az 8 ref-px. */
    @Test
    fun plateBandsAreThickEnoughToRead() {
        val total = FriendlyPlate.EDGE_REF_PX + FriendlyPlate.RIM_REF_PX
        assertTrue("plaka yakasi $total ref-px, okunamayacak kadar ince", total >= 8f)
    }

    // =====================================================================
    // P0-5  NAMLU ALEVI + TRACER
    // =====================================================================

    /**
     * KABUL KRITERI: iki sprite de bes biyomda esigi gecmeli.
     *
     * Denetimdeki ONCEKI degerler: namlu alevi en iyi 2,53 / en kotu 2,12,
     * tracer en iyi 2,50 / en kotu 1,92 - hicbiri 3,0'a ulasmiyordu. Koyu hale
     * + sicak cekirdek eklendikten SONRA her biyomda en az bir kanal esigi
     * gecer.
     */
    @Test
    fun muzzleFlashAndTracerCarryContrastInEveryBiome() {
        val halo = luminance(FxOutline.HALO_ARGB)
        val core = luminance(FxOutline.CORE_ARGB)
        biomes.forEach { b ->
            listOf("zemin" to b.ground, "yol" to b.road).forEach { (surfaceName, bg) ->
                val cHalo = contrast(halo, bg)
                val cCore = contrast(core, bg)
                assertTrue(
                    "${b.name}/$surfaceName: alev/tracer hala okunmuyor " +
                        "(koyu hale $cHalo, sicak cekirdek $cCore)",
                    max(cHalo, cCore) >= WCAG_THRESHOLD
                )
            }
        }
    }

    /**
     * Iki kanal GERCEKTEN birbirini tamamlamali: en parlak biyomda koyu hale,
     * en koyu biyomda cekirdek tasimali. Ikisi ayni yone bakarsa tek kanalli
     * cozume geri dusulur ve bir biyom sessizce esigin altina kayar.
     */
    @Test
    fun outlineChannelsAreComplementary() {
        val halo = luminance(FxOutline.HALO_ARGB)
        val core = luminance(FxOutline.CORE_ARGB)
        val brightest = biomes.maxByOrNull { it.ground }!!   // WINTER
        val darkest = biomes.minByOrNull { it.ground }!!     // NIGHT
        assertTrue(
            "${brightest.name}: koyu hale tasiyici olmali",
            contrast(halo, brightest.ground) >= WCAG_THRESHOLD
        )
        assertTrue(
            "${darkest.name}: sicak cekirdek tasiyici olmali",
            contrast(core, darkest.ground) >= WCAG_THRESHOLD
        )
    }

    /** Hale sprite'i tamamen yutmamali, cekirdek de sprite'i ezmemeli. */
    @Test
    fun outlineProportionsStayReadable() {
        assertTrue(
            "hale olcegi ${FxOutline.HALO_SCALE} - kontur degil ikinci bir alev olur",
            FxOutline.HALO_SCALE in 1.05f..1.30f
        )
        assertTrue(
            "cekirdek yaricapi ${FxOutline.CORE_RADIUS_FRAC} - sprite'i ezer",
            FxOutline.CORE_RADIUS_FRAC in 0.08f..0.25f
        )
        assertTrue(
            "hale alfasi ${FxOutline.HALO_ALPHA_MUL}",
            FxOutline.HALO_ALPHA_MUL in 0.5f..1.0f
        )
    }
}
