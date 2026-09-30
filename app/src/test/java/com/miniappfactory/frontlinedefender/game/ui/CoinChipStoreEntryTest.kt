package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.miniappfactory.frontlinedefender.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * COIN CIPINDEN IKMAL DEPOSUNA GIRIS — EKRANDA GERCEKTEN VAR MI VE SIGIYOR MU?
 *
 * Reklamlar kaldirildi; coin cipi artik odullu reklam degil **Ikmal Deposu
 * (StoreScreen)** kapisidir: `LevelSelectScreen(onOpenStore = ...)`. Kural:
 *  - `onOpenStore != null`  -> cip dokunulabilir + "+" rozeti (`coin_store_badge`)
 *  - `onOpenStore == null`  -> cip dokunulamaz, rozet YOK, ama bakiye HER ZAMAN okunur.
 *
 * Bu dosya o sozlesmenin **piksele ulastigini** kanitlar (mantik testleri
 * "dogru hesaplaniyordu ama hicbir composable'a ulasmiyordu" hatasini
 * yakalayamaz).
 *
 * ## Neden 740x360 dp
 * Test cihazi Galaxy S8 ve oyun `sensorLandscape`: gercek yuzey **740x360 dp**.
 * Bolum secim ekraninin baslik satiri bu genislikte zaten dolu (geri, baslik,
 * Gorevler, Cephanelik, Coin). Rozet TEK KARAKTER ve `translatable=false`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land")
class CoinChipStoreEntryTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Ekranin ihtiyac duydugu her seyi sabit veren sahte ilerleme.
     *
     * `todaysMissions` varsayilan olarak bos, yani Gorevler cipi cizilmez.
     * Metin genisligi Robolectric'te olculemez (bkz. GEOMETRI bolumu); o butce
     * `LevelSelectHeaderWidthBudgetTest` icinde.
     */
    private class FakeProgress(
        override val coins: Int,
        private val cleared: Int = 8,
    ) : CampaignProgress {
        override fun starsFor(levelId: Int): Int = if (levelId <= cleared) 3 else 0
        override fun isUnlocked(levelId: Int): Boolean = levelId <= cleared + 1
        override fun tryUnlock(levelId: Int): Boolean = false
    }

    private fun setContent(
        onOpenStore: (() -> Unit)?,
        coins: Int = 123_456,
    ) {
        composeRule.setContent {
            LevelSelectScreen(
                progress = FakeProgress(coins),
                onPlayLevel = {},
                onBack = {},
                onOpenArmory = {},
                onOpenStore = onOpenStore,
            )
        }
    }

    private fun badge() = composeRule.onNodeWithTag("coin_store_badge", useUnmergedTree = true)

    // =================================================================================
    // 1. Depo girisi VAR — rozet gorunur ve dokunma TAM BIR KEZ cagriya ulasir
    // =================================================================================

    @Test
    fun tappingTheCoinChipOpensTheStoreExactlyOnce() {
        var opened = 0
        setContent(onOpenStore = { opened++ })

        badge().assertIsDisplayed()
        composeRule.onNodeWithTag("coin_chip").assertIsDisplayed().assertHasClickAction()
        assertEquals("dokunmadan once depo acilmamali", 0, opened)

        composeRule.onNodeWithTag("coin_chip").performClick()

        assertEquals("coin cipine dokunma depoya TAM BIR KEZ ulasmali", 1, opened)
    }

    // =================================================================================
    // 2. Depo girisi YOK — cip atil, rozet yok, bakiye durur
    // =================================================================================

    @Test
    fun withoutAStoreEntryTheChipIsInertHasNoBadgeAndStillShowsTheBalance() {
        setContent(onOpenStore = null, coins = 4_321)

        composeRule.onNodeWithTag("coin_chip").assertIsDisplayed()
        badge().assertDoesNotExist()

        // Bilgi kaybi YOK: bakiye her durumda okunur.
        composeRule.onNodeWithTag("coin_amount", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("4321")

        // Dokunulabilir DEGIL: `clickable(enabled = false)` semantikte ya hic
        // OnClick yayinlamaz ya da Disabled isaretler. Ikisi de "tiklanamaz".
        val config = composeRule.onNodeWithTag("coin_chip").fetchSemanticsNode().config
        val clickable = config.contains(SemanticsActions.OnClick) &&
            !config.contains(SemanticsProperties.Disabled)
        assertFalse("depo girisi yokken coin cipi tiklanabilir olmamali", clickable)
    }

    @Test
    fun theStoreEntryFollowsTheCallbackWhenItAppearsAndDisappears() {
        // Ayni ekran, calisirken: giris eklenince rozet + dokunulabilirlik gelir,
        // kalkinca ikisi de gider — bakiye her iki durumda da yerinde.
        val entry = mutableStateOf<(() -> Unit)?>(null)
        var opened = 0
        composeRule.setContent {
            LevelSelectScreen(
                progress = FakeProgress(777),
                onPlayLevel = {},
                onBack = {},
                onOpenArmory = {},
                onOpenStore = entry.value,
            )
        }
        badge().assertDoesNotExist()

        composeRule.runOnIdle { entry.value = { opened++ } }
        badge().assertIsDisplayed()
        composeRule.onNodeWithTag("coin_chip").assertHasClickAction().performClick()
        assertEquals(1, opened)

        composeRule.runOnIdle { entry.value = null }
        badge().assertDoesNotExist()
        composeRule.onNodeWithTag("coin_amount", useUnmergedTree = true).assertTextEquals("777")
        composeRule.onNodeWithTag("coin_chip").performClick()
        assertEquals("giris kalkinca dokunma eski callback'i cagirmamali", 1, opened)
    }

    // =================================================================================
    // 3. GEOMETRI — 740x360 dp yatayda cip nereye oturuyor
    //
    // ROBOLECTRIC'IN SINIRI, ACIKCA: burada metin GENISLIGI olculemez.
    // Robolectric (LEGACY graphics) gercek font metrigi calistirmaz;
    // `Paint.measureText` sahte bir deger doner ve olculen "COIN" etiketi 4 dp
    // cikar. Asagidaki kontroller **sabit boyutlu** ogeler icin gecerlidir
    // (rozet 20 dp daire, aralik 8 dp, cip ic dolgusu 12 dp, 44 dp yukseklik
    // tabani) ve metin tasmasini KANITLAMAZ. Metin butcesi karakter sayisindan
    // turetilerek `LevelSelectHeaderWidthBudgetTest`te olculuyor.
    // =================================================================================

    @Test
    fun theCoinChipGeometryStaysInsideTheScreenOnTheNarrowLandscapeDevice() {
        setContent(onOpenStore = {})

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val chip = composeRule.onNodeWithTag("coin_chip").getUnclippedBoundsInRoot()
        val badge = badge().getUnclippedBoundsInRoot()

        // Once OLCU YUZEYININ KENDISINI dogrula: `@Config(qualifiers=...)`
        // sessizce uygulanmazsa asagidaki kontroller HICBIR SEY olcmezdi.
        assertEquals(
            "olcum yuzeyi 740 dp genis olmali",
            740f, (root.right - root.left).value, 0.5f,
        )
        assertEquals(
            "olcum yuzeyi 360 dp yuksek olmali",
            360f, (root.bottom - root.top).value, 0.5f,
        )

        assertTrue(
            "coin cipi ekranin sagindan tasiyor: ${chip.right} > ${root.right}",
            chip.right <= root.right,
        )
        assertTrue(
            "coin cipi ekranin solundan tasiyor: ${chip.left} < ${root.left}",
            chip.left >= root.left,
        )
        assertTrue(
            "coin cipi ekranin ustunden tasiyor: ${chip.top} < ${root.top}",
            chip.top >= root.top,
        )
        // Kirpilmis bir "+" gorunur ama okunmaz olurdu.
        assertTrue(
            "rozet cipin disina tasiyor: ${badge.right} > ${chip.right}",
            badge.right <= chip.right,
        )
    }

    @Test
    fun theStoreBadgeCostsExactlyTheReservedTwentyEightDp() {
        // ROZETIN BEDELI OLCULUR, TAHMIN EDILMEZ.
        //
        // `LevelSelectHeaderWidthBudgetTest.COIN_BADGE_DP` baslik satirinda ne
        // kadar bos yer kaldigini bu sayidan hesapliyor. Rozet buyurse (ornegin
        // metne donuse) bu test aninda kirilir ve butce yeniden hesaplanmadan
        // degisiklik gecemez.
        val entry = mutableStateOf<(() -> Unit)?>(null)
        composeRule.setContent {
            LevelSelectScreen(
                progress = FakeProgress(9_999_999),
                onPlayLevel = {},
                onBack = {},
                onOpenArmory = {},
                onOpenStore = entry.value,
            )
        }

        val plain = composeRule.onNodeWithTag("coin_chip").getUnclippedBoundsInRoot()
        val plainWidth = plain.right - plain.left

        composeRule.runOnIdle { entry.value = {} }

        val withBadge = composeRule.onNodeWithTag("coin_chip").getUnclippedBoundsInRoot()
        val badgeCost = (withBadge.right - withBadge.left) - plainWidth

        assertEquals(
            "rozetin bedeli 20 dp daire + 8 dp aralik = 28 dp olmali",
            28f, badgeCost.value, 0.5f,
        )
        // Cip SAGA capali: rozet eklendiginde sag kenari OYNAMAMALI.
        assertEquals(
            "rozet eklenince cipin sag kenari kaymamali",
            plain.right.value, withBadge.right.value, 0.5f,
        )
    }

    @Test
    fun theChipsNeverOverlapEachOtherOnTheNarrowScreen() {
        setContent(onOpenStore = {}, coins = 9_999_999)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val chip = composeRule.onNodeWithTag("coin_chip").getUnclippedBoundsInRoot()
        val armory = composeRule.onNodeWithTag("open_armory").getUnclippedBoundsInRoot()

        assertTrue(
            "coin cipi tasiyor: ${chip.right} > ${root.right}",
            chip.right <= root.right,
        )
        assertTrue(
            "Cephanelik cipi sola tasiyor: ${armory.left} < ${root.left}",
            armory.left >= root.left,
        )
        assertTrue(
            "Cephanelik ve Coin cipleri ust uste biniyor",
            armory.right <= chip.left,
        )
    }

    // =================================================================================
    // 4. DOKUNMA HEDEFI — coin cipi 44 dp'nin altina inmez (her iki durumda)
    // =================================================================================

    @Test
    fun theCoinChipIsAtLeastFortyFourDpTallWithAndWithoutTheStoreEntry() {
        // Ana kaynakta `defaultMinSize(minHeight = 44.dp)`. Reklam seridi
        // kalkinca bu ekran dikeyde 44 dp kazandi; cip o payla buyudu.
        // Iki durum da olculur (rozetli / rozetsiz).
        //
        // SINIR (mutasyonla OLCULDU): Robolectric LEGACY altinda cipin dogal
        // yuksekligi zaten 47 dp cikiyor; `defaultMinSize` silinse bu test
        // YINE gecer. Yani bu test "cip Robolectric'te >= 44 dp cizilir" der,
        // "min-size korunuyor" DEMEZ. Gercek font metrigiyle dogal yukseklik
        // (11/15 sp metin + 12 dp dolgu) 44 dp'nin altinda kalabilir; o kanit
        // cihaz/emulator ekran goruntusu ister.
        val entry = mutableStateOf<(() -> Unit)?>({})
        composeRule.setContent {
            LevelSelectScreen(
                progress = FakeProgress(123_456),
                onPlayLevel = {},
                onBack = {},
                onOpenArmory = {},
                onOpenStore = entry.value,
            )
        }

        fun chipHeightDp(): Float {
            val b = composeRule.onNodeWithTag("coin_chip").getUnclippedBoundsInRoot()
            return (b.bottom - b.top).value
        }

        val withStore = chipHeightDp()
        assertTrue("depo girisli coin cipi $withStore dp — 44 dp'nin altinda", withStore >= 44f)

        composeRule.runOnIdle { entry.value = null }
        val withoutStore = chipHeightDp()
        assertTrue("depo girissiz coin cipi $withoutStore dp — 44 dp'nin altinda", withoutStore >= 44f)
        println("COIN_CHIP_HEIGHT_DP withStore=$withStore withoutStore=$withoutStore")
    }

    // =================================================================================
    // 5. Rozet TEK KARAKTER kalmali
    // =================================================================================

    @Test
    fun theBadgeContentStaysASingleUntranslatedCharacter() {
        // Rozete metin konursa ("DEPO") ceviriler baslik satirini tasirir;
        // kaynak `translatable=false` oldugu icin ceviri dosyasinda gorunmez,
        // yani tasma ancak cihazda fark edilirdi.
        val text = RuntimeEnvironment.getApplication()
            .getString(R.string.level_coin_topup_badge)
        assertEquals("rozet tek karakter olmali, bulundu: '$text'", 1, text.length)
    }
}
