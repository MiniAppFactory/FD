package com.miniappfactory.frontlinedefender.game.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Faz 5 — rewarded noktalarinin ortak yurutucusu. GDD §G.3'teki uc nokta
 * (R1/R2/R3) ve ayri kararla eklenen savas-ici R4 ([RewardedPlacement.BOOSTER])
 * ayni sozlesmeyi paylasir; yerlesime gore degisen tek sey ad unit kimligi
 * ([AdIds.rewardedAdUnitId]) ve hak muhasebesidir ([RewardedQuotaStore]).
 *
 * ## NO-FILL DOKTRINI (GDD §G.4, DECISIONS bağlayıcı)
 *
 * Bes dal vardir ve **hepsi akisi ayni sekilde surdurur**:
 * `onUserEarnedReward` · `onAdDismissedWithoutReward` · `onAdFailedToLoad` ·
 * `onAdFailedToShow` · `onLoadTimeout (5 sn)`
 *
 * Fark **yalnizca odul buyuklugundedir**:
 *  - [RewardedOutcome.FULL_REWARD]    → tam odul, hak tuketilir
 *  - [RewardedOutcome.REDUCED_REWARD] → azaltilmis odul, R1/R3'te hak TUKETILMEZ
 *  - [RewardedOutcome.UNAVAILABLE]    → teklif zaten tukenmis; odul yok, akis yine acik
 *
 * Sozlesme: [show] cagrildiktan sonra `onResult` **tam olarak bir kez** cagrilir.
 * Cagiran taraf `if (odul) ... else ...` ile IKI AYRI AKIS kurmaz; tek akisin
 * icinde yalnizca odul miktarini secer.
 */
