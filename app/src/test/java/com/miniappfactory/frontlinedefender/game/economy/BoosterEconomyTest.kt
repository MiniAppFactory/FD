package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Faz 10 — GUCLENDIRICI EKONOMISI (REKLAMSIZ, TEK ODEME YOLU = COIN).
 *
 * 2026-09-30: oyundan tum reklamlar kalkti; eskiden reklamla alinan haklar coinle
 * fiyatlandi. Bu sinif eski "reklam yolu / arbitraj kalkani / gunluk reklam butcesi"
 * testlerinin YERINE gecen yeni kurallari kilitler; eski dallara ait testler
 * gevsetilmedi, SILINDI (dal artik yok) ve kurallari yeni dunyaya gore YENIDEN YAZILDI.
 *
 * KILITLENEN SERT KURALLAR
 *  1. Her guclendirici COIN fiyatli; bedel dokunustan ONCE bilinir (`Allowed(price)`).
 *  2. Acil Tedarik: 2 coin = 1 Tedarik, savas basina 1 kez, L2'de acilir, fiyat < R(L,1),
 *     Konuslanma Rezervi'ne tabi. (GDD D.4 "coin Tedarik olmaz" BILINCLI GERI ALINDI.)
 *  3. Hava Destegi: iki kullanim ayni fiyat; toplam hasar `kullanim x oran < 1,0`.
 *  4. Us Tamiri: 1. kullanim 4 can, 2. kullanim 7 can ve CAN BASINA AYNI FIYAT; yildiz notr.
 *  5. Rezerv coini guclendiriciye harcanamaz; reddedilen kullanim hicbir seyi degistirmez.
 *  6. Guclendirici harcamasi kampanyayi soft-lock'a sokamaz.
 *
 * Test SAYI EZBERLEMEZ: kural enum'dan/formulden turetilir; elle yazilan sayilar ya
 * sahibinin kararidir (islerine "sahip karari" yazilidir) ya da aritmetigi yorumda gosterilir.
 */
class BoosterEconomyTest {

    // =================================================================================
    // Test altyapisi
    // =================================================================================

    private val lastLevel = EconomyConfig.CAMPAIGN_LEVELS

    private fun state(level: Int) = BoosterState.startBattle(level)

    /** Tum bolumler acik -> Konuslanma Rezervi 0. Fiyat sinirlari saf haliyle olculur. */
    private fun openWallet(coins: Int) =
        PlayerWallet(coins = coins, unlockedLevels = (1..lastLevel).toSet())

    /** 1-6 temizlenmis, 7 kilitli -> rezerv TAM 100 (`lockCost(7)`). */
    private fun gateWallet(coins: Int) = PlayerWallet(
        coins = coins,
        unlockedLevels = (1..6).toSet(),
        clearedLevels = (1..6).toSet(),
        bestStars = (1..6).associateWith { 2 },
    )

    private val richWallet = openWallet(1_000_000)

    /** [type]'in [useNumber]. kullanimina hazir savas durumu (onceki kullanim + bitmis bekleme). */
    private fun stateBeforeUse(type: BoosterType, level: Int, useNumber: Int): BoosterState =
        if (useNumber <= 1) {
            state(level)
        } else {
            BoosterState(
                level = level,
                paidUses = mapOf(type to useNumber - 1),
                lastUseMs = mapOf(type to 0L),
            )
        }

    /** Ortak sahne: us cani 5/20 (tamir icin 15 kayip), sahada 5 dusman. */
    private fun decide(
        type: BoosterType,
        level: Int,
        useNumber: Int,
        wallet: PlayerWallet,
        supplyOnHand: Int = 0,
        baseHealth: Int = 5,
        enemiesOnField: Int = 5,
    ): BoosterDecision = boosterAllowed(
        stateBeforeUse(type, level, useNumber), type, wallet,
        supplyOnHand = supplyOnHand, baseHealth = baseHealth, enemiesOnField = enemiesOnField,
    )

    /** (tip, bolum, kullanim) uclulerinin TAMAMI: acildigi bolumden kampanya sonuna. */
    private fun everyPricedUse(): List<Triple<BoosterType, Int, Int>> =
        BoosterType.entries.flatMap { type ->
            (type.unlockLevel..lastLevel).flatMap { level ->
                (1..type.paidUsesPerBattle).map { use -> Triple(type, level, use) }
            }
        }

    // =================================================================================
    // 1. Acilma bolumleri
    // =================================================================================

    @Test
    fun eachBoosterUnlocksExactlyAtItsDesignedLevel() {
        assertEquals(2, BoosterType.EMERGENCY_SUPPLY.unlockLevel)
        assertEquals(4, BoosterType.AIR_SUPPORT.unlockLevel)
        assertEquals(7, BoosterType.BASE_REPAIR.unlockLevel)

        BoosterType.entries.forEach { type ->
            for (level in 1..lastLevel) {
                assertEquals("$type L$level", level >= type.unlockLevel, boosterUnlocked(type, level))
            }
        }
    }

    @Test
    fun firstLevelHasNoBoostersAtAll() {
        // Bolum 1 saf ogretme alani: hicbir guclendirici, hicbir coin dugmesi.
        assertTrue(boostersAvailableAt(1).isEmpty())
        assertEquals(listOf(BoosterType.EMERGENCY_SUPPLY), boostersAvailableAt(2))
        assertEquals(
            listOf(BoosterType.EMERGENCY_SUPPLY, BoosterType.AIR_SUPPORT),
            boostersAvailableAt(4),
        )
        assertEquals(BoosterType.entries.toList(), boostersAvailableAt(7))
    }

    @Test
    fun lockedBoosterIsRefusedWhateverTheWalletHolds() {
        // Bolum kilidi cuzdandan ONCE gelir: sinirsiz coin bile kilitli tipi acmaz.
        BoosterType.entries.forEach { type ->
            for (level in 1 until type.unlockLevel) {
                assertEquals(
                    "$type L$level",
                    BoosterDecision.Locked(type.unlockLevel),
                    boosterAllowed(
                        state(level), type, richWallet,
                        supplyOnHand = 9_999, baseHealth = 5, enemiesOnField = 5,
                    ),
                )
            }
        }
    }

    // =================================================================================
    // 2. Fiyatlama — "hemen alinabilir ama anlamsiz derecede ucuz degil"
    // =================================================================================

    @Test
    fun everyBoosterIsPricedInCoinOnly() {
        // SAHIP KARARI 2026-09-30: tek odeme yolu coin. Tedarik fiyatli guclendirici yok.
        BoosterType.entries.forEach { type ->
            assertEquals("$type", BoosterCurrency.COIN, type.currency)
        }
        // Bedel dokunustan once bilinir: karar kendi fiyatini ve para birimini tasir.
        everyPricedUse().forEach { (type, level, use) ->
            assertEquals(
                "$type L$level #$use",
                BoosterDecision.Allowed(boosterPrice(type, level, use), BoosterCurrency.COIN),
                decide(type, level, use, richWallet),
            )
        }
    }

    @Test
    fun ownerDecisionUsesPerBattle() {
        // SAHIP KARARI (FOLLOW-UP 006): Acil Tedarik 1, Hava Destegi 2, Us Tamiri 2 kullanim.
        assertEquals(1, BoosterType.EMERGENCY_SUPPLY.paidUsesPerBattle)
        assertEquals(2, BoosterType.AIR_SUPPORT.paidUsesPerBattle)
        assertEquals(2, BoosterType.BASE_REPAIR.paidUsesPerBattle)
        BoosterType.entries.forEach { assertEquals(it.paidUsesPerBattle, it.maxUsesPerBattle) }
    }

    @Test
    fun priceTableMatchesTheFormulas() {
        // Hava Destegi 134 + 22(L-1), IKI kullanim ayni fiyat.
        assertEquals(200, boosterPrice(BoosterType.AIR_SUPPORT, 4))       // 134 + 66
        assertEquals(266, boosterPrice(BoosterType.AIR_SUPPORT, 7))       // 134 + 132
        assertEquals(596, boosterPrice(BoosterType.AIR_SUPPORT, 22))      // 134 + 462
        assertEquals(266, boosterPrice(BoosterType.AIR_SUPPORT, 7, useNumber = 2))

        // Us Tamiri 120 + 20(L-1); 2. kullanim = roundToTen(1. x 7 / 4).
        assertEquals(240, boosterPrice(BoosterType.BASE_REPAIR, 7))       // 120 + 120
        assertEquals(540, boosterPrice(BoosterType.BASE_REPAIR, 22))      // 120 + 420
        assertEquals(420, boosterPrice(BoosterType.BASE_REPAIR, 7, 2))    // 240 x 7/4 = 420
        assertEquals(950, boosterPrice(BoosterType.BASE_REPAIR, 22, 2))   // 540 x 7/4 = 945 -> 950

        // Acil Tedarik = 2 x verdigi Tedarik (amount = roundToTen(60 + 6(L-1))).
        assertEquals(70, emergencySupplyAmount(2))                        // 66 -> 70
        assertEquals(90, emergencySupplyAmount(6))                        // 90
        assertEquals(190, emergencySupplyAmount(22))                      // 186 -> 190
        assertEquals(140, boosterPrice(BoosterType.EMERGENCY_SUPPLY, 2))
        assertEquals(180, boosterPrice(BoosterType.EMERGENCY_SUPPLY, 6))
        assertEquals(380, boosterPrice(BoosterType.EMERGENCY_SUPPLY, 22))
    }

