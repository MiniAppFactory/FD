package com.miniappfactory.frontlinedefender.audio

import android.Manifest
import android.app.Application
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.HapticsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Faz 14 — DOKUNSAL KANAL SECIMI.
 *
 * [HapticsManager] iki yoldan birini secer:
 *   VIBRATOR - `VibratorManager`/`Vibrator`, sure ve genlik kontrollu,
 *              `android.permission.VIBRATE` ISTER.
 *   VIEW     - `View.performHapticFeedback`, izinsiz calisan yedek.
 *
 * Secim izne + cihaza + SDK surumune bagli ve cagri yerinden GORUNMEZ; yani
 * yanlis dala dusmek sessiz bir kayiptir (titresim ya hic olmaz ya da zayif
 * olur, kimse fark etmez). Bu yuzden dal secimi ayrica kilitlenir.
 *
 * Ayarlar ajani izni manifest'e ekledigi icin uretimde ZENGIN yol aktiftir;
 * ilk test tam olarak bu gercegi pinler.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HapticsChannelTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** Gradle birim testi modul kokunden kosar; CI/IDE farki icin birkac yol denenir. */
    private fun manifestFile(): File =
        listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).firstOrNull { it.isFile } ?: error("AndroidManifest.xml bulunamadi")

    @Test
    fun `manifest VIBRATE iznini bildirir`() {
        // Bu satir silinirse zengin yol sessizce kapanir ve haptik VIEW
        // yedegine duser: hicbir test kirilmadan his zayiflar.
        val text = manifestFile().readText(Charsets.UTF_8)
        assertTrue(
            "AndroidManifest.xml android.permission.VIBRATE bildirmiyor",
            text.contains("android.permission.VIBRATE")
        )
    }

    @Test
    fun `izin verildiginde titresim API'si secilir`() {
        shadowOf(app).grantPermissions(Manifest.permission.VIBRATE)

        val haptics = HapticsManager(app)

        assertTrue(
            "izin varken zengin yol (Vibrator) secilmeliydi",
            haptics.usesVibratorApi
        )
    }

    @Test
    fun `izin yokken izinsiz yedege dusulur`() {
        shadowOf(app).denyPermissions(Manifest.permission.VIBRATE)

        val haptics = HapticsManager(app)

        assertFalse(
            "izin yokken Vibrator cagrilirsa SecurityException gelir",
            haptics.usesVibratorApi
        )
    }

    @Test
    fun `izin yokken olay tetiklemek cokmez`() {
        shadowOf(app).denyPermissions(Manifest.permission.VIBRATE)
        val haptics = HapticsManager(app)

        // View de bagli degil: hicbir kanal yokken bile cagri sessizce gecmeli.
        HapticsManager.Cue.entries.forEach { _ ->
            haptics.onTowerBuilt()
            haptics.onBoosterConfirmed()
            haptics.onBaseHit()
        }
        haptics.onPause()
    }

    @Test
    fun `cihazda titresim motoru yoksa yedege dusulur`() {
        // Izin VAR ama donanim YOK. Tablet/emulator gercegi; izne bakip
        // donanima bakmamak sessizce hicbir sey yapmayan bir kanal secerdi.
        shadowOf(app).grantPermissions(Manifest.permission.VIBRATE)
        val vibrator = app.getSystemService(Vibrator::class.java)
        shadowOf(vibrator).setHasVibrator(false)

        assertFalse(
            "titresim motoru yokken Vibrator kanali secilmemeli",
            HapticsManager(app).usesVibratorApi
        )

        shadowOf(vibrator).setHasVibrator(true)
    }

    @Test
    fun `genlik destegi cihazdan okunur`() {
        shadowOf(app).grantPermissions(Manifest.permission.VIBRATE)
        val vibrator = app.getSystemService(Vibrator::class.java)
        shadowOf(vibrator).setHasVibrator(true)

        shadowOf(vibrator).setHasAmplitudeControl(true)
        assertTrue(HapticsManager(app).amplitudeControlAvailable)

        // Desteklemeyen cihazda DEFAULT_AMPLITUDE yoluna dusulur; bunu
        // varsaymak eski telefonlarda sabit siddetli titresim demekti.
        shadowOf(vibrator).setHasAmplitudeControl(false)
        assertFalse(HapticsManager(app).amplitudeControlAvailable)
    }

    @Test
    fun `kapali haptik titresim uretmez`() {
        shadowOf(app).grantPermissions(Manifest.permission.VIBRATE)
        val vibrator = app.getSystemService(Vibrator::class.java)
        shadowOf(vibrator).setHasVibrator(true)

        val haptics = HapticsManager(app)
        assertTrue("on kosul: zengin yol aktif olmali", haptics.usesVibratorApi)

        haptics.isHapticsEnabled = false
        haptics.onTowerBuilt()
        // WARN: `isVibrating()` METOT olarak cagriliyor, `.isVibrating`
        // sentetik ozellik kisayolu olarak DEGIL. Kisayol sozdizimi lintin
        // UAST cozumleyicisinde bir cokmeye yol aciyor
        // (resolveSyntheticJavaPropertyAccessorCall) ve
        // `lintAnalyzeDebugUnitTest` gorevi "Unexpected failure during lint
        // analysis" ile dusuyor. Yanilticisi su: lint hatayi BASKA bir
        // dosyanin adiyla raporluyor (BalanceConsistencyTest.kt), yani
        // sebebi burada aramak icin once dosyalari cikarip denemek gerekti.
        // Ozellik sozdizimine GERI CEVIRMEYIN.
        assertFalse("kapaliyken titresim uretilmemeli", shadowOf(vibrator).isVibrating())

        // ...acilinca yeniden calismali: tek yonlu bir anahtar oyuncuyu kilitler.
        haptics.isHapticsEnabled = true
        haptics.onBoosterConfirmed()
        assertTrue("acikken titresim uretilmeli", shadowOf(vibrator).isVibrating())
    }
}
