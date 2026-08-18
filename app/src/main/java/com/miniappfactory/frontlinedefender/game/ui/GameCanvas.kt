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
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Dusmanin bunker agzindan "cikma" ve us rampasinda "girme" gecisinin
 * uzunlugu, REFERANS tuval px'i (1920x1080). Rota uclari artik ekran ICINDE
 * (bkz. LevelGeometry) — gecis olmadan dusman kapinin onunde POP ederek
 * belirir/kaybolurdu.
 *
 * GameConfig'e degil buraya konuldu: bu sayi bir DENGE degeri degil, saf
 * cizim sabitidir; simulasyona hic girmez.
 */
private const val ENEMY_GATE_FADE_REF_PX = 60f

/**
 * Faz 14 - ZINCIR patlamasinin yazi boyu, REFERANS tuval px.
 * Coin yazisindan (32) belirgin sekilde buyuk: kademe atlama bir OLAY.
 */
private const val COMBO_BURST_TEXT_REF_PX = 46f

/**
 * Faz 14 - zincir kademesinin RENGI: soguk altindan sicak kirmiziya.
 *
 * "Artan renk sicakligi" combo geri bildiriminin uc kanalindan biri (digerleri
 * artan olcek ve ses). Dizi `IntArray` cunku her karede okunuyor ve `Color`
 * listesi kutulama uretirdi; `Color(Long)` cagrisi deger sinifi oldugu icin
 * tahsissizdir.
 */
private val COMBO_TIER_ARGB = longArrayOf(
    0xFFFFD54F, // kademe 1 - altin
    0xFFFFA726, // kademe 2 - turuncu
    0xFFFF7043, // kademe 3 - derin turuncu
    // Kademe 4 KIZIL DEGIL, AK-SICAK. Isinan metal gibi (kirmizi -> turuncu ->
    // sari -> ak) en sicak ucta beyaz durur. Saf kirmizi kullanilsaydi "+4g x18"
    // yazisi hasar/can kaybi yazisiyla (DAMAGE_TEXT, 244/67/54) ayni renk
    // ailesine duserdi: en iyi anin en kotu anla ayni renkte olmasi.
    0xFFFFF3E0  // kademe 4 - ak sicak
)

private fun comboTierColor(tier: Int): Color {
    if (tier <= 0) return Color(0xFFFFD54F)
    return Color(COMBO_TIER_ARGB[(tier - 1).coerceAtMost(COMBO_TIER_ARGB.lastIndex)])
}

/**
 * Kademe gostergesi renkleri (harita uzerinde okunabilirlik).
 *
 * Kademe 2 ile 3 eskiden AYNI ciziliyordu (`level >= 2`): panel "Kd.3/3" derken
 * oyuncu hangi pad'inin derinlestigini haritadan okuyamiyordu. 7-11 kuleli
 * Act II bolumlerinde bu gercek bir okunabilirlik kaybi.
 */
private val TIER_PIP_COLORS = longArrayOf(
    0xCCB0BEC5, // kademe 1 - notr gri
    0xFFFFD700, // kademe 2 - altin
    0xFF4FC3F7  // kademe 3 - parlak camgobegi
)

/**
 * Yuzen yazi icin PAYLASILAN Paint.
 *
 * FAZ 14 KARE BUTCESI DUZELTMESI: `drawFloatingText` her cagrida yeni bir
 * `android.graphics.Paint` ayiriyordu. Bir dalgada ayni anda 8-10 yuzen yazi
 * yasayabildigi icin bu saniyede ~600 kisa omurlu nesne demekti, yani GC
 * duraklamasi ve gorunur jank. Cizim tek bir is parcaciginda (render thread)
 * kostugu icin paylasilan tek ornek guvenli.
 */
private val floatingTextPaint = android.graphics.Paint().apply {
    isFakeBoldText = true
    isAntiAlias = true
    textAlign = android.graphics.Paint.Align.CENTER
}

/**
 * ISABET PARLAMASI RENK FILTRESI — PAYLASILAN, sabit.
 *
 * KARE BUTCESI DUZELTMESI (olculdu: `WorstCaseFrameBudgetTest`).
 * `drawEnemy` her cagrida `ColorFilter.tint(Color.White, BlendMode.SrcIn)`
 * uretiyordu. Deger SABIT — ne renk ne harman modu degisiyor; degisen tek sey
 * `alpha` ve o zaten `drawImage`e AYRI parametre olarak gidiyor.
 *
 * Neden `Paint` bugu ile ayni siniftan: uretilen nesnenin **native esi** var
 * (android.graphics color filter). Agir bir dalgada ayni anda 20 dusman
 * parlayabildigi icin bu, saniyede 1200 kisa omurlu native-esli nesne
 * demekti; yani dogrudan GC duraklamasi ve gorunur jank.
 *
 * Cizim tek is parcaciginda (kare basina, ana thread) kostugu icin
 * paylasilan tek ornek guvenlidir — `floatingTextPaint` ile ayni gerekce.
 */