    @Test
    fun useNumberOutsideTheDeclaredUsesIsRejected() {
        BoosterType.entries.forEach { type ->
            listOf(0, type.paidUsesPerBattle + 1).forEach { bad ->
                try {
                    boosterPrice(type, 10, bad)
                    fail("$type useNumber=$bad kabul edildi")
                } catch (expected: IllegalArgumentException) {
                    // beklenen
                }
            }
        }
    }

    @Test
    fun pricesNeverDecreaseWithLevel() {
        BoosterType.entries.forEach { type ->
            for (use in 1..type.paidUsesPerBattle) {
                for (level in (type.unlockLevel + 1)..lastLevel) {
                    assertTrue(
                        "$type #$use L$level fiyati L${level - 1}'den dusuk",
                        boosterPrice(type, level, use) >= boosterPrice(type, level - 1, use),
                    )
                }
            }
        }
    }

    /**
     * Hava Destegi, en ucuz kalici meta yukseltmesinden UCUZ olamaz (altina duserse en iyi
     * strateji yukseltme almak yerine her savasta dugmeye basmak olurdu); acilista fiyat TAM
     * o taban. Bu taban Acil Tedarik'e UYGULANMAZ, bkz. [emergencySupplyFloorCannotApply].
     */
    @Test
    fun airSupportNeverCostsLessThanTheCheapestPermanentRank() {
        val cheapestRank = UpgradeLine.entries.minOf { it.costOfRank(1) }
        assertEquals(
            "acilista fiyat TAM olarak en ucuz kalici meta yukseltmesi olmali",
            cheapestRank,
            boosterPrice(BoosterType.AIR_SUPPORT, BoosterType.AIR_SUPPORT.unlockLevel),
        )
        for (level in BoosterType.AIR_SUPPORT.unlockLevel..lastLevel) {
            val price = boosterPrice(BoosterType.AIR_SUPPORT, level)
            assertTrue("L$level: hava destegi $price < en ucuz rank $cheapestRank", price >= cheapestRank)
        }
    }

    /** Hava Destegi Us Tamiri'nden (1. kullanim) PAHALI ve 1-yildiz odulunun ALTINDA olmali. */
    @Test
    fun airSupportPriceSitsBetweenBaseRepairAndTheOneStarReward() {
        for (level in BoosterType.AIR_SUPPORT.unlockLevel..lastLevel) {
            val air = boosterPrice(BoosterType.AIR_SUPPORT, level)
            val oneStar = levelReward(level, 1)
            assertTrue("L$level: hava destegi $air >= 1 yildiz odulu $oneStar", air < oneStar)
            // Butce tavani: guclendirici tek basina bir bolum odulunun %90'ini yiyemez.
            assertTrue(
                "L$level: hava destegi 1-yildiz odulunun %.1f%%'i".format(100.0 * air / oneStar),
                100.0 * air / oneStar <= 90.0,
            )
            if (level >= BoosterType.BASE_REPAIR.unlockLevel) {
                val repair = boosterPrice(BoosterType.BASE_REPAIR, level)
                assertTrue("L$level: hava destegi $air, us tamiri $repair", air > repair)
            }
        }
    }

    @Test
    fun baseRepairFirstUseIsPricedBetweenTheCheapestRankAndOneStarReward() {
        val cheapestRank = UpgradeLine.entries.minOf { it.costOfRank(1) }
        assertEquals(200, cheapestRank)
        for (level in BoosterType.BASE_REPAIR.unlockLevel..lastLevel) {
            val price = boosterPrice(BoosterType.BASE_REPAIR, level)
            assertTrue("L$level tamir $price <= en ucuz rank $cheapestRank", price > cheapestRank)
            assertTrue(
                "L$level tamir $price >= 1 yildiz odulu ${levelReward(level, 1)}",
                price < levelReward(level, 1),
            )
        }
    }

    /**
     * YENI KURAL — Us Tamiri 2. kullanim (+7 can): CAN BASINA fiyat 1. kullanimdan ASLA dusuk
     * degil (eski reklam hakki bedavaydi; indirimli satis arbitraj acardi). `roundToTen` yalnizca
     * en fazla 5 coin'lik (toplamda 4 x 5 = 20) yukari/asagi kayma yaratabilir.
     */
    @Test
    fun repairSecondUsePerLifePriceIsNeverLowerThanTheFirstUse() {
        val lives1 = EconomyConfig.BASE_REPAIR_LIVES_FIRST_USE
        val lives2 = EconomyConfig.BASE_REPAIR_LIVES_SECOND_USE
        assertEquals(4, lives1)
        assertEquals(7, lives2)
        for (level in BoosterType.BASE_REPAIR.unlockLevel..lastLevel) {
            val p1 = boosterPrice(BoosterType.BASE_REPAIR, level, 1)
            val p2 = boosterPrice(BoosterType.BASE_REPAIR, level, 2)
            // p2 / 7 >= p1 / 4   <=>   p2 x 4 >= p1 x 7   (tamsayi, yuvarlama hatasiz)
            assertTrue("L$level: 2. kullanim $p2 ($lives2 can) 1. kullanimdan ($p1, $lives1 can) ucuz", p2 * lives1 >= p1 * lives2)
            assertTrue("L$level: 2. kullanim yuvarlamadan fazla pahali: $p2 vs $p1", p2 * lives1 - p1 * lives2 <= 20)
            assertTrue("L$level: 2. kullanim 1.'den pahali olmali", p2 > p1)
        }
    }

    @Test
    fun repairAmountsPerUseAndCappedByWhatWasLost() {
        // Duz can, orandan bagimsiz: 1. kullanim min(4, kayip), 2. kullanim min(7, kayip).
        assertEquals(0, baseRepairAmount(20, 20))
        assertEquals(0, baseRepairAmount(20, 20, useNumber = 2))
        assertEquals(3, baseRepairAmount(17, 20))                 // kayip 3 < 4
        assertEquals(3, baseRepairAmount(17, 20, useNumber = 2))  // kayip 3 < 7
        assertEquals(4, baseRepairAmount(4, 20))
        assertEquals(7, baseRepairAmount(4, 20, useNumber = 2))
        assertEquals(6, baseRepairAmount(14, 20, useNumber = 2))  // kayip 6 < 7
        assertEquals(7, baseRepairAmount(13, 20, useNumber = 2))  // kayip 7 = 7
        assertEquals(4, baseRepairAmount(1, 30))                  // maks candan BAGIMSIZ
        assertEquals(7, baseRepairAmount(1, 30, useNumber = 2))
    }

    // ---- Acil Tedarik: coin -> Tedarik (SAHIP KARARI, GDD D.4 geri alindi) -----------

    /** SAHIP KARARI: 2 coin = 1 Tedarik. Sabit ve seffaf; sabit degisirse burasi kirilir. */
    @Test
    fun emergencySupplyCostsTwoCoinPerSupplyAtEveryLevel() {
        assertEquals(2, EconomyConfig.EMERGENCY_SUPPLY_COIN_PER_SUPPLY)
        for (level in BoosterType.EMERGENCY_SUPPLY.unlockLevel..lastLevel) {
            assertEquals(
                "L$level",
                2 * emergencySupplyAmount(level),
                boosterPrice(BoosterType.EMERGENCY_SUPPLY, level),
            )
        }
    }

    /**
     * Fiyat < R(L,1): satin almak, bolumun 1-yildiz odulundan fazlasina mal olamaz (yoksa
     * dugme olu yatirim). Tum 2..55 bolumde.
     */
    @Test
    fun emergencySupplyPriceStaysBelowTheOneStarRewardAtEveryLevel() {
        for (level in BoosterType.EMERGENCY_SUPPLY.unlockLevel..lastLevel) {
            val price = boosterPrice(BoosterType.EMERGENCY_SUPPLY, level)
            val oneStar = levelReward(level, 1)
            assertTrue("L$level: acil tedarik $price >= 1-yildiz odulu $oneStar", price < oneStar)
        }
    }

    /**
     * Neden Acil Tedarik "fiyat >= en ucuz kalici rank (200)" tabanindan MUAF: L2'de R(2,1)=170,
     * yani `200 <= fiyat < R(L,1)` araligi BOS. Bu, muafiyetin keyfi degil YAPISAL oldugunun kaniti.
     */
    @Test
    fun emergencySupplyFloorCannotApply() {
        val cheapestRank = UpgradeLine.entries.minOf { it.costOfRank(1) }
        val unlock = BoosterType.EMERGENCY_SUPPLY.unlockLevel
        assertTrue(
            "L$unlock: R=${levelReward(unlock, 1)} > taban $cheapestRank — muafiyet gerekmiyor, taban uygulanmali",
            levelReward(unlock, 1) <= cheapestRank,
        )
        // ... ve bu yuzden Acil Tedarik'in erken bolumlerde fiyati tabanin altinda.
        assertTrue(boosterPrice(BoosterType.EMERGENCY_SUPPLY, unlock) < cheapestRank)
    }

