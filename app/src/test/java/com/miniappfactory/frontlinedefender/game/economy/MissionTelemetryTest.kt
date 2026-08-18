package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 15 — **OLCUM KAPISI ACILDI.**
 *
 * ## Neden bu dosya var
 * `MissionWiringTest` gorev sisteminin kendi canliligini kilitliyor. Bu dosya
 * bir adim otesini sorar: `BATTLE_TELEMETRY_WIRED` artik `true` ve alti sablon
 * havuza dondu — **her birinin sayaci gercekten doluyor mu?**
 *
 * Kapinin kapali oldugu donemde su alti sablon oyuncuya HIC gosterilmiyordu:
 *
 * | Slot     | Sablon             | Sayac                              |
 * |----------|--------------------|------------------------------------|
 * | Katilim  | `d_p_skip3`        | `prepTimersSkipped`                |
 * | Hacim    | `d_v_build15`      | `towersBuilt`                      |
 * | Hacim    | `d_v_upg30`        | `towerUpgrades`                    |
 * | Beceri   | `d_s_no_sell`      | `towersSold` + satis olcumu ACIK   |
 * | Beceri   | `d_s_all_towers`   | `distinctTowerTypes`               |
 * | Beceri   | `d_s_double_speed` | `clearedAtDoubleSpeed`             |
 *
 * Asagidaki testler her biri icin **oynanis olayini uretir** ve sayacin
 * ilerledigini kanitlar. `MissionTelemetryWiringTest` ayni iddiayi bir adim
 * daha asagida — GERCEK DUGMEYE BASARAK — dogrular; bu dosya ekonomi
 * tarafinin dogrulugunu, o dosya dikisin varligini kilitler.
 *
 * **SAF JUnit**: `InMemoryKeyValueStore` + enjekte saat; Robolectric yok.
 */
class MissionTelemetryTest {

    // =================================================================================
    // Test altyapisi
    // =================================================================================

    private class StepClock(var day: Long = 20_500L) : ClockProvider {
        private var wall = day * EconomyConfig.MS_PER_DAY
        fun advanceDays(count: Long) {
            day += count
            wall += count * EconomyConfig.MS_PER_DAY
        }
        override fun sample(): ClockSample = ClockSample(
            epochDay = day,
            wallClockMs = wall,
            elapsedRealtimeMs = wall,
            bootId = 1L,
        )
    }

    private class CoinLog {
        var missionCoins = 0
        val sink: (String, Map<String, Any>) -> Unit = { event, params ->
            if (event == "coin_earn" && params["source"] == "mission") {
                missionCoins += params["amount"] as Int
            }
        }
    }

    /** Gec kampanya oyuncusu: 4 kule tipi acik, tum kapilar gecilmis. */
    private fun veteranSave(cleared: Int = SIM_LEVEL): SaveManager {
        val save = SaveManager(InMemoryKeyValueStore())
        save.saveWallet(
            PlayerWallet(
                coins = 0,
                unlockedLevels = (1..CAMPAIGN).toSet(),
                clearedLevels = (1..cleared).toSet(),
                bestStars = (1..cleared).associateWith { 3 },
            )
        )
        return save
    }

    /**
     * Gunluk listesinde [type] tipinde bir gorev CIKAN kayit uretir.
     * Gorev secimi tohuma bagli oldugu icin uygun tohum ARANIR; testin
     * "gorev cikmadiysa gec" gibi kendini muaf tutan bir dali YOKTUR.
     */
    private fun saveShowingDailyType(type: MissionType): Pair<SaveManager, Mission> {
        repeat(4_000) {
            val save = veteranSave()
            val generated = dailyMissions(
                epochDay = 20_500L,
                seed = save.missionSeed,
                clearedLevels = SIM_LEVEL,
                unlockedTowerTypes = 4,
                measurableTypes = MEASURABLE_MISSION_TYPES,
            )
            generated.firstOrNull { it.type == type }?.let { return save to it }
        }
        throw AssertionError("$type gunluk havuzda hic cikmadi — havuz veya kapi bozuk")
    }

    /**
     * Panelde [type] tipindeki gorevin o anki ilerlemesi.
     *
     * Gorev listede YOKSA hata verir — sessizce 0 dondurmek testi kendini
     * dogrulayan bir seye cevirirdi ("gorev yoktu, o yuzden ilerlemedi").
     * Cagiran taraf [saveShowingDailyType] ile o tipi listeye SOKMUS olmali.
     */
    private fun CampaignProgressImpl.progressOf(type: MissionType): Int =
        todaysMissions.firstOrNull { it.type == type }?.progress
            ?: throw AssertionError("$type bugunun listesinde yok — test kurulumu hatali")

    // =================================================================================
    // 0. KAPI GERCEKTEN ACIK MI
    // =================================================================================

