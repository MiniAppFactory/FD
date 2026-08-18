package com.miniappfactory.frontlinedefender.biome

import com.miniappfactory.frontlinedefender.game.model.Biome
import com.miniappfactory.frontlinedefender.game.model.BiomeRecolor
import com.miniappfactory.frontlinedefender.game.model.BiomeVariants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Faz 11 — PIKSEL GECISI sozlesmesi.
 *
 * Bu dosyanin varlik sebebi tek bir risk: **HSV olcegi**. Referans
 * (`docs/tools/biome_preview.py`) PIL kullaniyor ve PIL'de H, S, V hepsi
 * 0..255. Ayni parametreler 0..360'lik bir HSV implementasyonuna verilirse
 * kis biyomu kar yerine mor uretir ve kimse derleme hatasi gormez.
 *
 * Python bu makinede KURULU DEGIL, yani Kotlin ciktisini betikle otomatik
 * karsilastiramiyoruz. Bunun yerine PIL'in belgelenmis davranisi burada
 * dogrudan sabitlendi (LUT semantigi, hue sarmasi, gidis-donus tutarliligi).
 */
class BiomeRecolorTest {

    // =====================================================================
    // 1. HSV donusumu — PIL olcegi (0..255 x 3)
    // =====================================================================

    @Test
    fun hsvUsesThe0To255ScaleOnAllThreeChannels() {
        // Saf kirmizi: H = 0, S = 255, V = 255
        assertEquals(0, BiomeRecolor.rgbToHsv(255, 0, 0) ushr 16)
        assertEquals(255, (BiomeRecolor.rgbToHsv(255, 0, 0) ushr 8) and 0xFF)
        assertEquals(255, BiomeRecolor.rgbToHsv(255, 0, 0) and 0xFF)

        // Saf yesil: 120 derece -> 0..255 olceginde 255/3 = 85
        assertEquals(85, BiomeRecolor.rgbToHsv(0, 255, 0) ushr 16)
        // Saf mavi: 240 derece -> 170
        assertEquals(170, BiomeRecolor.rgbToHsv(0, 0, 255) ushr 16)

        // Gri: doygunluk 0, hue tanimsiz -> 0
        val gray = BiomeRecolor.rgbToHsv(128, 128, 128)
        assertEquals(0, gray ushr 16)
        assertEquals(0, (gray ushr 8) and 0xFF)
        assertEquals(128, gray and 0xFF)
    }

    @Test
    fun measuredSceneColoursLandInTheMeasuredHueBands() {
        // docs/BIOME_VARIANTS.md 2. bolum tablosu — olculmus degerler.
        // Bu test, HSV implementasyonunun o olcumlerle AYNI dunyada oldugunu
        // kanitlar: yol 24-28, cimen 37-42 bandina dusmeli.
        val road = BiomeRecolor.hsvToRgb(26, 154, 118)
        val grass = BiomeRecolor.hsvToRgb(39, 172, 127)
        val roadBack = BiomeRecolor.rgbToHsv((road ushr 16) and 0xFF, (road ushr 8) and 0xFF, road and 0xFF)
        val grassBack = BiomeRecolor.rgbToHsv((grass ushr 16) and 0xFF, (grass ushr 8) and 0xFF, grass and 0xFF)

        assertTrue("yol hue bandi disina cikti: ${roadBack ushr 16}", (roadBack ushr 16) in 24..28)
        assertTrue("cimen hue bandi disina cikti: ${grassBack ushr 16}", (grassBack ushr 16) in 37..42)
    }

