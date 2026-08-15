package com.miniappfactory.frontlinedefender.game.ads

import android.util.Log

/**
 * Faz 5 — GDD §H.9'daki reklam olay sozlesmesi.
 *
 * Firebase Analytics bu projede HENUZ BAGIMLILIK DEGIL (Faz 1'de bilincli
 * kaldirildi). Bu arayuz, olay isimlerini ve alanlarini bugunden sabitler;
 * Firebase eklendiginde tek bir implementasyon yazilir, cagri yerleri
 * degismez.
 *
 * Varsayilan [LogcatAdAnalytics] yalnizca logcat'e yazar — reklam akisinin
 * cihazda kanitlanmasi da bu log uzerinden yapilir
 * (`adb logcat -s FD-Ads`).
 */
interface AdAnalytics {
    /** `ad_request(format, placement)` */
    fun adRequest(format: String, placement: String)

    /** `ad_impression(format, placement)` — reklam ekrani gercekten acildi. */
    fun adImpression(format: String, placement: String)

    /** `ad_no_fill(format, placement)` — yukleme basarisiz/timeout. */
    fun adNoFill(format: String, placement: String, reason: String)

    /** `rewarded_fallback_granted(placement, reason)` — azaltilmis odul verildi. */
    fun rewardedFallbackGranted(placement: String, reason: String)

    /** Frekans kapisinin kapali oldugu durumlar (kap ayarlamasi icin gerekli). */
    fun interstitialSuppressed(cause: String)
}

const val AD_LOG_TAG: String = "FD-Ads"

object LogcatAdAnalytics : AdAnalytics {
    override fun adRequest(format: String, placement: String) {
        Log.i(AD_LOG_TAG, "ad_request format=$format placement=$placement")
    }

    override fun adImpression(format: String, placement: String) {
        Log.i(AD_LOG_TAG, "ad_impression format=$format placement=$placement")
    }

    override fun adNoFill(format: String, placement: String, reason: String) {
        Log.w(AD_LOG_TAG, "ad_no_fill format=$format placement=$placement reason=$reason")
    }

    override fun rewardedFallbackGranted(placement: String, reason: String) {
        Log.w(AD_LOG_TAG, "rewarded_fallback_granted placement=$placement reason=$reason")
    }

    override fun interstitialSuppressed(cause: String) {
        Log.i(AD_LOG_TAG, "interstitial_suppressed cause=$cause")
    }
}
