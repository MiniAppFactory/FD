package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.MetaUpgrades
import com.miniappfactory.frontlinedefender.game.economy.UpgradeLine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.Test

/**
 * OLCUM ARACI — hicbir sey iddia ETMEZ. Meta yukseltme agacinin **oynanis
 * karsiligini** olcer: her hat, rank rank, gercek motor aynasiyla oynanarak.
 *
 * Sorunun kaynagi: dukkanda "+%3 hasar" yaziyor ve oyuncu bunun ne demek
 * oldugunu BILMIYOR. Bu arac yuzdeyi oynanisa cevirir: rank basina kac sizinti
 * daha az, kac kule kadar ek DPS, kac ref-px menzil, kac Tedarik.
 *
 * Cikti `app/build/test-results` altindaki XML raporda `<system-out>` icinde
 * durur. Kapi degil ARAC oldugu icin daima yesildir.
 */
class MetaImpactReportTool {

    /** Olcum bolumleri: ogretici bant, perde finalleri, gec oyun. */
    private val probeLevels = listOf(1, 4, 8, 11, 22, 34, 45, 55)

    private fun maxed(): MetaUpgrades = MetaUpgrades(
        firepower = UpgradeLine.FIREPOWER.maxRank,
        optics = UpgradeLine.OPTICS.maxRank,
        startingSupplyRank = UpgradeLine.STARTING_SUPPLY.maxRank,
        fortification = UpgradeLine.FORTIFICATION.maxRank,
        salvage = UpgradeLine.SALVAGE.maxRank,
    )

    private fun lineOnly(line: UpgradeLine, rank: Int) = MetaUpgrades().withRank(line, rank)

    // =======================================================================
    // 1. Hat hat, rank rank: dukkanda ne yaziyor + oynanista ne demek
    // =======================================================================

    @Test
    fun printPerRankEffectLadder() {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== META HAT MERDIVENI — rank basina ETKI ve FIYAT ===")
        sb.appendLine("hat            rank fiyat  kumul  etki            rank basi adim")
        for (line in UpgradeLine.entries) {
            var cumulative = 0
            for (rank in 1..line.maxRank) {
                val price = line.costOfRank(rank)
                cumulative += price
                val m = lineOnly(line, rank)
                val prev = lineOnly(line, rank - 1)
                val (value, step) = when (line) {
                    UpgradeLine.FIREPOWER -> Pair(
                        "hasar x%.3f".format(m.damageMultiplier),
                        "+%.1f%%".format((m.damageMultiplier - prev.damageMultiplier) * 100),
                    )
                    UpgradeLine.OPTICS -> Pair(
                        "menzil x%.3f".format(m.rangeMultiplier),
                        "+%.1f%%".format((m.rangeMultiplier - prev.rangeMultiplier) * 100),
                    )
                    UpgradeLine.STARTING_SUPPLY -> Pair(
                        "sermaye %d".format(m.startingSupply),
                        "+%d".format(m.startingSupply - prev.startingSupply),
                    )
                    UpgradeLine.FORTIFICATION -> Pair(
                        "maks can %d".format(m.maxBaseHealth),
                        "+%d".format(m.maxBaseHealth - prev.maxBaseHealth),
                    )
                    UpgradeLine.SALVAGE -> Pair(
                        "iade %%%.0f".format(m.salvageRatio * 100),
                        "+%.0f puan".format((m.salvageRatio - prev.salvageRatio) * 100),
                    )
                }
                sb.appendLine(
                    "%-14s %-4d %-6d %-6d %-15s %s".format(
                        line.name, rank, price, cumulative, value, step
                    )
                )
            }
        }
        sb.appendLine("--- agac toplami ${UpgradeLine.entries.sumOf { it.totalCost() }} coin / " +
            "${UpgradeLine.entries.sumOf { it.maxRank }} rank")
        println(sb)
    }

    // =======================================================================
    // 2. Yuzdeyi OYNANISA cevir: kule ve menzil karsiliklari
    // =======================================================================

    @Test
    fun printGameplayEquivalents() {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== YUZDENIN OYNANIS KARSILIGI ===")

        val gatling = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN)
        val g1 = gatling.tier(1)
        val full = maxed()

        sb.appendLine(
            "Gatling kd.1: DPS %.1f · menzil %.0f ref-px · insa %d Tedarik"
                .format(g1.dps, g1.range, gatling.buildCost)
        )
        for (rank in 1..UpgradeLine.FIREPOWER.maxRank) {
            val m = lineOnly(UpgradeLine.FIREPOWER, rank)
            val extraDps = g1.dps * (m.damageMultiplier - 1.0)
            sb.appendLine(
                "  ATESGUCU rank %d -> Gatling DPS %.1f (+%.1f) = %.2f KULE kadar ek DPS"
                    .format(rank, g1.dps * m.damageMultiplier, extraDps, extraDps / g1.dps)
            )
        }
        for (rank in 1..UpgradeLine.OPTICS.maxRank) {
            val m = lineOnly(UpgradeLine.OPTICS, rank)
            sb.appendLine(
                "  MENZIL   rank %d -> Gatling menzil %.0f ref-px (+%.0f)"
                    .format(rank, g1.range * m.rangeMultiplier, g1.range * (m.rangeMultiplier - 1.0))
            )
        }
        sb.appendLine(
            "  TAM AGAC -> etkin verim x%.3f = %.2f kule kadar ek gucun tahtaya eklenmesi"
                .format(full.effectiveThroughput, full.effectiveThroughput - 1.0)
        )