private val enemyHitFlashFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn)

/** easeOutBack - hafif overshoot. Vurgu anlarinda kullanilir (lineer DEGIL). */
private fun easeOutBack(t: Float): Float {
    val x = t.coerceIn(0f, 1f) - 1f
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

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
    // Faz 11 — BIYOM: ayni 11 taban harita, bolume gore 5 farkli renk durumu
    // (docs/BIOME_VARIANTS.md). Harita ID'si HÂLÂ motordan gelir; motor eksik
    // bitmap'te fallback yapabildigi icin `BiomeVariants.baseMapIdFor` cizim
    // yolunda TEK BASINA yetkili degil. Biyom ise tamamen bolum numarasinin
    // fonksiyonu — deterministik, ayni bolum her zaman ayni varyant.
    val mapId = remember(levelId) { gameEngine.activeMapId }
    val biome = remember(levelId) { BiomeVariants.biomeFor(levelId) }
    val mapBitmap = rememberMapBitmap(mapId, biome)

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
            //    Faz 11: biyom donusumu ARKA PLANDA yapiliyor; hazir olana
            //    kadar `mapBitmap` null olur ve YALNIZCA bu katman atlanir.
            //    Pad/kule/dusman/HUD cizimi ve dokunma girdisi kesintiye
            //    ugramaz — bolum PREPARATION fazinda basladigi icin oyuncu
            //    bu kisa boslugu bir gecikme olarak hissetmez.
            val over = GameConfig.SHAKE_OVERSCAN_REF_PX * s
            if (mapBitmap != null) {
                drawSprite(
                    image = mapBitmap,
                    left = gameEngine.fieldLeftPx - over,
                    top = gameEngine.fieldTopPx - over,
                    width = gameEngine.fieldWidthPx + over * 2f,
                    height = gameEngine.fieldHeightPx + over * 2f
                )
            }

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
            //    Rota uclari kapi agzinda oldugu icin belirme/kaybolma
            //    alfa ile yumusatilir; rotanin son noktasi kare basina
            //    TAHSIS YAPMADAN okunur (mevcut PointF referansi).
            val routes = gameEngine.scaledRoutes
            gameEngine.enemies.forEach { enemy ->
                val end = routes.getOrNull(enemy.routeIndex)?.lastOrNull()
                drawEnemy(enemy, sprites, s, end)
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

    // ------------------------------------------------------------------------
    // KADEME GOSTERGESI (bilgi, sprite degil).
    //
    // Faz 14 duzeltmesi: eski esik `tower.level >= 2` idi, yani kademe 2 ile
    // kademe 3 HARITADA BIREBIR AYNI gorunuyordu. Panel "Kd.3/3" diyor ama
    // oyuncu 7-11 kuleli bir haritada hangi pad'ini derinlestirdigini
    // goremiyordu. Artik centik SAYISI = kademe ve renk de kademeyle degisir
    // (iki kanal: sayim + renk). Kademe 3 ayrica bir taban yayi alir.
    //
    // Ucuncu kademe isareti icin YENI ASSET URETILMEDI - saf Canvas cizimi.
    // ------------------------------------------------------------------------
    val tierIndex = (tower.level - 1).coerceIn(0, TIER_PIP_COLORS.lastIndex)
    val tierColor = Color(TIER_PIP_COLORS[tierIndex])
    val pipCount = tierIndex + 1
    val dotR = 3.5f * s
    val dotY = tower.posY + spec.widthRefPx * s * 0.42f
    val pipSpacing = dotR * 2.6f
    val pipStartX = tower.posX - pipSpacing * (pipCount - 1) / 2f
    for (i in 0 until pipCount) {
        drawCircle(tierColor, dotR, Offset(pipStartX + pipSpacing * i, dotY))
    }

    if (tower.level >= 3) {
        // TABAN YAYI - yalnizca SON kademe. Kasitli olarak yarim yay: tam
        // halka olsaydi secim halkasiyla (0.62 yaricap, altin) karisirdi;
        // kulenin ALTINI saran bir yaka "guclendirilmis platform" okunur.
        val r = spec.widthRefPx * s * 0.50f
        drawArc(
            color = tierColor,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(tower.posX - r, tower.posY - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = 2.6f * s.coerceAtLeast(0.5f))
        )
    }
}

/** Yumusak giris/cikis egrisi (smoothstep) — lineer alfa "kesik" hissettirir. */
private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Kapi gecisi alfasi.
 *
 * - Yolun ilk [ENEMY_GATE_FADE_REF_PX] referans px'inde 0 -> 1: dusman cikis
 *   bunkerinin AGZINDAN cikiyormus gibi belirir.
 * - Son [ENEMY_GATE_FADE_REF_PX] referans px'inde 1 -> 0: us rampasina
 *   varirken ussun ICINE giriyormus gibi soner.
 *
 * YENI ALAN YOK: giris `enemy.distanceTraveledPx` (zaten var, canvas px),
 * cikis ise dusmanin rota son noktasina kalan kus ucusu mesafesi. Son segment
 * pratikte duz oldugu icin bu, kalan yay uzunluguna esittir.
 *
 * [routeEnd] null ise (rota indeksi gecersiz) sonme uygulanmaz — dusman
 * gorunur kalir, yani hata durumunda oyuncu bir seyi kaybetmez.
 */
private fun gateFadeAlpha(enemy: EnemyEntity, routeEnd: PointF?, s: Float): Float {
    val fadePx = ENEMY_GATE_FADE_REF_PX * s
    if (fadePx <= 0f) return 1f
    val fadeIn = smoothstep(enemy.distanceTraveledPx / fadePx)
    if (routeEnd == null) return fadeIn
    val remaining = hypot(routeEnd.x - enemy.posX, routeEnd.y - enemy.posY)
    return min(fadeIn, smoothstep(remaining / fadePx))
}

private fun DrawScope.drawEnemy(
    enemy: EnemyEntity,
    sprites: GameSprites,
    s: Float,
    routeEnd: PointF?
) {
    val spec = GameConfig.ENEMY_SPRITES[enemy.type] ?: return
    val image = sprites.enemies[enemy.type] ?: return

    // Bunkerden cikis / usse giris gecisi. Tam sonmusse hic cizme.
    val gate = gateFadeAlpha(enemy, routeEnd, s)
    if (gate <= 0.01f) return

    val width = spec.widthRefPx * s
    // Dusman sprite'lari ASAGI bakiyor; atan2 0 = sag -> base aci cikarilir.
    val rotation = Math.toDegrees(enemy.rotationAngleRad.toDouble()).toFloat() -
        GameConfig.ENEMY_SPRITE_BASE_ANGLE_DEG

    drawSpriteAt(image, enemy.posX, enemy.posY, width, rotation, spec.pivotYFrac, alpha = gate)

    // Isabet parlamasi: ayni silueti beyaz olarak ustune bindir (tint yerine
    // SrcIn -> sprite'in sekli korunur, sadece rengi beyazlar).
    if (enemy.hitFlashTimerSeconds > 0f) {
        val a = (enemy.hitFlashTimerSeconds / HIT_FLASH_DURATION_SECONDS)
            .coerceIn(0f, 1f) * 0.75f
        drawSpriteAt(
            image, enemy.posX, enemy.posY, width, rotation, spec.pivotYFrac,
            alpha = a * gate,
            colorFilter = enemyHitFlashFilter
        )
    }

    // Yavaslama aurasi
    if (enemy.activeSlow != null) {
        drawCircle(
            color = Color(0x8800E5FF),
            radius = width * 0.62f,
            center = Offset(enemy.posX, enemy.posY),
            alpha = gate,
            style = Stroke(width = 2.5f * s.coerceAtLeast(0.5f))
        )
    }

    // Can bari (BILGI — sprite'a cevrilmez)
    val hpRatio = (enemy.hp / enemy.maxHp).coerceIn(0f, 1f)
    val barWidth = width * 0.95f
    val barHeight = 5f * s.coerceAtLeast(0.6f)
    val barTopLeft = Offset(enemy.posX - barWidth / 2f, enemy.posY - width * 0.72f)

    drawRect(
        color = Color(0xBB000000), topLeft = barTopLeft,
        size = Size(barWidth, barHeight), alpha = gate
    )
    val hpColor = when {
        hpRatio > 0.6f -> Color(0xFF4CAF50)
        hpRatio > 0.3f -> Color(0xFFFFEB3B)
        else -> Color(0xFFF44336)
    }
    drawRect(
        color = hpColor, topLeft = barTopLeft,
        size = Size(barWidth * hpRatio, barHeight), alpha = gate
    )
}

private fun DrawScope.drawProjectile(proj: ProjectileEntity, sprites: GameSprites, s: Float) {
    val headingDeg = Math.toDegrees(
        atan2(proj.targetY - proj.startY, proj.targetX - proj.startX).toDouble()
    ).toFloat() - GameConfig.PROJECTILE_SPRITE_BASE_ANGLE_DEG

    // KARE BUTCESI DUZELTMESI (olculdu: `FramePathAllocationTest`).
    // Burasi `sprite to refWidth` ile bir `Pair` uretiyordu ve `Pair` generic
    // oldugu icin `Float` de KUTULANIYORDU: cagri basina 28 bayt, mermi
    // BASINA her KAREDE. 40 mermilik agir bir dalgada saniyede ~67 KB saf
    // cop. Iki ayri `when` ayni sonucu 0 bayt ile veriyor.
    //
    // ANTI_ARMOR kulesi missile_launcher sprite'i kullaniyor -> mermisi de
    // fuze. Sprite ucus yonunde doner (PROJECTILE_SPRITE_BASE_ANGLE_DEG = 0,
    // yani nominal olarak saga bakiyor).
    val image = when (proj.type) {
        ProjectileType.BULLET -> sprites.tracer
        ProjectileType.CANNON_SHELL -> sprites.cannonShell
        ProjectileType.MISSILE -> sprites.missile
        ProjectileType.FROST_PULSE -> sprites.hitSpark
    }
    val refWidth = when (proj.type) {
        ProjectileType.BULLET -> GameConfig.FX_TRACER_REF_PX
        ProjectileType.CANNON_SHELL -> GameConfig.FX_CANNON_SHELL_REF_PX
        ProjectileType.MISSILE -> GameConfig.FX_MISSILE_REF_PX
        ProjectileType.FROST_PULSE -> GameConfig.FX_HIT_SPARK_REF_PX
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
        EffectType.COIN_POPUP -> {
            // ZINCIR TIRMANMASI - iki gorsel kanal ayni anda:
            //  1) OLCEK: kademe basina %14 buyume,
            //  2) RENK SICAKLIGI: altin -> turuncu -> kizil.
            // Ucuncu kanal (ses) motorda, ayni karede.
            // Yeni bir efekt NESNESI eklenmedi: sayac zaten ekranda olan
            // "+4g" yazisinin icine girdi, yani ekran bogulmadi.
            val c = comboTierColor(fx.tier)
            drawFloatingText(
                fx, progress, s,
                (c.red * 255f).toInt(), (c.green * 255f).toInt(), (c.blue * 255f).toInt(),
                32f * (1f + 0.14f * fx.tier), 40f
            )
        }
        EffectType.DAMAGE_TEXT -> drawFloatingText(fx, progress, s, 244, 67, 54, 30f, 30f)
        EffectType.COMBO_BURST -> {
            // Kademe atlama - dalgada en fazla 4 kez cikar.
            val c = comboTierColor(fx.tier)
            // Genisleyen halka: easeOutCubic ile hizli acilir, yavas soner.
            val ringR = GameConfig.FX_FROST_RING_REF_PX * s *
                (0.22f + eased * 0.68f) * (1f + 0.12f * fx.tier)
            drawCircle(
                color = c,
                radius = ringR,
                center = Offset(fx.posX, fx.posY),
                alpha = fade * 0.8f,
                style = Stroke(width = 2.5f * s.coerceAtLeast(0.5f))
            )
            // Yazi: easeOutBack ile hafif overshoot (vurgu egrisi).
            val txt = fx.text
            if (txt != null) {
                val pop = easeOutBack(progress.coerceAtMost(0.45f) / 0.45f)
                val paint = floatingTextPaint
                val alpha = ((1f - progress) * 255f).toInt().coerceIn(0, 255)
                paint.color = android.graphics.Color.argb(
                    alpha,
                    (c.red * 255f).toInt(), (c.green * 255f).toInt(), (c.blue * 255f).toInt()
                )
                paint.textSize = COMBO_BURST_TEXT_REF_PX * s.coerceAtLeast(0.5f) *
                    (0.55f + 0.45f * pop) * (1f + 0.10f * fx.tier)
                paint.setShadowLayer(4f, 0f, 2f, android.graphics.Color.argb(alpha, 0, 0, 0))
                drawContext.canvas.nativeCanvas.drawText(
                    txt, fx.posX, fx.posY - (18f + eased * 22f) * s, paint
                )
            }
        }
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
    // Paint PAYLASILIR, her karede yeniden ayrilmaz (bkz. floatingTextPaint).
    val paint = floatingTextPaint
    paint.color = android.graphics.Color.argb(alpha, r, g, b)
    paint.textSize = sizeRefPx * s.coerceAtLeast(0.5f)
    paint.setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(alpha, 0, 0, 0))
    drawContext.canvas.nativeCanvas.drawText(txt, fx.posX, yOffset, paint)
}
