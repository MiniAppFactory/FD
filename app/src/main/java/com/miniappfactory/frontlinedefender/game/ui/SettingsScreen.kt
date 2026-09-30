package com.miniappfactory.frontlinedefender.game.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.BuildConfig
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.settings.AppLanguage
import com.miniappfactory.frontlinedefender.game.settings.LocalAppLanguage
import com.miniappfactory.frontlinedefender.ui.theme.ArtTextPrimary
import com.miniappfactory.frontlinedefender.ui.theme.SleekBorderLight
import com.miniappfactory.frontlinedefender.ui.theme.SleekDarkBg
import com.miniappfactory.frontlinedefender.ui.theme.SleekPrimaryGreen
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceCard
import com.miniappfactory.frontlinedefender.ui.theme.SleekTextAccent

/**
 * GERCEK AYARLAR EKRANI.
 *
 * ## Neden var
 * Ana menudeki disli ikonu eskiden **yalnizca bir ses ac/kapa dugmesiydi** ve arkasinda
 * bir ekran yoktu. Ekran ses, muzik, dokunsal geri bildirim ve sifirlama eylemlerini
 * toplar. Oyunda reklam ve riza formu YOKTUR (2026-09-30); onlarin eski "Gizlilik
 * Secenekleri" girisi kaldirildi.
 *
 * ## Bilincli olarak GENISLETILEBILIR
 * Ayni kod tabaninda su an muzik ve haptik yazan baska bir is kolu var; onlarin
 * API'si HENUZ YOK. Bu yuzden burada satir "uydurulmadi". Bunun yerine ekran,
 * yeni satirin **tek cagri** ile eklenebilecegi sekilde kuruldu — bkz.
 * [SettingsToggleRow] ve [SettingsSectionHeader]. Eklenecek yer "SES" bolumu
 * icinde, ses satirinin hemen altidir (asagida isaretli).
 *
 * Gizlilik politikasi baglantisi da ayni sebeple **yok**: metin
 * `docs/PRIVACY_POLICY.md` icinde yazildi ama host EDILMEDI, yani gercek bir URL
 * yok. Placeholder bir adres koymak calismayan bir buton koymaktir; ustelik
 * magaza incelemesinde "politika yayinda" izlenimi verir. Satir, URL gercekten
 * olustugunda "GIZLILIK" bolumune eklenir.
 *
 * ## Neden GameEngine almiyor
 * Ekranin motora ihtiyaci yok; ses durumu **deger + geri cagirma** olarak
 * disaridan gelir. Boylece ekran `GameEngine`/`Context` kurmadan test edilebilir
 * ve ayni ekran birden fazla yerden (ana menu ve duraklatma menusu) ayni
 * sozlesmeyle acilabilir — nitekim oyle aciliyor.
 *
 * @param soundEnabled ses efektlerinin su anki durumu.
 * @param onSoundEnabledChange kalici kayda VE calisan ses motoruna yazmak
 *   cagiranin isidir (bkz. `GameDialogs.kt`); ekran yalnizca niyeti bildirir.
 * @param onDismiss ekrandan cikis. Geri tusu de bunu cagirir.
 */
