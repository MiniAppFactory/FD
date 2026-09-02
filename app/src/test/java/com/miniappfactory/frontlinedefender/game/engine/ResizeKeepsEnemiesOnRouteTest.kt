package com.miniappfactory.frontlinedefender.game.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ===========================================================================
 * EKRAN YENIDEN BOYUTLANDIGINDA DUSMAN ROTADA KALIYOR MU?
 * ===========================================================================
 *
 * BULUNAN HATA (2026-09-02): [GameEngine.updateMapDimensions] olcu degisince
 * rotalari ve pad'leri yeni oynanis dikdortgenine tasiyor, KULELERI de pad
 * kimliginden yeniden konumlandiriyordu — ama **canli dusmanlar ve mermiler
 * eski piksel uzayinda kaliyordu**. Rota altlarindan kayiyor, dusman bir anda
 * yolun disinda beliriyor ve bir sonraki karede yeni rotaya CAPRAZ kesiyordu:
 * tam olarak oyuncunun "yol olmayan yerden geciyorlar" dedigi goruntu.
 *
 * NE ZAMAN OLUSUR: Activity YENIDEN OLUSTURULMADAN olan yeniden
 * boyutlandirmalarda. Manifest `screenSize|screenLayout|smallestScreenSize`
 * bayraklarini sogurdugu icin savas hayatta kalir ve geometri altindan
 * degisir. (`smallestScreenSize` AYNI degisiklikte eklendi: onsuz cok
 * pencereli bir yeniden boyutlandirma Activity'yi yeniden yaratip SUREN SAVASI
 * TUMUYLE yok ediyordu — motor `remember` ile tutuluyor, ViewModel degil.)
 *
 * BU TEST NEYI KILITLIYOR: yeniden boyutlandirmadan sonra her canli dusman,
 * YENI olcekteki rotaya en fazla bir tolerans kadar uzakta olmali. Duzeltme
 * geri alinirsa uzaklik yuzlerce piksele cikar ve test duser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land-xxhdpi")
class ResizeKeepsEnemiesOnRouteTest {

    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        audio = AudioManager(ctx)
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(ctx), audio)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    private class Norm(val id: String, val nx: Float, val ny: Float)

    /** Dusmanin OYNANIS DIKDORTGENI icindeki normalize konumu. */
    private fun normalized(): List<Norm> {
        val r = engine.fieldRect
        return engine.enemies.map {
            Norm(it.id, (it.posX - r.left) / r.width, (it.posY - r.top) / r.height)
        }
    }

    /**
     * ESAS GARANTI: yeniden boyutlandirma dusmanin SAHADAKI GORELI KONUMUNU
     * degistirmez.
     *
     * Bu, "rotaya uzakligi" olcmekten daha dogrudan ve daha saglam bir ifade:
     * rotalar da ayni dikdortgenden `normXToScreen`/`normYToScreen` ile
     * turedigi icin, normalize konum korunuyorsa dusman rotanin AYNI noktasinda
     * kalir — geometriyi yeniden hesaplamaya gerek kalmaz.
     */
    @Test
    fun aResizeDoesNotMoveEnemiesRelativeToTheField() {
        engine.updateMapDimensions(2220f, 1080f, 96f)
        engine.startNewGame(1)
        engine.startNextWaveNow()
        repeat(400) { engine.tick(1f / 60f) }

        val before = normalized().associateBy { it.id }
        assertTrue(
            "test anlamli olmadi: yeniden boyutlandirma aninda sahada dusman yok",
            before.isNotEmpty(),
        )
        val oldRect = engine.fieldRect

        // Activity yeniden yaratilmadan yeniden boyutlandirma (cok pencereli
        // ayirac cubugunun surtulmesi). Dikdortgen GERCEKTEN degismeli, yoksa
        // test bir sey olcmez.
        engine.updateMapDimensions(1500f, 1080f, 96f)
        val newRect = engine.fieldRect
        assertTrue(
            "oynanis dikdortgeni degismedi (%.0fx%.0f -> %.0fx%.0f) — senaryo hatayi uretmiyor"
                .format(oldRect.width, oldRect.height, newRect.width, newRect.height),
            kotlin.math.abs(newRect.width - oldRect.width) > 1f ||
                kotlin.math.abs(newRect.height - oldRect.height) > 1f,
        )

        normalized().forEach { now ->
            val was = before[now.id] ?: return@forEach
            val dx = kotlin.math.abs(now.nx - was.nx)
            val dy = kotlin.math.abs(now.ny - was.ny)
            assertTrue(
                "dusman %s sahada kaydi: (%.4f, %.4f) -> (%.4f, %.4f) — canli varliklar yeni dikdortgene tasinmamis"
                    .format(now.id, was.nx, was.ny, now.nx, now.ny),
                dx < 1e-3f && dy < 1e-3f,
            )
        }
    }

    /**
     * KARSIT ORNEK — tasima OLMASAYDI sapma buyuk olurdu.
     *
     * Tasima yapilmamis dunya, PIKSEL konumlari sabit tutulup YENI dikdortgene
     * gore normalize edilerek yeniden kurulur. Sapma olcum gurultusunun cok
     * uzerinde olmali; degilse bu senaryo hatayi uretmiyordur ve test bir sey
     * korumuyordur.
     */
    @Test
    fun withoutTheRemapTheRelativePositionWouldJump() {
        engine.updateMapDimensions(2220f, 1080f, 96f)
        engine.startNewGame(1)
        engine.startNextWaveNow()
        repeat(400) { engine.tick(1f / 60f) }

        val oldRect = engine.fieldRect
        val pixelBefore = engine.enemies.map { Triple(it.id, it.posX, it.posY) }
        assertTrue("sahada dusman yok", pixelBefore.isNotEmpty())
        val normBefore = pixelBefore.associate {
            it.first to Pair((it.second - oldRect.left) / oldRect.width, (it.third - oldRect.top) / oldRect.height)
        }

        engine.updateMapDimensions(1500f, 1080f, 96f)
        val newRect = engine.fieldRect

        // Tasima YAPILMAMIS hali: eski PIKSEL konumu, YENI dikdortgene gore.
        var worst = 0f
        pixelBefore.forEach { (id, px, py) ->
            val was = normBefore[id] ?: return@forEach
            val nx = (px - newRect.left) / newRect.width
            val ny = (py - newRect.top) / newRect.height
            val d = kotlin.math.max(kotlin.math.abs(nx - was.first), kotlin.math.abs(ny - was.second))
            if (d > worst) worst = d
        }
        assertTrue(
            "tasima yapilmasa bile en kotu goreli sapma yalnizca %.4f — senaryo hatayi uretmiyor".format(worst),
            worst > 0.02f,
        )
    }
}