    @Test
    fun emergencySupplyIsRoughlyOneBasicTowerAndNeverAnOpeningReplacement() {
        // Daha azi butona basmaya degmez, daha fazlasi bolum butcesini yeniden sisirir.
        for (level in BoosterType.EMERGENCY_SUPPLY.unlockLevel..lastLevel) {
            val amount = emergencySupplyAmount(level)
            val opening = startingSupplyFor(level)
            assertTrue("L$level acil tedarik $amount cok kucuk", amount >= 60)
            assertTrue("L$level acil tedarik $amount, acilis Tedariki $opening'i asiyor", amount <= opening)
        }
    }

    // =================================================================================
    // 3. Kaldirilan reklam katmani GERCEKTEN yok (derleme + yansima)
    // =================================================================================

    /**
     * Ustteki `when` DERLEME kapisidir: `else` yok, dolayisiyla karar tipi eklenirse/cikarilirsa
     * bu dosya derlenmez ve biri bu testi bilerek gunceller. Kaldirilan tiplere (AdLimitReached,
     * DailyAdLimitReached, PaidPathNotExhausted, PaidPathUnavailable) burada ATIF YAPILAMAZ.
     */
    private fun label(d: BoosterDecision): String = when (d) {
        is BoosterDecision.Allowed -> "Allowed"
        is BoosterDecision.Disabled -> "Disabled"
        is BoosterDecision.Locked -> "Locked"
        is BoosterDecision.PaidLimitReached -> "PaidLimitReached"
        is BoosterDecision.InsufficientSupply -> "InsufficientSupply"
        is BoosterDecision.InsufficientCoins -> "InsufficientCoins"
        is BoosterDecision.ReserveLocked -> "ReserveLocked"
        is BoosterDecision.Cooldown -> "Cooldown"
        is BoosterDecision.NoEffect -> "NoEffect"
    }

    private val adSurface = Regex("""(?i)viaAd|AD_ONLY|adUses|adViews|^ad[A-Z_]|Daily.*Ad|PaidPath|AdLimit|sharedUseCap|adPathIsPrimary|hasPaidPath""")

    private fun instanceFields(c: Class<*>): List<String> =
        c.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.map { it.name }

    @Test
    fun removedAdDecisionTypesAndFieldsNoLongerExist() {
        val decisionNames = BoosterDecision::class.java.declaredClasses.map { it.simpleName }.toSet()
        assertEquals(
            setOf(
                "Allowed", "Disabled", "Locked", "PaidLimitReached", "InsufficientSupply",
                "InsufficientCoins", "ReserveLocked", "Cooldown", "NoEffect",
            ),
            decisionNames,
        )
        assertEquals(setOf("SUPPLY", "COIN"), BoosterCurrency.entries.map { it.name }.toSet())

        val surface = listOf(
            BoosterState::class.java, BoosterType::class.java, BoosterActivation::class.java,
            BoosterDecision.Allowed::class.java,
        ).flatMap { c -> instanceFields(c).map { "${c.simpleName}.$it" } } +
            BoosterDecision::class.java.declaredClasses.map { it.simpleName }
        val leaks = surface.filter { adSurface.containsMatchIn(it.substringAfter('.')) }
        assertTrue("reklam yuzeyi kalmis: $leaks", leaks.isEmpty())

        // Allowed yalnizca (price, currency) tasir; label() cagrisi tam eslemeyi de calistirir.
        assertEquals(setOf("price", "currency"), instanceFields(BoosterDecision.Allowed::class.java).toSet())
        assertEquals("Allowed", label(BoosterDecision.Allowed(1, BoosterCurrency.COIN)))
    }

    // =================================================================================
    // 4. Limitler, siralama ve exploit kapaklari
    // =================================================================================

    /**
     * Her guclendirici KENDI beyan ettigi tavani sonuna kadar tuketir (her adimda fiyat =
     * `boosterPrice(tip, bolum, kullanim)`, para birimi COIN) ve sonrasini, bakiye milyon
     * olsa bile, `PaidLimitReached` ile reddeder. Toplam harcama = kullanim fiyatlari toplami.
     */
    @Test
    fun everyBoosterSaturatesItsPaidUsesAndThenRefuses() {
        BoosterType.entries.forEach { type ->
            var s = state(lastLevel)
            var wallet = openWallet(1_000_000)
            val start = wallet.coins
            var now = 0L
            val step = boosterCooldownMs(type) + 1L
            var expectedSpend = 0

            for (use in 1..type.paidUsesPerBattle) {
                val d = boosterAllowed(
                    s, type, wallet, supplyOnHand = 9_999, baseHealth = 5, enemiesOnField = 5, nowMs = now,
                )
                val price = boosterPrice(type, lastLevel, use)
                assertEquals("$type kullanim #$use", BoosterDecision.Allowed(price, BoosterCurrency.COIN), d)
                wallet = payForBooster(wallet, d)
                s = useBooster(s, type, d, nowMs = now)
                expectedSpend += price
                now += step
            }

            assertEquals("$type toplam kullanim", type.maxUsesPerBattle, s.usesOf(type))
            assertEquals(start - expectedSpend, wallet.coins)
            assertEquals(expectedSpend, wallet.lifetimeSpent)
            assertEquals(
                "$type doyduktan sonra hala acik",
                BoosterDecision.PaidLimitReached,
                boosterAllowed(
                    s, type, wallet, supplyOnHand = 1_000_000, baseHealth = 5, enemiesOnField = 5, nowMs = now,
                ),
            )
        }
    }

    /** Hak sayaci ve kayitli tamir, savasla birlikte yok olur: envanter/stoklama yok. */
    @Test
    fun boostersAreNotInventoryAndDoNotSurviveTheBattle() {
        var s = state(level = 10)
        val repair = boosterAllowed(s, BoosterType.BASE_REPAIR, richWallet, baseHealth = 5, nowMs = 0L)
        assertTrue(repair.isAllowed)
        s = useBooster(s, BoosterType.BASE_REPAIR, repair, nowMs = 0L, repairedHealth = 4)
        assertEquals(1, s.paidUsesOf(BoosterType.BASE_REPAIR))
        assertEquals(4, s.repairedHealth)

        val air = boosterAllowed(s, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 3, nowMs = 0L)
        assertTrue(air.isAllowed)
        s = useBooster(s, BoosterType.AIR_SUPPORT, air, nowMs = 0L)
        assertEquals(1, s.usesOf(BoosterType.AIR_SUPPORT))

        val next = BoosterState.startBattle(11)
        BoosterType.entries.forEach { assertEquals("$it", 0, next.usesOf(it)) }
        assertEquals(0, next.repairedHealth)
        assertTrue("bekleme sayaci da savasla birlikte olmeli", next.lastUseMs.isEmpty())
    }

    /**
     * Reddin HER turunde durum, cuzdan ve bekleme AYNEN kalir.
     * (Locked, Cooldown, NoEffect, InsufficientCoins, ReserveLocked, PaidLimitReached, Disabled.)
     */
    @Test
    fun rejectedActivationChangesNothing() {
        class Case(val name: String, val s: BoosterState, val wallet: PlayerWallet, val decision: BoosterDecision)

        val bought = BoosterState(
            level = 10,
            paidUses = mapOf(BoosterType.EMERGENCY_SUPPLY to 1),
            lastUseMs = mapOf(BoosterType.EMERGENCY_SUPPLY to 0L),
        )
        val airOnce = stateBeforeUse(BoosterType.AIR_SUPPORT, 10, 2)
            .copy(lastUseMs = mapOf(BoosterType.AIR_SUPPORT to 1_000L))
        val airPrice = boosterPrice(BoosterType.AIR_SUPPORT, 10)
        val poor = openWallet(1)
        val reserved = gateWallet(airPrice + 8) // bakiye yetiyor, rezerv (100) yetmiyor

        val cases = listOf(
            Case("Locked", state(3), richWallet,
                boosterAllowed(state(3), BoosterType.AIR_SUPPORT, richWallet)),
            Case("PaidLimitReached", bought, richWallet,
                boosterAllowed(bought, BoosterType.EMERGENCY_SUPPLY, richWallet)),
            Case("Cooldown", airOnce, richWallet,
                boosterAllowed(airOnce, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 4, nowMs = 2_000L)),
            Case("NoEffect", state(10), richWallet,
                boosterAllowed(state(10), BoosterType.BASE_REPAIR, richWallet, baseHealth = 20)),
            Case("InsufficientCoins", state(10), poor,
                boosterAllowed(state(10), BoosterType.AIR_SUPPORT, poor, enemiesOnField = 3)),
            Case("ReserveLocked", state(10), reserved,
                boosterAllowed(state(10), BoosterType.AIR_SUPPORT, reserved, enemiesOnField = 3)),
            Case("Disabled", state(10), richWallet, BoosterDecision.Disabled),
        )
        cases.forEach { c ->
            assertFalse("${c.name} karari izin vermemeli: ${c.decision}", c.decision.isAllowed)
            assertEquals("karar turu", c.name, label(c.decision))
            assertSame("${c.name}: durum degisti", c.s,
                useBooster(c.s, BoosterType.AIR_SUPPORT, c.decision, nowMs = 9_999L, repairedHealth = 4))
            assertSame("${c.name}: cuzdan degisti", c.wallet, payForBooster(c.wallet, c.decision))
        }
    }

