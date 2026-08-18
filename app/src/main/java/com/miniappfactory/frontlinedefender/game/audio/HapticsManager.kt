package com.miniappfactory.frontlinedefender.game.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * ---------------------------------------------------------------------------
 * Faz 14 — DOKUNSAL GERI BILDIRIM
 * ---------------------------------------------------------------------------
 * Once durum: tum kod tabaninda SIFIR titresim cagrisi vardi. Gorsel ve isitsel
 * geri bildirim varken dokunsal olani olmayinca butonlar "olu" hissettiriyor —
 * ozellikle bu oyunda, cunku kule insa etme ve guclendirici onaylama gibi
 * kararlarin bedeli var ve oyuncu parmagini kaldirmadan once "oldu" bilgisini
 * istiyor.
 *
 * ---------------------------------------------------------------------------
 * IZIN: `VIBRATE` MANIFEST'E EKLENMEDI — VE BUNA GEREK YOK
 * ---------------------------------------------------------------------------
 * `Vibrator.vibrate(...)` `android.permission.VIBRATE` ISTER. Manifest bugun
 * yalnizca INTERNET + ACCESS_NETWORK_STATE iceriyor ve bu bilincli bir sadelik
 * (Play Data Safety denetimini okunur tutuyor). Bu dosya izin EKLEMEZ.
 *
 * Bunun yerine IKI KANAL vardir ve calisma aninda hangisinin kullanilacagi
 * secilir:
 *
 *  1. [Channel.VIEW] — `View.performHapticFeedback(HapticFeedbackConstants...)`.
 *     IZIN GEREKTIRMEZ; titresimi sistem kendi adina calistirir, cihazin
 *     dokunsal geri bildirim ayarina da otomatik saygi duyar. Sozlugu dardir
 *     (tik / uzun basma / onay / ret) ama bu oyunun ihtiyaci olan olaylarin
 *     hepsini karsilar. VARSAYILAN kanal budur ve BUGUN calisan yol budur.
 *
 *  2. [Channel.VIBRATOR] — `VibratorManager` (API 31+) / `Vibrator` (eski).
 *     Sure ve genlik uzerinde tam kontrol, dalga bicimi (ornegin us hasarinda
 *     cift darbe) mumkun. YALNIZCA izin gercekten verilmisse devreye girer;
 *     [hasVibratePermission] bunu calisma aninda olcer.
 *
 * Yani manifest'e `VIBRATE` EKLENIRSE bu sinif kendiliginden daha zengin
 * desenlere gecer, eklenmezse dokunsal geri bildirim yine de CALISIR. Karar
 * ayarlar/manifest ajanina aittir; bu dosya ikisinde de dogru davranir.
 *
 * ---------------------------------------------------------------------------
 * API'DE SURE YOK, OLAY VAR
 * ---------------------------------------------------------------------------
 * Cagri yerleri `vibrate(30)` gibi bir sey GORMEZ; `onTowerBuilt()` cagirir.
 * Sure/genlik/desen eslemesi tek yerde ([Cue]) durur, boylece "insa hissi biraz
 * daha agir olsun" kararı tek satirda alinir ve UI dosyalarina sizmaz.
 */
class HapticsManager internal constructor(private val appContext: Context) : HapticsFeedback {

