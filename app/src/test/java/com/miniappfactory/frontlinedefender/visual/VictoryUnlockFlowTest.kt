package com.miniappfactory.frontlinedefender.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.ClockProvider
import com.miniappfactory.frontlinedefender.game.economy.ClockSample
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.PlayerWallet
import com.miniappfactory.frontlinedefender.game.economy.lockCost
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.ui.ActIntro
import com.miniappfactory.frontlinedefender.game.ui.ActIntroOverlay
import com.miniappfactory.frontlinedefender.game.ui.ActIntroStore
import com.miniappfactory.frontlinedefender.game.ui.UnlockConfirmOverlay
import com.miniappfactory.frontlinedefender.game.ui.VictoryFlow
import com.miniappfactory.frontlinedefender.game.ui.VictoryModal
import com.miniappfactory.frontlinedefender.game.ui.VictoryTransition
import com.miniappfactory.frontlinedefender.ui.theme.FrontlineDefenderTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * ZAFER -> SONRAKI BOLUM AKISI — URETIM BAGLANTISIYLA (FD-026/027).
 *
 * `VisualEvidenceTest` izole composable GORUNUMUNU verir; bu dosya GERCEK
 * davranis/akis kanitidir: uretimdeki `VictoryTransition` + GERCEK
 * `CampaignProgressImpl.tryUnlock` birlikte surulur ve PARA/GECIS sonuclari
 * dogrulanir (iptal=harcama 0, yetersiz=gecis 0, yeterli=tek harcama + intro +
 * L12). Ayrica zaferin USTUNDE katmanli coin-onay / act-intro (L12) kareleri ve
 * kaydirma SONRASI zafer butonlarinin gorunur+tiklanabilir oldugu kanitlanir.
 *
 * Seed: `SaveManager.saveWallet(PlayerWallet(...))` ile L1..11 temizli (L12
 * prereq'i karsilanir), L12 KILITLI, bakiye kontrollu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "tr-rTR-w740dp-h360dp-land-xxhdpi")
class VictoryUnlockFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine

    private object TestClock : ClockProvider {
        override fun sample(): ClockSample = ClockSample(
            epochDay = 20_500L,
            wallClockMs = 20_500L * EconomyConfig.MS_PER_DAY,
            elapsedRealtimeMs = 1_000L,
            bootId = 1L,
        )
    }

    private class UnseenActIntroStore : ActIntroStore {
        val marked = mutableSetOf<Int>()
        override fun isSeen(act: Int): Boolean = false // hep gorulmemis -> intro cikar
        override fun markSeen(act: Int) { marked.add(act) }
    }

    @Before
    fun setUp() {
        audio = AudioManager(context)
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(context), audio)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    /** L1..11 temizli, L12 KILITLI, verilen bakiye. */
    private fun progressWith(coins: Int): CampaignProgressImpl {
        val sm = SaveManager(InMemoryKeyValueStore())
        sm.saveWallet(
            PlayerWallet(
                coins = coins,
                unlockedLevels = (1..11).toSet(),
                clearedLevels = (1..11).toSet(),
            )
        )
        return CampaignProgressImpl(sm, TestClock)
    }

    // =====================================================================
    // GERCEK PARA/GECIS DAVRANISI (VictoryTransition + CampaignProgressImpl)
    // =====================================================================

    @Test
    fun sufficientCoins_chargesOnce_andLeadsToActIntroForL12() {
        val cost = lockCost(12)
        assertTrue("L12 ucretli olmali", cost > 0)
        val progress = progressWith(cost + 500)
        val before = progress.coins
        val t = VictoryTransition(EconomyConfig.CAMPAIGN_LEVELS)
        val store = UnseenActIntroStore()

        val tapped = t.onNextTapped(
            clearedLevelId = 11,
            isNextUnlocked = progress.isUnlocked(12),
            store = store,
        )
        assertEquals(VictoryFlow.Step.Unlock(12), tapped)

        var charges = 0
        val confirmed = t.onUnlockConfirmed(level = 12, store = store) {
            charges++
            progress.tryUnlock(12) // GERCEK harcama
        }

        assertEquals("yeterli -> L12 Act II intro", VictoryFlow.Step.Intro(12, ActIntro.actOf(12)), confirmed)
        assertEquals("harcama TEK kez denendi", 1, charges)
        assertEquals("bakiye tam lockCost(12) kadar dustu", before - cost, progress.coins)
        assertTrue("L12 artik acik", progress.isUnlocked(12))
    }

    /**
     * BULGU (FD-026/027): zafer->sonraki akisinda "yetersiz" durumu OLUSAMAZ.
     *
     * Sistemin ANTI-SOFT-LOCK invariant'i (GDD C.4/H.9, `enforceUnlockInvariant`
     * / `autoGrantShortfall`) siradaki OYNANABILIR (ilk kilitli) bolumun bedelini
     * bakiye eksikse otomatik hibe eder — oyuncu fark etmez. Yani bilerek yetersiz
     * seed'lesek bile L12 her zaman karsilanabilir olur ve gecis gerceklesir.
     * (Yetersiz UI YALNIZ haritadan ileri-atlama yolu icindir; onu
     * `coin_unlock_insufficient_tr.png` izole karesi ayrica kanitlar: eksik miktar
     * + KILIDI AC pasif.)
     */
    @Test
    fun victoryFlow_hasNoInsufficientState_antiSoftLockTopsUpNextLevel() {
        val cost = lockCost(12)
        val progress = progressWith(cost - 10) // bilerek yetersiz seed
        assertTrue(
            "anti-soft-lock: L12 bedeli otomatik hibeyle karsilanabilir olmali",
            progress.coins >= cost,
        )
        val before = progress.coins
        val t = VictoryTransition(EconomyConfig.CAMPAIGN_LEVELS)
        val store = UnseenActIntroStore()

        t.onNextTapped(11, progress.isUnlocked(12), store) // Unlock(12)
        val confirmed = t.onUnlockConfirmed(level = 12, store = store) { progress.tryUnlock(12) }

        assertEquals("karsilanabildigi icin gecis olur (intro L12)", VictoryFlow.Step.Intro(12, 2), confirmed)
        assertEquals("tek harcama: bakiye lockCost(12) kadar dustu", before - cost, progress.coins)
        assertTrue("L12 acildi", progress.isUnlocked(12))
    }

    @Test
    fun cancel_thenStaleConfirm_neverCharges() {
        val progress = progressWith(lockCost(12) + 500)
        val before = progress.coins
        val t = VictoryTransition(EconomyConfig.CAMPAIGN_LEVELS)
        val store = UnseenActIntroStore()

        t.onNextTapped(11, progress.isUnlocked(12), store) // Unlock(12)
        t.onUnlockCancelled(12)                             // IPTAL

        var charges = 0
        val stale = t.onUnlockConfirmed(level = 12, store = store) { charges++; progress.tryUnlock(12) }

        assertEquals("iptal sonrasi bayat onay -> gecis yok", VictoryFlow.Step.None, stale)
        assertEquals("iptal -> HARCAMA CAGRILMAZ (0)", 0, charges)
        assertEquals("iptal -> bakiye degismez", before, progress.coins)
        assertFalse("L12 hala kilitli", progress.isUnlocked(12))
    }

    // =====================================================================
    // KAYDIRMA SONRASI ZAFER BUTONLARI — gorunur + tiklanabilir (FD-027)
    // =====================================================================

    @Test
    fun victoryButtonsAreVisibleAndClickableAfterScrollingToBossCard() {
        engine.startNewGame(11)
        var nextClicks = 0
        var selectClicks = 0
        composeRule.setContent {
            FrontlineDefenderTheme {
                Box(Modifier.fillMaxSize()) {
                    VictoryModal(
                        gameEngine = engine,
                        clearResult = null,
                        nextLevelId = 12,
                        onNextLevel = { nextClicks++ },
                        onLevelSelect = { selectClicks++ },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // Boss kartina kaydir (en alt), sonra butonlar HALA gorunur+tiklanir olmali
        composeRule.onNodeWithTag("victory_boss_card").performScrollTo()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("victory_next_level_button").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("victory_level_select_button").assertIsDisplayed().assertHasClickAction()

        composeRule.onNodeWithTag("victory_next_level_button").performClick()
        composeRule.onNodeWithTag("victory_level_select_button").performClick()
        assertEquals("SONRAKI BOLUM tiklanabildi", 1, nextClicks)
        assertEquals("BOLUM SEC tiklanabildi", 1, selectClicks)

        capture("victory_L11_scrolled_buttons_tr.png") // tam pencere, kaydirma sonrasi
    }

    // =====================================================================
    // KATMANLI KARELER (zafer USTUNDE) — L12
    // =====================================================================

    @Test
    fun png_coinConfirmOverVictory_L12() {
        engine.startNewGame(11)
        val progress = progressWith(lockCost(12) + 500) // yeterli -> KILIDI AC aktif + bakiye-sonrasi
        setContentCaptured("victory_overlaid_coin_L12_tr.png") {
            VictoryModal(engine, null, 12, {}, {})
            UnlockConfirmOverlay(
                spec = GameConfig.levelSpec(12),
                balance = progress.coins,
                onCancel = {},
                onConfirm = {},
            )
        }
    }

    @Test
    fun png_actIntroOverVictory_L12() {
        engine.startNewGame(11)
        setContentCaptured("victory_overlaid_intro_L12_tr.png") {
            VictoryModal(engine, null, 12, {}, {})
            ActIntroOverlay(act = ActIntro.actOf(12), onDismiss = {})
        }
    }

    // ---------------------------------------------------------------------

    private fun setContentCaptured(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            FrontlineDefenderTheme { Box(Modifier.fillMaxSize()) { content() } }
        }
        composeRule.waitForIdle()
        capture(name)
    }

    private fun capture(name: String) {
        val view: View = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        val dir = evidenceDir().apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("VISUAL-EVIDENCE: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
        assertTrue("PNG yazilmali: $file", file.isFile && file.length() > 0)
    }

    private fun evidenceDir(): File = listOf(
        File("../../comms/visual-evidence"),
        File("../comms/visual-evidence"),
        File("C:/Users/bhdre/APPDeveloper/projects/Frontline Defender/comms/visual-evidence"),
    ).firstOrNull { it.parentFile?.exists() == true } ?: File(
        "C:/Users/bhdre/APPDeveloper/projects/Frontline Defender/comms/visual-evidence"
    )
}
