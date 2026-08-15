package com.miniappfactory.frontlinedefender.game.ads

import android.os.SystemClock
import java.util.Calendar

/**
 * Faz 5 — reklam sikligi ve hak muhasebesi. Reklam SDK'sina hic dokunmaz,
 * bu yuzden tamamen birim-test edilebilir (JVM'de calisir).
 *
 * Buradaki tum sayilar GDD §G.2 / §G.3'ten gelir; degistirilmesi karar ister.
 */
object AdPolicyConfig {

    // --- Interstitial frekans kaplari (GDD §G.2, hepsi BIRLIKTE uygulanir) ---

    /** #1 — yeni kurulumun ilk 3 savasinda hic interstitial gosterilmez. */
    const val ONBOARDING_FREE_BATTLES: Int = 3

    /** #2 — uygulama basladiktan sonraki ilk 60 saniye reklamsiz (oturum-farkindalikli cap). */
    const val SESSION_WARMUP_MS: Long = 60_000L

    /**
     * #2b — **ILK oturum** (kurulumdan sonra hic savas tamamlanmamis) icin
     * daha uzun isinma: 3 dakika. GDD §G.2/2'nin (60 sn) ustune eklenen, ondan
     * DAHA SIKI bir kap; yeni oyuncunun ilk oturumu pratikte reklamsiz gecer.
     * ONBOARDING_FREE_BATTLES ile birlikte calisir, onun yerine gecmez.
     */
    const val FIRST_SESSION_WARMUP_MS: Long = 180_000L

    /** #3 — iki interstitial arasi minimum sure (cooldown). */
    const val INTERSTITIAL_COOLDOWN_MS: Long = 120_000L

    /** #4 — oturum basina maksimum interstitial. */
    const val MAX_INTERSTITIALS_PER_SESSION: Int = 6

    /**
     * #4b — **her bolumde degil**: yalnizca tamamlanan her N. savastan sonra
     * interstitial hakki dogar (N=2 -> 4., 6., 8. savas...).
     *
     * Neden cooldown yetmiyor: bir TD bolumu 3-5 dakika surer, yani 120 sn'lik
     * cooldown iki bolum arasinda kendi kendine dolar ve pratikte HER bolum
     * sonunda reklam cikar. Reklam yorgunlugunun en hizli yolu bu. Kadans
     * kapisi, gosterim basi geliri korurken gosterim sayisini yariya indirir.
     *
     * GDD §G.2'de yok; oradaki kaplarin ustune eklenen DAHA SIKI bir kap
     * oldugu icin hicbir GDD kuralini ihlal etmez.
     */
    const val INTERSTITIAL_EVERY_NTH_COMPLETED_BATTLE: Int = 2

    /**
     * Sonuc modali kapandiktan sonra reklam akisi bu sureden uzun surerse
     * navigasyon KOSULSUZ yapilir. Reklam SDK'si callback'i hic dondurmezse
     * oyuncu sonuc ekransiz + modalsiz bir savas alaninda kilitli kalirdi;
     * bu supap "no-fill oyuncuyu ASLA kilitlemez" kuralinin son savunmasidir.
     */
    const val EXIT_WATCHDOG_MS: Long = 15_000L

    /** GDD §G.2/6 — zaferi kutsal olan boss bolumleri. */
    val BOSS_LEVEL_IDS: Set<Int> = setOf(11, 22)

    // --- Rewarded limitleri (GDD §G.3) ---

    /** R1 Tedarik Talebi: 3/gun. */
    const val SUPPLY_DROP_DAILY_LIMIT: Int = 3

    /** R2 Takviye: savas basina 1. */
    const val REINFORCEMENT_PER_BATTLE_LIMIT: Int = 1

    /** R3 Cift Odeme: savas basina 1. */
    const val DOUBLE_PAYOUT_PER_BATTLE_LIMIT: Int = 1

    // --- Odul buyuklukleri (GDD §G.3 / §G.4 tablosu) ---

    /** R1 tam odul. */
    const val SUPPLY_DROP_FULL_COIN: Int = 150

    /** R1 azaltilmis odul ("Karargah elindekini gonderdi"). */
    const val SUPPLY_DROP_REDUCED_COIN: Int = 50

    /** R2 tam odul: us cani bu degere getirilir ve savas kaldigi dalgadan devam eder. */
    const val REINFORCEMENT_LIVES: Int = 5

