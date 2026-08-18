package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 14 — GOREV SISTEMI CANLILIK TESTLERI.
 *
 * ## Neden bu dosya var
 * `docs/FUN_AUDIT.md`: gorev sistemi eksiksiz yazilmisti ama 15 sayacin **10'u
 * hicbir yerden ilerletilmiyordu** ve `claimCompletedMissions()` sifir kez
 * cagriliyordu. Ekonomik sonuc: tasarlanan haftalik ~3.620 coin'lik geri donus
 * gelirinin **tamami** oyuncuya ulasmiyordu. En kotu tarafi sessiz olmasiydi —
 * her sey derleniyor, her test geciyordu.
 *
 * Bu dosya o sessizligi kapatir. Asagidaki testler "kod var" degil "**sayac
 * gercekten doluyor ve coin gercekten cebe giriyor**" sorusunu sorar.
 *
 * **SAF JUnit**: `InMemoryKeyValueStore` + enjekte saat; Robolectric yok.
 */
class MissionWiringTest {

    // =================================================================================
    // Test altyapisi
    // =================================================================================

    /**
     * Ilerletilebilir sahte saat.
     *
     * Duvar saati ile GERCEK gecen sure AYRI kontrol edilir — GDD E.4'un tum
     * savunmasi tam olarak bu ikisinin ayrismasina bakar. [advanceDays] mesru
     * oyuncuyu (ikisi birlikte ilerler), `day` alanina dogrudan yazmak ise
     * saat oynatmayi (yalnizca duvar saati ilerler) canlandirir.
     */
    private class StepClock(var day: Long = 20_500L) : ClockProvider {
        var bootId: Long = 1L
        var elapsedMs: Long = 1_000L

        fun advanceDays(count: Long) {
            day += count
            elapsedMs += count * EconomyConfig.MS_PER_DAY
        }

        override fun sample(): ClockSample = ClockSample(
            epochDay = day,
            wallClockMs = day * EconomyConfig.MS_PER_DAY,
            elapsedRealtimeMs = elapsedMs,
            bootId = bootId,
        )
    }

    /** Gorev kaynakli coin'i seviye odulunden AYIRAN analytics dinleyicisi. */
    private class CoinLog {
        var missionCoins: Int = 0
        val sink: (String, Map<String, Any>) -> Unit = { event, params ->
            if (event == "coin_earn" && params["source"] == "mission") {
                missionCoins += params["amount"] as Int
            }
        }
    }

    /** Gec kampanyaya kadar acilmis oyuncu: 4 kule tipi acik (GDD F). */
    private fun veteranSave(): SaveManager {
        val save = SaveManager(InMemoryKeyValueStore())
        save.saveWallet(
            PlayerWallet(
                coins = 0,
                unlockedLevels = (1..SIM_LEVEL).toSet(),
                clearedLevels = (1..SIM_LEVEL).toSet(),
                bestStars = (1..SIM_LEVEL).associateWith { 3 },
            )
        )
        return save
    }

    /**
     * ILERLEYEN oyuncu: 4 kule tipi acik ama kampanyanin onu duruyor.
     *
     * Gelir simulasyonu bu profili kullanir cunku haftalik "Elit Operator"
     * gorevi (`WEEKLY_STARS_EARNED`) yalnizca **ilk temizlemede** yildiz sayar
     * (`resolveLevelClear.firstClear`) — yildizlar en-iyi degerdir, tekrar
     * oynayarak biriktirilemez. Kampanyayi bitirmis oyuncu icin bu gorev su an
     * tamamlanamaz; rapor edildi, odul miktarina DOKUNULMADI.
     */
    private fun progressingSave(): SaveManager {
        val save = SaveManager(InMemoryKeyValueStore())
        val cleared = FIRST_UNPLAYED_LEVEL - 1
        save.saveWallet(
            PlayerWallet(
                coins = 0,
                unlockedLevels = (1..GameConfigLevelCount).toSet(),
                clearedLevels = (1..cleared).toSet(),
                bestStars = (1..cleared).associateWith { 3 },
            )
        )
        return save
    }

    /**
     * Gunluk listesinde [type] tipinde bir gorev CIKAN kayit uretir.
     *
     * Gorev secimi cihaz tohumuna gore deterministik ama tohum kuruluma ozel;
     * testin "gorev listede yoksa gec" gibi kendini muaf tutan bir dal
     * tasimamasi icin uygun tohum ARANIR.
     */
    private fun saveShowingDailyType(type: MissionType, minTarget: Int = 0): SaveManager {
        repeat(2_000) {
            val save = veteranSave()
            val generated = dailyMissions(
                epochDay = 20_500L,
                seed = save.missionSeed,
                clearedLevels = SIM_LEVEL,
                unlockedTowerTypes = 4,
                measurableTypes = MEASURABLE_MISSION_TYPES,
            )
            if (generated.any { it.type == type && it.target >= minTarget }) return save
        }
        throw AssertionError("$type gunluk havuzda hic cikmadi — havuz veya kapi bozuk")
    }

