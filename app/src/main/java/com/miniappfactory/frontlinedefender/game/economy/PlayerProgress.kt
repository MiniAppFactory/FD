package com.miniappfactory.frontlinedefender.game.economy

/**
 * Faz 9 — OYUNCU ILERLEMESI + SAF EKONOMI FONKSIYONLARI.
 *
 * **Android importu YOK.** Saf veri siniflari ve saf fonksiyonlar; JVM'de
 * Robolectric olmadan kosar. Kalicilik `SaveManager`'in, UI baglantisi
 * `CampaignProgressImpl`'in isi.
 *
 * Tum sayilar [EconomyConfig]'ten gelir; bu dosyada hicbir ekonomi sabiti yoktur.
 */

// =====================================================================================
// Meta yukseltme hatlari (GDD F)
// =====================================================================================

enum class UpgradeLine(val maxRank: Int) {
    /** Ates Gucu — tum kule hasari +%6/rank, maks +%24. */
    FIREPOWER(4),

    /** Menzil (Optics) — tum kule menzili +%5/rank, maks +%15. */
    OPTICS(3),

    /** Baslangic Tedariki — savas basi Tedarik +25/rank, 150 -> 300. */
    STARTING_SUPPLY(6),

    /** Us Tahkimi — maks us cani +2/rank, 20 -> 30. */
    FORTIFICATION(5),

    /**
     * Hurda Degeri — kule satis iadesi +%5/rank, **%70 -> %90**.
     *
     * Rank 0 = %70, yani oyunun her zaman verdigi iade
     * ([EconomyConfig.BASE_SALVAGE_RATIO] gerekcesine bak: taban bir zamanlar
     * 0,50'ye indirilmisti ve hat "sahip oldugunu geri satin al"a donmustu).
     */
    SALVAGE(4),
    ;

    /** Bu hattaki `rank` (1 tabanli) icin fiyat. */
    fun costOfRank(rank: Int): Int {
        val table = costTable()
        require(rank in 1..table.size) { "$name rank $rank aralik disi (1..${table.size})" }
        return table[rank - 1]
    }

    /** Bu hatti maksa cikarmanin toplam maliyeti. */
    fun totalCost(): Int = (1..maxRank).sumOf { costOfRank(it) }

    private fun costTable(): IntArray = when (this) {
        FIREPOWER -> EconomyConfig.FIREPOWER_COSTS
        OPTICS -> EconomyConfig.OPTICS_COSTS
        STARTING_SUPPLY -> EconomyConfig.STARTING_SUPPLY_COSTS
        FORTIFICATION -> EconomyConfig.FORTIFICATION_COSTS
        SALVAGE -> EconomyConfig.SALVAGE_COSTS
    }
}

/**
 * Meta yukseltme durumu. Rank'lar 0 tabanlidir (0 = hic alinmamis).
 *
 * Oynanis etkileri de BURADA turetilir; motor bu carpanlari okur, kendi yukseltme
 * matematigini yazmaz. Boylece "hasar +%24" tek yerde tanimli kalir.
 */
