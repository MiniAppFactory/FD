package com.miniappfactory.frontlinedefender.game.economy

/**
 * Faz 9 — GOREV SISTEMI (GDD E) ve SIFIRLAMA TAKVIMI (GDD E.4).
 *
 * **Android importu YOK.** Saat okumasi platform katmaninda yapilir ve buraya
 * [ClockSample] olarak *hazir* verilir; bu dosya `System.currentTimeMillis`,
 * `SystemClock` veya `TimeZone` cagirmaz. Boylece tarih hilesi savunmasi
 * Robolectric olmadan, milisaniyeler icinde test edilebilir.
 *
 * Determinizm: gunluk gorev secimi `epochDay` + `seed` uzerinden **saf**tir ve
 * [Math.floorMod] kullanir. Kotlin `%` negatif `epochDay`/negatif hash icin negatif
 * doner ve `IndexOutOfBounds` uretir — cihaz saatini 1970 oncesine alan oyuncu
 * uygulamayi cokerterdi.
 */

// =====================================================================================
// Gorev modeli
// =====================================================================================

/** Gunluk gorevin 3 slotu. Her slot AYRI havuzdan cekilir (GDD E.1). */
enum class MissionSlot { PARTICIPATION, VOLUME, SKILL }

enum class MissionCadence { DAILY, WEEKLY }

/** Gorev sayaclarinin baglandigi oynanis olaylari. */
enum class MissionType {
    // Slot 1 — Katilim
    COMPLETE_ANY_LEVEL,
    SKIP_PREP_TIMER,

    // Slot 2 — Hacim
    KILL_ENEMIES,
    KILL_ARMORED,
    KILL_TANKS,
    BUILD_TOWERS,
    UPGRADE_TOWERS,
    EARN_SUPPLY_IN_ONE_BATTLE,

    // Slot 3 — Beceri
    CLEAR_WITH_THREE_STARS,
    CLEAR_WITHOUT_SELLING,
    CLEAR_WITH_HIGH_HEALTH,
    BUILD_ALL_TOWER_TYPES,
    CLEAR_AT_DOUBLE_SPEED,

    // Haftalik
    WEEKLY_LEVELS_COMPLETED,
    WEEKLY_STARS_EARNED,
}

/**
 * Gorev sablonu; havuzlar bunlardan olusur.
 *
 * @param minClearedLevels bu gorev yalnizca oyuncu bu kadar bolum temizlemisse havuza
 *   girer. GDD E.1: "acilmamis icerik isteyen gorev uretilmez" (kabul kriteri H.5).
 * @param minTowerTypes gereken acik kule tipi sayisi (MG=1, +CN=2, +Railgun=3, +Frost=4).
 */
data class MissionTemplate(
    val id: String,
    val type: MissionType,
    val slot: MissionSlot,
    val target: Int,
    val reward: Int,
    val minClearedLevels: Int = 0,
    val minTowerTypes: Int = 1,
)

/** Uretilmis, oyuncuya gosterilen gunluk gorev. */
data class Mission(
    val template: MissionTemplate,
    val progress: Int = 0,
    val claimed: Boolean = false,
) {
    val id: String get() = template.id
    val type: MissionType get() = template.type
    val target: Int get() = template.target
    val reward: Int get() = template.reward
    val isComplete: Boolean get() = progress >= template.target

    fun advanced(by: Int): Mission =
        copy(progress = minOf(template.target, progress + maxOf(0, by)))
}

/**
 * Haftalik gorev — GDD E.2'de **iki** tanedir ve toplam 1.100 coin verir.
 * Yapiyi degistirmek (ornek: 5 gorev x 3 kademe) GDD karari gerektirir;
 * oneri ECONOMY_SPEC 7 bayrak F-4'te durur, burada UYGULANMAMISTIR.
 */
data class WeeklyMission(
    val id: String,
    val type: MissionType,
    val target: Int,
    val reward: Int,
    val progress: Int = 0,
    val claimed: Boolean = false,
) {
    val isComplete: Boolean get() = progress >= target

    fun advanced(by: Int): WeeklyMission = copy(progress = minOf(target, progress + maxOf(0, by)))
}

object MissionPools {

