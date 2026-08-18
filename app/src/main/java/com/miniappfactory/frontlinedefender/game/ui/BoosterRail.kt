package com.miniappfactory.frontlinedefender.game.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.audio.rememberHaptics
import com.miniappfactory.frontlinedefender.game.economy.BoosterCurrency
import com.miniappfactory.frontlinedefender.game.economy.BoosterDecision
import com.miniappfactory.frontlinedefender.game.economy.BoosterType
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.boosterCooldownMs
import com.miniappfactory.frontlinedefender.game.economy.boostersAvailableAt
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.ui.theme.SleekBorderDark
import com.miniappfactory.frontlinedefender.ui.theme.SleekDarkBg
import com.miniappfactory.frontlinedefender.ui.theme.SleekGold
import com.miniappfactory.frontlinedefender.ui.theme.SleekGoldBg
import com.miniappfactory.frontlinedefender.ui.theme.SleekPrimaryGreen
import com.miniappfactory.frontlinedefender.ui.theme.SleekRedBg
import com.miniappfactory.frontlinedefender.ui.theme.SleekRedText
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceCard
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceHeader
import com.miniappfactory.frontlinedefender.ui.theme.SleekTextAccent
import kotlinx.coroutines.delay

/**
 * Faz 12 — SAVAS ICI GUCLENDIRICI RAYI.
 *
 * ---------------------------------------------------------------------------------
 * NEDEN SAG KENARDA, ALTA CAPALI
 * ---------------------------------------------------------------------------------
 * BottomCenter DOLU: [TowerBuildBar] ve [SelectedTowerInspector] ikisi de
 * `fillMaxWidth()` cekmece. Olculen yukseklikleri 63 dp ve 56 dp; ray 72 dp
 * capayla en yuksek cekmecenin 9 dp ustunde kalir, yani cekmece acilinca ray
 * YERINDEN OYNAMAZ (parmagin altinda duzen kaymasi yok).
 *
 * Sag serit build pad'lerle de cakismaz: `LevelGeometry`teki 134 pad'in en
 * sagdakisi normX = 0.8716, `GameConfig.TAP_RADIUS_REF_PX = 46` ile dokunma
 * dairesinin sag kenari 0.8956 x alan genisligi. Kalan sag serit 0.1044 x W;
 * 66 dp ray icin W >= 632 dp, 56 dp ray icin W >= 536 dp gerekir. Pratik taban
 * cihaz 640 dp oldugu icin [WIDE_BAND_DP] esiginin altinda dar bant kullanilir.
 *
 * ---------------------------------------------------------------------------------
 * KONUMSAL KARARLILIK
 * ---------------------------------------------------------------------------------
 * Kilitli guclendirici hic cizilmez ve dizilim ALTTAN YUKARI, artan unlockLevel
 * sirasindadir (L2 Acil Tedarik en altta, L7 Us Tamiri en ustte). Bolum 4'te
 * Hava Destegi acildiginda Acil Tedarik'in yeri KIPIRDAMAZ; kas hafizasi
 * kampanya boyunca bozulmaz.
 *
 * ---------------------------------------------------------------------------------
 * KARAR MANTIGI BURADA YOK
 * ---------------------------------------------------------------------------------
 * Butonun cizim durumu tamamen [CampaignProgressImpl.boosterDecision]'dan gelir;
 * bu dosya kendi bakiye/limit/bekleme/hedef karsilastirmasini YAPMAZ. "Hedefsiz
 * hava destegi" kapisi da ekonomi katmanindadir; bu dosya yalnizca sahadaki
 * dusman sayisini GIRDI olarak gecer.
 */

/** Genis bant esigi. Altinda 48 dp buton, ustunde 56 dp. */
private const val WIDE_BAND_DP = 680

/** Ucretli kullanimda "tekrar bas ve onayla" penceresi. */
private const val ARM_WINDOW_MS = 3_000L

/** Ret/onay mesajinin ekranda kalma suresi. */
private const val MESSAGE_MS = 2_200L

