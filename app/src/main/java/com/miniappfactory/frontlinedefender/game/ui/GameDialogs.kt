package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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

/**
 * Zafer ve yenilgi modallarinin AZAMI GENISLIGI.
 *
 * Bu bir estetik degeri DEGIL, ERISILEBILIRLIK PARAMETRESIDIR. Modalin alt
 * satirindaki eylem butonlari sanat varliklari ve en-boy oranini koruyorlar,
 * yani YUKSEKLIKLERI GENISLIKLERINDEN TURUYOR:
 *
 *   420 dp -> ic 392 -> 12 dp bosluk -> buton 190 dp -> 190/4,33 = 43,9 dp ✘
 *   480 dp -> ic 452 -> 12 dp bosluk -> buton 220 dp -> 220/4,33 = 50,8 dp ✔
 *
 * 44 dp dokunma tabani bu sayiya bagli oldugu icin deger TEK YERDE durur ve
 * `ArtButtonTouchTargetTest` onu BURADAN okur — testin kendi kopyasi olsaydi
 * biri burayi 420'ye cekince test yesil kalirdi.
 */
internal val ResultModalMaxWidth = 480.dp

/**
 * Modalin ic genisligi: dis dolgu (2x12) ve ic dolgu (2x14, compact) dusuldukten
 * sonra butonlara kalan satir. Test bu hesabi tekrarlamaz, buradan okur.
 */
