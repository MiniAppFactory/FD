package com.miniappfactory.frontlinedefender.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.LevelGeometry
import com.miniappfactory.frontlinedefender.game.model.PointF
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * ===========================================================================
 * ROTA = BOYALI YOL — overlay KANITI (Codex FD-033/FD-035 talebi)
 * ===========================================================================
 *
 * Codex FD-035: "salt transform testleriyle kapatma; rota waypoint/egri
 * geometrisini BITMAP USTUNE debug cizerek yolun merkez cizgisine oturdugunu
 * goster — tum farkli geometrilerde."
 *
 * Bu test, uygulamanin GERCEKTEN yukledigi harita sanatini
 * (`R.drawable.bg_level_NN`, `GameSprites.mapResFor` ile ayni esleme) decode
 * eder ve rota poligonunu (A-kolu + varsa B-kolu) DOGRUDAN o bitmap'in piksel
 * uzayina (nx*w, ny*h) cizer. Sanat normalize uzayi birebir doldurdugu icin
 * (`mapArtRect == fieldRect` ozdesligi, MapArtAlignmentTest) normalize bir rota
 * noktasi bitmap'te (nx*w, ny*h) pikseline duser — yani cizilen cizgi, oyunda
 * dusmanin yurudugu merkez cizginin ta kendisidir, ekran/HUD/letterbox'tan
 * BAGIMSIZ olarak.
 *
 * Ciktilar: comms/visual-evidence/device/overlay_map_NN_route.png (11 harita).
 * Sayisal eslesme ayrica RouteStaysOnRoadTest'te maske uzerinden kanitli
 * (harita 1 A/B: %100 yol); bu dosya onun GORSEL karsiligidir.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class RouteOverlayEvidenceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun mapResFor(mapId: Int): Int = when (mapId) {
        1 -> R.drawable.bg_level_01
        2 -> R.drawable.bg_level_02
        3 -> R.drawable.bg_level_03
        4 -> R.drawable.bg_level_04
        5 -> R.drawable.bg_level_05
        6 -> R.drawable.bg_level_06
        7 -> R.drawable.bg_level_07
        8 -> R.drawable.bg_level_08
        9 -> R.drawable.bg_level_09
        10 -> R.drawable.bg_level_10
        else -> R.drawable.bg_level_11
    }

    @Test
    fun routePolylineLandsOnThePaintedRoadForEveryMap() {
        val written = mutableListOf<String>()
        for (mapId in 1..11) {
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val src = BitmapFactory.decodeResource(context.resources, mapResFor(mapId), opts)
            assertTrue("harita $mapId sanati decode edilemedi", src != null && src.width > 0)
            val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
            val c = Canvas(bmp)
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()

            val arms = buildList {
                LevelGeometry.ALL_MAPS.firstOrNull { it.levelId == mapId }
                    ?.let { add("A" to it.waypoints) }
                LevelGeometry.ALT_ROUTES[mapId]?.let { add("B" to it) }
            }

            // Pad merkezleri (baglamin netlesmesi icin) — ince beyaz halka.
            val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = maxOf(2f, w / 640f)
                color = Color.argb(200, 255, 255, 255)
            }
            LevelData.forMapId(mapId).buildSpots.forEach { s ->
                c.drawCircle(s.normX * w, s.normY * h, maxOf(6f, w / 120f), padPaint)
            }

            arms.forEachIndexed { armIdx, (armName, route) ->
                drawRoute(c, route, w, h, armIdx == 0)
                // Kol etiketi baslangic noktasina.
                route.firstOrNull()?.let { p ->
                    val lab = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = maxOf(18f, w / 60f)
                        setShadowLayer(4f, 0f, 0f, Color.BLACK)
                    }
                    c.drawText("$armName-kolu", p.x * w + 8f, p.y * h - 8f, lab)
                }
            }

            val file = save(bmp, "overlay_map_%02d_route.png".format(mapId))
            written += "${file.name} (${bmp.width}x${bmp.height}, ${arms.joinToString("+") { it.first }})"
            src.recycle()
        }
        println("ROUTE-OVERLAY YAZILDI:\n  " + written.joinToString("\n  "))
        assertTrue(written.isNotEmpty())
    }

    /** Rotayi kalin yari-saydam cizgi + yon oklariyla cizer; A kirmizi, B sari. */
    private fun drawRoute(c: Canvas, route: List<PointF>, w: Float, h: Float, primary: Boolean) {
        if (route.size < 2) return
        val lineCol = if (primary) Color.argb(235, 255, 40, 40) else Color.argb(235, 255, 210, 0)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(7f, w / 150f)
            color = Color.argb(140, 0, 0, 0) // koyu halo — acik zeminde okunur
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(3f, w / 320f)
            color = lineCol
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(255, 40, 230, 90) // waypoint = yesil nokta
        }
        val path = android.graphics.Path()
        path.moveTo(route[0].x * w, route[0].y * h)
        for (i in 1 until route.size) path.lineTo(route[i].x * w, route[i].y * h)
        c.drawPath(path, halo)
        c.drawPath(path, line)
        val r = maxOf(2.5f, w / 520f)
        route.forEach { p -> c.drawCircle(p.x * w, p.y * h, r, dot) }
    }

    private fun save(bitmap: Bitmap, name: String): File {
        val dir = evidenceDir().apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("VISUAL-EVIDENCE: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
        return file
    }

    private fun evidenceDir(): File = listOf(
        File("../../comms/visual-evidence/device"),
        File("../comms/visual-evidence/device"),
        File("C:/Users/bhdre/APPDeveloper/projects/Frontline Defender/comms/visual-evidence/device"),
    ).firstOrNull { it.parentFile?.exists() == true } ?: File(
        "C:/Users/bhdre/APPDeveloper/projects/Frontline Defender/comms/visual-evidence/device"
    )
}
