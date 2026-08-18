package com.miniappfactory.frontlinedefender.waves

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.game.model.WaveMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * OTURUM SEKLI — docs/CAMPAIGN_55.md 7.4 (K-1 .. K-4)
 * ===========================================================================
 *
 * NEDEN VAR
 * ---------
 * Iki bagimsiz olcum ayni yeri gosterdi:
 *
 *  · **Sure.** L22 bugun **16,1 dakika** ve yenilgi = bastan basla. 17. dalgada
 *    kaybetmek 8-16 dakikanin cope gitmesi demek. Mobilde bu, oyuncunun
 *    "bir el daha" demesini engelleyen tek en buyuk sey.
 *  · **Ekonomi.** Ayni bolumde butce tahtanin kapasitesini asiyor: oyuncu butun
 *    pad'leri doldurup hepsini kademe 3'e cikarsa bile parayi bitiremiyor
 *    (SPI 5,37). Bir tower defense'in cekirdek karari PARA karariydi ve uzun
 *    bolumlerde oluyordu.
 *
 * Karar (K2): bolumu UZATMA, BOL. 5-7 dalga, 3-6,5 dakika. Bu ayni zamanda
 * ayri bir "dalga checkpoint" ozelligini de GEREKSIZ kilar — 6 dalgalik bir
 * bolumde **bolumun kendisi checkpoint'tir**.
 *
 * KAPSAM — ARTIK **55 BOLUMUN TAMAMI**
 * ------------------------------------
 * Bu kurallar bir zamanlar yalnizca L23..L55 icin baglayiciydi ve L1..L22'nin
 * ihlalleri `theHandwrittenActsStillViolateTheNewShapeRules` icinde
 * "isimlendirilerek" pinlenmisti (15 bolum 6,5 dk'yi asiyor, en kalabalik
 * dalga 91 govde). O pin KALDIRILDI cunku ihlalin kendisi kaldirildi:
 * elle kalibre edilmis dalgalarin KOMPOZISYONU korunarak bolum sekli tek
 * ritme tasindi (`WaveDefinitions.reshapeHandwritten`).
 */
class CampaignShapeTest {

    /** Sekil kurallari 55 bolumun TAMAMI icin baglayici. */
    private val allLevels = 1..55

    /** Uretici-ozel kontroller (butce zinciri) yalnizca uretilmis bolumlerde. */
    private val generated = 23..55

    private companion object {
        /**
         * Yeniden sekillendirme ONCESI kampanyanin en uzun bolumu (L22, 18
         * dalga). Tarihsel OLCUM; eski tablo artik kodda olmadigi icin sabit.
         */
        const val PRE_RESHAPE_WORST_MINUTES = 14.9
    }

    // =======================================================================
    // Sure modeli
    // =======================================================================

    /**
     * CAMPAIGN_55.md 1.3'un DOGRULANMIS sure modeli:
     *   `sure = Σ(spawn penceresi) + 20 sn/dalga yurume + hazirlik`
     * Hazirlik: W1 10 sn, sonrasi 5 sn (2.2 — bugun her dalga 10 sn, o degisiklik
     * motor ajaninin isi ve bu gorevin kapsaminda degil).
     *
     * Model bagimsiz olarak dogrulandi: L1 3,9 / L8 7,2 / L15 10,8 / L22 16,1 dk
     * cikti; `FUN_AUDIT.md` 5'in cihaz olcumleri 3,8 / 7,2 / 10,7 / 16,2 idi.
     */
    private fun designMinutes(level: Int): Double {
        val waves = WaveDefinitions.wavesFor(level)
        val window = waves.sumOf { WaveMetrics.spawnWindowSeconds(it).toDouble() }
        val walk = 20.0 * waves.size
        val prep = 10.0 + 5.0 * (waves.size - 1)
        return (window + walk + prep) / 60.0
    }

    /** Bugunku motorun hazirlik suresiyle (her dalga 10 sn) ayni model. */
    private fun shippedMinutes(level: Int): Double {
        val waves = WaveDefinitions.wavesFor(level)
        val window = waves.sumOf { WaveMetrics.spawnWindowSeconds(it).toDouble() }
        return (window + 20.0 * waves.size +
            GameConfig.PREPARATION_TIME_SECONDS.toDouble() * waves.size) / 60.0
    }

