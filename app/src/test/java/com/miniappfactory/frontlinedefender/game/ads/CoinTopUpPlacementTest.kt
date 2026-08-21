package com.miniappfactory.frontlinedefender.game.ads

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.ClockProvider
import com.miniappfactory.frontlinedefender.game.economy.ClockSample
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1b — COIN CIPI ODULLU REKLAMI: **IKINCI GIRIS NOKTASI, IKINCI MUSLUK DEGIL.**
 *
 * ## Bu dosyanin var olma sebebi
 * Tekrar oynama geliri kaldirildi (`replayReward` -> 0) ve yerine coin cipinden
 * acilan bir reklam yolu kondu. Bu tur bir degisikligin en kolay yanlisi, yeni
 * yolu **kendi butcesiyle** eklemektir: ekranda iki buton olur, ikisi de coin
 * oder, gunluk toplam sessizce ikiye katlanir ve `ECONOMY_AUDIT_2`'nin olctugu
 * "olu coin fazlasi" geri gelir.
 *
 * Burada kilitlenen sozlesme:
 *
 *   1. **Reklam katmani** — [RewardedPlacement.COIN_TOP_UP] ile
 *      [RewardedPlacement.SUPPLY_DROP] ayni hak kovasini paylasir.
 *   2. **Ekonomi katmani** — ikisi de ayni `RequisitionState` uzerinden ayni
 *      gunluk coin butcesine ([EconomyConfig.R1_COIN_BUDGET_PER_DAY] = 450) ve
 *      ayni dolu-gosterim hakkina ([EconomyConfig.R1_VIEWS_PER_DAY] = 3) tabidir.
 *   3. **Politika** — teklif tukendiginde oyuncu hicbir sey KAYBETMEZ; akis
 *      her dalda ayni sekilde surer.
 *
 * `EconomySimulationTest.infiniteNoFillLoopIsBoundedByDailyCoinBudget` tek
 * giris noktasi icin ayni siniri zaten olcuyordu. Bu dosya sorunun **iki giris
 * noktasi** halini soruyor — cunku bir tavani delmenin en sessiz yolu, tavani
 * degistirmek degil, tavana giden ikinci bir kapi acmaktir.
 */
class CoinTopUpPlacementTest {

    /** Sabit saat: gun donusu testin ortasinda tetiklenmesin. */
    private class FixedClock(private val day: Long = 20_500L) : ClockProvider {
        override fun sample(): ClockSample = ClockSample(
            epochDay = day,
            wallClockMs = day * EconomyConfig.MS_PER_DAY,
            elapsedRealtimeMs = 1_000L,
            bootId = 1L,
        )
    }

    private fun progress(): CampaignProgressImpl =
        CampaignProgressImpl(SaveManager(InMemoryKeyValueStore()), FixedClock())

    private fun bridge(progress: CampaignProgressImpl) = EconomyAdRewardBridge(
        progress = progress,
        lastClearResult = { null },
        resumeBattle = null,
        log = {},
    )

    private fun full(placement: RewardedPlacement) =
        RewardedResult(placement, RewardedOutcome.FULL_REWARD)

    private fun noFill(placement: RewardedPlacement) =
        RewardedResult(placement, RewardedOutcome.REDUCED_REWARD, AdFallbackReason.LOAD_FAILED)

    // =================================================================================
    // 1. REKLAM KATMANI — tek kova
    // =================================================================================

    @Test
    fun theCoinChipAndTheSupplyBarDrawFromOneDailyQuota() {
        val quota = InMemoryRewardedQuotaStore { 20_500 }

        assertEquals(
            AdPolicyConfig.SUPPLY_DROP_DAILY_LIMIT,
            quota.remaining(RewardedPlacement.COIN_TOP_UP),
        )

        // Serit uzerinden bir dolu gosterim: CIPIN kalan hakki da azalmali.
        quota.consume(RewardedPlacement.SUPPLY_DROP)
        assertEquals(
            "cip ve serit ayri sayiyorsa gunluk hak fiilen ikiye katlanir",
            AdPolicyConfig.SUPPLY_DROP_DAILY_LIMIT - 1,
            quota.remaining(RewardedPlacement.COIN_TOP_UP),
        )

        // Ve tersi.
        quota.consume(RewardedPlacement.COIN_TOP_UP)
        assertEquals(
            AdPolicyConfig.SUPPLY_DROP_DAILY_LIMIT - 2,
            quota.remaining(RewardedPlacement.SUPPLY_DROP),
        )
    }

    @Test
    fun alternatingBetweenTheTwoEntryPointsCannotDoubleTheDailyRight() {
        val quota = InMemoryRewardedQuotaStore { 20_500 }
        val entryPoints = listOf(RewardedPlacement.SUPPLY_DROP, RewardedPlacement.COIN_TOP_UP)

        var consumed = 0
        repeat(20) { i ->
            val placement = entryPoints[i % 2]
            if (quota.isOffered(placement)) {
                quota.consume(placement)
                consumed++
            }
        }

        assertEquals(
            "iki giris noktasi toplamda gunluk hakkin ustune cikamaz",
            AdPolicyConfig.SUPPLY_DROP_DAILY_LIMIT,
            consumed,
        )
        assertFalse(quota.isOffered(RewardedPlacement.SUPPLY_DROP))
        assertFalse(quota.isOffered(RewardedPlacement.COIN_TOP_UP))
    }