    /** Slot 1 — Katilim. Oyunu acan herkes bitirir. Odul 60. */
    val PARTICIPATION: List<MissionTemplate> = listOf(
        MissionTemplate("d_p_clear1", MissionType.COMPLETE_ANY_LEVEL, MissionSlot.PARTICIPATION, 1, EconomyConfig.DAILY_REWARD_PARTICIPATION),
        MissionTemplate("d_p_clear2", MissionType.COMPLETE_ANY_LEVEL, MissionSlot.PARTICIPATION, 2, EconomyConfig.DAILY_REWARD_PARTICIPATION),
        MissionTemplate("d_p_kill40", MissionType.KILL_ENEMIES, MissionSlot.PARTICIPATION, 40, EconomyConfig.DAILY_REWARD_PARTICIPATION),
        MissionTemplate("d_p_skip3", MissionType.SKIP_PREP_TIMER, MissionSlot.PARTICIPATION, 3, EconomyConfig.DAILY_REWARD_PARTICIPATION),
    )

    /** Slot 2 — Hacim. Dogal oynanisla dolar, ekstra grind istemez. Odul 80. */
    val VOLUME: List<MissionTemplate> = listOf(
        MissionTemplate("d_v_kill120", MissionType.KILL_ENEMIES, MissionSlot.VOLUME, 120, EconomyConfig.DAILY_REWARD_VOLUME),
        MissionTemplate("d_v_build15", MissionType.BUILD_TOWERS, MissionSlot.VOLUME, 15, EconomyConfig.DAILY_REWARD_VOLUME),
        MissionTemplate("d_v_upg30", MissionType.UPGRADE_TOWERS, MissionSlot.VOLUME, 30, EconomyConfig.DAILY_REWARD_VOLUME),
        MissionTemplate("d_v_arm8", MissionType.KILL_ARMORED, MissionSlot.VOLUME, 8, EconomyConfig.DAILY_REWARD_VOLUME, minClearedLevels = 5),
        MissionTemplate("d_v_tank5", MissionType.KILL_TANKS, MissionSlot.VOLUME, 5, EconomyConfig.DAILY_REWARD_VOLUME, minClearedLevels = 7),
        MissionTemplate("d_v_supply2500", MissionType.EARN_SUPPLY_IN_ONE_BATTLE, MissionSlot.VOLUME, 2500, EconomyConfig.DAILY_REWARD_VOLUME, minClearedLevels = 6),
    )

    /** Slot 3 — Beceri. Kasitli oyun ister ama zorlamaz. Odul 120. */
    val SKILL: List<MissionTemplate> = listOf(
        MissionTemplate("d_s_three_star", MissionType.CLEAR_WITH_THREE_STARS, MissionSlot.SKILL, 1, EconomyConfig.DAILY_REWARD_SKILL),
        MissionTemplate("d_s_no_sell", MissionType.CLEAR_WITHOUT_SELLING, MissionSlot.SKILL, 1, EconomyConfig.DAILY_REWARD_SKILL, minClearedLevels = 4),
        MissionTemplate("d_s_health90", MissionType.CLEAR_WITH_HIGH_HEALTH, MissionSlot.SKILL, 1, EconomyConfig.DAILY_REWARD_SKILL),
        MissionTemplate("d_s_all_towers", MissionType.BUILD_ALL_TOWER_TYPES, MissionSlot.SKILL, 1, EconomyConfig.DAILY_REWARD_SKILL, minClearedLevels = 8, minTowerTypes = 4),
        MissionTemplate("d_s_double_speed", MissionType.CLEAR_AT_DOUBLE_SPEED, MissionSlot.SKILL, 1, EconomyConfig.DAILY_REWARD_SKILL),
    )

    fun poolFor(slot: MissionSlot): List<MissionTemplate> = when (slot) {
        MissionSlot.PARTICIPATION -> PARTICIPATION
        MissionSlot.VOLUME -> VOLUME
        MissionSlot.SKILL -> SKILL
    }

    /** GDD E.2 — iki haftalik gorev, 500 + 600 = 1.100. Sabit, rastgele degil. */
    fun weekly(): List<WeeklyMission> = listOf(
        WeeklyMission(
            id = "w_long_patrol",
            type = MissionType.WEEKLY_LEVELS_COMPLETED,
            target = EconomyConfig.WEEKLY_LONG_PATROL_TARGET,
            reward = EconomyConfig.WEEKLY_LONG_PATROL_REWARD,
        ),
        WeeklyMission(
            id = "w_elite_operator",
            type = MissionType.WEEKLY_STARS_EARNED,
            target = EconomyConfig.WEEKLY_ELITE_TARGET,
            reward = EconomyConfig.WEEKLY_ELITE_REWARD,
        ),
    )
}

