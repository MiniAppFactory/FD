package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * ATES HATTI — "bu kule buradan yola yetisiyor mu?"
 * ===========================================================================
 *
 * NEDEN VAR
 * ---------
 * Cihaz geri bildirimi: *"gatling topunun yerlesim yerine gore vurus alanini
 * kapsamiyor"*. Olcum pad'in yerinin YANLIS OLMADIGINI gosterdi — bolum 8 /
 * pad 7'den Fuze Rampasi haritanin en iyi ikinci kapsamasini veriyor (674
 * ref-px yol), Gatling ise 1 ref-px farkla yetisemiyor. Yani pad yanlis
 * yerde degil, YANLIS KULE ICIN secilmis; eksik olan sey **kurmadan onceki
 * sinyaldi**.
 *
 * BU DOSYA NEYI KILITLER
 * ----------------------
 *  1. [GameConfig.coversRoute] / [GameConfig.distanceToRoutes] saf davranisi
 *     (sinir dahil, dejenere parca, bos rota).
 *  2. Cizim (`GameCanvas`), panel (`TowerBuildBar`) ve testlerin AYNI
 *     fonksiyondan gectigi — [GeometryTestSupport] artik kendi kopyasini
 *     tutmuyor.
 *  3. 11 haritanin TAM tablosu: hangi pad hangi kule icin "yetismiyor"
 *     isaretlenir. Tablo `println` ile rapora dokulur; testin ASSERT ettigi
 *     sey sabit sayilar DEGIL, degismemesi gereken ILISKILERDIR — harita
 *     geometrisi baska bir ajan tarafindan yeniden uretiliyor ve sabit bir
 *     altin tablo yarin sahte bir kirilma uretirdi.
 */
class LineOfFireTest {

    // ------------------------------------------------------------------------
    // 1. SAF FONKSIYON
    // ------------------------------------------------------------------------

    /** Parca UZERINDEKI nokta sifir uzaklikta. */
    @Test
    fun `parca uzerindeki nokta sifir uzaklikta`() {
        assertEquals(0f, GameConfig.pointToSegmentDistance(5f, 0f, 0f, 0f, 10f, 0f), 1e-4f)
    }

    /** Dik ayak parcanin ICINDE ise dik uzaklik verilir. */
    @Test
    fun `dik ayak parca icindeyse dik uzaklik verilir`() {
        assertEquals(3f, GameConfig.pointToSegmentDistance(5f, 3f, 0f, 0f, 10f, 0f), 1e-4f)
    }

    /**
     * Dik ayak parcanin DISINDA ise UCA olan uzaklik verilir.
     *
     * Bu, "sonsuz dogru" hatasinin kilidi: sonsuz dogruya olcseydik yolun
     * bittigi yerin cok otesindeki bir pad "yola yakin" gorunurdu ve oyuncuya
     * dusman gecmeyecek bir mevzi onerilirdi.
     */
    @Test
    fun `dik ayak parca disindaysa uca olan uzaklik verilir`() {
        // (20,0) parcanin sag ucundan (10,0) 10 birim otede.
        assertEquals(10f, GameConfig.pointToSegmentDistance(20f, 0f, 0f, 0f, 10f, 0f), 1e-4f)
        assertEquals(10f, GameConfig.pointToSegmentDistance(-10f, 0f, 0f, 0f, 10f, 0f), 1e-4f)
    }

    /** Dejenere parca (ust uste iki waypoint) bolme hatasi vermez. */
    @Test
    fun `dejenere parca noktaya uzaklik doner`() {
        assertEquals(5f, GameConfig.pointToSegmentDistance(3f, 4f, 0f, 0f, 0f, 0f), 1e-4f)
    }

    /**
     * SINIR DAHIL. Menzil mesafeye TAM esitse kule vurur; motor da `<=`
     * kullanir. Burada `<` yazilsaydi panel ile oynanis 1 ref-px ayrisirdi ve
     * cihazdan gelen sikayet tam olarak bu buyuklukteydi (151 vs 150).
     */
    @Test
    fun `menzil mesafeye tam esitse kapsar`() {
        val route = listOf(listOf(PointF(0f, 0f), PointF(100f, 0f)))
        assertTrue(GameConfig.coversRoute(50f, 150f, 150f, route))
        assertFalse(GameConfig.coversRoute(50f, 150.01f, 150f, route))
    }

    /** Rota yoksa hicbir sey kapsanmaz — bolum yuklenmeden cizim kosabilir. */
    @Test
    fun `bos rota kumesi kapsamaz ve patlamaz`() {
        assertEquals(Float.MAX_VALUE, GameConfig.distanceToRoutes(0f, 0f, emptyList()), 0f)
        assertFalse(GameConfig.coversRoute(0f, 0f, 1000f, emptyList()))
        // Tek noktalik rota parca uretmez -> kapsamaz.
        assertFalse(GameConfig.coversRoute(0f, 0f, 1000f, listOf(listOf(PointF(0f, 0f)))))
    }

