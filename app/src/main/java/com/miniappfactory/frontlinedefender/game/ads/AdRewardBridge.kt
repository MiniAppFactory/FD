package com.miniappfactory.frontlinedefender.game.ads

import android.util.Log
import com.miniappfactory.frontlinedefender.game.economy.AdOutcome

/**
 * Faz 13 — reklam katmani ile OYUN EKONOMISI arasindaki tek kopru.
 *
 * =====================================================================
 * NEDEN BU ARAYUZ DEGISTI (eski sekil: `grantCoins(amount: Int, source: String)`)
 * =====================================================================
 * Eski sekilde **miktarin sahibi reklam katmaniydi**: `AdOffers` odulu
 * `AdPolicyConfig.SUPPLY_DROP_FULL_COIN` / `_REDUCED_COIN` sabitlerinden okuyup
 * hazir bir sayiyi ekonomiye "yatir" diye veriyordu. Ama ayni odulun ekonomi
 * tarafinda ZATEN bir sahibi vardi ve o taraf cok daha fazlasini biliyordu:
 *
 *  - gunluk coin butcesi  (`EconomyConfig.R1_COIN_BUDGET_PER_DAY` = 450)
 *  - gunluk dolu-gosterim hakki (`R1_VIEWS_PER_DAY` = 3)
 *  - adaptif odul bayragi (F-11: odul siradaki rank fiyatina gore olceklenir)
 *  - R3'un carpilabilir tabani (`LevelClearResult.doublableAmount`) — reklam
 *    katmani bu sayiyi **hic bilmez ve bilmemelidir**
 *
 * Iki dogruluk kaynagi vardi: reklam katmaninin sabiti ile ekonominin kurali
 * bugun ayni sayiya denk geliyordu, ama ekonomi butcesi/bayragi degistiginde
 * oyuncuya gosterilen mesaj ile bakiyeye gecen miktar sessizce ayrisirdi.
 * "+150 coin" yazip 50 yatirmak, odulu hic vermemek kadar politika ihlalidir.
 *
 * YENI SEKIL: **miktar arayuzden hic gecmez.** Reklam katmani yalnizca
 * *ne oldugunu* bildirir ([RewardedResult] = outcome + fallback sebebi),
 * ekonomi ne verilecegine karar verir ve **gercekten verdigi** miktari geri
 * doner. `AdOffers` oyuncuya gosterilecek mesaji o donen degerden uretir.
 *
 * Rol dagilimi (bundan sonra baglayici):
 *  - **Ekonomi** = MIKTARIN sahibi (kac coin, hangi butce, hangi carpan).
 *  - **Reklam katmani** = TEKLIFIN sahibi (buton gorunur mu, gunluk/savas
 *    hakki kaldi mi, arbitraj tavani doldu mu — bkz. [RewardedQuotaStore]).
 *
 * Bu ayrim tek yonlu bir bagimlilik uretir: `game/ads` -> `game/economy`.
 * Ters yon YOKTUR; ekonomi paketi reklam SDK'sini de bu arayuzu de tanimaz.
 */
interface AdRewardBridge {

    /**
     * R1 Tedarik Talebi. Miktari EKONOMI belirler
     * (`CampaignProgressImpl.grantSupplyDrop`).
     *
     * @param result reklam katmaninin gozlemi; [RewardedOutcome.UNAVAILABLE]
     *   ile cagrilmaz (teklif zaten tukenmistir, ekonomiye hic gidilmez).
     * @return ekonominin GERCEKTEN yatirdigi miktar ve neden.
     */
    fun grantSupplyDrop(result: RewardedResult): SupplyDropReward

    /**
     * R3 Cift Odeme.
     *
     * KRITIK (GDD §G.4): **taban odul reklamdan ONCE yatirilmistir**
     * (`CampaignProgressImpl.onLevelCleared`, zafer aninda). Bu cagri yalnizca
     * EK katmani ekler; taban odulu ASLA tekrar yatirmaz. Ek katmanin
     * buyuklugu de ekonomiden gelir (`doublePayoutBonus(doublableAmount, ...)`),
     * reklam katmaninin bir carpanindan degil.
     */
    fun grantDoublePayout(result: RewardedResult): DoublePayoutReward

    /**
     * R2 Takviye: us cani geri gelir ve savas **kaldigi dalgadan** devam eder.
     *
     * [ReinforcementReward.applied] false donerse **hicbir sey degismemistir**
     * ve teklif hic gosterilmemis olmalidir ([reinforcementSupported]) —
     * calismayan bir odul teklif etmek, oyuncunun bir daha hicbir reklami
     * izlememesinin en kisa yoludur.
     */
    fun grantReinforcement(result: RewardedResult): ReinforcementReward

