package com.miniappfactory.frontlinedefender.perf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.Biome
import com.miniappfactory.frontlinedefender.game.model.BiomeSlotCache
import com.miniappfactory.frontlinedefender.game.model.BiomeVariants
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference

/**
 * UZUN OTURUM / 55 BOLUM GECISI — bellek birikimi var mi?
 *
 * Kampanya bugun 22'den 55 bolume cikti. Bir oturumda 55 bolum gecisi
 * yapiliyorsa, bolum basina SIZAN 8,3 MB'lik bir bitmap 455 MB eder ve
 * cihazda hicbir sekilde ayakta kalmaz. Bu dosya "bolum gecisi bitmap
 * biriktiriyor mu" sorusunu OLCEREK cevaplar, umut ederek degil.
 */
class BiomeSlotCacheLeakTest {

    /**
     * TEK YUVA SOZLESMESI: 55 bolumluk bir oturumda onbellek asla birden fazla
     * bitmap tutmaz. Bu sozlesme bozulursa (ornegin "hizli olsun" diye
     * `HashMap` yapilirsa) 11 harita x 5 biyom = 55 varyant x 8,3 MB = 456 MB
     * olur — yani anlik OOM.
     */
    @Test
    fun slotCacheNeverHoldsMoreThanOneEntryAcrossFullCampaign() {
        val cache = BiomeSlotCache<Any>()
        var maxSize = 0

        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val biome = BiomeVariants.biomeFor(level)
            val mapId = ((level - 1) % GameConfig.MAP_ID_MAX) + 1
            if (cache.get(mapId, biome) == null) {
                cache.put(mapId, biome, Any())
            }
            if (cache.size > maxSize) maxSize = cache.size
        }

        PerfHarness.report(
            "BiomeSlotCache — " + GameConfig.CAMPAIGN_LEVEL_COUNT + " bolumluk oturum",
            "gorulen en buyuk yuva sayisi = " + maxSize,
            "tek yuva tavani = 8.302.080 bayt (7,92 MiB)",
            "sozlesme bozulsaydi (11 harita x 5 biyom) = " +
                (11L * 5 * 8_302_080 / 1_048_576) + " MiB"
        )
        assertEquals("Onbellek TEK yuvali olmali", 1, maxSize)
    }

    /**
     * Eski girdi GERCEKTEN birakiliyor mu? `size == 1` demek yeterli degil:
     * yuva eski nesneye referansi baska bir alanda tutuyor olabilirdi. Zayif
     * referans bunu kanitlar.
     */
    @Test
    fun replacingTheSlotReleasesThePreviousBitmapForGc() {
        val cache = BiomeSlotCache<Any>()
        var first: Any? = Any()
        val weak = WeakReference(first)
        cache.put(1, Biome.ORIGINAL, first!!)
        first = null

        // Yeni bolum: yuva uzerine yazilir.
        cache.put(2, Biome.NIGHT, Any())

        var collected = false
        repeat(20) {
            if (weak.get() == null) {
                collected = true
                return@repeat
            }
            System.gc()
            Runtime.getRuntime().runFinalization()
        }

        PerfHarness.report(
            "BiomeSlotCache — eski girdi birakiliyor mu",
            "zayif referans temizlendi = " + (weak.get() == null)
        )
        assertTrue("Eski girdi GC'ye birakilmali; aksi halde bolum basina 7,92 MiB sizar", collected || weak.get() == null)
    }

    /**
     * `clear()` gercekten bosaltiyor mu? `onTrimMemory` / bolum disina cikis
     * icin TEK kurtarma yolu bu.
     */
    @Test
    fun clearEmptiesTheSlot() {
        val cache = BiomeSlotCache<Any>()
        cache.put(3, Biome.WINTER, Any())
        assertEquals(1, cache.size)
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get(3, Biome.WINTER))
    }
}

