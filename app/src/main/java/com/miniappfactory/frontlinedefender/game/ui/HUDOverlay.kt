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
import androidx.compose.runtime.remember
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
import com.miniappfactory.frontlinedefender.game.economy.BattleTelemetry
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.ui.theme.*

/**
 * @param telemetry gorev olcumu ([BattleTelemetry]). **Varsayilani YOKTUR** —
 *   `d_p_skip3` ve `d_s_double_speed` gorevlerinin tek besleyicisi burasi.
 */
@Composable
fun HUDOverlay(
    gameEngine: GameEngine,
    onOpenPauseMenu: () -> Unit,
    telemetry: BattleTelemetry,
    modifier: Modifier = Modifier
) {
    val gold by gameEngine.gold.collectAsState()
    val lives by gameEngine.lives.collectAsState()
    val waveIndex by gameEngine.currentWaveIndex.collectAsState()
    val totalWaves by gameEngine.totalWaves.collectAsState()
    val speed by gameEngine.gameSpeed.collectAsState()
    val gameState by gameEngine.gameState.collectAsState()
    // Degistirici rozetleri bolume gore var/yok. Spec motorun `levelSpec`
    // alanindan DEGIL akistan turetilir: duz bir `var` okumak recomposition
    // tetiklemez ve bolum degisince rozet eski bolumun kuralini gosterirdi.
    val levelId by gameEngine.currentLevelId.collectAsState()
    val levelSpec = remember(levelId) { GameConfig.levelSpec(levelId) }
    // ⚠ `preparationTimer` BURADA OKUNMAZ. Motor onu HER KARE guncelliyor;
    // govdede okumak hazirlik fazi boyunca tum HUD'i 60 Hz yeniden
    // besteliyordu (bolum basina ~600 recomposition) ve bu maliyet tam da
    // bolum acilisinin en pahali anina biniyordu (biyom recolor + sprite
    // decode ayni anda). Sayac kendi composable'inda okunur, bkz.
    // [PreparationTimerText].

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
                // DIKEY BOSLUK 10 -> 6 dp (cihaz geri bildirimi: "oyun alani
                // kucuk"). HUD 56 -> 48 dp.
                //
                // Neden bedeli buyuk: `GameScreen` bu Surface'in OLCULEN
                // yuksekligini `topInsetPx` olarak `GameCanvas`'a veriyor ve
                // harita orada FIT + letterbox yerlesiyor. Yani cubuktan
                // kirpilan her dp yalnizca tepeden kazanilmiyor — harita
                // en-boy oranini korudugu icin TAMAMI buyuyor ve yanlardaki
                // siyah bantlar daraliyor.
                //
                // Dokunma hedeflerine DOKUNULMADI (dugmeler 36 dp): bosluk
                // kirpmak geri alinabilir bir yerlesim karari, dokunma hedefi
                // kucultmek ise erisilebilirlik borcudur.
                .padding(horizontal = 16.dp, vertical = 6.dp),
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
                            PreparationTimerText(gameEngine)
                        }
                    }
                }

                // Savas ici TEDARIK rozeti (meta para birimi Coin DEGIL).
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
                        // Ikon bilincli olarak Coin glifi DEGIL: Tedarik savas ici
                        // kaynak, Coin ise meta para birimi ve Us Tamiri savas
                        // ICINDE Coin harciyor. Ikisi ayni gliften gosterilirse
                        // oyuncu hangi kesenin eridigini goremez.
                        SpriteIcon(
                            id = R.drawable.spr_ic_supply_crate,
                            size = 16.dp,
                            contentDescription = stringResource(R.string.hud_supply_icon_desc)
                        )
                        // KELIMEYLE "TEDARIK" — cihaz geri bildirimi: oyuncu bu
                        // sayiyi COIN sandi ("oyun disinda 1420 coin yaziyor,
                        // oyun icinde ayri bir para isliyor neden?").
                        //
                        // Ikon ayrimi (sandik vs coin) TEK BASINA yetmedi ve
                        // yetmemesi sasirtici degil: iki para birimi de sari,
                        // ikisi de ayni kosede, ve oyuncu 16 dp'lik bir glifi
                        // incelemiyor. Bolum secim ekranindaki cip zaten
                        // "COIN 1420" diye YAZIYOR (`level_coin_label`); savas
                        // rozetinin yalin sayi olmasi asimetrikti.
                        //
                        // `SUPPLY`/`TEDARIK` cevrilebilir (COIN'in aksine):
                        // "Coin" bir marka/para birimi adi, "Tedarik" ise bir
                        // kaynak adi ve Turkcesi var.
                        Text(
                            text = stringResource(R.string.hud_supply_label),
                            color = SleekGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.testTag("supply_label")
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

                // ------------------------------------------------------------
                // BOLUM DEGISTIRICILERI (GameConfig.LevelModifiers)
                //
                // Ikisi de yalnizca kurali TASIYAN bolumlerde cizilir; kalan
                // 49 bolumde HUD birebir eskisi gibi kalir.
                //
                // Neden HUD'da: gorunmeyen bir kisit, oyuncunun ancak
                // REDDEDILDIGINDE ogrendigi bir kisitiir. "3/5 mevzi" her an
                // okunabilir olmali ki tavan bir surpriz degil bir PLAN girdisi
                // olsun.
                // ------------------------------------------------------------
                val cap = levelSpec.maxTowers
                if (cap != null) {
                    EmplacementCapBadge(gameEngine = gameEngine, cap = cap)
                }
                if (levelSpec.buildLockedDuringWave) {
                    BuildWindowBadge(waveRunning = gameState == GameState.WAVE_RUNNING)
                }
            }

            // ----------------------------------------------------------------
            // ORTA: SIRADAKI DALGA ONIZLEMESI (yalnizca hazirlik fazinda).
            //
            // Neden BURASI: oyun yatay (`sensorLandscape`) ve bu satirin ortasi
            // bugun tamamen bos. HUD'un ALTINA ayri bir serit koymak olmazdi —
            // `GameScreen` bu Surface'in OLCULEN yuksekligini `topInsetPx`
            // olarak `GameCanvas`'a veriyor ve motor oynanis dikdortgenini ona
            // gore kuruyor; serit yalnizca hazirlik fazinda var oldugu icin
            // savas alani her dalgada asagi kayip geri ziplardi.
            //
            // `weight(1f)`: Row'da agirliksiz cocuklar ONCE olculur, yani sol ve
            // sag gruplar dogal boyutlarini korur ve serit yalnizca ARTAN yeri
            // alir. Hazirlik fazi disinda hicbir dugum uretilmez ve satir
            // bugunku SpaceBetween davranisina birebir doner.
            //
            // ⚠ Seridin yuksekligi (34 dp) asagidaki IconButton'larin 36 dp'sinden
            // KUCUK olmak zorunda; aksi halde HUD uzar ve oynanis alani kayar.
            // `WavePreviewLogicTest` bu iliskiyi kilitliyor.
            WavePreviewBar(
                gameEngine = gameEngine,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

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
                            // BUG (Faz 12): payda `GameConfig.INITIAL_BASE_LIVES`
                            // sabitiydi (20), oysa gercek azami can bolume ve
                            // Tahkimat meta yukseltmesine gore degisir. Us Tamiri
                            // guclendiricisi cani geri verdiginde gosterge "22/20"
                            // gibi imkansiz degerler uretecekti. Tek dogruluk
                            // kaynagi motorun `maxLives` degeri.
                            text = stringResource(
                                R.string.hud_lives_value,
                                lives,
                                gameEngine.maxLives
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
                        onClick = {
                            // "Atlandi" sayilmasi icin dalganin GERCEKTEN
                            // hazirlik fazinda baslatilmis olmasi gerekir.
                            // Bayrak akistan DEGIL motorun anlik degerinden
                            // okunur: `gameState` bir kare bayat olabilir ve
                            // sayac kendiliginden bittiginde de buton bir kare
                            // daha cizili kalabilirdi.
                            val skipped =
                                gameEngine.gameState.value == GameState.PREPARATION
                            gameEngine.startNextWaveNow()
                            if (skipped) telemetry.notePrepTimerSkipped()
                        },
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
                    onClick = {
                        gameEngine.toggleGameSpeed()
                        // Motorun YENI degeri okunur, yerel `speed` degil:
                        // yerel kopya bu karede hala eski degeri tasir ve
                        // 1x -> 2x gecisi hic olculmezdi.
                        telemetry.noteGameSpeed(gameEngine.gameSpeed.value)
                    },
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


/**
 * Hazirlik geri sayimi — KENDI RECOMPOSE KAPSAMI.
 *
 * ---------------------------------------------------------------------------
 * NEDEN AYRI BIR COMPOSABLE
 * ---------------------------------------------------------------------------
 * `preparationTimer` motor tarafindan HER KARE guncellenir. Akis [HUDOverlay]
 * govdesinde okunurken, hazirlik fazi boyunca saniyede 60 kez TUM HUD yeniden
 * besteleniyordu: tedarik rozeti, can rozeti, dalga sayaci, hiz dugmesi,
 * hepsi — bolum basina ~600 recomposition, 55 bolumluk oturumda ~33.000.
 * Ustelik bu firtina bolum acilisinin en pahali anina biniyordu (biyom
 * recolor + sprite decode). `GameScreen`in kendi yorumu da "HUD her karede
 * recompose OLMAZ" diyordu; kural ihlal edilmisti.
 *
 * Compose recomposition'i, durumu OKUYAN en kucuk kapsamda sinirlar. Okuma bu
 * fonksiyona tasindigi icin artik yalnizca bu `Text` yeniden kosar. Ayni
 * desen dalga onizleme seridinde de kullaniliyor.
 *
 * ⚠ Akisi yukari tasimayin: `HUDOverlay` govdesinde `collectAsState` cagirmak
 * duzeltmeyi sessizce geri alir ve hicbir test kirilmaz.
 */
@Composable
private fun PreparationTimerText(gameEngine: GameEngine) {
    val prepTimer by gameEngine.preparationTimer.collectAsState()
    Text(
        text = stringResource(R.string.hud_prep_timer, prepTimer.toInt()),
        color = SleekGold,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )
}

/**
 * MEVZI TAVANI rozeti — KENDI RECOMPOSE KAPSAMI.
 *
 * `towerCount` yalnizca insa/satis aninda degisir (kare basina degil), ama
 * okumayi yine de ayri tutmak HUD'un geri kalanini bu olaydan tamamen ayirir;
 * [PreparationTimerText] ile ayni desen.
 *
 * Renk SEBEP tasir: tavan dolunca rozet kirmiziya doner, yani oyuncu insa
 * cubugunu ACMADAN once hakkinin bittigini gorur.
 */
@Composable
private fun EmplacementCapBadge(gameEngine: GameEngine, cap: Int) {
    val used by gameEngine.towerCount.collectAsState()
    val full = used >= cap
    val desc = stringResource(R.string.hud_emplacement_desc, used, cap)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (full) SleekRedBg else SleekSurfaceCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (full) SleekRedBorder else SleekBorderLight
        )
    ) {
        Text(
            text = stringResource(R.string.hud_emplacement_label, used, cap),
            color = if (full) SleekRedText else SleekTextAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics { contentDescription = desc }
                .testTag("emplacement_cap_text")
        )
    }
}

/**
 * DONMUS MEVZI rozeti — insa penceresi acik mi?
 *
 * Hazirlik fazinda yesil "PLAN", dalga sirasinda kirmizi "KILITLI". Kural
 * yalnizca reddedildiginde ogrenilseydi hazirlik fazi bir planlama ani degil
 * bir tuzak olurdu; rozet pencereyi GORUNUR kilar.
 */
@Composable
private fun BuildWindowBadge(waveRunning: Boolean) {
    val desc = stringResource(
        if (waveRunning) R.string.hud_build_locked_desc else R.string.hud_build_plan_desc
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (waveRunning) SleekRedBg else SleekSurfaceCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (waveRunning) SleekRedBorder else SleekPrimaryGreen
        )
    ) {
        Text(
            text = stringResource(
                if (waveRunning) R.string.hud_build_locked else R.string.hud_build_plan
            ),
            color = if (waveRunning) SleekRedText else SleekPrimaryGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics { contentDescription = desc }
                .testTag("build_window_text")
        )
    }
}
