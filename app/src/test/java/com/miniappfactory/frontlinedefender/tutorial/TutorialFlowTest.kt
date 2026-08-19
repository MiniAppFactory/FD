package com.miniappfactory.frontlinedefender.tutorial

import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.ui.TutorialFlow
import com.miniappfactory.frontlinedefender.game.ui.TutorialOutcome
import com.miniappfactory.frontlinedefender.game.ui.TutorialSignals
import com.miniappfactory.frontlinedefender.game.ui.TutorialStep
import com.miniappfactory.frontlinedefender.geometry.GeometryTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ILK OTURUM OGRETICISI — saf mantik.
 *
 * `TutorialFlow` bilincli olarak Compose'a bagimsiz bir `object`: hangi adimin
 * ne zaman bittigine ve vurgunun nereye dusecegine dair TUM karar burada,
 * composable yalnizca ciziyor. Bu sayede Robolectric OLMADAN, duz JVM
 * testinde kosuyor (`WavePreview` ile ayni desen).
 */
class TutorialFlowTest {

    private fun signals(
        state: GameState = GameState.PREPARATION,
        pad: Boolean = false,
        towers: Int = 0,
        supply: Int = 80
    ) = TutorialSignals(
        gameState = state,
        padSelected = pad,
        towerCount = towers,
        supply = supply
    )

    // =======================================================================
    // 1) ETKINLESME — yalnizca bolum 1, yalnizca ILK oynanis
    // =======================================================================

    @Test
    fun runsOnlyOnFirstPlayOfLevelOne() {
        assertTrue(
            "bolum 1 + hic gorulmemis -> kosar",
            TutorialFlow.shouldStart(levelId = 1, alreadySeen = false)
        )
        assertFalse(
            "bayrak yazilmissa BIR DAHA gosterilmez",
            TutorialFlow.shouldStart(levelId = 1, alreadySeen = true)
        )
        for (level in 2..GameConfig.CAMPAIGN.size) {
            assertFalse(
                "ogretici yalnizca bolum 1'de kosar (bolum $level)",
                TutorialFlow.shouldStart(levelId = level, alreadySeen = false)
            )
        }
    }

    @Test
    fun overlayNeverDrawsOverAModal() {
        assertTrue(TutorialFlow.isOverlayVisible(GameState.PREPARATION))
        assertTrue(TutorialFlow.isOverlayVisible(GameState.WAVE_RUNNING))
        // Bu dordunun her birinde ekranda bir modal var.
        assertFalse(TutorialFlow.isOverlayVisible(GameState.PAUSED))
        assertFalse(TutorialFlow.isOverlayVisible(GameState.VICTORY))
        assertFalse(TutorialFlow.isOverlayVisible(GameState.DEFEAT))
        assertFalse(TutorialFlow.isOverlayVisible(GameState.LEVEL_SELECT))
        assertFalse(TutorialFlow.isOverlayVisible(GameState.MAIN_MENU))
    }

    // =======================================================================
    // 2) MUTLU YOL — 5 adim, sirasiyla
    // =======================================================================

    @Test
    fun happyPathWalksAllFiveStepsAndCompletes() {
        var state = TutorialFlow.start(supply = 80)
        assertEquals(TutorialStep.SELECT_PAD, state.step)

        // 1 -> 2: pad secildi.
        state = TutorialFlow.update(state, signals(pad = true), 0.016f)
        assertEquals(TutorialStep.BUILD_TOWER, state.step)

        // 2 -> 3: kule kuruldu (motor pad secimini kendisi temizler).
        state = TutorialFlow.update(state, signals(towers = 1, supply = 20), 0.016f)
        assertEquals(TutorialStep.SHOW_RANGE, state.step)

        // 3 -> 4: menzil adimi GIRDI ISTEMEZ, sure ile biter.
        state = TutorialFlow.update(
            state,
            signals(towers = 1, supply = 20),
            TutorialFlow.RANGE_STEP_SECONDS
        )
        assertEquals(TutorialStep.START_WAVE, state.step)

        // 4 -> 5: dalga basladi.
        state = TutorialFlow.update(
            state,
            signals(state = GameState.WAVE_RUNNING, towers = 1, supply = 20),
            0.016f
        )
        assertEquals(TutorialStep.SUPPLY_GROWS, state.step)
        assertEquals("Tedarik esigi adima GIRERKEN alinir", 20, state.supplyAtStepEntry)

        // 5 -> bitti: bir dusman oldu, Tedarik yukseldi.
        state = TutorialFlow.update(
            state,
            signals(state = GameState.WAVE_RUNNING, towers = 1, supply = 26),
            0.016f
        )
        assertEquals(TutorialOutcome.COMPLETED, state.outcome)
        assertFalse(state.running)
    }