    /** Sifir/negatif menzil hicbir seyi kapsamaz (kule tipi bilinmiyorsa). */
    @Test
    fun `sifir menzil kapsamaz`() {
        val route = listOf(listOf(PointF(0f, 0f), PointF(100f, 0f)))
        assertFalse(GameConfig.coversRoute(0f, 0f, 0f, route))
    }

    /** Coklu rota: EN YAKIN kol kazanir (catalli haritalarda B-kolu da sayilir). */
    @Test
    fun `coklu rotada en yakin kol kazanir`() {
        val routes = listOf(
            listOf(PointF(0f, 0f), PointF(100f, 0f)),      // y=0
            listOf(PointF(0f, 400f), PointF(100f, 400f))   // y=400
        )
        assertEquals(40f, GameConfig.distanceToRoutes(50f, 360f, routes), 1e-3f)
    }

    // ------------------------------------------------------------------------
    // 2. TEK KAYNAK — kopya hesap kalmadi
    // ------------------------------------------------------------------------

    /**
     * TEK HESAP KILIDI. [GeometryTestSupport] bir zamanlar kendi mesafe
     * kopyasini tutuyordu; "olu build pad" hatasinin 22 bolum boyunca testten
     * kacmasinin sebeplerinden biri buydu. Artik urun fonksiyonuna delege
     * ediyor ve bu test iki yolun AYNI sayiyi verdigini olcerek delegasyonun
     * sessizce geri alinmasini engeller.
     */
    @Test
    fun `test yardimcisi ile urun fonksiyonu ayni mesafeyi verir`() {
        GameConfig.CAMPAIGN.take(12).forEach { spec ->
            val routesNorm = GeometryTestSupport.activeRoutesFor(spec.mapId, spec.levelId)
            val routesRef = routesNorm.map { r -> r.map { GeometryTestSupport.toRef(it) } }
            LevelData.forMapId(spec.mapId).buildSpots.forEach { pad ->
                val viaSupport = GeometryTestSupport.padToActiveRoute(
                    pad.normX, pad.normY, spec.mapId, spec.levelId
                )
                val viaConfig = GameConfig.distanceToRoutes(
                    pad.normX * GameConfig.REFERENCE_WIDTH,
                    pad.normY * GameConfig.REFERENCE_HEIGHT,
                    routesRef
                )
                assertEquals(
                    "blm ${spec.levelId} pad ${pad.id}: iki yol ayni mesafeyi vermeli",
                    viaSupport,
                    viaConfig,
                    1e-3f
                )
            }
        }
    }

    // ------------------------------------------------------------------------
    // 3. KAMPANYA TABLOSU
    // ------------------------------------------------------------------------

