package com.miniappfactory.frontlinedefender.game.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ===========================================================================
 * PRESTIJ NISANI CIPI — SATIN ALINDIGI ANDA GORUNUYOR MU?
 * ===========================================================================
 *
 * CIHAZDA GORULEN HATA (2026-09-02, Galaxy S8 / API 24): Cephanelik'ten
 * 1.500 coin'e Bronz Nisan satin alindi, coin dustu (`eco_prestige_rank = 1`,
 * bakiye 3.670 -> 2.170 olarak `shared_prefs`te dogrulandi) ama ana menudeki
 * nisan cipi **CIKMADI**. Cip ancak uygulama tamamen kapatilip yeniden
 * acilinca goruldu.
 *
 * KOK NEDEN: [MainMenuOverlay] rank'i `gameEngine.saveManager.prestigeRank`
 * ile okuyordu — duz bir `SharedPreferences` okumasi. Compose snapshot
 * sistemi bu degeri GOZLEMEZ, dolayisiyla degistiginde recomposition
 * tetiklenmez. Reaktif kaynak `CampaignProgressImpl.prestigeRank`
 * (`mutableStateOf` ile destekli) idi ve ekrana HIC gecirilmiyordu.
 *
 * NEDEN BU HATA AGIRDIR: Prestij Nisani **tamamen kozmetiktir** —
 * `EconomyConfig` bunu acikca yazar. 1.500 coin'in satin aldigi TEK sey bu
 * cipin gorunmesidir. Gorunmedigi surece oyuncu parasini hicbir seye vermis
 * olur. Oynanis etkisi olan bir kalemde gecikmis bir UI guncellemesi
 * rahatsizlik verir; kozmetik bir kalemde URUNUN KENDISINI yok eder.
 *
 * NEDEN 1111 TESTIN HICBIRI YAKALAMADI: `EliteAndPrestigeTest` prestij
 * MATEMATIGINI ve KALICILIGINI eksiksiz kilitliyor (fiyat, rezerv kurali,
 * tavan, `resetProgress`). Ama hicbiri cipin CIZILDIGINI kontrol etmiyordu —
 * `prestige_chip` testTag'i depoda hicbir testte gecmiyordu. Bu, deponun
 * 4 numarali tekrar eden hata sinifinin ("kod var, cagiran yok") UI
 * tarafindaki kardesi: veri dogru, ekran onu okumuyor.
 *
 * BU TEST NEYI KILITLIYOR: cip, kompozisyon YENIDEN KURULMADAN, yalnizca
 * disaridan gelen rank degeri degistigi icin ortaya cikmali. Eski koda geri
 * donulurse (rank'i `SaveManager`dan okumak) burasi patlar, cunku
 * `SaveManager` degeri test boyunca 0'da birakiliyor.
 *
 * Ekran geometrisi gercek oynanis geometrisidir (yatay, 740x360 dp).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land-xxhdpi")
class PrestigeChipReactivityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var audio: AudioManager
    private lateinit var saveManager: SaveManager
    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        audio = AudioManager(ctx)
        audio.isSoundEnabled = false
        saveManager = SaveManager(ctx)
        // ⚠ KASITLI: kalici depo 0'da BIRAKILIYOR. Cip yalnizca disaridan
        // gecirilen reaktif deger yuzunden cikmali; `SaveManager`dan okuyan
        // bir surum bu testi GECEMEZ.
        saveManager.resetProgress()
        engine = GameEngine(saveManager, audio)
        engine.updateMapDimensions(2220f, 1080f, 96f)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    @Test
    fun prestigeChipAppearsTheMomentTheRankChanges_withoutRecreatingTheComposition() {
        var rank by mutableIntStateOf(0)

        composeRule.setContent {
            MainMenuOverlay(
                gameEngine = engine,
                onStartGame = {},
                prestigeRank = rank
            )
        }

        // Rank 0: uydurma unvan gosterilmez.
        composeRule.onNodeWithTag("prestige_chip").assertDoesNotExist()

        // Satin alma. Kompozisyon YENIDEN KURULMUYOR — yalnizca durum degisiyor,
        // cihazdaki gercek akisla ayni (oyuncu Cephanelik'ten geri donuyor).
        rank = 1
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("prestige_chip").assertIsDisplayed()

        // Kalici depo hala 0 — yani cip GERCEKTEN reaktif degerden ciziliyor,
        // yanlislikla `SaveManager`dan degil.
        assert(saveManager.prestigeRank == 0) {
            "test kurulumu bozuk: kalici rank 0 kalmaliydi, ${saveManager.prestigeRank} bulundu"
        }
    }

    /**
     * Parametre gecilmediginde eski davranis korunur (geri uyum). Bu dal
     * URETIMDE kullanilmiyor — `GameScreen` her zaman deger geciriyor — ama
     * varsayilanin sessizce "cip hic cizilmez"e donmedigini kanitlar.
     */
    @Test
    fun withoutTheReactiveParameterTheChipStillFallsBackToPersistedRank() {
        saveManager.prestigeRank = 3

        composeRule.setContent {
            MainMenuOverlay(gameEngine = engine, onStartGame = {})
        }

        composeRule.onNodeWithTag("prestige_chip").assertIsDisplayed()
    }
}
