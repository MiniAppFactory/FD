package com.miniappfactory.frontlinedefender.game.ads

import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import android.content.Context
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.ui.FrameTone
import com.miniappfactory.frontlinedefender.game.ui.TacticalFrame
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Faz 5 — REWARDED TEKLIF YUZEYLERI.
 *
 * Neden burada ve neden ayri: `GameDialogs.kt` (VictoryModal / DefeatModal) ve
 * `LevelSelectScreen.kt` baska ajanlarin dosyalari. Teklif yuzeyleri bu yuzden
 * reklam paketinde durur ve `GameScreen.kt` tarafindan **o modallarin yerine
 * degil, oncesinde/yaninda** gosterilir. Bir modalin uzerine bindirmek
 * landscape'te (S8'de ~300dp yukseklik) kacinilmaz olarak butonlari orter —
 * kazara tiklamanin ders kitabi ornegi.
 *
 * ## Uc kural (GDD §G.4)
 * 1. Buton **her zaman** tiklanabilir; reklam hazir olmadigi icin pasiflesmez.
 * 2. "Vazgec" her fazda erisilebilir; teklif hicbir zaman bir kapi degildir.
 * 3. Sonuc ne olursa olsun tek akis: yalnizca **odul buyuklugu** degisir.
 */

private enum class OfferPhase { OFFER, WAITING, RESULT }

/**
 * Tam ekran (scrim + kart) rewarded teklifi.
 *
 * @param onDismiss teklif kapandi — cagiran taraf akisi surdurur. **Her yolda
 *   tam bir kez** cagrilir: izle-odul, izle-nofill, vazgec, hata.
 * @param applyResult odulu uygular ve oyuncuya gosterilecek NOTR mesaji doner.
 *   Reklam katmani ekonomiyi tanimadigi icin uygulama isi cagiran tarafta
 *   (bkz. [applySupplyDrop], [applyDoublePayout]).
 */
