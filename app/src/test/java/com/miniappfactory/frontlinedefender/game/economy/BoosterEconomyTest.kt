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
        // ECONOMY_SPEC B fiyat tablosu.
        // Hava Destegi tabani 96 -> 101: fiyat acilista (L4) TAM olarak bir
        // kademe-2 Gatling (125) olsun diye. Ankraj artik "acilis Tedarikinin
        // %85'i" degil "bir kule" — bkz. EconomyConfig.AIR_SUPPORT_SUPPLY_BASE.
        assertEquals(125, boosterPrice(BoosterType.AIR_SUPPORT, 4))
        assertEquals(149, boosterPrice(BoosterType.AIR_SUPPORT, 7))
        assertEquals(269, boosterPrice(BoosterType.AIR_SUPPORT, 22))

        assertEquals(240, boosterPrice(BoosterType.BASE_REPAIR, 7))
        assertEquals(540, boosterPrice(BoosterType.BASE_REPAIR, 22))

        assertEquals(0, boosterPrice(BoosterType.EMERGENCY_SUPPLY, 2))

        assertEquals(70, emergencySupplyAmount(2))
        assertEquals(90, emergencySupplyAmount(6))
        assertEquals(190, emergencySupplyAmount(22))
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

    /**
     * ⚠⚠ 2026-08-21 — BU TEST ARTIK OLU BIR SAYIYI KORUYOR. GECIYOR AMA HICBIR
     * OYUNCU DAVRANISINI KILITLEMIYOR.
     *
     * Hava Destegi AD_ONLY'ye cevrildi: `boosterAllowed` ucretli yolu
     * [BoosterDecision.PaidPathUnavailable] ile daha ilk dalda kapatiyor, yani
     * `boosterPrice(AIR_SUPPORT, level)` ARTIK HICBIR KOD YOLUNDAN OKUNMUYOR
     * (tek uretim cagirani `TutorialOverlay` ve o da yalnizca SUPPLY/COIN
     * currency dallarinda okuyor). Fiyat fonksiyonu kendi KDoc'uyla da celisiyor:
     * "AD_ONLY tipler ... icin 0" diyor ama L4'te 125, L22'de 269 donuyor.
     *
     * TEST SILINMEDI, cunku silmek bayat sayiyi sessizce birakirdi. Ulasilamaz
     * oldugu [adOnlyBoostersCanNeverChargeAnyCurrency] ile kilitlendi; asil karar
     * (fiyati 0'a cekmek mi, ucretli yolu geri getirmek mi) urun tarafinda ve
     * ajan kapsaminin disinda — RAPOR EDILDI.
     *
     * Asagidaki gerekce yalnizca ucretli yol geri gelirse tekrar anlam kazanir:
     *
     * SERT KURAL: hava destegi **bir kule kurmaktan vazgecmek** demek.
     *
     * OLCUT DEGISTI — "acilis Tedarikinin %70'i" -> "tam yukseltilmis en ucuz
     * kule". Eski olcut, acilis Tedariki bir-iki kule alirken dogru vekildi.
     * Sermaye kadrodan turetilir olunca (3-7 kulelik purse) ayni yuzde artik
     * "bir kule" demiyor; ustelik yuzde, guclendirici fiyatiyla hicbir ilgisi
     * olmayan bir sebeple (kadro buyudu) kayiyor. Yeni olcut dogrudan kule
     * fiyatina bakiyor, yani kalibrasyonun NIYETI ile ayni birimde.
     */
    @Test
    fun airSupportNeverCostsLessThanAFullyUpgradedTower() {
        val cheapestFullTower = GameConfigCampaignFacts.towerBuildCost.keys
            .minOf { GameConfigCampaignFacts.tierTwoCost(it) }
        for (level in BoosterType.AIR_SUPPORT.unlockLevel..EconomyConfig.CAMPAIGN_LEVELS) {
            val price = boosterPrice(BoosterType.AIR_SUPPORT, level)
            assertTrue(
                "L$level: hava destegi $price, tam yukseltilmis en ucuz kule " +
                    "$cheapestFullTower — guclendirici kuleden ucuz olamaz",
                price >= cheapestFullTower,
            )
        }
    }

    @Test
    fun baseRepairIsPricedBetweenTheCheapestRankAndOneStarReward() {
        // Alt sinir: en ucuz meta rank (150). Altina duserse oyuncu agac yerine
        // tamir farming'i yapar.
        // Ust sinir: R(L) 1 yildiz odulu. Ustune cikarsa yenilgiyi zafere cevirmek
        // NET ZARAR olur ve guclendirici olu yatirim haline gelir.
        // Dukkan tabani: granularite duzeltmesinden sonra en ucuz rank-1
        // Baslangic Tedariki / Hurda Degeri (200). Ates Gucu rank 1 150 -> 400
        // cikti cunku artik +%3 degil +%6 veriyor.
        val cheapestRank = UpgradeLine.entries.minOf { it.costOfRank(1) }
        assertEquals(200, cheapestRank)

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

    /**
     * GERCEK KISIT. Arbitraj kalkani hâlâ [boosterAllowed] icinde yasayan sert bir
     * kuraldir; degisen tek sey onu tasiyan ORNEK.
     *
     * ⚠ 2026-08-21 — ORNEK DEGISTI, KURAL DEGISMEDI. Bu test eskiden hem
     * [BoosterType.AIR_SUPPORT]'u hem [BoosterType.BASE_REPAIR]'i ornek aliyordu.
     * Hava Destegi AD_ONLY'ye cevrilince ucretli yolu kalmadi, dolayisiyla
     * "once ucretli yolu tuket" kuralinin uzerinde calisabilecegi bir yol da
     * kalmadi (bkz. [airSupportHasNoPaidPathSoItStartsEveryBattleDeactivated]).
     * Kurali tasiyan TEK guclendirici artik Us Tamiri; ornek oraya tasindi.
     *
     * Testi "gecsin diye" gevsetmedim: kalkanin ISLEDIGI dogrulaniyor (reddin
     * turu, kalan hak sayisi, ucretli kullanim sonrasi acilma). Ayrica kuralin
     * KAPSAMI enum uzerinden ayrica kilitlendi ki ornegin tekrar bayatlamasi
     * sessiz kalmasin.
     */
    @Test
    fun adPathIsLockedUntilThePaidPathIsExhausted() {
        // TASARIM AKSIYOMU: reklam ikame DEGIL, uzantidir. Ucretli kullanim
        // tuketilmeden reklam teklifi acilmaz — yoksa oyuncu 300 coin yerine daima
        // bedava reklami secer ve coin fiyatlamasi olu harf olur.
        val s = state(level = 10)

        assertEquals(
            BoosterDecision.PaidPathNotExhausted(1),
            boosterAllowed(s, BoosterType.BASE_REPAIR, viaAd = true, richWallet, baseHealth = 5),
        )

        // Ucretli kullanim yapildiktan SONRA reklam yolu acilir.
        val paid = boosterAllowed(
            s, BoosterType.BASE_REPAIR, viaAd = false, richWallet, baseHealth = 5, nowMs = 0L,
        )
        assertTrue(paid.isAllowed)
        val after = useBooster(s, BoosterType.BASE_REPAIR, viaAd = false, decision = paid, nowMs = 0L)
        assertTrue(
            boosterAllowed(
                after, BoosterType.BASE_REPAIR, viaAd = true, richWallet,
                baseHealth = 5, nowMs = 10_000_000L,
            ).isAllowed
        )

        // KAPSAM KILIDI: kalkan "ucretli yolu olan HER guclendirici" icin gecerli.
        // Ornek yine bayatlarsa (Us Tamiri de AD_ONLY olursa) bu dongu bos kalir
        // ve asagidaki sayac testi patlar — sessizce kapsamsiz kalmaz.
        val withPaidPath = BoosterType.entries.filter { it.hasPaidPath }
        assertEquals("ucretli yolu olan guclendirici sayisi degisti", 1, withPaidPath.size)
        withPaidPath.forEach { type ->
            val d = boosterAllowed(
                state(level = EconomyConfig.CAMPAIGN_LEVELS), type, viaAd = true, richWallet,
                baseHealth = 5, supplyOnHand = 9_999,
            )
            assertEquals(
                "$type: ucretli yol tukenmeden reklam yolu acilmis",
                BoosterDecision.PaidPathNotExhausted(type.paidUsesPerBattle), d,
            )
        }
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

    /**
     * GERCEK KISIT — "parayla her seyi al" yolu yok. Ornek Hava Destegi'nden Us
     * Tamiri'ne tasindi (2026-08-21): ucretli+reklam ikilisini tasiyan tek
     * guclendirici Us Tamiri kaldi.
     *
     * "Herhangi bir fiyata" iddiasi milyonluk bakiyeyle olculuyor: reddin sebebi
     * odeme gucu DEGIL, tukenmis hak. Bakiye ne olursa olsun cevap ayni.
     */
    @Test
    fun payingCannotBuyTheAdUseAtAnyPrice() {
        // Ucretli kullanim tukendikten sonra ucretli yol KAPANIR; ikinci kullanim
        // yalnizca reklamla alinir.
        val loaded = PlayerWallet(coins = 1_000_000, unlockedLevels = (1..22).toSet())
        val s = state(level = 10)
        val d = boosterAllowed(s, BoosterType.BASE_REPAIR, viaAd = false, loaded, baseHealth = 5, nowMs = 0L)
        assertTrue(d.isAllowed)
        val after = useBooster(s, BoosterType.BASE_REPAIR, viaAd = false, decision = d, nowMs = 0L)
        assertEquals(
            BoosterDecision.PaidLimitReached,
            boosterAllowed(
                after, BoosterType.BASE_REPAIR, viaAd = false, loaded,
                baseHealth = 5, nowMs = 10_000_000L,
            ),
        )
    }

    // =================================================================================
    // 4. Limitler ve exploit kapaklari
    // =================================================================================

    /**
     * GERCEK KISIT — savas basina kullanim tavani var ve HER IKI yol da tam
     * doyduktan sonra kesin olarak kapaniyor.
     *
     * ⚠ 2026-08-21 — AD BAYATLAMISTI. Eski adi
     * `eachBoosterIsCappedAtOnePaidPlusOneAdUsePerBattle` idi ve "1 ucretli + 1
     * reklam" sayilarini isminde tasiyordu. O sayilar artik hicbir guclendirici
     * icin dogru degil (Acil Tedarik 0+1, Hava Destegi 0+2, Us Tamiri 1+1) —
     * yani ad, okuyana YANLIS bir genel kural ogretiyordu.
     *
     * Test ayni sebeple tek bir ORNEK guclendirici uzerinden yazilmisti ve
     * o ornek degisince kirildi. Yeni hali sayilari ezberlemek yerine her
     * guclendiricinin KENDI beyan ettigi tavani ([BoosterType.paidUsesPerBattle],
     * [BoosterType.adUsesPerBattle]) sonuna kadar tuketip sonrasini reddettirir.
     * Boylece tavan degerleri bir daha degistiginde test kirilmaz ama tavanin
     * UYGULANDIGI kanitlanmaya devam eder.
     */
    @Test
    fun everyBoosterSaturatesItsDeclaredPathsAndThenRefusesBoth() {
        BoosterType.entries.forEach { type ->
            var s = state(level = EconomyConfig.CAMPAIGN_LEVELS)
            var now = 0L
            val step = boosterCooldownMs(type) + 1L

            // Once ucretli yol: kalkan reklam yolunu zaten bu sirayi dayatiyor.
            repeat(type.paidUsesPerBattle) {
                val d = boosterAllowed(
                    s, type, viaAd = false, richWallet,
                    supplyOnHand = 9_999, baseHealth = 5, enemiesOnField = 5, nowMs = now,
                )
                assertTrue("$type ucretli kullanim #${it + 1} reddedildi: $d", d.isAllowed)
                s = useBooster(s, type, viaAd = false, decision = d, nowMs = now)
                now += step
            }
            repeat(type.adUsesPerBattle) {
                val d = boosterAllowed(
                    s, type, viaAd = true, richWallet,
                    baseHealth = 5, enemiesOnField = 5, nowMs = now,
                )
                assertTrue("$type reklam kullanimi #${it + 1} reddedildi: $d", d.isAllowed)
                s = useBooster(s, type, viaAd = true, decision = d, nowMs = now)
                now += step
            }

            assertEquals("$type toplam kullanim", type.maxUsesPerBattle, s.usesOf(type))
            assertEquals(type.paidUsesPerBattle, s.paidUsesOf(type))
            assertEquals(type.adUsesPerBattle, s.adUsesOf(type))

            // Doyduktan sonra iki yol da kapali. Ucretli yolu OLMAYAN tipte
            // reddin sebebi tavan degil "boyle bir yol yok" — ikisi ayri
            // mesajlar ve analytics acisindan da ayri kalmali.
            val paidAgain = boosterAllowed(
                s, type, viaAd = false, richWallet,
                supplyOnHand = 1_000_000, baseHealth = 5, enemiesOnField = 5, nowMs = now,
            )
            assertEquals(
                "$type ucretli yol doyduktan sonra hâlâ acik",
                if (type.hasPaidPath) BoosterDecision.PaidLimitReached
                else BoosterDecision.PaidPathUnavailable,
                paidAgain,
            )
            assertEquals(
                "$type reklam yolu doyduktan sonra hâlâ acik",
                BoosterDecision.AdLimitReached,
                boosterAllowed(
                    s, type, viaAd = true, richWallet,
                    baseHealth = 5, enemiesOnField = 5, nowMs = now,
                ),
            )
        }
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

    /**
     * GERCEK KISIT — savasa kapsamli model. Ornek Hava Destegi'nin ucretli
     * yolundan Us Tamiri'nin ucretli yoluna tasindi (2026-08-21) ve ayni anda
     * REKLAM sayacinin sifirlanmasi da olcume alindi.
     *
     * Eski hali yalnizca `paidUses` sifirlanmasini olcuyordu. Hava Destegi
     * reklam-only olunca savas ici hakkinin TAMAMI `adUses`'ta tutuluyor; o alan
     * savaslar arasi sifirlanmasa "bolumden cik-gir, iki hakki tekrar al" degil
     * TERSI bir hata olurdu (hak hic yenilenmezdi). Iki sayac da artik olculuyor.
     */
    @Test
    fun boostersAreNotInventoryAndDoNotSurviveTheBattle() {
        // Savasa kapsamli model: yeni savas sayaclari sifirlar, yalnizca gunluk reklam
        // sayaci tasinir. Stoklama olsaydi "20 tane biriktir, son bolumu ez" acilirdi.
        var s = state(level = 10)

        val paid = boosterAllowed(s, BoosterType.BASE_REPAIR, viaAd = false, richWallet, baseHealth = 5, nowMs = 0L)
        assertTrue(paid.isAllowed)
        s = useBooster(s, BoosterType.BASE_REPAIR, viaAd = false, decision = paid, nowMs = 0L, repairedHealth = 4)
        assertEquals(1, s.paidUsesOf(BoosterType.BASE_REPAIR))
        assertEquals(4, s.repairedHealth)

        val viaAd = boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 3, nowMs = 0L)
        assertTrue(viaAd.isAllowed)
        s = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = true, decision = viaAd, nowMs = 0L)
        assertEquals(1, s.adUsesOf(BoosterType.AIR_SUPPORT))
        assertEquals(1, s.adViewsToday)

        val next = BoosterState.startBattle(11, s.adViewsToday)
        assertEquals(0, next.paidUsesOf(BoosterType.BASE_REPAIR))
        assertEquals(0, next.adUsesOf(BoosterType.AIR_SUPPORT))
        assertEquals(0, next.repairedHealth)
        assertTrue("bekleme sayaci da savasla birlikte olmeli", next.lastUseMs.isEmpty())
        // Tasinan TEK sey gunluk reklam sayaci — farming kapisi bu.
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

    /**
     * GERCEK KISIT — ve hava destegi AD_ONLY olduktan sonra ONCEKINDEN DAHA
     * onemli hale geldi.
     *
     * Eskiden hava destegini frenleyen iki sey vardi: 45 sn bekleme VE Tedarik
     * fiyati. Tedarik yolu kalkinca geriye TEK fren kaldi — bu bekleme. Iki
     * kullanimin ust uste binmesini engelleyen baska hicbir sey yok
     * (bkz. [allAirSupportUsesInOneBattleStillCannotKillAnything]: 2 x 0,45
     * ekrandaki her seyi %90 siler; ayni saniyede olurlarsa dalga fiilen biter).
     * Bu yuzden test ucretli yoldan reklam yoluna tasindi ve IKI reklam
     * kullanimi arasini olcuyor.
     */
    @Test
    fun cooldownBlocksBackToBackAirSupport() {
        var s = state(level = 10)
        val d = boosterAllowed(
            s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 5, nowMs = 1_000L,
        )
        assertTrue(d.isAllowed)
        s = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = true, decision = d, nowMs = 1_000L)

        // Ikinci reklam hakki DURUYOR (savas basina 2) ama bekleme onu tutuyor.
        assertEquals(1, s.adUsesOf(BoosterType.AIR_SUPPORT))
        val tooSoon = boosterAllowed(
            s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 5, nowMs = 2_000L,
        )
        assertTrue("bekleme suresi uygulanmali", tooSoon is BoosterDecision.Cooldown)
        assertEquals(
            EconomyConfig.AIR_SUPPORT_COOLDOWN_MS - 1_000L,
            (tooSoon as BoosterDecision.Cooldown).remainingMs,
        )
        assertTrue(
            boosterAllowed(
                s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 5,
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
        // ⚠ 2026-08-21: us tamiri ORAN degil DUZ CAN veriyor (coin 4 / reklam 7).
        // Eski beklentiler %40'a gore yazilmisti (20 canda 8).
        assertEquals(0, baseRepairAmount(20, 20))
        // Kaybedilenden fazlasini vermez.
        assertEquals(3, baseRepairAmount(17, 20))   // kayip 3 < 4, kayip kadar
        assertEquals(4, baseRepairAmount(4, 20))    // duz 4
        assertEquals(7, baseRepairAmount(4, 20, viaAd = true))  // reklam yolu 7
        assertEquals(4, baseRepairAmount(1, 30))    // maks candan BAGIMSIZ
    }

    // ---------------------------------------------------------------------------------
    // 4b. HEDEFSIZ HAVA DESTEGI — geri alinamaz bos harcamanin ekonomi katmani kapisi
    // ---------------------------------------------------------------------------------
    // Hava Destegi ekrandaki dusmanlara vurur. Saha bosken (hazirlik fazi, dalgalar
    // arasi) kullanim savas basina IKI reklam hakkindan birini ve gunluk reklam
    // butcesinden bir gosterimi HICBIR SEYE yakar, ustune 45 sn bekleme baslatir;
    // geri alma yok. Kapi bu yuzden UI'da degil, tek dogruluk kaynagi olan ekonomi
    // katmanindadir.
    //
    // ⚠ 2026-08-21 — bu bolumun testleri ucretli yoldan reklam yoluna tasindi.
    // KAPININ SEBEBI GUCLENDI, ZAYIFLAMADI: eskiden bosa giden sey Tedarik'ti
    // (savas ici, yenilenebilir); simdi bosa giden sey oyuncunun IZLEDIGI BIR
    // REKLAM. Yanlis basma bedeli artik 30 saniyelik izleme.

    @Test
    fun airSupportIsRefusedWhenThereAreNoTargets() {
        val s = state(level = 10)
        assertEquals(
            BoosterDecision.NoEffect,
            boosterAllowed(
                s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 0,
            ),
        )
    }

    @Test
    fun refusedAirSupportCostsNothingBurnsNoUseAndStartsNoCooldown() {
        // Ret ucretsiz OLMALI: aksi halde oyuncu yanlis basmanin bedelini savasin
        // geri kalaninda oder (savas ici hak + gunluk reklam hakki + bekleme).
        val s = state(level = 10)
        val decision = boosterAllowed(
            s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet,
            enemiesOnField = 0, nowMs = 5_000L,
        )
        assertEquals(BoosterDecision.NoEffect, decision)
        assertFalse(decision.isAllowed)

        val after = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = true, decision = decision, nowMs = 5_000L)
        assertSame("reddedilen kullanim durumu degistirmemeli", s, after)
        assertEquals(0, after.paidUsesOf(BoosterType.AIR_SUPPORT))
        assertEquals(0, after.adUsesOf(BoosterType.AIR_SUPPORT))
        // GUNLUK REKLAM HAKKI DA YANMAMALI. Reklam-only modelde bu, savas ici
        // haktan daha degerli sayac: gunde 4, savasta 2.
        assertEquals("gunluk reklam sayaci yanmamali", 0, after.adViewsToday)
        assertTrue("bekleme baslamamali", after.lastUseMs.isEmpty())
        assertSame("coin dusulmemeli", richWallet, payForBooster(richWallet, decision))

        // Bekleme gercekten islememis olmali: hedef gelince ayni anda izin cikar.
        assertTrue(
            boosterAllowed(
                after, BoosterType.AIR_SUPPORT, viaAd = true, richWallet,
                enemiesOnField = 1, nowMs = 5_000L,
            ).isAllowed
        )
    }

    @Test
    fun airSupportIsAllowedAsSoonAsOneEnemyIsOnTheField() {
        val s = state(level = 10)
        val d = boosterAllowed(
            s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 1,
        )
        // Fiyat 0 / para birimi AD_ONLY / viaAd true: uc alan da yeni modelin
        // tanimi. Biri kayarsa (ornegin fiyat yeniden Tedarik'e baglanirsa)
        // burada gorulur.
        assertEquals(BoosterDecision.Allowed(0, BoosterCurrency.AD_ONLY, true), d)
    }

    /**
     * GERCEK KISIT — ve ad bayatlamisti. Eski adi `noTargetGateAlsoCoversTheAdPath`
     * idi; "AYRICA reklam yolunu da kapsar" ifadesi ucretli yolun var oldugu ve
     * asil yol oldugu bir dunyayi anlatiyor. Hava Destegi'nde artik TEK yol
     * reklam; kilitlenmesi gereken sey kapinin **her iki reklam kullanimini** da
     * kapsamasi.
     */
    @Test
    fun noTargetGateCoversEveryAirSupportUseNotJustTheFirst() {
        var s = state(level = 10)
        val first = boosterAllowed(
            s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 3, nowMs = 0L,
        )
        assertTrue(first.isAllowed)
        s = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = true, decision = first, nowMs = 0L)

        // Ikinci kullanim: bekleme bitti, hak duruyor — kapiyi tutan tek sey hedef.
        val late = EconomyConfig.AIR_SUPPORT_COOLDOWN_MS
        assertEquals(1, s.adUsesOf(BoosterType.AIR_SUPPORT))
        assertEquals(
            BoosterDecision.NoEffect,
            boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 0, nowMs = late),
        )
        assertTrue(
            boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 2, nowMs = late)
                .isAllowed
        )
    }

    /**
     * GERCEK KISIT (kontrol sirasi sozlesmesi) — ama ORNEK ZORUNLU OLARAK DEGISTI.
     *
     * Eski hali "hedef yok VE Tedarik da yetmiyor -> NoEffect" diyordu. Hava
     * Destegi AD_ONLY olunca onun icin **odeme gucu kontrolu diye bir sey
     * kalmadi** (reklam yolu her zaman fiyat 0 ile geciyor), yani o ornek
     * uzerinde siralamayi ISPATLAMAK artik imkansiz — test gecse bile hicbir sey
     * olcmuyor olurdu. Odeme gucu kontrolu olan tek guclendirici Us Tamiri
     * kaldi, ornek oraya tasindi: cani TAM olan ve coini HIC olmayan oyuncuya
     * InsufficientCoins degil NoEffect donmeli, yoksa oyuncuya "tek eksigin
     * para" YANLIS bilgisi verilir.
     *
     * Ikinci yari sirali kontrolun hava destegi tarafini korumaya devam ediyor:
     * kullanim tavani NoEffect'ten ONCE geliyor.
     */
    @Test
    fun noEffectIsDecidedBeforeAffordability() {
        val broke = PlayerWallet(coins = 0, unlockedLevels = (1..22).toSet())
        assertEquals(
            BoosterDecision.NoEffect,
            boosterAllowed(
                state(level = 10), BoosterType.BASE_REPAIR, viaAd = false, broke,
                baseHealth = 20, maxBaseHealth = 20,
            ),
        )

        // KONTROL SIRASININ HAVA DESTEGI TARAFI: hak tukendiginde cevap, sahada
        // hedef olup olmamasindan BAGIMSIZ olarak AdLimitReached olmali. Ters
        // sirada olsaydi hakki bitmis oyuncuya "hedef yok" denirdi ve oyuncu
        // dusman bekleyerek zaman kaybederdi.
        var s = state(level = 10)
        var now = 0L
        repeat(BoosterType.AIR_SUPPORT.adUsesPerBattle) {
            val d = boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 4, nowMs = now)
            assertTrue(d.isAllowed)
            s = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = true, decision = d, nowMs = now)
            now += EconomyConfig.AIR_SUPPORT_COOLDOWN_MS
        }
        assertEquals(
            BoosterDecision.AdLimitReached,
            boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 0, nowMs = now),
        )
    }

    @Test
    fun enemyCountDoesNotAffectTheOtherBoosters() {
        // Regresyon: yeni parametre yalnizca AIR_SUPPORT icin anlamli.
        // Us Tamiri hala YALNIZCA "can zaten tam" durumunda NoEffect doner.
        assertEquals(
            BoosterDecision.NoEffect,
            boosterAllowed(
                state(level = 10), BoosterType.BASE_REPAIR, viaAd = false, richWallet,
                baseHealth = 20, maxBaseHealth = 20, enemiesOnField = 0,
            ),
        )
        assertTrue(
            "bos saha tamiri engellememeli — tamirin hedefle ilgisi yok",
            boosterAllowed(
                state(level = 10), BoosterType.BASE_REPAIR, viaAd = false, richWallet,
                baseHealth = 5, maxBaseHealth = 20, enemiesOnField = 0,
            ).isAllowed
        )
        assertTrue(
            boosterAllowed(state(level = 10), BoosterType.EMERGENCY_SUPPLY, viaAd = true, richWallet, enemiesOnField = 0)
                .isAllowed
        )
    }

    @Test
    fun unknownEnemyCountPreservesLegacyBehaviourExactly() {
        // Varsayilan deger NEDEN -1: 0 gecerli ve anlamli bir sayidir ("saha bos").
        // Varsayilan 0 olsaydi, sahayi bildirmeyen her cagiran (simulasyon, birim
        // testleri, ileride tutorial/otomatik oynatma) sessizce reddedilirdi.
        assertEquals(-1, ENEMY_COUNT_UNKNOWN)
        val s = state(level = 10)
        val withTargets = boosterAllowed(
            s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 7,
        )
        val explicitUnknown = boosterAllowed(
            s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet,
            enemiesOnField = ENEMY_COUNT_UNKNOWN,
        )
        val defaulted = boosterAllowed(
            s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet,
        )
        assertEquals(withTargets, explicitUnknown)
        assertEquals(withTargets, defaulted)
        assertTrue(defaulted.isAllowed)
    }

    // =================================================================================
    // 4c. HAVA DESTEGI REKLAM-ONLY (2026-08-21 kullanici karari) — YENI TESTLER
    // =================================================================================
    // Istenen davranis iki cumle: (1) her bolume DEAKTIF baslar, yalnizca rewarded
    // reklamla aktiflesir; (2) bir bolumde IKI kez izlenip IKI kez cagrilabilir.
    //
    // Bu iki cumleyi degistiren bir kod degisikligi bugune kadar HICBIR testi
    // kirmiyordu: `paidUsesPerBattle`/`adUsesPerBattle` degerleri yalnizca dolayli
    // olarak (baska kurallarin ornegi olarak) goruluyordu. Asagidaki testler
    // davranisin KENDISINI hedef aliyor.

    /**
     * ISTENEN DAVRANIS 1 — hava destegi her bolume DEAKTIF baslar.
     *
     * "Deaktif baslamak" ekonomi katmaninda tek bir seye esittir: ucretli yol
     * YOKTUR, dolayisiyla savasin ilk saniyesinde reklam izlemeden hicbir sekilde
     * cagrilamaz. Kilidi acan tek anahtar rewarded reklam.
     */
    @Test
    fun airSupportHasNoPaidPathSoItStartsEveryBattleDeactivated() {
        assertEquals(BoosterCurrency.AD_ONLY, BoosterType.AIR_SUPPORT.currency)
        assertFalse(BoosterType.AIR_SUPPORT.hasPaidPath)
        assertEquals(0, BoosterType.AIR_SUPPORT.paidUsesPerBattle)

        val loaded = PlayerWallet(coins = 1_000_000, unlockedLevels = (1..22).toSet())
        for (level in BoosterType.AIR_SUPPORT.unlockLevel..EconomyConfig.CAMPAIGN_LEVELS) {
            // Ne coin, ne Tedarik: hicbir bakiye kapiyi acmaz.
            assertEquals(
                "L$level: hava destegi ucretli olarak alinabilmis",
                BoosterDecision.PaidPathUnavailable,
                boosterAllowed(
                    state(level), BoosterType.AIR_SUPPORT, viaAd = false, loaded,
                    supplyOnHand = 1_000_000, enemiesOnField = 5,
                ),
            )
            // Reklam yolu ise ilk saniyeden itibaren acik (kilit yalnizca reklam).
            assertTrue(
                "L$level: reklam yolu kapali",
                boosterAllowed(
                    state(level), BoosterType.AIR_SUPPORT, viaAd = true, loaded, enemiesOnField = 5,
                ).isAllowed,
            )
        }
    }

    /**
     * ISTENEN DAVRANIS 2 — bir bolumde IKI kez izlenip IKI kez cagrilabilir.
     *
     * Sayi burada ozellikle ELLE yaziliyor (`2`) cunku kullanicinin istedigi sey
     * "enum'da ne yaziyorsa o" degil, tam olarak iki. `adUsesPerBattle`'i biri 1
     * veya 3 yaparsa bu test kirilmali ve karar yeniden konusulmali.
     */
    @Test
    fun airSupportCanBeWatchedAndCalledTwiceInTheSameBattle() {
        assertEquals("hava destegi savas basina 2 reklam cagrisi olmali", 2, BoosterType.AIR_SUPPORT.adUsesPerBattle)
        assertEquals(2, BoosterType.AIR_SUPPORT.maxUsesPerBattle)

        var s = state(level = 10)
        var now = 0L
        repeat(2) { i ->
            val d = boosterAllowed(
                s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 5, nowMs = now,
            )
            assertEquals(
                "cagri #${i + 1} bedava reklam yolu olmali",
                BoosterDecision.Allowed(0, BoosterCurrency.AD_ONLY, true), d,
            )
            s = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = true, decision = d, nowMs = now)
            now += EconomyConfig.AIR_SUPPORT_COOLDOWN_MS
        }

        assertEquals("iki cagri islenmis olmali", 2, s.adUsesOf(BoosterType.AIR_SUPPORT))
        assertEquals("ucretli sayac hic artmamali", 0, s.paidUsesOf(BoosterType.AIR_SUPPORT))
        assertEquals("iki reklam izlenmis sayilmali", 2, s.adViewsToday)
        // Tedarik harcamiyor: cuzdan da savas ici Tedarik de disarida kaliyor.
        assertSame(richWallet, payForBooster(richWallet, BoosterDecision.Allowed(0, BoosterCurrency.AD_ONLY, true)))

        // UCUNCU cagri yok. "Reklam izledigim surece sinirsiz" DEGIL.
        assertEquals(
            BoosterDecision.AdLimitReached,
            boosterAllowed(s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 5, nowMs = now),
        )
    }

    /**
     * TUTARLILIK — AD_ONLY bir guclendirici hicbir para biriminden hicbir sey
     * tahsil edemez.
     *
     * ⚠ BILINEN BAYAT VERI (rapor edildi): [boosterPrice] hâlâ
     * `AIR_SUPPORT` icin sifir OLMAYAN bir Tedarik fiyati donduruyor
     * (L4 = 125 ... L22 = 269) ve kendi KDoc'u "AD_ONLY tipler ... icin 0" diyor,
     * yani fonksiyon kendi sozlesmesiyle celisiyor. Bugun bu zararsiz, cunku
     * ucretli yol [BoosterDecision.PaidPathUnavailable] ile daha ilk dalda
     * kapaniyor ve sayiya HIC ulasilmiyor.
     *
     * Bu test o "ulasilamazligi" kilitler. Sayiyi burada dogru kabul etmiyorum;
     * kimsenin ona ulasamayacagini kanitliyorum. Biri yarin ucretli yolu geri
     * acarsa bayat fiyat sessizce yururluge girmez, once burasi patlar.
     */
    @Test
    fun adOnlyBoostersCanNeverChargeAnyCurrency() {
        val loaded = PlayerWallet(coins = 1_000_000, unlockedLevels = (1..22).toSet())
        BoosterType.entries.filter { it.currency == BoosterCurrency.AD_ONLY }.forEach { type ->
            assertFalse("$type hasPaidPath true", type.hasPaidPath)
            assertEquals("$type ucretli kullanim hakki acilmis", 0, type.paidUsesPerBattle)

            for (level in type.unlockLevel..EconomyConfig.CAMPAIGN_LEVELS) {
                assertEquals(
                    "$type L$level: ucretli yol acilmis — bayat fiyat yururluge girdi",
                    BoosterDecision.PaidPathUnavailable,
                    boosterAllowed(
                        state(level), type, viaAd = false, loaded,
                        supplyOnHand = 1_000_000, enemiesOnField = 5,
                    ),
                )
                val viaAd = boosterAllowed(
                    state(level), type, viaAd = true, loaded, enemiesOnField = 5,
                )
                assertTrue("$type L$level reklam yolu kapali", viaAd.isAllowed)
                assertEquals(
                    "$type L$level: reklam yolu ucret tahakkuk ettirmis",
                    BoosterDecision.Allowed(0, BoosterCurrency.AD_ONLY, true),
                    viaAd,
                )
                assertSame("$type L$level cuzdana dokunmus", loaded, payForBooster(loaded, viaAd))
            }
        }
    }

    /**
     * SINIR — "savas basina iki cagri" bir GARANTI DEGIL, bir TAVAN.
     *
     * Gunluk guclendirici-reklam butcesi [EconomyConfig.BOOSTER_AD_VIEWS_PER_DAY]
     * = 4 ve tek basina hava destegi bunun IKISINI yiyor. Yani hava destegi
     * "her bolumde iki kez" degil, gunde en fazla iki BOLUM boyunca iki kez.
     * Ucuncu bolumden itibaren — Acil Tedarik ve Us Tamiri reklamlari hic
     * izlenmese bile — cevap [BoosterDecision.DailyAdLimitReached] olur.
     *
     * Bu test o sinirin VARLIGINI kilitliyor (gunluk butce, savas basi hakkin
     * ustundedir — farming kalkani budur), sayisini onaylamiyor. Butce/hak
     * oraninin urun tarafinda yeniden konusulmasi gerekiyor: reklam-only bir
     * guclendiricinin gunun buyuk kisminda TAMAMEN erisilemez olmasi, karari
     * veren "ad izlemeye tesvik etmeliyiz" gerekcesiyle celisir. Sayi
     * degistiginde bu test kirilir ve karar tekrar onune gelir.
     */
    @Test
    fun dailyAdBudgetOutranksTheTwoAirSupportUsesPerBattle() {
        val perBattle = BoosterType.AIR_SUPPORT.adUsesPerBattle
        val perDay = EconomyConfig.BOOSTER_AD_VIEWS_PER_DAY
        // ⚠ 4 -> 12 (2026-08-21). Bu satir KASITLI olarak sayiyi cakiyor ki
        // butce degisince karar tekrar onumuze gelsin — nitekim geldi.
        //
        // 4 ile hava destegi gunde YALNIZCA IKI bolumde cagrilabiliyordu; tek
        // basina gunluk hakkin yarisini yiyordu. Reklam-only bir guclendiricinin
        // gunun buyuk kisminda erisilemez olmasi, tasarimin gerekcesiyle
        // ("reklam izlemeye tesvik") dogrudan celisiyordu.
        //
        // 12 = uc dolu savas. Sinir KALKMADI: isi artik enflasyon degil
        // REKLAM YORGUNLUGU.
        assertEquals("gunluk guclendirici-reklam butcesi degisti", 12, perDay)

        var adViews = 0
        var fullBattles = 0
        repeat(10) {
            var s = state(level = 10, adViewsToday = adViews)
            var now = 0L
            var callsThisBattle = 0
            repeat(perBattle) {
                val d = boosterAllowed(
                    s, BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 5, nowMs = now,
                )
                if (d.isAllowed) {
                    s = useBooster(s, BoosterType.AIR_SUPPORT, viaAd = true, decision = d, nowMs = now)
                    callsThisBattle++
                    now += EconomyConfig.AIR_SUPPORT_COOLDOWN_MS
                }
            }
            adViews = s.adViewsToday
            if (callsThisBattle == perBattle) fullBattles++
        }

        assertEquals("gunluk butce asilmis — farming kalkani delinmis", perDay, adViews)
        assertEquals("tam iki cagri alinabilen bolum sayisi", perDay / perBattle, fullBattles)
        assertEquals(
            BoosterDecision.DailyAdLimitReached(perDay),
            boosterAllowed(
                state(level = 10, adViewsToday = adViews),
                BoosterType.AIR_SUPPORT, viaAd = true, richWallet, enemiesOnField = 5,
            ),
        )
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

    /**
     * GERCEK KISIT — coin YALNIZCA COIN fiyatli guclendiricide ve YALNIZCA bir kez
     * duser. Ornegin "coin dusmeyen guclendirici" yarisi Hava Destegi'nin Tedarik
     * yolundan onun reklam yoluna tasindi (2026-08-21); dahasi artik tek ornek
     * yerine TUM guclendiriciler ve TUM yollar taraniyor, cunku bu kural bir
     * ornegin ozelligi degil enum genelinde gecerli bir degismez.
     */
    @Test
    fun coinPathDebitsExactlyOnceAndOnlyForCoinPricedBoosters() {
        val wallet = PlayerWallet(coins = 1_000, unlockedLevels = (1..22).toSet())
        val repair = boosterAllowed(state(10), BoosterType.BASE_REPAIR, viaAd = false, wallet, baseHealth = 5)
        assertTrue(repair.isAllowed)
        val after = payForBooster(wallet, repair)
        assertEquals(1_000 - boosterPrice(BoosterType.BASE_REPAIR, 10), after.coins)
        // "Tam olarak bir kez": ayni karar tekrar odenmez diye bir sey yok, ama
        // ayni kararin BEDELI sabit ve tek kalemdir — ikinci cagri ikinci kez
        // duser, ucuncu bir gizli kesinti yoktur.
        assertEquals(1_000 - 2 * boosterPrice(BoosterType.BASE_REPAIR, 10), payForBooster(after, repair).coins)

        // Coin fiyatli OLMAYAN her yol cuzdana DOKUNMAZ (iki ekonomi arasinda
        // donusum yok, GDD D.4).
        BoosterType.entries.forEach { type ->
            val viaAdDecision = boosterAllowed(
                state(10), type, viaAd = true, wallet, baseHealth = 5, enemiesOnField = 5,
            )
            assertSame("$type reklam yolu coin dusmus", wallet, payForBooster(wallet, viaAdDecision))
            if (!type.hasPaidPath) {
                val paidDecision = boosterAllowed(
                    state(10), type, viaAd = false, wallet, supplyOnHand = 9_999, enemiesOnField = 5,
                )
                assertEquals(BoosterDecision.PaidPathUnavailable, paidDecision)
                assertSame("$type ucretli yolu yokken coin dusmus", wallet, payForBooster(wallet, paidDecision))
            }
        }
    }

    /**
     * GERCEK KISIT — GDD D.4: coin ve Tedarik arasinda DONUSUM YOK.
     *
     * ⚠ 2026-08-21 — ORNEK KAYBOLDU, KURAL KALDI. Bu test eskiden Hava
     * Destegi'ni "Tedarik fiyatli guclendirici" ornegi olarak kullaniyordu.
     * Hava Destegi AD_ONLY'ye cevrilince depoda **Tedarik fiyatli hicbir
     * guclendirici kalmadi**, yani eski govdenin yeniden yazilabilecegi bir
     * ornek yok.
     *
     * Bu, testi silmek icin degil GENISLETMEK icin sebep: ornek uzerinden
     * dogrulanan kural artik ENUM UZERINDEN dogrulaniyor. Iddia sudur ve
     * eskisinden GUCLUDUR: hicbir guclendirici coini Tedarike, Tedariki de
     * coine cevirebilecek bir yol acmaz — ne fiyat tarafinda, ne odul
     * tarafinda. Biri yarin AIR_SUPPORT'u tekrar SUPPLY yaparsa bu test
     * ayakta kalir ve `supplyOnHand` kontrolunun gercekten calistigini
     * dogrulamaya devam eder.
     */
    @Test
    fun noBoosterConvertsBetweenCoinsAndSupplyInEitherDirection() {
        val brokeCoins = PlayerWallet(coins = 0, unlockedLevels = (1..22).toSet())
        val richCoins = PlayerWallet(coins = 100_000, unlockedLevels = (1..22).toSet())

        BoosterType.entries.forEach { type ->
            when (type.currency) {
                // COIN fiyatli: Tedarik BOLLUGU coini ikame edemez.
                BoosterCurrency.COIN -> {
                    val d = boosterAllowed(
                        state(10), type, viaAd = false, brokeCoins,
                        supplyOnHand = 1_000_000, baseHealth = 5, enemiesOnField = 5,
                    )
                    assertTrue(
                        "$type: Tedarik bollugu coin eksigini kapatti — donusum acildi",
                        d is BoosterDecision.InsufficientCoins,
                    )
                }
                // SUPPLY fiyatli: coin ZENGINLIGI Tedariki ikame edemez.
                BoosterCurrency.SUPPLY -> {
                    val d = boosterAllowed(
                        state(10), type, viaAd = false, richCoins,
                        supplyOnHand = 0, baseHealth = 5, enemiesOnField = 5,
                    )
                    assertTrue(
                        "$type: coin zenginligi Tedarik eksigini kapatti — donusum acildi",
                        d is BoosterDecision.InsufficientSupply,
                    )
                }
                // AD_ONLY: satin alinabilir bir yol YOK, dolayisiyla hangi para
                // biriminden ne kadar olursa olsun kapi acilmaz.
                BoosterCurrency.AD_ONLY -> {
                    assertEquals(
                        "$type: AD_ONLY oldugu halde ucretli bir yol acilmis",
                        BoosterDecision.PaidPathUnavailable,
                        boosterAllowed(
                            state(10), type, viaAd = false, richCoins,
                            supplyOnHand = 1_000_000, baseHealth = 5, enemiesOnField = 5,
                        ),
                    )
                }
            }
        }

        // Ters yon: hicbir guclendirici coin URETMEZ. Tek coin etkisi Us
        // Tamiri'nin sink'idir; kaynak yonu yoktur.
        BoosterType.entries.forEach { type ->
            val d = boosterAllowed(
                state(10), type, viaAd = true, richCoins, baseHealth = 5, enemiesOnField = 5,
            )
            assertTrue(
                "$type coin bakiyesini ARTIRDI — guclendirici coin kaynagi olmus",
                payForBooster(richCoins, d).coins <= richCoins.coins,
            )
        }
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

    /**
     * FAZ 10.1 — YUKARIDAKI TESTIN ACIK BIRAKTIGI DELIK.
     *
     * `airSupportCanNeverClearAWaveByItself` yalnizca TEK kullanimi olcuyordu. Hava
     * Destegi'nin savas basina **iki** kullanimi var (2026-08-21'den beri: 0 ucretli
     * + 2 rewarded; oncesinde 1 + 1 — TOPLAM degismedi, bu testin kisiti da
     * degismedi) ve bekleme 45 sn, yani ikisi ayni uzun dalgada kullanilabilir. 0,60 oraniyla
     * 2 x 0,60 = 1,20 > 1,0 idi: iki kullanim ekrandaki her dusmani, KOMUTA TANKI
     * dahil, dogrudan olduruyordu. Yani pay-to-win kalkani kagit uzerinde vardi ama
     * sayilar onu ihlal ediyordu.
     *
     * Dogru kisit **savas basina toplam** uzerinde tanimli olmali.
     */
    @Test
    fun allAirSupportUsesInOneBattleStillCannotKillAnything() {
        val total = BoosterType.AIR_SUPPORT.maxUsesPerBattle *
            EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION
        assertEquals("savas basina kullanim sayisi degisti — orani yeniden hesapla", 2, BoosterType.AIR_SUPPORT.maxUsesPerBattle)
        assertTrue(
            "savas basina toplam hava destegi hasari maks canin %.0f%%'i — %%100'e ulasirsa "
                .format(total * 100) + "guclendirici tek basina oldurur",
            total < 1.0,
        )
        // Son vurusun kulelerden gelmesi icin anlamli bir pay kalmali (>= %10).
        // EPSILON KASITLI: 2 x 0,45 ikili tabanda tam 0,9 etmez (0,90000000000000002),
        // yani kalan pay 0,09999999999999998 cikar ve ciplak `>= 0.10` tasarimin TAM
        // sinirinda kirilir. Esik %10'un kendisi; epsilon yalnizca kayan nokta
        // gurultusunu tolere eder, gercek bir ihlali (or. 0,50 -> pay 0,0) hâlâ yakalar.
        assertTrue(
            "kalan can payi cok ince: %.4f".format(1.0 - total),
            1.0 - total >= 0.10 - 1e-9,
        )
    }

    /**
     * Hava Destegi HASARININ **oran** olmasi (sabit sayi degil) kalibrasyon-guvenli
     * olmasini sagliyor: dusman cani x3,5 edildiginde (Faz 10) kurtarma degeri
     * kendiliginden olcegi tuttu. Bu test o ozelligi kilitler — biri sabit hasara
     * cevirmeye kalkarsa oran testi olmadan fark edilmez.
     *
     * Ayni sebeple zirhtan da bagimsizdir: zirh 0,55 -> 0,78/0,86 cikarken hava
     * destegi zirhli/tank karsisinda deger kaybetmedi, dolayisiyla "agir sizdi ve
     * dogru muhimmatim yok" durumunun cevabi olmaya devam ediyor.
     */
    @Test
    fun airSupportRescueValueScalesWithEnemyHealthInsteadOfDecaying() {
        val facts = GameConfigCampaignFacts
        val infantry = facts.enemyMaxHp.getValue("INFANTRY")
        val tank = facts.enemyMaxHp.getValue("TANK")
        val fraction = EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION

        // Faz 12.1: taban carpan x3,5 -> x1,1 olarak yeniden olculdu (bolum 1
        // gecilemiyordu, bkz. GameConfig.ENEMY_SPECS). Bu testin ISPATLADIGI
        // SEY ISTE BU: hava destegi ORAN oldugu icin kalibrasyon her iki yone
        // gittiginde de kendiliginden olcegi tuttu, tek satir kod degismedi.
        assertEquals(82.0, infantry, 0.5)
        assertEquals(638.0, tank, 0.5)

        // Tek kullanim hicbirini oldurmez ama ikisinde de anlamli bir dilim alir.
        assertTrue("piyade tek vurusta olmemeli", fraction * infantry < infantry)
        assertTrue("silinen can hissedilir olmali", fraction >= 0.30)

        // Tank karsisinda: iki kullanim bile tanki bitirmez (yukaridaki kisit),
        // ama kalan can bir kademe-2 Gatling'in makul surede bitirebilecegi kadar
        // olmali, yoksa guclendirici "ise yaramaz" hissi verir.
        val tankLeftAfterBothUses = tank * (1.0 - 2 * fraction)
        assertTrue(
            "iki hava destegi sonrasi tankta %.0f can kaliyor — kurtarma hissi yok"
                .format(tankLeftAfterBothUses),
            tankLeftAfterBothUses in 1.0..(tank * 0.25),
        )
    }

    /**
     * ⚠⚠ 2026-08-21 — [airSupportNeverCostsLessThanAFullyUpgradedTower] ile AYNI
     * DURUMDA: bu test de artik ulasilamayan bir fiyati koruyor. Hava Destegi
     * Tedarik harcamadigi icin "3. kule mi hava destegi mi" TAKTIK KARARI ORTADAN
     * KALKTI (kullanicinin kendi patch notu da bunu acikca soyluyor). Testin
     * olctugu sey bugun oyuncuya hicbir sekilde gorunmuyor.
     *
     * Fiyat fonksiyonu duzeltilene kadar burada kaliyor; ulasilamazlik kilidi
     * [adOnlyBoostersCanNeverChargeAnyCurrency].
     *
     * Asagidaki gerekce yalnizca ucretli yol geri gelirse tekrar anlam kazanir:
     *
     * Hava Destegi fiyati **acilis bolumunde tam olarak bir kademe-2 Gatling** kadar
     * olmali: "3. kule mi hava destegi mi" karari ancak iki secenek ayni parayi
     * istiyorsa gercek bir karardir.
     *
     * Faz 10.1'de bu oran KONTROL EDILDI ve fiyat DEGISMEDI: 96 + 8(L-1), tasarlanan
     * kadronun %19-21'i (L4 120/620 ... L8 152/725) — bolumler boyunca sabit "bir
     * kule kadar". Dolayisiyla Tedarik bollugu degistigi halde fiyat kalibrasyonu
     * hâlâ dogru.
     */
    @Test
    fun airSupportCostsAboutOneFullyUpgradedGatlingAtUnlock() {
        val tierTwoGatling = GameConfigCampaignFacts.tierTwoCost("MACHINE_GUN")
        assertEquals(125, tierTwoGatling)

        val atUnlock = boosterPrice(BoosterType.AIR_SUPPORT, BoosterType.AIR_SUPPORT.unlockLevel)
        assertEquals("acilista fiyat TAM olarak bir kademe-2 Gatling", tierTwoGatling, atUnlock)

        // ---------------------------------------------------------------
        // FIYAT TAVANI — ARTIK 55 BOLUMUN TAMAMINDA
        // ---------------------------------------------------------------
        // Bu dongu bir zamanlar `unlockLevel..SupplyBudgetModel.MODELLED_LEVELS`
        // idi; MODELLED_LEVELS 8'den 55'e cikarilirken elle `..8`e sabitlendi ve
        // gerekce olarak "ust bolumlerin rampasi `boosterPriceRisesWithLevel`de
        // kilitli" yazildi. **Depoda oyle bir test yok** ve var olan
        // `pricesNeverDecreaseWithLevel` yalnizca monotonlugu kontrol ediyor,
        // bu testin varlik sebebi olan UST SINIRI degil. Sonuc: fiyat tavani
        // 55 bolumun 47'sinde denetimsiz kaliyordu.
        //
        // KAPSAM 5 BOLUMDEN 55 BOLUME ACILDI ve tavan BUTCEYE baglandi.
        //
        // Eski olcut "tasarlanan kadronun %15-25'i" idi ve yalnizca L4..L8'i
        // kapsiyordu. Iki sorunu vardi: (a) kapsam — 55 bolumun 47'sinde
        // guclendirici fiyat tavani DENETIMSIZDI (dongu `..8`e elle
        // sabitlenmisti ve gerekce olarak depoda VAR OLMAYAN bir teste atif
        // yapiliyordu); (b) olcut kadro BUYUKLUGUNE duyarliydi — ayni fiyat,
        // alti kulelik bir kadronun %20'si iken dort kulelik bir kadronun
        // %34'u olur ve bu, guclendiricinin fiyatiyla ilgili hicbir sey
        // soylemez.
        //
        // Tavan artik bolumun TOPLAM Tedarik butcesine gore: guclendirici hicbir
        // bolumde ekonominin dortte birini yiyemez. Olculen aralik %10,9 (L13)
        // .. %17,1 (L53). Bandin ALT ucu ayri ve MUTLAK bir testle korunuyor:
        // [airSupportNeverCostsLessThanAFullyUpgradedTower].
        for (level in BoosterType.AIR_SUPPORT.unlockLevel..EconomyConfig.CAMPAIGN_LEVELS) {
            val share = 100.0 * boosterPrice(BoosterType.AIR_SUPPORT, level) /
                SupplyBudgetModel.supplyBudget(level)
            assertTrue(
                "L$level: hava destegi bolum butcesinin %%%.1f'i — %%25 tavani asildi"
                    .format(share),
                share <= 25.0,
            )
        }
    }

    /**
     * Us Tamiri'nin coin fiyati yildiz atlamasini ASLA karsilamamali. Faz 10.1'de
     * yeniden dogrulandi: coin ekonomisi (odul formulu, yildiz carpanlari) Faz 10'dan
     * beri degismedi, dolayisiyla `repairPriceExceedsEveryStarJumpItCouldEverBuy`
     * kisiti aynen gecerli. Burada marjin de kilitleniyor ki daralirsa gorulsun.
     */
    @Test
    fun repairPriceKeepsAGrowingMarginOverTheBiggestStarJump() {
        for (level in BoosterType.BASE_REPAIR.unlockLevel..EconomyConfig.CAMPAIGN_LEVELS) {
            val jump = levelReward(level, 3) - levelReward(level, 1)
            val price = boosterPrice(BoosterType.BASE_REPAIR, level)
            assertTrue(
                "L$level: tamir $price, 1->3 yildiz farki $jump — tamir kara gecebilir",
                price > jump,
            )
        }
        // Marjin bolumle BUYUMELI: gec oyunda odul dogrusal buyurken tamir de
        // buyudugu icin arbitraj penceresi hic acilmaz.
        val marginAtUnlock = boosterPrice(BoosterType.BASE_REPAIR, 7) -
            (levelReward(7, 3) - levelReward(7, 1))
        val marginAtEnd = boosterPrice(BoosterType.BASE_REPAIR, 22) -
            (levelReward(22, 3) - levelReward(22, 1))
        assertEquals(50, marginAtUnlock)
        assertEquals(80, marginAtEnd)
        assertTrue("arbitraj marjini daraliyor", marginAtEnd > marginAtUnlock)
    }

    /**
     * Acil Tedarik **yalnizca reklam** kalmali: coin -> Tedarik donusumu GDD D.4
     * tarafindan yasak. Faz 10.1 gozden gecirmesinde korundu.
     */
    @Test
    fun emergencySupplyHasNoPaidPathAtAnyLevel() {
        assertEquals(BoosterCurrency.AD_ONLY, BoosterType.EMERGENCY_SUPPLY.currency)
        assertFalse(BoosterType.EMERGENCY_SUPPLY.hasPaidPath)
        assertEquals(0, BoosterType.EMERGENCY_SUPPLY.paidUsesPerBattle)
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            assertEquals("L$level acil tedarik ucretli yol acmis", 0, boosterPrice(BoosterType.EMERGENCY_SUPPLY, level))
        }
        assertEquals(
            BoosterDecision.PaidPathUnavailable,
            boosterAllowed(state(10), BoosterType.EMERGENCY_SUPPLY, viaAd = false, richWallet),
        )
    }

    @Test
    fun baseRepairIsStarNeutralSoItCanNeverBeCoinPositive() {
        // ARBITRAJ: "tamir et -> yildiz atla -> tamirden fazla coin kazan".
        // Yildiz, tamir edilen can DUSULEREK hesaplandigi icin arbitraj
        // fiyatlamaya bagli olmaktan cikar, YAPISAL olarak yok olur.
        //
        // ⚠ TEST SAYI EZBERLEMIYOR (2026-08-21). Eski hali "tamir 8 can verir"
        // ve "14 - 8 = 6" gibi ara sonuclari elle yaziyordu; us tamiri oran
        // yerine duz cana (coin 4 / reklam 7) donunce hepsi bayatladi. Oysa
        // kilitlenmesi gereken sey MIKTAR degil, DEGISMEZ: tamir ne kadar
        // olursa olsun yildizi ve odulu artiramaz. Yeni hali her iki yolu da
        // ve her kayip miktarini tarayarak bunu soruyor.
        val maxLives = 20
        val wallet = PlayerWallet(unlockedLevels = (1..22).toSet())

        for (viaAd in listOf(false, true)) {
            for (health in 1 until maxLives) {
                val repaired = baseRepairAmount(health, maxLives, viaAd)
                val rawAfter = health + repaired
                val effective = effectiveStarHealth(rawAfter, repaired)

                assertEquals(
                    "tamir (viaAd=$viaAd, can=$health) etkin cani degistirmemeli",
                    health, effective,
                )
                assertEquals(
                    "tamir (viaAd=$viaAd, can=$health) yildizi artirmamali",
                    starsFor(health, maxLives),
                    starsFor(effective, maxLives),
                )
                assertEquals(
                    "tamir (viaAd=$viaAd, can=$health) odulu ARTIRMAMALI",
                    resolveLevelClear(wallet, 10, health, maxLives).total,
                    resolveLevelClear(wallet, 10, effective, maxLives).total,
                )
            }
        }
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

    /**
     * GERCEK KISIT — guclendiriciler coin ENFLASYONU yaratmaz.
     *
     * ⚠ 2026-08-21 — BU TEST BIR VEKIL OLCUYORDU VE VEKIL YANLIS YERDEYDI.
     * Kirilan satir soyleydi:
     *
     *     boosterPrice(type, 22) == 0 || type.currency != AD_ONLY
     *
     * yani "AD_ONLY bir guclendiricinin fiyati 0 olmali". Bu, adi "coin
     * enflasyonu yok" olan bir testin altinda duran ama ENFLASYONLA HICBIR
     * ILGISI OLMAYAN bir tutarlilik kontroluydu — fiyat bir coin GIRISI degil,
     * cikisidir. Hava Destegi AD_ONLY yapilinca (fiyat fonksiyonu ise hâlâ
     * L22'de 269 donuyor) satir patladi ve bir enflasyon hatasi gibi gorundu.
     *
     * Iki parcaya ayirdim:
     *  - Enflasyon iddiasi burada kaldi ve artik GERCEKTEN olculuyor: hicbir
     *    yol cuzdani buyutmuyor, coin akisi yalnizca cikis yonunde.
     *  - "AD_ONLY tipin ucretli bir yolu yoktur" tutarlilik kurali kendi
     *    testine tasindi: [adOnlyBoostersCanNeverChargeAnyCurrency].
     *
     * Gevsetme DEGIL: bayat fiyat sabiti orada yasamaya devam ediyor ve ayri
     * testte ULASILAMAZ oldugu kanitlaniyor.
     */
    @Test
    fun boostersAddNoCoinInflationAtAll() {
        val wallet = PlayerWallet(coins = 5_000, unlockedLevels = (1..22).toSet())
        BoosterType.entries.forEach { type ->
            listOf(false, true).forEach { viaAd ->
                val d = boosterAllowed(
                    state(10), type, viaAd = viaAd, wallet,
                    supplyOnHand = 9_999, baseHealth = 5, enemiesOnField = 5,
                )
                assertTrue(
                    "$type (viaAd=$viaAd) cuzdani buyutmus — guclendirici coin kaynagi olmus",
                    payForBooster(wallet, d).coins <= wallet.coins,
                )
                if (d is BoosterDecision.Allowed) {
                    assertTrue("$type (viaAd=$viaAd) negatif fiyat", d.price >= 0)
                    assertTrue(
                        "$type reklam yolu coin/Tedarik odememeli",
                        !viaAd || d.price == 0,
                    )
                }
            }
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
        // Dukkan tabani SALVAGE rank 1 = 200. (STARTING_SUPPLY 6 x +25 -> 2 x +75
        // olunca rank-1'i 900'e cikti; taban tek basina Hurda Degeri'nde kaldi.)
        assertEquals(200, cheapestNextRankPrice(MetaUpgrades()))
        // FIREPOWER rank 1 alindi -> taban degismez (SALVAGE hâlâ 200).
        assertEquals(200, cheapestNextRankPrice(MetaUpgrades(firepower = 1)))
        assertNotEquals(null, cheapestNextRankPrice(MetaUpgrades(firepower = 4)))

        // Hat tavanlari elle yazilmamali; ECONOMY_AUDIT_2 P0 Tahkimat'i 5 -> 9,
        // Hurda'yi 4 -> 5 rank'a cikardi ve bu test sessizce anlamini yitirmisti.
        var maxed = MetaUpgrades()
        for (line in UpgradeLine.entries) maxed = maxed.withRank(line, line.maxRank)
        assertTrue(maxed.isMaxed())
        assertEquals(null, cheapestNextRankPrice(maxed))
    }
}
