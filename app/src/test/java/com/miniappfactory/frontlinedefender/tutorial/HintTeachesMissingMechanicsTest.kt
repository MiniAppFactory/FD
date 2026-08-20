package com.miniappfactory.frontlinedefender.tutorial

import com.miniappfactory.frontlinedefender.game.economy.BoosterType
import com.miniappfactory.frontlinedefender.game.economy.boostersAvailableAt
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.game.ui.HintCopy
import com.miniappfactory.frontlinedefender.game.ui.HintFlow
import com.miniappfactory.frontlinedefender.game.ui.HintSignals
import com.miniappfactory.frontlinedefender.game.ui.HintState
import com.miniappfactory.frontlinedefender.game.ui.HintTowerSnapshot
import com.miniappfactory.frontlinedefender.game.ui.UnlockHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ---------------------------------------------------------------------------------
 * DORT OGRETILMEYEN MEKANIK — docs/FUN_AUDIT_2.md (a)
 * ---------------------------------------------------------------------------------
 * Denetim: **satma, hedefleme modu, guclendiriciler ve yukseltme** oyunun hicbir
 * yerinde ogretilmiyordu. `CLEAR_WITHOUT_SELLING` diye bir GOREV vardi ama satis
 * mekanigini anlatan hicbir sey yoktu; `WaveDefinitions.kt` icinde "L6'da hedefleme
 * modlari tanitilir" yazan bir yorum vardi ama tanitan bir UI yoktu — olu yorum.
 *
 * Bu test dordunun de dogru OLAYDA tetiklendigini ve dosyanin var olan
 * sozlesmelerini bozmadigini kilitler. Kilitlenen dort madde:
 *
 *  1. **Tetikleyici bolum numarasi DEGIL.** Hicbir dal `levelId == n` sormaz;
 *     kampanya yeniden sekillenirse ders kendiliginden dogru yere tasinir.
 *  2. **Ders, oyuncunun O ANDA erisebildigi seyi anlatir.** Yukseltme odenebilir
 *     olmadan, guclendirici ray'de belirmeden, satis insa tikanmadan cikmaz.
 *  3. **Ayni anda TEK mesaj.** Dordu birden uygun olsa bile bir hazirlik fazinda
 *     yalnizca biri cizilir.
 *  4. **Ders kaybolmaz.** Kosul saglanmadiginda ipucu "gorulduye" yazilmaz.
 */
class HintTeachesMissingMechanicsTest {

    private companion object {
        /** Bir kare (60 FPS). */
        const val FRAME = 1f / 60f

        /** Yukseltme dersinin konusabilecegi bir kule: kademe 1, ucuz adim. */
        val GATLING = GameConfig.TowerType.MACHINE_GUN
    }

    // =======================================================================
    // Fikstur
    // =======================================================================

    private fun snapshot(
        id: String,
        type: GameConfig.TowerType = GATLING,
        tier: Int = 1,
        upgradeCost: Int? = 40,
        sellValue: Int = 42,
        salvagePercent: Int = 70
    ) = HintTowerSnapshot(
        towerId = id,
        type = type,
        tier = tier,
        upgradeCost = upgradeCost,
        sellValue = sellValue,
        salvagePercent = salvagePercent
    )

    private fun signals(
        levelId: Int = 4,
        gameState: GameState = GameState.PREPARATION,
        waveIndex: Int = 0,
        tutorialArmed: Boolean = false,
        // Kampanyanin GERCEK tablosu — elle yazilmis liste degil.
        levelEnemies: Set<GameConfig.EnemyType> = enemiesOf(levelId),
        incomingArmored: Set<GameConfig.EnemyType> = emptySet(),
        incomingTypes: Set<GameConfig.EnemyType> = emptySet(),
        fieldTowers: List<HintTowerSnapshot> = emptyList(),
        supply: Int = 0,
        buildBlocked: Boolean = false
    ) = HintSignals(
        gameState = gameState,
        levelId = levelId,
        waveIndex = waveIndex,
        tutorialArmed = tutorialArmed,
        levelEnemyTypes = levelEnemies,
        incomingArmoredTypes = incomingArmored,
        incomingEnemyTypes = incomingTypes,
        fieldTowers = fieldTowers,
        supply = supply,
        buildBlocked = buildBlocked
    )

