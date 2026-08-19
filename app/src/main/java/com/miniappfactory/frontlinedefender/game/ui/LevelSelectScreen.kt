package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.Mission
import com.miniappfactory.frontlinedefender.game.economy.WeeklyMission
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.ui.theme.SleekGold

/**
 * Faz 4 — BOLUM SECME EKRANI.
 *
 * Landscape'e uygun, YATAY kaydirmali kampanya seridi. 22 bolum tek satirda
 * ilerler; her kart bolum no, harita adi, Act, yildizlar ve kilit durumunu
 * gosterir.
 *
 * PARA BIRIMI KURALI (DECISIONS): **Coin yalnizca BURADA gorunur.** Savas
 * HUD'inda Coin asla cizilmez; savas ici para birimi "Tedarik"tir ve ikisi
 * hicbir zaman ayni ekranda olmaz.
 *
 * Faz 6: tum metinler `strings.xml` / `values-tr/strings.xml`'e tasindi.
 * Harita adi artik `GameConfig.LevelSpec.displayName` (Ingilizce fallback)
 * DEGIL, `mapNameRes(spec.mapId)` uzerinden geliyor.
 */

/**
 * Bolum secme ekraninin ihtiyac duydugu ilerleme/ekonomi arayuzu.
 *
 * Ekonominin KENDISI (coin kazanma/harcama dengesi, kaynak/gider tablosu)
 * `game-economy-progression-designer` ajanina ait. Burada yalnizca ekranin
 * ihtiyac duydugu SOZLESME tanimlanir; kalici implementasyon sonra baglanir.
 */
interface CampaignProgress {
    /** 0-3. Hic oynanmamis bolum icin 0. */
    fun starsFor(levelId: Int): Int

    /** Bolum oynanabilir mi? */
    fun isUnlocked(levelId: Int): Boolean

    /** Meta para birimi bakiyesi (Coin). */
    val coins: Int

    /**
     * Kilidi acmayi dener. Bakiye yetmezse `false` doner ve HICBIR sey degismez.
     * Su an bellek-ici; kalicilik ekonomi ajaninin isi.
     */
    fun tryUnlock(levelId: Int): Boolean

    // ---------------------------------------------------------------------------
    // Faz 14 — GOREV PANELI YUZEYI
    //
    // FUN_AUDIT: gorev sistemi eksiksiz yazilmisti ama HIC UI'si yoktu ve
    // `claimCompletedMissions()` sifir kez cagriliyordu — tasarlanan haftalik
    // ~3.620 coin'lik geri donus gelirinin TAMAMI oyuncuya ulasmiyordu.
    //
    // Hepsi varsayilanli: gorev tasimayan bir implementasyon (`InMemoryCampaignProgress`,
    // test sahteleri) hicbir sey yazmadan derlenmeye devam eder ve panel girisi
    // kendini gizler.
    // ---------------------------------------------------------------------------

    /** Bugunun 3 gorevi. Bos liste = panel girisi cizilmez. */
    val todaysMissions: List<Mission> get() = emptyList()

    /** Haftalik gorevler (GDD E.2 — iki adet). */
    val weeklyMissions: List<WeeklyMission> get() = emptyList()

    /** Saat suphesi damperi; panel odul etiketlerini buna gore gosterir. */
    val missionRewardMultiplier: Double get() = 1.0

    /** Tamamlanmis ama alinmamis gorev sayisi — rozetin ta kendisi. */
    val claimableMissionCount: Int
        get() = todaysMissions.count { it.isComplete && !it.claimed } +
            weeklyMissions.count { it.isComplete && !it.claimed }

    /** Odulleri oder ve "alindi" isaretler. Iki kez odemez. Donen deger coin. */
    fun claimCompletedMissions(): Int = 0

    /** Panel acilirken gun/hafta donusunu yeniden degerlendirir. */
    fun refreshMissions() {}
}

/**
 * GECICI implementasyon.
 *
 * - Yildizlar `SaveManager` uzerinden KALICI (zaten vardi).
 * - Kilit acma bellek-ici: `unlockedByCoin` uygulama kapaninca sifirlanir.
 * - Ilerleme kurali: bolum 1 her zaman acik; sonraki bolum onceki bolumden
 *   **en az 1 yildiz** alinmissa acilir. `deploymentCost > 0` olan bolumler
 *   ayrica Coin ile de acilabilir.
 *
 * Kalici hale gelince tek yapilacak: bu sinifi ekonomi ajaninin
 * implementasyonuyla degistirmek. Ekran degismez.
 */
