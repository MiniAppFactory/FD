package com.miniappfactory.frontlinedefender.story

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.ui.ActIntro
import com.miniappfactory.frontlinedefender.game.ui.ActIntroStore
import com.miniappfactory.frontlinedefender.game.ui.VictoryFlow
import com.miniappfactory.frontlinedefender.game.ui.VictoryTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ZAFER GECIS SAHIPLIGI (FD-010) — CIFT CALLBACK / IPTAL / BAYAT OVERLAY.
 *
 * `VictoryFlowTest` saf "hangi adim" kararini kilitler; bu test, o adimin UI
 * OMRUNU ve TEK-GECIS garantisini — yani FD-010'un isaret ettigi gercek
 * eszamanlilik tuzaklarini — `VictoryTransition` sahibi uzerinde dogrular.
 * Compose gerekmez: sahip saf Kotlin snapshot state tutar.
 */
class VictoryTransitionTest {

    private val levels = GameConfig.CAMPAIGN_LEVEL_COUNT

    private class FakeActIntroStore(seen: Set<Int> = emptySet()) : ActIntroStore {
        val seenActs = seen.toMutableSet()
        override fun isSeen(act: Int): Boolean = act in seenActs
        override fun markSeen(act: Int) { seenActs.add(act) }
    }

    private fun transition() = VictoryTransition(levels)

    // ---- Cift dokunus tek gecis ----------------------------------------

    @Test
    fun aSecondTapAfterADirectStartDoesNothing() {
        val t = transition()
        val store = FakeActIntroStore(seen = setOf(2)) // L12 opener'i gorulmus
        // L11 temizlendi, L12 acik ve opener gorulmus -> dogrudan START.
        val first = t.onNextTapped(clearedLevelId = 11, isNextUnlocked = true, store = store)
        assertEquals(VictoryFlow.Step.Start(12), first)
        // Bayat/ cift callback: ayni karede ikinci dokunus HICBIR sey baslatmaz
        // (motorun degismis levelId'si uzerinden L13 baslatilamaz).
        val second = t.onNextTapped(clearedLevelId = 12, isNextUnlocked = true, store = store)
        assertEquals(VictoryFlow.Step.None, second)
    }

    @Test
    fun aSecondTapWhileUnlockWindowOpenDoesNothing() {
        val t = transition()
        val first = t.onNextTapped(clearedLevelId = 11, isNextUnlocked = false, store = null)
        assertEquals(VictoryFlow.Step.Unlock(12), first)
        assertEquals(12, t.pendingUnlock)
        val second = t.onNextTapped(clearedLevelId = 11, isNextUnlocked = false, store = null)
        assertEquals("pencere acikken yeni gecis yok", VictoryFlow.Step.None, second)
    }

    // ---- Iptal / yetersiz bakiye zaferi korur --------------------------

    @Test
    fun cancellingUnlockPreservesVictoryAndAllowsRetry() {
        val t = transition()
        t.onNextTapped(clearedLevelId = 11, isNextUnlocked = false, store = null)
        t.onUnlockCancelled(12)
        assertNull("iptal pencereyi kapatir", t.pendingUnlock)
        // Iptal sonrasi tekrar denenebilir (sahiplenilmedi).
        val again = t.onNextTapped(clearedLevelId = 11, isNextUnlocked = false, store = null)
        assertEquals(VictoryFlow.Step.Unlock(12), again)
    }

    @Test
    fun insufficientBalanceKeepsPlayerInVictory() {
        val t = transition()
        t.onNextTapped(clearedLevelId = 11, isNextUnlocked = false, store = null)
        var charges = 0
        // Gecerli onay: harcama DENENIR ama tryUnlock false (yetersiz/zaten acik)
        // -> gecis yok, pencere kapanir.
        val step = t.onUnlockConfirmed(level = 12, store = null) { charges++; false }
        assertEquals(VictoryFlow.Step.None, step)
        assertEquals("gecerli onayda harcama bir kez denenir", 1, charges)
        assertNull(t.pendingUnlock)
        // Hala sahiplenilmedi; oyuncu tekrar deneyebilir.
        assertEquals(
            VictoryFlow.Step.Unlock(12),
            t.onNextTapped(clearedLevelId = 11, isNextUnlocked = false, store = null)
        )
    }