    @Test
    fun stepCountStaysWithinTheFiveStepBudget() {
        // FUN_AUDIT sarti: 4-5 adimi GECMEZ. Adim eklemek isteyen once
        // birini cikarmali.
        assertTrue(
            "ogretici en fazla 5 adim olabilir, su an ${TutorialFlow.STEPS.size}",
            TutorialFlow.STEPS.size <= 5
        )
    }

    // =======================================================================
    // 3) OYUNCU KILITLENMEZ — sirasiz oynanis ve atlama
    // =======================================================================

    @Test
    fun startingTheWaveEarlyCollapsesTheSkippedSteps() {
        // Oyuncu hicbir sey kurmadan dalgayi baslatti. Ogretici onu geri
        // cagirmaz, oyuncunun GERCEKTEN oldugu yere hizalanir.
        var state = TutorialFlow.start(supply = 80)
        state = TutorialFlow.update(
            state,
            signals(state = GameState.WAVE_RUNNING, supply = 80),
            0.016f
        )
        assertEquals(TutorialStep.SUPPLY_GROWS, state.step)
        assertTrue("akis capa atmaz, calismaya devam eder", state.running)
    }

    @Test
    fun anyPadSatisfiesTheFirstStepNotJustTheHighlightedOne() {
        // Vurgu YONLENDIRIR, ZORLAMAZ: baska bir pad secmek de adimi bitirir.
        val state = TutorialFlow.update(TutorialFlow.start(80), signals(pad = true), 0.016f)
        assertEquals(TutorialStep.BUILD_TOWER, state.step)
    }

    @Test
    fun waveStartedByTheCountdownCountsTheSameAsTheButton() {
        // 10 sn hazirlik sayaci dolunca dalga kendi baslar. START_WAVE adimi
        // "butona basildi mi" diye SORMAZ, sonuca bakar.
        var state = TutorialFlow.start(80).copy(step = TutorialStep.START_WAVE)
        state = TutorialFlow.update(
            state,
            signals(state = GameState.WAVE_RUNNING, towers = 1, supply = 20),
            0.016f
        )
        assertEquals(TutorialStep.SUPPLY_GROWS, state.step)
    }

    @Test
    fun skipClosesTheTutorialPermanentlyFromAnyStep() {
        for (step in TutorialFlow.STEPS) {
            val skipped = TutorialFlow.skip(TutorialFlow.start(80).copy(step = step))
            assertEquals("$step adiminda GEC", TutorialOutcome.SKIPPED, skipped.outcome)
            assertFalse(skipped.running)
        }
    }

    @Test
    fun terminalStateIsNeverReanimatedByFurtherFrames() {
        val skipped = TutorialFlow.skip(TutorialFlow.start(80))
        assertEquals(
            "biten ogretici sonraki karelerde geri gelmez",
            skipped,
            TutorialFlow.update(skipped, signals(state = GameState.WAVE_RUNNING), 1f)
        )
        // `skip` de kendini tekrarlamaz.
        assertEquals(skipped, TutorialFlow.skip(skipped))
    }

    // =======================================================================
    // 4) ZAMAN — donma, zaman asimi, bozuk delta
    // =======================================================================

    @Test
    fun pauseFreezesTheTimerSoTheRangeStepIsNeverMissed() {
        // Duraklatma menusu ogreticinin ONUNDE. Sayac donmasaydi menu
        // kapaninca menzil adimi coktan gecmis olurdu.
        val state = TutorialFlow.start(80).copy(step = TutorialStep.SHOW_RANGE)
        val paused = TutorialFlow.update(
            state,
            signals(state = GameState.PAUSED, towers = 1),
            10f
        )
        assertEquals(state, paused)
    }

    @Test
    fun rangeStepAdvancesOnItsOwnButNotBeforeItsTime() {
        val state = TutorialFlow.start(80).copy(step = TutorialStep.SHOW_RANGE)
        val early = TutorialFlow.update(
            state,
            signals(towers = 1),
            TutorialFlow.RANGE_STEP_SECONDS - 0.5f
        )
        assertEquals("suresi dolmadan ilerlemez", TutorialStep.SHOW_RANGE, early.step)

        val done = TutorialFlow.update(early, signals(towers = 1), 0.6f)
        assertEquals(TutorialStep.START_WAVE, done.step)
    }