data class MetaUpgrades(
    val firepower: Int = 0,
    val optics: Int = 0,
    val startingSupplyRank: Int = 0,
    val fortification: Int = 0,
    val salvage: Int = 0,
) {
    init {
        require(firepower in 0..UpgradeLine.FIREPOWER.maxRank) { "firepower=$firepower" }
        require(optics in 0..UpgradeLine.OPTICS.maxRank) { "optics=$optics" }
        require(startingSupplyRank in 0..UpgradeLine.STARTING_SUPPLY.maxRank) { "supply=$startingSupplyRank" }
        require(fortification in 0..UpgradeLine.FORTIFICATION.maxRank) { "fortification=$fortification" }
        require(salvage in 0..UpgradeLine.SALVAGE.maxRank) { "salvage=$salvage" }
    }

    fun rankOf(line: UpgradeLine): Int = when (line) {
        UpgradeLine.FIREPOWER -> firepower
        UpgradeLine.OPTICS -> optics
        UpgradeLine.STARTING_SUPPLY -> startingSupplyRank
        UpgradeLine.FORTIFICATION -> fortification
        UpgradeLine.SALVAGE -> salvage
    }

    fun withRank(line: UpgradeLine, rank: Int): MetaUpgrades = when (line) {
        UpgradeLine.FIREPOWER -> copy(firepower = rank)
        UpgradeLine.OPTICS -> copy(optics = rank)
        UpgradeLine.STARTING_SUPPLY -> copy(startingSupplyRank = rank)
        UpgradeLine.FORTIFICATION -> copy(fortification = rank)
        UpgradeLine.SALVAGE -> copy(salvage = rank)
    }

    fun incremented(line: UpgradeLine): MetaUpgrades = withRank(line, rankOf(line) + 1)

    // ---- Turetilmis oynanis etkileri ---------------------------------------------

    /** Tum kule hasarina uygulanan carpan. Maks rank (4) -> 1,24 (GDD H.6). */
    val damageMultiplier: Double
        get() = 1.0 + EconomyConfig.FIREPOWER_DAMAGE_PER_RANK * firepower

    /** Tum kule menziline uygulanan carpan. Maks rank (3) -> 1,15. */
    val rangeMultiplier: Double
        get() = 1.0 + EconomyConfig.OPTICS_RANGE_PER_RANK * optics

    /** Savas baslangic Tedariki. Rank 6 -> 300 (GDD H.6). */
    val startingSupply: Int
        get() = EconomyConfig.BASE_STARTING_SUPPLY +
            EconomyConfig.STARTING_SUPPLY_PER_RANK * startingSupplyRank

    /** Maks us cani. Rank 5 -> 30. Yildiz esikleri YUZDE oldugu icin guvenli (GDD B.3). */
    val maxBaseHealth: Int
        get() = EconomyConfig.BASE_MAX_HEALTH +
            EconomyConfig.FORTIFICATION_HEALTH_PER_RANK * fortification

    /** Kule satis iade orani. Rank 0 -> 0,70 (oyunun tabani), rank 4 -> 0,90. */
    val salvageRatio: Double
        get() = EconomyConfig.BASE_SALVAGE_RATIO + EconomyConfig.SALVAGE_PER_RANK * salvage

    /**
     * Bataryanin *etkin verim* carpani = hasar x menzil.
     * Menzil, dusmanin kule kapsamasinda gecirdigi kord suresini yaklasik dogrusal
     * buyutur (LEVEL_DESIGN 0.4), yani teslim edilen toplam hasari da carpar.
     * ECONOMY_SPEC 6.2 beklenen can kaybini (ELL) bununla yeniden olcekler.
     */
    val effectiveThroughput: Double
        get() = damageMultiplier * rangeMultiplier

    /** Alinmis rank'lara odenmis toplam coin. */
    fun spentCoins(): Int = UpgradeLine.entries.sumOf { line ->
        (1..rankOf(line)).sumOf { line.costOfRank(it) }
    }

    fun totalRanks(): Int = UpgradeLine.entries.sumOf { rankOf(it) }

    fun isMaxed(): Boolean = UpgradeLine.entries.all { rankOf(it) == it.maxRank }
}

// =====================================================================================
// Cuzdan + kampanya ilerlemesi
// =====================================================================================

/**
 * Coin cuzdani ve kampanya ilerlemesi. **Tedarik burada YOKTUR** (DECISIONS).
 *
 * @param unlockedLevels konuslanma bedeli odenmis VEYA bedava (1..6) bolumler.
 * @param clearedLevels en az 1 yildizla bitirilmis bolumler.
 * @param bestStars bolum -> en iyi yildiz. **Asla dusmez** (GDD H.2).
 * @param perfectDefenseLevels "Kusursuz Savunma" madalyasi alinmis bolumler (tek seferlik).
 */
data class PlayerWallet(
    val coins: Int = 0,
    val unlockedLevels: Set<Int> = (1 until EconomyConfig.FIRST_PAID_LEVEL).toSet(),
    val clearedLevels: Set<Int> = emptySet(),
    val bestStars: Map<Int, Int> = emptyMap(),
    val perfectDefenseLevels: Set<Int> = emptySet(),
    val lifetimeEarned: Int = 0,
    val lifetimeSpent: Int = 0,
) {
    init {
        require(coins >= 0) { "coins negatif olamaz: $coins" }
    }

    fun starsOf(level: Int): Int = bestStars[level] ?: 0

    /** Toplanmis yildiz (haftalik gorev ve basarimlar bunu okur). */
    fun totalStars(): Int = bestStars.values.sum()

    /** Acilmis en yuksek bolum. */
    fun highestUnlocked(): Int = unlockedLevels.maxOrNull() ?: 0

    /** Temizlenmis en yuksek bolum — meta rank kapisi ve gorev havuzu bunu okur. */
    fun highestCleared(): Int = clearedLevels.maxOrNull() ?: 0

    fun credited(amount: Int): PlayerWallet {
        require(amount >= 0) { "credit negatif olamaz: $amount" }
        return copy(coins = coins + amount, lifetimeEarned = lifetimeEarned + amount)
    }

    fun debited(amount: Int): PlayerWallet {
        require(amount >= 0) { "debit negatif olamaz: $amount" }
        require(coins - amount >= 0) { "bakiye negatife dusemez: $coins - $amount" }
        return copy(coins = coins - amount, lifetimeSpent = lifetimeSpent + amount)
    }
}

