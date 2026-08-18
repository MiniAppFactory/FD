package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerRole
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 13 / DECISIONS B5 — **KULE KADEME MERDIVENI.**
 *
 * `TowerStats` artik `level1...` / `level2...` duz alanlar degil bir kademe LISTESI.
 * Bu dosya iki isi birden yapiyor:
 *
 *  1. **REGRESYON KILIDI.** Kademe 3 bir EKLEMEDIR, bir yeniden dengeleme
 *     DEGIL. Kademe 1 ve 2'nin butun sayilari donduruldu ([FROZEN_TIER_1_2]);
 *     biri veri yapisini tasirken bir basamagi kaydirirsa (liste sirasi, yanlis
 *     kademeye yazilan deger) burasi kirmizi olur. Faz 10 §1-§5 tablolari,
 *     `WaveMetrics.referenceTowerDps` kilidi ve Faz 12.1'in cozulebilirlik
 *     testleri bu sayilarin uzerine kurulu.
 *  2. **MERDIVEN SOZLESMESI.** Kademe sayisi VERIDIR: motor ve UI "son kademe
 *     mi" sorusunu listeden turetir. Buradaki testler merdivenin sekline
 *     (monotonluk, azalan getiri, rol korunumu, kilit sirasi) bakar; hicbiri
 *     "kademe sayisi 3'tur" varsayimiyla yazilmadi — tek istisna
 *     [EXPECTED_TIER_COUNT], ki o zaten ITIRAZIN kendisidir.
 */
class TowerTierTest {

    /**
     * Her kulenin sahip olmasi GEREKEN kademe sayisi.
     *
     * Bu bir varsayim degil bir KARAR: dort kule de ayni merdiven boyuna
     * sahiptir, cunku "hangi kule daha derine gider" ayrimi kule kimliklerini
     * (Faz 10 rol sistemi) bulanistirir ve oyuncuya sahada okunmayan bir kural
     * ogretir. Kademe 4 bir gun eklenirse burasi ve `TOWER_SPECS` degisir,
     * baska hicbir yer degismez.
     */
    private val EXPECTED_TIER_COUNT = 3

    /**
     * **DONDURULMUS KADEME 1-2 TABLOSU** (Faz 10 kalibrasyonu + Faz 12.1
     * yeniden olcumu; cihazda dogrulandi).
     *
     * Sira: menzil (ref-px) · atis basina hasar · atis araligi (sn) ·
     * o kademeye yukselmenin bedeli.
     *
     * Bu tablonun elle yazilmis olmasi KASITLIDIR — bir regresyon kilidi,
     * korudugu degeri korudugu kaynaktan okuyamaz. Burayi degistirmek bir
     * YENIDEN DENGELEME kararidir ve `docs/TOWER_REBALANCE.md` guncellenmeden
     * yapilamaz.
     */
    private val FROZEN_TIER_1_2: Map<TowerType, List<FrozenTier>> = mapOf(
        TowerType.MACHINE_GUN to listOf(
            FrozenTier(range = 150f, damage = 14f, fireRate = 0.32f, upgradeCost = 0),
            FrozenTier(range = 180f, damage = 26f, fireRate = 0.24f, upgradeCost = 65)
        ),
        TowerType.CANNON to listOf(
            FrozenTier(range = 175f, damage = 46f, fireRate = 2.50f, upgradeCost = 0),
            FrozenTier(range = 205f, damage = 88f, fireRate = 2.20f, upgradeCost = 90)
        ),
        TowerType.ANTI_ARMOR to listOf(
            FrozenTier(range = 250f, damage = 110f, fireRate = 3.60f, upgradeCost = 0),
            FrozenTier(range = 290f, damage = 205f, fireRate = 3.10f, upgradeCost = 115)
        ),
        TowerType.SLOW to listOf(
            FrozenTier(range = 270f, damage = 3f, fireRate = 1.30f, upgradeCost = 0),
            FrozenTier(range = 320f, damage = 7f, fireRate = 1.10f, upgradeCost = 85)
        )
    )

    private data class FrozenTier(
        val range: Float,
        val damage: Float,
        val fireRate: Float,
        val upgradeCost: Int
    )

    private fun spec(t: TowerType) = GameConfig.TOWER_SPECS.getValue(t)

    // =====================================================================
    // Merdivenin sekli
    // =====================================================================