    /** Bekleme SAVAS ICI iki kullanim arasindadir: Hava Destegi 45 sn, Us Tamiri 60 sn. */
    @Test
    fun cooldownBlocksBackToBackUsesOfTheSameBooster() {
        listOf(BoosterType.AIR_SUPPORT, BoosterType.BASE_REPAIR).forEach { type ->
            val cooldown = boosterCooldownMs(type)
            assertTrue("$type bekleme tanimli olmali", cooldown > 0L)
            var s = state(10)
            val first = boosterAllowed(s, type, richWallet, baseHealth = 5, enemiesOnField = 5, nowMs = 1_000L)
            assertTrue(first.isAllowed)
            s = useBooster(s, type, first, nowMs = 1_000L)
            assertEquals(1, s.usesOf(type))

            // Ikinci hak DURUYOR ama bekleme tutuyor.
            val tooSoon = boosterAllowed(s, type, richWallet, baseHealth = 5, enemiesOnField = 5, nowMs = 2_000L)
            assertEquals("$type", BoosterDecision.Cooldown(cooldown - 1_000L), tooSoon)
            assertEquals(
                "$type bekleme bittiginde tam aninda acilmali",
                BoosterDecision.Allowed(boosterPrice(type, 10, 2), BoosterCurrency.COIN),
                boosterAllowed(s, type, richWallet, baseHealth = 5, enemiesOnField = 5, nowMs = 1_000L + cooldown),
            )
        }
        // Acil Tedarik'te bekleme yok ama tek hak var; ikinci deneme tavana carpar.
        assertEquals(0L, boosterCooldownMs(BoosterType.EMERGENCY_SUPPLY))
    }

    @Test
    fun repairIsRefusedWhenThereIsNothingToRepairForEitherUse() {
        listOf(1, 2).forEach { use ->
            assertEquals(
                "kullanim #$use",
                BoosterDecision.NoEffect,
                boosterAllowed(
                    stateBeforeUse(BoosterType.BASE_REPAIR, 10, use), BoosterType.BASE_REPAIR, richWallet,
                    baseHealth = 20, maxBaseHealth = 20,
                ),
            )
        }
    }

    // ---- 4b. Hedefsiz Hava Destegi: geri alinamaz bos harcamanin ekonomi katmani kapisi -----

    @Test
    fun airSupportIsRefusedWhenThereAreNoTargets() {
        assertEquals(
            BoosterDecision.NoEffect,
            boosterAllowed(state(10), BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 0),
        )
    }

    @Test
    fun refusedAirSupportCostsNothingBurnsNoUseAndStartsNoCooldown() {
        val s = state(level = 10)
        val decision = boosterAllowed(s, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 0, nowMs = 5_000L)
        assertEquals(BoosterDecision.NoEffect, decision)
        assertFalse(decision.isAllowed)

        val after = useBooster(s, BoosterType.AIR_SUPPORT, decision, nowMs = 5_000L)
        assertSame("reddedilen kullanim durumu degistirmemeli", s, after)
        assertEquals(0, after.usesOf(BoosterType.AIR_SUPPORT))
        assertTrue("bekleme baslamamali", after.lastUseMs.isEmpty())
        assertSame("coin dusulmemeli", richWallet, payForBooster(richWallet, decision))

        // Hedef gelince AYNI ANDA izin cikar: bekleme gercekten islememis.
        assertTrue(
            boosterAllowed(after, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 1, nowMs = 5_000L).isAllowed
        )
    }

    @Test
    fun airSupportIsAllowedAsSoonAsOneEnemyIsOnTheField() {
        val d = boosterAllowed(state(10), BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 1)
        assertEquals(BoosterDecision.Allowed(boosterPrice(BoosterType.AIR_SUPPORT, 10), BoosterCurrency.COIN), d)
    }

    @Test
    fun noTargetGateCoversEveryAirSupportUseNotJustTheFirst() {
        var s = state(level = 10)
        val first = boosterAllowed(s, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 3, nowMs = 0L)
        assertTrue(first.isAllowed)
        s = useBooster(s, BoosterType.AIR_SUPPORT, first, nowMs = 0L)

        // 2. kullanim: bekleme bitti, hak duruyor — kapiyi tutan tek sey hedef.
        val late = EconomyConfig.AIR_SUPPORT_COOLDOWN_MS
        assertEquals(1, s.usesOf(BoosterType.AIR_SUPPORT))
        assertEquals(
            BoosterDecision.NoEffect,
            boosterAllowed(s, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 0, nowMs = late),
        )
        assertTrue(
            boosterAllowed(s, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 2, nowMs = late).isAllowed
        )
    }

    /**
     * KONTROL SIRASI SOZLESMESI (analytics + UI mesaji buna baglidir):
     * bolum kilidi -> kullanim tavani -> bekleme -> etki (NoEffect) -> odeme gucu -> rezerv.
     * Her halka bir sonrakini GOLGELER; sirayi bozan bir degisiklik burada kirilir.
     */
    @Test
    fun decisionOrderIsUnlockLimitCooldownEffectAffordabilityReserve() {
        val broke = openWallet(0)

        // 1. Kilit, her seyden once (cuzdan bos, saha bos olsa da).
        assertEquals(
            BoosterDecision.Locked(4),
            boosterAllowed(state(3), BoosterType.AIR_SUPPORT, broke, enemiesOnField = 0),
        )

        // 2. Kullanim tavani, beklemeden ONCE: hakki bitmis oyuncuya "bekle" denmez.
        var spent = state(10)
        var now = 0L
        repeat(BoosterType.AIR_SUPPORT.paidUsesPerBattle) {
            val d = boosterAllowed(spent, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 4, nowMs = now)
            assertTrue(d.isAllowed)
            spent = useBooster(spent, BoosterType.AIR_SUPPORT, d, nowMs = now)
            now += EconomyConfig.AIR_SUPPORT_COOLDOWN_MS
        }
        assertEquals(
            "tavan bekleme ve hedef durumundan bagimsiz",
            BoosterDecision.PaidLimitReached,
            boosterAllowed(spent, BoosterType.AIR_SUPPORT, broke, enemiesOnField = 0, nowMs = now - 1L),
        )

        // 3. Bekleme, etkiden ONCE.
        val onceUsed = stateBeforeUse(BoosterType.AIR_SUPPORT, 10, 2).copy(lastUseMs = mapOf(BoosterType.AIR_SUPPORT to 1_000L))
        assertTrue(
            boosterAllowed(onceUsed, BoosterType.AIR_SUPPORT, broke, enemiesOnField = 0, nowMs = 2_000L)
                is BoosterDecision.Cooldown
        )

        // 4. Etki, odeme gucunden ONCE: tam canli us + bos cuzdan -> NoEffect (InsufficientCoins DEGIL),
        //    yoksa oyuncuya "tek eksigin para" YANLIS bilgisi verilir.
        assertEquals(
            BoosterDecision.NoEffect,
            boosterAllowed(state(10), BoosterType.BASE_REPAIR, broke, baseHealth = 20, maxBaseHealth = 20),
        )
        assertEquals(
            BoosterDecision.NoEffect,
            boosterAllowed(state(10), BoosterType.AIR_SUPPORT, broke, enemiesOnField = 0),
        )

        // 5. Odeme gucu, rezervden ONCE: bakiye hic yetmiyorsa ReserveLocked degil InsufficientCoins.
        val price = boosterPrice(BoosterType.AIR_SUPPORT, 10)
        assertEquals(
            BoosterDecision.InsufficientCoins(price, shortfall = 1),
            boosterAllowed(state(10), BoosterType.AIR_SUPPORT, gateWallet(price - 1), enemiesOnField = 3),
        )
    }

    @Test
    fun enemyCountDoesNotAffectTheOtherBoosters() {
        assertEquals(
            BoosterDecision.NoEffect,
            boosterAllowed(state(10), BoosterType.BASE_REPAIR, richWallet, baseHealth = 20, maxBaseHealth = 20, enemiesOnField = 0),
        )
        assertTrue(
            "bos saha tamiri engellememeli",
            boosterAllowed(state(10), BoosterType.BASE_REPAIR, richWallet, baseHealth = 5, maxBaseHealth = 20, enemiesOnField = 0).isAllowed
        )
        assertTrue(
            "bos saha acil tedariki engellememeli",
            boosterAllowed(state(10), BoosterType.EMERGENCY_SUPPLY, richWallet, enemiesOnField = 0).isAllowed
        )
    }