// =====================================================================================
// Deterministik gunluk gorev secimi
// =====================================================================================

/** SplitMix64 finalizer — dagilimi duzgun, tasma guvenli, platformdan bagimsiz. */
internal fun mix64(value: Long): Long {
    var z = value
    z = (z xor (z ushr 33)) * -0x40A7B892E31B1A47L // 0xBF58476D1CE4E5B9
    z = (z xor (z ushr 29)) * -0x6B2FB644ECCEEE15L // 0x94D049BB133111EB
    return z xor (z ushr 32)
}

internal fun missionHash(seed: Long, epochDay: Long, slotOrdinal: Int, reroll: Int): Long =
    mix64(
        seed * -0x61c8864680b583ebL + // 0x9E3779B97F4A7C15
            epochDay * 0x100000001B3L +
            slotOrdinal.toLong() * 0x27D4EB2FL +
            reroll.toLong() * 0x1EF3L
    )

/**
 * **Deterministik indeks.** [Math.floorMod] ZORUNLUDUR: Kotlin `%` negatif
 * `epochDay` veya negatif hash icin negatif doner ve dizin hatasi verir.
 */
internal fun deterministicIndex(hash: Long, size: Int): Int {
    require(size > 0) { "bos havuzdan secim yapilamaz" }
    return Math.floorMod(hash, size.toLong()).toInt()
}

/** Havuzu oyuncunun actigi icerige gore filtreler (GDD E.1 / H.5). */
internal fun eligibleTemplates(
    pool: List<MissionTemplate>,
    clearedLevels: Int,
    unlockedTowerTypes: Int,
): List<MissionTemplate> {
    val filtered = pool.filter {
        clearedLevels >= it.minClearedLevels && unlockedTowerTypes >= it.minTowerTypes
    }
    // Her havuzda en az bir sablon minClearedLevels=0 / minTowerTypes=1 olacak sekilde
    // tasarlandi, yani bu dal olu. Yine de savunma: havuz ASLA bos donmez.
    return filtered.ifEmpty { listOf(pool.minByOrNull { it.minClearedLevels }!!) }
}

internal fun gcd(a: Int, b: Int): Int {
    var x = a
    var y = b
    while (y != 0) {
        val t = x % y
        x = y
        y = t
    }
    return x
}

/**
 * Havuz boyutuna gore ASAL adim. Gunluk indeks `offset + gun * adim` seklinde
 * ilerledigi icin bu iki ozelligi GARANTI eder:
 *
 * 1. `adim mod boyut != 0` -> **ardisik iki gun ayni gorevi vermez.**
 * 2. `gcd(adim, boyut) == 1` -> havuzun tamami donmeden hicbir gorev tekrar etmez,
 *    yani gorevler esit dagilir ve "hep ayni iki gorev" sikayeti olusmaz.
 *
 * Onceki tasarim "dun ne cikmissa bir sonrakine kay" seklindeydi; o kural
 * ardisiklik garantisi VERMIYORDU (dunun kendisi de kaydirilmis olabildigi icin
 * zincir kirilir ve gorev ust uste iki gun cikabilirdi). Regresyon testi:
 * `missionInASlotDoesNotRepeatFromYesterday`.
 */
internal fun coprimeStride(seed: Long, slotOrdinal: Int, size: Int): Int {
    if (size <= 1) return 0
    val start = deterministicIndex(missionHash(seed, -7L, slotOrdinal, 0), size - 1)
    for (i in 0 until size - 1) {
        val candidate = 1 + ((start + i) % (size - 1))
        if (gcd(candidate, size) == 1) return candidate
    }
    return 1 // 1 her zaman asaldir; buraya asla dusmez
}

private fun pickTemplate(
    slot: MissionSlot,
    epochDay: Long,
    seed: Long,
    reroll: Int,
    clearedLevels: Int,
    unlockedTowerTypes: Int,
): MissionTemplate {
    val eligible = eligibleTemplates(MissionPools.poolFor(slot), clearedLevels, unlockedTowerTypes)
    val size = eligible.size
    if (size == 1) return eligible[0]

    val offset = deterministicIndex(missionHash(seed, 0L, slot.ordinal, 0), size)
    val stride = coprimeStride(seed, slot.ordinal, size)
    // reroll, gunu bir adim ileri saymakla ayni: adim asal oldugu icin sonuc
    // KESIN olarak farkli bir gorevdir.
    val index = Math.floorMod(offset + (epochDay + reroll) * stride, size.toLong()).toInt()
    return eligible[index]
}

