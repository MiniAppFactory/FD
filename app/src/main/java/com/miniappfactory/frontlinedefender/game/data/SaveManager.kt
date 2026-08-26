package com.miniappfactory.frontlinedefender.game.data

import android.content.Context
import android.content.SharedPreferences
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.Mission
import com.miniappfactory.frontlinedefender.game.economy.MissionState
import com.miniappfactory.frontlinedefender.game.economy.MetaUpgrades
import com.miniappfactory.frontlinedefender.game.economy.MissionPools
import com.miniappfactory.frontlinedefender.game.economy.PlayerWallet
import com.miniappfactory.frontlinedefender.game.economy.RequisitionState
import com.miniappfactory.frontlinedefender.game.economy.UpgradeLine
import com.miniappfactory.frontlinedefender.game.economy.WeeklyMission

/**
 * Kalici oyuncu durumu: yildizlar, ayarlar ve **Faz 9 ekonomisi** (cuzdan, kilit
 * durumu, meta yukseltmeler, gorev durumu).
 *
 * TASARIM KURALLARI
 * -----------------
 * 1. **Bozuk veya eksik kayitta COKME YOK.** Her okuma tip guvenli, aralik
 *    kirpmali ve savunmalidir; tanimlanamayan deger varsayilana doner. Kayit
 *    bozulmasi oyuncunun oynayamamasi anlamina GELMEZ.
 * 2. **Tek dogruluk kaynagi.** Bolum yildizlari yalnizca `level_stars_N`
 *    anahtarlarinda durur; [PlayerWallet.bestStars] bunlardan uretilir ve buraya
 *    geri yazilir. Motor ([setLevelStars]) ile ekonomi katmani ayni veriyi gorur.
 * 3. **Surumlu sema.** [EconomyConfig.SAVE_VERSION] ile korunur; [migrateIfNeeded]
 *    eski kayitlari ileri tasir, asla geriye kirmaz.
 * 4. **Android'e bagli degil.** Depolama [KeyValueStore] arkasindadir; birim
 *    testleri [InMemoryKeyValueStore] ile Robolectric OLMADAN kosar.
 * 5. Ekonomi SAYISI burada tanimlanmaz; hepsi [EconomyConfig]'ten okunur.
 */
class SaveManager internal constructor(private val store: KeyValueStore) {

    constructor(context: Context) : this(SharedPrefsStore(context))

    init {
        migrateIfNeeded()
        seedTutorialFlagForExistingSaves()
    }

