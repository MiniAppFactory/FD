package com.miniappfactory.frontlinedefender.geometry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ===========================================================================
 * HARITA KAYMASI — HUD YUKSEKLIGI OLCULUNCE ROTA/PAD HARITAYLA AYRISIYOR MU?
 * ===========================================================================
 *
 * BULUNAN HATA (2026-10-11, cihazda: SM-G950F, dalga 1 — "harita kaymis,
 * dusmanlar yoldan gitmiyor"):
 *
 * `GameScreen` ust HUD seridinin yuksekligini ONCE bir TAHMINLE baslatir
 * (`GameConfig.HUD_TOP_INSET_DP`), sonra HUD gercekten olculunce
 * (`Modifier.onSizeChanged`) GERCEK yukseklige gunceller. Bu degisim
 * `GameCanvas`ten `updateMapDimensions(..., topInsetPx)`e akar ve `fieldRect`i
 * yeniden hesaplatir — harita bitmap'i `mapArtRect(fieldRect)` = YENI
 * dikdortgene cizilir.
 *
 * ESKI HATA: `updateMapDimensions` yalniz `fieldRect`i ve (stale
 * `scaledBuildSpots`tan) kuleleri guncelliyor, ama `scaledRoutes` /
 * `scaledBuildSpots`i YENIDEN OLCEKLEMIYORDU (`rescaleGeometry` sadece
 * `loadLevel`de cagriliyordu). Sonuc: harita YENI dikdortgende, rota/pad ESKI
 * (tahmine dayali) dikdortgende — kalici DIKEY kayma.
 *
 * BU TEST NEYI KILITLIYOR: bir bolum TAHMIN inset'iyle yuklendikten SONRA
 * topInset degisirse, `scaledBuildSpots` ve `scaledRoutes` guncel `fieldRect`
 * uzerinden (`normXToScreen`/`normYToScreen`) turetilmis degerlerle BIREBIR
 * ortusmeli — yani sanat, rota ve pad ayni dunya->ekran donusumunu paylasir.
 * Duzeltme geri alinirsa sapma on/yuzlerce piksele cikar ve test duser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land-xxhdpi")
class MapShiftOnHudInsetChangeTest {

    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        audio = AudioManager(ctx)
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(ctx), audio)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    /** Pad/rota, GUNCEL fieldRect'ten turetilen beklenen ekran noktasina oturuyor mu? */
    private fun geometryMatchesCurrentField(level: Int): Float {
        val f = engine.fieldRect
        val mapId = GameConfig.levelSpec(level).mapId.let {
            if (it in GameConfig.SHIPPED_MAP_IDS) it else GameConfig.MAP_FALLBACK_ID
        }
        val data = LevelData.forMapId(mapId)

        var worst = 0f

        // Pad'ler: scaledBuildSpots, veri normX/normY'nin GUNCEL fieldRect'teki
        // izdusumune esit olmali.
        engine.scaledBuildSpots.forEach { spot ->
            val src = data.buildSpots.firstOrNull { it.id == spot.id } ?: return@forEach
            val ex = GameConfig.normXToScreen(f, src.normX)
            val ey = GameConfig.normYToScreen(f, src.normY)
            worst = maxOf(worst, kotlin.math.abs(spot.normX - ex), kotlin.math.abs(spot.normY - ey))
        }

        // Rota noktalari: scaledRoutes, veri rotasinin GUNCEL fieldRect'teki
        // izdusumune esit olmali.
        val routes = LevelData.routesForMapId(mapId)
        engine.scaledRoutes.forEachIndexed { ri, route ->
            val src = routes.getOrNull(ri) ?: return@forEachIndexed
            route.forEachIndexed { pi, p ->
                val sp = src.getOrNull(pi) ?: return@forEachIndexed
                val ex = GameConfig.normXToScreen(f, sp.x)
                val ey = GameConfig.normYToScreen(f, sp.y)
                worst = maxOf(worst, kotlin.math.abs(p.x - ex), kotlin.math.abs(p.y - ey))
            }
        }
        return worst
    }

    /**
     * ESAS GARANTI: bolum TAHMIN inset'iyle yuklenir, sonra HUD olculup inset
     * DEGISIR; rota ve pad'ler yeni fieldRect'e tasinir, harita ile ayrismaz.
     */
    @Test
    fun routesAndPadsTrackFieldRectAfterHudInsetMeasured() {
        // 1) Gercek ekran, ama inset TAHMINI (HUD daha olculmedi) — cold giris.
        val estimateInset = with(org.robolectric.RuntimeEnvironment.getApplication().resources.displayMetrics) {
            GameConfig.HUD_TOP_INSET_DP * density
        }
        engine.updateMapDimensions(2220f, 1080f, estimateInset)
        engine.startNewGame(1)
        // Dalga baslat + birkac kare: canli dusmanlar olussun (remap da calissin).
        engine.startNextWaveNow()
        repeat(120) { engine.tick(1f / 60f) }

        val fieldAtEstimate = engine.fieldRect

        // 2) HUD olculdu: GERCEK yukseklik tahminden FARKLI (onSizeChanged).
        val measuredInset = estimateInset + 48f
        engine.updateMapDimensions(2220f, 1080f, measuredInset)
        val fieldAfter = engine.fieldRect

        // Senaryonun hatayi gercekten uretebilmesi icin dikdortgen degismeli.
        assertTrue(
            "fieldRect degismedi (top %.1f->%.1f, h %.1f->%.1f) — senaryo hatayi uretmiyor".format(
                fieldAtEstimate.top, fieldAfter.top, fieldAtEstimate.height, fieldAfter.height
            ),
            kotlin.math.abs(fieldAfter.top - fieldAtEstimate.top) > 1f ||
                kotlin.math.abs(fieldAfter.height - fieldAtEstimate.height) > 1f
        )

        // 3) Rota/pad GUNCEL fieldRect'e oturmali (sanat da ayni rect'e cizilir).
        val worst = geometryMatchesCurrentField(1)
        assertTrue(
            "rota/pad harita dikdortgeninden kaydi: en kotu sapma %.2f px (duzeltme yoksa buyur)".format(worst),
            worst < 0.5f
        )
    }

    /**
     * KARSIT ORNEK — rescaleGeometry CAGRILMASAYDI sapma buyuk olurdu.
     *
     * Rota/pad'in ESKI (tahmin) fieldRect'e gore degerleri YENI fieldRect'ten
     * beklenen degerlerle karsilastirilir; fark olcum gurultusunun cok
     * uzerinde olmali, yoksa senaryo hatayi uretmiyordur.
     */
    @Test
    fun withoutRescaleTheGeometryWouldDriftFromTheArt() {
        val estimateInset = with(org.robolectric.RuntimeEnvironment.getApplication().resources.displayMetrics) {
            GameConfig.HUD_TOP_INSET_DP * density
        }
        engine.updateMapDimensions(2220f, 1080f, estimateInset)
        engine.startNewGame(1)
        val oldField = engine.fieldRect

        // Rota/pad'in TAHMIN rect'teki degerlerini yakala.
        val padsOld = engine.scaledBuildSpots.map { Triple(it.id, it.normX, it.normY) }
        assertTrue("pad yok", padsOld.isNotEmpty())

        val mapId = GameConfig.levelSpec(1).mapId.let {
            if (it in GameConfig.SHIPPED_MAP_IDS) it else GameConfig.MAP_FALLBACK_ID
        }
        val data = LevelData.forMapId(mapId)

        // Inset degisince harita YENI rect'e cizilir; rescale OLMASAYDI pad'ler
        // padsOld'ta kalirdi. O eski degerlerin YENI rect'e gore beklenenden
        // sapmasi ne kadar?
        val measuredInset = estimateInset + 48f
        engine.updateMapDimensions(2220f, 1080f, measuredInset)
        val newField = engine.fieldRect

        var worst = 0f
        padsOld.forEach { (id, ox, oy) ->
            val src = data.buildSpots.firstOrNull { it.id == id } ?: return@forEach
            val ex = GameConfig.normXToScreen(newField, src.normX)
            val ey = GameConfig.normYToScreen(newField, src.normY)
            worst = maxOf(worst, kotlin.math.abs(ox - ex), kotlin.math.abs(oy - ey))
        }
        assertTrue(
            "tahmin rect degerleri yeni rect'ten yalniz %.2f px sapiyor (eski %.1f->yeni %.1f top) — senaryo zayif"
                .format(worst, oldField.top, newField.top),
            worst > 5f
        )
    }
}
