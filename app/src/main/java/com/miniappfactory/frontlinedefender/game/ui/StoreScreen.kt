package com.miniappfactory.frontlinedefender.game.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import com.miniappfactory.frontlinedefender.game.billing.ProductsState
import com.miniappfactory.frontlinedefender.game.billing.StoreConnection
import com.miniappfactory.frontlinedefender.game.billing.StoreEvent
import com.miniappfactory.frontlinedefender.game.billing.messageRes
import com.miniappfactory.frontlinedefender.game.billing.nameRes
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import com.miniappfactory.frontlinedefender.ui.theme.SleekBorderLight
import com.miniappfactory.frontlinedefender.ui.theme.SleekDarkBg
import com.miniappfactory.frontlinedefender.ui.theme.SleekGold
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceHeader
import com.miniappfactory.frontlinedefender.ui.theme.SleekTextAccent
import java.text.NumberFormat

/**
 * IKMAL DEPOSU — bes coin paketi (Play Faturalandirma, 2026-09-30 yeniden yazim).
 *
 * `UpgradeShopScreen` (Cephanelik) ile AYNI ekran dili ve AYNI kart cercevesi
 * ([ShopCardFrame]): yatay kaydirilan kart siralari. Cephanelik coin HARCAR; burasi
 * coin'in kendisini satar (para HARCAR). Ikisi bilincli olarak AYRI ekran.
 *
 * ## FIYAT METNI HER ZAMAN PLAY'DEN
 * Karttaki her fiyat [ProductDetails.oneTimePurchaseOfferDetails] `.formattedPrice`. Play'den
 * cozulemeyen urun "satin alinamiyor" yazar ve karti KILITLENIR — sessizce kaybolmaz, ve bu dosyada
 * ya da string kaynaklarinda hicbir fiyat/yuzde metni YOKTUR (`StoreStringsTest`).
 *
 * ## Bu ekranda reklam yuzeyi YOK
 * Oyundan tum reklamlar kaldirildi; "Reklamlari Kaldir" urunu satistan kalkti. Eski hak sahipleri
 * icin `remove_ads` yalnizca sessizce onaylanir ([StoreCatalog.PRODUCT_REMOVE_ADS]).
 *
 * ## Yatay ekran (740x360 dp)
 * Baslik ~52 dp + kart 224 dp + alt serit ~44 dp sigar; beş 168 dp'lik kart yatay kaydirilir
 * (Cephanelik gibi). Tum icerik ayrica `verticalScroll` icinde: kucuk ekranda kesilmez.
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
    val connection by billing.connection.collectAsStateWithLifecycle()
    val productsState by billing.productsState.collectAsStateWithLifecycle()
    val purchaseInFlight by billing.purchaseInFlight.collectAsStateWithLifecycle()
    val pendingCount by billing.pendingCount.collectAsStateWithLifecycle()
    val recoverState by billing.restoreState.collectAsStateWithLifecycle()

    // Ekran acikken gelen tek seferlik geri bildirim (odeme geldi / iptal / hata sebebi).
    var lastEvent by remember { mutableStateOf<StoreEvent?>(null) }
    LaunchedEffect(billing) { billing.events.collect { lastEvent = it } }

    // Magaza acilinca eksik urun varsa yeniden sor (baglanti kopmus ya da sorgu bos donmus olabilir).
    LaunchedEffect(billing) { billing.ensureProducts() }

    // Debug sahte satin almadan sonra bakiye yenilensin diye.
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
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable(onClick = onBack)
                        .testTag("store_back")
                ) {
                    Text(
                        text = stringResource(R.string.level_back),
                        color = SleekTextAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
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
                        .heightIn(min = 44.dp)
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
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                // --- PAKET KARTLARI (ucuzdan pahaliya; yatay kaydirilir) ---
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .testTag("store_pack_row")
                ) {
                    StoreCatalog.coinPacks.forEachIndexed { index, pack ->
                        val price = products[pack.id]?.oneTimePrice()
                        val available = price != null && !purchaseInFlight
                        ShopCardFrame(
                            cardTag = "store_pack_${pack.coins}",
                            number = null,
                            title = stringResource(pack.tier.nameRes()),
                            effect = stringResource(R.string.store_pack_coins, formatCoins(pack.coins)),
                            shield = stringResource(
                                R.string.store_pack_ordinal,
                                index + 1,
                                StoreCatalog.coinPacks.size,
                            ),
                            dotCount = StoreCatalog.coinPacks.size,
                            dotsFilled = index + 1,
                            buttonLabel = price ?: stringResource(
                                if (productsState == ProductsState.LOADING) R.string.store_price_loading
                                else R.string.store_price_unavailable
                            ),
                            buttonActive = available,
                            buttonTag = "store_pack_${pack.coins}_buy",
                            onClick = if (available) ({ onPurchase(pack.id) }) else null,
                            window = { PackWindow(pack.id) }
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // --- DURUM SERIDI + KURTARMA ---
                StatusStrip(
                    lastEvent = lastEvent,
                    pending = pendingCount > 0,
                    productsFailed = productsState == ProductsState.FAILED ||
                        connection == StoreConnection.UNAVAILABLE,
                    productsLoading = productsState == ProductsState.LOADING,
                    recoverState = recoverState,
                    onRetry = { billing.refresh() },
                    onRecover = { billing.restorePurchases(manuel = true) },
                )

                // --- DEBUG PANELI — yalniz debug derlemede ---
                if (FakeBilling.enabled) {
                    Spacer(Modifier.height(10.dp))
                    DebugPanel(
                        progress = progress,
                        billing = billing,
                        onChanged = { tick++ },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Paket urun kimligine gore kart penceresi gorseli; bilinmeyen kimlik `null` (madeni para yedegi). */
