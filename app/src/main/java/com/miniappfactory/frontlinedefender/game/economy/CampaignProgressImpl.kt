package com.miniappfactory.frontlinedefender.game.economy

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.ui.CampaignProgress
import java.util.TimeZone

/**
 * Faz 9 — `CampaignProgress` sozlesmesinin KALICI implementasyonu.
 *
 * `game/ui/LevelSelectScreen.kt` icindeki [CampaignProgress] arayuzu baska bir ajan
 * tarafindan yazildi ve DEGISTIRILMEDI. Bu sinif onun gerceklesmesidir:
 * her mutasyon [SaveManager]'a yazilir, okumalar Compose snapshot state'i uzerinden
 * gider (yani zaferden sonra ekran kendini yeniler).
 *
 * BAGLAMA (tek satir, `game/ui/GameScreen.kt` sahibi ajanin isi):
 * ```
 * val campaignProgress = remember(saveManager) { CampaignProgressImpl(saveManager) }
 * ```
 * `InMemoryCampaignProgress` gecici iskeleydi; kaldirilmasi bu sinifi baglayan
 * ajanin sorumlulugundadir (bkz. docs/ECONOMY_SPEC.md 8).
 *
 * ROL DAGILIMI
 * ------------
 * - Karar/matematik: `PlayerProgress.kt` + `MissionSystem.kt` (saf, testli).
 * - Kalicilik: `SaveManager`.
 * - Bu sinif yalnizca **koordinasyon** yapar; kendi ekonomi kurali TANIMLAMAZ.
 */
