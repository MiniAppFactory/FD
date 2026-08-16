package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GameCanvas(
    gameEngine: GameEngine,
    /**
     * Faz 2: her karede artan sayac (bkz. GameScreen). Motorun entity listeleri
     * Compose snapshot state DEGIL, o yuzden cizimi gecersiz kilan tek sey bu.
     * Sadece asagidaki draw lambda'sinin ICINDE okunur -> yalnizca cizim fazi
     * yenilenir, bu Composable recompose olmaz.
     */
    frameTick: IntState,
    /** Ust HUD seridinin px yuksekligi — oynanis alani bunun altinda baslar. */
    topInsetPx: Float,
    modifier: Modifier = Modifier
) {
    // Faz 3: tum sprite'lar BIR KEZ decode edilir. Kare dongusunde decode yok.
    val sprites = rememberGameSprites()

    val selectedBuildSpot by gameEngine.selectedBuildSpot.collectAsState()
    val selectedTower by gameEngine.selectedTower.collectAsState()
    // Build cubugunda basili tutulan kart — birakma onizlemesi (bkz. asagi).
    val previewTowerType by gameEngine.previewTowerType.collectAsState()

    // Faz 4b: harita arkaplani bolume gore. `currentLevelId` gozlemlenebilir
    // oldugu icin bolum degisince bu Composable recompose olur ve `activeMapId`
    // o an guncel olur (motor loadLevel'da ikisini birlikte set ediyor).
    // Harita bitmap'i 8.3 MB; bellekte TEK harita tutulur.
    val levelId by gameEngine.currentLevelId.collectAsState()
    val mapBitmap = rememberMapBitmap(remember(levelId) { gameEngine.activeMapId })

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    // Faz 2: sarsinti degeri AKISTAN ANLIK okunur. Onceden
                    // collectAsState() ile alinan deger pointerInput(Unit)
                    // lambda'sinda yakalaniyordu; anahtar Unit oldugu icin blok
                    // hic yeniden kurulmuyor ve dokunma telafisi ilk (sifir)
                    // degerde donup kaliyordu.
                    val shake = gameEngine.screenShake.value
                    val adjustedTap = Offset(
                        tapOffset.x - shake.x,
                        tapOffset.y - shake.y
                    )
                    handleCanvasTap(gameEngine, adjustedTap)
                }
            }
    ) {
        // >>> KARE INVALIDATION: bu okuma silinirse savas alani yeniden donar. <<<
        @Suppress("UNUSED_EXPRESSION") frameTick.intValue

        gameEngine.updateMapDimensions(size.width, size.height, topInsetPx)

        val s = gameEngine.renderScale
        val screenShake = gameEngine.screenShake.value

        // Letterbox zemini — sarsinti transformunun DISINDA, sabit kalir.
        drawRect(color = Color(GameConfig.LETTERBOX_COLOR))

        withTransform({
            translate(screenShake.x, screenShake.y)
        }) {
            // 1. Harita: FIT + letterbox. Sarsinti kenarda koyu serit acmasin
            //    diye bitmap oynanis dikdortgeninden birkac px tasar cizilir;
            //    oynanis koordinatlari tasmayan gercek dikdortgene bagli.
            val over = GameConfig.SHAKE_OVERSCAN_REF_PX * s
            drawSprite(
                image = mapBitmap,
                left = gameEngine.fieldLeftPx - over,
                top = gameEngine.fieldTopPx - over,
                width = gameEngine.fieldWidthPx + over * 2f,
                height = gameEngine.fieldHeightPx + over * 2f
            )

            // 2. Yol/spawn/us CIZIMI YOK — yol artik haritada boyali.
            if (GameConfig.DEBUG_DRAW_PATH) drawDebugPath(gameEngine)

            // 3. Bos build pad isaretleri (fx_build_pad)
            drawBuildSpots(gameEngine, sprites, selectedBuildSpot, s)

            // 4. Menzil gostergesi kulenin ALTINDA kalir ki sprite'i bogmasin.
            //    Menzil REFERANS tuvalde tanimli -> cizimde s ile olceklenir.
            selectedTower?.let { tower ->
                drawRangeSprite(sprites.rangeGreen, tower.posX, tower.posY, tower.rangePx(s), 0.85f)
            }
            selectedBuildSpot?.let { spot ->
                // BIRAKMA ONIZLEMESI: kart basili tutuluyorsa O KULENIN gercek
                // menzili, degilse notr halka. Sabit halka artik yanlis bilgi
                // olurdu (menziller 150..270 ref-px arasinda degisiyor) ve
                // Frost Field'in tum satis noktasi genis kapsama alani.
                // Meta menzil yukseltmesi DAHIL — motor tek karar noktasi.
                val previewRangeRef = gameEngine.previewRangeRef(previewTowerType)
                drawRangeSprite(
                    sprites.rangeBlue, spot.normX, spot.normY,
                    previewRangeRef * s,
                    if (previewTowerType != null) 0.9f else 0.75f
                )
            }

            // 5. Kuleler
            gameEngine.towers.forEach { tower ->
                drawTower(tower, sprites, isSelected = (selectedTower?.id == tower.id), s = s)
            }

            // 6. Dusmanlar
            gameEngine.enemies.forEach { enemy ->
                drawEnemy(enemy, sprites, s)
            }

            // 7. Mermiler
            gameEngine.projectiles.forEach { proj ->
                drawProjectile(proj, sprites, s)
            }

            // 8. Efektler + yuzen metin
            gameEngine.visualEffects.forEach { fx ->
                drawVisualEffect(fx, sprites, s)
            }
        }
    }
}

