package com.miniappfactory.frontlinedefender.tutorial

import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.game.ui.HintCopy
import com.miniappfactory.frontlinedefender.game.ui.HintFlow
import com.miniappfactory.frontlinedefender.game.ui.HintSignals
import com.miniappfactory.frontlinedefender.game.ui.UnlockHint
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IPUCU, OYUNCUNUN O BOLUMDE GORDUGU BIR DUSMANI ANLATMALI.
 *
 * ---------------------------------------------------------------------------------
 * NEDEN BU TEST VAR
 * ---------------------------------------------------------------------------------
 * `HintFlow.pick` aday listesinde bolumun dusmani yoksa **listenin ilkine
 * dusuyordu**. Somut sonuc: L3'te Top acilir, `CANNON_TARGETS`'in ilki Kalkanli
 * Er'dir, ama L3'un butun dalgalari yalnizca piyade + hizli er — Kalkanli Er
 * ilk kez **L9'da** cikar. Yani oyuncunun hayatindaki ILK baglamsal ipucu, alti
 * bolum sonra gorecegi bir birimi anlatiyordu.
 *
 * `TutorialOverlay.kt` bunu kendi sozlesmesinde zaten YASAKLIYORDU
 * ("oyuncunun HIC gormedigi bir dusmanla ders vermek ogretmez, kafa
 * karistirir") — sozlesme yaziliydi, kod onu tutmuyordu, hicbir test de
 * sormuyordu.
 *
 * Test kampanyanin GERCEK dalga tablosundan besleniyor, elle yazilmis bir
 * dusman listesinden degil: kampanya yeniden sekillenirse test onunla birlikte
 * tasinir.
 */
class HintTeachesPresentEnemyTest {

    private fun enemiesOf(levelId: Int): Set<GameConfig.EnemyType> =
        WaveDefinitions.wavesFor(levelId)
            .flatMap { wave -> wave.spawns.map { it.enemyType } }
            .toSet()

    private fun signalsFor(levelId: Int) = HintSignals(
        gameState = GameState.PREPARATION,
        levelId = levelId,
        waveIndex = 0,
        tutorialArmed = false,
        levelEnemyTypes = enemiesOf(levelId),
        incomingArmoredTypes = emptySet()
    )

    /** Ipucunun ekranda ADINI VERDIGI dusmanlar. */
    private fun enemiesNamedBy(copy: HintCopy): Set<GameConfig.EnemyType> = when (copy) {
        is HintCopy.TowerMatchup -> setOf(copy.enemy)
        is HintCopy.ArmorContrast -> setOf(copy.softEnemy, copy.armoredEnemy)
        is HintCopy.SupportRole -> emptySet()
    }

    @Test
    fun noHintEverNamesAnEnemyThatDoesNotAppearInThatLevel() {
        val violations = mutableListOf<String>()
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val signals = signalsFor(level)
            val present = signals.levelEnemyTypes
            for (hint in UnlockHint.values()) {
                if (!HintFlow.isTriggered(hint, signals)) continue
                val copy = HintFlow.copyFor(hint, signals) ?: continue
                for (named in enemiesNamedBy(copy)) {
                    if (named !in present) {
                        violations += "L$level / $hint -> ${named.name} bu bolumde cikmiyor"
                    }
                }
            }
        }
        assertTrue(
            "Ipucu, oyuncunun o bolumde HIC gormedigi bir dusmani anlatiyor:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /**
     * Ders DUSMUYOR, ERTELENIYOR.
     *
     * Ornek yoksa ipucu cizilmez; ama "goruldu" de yazilmadigi icin ornek
     * sahaya ilk ciktiginda kendiliginden gelmeli. Bu test onu kanitliyor:
     * kule ipuclarinin her biri kampanyanin BIR yerinde gosterilebilir olmali,
     * yoksa sessizce kaybolmus demektir.
     */
    @Test
    fun everyTowerHintStillBecomesShowableSomewhereInTheCampaign() {
        val towerHints = UnlockHint.values().filter { it.unlockTower != null }
        for (hint in towerHints) {
            val firstLevel = (1..GameConfig.CAMPAIGN_LEVEL_COUNT).firstOrNull { level ->
                val signals = signalsFor(level)
                HintFlow.isTriggered(hint, signals) && HintFlow.copyFor(hint, signals) != null
            }
            assertNotNull(
                "$hint kampanyanin HICBIR bolumunde gosterilemiyor — ders " +
                    "ertelenmis degil, kaybolmus.",
                firstLevel
            )
        }
    }

    /**
     * Hatanin kendisi: L3'te Top acilir ama Kalkanli Er yoktur.
     *
     * Bu test tek bir bolumu sabitliyor cunku kullanicinin gordugu ILK ipucu
     * oydu ve regresyonun en olasi yeri orasidir.
     */
    @Test
    fun theCannonHintDoesNotTalkAboutShieldedTrooperBeforeItExists() {
        val cannonUnlock = GameConfig.TOWER_SPECS
            .getValue(GameConfig.TowerType.CANNON).unlockedAtLevel
        val signals = signalsFor(cannonUnlock)
        val copy = HintFlow.copyFor(UnlockHint.CANNON_ROLE, signals)
        if (copy != null) {
            for (named in enemiesNamedBy(copy)) {
                assertTrue(
                    "Top ipucu L$cannonUnlock'te ${named.name} anlatiyor ama o " +
                        "dusman bu bolumde cikmiyor",
                    named in signals.levelEnemyTypes
                )
            }
        }
    }
}
