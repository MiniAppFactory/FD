package com.miniappfactory.frontlinedefender.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.billingclient.api.ProductDetails
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.billing.BillingManager
import com.miniappfactory.frontlinedefender.game.billing.FakeBilling
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import com.miniappfactory.frontlinedefender.ui.theme.SleekBorderLight
import com.miniappfactory.frontlinedefender.ui.theme.SleekDarkBg
import com.miniappfactory.frontlinedefender.ui.theme.SleekGold
import com.miniappfactory.frontlinedefender.ui.theme.SleekGoldBorder
import com.miniappfactory.frontlinedefender.ui.theme.SleekPrimaryGreen
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceCard
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceHeader
import com.miniappfactory.frontlinedefender.ui.theme.SleekTextAccent
import java.text.NumberFormat

/**
 * IKMAL DEPOSU — coin paketleri ve "Reklamlari Kaldir" (IAP, 2026-09-02).
 *
 * `UpgradeShopScreen` (Cephanelik) ile AYNI ekran dili: baslik seridi, coin
 * cipi, koyu gradyan. Cephanelik META yukseltme satar (coin HARCAR); burasi
 * coin'in kendisini ve reklamsiz hakki satar (para HARCAR). Ikisi bilincli
 * olarak AYRI ekran: ayni kart sirasina gercek para ve oyun parasi
 * karistirmak, oyuncunun "bu buton bana kac lira?" sorusunu her kartta
 * yeniden sordurur.
 *
 * ## FIYAT METNI HER ZAMAN PLAY'DEN
 * Ekrandaki her fiyat [ProductDetails.oneTimePurchaseOfferDetails]
 * `.formattedPrice`. Play'den cozulemeyen urun (Console'da yok, ag yok, Play
 * Store guncelleniyor) "su an satin alinamiyor" yazar ve butonu kilitlenir —
 * SESSIZCE KAYBOLMAZ. Kaybolan satir oyuncuya "magaza bozuk" dedirtir.
 *
 * ## Bu ekranda reklam yuzeyi YOK
 * Satin alma teklifi gosteren ekran reklam tasimaz (banner zaten kapali).
 *
 * ## Yatay ekran
 * 740x360 dp'de dikey alan 300 dp'nin altinda; icerik `verticalScroll`.
 * Reklamsiz karti ve paketler tek sutun, ust uste. Kartlar `widthIn(max=560)`
 * ile ortalanir; genis tablette uzayip okunmaz hale gelmez.
 */