    @Test
    fun supplyStepGivesUpInsteadOfHangingOnScreenForever() {
        // Hicbir dusman olmezse (sizinti) ogretici sonsuza kadar serit
        // gostermez; zaman asimiyla kapanir.
        var state = TutorialFlow.start(80).copy(
            step = TutorialStep.SUPPLY_GROWS,
            supplyAtStepEntry = 20
        )
        state = TutorialFlow.update(
            state,
            signals(state = GameState.WAVE_RUNNING, towers = 1, supply = 20),
            TutorialFlow.SUPPLY_STEP_TIMEOUT_SECONDS
        )
        assertEquals(TutorialOutcome.COMPLETED, state.outcome)
    }

    @Test
    fun negativeOrHugeDeltaCannotCorruptTheTimer() {
        val state = TutorialFlow.start(80).copy(step = TutorialStep.SHOW_RANGE)
        val backwards = TutorialFlow.update(state, signals(towers = 1), -5f)
        assertEquals("negatif kare suresi yok sayilir", 0f, backwards.elapsedSeconds, 0.0001f)
        assertEquals(TutorialStep.SHOW_RANGE, backwards.step)
    }

    // =======================================================================
    // 5) SAVASIN SONU — ne zaman KALICI yazilir, ne zaman yazilmaz
    // =======================================================================

    @Test
    fun victoryCountsAsCompletedFromAnyStep() {
        for (step in TutorialFlow.STEPS) {
            val state = TutorialFlow.update(
                TutorialFlow.start(80).copy(step = step),
                signals(state = GameState.VICTORY, towers = 1),
                0.016f
            )
            assertEquals(
                "$step adiminda zafer -> ogrenildi sayilir",
                TutorialOutcome.COMPLETED,
                state.outcome
            )
        }
    }

    @Test
    fun abandonedBattleIsNotPersistedSoTheTutorialReturnsOnRetry() {
        // ABORTED tek basina bir sinyal: `TutorialOverlay` bunu KALICI
        // YAZMAZ, yani "tekrar dene" diyen oyuncu ogreticiyi yine gorur.
        for (state in listOf(GameState.DEFEAT, GameState.LEVEL_SELECT, GameState.MAIN_MENU)) {
            val result = TutorialFlow.update(
                TutorialFlow.start(80).copy(step = TutorialStep.BUILD_TOWER),
                signals(state = state),
                0.016f
            )
            assertEquals("$state -> kalici yazilmaz", TutorialOutcome.ABORTED, result.outcome)
        }
    }

    // =======================================================================
    // 6) YERLESIM GEOMETRISI — vurgu ekranda ve dogru bolgede kalir
    // =======================================================================

    /** Temsili yatay ekranlar: kucuk telefon, Galaxy S8 sinifi, tablet. */
    private val screens = listOf(
        Triple(1280f, 720f, 2.0f),
        Triple(2220f, 1080f, 3.0f),
        Triple(1920f, 1200f, 2.0f),
        Triple(960f, 540f, 1.5f)
    )

    @Test
    fun buildCardArrowLandsOverTheFirstCard() {
        for ((w, _, d) in screens) {
            val x = TutorialFlow.firstBuildCardCenterX(w, d)
            assertTrue("ok ekran icinde kalmali (${w}x$d)", x > 0f && x < w)
            // Ilk kart 4 kartin en solundakidir: merkezi ekranin sol
            // ceyreginin icinde olmali.
            assertTrue("ilk kartin merkezi sol ceyrekte olmali (${w}x$d)", x < w * 0.25f)
        }
    }

    @Test
    fun buildBarArrowSitsAboveTheDrawerNotUnderIt() {
        for ((_, h, d) in screens) {
            val y = TutorialFlow.buildBarTopY(h, d)
            assertTrue("cekmecenin ust kenari ekran icinde", y in 0f..h)
            assertTrue("ok cekmecenin ALTINDA kalamaz", y < h)
        }
    }

    @Test
    fun hudArrowsPointAtTheCorrectHalfOfTheHeader() {
        for ((w, _, d) in screens) {
            val startX = TutorialFlow.startWaveAnchorX(w, d)
            assertTrue("BASLAT oku sag yarida (${w}x$d)", startX >= w * 0.5f)
            assertTrue("BASLAT oku ekran disina tasamaz (${w}x$d)", startX <= w)

            val supplyX = TutorialFlow.supplyAnchorX(w, d)
            assertTrue("Tedarik oku sol yarida (${w}x$d)", supplyX <= w * 0.5f)
            assertTrue("Tedarik oku ekran disina tasamaz (${w}x$d)", supplyX >= 0f)
        }
    }