    /** R3 tam odul: o temizligin coin odulu bu katsayiyla carpilir. */
    const val DOUBLE_PAYOUT_MULTIPLIER: Int = 2

    // --- Yukleme suresi ---

    /**
     * GDD §G.4 "Asla suresiz spinner yok": yukleme 5 sn'de bitmezse buton
     * kendi reklamsiz davranisina duser. Interstitial'a da uygulanir — geciken
     * reklam navigasyondan SONRA gosterilmez (politika ihlali + UX kirilmasi).
     */
    const val LOAD_TIMEOUT_MS: Long = 5_000L

    // --- EKONOMI ARBITRAJI KORUMASI (Faz 5'te bayrak kaldirildi) ---

    /**
     * GDD §G.4 R1'i soyle tanimliyor: no-fill'de +50 coin verilir, **gunluk hak
     * tuketilmez, cooldown yok, hemen tekrar denenebilir.**
     *
     * Bu haliyle ucak modundaki oyuncu icin SINIRSIZ coin musluğudur:
     * dokun -> +50 -> hak tukenmedi -> tekrar dokun. Bolum 7 kilidi 100 coin
     * oldugu icin GDD §C'nin butun coin duvari matematigi anlamsizlasir ve
     * reklam geliri sifira iner (ayni sonucu veren iki yoldan biri reklamsiz).
     *
     * Cozum, doktrini BOZMADAN: gercek odul hakki (3/gun) **hic tuketilmez**;
     * yalnizca *azaltilmis* odulun gunde kac kez verilebilecegi tavanlanir.
     * Oyuncu asla kilitlenmez ve ilerleme kaybetmez — sadece farm edemez.
     *
     * Tavan dolduktan sonra buton yine tiklanabilir kalir; sonucu
     * [RewardedOutcome.UNAVAILABLE] olur ve UI notr bir mesaj gosterir.
     *
     * **BAYRAK: bu GDD §G.4 metninden bilincli bir sapmadir; orkestrasyon
     * onayi/reddi bekliyor.** Reddedilirse tek satir: `= false`.
     */
    const val ANTI_ARBITRAGE_FALLBACK_CAP_ENABLED: Boolean = true

    /** Gunde kac kez azaltilmis R1 odulu verilebilir (gercek hakla ayni sayi). */
    const val SUPPLY_DROP_FALLBACK_DAILY_CAP: Int = 3
}

/**
 * Reklam kararlari icin gereken KALICI durum. Kaliciligi bu katman yapmaz —
 * DataStore/SaveManager tarafina Faz 6'da baglanir (bkz. ADMOB_INTEGRATION.md §5).
 */
interface AdProgressStore {
    /** Kurulumdan bu yana SONUNA KADAR oynanmis savas sayisi (GDD §G.2/1). */
    var lifetimeBattlesCompleted: Int

    /** "Reklamlari Kaldir" satin alindi mi (GDD §G.2/7)? v1.0'da her zaman false. */
    val adsRemoved: Boolean
}

/** Kalicilik baglanana kadarki varsayilan — surec olumunde sifirlanir. */
class InMemoryAdProgressStore(
    override var lifetimeBattlesCompleted: Int = 0,
    override val adsRemoved: Boolean = false
) : AdProgressStore

/** Interstitial kararinin sonucu. Teshis edilebilirlik icin sebep tasir. */
sealed interface InterstitialDecision {
    data object Show : InterstitialDecision
    data class Skip(val cause: Cause) : InterstitialDecision {
        enum class Cause {
            ADS_REMOVED,
            ONBOARDING_GRACE,
            SESSION_WARMUP,
            BATTLE_CADENCE,
            COOLDOWN,
            SESSION_CAP,
            NO_COMPLETED_BATTLE,
            BOSS_VICTORY_PROTECTED
        }
    }
}

/**
 * Interstitial frekans kapisi. Oturum durumu bellekte, kalici durum
 * [AdProgressStore]'da. Test edilebilirlik icin saat enjekte edilebilir.
 */
