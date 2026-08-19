package com.miniappfactory.frontlinedefender.game.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ECONOMY_AUDIT_2 madde 5 — **HAFTALIK YILDIZ ARZININ TUKENMESI.**
 *
 * `w_elite_operator` "bu hafta 15 YENI yildiz kazan" der. Yildiz best-of'tur ve
 * asla dusmez, yani kampanyanin omur boyu arzi SABITTIR: 55 x 3 = 165. Bu tavana
 * ulasan oyuncuda gorev sonsuza kadar tamamlanamaz hale gelirdi — panelde 0/15'te
 * donan bir satir ve haftalik butcenin %55'i (600 / 1.100) kalici olarak
 * erisilemez.
 *
 * Cozum ODUL EKLEMEZ: arz yetmiyorsa ayni slot, ayni odulle tamamlanabilir bir
 * goreve (`w_elite_reserve`, 24 bolum) doner. Butce 1.100'de kalir.
 *
 * **SAF JUnit.**
 */
class WeeklyStarSupplyTest {

    private val eliteReward = EconomyConfig.WEEKLY_ELITE_REWARD

    private fun weeklyWith(remaining: Int) = MissionPools.weekly(remaining)

    // =================================================================================
    // 1. Arz varken HICBIR SEY degismez
    // =================================================================================

    @Test
    fun defaultGenerationIsUnchangedForEveryPlayerWithSupplyLeft() {
        val default = MissionPools.weekly()
        val brandNew = weeklyWith(EconomyConfig.TOTAL_CAMPAIGN_STARS)
        val exactlyEnough = weeklyWith(EconomyConfig.WEEKLY_ELITE_TARGET)

        assertEquals(default, brandNew)
        assertEquals(default, exactlyEnough)
        assertEquals("w_elite_operator", default[1].id)
        assertEquals(MissionType.WEEKLY_STARS_EARNED, default[1].type)
        assertEquals(EconomyConfig.WEEKLY_ELITE_TARGET, default[1].target)
    }

    @Test
    fun totalCampaignStarSupplyIsOneSixtyFive() {
        assertEquals(165, EconomyConfig.TOTAL_CAMPAIGN_STARS)
        assertEquals(EconomyConfig.CAMPAIGN_LEVELS * 3, EconomyConfig.TOTAL_CAMPAIGN_STARS)
    }

    // =================================================================================
    // 2. Arz bitince ikame — ODUL AYNI, BUTCE AYNI
    // =================================================================================

    @Test
    fun exhaustedStarSupplySwapsInACompletableMissionWithoutAddingACoin() {
        val exhausted = weeklyWith(0)
        assertEquals(EconomyConfig.WEEKLY_MISSION_COUNT, exhausted.size)
        assertEquals("w_elite_reserve", exhausted[1].id)
        assertEquals(MissionType.WEEKLY_LEVELS_COMPLETED, exhausted[1].type)
        assertEquals(EconomyConfig.WEEKLY_ELITE_RESERVE_TARGET, exhausted[1].target)
        assertEquals("odul DEGISMEZ", eliteReward, exhausted[1].reward)
        assertEquals(
            "haftalik butce 1.100 KORUNMALI",
            EconomyConfig.WEEKLY_BUDGET,
            exhausted.sumOf { it.reward },
        )
    }

    @Test
    fun partialSupplyBelowTheTargetAlsoSwaps() {
        // 14 yildiz kalmis: 15'lik hedef HALA tamamlanamaz.
        assertEquals("w_elite_reserve", weeklyWith(EconomyConfig.WEEKLY_ELITE_TARGET - 1)[1].id)
        assertEquals("w_elite_operator", weeklyWith(EconomyConfig.WEEKLY_ELITE_TARGET)[1].id)
    }

    @Test
    fun theReserveMissionIsNeverEasierThanTheLongPatrol() {
        val exhausted = weeklyWith(0)
        assertTrue(
            "ikame gorev uzun seferden hafif olamaz",
            exhausted[1].target > exhausted[0].target,
        )
        assertEquals(2 * EconomyConfig.WEEKLY_LONG_PATROL_TARGET, exhausted[1].target)
    }

    @Test
    fun theReserveMissionIsActuallyCompletableByPlaying() {
        var weekly = weeklyWith(0)
        assertEquals(0, weeklyMissionPayout(weekly))
        weekly = advanceWeekly(weekly, MissionType.WEEKLY_LEVELS_COMPLETED, 23)
        assertEquals("23/24 -> yalnizca uzun sefer odendi", 500, weeklyMissionPayout(weekly))
        weekly = advanceWeekly(weekly, MissionType.WEEKLY_LEVELS_COMPLETED, 1)
        assertEquals(
            "24 bolum -> haftalik butcenin tamami",
            EconomyConfig.WEEKLY_BUDGET,
            weeklyMissionPayout(weekly),
        )
        // Sayac hedefi asamaz -> ikinci odeme yok.
        weekly = advanceWeekly(weekly, MissionType.WEEKLY_LEVELS_COMPLETED, 99)
        assertEquals(EconomyConfig.WEEKLY_BUDGET, weeklyMissionPayout(weekly))
    }

