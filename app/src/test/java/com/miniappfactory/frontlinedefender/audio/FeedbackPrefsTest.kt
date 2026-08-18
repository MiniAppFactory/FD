package com.miniappfactory.frontlinedefender.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.FeedbackPrefs
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Faz 14 — muzik/titresim tercihlerinin kalici deposu.
 *
 * ⚠ BU TESTIN ASIL KONUSU BIR REGRESYON: tercihler once `SaveManager` ile AYNI
 * SharedPreferences dosyasindaydi ve `resetProgress()` `PRESERVED_ON_RESET`
 * disindaki her anahtari sildigi icin **"ilerlemeyi sifirla" muzigi ve
 * titresimi de aciyordu**. Bunlar ilerleme degil AYAR; `SaveManager`in kendi
 * belgesi de "ayarlar korunur" diyor, celiski koddaydi.
 *
 * Cozum: AYRI bir prefs dosyasi. Boylece `resetProgress` bu tercihlere
 * yapisal olarak dokunamaz — baskasinin dosyasindaki bir listeyi guncel tutma
 * zorunlulugu ortadan kalkar. Asagidaki son test tam da bunu kilitliyor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FeedbackPrefsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `varsayilan olarak muzik ve titresim aciktir`() {
        assertTrue("muzik varsayilan olarak acik olmali", FeedbackPrefs.isMusicEnabled(context))
        assertTrue("titresim varsayilan olarak acik olmali", FeedbackPrefs.isHapticsEnabled(context))
    }

    @Test
    fun `tercihler kalicidir`() {
        FeedbackPrefs.setMusicEnabled(context, false)
        FeedbackPrefs.setHapticsEnabled(context, false)

        assertFalse(FeedbackPrefs.isMusicEnabled(context))
        assertFalse(FeedbackPrefs.isHapticsEnabled(context))

        // Tekrar acilabilmeli: tek yonlu bir anahtar oyuncuyu kilitler.
        FeedbackPrefs.setMusicEnabled(context, true)
        assertTrue(FeedbackPrefs.isMusicEnabled(context))
        assertFalse("titresim ayri bir anahtar; muzik acilinca degismemeli",
            FeedbackPrefs.isHapticsEnabled(context))
    }

    @Test
    fun `muzik ve titresim birbirinden bagimsizdir`() {
        FeedbackPrefs.setMusicEnabled(context, false)
        FeedbackPrefs.setHapticsEnabled(context, true)
        assertFalse(FeedbackPrefs.isMusicEnabled(context))
        assertTrue(FeedbackPrefs.isHapticsEnabled(context))
    }

    @Test
    fun `ayni dosyayi paylasmak SaveManager verisini bozmaz`() {
        val save = SaveManager(context)
        save.soundEnabled = false
        save.highScore = 4242
        save.setLevelStars(3, 2)

        FeedbackPrefs.setMusicEnabled(context, false)
        FeedbackPrefs.setHapticsEnabled(context, false)

        // Yandan yazma SaveManager'in hicbir alanina dokunmamali.
        assertFalse("ses ayari korunmali", save.soundEnabled)
        assertEquals("yuksek skor korunmali", 4242, save.highScore)
        assertEquals("yildizlar korunmali", 2, save.getLevelStars(3))

        // ...ve tersi: SaveManager yazinca geri bildirim tercihleri durmali.
        save.soundEnabled = true
        assertFalse("muzik tercihi SaveManager yazisindan etkilenmemeli",
            FeedbackPrefs.isMusicEnabled(context))
        assertFalse("titresim tercihi SaveManager yazisindan etkilenmemeli",
            FeedbackPrefs.isHapticsEnabled(context))
    }

    @Test
    fun `anahtar adlari sabittir`() {
        // Degistirilirse oyuncunun kaydettigi tercih sessizce kaybolur.
        assertEquals("music_enabled", FeedbackPrefs.KEY_MUSIC_ENABLED)
        assertEquals("haptics_enabled", FeedbackPrefs.KEY_HAPTICS_ENABLED)
    }

    @Test
    fun `tercihler SaveManager kayit dosyasinin DISINDA tutulur`() {
        FeedbackPrefs.setMusicEnabled(context, false)
        FeedbackPrefs.setHapticsEnabled(context, false)

        // Kayit dosyasinda bu anahtarlar HIC olmamali. Orada olsalardi
        // resetProgress() onlari silerdi.
        val saveFile = context.getSharedPreferences(
            "frontline_defender_prefs", Context.MODE_PRIVATE
        )
        assertFalse(
            "muzik tercihi kayit dosyasina sizmis",
            saveFile.contains(FeedbackPrefs.KEY_MUSIC_ENABLED)
        )
        assertFalse(
            "titresim tercihi kayit dosyasina sizmis",
            saveFile.contains(FeedbackPrefs.KEY_HAPTICS_ENABLED)
        )
    }

    @Test
    fun `ilerleme sifirlansa da ayarlar korunur`() {
        // REGRESYON KILIDI. Ayni dosyada olsalardi bu test duserdi.
        val save = SaveManager(context)
        save.highScore = 999
        FeedbackPrefs.setMusicEnabled(context, false)
        FeedbackPrefs.setHapticsEnabled(context, false)

        save.resetProgress()

        assertFalse(
            "sifirlama muzik tercihini acmamali - bu bir AYAR, ilerleme degil",
            FeedbackPrefs.isMusicEnabled(context)
        )
        assertFalse(
            "sifirlama titresim tercihini acmamali",
            FeedbackPrefs.isHapticsEnabled(context)
        )
    }
}
