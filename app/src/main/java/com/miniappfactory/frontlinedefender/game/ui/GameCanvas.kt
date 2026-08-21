package com.miniappfactory.frontlinedefender.game.ui

import androidx.annotation.VisibleForTesting
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.*
import kotlin.math.atan2
import kotlin.math.ceil
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

// ---------------------------------------------------------------------------
// HAVA TAARRUZU KOSUSU — cizim sabitleri (bkz. EffectType.AIR_STRIKE_RUN).
//
// GameConfig'e KONULMADI: hicbiri denge degeri degil, tamamen cizim sayilari.
// Ayni gerekce ENEMY_GATE_FADE_REF_PX'te de gecerli.
// ---------------------------------------------------------------------------

/** Ucus aracinin (odunc fuze sprite'i) referans genisligi. Mermiden ~2x buyuk. */
private const val AIR_STRIKE_JET_REF_PX = 104f

/**
 * Duman izinin parcacik sayisi. Bunlar EFEKT NESNESI DEGIL — konumlari her
 * karede ucagin gerisinde hesaplanir, listeye hicbir sey eklenmez. Tahsis
 * sifir, efekt butcesinden yer kaplamaz.
 */
private const val AIR_STRIKE_TRAIL_PUFFS = 8

/** Izin ucagin gerisinde kapladigi YOL orani (0..1). */
private const val AIR_STRIKE_TRAIL_SPAN = 0.34f

/** Duman parcaciklarinin referans genisligi. */
private const val AIR_STRIKE_TRAIL_PUFF_REF_PX = 54f

/** Ucus hattinin kalinligi, referans px. */
private const val AIR_STRIKE_PATH_STROKE_REF_PX = 3.5f

/** Hattin ve izin rengi — sicak isaret fisegi turuncusu. */
private val AIR_STRIKE_PATH_COLOR = Color(0xFFFFB74D)

/** Ekran flasi rengi. Beyaz DEGIL: patlama sicakligi (soluk kehribar). */
private val AIR_STRIKE_FLASH_COLOR = Color(0xFFFFE0B2)

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

// ---------------------------------------------------------------------------
// ORTULEN SECIM HAYALETI — renkler (bkz. drawOcclusionCallout).
// Altin aile secim rengiyle ayni (kule secim halkasi 0xFFFFD54F), boylece
// hayalet "secili olan sey" olarak okunur; plaka koyu ve opak, cunku
// haritanin uzerinde kontrast tasimasi gerekiyor.
// ---------------------------------------------------------------------------
private val CALLOUT_PLATE_COLOR = Color(0xE60B0E08)
private val CALLOUT_BORDER_COLOR = Color(0xFFFFD54F)
private val CALLOUT_LINK_COLOR = Color(0xB3FFD54F)

// ---------------------------------------------------------------------------
// "ATES HATTI YOK" ISARETI — renkler (bkz. drawNoLineOfFireMark).
//
// RENK SECIMI BIR KISIT PROBLEMIYDI, tercih degil. Haritada zaten uc anlamli
// renk ailesi var ve dordunculeri onlarin uzerine yazamaz:
//  · soguk mavi/camgobegi (FriendlyPlate 187-205 derece) = "bu benim",
//  · haki ton bandi (35,8-60,2 derece) = "bu dusman",
//  · altin (0xFFFFD54F, ~45 derece) = "bu secili" / kutlama.
// Kehribar-turuncu uyari (0xFFFFB74D) hem haki bandinin hem altinin icine
// duser; kirmizi ise hasar/can kaybi rengiyle (0xFFF44336) ayni aile ve bu bir
// KAYIP degil, bir BILGI. Geriye tek dogru cevap kaliyor: doygunlugu ~0 olan
// notr gri. Hicbir aileye ait olmadigi icin hicbir anlami bozmaz.
//
// Anlami tasiyan asil kanal ZATEN SEKIL (halka + capraz cizgi); renk yalnizca
// okunabilirlik saglar.
// ---------------------------------------------------------------------------
private val NO_LINE_OF_FIRE_MARK_COLOR = Color(0xFFE8EAE6)
private val NO_LINE_OF_FIRE_SHADE_COLOR = Color(0xB30B0E08)

/** Isaretin cizgi kalinligi, referans px. Pad konturundan kalin — geri planda kalmamali. */
private const val NO_LINE_OF_FIRE_STROKE_REF_PX = 5f