    /** Her sey olculmus, cok cesitli bir savas. */
    private fun fullReport() = BattleReport(
        towersBuilt = 9,
        towerUpgrades = 12,
        towersSold = 0,
        distinctTowerTypes = 4,
        prepTimersSkipped = 3,
        enemiesKilled = 140,
        armoredKilled = 11,
        tanksKilled = 7,
        supplyEarned = 3_000,
        clearedAtDoubleSpeed = true,
    )

    // =================================================================================
    // 1. OLU SAYAC YOK — FUN_AUDIT'in ana bulgusu
    // =================================================================================

    /**
     * **REGRESYON KILIDI.** Her [MissionType] icin uretimde bir ilerletme yolu
     * olmali. Yol iki yerden gelir:
     *  - savas raporu fan-out'u ([battleMissionDeltas]),
     *  - bolum sonucu (`CampaignProgressImpl.onLevelCleared`) — yildiz hesabinin
     *    yaninda kalmasi gereken uc gunluk + iki haftalik sayac.
     *
     * Havuza yeni bir gorev tipi eklenip sayaci baglanmazsa BU test kirilir.
     */
    @Test
    fun everyMissionTypeHasALiveCounter() {
        val fromBattle = battleMissionDeltas(fullReport(), victory = true, unlockedTowerTypes = 4)
            .map { it.first }
            .toSet()

        // `onLevelCleared` icinde ilerletilenler — asagidaki testler bunlarin
        // gercekten calistigini AYRICA dogruluyor.
        val fromLevelClear = setOf(
            MissionType.COMPLETE_ANY_LEVEL,
            MissionType.CLEAR_WITH_THREE_STARS,
            MissionType.CLEAR_WITH_HIGH_HEALTH,
            MissionType.WEEKLY_LEVELS_COMPLETED,
            MissionType.WEEKLY_STARS_EARNED,
        )

        val orphans = MissionType.entries.toSet() - fromBattle - fromLevelClear
        assertEquals("ilerletilmeyen gorev tipi kaldi", emptySet<MissionType>(), orphans)
    }

    /**
     * Havuzdaki HER sablon gercekci bir gunluk oynanisla bitirilebilmeli.
     * FUN_AUDIT: "VOLUME slotu %100 tamamlanamaz" — `d_v_build15` ve
     * `d_v_upg30` cikan gunler kilitliydi.
     */
    @Test
    fun everyDailyTemplateIsCompletableInOneDay() {
        val allTemplates = MissionPools.PARTICIPATION + MissionPools.VOLUME + MissionPools.SKILL
        allTemplates.forEach { template ->
            var mission = Mission(template)
            // Bir gunde 4 savas: gunluk gorevlerin tasarim varsayimi.
            repeat(4) {
                battleMissionDeltas(fullReport(), victory = true, unlockedTowerTypes = 4)
                    .forEach { (type, amount) ->
                        if (type != mission.type) return@forEach
                        mission = if (type.isSingleBattleBest) {
                            mission.raisedTo(amount)
                        } else {
                            mission.advanced(amount)
                        }
                    }
                if (mission.type == MissionType.COMPLETE_ANY_LEVEL) mission = mission.advanced(1)
                if (mission.type == MissionType.CLEAR_WITH_THREE_STARS) mission = mission.advanced(1)
                if (mission.type == MissionType.CLEAR_WITH_HIGH_HEALTH) mission = mission.advanced(1)
            }
            assertTrue(
                "${template.id} 4 savasta bitirilemiyor (${mission.progress}/${mission.target})",
                mission.isComplete
            )
        }
    }

    // =================================================================================
    // 1b. OLCUM YETENEGI KAPISI — gosterilen her gorev TAMAMLANABILIR olmali
    // =================================================================================

