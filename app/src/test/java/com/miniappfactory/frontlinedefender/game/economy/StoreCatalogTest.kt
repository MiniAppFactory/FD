package com.miniappfactory.frontlinedefender.game.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * =============================================================================
 * COIN MAGAZASI — paket dengesinin ve katalog sozlesmesinin kilidi
 * =============================================================================
 *
 * Denge testleri sayilari degil KURALLARI kilitler: paket miktarlari fiyatlanma
 * verisiyle degisebilir, kurallar degisemez. Cipa ([StoreCatalog.oneTimeSinkTotal])
 * ekonomiden hesaplanir; kampanya buyudugunde bu test yeni cipayla kendini
 * gunceller — elle tablo yok.
 *
 * Buna karsilik URUN KIMLIKLERI ve ESKI MIKTARLAR bilincli olarak SABIT yazilidir:
 * Play Console'daki id'ler ve oyuncularin gecmiste odedigi miktarlar bir sayi
 * ayari degil, degistirilemez tarihtir.
 */
class StoreCatalogTest {

    // =========================================================================
    // Denge kurallari
    // =========================================================================

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
        // Belgelenen deger. Emiciler degisirse burasi BILINCLI guncellenir —
        // 2026-09-02'de prestij 19.900 -> 38.800 cikinca 62.605 -> 81.505 oldu.
        // Yinelenen emiciler (elit bilet, us tamiri, hava destegi) toplama GIRMEZ;
        // katalog degisikligi bu sayiyi oynatmamalidir.
        assertEquals(81_505, StoreCatalog.oneTimeSinkTotal)
    }

    /**
     * En kucuk paket ANLAMLI ama oyunu kirmayan bir giris olmali: ilk ucretli
     * bolumlerin kilidini birkac kez acar, ama ilk prestij nisanini tek basina
     * ALMAZ — en ucuz paket "kestirme" degil "ivme" satar.
     */
    @Test
    fun smallestPackIsMeaningfulButNotAShortcut() {
        val smallest = StoreCatalog.smallestPackCoins
        assertTrue("en kucuk paket birkac erken kilidi acmali", smallest >= 5 * lockCost(EconomyConfig.FIRST_PAID_LEVEL))
        assertTrue("en kucuk paket ilk prestij nisanini tek basina almamali", smallest < EconomyConfig.PRESTIGE_COSTS.first())
    }

    // =========================================================================
    // Kimlik / miktar sozlesmesi
    // =========================================================================

    /** Onayli urun sayfasi (PRODUCTS.md): bes id ve bes miktar, BIREBIR. */
    @Test
    fun catalogMatchesTheFiveApprovedProductsAndAmounts() {
        val expected = linkedMapOf(
            "fd_coins_1000" to 1_000,
            "fd_coins_2200" to 2_200,
            "fd_coins_3600" to 3_600,
            "fd_coins_5200" to 5_200,
            "fd_coins_7000" to 7_000,
        )
        assertEquals(expected, StoreCatalog.coinPacks.associate { it.id to it.coins })
        assertEquals(expected.keys.toList(), StoreCatalog.allProductIds)
        // Test-yalniz referans fiyatlar Console taban fiyatlariyla eslesir; ekrana CIKMAZ.
        assertEquals(
            listOf(0.99, 1.99, 2.99, 3.99, 4.99),
            StoreCatalog.coinPacks.map { it.usdReference },
        )
    }

    /**
     * Urun id'si icindeki sayi, verilen coin'e esit olmali. Play Console'da id
     * degistirilemez; id ile miktar ayrisirsa oyuncu magazada "fd_coins_2200"
     * gorup baska bir sey alir — destek talebinin ders kitabi ornegi.
     */
    @Test
    fun productIdEncodesTheCoinAmount() {
        StoreCatalog.coinPacks.forEach { pack ->
            assertTrue("${pack.id} fd_coins_ ile baslamali", pack.id.startsWith("fd_coins_"))
            val encoded = pack.id.removePrefix("fd_coins_").toIntOrNull()
            assertEquals("${pack.id} id'si coin miktarini yansitmiyor", pack.coins, encoded)
        }
    }

    @Test
    fun everySaleIdHasADistinctAmountAndTier() {
        val ids = StoreCatalog.coinPacks.map { it.id }
        val amounts = StoreCatalog.coinPacks.map { it.coins }
        val tiers = StoreCatalog.coinPacks.map { it.tier }
        assertEquals("tekrar eden urun id'si", ids.size, ids.toSet().size)
        assertEquals("iki paket ayni coin miktarini veriyor", amounts.size, amounts.toSet().size)
        assertEquals("iki paket ayni rutbeyi tasiyor", tiers.size, tiers.toSet().size)
        assertEquals(
            "rutbe kodlari kod etiketi olarak tekil ve dolu olmali",
            StoreCatalog.PackTier.entries.size,
            StoreCatalog.PackTier.entries.map { it.code }.toSet().size,
        )
        // Her rutbe tam bir pakette kullanilir (ekranda bos/tekrar eden rutbe yok).
        assertEquals(StoreCatalog.PackTier.entries.toSet(), tiers.toSet())
    }

    @Test
    fun productIdsAreUniqueAndAllOnSale() {
        val all = StoreCatalog.allProductIds
        assertEquals("tekrar eden urun id'si", all.size, all.toSet().size)
        StoreCatalog.coinPacks.forEach { assertTrue(it.id in StoreCatalog.consumableIds) }
        assertEquals(StoreCatalog.consumableIds, all)
    }

    @Test
    fun packOfResolvesOnlySalePacks() {
        StoreCatalog.coinPacks.forEach { pack ->
            assertEquals(pack, StoreCatalog.packOf(pack.id))
        }
        assertNull("remove_ads bir coin paketi degil", StoreCatalog.packOf(StoreCatalog.PRODUCT_REMOVE_ADS))
        assertNull("eski id satis paketi degil", StoreCatalog.packOf("coins_1200"))
        assertNull(StoreCatalog.packOf("olmayan_urun"))
        assertNotNull(StoreCatalog.packOf(StoreCatalog.PACK_RECRUIT))
    }

    // =========================================================================
    // ESKI urunler — satista degil, ama odenmis olanlar ESKI miktarla odenir
    // =========================================================================

    /**
     * SERT KURAL: eski bir urun id'sini YENI tabloyla okumak, oyuncudan coin calmak
     * olurdu (eski 0,99$ paketi 1.200 coindi; yeni 0,99$ paketi 1.000). Miktarlar
     * BILINCLI olarak sabit yazili: bunlar oyuncularin gecmiste odedigi tarihtir.
     */
    @Test
    fun legacyGrantsEqualTheOriginalAmounts() {
        val original = linkedMapOf(
            "coins_1200" to 1_200,
            "coins_4000" to 4_000,
            "coins_7400" to 7_400,
            "coins_16000" to 16_000,
            "coins_25000" to 25_000,
        )
        assertEquals(original, StoreCatalog.legacyFulfillment)
        original.forEach { (id, amount) ->
            assertEquals("$id eski miktarla odenmeli", amount, StoreCatalog.coinsOf(id))
            assertEquals(StoreCatalog.ProductKind.LEGACY_COINS, StoreCatalog.kindOf(id))
        }
    }

    @Test
    fun legacyIdsAreNotInTheSaleList() {
        val sale = StoreCatalog.allProductIds.toSet()
        StoreCatalog.legacyCoinProductIds.forEach { id ->
            assertFalse("$id satis listesinde (queryProductDetails'e gider)", id in sale)
            assertFalse("$id coinPacks'te", StoreCatalog.coinPacks.any { it.id == id })
            assertFalse("$id consumableIds'te", id in StoreCatalog.consumableIds)
        }
        assertFalse("remove_ads satista", StoreCatalog.PRODUCT_REMOVE_ADS in sale)
    }

    @Test
    fun newAndLegacyIdsAreDisjoint() {
        val sale = StoreCatalog.coinPacks.map { it.id }.toSet()
        val legacy = StoreCatalog.legacyCoinProductIds.toSet() + StoreCatalog.legacyAcknowledgeOnlyIds
        assertTrue("yeni ve eski id kumeleri kesisiyor: ${sale intersect legacy}", (sale intersect legacy).isEmpty())
        // Eski coin id'leri yeni miktarlarla ESLESMEZ (ayni 0,99$ paketi: 1.200 != 1.000).
        assertTrue(StoreCatalog.coinsOf("coins_1200") != StoreCatalog.coinsOf(StoreCatalog.PACK_RECRUIT))
    }

    /** Eski reklamsiz urunu satistan kalkti; yalnizca ESKI alicilar icin bilinen (onaylanir). */
    @Test
    fun removeAdsIsKnownOnlyAsALegacyAcknowledgeOnlyProduct() {
        assertEquals(StoreCatalog.ProductKind.LEGACY_REMOVE_ADS, StoreCatalog.kindOf(StoreCatalog.PRODUCT_REMOVE_ADS))
        assertNull("remove_ads coin vermez", StoreCatalog.coinsOf(StoreCatalog.PRODUCT_REMOVE_ADS))
        assertTrue(StoreCatalog.PRODUCT_REMOVE_ADS in StoreCatalog.legacyAcknowledgeOnlyIds)
    }

    @Test
    fun unknownIdsHaveNoKindAndNoAmount() {
        listOf("", " ", "fd_coins_1001", "FD_COINS_1000", " fd_coins_1000", "coins_1000", "krondx_coins_1000", "olmayan").forEach {
            assertNull("'$it' bilinmeyen urun olmali", StoreCatalog.kindOf(it))
            assertNull(StoreCatalog.coinsOf(it))
        }
    }
}