    companion object {
        private const val KEY_SAVE_VERSION = "save_version"

        // ---- Faz 1-8 (surum 1) anahtarlari — DEGISTIRILMEDI --------------------
        private const val KEY_HIGH_SCORE = "high_score"
        private const val KEY_ENEMIES_KILLED = "enemies_killed"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_LEVEL_STARS_PREFIX = "level_stars_"

        // ---- Faz 9 (surum 2) — ekonomi ----------------------------------------
        private const val KEY_COINS = "eco_coins"
        private const val KEY_UNLOCKED = "eco_unlocked_levels"
        private const val KEY_CLEARED = "eco_cleared_levels"
        private const val KEY_PERFECT = "eco_perfect_levels"
        private const val KEY_LIFETIME_EARNED = "eco_lifetime_earned"
        private const val KEY_LIFETIME_SPENT = "eco_lifetime_spent"

        // Elit Sevk + Prestij (2026-08-26, ECONOMY_ANALYSIS C+B)
        private const val KEY_ELITE_CLEARS = "eco_elite_clears"
        private const val KEY_PRESTIGE_RANK = "eco_prestige_rank"

        private const val KEY_UPG_FIREPOWER = "eco_upg_firepower"
        private const val KEY_UPG_OPTICS = "eco_upg_optics"
        private const val KEY_UPG_SUPPLY = "eco_upg_supply"
        private const val KEY_UPG_FORTIFICATION = "eco_upg_fortification"
        private const val KEY_UPG_SALVAGE = "eco_upg_salvage"

        private const val KEY_MISSION_SEED = "eco_mission_seed"
        private const val KEY_LAST_RESET_DAY = "eco_last_reset_day"
        private const val KEY_LAST_WEEKLY_WEEK = "eco_last_weekly_week"
        private const val KEY_LAST_SEEN_WALL = "eco_last_seen_wall_ms"
        private const val KEY_LAST_SEEN_ELAPSED = "eco_last_seen_elapsed_ms"
        private const val KEY_LAST_BOOT_ID = "eco_last_boot_id"
        private const val KEY_ACCUM_REALTIME = "eco_accum_realtime_ms"
        private const val KEY_WINDOW_START = "eco_reset_window_start_ms"
        private const val KEY_RESETS_IN_WINDOW = "eco_resets_in_window"
        private const val KEY_CLOCK_SUSPECT = "eco_clock_suspect"
        private const val KEY_REWARD_MULTIPLIER_PCT = "eco_reward_multiplier_pct"
        private const val KEY_REROLLS_USED = "eco_rerolls_used_today"
        private const val KEY_DAILY_PROGRESS = "eco_daily_progress"
        private const val KEY_WEEKLY_PROGRESS = "eco_weekly_progress"

        // ---- Ilk oturum onboarding (game/ui/TutorialOverlay.kt) ---------------
        private const val KEY_TUTORIAL_STATUS = "tutorial_status"
        /** Reklam politikasi: kurulumdan beri tamamlanan savas sayisi. */
        private const val KEY_LIFETIME_BATTLES = "ads_lifetime_battles"

        /** [tutorialStatus] alfabesi. Aralik disi deger [TUTORIAL_UNSEEN]e kirpilir. */
        const val TUTORIAL_UNSEEN = 0
        const val TUTORIAL_COMPLETED = 1
        const val TUTORIAL_SKIPPED = 2

        /** Ogreticinin kostugu tek bolum. */
        private const val TUTORIAL_LEVEL = 1

        // ---- Kilit acilma ipuclari (game/ui/TutorialOverlay.kt, UnlockHint) ----
        //
        // Her ipucu KENDI anahtarini tasir: `hint_seen_<id>`. Tek bir bit
        // maskesi kullanilmadi cunku ipucu listesi buyuyecek ve maskede sira
        // degistirmek eski kayitlarda YANLIS ipucunu "gorulmus" isaretlerdi.
        // Ayrik anahtarlar sirasizdir; silinen bir ipucunun anahtari yalnizca
        // olu veri birakir, yanlis davranis uretmez.
        private const val KEY_HINT_PREFIX = "hint_seen_"

        private const val KEY_R1_FILLED_VIEWS = "eco_r1_filled_views_today"
        private const val KEY_R1_COINS_PAID = "eco_r1_coins_paid_today"
        private const val KEY_BOOSTED_REPLAYS = "eco_boosted_replays_today"

        /** Ayarlar ilerleme sifirlamasinda KORUNUR (GDD H.1). */
        private val PRESERVED_ON_RESET = setOf(KEY_SOUND_ENABLED, KEY_SAVE_VERSION)
    }

    // =================================================================================
    // Surum / migrasyon
    // =================================================================================

    val saveVersion: Int
        get() = store.getInt(KEY_SAVE_VERSION, 0).coerceIn(0, EconomyConfig.SAVE_VERSION)

    /**
     * Eski kayitlari mevcut semaya tasir. Ileri surumlu bir kayit gorulurse
     * (kullanici downgrade etti) HICBIR SEY yapilmaz — veri silmek yerine
     * bilinmeyen alanlar yok sayilir.
     */
    private fun migrateIfNeeded() {
        val current = store.getInt(KEY_SAVE_VERSION, 0)
        if (current >= EconomyConfig.SAVE_VERSION) {
            if (current == 0) store.putInt(KEY_SAVE_VERSION, EconomyConfig.SAVE_VERSION)
            return
        }

        if (current < 2) {
            // Surum 1 -> 2: ekonomi alanlari yoktu. Yildizlar korunur; kilit durumu
            // yildiz zincirinden turetilir ki mevcut debug kayitlari bolum kaybetmesin.
            //
            // **Coin hibe EDILMEZ.** Gerek de yok: turetilen zincirin son halkasi
            // tanim geregi henuz TEMIZLENMEMIS bolumdur, yani siradaki kilit
            // oynanamaz durumdadir ve GDD C.4 katman 4 invariant'i baglayici degildir.
            // O bolum temizlendiginde R(L) >= 2,1 x D(L+1) bakiyeyi kendisi kapatir.
            val stars = readStarsMap()
            if (!store.hasKey(KEY_UNLOCKED)) {
                store.putString(KEY_UNLOCKED, encodeLevels(derivedUnlockedFromStars(stars)))
                store.putString(KEY_CLEARED, encodeLevels(stars.filterValues { it > 0 }.keys))
            }
        }

        store.putInt(KEY_SAVE_VERSION, EconomyConfig.SAVE_VERSION)
    }