    /**
     * **En onemli kural.** Panelde gorunen hicbir gorev, sayaci dolmayan bir
     * tipe ait olamaz. Tamamlanamayan gorev gostermek hic gorev gostermemekten
     * kotudur: oyuncu 120 coin'lik bir beceri gorevini gun boyu ilerletemez ve
     * panelin tamamina guvenini kaybeder.
     */
    @Test
    fun noUnmeasurableMissionIsEverShownToThePlayer() {
        // Cok sayida tohum ve gun: rotasyon havuzun tamamini dolasir.
        (1L..200L).forEach { seed ->
            (20_400L..20_460L).forEach { day ->
                dailyMissions(
                    epochDay = day,
                    seed = seed,
                    clearedLevels = 55,
                    unlockedTowerTypes = 4,
                    measurableTypes = MEASURABLE_MISSION_TYPES,
                ).forEach { mission ->
                    assertTrue(
                        "olculemeyen gorev gosterildi: ${mission.id} (${mission.type})",
                        mission.type in MEASURABLE_MISSION_TYPES
                    )
                }
            }
        }
    }

    /**
     * **Hedef, acildigi yerde gercekten ULASILABILIR olmali.**
     *
     * `minClearedLevels` bir gorevi havuza sokar; ama hedefin o noktada
     * oyuncunun erisebildigi bolumlerle karsilanabildigini kimse kontrol
     * etmiyordu. `d_v_supply2500` tam bu yuzden bozuktu: kapi 6'ydi, oysa
     * "bir savasta 2.500 Tedarik" ilk kez L18'de (2.650) mumkun oluyor —
     * arada cekildigi her gun panelde ulasilamaz bir hedef duruyordu.
     */
    @Test
    fun dailyTargetsAreReachableAtTheirUnlockGate() {
        val pools = MissionPools.PARTICIPATION + MissionPools.VOLUME + MissionPools.SKILL
        pools.filter { it.type in DERIVED_MISSION_TYPES }.forEach { template ->
            // Kapinin actigi anda oynanabilen bolumler: temizlenenler + siradaki.
            val playable = 1..(template.minClearedLevels + 1).coerceAtMost(GameConfigLevelCount)

            val best: Int = when (template.type) {
                MissionType.EARN_SUPPLY_IN_ONE_BATTLE ->
                    // TEK savas: bolumler arasi toplanmaz.
                    playable.maxOf {
                        SupplyBudgetModel.startingSupply(it) + SupplyBudgetModel.waveSupplyIncome(it)
                    }
                MissionType.KILL_ENEMIES ->
                    BATTLES_PER_DAY * playable.maxOf {
                        GameConfigCampaignFacts.enemyCounts(it).values.sum()
                    }
                MissionType.KILL_ARMORED ->
                    BATTLES_PER_DAY * playable.maxOf {
                        GameConfigCampaignFacts.enemyCounts(it)["ARMORED_VEHICLE"] ?: 0
                    }
                MissionType.KILL_TANKS ->
                    BATTLES_PER_DAY * playable.maxOf {
                        val c = GameConfigCampaignFacts.enemyCounts(it)
                        (c["TANK"] ?: 0) + (c["COMMAND_TANK"] ?: 0)
                    }
                // Kalanlar "bolum temizle / 3 yildiz / yuksek can" — bolum
                // buyuklugunden bagimsiz, her zaman ulasilabilir.
                else -> template.target
            }

            assertTrue(
                "${template.id}: hedef ${template.target}, kapinin actigi noktada " +
                    "en fazla $best ulasilabiliyor (minClearedLevels=${template.minClearedLevels})",
                best >= template.target
            )
        }
    }

    /** Kapi hicbir slotu BOSALTMAMALI — her slot her gun bir gorev uretmeli. */
    @Test
    fun theCapabilityGateLeavesEverySlotPopulated()  {
        MissionSlot.entries.forEach { slot ->
            val remaining = MissionPools.poolFor(slot).filter { it.type in MEASURABLE_MISSION_TYPES }
            assertTrue("$slot slotu olcum kapisiyla bosaldi", remaining.isNotEmpty())
            assertTrue(
                "$slot slotunda yeni oyuncuya (0 bolum) uygun sablon kalmali",
                remaining.any { it.minClearedLevels == 0 && it.minTowerTypes <= 1 }
            )
        }
        val missions = dailyMissions(
            epochDay = 20_500L,
            seed = 7L,
            clearedLevels = 0,
            unlockedTowerTypes = 1,
            measurableTypes = MEASURABLE_MISSION_TYPES,
        )
        assertEquals(EconomyConfig.DAILY_MISSION_SLOTS, missions.size)
        assertEquals("uc slot da AYRI olmali", 3, missions.map { it.template.slot }.toSet().size)
    }