    @Test
    fun everyTowerHasExactlyTheExpectedNumberOfTiers() {
        TowerType.values().forEach { t ->
            assertEquals(
                "$t kademe sayisi — dort kule de ayni merdiven boyuna sahip olmali",
                EXPECTED_TIER_COUNT, spec(t).maxTier
            )
        }
    }

    @Test
    fun tierNumbersAreConsecutiveStartingAtOne() {
        TowerType.values().forEach { t ->
            spec(t).tiers.forEachIndexed { index, tier ->
                assertEquals(
                    "$t: listenin $index. elemani kademe ${index + 1} olmali, " +
                        "${tier.tier} yazilmis",
                    index + 1, tier.tier
                )
            }
        }
    }

    @Test
    fun theFirstTierIsReachedByBuildingNotByUpgrading() {
        TowerType.values().forEach { t ->
            assertEquals(
                "$t kademe 1'in yukseltme bedeli 0 olmali — o kademeye insa " +
                    "bedeliyle gelinir, iki kez odenmez",
                0, spec(t).tier(1).upgradeCost
            )
            assertTrue("$t insa bedeli pozitif olmali", spec(t).buildCost > 0)
        }
    }

    // =====================================================================
    // 1. REGRESYON KILIDI — kademe 1 ve 2 DEGISMEDI
    // =====================================================================

    @Test
    fun tierOneAndTierTwoValuesAreUnchangedByTheTierThreeAddition() {
        FROZEN_TIER_1_2.forEach { (type, frozen) ->
            val s = spec(type)
            frozen.forEachIndexed { index, expected ->
                val tier = index + 1
                val actual = s.tier(tier)
                assertEquals("$type kademe $tier menzili", expected.range, actual.range, 0f)
                assertEquals("$type kademe $tier hasari", expected.damage, actual.damage, 0f)
                assertEquals("$type kademe $tier atis araligi", expected.fireRate, actual.fireRate, 0f)
                assertEquals(
                    "$type kademe $tier yukseltme bedeli",
                    expected.upgradeCost, actual.upgradeCost
                )
            }
        }
    }

    @Test
    fun theFrozenTableCoversEveryTowerAndBothEarlyTiers() {
        // Kilidin kendisi bayatlamasin: yeni bir kule tipi eklenirse tabloya da
        // eklenmeli, yoksa o kule sessizce korumasiz kalir.
        assertEquals(
            "dondurulmus tablo butun kule tiplerini kapsamali",
            TowerType.values().toSet(), FROZEN_TIER_1_2.keys
        )
        FROZEN_TIER_1_2.forEach { (type, frozen) ->
            assertEquals("$type icin kademe 1 ve 2 dondurulmus olmali", 2, frozen.size)
        }
    }

    /**
     * Dalga zorluk egrisinin olcu birimi. `WaveMetrics.referenceTowerDps`
     * Gatling kademe 1 DPS'ini okuyor; bu sayi kayarsa 22 bolumun AEHP
     * tablosu sessizce baska bir seyi olcmeye baslar.
     */
    @Test
    fun theReferenceTowerDpsUsedByTheWaveCurveIsUntouched() {
        assertEquals(
            "referans kule DPS'i (Gatling Kd.1) degismemeli",
            43.75f, spec(TowerType.MACHINE_GUN).tier(1).dps, 1e-4f
        )
    }

    // =====================================================================
    // 2. KADEME 3 EGRISI — monoton ve azalan getirili
    // =====================================================================

    @Test
    fun everyTierIsStrictlyBetterThanTheOneBelowItInEveryDimension() {
        TowerType.values().forEach { t ->
            val s = spec(t)
            for (tier in 2..s.maxTier) {
                val lower = s.tier(tier - 1)
                val upper = s.tier(tier)
                assertTrue(
                    "$t: kademe $tier menzili (${upper.range}) alt kademeden " +
                        "(${lower.range}) buyuk olmali",
                    upper.range > lower.range
                )
                assertTrue(
                    "$t: kademe $tier hasari (${upper.damage}) alt kademeden " +
                        "(${lower.damage}) buyuk olmali",
                    upper.damage > lower.damage
                )
                assertTrue(
                    "$t: kademe $tier atis ARALIGI (${upper.fireRate}s) alt " +
                        "kademeden (${lower.fireRate}s) KUCUK olmali — sn cinsinden",
                    upper.fireRate < lower.fireRate
                )
                assertTrue(
                    "$t: kademe $tier DPS'i (${upper.dps}) alt kademeden " +
                        "(${lower.dps}) buyuk olmali",
                    upper.dps > lower.dps
                )
            }
        }
    }

