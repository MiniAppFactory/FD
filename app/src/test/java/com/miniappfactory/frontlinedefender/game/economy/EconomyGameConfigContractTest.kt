package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 9 — EKONOMI <-> GAMECONFIG SOZLESMESI.
 *
 * Kampanya bedelleri iki yerde gorunur: motorun okudugu `GameConfig.CAMPAIGN`
 * (`deploymentCost`) ve ekonominin okudugu [EconomyConfig.LOCK_COSTS]. Ikisi
 * ayrisirsa bolum secme ekrani bir bedel gosterip cuzdan baska bir bedel duser —
 * sessiz ve teshisi zor bir hata. Bu sinif ayrismayi **derleme sonrasi ilk testte**
 * yakalar.
 *
 * `GameConfig` saf Kotlin oldugu icin (Android importu yok) burada Robolectric
 * gerekmez.
 */
class EconomyGameConfigContractTest {

    @Test
    fun campaignLengthAgrees() {
        assertEquals(EconomyConfig.CAMPAIGN_LEVELS, GameConfig.CAMPAIGN_LEVEL_COUNT)
        assertEquals(EconomyConfig.CAMPAIGN_LEVELS, GameConfig.CAMPAIGN.size)
    }

    @Test
    fun deploymentCostsAgreeLevelByLevel() {
        GameConfig.CAMPAIGN.forEach { spec ->
            assertEquals(
                "L${spec.levelId}: GameConfig ${spec.deploymentCost} vs ekonomi ${lockCost(spec.levelId)}",
                lockCost(spec.levelId),
                spec.deploymentCost,
            )
        }
    }

    @Test
    fun freeLevelsAgree() {
        val freeInGameConfig = GameConfig.CAMPAIGN.filter { it.deploymentCost == 0 }.map { it.levelId }
        assertEquals((1 until EconomyConfig.FIRST_PAID_LEVEL).toList(), freeInGameConfig)
    }

    @Test
    fun starThresholdsAgree() {
        assertEquals(
            EconomyConfig.STAR3_HEALTH_RATIO,
            GameConfig.STAR3_LIVES_FRACTION.toDouble(),
            1e-6,
        )
        assertEquals(
            EconomyConfig.STAR2_HEALTH_RATIO,
            GameConfig.STAR2_LIVES_FRACTION.toDouble(),
            1e-6,
        )
    }

    @Test
    fun baseLivesAndStartingSupplyAgree() {
        assertEquals(EconomyConfig.BASE_MAX_HEALTH, GameConfig.INITIAL_BASE_LIVES)
        assertEquals(EconomyConfig.BASE_STARTING_SUPPLY, GameConfig.INITIAL_GOLD)
        // Meta yukseltmenin tabani motorun tabaniyla ayni olmali, yoksa rank 0
        // oyuncu bile farkli baslar.
        assertEquals(GameConfig.INITIAL_GOLD, MetaUpgrades().startingSupply)
        assertEquals(GameConfig.INITIAL_BASE_LIVES, MetaUpgrades().maxBaseHealth)
    }

    @Test
    fun engineStarLogicMatchesEconomyStarLogic() {
        // Motor `livesFraction >= GameConfig.STAR3_LIVES_FRACTION` kullaniyor;
        // ekonomi `starsFor` kullaniyor. Ayni girdide ayni sonucu vermeleri ZORUNLU,
        // yoksa gosterilen yildiz ile odenen coin celisir.
        for (maxLives in listOf(20, 24, 30)) {
            for (lives in 0..maxLives) {
                val fraction = lives.toFloat() / maxLives
                val engineStars = when {
                    lives <= 0 -> 0
                    fraction >= GameConfig.STAR3_LIVES_FRACTION -> 3
                    fraction >= GameConfig.STAR2_LIVES_FRACTION -> 2
                    else -> 1
                }
                assertEquals(
                    "maxLives=$maxLives lives=$lives",
                    engineStars,
                    starsFor(lives, maxLives),
                )
            }
        }
    }

    @Test
    fun fortificationCeilingStaysWithinDesignedRange() {
        // GDD F: 20 -> 30 idi; ECONOMY_AUDIT_2 P0 ile hat 9 rank'a cikti, 20 -> 38.
        // Motor `LevelSpec.maxBaseLives` uzerinden calisiyor, meta bunu buyuttugu
        // icin yildiz esiklerinin yuzde olmasi zorunluydu (B.3).
        val top = UpgradeLine.FORTIFICATION.maxRank
        assertEquals(38, MetaUpgrades(fortification = top).maxBaseHealth)
        assertEquals(
            "her rank +2 can — tavan hattin uzunlugundan turemeli, elle yazilmamali",
            GameConfig.INITIAL_BASE_LIVES + 2 * top,
            MetaUpgrades(fortification = top).maxBaseHealth,
        )
        // **SERT TAVAN.** 40 canda tam meta L11'i (4 sizinti) 3 yildiza cevirir;
        // EconomySimulationTest.maxedMetaDoesNotMakePeakLevelsThreeStarTrivial.
        assertTrue(
            "meta us cani tavani 38'i GECEMEZ (zorluk egrisi kilidi)",
            MetaUpgrades(fortification = top).maxBaseHealth <= 38,
        )
    }
}
