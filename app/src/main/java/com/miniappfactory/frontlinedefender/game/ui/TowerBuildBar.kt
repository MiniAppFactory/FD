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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.ui.theme.*

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
 * LOKALIZASYON: buradaki kilit etiketi ("LOCKED" / "Lv N") Ingilizce SABIT.
 * `strings.xml` baska bir ajanin dosyasi; eklenecek anahtarlar
 * docs/TOWER_REBALANCE.md'de listeli.
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
 */
@Composable
fun TowerBuildBar(
    gameEngine: GameEngine,
    modifier: Modifier = Modifier
) {
    val selectedBuildSpot by gameEngine.selectedBuildSpot.collectAsState()
    val gold by gameEngine.gold.collectAsState()
    val levelId by gameEngine.currentLevelId.collectAsState()
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
            modifier = Modifier.fillMaxWidth()
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
                    val unlocked = GameConfig.isTowerUnlocked(towerType, levelId)
                    TowerBuildCard(
                        spec = spec,
                        unlocked = unlocked,
                        canAfford = gold >= spec.buildCost,
                        onBuild = {
                            // HAPTIK, SES VE GORSEL AYNI KAREDE. Titresim
                            // `buildTower`dan ONCE tetiklenir: motor cagrisi
                            // kule listesini ve altini guncelleyip recomposition
                            // baslatir, dokunsal geri bildirimi onun ARKASINA
                            // koymak parmagin altinda olculebilir bir gecikme
                            // yaratirdi.
                            haptics.onTowerBuilt()
                            gameEngine.buildTower(towerType)
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
    unlocked: Boolean,
    canAfford: Boolean,
    onBuild: () -> Unit,
    onPreview: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val buildable = unlocked && canAfford
    val cardColor = if (buildable) SleekSurfaceCard else SleekDarkBg
    val borderColor = when {
        buildable -> SleekPrimaryGreen
        !unlocked -> SleekBorderDark
        else -> SleekBorderDark
    }
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
                enabled = buildable,
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
            // Kilitli kart daha da soluk: "param yetmiyor" ile "henuz acilmadi"
            // ayni gorunmemeli.
            modifier = when {
                buildable -> Modifier
                !unlocked -> Modifier.alpha(0.25f)
                else -> Modifier.alpha(0.4f)
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
            if (!unlocked) {
                // LOKALIZE EDILECEK: build_locked_at_level ("Lv %1$d'de acilir").
                // Bkz. docs/TOWER_REBALANCE.md — strings.xml baska ajanin dosyasi.
                AutoShrinkText(
                    text = "LOCKED · Lv ${spec.unlockedAtLevel}",
                    color = Color(0xFF9AA5B1),
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 11.sp,
                    minFontSize = 8.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("build_locked_${spec.type.name.lowercase()}")
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // TEDARIK glifi, COIN degil. Yukarida `canAfford`
                    // `gold >= spec.buildCost` ile hesaplaniyor: insa bedeli
                    // savas ici TEDARIK ile odeniyor. Buraya kadar coin glifi
                    // cizildigi icin oyuncuya yanlis para birimi gosteriliyordu.
                    // (Meta para birimi olan coin'in yeri `UpgradeShopScreen`.)
                    SpriteIcon(
                        id = R.drawable.spr_ic_supply_crate,
                        size = 11.dp,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.build_cost, spec.buildCost),
                        color = if (canAfford) SleekGold else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