    @Test
    fun roundTripStaysWithinHueQuantisationLimit() {
        // 8-bit HSV kayiplidir; sozlesme "kayipsiz" degil, "gorunmez kayipli".
        //
        // TAVAN NEREDEN GELIYOR: hue 256 adima kuantalanir, yani bir adim
        // 360/256 = 1.41 derece. Tam doygun bir renkte (chroma 255) 1.41
        // derecelik hue hatasi bir kanali 255 * 1.41/60 = **6.0 birim**
        // kaydirir. Yani 6, implementasyon hatasi degil ARITMETIK TAVANDIR;
        // PIL'in kendi HSV modu da ayni tavana sahiptir. 6'nin USTU gercek
        // bir hatadir.
        var worst = 0
        for (r in 0..255 step 7) for (g in 0..255 step 11) for (b in 0..255 step 13) {
            val hsv = BiomeRecolor.rgbToHsv(r, g, b)
            val rgb = BiomeRecolor.hsvToRgb(hsv ushr 16, (hsv ushr 8) and 0xFF, hsv and 0xFF)
            worst = maxOf(
                worst,
                abs(((rgb ushr 16) and 0xFF) - r),
                abs(((rgb ushr 8) and 0xFF) - g),
                abs((rgb and 0xFF) - b)
            )
        }
        assertTrue("HSV gidis-donus sapmasi cok buyuk: $worst", worst <= 6)
    }

    // =====================================================================
    // 2. LUT semantigi — PIL `Image.point()` birebir
    // =====================================================================

    @Test
    fun hueLutWrapsAroundTheColourWheelInBothDirections() {
        // PIL: [(i + d) & 255 for i in range(256)]
        assertEquals(0, BiomeRecolor.hueLut(118)[138])   // 138 + 118 = 256 -> 0
        assertEquals(250, BiomeRecolor.hueLut(-6)[0])    // col: negatif sarar
        assertEquals(232, BiomeRecolor.hueLut(-24)[0])   // sonbahar
        for (i in 0..255) assertEquals(i, BiomeRecolor.hueLut(0)[i])
    }

    @Test
    fun channelLutTruncatesAndClampsLikePil() {
        // PIL: max(0, min(255, int(i * k + add))) — int() KESER, yuvarlamaz.
        val kis = BiomeRecolor.channelLut(0.12f, 0f)
        assertEquals(0, kis[7])          // 0.84 -> 0
        assertEquals(30, kis[255])       // 30.6 -> 30
        val kar = BiomeRecolor.channelLut(0.58f, 126f)
        assertEquals(126, kar[0])
        assertEquals(255, kar[255])      // 273.9 -> kirpilir
        val col = BiomeRecolor.channelLut(0.72f, 76f)
        assertEquals(255, col[249])      // tasma kirpiliyor
        assertTrue(BiomeRecolor.channelLut(1.05f, 14f).all { it in 0..255 })
    }

    // =====================================================================
    // 3. Bitki maskesi — OYNANIS OKUNABILIRLIGININ KILIDI
    //
    // Maske yanlissa yol da yeniden renklenir ve oyun okunamaz hale gelir.
    // =====================================================================

    private fun maskValueFor(h: Int, s: Int, v: Int): Int =
        maskValueOf(-0x1000000 or BiomeRecolor.hsvToRgb(h, s, v))

    private fun maskValueOf(argb: Int): Int {
        // Tek renkli genis bir alan: bulaniklik merkez pikseli etkilemesin.
        val w = 16
        val px = IntArray(w * w) { argb }
        val mask = BiomeRecolor.buildVegetationMask(px, w, w)
        return mask[w / 2 * w + w / 2].toInt() and 0xFF
    }

    @Test
    fun maskSelectsVegetationOnly() {
        // Bandin ICINDE (docs 2. bolum "hedef" satirlari)
        assertEquals("cimen maskede olmali", 255, maskValueFor(39, 172, 127))
        assertEquals("agac maskede olmali", 255, maskValueFor(47, 140, 60))
        assertEquals("spawn platformu (sinirda) maskede", 255, maskValueFor(50, 119, 96))
    }

    @Test
    fun maskExcludesEveryGameplayCriticalSurface() {
        // "korunmali" isaretli her sey maskenin DISINDA kalmali.
        assertEquals("YOL yeniden renklendi — oyun okunamaz", 0, maskValueFor(26, 154, 118))
        assertEquals("build pad korunmadi", 0, maskValueFor(27, 44, 97))
        assertEquals("kaya korunmadi", 0, maskValueFor(22, 72, 53))
        assertEquals("oyuncu ussu korunmadi", 0, maskValueFor(43, 69, 147))
        assertEquals("su korunmadi", 0, maskValueFor(138, 207, 75))
    }