// =====================================================================================
// Bolum kilidi, odul, yildiz
// =====================================================================================

/** Konuslanma bedeli D(L). L <= 6 bedava. TEK SEFERLIK; tekrar oynamak daima 0. */
fun lockCost(level: Int): Int {
    require(level in 1..EconomyConfig.CAMPAIGN_LEVELS) { "level $level aralik disi" }
    if (level < EconomyConfig.FIRST_PAID_LEVEL) return 0
    return EconomyConfig.LOCK_COSTS[level - EconomyConfig.FIRST_PAID_LEVEL]
}

/** R(L) tabani, yildiz carpani uygulanmadan. */
fun baseLevelReward(level: Int): Int {
    require(level in 1..EconomyConfig.CAMPAIGN_LEVELS) { "level $level aralik disi" }
    return EconomyConfig.REWARD_BASE + EconomyConfig.REWARD_STEP * (level - 1)
}

fun starMultiplier(stars: Int): Double = when (stars) {
    0 -> 0.0
    1 -> EconomyConfig.STAR_MULT_1
    2 -> EconomyConfig.STAR_MULT_2
    3 -> EconomyConfig.STAR_MULT_3
    else -> throw IllegalArgumentException("stars $stars aralik disi (0..3)")
}

/** GDD C.2 "10'a yuvarlanir" — bankaci yuvarlamasi degil, EN YAKIN 10. */
internal fun roundToTen(value: Double): Int = (Math.round(value / 10.0) * 10L).toInt()

/** Bolumun **ilk kez** tamamlanma odulu, yildiz carpanli ve 10'a yuvarlanmis. */
fun levelReward(level: Int, stars: Int): Int {
    if (stars == 0) return 0
    return roundToTen(baseLevelReward(level) * starMultiplier(stars))
}

/**
 * Yildiz iyilestirme farki (BAYRAK F-3, [EconomyConfig.STAR_IMPROVEMENT_BONUS_ENABLED]).
 * Bir bolumu 1 yildizla gecip sonra 3 yildiz yapan oyuncu aradaki farki **bir kez** alir.
 */
fun starImprovementBonus(level: Int, oldStars: Int, newStars: Int): Int {
    if (!EconomyConfig.STAR_IMPROVEMENT_BONUS_ENABLED) return 0
    if (newStars <= oldStars) return 0
    return levelReward(level, newStars) - levelReward(level, oldStars)
}

/**
 * Tekrar oynama odulu (GDD C.2 / C.4 katman 2).
 *
 * Gunun ilk [EconomyConfig.BOOSTED_REPLAYS_PER_DAY] tekrarinda en iyi yildiza gore
 * `R(L) x 0,20`; sonrasinda sabit taban 25. Taban **asla 0 olmaz**, gunluk ust sinir
 * yoktur — sinirlama *verimsizlik* ile yapilir (ECONOMY_SPEC 5.1).
 *
 * @param boostedReplaysUsedToday GLOBAL gunluk sayac (bolum basina DEGIL).
 */
fun replayReward(level: Int, bestStars: Int, boostedReplaysUsedToday: Int): Int {
    require(boostedReplaysUsedToday >= 0) { "negatif tekrar sayaci" }
    if (bestStars == 0) return 0 // hic gecilmemis bolum "tekrar" edilemez
    if (boostedReplaysUsedToday >= EconomyConfig.BOOSTED_REPLAYS_PER_DAY) {
        return EconomyConfig.REPLAY_FLOOR
    }
    val boosted = roundToTen(
        baseLevelReward(level) * starMultiplier(bestStars) * EconomyConfig.REPLAY_RATIO
    )
    return maxOf(EconomyConfig.REPLAY_FLOOR, boosted)
}

/** Bu tekrar odulu R3 "Cift Odeme" ile katlanabilir mi? Taban 25 katlanmaz. */
fun replayRewardIsDoublable(boostedReplaysUsedToday: Int): Boolean =
    EconomyConfig.R3_APPLIES_TO_REPLAY_FLOOR ||
        boostedReplaysUsedToday < EconomyConfig.BOOSTED_REPLAYS_PER_DAY

/**
 * GDD B.3 — yildiz YALNIZCA kalan us cani yuzdesine bakar.
 * Kule verimliligi, sure, kullanilan kule sayisi yildizi ETKILEMEZ.
 */
fun starsFor(livesLeft: Int, maxLives: Int): Int {
    require(maxLives > 0) { "maxLives > 0 olmali" }
    if (livesLeft <= 0) return 0
    val ratio = livesLeft.toDouble() / maxLives.toDouble()
    return when {
        ratio >= EconomyConfig.STAR3_HEALTH_RATIO -> 3
        ratio >= EconomyConfig.STAR2_HEALTH_RATIO -> 2
        else -> 1
    }
}

