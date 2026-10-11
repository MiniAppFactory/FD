package com.miniappfactory.frontlinedefender.story

import com.miniappfactory.frontlinedefender.game.economy.PlayerWallet
import com.miniappfactory.frontlinedefender.game.economy.canUnlock
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.ui.ActIntro
import com.miniappfactory.frontlinedefender.game.ui.ActIntroStore
import com.miniappfactory.frontlinedefender.game.ui.VictoryFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ZAFER -> SONRAKI BOLUM AKISI (FD-008).
 *
 * `GameScreen`in zafer dali Compose'dur ve burada kosmaz; kilitlenen sey, "SONRAKI
 * BOLUM"e dokununca ne olacagina karar veren SAF mantiktir (`VictoryFlow`). Ekran
 * yalnizca donen adimi uygular (pencere ac / savas baslat), yani bu testin
 * dogruladigi kararlar uretimde birebir yurur.
 *
 * FD-008 senaryolari:
 *   - acik sonraki            -> dogrudan Start (veya gorulmemis opener'da Intro)
 *   - kilitli sonraki         -> Unlock (haritaya donmeden onay penceresi)
 *   - gorulmemis perde acilisi (L11->12, L22->23) -> once Intro
 *   - gorulmus perde acilisi  -> dogrudan Start
 *   - kampanya finali (L55)   -> None (SONRAKI BOLUM cizilmez)
 *   - cift tik / zaten acik   -> tekrar UCRETLENDIRMEZ (canUnlock=false)
 *
 * Yetersiz bakiye / iptal / islem-ani bakiye kontrolu akisin PARA tarafindadir
 * (`tryUnlock` -> `canUnlock`: `coins >= lockCost`, zaten acik -> false) ve
 * `UnlockConfirmOverlay` (yetersizse onay kapali + eksik miktar yazili). Buradaki
 * `cannotRechargeAnAlreadyUnlockedLevel` o invaryanti dogrudan gosterir; pozitif
 * yol `CoinLedgerTest`te tum kampanya boyunca kanitli.
 */
class VictoryFlowTest {

    private val campaignLevels = GameConfig.CAMPAIGN_LEVEL_COUNT

    /** Bellek-ici gorulmus-perde deposu (ActIntroTest ile ayni desen). */
    private class FakeActIntroStore(seenActs: Set<Int> = emptySet()) : ActIntroStore {
        val seen = seenActs.toMutableSet()
        override fun isSeen(act: Int): Boolean = act in seen
        override fun markSeen(act: Int) { seen.add(act) }
    }

    // =====================================================================
    // nextCandidate — sonraki aday ve kampanya siniri
    // =====================================================================

    @Test
    fun nextCandidateIsTheFollowingLevelWithinTheCampaign() {
        assertEquals(11, VictoryFlow.nextCandidate(10, campaignLevels))
        assertEquals(campaignLevels, VictoryFlow.nextCandidate(campaignLevels - 1, campaignLevels))
    }

    @Test
    fun clearingTheFinalLevelHasNoNext() {
        assertEquals(
            "L$campaignLevels (final) temizlenince SONRAKI BOLUM olmamali",
            null,
            VictoryFlow.nextCandidate(campaignLevels, campaignLevels)
        )
    }

    // =====================================================================
    // firstStep — acik / kilitli / opener
    // =====================================================================

    @Test
    fun lockedNextRoutesToTheUnlockConfirmation() {
        // L11 temizlendi, L12 KILITLI -> once onay penceresi (haritaya donmeden).
        val step = VictoryFlow.firstStep(
            clearedLevelId = 11,
            campaignLevels = campaignLevels,
            isNextUnlocked = false,
            store = FakeActIntroStore()
        )
        assertEquals(VictoryFlow.Step.Unlock(12), step)
    }

    @Test
    fun lockedNextRoutesToUnlockRegardlessOfBalance() {
        // firstStep bakiyeyi GIRDI olarak almaz: her kilitli sonraki onaya gider,
        // bakiye islem aninda tryUnlock icinde kontrol edilir ("islem-ani bakiye").
        val step = VictoryFlow.firstStep(48, campaignLevels, isNextUnlocked = false, store = null)
        assertTrue(step is VictoryFlow.Step.Unlock)
    }

    @Test
    fun unlockedActOpenerShowsTheIntroFirst_L11toL12() {
        val step = VictoryFlow.firstStep(
            clearedLevelId = 11, // sonraki L12 = Perde II acilisi
            campaignLevels = campaignLevels,
            isNextUnlocked = true,
            store = FakeActIntroStore()
        )
        assertEquals(VictoryFlow.Step.Intro(12, ActIntro.actOf(12)), step)
        assertEquals("L12 Perde II", 2, ActIntro.actOf(12))
    }

    @Test
    fun unlockedActOpenerShowsTheIntroFirst_L22toL23() {
        val step = VictoryFlow.firstStep(
            clearedLevelId = 22, // sonraki L23 = Perde III acilisi
            campaignLevels = campaignLevels,
            isNextUnlocked = true,
            store = FakeActIntroStore()
        )
        assertEquals(VictoryFlow.Step.Intro(23, 3), step)
    }

    @Test
    fun aSeenActOpenerStartsDirectlyWithNoIntro() {
        val store = FakeActIntroStore(seenActs = setOf(2))
        val step = VictoryFlow.firstStep(11, campaignLevels, isNextUnlocked = true, store = store)
        assertEquals(VictoryFlow.Step.Start(12), step)
    }

    @Test
    fun anUnlockedNonOpenerStartsDirectly() {
        // L12 temizlendi -> L13 (opener DEGIL) -> dogrudan savas.
        val step = VictoryFlow.firstStep(12, campaignLevels, isNextUnlocked = true, store = FakeActIntroStore())
        assertEquals(VictoryFlow.Step.Start(13), step)
    }

    @Test
    fun withoutAStoreAnOpenerStartsDirectly() {
        // Act-intro bagli degilse (store = null) eski davranis: kart yok, dogrudan savas.
        val step = VictoryFlow.firstStep(11, campaignLevels, isNextUnlocked = true, store = null)
        assertEquals(VictoryFlow.Step.Start(12), step)
    }

    @Test
    fun clearingTheFinalLevelYieldsNoStep() {
        val step = VictoryFlow.firstStep(campaignLevels, campaignLevels, isNextUnlocked = true, store = FakeActIntroStore())
        assertEquals(VictoryFlow.Step.None, step)
    }

    // =====================================================================
    // afterUnlock — kilit acildiktan SONRA perde karti mi, savas mi
    // =====================================================================

    @Test
    fun afterUnlockingAnOpenerTheIntroShowsThenNextTimeItStarts() {
        val store = FakeActIntroStore()
        // Kilit acildi; L12 gorulmemis opener -> once kart.
        val first = VictoryFlow.afterUnlock(12, store)
        assertEquals(VictoryFlow.Step.Intro(12, 2), first)
        // Kart kapaninca uretim markSeen yazar; sonrasi dogrudan savas olurdu.
        store.markSeen(ActIntro.actOf(12))
        assertEquals(VictoryFlow.Step.Start(12), VictoryFlow.afterUnlock(12, store))
    }

    @Test
    fun afterUnlockingANonOpenerItStartsDirectly() {
        assertEquals(VictoryFlow.Step.Start(13), VictoryFlow.afterUnlock(13, FakeActIntroStore()))
    }

    // =====================================================================
    // Para guvenligi — cift tik / zaten acik tekrar UCRETLENDIRMEZ
    // =====================================================================

    @Test
    fun cannotRechargeAnAlreadyUnlockedLevel() {
        // Varsayilan cuzdanda bedava bolumler (1..6) ZATEN acik. "Zaten acik ->
        // canUnlock=false" invaryanti, zaferdeki onay penceresinde tryUnlock'un
        // ikinci kez (cift tik) cagrilsa bile TEKRAR UCRETLENDIRMEYECEGINI
        // garanti eder; harcama yalnizca bir kez olur.
        val wallet = PlayerWallet()
        assertFalse(
            "zaten acik bir bolum TEKRAR acilamaz — cift tik cift ucret uretmez",
            canUnlock(wallet, 1)
        )
        assertFalse("acik bedava bolum tekrar acilamaz", canUnlock(wallet, 6))
        // Sirali ilerleme kapisi: prerequisiti temizlenmemis paid bolum de
        // acilamaz (onay penceresi acilsa bile tryUnlock false doner).
        assertFalse("L6 temizlenmeden L7 acilamaz", canUnlock(wallet, 7))
    }
}