    @Test
    fun upgradePricesRiseMonotonicallyUpTheLadder() {
        TowerType.values().forEach { t ->
            val s = spec(t)
            for (tier in 3..s.maxTier) {
                assertTrue(
                    "$t: kademe $tier bedeli (${s.tier(tier).upgradeCost}) bir alt " +
                        "kademeninkinden (${s.tier(tier - 1).upgradeCost}) buyuk olmali",
                    s.tier(tier).upgradeCost > s.tier(tier - 1).upgradeCost
                )
            }
        }
    }

    /**
     * Yukseltme insa etmekten cok pahali olursa oyuncu hep yeni kule diker ve
     * yukseltme mekanigi olu kalir. Faz 10'daki "insa bedelinin 2 katini
     * gecmez" kurali kademe 3'te dogru olcuye BAGLANIR: o kademeye kadar
     * yapilmis TOPLAM yatirim.
     */
    @Test
    fun noSingleUpgradeCostsMoreThanTwiceWhatTheTowerAlreadySwallowed() {
        TowerType.values().forEach { t ->
            val s = spec(t)
            var invested = s.buildCost
            for (tier in 2..s.maxTier) {
                val cost = s.tier(tier).upgradeCost
                assertTrue(
                    "$t: kademe $tier bedeli $cost, o ana kadarki yatirim $invested — " +
                        "tek bir yukseltme yatirimin 2 katini gecmemeli",
                    cost <= invested * 2
                )
                invested += cost
            }
        }
    }

    /**
     * **AZALAN GETIRI.** Kademe 3, kademe 2'den DAHA PAHALI bir DPS satin alir.
     *
     * Bu, kademe 3'un otomatik alinan bir sey olmamasinin garantisi: yatay
     * buyume (yeni kule) mumkun oldugu surece daha ucuzdur, ve kademe 3
     * yalnizca kit olan kaynak PAD oldugunda (Act II) mantikli olur. Ters
     * yonde bir egri, gec oyunda tek dogru hamle uretirdi = karar yine olurdu.
     *
     * Destek kulesi haric: onun urunu DPS degil ALAN ve TEMPO.
     */
    @Test
    fun eachTierBuysItsDamagePerSecondAtAWorsePriceThanTheTierBelow() {
        GameConfig.TOWER_SPECS.values
            .filter { it.role != TowerRole.SUPPORT }
            .forEach { s ->
                var previousPricePerDps: Float? = null
                for (tier in 2..s.maxTier) {
                    val gain = s.tier(tier).dps - s.tier(tier - 1).dps
                    assertTrue("${s.type}: kademe $tier DPS kazanci pozitif olmali", gain > 0f)
                    val pricePerDps = s.tier(tier).upgradeCost / gain
                    previousPricePerDps?.let { previous ->
                        assertTrue(
                            "${s.type}: kademe $tier DPS basina $pricePerDps Tedarik, " +
                                "kademe ${tier - 1} $previous — ust kademe DAHA PAHALI " +
                                "olmali (azalan getiri)",
                            pricePerDps > previous
                        )
                    }
                    previousPricePerDps = pricePerDps
                }
            }
    }

    // =====================================================================
    // 3. KADEME KILIDI
    // =====================================================================

    @Test
    fun tierUnlockLevelsNeverGoBackwardsUpTheLadder() {
        TowerType.values().forEach { t ->
            val s = spec(t)
            for (tier in 2..s.maxTier) {
                assertTrue(
                    "$t: kademe $tier kilidi (${s.tier(tier).unlockedAtLevel}) alt " +
                        "kademeninkinden (${s.tier(tier - 1).unlockedAtLevel}) once " +
                        "olamaz — `maxTierAt` sayarak calisiyor",
                    s.tier(tier).unlockedAtLevel >= s.tier(tier - 1).unlockedAtLevel
                )
            }
        }
    }