@Composable
fun RewardedOfferSheet(
    adHost: AdHost,
    placement: RewardedPlacement,
    title: String,
    body: String,
    onDismiss: () -> Unit,
    applyResult: (RewardedResult) -> String,
    watchLabel: String = stringResource(R.string.ad_sheet_watch),
    skipLabel: String = stringResource(R.string.ad_sheet_skip),
    remainingLabel: String? = null
) {
    val activity = LocalContext.current.findActivity()
    var phase by remember(placement) { mutableStateOf(OfferPhase.OFFER) }
    var message by remember(placement) { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            // Scrim tiklamayi YUTAR: altta kalan savas alani/kartlar dokunus almaz.
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF21A2213))
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("rewarded_offer_sheet")
        ) {
            Text(
                text = title,
                color = Color(0xFFFFD54F),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            when (phase) {
                OfferPhase.OFFER -> {
                    Text(
                        text = body,
                        color = Color(0xFFDCE8CC),
                        fontSize = 12.sp,
                        maxLines = 4,
                        textAlign = TextAlign.Center
                    )
                    if (remainingLabel != null) {
                        Text(
                            text = remainingLabel,
                            color = Color(0x99C5D6B4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OfferButton(
                            label = skipLabel,
                            background = Color(0xFF2A3320),
                            textColor = Color(0xFFC5D6B4),
                            modifier = Modifier.weight(1f).testTag("offer_skip"),
                            onClick = onDismiss
                        )
                        OfferButton(
                            label = watchLabel,
                            background = Color(0xFF4C7A2E),
                            textColor = Color(0xFFF2FFE4),
                            modifier = Modifier.weight(1f).testTag("offer_watch"),
                            onClick = {
                                phase = OfferPhase.WAITING
                                if (activity == null) {
                                    // Activity yok (teorik): odul akisi yine
                                    // surer, crash veya kilit YOK.
                                    message = applyResult(
                                        RewardedResult(
                                            placement,
                                            RewardedOutcome.REDUCED_REWARD,
                                            AdFallbackReason.SHOW_FAILED
                                        )
                                    )
                                    phase = OfferPhase.RESULT
                                } else {
                                    adHost.showRewarded(activity, placement) { result ->
                                        // ALREADY_IN_FLIGHT sessizce yok sayilir:
                                        // birinci cagri zaten sonuca ulasacak.
                                        if (result.reason == AdFallbackReason.ALREADY_IN_FLIGHT) {
                                            return@showRewarded
                                        }
                                        message = applyResult(result)
                                        phase = OfferPhase.RESULT
                                    }
                                }
                            }
                        )
                    }
                }

                OfferPhase.WAITING -> {
                    // Suresiz spinner YOK: RewardedAdManager 5 sn'de kendi
                    // reklamsiz dalina duser (AdPolicyConfig.LOAD_TIMEOUT_MS).
                    Text(
                        text = stringResource(R.string.ad_contacting_hq),
                        color = Color(0xFFDCE8CC),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                }

                OfferPhase.RESULT -> {
                    Text(
                        text = message,
                        color = Color(0xFFDCE8CC),
                        fontSize = 12.sp,
                        maxLines = 4,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("offer_result_message")
                    )
                    OfferButton(
                        // CIHAZDA GORULDU (Faz 17): etiket koda gomuluydu ve
                        // oyunun geri kalani Turkceyken Ingilizce kaliyordu.
                        label = stringResource(R.string.ad_sheet_continue),
                        background = Color(0xFF4C7A2E),
                        textColor = Color(0xFFF2FFE4),
                        modifier = Modifier.fillMaxWidth().testTag("offer_continue"),
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferButton(
    label: String,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // Min dokunma alani 48dp (GDD §H.8) — sabit yukseklik degil.
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * R1 TEDARIK TALEBI seridi — bolum secim ekraninin altinda, banner'in USTUNDE.
 *
 * Yuksekligi teklif tukendiginde de KORUNUR: satir yok olsa bolum kartlari
 * 44dp asagi kayardi ve gunde bir kez de olsa parmagin altinda duzen kaymasi
 * olurdu.
 */
@Composable
fun SupplyDropBar(
    offered: Boolean,
    remaining: Int,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp)
    ) {
        if (offered) {
            // HEDEF TASARIM: duz yesil M3 butonu degil, kampanya kartlariyla
            // ayni dili konusan SANDIK BANDI — kosesi kesilmis taktik cerceve
            // (TacticalFrame, cizim) + sandigin kapak cizgisi hissini veren
            // altin vurgulu metin. Davranis, testTag ve 44 dp yukseklik
            // sozlesmesi AYNEN korundu.
            TacticalFrame(
                tone = FrameTone.CLEARED,
                chamfer = 7.dp,
                showTicks = false,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clickable(onClick = onRequest)
                    .testTag("supply_drop_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // Sandik ikonu — hedef tasarimin tedarik bandindaki sandik.
                    // `spr_ic_supply_crate` zaten pakette vardi ve BU band
                    // icin cizilmisti; simdiye kadar yalnizca teklif sayfasinda
                    // gorunuyordu.
                    Image(
                        painter = painterResource(R.drawable.spr_ic_supply_crate),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ad_supply_bar_button, remaining),
                        color = Color(0xFFE9F4D8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.ad_supply_bar_used),
                color = Color(0x99C5D6B4),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ODUL UYGULAYICILAR — tek akis, yalnizca odul buyuklugu degisir.
// Her biri oyuncuya gosterilecek NOTR mesaji doner (hata dili YOK).
// ---------------------------------------------------------------------------

/**
 * R1 Tedarik Talebi.
 *
 * IKI GIRIS NOKTASI, TEK FONKSIYON: hem [SupplyDropBar] seridi
 * ([RewardedPlacement.SUPPLY_DROP]) hem de bolum secim ekranindaki coin cipi
 * ([RewardedPlacement.COIN_TOP_UP]) buraya duser. Kasitli olarak
 * `result.placement`e BAKMAZ — iki giris noktasinin farkli odul odemesi, ayni
 * ekranda iki farkli fiyatli buton demek olurdu ve oyuncu her zaman ucuz olani
 * secip digerini gormezden gelirdi.
 *
 * MIKTAR BURADA YAZMIYOR — ekonomi ne verdiyse mesaj onu soyler. Eskiden
 * `AdPolicyConfig.SUPPLY_DROP_FULL_COIN` sabiti hem mesaja hem "yatir"
 * cagrisina gidiyordu; ekonominin gunluk butcesi (450) veya adaptif odul
 * bayragi devreye girdiginde ekranda yazan sayi ile bakiyeye gecen sayi
 * ayrisirdi. Artik tek sayi var ve o ekonominin dondurdugu sayidir.
 */
fun applySupplyDrop(context: Context, result: RewardedResult, bridge: AdRewardBridge): String {
    // Teklif zaten tukenmis: ekonomiye HIC gidilmez (yoksa hakki bitmis
    // oyuncu her dokunusta gunluk butceyi yerdi). Ilerleme kaybi YOK.
    if (result.outcome == RewardedOutcome.UNAVAILABLE) {
        return context.getString(R.string.ad_supply_unavailable)
    }
    val reward = bridge.grantSupplyDrop(result)
    return when {
        // Gunluk coin butcesi doldu (veya kopru bagli degil): notr mesaj,
        // hicbir ilerleme yolu kapanmaz.
        reward.coins <= 0 -> context.getString(R.string.ad_supply_unavailable)
        reward.full -> context.getString(R.string.ad_supply_full, reward.coins)
        else -> context.getString(R.string.ad_supply_reduced, reward.coins)
    }
}

/**
 * R3 Cift Odeme. Taban odul reklamdan ONCE verilmis olmalidir (GDD §G.4);
 * bu fonksiyon yalnizca EK katmanin sonucunu anlatir.
 */
fun applyDoublePayout(context: Context, result: RewardedResult, bridge: AdRewardBridge): String {
    if (result.outcome == RewardedOutcome.UNAVAILABLE) {
        return context.getString(R.string.ad_double_claimed)
    }
    val reward = bridge.grantDoublePayout(result)
    // Ek katman verilmediyse oyuncu HICBIR SEY kaybetmedi: taban odul cebinde.
    return if (reward.bonusCoins > 0) {
        context.getString(R.string.ad_double_full, reward.bonusCoins)
    } else {
        context.getString(R.string.ad_double_reduced)
    }
}

/**
 * R2 Takviye.
 *
 * GDD §G.4: reklam gelmese de **savas YINE devam eder** — bu yuzden FULL ve
 * REDUCED dallari ayni seyi yapar, fark yalnizca metindedir. Kopru
 * uygulayamadiysa (motor reddetti / bagli degil) akis yine bloklanmaz:
 * cagiran taraf yenilgi modalini gosterir ve oyuncu "tekrar dene" der.
 */
fun applyReinforcement(context: Context, result: RewardedResult, bridge: AdRewardBridge): String {
    val reward = bridge.grantReinforcement(result)
    return when {
        !reward.applied -> context.getString(R.string.ad_reinforce_failed)
        result.outcome == RewardedOutcome.FULL_REWARD ->
            context.getString(R.string.ad_reinforce_full, reward.lives)
        else ->
            context.getString(R.string.ad_reinforce_noad, reward.lives)
    }
}
