package com.miniappfactory.frontlinedefender.game.economy

/**
 * Yenilgiden sonra COIN karsiligi DEVAM (eski R2 "Takviye"; 2026-09-30'dan beri reklamsiz).
 *
 * **Android importu YOK.** Saf karar mantigi; JVM'de kosar. Sayilar [EconomyConfig]'ten gelir.
 *
 * Devam, savasi kaldigi dalgadan surdurur ve us canini [EconomyConfig.CONTINUE_LIVES]'a
 * getirir (motor: `GameEngine.reinforceAfterDefeat`). **Yildiz notrdur**: geri verilen can
 * yildiz hesabindan dusulur ([CampaignProgressImpl.noteReinforcement]), yani devamla kazanilan
 * bolum en fazla 1 yildiz verir — devam hayatta kalma satin alir, yildiz/coin asla.
 * "TEKRAR DENE" her zaman bedavadir; devam yalnizca ek bir secenektir.
 */

/** Devam bedeli: bolumun ilk temizleme (1 yildiz) odulunun [EconomyConfig.CONTINUE_PRICE_PERCENT_OF_FIRST_CLEAR] yuzdesi, 10'a asagi. */
fun continuePrice(level: Int): Int =
    (levelReward(level, 1) * EconomyConfig.CONTINUE_PRICE_PERCENT_OF_FIRST_CLEAR / 100 / 10) * 10

/** Devam etme denemesinin karari. UI kendi bakiye karsilastirmasini YAPMAZ. */
sealed class ContinueDecision {
    /** Devam edilebilir; [price] odenir, [balanceAfter] kalan bakiye. */
    data class Allowed(val price: Int, val lives: Int, val balanceAfter: Int) : ContinueDecision()

    /** Kill switch kapali. */
    data object Disabled : ContinueDecision()

    /** Bu savasta devam hakki kullanildi ([EconomyConfig.CONTINUE_USES_PER_BATTLE]). */
    data object AlreadyUsed : ContinueDecision()

    /** Bakiye bedeli karsilamiyor; [shortfall] eksik coin. */
    data class InsufficientCoins(val price: Int, val shortfall: Int) : ContinueDecision()

    /** Bakiye yetiyor ama alim Konuslanma Rezervi'ni ihlal ediyor. */
    data class ReserveLocked(val price: Int, val reserve: Int, val shortfall: Int) : ContinueDecision()

    val isAllowed: Boolean get() = this is Allowed
}

/**
 * Devam etmeye izin var mi? KONTROL SIRASI: kill switch -> savas basi hak -> bakiye -> rezerv.
 *
 * Rezerv kontrolu ZORUNLU: bedel R/2 <= R - D(L+1) oldugundan normalde rezervi ihlal etmez, ama
 * kilitli bir sonraki bolumle tekrar oynanan bir bolumde bakiye D(L+1)'in altina inerse
 * `enforceUnlockInvariant` coini sessizce geri yazar ve devam BEDAVA olurdu.
 *
 * "Yenilgide mi" sorusu burada SORULMAZ: motor tek dogruluk kaynagidir
 * (`reinforceAfterDefeat` DEFEAT disinda 0 doner ve hicbir sey degismez).
 */
fun continueAllowed(wallet: PlayerWallet, level: Int, usesThisBattle: Int): ContinueDecision {
    if (!EconomyConfig.CONTINUE_ENABLED) return ContinueDecision.Disabled
    if (usesThisBattle >= EconomyConfig.CONTINUE_USES_PER_BATTLE) return ContinueDecision.AlreadyUsed
    val price = continuePrice(level)
    if (wallet.coins < price) return ContinueDecision.InsufficientCoins(price, price - wallet.coins)
    val reserve = reserveFor(wallet)
    val left = wallet.coins - price
    if (left < reserve) return ContinueDecision.ReserveLocked(price, reserve, reserve - left)
    return ContinueDecision.Allowed(price, EconomyConfig.CONTINUE_LIVES, left)
}

/** [CampaignProgressImpl.continueAfterDefeat] sonucu. */
sealed class ContinueResult {
    /** Savas surdu; [price] coin odendi, [lives] can geri geldi. */
    data class Continued(val price: Int, val lives: Int) : ContinueResult()

    /** Ekonomi izin vermedi; hicbir sey degismedi. */
    data class Declined(val decision: ContinueDecision) : ContinueResult()

    /** Ekonomi izin verdi ama motor savasi surdurmedi (yenilgide degil / cift dokunus); hicbir sey degismedi. */
    data object EngineRefused : ContinueResult()
}
