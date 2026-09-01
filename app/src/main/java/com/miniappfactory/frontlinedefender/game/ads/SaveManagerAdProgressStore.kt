package com.miniappfactory.frontlinedefender.game.ads

import com.miniappfactory.frontlinedefender.game.data.SaveManager

/**
 * Reklam politikasi sayacinin KALICI kopyasi.
 *
 * ## Neden var
 * `AdProgressStore` arayuzunun KDoc'u *"Kaliciligi bu katman yapmaz —
 * DataStore/SaveManager tarafina Faz 6'da baglanir"* diyordu. Faz 6 geldi
 * gecti ve baglanmadi: `AdMobAdHost` varsayilan `InMemoryAdProgressStore`u
 * kullanmaya devam etti, o sinifin kendi yorumu ise *"surec olumunde
 * sifirlanir"* diyordu.
 *
 * Cihazda gorulen sonuc (2026-08-22): *"Level 1 bitirdim, next level
 * dedigimde reklam gelmedi."* Sebep sayacin her acilista 0'a donmesiydi:
 *   - `ONBOARDING_FREE_BATTLES = 3` -> her oturumun ilk 3 savasi reklamsiz
 *   - `isFirstSession` hep true -> `FIRST_SESSION_WARMUP_MS` (3 dk) her
 *     oturumda bastan
 * Yani yeni-oyuncu muafiyeti HIC BITMIYORDU ve gecis reklami pratikte
 * yalnizca uzun oturumlarin sonlarinda cikabiliyordu.
 *
 * ## Neden ayri bir sinif
 * `AdMobAdHost` bilincli olarak `Context` almiyor (reklam katmani saf kalsin,
 * test edilebilir olsun). Kalicilik bu ince adaptorle disaridan enjekte
 * ediliyor — `MainActivity` `SaveManager`i zaten kuruyor.
 *
 * ## adsRemoved
 * 2026-09-02'ye kadar satin alma yoktu ve `false` donuyordu. Artik
 * `SaveManager.adsRemoved` okunur — kaynak Play, kopya cihaz.
 */
class SaveManagerAdProgressStore(
    private val saveManager: SaveManager
) : AdProgressStore {

    override var lifetimeBattlesCompleted: Int
        get() = saveManager.lifetimeBattlesCompleted
        set(value) {
            saveManager.lifetimeBattlesCompleted = value
        }

    /**
     * 2026-09-02 — "Reklamlari Kaldir" IAP eklendi; deger artik `SaveManager`dan
     * gelir (Play'den dogrulanip oraya yazilir, bkz. `billing/BillingManager`).
     * Yalnizca interstitial kapisi bunu okur; rewarded teklifleri okumaz.
     */
    override val adsRemoved: Boolean get() = saveManager.adsRemoved
}
