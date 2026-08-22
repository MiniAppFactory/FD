package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.PointF
import org.junit.Test
import kotlin.math.hypot

/**
 * SPAWN KAMPI DENETIMI — olcum araci.
 *
 * ## Soru DEGISTI (2026-08-22, ikinci cihaz raporu)
 * Ilk surum yanlis soruyu soruyordu: *"mevzi, rotanin ILK NOKTASINA kac
 * ref-px uzakta?"*. O olcumle tum kampanyada tek bir mevzi cikti (harita 04
 * pad 1) — ama kullanici L2 ve L3'te de *"rakip karargah onunde pad var"*
 * dedi ve ekran goruntusuyle gosterdi.
 *
 * Neden yanilmisti: rotanin ilk noktasi ile ekranda gorunen dusman yapisinin
 * agzi ayni yer degil, ve asil onemli olan mesafe degil **KAPSAMA BASLANGICI**.
 * Bir kule spawn noktasindan 200 px uzakta olabilir ama rotanin 0. metresini
 * hala menzilinde tutuyorsa dusman daha yurumeye baslamadan olur.
 *
 * ## Dogru olcum
 * Rota ~10 ref-px araliklarla orneklenir ve her ornegin YAY UZUNLUGU (spawn'dan
 * itibaren yurunen mesafe) tutulur. Bir mevzi icin sorulan sey:
 *
 *   "Uzerine kurulan EN KISA menzilli kule (Gatling, 150) rotayi ilk kez
 *    KACINCI metrede goruyor?"
 *
 * Bu sayi 0'a yakinsa mevzi bir spawn kampidir: dusman cikis agzindan
 * itibaren ates altindadir.
 *
 * Bu dosya SAYI uretir; esik ve duzeltme karari kullanicinin.
 */
class SpawnCampPadAuditTest {

    /** Rotayi ~10 ref-px adimlarla ornekler; her ornek (yayUzunlugu, nokta). */
    private fun sampleWithArcLength(routeNorm: List<PointF>): List<Pair<Float, PointF>> {
        val ref = routeNorm.map { GeometryTestSupport.toRef(it) }
        val out = ArrayList<Pair<Float, PointF>>()
        if (ref.isEmpty()) return out
        var acc = 0f
        out.add(0f to ref.first())
        for (i in 0 until ref.size - 1) {
            val seg = hypot(ref[i + 1].x - ref[i].x, ref[i + 1].y - ref[i].y)
            val steps = maxOf(1, (seg / 10f).toInt())
            for (k in 1..steps) {
                val t = k.toFloat() / steps
                val p = PointF(
                    ref[i].x + (ref[i + 1].x - ref[i].x) * t,
                    ref[i].y + (ref[i + 1].y - ref[i].y) * t
                )
                acc += seg / steps
                out.add(acc to p)
            }
        }
        return out
    }

    @Test
    fun `her mevzinin rotayi kacinci metreden gordugunu raporla`() {
        val gatlingRange = GameConfig.TOWER_SPECS
            .getValue(GameConfig.TowerType.MACHINE_GUN).level1Range

        println("=== Gatling kademe-1 menzili: $gatlingRange ref-px ===")
        println("=== KAPSAMA BASLANGICI (rotanin kacinci metresi) ===")
        println("   0'a yakin = spawn kampi · buyuk = guvenli")

        val seenMaps = HashSet<Int>()
        for (spec in GameConfig.CAMPAIGN) {
            val mapId = spec.mapId
            if (!seenMaps.add(mapId)) continue

            val data = LevelData.forMapId(mapId)
            val routes = GeometryTestSupport.activeRoutesFor(mapId, spec.levelId)
            if (routes.isEmpty()) continue
            val sampled = routes.map { sampleWithArcLength(it) }

            // Her mevzi icin: kule menzilindeki EN KUCUK yay uzunlugu.
            val rows = ArrayList<Triple<Int, Float, Float>>()
            for (spot in data.buildSpots) {
                val px = spot.normX * GeometryTestSupport.refW
                val py = spot.normY * GeometryTestSupport.refH
                var firstCovered = Float.MAX_VALUE
                var nearest = Float.MAX_VALUE
                for (route in sampled) {
                    for ((arc, p) in route) {
                        val d = hypot(p.x - px, p.y - py)
                        if (d < nearest) nearest = d
                        if (d <= gatlingRange && arc < firstCovered) firstCovered = arc
                    }
                }
                if (firstCovered != Float.MAX_VALUE) {
                    rows.add(Triple(spot.id, firstCovered, nearest))
                }
            }

            // En erken kapsayan UC mevziyi bas — ilgilendigimiz uc bunlar.
            val worst = rows.sortedBy { it.second }.take(3)
            println("  --- harita %02d ---".format(mapId))
            worst.forEach { (id, arc, near) ->
                val flag = if (arc <= 60f) "  <<< SPAWN KAMPI" else ""
                println(
                    "     pad %-2d · rotayi %6.1f. metreden gorur · yola %5.1f%s"
                        .format(id, arc, near, flag)
                )
            }
        }
    }
}