private fun handleCanvasTap(gameEngine: GameEngine, tap: Offset) {
    // Faz 3 BULGU: VICTORY / DEFEAT / PAUSED modallari yariseffaf bir Box; hicbir
    // pointer girdisi TUKETMIYOR. Bu yuzden "BASE OVERRUN" ekrani aciken modalin
    // ustune yapilan dokunus alttaki tuvale gecip kule insa/secme yapiyordu
    // (cihazda dogrulandi: yenilgi ekraninda pad secilip kule kuruldu, 60
    // Tedarik harcandi). Oyun bitmis ya da duraklamisken savas alani girdi
    // KABUL ETMEZ.
    if (!gameEngine.acceptsBattlefieldInput()) return

    // Faz 3: dokunma yaricapi referans tuvalde tanimli -> tablette de telefonda
    // da ayni FIZIKSEL buyuklukte hedef. Ham 32f sabiti kaldirildi.
    val r = GameConfig.TAP_RADIUS_REF_PX * gameEngine.renderScale
    val rSq = r * r

    // 1. Check if tapped an existing placed tower
    val tappedTower = gameEngine.towers.minByOrNull { tower ->
        val dx = tower.posX - tap.x
        val dy = tower.posY - tap.y
        dx * dx + dy * dy
    }?.takeIf { tower ->
        val dx = tower.posX - tap.x
        val dy = tower.posY - tap.y
        (dx * dx + dy * dy) <= rSq
    }

    if (tappedTower != null) {
        gameEngine.selectTower(tappedTower)
        return
    }

    // 2. Check if tapped an open build spot
    val tappedSpot = gameEngine.scaledBuildSpots.minByOrNull { spot ->
        val dx = spot.normX - tap.x
        val dy = spot.normY - tap.y
        dx * dx + dy * dy
    }?.takeIf { spot ->
        val dx = spot.normX - tap.x
        val dy = spot.normY - tap.y
        (dx * dx + dy * dy) <= rSq
    }

    if (tappedSpot != null) {
        gameEngine.selectBuildSpot(tappedSpot)
        return
    }

    // Tapped elsewhere on battlefield
    gameEngine.deselectAll()
}