    /**
     * K-1 — bolum suresi 1x hizda <= 6,5 dk; perde finalleri <= 7,0 dk.
     *
     * 3 dk mobilde bir "bir el daha" birimi; 6,5 dk ise yenilgide kaybedilen
     * surenin hâlâ katlanilabilir oldugu ust sinir.
     */
    @Test
    fun everyLevelFitsTheSessionLengthBand() {
        allLevels.forEach { level ->
            val minutes = designMinutes(level)
            val isActFinale = level % 11 == 0
            val cap = if (isActFinale) 7.0 else 6.5
            assertTrue(
                "L$level %.2f dk — K-1 tavani %.1f dk".format(minutes, cap),
                minutes <= cap,
            )
            // TABAN — 3 dk, ama OGRETME DILIMI (L1..L4) haric. O dort bolumun
            // isi ekonomi arki kurmak degil MEKANIK TANITMAK: L1 tek kule,
            // L2 yukseltme, L3 Heavy Cannon, L4 iki kule karari. Onlari 3
            // dakikaya sismek dersi seyreltir. Olculen taban 2,95 dk (L1).
            val floor = if (level <= 4) 2.5 else 3.0
            assertTrue(
                "L$level %.2f dk — %.1f dk'nin altinda bir bolumde ekonomi arki kurulamaz"
                    .format(minutes, floor),
                minutes >= floor,
            )
        }
    }

    /**
     * Hazirlik suresi 5 sn'ye INMESE BILE (motor degisikligi bekliyor) hicbir
     * yeni bolum 7,5 dakikayi gecmemeli. Yani K-1 baska bir ajanin isine
     * BAGIMLI degil; bekleyen degisiklik yalnizca marji genisletir.
     */
    @Test
    fun theBandHoldsEvenWithTodaysTenSecondPreparationPhase() {
        allLevels.forEach { level ->
            assertTrue(
                "L$level bugunku hazirlik suresiyle %.2f dk".format(shippedMinutes(level)),
                shippedMinutes(level) <= 7.5,
            )
        }
    }

    /**
     * OYNANIS FARKI TEK SAYIYLA: **yenilgi maliyeti**.
     *
     * Yeniden sekillendirme oncesi kampanyanin en uzun bolumu L22 = **14,9 dk**
     * idi (18 dalga) ve yenilgi = bastan basla. Yani bir hata 15 dakikayi cope
     * atiyordu. Tek ritimden sonra HICBIR bolum bunun yarisini gecmiyor.
     *
     * [PRE_RESHAPE_WORST_MINUTES] tarihsel bir OLCUMDUR, canli bir hesap degil:
     * eski tablo artik kodda yok, bu yuzden testin karsilastirdigi taban sabit.
     */
    @Test
    fun theWorstCaseLossCostsLessThanHalfOfTheOldCampaign() {
        val worstNow = allLevels.maxOf { designMinutes(it) }
        assertTrue(
            "yeniden sekillendirme oncesi en uzun bolum %.1f dk, bugun %.1f dk — " +
                "yenilgi maliyeti yarilanmali"
                .format(PRE_RESHAPE_WORST_MINUTES, worstNow),
            worstNow <= PRE_RESHAPE_WORST_MINUTES / 2.0,
        )
    }

    // =======================================================================
    // K-2 / K-3 / K-4
    // =======================================================================

    /** K-2 — bolum basina 5-7 dalga (perde finali 8'e kadar). */
    @Test
    fun everyLevelHasFiveToSevenWaves() {
        allLevels.forEach { level ->
            val n = WaveDefinitions.waveCount(level)
            val cap = if (level % 11 == 0) 8 else 7
            assertTrue("L$level $n dalga — K-2 bandi 5..$cap", n in 5..cap)
        }
    }

