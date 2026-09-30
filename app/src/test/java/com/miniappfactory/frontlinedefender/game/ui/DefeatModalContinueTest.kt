package com.miniappfactory.frontlinedefender.game.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.ContinueDecision
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * =============================================================================
 * YENILGI MODALI — "DEVAM ET (COIN)" SATIRI
 * =============================================================================
 *
 * Reklam kalktiktan sonra yenilgide ucuncu bir yol var: coin karsiliginda
 * bolumu yenilgi noktasindan surdurmek. `DefeatModal(continueOffer, onContinue)`
 * satiri kararin ([ContinueDecision]) TIPINDEN cizer; modal kendi bakiye
 * karsilastirmasini YAPMAZ. Bu dosya o karar -> piksel yolunu kanitlar:
 *
 *  - `null`            -> satir HIC yok; iki eski buton calismaya devam eder
 *  - `Allowed`         -> etkin buton + bakiye/can yazisi; dokunma SADECE onContinue
 *  - `InsufficientCoins` -> buton VAR ama devre disi + eksik ve bakiye yazili
 *  - `ReserveLocked`   -> buton VAR ama devre disi + rezerv nedeni yazili
 *  - `AlreadyUsed`     -> buton YOK, "zaten kullanildi" yazisi VAR
 *  - HER durumda TEKRAR DENE ve ANA MENU >= 44 dp, ekran icinde ve dokunulabilir.
 *
 * ## Olcu yuzeyi
 * Galaxy S8 yatay = 740x360 dp. Modalin yukseklik tavani 360 - 24 = 336 dp
 * (`heightIn(max = maxHeight - 24.dp)`). Uc satir (baslik+govde, DEVAM, butonlar)
 * bu butceye sigmak ZORUNDA; sigmazsa TEKRAR DENE ekran disinda kalir ve
 * oyuncunun yenilgiden cikis yolu kaybolur (2026-08-26 cihaz bulgusu).
 *
 * ## ROBOLECTRIC'IN SINIRI, ACIKCA
 * Robolectric LEGACY graphics gercek font metrigi calistirmaz: metin genisligi
 * ve satir sayisi SAHTE olculur. Bu yuzden buradaki geometri **sabit boyutlu
 * ogeler** (sanat butonlarinin en-boy orani, dolgular, hizalama) icin gecerlidir.
 * "Uzun Turkce etiket butonun icine sigiyor mu" ve "govde metni 3 satira
 * ciktiginda ne oluyor" sorulari BURADA KANITLANMAZ; onlar cihaz/emulator
 * ekran goruntusu ister (`AutoShrinkText` kucultme + `verticalScroll` govdesi).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land")
class DefeatModalContinueTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var continues = 0
    private var retries = 0
    private var menus = 0

    @Before
    fun setUp() {
        audio = AudioManager(context)
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(context), audio)
        continues = 0
        retries = 0
        menus = 0
    }

    @After
    fun tearDown() {
        audio.release()
    }

    private fun show(offer: ContinueOffer?) {
        composeRule.setContent {
            DefeatModal(
                gameEngine = engine,
                onRetry = { retries++ },
                onMainMenu = { menus++ },
                continueOffer = offer,
                onContinue = { continues++ },
            )
        }
    }

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun heightDp(tag: String): Float {
        val b = node(tag).getUnclippedBoundsInRoot()
        return (b.bottom - b.top).value
    }

    /** Ekran (kok) sinirlari 740x360 dp mi — sessizce varsayilan ekrana dusmedik mi. */
    private fun assertMeasuringSurfaceIs740x360() {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals("olcum yuzeyi 740 dp genis olmali", 740f, (root.right - root.left).value, 0.5f)
        assertEquals("olcum yuzeyi 360 dp yuksek olmali", 360f, (root.bottom - root.top).value, 0.5f)
    }

    /**
     * TEKRAR DENE ve ANA MENU: >= 44 dp, ekranin ICINDE ve dokununca cagriya ULASIR.
     * Her durumda cagrilir; "DEVAM satiri geldi, cikis butonlari itildi" hatasini yakalar.
     */
    private fun assertExitButtonsAreReachable() {
        assertMeasuringSurfaceIs740x360()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        for (tag in listOf("defeat_menu_button", "defeat_retry_button")) {
            val h = heightDp(tag)
            assertTrue("$tag $h dp — 44 dp dokunma tabaninin altinda", h >= 44f)
            val b = node(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag ekranin altindan tasiyor: ${b.bottom} > ${root.bottom}", b.bottom <= root.bottom)
            assertTrue("$tag ekranin ustunden tasiyor: ${b.top} < ${root.top}", b.top >= root.top)
            assertTrue("$tag ekranin sagindan tasiyor", b.right <= root.right)
            assertTrue("$tag ekranin solundan tasiyor", b.left >= root.left)
            node(tag).assertHasClickAction().assertIsEnabled()
        }
        val retryBefore = retries
        val menuBefore = menus
        val continueBefore = continues
        node("defeat_retry_button").performClick()
        assertEquals("TEKRAR DENE tam bir kez cagrilmali", retryBefore + 1, retries)
        node("defeat_menu_button").performClick()
        assertEquals("ANA MENU tam bir kez cagrilmali", menuBefore + 1, menus)
        assertEquals("cikis butonlari DEVAM'i tetiklememeli", continueBefore, continues)
    }

    /** Butonun etiketi: gorunen metin (birlesik agac). */
    private fun labelText(tag: String): String {
        val cfg = node(tag).fetchSemanticsNode().config
        return cfg.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
    }

    /** `ArtSecondaryButton(contentDescription = label)` tik-etiketi olarak yayinlanir. */
    private fun clickLabel(tag: String): String? =
        node(tag).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

    private val allowed = ContinueDecision.Allowed(
        price = 190,
        lives = EconomyConfig.CONTINUE_LIVES,
        balanceAfter = 1_044,
    )
    private val allowedOffer = ContinueOffer(allowed, balance = 1_234)

    // =================================================================================
    // 1. Teklif YOK — eski davranis birebir
    // =================================================================================

    @Test
    fun noOfferDrawsNoContinueRowAndTheTwoOriginalButtonsStillWork() {
        show(null)

        node("defeat_continue_button").assertDoesNotExist()
        node("defeat_continue_caption").assertDoesNotExist()
        node("defeat_menu_button").assertExists()
        node("defeat_retry_button").assertExists()
        assertExitButtonsAreReachable()
        assertEquals("teklif yokken DEVAM cagrilmamali", 0, continues)
    }

    @Test
    fun aDisabledContinueSwitchDrawsNothingEither() {
        // `ContinueDecision.Disabled` (kill switch) = fiyat yok, yazi yok: ne buton
        // ne aciklama. Bos bir "yazisiz satir" da cizilmemeli.
        show(ContinueOffer(ContinueDecision.Disabled, balance = 500))

        node("defeat_continue_button").assertDoesNotExist()
        node("defeat_continue_caption").assertDoesNotExist()
        assertExitButtonsAreReachable()
    }

    // =================================================================================
    // 2. Allowed — etkin, fiyat etikette, yazi bakiye + can, dokunma SADECE onContinue
    // =================================================================================

    @Test
    fun allowedShowsAnEnabledTallEnoughButtonWithThePriceAndFiresOnlyOnContinue() {
        show(allowedOffer)
        assertMeasuringSurfaceIs740x360()

        node("defeat_continue_button").assertExists().assertIsEnabled().assertHasClickAction()
        val h = heightDp("defeat_continue_button")
        assertTrue("DEVAM butonu $h dp — 44 dp dokunma tabaninin altinda", h >= 44f)

        // Fiyat etikette: hem gorunen metin hem tik-etiketi (contentDescription).
        val expectedLabel = str(R.string.defeat_continue_label, allowed.price)
        assertTrue("fiyat gorunen etikette olmali: '${labelText("defeat_continue_button")}'",
            labelText("defeat_continue_button").contains("${allowed.price}"))
        assertTrue("fiyat tik-etiketinde olmali: '${clickLabel("defeat_continue_button")}'",
            clickLabel("defeat_continue_button")?.contains("${allowed.price}") == true)
        assertEquals(expectedLabel, clickLabel("defeat_continue_button"))
        node("defeat_continue_button").assertTextEquals(expectedLabel)

        // Aciklama: bakiye + geri gelecek can.
        node("defeat_continue_caption").assertTextEquals(
            str(R.string.defeat_continue_balance, allowedOffer.balance, allowed.lives)
        )

        node("defeat_continue_button").performClick()
        assertEquals("DEVAM tam bir kez cagrilmali", 1, continues)
        assertEquals("DEVAM TEKRAR DENE'yi tetiklememeli", 0, retries)
        assertEquals("DEVAM ANA MENU'yu tetiklememeli", 0, menus)

        assertExitButtonsAreReachable()
    }

    @Test
    fun theContinueRowAndBothExitButtonsFitTheThreeHundredThirtySixDpBudget() {
        show(allowedOffer)
        assertMeasuringSurfaceIs740x360()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val cont = node("defeat_continue_button").getUnclippedBoundsInRoot()
        val caption = node("defeat_continue_caption").getUnclippedBoundsInRoot()
        val menu = node("defeat_menu_button").getUnclippedBoundsInRoot()
        val retry = node("defeat_retry_button").getUnclippedBoundsInRoot()
        val plate = node("defeat_title_plate").getUnclippedBoundsInRoot()

        // Modal yuksekligi (kabaca): baslik plakasinin ustunden butonlarin altina.
        // Ust sinir 336 dp'dir; yuzeyin kendisi test etiketi tasimadigi icin
        // icerigin ust-alt kenari olculur.
        val contentSpan = (retry.bottom - plate.top).value
        assertTrue("icerik $contentSpan dp — 336 dp modal butcesini asiyor", contentSpan <= 336f)

        assertTrue("TEKRAR DENE ekran disina tasti: ${retry.bottom} > ${root.bottom}", retry.bottom <= root.bottom)
        assertTrue("ANA MENU ekran disina tasti: ${menu.bottom} > ${root.bottom}", menu.bottom <= root.bottom)
        // Modalin 12 dp dis dolgusu: butonlarin altinda en az bu kadar bosluk kalmali.
        assertTrue(
            "butonlar modal dolgusunu (12 dp) yiyor: alt ${retry.bottom}, ekran ${root.bottom}",
            retry.bottom <= root.bottom - 12.dp,
        )

        // Satirlar ust uste BINMEZ ve sirayla dizilir: DEVAM < yazi < cikis butonlari.
        assertTrue("DEVAM butonu ile yazisi ust uste biniyor", cont.bottom <= caption.top)
        assertTrue("DEVAM yazisi ile cikis butonlari ust uste biniyor", caption.bottom <= retry.top)
        assertTrue("DEVAM butonu cikis butonlarinin altina dustu", cont.bottom <= menu.top)
        // Birincil eylem (TEKRAR DENE) sagda, ikincil (ANA MENU) solda — sira degismedi.
        assertTrue("sira degisti: ANA MENU TEKRAR DENE'nin solunda olmali", menu.right <= retry.left)
        println(
            "DEFEAT_MODAL_GEOMETRY span=${contentSpan}dp continueH=${(cont.bottom - cont.top).value} " +
                "retryH=${(retry.bottom - retry.top).value} retryBottom=${retry.bottom} root=${root.bottom}"
        )
    }

    // =================================================================================
    // 3. InsufficientCoins — buton VAR ama devre disi; eksik VE bakiye yazili
    // =================================================================================

    @Test
    fun insufficientCoinsShowsADisabledButtonWithTheShortfallAndTheBalance() {
        val decision = ContinueDecision.InsufficientCoins(price = 190, shortfall = 73)
        val offer = ContinueOffer(decision, balance = 117)
        show(offer)

        node("defeat_continue_button").assertExists().assertIsNotEnabled()
        node("defeat_continue_button").assertTextEquals(str(R.string.defeat_continue_label, decision.price))

        node("defeat_continue_caption").assertTextEquals(
            str(R.string.defeat_continue_short, decision.shortfall, offer.balance)
        )
        val caption = node("defeat_continue_caption").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text }
        assertTrue("yazi eksik miktari icermeli: '$caption'", caption.contains("73"))
        assertTrue("yazi bakiyeyi icermeli: '$caption'", caption.contains("117"))

        node("defeat_continue_button").performClick()
        assertEquals("yetersiz coin'de dokunma HICBIR sey tetiklememeli (DEVAM)", 0, continues)
        assertEquals("yetersiz coin'de dokunma HICBIR sey tetiklememeli (TEKRAR)", 0, retries)
        assertEquals("yetersiz coin'de dokunma HICBIR sey tetiklememeli (MENU)", 0, menus)

        assertExitButtonsAreReachable()
    }

    // =================================================================================
    // 4. ReserveLocked — devre disi + rezerv nedeni
    // =================================================================================

    @Test
    fun reserveLockedShowsADisabledButtonWithTheReserveCaption() {
        val decision = ContinueDecision.ReserveLocked(price = 190, reserve = 300, shortfall = 60)
        show(ContinueOffer(decision, balance = 330))

        node("defeat_continue_button").assertExists().assertIsNotEnabled()
        node("defeat_continue_button").assertTextEquals(str(R.string.defeat_continue_label, decision.price))
        node("defeat_continue_caption").assertTextEquals(str(R.string.defeat_continue_reserve))

        node("defeat_continue_button").performClick()
        assertEquals("rezerv kilidinde dokunma DEVAM'i tetiklememeli", 0, continues)
        assertEquals(0, retries)
        assertEquals(0, menus)

        assertExitButtonsAreReachable()
    }

    // =================================================================================
    // 5. AlreadyUsed — buton YOK, "kullanildi" yazisi VAR
    // =================================================================================

    @Test
    fun alreadyUsedDrawsNoButtonButShowsTheUsedCaption() {
        show(ContinueOffer(ContinueDecision.AlreadyUsed, balance = 5_000))

        node("defeat_continue_button").assertDoesNotExist()
        node("defeat_continue_caption").assertExists()
            .assertTextEquals(str(R.string.defeat_continue_used))

        assertExitButtonsAreReachable()
        assertEquals("kullanilmis hakta DEVAM cagrilmamali", 0, continues)
    }

    // =================================================================================
    // 6. Her durumda cikis yolu (durum matrisi) — yeni bir dal eklenirse burasi zorlar
    // =================================================================================

    @Test
    fun everyDecisionKeepsBothExitButtonsAtOrAboveFortyFourDpAndOnScreen() {
        val states = listOf<Pair<String, ContinueOffer?>>(
            "null" to null,
            "Disabled" to ContinueOffer(ContinueDecision.Disabled, 100),
            "Allowed" to allowedOffer,
            "InsufficientCoins" to ContinueOffer(ContinueDecision.InsufficientCoins(190, 73), 117),
            "ReserveLocked" to ContinueOffer(ContinueDecision.ReserveLocked(190, 300, 60), 330),
            "AlreadyUsed" to ContinueOffer(ContinueDecision.AlreadyUsed, 5_000),
        )
        // Karar tipleri sealed: yeni bir alt tip eklenirse `when` derlenmez ve bu
        // liste guncellenmeden test dosyasi derlenmez.
        for ((name, offer) in states) {
            val kind: String = when (offer?.decision) {
                null -> "null"
                is ContinueDecision.Allowed -> "Allowed"
                is ContinueDecision.Disabled -> "Disabled"
                is ContinueDecision.AlreadyUsed -> "AlreadyUsed"
                is ContinueDecision.InsufficientCoins -> "InsufficientCoins"
                is ContinueDecision.ReserveLocked -> "ReserveLocked"
            }
            assertEquals(name, kind)
        }

        // Her durum AYRI bir kompozisyon: tek testte tekrar setContent yasak, bu yuzden
        // tek kompozisyonda durum degistirilir.
        val current = mutableStateOf<ContinueOffer?>(null)
        composeRule.setContent {
            DefeatModal(
                gameEngine = engine,
                onRetry = { retries++ },
                onMainMenu = { menus++ },
                continueOffer = current.value,
                onContinue = { continues++ },
            )
        }
        for ((name, offer) in states) {
            composeRule.runOnIdle { current.value = offer }
            composeRule.waitForIdle()
            try {
                assertExitButtonsAreReachable()
            } catch (e: AssertionError) {
                throw AssertionError("durum '$name': ${e.message}", e)
            }
        }
    }

    // =================================================================================
    // 7. Turkce — ayni satir, ayni kurallar
    // =================================================================================

    @Test
    @Config(sdk = [33], qualifiers = "tr-rTR-w740dp-h360dp-land")
    fun turkishLocaleKeepsThePriceInTheLabelAndTheExitButtonsReachable() {
        show(allowedOffer)

        // Yerel ayar GERCEKTEN Turkce mi (yoksa asagidaki kontroller Ingilizceyi olcerdi).
        val label = labelText("defeat_continue_button")
        assertTrue("Turkce etiket bekleniyordu, bulundu: '$label'", label.contains("DEVAM ET"))
        assertTrue("fiyat Turkce etikette de olmali: '$label'", label.contains("${allowed.price}"))
        node("defeat_continue_button").assertTextEquals(str(R.string.defeat_continue_label, allowed.price))
        node("defeat_continue_caption").assertTextEquals(
            str(R.string.defeat_continue_balance, allowedOffer.balance, allowed.lives)
        )
        assertTrue(heightDp("defeat_continue_button") >= 44f)

        assertExitButtonsAreReachable()
    }
}