    /**
     * Dokunsal olaylar. Cagri yerleri YALNIZCA bunlari bilir.
     *
     * @param viewConstant izinsiz yolda kullanilan sistem sabiti.
     * @param durationMs izinli yolda temel sure. 40 ms'nin uzeri "agir" his
     *   verir; UI tiklarinda 10-15 ms'nin ustu gecikmis hissettirir.
     * @param amplitude 1..255 (API 26+ ve cihaz genlik destekliyorsa).
     * @param doublePulse us hasari gibi "kotu haber" olaylarinda cift darbe;
     *   tek darbeden AYIRT EDILEBILIR olmasi bilincli.
     */
    enum class Cue(
        val viewConstant: Int,
        val durationMs: Long,
        val amplitude: Int,
        val doublePulse: Boolean = false
    ) {
        /** Siradan buton/kart dokunusu. En hafif olan. */
        TAP(HapticFeedbackConstants.KEYBOARD_TAP, durationMs = 10L, amplitude = 60),

        /** Basili tutup menzil onizlemesi acma. */
        PREVIEW(HapticFeedbackConstants.LONG_PRESS, durationMs = 18L, amplitude = 110),

        /** Kule kuruldu — kararin "oturdugu" an, belirgin olmali. */
        BUILD(HapticFeedbackConstants.LONG_PRESS, durationMs = 32L, amplitude = 170),

        /** Kule yukseltildi. Insadan biraz daha hafif. */
        UPGRADE(HapticFeedbackConstants.LONG_PRESS, durationMs = 24L, amplitude = 140),

        /** Kule satildi — geri alma hissi, orta sertlik. */
        SELL(HapticFeedbackConstants.LONG_PRESS, durationMs = 20L, amplitude = 120),

        /** Guclendirici SILAHLANDI: "onay bekliyorum" uyarisi. */
        ARM(HapticFeedbackConstants.LONG_PRESS, durationMs = 22L, amplitude = 130),

        /**
         * Guclendirici ONAYLANDI ve ucret kesildi. En agir olumlu olay.
         *
         * `CONFIRM`/`REJECT` sabitleri API 30'da eklendi ama derleyici `int`
         * sabitlerini SATIR ICINE ALIR, yani eski cihazda sinif yuklenirken
         * hata olmaz; degerin gecersiz oldugu cihazlarda [fireView] zaten
         * SDK_INT'e bakip yedek sabite duser. InlinedApi bu yuzden bastirildi.
         */
        @SuppressLint("InlinedApi")
        CONFIRM(HapticFeedbackConstants.CONFIRM, durationMs = 38L, amplitude = 200),

        /** Islem REDDEDILDI (para yetmiyor, bekleme, hedef yok). */
        @SuppressLint("InlinedApi")
        REJECT(HapticFeedbackConstants.REJECT, durationMs = 26L, amplitude = 150, doublePulse = true),

        /** Us hasar aldi. Cift darbe: iyi haberle karistirilamaz. */
        BASE_HIT(HapticFeedbackConstants.LONG_PRESS, durationMs = 45L, amplitude = 220, doublePulse = true),

        /**
         * Oldurme zinciri kademe atladi (1..4).
         *
         * UI cue'lari YENIDEN KULLANILMADI, ayri dort basamak acildi: zincir
         * bir OYNANIS olayi, "kule kuruldu" ise bir arayuz onayi. Ayni cue
         * paylasilsaydi buton hissini ayarlamak zincir merdivenini sessizce
         * bozardi. Merdiven ses tarafiyla ayni: `sfx_combo_up_1..4`.
         */
        COMBO_1(HapticFeedbackConstants.KEYBOARD_TAP, durationMs = 14L, amplitude = 90),
        COMBO_2(HapticFeedbackConstants.LONG_PRESS, durationMs = 20L, amplitude = 130),
        COMBO_3(HapticFeedbackConstants.LONG_PRESS, durationMs = 27L, amplitude = 170),
        COMBO_4(HapticFeedbackConstants.LONG_PRESS, durationMs = 34L, amplitude = 205)
    }

    private enum class Channel { VIEW, VIBRATOR, NONE }

    companion object {
        /** Ayni olayin spam'ini keser: 60 Hz'de tekrar tetiklenen tik camur olur. */
        private const val MIN_INTERVAL_MS = 45L

        @Volatile
        private var instance: HapticsManager? = null

        /**
         * Surec basina TEK ornek. `Vibrator` zaten bir sistem servisi ve
         * `GameScreen` bu ajanin dosyasi DEGIL — yani manager'i orada
         * olusturup asagi gecirmek mumkun degildi. Uygulama context'ine bagli
         * bir singleton ile UI dosyalari [rememberHaptics] uzerinden erisiyor.
         */
        internal fun get(context: Context): HapticsManager =
            instance ?: synchronized(this) {
                instance ?: HapticsManager(context.applicationContext).also { instance = it }
            }

        /**
         * Zincir kademesi -> dokunsal basamak. 1 tabanli.
         *
         * KIRPILIR, sarmaz — `AudioManager.comboEffectFor` ile ayni kural:
         * modulo alinsaydi 5. kademe EN HAFIF darbeye donerdi ve tirmanma
         * tam zirvede cokerdi.
         */
        internal fun comboCueFor(tier: Int): Cue = when {
            tier <= 1 -> Cue.COMBO_1
            tier == 2 -> Cue.COMBO_2
            tier == 3 -> Cue.COMBO_3
            else -> Cue.COMBO_4
        }
    }