    /** Bolum 1-6 bedava; sonrasi onceki bolumden en az 1 yildiz alinmissa acik sayilir. */
    private fun derivedUnlockedFromStars(stars: Map<Int, Int>): Set<Int> {
        val result = (1 until EconomyConfig.FIRST_PAID_LEVEL).toMutableSet()
        for (level in EconomyConfig.FIRST_PAID_LEVEL..EconomyConfig.CAMPAIGN_LEVELS) {
            if ((stars[level - 1] ?: 0) >= 1) result += level else break
        }
        return result
    }

    // =================================================================================
    // Faz 1-8 API'si — motor ve UI bunlari cagiriyor, imzalar KORUNDU
    // =================================================================================

    var highScore: Int
        get() = store.getInt(KEY_HIGH_SCORE, 0).coerceAtLeast(0)
        set(value) {
            if (value > highScore) store.putInt(KEY_HIGH_SCORE, value)
        }

    var totalEnemiesKilled: Int
        get() = store.getInt(KEY_ENEMIES_KILLED, 0).coerceAtLeast(0)
        set(value) {
            store.putInt(KEY_ENEMIES_KILLED, value.coerceAtLeast(0))
        }

    fun addEnemiesKilled(count: Int) {
        totalEnemiesKilled += count.coerceAtLeast(0)
    }

