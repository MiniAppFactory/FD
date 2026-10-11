package com.miniappfactory.frontlinedefender.rendering

import com.miniappfactory.frontlinedefender.game.model.Biome
import com.miniappfactory.frontlinedefender.game.model.BiomeVariants
import com.miniappfactory.frontlinedefender.game.ui.KeyedMapLoad
import com.miniappfactory.frontlinedefender.game.ui.MapBackgroundLoader
import com.miniappfactory.frontlinedefender.game.ui.MapLoad
import com.miniappfactory.frontlinedefender.game.ui.loadMapWithRetry
import com.miniappfactory.frontlinedefender.game.ui.selectMapState
import com.miniappfactory.frontlinedefender.game.ui.singleFlightMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * SOGUK-GIRIS HARITA YUKLEYICI DAYANIKLILIGI (FD-018).
 *
 * Codex'in istedigi gibi SAHTE/ERTELENMIS yukleyiciyle test edilir —
 * **gercek bitmap SART DEGIL**: `loadMapWithRetry`/`singleFlightMap` [T] uzerinde
 * generic oldugu icin burada "bitmap" yerine `String` kullanilir. Kapsanan:
 * retry, fallback, askida kalmama, gercek-hata vs iptal ayrimi, ve ayni anahtar
 * icin es-zamanli tek-ucus (dedupe).
 */
class MapBackgroundTest {

    private val biome: Biome = BiomeVariants.biomeFor(1)

    // =====================================================================
    // loadMapWithRetry — retry / fallback / askida kalmama / hata vs iptal
    // =====================================================================

    @Test
    fun retriesTheSameMapThenSucceeds() = runBlocking {
        val calls = AtomicInteger(0)
        val result = loadMapWithRetry(mapId = 5, biome = biome, maxAttempts = 2) { _, _ ->
            if (calls.incrementAndGet() < 2) null else "map5"
        }
        assertEquals(MapLoad.Ready("map5"), result)
        assertEquals("ikinci denemede basarili olmali", 2, calls.get())
    }

    @Test
    fun onlyRetriesTheSameMapNeverADifferentFallbackMap() = runBlocking {
        // FD-019: farkli harita fallback'i YOK — map geometrisi activeMapId'den
        // gelir, baska mapId'nin bitmap'i yol/pad'lerle uyumsuz cizim uretirdi.
        // Birincil hep basarisizsa yalniz Failed doner; decode BASKA bir mapId
        // ile ASLA cagrilmaz.
        val seenMaps = mutableSetOf<Int>()
        val result = loadMapWithRetry(mapId = 7, biome = biome, maxAttempts = 2) { m, _ ->
            seenMaps.add(m); null
        }
        assertEquals(MapLoad.Failed, result)
        assertEquals("decode yalniz istenen haritayla cagrilmali", setOf(7), seenMaps)
    }

    @Test
    fun failsWithoutHangingWhenAllAttemptsFail() = runBlocking {
        val calls = AtomicInteger(0)
        val result = loadMapWithRetry(mapId = 7, biome = biome, maxAttempts = 2) { _, _ ->
            calls.incrementAndGet(); null
        }
        assertEquals("askida kalmaz, Failed doner", MapLoad.Failed, result)
        assertEquals("deneme sayisi SINIRLI (sonsuz dongu yok)", 2, calls.get())
    }

    @Test
    fun aThrownDecodeIsRetriedNotPropagated() = runBlocking {
        val calls = AtomicInteger(0)
        val result = loadMapWithRetry(mapId = 3, biome = biome, maxAttempts = 2) { _, _ ->
            if (calls.incrementAndGet() < 2) error("decode patladi") else "ok"
        }
        assertEquals(MapLoad.Ready("ok"), result)
        assertEquals(2, calls.get())
    }