    /**
     * K-4 — dalga basina <= 56 GOVDE (boss'lar dahil).
     *
     * OLCUM: bugun L20/L21/L22 tek dalgada **88 / 89 / 91** govde yolluyor.
     * `LEVEL_DESIGN.md` I.4.2'nin endless kisiti ekranda en fazla 60 dusman
     * diyor; kampanyada boyle bir tavan YOKTU ve gec bolumler onu tek dalgada
     * asiyordu. Bu hem kare suresi hem OKUNABILIRLIK riski: oyuncu 90 hedefin
     * icinde hangi tehdidin once geldigini goremezse "hedef secimi" diye bir
     * karar kalmaz.
     */
    @Test
    fun noWaveExceedsTheBodyCap() {
        allLevels.forEach { level ->
            WaveDefinitions.wavesFor(level).forEach { wave ->
                assertTrue(
                    "L$level W${wave.waveIndex}: ${wave.spawns.size} govde — K-4 tavani " +
                        "${WaveDefinitions.MAX_BODIES_PER_WAVE}",
                    wave.spawns.size <= WaveDefinitions.MAX_BODIES_PER_WAVE,
                )
            }
        }
        assertEquals(56, WaveDefinitions.MAX_BODIES_PER_WAVE)
        // Tavan gercekten BAGLAYICI olmali: hicbir dalga onu asmiyor ama en az
        // biri ona dayaniyor, yoksa tavan bir sey yapmiyor demektir.
        val maxSeen = allLevels.flatMap { WaveDefinitions.wavesFor(it) }.maxOf { it.spawns.size }
        assertEquals("kampanyanin en kalabalik dalgasi", 56, maxSeen)
    }

    /**
     * K-3 — acilis dalgasi, bolumun en agir dalgasinin en fazla %45'i;
     * `tank = 0`, `boss = 0`.
     *
     * AS-1'in mutlak 2.900 AEHP tavaninin ORANSAL hâli (CAMPAIGN_55.md 2.3):
     * bolum agirligiyla olceklenir. Oyuncunun ilk dalgasi hâlâ "kadromu
     * kurdugum" dalgadir; oraya tank koymak ogretilmis bir karari kumara cevirir.
     */
    @Test
    fun theOpeningWaveIsLightAndCarriesNoHeavyArmour() {
        allLevels.forEach { level ->
            val waves = WaveDefinitions.wavesFor(level)
            val opening = waves.first()
            val ratio = WaveMetrics.waveAehp(opening) / WaveMetrics.peakWaveAehp(waves)
            assertTrue(
                "L$level acilis dalgasi tepe dalganin %%${"%.0f".format(ratio * 100)}'i — K-3 tavani %45",
                ratio <= 0.45f,
            )
            assertEquals(
                "L$level acilis dalgasinda TANK var",
                0, opening.spawns.count { it.enemyType == EnemyType.TANK },
            )
            assertEquals(
                "L$level acilis dalgasinda BOSS var",
                0, opening.spawns.count { it.enemyType == EnemyType.COMMAND_TANK },
            )
        }
    }

    // =======================================================================
    // Ekonomi zinciri kapali mi
    // =======================================================================

    /**
     * URETIM KURALININ KENDISI: dalga tablosu, butcenin ISTEDIGI geliri uretmeli.
     *
     * CAMPAIGN_55.md 8.1 SPI bandini bir kontrol degil bir URETIM KURALI yapiyor
     * ve bu test zincirin iki ucunun birbirine bagli kaldigini kanitliyor. Bir
     * kurus sapma, kompozisyon ureticisinin motorun odul aritmetiginden
     * ayristigi anlamina gelir.
     */
    @Test
    fun everyGeneratedLevelDeliversExactlyItsBudgetedKillIncome() {
        generated.forEach { level ->
            val target = WaveDefinitions.targetKillSupply(level)
            val actual = WaveDefinitions.wavesFor(level).sumOf { wave ->
                wave.spawns.sumOf { spawn ->
                    val nominal = GameConfig.ENEMY_SPECS.getValue(spawn.enemyType).rewardGold
                    val mul = GameConfig.actRewardMultiplier(GameConfig.levelSpec(level).act)
                    (nominal * mul).toInt().coerceAtLeast(1)
                }
            }
            assertEquals("L$level oldurme geliri hedeften sapti", target, actual)
        }
    }

}
