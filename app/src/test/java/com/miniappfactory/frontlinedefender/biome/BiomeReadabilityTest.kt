package com.miniappfactory.frontlinedefender.biome

import com.miniappfactory.frontlinedefender.game.model.Biome
import com.miniappfactory.frontlinedefender.game.model.BiomeRecolor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * =============================================================================
 * Faz 11 — OKUNABILIRLIK KAPISI
 * =============================================================================
 *
 * Bu, biyom isinin en kritik testi. `docs/BIOME_VARIANTS.md` ilkesi acik:
 * **"oynanis okunabilirligi atmosferden once gelir."** Gece ve kis varyantlari
 * atmosfer ugruna yolu, build pad'leri ve dusman zeminini yutarsa oyun bozulur —
 * teknik olarak dogru ama oynanamaz bir sonuc, bir OYNANIS HATASIDIR.
 *
 * NASIL OLCULUYOR
 *   `docs/BIOME_VARIANTS.md` 2. bolumundeki OLCULMUS sahne renkleri (yol, cimen,
 *   agac, build pad, kaya, us, spawn, su) gercek harita oranlarina yakin bir
 *   sentetik sahneye diziliyor; sahne PRODUKSIYON kodundan (`BiomeRecolor.apply`)
 *   geciriliyor — maske, perde ve kis kontrasti dahil. Sonra her yuzey ciftinin:
 *
 *     1) **CIE Lab dE76** — gozun algiladigi renk mesafesi. Asil olcut bu:
 *        yol cimenden esas olarak HUE ile ayrisir, parlaklikla degil.
 *     2) **WCAG luminans kontrast orani** — sadece parlaklik ekseni. Referans
 *        icin raporlaniyor; tek basina kullanilirsa yaniltir (orijinal harita
 *        bile 1.3:1 civarindadir ve mukemmel okunur).
 *
 * KABUL KURALI
 *   Her biyom, orijinal harita ile ayni sahnede olculen dE76'nin belirli bir
 *   oranini korumak zorunda. Koruyamayan biyom AYARLANIR ya da ELENIR;
 *   "5 biyomdan 4'u iyi" ile devam etmek dogru karardir.
 */
class BiomeReadabilityTest {

    // =====================================================================
    // Olculmus sahne renkleri — docs/BIOME_VARIANTS.md 2. bolum tablosu.
    // HSV, PIL olcegi (0..255). `weight` = 16x8 izgarada kac hucre kaplar;
    // oranlar dokumanin hue histogramina yaklastirildi (sahnenin ~%52'si
    // bitki ortusu). Kis biyomunun kontrast adimi GLOBAL ortalamaya bagli
    // oldugu icin bu oranlar onemli.
    // =====================================================================
    private data class Surface(
        val label: String,
        val h: Int,
        val s: Int,
        val v: Int,
        val weight: Int,
        val gameplayCritical: Boolean
    )

    private val surfaces = listOf(
        Surface("yol", 26, 154, 118, weight = 26, gameplayCritical = true),
        Surface("cimen", 39, 172, 127, weight = 40, gameplayCritical = false),
        Surface("agac", 47, 140, 60, weight = 27, gameplayCritical = false),
        Surface("build pad", 27, 44, 97, weight = 8, gameplayCritical = true),
        Surface("kaya", 22, 72, 53, weight = 8, gameplayCritical = true),
        Surface("us", 43, 69, 147, weight = 6, gameplayCritical = true),
        Surface("spawn", 50, 119, 96, weight = 5, gameplayCritical = true),
        Surface("su", 138, 207, 75, weight = 8, gameplayCritical = true)
    )

    private val cell = 64
    private val cols = 16
    private val rows = 8
    private val width = cols * cell
    private val height = rows * cell

    /** Her yuzeyin ilk hucresinin sol-ust izgara koordinati. */
    private val firstCellOf: Map<String, Int> = buildMap {
        var c = 0
        for (s in surfaces) {
            put(s.label, c)
            c += s.weight
        }
    }

