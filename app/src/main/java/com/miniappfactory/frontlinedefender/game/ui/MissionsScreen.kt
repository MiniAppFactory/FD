package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.Mission
import com.miniappfactory.frontlinedefender.game.economy.MissionSlot
import com.miniappfactory.frontlinedefender.game.economy.MissionType
import com.miniappfactory.frontlinedefender.game.economy.WeeklyMission

/**
 * Faz 14 — GOREV PANELI.
 *
 * ## Neden bu ekran var
 * `docs/FUN_AUDIT.md`: gunluk/haftalik gorev sistemi saat-hilesi savunmasiyla
 * birlikte EKSIKSIZ yazilmisti (`MissionSystem.kt`, 500+ satir, testli) ama
 * **hicbir UI'si yoktu** ve `claimCompletedMissions()` sifir kez cagriliyordu.
 * Yani tasarlanan haftalik ~3.620 coin'lik "geri donme sebebi" oyuncuya HIC
 * ulasmiyordu. Bu dosya o katmanin gorunur yuzu.
 *
 * ## Tasarim kurallari
 * 1. **Ekran hicbir ekonomi karari VERMEZ.** Odul miktari, damper carpani,
 *    "alinabilir mi" sorusu — hepsi [CampaignProgress] uzerinden gelir. Panel
 *    yalnizca cizer ve tek bir eylem tetikler ([CampaignProgress.claimCompletedMissions]).
 * 2. **Tek buton.** Gorev basina ayri "AL" butonu YOK: uc gunluk gorev bittiginde
 *    +100 bonus veriliyor (GDD E.1) ve gorev basina toplama, bonusun ne zaman
 *    dustugunu oyuncu icin belirsizlestirirdi. Tek "ODULU AL" hepsini oder.
 * 3. **Landscape.** Oyun yatay; gunluk ve haftalik SUTUN yan yana durur, dikey
 *    kaydirma yalnizca kucuk ekranlarda devreye girer.
 * 4. **Tasma.** Gorev metinleri Turkcede belirgin sekilde uzuyor
 *    ("Tek savasta tum acik kule tiplerini kur"); basliklar iki satira sarar,
 *    butonlar `AutoShrinkText` ile kucultulur, hicbiri kesilmez.
 */