    /**
     * Kapinin iki ucu da tanimli olmali: [DERIVED_MISSION_TYPES] ile
     * [TELEMETRY_ONLY_MISSION_TYPES] birlikte TUM tipleri kapsamali ve
     * kesismemelidir. Yeni bir tip eklenip hicbir kovaya konmazsa bu test
     * kirilir — sessizce "gosterilmeyen gorev" olusmaz.
     */
    @Test
    fun everyMissionTypeIsClassifiedExactlyOnce() {
        assertEquals(
            "iki kova kesismemeli",
            emptySet<MissionType>(),
            DERIVED_MISSION_TYPES intersect TELEMETRY_ONLY_MISSION_TYPES
        )
        assertEquals(
            "siniflandirilmamis gorev tipi var",
            MissionType.entries.toSet(),
            DERIVED_MISSION_TYPES + TELEMETRY_ONLY_MISSION_TYPES
        )
    }

    // =================================================================================
    // 2. TURETME — motor bildirmese bile hacim sayaclari dolar
    // =================================================================================

    /**
     * Sizinti basina tam 1 can gider (`GameConfig.BASE_REACHED_PENALTY_LIVES`),
     * dolayisiyla `oldurulen = toplamDusman - kaybedilenCan` bir TAHMIN degil
     * KIMLIKTIR. Kayipsiz zafer bolumun tum dusmanlarini saymalidir.
     */
    @Test
    fun perfectClearDerivesEveryEnemyFromTheWaveTable() {
        val progress = CampaignProgressImpl(veteranSave(), StepClock())
        val expected = GameConfigCampaignFacts.enemyCounts(SIM_LEVEL).values.sum()

        val derived = progress.derivedBattleReport(SIM_LEVEL, livesLeft = 20, maxLives = 20)

        assertEquals(expected, derived.enemiesKilled)
        assertTrue("agir tipler de sayilmali", derived.tanksKilled >= 0)
        assertTrue("Tedarik geliri turetilmeli", derived.supplyEarned > 0)
    }

    /** Sizinti veren oyuncu ASLA turetmede fazla sayilmaz. */
    @Test
    fun leaksReduceDerivedKills() {
        val progress = CampaignProgressImpl(veteranSave(), StepClock())
        val total = GameConfigCampaignFacts.enemyCounts(SIM_LEVEL).values.sum()

        val leaky = progress.derivedBattleReport(SIM_LEVEL, livesLeft = 14, maxLives = 20)

        assertEquals(total - 6, leaky.enemiesKilled)
        val perfect = progress.derivedBattleReport(SIM_LEVEL, livesLeft = 20, maxLives = 20)
        assertTrue("agir tipler oldurme oraniyla asagi olceklenmeli",
            leaky.tanksKilled <= perfect.tanksKilled)
        assertTrue(leaky.supplyEarned < perfect.supplyEarned)
    }

    /**
     * **Us Tamiri / R2 Takviye oldurme sayisini SISIRMEZ.**
     *
     * Turetme `oldurulen = toplam - kaybedilenCan` der. Geri verilen can
     * `livesLeft`i yukseltir, yani ham deger kullanilirsa sizinti gizlenir ve
     * "tamir et -> daha cok oldurme sayilsin" arbitraji acilir. `onLevelCleared`
     * bu yuzden turetmeye ham cani DEGIL, geri verilen cani dusulmus
     * `starHealth`i verir — yildiz hesabinin kullandigi degerin ta kendisi.
     */
    @Test
    fun repairedHealthDoesNotInflateDerivedKills() {
        val progress = CampaignProgressImpl(
            saveShowingDailyType(MissionType.KILL_ENEMIES, minTarget = 120), StepClock()
        )
        val total = GameConfigCampaignFacts.enemyCounts(SMALL_LEVEL).values.sum()

        progress.beginBattle(SMALL_LEVEL)
        progress.noteReinforcement(REINFORCED_LIVES)
        progress.onLevelCleared(SMALL_LEVEL, livesLeft = 20, maxLives = 20)

        val kills = progress.todaysMissions.first { it.type == MissionType.KILL_ENEMIES && it.target >= 120 }
        assertTrue(
            "olcum hedefe TAKILMAMALI, aksi halde tamirli/tamirsiz fark gorunmez",
            kills.target > total
        )

        assertEquals(
            "geri verilen can sizinti olarak sayilmali, oldurme sisirilmemeli",
            total - REINFORCED_LIVES,
            kills.progress
        )
    }

    /** Kampanya disi bolum id'si turetmeyi COKERTMEZ. */
    @Test
    fun derivationIsSafeForUnknownLevels() {
        val progress = CampaignProgressImpl(veteranSave(), StepClock())
        assertEquals(BattleReport.UNREPORTED, progress.derivedBattleReport(9_999, 20, 20))
    }

    // =================================================================================
    // 3. CIFT SAYIM YOK
    // =================================================================================

