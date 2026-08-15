package com.miniappfactory.frontlinedefender.progression

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YILDIZ HESABI — bilinen bug alani.
 *
 * Sozlesme (GDD B.3 + GameConfig KDoc): yildiz **kalan us cani YUZDESINE**
 * gore verilir, mutlak cana gore DEGIL. Us cani bolume ve meta yukseltmelere
 * gore degisiyor (`LevelSpec.maxBaseLives`: 20 -> meta ile 30), bu yuzden
 * mutlak esik yanlis yildiz verir.
 *
 * ---------------------------------------------------------------------------
 * TEST EDILEBILIRLIK BORCU
 * ---------------------------------------------------------------------------
 * Hesap `GameEngine.tick()` icine GOMULU (GameEngine.kt:697-703) ve
 * `GameEngine` yapicisinda `SaveManager(Context)` + `AudioManager(Context)`
 * istiyor — yani saf JUnit'te ornek uretilemiyor. Bu yuzden asagidaki
 * `starsForRemainingLives` motorun formulunun **aynasidir**, motorun kendisi
 * degil.
 *
 * Sonuc: bu test yanlis formulu yakalamaz, yalnizca SABITLERI ve DOGRU
 * formulun davranisini pinler. Gercek koruma icin hesap
 * `GameConfig.starsForRemainingLives(lives, maxLives)` gibi saf bir fonksiyona
 * cikarilmali ve hem motor hem VictoryModal onu cagirmali.
 * Bkz. docs/QA_REPORT.md B-01.
 */
class StarRatingTest {

    /** GameEngine.kt:697-703'teki formulun aynasi. */
    private fun starsForRemainingLives(lives: Int, maxLives: Int): Int {
        val max = maxLives.coerceAtLeast(1)
        val fraction = lives.toFloat() / max
        return when {
            fraction >= GameConfig.STAR3_LIVES_FRACTION -> 3
            fraction >= GameConfig.STAR2_LIVES_FRACTION -> 2
            else -> 1
        }
    }

    /** GameDialogs.kt:155-159'daki HATALI mutlak-esik formulu. */
    private fun starsHardcodedAbsolute(lives: Int): Int = when {
        lives >= 18 -> 3
        lives >= 10 -> 2
        else -> 1
    }

    // ------------------------------------------------------- sabit sozlesmesi

    @Test
    fun starThresholdsAreFractionsNotAbsoluteLifeCounts() {
        assertTrue(
            "STAR3_LIVES_FRACTION ${GameConfig.STAR3_LIVES_FRACTION} bir ORAN olmali (0..1). " +
                "1'den buyukse mutlak can esigine geri donulmus.",
            GameConfig.STAR3_LIVES_FRACTION in 0f..1f
        )
        assertTrue(
            "STAR2_LIVES_FRACTION ${GameConfig.STAR2_LIVES_FRACTION} bir ORAN olmali (0..1)",
            GameConfig.STAR2_LIVES_FRACTION in 0f..1f
        )
    }

    @Test
    fun theThreeStarThresholdIsStricterThanTheTwoStarThreshold() {
        assertTrue(
            "3 yildiz esigi 2 yildiz esiginden yuksek olmali",
            GameConfig.STAR3_LIVES_FRACTION > GameConfig.STAR2_LIVES_FRACTION
        )
    }

    @Test
    fun starThresholdsMatchTheGddContract() {
        // GDD B.3: %90 -> 3 yildiz, %50 -> 2 yildiz, >0 -> 1 yildiz.
        assertEquals(0.90f, GameConfig.STAR3_LIVES_FRACTION, 1e-6f)
        assertEquals(0.50f, GameConfig.STAR2_LIVES_FRACTION, 1e-6f)
    }

    // -------------------------------------------- 20 canli varsayilan bolumler

    @Test
    fun withTwentyMaxLivesTheThresholdsLandOnEighteenAndTen() {
        val max = 20
        assertEquals("20/20 = %100", 3, starsForRemainingLives(20, max))
        assertEquals("18/20 = %90 tam esik", 3, starsForRemainingLives(18, max))
        assertEquals("17/20 = %85", 2, starsForRemainingLives(17, max))
        assertEquals("10/20 = %50 tam esik", 2, starsForRemainingLives(10, max))
        assertEquals("9/20 = %45", 1, starsForRemainingLives(9, max))
        assertEquals("1/20 = %5", 1, starsForRemainingLives(1, max))
    }

    @Test
    fun defaultLevelsUseTwentyBaseLives() {
        assertEquals(20, GameConfig.INITIAL_BASE_LIVES)
        GameConfig.CAMPAIGN.forEach { spec ->
            assertEquals(
                "bolum ${spec.levelId} varsayilan us canini kullanmiyor",
                GameConfig.INITIAL_BASE_LIVES, spec.maxBaseLives
            )
        }
    }

    // --------------------------------- 30 canli bolumler (meta yukseltme sonrasi)

    @Test
    fun withThirtyMaxLivesTheThresholdsScaleToTwentySevenAndFifteen() {
        val max = 30
        assertEquals("30/30 = %100", 3, starsForRemainingLives(30, max))
        assertEquals("27/30 = %90 tam esik", 3, starsForRemainingLives(27, max))
        assertEquals("26/30 = %86.7 — 3 yildiz OLMAMALI", 2, starsForRemainingLives(26, max))
        assertEquals("18/30 = %60 — 2 yildiz", 2, starsForRemainingLives(18, max))
        assertEquals("15/30 = %50 tam esik", 2, starsForRemainingLives(15, max))
        assertEquals("14/30 = %46.7", 1, starsForRemainingLives(14, max))
    }

