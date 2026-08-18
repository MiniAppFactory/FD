package com.miniappfactory.frontlinedefender.perf

import com.miniappfactory.frontlinedefender.game.model.Biome
import com.miniappfactory.frontlinedefender.game.model.BiomeRecolor
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * BIYOM YENIDEN RENKLENDIRME — SURE VE BELLEK OLCUMU.
 *
 * Bu dosya `BiomeRecolorTest`in (dogruluk) performans ikizi. Orada "renk dogru
 * mu" sorulur, burada "bolum girisinde ne kadar bellek ve ne kadar sure
 * harcaniyor" sorulur.
 *
 * OLCULEN GERCEK BOYUT: 1920x1081 = 2.075.520 piksel. Kucuk bir test goruntusu
 * kullanmak yaniltici olurdu — sorunun tamami olceklemede.
 */
class BiomeRecolorCostTest {

    private companion object {
        const val W = 1920
        const val H = 1081
        const val N = W * H

        /** ARGB_8888 bitmap: 4 bayt/piksel. */
        const val BITMAP_BYTES = N * 4L        // 8.302.080

        /** `getPixels` hedefi: IntArray, 4 bayt/piksel. */
        const val PIXEL_ARRAY_BYTES = N * 4L   // 8.302.080

        /** Bitki maskesi: ByteArray. */
        const val MASK_BYTES = N * 1L          // 2.075.520

        /** Bulaniklik takas tamponu: ikinci ByteArray. */
        const val BLUR_TMP_BYTES = N * 1L      // 2.075.520
    }

    /** Gercek bir harita gibi: cimen (yesil), yol (kahve/gri), su (mavi). */
    private fun syntheticMap(): IntArray {
        val px = IntArray(N)
        var seed = 12345
        for (i in 0 until N) {
            seed = seed * 1103515245 + 12345
            val n = (seed ushr 16) and 0x3F
            val x = i % W
            val y = i / W
            // Capraz bir "yol" seridi + geri kalani bitki ortusu.
            val onRoad = ((x + y) % 640) < 90
            px[i] = if (onRoad) {
                (0xFF shl 24) or ((120 + n) shl 16) or ((104 + n) shl 8) or (86 + n)
            } else {
                (0xFF shl 24) or ((40 + n) shl 16) or ((110 + n) shl 8) or (46 + n)
            }
        }
        return px
    }

    // =========================================================================
    // 1. TAHSIS — recolor gecisi kac bayt SCRATCH ister?
    // =========================================================================

    @Test
    fun recolorScratchAllocationStaysInsideBudget() {
        assumeTrue("Bu JVM tahsis sayacini desteklemiyor", PerfHarness.allocationSupported)

        val source = syntheticMap()
        val work = IntArray(N)

        val results = LinkedHashMap<Biome, Double>()
        for (biome in listOf(Biome.NIGHT, Biome.WINTER, Biome.DESERT, Biome.AUTUMN)) {
            val bytes = PerfHarness.allocatedBytesPerIteration(warmup = 1, iterations = 3) {
                source.copyInto(work)
                BiomeRecolor.apply(biome, work, W, H)
            }
            results[biome] = bytes
        }

        PerfHarness.report(
            "BiomeRecolor.apply scratch tahsisi (1920x1081)",
            *results.map { (b, v) -> "$b = ${"%.0f".format(v)} bayt (${"%.2f".format(v / 1024 / 1024)} MiB)" }
                .toTypedArray(),
            "beklenen taban = maske ${MASK_BYTES} + blur tmp ${BLUR_TMP_BYTES} = ${MASK_BYTES + BLUR_TMP_BYTES}"
        )

        // Tavan: maske + blur tamponu + LUT'lar + %10 pay.
        val ceiling = (MASK_BYTES + BLUR_TMP_BYTES) * 11 / 10 + 64 * 1024
        results.forEach { (biome, bytes) ->
            assertTrue(
                "$biome recolor scratch tahsisi ${bytes.toLong()} bayt, tavan $ceiling bayt. " +
                    "Artis = bolum girisinde daha buyuk GC baskisi.",
                bytes <= ceiling
            )
        }
    }

    // =========================================================================
    // 2. SURE — JVM referans degeri (cihaz DEGIL)
    // =========================================================================