    @Test
    fun maskBoundariesMatchTheMeasuredThresholds() {
        // Ic bandin ortasi ve disi — kuantalama sinirlarindan uzak noktalar.
        assertEquals(0, maskValueFor(BiomeVariants.VEG_HUE_MIN - 3, 200, 128))
        assertEquals(255, maskValueFor(BiomeVariants.VEG_HUE_MIN + 3, 200, 128))
        assertEquals(255, maskValueFor(BiomeVariants.VEG_HUE_MAX - 3, 200, 128))
        assertEquals(0, maskValueFor(BiomeVariants.VEG_HUE_MAX + 3, 200, 128))
        // Doygunluk esigi: hue bandin icinde ama S dusuk -> DOKUNULMAZ.
        assertEquals(0, maskValueFor(50, BiomeVariants.VEG_SAT_MIN - 6, 128))
        assertEquals(255, maskValueFor(50, BiomeVariants.VEG_SAT_MIN + 6, 128))
    }

    /**
     * Maskenin TAM sozlesmesi, tum hue x doygunluk uzayinda.
     *
     * Neden "istenen" degil "geri okunan" HSV ile karsilastiriliyor: 8-bit HSV
     * gidis-donusu hue'yu +-1 kaydirabilir, yani `hsvToRgb(34, ...)` uretilip
     * geri okundugunda 33 cikabilir. Maske gercek piksellerle calisir, o yuzden
     * dogru referans da geri okunan degerdir. Aksi halde test kendi olcegini
     * degil, kuantalama gurultusunu olcer.
     */
    @Test
    fun maskContractHoldsAcrossTheWholeHueSaturationSpace() {
        for (h in 0..255) {
            for (s in intArrayOf(40, 88, 96, 150, 230)) {
                val argb = BiomeRecolor.hsvToRgb(h, s, 140)
                val back = BiomeRecolor.rgbToHsv(
                    (argb ushr 16) and 0xFF, (argb ushr 8) and 0xFF, argb and 0xFF
                )
                val actualHue = back ushr 16
                val actualSat = (back ushr 8) and 0xFF
                val expected = if (actualHue in BiomeVariants.VEG_HUE_MIN..BiomeVariants.VEG_HUE_MAX &&
                    actualSat >= BiomeVariants.VEG_SAT_MIN
                ) 255 else 0
                assertEquals(
                    "h=$h s=$s (geri okunan h=$actualHue s=$actualSat)",
                    expected,
                    maskValueOf(argb)
                )
            }
        }
    }

    @Test
    fun maskEdgeIsSoftenedNotHard() {
        // Sol yari bitki, sag yari yol. Sinirda ara degerler olmali; sert
        // maske gozde "kesik" gorunur (docs 2. bolum).
        val w = 64
        val h = 16
        val veg = -0x1000000 or BiomeRecolor.hsvToRgb(39, 172, 127)
        val road = -0x1000000 or BiomeRecolor.hsvToRgb(26, 154, 118)
        val px = IntArray(w * h) { if (it % w < w / 2) veg else road }
        val mask = BiomeRecolor.buildVegetationMask(px, w, h)
        val row = (h / 2) * w
        val values = (0 until w).map { mask[row + it].toInt() and 0xFF }
        assertEquals("bitki ici tam maskeli", 255, values[4])
        assertEquals("yol ici tamamen disarida", 0, values[w - 4])
        assertTrue(
            "maske kenari yumusatilmamis (ara deger yok)",
            values.any { it in 1..254 }
        )
    }

    // =====================================================================
    // 4. Biyom uygulamasi
    // =====================================================================

    private fun scene(w: Int = 48, h: Int = 48, argb: Int) = IntArray(w * h) { argb }

    @Test
    fun originalBiomeTouchesNothing() {
        // Kimlik donusumu bile HSV gidis-donusu yuzunden pikselleri kaydirirdi;
        // ORIGINAL icin gecis HIC calismamali.
        val before = IntArray(64 * 64) { -0x1000000 or (it * 2654435761u.toInt() and 0xFFFFFF) }
        val after = before.copyOf()
        BiomeRecolor.apply(Biome.ORIGINAL, after, 64, 64)
        assertArrayEquals("ORIGINAL biyomu pikselleri degistirdi", before, after)
    }