// ---------------------------------------------------------------------------
// SPRITE CIZIM YARDIMCILARI
//
// drawImage(dstOffset/dstSize) IntOffset/IntSize alir -> alt-piksel konumda
// titreme yapar. Bu yuzden dogal boyutta cizip transform ile olcekliyoruz;
// boylece konum ve olcek FLOAT hassasiyetinde kalir.
// ---------------------------------------------------------------------------

private fun DrawScope.drawSprite(
    image: ImageBitmap,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null
) {
    if (image.width == 0 || image.height == 0) return
    withTransform({
        translate(left, top)
        scale(width / image.width, height / image.height, pivot = Offset.Zero)
    }) {
        drawImage(image, topLeft = Offset.Zero, alpha = alpha, colorFilter = colorFilter)
    }
}

/**
 * Sprite'i (cx, cy) noktasina, verilen genislikte, en-boy oranini KORUYARAK cizer.
 *
 * @param pivotYFrac sprite yuksekliginin hangi orani (cx, cy) uzerine oturur.
 *   Tuvalin merkezi degil: turret'in donus ekseni / kulenin oturma noktasi.
 * @param rotationDeg ekran acisi (0 = sag). Sprite'in nominal yonu cagiran
 *   tarafta GameConfig sabitiyle cikarilir; buraya NIHAI aci gelir.
 */
private fun DrawScope.drawSpriteAt(
    image: ImageBitmap,
    cx: Float,
    cy: Float,
    width: Float,
    rotationDeg: Float = 0f,
    pivotYFrac: Float = 0.5f,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null
) {
    if (image.width == 0) return
    val h = width * image.height / image.width
    val left = cx - width / 2f
    val top = cy - h * pivotYFrac
    if (rotationDeg == 0f) {
        drawSprite(image, left, top, width, h, alpha, colorFilter)
    } else {
        rotate(degrees = rotationDeg, pivot = Offset(cx, cy)) {
            drawSprite(image, left, top, width, h, alpha, colorFilter)
        }
    }
}

/** Menzil halkasi: cap = 2 * menzil, DAIRE olarak (halka sprite'i hafif
 *  dikdortgen; oynanis okunabilirligi icin gercek menzile birebir oturmasi
 *  sprite'in kendi en-boy oranindan onemli). */
private fun DrawScope.drawRangeSprite(
    image: ImageBitmap,
    cx: Float,
    cy: Float,
    range: Float,
    alpha: Float
) {
    val d = range * 2f
    drawSprite(image, cx - range, cy - range, d, d, alpha)
}

// ---------------------------------------------------------------------------

private fun DrawScope.drawDebugPath(gameEngine: GameEngine) {
    val pts = gameEngine.scaledWaypoints
    if (pts.size < 2) return
    val path = Path().apply {
        moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }
    drawPath(path, Color(0x99FF00FF), style = Stroke(width = 3f))
    pts.forEach { drawCircle(Color(0xCC00FFFF), 5f, Offset(it.x, it.y)) }
    gameEngine.scaledBuildSpots.forEach {
        drawCircle(Color(0xCCFFFF00), 6f, Offset(it.normX, it.normY), style = Stroke(width = 2f))
    }
}

private fun DrawScope.drawBuildSpots(
    gameEngine: GameEngine,
    sprites: GameSprites,
    selectedSpot: BuildSpot?,
    s: Float
) {
    val w = GameConfig.FX_BUILD_PAD_REF_PX * s
    gameEngine.scaledBuildSpots.forEach { spot ->
        val isOccupied = gameEngine.towers.any { it.buildSpotId == spot.id }
        if (isOccupied) return@forEach
        val isSelected = selectedSpot?.id == spot.id
        drawSpriteAt(
            image = sprites.buildPad,
            cx = spot.normX,
            cy = spot.normY,
            width = if (isSelected) w * 1.08f else w,
            alpha = if (isSelected) GameConfig.BUILD_PAD_SELECTED_ALPHA
            else GameConfig.BUILD_PAD_IDLE_ALPHA
        )
    }
}

