package com.miniappfactory.frontlinedefender.waves

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.game.model.WaveMetrics
import org.junit.Test

/**
 * OLCUM ARACI — hicbir sey iddia ETMEZ. Her bolumun dusman tipi dagilimini
 * (govde payi VE AEHP payi ayri ayri) basar. "Arketip etiketi sahada okunuyor
 * mu" sorusunun once/sonra kaniti buradan okunur.
 */
class ArchetypeMixReportTool {

    @Test
    fun printArchetypeMixTable() {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== ARKETIP vs GERCEK DAGILIM (govde% | AEHP%) ===")
        sb.appendLine(
            "L   act ark  inf%  fast% shld% arm%  tank% | AEHP: inf   fast  shld  arm   tank  | tepe=tip(pay)"
        )
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val spec = GameConfig.levelSpec(level)
            val waves = WaveDefinitions.wavesFor(level)
            val counts = HashMap<GameConfig.EnemyType, Int>()
            val aehp = HashMap<GameConfig.EnemyType, Double>()
            waves.forEach { w ->
                w.spawns.forEach { s ->
                    counts[s.enemyType] = (counts[s.enemyType] ?: 0) + 1
                    aehp[s.enemyType] =
                        (aehp[s.enemyType] ?: 0.0) + WaveMetrics.AEHP.getValue(s.enemyType).toDouble()
                }
            }
            val totalBodies = counts.values.sum().toDouble()
            val totalAehp = aehp.values.sum()
            val order = listOf(
                GameConfig.EnemyType.INFANTRY, GameConfig.EnemyType.FAST_SOLDIER,
                GameConfig.EnemyType.SHIELDED_TROOPER, GameConfig.EnemyType.ARMORED_VEHICLE,
                GameConfig.EnemyType.TANK
            )
            val ark = WaveDefinitions.archetypeOfOrNull(level)?.toString() ?: "-"
            val bodyPct = order.map { 100.0 * (counts[it] ?: 0) / totalBodies }
            val hpPct = order.map { 100.0 * (aehp[it] ?: 0.0) / totalAehp }
            val topIdx = bodyPct.indices.maxByOrNull { bodyPct[it] }!!
            sb.appendLine(
                "L%-3d %d   %s   ".format(level, spec.act, ark) +
                    bodyPct.joinToString("") { "%5.1f ".format(it) } +
                    "|      " + hpPct.joinToString("") { "%5.1f ".format(it) } +
                    "| %s(%.1f%%)".format(order[topIdx].name.take(5), bodyPct[topIdx])
            )
        }
        println(sb)
    }
    @Test
    fun printPerWaveShieldShare() {
        val order = listOf(
            GameConfig.EnemyType.INFANTRY, GameConfig.EnemyType.FAST_SOLDIER,
            GameConfig.EnemyType.SHIELDED_TROOPER, GameConfig.EnemyType.ARMORED_VEHICLE,
            GameConfig.EnemyType.TANK
        )
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== DALGA BAZINDA TEK-TIP TEPE PAYI (govde%) ===")
        sb.appendLine("L   ark  dalga basina hakim tip payi ->                       | enKotu")
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val ark = WaveDefinitions.archetypeOfOrNull(level)?.toString() ?: "-"
            val waves = WaveDefinitions.wavesFor(level)
            val perWave = waves.map { w ->
                val n = w.spawns.size.toDouble()
                val top = order.maxByOrNull { t -> w.spawns.count { it.enemyType == t } }!!
                val share = 100.0 * w.spawns.count { it.enemyType == top } / n
                Triple(top, share, w.spawns.size)
            }
            val worst = perWave.maxByOrNull { it.second }!!
            sb.appendLine(
                "L%-3d %s   ".format(level, ark) +
                    perWave.joinToString(" ") { "%s%.0f".format(it.first.name.take(1), it.second) }
                        .padEnd(48) +
                    "| %s %.1f%%".format(worst.first.name.take(5), worst.second)
            )
        }
        println(sb)
    }
}
