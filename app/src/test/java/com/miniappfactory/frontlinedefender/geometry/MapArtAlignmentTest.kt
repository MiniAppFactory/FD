package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.LevelGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * ===========================================================================
 * SANAT = OYNANIS — "boyali yol ile yurunen rota ayni yerde mi?"
 * ===========================================================================
 *
 * NEDEN VAR (cihaz geri bildirimi + rota ajaninin olcumu)
 * -------------------------------------------------------
 * Kullanici: *"yoldan gelmeyen askerler var."* Rota ajani rotayi yol maskesine
 * oturttu (govdesi cime tasan oran %19,7 -> %13,6) ama kalan payin baska bir
 * yerden geldigini raporladi: harita bitmap'i oynanis dikdortgeninden
 * `SHAKE_OVERSCAN_REF_PX = 10` ref-px TASIRILARAK, yani GERILEREK ciziliyordu.
 *
 *     sanatX = (sol - tasma) + nx * (genislik + 2*tasma)
 *     oynanisX = sol + nx * genislik
 *     fark    = tasma * (2*nx - 1)
 *
 * Yani merkezde 0, sol/sag kenarda 10 ref-px, kosede 14,1 ref-px SISTEMATIK
 * kayma. Rota dogru yerdeydi; **harita yanlis yerde ciziliyordu.** Kayma
 * sureklidir — sarsinti olmayan karelerde de.
 *
 * BU DOSYA NEYI KILITLER
 * ----------------------
 *  1. Verilen bir normalize koordinat, harita ciziminde
 *     (`GameConfig.mapArtRect`) ve oynanis hesabinda
 *     (`GameConfig.normXToScreen` / `normYToScreen`) AYNI ekran noktasina
 *     duser — merkezde de, KOSEDE de, 11 harita x 3 ekran icin.
 *  2. Ayni sey haritalarin GERCEK icerigi icin de gecerlidir: her build
 *     pad'i ve her rota noktasi (fork kollari dahil).
 *  3. Sarsinti kenarinda serit acilmaz: kenar sivamasi
 *     (`GameConfig.mapEdgeBleedPx`) sarsintinin en buyuk genligini kapatir.
 *  4. Test BOS DEGILDIR: eski tasma formulu burada elle yeniden kurulur ve
 *     duser.
 */
class MapArtAlignmentTest {

    /** `MapLayoutSafetyTest` ile ayni gercek cihaz olculeri (dp). */
    private val screens = listOf(
        Triple("S8 740x360", 740f, 360f),
        Triple("16:9 640x360", 640f, 360f),
        Triple("19.5:9 891x411", 891f, 411f)
    )

    private val hudDp = GameConfig.HUD_TOP_INSET_DP
    private val mapIds = GameConfig.SHIPPED_MAP_IDS.sorted()

    /** Eski (hatali) tasma sabiti — yalnizca ispat testinde kullanilir. */
    private val legacyOverscanRefPx = 10f

    // =======================================================================