/**
 * Yildiz hesabina giren can — **meta yukseltmeden ARINDIRILMIS**.
 *
 * ## Kapattigi exploit
 * "Tahkimat" meta yukseltmesi maks us canini 20'den 30'a cikariyor. Motor
 * yildizi `kalanCan / tabanCan` ile hesapliyordu; pay meta DAHIL, payda taban
 * idi. Tahkimat rank 5'teki oyuncu 12 dusman sizdirip 3 yildiz, 10 sizdirip
 * "Kusursuz Savunma" madalyasi (+80 coin) aliyordu — meta yukseltme bir yildiz
 * hilesine donusmustu.
 *
 * ## Neden payda [baseMaxLives] birakildi
 * Alternatif, paydayi da meta-dahil yapmakti; matematik duzelirdi ama Tahkimat
 * bir CEZAYA donerdi: 30 canli oyuncunun 3 yildiz icin 3 sizintiya hakki
 * olurken 20 canlinin 2 sizintiya hakki olurdu, yani oyuncu satin aldigi seyle
 * zorlastirilirdi. Secilen kural: **yildiz yalnizca SIZINTI SAYISINA bakar.**
 * Her rankta 3 yildiz ayni sizinti sayisini gerektirir; Tahkimat'in aldigi sey
 * yildiz degil dayaniklilik marjidir. Bu, [effectiveStarHealth] ile birebir
 * ayni doktrindir ("geri verilen veya fazladan can, yildiz degil hayatta kalma
 * satin alir").
 *
 * En az 1 doner: meta sayesinde ayakta kalan oyuncuya 0 yildiz demek zaferi
 * iptal etmek olurdu ve [resolveLevelClear] (stars > 0 sartli) patlardi.
 *
 * @param baseMaxLives bolumun TABAN us cani (meta bonusu HARIC).
 * @param livesLost bu savasta usse sizan dusman sayisi.
 */
fun starHealthFromLeaks(baseMaxLives: Int, livesLost: Int): Int {
    require(baseMaxLives > 0) { "baseMaxLives > 0 olmali" }
    return (baseMaxLives - livesLost.coerceAtLeast(0)).coerceAtLeast(1)
}

/**
 * [targetStars] yildizi almak icin gereken **en az** can.
 *
 * Zafer ekranindaki "N sizinti daha az = 3 yildiz" ipucunun kaynagi. Esikleri
 * elle cevirmek yerine [starsFor]'u tarayarak bulur: yuzde esikleri
 * ([EconomyConfig.STAR3_HEALTH_RATIO]) ondalik oldugu icin `ceil(0.90 * 20)`
 * gibi bir hesap kayan nokta yuzunden 19 dondurup ipucunu bir sizinti YANLIS
 * gosterebilirdi. Tarama, gosterilen sayinin gercek yildiz kurali ile
 * ayrisamayacagini garanti eder.
 *
 * @return 1..[maxLives] araliginda bir can; [targetStars] <= 0 icin 0.
 */
fun healthNeededForStars(targetStars: Int, maxLives: Int): Int {
    require(maxLives > 0) { "maxLives > 0 olmali" }
    if (targetStars <= 0) return 0
    for (health in 1..maxLives) {
        if (starsFor(health, maxLives) >= targetStars) return health
    }
    return maxLives
}

/** Okunurluk takma adi; [starsFor] ile ayni fonksiyon. */
fun starsForRemainingHealth(remainingHealth: Int, maxHealth: Int): Int =
    starsFor(remainingHealth, maxHealth)

/** Kusursuz Savunma: hic sizma yok, yani kalan can = maks can (GDD B.3). */
fun isPerfectDefense(livesLeft: Int, maxLives: Int): Boolean =
    maxLives > 0 && livesLeft >= maxLives

// =====================================================================================
// Konuslanma Rezervi — soft-lock'un BIRINCIL garantisi (GDD C.4 katman 1)
// =====================================================================================

/** Bedeli odenmemis en kucuk bolum. Hepsi acilmissa `null`. */
fun firstLockedLevel(unlockedLevels: Set<Int>): Int? =
    (EconomyConfig.FIRST_PAID_LEVEL..EconomyConfig.CAMPAIGN_LEVELS)
        .firstOrNull { it !in unlockedLevels }

/** Siradaki kilitli bolum icin ayrilan coin. Kampanya bittiyse 0. */
fun reserveFor(nextLockedLevel: Int?): Int =
    if (nextLockedLevel == null) 0 else lockCost(nextLockedLevel)

