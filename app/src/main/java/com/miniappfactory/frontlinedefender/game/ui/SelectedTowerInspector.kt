package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.ui.theme.*

/**
 * Faz 3: tek satirlik kompakt duzen + asset pack ikonlari.
 *
 * Onceki iki satirli hali cihazda ~300 px kapliyor ve alt sirada duran build
 * pad'leri kapatiyordu. Ayrica satis butonundaki `"+$$${...}"` ifadesi Kotlin'de
 * IKI dolar isareti uretiyordu ("+$$56"); duzeltildi.
 */
@Composable
fun SelectedTowerInspector(
    gameEngine: GameEngine,
    modifier: Modifier = Modifier
) {
    val selectedTower by gameEngine.selectedTower.collectAsState()
    val gold by gameEngine.gold.collectAsState()

    AnimatedVisibility(
        visible = selectedTower != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        selectedTower?.let { tower ->
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Faz 6: ad ve istatistik satiri kaynaktan geliyor.
                    val towerName = stringResource(tower.type.nameRes())

                    SpriteIcon(
                        id = towerSpriteRes(tower.type),
                        size = 38.dp,
                        contentDescription = towerName
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // TASMA: panel tek satir; dort buton + kapat ikonuyla
                        // ayni Row'da. Turkce "Fuze Rampasi (Kd.2)" Ingilizce
                        // karsiligindan uzun, ustelik kalan genislik butonlarin
                        // dilden dile degisen genisligine bagli. Bu yuzden iki
                        // satir da olceklenir, kirpilmaz.
                        AutoShrinkText(
                            text = stringResource(
                                R.string.inspector_tower_title,
                                towerName,
                                tower.level
                            ),
                            color = SleekTextAccent,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = 13.sp,
                            minFontSize = 9.sp,
                            maxLines = 1,
                            // Kademe degisince yeniden olcul; imha sayaci her
                            // artisinda sifirlanmasin diye anahtar sabit.
                            resetKey = tower.type to tower.level,
                            modifier = Modifier.fillMaxWidth()
                        )
                        AutoShrinkText(
                            text = stringResource(
                                R.string.inspector_stats,
                                tower.damage.toInt(),
                                tower.fireRate,
                                tower.killsCount
                            ),
                            color = Color.White.copy(alpha = 0.85f),
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp,
                            maxLines = 1,
                            resetKey = tower.type to tower.level,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Hedefleme modu — icon_target
                    Button(
                        onClick = { gameEngine.cycleTargetingMode() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SleekSurfaceCard,
                            contentColor = SleekTextAccent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("cycle_target_button")
                    ) {
                        SpriteIcon(
                            id = R.drawable.spr_ic_target,
                            size = 18.dp,
                            contentDescription = stringResource(
                                R.string.inspector_targeting_icon_desc
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Onceden enum adi (FIRST/LAST/...) basiliyordu; artik
                        // cevrilmis etiket. Buton wrap-content, esner.
                        Text(
                            text = stringResource(tower.targetingMode.labelRes()),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // Yukseltme — icon_upgrade
                    if (tower.level == 1) {
                        val upgradeCost = tower.upgradeCost ?: 0
                        Button(
                            onClick = { gameEngine.upgradeSelectedTower() },
                            enabled = gold >= upgradeCost,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SleekPrimaryGreen,
                                contentColor = Color.White,
                                disabledContainerColor = SleekSurfaceCard
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("upgrade_button")
                        ) {
                            SpriteIcon(
                                id = R.drawable.spr_ic_upgrade,
                                size = 20.dp,
                                contentDescription = stringResource(
                                    R.string.inspector_upgrade_icon_desc
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.inspector_upgrade_cost,
                                    upgradeCost
                                ),
                                color = SleekGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Surface(
                            color = SleekSurfaceCard,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SleekBorderLight),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.inspector_max_level),
                                    color = SleekGold,
                                    maxLines = 1,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Satis — icon_sell
                    Button(
                        onClick = { gameEngine.sellSelectedTower() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SleekRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("sell_button")
                    ) {
                        SpriteIcon(
                            id = R.drawable.spr_ic_sell,
                            size = 20.dp,
                            contentDescription = stringResource(R.string.inspector_sell_icon_desc)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Faz 3 notu: "+$$${...}" IKI dolar basiyordu. Faz 6'da
                        // bicim tamamen kaynaga tasindi (inspector_sell_value).
                        Text(
                            text = stringResource(
                                R.string.inspector_sell_value,
                                tower.sellValue
                            ),
                            color = SleekGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val closeDesc = stringResource(R.string.inspector_close_desc)
                    IconButton(
                        onClick = { gameEngine.deselectAll() },
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = closeDesc }
                    ) {
                        Text(
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
}
