package com.miniappfactory.frontlinedefender.tutorial

import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.game.ui.HintCopy
import com.miniappfactory.frontlinedefender.game.ui.HintFlow
import com.miniappfactory.frontlinedefender.game.ui.HintSignals
import com.miniappfactory.frontlinedefender.game.ui.HintState
import com.miniappfactory.frontlinedefender.game.ui.UnlockHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KILIT ACILMA IPUCLARI — DURUM MAKINESI.
 *
 * **SAF JUnit**: [HintFlow] Compose ve Android'e bagimli degil, dolayisiyla
 * Robolectric YOK. `TutorialFlowTest` ile ayni desen.
 *
 * Kilitlenen sozlesme dort maddedir:
 *  1. Ipucu bolum NUMARASINA degil, **kilit acilma olayina** baglidir. Kampanya
 *     tablosu yeniden sekillenirse ipucu kendiliginden dogru bolume tasinir.
 *  2. Her ipucu **bir kez** gosterilir ve kendi kalici bayragini tasir.
 *  3. Ayni anda **tek** mesaj: bir hazirlik fazinda en fazla bir ipucu, oncelik
 *     sirasi "kritik uyari > ilerleme".
 *  4. Ipucu oyuncuyu **hicbir sekilde engellemez**: dalga baslayinca duser,
 *     duraklatmada donar, okunmadan kaybolursa kalici yazilmaz.
 */
class UnlockHintFlowTest {

    private companion object {
        /** Bir kare (60 FPS). Testler gercek kare suresiyle ilerler. */
        const val FRAME = 1f / 60f
    }

    // =======================================================================
    // Yardimcilar
    // =======================================================================

    private fun signals(
        levelId: Int,
        gameState: GameState = GameState.PREPARATION,
        waveIndex: Int = 0,
        tutorialArmed: Boolean = false,
        // VARSAYILAN, KAMPANYANIN GERCEK TABLOSU — bos kume DEGIL.
        //
        // Eskiden `emptySet()` idi ve bu, gercek bir bolumde asla olusmayan
        // bir durumdu: her bolumde en az bir dusman tipi vardir. Fikstur
        // gercek disi oldugu icin "ipucu, o bolumde CIKAN bir dusmani
        // anlatmali" kurali test edilemez haldeydi ve kural kodda da yoktu
        // (bkz. HintTeachesPresentEnemyTest).
        levelEnemies: Set<GameConfig.EnemyType> =
            WaveDefinitions.wavesFor(levelId)
                .flatMap { wave -> wave.spawns.map { it.enemyType } }
                .toSet(),
        incomingArmored: Set<GameConfig.EnemyType> = emptySet()
    ) = HintSignals(
        gameState = gameState,
        levelId = levelId,
        waveIndex = waveIndex,
        tutorialArmed = tutorialArmed,
        levelEnemyTypes = levelEnemies,
        incomingArmoredTypes = incomingArmored
    )

    /** [seconds] kadar kare kare ilerlet. Motorun yaptigi isin aynisi. */
    private fun advance(state: HintState, signals: HintSignals, seconds: Float): HintState {
        var current = state
        var elapsed = 0f
        while (elapsed < seconds) {
            current = HintFlow.update(current, signals, FRAME)
            elapsed += FRAME
        }
        return current
    }

    private fun unlockLevel(tower: GameConfig.TowerType): Int =
        GameConfig.TOWER_SPECS.getValue(tower).unlockedAtLevel

    /** Bir kule ipucunun tetiklendigi bolum. */
    private fun unlockLevel(hint: UnlockHint): Int =
        unlockLevel(requireNotNull(hint.unlockTower) { "$hint bir kule kilidine bagli degil" })