/**
 * Cuzdana gore rezerv — **yalnizca gercekten gereken kadar**.
 *
 * SORUN (cihazda goruldu): `unlockedLevels` bastan 1..6'yi iceriyor, dolayisiyla
 * `firstLockedLevel` daha ilk dakikada 7 donuyordu ve rezerv 100 oluyordu.
 * Oyuncu 2. bolumdeyken 100 coin'i varken harcanabilir bakiye 0 cikiyor, dukkanin
 * BES KARTI da "coin eksik" gosteriyordu. Rezervin korudugu sey "oyuncu kapiya
 * geldiginde parasi olsun"; kapi bes bolum uzaktayken tum bakiyeyi kilitlemek
 * hicbir sey korumuyor, sadece ekonomiyi olduruyor.
 *
 * DOGRU HESAP: kapiya varmadan once kesin kazanilacak coin projeksiyonu
 * dusulur. Her bolum en az [EconomyConfig.REWARD_BASE] oder (1 yildiz,
 * gorev/reklam yok), dolayisiyla kapiya kadar temizlenecek her bolum garantili
 * gelirdir. Rezerv = kapi bedeli − bu garantili gelir.
 *
 * Soft-lock garantisi KORUNUR ve daha da siki: projeksiyon TABAN odul uzerinden
 * yapiliyor, gercek odul her zaman >= bu. Kapinin hemen oncesinde projeksiyon
 * sifira dustugu icin rezerv tam bedele cikar.
 */
fun reserveFor(wallet: PlayerWallet): Int {
    val gate = firstLockedLevel(wallet.unlockedLevels) ?: return 0
    val bedel = lockCost(gate)

    // Kapiya kadar HENUZ TEMIZLENMEMIS bolumler: her biri garantili gelir.
    val kalanBolum = (1 until gate).count { it !in wallet.clearedLevels }
    val garantiliGelir = kalanBolum * EconomyConfig.REWARD_BASE

    return (bedel - garantiliGelir).coerceAtLeast(0)
}

/** Dukkanda gosterilen "harcanabilir bakiye". Negatife dusmez. */
fun spendableBalance(coins: Int, reserve: Int): Int = maxOf(0, coins - reserve)

fun spendableBalance(wallet: PlayerWallet): Int = spendableBalance(wallet.coins, reserveFor(wallet))

// =====================================================================================
// Yukseltme satin alma
// =====================================================================================

/**
 * Satin alma karari. **Siralama analytics sozlesmesidir**: [ReserveLocked] yalnizca
 * oyuncu parayi gercekten *karsilayabildigi* halde rezerv engelledginde doner —
 * GDD H.9'daki `reserve_lock_blocked` metrigi bunu olcer. Bakiye zaten yetmiyorsa
 * [InsufficientFunds] doner ve o olay gonderilmez.
 */
sealed class PurchaseDecision {
    data class Allowed(val price: Int, val balanceAfter: Int) : PurchaseDecision()

    /** Hat maksta. */
    data object MaxRank : PurchaseDecision()

    /** Kampanya ilerlemesi kapisi (BAYRAK F-1, varsayilan kapali). */
    data class RankGated(val requiredClearedLevel: Int) : PurchaseDecision()

    /** Bakiye fiyati hic karsilamiyor. */
    data class InsufficientFunds(val price: Int, val shortfall: Int) : PurchaseDecision()

    /** Bakiye yetiyor ama alim rezervi ihlal ediyor -> buton pasif, "Rezerv korunuyor". */
    data class ReserveLocked(val price: Int, val reserve: Int, val shortfall: Int) : PurchaseDecision()

    val isAllowed: Boolean get() = this is Allowed
}

/** Bir sonraki rank'in fiyati; hat maksta `null`. */
fun nextRankPrice(line: UpgradeLine, upgrades: MetaUpgrades): Int? {
    val next = upgrades.rankOf(line) + 1
    return if (next > line.maxRank) null else line.costOfRank(next)
}

/** Bir rank'in acilmasi icin gereken temizlenmis bolum. Kapi kapaliysa 0. */
fun rankGateClearedLevel(
    rank: Int,
    gatesEnabled: Boolean = EconomyConfig.META_RANK_GATES_ENABLED,
): Int {
    if (!gatesEnabled) return 0
    if (rank < 1) return 0
    val idx = minOf(rank, EconomyConfig.RANK_GATE_CLEARED_LEVEL.size) - 1
    return EconomyConfig.RANK_GATE_CLEARED_LEVEL[idx]
}

/**
 * **Konuslanma Rezervi kontrolu.** GDD C.4 katman 1 ve H.3 kabul kriterlerinin
 * kodla yazilmis hali. Dukkan UI'i butonu bu fonksiyonun sonucuna gore cizer;
 * kendi bakiye karsilastirmasini YAPMAZ.
 */