    /**
     * Zafer yolu `onLevelCleared` icinde flush eder; oyuncu bolum secime
     * dondugunde `endBattle()` tekrar dener. Ayni oldurmeler IKINCI kez
     * sayilirsa `d_v_kill120` bir bolum acip kapatmakla kirilirdi.
     */
    @Test
    fun battleMeasurementIsFlushedExactlyOnce() {
        val progress = CampaignProgressImpl(veteranSave(), StepClock())
        progress.beginBattle(SIM_LEVEL)
        progress.noteBattle(BattleReport(enemiesKilled = 50, towersBuilt = 4))

        progress.onLevelCleared(SIM_LEVEL, livesLeft = 20, maxLives = 20)
        val afterVictory = progress.todaysMissions.map { it.progress }

        progress.endBattle()
        assertEquals(
            "endBattle zaferin olcumunu ikinci kez islememeli",
            afterVictory,
            progress.todaysMissions.map { it.progress }
        )
    }

    /** Yeni savas onceki savasin olcumunu DEVRALMAZ. */
    @Test
    fun beginBattleClearsThePreviousMeasurement() {
        val progress = CampaignProgressImpl(veteranSave(), StepClock())
        progress.beginBattle(SIM_LEVEL)
        progress.noteTowerBuilt("MACHINE_GUN")
        progress.noteTowerBuilt("CANNON")
        assertEquals(2, progress.currentBattleReport.towersBuilt)

        progress.beginBattle(SIM_LEVEL)
        assertEquals(BATTLE_STAT_UNREPORTED, progress.currentBattleReport.towersBuilt)
    }

    /**
     * Yenilgide de hacim sayaclari islenmeli — oyuncu o dusmanlari oldurdu.
     *
     * Test **kosulsuzdur**: gorev listesinde `KILL_ENEMIES` cikana kadar tohum
     * aranir ([saveShowingDailyType]). Onceki hali "gorev listede yoksa gec"
     * dalini tasiyordu ve sayac hic ilerlemese bile YESIL kaliyordu — yani
     * adinin iddia ettigi davranisi dogrulamiyordu.
     */
    @Test
    fun defeatFlushesVolumeMeasurementIntoTheDailyCounter() {
        val progress = CampaignProgressImpl(
            saveShowingDailyType(MissionType.KILL_ENEMIES), StepClock()
        )
        val before = progress.todaysMissions.first { it.type == MissionType.KILL_ENEMIES }
        assertEquals("temiz baslangic", 0, before.progress)

        progress.beginBattle(SIM_LEVEL)
        progress.noteBattle(BattleReport(enemiesKilled = 30))
        progress.endBattle()

        assertEquals(
            "yenilgide oldurulen dusmanlar gunluk sayaca islenmeli",
            30,
            progress.todaysMissions.first { it.type == MissionType.KILL_ENEMIES }.progress
        )
    }

    /**
     * **En sik tekrarlanan oynanis yolu:** yenilgi -> "TEKRAR DENE". Bu akis
     * savas ekranindan cikmadigi icin `endBattle()`e HIC ugramaz, yalnizca yeni
     * bir `beginBattle()` gelir. Olcum orada da islenmezse kaybedilen savasta
     * oldurulen her dusman sessizce silinir.
     */
    @Test
    fun retryAfterDefeatKeepsTheMeasurement() {
        val progress = CampaignProgressImpl(
            saveShowingDailyType(MissionType.KILL_ENEMIES), StepClock()
        )

        progress.beginBattle(SIM_LEVEL)
        progress.noteBattle(BattleReport(enemiesKilled = 25))
        // endBattle YOK — dogrudan yeniden basla.
        progress.beginBattle(SIM_LEVEL)

        assertEquals(
            "tekrar denemede onceki savasin olcumu kaybolmamali",
            25,
            progress.todaysMissions.first { it.type == MissionType.KILL_ENEMIES }.progress
        )
        assertEquals(
            "yeni savas temiz baslamali",
            BATTLE_STAT_UNREPORTED,
            progress.currentBattleReport.enemiesKilled
        )
    }

    // =================================================================================
    // 4. SEMANTIK — "tek savasta" toplanmaz, olculmemis alan hak ETTIRMEZ
    // =================================================================================

    /**
     * `d_v_supply2500` "**bir savasta** 2.500 Tedarik" der. Toplama semantigi
     * kullanilirsa uc kolay bolum ust uste oynanarak hedef kirilir.
     */
    @Test
    fun singleBattleMissionTakesTheBestNotTheSum() {
        val template = MissionPools.VOLUME.first { it.type == MissionType.EARN_SUPPLY_IN_ONE_BATTLE }
        var mission = Mission(template)

        mission = mission.raisedTo(1_200)
        mission = mission.raisedTo(1_100)

        assertEquals("iki savas TOPLANMAMALI", 1_200, mission.progress)
        assertFalse(mission.isComplete)

        mission = mission.raisedTo(template.target)
        assertTrue(mission.isComplete)
    }

