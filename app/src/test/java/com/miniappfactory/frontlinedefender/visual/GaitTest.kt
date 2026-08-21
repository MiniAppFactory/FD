package com.miniappfactory.frontlinedefender.visual

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.ui.Gait
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YURUYUS DONGUSU — prosedurel salinim.
 *
 * Cihaz raporu: *"piyadeler yururken tek adimi ileri atar halde ve sabit."*
 * Sprite'lar TEK KARE; yurume dongusu icin yeni kare uretmek yerine govde,
 * kendi ilerlemesine bagli olarak saliniyor.
 *
 * ---------------------------------------------------------------------------------
 * BURADA KILITLENEN SEY GUZELLIK DEGIL, IKI SOMUT RISK
 * ---------------------------------------------------------------------------------
 *
 * 1. **SALINIM ASKERI YOLUN DISINA TASIYAMAZ.** Ayni gun bes rota tek tek yola
 *    oturtuldu (`RouteStaysOnRoadTest`); genligi buyutmek o isi GORSEL olarak
 *    geri alirdi — rota yolda kalir ama gorunen asker cimde olurdu. Yolun yarim
 *    genisligi ~37 ref-px, piyade govdesinin yarisi ~23; kalan pay salinimin
 *    TAVANIDIR.
 *
 * 2. **ARACLAR SALINMAZ.** Tank ve jeepte yanal salinim "suspansiyon" degil
 *    "kayma" olarak okunur. Siniflandirma `when` ile TUKETICI oldugu icin yeni
 *    bir dusman tipi eklendiginde derleyici karar vermeye ZORLAR — sessizce
 *    yaya sayilmaz.
 */
class GaitTest {

    /** Yolun olculen en dar yarim genisligi (RouteStaysOnRoadTest KDoc'u). */
    private val roadHalfWidthRefPx = 37f

    /** Piyade sprite genisliginin yarisi. */
    private val infantryHalfWidthRefPx =
        (GameConfig.ENEMY_SPRITES.getValue(GameConfig.EnemyType.INFANTRY).widthRefPx) / 2f

    @Test
    fun swayNeverPushesASoldierOffTheRoad() {
        val edge = infantryHalfWidthRefPx + Gait.SWAY_REF_PX
        assertTrue(
            "salinim tepesinde piyade govdesinin kenari $edge ref-px'e cikiyor; " +
                "yolun yarim genisligi $roadHalfWidthRefPx — asker cime tasar ve " +
                "ayni gun rotalari yola oturtan is GORSEL olarak geri alinir",
            edge <= roadHalfWidthRefPx
        )
    }

    @Test
    fun swayIsVisibleButNotAWobble() {
        assertTrue(
            "salinim ${Gait.SWAY_REF_PX} ref-px — gorunmeyecek kadar kucuk",
            Gait.SWAY_REF_PX >= 1.5f
        )
        assertTrue(
            "egilme ${Gait.LEAN_DEG} derece — 8 derece ustu 'sarhos yuruyus' olur",
            Gait.LEAN_DEG in 1.5f..8f
        )
    }

    /**
     * ADIM UZUNLUGU GOVDE OLCUSUYLE ILISKILI OLMALI.
     *
     * Cok kisaysa asker titrer, cok uzunsa dongu okunmaz ve yine "kayan heykel"
     * gorunur — duzeltilmeye calisilan hatanin ta kendisi.
     */
    @Test
    fun strideIsProportionalToTheBody() {
        val bodyWidth = infantryHalfWidthRefPx * 2f
        assertTrue(
            "adim ${Gait.STRIDE_REF_PX} ref-px, govde $bodyWidth — dongu govdenin " +
                "yarisindan kisa olursa titreme olarak okunur",
            Gait.STRIDE_REF_PX >= bodyWidth * 0.5f
        )
        assertTrue(
            "adim ${Gait.STRIDE_REF_PX} ref-px, govde $bodyWidth — dongu govdenin " +
                "iki katindan uzunsa hareket hic okunmaz",
            Gait.STRIDE_REF_PX <= bodyWidth * 2f
        )
    }

    @Test
    fun onlyFootUnitsSway() {
        listOf(
            GameConfig.EnemyType.INFANTRY,
            GameConfig.EnemyType.FAST_SOLDIER,
            GameConfig.EnemyType.SHIELDED_TROOPER
        ).forEach {
            assertTrue("$it yaya sayilmali", Gait.isFootUnit(it))
        }
        listOf(
            GameConfig.EnemyType.ARMORED_VEHICLE,
            GameConfig.EnemyType.TANK,
            GameConfig.EnemyType.COMMAND_TANK
        ).forEach {
            assertTrue("$it salinmamali — arac yanal salinimi 'kayma' okunur", !Gait.isFootUnit(it))
        }
    }
}