    @Test
    fun unknownEnemyCountPreservesLegacyBehaviourExactly() {
        // Varsayilan -1: 0 gecerli ve anlamli ("saha bos"); varsayilan 0 olsaydi sahayi
        // bildirmeyen her cagiran sessizce reddedilirdi.
        assertEquals(-1, ENEMY_COUNT_UNKNOWN)
        val s = state(level = 10)
        val withTargets = boosterAllowed(s, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 7)
        val explicitUnknown = boosterAllowed(s, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = ENEMY_COUNT_UNKNOWN)
        val defaulted = boosterAllowed(s, BoosterType.AIR_SUPPORT, richWallet)
        assertEquals(withTargets, explicitUnknown)
        assertEquals(withTargets, defaulted)
        assertTrue(defaulted.isAllowed)
    }

    // =================================================================================
    // 5. Coin yolu — yetmeyen bakiye, Konuslanma Rezervi, tek sefer tahsilat
    // =================================================================================

    /**
     * Bakiye fiyattan TAM 1 coin eksikse: `InsufficientCoins(price, shortfall = 1)` ve cuzdan/durum
     * DEGISMEZ. Uc guclendirici, tum bolumler, tum kullanimlar. Tam fiyatta izin cikar.
     */
    @Test
    fun oneCoinShortIsInsufficientCoinsAndExactPriceIsAllowed() {
        var checked = 0
        everyPricedUse().forEach { (type, level, use) ->
            val price = boosterPrice(type, level, use)
            val short = openWallet(price - 1)
            val d = decide(type, level, use, short)
            assertEquals("$type L$level #$use", BoosterDecision.InsufficientCoins(price, 1), d)
            assertSame(short, payForBooster(short, d))
            val s = stateBeforeUse(type, level, use)
            assertSame(s, useBooster(s, type, d, nowMs = 1L))

            assertEquals(
                "$type L$level #$use tam fiyatla",
                BoosterDecision.Allowed(price, BoosterCurrency.COIN),
                decide(type, level, use, openWallet(price)),
            )
            checked++
        }
        assertTrue("tarama bos: $checked", checked >= 3 * 40)
    }

    /**
     * Bakiye fiyati karsiliyor ama REZERV karsilamiyorsa `ReserveLocked(price, reserve, shortfall)`;
     * rezerv kadar fazlasi varsa izin. Uc guclendirici, tum bolumler, tum kullanimlar.
     */
    @Test
    fun reserveLockedWhenBalanceSufficesButReserveDoesNot() {
        val reserve = reserveFor(gateWallet(0))
        assertEquals("gate cuzdani rezervi 100 olmali (lockCost(7))", 100, reserve)
        everyPricedUse().forEach { (type, level, use) ->
            val price = boosterPrice(type, level, use)

            // Yalniz fiyat kadar coin: bakiye yetiyor, rezerv tamamen ihlal.
            val exact = gateWallet(price)
            val locked = decide(type, level, use, exact)
            assertEquals("$type L$level #$use", BoosterDecision.ReserveLocked(price, reserve, reserve), locked)
            assertSame("rezerv reddi cuzdani degistirdi", exact, payForBooster(exact, locked))

            // Rezervden TAM 1 coin eksik kalan alim.
            assertEquals(
                "$type L$level #$use",
                BoosterDecision.ReserveLocked(price, reserve, 1),
                decide(type, level, use, gateWallet(price + reserve - 1)),
            )
            // Rezerv korunuyorsa izin.
            assertEquals(
                "$type L$level #$use",
                BoosterDecision.Allowed(price, BoosterCurrency.COIN),
                decide(type, level, use, gateWallet(price + reserve)),
            )
            // Bakiye fiyatin altindaysa rezerv devreye GIRMEZ (analytics: reserve_lock_blocked).
            assertEquals(
                BoosterDecision.InsufficientCoins(price, 1),
                decide(type, level, use, gateWallet(price - 1)),
            )
        }
    }

    @Test
    fun reserveCoinsCanNeverBeSpentOnABooster() {
        // Bolum 7 kapisinin hemen oncesi: 1..6 temizlenmis, rezerv tam bedel (100).
        val wallet = gateWallet(250)
        assertEquals(100, reserveFor(wallet))
        val price = boosterPrice(BoosterType.BASE_REPAIR, 7) // 240
        val decision = boosterAllowed(state(7), BoosterType.BASE_REPAIR, wallet, baseHealth = 5)
        // 250 - 240 = 10 < 100 -> 90 coin eksik.
        assertEquals(BoosterDecision.ReserveLocked(price, 100, 100 - (250 - price)), decision)
        assertSame("reddedilen alim cuzdani degistirmemeli", wallet, payForBooster(wallet, decision))
        assertFalse(boosterUnlocked(BoosterType.BASE_REPAIR, 6))
    }

    @Test
    fun insufficientCoinsIsDistinctFromReserveLock() {
        val poor = PlayerWallet(coins = 50, unlockedLevels = (1..6).toSet())
        val d = boosterAllowed(state(7), BoosterType.BASE_REPAIR, poor, baseHealth = 5)
        assertTrue("bakiye hic yetmiyor -> InsufficientCoins", d is BoosterDecision.InsufficientCoins)
        assertEquals(240 - 50, (d as BoosterDecision.InsufficientCoins).shortfall)
    }

    /**
     * Coin TAM BIR KEZ, YALNIZCA coin fiyatli guclendiricide duser; `lifetimeSpent` artar,
     * `lifetimeEarned` ASLA degismez (tahsilat gelir degildir).
     */
    @Test
    fun coinPathDebitsExactlyOncePerDecisionAndNeverEarns() {
        everyPricedUse().forEach { (type, level, use) ->
            val wallet = PlayerWallet(
                coins = 1_000_000, unlockedLevels = (1..lastLevel).toSet(),
                lifetimeEarned = 5_000, lifetimeSpent = 300,
            )
            val d = decide(type, level, use, wallet)
            assertTrue("$type L$level #$use", d.isAllowed)
            val price = (d as BoosterDecision.Allowed).price
            val after = payForBooster(wallet, d)
            assertEquals(1_000_000 - price, after.coins)
            assertEquals(300 + price, after.lifetimeSpent)
            assertEquals("tahsilat lifetimeEarned'i degistirdi", 5_000, after.lifetimeEarned)
            assertEquals(wallet.unlockedLevels, after.unlockedLevels)
        }
    }

    // ---- Coin <-> Tedarik: YENI ACIK KURAL --------------------------------------------

    /**
     * ESKI KURAL (GDD D.4): "coin hicbir guclendiricide Tedarik'e donusmez". ARTIK GECERSIZ:
     * Acil Tedarik coin fiyatlidir (2 coin = 1 Tedarik, savas basina 1 kez, L2, rezerv korumali).
     *
     * YENI KURAL, uc parca:
     *  a) Tedarik BOLLUGU coin eksigini kapatmaz (hicbir tip Tedarikle satin alinmaz);
     *  b) Acil Tedarik'in coin bedeli, verdigi Tedarigin TAM 2 kati ve savas basina bir kez;
     *  c) hicbir guclendirici coin URETMEZ (ters yon kapali).
     */
    @Test
    fun coinBuysSupplyOnlyThroughEmergencySupplyAtTheFixedRate() {
        val brokeCoins = openWallet(0)

        // (a) Tedarik ne kadar bol olursa olsun coin eksigi kapanmaz.
        BoosterType.entries.forEach { type ->
            val level = maxOf(10, type.unlockLevel)
            val d = boosterAllowed(
                state(level), type, brokeCoins, supplyOnHand = 1_000_000, baseHealth = 5, enemiesOnField = 5,
            )
            assertEquals(
                "$type: Tedarik bollugu coin eksigini kapatti",
                BoosterDecision.InsufficientCoins(boosterPrice(type, level), boosterPrice(type, level)),
                d,
            )
        }

        // (b) Sabit kur: fiyat / verilen Tedarik = 2, her bolumde; tek kullanim.
        for (level in BoosterType.EMERGENCY_SUPPLY.unlockLevel..lastLevel) {
            val price = boosterPrice(BoosterType.EMERGENCY_SUPPLY, level)
            assertEquals("L$level kur", 2.0, price.toDouble() / emergencySupplyAmount(level), 0.0)
        }
        assertEquals(1, BoosterType.EMERGENCY_SUPPLY.paidUsesPerBattle)

        // (c) Ters yon: hicbir karar cuzdani buyutmez.
        everyPricedUse().forEach { (type, level, use) ->
            val d = decide(type, level, use, richWallet)
            assertTrue(
                "$type L$level #$use coin bakiyesini ARTIRDI",
                payForBooster(richWallet, d).coins <= richWallet.coins,
            )
        }
    }

    // =================================================================================
    // 6. PAY-TO-WIN OLMAMA
    // =================================================================================

