package com.miniappfactory.frontlinedefender.modifiers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ===========================================================================
 * BOLUM DEGISTIRICILERI — **MOTOR SON SOZU SOYLUYOR MU?**
 * ===========================================================================
 *
 * `GameEngine.buildTower` kule KILIDINI zaten motorda kontrol ediyordu ve
 * gerekcesi acikti: *"UI pasif kart cizse de motor son sozu soyler"*. Uc yeni
 * degistirici ayni deseni izlemek zorunda — aksi halde kural yalnizca bir
 * cizim detayi olur ve ogretici, test ya da ileride eklenecek surukle-birak
 * yolu onu sessizce atlar.
 *
 * Burada UI hic cizilmez: dogrudan motora insa denemesi yapilir. Olculen sey
 * "kart pasif mi" degil, **motor gercekten reddediyor mu ve SEBEBI yayinliyor
 * mu**.
 *
 * KONTROL GRUBU da var: degistirici tasimayan bir bolumde ayni cagrilar
 * BIREBIR eski davranisi vermeli.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LevelModifierEngineTest {

    /** Kadro kisiti: Gatling bu harekatta yok (ucuz is ati kaldirildi). */
    private val restrictedLevel = 15

    /** Mevzi tavani. */
    private val cappedLevel = 19

    /** Donmus mevzi: dalga basladiktan sonra yeni kule yok. */
    private val frozenLevel = 24

    /** Degistiricisiz kontrol bolumu. */
    private val plainLevel = 20

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

    /** Bos bir pad secer; yoksa testin kendisi anlamsizdir. */
    private fun selectFreePad() {
        val occupied = engine.towers.map { it.buildSpotId }.toSet()
        val spot = engine.scaledBuildSpots.firstOrNull { it.id !in occupied }
        assertNotNull("bos build pad kalmadi", spot)
        engine.selectBuildSpot(spot)
    }

    // =======================================================================
    // KISITLI KADRO
    // =======================================================================

    @Test
    fun `kadro disi kule motor tarafindan reddedilir`() {
        engine.startNewGame(restrictedLevel)
        val banned = TowerType.values().first {
            !GameConfig.levelSpec(restrictedLevel).allowsTowerType(it)
        }

        selectFreePad()
        assertEquals(
            "kadro disi kule icin sebep NOT_IN_LOADOUT olmali",
            GameEngine.BuildRejection.NOT_IN_LOADOUT,
            engine.buildRejectionFor(banned)
        )
        assertTrue("kadro disi kule kuruldu", !engine.buildTower(banned))
        assertEquals(0, engine.towers.size)
    }

    @Test
    fun `kadro icindeki kule ayni bolumde normal kurulur`() {
        engine.startNewGame(restrictedLevel)
        selectFreePad()
        assertNull(engine.buildRejectionFor(TowerType.CANNON))
        assertTrue(engine.buildTower(TowerType.CANNON))
        assertEquals(1, engine.towers.size)
        assertEquals(1, engine.towerCount.value)
    }

    /**
     * SEBEP AYRIMI: bolum kilidi ile harekat kisiti AYNI olamaz. Oyuncu icin
     * biri "ilerleyince acilir", digeri "bu bolumde hic acilmaz".
     */
    @Test
    fun `bolum kilidi ile kadro kisiti FARKLI sebep dondurur`() {
        engine.startNewGame(1) // fuze rampasi L7'de acilir
        selectFreePad()
        assertEquals(
            GameEngine.BuildRejection.TOWER_LOCKED,
            engine.buildRejectionFor(TowerType.ANTI_ARMOR)
        )

        engine.startNewGame(restrictedLevel)
        selectFreePad()
        assertEquals(
            GameEngine.BuildRejection.NOT_IN_LOADOUT,
            engine.buildRejectionFor(TowerType.MACHINE_GUN)
        )
    }

    // =======================================================================
    // MEVZI TAVANI
    // =======================================================================

    @Test
    fun `tavana ulasilinca insa reddedilir ve sebep soylenir`() {
        engine.startNewGame(cappedLevel)
        val cap = GameConfig.levelSpec(cappedLevel).maxTowers
        assertNotNull("bolum tavan tasimiyor", cap)

        var built = 0
        while (built < cap!! + 2) {
            selectFreePad()
            if (!engine.buildTower(TowerType.MACHINE_GUN)) break
            built++
        }

        assertEquals("tavan kadar kule kurulabilmeliydi", cap, built)
        assertEquals(cap, engine.towers.size)
        selectFreePad()
        assertEquals(
            "tavan dolu ama sebep EMPLACEMENT_CAP degil",
            GameEngine.BuildRejection.EMPLACEMENT_CAP,
            engine.buildRejectionFor(TowerType.MACHINE_GUN)
        )
        assertTrue("tavanin ustune kule kuruldu", !engine.buildTower(TowerType.MACHINE_GUN))
        assertEquals(cap, engine.towerCount.value)
    }

    /** Tavan bir KOTA degil STOK kisiti: satis yer acar. */
    @Test
    fun `satis tavanda yer acar`() {
        engine.startNewGame(cappedLevel)
        val cap = GameConfig.levelSpec(cappedLevel).maxTowers!!
        repeat(cap) {
            selectFreePad()
            assertTrue(engine.buildTower(TowerType.MACHINE_GUN))
        }
        engine.selectTower(engine.towers.first())
        assertTrue("tavandayken satis engellendi", engine.sellSelectedTower())
        assertEquals(cap - 1, engine.towerCount.value)

        selectFreePad()
        assertNull(
            "satistan sonra insa hâlâ reddediliyor",
            engine.buildRejectionFor(TowerType.MACHINE_GUN)
        )
        assertTrue(engine.buildTower(TowerType.MACHINE_GUN))
    }

    // =======================================================================
    // DONMUS MEVZI
    // =======================================================================

    @Test
    fun `hazirlik fazinda insa serbest, dalga baslayinca reddedilir`() {
        engine.startNewGame(frozenLevel)
        assertEquals(GameState.PREPARATION, engine.gameState.value)

        selectFreePad()
        assertTrue("hazirlik fazinda insa engellendi", engine.buildTower(TowerType.MACHINE_GUN))

        engine.startNextWaveNow()
        assertEquals(GameState.WAVE_RUNNING, engine.gameState.value)

        selectFreePad()
        assertEquals(
            GameEngine.BuildRejection.WAVE_IN_PROGRESS,
            engine.buildRejectionFor(TowerType.MACHINE_GUN)
        )
        assertTrue("dalga sirasinda kule kuruldu", !engine.buildTower(TowerType.MACHINE_GUN))
        assertEquals(1, engine.towerCount.value)
    }

    /**
     * Kural "catisma sirasinda tepki verme" DEGIL "hattini dalga baslamadan
     * kur" der. Yukseltme ve satis serbest kalir; aksi halde dalga icinde
     * kazanilan Tedarik OLU PARAYA donerdi.
     */
    @Test
    fun `donmus mevzide yukseltme ve satis dalga sirasinda da calisir`() {
        engine.startNewGame(frozenLevel)
        selectFreePad()
        assertTrue(engine.buildTower(TowerType.MACHINE_GUN))
        engine.startNextWaveNow()

        engine.selectTower(engine.towers.first())
        assertTrue("dalga sirasinda yukseltme engellendi", engine.upgradeSelectedTower())
        assertEquals(2, engine.towers.first().level)

        engine.selectTower(engine.towers.first())
        assertTrue("dalga sirasinda satis engellendi", engine.sellSelectedTower())
        assertEquals(0, engine.towerCount.value)
    }

    // =======================================================================
    // RET SESSIZ DEGIL
    // =======================================================================

    /**
     * Ayni sebep ust uste geldiginde de yeni bir olay uretilmeli. `StateFlow`
     * ayni degeri iki kez yaymadigi icin sayac olmasa ikinci ret SESSIZ kalirdi
     * — yani kural tam da en cok tekrarlandigi anda gorunmez olurdu.
     */
    @Test
    fun `her ret ayri bir olay yayinlar`() {
        engine.startNewGame(restrictedLevel)
        assertNull("savas basinda ret olayi olmamali", engine.buildRejection.value)

        selectFreePad()
        engine.buildTower(TowerType.MACHINE_GUN)
        val first = engine.buildRejection.value
        assertNotNull("ret sessiz kaldi", first)
        assertEquals(GameEngine.BuildRejection.NOT_IN_LOADOUT, first!!.reason)
        assertEquals(TowerType.MACHINE_GUN, first.type)

        engine.buildTower(TowerType.MACHINE_GUN)
        val second = engine.buildRejection.value!!
        assertTrue("ikinci ret yeni olay uretmedi", second.serial > first.serial)
    }

    @Test
    fun `yeni savas onceki ret mesajini temizler`() {
        engine.startNewGame(restrictedLevel)
        selectFreePad()
        engine.buildTower(TowerType.MACHINE_GUN)
        assertNotNull(engine.buildRejection.value)

        engine.startNewGame(plainLevel)
        assertNull("ret mesaji yeni savasa tasindi", engine.buildRejection.value)
    }

    // =======================================================================
    // KONTROL GRUBU — degistiricisiz bolum BIREBIR eski davranis
    // =======================================================================

    @Test
    fun `degistiricisiz bolumde tavan yok ve dalga sirasinda insa serbest`() {
        engine.startNewGame(plainLevel)
        assertNull(GameConfig.levelSpec(plainLevel).maxTowers)

        // Bes kule: tavanli bolumde tam burada reddedilirdi.
        repeat(5) {
            selectFreePad()
            assertTrue("degistiricisiz bolumde insa reddedildi", engine.buildTower(TowerType.MACHINE_GUN))
        }

        engine.startNextWaveNow()
        selectFreePad()
        assertNull(
            "degistiricisiz bolumde dalga sirasinda insa engellendi",
            engine.buildRejectionFor(TowerType.MACHINE_GUN)
        )
        assertTrue(engine.buildTower(TowerType.MACHINE_GUN))
        assertEquals(6, engine.towerCount.value)
        assertNull("degistiricisiz bolumde ret olayi yayinlandi", engine.buildRejection.value)
    }

    @Test
    fun `degistiricisiz bolumde acik her kule kurulabilir`() {
        engine.startNewGame(plainLevel)
        GameConfig.unlockedTowers(plainLevel).forEach { type ->
            selectFreePad()
            assertNull(
                "$type degistiricisiz bolumde reddedildi",
                engine.buildRejectionFor(type)
            )
            assertTrue(engine.buildTower(type))
        }
    }
}
