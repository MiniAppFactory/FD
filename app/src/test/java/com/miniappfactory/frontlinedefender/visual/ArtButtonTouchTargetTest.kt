package com.miniappfactory.frontlinedefender.visual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.miniappfactory.frontlinedefender.game.ui.ArtPrimaryButton
import com.miniappfactory.frontlinedefender.game.ui.ResultModalInnerWidth
import com.miniappfactory.frontlinedefender.game.ui.ArtSecondaryButton
import com.miniappfactory.frontlinedefender.ui.theme.ArtTextPrimary
import com.miniappfactory.frontlinedefender.ui.theme.ArtTextSecondary
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * =============================================================================
 * SANAT BUTONLARI — DOKUNMA HEDEFI VE KONTRAST KURALLARI
 * =============================================================================
 *
 * Iki kural, ikisi de CIHAZDA olculen bir hatadan dogdu.
 */
@RunWith(RobolectricTestRunner::class)
// `qualifiers` ZORUNLU: Robolectric'in varsayilan ekrani 452 dp'den DAR ve
// `Modifier.width(452.dp)` sessizce ekrana kirpiliyor. Ilk kosuda buton 39 dp
// olcuduler — sanat yanlis oldugu icin degil, TEST TUVALI kucuk oldugu icin.
// Burada gercek hedef cihaz taklit edilir: Galaxy S8, yatay, 740x360 dp.
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land")
class ArtButtonTouchTargetTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * ZAFER/YENILGI MODALINDA BUTONLAR 44 dp'NIN ALTINA DUSMEZ.
     *
     * Sanat butonlari en-boy oranini KORUYOR, yani yukseklikleri
     * genisliklerinden turuyor ve modal darlastikca KUCULUYORLAR. Modalin
     * `widthIn(max = ...)` degeri bu yuzden estetik degil ERISILEBILIRLIK
     * parametresidir:
     *
     *   420 dp taban -> ic genislik 392 -> 12 dp bosluk -> buton 190 dp
     *                -> 190 / 4,33 = **43,9 dp**  ✘ 44 dp tabaninin ALTINDA
     *   480 dp taban -> ic genislik 452 -> 12 dp bosluk -> buton 220 dp
     *                -> 220 / 4,33 = **50,8 dp**  ✔
     *
     * Test modalin ic duzenini birebir taklit eder (452 dp'lik satir, iki esit
     * agirlikli buton, 12 dp aralik). Biri `widthIn`i 420'ye geri cekerse ya da
     * sanatin orani degisirse burasi kirilir.
     */
    @Test
    fun modalActionButtonsStayAtOrAboveTheFortyFourDpTouchFloor() {
        composeRule.setContent {
            Box(modifier = Modifier.width(MODAL_INNER_WIDTH)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ArtSecondaryButton(
                        label = "BÖLÜM SEÇ",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        labelColor = ArtTextPrimary,
                        testTag = "secondary"
                    )
                    ArtPrimaryButton(
                        label = "SONRAKİ BÖLÜM",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        testTag = "primary"
                    )
                }
            }
        }

        listOf("secondary", "primary").forEach { tag ->
            val height = composeRule.onNodeWithTag(tag)
                .fetchSemanticsNode()
                .size.height

            val heightDp = with(composeRule.density) { height.toDp() }
            assertTrue(
                "$tag butonu $heightDp — 44 dp dokunma tabaninin altinda. " +
                    "Modalin widthIn degeri dusuruldu mu, yoksa sanatin en-boy " +
                    "orani mi degisti?",
                heightDp >= 44.dp
            )
        }
    }

    /**
     * SANAT UZERINDEKI YAZI OKUNUR KALIR.
     *
     * ⚠ BU TEST BIR HATADAN DOGDU. Ilk surumde etiketler KOYU cizildi
     * (`ArtOnBright = #10160C`) cunku sanata bakinca butonlar "parlak
     * sari-yesil" gorunuyordu. Galaxy S8'de olculen gercek: parlak olan
     * yalnizca KENAR ISILTISI; yazinin oturdugu ic alan koyu zeytin.
     *
     *   birincil buton ic alani  RGB(44, 62, 7) -> koyu yaziyla **1,58:1**
     *   secili segment ic alani  RGB(70, 85, 7) -> koyu yaziyla **2,31:1**
     *
     * Asagidaki zemin renkleri o OLCUMDEN gelir, sanata bakilarak tahmin
     * edilmemistir. Biri etiket rengini yeniden koyulastirirsa test kirilir.
     */
    @Test
    fun artLabelColorsClearTheContrastFloorOnTheMeasuredBackgrounds() {
        // Her satir GERCEK bir cagri yeridir; capraz carpim YAPILMAZ.
        // Ornegin `ArtTextSecondary` hicbir zaman SECILI segmentin uzerine
        // cizilmez, o yuzden o cift burada da yoktur — olmayan bir kullanimi
        // test etmek, gercek bir hatayi bulmadan kirmizi satir uretir.
        //
        // Zemin renkleri Galaxy S8 ekran goruntusunden ORNEKLENDI
        // (docs/device_evidence/ui_art_pack_v2/), sanata bakilarak tahmin
        // edilmedi.
        data class Pairing(val where: String, val label: Color, val background: Color)

        val pairings = listOf(
            Pairing("birincil buton etiketi", ArtTextPrimary, Color(0xFF2C3E07)),
            Pairing("secili segment etiketi", ArtTextPrimary, Color(0xFF465507)),
            Pairing("sonuk segment etiketi", ArtTextSecondary, Color(0xFF1F2211)),
            Pairing("baslik plakasi basligi", ArtTextPrimary, Color(0xFF181A0F)),
            Pairing("baslik plakasi alt basligi", ArtTextSecondary, Color(0xFF181A0F)),
            Pairing("ikincil buton etiketi", ArtTextPrimary, Color(0xFF232A16))
        )

        pairings.forEach { (where, label, background) ->
            val ratio = contrastRatio(label, background)
            assertTrue(
                "%s: %.2f:1 — WCAG AA tabani 4,5:1. ".format(where, ratio) +
                    "Sanat uzerindeki yazi rengi koyulastirildiysa geri alin; " +
                    "olculen zemin degistiyse once cihaz olcumu yenilenmeli.",
                ratio >= 4.5
            )
        }
    }

    // -------------------------------------------------------------------------

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** WCAG 2.1 bagil luminans. */
    private fun relativeLuminance(c: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private companion object {
        /**
         * Modalin ic genisligi GERCEK KODDAN gelir
         * (`GameDialogs.ResultModalInnerWidth`), burada YENIDEN HESAPLANMAZ.
         * Kopyalansaydi biri `ResultModalMaxWidth`i 420'ye cekince bu test
         * yesil kalir ve 44 dp ihlali sessizce gecerdi.
         */
        val MODAL_INNER_WIDTH = ResultModalInnerWidth
    }
}