    @Test
    fun theAntiArbitrageFallbackCapIsSharedByBothEntryPoints() {
        // UCAK MODU SENARYOSU: hicbir reklam dolmuyor, oyuncu iki butona da
        // sirayla basiyor. Tavan PAYLASILMAZSA gunde 6 x 50 = 300 azaltilmis
        // odul cikar; paylasilirsa 3 x 50 = 150.
        val quota = InMemoryRewardedQuotaStore { 20_500 }
        val entryPoints = listOf(RewardedPlacement.COIN_TOP_UP, RewardedPlacement.SUPPLY_DROP)

        val granted = (0 until 50).count { i -> quota.noteFallback(entryPoints[i % 2]) }

        assertEquals(
            "azaltilmis odul tavani da tek kova olmali",
            AdPolicyConfig.SUPPLY_DROP_FALLBACK_DAILY_CAP,
            granted,
        )

        // KRITIK: tavan dolsa da GERCEK hak yanmaz — oyuncu ilerleme kaybetmez.
        assertEquals(
            "no-fill gunluk gercek hakki tuketmemeli (GDD G.4)",
            AdPolicyConfig.SUPPLY_DROP_DAILY_LIMIT,
            quota.remaining(RewardedPlacement.COIN_TOP_UP),
        )
        assertTrue(quota.isOffered(RewardedPlacement.COIN_TOP_UP))
    }

    // =================================================================================
    // 2. EKONOMI KATMANI — tek butce
    // =================================================================================

    @Test
    fun bothEntryPointsPayFromTheSameDailyCoinBudget() {
        val progress = progress()
        val bridge = bridge(progress)
        val before = progress.coins
        val entryPoints = listOf(RewardedPlacement.COIN_TOP_UP, RewardedPlacement.SUPPLY_DROP)

        // 200 dokunus, iki butona sirayla, hepsi DOLU reklam.
        repeat(200) { i -> bridge.grantSupplyDrop(full(entryPoints[i % 2])) }

        assertEquals(
            "gunluk coin tavani giris noktasi sayisiyla buyumemeli",
            EconomyConfig.R1_COIN_BUDGET_PER_DAY,
            progress.coins - before,
        )
        assertEquals(0, progress.supplyDropBudgetLeftToday)
        assertEquals(0, progress.supplyDropViewsLeftToday)
    }

    @Test
    fun theCoinChipAloneCannotOutEarnTheOldSingleEntryPoint() {
        // Referans: yalnizca SERITTEN kazanilan gunluk toplam.
        val barOnly = progress()
        val barBridge = bridge(barOnly)
        repeat(50) { barBridge.grantSupplyDrop(full(RewardedPlacement.SUPPLY_DROP)) }
        val barTotal = barOnly.coins

        // Ayni gun, yalnizca CIPTEN.
        val chipOnly = progress()
        val chipBridge = bridge(chipOnly)
        repeat(50) { chipBridge.grantSupplyDrop(full(RewardedPlacement.COIN_TOP_UP)) }

        assertEquals(
            "yeni giris noktasi gunluk geliri degistirmemeli, yalnizca GORUNUR kilmali",
            barTotal,
            chipOnly.coins,
        )
        assertEquals(EconomyConfig.R1_COIN_BUDGET_PER_DAY, barTotal)
    }

    @Test
    fun theInfiniteNoFillLoopStaysBoundedWithTwoEntryPoints() {
        val progress = progress()
        val bridge = bridge(progress)
        val before = progress.coins
        val entryPoints = listOf(RewardedPlacement.SUPPLY_DROP, RewardedPlacement.COIN_TOP_UP)

        repeat(500) { i -> bridge.grantSupplyDrop(noFill(entryPoints[i % 2])) }

        assertEquals(
            "ucak modunda iki buton birden sonsuz coin uretmemeli",
            EconomyConfig.R1_COIN_BUDGET_PER_DAY,
            progress.coins - before,
        )
    }

    @Test
    fun anExhaustedOfferTakesNothingFromThePlayer() {
        // GDD G.4 / bu depodaki kural: yarim kalan veya hic gelmeyen reklamda
        // oyuncudan HICBIR SEY alinmaz ve hicbir hak yanmaz.
        val progress = progress()
        val bridge = bridge(progress)

        repeat(3) { bridge.grantSupplyDrop(full(RewardedPlacement.COIN_TOP_UP)) }
        val afterBudget = progress.coins

        val exhausted = bridge.grantSupplyDrop(full(RewardedPlacement.COIN_TOP_UP))

        assertEquals(0, exhausted.coins)
        assertTrue("butce tukendi bildirilmeli (UI notr mesaj gosterir)", exhausted.budgetExhausted)
        assertEquals("bakiye ASLA dusmez", afterBudget, progress.coins)
    }