// ---------------------------------------------------------------------------
// DOST TABAN PLAKASI  (VISUAL_AUDIT P0-1)
//
// OLCUM: kule sprite'larinin renkli piksel ton ortalamalari 37,8 / 50,6 / 57,4
// derece; dusman ton bandi 35,8-60,2 derece. Yani DORT KULENIN UCU dusman ton
// bandinin TAM ICINDE. Aciklik da ayirmiyor (kule 0,33-0,43 / dusman
// 0,29-0,43). Bir TD oyununda oyuncunun ekrandan okumasi gereken en temel sey
// "hangisi benim" ve su an iki taraf da ayni haki askeri arac.
//
// COZUM: kule sprite'ini yeniden cizmek yerine ALTINA soguk mavi bir taban
// plakasi. Dort sprite'i yeniden uretmekten cok ucuz ve ILERIDE EKLENECEK
// KULELER OTOMATIK KAPSANIR - plaka sprite'tan bagimsiz cizilir.
//
// PLAKA BIR VisualEffect DEGIL: kule ciziminin parcasi. Efekt havuzundan yer
// kaplasaydi agir bir dalgada MAX_VISUAL_EFFECTS tavaninda kule plakalari ile
// patlamalar birbirini kovardi.
//
// IKI BANT, TEK SEBEP: tek renk bes biyomu birden tasiyamaz. Koyu lacivert
// gudde KIS/COL gibi PARLAK zeminlerde, parlak camgobegi bant GECE/SONBAHAR
// gibi KOYU zeminlerde esigi gecer (olcum: FriendlyPlateContrastTest).
// ---------------------------------------------------------------------------
@VisibleForTesting
internal object FriendlyPlate {
    /**
     * Plakanin dis yaricapi, kule sprite genisliginin orani olarak.
     *
     * 0,615 rastgele degil, UC KISITIN kesisimi:
     *  · Sprite'in opak yaricapi yonlere gore 41-75 ref-px (olculdu: alfa>200).
     *    Ortalama ~60, yani 0,615 * 112 = 68,9 plaka yonlerin ~%78'inde
     *    sprite'in DISINDA kalir - yaka gercekten gorunur.
     *  · Ayni haritadaki iki build spot'un EN KISA mesafesi 147 ref-px
     *    (MAP_05). 2 * 68,9 = 137,8 < 147 -> iki komsu plaka HIC CAKISMAZ.
     *  · Kademe-3 taban yayi 0,50 yaricapta -> plaka guvertesinin USTUNDE
     *    kalir, camgobegi yay koyu lacivert uzerinde okunur (once haritanin
     *    degisken zemini uzerindeydi).
     */
    const val RADIUS_FRAC = 0.615f

    /** Dis koyu kontur bandinin kalinligi, referans px. */
    const val EDGE_REF_PX = 3.0f

    /** Parlak camgobegi bandin kalinligi, referans px. */
    const val RIM_REF_PX = 6.5f

    /**
     * Secim halkasinin yaricap orani. ESKIDEN 0,62 IDI ve plakanin tam
     * ustunden geciyordu; altin halka ile plakanin dis konturu tek bir bulanik
     * seride donusurdu. 0,685'e alindi: halka artik plakayi CEVRELIYOR (68,9
     * plaka / 76,7 halka, 7,8 ref-px bosluk) ve "secili olan bu platform"
     * okunur. Denge degeri degil, saf cizim sabiti.
     */
    const val SELECTION_RADIUS_FRAC = 0.685f

    /**
     * Guverte / dis kontur: koyu lacivert. Ton 205,5 derece (180-220 kabul
     * araliginda), bagil parlaklik 0,0145. Dusman ton bandindan (35,8-60,2)
     * 145 derece uzak.
     */
    const val DECK_ARGB = 0xFF0B2233L

    /**
     * Parlak bant: soguk camgobegi. Ton 186,9 derece, bagil parlaklik 0,5207.
     * Kademe-3 pip rengi (0xFF4FC3F7, 198,6 derece) ile ayni aileden ama
     * FARKLI YARICAPTA ve pip/yay her zaman koyu guverte uzerinde duruyor.
     */
    const val RIM_ARGB = 0xFF4DD0E1L
}

private val PLATE_DECK_COLOR = Color(FriendlyPlate.DECK_ARGB)
private val PLATE_RIM_COLOR = Color(FriendlyPlate.RIM_ARGB)

/**
 * UCAK PALETI — soguk taraf (bu OYUNCUNUN varligi), tek sicak vurgu motorda.
 * Tonlar `FriendlyPlate` ailesiyle akraba; dusman haki bandindan (35,8-60,2
 * derece) uzak durmasi bilincli.
 */

/** Kontur pasinin olcegi — govdenin %10 disina tasar. */

/**
 * Hava taarruzu ucaginin govdesi: BIRIM uzunlukta (yari-uzunluk = 1), burun +X.
 *
 * Dizide yalnizca UST yari var; alt yari cizilirken y isareti donduruluyor,
 * boylece siluet tanim geregi simetrik ve elle iki kez guncellenmesi gereken
 * bir tablo olusmuyor (bu depoda elle yazilan ikizler defalarca ayristi).
 *
 * Nokta sirasi: burun -> govde -> kanat ucu -> kanat firar kenari -> govde ->
 * yatay dumen -> kuyruk.
 */

/**
 * Yol BIR KEZ kuruluyor, her karede degil. Cizim `withTransform` icinde
 * olceklendigi icin birim yol her boyutta yeniden kullanilabiliyor — kare
 * basina Path tahsisi yok.
 */

// ---------------------------------------------------------------------------
// NAMLU ALEVI / TRACER KONTRASTI  (VISUAL_AUDIT P0-5)
//
// OLCUM: fx_muzzle_flash BES biyomun BESINDE, fx_tracer besin DORDUNDE WCAG
// 3,0 esiginin altinda (en iyi 2,53 / en kotu 1,92). Ikisinin de koyu dis
// cizgisi YOK; turuncu-sari parlama cim ve toprakla ayni parlaklik bandina
// dusuyor. Bunlar "kulem ates ediyor mu" sorusunun gorsel cevabi.
//
// YENI ASSET URETILMEDI: ayni bitmap uc gecisle ciziliyor.
//   1) KOYU HALE - sprite buyutulmus ve neredeyse siyaha boyanmis halde
//      arkada. Parlak biyomlarda (KIS/COL/SONBAHAR/ORIJINAL) esigi tek
//      basina gecer.
//   2) SPRITE'IN KENDISI - renk kimligi degismez.
//   3) SICAK CEKIRDEK - tek `drawCircle`, ak-sicak. GECE'de koyu hale
//      zeminden ayrisamaz (2,48); esigi cekirdek tasir (7,02).
//
// Ikisi BIRLIKTE her biyomda en az bir kanalin 3,0'i gectigini garanti eder
// (olcum: FriendlyPlateContrastTest).
//
// KARE BUTCESI: renk filtreleri PAYLASILAN sabitler (bkz. enemyHitFlashFilter
// ile ayni gerekce) - degerleri hic degismiyor, degisen tek sey `alpha` ve o
// zaten drawImage'e ayri parametre olarak gidiyor. Cekirdek `drawCircle`
// oldugu icin ucuncu bir bitmap gecisi DEGIL.
// ---------------------------------------------------------------------------
@VisibleForTesting
internal object FxOutline {
    /** Koyu halenin sprite'a gore olcegi. */
    const val HALO_SCALE = 1.18f

