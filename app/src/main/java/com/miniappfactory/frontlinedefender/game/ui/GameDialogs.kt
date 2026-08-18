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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.audio.rememberHaptics
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.economy.LevelClearResult
import com.miniappfactory.frontlinedefender.game.economy.healthNeededForStars
import com.miniappfactory.frontlinedefender.ui.theme.*

@Composable
fun MainMenuOverlay(
    gameEngine: GameEngine,
    onStartGame: () -> Unit
) {
    val highScore = gameEngine.saveManager.highScore
    var soundEnabled by remember { mutableStateOf(gameEngine.saveManager.soundEnabled) }
    // Faz 14 - muzik ve dokunsal geri bildirim. Tercihler `SaveManager`da
    // DEGIL, ilgili yoneticilerin kendi kalici deposunda; burada yalnizca
    // anahtarin cizilecek durumu tutuluyor (ses satiriyla ayni desen).
    var musicEnabled by remember { mutableStateOf(gameEngine.audioManager.isMusicEnabled) }
    val haptics = rememberHaptics()
    var hapticsEnabled by remember { mutableStateOf(haptics.isHapticsEnabled) }

    // Disli ikonu ARTIK gercekten bir ekran aciyor (bkz. asagidaki not).
    var settingsOpen by remember { mutableStateOf(false) }

    // KAMUFLAJ ZEMIN.
    //
    // 512x512 SARMALI (seamless) desen, tekrarli shader ile tum ekrana dosenir —
    // tam ekran bir bitmap yerine 2.7 KB'lik tek karo. Desen prosedurel
    // uretildi ve uretici korunuyor: docs/tools/camo_pattern.py (seed sabit,
    // ayni deseni tekrar uretir).
    //
    // Uzerine KOYU PERDE biner: kamuflaj kasitli olarak dusuk kontrast ve
    // koyu tutuldu ki baslik ve butonlar okunur kalsin. Dekor, oynanis
    // okunabilirliginin onune gecmez.
    val camo = ImageBitmap.imageResource(R.drawable.bg_camo)
    val camoBrush = remember(camo) {
        ShaderBrush(ImageShader(camo, TileMode.Repeated, TileMode.Repeated))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(camoBrush)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SleekSurfaceHeader.copy(alpha = 0.82f),
                        SleekDarkBg.copy(alpha = 0.94f)
                    )
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

            // AYARLAR GIRISI
            //
            // ESKI HALI: bu disli ikonu **yalnizca bir ses ac/kapa dugmesiydi**;
            // kodun kendi yorumu "disli ikonu bir ayarlar EKRANI bekletiyor"
            // diye itiraf ediyordu ve kullanici da "settings'e basamadim" diye
            // bildirmisti — cunku basiliyordu ama hicbir ekran acilmiyordu.
            //
            // ARTIK: ikon gercek bir ayarlar ekrani aciyor. Ses ac/kapa oraya
            // TASINDI (davranis ayni: hem kalici kayda hem calisan ses motoruna
            // yazilir), ve ekran UMP "Gizlilik Secenekleri" girisini de tasiyor —
            // oyuncunun rizasini geri cekebilecegi tek kalici nokta.
            //
            // Ikonun altindaki YAZI korundu: ikon tek basina ne oldugunu
            // anlatmiyor ve renk tek ayrim kanali olamaz (erisilebilirlik).
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { settingsOpen = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(SleekSurfaceCard, CircleShape)
                        .border(1.dp, SleekBorderLight, CircleShape)
                        .testTag("settings_button")
                ) {
                    SpriteIcon(
                        id = R.drawable.spr_ic_settings,
                        size = 26.dp,
                        contentDescription = stringResource(R.string.settings_open_desc)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_button_label),
                    color = SleekTextAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        if (settingsOpen) {
            SettingsScreen(
                soundEnabled = soundEnabled,
                onSoundEnabledChange = { enabled ->
                    // Eski dugmenin YAPTIGI ISIN AYNISI, tasindi: once yerel
                    // durum (aninda geri bildirim), sonra kalici kayit, sonra
                    // calisan ses motoru.
                    soundEnabled = enabled
                    gameEngine.saveManager.soundEnabled = enabled
                    gameEngine.audioManager.isSoundEnabled = enabled
                },
                musicEnabled = musicEnabled,
                onMusicEnabledChange = { enabled ->
                    // Ses satiriyla AYNI sira: once yerel durum (aninda geri
                    // bildirim), sonra calisan motor. Kalici kayit motorun
                    // kendi setter'i icinde yapiliyor.
                    musicEnabled = enabled
                    gameEngine.audioManager.isMusicEnabled = enabled
                },
                hapticsEnabled = hapticsEnabled,
                onHapticsEnabledChange = { enabled ->
                    hapticsEnabled = enabled
                    haptics.isHapticsEnabled = enabled
                },
                // SIFIRLAMA. `SaveManager` bu uc metodu tasiyordu ama hicbir
                // yerden cagrilmiyordu; ayarlar ekrani onlari saf lambda
                // olarak alir, motoru/kaydi TANIMAZ.
                onResetHints = { gameEngine.saveManager.resetHints() },
                onResetTutorial = { gameEngine.saveManager.resetTutorial() },
                onResetProgress = { gameEngine.saveManager.resetProgress() },
                onDismiss = { settingsOpen = false }
            )
        }
    }
}