    @Test
    fun everyNonOriginalBiomeActuallyChangesVegetation() {
        val grass = -0x1000000 or BiomeRecolor.hsvToRgb(39, 172, 127)
        for (biome in Biome.entries.filter { !it.isIdentity }) {
            val px = scene(argb = grass)
            BiomeRecolor.apply(biome, px, 48, 48)
            val center = px[48 * 24 + 24]
            assertTrue("$biome cimeni hic degistirmedi", center != grass)
            assertEquals("$biome alfa kanalini bozdu", -0x1000000, center and -0x1000000)
        }
    }

    @Test
    fun maskedBiomesLeaveTheRoadEssentiallyUntouched() {
        // Kis / col / sonbahar yola YALNIZCA ince perdeyi uygular. Perde
        // oranlari %7-9 oldugu icin sapma sinirli olmali; buyurse yolun
        // kimligi kaybolur.
        val road = -0x1000000 or BiomeRecolor.hsvToRgb(26, 154, 118)
        for (biome in listOf(Biome.WINTER, Biome.DESERT, Biome.AUTUMN)) {
            val px = scene(argb = road)
            BiomeRecolor.apply(biome, px, 48, 48)
            val c = px[48 * 24 + 24]
            val delta = maxOf(
                abs(((c ushr 16) and 0xFF) - ((road ushr 16) and 0xFF)),
                abs(((c ushr 8) and 0xFF) - ((road ushr 8) and 0xFF)),
                abs((c and 0xFF) - (road and 0xFF))
            )
            assertTrue("$biome yolu $delta birim kaydirdi — perde disinda etki var", delta <= 24)
        }
    }

    @Test
    fun nightIsTheOnlyBiomeThatTouchesPixelsOutsideTheMask() {
        // Maskeli biyomlar yola YALNIZCA perde uygular (yukaridaki test).
        // Gece ise maskeyi ters yonde de kullanir: yol/pad/kaya AYRICA geri
        // aydinlatilir, yani gece maskenin HER IKI tarafina da dokunan tek
        // biyomdur. Yolun degisim buyuklugu bunu kanitlar.
        val road = -0x1000000 or BiomeRecolor.hsvToRgb(26, 154, 118)
        val px = scene(argb = road)
        BiomeRecolor.apply(Biome.NIGHT, px, 48, 48)
        val c = px[48 * 24 + 24]
        val delta = maxOf(
            abs(((c ushr 16) and 0xFF) - ((road ushr 16) and 0xFF)),
            abs(((c ushr 8) and 0xFF) - ((road ushr 8) and 0xFF)),
            abs((c and 0xFF) - (road and 0xFF))
        )
        assertTrue("gece yola hic dokunmadi (delta=$delta)", delta > 24)
        // Ay isigi modeli: yol tamamen kararmaz.
        assertTrue(
            "gece yolu asiri karartti (luma ${luma(c)}) — okunabilirlik kaybi",
            luma(c) > luma(road) * 0.6f
        )
    }

    @Test
    fun nightKeepsVegetationDarkerThanTheRoad() {
        // Ay isigi modelinin tum amaci bu: yol parlar, bitki koyu kalir.
        val w = 64
        val h = 16
        val veg = -0x1000000 or BiomeRecolor.hsvToRgb(39, 172, 127)
        val road = -0x1000000 or BiomeRecolor.hsvToRgb(26, 154, 118)
        val px = IntArray(w * h) { if (it % w < w / 2) veg else road }
        BiomeRecolor.apply(Biome.NIGHT, px, w, h)
        val vegLuma = luma(px[(h / 2) * w + 4])
        val roadLuma = luma(px[(h / 2) * w + w - 4])
        assertTrue(
            "gecede bitki ($vegLuma) yoldan ($roadLuma) daha parlak — ay isigi modeli ters",
            roadLuma > vegLuma
        )
    }

    @Test
    fun transformIsDeterministic() {
        val seedPx = IntArray(96 * 96) { -0x1000000 or ((it * 2654435761u.toInt()) and 0xFFFFFF) }
        for (biome in Biome.entries) {
            val a = seedPx.copyOf()
            val b = seedPx.copyOf()
            BiomeRecolor.apply(biome, a, 96, 96)
            BiomeRecolor.apply(biome, b, 96, 96)
            assertArrayEquals("$biome deterministik degil", a, b)
        }
    }

