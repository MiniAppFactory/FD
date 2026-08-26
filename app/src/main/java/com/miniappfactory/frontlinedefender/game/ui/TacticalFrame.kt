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
 * Hedef tasarimda kartlar DOLU METAL PANELLER: kosesi kesilmis govde, KALIN
 * bevel'li kenar (ustten isik alan, alta dogru kararan), koselerde parlak
 * isaret cizgileri ve siradaki ogede altin hale.
 *
 * ## ⚠ ILK SURUM YETERSIZDI — kullanici reddetti (2026-08-26)
 *
 * Ilk cizim 1,5 dp'lik INCE cift konturdu ve kart "tel cerceve" gibi
 * gorunuyordu; kullanici "hedef ile alakasi yok" dedi ve hakliydi. Hedefteki
 * panel hissini veren uc sey eksikti:
 *  1. kenar KALIN (3-4 dp) ve DIKEY GRADYANLI — ustten isik aliyor,
 *  2. govde OPAK ve zeminden net ayrisiyor,
 *  3. icerik kenara kadar gidiyor (kart ici ayri bir kutu gibi degil).
 * Bu surum ucunu de duzeltir.
 *
 * ## Neden GORSEL DOSYA degil, CIZIM
 *
 * PNG olarak eklemek dort boyut x iki durum = sekiz dosya demekti ve her
 * boyut degisiminde yeniden uretim gerekirdi. Cizim her olcude keskin, APK'ya
 * sifir bayt, durum (kilitli/tamamlanmis/siradaki) kodda yasiyor.
 */
enum class FrameTone {
    /** Kilitli / ileride — sonuk, halesiz. */
    MUTED,

    /** Acik ama siradaki degil — yesil kenar. */
    ACTIVE,

    /** Tamamlanmis — parlak yesil kenar. */
    CLEARED,

    /** SIRADAKI — altin kenar + hale. Ekranda tek oge bunu tasir. */
    NEXT
}

/** Kenar gradyani: ustte ISIK, altta GOLGE — bevel hissinin kaynagi. */
private fun FrameTone.borderBrush(): Brush = when (this) {
    FrameTone.NEXT -> Brush.verticalGradient(
        listOf(Color(0xFFF1C95D), Color(0xFFD8A52A), Color(0xFF7A5B12))
    )
    FrameTone.CLEARED -> Brush.verticalGradient(
        listOf(Color(0xFFB4D284), Color(0xFF7FA24E), Color(0xFF3A4C22))
    )
    FrameTone.ACTIVE -> Brush.verticalGradient(
        listOf(Color(0xFF8AA061), Color(0xFF5C7440), Color(0xFF2C3A1E))
    )
    FrameTone.MUTED -> Brush.verticalGradient(
        listOf(Color(0xFF5A6350), Color(0xFF3E4636), Color(0xFF23281E))
    )
}

private fun FrameTone.tickColor(): Color = when (this) {
    FrameTone.NEXT -> Color(0xFFF1C95D)
    FrameTone.CLEARED -> Color(0xFFA8CC74)
    FrameTone.ACTIVE -> Color(0xFF7E9656)
    FrameTone.MUTED -> Color(0xFF525C46)
}

/** Govde OPAK: panel zeminden ayrismali, arkasi okunmamali. */
private fun FrameTone.fill(): Brush = when (this) {
    FrameTone.MUTED -> Brush.verticalGradient(
        listOf(Color(0xFF20241B), Color(0xFF15180F))
    )
    FrameTone.CLEARED -> Brush.verticalGradient(
        listOf(Color(0xFF2C3A1D), Color(0xFF1C2611))
    )
    FrameTone.NEXT -> Brush.verticalGradient(
        listOf(Color(0xFF33321B), Color(0xFF201F10))
    )
    FrameTone.ACTIVE -> Brush.verticalGradient(
        listOf(Color(0xFF262E1D), Color(0xFF181E11))
    )
}