    @Test
    fun airSupportCanNeverClearAWaveByItself() {
        assertTrue(
            "hava destegi hasar orani 1,0'a esit/buyuk olamaz",
            EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION < 1.0,
        )
        assertTrue(EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION > 0.0)
    }

    /**
     * Pay-to-win kalkani SAVAS BASINA TOPLAM uzerinde: `maxUsesPerBattle x oran < 1,0`.
     * Kullanim sayisi ENUM'dan okunur (literal degil): biri 3. kullanimi acarsa (3 x 0,45 = 1,35)
     * bu test, sayi kimin elinden gelirse gelsin, kirilir.
     */
    @Test
    fun allAirSupportUsesInOneBattleStillCannotKillAnything() {
        val uses = BoosterType.AIR_SUPPORT.maxUsesPerBattle
        val total = uses * EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION
        assertTrue(
            "savas basina toplam hava destegi hasari maks canin %.0f%%'i".format(total * 100),
            total < 1.0,
        )
        // Son vurusun kulelerden gelmesi icin anlamli bir pay kalmali (>= %10).
        // EPSILON KASITLI: 2 x 0,45 ikili tabanda tam 0,9 etmez; esik %10'un kendisi.
        assertTrue("kalan can payi cok ince: %.4f".format(1.0 - total), 1.0 - total >= 0.10 - 1e-9)

        // Kural gercekten kullanilabilir en fazla kullanimi olcuyor: karar zinciri de ucuncu
        // kullanima IZIN VERMEZ (parayla bile).
        var s = state(10)
        var now = 0L
        var granted = 0
        repeat(uses + 3) {
            val d = boosterAllowed(s, BoosterType.AIR_SUPPORT, richWallet, enemiesOnField = 5, nowMs = now)
            if (d.isAllowed) {
                granted++
                s = useBooster(s, BoosterType.AIR_SUPPORT, d, nowMs = now)
            }
            now += EconomyConfig.AIR_SUPPORT_COOLDOWN_MS + 1L
        }
        assertEquals("karar zinciri tavandan fazla vurus verdi", uses, granted)
        assertTrue(granted * EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION < 1.0)
    }

    /**
     * Hasar ORAN (sabit sayi degil): dusman cani olceklenince kurtarma degeri kendiliginden
     * korunur; zirhtan da bagimsiz.
     */
    @Test
    fun airSupportRescueValueScalesWithEnemyHealthInsteadOfDecaying() {
        val facts = GameConfigCampaignFacts
        val infantry = facts.enemyMaxHp.getValue("INFANTRY")
        val tank = facts.enemyMaxHp.getValue("TANK")
        val fraction = EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION
        val uses = BoosterType.AIR_SUPPORT.maxUsesPerBattle

        assertEquals(82.0, infantry, 0.5)
        assertEquals(638.0, tank, 0.5)

        assertTrue("piyade tek vurusta olmemeli", fraction * infantry < infantry)
        assertTrue("silinen can hissedilir olmali", fraction >= 0.30)

        val tankLeftAfterAllUses = tank * (1.0 - uses * fraction)
        assertTrue(
            "hava destegi sonrasi tankta %.0f can kaliyor — kurtarma hissi yok".format(tankLeftAfterAllUses),
            tankLeftAfterAllUses in 1.0..(tank * 0.25),
        )
    }

    /** Fiyat marji buyur: odul dogrusal artarken tamir de artar, arbitraj penceresi acilmaz. */
    @Test
    fun repairPriceKeepsAGrowingMarginOverTheBiggestStarJump() {
        for (level in BoosterType.BASE_REPAIR.unlockLevel..lastLevel) {
            val jump = levelReward(level, 3) - levelReward(level, 1)
            val price = boosterPrice(BoosterType.BASE_REPAIR, level)
            assertTrue("L$level: tamir $price, 1->3 yildiz farki $jump", price > jump)
        }
        val marginAtUnlock = boosterPrice(BoosterType.BASE_REPAIR, 7) -
            (levelReward(7, 3) - levelReward(7, 1))
        val marginAtL22 = boosterPrice(BoosterType.BASE_REPAIR, 22) -
            (levelReward(22, 3) - levelReward(22, 1))
        assertEquals(50, marginAtUnlock)  // 240 - (510 - 320)
        assertEquals(80, marginAtL22)     // 540 - (1230 - 770)
        assertTrue("arbitraj marjini daraliyor", marginAtL22 > marginAtUnlock)
    }

    /**
     * ARBITRAJ: "tamir et -> yildiz atla -> tamirden fazla coin kazan". Geri verilen HER can
     * yildiz hesabindan dusulur; iki kullanimin ikisi de ve her kayip miktari icin.
     */
    @Test
    fun baseRepairIsStarNeutralForEveryUseSoItCanNeverBeCoinPositive() {
        val maxLives = 20
        val wallet = PlayerWallet(unlockedLevels = (1..22).toSet())
        for (use in 1..BoosterType.BASE_REPAIR.paidUsesPerBattle) {
            for (health in 1 until maxLives) {
                val repaired = baseRepairAmount(health, maxLives, useNumber = use)
                assertTrue("tamir uygulanmadi: can=$health use=$use", repaired > 0)
                val effective = effectiveStarHealth(health + repaired, repaired)

                assertEquals("tamir (use=$use, can=$health) etkin cani degistirmemeli", health, effective)
                assertEquals(
                    "tamir (use=$use, can=$health) yildizi artirmamali",
                    starsFor(health, maxLives), starsFor(effective, maxLives),
                )
                assertEquals(
                    "tamir (use=$use, can=$health) odulu ARTIRMAMALI",
                    resolveLevelClear(wallet, 10, health, maxLives).total,
                    resolveLevelClear(wallet, 10, effective, maxLives).total,
                )
            }
        }
    }

    /**
     * Iki tamir birikir ve savas sonu can hesabi HALA notr: etkin can, savas boyunca gorulen
     * en kotu durumu ASLA asmaz (yildiz hayatta kalma degil, en kotu an ile verilir).
     */
    @Test
    fun cumulativeRepairsNeverRaiseTheStarHealthAboveTheWorstMomentSeen() {
        val maxLives = 20
        var s = state(10)
        var health = 10
        val seen = mutableListOf(health)

        val r1 = baseRepairAmount(health, maxLives, 1)
        val d1 = boosterAllowed(s, BoosterType.BASE_REPAIR, richWallet, baseHealth = health, nowMs = 0L)
        s = useBooster(s, BoosterType.BASE_REPAIR, d1, nowMs = 0L, repairedHealth = r1)
        health += r1; seen += health
        health -= 6; seen += health                                    // sizma devam ediyor

        val r2 = baseRepairAmount(health, maxLives, 2)
        val d2 = boosterAllowed(s, BoosterType.BASE_REPAIR, richWallet, baseHealth = health, nowMs = EconomyConfig.BASE_REPAIR_COOLDOWN_MS)
        assertTrue(d2.isAllowed)
        s = useBooster(s, BoosterType.BASE_REPAIR, d2, nowMs = EconomyConfig.BASE_REPAIR_COOLDOWN_MS, repairedHealth = r2)
        health += r2; seen += health

        assertEquals("iki tamir birikmeli", r1 + r2, s.repairedHealth)
        assertEquals(
            "2 kullanim sonrasi 3. tamir yok",
            BoosterDecision.PaidLimitReached,
            boosterAllowed(s, BoosterType.BASE_REPAIR, richWallet, baseHealth = 5, nowMs = Long.MAX_VALUE / 2),
        )
        val effective = effectiveStarHealth(health, s)
        assertTrue("etkin can $effective > gorulen en kotu ${seen.min()}", effective <= seen.min())
        assertTrue(starsFor(effective, maxLives) <= starsFor(seen.min(), maxLives))
    }

    @Test
    fun repairCanNeverManufactureAPerfectDefenseMedal() {
        val maxLives = 20
        val restoredBoth = baseRepairAmount(13, maxLives, 1) + baseRepairAmount(9, maxLives, 2)
        assertTrue(restoredBoth > 0)
        assertFalse(isPerfectDefense(effectiveStarHealth(maxLives, restoredBoth), maxLives))
        assertTrue(isPerfectDefense(20, maxLives))
    }

    /** Ikinci kemer: yildiz notrlugu kalksa bile fiyat tek basina arbitraji zararli tutar. */
    @Test
    fun repairPriceExceedsEveryStarJumpItCouldEverBuyForBothUses() {
        for (level in BoosterType.BASE_REPAIR.unlockLevel..lastLevel) {
            val maxJump = levelReward(level, 3) - levelReward(level, 1)
            for (use in 1..BoosterType.BASE_REPAIR.paidUsesPerBattle) {
                val price = boosterPrice(BoosterType.BASE_REPAIR, level, use)
                assertTrue("L$level #$use: tamir $price < olasi en buyuk yildiz farki $maxJump", price >= maxJump)
            }
        }
    }