    /**
     * Bir kule ipucunun GERCEKTEN gosterilebildigi ilk bolum.
     *
     * Kilidin acildigi bolum ILE AYNI OLMAK ZORUNDA DEGIL: kule L3te acilabilir
     * ama dersin ornek dusmani L5e kadar sahaya cikmayabilir. Ipucu o zamana
     * kadar ERTELENIR (bkz. HintTeachesPresentEnemyTest); goruldu yazilmadigi
     * icin ders kaybolmaz.
     *
     * Zamanlama ve durum-makinesi testleri bu bolumu kullanir: onlarin olctugu
     * sey ipucunun ICERIGI degil, gorunme suresi ve duraklatma davranisidir.
     * Ipucunun hic cikmadigi bir bolumde kurulurlarsa olctukleri seyi
     * olcemezler.
     */
    private fun firstShowableLevel(hint: UnlockHint): Int =
        (1..GameConfig.CAMPAIGN_LEVEL_COUNT).first { level ->
            HintFlow.isTriggered(hint, signals(levelId = level))
        }

    private val towerHints = UnlockHint.values().filter { it.unlockTower != null }

    // =======================================================================
    // 1) Tetikleyici BOLUM NUMARASI degil, KILIT OLAYI
    // =======================================================================

    /**
     * Gorevin en kritik kurali. Kampanya 55 bolum ve L1-L22 su anda yeniden
     * sekilleniyor; bu test kilit tablosundan NE OKUNURSA ona gore kosar, yani
     * `unlockedAtLevel` degerleri degistiginde bile gecerli kalir.
     */
    @Test
    fun towerHintFiresExactlyWhenTheTowerUnlocks() {
        for (hint in towerHints) {
            val level = unlockLevel(hint)

            // ⚠ SOZLESME 2026-08-19'DA DARALTILDI. Eskiden burada "ipucu
            // kilidin acildigi bolumde TETIKLENMELI" yaziyordu ve bu, ipucunun
            // oyuncunun o bolumde hic gormedigi bir dusmani anlatmasina izin
            // veriyordu (L3'te Top acilir, ders Kalkanli Er'i anlatirdi, o
            // dusman ilk kez L9'da cikar). Yeni kural: kilit acilmasi GEREK
            // ama YETER degil — dersin ornegi de sahada olmali.
            val showable = firstShowableLevel(hint)
            assertTrue(
                "$hint kilit acilmadan ONCE gosterilemez: gosterilebildigi ilk " +
                    "bolum $showable, kilit bolumu $level",
                showable >= level
            )
            if (level > 1) {
                assertFalse(
                    "$hint kilit acilmadan ONCE (bolum ${level - 1}) tetiklenmemeli",
                    HintFlow.isTriggered(hint, signals(levelId = level - 1))
                )
            }
            assertTrue(
                "$hint gosterilebildigi bolumden SONRA da gecerli kalmali",
                HintFlow.isTriggered(hint, signals(levelId = showable))
            )
        }
    }

    /** Kilit tablosu ile ipucu tablosu ayni kuleleri konusmali. */
    @Test
    fun everyLockedTowerHasItsOwnHint() {
        val lockedTowers = GameConfig.TowerType.values()
            .filter { GameConfig.TOWER_SPECS.getValue(it).unlockedAtLevel > 1 }
        val hintedTowers = towerHints.mapNotNull { it.unlockTower }

        assertEquals(
            "kilitli her kulenin bir rol ipucu olmali (kilitli: $lockedTowers)",
            lockedTowers.toSet(),
            hintedTowers.toSet()
        )
    }

    // =======================================================================
    // 2) Zirh uyarisi SIRADAKI DALGAYA bagli
    // =======================================================================

    @Test
    fun armourWarningNeedsAnArmouredEnemyInTheIncomingWave() {
        assertFalse(
            "zirhsiz dalgada zirh uyarisi cikmamali",
            HintFlow.isTriggered(UnlockHint.ARMOR_INTRO, signals(levelId = 4))
        )
        assertTrue(
            "siradaki dalgada zirhli varsa uyari cikmali",
            HintFlow.isTriggered(
                UnlockHint.ARMOR_INTRO,
                signals(
                    levelId = 4,
                    incomingArmored = setOf(GameConfig.EnemyType.SHIELDED_TROOPER)
                )
            )
        )
    }

    // =======================================================================
    // 3) Bir kez goster
    // =======================================================================

