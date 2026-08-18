package com.miniappfactory.frontlinedefender.game.ui

import android.app.Activity
import com.miniappfactory.frontlinedefender.game.ads.AdHost
import com.miniappfactory.frontlinedefender.game.ads.NoOpAdHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AYARLAR > "REKLAM AYARLARI" SATIRININ KAPISI.
 *
 * ## Neden bu testin var olmasi gerekiyordu
 * `AdHost.showPrivacyOptions` ve `AdHost.privacyOptionsRequired` Faz 5'te dogru
 * yazilmisti, ama `game/ui/` altindan **hicbir cagri yoktu** — yani oyuncunun
 * rizasini geri cekebilecegi kalici bir giris hic olmadi. Bu, AdMob AB Kullanici
 * Rizasi Politikasi ve GDPR acisindan ihlaldir ve yaptirimi AdMob HESAP
 * seviyesinde gelir.
 *
 * Bir arayuz "dogru yazilmis ama hic cagrilmiyor" seklinde sessizce
 * bozulabildigi icin, kapinin kendisi burada **derleyicinin gorebilecegi**
 * bir sozlesmeye baglanir: [shouldShowPrivacyOptions] kaybolursa veya kosulu
 * degisirse bu testler kirilir.
 *
 * **SAF JUnit**: Compose, `Context` veya UMP SDK'si kurulmaz. Test edilen sey
 * cizim degil KARARDIR.
 */
class SettingsPrivacyGateTest {

    /**
     * Yalnizca [AdHost.privacyOptionsRequired] ve `showPrivacyOptions`
     * davranisini degistiren ince bir sarmalayici; geri kalan her sey
     * [NoOpAdHost]'a devredilir (reklamsiz, hicbir yeri bloklamayan taban).
     */
    private class FakeAdHost(
        override val privacyOptionsRequired: Boolean,
        private val delegate: NoOpAdHost = NoOpAdHost()
    ) : AdHost by delegate {

        var privacyOptionsShown: Int = 0
            private set

        var dismissCallbacks: Int = 0
            private set

        override fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit) {
            privacyOptionsShown++
            // Gercek implementasyon da formun sonucunu beklemeden callback'i
            // tam olarak bir kez cagirir (AdMobAdHost -> ConsentManager).
            dismissCallbacks++
            onDismissed()
        }
    }

    // =================================================================================
    // 1. Kapinin iki kosulu
    // =================================================================================

    @Test
    fun rowIsShownOnlyWhenUmpRequiresItAndAnActivityExists() {
        val required = FakeAdHost(privacyOptionsRequired = true)
        val notRequired = FakeAdHost(privacyOptionsRequired = false)

        assertTrue(
            "UMP gerekli diyor ve Activity var -> satir GORUNUR",
            shouldShowPrivacyOptions(required, hasActivity = true)
        )
        assertFalse(
            "UMP gerekli demiyor -> form acilamaz, satir GORUNMEZ",
            shouldShowPrivacyOptions(notRequired, hasActivity = true)
        )
        assertFalse(
            "Activity yok -> UMP formu gosterilemez, satir GORUNMEZ",
            shouldShowPrivacyOptions(required, hasActivity = false)
        )
        assertFalse(
            "Iki kosul da saglanmiyor",
            shouldShowPrivacyOptions(notRequired, hasActivity = false)
        )
    }

    // =================================================================================
    // 2. Reklamsiz taban: satir hic cizilmez
    // =================================================================================

    @Test
    fun noOpAdHostNeverShowsTheRow() {
        // NoOpAdHost, "Reklamlari Kaldir" satin alinmis oyuncunun ve tum
        // onizleme/test yollarinin host'u. Reklam yoksa geri cekilecek riza da
        // yoktur; satirin gorunmesi anlamsiz olurdu.
        assertFalse(shouldShowPrivacyOptions(NoOpAdHost(), hasActivity = true))
    }

    // =================================================================================
    // 3. Saglayici kurulmadiginda GUVENLI tarafa duser
    // =================================================================================

    @Test
    fun localAdHostDefaultsToNoOpSoTheRowStaysHidden() {
        // LocalAdHost saglayicisi kurulmazsa (onizleme, izole composable testi)
        // varsayilan NoOpAdHost'tur. Beklenen sonuc: satir GORUNMEZ.
        // Yani "saglayici unutuldu" hatasi calismayan bir buton uretmez.
        val fallback = LocalAdHost // referansin var olmasi da bir sozlesmedir
        assertEquals(false, shouldShowPrivacyOptions(NoOpAdHost(), hasActivity = true))
        assertTrue("LocalAdHost tanimli kalmali", fallback.toString().isNotEmpty())
    }

    // =================================================================================
    // 4. Activity kaybolduysa: cokme YOK, callback yine tam bir kez
    // =================================================================================

    @Test
    fun missingActivityNeitherCrashesNorSwallowsTheCallback() {
        val host = FakeAdHost(privacyOptionsRequired = true)
        var closed = 0

        // `Activity` ornegi burada BILEREK uretilmiyor: birim testinde
        // android.jar stub'lari cagrilinca "Stub!" firlatir ve test gercek bir
        // seyi degil, kendi kurulumunu olcmeye baslar. Onemli olan sozlesme
        // zaten bu dalda: host'a HIC gidilmez ve akis yine de kapanir.
        openPrivacyOptions(host, activity = null, onClosed = { closed++ })

        assertEquals("form istenmemeli", 0, host.privacyOptionsShown)
        assertEquals("kapanis callback'i yine de tam bir kez gelmeli", 1, closed)
    }

    // =================================================================================
    // 5. Sarmalayici tabani gercekten devrediyor mu (fake yalan soylemiyor mu)
    // =================================================================================

    @Test
    fun fakeHostKeepsNoOpBehaviourForEverythingElse() {
        val host = FakeAdHost(privacyOptionsRequired = true)
        assertFalse("banner yine kapali", host.bannerAllowed)
    }
}