    /**
     * Kapinin tamami acilmali: 15 gorev tipinin HEPSI oyuncuya gosterilebilir
     * olmali. Bu testin kirilmasi "bir tip yine olculemiyor" demektir.
     */
    @Test
    fun theCapabilityGateNowPassesEveryMissionType() {
        assertTrue("olcum dikisi baglanmadan kapi acilamaz", BATTLE_TELEMETRY_WIRED)
        assertEquals(
            "15 gorev tipinin hepsi olculebilir olmali",
            MissionType.entries.toSet(),
            MEASURABLE_MISSION_TYPES,
        )
        assertEquals(15, MEASURABLE_MISSION_TYPES.size)
    }

    /** Kapi acilinca ONCEDEN GIZLI olan alti sablon havuza donmus olmali. */
    @Test
    fun theSixPreviouslyHiddenTemplatesAreBackInThePools() {
        val visible = (MissionPools.PARTICIPATION + MissionPools.VOLUME + MissionPools.SKILL)
            .filter { it.type in MEASURABLE_MISSION_TYPES }
            .map { it.id }
            .toSet()
        listOf(
            "d_p_skip3", "d_v_build15", "d_v_upg30",
            "d_s_no_sell", "d_s_all_towers", "d_s_double_speed",
        ).forEach {
            assertTrue("$it hala gizli", it in visible)
        }
    }

    // =================================================================================
    // 1. ALTI SAYAC — her biri icin oynanis olayi -> ilerleme
    // =================================================================================

    /** `d_p_skip3` — hazirlik sayacini atlamak Katilim gorevini ilerletir. */
    @Test
    fun skippingThePrepTimerAdvancesTheParticipationMission() {
        val (save, mission) = saveShowingDailyType(MissionType.SKIP_PREP_TIMER)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(SIM_LEVEL)
        repeat(mission.target) { progress.notePrepTimerSkipped() }
        assertEquals(
            "olcum savas ICINDE gorev ilerletmemeli (cift sayim korumasi)",
            0,
            progress.progressOf(MissionType.SKIP_PREP_TIMER),
        )

        progress.onLevelCleared(SIM_LEVEL, 20, 20)
        assertEquals(
            "hazirlik atlama sayaci ilerlemedi",
            mission.target,
            progress.progressOf(MissionType.SKIP_PREP_TIMER),
        )
    }

    /** `d_v_build15` — kule kurmak Hacim gorevini ilerletir. */
    @Test
    fun buildingTowersAdvancesTheVolumeMission() {
        val (save, mission) = saveShowingDailyType(MissionType.BUILD_TOWERS)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(SIM_LEVEL)
        repeat(7) { progress.noteTowerBuilt("MACHINE_GUN") }
        progress.onLevelCleared(SIM_LEVEL, 20, 20)

        assertEquals(
            "kurulan kule sayaci ilerlemedi",
            minOf(7, mission.target),
            progress.progressOf(MissionType.BUILD_TOWERS),
        )
    }

    /** `d_v_upg30` — kule yukseltmek Hacim gorevini ilerletir. */
    @Test
    fun upgradingTowersAdvancesTheVolumeMission() {
        val (save, mission) = saveShowingDailyType(MissionType.UPGRADE_TOWERS)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(SIM_LEVEL)
        repeat(9) { progress.noteTowerUpgraded() }
        progress.onLevelCleared(SIM_LEVEL, 20, 20)

        assertEquals(
            "yukseltme sayaci ilerlemedi",
            minOf(9, mission.target),
            progress.progressOf(MissionType.UPGRADE_TOWERS),
        )
    }

    /**
     * `d_s_no_sell` — satis olcumu ACIKKEN hic satmadan kazanmak gorevi verir.
     *
     * Kritik ayrim: sayacin 0 OLMASI degil, 0 OLDUGUNUN OLCULMUS olmasi
     * gerekir. `noteSellTrackingActive()` cagrilmazsa alan `UNREPORTED` kalir
     * ve gorev ilerlememelidir (asagidaki ikinci iddia).
     */
    @Test
    fun clearingWithoutSellingEarnsTheSkillMission() {
        val (save, _) = saveShowingDailyType(MissionType.CLEAR_WITHOUT_SELLING)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(SIM_LEVEL)
        progress.noteSellTrackingActive()
        progress.onLevelCleared(SIM_LEVEL, 20, 20)

        assertEquals(
            "satmadan temizleme gorevi ilerlemedi",
            1,
            progress.progressOf(MissionType.CLEAR_WITHOUT_SELLING),
        )
    }