    @Test
    fun aHintIsRetiredAfterItsVisibleWindow() {
        val level = firstShowableLevel(UnlockHint.CANNON_ROLE)
        val at = signals(levelId = level)

        var state = HintFlow.update(HintFlow.start(emptySet()), at, FRAME)
        assertEquals(UnlockHint.CANNON_ROLE, state.active)

        state = advance(state, at, HintFlow.VISIBLE_SECONDS + 0.2f)
        assertNull("gorunme suresi dolunca serit kendiliginden kaybolmali", state.active)
        assertTrue(
            "yeterince uzun gorunen ipucu KALICI olarak gorulmus sayilmali",
            UnlockHint.CANNON_ROLE in state.seen
        )
    }

    @Test
    fun aSeenHintNeverComesBack() {
        val level = firstShowableLevel(UnlockHint.CANNON_ROLE)
        val seen = HintFlow.PRIORITY.toSet() - UnlockHint.CANNON_ROLE
        val state = HintState(seen = seen + UnlockHint.CANNON_ROLE)

        // Farkli dalga: "faz basina tek ipucu" kilidi bu testi maskelemesin.
        assertNull(
            "gorulmus ipucu sonraki dalgalarda da cikmamali",
            HintFlow.nextHint(state, signals(levelId = level, waveIndex = 7))
        )
        assertTrue("hepsi gorulduyse katman kendini kapatmali", HintFlow.isExhausted(state))
    }

    // =======================================================================
    // 4) Ayni anda TEK mesaj + oncelik
    // =======================================================================

    /**
     * Geri bildirim hiyerarsisi: kritik uyari > ilerleme. Oyuncunun kulesi az
     * sonra ise yaramayacaksa once bunu bilmeli; yeni kule mujdesi bekleyebilir.
     */
    @Test
    fun theArmourWarningOutranksEveryUnlockHint() {
        val level = firstShowableLevel(UnlockHint.MISSILE_ROLE)
        val everythingEligible = signals(
            levelId = level,
            incomingArmored = setOf(GameConfig.EnemyType.TANK)
        )

        assertEquals(
            "zirh uyarisi kule ipuclarinin ONUNDE olmali",
            UnlockHint.ARMOR_INTRO,
            HintFlow.nextHint(HintFlow.start(emptySet()), everythingEligible)
        )
    }

    /** Denetimin en buyuk sayisal farki once ogretilir. */
    @Test
    fun theMissileLessonOutranksCannonAndFrost() {
        val level = firstShowableLevel(UnlockHint.MISSILE_ROLE)
        val state = HintFlow.start(setOf(UnlockHint.ARMOR_INTRO))

        assertEquals(
            UnlockHint.MISSILE_ROLE,
            HintFlow.nextHint(state, signals(levelId = level))
        )
        assertTrue(
            "oncelik listesinde Fuze, Top'tan once gelmeli",
            HintFlow.PRIORITY.indexOf(UnlockHint.MISSILE_ROLE) <
                HintFlow.PRIORITY.indexOf(UnlockHint.CANNON_ROLE)
        )
    }

    @Test
    fun onlyOneHintPerPreparationPhase() {
        val level = firstShowableLevel(UnlockHint.MISSILE_ROLE)
        val wave = signals(levelId = level, waveIndex = 3)

        // Ilk ipucu cikar, suresi dolar, kalici yazilir.
        var state = advance(
            HintFlow.update(HintFlow.start(emptySet()), wave, FRAME),
            wave,
            HintFlow.VISIBLE_SECONDS + 0.2f
        )
        val first = state.seen.single()

        // AYNI dalgada ikinci ders VERILMEZ: iki serit arka arkaya okunmaz.
        state = advance(state, wave, 5f)
        assertNull("ayni hazirlik fazinda ikinci ipucu cikmamali", state.active)

        // SONRAKI hazirlik fazinda sira digerine gelir.
        val nextWave = wave.copy(waveIndex = 4)
        state = HintFlow.update(state, nextWave, FRAME)
        assertNotNull("sonraki hazirlik fazinda ikinci ipucu cikmali", state.active)
        assertTrue("ayni ipucu tekrar edilmemeli", state.active != first)
    }

    // =======================================================================
    // 5) Oyuncuyu ENGELLEMEZ
    // =======================================================================

