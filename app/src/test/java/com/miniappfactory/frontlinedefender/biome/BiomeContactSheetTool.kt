package com.miniappfactory.frontlinedefender.biome

import com.miniappfactory.frontlinedefender.game.model.Biome
import com.miniappfactory.frontlinedefender.game.model.BiomeRecolor
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * =============================================================================
 * GOZLE DOGRULAMA ARACI — normal test kosusunda CALISMAZ
 * =============================================================================
 *
 * `docs/BIOME_VARIANTS.md` 8. bolum acik maddesi:
 *   "[ ] Kotlin ciktisi Python onizlemesiyle gozle karsilastirilacak"
 *
 * Bu makinede Python KURULU DEGIL, yani referans betik yeniden calistirilamiyor.
 * Bunun yerine bu arac, ayni 11 kaynak PNG uzerinde **URETIM KODUNU**
 * (`BiomeRecolor.apply`) calistirip Python'unkiyle ayni duzende bir kontak
 * sayfasi uretir: 11 satir (harita) x 5 kolon (orijinal | gece | kis | col |
 * sonbahar). Iki sayfa yan yana konularak Kotlin HSV implementasyonunun
 * referanstan sapip sapmadigi gozle gorulur.
 *
 * NEDEN TEST DEGIL: dosya sistemine yazar ve depo disindaki kaynak PNG'lere
 * bagimlidir. Tetikleyici dosya yoksa kendini atlar, yani normal
 * `testDebugUnitTest` kosusunu ne yavaslatir ne de depoya dosya yazar.
 *
 * NEDEN ORTAM DEGISKENI / SISTEM OZELLIGI DEGIL: Gradle'in test JVM'i ne
 * `-D` ozelliklerini ne de ortam degiskenlerini varsayilan olarak devralir;
 * bunu duzeltmek `app/build.gradle.kts`'e dokunmayi gerektirirdi ve o dosya
 * bu gorevin kapsami disinda. Tetikleyici dosya build betigini degistirmeden
 * calisir.
 *
 * CALISTIRMA
 *   touch "docs/.biome_preview_request"
 *   ./gradlew.bat testDebugUnitTest --tests "*BiomeContactSheetTool*" --rerun-tasks
 *   rm "docs/.biome_preview_request"
 *
 * FARK NOTU: Python onizlemesi once 640 px'e kucultup sonra donusturur;
 * bu arac URETIMDEKI gibi TAM COZUNURLUKTE donusturup sonra kucultur. Maske
 * bulanikligi mutlak px cinsinden oldugu icin Python sayfasindaki kenar
 * yumusakligi oransal olarak daha genistir. Renkler karsilastirilabilir,
 * kenar yumusakligi birebir degildir.
 */
class BiomeContactSheetTool {

    private val biomeOrder = listOf(
        Biome.ORIGINAL, Biome.NIGHT, Biome.WINTER, Biome.DESERT, Biome.AUTUMN
    )

