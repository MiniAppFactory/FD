package com.miniappfactory.frontlinedefender.waves

import com.miniappfactory.frontlinedefender.game.economy.SupplyBudgetModel
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.game.model.WaveMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * KAMPANYA OLCEGI — 22 -> 55 bolumun tek sayfalik regresyon kilidi
 * ===========================================================================
 *
 * Bu dosyanin isi tek tek kurallari denetlemek DEGIL (onlar
 * [CampaignShapeTest], `CampaignTableTest` ve `SupplyBudgetTest` icinde);
 * burasi **kampanyanin buyuklugunu** pinler. Bir dalga silinir, bir bolum
 * elden gecirilir ya da uretici parametresi kayarsa toplamlar degisir ve bu
 * test kirilarak "kampanyanin sekli degisti" der.
 *
 * Ayrica CAMPAIGN_55.md 12.2'nin **V1/V2 varsayimlarini** olcer: doküman
 * `ρ` (AEHP basina Tedarik) ve govde basina AEHP icin tasarim degerleri
 * kullandi ve "dalgalar yazildiktan sonra yeniden olculur" dedi. Olculen
 * degerler burada duruyor.
 */
class CampaignScaleTest {

    private fun bodies(level: Int) = WaveDefinitions.wavesFor(level).sumOf { it.spawns.size }

    private fun designMinutes(level: Int): Double {
        val waves = WaveDefinitions.wavesFor(level)
        val window = waves.sumOf { WaveMetrics.spawnWindowSeconds(it).toDouble() }
        return (window + 20.0 * waves.size + 10.0 + 5.0 * (waves.size - 1)) / 60.0
    }

    @Test
    fun theCampaignIsFiftyFiveLevelsAndThreeHundredFiftyNineWaves() {
        assertEquals(55, GameConfig.CAMPAIGN_LEVEL_COUNT)
        // 483 -> 359. L1..L22 elle kalibre edilmis dalgalarin KOMPOZISYONU
        // korundu; degisen sey OTURUM SEKLI (259 -> 135 dalga, 5-7 bant).
        assertEquals(359, (1..55).sumOf { WaveDefinitions.waveCount(it) })
        assertEquals("L1..L22 dalga sayisi", 135, (1..22).sumOf { WaveDefinitions.waveCount(it) })
        assertEquals("L23..L55 uretilen dalga sayisi", 224, (23..55).sumOf { WaveDefinitions.waveCount(it) })
    }

    /**
     * TOPLAM SURE — tek ritim sonrasi.
     *
     * Once: 55 bolum 378 dk, en uzun bolum **14,9 dk** (L22 bolunmemisti),
     * ortalama 6,9 dk. Simdi: 280 dk, en uzun **6,58 dk** (L44, perde finali),
     * ortalama 5,1 dk. Yani kampanya %26 kisaldi ama asil kazanc ORADA DEGIL:
     * bir yenilginin bedeli 14,9 dakikadan 6,6 dakikaya indi.
     */
    @Test
    fun everyLevelFitsTheSessionBandAndTheCampaignRunsUnderFiveHours() {
        val total = (1..55).sumOf { designMinutes(it) }
        assertEquals("toplam kampanya suresi (dk)", 280.5, total, 8.0)

        val longest = (1..55).maxOf { designMinutes(it) }
        assertTrue(
            "en uzun bolum %.2f dk — 7,0 tavani (K-1, perde finali)".format(longest),
            longest <= 7.0,
        )
        (1..55).filterNot { it % 11 == 0 }.forEach { level ->
            assertTrue(
                "bolum $level %.2f dk — finalsiz tavan 6,5 (K-1)".format(designMinutes(level)),
                designMinutes(level) <= 6.5,
            )
        }
        assertEquals(
            "L23..L55 ortalama suresi (dk)",
            5.6, (23..55).map { designMinutes(it) }.average(), 0.3,
        )
    }

    /**
     * V1/V2 — OLCULEN `ρ` ve govde basina AEHP.
     *
     * CAMPAIGN_55.md 8.1 `ρ` icin Act III/IV/V'te 0,036 / 0,034 / 0,032
     * VARSAYDI ve bu varsayimi 12.2'de "yazildiktan sonra olculecek" diye
     * isaretledi. Olcum sonucu asagida.
     *
     * Sapmanin sebebi **K-4 tavani**: dalga basina 56 govde siniri, geliri
     * daha AZ govdeye sigdirmayi zorunlu kiliyor, yani karisim agirlasiyor ve
     * ayni Tedarik daha cok AEHP satin aliyor. Doküman'in kendi 9. tablosu bu
     * iki kisiti ayni anda saglayamiyor (govde tavani + hedef AEHP); zincirin
     * ucu olan **butce** (SPI 2,00) korundu, AEHP serbest birakildi.
     *
     * BU BIR ACIK RISKTIR ve `CampaignSolvabilityTest`in L1..L55'e
     * genisletilmesiyle kapanmalidir (dokümanin V4 maddesi: "masa basi hesabi
     * YETMEZ").
     */
    @Test
    fun theMeasuredSupplyPerThreatRatioIsPinnedPerAct() {
        val measured = (3..5).associateWith { act ->
            val levels = 11 * (act - 1) + 1..11 * act
            val aehp = levels.sumOf { WaveMetrics.levelAehp(WaveDefinitions.wavesFor(it)).toDouble() }
            val supply = levels.sumOf { SupplyBudgetModel.waveKillSupply(it) }
            supply / aehp / 1.30
        }
        // Doküman varsayimi 0,036 / 0,034 / 0,032. Olculen degerler once daha
        // DUSUKtu (0,0300 / 0,0273 / 0,0254); baslangic sermayesi kadrodan
        // turetilince hedef oldurme geliri dustu, dalgalar hafifledi ve `rho`
        // dokumanin varsayimina YAKLASTI.
        assertEquals("Act III rho", 0.0333, measured.getValue(3), 0.0020)
        assertEquals("Act IV rho", 0.0300, measured.getValue(4), 0.0020)
        assertEquals("Act V rho", 0.0285, measured.getValue(5), 0.0020)
        // Yon dogru olmali: perde ilerledikce ayni Tedarik daha AZ satin alir.
        assertTrue("rho perde perde dusmeli", measured.getValue(3) > measured.getValue(4))
        assertTrue("rho perde perde dusmeli", measured.getValue(4) > measured.getValue(5))
    }