private fun DrawScope.drawTower(
    tower: TowerEntity,
    sprites: GameSprites,
    isSelected: Boolean,
    s: Float
) {
    val spec = GameConfig.TOWER_SPRITES[tower.type] ?: return
    val image = sprites.towers[tower.type] ?: return

    // Secim halkasi kulenin ALTINDA
    if (isSelected) {
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = spec.widthRefPx * s * 0.62f,
            center = Offset(tower.posX, tower.posY),
            style = Stroke(width = 3f * s.coerceAtLeast(0.5f))
        )
    }

    // SPRITE YONU: sprite'in nominal bakis yonu GameConfig'te, renderer'da degil.
    val aimDeg = Math.toDegrees(tower.currentAngleRad.toDouble()).toFloat()
    val rotation = if (spec.rotates) aimDeg - spec.baseAngleDeg else 0f

    // Geri tepme: donen kulede namlu ekseninde, sabit kulede hafif kucultme.
    val recoil = tower.recoilOffsetPx * s
    val cx: Float
    val cy: Float
    if (spec.rotates) {
        cx = tower.posX - cos(tower.currentAngleRad) * recoil
        cy = tower.posY - sin(tower.currentAngleRad) * recoil
    } else {
        cx = tower.posX
        cy = tower.posY
    }
    val widthPx = spec.widthRefPx * s * if (spec.rotates) 1f else (1f - recoil * 0.004f)

    drawSpriteAt(
        image = image,
        cx = cx,
        cy = cy,
        width = widthPx,
        rotationDeg = rotation,
        pivotYFrac = spec.pivotYFrac
    )

    // Kademe gostergesi (bilgi, sprite degil) — kulenin altinda kucuk cubuklar
    val dotR = 3.5f * s
    val dotY = tower.posY + spec.widthRefPx * s * 0.42f
    if (tower.level >= 2) {
        drawCircle(Color(0xFFFFD700), dotR, Offset(tower.posX - dotR * 2.2f, dotY))
        drawCircle(Color(0xFFFFD700), dotR, Offset(tower.posX + dotR * 2.2f, dotY))
    } else {
        drawCircle(Color(0xCCB0BEC5), dotR, Offset(tower.posX, dotY))
    }
}

private fun DrawScope.drawEnemy(enemy: EnemyEntity, sprites: GameSprites, s: Float) {
    val spec = GameConfig.ENEMY_SPRITES[enemy.type] ?: return
    val image = sprites.enemies[enemy.type] ?: return

    val width = spec.widthRefPx * s
    // Dusman sprite'lari ASAGI bakiyor; atan2 0 = sag -> base aci cikarilir.
    val rotation = Math.toDegrees(enemy.rotationAngleRad.toDouble()).toFloat() -
        GameConfig.ENEMY_SPRITE_BASE_ANGLE_DEG

    drawSpriteAt(image, enemy.posX, enemy.posY, width, rotation, spec.pivotYFrac)

    // Isabet parlamasi: ayni silueti beyaz olarak ustune bindir (tint yerine
    // SrcIn -> sprite'in sekli korunur, sadece rengi beyazlar).
    if (enemy.hitFlashTimerSeconds > 0f) {
        val a = (enemy.hitFlashTimerSeconds / 0.12f).coerceIn(0f, 1f) * 0.75f
        drawSpriteAt(
            image, enemy.posX, enemy.posY, width, rotation, spec.pivotYFrac,
            alpha = a,
            colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn)
        )
    }

    // Yavaslama aurasi
    if (enemy.activeSlow != null) {
        drawCircle(
            color = Color(0x8800E5FF),
            radius = width * 0.62f,
            center = Offset(enemy.posX, enemy.posY),
            style = Stroke(width = 2.5f * s.coerceAtLeast(0.5f))
        )
    }

    // Can bari (BILGI — sprite'a cevrilmez)
    val hpRatio = (enemy.hp / enemy.maxHp).coerceIn(0f, 1f)
    val barWidth = width * 0.95f
    val barHeight = 5f * s.coerceAtLeast(0.6f)
    val barTopLeft = Offset(enemy.posX - barWidth / 2f, enemy.posY - width * 0.72f)

    drawRect(color = Color(0xBB000000), topLeft = barTopLeft, size = Size(barWidth, barHeight))
    val hpColor = when {
        hpRatio > 0.6f -> Color(0xFF4CAF50)
        hpRatio > 0.3f -> Color(0xFFFFEB3B)
        else -> Color(0xFFF44336)
    }
    drawRect(color = hpColor, topLeft = barTopLeft, size = Size(barWidth * hpRatio, barHeight))
}