@Composable
fun StoreScreen(
    progress: CampaignProgressImpl,
    billing: BillingManager,
    onBack: () -> Unit,
    /** Satin alma akisini baslatan taraf — disarida, cunku Activity ister. */
    onPurchase: (productId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val products by billing.products.collectAsStateWithLifecycle()
    val billingReady by billing.ready.collectAsStateWithLifecycle()
    val purchaseInFlight by billing.purchaseInFlight.collectAsStateWithLifecycle()
    val restoreState by billing.restoreState.collectAsStateWithLifecycle()

    // Debug sahte satin almadan sonra bakiye/bayrak yenilensin diye.
    var tick by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SleekSurfaceHeader, SleekDarkBg)))
            .testTag("store_screen")
    ) {
        Column(Modifier.fillMaxSize()) {

            // ---- baslik seridi (Cephanelik ile ayni) ----
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
                        .testTag("store_back")
                )
                Spacer(Modifier.width(20.dp))
                Text(
                    text = stringResource(R.string.store_title),
                    color = SleekTextAccent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.weight(1f))
                @Suppress("UNUSED_EXPRESSION") tick
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33FFD700))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("store_coin_chip")
                ) {
                    SpriteIcon(id = R.drawable.spr_ic_coins, size = 18.dp, contentDescription = null)
                    Text(
                        text = stringResource(R.string.level_coin_amount, progress.coins),
                        color = SleekGold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // --- REKLAMSIZ ---
                    SectionLabel(stringResource(R.string.store_section_remove_ads))
                    RemoveAdsCard(
                        owned = progress.adsRemoved,
                        priceText = products[StoreCatalog.PRODUCT_REMOVE_ADS]?.oneTimePrice(),
                        enabled = !purchaseInFlight,
                        onBuy = { onPurchase(StoreCatalog.PRODUCT_REMOVE_ADS) },
                    )

                    Spacer(Modifier.height(6.dp))

                    // --- COIN PAKETLERI (ucuzdan pahaliya) ---
                    SectionLabel(stringResource(R.string.store_section_coins))
                    Text(
                        text = stringResource(R.string.store_coins_hint),
                        color = SleekTextAccent.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                    StoreCatalog.coinPacks.forEach { pack ->
                        CoinPackRow(
                            coins = pack.coins,
                            priceText = products[pack.id]?.oneTimePrice(),
                            best = pack.coins == StoreCatalog.largestPackCoins,
                            enabled = !purchaseInFlight,
                            onBuy = { onPurchase(pack.id) },
                            testTag = "store_pack_${pack.coins}",
                        )
                    }

                    if (!billingReady) {
                        Text(
                            text = stringResource(R.string.store_connecting),
                            color = SleekTextAccent.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().testTag("store_connecting"),
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // --- GERI YUKLE ---
                    RestoreRow(state = restoreState) { billing.restorePurchases(manuel = true) }

                    // --- DEBUG PANELI — yalniz debug derlemede ---
                    if (FakeBilling.enabled) {
                        Spacer(Modifier.height(10.dp))
                        DebugPanel(progress = progress, onChanged = { tick++ })
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Tek seferlik urunun bicimli fiyati. `oneTimePurchaseOfferDetails` null
 * olabilir (urun Play'de abonelik tanimlanmis ya da detay eksik gelmis);
 * cagiran null'i "su an satin alinamiyor" olarak gosterir, fiyat UYDURMAZ.
 */
private fun ProductDetails.oneTimePrice(): String? =
    oneTimePurchaseOfferDetails?.formattedPrice

/** Binlik ayracli coin metni ("25.000"). Dil ayari ne olursa olsun okunur. */
internal fun formatCoins(coins: Int): String = NumberFormat.getIntegerInstance().format(coins)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = SleekTextAccent.copy(alpha = 0.75f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun StoreCard(
    modifier: Modifier = Modifier,
    borderColor: Color = SleekBorderLight,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SleekSurfaceCard)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) { content() }
}

@Composable
private fun PriceButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String,
) {
    Text(
        text = text,
        color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) SleekPrimaryGreen else SleekPrimaryGreen.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            // 44 dp dokunma tabani (ArtButtonTouchTargetTest ile ayni kural).
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag(testTag),
    )
}

@Composable
private fun UnavailableNote() {
    Text(
        text = stringResource(R.string.store_unavailable),
        color = SleekTextAccent.copy(alpha = 0.55f),
        fontSize = 11.sp,
        textAlign = TextAlign.End,
        modifier = Modifier.testTag("store_unavailable"),
    )
}

@Composable
private fun RemoveAdsCard(
    owned: Boolean,
    priceText: String?,
    enabled: Boolean,
    onBuy: () -> Unit,
) {
    StoreCard(borderColor = SleekGoldBorder, modifier = Modifier.testTag("store_remove_ads")) {
        Column {
            Text(
                text = stringResource(R.string.store_remove_ads_title),
                color = SleekGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.store_remove_ads_body),
                color = SleekTextAccent,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(3.dp))
            // ZORUNLU CUMLE: rewarded kalkmaz, oyuncu bunu satin almadan ONCE
            // bilmeli — sonradan ogrenmesi "bozuk urun" olarak okunur.
            Text(
                text = stringResource(R.string.store_remove_ads_note),
                color = SleekTextAccent.copy(alpha = 0.65f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))
            when {
                owned -> Text(
                    text = stringResource(R.string.store_owned),
                    color = Color(0xFF9CD67D),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag("store_remove_ads_owned"),
                )

                priceText == null -> UnavailableNote()

                else -> PriceButton(
                    text = priceText,
                    enabled = enabled,
                    onClick = onBuy,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "store_remove_ads_buy",
                )
            }
        }
    }
}

@Composable
private fun CoinPackRow(
    coins: Int,
    priceText: String?,
    best: Boolean,
    enabled: Boolean,
    onBuy: () -> Unit,
    testTag: String,
) {
    StoreCard(
        borderColor = if (best) SleekGold.copy(alpha = 0.6f) else SleekBorderLight,
        modifier = Modifier.testTag(testTag),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpriteIcon(id = R.drawable.spr_ic_coins, size = 28.dp, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.store_pack_coins, formatCoins(coins)),
                    color = Color(0xFFFFF3C4),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                if (best) {
                    Text(
                        text = stringResource(R.string.store_best_value),
                        color = SleekGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (priceText == null) UnavailableNote()
            else PriceButton(text = priceText, enabled = enabled, onClick = onBuy, testTag = "${testTag}_buy")
        }
    }
}

/**
 * "SATIN ALMALARI GERI YUKLE". Play bunu ZORUNLU tutmaz (hak hesaba bagli,
 * `queryPurchasesAsync` ile kendi gelir); dugme su gercek ariza icin var:
 * cevrimdisi acilan oturumda `remove_ads` yazilamaz ve oyuncunun elinde
 * hicbir kontrol kalmaz — "odedim, hala reklam goruyorum". `refresh()` her
 * on plana gelmede dener; bu dugme oyuncunun ACIKCA "simdi dene" demesi.
 */
@Composable
private fun RestoreRow(state: BillingManager.RestoreState, onRestore: () -> Unit) {
    val running = state == BillingManager.RestoreState.RUNNING
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(if (running) R.string.store_restore_running else R.string.store_restore),
            color = SleekTextAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, SleekBorderLight, RoundedCornerShape(10.dp))
                .clickable(enabled = !running, onClick = onRestore)
                .padding(vertical = 12.dp)
                .testTag("store_restore"),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                when (state) {
                    BillingManager.RestoreState.DONE -> R.string.store_restore_done
                    BillingManager.RestoreState.FAILED -> R.string.store_restore_failed
                    else -> R.string.store_restore_hint
                }
            ),
            color = SleekTextAccent.copy(alpha = 0.6f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * DEBUG PANELI — release'te HIC cizilmez ([FakeBilling.enabled]).
 * Uc soruyu Play'e dokunmadan cevaplar: coin yaziliyor mu; ayni jeton IKI kez
 * odeniyor mu (bakiye DEGISMEMELI); reklamsiz bayragi interstitial kapisini
 * kapatiyor mu. Etiketler "hak ver/geri al" DEMEZ — bu hak yonetimi degil,
 * test durumu sifirlamasidir (Kron-Drive 2026-09-01 dersi).
 */
@Composable
private fun DebugPanel(progress: CampaignProgressImpl, onChanged: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A2230))
            .border(1.dp, Color(0xFF4A78B0), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("store_debug_panel")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.store_debug_title),
                color = Color(0xFF8FB8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                text = stringResource(R.string.store_debug_body),
                color = SleekTextAccent.copy(alpha = 0.6f),
                fontSize = 10.sp,
            )
            DebugButton(
                text = stringResource(R.string.store_debug_fake_pack, formatCoins(StoreCatalog.smallestPackCoins)),
                testTag = "store_debug_fake_pack",
            ) {
                val token = FakeBilling.newToken(StoreCatalog.PACK_RECRUIT)
                FakeBilling.rememberToken(token)
                progress.grantPurchasedCoins(token, FakeBilling.coinsOf(StoreCatalog.PACK_RECRUIT))
                onChanged()
            }
            DebugButton(
                text = stringResource(R.string.store_debug_same_token),
                testTag = "store_debug_same_token",
            ) {
                val token = FakeBilling.lastToken ?: FakeBilling.newToken(StoreCatalog.PACK_RECRUIT)
                FakeBilling.rememberToken(token)
                progress.grantPurchasedCoins(token, FakeBilling.coinsOf(StoreCatalog.PACK_RECRUIT))
                onChanged()
            }
            DebugButton(
                text = stringResource(if (progress.adsRemoved) R.string.store_debug_ads_off else R.string.store_debug_ads_on),
                testTag = "store_debug_toggle_ads",
            ) {
                progress.setAdsRemoved(!progress.adsRemoved)
                onChanged()
            }
            Text(
                text = stringResource(
                    R.string.store_debug_status,
                    formatCoins(progress.coins),
                    if (progress.adsRemoved) "ACIK" else "kapali",
                    formatCoins(progress.lifetimePurchasedCoins),
                ),
                color = SleekTextAccent,
                fontSize = 11.sp,
                modifier = Modifier.testTag("store_debug_status"),
            )
        }
    }
}

@Composable
private fun DebugButton(text: String, testTag: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = SleekTextAccent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF4A78B0), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp)
            .testTag(testTag),
    )
}