class InMemoryCampaignProgress(
    private val saveManager: SaveManager,
    initialCoins: Int = 0
) : CampaignProgress {

    // Compose snapshot listesi: eklendiginde ekran yeniden cizilir.
    private val unlockedByCoin = mutableStateListOf<Int>()
    private var coinBalance by mutableIntStateOf(initialCoins)

    override val coins: Int get() = coinBalance

    override fun starsFor(levelId: Int): Int = saveManager.getLevelStars(levelId)

    override fun isUnlocked(levelId: Int): Boolean {
        if (levelId <= 1) return true
        if (levelId in unlockedByCoin) return true
        return starsFor(levelId - 1) >= 1
    }

    override fun tryUnlock(levelId: Int): Boolean {
        val spec = GameConfig.levelSpec(levelId)
        if (spec.deploymentCost <= 0) return false
        if (coinBalance < spec.deploymentCost) return false
        coinBalance -= spec.deploymentCost
        unlockedByCoin.add(levelId)
        return true
    }
}

@Composable
fun LevelSelectScreen(
    progress: CampaignProgress,
    onPlayLevel: (Int) -> Unit,
    onBack: () -> Unit,
    /**
     * Faz 17: Cephanelik girisi ARTIK BU EKRANIN ISI.
     *
     * CIHAZDA GORULDU: giris `GameScreen` icinde `TopEnd` + `top = 62.dp` ile
     * SERBEST katman olarak duruyordu ve yatayda (360 dp yukseklik) tam olarak
     * bolum kartlarinin ustune biniyordu — 5. kartin basligini ortuyordu.
     * Kartlarin konumu ekran boyutuna gore degistigi icin sabit bir dp ofseti
     * bu carpismayi kacinilmaz kilar.
     *
     * Artik baslik satirinin ICINDE, Gorevler ve Coin ile ayni sag blokta.
     * Uc cip tek bir saga yasli grup olusturur, aralarinda bosluk kalmaz.
     */
    onOpenArmory: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Faz 20 — PERDE ACILIS KARTI (`docs/STORY.md` §4).
     *
     * `null` (varsayilan) = kart HIC cizilmez ve bu ekranin davranisi
     * bugunkuyle birebir aynidir. Mevcut cagrilar ve testler degismeden
     * derlenir; uretimde acmak icin cagriya tek adlandirilmis arguman
     * eklenir:
     *
     *     actIntroStore = remember(saveManager) { SaveManagerActIntroStore(saveManager) }
     *
     * Kalicilik `SaveManager`in var olan ipucu bayragi API'si uzerinden
     * yurur; `SaveManager` DEGISTIRILMEDI (gerekce `ActIntroOverlay.kt`).
     */
    actIntroStore: ActIntroStore? = null
) {
    // Bir sonraki oynanabilir bolum — ekran bunu vurgular.
    //
    // Bilincli olarak `remember` YOK: `starsFor` SharedPreferences okuyor, yani
    // Compose snapshot state degil. remember edilse zaferden sonra bayat kalirdi.
    // 22 ogelik tarama kare dongusunde degil, yalnizca composition'da kosar.
    val nextLevel = GameConfig.CAMPAIGN.firstOrNull {
        progress.isUnlocked(it.levelId) && progress.starsFor(it.levelId) == 0
    }?.levelId ?: 1

    // Faz 14 — gorev paneli. Tam ekran KATMAN: aciken bolum kartlari ve alttaki
    // R1 seridi erisilemez olur, boylece kazara sevk/reklam olmaz (Cephanelik
    // katmaninin ayni deseni).
    var missionsOpen by remember { mutableStateOf(false) }

    // Perde acilis karti bekleyen bolum. null = kart yok.
    //
    // Bolum ID'si TUTULUR, sadece bir bayrak degil: kart onaylandiginda
    // hangi bolume sevk edilecegi kartin acildigi andaki karardir. Boylece
    // kart aciken arkadaki listede bir sey degisse bile yanlis bolum
    // baslatilamaz.
    var pendingIntroLevel by remember { mutableStateOf<Int?>(null) }

    /**
     * Sevk kapisi: perde acilisiysa ve kart daha once gorulmediyse ONCE kart,
     * sonra savas. Diger her durumda dogrudan savas — yani bu kapi kampanya
     * bolumlerinin 50'sinde hicbir sey yapmaz.
     */
    fun deploy(levelId: Int) {
        val store = actIntroStore
        if (store != null &&
            ActIntro.isActOpener(levelId) &&
            !store.isSeen(ActIntro.actOf(levelId))
        ) {
            pendingIntroLevel = levelId
        } else {
            onPlayLevel(levelId)
        }
    }
    val missionsAvailable = progress.todaysMissions.isNotEmpty() ||
        progress.weeklyMissions.isNotEmpty()
    val claimableMissions = progress.claimableMissionCount

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF10160E), Color(0xFF1D2617), Color(0xFF0B0E08))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ---- Ust serit: geri, baslik, COIN BAKIYESI --------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE6121A10))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.level_back),
                    color = Color(0xFFB8C9A8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )

                Spacer(Modifier.width(14.dp))

                Text(
                    text = stringResource(R.string.level_campaign_title),
                    color = Color(0xFFE8F0DC),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )

                Spacer(Modifier.weight(1f))

                // GOREVLER girisi — coin cipinin HEMEN SOLUNDA, sag bloga
                // bitisik (kullanici geri bildirimi: "ikonlar saga yasli olsun,
                // bosluk kalmasin"). Cephanelik girisi bu satirin ALTINDA, ayni
                // sag kenarda duruyor; ucu birlikte tek bir sag sutun olusturur.
                //
                // Rozet: tamamlanmis ama ALINMAMIS gorev sayisi. Panel
                // acilmadan da gorunur olmasi bu ekranin tek isi — FUN_AUDIT'in
                // "kimse fark etmiyor" bulgusu tam olarak buydu.
                if (missionsAvailable) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (claimableMissions > 0) Color(0x554C7A2E) else Color(0x334C7A2E)
                            )
                            .clickable { missionsOpen = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("open_missions")
                    ) {
                        Text(
                            text = stringResource(R.string.mission_open),
                            color = if (claimableMissions > 0) Color(0xFFCDEB9E) else SleekGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        if (claimableMissions > 0) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFF9CD65B))
                            ) {
                                Text(
                                    text = stringResource(R.string.mission_badge, claimableMissions),
                                    color = Color(0xFF14200C),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                }

                // CEPHANELIK — Gorevler ile Coin arasinda, ayni sag blokta.
                Text(
                    text = stringResource(R.string.shop_open),
                    color = SleekGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x334C7A2E))
                        .clickable { onOpenArmory() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("open_armory")
                )

                Spacer(Modifier.width(8.dp))

                // Coin — SADECE bu ekranda. Savas HUD'inda asla.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33FFD54F))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // "COIN" translatable=false: meta para birimi adi Turkcede
                    // de "Coin" (DECISIONS — para birimi adlandirmasi).
                    Text(
                        text = stringResource(R.string.level_coin_label),
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.level_coin_amount, progress.coins),
                        color = Color(0xFFFFF3C4),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }

            // ---- Yatay kampanya seridi ------------------------------------
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var lastAct = 0
                GameConfig.CAMPAIGN.forEach { spec ->
                    if (spec.act != lastAct) {
                        lastAct = spec.act
                        ActDivider(act = spec.act)
                    }
                    LevelCard(
                        spec = spec,
                        stars = progress.starsFor(spec.levelId),
                        unlocked = progress.isUnlocked(spec.levelId),
                        isNext = spec.levelId == nextLevel,
                        onClick = {
                            if (progress.isUnlocked(spec.levelId)) {
                                deploy(spec.levelId)
                            } else if (progress.tryUnlock(spec.levelId)) {
                                deploy(spec.levelId)
                            }
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                }
            }

            Text(
                text = pluralStringResource(
                    R.plurals.level_browse_hint,
                    GameConfig.CAMPAIGN_LEVEL_COUNT,
                    GameConfig.CAMPAIGN_LEVEL_COUNT
                ),
                color = Color(0x99C5D6B4),
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        // Gorev paneli — TAM EKRAN katman, kok Box'in en ustunde.
        if (missionsOpen) {
            MissionsScreen(
                progress = progress,
                onClose = { missionsOpen = false }
            )
        }

        // Perde acilis karti — gorev panelinin de USTUNDE. Yalnizca dokunma
        // sonucu acildigi icin panelle ayni anda gorunemez; siralama yine de
        // sabitlendi ki ikisi carpisirsa kart kazansin (oyuncu SEVK etmek
        // uzereydi).
        //
        // Bayrak kart KAPANIRKEN yazilir, acilirken degil: acilis ile kapanis
        // arasinda uygulama olduruluse oyuncu brifingi hic okumamis olur ve
        // bir dahaki sefere tekrar gormelidir.
        pendingIntroLevel?.let { levelId ->
            ActIntroOverlay(
                act = ActIntro.actOf(levelId),
                onDismiss = {
                    actIntroStore?.markSeen(ActIntro.actOf(levelId))
                    pendingIntroLevel = null
                    onPlayLevel(levelId)
                }
            )
        }
    }
}

