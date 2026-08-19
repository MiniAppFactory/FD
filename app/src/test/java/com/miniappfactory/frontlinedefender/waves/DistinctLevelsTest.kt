package com.miniappfactory.frontlinedefender.waves

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HICBIR IKI BOLUM AYNI OLAMAZ.
 *
 * ---------------------------------------------------------------------------------
 * NEDEN BU TEST VAR — ayni hata UC KEZ olustu
 * ---------------------------------------------------------------------------------
 * Gec perde dalgalari (L23-55) elle yazilmiyor, `LATE_PLAN` tablosundan SAF bir
 * fonksiyonla URETILIYOR. Saf fonksiyonun dogal sonucu su: **ayni girdi ayni
 * bolumu verir.** Iki satir yanlislikla ayni degerleri tasiyorsa oyuncu 55
 * bolumluk kampanyada ayni bolumu iki kez oynar ve bunu hicbir sey yakalamaz —
 * ne derleyici, ne cozulebilirlik testi (ikisi de gecilebilir), ne SPI kilidi
 * (ikisi de bantta).
 *
 * Gecmis:
 *  1. L53 elle yakalandi ve duzeltildi (kd3 5 -> 3, arketip 'C' -> 'M').
 *  2. L50/L54 cifti KACTI — 2026-08-19'da FUN GATE'in ikinci kosumunda bulundu.
 *  3. Bu test yazildi.
 *
 * Kontrol dalga TABLOSU uzerinden yapilir, `LatePlan` satiri uzerinden DEGIL:
 * iki farkli satir da ayni tabloyu uretebilir (ornegin yalnizca dengeyi
 * etkileyen bir alanda ayrisiyorlarsa). Oyuncunun gordugu sey tablodur.
 */
class DistinctLevelsTest {

    /** Bir bolumun dalga tablosunun, oyuncunun GORDUGU haliyle parmak izi. */
    private fun fingerprintOf(levelId: Int): String =
        WaveDefinitions.wavesFor(levelId).joinToString("|") { wave ->
            wave.spawns.joinToString(",") { "${it.enemyType.name}:${it.delaySeconds}" }
        }

    @Test
    fun noTwoLevelsGenerateTheSameWaveTable() {
        val byFingerprint = mutableMapOf<String, MutableList<Int>>()
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            byFingerprint.getOrPut(fingerprintOf(level)) { mutableListOf() }.add(level)
        }

        val duplicates = byFingerprint.values.filter { it.size > 1 }
        assertTrue(
            "Ayni dalga tablosunu ureten bolumler var: " +
                duplicates.joinToString("; ") { it.joinToString(" == L") { l -> "L$l" } } +
                ". Gec perde bolumleri SAF bir fonksiyonla uretiliyor, yani ayni " +
                "`LatePlan` girdisi ayni bolumu verir. Ayrisan bir alan sec " +
                "(dalga sayisi, arketip, boss, nefes) ve gerekcesini yaz.",
            duplicates.isEmpty()
        )
    }

    @Test
    fun everyCampaignLevelActuallyHasWaves() {
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val waves = WaveDefinitions.wavesFor(level)
            assertTrue("L$level dalga tasimiyor", waves.isNotEmpty())
            assertTrue(
                "L$level icinde bos dalga var",
                waves.all { it.spawns.isNotEmpty() }
            )
        }
    }

    /**
     * Parmak izi yonteminin GERCEKTEN ayirt ettiginin kaniti.
     *
     * Bos bir test, "hicbir cift yok" derken kendisi kirilmis olabilir. Bu test
     * yontemin duyarli oldugunu gosterir: ayni bolum kendisiyle ayni parmak izini,
     * farkli iki bolum farkli parmak izini uretmeli.
     */
    @Test
    fun theFingerprintMethodCanActuallyTellLevelsApart() {
        assertEquals(fingerprintOf(1), fingerprintOf(1))
        assertTrue(
            "Parmak izi L1 ile L2'yi ayirt edemiyor — yontem bozuk",
            fingerprintOf(1) != fingerprintOf(2)
        )
    }
}
