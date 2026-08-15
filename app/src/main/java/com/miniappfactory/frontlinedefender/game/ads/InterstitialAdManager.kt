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
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Faz 5 — zorunlu gecis reklami. **Tek mesru nokta:** sonuc ekranindan bolum
 * secime donus (GDD §G.1). Savas BASLAMADAN once interstitial YOK, savas
 * icinde YOK, App Open YOK.
 *
 * Sozlesme: [showIfAllowed] cagrildiktan sonra `onProceed` **tam olarak bir kez**
 * cagrilir. Reklam gosterildi, gosterilemedi, frekans kapi kapali, riza yok,
 * yukleme timeout — hepsinde ayni. Cagiran taraf navigasyonu SADECE bu
 * callback'in icinde yapar; boylece "reklam varsa baska, yoksa baska" gibi iki
 * ayri akis olusmaz.
 */
class InterstitialAdManager(
    private val policy: InterstitialPolicy,
    private val analytics: AdAnalytics = LogcatAdAnalytics
) {
    private companion object {
        const val FORMAT = "interstitial"

        /**
         * Guvenlik supabi: SDK callback'i hic gelmezse (uretici hatasi, surec
         * olumu) yeniden-giris bayragi sonsuza kadar takili kalir ve TUM
         * ilerleme butonlari sessizce olur — oyuncuyu kilitlememe ilkesinin tam
         * tersi. Bu sure sonunda bayrak bayat sayilir.
         */
        const val STALE_AFTER_MS = 15_000L
    }

    private val handler = Handler(Looper.getMainLooper())

    /** On-yuklenmis, henuz gosterilmemis reklam. */
    private var cached: InterstitialAd? = null
    private var loading: Boolean = false

    /**
     * Yeniden-giris korumasi. load() ile show() arasinda 1-3 saniye sessizlik
     * var; oyuncunun butona TEKRAR basmasi dogal bir tepki. Her dokunus ayri
     * bir akis baslatirsa iki tam ekran reklam pes pese acilir (dogrudan bir
     * AdMob yerlesim politikasi ihlali) ve `onProceed` iki kez calisip cift
     * navigasyon yapar. Ucusta bir gosterim varken gelen cagri SESSIZCE
     * DUSURULUR — `onProceed` cagrilmaz, cunku birinci cagrinin callback'i
     * akisi zaten surdurecek.
     */
    @Volatile
    private var inFlightSince: Long = 0L

    /** Sonuc ekrani gorunurken cagrilir: sessiz on-yukleme, UI'a hata gosterilmez. */
    fun preload(context: Context) {
        val adUnitId = AdIds.interstitialAdUnitId()
        if (!AdIds.isConfigured(adUnitId)) return
        if (cached != null || loading) return
        if (!ConsentManager.canRequestAds) return

        loading = true
        analytics.adRequest(FORMAT, "preload")
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    cached = ad
                    Log.i(AD_LOG_TAG, "interstitial preloaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    cached = null
                    // Sessizce loglanir; oyuncuya hata GOSTERILMEZ (GDD §G.4).
                    analytics.adNoFill(FORMAT, "preload", "code=${error.code}")
                }
            }
        )
    }

    fun showIfAllowed(activity: Activity, reason: InterstitialReason, onProceed: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        val busy = inFlightSince != 0L && (now - inFlightSince) < STALE_AFTER_MS
        if (busy) {
            Log.w(AD_LOG_TAG, "interstitial request dropped: already in flight")
            return
        }

        // 1) Frekans kapisi (GDD §G.2). Reklam ISTENMEDEN once karar verilir —
        //    gereksiz istek hem gecikme hem fill-rate kirliligi demek.
        val decision = policy.decide(reason)
        if (decision is InterstitialDecision.Skip) {
            analytics.interstitialSuppressed(decision.cause.name)
            policy.onInterstitialSkipped()
            // Sonraki dogal mola noktasi icin arka planda hazirlan.
            preload(activity.applicationContext)
            onProceed()
            return
        }

        // 2) Riza kapisi. canRequestAds false iken reklam ISTENMEZ — ama oyuncu
        //    yine ilerler. Reklamsiz akis her zaman calisan akistir.
        if (!ConsentManager.canRequestAds) {
            Log.i(AD_LOG_TAG, "interstitial skipped: consent not granted")
            policy.onInterstitialSkipped()
            onProceed()
            return
        }

        val adUnitId = AdIds.interstitialAdUnitId()
        if (!AdIds.isConfigured(adUnitId)) {
            policy.onInterstitialSkipped()
            onProceed()
            return
        }

        inFlightSince = now
        var finished = false
        val finish: (String) -> Unit = { note ->
            if (!finished) {
                finished = true
                handler.removeCallbacksAndMessages(null)
                inFlightSince = 0L
                Log.i(AD_LOG_TAG, "interstitial flow finished ($note)")
                onProceed()
            }
        }

        val ready = cached
        if (ready != null) {
            cached = null
            showNow(activity, ready, finish)
            return
        }

        // 3) Elde hazir reklam yok: yukle, ama 5 sn'den fazla bekletmeyiz.
        analytics.adRequest(FORMAT, reason.name)
        loading = true
        handler.postDelayed({
            // Timeout: akisi HEMEN surduruyoruz. Geciken reklam navigasyondan
            // SONRA gosterilmez (hem politika ihlali hem UX kirilmasi); onun
            // yerine cache'e alinip bir sonraki dogal mola noktasinda kullanilir.
            analytics.adNoFill(FORMAT, reason.name, "timeout")
            policy.onInterstitialSkipped()
            finish("timeout")
        }, AdPolicyConfig.LOAD_TIMEOUT_MS)

        InterstitialAd.load(
            activity.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    if (finished) {
                        // Timeout gecti, oyuncu ilerledi. Reklami gostermek yerine sakla.
                        cached = ad
                        return
                    }
                    showNow(activity, ad, finish)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    cached = null
                    if (finished) return
                    analytics.adNoFill(FORMAT, reason.name, "code=${error.code}")
                    // No-fill cooldown BASLATMAZ: hic reklam gormeyen oyuncuyu
                    // 120 sn boyunca bir daha denememek fill'i bosa harcar.
                    policy.onInterstitialSkipped()
                    finish("no-fill")
                }
            }
        )
    }

    private fun showNow(activity: Activity, ad: InterstitialAd, finish: (String) -> Unit) {
        // Activity yok olduysa gosterme (crash yerine sessiz devam).
        if (activity.isFinishing || activity.isDestroyed) {
            cached = ad
            finish("activity gone")
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                analytics.adImpression(FORMAT, "result_to_level_select")
                policy.onInterstitialShown()
            }

            override fun onAdDismissedFullScreenContent() = finish("dismissed")

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(AD_LOG_TAG, "interstitial show failed code=${error.code}")
                policy.onInterstitialSkipped()
                finish("show-failed")
            }
        }
        ad.show(activity)
    }

    /** Activity yok olurken: bekleyen timeout'lari temizle, referanslari birak. */
    fun dispose() {
        handler.removeCallbacksAndMessages(null)
        cached?.fullScreenContentCallback = null
        cached = null
        inFlightSince = 0L
        loading = false
    }
}