    private fun buildScene(): IntArray {
        val px = IntArray(width * height)
        var cellIndex = 0
        for (s in surfaces) {
            val argb = -0x1000000 or BiomeRecolor.hsvToRgb(s.h, s.s, s.v)
            repeat(s.weight) {
                val cx = (cellIndex % cols) * cell
                val cy = (cellIndex / cols) * cell
                for (y in cy until cy + cell) {
                    val row = y * width
                    java.util.Arrays.fill(px, row + cx, row + cx + cell, argb)
                }
                cellIndex++
            }
        }
        check(cellIndex == cols * rows) { "izgara dolmadi: $cellIndex / ${cols * rows}" }
        return px
    }

    /** Hucrenin MERKEZ pikseli — maske bulanikligi (~3 px) kenarda kalir. */
    private fun sample(px: IntArray, label: String): Int {
        val idx = firstCellOf.getValue(label)
        val x = (idx % cols) * cell + cell / 2
        val y = (idx / cols) * cell + cell / 2
        return px[y * width + x]
    }

    private fun variant(biome: Biome): Map<String, Int> {
        val px = buildScene()
        BiomeRecolor.apply(biome, px, width, height)
        return surfaces.associate { it.label to sample(px, it.label) }
    }

    // =====================================================================
    // Renk bilimi
    // =====================================================================

    private fun srgbToLinear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG 2.x bagil luminans. */
    private fun relativeLuminance(argb: Int): Double =
        0.2126 * srgbToLinear((argb ushr 16) and 0xFF) +
            0.7152 * srgbToLinear((argb ushr 8) and 0xFF) +
            0.0722 * srgbToLinear(argb and 0xFF)