class CampaignProgressImpl(
    private val saveManager: SaveManager,
    private val clock: ClockProvider = AndroidClockProvider(),
    /** Analytics kancasi (GDD H.9). Baglanmadigi surece sessizdir. */
    private val analytics: (event: String, params: Map<String, Any>) -> Unit = { _, _ -> },
) : CampaignProgress {

    private var wallet by mutableStateOf(saveManager.loadWallet())
    private var upgrades by mutableStateOf(saveManager.loadMetaUpgrades())
    private var missionState by mutableStateOf(saveManager.loadMissionState())
    private var requisition by mutableStateOf(saveManager.loadRequisitionState())
    private var boostedReplaysToday by mutableStateOf(saveManager.boostedReplaysUsedToday)
    private var daily by mutableStateOf<List<Mission>>(emptyList())

    init {
        // Uygulama acilisinda: gun/hafta donusu + soft-lock invariant'i (GDD C.4/4).
        refreshCalendar()
        enforceUnlockInvariant()
    }

    // =================================================================================
    // CampaignProgress sozlesmesi
    // =================================================================================

    override fun starsFor(levelId: Int): Int = wallet.starsOf(levelId)

    override fun isUnlocked(levelId: Int): Boolean = levelId in wallet.unlockedLevels

    override val coins: Int get() = wallet.coins

    /**
     * Konuslanma bedelini oder ve bolumu **kalici olarak** acar.
     * Reddedilirse HICBIR sey degismez (GDD C.1 kural 2: kilit tek seferlik).
     */
    override fun tryUnlock(levelId: Int): Boolean {
        if (!canUnlock(wallet, levelId)) return false
        val cost = lockCost(levelId)
        commitWallet(applyUnlock(wallet, levelId))
        analytics("level_unlock", mapOf("level" to levelId, "cost" to cost, "balance_after" to wallet.coins))
        return true
    }

    // =================================================================================
    // Dukkan (Konuslanma Rezervi)
    // =================================================================================

    val metaUpgrades: MetaUpgrades get() = upgrades

    /** Siradaki kilit icin ayrilan coin; dukkan basliginda surekli gorunur. */
    val reserve: Int get() = reserveFor(wallet)

    /** Harcanabilir bakiye = bakiye - rezerv. Fiyat etiketleri buna gore aktif/pasif. */
    val spendable: Int get() = spendableBalance(wallet)

    /** Butonun cizim durumu. UI kendi bakiye karsilastirmasini YAPMAZ. */
    fun purchaseDecision(line: UpgradeLine): PurchaseDecision =
        purchaseAllowed(wallet, upgrades, line)

    /** Yukseltmeyi satin almayi dener. Rezerv ihlalinde bakiye ve rank degismez. */
    fun buyUpgrade(line: UpgradeLine): PurchaseDecision {
        val decision = purchaseDecision(line)
        when (decision) {
            is PurchaseDecision.Allowed -> {
                val (w, u) = applyPurchase(wallet, upgrades, line)
                commitWallet(w)
                upgrades = u
                saveManager.saveMetaUpgrades(u)
                analytics(
                    "upgrade_purchase",
                    mapOf("line" to line.name, "rank" to u.rankOf(line), "cost" to decision.price)
                )
            }
            is PurchaseDecision.ReserveLocked -> analytics(
                "reserve_lock_blocked",
                mapOf("level" to (firstLockedLevel(wallet.unlockedLevels) ?: 0), "shortfall" to decision.shortfall)
            )
            else -> Unit
        }
        return decision
    }

    // =================================================================================
    // Savas sonucu
    // =================================================================================

    /**
     * Zaferi kaydeder ve coin oder. **Reklamdan ONCE cagrilir ve hemen yazilir**
     * (GDD G.4 R3 kurali: taban odul reklamin on kosulu degildir).
     *
     * @return odul dokumu; UI kalem kalem gosterir, R3 icin
     *   [LevelClearResult.doublableAmount] kullanilir.
     */
    fun onLevelCleared(levelId: Int, livesLeft: Int, maxLives: Int): LevelClearResult {
        val result = resolveLevelClear(wallet, levelId, livesLeft, maxLives, boostedReplaysToday)
        commitWallet(applyLevelClear(wallet, result))
        if (result.consumesBoostedReplay) {
            boostedReplaysToday += 1
            saveManager.boostedReplaysUsedToday = boostedReplaysToday
        }
        advanceDaily(MissionType.COMPLETE_ANY_LEVEL, 1)
        if (result.stars >= 3) advanceDaily(MissionType.CLEAR_WITH_THREE_STARS, 1)
        if (livesLeft.toDouble() / maxLives >= EconomyConfig.STAR3_HEALTH_RATIO) {
            advanceDaily(MissionType.CLEAR_WITH_HIGH_HEALTH, 1)
        }
        advanceWeeklyCounter(MissionType.WEEKLY_LEVELS_COMPLETED, 1)
        if (result.firstClear) advanceWeeklyCounter(MissionType.WEEKLY_STARS_EARNED, result.stars)

        analytics(
            "level_complete",
            mapOf(
                "level" to levelId,
                "stars" to result.stars,
                "lives_pct" to (100 * livesLeft / maxOf(1, maxLives)),
                "perfect" to (result.perfectBonus > 0),
                "coins" to result.total,
            )
        )
        enforceUnlockInvariant()
        return result
    }

    /** R3 "Cift Odeme" — reklam DOLU dondugunde ek katmani yatirir. */
    fun grantDoublePayout(result: LevelClearResult, outcome: AdOutcome): Int {
        val bonus = doublePayoutBonus(result.doublableAmount, outcome)
        if (bonus > 0) {
            commitWallet(wallet.credited(bonus))
            analytics("coin_earn", mapOf("source" to "double_payout", "amount" to bonus))
        }
        return bonus
    }

    /**
     * R1 "Tedarik Talebi". No-fill/kapatma/timeout'ta da odeme yapar ve **gunluk
     * hakki tuketmez** (GDD G.4). Gunluk coin butcesi tukendiyse 0 doner ama
     * hicbir ilerleme yolu kapanmaz.
     */
    fun grantSupplyDrop(outcome: AdOutcome): RequisitionGrant {
        val grant = grantRequisition(requisition, outcome)
        requisition = grant.newState
        saveManager.saveRequisitionState(requisition)
        if (grant.coins > 0) {
            commitWallet(wallet.credited(grant.coins))
            analytics(
                if (grant.fallback) "rewarded_fallback_granted" else "coin_earn",
                mapOf("placement" to "supply_drop", "amount" to grant.coins)
            )
        }
        return grant
    }

    // =================================================================================
    // Gorevler
    // =================================================================================

    /** Bugunun 3 gorevi; deterministik uretilir, ilerlemesi kalicidir. */
    val todaysMissions: List<Mission> get() = daily

    val weeklyMissions: List<WeeklyMission> get() = missionState.weekly

    /** Saat suphesi damperi (yalnizca GOREV odulune uygulanir, GDD E.4). */
    val missionRewardMultiplier: Double get() = missionState.rewardMultiplier

    fun advanceDaily(type: MissionType, amount: Int) {
        if (amount <= 0) return
        daily = advanceMissions(daily, type, amount)
        saveManager.saveDailyProgress(daily)
    }

    fun advanceWeeklyCounter(type: MissionType, amount: Int) {
        if (amount <= 0) return
        missionState = missionState.copy(weekly = advanceWeekly(missionState.weekly, type, amount))
        saveManager.saveMissionState(missionState)
    }

    /** Tamamlanmis gorevlerin odulunu odeyip "alindi" isaretler. Iki kez odemez. */
    fun claimCompletedMissions(): Int {
        val multiplier = missionState.rewardMultiplier
        val claimable = daily.filter { it.isComplete && !it.claimed }
        var payout = 0
        if (claimable.isNotEmpty()) {
            payout += Math.floor(claimable.sumOf { it.reward } * multiplier).toInt()
            val allDone = daily.size == EconomyConfig.DAILY_MISSION_SLOTS &&
                daily.all { it.isComplete } &&
                daily.any { !it.claimed }
            if (allDone) {
                payout += Math.floor(EconomyConfig.DAILY_ALL_COMPLETE_BONUS * multiplier).toInt()
            }
            daily = daily.map { if (it.isComplete) it.copy(claimed = true) else it }
            saveManager.saveDailyProgress(daily)
        }

        val weeklyClaimable = missionState.weekly.filter { it.isComplete && !it.claimed }
        if (weeklyClaimable.isNotEmpty()) {
            payout += Math.floor(weeklyClaimable.sumOf { it.reward } * multiplier).toInt()
            missionState = missionState.copy(
                weekly = missionState.weekly.map { if (it.isComplete) it.copy(claimed = true) else it }
            )
            saveManager.saveMissionState(missionState)
        }

        if (payout > 0) {
            commitWallet(wallet.credited(payout))
            analytics("coin_earn", mapOf("source" to "mission", "amount" to payout))
        }
        return payout
    }

    // =================================================================================
    // Takvim / saat
    // =================================================================================

    /**
     * Gun ve hafta donusunu degerlendirir. **Saat geriye alindiysa sifirlama
     * TETIKLENMEZ ve odul verilmez**; ileri atlama en fazla BIR sifirlama sayilir
     * (GDD E.4). Offline oyunda tek savunma budur.
     */
    fun refreshCalendar(): ResetDecision {
        val decision = evaluateReset(missionState, clock.sample())
        missionState = decision.newState
        if (decision.dailyReset) {
            saveManager.resetDailyCounters()
            requisition = RequisitionState()
            boostedReplaysToday = 0
            saveManager.boostedReplaysUsedToday = 0
            missionState = missionState.copy(rerollsUsedToday = 0)
        }
        if (decision.weeklyReset) {
            saveManager.resetWeeklyCounters()
            missionState = missionState.copy(weekly = MissionPools.weekly())
        }
        if (decision.clockSuspect) {
            analytics(
                "clock_suspect_triggered",
                mapOf("direction" to if (decision.frozenByBackwardClock) "backward" else "forward")
            )
        }
        saveManager.saveMissionState(missionState)
        regenerateDailyMissions()
        return decision
    }

    private fun regenerateDailyMissions() {
        val generated = dailyMissions(
            epochDay = clock.sample().epochDay,
            seed = saveManager.missionSeed,
            clearedLevels = wallet.highestCleared(),
            unlockedTowerTypes = unlockedTowerTypes(),
        )
        val progress = saveManager.loadDailyProgress()
        val claims = saveManager.loadDailyClaims()
        daily = generated.map { mission ->
            mission.copy(
                progress = (progress[mission.id] ?: 0).coerceIn(0, mission.target),
                claimed = mission.id in claims,
            )
        }
    }

    /**
     * Acik kule tipi sayisi — gorev havuzu filtresi bunu okur (GDD H.5).
     * Railgun bolum 5'te, Frost Field bolum 8'de **bedava** acilir (GDD F).
     */
    private fun unlockedTowerTypes(): Int {
        val cleared = wallet.highestCleared()
        return when {
            cleared >= 8 -> 4
            cleared >= 5 -> 3
            cleared >= 3 -> 2
            else -> 1
        }
    }

    // =================================================================================
    // Soft-lock invariant'i (GDD C.4 katman 4)
    // =================================================================================

    /**
     * `bakiye >= siradakiKilitliBolumBedeli` invariant'i. Katman 1 (Rezerv) bunu
     * zaten garanti ettigi icin bu dal **hicbir zaman tetiklenmemeli**; tetiklenirse
     * ekonomide bug var demektir ve `auto_grant_fired` ile gorulur (GDD H.9 hedefi 0).
     * Oyuncu asla fark etmez, coin'i yeterli olur.
     */
    fun enforceUnlockInvariant(): Int {
        val shortfall = autoGrantShortfall(wallet)
        if (shortfall > 0) {
            commitWallet(wallet.credited(shortfall))
            analytics(
                "auto_grant_fired",
                mapOf("level" to (firstLockedLevel(wallet.unlockedLevels) ?: 0), "shortfall" to shortfall)
            )
        }
        return shortfall
    }

    private fun commitWallet(updated: PlayerWallet) {
        wallet = updated
        saveManager.saveWallet(updated)
    }
}