    /** Halenin sprite alfasina carpani - kontur olacak kadar koyu, golge kadar yumusak. */
    const val HALO_ALPHA_MUL = 0.85f

    /** Sicak cekirdegin yaricapi, sprite genisliginin orani. */
    const val CORE_RADIUS_FRAC = 0.17f

    /** Neredeyse siyah kontur. Bagil parlaklik 0,0045. */
    const val HALO_ARGB = 0xFF0A0B0CL

    /**
     * Ak-sicak cekirdek. Bagil parlaklik 0,9255. SAF BEYAZ DEGIL: namlu alevi
     * sicak bir olay, soguk beyaz onu enerji efekti gibi gosterirdi.
     */
    const val CORE_ARGB = 0xFFFFF6E0L
}

private val FX_HALO_FILTER = ColorFilter.tint(Color(FxOutline.HALO_ARGB), BlendMode.SrcIn)
private val FX_CORE_COLOR = Color(FxOutline.CORE_ARGB)

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

    // Alt cekmecelerin dp yuksekligini px'e cevirmek icin. Cekmeceler
    // GameCanvas'in USTUNDE ayri Composable'lar oldugu icin yukseklikleri
    // olculerek buraya gelemiyor; ortak sayi GameConfig'te duruyor.
    val density = LocalDensity.current.density

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
            //    P0 DUZELTMESI: harita artik GERILMIYOR. Eskiden bitmap
            //    oynanis dikdortgeninden 10 ref-px tasirilarak (buyutulerek)
            //    ciziliyordu; bu, boyali yol ile yurunen rota arasinda kenarda
            //    10, kosede 14 ref-px SUREKLI kayma demekti. Simdi bitmap TAM
            //    oynanis dikdortgenine oturur ve sarsinti bandi kenar
            //    pikselinin disari sivanmasiyla kapatilir — icerik kaymaz.
            val art = GameConfig.mapArtRect(gameEngine.fieldRect)
            if (mapBitmap != null) {
                drawSprite(
                    image = mapBitmap,
                    left = art.left,
                    top = art.top,
                    width = art.width,
                    height = art.height
                )
                drawMapEdgeBleed(mapBitmap, art, GameConfig.mapEdgeBleedPx(s))
            }

            // 2. Yol/spawn/us CIZIMI YOK — yol artik haritada boyali.
            if (GameConfig.DEBUG_DRAW_PATH) drawDebugPath(gameEngine)

            // 3. Bos build pad isaretleri (fx_build_pad) + "ates hatti yok"
            //    isareti. Kart basili iken O KULENIN menzili yolu gormeyen
            //    pad'ler geri ceker; bkz. drawBuildSpots KDoc'u.
            drawBuildSpots(gameEngine, sprites, selectedBuildSpot, previewTowerType, s)

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

            // 9. ORTULEN SECIM — hayalet gosterge.
            //    Alt cekmece (63 / 56 dp) secili pad'i ya da kuleyi yutuyorsa
            //    hedef cekmecenin USTUNDE tekrarlanir. En uste cizilir:
            //    kule/dusman yiginin altinda kalirsa amacini kaybeder.
            drawOcclusionCallout(
                gameEngine = gameEngine,
                sprites = sprites,
                selectedSpot = selectedBuildSpot,
                selectedTower = selectedTower,
                previewTowerType = previewTowerType,
                drawerDensity = density,
                topInsetPx = topInsetPx,
                s = s
            )
        }

        // 9. EKRAN FLASI — "buyuk olay" isareti (bugun yalniz hava taarruzu).
        //
        //    SARSINTI TRANSFORMUNUN DISINDA: flas tum ekrani kaplar, sarsintiyla
        //    birlikte kaydirilsaydi kenarlarda letterbox rengiyle arasinda
        //    titreyen bir serit acilirdi.
        //
        //    Deger motordan HER KARE okunur; `screenFlashAlpha` StateFlow degil,
        //    yani flasin her karesi HUD'u recompose ETMEZ (kare gecersizligi
        //    zaten yukaridaki `frameTick` okumasindan geliyor).
        val flashAlpha = gameEngine.screenFlashAlpha
        if (flashAlpha > 0f) {
            drawRect(color = AIR_STRIKE_FLASH_COLOR, alpha = flashAlpha)
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

/**
 * HARITA KENAR SIVAMASI — sarsintida acilan koyu seridi kapatir, icerigi
 * KAYDIRMADAN.
 *
 * Eski cozum haritayi buyutuyordu ve bu, sanat ile oynanis arasinda kalici bir
 * kayma uretiyordu (bkz. `GameConfig.MAP_EDGE_BLEED_REF_PX` KDoc'u). Burada
 * bitmap'in en dis 1 piksellik satir/sutunlari dikdortgenin DISINA gerilir:
 * dikdortgenin ICI birebir yerinde kalir, disina yalnizca kenar rengi uzar.
 * Harita kenarlari cim/toprak oldugu icin sivama gozle ayirt edilemez.
 *
 * KARE BUTCESI: dort ince serit, toplam ~%3 ek piksel (tam harita ~1,7 Mpx,
 * seritler ~48 Kpx). Tahsis YOK — `IntOffset`/`IntSize` deger siniflaridir.
 * Cizim scale transformunun ICINDE yapildigi icin tamsayi dst koordinatlari
 * GORUNTU pikseli birimindedir, yani alt-piksel titremesi olusmaz.
 */
private fun DrawScope.drawMapEdgeBleed(
    image: ImageBitmap,
    art: GameConfig.MapFieldRect,
    bleedPx: Float
) {
    val w = image.width
    val h = image.height
    if (bleedPx <= 0f || w < 2 || h < 2) return
    val sx = art.width / w
    val sy = art.height / h
    if (sx <= 0f || sy <= 0f) return
    val bx = ceil(bleedPx / sx).toInt().coerceAtLeast(1)
    val by = ceil(bleedPx / sy).toInt().coerceAtLeast(1)

    withTransform({
        translate(art.left, art.top)
        scale(sx, sy, pivot = Offset.Zero)
    }) {
        // Ust ve alt seritler kose bosluklarini da kapatsin diye yanlardan tasar.
        drawImage(
            image = image,
            srcOffset = IntOffset(0, 0), srcSize = IntSize(w, 1),
            dstOffset = IntOffset(-bx, -by), dstSize = IntSize(w + 2 * bx, by)
        )
        drawImage(
            image = image,
            srcOffset = IntOffset(0, h - 1), srcSize = IntSize(w, 1),
            dstOffset = IntOffset(-bx, h), dstSize = IntSize(w + 2 * bx, by)
        )
        drawImage(
            image = image,
            srcOffset = IntOffset(0, 0), srcSize = IntSize(1, h),
            dstOffset = IntOffset(-bx, 0), dstSize = IntSize(bx, h)
        )
        drawImage(
            image = image,
            srcOffset = IntOffset(w - 1, 0), srcSize = IntSize(1, h),
            dstOffset = IntOffset(w, 0), dstSize = IntSize(bx, h)
        )
    }
}

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

/**
 * ===========================================================================
 * ORTULEN SECIM HAYALETI — "bastigim pad'i acilan panel yuttu"
 * ===========================================================================
 *
 * OLCULEN SORUN (`MapLayoutSafetyTest.bottomDrawerOcclusionIsMeasuredAndReported`):
 * TowerBuildBar (63 dp) acilinca 740x360 dp'de harita 2'de 4, harita 4'te 3 pad
 * cekmecenin altinda kaliyor. Oyuncu pad'e basiyor, panel aciliyor ve hem
 * pad'i hem BIRAKMA ONIZLEMESINI kapatiyor: neye kurdugunu goremiyor.
 *
 * NEDEN ALAN KUCULTULMEDI: 63 dp daha kucultmek 740x360'ta yuksekligin
 * %20'sini goturur ve bu bedel KALICI olurdu; cekmece ise sadece secim
 * varken acik. Bilgi tasindi, alan degil.
 *
 * COZUM (uc kanal birlikte):
 *  1. BAGLI CIZGI — hayaletin hangi pad'e ait oldugunu tekil olarak soyler;
 *     onsuz ekranda "ikinci bir pad" belirmis gibi okunur.
 *  2. HAYALET PLAKA — gercek pad'lerde olmayan koyu disk + altin cerceve,
 *     yani hayalet asla gercek bir hedefle karistirilmaz ve dokunulabilir
 *     gorunmez.
 *  3. ICERIK — bir kule karti BASILI tutuluyorsa O KULENIN sprite'i cizilir:
 *     birakma onizlemesi panelin ustune TASINMIS olur, kaybolmaz.
 *
 * Sarsinti transformunun ICINDE cizilir: bagli cizginin ucu pad ile birlikte
 * hareket etmezse baglanti kopuk gorunur. Sarsinti genligi (maks 5 px)
 * `OCCLUSION_CALLOUT_GAP_REF_PX` boslugunun cok altinda kalir.
 */
private fun DrawScope.drawOcclusionCallout(
    gameEngine: GameEngine,
    sprites: GameSprites,
    selectedSpot: BuildSpot?,
    selectedTower: TowerEntity?,
    previewTowerType: GameConfig.TowerType?,
    drawerDensity: Float,
    topInsetPx: Float,
    s: Float
) {
    // Hedef ve hangi cekmecenin acik oldugu. Pad secimi onceliklidir: iki
    // cekmece ayni anda acik olamaz (motor secimleri birbirini disliyor).
    val targetX: Float
    val targetY: Float
    val drawerDp: Float
    val badge: ImageBitmap?
    when {
        selectedSpot != null -> {
            targetX = selectedSpot.normX
            targetY = selectedSpot.normY
            drawerDp = GameConfig.BUILD_DRAWER_HEIGHT_DP
            badge = previewTowerType?.let { sprites.towers[it] } ?: sprites.buildPad
        }
        selectedTower != null -> {
            targetX = selectedTower.posX
            targetY = selectedTower.posY
            drawerDp = GameConfig.INSPECTOR_DRAWER_HEIGHT_DP
            badge = sprites.towers[selectedTower.type]
        }
        else -> return
    }

    val drawerPx = drawerDp * drawerDensity
    val tapR = GameConfig.TAP_RADIUS_REF_PX * s
    if (!GameConfig.isOccludedByBottomDrawer(targetY, tapR, size.height, drawerPx)) return

    val field = gameEngine.fieldRect
    val r = GameConfig.OCCLUSION_CALLOUT_RADIUS_REF_PX * s
    val cy = GameConfig.occlusionCalloutY(size.height, drawerPx, topInsetPx, s)
    val cx = targetX.coerceIn(
        (field.left + r).coerceAtMost(field.right - r),
        (field.right - r).coerceAtLeast(field.left + r)
    )

    // 1. Bagli cizgi — pad'in ust kenarindan plakanin alt kenarina.
    val strokeW = (3f * s).coerceAtLeast(1.5f)
    drawLine(
        color = CALLOUT_LINK_COLOR,
        start = Offset(targetX, targetY - tapR),
        end = Offset(cx, cy + r),
        strokeWidth = strokeW
    )

    // 2. Plaka: koyu disk + altin cerceve (gercek pad'de bunlar yok).
    drawCircle(color = CALLOUT_PLATE_COLOR, radius = r, center = Offset(cx, cy))
    drawCircle(
        color = CALLOUT_BORDER_COLOR,
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = strokeW)
    )

    // 3. Icerik: basili tutulan kule, yoksa pad isareti.
    if (badge != null) {
        drawSpriteAt(image = badge, cx = cx, cy = cy, width = r * 1.5f, alpha = 0.95f)
    }
}