/**
 * MOTOR TARAFI UZUN OTURUM — 55 bolum yuklenip birakildiginda motorun
 * koleksiyonlarinda kalinti kaliyor mu?
 *
 * Bir bolum bittiginde `enemies` / `projectiles` / `visualEffects` bosalmiyorsa
 * her bolum bir oncekinin cop entity'lerini tasir; 55 bolumluk bir oturumda bu
 * hem bellek hem KARE SURESI sorunudur (her karede taranan liste buyur).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EngineLongSessionTest {

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

    @Test
    fun fiftyFiveLevelTransitionsLeaveNoResidueInEngineCollections() {
        var maxEnemies = 0
        var maxProjectiles = 0
        var maxEffects = 0
        var maxTowers = 0
        var totalTicks = 0

        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            engine.startNewGame(level)

            // GERCEKTEN OYNA: para yettigi kadar kule kur. Kule kurulmazsa
            // mermi ve efekt hic uretilmez ve test "bos bir bolumun"
            // temizligini dogrulamis olur — yani hicbir sey.
            engine.scaledBuildSpots.forEach { spot ->
                engine.selectBuildSpot(spot)
                engine.buildTower(GameConfig.TowerType.MACHINE_GUN)
            }
            engine.deselectAll()

            engine.startNextWaveNow()
            // Bir dalganin bir kismini gercekten oyna: spawn, atis, patlama,
            // efekt uretimi. Sonra bolumden CIK ve bir sonrakine gec.
            // Bolum basina 300 kare = 5 sn simulasyon. 55 bolumde 16.500 kare.
            // Daha uzunu kapsam eklemiyor (spawn -> atis -> carpma -> efekt
            // dongusunun tamami ilk saniyelerde kosuyor) ama testi bu makinede
            // 5 ajan es zamanli Gradle kosarken zaman asimina acik hale
            // getiriyordu.
            repeat(300) {
                engine.tick(1f / 60f)
                totalTicks++
                maxEnemies = maxOf(maxEnemies, engine.enemies.size)
                maxProjectiles = maxOf(maxProjectiles, engine.projectiles.size)
                maxEffects = maxOf(maxEffects, engine.visualEffects.size)
            }
            maxTowers = maxOf(maxTowers, engine.towers.size)
        }

        // Son bolumden sonra bir kez daha bastan basla: koleksiyonlar
        // TEMIZLENMELI.
        engine.startNewGame(1)

        PerfHarness.report(
            "55 bolum gecisi — motor koleksiyonlari",
            "simule edilen kare = " + totalTicks + " (~" + (totalTicks / 60 / 60) + " dk oynanis)",
            "en yuksek kule sayisi = " + maxTowers,
            "oturum boyunca gorulen en yuksek dusman sayisi = " + maxEnemies,
            "en yuksek mermi sayisi   = " + maxProjectiles,
            "en yuksek efekt sayisi   = " + maxEffects + " (tavan 96)",
            "gecis sonrasi dusman/mermi/efekt = " + engine.enemies.size + "/" +
                engine.projectiles.size + "/" + engine.visualEffects.size
        )

        val residue = "dusman=" + engine.enemies.size + " mermi=" + engine.projectiles.size +
            " efekt=" + engine.visualEffects.size + " kule=" + engine.towers.size
        assertEquals("Bolum gecisinde dusman kalintisi ($residue)", 0, engine.enemies.size)
        assertEquals("Bolum gecisinde mermi kalintisi ($residue)", 0, engine.projectiles.size)
        assertEquals("Bolum gecisinde efekt kalintisi ($residue)", 0, engine.visualEffects.size)
        assertEquals("Bolum gecisinde kule kalintisi ($residue)", 0, engine.towers.size)

        // EFEKT TAVANI gercekten uygulanmali: 55 bolumluk oturumun HICBIR
        // aninda 96 asilmamali, yoksa cizim maliyeti sinirsiz buyur.
        assertTrue(
            "Gorsel efekt tavani asildi: " + maxEffects,
            maxEffects <= 96
        )
    }
}
