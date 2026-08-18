package com.miniappfactory.frontlinedefender.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Faz 14 — HUD RECOMPOSE KAPSAMI (YAPISAL KILIT).
 *
 * ---------------------------------------------------------------------------
 * DUZELTILEN HATA
 * ---------------------------------------------------------------------------
 * `HUDOverlay` govdesi `gameEngine.preparationTimer.collectAsState()` okuyordu
 * ve motor bu akisi HER KARE guncelliyor. Sonuc: hazirlik fazi boyunca
 * saniyede 60 kez TUM HUD yeniden besteleniyordu — bolum basina ~600
 * recomposition, 55 bolumluk oturumda ~33.000 — ustelik tam da bolum
 * acilisinin en pahali anina binerek (biyom recolor + sprite decode).
 * `GameScreen`in kendi yorumu "HUD her karede recompose OLMAZ" diyordu.
 *
 * ---------------------------------------------------------------------------
 * NEDEN KAYNAK METNI UZERINDEN
 * ---------------------------------------------------------------------------
 * Bu bir DAVRANIS testi degil, YAPISAL kilittir ve oyle olmasi bilincli.
 * Recomposition sayimi `HUDOverlay`in ICINE sayac enjekte etmeyi gerektirirdi;
 * yani olcmek icin olculen seyi degistirmek gerekirdi. Buradaki regresyon
 * bicimi zaten tek ve nettir: biri `collectAsState`i govdeye geri tasir.
 * O hareket hicbir davranis testini kirmaz, hicbir ekran bozulmaz, yalnizca
 * kare suresi sessizce geri gelir. Kaynak yapisini kilitlemek bunu yakalar.
 *
 * Gercek kare-suresi olcumu cihazda `FDPerf` enstrumantasyonu ile yapilir
 * (docs/PERFORMANCE_REPORT.md 7.3).
 */
class HudRecompositionScopeTest {

    private fun hudSource(): String =
        listOf(
            File("src/main/java/com/miniappfactory/frontlinedefender/game/ui/HUDOverlay.kt"),
            File("app/src/main/java/com/miniappfactory/frontlinedefender/game/ui/HUDOverlay.kt"),
        ).firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
            ?: error("HUDOverlay.kt bulunamadi")

    @Test
    fun `hazirlik sayaci yalnizca TEK yerde okunur`() {
        // Duz metin arama: duzenli ifade kacislari testin kendisini kirilgan yapar.
        val reads = hudSource().split("preparationTimer.collectAsState").size - 1
        assertEquals(
            "preparationTimer birden fazla yerde toplaniyor; her ek okuma o " +
                "kapsami 60 Hz recompose eder",
            1,
            reads
        )
    }

    @Test
    fun `sayac okumasi kendi composable'inda, HUD govdesinde DEGIL`() {
        val src = hudSource()
        val bodyStart = src.indexOf("fun HUDOverlay(")
        assertTrue("HUDOverlay bulunamadi", bodyStart >= 0)

        val timerFn = src.indexOf("private fun PreparationTimerText(")
        assertTrue(
            "sayac kendi composable'ina ayrilmamis (PreparationTimerText yok)",
            timerFn > 0
        )

        val readAt = src.indexOf("preparationTimer.collectAsState")
        assertTrue("preparationTimer okumasi bulunamadi", readAt > 0)
        assertTrue(
            "preparationTimer HUDOverlay govdesinde okunuyor: hazirlik fazinda " +
                "tum HUD 60 Hz yeniden bestelenir",
            readAt > timerFn
        )
    }

    @Test
    fun `HUD govdesi diger akislari okumaya devam eder`() {
        // Kapsam ayirmasi HUD'in geri kalanini bozmamali: bunlar nadiren
        // degisir ve govdede okunmalari dogru.
        val src = hudSource()
        listOf("gold", "lives", "currentWaveIndex", "gameState").forEach { flow ->
            assertTrue(
                "$flow akisi HUD'dan kaybolmus",
                src.contains("$flow.collectAsState")
            )
        }
    }
}