    var soundEnabled: Boolean
        get() = store.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) {
            store.putBoolean(KEY_SOUND_ENABLED, value)
        }

    /** 0..3 arasina kirpilir; cop deger 0 olur. */
    fun getLevelStars(levelId: Int): Int =
        store.getInt("$KEY_LEVEL_STARS_PREFIX$levelId", 0).coerceIn(0, 3)

    /** Yildiz **asla dusmez** (GDD H.2). */
    fun setLevelStars(levelId: Int, stars: Int) {
        val clamped = stars.coerceIn(0, 3)
        if (clamped > getLevelStars(levelId)) {
            store.putInt("$KEY_LEVEL_STARS_PREFIX$levelId", clamped)
        }
    }

    // =================================================================================
    // Faz 9 — cuzdan
    // =================================================================================

    private fun readStarsMap(): Map<Int, Int> =
        (1..EconomyConfig.CAMPAIGN_LEVELS)
            .associateWith { getLevelStars(it) }
            .filterValues { it > 0 }

    /**
     * Cuzdani okur. Her alan kirpilir: negatif coin 0 olur, kampanya disi bolum
     * numaralari atilir, yildizlar 0..3'e sikistirilir. Boylece [PlayerWallet]'in
     * `require` kontrolleri **asla** bozuk kayit yuzunden patlamaz.
     */
    fun loadWallet(): PlayerWallet {
        val stars = readStarsMap()
        val cleared = decodeLevels(store.getString(KEY_CLEARED, "")).toMutableSet()
        // Yildizi olan bolum tanim geregi temizlenmistir; tutarsizligi sessizce onar.
        cleared += stars.keys

        val unlocked = decodeLevels(store.getString(KEY_UNLOCKED, "")).toMutableSet()
        unlocked += (1 until EconomyConfig.FIRST_PAID_LEVEL) // 1-6 her zaman acik
        unlocked += cleared                                  // oynanmis bolum kilitli olamaz

        return PlayerWallet(
            coins = store.getInt(KEY_COINS, 0).coerceAtLeast(0),
            unlockedLevels = unlocked,
            clearedLevels = cleared,
            bestStars = stars,
            perfectDefenseLevels = decodeLevels(store.getString(KEY_PERFECT, "")),
            lifetimeEarned = store.getInt(KEY_LIFETIME_EARNED, 0).coerceAtLeast(0),
            lifetimeSpent = store.getInt(KEY_LIFETIME_SPENT, 0).coerceAtLeast(0),
        )
    }

    fun saveWallet(wallet: PlayerWallet) {
        store.putInt(KEY_COINS, wallet.coins.coerceAtLeast(0))
        store.putString(KEY_UNLOCKED, encodeLevels(wallet.unlockedLevels))
        store.putString(KEY_CLEARED, encodeLevels(wallet.clearedLevels))
        store.putString(KEY_PERFECT, encodeLevels(wallet.perfectDefenseLevels))
        store.putInt(KEY_LIFETIME_EARNED, wallet.lifetimeEarned.coerceAtLeast(0))
        store.putInt(KEY_LIFETIME_SPENT, wallet.lifetimeSpent.coerceAtLeast(0))
        // Yildizlar tek kaynakta: motorun da yazdigi anahtarlar.
        wallet.bestStars.forEach { (level, stars) -> setLevelStars(level, stars) }
    }

    // =================================================================================
    // Faz 9 — meta yukseltmeler
    // =================================================================================

    fun loadMetaUpgrades(): MetaUpgrades = MetaUpgrades(
        firepower = rank(KEY_UPG_FIREPOWER, UpgradeLine.FIREPOWER),
        optics = rank(KEY_UPG_OPTICS, UpgradeLine.OPTICS),
        startingSupplyRank = rank(KEY_UPG_SUPPLY, UpgradeLine.STARTING_SUPPLY),
        fortification = rank(KEY_UPG_FORTIFICATION, UpgradeLine.FORTIFICATION),
        salvage = rank(KEY_UPG_SALVAGE, UpgradeLine.SALVAGE),
    )

    private fun rank(key: String, line: UpgradeLine): Int =
        store.getInt(key, 0).coerceIn(0, line.maxRank)

    fun saveMetaUpgrades(upgrades: MetaUpgrades) {
        store.putInt(KEY_UPG_FIREPOWER, upgrades.firepower)
        store.putInt(KEY_UPG_OPTICS, upgrades.optics)
        store.putInt(KEY_UPG_SUPPLY, upgrades.startingSupplyRank)
        store.putInt(KEY_UPG_FORTIFICATION, upgrades.fortification)
        store.putInt(KEY_UPG_SALVAGE, upgrades.salvage)
    }

    // =================================================================================
    // Faz 9 — gorev durumu
    // =================================================================================

    /**
     * Gorev secim tohumu. Kurulumda BIR KEZ uretilir ve bir daha degismez; boylece
     * ayni cihazda ayni gun her zaman ayni 3 gorev uretilir (determinizm).
     * Uretim `hashCode` tabanlidir, kriptografik degildir — gerek de yok.
     */
    val missionSeed: Long
        get() {
            val existing = store.getLong(KEY_MISSION_SEED, 0L)
            if (existing != 0L) return existing
            // 0x9E3779B97F4A7C15 (altin oran) -> imzali Long karsiligi.
            val generated = (System.nanoTime() * -0x61c8864680b583ebL) or 1L
            store.putLong(KEY_MISSION_SEED, generated)
            return generated
        }

    fun loadMissionState(): MissionState {
        val weekly = MissionPools.weekly()
        return MissionState(
            lastResetEpochDay = store.getLong(KEY_LAST_RESET_DAY, Long.MIN_VALUE),
            lastWeeklyResetWeek = store.getLong(KEY_LAST_WEEKLY_WEEK, Long.MIN_VALUE),
            lastSeenWallClockMs = store.getLong(KEY_LAST_SEEN_WALL, 0L).coerceAtLeast(0L),
            lastSeenElapsedRealtimeMs = store.getLong(KEY_LAST_SEEN_ELAPSED, 0L).coerceAtLeast(0L),
            lastBootId = store.getLong(KEY_LAST_BOOT_ID, 0L),
            accumulatedRealtimeMs = store.getLong(KEY_ACCUM_REALTIME, 0L).coerceAtLeast(0L),
            resetWindowStartRealtimeMs = store.getLong(KEY_WINDOW_START, 0L).coerceAtLeast(0L),
            dailyResetsInWindow = store.getInt(KEY_RESETS_IN_WINDOW, 0).coerceAtLeast(0),
            clockSuspect = store.getBoolean(KEY_CLOCK_SUSPECT, false),
            // Carpan yuzde olarak saklanir (Double serialize etmemek icin). 1..100.
            rewardMultiplier = store.getInt(KEY_REWARD_MULTIPLIER_PCT, 100)
                .coerceIn(1, 100) / 100.0,
            rerollsUsedToday = store.getInt(KEY_REROLLS_USED, 0)
                .coerceIn(0, EconomyConfig.DAILY_REROLLS_PER_DAY),
            daily = emptyList(), // gorev listesi deterministik uretilir, saklanmaz
            weekly = applyWeeklyProgress(weekly, store.getString(KEY_WEEKLY_PROGRESS, "")),
        )
    }

    fun saveMissionState(state: MissionState) {
        store.putLong(KEY_LAST_RESET_DAY, state.lastResetEpochDay)
        store.putLong(KEY_LAST_WEEKLY_WEEK, state.lastWeeklyResetWeek)
        store.putLong(KEY_LAST_SEEN_WALL, state.lastSeenWallClockMs)
        store.putLong(KEY_LAST_SEEN_ELAPSED, state.lastSeenElapsedRealtimeMs)
        store.putLong(KEY_LAST_BOOT_ID, state.lastBootId)
        store.putLong(KEY_ACCUM_REALTIME, state.accumulatedRealtimeMs)
        store.putLong(KEY_WINDOW_START, state.resetWindowStartRealtimeMs)
        store.putInt(KEY_RESETS_IN_WINDOW, state.dailyResetsInWindow)
        store.putBoolean(KEY_CLOCK_SUSPECT, state.clockSuspect)
        store.putInt(KEY_REWARD_MULTIPLIER_PCT, (state.rewardMultiplier * 100).toInt().coerceIn(1, 100))
        store.putInt(KEY_REROLLS_USED, state.rerollsUsedToday)
        store.putString(KEY_WEEKLY_PROGRESS, encodeWeeklyProgress(state.weekly))
    }

    /** Gunluk gorev ILERLEMESI (gorev kimlikleri deterministik uretildigi icin yalnizca sayaclar). */
    fun loadDailyProgress(): Map<String, Int> = decodeProgress(store.getString(KEY_DAILY_PROGRESS, ""))

    fun saveDailyProgress(missions: List<Mission>) {
        store.putString(
            KEY_DAILY_PROGRESS,
            missions.joinToString(",") { "${it.id}:${it.progress}:${if (it.claimed) 1 else 0}" }
        )
    }

    fun loadDailyClaims(): Set<String> = decodeClaims(store.getString(KEY_DAILY_PROGRESS, ""))

    // =================================================================================
    // Faz 9 — gunluk reklam / tekrar sayaclari
    // =================================================================================

    fun loadRequisitionState(): RequisitionState = RequisitionState(
        filledViewsToday = store.getInt(KEY_R1_FILLED_VIEWS, 0)
            .coerceIn(0, EconomyConfig.R1_VIEWS_PER_DAY),
        coinsPaidToday = store.getInt(KEY_R1_COINS_PAID, 0)
            .coerceIn(0, EconomyConfig.R1_COIN_BUDGET_PER_DAY),
    )

    fun saveRequisitionState(state: RequisitionState) {
        store.putInt(KEY_R1_FILLED_VIEWS, state.filledViewsToday)
        store.putInt(KEY_R1_COINS_PAID, state.coinsPaidToday)
    }

    /**
     * Bolum basina ELIT zafer sayisi ("7:2,12:1" bicimi — gorev ilerlemesiyle
     * ayni encode dili). Yildizdan AYRI tutulur: elit yildiz vermez/dusurmez.
     */
    var eliteClears: Map<Int, Int>
        get() = store.getString(KEY_ELITE_CLEARS, "")
            .split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                val level = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val count = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                if (level > 0 && count > 0) level to count else null
            }
            .toMap()
        set(value) {
            store.putString(
                KEY_ELITE_CLEARS,
                value.entries
                    .filter { it.value > 0 }
                    .sortedBy { it.key }
                    .joinToString(",") { "${it.key}:${it.value}" },
            )
        }

    /** Prestij nisani kademesi (0..PRESTIGE_MAX). Tamamen kozmetik. */
    var prestigeRank: Int
        get() = store.getInt(KEY_PRESTIGE_RANK, 0)
            .coerceIn(0, EconomyConfig.PRESTIGE_MAX)
        set(value) {
            store.putInt(KEY_PRESTIGE_RANK, value.coerceIn(0, EconomyConfig.PRESTIGE_MAX))
        }

    var boostedReplaysUsedToday: Int
        get() = store.getInt(KEY_BOOSTED_REPLAYS, 0).coerceAtLeast(0)
        set(value) {
            store.putInt(KEY_BOOSTED_REPLAYS, value.coerceAtLeast(0))
        }

    /** Gun donusunde sifirlanan sayaclar. Bolum/coin/yukseltme ilerlemesi ETKILENMEZ. */
    fun resetDailyCounters() {
        store.putInt(KEY_R1_FILLED_VIEWS, 0)
        store.putInt(KEY_R1_COINS_PAID, 0)
        store.putInt(KEY_BOOSTED_REPLAYS, 0)
        store.putInt(KEY_REROLLS_USED, 0)
        store.putString(KEY_DAILY_PROGRESS, "")
    }

    fun resetWeeklyCounters() {
        store.putString(KEY_WEEKLY_PROGRESS, "")
    }

    // =================================================================================
    // Ilk oturum onboarding — TutorialOverlay
    // =================================================================================

    /**
     * Ogreticinin durumu: [TUTORIAL_UNSEEN] / [TUTORIAL_COMPLETED] / [TUTORIAL_SKIPPED].
     *
     * Diger alanlarla ayni savunma: cop veya aralik disi deger sessizce
     * kirpilir, bozuk kayit oyuncuyu sonsuz ogreticiye HAPSETMEZ ve okuma
     * istisna atmaz.
     *
     * "Atlandi" ile "tamamlandi" ayri tutuluyor cunku ikisi ayni sey DEGIL:
     * ikisi de ogreticiyi bir daha gostermez ama ilk oturum tamamlama oranini
     * (PRD KPI) yalnizca [TUTORIAL_COMPLETED] besler.
     */
    var tutorialStatus: Int
        get() = store.getInt(KEY_TUTORIAL_STATUS, TUTORIAL_UNSEEN)
            .coerceIn(TUTORIAL_UNSEEN, TUTORIAL_SKIPPED)
        set(value) {
            store.putInt(
                KEY_TUTORIAL_STATUS,
                value.coerceIn(TUTORIAL_UNSEEN, TUTORIAL_SKIPPED)
            )
        }

    /** Ogretici bir daha gosterilmeli mi? Tek karar noktasi. */
    val tutorialSeen: Boolean
        get() = tutorialStatus != TUTORIAL_UNSEEN

    fun markTutorialCompleted() {
        tutorialStatus = TUTORIAL_COMPLETED
    }

    fun markTutorialSkipped() {
        tutorialStatus = TUTORIAL_SKIPPED
    }

    /**
     * Ogreticiyi TEKRAR gosterilebilir yapar (test / QA / cihaz dogrulamasi).
     *
     * Anahtari SILMEZ, acikca [TUTORIAL_UNSEEN] yazar. Silseydi bir sonraki
     * [SaveManager] kurulumunda [seedTutorialFlagForExistingSaves] bolum 1
     * yildizini gorup bayragi yeniden "tamamlandi"ya cevirirdi ve sifirlama
     * hicbir sey yapmamis gibi gorunurdu.
     */
    fun resetTutorial() {
        store.putInt(KEY_TUTORIAL_STATUS, TUTORIAL_UNSEEN)
    }

    // =================================================================================
    // Reklam politikasi sayaci (GDD §G.2/1)
    // =================================================================================

    /**
     * Kurulumdan bu yana SONUNA KADAR oynanmis savas sayisi.
     *
     * ⚠ 2026-08-22 — BU ALAN KALICI DEGILDI ve bu gercek bir hataydi.
     *
     * `AdProgressStore`in KDoc'u *"Kaliciligi bu katman yapmaz —
     * DataStore/SaveManager tarafina Faz 6'da baglanir"* diyordu. Faz 6 geldi
     * gecti, baglanmadi: uretimde `AdMobAdHost` varsayilan
     * `InMemoryAdProgressStore`u kullaniyordu ve o sinifin kendi yorumu
     * *"surec olumunde sifirlanir"* diyordu.
     *
     * Sonuc: sayac HER UYGULAMA ACILISINDA 0'a donuyordu, dolayisiyla
     *   - `ONBOARDING_FREE_BATTLES = 3` (ilk uc savas reklamsiz) her oturumda
     *     bastan uygulaniyordu,
     *   - `isFirstSession` hep `true` oldugu icin `FIRST_SESSION_WARMUP_MS`
     *     (3 dakika) da her oturumda uygulaniyordu.
     * Yani "yeni oyuncu muafiyeti" HIC BITMIYORDU. Cihaz raporu: *"level 1
     * bitirdim, next level dedigimde reklam gelmedi."*
     *
     * Bu deponun 4 numarali tekrar eden hatasi: **kod var, cagiran yok.**
     *
     * `resetProgress` bunu da siler (PRESERVED_ON_RESET disinda) — dogru
     * davranis: ilerlemesini sifirlayan oyuncu muafiyeti yeniden hak eder.
     */
    var lifetimeBattlesCompleted: Int
        get() = store.getInt(KEY_LIFETIME_BATTLES, 0).coerceAtLeast(0)
        set(value) = store.putInt(KEY_LIFETIME_BATTLES, value.coerceAtLeast(0))

    /**
     * Bayrak eklenmeden ONCE olusmus kayitlar icin bir kerelik tohumlama.
     *
     * Bolum 1 yildizini zaten almis bir oyuncu oyunun nasil oynandigini
     * ogrenmistir; ona "mevziye dokun" demek gerileme olurdu. Yalnizca anahtar
     * HIC YOKKEN calisir, yani [resetTutorial] ile bilincli sifirlamayi geri
     * almaz ve temiz kurulumda hicbir sey yazmaz.
     */
    private fun seedTutorialFlagForExistingSaves() {
        if (store.hasKey(KEY_TUTORIAL_STATUS)) return
        if (getLevelStars(TUTORIAL_LEVEL) > 0) {
            store.putInt(KEY_TUTORIAL_STATUS, TUTORIAL_COMPLETED)
        }
    }

    // =================================================================================
    // Kilit acilma ipuclari — game/ui/TutorialOverlay.kt (`UnlockHint`)
    // =================================================================================
    //
    // TOHUMLAMA YOK — ogreticiden bilincli fark.
    //
    // Ogretici icin `seedTutorialFlagForExistingSaves` var: bolum 1'i gecmis
    // oyuncu dongunun nasil isledigini ZATEN biliyor, ona "mevziye dokun"
    // demek gerileme olurdu. Ipuclarinda tersi dogru: FUN_AUDIT'in merkezi
    // tespiti, bolum 20'deki oyuncunun da kule ROLLERINI bilmedigidir (dersi
    // veren hicbir sey yoktu). Dolayisiyla mevcut kayitlar da ipuclarini
    // GORUR; ipuclari oncelik sirasina gore geldigi icin ilerlemis oyuncu
    // once en degerli olani (zirh deleme) alir.

    /**
     * Bu ipucu daha once GOSTERILDI mi?
     *
     * [hintId] `game/ui` katmanindan gelen KARARLI bir metin kimliktir; enum
     * TIPI bilincli olarak buraya sizmiyor — `data` katmani `ui` katmanina
     * bagimli olamaz.
     *
     * Bos/bosluk kimlik "gosterildi" (`true`) doner: cagiran taraftaki bir hata
     * en kotu ihtimalle bir ipucunu SUSTURUR, oyuncuyu her hazirlik fazinda
     * ayni seridi gormeye MAHKUM ETMEZ.
     */
    fun isHintSeen(hintId: String): Boolean {
        val key = hintKey(hintId) ?: return true
        return store.getBoolean(key, false)
    }

    /** Ipucu bir daha gosterilmez. Ayni kimligi iki kez yazmak zararsizdir. */
    fun markHintSeen(hintId: String) {
        val key = hintKey(hintId) ?: return
        store.putBoolean(key, true)
    }

    /**
     * TUM ipuclarini yeniden gosterilebilir yapar (Ayarlar > "Ipuclarini
     * sifirla", QA, cihaz dogrulamasi).
     *
     * Anahtarlari SILER — [resetTutorial]'in aksine bir deger yazmaz. Yapabilir,
     * cunku ipuclarinin tohumlamasi yok (yukaridaki nota bak): silinen anahtar
     * bir sonraki [SaveManager] kurulumunda geri gelmez, yani "sifirla"
     * gercekten sifirlar.
     *
     * Onek taramasi ipucu listesini BILMEDEN calisir: yeni ipucu eklendiginde
     * burasi degismez.
     */
    fun resetHints() {
        store.keys().filter { it.startsWith(KEY_HINT_PREFIX) }.forEach { store.remove(it) }
    }

    /** Gecersiz kimlik icin `null`. Kimlik normalizasyonu YALNIZCA burada. */
    private fun hintKey(hintId: String): String? {
        val trimmed = hintId.trim()
        return if (trimmed.isEmpty()) null else "$KEY_HINT_PREFIX$trimmed"
    }

    // =================================================================================
    // Ilerlemeyi sifirla (GDD H.1 — cift onayli, Ayarlar'dan)
    // =================================================================================

    /** Tum ilerleme silinir, **ayarlar (ses/dil) korunur**. */
    fun resetProgress() {
        store.keys().filterNot { it in PRESERVED_ON_RESET }.forEach { store.remove(it) }
        store.putInt(KEY_SAVE_VERSION, EconomyConfig.SAVE_VERSION)
        store.remove(KEY_ELITE_CLEARS)
        store.remove(KEY_PRESTIGE_RANK)
    }

    // =================================================================================
    // Kodlama / kod cozme — hepsi COP DEGERE TOLERANSLI
    // =================================================================================

    private fun encodeLevels(levels: Set<Int>): String =
        levels.filter { it in 1..EconomyConfig.CAMPAIGN_LEVELS }.sorted().joinToString(",")

    /** "1,2,7" -> {1,2,7}. Sayi olmayan, aralik disi ve tekrarli token'lar ATILIR. */
    private fun decodeLevels(raw: String): Set<Int> =
        raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..EconomyConfig.CAMPAIGN_LEVELS }
            .toSet()

    /** "id:progress:claimed,..." -> id -> progress. Bozuk kayit sessizce atlanir. */
    private fun decodeProgress(raw: String): Map<String, Int> =
        raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size < 2) return@mapNotNull null
            val id = parts[0].trim()
            val progress = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
            if (id.isEmpty() || progress < 0) null else id to progress
        }.toMap()

    private fun decodeClaims(raw: String): Set<String> =
        raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size < 3) return@mapNotNull null
            if (parts[2].trim() == "1") parts[0].trim().ifEmpty { null } else null
        }.toSet()

    private fun encodeWeeklyProgress(missions: List<WeeklyMission>): String =
        missions.joinToString(",") { "${it.id}:${it.progress}:${if (it.claimed) 1 else 0}" }

    private fun applyWeeklyProgress(missions: List<WeeklyMission>, raw: String): List<WeeklyMission> {
        if (raw.isBlank()) return missions
        val progress = decodeProgress(raw)
        val claims = decodeClaims(raw)
        return missions.map { mission ->
            mission.copy(
                progress = (progress[mission.id] ?: 0).coerceIn(0, mission.target),
                claimed = mission.id in claims,
            )
        }
    }
}