    /**
     * TEHDIT / TEDARIK ORANI — "ayni parayla ne kadar can oldurmem gerekiyor".
     *
     * Zorluk rampasinin en dogru tek sayisi bu: SPI 2,00 sabit oldugu icin
     * oyuncunun ELINDEKI para bolum bolum tanimli, degisen sey ondan beklenen
     * IS. L23'te 33, L55'te 50 — yani kampanya boyunca %52 daha verimli oynamak
     * gerekiyor. Karsiliginda oyuncunun eline gecen sey: daha buyuk tahta
     * (Act II'de 7-11 acik pad, Act V'te 8-15), kademe 3 ve meta yukseltmeler.
     *
     * TAVAN: 55 hicbir zaman 22'nin 1,5 katini gecmemeli. Gecerse rampa
     * "zorlasiyor"dan "gecilemez"e doner ve bunu kimse fark etmez.
     */
    @Test
    fun theThreatPerSupplyRampIsBoundedAcrossTheCampaign() {
        fun ratio(level: Int): Double {
            val spec = GameConfig.levelSpec(level)
            val effective = WaveMetrics.levelAehp(WaveDefinitions.wavesFor(level)) *
                GameConfig.actHpMultiplier(spec.act)
            return effective.toDouble() / SupplyBudgetModel.waveKillSupply(level)
        }
        assertEquals("L23 tehdit/Tedarik", 32.4, ratio(23), 1.5)
        // 48,7 -> 52,1 (2026-08-20). Arketip karisimi duzeltilince gec perde
        // dalgalarinin tip dagilimi degisti; Kalkanli Er payi dusup yerine
        // odulu/HP orani farkli tipler gelince oran YUKARI kaydi.
        //
        // GERCEK KISIT KIRILMADI ve asil onemli olan o: asagidaki
        // "L55 <= L22 x 1,5" bagi hala saglaniyor (52,1 <= 57,2). Buradaki sayi
        // bir tavan degil, bugunku olcumun KAYDI.
        assertEquals("L55 tehdit/Tedarik", 52.1, ratio(55), 1.5)
        assertEquals("L22 tehdit/Tedarik", 38.1, ratio(22), 1.5)

        assertTrue(
            "L55 orani (%.1f) L22'nin (%.1f) 1,5 katini asti — rampa gecilemezlige donuyor"
                .format(ratio(55), ratio(22)),
            ratio(55) <= ratio(22) * 1.5,
        )
        // Perde gecisi bir NEFES: L23 orani L22'ninkinden dusuk olmali.
        assertTrue("L23 orani L22'den dusuk olmali (perde gecisi nefesi)", ratio(23) < ratio(22))
    }

    /** Perde toplamlari — icerik hacminin kaydi. */
    @Test
    fun actTotalsArePinned() {
        val waves = (1..5).map { act -> (11 * (act - 1) + 1..11 * act).sumOf { WaveDefinitions.waveCount(it) } }
        // Once [96, 163, 73, 75, 76] — Act I/II'nin sismisligi tek bakista
        // goruluyordu (Act II tek basina Act V'in iki katindan fazla dalga).
        // Tek ritimden sonra bes perde birbirine yakin ve MONOTON artiyor.
        assertEquals(listOf(65, 70, 73, 75, 76), waves)
        assertTrue(
            "perde dalga sayilari monoton artmali: $waves",
            waves.zipWithNext().all { (a, b) -> b >= a },
        )

        val bodies = (1..5).map { act -> (11 * (act - 1) + 1..11 * act).sumOf { bodies(it) } }
        // Govde sayisi da perde perde artar; K-4 tavani (56/dalga) hepsinde gecerli.
        assertTrue(
            "perde govde sayilari monoton artmali: $bodies",
            bodies.zipWithNext().all { (a, b) -> b >= a },
        )
        listOf(2, 3, 4).forEach { i ->
            assertTrue(
                "Act ${i + 1} govde sayisi ${bodies[i]} — 3.000..4.200 bandi disinda",
                bodies[i] in 3_000..4_200,
            )
        }
    }
}