fun purchaseAllowed(
    wallet: PlayerWallet,
    upgrades: MetaUpgrades,
    line: UpgradeLine,
    gatesEnabled: Boolean = EconomyConfig.META_RANK_GATES_ENABLED,
): PurchaseDecision {
    val price = nextRankPrice(line, upgrades) ?: return PurchaseDecision.MaxRank

    val gate = rankGateClearedLevel(upgrades.rankOf(line) + 1, gatesEnabled)
    if (gate > 0 && wallet.highestCleared() < gate) {
        return PurchaseDecision.RankGated(gate)
    }

    if (wallet.coins < price) {
        return PurchaseDecision.InsufficientFunds(price, price - wallet.coins)
    }

    val reserve = reserveFor(wallet)
    if (wallet.coins - price < reserve) {
        return PurchaseDecision.ReserveLocked(price, reserve, reserve - (wallet.coins - price))
    }

    return PurchaseDecision.Allowed(price, wallet.coins - price)
}

/** Satin almayi uygular. Reddedilirse cuzdan ve yukseltmeler **degismez**. */
fun applyPurchase(
    wallet: PlayerWallet,
    upgrades: MetaUpgrades,
    line: UpgradeLine,
    gatesEnabled: Boolean = EconomyConfig.META_RANK_GATES_ENABLED,
): Pair<PlayerWallet, MetaUpgrades> {
    val decision = purchaseAllowed(wallet, upgrades, line, gatesEnabled)
    if (decision !is PurchaseDecision.Allowed) return wallet to upgrades
    return wallet.debited(decision.price) to upgrades.incremented(line)
}

/** Ilerlemeye gore o an satin alinabilir hale gelmis toplam agac maliyeti (kapi tavani). */
fun purchasableTreeCeiling(
    highestClearedLevel: Int,
    gatesEnabled: Boolean = EconomyConfig.META_RANK_GATES_ENABLED,
): Int = UpgradeLine.entries.sumOf { line ->
    (1..line.maxRank)
        .filter { rank ->
            val gate = rankGateClearedLevel(rank, gatesEnabled)
            gate == 0 || highestClearedLevel >= gate
        }
        .sumOf { line.costOfRank(it) }
}

// =====================================================================================
// Bolum kilidi acma + son care invariant (GDD C.4 katman 4)
// =====================================================================================

/** Sirali ilerleme: L(n) yalnizca L(n-1) temizlenmisse acilabilir (GDD H.1). */
fun prerequisiteCleared(wallet: PlayerWallet, level: Int): Boolean =
    level <= 1 || (level - 1) in wallet.clearedLevels

fun canUnlock(wallet: PlayerWallet, level: Int): Boolean {
    if (level in wallet.unlockedLevels) return false
    if (!prerequisiteCleared(wallet, level)) return false
    return wallet.coins >= lockCost(level)
}

fun applyUnlock(wallet: PlayerWallet, level: Int): PlayerWallet {
    if (!canUnlock(wallet, level)) return wallet
    return wallet.debited(lockCost(level)).copy(unlockedLevels = wallet.unlockedLevels + level)
}

/**
 * **Son care otomatik hibe invariant'i** (GDD C.4 katman 4).
 * Hedef: `auto_grant_fired` KESIN 0 (GDD H.9).
 *
 * KRITIK NETLESTIRME (ECONOMY_SPEC 7 bayrak F-2): invariant yalnizca oyuncunun o
 * bolumu **gercekten deneyebildigi** anda baglayicidir. Aksi halde normal oyunda
 * yanlis tetiklenir: rezerve kadar harcayip L(n)'i acan oyuncunun bakiyesi gecici
 * olarak D(n+1)'in altina duser — ama L(n) henuz temizlenmedigi icin L(n+1) zaten
 * oynanamaz durumdadir ve bakiye L(n) temizlenince R(n) >= 2,1 x D(n+1) ile kendini
 * onarir (GDD C.1 kural 3).
 */
fun autoGrantShortfall(wallet: PlayerWallet): Int {
    val next = firstLockedLevel(wallet.unlockedLevels) ?: return 0
    if (!prerequisiteCleared(wallet, next)) return 0
    return maxOf(0, lockCost(next) - wallet.coins)
}

// =====================================================================================
// Savas sonucu -> coin muhasebesi (motorun tek giris noktasi)
// =====================================================================================

/**
 * Bir zaferin coin dokumu. UI zafer ekraninda kalem kalem gosterebilir; toplam
 * [total]'dir ve **reklam gosterilmeden ONCE** yazilir (GDD G.4 R3 kurali).
 *
 * @param doublableAmount R3 "Cift Odeme" ile katlanabilecek kisim. Taban tekrar
 *   odulu (25) buraya girmez (BAYRAK F-7).
 */