class RewardedAdManager(
    private val quota: RewardedQuotaStore,
    private val analytics: AdAnalytics = LogcatAdAnalytics
) {
    private companion object {
        const val FORMAT = "rewarded"
        const val STALE_AFTER_MS = 20_000L
    }

    private val handler = Handler(Looper.getMainLooper())

    private var cached: RewardedAd? = null

    /**
     * On-yuklenen reklamin HANGI ad unit'ten geldigi. Yerlesimlere ayri birimler
     * verildiginde bir yerlesimin reklami baska bir yerlesimde gosterilmemeli —
     * yoksa AdMob raporlari yanlis birime yazilir ve yerlesim performansi
     * olculemez. Bugun tum birimler ayni test kimligi oldugu icin bu kontrol
     * hicbir davranisi degistirmez.
     */
    private var cachedAdUnitId: String? = null

    private var loading: Boolean = false

    @Volatile
    private var inFlightSince: Long = 0L

    /** Sonuc/bolum-secim ekrani gorunurken sessiz on-yukleme (GDD §G.4). */
    fun preload(context: Context) {
        val adUnitId = AdIds.rewardedAdUnitId()
        if (!AdIds.isConfigured(adUnitId)) return
        if (cached != null || loading) return
        if (!ConsentManager.canRequestAds) return

        loading = true
        analytics.adRequest(FORMAT, "preload")
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    cached = ad
                    cachedAdUnitId = adUnitId
                    Log.i(AD_LOG_TAG, "rewarded preloaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    cached = null
                    cachedAdUnitId = null
                    analytics.adNoFill(FORMAT, "preload", "code=${error.code}")
                }
            }
        )
    }

    fun isOffered(placement: RewardedPlacement): Boolean = quota.isOffered(placement)

    fun remaining(placement: RewardedPlacement): Int = quota.remaining(placement)

    fun show(
        activity: Activity,
        placement: RewardedPlacement,
        onResult: (RewardedResult) -> Unit
    ) {
        // 0) Teklif tukendi mi? (R1 gunluk 3, R2/R3 savas basina 1)
        //    Bu bir REKLAM kontrolu degil, ODUL hakki kontrolu. UI butonu bu
        //    durumda hic gostermemis olmalidir; savunma amacli tekrar bakilir.
        if (!quota.isOffered(placement)) {
            onResult(
                RewardedResult(placement, RewardedOutcome.UNAVAILABLE, AdFallbackReason.QUOTA_EXHAUSTED)
            )
            return
        }

        // 1) Yeniden-giris: ucusta bir gosterim varken ikinci dokunus cift odul
        //    verebilir. Sonuc UNAVAILABLE/ALREADY_IN_FLIGHT doner ve UI bunu
        //    SESSIZCE yok sayar (mesaj gostermez) — birinci cagri zaten sonuca
        //    ulasacak, spinner asili kalmaz.
        val now = SystemClock.elapsedRealtime()
        if (inFlightSince != 0L && (now - inFlightSince) < STALE_AFTER_MS) {
            Log.w(AD_LOG_TAG, "rewarded request ignored: already in flight")
            onResult(
                RewardedResult(placement, RewardedOutcome.UNAVAILABLE, AdFallbackReason.ALREADY_IN_FLIGHT)
            )
            return
        }

        val adUnitId = AdIds.rewardedAdUnitId(placement)

        // 2) Riza kapisi. Onaysiz reklam ISTENMEZ; oyuncu azaltilmis odulle
        //    ilerler (kilitlenme yok).
        if (!ConsentManager.canRequestAds) {
            resolveReduced(placement, AdFallbackReason.NO_CONSENT, onResult)
            return
        }
        if (!AdIds.isConfigured(adUnitId)) {
            resolveReduced(placement, AdFallbackReason.NOT_CONFIGURED, onResult)
            return
        }

        inFlightSince = now
        var finished = false
        val settle: (RewardedResult) -> Unit = { result ->
            if (!finished) {
                finished = true
                handler.removeCallbacksAndMessages(null)
                inFlightSince = 0L
                onResult(result)
            }
        }
        val reduced: (AdFallbackReason) -> Unit = { reason ->
            if (!finished) {
                finished = true
                handler.removeCallbacksAndMessages(null)
                inFlightSince = 0L
                resolveReduced(placement, reason, onResult)
            }
        }

        // On-yuklenen reklam yalnizca AYNI ad unit'e aitse kullanilir; degilse
        // saklanir ve bu yerlesim icin taze bir istek yapilir.
        val ready = if (cachedAdUnitId == adUnitId) cached else null
        if (ready != null) {
            cached = null
            cachedAdUnitId = null
            showNow(activity, ready, adUnitId, placement, settle, reduced)
            return
        }

        analytics.adRequest(FORMAT, placement.name)
        loading = true
        // GDD §G.4 "Asla suresiz spinner yok": 5 sn'de yuklenmezse buton kendi
        // reklamsiz davranisina duser.
        handler.postDelayed({
            analytics.adNoFill(FORMAT, placement.name, "timeout")
            reduced(AdFallbackReason.LOAD_TIMEOUT)
        }, AdPolicyConfig.LOAD_TIMEOUT_MS)

        RewardedAd.load(
            activity.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    if (finished) {
                        // Timeout gecti, oyuncu azaltilmis odulu aldi ve devam etti.
                        // Reklami ZORLA gostermek yerine sakla — bir sonraki
                        // teklif aninda hazir olur.
                        cached = ad
                        cachedAdUnitId = adUnitId
                        return
                    }
                    showNow(activity, ad, adUnitId, placement, settle, reduced)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    cached = null
                    cachedAdUnitId = null
                    if (finished) return
                    analytics.adNoFill(FORMAT, placement.name, "code=${error.code}")
                    reduced(AdFallbackReason.LOAD_FAILED)
                }
            }
        )
    }

    private fun showNow(
        activity: Activity,
        ad: RewardedAd,
        adUnitId: String,
        placement: RewardedPlacement,
        settle: (RewardedResult) -> Unit,
        reduced: (AdFallbackReason) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            cached = ad
            cachedAdUnitId = adUnitId
            reduced(AdFallbackReason.SHOW_FAILED)
            return
        }

        // Odul SADECE gercek SDK callback'inde sayilir. Sahte/anlik odul yok.
        var earned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                analytics.adImpression(FORMAT, placement.name)
            }

            override fun onAdDismissedFullScreenContent() {
                if (earned) {
                    quota.consume(placement)
                    settle(RewardedResult(placement, RewardedOutcome.FULL_REWARD))
                } else {
                    // Oyuncu videoyu bitirmeden kapatti. Ceza yok: azaltilmis
                    // odul verilir, hak tuketilmez (R1/R3).
                    reduced(AdFallbackReason.DISMISSED_WITHOUT_REWARD)
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(AD_LOG_TAG, "rewarded show failed code=${error.code}")
                reduced(AdFallbackReason.SHOW_FAILED)
            }
        }

        ad.show(activity) { earned = true }
    }

    /**
     * Azaltilmis odul dali. Tek istisna: arbitraj tavani dolduysa
     * ([AdPolicyConfig.ANTI_ARBITRAGE_FALLBACK_CAP_ENABLED]) odul verilmez —
     * ama oyuncu yine kilitlenmez, ilerleme kaybetmez.
     */
    private fun resolveReduced(
        placement: RewardedPlacement,
        reason: AdFallbackReason,
        onResult: (RewardedResult) -> Unit
    ) {
        val granted = quota.noteFallback(placement)
        if (granted) {
            analytics.rewardedFallbackGranted(placement.name, reason.name)
            onResult(RewardedResult(placement, RewardedOutcome.REDUCED_REWARD, reason))
        } else {
            Log.i(AD_LOG_TAG, "rewarded fallback cap reached placement=${placement.name}")
            onResult(
                RewardedResult(placement, RewardedOutcome.UNAVAILABLE, AdFallbackReason.FALLBACK_CAP_REACHED)
            )
        }
    }

    fun dispose() {
        handler.removeCallbacksAndMessages(null)
        cached?.fullScreenContentCallback = null
        cached = null
        cachedAdUnitId = null
        inFlightSince = 0L
        loading = false
    }
}