@Composable
fun MissionsScreen(
    progress: CampaignProgress,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Panel acilirken gun/hafta donusunu yeniden degerlendir: uygulama gece
    // yarisini acik gecirmis olabilir ve panel bayat gorevleri gosterirdi.
    // Saat hilesi savunmasi ayni `evaluateReset` kapisindan gectigi icin panel
    // acip kapatarak sifirlama URETILEMEZ.
    LaunchedEffect(progress) { progress.refreshMissions() }

    // Odul alindiktan sonra listeyi tazelemek icin. `todaysMissions` Compose
    // snapshot state'i uzerinden geliyor ama sahte implementasyonlarda
    // olmayabilir; bu sayac her iki durumda da yeniden cizimi garanti eder.
    var claimTick by remember { mutableIntStateOf(0) }
    var lastPayout by remember { mutableIntStateOf(0) }

    val daily = progress.todaysMissions
    val weekly = progress.weeklyMissions
    val claimable = progress.claimableMissionCount
    val pendingCoins = remember(claimTick, daily, weekly) { pendingReward(progress) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0E140C), Color(0xFF1B2416), Color(0xFF090C07))
                )
            )
            // Alttaki bolum kartlarina dokunus SIZMASIN: panel aciksa kazara
            // sevk olmamali.
            .clickable(enabled = true, onClick = {})
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ---- Ust serit -------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE6121A10))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.mission_title),
                    color = Color(0xFFE8F0DC),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )

                Spacer(Modifier.weight(1f))

                if (progress.missionRewardMultiplier < 1.0) {
                    // GDD E.4 kural 6: damper YALNIZCA gorev odulunu etkiler ve
                    // oyuncuya NOTR dille bildirilir; suclama yok.
                    Text(
                        text = stringResource(
                            R.string.mission_damped,
                            Math.round(progress.missionRewardMultiplier * 100).toInt()
                        ),
                        color = Color(0xFFD6B87F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        modifier = Modifier.width(190.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                }

                Text(
                    text = stringResource(R.string.mission_close),
                    color = Color(0xFFB8C9A8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x33FFFFFF))
                        .clickable { onClose() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("missions_close")
                )
            }

            if (daily.isEmpty() && weekly.isEmpty()) {
                Text(
                    text = stringResource(R.string.mission_empty),
                    color = Color(0x99C5D6B4),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp)
                )
                return@Column
            }

            // ---- Iki sutun: gunluk | haftalik ------------------------------
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.35f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    SectionHeader(stringResource(R.string.mission_daily_header))
                    daily.forEach { mission ->
                        DailyMissionRow(mission)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = stringResource(
                            R.string.mission_all_bonus,
                            EconomyConfig.DAILY_ALL_COMPLETE_BONUS
                        ),
                        color = Color(0x99C5D6B4),
                        fontSize = 10.sp,
                        maxLines = 2
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    SectionHeader(stringResource(R.string.mission_weekly_header))
                    weekly.forEach { mission ->
                        WeeklyMissionRow(mission)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = stringResource(R.string.mission_reset_hint),
                        color = Color(0x99C5D6B4),
                        fontSize = 10.sp,
                        maxLines = 3
                    )
                }
            }

            // ---- Tek eylem: ODULU AL --------------------------------------
            val enabled = claimable > 0
            val label = when {
                lastPayout > 0 && !enabled ->
                    stringResource(R.string.mission_claimed_toast, lastPayout)
                enabled -> stringResource(R.string.mission_claim_all, pendingCoins)
                else -> stringResource(R.string.mission_nothing_to_claim)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 12.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (enabled) Color(0xFF4C7A2E) else Color(0x22FFFFFF))
                    .clickable(enabled = enabled) {
                        lastPayout = progress.claimCompletedMissions()
                        claimTick++
                    }
                    .testTag("missions_claim")
            ) {
                AutoShrinkText(
                    text = label,
                    color = if (enabled) Color(0xFFFFF3C4) else Color(0x66FFFFFF),
                    fontWeight = FontWeight.Black,
                    maxFontSize = 15.sp,
                    minFontSize = 10.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }
        }
    }
}

/**
 * Panelin GOSTERECEGI toplam — damper dahil. Ekonomi karari degil, ekonominin
 * kendi carpaniyla yapilan bir GOSTERIM hesabidir; odemenin tek sahibi
 * [CampaignProgress.claimCompletedMissions] olmaya devam eder.
 */
private fun pendingReward(progress: CampaignProgress): Int {
    val multiplier = progress.missionRewardMultiplier
    val daily = progress.todaysMissions
    var base = daily.filter { it.isComplete && !it.claimed }.sumOf { it.reward }
    base += progress.weeklyMissions.filter { it.isComplete && !it.claimed }.sumOf { it.reward }
    val allDone = daily.size == EconomyConfig.DAILY_MISSION_SLOTS &&
        daily.all { it.isComplete } &&
        daily.any { !it.claimed }
    if (allDone) base += EconomyConfig.DAILY_ALL_COMPLETE_BONUS
    return Math.floor(base * multiplier).toInt()
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = Color(0xFF8FA87A),
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun DailyMissionRow(mission: Mission) {
    MissionRow(
        slotLabel = stringResource(slotLabelRes(mission.template.slot)),
        title = stringResource(missionTitleRes(mission.type), mission.target),
        progress = mission.progress,
        target = mission.target,
        reward = mission.reward,
        complete = mission.isComplete,
        claimed = mission.claimed,
        tag = "mission_${mission.id}",
    )
}

@Composable
private fun WeeklyMissionRow(mission: WeeklyMission) {
    MissionRow(
        slotLabel = null,
        title = stringResource(missionTitleRes(mission.type), mission.target),
        progress = mission.progress,
        target = mission.target,
        reward = mission.reward,
        complete = mission.isComplete,
        claimed = mission.claimed,
        tag = "mission_${mission.id}",
    )
}

/**
 * Tek gorev satiri: baslik, ilerleme cubugu, sayac ve odul.
 *
 * TAMAMLANMISLIK GORUNURLUGU (gorev 5): bitmis gorev yalnizca cubugu doldurmaz,
 * kart kenari ve odul rengi de degisir; ALINMIS gorev soner. Panel acilmadan da
 * fark edilebilmesi icin ayni sayac bolum secme ekranindaki rozete yansir
 * (`LevelSelectScreen`, `claimableMissionCount`).
 */
@Composable
private fun MissionRow(
    slotLabel: String?,
    title: String,
    progress: Int,
    target: Int,
    reward: Int,
    complete: Boolean,
    claimed: Boolean,
    tag: String,
) {
    val accent = when {
        claimed -> Color(0x556E7A5E)
        complete -> Color(0xFF9CD65B)
        else -> Color(0x66A8C48C)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (claimed) Color(0x8815170F) else Color(0xCC1E2A18))
            .padding(10.dp)
            .testTag(tag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (slotLabel != null) {
                Text(
                    text = slotLabel,
                    color = Color(0xFF8FA87A),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x22A8C48C))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.mission_reward, reward),
                color = if (claimed) Color(0x66FFD54F) else Color(0xFFFFD54F),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (claimed) {
                    stringResource(R.string.mission_claimed)
                } else {
                    stringResource(R.string.mission_progress, progress, target)
                },
                color = if (complete) Color(0xFF9CD65B) else Color(0x99C5D6B4),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(6.dp))

        // Gorev metni TR'de iki satira sarabilir; kesme yok, punto sabit.
        Text(
            text = title,
            color = if (claimed) Color(0x99DCE8CC) else Color(0xFFDCE8CC),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )

        Spacer(Modifier.height(6.dp))

        // Ilerleme cubugu — Canvas degil iki Box: duragan ekranda yeniden
        // cizim maliyeti sifir ve tema renkleri dogrudan okunur.
        val fraction = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x33000000))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
        }
    }
}