    /**
     * Kampanya DISI bolum numarasi bos kume verir.
     *
     * `wavesFor` tanimsiz bolumde bilincli olarak PATLAR (sessiz bos dalga
     * tablosu, bir bolumun kayboldugunu gizlerdi). Ama bu testin kendisi
     * sinir degerleri — -1, 0, 999 — kasitli olarak deniyor; motorun dogru
     * davranisini test altyapisinda carpma haline getirmemek gerekiyor.
     */
    private fun enemiesOf(levelId: Int): Set<GameConfig.EnemyType> {
        if (levelId !in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) return emptySet()
        return WaveDefinitions.wavesFor(levelId)
            .flatMap { wave -> wave.spawns.map { it.enemyType } }
            .toSet()
    }

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

    /** Dordunun de tetikleyicisi. Testlerin cogu bunlarin uzerinden kosar. */
    private val newHints = listOf(
        UnlockHint.UPGRADE_INTRO,
        UnlockHint.TARGETING_INTRO,
        UnlockHint.BOOSTER_INTRO,
        UnlockHint.SELL_INTRO
    )

    // =======================================================================
    // 1) YUKSELTME — tetikleyici "odenebilir bir yukseltme var"
    // =======================================================================

    @Test
    fun theUpgradeLessonWaitsUntilThePlayerOwnsAnUpgradableTower() {
        assertFalse(
            "sahada kule yokken yukseltme dersi cikmamali",
            HintFlow.isTriggered(UnlockHint.UPGRADE_INTRO, signals(supply = 9_999))
        )
    }

    @Test
    fun theUpgradeLessonWaitsUntilTheUpgradeIsAffordable() {
        val field = listOf(snapshot("t1", upgradeCost = 40))

        assertFalse(
            "Tedarik yetmiyorken yukseltme dersi cikmamali",
            HintFlow.isTriggered(
                UnlockHint.UPGRADE_INTRO,
                signals(fieldTowers = field, supply = 39)
            )
        )
        assertTrue(
            "Tedarik tam yettiginde ders cikmali",
            HintFlow.isTriggered(
                UnlockHint.UPGRADE_INTRO,
                signals(fieldTowers = field, supply = 40)
            )
        )
    }

    @Test
    fun aMaxedTowerNeverTriggersTheUpgradeLesson() {
        assertFalse(
            "son kademedeki kule yukseltme dersi acmamali",
            HintFlow.isTriggered(
                UnlockHint.UPGRADE_INTRO,
                signals(fieldTowers = listOf(snapshot("t1", upgradeCost = null)), supply = 9_999)
            )
        )
    }

    /** Ders bir EYLEM oneriyor: en ucuz adim secilir ki kasayi bosaltmasin. */
    @Test
    fun theUpgradeLessonPicksTheCheapestAffordableStep() {
        val copy = HintFlow.copyFor(
            UnlockHint.UPGRADE_INTRO,
            signals(
                fieldTowers = listOf(
                    snapshot("expensive", upgradeCost = 180),
                    snapshot("cheap", upgradeCost = 40)
                ),
                supply = 500
            )
        ) as HintCopy.UpgradeStep

        assertEquals("cheap", copy.towerId)
        assertEquals(40, copy.cost)
    }

    /** Yukseltme BUYUME gostermeli, yoksa ders bir sey ogretmez. */
    @Test
    fun theUpgradeLessonShowsAnIncrease() {
        val copy = HintFlow.copyFor(
            UnlockHint.UPGRADE_INTRO,
            signals(fieldTowers = listOf(snapshot("t1")), supply = 500)
        ) as HintCopy.UpgradeStep

        assertTrue(
            "yukseltilmis DPS su ankinden buyuk olmali (${copy.currentDps} -> ${copy.nextDps})",
            copy.nextDps > copy.currentDps
        )
    }

    /**
     * Yukseltme dersi HICBIR dusman adi gecirmez.
     *
     * Bu bir tercih degil, bir GUVENLIK: dun duzeltilen hata (ipucu, oyuncunun
     * o bolumde gormedigi bir dusmani anlatiyordu) ekonomi tarafinda geri
     * gelemez cunku ders zaten hedefsizdir ([HintFacts.rawDps]).
     */
    @Test
    fun theUpgradeLessonNamesNoEnemy() {
        val copy = HintFlow.copyFor(
            UnlockHint.UPGRADE_INTRO,
            signals(fieldTowers = listOf(snapshot("t1")), supply = 500)
        )
        assertTrue("yukseltme dersi bir dusman adi tasimamali", copy is HintCopy.UpgradeStep)
    }

