package com.miniappfactory.frontlinedefender.game.ads

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.AdOutcome
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.ClockProvider
import com.miniappfactory.frontlinedefender.game.economy.ClockSample
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.LevelClearResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 13 — ODULLU REKLAM GERCEKTEN ODUL VERIYOR MU?
 *
 * Bu dosyanin var olma sebebi somut bir hatadir: `MainActivity` kopru olarak
 * `LoggingAdRewardBridge` geciyordu, yani oyuncuya "reklam izle, +150 coin al"
 * deniyor ve **bakiyeye hicbir sey yazilmiyordu**. Reklam akisinin dogru
 * calismasi bunu yakalamiyordu; yakalayabilecek tek sey, odul cagrisindan
 * SONRA cuzdana bakan bir testtir. Buradaki her test tam olarak bunu yapar.
 *
 * **SAF JUnit**: `SaveManager` [InMemoryKeyValueStore] ile kuruluyor, saat
 * enjekte ediliyor ve kopru log cikisini bir lambda'ya yaziyor — `Context`,
 * `SystemClock` ve `android.util.Log` HIC dokunulmuyor.
 */
class AdRewardBridgeTest {

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

    private fun bridge(
        progress: CampaignProgressImpl,
        clear: () -> LevelClearResult? = { null },
        resume: ((Int) -> Int)? = null,
    ) = EconomyAdRewardBridge(
        progress = progress,
        lastClearResult = clear,
        resumeBattle = resume,
        log = {},
    )

    private fun result(
        placement: RewardedPlacement,
        outcome: RewardedOutcome,
        reason: AdFallbackReason = AdFallbackReason.NONE,
    ) = RewardedResult(placement, outcome, reason)

    // =================================================================================
    // R1 — TEDARIK TALEBI
    // =================================================================================

    @Test
    fun supplyDropFullRewardActuallyCreditsWallet() {
        val progress = progress()
        val before = progress.coins

        val reward = bridge(progress).grantSupplyDrop(
            result(RewardedPlacement.SUPPLY_DROP, RewardedOutcome.FULL_REWARD)
        )

        assertEquals("odul miktarinin sahibi ekonomi", EconomyConfig.R1_REWARD_FILLED, reward.coins)
        assertTrue("dolu gosterim isaretlenmeli", reward.full)
        // ESKI HATA TAM OLARAK BURADA GORULURDU: LoggingAdRewardBridge ile bu
        // satir "bakiye degismedi" derdi.
        assertEquals("bakiye gercekten artmali", before + reward.coins, progress.coins)
    }

    @Test
    fun supplyDropNoFillPaysReducedAndKeepsDailyRight() {
        val progress = progress()

        val fallback = bridge(progress).grantSupplyDrop(
            result(
                RewardedPlacement.SUPPLY_DROP,
                RewardedOutcome.REDUCED_REWARD,
                AdFallbackReason.LOAD_FAILED,
            )
        )
        assertEquals(EconomyConfig.R1_REWARD_FALLBACK, fallback.coins)
        assertFalse("no-fill dolu gosterim sayilmaz", fallback.full)
        assertEquals(fallback.coins, progress.coins)

        // GDD G.4: no-fill gunluk hakki yakmaz — sonraki dolu gosterim TAM odul.
        val full = bridge(progress).grantSupplyDrop(
            result(RewardedPlacement.SUPPLY_DROP, RewardedOutcome.FULL_REWARD)
        )
        assertEquals(EconomyConfig.R1_REWARD_FILLED, full.coins)
        assertTrue(full.full)
    }

    @Test
    fun supplyDropCannotExceedDailyCoinBudgetAndNeverLocksPlayer() {
        val progress = progress()
        val bridge = bridge(progress)

        // Ucak modundaki oyuncu: sonsuz "+50" denemesi.
        var lastBudgetExhausted = false
        repeat(40) {
            val reward = bridge.grantSupplyDrop(
                result(
                    RewardedPlacement.SUPPLY_DROP,
                    RewardedOutcome.REDUCED_REWARD,
                    AdFallbackReason.LOAD_FAILED,
                )
            )
            lastBudgetExhausted = reward.budgetExhausted
        }

        assertEquals(
            "gunluk coin butcesi ekonominin tavanidir",
            EconomyConfig.R1_COIN_BUDGET_PER_DAY,
            progress.coins,
        )
        assertTrue("butce doldugu bildirilmeli (UI notr mesaj gosterir)", lastBudgetExhausted)
    }

    // =================================================================================
    // R3 — CIFT ODEME (kritik kural: taban odul reklamdan ONCE yatirilir)
    // =================================================================================