    @Test
    fun cancellationIsNotSwallowed() = runBlocking {
        val gate = CompletableDeferred<String>() // hic tamamlanmaz
        var produced: MapLoad<String>? = null
        var cancelled = false
        val job = launch {
            try {
                produced = loadMapWithRetry(mapId = 9, biome = biome, maxAttempts = 2) { _, _ ->
                    gate.await() // suspend; iptal burada tetiklenir
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw c
            }
        }
        yield() // job'un decode icinde suspend olmasini sagla
        job.cancelAndJoin()
        assertTrue("iptal CancellationException olarak yayilmali (yutulmamali)", cancelled)
        assertEquals("iptalde SONUC uretilmemeli (stale map gosterilmez)", null, produced)
    }


    // =====================================================================
    // singleFlightMap — ayni anahtar icin es-zamanli TEK decode
    // =====================================================================

    @Test
    fun concurrentSameKeyLoadsDecodeOnlyOnce() = runBlocking {
        val loadCalls = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        var cacheValue: String? = null
        val cached = { cacheValue }
        val load: suspend () -> String = {
            loadCalls.incrementAndGet()
            gate.await() // ilk cagriyi kilidi tutarken beklet
            "bitmap".also { cacheValue = it }
        }

        val r1 = async { singleFlightMap(cached, load) }
        yield() // r1 kilidi alip load'a girsin
        val r2 = async { singleFlightMap(cached, load) } // kilidi bekler
        yield()
        gate.complete(Unit) // ilk load tamamlansin

        assertEquals("bitmap", r1.await())
        assertEquals("bitmap", r2.await())
        assertEquals("ayni anahtar icin decode YALNIZ BIR kez calismali", 1, loadCalls.get())
    }

    @Test
    fun aCachedValueSkipsTheLoaderEntirely() = runBlocking {
        val loadCalls = AtomicInteger(0)
        val result = singleFlightMap<String>(
            cached = { "hazir" },
            load = { loadCalls.incrementAndGet(); "yeni" },
        )
        assertEquals("hazir", result)
        assertEquals("cache dolu iken load CAGRILMAZ", 0, loadCalls.get())
    }

    // =====================================================================
    // MapBackgroundLoader — URETIMDEKI yayinlama/anahtar guard (FD-020)
    // rememberMapBackground bu sinifi kullanir; testler onu dogrudan surer.
    // =====================================================================

    @Test
    fun aLateResultFromACancelledKeyDoesNotOverwriteTheNewKey() = runBlocking {
        // FD-020: HIZLI HEDEF DEGISIMI. A (map1) yuklenirken B (map2)'ye gecilir;
        // B once Ready olur; SONRA A GEC tamamlanir (decode iptal edilemese bile)
        // -> B KORUNUR, eski A sonucu yayinlanmaz. Gercek epoch guard sinaniyor.
        val gateA = CompletableDeferred<String>()
        val gateB = CompletableDeferred<String>()
        val biomeB = BiomeVariants.biomeFor(12)
        val loader = MapBackgroundLoader<String>(
            scope = this,
            maxAttempts = 1,
            cached = { _, _ -> null },
            decode = { m, _ ->
                if (m == 1) withContext(NonCancellable) { gateA.await() } // iptale ragmen biter
                else gateB.await()
            },
        )
        loader.request(1, biome)    // A baslar
        yield()
        loader.request(2, biomeB)   // B'ye gec (A iptal istenir)
        yield()
        gateB.complete("B")         // B ONCE biter
        yield(); yield()
        assertEquals(KeyedMapLoad(2, biomeB, MapLoad.Ready("B")), loader.state.value)
        gateA.complete("A-gec")     // A GEC biter
        yield(); yield()
        assertEquals(
            "gec gelen A sonucu B'yi EZMEMELI",
            KeyedMapLoad(2, biomeB, MapLoad.Ready("B")),
            loader.state.value
        )
    }

    @Test
    fun retryAfterFailedProducesReady() = runBlocking {
        // Failed -> retry -> Ready entegrasyonu (ayni hedef).
        val attempts = AtomicInteger(0)
        val loader = MapBackgroundLoader<String>(
            scope = this,
            maxAttempts = 2,
            cached = { _, _ -> null },
            decode = { _, _ -> if (attempts.incrementAndGet() <= 2) null else "ok" },
        )
        loader.request(7, biome)
        yield()
        assertEquals("ilk istek 2 deneme basarisiz -> Failed", MapLoad.Failed, loader.state.value?.load)
        loader.retry()
        yield()
        assertEquals("retry sonraki decode'da basarili -> Ready", MapLoad.Ready("ok"), loader.state.value?.load)
    }

    @Test
    fun aCacheHitPublishesReadySynchronouslyWithoutDecoding() = runBlocking {
        val loader = MapBackgroundLoader<String>(
            scope = this,
            maxAttempts = 2,
            cached = { m, _ -> if (m == 3) "cached3" else null },
            decode = { _, _ -> fail("onbellek hit'te decode CAGRILMAMALI"); "" },
        )
        loader.request(3, biome)
        assertEquals(
            "onbellek hit -> SENKRON Ready",
            KeyedMapLoad(3, biome, MapLoad.Ready("cached3")),
            loader.state.value
        )
    }

    @Test
    fun anAlreadyReadyKeyIsNotReloaded() = runBlocking {
        val decodeCalls = AtomicInteger(0)
        val loader = MapBackgroundLoader<String>(
            scope = this,
            maxAttempts = 2,
            cached = { _, _ -> null },
            decode = { _, _ -> decodeCalls.incrementAndGet(); "m5" },
        )
        loader.request(5, biome)
        yield()
        assertEquals(MapLoad.Ready("m5"), loader.state.value?.load)
        loader.request(5, biome) // ayni hedef, zaten Ready -> yeniden yukleme YOK
        yield()
        assertEquals("ayni Ready hedef tekrar decode edilmez", 1, decodeCalls.get())
    }

    // =====================================================================
    // selectMapState — CIZIME DONEN DURUM SECIMI (FD-021, uretim mekanizmasi)
    // rememberMapBackground bunu kullanir: hedef uyusmazsa SENKRON Loading.
    // =====================================================================

    @Test
    fun selectReturnsSyncLoadingWhenPublishedIsForADifferentTarget() {
        // A Ready yayinlanmis; hedef B -> request efekti kosmadan bile Loading
        // (eski A bitmap'i/Ready'si yeni geometriyle KULLANILMAZ).
        val publishedA = KeyedMapLoad(1, biome, MapLoad.Ready("A"))
        assertEquals(MapLoad.Loading, selectMapState(2, BiomeVariants.biomeFor(12), publishedA))
    }

    @Test
    fun selectReturnsSyncLoadingWhenSameMapButDifferentBiome() {
        val publishedDay = KeyedMapLoad(1, biome, MapLoad.Ready("day"))
        assertEquals(MapLoad.Loading, selectMapState(1, BiomeVariants.biomeFor(12), publishedDay))
    }

    @Test
    fun selectReturnsPublishedLoadOnlyWhenTargetMatches() {
        val published = KeyedMapLoad(1, biome, MapLoad.Ready("A"))
        assertEquals(MapLoad.Ready("A"), selectMapState(1, biome, published))
    }

    @Test
    fun selectReturnsLoadingWhenNothingPublishedYet() {
        assertEquals(MapLoad.Loading, selectMapState<String>(1, biome, null))
    }
}