    private val vibrator: Vibrator? = resolveVibrator(appContext)

    /**
     * Izin GERCEKTEN verilmis mi. Manifest'te `VIBRATE` yoksa false doner ve
     * [Channel.VIEW] kullanilir.
     */
    private val hasVibratePermission: Boolean =
        runCatching {
            appContext.checkSelfPermission(android.Manifest.permission.VIBRATE)
        }.getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED

    private val channel: Channel = when {
        hasVibratePermission && vibrator?.hasVibrator() == true -> Channel.VIBRATOR
        else -> Channel.VIEW
    }

    /**
     * TANI YUZEYI. Hangi kanalin secildigi disaridan GORULEBILIR olmali:
     * "haptik calisiyor mu" sorusunun cevabi izne, cihaza ve SDK surumune
     * bagli ve bunlarin hicbiri cagri yerinden anlasilmaz. Testler bu iki
     * ozellik uzerinden dogru dala dusuldugunu kanitlar.
     */
    internal val usesVibratorApi: Boolean get() = channel == Channel.VIBRATOR
    internal val amplitudeControlAvailable: Boolean get() = hasAmplitudeControl

    /** Cihaz genlik destekliyor mu (API 26+). Desteklemiyorsa sure ile idare edilir. */
    private val hasAmplitudeControl: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasAmplitudeControl() == true

    /**
     * Fallback kanali icin gereken `View`. `LocalView` composition'a bagli
     * oldugu icin [rememberHaptics] tarafindan takilir/cikarilir.
     */
    @Volatile
    private var attachedView: View? = null

    private val lastFiredAt = HashMap<Cue, Long>()

    /**
     * Oyuncu dokunsal geri bildirimi kapatabilir. Tercih [FeedbackPrefs]
     * icinde, ilerleme kaydindan AYRI bir dosyada tutulur.
     */
    var isHapticsEnabled: Boolean = FeedbackPrefs.isHapticsEnabled(appContext)
        set(value) {
            field = value
            FeedbackPrefs.setHapticsEnabled(appContext, value)
        }

    // =====================================================================
    // OLAY API'SI — cagri yerlerinin gordugu tek yuzey
    // =====================================================================

    /** Siradan bir buton/kart dokunusu. */
    fun onUiTap() = fire(Cue.TAP)

    /** Kule karti basili tutuldu, menzil onizlemesi acildi. */
    fun onRangePreviewShown() = fire(Cue.PREVIEW)

    /** Kule kuruldu. */
    fun onTowerBuilt() = fire(Cue.BUILD)

    /** Kule yukseltildi. */
    fun onTowerUpgraded() = fire(Cue.UPGRADE)

    /** Kule satildi. */
    fun onTowerSold() = fire(Cue.SELL)

    /** Hedefleme modu degistirildi. */
    fun onTargetingModeCycled() = fire(Cue.TAP)

    /** Guclendirici silahlandi, onay bekliyor. */
    fun onBoosterArmed() = fire(Cue.ARM)

    /** Guclendirici onaylandi ve uygulandi. */
    fun onBoosterConfirmed() = fire(Cue.CONFIRM)

    /** Islem reddedildi (bakiye, bekleme, hedef yok...). */
    fun onActionRejected() = fire(Cue.REJECT)

    /** Us hasar aldi. */
    override fun onBaseHit() = fire(Cue.BASE_HIT)

    /** Oldurme zinciri bir kademe atladi; siddet kademeyle artar. */
    override fun onComboTierUp(tier: Int) = fire(comboCueFor(tier))

    // =====================================================================

    private fun fire(cue: Cue) {
        if (!isHapticsEnabled || channel == Channel.NONE) return

        val now = SystemClock.uptimeMillis()
        val last = lastFiredAt[cue] ?: 0L
        if (now - last < MIN_INTERVAL_MS) return
        lastFiredAt[cue] = now

        when (channel) {
            Channel.VIBRATOR -> fireVibrator(cue)
            Channel.VIEW -> fireView(cue)
            Channel.NONE -> Unit
        }
    }

