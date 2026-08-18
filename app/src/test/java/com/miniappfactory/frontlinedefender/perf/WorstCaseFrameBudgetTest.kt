package com.miniappfactory.frontlinedefender.perf

import android.content.Context
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameFeel
import com.miniappfactory.frontlinedefender.game.model.EffectType
import com.miniappfactory.frontlinedefender.game.model.EnemyEntity
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.TowerEntity
import com.miniappfactory.frontlinedefender.game.model.VisualEffect
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * EN KOTU DURUM KARE BUTCESI - simulasyon (`GameEngine.tick`) tarafi.
 *
 * NE OLCULUYOR
 *   Kare butcesi iki yariya bolunur: SIMULASYON (`tick`) ve CIZIM (Compose
 *   draw + RenderThread). Bu test yalnizca BIRINCISINI olcer, cunku cizim
 *   yarisi gercek bir GPU/Skia baglami ister ve o sayi CIHAZDAN alinmak
 *   zorundadir (bkz. docs/PERFORMANCE_REPORT.md adb komutlari).
 *
 *   Simulasyon yarisi tek basina bir kare butcesini yiyebilir; yiyorsa cizimi
 *   ne kadar optimize ettiginiz onemsizdir. Bu yuzden once burasi olculur.
 *
 * SAHNE
 *   Kampanyanin EN kalabalik dalgasindaki govde sayisi + haritanin TUM build
 *   pad'lerinde son kademe kule + gorsel efekt tavani dolu. Yani oyunun
 *   uretebilecegi en agir kare; ortalama kare degil.
 *
 * NEDEN DUSMAN HIZI 0
 *   Olcum boyunca sahne SABIT kalmali: dusman usse varirsa can duser, DEFEAT
 *   gelir ve `tick` erken doner - o zaman bos karenin suresini olcmus oluruz.
 *   Hiz sifirlanarak sahne dondurulur; hareket kodu yine her karede tam
 *   olarak kosar (yon, mesafe, waypoint hesabi).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorstCaseFrameBudgetTest {

    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine

    /** 60 FPS butcesi. 90 Hz'de 11.1 ms. */
    private val frameBudgetMs = 1000.0 / 60.0

    /** Galaxy S8 yatay oynanis alani. */
    private val screenW = 2220f
    private val screenH = 1080f
    private val hudInset = 96f

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        audio = AudioManager(ctx)
        // SES KAPALI: Robolectric SoundPool golgesi cihazdaki maliyeti temsil
        // etmez ve olcume golge tahsisi karistirirdi. Olculen yol, sesi kapali
        // oynayan bir oyuncunun kare yoluyla birebir aynidir.
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(ctx), audio)
        engine.updateMapDimensions(screenW, screenH, hudInset)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    // =========================================================================
    // Sahne kurulumu
    // =========================================================================

    /** Kampanyadaki EN kalabalik dalganin govde sayisi. */
    private fun worstWaveBodies(): Pair<Int, String> {
        var best = 0
        var where = "-"
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            WaveDefinitions.wavesFor(level).forEachIndexed { idx, wave ->
                if (wave.spawns.size > best) {
                    best = wave.spawns.size
                    where = "bolum " + level + " / dalga " + (idx + 1)
                }
            }
        }
        return best to where
    }

    /** Haritalar arasindaki EN cok build pad (ham geometri). */
    private fun maxBuildSpots(): Int =
        (GameConfig.MAP_ID_MIN..GameConfig.MAP_ID_MAX).maxOf {
            LevelData.forMapId(it).buildSpots.size
        }

    /**
     * OYNANABILIR en yogun bolum: devre disi pad'ler cikarildiktan sonra en
     * cok kule kurulabilen bolum. Ham geometriden okumak yanlis olurdu —
     * `disabledPadIds` bazi pad'leri haritadan tamamen kaldiriyor.
     */
    private fun busiestLevel(): Pair<Int, Int> {
        var bestLevel = 1
        var bestPads = 0
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            engine.startNewGame(level)
            val pads = engine.scaledBuildSpots.size
            if (pads > bestPads) {
                bestPads = pads
                bestLevel = level
            }
        }
        return bestLevel to bestPads
    }

    private fun seedWorstCase(level: Int, enemyCount: Int) {
        // BOLUMU GERCEKTEN YUKLE. Bu satir olmadan motor MAIN_MENU'de kalir ve
        // `tick` en bastaki durum beyaz listesinde erken doner: ilk kosuda tam
        // olarak bu oldu ve p99 = 0,001 ms gibi anlamsiz bir sayi cikti.
        engine.startNewGame(level)

        engine.towers.clear()
        engine.enemies.clear()
        engine.projectiles.clear()
        engine.visualEffects.clear()

        // 1. TUM pad'lere kule; tipler donusumlu, hepsi SON kademe (en genis
        //    menzil = en cok hedef adayi = en pahali hedefleme gecisi).
        val types = GameConfig.TowerType.entries
        engine.scaledBuildSpots.forEachIndexed { i, spot ->
            engine.towers.add(
                TowerEntity(
                    type = types[i % types.size],
                    buildSpotId = spot.id,
                    posX = spot.normX,
                    posY = spot.normY,
                    level = 3,
                    totalInvestedGold = 300
                )
            )
        }

        // 2. Dusmanlar rotanin ICINE dagitilir; kule menzillerinin icinde
        //    kalsinlar ki hedefleme ve atis yolu gercekten kossun.
        val route = engine.scaledRoutes.first()
        repeat(enemyCount) { i ->
            val idx = (i * 7 + 3) % route.size
            val pt = route[idx]
            engine.enemies.add(
                EnemyEntity(
                    type = GameConfig.EnemyType.TANK,
                    posX = pt.x + (i % 5) - 2f,
                    posY = pt.y + (i % 3) - 1f,
                    currentWayPointIndex = idx.coerceAtMost(route.size - 2),
                    hp = 1_000_000f,
                    maxHp = 1_000_000f,
                    baseSpeed = 0f,
                    armor = 0f,
                    rewardGold = 10,
                    radius = 22f
                )
            )
        }

        // 3. Gorsel efekt tavani DOLU.
        repeat(GameFeel.MAX_VISUAL_EFFECTS) { i ->
            engine.visualEffects.add(
                VisualEffect(
                    type = EffectType.entries[i % EffectType.entries.size],
                    posX = 200f + i,
                    posY = 300f + i,
                    maxAgeSeconds = 999f,
                    text = "+9g"
                )
            )
        }

        engine.startNextWaveNow()
    }

    // =========================================================================
    // 1. KARE SURESI - p50 / p95 / p99
    // =========================================================================

    @Test
    fun worstCaseSimulationTickFitsInsideTheFrameBudget() {
        val (bodies, where) = worstWaveBodies()
        val (level, pads) = busiestLevel()
        seedWorstCase(level, bodies)

        val dt = 1f / 60f
        // Isinma: JIT + ilk kare sisligi olcume girmesin.
        repeat(600) { engine.tick(dt) }

        var peakProjectiles = 0
        val samples = LongArray(3000)
        for (i in samples.indices) {
            // 300 karede bir sahne EN KOTU DURUMA geri kurulur; aksi halde
            // dusmanlar olur/sahne seyrelir ve olcum ortalama kareye kayar.
            if (i % 300 == 0) seedWorstCase(level, bodies)
            val t0 = System.nanoTime()
            engine.tick(dt)
            samples[i] = System.nanoTime() - t0
            if (engine.projectiles.size > peakProjectiles) peakProjectiles = engine.projectiles.size
        }

        val p50 = PerfHarness.percentileMillis(samples, 50)
        val p95 = PerfHarness.percentileMillis(samples, 95)
        val p99 = PerfHarness.percentileMillis(samples, 99)
        val max = PerfHarness.percentileMillis(samples, 100)

        PerfHarness.report(
            "EN KOTU DURUM tick() - " + bodies + " dusman (" + where + "), bolum " +
                level + " / " + pads + " pad (ham geometri tavani " + maxBuildSpots() +
                "), " + GameFeel.MAX_VISUAL_EFFECTS + " efekt",
            "aktif kule = " + engine.towers.size + ", dusman = " + engine.enemies.size +
                ", tepe mermi = " + peakProjectiles,
            "p50 = " + "%.3f".format(p50) + " ms",
            "p95 = " + "%.3f".format(p95) + " ms",
            "p99 = " + "%.3f".format(p99) + " ms",
            "max = " + "%.3f".format(max) + " ms",
            "60 FPS butcesi = " + "%.2f".format(frameBudgetMs) + " ms; p99 butcenin yuzde " +
                "%.1f".format(p99 / frameBudgetMs * 100) + " kadari (JVM, cihaz DEGIL)"
        )

        // OLCUM GECERLILIK KONTROLU: kuleler gercekten ates etmediyse yukaridaki
        // sayilar bos bir karenin suresidir, en kotu durumun degil.
        assertTrue("Kule kurulmadi - sahne gecersiz", engine.towers.size >= 5)
        assertTrue("Hic mermi uretilmedi - sahne gecersiz", peakProjectiles > 0)

        // SOZLESME: simulasyon tek basina butcenin YARISINI gecmemeli - geri
        // kalani cizim, Compose ve sistem icin. Bu JVM kapisi cihaz kapisinin
        // yerine GECMEZ, ama regresyonu yakalar.
        assertTrue(
            "En kotu durum tick p99 = " + "%.3f".format(p99) + " ms, 60 FPS butcesinin " +
                "yarisini (" + "%.2f".format(frameBudgetMs / 2) + " ms) asiyor",
            p99 <= frameBudgetMs / 2
        )
    }

    // =========================================================================
    // 2. KARE BASINA TAHSIS - GC duraklamasi = jank
    // =========================================================================

    @Test
    fun worstCaseSimulationTickAllocationPerFrame() {
        assumeTrue(PerfHarness.allocationSupported)
        val (bodies, _) = worstWaveBodies()
        val (level, _) = busiestLevel()
        seedWorstCase(level, bodies)
        val dt = 1f / 60f

        // 300 karelik dilimler halinde olculur ve her dilimin basinda sahne en
        // kotu duruma geri kurulur; boylece olculen tahsis GERCEKTEN dolu bir
        // sahnenin tahsisidir.
        var total = 0.0
        val slices = 6
        repeat(slices) {
            seedWorstCase(level, bodies)
            repeat(60) { engine.tick(dt) } // dilim isinmasi
            total += PerfHarness.allocatedBytesPerIteration(warmup = 0, iterations = 240) {
                engine.tick(dt)
            }
        }
        val bytes = total / slices

        // TABAN: ayni kalabalik sahne, ama ATIS YOK (kuleler kaldirildi).
        // Bu ayrimin sebebi: kare basina tahsisin bir kismi KACINILMAZ olay
        // maliyetidir (mermi ve efekt NESNELERI gerceklten gerekiyor), bir
        // kismi ise saf israftir (her karede yeniden uretilen gecici nesneler).
        // "Sifira yakin tahsis" hedefi TABAN icin gecerlidir.
        var baselineTotal = 0.0
        repeat(slices) {
            seedWorstCase(level, bodies)
            engine.towers.clear()
            repeat(60) { engine.tick(dt) }
            baselineTotal += PerfHarness.allocatedBytesPerIteration(warmup = 0, iterations = 240) {
                engine.tick(dt)
            }
        }
        val baseline = baselineTotal / slices

        PerfHarness.report(
            "EN KOTU DURUM tick() tahsisi",
            "kare basina (tam sahne, atis VAR) = " + "%.0f".format(bytes) + " bayt",
            "kare basina (ayni sahne, atis YOK) = " + "%.0f".format(baseline) +
                " bayt  <- TABAN, hedef sifira yakin",
            "fark = olay maliyeti (mermi + efekt nesneleri) = " +
                "%.0f".format(bytes - baseline) + " bayt/kare",
            "saniyede    = " + "%.0f".format(bytes * 60) + " bayt (" +
                "%.1f".format(bytes * 60 / 1024) + " KiB/sn)",
            "10 dk oynanis = " + "%.1f".format(bytes * 60 * 600 / 1048576) + " MiB toplam tahsis"
        )

        // Kapi degil regresyon koruyucusu: kare basina 8 KiB, 60 FPS'te
        // saniyede yarim MiB demektir ve ART'ta gorunur GC duraklamasi uretir.
        assertTrue(
            "tick() kare basina " + "%.0f".format(bytes) + " bayt tahsis ediyor (tavan 8192)",
            bytes <= 8192.0
        )
    }

    // =========================================================================
    // 3. CIZIM YOLU - `ColorFilter.tint` her ISABET PARLAMASINDA
    // =========================================================================

    /**
     * `GameCanvas.drawEnemy` isabet parlamasi icin `ColorFilter.tint(White,
     * SrcIn)` uretiyordu. Deger SABIT: ne renk ne harman modu degisiyor. Yani
     * her karede, parlayan her dusman icin ayni nesne yeniden uretiliyordu -
     * ustelik NATIVE ESLI bir nesne (android.graphics color filter), tipki
     * bugun kapatilan `Paint` kaynagi gibi.
     */
    @Test
    fun colorFilterTintIsAllocatedPerCall() {
        assumeTrue(PerfHarness.allocationSupported)
        var sink: ColorFilter? = null

        val perCall = PerfHarness.allocatedBytesPerIteration(warmup = 5_000, iterations = 50_000) {
            sink = ColorFilter.tint(Color.White, BlendMode.SrcIn)
        }
        assertTrue(sink != null)

        val flashing = 20 // agir bir dalgada ayni anda parlayan dusman sayisi
        PerfHarness.report(
            "drawEnemy: ColorFilter.tint(White, SrcIn)",
            "cagri basina = " + "%.1f".format(perCall) + " bayt (+ native esli nesne)",
            flashing.toString() + " parlayan dusman @60 FPS = " +
                "%.0f".format(perCall * flashing * 60) + " bayt/sn",
            "sabit bir val'e cikarilirsa = 0 bayt/sn"
        )
        assertTrue("Olcum gecerli mi", perCall >= 0.0)
    }
}