/** Bekleme sayacinin tazelenme araligi. 60 Hz DEGIL — HUD her karede recompose olmaz. */
private const val TICK_MS = 250L

/**
 * Guclendirici ikonlari.
 *
 * Ikisi artik GUCLENDIRICIYE OZEL cizilmis asset kullaniyor; onceki
 * "anlami zaten kurulmus mevcut sprite'lardan gecici secim" notu bu ikisi
 * icin GECERSIZ:
 *   Acil Tedarik -> spr_ic_booster_supply_drop (paraşütlü tedarik sandigi)
 *   Us Tamiri    -> spr_ic_booster_base_repair (onarim glifi)
 *
 * Hava Destegi HALA odunc: pakette ona ait bir ikon gelmedi ve fuze glifi
 * (havadan gelen agir hasar) anlami tasiyan en yakin mevcut sprite. Ozel
 * ikonu uretildiginde tek satirda degisir.
 */
@DrawableRes
private fun boosterSpriteRes(type: BoosterType): Int = when (type) {
    BoosterType.EMERGENCY_SUPPLY -> R.drawable.spr_ic_booster_supply_drop
    BoosterType.AIR_SUPPORT -> R.drawable.spr_fx_missile
    BoosterType.BASE_REPAIR -> R.drawable.spr_ic_booster_base_repair
}

/**
 * Guclendiricinin gorunen adi. Composable DEGIL: reklam odulu callback'i
 * kompozisyon disinda calisir ve adi `context.getString` ile okur.
 */
@StringRes
fun boosterNameRes(type: BoosterType): Int = when (type) {
    BoosterType.EMERGENCY_SUPPLY -> R.string.booster_emergency_supply_name
    BoosterType.AIR_SUPPORT -> R.string.booster_air_support_name
    BoosterType.BASE_REPAIR -> R.string.booster_base_repair_name
}

@Composable
private fun boosterName(type: BoosterType): String = stringResource(boosterNameRes(type))

/** Butonun gorsel durumu — [BoosterDecision] ve silahlanma birlikte belirler. */
private enum class RailVisual { READY_PAID, READY_AD, ARMED, BLOCKED, SPENT, COOLDOWN }

