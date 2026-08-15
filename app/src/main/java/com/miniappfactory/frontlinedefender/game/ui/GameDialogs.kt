package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.ui.theme.*

@Composable
fun MainMenuOverlay(
    gameEngine: GameEngine,
    onStartGame: () -> Unit
) {
    val highScore = gameEngine.saveManager.highScore
    var soundEnabled by remember { mutableStateOf(gameEngine.saveManager.soundEnabled) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SleekSurfaceHeader, SleekDarkBg)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(24.dp)
        ) {
            // Game Title Header
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SleekSurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SleekBorderLight)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        // Marka adi: translatable="false", her dilde ayni.
                        text = stringResource(R.string.dialog_game_title),
                        color = SleekTextAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dialog_game_subtitle),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // High Score
            if (highScore > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpriteIcon(
                        id = R.drawable.spr_ic_victory_star,
                        size = 24.dp,
                        contentDescription = stringResource(R.string.dialog_high_score_icon_desc)
                    )
                    Text(
                        text = stringResource(R.string.dialog_high_score, highScore),
                        color = SleekGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                }
            }

            // PLAY BUTTON
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SleekPrimaryGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                modifier = Modifier.testTag("play_game_button")
            ) {
                SpriteIcon(
                    id = R.drawable.spr_ic_play,
                    size = 28.dp,
                    contentDescription = stringResource(R.string.dialog_play_icon_desc)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Buton wrap-content, kolonun ust siniri 480 dp: "HAREKATI
                // BASLAT" 16 sp ExtraBold'da ~150 dp, 300 dp'lik bosluga sigar.
                // Yine de tek satirda kalmasi garanti edilir.
                Text(
                    text = stringResource(R.string.dialog_start_operation),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
            }

            // Sound Toggle
            IconButton(
                onClick = {
                    soundEnabled = !soundEnabled
                    gameEngine.saveManager.soundEnabled = soundEnabled
                    gameEngine.audioManager.isSoundEnabled = soundEnabled
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(SleekSurfaceCard, CircleShape)
                    .border(1.dp, SleekBorderLight, CircleShape)
                    .testTag("sound_toggle_button")
            ) {
                // icon_settings: ses acik/kapali durumu alfa ile ayrisir
                SpriteIcon(
                    id = R.drawable.spr_ic_settings,
                    size = 26.dp,
                    contentDescription = stringResource(R.string.dialog_sound_toggle_desc),
                    modifier = if (soundEnabled) Modifier else Modifier.alpha(0.35f)
                )
            }
        }
    }
}

@Composable
fun VictoryModal(
    gameEngine: GameEngine,
    onReplay: () -> Unit
) {
    val lives by gameEngine.lives.collectAsState()
    val score by gameEngine.score.collectAsState()

    val stars = when {
        lives >= 18 -> 3
        lives >= 10 -> 2
        else -> 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SleekDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SleekPrimaryGreen),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_victory_title),
                    color = SleekTextAccent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                )

                // 3 Stars Display — Faz 3: asset pack icon_victory_star.
                // Kazanilmayan yildiz ayni sprite'in soluk/gri hali (tek dosya).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..3) {
                        val isEarned = i <= stars
                        SpriteIcon(
                            id = R.drawable.spr_ic_victory_star,
                            size = 44.dp,
                            contentDescription = stringResource(R.string.dialog_star_desc, i),
                            modifier = if (isEarned) Modifier else Modifier.alpha(0.22f)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.dialog_victory_subtitle),
                    color = SleekTextAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.dialog_lives_remaining,
                            lives,
                            GameConfig.INITIAL_BASE_LIVES
                        ),
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.dialog_final_score, score),
                        color = SleekGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onReplay,
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryGreen),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("victory_replay_button")
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_replay),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DefeatModal(
    gameEngine: GameEngine,
    onRetry: () -> Unit,
    onMainMenu: () -> Unit
) {
    val waveIndex by gameEngine.currentWaveIndex.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SleekDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SleekRed),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Faz 3: asset pack icon_defeat_skull
                SpriteIcon(
                    id = R.drawable.spr_ic_defeat_skull,
                    size = 52.dp,
                    contentDescription = null
                )

                Text(
                    text = stringResource(R.string.dialog_defeat_title),
                    color = SleekRedText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    // Coklu satira sarar (maxLines yok): dialog genisligi 420 dp
                    // sabit ama YUKSEKLIK esner, yani uzun ceviri tasmaz.
                    text = stringResource(R.string.dialog_defeat_body, waveIndex + 1),
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onMainMenu,
                        colors = ButtonDefaults.buttonColors(containerColor = SleekSurfaceCard),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("defeat_menu_button")
                    ) {
                        // Iki buton weight(1f) ile ESIT boluyor; "TEKRAR DENE"
                        // gibi uzun karsiliklar icin punto kucultmeye izin var.
                        AutoShrinkText(
                            text = stringResource(R.string.dialog_main_menu),
                            // Unspecified = eskisi gibi butonun contentColor'u.
                            color = Color.Unspecified,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = 12.sp,
                            minFontSize = 9.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = SleekRed),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("defeat_retry_button")
                    ) {
                        AutoShrinkText(
                            text = stringResource(R.string.dialog_retry),
                            color = Color.Unspecified,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = 12.sp,
                            minFontSize = 9.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PauseMenuModal(
    gameEngine: GameEngine,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    var soundEnabled by remember { mutableStateOf(gameEngine.saveManager.soundEnabled) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SleekDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SleekBorderLight),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_paused_title),
                    color = SleekTextAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )

                // Sound Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Row SpaceBetween: etiket Switch'i itmesin diye weight(1f)
                    // ile sinirli ve gerekirse olcek kuculur.
                    AutoShrinkText(
                        text = stringResource(R.string.dialog_sound_effects),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxFontSize = 16.sp,
                        minFontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            gameEngine.saveManager.soundEnabled = it
                            gameEngine.audioManager.isSoundEnabled = it
                        }
                    )
                }

                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pause_resume_button")
                ) {
                    Text(
                        text = stringResource(R.string.dialog_resume),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = SleekSurfaceCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pause_restart_button")
                ) {
                    // En uzun karsilik: "BOLUMU BASTAN AL" (TR). Buton
                    // fillMaxWidth (360 dp dialog - 80 dp dolgu = 280 dp),
                    // 16 sp Bold'da ~150 dp -> sigar; tek satirda kalir.
                    AutoShrinkText(
                        text = stringResource(R.string.dialog_restart_level),
                        color = Color.Unspecified,
                        fontWeight = FontWeight.Bold,
                        maxFontSize = 16.sp,
                        minFontSize = 12.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onMainMenu,
                    colors = ButtonDefaults.buttonColors(containerColor = SleekDarkBg),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pause_main_menu_button")
                ) {
                    Text(
                        text = stringResource(R.string.dialog_main_menu),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

