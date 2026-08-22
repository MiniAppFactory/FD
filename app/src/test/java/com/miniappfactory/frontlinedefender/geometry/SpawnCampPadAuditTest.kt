package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import org.junit.Test
import kotlin.math.hypot

/**
 * SPAWN KAMPI DENETIMI — olcum araci.
 *
 * Cihaz raporu (2026-08-22): *"4. levelde onlarin base onune iki gatling
 * koydum, levellerini max yaptim, kendi baselerinden bile cikamadan
 * oldüler."* Kullanici bunun yanlis oldugunu soyledi: dusman cikisinin tam
 * onunde mevzi olmamali.
 *
 * Burada "cikisin onunde" tanimi GOZLE degil MENZILLE yapilir: bir mevzi,
 * uzerine kurulan EN KISA menzilli kule (Gatling, 150 ref-px) ile rotanin
 * BASLANGIC noktasini vurabiliyorsa, o mevzi bir spawn kampidir — dusman
 * daha yurumeye baslamadan olur ve haritanin geri kalani anlamsizlasir.
 *
 * Bu dosya once SAYIYI uretir. Kural testi ayri yazilir (bu depoda "test
 * kusuru yasaklamaz, SAYAR" hatasi yedi kez tekrarlandi).
 */
class SpawnCampPadAuditTest {

    @Test
    fun `spawn noktasini vurabilen mevzileri raporla`() {
        val gatlingRange = GameConfig.TOWER_SPECS
            .getValue(GameConfig.TowerType.MACHINE_GUN).level1Range

        println("=== Gatling kademe-1 menzili: $gatlingRange ref-px ===")
        println("=== SPAWN'I VURABILEN MEVZILER ===")

        var total = 0
        val seenMaps = HashSet<Int>()
        for (spec in GameConfig.CAMPAIGN) {
            val mapId = spec.mapId
            if (!seenMaps.add(mapId)) continue

            val data = LevelData.forMapId(mapId)
            val routes = GeometryTestSupport.activeRoutesFor(mapId, spec.levelId)

            // Her rotanin ILK noktasi bir spawn agzidir. Cift kollu haritada
            // iki ayri spawn olabilir; hepsi kontrol edilir.
            val spawns = routes.mapNotNull { it.firstOrNull() }
                .map { GeometryTestSupport.toRef(it) }
            if (spawns.isEmpty()) continue

            for (spot in data.buildSpots) {
                val px = spot.normX * GeometryTestSupport.refW
                val py = spot.normY * GeometryTestSupport.refH
                val dMin = spawns.minOf { hypot(it.x - px, it.y - py) }
                if (dMin <= gatlingRange) {
                    total++
                    val mask = MapMaskFixture.maskFor(mapId)
                    println(
                        "  harita %02d · pad %-2d · spawn'a %.1f · yola %.1f · zemin=%d".format(
                            mapId, spot.id, dMin,
                            GeometryTestSupport.padToNearestOfRoutes(
                                spot.normX, spot.normY, routes
                            ),
                            mask.classAt(spot.normX, spot.normY)
                        )
                    )

                    // ADAY TARAMASI: spawn'dan UZAKLASAN yonde kucuk adimlarla
                    // ilerle; ilk gecerli konumu bildir.
                    //
                    // GECERLILIK UC KOSUL:
                    //   1. spawn'a >= 190 ref-px  (Gatling 150 ve Agir Top 175
                    //      artik spawn agzini goremez, 15 px guvenlik payiyla)
                    //   2. yola <= 150 ref-px     (mevzi HALA ise yarar; yoksa
                    //      olu pad uretmis oluruz — dun tam bunu duzelttik)
                    //   3. maske sinifi ROAD DEGIL (kule yolun ustune kurulamaz)
                    val nearestSpawn = spawns.minByOrNull { hypot(it.x - px, it.y - py) }!!
                    val ux = (px - nearestSpawn.x) / dMin
                    val uy = (py - nearestSpawn.y) / dMin
                    var found = false
                    var step = 5f
                    while (step <= 160f && !found) {
                        val nx = px + ux * step
                        val ny = py + uy * step
                        val nnx = nx / GeometryTestSupport.refW
                        val nny = ny / GeometryTestSupport.refH
                        val dSpawn = spawns.minOf { hypot(it.x - nx, it.y - ny) }
                        val dRoute = GeometryTestSupport.padToNearestOfRoutes(nnx, nny, routes)
                        val cls = mask.classAt(nnx, nny)
                        if (dSpawn >= 190f && dRoute <= gatlingRange &&
                            cls != MapMaskFixture.CLASS_ROAD
                        ) {
                            println(
                                "      -> ADAY normX %.5f normY %.5f · spawn %.1f · yol %.1f · zemin=%d · kaydirma %.0f px".format(
                                    nnx, nny, dSpawn, dRoute, cls, step
                                )
                            )
                            found = true
                        }
                        step += 5f
                    }
                    if (!found) println("      -> UYGUN ADAY YOK (elle bakilmali)")
                }
            }
        }
        println()
        println("TOPLAM spawn kampi mevzisi: $total")
    }
}