@Composable
fun BoosterRail(
    gameEngine: GameEngine,
    progress: CampaignProgressImpl,
    /**
     * Reklam yolu istendi. Sheet'i ve odul akisini [com.miniappfactory.frontlinedefender.game.ui.GameScreen]
     * yurutur — reklam acilinca oyun PAUSED'a duser ve aktivasyonun SIRASI
     * kritiktir (bkz. GameScreen'deki `activateBoosterViaAd`).
     */
    onAdBoosterRequested: (BoosterType) -> Unit,
    modifier: Modifier = Modifier
) {
    val gameState by gameEngine.gameState.collectAsState()
    val levelId by gameEngine.currentLevelId.collectAsState()
    val supply by gameEngine.gold.collectAsState()
    val lives by gameEngine.lives.collectAsState()
    val battleEpoch by gameEngine.battleEpoch.collectAsState()

    // Ray YALNIZCA gercek oynanista gorunur. Modal, duraklama ve sonuc
    // ekranlarinda yok: acceptsBattlefieldInput() ile ayni kapi, cunku
    // GameEngine.applyBoosterActivation da tam olarak bu kapiya bakiyor.
    val visible = gameState == GameState.PREPARATION || gameState == GameState.WAVE_RUNNING
    val types = remember(levelId) { boostersAvailableAt(levelId) }
    if (!visible || types.isEmpty()) return

    val haptics = rememberHaptics()

    /** Silahlanmis (onay bekleyen) guclendirici. */
    var armed by remember { mutableStateOf<BoosterType?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    /** Yildiz notrlugu uyarisi savas basina BIR KEZ gosterilir. */
    var starNoteShown by remember { mutableStateOf(false) }

    // Yeni savas: sayaclar ve gecici durum sifirlanir. gameState'i dinlemek
    // YETMEZ — PAUSED->restart ve DEFEAT->retry akislarinda PREPARATION degeri
    // tekrar gelir ve degisim gozlenmez.
    LaunchedEffect(battleEpoch) {
        armed = null
        message = null
        starNoteShown = false
    }

    val armedMsg = stringResource(R.string.booster_msg_armed)
    val noTargetsMsg = stringResource(R.string.booster_msg_no_targets)
    val starNoteMsg = stringResource(R.string.booster_repair_star_note)

    // Kararlar. `tick` burada OKUNUR ki bekleme sayaci aksin.
    @Suppress("UNUSED_EXPRESSION") tick
    val decisions = types.associateWith { type ->
        // Ucretli hak duruyorsa ucretli yol, tukendiyse reklam yolu denenir.
        // Bu kural PaidPathNotExhausted / PaidPathUnavailable dallarini
        // YAPISAL OLARAK imkansiz kilar: UI onlari hic gormez.
        val state = progress.boosterState
        val viaAd = state == null ||
            !(type.hasPaidPath && state.paidUsesOf(type) < type.paidUsesPerBattle)
        viaAd to progress.boosterDecision(
            type = type,
            viaAd = viaAd,
            supplyOnHand = supply,
            baseHealth = lives,
            maxBaseHealth = gameEngine.maxLives,
            enemiesOnField = gameEngine.enemies.size
        )
    }

    val needsTicker = armed != null || decisions.values.any { it.second is BoosterDecision.Cooldown }
    LaunchedEffect(needsTicker) {
        while (needsTicker) {
            delay(TICK_MS)
            tick++
        }
    }

    LaunchedEffect(armed) {
        if (armed != null) {
            delay(ARM_WINDOW_MS)
            armed = null // Sessiz iptal: yanlis basma cezalandirilmaz.
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_MS)
            message = null
        }
    }

    /** Ucretli kullanimi gercekten uygular. */
    fun commitPaid(type: BoosterType) {
        armed = null

        // Kapi ucretten ONCE: applyBoosterActivation da ayni kontrole bakiyor
        // ve false donerse ekonomi tarafi parayi ZATEN kesmis olurdu.
        if (!gameEngine.acceptsBattlefieldInput()) return

        // Sahadaki dusman sayisi ekonomi katmanina GIRDI olarak gecer: hedefsiz
        // hava destegini reddetme karari orada verilir (BoosterDecision.NoEffect),
        // dolayisiyla ucret kesilmez, kullanim hakki yanmaz, bekleme baslamaz.
        val activation = progress.activateBooster(
            type = type,
            viaAd = false,
            supplyOnHand = gameEngine.gold.value,
            baseHealth = gameEngine.lives.value,
            maxBaseHealth = gameEngine.maxLives,
            enemiesOnField = gameEngine.enemies.size
        )
        val applied = gameEngine.applyBoosterActivation(activation)

        // Onay ile ret AYRI dokunsal desenlerdir: onay tek agir darbe, ret
        // cift darbe. Oyuncu ekrana bakmadan da odemenin gecip gecmedigini
        // ayirt edebilmeli.
        if (applied) haptics.onBoosterConfirmed() else haptics.onActionRejected()

        // Yaris penceresi: buton cizildikten sonra son dusman olmus olabilir
        // (silahlanma penceresi 3 sn). Ekonomi katmani zaten reddetti; oyuncuya
        // "neden olmadi" sessizce birakilmaz.
        if (type == BoosterType.AIR_SUPPORT && activation.decision is BoosterDecision.NoEffect) {
            message = noTargetsMsg
        }

        if (applied && type == BoosterType.BASE_REPAIR && !starNoteShown) {
            // Oyuncu 120-540 coin odedi; onarilan canin yildiza SAYILMADIGINI
            // sonradan ogrenmesi aldatilmis hissi yaratir.
            starNoteShown = true
            message = starNoteMsg
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth.value >= WIDE_BAND_DP
        val btn: Dp = if (wide) 56.dp else 48.dp
        val gap: Dp = if (wide) 10.dp else 8.dp
        val railEnd: Dp = if (wide) 10.dp else 8.dp
        val iconSize: Dp = if (wide) 28.dp else 24.dp
        val chipHeight: Dp = if (wide) 18.dp else 16.dp
        val corner: Dp = if (wide) 14.dp else 12.dp

        val current = message
        if (current != null) {
            // Mesaj rayin SOLUNA acilir — parmagin altina degil. Toast
            // KULLANILMAZ: yatayda alt-ortada belirip TowerBuildBar'i orterdi.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = railEnd + btn + 8.dp, bottom = 72.dp)
                    .widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SleekSurfaceHeader.copy(alpha = 0.92f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("booster_caption")
            ) {
                AutoShrinkText(
                    text = current,
                    color = SleekTextAccent,
                    maxFontSize = 11.sp,
                    minFontSize = 8.sp,
                    maxLines = 2,
                    textAlign = TextAlign.End,
                    resetKey = current
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.Bottom),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = railEnd, bottom = 72.dp)
                .testTag("booster_rail")
        ) {
            // Alttan yukari artan unlockLevel -> Column'da tersten cizilir.
            types.sortedByDescending { it.unlockLevel }.forEach { type ->
                val (viaAd, decision) = decisions.getValue(type)
                // Ret mesaji KOMPOZISYON sirasinda cozulur: `blockedMessage`
                // bir @Composable'dir (stringResource kullanir) ve tiklama
                // lambda'sindan cagrilamaz.
                val blockedMsg = if (decision is BoosterDecision.Allowed) {
                    ""
                } else {
                    blockedMessage(decision, type)
                }
                BoosterButton(
                    type = type,
                    decision = decision,
                    viaAd = viaAd,
                    isArmed = armed == type,
                    buttonSize = btn,
                    iconSize = iconSize,
                    chipHeight = chipHeight,
                    corner = corner,
                    onClick = {
                        when {
                            decision !is BoosterDecision.Allowed -> {
                                haptics.onActionRejected()
                                armed = null
                                message = blockedMsg
                            }
                            // Bedava yol silahlanmaz: yanlis basmanin maliyeti yok,
                            // sheet zaten onay adimidir.
                            viaAd -> {
                                haptics.onUiTap()
                                armed = null
                                onAdBoosterRequested(type)
                            }
                            armed != type -> {
                                // SILAHLANMA onaydan FARKLI hissetmeli: bu bir
                                // "bir daha bas" uyarisi, sonucun kendisi degil.
                                haptics.onBoosterArmed()
                                armed = type
                                message = armedMsg
                            }
                            else -> commitPaid(type)
                        }
                    }
                )
            }
        }
    }
}

/** Ret sebebinin oyuncuya gosterilecek karsiligi. Sebepler AYRI AYRI ayirt edilir. */
/**
 * @param type NoEffect'in SEBEBI guclendiriciye gore degisir: hava destegi icin
 *   "sahada hedef yok", us tamiri icin "can zaten tam". Ayni dal iki farkli
 *   ekonomi kuralini temsil ettigi icin mesaj tipe gore secilir.
 */
@Composable
private fun blockedMessage(decision: BoosterDecision, type: BoosterType): String = when (decision) {
    is BoosterDecision.Cooldown ->
        stringResource(R.string.booster_msg_cooldown, ((decision.remainingMs + 999) / 1000).toInt())
    is BoosterDecision.InsufficientSupply ->
        stringResource(R.string.booster_msg_need_supply, decision.shortfall)
    is BoosterDecision.InsufficientCoins ->
        stringResource(R.string.booster_msg_need_coin, decision.shortfall)
    // Rezerv kilidi ile "coin yetmiyor" AYNI gorunmez: biri bakiye sorunu,
    // digeri soft-lock garantisinin bilincli korumasi.
    is BoosterDecision.ReserveLocked -> stringResource(R.string.booster_msg_reserve)
    is BoosterDecision.NoEffect -> when (type) {
        BoosterType.AIR_SUPPORT -> stringResource(R.string.booster_msg_no_targets)
        else -> stringResource(R.string.booster_msg_no_repair)
    }
    is BoosterDecision.DailyAdLimitReached -> stringResource(R.string.booster_msg_daily_ad)
    is BoosterDecision.AdLimitReached, is BoosterDecision.PaidLimitReached ->
        stringResource(R.string.booster_msg_spent)
    is BoosterDecision.PaidPathNotExhausted -> stringResource(R.string.booster_msg_paid_first)
    else -> stringResource(R.string.booster_msg_spent)
}

@Composable
private fun BoosterButton(
    type: BoosterType,
    decision: BoosterDecision,
    viaAd: Boolean,
    isArmed: Boolean,
    buttonSize: Dp,
    iconSize: Dp,
    chipHeight: Dp,
    corner: Dp,
    onClick: () -> Unit
) {
    val visual = when {
        isArmed -> RailVisual.ARMED
        decision is BoosterDecision.Allowed && viaAd -> RailVisual.READY_AD
        decision is BoosterDecision.Allowed -> RailVisual.READY_PAID
        decision is BoosterDecision.Cooldown -> RailVisual.COOLDOWN
        decision is BoosterDecision.AdLimitReached ||
            decision is BoosterDecision.DailyAdLimitReached ||
            decision is BoosterDecision.PaidLimitReached -> RailVisual.SPENT
        else -> RailVisual.BLOCKED
    }

    val fill = when (visual) {
        RailVisual.READY_PAID, RailVisual.READY_AD, RailVisual.ARMED -> SleekPrimaryGreen
        RailVisual.SPENT -> SleekSurfaceCard.copy(alpha = 0.45f)
        else -> SleekSurfaceCard
    }
    val borderColor = when (visual) {
        RailVisual.ARMED -> SleekGold
        RailVisual.READY_PAID, RailVisual.READY_AD -> SleekTextAccent
        RailVisual.SPENT -> SleekBorderDark
        else -> SleekTextAccent.copy(alpha = 0.5f)
    }
    val borderWidth = when (visual) {
        RailVisual.ARMED -> 2.5.dp
        RailVisual.SPENT -> 1.dp
        else -> 1.5.dp
    }
    val iconAlpha = when (visual) {
        RailVisual.READY_PAID, RailVisual.READY_AD, RailVisual.ARMED -> 1.0f
        RailVisual.COOLDOWN -> 0.55f
        RailVisual.SPENT -> 0.25f
        else -> 0.40f
    }
    val scale by animateFloatAsState(
        targetValue = if (isArmed) 1.04f else 1.0f,
        label = "booster_arm_scale"
    )

    val name = boosterName(type)
    val chip = chipText(type, decision, viaAd, isArmed)
    val description = when {
        isArmed -> stringResource(R.string.booster_desc_armed, name)
        decision is BoosterDecision.Allowed && viaAd -> stringResource(R.string.booster_desc_ad, name)
        decision is BoosterDecision.Allowed -> stringResource(R.string.booster_desc_ready, name, chip)
        decision is BoosterDecision.Cooldown -> stringResource(
            R.string.booster_desc_cooldown, name, ((decision.remainingMs + 999) / 1000).toInt()
        )
        else -> stringResource(R.string.booster_desc_blocked, name, blockedMessage(decision, type))
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .size(buttonSize)
            .clip(RoundedCornerShape(corner))
            .background(fill)
            .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(corner))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("booster_btn_${type.name.lowercase()}")
    ) {
        // Bekleme halkasi. Sayisal saniye alt seritte de var: 45-60 sn'lik
        // beklemeler yalnizca yayla okunamaz.
        if (decision is BoosterDecision.Cooldown) {
            val total = boosterCooldownMs(type).coerceAtLeast(1L)
            val remaining = (decision.remainingMs.toFloat() / total).coerceIn(0f, 1f)
            CooldownRing(remaining = remaining, color = SleekGold, buttonSize = buttonSize)
        }

        SpriteIcon(
            id = boosterSpriteRes(type),
            size = iconSize,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (buttonSize > 50.dp) 7.dp else 5.dp)
                .alpha(iconAlpha)
        )

        if (chip.isNotEmpty()) {
            val chipBg = when {
                isArmed -> SleekGold
                decision is BoosterDecision.Allowed && viaAd -> SleekSurfaceCard
                decision is BoosterDecision.Allowed -> SleekGoldBg
                decision is BoosterDecision.Cooldown -> SleekSurfaceCard
                else -> SleekRedBg
            }
            val chipFg = when {
                isArmed -> SleekDarkBg
                decision is BoosterDecision.Allowed && viaAd -> SleekTextAccent
                decision is BoosterDecision.Allowed -> SleekGold
                decision is BoosterDecision.Cooldown -> SleekTextAccent
                else -> SleekRedText
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(chipHeight)
                    .background(chipBg)
                    .testTag("booster_chip_${type.name.lowercase()}")
            ) {
                // Kirpma DEGIL punto kucultme: en uzun aday "540 COIN" 48 dp
                // butonun 44 dp ic genisligine 9 sp'de 0.8 dp payla siginiyor.
                AutoShrinkText(
                    text = chip,
                    color = chipFg,
                    maxFontSize = 9.sp,
                    minFontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    resetKey = chip
                )
            }
        }
    }
}