    /**
     * KAPSAMA TABLOSU — 11 harita, her pad, her kule.
     *
     * Menzil KADEME 1'dir: oyuncu kuleyi once KURAR sonra yukseltir, yani
     * kurma anindaki gercek menzil budur. Meta carpani 1 alinir (yeni oyuncu:
     * en kotu durum). Rota kumesi o bolumde motorun GERCEKTEN kullandigi
     * kumedir ([GeometryTestSupport.activeRoutesFor]) — tum rotalarin
     * minimumuna bakmak pad'i olmadigi kadar iyi gosterirdi.
     */
    @Test
    fun `kampanya kapsama tablosu raporlanir`() {
        val towers = GameConfig.TowerType.values()
        val sb = StringBuilder()
        sb.append("\n=== ATES HATTI TABLOSU (kd.1 menzil, meta x1) ===\n")
        sb.append("X = o kule bu pad'den yola YETISMIYOR (isaretlenir)\n")
        // Ayni harita birden cok bolumde kullaniliyor ve rota kumesi
        // ALT_ROUTE_FIRST_LEVEL'da degisiyor. Tekrarli satir uretmemek icin
        // (harita, rota kumesi) ikilisi basina TEK ornek bolum raporlanir.
        val seen = mutableSetOf<Pair<Int, Boolean>>()
        GameConfig.CAMPAIGN.forEach { spec ->
            val alt = GameConfig.usesAlternateRoutes(spec.levelId)
            if (!seen.add(spec.mapId to alt)) return@forEach
            val routes = GeometryTestSupport.activeRoutesFor(spec.mapId, spec.levelId)
            val routesRef = routes.map { r -> r.map { GeometryTestSupport.toRef(it) } }
            val kol = if (alt) "A+B kolu" else "A kolu"
            sb.append("\n-- harita %02d  (ornek blm %d, %s) --\n".format(spec.mapId, spec.levelId, kol))
            sb.append("pad  uzaklik  " + towers.joinToString("  ") { it.name.take(9).padEnd(9) } + "\n")
            LevelData.forMapId(spec.mapId).buildSpots
                .filter { it.id !in spec.disabledPadIds }
                .forEach { pad ->
                    val px = pad.normX * GameConfig.REFERENCE_WIDTH
                    val py = pad.normY * GameConfig.REFERENCE_HEIGHT
                    val d = GameConfig.distanceToRoutes(px, py, routesRef)
                    val cells = towers.joinToString("  ") { t ->
                        val range = GameConfig.TOWER_SPECS.getValue(t).level1Range
                        val ok = GameConfig.coversRoute(px, py, range, routesRef)
                        (if (ok) "." else "X").padEnd(9)
                    }
                    sb.append("%3d  %7d  %s\n".format(pad.id, d.toInt(), cells))
                }
        }
        println(sb)

        // ---- ASSERT 1: tablo ile mesafe TUTARLI. Isaret, olculen mesafenin
        // fonksiyonu olmali; "X" ile "d > menzil" birbirinden ayrisirsa
        // oyuncuya gosterilen isaret yalan olur.
        val mismatches = mutableListOf<String>()
        GameConfig.CAMPAIGN.forEach { spec ->
            val routesRef = GeometryTestSupport.activeRoutesFor(spec.mapId, spec.levelId)
                .map { r -> r.map { GeometryTestSupport.toRef(it) } }
            LevelData.forMapId(spec.mapId).buildSpots
                .filter { it.id !in spec.disabledPadIds }
                .forEach { pad ->
                    val px = pad.normX * GameConfig.REFERENCE_WIDTH
                    val py = pad.normY * GameConfig.REFERENCE_HEIGHT
                    val d = GameConfig.distanceToRoutes(px, py, routesRef)
                    towers.forEach { t ->
                        val range = GameConfig.TOWER_SPECS.getValue(t).level1Range
                        val marked = !GameConfig.coversRoute(px, py, range, routesRef)
                        if (marked != (d > range)) {
                            mismatches += "blm ${spec.levelId} pad ${pad.id} $t"
                        }
                    }
                }
        }
        assertTrue("Isaret ile olculen mesafe ayrisiyor: $mismatches", mismatches.isEmpty())

        // ---- ASSERT 2: isaretin GERCEKTEN gorunecegi durumlar var. Bu sifir
        // olsaydi ozellik olu kod olurdu — uyari hicbir zaman cizilmezdi ve
        // hicbir test bunu fark etmezdi.
        var marked = 0
        GameConfig.CAMPAIGN.forEach { spec ->
            val routesRef = GeometryTestSupport.activeRoutesFor(spec.mapId, spec.levelId)
                .map { r -> r.map { GeometryTestSupport.toRef(it) } }
            LevelData.forMapId(spec.mapId).buildSpots
                .filter { it.id !in spec.disabledPadIds }
                .forEach { pad ->
                    GameConfig.unlockedTowers(spec.levelId).forEach { t ->
                        val range = GameConfig.TOWER_SPECS.getValue(t).level1Range
                        if (!GameConfig.coversRoute(
                                pad.normX * GameConfig.REFERENCE_WIDTH,
                                pad.normY * GameConfig.REFERENCE_HEIGHT,
                                range,
                                routesRef
                            )
                        ) {
                            marked++
                        }
                    }
                }
        }
        println("Isaretlenen (bolum, pad, kule) uclusu sayisi: $marked")
        assertTrue(
            "Hicbir pad/kule ciftinde 'yetismiyor' cikmiyor — uyari olu kod demektir",
            marked > 0
        )

        // ---- ASSERT 3: hicbir GORUNUR pad, o bolumde KILIDI ACIK TUM kuleler
        // icin birden isaretlenmemeli. Oyle bir pad, uzerine ne kurulursa
        // kurulsun calismayan bir mevzidir ve uyari onu KURTARMAZ — orada
        // yapilacak sey geometriyi duzeltmektir, uyari yazmak degil.
        // (Ayni kural `PadReachabilityPerLevelTest` tarafindan da tutuluyor;
        // burada UI'nin gosterecegi menzille, yani ayni fonksiyonla olculuyor.)
        val dead = mutableListOf<String>()
        GameConfig.CAMPAIGN.forEach { spec ->
            val routesRef = GeometryTestSupport.activeRoutesFor(spec.mapId, spec.levelId)
                .map { r -> r.map { GeometryTestSupport.toRef(it) } }
            LevelData.forMapId(spec.mapId).buildSpots
                .filter { it.id !in spec.disabledPadIds }
                .forEach { pad ->
                    val any = GameConfig.unlockedTowers(spec.levelId).any { t ->
                        GameConfig.coversRoute(
                            pad.normX * GameConfig.REFERENCE_WIDTH,
                            pad.normY * GameConfig.REFERENCE_HEIGHT,
                            GameConfig.TOWER_SPECS.getValue(t).level1Range,
                            routesRef
                        )
                    }
                    if (!any) dead += "blm ${spec.levelId} (harita ${spec.mapId}) pad ${pad.id}"
                }
        }
        assertTrue("HICBIR acik kulenin yetismedigi gorunur pad: $dead", dead.isEmpty())
    }
}