/**
 * ===========================================================================
 * BOS BUILD PAD'LER + "ATES HATTI YOK" ISARETI
 * ===========================================================================
 *
 * OLCULEN SORUN (cihaz): bolum 8'de pad 7'ye Gatling kuruldu ve kule hicbir
 * seye ates edemedi. Olcum pad'in yerinin YANLIS OLMADIGINI gosterdi — ayni
 * pad'den Fuze Rampasi haritanin en iyi ikinci kapsamasini veriyor (674
 * ref-px yol). Eksik olan sey pad degil, **kurmadan onceki sinyal**: oyun
 * "bu kule buradan yola yetismiyor" demiyordu.
 *
 * NEDEN GIZLEMEK/BLOKLAMAK DEGIL: menzil kalici olarak buyuyor (Gatling kd.1
 * 150 -> kd.2 180 -> kd.3 210) ve meta menzil yukseltmesi de var. Bugun
 * yetismeyen mevzi yarin en iyi mevzi olabilir; pad'i kaldirmak oyuncunun
 * elinden bir plani alir. Mesaj "yapamazsin" degil "yetismiyor".
 *
 * NE ZAMAN CIZILIR: yalnizca build cubugunda bir kart BASILI iken
 * (`previewTowerType != null`). Sebep, geri bildirim hiyerarsisi: isaret bir
 * KULEYE goredir, kule secili degilken cizmek her pad'in yaninda sahibi
 * olmayan bir uyari birakirdi. Kart basili degilken ayni bilgiyi panel
 * tarafi tasir (`TowerBuildBar` kart rozeti) — o, secili PAD'e goredir.
 *
 * UC KANAL, CUNKU RENK TEK BASINA AYIRMAZ:
 *  1. DEGER — pad [GameConfig.BUILD_PAD_NO_REACH_ALPHA] ile geri ceker.
 *  2. SEKIL — halka + capraz cizgi. Sekil, biyom rengi ne olursa olsun ve
 *     renk korlugunde de okunur.
 *  3. RENK — notr acik gri. Kasten NOTR: soguk mavi taban plakasi
 *     ([FriendlyPlate]) "bu benim", haki ton bandi "bu dusman", altin
 *     "bu secili". Uyariya bu uc aileden bir renk vermek dorduncu bir anlami
 *     mevcut uc anlamdan birinin uzerine yazardi.
 */
