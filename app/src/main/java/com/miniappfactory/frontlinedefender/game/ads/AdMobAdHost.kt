package com.miniappfactory.frontlinedefender.game.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

/**
 * Faz 5 — [AdHost]'un gercek AdMob implementasyonu.
 *
 * Kurulum sirasi (Google'in onerdigi sira, DEGISTIRILMEMELI):
 *   1. UMP riza bilgisini topla / formu gerekiyorsa goster
 *   2. `canRequestAds` true ise Mobile Ads SDK'yi baslat
 *   3. On-yukleme yap
 *
 * SDK **riza alinmadan baslatilmaz**; boylece onaysiz hicbir reklam istegi
 * cikmaz. Oyun bu adimlarin HICBIRINI beklemez — 2. ve 3. adim hic olmasa
 * bile oyun tam oynanir (offline oynanabilir bir oyundur).
 */
class AdMobAdHost(
    private val progressStore: AdProgressStore = InMemoryAdProgressStore(),
    private val quota: RewardedQuotaStore = InMemoryRewardedQuotaStore(),
    private val analytics: AdAnalytics = LogcatAdAnalytics
) : AdHost {

    private val policy = InterstitialPolicy(progressStore)
    private val interstitial = InterstitialAdManager(policy, analytics)
    private val rewarded = RewardedAdManager(quota, analytics)

    @Volatile
    private var sdkInitialized: Boolean = false

    override val bannerAllowed: Boolean
        get() = !progressStore.adsRemoved && ConsentManager.canRequestAds

    override val privacyOptionsRequired: Boolean
        get() = ConsentManager.privacyOptionsRequired

    override fun initialize(activity: Activity) {
        val appContext = activity.applicationContext
        ConsentManager.gather(activity) {
            if (ConsentManager.canRequestAds) {
                ensureSdkInitialized(appContext) {
                    preload(appContext)
                }
            } else {
                Log.i(AD_LOG_TAG, "SDK init skipped: canRequestAds=false")
            }
        }
    }

    private fun ensureSdkInitialized(context: Context, onReady: () -> Unit) {
        if (sdkInitialized) {
            onReady()
            return
        }

        // GDD §G.5 uyum ayarlari. Cocuklara yonelik DEGIL, ama icerik
        // derecelendirmesi hedefi Everyone 10+ / PEGI 7 oldugu icin reklam
        // icerigi "G" ile sinirlanir.
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(
                    RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
                )
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .setTestDeviceIds(AdIds.developerTestDeviceIds)
                .build()
        )

        // MobileAds.initialize disk ve ag isi yapar; ana is parcaciginda
        // cagrilmasi acilista jank/ANR riski demektir.
        Thread {
            MobileAds.initialize(context) { status ->
                sdkInitialized = true
                status.adapterStatusMap.forEach { (adapter, state) ->
                    Log.i(AD_LOG_TAG, "adapter $adapter -> ${state.initializationState}")
                }
                Log.i(
                    AD_LOG_TAG,
                    "MobileAds initialized testAds=${AdIds.USE_TEST_ADS} " +
                        "appId=${AdIds.manifestAppId}"
                )
                onReady()
            }
        }.apply { name = "admob-init" }.start()
    }

    override fun preload(context: Context) {
        if (!sdkInitialized || !ConsentManager.canRequestAds) return
        interstitial.preload(context)
        rewarded.preload(context)
    }

    override fun onBattleStarted() {
        policy.onBattleStarted()
        quota.onBattleStarted()
    }

    override fun onBattleCompleted() = policy.onBattleCompleted()

    override fun onBattleAbandoned() = policy.onBattleAbandoned()

    override fun showInterstitial(
        activity: Activity,
        reason: InterstitialReason,
        onProceed: () -> Unit
    ) = interstitial.showIfAllowed(activity, reason, onProceed)

    override fun showRewarded(
        activity: Activity,
        placement: RewardedPlacement,
        onResult: (RewardedResult) -> Unit
    ) = rewarded.show(activity, placement, onResult)

    override fun isRewardedOffered(placement: RewardedPlacement): Boolean =
        rewarded.isOffered(placement)

    override fun rewardedRemaining(placement: RewardedPlacement): Int =
        rewarded.remaining(placement)

    override fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit) {
        ConsentManager.showPrivacyOptions(activity) {
            // Riza geri cekilmis olabilir; sonraki istekler yeni duruma uyar.
            if (ConsentManager.canRequestAds) preload(activity.applicationContext)
            onDismissed()
        }
    }

    /** Activity yok olurken cagrilir: bekleyen timeout ve reklam referanslari birakilir. */
    fun dispose() {
        interstitial.dispose()
        rewarded.dispose()
    }
}