    @Test
    fun aStaleConfirmAfterCancelNeverSpends() {
        // FD-012: iptal/ekrandan cikis sonrasi BAYAT onay callback'i calissa bile
        // coin DUSMEMELI. Harcama sahip guard'inin ICINDE oldugu icin hic cagrilmaz.
        val t = transition()
        t.onNextTapped(clearedLevelId = 11, isNextUnlocked = false, store = null)
        t.onUnlockCancelled(12)
        var charges = 0
        // Mutable sahte cuzdan: charge CAGRILIRSA bakiye duser. Boylece "bakiye
        // ayni" iddiasi, charge'in HIC cagrilmadiginin GERCEK sonucudur
        // (FD-014 notu: sabit val immutability'yi tek basina kanitlamaz).
        var walletBalance = 5000
        val step = t.onUnlockConfirmed(level = 12, store = null) { charges++; walletBalance -= 120; true }
        assertEquals(VictoryFlow.Step.None, step)
        assertEquals("bayat onay HARCAMA CAGIRMAMALI (coin dusmez)", 0, charges)
        assertEquals("charge cagrilmadigi icin bakiye AYNEN kalir", 5000, walletBalance)
        assertNull(t.pendingUnlock)
    }

    @Test
    fun aStaleConfirmAfterLeavingVictoryNeverSpends() {
        // Ekrandan cikis (BackHandler/harita/ana menu) sonrasi bayat onay: harcama yok.
        val t = transition()
        t.onNextTapped(clearedLevelId = 11, isNextUnlocked = false, store = null)
        t.onLeftVictory()
        var charges = 0
        var walletBalance = 5000
        assertEquals(
            VictoryFlow.Step.None,
            t.onUnlockConfirmed(level = 12, store = null) { charges++; walletBalance -= 120; true }
        )
        assertEquals("cikis sonrasi bayat onay harcamaz", 0, charges)
        assertEquals("bakiye AYNEN kalir", 5000, walletBalance)
    }

    @Test
    fun aDoubleConfirmChargesOnlyOnce() {
        // Ayni karede iki onay: ilki harcar ve pencereyi kapatir; ikincisi bayat.
        val t = transition()
        t.onNextTapped(clearedLevelId = 12, isNextUnlocked = false, store = FakeActIntroStore())
        var charges = 0
        val first = t.onUnlockConfirmed(level = 13, store = FakeActIntroStore()) { charges++; true }
        val second = t.onUnlockConfirmed(level = 13, store = FakeActIntroStore()) { charges++; true }
        assertEquals(VictoryFlow.Step.Start(13), first)
        assertEquals(VictoryFlow.Step.None, second)
        assertEquals("cift onay yalniz BIR kez harcar", 1, charges)
    }

    // ---- Kilit -> perde karti -> savas zinciri -------------------------

    @Test
    fun unlockingAnUnseenOpenerShowsIntroThenStartsOnce() {
        val t = transition()
        val store = FakeActIntroStore()
        t.onNextTapped(clearedLevelId = 11, isNextUnlocked = false, store = store)
        // Kilit acildi (tryUnlock ok) -> L12 gorulmemis opener -> once kart.
        val afterUnlock = t.onUnlockConfirmed(level = 12, store = store) { true }
        assertEquals(VictoryFlow.Step.Intro(12, 2), afterUnlock)
        assertEquals(12, t.pendingIntro)
        assertNull(t.pendingUnlock)
        // Kart kapaninca tek sefer START.
        assertEquals(VictoryFlow.Step.Start(12), t.onIntroDismissed(12))
        // Bayat ikinci kapanis: hicbir sey.
        assertEquals(VictoryFlow.Step.None, t.onIntroDismissed(12))
    }