@Composable
fun SettingsScreen(
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Faz 14 — muzik ve dokunsal geri bildirim anahtarlari.
     *
     * VARSAYILANLI: bu ekranin mevcut testleri (SettingsScreenComposeTest,
     * gizlilik kapisi testleri) iki yeni argumani vermek zorunda kalmasin.
     * Varsayilan "acik + hicbir sey yapma" oldugu icin bagli olmayan bir cagri
     * yeri ekrani BOZMAZ, yalnizca anahtar etkisiz kalir.
     */
    musicEnabled: Boolean = true,
    onMusicEnabledChange: (Boolean) -> Unit = {},
    hapticsEnabled: Boolean = true,
    onHapticsEnabledChange: (Boolean) -> Unit = {},
    /**
     * Faz 14 — SIFIRLAMA EYLEMLERI.
     *
     * NEDEN LAMBDA, NEDEN CompositionLocal DEGIL: bu ekran bilincli olarak
     * `SaveManager` ya da `Context` ALMIYOR ve almasi da gerekmiyor — burada
     * verilen karar "oyuncu sifirlamak istedi", islemi kim yapacagi degil.
     * Lambda bunu imzada GORUNUR kilar; CompositionLocal ise YIKICI bir
     * yetenegi (tum ilerlemeyi silmek) her composable'in kapsamina gorunmez
     * bicimde sokardi ve testte saglayici kurmayi zorunlu tutardi. Ses/muzik
     * satirlari da zaten ayni kalibi kullaniyor.
     *
     * Varsayilan no-op: baglanmamis bir cagri yeri ekrani BOZMAZ.
     */
    onResetHints: () -> Unit = {},
    onResetTutorial: () -> Unit = {},
    onResetProgress: () -> Unit = {},
    modifier: Modifier = Modifier,
    versionName: String = BuildConfig.VERSION_NAME,
    versionCode: Int = BuildConfig.VERSION_CODE
) {
    BackHandler(onBack = onDismiss)

    // "Ilerlemeyi sifirla" IKI ADIMLI. Diyalog yerine yerinde onay:
    // bu ekran zaten kaydirilabilir bir overlay ve ustune ikinci bir Dialog
    // acmak yatay ekranda (kullanilabilir yukseklik 320-400 dp) onay
    // butonlarini ekran disina itebiliyor.
    var confirmingReset by remember { mutableStateOf(false) }

    // Islem sonrasi geri bildirim. Sifirlama SESSIZ olursa oyuncu dokunup
    // hicbir sey olmadigini sanir ve tekrar dokunur.
    var resetDone by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .testTag("settings_screen"),
        contentAlignment = Alignment.Center
    ) {
        // ---------------------------------------------------------------
        // PANEL OLCUSU — UI ART PACK v2
        //
        // Panel sanati EN-BOY ORANINI korur (1,44). Yani genisligi secmek
        // yuksekligi de secmektir; ters yon (yukseklik sec) yasak, cunku dar
        // olan taraf DIKEY (yatay telefonda 360 dp).
        //
        // Uc sinirin en kucugu alinir:
        //   · 480 dp   — ustunde panel gereksiz devlesir ve satirlar
        //                okunamayacak kadar genis olur,
        //   · ekran genisliginin %72'si,
        //   · (kullanilabilir yukseklik − 16 dp) × 1,44 — DIKEY sinir.
        //
        // 740x360 dp'lik Galaxy S8'de: min(480, 532,8, 495,4) = 480 dp,
        // yani yukseklik 480/1,44 = **333,3 dp** ≤ 344 ✔.
        // ---------------------------------------------------------------
        val panelWidth = minOf(
            480.dp,
            maxWidth * 0.72f,
            (maxHeight - 16.dp) * Art.ModalPanel.aspect
        )

        ArtModalPanel(
            title = stringResource(R.string.settings_title),
            modifier = Modifier.width(panelWidth),
            titleColor = ArtTextPrimary,
            trailing = {
                // KAPAT panelin UST SERIDINDE — kaydirma alaninin DISINDA.
                // Bu, sanattan ONCE de boyleydi ve gerekcesi degismedi: yatayda
                // kullanilabilir yukseklik 320-400 dp'ye duser ve kaydirilabilir
                // bir baslikta cikis butonu ekran disina cikabilir.
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryGreen),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("settings_close_button")
                ) {
                    AutoShrinkText(
                        text = stringResource(R.string.settings_close),
                        color = Color.Unspecified,
                        fontWeight = FontWeight.Bold,
                        maxFontSize = 13.sp,
                        minFontSize = 10.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        ) {
            // Icerik kaydirilir: yeni satirlar (muzik, haptik, gizlilik
            // politikasi) eklendikce yatay ekranda tasma olusmaz.
            // Panelin ic alani 333 dp x 0,756 = ~252 dp; gereken icerik ~290 dp.
            // Yani kaydirma ZORUNLU ve kasitli.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ---------------------------------------------------------
                // SES
                // ---------------------------------------------------------
                SettingsSectionHeader(stringResource(R.string.settings_section_audio))

                SettingsToggleRow(
                    label = stringResource(R.string.dialog_sound_effects),
                    checked = soundEnabled,
                    onCheckedChange = onSoundEnabledChange,
                    testTag = "settings_sound_switch"
                )

                // Faz 14 - MUZIK. Ses Efektleri anahtarinin ALTINDA ve ayni
                // bolumde: ikisi de "ne duyacagim" sorusunun parcasi.
                //
                // Not: Ses Efektleri anahtari ANA anahtardir; kapatilinca
                // muzik de susar (bkz. AudioManager.isSoundEnabled). Muzik
                // anahtari yalnizca ses acikken anlamlidir ve tercihi ayri
                // saklanir, yani ses tekrar acilinca muzik oyuncunun
                // biraktigi durumda geri gelir.
                SettingsToggleRow(
                    label = stringResource(R.string.settings_music),
                    checked = musicEnabled,
                    onCheckedChange = onMusicEnabledChange,
                    testTag = "settings_music_switch"
                )

                // Faz 14 - DOKUNSAL GERI BILDIRIM. Ses bolumunde duruyor
                // cunku oyuncu icin ayni soru: "cihaz bana geri bildirim
                // versin mi". Ayri bir baslik acmak uc satirlik bir bolum
                // uretirdi ve yatay ekranda kaydirmayi uzatirdi.
                SettingsToggleRow(
                    label = stringResource(R.string.settings_haptics),
                    checked = hapticsEnabled,
                    onCheckedChange = onHapticsEnabledChange,
                    testTag = "settings_haptics_switch"
                )

                // ---------------------------------------------------------
                // DIL
                //
                // Mockup'taki sirayi izler: SES -> DIL -> (GIZLILIK) -> SIFIRLAMA.
                // Secici iki durumlu bir SANAT varligi; ucuncu bir "sistem"
                // segmenti cizemez. `AppLanguage.SYSTEM` yalnizca oyuncunun
                // HIC dokunmadigi baslangic halidir ve o durumda hangi
                // segmentin parlayacagi CIHAZIN dilinden okunur — yani secici
                // bosta durmaz, oyuncunun o an gordugu dili gosterir.
                // ---------------------------------------------------------
                SettingsSectionHeader(stringResource(R.string.settings_section_language))

                val languageController = LocalAppLanguage.current
                val languageLabels = listOf(
                    stringResource(R.string.settings_language_english),
                    stringResource(R.string.settings_language_turkish)
                )
                val effectiveLanguage = when (languageController.current) {
                    AppLanguage.SYSTEM ->
                        // Sistem dili TR ise TURKISH parlar. `Locale` yerine
                        // gercekten cizilen kaynagin dili okunur: cihaz
                        // "tr-TR" ama uygulamada `values-tr` yoksa oyuncu
                        // Ingilizce goruyordur ve secici de oyle demelidir.
                        if (stringResource(R.string.settings_locale_tag) == "tr") {
                            AppLanguage.TURKISH
                        } else {
                            AppLanguage.ENGLISH
                        }
                    else -> languageController.current
                }

                ArtSegmentedSelector(
                    selectedIndex = AppLanguage.SELECTABLE.indexOf(effectiveLanguage)
                        .coerceAtLeast(0),
                    labels = languageLabels,
                    onSelect = { index ->
                        languageController.onChange(AppLanguage.SELECTABLE[index])
                    },
                    // GENISLIK KASITLI OLARAK SINIRLI. Secici sanatinin orani
                    // 5,03; panelin tam genisligini (~430 dp) verseydik
                    // yukseklik 85 dp olurdu ve 252 dp'lik govdenin ucte birini
                    // tek satir yerdi. %55'te 236 dp -> 47 dp, yani 44 dp
                    // dokunma tabaninin ustunde ve satir yuksekliginde.
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 4.dp),
                    testTagPrefix = "settings_language"
                )

                Text(
                    text = stringResource(R.string.settings_language_desc),
                    color = SleekTextAccent.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                // >>> GIZLILIK POLITIKASI BAGLANTISI BURAYA GELECEK. <<<
                // Metin docs/PRIVACY_POLICY.md icinde HAZIR ama HOST EDILMEDI.
                // Gercek URL olusmadan satir eklenmez.

                // ---------------------------------------------------------
                // SIFIRLAMA
                //
                // `SaveManager.resetHints/resetTutorial/resetProgress`
                // yazilmisti ama `main/` icinde HIC cagri yeri yoktu:
                // oyuncu ogreticiyi tekrar goremiyordu, tek yol adb idi.
                // ---------------------------------------------------------
                SettingsSectionHeader(stringResource(R.string.settings_section_reset))

                val doneLabel = stringResource(R.string.settings_reset_done)

                SettingsActionRow(
                    label = stringResource(R.string.settings_reset_hints),
                    description = stringResource(R.string.settings_reset_hints_desc),
                    onClick = {
                        onResetHints()
                        confirmingReset = false
                        resetDone = doneLabel
                    },
                    testTag = "settings_reset_hints_row"
                )

                SettingsActionRow(
                    label = stringResource(R.string.settings_reset_tutorial),
                    description = stringResource(R.string.settings_reset_tutorial_desc),
                    onClick = {
                        onResetTutorial()
                        confirmingReset = false
                        resetDone = doneLabel
                    },
                    testTag = "settings_reset_tutorial_row"
                )

                // YIKICI. Ilk dokunus yalnizca onay ister, ikinci dokunus
                // uygular. Etiket de degisir: onay satiri, aciklamanin
                // degil KENDISININ okundugu yerdir.
                SettingsActionRow(
                    label = if (confirmingReset) {
                        stringResource(R.string.settings_reset_progress_confirm)
                    } else {
                        stringResource(R.string.settings_reset_progress)
                    },
                    description = stringResource(R.string.settings_reset_progress_desc),
                    onClick = {
                        if (confirmingReset) {
                            confirmingReset = false
                            onResetProgress()
                            resetDone = doneLabel
                        } else {
                            confirmingReset = true
                            resetDone = null
                        }
                    },
                    testTag = "settings_reset_progress_row"
                )

                if (confirmingReset) {
                    // Vazgecme yolu ACIK olmali: yikici bir onayda tek
                    // cikis "baska bir yere dokun" olamaz.
                    SettingsActionRow(
                        label = stringResource(R.string.settings_reset_progress_cancel),
                        description = null,
                        onClick = { confirmingReset = false },
                        testTag = "settings_reset_cancel_row"
                    )
                }

                val doneMessage = resetDone
                if (doneMessage != null) {
                    Text(
                        text = doneMessage,
                        color = SleekPrimaryGreen,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("settings_reset_done")
                    )
                }

                // ---------------------------------------------------------
                // HAKKINDA
                // ---------------------------------------------------------
                SettingsSectionHeader(stringResource(R.string.settings_section_about))

                Text(
                    text = stringResource(R.string.settings_version, versionName, versionCode),
                    color = SleekTextAccent.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("settings_version")
                )
            }
        }
    }
}

