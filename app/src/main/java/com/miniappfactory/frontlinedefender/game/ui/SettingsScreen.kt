package com.miniappfactory.frontlinedefender.game.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Switch
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
import com.miniappfactory.frontlinedefender.game.ads.AdHost
import com.miniappfactory.frontlinedefender.game.ads.ConsentManager
import com.miniappfactory.frontlinedefender.game.ads.NoOpAdHost
import com.miniappfactory.frontlinedefender.game.ads.findActivity
import com.miniappfactory.frontlinedefender.ui.theme.SleekBorderLight
import com.miniappfactory.frontlinedefender.ui.theme.SleekDarkBg
import com.miniappfactory.frontlinedefender.ui.theme.SleekPrimaryGreen
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceCard
import com.miniappfactory.frontlinedefender.ui.theme.SleekTextAccent

/**
 * GERCEK AYARLAR EKRANI.
 *
 * ## Neden var
 * Ana menudeki disli ikonu bugune kadar **yalnizca bir ses ac/kapa dugmesiydi**
 * ve arkasinda bir ekran yoktu. Bu, iki bagimsiz denetimin buldugu iki
 * maddeden birincisinin de sebebiydi: **UMP "Gizlilik Secenekleri" girisi
 * hicbir yere bagli degildi.**
 *
 * [AdHost.showPrivacyOptions] ve [AdHost.privacyOptionsRequired] Faz 5'te dogru
 * yazilmisti ama `game/ui/` altindan **sifir cagri** vardi. Yani oyuncunun
 * rizasini geri cekebilecegi KALICI bir giris yoktu. Bu, AdMob AB Kullanici
 * Rizasi Politikasi (ve GDPR) acisindan ihlaldir; yaptirimi **AdMob hesap
 * seviyesinde** gelir, tek bir uygulamanin kaldirilmasiyla sinirli kalmaz.
 * Ekranin ilk varlik sebebi budur; ses satiri buraya **tasindi**.
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
 * @param adHost gizlilik secenekleri satirinin sahibi. Varsayilani
 *   [LocalAdHost] — cunku bu ekrani acan composable'lar (`MainMenuOverlay`,
 *   `PauseMenuModal`) `AdHost` almiyor ve imzalarini degistirmek `GameScreen.kt`
 *   dosyasindaki cagri yerlerine dokunmayi gerektirirdi (o dosya baska bir is
 *   kolunun elinde).
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
    adHost: AdHost = LocalAdHost.current,
    versionName: String = BuildConfig.VERSION_NAME,
    versionCode: Int = BuildConfig.VERSION_CODE
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // UMP formu kapaninca "gizlilik secenekleri gerekli mi" cevabi degisebilir.
    var privacyTick by remember { mutableIntStateOf(0) }

    // "Ilerlemeyi sifirla" IKI ADIMLI. Diyalog yerine yerinde onay:
    // bu ekran zaten kaydirilabilir bir overlay ve ustune ikinci bir Dialog
    // acmak yatay ekranda (kullanilabilir yukseklik 320-400 dp) onay
    // butonlarini ekran disina itebiliyor.
    var confirmingReset by remember { mutableStateOf(false) }

    // Islem sonrasi geri bildirim. Sifirlama SESSIZ olursa oyuncu dokunup
    // hicbir sey olmadigini sanir ve tekrar dokunur.
    var resetDone by remember { mutableStateOf<String?>(null) }

    // `privacyOptionsRequired` @Volatile bir ALAN, akis degil — kendi basina
    // recomposition tetiklemez. Riza akisi sonuclandiginda degisen gozlenebilir
    // sinyal `canRequestAdsFlow`; onu toplayip alani YENIDEN OKUYORUZ. Ayni
    // kalip banner yuvasinda da kullaniliyor (`GameScreen.kt`), yani yeni degil.
    val consentSettled by ConsentManager.canRequestAdsFlow.collectAsState()

    val showPrivacyOptions = remember(adHost, consentSettled, privacyTick, activity) {
        shouldShowPrivacyOptions(adHost, hasActivity = activity != null)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .testTag("settings_screen"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SleekDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SleekBorderLight),
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

                // BASLIK SERIDI — kaydirma alaninin DISINDA. Oyun sensorLandscape;
                // yatayda kullanilabilir yukseklik 320-400 dp'ye kadar duser ve
                // kaydirilabilir bir baslikta "KAPAT" ekrandan cikabilir.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        color = SleekTextAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryGreen),
                        shape = RoundedCornerShape(14.dp),
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

                Spacer(Modifier.height(10.dp))

                // Icerik kaydirilir: yeni satirlar (muzik, haptik, gizlilik
                // politikasi) eklendikce yatay ekranda tasma olusmaz.
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
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
                    // GIZLILIK
                    //
                    // Satir KOSULLU: yalnizca UMP "bu oyuncu icin gizlilik
                    // secenekleri gerekli" dediginde cizilir (pratikte AB/EEA ve
                    // UK oyuncular). Kosul saglanmadiginda form ACILMAZ; her
                    // zaman gorunen bir satir, dokununca hicbir sey olmayan bir
                    // satir olurdu.
                    // ---------------------------------------------------------
                    if (showPrivacyOptions && activity != null) {
                        SettingsSectionHeader(stringResource(R.string.settings_section_privacy))
                        SettingsActionRow(
                            label = stringResource(R.string.settings_ad_settings),
                            description = stringResource(R.string.settings_ad_settings_desc),
                            onClick = {
                                // Form kapaninca gereklilik degismis olabilir
                                // (oyuncu rizayi geri cekti) -> satiri yeniden oku.
                                openPrivacyOptions(adHost, activity) { privacyTick++ }
                            },
                            testTag = "settings_privacy_options_row"
                        )
                    }

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
}

/**
 * "Reklam Ayarlari" satiri cizilsin mi?
 *
 * Composable'dan AYRI bir fonksiyon cunku bu, ekranin tek gercek KARARI ve
 * Compose kurmadan dogrulanabilmesi gerekiyor (bkz. `SettingsPrivacyGateTest`).
 *
 * Iki kosul da zorunlu:
 *  - [AdHost.privacyOptionsRequired] — UMP "bu oyuncu icin gizlilik secenekleri
 *    gerekli" diyor mu? Gerekmiyorsa form ACILMAZ; her zaman gorunen bir satir,
 *    dokununca hicbir sey olmayan bir satir olurdu.
 *  - [hasActivity] — UMP formu Activity ister. Activity bulunamadiginda
 *    (onizleme, test harness'i) satir cizilmez; crash yerine yokluk.
 */