/**
 * Gunun 3 gorevini uretir. **Saf ve deterministik**: ayni (`epochDay`, `seed`,
 * ilerleme, reroll) girdisi HER ZAMAN ayni 3 gorevi verir; kalicilik katmani gorev
 * listesini saklamak zorunda degildir, yeniden uretebilir (yalnizca *ilerleme*
 * saklanir).
 *
 * Her slot ayri havuzdan cekilir (GDD E.1: "3 tanesi de ayni tip olmaz") ve dunun
 * secimi ayni slotta tekrar etmez.
 *
 * @param seed cihaz kurulumunda BIR KEZ uretilen kalici tohum.
 * @param rerollsBySlot slot basina kullanilmis yenileme sayisi (boyut 3).
 */
fun dailyMissions(
    epochDay: Long,
    seed: Long,
    clearedLevels: Int = 0,
    unlockedTowerTypes: Int = 1,
    rerollsBySlot: IntArray = IntArray(EconomyConfig.DAILY_MISSION_SLOTS),
): List<Mission> {
    require(rerollsBySlot.size == EconomyConfig.DAILY_MISSION_SLOTS) {
        "rerollsBySlot boyutu ${EconomyConfig.DAILY_MISSION_SLOTS} olmali"
    }
    return MissionSlot.entries.map { slot ->
        Mission(
            pickTemplate(
                slot, epochDay, seed, rerollsBySlot[slot.ordinal],
                clearedLevels, unlockedTowerTypes
            )
        )
    }
}

/** Belirli tipteki gorevlerin sayacini ilerletir. Motor/UI tek bu fonksiyonu cagirir. */
fun advanceMissions(missions: List<Mission>, type: MissionType, amount: Int): List<Mission> =
    missions.map { if (it.type == type) it.advanced(amount) else it }

fun advanceWeekly(missions: List<WeeklyMission>, type: MissionType, amount: Int): List<WeeklyMission> =
    missions.map { if (it.type == type) it.advanced(amount) else it }

/**
 * Gunluk gorev coin toplami; ucu de bitmisse +100 bonus (GDD E.1, tavan 360).
 * [rewardMultiplier] YALNIZCA saat suphesi damperi icindir (GDD E.4 kural 3);
 * bolum odulu, kilit veya yukseltmeye ASLA uygulanmaz.
 */
fun dailyMissionPayout(missions: List<Mission>, rewardMultiplier: Double = 1.0): Int {
    require(rewardMultiplier > 0.0) { "carpan pozitif olmali" }
    val base = missions.filter { it.isComplete }.sumOf { it.reward }
    val bonus = if (missions.size == EconomyConfig.DAILY_MISSION_SLOTS && missions.all { it.isComplete }) {
        EconomyConfig.DAILY_ALL_COMPLETE_BONUS
    } else {
        0
    }
    return Math.floor((base + bonus) * rewardMultiplier).toInt()
}

/** Haftalik gorev coin toplami. Damper burada da yalnizca GOREV oduluna uygulanir. */
fun weeklyMissionPayout(missions: List<WeeklyMission>, rewardMultiplier: Double = 1.0): Int {
    require(rewardMultiplier > 0.0) { "carpan pozitif olmali" }
    val base = missions.filter { it.isComplete }.sumOf { it.reward }
    return Math.floor(base * rewardMultiplier).toInt()
}

// =====================================================================================
// Sifirlama takvimi + tarih hilesi savunmasi (GDD E.4)
// =====================================================================================

/**
 * ISO hafta indeksi. epochDay 0 = 1970-01-01 Persembe, dolayisiyla Pazartesi hizasi
 * icin +3 kaydirma gerekir. [Math.floorDiv] negatif epochDay'de dogru (asagiya)
 * yuvarlar; Kotlin `/` sifira dogru yuvarlar ve hafta sinirini kaydirir.
 */
fun isoWeekIndex(epochDay: Long): Long = Math.floorDiv(epochDay + 3L, 7L)

/**
 * Platform katmanindan gelen saat ornegi. `epochDay` **yerel** gece yarisina gore
 * hesaplanir (seyahatte TZ degisimi mesrudur, GDD E.4 kural 6) ve bu saf modele
 * hazir verilir — burada `TimeZone` okumasi YAPILMAZ.
 */
data class ClockSample(
    val epochDay: Long,
    val wallClockMs: Long,
    val elapsedRealtimeMs: Long,
    val bootId: Long,
)

