package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * ZAFER -> SONRAKI BOLUM GECIS SAHIBI (FD-008 / FD-010).
 *
 * `VictoryFlow` "hangi adim" kararini verir; bu sinif o adimin UI OMRUNU ve
 * TEK-GECIS garantisini tutar. `GameScreen`in zafer dali Compose'dur ve iki
 * tuzak barindirir (FD-010 ara incelemesi):
 *
 *   1. **Cift callback / iki geçiş.** Zafer butonu veya onay/ kart callback'i
 *      ayni karede iki kez tetiklenirse, ya da ilk `startNewGame`den SONRA
 *      bayat bir callback tekrar kosarsa, motorun artik DEGISMIS `levelId`'si
 *      uzerinden YANLIS (bir sonraki) bolum baslatilabilir.
 *   2. **Bayat overlay.** `pendingUnlock/Intro` ekran omru boyunca yasar;
 *      BackHandler ile haritaya cikip baska bir zafere donuldugunde eski
 *      pencere acilabilir.
 *
 * Cozum: butun gecis durumu BURADA; her giris noktasi bir [VictoryFlow.Step]
 * doner ve ikinci kez cagrildiginda [VictoryFlow.Step.None] verir (tek sahiplik).
 * `GameScreen` yalnizca donen `Start` adimini uygular (savas baslatir) ve
 * [pendingUnlock]/[pendingIntro]'yu cizer. Durum saf Kotlin snapshot state'te
 * oldugu icin bu sinif Compose'suz birim testlenebilir ([VictoryTransitionTest]).
 *
 * @param campaignLevels kampanya bolum sayisi (`EconomyConfig.CAMPAIGN_LEVELS`).
 */
class VictoryTransition(private val campaignLevels: Int) {

    /** Kilit acma onayi bekleyen bolum (haritaya donmeden). null = pencere yok. */
    var pendingUnlock by mutableStateOf<Int?>(null)
        private set

    /** Gorulmemis perde acilis karti bekleyen bolum. null = kart yok. */
    var pendingIntro by mutableStateOf<Int?>(null)
        private set

    /** Bu zaferde gecis sahiplenildi mi — tek `startNewGame` garantisi. */
    private var handed = false

    /**
     * SONRAKI BOLUM dokunuldu. [clearedLevelId] zaferdeki DONMUS hedeftir
     * (composition degeri `nextCampaignLevel - 1`), motorun anlik degeri DEGIL.
     * Bir pencere acikken ya da gecis sahiplenildiyse yeni gecis baslamaz.
     */
    fun onNextTapped(
        clearedLevelId: Int,
        isNextUnlocked: Boolean,
        store: ActIntroStore?
    ): VictoryFlow.Step {
        if (handed || pendingUnlock != null || pendingIntro != null) return VictoryFlow.Step.None
        return apply(VictoryFlow.firstStep(clearedLevelId, campaignLevels, isNextUnlocked, store))
    }

    /**
     * Kilit acma onaylandi. PARA YAN ETKISI ([charge], tipik olarak
     * `tryUnlock`) sahip guard'inin ICINDE calisir: yalnizca pencere gercekten
     * BU bolum icin acikken ve gecis sahiplenilmemisken. Boylece iptal/ekrandan
     * cikis sonrasi BAYAT bir onay callback'i calissa bile `charge` HIC
     * cagrilmaz — coin dusmez, kilit acilmaz (FD-012). [charge] islem-ani
     * bakiyeyi kontrol eder ve basari (`true`) ya da yetersiz/zaten-acik
     * (`false`) doner; `false` ise gecis olmaz, oyuncu zaferde kalir.
     */
    fun onUnlockConfirmed(level: Int, store: ActIntroStore?, charge: () -> Boolean): VictoryFlow.Step {
        if (pendingUnlock != level || handed) return VictoryFlow.Step.None
        pendingUnlock = null
        if (!charge()) return VictoryFlow.Step.None
        return apply(VictoryFlow.afterUnlock(level, store))
    }

    /** Onay iptal edildi: pencere kapanir, zafer durumu korunur, ucret yok. */
    fun onUnlockCancelled(level: Int) {
        if (pendingUnlock == level) pendingUnlock = null
    }

    /** Perde karti kapatildi: tek sefer savasi baslatir (bayat tekrar korumali). */
    fun onIntroDismissed(level: Int): VictoryFlow.Step {
        if (pendingIntro != level || handed) return VictoryFlow.Step.None
        pendingIntro = null
        handed = true
        return VictoryFlow.Step.Start(level)
    }

    /**
     * VICTORY durumundan cikildi (sonraki baslatildi / haritaya donuldu / ana
     * menu / geri tusu). Butun bekleyen pencereleri ve sahiplik bayragini
     * sifirlar; boylece bir sonraki zafer TEMIZ baslar ve bayat overlay acilmaz.
     */
    fun onLeftVictory() {
        pendingUnlock = null
        pendingIntro = null
        handed = false
    }

    private fun apply(step: VictoryFlow.Step): VictoryFlow.Step = when (step) {
        is VictoryFlow.Step.Unlock -> { pendingUnlock = step.level; step }
        is VictoryFlow.Step.Intro -> { pendingIntro = step.level; step }
        is VictoryFlow.Step.Start -> { handed = true; step }
        VictoryFlow.Step.None -> step
    }
}
