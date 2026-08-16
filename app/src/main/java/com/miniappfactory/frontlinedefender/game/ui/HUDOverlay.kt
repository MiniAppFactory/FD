package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.ui.theme.*

@Composable
fun HUDOverlay(
    gameEngine: GameEngine,
    onOpenPauseMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gold by gameEngine.gold.collectAsState()
    val lives by gameEngine.lives.collectAsState()
    val waveIndex by gameEngine.currentWaveIndex.collectAsState()
    val totalWaves by gameEngine.totalWaves.collectAsState()
    val speed by gameEngine.gameSpeed.collectAsState()
    val gameState by gameEngine.gameState.collectAsState()
    val prepTimer by gameEngine.preparationTimer.collectAsState()

    Surface(
        color = SleekSurfaceHeader,
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(width = (0.5).dp, color = SleekBorderDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Wave Status & Gold Coins Badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wave Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SleekSurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SleekBorderLight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // BUG (cihazda goruldu: "DALGA 9/6"). Toplam dalga sayisi
                        // `GameConfig.WAVES.size`den okunuyordu; o liste eski
                        // TEK-BOLUM listesi ve sabit 6 eleman. Kampanyada dalga
                        // sayisi bolume gore 6..18 arasinda degisiyor, bu yuzden
                        // 9. dalgada "9/6" gibi imkansiz bir deger cikiyordu.
                        // Tek dogruluk kaynagi motorun aktif bolum icin
                        // hesapladigi `totalWaves` akisi.
                        Text(
                            text = stringResource(
                                R.string.hud_wave_label,
                                waveIndex + 1,
                                totalWaves
                            ),
                            color = SleekTextAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.testTag("wave_status_text")
                        )

                        if (gameState == GameState.PREPARATION) {
                            Text(
                                text = stringResource(
                                    R.string.hud_prep_timer,
                                    prepTimer.toInt()
                                ),
                                color = SleekGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Gold Coins Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SleekGoldBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SleekGoldBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Faz 3: asset pack ikonu (icon_coins)
                        SpriteIcon(
                            id = R.drawable.spr_ic_coins,
                            size = 16.dp,
                            contentDescription = stringResource(R.string.hud_supply_icon_desc)
                        )
                        Text(
                            text = stringResource(R.string.hud_supply_amount, gold),
                            color = SleekGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.testTag("gold_text")
                        )
                    }
                }
            }

            // Right Side: Base Lives, Game Speed & Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Base Lives Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SleekRedBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SleekRedBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Faz 3: asset pack ikonu (icon_base_health)
                        SpriteIcon(
                            id = R.drawable.spr_ic_base_health,
                            size = 16.dp,
                            contentDescription = stringResource(R.string.hud_lives_icon_desc)
                        )
                        Text(
                            // Azami can artik Kotlin'de sabit degil, GameConfig'ten
                            // okunuyor (ayni deger: 20). Metin kaynakta.
                            text = stringResource(
                                R.string.hud_lives_value,
                                lives,
                                GameConfig.INITIAL_BASE_LIVES
                            ),
                            color = SleekRedText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.testTag("lives_text")
                        )
                    }
                }

                // Start Wave Button (During Prep Phase)
                if (gameState == GameState.PREPARATION) {
                    Button(
                        onClick = { gameEngine.startNextWaveNow() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SleekPrimaryGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("start_wave_button")
                    ) {
                        SpriteIcon(
                            id = R.drawable.spr_ic_play,
                            size = 18.dp,
                            contentDescription = stringResource(R.string.hud_start_wave_icon_desc)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Buton genisligi icerige gore esner (wrap content),
                        // yani "BASLAT" Ingilizce "START"tan uzun olsa da
                        // kirpilmaz; Row zaten SpaceBetween.
                        Text(
                            text = stringResource(R.string.hud_start_wave),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                // Speed Toggle Button (1x / 2x) — icon_fast_forward + carpan
                IconButton(
                    onClick = { gameEngine.toggleGameSpeed() },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (speed > 1.0f) SleekPrimaryGreen else SleekSurfaceCard,
                            CircleShape
                        )
                        .border(1.dp, SleekBorderLight, CircleShape)
                        .testTag("speed_toggle_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SpriteIcon(
                            id = R.drawable.spr_ic_fast_forward,
                            size = 15.dp,
                            contentDescription = stringResource(R.string.hud_speed_icon_desc)
                        )
                        Text(
                            text = stringResource(
                                if (speed == 1.0f) R.string.hud_speed_1x
                                else R.string.hud_speed_2x
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }

                // Pause Menu Button — icon_pause
                IconButton(
                    onClick = onOpenPauseMenu,
                    modifier = Modifier
                        .size(36.dp)
                        .background(SleekSurfaceCard, CircleShape)
                        .border(1.dp, SleekBorderLight, CircleShape)
                        .testTag("pause_button")
                ) {
                    SpriteIcon(
                        id = R.drawable.spr_ic_pause,
                        size = 18.dp,
                        contentDescription = stringResource(R.string.hud_pause_icon_desc)
                    )
                }
            }
        }
    }
}

