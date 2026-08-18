package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 9 — KAYIT DAYANIKLILIGI.
 *
 * **SAF JUnit**: `SaveManager` [InMemoryKeyValueStore] ile kuruluyor, yani
 * `Context`/`SharedPreferences` HIC olusmuyor ve Robolectric gerekmiyor.
 *
 * Ana sozlesme: **bozuk veya eksik kayitta cokme yok, varsayilana donulur.**
 * Kayit bozulmasi oyuncunun oyunu acamamasi anlamina gelmez.
 */
class SaveManagerEconomyTest {

    private fun manager(store: InMemoryKeyValueStore = InMemoryKeyValueStore()) = SaveManager(store)

    // =================================================================================
    // 1. Bos kayit -> saglikli varsayilanlar
    // =================================================================================

    @Test
    fun freshInstallProducesUsableDefaults() {
        val save = manager()
        val wallet = save.loadWallet()

        assertEquals(0, wallet.coins)
        assertEquals("bolum 1-6 bedava ve acik", (1..6).toSet(), wallet.unlockedLevels)
        assertTrue(wallet.clearedLevels.isEmpty())
        assertEquals(0, wallet.starsOf(1))
        assertEquals(MetaUpgrades(), save.loadMetaUpgrades())
        assertEquals(RequisitionState(), save.loadRequisitionState())
        assertEquals(0, save.boostedReplaysUsedToday)
        assertTrue(save.soundEnabled)
        assertEquals(EconomyConfig.SAVE_VERSION, save.saveVersion)
    }

    @Test
    fun missionSeedIsGeneratedOnceAndIsStable() {
        val save = manager()
        val first = save.missionSeed
        assertTrue("tohum 0 olamaz, yoksa tum cihazlar ayni gorevi alir", first != 0L)
        assertEquals("tohum degismemeli", first, save.missionSeed)
        // Ayni tohum + ayni gun -> ayni 3 gorev (determinizm sozlesmesi).
        assertEquals(
            dailyMissions(20_500L, first).map { it.id },
            dailyMissions(20_500L, save.missionSeed).map { it.id },
        )
    }

    // =================================================================================
    // 2. Tur gidis-donus
    // =================================================================================

    @Test
    fun walletRoundTripsThroughStorage() {
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        val wallet = PlayerWallet(
            coins = 1_234,
            unlockedLevels = (1..9).toSet(),
            clearedLevels = (1..8).toSet(),
            bestStars = mapOf(1 to 3, 2 to 2, 8 to 1),
            perfectDefenseLevels = setOf(1),
            lifetimeEarned = 5_000,
            lifetimeSpent = 3_766,
        )
        save.saveWallet(wallet)

        val reloaded = manager(store).loadWallet()
        assertEquals(1_234, reloaded.coins)
        assertEquals((1..9).toSet(), reloaded.unlockedLevels)
        assertEquals((1..8).toSet(), reloaded.clearedLevels)
        assertEquals(3, reloaded.starsOf(1))
        assertEquals(2, reloaded.starsOf(2))
        assertEquals(1, reloaded.starsOf(8))
        assertEquals(setOf(1), reloaded.perfectDefenseLevels)
        assertEquals(5_000, reloaded.lifetimeEarned)
        assertEquals(3_766, reloaded.lifetimeSpent)
    }

    @Test
    fun starsUseTheSameStorageAsTheEngine() {
        // Tek dogruluk kaynagi: motorun `setLevelStars` yazdigi anahtar, ekonominin
        // okudugu anahtardir. Iki kopya olsaydi yildizlar sessizce ayrisirdi.
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        save.setLevelStars(7, 2)
        assertEquals(2, save.loadWallet().starsOf(7))

        save.saveWallet(save.loadWallet().copy(bestStars = mapOf(7 to 3)))
        assertEquals("ekonomi yazinca motor da 3 gorur", 3, save.getLevelStars(7))

        save.setLevelStars(7, 1)
        assertEquals("yildiz ASLA dusmez", 3, save.getLevelStars(7))
    }