    /**
     * Bu, bug'in TAM OLARAK nerede oldugunu gosteren test.
     *
     * `GameDialogs.kt:155-159` yildizi mutlak cana gore hesapliyor. 30 canli
     * bir bolumu 18 canla bitiren oyuncuya:
     *   - motor  (GameEngine.kt:697-703, dogru) -> %60 -> **2 yildiz** kaydeder
     *   - modal  (GameDialogs.kt:155-159, hatali) -> 18 >= 18 -> **3 yildiz** gosterir
     *
     * Oyuncu 3 yildiz gorur, bolum secme ekraninda 2 yildiz bulur.
     */
    @Test
    fun theHardcodedModalFormulaDisagreesWithTheEngineAtThirtyMaxLives() {
        val max = 30
        val lives = 18

        val engineStars = starsForRemainingLives(lives, max)
        val modalStars = starsHardcodedAbsolute(lives)

        assertEquals("motor formulu: 18/30 = %60 -> 2 yildiz", 2, engineStars)
        assertEquals("modal formulu: 18 >= 18 -> 3 yildiz", 3, modalStars)
        assertNotEquals(
            "GameDialogs.kt:155-159 mutlak esikleri motorun yuzde hesabiyla " +
                "uyusmuyor — modal 3 yildiz gosterirken kayit 2 yildiz tutuyor",
            engineStars, modalStars
        )
    }

    @Test
    fun theHardcodedModalFormulaAlsoUnderRewardsHighLifeCountsAtLowMaxLives() {
        // 10 canli bir bolumu HIC can kaybetmeden bitirmek: %100 -> 3 yildiz.
        // Mutlak formul 10 >= 18 degil, 10 >= 10 -> 2 yildiz verir.
        val max = 10
        assertEquals("10/10 = %100 -> 3 yildiz", 3, starsForRemainingLives(10, max))
        assertEquals("mutlak formul kusursuz oyunu 2 yildizla cezalandirir", 2, starsHardcodedAbsolute(10))
    }

    @Test
    fun theTwoFormulasOnlyAgreeAtTheDefaultTwentyLifeConfiguration() {
        // 20 canli bolumlerde ikisi ayni sonucu verir — bug bu yuzden gozden
        // kacti. Herhangi bir bolum 20'den farkli maxBaseLives kullandigi anda
        // uyusmazlik oyuncuya gorunur hale gelir.
        for (lives in 0..20) {
            assertEquals(
                "maxLives=20, lives=$lives: iki formul burada uyusmali",
                starsForRemainingLives(lives, 20), starsHardcodedAbsolute(lives)
            )
        }

        val disagreements = (0..30).count { starsForRemainingLives(it, 30) != starsHardcodedAbsolute(it) }
        assertTrue(
            "maxLives=30'da iki formul en az bir yerde ayrismali (bulunan: $disagreements)",
            disagreements > 0
        )
    }

    // ---------------------------------------------------- genel invaryantlar

    @Test
    fun starsAreAlwaysBetweenOneAndThreeForAnySurvivingBase() {
        for (max in intArrayOf(1, 5, 10, 20, 30, 50, 99)) {
            for (lives in 1..max) {
                val stars = starsForRemainingLives(lives, max)
                assertTrue("max=$max lives=$lives -> $stars (1..3 disinda)", stars in 1..3)
            }
        }
    }

    @Test
    fun starsNeverDecreaseAsMoreLivesSurvive() {
        for (max in intArrayOf(5, 10, 20, 30, 50)) {
            for (lives in 1 until max) {
                val lower = starsForRemainingLives(lives, max)
                val higher = starsForRemainingLives(lives + 1, max)
                assertTrue(
                    "max=$max: $lives can -> $lower yildiz ama ${lives + 1} can -> $higher yildiz",
                    higher >= lower
                )
            }
        }
    }

    @Test
    fun aFlawlessRunAlwaysEarnsThreeStarsAtAnyMaxLifeCount() {
        for (max in intArrayOf(1, 5, 10, 20, 30, 50, 99)) {
            assertEquals(
                "hic can kaybetmeden bitirmek her zaman 3 yildiz olmali (max=$max)",
                3, starsForRemainingLives(max, max)
            )
        }
    }

    @Test
    fun theSameLifeFractionAlwaysEarnsTheSameStarsRegardlessOfMaxLives() {
        // Adalet kurali: %50 ile bitiren iki oyuncu ayni yildizi almali,
        // us canlari farkli olsa bile.
        val halfway = listOf(10 to 20, 15 to 30, 25 to 50, 5 to 10)
        val expected = starsForRemainingLives(10, 20)
        halfway.forEach { (lives, max) ->
            assertEquals(
                "$lives/$max = %50 ama farkli yildiz verdi",
                expected, starsForRemainingLives(lives, max)
            )
        }
    }

    @Test
    fun aZeroMaxLivesConfigurationCannotCrashOrDivideByZero() {
        // Motor `coerceAtLeast(1)` uyguluyor; bu davranis korunmali.
        assertEquals(1, starsForRemainingLives(0, 0))
        assertEquals(3, starsForRemainingLives(5, 0))
    }

    @Test
    fun losingASingleLifeNeverCostsThreeStarsAtTwentyBaseLives() {
        // 19/20 = %95 >= %90. Tek sizma 3 yildizi goturmemeli, yoksa yildiz
        // sistemi "kusursuz oyna ya da bosver" ikilemine coker.
        assertEquals(3, starsForRemainingLives(19, 20))
        assertEquals(3, starsForRemainingLives(18, 20))
        assertEquals("iki sizmadan sonra 3 yildiz kaybedilir", 2, starsForRemainingLives(17, 20))
    }
}
