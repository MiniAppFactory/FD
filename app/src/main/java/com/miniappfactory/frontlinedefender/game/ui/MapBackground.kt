package com.miniappfactory.frontlinedefender.game.ui

import com.miniappfactory.frontlinedefender.game.model.Biome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SOGUK-GIRIS HARITA YUKLEME DURUMU + yukleme cekirdegi (FD-018).
 *
 * Harita arkaplani (boyali pad yuvalari + yol) biyom decode+recolor ile
 * asenkron gelir. Bu dosya, o yuklemenin DURUMUNU ve dayanikliligini —
 * retry/fallback, askida kalmama, tek-ucus (dedupe), iptal — Compose ve gercek
 * bitmap olmadan test edilebilecek SAF cekirdek olarak tutar (`GameSprites`
 * yalnizca gercek decode'u enjekte eder; `GameCanvas`/`GameEngine` durumu okur).
 */

/** Harita arkaplaninin yukleme durumu; [T] uretimde `ImageBitmap`, testte sahte tip. */
sealed interface MapLoad<out T> {
    /** Henuz hazir degil — UI "yukleniyor" gosterir, saha etkilesimi ve dalga baslatma beklenir. */
    data object Loading : MapLoad<Nothing>
    data class Ready<T>(val value: T) : MapLoad<T>
    /** Deneme + fallback tukendi; ASKIDA kalinmaz, UI bu durumu isler. */
    data object Failed : MapLoad<Nothing>
}

val <T> MapLoad<T>.valueOrNull: T? get() = (this as? MapLoad.Ready)?.value
val MapLoad<*>.isReady: Boolean get() = this is MapLoad.Ready

/**
 * Bir yukleme sonucunu HANGI (mapId,biome) icin oldugu kimligiyle tasir
 * (FD-021). Yayinlanan durum bu kimlikle etiketlenir ki cizim/okuma tarafi,
 * hedef degisiminde ESKI haritanin sonucunu yeni geometriyle kullanmasin.
 */
data class KeyedMapLoad<out T>(val mapId: Int, val biome: Biome, val load: MapLoad<T>)

/**
 * CIZIME DONEN DURUM SECIMI (FD-021).
 *
 * Yalnizca yayinlanan sonuc ISTENEN (mapId,biome) icinse onu dondurur; aksi
 * halde SENKRON [MapLoad.Loading]. Boylece hedef A'dan B'ye degisince, request
 * efekti henuz kosmamis olsa bile, B'nin ilk karesinde A'nin bitmap'i/Ready'si
 * KULLANILMAZ — eski harita yeni geometriyle cizilmez, readiness eski kalmaz.
 */
fun <T> selectMapState(
    requestedMapId: Int,
    requestedBiome: Biome,
    published: KeyedMapLoad<T>?,
): MapLoad<T> =
    if (published != null && published.mapId == requestedMapId && published.biome == requestedBiome) {
        published.load
    } else {
        MapLoad.Loading
    }

/**
 * Harita arkaplanini AYNI HEDEF icin RETRY ile yukler; ASLA sonsuz beklemez.
 *
 * - [decode] enjekte edilir: hata'da exception atabilir ya da `null` donebilir
 *   (gercek bitmap TEST icin SART DEGIL — [T] herhangi bir tip olabilir).
 * - [mapId] icin [maxAttempts] deneme; hepsi basarisizsa [MapLoad.Failed].
 * - **FARKLI harita fallback'i YOK** (FD-019): map geometrisi/yollari
 *   `GameEngine.activeMapId`'den gelir; baska bir mapId'nin bitmap'ini `Ready`
 *   sunmak yol/pad'lerle UYUMSUZ bir cizim uretir. Basarisizlikta cagiran
 *   taraf hata ekrani (yeniden dene / bolum sec) gosterir, yanlis harita DEGIL.
 * - **Iptal yayilir:** `decode` suspend oldugu icin cagiran coroutine iptal
 *   edilince (hizli hedef degisimi) bu fonksiyon da iptal olur ve SONUC DONMEZ;
 *   `CancellationException` YUTULMAZ (yalnizca gercek hatalar yeniden denenir),
 *   yani gec gelen eski sonuc yayinlanmaz.
 */
suspend fun <T> loadMapWithRetry(
    mapId: Int,
    biome: Biome,
    maxAttempts: Int,
    decode: suspend (Int, Biome) -> T?,
): MapLoad<T> {
    repeat(maxAttempts.coerceAtLeast(1)) {
        val r = try {
            decode(mapId, biome)
        } catch (c: CancellationException) {
            throw c // iptal YUTULMAZ — stale sonuc uretme, yayil
        } catch (e: Throwable) {
            null // gercek decode hatasi -> ayni hedef retry
        }
        if (r != null) return MapLoad.Ready(r)
    }
    return MapLoad.Failed
}

/**
 * TEK-UCUS (single-flight) — ayni harita arkaplani icin es-zamanli yuklemeleri
 * SERILESTIRIR. Onbellek tek yuvalidir ve [load] 8.3 MB decode + 2.08M px
 * recolor yapar; iki yukleme AYNI ANDA kosarsa iki bitmap birden bellekte olur
 * ve API 24 Java heap'inde OOM riski dogar (on-isitma + `rememberMapBackground`
 * cakismasi). Kilit, ikinci cagriyi birincinin bitmesine kadar bekletir; kilit
 * acildiginda [cached] hazir sonucu dondurur ve [load] IKINCI kez CAGRILMAZ.
 *
 * Enjekte edilebilir ([cached]/[load] lambda) oldugu icin dedupe davranisi
 * sahte yukleyiciyle test edilebilir (gercek bitmap sart degil).
 */
private val mapSingleFlightMutex = Mutex()

internal suspend fun <T : Any> singleFlightMap(
    cached: () -> T?,
    load: suspend () -> T,
): T = cached() ?: mapSingleFlightMutex.withLock { cached() ?: load() }

/**
 * HARITA ARKAPLANI YAYINLAYICI (FD-019/020).
 *
 * Compose'dan BAGIMSIZ, bu yuzden uretimdeki ANAHTAR/YAYINLAMA mekanizmasi
 * gercek bitmap olmadan test edilebilir ([MapBackgroundTest]). Garantiler:
 *  - [request] yeni (mapId,biome) ister: onceki yuklemeyi IPTAL eder, Loading
 *    yayinlar, yeni yuklemeyi baslatir. Sonuc YALNIZ hala en guncel istekse
 *    yayinlanir (epoch guard) -> hizli hedef degisiminde gec gelen eski (A)
 *    sonuc yeni (B) durumu EZMEZ (decode iptal edilemese bile).
 *  - Onbellek ([cached]) doluysa SENKRON Ready yayinlar (soguk-olmayan girişte
 *    "yukleniyor" bosluğu yok).
 *  - [retry] Failed sonrasi AYNI hedefi yeniden dener (farkli harita YOK).
 *
 * `rememberMapBackground` uretimde bunu `cached = BiomeBackgroundCache.get`,
 * `decode = GameSprites.loadBackgroundDeduped` ile kullanir.
 */
class MapBackgroundLoader<T : Any>(
    private val scope: CoroutineScope,
    private val maxAttempts: Int,
    private val cached: (Int, Biome) -> T?,
    private val decode: suspend (Int, Biome) -> T?,
) {
    // Yayinlanan durum HANGI (mapId,biome) icin oldugu kimligiyle (FD-021).
    // `null` = henuz hicbir hedef istenmedi. Okuyan taraf `selectMapState` ile
    // yalniz guncel hedefin sonucunu kullanir.
    private val _state = MutableStateFlow<KeyedMapLoad<T>?>(null)
    val state: StateFlow<KeyedMapLoad<T>?> = _state

    private var job: Job? = null
    private var currentMapId: Int? = null
    private var currentBiome: Biome? = null
    // Her yukleme bir epoch alir; yayinlarken hala en guncel epoch mu diye bakar.
    private var epoch = 0

    fun request(mapId: Int, biome: Biome) {
        // Ayni hedef ve o hedef icin zaten Ready ise yeniden yukleme yok.
        val cur = _state.value
        if (mapId == currentMapId && biome == currentBiome &&
            cur != null && cur.mapId == mapId && cur.biome == biome && cur.load is MapLoad.Ready
        ) {
            return
        }
        currentMapId = mapId
        currentBiome = biome
        job?.cancel()
        val hit = cached(mapId, biome)
        if (hit != null) {
            epoch++ // bekleyen bir yukleme varsa artik onun sonucu yayinlanmasin
            _state.value = KeyedMapLoad(mapId, biome, MapLoad.Ready(hit))
            job = null
            return
        }
        startLoad(mapId, biome)
    }

    /** Failed sonrasi AYNI hedefi yeniden dener (hata ekranindaki "yeniden dene"). */
    fun retry() {
        val m = currentMapId ?: return
        val b = currentBiome ?: return
        startLoad(m, b)
    }

    private fun startLoad(mapId: Int, biome: Biome) {
        job?.cancel()
        val myEpoch = ++epoch
        _state.value = KeyedMapLoad(mapId, biome, MapLoad.Loading)
        job = scope.launch {
            val result = loadMapWithRetry(mapId, biome, maxAttempts, decode)
            // YALNIZ hala en guncel istek isem yayinla: gec gelen eski sonuc
            // yeni durumu EZEMEZ (iptal decode'u durduramasa bile).
            if (myEpoch == epoch) _state.value = KeyedMapLoad(mapId, biome, result)
        }
    }
}
