package com.miniappfactory.frontlinedefender.game.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * =============================================================================
 * COIN MAGAZASI — paket dengesinin sozlesmesi
 * =============================================================================
 *
 * Sayilar degil KURALLAR kilitlenir: paket miktarlari fiyatlanma verisiyle
 * degisebilir, kurallar degisemez. Cipa ([StoreCatalog.oneTimeSinkTotal])
 * ekonomiden hesaplanir; kampanya buyudugunde bu test yeni cipayla kendini
 * gunceller — elle tablo yok.
 */
class StoreCatalogTest {

    @Test
    fun packsAreOrderedCheapToExpensive() {
        val prices = StoreCatalog.coinPacks.map { it.usdReference }
        assertEquals("ekran sirasi = fiyat sirasi", prices.sorted(), prices)
        val coins = StoreCatalog.coinPacks.map { it.coins }
        assertEquals("daha pahali paket daha cok coin verir", coins.sorted(), coins)
    }

    /** Kural 1 — buyuk paket her zaman daha iyi birim deger verir. */
    @Test
    fun coinsPerUsdStrictlyIncreasesWithPackSize() {
        val ratios = StoreCatalog.coinPacks.map { it.coinsPerUsd }
        for (i in 1 until ratios.size) {
            assertTrue(
                "${StoreCatalog.coinPacks[i].id}: ${"%.0f".format(ratios[i])} coin/USD, " +
                    "bir oncekinden (${"%.0f".format(ratios[i - 1])}) kucuk veya esit",
                ratios[i] > ratios[i - 1],
            )
        }
    }

    /** Kural 2 — tek paket oyunu bitiremez. */
    @Test
    fun largestPackStaysBelowHalfOfOneTimeSinks() {
        val share = StoreCatalog.largestPackShareOfSinks
        assertTrue(
            "en buyuk paket ${StoreCatalog.largestPackCoins} = tek seferlik giderlerin " +
                "%${"%.1f".format(share * 100)}'i — %50 tavani asildi",
            share < 0.50,
        )
    }

    /** Cipa gercekten ekonomiden geliyor; elle yazilan sayi yok. */
    @Test
    fun oneTimeSinkTotalIsDerivedFromEconomyConfig() {
        assertEquals(
            EconomyConfig.TOTAL_LOCK_COST + EconomyConfig.TREE_TOTAL_COST +
                EconomyConfig.PRESTIGE_TOTAL_COST,
            StoreCatalog.oneTimeSinkTotal,
        )
        // Belgelenen deger (2026-09-02). Kampanya buyurse burasi BILINCLI guncellenir.
        assertEquals(62_605, StoreCatalog.oneTimeSinkTotal)
    }

    /**
     * En kucuk paket ANLAMLI ama oyunu kirmayan bir giris olmali: ilk ucretli
     * bolumlerin kilidini birkac kez acar (L7-L12 kilitleri 100-150), ama ilk
     * prestij nisanini (1.500) tek basina ALMAZ — en ucuz paket "kestirme"
     * degil "ivme" satar.
     */
    @Test
    fun smallestPackIsMeaningfulButNotAShortcut() {
        val smallest = StoreCatalog.smallestPackCoins
        assertTrue("en kucuk paket birkac erken kilidi acmali", smallest >= 5 * lockCost(EconomyConfig.FIRST_PAID_LEVEL))
        assertTrue("en kucuk paket ilk prestij nisanini tek basina almamali", smallest < EconomyConfig.PRESTIGE_COSTS.first())
    }

    @Test
    fun productIdsAreUniqueAndSplitIntoConsumableAndNonConsumable() {
        val all = StoreCatalog.allProductIds
        assertEquals("tekrar eden urun id'si", all.size, all.toSet().size)
        assertTrue(StoreCatalog.PRODUCT_REMOVE_ADS in StoreCatalog.nonConsumableIds)
        assertTrue(StoreCatalog.PRODUCT_REMOVE_ADS !in StoreCatalog.consumableIds)
        StoreCatalog.coinPacks.forEach { assertTrue(it.id in StoreCatalog.consumableIds) }
    }

    @Test
    fun packOfResolvesOnlyCoinPacks() {
        StoreCatalog.coinPacks.forEach { pack ->
            assertEquals(pack, StoreCatalog.packOf(pack.id))
        }
        assertNull("remove_ads bir coin paketi degil", StoreCatalog.packOf(StoreCatalog.PRODUCT_REMOVE_ADS))
        assertNull(StoreCatalog.packOf("olmayan_urun"))
        assertNotNull(StoreCatalog.packOf(StoreCatalog.PACK_RECRUIT))
    }

    /**
     * Urun id'si icindeki sayi, verilen coin'e esit olmali. Play Console'da id
     * degistirilemez; id ile miktar ayrisirsa oyuncu magazada "coins_4000"
     * gorup 3.500 alir — destek talebinin ders kitabi ornegi.
     */
    @Test
    fun productIdEncodesTheCoinAmount() {
        StoreCatalog.coinPacks.forEach { pack ->
            val encoded = pack.id.removePrefix("coins_").toIntOrNull()
            assertEquals("${pack.id} id'si coin miktarini yansitmiyor", pack.coins, encoded)
        }
    }
}