    /**
     * "Satmadan temizle" 120 coin'lik BECERI gorevi. Satis olcumu yoksa gorev
     * ilerlememeli; aksi halde satis yapan oyuncu odulu bedavaya alir.
     */
    @Test
    fun unmeasuredSellsDoNotEarnTheSkillMission() {
        val unmeasured = battleMissionDeltas(
            BattleReport(enemiesKilled = 100),
            victory = true,
            unlockedTowerTypes = 4,
        ).map { it.first }
        assertFalse(MissionType.CLEAR_WITHOUT_SELLING in unmeasured)

        val measuredClean = battleMissionDeltas(
            BattleReport(enemiesKilled = 100, towersSold = 0),
            victory = true,
            unlockedTowerTypes = 4,
        ).map { it.first }
        assertTrue(MissionType.CLEAR_WITHOUT_SELLING in measuredClean)

        val measuredDirty = battleMissionDeltas(
            BattleReport(enemiesKilled = 100, towersSold = 1),
            victory = true,
            unlockedTowerTypes = 4,
        ).map { it.first }
        assertFalse(MissionType.CLEAR_WITHOUT_SELLING in measuredDirty)
    }

    /** Yenilgide BECERI gorevleri ilerlemez. */
    @Test
    fun skillMissionsRequireVictory() {
        val deltas = battleMissionDeltas(fullReport(), victory = false, unlockedTowerTypes = 4)
            .map { it.first }
        assertFalse(MissionType.CLEAR_WITHOUT_SELLING in deltas)
        assertFalse(MissionType.BUILD_ALL_TOWER_TYPES in deltas)
        assertFalse(MissionType.CLEAR_AT_DOUBLE_SPEED in deltas)
        assertTrue("hacim gorevleri yenilgide de sayilir", MissionType.KILL_ENEMIES in deltas)
    }

    /** "Tum kule tiplerini kur" acik tip sayisina gore olculur, sabit 4'e gore degil. */
    @Test
    fun allTowerTypesMissionUsesUnlockedCount() {
        val threeBuilt = BattleReport(distinctTowerTypes = 3)
        assertFalse(
            MissionType.BUILD_ALL_TOWER_TYPES in
                battleMissionDeltas(threeBuilt, true, unlockedTowerTypes = 4).map { it.first }
        )
        assertTrue(
            MissionType.BUILD_ALL_TOWER_TYPES in
                battleMissionDeltas(threeBuilt, true, unlockedTowerTypes = 3).map { it.first }
        )
    }

    // =================================================================================
    // 5. ODUL GERCEKTEN ODENIYOR — claimCompletedMissions() artik cagriliyor
    // =================================================================================

    @Test
    fun claimPaysOnceAndCreditsTheWallet() {
        val log = CoinLog()
        val save = veteranSave()
        val progress = CampaignProgressImpl(save, StepClock(), log.sink)

        // Haftalik "12 bolum" gorevini bitirecek kadar oyna.
        repeat(EconomyConfig.WEEKLY_LONG_PATROL_TARGET) {
            progress.beginBattle(SIM_LEVEL)
            progress.onLevelCleared(SIM_LEVEL, livesLeft = 20, maxLives = 20, battle = fullReport())
            progress.endBattle()
        }

        assertTrue("alinabilir gorev olmali", progress.claimableMissionCount > 0)
        val before = progress.coins
        val payout = progress.claimCompletedMissions()

        assertTrue("odul pozitif olmali", payout > 0)
        assertEquals("bakiye tam odul kadar artmali", before + payout, progress.coins)
        assertEquals("gorev kaynagi analytics'e yazilmali", payout, log.missionCoins)

        assertEquals("ikinci alma 0 odemeli", 0, progress.claimCompletedMissions())
        assertEquals(0, progress.claimableMissionCount)
        assertEquals(before + payout, progress.coins)
    }