    /** Satis yapildiysa ayni gorev ilerlememeli — 120 coin bedavaya gitmez. */
    @Test
    fun sellingATowerForfeitsTheSkillMission() {
        val (save, _) = saveShowingDailyType(MissionType.CLEAR_WITHOUT_SELLING)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(SIM_LEVEL)
        progress.noteSellTrackingActive()
        progress.noteTowerSold()
        progress.onLevelCleared(SIM_LEVEL, 20, 20)

        assertEquals(
            "kule satan oyuncu 'satmadan temizle' odulunu ALMAMALI",
            0,
            progress.progressOf(MissionType.CLEAR_WITHOUT_SELLING),
        )
    }

    /** `d_s_all_towers` — acik tum kule tiplerini kurmak gorevi verir. */
    @Test
    fun buildingEveryUnlockedTowerTypeEarnsTheSkillMission() {
        val (save, _) = saveShowingDailyType(MissionType.BUILD_ALL_TOWER_TYPES)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(SIM_LEVEL)
        // SIM_LEVEL'de 4 tip acik (GDD F: Railgun L5, Frost L8).
        GameConfig.TowerType.values().forEach { progress.noteTowerBuilt(it.name) }
        progress.onLevelCleared(SIM_LEVEL, 20, 20)

        assertEquals(
            "tum kule tiplerini kurma gorevi ilerlemedi",
            1,
            progress.progressOf(MissionType.BUILD_ALL_TOWER_TYPES),
        )
    }

    /** Ayni kuleyi tekrar tekrar kurmak "tum tipler"i vermez. */
    @Test
    fun repeatingOneTowerTypeDoesNotEarnTheAllTypesMission() {
        val (save, _) = saveShowingDailyType(MissionType.BUILD_ALL_TOWER_TYPES)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(SIM_LEVEL)
        repeat(12) { progress.noteTowerBuilt("MACHINE_GUN") }
        progress.onLevelCleared(SIM_LEVEL, 20, 20)

        assertEquals(
            "tek tipten 12 kule 'tum tipleri kur' gorevini vermemeli",
            0,
            progress.progressOf(MissionType.BUILD_ALL_TOWER_TYPES),
        )
    }

    /** `d_s_double_speed` — 2x hizda kazanmak gorevi verir. */
    @Test
    fun clearingAtDoubleSpeedEarnsTheSkillMission() {
        val (save, _) = saveShowingDailyType(MissionType.CLEAR_AT_DOUBLE_SPEED)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(SIM_LEVEL)
        progress.noteGameSpeed(1f) // savas basi tabani
        progress.noteGameSpeed(2f) // oyuncu hizlandirdi
        progress.onLevelCleared(SIM_LEVEL, 20, 20)

        assertEquals(
            "2x hizda temizleme gorevi ilerlemedi",
            1,
            progress.progressOf(MissionType.CLEAR_AT_DOUBLE_SPEED),
        )
    }

    /** 1x'te kalan oyuncu 2x gorevini almamali. */
    @Test
    fun stayingAtSingleSpeedDoesNotEarnTheDoubleSpeedMission() {
        val (save, _) = saveShowingDailyType(MissionType.CLEAR_AT_DOUBLE_SPEED)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(SIM_LEVEL)
        progress.noteGameSpeed(1f)
        progress.onLevelCleared(SIM_LEVEL, 20, 20)

        assertEquals(
            "1x oynayan oyuncu 2x odulunu ALMAMALI",
            0,
            progress.progressOf(MissionType.CLEAR_AT_DOUBLE_SPEED),
        )
    }

    // =================================================================================
    // 2. YENILGI YOLU — olcum kaybolmamali
    // =================================================================================

    /**
     * **YENILGI DE OLCULUR.** Hacim gorevleri (insa / yukseltme / hazirlik
     * atlama) zafer sarti ARAMAZ: oyuncu o emegi verdi.
     *
     * Yol gercek ve en sik tekrarlanan yollardan biri: yenilgi -> "TEKRAR
     * DENE". Bu akis `endBattle()`e HIC ugramaz, yalnizca yeni bir
     * [CampaignProgressImpl.beginBattle] gelir ve onceki savasin olcumunu
     * O flush eder.
     */
    @Test
    fun defeatThenRetryStillCountsTheVolumeMeasurement() {
        // Uc hacim sayacinin her biri AYRI kurulumda sinanir: gunun listesi
        // uc slottan yalnizca birer gorev tasir, yani uc tip ayni gun ayni
        // panelde bulunmayabilir.
        mapOf<MissionType, CampaignProgressImpl.() -> Unit>(
            MissionType.BUILD_TOWERS to { repeat(5) { noteTowerBuilt("MACHINE_GUN") } },
            MissionType.UPGRADE_TOWERS to { repeat(5) { noteTowerUpgraded() } },
            MissionType.SKIP_PREP_TIMER to { repeat(5) { notePrepTimerSkipped() } },
        ).forEach { (type, play) ->
            val (save, mission) = saveShowingDailyType(type)
            val progress = CampaignProgressImpl(save, StepClock())

            progress.beginBattle(SIM_LEVEL)
            progress.play()

            // Yenilgi: modal acilir, oyuncu TEKRAR DENE der. `endBattle` YOK,
            // yalnizca yeni bir savas baslar ve onceki olcumu O flush eder.
            progress.beginBattle(SIM_LEVEL)

            assertEquals(
                "$type: kaybedilen savasin olcumu sessizce silindi",
                minOf(5, mission.target),
                progress.progressOf(type),
            )
        }
    }