    // =======================================================================
    // 2) HEDEFLEME MODU — tetikleyici "kule var + dalga KARISIK"
    // =======================================================================

    @Test
    fun theTargetingLessonNeedsATowerOnTheField() {
        assertFalse(
            "sahada kule yokken hedefleme dersi cikmamali",
            HintFlow.isTriggered(
                UnlockHint.TARGETING_INTRO,
                signals(
                    incomingTypes = setOf(
                        GameConfig.EnemyType.INFANTRY,
                        GameConfig.EnemyType.TANK
                    )
                )
            )
        )
    }

    /**
     * Tek tipli dalgada "kule once kime atsin" diye bir SORU yoktur; ders o
     * zaman cevabi olmayan bir soruyu sorar.
     */
    @Test
    fun theTargetingLessonNeedsAMixedWave() {
        val field = listOf(snapshot("t1"))
        assertFalse(
            "tek tipli dalgada hedefleme dersi cikmamali",
            HintFlow.isTriggered(
                UnlockHint.TARGETING_INTRO,
                signals(
                    fieldTowers = field,
                    incomingTypes = setOf(GameConfig.EnemyType.INFANTRY)
                )
            )
        )
        assertTrue(
            "karisik dalgada hedefleme dersi cikmali",
            HintFlow.isTriggered(
                UnlockHint.TARGETING_INTRO,
                signals(
                    fieldTowers = field,
                    incomingTypes = setOf(
                        GameConfig.EnemyType.INFANTRY,
                        GameConfig.EnemyType.TANK
                    )
                )
            )
        )
    }

    /** Ornekler SIRADAKI dalgadan; ikisi de gercekten geliyor. */
    @Test
    fun theTargetingLessonNamesOnlyEnemiesFromTheIncomingWave() {
        val incoming = setOf(
            GameConfig.EnemyType.INFANTRY,
            GameConfig.EnemyType.FAST_SOLDIER,
            GameConfig.EnemyType.TANK
        )
        val copy = HintFlow.copyFor(
            UnlockHint.TARGETING_INTRO,
            signals(fieldTowers = listOf(snapshot("t1")), incomingTypes = incoming)
        ) as HintCopy.TargetingChoice

        assertTrue(copy.strongEnemy in incoming)
        assertTrue(copy.weakEnemy in incoming)
        assertTrue(
            "guclu ornek gercekten daha canli olmali (${copy.strongHp} > ${copy.weakHp})",
            copy.strongHp > copy.weakHp
        )
    }

    /** Farki okunmayan bir ornek ciftinde ders CIZILMEZ. */
    @Test
    fun theTargetingLessonSkipsWavesWhereBothSamplesShareTheSameHealth() {
        val twins = GameConfig.EnemyType.values()
            .groupBy { GameConfig.ENEMY_SPECS.getValue(it).maxHp }
            .values
            .firstOrNull { it.size >= 2 }
            ?.take(2)
            ?.toSet()

        // Kampanyada esit canli iki tip yoksa kural zaten ihlal edilemez.
        if (twins == null) return

        assertNull(
            "esit canli iki ornekle ders yarim kalir, cizilmemeli",
            HintFlow.copyFor(
                UnlockHint.TARGETING_INTRO,
                signals(fieldTowers = listOf(snapshot("t1")), incomingTypes = twins)
            )
        )
    }

    // =======================================================================
    // 3) GUCLENDIRICILER — tetikleyici "ray GERCEKTEN buton ciziyor"
    // =======================================================================

