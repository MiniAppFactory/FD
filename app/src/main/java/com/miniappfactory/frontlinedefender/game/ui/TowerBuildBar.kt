package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.animation.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.audio.rememberHaptics
import com.miniappfactory.frontlinedefender.game.economy.BattleTelemetry
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.ui.theme.*
import kotlinx.coroutines.delay

/**
 * ---------------------------------------------------------------------------
 * Faz 10 — KILIT + BIRAKMA ONIZLEMESI
 * ---------------------------------------------------------------------------
 * 1. Kilitli kule GIZLENMEZ, pasif gosterilir ve hangi bolumde acilacagi
 *    yazilir. Gizlemek oyuncuya hedef vermez; "bolum 7'de fuze rampasi
 *    aciliyor" bilgisi ilerlemeyi anlamli kilar (testci: "ilk bolumden
 *    itibaren her seyi acmak dogru degil").
 *
 * 2. Bir kart BASILI tutuldugunda secili pad'in etrafinda O KULENIN gercek
 *    menzil halkasi cizilir (`GameEngine.previewTowerType`). Menziller artik
 *    150 ile 270 ref-px arasinda degistigi icin tek bir notr halka yanlis
 *    bilgi verirdi — ve Frost Field'in tum degeri genis kapsama alani.
 *
 * 3. BOLUM DEGISTIRICILERI (`GameConfig.LevelModifiers`). Kartin ikinci satiri
 *    artik "fiyat ya da kilit" degil, **hangi engel varsa ONUN adi**:
 *    bolum kilidi · kadro disi · mevzi tavani dolu · dalga suruyor. Sebepler
 *    AYIRT EDILEBILIR olmak zorunda; "Lv 12'de acilir" ile "bu harekatta yok"
 *    oyuncu icin bambaska iki bilgidir ve ayni gorunurlerse oyuncuya var
 *    olmayan bir hedef gosterilir.
 *
 *    Kural sahibi UI DEGIL MOTOR: kart `GameEngine.buildRejectionFor` sorar.
 *    YAPISAL engeller (kilit / kadro / para) kartı tiklanamaz birakir — sebep
 *    zaten SUREKLI yazili. GECICI engeller (tavan / catisma) kartı TIKLANABILIR
 *    birakir ki dokunus cevapsiz kalmasin: ret desenli titresim + [BuildRejectionStrip].
 *
 * LOKALIZASYON: kilit etiketi ("LOCKED · Lv N") hâlâ Ingilizce SABIT (bkz.
 * docs/TOWER_REBALANCE.md); degistirici etiketleri `strings.xml` + `values-tr`
 * ikilisinden gelir.
 *
 * Faz 3'te iki sey degisti:
 *
 * 1. Kart ikonu artik soyut bir Material vektoru degil, kulenin OYNANISTA
 *    gorunen sprite'i. Oyuncu ne insa ettigini gorerek secer.
 *
 * 2. Panel KOMPAKT hale getirildi. Onceki iki satirli duzen cihazda 486 px
 *    yer kapliyordu (2220x1080 ekranin %45'i) ve 10 build pad'in 6'sini,
 *    ustune bir de secili pad'in menzil on-izlemesini kapatiyordu — yani
 *    "birakma onizlemesi her zaman gorunur" kurali ihlal ediliyordu.
 *    Olculen yeni yukseklik ~132 px; en alttaki pad (normY=0.80) ve halkasinin
 *    buyuk kismi acikta kalir.
 *
 * @param telemetry gorev olcumu ([BattleTelemetry]). **Varsayilani YOKTUR**:
 *   `d_v_build15` ve `d_s_all_towers` gorevlerinin tek besleyicisi bu cagri
 *   yeridir, dolayisiyla baglanti kopmasi derleme hatasi olmali — sessiz bir
 *   `null` degil (kapi tam bu yuzden aylarca kapali kalmisti, bkz.
 *   `BATTLE_TELEMETRY_WIRED`).
 */