    @Test
    fun theFirstTwoTiersOpenTogetherWithTheTowerItself() {
        TowerType.values().forEach { t ->
            val s = spec(t)
            (1..2).forEach { tier ->
                assertEquals(
                    "$t kademe $tier, kulenin kendi kilidiyle (bolum " +
                        "${s.unlockedAtLevel}) birlikte acilmali — Faz 10 kilit " +
                        "tablosu degistirilmedi",
                    s.unlockedAtLevel, s.tier(tier).unlockedAtLevel
                )
            }
        }
    }

    /**
     * Kademe 3 Act II'nin ILK bolumunde acilir. Act I'in doruk noktasi L11
     * (ilk boss, LEVEL_DESIGN H.1 %40-55 yenilgi bekliyor) kademe 3'e
     * DOKUNMADAN oynanmali; aksi halde erken bolumler trivial olur.
     */
    @Test
    fun theThirdTierOpensExactlyWhenActTwoBegins() {
        val unlock = GameConfig.TIER_THREE_UNLOCK_LEVEL
        val firstActTwoLevel = GameConfig.CAMPAIGN.filter { it.act == 2 }.minOf { it.levelId }
        assertEquals(
            "kademe 3 kilidi Act II'nin ilk bolumu olmali",
            firstActTwoLevel, unlock
        )

        TowerType.values().forEach { t ->
            val s = spec(t)
            for (tier in 3..s.maxTier) {
                assertEquals(
                    "$t kademe $tier bolum $unlock'de acilmali",
                    unlock, s.tier(tier).unlockedAtLevel
                )
            }
            assertEquals(
                "$t: Act I'in son bolumunde merdiven 2 kademeyle sinirli olmali",
                2, s.maxTierAt(unlock - 1)
            )
            assertEquals(
                "$t: bolum $unlock'de merdivenin tamami acik olmali",
                s.maxTier, s.maxTierAt(unlock)
            )
        }
    }

