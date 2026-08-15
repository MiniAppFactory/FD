package com.miniappfactory.frontlinedefender.game.ads

import android.util.Log

/**
 * Faz 5 — reklam katmani ile OYUN EKONOMISI arasindaki tek kopru.
 *
 * Reklam katmani ekonomiyi TANIMAZ: `game/economy/` su anda ayri bir ajan
 * tarafindan yaziliyor ve coin bakiyesinin kalici sahibi o katman olacak.
 * Bu arayuz o katman hazir olmadan cagri yerlerinin baglanabilmesi icindir:
 * reklam akisi bugun tam calisir, odulun **muhasebesi** tek satirla baglanir.
 *
 * BAGLAMA TALIMATI (ekonomi ajani icin) — `docs/ADMOB_INTEGRATION.md` §5:
 *   `MainActivity.kt` icinde `GameScreen(adHost = adHost, rewardBridge = ...)`
 *   cagrisindaki `LoggingAdRewardBridge` yerine ekonomi implementasyonu verilir.
 *   Baska HICBIR dosya degismez.
 */
interface AdRewardBridge {

    /**
     * Coin ODULU yatirilir (R1 tam/azaltilmis odul, R3 x2 farki).
     *
     * @param source analitik/log icin kaynak adi (`rewarded_supply_drop` gibi).
     */
    fun grantCoins(amount: Int, source: String)

    /**
     * R3 Cift Odeme tam odulu: **son tamamlanan bolumun** coin odulu
     * [multiplier] ile carpilir.
     *
     * Taban odulu ekonomi katmani zaten reklamdan ONCE yatirmis olmalidir
     * (GDD §G.4, kritik kural); bu cagri yalnizca EK katmani ekler. Reklam
     * katmani taban odulun ne oldugunu bilmez ve bilmemelidir.
     */
    fun grantDoublePayout(multiplier: Int)

    /**
     * R2 Takviye tam odulu: us cani [lives] degerine getirilir ve savas
     * **kaldigi dalgadan** devam eder.
     *
     * @return odul gercekten uygulandiysa true. **false donerse teklif hic
     *   gosterilmemis olmalidir** ([reinforcementSupported]) — calismayan bir
     *   odul teklif etmek, oyuncunun bir daha hicbir reklami izlememesinin en
     *   kisa yoludur.
     */
    fun grantReinforcement(lives: Int): Boolean

    /**
     * R2 teklifi UI'da gosterilebilir mi?
     *
     * **BUGUN false** ve bu bilincli: `GameEngine`'de yenilgiden sonra savasi
     * surdurecek bir API YOK (`_lives` ve `_gameState` private, DEFEAT'ten
     * cikis yolu yok) ve `game/engine` paketi bu ajanin yazma kapsaminda degil.
     * Gereken motor API'si `docs/ADMOB_INTEGRATION.md` §6'da satir seviyesinde
     * tarif edildi (gameplay-developer ajani).
     */
    val reinforcementSupported: Boolean get() = false
}

/**
 * Ekonomi baglanana kadarki varsayilan: odulu yalnizca loglar.
 *
 * DIKKAT — yayin oncesi ZORUNLU degisiklik: bu implementasyon ile R1/R3
 * odulleri oyuncunun bakiyesine GECMEZ. Reklam akisinin dogrulugu bundan
 * bagimsizdir (akis, timeout, no-fill ve hak muhasebesi bu koprunun ustunde
 * calisir), ama gercek odul olmadan yayina cikilmaz.
 */
object LoggingAdRewardBridge : AdRewardBridge {

    override fun grantCoins(amount: Int, source: String) {
        Log.w(
            AD_LOG_TAG,
            "reward_pending_economy coins=$amount source=$source " +
                "(LoggingAdRewardBridge: bakiye DEGISMEDI, ekonomi katmani baglanmali)"
        )
    }

    override fun grantDoublePayout(multiplier: Int) {
        Log.w(
            AD_LOG_TAG,
            "reward_pending_economy double_payout x$multiplier " +
                "(LoggingAdRewardBridge: bakiye DEGISMEDI)"
        )
    }

    override fun grantReinforcement(lives: Int): Boolean {
        Log.w(AD_LOG_TAG, "reward_pending_economy reinforcement lives=$lives (motor API yok)")
        return false
    }

    override val reinforcementSupported: Boolean get() = false
}