internal fun shouldShowPrivacyOptions(adHost: AdHost, hasActivity: Boolean): Boolean =
    hasActivity && adHost.privacyOptionsRequired

/**
 * "Reklam Ayarlari" satirina dokunuldugunda calisan is.
 *
 * [activity] null olamayacagi bir yerden cagriliyor ([shouldShowPrivacyOptions]
 * zaten bunu garanti ediyor), ama savunma yine de burada: Activity kaybolmus bir
 * kenar durumda **cokme yok** — form acilmaz ve [onClosed] yine tam bir kez
 * cagrilir. Bu, reklam katmaninin genel sozlesmesiyle ayni ("hicbir cagri
 * basarisiz olamaz, callback tam bir kez gelir" — `AdHost` KDoc).
 *
 * [onClosed] satirin YENIDEN OKUNMASINI tetikler: oyuncu formda rizasini geri
 * cekmis olabilir ve `privacyOptionsRequired` degismis olabilir.
 */
internal fun openPrivacyOptions(adHost: AdHost, activity: Activity?, onClosed: () -> Unit) {
    if (activity == null) {
        onClosed()
        return
    }
    adHost.showPrivacyOptions(activity, onClosed)
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
            // onCheckedChange = null: anahtar burada GORSELDIR, dokunmayi satir
            // isler. Aksi halde ayni satirda iki ayri ac/kapa ogesi bildirilir
            // ve erisilebilirlik agaci ikiye bolunur.
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/**
 * Dokununca bir sey ACAN satir (su an: UMP gizlilik secenekleri formu).
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

/**
 * Ayarlar ekranina [AdHost]'u tasiyan kanal.
 *
 * Neden CompositionLocal: reklam katmaninin sahibi `MainActivity`'dir ve host
 * oradan `GameScreen`'e parametre olarak veriliyor. Ancak ayarlari ACAN
 * composable'lar (`MainMenuOverlay`, `PauseMenuModal`) `AdHost` almiyor; onlara
 * parametre eklemek `GameScreen.kt`'deki cagri yerlerini degistirmeyi
 * gerektirirdi ve o dosya su an baska bir is kolunun elinde. CompositionLocal,
 * ARADAKI dosyalara dokunmadan degeri gecirmenin tam da bu duruma uygun araci.
 *
 * Varsayilan [NoOpAdHost]: saglayici kurulmamissa (onizleme, birim testi)
 * `privacyOptionsRequired == false` doner, yani satir hic cizilmez. Guvenli
 * taraf varsayilan.
 */
val LocalAdHost = staticCompositionLocalOf<AdHost> { NoOpAdHost() }