data class LevelClearResult(
    val level: Int,
    val stars: Int,
    val firstClear: Boolean,
    val firstClearReward: Int,
    val starImprovement: Int,
    val perfectBonus: Int,
    val replayReward: Int,
    val consumesBoostedReplay: Boolean,
    val doublableAmount: Int,
) {
    val total: Int get() = firstClearReward + starImprovement + perfectBonus + replayReward
}

/**
 * Zafer muhasebesini hesaplar. **Saf**: cuzdani degistirmez, yalnizca ne odenecegini
 * soyler. Uygulamak icin [applyLevelClear].
 */
fun resolveLevelClear(
    wallet: PlayerWallet,
    level: Int,
    livesLeft: Int,
    maxLives: Int,
    boostedReplaysUsedToday: Int = 0,
): LevelClearResult {
    val stars = starsFor(livesLeft, maxLives)
    require(stars > 0) { "zafer icin en az 1 yildiz gerekir (livesLeft=$livesLeft)" }

    val oldStars = wallet.starsOf(level)
    val firstClear = level !in wallet.clearedLevels

    val firstClearReward = if (firstClear) levelReward(level, stars) else 0
    val starImprovement = if (firstClear) 0 else starImprovementBonus(level, oldStars, stars)
    val perfect = isPerfectDefense(livesLeft, maxLives) && level !in wallet.perfectDefenseLevels
    val perfectBonus = if (perfect) EconomyConfig.PERFECT_DEFENSE_BONUS else 0

    val replay = if (firstClear) {
        0
    } else {
        replayReward(level, maxOf(oldStars, stars), boostedReplaysUsedToday)
    }
    val consumesBoosted = !firstClear &&
        boostedReplaysUsedToday < EconomyConfig.BOOSTED_REPLAYS_PER_DAY

    // R3 yalnizca "bu temizligin odulunu" katlar: ilk temizlik odulu veya
    // katlanabilir tekrar odulu. Kusursuz Savunma madalyasi ve yildiz farki
    // tek seferlik kalemlerdir, reklamla katlanmaz.
    val doublable = firstClearReward +
        if (!firstClear && replayRewardIsDoublable(boostedReplaysUsedToday)) replay else 0

    return LevelClearResult(
        level = level,
        stars = stars,
        firstClear = firstClear,
        firstClearReward = firstClearReward,
        starImprovement = starImprovement,
        perfectBonus = perfectBonus,
        replayReward = replay,
        consumesBoostedReplay = consumesBoosted,
        doublableAmount = doublable,
    )
}

/** [resolveLevelClear] sonucunu cuzdana uygular. Yildiz asla dusmez (GDD H.2). */
fun applyLevelClear(wallet: PlayerWallet, result: LevelClearResult): PlayerWallet {
    val level = result.level
    val newStars = maxOf(wallet.starsOf(level), result.stars)
    return wallet.credited(result.total).copy(
        clearedLevels = wallet.clearedLevels + level,
        bestStars = wallet.bestStars + (level to newStars),
        perfectDefenseLevels = if (result.perfectBonus > 0) {
            wallet.perfectDefenseLevels + level
        } else {
            wallet.perfectDefenseLevels
        },
    )
}

// =====================================================================================
// Rewarded reklam ekonomisi (GDD G.3 / G.4)
// =====================================================================================

/** Rewarded gosterim sonucu — GDD G.4'un DORT dali. */
enum class AdOutcome {
    /** `onUserEarnedReward` */
    REWARD_EARNED,

    /** `onAdDismissedWithoutReward` */
    DISMISSED,

    /** `onAdFailedToLoad` / `onAdFailedToShow` — no-fill dahil */
    NO_FILL,

    /** 5 sn yukleme timeout'u */
    TIMEOUT,
    ;

    val isFilled: Boolean get() = this == REWARD_EARNED
}

/** R1 "Tedarik Talebi" gunluk durumu. Gun donusunde sifirlanir. */
data class RequisitionState(
    val filledViewsToday: Int = 0,
    val coinsPaidToday: Int = 0,
) {
    init {
        require(filledViewsToday >= 0 && coinsPaidToday >= 0) { "negatif R1 sayaci" }
    }
}

data class RequisitionGrant(
    val coins: Int,
    val consumedFilledView: Boolean,
    val newState: RequisitionState,
    val fallback: Boolean,
    /** Butce tukendiyse UI notr mesaj gosterir; **hicbir ilerleme yolu kapanmaz**. */
    val budgetExhausted: Boolean,
)

