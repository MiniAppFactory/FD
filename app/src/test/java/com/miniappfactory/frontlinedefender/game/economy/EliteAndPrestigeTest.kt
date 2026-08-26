package com.miniappfactory.frontlinedefender.game.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.Test

/**
 * =============================================================================
 * ELIT SEVK + PRESTIJ — ECONOMY_ANALYSIS C+B emicilerinin sozlesmesi
 * =============================================================================
 *
 * Bu iki emici, `CoinLedgerTest`in olctugu yapisal acigi (3 yildiz bandinda
 * 45.295 olu coin) kapatmak icin eklendi. Buradaki testler EMICILERIN KENDI
 * kurallarini kilitler; ledger'in kampanya sayilari DEGISMEZ cunku ikisi de
 * OPSIYONELDIR ve zorunlu yoldan coin cekmez.
 */
class EliteAndPrestigeTest {

    // =========================================================================
    // ELIT BILET FIYATI — kurallar, sayilar degil
    // =========================================================================

    @Test
    fun eliteTicketPriceIsHalfTheOneStarRewardRoundedToTens() {
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            val price = eliteTicketPrice(level)
            val oneStar = levelReward(level, 1)

            // GDD kisiti (Us Tamiri ile ayni): fiyat 1-yildiz odulunun altinda.
            assertTrue("L$level: bilet $price >= 1 yildiz odulu $oneStar", price < oneStar)

            // Oran sabit: yari, 10'a yuvarli. Elle tablo YOK — gelir egrisi
            // degisirse bilet kendiliginden olceklenir.
            assertEquals("L$level yuvarlama", 0, price % 10)
            assertTrue("L$level: $price yarim odulden sapmis", oneStar / 2 - price in 0..9)
        }
        // Uc nokta, belgelenen degerler (rapor ile ayni dil):
        assertEquals(160, eliteTicketPrice(7))
        assertEquals(880, eliteTicketPrice(55))
        // Monoton: gec bolum bileti hep daha pahali (fazla nerede buyuyorsa
        // emici de orada buyur).
        for (level in 2..EconomyConfig.CAMPAIGN_LEVELS) {
            assertTrue(eliteTicketPrice(level) >= eliteTicketPrice(level - 1))
        }
    }

    // =========================================================================
    // ELIT KARAR YOLLARI
    // =========================================================================

    @Test
    fun eliteRequiresAClearedLevelSufficientFundsAndRespectsTheReserve() {
        // Temizlenmemis bolum: para olsa da HAYIR — elit zaferin uzerine kurulur.
        val rich = PlayerWallet(coins = 10_000)
        assertTrue(eliteAllowed(rich, 7) is EliteDecision.NotCleared)

        // Temizlenmis + para var: EVET, fiyat dogru duser.
        val cleared = rich.copy(
            clearedLevels = setOf(7),
            unlockedLevels = rich.unlockedLevels + setOf(7, 8),
        )
        val d = eliteAllowed(cleared, 7)
        assertTrue("$d", d is EliteDecision.Allowed)
        val after = applyEliteTicket(cleared, 7)
        assertEquals(cleared.coins - eliteTicketPrice(7), after.coins)
        assertEquals(
            "harcama defteri de islenmeli",
            cleared.lifetimeSpent + eliteTicketPrice(7), after.lifetimeSpent,
        )

        // Para yok: InsufficientFunds, eksik dogru hesaplanir.
        val broke = cleared.copy(coins = eliteTicketPrice(7) - 30)
        val d2 = eliteAllowed(broke, 7)
        assertTrue("$d2", d2 is EliteDecision.InsufficientFunds)
        assertEquals(30, (d2 as EliteDecision.InsufficientFunds).shortfall)

        // REZERV: bakiye bileti karsilar ama siradaki kilit icin ayrilmis —
        // soft-lock garantisi elit bilete de uygulanir.
        val nextLock = EconomyConfig.LOCK_COSTS.first { it > 0 }
        val reserved = cleared.copy(coins = eliteTicketPrice(7) + nextLock - 10)
        // (siradaki kilitli bolum 8 icin rezerv `reserveFor` uzerinden gelir)
        val d3 = eliteAllowed(reserved, 7)
        if (reserveFor(reserved) > 0) {
            assertTrue("rezerv varken $d3", d3 is EliteDecision.ReserveLocked)
        }
    }

    // =========================================================================
    // PRESTIJ — toplam, siralama, karar yollari
    // =========================================================================

    @Test
    fun prestigeCostsSumToTheDocumentedDeepSinkAndIncreaseMonotonically() {
        assertEquals(5, EconomyConfig.PRESTIGE_MAX)
        assertEquals(19_900, EconomyConfig.PRESTIGE_TOTAL_COST)
        for (i in 1 until EconomyConfig.PRESTIGE_MAX) {
            assertTrue(
                "nisan ${i + 1} oncekinden ucuz olamaz",
                EconomyConfig.PRESTIGE_COSTS[i] > EconomyConfig.PRESTIGE_COSTS[i - 1],
            )
        }
        // Ilk nisan kampanya ORTASINDA alinabilir olmali: 1 yildiz bandinin
        // L28 bakiyesi ~10-11k, fiyat 1.500 — erken oyunda rezervle bogusmaz,
        // gec oyunda onemsizlesmez. Kaba sinir: ilk fiyat < L10 bakiyesi olmasin
        // (cok erken), son fiyat 1 yildiz kampanya fazlasinin (9.545) altinda
        // olsun (en kotu bant bile SON nisani alabilmeli).
        assertTrue(EconomyConfig.PRESTIGE_COSTS.first() > 1_000)
        assertTrue(
            "son nisan en kotu bandin fazlasini asiyor",
            EconomyConfig.PRESTIGE_COSTS.last() < 9_545,
        )
    }

    @Test
    fun prestigePurchaseWalksTheRanksAndStopsAtMax() {
        var wallet = PlayerWallet(coins = EconomyConfig.PRESTIGE_TOTAL_COST + 5_000)
        var rank = 0
        while (true) {
            val d = prestigeAllowed(wallet, rank)
            if (d is PrestigeDecision.MaxRank) break
            assertTrue("$d", d is PrestigeDecision.Allowed)
            val (w, r) = applyPrestige(wallet, rank)
            wallet = w
            rank = r
        }
        assertEquals(EconomyConfig.PRESTIGE_MAX, rank)
        assertEquals(5_000, wallet.coins)
        assertTrue(prestigeAllowed(wallet, rank) is PrestigeDecision.MaxRank)
    }

    // =========================================================================
    // EMICI KAPASITESI — analiz raporunun kapanis matematigi
    // =========================================================================

    /**
     * ECONOMY_ANALYSIS S1: 3 yildiz bandi 45.295 olu coin. Prestij tek basina
     * 19.900 emer; kalan ~25.400 icin elit ORTALAMA bilet fiyatiyla (472) ~53 kosu
     * gerekir — yani emici "iki dokunusta biten" bir hazne degil, kampanya
     * sonrasi GERCEK bir oyun dongusudur. Bu test kapasite matematigini
     * belgeler ve iki taraftan biri sessizce degisirse kirmizi yanar.
     */
    @Test
    fun theTwoSinksTogetherCanAbsorbTheThreeStarSurplus() {
        val surplus = 45_295 // CoinLedgerTest kilidi
        val afterPrestige = surplus - EconomyConfig.PRESTIGE_TOTAL_COST
        assertEquals(25_395, afterPrestige)

        val avgTicket = (1..EconomyConfig.CAMPAIGN_LEVELS)
            .sumOf { eliteTicketPrice(it) } / EconomyConfig.CAMPAIGN_LEVELS
        assertEquals(472, avgTicket)

        val runsNeeded = afterPrestige / avgTicket
        assertTrue(
            "kalan fazla ortalama ${runsNeeded} elit kosuyla emilmeli (20-100 bandi)",
            runsNeeded in 20..100,
        )
    }

    // =========================================================================
    // ELIT CAN KURALI
    // =========================================================================

    @Test
    fun eliteLivesHalvesRoundingUpAndNeverReachesZero() {
        assertEquals(10, EconomyConfig.eliteLives(20))
        assertEquals(11, EconomyConfig.eliteLives(21))
        assertEquals(15, EconomyConfig.eliteLives(30)) // maks Tahkimat'la
        assertEquals(1, EconomyConfig.eliteLives(1))
        assertEquals(1, EconomyConfig.eliteLives(0)) // savunma: asla 0 olmaz
    }

    // =========================================================================
    // KALICILIK — SaveManager encode/decode
    // =========================================================================

    @Test
    fun eliteClearsSurviveAPersistenceRoundTrip() {
        val store = InMemoryKeyValueStore()
        val save = SaveManager(store)

        save.eliteClears = mapOf(7 to 2, 12 to 1, 55 to 9)
        assertEquals(mapOf(7 to 2, 12 to 1, 55 to 9), save.eliteClears)

        // Ikinci "acilis": ayni store'dan yeni SaveManager ayni veriyi okur.
        val reopened = SaveManager(store)
        assertEquals(mapOf(7 to 2, 12 to 1, 55 to 9), reopened.eliteClears)

        save.prestigeRank = 3
        assertEquals(3, SaveManager(store).prestigeRank)
        // Tavan korunur.
        save.prestigeRank = 99
        assertEquals(EconomyConfig.PRESTIGE_MAX, save.prestigeRank)

        // Ilerleme sifirlamasi IKISINI de goturur (dil tercihi kurali bunlara
        // uygulanmaz: nisan ve elit sayaci ILERLEMEDIR).
        save.resetProgress()
        assertTrue(save.eliteClears.isEmpty())
        assertEquals(0, save.prestigeRank)
    }

    @Test
    fun corruptEliteClearEntriesAreDroppedNotCrashed() {
        val store = InMemoryKeyValueStore()
        store.putString("eco_elite_clears", "7:2,BOZUK,:,12:abc,0:5,-3:1,55:1")
        val save = SaveManager(store)
        assertEquals(mapOf(7 to 2, 55 to 1), save.eliteClears)
    }
}
