package com.miniappfactory.frontlinedefender.tutorial

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.ui.HintFlow
import com.miniappfactory.frontlinedefender.game.ui.UnlockHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KILIT ACILMA IPUCLARI — KALICILIK.
 *
 * **SAF JUnit**: `SaveManager` [InMemoryKeyValueStore] ile kuruluyor, yani
 * `Context`/`SharedPreferences` HIC olusmuyor (`TutorialSaveStateTest` ile ayni
 * desen).
 *
 * Ana sozlesme uc maddedir:
 *  1. Her ipucu KENDI bayragini tasir; biri gosterildiginde digerleri
 *     etkilenmez.
 *  2. Gosterilen ipucu oturumlar arasi geri gelmez.
 *  3. Bozuk/cop kayit ne cokme ne de sonsuz ipucu uretir.
 */
class UnlockHintSaveStateTest {

    private fun manager(store: InMemoryKeyValueStore = InMemoryKeyValueStore()) =
        SaveManager(store)

    // =======================================================================
    // 1) Varsayilanlar
    // =======================================================================

    @Test
    fun freshInstallHasSeenNoHints() {
        val save = manager()
        for (hint in UnlockHint.values()) {
            assertFalse("temiz kurulumda $hint gorulmemis olmali", save.isHintSeen(hint.saveId))
        }
        assertTrue(HintFlow.seenFrom(save).isEmpty())
        assertFalse(
            "temiz kurulumda gosterilecek ipucu VAR",
            HintFlow.isExhausted(HintFlow.start(HintFlow.seenFrom(save)))
        )
    }

    /**
     * Ogreticiden BILINCLI fark: ipuclarinin "eski kayit" tohumlamasi YOK.
     *
     * FUN_AUDIT'in tespiti, bolum 20'deki oyuncunun da kule rollerini
     * bilmedigidir — dersi veren hicbir sey yoktu. Dolayisiyla ilerlemis kayit
     * ipuclari GORUR. Ayni kayitta ogretici ise gorulmus sayilir.
     */
    @Test
    fun existingProgressStillGetsTheRoleHints() {
        val store = InMemoryKeyValueStore()
        manager(store).setLevelStars(1, 3)

        val next = manager(store)
        assertTrue(
            "bolum 1'i gecmis oyuncuya ilk oturum ogreticisi tekrar gosterilmez",
            next.tutorialSeen
        )
        assertTrue(
            "ama rol ipuclari ona da gosterilir — dersi hic almadi",
            HintFlow.seenFrom(next).isEmpty()
        )
    }

    // =======================================================================
    // 2) Bir kez gosterilir
    // =======================================================================

    @Test
    fun aSeenHintSurvivesTheSession() {
        val store = InMemoryKeyValueStore()
        manager(store).markHintSeen(UnlockHint.MISSILE_ROLE.saveId)

        val next = manager(store)
        assertTrue(next.isHintSeen(UnlockHint.MISSILE_ROLE.saveId))
        assertEquals(setOf(UnlockHint.MISSILE_ROLE), HintFlow.seenFrom(next))
    }

    @Test
    fun hintsAreIndependentOfEachOther() {
        val save = manager()
        save.markHintSeen(UnlockHint.CANNON_ROLE.saveId)

        assertTrue(save.isHintSeen(UnlockHint.CANNON_ROLE.saveId))
        for (other in UnlockHint.values().filter { it != UnlockHint.CANNON_ROLE }) {
            assertFalse("$other etkilenmemeli", save.isHintSeen(other.saveId))
        }
    }

    @Test
    fun markingTwiceIsHarmless() {
        val save = manager()
        save.markHintSeen(UnlockHint.FROST_ROLE.saveId)
        save.markHintSeen(UnlockHint.FROST_ROLE.saveId)
        assertTrue(save.isHintSeen(UnlockHint.FROST_ROLE.saveId))
    }

    @Test
    fun theHintFlagIsIndependentOfTheTutorialFlag() {
        val save = manager()
        save.markTutorialCompleted()
        for (hint in UnlockHint.values()) {
            assertFalse(
                "ogreticiyi bitirmek ipuclarini yakmamali ($hint)",
                save.isHintSeen(hint.saveId)
            )
        }

        save.resetHints()
        assertTrue("ipuclarini sifirlamak ogreticiyi geri getirmemeli", save.tutorialSeen)
    }

    // =======================================================================
    // 3) Sifirlama
    // =======================================================================

    /** Ayarlar ekranindaki "ipuclarini sifirla" satirinin arkasindaki cagri. */
    @Test
    fun resetHintsBringsThemAllBack() {
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        UnlockHint.values().forEach { save.markHintSeen(it.saveId) }
        assertTrue(HintFlow.isExhausted(HintFlow.start(HintFlow.seenFrom(save))))

        save.resetHints()

        // Silme KALICI olmali: yeni oturumda geri gelmemeli.
        val next = manager(store)
        assertTrue("sifirlama gercekten sifirlamali", HintFlow.seenFrom(next).isEmpty())
    }

    @Test
    fun resettingProgressAlsoClearsHints() {
        val save = manager()
        save.markHintSeen(UnlockHint.ARMOR_INTRO.saveId)
        save.resetProgress()
        assertFalse(save.isHintSeen(UnlockHint.ARMOR_INTRO.saveId))
    }

    /** Sifirlama yalnizca ipucu anahtarlarina dokunur; ilerleme KORUNUR. */
    @Test
    fun resetHintsLeavesProgressAlone() {
        val save = manager()
        save.setLevelStars(4, 3)
        save.markHintSeen(UnlockHint.CANNON_ROLE.saveId)

        save.resetHints()

        assertEquals("yildizlar korunmali", 3, save.getLevelStars(4))
    }

    // =======================================================================
    // 4) Bozuk kayit
    // =======================================================================

    @Test
    fun aCorruptFlagFallsBackToNotSeenWithoutCrashing() {
        val store = InMemoryKeyValueStore()
        store.putRaw("hint_seen_${UnlockHint.MISSILE_ROLE.saveId}", "evet")

        val save = manager(store)
        assertFalse(
            "yanlis tipteki deger cokme degil varsayilan uretmeli",
            save.isHintSeen(UnlockHint.MISSILE_ROLE.saveId)
        )
    }

    /**
     * Bos kimlik "gorulmus" doner: cagiran taraftaki bir hata en kotu ihtimalle
     * bir ipucunu SUSTURUR, oyuncuyu her hazirlik fazinda ayni seridi gormeye
     * MAHKUM ETMEZ.
     */
    @Test
    fun aBlankHintIdIsTreatedAsAlreadySeenAndIsNeverWritten() {
        val store = InMemoryKeyValueStore()
        val save = manager(store)

        assertTrue(save.isHintSeen(""))
        assertTrue(save.isHintSeen("   "))

        save.markHintSeen("")
        assertTrue(
            "bos kimlik icin anahtar YAZILMAMALI",
            store.keys().none { it.startsWith("hint_seen_") }
        )
    }
}