    @Test
    fun metaUpgradesRoundTrip() {
        val store = InMemoryKeyValueStore()
        val upgrades = MetaUpgrades(firepower = 3, optics = 2, startingSupplyRank = 2, fortification = 2, salvage = 4)
        manager(store).saveMetaUpgrades(upgrades)
        assertEquals(upgrades, manager(store).loadMetaUpgrades())
    }

    @Test
    fun missionStateRoundTripsIncludingWeeklyProgress() {
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        val state = save.loadMissionState().copy(
            lastResetEpochDay = 20_500L,
            lastWeeklyResetWeek = 2_929L,
            clockSuspect = true,
            rewardMultiplier = 0.5,
            dailyResetsInWindow = 4,
            weekly = advanceWeekly(MissionPools.weekly(), MissionType.WEEKLY_LEVELS_COMPLETED, 7),
        )
        save.saveMissionState(state)

        val reloaded = manager(store).loadMissionState()
        assertEquals(20_500L, reloaded.lastResetEpochDay)
        assertEquals(2_929L, reloaded.lastWeeklyResetWeek)
        assertTrue(reloaded.clockSuspect)
        assertEquals(0.5, reloaded.rewardMultiplier, 1e-9)
        assertEquals(4, reloaded.dailyResetsInWindow)
        assertEquals(7, reloaded.weekly.first().progress)
        assertFalse(reloaded.weekly.first().isComplete)
    }

    @Test
    fun dailyProgressRoundTripsByMissionId() {
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        val missions = dailyMissions(20_500L, 99L, clearedLevels = 10, unlockedTowerTypes = 4)
            .map { it.advanced(1) }
        save.saveDailyProgress(missions)

        val progress = manager(store).loadDailyProgress()
        missions.forEach { assertEquals(1, progress[it.id]) }
        assertTrue(manager(store).loadDailyClaims().isEmpty())
    }

    // =================================================================================
    // 3. BOZUK KAYIT — cokme yok, varsayilana donus
    // =================================================================================

    @Test
    fun garbageTypesFallBackToDefaultsWithoutCrashing() {
        val store = InMemoryKeyValueStore()
        // Yanlis TIP: Int beklenen yere String, String beklenen yere Int.
        store.putRaw("eco_coins", "banana")
        store.putRaw("eco_unlocked_levels", 42)
        store.putRaw("eco_upg_firepower", "cok")
        store.putRaw("level_stars_3", "three")
        store.putRaw("eco_last_reset_day", "yesterday")
        store.putRaw("sound_enabled", "yes")

        val save = manager(store)
        val wallet = save.loadWallet()
        assertEquals(0, wallet.coins)
        assertEquals((1..6).toSet(), wallet.unlockedLevels)
        assertEquals(0, wallet.starsOf(3))
        assertEquals(MetaUpgrades(), save.loadMetaUpgrades())
        assertEquals(Long.MIN_VALUE, save.loadMissionState().lastResetEpochDay)
        assertTrue(save.soundEnabled)
    }

    @Test
    fun outOfRangeValuesAreClampedNotRejected() {
        val store = InMemoryKeyValueStore()
        // Elle kurcalanmis kayit: negatif coin, imkansiz rank, imkansiz yildiz.
        store.putRaw("eco_coins", -5_000)
        store.putRaw("eco_upg_firepower", 99)
        store.putRaw("eco_upg_salvage", -3)
        store.putRaw("level_stars_1", 17)
        store.putRaw("eco_r1_coins_paid_today", 999_999)
        store.putRaw("eco_reward_multiplier_pct", 4_000)

        val save = manager(store)
        val wallet = save.loadWallet() // MetaUpgrades/PlayerWallet `require`'lari patlamamali
        assertEquals("negatif bakiye 0'a kirpilir", 0, wallet.coins)
        assertEquals("yildiz 0..3 arasina kirpilir", 3, wallet.starsOf(1))

        val upgrades = save.loadMetaUpgrades()
        assertEquals(UpgradeLine.FIREPOWER.maxRank, upgrades.firepower)
        assertEquals(0, upgrades.salvage)

        assertEquals(
            EconomyConfig.R1_COIN_BUDGET_PER_DAY,
            save.loadRequisitionState().coinsPaidToday,
        )
        assertEquals(1.0, save.loadMissionState().rewardMultiplier, 1e-9)
    }