    /** Yenilgide BECERI gorevleri ilerlemez — zafer sarti korunmali. */
    @Test
    fun defeatDoesNotEarnSkillMissions() {
        listOf(
            MissionType.CLEAR_WITHOUT_SELLING,
            MissionType.CLEAR_AT_DOUBLE_SPEED,
            MissionType.BUILD_ALL_TOWER_TYPES,
        ).forEach { type ->
            val (save, _) = saveShowingDailyType(type)
            val progress = CampaignProgressImpl(save, StepClock())

            progress.beginBattle(SIM_LEVEL)
            progress.noteSellTrackingActive()
            progress.noteGameSpeed(2f)
            GameConfig.TowerType.values().forEach { progress.noteTowerBuilt(it.name) }
            progress.endBattle() // yenilgi / cikis

            assertEquals(
                "$type: yenilgide beceri gorevi ilerledi",
                0,
                progress.progressOf(type),
            )
        }
    }

    /** Olcum savas basina TEK KEZ islenir; zafer + cikis iki kez saymaz. */
    @Test
    fun telemetryIsFlushedExactlyOncePerBattle() {
        val (save, _) = saveShowingDailyType(MissionType.BUILD_TOWERS)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(SIM_LEVEL)
        repeat(6) { progress.noteTowerBuilt("MACHINE_GUN") }
        progress.onLevelCleared(SIM_LEVEL, 20, 20)
        val after = progress.progressOf(MissionType.BUILD_TOWERS)

        progress.endBattle() // bolum secime donus — ikinci flush denemesi
        assertEquals("olcum iki kez sayildi", after, progress.progressOf(MissionType.BUILD_TOWERS))
        assertEquals(6, after)
    }

    /** Yeni savas onceki savasin olcumunu DEVRALMAZ. */
    @Test
    fun beginBattleResetsDistinctTowerTypeTracking() {
        val (save, _) = saveShowingDailyType(MissionType.BUILD_ALL_TOWER_TYPES)
        val progress = CampaignProgressImpl(save, StepClock())

        // Savas 1: iki tip.
        progress.beginBattle(SIM_LEVEL)
        progress.noteTowerBuilt("MACHINE_GUN")
        progress.noteTowerBuilt("CANNON")
        progress.endBattle()

        // Savas 2: diger iki tip. Tipler savas 1'den DEVRETMEMELI.
        progress.beginBattle(SIM_LEVEL)
        progress.noteTowerBuilt("RAILGUN")
        progress.noteTowerBuilt("FROST_FIELD")
        progress.onLevelCleared(SIM_LEVEL, 20, 20)

        assertEquals(
            "iki savasin kule tipleri birlestirilip gorev haksiz kazanildi",
            0,
            progress.progressOf(MissionType.BUILD_ALL_TOWER_TYPES),
        )
    }

    // =================================================================================
    // 3. ODUL ODEMESI — kapi acilinca da tek sefer
    // =================================================================================

    /** Olcumle tamamlanan gorevin odulu bir kez odenir, ikinci `claim` 0 verir. */
    @Test
    fun telemetryEarnedMissionPaysExactlyOnce() {
        val (save, mission) = saveShowingDailyType(MissionType.BUILD_TOWERS)
        val log = CoinLog()
        val progress = CampaignProgressImpl(save, StepClock(), log.sink)

        progress.beginBattle(SIM_LEVEL)
        repeat(mission.target) { progress.noteTowerBuilt("MACHINE_GUN") }
        progress.onLevelCleared(SIM_LEVEL, 20, 20)

        val first = progress.claimCompletedMissions()
        assertTrue("olcumle tamamlanan gorev hic odeme yapmadi", first >= mission.reward)
        assertEquals("ayni gorev ikinci kez odendi", 0, progress.claimCompletedMissions())
        assertEquals(first, log.missionCoins)
    }

    // =================================================================================
    // 4. HAFTALIK YILDIZ — kampanya sonunda olu kalmamali
    // =================================================================================