    private fun fireView(cue: Cue) {
        val view = attachedView ?: return
        // CONFIRM/REJECT API 30 oncesinde yok; eski cihazda anlamli en yakin
        // sabite dus. `performHapticFeedback` bilinmeyen sabitte false doner,
        // sessizce hicbir sey OLMAMASINDANSA yakin bir his verilir.
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cue.viewConstant
        } else {
            when (cue) {
                Cue.CONFIRM, Cue.BASE_HIT -> HapticFeedbackConstants.LONG_PRESS
                Cue.REJECT -> HapticFeedbackConstants.LONG_PRESS
                else -> cue.viewConstant
            }
        }
        view.performHapticFeedback(
            constant,
            // Cihazin "dokunsal geri bildirim" ayari kapaliysa BASTIRMA:
            // kullanicinin sistem tercihine saygi duyulur.
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    /**
     * ⚠ `MissingPermission` BILEREK bastirildi. Lint hakli: manifest'te
     * `VIBRATE` YOK. Ama bu metoda yalnizca [channel] == VIBRATOR iken
     * girilir ve o da [hasVibratePermission] calisma aninda GRANTED donduyse
     * secilir — yani izin gercekten yoksa bu kod hic calismaz, izin ileride
     * eklenirse kendiliginden devreye girer. Ayrica tum cagri `runCatching`
     * icinde: beklenmedik bir `SecurityException` oyunu dusurmez.
     */
    @SuppressLint("MissingPermission")
    private fun fireVibrator(cue: Cue) {
        val vib = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when {
                    cue.doublePulse -> {
                        val timings = longArrayOf(0L, cue.durationMs, 55L, cue.durationMs)
                        if (hasAmplitudeControl) {
                            val amps = intArrayOf(0, cue.amplitude, 0, cue.amplitude)
                            VibrationEffect.createWaveform(timings, amps, -1)
                        } else {
                            VibrationEffect.createWaveform(timings, -1)
                        }
                    }
                    hasAmplitudeControl ->
                        VibrationEffect.createOneShot(cue.durationMs, cue.amplitude)
                    else ->
                        VibrationEffect.createOneShot(
                            cue.durationMs,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                }
                vib.vibrate(effect)
            } else {
                // API 24-25: yalnizca sure. Genlik yok, cift darbe elle kurulur.
                @Suppress("DEPRECATION")
                if (cue.doublePulse) {
                    vib.vibrate(longArrayOf(0L, cue.durationMs, 55L, cue.durationMs), -1)
                } else {
                    vib.vibrate(cue.durationMs)
                }
            }
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /**
     * View SAYAC ILE tutulur.
     *
     * Neden: [rememberHaptics] kullanan uc composable ayni anda ekranda
     * olabilir ve hepsi AYNI kok `View`i (`LocalView`) gorur. Basit bir
     * "dispose olunca null'la" yaklasiminda `BoosterRail` gorunmez olur olmaz
     * view kopardi ve hala ekranda duran `TowerBuildBar` sessizce dokunsal
     * geri bildirimini kaybederdi. Sayac sifira inmeden kopmaz.
     *
     * Tutmayi birakmak da ZORUNLU: kok view Activity'yi tutar, singleton'da
     * asili birakmak Activity sizintisi demektir.
     */
    private val viewRefs = HashMap<View, Int>()

    /** [rememberHaptics] tarafindan cagrilir; baska yerden cagrilmamali. */
    internal fun attachView(view: View) {
        synchronized(viewRefs) {
            viewRefs[view] = (viewRefs[view] ?: 0) + 1
            attachedView = view
        }
    }

    internal fun detachView(view: View) {
        synchronized(viewRefs) {
            val remaining = (viewRefs[view] ?: 0) - 1
            if (remaining > 0) {
                viewRefs[view] = remaining
                return
            }
            viewRefs.remove(view)
            if (attachedView === view) {
                // Baska bir view (ornegin dialog) hala tutuyorsa ona gec.
                attachedView = viewRefs.keys.firstOrNull()
            }
        }
    }

    /**
     * Devam eden titresimi keser — lifecycle ON_PAUSE ve reklam icin. Reklam
     * acilirken elde titreyen bir darbe kalmasi "uygulama takildi" hissi verir.
     *
     * `MissingPermission` [fireVibrator] ile ayni gerekceyle bastirildi:
     * buraya yalnizca izin GERCEKTEN verilmisken secilen VIBRATOR kanalinda
     * girilir.
     */
    @SuppressLint("MissingPermission")
    fun onPause() {
        if (channel == Channel.VIBRATOR) runCatching { vibrator?.cancel() }
    }
}

/**
 * Compose tarafindan dokunsal geri bildirime erisim.
 *
 * `GameScreen.kt` baska bir ajanin dosyasi oldugu icin manager oradan asagi
 * gecirilemedi; bunun yerine her cagri yeri bu yardimciyi kullanir. Singleton
 * oldugu icin ekstra ornek olusmaz, `View` ise composition'a bagli olarak
 * takilip cikarilir.
 */
@Composable
fun rememberHaptics(): HapticsManager {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember(context.applicationContext) { HapticsManager.get(context) }

    DisposableEffect(view, haptics) {
        haptics.attachView(view)
        onDispose { haptics.detachView(view) }
    }

    // YASAM DONGUSU BURADA BAGLANIR. `AudioManager` lifecycle olaylarini
    // `GameScreen`den aliyor ama o dosya bu ajanin degil; haptik icin de ayni
    // kancayi istemek yerine composable kendi gozlemcisini kuruyor. Onemi:
    // reklam acilirken elde titreyen bir darbe kalirsa oyuncu bunu
    // "uygulama takildi" diye okur.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, haptics) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) haptics.onPause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            haptics.onPause()
        }
    }
    return haptics
}

