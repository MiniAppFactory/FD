package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * YERLESIM KILIDI — "ust siradaki pad'e DOKUNABILIYOR muyum?"
 * ===========================================================================
 *
 * NEDEN VAR (cihaz geri bildirimi, Galaxy S8 / API 24 / yatay 740x360 dp)
 * ----------------------------------------------------------------------
 * *"harita sigmamis, ustteki silah koyma yerine dokunamiyorum."* Ekran
 * goruntusunde harita 4'un en ust sirasindaki iki build pad'in ust yarisi opak
 * HUD seridinin (56 dp) altinda kaliyor ve dokunus almiyordu.
 *
 * ESKI FORMULUN HATASI (`GameConfig.MAP_SAFE_TOP_FRAC = 0.10`)
 * ------------------------------------------------------------
 * Kaydirma SABIT bir orandi: `fieldTop = hudInset - 0.10 * fh`. HUD'in OLCULEN
 * yuksekligi formule GIRIYORDU ama haritanin GERCEK geometrisi hic girmiyordu.
 * 360 dp'lik ekranda kaydirma 0,10 * 338 = 34 dp, HUD ise 56 dp -> 22 dp'lik
 * serit ortuluyordu. Ustelik KDoc'un dayandigi olcum ("en ustteki pad merkezi
 * normY = 0,1655") 11 haritanin sadece bir kismi icin dogruydu; gercek en kotu
 * durumlar harita 10 (0,0691) ve harita 06 (0,0878) — yani "ust %10 dekoratif"
 * varsayimi bu haritalarda BASTAN yanlisti.
 *
 * BU DOSYA NEYI KILITLER
 * ----------------------
 * 11 harita x 3 gercek ekran orani icin, `GameConfig.computeFieldRect` ile
 * kurulan oynanis dikdortgeninde:
 *  1. hicbir pad'in DOKUNMA DAIRESI HUD seridinin altina girmez,
 *  2. hicbir pad'in kule sprite'i HUD tarafindan kirpilmaz,
 *  3. hicbir pad'in dokunma dairesi alanin ALT kenarindan tasmaz,
 *  4. yolun ust ucu HUD'in altinda kalmaz.
 *
 * Guvenlik EKRANDAN BAGIMSIZ oldugu icin (bkz. `GameConfig.mapSafeTopFrac`
 * KDoc'u: `fh` sadelesir) uc ekran orani "ornek" degil KANITTIR; yine de
 * regresyon anlatimi icin uc gercek cihaz orani secildi.
 */
class MapLayoutSafetyTest {

    /**
     * Gercek cihaz yatay olculeri (dp). Guvenlik oranlar uzerinden ispatlanmis
     * olsa da, sayilarin gercek bir cihazi temsil etmesi rapor okunabilirligi
     * icin onemli.
     *
     * · 740x360 — Galaxy S8 / API 24, kullanicinin hatayi buldugu cihaz
     * · 640x360 — 16:9 telefon (en dar oynanis alani)
     * · 891x411 — modern 19.5:9 telefon
     */
    private val screens = listOf(
        Triple("S8 740x360", 740f, 360f),
        Triple("16:9 640x360", 640f, 360f),
        Triple("19.5:9 891x411", 891f, 411f)
    )

    /** HUDOverlay'in olculen yuksekligi (dp). `GameConfig.HUD_TOP_INSET_DP`. */
    private val hudDp = GameConfig.HUD_TOP_INSET_DP

    private val mapIds = GameConfig.SHIPPED_MAP_IDS.sorted()

    // =======================================================================

    @Test
    fun noPadTapCircleIsEverHiddenUnderTheTopHud() {
        val failures = mutableListOf<String>()
        mapIds.forEach { mapId ->
            val pads = LevelData.forMapId(mapId).buildSpots
            screens.forEach { (name, w, h) ->
                val r = GameConfig.computeFieldRect(mapId, w, h, hudDp)
                val tapR = GameConfig.TAP_RADIUS_REF_PX * r.renderScale
                pads.forEach { pad ->
                    val top = r.top + pad.normY * r.height - tapR
                    if (top < hudDp - EPS) {
                        failures += "harita $mapId pad ${pad.id} @ $name: dokunma dairesinin " +
                            "ustu ${"%.1f".format(top)} dp, HUD alt kenari $hudDp dp " +
                            "-> ${"%.1f".format(hudDp - top)} dp ORTULU"
                    }
                }
            }
        }
        assertTrue(
            "ULASILAMAYAN BUILD PAD — HUD seridi dokunma dairesini yutuyor:\n" +
                failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun noTowerSpriteIsClippedByTheTopHud() {
        val failures = mutableListOf<String>()
        mapIds.forEach { mapId ->
            val pads = LevelData.forMapId(mapId).buildSpots
            screens.forEach { (name, w, h) ->
                val r = GameConfig.computeFieldRect(mapId, w, h, hudDp)
                val clear = GameConfig.PAD_TOP_CLEARANCE_REF_PX * r.renderScale
                pads.forEach { pad ->
                    val top = r.top + pad.normY * r.height - clear
                    if (top < hudDp - EPS) {
                        failures += "harita $mapId pad ${pad.id} @ $name: " +
                            "kule sprite'i ${"%.1f".format(hudDp - top)} dp kirpiliyor"
                    }
                }
            }
        }
        assertTrue(
            "KULE SPRITE'I HUD ALTINDA KIRPILIYOR:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun noPadTapCircleFallsBelowTheFieldBottom() {
        val failures = mutableListOf<String>()
        mapIds.forEach { mapId ->
            val pads = LevelData.forMapId(mapId).buildSpots
            screens.forEach { (name, w, h) ->
                val r = GameConfig.computeFieldRect(mapId, w, h, hudDp)
                val tapR = GameConfig.TAP_RADIUS_REF_PX * r.renderScale
                pads.forEach { pad ->
                    val bottom = r.top + pad.normY * r.height + tapR
                    if (bottom > h + EPS) {
                        failures += "harita $mapId pad ${pad.id} @ $name: dokunma dairesinin " +
                            "alti ${"%.1f".format(bottom)} dp, ekran ${h.toInt()} dp"
                    }
                }
            }
        }
        assertTrue(
            "PAD EKRANIN ALT KENARINDAN TASIYOR:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    /**
     * Dusmanin cikis agzi HUD'in altinda kalirsa oyuncu tehdidin NEREDEN
     * geldigini goremez. Yol her iki kol icin de (fork dahil) olculur.
     */
    @Test
    fun theTopOfEveryRouteStaysBelowTheTopHud() {
        val failures = mutableListOf<String>()
        mapIds.forEach { mapId ->
            val routes = LevelData.routesForMapId(mapId) +
                listOfNotNull(com.miniappfactory.frontlinedefender.game.model.LevelGeometry.ALT_ROUTES[mapId])
            screens.forEach { (name, w, h) ->
                val r = GameConfig.computeFieldRect(mapId, w, h, hudDp)
                val clear = GameConfig.PATH_TOP_CLEARANCE_REF_PX * r.renderScale
                routes.flatten().forEach { p ->
                    val top = r.top + p.y * r.height - clear
                    if (top < hudDp - EPS) {
                        failures += "harita $mapId @ $name: yolun ust ucu " +
                            "${"%.1f".format(hudDp - top)} dp HUD altinda"
                    }
                }
            }
        }
        assertTrue(
            "YOLUN UST UCU HUD ALTINDA:\n" + failures.distinct().joinToString("\n"),
            failures.isEmpty()
        )
    }

    /**
     * Guvenligin EKRANDAN BAGIMSIZ oldugunun ispati: `fieldTop`in HUD'a gore
     * kaydirmasi `s * fh`, pad'in ustu ise `(padY - pay) * fh` — `fh` iki
     * tarafta da carpan oldugu icin sadelesir. Bu test onu sayiyla gosterir:
     * ayni harita, cok farkli ekranlar, AYNI guvenlik payi orani.
     */
    @Test
    fun clearanceMarginIsIndependentOfScreenSize() {
        mapIds.forEach { mapId ->
            val margins = screens.map { (_, w, h) ->
                val r = GameConfig.computeFieldRect(mapId, w, h, hudDp)
                val tapR = GameConfig.TAP_RADIUS_REF_PX * r.renderScale
                val minPad = LevelData.forMapId(mapId).buildSpots.minOf {
                    r.top + it.normY * r.height - tapR
                }
                (minPad - hudDp) / r.height
            }
            margins.forEach {
                assertEquals(
                    "harita $mapId: guvenlik payi ORANI ekrandan bagimsiz olmali",
                    margins.first().toDouble(), it.toDouble(), 1e-4
                )
            }
        }
    }

    /**
     * KAYIT — degisikligin gercekte ne kadar yer maliyetine mal oldugu.
     * Eski sabit 0,10 ile yeni olculu oran yan yana yazilir; bir harita
     * geometrisi degisirse bu tablo da degisir ve karar yeniden gorulur.
     */
    @Test
    fun measuredSafeTopFractionMatchesGeometry() {
        val padFrac = GameConfig.refPxToHeightFrac(GameConfig.PAD_TOP_CLEARANCE_REF_PX)
        val pathFrac = GameConfig.refPxToHeightFrac(GameConfig.PATH_TOP_CLEARANCE_REF_PX)
        val rows = StringBuilder(
            "\nharita | enUstPad | enUstYol | olculenBant | eskiSabit |" +
                " en dar pad payi (dp): 740x360 / 640x360 / 891x411 | alan yuks. (dp) yeni/eski\n"
        )
        mapIds.forEach { mapId ->
            val data = LevelData.forMapId(mapId)
            val minPad = data.buildSpots.minOf { it.normY }
            val minPath = LevelData.routesForMapId(mapId).flatten().minOf { it.y }
            val s = GameConfig.mapSafeTopFrac(mapId)
            val expected = minOf(minPad - padFrac, minPath - pathFrac)
                .coerceIn(GameConfig.MAP_SAFE_TOP_FRAC_MIN, GameConfig.MAP_SAFE_TOP_FRAC_MAX)
            assertEquals("harita $mapId guvenli bant", expected.toDouble(), s.toDouble(), 1e-6)

            val margins = screens.map { (_, w, h) ->
                val r = GameConfig.computeFieldRect(mapId, w, h, hudDp)
                val tapR = GameConfig.TAP_RADIUS_REF_PX * r.renderScale
                data.buildSpots.minOf { r.top + it.normY * r.height - tapR } - hudDp
            }
            val newH = GameConfig.computeFieldRect(mapId, 740f, 360f, hudDp).height
            val oldH = (360f - hudDp) / (1f - 0.10f)
            rows.append(
                "  %2d   |  %.4f  |  %.4f  |   %+.4f   |   0.1000  | %+6.1f / %+6.1f / %+6.1f | %.0f / %.0f\n"
                    .format(mapId, minPad, minPath, s, margins[0], margins[1], margins[2], newH, oldH)
            )
        }
        println(rows)

        // En kotu harita gercekten HUD'un altina KUCULTULEREK sigiyor mu?
        assertTrue(
            "harita 10 (en ust pad normY=0,0691) icin bant NEGATIF olmali — " +
                "yani harita kaydirilamaz, kucultulur",
            GameConfig.mapSafeTopFrac(10) < 0f
        )
    }

    /**
     * ALT KENAR — OLCUM **VE** GERI BILDIRIM KILIDI.
     *
     * Ust HUD daimidir, alttaki iki cekmece ise GECICIDIR: `TowerBuildBar`
     * ([GameConfig.BUILD_DRAWER_HEIGHT_DP] = 63 dp) yalnizca bos bir pad
     * SECILINCE, `SelectedTowerInspector` (56 dp) yalnizca bir kule secilince
     * acilir. Yani bunlar oynanis alanini daimi olarak kucultmezler ama
     * SECILEN pad'i kendi cekmeceleri altinda birakabilirler — klasik
     * "parmak/panel hedefi ortuyor" durumu.
     *
     * Alani 63 dp daha kucultmek 740x360 dp'de yuksekligi %20 goturuyordu; bu
     * gecici bir panel icin kalici ve buyuk bir bedel — YAPILMADI. Bunun
     * yerine bilgi tasindi: ortulen hedef cekmecenin USTUNDE bagli cizgi +
     * hayalet plaka olarak tekrarlanir (`GameCanvas.drawOcclusionCallout`).
     * Bir kule karti basili tutuluyorsa plakada O KULENIN sprite'i cizilir,
     * yani birakma onizlemesi kaybolmaz.
     *
     * Bu test artik iki sey yapar:
     *  1. ORTMEYI olcer ve tabloyu basar (esik: harita basina <= 6),
     *  2. ortulen HER pad icin hayalet gostergenin TAMAMEN gorunur bir yere
     *     dustugunu KILITLER — HUD'un altinda, cekmecenin ustunde ve hedefin
     *     yukarisinda. Aksi halde "cozdum" iddiasi olculemezdi.
     */
    @Test
    fun bottomDrawerOcclusionIsMeasuredAndReported() {
        val drawerDp = GameConfig.BUILD_DRAWER_HEIGHT_DP
        val rows = StringBuilder(
            "\nALT CEKMECE ORTMESI (${drawerDp.toInt()} dp, yalnizca pad secilince acik)\n" +
                "harita | ekran | cekmece altinda kalan pad | hayalet gosterge y (dp)\n"
        )
        var worst = 0
        var occludedTotal = 0
        val unmitigated = mutableListOf<String>()

        mapIds.forEach { mapId ->
            val pads = LevelData.forMapId(mapId).buildSpots
            screens.forEach { (name, w, h) ->
                val r = GameConfig.computeFieldRect(mapId, w, h, hudDp)
                val tapR = GameConfig.TAP_RADIUS_REF_PX * r.renderScale
                val hit = pads.filter {
                    GameConfig.isOccludedByBottomDrawer(
                        targetScreenY = r.top + it.normY * r.height,
                        targetRadiusPx = tapR,
                        screenHeightPx = h,
                        drawerHeightPx = drawerDp
                    )
                }
                if (hit.isEmpty()) return@forEach

                worst = maxOf(worst, hit.size)
                occludedTotal += hit.size

                val calloutR = GameConfig.OCCLUSION_CALLOUT_RADIUS_REF_PX * r.renderScale
                val cy = GameConfig.occlusionCalloutY(h, drawerDp, hudDp, r.renderScale)

                // (a) Hayalet HUD'in altinda ve cekmecenin ustunde, TAMAMEN.
                if (cy - calloutR < hudDp - EPS) {
                    unmitigated += "harita $mapId @ $name: hayaletin ustu " +
                        "${"%.1f".format(cy - calloutR)} dp, HUD alt kenari $hudDp dp"
                }
                if (cy + calloutR > h - drawerDp + EPS) {
                    unmitigated += "harita $mapId @ $name: hayaletin alti " +
                        "${"%.1f".format(cy + calloutR)} dp, cekmece ust kenari " +
                        "${"%.1f".format(h - drawerDp)} dp"
                }
                // (b) Hayalet ortulen pad'in YUKARISINDA — bagli cizgi daima
                //     yukari gostersin, hedefin uzerine binmesin.
                hit.forEach { pad ->
                    val padY = r.top + pad.normY * r.height
                    if (cy >= padY - EPS) {
                        unmitigated += "harita $mapId pad ${pad.id} @ $name: hayalet " +
                            "${"%.1f".format(cy)} dp, pad ${"%.1f".format(padY)} dp — yukarida degil"
                    }
                }
                // (c) Yatayda alanin icinde kalabilmeli (kirpilmis hayalet olmaz).
                if (r.width < 2f * calloutR) {
                    unmitigated += "harita $mapId @ $name: alan genisligi " +
                        "${"%.1f".format(r.width)} dp, hayalet capi ${"%.1f".format(2f * calloutR)} dp"
                }

                rows.append(
                    "  %2d   | %-14s | %-24s | %.1f\n".format(
                        mapId, name, hit.joinToString(",") { "${it.id}" }, cy
                    )
                )
            }
        }
        println(rows)
        println("ORTULEN PAD TOPLAMI: $occludedTotal (11 harita x 3 ekran), hepsi hayalet gosterge aliyor")

        assertTrue(
            "alt cekmecenin ortugu pad sayisi harita basina 6'yi asti — " +
                "cekmece tasarimi yeniden ele alinmali (olculen en kotu: $worst)",
            worst <= 6
        )
        assertTrue(
            "ORTULEN PAD GERI BILDIRIMSIZ KALDI — hayalet gosterge gorunur bir " +
                "yere dusmuyor:\n" + unmitigated.distinct().joinToString("\n"),
            unmitigated.isEmpty()
        )
        assertTrue(
            "olcum bos olmamali: hicbir pad ortulmuyorsa bu test bir sey ispat etmiyor",
            occludedTotal > 0
        )
    }

    /**
     * TESTIN BOS OLMADIGININ KANITI — eski formul burada DUSER.
     *
     * Yukaridaki kilitler ancak gercek hatayi yakaliyorsa bir sey ifade eder.
     * Bu test eski sabit orani (0,10) elle yeniden kurar ve kullanicinin
     * cihazinda olculen ortulmeyi SAYIYLA gosterir: harita 4 / 740x360 dp /
     * HUD 56 dp -> ust pad'in dokunma dairesi ~10 dp HUD altinda.
     */
    @Test
    fun theOldFixedFractionWouldHaveFailedOnTheDeviceThatReportedTheBug() {
        val legacyFrac = 0.10f
        val w = 740f
        val h = 360f
        val mapId = 4

        val available = h - hudDp
        val fh = available / (1f - legacyFrac)
        val fw = fh * GameConfig.MAP_ASPECT_RATIO
        val top = hudDp - legacyFrac * fh
        val tapR = GameConfig.TAP_RADIUS_REF_PX * (fw / GameConfig.REFERENCE_WIDTH)
        val topPad = LevelData.forMapId(mapId).buildSpots.minByOrNull { it.normY }!!
        val legacyCircleTop = top + topPad.normY * fh - tapR
        val hidden = hudDp - legacyCircleTop

        assertTrue(
            "eski formul ortmuyorsa bu testler bos demektir — olculen: " +
                "${"%.1f".format(hidden)} dp",
            hidden > 5f
        )
        println(
            "ESKI FORMUL (harita 4, 740x360 dp, HUD 56 dp): pad ${topPad.id} " +
                "dokunma dairesinin ustu ${"%.1f".format(legacyCircleTop)} dp " +
                "-> ${"%.1f".format(hidden)} dp HUD ALTINDA (dokunulamaz)"
        )

        // AYNI durum yeni formulle: pay POZITIF olmali.
        val r = GameConfig.computeFieldRect(mapId, w, h, hudDp)
        val fixedCircleTop = r.top + topPad.normY * r.height -
            GameConfig.TAP_RADIUS_REF_PX * r.renderScale
        assertTrue(
            "yeni formul ayni pad'i HUD altindan cikarmali",
            fixedCircleTop >= hudDp
        )
        println(
            "YENI FORMUL: ayni pad'in dokunma dairesinin ustu " +
                "${"%.1f".format(fixedCircleTop)} dp (HUD alt kenari $hudDp dp), " +
                "alan yuksekligi ${"%.1f".format(r.height)} dp " +
                "(eski ${"%.1f".format(fh)} dp)"
        )
    }

    private companion object {
        /** Float toplamalarinin yuvarlama gurultusu. */
        const val EPS = 0.01f
    }
}