    /**
     * **ILK TEMIZLEMEDE DAVRANIS BIREBIR KORUNUR.**
     *
     * Yeni kural "yildiz ARTISI" oldugu icin ilk temizlemede `starsBefore = 0`
     * ve sonuc `result.stars`tir — yani Faz 14 davranisi aynen surer.
     */
    @Test
    fun firstClearStillCountsEveryStarTowardTheWeekly() {
        val save = veteranSave(cleared = 0)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(1)
        progress.onLevelCleared(1, 20, 20) // kayipsiz -> 3 yildiz
        progress.endBattle()

        assertEquals(
            "ilk temizlemede yildiz sayaci degismemeli",
            3,
            progress.weeklyMissions.first { it.type == MissionType.WEEKLY_STARS_EARNED }.progress,
        )
    }

    /**
     * **ANTI-FARMING.** Ayni bolumu ayni yildizla tekrar oynamak sayaci
     * ILERLETMEZ. Yildiz best-of'tur; artis yoksa kazanim da yoktur.
     */
    @Test
    fun replayingALevelAtTheSameStarsAddsNothing() {
        val save = veteranSave(cleared = 0)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(1)
        progress.onLevelCleared(1, 20, 20)
        progress.endBattle()
        val afterFirst = progress.weeklyStars()

        repeat(20) {
            progress.beginBattle(1)
            progress.onLevelCleared(1, 20, 20)
            progress.endBattle()
        }

        assertEquals(
            "ayni bolumu tekrar oynamak haftalik yildiz sayacini sisiriyor (FARMING)",
            afterFirst,
            progress.weeklyStars(),
        )
    }

    /**
     * **KOR NOKTA KAPANDI.** Kampanyayi bitirmis oyuncu, dusuk yildizli bir
     * bolumu yukselterek haftaligi yine tamamlayabilmeli.
     *
     * Onceki kural `result.firstClear` idi: 55 bolumu bitiren oyuncu icin sayac
     * SONSUZA KADAR olu kaliyor ve 600 coin'lik `w_elite_operator` bir daha
     * ASLA tamamlanamiyordu — haftalik butcenin %55'i erisilemezdi.
     */
    @Test
    fun improvingStarsAfterFinishingTheCampaignStillCompletesTheWeekly() {
        // Kampanyanin TAMAMI temizlenmis ama her bolum 1 yildiz.
        val save = SaveManager(InMemoryKeyValueStore())
        save.saveWallet(
            PlayerWallet(
                coins = 0,
                unlockedLevels = (1..CAMPAIGN).toSet(),
                clearedLevels = (1..CAMPAIGN).toSet(),
                bestStars = (1..CAMPAIGN).associateWith { 1 },
            )
        )
        val log = CoinLog()
        val progress = CampaignProgressImpl(save, StepClock(), log.sink)

        assertEquals("kurulum: haftalik sayac bos baslamali", 0, progress.weeklyStars())

        // Her bolumu 1 -> 3 yildiza cikar: bolum basi +2.
        var level = 1
        while (progress.weeklyStars() < EconomyConfig.WEEKLY_ELITE_TARGET) {
            progress.beginBattle(level)
            progress.onLevelCleared(level, 20, 20)
            progress.endBattle()
            level++
        }

        val weekly = progress.weeklyMissions.first { it.type == MissionType.WEEKLY_STARS_EARNED }
        assertTrue(
            "kampanyayi bitiren oyuncu haftaligi hala tamamlayamiyor",
            weekly.isComplete,
        )
        assertTrue(
            "haftalik odul odenmedi",
            progress.claimCompletedMissions() >= EconomyConfig.WEEKLY_ELITE_REWARD,
        )
    }

    /**
     * **UST SINIR SABIT.** Yildiz artisi omur boyu en fazla `55 x 3` kez
     * sayilabilir; yani bu yol bir farming musluğu DEGIL, tuketilen icerigin
     * karsiligidir.
     */
    @Test
    fun starGainsAreBoundedByTheCampaignSize() {
        val save = veteranSave(cleared = 0)
        val clock = StepClock()
        val progress = CampaignProgressImpl(save, clock)

        // Haftalik sayac hedefinde DOYAR (`WeeklyMission.advanced` -> minOf),
        // dolayisiyla omurluk arzi onun uzerinden olcemeyiz. Arzin kaynagi
        // cuzdandaki EN IYI yildiz toplamidir; sayac yalnizca onun ARTISINI
        // gorur, o yuzden tavan da odur.
        var weeksElapsed = 0
        (1..CAMPAIGN).forEach { lv ->
            repeat(3) { // ayni bolum uc kez: ilki 3 yildiz, kalan ikisi 0 artis
                progress.beginBattle(lv)
                progress.onLevelCleared(lv, 20, 20)
                progress.endBattle()
            }
            // Sayacin doymasi olcumu bozmasin diye haftayi ilerlet.
            if (lv % 5 == 0) {
                clock.advanceDays(7)
                progress.refreshCalendar()
                weeksElapsed++
            }
        }

        val lifetimeSupply = (1..CAMPAIGN).sumOf { progress.starsFor(it) }
        assertEquals(
            "yildiz arzi kampanya buyuklugunu asti — tekrar oynayarak farming acik",
            CAMPAIGN * 3,
            lifetimeSupply,
        )

        // Bu arz kac haftalik `w_elite_operator` eder: 165 / 15 = 11 hafta.
        // Yani cozum bir musluk DEGIL, tuketilen icerigin karsiligidir.
        assertEquals(
            "haftalik gorevin omurluk kosu suresi degisti",
            11,
            lifetimeSupply / EconomyConfig.WEEKLY_ELITE_TARGET,
        )
        assertTrue(weeksElapsed > 0)
    }

