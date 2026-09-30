package com.miniappfactory.frontlinedefender.game.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MAGAZA OTURUMU DURUMU (saf Kotlin) — `BillingManager`in Android'siz beyni.
 *
 * `BillingManager` SDK'yi surer; "simdi baglanayim mi", "urunleri yeniden
 * sorayim mi", "kac ms sonra tekrar deneyeyim" kararlari BURADA, JVM'de sinanir.
 *
 * ## Duzeltilen hatalar (2026-09-30)
 *
 *  * **Iki `startConnection` ayni anda.** `GameScreen` yasam dongusu gozlemcisini
 *    ekler (zaten RESUMED oldugu icin ON_RESUME hemen tekrarlanir -> `refresh()`
 *    -> `connect()`) ve hemen ardindan bir kez daha `connect()` cagirir. Tek
 *    ucus kilidi ([beginConnect]) ikinciyi yutar.
 *  * **Urun sorgusu bir kez calisiyor, basarisiz olursa sonsuza kadar bos.**
 *    Artik her yenilemede ve magaza acilisinda, eksik urun varsa yeniden sorulur
 *    ([beginProductQuery]).
 *  * **Ekran sonsuza kadar "Baglaniyor" diyordu.** Baglanti basarisiz olunca
 *    [StoreConnection.UNAVAILABLE] + [ProductsState.FAILED] yayinlanir; ekran
 *    sebep ve "tekrar dene" gosterebilir.
 */
enum class StoreConnection {
    /** `startConnection` yolda (baslangic durumu). */
    CONNECTING,

    /** Baglandi; urunler ve bitmemis satin almalar sorgulanabilir. */
    READY,

    /** Baglanti kurulamadi / dustu ve yeniden deneme hakki bitti. Sebep [StoreSession.unavailableReason]. */
    UNAVAILABLE,
}

enum class ProductsState { LOADING, LOADED, FAILED }

/** [StoreEvent.Failed]in sinifi — UI oyuncuya yerel metni buna gore secer. */
enum class FailureReason {
    /** Baglanti yok / henuz hazir degil. */
    NOT_READY,
    NETWORK,
    BILLING_UNAVAILABLE,
    PRODUCT_UNAVAILABLE,
    DEVELOPER_ERROR,

    /** Odeme alindi ama diske yazilamadi: coin GELMEDI, tuketilmedi, yeniden denenecek. */
    SAVE_FAILED,
    REJECTED,
    UNKNOWN_ITEM,
    BILLING_ERROR,
}

/**
 * Tek seferlik geri bildirim. UI gosterir ve UNUTUR (kalici durum degildir).
 *
 * ⚠ KronDx'in bilinen acigi: `Failed.message` hicbir yerde gosterilmiyordu ve
 * oyuncu para odeyip sessiz bir basarisizlik yasiyordu. Burada [Failed] hem
 * yerellestirilebilir bir [FailureReason] hem de tanilama metni tasir.
 */
sealed interface StoreEvent {
    data class Granted(val productId: String, val coins: Int, val legacy: Boolean = false) : StoreEvent
    data class AlreadyCredited(val productId: String) : StoreEvent
    data class Pending(val productId: String?) : StoreEvent
    data object Cancelled : StoreEvent
    data class Failed(val reason: FailureReason, val message: String) : StoreEvent
}

class StoreSession(
    private val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    private val baseBackoffMs: Long = DEFAULT_BASE_BACKOFF_MS,
) {
    companion object {
        const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 3
        const val DEFAULT_BASE_BACKOFF_MS = 1_500L
        const val MAX_BACKOFF_MS = 30_000L
    }

    private val lock = Any()
    private var connecting = false
    private var reconnectAttempts = 0
    private var productsInFlight = false
    private var closed = false

    private val _connection = MutableStateFlow(StoreConnection.CONNECTING)
    val connection: StateFlow<StoreConnection> = _connection.asStateFlow()

    private val _productsState = MutableStateFlow(ProductsState.LOADING)
    val productsState: StateFlow<ProductsState> = _productsState.asStateFlow()

    private val _unavailableReason = MutableStateFlow<String?>(null)
    val unavailableReason: StateFlow<String?> = _unavailableReason.asStateFlow()

    /** Bir `startConnection` su an yolda mi (testler icin). */
    val isConnecting: Boolean get() = synchronized(lock) { connecting }

    /**
     * `startConnection` cagrilmali mi? **TEK UCUS**: zaten yoldaysa (ya da kapaliysa) `false`.
     * `true` donerse cagiran HEMEN baglantiyi baslatmak zorundadir ve sonucu
     * [connectSucceeded] / [connectFailed] / [disconnected] ile bildirir.
     */
    fun beginConnect(): Boolean = synchronized(lock) {
        if (closed || connecting) return@synchronized false
        connecting = true
        _connection.value = StoreConnection.CONNECTING
        true
    }

    fun connectSucceeded() = synchronized(lock) {
        connecting = false
        reconnectAttempts = 0
        _connection.value = StoreConnection.READY
        _unavailableReason.value = null
    }

    fun connectFailed(reason: String) = synchronized(lock) {
        connecting = false
        _unavailableReason.value = reason
        _connection.value = StoreConnection.UNAVAILABLE
        // Urunler de yuklenemez; ekran "Baglaniyor" yerine hatayi gostersin.
        if (_productsState.value != ProductsState.LOADED) _productsState.value = ProductsState.FAILED
        productsInFlight = false
    }

    /**
     * Servis dustu. Yeniden deneme hakki varsa gecikmeyi (ms, ustel geri cekilme) dondurur ve durum
     * `CONNECTING` olur; hak bittiyse `null` ve durum `UNAVAILABLE`. Sinirsiz dongu pil yakar.
     */
    fun disconnected(): Long? = synchronized(lock) {
        connecting = false
        productsInFlight = false
        if (closed) return@synchronized null
        if (reconnectAttempts >= maxReconnectAttempts) {
            _unavailableReason.value = "connection to Google Play was lost"
            _connection.value = StoreConnection.UNAVAILABLE
            return@synchronized null
        }
        val delay = (baseBackoffMs shl reconnectAttempts.coerceAtMost(20)).coerceAtMost(MAX_BACKOFF_MS)
        reconnectAttempts++
        _connection.value = StoreConnection.CONNECTING
        delay
    }

    /**
     * Urun sorgusu gonderilmeli mi? [haveAllProducts] = satistaki TUM urunler elimizde.
     * Eksik urun varsa (bos sonuc, ag hatasi ya da Console'da eksik urun) VE bir sorgu
     * yolda degilse `true` — ve durum `LOADING` olur.
     */
    fun beginProductQuery(haveAllProducts: Boolean): Boolean = synchronized(lock) {
        if (closed || productsInFlight || haveAllProducts) return@synchronized false
        if (_connection.value != StoreConnection.READY) return@synchronized false
        productsInFlight = true
        _productsState.value = ProductsState.LOADING
        true
    }

    fun productsLoaded() = synchronized(lock) {
        productsInFlight = false
        _productsState.value = ProductsState.LOADED
    }

    fun productsFailed() = synchronized(lock) {
        productsInFlight = false
        _productsState.value = ProductsState.FAILED
    }

    fun close() = synchronized(lock) {
        closed = true
        connecting = false
        productsInFlight = false
    }
}
