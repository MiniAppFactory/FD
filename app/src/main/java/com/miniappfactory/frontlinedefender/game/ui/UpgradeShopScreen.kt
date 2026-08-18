package com.miniappfactory.frontlinedefender.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.MetaUpgrades
import com.miniappfactory.frontlinedefender.game.economy.PurchaseDecision
import com.miniappfactory.frontlinedefender.game.economy.UpgradeLine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.ui.theme.*

/**
 * CEPHANELIK — kalici meta yukseltme dukkani.
 *
 * NEDEN VAR: kullanici testinde "ilk levellarda hic ihtiyacim olmadi" dedi ve
 * haklıydi — coin'in gidecek GORUNUR bir yeri yoktu, dolayisiyla reklam izlemek
 * de anlamsizdi. Ekonomi modeli (5 yukseltme hatti, rezerv kurali, fiyat
 * tablolari) bastan hazirdi; eksik olan tek sey bu ekrandi.
 *
 * KONUSLANMA REZERVI GORUNUR: GDD'nin soft-lock garantisi, bakiyeyi siradaki
 * bolum kilidinin altina dusurecek satin almayi REDDETMEK. Oyuncunun bunu bir
 * hata sanmamasi icin rezerv miktari ve sebebi ustte yazili duruyor.
 */
@Composable
fun UpgradeShopScreen(
    progress: CampaignProgressImpl,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Tam ekran katman: geri tusu ile de kapanmali. Bkz. MissionsScreen —
    // cihazda oyuncu gorev panelinde mahsur kaldi (2026-08-18).
    BackHandler(onBack = onBack)

    // Satin alma sonrasi kartlarin yenilenmesi icin. CampaignProgressImpl kendi
    // icinde mutableStateOf tutuyor ama rank/fiyat okumalari turetilmis oldugu
    // icin acik bir tetikleyici davranisi netlestiriyor.
    var buyTick by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SleekSurfaceHeader, SleekDarkBg)))
    ) {
        Column(Modifier.fillMaxSize()) {

            // ---- baslik seridi ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurfaceHeader)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.level_back),
                    color = SleekTextAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .testTag("shop_back")
                )
                Spacer(Modifier.width(20.dp))
                Text(
                    text = stringResource(R.string.shop_title),
                    color = SleekTextAccent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.weight(1f))
                key(buyTick) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33FFD700))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        SpriteIcon(
                            id = R.drawable.spr_ic_coins,
                            size = 18.dp,
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(R.string.level_coin_amount, progress.coins),
                            color = SleekGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // ---- rezerv aciklamasi ----
            key(buyTick) {
                if (progress.reserve > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.shop_reserve, progress.reserve),
                            color = SleekGold.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stringResource(R.string.shop_reserve_hint),
                            color = SleekTextAccent.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // ---- yukseltme kartlari ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                UpgradeLine.entries.forEach { line ->
                    key(line, buyTick) {
                        UpgradeCard(
                            line = line,
                            progress = progress,
                            onBought = { buyTick++ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpgradeCard(
    line: UpgradeLine,
    progress: CampaignProgressImpl,
    onBought: () -> Unit
) {
    val upgrades = progress.metaUpgrades
    val rank = upgrades.rankOf(line)
    val decision = progress.purchaseDecision(line)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(168.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(SleekSurfaceCard)
            .border(1.dp, SleekBorderLight, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("upgrade_card_${line.name}")
    ) {
        AutoShrinkText(
            text = stringResource(lineNameRes(line)),
            color = SleekTextAccent,
            maxFontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.shop_rank, rank, line.maxRank),
            color = SleekGold.copy(alpha = 0.9f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        // Bir SONRAKI kademenin toplam etkisi — oyuncu ne alacagini gorur.
        AutoShrinkText(
            text = stringResource(lineEffectRes(line), effectValue(line, (rank + 1).coerceAtMost(line.maxRank))),
            color = SleekTextAccent.copy(alpha = 0.75f),
            maxFontSize = 12.sp,
            maxLines = 3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        // Rank gostergesi: nokta dizisi (renk tek ayrim kanali degil, DOLU/BOS
        // sekil farki da var).
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(line.maxRank) { i ->
                Box(
                    Modifier
                        .size(if (i < rank) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i < rank) SleekGold else SleekBorderLight)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val satinAlinabilir = decision is PurchaseDecision.Allowed
        val etiket = when (decision) {
            is PurchaseDecision.Allowed -> stringResource(R.string.shop_buy, decision.price)
            is PurchaseDecision.MaxRank -> stringResource(R.string.shop_max)
            is PurchaseDecision.InsufficientFunds ->
                stringResource(R.string.shop_short, decision.shortfall)
            is PurchaseDecision.RankGated ->
                stringResource(R.string.shop_gated, decision.requiredClearedLevel)
            else -> stringResource(R.string.shop_reserve_blocked)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (satinAlinabilir) SleekPrimaryGreen else SleekBorderDark)
                .then(
                    if (satinAlinabilir) {
                        Modifier.clickable {
                            if (progress.buyUpgrade(line) is PurchaseDecision.Allowed) onBought()
                        }
                    } else Modifier
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("upgrade_buy_${line.name}")
        ) {
            AutoShrinkText(
                text = etiket,
                color = if (satinAlinabilir) Color.White else SleekTextAccent.copy(alpha = 0.55f),
                maxFontSize = 12.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun lineNameRes(line: UpgradeLine): Int = when (line) {
    UpgradeLine.FIREPOWER -> R.string.shop_line_firepower
    UpgradeLine.OPTICS -> R.string.shop_line_optics
    UpgradeLine.STARTING_SUPPLY -> R.string.shop_line_supply
    UpgradeLine.FORTIFICATION -> R.string.shop_line_fortification
    UpgradeLine.SALVAGE -> R.string.shop_line_salvage
}

private fun lineEffectRes(line: UpgradeLine): Int = when (line) {
    UpgradeLine.FIREPOWER -> R.string.shop_line_firepower_fx
    UpgradeLine.OPTICS -> R.string.shop_line_optics_fx
    UpgradeLine.STARTING_SUPPLY -> R.string.shop_line_supply_fx
    UpgradeLine.FORTIFICATION -> R.string.shop_line_fortification_fx
    UpgradeLine.SALVAGE -> R.string.shop_line_salvage_fx
}

/**
 * Verilen kademedeki TOPLAM etki.
 *
 * Deger `MetaUpgrades`'in KENDI turetilmis alanlarindan okunur — dukkan kendi
 * yukseltme matematigini yazmaz. Ayni hesabi iki yerde tutmak, panelde "+%9"
 * gosterip oyunda +%12 uygulamak gibi sessiz tutarsizliklar uretir (AEHP
 * hatasinin sebebi tam olarak buydu).
 */
private fun effectValue(line: UpgradeLine, rank: Int): Int {
    val m = MetaUpgrades().withRank(line, rank)
    return when (line) {
        UpgradeLine.FIREPOWER -> ((m.damageMultiplier - 1.0) * 100).toInt()
        UpgradeLine.OPTICS -> ((m.rangeMultiplier - 1.0) * 100).toInt()
        UpgradeLine.STARTING_SUPPLY -> m.startingSupply
        UpgradeLine.FORTIFICATION -> m.maxBaseHealth
        UpgradeLine.SALVAGE -> (m.salvageRatio * 100).toInt()
    }
}
