package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import org.junit.Test

/**
 * "YALNIZ-BUZ" MEVZI DENETIMI — olcum aracı, iddia degil.
 *
 * Amac: hasar veren UC kulenin (Gatling 150 / Agir Top 175 / Fuze 250) hicbiri
 * yetismiyorken YALNIZCA Buz Alani'nin (270) yetistigi mevzileri bulmak.
 *
 * Boyle bir mevzi oyuncuya yalnizca "yavaslatici koy" secenegi birakir; Buz
 * Alani'nin hasari 4,5/10,5/19,5 yani kodun kendi ifadesiyle "almost no
 * damage". Kullanici bunun KAZA oldugunu soyledi (2026-08-22), yani duzeltilmeli.
 *
 * Bu dosya once SAYIYI uretir; kural testi ayri yazilir (bu depoda "test kusuru
 * yasaklamaz, SAYAR" hatasi yedi kez tekrarlandi — once olc, sonra kurali yaz).
 */
class FrostOnlyPadAuditTest {

    private companion object {
        /** Menziller GameConfig.TOWER_SPECS'ten CANLI okunur, elle yazilmaz. */
        val RANGES: Map<GameConfig.TowerType, Float> =
            GameConfig.TowerType.entries.associateWith { type ->
                GameConfig.TOWER_SPECS.getValue(type).level1Range
            }
    }

    @Test
    fun `yalnizca Buz Alani'nin yetistigi mevzileri raporla`() {
        val damageTowers = listOf(
            GameConfig.TowerType.MACHINE_GUN,
            GameConfig.TowerType.CANNON,
            GameConfig.TowerType.ANTI_ARMOR
        )
        val bestDamageRange = damageTowers.maxOf { RANGES.getValue(it) }
        val frostRange = RANGES.getValue(GameConfig.TowerType.SLOW)

        println("=== MENZILLER (kademe 1, ref-px) ===")
        RANGES.forEach { (t, r) -> println("  $t = $r") }
        println("  en iyi HASAR menzili = $bestDamageRange · Buz = $frostRange")
        println()
        println("=== YALNIZ-BUZ MEVZILERI ===")

        var total = 0
        // Her haritanin ilk bolumunu temsilci al: rota harita basina sabit.
        val seenMaps = HashSet<Int>()
        for (spec in GameConfig.CAMPAIGN) {
            val mapId = spec.mapId
            if (!seenMaps.add(mapId)) continue

            val data = LevelData.forMapId(mapId)
            val routes = GeometryTestSupport.activeRoutesFor(mapId, spec.levelId)

            for (spot in data.buildSpots) {
                val d = GeometryTestSupport.padToNearestOfRoutes(
                    spot.normX, spot.normY, routes
                )
                val frostReaches = d <= frostRange
                val anyDamageReaches = d <= bestDamageRange
                if (frostReaches && !anyDamageReaches) {
                    total++
                    val mask = MapMaskFixture.maskFor(mapId)
                    val nowClass = mask.classAt(spot.normX, spot.normY)

                    // ADAY KONUM: pad'i EN YAKIN rota noktasina dogru, mesafe
                    // hedefe inene kadar kaydir. Hedef 245 = Fuze menzili 250
                    // eksi 5 ref-px guvenlik payi (rota bir gun birkac piksel
                    // oynarsa esik tekrar asilmasin).
                    val px = spot.normX * GeometryTestSupport.refW
                    val py = spot.normY * GeometryTestSupport.refH
                    var bx = px
                    var by = py
                    var best = Float.MAX_VALUE
                    for (route in routes) {
                        for (p in route) {
                            val r = GeometryTestSupport.toRef(p)
                            val dd = kotlin.math.hypot(r.x - px, r.y - py)
                            if (dd < best) { best = dd; bx = r.x; by = r.y }
                        }
                    }
                    val target = 245f
                    val move = (d - target).coerceAtLeast(0f)
                    val ux = (bx - px) / best
                    val uy = (by - py) / best
                    val nx = px + ux * move
                    val ny = py + uy * move
                    val newNormX = nx / GeometryTestSupport.refW
                    val newNormY = ny / GeometryTestSupport.refH
                    val newClass = mask.classAt(newNormX, newNormY)
                    val newD = GeometryTestSupport.padToNearestOfRoutes(
                        newNormX, newNormY, routes
                    )

                    println(
                        "  harita %02d · pad %-2d · mesafe %.1f  zemin=%d".format(
                            mapId, spot.id, d, nowClass
                        )
                    )
                    println(
                        "      -> ADAY normX %.5f normY %.5f · mesafe %.1f · zemin=%d · kaydirma %.1f px".format(
                            newNormX, newNormY, newD, newClass, move
                        )
                    )
                }
            }
        }
        println()
        println("TOPLAM yalniz-buz mevzi: $total")

        // KURAL, SAYI DEGIL.
        //
        // Bu depoda yedi kez tekrarlanan hata: "test kusuru yasaklamaz, SAYAR"
        // (ornekler HANDOVER 5. bolum). Burada esik `assertEquals(4, total)`
        // OLSAYDI dorde kadar yalniz-buz mevzi serbest kalirdi ve bugun
        // duzelttigimiz sey yarin baska bir haritada sessizce geri gelirdi.
        //
        // Kural: hicbir mevzi, yalnizca hasar vermeyen bir kulenin ulasabildigi
        // yerde durmamali. Buz Alani'nin hasari 4,5/10,5/19,5 — kodun kendi
        // ifadesiyle "almost no damage".
        //
        // Bu test KASITLI bir tasarim kararini da yakalar; oyle bir karar
        // alinirsa test degil, KURAL degistirilmeli (istisna listesiyle) ve
        // gerekcesi buraya yazilmali.
        org.junit.Assert.assertEquals(
            "Yalnizca Buz Alani'nin yetistigi mevzi var: oyuncuya sadece " +
                "yavaslatici koyma secenegi kalir. Ya pad yola yaklastirilmali " +
                "ya da bilincli bir tasarimsa bu test istisna listesiyle " +
                "guncellenmeli. Ayrinti icin yukaridaki rapora bak.",
            0,
            total
        )
    }
}
