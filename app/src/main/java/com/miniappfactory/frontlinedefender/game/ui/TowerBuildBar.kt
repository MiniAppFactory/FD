package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.ui.theme.*

/**
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
                    TowerBuildCard(
                        spec = spec,
                        canAfford = gold >= spec.buildCost,
                        onBuild = { gameEngine.buildTower(towerType) },
                        modifier = Modifier.weight(1f)
                    )
                }

                val closeDesc = stringResource(R.string.build_close_desc)
                IconButton(
                    onClick = { gameEngine.deselectAll() },
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
    canAfford: Boolean,
    onBuild: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = if (canAfford) SleekSurfaceCard else SleekDarkBg
    val borderColor = if (canAfford) SleekPrimaryGreen else SleekBorderDark
    // Faz 6: ad GameConfig'ten DEGIL kaynaktan. GameConfig.TowerStats.name
    // kullanilmayan Ingilizce fallback olarak kaldi.
    val towerName = stringResource(spec.type.nameRes())

    Row(
        modifier = modifier
            .background(cardColor, RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = canAfford, onClick = onBuild)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("build_card_${spec.type.name.lowercase()}"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpriteIcon(
            id = towerSpriteRes(spec.type),
            size = 34.dp,
            contentDescription = towerName,
            modifier = if (canAfford) Modifier else Modifier.alpha(0.4f)
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
                color = if (canAfford) SleekTextAccent else Color.Gray,
                fontWeight = FontWeight.Bold,
                maxFontSize = 11.sp,
                minFontSize = 8.sp,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SpriteIcon(
                    id = R.drawable.spr_ic_coins,
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