    @Test
    fun degenerateSizesDoNotThrow() {
        // Ilk karede tuval olculeri 0 olabilir; geometri istisna ATMAMALI.
        assertEquals(0f, TutorialFlow.firstBuildCardCenterX(0f, 0f), 0f)
        assertEquals(0f, TutorialFlow.startWaveAnchorX(0f, 2f), 0f)
        assertEquals(0f, TutorialFlow.supplyAnchorX(0f, 2f), 0f)
        assertEquals(0f, TutorialFlow.buildBarTopY(0f, 2f), 0f)
    }

    // =======================================================================
    // 7) BOLUM 1'IN GERCEGI — ogreticinin dayandigi varsayimlar
    //
    // Bu blok kampanya/ekonomi ajanlarina karsi bir SIGORTA. Sayilar
    // degistiginde ogretici sessizce yalan soylemeye baslamasin diye
    // varsayimlar burada kilitleniyor.
    // =======================================================================

    @Test
    fun highlightedPadIsActuallyVisibleOnLevelOne() {
        val spec = GameConfig.levelSpec(TutorialFlow.TUTORIAL_LEVEL_ID)
        assertFalse(
            "ogretici GIZLI bir pad'i isaret edemez " +
                "(pad ${TutorialFlow.HIGHLIGHT_PAD_ID}, gizli: ${spec.disabledPadIds})",
            TutorialFlow.HIGHLIGHT_PAD_ID in spec.disabledPadIds
        )

        val pad = LevelData.forMapId(spec.mapId).buildSpots
            .firstOrNull { it.id == TutorialFlow.HIGHLIGHT_PAD_ID }
        assertNotNull("isaretlenen pad haritada var olmali", pad)
    }

    @Test
    fun highlightedPadCanActuallyShootTheRoute() {
        // Ogreticinin en agir sozu bu: "buraya kur". Kurulan kule hicbir
        // seye ates edemiyorsa ogretici oyuncuya TUZAK kurmus olur.
        val spec = GameConfig.levelSpec(TutorialFlow.TUTORIAL_LEVEL_ID)
        val pad = LevelData.forMapId(spec.mapId).buildSpots
            .first { it.id == TutorialFlow.HIGHLIGHT_PAD_ID }

        val distance = GeometryTestSupport.padToActiveRoute(
            padNormX = pad.normX,
            padNormY = pad.normY,
            mapId = spec.mapId,
            levelId = spec.levelId
        )
        val gatlingRange = GameConfig.TOWER_SPECS
            .getValue(GameConfig.TowerType.MACHINE_GUN)
            .level1Range

        assertTrue(
            "isaretlenen pad aktif rotaya $distance ref-px uzakta, " +
                "Gatling menzili $gatlingRange",
            distance <= gatlingRange
        )
    }

    @Test
    fun levelOneAffordsExactlyTwoTowers() {
        // ⚠ 2026-08-19: L1 sermayesi 80 -> 120, yani IKI Gatling.
        //
        // Eski yorum "oyuncunun tek kule hakki var, yanlis yer bolumu bitirir"
        // diyor ve oranin degismesi halinde ogreticinin sertliginin gozden
        // gecirilmesini istiyordu. Gecirildi: ogretici SERTLESTIRILMEDI cunku
        // zaten sert degil — `BUILD_TOWER` adimi `towerCount > 0` ile duser,
        // yani isaretli pad bir ONERI, kural degil. Ikinci kule bu yuzden
        // ogreticinin hicbir adimini kirmiyor; tersine, "iki silahini nerede
        // kesistireceksin" sorusu oyunun gercek dersine daha yakin.
        //
        // UST SINIR KORUNUYOR: ucuncu kuleye yetmemeli, yoksa acilis bolumu
        // yerlestirme karari olmayan bir ekrana doner.
        val spec = GameConfig.levelSpec(TutorialFlow.TUTORIAL_LEVEL_ID)
        val cost = GameConfig.TOWER_SPECS
            .getValue(GameConfig.TowerType.MACHINE_GUN)
            .buildCost
        val affordable = spec.startingSupply / cost
        assertEquals(
            "bolum 1 baslangic Tedariki ${spec.startingSupply}, Gatling $cost -> " +
                "tam olarak 2 kule bekleniyor",
            2,
            affordable
        )
    }

    @Test
    fun gatlingIsTheOnlyTowerTheTutorialCanNameOnLevelOne() {
        // Ogretici acikca "Gatling kur" diyor. Bolum 1'de baska bir kule
        // acilirsa bu satir eksik kalir ve akis gozden gecirilmeli.
        val unlocked = GameConfig.unlockedTowers(TutorialFlow.TUTORIAL_LEVEL_ID)
        assertEquals(
            "bolum 1'de yalnizca Gatling acik olmali, bulunan: $unlocked",
            listOf(GameConfig.TowerType.MACHINE_GUN),
            unlocked
        )
    }
}
