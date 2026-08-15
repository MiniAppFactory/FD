package com.miniappfactory.frontlinedefender.game.ads

import androidx.compose.foundation.background
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
    watchLabel: String = "WATCH AD",
    skipLabel: String = "NO THANKS",
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
                        text = "Contacting HQ...",
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
                        label = "CONTINUE",
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF4C7A2E))
                    .clickable(onClick = onRequest)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("supply_drop_button")
            ) {
                Text(
                    text = "REQUEST SUPPLY DROP  ($remaining left today)",
                    color = Color(0xFFF2FFE4),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = "Supply requisition used up — resets at midnight",
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

/** R1 Tedarik Talebi. */
fun applySupplyDrop(result: RewardedResult, bridge: AdRewardBridge): String =
    when (result.outcome) {
        RewardedOutcome.FULL_REWARD -> {
            bridge.grantCoins(AdPolicyConfig.SUPPLY_DROP_FULL_COIN, "rewarded_supply_drop_full")
            "Supply drop secured: +${AdPolicyConfig.SUPPLY_DROP_FULL_COIN} coin."
        }
        RewardedOutcome.REDUCED_REWARD -> {
            bridge.grantCoins(AdPolicyConfig.SUPPLY_DROP_REDUCED_COIN, "rewarded_supply_drop_reduced")
            "HQ sent what it had: +${AdPolicyConfig.SUPPLY_DROP_REDUCED_COIN} coin. " +
                "Your daily requisition was not used."
        }
        // Arbitraj tavani doldu (veya hak tukendi): ilerleme kaybi YOK,
        // yalnizca bugun daha fazla azaltilmis odul verilmez.
        RewardedOutcome.UNAVAILABLE ->
            "Supply lines are busy right now. Your daily requisition is still available."
    }

/** R3 Cift Odeme. Taban odul reklamdan ONCE verilmis olmalidir (GDD §G.4). */
fun applyDoublePayout(result: RewardedResult, bridge: AdRewardBridge): String =
    when (result.outcome) {
        RewardedOutcome.FULL_REWARD -> {
            bridge.grantDoublePayout(AdPolicyConfig.DOUBLE_PAYOUT_MULTIPLIER)
            "Double payout approved: this operation's coin payout is " +
                "x${AdPolicyConfig.DOUBLE_PAYOUT_MULTIPLIER}."
        }
        RewardedOutcome.REDUCED_REWARD ->
            "No ad was available. Your operation payout is already banked."
        RewardedOutcome.UNAVAILABLE ->
            "Double payout already claimed for this operation."
    }

/**
 * R2 Takviye. **Bugun cagrilmaz** — [AdRewardBridge.reinforcementSupported]
 * false oldugu icin teklif hic gosterilmez. Motor API'si eklendiginde
 * `GameScreen` bu fonksiyonu R2 sheet'ine baglar; iki dal da savasi
 * SURDURUR, fark yalnizca canin geri gelip gelmedigi degil — reklam yoksa da
 * can verilir (GDD §G.4: "Savas YINE devam eder").
 */
fun applyReinforcement(result: RewardedResult, bridge: AdRewardBridge): String {
    val applied = bridge.grantReinforcement(AdPolicyConfig.REINFORCEMENT_LIVES)
    return when {
        !applied -> "Reinforcements could not deploy. Your progress is safe."
        result.outcome == RewardedOutcome.FULL_REWARD ->
            "Reinforcements deployed: base restored to " +
                "${AdPolicyConfig.REINFORCEMENT_LIVES} lives."
        else ->
            "No ad was available, but reinforcements deployed anyway: base " +
                "restored to ${AdPolicyConfig.REINFORCEMENT_LIVES} lives."
    }
}