    // =================================================================================
    // 3. Hafta ORTASINDA uzlastirma — `reconcileWeekly`
    // =================================================================================

    @Test
    fun midWeekExhaustionReplacesTheNowImpossibleMission() {
        // Hafta basinda 20 yildiz arzi vardi; oyuncu Sali gunu son 20'yi de aldi.
        val started = weeklyWith(20)
        assertEquals("w_elite_operator", started[1].id)
        val reconciled = reconcileWeekly(started, remainingNewStars = 0)
        assertEquals("w_elite_reserve", reconciled[1].id)
        assertEquals(eliteReward, reconciled[1].reward)
        assertEquals("uzun sefer gorevi ELLENMEZ", started[0], reconciled[0])
    }

    @Test
    fun reconcileLooksAtWhatIsSTILLNEEDEDNotTheWholeTarget() {
        // 15 hedefin 13'u dolmus, 2 yildiz kalmis -> gorev HALA tamamlanabilir.
        val nearlyDone = weeklyWith(165).map {
            if (it.type == MissionType.WEEKLY_STARS_EARNED) it.advanced(13) else it
        }
        assertEquals("w_elite_operator", reconcileWeekly(nearlyDone, remainingNewStars = 2)[1].id)
        // 1 yildiz kalmis -> 2 gerekiyor, artik imkansiz.
        assertEquals("w_elite_reserve", reconcileWeekly(nearlyDone, remainingNewStars = 1)[1].id)
    }

    @Test
    fun aCompletedOrClaimedMissionIsNeverSwapped() {
        // Ikame, tamamlanmis gorevi sifirlarsa ayni hafta 600 coin IKI KEZ alinir.
        val done = weeklyWith(165).map {
            if (it.type == MissionType.WEEKLY_STARS_EARNED) it.advanced(15) else it
        }
        assertTrue(done[1].isComplete)
        assertEquals(done, reconcileWeekly(done, remainingNewStars = 0))

        val claimed = done.map { it.copy(claimed = true) }
        assertEquals(claimed, reconcileWeekly(claimed, remainingNewStars = 0))
        // Odul iki kez uretilmedi: butce hala 1.100.
        assertEquals(
            EconomyConfig.WEEKLY_BUDGET,
            reconcileWeekly(done, remainingNewStars = 0).sumOf { it.reward },
        )
    }

    @Test
    fun progressIsNeverCarriedAcrossTheSwapBecauseTheUnitsDiffer() {
        // 14 yildiz ilerlemis ama arz bitmis -> ikame 0'dan baslar.
        val partial = weeklyWith(165).map {
            if (it.type == MissionType.WEEKLY_STARS_EARNED) it.advanced(14) else it
        }
        val reconciled = reconcileWeekly(partial, remainingNewStars = 0)
        assertEquals("w_elite_reserve", reconciled[1].id)
        assertEquals("yildiz ilerlemesi bolum sayacina TASINMAZ", 0, reconciled[1].progress)
        assertEquals(0, weeklyMissionPayout(reconciled))
    }

    @Test
    fun reconcileIsIdempotent() {
        val once = reconcileWeekly(weeklyWith(165), remainingNewStars = 0)
        assertEquals(once, reconcileWeekly(once, remainingNewStars = 0))
    }

    @Test
    fun freshSupplyRestoresTheStarMissionWhenNoProgressWasMade() {
        // Yeni bolum paketi gelirse arz yeniden acilir; ilerlemesi olmayan ikame
        // gorev asil gorevine doner.
        val reserve = weeklyWith(0)
        val restored = reconcileWeekly(reserve, remainingNewStars = 30)
        assertEquals("w_elite_operator", restored[1].id)
        // Ilerleme varsa hafta ortasinda geri alinmaz — oyuncunun emegi silinmez.
        val worked = reserve.map { if (it.id == "w_elite_reserve") it.advanced(5) else it }
        assertEquals("w_elite_reserve", reconcileWeekly(worked, remainingNewStars = 30)[1].id)
    }

    @Test
    fun theSwapChangesReachabilityNotIncome() {
        val normal = weeklyWith(165)
        val swapped = weeklyWith(0)
        assertNotEquals(normal[1].id, swapped[1].id)
        assertEquals(normal.sumOf { it.reward }, swapped.sumOf { it.reward })
        assertEquals(normal.size, swapped.size)
    }
}
