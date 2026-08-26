package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * =============================================================================
 * TAKTIK CERCEVE — kampanya ekraninin cizim dili
 * =============================================================================
 *
 * Hedef tasarimda (kullanicinin gonderdigi kampanya mockup'i) kartlar, perde
 * rayi, ust serit ve tedarik seridi ayni metalik dili paylasiyor: **kosesi
 * kesilmis** bir govde, cift kontur, koselerde parlak isaret cizgileri ve
 * "siradaki" ogede altin bir hale.
 *
 * ## Neden GORSEL DOSYA degil, CIZIM
 *
 * Bu cerceveyi bir PNG olarak eklemek dort ayri boyut (kart, ray, serit,
 * tedarik) ve iki durum (normal, vurgulu) icin **sekiz dosya** demekti; her
 * boyut degisiminde de yeniden uretim gerekirdi. Compose ile cizilince:
 *  · her olcude keskin — gerilme ve yeniden orneklemeden dogan bulaniklik yok,
 *  · APK'ya sifir bayt ekliyor,
 *  · renk/hale durumu (kilitli · tamamlanmis · siradaki) **kodda** yasiyor,
 *    yani sanat ile durum iki ayri yerde bayatlamiyor.
 *
 * Pack'in metalik plaka sanati (`ui_plate_header` vb.) SABIT oranlidir ve bu
 * ekrandaki degisken yukseklikli yuzeylere oturmuyordu — nitekim ust serite
 * plaka koyma denemesi kartlari kirpmisti (bkz. `LevelSelectScreen`).
 */

/** Cercevenin durum rengi ve halesi. */
enum class FrameTone {
    /** Kilitli / ileride — sonuk, halesiz. */
    MUTED,

    /** Acik ama siradaki degil — yesil kontur. */
    ACTIVE,

    /** Tamamlanmis — yesil, biraz daha parlak. */
    CLEARED,

    /** SIRADAKI — altin kontur + hale. Ekranda tek bir oge bunu tasir. */
    NEXT
}

private fun FrameTone.stroke(): Color = when (this) {
    FrameTone.MUTED -> Color(0xFF44503A)
    FrameTone.ACTIVE -> Color(0xFF6E8A4E)
    FrameTone.CLEARED -> Color(0xFF8CB45C)
    FrameTone.NEXT -> Color(0xFFD8A52A)
}

private fun FrameTone.glow(): Color? = when (this) {
    FrameTone.NEXT -> Color(0x66F1C95D)
    FrameTone.CLEARED -> Color(0x2E8CB45C)
    else -> null
}

private fun FrameTone.fill(): Brush = when (this) {
    FrameTone.MUTED -> Brush.verticalGradient(
        listOf(Color(0xFF1A1F16), Color(0xFF12160F))
    )
    FrameTone.CLEARED -> Brush.verticalGradient(
        listOf(Color(0xFF243019), Color(0xFF18200F))
    )
    FrameTone.NEXT -> Brush.verticalGradient(
        listOf(Color(0xFF2A2C16), Color(0xFF1A1B0E))
    )
    FrameTone.ACTIVE -> Brush.verticalGradient(
        listOf(Color(0xFF1E2618), Color(0xFF141A10))
    )
}

/**
 * Kosesi kesilmis (sekizgen) govde yolu.
 *
 * Kesim [chamfer] kadar; kutu cok kucukse kesim kendiliginden kuculur ki
 * kenarlar carpismasin.
 */
private fun chamferPath(size: Size, chamfer: Float): Path {
    val c = chamfer.coerceAtMost(minOf(size.width, size.height) / 2.5f)
    return Path().apply {
        moveTo(c, 0f)
        lineTo(size.width - c, 0f)
        lineTo(size.width, c)
        lineTo(size.width, size.height - c)
        lineTo(size.width - c, size.height)
        lineTo(c, size.height)
        lineTo(0f, size.height - c)
        lineTo(0f, c)
        close()
    }
}

/**
 * Koselerdeki KISA parlak isaret cizgileri.
 *
 * Mockup'ta cercevenin kendisi duz degil; her kosede kisa, daha parlak bir
 * cizgi var ve "olculmus/teknik" hissi buradan geliyor. Cizgiler kenar
 * uzunlugunun %18'i kadar.
 */
private fun DrawScope.cornerTicks(size: Size, color: Color, inset: Float, w: Float) {
    val lx = size.width * 0.18f
    val ly = size.height * 0.18f
    val l = size.width - inset
    val b = size.height - inset
    val s = Stroke(width = w)
    // ust-sol / ust-sag / alt-sol / alt-sag — her kosede yatay + dikey bir cizgi
    listOf(
        Offset(inset, inset) to Offset(inset + lx, inset),
        Offset(inset, inset) to Offset(inset, inset + ly),
        Offset(l, inset) to Offset(l - lx, inset),
        Offset(l, inset) to Offset(l, inset + ly),
        Offset(inset, b) to Offset(inset + lx, b),
        Offset(inset, b) to Offset(inset, b - ly),
        Offset(l, b) to Offset(l - lx, b),
        Offset(l, b) to Offset(l, b - ly)
    ).forEach { (from, to) -> drawLine(color, from, to, strokeWidth = s.width) }
}

/**
 * Taktik cerceveli yuzey.
 *
 * @param tone durum rengi ve halesi.
 * @param chamfer kose kesimi.
 * @param showTicks kose isaret cizgileri cizilsin mi (kucuk yuzeylerde
 *   kalabaliklastirdigi icin kapatilabilir).
 */
@Composable
fun TacticalFrame(
    modifier: Modifier = Modifier,
    tone: FrameTone = FrameTone.ACTIVE,
    chamfer: Dp = 10.dp,
    showTicks: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val strokeColor = tone.stroke()
    val glowColor = tone.glow()
    val fill = tone.fill()

    Box(
        modifier = modifier.drawBehind {
            val c = chamfer.toPx()
            val path = chamferPath(size, c)

            // HALE — govdenin ALTINA, disa dogru. Siradaki kartin ekranda
            // aranmadan bulunmasini saglayan tek sinyal bu.
            if (glowColor != null) {
                val g = 3.dp.toPx()
                drawPath(
                    path = chamferPath(size, c),
                    color = glowColor,
                    style = Stroke(width = g * 2f)
                )
            }

            drawPath(path = path, brush = fill)
            drawPath(path = path, color = strokeColor, style = Stroke(width = 1.5.dp.toPx()))

            // IC KONTUR — 3 dp iceride, daha koyu. Cift kontur, tek konturun
            // veremedigi "cukur govde" hissini veriyor.
            val inset = 3.dp.toPx()
            val innerSize = Size(size.width - inset * 2, size.height - inset * 2)
            if (innerSize.width > 0 && innerSize.height > 0) {
                translate(inset, inset) {
                    drawPath(
                        path = chamferPath(innerSize, (c - inset).coerceAtLeast(0f)),
                        color = strokeColor.copy(alpha = 0.35f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            if (showTicks) {
                cornerTicks(
                    size = size,
                    color = strokeColor.copy(alpha = 0.85f),
                    inset = 1.5.dp.toPx(),
                    w = 1.5.dp.toPx()
                )
            }
        },
        content = content
    )
}

/** `translate` icin kucuk yardimci — DrawScope'un kendi surumu inline degil. */
private inline fun DrawScope.translate(dx: Float, dy: Float, block: DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.canvas.translate(dx, dy)
    block()
    drawContext.canvas.restore()
}
