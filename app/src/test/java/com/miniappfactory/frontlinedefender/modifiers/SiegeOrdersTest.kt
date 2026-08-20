package com.miniappfactory.frontlinedefender.modifiers

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KUSATMA EMRI (CAMPAIGN_55.md M7) — "butce tek seferlik".
 *
 * ---------------------------------------------------------------------------------
 * BU DOSYA UC HATAYI BIRDEN ENGELLIYOR, ucu de bu ozelligi yazarken GERCEKTEN olustu
 * ---------------------------------------------------------------------------------
 *
 * 1. **x0,25 OLCUMLE REDDEDILDI.** Odul tam sayiya kirpildigi icin kucuk oduller
 *    (5 -> 1) cokuyor ve bolumun geliri 2.594 yerine 132 cikiyordu; yani carpan
 *    "%25 gelir" degil, dusman tipine gore ONGORULEMEZ bir kirpinti demekti.
 *    Tasarim zaten "(veya x0)" diyordu; sifir hem sadik hem olculebilir.
 *
 * 2. **TELAFI `LevelSpec.startingSupply` ICINE KONULAMAZ.** Dalga URETICISI o
 *    degeri okuyor: L35'e 2.594 eklemek bolumun dalgalarini buyuttu, gelir
 *    degisti, butce degisti — on yedi test birden kirildi. Ek, calisma zamaninda
 *    motorun `_gold`una biner; uretici bolumun TASARIM degerini gorur.
 *
 * 3. **SIMULATOR KISITI TANIMIYORDU.** `CampaignSolvabilityAllLevelsTest` L35 icin
 *    YESILDI ama olctugu sey oyuncunun oynayacagi bolum DEGILDI — ne sifir geliri
 *    ne de basta verilen butceyi biliyordu. Kisiti modellemeden yesil kalan bir
 *    test, yakalamasi gereken yerde gozden kacirir.
 */
class SiegeOrdersTest {

    private val siegeLevels: List<Int>
        get() = GameConfig.LEVEL_MODIFIERS
            .filterValues { it.supplyRewardMultiplier < 1f }
            .keys.sorted()

    @Test
    fun siegeOrdersExistInTheCampaign() {
        assertTrue(
            "kusatma emri hicbir bolumde yok — ozellik yazildi ama kampanyaya baglanmadi",
            siegeLevels.isNotEmpty()
        )
    }

    /**
     * Gelir SIFIR olmali, "az" degil. Ara bir carpan (1) numarali derste
     * olculerek reddedildi; bu kilit onun geri gelmesini engeller.
     */
    @Test
    fun siegeOrdersZeroTheKillIncomeRatherThanShrinkingIt() {
        siegeLevels.forEach { level ->
            assertEquals(
                "L$level kusatma carpani ara bir deger — odul kirpilmasi yuzunden " +
                    "ara carpanlar ongorulemez gelir uretir (bkz. sinif KDoc'u 1)",
                0f,
                GameConfig.modifiersFor(level).supplyRewardMultiplier,
                0.0001f
            )
        }
    }

    /**
     * Bolum FAKIRLESMEMELI: goturulen gelir kadar sermaye geri verilmeli.
     * Kural bir zorluk artisi degil, gelirin ZAMANINI degistiren bir karar.
     */
    @Test
    fun theCompensationGivesBackWhatTheMultiplierTakesAway() {
        siegeLevels.forEach { level ->
            val bonus = GameConfig.modifiersFor(level).startingSupplyBonus
            assertTrue(
                "L$level telafisi yok ($bonus) — gelir goturuldu ama sermaye " +
                    "yukseltilmedi, bolum sessizce fakirlesti",
                bonus > 0
            )
        }
    }

    /**
     * Telafi TASARIM degerine degil, CALISMA ZAMANI cuzdanina binmeli.
     *
     * `LevelSpec.startingSupply` uretinin girdisi; oraya eklemek dalgalari
     * buyutur (bkz. sinif KDoc'u 2). Bu test o ayrimi dogrudan kilitliyor:
     * spec HALA tasarim degerini tasimali.
     */
    @Test
    fun theBonusStaysOutOfTheLevelSpecSoTheWaveGeneratorNeverSeesIt() {
        siegeLevels.forEach { level ->
            val spec = GameConfig.levelSpec(level)
            val design = GameConfig.startingSupplyFor(level)
            assertEquals(
                "L$level spec sermayesi tasarim degerinden sapti — telafi spec'e " +
                    "sizmis, dalga uretici bolumu zengin sanacak",
                design,
                spec.startingSupply
            )
        }
    }
}
