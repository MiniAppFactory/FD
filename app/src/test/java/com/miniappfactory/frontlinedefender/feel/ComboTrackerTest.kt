package com.miniappfactory.frontlinedefender.feel

import com.miniappfactory.frontlinedefender.game.engine.ComboTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ZINCIR (kill-streak) SOZLESMESI — docs/FUN_AUDIT.md 4. madde.
 *
 * Denetimin tespiti: "18 dusmanlik dalgada 18 kez AYNI ses + AYNI '+4g' yazisi,
 * hicbir sey tirmanmiyor." Cozum bir zincir sayaci; bu test onun KURALLARINI
 * pinler.
 *
 * NEDEN GERCEK NESNE SURULEBILIYOR: `ComboTracker` bilincli olarak `GameEngine`
 * disinda, Android bagimliligi olmayan bir sinif. Motorun kendisi yapicisinda
 * `SaveManager(Context)` + `AudioManager(Context)` istedigi icin saf JUnit'te
 * ornek uretilemiyor (bkz. StarRatingTest'teki test-edilebilirlik borcu notu);
 * kurallar motora gomulseydi bu esikler HIC test edilemezdi.
 */
class ComboTrackerTest {

    private fun tracker() = ComboTracker()

    // ------------------------------------------------------------ temel sayim

    @Test
    fun freshTrackerHasNoCombo() {
        val c = tracker()
        assertEquals(0, c.count)
        assertEquals(0, c.tier)
        assertFalse(c.isActive)
    }

    @Test
    fun firstKillStartsChainAtOne() {
        val c = tracker()
        c.registerKill()
        assertEquals(1, c.count)
        assertTrue(c.isActive)
    }

    @Test
    fun killsInsideWindowAccumulate() {
        val c = tracker()
        repeat(5) {
            c.registerKill()
            c.age(ComboTracker.COMBO_WINDOW_SECONDS * 0.5f)
        }
        assertEquals(5, c.count)
    }

    /**
     * Pencere dolunca zincir KOPAR. Kopmasaydi bir bolum boyunca sayac
     * artmaya devam eder ve "tirmanma" anlamini kaybederdi: 40. oldurmede
     * hâlâ ayni maksimum kademede kalinirdi.
     */
    @Test
    fun chainBreaksAfterWindowElapses() {
        val c = tracker()
        c.registerKill()
        c.registerKill()
        c.age(ComboTracker.COMBO_WINDOW_SECONDS + 0.01f)
        assertFalse(c.isActive)
        assertEquals(0, c.count)
        assertEquals(0, c.tier)

        c.registerKill()
        assertEquals("Kopan zincirden sonra yeniden 1'den baslamali", 1, c.count)
    }

    @Test
    fun eachKillRefreshesTheWindow() {
        val c = tracker()
        c.registerKill()
        c.age(ComboTracker.COMBO_WINDOW_SECONDS * 0.9f)
        c.registerKill()
        // Pencere tazelendi: ilk oldurmeden bu yana toplam sure penceredsen
        // uzun olsa bile zincir yasamali.
        c.age(ComboTracker.COMBO_WINDOW_SECONDS * 0.9f)
        assertTrue("Her oldurme pencereyi tazelemeli", c.isActive)
        assertEquals(2, c.count)
    }

    // ------------------------------------------------------------- kademeler

    @Test
    fun tierThresholdsAreStrictlyIncreasing() {
        val t = ComboTracker.COMBO_TIER_THRESHOLDS
        assertTrue("En az iki kademe olmali ki tirmanma hissedilsin", t.size >= 2)
        for (i in 1 until t.size) {
            assertTrue(
                "Esikler artan olmali: ${t[i - 1]} -> ${t[i]}",
                t[i] > t[i - 1]
            )
        }
        assertTrue("Ilk esik 1 olamaz; her oldurme kademe atlarsa tirmanma yok", t[0] >= 2)
    }

    @Test
    fun tierClimbsWithKillCount() {
        val c = tracker()
        var lastTier = 0
        for (kill in 1..ComboTracker.COMBO_TIER_THRESHOLDS.last() + 4) {
            c.registerKill()
            assertTrue(
                "Kademe geri gitmemeli (oldurme $kill)",
                c.tier >= lastTier
            )
            lastTier = c.tier
        }
        assertEquals(
            "Son esik gecilince en yuksek kademeye ulasilmali",
            ComboTracker.MAX_TIER,
            c.tier
        )
    }

    /**
     * KADEME ATLAMA TEK SEFERLIKTIR. Cagiran taraf (motor) donus degeri
     * sifirdan farkliysa patlama + ses + hit stop uretiyor. Her oldurmede
     * sifirdan farkli donseydi 18 dusmanlik bir dalgada 18 patlama cikardi —
     * denetimin sikayet ettigi bogulmanin aynisi, sadece daha gurultulusu.
     */
    @Test
    fun tierUpIsReportedExactlyOncePerTier() {
        val c = tracker()
        val reported = mutableListOf<Int>()
        repeat(ComboTracker.COMBO_TIER_THRESHOLDS.last() + 6) {
            val climbed = c.registerKill()
            if (climbed > 0) reported.add(climbed)
        }
        assertEquals(
            "Her kademe TAM BIR KEZ bildirilmeli",
            (1..ComboTracker.MAX_TIER).toList(),
            reported
        )
    }

    @Test
    fun tierUpNotReportedWhenChainMerelyContinues() {
        val c = tracker()
        // Ilk esige kadar hicbir kademe atlanmamali.
        for (i in 1 until ComboTracker.COMBO_TIER_THRESHOLDS[0]) {
            assertEquals("Oldurme $i kademe atlatmamali", 0, c.registerKill())
        }
        assertTrue("Ilk esikte kademe atlanmali", c.registerKill() > 0)
    }

    // ------------------------------------------------------ etiket ve sifirlama

    @Test
    fun labelThresholdIsNotShownForSingleKills() {
        assertTrue(
            "Tek tuk oldurmede ekranda 'x1' cikmamali",
            ComboTracker.COMBO_LABEL_MIN_KILLS >= 2
        )
        assertTrue(
            "Etiket esigi ilk kademe esigini gecmemeli; yoksa kademe atlar " +
                "ama sayac hâlâ gizli kalir",
            ComboTracker.COMBO_LABEL_MIN_KILLS <= ComboTracker.COMBO_TIER_THRESHOLDS[0]
        )
    }

    /** Us sizintisi / dalga sonu zinciri koparir. */
    @Test
    fun resetBreaksChainButKeepsPeak() {
        val c = tracker()
        repeat(ComboTracker.COMBO_TIER_THRESHOLDS[0]) { c.registerKill() }
        val peak = c.peakTier
        assertTrue(peak > 0)
        c.reset()
        assertEquals(0, c.count)
        assertEquals(0, c.tier)
        assertEquals("peakTier bolum ozeti icin korunmali", peak, c.peakTier)
        c.resetAll()
        assertEquals(0, c.peakTier)
    }

    /**
     * ZAMAN TABANI: [ComboTracker.age] SIMULASYON dt'si ile beslenir, gercek
     * zamanla degil. Bu test yalnizca pencerenin dt'ye dogrusal bagli
     * oldugunu pinler — 2x oyun hizinda motor dt'yi zaten iki katiyla
     * besledigi icin zincir davranisi oyun hizindan BAGIMSIZ kalir.
     */
    @Test
    fun windowConsumesExactlyTheSuppliedDelta() {
        val c = tracker()
        c.registerKill()
        val half = ComboTracker.COMBO_WINDOW_SECONDS / 2f
        c.age(half)
        assertTrue(c.isActive)
        assertEquals(half, c.timeRemainingSeconds, 1e-4f)
        c.age(half)
        assertFalse(c.isActive)
    }

    @Test
    fun agingWithoutChainIsNoOp() {
        val c = tracker()
        c.age(10f)
        assertEquals(0, c.count)
        assertFalse(c.isActive)
    }
}
