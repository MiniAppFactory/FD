package com.miniappfactory.frontlinedefender.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.PrestigeDecision
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.MetaUpgrades
import com.miniappfactory.frontlinedefender.game.economy.PurchaseDecision
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import com.miniappfactory.frontlinedefender.game.economy.UpgradeLine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.ui.theme.*

/**
 * Cephanelik "geri" hedefinin dokunma tabani. Depodaki 44 dp kuralinin ayni
 * degeri (`GameDialogs.MinTouchTarget`, `ArtToggleMinTouch`). Ilk halinde bu
 * hedef ciplak 15 sp'lik bir `Text`ti: ~18 dp yuksekligindeydi (yazi tipinden
 * TAHMIN edildi, calisma zamaninda olculmedi). Bkz. `ShopScreenTouchTest`.
 */
internal val ShopBackMinTouch = 44.dp

/**
 * CEPHANELIK — kalici meta yukseltme dukkani.
 *
 * NEDEN VAR: kullanici testinde "ilk levellarda hic ihtiyacim olmadi" dedi ve
 * haklıydı — coin'in gidecek GORUNUR bir yeri yoktu, dolayisiyla reklam izlemek
 * de anlamsizdi. Ekonomi modeli (5 yukseltme hatti, rezerv kurali, fiyat
 * tablolari) bastan hazirdi; eksik olan tek sey bu ekrandi.
 *
 * KONUSLANMA REZERVI GORUNUR: GDD'nin soft-lock garantisi, bakiyeyi siradaki
 * bolum kilidinin altina dusurecek satin almayi REDDETMEK. Oyuncunun bunu bir
 * hata sanmamasi icin rezerv miktari ve sebebi ustte yazili duruyor.
 */
@Composable
fun UpgradeShopScreen(
    progress: CampaignProgressImpl,
    onBack: () -> Unit,
    /**
     * IKMAL DEPOSU (IAP) girisi — 2026-09-02. null ise kart HIC cizilmez ve coin
     * cipi dokunulmaz kalir (testler ve eski cagri yerleri icin sifir fark).
     */
    onOpenStore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Tam ekran katman: geri tusu ile de kapanmali. Bkz. MissionsScreen —
    // cihazda oyuncu gorev panelinde mahsur kaldi (2026-08-18).
    BackHandler(onBack = onBack)

    // Satin alma sonrasi kartlarin yenilenmesi icin. CampaignProgressImpl kendi
    // icinde mutableStateOf tutuyor ama rank/fiyat okumalari turetilmis oldugu
    // icin acik bir tetikleyici davranisi netlestiriyor.
    var buyTick by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SleekSurfaceHeader, SleekDarkBg)))
    ) {
        Column(Modifier.fillMaxSize()) {

            // ---- baslik seridi ----
            // Dikey ic bosluk 12 -> 8 dp: "geri" hedefi 44 dp'ye cikti ve serit
            // (8 + 44 + 8 = 60 dp) eski yukseklikte (12 + 36 coin cipi + 12)
            // KALDI. Cocuklarin hepsi dikeyde ortali, yani gorunum kaymaz.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurfaceHeader)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .heightIn(min = ShopBackMinTouch)
                        .widthIn(min = ShopBackMinTouch)
                        .clickable(onClick = onBack)
                        .testTag("shop_back")
                ) {
                    Text(
                        text = stringResource(R.string.level_back),
                        color = SleekTextAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(Modifier.width(20.dp))
                Text(
                    text = stringResource(R.string.shop_title),
                    color = SleekTextAccent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.weight(1f))
                key(buyTick) {
                    // Bakiyeye dokunmak = "daha fazla coin" (Ikmal Deposu). Giris
                    // lambda'si yoksa cip eskisi gibi salt okunur.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            // 44 dp dokunma tabani: cip Ikmal Deposu kapisi (ShopBackMinTouch ile ayni kural).
                            .heightIn(min = ShopBackMinTouch)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33FFD700))
                            .clickable(enabled = onOpenStore != null) { onOpenStore?.invoke() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("shop_coin_chip")
                    ) {
                        SpriteIcon(
                            id = R.drawable.spr_ic_coins,
                            size = 18.dp,
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(R.string.level_coin_amount, progress.coins),
                            color = SleekGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // ---- rezerv aciklamasi ----
            key(buyTick) {
                if (progress.reserve > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.shop_reserve, progress.reserve),
                            color = SleekGold.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stringResource(R.string.shop_reserve_hint),
                            color = SleekTextAccent.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // ---- yukseltme kartlari ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                UpgradeLine.entries.forEach { line ->
                    key(line, buyTick) {
                        UpgradeCard(
                            line = line,
                            progress = progress,
                            onBought = { buyTick++ }
                        )
                    }
                }
                // PRESTIJ NISANI — 6. kart (ECONOMY_ANALYSIS B). `UpgradeLine`
                // enum'una EKLENMEDI: o enum `MetaUpgrades` matematigine ve
                // rank kapilarina bagli; kozmetik bir kalemi oraya sokmak
                // ekonomi kapilarini prestije de uygulardi. Ayri kart, ayni
                // sablon dili.
                key(buyTick) {
                    PrestigeCard(progress = progress, onBought = { buyTick++ })
                }
                // IKMAL DEPOSU — 7. kart (IAP, 2026-09-02). Gercek para burada
                // HARCANMAZ; kart yalnizca ayri ekrana gecis. Ayni sablon dili,
                // ama coin fiyati yerine "AC": oyuncu bu kartta kac lira
                // odeyecegini gormez, cunku fiyat Play'den ancak o ekranda gelir.
                if (onOpenStore != null) {
                    StoreEntryCard(onOpen = onOpenStore)
                }
            }
        }
    }
}