    @Test
    fun theTutorialSuppressesEveryHint() {
        val level = firstShowableLevel(UnlockHint.CANNON_ROLE)
        for (hint in UnlockHint.values()) {
            assertNull(
                "ilk oturum ogreticisi kosarken hicbir ipucu cikmamali ($hint)",
                HintFlow.nextHint(
                    HintFlow.start(emptySet()),
                    signals(
                        levelId = level,
                        tutorialArmed = true,
                        incomingArmored = setOf(GameConfig.EnemyType.TANK)
                    )
                )
            )
        }
    }

    @Test
    fun theStripOnlyExistsDuringPreparation() {
        for (state in GameState.values()) {
            assertEquals(
                "$state: serit yalnizca hazirlik fazinda cizilebilir",
                state == GameState.PREPARATION,
                HintFlow.isOverlayVisible(state)
            )
        }
    }

    @Test
    fun startingTheWaveHidesTheStripImmediately() {
        val level = firstShowableLevel(UnlockHint.CANNON_ROLE)
        val prep = signals(levelId = level)

        var state = HintFlow.update(HintFlow.start(emptySet()), prep, FRAME)
        state = advance(state, prep, HintFlow.MIN_READ_SECONDS + 0.2f)
        assertNotNull(state.active)

        state = HintFlow.update(state, prep.copy(gameState = GameState.WAVE_RUNNING), FRAME)
        assertNull("dalga baslayinca serit dusmeli", state.active)
    }

    @Test
    fun pauseFreezesTheVisibleWindow() {
        val level = firstShowableLevel(UnlockHint.CANNON_ROLE)
        val prep = signals(levelId = level)

        var state = HintFlow.update(HintFlow.start(emptySet()), prep, FRAME)
        val paused = prep.copy(gameState = GameState.PAUSED)

        // Duraklatma menusunun arkasinda gorunme suresi DOLMAMALI.
        state = advance(state, paused, HintFlow.VISIBLE_SECONDS * 2f)
        assertEquals(
            "duraklatmada ipucu ayakta kalmali",
            UnlockHint.CANNON_ROLE,
            state.active
        )
        assertTrue("duraklatmada sayac donmali", state.shownSeconds <= FRAME * 2f)
    }

    // =======================================================================
    // 6) Kalicilik esigi
    // =======================================================================

    @Test
    fun aHintReadForTooShortIsNotBurned() {
        val level = firstShowableLevel(UnlockHint.CANNON_ROLE)
        val prep = signals(levelId = level)

        var state = HintFlow.update(HintFlow.start(emptySet()), prep, FRAME)
        state = advance(state, prep, HintFlow.MIN_READ_SECONDS - 0.5f)
        // Oyuncu okumaya firsat bulmadan dalgayi baslatti.
        state = HintFlow.update(state, prep.copy(gameState = GameState.WAVE_RUNNING), FRAME)

        assertNull(state.active)
        assertTrue(
            "okunmadan kaybolan ipucu HARCANMIS sayilmamali",
            state.seen.isEmpty()
        )
    }

    @Test
    fun dismissingBurnsTheHintEvenInstantly() {
        val level = firstShowableLevel(UnlockHint.CANNON_ROLE)
        val prep = signals(levelId = level)

        val shown = HintFlow.update(HintFlow.start(emptySet()), prep, FRAME)
        val dismissed = HintFlow.dismiss(shown)

        assertNull(dismissed.active)
        assertTrue(
            "acikca kapatan oyuncu bu ipucunu bir daha gormemeli",
            UnlockHint.CANNON_ROLE in dismissed.seen
        )
    }

    @Test
    fun dismissWithoutAnActiveHintIsANoOp() {
        val state = HintFlow.start(emptySet())
        assertEquals(state, HintFlow.dismiss(state))
    }

    @Test
    fun negativeFrameTimeIsIgnored() {
        val level = firstShowableLevel(UnlockHint.CANNON_ROLE)
        val prep = signals(levelId = level)

        var state = HintFlow.update(HintFlow.start(emptySet()), prep, FRAME)
        val before = state.shownSeconds
        state = HintFlow.update(state, prep, -5f)

        assertTrue("negatif kare suresi sayaci geri sarmamali", state.shownSeconds >= before)
        assertEquals(UnlockHint.CANNON_ROLE, state.active)
    }

