package com.miniappfactory.frontlinedefender.game.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 10 — GUCLENDIRICI EKONOMISI.
 *
 * Bu sinif uc seyi kanitlar ve hicbiri "test yazdim" formalitesi degil; ucu de
 * ECONOMY_SPEC'te soz verilen SERT KURALLARIN kodla yazilmis halidir:
 *
 * 1. **Arbitraj yok** — reklam yolu ucretli yolun yerine gecemez (bolum 3).
 * 2. **Pay-to-win yok** — guclendirici KULLANMAYAN oyuncu kampanyayi bitirir,
 *    guclendirici HICBIR odul kaleminde gorunmez, tamir yildiz satin almaz (bolum 5-6).
 * 3. **Sikilastirma geri alinmaz** — Acil Tedarik reklamini izleyen oyuncu bile
 *    bugunku fazla bol ekonomiden daha rahat olmaz (bolum 7).
 */
class BoosterEconomyTest {

    private val fresh = BoosterState.startBattle(level = 10)
    private val richWallet = PlayerWallet(coins = 5_000, unlockedLevels = (1..22).toSet())

    private fun state(level: Int, adViewsToday: Int = 0) =
        BoosterState.startBattle(level, adViewsToday)

    // =================================================================================
    // 1. Acilma bolumleri
    // =================================================================================