    /**
     * Gorevin en acik kurali: guclendirici dersi, guclendirici GERCEKTEN
     * ERISILEBILIR oldugunda cikmali. Kaynak `BoosterRail`'in okudugu AYNI
     * fonksiyondur, yani ray ile ders ASLA ayrisamaz.
     */
    @Test
    fun theBoosterLessonFollowsTheRailExactly() {
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val railHasButtons = boostersAvailableAt(level).isNotEmpty()
            assertEquals(
                "L$level: ray ${if (railHasButtons) "dolu" else "bos"} ama ders " +
                    "tersini soyluyor",
                railHasButtons,
                HintFlow.isTriggered(UnlockHint.BOOSTER_INTRO, signals(levelId = level))
            )
        }
    }

    /** Ders, ray'in EN ALTTAKI (ilk acilan) butonunu anlatir. */
    @Test
    fun theBoosterLessonTeachesTheFirstUnlockedBooster() {
        val level = (1..GameConfig.CAMPAIGN_LEVEL_COUNT)
            .first { boostersAvailableAt(it).isNotEmpty() }
        val expected = boostersAvailableAt(level).minByOrNull { it.unlockLevel }

        val copy = HintFlow.copyFor(UnlockHint.BOOSTER_INTRO, signals(levelId = level))
            as HintCopy.BoosterIntro

        assertEquals(expected, copy.booster)
        assertTrue("etki miktari anlamli olmali", copy.effectAmount > 0)
    }

    /**
     * Ray'in dolu oldugu HER bolumde ders uretilebilmeli ve ray'in en alttaki
     * butonunu anlatmali.
     *
     * Etki ve bedel birimleri tipe gore degisiyor (Tedarik / yuzde hasar /
     * yuzde us cani; reklam / Tedarik / Coin). Bir gun kill switch ile
     * guclendiriciler kapatilir ya da kilit bolumleri kayarsa ders sessizce
     * KAYBOLMAMALI: bu dongu butun kampanyayi tarar.
     */
    @Test
    fun theBoosterLessonWorksAtEveryLevelWhereTheRailIsDrawn() {
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val expected = boostersAvailableAt(level).minByOrNull { it.unlockLevel } ?: continue
            val copy = HintFlow.copyFor(UnlockHint.BOOSTER_INTRO, signals(levelId = level))
            assertNotNull("L$level: guclendirici dersi uretilemedi", copy)
            assertEquals(
                "L$level: ders ray'in en alttaki butonunu anlatmali",
                expected,
                (copy as HintCopy.BoosterIntro).booster
            )
            assertTrue("L$level: etki miktari anlamli olmali", copy.effectAmount > 0)
        }
    }

    /** Kill switch'i kapali bir guclendirici ne ray'de cizilir ne ders konusu olur. */
    @Test
    fun disabledBoostersNeverBecomeTheLesson() {
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val chosen = HintFlow.firstBooster(level) ?: continue
            assertTrue("L$level: kapali guclendirici ders konusu olamaz", chosen.enabled)
            assertTrue("L$level: kilidi acilmamis guclendirici secilemez", level >= chosen.unlockLevel)
        }
        // BoosterType tablosu bir gun bosalirsa bu testin olctugu sey kalmaz.
        assertTrue("guclendirici tablosu bos olmamali", BoosterType.values().isNotEmpty())
    }

    /** Aralik disi bolum motoru COKTURMEMELI ([boosterPrice] `require` atar). */
    @Test
    fun outOfRangeLevelsProduceNoBoosterLesson() {
        for (level in listOf(-1, 0, GameConfig.CAMPAIGN_LEVEL_COUNT + 1, 9_999)) {
            assertNull("L$level icin guclendirici secilmemeli", HintFlow.firstBooster(level))
            assertFalse(
                "L$level icin ders tetiklenmemeli",
                HintFlow.isTriggered(UnlockHint.BOOSTER_INTRO, signals(levelId = level))
            )
        }
    }

    // =======================================================================
    // 4) SATMA — tetikleyici "insa tikandi + yedek kule var"
    // =======================================================================

    @Test
    fun theSellLessonNeedsBothBlockedBuildingAndASpareTower() {
        val two = listOf(snapshot("a", sellValue = 42), snapshot("b", sellValue = 84))

        assertFalse(
            "insa tikanmamisken satis dersi cikmamali",
            HintFlow.isTriggered(
                UnlockHint.SELL_INTRO,
                signals(fieldTowers = two, buildBlocked = false)
            )
        )
        assertTrue(
            "insa tikandiginda ve yedek kule varken ders cikmali",
            HintFlow.isTriggered(
                UnlockHint.SELL_INTRO,
                signals(fieldTowers = two, buildBlocked = true)
            )
        )
    }

    /**
     * TEK kulesi olan oyuncuya "satabilirsin" demek savunmasini bosaltmaya
     * davettir. Bu, oncelik listesinin en altinda olmasiyla AYNI gerekce.
     */
    @Test
    fun theSellLessonNeverFiresWithASingleTower() {
        assertFalse(
            "tek kule varken satis dersi ZARARLI olurdu",
            HintFlow.isTriggered(
                UnlockHint.SELL_INTRO,
                signals(fieldTowers = listOf(snapshot("only")), buildBlocked = true)
            )
        )
    }

    /** Ornek, en az yatirim yapilmis kule: en az yikici oneri. */
    @Test
    fun theSellLessonPointsAtTheLeastValuableTower() {
        val copy = HintFlow.copyFor(
            UnlockHint.SELL_INTRO,
            signals(
                fieldTowers = listOf(
                    snapshot("rich", sellValue = 300),
                    snapshot("poor", sellValue = 42)
                ),
                buildBlocked = true
            )
        ) as HintCopy.SellRefund

        assertEquals("poor", copy.towerId)
        assertEquals(42, copy.refund)
        assertTrue(
            "geri donus orani her zaman %100'un ALTINDA (satis kar ettirmez)",
            copy.salvagePercent in 1..99
        )
    }

    // =======================================================================
    // 5) ORTAK SOZLESMELER — dordu de dosyanin kurallarini bozmamali
    // =======================================================================

    /**
     * Hicbir yeni ipucu bir kule KILIDINE baglanmadi.
     *
     * `UnlockHintFlowTest.everyLockedTowerHasItsOwnHint` "kilitli her kulenin
     * bir rol dersi var" esitligini kilitliyor; yeni ipuclarindan biri
     * `unlockTower` doldurursa o esitlik sessizce bozulurdu.
     */
    @Test
    fun theNewLessonsAreNotBoundToTowerUnlocks() {
        for (hint in newHints) {
            assertNull("$hint bir kule kilidine baglanmamali", hint.unlockTower)
        }
    }

    /** Tetikleyici olay olmali: kosullar saglanmadan hicbiri cikmaz. */
    @Test
    fun noNewLessonFiresOnAnEmptyBattlefield() {
        val nothingHappened = signals(levelId = 1)
        for (hint in newHints) {
            // Guclendirici L1'de ray'de de yok; digerleri saha bos oldugu icin yok.
            assertFalse(
                "$hint bos bir sahada tetiklenmemeli",
                HintFlow.isTriggered(hint, nothingHappened)
            )
        }
    }

    /** Ilk oturum ogreticisi kosarken hicbiri cizilmez — iki serit ust uste binemez. */
    @Test
    fun theTutorialSuppressesTheNewLessons() {
        val everythingEligible = allEligibleSignals().copy(tutorialArmed = true)
        assertNull(
            "ogretici kosarken hicbir ders cikmamali",
            HintFlow.nextHint(HintFlow.start(emptySet()), everythingEligible)
        )
    }

    /**
     * **AYNI ANDA EN FAZLA BIR MESAJ.**
     *
     * Dordu birden uygun olsa bile bir hazirlik fazinda tek serit cizilir;
     * digerleri SONRAKI fazlara kayar. Dosyanin en sik ihlal edilebilecek
     * kurali bu: yeni bir ders eklemek, ayni fazda ikinci bir mesaj uretmenin
     * en kolay yolu.
     */
    @Test
    fun onlyOneOfTheNewLessonsIsShownPerPreparationPhase() {
        val eligible = allEligibleSignals()
        // Kule rolleri disarida: bu test yeni dersleri olcuyor.
        val seen = setOf(
            UnlockHint.ARMOR_INTRO,
            UnlockHint.MISSILE_ROLE,
            UnlockHint.CANNON_ROLE,
            UnlockHint.FROST_ROLE
        )

        var state = advance(
            HintFlow.update(HintFlow.start(seen), eligible, FRAME),
            eligible,
            HintFlow.VISIBLE_SECONDS + 0.2f
        )
        val first = (state.seen - seen).single()

        state = advance(state, eligible, HintFlow.VISIBLE_SECONDS * 2f)
        assertNull("ayni hazirlik fazinda ikinci ders cikmamali", state.active)

        val nextWave = eligible.copy(waveIndex = eligible.waveIndex + 1)
        state = HintFlow.update(state, nextWave, FRAME)
        assertNotNull("sonraki hazirlik fazinda sira digerine gelmeli", state.active)
        assertTrue("ayni ders tekrar edilmemeli", state.active != first)
    }

    /**
     * Geri bildirim hiyerarsisi: kritik uyari ve yeni kule haberi, yonetim
     * derslerinin ONUNDE. Yeni dersler oncelik listesinin ALTINDA durmali.
     */
    @Test
    fun theNewLessonsRankBelowThreatAndUnlockNews() {
        val lowestOldRank = listOf(
            UnlockHint.ARMOR_INTRO,
            UnlockHint.MISSILE_ROLE,
            UnlockHint.CANNON_ROLE,
            UnlockHint.FROST_ROLE
        ).maxOf { HintFlow.PRIORITY.indexOf(it) }

        for (hint in newHints) {
            assertTrue(
                "$hint kule/zirh haberlerinin ONUNE gecmemeli",
                HintFlow.PRIORITY.indexOf(hint) > lowestOldRank
            )
        }
        assertEquals(
            "satis en son ogretilmeli: geri alinmasi en pahali eylem",
            UnlockHint.SELL_INTRO,
            HintFlow.PRIORITY.last()
        )
    }

    /** Dalga baslayinca serit duser; oyuncuyu HICBIR sekilde engellemez. */
    @Test
    fun startingTheWaveDropsTheNewLessonsToo() {
        val eligible = allEligibleSignals()
        var state = HintFlow.update(HintFlow.start(emptySet()), eligible, FRAME)
        assertNotNull(state.active)

        state = HintFlow.update(
            state,
            eligible.copy(gameState = GameState.WAVE_RUNNING),
            FRAME
        )
        assertNull("dalga baslayinca serit dusmeli", state.active)
    }

    /** Okunmadan kaybolan ders HARCANMIS sayilmaz; sonraki fazda geri gelir. */
    @Test
    fun anUnreadNewLessonIsNotBurned() {
        val eligible = allEligibleSignals()
        var state = HintFlow.update(HintFlow.start(emptySet()), eligible, FRAME)
        state = advance(state, eligible, HintFlow.MIN_READ_SECONDS - 0.5f)
        state = HintFlow.update(state, eligible.copy(gameState = GameState.WAVE_RUNNING), FRAME)

        assertTrue("okunmadan kaybolan ders yazilmamali", state.seen.isEmpty())
    }

    /**
     * Kalici anahtarlar BENZERSIZ ve DEGISMEZ.
     *
     * Anahtar govdeleri burada ACIKCA sabitleniyor: enum adi bir refactor'da
     * degisebilir, kalici anahtar degisemez — degisirse ders butun eski
     * kayitlarda yeniden gorunur.
     */
    @Test
    fun theNewLessonsCarryStableSaveIds() {
        assertEquals("upgrade_intro", UnlockHint.UPGRADE_INTRO.saveId)
        assertEquals("targeting_intro", UnlockHint.TARGETING_INTRO.saveId)
        assertEquals("booster_intro", UnlockHint.BOOSTER_INTRO.saveId)
        assertEquals("sell_intro", UnlockHint.SELL_INTRO.saveId)

        val ids = UnlockHint.values().map { it.saveId }
        assertEquals("kalici anahtarlar benzersiz olmali", ids.size, ids.toSet().size)
    }

    /** Tetiklenen her ders METIN URETEBILMELI, yoksa serit bos kalir. */
    @Test
    fun everyTriggeredNewLessonProducesCopy() {
        val eligible = allEligibleSignals()
        for (hint in newHints) {
            assertTrue("$hint bu fikstur ile tetiklenmeli", HintFlow.isTriggered(hint, eligible))
            assertNotNull("$hint tetiklendi ama metin uretemedi", HintFlow.copyFor(hint, eligible))
        }
    }

    // =======================================================================
    // Dordu de ayni anda uygun olan durum
    // =======================================================================

    /**
     * Dort dersin de kosulu ayni anda saglanan sinyal.
     *
     * Guclendirici icin ray'in dolu oldugu ilk bolum secilir; kalan uc kosul
     * saha durumundan gelir.
     */
    private fun allEligibleSignals(): HintSignals {
        val level = (1..GameConfig.CAMPAIGN_LEVEL_COUNT)
            .first { boostersAvailableAt(it).isNotEmpty() }
        return signals(
            levelId = level,
            waveIndex = 0,
            incomingTypes = setOf(
                GameConfig.EnemyType.INFANTRY,
                GameConfig.EnemyType.TANK
            ),
            fieldTowers = listOf(snapshot("a", sellValue = 42), snapshot("b", sellValue = 84)),
            supply = 9_999,
            buildBlocked = true
        )
    }
}