class InterstitialPolicy(
    private val store: AdProgressStore,
    private val elapsedMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val sessionStartMs: Long = elapsedMs()
    private var lastShownMs: Long = Long.MIN_VALUE
    private var shownThisSession: Int = 0

    /**
     * Bu oturum, kurulumdan sonraki ILK oyun oturumu mu? Oturum basinda hic
     * tamamlanmis savas yoksa evet — o zaman isinma suresi 60 sn yerine
     * 3 dakikadir (bkz. [AdPolicyConfig.FIRST_SESSION_WARMUP_MS]).
     */
    private val isFirstSession: Boolean = store.lifetimeBattlesCompleted == 0

    private val warmupMs: Long
        get() = if (isFirstSession) {
            AdPolicyConfig.FIRST_SESSION_WARMUP_MS
        } else {
            AdPolicyConfig.SESSION_WARMUP_MS
        }

    /**
     * GDD §G.2/5 — yarida birakilan savas interstitial tetiklemez. Savas
     * gercekten bitince true olur, gosterim veya yeni savas ile temizlenir.
     */
    private var battleCompletedPending: Boolean = false

    fun onBattleStarted() {
        battleCompletedPending = false
    }

    fun onBattleCompleted() {
        battleCompletedPending = true
        store.lifetimeBattlesCompleted = store.lifetimeBattlesCompleted + 1
    }

    fun onBattleAbandoned() {
        battleCompletedPending = false
    }

    fun decide(reason: InterstitialReason): InterstitialDecision {
        if (store.adsRemoved) {
            return InterstitialDecision.Skip(InterstitialDecision.Skip.Cause.ADS_REMOVED)
        }
        // GDD §G.2/6 — boss zaferi kutsal; hak bir sonraki donuse kayar
        // (battleCompletedPending TEMIZLENMEZ).
        if (reason == InterstitialReason.BOSS_VICTORY_TO_LEVEL_SELECT) {
            return InterstitialDecision.Skip(InterstitialDecision.Skip.Cause.BOSS_VICTORY_PROTECTED)
        }
        if (!battleCompletedPending) {
            return InterstitialDecision.Skip(InterstitialDecision.Skip.Cause.NO_COMPLETED_BATTLE)
        }
        if (store.lifetimeBattlesCompleted <= AdPolicyConfig.ONBOARDING_FREE_BATTLES) {
            return InterstitialDecision.Skip(InterstitialDecision.Skip.Cause.ONBOARDING_GRACE)
        }
        val now = elapsedMs()
        if (now - sessionStartMs < warmupMs) {
            return InterstitialDecision.Skip(InterstitialDecision.Skip.Cause.SESSION_WARMUP)
        }
        // Her bolumde degil: yalnizca her N. tamamlanan savastan sonra.
        val nth = AdPolicyConfig.INTERSTITIAL_EVERY_NTH_COMPLETED_BATTLE
        if (nth > 1 && store.lifetimeBattlesCompleted % nth != 0) {
            return InterstitialDecision.Skip(InterstitialDecision.Skip.Cause.BATTLE_CADENCE)
        }
        if (shownThisSession >= AdPolicyConfig.MAX_INTERSTITIALS_PER_SESSION) {
            return InterstitialDecision.Skip(InterstitialDecision.Skip.Cause.SESSION_CAP)
        }
        if (lastShownMs != Long.MIN_VALUE &&
            now - lastShownMs < AdPolicyConfig.INTERSTITIAL_COOLDOWN_MS
        ) {
            return InterstitialDecision.Skip(InterstitialDecision.Skip.Cause.COOLDOWN)
        }
        return InterstitialDecision.Show
    }

    /**
     * Gerceklesen gosterimi kaydeder. SADECE reklam ekrani gercekten acildiginda
     * cagrilir — no-fill cooldown baslatmaz, yoksa hic reklam gormeyen oyuncu
     * 120 sn boyunca bir daha denenmez.
     */
    fun onInterstitialShown() {
        lastShownMs = elapsedMs()
        shownThisSession++
        battleCompletedPending = false
    }

    /** Gosterim olmadan akis surdurulduginde: hak yanmaz, ama tekrar denenmez. */
    fun onInterstitialSkipped() {
        battleCompletedPending = false
    }
}

/**
 * Rewarded hak muhasebesi ARAYUZU. Kalicilik (DataStore) baska katmanin isi;
 * burada yalnizca sozlesme + bellek-ici varsayilan var.
 */
interface RewardedQuotaStore {
    /** Teklif hala acik mi (buton gosterilsin mi)? */
    fun isOffered(placement: RewardedPlacement): Boolean

    /** Kalan hak. Sinirsiz icin [Int.MAX_VALUE]. */
    fun remaining(placement: RewardedPlacement): Int

