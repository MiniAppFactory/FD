package com.miniappfactory.frontlinedefender.game.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.BattleTelemetry
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.BuildSpot
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ===========================================================================
 * ATES HATTI SINYALI — **OYUNCU KURMADAN ONCE GORUYOR MU?**
 * ===========================================================================
 *
 * Cihaz sikayeti: bolum 8'de pad 7'ye Gatling kuruldu, kule hicbir seye ates
 * edemedi. Olcum pad'i akladi (ayni pad'den Fuze Rampasi haritanin en iyi
 * ikinci kapsamasini veriyor); kusur SINYALIN YOKLUGUYDU.
 *
 * Kabul kurali uc parcali ve ucu de burada olculuyor:
 *  1. Kart, cekmece acik oldugu SURECE rozet tasir — oyuncunun cogu dokunusu
 *     basip-bekleme degil KISA TAP oldugu icin "basili tutunca gorunur" bir
 *     sinyal tek basina yetmez.
 *  2. Kart BASILI tutuldugunda serit sebebi CUMLE olarak yazar.
 *  3. Uyari BLOKE ETMEZ: insa yine gerceklesir. Menzil kalici olarak buyuyor
 *     (Gatling kd.1 150 -> kd.2 180 -> kd.3 210), yani bugun yetismeyen mevzi
 *     bilincli bir plan olabilir.
 *
 * Ekran geometrisi gercek oynanis geometrisi (yatay, 740x360 dp) — bu depoda
 * tasan metin hatasi uc kez cikti ve hepsi bu dar ekranda olustu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land-xxhdpi")
class LineOfFireUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private object NoTelemetry : BattleTelemetry {
        override fun noteTowerBuilt(towerTypeName: String) {}
        override fun noteTowerUpgraded() {}
        override fun noteTowerSold() {}
        override fun noteSellTrackingActive() {}
        override fun notePrepTimerSkipped() {}
        override fun noteGameSpeed(speed: Float) {}
    }

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
     * Cihazdan gelen durumun TAM ESI, ama sabit pad numarasi YAZMADAN.
     *
     * Harita geometrisi baska bir is kolunda yeniden uretiliyor; "harita 8 pad
     * 7" diye sabitlemek yarin sahte bir kirilma uretirdi. Bunun yerine
     * kosulun KENDISI araniyor: Gatling'in yetismedigi ama baska bir ACIK
     * kulenin yetistigi bir mevzi. Boyle bir mevzi yoksa test anlamsizdir ve
     * bunu sessizce gecmek yerine soyluyoruz.
     */
    private fun selectPadWhereOnlyGatlingCannotReach(levelId: Int): BuildSpot {
        engine.startNewGame(levelId)
        val routes = engine.scaledRoutes
        val s = engine.renderScale
        fun reaches(spot: BuildSpot, t: TowerType) = GameConfig.coversRoute(
            spot.normX, spot.normY, engine.previewRangeRef(t) * s, routes
        )
        // YALNIZCA Gatling yetismesin: boylece "rozet tasiyan kart tam olarak
        // bir tanedir" iddiasi da olculebilir. Iki kule birden yetismeseydi
        // iki rozet cikardi ve test "her karta rozet basiyoruz" hatasini
        // ayirt edemezdi.
        val spot = engine.scaledBuildSpots.firstOrNull { s2 ->
            !reaches(s2, TowerType.MACHINE_GUN) &&
                TowerType.values().all { it == TowerType.MACHINE_GUN || reaches(s2, it) }
        }
        assertNotNull(
            "Bolum $levelId'de YALNIZCA Gatling'in yetismedigi mevzi yok — " +
                "cihazdan gelen durum bu bolumde artik uretilemiyor",
            spot
        )
        engine.selectBuildSpot(spot)
        return spot!!
    }

    // ------------------------------------------------------------------------
    // 1. KART ROZETI — kisa tap yolunu kurtaran sinyal
    // ------------------------------------------------------------------------

    /**
     * Yetismeyen kule rozet tasir, yetisen kule TASIMAZ.
     *
     * Ikinci yari birincisi kadar onemli: her karta rozet basmak sinyali yok
     * eder. Rozet ancak AYIRT ETTIGI surece bilgidir.
     */
    @Test
    fun `yetismeyen kule rozet tasir yetisen tasimaz`() {
        selectPadWhereOnlyGatlingCannotReach(8)
        composeRule.setContent { TowerBuildBar(gameEngine = engine, telemetry = NoTelemetry) }

        // Rozet kartin ICINDE; kart Row'u tiklanabilir oldugu icin cocuk
        // semantigini birlestiriyor -> birlesmemis agac gerekiyor.
        composeRule.onNodeWithTag("build_card_machine_gun", useUnmergedTree = true)
            .assertIsDisplayed()
        // Tekil sorgu: birden fazla rozet cizilseydi (orn. her karta birden)
        // bu satir "birden fazla dugum" ile patlardi. Yani "yalnizca yetismeyen
        // kartta rozet var" iddiasi da burada olculuyor.
        composeRule.onNodeWithTag("build_no_reach_badge", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** Yetisen mevzide HICBIR kart rozet tasimaz — sinyal gurultuye donmez. */
    @Test
    fun `her kulenin yetistigi mevzide rozet cizilmez`() {
        engine.startNewGame(8)
        val routes = engine.scaledRoutes
        val s = engine.renderScale
        val allReach = engine.scaledBuildSpots.firstOrNull { spot ->
            TowerType.values().all { t ->
                GameConfig.coversRoute(spot.normX, spot.normY, engine.previewRangeRef(t) * s, routes)
            }
        }
        assertNotNull("bolum 8'de tum kulelerin yetistigi bir mevzi olmali", allReach)
        engine.selectBuildSpot(allReach)

        composeRule.setContent { TowerBuildBar(gameEngine = engine, telemetry = NoTelemetry) }
        composeRule.onNodeWithTag("build_no_reach_badge", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    // ------------------------------------------------------------------------
    // 2. SEBEP CUMLESI + DAR EKRANDA TASMA
    // ------------------------------------------------------------------------

    /**
     * Kart BASILI iken serit sebebi yazar ve serit EKRANIN ICINDE kalir.
     *
     * TASMA KONTROLU burada gercek olcumle yapiliyor: dugumun kirpilmamis
     * (unclipped) sinirlari koke sigmiyorsa metin ekran disina tasmis
     * demektir. `getUnclippedBoundsInRoot` KIRPMADAN olctugu icin "gorunmuyor
     * ama tasiyor" durumu da yakalanir — kirpilmis olcum tasmayi gizlerdi.
     */
    @Test
    fun `basili kartta serit sebebi yazar ve dar ekrana sigar`() {
        selectPadWhereOnlyGatlingCannotReach(8)
        composeRule.setContent {
            TowerBuildBar(gameEngine = engine, telemetry = NoTelemetry)
            BuildRejectionStrip(gameEngine = engine)
        }

        composeRule.onNodeWithTag("build_reach_caption").assertDoesNotExist()
        composeRule.onNodeWithTag("build_card_machine_gun")
            .performTouchInput { down(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("build_reach_caption").assertIsDisplayed()
        assertFitsInRoot("build_reach_caption")

        // Parmak kalkinca uyari gider: uyari bir DURUM anlatir, bir olay degil.
        composeRule.onNodeWithTag("build_card_machine_gun").performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("build_reach_caption").assertDoesNotExist()
    }

    /**
     * TURKCE — bu depoda tasan metin hatasinin uc kez ciktigi dil.
     *
     * EN UZUN SERIT METNI ile olculur: cumleye kule adi giriyor ve en uzun ad
     * "Fuze Rampasi" (`ANTI_ARMOR`). Yani olculen sey
     * *"Fuze Rampasi buradan yola yetismiyor."* — desteklenen iki dilin en
     * genis hali.
     *
     * Bolum SABIT YAZILMAZ, araniyor: Fuze menzili 250 ve onun yetismedigi
     * mevzi her haritada yok. Kampanyayi tarayip kosulun gercekten olustugu
     * ilk bolume gidiyoruz — boylece harita geometrisi degistiginde test
     * sessizce "hicbir sey olcmeyen" bir teste donusmez.
     *
     * `@Config` metot duzeyinde sinif ayarini ezer; degisen tek sey yerel ayar.
     */
    @Test
    @Config(sdk = [33], qualifiers = "tr-rTR-w740dp-h360dp-land-xxhdpi")
    fun `turkce en uzun kule adiyla serit dar ekrana sigar`() {
        var found = false
        for (spec in GameConfig.CAMPAIGN) {
            if (!GameConfig.isTowerUnlocked(TowerType.ANTI_ARMOR, spec.levelId)) continue
            engine.startNewGame(spec.levelId)
            val routes = engine.scaledRoutes
            val range = engine.previewRangeRef(TowerType.ANTI_ARMOR) * engine.renderScale
            val spot = engine.scaledBuildSpots.firstOrNull {
                !GameConfig.coversRoute(it.normX, it.normY, range, routes)
            } ?: continue
            engine.selectBuildSpot(spot)
            found = true
            break
        }
        assertTrue(
            "Fuze Rampasi'nin yetismedigi hicbir mevzi yok — en uzun serit " +
                "metni artik uretilemiyor, tasma olcumu anlamsizlasir",
            found
        )

        composeRule.setContent {
            TowerBuildBar(gameEngine = engine, telemetry = NoTelemetry)
            BuildRejectionStrip(gameEngine = engine)
        }
        composeRule.onNodeWithTag("build_card_anti_armor").performTouchInput { down(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("build_reach_caption").assertIsDisplayed()
        assertFitsInRoot("build_reach_caption")
        // Kart etiketleri de olculur: TR adlar EN'den %20-35 uzun ve rozet
        // fiyat satirina 12 dp + 3 dp ekliyor.
        assertFitsInRoot("build_card_anti_armor")
        assertFitsInRoot("build_card_machine_gun")
    }

    // ------------------------------------------------------------------------
    // 3. ENGELLEMEZ + CEKMECE YUKSEKLIGI SABIT
    // ------------------------------------------------------------------------

    /**
     * UYARI BLOKE ETMEZ. Oyuncu yine kurabilmeli: menzil yukseltmesiyle
     * (150 -> 180 -> 210) o mevzi ileride acilir ve pad'i elinden almak bir
     * plani elinden almak olurdu. Mesaj "yapamazsin" degil "yetismiyor".
     */
    @Test
    fun `yetismeyen mevzide insa yine gerceklesir`() {
        selectPadWhereOnlyGatlingCannotReach(8)
        val before = engine.towerCount.value
        composeRule.setContent { TowerBuildBar(gameEngine = engine, telemetry = NoTelemetry) }

        composeRule.onNodeWithTag("build_card_machine_gun").performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()
        assertTrue(
            "uyari insayi engellememeli (once=$before, sonra=${engine.towerCount.value})",
            engine.towerCount.value == before + 1
        )
    }

    /**
     * ROZET CEKMECEYI BUYUTMEZ.
     *
     * `GameCanvas`in "ortulen secim hayaleti" bu cekmecenin ust kenarini capa
     * olarak kullaniyor ve capayi [GameConfig.BUILD_DRAWER_HEIGHT_DP]den
     * hesapliyor. Rozet ikinci satiri gaspetmek yerine fiyatin yanina
     * oturtuldu; bu test o kararin sessizce bozulmadigini olcer.
     */
    @Test
    fun `rozet cekmece yuksekligini degistirmez`() {
        selectPadWhereOnlyGatlingCannotReach(8)
        composeRule.setContent { TowerBuildBar(gameEngine = engine, telemetry = NoTelemetry) }
        val h = composeRule.onNodeWithTag("build_drawer").getUnclippedBoundsInRoot().height
        assertTrue(
            "cekmece ${h} != ${GameConfig.BUILD_DRAWER_HEIGHT_DP} dp",
            kotlin.math.abs(h.value - GameConfig.BUILD_DRAWER_HEIGHT_DP) < 0.5f
        )
    }

    // ------------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------------

    /** Dugumun KIRPILMAMIS sinirlari kokun icinde mi? Tasma kontrolu. */
    private fun assertFitsInRoot(tag: String) {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val node = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue(
            "'$tag' ekrandan tasiyor: node=$node root=$root",
            node.left >= root.left - EPS &&
                node.top >= root.top - EPS &&
                node.right <= root.right + EPS &&
                node.bottom <= root.bottom + EPS
        )
    }

    /** Olcum yuvarlamasi payi. */
    private val EPS = 0.5.dp
}