internal val ResultModalInnerWidth = ResultModalMaxWidth - 24.dp - 28.dp

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
    // ⚠ 2026-08-26: SANAT UI ART PACK v2 ILE DEGISTI, PERDE DE ONUNLA BIRLIKTE.
    //
    // ESKI desen PARLAK bir woodland kamuflajiydi (olculen luma ort. 100,7 ·
    // std 43,1). Uzerindeki koyu perde (0,72 → 0,88) o parlakligi bastirmak
    // icindi ve cihaz geri bildirimiyle ayarlanmisti.
    //
    // YENI desen (pack'in `bg_camo_tactical_16x9`) ZATEN KOYU ve kenarlari
    // vinyetli: luma ort. **26,2** · std **14,2** · maks 101. Yani eski perde
    // AYNEN korunsaydi desen 0,72-0,88 opaklik altinda pratikte SIMSIYAH
    // olurdu ve kullanicinin 2026-08-21'de bildirdigi "kamuflaj deseni belli
    // olmuyor" sikayeti daha kotu bicimde geri gelirdi.
    //
    // Bu yuzden perde 0,72/0,88 → **0,10/0,34** yapildi. Perdenin ISI DEGISTI:
    // artik deseni bastirmak degil, ALT KENARI koyulastirmak (banner ile
    // birlesme cizgisi) ve sag ust skor cipinin arkasini oturtmak.
    //
    // OKUNABILIRLIK NEDEN BOZULMUYOR: bu ekrandaki her yazi ya sanat
    // plakasinin kendi koyu ic alaninda (baslik, birincil buton) ya da opak
    // bir kart icinde (skor cipi) duruyor. Hicbir metin dogrudan kamuflajin
    // uzerinde degil.
    //
    // Eski desen: incoming/bg_camo_ONCEKI_2026-08-26.webp (geri donus icin).
    // Yeni desenin uretici betigi: tools/ui_art_pipeline.py.
    val camoPainter = painterResource(R.drawable.bg_camo)

    // `BoxWithConstraints`: asagidaki yerlesim kullanilabilir YUKSEKLIGI
    // okumak zorunda (sanat plakalari en-boy oranini korudugu icin genislik
    // ile yukseklik birbirine bagli). Duz `Box` bu bilgiyi vermez.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // `sizeToIntrinsics = false`: aksi halde Box gorselin 1672x941
            // dogal boyutuna olcmeye calisirdi. Boyutu `fillMaxSize` belirler,
            // gorsel o kutuyu `Crop` ile doldurur (en-boy korunur, tasan kenar
            // kirpilir) — gerilme YOK.
            //
            // Cizim sirasi modifier sirasidir: once kamuflaj, sonra perde.
            .paint(
                painter = camoPainter,
                sizeToIntrinsics = false,
                contentScale = ContentScale.Crop
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SleekSurfaceHeader.copy(alpha = 0.10f),
                        SleekDarkBg.copy(alpha = 0.34f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // ---------------------------------------------------------------
        // YERLESIM — UI ART PACK v2
        //
        // Tum olculer YUKSEKLIK BUTCESINDEN turetilir, sabit dp DEGIL.
        //
        // NEDEN: oyun `sensorLandscape` ve bu Box'in altinda banner yuvasi
        // var; kullanilabilir yukseklik 360 dp'lik bir cihazda ~300 dp'ye
        // kadar duser. Sanat plakalari en-boy oranini KORUDUGU icin
        // (bkz. ArtSurfaces.kt) genisligi buyutmek yuksekligi de buyutur —
        // sabit bir genislik verseydik dar cihazda plakalar ust uste
        // binerdi. Burada tersini yapiyoruz: once her ogeye yuzde olarak
        // yukseklik payi ayrilir, genislik ORANDAN geri hesaplanir.
        //
        //   baslik plakasi   %32
        //   birincil buton   %21
        //   disli + etiket   %17
        //   bosluklar        %14
        //   dis dolgu        %16
        //                    ----
        //                    %100
        //
        // Ust sinir olarak ekran genisliginin bir yuzdesi de uygulanir ki
        // cok genis (tablet) ekranda plaka devlesip komik gorunmesin.
        // ---------------------------------------------------------------
        val availableHeight = maxHeight
        val availableWidth = maxWidth

        val headerWidth = minOf(
            availableWidth * 0.86f,
            availableHeight * 0.32f * Art.HeaderPlate.aspect
        )
        val primaryWidth = minOf(
            availableWidth * 0.62f,
            availableHeight * 0.21f * Art.PrimaryButton.aspect
        )
        val gearSize = minOf(58.dp, availableHeight * 0.15f)
        val gap = (availableHeight * 0.045f).coerceIn(6.dp, 16.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // BASLIK PLAKASI. Marka adi `translatable="false"` — her dilde
            // ayni; alt baslik cevrilir. Ikisi de sanata GOMULU DEGIL,
            // plakanin olculmus ic alanina cizilir.
            ArtHeaderPlate(
                title = stringResource(R.string.dialog_game_title),
                subtitle = stringResource(R.string.dialog_game_subtitle),
                modifier = Modifier.width(headerWidth),
                testTag = "menu_title_plate"
            )

            // BIRINCIL EYLEM. `testTag` DEGISMEDI ("play_game_button") —
            // mevcut UI testleri bu etikete bagli ve sanat degisikligi
            // testleri kirmamali.
            ArtPrimaryButton(
                label = stringResource(R.string.dialog_start_operation),
                onClick = onStartGame,
                modifier = Modifier.width(primaryWidth),
                testTag = "play_game_button"
            )

            // AYARLAR GIRISI.
            //
            // Ikonun ALTINDAKI YAZI KORUNDU: ikon tek basina ne oldugunu
            // anlatmiyor ve renk tek ayrim kanali olamaz (erisilebilirlik).
            // Sanat dislisi eski `spr_ic_settings` yerine gecti; davranis
            // birebir ayni kaldi.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ArtSurface(
                    spec = Art.GearIcon,
                    modifier = Modifier
                        .width(gearSize)
                        .testTag("settings_button"),
                    onClick = { settingsOpen = true },
                    contentDescription = stringResource(R.string.settings_open_desc)
                ) {}
                Text(
                    text = stringResource(R.string.settings_button_label),
                    color = ArtTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        // EN YUKSEK SKOR — mockup'taki gibi SAG UST KOSEDE, serbest katman.
        //
        // Kolonun ICINDE degil cunku orada dordumcu bir satir olarak
        // yukseklik butcesini bozardi ve skor 0 iken satir kaybolunca tum
        // menu zipllardi. Kosede duran bir cip, varligi ve yoklugu duzeni
        // DEGISTIRMEZ.
        if (highScore > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(
                        SleekSurfaceCard.copy(alpha = 0.85f),
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, SleekBorderLight, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                SpriteIcon(
                    id = R.drawable.spr_ic_victory_star,
                    size = 16.dp,
                    contentDescription = stringResource(R.string.dialog_high_score_icon_desc)
                )
                Text(
                    text = stringResource(R.string.dialog_high_score, highScore),
                    color = ArtAccentGoldGlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        // CIHAZDA BULUNDU (2026-08-18, Galaxy S8 yatay 740x360 dp):
        // modal EKRANA SIGMIYORDU ve **butonlar kirpiliyordu**. Oyuncu zaferi
        // goruyor ama "SONRAKI BOLUM"e ulasamiyordu — bir sonraki bolume
        // gecmenin tek yolu uygulamayi kapatip acmakti.
        //
        // Sebep: icerik (baslik 28sp + 44 dp yildizlar + dort satir istatistik
        // + butonlar, aralarinda 16 dp) 360 dp'lik yatay yukseklige sigmiyor.
        // Zafer ekrani buyudukce (kazanilan coin, yildiz ipucu) tasma
        // kacinilmazdi.
        //
        // Cozum iki katmanli:
        //  1) Yukseklik ekrana KILITLENIR (`heightIn`), icerik kaydirilir.
        //  2) **Butonlar kaydirma alaninin DISINDA** — yani icerik ne kadar
        //     buyurse buyusun butonlar HER ZAMAN gorunur. Kirpilacak sey
        //     istatistik olur, cikis yolu asla.
        val compact = maxHeight < 420.dp
        val gap = if (compact) 8.dp else 16.dp
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SleekDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SleekPrimaryGreen),
            modifier = Modifier
                .padding(12.dp)
                // 420 -> 480: SAYISAL ZORUNLULUK, estetik degil. Butonlar artik
                // sanat varliklari ve en-boy oranini koruyorlar; 420 dp'de ic
                // genislik 392, 12 dp bosluk dusunce buton 190 dp olur ve
                // 190/4.33 = **43,9 dp** yukseklik cikar — 44 dp dokunma
                // tabaninin ALTINDA. 480'de ic 452 -> buton 220 dp ->
                // 220/4.33 = 50,8 dp ✔. Ekran 740 dp genis, 480 sorun degil.
                .widthIn(max = ResultModalMaxWidth)
                .heightIn(max = maxHeight - 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(if (compact) 14.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
              Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap)
              ) {
                // Baslik ARTIK sanat plakasinin uzerinde. Metin plakaya
                // GOMULU DEGIL; `AutoShrinkText` ile ic alana cizilir, yani
                // TR/EN ve `fontScale` degisimleri hâlâ calisir.
                ArtHeaderPlate(
                    title = stringResource(R.string.dialog_victory_title),
                    modifier = Modifier.fillMaxWidth(0.62f),
                    titleColor = ArtTextPrimary,
                    titleSize = 24.sp,
                    testTag = "victory_title_plate"
                )

                // 3 Stars Display — Faz 3: asset pack icon_victory_star.
                // Kazanilmayan yildiz ayni sprite'in soluk/gri hali (tek dosya).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..3) {
                        val isEarned = i <= stars
                        SpriteIcon(
                            id = R.drawable.spr_ic_victory_star,
                            // 44 -> 40: baslik plakasi 64 dp aldi, dikey butce
                            // (bkz. docs/UI_ART_INTEGRATION_SPEC.md §3.4)
                            // yildiz satirindan 4 dp geri istiyor. Sprite ayni.
                            size = 40.dp,
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

              } // <- kaydirilabilir istatistik alani biter

                // BUTONLAR: kaydirma alaninin DISINDA, yani her zaman gorunur.
                // BUTON SIRASI KORUNDU: ikincil SOLDA, birincil eylem her
                // zaman SAGDA. Kas hafizasi; sanat degisti diye yer degismez.
                //
                // Tek buton kalinca (kampanya bitti / siradaki kilitli) "BOLUM
                // SEC" BIRINCIL sanata terfi eder — eskiden de rengi
                // `SleekPrimaryGreen`e donuyordu, ayni kural.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasNext = onNextLevel != null && nextLevelId != null
                    if (hasNext) {
                        ArtSecondaryButton(
                            label = stringResource(R.string.dialog_level_select),
                            onClick = onLevelSelect,
                            modifier = Modifier.weight(1f),
                            testTag = "victory_level_select_button"
                        )
                    } else {
                        ArtPrimaryButton(
                            label = stringResource(R.string.dialog_level_select),
                            onClick = onLevelSelect,
                            modifier = Modifier.weight(1f),
                            testTag = "victory_level_select_button"
                        )
                    }
                    if (hasNext) {
                        ArtPrimaryButton(
                            label = stringResource(R.string.dialog_next_level),
                            onClick = onNextLevel!!,
                            modifier = Modifier.weight(1f),
                            testTag = "victory_next_level_button"
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        // ⚠ YUKSEKLIK KILIDI + KAYDIRMA — 2026-08-26'da EKLENDI.
        //
        // `VictoryModal` ve `PauseMenuModal` bu iki katmani 2026-08-18'de
        // cihaz bulgusuyla kazanmisti; YENILGI MODALI ATLANMISTI. Ayni hata
        // burada da canliydi: TR govde metni (`dialog_defeat_body`) uc satira
        // ciktiginda 360 dp'lik yatay ekranda butonlar asagi itiliyor ve
        // "TEKRAR DENE" ekran disinda kaliyordu — yani oyuncunun yenilgiden
        // cikis yolu yoktu. Sanat butonlari eklemek bu tasmayi GORUNUR yapardi,
        // yaratmazdi; once tasma kapatildi.
        //
        // Cozum zaferdekiyle BIREBIR ayni:
        //  1) yukseklik ekrana kilitlenir (`heightIn`), govde kaydirilir,
        //  2) BUTONLAR kaydirma alaninin DISINDA — kirpilacak sey aciklama
        //     metni olur, cikis yolu ASLA.
        val compact = maxHeight < 420.dp
        val gap = if (compact) 8.dp else 16.dp
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SleekDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SleekRed),
            modifier = Modifier
                .padding(12.dp)
                // Zafer modaliyla AYNI taban ve AYNI gerekce: sanat butonlari
                // en-boy oranini korudugu icin 420 dp'de yukseklik 43,9 dp'ye
                // dusup 44 dp dokunma tabaninin altina iniyordu.
                .widthIn(max = ResultModalMaxWidth)
                .heightIn(max = maxHeight - 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(if (compact) 14.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    // Faz 3: asset pack icon_defeat_skull. Sprite DEGISMEDI.
                    SpriteIcon(
                        id = R.drawable.spr_ic_defeat_skull,
                        // 52 -> 44: baslik plakasi dikey yer aldi
                        // (docs/UI_ART_INTEGRATION_SPEC.md §3.4).
                        size = 44.dp,
                        contentDescription = null
                    )

                    ArtHeaderPlate(
                        title = stringResource(R.string.dialog_defeat_title),
                        modifier = Modifier.fillMaxWidth(0.62f),
                        titleColor = SleekRedText,
                        titleSize = 24.sp,
                        testTag = "defeat_title_plate"
                    )

                    Text(
                        // `maxLines` YOK: son cumle kirpilmaz. Tasarsa artik
                        // kaydirma alaninin icinde tasar, butonlari itmez.
                        text = stringResource(R.string.dialog_defeat_body, waveIndex + 1),
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }

                // BUTONLAR: kaydirma alaninin DISINDA, yani her zaman gorunur.
                // Sira korundu — ikincil (ANA MENU) solda, birincil eylem
                // (TEKRAR DENE) sagda.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtSecondaryButton(
                        label = stringResource(R.string.dialog_main_menu),
                        onClick = onMainMenu,
                        modifier = Modifier.weight(1f),
                        testTag = "defeat_menu_button"
                    )
                    ArtPrimaryButton(
                        label = stringResource(R.string.dialog_retry),
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        testTag = "defeat_retry_button"
                    )
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

    // ⛔ BU MODAL, ZAFER MODALIYLA AYNI YAPISAL HATAYI TASIYORDU.
    //
    // Yukseklik sinirlanmamis, icerik kaydirilamaz ve bosluklar sabitti. Test
    // cihazinda (Galaxy S8, yatay 740x360 dp) dorduncu buton — ANA MENU —
    // ekranin altinda YARIM kaliyordu: oyuncu ana menuye donemiyordu.
    //
    // Zafer modalinde ayni hata duzeltilirken bu modal GOZDEN KACTI; ikisi
    // ayni cati altinda olmadigi icin duzeltme kendiliginden buraya gelmedi.
    // Simdi ikisi de ayni uc kurali uyguluyor:
    //  1) Yukseklik ekrana KILITLENIR (`heightIn`), icerik kaydirilir.
    //  2) Dar ekranda bosluklar ve dolgu kucululur (`compact`).
    //  3) Baslik sabit kalir, butonlar kaydirma alanindadir.
    //
    // BUTON SIRASI DEGISTIRILMEDI. Zafer modalinde birincil butonlar kaydirma
    // alaninin disinda tutulmustu; burada ayni seyi yapmak DEVAM ET'i listenin
    // basina veya sonuna tasimak demekti ve duraklatma menusu oyuncunun kas
    // hafizasiyla kullandigi bir ekran. Compact modda dort buton 360 dp'ye
    // zaten sigiyor (olcum: ~280 dp), yani kaydirma bir emniyet agi; sirayi
    // bozmaya deger bir kazanc yok.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 420.dp
        val gap = if (compact) 8.dp else 14.dp
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SleekDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SleekBorderLight),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .heightIn(max = maxHeight - 24.dp)
                .padding(if (compact) 8.dp else 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(if (compact) 14.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                Text(
                    text = stringResource(R.string.dialog_paused_title),
                    color = SleekTextAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 18.sp else 22.sp,
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier
                        // `fill = false`: butonlar sigiyorsa modal onlarin
                        // boyunda kalir, ekrani gereksiz yere doldurmaz.
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {

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