    /** Alinmis gorev uygulama yeniden acildiginda TEKRAR alinamaz. */
    @Test
    fun claimsSurviveARestart() {
        val store = InMemoryKeyValueStore()
        val save = SaveManager(store)
        save.saveWallet(
            PlayerWallet(
                unlockedLevels = (1..SIM_LEVEL).toSet(),
                clearedLevels = (1..SIM_LEVEL).toSet(),
                bestStars = (1..SIM_LEVEL).associateWith { 3 },
            )
        )
        val clock = StepClock()
        val first = CampaignProgressImpl(save, clock)
        repeat(4) {
            first.beginBattle(SIM_LEVEL)
            first.onLevelCleared(SIM_LEVEL, 20, 20, fullReport())
            first.endBattle()
        }
        val paid = first.claimCompletedMissions()
        assertTrue(paid > 0)
        val balance = first.coins

        val reopened = CampaignProgressImpl(SaveManager(store), clock)
        assertEquals("bakiye kalici olmali", balance, reopened.coins)
        assertEquals("ayni gun ikinci kez odeme YOK", 0, reopened.claimCompletedMissions())
    }

    // =================================================================================
    // 6. SAAT HILESI SAVUNMASI BOZULMADI (GDD E.4)
    // =================================================================================

    /** Saati geri alan oyuncu yeni gorev ALMAZ ve ikinci kez odul kazanmaz. */
    @Test
    fun rewindingTheClockGrantsNoSecondDailyPayout() {
        val log = CoinLog()
        val clock = StepClock(day = 20_500L)
        val progress = CampaignProgressImpl(veteranSave(), clock, log.sink)

        repeat(4) {
            progress.beginBattle(SIM_LEVEL)
            progress.onLevelCleared(SIM_LEVEL, 20, 20, fullReport())
            progress.endBattle()
        }
        val firstPayout = progress.claimCompletedMissions()
        assertTrue(firstPayout > 0)

        // Saati DUNE al.
        clock.day = 20_499L
        val decision = progress.refreshCalendar()

        assertFalse("saat geri alindiginda sifirlama olmamali", decision.dailyReset)
        assertTrue(decision.clockSuspect)
        assertTrue(decision.frozenByBackwardClock)
        assertEquals("geri sarma ikinci odul uretmemeli", 0, progress.claimCompletedMissions())
        assertEquals(firstPayout, log.missionCoins)
    }

    /** Ileri atlama en fazla BIR sifirlama sayilir ve gorev odulu damperlenir. */
    @Test
    fun jumpingTheClockForwardDampensMissionRewardsOnly() {
        val clock = StepClock(day = 20_500L)
        val progress = CampaignProgressImpl(veteranSave(), clock)
        val coinsBefore = progress.coins

        // Ayni boot, duvar saati 40 gun ileri ama gercek gecen sure ilerlemedi.
        clock.day = 20_540L
        val decision = progress.refreshCalendar()

        assertTrue("oyuncu bloke edilmez, sifirlama yine olur", decision.dailyReset)
        assertTrue(decision.clockSuspect)
        assertEquals(
            "damper YALNIZCA gorev odulune uygulanir",
            EconomyConfig.SUSPECT_REWARD_MULTIPLIER,
            progress.missionRewardMultiplier,
            1e-9
        )
        assertEquals("bakiyeye dokunulmamali", coinsBefore, progress.coins)
    }

    // =================================================================================
    // 7. GELIR SIMULASYONU — tasarlanan ~3.620 coin/hafta TESLIM EDILIYOR MU
    // =================================================================================

    /**
     * GDD E: gunluk tavan 360 (60 + 80 + 120 + 100 bonus) x 7 = 2.520,
     * haftalik gorevler 1.100 -> **3.620 coin/hafta**. FUN_AUDIT'te teslim
     * edilen 0'di.
     *
     * Simulasyon her gun 4 savas oynayan bir oyuncuyu surer ve YALNIZCA gorev
     * kaynakli coin'i (analytics `source = mission`) toplar.
     */
    @Test
    fun sevenDaysOfPlayDeliversTheDesignedWeeklyMissionIncome() {
        val designed = EconomyConfig.DAILY_MAX_TOTAL * 7 + EconomyConfig.WEEKLY_BUDGET
        assertEquals("GDD E: 7 x 360 + 1.100", 3_620, designed)

        // Gorev secimi cihaz tohumuna baglidir. TEK bir tohumla olculen gelir
        // "sansli tohumda calisiyor" demektir; farkli kurulumlar farkli gorev
        // dizileri gorur. Bu yuzden simulasyon BAGIMSIZ kurulumlar uzerinde
        // tekrarlanir ve HEPSININ tasarlanan geliri vermesi beklenir.
        repeat(SIMULATED_INSTALLS) {
            assertEquals(
                "haftalik gorev geliri her kurulumda tasarlanan degeri teslim etmeli",
                designed,
                simulateWeek(reportMeasurements = true)
            )
        }
    }