private fun DrawScope.drawBuildSpots(
    gameEngine: GameEngine,
    sprites: GameSprites,
    selectedSpot: BuildSpot?,
    previewTowerType: GameConfig.TowerType?,
    s: Float
) {
    val w = GameConfig.FX_BUILD_PAD_REF_PX * s
    // Menzil EKRAN pikselinde: rotalar da (`scaledRoutes`) ekran pikselinde.
    // `previewRangeRef` meta menzil yukseltmesini zaten iceriyor, yani isaret
    // oyuncunun GERCEK menziline gore verilir — panelde bir sey gosterip
    // sahada baskasini kullanmak bu depoda ayri bir hata sinifi.
    val previewRangePx = previewTowerType?.let { gameEngine.previewRangeRef(it) * s } ?: 0f
    val routes = gameEngine.scaledRoutes
    gameEngine.scaledBuildSpots.forEach { spot ->
        val isOccupied = gameEngine.towers.any { it.buildSpotId == spot.id }
        if (isOccupied) return@forEach
        val isSelected = selectedSpot?.id == spot.id
        val noLineOfFire = previewRangePx > 0f &&
            !GameConfig.coversRoute(spot.normX, spot.normY, previewRangePx, routes)
        drawSpriteAt(
            image = sprites.buildPad,
            cx = spot.normX,
            cy = spot.normY,
            width = if (isSelected) w * 1.08f else w,
            alpha = when {
                noLineOfFire -> GameConfig.BUILD_PAD_NO_REACH_ALPHA
                isSelected -> GameConfig.BUILD_PAD_SELECTED_ALPHA
                else -> GameConfig.BUILD_PAD_IDLE_ALPHA
            }
        )
        if (noLineOfFire) drawNoLineOfFireMark(spot.normX, spot.normY, w, s)
    }
}

/**
 * "ATES HATTI YOK" ISARETI — halka + capraz cizgi.
 *
 * Once koyu bir disk, sonra acik gri halka ve cizgi: harita zemini bes biyomda
 * (kis/col/gece/sonbahar/...) hem cok acik hem cok koyu olabildigi icin tek
 * renkli bir cizim bazi biyomlarda kaybolurdu. Koyu disk kendi kontrastini
 * yaninda tasir — ayni cozum `drawOcclusionCallout` plakasinda da kullanildi.
 *
 * TAHSIS: `Stroke` cagri basina nesne uretir ve bu fonksiyon kare basina en
 * fazla pad sayisi kadar (11) kosar, YALNIZCA kart basili iken. Onizleme
 * kisa omurlu bir etkilesim oldugu icin bu, kule basina her kare kosan
 * `drawTower` ile ayni butcede degil; yine de stroke tek kez uretilip iki
 * cizimde paylasilir.
 */