@DrawableRes
internal fun packArtRes(productId: String): Int? = when (productId) {
    StoreCatalog.PACK_RECRUIT -> R.drawable.spr_pack_fd_coins_1000
    StoreCatalog.PACK_SERGEANT -> R.drawable.spr_pack_fd_coins_2200
    StoreCatalog.PACK_CAPTAIN -> R.drawable.spr_pack_fd_coins_3600
    StoreCatalog.PACK_COLONEL -> R.drawable.spr_pack_fd_coins_5200
    StoreCatalog.PACK_GENERAL -> R.drawable.spr_pack_fd_coins_7000
    else -> null
}

@Composable
private fun BoxScope.PackWindow(productId: String) {
    Image(
        painter = painterResource(packArtRes(productId) ?: R.drawable.spr_ic_coins),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Tek seferlik urunun bicimli fiyati. `oneTimePurchaseOfferDetails` null olabilir; cagiran null'i
 * "satin alinamiyor" olarak gosterir, fiyat UYDURMAZ.
 */
private fun ProductDetails.oneTimePrice(): String? =
    oneTimePurchaseOfferDetails?.formattedPrice

/** Binlik ayracli coin metni ("7.000"). Dil ayari ne olursa olsun okunur. */
internal fun formatCoins(coins: Int): String = NumberFormat.getIntegerInstance().format(coins)

/**
 * Kartlarin altindaki tek satirlik serit: en onemli mesaj + gerekli tek dugme.
 * Oncelik: son olay > bekleyen odeme > urunler yuklenemedi > yukleniyor > bos durumda kurtarma ipucu.
 * "Tekrar dene" YALNIZ yukleme basarisizken; "bitmemis satin almalari kontrol et" her zaman.
 */
@Composable
private fun StatusStrip(
    lastEvent: StoreEvent?,
    pending: Boolean,
    productsFailed: Boolean,
    productsLoading: Boolean,
    recoverState: BillingManager.RestoreState,
    onRetry: () -> Unit,
    onRecover: () -> Unit,
) {
    val running = recoverState == BillingManager.RestoreState.RUNNING
    val message: String = when {
        lastEvent is StoreEvent.Granted ->
            stringResource(R.string.store_event_granted, formatCoins(lastEvent.coins))

        lastEvent != null -> stringResource(lastEvent.messageRes())
        pending -> stringResource(R.string.store_pending_note)
        productsFailed -> stringResource(R.string.store_products_failed)
        productsLoading -> stringResource(R.string.store_products_loading)
        recoverState == BillingManager.RestoreState.DONE -> stringResource(R.string.store_recover_done)
        recoverState == BillingManager.RestoreState.FAILED -> stringResource(R.string.store_recover_failed)
        else -> stringResource(R.string.store_recover_hint)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = message,
            color = SleekTextAccent.copy(alpha = 0.75f),
            fontSize = 10.sp,
            maxLines = 3,
            modifier = Modifier
                .weight(1f)
                .testTag("store_status")
        )
        if (productsFailed) {
            StripButton(
                text = stringResource(R.string.store_products_retry),
                enabled = true,
                onClick = onRetry,
                testTag = "store_retry",
            )
        }
        StripButton(
            text = stringResource(if (running) R.string.store_recover_running else R.string.store_recover_button),
            enabled = !running,
            onClick = onRecover,
            testTag = "store_recover",
        )
    }
}

@Composable
private fun StripButton(text: String, enabled: Boolean, onClick: () -> Unit, testTag: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, SleekBorderLight, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            color = if (enabled) SleekTextAccent else SleekTextAccent.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * DEBUG PANELI — release'te HIC cizilmez ([FakeBilling.enabled]).
 * Senaryolar GERCEK isleme hattindan ([BillingManager.debugRun]) gecer; yalniz Play'e giden
 * tuketme/onay sahtedir. Etiketler "hak ver/geri al" DEMEZ: bu test durumu sifirlamasidir
 * (Kron-Drive 2026-09-01 dersi). Sahte satin alma, Play'de gercek bir kayit olusturmaz.
 */
@Composable
private fun DebugPanel(
    progress: CampaignProgressImpl,
    billing: BillingManager,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
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
            debugScenarios().forEach { (scenario, labelRes) ->
                DebugButton(
                    text = stringResource(labelRes),
                    testTag = "store_debug_${scenario.name.lowercase()}",
                ) {
                    billing.debugRun(scenario)
                    onChanged()
                }
            }
            Text(
                text = stringResource(
                    R.string.store_debug_status,
                    formatCoins(progress.coins),
                    formatCoins(progress.lifetimePurchasedCoins),
                ),
                color = SleekTextAccent,
                fontSize = 11.sp,
                modifier = Modifier.testTag("store_debug_status"),
            )
        }
    }
}

private fun debugScenarios(): List<Pair<FakeBilling.Scenario, Int>> = listOf(
    FakeBilling.Scenario.BUY to R.string.store_debug_sc_buy,
    FakeBilling.Scenario.PENDING to R.string.store_debug_sc_pending,
    FakeBilling.Scenario.COMPLETE_PENDING to R.string.store_debug_sc_complete_pending,
    FakeBilling.Scenario.CANCEL to R.string.store_debug_sc_cancel,
    FakeBilling.Scenario.ARM_PERSIST_FAILURE to R.string.store_debug_sc_persist_failed,
    FakeBilling.Scenario.LEGACY_COINS_1200 to R.string.store_debug_sc_legacy,
    FakeBilling.Scenario.REPLAY_LAST to R.string.store_debug_sc_replay,
    FakeBilling.Scenario.QUANTITY_2 to R.string.store_debug_sc_quantity2,
)

@Composable
private fun DebugButton(text: String, testTag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF4A78B0), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            color = SleekTextAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
