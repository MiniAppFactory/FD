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
) : CampaignProgress, BattleTelemetry {

    private var wallet by mutableStateOf(saveManager.loadWallet())
    private var upgrades by mutableStateOf(saveManager.loadMetaUpgrades())
    private var missionState by mutableStateOf(saveManager.loadMissionState())
    private var requisition by mutableStateOf(saveManager.loadRequisitionState())
    private var boostedReplaysToday by mutableStateOf(saveManager.boostedReplaysUsedToday)
    private var daily by mutableStateOf<List<Mission>>(emptyList())

    /**
     * Faz 14 — SUREN SAVASIN GOREV OLCUMU.
     *
     * Savas ici olaylar gorevleri ANINDA ilerletmez; burada birikir ve savas
     * bitince [flushBattleMissions] ile TEK SEFERDE islenir. Sebep cift sayimin
     * onlenmesi: bolum bittiginde dalga tablosundan turetilen taban rapor
     * (bkz. [derivedBattleReport]) ile olculen rapor birlestirilir; ikisi de
     * anlik ilerletme yapsaydi ayni oldurme iki kez sayilirdi.
     *
     * Savasa kapsamli, kalici DEGIL: yarim birakilan savasin olcumu
     * [beginBattle] ile silinir.
     */
    private var battleReport by mutableStateOf(BattleReport.UNREPORTED)

    /** Bu savasin olcumu goreve islendi mi? Zafer + cikis yolunun ikisi de flush eder. */
    private var battleMissionsFlushed by mutableStateOf(false)

    /**
     * Faz 10 — guclendirici gunluk reklam sayaci.
     *
     * **Bilincli olarak KALICI DEGIL.** Guclendirici reklami hicbir coin odemez
     * (BoosterSystem.kt dosya basi 3), dolayisiyla uygulamayi yeniden baslatarak
     * sifirlamak bir *ekonomi* exploit'i degildir: oyuncu yalnizca daha fazla reklam
     * izleyebilir (gelir yonu olumlu) ve savas basina 1 kullanim limiti degismez.
     * Kalici hale getirmek istenirse `SaveManager`'a tek `Int` alani yeter —
     * ECONOMY_SPEC 9 devir listesi madde 5.
     */
    private var boosterAdViewsToday by mutableStateOf(0)

    /** Suren savasin guclendirici durumu. Savas disinda `null`. */
    private var battleBoosters by mutableStateOf<BoosterState?>(null)

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
    // Faz 10 — Guclendiriciler (savas ici tek kullanimlik)
    //
    // Bu blok kendi ekonomi kurali TANIMLAMAZ; tum kararlar `BoosterSystem.kt`in saf
    // fonksiyonlarindan gelir. Burada yalnizca durum tutulur ve coin dususu yapilir.
    // =================================================================================

    /** Suren savasin guclendirici durumu; savas disinda `null`. */
    val boosterState: BoosterState? get() = battleBoosters

    /** Bugun guclendirici reklami icin kalan hak. */
    val boosterAdViewsLeftToday: Int
        get() = (EconomyConfig.BOOSTER_AD_VIEWS_PER_DAY - boosterAdViewsToday).coerceAtLeast(0)

    /**
     * Savas basladi — guclendirici sayaclarini sifirlar (gunluk reklam sayaci tasinir).
     * Motor bolume girerken bir kez cagirir.
     */
    fun beginBattle(levelId: Int): BoosterState {
        // Onceki savas hic KAPATILMADIYSA olcumu burada isle. Bu dal gercek bir
        // yolu kurtarir: yenilgi -> "TEKRAR DENE" akisi `endBattle()`e HIC
        // ugramaz (savas ekranindan cikilmadigi icin), yalnizca yeni bir
        // `beginBattle` gelir. Bu cagri olmadan kaybedilen savasta oldurulen
        // her dusman sessizce silinirdi — en sik tekrarlanan oynanis yolunda.
        flushBattleMissions(victory = false)

        val state = BoosterState.startBattle(levelId, boosterAdViewsToday)
        battleBoosters = state
        // R2 takviyesi de savasa kapsamlidir: onceki savastan devreden bir
        // "geri verilmis can" yeni savasin yildizini haksiz yere dusururdu.
        reinforcedHealth = 0
        // Faz 14: gorev olcumu de savasa kapsamlidir. Yarida birakilan savasin
        // "kurulan kule" sayisi yeni savasa devrederse `d_v_build15` bir bolum
        // acip kapatarak kirilirdi.
        battleReport = BattleReport.UNREPORTED
        battleMissionsFlushed = false
        return state
    }

    /**
     * Savas bitti (zafer, yenilgi veya cikis). Guclendiriciler stoklanmaz.
     *
     * Faz 14: zaferle bitmeyen savasin **hacim** olcumu burada goreve islenir —
     * yenilen oyuncu da dusman oldurmustur ve `d_v_kill120` bunu saymalidir.
     * Zaferde [onLevelCleared] zaten flush etmis olur ve
     * [battleMissionsFlushed] ikinci islemeyi engeller.
     */
    fun endBattle() {
        flushBattleMissions(victory = false)
        battleBoosters = null
        reinforcedHealth = 0
    }

    /** Bu bolumde HUD'da gosterilecek guclendiriciler. */
    fun availableBoosters(levelId: Int): List<BoosterType> = boostersAvailableAt(levelId)

    /**
     * Butonun cizim durumu. UI kendi bakiye/limit karsilastirmasini YAPMAZ.
     * `beginBattle` cagrilmadiysa [BoosterDecision.Disabled] doner.
     */
    fun boosterDecision(
        type: BoosterType,
        viaAd: Boolean,
        supplyOnHand: Int = 0,
        baseHealth: Int = 0,
        maxBaseHealth: Int = upgrades.maxBaseHealth,
        enemiesOnField: Int = ENEMY_COUNT_UNKNOWN,
        nowMs: Long = clock.sample().elapsedRealtimeMs,
    ): BoosterDecision {
        val state = battleBoosters ?: return BoosterDecision.Disabled
        return boosterAllowed(
            state = state,
            type = type,
            viaAd = viaAd,
            wallet = wallet,
            supplyOnHand = supplyOnHand,
            baseHealth = baseHealth,
            maxBaseHealth = maxBaseHealth,
            enemiesOnField = enemiesOnField,
            nowMs = nowMs,
        )
    }

    /**
     * Guclendiriciyi kullanir. Reddedilirse **hicbir sey degismez** (cuzdan, sayaclar,
     * gunluk reklam hakki). Coin dususu burada yapilir; Tedarik dususu/eklemesi motorun
     * isidir ve [BoosterActivation.supplyCharged] / [BoosterActivation.supplyGranted]
     * uzerinden bildirilir.
     *
     * [enemiesOnField] sahadaki dusman sayisidir; bildirilirse ekonomi katmani
     * hedefsiz [BoosterType.AIR_SUPPORT] kullanimini ucret kesmeden reddeder
     * ([BoosterDecision.NoEffect]). Varsayilan [ENEMY_COUNT_UNKNOWN] = bildirilmedi,
     * kontrol atlanir.
     */
    fun activateBooster(
        type: BoosterType,
        viaAd: Boolean,
        supplyOnHand: Int = 0,
        baseHealth: Int = 0,
        maxBaseHealth: Int = upgrades.maxBaseHealth,
        enemiesOnField: Int = ENEMY_COUNT_UNKNOWN,
        nowMs: Long = clock.sample().elapsedRealtimeMs,
    ): BoosterActivation {
        val state = battleBoosters
            ?: return BoosterActivation(type, BoosterDecision.Disabled, viaAd)

        val decision = boosterDecision(
            type = type,
            viaAd = viaAd,
            supplyOnHand = supplyOnHand,
            baseHealth = baseHealth,
            maxBaseHealth = maxBaseHealth,
            enemiesOnField = enemiesOnField,
            nowMs = nowMs,
        )
        if (decision !is BoosterDecision.Allowed) {
            analytics(
                "booster_blocked",
                mapOf(
                    "type" to type.name,
                    "level" to state.level,
                    "via_ad" to viaAd,
                    "reason" to decision::class.simpleName.orEmpty(),
                )
            )
            return BoosterActivation(type, decision, viaAd)
        }

        val restored = if (type == BoosterType.BASE_REPAIR) {
            baseRepairAmount(baseHealth, maxBaseHealth)
        } else {
            0
        }
        val granted = if (type == BoosterType.EMERGENCY_SUPPLY) emergencySupplyAmount(state.level) else 0
        val charged = if (decision.currency == BoosterCurrency.SUPPLY && !viaAd) decision.price else 0
        val coins = if (decision.currency == BoosterCurrency.COIN && !viaAd) decision.price else 0

        if (coins > 0) commitWallet(payForBooster(wallet, decision))

        val updated = useBooster(
            state = state,
            type = type,
            viaAd = viaAd,
            decision = decision,
            nowMs = nowMs,
            repairedHealth = restored,
        )
        battleBoosters = updated
        if (viaAd) boosterAdViewsToday = updated.adViewsToday

        analytics(
            "booster_used",
            mapOf(
                "type" to type.name,
                "level" to state.level,
                "via_ad" to viaAd,
                "supply_cost" to charged,
                "coin_cost" to coins,
            )
        )

        return BoosterActivation(
            type = type,
            decision = decision,
            viaAd = viaAd,
            supplyGranted = granted,
            supplyCharged = charged,
            healthRestored = restored,
            airSupportDamageFraction = if (type == BoosterType.AIR_SUPPORT) {
                EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION
            } else {
                0.0
            },
            coinsSpent = coins,
        )
    }

    /**
     * Faz 13 — R2 "Takviye" ile bu savasta geri verilen us cani.
     *
     * Us Tamiri'nin [BoosterState.repairedHealth] alaniyla **ayni rolu** oynar ve
     * ayni yere, [starHealthFor]'a akar: takviye HAYATTA KALMA satin alir, yildiz
     * ve coin ASLA. Ayri bir alan olmasinin sebebi, takviyenin bir guclendirici
     * olmamasi — savas bittikten (DEFEAT) sonra gelir, dolayisiyla
     * `BoosterState`in savas-ici kullanim tavanlarina ve fiyat mantigina hic
     * girmemelidir.
     *
     * Kalici DEGIL: savasa kapsamlidir, [beginBattle] / [endBattle] sifirlar.
     */
    private var reinforcedHealth by mutableStateOf(0)

    /**
     * R2 Takviye uygulandi — geri verilen [lives] cani yildiz hesabindan dusulur.
     *
     * Motor cagrisini yapan taraf (reklam koprusu) bunu **motor gercekten savasi
     * surdurduyse** cagirir; uygulanmayan bir takviye yildizi cezalandirmamalidir.
     */
    fun noteReinforcement(lives: Int) {
        reinforcedHealth += lives.coerceAtLeast(0)
    }

    /**
     * Yildiz hesabina girmesi gereken can. **Us Tamiri ve R2 Takviye ile geri
     * verilen can dusulur** (BoosterSystem.effectiveStarHealth) — ikisi de hayatta
     * kalma satin alir, yildiz ve coin ASLA. Hicbiri kullanilmadiysa girdiyle ayni
     * degeri dondurur.
     */
    fun starHealthFor(livesLeft: Int): Int {
        val granted = (battleBoosters?.repairedHealth ?: 0) + reinforcedHealth
        if (granted == 0) return livesLeft
        val effective = effectiveStarHealth(livesLeft, granted)
        // Savas KAZANILDIYSA en az 1 yildiz verilmeli: tamir sayesinde ayakta kalan
        // oyuncuya "0 yildiz" demek zaferi iptal etmek olur ve `resolveLevelClear`
        // (stars > 0 sartli) patlar. Tamir yildiz KAZANDIRMAZ ama zaferi de silmez.
        return if (livesLeft > 0) effective.coerceAtLeast(1) else effective
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
    fun onLevelCleared(
        levelId: Int,
        livesLeft: Int,
        maxLives: Int,
        battle: BattleReport = BattleReport.UNREPORTED,
    ): LevelClearResult {
        // Yildiz **Us Tamiri'ne ragmen** gercek dayanikliliga gore verilir; guclendirici
        // kullanilmadiysa `starHealthFor` girdiyi aynen dondurur, yani Faz 9 davranisi
        // birebir korunur.
        val starHealth = starHealthFor(livesLeft)
        // Haftalik "Elit Operator" sayaci icin: cuzdan YAZILMADAN ONCEKI en iyi
        // yildiz. Asagida yalnizca ARTIS sayilir (bkz. `starGain`).
        val starsBefore = wallet.starsOf(levelId)
        val result = resolveLevelClear(wallet, levelId, starHealth, maxLives, boostedReplaysToday)
        commitWallet(applyLevelClear(wallet, result))
        if (result.consumesBoostedReplay) {
            boostedReplaysToday += 1
            saveManager.boostedReplaysUsedToday = boostedReplaysToday
        }
        advanceDaily(MissionType.COMPLETE_ANY_LEVEL, 1)
        if (result.stars >= 3) advanceDaily(MissionType.CLEAR_WITH_THREE_STARS, 1)
        // Gorev de tamir edilmis cana gore ILERLEMEZ; yoksa "tamir et -> yuksek can
        // gorevi -> 120 coin" arbitraji acilir (BoosterSystem yildiz notrlugu).
        if (starHealth.toDouble() / maxLives >= EconomyConfig.STAR3_HEALTH_RATIO) {
            advanceDaily(MissionType.CLEAR_WITH_HIGH_HEALTH, 1)
        }
        advanceWeeklyCounter(MissionType.WEEKLY_LEVELS_COMPLETED, 1)
        // Faz 15 — HAFTALIK YILDIZ SAYACI ARTIK "ARTIS" SAYIYOR.
        //
        // Onceki kural `result.firstClear` idi ve tek bir kor nokta uretiyordu:
        // kampanyayi (55 bolum) bitirmis oyuncu icin sayac SONSUZA KADAR olu
        // kaliyordu — 600 coin'lik `w_elite_operator` bir daha ASLA
        // tamamlanamiyordu, yani haftalik butcenin %55'i (1.100 -> 500)
        // erisilemez hale geliyordu. Hedef 15 yildiz/hafta; ilk temizleme
        // biterse arz da biter.
        //
        // Yeni kural: bu temizlik bolumun EN IYI yildizini kac basamak
        // yukselttiyse o kadar sayilir (2 -> 3 = +1). Ilk temizlemede
        // `starsBefore = 0` oldugu icin sonuc `result.stars`tir, yani bugunku
        // davranis BIREBIR korunur; degisen tek sey tekrar oynanan bolumun
        // artik sifir yerine FARKI sayiyor olmasi.
        //
        // FARMING'E KAPALI: yildiz best-of'tur (`applyLevelClear` maxOf) ve
        // asla dusmez, dolayisiyla bir bolumun her yildizi omur boyu EN FAZLA
        // BIR KEZ sayilabilir. Ust sinir sabit: 55 bolum x 3 = 165 yildiz.
        // Ayni bolumu tekrar oynamak 0 uretir. Us Tamiri / R2 Takviye ile
        // satin alinan can zaten `starHealth` uzerinden dusulmustur, yani
        // guclendiriciyle yildiz yukseltip sayac doldurma yolu da kapalidir.
        //
        // KALAN ACIK (rapor edildi, odul MIKTARI degistirilmedi): 165/165
        // yapmis oyuncu icin arz yine biter. O noktada kampanyanin tamami
        // tuketilmistir; yeni bolum gelene kadar bu gorevin yerine gececek bir
        // sablon GDD karari ister (ECONOMY_SPEC bayrak F-4).
        val starGain = (result.stars - starsBefore).coerceAtLeast(0)
        advanceWeeklyCounter(MissionType.WEEKLY_STARS_EARNED, starGain)

        // Faz 14 — GERI KALAN 10 SAYAC. Cagiranin bildirdigi olcum, savas
        // boyunca biriken olcum ve dalga tablosundan TURETILEN taban burada
        // birlesir; her alanda buyuk olan kazanir (turetme bir tabandir).
        noteBattle(battle)
        // Turetme HAM cana degil, guclendirici/takviye ile geri verilen cani
        // DUSULMUS `starHealth`e bakar. Ham deger kullanilsaydi Us Tamiri
        // sizintiyi gizler, `oldurulen = toplam - sizinti` sisirilir ve
        // "tamir et -> daha cok oldurme sayilsin" arbitraji acilirdi.
        noteBattle(derivedBattleReport(levelId, starHealth, maxLives))
        flushBattleMissions(victory = true)

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
     * R1 icin bugun kalan **dolu gosterim** hakki — oyuncuya "N/3" olarak
     * gosterilen sayi.
     *
     * Faz 13: bu sayinin sahibi ARTIK EKONOMI. Onceden UI onu reklam
     * katmanindan (`AdHost.rewardedRemaining`) okuyordu; oradaki sayac
     * BELLEK-ICI ve uygulama yeniden baslayinca sifirlaniyor, buradaki ise
     * [SaveManager] ile KALICI. Yani oyuncu uygulamayi kapatip acinca ekran
     * "3 hakkin var" diyor, ekonomi ise hakki dolu gorup 150 yerine 50 coin
     * oduyordu — ekranda yazan sayi ile cebe giren para ayrisiyordu.
     */
    val supplyDropViewsLeftToday: Int
        get() = (EconomyConfig.R1_VIEWS_PER_DAY - requisition.filledViewsToday).coerceAtLeast(0)

    /** R1 icin bugun odenebilecek kalan coin. 0 = butce doldu (ilerleme kapanmaz). */
    val supplyDropBudgetLeftToday: Int
        get() = (EconomyConfig.R1_COIN_BUDGET_PER_DAY - requisition.coinsPaidToday).coerceAtLeast(0)

    /**
     * R1 "Tedarik Talebi". No-fill/kapatma/timeout'ta da odeme yapar ve **gunluk
     * hakki tuketmez** (GDD G.4). Gunluk coin butcesi tukendiyse 0 doner ama
     * hicbir ilerleme yolu kapanmaz.
     */
    fun grantSupplyDrop(outcome: AdOutcome): RequisitionGrant {
        // Siradaki rank fiyati BAYRAK F-11 (adaptif odul) icin girdi; bayrak kapaliyken
        // deger yok sayilir ve odul sabit 150 kalir.
        val grant = grantRequisition(requisition, outcome, cheapestNextRankPrice(upgrades) ?: 0)
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
    override val todaysMissions: List<Mission> get() = daily

    override val weeklyMissions: List<WeeklyMission> get() = missionState.weekly

    /** Saat suphesi damperi (yalnizca GOREV odulune uygulanir, GDD E.4). */
    override val missionRewardMultiplier: Double get() = missionState.rewardMultiplier

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

    /** "Tek savasta en iyi" gorevleri toplamaz, [value]'ya yukseltir. */
    fun raiseDaily(type: MissionType, value: Int) {
        if (value <= 0) return
        daily = raiseMissions(daily, type, value)
        saveManager.saveDailyProgress(daily)
    }

    // ---------------------------------------------------------------------------------
    // Faz 14 — SAVAS OLCUMU
    //
    // Bu blok gorevleri ANINDA ilerletmez. Olcumu biriktirir; donusum ve
    // ilerletme yalnizca [flushBattleMissions] icinde, savas basina BIR KEZ olur.
    // ---------------------------------------------------------------------------------

    /** Suren savasin o ana kadarki gorev olcumu (UI/test gorunurlugu icin). */
    val currentBattleReport: BattleReport get() = battleReport

    /** Kismi bir olcumu suren savasin raporuna katar. Alan bazinda buyugu alir. */
    fun noteBattle(report: BattleReport) {
        if (report.isEmpty) return
        battleReport = battleReport.mergedWith(report)
    }

    /** Kule kuruldu (`GameConfig.TowerType` adi ile — tip cesitliligi de sayilir). */
    override fun noteTowerBuilt(towerTypeName: String) {
        val built = battleReport.towersBuilt.coerceAtLeast(0) + 1
        typesBuiltThisBattle.add(towerTypeName)
        battleReport = battleReport.copy(
            towersBuilt = built,
            distinctTowerTypes = typesBuiltThisBattle.size,
        )
    }

    /** Kule yukseltildi (bir kademe). */
    override fun noteTowerUpgraded() {
        battleReport = battleReport.copy(
            towerUpgrades = battleReport.towerUpgrades.coerceAtLeast(0) + 1
        )
    }

    /**
     * Kule satildi. Sifir da ANLAMLIDIR: "satis olcumu aktif ve hic satis yok"
     * demek `CLEAR_WITHOUT_SELLING`in hak edilmesi demektir, bu yuzden savas
     * basinda sayaci acikca 0'a cekmek gerekir ([noteSellTrackingActive]).
     */
    override fun noteTowerSold() {
        battleReport = battleReport.copy(
            towersSold = battleReport.towersSold.coerceAtLeast(0) + 1
        )
    }

    /** Satis olcumunun ACIK oldugunu bildirir; sayac 0'dan baslar. */
    override fun noteSellTrackingActive() {
        if (battleReport.towersSold == BATTLE_STAT_UNREPORTED) {
            battleReport = battleReport.copy(towersSold = 0)
        }
    }

    /** Hazirlik sayaci beklenmeden dalga baslatildi. */
    override fun notePrepTimerSkipped() {
        battleReport = battleReport.copy(
            prepTimersSkipped = battleReport.prepTimersSkipped.coerceAtLeast(0) + 1
        )
    }

    /** Savas hizi degisti; 2x'e cikildiysa savas boyunca hatirlanir. */
    override fun noteGameSpeed(speed: Float) {
        val doubled = battleReport.clearedAtDoubleSpeed == true || speed >= 2f
        battleReport = battleReport.copy(clearedAtDoubleSpeed = doubled)
    }

    private val typesBuiltThisBattle = mutableSetOf<String>()

    /**
     * Savasin olcumunu gorev sayaclarina isler. **Savas basina BIR KEZ.**
     *
     * Ikinci cagri sessizce doner: zafer yolunda [onLevelCleared] flush eder,
     * oyuncu bolum secime dondugunde [endBattle] tekrar dener ve ayni oldurmeler
     * ikinci kez sayilmamalidir.
     */
    private fun flushBattleMissions(victory: Boolean) {
        if (battleMissionsFlushed) return
        battleMissionsFlushed = true
        typesBuiltThisBattle.clear()
        val report = battleReport
        battleReport = BattleReport.UNREPORTED
        if (report.isEmpty) return

        battleMissionDeltas(report, victory, unlockedTowerTypes()).forEach { (type, amount) ->
            if (type.isSingleBattleBest) raiseDaily(type, amount) else advanceDaily(type, amount)
        }
    }

    /**
     * Bolumun dalga tablosundan TURETILEN taban rapor (yalnizca zafer yolunda).
     *
     * Bu bir tahmin degil KIMLIKTIR: sizinti basina tam 1 can gider
     * (`GameConfig.BASE_REACHED_PENALTY_LIVES = 1`), dolayisiyla
     * `oldurulen = toplamDusman - kaybedilenCan`. Agir tipler ve Tedarik geliri
     * ayni oldurme oraniyla ASAGI olceklenir, yani turetme hicbir zaman
     * gercekten fazlasini yazmaz.
     *
     * Neden gerekli: motor ekonomiyi tanimaz ve savas ici oldurme olaylarini
     * ekonomiye bildirmez. Turetme olmadan `KILL_ENEMIES` / `KILL_ARMORED` /
     * `KILL_TANKS` / `EARN_SUPPLY_IN_ONE_BATTLE` sayaclari — yani VOLUME
     * slotunun 4/6'si — hicbir zaman ilerlemezdi (FUN_AUDIT bulgusu).
     */
    internal fun derivedBattleReport(levelId: Int, livesLeft: Int, maxLives: Int): BattleReport {
        val counts = runCatching { GameConfigCampaignFacts.enemyCounts(levelId) }
            .getOrNull() ?: return BattleReport.UNREPORTED
        val total = counts.values.sum()
        if (total <= 0) return BattleReport.UNREPORTED

        val leaked = (maxLives - livesLeft).coerceIn(0, total)
        val killed = total - leaked
        val ratio = killed.toDouble() / total

        val armored = counts[ENEMY_ARMORED_VEHICLE] ?: 0
        val tanks = (counts[ENEMY_TANK] ?: 0) + (counts[ENEMY_COMMAND_TANK] ?: 0)
        val income = runCatching {
            SupplyBudgetModel.startingSupply(levelId) + SupplyBudgetModel.waveSupplyIncome(levelId)
        }.getOrDefault(0)

        return BattleReport(
            enemiesKilled = killed,
            armoredKilled = Math.floor(armored * ratio).toInt(),
            tanksKilled = Math.floor(tanks * ratio).toInt(),
            supplyEarned = Math.floor(income * ratio).toInt(),
        )
    }

    /** Tamamlanmis gorevlerin odulunu odeyip "alindi" isaretler. Iki kez odemez. */
    override fun claimCompletedMissions(): Int {
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
    /**
     * Gorev paneli acilirken cagrilir. Uygulama gece yarisini acikken gecmisse
     * panel BAYAT gorevleri gosterirdi; burada gun/hafta donusu yeniden
     * degerlendirilir. Saat hilesi savunmasi ayni [evaluateReset] uzerinden
     * gectigi icin panel acip kapatarak sifirlama uretilemez.
     */
    override fun refreshMissions() {
        refreshCalendar()
    }

    fun refreshCalendar(): ResetDecision {
        val decision = evaluateReset(missionState, clock.sample())
        missionState = decision.newState
        if (decision.dailyReset) {
            saveManager.resetDailyCounters()
            requisition = RequisitionState()
            boostedReplaysToday = 0
            saveManager.boostedReplaysUsedToday = 0
            boosterAdViewsToday = 0
            battleBoosters = battleBoosters?.copy(adViewsToday = 0)
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
            // Sayaci dolmayan gorev oyuncuya GOSTERILMEZ (bkz. MEASURABLE_MISSION_TYPES).
            measurableTypes = MEASURABLE_MISSION_TYPES,
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

/**
 * Faz 14 — **OLCUM YETENEGI KAPISI.**
 *
 * Bir gorev tipi ancak sayaci gercekten doluyorsa oyuncuya gosterilir. Kapiyi
 * acan iki kaynak var:
 *
 * 1. **Turetme** — `CampaignProgressImpl.derivedBattleReport` bolumun dalga
 *    tablosundan hesaplar. Hicbir dis baglanti gerektirmez, HER ZAMAN calisir.
 * 2. **Olcum** — savas UI'sinin [BattleTelemetry] cagrilari. Faz 15'te
 *    **BAGLANDI** ([BATTLE_TELEMETRY_WIRED]).
 *
 * ## Neden kapi var
 * Tamamlanamayan bir gorev gostermek, hic gorev gostermemekten KOTUDUR:
 * oyuncu 120 coin'lik bir beceri gorevini gorur, gunu boyunca ilerletemez ve
 * panelin tamamina guvenini kaybeder. Kapi olmadan gunlerin ~%60'inda beceri
 * slotu boyle bir gorev cikariyordu.
 *
 * ## Kapi ACILDI (Faz 15)
 * Savas yuzeyleri [BattleTelemetry] uzerinden olcumu bildiriyor:
 * ```
 * TowerBuildBar          -> noteTowerBuilt(type.name)        // buildTower() true dondu
 * SelectedTowerInspector -> noteTowerUpgraded() / noteTowerSold()
 * HUDOverlay             -> notePrepTimerSkipped() / noteGameSpeed(speed)
 * GameScreen (savas basi)-> noteSellTrackingActive() + noteGameSpeed(1x tabani)
 * ```
 * Dikis **derleyici zorunlu**: uc composable da `telemetry` parametresini
 * VARSAYILANSIZ alir, yani baglanti sessizce kopamaz. `MissionTelemetryWiringTest`
 * gercek dugmelere basip sayaclarin ilerledigini kanitlar.
 *
 * Bu bayrak artik yalnizca kapinin **niye** var oldugunu belgeliyor; `false`a
 * cekilirse alti sablon ayni gun havuzdan cikar (geri alma supabi).
 */
internal const val BATTLE_TELEMETRY_WIRED: Boolean = true

/** Dalga tablosundan TURETILEN — dis baglanti gerektirmez. */
internal val DERIVED_MISSION_TYPES: Set<MissionType> = setOf(
    MissionType.COMPLETE_ANY_LEVEL,
    MissionType.CLEAR_WITH_THREE_STARS,
    MissionType.CLEAR_WITH_HIGH_HEALTH,
    MissionType.KILL_ENEMIES,
    MissionType.KILL_ARMORED,
    MissionType.KILL_TANKS,
    MissionType.EARN_SUPPLY_IN_ONE_BATTLE,
    MissionType.WEEKLY_LEVELS_COMPLETED,
    MissionType.WEEKLY_STARS_EARNED,
)

/** Yalnizca savas ici OLCUM ile dolabilenler. */
internal val TELEMETRY_ONLY_MISSION_TYPES: Set<MissionType> = setOf(
    MissionType.SKIP_PREP_TIMER,
    MissionType.BUILD_TOWERS,
    MissionType.UPGRADE_TOWERS,
    MissionType.CLEAR_WITHOUT_SELLING,
    MissionType.BUILD_ALL_TOWER_TYPES,
    MissionType.CLEAR_AT_DOUBLE_SPEED,
)

/** Bugun oyuncuya gosterilmesi MESRU olan gorev tipleri. */
internal val MEASURABLE_MISSION_TYPES: Set<MissionType>
    get() = if (BATTLE_TELEMETRY_WIRED) {
        DERIVED_MISSION_TYPES + TELEMETRY_ONLY_MISSION_TYPES
    } else {
        DERIVED_MISSION_TYPES
    }

/**
 * `GameConfig.EnemyType` adlari. `CampaignFacts.enemyCounts` anahtarlari bunlar
 * ve bu dosya bilincli olarak `game/model`i IMPORT ETMEZ (ekonomi katmani
 * modelden bagimsiz kalir; koprü `GameConfigCampaignFacts`tir).
 * `MissionCounterWiringTest` adlarin enum ile ayni oldugunu kilitler.
 */
private const val ENEMY_ARMORED_VEHICLE = "ARMORED_VEHICLE"
private const val ENEMY_TANK = "TANK"
private const val ENEMY_COMMAND_TANK = "COMMAND_TANK"

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
