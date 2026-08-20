package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.PointF
import org.junit.Test
import kotlin.math.hypot

/**
 * OLCUM ARACI — acilis bolumlerinde yerlestirme karari NEDEN atil?
 *
 * Bulgu (2026-08-19): L1..L6'nin hicbirinde en kotu pad siralamasi 3 yildizi
 * bozmuyor. Bu arac hangi KALDIRACIN eksik oldugunu soyler:
 *
 *  · Pad'ler arasi KAPSAMA FARKI kucukse -> sorun geometride (iyi pad ile kotu
 *    pad ayni isi goruyor, secim zaten anlamsiz).
 *  · Kapsama farki buyuk ama sonuc ayniysa -> sorun DALGA BASKISINDA (kotu
 *    yerlestirme bile dalgayi tutmaya yetiyor, yani bolum fazla yumusak).
 *
 * Assert YOK: bu bir rapor. Kararin dayanagi olsun diye depoda duruyor.
 */
class EarlyPlacementProbeTool {

    private fun routeFor(level: Int): List<PointF> {
        val spec = GameConfig.levelSpec(level)
        return LevelData.routesForMapId(spec.mapId).first()
    }

    private fun padsFor(level: Int): List<Triple<Int, Float, Float>> {
        val spec = GameConfig.levelSpec(level)
        return LevelData.forMapId(spec.mapId).buildSpots
            .filter { it.id !in spec.disabledPadIds }
            .map { Triple(it.id, it.normX * 1920f, it.normY * 1080f) }
    }

    /** Rotayi 2 ref-px adimlarla ornekler; kapsama dogrudan sayilir. */
    private fun samples(level: Int): List<Pair<Float, Float>> {
        val r = routeFor(level).map { it.x * 1920f to it.y * 1080f }
        val out = mutableListOf<Pair<Float, Float>>()
        for (k in 0 until r.size - 1) {
            val (ax, ay) = r[k]
            val (bx, by) = r[k + 1]
            val len = hypot(bx - ax, by - ay)
            val n = maxOf(1, Math.ceil((len / 2f).toDouble()).toInt())
            for (t in 0 until n) {
                out += (ax + (bx - ax) * t / n) to (ay + (by - ay) * t / n)
            }
        }
        return out
    }

    private fun coverage(level: Int, px: Float, py: Float, range: Float): Int =
        samples(level).count { hypot(it.first - px, it.second - py) <= range } * 2

    @Test
    fun reportEarlyLevelPadCoverageSpread() {
        val range = GameConfig.TOWER_SPECS.getValue(TowerType.MACHINE_GUN).tiers.first().range
        println()
        println("=== ACILIS BOLUMLERI — PAD KAPSAMA DAGILIMI (Gatling menzili $range ref-px) ===")
        println("bolum | acik pad | rota uzunlugu | en iyi | medyan | en kotu | en iyi/en kotu")
        for (level in 1..8) {
            val routeLen = samples(level).size * 2
            val covs = padsFor(level)
                .map { coverage(level, it.second, it.third, range) }
                .filter { it > 2 }
                .sorted()
            if (covs.isEmpty()) continue
            val best = covs.last()
            val worst = covs.first()
            val median = covs[covs.size / 2]
            val ratio = if (worst == 0) Float.POSITIVE_INFINITY else best.toFloat() / worst
            println(
                "L$level".padEnd(6) + "| " + covs.size.toString().padEnd(9) +
                    "| " + routeLen.toString().padEnd(14) +
                    "| " + best.toString().padEnd(7) +
                    "| " + median.toString().padEnd(7) +
                    "| " + worst.toString().padEnd(8) +
                    "| " + "%.2f".format(ratio)
            )
        }
        println()
    }
}

/**
 * OLCUM ARACI — KUSATMA EMRI telafisini boyutlandirmak icin.
 *
 * Carpan oldurme gelirini kirpar; telafi baslangic Tedarikine biner. Dogru
 * telafi TAHMINLE degil, bolumun GERCEK oldurme geliriyle secilir: bolum
 * fakirlesmemeli, gelirin ZAMANI degismeli.
 */
class SiegeOrderSizingTool {

    @Test
    fun reportSiegeCandidateBudgets() {
        println()
        println("=== KUSATMA EMRI ADAYLARI — bugunku butce bilesimi ===")
        println("bolum | baslangic | oldurme geliri | toplam | x0,25'te kaybedilen")
        for (level in intArrayOf(30, 33, 35, 37, 40)) {
            val start = com.miniappfactory.frontlinedefender.game.economy
                .SupplyBudgetModel.startingSupply(level)
            val kill = com.miniappfactory.frontlinedefender.game.economy
                .SupplyBudgetModel.waveKillSupply(level)
            val total = start + kill
            val lost = Math.round(kill * 0.75f)
            println(
                "L$level".padEnd(6) + "| " + start.toString().padEnd(10) +
                    "| " + kill.toString().padEnd(15) +
                    "| " + total.toString().padEnd(7) +
                    "| " + lost
            )
        }
        println()
    }
}
