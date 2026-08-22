package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.Test

/**
 * ERKEN BOLUM SERMAYE RAPORU — olcum araci, iddia degil.
 *
 * Cihaz raporu (2026-08-22): *"Daha 7. leveldayim, 370 supply ile basliyorum,
 * bu bence cok yuksek."* Ayni oturumda: 7/7 son dalgada elde harcanmamis
 * 318 Tedarik kalmis.
 *
 * Bu dosya sayiyi uretir; karar ekonomi tarafinin.
 */
class EarlySupplyReportTest {

    @Test
    fun `L1-L12 baslangic sermayesi ve kule alim gucu`() {
        val gatling = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN)
        val cannon = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.CANNON)
        val missile = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.ANTI_ARMOR)

        fun maxedCost(s: GameConfig.TowerStats): Int =
            s.buildCost + (s.tiers.getOrNull(1)?.upgradeCost ?: 0)

        val gMax = maxedCost(gatling)
        val cMax = maxedCost(cannon)
        val mMax = maxedCost(missile)

        println("=== MAKSIMUM (kademe-2) KULE MALIYETLERI ===")
        println("  Gatling  kur ${gatling.buildCost} + kd2 ${gatling.tiers[1].upgradeCost} = $gMax")
        println("  Agir Top kur ${cannon.buildCost} + kd2 ${cannon.tiers[1].upgradeCost} = $cMax")
        println("  Fuze     kur ${missile.buildCost} + kd2 ${missile.tiers[1].upgradeCost} = $mMax")
        println()
        println("=== BOLUM BASLANGIC SERMAYESI ===")
        println("  L   sermaye   = kac MAKS Gatling  |  kac MAKS Fuze")

        for (level in 1..12) {
            val supply = GameConfig.startingSupplyFor(level)
            println(
                "  %-3d %-9d   %-18.2f  %.2f".format(
                    level, supply, supply.toFloat() / gMax, supply.toFloat() / mMax
                )
            )
        }
    }
}
