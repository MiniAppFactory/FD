package com.miniappfactory.frontlinedefender.modifiers

import com.miniappfactory.frontlinedefender.balance.CampaignSimulator
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.LevelModifiers
import org.junit.Test

/**
 * OLCUM ARACI — hicbir sey iddia ETMEZ (kapi degil arac, daima yesil).
 *
 * Bir degistiricinin oynanis karsiligi "kural eklendi" cumlesiyle olculemez.
 * Bu arac AYNI bolumu iki kez oynatir — degistiriciyle ve degistiricisiz — ve
 * farki sayilarla yazar: kac sizinti, kac kule, kac Tedarik, kac yildiz.
 *
 * Cikti `app/build/test-results/testDebugUnitTest` altindaki XML raporda
 * `<system-out>` icinde durur.
 */
class LevelModifierImpactReportTool {

    @Test
    fun printModifierImpactTable() {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== BOLUM DEGISTIRICILERI — KISITLI vs KISITSIZ (meta 0, guclendirici yok) ===")
        sb.appendLine(
            "L   kural                 davranis        gecti sizinti yildiz kule tedarik  " +
                "| kisitsiz: gecti sizinti yildiz kule"
        )

        GameConfig.LEVEL_MODIFIERS.keys.sorted().forEach { level ->
            val label = labelOf(GameConfig.LEVEL_MODIFIERS.getValue(level))
            CampaignSimulator.CAREFUL_STYLES.forEach { style ->
                val on = CampaignSimulator.play(CampaignSimulator.LevelModel(level), style)
                val off = CampaignSimulator.play(
                    CampaignSimulator.LevelModel(level, LevelModifiers.NONE),
                    style
                )
                sb.appendLine(
                    "%-3d %-21s %-15s %-5s %-7d %-6d %-4d %-8d | %-5s %-7d %-6d %d".format(
                        level,
                        label,
                        style.name,
                        if (on.cleared) "EVET" else "HAYIR",
                        on.leaked,
                        on.stars,
                        countOf(on.roster),
                        on.leftoverSupply,
                        if (off.cleared) "EVET" else "HAYIR",
                        off.leaked,
                        off.stars,
                        countOf(off.roster)
                    )
                )
            }
            sb.appendLine()
        }
        println(sb)
    }

    private fun labelOf(m: LevelModifiers): String = when {
        m.allowedTowers != null -> "kadro=" + m.allowedTowers!!.joinToString(",") {
            it.name.take(2)
        }
        m.maxTowers != null -> "tavan=${m.maxTowers}"
        m.buildLockedDuringWave -> "donmus mevzi"
        else -> "-"
    }

    private fun countOf(roster: String): Int =
        if (roster == "-") 0
        else roster.split("+").sumOf { it.substringBefore('x').toIntOrNull() ?: 0 }
}