    /**
     * KILIT — kayma SIFIR. Kose dahil.
     *
     * Olcum ref-px cinsinden raporlanir cunku "yolun yarim genisligi ~45
     * ref-px" gibi oynanis buyuklukleriyle ancak o birimde karsilastirilabilir.
     */
    @Test
    fun artAndGameplayLandOnTheSameScreenPointEverywhere() {
        val probes = listOf(
            "sol-ust" to Pair(0f, 0f),
            "sag-ust" to Pair(1f, 0f),
            "sol-alt" to Pair(0f, 1f),
            "sag-alt" to Pair(1f, 1f),
            "merkez" to Pair(0.5f, 0.5f),
            "sol-orta" to Pair(0f, 0.5f),
            "ust-orta" to Pair(0.5f, 0f)
        )
        val failures = mutableListOf<String>()
        var worstRefPx = 0f

        mapIds.forEach { mapId ->
            screens.forEach { (name, w, h) ->
                val field = GameConfig.computeFieldRect(mapId, w, h, hudDp)
                val art = GameConfig.mapArtRect(field)
                probes.forEach { (label, p) ->
                    val (nx, ny) = p
                    // Harita bitmap'i `art` dikdortgenine cizilir; icindeki
                    // normalize nx bu yuzden art.left + nx*art.width'e duser.
                    val artX = art.left + nx * art.width
                    val artY = art.top + ny * art.height
                    val playX = GameConfig.normXToScreen(field, nx)
                    val playY = GameConfig.normYToScreen(field, ny)
                    val driftPx = hypot((artX - playX).toDouble(), (artY - playY).toDouble())
                    val driftRef = (driftPx / field.renderScale).toFloat()
                    worstRefPx = maxOf(worstRefPx, driftRef)
                    if (driftPx > 1e-3) {
                        failures += "harita $mapId @ $name $label: " +
                            "kayma ${"%.3f".format(driftPx)} px " +
                            "(${"%.2f".format(driftRef)} ref-px)"
                    }
                }
            }
        }

        println(
            "SANAT/OYNANIS KAYMASI (sonrasi): en kotu " +
                "${"%.4f".format(worstRefPx)} ref-px — 11 harita x 3 ekran x 7 nokta"
        )
        assertTrue(
            "SANAT ILE OYNANIS AYRI KOORDINAT UZAYINDA:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    /**
     * Ayni kilit, ama sentetik nokta yerine haritalarin GERCEK icerigi: her
     * build pad'i ve her rota noktasi (ikinci kol dahil). Sentetik kose
     * gecerken gercek geometride kayma cikmasi mumkun olmamali; bu test onu
     * sayiyla kapatir.
     */
    @Test
    fun everyPadAndWaypointDrawsWhereGameplayPutsIt() {
        var checked = 0
        var worst = 0.0
        mapIds.forEach { mapId ->
            val pads = LevelData.forMapId(mapId).buildSpots
            val routes = LevelData.routesForMapId(mapId) +
                listOfNotNull(LevelGeometry.ALT_ROUTES[mapId])
            screens.forEach { (_, w, h) ->
                val field = GameConfig.computeFieldRect(mapId, w, h, hudDp)
                val art = GameConfig.mapArtRect(field)
                pads.forEach { pad ->
                    worst = maxOf(worst, drift(art, field, pad.normX, pad.normY))
                    checked++
                }
                routes.flatten().forEach { p ->
                    worst = maxOf(worst, drift(art, field, p.x, p.y))
                    checked++
                }
            }
        }
        println("GERCEK GEOMETRI: $checked nokta, en kotu kayma ${"%.5f".format(worst)} px")
        assertTrue("pad/rota noktalari haritayla ortusmuyor: $worst px", worst < 1e-3)
    }

    /**
     * SARSINTI KENARI — sivama genligi kapatmali.
     *
     * Sarsinti tum sahneyi (harita + kule + dusman + efekt) BIRLIKTE oteler
     * (GameCanvas'ta tek `withTransform`), yani kenarda acilan tek sey
     * haritanin disindaki letterbox/ekran zeminidir. Sivama bandi bu acilmayi
     * kapatir; sart, bandin sarsintinin en buyuk genliginden kucuk olmamasi.
     */
    @Test
    fun edgeBleedCoversTheWorstCaseShake() {
        val failures = mutableListOf<String>()
        val rows = StringBuilder("\nKENAR SIVAMASI vs SARSINTI\nharita | ekran | sivama px | sarsinti px\n")
        mapIds.forEach { mapId ->
            screens.forEach { (name, w, h) ->
                val field = GameConfig.computeFieldRect(mapId, w, h, hudDp)
                val bleed = GameConfig.mapEdgeBleedPx(field.renderScale)
                if (bleed < GameConfig.SHAKE_MAX_AMPLITUDE_PX * GameConfig.MAP_EDGE_BLEED_SAFETY - 1e-4f) {
                    failures += "harita $mapId @ $name: sivama $bleed px < " +
                        "sarsinti ${GameConfig.SHAKE_MAX_AMPLITUDE_PX} px"
                }
                if (mapId == 1) {
                    rows.append(
                        "  %2d   | %-14s | %8.2f | %8.2f\n".format(
                            mapId, name, bleed, GameConfig.SHAKE_MAX_AMPLITUDE_PX
                        )
                    )
                }
            }
        }
        println(rows)
        assertTrue(
            "SARSINTIDA KENAR ACILIR — sivama bandi yetersiz:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    /**
     * Sarsinti genligi motorun formulunden TURETILMIS olmali; iki sayi
     * ayrisirsa yukaridaki kapsama iddiasi sessizce yalan olur.
     */
    @Test
    fun shakeAmplitudeMatchesTheEngineFormula() {
        // GameEngine.updateScreenShake: intensity = min(sure * RAMP, MAX),
        // sapma = rand*intensity - intensity/2  ->  [-MAX/2, +MAX/2]
        assertEquals(
            "sarsinti genligi = en buyuk siddetin yarisi",
            (GameConfig.SHAKE_MAX_INTENSITY_PX / 2f).toDouble(),
            GameConfig.SHAKE_MAX_AMPLITUDE_PX.toDouble(),
            1e-6
        )
        assertTrue("sarsinti rampasi pozitif", GameConfig.SHAKE_INTENSITY_RAMP_PER_SECOND > 0f)
    }

    /**
     * TESTIN BOS OLMADIGININ KANITI — eski tasma formulu burada DUSER.
     *
     * Kullanicinin cihazi (harita 4, 740x360 dp, HUD 56 dp) uzerinden olculur
     * ve kaymanin yol yarim genisligine orani yazilir.
     */
    @Test
    fun theOldOverscanWouldHaveDriftedTenRefPxAtTheEdges() {
        val mapId = 4
        val field = GameConfig.computeFieldRect(mapId, 740f, 360f, hudDp)
        val over = legacyOverscanRefPx * field.renderScale

        fun legacyArtX(nx: Float) = (field.left - over) + nx * (field.width + 2f * over)
        fun legacyArtY(ny: Float) = (field.top - over) + ny * (field.height + 2f * over)

        val centerDrift = hypot(
            (legacyArtX(0.5f) - GameConfig.normXToScreen(field, 0.5f)).toDouble(),
            (legacyArtY(0.5f) - GameConfig.normYToScreen(field, 0.5f)).toDouble()
        )
        val edgeDrift = abs(legacyArtX(0f) - GameConfig.normXToScreen(field, 0f))
        val cornerDrift = hypot(
            (legacyArtX(0f) - GameConfig.normXToScreen(field, 0f)).toDouble(),
            (legacyArtY(0f) - GameConfig.normYToScreen(field, 0f)).toDouble()
        )

        val edgeRef = edgeDrift / field.renderScale
        val cornerRef = (cornerDrift / field.renderScale).toFloat()

        println(
            "ESKI TASMA (harita 4, 740x360 dp): merkez " +
                "${"%.3f".format(centerDrift)} px / kenar ${"%.2f".format(edgeRef)} ref-px / " +
                "kose ${"%.2f".format(cornerRef)} ref-px"
        )

        assertTrue("eski formul merkezde kaymamaliydi", centerDrift < 1e-3)
        assertEquals("eski formul kenarda tam tasma kadar kayardi", 10.0, edgeRef.toDouble(), 0.01)
        assertTrue("eski formul kosede ~14 ref-px kayardi", cornerRef > 13.5f)

        // ... ve yeni formul ayni yerlerde SIFIR.
        val art = GameConfig.mapArtRect(field)
        assertEquals(
            "yeni formul kosede sifir kaymali",
            0.0,
            hypot(
                (art.left - GameConfig.normXToScreen(field, 0f)).toDouble(),
                (art.top - GameConfig.normYToScreen(field, 0f)).toDouble()
            ),
            1e-4
        )
    }

    // =======================================================================

    private fun drift(
        art: GameConfig.MapFieldRect,
        field: GameConfig.MapFieldRect,
        nx: Float,
        ny: Float
    ): Double = hypot(
        ((art.left + nx * art.width) - GameConfig.normXToScreen(field, nx)).toDouble(),
        ((art.top + ny * art.height) - GameConfig.normYToScreen(field, ny)).toDouble()
    )
}