/**
 * Zafer ekrani.
 *
 * ## Faz 13'te neden degisti
 * 1. **Tek buton "REPLAY" yaziyordu ama `returnToLevelSelect()` cagiriyordu** —
 *    etiket yalandi.
 * 2. **"SONRAKI BOLUM" yoktu.** Kampanyayi surdurmenin yolu zafer ekranindan
 *    cikip haritada siradaki bolumu bulmaktan geciyordu; tutunmanin en buyuk
 *    tek kaybi buydu.
 * 3. **Kazanilan coin hic gosterilmiyordu.** Ekonomi coini yatiriyor
 *    (`onLevelCleared`), oyuncu ise yalnizca meta karsiligi olmayan "Final
 *    score" goruyordu — yani ilerlemenin gorunur karsiligi yoktu.
 * 4. **Kalan can paydasi sabit sabit bir kampanya degeriydi**; Tahkimat
 *    meta yukseltmesiyle can 30'a ciktiginda ekran "30/20" yaziyordu.
 *
 * @param clearResult ekonominin bu temizlik icin urettigi odul dokumu.
 *   `null` ise coin satiri hic cizilmez (uydurma sayi gosterilmez).
 * @param nextLevelId siradaki oynanabilir bolum; `null` ise (kampanya bitti
 *   veya siradaki bolum kilitli) "SONRAKI BOLUM" butonu **gosterilmez** —
 *   calismayan bir buton koymaktansa hic koymamak.
 * @param onNextLevel [nextLevelId] null degilken cagrilir.
 * @param onLevelSelect bolum secime donus (eski "REPLAY" butonunun GERCEK isi).
 */