    /** SADECE gercek odul kazanildiginda (FULL_REWARD) cagrilir. */
    fun consume(placement: RewardedPlacement)

    /**
     * Azaltilmis odul verilecek. Donen deger: odul verilebilir mi?
     *
     * R1: gunluk gercek hak **tuketilmez** (GDD §G.4). Yalnizca arbitraj
     * tavani sayilir.
     * R2: GDD §G.4 aciktir — "bu savas icin hak kullanilmis sayilir", yani
     * no-fill'de de savas-basi hak tuketilir.
     */
    fun noteFallback(placement: RewardedPlacement): Boolean

    /** Yeni savas: savas-basi haklar sifirlanir. */
    fun onBattleStarted()
}

/**
 * Bellek-ici varsayilan. Uygulama yeniden baslatildiginda gunluk haklar
 * sifirlanir — bu yuzden Faz 6'da DataStore destekli bir implementasyonla
 * DEGISTIRILMESI ZORUNLUDUR (bkz. ADMOB_INTEGRATION.md §5).
 */
class InMemoryRewardedQuotaStore(
    private val dayKey: () -> Int = { localDayKey() }
) : RewardedQuotaStore {

    private var currentDay: Int = dayKey()
    private var supplyDropUsedToday: Int = 0
    private var supplyDropFallbacksToday: Int = 0
    private var reinforcementUsedThisBattle: Int = 0
    private var doublePayoutUsedThisBattle: Int = 0

    private fun rollDayIfNeeded() {
        val today = dayKey()
        if (today != currentDay) {
            currentDay = today
            supplyDropUsedToday = 0
            supplyDropFallbacksToday = 0
        }
    }

    override fun onBattleStarted() {
        reinforcementUsedThisBattle = 0
        doublePayoutUsedThisBattle = 0
    }

    override fun remaining(placement: RewardedPlacement): Int {
        rollDayIfNeeded()
        return when (placement) {
            RewardedPlacement.SUPPLY_DROP ->
                (AdPolicyConfig.SUPPLY_DROP_DAILY_LIMIT - supplyDropUsedToday).coerceAtLeast(0)
            RewardedPlacement.REINFORCEMENT ->
                (AdPolicyConfig.REINFORCEMENT_PER_BATTLE_LIMIT - reinforcementUsedThisBattle).coerceAtLeast(0)
            RewardedPlacement.DOUBLE_PAYOUT ->
                (AdPolicyConfig.DOUBLE_PAYOUT_PER_BATTLE_LIMIT - doublePayoutUsedThisBattle).coerceAtLeast(0)
        }
    }

    override fun isOffered(placement: RewardedPlacement): Boolean = remaining(placement) > 0

    override fun consume(placement: RewardedPlacement) {
        rollDayIfNeeded()
        when (placement) {
            RewardedPlacement.SUPPLY_DROP -> supplyDropUsedToday++
            RewardedPlacement.REINFORCEMENT -> reinforcementUsedThisBattle++
            RewardedPlacement.DOUBLE_PAYOUT -> doublePayoutUsedThisBattle++
        }
    }

    override fun noteFallback(placement: RewardedPlacement): Boolean {
        rollDayIfNeeded()
        return when (placement) {
            RewardedPlacement.SUPPLY_DROP -> {
                if (!AdPolicyConfig.ANTI_ARBITRAGE_FALLBACK_CAP_ENABLED) return true
                if (supplyDropFallbacksToday >= AdPolicyConfig.SUPPLY_DROP_FALLBACK_DAILY_CAP) {
                    false
                } else {
                    supplyDropFallbacksToday++
                    true
                }
            }
            // GDD §G.4: reklam gelmese de savas devam eder, ama bu savas icin
            // hak kullanilmis sayilir — sonsuz revive olmasin.
            RewardedPlacement.REINFORCEMENT -> {
                reinforcementUsedThisBattle++
                true
            }
            // Taban odul reklamdan ONCE zaten verildi ve kaydedildi; fallback
            // sadece "x2 olmadi" demek. Hak tuketilmez, tekrar denenebilir.
            RewardedPlacement.DOUBLE_PAYOUT -> true
        }
    }
}

/** Yerel gun anahtari (yil*1000 + gun). Saat dilimi degisimine dayanikli. */
private fun localDayKey(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}
