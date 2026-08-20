package com.miniappfactory.frontlinedefender.biome

import com.miniappfactory.frontlinedefender.game.model.Biome
import com.miniappfactory.frontlinedefender.game.model.BiomeRecolor
import com.miniappfactory.frontlinedefender.game.model.BiomeVariants
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * =============================================================================
 * YOL / ZEMIN KONTRASTI — GERCEK HARITALAR UZERINDE OLCUM ARACI
 * =============================================================================
 *
 * `docs/VISUAL_AUDIT.md` P0-2'nin sayilarini URETEN arac. Normal test
 * kosusunda CALISMAZ (bkz. `BiomeContactSheetTool` ile ayni gerekce: dosya
 * sistemine ve depo disindaki kaynak PNG'lere bagimli).
 *
 * -----------------------------------------------------------------------------
 * DENETIMIN SAYILARI NEDEN YENIDEN URETILEMEDI — VE NEDEN URETILMEMELI
 * -----------------------------------------------------------------------------
 * Denetim 3.1 girdisini `docs/biome_previews_kotlin/` altindaki PNG dosyalari
 * olarak veriyor. O dosyalar OLCULDU: **300x168**, yani 1672x941 kaynagin 5,6
 * kati kucultulmus hali (`BiomeContactSheetTool` kontak sayfasini `cellW = 300`
 * ile uretiyor).
 * Bu olcekte 30 px'lik bir yol ~5 px'e iner ve HER ornek yol ile zeminin
 * bilinear karisimidir. Karisim iki yuzeyi birbirine dogru cektigi icin olculen
 * oran sistematik olarak 1,0'a dogru cokuyor:
 *
 *   biyom     denetim (300 px onizleme)   bu arac (tam cozunurluk)
 *   ORIGINAL  1,59                        1,38
 *   NIGHT     2,23                        2,52
 *   WINTER    1,16                        2,04
 *   DESERT    1,07                        1,41
 *   AUTUMN    1,84                        1,63
 *
 * Denetimin BULGU SIRALAMASI dogruydu (col en kotu), MUTLAK sayilari degil.
 * Oyuncu arkaplani cihazda tam cozunurlukte gorur, 300 px'lik bir kucuk resimde
 * degil; dolayisiyla gecerli olcum bu aractakidir.
 *
 * -----------------------------------------------------------------------------
 * YONTEM — neden "sinir bandi", neden global ortalama degil
 * -----------------------------------------------------------------------------
 * Oyuncu yolu, yolun ORTASINA bakarak degil KENARINA bakarak okur. Iki genis
 * yuzeyin global ortalamasini karsilastirmak, aralarindaki gecisi hic gormeyen
 * bir olcumdur ve gercekte kaybolan kenari yakalayamaz. Bu yuzden:
 *
 *   1. YOL maskesi  = `docs/level_geometry/geometry.json` icindeki harita basina
 *      OLCULMUS HSV penceresi (`road.h_lo..v_hi`). Geometri cikarimi bu
 *      pencereyi zaten rota tespiti icin kullaniyor — ikinci bir dogruluk
 *      kaynagi uretilmedi.
 *   2. ZEMIN maskesi = URETIM kodunun kendi bitki ortusu olcutu
 *      (`VEG_HUE_MIN..MAX` + `VEG_SAT_MIN`), yani recolor'un gercekten
 *      donusturdugu piksel kumesi.
 *   3. SINIR BANDI = birbirine [BOUNDARY_PX] piksel mesafedeki yol ve zemin
 *      pikselleri. Kontrast yalnizca bu bantta olculur.
 *   4. Her piksel URETIM kodundan (`BiomeRecolor.apply`) gecirilir — maske,
 *      perde, kis kontrasti ve yol bandi kaydirmasi dahil.
 *
 * Iki eksen birden raporlanir; ikisi FARKLI seyler soyler:
 *   - **dE76** (CIE Lab) — asil olcut. Yol zeminden esas olarak HUE ile ayrisir.
 *   - **WCAG** — yalnizca parlaklik ekseni. Tek basina kullanilirsa yaniltir;
 *     kaniti ORIGINAL biyomdur (1,38 ve mukemmel oynanir).
 */
class BiomeGroundContrastTool {

    private companion object {
        /**
         * Sinir bandinin kalinligi. 16 px, 1672 px genisliginde bir haritada
         * yol genisliginin ~yarisi: bant yolun tamamini yutmadan kenarin iki
         * yanini da orneklemeye yetiyor. 4'e dusuruldugunde siralama
         * degismiyor, yalnizca gurultu artiyor.
         */
        const val BOUNDARY_PX = 16
    }

    @Test
    fun measureRoadGroundContrastOnEveryMap() {
        var root: File? = File(System.getProperty("user.dir")).absoluteFile
        while (root != null && !File(root, "docs/BIOME_VARIANTS.md").exists()) root = root.parentFile
        assumeTrue("proje koku bulunamadi", root != null)
        val projectRoot = root!!
        assumeTrue(
            "olcum araci — docs/.biome_contrast_request olusturularak calistirilir",
            File(projectRoot, "docs/.biome_contrast_request").exists()
        )

        val geometry = File(projectRoot, "docs/level_geometry/geometry.json")
        assumeTrue("geometry.json yok", geometry.exists())
        val roadWindows = parseRoadWindows(geometry.readText())

        val sources = buildList {
            File(projectRoot, "asset-pack/maps/level_01_battlefield_map.png")
                .let { if (it.exists()) add("m00" to it) }
            for (i in 1..10) {
                File(projectRoot, "copied items/map ($i).png")
                    .let { if (it.exists()) add(String.format("m%02d", i) to it) }
            }
        }
        assumeTrue("kaynak PNG bulunamadi", sources.isNotEmpty())

        val biomes = Biome.entries
        val wcag = biomes.associateWith { mutableListOf<Double>() }
        val de = biomes.associateWith { mutableListOf<Double>() }

        println()
        println("========== YOL / ZEMIN KONTRASTI (tam cozunurluk, sinir bandi) ==========")
        for ((tag, file) in sources) {
            val window = roadWindows[tag] ?: continue
            val src = ImageIO.read(file) ?: continue
            val w = src.width
            val h = src.height
            val base = IntArray(w * h)
            toArgb(src).getRGB(0, 0, w, h, base, 0, w)

            val roadMask = BooleanArray(w * h)
            val vegMask = BooleanArray(w * h)
            for (i in base.indices) {
                val hsv = BiomeRecolor.rgbToHsv(
                    (base[i] ushr 16) and 0xFF, (base[i] ushr 8) and 0xFF, base[i] and 0xFF
                )
                val hh = hsv ushr 16
                val ss = (hsv ushr 8) and 0xFF
                val vv = hsv and 0xFF
                if (window.contains(hh, ss, vv)) {
                    roadMask[i] = true
                } else if (ss >= BiomeVariants.VEG_SAT_MIN &&
                    hh in BiomeVariants.VEG_HUE_MIN..BiomeVariants.VEG_HUE_MAX
                ) {
                    vegMask[i] = true
                }
            }
            val roadBand = boundary(roadMask, vegMask, w, h)
            val vegBand = boundary(vegMask, roadMask, w, h)
            if (roadBand.isEmpty() || vegBand.isEmpty()) continue

            val row = StringBuilder(tag.padEnd(5))
            for (biome in biomes) {
                val px = base.copyOf()
                BiomeRecolor.apply(biome, px, w, h)
                val roadRgb = meanRgb(px, roadBand)
                val vegRgb = meanRgb(px, vegBand)
                val c = contrastRatio(roadRgb, vegRgb)
                val d = deltaE(roadRgb, vegRgb)
                wcag.getValue(biome) += c
                de.getValue(biome) += d
                row.append(String.format("  %s:%.2f/%.0f", biome.name.take(3), c, d))
            }
            println(row)
        }

        println()
        println(String.format("%-9s | %-16s | %-16s | %s", "biyom", "WCAG ort/en kotu", "dE76 ort/en kotu", "dE %% ORIGINAL"))
        println("-".repeat(72))
        val originalDe = de.getValue(Biome.ORIGINAL).average()
        for (biome in biomes) {
            val c = wcag.getValue(biome)
            val d = de.getValue(biome)
            println(
                String.format(
                    "%-9s | %6.2f / %-7.2f | %6.1f / %-7.1f | %5.0f%%",
                    biome, c.average(), c.min(), d.average(), d.min(),
                    100.0 * d.average() / originalDe
                )
            )
        }
        println("=".repeat(72))
    }

    // =====================================================================
    // Maske ve bant
    // =====================================================================

    private class RoadWindow(
        val hLo: Int, val hHi: Int, val sLo: Int, val sHi: Int, val vLo: Int, val vHi: Int
    ) {
        fun contains(h: Int, s: Int, v: Int) = h in hLo..hHi && s in sLo..sHi && v in vLo..vHi
    }

    /**
     * `geometry.json` icinden harita basina yol HSV penceresini cikarir.
     *
     * Elle ayristiriliyor cunku birim test siniftaki tek JSON kutuphanesi
     * Robolectric'in `org.json`'u ve onu bu arac icin ayaga kaldirmak testi
     * gereksizce Robolectric'e baglardi. Aranan sekil sabit ve dar:
     * `"mNN": { ... "road": { "h_lo": N, ... "v_hi": N } ... }`.
     */
    private fun parseRoadWindows(json: String): Map<String, RoadWindow> = buildMap {
        val mapKey = Regex("\"(m\\d\\d)\"\\s*:")
        val roadBlock = Regex("\"road\"\\s*:\\s*\\{([^}]*)\\}")
        val field = Regex("\"([hsv]_(?:lo|hi))\"\\s*:\\s*(-?\\d+)")
        for (m in mapKey.findAll(json)) {
            val rest = json.substring(m.range.last)
            val block = roadBlock.find(rest) ?: continue
            val f = field.findAll(block.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2].toInt() }
            put(
                m.groupValues[1],
                RoadWindow(
                    f["h_lo"] ?: continue, f["h_hi"] ?: continue,
                    f["s_lo"] ?: continue, f["s_hi"] ?: continue,
                    f["v_lo"] ?: continue, f["v_hi"] ?: continue
                )
            )
        }
    }

    /** [self] icinde, [other]'a en fazla [BOUNDARY_PX] uzaklikta olan pikseller. */
    private fun boundary(self: BooleanArray, other: BooleanArray, w: Int, h: Int): IntArray {
        var cur = other.copyOf()
        var next = BooleanArray(w * h)
        repeat(BOUNDARY_PX) {
            java.util.Arrays.fill(next, false)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    if (cur[i]) { next[i] = true; continue }
                    var hit = false
                    var dy = -1
                    while (dy <= 1 && !hit) {
                        val ny = y + dy
                        if (ny in 0 until h) {
                            for (dx in -1..1) {
                                val nx = x + dx
                                if (nx in 0 until w && cur[ny * w + nx]) { hit = true; break }
                            }
                        }
                        dy++
                    }
                    next[i] = hit
                }
            }
            val t = cur; cur = next; next = t
        }
        val out = ArrayList<Int>()
        for (i in self.indices) if (self[i] && cur[i]) out += i
        return out.toIntArray()
    }

    private fun meanRgb(px: IntArray, idx: IntArray): DoubleArray {
        var r = 0.0; var g = 0.0; var b = 0.0
        for (i in idx) {
            r += (px[i] ushr 16) and 0xFF
            g += (px[i] ushr 8) and 0xFF
            b += px[i] and 0xFF
        }
        return doubleArrayOf(r / idx.size, g / idx.size, b / idx.size)
    }

    // =====================================================================
    // Renk bilimi — BiomeReadabilityTest ile ayni formuller
    // =====================================================================

    private fun srgbToLinear(c: Double): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: DoubleArray): Double =
        0.2126 * srgbToLinear(c[0]) + 0.7152 * srgbToLinear(c[1]) + 0.0722 * srgbToLinear(c[2])

    private fun contrastRatio(a: DoubleArray, b: DoubleArray): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun toLab(c: DoubleArray): DoubleArray {
        val r = srgbToLinear(c[0])
        val g = srgbToLinear(c[1])
        val b = srgbToLinear(c[2])
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = (0.2126 * r + 0.7152 * g + 0.0722 * b)
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else (7.787 * t + 16.0 / 116.0)
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    private fun deltaE(a: DoubleArray, b: DoubleArray): Double {
        val la = toLab(a)
        val lb = toLab(b)
        return sqrt((la[0] - lb[0]).pow(2) + (la[1] - lb[1]).pow(2) + (la[2] - lb[2]).pow(2))
    }

    private fun toArgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_ARGB) return src
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return out
    }
}
