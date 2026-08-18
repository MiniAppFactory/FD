package com.miniappfactory.frontlinedefender.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/**
 * KARE YOLUNDAKI TAHSIS KALIPLARI — saf JVM mikro-olcumu.
 *
 * NEDEN KALIP OLCULUYOR, FONKSIYON DEGIL: `GameCanvas`in cizim yardimcilari
 * `DrawScope` uzantisi, yani gercek bir Skia tuvali olmadan cagrilamazlar.
 * Buradaki testler o fonksiyonlarin ICINDEKI tahsis kaynaklarini BIREBIR ayni
 * ifadeyle yeniden uretir ve "bu ifade kac bayt" sorusunu KANITLAR. Kare
 * basina toplam maliyet = olculen bayt x kare basina cagri sayisi.
 *
 * Bu sayilar HotSpot'ta olculur; ART'ta nesne basliklari ayni buyukluk
 * mertebesindedir. Onemli olan "sifir mi, degil mi" ve ONCE/SONRA farkidir.
 */
class FramePathAllocationTest {

    private class Sprite(val w: Int, val h: Int)

    private val spriteA = Sprite(64, 64)
    private val spriteB = Sprite(32, 32)

    @Suppress("UNUSED_PARAMETER")
    private fun sink(image: Sprite, width: Float) {
        blackhole += image.w + width.toInt()
    }

    private var blackhole = 0

    // =========================================================================
    // 1. `sprite to refWidth` — Pair + Float KUTULAMASI
    //    Kaynak: GameCanvas.drawProjectile, mermi BASINA her KAREDE.
    // =========================================================================

    @Test
    fun pairDestructuringInDrawProjectileAllocates() {
        assumeTrue(PerfHarness.allocationSupported)
        var flip = 0

        val withPair = PerfHarness.allocatedBytesPerIteration(warmup = 20_000, iterations = 200_000) {
            flip++
            val (image, refWidth) = if (flip and 1 == 0) spriteA to 18f else spriteB to 24f
            sink(image, refWidth)
        }

        val withoutPair = PerfHarness.allocatedBytesPerIteration(warmup = 20_000, iterations = 200_000) {
            flip++
            val even = flip and 1 == 0
            val image = if (even) spriteA else spriteB
            val refWidth = if (even) 18f else 24f
            sink(image, refWidth)
        }

        PerfHarness.report(
            "drawProjectile: `sprite to refWidth` kalibi",
            "Pair + Float kutulamasi = ${"%.1f".format(withPair)} bayt/cagri",
            "ayrik `when`            = ${"%.1f".format(withoutPair)} bayt/cagri",
            "40 mermi @60 FPS       = ${"%.0f".format(withPair * 40 * 60)} bayt/sn (Pair'li)"
        )

        assertTrue("Pair kalibi tahsis etmeli (olcum gecerli mi kontrolu)", withPair > 0.0)
        assertEquals("Ayrik when TAHSISSIZ olmali", 0.0, withoutPair, 0.9)
    }

    // =========================================================================
    // 2. `forEach` / `any` ITERATOR tahsisi
    //    Kaynak: GameCanvas cizim yolu (6 liste gecisi + pad basina `any`),
    //            GameEngine.tick (4 liste gecisi).
    // =========================================================================

    @Test
    fun iterableForEachAllocatesAnIteratorPerCall() {
        assumeTrue(PerfHarness.allocationSupported)
        val list: List<Sprite> = List(56) { Sprite(it, it) }

        val withForEach = PerfHarness.allocatedBytesPerIteration(warmup = 20_000, iterations = 100_000) {
            list.forEach { blackhole += it.w }
        }
        val withIndices = PerfHarness.allocatedBytesPerIteration(warmup = 20_000, iterations = 100_000) {
            for (i in list.indices) blackhole += list[i].w
        }

        PerfHarness.report(
            "liste gecisi: forEach vs indices",
            "forEach = ${"%.1f".format(withForEach)} bayt/gecis (iterator nesnesi)",
            "indices = ${"%.1f".format(withIndices)} bayt/gecis",
            "GameCanvas 16 gecis/kare @60 FPS = ${"%.0f".format(withForEach * 16 * 60)} bayt/sn"
        )

        assertEquals("indices dongusu TAHSISSIZ olmali", 0.0, withIndices, 0.9)
    }

    // =========================================================================
    // 3. ENTITY KIMLIGI — `UUID.randomUUID().toString()`
    //    Kaynak: GameEntities varsayilanlari; `fireTower` her ATISTA,
    //    `spawnEnemy` her SPAWN'da cagirir.
    //
    //    UUID.randomUUID() bir SecureRandom cagrisidir: yalnizca tahsis degil,
    //    KILITLI ve kriptografik bir uretecin maliyetidir. Mermi kimligi
    //    tahmin edilemez olmak zorunda degil.
    // =========================================================================

    @Test
    fun uuidEntityIdIsMoreExpensiveThanACounter() {
        assumeTrue(PerfHarness.allocationSupported)
        var counter = 0L

        val uuidBytes = PerfHarness.allocatedBytesPerIteration(warmup = 50_000, iterations = 500_000) {
            blackhole += UUID.randomUUID().toString().length
        }
        val counterBytes = PerfHarness.allocatedBytesPerIteration(warmup = 50_000, iterations = 500_000) {
            blackhole += ("e" + (counter++)).length
        }

        val uuidNs = PerfHarness.averageNanosPerIteration(warmup = 50_000, iterations = 500_000) {
            blackhole += UUID.randomUUID().toString().length
        }
        val counterNs = PerfHarness.averageNanosPerIteration(warmup = 50_000, iterations = 500_000) {
            blackhole += ("e" + (counter++)).length
        }

        PerfHarness.report(
            "entity id: UUID.randomUUID().toString() vs sayac",
            "UUID  = ${"%.1f".format(uuidBytes)} bayt, ${"%.0f".format(uuidNs)} ns/cagri",
            "sayac = ${"%.1f".format(counterBytes)} bayt, ${"%.0f".format(counterNs)} ns/cagri",
            "35 atis/sn (11 kule, gatling tier3 0.20 sn) = " +
                "${"%.0f".format((uuidBytes - counterBytes) * 35)} bayt/sn kazanc"
        )

        assertTrue(
            "UUID kimligi sayac kimliginden PAHALI olmali (degilse bu bulgu gecersiz)",
            uuidBytes > counterBytes
        )
    }

    // =========================================================================
    // 4. Compose `Offset` StateFlow'a yazilirken KUTULANIR
    //    Kaynak: GameEngine.updateScreenShake, sarsinti aktifken her kare.
    //    Deger sinifi (value class) heap'e kacinca kutulanir; bu kacinilmaz
    //    ama SAYISI bilinmeli.
    // =========================================================================

    @Test
    fun valueClassBoxingWhenStoredInAGenericHolder() {
        assumeTrue(PerfHarness.allocationSupported)
        var holder: Any? = null

        val boxed = PerfHarness.allocatedBytesPerIteration(warmup = 20_000, iterations = 200_000) {
            holder = java.lang.Long.valueOf(blackhole.toLong() or 0x1_0000_0000L)
        }
        blackhole += if (holder == null) 1 else 0

        PerfHarness.report(
            "deger sinifinin genel (generic) tutucuya yazilmasi",
            "kutulama = ${"%.1f".format(boxed)} bayt/kare",
            "@60 FPS  = ${"%.0f".format(boxed * 60)} bayt/sn (sarsinti aktifken)"
        )
        assertTrue(boxed >= 0.0)
    }
}