/**
 * Muzik ve dokunsal geri bildirim tercihlerinin kalici deposu.
 *
 * ---------------------------------------------------------------------------
 * NEDEN AYRI BIR DOSYA (`SaveManager`in dosyasi DEGIL)
 * ---------------------------------------------------------------------------
 * Ilk uygulamada bu tercihler `SaveManager` ile ayni dosyada
 * (`frontline_defender_prefs`) tutuluyordu. Regresyon denetimi bunun sessiz
 * bir hata urettigini gosterdi:
 *
 *   `SaveManager.resetProgress()` `PRESERVED_ON_RESET` disindaki TUM
 *   anahtarlari siler ve o kume yalnizca `sound_enabled` + `save_version`
 *   iceriyor. Yani "ilerlemeyi sifirla" muzigi ve titresimi de aciyordu —
 *   oysa bunlar ilerleme degil AYAR. `SaveManager`in kendi KDoc'u da
 *   "ayarlar korunur" diyor; celiski koddaydi.
 *
 * `PRESERVED_ON_RESET`e eklemek de cozerdi ama `SaveManager.kt` baska bir
 * ajanda ve cozum baskasinin dosyasindaki bir listeyi guncel tutmaya bagli
 * kalirdi. AYRI DOSYA celiskiyi YAPISAL olarak kapatir: `resetProgress` bu
 * dosyaya hic dokunamaz, ileride eklenecek yeni bir ayar da otomatik korunur.
 *
 * Sahiplik de netlesir: oyun AYARLARI ile oyun ILERLEMESI zaten farkli yasam
 * dongulerine sahip. Maliyet tek bir ek XML dosyasi.
 *
 * Not: bu tercihler hic yayinlanmis bir surumde bulunmadi, dolayisiyla eski
 * dosyadan tasima gerekmiyor.
 */
internal object FeedbackPrefs {
    /** ⚠ `SaveManager`in "frontline_defender_prefs" dosyasindan KASITLI olarak ayri. */
    private const val PREFS_NAME = "frontline_defender_feedback_prefs"
    const val KEY_MUSIC_ENABLED = "music_enabled"
    const val KEY_HAPTICS_ENABLED = "haptics_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMusicEnabled(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_MUSIC_ENABLED, true) }.getOrDefault(true)

    fun setMusicEnabled(context: Context, value: Boolean) {
        runCatching { prefs(context).edit().putBoolean(KEY_MUSIC_ENABLED, value).apply() }
    }

    fun isHapticsEnabled(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_HAPTICS_ENABLED, true) }.getOrDefault(true)

    fun setHapticsEnabled(context: Context, value: Boolean) {
        runCatching { prefs(context).edit().putBoolean(KEY_HAPTICS_ENABLED, value).apply() }
    }
}