    // =======================================================================
    // 7) Metin verisi — sayilar ve ornek dusman secimi
    // =======================================================================

    /**
     * Ipucu, oyuncunun bu bolumde GERCEKTEN gordugu bir dusmandan bahsetmeli.
     * Aksi halde "Komuta Tanki'na 26,6 DPS" gibi, oyuncunun 11. bolume kadar
     * hic gormedigi bir hedefle ders verilirdi.
     */
    @Test
    fun theExampleEnemyIsOneThePlayerActuallyMeets() {
        val level = firstShowableLevel(UnlockHint.MISSILE_ROLE)
        val present = setOf(
            GameConfig.EnemyType.INFANTRY,
            GameConfig.EnemyType.ARMORED_VEHICLE
        )
        val copy = HintFlow.copyFor(
            UnlockHint.MISSILE_ROLE,
            signals(levelId = level, levelEnemies = present)
        ) as HintCopy.TowerMatchup

        assertTrue(
            "ornek dusman bolumde bulunanlardan secilmeli, secilen: ${copy.enemy}",
            copy.enemy in present
        )
    }

    /** Bolumde hicbir aday yoksa ipucu yine de anlamli bir hedefe duser. */
    @Test
    fun theExampleEnemyFallsBackInsteadOfVanishing() {
        val level = firstShowableLevel(UnlockHint.MISSILE_ROLE)
        val copy = HintFlow.copyFor(UnlockHint.MISSILE_ROLE, signals(levelId = level))
        assertNotNull("bolum verisi bos olsa bile rol dersi verilebilmeli", copy)
    }

    /** Zirh uyarisi EN KALIN zirhi ornek alir: fark en okunur oldugu yerde. */
    @Test
    fun theArmourWarningPicksTheThickestArmour() {
        val incoming = setOf(
            GameConfig.EnemyType.SHIELDED_TROOPER,
            GameConfig.EnemyType.TANK
        )
        val copy = HintFlow.copyFor(
            UnlockHint.ARMOR_INTRO,
            signals(levelId = 6, incomingArmored = incoming)
        ) as HintCopy.ArmorContrast

        val thickest = incoming.maxByOrNull {
            GameConfig.ENEMY_SPECS.getValue(it).armor
        }
        assertEquals(thickest, copy.armoredEnemy)
        assertTrue(
            "karsilastirma tarafi ZIRHSIZ olmali",
            GameConfig.ENEMY_SPECS.getValue(copy.softEnemy).armor == 0f
        )
    }

    /** Zirhli yoksa uyari ciziLMEZ; yarim ders vermektense hic verme. */
    @Test
    fun theArmourWarningProducesNoCopyWithoutAnArmouredEnemy() {
        assertNull(HintFlow.copyFor(UnlockHint.ARMOR_INTRO, signals(levelId = 2)))
    }

    /** Her ipucunun kendine ozgu, DEGISMEZ bir kalici anahtari olmali. */
    @Test
    fun everyHintHasAUniqueStableSaveId() {
        val ids = UnlockHint.values().map { it.saveId }
        assertEquals("kalici anahtarlar benzersiz olmali", ids.size, ids.toSet().size)
        assertTrue("kalici anahtar bos olamaz", ids.none { it.isBlank() })
    }

    // =======================================================================
    // 8) Dalga tablosu okumasi — savunmali
    // =======================================================================

    @Test
    fun unknownLevelsProduceNoWaveData() {
        assertTrue(HintFlow.levelEnemyTypes(levelId = -1, totalWaves = 5).isEmpty())
        assertTrue(HintFlow.levelEnemyTypes(levelId = 1, totalWaves = 0).isEmpty())
        assertTrue(HintFlow.armoredTypesInWave(levelId = -1, waveIndex = 0).isEmpty())
        assertTrue(HintFlow.armoredTypesInWave(levelId = 1, waveIndex = 9_999).isEmpty())
    }

    @Test
    fun theFirstLevelYieldsRealWaveData() {
        val types = HintFlow.levelEnemyTypes(levelId = 1, totalWaves = 30)
        assertTrue("bolum 1'in dalgalarinda dusman olmali", types.isNotEmpty())
    }
}