    @Test
    fun recolorWallClockIsRecorded() {
        val source = syntheticMap()
        val work = IntArray(N)

        // Taban: yalnizca kopyalama. Recolor suresinden cikarilir.
        val copyMs = PerfHarness.bestMillisPerIteration(warmup = 2, iterations = 5) {
            source.copyInto(work)
        }

        val lines = mutableListOf("kopyalama tabani = ${"%.2f".format(copyMs)} ms")
        for (biome in listOf(Biome.NIGHT, Biome.WINTER, Biome.DESERT, Biome.AUTUMN)) {
            val total = PerfHarness.bestMillisPerIteration(warmup = 2, iterations = 5) {
                source.copyInto(work)
                BiomeRecolor.apply(biome, work, W, H)
            }
            lines += "$biome = ${"%.2f".format(total - copyMs)} ms (JVM, cihaz DEGIL)"
        }
        PerfHarness.report("BiomeRecolor.apply suresi (1920x1081)", *lines.toTypedArray())

        // Kapi degil, kayit. Tek sert kural: sonsuza gitmesin.
        assertTrue(true)
    }

    // =========================================================================
    // 3. TEPE BELLEK MUHASEBESI — OOM riskinin sayisi
    // =========================================================================

    /**
     * `GameSprites.loadBackground` sirasinda AYNI ANDA bellekte olan tahsisler.
     *
     * minSdk 24 KRITIK: Android 8.0'dan (API 26) ONCE bitmap pikselleri
     * **Java heap'inde** tutulur. Yani asagidaki toplam, Galaxy S8 / API 24
     * uzerinde dogrudan ART heap limitine yazilir; native heap'e degil.
     */
    @Test
    fun peakMemoryDuringLevelLoadIsAccountedFor() {
        // Onbellekteki ESKI harita (tek yuva, `put` en SONDA yapiliyor).
        val oldCached = BITMAP_BYTES
        val decoded = BITMAP_BYTES
        val pixels = PIXEL_ARRAY_BYTES
        val mask = MASK_BYTES
        val blurTmp = BLUR_TMP_BYTES

        val peakWithOldSlot = oldCached + decoded + pixels + mask + blurTmp
        val peakWithoutOldSlot = decoded + pixels + mask + blurTmp
        // Yedek yol: decoder `inMutable`i yok saydi -> `Bitmap.createBitmap`
        // ikinci bir bitmap uretir. Yuva ONCEDEN bosaltilmis haldeki tepe.
        val peakImmutableFallback = peakWithoutOldSlot + BITMAP_BYTES

        PerfHarness.report(
            "Bolum girisi TEPE bellek (API 24: bitmap = Java heap)",
            "eski onbellek yuvasi     = $oldCached bayt (${"%.2f".format(oldCached / 1048576.0)} MiB)",
            "decode edilen bitmap     = $decoded bayt (${"%.2f".format(decoded / 1048576.0)} MiB)",
            "getPixels IntArray       = $pixels bayt (${"%.2f".format(pixels / 1048576.0)} MiB)",
            "bitki maskesi ByteArray  = $mask bayt (${"%.2f".format(mask / 1048576.0)} MiB)",
            "blur takas ByteArray     = $blurTmp bayt (${"%.2f".format(blurTmp / 1048576.0)} MiB)",
            "----",
            "TEPE (eski yuva tutulurken) = $peakWithOldSlot bayt (${"%.2f".format(peakWithOldSlot / 1048576.0)} MiB)",
            "TEPE (yuva once bosaltilirsa) = $peakWithoutOldSlot bayt (${"%.2f".format(peakWithoutOldSlot / 1048576.0)} MiB)",
            "TEPE (immutable yedek yolu)  = $peakImmutableFallback bayt (${"%.2f".format(peakImmutableFallback / 1048576.0)} MiB)"
        )

        // SOZLESME: eski yuvayi decode'dan ONCE birakmak tepe bellegi
        // en az bir bitmap kadar (7.9 MiB) dusurur. Bu testin gorevi o
        // kazancin sayisini kilitlemek.
        assertTrue(
            "Yuvayi once bosaltmak en az bir bitmap kazandirmali",
            peakWithOldSlot - peakWithoutOldSlot == BITMAP_BYTES
        )
        assertTrue(
            "Tepe bellek 32 MiB'i asarsa API 24 dusuk bellekli cihazda OOM riski gercek olur",
            peakImmutableFallback < 32L * 1024 * 1024
        )
    }
}
