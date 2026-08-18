package com.miniappfactory.frontlinedefender.game.ads

import android.util.Log
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.LevelClearResult

/**
 * Faz 13 — [AdRewardBridge]'in GERCEK implementasyonu: odulu ekonomiye yatirir.
 *
 * ## Neden burada, `game/economy` icinde degil
 * Bagimlilik yonu tek yonlu olsun diye: `game/ads` -> `game/economy`. Ekonomi
 * paketi cekirdektir ve reklam SDK'sini de reklam tiplerini de tanimaz; bu
 * adaptor cevreseldir ve iki tarafi da tanir. Ters yon (`economy` -> `ads`)
 * ekonomi testlerini reklam katmanina baglardi.
 *
 * ## Neden `MainActivity` degil `GameScreen` insa ediyor
 * Adapte ettigi nesne ([CampaignProgressImpl]) ve carpilacak taban odul
 * ([LevelClearResult]) `GameScreen`'in sahipligindedir. `MainActivity`'nin
 * bunlari uretmesi ekonomi sahipliğini bir Activity'ye tasirdi. Reklam
 * *host*'unun sahibi hala Activity'dir (SDK + UMP + tam ekran cagrilari);
 * degisen yalnizca **odul muhasebesinin** sahibi.
 *
 * @param progress ekonomi katmani — odul miktarinin TEK sahibi.
 * @param lastClearResult R3 icin: reklamdan ONCE yatirilmis taban odul dokumu.
 *   Lambda, cunku her zaferde degisir ve kopru savas boyunca yasar.
 * @param resumeBattle R2 icin: yenilgiden cikip savasi kaldigi dalgadan
 *   surduren motor cagrisi. Girdi istenen can, cikti **gercekten uygulanan**
 *   can (0 = uygulanamadi). `null` verilirse R2 teklifi hic gosterilmez.
 */
class EconomyAdRewardBridge(
    private val progress: CampaignProgressImpl,
    private val lastClearResult: () -> LevelClearResult?,
    private val resumeBattle: ((lives: Int) -> Int)? = null,
    /**
     * Odul muhasebesinin LOGCAT KANITI (`adb logcat -s FD-Ads`). Enjekte
     * edilebilir olmasinin tek sebebi birim testler: `android.util.Log` JVM
     * testinde "not mocked" atar ve odulun gercekten yatirildigini kanitlayan
     * testler yazilamazdi. Uretimde varsayilan kullanilir.
     */
    private val log: (String) -> Unit = { Log.i(AD_LOG_TAG, it) },
) : AdRewardBridge {

    /**
     * R3'un ayni zafer icin IKI KEZ odenmesine karsi kilit.
     *
     * Teklif akisi bugun tek sonuc uretir (`RewardedOfferSheet` OFFER -> WAITING
     * -> RESULT) ve savas-basi hak da tuketilir, yani bu dal normalde hic
     * calismaz. Yine de burada duruyor: cift odeme sessiz bir ekonomi
     * enflasyonudur, hicbir ekranda gorunmez ve ancak bakiye tablosundan
     * fark edilir.
     */
    private var doublePaidFor: LevelClearResult? = null

    override fun grantSupplyDrop(result: RewardedResult): SupplyDropReward {
        val grant = progress.grantSupplyDrop(result.toEconomyOutcome())
        log(
            "reward_granted placement=supply_drop outcome=${result.outcome} " +
                "reason=${result.reason} coins=${grant.coins} filled=${grant.consumedFilledView} " +
                "budget_exhausted=${grant.budgetExhausted}"
        )
        return SupplyDropReward(
            coins = grant.coins,
            full = grant.consumedFilledView,
            budgetExhausted = grant.budgetExhausted,
        )
    }

    override fun grantDoublePayout(result: RewardedResult): DoublePayoutReward {
        // Zafer dokumu yoksa carpacak taban da yoktur. Sessizce 0: oyuncu
        // taban odulunu zaten aldi, hicbir sey kaybetmez.
        val clear = lastClearResult() ?: return DoublePayoutReward(bonusCoins = 0, baseCoins = 0)
        if (clear === doublePaidFor) {
            log("double_payout_already_paid level=${clear.level} (ikinci odeme ENGELLENDI)")
            return DoublePayoutReward(bonusCoins = 0, baseCoins = clear.total)
        }
        // Ek katman ekonomiden gelir: doublableAmount x (R3_MULTIPLIER - 1).
        // TABAN ODUL BURADA TEKRAR YATIRILMAZ (GDD §G.4).
        val bonus = progress.grantDoublePayout(clear, result.toEconomyOutcome())
        if (bonus > 0) doublePaidFor = clear
        log(
            "reward_granted placement=double_payout outcome=${result.outcome} " +
                "base=${clear.total} doublable=${clear.doublableAmount} bonus=$bonus"
        )
        return DoublePayoutReward(bonusCoins = bonus, baseCoins = clear.total)
    }

    override fun grantReinforcement(result: RewardedResult): ReinforcementReward {
        val resume = resumeBattle ?: return ReinforcementReward(applied = false, lives = 0)
        // Teklif tukendiyse odul yok — ama akis yine bloklanmaz, cagiran taraf
        // yenilgi modalini gosterir.
        if (result.outcome == RewardedOutcome.UNAVAILABLE) {
            return ReinforcementReward(applied = false, lives = 0)
        }
        // GDD §G.4: reklam gelmese de savas devam eder. Bu yuzden FULL ve
        // REDUCED dallari BIREBIR AYNI seyi yapar; fark yalnizca mesajdadir.
        val applied = resume(AdPolicyConfig.REINFORCEMENT_LIVES)
        if (applied <= 0) {
            log("reinforcement_not_applied outcome=${result.outcome} (motor reddetti)")
            return ReinforcementReward(applied = false, lives = 0)
        }
        // YILDIZ ARBITRAJINI KAPATAN SATIR — Us Tamiri ile ayni desen:
        // takviyeyle geri gelen can yildiz hesabindan DUSULUR, yani takviye
        // hayatta kalma satin alir, yildiz ve coin ASLA (bkz.
        // CampaignProgressImpl.starHealthFor / BoosterSystem.effectiveStarHealth).
        progress.noteReinforcement(applied)
        log(
            "reward_granted placement=reinforcement outcome=${result.outcome} lives=$applied"
        )
        return ReinforcementReward(applied = true, lives = applied)
    }

    /** Motor cagrisi baglanmadiysa R2 teklifi HIC gosterilmez. */
    override val reinforcementSupported: Boolean get() = resumeBattle != null
}