// =====================================================================================
// Depolama soyutlamasi
// =====================================================================================

/**
 * Anahtar/deger deposu. Amac tek: [SaveManager]'in ayristirma ve kirpma
 * mantiginin **Android olmadan** test edilebilmesi. Tum okumalar tip guvenli
 * olmali; yanlis tipte saklanmis deger `default` dondurur, ISTISNA ATMAZ.
 */
interface KeyValueStore {
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getString(key: String, default: String): String
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String)
    fun hasKey(key: String): Boolean
    fun remove(key: String)
    fun keys(): Set<String>
}

/**
 * Uretim implementasyonu. SharedPreferences yanlis tipte bir deger icin
 * `ClassCastException` atar (ornek: `Int` beklenen yere `String` yazilmis eski
 * bir kayit) — bu yuzden her okuma yakalanir ve varsayilana duser.
 */
internal class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("frontline_defender_prefs", Context.MODE_PRIVATE)

    override fun getInt(key: String, default: Int): Int =
        try { prefs.getInt(key, default) } catch (_: ClassCastException) { default }

    override fun getLong(key: String, default: Long): Long =
        try { prefs.getLong(key, default) } catch (_: ClassCastException) { default }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        try { prefs.getBoolean(key, default) } catch (_: ClassCastException) { default }

    override fun getString(key: String, default: String): String =
        try { prefs.getString(key, default) ?: default } catch (_: ClassCastException) { default }

    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun hasKey(key: String): Boolean = prefs.contains(key)
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun keys(): Set<String> = prefs.all.keys.toSet()
}

/**
 * Test/ongorunum implementasyonu. Bilincli olarak **cop veri kabul eder**
 * ([putRaw]) ki "bozuk kayitta cokme yok" kurali gercekten dogrulanabilsin.
 */
class InMemoryKeyValueStore(
    private val map: MutableMap<String, Any> = mutableMapOf()
) : KeyValueStore {

    /** Yanlis tipte / cop deger yazar — bozuk kayit simulasyonu. */
    fun putRaw(key: String, value: Any) {
        map[key] = value
    }

    override fun getInt(key: String, default: Int): Int = map[key] as? Int ?: default
    override fun getLong(key: String, default: Long): Long = map[key] as? Long ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean = map[key] as? Boolean ?: default
    override fun getString(key: String, default: String): String = map[key] as? String ?: default
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun putString(key: String, value: String) { map[key] = value }
    override fun hasKey(key: String): Boolean = map.containsKey(key)
    override fun remove(key: String) { map.remove(key) }
    override fun keys(): Set<String> = map.keys.toSet()
}