    @Test
    fun doublePayoutAddsOnlyTheExtraLayerAndNeverRebanksTheBase() {
        val progress = progress()
        // Zafer: taban odul reklamdan ONCE yatirilir (GDD G.4).
        val clear = progress.onLevelCleared(levelId = 1, livesLeft = 20, maxLives = 20)
        val afterBaseReward = progress.coins
        assertEquals("taban odul hemen yatmali", clear.total, afterBaseReward)
        assertTrue("carpilabilir bir taban olmali", clear.doublableAmount > 0)

        val reward = bridge(progress, clear = { clear }).grantDoublePayout(
            result(RewardedPlacement.DOUBLE_PAYOUT, RewardedOutcome.FULL_REWARD)
        )

        val expectedBonus = clear.doublableAmount * (EconomyConfig.R3_MULTIPLIER - 1)
        assertEquals("ek katman ekonomiden gelir", expectedBonus, reward.bonusCoins)
        assertEquals(
            "TABAN ODUL IKI KEZ YATIRILMAMALI",
            afterBaseReward + expectedBonus,
            progress.coins,
        )
        assertEquals(clear.total, reward.baseCoins)
    }

    @Test
    fun doublePayoutWithoutAdKeepsTheBaseRewardIntact() {
        val progress = progress()
        val clear = progress.onLevelCleared(levelId = 1, livesLeft = 20, maxLives = 20)
        val afterBaseReward = progress.coins

        val reward = bridge(progress, clear = { clear }).grantDoublePayout(
            result(
                RewardedPlacement.DOUBLE_PAYOUT,
                RewardedOutcome.REDUCED_REWARD,
                AdFallbackReason.LOAD_TIMEOUT,
            )
        )

        assertEquals(0, reward.bonusCoins)
        assertEquals("no-fill oyuncuyu CEZALANDIRMAZ", afterBaseReward, progress.coins)
    }

    @Test
    fun doublePayoutIsNeverPaidTwiceForTheSameClear() {
        val progress = progress()
        val clear = progress.onLevelCleared(levelId = 1, livesLeft = 20, maxLives = 20)
        val bridge = bridge(progress, clear = { clear })
        val full = result(RewardedPlacement.DOUBLE_PAYOUT, RewardedOutcome.FULL_REWARD)

        bridge.grantDoublePayout(full)
        val afterFirst = progress.coins
        val second = bridge.grantDoublePayout(full)

        assertEquals("ikinci odeme engellenmeli", 0, second.bonusCoins)
        assertEquals(afterFirst, progress.coins)
    }

    @Test
    fun doublePayoutSkipsEconomyWhenOfferIsExhausted() {
        val progress = progress()
        val clear = progress.onLevelCleared(levelId = 1, livesLeft = 20, maxLives = 20)
        val before = progress.coins

        val reward = bridge(progress, clear = { clear }).grantDoublePayout(
            result(
                RewardedPlacement.DOUBLE_PAYOUT,
                RewardedOutcome.UNAVAILABLE,
                AdFallbackReason.QUOTA_EXHAUSTED,
            )
        )
        // UNAVAILABLE'i AdOffers zaten oncesinde yakalar; kopru de kendi
        // basina guvenli olmali.
        assertEquals(0, reward.bonusCoins)
        assertEquals(before, progress.coins)
    }

    // =================================================================================
    // R2 — TAKVIYE
    // =================================================================================

    @Test
    fun reinforcementIsNotOfferedWhenTheEngineIsNotWired() {
        val bridge = bridge(progress(), resume = null)
        assertFalse("calismayan odul teklif EDILMEZ", bridge.reinforcementSupported)
        assertFalse(
            bridge.grantReinforcement(
                result(RewardedPlacement.REINFORCEMENT, RewardedOutcome.FULL_REWARD)
            ).applied
        )
    }

    @Test
    fun reinforcementResumesTheBattleEvenWithoutAnAd() {
        var requested = 0
        val bridge = bridge(progress(), resume = { lives -> requested = lives; lives })

        val reward = bridge.grantReinforcement(
            result(
                RewardedPlacement.REINFORCEMENT,
                RewardedOutcome.REDUCED_REWARD,
                AdFallbackReason.LOAD_FAILED,
            )
        )

        assertTrue("GDD G.4: reklam yoksa da savas SURER", reward.applied)
        assertEquals(AdPolicyConfig.REINFORCEMENT_LIVES, requested)
        assertEquals(AdPolicyConfig.REINFORCEMENT_LIVES, reward.lives)
    }

