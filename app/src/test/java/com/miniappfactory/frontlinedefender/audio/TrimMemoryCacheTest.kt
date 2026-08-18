package com.miniappfactory.frontlinedefender.audio

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.miniappfactory.frontlinedefender.MainActivity
import com.miniappfactory.frontlinedefender.game.model.Biome
import com.miniappfactory.frontlinedefender.game.ui.BiomeBackgroundCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Faz 14 — ARKA PLANDA BITMAP IADESI.
 *
 * ---------------------------------------------------------------------------
 * DUZELTILEN HATA
 * ---------------------------------------------------------------------------
 * `BiomeBackgroundCache.clear()` tanimliydi ve birim testi VARDI, ama
 * `app/src/main` icinde HICBIR CAGIRANI YOKTU. Yani test yesildi, kod
 * dogruydu, ve uygulama arka plandayken decode + recolor edilmis arka plan
 * bitmapi (performans denetiminde olculen **7,92 MiB**) bellekte duruyordu.
 * Birincil test cihazi Galaxy S8 / API 24 — projenin en dar bellek butcesi.
 *
 * Bu tam olarak "yazilmis ama baglanmamis" sinifindan bir hata; asagidaki
 * testler `clear()`in dogrulugunu DEGIL, **CAGRILDIGINI** kilitler.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrimMemoryCacheTest {

    private fun activity(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).get()

    private fun fillCache() {
        // `ImageBitmap(w, h)` Robolectric altinda ColorSpace yolunda NPE atiyor;
        // golgelenmis `Bitmap.createBitmap` guvenli ve burada onemli olan tek sey
        // onbellegin DOLU olmasi.
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImageBitmap()
        BiomeBackgroundCache.put(mapId = 1, biome = Biome.NIGHT, bitmap = bitmap)
        assertEquals("on kosul: onbellek dolu olmali", 1, BiomeBackgroundCache.size)
    }

    @Before
    fun setUp() = BiomeBackgroundCache.clear()

    @After
    fun tearDown() = BiomeBackgroundCache.clear()

    @Test
    fun `arayuz gizlendiginde bitmap serbest birakilir`() {
        fillCache()

        activity().onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        assertEquals(
            "uygulama arka plana alininca arka plan bitmapi iade edilmeli",
            0,
            BiomeBackgroundCache.size
        )
    }

    @Test
    fun `daha agir bellek baskisinda da serbest birakilir`() {
        // Esik `>=` ile yazildi; UI_HIDDEN uzerindeki her seviye de temizlemeli.
        listOf(
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        ).forEach { level ->
            fillCache()
            activity().onTrimMemory(level)
            assertEquals("seviye $level temizlemeliydi", 0, BiomeBackgroundCache.size)
        }
    }

    @Test
    fun `oyun ON PLANDAYKEN onbellek KORUNUR`() {
        // ⚠ Asil incelik burada. Oyuncu hala oynuyorken temizlemek, "tekrar
        // dene" dendiginde ayni haritanin yeniden decode + recolor edilmesi
        // demek (gece biyomunda 137 ms+): bellek kazanci dogrudan bolum acilis
        // gecikmesine cevrilirdi. Bu yuzden esik UI_HIDDEN, daha asagisi degil.
        fillCache()

        listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        ).forEach { level ->
            activity().onTrimMemory(level)
            assertEquals(
                "on planda temizlemek bolum acilisini yavaslatir (seviye $level)",
                1,
                BiomeBackgroundCache.size
            )
        }
    }
}