        // Baslangic Tedariki: kac EK ACILIS KULESI aliyor?
        sb.appendLine()
        sb.appendLine("BASLANGIC TEDARIKI — acilista kac Gatling (60 Tedarik) alinabiliyor")
        sb.appendLine("L    sermaye(meta0)  " +
            (1..UpgradeLine.STARTING_SUPPLY.maxRank).joinToString(" ") { "r$it" })
        for (level in probeLevels) {
            val base = GameConfig.levelSpec(level).startingSupply
            val row = (1..UpgradeLine.STARTING_SUPPLY.maxRank).joinToString(" ") { r ->
                val m = lineOnly(UpgradeLine.STARTING_SUPPLY, r)
                val supply = base + (m.startingSupply - 150)
                "%d(%d)".format(supply / gatling.buildCost, supply)
            }
            sb.appendLine("%-4d %-15s %s".format(level, "${base / gatling.buildCost}($base)", row))
        }

        // Hurda Degeri: bir kule satildiginda geri gelen Tedarik.
        sb.appendLine()
        sb.appendLine("HURDA DEGERI — kd.3 Gatling (yatirim ${gatling.buildCost + 65 + 130}) satisi")
        for (rank in 0..UpgradeLine.SALVAGE.maxRank) {
            val m = lineOnly(UpgradeLine.SALVAGE, rank)
            val invested = gatling.buildCost + 65 + 130
            sb.appendLine(
                "  rank %d -> iade %%%.0f = %d Tedarik".format(
                    rank, m.salvageRatio * 100, (invested * m.salvageRatio).toInt()
                )
            )
        }
        println(sb)
    }

    // =======================================================================
    // 3. GERCEK OLCUM: meta 0 ile tam rank arasindaki oynanis farki
    // =======================================================================

    @Test
    fun printMeasuredMetaDelta() {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== OLCULEN META FARKI (CampaignSimulator) ===")
        sb.appendLine(
            "Olcut: dikkatli oyuncunun EN IYI kosusundaki sizinti sayisi " +
                "(gecebilen davranislarin en azi). `bestOutcome` degil, cunku o " +
                "ILK gecen davranisi doner ve meta degisince davranis degisir — " +
                "farkli davranislarin sizintisini kiyaslamak gurultu uretir."
        )
        sb.appendLine("L    meta0  tamRank  fark   meta0 yildiz -> tamRank yildiz")
        val full = maxed()
        for (level in probeLevels) {
            val zero = MetaImpact.bestLeaks(level)
            val max = MetaImpact.bestLeaks(level, full)
            sb.appendLine(
                "%-4d %-6d %-8d %-6d %d -> %d".format(
                    level, zero.leaked, max.leaked, max.leaked - zero.leaked,
                    zero.stars, max.stars
                )
            )
        }

        sb.appendLine()
        sb.appendLine("=== HAT HAT OLCUM (yalnizca o hat tam rank) — en iyi kosunun sizintisi ===")
        sb.appendLine("L    meta0  " + UpgradeLine.entries.joinToString("  ") { it.name.take(6) })
        for (level in probeLevels) {
            val zero = MetaImpact.bestLeaks(level).leaked
            val cells = UpgradeLine.entries.joinToString("  ") { line ->
                "%-6d".format(MetaImpact.bestLeaks(level, lineOnly(line, line.maxRank)).leaked)
            }
            sb.appendLine("%-4d %-6d %s".format(level, zero, cells))
        }

        sb.appendLine()
        sb.appendLine("=== RANK RANK OLCUM — 8 OLCUM BOLUMU (toplam sizinti) ===")
        sb.appendLine(
            "!! DIKKAT: bu tablo KUCUK ORNEKLEM. Doygunluga ulasir (Ates Gucu " +
                "r3=r4) ve gurultuludur. 'Olu rank' karari BUNUNLA verilmez — " +
                "asagidaki kampanya capindaki tablo kullanilir."
        )
        for (line in UpgradeLine.entries) {
            val row = (0..line.maxRank).joinToString(" ") { rank ->
                val total = probeLevels.sumOf { MetaImpact.bestLeaks(it, lineOnly(line, rank)).leaked }
                "r$rank=$total"
            }
            sb.appendLine("%-16s %s".format(line.name, row))
        }

        sb.appendLine()
        sb.appendLine("=== RANK RANK OLCUM — 55 BOLUMUN TAMAMI (olu rank kapisinin olcutu) ===")
        sb.appendLine(
            "`MetaUpgradeImpactTest.everyRankIsMeasurablyAliveAcrossTheCampaign` " +
                "bu merdivenin saldiri hatlarinda KESIN AZALAN olmasini sart kosar. " +
                "Tahkimat ve Hurda Degeri sizinti eksenine girmez (biri toleransi " +
                "buyutur, digeri kule satisiyla calisir) ve kendi eksenlerinde olculur."
        )
        val allLevels = (1..EconomyConfig.CAMPAIGN_LEVELS).toList()
        for (line in listOf(UpgradeLine.FIREPOWER, UpgradeLine.OPTICS, UpgradeLine.STARTING_SUPPLY)) {
            var prev = -1
            val row = (0..line.maxRank).joinToString(" ") { rank ->
                val total = allLevels.sumOf { MetaImpact.bestLeaks(it, lineOnly(line, rank)).leaked }
                val delta = if (prev < 0) "" else "(%+d)".format(total - prev)
                prev = total
                "r$rank=$total$delta"
            }
            sb.appendLine("%-16s %s".format(line.name, row))
        }
        println(sb)
    }

    // =======================================================================
    // 4. Ogretici bant: tek kule + hicbir sey yapmayan oyuncu, meta 0 vs tam
    // =======================================================================

    @Test
    fun printTutorialBandUnderFullMeta() {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== OGRETICI BANT (L1-L8) — TEK KULE ve IDLE, meta 0 vs tam agac ===")
        sb.appendLine("L    tekKule(meta0)          tekKule(tamAgac)        IDLE(tamAgac)")
        val full = maxed()
        for (level in 1..8) {
            val solo0 = CampaignSimulator.play(
                CampaignSimulator.LevelModel(level), CampaignSimulator.Playstyle.SINGLE_TOWER
            )
            val solo1 = CampaignSimulator.play(
                CampaignSimulator.LevelModel(level), CampaignSimulator.Playstyle.SINGLE_TOWER,
                null, full
            )
            val idle1 = CampaignSimulator.play(
                CampaignSimulator.LevelModel(level), CampaignSimulator.Playstyle.IDLE, null, full
            )
            sb.appendLine(
                "%-4d %-23s %-23s %s".format(
                    level,
                    "%s can %d/%d yild %d".format(
                        if (solo0.cleared) "GECTI" else "KAYIP",
                        solo0.livesLeft, solo0.maxLives, solo0.stars
                    ),
                    "%s can %d/%d yild %d".format(
                        if (solo1.cleared) "GECTI" else "KAYIP",
                        solo1.livesLeft, solo1.maxLives, solo1.stars
                    ),
                    if (idle1.cleared) "GECTI (!!)" else "KAYIP"
                )
            )
        }
        // Hangi TEK hat tek kuleyi tasiyor?
        sb.appendLine()
        sb.appendLine("L5/L6 tek kule — hangi hat tek basina sonucu ceviriyor?")
        for (line in UpgradeLine.entries) {
            val m = lineOnly(line, line.maxRank)
            val r = (5..6).joinToString(" ") { lv ->
                val o = CampaignSimulator.play(
                    CampaignSimulator.LevelModel(lv), CampaignSimulator.Playstyle.SINGLE_TOWER,
                    null, m
                )
                "L$lv=" + (if (o.cleared) "GECTI" else "KAYIP") + "(${o.livesLeft}/${o.maxLives})"
            }
            sb.appendLine("  %-16s %s".format(line.name, r))
        }
        println(sb)
    }
}