    @Test
    fun reinforcementAppliesNothingWhenTheEngineRefuses() {
        val progress = progress()
        val reward = bridge(progress, resume = { 0 }).grantReinforcement(
            result(RewardedPlacement.REINFORCEMENT, RewardedOutcome.FULL_REWARD)
        )
        assertFalse(reward.applied)
        assertEquals("reddedilen takviye yildizi cezalandirmamali", 7, progress.starHealthFor(7))
    }

    @Test
    fun reinforcementBuysSurvivalNeverStars() {
        val progress = progress()
        val lives = AdPolicyConfig.REINFORCEMENT_LIVES
        assertEquals("takviye oncesi kimlik fonksiyonu", lives, progress.starHealthFor(lives))

        bridge(progress, resume = { it }).grantReinforcement(
            result(RewardedPlacement.REINFORCEMENT, RewardedOutcome.FULL_REWARD)
        )

        // Us Tamiri deseninin ta kendisi: geri verilen can yildiz hesabindan
        // dusulur. Zafer iptal edilmez (en az 1 yildiz), ama takviyeyle hayatta
        // kalan oyuncu 2-3 yildiza ULASAMAZ.
        assertEquals(1, progress.starHealthFor(lives))
        assertNotEquals("takviye yildiz KAZANDIRMAZ", lives, progress.starHealthFor(lives))
    }

    @Test
    fun reinforcementCounterIsScopedToTheBattle() {
        val progress = progress()
        bridge(progress, resume = { it }).grantReinforcement(
            result(RewardedPlacement.REINFORCEMENT, RewardedOutcome.FULL_REWARD)
        )
        assertEquals(1, progress.starHealthFor(AdPolicyConfig.REINFORCEMENT_LIVES))

        progress.beginBattle(levelId = 2)

        assertEquals(
            "onceki savasin takviyesi yeni savasin yildizini dusuremez",
            AdPolicyConfig.REINFORCEMENT_LIVES,
            progress.starHealthFor(AdPolicyConfig.REINFORCEMENT_LIVES),
        )
    }

    // =================================================================================
    // Outcome eslemesi — reklam katmani ile ekonominin ortak dili
    // =================================================================================

    @Test
    fun outcomeMappingCoversEveryAdBranch() {
        fun map(outcome: RewardedOutcome, reason: AdFallbackReason) =
            result(RewardedPlacement.SUPPLY_DROP, outcome, reason).toEconomyOutcome()

        assertEquals(
            AdOutcome.REWARD_EARNED,
            map(RewardedOutcome.FULL_REWARD, AdFallbackReason.NONE),
        )
        assertEquals(
            AdOutcome.DISMISSED,
            map(RewardedOutcome.REDUCED_REWARD, AdFallbackReason.DISMISSED_WITHOUT_REWARD),
        )
        assertEquals(
            AdOutcome.TIMEOUT,
            map(RewardedOutcome.REDUCED_REWARD, AdFallbackReason.LOAD_TIMEOUT),
        )
        assertEquals(
            AdOutcome.NO_FILL,
            map(RewardedOutcome.REDUCED_REWARD, AdFallbackReason.LOAD_FAILED),
        )
        assertEquals(
            AdOutcome.NO_FILL,
            map(RewardedOutcome.REDUCED_REWARD, AdFallbackReason.NO_CONSENT),
        )
        assertEquals(
            AdOutcome.NO_FILL,
            map(RewardedOutcome.UNAVAILABLE, AdFallbackReason.QUOTA_EXHAUSTED),
        )
        // Ekonomi icin FULL disindaki her sey ayni odulu verir; fark analitiktedir.
        assertFalse(map(RewardedOutcome.REDUCED_REWARD, AdFallbackReason.LOAD_FAILED).isFilled)
        assertTrue(map(RewardedOutcome.FULL_REWARD, AdFallbackReason.NONE).isFilled)
    }

    // =================================================================================
    // Tek dogruluk kaynagi — reklam katmaninin sabitleri ekonomiyi EZEMEZ
    // =================================================================================

    @Test
    fun adLayerOfferConstantsAreDerivedFromTheEconomy() {
        assertEquals(EconomyConfig.R1_REWARD_FILLED, AdPolicyConfig.SUPPLY_DROP_FULL_COIN)
        assertEquals(EconomyConfig.R1_REWARD_FALLBACK, AdPolicyConfig.SUPPLY_DROP_REDUCED_COIN)
        assertEquals(EconomyConfig.R1_VIEWS_PER_DAY, AdPolicyConfig.SUPPLY_DROP_DAILY_LIMIT)
        assertEquals(EconomyConfig.R3_MULTIPLIER, AdPolicyConfig.DOUBLE_PAYOUT_MULTIPLIER)
    }
}