private fun DrawScope.drawProjectile(proj: ProjectileEntity, sprites: GameSprites, s: Float) {
    val headingDeg = Math.toDegrees(
        atan2(proj.targetY - proj.startY, proj.targetX - proj.startX).toDouble()
    ).toFloat() - GameConfig.PROJECTILE_SPRITE_BASE_ANGLE_DEG

    val (image, refWidth) = when (proj.type) {
        ProjectileType.BULLET -> sprites.tracer to GameConfig.FX_TRACER_REF_PX
        ProjectileType.CANNON_SHELL -> sprites.cannonShell to GameConfig.FX_CANNON_SHELL_REF_PX
        // ANTI_ARMOR kulesi missile_launcher sprite'i kullaniyor -> mermisi de
        // fuze. Sprite ucus yonunde doner (PROJECTILE_SPRITE_BASE_ANGLE_DEG = 0,
        // yani nominal olarak saga bakiyor).
        ProjectileType.MISSILE -> sprites.missile to GameConfig.FX_MISSILE_REF_PX
        ProjectileType.FROST_PULSE -> sprites.hitSpark to GameConfig.FX_HIT_SPARK_REF_PX
    }

    // Fuze YOL ALIR ve bunun gorunmesi lazim: kalkista biraz kucuk baslar,
    // hizlanirken buyur (easeOutCubic). Isin aninda variyordu, fuzenin havada
    // gecirdigi sure oyuncunun okumasi gereken bir OYNANIS bilgisi.
    val widthPx = if (proj.type == ProjectileType.MISSILE) {
        val p = proj.progress.coerceIn(0f, 1f)
        refWidth * s * (0.72f + 0.28f * (1f - (1f - p) * (1f - p) * (1f - p)))
    } else {
        refWidth * s
    }

    drawSpriteAt(image, proj.posX, proj.posY, widthPx, headingDeg)
}