    @Test
    fun unlockingANonOpenerStartsDirectly() {
        val t = transition()
        t.onNextTapped(clearedLevelId = 12, isNextUnlocked = false, store = FakeActIntroStore())
        // L13 opener degil -> kilit acilinca dogrudan START.
        assertEquals(
            VictoryFlow.Step.Start(13),
            t.onUnlockConfirmed(level = 13, store = FakeActIntroStore()) { true }
        )
    }

    // ---- VICTORY disina cikis bayat overlay'i temizler -----------------

    @Test
    fun leavingVictoryClearsPendingWindowsAndOwnership() {
        val t = transition()
        t.onNextTapped(clearedLevelId = 43, isNextUnlocked = false, store = null) // L44 kilitli
        assertEquals(44, t.pendingUnlock)
        // BackHandler ile haritaya cikildi / ana menu / sonraki baslatildi:
        t.onLeftVictory()
        assertNull("bayat kilit penceresi temizlenmeli", t.pendingUnlock)
        assertNull(t.pendingIntro)
        // Yeni zafer temiz baslar.
        assertEquals(
            VictoryFlow.Step.Unlock(45),
            t.onNextTapped(clearedLevelId = 44, isNextUnlocked = false, store = null)
        )
    }

    // ---- Intro: markSeen yalniz kabul edilen kapanista (seen kirlenmez) ----

    /**
     * `GameScreen.onDismiss`in sadelestirilmis ikizi: markSeen YALNIZCA
     * onIntroDismissed `Start` dondurdugunde yazilir. Bayat/cikis sonrasi
     * dismiss `None` doner ve seen DEGISMEZ (FD-013 P1).
     */
    private fun dismiss(t: VictoryTransition, lvl: Int, store: FakeActIntroStore): VictoryFlow.Step {
        val step = t.onIntroDismissed(lvl)
        if (step is VictoryFlow.Step.Start) store.markSeen(ActIntro.actOf(lvl))
        return step
    }

    @Test
    fun aValidIntroDismissMarksSeenExactlyOnce() {
        val t = transition()
        val store = FakeActIntroStore()
        t.onNextTapped(clearedLevelId = 11, isNextUnlocked = true, store = store) // pendingIntro = 12
        assertEquals(VictoryFlow.Step.Start(12), dismiss(t, 12, store))
        assertEquals("perde II gorulmus isaretlenmeli", setOf(2), store.seenActs)
        // Bayat ikinci kapanis: None + seen DEGISMEZ (tekrar markSeen yok).
        assertEquals(VictoryFlow.Step.None, dismiss(t, 12, store))
        assertEquals(setOf(2), store.seenActs)
    }

    @Test
    fun aStaleIntroDismissAfterLeavingVictoryDoesNotMarkSeen() {
        val t = transition()
        val store = FakeActIntroStore()
        t.onNextTapped(clearedLevelId = 11, isNextUnlocked = true, store = store) // pendingIntro = 12
        t.onLeftVictory()
        // Ekrandan cikis sonrasi bayat kart kapanisi: gecis yok, seen DEGISMEZ.
        assertEquals(VictoryFlow.Step.None, dismiss(t, 12, store))
        assertEquals("cikis sonrasi bayat dismiss seen'i kirletmemeli", emptySet<Int>(), store.seenActs)
    }

    @Test
    fun finaleClearHasNoNextStep() {
        val t = transition()
        assertEquals(
            VictoryFlow.Step.None,
            t.onNextTapped(clearedLevelId = levels, isNextUnlocked = true, store = FakeActIntroStore())
        )
        assertNull(t.pendingUnlock)
        assertNull(t.pendingIntro)
    }

    @Test
    fun actOfResolvesOpenerActsForTheIntroStep() {
        // Sahip L22->23 (Perde III) ve L11->12 (Perde II) opener'larini dogru
        // perdeyle Intro'ya cevirir.
        assertEquals(3, ActIntro.actOf(23))
        val t = transition()
        val store = FakeActIntroStore()
        assertEquals(
            VictoryFlow.Step.Intro(23, 3),
            t.onNextTapped(clearedLevelId = 22, isNextUnlocked = true, store = store)
        )
    }
}
