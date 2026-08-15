package com.miniappfactory.frontlinedefender.game.ads

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Faz 5 — anchored adaptive banner.
 *
 * ## NEREDE KULLANILIR
 * Ana menu, bolum secim, yukseltme dukkani, gorevler, ayarlar — **alt kenar**.
 *
 * ## NEREDE KULLANILMAZ — DECISIONS bağlayıcı
 * **SAVAS EKRANINDA BANNER YOKTUR.** `GameScreen.kt` icindeki savas dalina
 * (GameCanvas / HUDOverlay / TowerBuildBar / SelectedTowerInspector) bu
 * composable **eklenmez**. Gerekce: oynanis alani kutsal + parmak altinda
 * kazara tiklama (invalid traffic) + landscape'te dikey alan kit.
 *
 * ## YUKSEKLIK REZERVASYONU
 * Yer **onceden** ayrilir. AdView'e yukseklik verilmezse reklam gelene kadar
 * 0dp kalir, fill gelince bir anda ~32-90dp'ye sicrar ve ustundeki her seyi
 * yukari iter; parmagin altinda olusan bu duzen kaymasi kazara reklam
 * tiklamasinin en bilinen uretim yoludur.
 *
 * ## NO-FILL: 0dp'YE COKME
 * GDD §G.4 + kabul kriteri §H.7: reklam **kesin olarak** gelmediginde
 * (`onAdFailedToLoad`) serit 0dp'ye coker — bos gri kutu birakilmaz. Cokme
 * yalnizca hata callback'inde olur, **basarili bir fill'den sonra asla**.
 * Serit ekranin en alt kenarina yerlestirildigi ve ustteki icerik
 * `Modifier.weight(1f)` ile buyudugu icin cokme butonlarin yerini
 * degistirmez — bu iki kural birlikte uygulanmak zorundadir.
 */
@Composable
fun BannerAdSlot(
    modifier: Modifier = Modifier,
    /**
     * "Reklamlari Kaldir" satin alindiysa veya bu ekranda banner istenmiyorsa
     * false. false iken **hic yer ayrilmaz** (0dp) ve reklam istenmez.
     */
    enabled: Boolean = true,
    /**
     * Reklam ile ustteki etkilesimli icerik arasinda birakilan bosluk.
     * Kazara tiklamaya karsi zorunlu: hicbir buton reklama bitisik durmaz.
     */
    guardGapDp: Dp = 8.dp
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val canRequestAds by ConsentManager.canRequestAdsFlow.collectAsState()

    if (!enabled) return

    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp

    // Anchored adaptive boyut, GDD §G.1'in "ekran yuksekliginin %10'unu asmaz"
    // kurali ile sinirlanir. Landscape'te dikey alan kit; tablette adaptive
    // boyut 90dp'ye kadar cikabiliyor ve 800dp'lik bir ekranda bu %11'i asar.
    val adSize = remember(widthDp, heightDp) {
        resolveBannerAdSize(context, widthDp, heightDp)
    }

    // Riza yoksa reklam ISTENMEZ ve yer de ayrilmaz.
    if (!canRequestAds) return

    var failed by remember(adSize) { mutableStateOf(false) }
    if (failed) return // 0dp'ye cokme (GDD §H.7)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Rezervasyon: reklam gelmeden ONCE de bu yuksekligi kaplar.
            .height(adSize.height.dp + guardGapDp)
            .padding(top = guardGapDp)
    ) {
        val adView = remember(adSize) {
            AdView(context).apply {
                setAdSize(adSize)
                adUnitId = AdIds.bannerAdUnitId()
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        failed = false
                        LogcatAdAnalytics.adImpression("banner", "menu")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // Serit 0dp'ye coker. Oyuncuya hata GOSTERILMEZ.
                        failed = true
                        LogcatAdAnalytics.adNoFill("banner", "menu", "code=${error.code}")
                    }
                }
            }
        }

        // AdView bir View'dur: pause/resume/destroy edilmezse animasyonlu
        // reklam arka planda calismaya ve pil harcamaya devam eder, Activity
        // yok olurken de sizar.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, adView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                adView.destroy()
            }
        }

        DisposableEffect(adView) {
            if (AdIds.isConfigured(adView.adUnitId ?: "")) {
                LogcatAdAnalytics.adRequest("banner", "menu")
                adView.loadAd(AdRequest.Builder().build())
            }
            onDispose { }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { adView }
        )
    }
}

/**
 * Anchored adaptive boyut + %10 dikey butce siniri.
 *
 * Anchored adaptive kurallari: cihaz yuksekligi <=400dp -> 32dp,
 * 400-720dp -> 50dp, >720dp -> 90dp. Galaxy S8 landscape'te 360dp yukseklik
 * -> 32dp -> %8,9 (butce icinde). Tablet landscape'te 800dp -> 90dp -> %11,25
 * (butce disi) — bu durumda standart 320x50'ye dusulur (yine yuksek fill
 * oranli standart bir boyut, ozel AdSize DEGIL).
 */
private fun resolveBannerAdSize(
    context: android.content.Context,
    widthDp: Int,
    screenHeightDp: Int
): AdSize {
    val budgetDp = (screenHeightDp * 0.10f)
    val adaptive = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    if (adaptive.height <= budgetDp) return adaptive

    // 320x50 standart banner: en yaygin envanter, ozel (custom) AdSize degil.
    // Butceyi bu da asarsa (yani ekran yuksekligi <500dp iken adaptive 90dp
    // dondurduyse — pratikte olusmaz) yine bunu kullaniriz; ozel boyut
    // istemek fill oranini dusurur ve daha kotu bir takas olurdu.
    val downgraded = AdSize.BANNER
    Log.i(
        AD_LOG_TAG,
        "banner size downgraded ${adaptive.height}dp -> ${downgraded.height}dp " +
            "(budget ${budgetDp}dp of ${screenHeightDp}dp)"
    )
    return downgraded
}