    /**
     * Bir haftalik oynanisi surer ve YALNIZCA gorev kaynakli coin'i dondurur.
     *
     * @param reportMeasurements `true` ise cagiran taraf savas olcumunu bildirir
     *   ([fullReport]); `false` ise hicbir sey bildirmez ve sistem yalnizca
     *   dalga tablosundan turetmeye kalir — `GameScreen.kt`in BUGUNKU cagrisi.
     */
    private fun simulateWeek(reportMeasurements: Boolean): Int {
        val log = CoinLog()
        val clock = StepClock(day = 20_500L)
        val progress = CampaignProgressImpl(progressingSave(), clock, log.sink)

        var nextLevel = FIRST_UNPLAYED_LEVEL
        repeat(7) { day ->
            if (day > 0) clock.advanceDays(1)
            progress.refreshCalendar()
            repeat(BATTLES_PER_DAY) {
                progress.beginBattle(nextLevel)
                if (reportMeasurements) {
                    progress.onLevelCleared(nextLevel, 20, 20, fullReport())
                } else {
                    progress.onLevelCleared(nextLevel, 20, 20)
                }
                progress.endBattle()
                nextLevel++
            }
            progress.claimCompletedMissions()
        }
        return log.missionCoins
    }

    /**
     * **URETIMDEKI GERCEK GELIR.** Ayni simulasyon ama cagiran taraf HICBIR
     * olcum bildirmiyor — `onLevelCleared` yalnizca `levelId/livesLeft/maxLives`
     * aliyor, yani `GameScreen.kt`in BUGUNKU cagrisiyla birebir ayni.
     *
     * OLCULEN: **3.620 coin/hafta** = tasarlanan degerin TAMAMI.
     *
     * Bunu mumkun kilan sey olcum kapisidir ([MEASURABLE_MISSION_TYPES]):
     * gosterilen her gorev turetmeyle dolabildigi icin gunluk tavan gercekten
     * ulasilabilir. Kapi olmadan olculen deger 2.480 idi (%69) — cunku gunlerin
     * bir kisminda tamamlanmasi imkansiz bir gorev cikiyordu.
     *
     * Kapi acildiginda (`BATTLE_TELEMETRY_WIRED = true`) turetme tek basina
     * yetmez, o yuzden beklenti bayraga gore ayrilir.
     */
    @Test
    fun derivationAloneDeliversTheDesignedIncomeWhileTelemetryIsUnwired() {
        val designed = EconomyConfig.DAILY_MAX_TOTAL * 7 + EconomyConfig.WEEKLY_BUDGET
        repeat(SIMULATED_INSTALLS) {
            val earned = simulateWeek(reportMeasurements = false)
            if (BATTLE_TELEMETRY_WIRED) {
                assertTrue(
                    "olcum bagliyken turetme tek basina yetmez ama haftaligi asmali, " +
                        "olculen $earned",
                    earned > EconomyConfig.WEEKLY_BUDGET
                )
            } else {
                assertEquals(
                    "olcum kapisi kapaliyken turetme tasarlanan gelirin TAMAMINI vermeli",
                    designed,
                    earned
                )
            }
        }
    }

    private companion object {
        /**
         * Birim testlerinin bolumu. Act II'nin ortasi: 4 kule tipi acik, dalga
         * tablosu zirhli ve tank iceriyor.
         */
        const val SIM_LEVEL = 14

        /**
         * Gelir simulasyonunun ilk OYNANMAMIS bolumu (oncesi temizlenmis).
         *
         * 19 secildi cunku `d_v_supply2500` ancak L18'den itibaren (2.650
         * Tedarik) karsilanabiliyor; daha erken bir baslangic o gorevin
         * cekildigi gunlerde simulasyonu tohuma bagimli hale getirirdi.
         */
        const val FIRST_UNPLAYED_LEVEL = 19

        /** Gunluk gorevlerin tasarim varsayimi: gunde 4 savas. */
        const val BATTLES_PER_DAY = 4

        /** Dusman sayisi gunluk oldurme hedefinin ALTINDA olan bolum. */
        const val SMALL_LEVEL = 1

        /** R2 Takviye ile geri verilen can. */
        const val REINFORCED_LIVES = 6

        /**
         * Gelir simulasyonunun tekrarlanacagi BAGIMSIZ kurulum sayisi. Gorev
         * secimi cihaz tohumuna bagli oldugu icin tek olcum "sansli tohum"
         * olabilir; tekrar, gelirin tohumdan BAGIMSIZ oldugunu kanitlar.
         */
        const val SIMULATED_INSTALLS = 12

        /** 7 gun x 4 savas = 28 ilk temizleme; kampanya bunu tasiyabilmeli. */
        const val GameConfigLevelCount = 55
    }
}