/** Gorev sisteminin kalici durumu. */
data class MissionState(
    val lastResetEpochDay: Long = Long.MIN_VALUE,
    val lastWeeklyResetWeek: Long = Long.MIN_VALUE,
    val lastSeenWallClockMs: Long = 0L,
    val lastSeenElapsedRealtimeMs: Long = 0L,
    val lastBootId: Long = 0L,
    /**
     * Boot icinde OLCULMUS gercek gecen sure toplami. Boot sinirinda offline kalinan
     * sure eklenmez — bu bilincli olarak oyuncunun lehinedir.
     *
     * Kaba tavan (GDD E.4 kural 4) bu saate gore isler, takvim gunune gore DEGIL:
     * aksi halde her gun oynayan mesru oyuncu 9. gunde kalici olarak damperlenirdi.
     */
    val accumulatedRealtimeMs: Long = 0L,
    val resetWindowStartRealtimeMs: Long = 0L,
    val dailyResetsInWindow: Int = 0,
    val clockSuspect: Boolean = false,
    val rewardMultiplier: Double = 1.0,
    val rerollsUsedToday: Int = 0,
    val daily: List<Mission> = emptyList(),
    val weekly: List<WeeklyMission> = MissionPools.weekly(),
) {
    val hasEverReset: Boolean get() = lastResetEpochDay != Long.MIN_VALUE
}

/** Sifirlama degerlendirmesi sonucu. */
data class ResetDecision(
    val dailyReset: Boolean,
    val weeklyReset: Boolean,
    val rewardMultiplier: Double,
    val clockSuspect: Boolean,
    /** Saat GERIYE alindigi icin sifirlama donduruldu mu? */
    val frozenByBackwardClock: Boolean,
    val newState: MissionState,
)

/**
 * **Tarih hilesine karsi TEK savunma** (offline oyunda baska yok).
 * GDD E.4'un kodla yazilmis hali. Amac farming'i durdurmak; amac DEGIL oyuncuyu
 * cezalandirmak.
 *
 * 1. `epochDay == lastResetEpochDay` -> sifirlama yok.
 * 2. `epochDay < lastResetEpochDay` (saat GERI alindi) -> **sifirlama YOK, odul YOK**,
 *    `clockSuspect = true`. Mevcut gorev ilerlemesi KORUNUR, oyun tam oynanir kalir.
 *    Kayitli gun geriye YAZILMAZ; `epochDay` o gunu gecene kadar sifirlama donar.
 * 3. `epochDay > lastResetEpochDay` -> sifirlanir ama **en fazla BIR** sifirlama:
 *    `lastResetEpochDay = epochDay` yazilir, aradaki gunler biriktirilmez.
 * 4. Ayni boot icinde duvar saati 20 saatten fazla ilerlemisken `elapsedRealtime`
 *    eslik etmediyse -> sifirlama YINE yapilir (oyuncu bloke edilmez) ama o dongunun
 *    **gorev** odulleri %50'ye duser ve notr toast gosterilir.
 * 5. 7 gercek gun icinde 8'den fazla sifirlama -> ayni %50 damper.
 * 6. Damper YALNIZCA gorev oduludur. Bolum odulu, kilit, bakiye, yukseltme ve
 *    kampanya ilerlemesi hicbir kosulda etkilenmez.
 */