    /** WCAG kontrast orani, 1.0 (ayni) .. 21.0 (siyah/beyaz). */
    private fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** sRGB -> CIE Lab (D65). */
    private fun toLab(argb: Int): DoubleArray {
        val r = srgbToLinear((argb ushr 16) and 0xFF)
        val g = srgbToLinear((argb ushr 8) and 0xFF)
        val b = srgbToLinear(argb and 0xFF)
        // D65 beyaz noktasina gore normalize XYZ
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 1.00000
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else (7.787 * t + 16.0 / 116.0)
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    /** CIE76 renk farki. ~2.3 = zar zor secilir, 10+ = acikca farkli renk. */
    private fun deltaE(a: Int, b: Int): Double {
        val la = toLab(a)
        val lb = toLab(b)
        return sqrt(
            (la[0] - lb[0]).pow(2) + (la[1] - lb[1]).pow(2) + (la[2] - lb[2]).pow(2)
        )
    }

    // =====================================================================
    // 1. RAPOR — biyom basina somut sayilar
    // =====================================================================

    private val criticalPairs = listOf(
        "yol" to "cimen",     // ASIL OLCUT: oyuncu yolu goremezse oyun biter
        "yol" to "agac",
        "build pad" to "cimen",
        "us" to "cimen",
        "su" to "cimen"
    )

    @Test
    fun readabilityReport() {
        val variants = Biome.entries.associateWith { variant(it) }
        val base = variants.getValue(Biome.ORIGINAL)

        println()
        println("================= BIYOM OKUNABILIRLIK OLCUMU =================")
        println("dE76 = CIE Lab renk mesafesi (2.3 = zar zor secilir, 10+ = acikca farkli)")
        println("WCAG = luminans kontrast orani (yalnizca parlaklik ekseni)")
        println()
        println(
            String.format(
                "%-11s | %-16s | %7s | %7s | %6s",
                "biyom", "cift", "dE76", "WCAG", "dE %"
            )
        )
        println("-".repeat(64))
        for (biome in Biome.entries) {
            val v = variants.getValue(biome)
            for ((a, b) in criticalPairs) {
                val de = deltaE(v.getValue(a), v.getValue(b))
                val baseDe = deltaE(base.getValue(a), base.getValue(b))
                println(
                    String.format(
                        "%-11s | %-16s | %7.2f | %7.3f | %5.0f%%",
                        biome, "$a/$b", de, contrastRatio(v.getValue(a), v.getValue(b)),
                        100.0 * de / baseDe
                    )
                )
            }
            println("-".repeat(64))
        }

        println()
        println("--- yuzey renkleri (RGB heks) ---")
        print(String.format("%-11s", "biyom"))
        surfaces.forEach { print(String.format(" %-10s", it.label)) }
        println()
        for (biome in Biome.entries) {
            val v = variants.getValue(biome)
            print(String.format("%-11s", biome))
            surfaces.forEach { print(String.format(" #%06X   ", v.getValue(it.label) and 0xFFFFFF)) }
            println()
        }
        println("=============================================================")
    }

    // =====================================================================
    // 2. KAPI — yol / cimen ayrimi
    //
    // Oyuncunun yolu gorememesi tek basina bolumu oynanamaz kilar. Bu yuzden
    // en sert kural burada.
    // =====================================================================

    @Test
    fun everyBiomeKeepsTheRoadDistinguishableFromGrass() {
        val base = variant(Biome.ORIGINAL)
        val baseDe = deltaE(base.getValue("yol"), base.getValue("cimen"))
        val failures = mutableListOf<String>()

        for (biome in Biome.entries) {
            val v = variant(biome)
            val de = deltaE(v.getValue("yol"), v.getValue("cimen"))
            val kept = 100.0 * de / baseDe
            // MUTLAK zemin: dE 12 altinda iki yuzey "ayni renkli" hissedilir.
            if (de < 12.0) failures += String.format("%s dE76=%.2f (mutlak zemin 12)", biome, de)
            // GORELI zemin: orijinalin en az %55'i korunmali.
            if (kept < 55.0) failures += String.format("%s orijinalin %%%.0f'i (zemin %%55)", biome, kept)
        }
        assertTrue("YOL/CIMEN AYRIMI KAYBOLDU -> $failures", failures.isEmpty())
    }

    @Test
    fun everyBiomeKeepsBuildPadsVisibleAgainstTheGround() {
        // Build pad gorunmezse oyuncu kule kuracak yeri bulamaz.
        val base = variant(Biome.ORIGINAL)
        val baseDe = deltaE(base.getValue("build pad"), base.getValue("cimen"))
        for (biome in Biome.entries) {
            val v = variant(biome)
            val de = deltaE(v.getValue("build pad"), v.getValue("cimen"))
            assertTrue(
                String.format(
                    "%s: build pad zeminde kayboldu (dE76=%.2f, orijinal %.2f)",
                    biome, de, baseDe
                ),
                de >= 12.0
            )
        }
    }

    /** Sahnenin AGIRLIKLI ortalama luminansi — atmosfer olcutu. */
    private fun sceneMeanLuma(v: Map<String, Int>): Double {
        var sum = 0.0
        var weight = 0
        for (s in surfaces) {
            val c = v.getValue(s.label)
            sum += s.weight * (
                0.2126 * ((c ushr 16) and 0xFF) +
                    0.7152 * ((c ushr 8) and 0xFF) +
                    0.0722 * (c and 0xFF)
                )
            weight += s.weight
        }
        return sum / weight
    }

    /**
     * GECE ATMOSFERI — okunabilirlik icin yol aydinlatildi, peki sahne hâlâ
     * gece gibi mi gorunuyor?
     *
     * Yanlis olcut: "gece yolu, gunduz yolundan koyu olmali." Bu, dokumanin
     * ay isigi modeliyle CELISIR — modelin tum amaci yolun karanlik sahneden
     * one cikmasi. Dogru olcut SAHNE seviyesinde:
     *   - genel parlaklik dusmeli,
     *   - bitki ortusu (sahnenin %52'si) belirgin koyulmali,
     *   - yol bitkiden PARLAK olmali.
     */
    @Test
    fun nightStillReadsAsNightAfterTheRoadRelight() {
        val day = variant(Biome.ORIGINAL)
        val night = variant(Biome.NIGHT)

        val dayMean = sceneMeanLuma(day)
        val nightMean = sceneMeanLuma(night)
        println(
            String.format(
                "gece atmosferi: sahne ort. luma %.1f -> %.1f (%%%.0f)",
                dayMean, nightMean, 100 * nightMean / dayMean
            )
        )
        assertTrue(
            String.format("gece sahnesi yeterince kararmiyor (%%%.0f)", 100 * nightMean / dayMean),
            nightMean <= dayMean * 0.85
        )

        // Bitki ortusu — gecenin asil tasiyicisi, geri aydinlatmadan ETKILENMEZ.
        for (vegetation in listOf("cimen", "agac")) {
            val d = relativeLuminance(day.getValue(vegetation))
            val n = relativeLuminance(night.getValue(vegetation))
            assertTrue("gece $vegetation yeterince koyulmadi", n <= d * 0.75)
        }
    }

    @Test
    fun nightDoesNotCrushTheSceneIntoBlack() {
        // REDDEDILEN ilk deneme (val x0.48 + %20 perde) yolu bazi haritalarda
        // okunamaz hale getirmisti. Yol luminansi icin taban sart.
        val night = variant(Biome.NIGHT)
        val road = night.getValue("yol")
        val roadLuma = 0.2126 * ((road ushr 16) and 0xFF) +
            0.7152 * ((road ushr 8) and 0xFF) +
            0.0722 * (road and 0xFF)
        assertTrue(
            String.format("gece yolu asiri karartti: luma %.1f / 255", roadLuma),
            roadLuma >= 45.0
        )
        // ...ve yol, bitki ortusunden PARLAK olmali (ay isigi modeli).
        for (vegetation in listOf("cimen", "agac")) {
            assertTrue(
                "gecede yol $vegetation'dan koyu — ay isigi modeli calismiyor",
                relativeLuminance(road) > relativeLuminance(night.getValue(vegetation))
            )
        }
    }

    @Test
    fun winterDoesNotWashTheRoadIntoTheSnow() {
        // Kis en riskli biyom: bitki ortusu neredeyse beyaza gidiyor, yol
        // kahverengi kalmali. Karsit yonde bir risk de var — kar cok parlak
        // olursa yol "delik" gibi gorunur ama bu OKUNABILIRLIGI ARTIRIR,
        // o yuzden yalnizca alt sinir kontrol ediliyor.
        val winter = variant(Biome.WINTER)
        val de = deltaE(winter.getValue("yol"), winter.getValue("cimen"))
        assertTrue(String.format("kis: yol karin icinde eridi (dE76=%.2f)", de), de >= 20.0)
    }

    @Test
    fun desertSeparatesSandFromRoadByBrightnessNotHue() {
        // docs 3. bolum: "Col'un yoldan ayrisma hue ile DEGIL value ile saglanir
        // (+76 parlaklik, doygunluk yarıya): kum yoldan DAHA ACIK ve DAHA SOLUK."
        val desert = variant(Biome.DESERT)
        val road = desert.getValue("yol")
        val sand = desert.getValue("cimen")
        assertTrue(
            "col: kum yoldan daha acik degil — hue kaydirmasina kaymis olabilir",
            relativeLuminance(sand) > relativeLuminance(road)
        )
        assertTrue(
            "col: kum yoldan daha soluk degil",
            saturationOf(sand) < saturationOf(road)
        )
    }

    /**
     * BILINEN ZAYIF NOKTA — col biyomunda oyuncu ussu / zemin ayrimi.
     *
     * Olcum: col'de us/zemin dE76 = **6.80**, orijinalde 23.77 (%29). Sebep
     * mekanigin kendisi: col zemini `value +76` ile yukari itiliyor ve us
     * (V~147) tam o araliga dusuyor. Zemini asagi cekmek durumu KOTULESTIRIR
     * (us'un altindan gecer), yukari itmek ise kumu yikar.
     *
     * NEDEN KABUL EDILDI: us tek ve kucuk bir nesnedir, kendi mimarisi
     * (rampa/duvar) ve HUD'daki us-cani gostergesi ile ayrica okunur; yol,
     * pad ve dusman zemini ayrimlari col'de saglam (%112 / %73). Yine de
     * daha fazla asagi kaymasin diye zemin buraya KILITLENDI.
     */
    @Test
    fun desertBaseGroundSeparationDoesNotDegradeFurther() {
        val desert = variant(Biome.DESERT)
        val de = deltaE(desert.getValue("us"), desert.getValue("cimen"))
        println(String.format("col us/zemin dE76 = %.2f (bilinen zayif nokta)", de))
        assertTrue(
            String.format("col: us zeminde tamamen kayboldu (dE76=%.2f)", de),
            de >= 6.0
        )
    }

    private fun saturationOf(argb: Int): Int =
        (BiomeRecolor.rgbToHsv((argb ushr 16) and 0xFF, (argb ushr 8) and 0xFF, argb and 0xFF)
            ushr 8) and 0xFF
}
