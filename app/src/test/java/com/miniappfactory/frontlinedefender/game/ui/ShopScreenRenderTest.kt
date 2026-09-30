package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.ClockProvider
import com.miniappfactory.frontlinedefender.game.economy.ClockSample
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import com.miniappfactory.frontlinedefender.game.economy.UpgradeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * =============================================================================
 * CEPHANELIK — UC KART TURU, ORTAK CERCEVE, DOKUNMA HEDEFI
 * =============================================================================
 *
 * Bu depoda Cephanelik kartlarinin geometrisine ve tiklama davranisina bakan
 * TEK test bu dosyadir (once hicbiri yoktu).
 *
 * KAPSAM SINIRI — DURUST NOT: Robolectric `graphicsMode` LEGACY calisir ve
 * metin GENISLIGINI / gercek piksel yerlesimini OLCEMEZ (HANDOVER §4 madde 12).
 * Bu test yalnizca DUZEN geometrisini (kart boyutu, dokunma hedefi, dikey
 * butce) ve DAVRANISI (etiketler, tiklama, satin alma) kanitlar. Yazinin
 * bandin icinde durdugu, ayraclari kesmedigi ve TR/EN'de sigdigi CIHAZDA
 * (Galaxy S8, 740x360 dp) dogrulanmalidir — bu calismada cihaz YOKTU.
 *
 * Ekran: 740x360 dp yatay, yogunluk mdpi (1 px = 1 dp; bkz.
 * `ArtButtonTouchTargetTest`'teki `qualifiers` notu).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land")
class ShopScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FixedClock : ClockProvider {
        override fun sample(): ClockSample = ClockSample(
            epochDay = 20_500L,
            wallClockMs = 20_500L * EconomyConfig.MS_PER_DAY,
            elapsedRealtimeMs = 1_000L,
            bootId = 1L,
        )
    }

    private fun newProgress() =
        CampaignProgressImpl(SaveManager(InMemoryKeyValueStore()), FixedClock())

    private fun show(
        progress: CampaignProgressImpl = newProgress(),
        onBack: () -> Unit = {},
        onOpenStore: (() -> Unit)? = {}
    ) {
        composeRule.setContent {
            UpgradeShopScreen(progress = progress, onBack = onBack, onOpenStore = onOpenStore)
        }
    }

    private fun allCardTags(): List<String> =
        UpgradeLine.entries.map { "upgrade_card_${it.name}" } + "prestige_card" + "store_entry_card"

    private fun allButtonTags(): List<String> =
        UpgradeLine.entries.map { "upgrade_buy_${it.name}" } + "prestige_buy" + "store_entry_open"

    // =========================================================================
    // Uc kart turu, ayni etiketlerle cizilir
    // =========================================================================

    @Test
    fun allThreeCardKindsRenderWithTheirTestTags() {
        show()

        // 5 yukseltme + 1 prestij + 1 ikmal deposu = 7 kart.
        assertEquals(7, allCardTags().size)
        allCardTags().forEach { composeRule.onNodeWithTag(it).assertExists() }
        // Buton bandi etiketleri KARTIN ICINDE: tiklanabilir kart alt agaci
        // birlestirir (satin alinabilir kart, ikmal karti), o yuzden birlestirilmemis
        // agacta aranir. Bu, eski ikmal kartinda da boyleydi.
        allButtonTags().forEach {
            composeRule.onNodeWithTag(it, useUnmergedTree = true).assertExists()
        }

        // Ust serit etiketleri de yerinde.
        composeRule.onNodeWithTag("shop_back").assertExists()
        composeRule.onNodeWithTag("shop_coin_chip").assertExists()
    }

    @Test
    fun storeCardIsAbsentWhenThereIsNoStoreEntryAndTheCoinChipStaysReadOnly() {
        show(onOpenStore = null)

        composeRule.onNodeWithTag("store_entry_card").assertDoesNotExist()
        composeRule.onNodeWithTag("store_entry_open", useUnmergedTree = true).assertDoesNotExist()
        // Diger alti kart ve cip yine var.
        composeRule.onNodeWithTag("prestige_card").assertExists()
        composeRule.onNodeWithTag("shop_coin_chip").assertExists()
    }

    // =========================================================================
    // Ortak cerceve: yedi kart AYNI boyutta (genislik-oncelikli)
    // =========================================================================

    /**
     * Yedi kartin hepsi 168 dp x ~224 dp ve ayni cerceveden geliyor; kart basina
     * ayri bir `width`/`fillMaxHeight` kalmadi (eski ikmal karti 150 dp x
     * fillMaxHeight'ti). Dikey butce: kartin alt kenari 360 dp ekranin icinde.
     */
    @Test
    fun allSevenCardsShareOneWidthFirstSizeAndFitTheVerticalBudget() {
        show()

        val expectedHeightDp = (ShopCardBands.CardWidth / ShopCardBands.ASPECT).value

        allCardTags().forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo()
            val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            val widthDp = with(composeRule.density) { node.size.width.toDp() }
            val heightDp = with(composeRule.density) { node.size.height.toDp() }
            val bottomDp = with(composeRule.density) { node.boundsInRoot.bottom.toDp() }

            assertEquals("$tag genisligi", ShopCardBands.CardWidth.value, widthDp.value, 0.5f)
            assertEquals("$tag yuksekligi", expectedHeightDp, heightDp.value, 0.5f)
            assertTrue(
                "$tag alt kenari $bottomDp — 360 dp ekranin disinda (dikey butce asildi)",
                bottomDp <= 360.dp
            )
        }
    }

    // =========================================================================
    // Ikmal Deposu karti: tek dokunus = tek geri cagri
    // =========================================================================

    @Test
    fun storeCardFiresItsCallbackExactlyOnceOnATap() {
        var opened = 0
        show(onOpenStore = { opened++ })

        composeRule.onNodeWithTag("store_entry_card").performScrollTo()
        composeRule.onNodeWithTag("store_entry_card").assertIsDisplayed()
        composeRule.onNodeWithTag("store_entry_card").assertHasClickAction()

        assertEquals("dokunmadan once cagri olmamali", 0, opened)
        composeRule.onNodeWithTag("store_entry_card").performClick()
        composeRule.waitForIdle()
        assertEquals("tek dokunus tek cagri", 1, opened)
    }

    /**
     * Ikmal karti metni: paket sayisi KATALOGDAN gelir (elle "5" degil) ve metin
     * REKLAMSIZDIR (oyundan tum reklamlar kalkiyor). Varsayilan yerel ayar: EN.
     */
    @Test
    fun storeCardTextIsAdFreeAndTheEnglishPackCountComesFromTheCatalog() {
        show()

        val packs = StoreCatalog.coinPacks.size
        composeRule.onNodeWithText("$packs PACKS", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Buy coin packs", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("OPEN", useUnmergedTree = true).assertExists()

        composeRule.onAllNodes(
            hasText("ads", substring = true, ignoreCase = true) and
                hasAnyAncestor(hasTestTag("store_entry_card")),
            useUnmergedTree = true
        ).assertCountEquals(0)
    }

    @Test
    @Config(sdk = [33], qualifiers = "tr-w740dp-h360dp-land")
    fun storeCardTextIsAdFreeInTurkishToo() {
        show()

        val packs = StoreCatalog.coinPacks.size
        composeRule.onNodeWithText("$packs PAKET", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Coin paketleri al", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("AÇ", useUnmergedTree = true).assertExists()

        composeRule.onAllNodes(
            hasText("reklam", substring = true, ignoreCase = true) and
                hasAnyAncestor(hasTestTag("store_entry_card")),
            useUnmergedTree = true
        ).assertCountEquals(0)
    }

    // =========================================================================
    // Davranis korunuyor: yukseltme / prestij satin alma kablolamasi
    // =========================================================================

    @Test
    fun upgradeCardIsInertWhenUnaffordableAndBuysWhenAffordable() {
        val progress = newProgress()
        show(progress = progress)

        // Bos cuzdan: satin alinamaz -> tiklama eylemi YOK (eski davranisla ayni).
        composeRule.onNodeWithTag("upgrade_card_FIREPOWER").assertHasNoClickAction()
        assertEquals(0, progress.metaUpgrades.rankOf(UpgradeLine.FIREPOWER))

        // Bolge kilidi / rezerv fiyati asacak kadar coin ver.
        assertTrue(progress.grantPurchasedCoins("shop-render-test", 50_000))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("upgrade_card_FIREPOWER").assertHasClickAction()
        composeRule.onNodeWithTag("upgrade_card_FIREPOWER").performClick()
        composeRule.waitForIdle()

        assertEquals(
            "tek dokunus tek kademe",
            1, progress.metaUpgrades.rankOf(UpgradeLine.FIREPOWER)
        )
    }

    @Test
    fun prestigeCardBuysOnceWhenAffordable() {
        val progress = newProgress()
        show(progress = progress)

        composeRule.onNodeWithTag("prestige_card").assertHasNoClickAction()
        assertEquals(0, progress.prestigeRank)

        assertTrue(progress.grantPurchasedCoins("shop-render-test-prestige", 200_000))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("prestige_card").performScrollTo()
        composeRule.onNodeWithTag("prestige_card").performClick()
        composeRule.waitForIdle()

        assertEquals("tek dokunus tek nisan", 1, progress.prestigeRank)
    }

    // =========================================================================
    // "Geri" dokunma hedefi >= 44 dp
    // =========================================================================

    /**
     * "GERI" once cıplak bir 15 sp `Text`ti (~18 dp; yazi tipinden tahmin,
     * calisma zamaninda olculmedi). Artik 44 dp'lik bir kutu. Hem yukseklik
     * hem GENISLIK sinanir: kisa bir etiket ("< GERI") dar kalabilirdi.
     */
    @Test
    fun backButtonIsAtLeastFortyFourDpInBothDimensionsAndFiresOnce() {
        var backs = 0
        show(onBack = { backs++ })

        val node = composeRule.onNodeWithTag("shop_back").fetchSemanticsNode()
        val heightDp = with(composeRule.density) { node.size.height.toDp() }
        val widthDp = with(composeRule.density) { node.size.width.toDp() }

        assertTrue("geri hedefi yuksekligi $heightDp < 44 dp", heightDp >= 44.dp)
        assertTrue("geri hedefi genisligi $widthDp < 44 dp", widthDp >= 44.dp)
        assertTrue(
            "kod sabiti ($ShopBackMinTouch) 44 dp tabaninin altina indirilmis",
            ShopBackMinTouch >= 44.dp
        )

        composeRule.onNodeWithTag("shop_back").performClick()
        composeRule.waitForIdle()
        assertEquals("tek dokunus tek geri", 1, backs)
    }

    /**
     * Geri hedefi buyudu ama serit BUYUMEDI: ust serit ~60 dp kalir (8 + 44 + 8).
     * Serit sismis olsaydi kartlar dikeyde daha az yer bulurdu.
     */
    @Test
    fun headerStripDidNotGrowBecauseOfTheBiggerBackTarget() {
        show()

        val back = composeRule.onNodeWithTag("shop_back").fetchSemanticsNode()
        val chip = composeRule.onNodeWithTag("shop_coin_chip").fetchSemanticsNode()
        // Geri hedefi ve coin cipi ayni serit icinde: ikisinin de alt kenari
        // 60 dp'yi (eski serit yuksekligi) gecmez.
        val backBottom = with(composeRule.density) { back.boundsInRoot.bottom.toDp() }
        val chipBottom = with(composeRule.density) { chip.boundsInRoot.bottom.toDp() }
        assertTrue("geri hedefi alt kenari $backBottom > 60 dp", backBottom <= 60.dp)
        assertTrue("coin cipi alt kenari $chipBottom > 60 dp", chipBottom <= 60.dp)
    }
}
