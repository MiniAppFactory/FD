package com.miniappfactory.frontlinedefender.game.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Faz 5 — UMP (User Messaging Platform) rizasinin TEK kaynagi.
 *
 * Iki ayri soru vardir ve KARISTIRILMAMALIDIR:
 *
 *  1. **[resolved]** — "riza akisi bitti mi?" Yalnizca *UI'i serbest birak*
 *     anlamina gelir (banner cizilebilir, oyun akisi devam eder). Watchdog
 *     suresi dolarsa bu bayrak yine true olur; oyun ASLA UMP'yi beklemek
 *     zorunda kalmaz.
 *  2. **[canRequestAds]** — "reklam ISTEYEBILIR miyim?" Sistemin tek yetkili
 *     cevabi. UC reklam turunun de yukleme oncesi kapisi budur. Watchdog
 *     bunu ASLA true yapmaz.
 *
 * Bu ayrim Boom-Blocks'ta once yanlis yapilmis, sonra duzeltilmis bir hatadir:
 * orada 4 saniyelik guvenlik agi bayragi kosulsuz true'ya cekiliyordu ve
 * `canRequestAds() == false` iken bile reklam aciliyordu — AB Kullanici Rizasi
 * Politikasi ihlali (yaptirim AdMob hesap seviyesinde gelir).
 *
 * Hata/timeout davranisi (GDD §G.5): oyun **asla** bloke edilmez. Form
 * yuklenemezse UMP'nin sakladigi mevcut durum ne diyorsa o gecerlidir; AB'de
 * riza yoksa reklam istenmez ve oyun reklamsiz akisla tam oynanir. Onaysiz
 * kisiselleştirilmis reklam ISTENMEZ.
 */
object ConsentManager {

    /**
     * Reklam istegi yapilabilir mi? Varsayilan false — okunana kadar reklam yok.
     *
     * Compose tarafi [canRequestAdsFlow]'u toplar: riza formu kapandiginda
     * banner yuvasi kendi kendine dogru duruma gecer (yoksa @Volatile bir alan
     * recomposition tetiklemez ve banner ilk acilista hic gelmez).
     */
    private val _canRequestAdsFlow = MutableStateFlow(false)
    val canRequestAdsFlow: StateFlow<Boolean> = _canRequestAdsFlow.asStateFlow()

    val canRequestAds: Boolean get() = _canRequestAdsFlow.value

    /** Ayarlar'da "Reklam ayarlari / Gizlilik Secenekleri" satiri gorunsun mu? */
    @Volatile
    var privacyOptionsRequired: Boolean = false
        private set

    /** Riza akisi sonuclandi mi (UI kapisi, reklam yetkisi DEGIL)? */
    @Volatile
    var resolved: Boolean = false
        private set

    /**
     * UMP callback'i hic gelmezse oyunun beklememesi icin guvenlik agi.
     * Sadece [resolved] bayragini kaldirir.
     */
    private const val WATCHDOG_MS: Long = 4_000L

    /**
     * AB icindeymis gibi davranarak formu test etmek icin. `true` yapmak icin
     * [AdIds.developerTestDeviceIds] listesinin dolu olmasi gerekir (UMP
     * hash'lenmis cihaz kimligi ister; logcat'te "UMP" etiketiyle yazilir).
     * Uretimde HER ZAMAN false.
     */
    private const val FORCE_EEA_FOR_TESTING: Boolean = false

    private var gathering: Boolean = false

    /**
     * Uygulama acilisinda bir kez cagrilir.
     *
     * [onResolved] **her durumda ve tam bir kez** cagrilir (basari, hata,
     * timeout). Cagiran taraf bu callback'i "artik reklam katmani hazir"
     * sinyali olarak kullanir; oyunun baslamasi icin BEKLEMEZ.
     */
    fun gather(activity: Activity, onResolved: () -> Unit = {}) {
        if (gathering) return
        gathering = true

        val handler = Handler(Looper.getMainLooper())
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                handler.removeCallbacksAndMessages(null)
                refresh(activity)
                resolved = true
                gathering = false
                Log.i(
                    AD_LOG_TAG,
                    "consent resolved canRequestAds=$canRequestAds " +
                        "privacyOptionsRequired=$privacyOptionsRequired"
                )
                onResolved()
            }
        }

        handler.postDelayed({
            if (!finished) {
                // DIKKAT: burada canRequestAds'e DOKUNULMAZ. Yalnizca UI serbest
                // birakilir; riza bilinmiyorsa reklam istenmemeye devam eder.
                Log.w(AD_LOG_TAG, "consent watchdog fired after ${WATCHDOG_MS}ms")
                finish()
            }
        }, WATCHDOG_MS)

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .apply {
                if (FORCE_EEA_FOR_TESTING && AdIds.developerTestDeviceIds.isNotEmpty()) {
                    val debug = ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .apply { AdIds.developerTestDeviceIds.forEach { addTestDeviceHashedId(it) } }
                        .build()
                    setConsentDebugSettings(debug)
                }
            }
            .build()

        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Guncelleme basarili: form GEREKIYORSA gosterilir, gerekmiyorsa
                // bu cagri aninda callback'i doner (ekstra bir dal gerekmez).
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(
                            AD_LOG_TAG,
                            "consent form error ${formError.errorCode}: ${formError.message}"
                        )
                    }
                    finish()
                }
            },
            { requestError ->
                // Ag yok / UMP hatasi. Oyun bloklanmaz; mevcut kayitli riza
                // durumu ne diyorsa o gecerli olur.
                Log.w(
                    AD_LOG_TAG,
                    "consent info update failed ${requestError.errorCode}: ${requestError.message}"
                )
                finish()
            }
        )
    }

    /** Riza durumunu UMP'den yeniden okur. Her sonuc/degisiklik sonrasi cagrilir. */
    fun refresh(context: Context) {
        val info = UserMessagingPlatform.getConsentInformation(context)
        _canRequestAdsFlow.value = info.canRequestAds()
        privacyOptionsRequired =
            info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /**
     * Ayarlar > "Reklam ayarlari". UMP, form gerektigi bolgelerde uygulamanin
     * rizayi SONRADAN degistirebilecek kalici bir giris noktasi sunmasini
     * zorunlu kilar.
     *
     * Form kapaninca durum yeniden okunur: oyuncu rizasini geri cekmisse
     * [canRequestAds] aninda false olur ve sonraki tum reklam istekleri
     * kapida durur.
     */
    fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(
                    AD_LOG_TAG,
                    "privacy options error ${formError.errorCode}: ${formError.message}"
                )
            }
            refresh(activity)
            onDismissed()
        }
    }

    /** Yalnizca test/QA icin: kaydedilmis rizayi siler, sonraki acilista form gelir. */
    fun resetForTesting(context: Context) {
        UserMessagingPlatform.getConsentInformation(context).reset()
        _canRequestAdsFlow.value = false
        privacyOptionsRequired = false
        resolved = false
    }
}