    /**
     * Haftalik sayac hedefini ASMAZ: tek bir haftada 165 yildizin tamamini
     * toplasa bile odul bir kez odenir (`WeeklyMission.advanced` doyurur).
     */
    @Test
    fun theWeeklyStarCounterNeverExceedsItsTarget() {
        val save = veteranSave(cleared = 0)
        val progress = CampaignProgressImpl(save, StepClock())

        (1..CAMPAIGN).forEach { lv ->
            progress.beginBattle(lv)
            progress.onLevelCleared(lv, 20, 20)
            progress.endBattle()
        }

        assertEquals(
            "haftalik yildiz sayaci hedefin uzerine cikti",
            EconomyConfig.WEEKLY_ELITE_TARGET,
            progress.weeklyStars(),
        )
    }

    /**
     * Guclendiriciyle satin alinan can yildiz sayacini da sismemeli: Us Tamiri
     * ve R2 Takviye `starHealthFor` uzerinden zaten dusuluyor, dolayisiyla
     * "tamir et -> yildizi yukselt -> haftaligi doldur" yolu KAPALI.
     */
    @Test
    fun reinforcedHealthDoesNotInflateWeeklyStars() {
        val save = veteranSave(cleared = 0)
        val progress = CampaignProgressImpl(save, StepClock())

        progress.beginBattle(1)
        // 14 can kaybedildi, 6'si R2 Takviye ile geri verildi.
        progress.noteReinforcement(6)
        progress.onLevelCleared(1, livesLeft = 12, maxLives = 20)
        progress.endBattle()

        val stars = progress.starsFor(1)
        assertEquals(
            "haftalik sayac gercek yildizdan fazlasini yazdi",
            stars,
            progress.weeklyStars(),
        )
        assertTrue("takviye 3 yildiz satin aldi (arbitraj acik)", stars < 3)
    }

    // =================================================================================
    // 5. SAAT HILESI SAVUNMASI — kapi acilinca da ayakta
    // =================================================================================

    /** Saati geri almak ikinci bir gunluk odeme uretmemeli. */
    @Test
    fun rewindingTheClockStillGrantsNoSecondPayout() {
        val (save, mission) = saveShowingDailyType(MissionType.BUILD_TOWERS)
        val clock = StepClock()
        val progress = CampaignProgressImpl(save, clock)

        progress.beginBattle(SIM_LEVEL)
        repeat(mission.target) { progress.noteTowerBuilt("MACHINE_GUN") }
        progress.onLevelCleared(SIM_LEVEL, 20, 20)
        assertTrue(progress.claimCompletedMissions() > 0)

        // Saat geriye alindi.
        clock.day -= 3
        progress.refreshCalendar()

        assertEquals(
            "saat geri alinarak ikinci odeme alindi",
            0,
            progress.claimCompletedMissions(),
        )
    }

    // =================================================================================
    // 6. ULASILABILIRLIK — olcumle dolan hedefler DOGAL oynanisla karsilanmali
    // =================================================================================