    // =================================================================================
    // 3. YAPILANDIRMA — yeni yerlesim SDK'ya bos kimlikle gitmez
    // =================================================================================

    @Test
    fun everyRewardedPlacementResolvesToAUsableAdUnit() {
        // Yeni bir yerlesim eklenip `AdIds.rewardedAdUnitId` eslemesi
        // unutulursa `isConfigured` false doner ve o yerlesim SESSIZCE hic
        // reklam gostermez — hata degil, bos reklam. Bu test o sessizligi
        // bozar.
        RewardedPlacement.entries.forEach { placement ->
            val unit = AdIds.rewardedAdUnitId(placement)
            assertTrue(
                "$placement icin rewarded birim kimligi bos — SDK'ya hic gidilmez",
                AdIds.isConfigured(unit),
            )
        }
    }

    @Test
    fun theCoinChipUsesADistinctAnalyticsLabel() {
        // Ayni odulun iki giris noktasi var; hangisinin gercekten gosterim
        // urettigi yalnizca `placement.name` ile ayirt edilebilir
        // (`RewardedAdManager` bu degeri dogrudan olay etiketine yaziyor).
        assertEquals("COIN_TOP_UP", RewardedPlacement.COIN_TOP_UP.name)
        assertTrue(
            "iki giris noktasi ayni etikete duserse performanslari tek ortalamanin " +
                "arkasinda kaybolur",
            RewardedPlacement.COIN_TOP_UP.name != RewardedPlacement.SUPPLY_DROP.name,
        )
    }

    // =================================================================================
    // 4. CALISMAYAN ODUL TEKLIF EDILMEZ (R3 yan etkisi)
    // =================================================================================

    @Test
    fun aClearWithNothingToDoubleMustNotProduceAPayout() {
        // Tekrar oynama odulu kaldirildiginda tekrar zaferinin
        // `doublableAmount` degeri 0 olur. Ekonomi bu durumda dogru davranir
        // (0 oder) — asil risk, UI'nin yine de "odulunu IKIYE KATLA" teklifini
        // acmasidir. `GameScreen` artik `doublableAmount > 0` kapisini
        // uyguluyor; burada kilitlenen sey o kapinin GEREKCESI: taban 0 iken
        // reklamin karsiligi gercekten 0'dir.
        val progress = progress()
        val clear = com.miniappfactory.frontlinedefender.game.economy.LevelClearResult(
            level = 5,
            stars = 3,
            firstClear = false,
            firstClearReward = 0,
            starImprovement = 0,
            perfectBonus = 0,
            replayReward = 0,
            consumesBoostedReplay = false,
            doublableAmount = 0,
        )
        val bridge = EconomyAdRewardBridge(
            progress = progress,
            lastClearResult = { clear },
            resumeBattle = null,
            log = {},
        )
        val before = progress.coins

        val reward = bridge.grantDoublePayout(full(RewardedPlacement.DOUBLE_PAYOUT))

        assertEquals("carpilacak taban yokken ek katman 0 olmali", 0, reward.bonusCoins)
        assertEquals("bakiye degismemeli", before, progress.coins)
    }

    // =================================================================================
    // 5. TAVANIN GEREKCESI — sayi degismedi, OLCU degismedi
    // =================================================================================

    @Test
    fun theAdCoinCeilingStillStaysBelowHalfOfDailyGameplayIncome() {
        // ECONOMY_SPEC 1.3 / ECONOMY_AUDIT_2 tablo 7: medyan oyuncunun gunluk
        // BOLUM geliri ~930 coin ve reklam geliri bunun yarisini asamaz.
        //
        // Tekrar oynama geliri kaldirildiginda bu payda KUCULMEZ (930 yalnizca
        // ilk temizlik odulleridir, tekrar geliri zaten disindaydi), yani kural
        // aynen gecerli. Yeni giris noktasi bu yuzden butceyi BUYUTEMEZ:
        // 450 <= 465 sinirinda yalnizca 15 coin'lik pay var.
        val gameplayPerDay = 930
        assertTrue(
            "reklam coin tavani ${EconomyConfig.R1_COIN_BUDGET_PER_DAY}, " +
                "gunluk oynanis gelirinin yarisini asiyor",
            EconomyConfig.R1_COIN_BUDGET_PER_DAY <= gameplayPerDay / 2,
        )
        // Tavanin gercekten 3 x 150 oldugunu da kilitle: giris noktasi eklemek
        // odul basina miktari da degistirmemeli.
        assertEquals(
            EconomyConfig.R1_REWARD_FILLED * EconomyConfig.R1_VIEWS_PER_DAY,
            EconomyConfig.R1_COIN_BUDGET_PER_DAY,
        )
    }
}