    @Test
    fun aPlayerWhoNeverTouchesABoosterStillFinishesTheCampaign() {
        // Guclendiriciler zorunlu degildir: bir tanesini bile kullanmayan oyuncu kampanyayi bitirir.
        var wallet = PlayerWallet()
        for (level in 1..lastLevel) {
            if (level >= EconomyConfig.FIRST_PAID_LEVEL) {
                assertTrue(
                    "L$level: guclendiricisiz oyuncu kapiyi acamadi (bakiye ${wallet.coins}, bedel ${lockCost(level)})",
                    canUnlock(wallet, level),
                )
                wallet = applyUnlock(wallet, level)
            }
            // En kotu senaryo: her bolum 1 yildiz, gorev yok, guclendirici yok.
            val result = resolveLevelClear(wallet, level, livesLeft = 6, maxLives = 20)
            assertEquals(1, result.stars)
            wallet = applyLevelClear(wallet, result)
        }
        assertEquals(lastLevel, wallet.clearedLevels.size)
        assertEquals(0, autoGrantShortfall(wallet))
    }

    // =================================================================================
    // 6b. SOFT-LOCK YOK — guclendirici harcamasi kampanyayi kilitleyemez (4 guclendirici)
    // =================================================================================

    /**
     * ACGOZLU OYUNCU: her bolumu 1-yildiz benzeri kotu sonucla bitirir, ardindan her
     * guclendiriciyi REZERV IZIN VERDIGI SURECE tekrar tekrar satin alir (kullanim sayaci
     * bilerek ilerletilmez: en sert senaryo, "tavan yok"). Her satin almadan sonra kapiyi
     * acacak coin kalmis olmali: `autoGrantShortfall == 0`.
     *
     * Ayni dongu Us Tamiri (1. kullanim), Us Tamiri 2. kullanim, Hava Destegi ve Acil Tedarik
     * icin kosar. Testin duyarli oldugu iki sayacla kanitlanir: satin alma GERCEKTEN oldu ve
     * dongu bazi bolumlerde rezerv yuzunden DURDU (yoksa dongu hicbir seyi olcmuyor olurdu).
     */
    private data class GreedyOutcome(val purchases: Int, val stoppedByReserve: Int, val stoppedByCoins: Int)

    private fun greedyCampaignNeverSoftLocks(
        type: BoosterType,
        useNumber: Int,
        livesLeftPerClear: Int,
    ): GreedyOutcome {
        var wallet = PlayerWallet()
        var purchases = 0
        var byReserve = 0
        var byCoins = 0
        for (level in 1..lastLevel) {
            if (level >= EconomyConfig.FIRST_PAID_LEVEL) {
                assertTrue("$type #$useNumber L$level acilamadi", canUnlock(wallet, level))
                wallet = applyUnlock(wallet, level)
            }
            wallet = applyLevelClear(wallet, resolveLevelClear(wallet, level, livesLeftPerClear, 20))

            if (level < type.unlockLevel) continue
            var guard = 0
            while (guard++ < 100) {
                val d = decide(type, level, useNumber, wallet)
                if (d is BoosterDecision.ReserveLocked) byReserve++
                if (d is BoosterDecision.InsufficientCoins) byCoins++
                if (!d.isAllowed) break
                wallet = payForBooster(wallet, d)
                purchases++
                assertEquals(
                    "$type #$useNumber L$level satin alma sonrasi soft-lock: rezerv yenmis",
                    0, autoGrantShortfall(wallet),
                )
            }
            assertEquals("$type #$useNumber L$level sonu soft-lock", 0, autoGrantShortfall(wallet))
        }
        assertEquals(lastLevel, wallet.clearedLevels.size)
        return GreedyOutcome(purchases, byReserve, byCoins)
    }

    @Test
    fun repairSpendingCanNeverSoftLockTheCampaign() {
        val o = greedyCampaignNeverSoftLocks(BoosterType.BASE_REPAIR, 1, livesLeftPerClear = 12)
        assertTrue("satin alma olmadi (kanit bos): $o", o.purchases > 0)
        assertTrue("rezerv hic devreye girmedi (kanit bos): $o", o.stoppedByReserve > 0)
    }

    @Test
    fun repairSecondUseSpendingCanNeverSoftLockTheCampaign() {
        val o = greedyCampaignNeverSoftLocks(BoosterType.BASE_REPAIR, 2, livesLeftPerClear = 12)
        assertTrue("satin alma olmadi (kanit bos): $o", o.purchases > 0)
        assertTrue("rezerv hic devreye girmedi (kanit bos): $o", o.stoppedByReserve > 0)
    }

    @Test
    fun airSupportSpendingCanNeverSoftLockTheCampaign() {
        val o = greedyCampaignNeverSoftLocks(BoosterType.AIR_SUPPORT, 1, livesLeftPerClear = 12)
        assertTrue("satin alma olmadi (kanit bos): $o", o.purchases > 0)
        assertTrue("rezerv hic devreye girmedi (kanit bos): $o", o.stoppedByReserve > 0)
    }

    @Test
    fun emergencySupplySpendingCanNeverSoftLockTheCampaign() {
        val o = greedyCampaignNeverSoftLocks(BoosterType.EMERGENCY_SUPPLY, 1, livesLeftPerClear = 12)
        assertTrue("satin alma olmadi (kanit bos): $o", o.purchases > 0)
        assertTrue("rezerv hic devreye girmedi (kanit bos): $o", o.stoppedByReserve > 0)
    }

    /**
     * Gercekci acgozlu: ayni savasta uc guclendiricinin TUM haklarini (Acil Tedarik x1, Hava
     * Destegi x2, Us Tamiri x2) sirayla satin alir; her satin almadan sonra kapi acik kalmali.
     */
    @Test
    fun buyingEveryRightInEveryBattleCanNeverSoftLockTheCampaign() {
        var wallet = PlayerWallet()
        var purchases = 0
        for (level in 1..lastLevel) {
            if (level >= EconomyConfig.FIRST_PAID_LEVEL) {
                wallet = applyUnlock(wallet, level)
            }
            wallet = applyLevelClear(wallet, resolveLevelClear(wallet, level, 12, 20))

            var s = state(level)
            var now = 0L
            for (type in BoosterType.entries) {
                repeat(type.paidUsesPerBattle) {
                    val d = boosterAllowed(s, type, wallet, baseHealth = 5, enemiesOnField = 5, nowMs = now)
                    if (d.isAllowed) {
                        wallet = payForBooster(wallet, d)
                        s = useBooster(s, type, d, nowMs = now)
                        purchases++
                        assertEquals("L$level $type sonrasi soft-lock", 0, autoGrantShortfall(wallet))
                    }
                    now += 100_000L
                }
            }
        }
        assertTrue("satin alma olmadi", purchases > 0)
        assertEquals(lastLevel, wallet.clearedLevels.size)
    }

    // =================================================================================
    // 7. SIKILASTIRMA GERI ALINMAZ (ECONOMY_SPEC A.4) ve coin cikis tavani
    // =================================================================================

    /**
     * SERT KURAL: sikilastirilmis butce + coinle alinabilen TUM ek Tedarik, bugunku (fazla bol)
     * butcenin altinda kalmali. `maxBoosterSupplyInjection` bir zamanlar "reklam sayisi" ile
     * carpiliyordu; artik Acil Tedarik'in tek coin-kullanimindan turetilir.
     *
     * NON-VACUOUS: enjeksiyon L1'de 0, L2-6'da tam `emergencySupplyAmount` (> 0) olmali;
     * yoksa `<= legacy` her zaman gecerdi.
     */
    @Test
    fun coinBoughtSupplyStaysBelowTodaysOverflushEconomyInTheEarlyLevels() {
        assertEquals("L1'de guclendirici yok", 0, maxBoosterSupplyInjection(1))
        for (level in 1..6) {
            val tightened = startingSupplyFor(level) + waveSupplyIncome(level)
            val injection = maxBoosterSupplyInjection(level)
            val expected = if (level >= BoosterType.EMERGENCY_SUPPLY.unlockLevel) {
                emergencySupplyAmount(level) * BoosterType.EMERGENCY_SUPPLY.paidUsesPerBattle
            } else {
                0
            }
            assertEquals("L$level enjeksiyon formulu", expected, injection)
            if (level >= BoosterType.EMERGENCY_SUPPLY.unlockLevel) {
                assertTrue("L$level enjeksiyon 0 — test bos gecerdi", injection > 0)
            }
            val withCoins = tightened + injection
            val legacy = LEGACY_STARTING_SUPPLY + legacyWaveSupplyIncome(level)
            assertTrue(
                "L$level: coinli butce $withCoins, eski bol butce $legacy — sikilastirma geri alindi",
                withCoins <= legacy,
            )
            assertTrue("L$level: paylasim payi kalmamis", legacy - withCoins > 0)
        }
    }