fun evaluateReset(state: MissionState, sample: ClockSample): ResetDecision {
    val sameBoot = state.lastBootId == sample.bootId && state.lastSeenElapsedRealtimeMs > 0L

    val wentBackward = state.hasEverReset && (
        sample.epochDay < state.lastResetEpochDay ||
            (sameBoot && sample.wallClockMs <
                state.lastSeenWallClockMs - EconomyConfig.CLOCK_BACKWARD_TOLERANCE_MS)
        )

    // Gercek gecen sureyi biriktir. `elapsedRealtime` yalnizca ayni boot icinde
    // karsilastirilabilir; boot degisiminde fark eklenmez (oyuncunun lehine).
    val realtimeGain = if (sameBoot) {
        maxOf(0L, sample.elapsedRealtimeMs - state.lastSeenElapsedRealtimeMs)
    } else {
        0L
    }
    val accumulated = state.accumulatedRealtimeMs + realtimeGain

    val touched = state.copy(
        lastSeenWallClockMs = sample.wallClockMs,
        lastSeenElapsedRealtimeMs = sample.elapsedRealtimeMs,
        lastBootId = sample.bootId,
        accumulatedRealtimeMs = accumulated,
    )

    // --- Kural 2: saat geri alindi -> DON. Sifirlama yok, odul yok, ilerleme korunur.
    if (wentBackward) {
        return ResetDecision(
            dailyReset = false,
            weeklyReset = false,
            rewardMultiplier = state.rewardMultiplier,
            clockSuspect = true,
            frozenByBackwardClock = true,
            newState = touched.copy(clockSuspect = true),
        )
    }

    // --- Ilk calistirma: sifirlama yapilir, suphe yok.
    if (!state.hasEverReset) {
        return ResetDecision(
            dailyReset = true,
            weeklyReset = true,
            rewardMultiplier = 1.0,
            clockSuspect = false,
            frozenByBackwardClock = false,
            newState = touched.copy(
                lastResetEpochDay = sample.epochDay,
                lastWeeklyResetWeek = isoWeekIndex(sample.epochDay),
                dailyResetsInWindow = 1,
                resetWindowStartRealtimeMs = accumulated,
                clockSuspect = false,
                rewardMultiplier = 1.0,
                rerollsUsedToday = 0,
            ),
        )
    }

    // --- Kural 1: ayni gun -> sifirlama yok.
    if (sample.epochDay == state.lastResetEpochDay) {
        return ResetDecision(
            dailyReset = false,
            weeklyReset = false,
            rewardMultiplier = state.rewardMultiplier,
            clockSuspect = state.clockSuspect,
            frozenByBackwardClock = false,
            newState = touched,
        )
    }

    // --- Kural 3/4: ileriye gidis. EN FAZLA BIR sifirlama.
    val wallDelta = sample.wallClockMs - state.lastSeenWallClockMs
    val elapsedDelta = if (sameBoot) {
        sample.elapsedRealtimeMs - state.lastSeenElapsedRealtimeMs
    } else {
        Long.MAX_VALUE
    }
    val threshold = EconomyConfig.CLOCK_FORWARD_SUSPECT_HOURS * EconomyConfig.MS_PER_HOUR
    val implausibleJump = sameBoot && wallDelta > threshold && elapsedDelta < threshold

    // Kural 5 — kaba tavan, GERCEK saat penceresinde: 7 gun gercek sure icinde en
    // fazla 8 sifirlama. Pencere kaydikca sayac sifirlanir, boylece her gun oynayan
    // mesru oyuncu ASLA damperlenmez (yanlis pozitif hedefi < %0,5 DAU).
    val windowElapsed = accumulated - state.resetWindowStartRealtimeMs
    val windowExpired = windowElapsed >= 7L * EconomyConfig.MS_PER_DAY
    val resetsInWindow = if (windowExpired) {
        1
    } else {
        state.dailyResetsInWindow + EconomyConfig.MAX_RESETS_PER_EVALUATION
    }
    val windowStart = if (windowExpired) accumulated else state.resetWindowStartRealtimeMs

    val overCap = resetsInWindow > EconomyConfig.MAX_DAILY_RESETS_PER_7_DAYS
    val suspect = implausibleJump || overCap
    val multiplier = if (suspect) EconomyConfig.SUSPECT_REWARD_MULTIPLIER else 1.0

    val newWeek = isoWeekIndex(sample.epochDay)
    return ResetDecision(
        dailyReset = true,
        weeklyReset = newWeek > state.lastWeeklyResetWeek,
        rewardMultiplier = multiplier,
        clockSuspect = suspect,
        frozenByBackwardClock = false,
        newState = touched.copy(
            lastResetEpochDay = sample.epochDay,
            lastWeeklyResetWeek = maxOf(state.lastWeeklyResetWeek, newWeek),
            dailyResetsInWindow = resetsInWindow,
            resetWindowStartRealtimeMs = windowStart,
            clockSuspect = suspect,
            rewardMultiplier = multiplier,
            rerollsUsedToday = 0,
        ),
    )
}

/**
 * Gorev yenileme hakki (GDD E.1: gunde 1 bedava yenileme).
 * **Tamamlanmis gorev yenilenemez** — aksi halde ayni odul iki kez alinabilirdi.
 */
fun canReroll(state: MissionState, slot: MissionSlot): Boolean {
    if (state.rerollsUsedToday >= EconomyConfig.DAILY_REROLLS_PER_DAY) return false
    val mission = state.daily.firstOrNull { it.template.slot == slot } ?: return false
    return !mission.isComplete
}