// =============================================================================
// KART SABLONU — TEK KAYNAK
// =============================================================================

/**
 * `ui_card_shop.webp` (560 x 747) uzerindeki bolgeler. UpgradeCard, PrestigeCard
 * ve StoreEntryCard'in UCU DE bu nesneyi kullanir; bolge sayisi artik uc kopya
 * DEGIL (HANDOVER §5 sinif 2 — "ayni deger iki yerde, biri bayatlar": kalkan
 * satiri bolum kartlarinda duzeltildiginde burada 0,645'te kalmisti).
 *
 * Bir bolge, kartin GENISLIK/YUKSEKLIGINE oranla dort ic bosluktur (`start` ve
 * `end` genislige, `top` ve `bottom` yukseklige). Yerlestirme `w * fraction`
 * ile yapilir: kart tek bir genislik secilerek (168 dp) kurulur, yukseklik
 * sablonun oranindan turer.
 *
 * OLCUM (2026-09-30, `ui_card_shop.webp` piksel satirlari, WIC ile cozulerek):
 *   satir 376  -> 0,5033   tam genislikte ayrac (baslik plakasinin alti)
 *   satir 436  -> 0,5837   crosshair satirinin ALT cizgisi (x 168-446)
 *   satir 450  -> 0,6024   tam genislikte ince ayrac (yuvanin ICINDE kalir)
 *   satir 498  -> 0,6667   yama bolgesinin baslangici (dokuda 23 -> 57 luminans)
 * Ayni uc deger `ui_card_available.webp` uzerinde de olculmustu ve LevelCard
 * cihazda dogrulandi (2026-09-02). Ikon merkezleri: crosshair 0,5569 · kalkan
 * 0,6412 (yani yuva merkezlerinin ~3 dp ALTINDA — bkz. asagidaki "yazi orta
 * noktasi" notu).
 *
 * YAZI ORTA NOKTASI (cikarim, yeni kodda cihazda OLCULMEDI): Tema
 * `bodyLarge`'i `lineHeight = 24.sp` tasir ve `MaterialTheme` bunu her `Text`e
 * varsayilan verir. 18 dp'lik yuva 24 sp'lik satira SIGMAZ; metin yuvanin
 * ustunden baslar ve ~3 dp asagiya tasar. Bu tasma tam olarak ikon
 * merkezlerine denk gelir (yuva merkezi + ~3 dp ~ ikon merkezi). Ayni tasma
 * `AutoShrinkText`in yuksekligi TASMA saymasina yol acar; yani bu yuvalardaki
 * `AutoShrinkText` fiilen HEP taban punto (7 sp) ile cizilir — LevelCard'daki
 * hedef/dalga satirlari gibi. v52 cihaz karesinde ayni sey baslik (22 dp band)
 * ve buton (22,8 dp band) icin gorulur: ikisi de 11,5 / 10 sp yerine 7 sp.
 */
internal object ShopCardBands {