// =====================================================================================
// Saat kaynagi
// =====================================================================================

/**
 * Saat ornegi kaynagi. [MissionSystem]'in saf kalmasi icin platform okumasi burada
 * izole edilir; testler sahte bir uygulama enjekte eder.
 */
interface ClockProvider {
    fun sample(): ClockSample
}

/**
 * Uretim saati.
 *
 * - `epochDay` **yerel** gece yarisina gore hesaplanir (GDD E.4 kural 6: seyahatte
 *   saat dilimi degisimi mesrudur, tek basina suphe uretmez).
 * - `bootId` dogrudan API'si olmadigi icin `duvarSaati - elapsedRealtime`
 *   degerinden turetilir ve dakikaya yuvarlanir: ayni boot icinde sabit, yeniden
 *   baslatmada degisir.
 */
class AndroidClockProvider : ClockProvider {
    override fun sample(): ClockSample {
        val nowMs = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val offset = TimeZone.getDefault().getOffset(nowMs).toLong()
        return ClockSample(
            epochDay = Math.floorDiv(nowMs + offset, EconomyConfig.MS_PER_DAY),
            wallClockMs = nowMs,
            elapsedRealtimeMs = elapsed,
            bootId = (nowMs - elapsed) / 60_000L,
        )
    }
}
