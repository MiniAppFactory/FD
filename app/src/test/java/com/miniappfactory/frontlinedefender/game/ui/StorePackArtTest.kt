package com.miniappfactory.frontlinedefender.game.ui

import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Her satistaki coin paketinin KENDI gorseli vardir; farkli paketler ayni gorseli paylasmaz ve
 * eski/bilinmeyen kimlik gorsel uydurmaz (kart madeni para yedegine duser).
 */
class StorePackArtTest {

    @Test
    fun everySellablePackHasItsOwnArt() {
        val ids = StoreCatalog.coinPacks.map { it.id }
        val arts = ids.map { packArtRes(it) }
        arts.forEachIndexed { i, res -> assertNotNull("${ids[i]} icin gorsel yok", res) }
        assertEquals("iki paket ayni gorseli paylasiyor", ids.size, arts.toSet().size)
    }

    @Test
    fun legacyAndUnknownIdsHaveNoPackArt() {
        StoreCatalog.legacyFulfillment.keys.forEach { assertNull("$it eski kimlik, satista degil", packArtRes(it)) }
        assertNull(packArtRes(StoreCatalog.PRODUCT_REMOVE_ADS))
        assertNull(packArtRes("nope"))
    }
}