    /** Genislik SECILIR, yukseklik sablonun oranindan turer. */
    val CardWidth: Dp = 168.dp
    const val ASPECT: Float = 560f / 747f

    /** Sablondaki yatay ayrac cizgileri (y / h). */
    const val DIVIDER_TOP: Float = 0.5033f
    const val DIVIDER_MID: Float = 0.5837f
    const val DIVIDER_BOTTOM: Float = 0.6667f

    /** Bir bolge: kart olcusune KESIR olarak dort ic bosluk. */
    data class Band(val start: Float, val top: Float, val end: Float, val bottom: Float) {
        /** Sol / ust / sag / alt KENARIN kart icindeki konumu (0..1). */
        val x0: Float get() = start
        val y0: Float get() = top
        val x1: Float get() = 1f - end
        val y1: Float get() = 1f - bottom
        val centreY: Float get() = (y0 + y1) / 2f
        val height: Float get() = y1 - y0
    }

    /** Pencere: ikon / madeni para / (gelecekte) paket gorseli. */
    val WINDOW = Band(start = 0.110f, top = 0.085f, end = 0.116f, bottom = 0.615f)

    /** Baslik plakasi. */
    val TITLE = Band(start = 0.14f, top = 0.408f, end = 0.14f, bottom = 0.492f)

    /** Crosshair satiri — sablonun ilk metin yuvasi (0,5033 - 0,5837). */
    val EFFECT = Band(start = 0.285f, top = DIVIDER_TOP, end = 0.13f, bottom = 1f - DIVIDER_MID)

    /** Kalkan satiri — sablonun ikinci metin yuvasi (0,5837 - 0,6667). */
    val SHIELD = Band(start = 0.285f, top = DIVIDER_MID, end = 0.13f, bottom = 1f - DIVIDER_BOTTOM)

    /** Buton bandi (etiket; buton sanati sablonda GOMULU). */
    val BUTTON = Band(start = 0.16f, top = 0.806f, end = 0.16f, bottom = 0.092f)

    /** Numara dairesi: sol ust; `size` GENISLIGIN kesridir (kare daire). */
    const val NUMBER_START: Float = 0.115f
    const val NUMBER_TOP: Float = 0.077f
    const val NUMBER_SIZE: Float = 0.165f

    /** Nokta satirinin ust kenari (yama bolgesinde). */
    const val DOTS_TOP: Float = 0.695f

    /** Nokta capi: dolu / bos. Dolu-bos ayrimi boyutla da verilir (renk tek kanal degil). */
    val DotFilled: Dp = 8.dp
    val DotEmpty: Dp = 6.dp
}

private val ShopWindowBackground = Color(0xFF141A0E)
private val ShopNumberColor = Color(0xFFEAF2DC)
private val ShopTitleColor = Color(0xFFE8F0DC)
private val ShopEffectColor = Color(0xFFAAB894)
private val ShopShieldColor = Color(0xFFD8C46A)
private val ShopDotEmptyColor = Color(0xFF49533E)
private val ShopButtonActiveColor = Color(0xFFDCE8CC)
private val ShopButtonInactiveColor = Color(0x99C5D6B4)

private fun Modifier.inShopBand(band: ShopCardBands.Band, w: Dp, h: Dp): Modifier =
    padding(
        start = w * band.start,
        top = h * band.top,
        end = w * band.end,
        bottom = h * band.bottom
    ).fillMaxSize()