    @Test
    fun alphaChannelSurvivesEveryBiome() {
        val px = IntArray(32 * 32) { 0x80_20_A0_40.toInt() }
        for (biome in Biome.entries) {
            val work = px.copyOf()
            BiomeRecolor.apply(biome, work, 32, 32)
            assertTrue("$biome alfa kanalini bozdu", work.all { (it ushr 24) and 0xFF == 0x80 })
        }
    }

    // =====================================================================
    // 5. Gecis SURESI — kanit
    //
    // Bu bir esik testi degil, OLCUM. Gercek harita boyutunda (1920x1081)
    // calisir ve rakami raporlar. CI makinesi degistiginde kirmizi yanmasin
    // diye siniri cok gevsek tutuyoruz; degerin kendisi rapora giriyor.
    // =====================================================================

    @Test
    fun measureFullSizeTransformCost() {
        val w = 1920
        val h = 1081
        val base = IntArray(w * h) { -0x1000000 or ((it * 2654435761u.toInt()) and 0xFFFFFF) }
        println("=== BIYOM GECIS SURESI (JVM, ${w}x$h = ${w * h} piksel) ===")
        for (biome in Biome.entries) {
            // isinma
            BiomeRecolor.apply(biome, base.copyOf(), w, h)
            val px = base.copyOf()
            val t0 = System.nanoTime()
            BiomeRecolor.apply(biome, px, w, h)
            val ms = (System.nanoTime() - t0) / 1_000_000.0
            println(String.format("  %-9s %7.1f ms", biome, ms))
            assertTrue("$biome gecisi $ms ms — bolum yuklemesi icin cok uzun", ms < 5000)
        }
    }

    /**
     * PARALEL BANTLAMA CIKTIYI DEGISTIRMEZ — bayt bayt ayni.
     *
     * `BiomeRecolor` gec-oyun bolum yuklemesinde 2 M pikseli donusturuyor ve
     * olcum NIGHT'in digerlerinin 2,0-2,4 kati surdugunu gosterdi (137/209/314 ms
     * karsi 64-108 ms). Cozum donusumu satir bantlarina bolup paralel
     * kosturmak; guvenligi, her pikselin YALNIZCA kendi degerinden turemesine
     * dayaniyor (komsu okuma yok, sirali durum yok).
     *
     * Bu test o dayanagi KANITLAR: ayni goruntu tek bantla ve dort bantla
     * kosturulur, sonuclar birebir esit olmali. Kontrast asamasi da dahildir —
     * o, bantlarin luma toplamlarinin BIRLESIMINDEN hesaplanir ve bolme
     * hatasina en acik yerdir.
     */
    @Test
    fun bandingProducesByteIdenticalOutput() {
        // 400x300 = 120.000 piksel: uretimdeki paralel esigin ustunde ve her
        // bant sinirina kesirli bir satir dusecek kadar "yuvarlak olmayan".
        val width = 400
        val height = 300
        fun source() = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            (0xFF shl 24) or ((x * 5 % 256) shl 16) or ((y * 7 % 256) shl 8) or ((x + y) % 256)
        }

        for (biome in Biome.entries.filterNot { it.isIdentity }) {
            val serial = source()
            val parallel = source()
            try {
                BiomeRecolor.forcedBandCount = 1
                BiomeRecolor.apply(biome, serial, width, height)
                BiomeRecolor.forcedBandCount = 4
                BiomeRecolor.apply(biome, parallel, width, height)
            } finally {
                BiomeRecolor.forcedBandCount = null
            }
            assertArrayEquals(
                "$biome: 4 bantli cikti tek bantli ciktidan farkli — bantlama " +
                    "pikseller arasi bir bagimlilik uretiyor",
                serial, parallel,
            )
        }
    }

    private fun luma(argb: Int): Float =
        0.2126f * ((argb ushr 16) and 0xFF) +
            0.7152f * ((argb ushr 8) and 0xFF) +
            0.0722f * (argb and 0xFF)
}