private fun FrameTone.glow(): Color? = when (this) {
    FrameTone.NEXT -> Color(0xFFF1C95D)
    else -> null
}

/** Kosesi kesilmis (sekizgen) govde yolu. */
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
 * Kose isaret cizgileri — hedefteki "olculmus/teknik" his. Kenarin ic
 * hizasinda, kesim koselerini atlayarak cizilir.
 */
private fun DrawScope.cornerTicks(size: Size, color: Color, inset: Float, w: Float, chamfer: Float) {
    val lx = size.width * 0.16f
    val ly = size.height * 0.16f
    val l = size.width - inset
    val b = size.height - inset
    val c = chamfer
    listOf(
        Offset(inset + c, inset) to Offset(inset + c + lx, inset),
        Offset(inset, inset + c) to Offset(inset, inset + c + ly),
        Offset(l - c, inset) to Offset(l - c - lx, inset),
        Offset(l, inset + c) to Offset(l, inset + c + ly),
        Offset(inset + c, b) to Offset(inset + c + lx, b),
        Offset(inset, b - c) to Offset(inset, b - c - ly),
        Offset(l - c, b) to Offset(l - c - lx, b),
        Offset(l, b - c) to Offset(l, b - c - ly)
    ).forEach { (from, to) -> drawLine(color, from, to, strokeWidth = w) }
}

/**
 * Taktik cerceveli DOLU panel.
 *
 * Icerik, kenarin hemen icinden baslar ([content] kendi dolgusunu koyar);
 * cerceve bir "kutu icinde kutu" degil, panelin kendisidir.
 */
@Composable
fun TacticalFrame(
    modifier: Modifier = Modifier,
    tone: FrameTone = FrameTone.ACTIVE,
    chamfer: Dp = 10.dp,
    showTicks: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val borderBrush = tone.borderBrush()
    val fill = tone.fill()
    val glowColor = tone.glow()
    val tick = tone.tickColor()

    Box(
        modifier = modifier.drawBehind {
            val c = chamfer.toPx()
            val path = chamferPath(size, c)
            val bw = 3.dp.toPx()

            // HALE — yalnizca SIRADAKI. Blur yok; disari dogru zayiflayan uc
            // kademeli stroke ayni isi goruyor ve her cihazda ayni gorunuyor.
            if (glowColor != null) {
                listOf(0.30f to 7f, 0.18f to 12f, 0.08f to 18f).forEach { (a, wd) ->
                    drawPath(
                        path = path,
                        color = glowColor.copy(alpha = a),
                        style = Stroke(width = wd.dp.toPx() / 3f)
                    )
                }
            }

            // GOVDE — opak.
            drawPath(path = path, brush = fill)

            // KALIN BEVEL KENAR — ustten isikli dikey gradyan. Stroke yolun
            // uzerine ortalanir; disa tasan yarisi haleyle, ice tasan yarisi
            // govdeyle birlesir ve kenar "metal cita" gibi okunur.
            drawPath(path = path, brush = borderBrush, style = Stroke(width = bw))

            // IC GOLGE KONTURU — kenarin hemen icinde 1 dp koyu cizgi; bevel
            // ile govde arasindaki derinlik sicramasi buradan geliyor.
            val inset = bw
            val innerSize = Size(size.width - inset * 2, size.height - inset * 2)
            if (innerSize.width > 0 && innerSize.height > 0) {
                withTranslate(inset, inset) {
                    drawPath(
                        path = chamferPath(innerSize, (c - inset).coerceAtLeast(0f)),
                        color = Color(0x66000000),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            if (showTicks) {
                cornerTicks(
                    size = size,
                    color = tick,
                    inset = bw / 2f,
                    w = 2.dp.toPx(),
                    chamfer = c
                )
            }
        },
        content = content
    )
}

private inline fun DrawScope.withTranslate(dx: Float, dy: Float, block: DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.canvas.translate(dx, dy)
    block()
    drawContext.canvas.restore()
}