    @Test
    fun corruptLevelListsAreParsedTokenByToken() {
        val store = InMemoryKeyValueStore()
        // Yarim yazilmis / cop token'lar iceren liste. Gecerli olanlar KURTARILIR.
        store.putRaw("eco_unlocked_levels", "1,2,,x,7,8,99,-4, 9 ")
        store.putRaw("eco_cleared_levels", ";;;")
        store.putRaw("eco_perfect_levels", "3:3:3")

        val wallet = manager(store).loadWallet()
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7, 8, 9), wallet.unlockedLevels)
        assertTrue("cop temizlik listesi bos kabul edilir", wallet.clearedLevels.isEmpty())
        assertTrue(wallet.perfectDefenseLevels.isEmpty())
    }

    @Test
    fun corruptWeeklyProgressFallsBackToZeroProgress() {
        val store = InMemoryKeyValueStore()
        store.putRaw("eco_weekly_progress", "w_long_patrol:abc,w_elite_operator")
        val weekly = manager(store).loadMissionState().weekly
        assertEquals(EconomyConfig.WEEKLY_MISSION_COUNT, weekly.size)
        weekly.forEach { assertEquals(0, it.progress) }
        assertEquals(EconomyConfig.WEEKLY_BUDGET, weekly.sumOf { it.reward })
    }

    @Test
    fun inconsistentSaveIsSilentlyRepaired() {
        // Yildizi olan bir bolum "temizlenmemis" ve "kilitli" gorunuyor: imkansiz durum.
        val store = InMemoryKeyValueStore()
        store.putRaw("level_stars_8", 2)
        store.putRaw("eco_cleared_levels", "")
        store.putRaw("eco_unlocked_levels", "1,2,3,4,5,6")

        val wallet = manager(store).loadWallet()
        assertTrue("yildizi olan bolum temizlenmis sayilir", 8 in wallet.clearedLevels)
        assertTrue("oynanmis bolum kilitli kalamaz", 8 in wallet.unlockedLevels)
    }

    @Test
    fun walletFromCorruptSaveNeverBreaksTheSoftLockInvariant() {
        // En kotu durum: Faz 9 kaydi bozulmus (bakiye cop) ama L6 temizlenmis.
        // `save_version` yazili oldugu icin migrasyon KOSMAZ, yani L7 bedava acilmaz.
        val store = InMemoryKeyValueStore()
        store.putRaw("save_version", EconomyConfig.SAVE_VERSION)
        store.putRaw("eco_coins", "cop")
        store.putRaw("eco_unlocked_levels", "1,2,3,4,5,6")
        store.putRaw("level_stars_6", 1)
        val wallet = manager(store).loadWallet()
        assertEquals(0, wallet.coins)
        // L7 oynanabilir hale geldi ama bakiye 0 -> katman 4 hibeyi hesaplar.
        assertTrue(prerequisiteCleared(wallet, 7))
        assertEquals(lockCost(7), autoGrantShortfall(wallet))
        // Hibe uygulandiktan sonra invariant dogru.
        assertEquals(0, autoGrantShortfall(wallet.credited(autoGrantShortfall(wallet))))
    }

    // =================================================================================
    // 4. Surum / migrasyon
    // =================================================================================

    @Test
    fun legacyVersionOneSaveIsMigratedWithoutLosingStars() {
        val store = InMemoryKeyValueStore()
        // Faz 1-8 kaydi: yalnizca yildizlar, skor ve ses. `save_version` YOK.
        store.putRaw("level_stars_1", 3)
        store.putRaw("level_stars_2", 2)
        store.putRaw("level_stars_3", 1)
        store.putRaw("high_score", 4_200)
        store.putRaw("sound_enabled", false)

        val save = manager(store)
        assertEquals(EconomyConfig.SAVE_VERSION, save.saveVersion)
        val wallet = save.loadWallet()
        assertEquals(3, wallet.starsOf(1))
        assertEquals(setOf(1, 2, 3), wallet.clearedLevels)
        assertEquals(4_200, save.highScore)
        assertFalse(save.soundEnabled)
        // Migrasyon sonrasi soft-lock invariant'i dogru olmali (analytics gurultusu yok).
        assertEquals(0, autoGrantShortfall(wallet))
    }

    @Test
    fun migrationUnlocksTheStarChainWithoutInventingCoins() {
        val store = InMemoryKeyValueStore()
        (1..6).forEach { store.putRaw("level_stars_$it", 1) }

        val wallet = manager(store).loadWallet()
        assertTrue("L6 temizlendigi icin L7 acik sayilir", 7 in wallet.unlockedLevels)
        assertFalse("L8 acilmaz — bedeli odenmedi", 8 in wallet.unlockedLevels)
        assertEquals("migrasyon coin URETMEZ", 0, wallet.coins)
        // Siradaki kilit (L8) oynanamaz durumda oldugu icin invariant baglayici degil.
        assertFalse(prerequisiteCleared(wallet, 8))
        assertEquals(0, autoGrantShortfall(wallet))
    }

    @Test
    fun migrationRunsOnlyOnceAndDoesNotOverwriteLaterProgress() {
        val store = InMemoryKeyValueStore()
        (1..6).forEach { store.putRaw("level_stars_$it", 1) }
        val first = manager(store)
        first.saveWallet(first.loadWallet().copy(coins = 900, unlockedLevels = (1..9).toSet()))

        val second = manager(store).loadWallet()
        assertEquals("migrasyon ikinci kez kosmaz", 900, second.coins)
        assertEquals((1..9).toSet(), second.unlockedLevels)
    }

    @Test
    fun futureVersionSaveIsLeftUntouched() {
        val store = InMemoryKeyValueStore()
        store.putRaw("save_version", EconomyConfig.SAVE_VERSION + 5)
        store.putRaw("eco_coins", 777)
        val save = manager(store)
        assertEquals("ileri surumlu kayit SILINMEZ", 777, save.loadWallet().coins)
    }

    // =================================================================================
    // 5. Gunluk sayaclar ve ilerleme sifirlama
    // =================================================================================

    @Test
    fun dailyCounterResetDoesNotTouchCampaignProgress() {
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        save.saveWallet(PlayerWallet(coins = 900, unlockedLevels = (1..9).toSet(), clearedLevels = (1..8).toSet()))
        save.saveMetaUpgrades(MetaUpgrades(firepower = 3))
        save.saveRequisitionState(RequisitionState(filledViewsToday = 3, coinsPaidToday = 450))
        save.boostedReplaysUsedToday = 3

        save.resetDailyCounters()

        assertEquals(RequisitionState(), save.loadRequisitionState())
        assertEquals(0, save.boostedReplaysUsedToday)
        assertEquals("coin ETKILENMEZ", 900, save.loadWallet().coins)
        assertEquals("yukseltme ETKILENMEZ", 3, save.loadMetaUpgrades().firepower)
        assertEquals("kilit durumu ETKILENMEZ", (1..9).toSet(), save.loadWallet().unlockedLevels)
    }

    @Test
    fun resetProgressWipesEconomyButKeepsSettings() {
        // GDD H.1: ilerleme sifirlanir, ayarlar (ses/dil) KORUNUR.
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        save.soundEnabled = false
        save.saveWallet(PlayerWallet(coins = 5_000, unlockedLevels = (1..15).toSet(), clearedLevels = (1..14).toSet()))
        save.saveMetaUpgrades(MetaUpgrades(firepower = 4))
        save.setLevelStars(14, 3)

        save.resetProgress()

        val wallet = save.loadWallet()
        assertEquals(0, wallet.coins)
        assertEquals((1..6).toSet(), wallet.unlockedLevels)
        assertTrue(wallet.clearedLevels.isEmpty())
        assertEquals(0, save.getLevelStars(14))
        assertEquals(MetaUpgrades(), save.loadMetaUpgrades())
        assertFalse("ses ayari korunur", save.soundEnabled)
        assertEquals(EconomyConfig.SAVE_VERSION, save.saveVersion)
    }
}