@Composable
fun TowerBuildBar(
    gameEngine: GameEngine,
    telemetry: BattleTelemetry,
    modifier: Modifier = Modifier
) {
    val selectedBuildSpot by gameEngine.selectedBuildSpot.collectAsState()
    val gold by gameEngine.gold.collectAsState()
    val levelId by gameEngine.currentLevelId.collectAsState()
    // BOLUM DEGISTIRICILERI: kart durumu artik yalnizca kilit + paraya degil,
    // savas durumuna (DONMUS MEVZI) ve sahadaki kule sayisina (MEVZI TAVANI) de
    // bagli. Ikisi de BURADA toplanir, yoksa kartlar bayat cizilir: dalga
    // baslayinca "CATISMADA" etiketi hic gorunmez, tavan dolunca kart hala
    // kurulabilir gibi durur.
    val gameState by gameEngine.gameState.collectAsState()
    val towerCount by gameEngine.towerCount.collectAsState()
    val haptics = rememberHaptics()

    AnimatedVisibility(
        visible = selectedBuildSpot != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = SleekDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, SleekBorderDark),
            shadowElevation = 16.dp,
            // YUKSEKLIK ARTIK TEK YERDE. `GameCanvas`in "ortulen secim
            // hayaleti" bu cekmecenin ust kenarini capa olarak kullaniyor ve
            // capayi `GameConfig.BUILD_DRAWER_HEIGHT_DP`den hesapliyor. Sabit
            // buradan OKUNMASAYDI ic bosluklarda yapilan bir degisiklik
            // cekmeceyi buyutur, hayaletin capasi sessizce kayar ve plaka
            // cekmecenin ALTINDA kalirdi — hicbir test kirilmadan.
            //
            // `defaultMinSize`, `height` DEGIL: buyuk sistem yazi olceginde
            // icerik bu sinirin ustune cikarsa kirpilmasin. Iki degerin
            // gercekten esit oldugunu `MissionTelemetryWiringTest` OLCEREK
            // dogruluyor, yani sapma sessiz kalmiyor.
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = GameConfig.BUILD_DRAWER_HEIGHT_DP.dp)
                .testTag("build_drawer")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameConfig.TowerType.values().forEach { towerType ->
                    val spec = GameConfig.TOWER_SPECS[towerType]!!
                    // KURALIN TEK SAHIBI MOTOR. Kart kendi kilit/kadro/tavan
                    // matematigini YAPMAZ, motorun kapisini sorar — panelin
                    // dedigi ile motorun yaptigi boylece ayrisamaz.
                    // Anahtarlar BAGIMLILIK BEYANIDIR: kartin durumu bu dort
                    // degerin fonksiyonudur ve dordu de yukarida toplaniyor.
                    // Biri listeden dusarse kart bayat cizilir — orn. tavan
                    // dolunca kart hala kurulabilir gorunur.
                    val rejection = remember(towerType, levelId, gold, gameState, towerCount) {
                        gameEngine.buildRejectionFor(towerType)
                    }
                    TowerBuildCard(
                        spec = spec,
                        rejection = rejection,
                        onBuild = {
                            if (rejection == null) {
                                // HAPTIK, SES VE GORSEL AYNI KAREDE. Titresim
                                // `buildTower`dan ONCE tetiklenir: motor cagrisi
                                // kule listesini ve altini guncelleyip
                                // recomposition baslatir, dokunsal geri bildirimi
                                // onun ARKASINA koymak parmagin altinda
                                // olculebilir bir gecikme yaratirdi.
                                haptics.onTowerBuilt()
                                // GOREV OLCUMU yalnizca motor GERCEKTEN kurduysa.
                                // `buildTower` kilit/Tedarik/girdi kontrollerinde
                                // false donebilir; reddedilen bir dokunusu saymak
                                // `d_v_build15`i bedava doldururdu.
                                if (gameEngine.buildTower(towerType)) {
                                    telemetry.noteTowerBuilt(towerType.name)
                                }
                            } else {
                                // GECICI kisitlar (tavan doldu / dalga suruyor)
                                // TIKLANABILIR kalir: dokunus CEVAPSIZ kalmaz,
                                // ret desenli titresim + mesaj seridi ile ayni
                                // karede geri doner. Motor yine son sozu soyler
                                // ve SEBEBI kendisi yayinlar.
                                haptics.onActionRejected()
                                gameEngine.buildTower(towerType)
                            }
                        },
                        onPreview = { pressed ->
                            if (pressed) {
                                haptics.onRangePreviewShown()
                                gameEngine.setPreviewTowerType(towerType)
                            } else if (gameEngine.previewTowerType.value == towerType) {
                                // Yalnizca KENDI onizlemesini kapatir: iki
                                // parmakla iki karta basilirsa birbirini silmez.
                                gameEngine.setPreviewTowerType(null)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                val closeDesc = stringResource(R.string.build_close_desc)
                IconButton(
                    onClick = {
                        haptics.onUiTap()
                        gameEngine.deselectAll()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = closeDesc }
                ) {
                    Text(
                        // Glif kaynakta (translatable=false); ekran okuyucunun
                        // okudugu ad build_close_desc.
                        text = stringResource(R.string.common_close_glyph),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TowerBuildCard(
    spec: GameConfig.TowerStats,
    rejection: GameEngine.BuildRejection?,
    onBuild: () -> Unit,
    onPreview: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val buildable = rejection == null
    // YAPISAL kisit (bu bolumde ASLA kurulamaz) ile GECICI kisit (su an
    // kurulamaz) ayri davranir: ilki tiklanamaz — kartin uzerindeki etiket
    // sebebi zaten SUREKLI gosterir; ikincisi tiklanabilir kalir ki dokunus
    // cevapsiz kalmasin ve oyuncu sebebi anlik olarak gorsun.
    val structural = rejection == GameEngine.BuildRejection.TOWER_LOCKED ||
        rejection == GameEngine.BuildRejection.NOT_IN_LOADOUT ||
        rejection == GameEngine.BuildRejection.INSUFFICIENT_SUPPLY
    val clickable = buildable || !structural
    val cardColor = if (buildable) SleekSurfaceCard else SleekDarkBg
    val borderColor = if (buildable) SleekPrimaryGreen else SleekBorderDark
    // Faz 6: ad GameConfig'ten DEGIL kaynaktan. GameConfig.TowerStats.name
    // kullanilmayan Ingilizce fallback olarak kaldi.
    val towerName = stringResource(spec.type.nameRes())

    // Basili tutma = menzil onizlemesi. `collectIsPressedAsState` tap'i
    // BOZMAZ: onClick yine calisir, sadece basili oldugu sure boyunca halka
    // gorunur. Kilitli kartta onizleme de yok (kurulamayacak seyin halkasi
    // yalan olur).
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed, buildable) {
        onPreview(pressed && buildable)
    }

    Row(
        modifier = modifier
            .background(cardColor, RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                enabled = clickable,
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onBuild
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("build_card_${spec.type.name.lowercase()}"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpriteIcon(
            id = towerSpriteRes(spec.type),
            size = 34.dp,
            contentDescription = towerName,
            // UC AYRI SOLUKLUK, UC AYRI SEBEP: "bu bolumde hic yok" (kilit ya
            // da kadro disi) en soluk, "su an olmaz" (tavan/catisma) orta,
            // "param yetmiyor" en az soluk. Oyuncu karta bakmadan da hangi
            // engelle karsilastigini ayirt edebilmeli.
            modifier = when (rejection) {
                null -> Modifier
                GameEngine.BuildRejection.TOWER_LOCKED,
                GameEngine.BuildRejection.NOT_IN_LOADOUT -> Modifier.alpha(0.25f)
                GameEngine.BuildRejection.INSUFFICIENT_SUPPLY -> Modifier.alpha(0.4f)
                else -> Modifier.alpha(0.33f)
            }
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // TASMA: 4 kart landscape'te ekrani weight(1f) ile boluyor, yani
            // kart genisligi SABIT. "Fuze Rampasi" / "Missile Battery" gibi
            // uzun adlar kirpilirsa oyuncu ne insa ettigini anlamaz -> punto
            // kucultulur (bkz. UiStrings.AutoShrinkText).
            AutoShrinkText(
                text = towerName,
                color = if (buildable) SleekTextAccent else Color.Gray,
                fontWeight = FontWeight.Bold,
                maxFontSize = 11.sp,
                minFontSize = 8.sp,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            // IKINCI SATIR = SEBEP SATIRI. Kurulabilir kartta fiyati, aksi
            // halde ENGELIN ADINI tasir. Bolum kilidi ("Lv 12") ile harekat
            // kisiti ("KADRO DISI") ayni gorunmez: biri ilerleyince acilir,
            // digeri BU BOLUMDE hic acilmaz.
            val blockLabel: String? = when (rejection) {
                GameEngine.BuildRejection.TOWER_LOCKED ->
                    "LOCKED · Lv ${spec.unlockedAtLevel}"
                GameEngine.BuildRejection.NOT_IN_LOADOUT ->
                    stringResource(R.string.build_off_roster)
                GameEngine.BuildRejection.EMPLACEMENT_CAP ->
                    stringResource(R.string.build_slots_full)
                GameEngine.BuildRejection.WAVE_IN_PROGRESS ->
                    stringResource(R.string.build_in_combat)
                // Tedarik yetersizligi kendi satirini KULLANMAZ: fiyat zaten
                // gri cizilir ve oyuncunun gormesi gereken sey fiyatin kendisi.
                GameEngine.BuildRejection.INSUFFICIENT_SUPPLY, null -> null
            }
            if (blockLabel != null) {
                AutoShrinkText(
                    text = blockLabel,
                    color = Color(0xFF9AA5B1),
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 11.sp,
                    minFontSize = 8.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    resetKey = blockLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("build_locked_${spec.type.name.lowercase()}")
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // TEDARIK glifi, COIN degil: insa bedeli savas ici TEDARIK
                    // ile odeniyor. Buraya kadar coin glifi cizildigi icin
                    // oyuncuya yanlis para birimi gosteriliyordu. (Meta para
                    // birimi olan coin'in yeri `UpgradeShopScreen`.)
                    SpriteIcon(
                        id = R.drawable.spr_ic_supply_crate,
                        size = 11.dp,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.build_cost, spec.buildCost),
                        color = if (
                            rejection == GameEngine.BuildRejection.INSUFFICIENT_SUPPLY
                        ) Color.Gray else SleekGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * ---------------------------------------------------------------------------
 * INSA RET SERIDI — "reddedilen bir insa sessiz olamaz"
 * ---------------------------------------------------------------------------
 * Motor bir insayi reddettiginde SEBEBI yayinlar
 * ([GameEngine.buildRejection]); bu serit onu okur ve kisa sure gosterir.
 *
 * Neden motorun akisindan, kartin bildiginden DEGIL: reddin tek dogru sahibi
 * motordur. Ogretici, test ya da ileride eklenecek surukle-birak yolu da ayni
 * kapidan gecer ve ayni mesaji uretir — UI'ye ikinci bir kural kopyasi
 * yazilmaz.
 *
 * Neden `Toast` DEGIL: yatay oyunda toast alt-ortada belirir ve tam olarak
 * insa cekmecesinin uzerine oturur. Serit cekmecenin USTUNDE, oyuncunun
 * parmaginin DISINDA durur (finger occlusion) ve girdi YUTMAZ.
 *
 * Yerlesim `GameConfig.BUILD_DRAWER_HEIGHT_DP`den turetilir: cekmece bir gun
 * buyurse serit onunla birlikte yukari kayar, altinda kalmaz.
 */
@Composable
fun BuildRejectionStrip(
    gameEngine: GameEngine,
    modifier: Modifier = Modifier
) {
    val notice by gameEngine.buildRejection.collectAsState()
    val levelId by gameEngine.currentLevelId.collectAsState()

    // Gorunurluk `notice`in KENDISINDEN degil yerel bir bayraktan gelir:
    // ayni sebep ust uste geldiginde serit yeniden acilmali (notice.serial
    // her seferinde degisir), ama sure dolunca motor durumunu temizlemeden
    // kapanmali.
    var shownSerial by remember { mutableStateOf(0L) }
    LaunchedEffect(notice?.serial) {
        val serial = notice?.serial ?: return@LaunchedEffect
        shownSerial = serial
        delay(REJECTION_MESSAGE_MS)
        if (shownSerial == serial) shownSerial = 0L
    }
    // Yeni savas: kalan mesaj yeni bolumun uzerinde durmasin.
    LaunchedEffect(levelId) { shownSerial = 0L }

    val current = notice?.takeIf { it.serial == shownSerial && shownSerial != 0L }
    val text = current?.let { rejectionMessage(it, gameEngine.levelSpec) }

    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = GameConfig.BUILD_DRAWER_HEIGHT_DP.dp + 10.dp)
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SleekSurfaceHeader.copy(alpha = 0.94f))
                .border(1.dp, SleekRedBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("build_rejection_caption")
        ) {
            AutoShrinkText(
                text = text.orEmpty(),
                color = SleekTextAccent,
                maxFontSize = 12.sp,
                minFontSize = 9.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                resetKey = text
            )
        }
    }
}

/** Serit ne kadar kalir. BoosterRail'in mesaj suresiyle ayni bant. */
private const val REJECTION_MESSAGE_MS = 1800L

/** Ret sebebinin oyuncuya gosterilecek karsiligi. Sebepler AYRI AYRI ayirt edilir. */
@Composable
private fun rejectionMessage(
    notice: GameEngine.BuildRejectionNotice,
    spec: GameConfig.LevelSpec
): String = when (notice.reason) {
    GameEngine.BuildRejection.TOWER_LOCKED -> stringResource(
        R.string.build_msg_locked,
        GameConfig.TOWER_SPECS[notice.type]?.unlockedAtLevel ?: 1
    )
    GameEngine.BuildRejection.NOT_IN_LOADOUT ->
        stringResource(R.string.build_msg_off_roster)
    GameEngine.BuildRejection.WAVE_IN_PROGRESS ->
        stringResource(R.string.build_msg_in_combat)
    GameEngine.BuildRejection.EMPLACEMENT_CAP ->
        stringResource(R.string.build_msg_slots_full, spec.maxTowers ?: 0)
    GameEngine.BuildRejection.INSUFFICIENT_SUPPLY ->
        stringResource(R.string.build_msg_supply)
}
