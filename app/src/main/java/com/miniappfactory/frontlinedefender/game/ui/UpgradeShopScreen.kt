package com.miniappfactory.frontlinedefender.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
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
import com.miniappfactory.frontlinedefender.game.economy.PrestigeDecision
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
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
                // PRESTIJ NISANI — 6. kart (ECONOMY_ANALYSIS B). `UpgradeLine`
                // enum'una EKLENMEDI: o enum `MetaUpgrades` matematigine ve
                // rank kapilarina bagli; kozmetik bir kalemi oraya sokmak
                // ekonomi kapilarini prestije de uygulardi. Ayri kart, ayni
                // sablon dili.
                key(buyTick) {
                    PrestigeCard(progress = progress, onBought = { buyTick++ })
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

    // =========================================================================
    // BOLUM KARTLARIYLA AYNI DIL (kullanici istegi 2026-08-26: "cephanelikteki
    // kartlar ... bolum kartlari gibi tasarla").
    //
    // Sablon `ui_card_shop`: available sablonunun YILDIZSIZ turevi (uretici
    // tools/ui_art_pipeline.py — yildiz bandi pencere dokusuyla yamali).
    // Bolum kartindaki bolgeler burada su anlamlari tasir:
    //   pencere       -> yukseltme hattinin IKONU (savastaki sprite'in kendisi)
    //   numara dairesi -> mevcut KADEME
    //   baslik bandi  -> hat adi
    //   crosshair sat. -> bir SONRAKI kademenin etkisi
    //   kalkan satiri -> KADEME n/m
    //   yama bolgesi  -> kademe NOKTALARI (dolu/bos — renk tek kanal degil)
    //   buton bandi   -> fiyat / MAKS / kisit etiketi
    // =========================================================================
    val satinAlinabilir = decision is PurchaseDecision.Allowed

    BoxWithConstraints(
        modifier = Modifier
            .width(168.dp)
            .aspectRatio(560f / 747f)
            .then(
                if (satinAlinabilir) {
                    Modifier.clickable {
                        if (progress.buyUpgrade(line) is PurchaseDecision.Allowed) onBought()
                    }
                } else Modifier
            )
            .testTag("upgrade_card_${line.name}")
    ) {
        val w = maxWidth
        val h = maxHeight

        // IKON — pencerede, koyu zeminde. Kupurun aksine kenardan kenara
        // degil ORTALI: sprite'lar dikdortgen doldurmaz, Fit ile ortalanir.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(
                    start = w * 0.110f,
                    top = h * 0.085f,
                    end = w * 0.116f,
                    bottom = h * 0.615f
                )
                .fillMaxSize()
                .background(Color(0xFF141A0E))
        ) {
            Image(
                painter = painterResource(lineIconRes(line)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.72f)
            )
        }

        Image(
            painter = painterResource(R.drawable.ui_card_shop),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize()
        )

        // KADEME — numara dairesinde.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = w * 0.115f, top = h * 0.077f)
                .size(w * 0.165f)
        ) {
            AutoShrinkText(
                text = stringResource(R.string.level_number, rank),
                color = Color(0xFFEAF2DC),
                maxFontSize = 13.sp,
                minFontSize = 9.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // HAT ADI — baslik bandinda.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(
                    start = w * 0.14f,
                    end = w * 0.14f,
                    top = h * 0.408f,
                    bottom = h * 0.492f
                )
                .fillMaxSize()
        ) {
            AutoShrinkText(
                text = stringResource(lineNameRes(line)),
                color = Color(0xFFE8F0DC),
                fontWeight = FontWeight.Black,
                maxFontSize = 11.5.sp,
                minFontSize = 7.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ETKI — crosshair satirinda: bir SONRAKI kademenin toplam etkisi.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .padding(
                    start = w * 0.285f,
                    end = w * 0.13f,
                    // Etki cumleleri bolum hedeflerinden UZUN ("Tum
                    // kulelerin atis mesafesi +%5") — tek satir kesiyordu
                    // (cihazda goruldu). Iki satira izin verilir; band
                    // kalkan satirina (0,605) kadar buyutuldu.
                    top = h * 0.478f,
                    bottom = h * 0.392f
                )
                .fillMaxSize()
        ) {
            AutoShrinkText(
                text = stringResource(
                    lineEffectRes(line),
                    effectValue(line, (rank + 1).coerceAtMost(line.maxRank))
                ),
                color = Color(0xFFAAB894),
                maxFontSize = 9.sp,
                minFontSize = 7.sp,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // KADEME n/m — kalkan satirinda.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .padding(
                    start = w * 0.285f,
                    end = w * 0.13f,
                    top = h * 0.605f,
                    bottom = h * 0.315f
                )
                .fillMaxSize()
        ) {
            Text(
                text = stringResource(R.string.shop_rank, rank, line.maxRank),
                color = Color(0xFFD8C46A),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        // KADEME NOKTALARI — eski kartin gostergesi, yamali bolgede.
        // Dolu/bos ayrimi boyutla da verilir (renk tek kanal degil).
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = h * 0.695f)
                .align(Alignment.TopCenter)
        ) {
            repeat(line.maxRank) { i ->
                Box(
                    Modifier
                        .size(if (i < rank) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i < rank) SleekGold else Color(0xFF49533E))
                )
            }
        }

        // FIYAT / DURUM — buton bandinda (bolum kartiyla ayni band).
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
                .padding(
                    start = w * 0.16f,
                    end = w * 0.16f,
                    top = h * 0.806f,
                    bottom = h * 0.092f
                )
                .fillMaxSize()
                .testTag("upgrade_buy_${line.name}")
        ) {
            AutoShrinkText(
                text = etiket,
                color = if (satinAlinabilir) Color(0xFFDCE8CC) else Color(0x99C5D6B4),
                maxFontSize = 10.sp,
                minFontSize = 7.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Hattin pencerede gosterilen ikonu — OYUNDAN TANIDIK sprite'lar, yeni cizim
 * degil: oyuncu "Ates Gucu"nun topun hasari oldugunu savastaki ayni gorselden
 * okur.
 */
private fun lineIconRes(line: UpgradeLine): Int = when (line) {
    UpgradeLine.FIREPOWER -> R.drawable.spr_tower_heavy_cannon
    UpgradeLine.OPTICS -> R.drawable.spr_ic_target
    UpgradeLine.STARTING_SUPPLY -> R.drawable.spr_ic_supply_crate
    UpgradeLine.FORTIFICATION -> R.drawable.spr_ic_base_health
    UpgradeLine.SALVAGE -> R.drawable.spr_ic_sell
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

/**
 * Prestij Nisani karti — tamamen KOZMETIK derin emici (toplam 19.900).
 *
 * [UpgradeCard] ile ayni sablon yerlesimi; kopya olmasinin sebebi
 * `UpgradeLine`a bagimli olmamasi (bkz. cagri yerindeki not).
 */
@Composable
private fun PrestigeCard(
    progress: CampaignProgressImpl,
    onBought: () -> Unit
) {
    val rank = progress.prestigeRank
    val decision = progress.prestigeDecision()
    val satinAlinabilir = decision is PrestigeDecision.Allowed

    BoxWithConstraints(
        modifier = Modifier
            .width(168.dp)
            .aspectRatio(560f / 747f)
            .then(
                if (satinAlinabilir) {
                    Modifier.clickable { if (progress.buyPrestige()) onBought() }
                } else Modifier
            )
            .testTag("prestige_card")
    ) {
        val w = maxWidth
        val h = maxHeight

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(
                    start = w * 0.110f,
                    top = h * 0.085f,
                    end = w * 0.116f,
                    bottom = h * 0.615f
                )
                .fillMaxSize()
                .background(Color(0xFF141A0E))
        ) {
            Image(
                painter = painterResource(R.drawable.spr_ic_victory_star),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.62f)
            )
        }

        Image(
            painter = painterResource(R.drawable.ui_card_shop),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize()
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = w * 0.115f, top = h * 0.077f)
                .size(w * 0.165f)
        ) {
            AutoShrinkText(
                text = stringResource(R.string.level_number, rank),
                color = Color(0xFFEAF2DC),
                maxFontSize = 13.sp,
                minFontSize = 9.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(
                    start = w * 0.14f,
                    end = w * 0.14f,
                    top = h * 0.408f,
                    bottom = h * 0.492f
                )
                .fillMaxSize()
        ) {
            AutoShrinkText(
                text = stringResource(R.string.prestige_title),
                color = Color(0xFFE8F0DC),
                fontWeight = FontWeight.Black,
                maxFontSize = 11.5.sp,
                minFontSize = 7.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .padding(
                    start = w * 0.285f,
                    end = w * 0.13f,
                    top = h * 0.478f,
                    bottom = h * 0.392f
                )
                .fillMaxSize()
        ) {
            AutoShrinkText(
                text = if (rank >= EconomyConfig.PRESTIGE_MAX) {
                    stringResource(R.string.prestige_effect_max)
                } else {
                    stringResource(
                        R.string.prestige_effect_next,
                        stringResource(prestigeNameRes(rank + 1))
                    )
                },
                color = Color(0xFFAAB894),
                maxFontSize = 9.sp,
                minFontSize = 7.sp,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .padding(
                    start = w * 0.285f,
                    end = w * 0.13f,
                    top = h * 0.605f,
                    bottom = h * 0.315f
                )
                .fillMaxSize()
        ) {
            Text(
                text = stringResource(
                    R.string.prestige_rank_label, rank, EconomyConfig.PRESTIGE_MAX
                ),
                color = Color(0xFFD8C46A),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = h * 0.695f)
                .align(Alignment.TopCenter)
        ) {
            repeat(EconomyConfig.PRESTIGE_MAX) { i ->
                Box(
                    Modifier
                        .size(if (i < rank) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i < rank) SleekGold else Color(0xFF49533E))
                )
            }
        }

        val etiket = when (decision) {
            is PrestigeDecision.Allowed -> stringResource(R.string.shop_buy, decision.price)
            is PrestigeDecision.MaxRank -> stringResource(R.string.shop_max)
            is PrestigeDecision.InsufficientFunds ->
                stringResource(R.string.shop_short, decision.shortfall)
            is PrestigeDecision.ReserveLocked ->
                stringResource(R.string.shop_reserve_blocked)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(
                    start = w * 0.16f,
                    end = w * 0.16f,
                    top = h * 0.806f,
                    bottom = h * 0.092f
                )
                .fillMaxSize()
                .testTag("prestige_buy")
        ) {
            AutoShrinkText(
                text = etiket,
                color = if (satinAlinabilir) Color(0xFFDCE8CC) else Color(0x99C5D6B4),
                maxFontSize = 10.sp,
                minFontSize = 7.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Nisan adi — kademe 1..5. */
fun prestigeNameRes(rank: Int): Int = when (rank.coerceIn(1, 5)) {
    1 -> R.string.prestige_name_1
    2 -> R.string.prestige_name_2
    3 -> R.string.prestige_name_3
    4 -> R.string.prestige_name_4
    else -> R.string.prestige_name_5
}

