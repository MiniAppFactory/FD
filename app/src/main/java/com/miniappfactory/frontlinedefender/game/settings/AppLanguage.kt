package com.miniappfactory.frontlinedefender.game.settings

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * =============================================================================
 * UYGULAMA ICI DIL SECIMI
 * =============================================================================
 *
 * Oyun bugune kadar YALNIZCA sistem dilini kullaniyordu (`values/` + `values-tr/`).
 * Bu dosya oyuncuya oyunun icinden Ingilizce/Turkce secme imkani verir.
 *
 * ## Neden `AppCompatDelegate.setApplicationLocales` DEGIL
 *
 * Bu modul `appcompat` bagimliligi TASIMIYOR (bkz. `app/build.gradle.kts` —
 * bagimlilik listesi Compose + core-ktx + lifecycle + ads). Yalnizca dil
 * anahtari icin appcompat eklemek, `minSdk 24` hedefli bir oyuna sirf bir
 * ayar satiri ugruna yeni bir kutuphane ve yeni bir Activity taban sinifi
 * sokardi.
 *
 * ## Kullanilan yontem
 *
 * Compose agacinin kokunde [LocalContext], dili DEGISTIRILMIS bir
 * `ContextWrapper` ile degistirilir. `stringResource` cagrilarinin tamami o
 * context'in `Resources` nesnesinden okur, yani TEK BIR SAGLAYICI butun
 * ekranlari cevirir; hicbir cagri yerine dokunulmaz.
 *
 * ## ⚠ NEDEN `createConfigurationContext` DOGRUDAN KULLANILMIYOR
 *
 * `context.createConfigurationContext(conf)` bir `ContextImpl` dondurur —
 * yani zincirde Activity YOKTUR. `SettingsScreen.findActivity()` zinciri
 * `ContextWrapper.baseContext` uzerinden yuruyerek Activity ariyor ve null
 * bulursa **UMP "Reklam Ayarlari" satiri hic cizilmiyor** (bkz.
 * `shouldShowPrivacyOptions`). Yani duz `createConfigurationContext` gizlilik
 * satirini sessizce yok ederdi.
 *
 * Buradaki `localizedContext` bunun yerine Activity'yi SARAR: `baseContext` hala
 * Activity'dir, yalnizca `getResources()` cevrilmis olani dondurur. Zincir
 * bozulmaz.
 */
enum class AppLanguage(val tag: String?) {
    /** Cihazin sistem dili — varsayilan. */
    SYSTEM(null),
    ENGLISH("en"),
    TURKISH("tr");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM

        /**
         * Secicide gosterilen IKI segment. `SYSTEM` segment DEGILDIR:
         * secici iki durumlu bir sanat varligi ve ucuncu bir durumu cizemez.
         * Oyuncu bir dile dokundugu anda secim artik acik bir tercihtir;
         * "sistem" yalnizca HIC dokunulmamis baslangic halidir.
         */
        val SELECTABLE = listOf(ENGLISH, TURKISH)
    }
}

/**
 * Dil tercihinin kalici deposu.
 *
 * `SaveManager`'da DEGIL, kendi dosyasinda — muzik ve dokunsal geri bildirim
 * tercihleriyle AYNI desen ("Tercihler `SaveManager`da DEGIL, ilgili
 * yoneticilerin kendi kalici deposunda", bkz. `MainMenuOverlay`). Boylece
 * `SaveManager.resetProgress()` oyuncunun dilini SIFIRLAMAZ: ilerlemeyi
 * silmek, oyunu anlamadigi bir dile dondurmek anlamina gelmemeli.
 */
class LanguagePreference(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null))
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value.tag).apply()
        }

    private companion object {
        const val PREFS_NAME = "fd_language"
        const val KEY_LANGUAGE = "language_tag"
    }
}

/**
 * Secili dili ve onu degistirme yolunu agaca tasiyan kanal.
 *
 * Varsayilan [AppLanguage.SYSTEM] + no-op setter: saglayici kurulmamissa
 * (onizleme, birim testi) ekran sistem diliyle cizilir ve secici dokunmaya
 * tepki vermez — cokme yerine yokluk.
 */
class AppLanguageController(
    val current: AppLanguage,
    val onChange: (AppLanguage) -> Unit
)

val LocalAppLanguage = staticCompositionLocalOf {
    AppLanguageController(AppLanguage.SYSTEM) {}
}

/**
 * Activity'yi saran, YALNIZCA `Resources`'i degistiren wrapper.
 *
 * `android.view.ContextThemeWrapper` (appcompat'inki DEGIL — o bagimlilik
 * projede yok) secildi cunku `applyOverrideConfiguration` API 17'den
 * beri var ve `baseContext` olarak Activity'yi KORUR — `findActivity()`
 * zinciri calismaya devam eder (bkz. sinif KDoc'undaki uyari).
 */
private fun localizedContext(base: Context, locale: Locale): Context {
    val overrideConfig = Configuration(base.resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return ContextThemeWrapper(base, /* themeResId = */ 0).apply {
        applyOverrideConfiguration(overrideConfig)
    }
}

/**
 * Compose agacinin kokune sarilir; altindaki HER `stringResource` secili dilde
 * cozulur.
 *
 * [AppLanguage.SYSTEM] secildiginde HICBIR sarma yapilmaz — context aynen
 * gecer. Yani dil hic degistirilmemis bir kurulumda bu saglayici davranissal
 * olarak GORUNMEZDIR ve mevcut ekranlar birebir eskisi gibi cizilir.
 */
@Composable
fun AppLanguageProvider(
    preference: LanguagePreference,
    content: @Composable () -> Unit
) {
    var language by rememberLanguageState(preference)
    val baseContext = LocalContext.current

    val effectiveContext = remember(language, baseContext) {
        val tag = language.tag
        if (tag == null) baseContext else localizedContext(baseContext, Locale.forLanguageTag(tag))
    }

    val controller = AppLanguageController(current = language) { selected ->
        // Once KALICI kayit, sonra ekrandaki durum: uygulama tam bu anda
        // oldurulurse oyuncunun secimi kaybolmasin.
        preference.language = selected
        language = selected
    }

    CompositionLocalProvider(
        LocalContext provides effectiveContext,
        // `stringResource` degisimi FARK ETSIN diye configuration da
        // saglanir; yalnizca context degisseydi Compose onbellekteki
        // eski dizeyi yeniden kullanabilirdi.
        LocalConfiguration provides effectiveContext.resources.configuration,
        LocalAppLanguage provides controller,
        content = content
    )
}

@Composable
private fun rememberLanguageState(preference: LanguagePreference): MutableState<AppLanguage> =
    remember(preference) { mutableStateOf(preference.language) }