    /**
     * R2 teklifi UI'da gosterilebilir mi? Yalnizca kopru gercekten bir motora
     * bagliysa true olur; [LoggingAdRewardBridge] icin her zaman false.
     */
    val reinforcementSupported: Boolean get() = false
}

// ---------------------------------------------------------------------------
// Donus tipleri — hepsi "ne OLDU" bildirir, "ne olmali" degil.
// ---------------------------------------------------------------------------

/**
 * R1 sonucu.
 *
 * @param coins bakiyeye GERCEKTEN eklenen coin. 0 olabilir (gunluk butce doldu)
 *   ve bu bir hata degildir — hicbir ilerleme yolu kapanmaz.
 * @param full dolu gosterim odulu mu (gunluk hak tuketildi) yoksa azaltilmis mi.
 * @param budgetExhausted ekonominin gunluk coin butcesi doldu; UI notr mesaj gosterir.
 */
data class SupplyDropReward(
    val coins: Int,
    val full: Boolean,
    val budgetExhausted: Boolean,
)

/**
 * R3 sonucu.
 *
 * @param bonusCoins taban odulun USTUNE eklenen EK coin. 0 = ek yok (reklam
 *   gelmedi/kapatildi) — taban odul yine de oyuncunun cebindedir.
 * @param baseCoins reklamdan ONCE zaten yatirilmis taban odul; yalnizca mesaj
 *   uretmek icin tasinir, tekrar yatirilmaz.
 */
data class DoublePayoutReward(
    val bonusCoins: Int,
    val baseCoins: Int,
)

/**
 * R2 sonucu.
 *
 * @param applied savas gercekten surduruldu mu.
 * @param lives us caninin getirildigi deger (motorun tavaniyla kirpilmis olabilir).
 */
data class ReinforcementReward(
    val applied: Boolean,
    val lives: Int,
)

// ---------------------------------------------------------------------------
// Outcome eslemesi
// ---------------------------------------------------------------------------

/**
 * Reklam katmaninin [RewardedOutcome]'unu ekonominin [AdOutcome]'una cevirir.
 *
 * Iki enum kasitli olarak AYRI: reklam katmani "oyuncuya ne kadar odul
 * gosterecegim" sorusunu, ekonomi "hangi SDK olayi gerceklesti" sorusunu
 * cevaplar. Ekonomi tarafinda yalnizca [AdOutcome.REWARD_EARNED] doludur
 * (`AdOutcome.isFilled`); diger uc deger ayni odulu verir ve fark
 * **yalnizca analitiktedir** — bu yuzden fallback sebebi burada korunur,
 * yutulmaz.
 */
fun RewardedResult.toEconomyOutcome(): AdOutcome = when (outcome) {
    RewardedOutcome.FULL_REWARD -> AdOutcome.REWARD_EARNED
    RewardedOutcome.REDUCED_REWARD, RewardedOutcome.UNAVAILABLE -> when (reason) {
        AdFallbackReason.DISMISSED_WITHOUT_REWARD -> AdOutcome.DISMISSED
        AdFallbackReason.LOAD_TIMEOUT -> AdOutcome.TIMEOUT
        else -> AdOutcome.NO_FILL
    }
}

// ---------------------------------------------------------------------------
// Reklamsiz / ekonomisiz varsayilan
// ---------------------------------------------------------------------------

/**
 * Ekonomiye BAGLANMAMIS kopru: odulu yalnizca loglar, bakiyeyi degistirmez.
 *
 * Artik yalnizca **onizleme ve test** icindir. Uretim yolu `GameScreen` icinde
 * [EconomyAdRewardBridge]'e baglidir; bu nesneyi uretimde kullanmak
 * "reklam izle, +150 coin al" deyip hicbir sey vermemek demektir (AdMob
 * odullu reklam politikasi ihlali).
 */
object LoggingAdRewardBridge : AdRewardBridge {

    override fun grantSupplyDrop(result: RewardedResult): SupplyDropReward {
        Log.w(AD_LOG_TAG, "reward_not_wired supply_drop outcome=${result.outcome} (bakiye DEGISMEDI)")
        return SupplyDropReward(coins = 0, full = false, budgetExhausted = false)
    }

    override fun grantDoublePayout(result: RewardedResult): DoublePayoutReward {
        Log.w(AD_LOG_TAG, "reward_not_wired double_payout outcome=${result.outcome} (bakiye DEGISMEDI)")
        return DoublePayoutReward(bonusCoins = 0, baseCoins = 0)
    }

    override fun grantReinforcement(result: RewardedResult): ReinforcementReward {
        Log.w(AD_LOG_TAG, "reward_not_wired reinforcement (motor bagli degil)")
        return ReinforcementReward(applied = false, lives = 0)
    }

    override val reinforcementSupported: Boolean get() = false
}