/**
 * Cephanelik kart cercevesi — UC kart (yukseltme, prestij, ikmal deposu) ayni
 * cerceveyi kullanir.
 *
 * BOLGELERIN ANLAMI (bolum kartlariyla ayni dil; kullanici istegi 2026-08-26):
 *   pencere        -> [window] (ikon / madeni para; koyu zeminde, Fit)
 *   numara dairesi -> [number] (null ise BOS: magaza ilerleme kalemi degil, kapi)
 *   baslik bandi   -> [title]
 *   crosshair sat. -> [effect] (TEK satir, 9 -> 7 sp)
 *   kalkan satiri  -> [shield]
 *   yama bolgesi   -> [dotCount] nokta, ilk [dotsFilled] tanesi dolu
 *   buton bandi    -> [buttonLabel] (fiyat / MAKS / kisit etiketi / AC)
 *
 * EFFECT NEDEN TEK SATIR: eski band (0,478 / 0,392) iki satira izin veriyordu
 * ama sablonun iki ayrac cizgisini (0,5033 ve 0,5837) YIYORDU; yuva yalnizca
 * ~18 dp. O iki satirin sebebi ("etki cumleleri hedeften uzun, tek satir
 * kesiyordu") cumleler kisaltilinca ortadan kalkti — en uzun etki metni
 * ("Next: Command Insignia") ~98 dp'lik yuvada 9 sp'de ~97, 7 sp'de ~77 dp
 * (java.awt yazi olcusunun, v52 cihaz karesindeki tek bir olcume kalibre
 * edilmis TAHMINI — calisma zamaninda olculmedi; cihazda dogrulanmali).
 *
 * SAF DESEN: sablon sanati + metin bandi. Buton/`Surface` YOK — S8 (API 24)
 * buyuk sablon ile ayni kapta Material `Surface` butonunu METINSIZ ciziyor
 * (HANDOVER §1c). Tiklama kartin kendisinde (`clickable`).
 */
