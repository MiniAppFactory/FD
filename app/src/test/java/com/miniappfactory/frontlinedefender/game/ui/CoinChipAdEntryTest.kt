package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.miniappfactory.frontlinedefender.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * COIN CIPINDEN ODULLU REKLAM — EKRANDA GERCEKTEN VAR MI VE SIGIYOR MU?
 *
 * `CoinTopUpPlacementTest` odul/tavan sozlesmesini kanitliyor; bu dosya o
 * sozlesmenin **piksele ulastigini** kanitliyor. Ikisi ayri sorulardir ve bu
 * depoda ikincisi defalarca kaybedildi (`SettingsScreenComposeTest` KDoc'u:
 * "dogru hesaplaniyordu, dogru okunuyordu, ama hicbir composable'a
 * ulasmiyordu").
 *
 * ## Neden 740x360 dp
 * Test cihazi Galaxy S8 ve oyun `sensorLandscape`: gercek yuzey **740x360 dp**.
 * Bolum secim ekraninin baslik satiri bu genislikte zaten dolu (geri, baslik,
 * Gorevler, Cephanelik, Coin). Coin cipine bir rozet eklemek, bu depoda dort
 * kez cikmis olan tasma hatasinin tam adayidir — bu yuzden rozet TEK KARAKTER
 * ve `translatable=false`, ve bu test onun ekrandan tasmadigini olcer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land")
class CoinChipAdEntryTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Ekranin ihtiyac duydugu her seyi sabit veren sahte ilerleme.
     *
     * `todaysMissions` varsayilan olarak bos, yani Gorevler cipi cizilmez.
     * Tasma olcumu bu yuzden [theHeaderStillFitsWhenEveryChipIsDrawn] ile
     * birlikte okunmalidir: orada gorev cipi de cizilir ve bakiye 7 hanelidir.
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
        offered: Boolean,
        coins: Int = 123_456,
        onRequest: () -> Unit = {},
    ) {
        composeRule.setContent {
            LevelSelectScreen(
                progress = FakeProgress(coins),
                onPlayLevel = {},
                onBack = {},
                onOpenArmory = {},
                coinAdOffered = offered,
                onCoinAdRequested = onRequest,
            )
        }
    }

    private fun badge() = composeRule.onNodeWithTag("coin_ad_badge", useUnmergedTree = true)

    // =================================================================================
    // 1. Teklif ACIK — rozet gorunur ve dokunma cagriya ULASIR
    // =================================================================================

    @Test
    fun tappingTheCoinChipOpensTheRewardedOffer() {
        var requested = 0
        setContent(offered = true, onRequest = { requested++ })

        badge().assertIsDisplayed()
        composeRule.onNodeWithTag("coin_chip").assertIsDisplayed().performClick()

        assertEquals("coin cipine dokunma teklif yuzeyine ulasmali", 1, requested)
    }

    // =================================================================================
    // 2. Teklif TUKENMIS — bakiye durur, yalnizca reklam yolu kapanir
    // =================================================================================

    @Test
    fun anExhaustedOfferHidesOnlyTheBadgeAndNeverTheBalance() {
        var requested = 0
        setContent(offered = false, onRequest = { requested++ })

        // Bakiye HER DURUMDA okunur kalir: reklam teklifinin tukenmesi bir
        // bilgi kaybina donusemez.
        composeRule.onNodeWithTag("coin_chip").assertIsDisplayed()
        badge().assertDoesNotExist()

        // Dokunma sessizce YUTULUR — hicbir sey acilmaz, hicbir sey kaybolmaz.
        composeRule.onNodeWithTag("coin_chip").performClick()
        assertEquals("teklif tukenmisken dokunma teklif acmamali", 0, requested)
    }

    // =================================================================================
    // 3. GEOMETRI — 740x360 dp yatayda rozet nereye oturuyor
    //
    // ROBOLECTRIC'IN SINIRI, ACIKCA: burada metin GENISLIGI olculemez.
    // Robolectric gercek font metrigi calistirmaz; `Paint.measureText` sahte bir
    // deger doner ve olculen "COIN" etiketi 4 dp cikar. Yani asagidaki
    // kontroller **sabit boyutlu** ogeler icin gecerlidir (rozet 20 dp daire,
    // aralik 8 dp, cip ic dolgusu 12 dp) ve metin tasmasini KANITLAMAZ.
    //
    // Metin genisligi butcesi bu yuzden ayri bir dosyada, karakter sayisindan
    // turetilerek olculuyor: `LevelSelectHeaderWidthBudgetTest`. Bu ayrim
    // bilerek yapildi — "test gecti" ile "cihazda sigiyor" ayni sey degil ve bu
    // depoda ikisini karistirmak dort kez tasma hatasi uretti.
    // =================================================================================

    @Test
    fun theCoinChipGeometryStaysInsideTheScreenOnTheNarrowLandscapeDevice() {
        setContent(offered = true)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val chip = composeRule.onNodeWithTag("coin_chip").getUnclippedBoundsInRoot()
        val badge = badge().getUnclippedBoundsInRoot()

        // Once OLCU YUZEYININ KENDISINI dogrula. `@Config(qualifiers=...)`
        // sessizce uygulanmazsa Robolectric varsayilan telefon boyutunda kosar
        // ve asagidaki tasma kontrolleri HICBIR SEY olcmez — testin gecmesi
        // yanlis bir guven verirdi.
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
        // Kirpilmis bir "+" gorunur ama okunmaz olurdu.
        assertTrue(
            "rozet cipin disina tasiyor: ${badge.right} > ${chip.right}",
            badge.right <= chip.right,
        )
    }

    @Test
    fun theBadgeCostsExactlyTheReservedTwentyEightDp() {
        // ROZETIN BEDELI OLCULUR, TAHMIN EDILMEZ.
        //
        // `LevelSelectHeaderWidthBudgetTest` baslik satirinda ne kadar bos yer
        // kaldigini karakter sayisindan hesapliyor; o hesabin girdisi bu
        // sayidir. Rozet buyurse (ornegin metne donuse) bu test aninda kirilir
        // ve butce yeniden hesaplanmadan degisiklik gecemez.
        val offered = mutableStateOf(false)
        composeRule.setContent {
            LevelSelectScreen(
                progress = FakeProgress(9_999_999),
                onPlayLevel = {},
                onBack = {},
                onOpenArmory = {},
                coinAdOffered = offered.value,
                onCoinAdRequested = {},
            )
        }

        val plain = composeRule.onNodeWithTag("coin_chip").getUnclippedBoundsInRoot()
        val plainWidth = plain.right - plain.left

        composeRule.runOnIdle { offered.value = true }

        val withBadge = composeRule.onNodeWithTag("coin_chip").getUnclippedBoundsInRoot()
        val badgeCost = (withBadge.right - withBadge.left) - plainWidth

        assertEquals(
            "rozetin bedeli 20 dp daire + 8 dp aralik = 28 dp olmali",
            28f, badgeCost.value, 0.5f,
        )
        // Cip SAGA capali: rozet eklendiginde sag kenari OYNAMAMALI, yoksa
        // "ikonlar saga yasli, bosluk kalmasin" kurali her acilis/kapanista
        // bozulurdu.
        assertEquals(
            "rozet eklenince cipin sag kenari kaymamali",
            plain.right.value, withBadge.right.value, 0.5f,
        )
    }

    @Test
    fun theChipsNeverOverlapEachOtherOnTheNarrowScreen() {
        setContent(offered = true, coins = 9_999_999)

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
    // 4. Rozet TEK KARAKTER kalmali
    // =================================================================================

    @Test
    fun theBadgeContentStaysASingleUntranslatedCharacter() {
        // Rozete metin konursa ("REKLAM IZLE") ceviriler baslik satirini
        // tasirir; kaynak `translatable=false` oldugu icin de ceviri dosyasinda
        // gorunmez, yani tasma ancak cihazda fark edilirdi.
        val text = RuntimeEnvironment.getApplication()
            .getString(R.string.level_coin_topup_badge)
        assertEquals("rozet tek karakter olmali, bulundu: '$text'", 1, text.length)
    }
}