/** Alt serit TEK elemanlidir: ayni anda fiyat + reklam + sayac gosterilmez. */
@Composable
private fun chipText(
    type: BoosterType,
    decision: BoosterDecision,
    viaAd: Boolean,
    isArmed: Boolean
): String = when {
    isArmed -> stringResource(R.string.booster_chip_confirm)
    decision is BoosterDecision.Allowed && viaAd -> stringResource(R.string.booster_chip_ad)
    decision is BoosterDecision.Allowed && decision.currency == BoosterCurrency.COIN ->
        // Coin cipte KELIME olarak yazilir ("540 COIN"), ikonla degil.
        //
        // ⚠ GEREKCE DEGISTI: eskiden bu bir ZORUNLULUKTU, cunku HUD'daki
        // Tedarik rozeti de coin glifini kullaniyordu ve iki para birimi ayni
        // ikonla gosterilince karisiyordu. `HUDOverlay` artik
        // `spr_ic_supply_crate` kullaniyor, yani o cakisma YOK.
        //
        // Kelime kalmasi artik bir TERCIH: cipin ic genisligi 44 dp ve ikon +
        // sayi birlikte sigmiyor (en uzun aday "540 COIN" 9 sp'de zaten
        // 0.8 dp payla giriyor). Kelime ayrica hicbir boyutta belirsiz degil.
        // Yani istenirse ikonlanabilir; yanlis bir kisit olarak mirasla
        // alinmasin.
        stringResource(R.string.booster_chip_price_coin, decision.price)
    decision is BoosterDecision.Allowed -> stringResource(
        R.string.booster_chip_price_supply, decision.price
    )
    decision is BoosterDecision.Cooldown -> stringResource(
        R.string.booster_chip_cooldown, ((decision.remainingMs + 999) / 1000).toInt()
    )
    decision is BoosterDecision.InsufficientSupply ->
        stringResource(R.string.booster_chip_short, decision.shortfall)
    decision is BoosterDecision.InsufficientCoins ->
        stringResource(R.string.booster_chip_short, decision.shortfall)
    decision is BoosterDecision.ReserveLocked -> stringResource(R.string.booster_chip_reserve)
    decision is BoosterDecision.NoEffect -> stringResource(R.string.booster_chip_spent)
    else -> ""
}

/** Butonun icine oturan, saat yonunde bosalan bekleme halkasi. */
@Composable
private fun CooldownRing(remaining: Float, color: Color, buttonSize: Dp) {
    Canvas(modifier = Modifier.size(buttonSize)) {
        val stroke = 2.5.dp.toPx()
        val inset = stroke
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = -360f * remaining,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(width = stroke)
        )
    }
}