@Composable
internal fun ShopCardFrame(
    cardTag: String,
    number: String?,
    title: String,
    effect: String,
    shield: String,
    dotCount: Int,
    dotsFilled: Int,
    buttonLabel: String,
    buttonActive: Boolean,
    buttonTag: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    window: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .width(ShopCardBands.CardWidth)
            .aspectRatio(ShopCardBands.ASPECT)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(cardTag)
    ) {
        val w = maxWidth
        val h = maxHeight

        // PENCERE — sablonun ALTINDA (sablonun penceresi seffaf).
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .inShopBand(ShopCardBands.WINDOW, w, h)
                .background(ShopWindowBackground),
            content = window
        )

        Image(
            painter = painterResource(R.drawable.ui_card_shop),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize()
        )

        // NUMARA DAIRESI — yalnizca deger varsa.
        if (number != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = w * ShopCardBands.NUMBER_START, top = h * ShopCardBands.NUMBER_TOP)
                    .size(w * ShopCardBands.NUMBER_SIZE)
            ) {
                AutoShrinkText(
                    text = number,
                    color = ShopNumberColor,
                    maxFontSize = 13.sp,
                    minFontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }

        // BASLIK
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.inShopBand(ShopCardBands.TITLE, w, h)
        ) {
            AutoShrinkText(
                text = title,
                color = ShopTitleColor,
                fontWeight = FontWeight.Black,
                maxFontSize = 11.5.sp,
                minFontSize = 7.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ETKI — crosshair satiri (ikon sablonda GOMULU, metin sola hizali).
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.inShopBand(ShopCardBands.EFFECT, w, h)
        ) {
            AutoShrinkText(
                text = effect,
                color = ShopEffectColor,
                maxFontSize = 9.sp,
                minFontSize = 7.sp,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // KALKAN SATIRI — kademe n/m, paket sayisi vb.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.inShopBand(ShopCardBands.SHIELD, w, h)
        ) {
            Text(
                text = shield,
                color = ShopShieldColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        // NOKTALAR — yamali bolgede. Dolu/bos ayrimi boyutla da verilir.
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = h * ShopCardBands.DOTS_TOP)
                .align(Alignment.TopCenter)
        ) {
            repeat(dotCount) { i ->
                val filled = i < dotsFilled
                Box(
                    Modifier
                        .size(if (filled) ShopCardBands.DotFilled else ShopCardBands.DotEmpty)
                        .clip(RoundedCornerShape(50))
                        .background(if (filled) SleekGold else ShopDotEmptyColor)
                )
            }
        }

        // FIYAT / DURUM — buton bandi (buton sanati sablonda gomulu).
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .inShopBand(ShopCardBands.BUTTON, w, h)
                .testTag(buttonTag)
        ) {
            AutoShrinkText(
                text = buttonLabel,
                color = if (buttonActive) ShopButtonActiveColor else ShopButtonInactiveColor,
                maxFontSize = 10.sp,
                minFontSize = 7.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// =============================================================================
// 7. KART — IKMAL DEPOSU GIRISI
// =============================================================================

/**
 * Ikmal Deposu girisi. Numara dairesi BOS (kapi, ilerleme kalemi degil);
 * pencere madeni para; noktalar = paket sayisi ([StoreCatalog.coinPacks]'ten,
 * elle yazilmis "5" DEGIL). Metin reklamsizdir: oyundan tum reklamlar kalkiyor.
 */
@Composable
private fun StoreEntryCard(onOpen: () -> Unit) {
    val packCount = StoreCatalog.coinPacks.size
    ShopCardFrame(
        cardTag = "store_entry_card",
        onClick = onOpen,
        number = null,
        title = stringResource(R.string.store_card_title),
        effect = stringResource(R.string.store_card_body),
        shield = stringResource(R.string.store_card_packs, packCount),
        dotCount = packCount,
        dotsFilled = packCount,
        buttonLabel = stringResource(R.string.store_card_open),
        buttonActive = true,
        buttonTag = "store_entry_open",
        window = { StoreCardWindow() }
    )
}

/**
 * Ikmal Deposu kartinin pencere ICERIGI — bilerek kucuk ve DEGISTIRILEBILIR:
 * paket basina gorsel gelince yalnizca bu govde degisir, cerceve/bandlar degil.
 */
@Composable
private fun BoxScope.StoreCardWindow() {
    Image(
        painter = painterResource(R.drawable.spr_ic_coins),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(0.62f)
    )
}

// =============================================================================
// YUKSELTME KARTI
// =============================================================================

@Composable
private fun UpgradeCard(
    line: UpgradeLine,
    progress: CampaignProgressImpl,
    onBought: () -> Unit
) {
    val upgrades = progress.metaUpgrades
    val rank = upgrades.rankOf(line)
    val decision = progress.purchaseDecision(line)
    val satinAlinabilir = decision is PurchaseDecision.Allowed

    // FIYAT / DURUM etiketi.
    val etiket = when (decision) {
        is PurchaseDecision.Allowed -> stringResource(R.string.shop_buy, decision.price)
        is PurchaseDecision.MaxRank -> stringResource(R.string.shop_max)
        is PurchaseDecision.InsufficientFunds ->
            stringResource(R.string.shop_short, decision.shortfall)
        is PurchaseDecision.RankGated ->
            stringResource(R.string.shop_gated, decision.requiredClearedLevel)
        else -> stringResource(R.string.shop_reserve_blocked)
    }

    ShopCardFrame(
        cardTag = "upgrade_card_${line.name}",
        onClick = if (satinAlinabilir) {
            { if (progress.buyUpgrade(line) is PurchaseDecision.Allowed) onBought() }
        } else null,
        // KADEME — numara dairesinde.
        number = stringResource(R.string.level_number, rank),
        title = stringResource(lineNameRes(line)),
        // Bir SONRAKI kademenin toplam etkisi.
        effect = stringResource(
            lineEffectRes(line),
            effectValue(line, (rank + 1).coerceAtMost(line.maxRank))
        ),
        shield = stringResource(R.string.shop_rank, rank, line.maxRank),
        dotCount = line.maxRank,
        dotsFilled = rank,
        buttonLabel = etiket,
        buttonActive = satinAlinabilir,
        buttonTag = "upgrade_buy_${line.name}",
        window = {
            // IKON — oyundan TANIDIK sprite; kupurun aksine kenardan kenara
            // degil ORTALI: sprite'lar dikdortgen doldurmaz, Fit ile ortalanir.
            Image(
                painter = painterResource(lineIconRes(line)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.72f)
            )
        }
    )
}

/**
 * Hattin pencerede gosterilen ikonu — OYUNDAN TANIDIK sprite'lar, yeni cizim
 * degil: oyuncu "Ates Gucu"nun topun hasari oldugunu savastaki ayni gorselden
 * okur.
 */
private fun lineIconRes(line: UpgradeLine): Int = when (line) {
    UpgradeLine.FIREPOWER -> R.drawable.spr_tower_heavy_cannon
    UpgradeLine.OPTICS -> R.drawable.spr_ic_target
    UpgradeLine.STARTING_SUPPLY -> R.drawable.spr_ic_supply_crate
    UpgradeLine.FORTIFICATION -> R.drawable.spr_ic_base_health
    UpgradeLine.SALVAGE -> R.drawable.spr_ic_sell
}

private fun lineNameRes(line: UpgradeLine): Int = when (line) {
    UpgradeLine.FIREPOWER -> R.string.shop_line_firepower
    UpgradeLine.OPTICS -> R.string.shop_line_optics
    UpgradeLine.STARTING_SUPPLY -> R.string.shop_line_supply
    UpgradeLine.FORTIFICATION -> R.string.shop_line_fortification
    UpgradeLine.SALVAGE -> R.string.shop_line_salvage
}

private fun lineEffectRes(line: UpgradeLine): Int = when (line) {
    UpgradeLine.FIREPOWER -> R.string.shop_line_firepower_fx
    UpgradeLine.OPTICS -> R.string.shop_line_optics_fx
    UpgradeLine.STARTING_SUPPLY -> R.string.shop_line_supply_fx
    UpgradeLine.FORTIFICATION -> R.string.shop_line_fortification_fx
    UpgradeLine.SALVAGE -> R.string.shop_line_salvage_fx
}

/**
 * Verilen kademedeki TOPLAM etki.
 *
 * Deger `MetaUpgrades`'in KENDI turetilmis alanlarindan okunur — dukkan kendi
 * yukseltme matematigini yazmaz. Ayni hesabi iki yerde tutmak, panelde "+%9"
 * gosterip oyunda +%12 uygulamak gibi sessiz tutarsizliklar uretir (AEHP
 * hatasinin sebebi tam olarak buydu).
 */
private fun effectValue(line: UpgradeLine, rank: Int): Int {
    val m = MetaUpgrades().withRank(line, rank)
    return when (line) {
        UpgradeLine.FIREPOWER -> ((m.damageMultiplier - 1.0) * 100).toInt()
        UpgradeLine.OPTICS -> ((m.rangeMultiplier - 1.0) * 100).toInt()
        UpgradeLine.STARTING_SUPPLY -> m.startingSupply
        UpgradeLine.FORTIFICATION -> m.maxBaseHealth
        UpgradeLine.SALVAGE -> (m.salvageRatio * 100).toInt()
    }
}

/**
 * Prestij Nisani karti — tamamen KOZMETIK derin emici.
 *
 * [UpgradeCard] ile ayni cerceve ([ShopCardFrame]); ayri kompozisyon olmasinin
 * sebebi `UpgradeLine`a bagimli olmamasi (bkz. cagri yerindeki not).
 */
@Composable
private fun PrestigeCard(
    progress: CampaignProgressImpl,
    onBought: () -> Unit
) {
    val rank = progress.prestigeRank
    val decision = progress.prestigeDecision()
    val satinAlinabilir = decision is PrestigeDecision.Allowed

    val etiket = when (decision) {
        is PrestigeDecision.Allowed -> stringResource(R.string.shop_buy, decision.price)
        is PrestigeDecision.MaxRank -> stringResource(R.string.shop_max)
        is PrestigeDecision.InsufficientFunds ->
            stringResource(R.string.shop_short, decision.shortfall)
        is PrestigeDecision.ReserveLocked ->
            stringResource(R.string.shop_reserve_blocked)
    }

    ShopCardFrame(
        cardTag = "prestige_card",
        onClick = if (satinAlinabilir) {
            { if (progress.buyPrestige()) onBought() }
        } else null,
        number = stringResource(R.string.level_number, rank),
        title = stringResource(R.string.prestige_title),
        effect = if (rank >= EconomyConfig.PRESTIGE_MAX) {
            stringResource(R.string.prestige_effect_max)
        } else {
            stringResource(
                R.string.prestige_effect_next,
                stringResource(prestigeNameRes(rank + 1))
            )
        },
        shield = stringResource(
            R.string.prestige_rank_label, rank, EconomyConfig.PRESTIGE_MAX
        ),
        dotCount = EconomyConfig.PRESTIGE_MAX,
        dotsFilled = rank,
        buttonLabel = etiket,
        buttonActive = satinAlinabilir,
        buttonTag = "prestige_buy",
        window = {
            Image(
                painter = painterResource(R.drawable.spr_ic_victory_star),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.62f)
            )
        }
    )
}

/** Nisan adi — kademe 1..7. */
fun prestigeNameRes(rank: Int): Int = when (rank.coerceIn(1, EconomyConfig.PRESTIGE_MAX)) {
    1 -> R.string.prestige_name_1
    2 -> R.string.prestige_name_2
    3 -> R.string.prestige_name_3
    4 -> R.string.prestige_name_4
    5 -> R.string.prestige_name_5
    6 -> R.string.prestige_name_6
    else -> R.string.prestige_name_7
}