    @Test
    fun theTierCapNeverDropsAsTheCampaignProgresses() {
        TowerType.values().forEach { t ->
            var previous = 0
            for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
                val cap = GameConfig.maxTowerTier(t, level)
                assertTrue(
                    "$t: bolum $level kademe tavani $cap, onceki bolumde $previous — " +
                        "oyuncunun elinden bir kademe geri alinamaz",
                    cap >= previous
                )
                previous = cap
            }
            assertEquals(
                "$t: kampanyanin sonunda merdivenin tamami acik olmali",
                spec(t).maxTier, previous
            )
        }
    }

    // =====================================================================
    // 4. ROL KORUNUMU — kademe 3 dort kuleyi ayni seye donusturmemeli
    // =====================================================================

    /** `GameEngine.applyDamageToEnemy` aynasi; splash zirhi bypass eder (B2). */
    private fun effectiveDpsAgainst(type: TowerType, tier: Int, enemy: EnemyType): Float {
        val s = spec(type)
        val e = GameConfig.ENEMY_SPECS.getValue(enemy)
        val perShot = when {
            s.splashRadius > 0f -> s.tier(tier).damage * e.splashVulnerability
            else -> {
                val armorLeft = (e.armor * (1f - s.armorPierce)).coerceAtLeast(0f)
                s.tier(tier).damage * (1f - armorLeft)
            }
        }
        return perShot / s.tier(tier).fireRate
    }

    @Test
    fun theCrowdTowerStaysTheBestAgainstUnarmouredInfantryAtEveryTier() {
        val crowd = GameConfig.TOWER_SPECS.values.single { it.role == TowerRole.CROWD }
        for (tier in 1..crowd.maxTier) {
            val mine = effectiveDpsAgainst(crowd.type, tier, EnemyType.INFANTRY)
            GameConfig.TOWER_SPECS.values.filter { it.type != crowd.type }.forEach { other ->
                assertTrue(
                    "kademe $tier: ${crowd.type} piyadeye $mine DPS, ${other.type} " +
                        "${effectiveDpsAgainst(other.type, tier, EnemyType.INFANTRY)} — " +
                        "kalabalik kulesi piyadede en iyi kalmali",
                    mine > effectiveDpsAgainst(other.type, tier, EnemyType.INFANTRY)
                )
            }
        }
    }

    @Test
    fun theAntiTankTowerStaysTheBestAgainstArmourAtEveryTier() {
        val antiTank = GameConfig.TOWER_SPECS.values.single { it.role == TowerRole.ANTI_TANK }
        for (tier in 1..antiTank.maxTier) {
            val mine = effectiveDpsAgainst(antiTank.type, tier, EnemyType.TANK)
            GameConfig.TOWER_SPECS.values.filter { it.type != antiTank.type }.forEach { other ->
                assertTrue(
                    "kademe $tier: ${antiTank.type} tanka $mine DPS, ${other.type} " +
                        "${effectiveDpsAgainst(other.type, tier, EnemyType.TANK)} — " +
                        "zirh kirici tankta en iyi kalmali",
                    mine > effectiveDpsAgainst(other.type, tier, EnemyType.TANK)
                )
            }
        }
    }

    /**
     * Gatling zirha karsi ise yaramaz KALMALI. Faz 10'un en onemli testci
     * maddesi ("her kule hepsinde ise yariyor") kademe 3'te geri gelemez.
     */
    @Test
    fun theCrowdTowerRemainsUselessAgainstArmourAtEveryTier() {
        val crowd = GameConfig.TOWER_SPECS.values.single { it.role == TowerRole.CROWD }
        val antiTank = GameConfig.TOWER_SPECS.values.single { it.role == TowerRole.ANTI_TANK }
        for (tier in 1..crowd.maxTier) {
            val crowdVsTank = effectiveDpsAgainst(crowd.type, tier, EnemyType.TANK)
            val antiTankVsTank = effectiveDpsAgainst(antiTank.type, tier, EnemyType.TANK)
            assertTrue(
                "kademe $tier: Gatling tanka $crowdVsTank DPS, fuze $antiTankVsTank — " +
                    "kursun zirha karsi fuzenin ucte birini bile gecmemeli",
                crowdVsTank * 3f < antiTankVsTank
            )
        }
    }

    @Test
    fun theSupportTowerNeverBecomesADamageDealerAtAnyTier() {
        val support = GameConfig.TOWER_SPECS.values.single { it.role == TowerRole.SUPPORT }
        val others = GameConfig.TOWER_SPECS.values.filter { it.role != TowerRole.SUPPORT }
        for (tier in 1..support.maxTier) {
            others.forEach { o ->
                assertTrue(
                    "kademe $tier: destek DPS'i (${support.tier(tier).dps}) ${o.type} " +
                        "DPS'inin (${o.tier(tier).dps}) %20'sini gecmemeli — hasar " +
                        "vermesi kimligi degil",
                    support.tier(tier).dps <= o.tier(tier).dps * 0.20f
                )
            }
        }
    }

    @Test
    fun theSupportTowerKeepsTheWidestCoverageAtEveryTier() {
        val support = GameConfig.TOWER_SPECS.values.single { it.role == TowerRole.SUPPORT }
        val others = GameConfig.TOWER_SPECS.values.filter { it.role != TowerRole.SUPPORT }
        for (tier in 1..support.maxTier) {
            others.forEach { o ->
                assertTrue(
                    "kademe $tier: destek menzili (${support.tier(tier).range}) " +
                        "${o.type} menzilinden (${o.tier(tier).range}) buyuk olmali",
                    support.tier(tier).range > o.tier(tier).range
                )
            }
        }
    }

    /**
     * Rol alanlari (splash / pierce / yavaslatma / fuze carpmasi) KADEMEDEN
     * BAGIMSIZ. Bu, "kademe 3 kulenin ne yaptigini degil ne kadar iyi
     * yaptigini degistirir" kararinin makineyle dogrulanan hali: alanlar bir
     * gun kademeye tasinirsa bu test insanlarin dikkatini ceker.
     */
    @Test
    fun roleDefiningBehavioursAreDeclaredOncePerTowerNotPerTier() {
        assertEquals(
            "splash YALNIZCA Cannon'da olmali",
            setOf(TowerType.CANNON),
            GameConfig.TOWER_SPECS.values.filter { it.splashRadius > 0f }.map { it.type }.toSet()
        )
        assertEquals(
            "zirh delme YALNIZCA fuzede olmali",
            setOf(TowerType.ANTI_ARMOR),
            GameConfig.TOWER_SPECS.values.filter { it.armorPierce > 0f }.map { it.type }.toSet()
        )
        assertEquals(
            "yavaslatma YALNIZCA destek kulesinde olmali",
            setOf(TowerType.SLOW),
            GameConfig.TOWER_SPECS.values.filter { it.slowFactor > 0f }.map { it.type }.toSet()
        )
    }
}