@Composable
private fun ActDivider(act: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Text(
            text = stringResource(actLabelRes(act)),
            color = Color(0xFF8FA87A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(2.dp)
                .height(96.dp)
                .background(Color(0x558FA87A))
        )
    }
}

@Composable
private fun LevelCard(
    spec: GameConfig.LevelSpec,
    stars: Int,
    unlocked: Boolean,
    isNext: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isNext -> Color(0xFFFFD54F)
        unlocked -> Color(0x66A8C48C)
        else -> Color(0x33FFFFFF)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(126.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (unlocked) Color(0xCC1E2A18) else Color(0xCC15170F))
            .clickable(enabled = true, onClick = onClick)
            .padding(10.dp)
            .alpha(if (unlocked) 1f else 0.62f)
    ) {
        // Bolum numarasi
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(borderColor.copy(alpha = if (isNext) 0.9f else 0.25f))
        ) {
            Text(
                text = stringResource(R.string.level_number, spec.levelId),
                color = if (isNext) Color(0xFF1A1A0E) else Color(0xFFE8F0DC),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(6.dp))

        // TASMA — bu ekranin en riskli yeri. Kart genisligi SABIT 126 dp
        // (yatay seritte gorsel ritim icin) yani ic genislik 106 dp. En uzun
        // adlar: "Village Outskirts" (EN, 17 kr) ve "Karanlik Bogaz" (TR).
        // Iki satira sarar; UCUNCU satira tasarsa punto kuculur. Kesme YOK:
        // "Karanlik Bo…" hangi harita oldugunu belirsizlestirir.
        AutoShrinkText(
            text = stringResource(mapNameRes(spec.mapId)),
            color = Color(0xFFDCE8CC),
            fontWeight = FontWeight.Bold,
            maxFontSize = 11.sp,
            minFontSize = 8.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // ANLATI — bolum durum satiri (`docs/STORY.md` §3).
        //
        // Bolum adinin hemen ALTINDA, tek satir, emir kipi ("Gecidi tut.").
        // Bu, oyuncunun kampanyada NEDEN savastigini ogrendigi tek daimi
        // yuzey; perde karti bes kez cikar, bu satir 55 kez.
        //
        // Butce 24 karakter (turetilis `strings_story.xml` basliginda,
        // `StoryStringsTest` ile kilitli) ama yine de `AutoShrinkText`:
        // kirpma bu kartta bilgi YOK EDER ("Komuta tankini v…" hangi emir
        // oldugunu belirsizlestirir). Bos dize cizilmez — eksik bir ceviri
        // yerlesimi bozmak yerine yalnizca satiri yok eder.
        val objective = levelObjectiveOrEmpty(spec.levelId)
        if (objective.isNotEmpty()) {
            AutoShrinkText(
                text = objective,
                color = Color(0xCC9FBF84),
                maxFontSize = 9.sp,
                minFontSize = 7.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = pluralStringResource(
                R.plurals.level_wave_count,
                spec.waveCount,
                spec.waveCount
            ),
            color = Color(0x99C5D6B4),
            fontSize = 10.sp,
            maxLines = 1
        )

        if (spec.overlay == GameConfig.MapOverlay.NIGHT) {
            Text(
                text = stringResource(R.string.level_night_badge),
                color = Color(0xFF7FA6D6),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(6.dp))

        // Yildizlar (0-3). Glifler translatable="false".
        val starFilled = stringResource(R.string.level_star_filled_glyph)
        val starEmpty = stringResource(R.string.level_star_empty_glyph)
        Row {
            repeat(3) { i ->
                Text(
                    text = if (i < stars) starFilled else starEmpty,
                    color = if (i < stars) Color(0xFFFFD54F) else Color(0x55FFFFFF),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(2.dp))
            }
        }

        Spacer(Modifier.height(6.dp))

        // Durum satiri: kilitliyse bedel, degilse eylem.
        // 106 dp'lik sabit genislikte en uzun karsilik "KILIDI AC 350" (TR);
        // hepsi AutoShrinkText ile tek satirda kalir, kesilmez.
        val statusColor: Color
        val statusText: String
        val statusSize = if (unlocked && isNext) 11.sp else 10.sp
        val statusWeight = if (unlocked && isNext) FontWeight.Black else FontWeight.Bold
        when {
            !unlocked && spec.deploymentCost > 0 -> {
                statusText = stringResource(R.string.level_unlock_cost, spec.deploymentCost)
                statusColor = Color(0xFFFFD54F)
            }
            !unlocked -> {
                statusText = stringResource(R.string.level_locked)
                statusColor = Color(0x99FFFFFF)
            }
            isNext -> {
                statusText = stringResource(R.string.level_deploy)
                statusColor = Color(0xFFFFD54F)
            }
            else -> {
                statusText = stringResource(R.string.level_replay)
                statusColor = Color(0xFFA8C48C)
            }
        }
        AutoShrinkText(
            text = statusText,
            color = statusColor,
            fontWeight = statusWeight,
            maxFontSize = statusSize,
            minFontSize = 8.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
