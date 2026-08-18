package com.miniappfactory.frontlinedefender.perf

import java.lang.management.ManagementFactory

/**
 * OLCUM ALTYAPISI — performans ajani.
 *
 * NE OLCER / NE OLCMEZ
 *   Bu harness bir JVM uzerinde **tahsis edilen bayt** ve **duvar saati**
 *   olcer. Bu sayilar CIHAZDAKI kare suresi DEGILDIR ve oyle sunulmamalidir:
 *   ART'in tahsis yolu, JIT'i ve GC'si HotSpot'tan farklidir.
 *
 *   Buradaki sayilarin gecerli oldugu iki soru vardir ve ikisi de kritiktir:
 *     1. "Kare basina KAC BAYT tahsis ediliyor?" — tahsis SAYISI ve BOYUTU
 *        platformdan bagimsizdir (ayni nesneler, ayni alanlar). GC duraklamasi
 *        dogrudan bunun fonksiyonudur, yani jank'in kok sebebi burada gorunur.
 *     2. "Bu degisiklik ONCESINE gore ne yapti?" — ayni JVM'de once/sonra
 *        karsilastirmasi anlamlidir.
 *
 *   Mutlak ms degerleri yalnizca BUYUKLUK MERTEBESI icindir; cihaz sayilari
 *   docs/PERFORMANCE_REPORT.md icindeki adb komut listesinden alinir.
 *
 * NEDEN YANSIMA (reflection): `getThreadAllocatedBytes` `com.sun.management`
 * altindadir. Android birim testi kaynak setinde bu paketi dogrudan import
 * etmek, JDK'si farkli bir makinede derlemeyi kirar. Yansima ile cagirmak
 * destek yoksa testi ATLAYAN (fail etmeyen) bir yol birakir.
 */
object PerfHarness {

    private val bean: Any = ManagementFactory.getThreadMXBean()

    private val allocMethod = runCatching {
        Class.forName("com.sun.management.ThreadMXBean")
            .getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)
    }.getOrNull()

    /** Tahsis sayaci bu JVM'de var mi? Yoksa olcum testleri ATLANIR. */
    val allocationSupported: Boolean = allocMethod != null && rawBytes() >= 0L

    private fun rawBytes(): Long = runCatching {
        @Suppress("DEPRECATION")
        allocMethod?.invoke(bean, Thread.currentThread().id) as? Long ?: -1L
    }.getOrDefault(-1L)

    /**
     * Olcum cagrisinin KENDI tahsisi (yansima kutulamasi). Sifir isli bir
     * blogun 0 bayt olcumesi icin cikarilir; aksi halde her olcum ~48 bayt
     * sabit hata tasirdi ve "kare basina 0 tahsis" iddiasi kanitlanamazdi.
     */
    private val selfOverheadBytes: Long = run {
        if (allocMethod == null) return@run 0L
        // En kucuk degeri al: JIT isindiktan sonraki gercek maliyet budur.
        var best = Long.MAX_VALUE
        repeat(200) {
            val a = rawBytes()
            val b = rawBytes()
            val d = b - a
            if (d in 0 until best) best = d
        }
        if (best == Long.MAX_VALUE) 0L else best
    }

    /**
     * [block]'u [iterations] kez calistirip ITERASYON BASINA tahsis edilen
     * bayti dondurur. [warmup] iterasyonu JIT/sinif yukleme icin atilir.
     */
    fun allocatedBytesPerIteration(warmup: Int, iterations: Int, block: () -> Unit): Double {
        repeat(warmup) { block() }
        val start = rawBytes()
        repeat(iterations) { block() }
        val end = rawBytes()
        val total = (end - start - selfOverheadBytes).coerceAtLeast(0L)
        return total.toDouble() / iterations
    }

    /**
     * Iterasyon basina ORTALAMA nanosaniye — tum dongu bir kez zamanlanir.
     *
     * NEDEN TEK TEK OLCULMUYOR: Windows'ta `System.nanoTime` cozunurlugu ~100
     * ns. 100 ns'lik bir islemi tek tek olcmek "0 ns" ya da "1300 ns" gibi
     * anlamsiz degerler uretir (ilk kosuda tam olarak bu oldu). Toplu olcum
     * cozunurluk hatasini iterasyon sayisina boler.
     */
    fun averageNanosPerIteration(warmup: Int, iterations: Int, block: () -> Unit): Double {
        repeat(warmup) { block() }
        val t0 = System.nanoTime()
        repeat(iterations) { block() }
        return (System.nanoTime() - t0).toDouble() / iterations
    }

    /** Iterasyon basina en iyi (en dusuk) duvar saati, milisaniye. */
    fun bestMillisPerIteration(warmup: Int, iterations: Int, block: () -> Unit): Double {
        repeat(warmup) { block() }
        var best = Long.MAX_VALUE
        repeat(iterations) {
            val t0 = System.nanoTime()
            block()
            val d = System.nanoTime() - t0
            if (d < best) best = d
        }
        return best / 1_000_000.0
    }

    /**
     * Kare suresi ORNEKLERINDEN yuzdelik. Ortalama RAPORLANMAZ: jank tam
     * olarak ortalamanin gizledigi seydir (p50 iyi, p99 40 ms = "takiliyor").
     */
    fun percentileMillis(samplesNanos: LongArray, percentile: Int): Double {
        require(samplesNanos.isNotEmpty())
        val sorted = samplesNanos.copyOf().also { it.sort() }
        val idx = ((percentile / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx] / 1_000_000.0
    }

    fun report(title: String, vararg lines: String) {
        println("### PERF: $title")
        lines.forEach { println("###   $it") }
    }
}
