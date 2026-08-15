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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.model.GameConfig

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
    modifier: Modifier = Modifier
) {
    // Bir sonraki oynanabilir bolum — ekran bunu vurgular.
    //
    // Bilincli olarak `remember` YOK: `starsFor` SharedPreferences okuyor, yani
    // Compose snapshot state degil. remember edilse zaferden sonra bayat kalirdi.
    // 22 ogelik tarama kare dongusunde degil, yalnizca composition'da kosar.
    val nextLevel = GameConfig.CAMPAIGN.firstOrNull {
        progress.isUnlocked(it.levelId) && progress.starsFor(it.levelId) == 0
    }?.levelId ?: 1

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

                // Coin — SADECE bu ekranda. Savas HUD'inda asla.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33FFD54F))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("COIN", color = Color(0xFFFFD54F), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("${progress.coins}", color = Color(0xFFFFF3C4), fontSize = 15.sp,
                        fontWeight = FontWeight.Black)
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
                                onPlayLevel(spec.levelId)
                            } else if (progress.tryUnlock(spec.levelId)) {
                                onPlayLevel(spec.levelId)
                            }
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                }
            }

            Text(
                text = "Swipe horizontally to browse all " +
                    "${GameConfig.CAMPAIGN_LEVEL_COUNT} operations",
                color = Color(0x99C5D6B4),
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center
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
            text = if (act == 1) "ACT I" else "ACT II",
            color = Color(0xFF8FA87A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
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
                text = "${spec.levelId}",
                color = if (isNext) Color(0xFF1A1A0E) else Color(0xFFE8F0DC),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = spec.displayName,
            color = Color(0xFFDCE8CC),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Text(
            text = "${spec.waveCount} waves",
            color = Color(0x99C5D6B4),
            fontSize = 10.sp
        )

        if (spec.overlay == GameConfig.MapOverlay.NIGHT) {
            Text("NIGHT", color = Color(0xFF7FA6D6), fontSize = 9.sp,
                fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(6.dp))

        // Yildizlar (0-3)
        Row {
            repeat(3) { i ->
                Text(
                    text = if (i < stars) "*" else "-",
                    color = if (i < stars) Color(0xFFFFD54F) else Color(0x55FFFFFF),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(2.dp))
            }
        }

        Spacer(Modifier.height(6.dp))

        // Durum satiri: kilitliyse bedel, degilse eylem
        when {
            !unlocked && spec.deploymentCost > 0 -> Text(
                text = "UNLOCK ${spec.deploymentCost}",
                color = Color(0xFFFFD54F),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            !unlocked -> Text(
                text = "LOCKED",
                color = Color(0x99FFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            isNext -> Text(
                text = "DEPLOY",
                color = Color(0xFFFFD54F),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            else -> Text(
                text = "REPLAY",
                color = Color(0xFFA8C48C),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