@Composable
fun VictoryModal(
    gameEngine: GameEngine,
    clearResult: LevelClearResult? = null,
    nextLevelId: Int? = null,
    onNextLevel: (() -> Unit)? = null,
    onLevelSelect: () -> Unit
) {
    val lives by gameEngine.lives.collectAsState()
    val score by gameEngine.score.collectAsState()

    // BUG: burada yildiz MUTLAK esikle (lives >= 18 / >= 10) YENIDEN
    // hesaplaniyordu. Motor tarafi yuzdeye cevrildi (GDD B.3: %90/%50/>0) cunku
    // us cani bolume ve meta yukseltmelere gore degisiyor. Iki ayri hesap
    // oldugu icin modal, KAYDEDILEN yildizdan farkli bir sayi gosterebiliyordu.
    // Tek dogru kaynak motor: hesabi tekrarlamak yerine sonucu okuyoruz.
    val stars by gameEngine.lastEarnedStars.collectAsState()

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
                        // Payda ARTIK sabit degil: Tahkimat meta yukseltmesi
                        // cani 20'den 30'a cikarabiliyor ve ekran "30/20"
                        // yaziyordu. `maxLives` taban + meta bonusunu tek yerde
                        // toplayan tek dogru kaynak.
                        text = stringResource(
                            R.string.dialog_lives_remaining,
                            lives,
                            gameEngine.maxLives
                        ),
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    // KAZANILAN COIN — ilerlemenin gorunur karsiligi. Ekonomi
                    // bunu zaten yatirdi; gostermemek, odulu hic vermemis gibi
                    // hissettiriyordu.
                    if (clearResult != null && clearResult.total > 0) {
                        Text(
                            text = stringResource(R.string.dialog_coins_earned, clearResult.total),
                            color = SleekGold,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("victory_coins_earned")
                        )
                    }
                    Text(
                        text = stringResource(R.string.dialog_final_score, score),
                        color = SleekTextAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    // SIRADAKI HEDEF. Yalnizca 3 yildizin altindayken ve hedef
                    // gercekten ulasilabilirken gosterilir; 3 yildizda "daha
                    // iyisini yap" demek anlamsiz olurdu.
                    if (stars < 3) {
                        val needed = healthNeededForStars(
                            targetStars = 3,
                            maxLives = gameEngine.levelSpec.maxBaseLives.coerceAtLeast(1)
                        )
                        val gap = needed - gameEngine.lastStarHealth
                        if (gap > 0) {
                            Text(
                                text = stringResource(R.string.dialog_star_hint, gap),
                                color = SleekTextAccent,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("victory_star_hint")
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // BOLUM SEC: kampanya bittiyse veya siradaki bolum
                    // kilitliyse TEK buton olarak kalir (eski davranis).
                    Button(
                        onClick = onLevelSelect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (onNextLevel == null) {
                                SleekPrimaryGreen
                            } else {
                                SleekSurfaceCard
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("victory_level_select_button")
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_level_select),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    if (onNextLevel != null && nextLevelId != null) {
                        Button(
                            onClick = onNextLevel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SleekPrimaryGreen
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("victory_next_level_button")
                        ) {
                            Text(
                                text = stringResource(R.string.dialog_next_level),
                                fontWeight = FontWeight.Bold,
                                // 13sp: TR "SONRAKİ BÖLÜM" 14 karakter ve
                                // butonlar esit agirlikli; 14sp'de dar
                                // ekranlarda (S8 landscape) tasiyordu.
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
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
    // Faz 14 - muzik ve dokunsal geri bildirim. Tercihler `SaveManager`da
    // DEGIL, ilgili yoneticilerin kendi kalici deposunda; burada yalnizca
    // anahtarin cizilecek durumu tutuluyor (ses satiriyla ayni desen).
    var musicEnabled by remember { mutableStateOf(gameEngine.audioManager.isMusicEnabled) }
    val haptics = rememberHaptics()
    var hapticsEnabled by remember { mutableStateOf(haptics.isHapticsEnabled) }
    var settingsOpen by remember { mutableStateOf(false) }

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

                // AYARLAR
                //
                // Buradaki tek basina duran ses anahtari, ayarlarin YARISININ
                // duraklatma menusunde YARISININ ana menude olmasi demekti; ustelik
                // duraklatma menusunden gizlilik secenekleri erisilemiyordu.
                // Anahtar ayarlar ekranina TASINDI (davranis birebir ayni) ve
                // yerine ekranin tamamini acan tek bir giris kondu. Boylece her
                // ayar tek bir yerde yasar ve iki kopya zamanla ayrisamaz.
                Button(
                    onClick = { settingsOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekSurfaceCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pause_settings_button")
                ) {
                    AutoShrinkText(
                        text = stringResource(R.string.settings_button_label),
                        color = Color.Unspecified,
                        fontWeight = FontWeight.Bold,
                        maxFontSize = 16.sp,
                        minFontSize = 12.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
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

        // Duraklatma modalinin USTUNDE acilir. Oyun zaten duraklatilmis
        // durumda; ayarlar kapaninca ayni modala geri donulur, oynanis
        // kendiliginden devam ETMEZ.
        if (settingsOpen) {
            SettingsScreen(
                soundEnabled = soundEnabled,
                onSoundEnabledChange = { enabled ->
                    soundEnabled = enabled
                    gameEngine.saveManager.soundEnabled = enabled
                    gameEngine.audioManager.isSoundEnabled = enabled
                },
                musicEnabled = musicEnabled,
                onMusicEnabledChange = { enabled ->
                    // Ses satiriyla AYNI sira: once yerel durum (aninda geri
                    // bildirim), sonra calisan motor. Kalici kayit motorun
                    // kendi setter'i icinde yapiliyor.
                    musicEnabled = enabled
                    gameEngine.audioManager.isMusicEnabled = enabled
                },
                hapticsEnabled = hapticsEnabled,
                onHapticsEnabledChange = { enabled ->
                    hapticsEnabled = enabled
                    haptics.isHapticsEnabled = enabled
                },
                // SIFIRLAMA. `SaveManager` bu uc metodu tasiyordu ama hicbir
                // yerden cagrilmiyordu; ayarlar ekrani onlari saf lambda
                // olarak alir, motoru/kaydi TANIMAZ.
                onResetHints = { gameEngine.saveManager.resetHints() },
                onResetTutorial = { gameEngine.saveManager.resetTutorial() },
                onResetProgress = { gameEngine.saveManager.resetProgress() },
                onDismiss = { settingsOpen = false }
            )
        }
    }
}