/**
 * Meta olcumlerinin ORTAK olcutu.
 *
 * `CampaignSimulator.bestOutcome` ILK gecen davranisi dondurur; meta degisince
 * gecen davranis da degisir ve iki farkli oyuncu modelinin sizintisini
 * kiyaslamak gurultu uretir (olculdu: L34'te menzil tam rank ile "10 -> 16
 * sizinti", oysa gercek fark davranisin degismesiydi). Dogru olcut, o metayla
 * **dikkatli oyuncunun ulasabilecegi en iyi sonuc**: gecebilen tum davranislar
 * icinde en az sizinti.
 */
object MetaImpact {

    data class Best(val leaked: Int, val stars: Int, val cleared: Boolean, val roster: String)

    /**
     * Simulator DETERMINISTIK oldugu icin (ayni bolum + ayni meta -> ayni sonuc)
     * olcumler bellege alinir. Kampanya capinda olcum yapan
     * `everyRankIsMeasurablyAliveAcrossTheCampaign` ile rapor araci ayni
     * (bolum, meta) ciftlerini defalarca ister; onbellek olmadan ayni simulasyon
     * onlarca kez kosardi.
     */
    private val cache = HashMap<Pair<Int, MetaUpgrades>, Best>()

    fun bestLeaks(levelId: Int, meta: MetaUpgrades = MetaUpgrades()): Best =
        cache.getOrPut(levelId to meta) { compute(levelId, meta) }

    private fun compute(levelId: Int, meta: MetaUpgrades): Best {
        val outcomes = CampaignSimulator.allOutcomes(levelId, meta)
        val clearing = outcomes.filter { it.cleared }
        if (clearing.isEmpty()) {
            val furthest = outcomes.maxByOrNull { it.wavesCleared }!!
            return Best(furthest.leaked, 0, false, furthest.roster)
        }
        val best = clearing.minByOrNull { it.leaked }!!
        return Best(best.leaked, best.stars, true, best.roster)
    }
}