private fun DrawScope.drawVisualEffect(fx: VisualEffect, sprites: GameSprites, s: Float) {
    val progress = (fx.ageSeconds / fx.maxAgeSeconds).coerceIn(0f, 1f)
    // easeOutCubic: efektler hizli acilir, yavas soner. Lineer kullanilmaz.
    val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
    val fade = (1f - progress).coerceIn(0f, 1f)

    when (fx.type) {
        EffectType.MUZZLE_FLASH -> {
            val deg = Math.toDegrees(fx.angleRad.toDouble()).toFloat() -
                GameConfig.PROJECTILE_SPRITE_BASE_ANGLE_DEG
            drawSpriteAt(
                sprites.muzzleFlash, fx.posX, fx.posY,
                GameConfig.FX_MUZZLE_FLASH_REF_PX * s * fx.scale * (0.75f + eased * 0.45f),
                rotationDeg = deg, alpha = fade
            )
        }
        EffectType.CANNON_EXPLOSION -> {
            // radiusPx doluysa patlama GERCEK splash alanini gosterir; 0 ise
            // (yukseltme suslemesi) nominal sprite boyutu kullanilir.
            val w = if (fx.radiusPx > 0f) {
                fx.radiusPx * 2f * (0.72f + eased * 0.45f)
            } else {
                GameConfig.FX_LARGE_EXPLOSION_REF_PX * s * fx.scale * (0.5f + eased * 0.6f)
            }
            drawSpriteAt(sprites.largeExplosion, fx.posX, fx.posY, w, alpha = fade)
        }
        EffectType.MISSILE_IMPACT -> {
            // Fuze carpmasi: buyuk patlama sprite'i. Gorsel fireball, hasarli
            // alandan (40 ref-px) kasitli olarak BUYUK — fuze oyle gorunur.
            drawSpriteAt(
                sprites.largeExplosion, fx.posX, fx.posY,
                GameConfig.FX_LARGE_EXPLOSION_REF_PX * s * fx.scale * (0.45f + eased * 0.7f),
                alpha = fade
            )
        }
        EffectType.FROST_PULSE_RING -> {
            // Cryo darbesinin GERCEK sogutma alani. Halka disa dogru genisler
            // ve sonerken tam alani doldurur -> oyuncu neyi sogutacagini gorur.
            val d = fx.radiusPx * 2f * (0.45f + eased * 0.55f)
            drawSprite(
                sprites.rangeBlue, fx.posX - d / 2f, fx.posY - d / 2f, d, d,
                alpha = fade * 0.9f
            )
        }
        EffectType.HIT_SPARK -> {
            drawSpriteAt(
                sprites.hitSpark, fx.posX, fx.posY,
                GameConfig.FX_HIT_SPARK_REF_PX * s * fx.scale * (0.6f + eased * 0.6f),
                rotationDeg = fx.angleRad * 57.29578f, alpha = fade
            )
        }
        EffectType.SMOKE_PUFF -> {
            drawSpriteAt(
                sprites.smokePuff, fx.posX, fx.posY - eased * 14f * s,
                GameConfig.FX_SMOKE_PUFF_REF_PX * s * fx.scale * (0.55f + eased * 0.85f),
                alpha = fade * 0.8f
            )
        }
        EffectType.FROST_WAVE -> {
            // Genisleyen halka — enerji/yukseltme geri bildirimi
            val d = GameConfig.FX_FROST_RING_REF_PX * s * fx.scale * (0.35f + eased * 1.1f)
            drawSprite(
                sprites.rangeBlue, fx.posX - d / 2f, fx.posY - d / 2f, d, d, alpha = fade
            )
        }
        EffectType.ENEMY_DEATH -> {
            drawSpriteAt(
                sprites.smallExplosion, fx.posX, fx.posY,
                GameConfig.FX_SMALL_EXPLOSION_REF_PX * s * fx.scale * (0.45f + eased * 0.75f),
                alpha = fade
            )
            drawSpriteAt(
                sprites.smokePuff, fx.posX, fx.posY - eased * 10f * s,
                GameConfig.FX_SMOKE_PUFF_REF_PX * s * fx.scale * (0.4f + eased * 0.9f),
                alpha = fade * 0.55f
            )
        }
        EffectType.COIN_POPUP -> drawFloatingText(fx, progress, s, 255, 215, 0, 32f, 40f)
        EffectType.DAMAGE_TEXT -> drawFloatingText(fx, progress, s, 244, 67, 54, 30f, 30f)
    }
}

private fun DrawScope.drawFloatingText(
    fx: VisualEffect,
    progress: Float,
    s: Float,
    r: Int,
    g: Int,
    b: Int,
    sizeRefPx: Float,
    riseRefPx: Float
) {
    val txt = fx.text ?: return
    val yOffset = fx.posY - progress * riseRefPx * s
    val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(alpha, r, g, b)
        textSize = sizeRefPx * s.coerceAtLeast(0.5f)
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(alpha, 0, 0, 0))
    }
    drawContext.canvas.nativeCanvas.drawText(txt, fx.posX, yOffset, paint)
}