    /**
     * **HACIM SLOTUNUN SOZLESMESI:** "Dogal oynanisla dolar, ekstra grind
     * istemez." Olcut projenin kendi tasarlanan-oynanis modelidir:
     * [SupplyBudgetModel.designedRoster] kac kule kurulacagini,
     * [SupplyBudgetModel.DESIGNED_TIER_THREE_COUNT] kacinin kademe atlayacagini
     * soyler — ayni model SPI'nin boleni, yani "bu bolum boyle oynanir"in
     * resmi tanimi.
     *
     * `d_v_build15` (kapi 0 -> 6) ve `d_v_upg30` (kapi 0 -> 17) tam bu testle
     * bulundu: ikisi de olcum kapisi kapali oldugu icin bugune kadar hic
     * sinanmamisti ve kapilari tutmuyordu (L1'de gunde 8 insa / 8 yukseltme).
     */
    @Test
    fun volumeTelemetryTargetsAreMetByDesignedPlay() {
        listOf(
            MissionType.BUILD_TOWERS to { lv: Int -> SupplyBudgetModel.designedRoster(lv).size },
            MissionType.UPGRADE_TOWERS to { lv: Int ->
                SupplyBudgetModel.designedRoster(lv).size +
                    SupplyBudgetModel.DESIGNED_TIER_THREE_COUNT[lv - 1]
            },
        ).forEach { (type, perBattle) ->
            MissionPools.VOLUME.filter { it.type == type }.forEach { template ->
                val playable = 1..(template.minClearedLevels + 1).coerceAtMost(CAMPAIGN)
                val best = BATTLES_PER_DAY * playable.maxOf(perBattle)
                assertTrue(
                    "${template.id}: hedef ${template.target}, kapinin (min" +
                        "ClearedLevels=${template.minClearedLevels}) actigi noktada " +
                        "TASARLANAN oynanisla gunde en fazla $best yapiliyor",
                    best >= template.target,
                )
            }
        }
    }

    /**
     * `d_p_skip3` — hazirlik sayaci bolum basina dalga sayisi kadar atlanabilir,
     * yani hedef ilk bolumde bile fazlasiyla karsilanir.
     */
    @Test
    fun prepSkipTargetIsMetOnTheVeryFirstLevel() {
        val template = MissionPools.PARTICIPATION.first { it.id == "d_p_skip3" }
        val perDay = BATTLES_PER_DAY * GameConfigCampaignFacts.waveCount(1)
        assertTrue(
            "${template.id}: hedef ${template.target}, L1'de gunde $perDay atlama mumkun",
            perDay >= template.target,
        )
    }

    /**
     * `d_s_all_towers` — hedef "acik tum tipler"dir ve kapisi (L8) tam olarak
     * dorduncu kule tipinin acildigi yerdir; tasarlanan kadro o bolumde zaten
     * dort FARKLI tip iceriyor.
     */
    @Test
    fun allTowerTypesTargetIsMetByTheDesignedRosterAtItsGate() {
        val template = MissionPools.SKILL.first { it.id == "d_s_all_towers" }
        val level = template.minClearedLevels + 1
        val distinct = SupplyBudgetModel.designedRoster(level).distinct().size
        assertEquals(
            "${template.id}: kapida (L$level) tasarlanan kadro ${distinct} farkli tip " +
                "iceriyor, gorev ${template.minTowerTypes} istiyor",
            template.minTowerTypes,
            distinct,
        )
    }

    // =================================================================================
    // 7. HAFTALIK GELIR SIMULASYONU — kapi acilinca butce korunuyor mu
    // =================================================================================

    /**
     * **TASARLANAN GELIR KORUNDU.** Gorevin istedigi sekilde oynayan oyuncu
     * haftalik butcenin TAMAMINI alir: 7 x 360 + 1.100 = 3.620.
     *
     * Olcum kapisi kapaliyken de 3.620 olculuyordu, ama o rakam yaniltici bir
     * sekilde ucuzdu: gosterilen her gorev dalga tablosundan TURETILEREK
     * kendiliginden doluyordu, yani oyuncu hicbir sey yapmadan tavani
     * aliyordu. Kapi acildiktan sonra ayni tavan hala ULASILABILIR — degisen
     * tek sey, artik iki dokunusun (DALGA BASLAT ve 2x) oyuncudan gelmesi.
     *
     * Deterministik: 3.620 her tohumda birebir cikiyor.
     */
    @Test
    fun anEngagedPlayerStillEarnsTheFullDesignedWeeklyIncome() {
        val designed = EconomyConfig.DAILY_MAX_TOTAL * 7 + EconomyConfig.WEEKLY_BUDGET
        assertEquals("GDD E: 7 x 360 + 1.100", 3_620, designed)

        repeat(SIMULATED_INSTALLS) {
            assertEquals(
                "olcum bagliyken tasarlanan haftalik gelir teslim edilmiyor",
                designed,
                simulateWeek(intentional = true),
            )
        }
    }