    @Test
    fun eachBoosterUnlocksExactlyAtItsDesignedLevel() {
        assertEquals(2, BoosterType.EMERGENCY_SUPPLY.unlockLevel)
        assertEquals(4, BoosterType.AIR_SUPPORT.unlockLevel)
        assertEquals(7, BoosterType.BASE_REPAIR.unlockLevel)

        BoosterType.entries.forEach { type ->
            for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
                assertEquals(
                    "$type L$level",
                    level >= type.unlockLevel,
                    boosterUnlocked(type, level),
                )
            }
        }
    }

    @Test
    fun firstLevelHasNoBoostersAtAll() {
        // Bolum 1 saf ogretme alani: hicbir guclendirici, hicbir reklam kancasi.
        assertTrue(boostersAvailableAt(1).isEmpty())
        assertEquals(listOf(BoosterType.EMERGENCY_SUPPLY), boostersAvailableAt(2))
        assertEquals(
            listOf(BoosterType.EMERGENCY_SUPPLY, BoosterType.AIR_SUPPORT),
            boostersAvailableAt(4),
        )
        assertEquals(BoosterType.entries.toList(), boostersAvailableAt(7))
    }

    @Test
    fun lockedBoosterCannotBeUsedByAnyPath() {
        val early = state(level = 3) // Hava Destegi L4'te acilir
        assertEquals(
            BoosterDecision.Locked(4),
            boosterAllowed(early, BoosterType.AIR_SUPPORT, viaAd = false, richWallet, supplyOnHand = 9_999),
        )
        assertEquals(
            BoosterDecision.Locked(4),
            boosterAllowed(early, BoosterType.AIR_SUPPORT, viaAd = true, richWallet),
        )
    }

    // =================================================================================
    // 2. Fiyatlama — "hemen alinabilir ama anlamsiz derecede ucuz degil"
    // =================================================================================

    @Test
    fun priceTableMatchesEconomySpec() {
        // ECONOMY_SPEC B fiyat tablosu. Bu degerler dokumandaki tabloyla BIREBIR aynidir.
        assertEquals(120, boosterPrice(BoosterType.AIR_SUPPORT, 4))
        assertEquals(144, boosterPrice(BoosterType.AIR_SUPPORT, 7))
        assertEquals(264, boosterPrice(BoosterType.AIR_SUPPORT, 22))

        assertEquals(240, boosterPrice(BoosterType.BASE_REPAIR, 7))
        assertEquals(540, boosterPrice(BoosterType.BASE_REPAIR, 22))

        assertEquals(0, boosterPrice(BoosterType.EMERGENCY_SUPPLY, 2))

        assertEquals(60, emergencySupplyAmount(2))
        assertEquals(80, emergencySupplyAmount(6))
        assertEquals(140, emergencySupplyAmount(22))
    }

    @Test
    fun pricesNeverDecreaseWithLevel() {
        BoosterType.entries.forEach { type ->
            for (level in 2..EconomyConfig.CAMPAIGN_LEVELS) {
                assertTrue(
                    "$type L$level fiyati L${level - 1}'den dusuk",
                    boosterPrice(type, level) >= boosterPrice(type, level - 1),
                )
            }
        }
    }

    @Test
    fun airSupportCostsAtLeastAWholeOpeningTower() {
        // SERT KURAL: hava destegi bir kule kurmaktan vazgecmek demek. Fiyat, o bolumun
        // baslangic Tedarikinin %70'inin altina duserse "bedava gibi" olur ve
        // ECONOMY_SPEC A sikilastirmasi anlamsizlasir.
        for (level in BoosterType.AIR_SUPPORT.unlockLevel..EconomyConfig.CAMPAIGN_LEVELS) {
            val opening = startingSupplyFor(level)
            assertTrue(
                "L$level: hava destegi ${boosterPrice(BoosterType.AIR_SUPPORT, level)}, " +
                    "acilis Tedariki $opening — cok ucuz",
                boosterPrice(BoosterType.AIR_SUPPORT, level) >= opening * 70 / 100,
            )
        }
    }

    @Test
    fun baseRepairIsPricedBetweenTheCheapestRankAndOneStarReward() {
        // Alt sinir: en ucuz meta rank (150). Altina duserse oyuncu agac yerine
        // tamir farming'i yapar.
        // Ust sinir: R(L) 1 yildiz odulu. Ustune cikarsa yenilgiyi zafere cevirmek
        // NET ZARAR olur ve guclendirici olu yatirim haline gelir.
        val cheapestRank = UpgradeLine.entries.minOf { it.costOfRank(1) }
        assertEquals(150, cheapestRank)

        for (level in BoosterType.BASE_REPAIR.unlockLevel..EconomyConfig.CAMPAIGN_LEVELS) {
            val price = boosterPrice(BoosterType.BASE_REPAIR, level)
            assertTrue("L$level tamir $price <= en ucuz rank $cheapestRank", price > cheapestRank)
            assertTrue(
                "L$level tamir $price >= 1 yildiz odulu ${levelReward(level, 1)}",
                price < levelReward(level, 1),
            )
        }
    }

    @Test
    fun emergencySupplyIsRoughlyOneBasicTowerAndNeverAnOpeningReplacement() {
        // Acil Tedarik "bir temel kule" kadar olmali: daha azi butona basmaya degmez,
        // daha fazlasi bolum butcesini yeniden sisirir.
        for (level in BoosterType.EMERGENCY_SUPPLY.unlockLevel..EconomyConfig.CAMPAIGN_LEVELS) {
            val amount = emergencySupplyAmount(level)
            val opening = startingSupplyFor(level)
            assertTrue("L$level acil tedarik $amount cok kucuk", amount >= 60)
            assertTrue(
                "L$level acil tedarik $amount, acilis Tedariki $opening'i asiyor — " +
                    "reklam tum acilisi ikame ediyor",
                amount <= opening,
            )
        }
    }

    // =================================================================================
    // 3. ARBITRAJ TESTI — reklamsiz yol reklamli yolu (ve tersini) degersizlestirmiyor
    // =================================================================================

    @Test
    fun adPathIsLockedUntilThePaidPathIsExhausted() {
        // TASARIM AKSIYOMU: reklam ikame DEGIL, uzantidir. Ucretli kullanim
        // tuketilmeden reklam teklifi acilmaz — yoksa oyuncu 120 Tedarik yerine daima
        // bedava reklami secer ve Tedarik fiyatlamasi olu harf olur.
        val s = state(level = 10)

        assertEquals(
            BoosterDecision.PaidPathNotExhausted(1),
            boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet),
        )
        assertEquals(
            BoosterDecision.PaidPathNotExhausted(1),
            boosterAllowed(s, BoosterType.BASE_REPAIR, viaAd = true, richWallet, baseHealth = 5),
        )

        // Ucretli kullanim yapildiktan SONRA reklam yolu acilir.
        val paid = boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = false, richWallet, supplyOnHand = 9_999)
        assertTrue(paid.isAllowed)
        val after = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = false, decision = paid, nowMs = 0L)
        assertTrue(
            boosterAllowed(after, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, nowMs = 10_000_000L)
                .isAllowed
        )
    }

    @Test
    fun adOnlyBoosterHasNoPaidSubstituteSoArbitrageIsStructurallyImpossible() {
        val s = state(level = 5)
        // Ucretli yol yok -> reklam hicbir seyin ikamesi degil.
        assertEquals(
            BoosterDecision.PaidPathUnavailable,
            boosterAllowed(s, BoosterType.EMERGENCY_SUPPLY, viaAd = false, richWallet, supplyOnHand = 9_999),
        )
        assertTrue(boosterAllowed(s, BoosterType.EMERGENCY_SUPPLY, viaAd = true, richWallet).isAllowed)
        assertEquals(BoosterCurrency.AD_ONLY, BoosterType.EMERGENCY_SUPPLY.currency)
        assertFalse(BoosterType.EMERGENCY_SUPPLY.hasPaidPath)
        assertEquals(0, BoosterType.EMERGENCY_SUPPLY.paidUsesPerBattle)
    }

    @Test
    fun everyBoosterOffersStrictlyMoreThroughAdsThanMoneyCanBuy() {
        // Arbitraj kalkaninin yapisal ifadesi: reklamin acabildigi kullanim sayisi,
        // ucretli yolun ulasabildigi sayinin DAIMA otesindedir. Esit olsaydi iki yol
        // ikame olurdu ve biri olurdu.
        BoosterType.entries.forEach { type ->
            assertTrue("$type reklam yolu yok", type.adUsesPerBattle > 0)
            assertTrue(
                "$type: ucretli ${type.paidUsesPerBattle} vs toplam ${type.maxUsesPerBattle}",
                type.paidUsesPerBattle < type.maxUsesPerBattle,
            )
        }
    }

    @Test
    fun payingCannotBuyTheAdUseAtAnyPrice() {
        // Ucretli kullanim tukendikten sonra ucretli yol KAPANIR; ikinci kullanim
        // yalnizca reklamla alinir. "Parayla her seyi al" yolu yok.
        val s = state(level = 10)
        val d = boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = false, richWallet, supplyOnHand = 9_999)
        val after = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = false, decision = d, nowMs = 0L)
        assertEquals(
            BoosterDecision.PaidLimitReached,
            boosterAllowed(
                after, BoosterType.AIR_SUPPORT, viaAd = false, richWallet,
                supplyOnHand = 1_000_000, nowMs = 10_000_000L,
            ),
        )
    }

    // =================================================================================
    // 4. Limitler ve exploit kapaklari
    // =================================================================================

    @Test
    fun eachBoosterIsCappedAtOnePaidPlusOneAdUsePerBattle() {
        var s = state(level = 10)
        var now = 0L
        val type = BoosterType.AIR_SUPPORT

        val paid = boosterAllowed(s, type, viaAd = false, richWallet, supplyOnHand = 9_999, nowMs = now)
        s = useBooster(s, type, viaAd = false, decision = paid, nowMs = now)
        now += EconomyConfig.AIR_SUPPORT_COOLDOWN_MS

        val ad = boosterAllowed(s, type, viaAd = true, richWallet, nowMs = now)
        s = useBooster(s, type, viaAd = true, decision = ad, nowMs = now)
        now += EconomyConfig.AIR_SUPPORT_COOLDOWN_MS

        assertEquals(2, s.usesOf(type))
        assertEquals(
            BoosterDecision.PaidLimitReached,
            boosterAllowed(s, type, viaAd = false, richWallet, supplyOnHand = 9_999, nowMs = now),
        )
        assertEquals(
            BoosterDecision.AdLimitReached,
            boosterAllowed(s, type, viaAd = true, richWallet, nowMs = now),
        )
    }

    @Test
    fun dailyBoosterAdRightIsBoundedAndCannotBeFarmed() {
        // EXPLOIT: "bolume gir, reklam izle, cik, tekrar gir" dongusu. Gunluk hak
        // savaslar arasi tasindigi icin dongü kapali.
        var adViews = 0
        var granted = 0
        repeat(50) {
            var s = state(level = 5, adViewsToday = adViews)
            val d = boosterAllowed(s, BoosterType.EMERGENCY_SUPPLY, viaAd = true, richWallet)
            if (d.isAllowed) {
                s = useBooster(s, BoosterType.EMERGENCY_SUPPLY, viaAd = true, decision = d, nowMs = 0L)
                granted++
                adViews = s.adViewsToday
            }
        }
        assertEquals(EconomyConfig.BOOSTER_AD_VIEWS_PER_DAY, granted)
        assertEquals(
            BoosterDecision.DailyAdLimitReached(EconomyConfig.BOOSTER_AD_VIEWS_PER_DAY),
            boosterAllowed(
                state(level = 5, adViewsToday = adViews),
                BoosterType.EMERGENCY_SUPPLY, viaAd = true, richWallet,
            ),
        )
    }

    @Test
    fun boostersAreNotInventoryAndDoNotSurviveTheBattle() {
        // Savasa kapsamli model: yeni savas sayaclari sifirlar, yalnizca gunluk reklam
        // sayaci tasinir. Stoklama olsaydi "20 tane biriktir, son bolumu ez" acilirdi.
        var s = state(level = 10)
        val d = boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = false, richWallet, supplyOnHand = 9_999)
        s = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = false, decision = d, nowMs = 0L)
        assertEquals(1, s.paidUsesOf(BoosterType.AIR_SUPPORT))

        val next = BoosterState.startBattle(11, s.adViewsToday)
        assertEquals(0, next.paidUsesOf(BoosterType.AIR_SUPPORT))
        assertEquals(0, next.repairedHealth)
        assertEquals(s.adViewsToday, next.adViewsToday)
    }

    @Test
    fun rejectedActivationChangesNothing() {
        val s = state(level = 3) // Hava Destegi kilitli
        val denied = boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = false, richWallet, supplyOnHand = 9_999)
        assertFalse(denied.isAllowed)
        assertSame(s, useBooster(s, BoosterType.AIR_SUPPORT, viaAd = false, decision = denied))
        assertSame(richWallet, payForBooster(richWallet, denied))
    }

    @Test
    fun cooldownBlocksBackToBackAirSupport() {
        var s = state(level = 10)
        val d = boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = false, richWallet, supplyOnHand = 9_999, nowMs = 1_000L)
        s = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = false, decision = d, nowMs = 1_000L)

        val tooSoon = boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, nowMs = 2_000L)
        assertTrue("bekleme suresi uygulanmali", tooSoon is BoosterDecision.Cooldown)
        assertEquals(
            EconomyConfig.AIR_SUPPORT_COOLDOWN_MS - 1_000L,
            (tooSoon as BoosterDecision.Cooldown).remainingMs,
        )
        assertTrue(
            boosterAllowed(
                s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet,
                nowMs = 1_000L + EconomyConfig.AIR_SUPPORT_COOLDOWN_MS,
            ).isAllowed
        )
    }

    @Test
    fun repairIsRefusedWhenThereIsNothingToRepair() {
        val s = state(level = 10)
        assertEquals(
            BoosterDecision.NoEffect,
            boosterAllowed(
                s, BoosterType.BASE_REPAIR, viaAd = false, richWallet,
                baseHealth = 20, maxBaseHealth = 20,
            ),
        )
        assertEquals(0, baseRepairAmount(20, 20))
        // Kaybedilenden fazlasini vermez.
        assertEquals(3, baseRepairAmount(17, 20))
        assertEquals(8, baseRepairAmount(4, 20))
        assertEquals(12, baseRepairAmount(1, 30))
    }

    // =================================================================================
    // 5. Coin yolu — Konuslanma Rezervi guclendiriciler yuzunden kirilmaz
    // =================================================================================

    @Test
    fun reserveCoinsCanNeverBeSpentOnABooster() {
        // Bolum 7 kapisinin hemen oncesi: 1..6 temizlenmis, rezerv tam bedel (100).
        val wallet = PlayerWallet(
            coins = 250,
            unlockedLevels = (1..6).toSet(),
            clearedLevels = (1..6).toSet(),
            bestStars = (1..6).associateWith { 2 },
        )
        assertEquals(100, reserveFor(wallet))

        val s = state(level = 6)
        // L6'da tamir zaten kilitli; L7'de dene.
        val s7 = state(level = 7)
        val price = boosterPrice(BoosterType.BASE_REPAIR, 7) // 240
        val decision = boosterAllowed(s7, BoosterType.BASE_REPAIR, viaAd = false, wallet, baseHealth = 5)
        assertEquals(
            BoosterDecision.ReserveLocked(price, 100, 100 - (250 - price)),
            decision,
        )
        assertSame("reddedilen alim cuzdani degistirmemeli", wallet, payForBooster(wallet, decision))
        assertFalse(boosterUnlocked(BoosterType.BASE_REPAIR, s.level))
    }

    @Test
    fun insufficientCoinsIsDistinctFromReserveLock() {
        // Analytics sozlesmesi: `reserve_lock_blocked` yalnizca oyuncu parayi
        // GERCEKTEN karsilayabildigi halde rezerv engelledginde gonderilir.
        val poor = PlayerWallet(coins = 50, unlockedLevels = (1..6).toSet())
        val d = boosterAllowed(state(7), BoosterType.BASE_REPAIR, viaAd = false, poor, baseHealth = 5)
        assertTrue("bakiye hic yetmiyor -> InsufficientCoins", d is BoosterDecision.InsufficientCoins)
        assertEquals(240 - 50, (d as BoosterDecision.InsufficientCoins).shortfall)
    }

    @Test
    fun coinPathDebitsExactlyOnceAndOnlyForCoinPricedBoosters() {
        val wallet = PlayerWallet(coins = 1_000, unlockedLevels = (1..22).toSet())
        val repair = boosterAllowed(state(10), BoosterType.BASE_REPAIR, viaAd = false, wallet, baseHealth = 5)
        assertTrue(repair.isAllowed)
        val after = payForBooster(wallet, repair)
        assertEquals(1_000 - boosterPrice(BoosterType.BASE_REPAIR, 10), after.coins)

        // Tedarik fiyatli guclendirici coin DUSMEZ (iki ekonomi arasinda donusum yok).
        val air = boosterAllowed(state(10), BoosterType.AIR_SUPPORT, viaAd = false, wallet, supplyOnHand = 9_999)
        assertTrue(air.isAllowed)
        assertSame(wallet, payForBooster(wallet, air))
    }

    @Test
    fun supplyPricedBoosterNeverTouchesCoinsAndViceVersa() {
        // GDD D.4: iki ekonomi arasinda DONUSUM YOK. Guclendiriciler bunu ihlal etmez.
        val brokeCoins = PlayerWallet(coins = 0, unlockedLevels = (1..22).toSet())
        assertTrue(
            "coini 0 olan oyuncu Tedarikle hava destegi alabilmeli",
            boosterAllowed(state(10), BoosterType.AIR_SUPPORT, viaAd = false, brokeCoins, supplyOnHand = 500)
                .isAllowed
        )
        val rich = PlayerWallet(coins = 100_000, unlockedLevels = (1..22).toSet())
        val d = boosterAllowed(state(10), BoosterType.AIR_SUPPORT, viaAd = false, rich, supplyOnHand = 0)
        assertTrue("coin zenginligi Tedarik eksigini kapatamaz", d is BoosterDecision.InsufficientSupply)
    }

    // =================================================================================
    // 6. PAY-TO-WIN OLMAMA
    // =================================================================================

    @Test
    fun airSupportCanNeverClearAWaveByItself() {
        // Tam canli hicbir dusmani tek basina oldurmez -> "dalga temizleme butonu" degil.
        assertTrue(
            "hava destegi hasar orani 1,0'a esit/buyuk olamaz",
            EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION < 1.0,
        )
        assertTrue(EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION > 0.0)
    }

    @Test
    fun baseRepairIsStarNeutralSoItCanNeverBeCoinPositive() {
        // ARBITRAJ: "tamir et -> yildiz atla -> tamirden fazla coin kazan".
        // Yildiz, tamir edilen can DUSULEREK hesaplandigi icin arbitraj fiyatlamaya
        // bagli olmaktan cikar, YAPISAL olarak yok olur.
        val maxLives = 20
        val repaired = baseRepairAmount(currentHealth = 6, maxHealth = maxLives) // 8
        assertEquals(8, repaired)

        // Tamir edilmeden bitis: 6/20 = %30 -> 1 yildiz.
        assertEquals(1, starsFor(6, maxLives))
        // Tamir sonrasi ham can 14/20 = %70 -> 2 yildiz gorunurdu...
        assertEquals(2, starsFor(14, maxLives))
        // ...ama etkin can tamiri duser: 14 - 8 = 6 -> yine 1 yildiz.
        assertEquals(6, effectiveStarHealth(14, repaired))
        assertEquals(1, starsFor(effectiveStarHealth(14, repaired), maxLives))

        val wallet = PlayerWallet(unlockedLevels = (1..22).toSet())
        val withRepair = resolveLevelClear(wallet, 10, effectiveStarHealth(14, repaired), maxLives)
        val withoutRepair = resolveLevelClear(wallet, 10, 6, maxLives)
        assertEquals(
            "tamir odulu ARTIRMAMALI",
            withoutRepair.total, withRepair.total,
        )
    }

    @Test
    fun repairCanNeverManufactureAPerfectDefenseMedal() {
        // Kusursuz Savunma "hic sizma yok" demek; tamirle satin alinamaz.
        val maxLives = 20
        assertFalse(isPerfectDefense(effectiveStarHealth(20, 8), maxLives))
        assertTrue(isPerfectDefense(20, maxLives))
    }

    @Test
    fun repairPriceExceedsEveryStarJumpItCouldEverBuy() {
        // Ikinci kemer: yildiz notrlugu bir gun kaldirilsa bile fiyat tek basina
        // arbitraji zararli tutuyor mu? Kampanyadaki EN BUYUK yildiz atlama farki
        // (1 -> 3 yildiz + madalya) ile tamir ucretini kiyasla.
        for (level in BoosterType.BASE_REPAIR.unlockLevel..EconomyConfig.CAMPAIGN_LEVELS) {
            val maxJump = levelReward(level, 3) - levelReward(level, 1)
            val price = boosterPrice(BoosterType.BASE_REPAIR, level)
            assertTrue(
                "L$level: tamir $price, olasi en buyuk yildiz farki $maxJump — " +
                    "yildiz notrlugu kalkarsa arbitraj karli olur",
                price >= maxJump,
            )
        }
    }

    @Test
    fun aPlayerWhoNeverTouchesABoosterStillFinishesTheCampaign() {
        // Guclendiriciler ekonomiye HICBIR sekilde girmez: bir tanesini bile
        // kullanmayan oyuncunun ilerlemesi Faz 9 ile birebir aynidir.
        var wallet = PlayerWallet()
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            if (level >= EconomyConfig.FIRST_PAID_LEVEL) {
                assertTrue(
                    "L$level: guclendiricisiz oyuncu kapiyi acamadi (bakiye ${wallet.coins}, bedel ${lockCost(level)})",
                    canUnlock(wallet, level),
                )
                wallet = applyUnlock(wallet, level)
            }
            // En kotu senaryo: her bolum 1 yildiz, gorev yok, reklam yok, guclendirici yok.
            val result = resolveLevelClear(wallet, level, livesLeft = 6, maxLives = 20)
            assertEquals(1, result.stars)
            wallet = applyLevelClear(wallet, result)
        }
        assertEquals(EconomyConfig.CAMPAIGN_LEVELS, wallet.clearedLevels.size)
        assertEquals(0, autoGrantShortfall(wallet))
    }

    @Test
    fun boosterSpendingCanNeverSoftLockTheCampaign() {
        // Aç gözlü oyuncu: her bolumde tamiri sonuna kadar satin alir. Rezerv kilidi
        // sayesinde kapiyi acacak coin HER ZAMAN kalir.
        var wallet = PlayerWallet()
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            if (level >= EconomyConfig.FIRST_PAID_LEVEL) {
                assertTrue("L$level acilamadi", canUnlock(wallet, level))
                wallet = applyUnlock(wallet, level)
            }
            val result = resolveLevelClear(wallet, level, livesLeft = 12, maxLives = 20)
            wallet = applyLevelClear(wallet, result)

            // Tamiri rezerv izin verdigi surece tekrar tekrar satin al.
            var guard = 0
            while (guard++ < 100) {
                val d = boosterAllowed(
                    state(level), BoosterType.BASE_REPAIR, viaAd = false, wallet, baseHealth = 5,
                )
                if (!d.isAllowed) break
                wallet = payForBooster(wallet, d)
            }
            assertEquals(
                "L$level sonrasi soft-lock: guclendirici harcamasi rezervi yedi",
                0, autoGrantShortfall(wallet),
            )
        }
        assertEquals(EconomyConfig.CAMPAIGN_LEVELS, wallet.clearedLevels.size)
    }

    // =================================================================================
    // 7. SIKILASTIRMA GERI ALINMAZ (ECONOMY_SPEC A.4)
    // =================================================================================

    @Test
    fun adWatchingPlayerIsStillTighterThanTodaysOverflushEconomy() {
        // SERT KURAL: sikilastirilmis butce + reklamla alinabilen TUM ek Tedarik,
        // bugunku (fazla bol) butcenin altinda kalmali. Aksi halde reklam izleyen
        // oyuncu icin testcinin sikayeti hic cozulmemis olur.
        for (level in 1..6) {
            val tightened = startingSupplyFor(level) + waveSupplyIncome(level)
            val withAds = tightened + maxBoosterSupplyInjection(level)
            val legacy = LEGACY_STARTING_SUPPLY + legacyWaveSupplyIncome(level)
            assertTrue(
                "L$level: reklamli butce $withAds, eski bol butce $legacy — sikilastirma geri alindi",
                withAds <= legacy,
            )
        }
    }

    @Test
    fun boostersAddNoCoinInflationAtAll() {
        // Guclendirici reklamlari coin ODEMEZ. Gunluk coin girisi Faz 9 ile ayni kalir.
        BoosterType.entries.forEach { type ->
            assertTrue(
                "$type reklam yolu coin odememeli",
                boosterPrice(type, 22) == 0 || type.currency != BoosterCurrency.AD_ONLY,
            )
        }
        // Coin AKISI yalnizca CIKIS yonunde: Us Tamiri bir sink'tir, kaynak degil.
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            assertTrue(maxBoosterCoinSpend(level) >= 0)
        }
        assertEquals(0, maxBoosterCoinSpend(6))
        assertEquals(240, maxBoosterCoinSpend(7))
    }

    // =================================================================================
    // 8. R1 adaptif odul (BAYRAK F-11) — enflasyon yaratmadigi kanitlanir
    // =================================================================================

    @Test
    fun adaptiveRequisitionRewardIsOffByDefaultAndBehavesExactlyLikePhase9() {
        assertFalse(EconomyConfig.R1_ADAPTIVE_REWARD_ENABLED)
        listOf(0, 150, 400, 850, 10_000).forEach { price ->
            assertEquals(
                "bayrak kapaliyken odul her zaman sabit olmali",
                EconomyConfig.R1_REWARD_FILLED, requisitionFilledReward(price),
            )
        }
    }

    @Test
    fun adaptiveRewardArithmeticNeverDropsBelowTheFlatRewardNorExceedsItsCap() {
        // Bayrak ACILDIGINDA uygulanacak aritmetigi belgeler: ECONOMY_SPEC C.4 tablosu.
        fun scaled(nextRank: Int) = roundToTen(nextRank * EconomyConfig.R1_SCALE_OF_NEXT_RANK)
            .coerceIn(EconomyConfig.R1_REWARD_FILLED_MIN, EconomyConfig.R1_REWARD_FILLED_MAX)

        assertEquals(150, scaled(150))
        assertEquals(150, scaled(600))
        assertEquals(180, scaled(700))
        assertEquals(210, scaled(850))
        assertEquals(250, scaled(100_000)) // tavan

        // Taban = sabit odul: olceklenme oyuncuyu ASLA bugunkunden kotu duruma dusurmez.
        assertEquals(EconomyConfig.R1_REWARD_FILLED, EconomyConfig.R1_REWARD_FILLED_MIN)
    }

    @Test
    fun dailyRequisitionCoinTotalIsBudgetBoundRegardlessOfScaling() {
        // ENFLASYON YASAGI: odul/gosterim ne olursa olsun gunluk toplam sabit kalir.
        listOf(0, 150, 850, 100_000).forEach { nextRank ->
            var s = RequisitionState()
            var total = 0
            repeat(40) {
                val g = grantRequisition(s, AdOutcome.REWARD_EARNED, nextRank)
                total += g.coins
                s = g.newState
            }
            assertEquals(
                "nextRank=$nextRank icin gunluk R1 toplami butceyi asti",
                EconomyConfig.R1_COIN_BUDGET_PER_DAY, total,
            )
        }
    }

    @Test
    fun cheapestNextRankPriceTracksTheRealShopFloor() {
        assertEquals(150, cheapestNextRankPrice(MetaUpgrades()))
        // FIREPOWER rank 1 alindi -> en ucuz siradaki artik FIREPOWER rank 2 (250) veya
        // SALVAGE/STARTING_SUPPLY rank 1 (200).
        assertEquals(200, cheapestNextRankPrice(MetaUpgrades(firepower = 1)))
        assertNotEquals(null, cheapestNextRankPrice(MetaUpgrades(firepower = 8)))

        val maxed = MetaUpgrades(
            firepower = 8, optics = 5, startingSupplyRank = 6, fortification = 5, salvage = 4,
        )
        assertTrue(maxed.isMaxed())
        assertEquals(null, cheapestNextRankPrice(maxed))
    }
}