/** Slot rozetinin metni (GDD E.1 slot adlari). */
internal fun slotLabelRes(slot: MissionSlot): Int = when (slot) {
    MissionSlot.PARTICIPATION -> R.string.mission_slot_participation
    MissionSlot.VOLUME -> R.string.mission_slot_volume
    MissionSlot.SKILL -> R.string.mission_slot_skill
}

/**
 * Gorev basligi — **tip bazinda**, sablon id bazinda DEGIL.
 *
 * Ayni tip birden fazla sablonda farkli hedeflerle geciyor (KILL_ENEMIES hem 40
 * hem 120). Hedef bicim argumani olarak gecilir; havuza yeni bir hedefli sablon
 * eklendiginde ceviri dosyasi degismek zorunda kalmaz.
 *
 * `when` bilincli olarak EKSIKSIZ (else yok): `MissionType`a yeni bir deger
 * eklendiginde bu dosya DERLENMEZ ve gorev sessizce metinsiz kalmaz.
 */
internal fun missionTitleRes(type: MissionType): Int = when (type) {
    MissionType.COMPLETE_ANY_LEVEL -> R.string.mission_type_complete_any_level
    MissionType.SKIP_PREP_TIMER -> R.string.mission_type_skip_prep_timer
    MissionType.KILL_ENEMIES -> R.string.mission_type_kill_enemies
    MissionType.KILL_ARMORED -> R.string.mission_type_kill_armored
    MissionType.KILL_TANKS -> R.string.mission_type_kill_tanks
    MissionType.BUILD_TOWERS -> R.string.mission_type_build_towers
    MissionType.UPGRADE_TOWERS -> R.string.mission_type_upgrade_towers
    MissionType.EARN_SUPPLY_IN_ONE_BATTLE -> R.string.mission_type_earn_supply
    MissionType.CLEAR_WITH_THREE_STARS -> R.string.mission_type_three_stars
    MissionType.CLEAR_WITHOUT_SELLING -> R.string.mission_type_no_selling
    MissionType.CLEAR_WITH_HIGH_HEALTH -> R.string.mission_type_high_health
    MissionType.BUILD_ALL_TOWER_TYPES -> R.string.mission_type_all_towers
    MissionType.CLEAR_AT_DOUBLE_SPEED -> R.string.mission_type_double_speed
    MissionType.WEEKLY_LEVELS_COMPLETED -> R.string.mission_type_weekly_levels
    MissionType.WEEKLY_STARS_EARNED -> R.string.mission_type_weekly_stars
}
