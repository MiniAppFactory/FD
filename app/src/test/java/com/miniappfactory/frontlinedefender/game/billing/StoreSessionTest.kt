package com.miniappfactory.frontlinedefender.game.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MAGAZA OTURUMU — tek ucus baglanti, urun sorgusu yeniden denemesi, geri cekilme.
 *
 * Bunlar `BillingManager`in SDK'siz beynidir; her biri cihazda gorulen ya da denetimde bulunan bir
 * hatayi kapatir (bkz. [StoreSession] KDoc).
 */
class StoreSessionTest {

    /** `BillingManager.connect()`in davranisini yansitan minik model: kac `startConnection` gitti? */
    private class ConnectModel(val session: StoreSession) {
        var startConnectionCalls = 0
        fun connect() {
            if (session.beginConnect()) startConnectionCalls++
        }
    }

    @Test
    fun beginConnectIsSingleFlight() {
        val s = StoreSession()
        assertTrue(s.beginConnect())
        assertFalse("yoldayken ikinci baglanti baslatilmamali", s.beginConnect())
        assertTrue(s.isConnecting)
        s.connectSucceeded()
        assertFalse(s.isConnecting)
        assertTrue("bitince yeni baglanti baslatilabilir", s.beginConnect())
    }

    /**
     * GameScreen soguk acilista: yasam dongusu gozlemcisi eklenir (zaten RESUMED -> ON_RESUME hemen
     * tekrarlanir -> refresh() -> connect()) ve HEMEN ARDINDAN acik `billing.connect()`. Ikisi de
     * baglanti henuz kurulmamisken calisir.
     */
    @Test
    fun theColdStartDoubleConnectStartsOnlyOneConnection() {
        val model = ConnectModel(StoreSession())
        model.connect() // ON_RESUME tekrari -> refresh() -> connect()
        model.connect() // GameScreen'in acik connect() cagrisi
        assertEquals(1, model.startConnectionCalls)
        assertEquals(StoreConnection.CONNECTING, model.session.connection.value)
    }

    @Test
    fun setupSuccessPublishesReadyAndClearsTheReason() {
        val s = StoreSession()
        s.beginConnect()
        s.connectFailed("earlier failure")
        assertEquals("earlier failure", s.unavailableReason.value)
        s.beginConnect()
        s.connectSucceeded()
        assertEquals(StoreConnection.READY, s.connection.value)
        assertNull(s.unavailableReason.value)
    }

    /** Ekran sonsuza kadar "Baglaniyor" demez: baglanti basarisiz -> UNAVAILABLE + FAILED + sebep. */
    @Test
    fun aFailedSetupPublishesUnavailableAndFailedProductsSoTheScreenCanShowTheReason() {
        val s = StoreSession()
        assertEquals("baslangic", StoreConnection.CONNECTING, s.connection.value)
        assertEquals(ProductsState.LOADING, s.productsState.value)
        s.beginConnect()
        s.connectFailed("code 3: billing unavailable")

        assertEquals(StoreConnection.UNAVAILABLE, s.connection.value)
        assertEquals(ProductsState.FAILED, s.productsState.value)
        assertEquals("code 3: billing unavailable", s.unavailableReason.value)
        assertFalse("basarisiz baglanti tek-ucus kilidini birakir", s.isConnecting)
        assertTrue("yeniden deneme mumkun", s.beginConnect())
    }

    @Test
    fun aLoadedCatalogIsNotDowngradedByALaterConnectionFailure() {
        val s = StoreSession()
        s.beginConnect(); s.connectSucceeded()
        assertTrue(s.beginProductQuery(haveAllProducts = false)); s.productsLoaded()
        s.beginConnect(); s.connectFailed("offline")
        assertEquals("elimizdeki fiyatlar korunur", ProductsState.LOADED, s.productsState.value)
        assertEquals(StoreConnection.UNAVAILABLE, s.connection.value)
    }

    @Test
    fun disconnectBacksOffExponentiallyThenGivesUp() {
        val s = StoreSession(maxReconnectAttempts = 3, baseBackoffMs = 1_500L)
        s.beginConnect(); s.connectSucceeded()

        assertEquals(1_500L, s.disconnected())
        assertEquals(StoreConnection.CONNECTING, s.connection.value)
        assertEquals(3_000L, s.disconnected())
        assertEquals(6_000L, s.disconnected())
        assertNull("hak bitti", s.disconnected())
        assertEquals(StoreConnection.UNAVAILABLE, s.connection.value)
        assertTrue(s.unavailableReason.value!!.isNotBlank())
    }

    @Test
    fun backoffIsCappedAndASuccessfulSetupResetsTheAttempts() {
        val s = StoreSession(maxReconnectAttempts = 50, baseBackoffMs = 1_500L)
        var last = 0L
        repeat(10) { last = s.disconnected()!! }
        assertEquals("geri cekilme tavani", StoreSession.MAX_BACKOFF_MS, last)

        s.beginConnect(); s.connectSucceeded()
        assertEquals("basarili baglanti sayaci sifirlar", 1_500L, s.disconnected())
    }

    /** Urun sorgusu bir kez calisip basarisiz olursa magaza sonsuza kadar bos KALMAZ. */
    @Test
    fun theProductQueryIsRetriedWhenProductsAreMissingAndNotWhenComplete() {
        val s = StoreSession()
        s.beginConnect(); s.connectSucceeded()

        assertTrue("ilk sorgu", s.beginProductQuery(haveAllProducts = false))
        assertEquals(ProductsState.LOADING, s.productsState.value)
        assertFalse("yoldayken ikinci sorgu yok", s.beginProductQuery(haveAllProducts = false))

        s.productsFailed()
        assertEquals(ProductsState.FAILED, s.productsState.value)
        // Sonraki yenileme / magaza acilisi tekrar sorar.
        assertTrue("basarisiz sonrasi yeniden sorgu", s.beginProductQuery(haveAllProducts = false))

        // Kismi sonuc (Console'da eksik urun): LOADED ama eksik -> yine sorulur.
        s.productsLoaded()
        assertEquals(ProductsState.LOADED, s.productsState.value)
        assertTrue("eksik urun varken yeniden sorgu", s.beginProductQuery(haveAllProducts = false))
        s.productsLoaded()

        // Hepsi elimizde: gereksiz sorgu yok.
        assertFalse(s.beginProductQuery(haveAllProducts = true))
    }

    @Test
    fun theProductQueryNeedsAReadyConnection() {
        val s = StoreSession()
        assertFalse("baglanti yokken sorgu gonderilmez", s.beginProductQuery(haveAllProducts = false))
        s.beginConnect()
        assertFalse("baglanti kurulurken de gonderilmez", s.beginProductQuery(haveAllProducts = false))
        s.connectSucceeded()
        assertTrue(s.beginProductQuery(haveAllProducts = false))
    }

    @Test
    fun aDisconnectFreesTheInFlightProductQuery() {
        val s = StoreSession()
        s.beginConnect(); s.connectSucceeded()
        assertTrue(s.beginProductQuery(false))
        s.disconnected()
        s.beginConnect(); s.connectSucceeded()
        assertTrue("dusen baglantinin yarim sorgusu kilit birakmamali", s.beginProductQuery(false))
    }

    @Test
    fun aClosedSessionStartsNothing() {
        val s = StoreSession()
        s.close()
        assertFalse(s.beginConnect())
        assertFalse(s.beginProductQuery(false))
        assertNull(s.disconnected())
    }
}
