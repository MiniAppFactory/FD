package com.miniappfactory.frontlinedefender.progression

import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.MetaUpgrades
import com.miniappfactory.frontlinedefender.game.economy.UpgradeLine
import com.miniappfactory.frontlinedefender.game.economy.healthNeededForStars
import com.miniappfactory.frontlinedefender.game.economy.isPerfectDefense
import com.miniappfactory.frontlinedefender.game.economy.starHealthFromLeaks
import com.miniappfactory.frontlinedefender.game.economy.starsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 13 — TAHKIMAT YILDIZ EXPLOIT'I.
 *
 * ## Kapatilan hata
 * Yildiz `kalanCan / tabanCan` ile hesaplaniyordu. Pay meta yukseltmeyi
 * ICERIYORDU ("Tahkimat" rank 5: maks can 20 -> 30), payda ise taban 20'de
 * kaliyordu. Sonuc: Tahkimat'li oyuncu **12 dusman sizdirip 3 yildiz**,
 * **10 sizdirip Kusursuz Savunma madalyasi (+80 coin)** aliyordu. Meta
 * yukseltme, yildiz ve coin satin alan bir hileye donusmustu.
 *
 * ## Kilitlenen kural
 * Yildiz **yalnizca SIZINTI SAYISINA** bakar ([starHealthFromLeaks]). Tahkimat
 * ne yildiz kazandirir ne de zorlastirir; aldigi sey dayaniklilik marjidir.
 */
class MetaNeutralStarTest {

    /** Kampanyanin standart taban us cani. */
    private val base = EconomyConfig.BASE_MAX_HEALTH

    /** Tahkimat agacinin en ust ranki — exploit'in en buyuk oldugu nokta. */
    private val maxMetaHealth: Int =
        MetaUpgrades(fortification = UpgradeLine.FORTIFICATION.maxRank).maxBaseHealth

    @Test
    fun fortificationActuallyRaisesMaxHealth() {
        assertTrue(
            "test anlamli olsun diye Tahkimat gercekten can eklemeli",
            maxMetaHealth > base,
        )
    }

    @Test
    fun fortificationCanNoLongerBuyStars() {
        val leaks = 12
        // ESKI (HATALI) HESAP: pay meta DAHIL kalan can, payda taban can.
        val oldStars = starsFor(maxMetaHealth - leaks, base)
        // YENI KURAL: yalnizca sizinti sayisi.
        val newStars = starsFor(starHealthFromLeaks(base, leaks), base)

        assertTrue(
            "test degerini kaybetmis: eski hesap zaten dogru sonucu veriyordu",
            oldStars > newStars,
        )
        assertTrue("12 sizinti 3 yildiz OLAMAZ", newStars < 3)
        assertEquals(
            "meta yukseltmesi olmayan oyuncunun davranisi DEGISMEMELI",
            starsFor(base - leaks, base),
            newStars,
        )
    }

    @Test
    fun starsDependOnLeaksOnlyNotOnMetaHealth() {
        // Ayni sizinti sayisi -> her Tahkimat rankinda AYNI yildiz.
        // Ne odul (eski hata) ne de ceza (paydayi meta-dahil yapan alternatif).
        for (rank in 0..UpgradeLine.FORTIFICATION.maxRank) {
            val startingLives = MetaUpgrades(fortification = rank).maxBaseHealth
            for (leaks in 0 until base) {
                if (startingLives - leaks <= 0) continue
                assertEquals(
                    "rank=$rank sizinti=$leaks",
                    starsFor(base - leaks, base),
                    starsFor(starHealthFromLeaks(base, leaks), base),
                )
            }
        }
    }

    @Test
    fun perfectDefenseRequiresZeroLeaksEvenWithMetaHealth() {
        assertTrue(
            "hic sizinti yoksa madalya hak edilir",
            isPerfectDefense(starHealthFromLeaks(base, 0), base),
        )
        // ESKI HATA: Tahkimat'li oyuncu 10 sizintiyla taban cana esit kalanla
        // bitiriyor, isPerfectDefense(20, 20) true donuyor ve +80 coin aliyordu.
        assertTrue(
            "test degerini kaybetmis: eski hesap zaten madalyayi vermiyordu",
            isPerfectDefense(maxMetaHealth - (maxMetaHealth - base), base),
        )
        assertFalse(
            "10 sizinti Kusursuz Savunma DEGILDIR",
            isPerfectDefense(starHealthFromLeaks(base, 10), base),
        )
        assertFalse(
            "tek sizinti bile madalyayi bozar",
            isPerfectDefense(starHealthFromLeaks(base, 1), base),
        )
    }

    @Test
    fun aWinIsNeverWorthZeroStars() {
        // Meta sayesinde ayakta kalan oyuncu tabandan FAZLA sizinti verebilir;
        // bu bir zaferdir ve 0 yildiz sayilamaz (resolveLevelClear stars > 0 ister).
        val extremeLeaks = maxMetaHealth - 1
        val health = starHealthFromLeaks(base, extremeLeaks)
        assertTrue("en az 1 can", health >= 1)
        assertEquals("zafer = en az 1 yildiz", 1, starsFor(health, base))
    }

    // =================================================================================
    // Zafer ekranindaki "N sizinti daha az = 3 yildiz" ipucu
    // =================================================================================

    @Test
    fun starHintTargetMatchesTheRealStarRule() {
        val needed = healthNeededForStars(3, base)
        assertEquals("hedef gercekten 3 yildiz vermeli", 3, starsFor(needed, base))
        assertTrue(
            "bir eksigi 3 yildiz VERMEMELI (ipucu bir sizinti sasmamali)",
            needed <= 1 || starsFor(needed - 1, base) < 3,
        )
    }

    @Test
    fun starHintTargetIsConsistentForEveryStarLevel() {
        for (target in 1..3) {
            val needed = healthNeededForStars(target, base)
            assertTrue("hedef 1..$base araliginda", needed in 1..base)
            assertTrue("hedef $target yildiz vermeli", starsFor(needed, base) >= target)
        }
        assertEquals("0 yildiz icin can gerekmez", 0, healthNeededForStars(0, base))
    }
}
