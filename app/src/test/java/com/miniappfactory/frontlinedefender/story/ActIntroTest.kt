package com.miniappfactory.frontlinedefender.story

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.ui.ActIntro
import com.miniappfactory.frontlinedefender.game.ui.ActIntroStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERDE ACILIS KARTI — PERDE ARITMETIGI VE BIR-KEZ-GOSTERIM SOZLESMESI.
 *
 * `docs/STORY.md` §4/§5. Karti CIZEN kod Compose'dur ve burada calismaz;
 * kilitlenen sey, kartin ne zaman cikacagina karar veren SAF mantiktir:
 *
 *   K1 — kart yalnizca perde acilislarinda (1, 12, 23, 34, 45) cikar
 *   K2 — bir kez gorulduyse BIR DAHA cikmaz
 *   —    ve `ActIntro.actOf` ile `GameConfig.LevelSpec.act` ayni cevabi verir
 *
 * Son madde onemli: iki ayri yerde ayni formul yaziliydi. Kayarlarsa kart
 * "Perde III" yazarken oyun Act IV yuklerdi ve bunu hicbir derleyici
 * soylemezdi.
 */
class ActIntroTest {

    /** Bellek-ici bayrak — `SaveManager`a (ve Android `Context`ine) gerek yok. */
    private class FakeActIntroStore : ActIntroStore {
        val seen = mutableSetOf<Int>()
        var markCount = 0
        override fun isSeen(act: Int): Boolean = act in seen
        override fun markSeen(act: Int) {
            markCount++
            seen.add(act)
        }
    }

    /**
     * `LevelSelectScreen.deploy` kapisinin saf ikizi. Ekranin kendisi Compose
     * oldugu icin buraya kopyalanmadi, MODELLENDI: kapi "kart mi, savas mi"
     * sorusuna cevap verir ve bu testin dogruladigi sey o cevaptir.
     */
    private fun gate(store: ActIntroStore?, levelId: Int): Boolean =
        store != null &&
            ActIntro.isActOpener(levelId) &&
            !store.isSeen(ActIntro.actOf(levelId))

    // =====================================================================
    // Perde aritmetigi
    // =====================================================================

    @Test
    fun actOpenersAreTheFirstLevelOfEachAct() {
        val openers = (1..GameConfig.CAMPAIGN_LEVEL_COUNT).filter { ActIntro.isActOpener(it) }
        assertEquals(listOf(1, 12, 23, 34, 45), openers)
    }

    @Test
    fun campaignCoversExactlyFiveActsOfElevenLevels() {
        assertEquals(
            "STORY.md perde yayi 5 perde x 11 bolum varsayiyor; kampanya boyu " +
                "degistiyse anlati iskeleti de degismelidir",
            GameConfig.CAMPAIGN_LEVEL_COUNT,
            ActIntro.ACT_COUNT * ActIntro.LEVELS_PER_ACT
        )
    }

    @Test
    fun actOfAgreesWithGameConfigForEveryCampaignLevel() {
        (1..GameConfig.CAMPAIGN_LEVEL_COUNT).forEach { levelId ->
            assertEquals(
                "bolum $levelId: perde karti ile motorun perde numarasi ayrisiyor",
                GameConfig.levelSpec(levelId).act,
                ActIntro.actOf(levelId)
            )
        }
    }

    @Test
    fun firstAndLastLevelOfEachActBracketElevenLevels() {
        (1..ActIntro.ACT_COUNT).forEach { act ->
            val first = ActIntro.firstLevelOf(act)
            val last = ActIntro.lastLevelOf(act)
            assertEquals("perde $act ilk bolumu", (act - 1) * 11 + 1, first)
            assertEquals("perde $act son bolumu", act * 11, last)
            assertTrue("perde $act acilisi opener olmali", ActIntro.isActOpener(first))
        }
    }

    @Test
    fun outOfRangeLevelsNeverOpenACard() {
        listOf(0, -1, 56, 67, Int.MIN_VALUE, Int.MAX_VALUE).forEach { levelId ->
            assertFalse("bolum $levelId kart acmamali", ActIntro.isActOpener(levelId))
        }
    }

    @Test
    fun hintIdsAreStableAndDistinctPerAct() {
        val ids = (1..ActIntro.ACT_COUNT).map { ActIntro.hintId(it) }
        assertEquals(
            listOf("act_intro_1", "act_intro_2", "act_intro_3", "act_intro_4", "act_intro_5"),
            ids
        )
        assertEquals("kimlikler benzersiz olmali", ids.size, ids.toSet().size)
    }

    // =====================================================================
    // K1 / K2 — kapi davranisi
    // =====================================================================

    @Test
    fun cardOpensOnlyOnActOpeners() {
        val store = FakeActIntroStore()
        val opened = (1..GameConfig.CAMPAIGN_LEVEL_COUNT).filter { gate(store, it) }
        assertEquals(
            "kart 55 bolumun yalnizca 5'inde cikar; kalan 50 bolum dogrudan savasa gider",
            listOf(1, 12, 23, 34, 45),
            opened
        )
    }

    @Test
    fun cardNeverOpensTwiceForTheSameAct() {
        val store = FakeActIntroStore()

        assertTrue("Perde II ilk giris kart acmali", gate(store, 12))
        store.markSeen(ActIntro.actOf(12))

        assertFalse("Perde II tekrar oynanisi kart ACMAMALI", gate(store, 12))
        assertEquals(1, store.markCount)

        // Diger perdeler etkilenmez — bayraklar ayriktir.
        assertTrue("Perde III hala kart acmali", gate(store, 23))
    }

    @Test
    fun aReplayedCampaignShowsEachCardExactlyOnce() {
        val store = FakeActIntroStore()
        var shown = 0
        // Kampanya iki kez bastan sona oynanir.
        repeat(2) {
            (1..GameConfig.CAMPAIGN_LEVEL_COUNT).forEach { levelId ->
                if (gate(store, levelId)) {
                    shown++
                    store.markSeen(ActIntro.actOf(levelId))
                }
            }
        }
        assertEquals("her perde karti tam bir kez gosterilmeli", ActIntro.ACT_COUNT, shown)
    }

    @Test
    fun withoutAStoreTheScreenBehavesExactlyAsBefore() {
        (1..GameConfig.CAMPAIGN_LEVEL_COUNT).forEach { levelId ->
            assertFalse(
                "actIntroStore baglanmadan hicbir bolum kart acmamali " +
                    "(varsayilan davranis bugunkuyle birebir ayni)",
                gate(null, levelId)
            )
        }
    }
}
