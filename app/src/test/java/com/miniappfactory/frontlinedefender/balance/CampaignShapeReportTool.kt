package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.economy.SupplyBudgetModel
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.game.model.WaveMetrics
import org.junit.Test

/**
 * OLCUM ARACI — hicbir sey iddia ETMEZ, yalnizca 55 bolumun sekil/ekonomi/
 * cozulebilirlik tablosunu basar. Yeniden dengeleme sirasinda "once/sonra"
 * kaniti buradan okunur.
 *
 * Cikti `app/build/test-results` altindaki XML raporda `<system-out>` icinde
 * durur. Kapi degil ARAC oldugu icin daima yesildir.
 */
class CampaignShapeReportTool {

    @Test
    fun printCampaignShapeTable() {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== 55 BOLUM SEKIL + EKONOMI + COZULEBILIRLIK ===")
        sb.appendLine(
            "L   act map dlg govde maxG spawn  sure   aehp    tepe  bask  butce kadro  SPI   " +
                "gecti can  yild sizinti kadroDetay"
        )
        var totalWaves = 0
        var totalSeconds = 0.0
        var totalBodies = 0
        val fails = ArrayList<Int>()

        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val spec = GameConfig.levelSpec(level)
            val waves = WaveDefinitions.wavesFor(level)
            val bodies = waves.sumOf { it.spawns.size }
            val maxBodies = waves.maxOf { it.spawns.size }
            val spawnSeconds = waves.sumOf { WaveMetrics.spawnWindowSeconds(it).toDouble() }
            val aehp = WaveMetrics.levelAehp(waves) * GameConfig.actHpMultiplier(spec.act)
            val peak = WaveMetrics.peakWaveAehp(waves) * GameConfig.actHpMultiplier(spec.act)
            val pressure = WaveMetrics.peakPressureRatio(waves) * GameConfig.actHpMultiplier(spec.act)
            val budget = SupplyBudgetModel.supplyBudget(level)
            val loadout = SupplyBudgetModel.designedLoadoutCost(level)
            val spi = SupplyBudgetModel.supplyPressureIndex(level)
            val outcome = CampaignSimulator.bestOutcome(level)
            if (!outcome.cleared) fails.add(level)

            totalWaves += waves.size
            totalBodies += bodies
            totalSeconds += outcome.elapsedSeconds.toDouble()

            sb.appendLine(
                ("%-3d %-3d %-3d %-3d %-5d %-4d %-6.0f %-6s %-7.0f %-5.0f %-5.2f %-5d %-6d %-5.2f " +
                    "%-5s %-5s %-4d %-7d %s").format(
                    level, spec.act, spec.mapId, waves.size, bodies, maxBodies,
                    spawnSeconds, "%.1fdk".format(outcome.elapsedSeconds / 60f),
                    aehp, peak, pressure, budget, loadout, spi,
                    if (outcome.cleared) "EVET" else "HAYIR",
                    "${outcome.livesLeft}/${outcome.maxLives}",
                    outcome.stars, outcome.leaked, outcome.roster
                )
            )
        }
        sb.appendLine(
            "--- toplam dalga $totalWaves · toplam govde $totalBodies · " +
                "toplam sure %.0f dk · gecilemeyen %s".format(totalSeconds / 60.0, fails)
        )
        println(sb)
    }

    /** Testlerin pinlemesi gereken turetilmis sayilar — tek yerde, tek koşuda. */
    @Test
    fun printDerivedConstantsForTestPins() {
        val sb = StringBuilder()
        fun designMinutes(level: Int): Double {
            val waves = WaveDefinitions.wavesFor(level)
            val spawn = waves.sumOf { WaveMetrics.spawnWindowSeconds(it).toDouble() }
            val n = waves.size
            return (spawn + 20.0 * n + 10.0 + 5.0 * (n - 1)) / 60.0
        }
        sb.appendLine()
        sb.appendLine("=== TEST PIN DEGERLERI ===")
        sb.appendLine("toplam dalga = ${(1..55).sumOf { WaveDefinitions.waveCount(it) }}")
        sb.appendLine("L1..L22 dalga = ${(1..22).sumOf { WaveDefinitions.waveCount(it) }}")
        sb.appendLine("L23..L55 dalga = ${(23..55).sumOf { WaveDefinitions.waveCount(it) }}")
        sb.appendLine(
            "perde dalga toplamlari = " +
                (1..5).map { act -> (1..55).filter { (it - 1) / 11 + 1 == act }
                    .sumOf { WaveDefinitions.waveCount(it) } }
        )
        sb.appendLine(
            "perde govde toplamlari = " +
                (1..5).map { act -> (1..55).filter { (it - 1) / 11 + 1 == act }
                    .sumOf { l -> WaveDefinitions.wavesFor(l).sumOf { it.spawns.size } } }
        )
        sb.appendLine("toplam tasarim suresi (dk) = %.1f".format((1..55).sumOf { designMinutes(it) }))
        sb.appendLine("en uzun bolum (dk) = %.2f @ L%d".format(
            (1..55).maxOf { designMinutes(it) },
            (1..55).maxByOrNull { designMinutes(it) }
        ))
        sb.appendLine("en kisa bolum (dk) = %.2f @ L%d".format(
            (1..55).minOf { designMinutes(it) },
            (1..55).minByOrNull { designMinutes(it) }
        ))
        sb.appendLine("L23..L55 ortalama sure (dk) = %.2f".format((23..55).map { designMinutes(it) }.average()))
        sb.appendLine("L23..L55 en uzun (dk) = %.2f".format((23..55).maxOf { designMinutes(it) }))
        for (act in 1..5) {
            val levels = (1..55).filter { (it - 1) / 11 + 1 == act }
            val rho = levels.sumOf { SupplyBudgetModel.waveKillSupply(it).toDouble() } /
                levels.sumOf { WaveMetrics.levelAehp(WaveDefinitions.wavesFor(it)).toDouble() }
            sb.appendLine("perde $act rho = %.4f".format(rho))
        }
        fun threatPerSupply(level: Int) =
            WaveMetrics.levelAehp(WaveDefinitions.wavesFor(level)).toDouble() /
                SupplyBudgetModel.waveKillSupply(level)
        for (l in intArrayOf(1, 11, 22, 23, 33, 44, 55)) {
            sb.appendLine("L$l tehdit/Tedarik = %.1f".format(threatPerSupply(l)))
        }
        sb.appendLine("maks govde/dalga = " +
            (1..55).maxOf { l -> WaveDefinitions.wavesFor(l).maxOf { it.spawns.size } })
        sb.appendLine("SPI = " + (1..55).joinToString(" ") {
            "%.2f".format(SupplyBudgetModel.supplyPressureIndex(it))
        })
        sb.appendLine("baslangic Tedariki = " + (1..55).joinToString(" ") {
            GameConfig.levelSpec(it).startingSupply.toString()
        })
        sb.appendLine("I(L) = " + (1..55).joinToString(" ") {
            SupplyBudgetModel.designedLoadoutCost(it).toString()
        })
        sb.appendLine("butce = " + (1..55).joinToString(" ") {
            SupplyBudgetModel.supplyBudget(it).toString()
        })
        sb.appendLine("--- TEK KULE (SINGLE_TOWER) sonucu, L1..L12 ---")
        for (level in 1..12) {
            val solo = CampaignSimulator.play(
                CampaignSimulator.LevelModel(level),
                CampaignSimulator.Playstyle.SINGLE_TOWER
            )
            sb.appendLine(
                "  L$level gecti=${solo.cleared} can=${solo.livesLeft}/${solo.maxLives} " +
                    "yildiz=${solo.stars} kadro=${solo.roster}"
            )
        }
        println(sb)
    }

    /**
     * NE OLURDU — baslangic Tedariki "TASARLANAN KADRONUN KADEME-1 MALIYETI"
     * olsaydi kac bolum gecilirdi?
     *
     * Hipotez: gec bolumlerdeki yenilgilerin sebebi toplam tehdit degil,
     * ACILIS DALGASINDA elde yeterli kule OLMAMASI. Dalga izi bunu gosteriyor
     * (L35: 270 Tedarik = 2 kule, W1 = ~40 govde, 20 can W1'de bitiyor).
     */
    @Test
    fun printWhatIfStartingSupplyCoversTheDesignedRoster() {
        val unlockOrder = { level: Int ->
            GameConfig.TowerType.values()
                .filter { GameConfig.TOWER_SPECS.getValue(it).unlockedAtLevel <= level }
                .sortedWith(
                    compareBy(
                        { GameConfig.TOWER_SPECS.getValue(it).unlockedAtLevel },
                        { GameConfig.TOWER_SPECS.getValue(it).buildCost },
                        { it.name }
                    )
                )
        }
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== NE OLURDU: baslangic Tedariki = kadro kademe-1 maliyeti ===")
        sb.appendLine("L    R  bugun  oneri  bugunGecti  oneriGecti  oneriCan  kadro")
        val failsNow = ArrayList<Int>()
        val failsNew = ArrayList<Int>()
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val r = com.miniappfactory.frontlinedefender.game.economy.SupplyBudgetModel
                .DESIGNED_ROSTER_SIZE[level - 1]
            val order = unlockOrder(level)
            val rosterCost = (0 until r).sumOf {
                GameConfig.TOWER_SPECS.getValue(order[it % order.size]).buildCost
            }
            // Iki kollu (catallanan) haritada kapsama IKIYE bolunur: ayni tehdide
            // karsi iki cephe kurulur. Sermaye bunu karsilamali.
            val forked = CampaignSimulator.LevelModel(level).routes.size > 1
            val proposed = if (forked) (rosterCost * 3 + 1) / 2 else rosterCost
            val today = GameConfig.levelSpec(level).startingSupply
            val now = CampaignSimulator.bestOutcome(level)
            val next = CampaignSimulator.bestOutcome(level, maxOf(today, proposed))
            if (!now.cleared) failsNow.add(level)
            if (!next.cleared) failsNew.add(level)
            sb.appendLine(
                "%-4d %-2d %-6d %-6d %-11s %-11s %-9s %s".format(
                    level, r, today, proposed,
                    if (now.cleared) "EVET" else "HAYIR",
                    if (next.cleared) "EVET" else "HAYIR",
                    "${next.livesLeft}/${next.maxLives}", next.roster
                )
            )
        }
        sb.appendLine("--- bugun gecilemeyen ${failsNow.size}: $failsNow")
        sb.appendLine("--- oneriyle gecilemeyen ${failsNew.size}: $failsNew")
        println(sb)
    }

    /** Secili bolumler icin dalga dalga iz — "nerede olduk" sorusunun cevabi. */
    @Test
    fun printPerWaveTraceForProblemLevels() {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== DALGA IZI (her davranis icin) ===")
        for (level in intArrayOf(11, 12, 14, 19, 21, 22, 24, 33, 44, 55)) {
            sb.appendLine("--- L$level (pad=${CampaignSimulator.LevelModel(level).pads.size}, " +
                "rota=${CampaignSimulator.LevelModel(level).routes.size}) ---")
            for (outcome in CampaignSimulator.allOutcomes(level)) {
                sb.appendLine(
                    "  ${outcome.style} gecti=${outcome.cleared} kadro=${outcome.roster}"
                )
                sb.appendLine("    " + outcome.trace.joinToString(" "))
            }
        }
        println(sb)
    }
}
