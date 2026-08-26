package com.miniappfactory.frontlinedefender.game.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.ui.theme.ArtAccentGoldGlow
import com.miniappfactory.frontlinedefender.ui.theme.ArtTextPrimary
import com.miniappfactory.frontlinedefender.ui.theme.ArtTextSecondary

/**
 * =============================================================================
 * UI ART PACK v2 — CIZIM YUZEYLERI
 * =============================================================================
 *
 * Asset pack'teki METINSIZ plaka/buton/panel sanatini Compose'a baglayan ince
 * katman. Kaynak: `asset-pack/Frontline_Defender_assets_individual_full/`.
 *
 * ## Uc kural (pack'in kendi `docs/implementation_guide.md` dosyasindan)
 *
 * 1. **Metin sanata GOMULMEZ.** Pack ayrica 142 adet metin-gomulu PNG
 *    iceriyordu (`level_titles/` 110, `map_name_labels/` 22, `act_titles/` 10 —
 *    her biri ~1,8 MB, ham toplam ~250 MB). Hicbiri kullanilmadi: yazi
 *    `stringResource` ile gelir, sanatin USTUNE cizilir. Aksi halde her yeni
 *    dil 142 yeni dosya demek olurdu, `AutoShrinkText` devre disi kalirdi ve
 *    ayni bolum adi biri kodda biri pikselde olmak uzere IKI yerde yasardi
 *    (bu depoda en sik hata sinifi).
 * 2. **Durum kodda tutulur**, sanatta degil. Ac/kapa, secili segment,
 *    etkin/devre disi — hepsi Kotlin tarafinda; sanat yalnizca kabuk.
 * 3. **Sanat EN-BOY ORANINI korur.** Her yuzey `aspectRatio` ile olculur;
 *    hicbir plaka gerilmez. Boyut yalnizca GENISLIKTEN gelir, yukseklik
 *    orandan cikar — boylece 360 dp'lik yatay ekranda "bu ekran sigiyor mu"
 *    sorusu cagri yerinde ARITMETIKLE cevaplanabilir.
 *
 * ## ⛔ `UnlockConfirmOverlay`'e SANAT KONULMADI
 *
 * `level_nameplate_template` bu pakette geldi ve bolum kilidi onay
 * penceresine konmasi planlanmisti. DORT ayri bicimde denendi, DORDUNDE de
 * cihazda (Galaxy S8 / API 24) pencerenin iki `Surface` butonu METINSIZ
 * kaldi ve kap rengi uygulanmadi — yani "KILIDI AC" onayi gorunmez oldu.
 * Sanat kaldirilinca dordunde de aninda duzeldi. Ayrinti ve ekran goruntusu
 * referanslari `LevelSelectScreen.kt` icindeki yorum blogunda; bu yuzden
 * `ArtNameplate` ve `Art.Nameplate` bu dosyadan SILINDI (olu kod
 * birakilmadi) ve `ui_plate_nameplate.webp` APK'ya girmiyor.
 *
 * ## Ic alan olcumleri nereden geliyor
 *
 * [ArtInset] degerleri elle tahmin EDILMEDI. Her plakanin merkezinden disa
 * dogru tarandi ve metalik cerceveye (opak, luma > 95) carpilan yerde
 * durduruldu; olculen kutu sanatin cukurlastirilmis ic alanidir. Sanat
 * degisirse olcum yeniden kosturulur, sabitler elle duzeltilmez.
 */