    @Test
    fun renderContactSheet() {
        // Gradle test JVM'inde `user.dir` MODUL dizinidir (.../source/app),
        // proje koku degil. Koku, plan dokumanini arayarak yukari dogru bul.
        var root: File? = File(System.getProperty("user.dir")).absoluteFile
        while (root != null && !File(root, "docs/BIOME_VARIANTS.md").exists()) root = root.parentFile
        assumeTrue("proje koku bulunamadi (user.dir=${System.getProperty("user.dir")})", root != null)
        val projectRoot = root!!
        assumeTrue(
            "gozle dogrulama araci — docs/.biome_preview_request olusturularak calistirilir",
            File(projectRoot, "docs/.biome_preview_request").exists()
        )

        val sources = buildList {
            File(projectRoot, "asset-pack/maps/level_01_battlefield_map.png").let { if (it.exists()) add(it) }
            for (i in 1..10) {
                File(projectRoot, "copied items/map ($i).png").let { if (it.exists()) add(it) }
            }
        }
        assumeTrue("kaynak PNG bulunamadi (kok: $projectRoot)", sources.isNotEmpty())

        val outDir = File(projectRoot, "docs/biome_previews_kotlin").apply { mkdirs() }
        val cellW = 300
        var cellH = 0
        val rows = mutableListOf<List<BufferedImage>>()

        for (src in sources) {
            val full = ImageIO.read(src) ?: continue
            val variants = biomeOrder.map { biome ->
                val work = toArgb(full)
                val px = IntArray(work.width * work.height)
                work.getRGB(0, 0, work.width, work.height, px, 0, work.width)
                val t0 = System.nanoTime()
                BiomeRecolor.apply(biome, px, work.width, work.height)
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                work.setRGB(0, 0, work.width, work.height, px, 0, work.width)
                println(String.format("%-28s %-9s %5dx%-5d %7.1f ms", src.name, biome, work.width, work.height, ms))
                val cell = scale(work, cellW)
                ImageIO.write(cell, "png", File(outDir, "${src.nameWithoutExtension}_${biome}.png".replace(' ', '_')))
                cell
            }
            cellH = variants[0].height
            rows += variants
        }

        val sheet = BufferedImage(cellW * biomeOrder.size, cellH * rows.size, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        for ((r, row) in rows.withIndex()) {
            for ((c, img) in row.withIndex()) g.drawImage(img, c * cellW, r * cellH, null)
        }
        g.dispose()
        val sheetFile = File(outDir, "00_KONTAK_SAYFASI_KOTLIN.png")
        ImageIO.write(sheet, "png", sheetFile)
        println("kontak sayfasi: ${sheetFile.absolutePath} (${sheet.width}x${sheet.height})")
        println("kolon sirasi: ${biomeOrder.joinToString(" | ")}")

        compareAgainstPythonReference(projectRoot, outDir)
    }

    /**
     * REFERANSLA SAYISAL KARSILASTIRMA.
     *
     * Goz yanilir; ozellikle iki goruntu farkli olcekte ve biri JPEG ise.
     * Bu blok her biyom icin **orijinale gore ortalama luminans orani**
     * uretir ve Python onizlemesiyle Kotlin ciktisini yan yana koyar. Oran
     * olcekten ve JPEG sikistirmasindan bagimsizdir, o yuzden iki
     * implementasyonun ayni seyi yapip yapmadigini gosteren dogru olcut budur.
     */
    private fun compareAgainstPythonReference(projectRoot: File, kotlinDir: File) {
        val refDir = File(projectRoot, "docs/biome_previews")
        if (!refDir.isDirectory) return
        val refNames = mapOf(
            Biome.NIGHT to "gece", Biome.WINTER to "kis",
            Biome.DESERT to "col", Biome.AUTUMN to "sonbahar"
        )

        fun meanLuma(f: File): Double? {
            val im = runCatching { ImageIO.read(f) }.getOrNull() ?: return null
            var sum = 0.0
            for (y in 0 until im.height) for (x in 0 until im.width) {
                val c = im.getRGB(x, y)
                sum += 0.2126 * ((c ushr 16) and 0xFF) +
                    0.7152 * ((c ushr 8) and 0xFF) + 0.0722 * (c and 0xFF)
            }
            return sum / (im.width * im.height)
        }

        val refBase = meanLuma(File(refDir, "m00_orijinal.jpg"))
            ?: meanLuma(File(refDir, "m00_orijinal.png")) ?: return
        val kotBase = meanLuma(File(kotlinDir, "level_01_battlefield_map_ORIGINAL.png")) ?: return

        println()
        println("=== REFERANS KARSILASTIRMASI (orijinale gore ort. luminans orani) ===")
        println(String.format("%-9s | %-14s | %-14s | %s", "biyom", "Python ref", "Kotlin", "fark"))
        for ((biome, tr) in refNames) {
            val ref = meanLuma(File(refDir, "m00_$tr.jpg")) ?: meanLuma(File(refDir, "m00_$tr.png"))
            val kot = meanLuma(File(kotlinDir, "level_01_battlefield_map_$biome.png"))
            if (ref == null || kot == null) continue
            val rRef = ref / refBase
            val rKot = kot / kotBase
            println(
                String.format(
                    "%-9s | %-14.3f | %-14.3f | %+.1f%%",
                    biome, rRef, rKot, 100 * (rKot - rRef) / rRef
                )
            )
        }
    }

    private fun toArgb(src: BufferedImage): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return out
    }

    private fun scale(src: BufferedImage, width: Int): BufferedImage {
        val height = (width.toLong() * src.height / src.width).toInt()
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, width, height, null)
        g.dispose()
        return out
    }
}