private fun DrawScope.drawNoLineOfFireMark(cx: Float, cy: Float, padWidth: Float, s: Float) {
    val r = padWidth * 0.36f
    val strokeW = (NO_LINE_OF_FIRE_STROKE_REF_PX * s).coerceAtLeast(1.5f)
    val stroke = Stroke(width = strokeW)
    drawCircle(color = NO_LINE_OF_FIRE_SHADE_COLOR, radius = r, center = Offset(cx, cy))
    drawCircle(color = NO_LINE_OF_FIRE_MARK_COLOR, radius = r, center = Offset(cx, cy), style = stroke)
    // Capraz cizgi 45 derece: halkanin icinde kalmasi icin yaricap 1/sqrt(2)
    // ile carpilir, yoksa uclar halkayi disaridan keser ve isaret "kirik" gorunur.
    val d = r * 0.7071f
    drawLine(
        color = NO_LINE_OF_FIRE_MARK_COLOR,
        start = Offset(cx - d, cy - d),
        end = Offset(cx + d, cy + d),
        strokeWidth = strokeW
    )
}

private fun DrawScope.drawTower(
    tower: TowerEntity,
    sprites: GameSprites,
    isSelected: Boolean,
    s: Float
) {
    val spec = GameConfig.TOWER_SPRITES[tower.type] ?: return
    val image = sprites.towers[tower.type] ?: return

    // ------------------------------------------------------------------------
    // 1. DOST TABAN PLAKASI - her seyin altinda (bkz. FriendlyPlate).
    //
    // Kulenin oturma noktasi (tower.posX/posY) merkezlidir; GERI TEPME ILE
    // BIRLIKTE OYNAMAZ. Plaka zemine civilenmis bir platform, kulenin bir
    // parcasi degil - geri tepmede kayarsa "kule sabit, plaka kayiyor" gibi
    // bir ayrilma hissi olurdu.
    //
    // Uc dolgu dairesi, SIFIR TAHSIS: `Offset`/`Color` deger sinifi, `Fill`
    // varsayilan tekil nesne. Stroke KULLANILMADI - `Stroke(...)` cagri basina
    // nesne uretir ve bu kod kule basina HER KARE calisir.
    // ------------------------------------------------------------------------
    val plateCenter = Offset(tower.posX, tower.posY)
    val plateR = spec.widthRefPx * s * FriendlyPlate.RADIUS_FRAC
    val plateEdge = FriendlyPlate.EDGE_REF_PX * s
    val plateRim = FriendlyPlate.RIM_REF_PX * s
    // Dis koyu kontur (parlak biyomlarda tasiyici kanal)
    drawCircle(color = PLATE_DECK_COLOR, radius = plateR, center = plateCenter)
    // Parlak camgobegi bant (koyu biyomlarda tasiyici kanal)
    drawCircle(color = PLATE_RIM_COLOR, radius = plateR - plateEdge, center = plateCenter)
    // Guverte: kademe centikleri ve kademe-3 yayi bunun uzerine duser
    drawCircle(
        color = PLATE_DECK_COLOR,
        radius = (plateR - plateEdge - plateRim).coerceAtLeast(0f),
        center = plateCenter
    )

    // 2. Secim halkasi - PLAKANIN DISINDA (bkz. SELECTION_RADIUS_FRAC KDoc'u).
    if (isSelected) {
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = spec.widthRefPx * s * FriendlyPlate.SELECTION_RADIUS_FRAC,
            center = plateCenter,
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

// ---------------------------------------------------------------------------
// YURUYUS DONGUSU  (prosedurel — yeni sprite YOK)
// ---------------------------------------------------------------------------
// Cihaz raporu: *"piyadeler yururken tek adimi ileri atar halde ve sabit."*
// Dogruydu: `spr_enemy_infantry` TEK KARE ve asker o karede adim atmis halde
// donmus duruyor, yani hareket eden bir HEYKEL gibi kayiyordu.
//
// Yurume dongusu icin ikinci/ucuncu kare URETMEDIK. Sebep sadece maliyet
// degil: kare uretmek bes dusman tipi x bes biyom demek ve APK'ya bayt
// eklerdi. Bunun yerine govde, kendi ilerlemesine bagli olarak SALINIYOR —
// tepeden bakista yuruyusu okutan sey zaten ayak degil, agirligin bir
// yandan digerine gecmesidir.
//
// ⚠ DONGU ZAMANA DEGIL, KAT EDILEN YOLA BAGLI. Bu tercih bedava uc dogru
// davranis veriyor:
//   · Frost ile yavaslayan dusman adimlarini da yavaslatir (zamana bagli
//     olsaydi yerinde tepinirdi — yavaslama mekanigi gorsel olarak yalan
//     soylerdi),
//   · 2x oyun hizinda dongu de iki kat hizli akar,
//   · duraklamada donar.
//
// YALNIZCA YAYA BIRIMLER. Arac ve tanklarin salinmasi "suspansiyon" degil
// "kayma" olarak okunur; onlar dokunulmadan birakildi.

@VisibleForTesting
internal object Gait {
    /** Bir tam adim dongusu icin kat edilen yol, referans px. */
    const val STRIDE_REF_PX = 34f

    /** Govdenin yurume yonune DIK salinim genligi, referans px. */
    const val SWAY_REF_PX = 2.4f

    /** Govde egilmesi, derece. */
    const val LEAN_DEG = 3.6f

    /** Yaya birimler salinir; araclar salinmaz. */
    fun isFootUnit(type: GameConfig.EnemyType): Boolean = when (type) {
        GameConfig.EnemyType.INFANTRY,
        GameConfig.EnemyType.FAST_SOLDIER,
        GameConfig.EnemyType.SHIELDED_TROOPER -> true
        GameConfig.EnemyType.ARMORED_VEHICLE,
        GameConfig.EnemyType.TANK,
        GameConfig.EnemyType.COMMAND_TANK -> false
    }
}

private const val GAIT_STRIDE_REF_PX = Gait.STRIDE_REF_PX

private const val GAIT_SWAY_REF_PX = Gait.SWAY_REF_PX

/** Salinimla CEYREK dongu faz farkli: agirlik once yana kayar, egilme onu
  * takip eder. Ayni fazda olsalardi tek parca sallanan bir tabela gorunurdu. */
private const val GAIT_LEAN_DEG = Gait.LEAN_DEG

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

    // Yuruyus salinimi (bkz. yukaridaki blok). Yaya olmayan birimde faz 0 kalir.
    var swayX = 0f
    var swayY = 0f
    var lean = 0f
    if (Gait.isFootUnit(enemy.type)) {
        val phase = enemy.distanceTraveledPx / GAIT_STRIDE_REF_PX * (2.0 * Math.PI).toFloat()
        val amp = GAIT_SWAY_REF_PX * s
        // Yurume yonune DIK eksen: heading + 90 derece.
        val perp = enemy.rotationAngleRad + (Math.PI / 2.0).toFloat()
        val swing = sin(phase)
        swayX = cos(perp) * amp * swing
        swayY = sin(perp) * amp * swing
        lean = cos(phase) * GAIT_LEAN_DEG
    }
    val bodyX = enemy.posX + swayX
    val bodyY = enemy.posY + swayY

    drawSpriteAt(image, bodyX, bodyY, width, rotation + lean, spec.pivotYFrac, alpha = gate)

    // Isabet parlamasi: ayni silueti beyaz olarak ustune bindir (tint yerine
    // SrcIn -> sprite'in sekli korunur, sadece rengi beyazlar).
    if (enemy.hitFlashTimerSeconds > 0f) {
        val a = (enemy.hitFlashTimerSeconds / HIT_FLASH_DURATION_SECONDS)
            .coerceIn(0f, 1f) * 0.75f
        // Parlama GOVDEYLE ayni yerde ve ayni acida olmali; ayrilirsa vurus
        // ani "ikinci bir dusman belirdi" gibi okunur.
        drawSpriteAt(
            image, bodyX, bodyY, width, rotation + lean, spec.pivotYFrac,
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

    // P0-5: tracer bes biyomun DORDUNDE esigin altindaydi (1,92-2,50) ve
    // koyu konturu yok. "Kulem ates ediyor mu" sorusunun yarisini bu sprite
    // cevapliyor - koyu hale + sicak cekirdek ile cizilir. Diger mermi
    // tipleri (top mermisi, fuze, buz darbesi) olcumde zaten konturlariyla
    // esigi geciyor, onlara DOKUNULMAZ.
    if (proj.type == ProjectileType.BULLET) {
        drawFxWithOutline(image, proj.posX, proj.posY, widthPx, headingDeg, alpha = 1f)
    } else {
        drawSpriteAt(image, proj.posX, proj.posY, widthPx, headingDeg)
    }
}

/**
 * KOYU HALE + SICAK CEKIRDEK ile cizim (VISUAL_AUDIT P0-5).
 *
 * Namlu alevi ve tracer'in tek sorunu KONTUR YOKLUGU: ikisi de gradyanli
 * turuncu-sari parlama ve hicbir biyomda WCAG 3,0'i gecemiyorlar. Burada ayni
 * bitmap uc gecisle cizilir - koyu hale (parlak zeminler icin), sprite'in
 * kendisi (renk kimligi), sicak cekirdek (koyu zeminler icin).
 *
 * KARE BUTCESI: bir bitmap gecisi EKLENIR (hale) + bir `drawCircle`. Filtre
 * paylasilan sabit, yeni nesne uretilmez. Cagiran taraf sayisi zaten
 * sinirlidir: namlu alevi MAX_VISUAL_EFFECTS tavaninda, tracer ise ucusta
 * olan mermi sayisiyla.
 */
private fun DrawScope.drawFxWithOutline(
    image: ImageBitmap,
    cx: Float,
    cy: Float,
    width: Float,
    rotationDeg: Float,
    alpha: Float
) {
    if (alpha <= 0f) return
    drawSpriteAt(
        image = image,
        cx = cx,
        cy = cy,
        width = width * FxOutline.HALO_SCALE,
        rotationDeg = rotationDeg,
        alpha = alpha * FxOutline.HALO_ALPHA_MUL,
        colorFilter = FX_HALO_FILTER
    )
    drawSpriteAt(image = image, cx = cx, cy = cy, width = width, rotationDeg = rotationDeg, alpha = alpha)
    drawCircle(
        color = FX_CORE_COLOR,
        radius = width * FxOutline.CORE_RADIUS_FRAC,
        center = Offset(cx, cy),
        alpha = alpha
    )
}

private fun DrawScope.drawVisualEffect(fx: VisualEffect, sprites: GameSprites, s: Float) {
    // GECIKMELI EFEKT: negatif yas "henuz baslamadi" demektir (bkz.
    // VisualEffect.ageSeconds). Bu kontrol olmadan zincirin TUM halkalari ilk
    // karede progress = 0'da, yani tam alfada birden belirirdi — gecikmenin
    // tersi. Zincirleme geri bildirimin (hava taarruzu) tek dayanagi bu satir.
    if (fx.ageSeconds < 0f) return

    val progress = (fx.ageSeconds / fx.maxAgeSeconds).coerceIn(0f, 1f)
    // easeOutCubic: efektler hizli acilir, yavas soner. Lineer kullanilmaz.
    val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
    val fade = (1f - progress).coerceIn(0f, 1f)

    when (fx.type) {
        EffectType.MUZZLE_FLASH -> {
            val deg = Math.toDegrees(fx.angleRad.toDouble()).toFloat() -
                GameConfig.PROJECTILE_SPRITE_BASE_ANGLE_DEG
            // P0-5: alev BES biyomun BESINDE de esigin altindaydi (en iyi
            // 2,53). Koyu hale + sicak cekirdek ile cizilir.
            drawFxWithOutline(
                image = sprites.muzzleFlash,
                cx = fx.posX,
                cy = fx.posY,
                width = GameConfig.FX_MUZZLE_FLASH_REF_PX * s * fx.scale * (0.75f + eased * 0.45f),
                rotationDeg = deg,
                alpha = fade
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
        EffectType.AIR_STRIKE_RUN -> drawAirStrikeRun(fx, sprites, s, progress)
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

/**
 * HAVA TAARRUZU KOSUSU — ekran capinda okunan tek olay.
 *
 * NEDEN VAR: cihazda kullanici *"hava destek istedim bir sey gelmedi sanki"*
 * dedi. Guclendirici calisiyordu; gorunen tek sey dusman basina kucuk bir
 * patlamaydi. Burasi olayin GENISLIGINI cizer: sahayi bastan basa kesen bir
 * ucus hatti, hat boyunca ilerleyen arac ve arkasindaki duman izi.
 *
 * ALAN SOZLESMESI (motor tarafi: `GameEngine.runAirStrike`):
 *   posX/posY = hattin giris noktasi, angleRad = dogrultu,
 *   radiusPx  = hattin toplam uzunlugu, scale = gecis suresinin omre orani.
 *
 * KARE BUTCESI: tek efekt nesnesi, kare basina SIFIR tahsis. Iz parcaciklari
 * burada hesaplanir; ayri `VisualEffect` uretmek 8 dusmanlik bir sahnede efekt
 * tavanini tek basina doldururdu. Cizim maliyeti sabit: 1 cizgi +
 * [AIR_STRIKE_TRAIL_PUFFS] sprite + 1 arac.
 */
private fun DrawScope.drawAirStrikeRun(
    fx: VisualEffect,
    sprites: GameSprites,
    s: Float,
    progress: Float
) {
    val length = fx.radiusPx
    if (length <= 0f) return

    val dirX = cos(fx.angleRad)
    val dirY = sin(fx.angleRad)

    // Gecis fazi omrun ilk `scale` kadari; sonrasi cikis kuyrugudur (arac
    // sahayi terk etmistir, yalnizca iz soner).
    val runFrac = fx.scale.coerceIn(0.05f, 1f)
    // easeOutCubic DEGIL LINEER: aracin hizi sabit olmali, cunku her hedefin
    // patlama ANI motorda X oranindan dogrusal turetiliyor. Egri kullanilsaydi
    // arac ile bombasi birbirinden kayardi.
    val travel = (progress / runFrac).coerceIn(0f, 1f)
    val fade = (1f - progress).coerceIn(0f, 1f)

    fun pointAt(u: Float): Offset =
        Offset(fx.posX + dirX * length * u, fx.posY + dirY * length * u)

    // 1. Ucus hatti: aracin GECTIGI kisim cizilir, onu goren oyuncu olayin
    //    nereden nereye gittigini tek bakista okur.
    val head = pointAt(travel)
    drawLine(
        color = AIR_STRIKE_PATH_COLOR,
        start = pointAt(0f),
        end = head,
        strokeWidth = AIR_STRIKE_PATH_STROKE_REF_PX * s.coerceAtLeast(0.5f),
        alpha = fade * 0.45f
    )

    // 2. Duman izi: aracin gerisinde, yaslandikca buyuyup solan puflar.
    val puffRef = AIR_STRIKE_TRAIL_PUFF_REF_PX * s
    for (i in 1..AIR_STRIKE_TRAIL_PUFFS) {
        val back = i.toFloat() / AIR_STRIKE_TRAIL_PUFFS
        val u = travel - AIR_STRIKE_TRAIL_SPAN * back
        if (u <= 0f) continue
        val p = pointAt(u)
        drawSpriteAt(
            sprites.smokePuff, p.x, p.y,
            puffRef * (0.45f + 0.75f * back),
            alpha = fade * 0.55f * (1f - back)
        )
    }

    // 3. Arac. Sahayi terk ettikten sonra cizilmez — kuyrukta yalniz iz kalir.
    if (travel < 1f) {
        // ⚠ PROSEDUREL SILUET BIRAKILDI (2026-08-21). Once gerilmis fuze
        // sprite'i vardi, sonra ben cizdim; cihaz raporu: *"ucak duz mavi
        // olmus, gorseli bozulmus"*. Haklıydi — tek renkli bir yol dolgusu
        // uzaktan bir ucagi degil mavi bir leke gosteriyor.
        //
        // Artik gercek sanat kullaniliyor. Sprite'in BURNU +X'e bakiyor, yani
        // taban aci duzeltmesi yok: dogrudan ucus acisi veriliyor.
        val deg = Math.toDegrees(fx.angleRad.toDouble()).toFloat()
        drawSpriteAt(
            sprites.airStrikeJet, head.x, head.y,
            AIR_STRIKE_JET_REF_PX * s,
            rotationDeg = deg,
            alpha = 1f
        )
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
