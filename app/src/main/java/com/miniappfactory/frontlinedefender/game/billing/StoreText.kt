package com.miniappfactory.frontlinedefender.game.billing

import androidx.annotation.StringRes
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog

/**
 * Magaza olaylari ve rutbe adlari icin yerellestirilmis metin kimlikleri (2026-09-30).
 *
 * Katalog ([StoreCatalog]) ve olay tipleri ([StoreEvent]) Android'siz kalir; `R` bagimliligi
 * yalnizca bu dosyada. Kaynaklar `strings_store.xml` (values ve values-tr) icinde; `StoreStringsTest` her
 * rutbe ve her hata sebebinin iki dilde de bir metni oldugunu kilitler.
 *
 * ⚠ Bu metinlerde FIYAT ya da fiyattan turetilmis yuzde YOKTUR ve olmamalidir: fiyat yalnizca
 * Play'den gelir (`ProductDetails.formattedPrice`).
 */
@StringRes
fun StoreCatalog.PackTier.nameRes(): Int = when (this) {
    StoreCatalog.PackTier.RECRUIT -> R.string.store_tier_recruit
    StoreCatalog.PackTier.SERGEANT -> R.string.store_tier_sergeant
    StoreCatalog.PackTier.CAPTAIN -> R.string.store_tier_captain
    StoreCatalog.PackTier.COLONEL -> R.string.store_tier_colonel
    StoreCatalog.PackTier.GENERAL -> R.string.store_tier_general
}

/** Hata sebebinin oyuncuya gosterilecek metni. */
@StringRes
fun FailureReason.messageRes(): Int = when (this) {
    FailureReason.NOT_READY -> R.string.store_error_not_ready
    FailureReason.NETWORK -> R.string.store_error_network
    FailureReason.BILLING_UNAVAILABLE -> R.string.store_error_billing_unavailable
    FailureReason.PRODUCT_UNAVAILABLE -> R.string.store_error_product_unavailable
    FailureReason.DEVELOPER_ERROR -> R.string.store_error_developer
    FailureReason.SAVE_FAILED -> R.string.store_error_save_failed
    FailureReason.REJECTED -> R.string.store_error_rejected
    FailureReason.UNKNOWN_ITEM -> R.string.store_error_unknown_item
    FailureReason.BILLING_ERROR -> R.string.store_error_generic
}

/**
 * Olayin metni. [StoreEvent.Granted] icin metin `%1$s` (bicimli coin miktari) ister; digerleri
 * bicimsizdir.
 */
@StringRes
fun StoreEvent.messageRes(): Int = when (this) {
    is StoreEvent.Granted -> R.string.store_event_granted
    is StoreEvent.AlreadyCredited -> R.string.store_event_already_credited
    is StoreEvent.Pending -> R.string.store_event_pending
    StoreEvent.Cancelled -> R.string.store_event_cancelled
    is StoreEvent.Failed -> reason.messageRes()
}