    /**
     * **TABAN: GOREV PANELINI HIC OKUMAYAN OYUNCU.**
     *
     * Bu profil bolumu "tasarlandigi gibi" oynar (kadroyu kurar, kademe atlar,
     * kule satmaz) ama iki dugmeye HIC dokunmaz: DALGA BASLAT ve 2x. Yani
     * `d_p_skip3` ve `d_s_double_speed` cektigi gunlerde o slotu kaybeder.
     *
     * OLCULEN (400 kurulum): ort. **3.066 coin/hafta = tasarlananin %85'i**,
     * en kotu tohum 2.860. Kaybin tamami yukaridaki iki sablondan gelir —
     * olculdu, baska sizinti yok.
     *
     * Bu bilincli bir kabul: ikisi de gorev metninin ACIKCA istedigi TEK bir
     * dokunus ve ikisi de oyuncuya oyunun kendi hizlandirma araclarini
     * ogretiyor. Odul MIKTARLARI degistirilmedi; alternatif (bu iki sablonu
     * havuzdan cikarmak) Katilim ve Beceri slotlarini yeniden tek-secenege
     * dusururdu.
     *
     * Test bir TABAN kilitler: taban duserse ekonomi sessizce bozulmus
     * demektir.
     */
    @Test
    fun aPlayerWhoIgnoresTheMissionPanelStillClearsTheMeasuredFloor() {
        val designed = EconomyConfig.DAILY_MAX_TOTAL * 7 + EconomyConfig.WEEKLY_BUDGET
        val runs = (0 until SIMULATED_INSTALLS).map { simulateWeek(intentional = false) }

        assertTrue(
            "gorev panelini okumayan oyuncunun haftalik geliri olculen tabanin " +
                "(2.860) ALTINA dustu: ${runs.min()}",
            runs.min() >= 2_800,
        )
        assertTrue(
            "ortalama gelir tasarlananin %80'inin altina dustu: ${runs.average().toInt()}",
            runs.average() >= 0.80 * designed,
        )
        assertTrue(
            "bu profil tavani almamali — alsaydi iki beceri gorevi anlamsiz olurdu",
            runs.average() < designed,
        )
    }

    /**
     * Bir haftalik oynanisi surer ve YALNIZCA gorev kaynakli coin'i dondurur.
     *
     * Oynanis TASARLANAN modelden turer, elle uydurulmaz:
     * [SupplyBudgetModel.designedRoster] kac kule kurulacagini,
     * [SupplyBudgetModel.DESIGNED_TIER_THREE_COUNT] kacinin kademe atlayacagini
     * soyler — ayni model SPI'nin boleni.
     *
     * @param intentional oyuncu gorev panelini okuyup ona gore mi oynuyor
     *   (hazirlik sayacini atlar, 2x kullanir)?
     */
    private fun simulateWeek(intentional: Boolean): Int {
        val log = CoinLog()
        val clock = StepClock()
        // Kampanyanin onu duran oyuncu: haftalik yildiz gorevi icin ILK
        // temizleme arzi olmali (bkz. `improvingStarsAfterFinishing...`).
        val progress = CampaignProgressImpl(veteranSave(FIRST_UNPLAYED - 1), clock, log.sink)

        var level = FIRST_UNPLAYED
        repeat(7) { day ->
            if (day > 0) clock.advanceDays(1)
            progress.refreshCalendar()
            repeat(BATTLES_PER_DAY) {
                progress.beginBattle(level)
                // GameScreen'in savas basi tabani.
                progress.noteSellTrackingActive()
                progress.noteGameSpeed(1f)

                SupplyBudgetModel.designedRoster(level).forEach { progress.noteTowerBuilt(it) }
                repeat(
                    SupplyBudgetModel.designedRoster(level).size +
                        SupplyBudgetModel.DESIGNED_TIER_THREE_COUNT[level - 1]
                ) { progress.noteTowerUpgraded() }

                if (intentional) {
                    repeat(GameConfigCampaignFacts.waveCount(level)) {
                        progress.notePrepTimerSkipped()
                    }
                    progress.noteGameSpeed(2f)
                }

                progress.onLevelCleared(level, 20, 20)
                progress.endBattle()
                level++
            }
            progress.claimCompletedMissions()
        }
        return log.missionCoins
    }

    private fun CampaignProgressImpl.weeklyStars(): Int =
        weeklyMissions.first { it.type == MissionType.WEEKLY_STARS_EARNED }.progress

    private companion object {
        /** Act II ortasi: 4 kule tipi acik, dalga tablosu zirhli/tank iceriyor. */
        const val SIM_LEVEL = 20

        const val CAMPAIGN = EconomyConfig.CAMPAIGN_LEVELS

        /** Gunluk gorevlerin tasarim varsayimi: gunde 4 savas. */
        const val BATTLES_PER_DAY = 4

        /**
         * Gelir simulasyonunun ilk OYNANMAMIS bolumu (oncesi temizlenmis).
         * 19 secildi cunku `d_v_supply2500` ancak L18'den itibaren (2.650
         * Tedarik) karsilanabiliyor.
         */
        const val FIRST_UNPLAYED = 19

        /**
         * Gelir simulasyonunun tekrarlanacagi BAGIMSIZ kurulum sayisi. Gorev
         * secimi cihaz tohumuna bagli oldugu icin tek olcum "sansli tohum"
         * olabilir.
         */
        const val SIMULATED_INSTALLS = 60
    }
}