@Immutable
data class ArtInset(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * Sanat varliginin OLCULMUS kunyesi.
 *
 * @param res drawable kimligi
 * @param aspect genislik / yukseklik — dosyanin gercek pikselinden
 * @param inset ic alan, kenar kesirleri olarak
 */
@Immutable
data class ArtSpec(
    @DrawableRes val res: Int,
    val aspect: Float,
    val inset: ArtInset
)

/**
 * OLCULMUS SABITLER — uretici `tools/ui_art_pipeline.py`, ic alan taramasi
 * ayni kaynaktan.
 *
 * Bu tablo TEK KAYNAKTIR; ayni sayilar baska bir dosyada tekrarlanmaz.
 */
object Art {
    /** Ana menu / bolum secim basligi. 900x257 px. */
    val HeaderPlate = ArtSpec(
        R.drawable.ui_plate_header, 3.50f,
        ArtInset(0.060f, 0.233f, 0.060f, 0.156f)
    )

    /** Perde acilis afisi — USTTE ayri serit, ALTTA genis alan. 900x286 px. */
    val ActBanner = ArtSpec(
        R.drawable.ui_plate_act_banner, 3.15f,
        ArtInset(0.088f, 0.378f, 0.089f, 0.164f)
    )

    /** Birincil eylem butonu — SOLUNDA oynat ucgeni GOMULU. 880x203 px. */
    val PrimaryButton = ArtSpec(
        R.drawable.ui_btn_primary, 4.33f,
        ArtInset(0.228f, 0.266f, 0.086f, 0.261f)
    )

    /** Ikincil buton — SOLUNDA sekizgen ikon yuvasi. 620x156 px. */
    val SecondaryButton = ArtSpec(
        R.drawable.ui_btn_secondary, 3.97f,
        ArtInset(0.211f, 0.250f, 0.044f, 0.173f)
    )

    /**
     * [SecondaryButton] sanatinin SOL UCUNDAKI sekizgen ikon yuvasi.
     *
     * ⚠ ILK SURUMDE GOZ KARARI KONMUSTU (`padding(start = w * 0.055f)`) ve
     * cihazda ikonlar yuvanin SOL-ALTINA kayip kenarindan tasti. Kutu artik
     * olculdu: sanatin sol ucu 3x buyutulup sekizgenin IC kenari okundu.
     *
     * Kesirler butonun TAMAMINA goredir; kutu `ArtInset` semantigiyle
     * (sol, ust, sag, alt kenar payi) yazilir.
     */
    val SecondaryIconSlot = ArtInset(0.070f, 0.203f, 0.785f, 0.242f)

    /**
     * Modal panel govdesi — USTTE baslik seridi. 1040x720 px.
     *
     * ⚠ ALT SINIR CIHAZDA DUZELTILDI (Galaxy S8, 2026-08-26). Merkezden disa
     * tarama alt kenarda 0,050 vermisti ve ayarlar icerigi panelin ALT
     * CERCEVESININ USTUNE tasiyordu (`docs/device_evidence/` 06_settings).
     * Sebep: bu panelin alt cercevesi KOYU (luma ~20-85), yani "parlak
     * cerceveye carpinca dur" kurali orada tetiklenmedi ve tarama cerceveden
     * gecip gitti.
     *
     * Yeniden olcum merkez sutunun luma profiliyle yapildi: ic cukur y=672'de
     * bitiyor (720 pikselde), yani gercek alt sinir 0,067. Uzerine bevel payi
     * eklenerek **0,085** kullanildi.
     */
    val ModalPanel = ArtSpec(
        R.drawable.ui_panel_modal, 1.44f,
        ArtInset(0.031f, 0.194f, 0.032f, 0.085f)
    )

    /**
     * Iki segmentli secici. 840x167 px.
     *
     * Ic alan TARAMASI bu varlikta ise yaramaz: merkezde iki segmenti ayiran
     * parlak bolme var, tarama daha bir piksel gitmeden durur. Segment
     * kutulari bu yuzden simetriden turetildi.
     */
    val SegmentedSelector = ArtSpec(
        R.drawable.ui_selector_dual, 5.03f,
        ArtInset(0.030f, 0.180f, 0.030f, 0.180f)
    )

    /** Ayarlar dislisi. 200x192 px — neredeyse kare. */
    val GearIcon = ArtSpec(R.drawable.ui_ic_gear, 1.04f, ArtInset(0f, 0f, 0f, 0f))

    /**
     * Ac/kapa anahtarinin en-boy orani (200x88 px).
     *
     * KAPALI durum AYRI SANAT DOSYASI DEGIL, acik durumdan turetildi (yatay
     * ayna + doygunluk 0,16 + parlaklik 0,55). Uretici
     * `tools/ui_art_pipeline.py` — elle duzenlenmis degil, yeniden uretilebilir.
     */
    const val TOGGLE_ASPECT = 2.27f
}

// -----------------------------------------------------------------------------
// Yardimcilar
// -----------------------------------------------------------------------------

/** Etiket null ise `testTag` HIC eklenmez (bos etiketli dugum uretmemek icin). */
private fun Modifier.optionalTestTag(tag: String?): Modifier =
    if (tag == null) this else this.testTag(tag)

/**
 * Yatay ayna. Sanatta METIN OLMADIGI icin guvenli — aynalanan tek sey
 * cerceve/isilti geometrisi.
 */
private fun Modifier.mirrorHorizontally(): Modifier = this.scale(scaleX = -1f, scaleY = 1f)

/**
 * Bir sanat plakasini cizer ve OLCULMUS ic alanina icerik yerlestirir.
 *
 * Yukseklik [ArtSpec.aspect] ile genislikten TURETILIR; cagiran yalnizca
 * genisligi verir.
 *
 * @param onClick null ise yuzey tiklanabilir DEGILDIR (saf dekor).
 * @param enabled false iken sanat soluklasir ve tiklama gecmez.
 */
@Composable
fun ArtSurface(
    spec: ArtSpec,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(spec.aspect)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = contentDescription,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
    ) {
        val w = maxWidth
        val h = maxHeight

        Image(
            painter = painterResource(spec.res),
            // Etiket METIN katmanindan gelir; sanat sussuz kalir ki ekran
            // okuyucu ayni seyi iki kez soylemesin.
            contentDescription = null,
            // FillBounds: kutu zaten `aspectRatio` ile sanatin kendi oranina
            // sabitlendigi icin GERILME OLUSMAZ. Fit ise yuvarlama farkindan
            // bir piksellik seffaf kenar birakabiliyor.
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .matchParentSize()
                .alpha(if (enabled) 1f else 0.45f)
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(
                    start = w * spec.inset.left,
                    top = h * spec.inset.top,
                    end = w * spec.inset.right,
                    bottom = h * spec.inset.bottom
                ),
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

/**
 * Birincil eylem butonu.
 *
 * Sanatta oynat ucgeni ZATEN GOMULU; ayrica ikon eklenmez. Etiket koyu cizilir
 * cunku butonun ic alani parlak sari-yesildir.
 */
@Composable
fun ArtPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // OLCULDU, TAHMIN EDILMEDI: butonun ic alani RGB(44,62,7); acik yazi
    // 9,1:1, koyu yazi 1,58:1 veriyor (bkz. Color.kt'deki not).
    labelColor: Color = ArtTextPrimary,
    testTag: String? = null
) {
    ArtSurface(
        spec = Art.PrimaryButton,
        modifier = modifier.optionalTestTag(testTag),
        onClick = onClick,
        enabled = enabled,
        contentDescription = label
    ) {
        AutoShrinkText(
            text = label,
            color = labelColor,
            maxFontSize = PrimaryLabelSize,
            minFontSize = PrimaryLabelMinSize,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Ikincil buton: solda sekizgen ikon yuvasi, saginda etiket.
 *
 * [icon] null birakilirsa yuva bos kalir ve buton yarim cizilmis gorunur;
 * cagri yerlerinin ikon vermesi beklenir.
 */
@Composable
fun ArtSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    labelColor: Color = ArtTextPrimary,
    testTag: String? = null
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(Art.SecondaryButton.aspect)) {
        val w = maxWidth

        ArtSurface(
            spec = Art.SecondaryButton,
            modifier = Modifier
                .matchParentSize()
                .optionalTestTag(testTag),
            onClick = onClick,
            enabled = enabled,
            contentDescription = label
        ) {
            AutoShrinkText(
                text = label,
                color = labelColor,
                maxFontSize = SecondaryLabelSize,
                minFontSize = SecondaryLabelMinSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (icon != null) {
            val h = maxHeight
            // Ikon, OLCULEN yuvanin icine ORTALANIR. Once yuva kadar bir kutu
            // konumlandirilir, sonra ikon o kutunun %72'sini kaplar — boylece
            // ikonun kendi en-boy orani ne olursa olsun sekizgenin disina
            // tasamaz ve merkezden kaymaz.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(
                        start = w * Art.SecondaryIconSlot.left,
                        top = h * Art.SecondaryIconSlot.top,
                        end = w * Art.SecondaryIconSlot.right,
                        bottom = h * Art.SecondaryIconSlot.bottom
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize(0.72f)
                        .alpha(if (enabled) 0.95f else 0.45f)
                )
            }
        }
    }
}

/**
 * Baslik plakasi: ortada buyuk ad, altinda ince alt baslik (istege bagli).
 */
@Composable
fun ArtHeaderPlate(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = ArtTextPrimary,
    subtitleColor: Color = ArtTextSecondary,
    titleSize: TextUnit = HeaderTitleSize,
    testTag: String? = null
) {
    ArtSurface(
        spec = Art.HeaderPlate,
        modifier = modifier.optionalTestTag(testTag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            AutoShrinkText(
                text = title,
                color = titleColor,
                maxFontSize = titleSize,
                minFontSize = titleSize * 0.5f,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (subtitle != null) {
                AutoShrinkText(
                    text = subtitle,
                    color = subtitleColor,
                    maxFontSize = HeaderSubtitleSize,
                    minFontSize = HeaderSubtitleMinSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Perde acilis afisi: ust seritte kisa perde etiketi, altta perde basligi.
 *
 * Iki ayri ic alan var; [ArtSpec.inset] yalnizca alttakini tasidigi icin ust
 * serit burada ayrica konumlandirilir (kesirler afisin ust cubugundan olculdu).
 */
@Composable
fun ArtActBanner(
    actLabel: String,
    actTitle: String,
    modifier: Modifier = Modifier,
    labelColor: Color = ArtAccentGoldGlow,
    titleColor: Color = ArtTextPrimary,
    testTag: String? = null
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(Art.ActBanner.aspect)) {
        val w = maxWidth
        val h = maxHeight

        ArtSurface(
            spec = Art.ActBanner,
            modifier = Modifier
                .matchParentSize()
                .optionalTestTag(testTag)
        ) {
            AutoShrinkText(
                text = actTitle,
                color = titleColor,
                maxFontSize = ActTitleSize,
                minFontSize = ActTitleMinSize,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // UST SERIT.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(
                    start = w * 0.260f,
                    end = w * 0.260f,
                    top = h * 0.115f,
                    bottom = h * 0.680f
                ),
            contentAlignment = Alignment.Center
        ) {
            AutoShrinkText(
                text = actLabel,
                color = labelColor,
                maxFontSize = ActLabelSize,
                minFontSize = ActLabelMinSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Iki segmentli secici (ornegin dil secimi).
 *
 * Sanat SABIT: sol segment parlak, sag segment sonuk. Secili taraf sagdayken
 * sanat AYNALANIR — tek dosyayla iki durum cizilir ve "secili olan parlak"
 * kurali bozulmaz.
 *
 * @param selectedIndex 0 = sol, 1 = sag.
 * @param labels tam olarak iki etiket.
 */
@Composable
fun ArtSegmentedSelector(
    selectedIndex: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // Secili taraf DAHA PARLAK yazi alir, koyu degil: segmentin ic alani
    // RGB(70,85,7) ve koyu yazi orada 2,31:1 veriyordu. Ayrim asil olarak
    // sanatin kenar isiltisindan geliyor (secili taraf aynalanarak parlar).
    selectedColor: Color = ArtTextPrimary,
    unselectedColor: Color = ArtTextSecondary,
    testTagPrefix: String? = null
) {
    require(labels.size == 2) { "ArtSegmentedSelector tam olarak iki etiket ister" }

    BoxWithConstraints(modifier = modifier.aspectRatio(Art.SegmentedSelector.aspect)) {
        val w = maxWidth
        val h = maxHeight

        Image(
            painter = painterResource(Art.SegmentedSelector.res),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .matchParentSize()
                .then(if (selectedIndex == 1) Modifier.mirrorHorizontally() else Modifier)
        )

        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(
                    start = w * Art.SegmentedSelector.inset.left,
                    end = w * Art.SegmentedSelector.inset.right,
                    top = h * Art.SegmentedSelector.inset.top,
                    bottom = h * Art.SegmentedSelector.inset.bottom
                ),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClickLabel = label,
                            onClick = { onSelect(index) }
                        )
                        .optionalTestTag(testTagPrefix?.let { "${it}_$index" }),
                    contentAlignment = Alignment.Center
                ) {
                    AutoShrinkText(
                        text = label,
                        color = if (index == selectedIndex) selectedColor else unselectedColor,
                        maxFontSize = SegmentLabelSize,
                        minFontSize = SegmentLabelMinSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Sanat ac/kapa anahtari — **YALNIZCA GORSEL**, tiklamayi ISLEMEZ.
 *
 * Neden tiklanabilir degil: `SettingsToggleRow` satirin TAMAMINI tek bir
 * `toggleable` ogesi olarak bildiriyor (dokunma hedefi yatay telefonda tek
 * elle ulasilabilsin diye satir genisligi kadar). Anahtar da kendi basina
 * tiklanabilir olsaydi ayni satirda IKI ac/kapa ogesi bildirilir ve
 * erisilebilirlik agaci ikiye bolunurdu — bu, yerine gectigi Material
 * `Switch(onCheckedChange = null)` cagrisinin de tam olarak yaptigi sey.
 *
 * DURUM IKI KANALDAN OKUNUR, yalnizca renkten DEGIL: kapali durumda topuz
 * SOLA gecer (sanat yatay aynalanmis) ve ayrica doygunlugu duser. Yani
 * "renk tek ayrim kanali olamaz" kurali saglanir.
 */
@Composable
fun ArtToggleVisual(
    checked: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = ArtToggleWidth,
    enabled: Boolean = true
) {
    Image(
        painter = painterResource(
            if (checked) R.drawable.ui_toggle_on else R.drawable.ui_toggle_off
        ),
        contentDescription = null, // durum satirin `toggleable` semantiginden
        contentScale = ContentScale.FillBounds,
        modifier = modifier
            .width(width)
            .aspectRatio(Art.TOGGLE_ASPECT)
            .alpha(if (enabled) 1f else 0.45f)
    )
}

/**
 * Modal panel kabugu: ust seritte baslik (+ istege bagli sag eylem),
 * govdede [content].
 *
 * Yukseklik oranla genislikten turedigi icin cagiran panelin yatay ekranda kac
 * dp yiyecegini hesaplayabilir: `yukseklik = genislik / 1,44`.
 */
@Composable
fun ArtModalPanel(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = ArtTextPrimary,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(Art.ModalPanel.aspect)) {
        val w = maxWidth
        val h = maxHeight

        Image(
            painter = painterResource(Art.ModalPanel.res),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize()
        )

        // UST SERIT — baslik + istege bagli sag eylem (ornegin KAPAT).
        Row(
            modifier = Modifier
                .matchParentSize()
                // Serit yuksekligi = h x 0,155. 333 dp'lik panelde 51,6 dp,
                // yani 44 dp'lik dokunma tabanini karsilayan bir KAPAT butonu
                // seride sigar. Alt sinir 0,194 olan ic alanla CAKISMAZ.
                .padding(
                    start = w * 0.070f,
                    end = w * 0.045f,
                    top = h * 0.035f,
                    bottom = h * 0.810f
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoShrinkText(
                text = title,
                color = titleColor,
                maxFontSize = ModalTitleSize,
                minFontSize = ModalTitleMinSize,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) trailing()
        }

        // GOVDE.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(
                    start = w * Art.ModalPanel.inset.left + PanelBodyPadding,
                    end = w * Art.ModalPanel.inset.right + PanelBodyPadding,
                    top = h * Art.ModalPanel.inset.top,
                    bottom = h * Art.ModalPanel.inset.bottom + PanelBodyPadding
                ),
            content = content
        )
    }
}

/**
 * Sanat yuzeylerinin arkasina cizilen KOYU PERDE.
 *
 * Modal panel sanatinin kenarlari yari saydam; altindaki oynanis alani
 * dogrudan gorunurse metin okunmaz olur.
 */
@Composable
fun ArtScrim(modifier: Modifier = Modifier, alpha: Float = 0.86f) {
    Box(modifier = modifier.background(Color.Black.copy(alpha = alpha)))
}

// -----------------------------------------------------------------------------
// Olculer — TEK YERDE. Sanat degisirse yalnizca burasi degisir.
// -----------------------------------------------------------------------------

private val PanelBodyPadding = 6.dp

/** Ac/kapa anahtarinin cizim genisligi ve en kucuk dokunma hedefi. */
val ArtToggleWidth = 60.dp
val ArtToggleMinTouch = 44.dp

private val HeaderTitleSize = 26.sp
private val HeaderSubtitleSize = 11.sp
private val HeaderSubtitleMinSize = 8.sp

private val PrimaryLabelSize = 18.sp
private val PrimaryLabelMinSize = 11.sp

private val SecondaryLabelSize = 13.sp
private val SecondaryLabelMinSize = 9.sp

private val ActLabelSize = 12.sp
private val ActLabelMinSize = 9.sp
private val ActTitleSize = 22.sp
private val ActTitleMinSize = 13.sp

private val SegmentLabelSize = 13.sp
private val SegmentLabelMinSize = 9.sp

private val ModalTitleSize = 19.sp
private val ModalTitleMinSize = 13.sp
