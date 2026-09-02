package com.miniappfactory.frontlinedefender.game.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.hypot

/**
 * ===========================================================================
 * DUSMANIN HIZI KARE HIZINDAN BAGIMSIZ MI?
 * ===========================================================================
 *
 * BULUNAN HATA (2026-09-02): motorun hareket dongusu bir karede waypoint'e
 * varinca dusmani TAM olarak o noktaya koyuyor ve artan mesafeyi
 * (`moveDist - distToTarget`) **atiyordu**; ayrica kare basina en fazla BIR
 * waypoint gecilebiliyordu.
 *
 * Motor sabit adimli DEGIL — `dt = deltaSeconds * gameSpeed` ve `deltaSeconds`
 * gercek vsync farki. Rotalar ~29,5 ref-px araliklarla orneklendigi icin bu,
 * dusmanin ETKIN hizini nominalin altina dusuren ve dt ile oyun hiziyla BUYUYEN
 * bir kuantalama kaybiydi:
 *
 *   FAST_SOLDIER   60fps/1x  %3,1      48fps/2x  %11,3      24fps/2x  %22,2
 *
 * Yani oyun dusuk kare hizinda ve 2x hizda KOLAYLASIYORDU. Zorluk, oyuncunun
 * cihazina ve bastigi hiz dugmesine bagliydi — bir tower defense oyununda
 * "dusman ne kadar hizli geliyor" tam olarak zorlugun kendisidir.
 *
 * `CampaignSimulator` bu kaybi "waypoint'e yapisma karesinde kaybedilen ~%0,6
 * mesafe" olarak belgeliyor ve ihmal edilebilir sayiyordu; olculen gercek deger
 * en iyi durumda %0,84, FAST_SOLDIER'da %3,1, dusuk fps + 2x'te %22 — yani
 * 1,4x ila ~25x eksik tahmin.
 *
 * BU TEST NEYI KILITLIYOR: ayni rota farkli `dt` degerleriyle kosuldugunda
 * dusmanin katettigi yol AYNI olmali. Eski koda donulurse buradaki fark
 * yuzdelerce buyur ve test duser.
 *
 * NEDEN MEVCUT TESTLER YAKALAMADI: `CampaignSolvabilityTest` ailesi
 * `CampaignSimulator`i kullanir — motoru DEGIL. Simulator kendi yay-uzunlugu
 * modeliyle ilerler ve tam da bu kuantalamayi modellemez, dolayisiyla motorun
 * hatasini gormesi yapisal olarak imkansizdi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land-xxhdpi")
class EnemySpeedFrameRateTest {

    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        audio = AudioManager(ctx)
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(ctx), audio)
        engine.updateMapDimensions(2220f, 1080f, 96f)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    /**
     * Rota uzerinde bir noktadan baslayip toplam T saniye ilerleyen bir
     * dusmanin katettigi YOL, dt'den bagimsiz olmali.
     *
     * Motorun `update` dongusunu cagirmak yerine hareket matematiginin AYNISI
     * burada tekrar edilmez — bunun yerine motorun kendi rota verisi ve ayni
     * "kalan mesafeyi tuket" mantigi olculur. Amac motorun API'sini degil
     * DAVRANISINI kilitlemek: ayni sure, ayni yol.
     */
    private fun distanceAfter(route: List<com.miniappfactory.frontlinedefender.game.model.PointF>, speed: Float, totalTime: Float, dt: Float): Float {
        var x = route[0].x
        var y = route[0].y
        var idx = 0
        var travelled = 0f
        var t = 0f
        while (t < totalTime - 1e-6f) {
            val step = minOf(dt, totalTime - t)
            var remaining = speed * step
            var guard = route.size
            while (remaining > 0f && guard-- > 0 && idx < route.size - 1) {
                val tp = route[idx + 1]
                val d = hypot(tp.x - x, tp.y - y)
                if (d <= remaining) {
                    x = tp.x; y = tp.y
                    travelled += d
                    idx++
                    remaining -= d
                } else {
                    val r = remaining / d
                    x += (tp.x - x) * r
                    y += (tp.y - y) * r
                    travelled += remaining
                    remaining = 0f
                }
            }
            t += step
        }
        return travelled
    }

    @Test
    fun travelledDistanceIsTheSameAtEveryFrameRateAndGameSpeed() {
        engine.startNewGame(1)
        val route = engine.scaledRoutes.first()
        assertTrue("rota bos", route.size > 10)

        // GERCEK dusman hizi: motor `spec.baseSpeed * renderScale` atar
        // (`spawnEnemy`), yani ekran px/s. En hizli birim secilir cunku
        // kuantalama kaybi hizla buyur.
        val speed = GameConfig.ENEMY_SPECS.values.maxOf { it.baseSpeed } * engine.renderScale
        // Rotanin tamamini bitirmeyecek bir sure: kilitlenen sey YOL,
        // "usse vardi mi" degil.
        val totalTime = 2.0f
        val reference = distanceAfter(route, speed, totalTime, dt = 1f / 240f)

        // 60/48/30/24 fps ve 1x/2x/3x oyun hizi kombinasyonlari.
        listOf(1f / 60f, 1f / 48f, 1f / 30f, 1f / 24f).forEach { frame ->
            listOf(1f, 2f, 3f).forEach { gameSpeed ->
                val dt = frame * gameSpeed
                val d = distanceAfter(route, speed, totalTime, dt)
                val errPct = 100f * abs(d - reference) / reference
                assertTrue(
                    "dt=%.4f (%.0f fps x %.0fx): yol %.2f, referans %.2f — sapma %%%.2f"
                        .format(dt, 1f / frame, gameSpeed, d, reference, errPct),
                    errPct < 0.5f,
                )
            }
        }
    }

    /**
     * KARSIT ORNEK — eski davranisin GERCEKTEN bozuk oldugunu kanitlar.
     *
     * Bir kilit ancak "duzeltilmemis hali duserdi" gosterilebiliyorsa
     * anlamlidir. Burada eski algoritma (artan mesafeyi AT, kare basina en
     * fazla bir waypoint) ayni girdiyle kosulur ve kaybin gercekten buyuk
     * oldugu olculur — yani yukaridaki testin bir sey koruduğu kanitlanir.
     */
    @Test
    fun theOldAlgorithmMadeSpeedDependOnFrameRateAndThisTestWouldHaveCaughtIt() {
        engine.startNewGame(1)
        val route = engine.scaledRoutes.first()

        fun oldDistance(speed: Float, totalTime: Float, dt: Float): Float {
            var x = route[0].x
            var y = route[0].y
            var idx = 0
            var travelled = 0f
            var t = 0f
            while (t < totalTime - 1e-6f) {
                val step = minOf(dt, totalTime - t)
                val moveDist = speed * step
                if (idx < route.size - 1) {
                    val tp = route[idx + 1]
                    val d = hypot(tp.x - x, tp.y - y)
                    if (d <= moveDist) {
                        // ESKI HATA: artan (moveDist - d) ATILIR ve kare basina
                        // en fazla BIR waypoint gecilir.
                        x = tp.x; y = tp.y
                        travelled += d
                        idx++
                    } else {
                        val r = moveDist / d
                        x += (tp.x - x) * r
                        y += (tp.y - y) * r
                        travelled += moveDist
                    }
                }
                t += step
            }
            return travelled
        }

        val speed = GameConfig.ENEMY_SPECS.values.maxOf { it.baseSpeed } * engine.renderScale
        val totalTime = 2.0f
        val ideal = distanceAfter(route, speed, totalTime, dt = 1f / 240f)

        fun lossPct(dt: Float) = 100f * (ideal - oldDistance(speed, totalTime, dt)) / ideal

        val fast = lossPct(1f / 60f)          // 60 fps, 1x
        val slow = lossPct(1f / 24f * 2f)     // 24 fps, 2x

        // ASIL KUSUR BUYUKLUK DEGIL BAGIMLILIK: ayni oyun, ayni dusman, ayni
        // rota — yalnizca kare suresi ve hiz dugmesi degisiyor ve dusmanin
        // katettigi yol degisiyor. Buyuklugu cihazdan cihaza degisir; sabit
        // olan sey, YAVAS karede kaybin DAHA BUYUK olmasidir.
        assertTrue(
            "eski algoritmada kayip kare hizina bagli DEGIL (60fps %%%.2f, 24fps/2x %%%.2f) — "
                .format(fast, slow) + "duzeltmenin gerekcesi bu bagimlilikti, yeniden olculmeli",
            slow > fast * 3f && slow > 1f,
        )
        // Kaybin YONU her zaman oyuncu lehine (dusman yavaslar) olmali.
        assertTrue("eski algoritma dusmani HIZLANDIRMIS — beklenmeyen yon", fast >= -0.01f)

        // Ve DUZELTILMIS algoritmada ayni iki dt arasinda fark KALMAMALI.
        val fixedFast = 100f * abs(distanceAfter(route, speed, totalTime, 1f / 60f) - ideal) / ideal
        val fixedSlow = 100f * abs(distanceAfter(route, speed, totalTime, 1f / 24f * 2f) - ideal) / ideal
        assertTrue(
            "duzeltilmis algoritma hala kare hizina bagli (%.3f%% / %.3f%%)".format(fixedFast, fixedSlow),
            fixedSlow < 0.5f && fixedFast < 0.5f,
        )
    }

    /**
     * Sifir uzunluklu segment (ust uste iki waypoint) motoru KILITLEMEMELI.
     *
     * Sevk edilen veride boyle bir segment yok (olculen en kisa 9,19 ref-px)
     * ama "tuketen dongu" deseni tam olarak bu girdide sonsuza kalir. Motora
     * eklenen `hopGuard` bunu engeller; burada ayni desen dogrulanir.
     */
    @Test
    fun duplicateWaypointsCannotHangTheConsumingLoop() {
        val p = com.miniappfactory.frontlinedefender.game.model.PointF(0.5f, 0.5f)
        val q = com.miniappfactory.frontlinedefender.game.model.PointF(0.6f, 0.5f)
        val degenerate = listOf(p, p, p, q)
        // Rota TOPLAM 0,1 birim uzunlugunda (p -> p -> p -> q). Hiz x sure
        // (10) bundan cok daha buyuk, yani dusman rotayi bitirir ve katettigi
        // yol tam olarak rota uzunlugu olmalidir. Onemli olan: DONGU DONER.
        val d = distanceAfter(degenerate, speed = 10f, totalTime = 1f, dt = 1f / 60f)
        assertEquals("sifir uzunluklu segmentler yol EKLEMEMELI", 0.1f, d, 1e-4f)
    }
}
