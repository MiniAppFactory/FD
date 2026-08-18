package com.miniappfactory.frontlinedefender.tutorial

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.ui.TutorialFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ILK OTURUM OGRETICISI — KALICILIK.
 *
 * **SAF JUnit**: `SaveManager` [InMemoryKeyValueStore] ile kuruluyor, yani
 * `Context`/`SharedPreferences` HIC olusmuyor (`SaveManagerEconomyTest` ile
 * ayni desen).
 *
 * Ana sozlesme iki maddedir:
 * 1. Ogretici **bir kez** gosterilir; tamamlansa da atlansa da geri gelmez.
 * 2. Bozuk/cop kayit oyuncuyu ogreticiye HAPSETMEZ ve okuma cokmez.
 */
class TutorialSaveStateTest {

    private fun manager(store: InMemoryKeyValueStore = InMemoryKeyValueStore()) =
        SaveManager(store)

    // =======================================================================
    // 1) Varsayilanlar
    // =======================================================================

    @Test
    fun freshInstallHasNotSeenTheTutorial() {
        val save = manager()
        assertEquals(SaveManager.TUTORIAL_UNSEEN, save.tutorialStatus)
        assertFalse(save.tutorialSeen)
        assertTrue(
            "temiz kurulumda bolum 1 ogreticiyi acmali",
            TutorialFlow.shouldStart(TutorialFlow.TUTORIAL_LEVEL_ID, save.tutorialSeen)
        )
    }

    // =======================================================================
    // 2) Bir kez gosterilir
    // =======================================================================

    @Test
    fun completingHidesTheTutorialForever() {
        val store = InMemoryKeyValueStore()
        manager(store).markTutorialCompleted()

        // Yeni oturum: ayni depo, yeni SaveManager.
        val next = manager(store)
        assertEquals(SaveManager.TUTORIAL_COMPLETED, next.tutorialStatus)
        assertTrue(next.tutorialSeen)
        assertFalse(TutorialFlow.shouldStart(TutorialFlow.TUTORIAL_LEVEL_ID, next.tutorialSeen))
    }

    @Test
    fun skippingHidesTheTutorialForeverButStaysDistinguishable() {
        val store = InMemoryKeyValueStore()
        manager(store).markTutorialSkipped()

        val next = manager(store)
        assertTrue("atlamak da 'gorulmus' sayilir", next.tutorialSeen)
        assertEquals(
            "atlandi ile tamamlandi ayri kalmali (ilk oturum KPI'si)",
            SaveManager.TUTORIAL_SKIPPED,
            next.tutorialStatus
        )
    }

    // =======================================================================
    // 3) Sifirlama yollari
    // =======================================================================

    @Test
    fun resetTutorialBringsItBackForTesting() {
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        save.markTutorialCompleted()
        assertTrue(save.tutorialSeen)

        save.resetTutorial()
        assertFalse(save.tutorialSeen)
        // Yeni oturumda da geri gelmeli.
        assertFalse(manager(store).tutorialSeen)
    }

    @Test
    fun resetTutorialSurvivesEvenWhenLevelOneIsAlreadyStarred() {
        // Tohumlama mantigi bilincli sifirlamayi EZMEMELI: QA cihazda bolum 1
        // yildizliyken ogreticiyi tekrar acabilmeli.
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        save.setLevelStars(1, 3)
        save.markTutorialCompleted()

        save.resetTutorial()
        assertFalse("sifirlama tohumlama tarafindan geri alinamaz", manager(store).tutorialSeen)
    }

    @Test
    fun resettingAllProgressAlsoRestoresTheTutorial() {
        // Ilerlemeyi sifirlayan oyuncu bastan basliyor; ogretici de bastan.
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        save.markTutorialCompleted()
        save.resetProgress()

        assertFalse(manager(store).tutorialSeen)
    }

    // =======================================================================
    // 4) Bayrak eklenmeden ONCE olusmus kayitlar
    // =======================================================================

    @Test
    fun existingPlayerWhoAlreadyClearedLevelOneIsNotRetaught() {
        val store = InMemoryKeyValueStore()
        // Bayrak HIC yokken bolum 1 yildizli: ogretici oncesi bir kayit.
        store.putInt("level_stars_1", 2)

        assertTrue(
            "bolum 1'i bitirmis oyuncuya 'mevziye dokun' demek gerileme olurdu",
            manager(store).tutorialSeen
        )
    }

    @Test
    fun existingPlayerWhoNeverClearedLevelOneStillGetsTheTutorial() {
        val store = InMemoryKeyValueStore()
        store.putInt("level_stars_2", 3) // baska bolum yildizli, bolum 1 degil

        assertFalse(manager(store).tutorialSeen)
    }

    // =======================================================================
    // 5) Bozuk kayit — cokme yok, kilitlenme yok
    // =======================================================================

    @Test
    fun garbageValueFallsBackToDefaultInsteadOfThrowing() {
        val store = InMemoryKeyValueStore()
        store.putRaw("tutorial_status", "evet") // yanlis TIP

        val save = manager(store)
        assertEquals(SaveManager.TUTORIAL_UNSEEN, save.tutorialStatus)
        assertFalse(save.tutorialSeen)
    }

    @Test
    fun outOfRangeValuesAreClampedNotTrusted() {
        val store = InMemoryKeyValueStore()
        val save = manager(store)

        store.putRaw("tutorial_status", 9999)
        assertEquals(SaveManager.TUTORIAL_SKIPPED, save.tutorialStatus)
        assertTrue("aralik disi deger oyuncuyu ogreticiye HAPSETMEZ", save.tutorialSeen)

        store.putRaw("tutorial_status", -42)
        assertEquals(SaveManager.TUTORIAL_UNSEEN, save.tutorialStatus)
    }

    @Test
    fun writingAnAbsurdStatusIsClampedOnTheWaySoStorageStaysValid() {
        val store = InMemoryKeyValueStore()
        val save = manager(store)

        save.tutorialStatus = 77
        assertEquals(SaveManager.TUTORIAL_SKIPPED, save.tutorialStatus)

        save.tutorialStatus = -1
        assertEquals(SaveManager.TUTORIAL_UNSEEN, save.tutorialStatus)
    }

    @Test
    fun tutorialFlagNeverDisturbsTheEconomySave() {
        // Bayrak ekonomi alanlarindan tamamen ayri bir anahtarda durur.
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        val before = save.loadWallet()

        save.markTutorialCompleted()

        assertEquals(before, save.loadWallet())
        assertEquals(0, save.loadWallet().coins)
    }
}