/**
 * R1 dolu-reklam odulu (BAYRAK F-11, [EconomyConfig.R1_ADAPTIVE_REWARD_ENABLED]).
 *
 * Bayrak kapaliyken **her zaman** sabit [EconomyConfig.R1_REWARD_FILLED] doner —
 * yani varsayilan davranis Faz 9 ile birebir aynidir.
 *
 * Bayrak acikken odul oyuncunun **siradaki meta rank'inin** fiyatina gore olceklenir:
 * `clamp(round10(fiyat x 0,25), 150, 250)`. Taban sabit odule esittir, dolayisiyla
 * olceklenme oyuncuyu ASLA bugunkunden kotu duruma dusurmez.
 *
 * **Enflasyon YOK:** gunluk toplam her iki durumda da [EconomyConfig.R1_COIN_BUDGET_PER_DAY]
 * ile sinirli; olceklenme yalnizca ayni butceyi yeniden dagitir.
 *
 * @param nextRankPrice oyuncunun en ucuz satin alabilir rank'inin fiyati; bilinmiyorsa 0.
 */
fun requisitionFilledReward(nextRankPrice: Int = 0): Int {
    if (!EconomyConfig.R1_ADAPTIVE_REWARD_ENABLED || nextRankPrice <= 0) {
        return EconomyConfig.R1_REWARD_FILLED
    }
    val scaled = roundToTen(nextRankPrice * EconomyConfig.R1_SCALE_OF_NEXT_RANK)
    return scaled.coerceIn(EconomyConfig.R1_REWARD_FILLED_MIN, EconomyConfig.R1_REWARD_FILLED_MAX)
}

/**
 * Oyuncunun **en ucuz** siradaki rank fiyati; agac maksta `null`.
 * [requisitionFilledReward] icin girdi; UI "1 reklam = siradaki yukseltmenin %X'i"
 * mesajini da bundan uretir.
 */
fun cheapestNextRankPrice(upgrades: MetaUpgrades): Int? =
    UpgradeLine.entries.mapNotNull { nextRankPrice(it, upgrades) }.minOrNull()

/**
 * R1 odul dagitimi.
 *
 * - Dolu reklam: +[requisitionFilledReward] (varsayilan 150), gunluk dolu-gosterim
 *   hakkini tuketir.
 * - No-fill / kapatma / hata / timeout: +50, **gunluk hakki TUKETMEZ**, cooldown yok.
 * - Her iki dal da gunluk **coin butcesine** (450) tabidir — sonsuz +50 dongusunu
 *   kapatan tek kisit budur (BAYRAK F-6). Butce bittiginde buton hala tiklanabilir,
 *   hicbir ilerleme engellenmez; rezerv kilidi bolum kilidini zaten garanti eder.
 *
 * @param nextRankPrice yalnizca BAYRAK F-11 acikken kullanilir; 0 = sabit odul.
 */
fun grantRequisition(
    state: RequisitionState,
    outcome: AdOutcome,
    nextRankPrice: Int = 0,
): RequisitionGrant {
    val remainingBudget = maxOf(0, EconomyConfig.R1_COIN_BUDGET_PER_DAY - state.coinsPaidToday)
    if (remainingBudget == 0) {
        return RequisitionGrant(
            coins = 0,
            consumedFilledView = false,
            newState = state,
            fallback = !outcome.isFilled,
            budgetExhausted = true,
        )
    }

    val rightsLeft = state.filledViewsToday < EconomyConfig.R1_VIEWS_PER_DAY
    return if (outcome.isFilled && rightsLeft) {
        val amount = minOf(requisitionFilledReward(nextRankPrice), remainingBudget)
        RequisitionGrant(
            coins = amount,
            consumedFilledView = true,
            newState = state.copy(
                filledViewsToday = state.filledViewsToday + 1,
                coinsPaidToday = state.coinsPaidToday + amount,
            ),
            fallback = false,
            budgetExhausted = false,
        )
    } else {
        val amount = minOf(EconomyConfig.R1_REWARD_FALLBACK, remainingBudget)
        RequisitionGrant(
            coins = amount,
            consumedFilledView = false,
            newState = state.copy(coinsPaidToday = state.coinsPaidToday + amount),
            fallback = true,
            budgetExhausted = false,
        )
    }
}

/**
 * R3 "Cift Odeme". **Taban odul reklam GOSTERILMEDEN ONCE yazilir** (GDD G.4);
 * bu fonksiyon yalnizca **ek katmani** dondurur.
 *
 * @param doublableAmount [LevelClearResult.doublableAmount].
 */
fun doublePayoutBonus(doublableAmount: Int, outcome: AdOutcome): Int {
    require(doublableAmount >= 0) { "negatif odul" }
    if (!outcome.isFilled) return 0
    return doublableAmount * (EconomyConfig.R3_MULTIPLIER - 1)
}