/**
 * Bolum basligi. Yeni bir ayar grubu (ornegin "OYNANIS") tek satirla acilir.
 */
@Composable
fun SettingsSectionHeader(label: String) {
    Column {
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = SleekTextAccent.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        HorizontalDivider(color = SleekBorderLight)
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Ac/kapa satiri.
 *
 * Tum satir dokunulabilir, yalnizca Switch degil: yatay telefonda tek elle
 * anahtara uzanmak zor. Satirin tamami hedef olunca dokunma alani kartin
 * genisligi kadar olur.
 *
 * Etiket [AutoShrinkText] ile cizilir cunku Turkce karsiliklar Ingilizceden
 * belirgin uzun ve satirin sag ucunu Switch tutuyor.
 */
@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SleekSurfaceCard,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            // `toggleable` (clickable DEGIL): satirin tamami tek bir ac/kapa
            // ogesi olarak bildirilir. Ekran okuyucu "acik/kapali" durumunu
            // dogrudan okur, ve dokunma hedefi anahtarin 48 dp'lik alani degil
            // satirin tamamidir — yatay telefonda tek elle kullanim icin fark
            // ediyor.
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    role = Role.Switch
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AutoShrinkText(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxFontSize = 15.sp,
                minFontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            // Anahtar burada GORSELDIR, dokunmayi satir isler. Aksi halde ayni
            // satirda iki ayri ac/kapa ogesi bildirilir ve erisilebilirlik
            // agaci ikiye bolunur — bu, Material `Switch(onCheckedChange = null)`
            // cagrisinin de yaptigi seydi, sanata gecerken korundu.
            //
            // DURUM IKI KANALDAN OKUNUR: kapali sanatta topuz SOLDA (yatay
            // ayna) ve ayrica doygunluk dusuk. Renk tek ayrim kanali degil.
            ArtToggleVisual(checked = checked)
        }
    }
}

/**
 * Dokununca bir sey ACAN satir (su an: sifirlama eylemleri).
 *
 * Aciklama satiri sarabilir — `maxLines` YOK. Kart genisligi sabit, yukseklik
 * esner; boylece uzun ceviri kirpilmaz.
 */
@Composable
fun SettingsActionRow(
    label: String,
    description: String?,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SleekSurfaceCard,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag(testTag)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    color = SleekTextAccent.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