    /** Enjeksiyon SAVAS BASINA tavanli: en fazla 1 kullanim x miktar, tum kampanya boyunca. */
    @Test
    fun supplyInjectionIsBoundedByTheSingleEmergencyUse() {
        for (level in 1..lastLevel) {
            val expected = if (level >= BoosterType.EMERGENCY_SUPPLY.unlockLevel) {
                emergencySupplyAmount(level) * EconomyConfig.EMERGENCY_SUPPLY_USES_PER_BATTLE
            } else {
                0
            }
            assertEquals("L$level", expected, maxBoosterSupplyInjection(level))
            assertTrue(
                "L$level: enjeksiyon acilis Tedarikini asiyor",
                level < BoosterType.EMERGENCY_SUPPLY.unlockLevel ||
                    maxBoosterSupplyInjection(level) <= startingSupplyFor(level),
            )
        }
    }

    /**
     * `maxBoosterCoinSpend` kural olarak: acik COIN kalemlerinin TUM kullanimlarinin
     * `boosterPrice` toplami. Elle dogrulanmis capalar (aritmetik yorumda):
     *   L1  = 0                      (guclendirici yok)
     *   L2  = 140                    (Acil Tedarik: 2 x 70)
     *   L3  = 140                    (amount(3) = 70)
     *   L4  = 160 + 2 x 200 = 560    (Acil 2x80 + Hava 2 kullanim x 200)
     *   L6  = 180 + 2 x 244 = 668
     *   L7  = 200 + 2 x 266 + (240 + 420) = 1392
     *   L55 = 760 + 2 x 1322 + (1200 + 2100) = 6704
     */
    @Test
    fun maxBoosterCoinSpendIsTheSumOfEveryUnlockedUsePrice() {
        for (level in 1..lastLevel) {
            val expected = BoosterType.entries
                .filter { level >= it.unlockLevel }
                .sumOf { type -> (1..type.paidUsesPerBattle).sumOf { use -> boosterPrice(type, level, use) } }
            assertEquals("L$level", expected, maxBoosterCoinSpend(level))
        }
        assertEquals(0, maxBoosterCoinSpend(1))
        assertEquals(140, maxBoosterCoinSpend(2))
        assertEquals(140, maxBoosterCoinSpend(3))
        assertEquals(560, maxBoosterCoinSpend(4))
        assertEquals(668, maxBoosterCoinSpend(6))
        assertEquals(1_392, maxBoosterCoinSpend(7))
        assertEquals(6_704, maxBoosterCoinSpend(55))
        // Yeni bir kalem acildikca tavan ASLA duserek gitmez.
        for (level in 2..lastLevel) {
            assertTrue("L$level", maxBoosterCoinSpend(level) >= maxBoosterCoinSpend(level - 1))
        }
    }

    // =================================================================================
    // 8. Gercek CampaignProgressImpl uzerinden: bir aktivasyon tam bir kez tahsil eder
    // =================================================================================

    private class FixedClock : ClockProvider {
        override fun sample(): ClockSample = ClockSample(
            epochDay = 20_500L,
            wallClockMs = 20_500L * EconomyConfig.MS_PER_DAY,
            elapsedRealtimeMs = 1_000L,
            bootId = 1L,
        )
    }

    private class Env(wallet: PlayerWallet, level: Int) {
        val save = SaveManager(InMemoryKeyValueStore()).also { it.saveWallet(wallet) }
        val progress = CampaignProgressImpl(save, FixedClock())
        init { progress.beginBattle(level) }
        val wallet: PlayerWallet get() = save.loadWallet()
    }

    @Test
    fun activationChargesExactlyTheDeclaredCoinPriceOncePerRight() {
        val level = 10
        BoosterType.entries.forEach { type ->
            val env = Env(openWallet(100_000), level)
            var now = 0L
            var spentSoFar = 0
            repeat(type.paidUsesPerBattle) { i ->
                val act = env.progress.activateBooster(type, baseHealth = 5, enemiesOnField = 5, nowMs = now)
                val price = boosterPrice(type, level, i + 1)
                assertTrue("$type #${i + 1} reddedildi: ${act.decision}", act.applied)
                assertEquals("$type #${i + 1} coinsSpent", price, act.coinsSpent)
                assertEquals("$type #${i + 1} Tedarik dusmemeli", 0, act.supplyCharged)
                spentSoFar += price
                assertEquals("$type #${i + 1} bakiye", 100_000 - spentSoFar, env.progress.coins)
                assertEquals("$type #${i + 1} lifetimeSpent", spentSoFar, env.wallet.lifetimeSpent)
                now += boosterCooldownMs(type) + 1L
            }
            // Tavan: ek deneme hicbir sey degistirmez.
            val stateBefore = env.progress.boosterState
            val denied = env.progress.activateBooster(type, baseHealth = 5, enemiesOnField = 5, nowMs = now)
            assertEquals("$type", BoosterDecision.PaidLimitReached, denied.decision)
            assertEquals(0, denied.coinsSpent)
            assertEquals(100_000 - spentSoFar, env.progress.coins)
            assertSame(stateBefore, env.progress.boosterState)
        }
    }

    @Test
    fun emergencySupplyGrantsExactlyWhatItsPriceBuysAndOthersGrantNoSupply() {
        val level = 10
        val env = Env(openWallet(10_000), level)
        val es = env.progress.activateBooster(BoosterType.EMERGENCY_SUPPLY)
        assertTrue(es.applied)
        assertEquals(emergencySupplyAmount(level), es.supplyGranted)
        assertEquals("kur: 2 coin = 1 Tedarik", 2 * es.supplyGranted, es.coinsSpent)

        val air = env.progress.activateBooster(BoosterType.AIR_SUPPORT, enemiesOnField = 4)
        val repair = env.progress.activateBooster(BoosterType.BASE_REPAIR, baseHealth = 5)
        listOf(air, repair).forEach {
            assertTrue("${it.type} reddedildi: ${it.decision}", it.applied)
            assertEquals("${it.type} Tedarik VERMEMELI", 0, it.supplyGranted)
            assertEquals("${it.type} Tedarik HARCAMAMALI", 0, it.supplyCharged)
        }
        assertEquals(EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION, air.airSupportDamageFraction, 0.0)
        assertEquals(EconomyConfig.BASE_REPAIR_LIVES_FIRST_USE, repair.healthRestored)
    }

    @Test
    fun repairSecondActivationRestoresSevenAndChargesTheSecondUsePrice() {
        val level = 10
        val env = Env(openWallet(10_000), level)
        val first = env.progress.activateBooster(BoosterType.BASE_REPAIR, baseHealth = 5, nowMs = 0L)
        assertEquals(4, first.healthRestored)
        val second = env.progress.activateBooster(
            BoosterType.BASE_REPAIR, baseHealth = 5, nowMs = EconomyConfig.BASE_REPAIR_COOLDOWN_MS,
        )
        assertEquals(7, second.healthRestored)
        assertEquals(boosterPrice(BoosterType.BASE_REPAIR, level, 2), second.coinsSpent)
        assertEquals(11, env.progress.boosterState?.repairedHealth)
        val third = env.progress.activateBooster(BoosterType.BASE_REPAIR, baseHealth = 5, nowMs = Long.MAX_VALUE / 4)
        assertEquals(BoosterDecision.PaidLimitReached, third.decision)
        assertEquals(11, env.progress.boosterState?.repairedHealth)
    }

    @Test
    fun rejectedActivationsChangeNoWalletNoUsesNoCooldown() {
        class Case(val expected: String, val env: Env, val act: (Env) -> BoosterActivation)

        val airPrice = boosterPrice(BoosterType.AIR_SUPPORT, 10)
        val cases = listOf(
            Case("InsufficientCoins", Env(openWallet(1), 10)) {
                it.progress.activateBooster(BoosterType.AIR_SUPPORT, enemiesOnField = 3)
            },
            Case("ReserveLocked", Env(gateWallet(airPrice + 8), 10)) {
                it.progress.activateBooster(BoosterType.AIR_SUPPORT, enemiesOnField = 3)
            },
            Case("NoEffect", Env(openWallet(10_000), 10)) {
                it.progress.activateBooster(BoosterType.BASE_REPAIR, baseHealth = 20)
            },
            Case("Locked", Env(openWallet(10_000), 3)) {
                it.progress.activateBooster(BoosterType.AIR_SUPPORT, enemiesOnField = 3)
            },
        )
        cases.forEach { c ->
            val walletBefore = c.env.wallet
            val stateBefore = c.env.progress.boosterState
            val result = c.act(c.env)
            assertFalse("${c.expected} uygulandi", result.applied)
            assertEquals("karar turu", c.expected, label(result.decision))
            assertEquals("${c.expected} coinsSpent", 0, result.coinsSpent)
            assertEquals("${c.expected} cuzdan", walletBefore, c.env.wallet)
            assertSame("${c.expected} durum", stateBefore, c.env.progress.boosterState)
        }
    }

    @Test
    fun activatingWithoutABattleIsDisabledAndFree() {
        val save = SaveManager(InMemoryKeyValueStore()).also { it.saveWallet(openWallet(10_000)) }
        val progress = CampaignProgressImpl(save, FixedClock())
        val act = progress.activateBooster(BoosterType.EMERGENCY_SUPPLY)
        assertEquals(BoosterDecision.Disabled, act.decision)
        assertEquals(10_000, progress.coins)
    }
}
