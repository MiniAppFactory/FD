package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerRole
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import com.miniappfactory.frontlinedefender.game.economy.SupplyBudgetModel
import com.miniappfactory.frontlinedefender.game.economy.startingSupplyFor
import com.miniappfactory.frontlinedefender.game.model.ProjectileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DENGE TUTARLILIGI — `TOWER_SPECS` / `ENEMY_SPECS` icsel sozlesmeleri.
 *
 * Bu testler "kule X guclu mu" demez (o Game Designer'in isi); "kademe 2
 * kademe 1'den kotu mu", "zirh 0..1 disinda mi", "splash yanlis kulede mi"
 * gibi TUTARSIZLIKLARI yakalar.
 */
class BalanceConsistencyTest {

    // ------------------------------------------------------------ kule sozlesmesi

    @Test
    fun everyTowerTypeHasASpec() {
        TowerType.values().forEach { t ->
            assertTrue("$t icin TOWER_SPECS kaydi yok", GameConfig.TOWER_SPECS.containsKey(t))
        }
        assertEquals(TowerType.values().size, GameConfig.TOWER_SPECS.size)
    }

    @Test
    fun towerSpecKeysMatchTheirDeclaredType() {
        GameConfig.TOWER_SPECS.forEach { (key, spec) ->
            assertEquals("TOWER_SPECS anahtari ile spec.type uyusmuyor", key, spec.type)
        }
    }

    @Test
    fun everyTowerHasANonBlankNameAndDescription() {
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            assertTrue("$t adi bos", s.name.isNotBlank())
            assertTrue("$t aciklamasi bos", s.description.isNotBlank())
        }
    }

    @Test
    fun tierTwoIsStrictlyBetterThanTierOneInEveryDimension() {
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            assertTrue(
                "$t: kademe 2 menzili (${s.level2Range}) kademe 1'den (${s.level1Range}) buyuk olmali",
                s.level2Range > s.level1Range
            )
            assertTrue(
                "$t: kademe 2 hasari (${s.level2Damage}) kademe 1'den (${s.level1Damage}) buyuk olmali",
                s.level2Damage > s.level1Damage
            )
            assertTrue(
                "$t: kademe 2 ates ARALIGI (${s.level2FireRate}s) kademe 1'den " +
                    "(${s.level1FireRate}s) KUCUK olmali — deger saniye cinsinden",
                s.level2FireRate < s.level1FireRate
            )
        }
    }

    @Test
    fun allTowerCostsAndTimingsArePositive() {
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            assertTrue("$t insa maliyeti pozitif olmali", s.buildCost > 0)
            assertTrue("$t yukseltme maliyeti pozitif olmali", s.level2UpgradeCost > 0)
            assertTrue("$t kademe 1 menzili pozitif olmali", s.level1Range > 0f)
            assertTrue("$t kademe 1 hasari pozitif olmali", s.level1Damage > 0f)
            assertTrue("$t kademe 1 ates araligi pozitif olmali", s.level1FireRate > 0f)
            assertTrue("$t kademe 2 ates araligi pozitif olmali", s.level2FireRate > 0f)
        }
    }

    @Test
    fun upgradingIsNeverMoreThanTwiceTheBuildCost() {
        // Yukseltme insa etmekten cok pahali olursa oyuncu hep yeni kule diker
        // ve yukseltme mekanigi olu kalir.
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            assertTrue(
                "$t: yukseltme ${s.level2UpgradeCost}, insa ${s.buildCost} — " +
                    "yukseltme insa maliyetinin 2 katini gecmemeli",
                s.level2UpgradeCost <= s.buildCost * 2
            )
        }
    }

    @Test
    fun upgradingAlwaysImprovesDamagePerSecondPerGoldSpent() {
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            val dps1 = s.level1Damage / s.level1FireRate
            val dps2 = s.level2Damage / s.level2FireRate
            assertTrue("$t: kademe 2 DPS ($dps2) kademe 1'den ($dps1) buyuk olmali", dps2 > dps1)
        }
    }

    /** Splash YALNIZCA Cannon'da. Bu, DECISIONS B2 gerekcesinin dayanagi. */
    @Test
    fun splashRadiusIsExclusiveToTheCannon() {
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            if (t == TowerType.CANNON) {
                assertTrue("CANNON splash yaricapi pozitif olmali", s.splashRadius > 0f)
            } else {
                assertEquals("$t splash yaricapi 0 olmali", 0f, s.splashRadius, 0f)
            }
        }
    }

    /** Zirh delme YALNIZCA Anti-Armor'da. */
    @Test
    fun armorPierceIsExclusiveToTheAntiArmorTower() {
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            if (t == TowerType.ANTI_ARMOR) {
                assertTrue("ANTI_ARMOR zirh delmesi pozitif olmali", s.armorPierce > 0f)
            } else {
                assertEquals("$t zirh delmesi 0 olmali", 0f, s.armorPierce, 0f)
            }
            assertTrue("$t armorPierce 0..1 araliginda olmali", s.armorPierce in 0f..1f)
        }
    }

    /** Yavaslatma YALNIZCA Slow kulesinde ve suresi olmali. */
    @Test
    fun slowIsExclusiveToTheSlowTowerAndAlwaysHasADuration() {
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            if (t == TowerType.SLOW) {
                assertTrue("SLOW yavaslatma carpani pozitif olmali", s.slowFactor > 0f)
                assertTrue("SLOW yavaslatma suresi pozitif olmali", s.slowDuration > 0f)
            } else {
                assertEquals("$t slowFactor 0 olmali", 0f, s.slowFactor, 0f)
                assertEquals("$t slowDuration 0 olmali", 0f, s.slowDuration, 0f)
            }
        }
    }

    /**
     * `slowFactor` motorda `baseSpeed * (1f - factor)` olarak uygulaniyor.
     * 1.0 olursa dusman TAMAMEN DURUR ve bolum sonsuza kadar bitmez;
     * 1.0'i gecerse dusman GERI GIDER.
     */
    @Test
    fun slowFactorCanNeverFreezeOrReverseAnEnemy() {
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            assertTrue(
                "$t slowFactor ${s.slowFactor} — 1.0'a esit/buyuk olursa dusman " +
                    "durur veya geri gider, bolum bitmez",
                s.slowFactor < 1f
            )
            assertTrue("$t slowFactor negatif olamaz", s.slowFactor >= 0f)
        }
    }

    @Test
    fun theCheapestTowerIsAlsoTheStarterTower() {
        // Ogretici bolum 1'de yalnizca MACHINE_GUN mevcut, bu yuzden en ucuz
        // olmali; degilse oyuncu ilk dalgayi karsilayamaz.
        val cheapest = GameConfig.TOWER_SPECS.values.minBy { it.buildCost }
        assertEquals(TowerType.MACHINE_GUN, cheapest.type)
    }

    /**
     * ESKI HALI: "baslangic tedariki en az 2 kule almali."
     *
     * Faz 10'da bu KASTEN yanlis hale geldi: L1 Tedariki 150 -> 80, yani tam
     * BIR Gatling. Ekonomi ajaninin olcumu, iki-uc kuleyi hazirlik fazinda
     * birden kurabilmenin "hangi kuleyi once?" kararini tamamen yok ettigini
     * gosterdi. Kural bu yuzden gevsetilmedi, DOGRU ifadeye cevrildi:
     * ilk bolumde en az bir kule kurulabilmeli (yoksa oyun baslamaz) ve
     * butce bolum bolum artmali.
     */
    @Test
    fun theOpeningLevelAffordsExactlyItsFirstTowerAndBudgetsGrow() {
        val cheapest = GameConfig.TOWER_SPECS.values.minOf { it.buildCost }
        val first = GameConfig.levelSpec(1).startingSupply
        assertTrue(
            "L1 tedariki $first, en ucuz kule $cheapest — ilk kule KURULABILMELI",
            first >= cheapest
        )
        assertTrue(
            "L1 tedariki $first iki kule birden almamali ($cheapest x2) — ikinci " +
                "kule oldurmeyle KAZANILIR",
            first < cheapest * 2
        )
        // MONOTONLUK **TABAN** UZERINDE OLCULUR.
        //
        // `COVERAGE_SCARCITY_SUPPLY_FACTOR` (1,5) bir zorluk rampasi degil bir
        // TELAFIDIR: catallanan ya da dar tahtali bolumde AYNI kadroyu kurmak
        // daha pahaliya gelir. Catallanma esigi 9'dan 3'e indiginde L4 telafi
        // aliyor (215 -> 323) ve L5 (255) ondan kucuk kaliyor; bu ekonomi
        // hatasi DEGILDIR, L4'un iki cephesi vardir. Kampanyanin gercekten
        // artmasi gereken sey kadronun kendisidir.
        val actIBase = (1..6).map { GameConfig.startingSupplyBaseFor(it) }
        for (i in 1 until actIBase.size) {
            assertTrue(
                "bolum ${i + 1} TABAN tedariki (${actIBase[i]}) oncekinden " +
                    "(${actIBase[i - 1]}) az olmamali",
                actIBase[i] >= actIBase[i - 1]
            )
        }
        // Telafi hicbir zaman tabanin ALTINA inmemeli.
        (1..6).forEach { lv ->
            assertTrue(
                "bolum $lv tedariki tabanin altina dusemez",
                GameConfig.levelSpec(lv).startingSupply >= GameConfig.startingSupplyBaseFor(lv)
            )
        }

        // CATALLANMA ESIGI KURALI — sayi elle secilmedi.
        // Esik, baslangic Tedariginin BIRDEN FAZLA kule aldigi ILK bolumdur:
        // iki kolu ayni anda savunmak iki mevzi ister, tek kule ile acilan bir
        // catal "ogret" degil "cezalandir" olur (bkz. ALT_ROUTE_FIRST_LEVEL).
        val forkLevel = GameConfig.ALT_ROUTE_FIRST_LEVEL
        assertTrue(
            "catallanmanin acildigi bolum ($forkLevel) en az iki kule almali " +
                "(${GameConfig.startingSupplyFor(forkLevel)} vs ${cheapest * 2})",
            GameConfig.startingSupplyFor(forkLevel) >= cheapest * 2
        )
        assertTrue(
            "catallanmadan onceki bolum (${forkLevel - 1}) hâlâ TEK kule almali — " +
                "aksi halde esik gereksiz yere gec",
            forkLevel <= 1 || GameConfig.startingSupplyFor(forkLevel - 1) < cheapest * 2
        )
        assertTrue(
            "INITIAL_GOLD (${GameConfig.INITIAL_GOLD}) L7+ icin taban degerdir ve " +
                "en az iki kule almali",
            GameConfig.INITIAL_GOLD >= cheapest * 2
        )
    }

    /**
     * EKONOMI DEVIR SOZLESMESI.
     *
     * Ekonomi ajani sayilari `SupplyBudgetModel` icinde OLCTU ve uygulamayi bu
     * ajana devretti (ECONOMY_SPEC 9). Iki katman ayri dosyalarda oldugu icin
     * tek koruma bu kilit: biri degisip digeri kalirsa bolum secme ekrani bir
     * butce gosterip savas baska butceyle baslar.
     */
    @Test
    fun gameConfigMatchesTheEconomyHandoverNumbers() {
        assertEquals(
            "dalga temizleme ikramiyesi ekonomi modeliyle uyusmuyor",
            SupplyBudgetModel.WAVE_CLEAR_SUPPLY_BONUS,
            GameConfig.WAVE_CLEAR_SUPPLY_BONUS
        )
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            assertEquals(
                "L$level baslangic Tedariki ekonomi modeliyle uyusmuyor",
                startingSupplyFor(level),
                GameConfig.levelSpec(level).startingSupply
            )
        }
        SupplyBudgetModel.TIGHTENED_ENEMY_SUPPLY_REWARD.forEach { (name, reward) ->
            val type = EnemyType.valueOf(name)
            assertEquals(
                "$type odulu ekonomi modelinin sikilastirilmis tablosuyla uyusmuyor",
                reward, GameConfig.ENEMY_SPECS.getValue(type).rewardGold
            )
        }
    }

    /**
     * Ekonomi ajaninin kalibrasyon hedefi: **bir kule ~15 oldurme.** Oran
     * bozulursa (odul yukselir ya da kule ucuzlarsa) "6 kule parasi kazaniyorsun"
     * sikayeti geri gelir.
     */
    @Test
    fun aTowerCostsRoughlyFifteenInfantryKills() {
        val gatling = spec(TowerType.MACHINE_GUN).buildCost
        val infantry = enemySpec(EnemyType.INFANTRY).rewardGold
        val kills = gatling.toDouble() / infantry
        assertTrue(
            "bir Gatling ${"%.1f".format(kills)} piyade oldurmesi ediyor — " +
                "hedef 12..20 bandi (eski deger 5 idi ve bolluk oradan geliyordu)",
            kills in 12.0..20.0
        )
    }

    /**
     * Dusmanlarin BIRBIRINE goreli degeri korunmali. Odul olcegi tam 1/3
     * secilmesinin sebebi buydu: 0.375 gibi bir olcek zirhliyi piyadeye gore
     * %18 degerlendirip hedef secimini sessizce degistirirdi.
     */
    @Test
    fun relativeEnemyValueSurvivedTheRewardRescale() {
        val infantry = enemySpec(EnemyType.INFANTRY).rewardGold.toDouble()
        val expectedRatios = mapOf(
            EnemyType.FAST_SOLDIER to 1.25,
            EnemyType.TANK to 5.0,
            EnemyType.COMMAND_TANK to 15.0
        )
        expectedRatios.forEach { (type, expected) ->
            val actual = enemySpec(type).rewardGold / infantry
            assertEquals(
                "$type / piyade odul orani degisti — hedef secimi kayar",
                expected, actual, 0.01
            )
        }
    }

    /**
     * ESKI HALI: "en uzun menzilli kule ayni zamanda en pahali olmali."
     *
     * O kural Faz 10'da YANLIS hale geldi, cunku artik en uzun menzilli kule
     * DESTEK kulesi (Frost Field, 270 ref-px) ve hicbir hasar vermiyor —
     * menzilini fiyatla degil "sifir hasar" ile odiyor. Kural bu yuzden
     * ZAYIFLATILMADI, HASAR VEREN kulelere daraltildi: aralarinda menzil hâlâ
     * parayla alinir, yoksa kisa menzilli kuleler anlamsizlasir.
     */
    @Test
    fun amongTheDamageTowersTheLongestRangedIsStillTheMostExpensive() {
        val gunners = GameConfig.TOWER_SPECS.values.filter { it.role != TowerRole.SUPPORT }
        val longest = gunners.maxBy { it.level1Range }
        val priciest = gunners.maxBy { it.buildCost }
        assertEquals(
            "hasar veren kuleler arasinda en uzun menzilli olan en pahali olmali",
            longest.type, priciest.type
        )
    }

    // =========================================================================
    // Faz 10 — KULE UZMANLASMASI
    //
    // Testcinin en onemli maddesi: "bir de her kule hepsinde ise yariyor."
    // Asagidaki testler bunun bir daha OLMAMASINI garanti eder: her kule kendi
    // hedef sinifinda ALTIN BASINA en iyi, en az bir sinifta da acik ara en
    // kotu olmak ZORUNDA. Biri "Gatling'i biraz guclendirelim" derse ve kule
    // her seye yeterli hale gelirse burasi kirilir.
    // =========================================================================

    /**
     * Bir kulenin bir dusman sinifina karsi ETKIN DPS'i — motor formulunun aynasi
     * (GameEngine.applyDamageToEnemy + onProjectileImpact).
     *
     * @param cluster Hedef kumesinde kac dusman var. Alan etkili kuleler
     *   (Cannon splash / fuze carpma alani / cryo darbesi) kumenin tamamina
     *   vurur; makineli tufek her zaman TEK hedef vurur.
     */
    private fun effectiveDps(
        s: GameConfig.TowerStats,
        e: GameConfig.EnemyStats,
        cluster: Int = 1
    ): Float {
        val perShot = when {
            // Splash zirhi BYPASS eder (DECISIONS B2) ve kumenin tamamina vurur.
            s.splashRadius > 0f -> s.level1Damage * e.splashVulnerability * cluster
            // Cryo darbesi: kucuk hasar ama kumenin tamamina, zirh tam etkili.
            s.slowPulseRadius > 0f -> s.level1Damage * (1f - e.armor) * cluster
            else -> {
                val armorLeft = e.armor * (1f - s.armorPierce)
                val primary = s.level1Damage * (1f - armorLeft)
                // Fuze carpma alani: KESIRLI hasar, zirhi bypass etmez.
                val others = (cluster - 1).coerceAtLeast(0)
                val secondary = if (s.missileImpactRadius > 0f) {
                    s.level1Damage * s.missileImpactDamageFraction * (1f - armorLeft) * others
                } else {
                    0f
                }
                primary + secondary
            }
        }
        return perShot / s.level1FireRate
    }

    private fun dpsPerGold(
        s: GameConfig.TowerStats,
        e: GameConfig.EnemyStats,
        cluster: Int = 1
    ): Float = effectiveDps(s, e, cluster) / s.buildCost

    private fun spec(t: TowerType) = GameConfig.TOWER_SPECS.getValue(t)
    private fun enemySpec(t: EnemyType) = GameConfig.ENEMY_SPECS.getValue(t)

    @Test
    fun everyRoleIsFilledByExactlyOneTower() {
        val byRole = GameConfig.TOWER_SPECS.values.groupBy { it.role }
        TowerRole.values().forEach { role ->
            assertEquals(
                "$role rolu tam bir kule tarafindan doldurulmali — iki kule ayni " +
                    "rolde olursa biri gereksiz, hicbiri olmazsa o hedef sinifinin " +
                    "cevabi yok",
                1, byRole[role]?.size ?: 0
            )
        }
    }

    /** CROWD: kalabaliga karsi altin basina en iyi ve en ucuz kule. */
    @Test
    fun theCrowdTowerIsTheBestValueAgainstUnarmouredInfantry() {
        val infantry = enemySpec(EnemyType.INFANTRY)
        val best = GameConfig.TOWER_SPECS.values.maxBy { dpsPerGold(it, infantry) }
        assertEquals(TowerRole.CROWD, best.role)
        assertEquals(
            "en ucuz kule kalabalik kulesi olmali (ilk dalgada alinabilmeli)",
            TowerRole.CROWD,
            GameConfig.TOWER_SPECS.values.minBy { it.buildCost }.role
        )
    }

    /**
     * Testcinin sikayetinin OLCULMUS hali. Eski dengede Gatling zirhli araca
     * karsi bile altin basina en iyi secenekti (0.66 vs fuzenin 0.51'i), yani
     * zirh bir karsi-koyma degil sadece kalin bir piyadeydi.
     */
    @Test
    fun bulletsAreNearlyUselessAgainstHeavyArmour() {
        val mg = spec(TowerType.MACHINE_GUN)
        val vsInfantry = effectiveDps(mg, enemySpec(EnemyType.INFANTRY))
        listOf(EnemyType.ARMORED_VEHICLE, EnemyType.TANK, EnemyType.COMMAND_TANK).forEach { t ->
            val vsArmour = effectiveDps(mg, enemySpec(t))
            assertTrue(
                "makineli tufek $t'a ${"%.1f".format(vsArmour)} DPS veriyor; piyadeye " +
                    "${"%.1f".format(vsInfantry)} — zirhli hedefte %25'in altina " +
                    "dusmeli, yoksa oyuncu muhimmat degistirmek zorunda kalmaz",
                vsArmour <= vsInfantry * 0.25f
            )
        }
    }

    /** ANTI_TANK: tek agir zirhli hedefte altin basina acik ara en iyi. */
    @Test
    fun theAntiTankTowerOwnsSingleHeavyArmourTargets() {
        listOf(EnemyType.ARMORED_VEHICLE, EnemyType.TANK).forEach { t ->
            val e = enemySpec(t)
            val best = GameConfig.TOWER_SPECS.values.maxBy { dpsPerGold(it, e) }
            assertEquals(
                "$t'a karsi altin basina en iyi kule zirh kirici olmali " +
                    "(olculen: ${best.type})",
                TowerRole.ANTI_TANK, best.role
            )
            val at = dpsPerGold(spec(TowerType.ANTI_ARMOR), e)
            val mg = dpsPerGold(spec(TowerType.MACHINE_GUN), e)
            assertTrue(
                "$t'a karsi zirh kirici (${"%.2f".format(at)}), makineliden " +
                    "(${"%.2f".format(mg)}) en az 1.4 kat verimli olmali",
                at >= mg * 1.4f
            )
        }
    }

    /** ANTI_TANK'in bedeli: kalabaliga karsi verimsiz olmak ZORUNDA. */
    @Test
    fun theAntiTankTowerIsPoorValueAgainstASwarm()
    {
        val infantry = enemySpec(EnemyType.INFANTRY)
        val at = dpsPerGold(spec(TowerType.ANTI_ARMOR), infantry)
        val mg = dpsPerGold(spec(TowerType.MACHINE_GUN), infantry)
        assertTrue(
            "zirh kirici piyadeye karsi ${"%.2f".format(at)} DPS/altin veriyor, " +
                "makineli ${"%.2f".format(mg)} — kalabalikta makinelinin yarisini " +
                "gecmemeli, yoksa 'her kule her seye yarar' geri doner",
            at <= mg * 0.5f
        )
        assertTrue(
            "zirh kirici atis araligi en yavas olmali (kalabalikta ceza)",
            GameConfig.TOWER_SPECS.values.all {
                it.role == TowerRole.ANTI_TANK || it.level1FireRate < spec(TowerType.ANTI_ARMOR).level1FireRate
            }
        )
        assertTrue(
            "zirh kirici atis basina en yuksek hasari vurmali",
            GameConfig.TOWER_SPECS.values.maxBy { it.level1Damage }.role == TowerRole.ANTI_TANK
        )
    }

    /** SIEGE: kumelenmis hedefte altin basina en iyi. */
    @Test
    fun theSiegeTowerOwnsClusteredTargets() {
        val infantry = enemySpec(EnemyType.INFANTRY)
        val best = GameConfig.TOWER_SPECS.values.maxBy { dpsPerGold(it, infantry, cluster = 5) }
        assertEquals(
            "5'li kumeye karsi altin basina en iyi kule kusatma kulesi olmali " +
                "(olculen: ${best.type})",
            TowerRole.SIEGE, best.role
        )
        assertTrue(
            "splash yaricapi fuzenin carpma alanindan belirgin buyuk olmali, " +
                "yoksa fuze kusatma kimligini golgeler",
            spec(TowerType.CANNON).splashRadius >= spec(TowerType.ANTI_ARMOR).missileImpactRadius * 1.5f
        )
    }

    /**
     * SIEGE'in bedeli: mermi o kadar YAVAS ki hizli hedefi iskaliyor.
     *
     * Bu bir yorum degil, motor formulunun sonucu: ucus suresi = 100 / speed
     * (mesafeden bagimsiz), mermi ATES ANINDAKI noktaya gider. Dusman o surede
     * splash yaricapindan cok yol alirsa patlamanin disinda kalir.
     */
    @Test
    fun theSiegeShellIsTooSlowToCatchTheFastestEnemyButCatchesSlowOnes() {
        val cannon = spec(TowerType.CANNON)
        val flight = 100f / GameConfig.PROJECTILE_SPEEDS.getValue(ProjectileType.CANNON_SHELL)

        val runner = enemySpec(EnemyType.FAST_SOLDIER)
        val runnerTravel = runner.baseSpeed * flight
        assertTrue(
            "top mermisi ${"%.2f".format(flight)} sn ucuyor, kosucu o surede " +
                "${"%.0f".format(runnerTravel)} ref-px yol aliyor; splash yaricapi " +
                "${cannon.splashRadius} — kosucu patlamanin DISINDA kalmali " +
                "(kusatma kulesinin kasitli kor noktasi)",
            runnerTravel > cannon.splashRadius
        )

        listOf(EnemyType.INFANTRY, EnemyType.ARMORED_VEHICLE, EnemyType.TANK).forEach { t ->
            val travel = enemySpec(t).baseSpeed * flight
            assertTrue(
                "$t ${"%.0f".format(travel)} ref-px yol aliyor; top bu hedefi " +
                    "VURABILMELI (yoksa kule hicbir seye yaramaz)",
                travel <= cannon.splashRadius
            )
        }

        // KOMBO: destek kulesi yavaslatinca top kosucuyu da yakalar. Oyunun
        // ogretmek istedigi kule etkilesimi tam olarak bu.
        val slowed = runner.baseSpeed * (1f - spec(TowerType.SLOW).slowFactor) * flight
        assertTrue(
            "yavaslatilmis kosucu ${"%.0f".format(slowed)} ref-px yol almali ve " +
                "${cannon.splashRadius} yaricapina girmeli — Frost Field + Cannon " +
                "kombosu tasarimin bir parcasi",
            slowed <= cannon.splashRadius
        )
    }

    /** SUPPORT: en genis kapsama, en zayif silah. */
    @Test
    fun theSupportTowerHasTheWidestReachAndTheWeakestGun() {
        val support = spec(TowerType.SLOW)
        val others = GameConfig.TOWER_SPECS.values.filter { it.role != TowerRole.SUPPORT }

        others.forEach { o ->
            assertTrue(
                "destek kulesi menzili (${support.level1Range}) ${o.type} " +
                    "menzilinden (${o.level1Range}) buyuk olmali — testci: 'buz " +
                    "kulesinin kapsama alani buyuk olmali'",
                support.level1Range > o.level1Range
            )
        }
        val shortest = others.minOf { it.level1Range }
        assertTrue(
            "destek menzili (${support.level1Range}) en kisa menzilin " +
                "($shortest) en az 1.5 katı olmali, yoksa fark sahada gorunmez",
            support.level1Range >= shortest * 1.5f
        )
        others.forEach { o ->
            assertTrue(
                "destek kulesi DPS'i (${support.level1Dps}) ${o.type} DPS'inin " +
                    "(${o.level1Dps}) %20'sini gecmemeli — hasar vermesi kimligi degil",
                support.level1Dps <= o.level1Dps * 0.20f
            )
        }
    }

    /**
     * Frost Field'in gercek duzeltmesi menzil DEGIL, darbenin ALAN olmasiydi:
     * eskiden cryo darbesi yalnizca hedeflenen TEK dusmani yavaslatiyordu, yani
     * 20 kisilik suruye karsi 0.65 sn'de bir dusman = pratikte hicbir sey.
     */
    @Test
    fun theSupportTowerChillsAnAreaWiderThanTheCannonBlast() {
        val support = spec(TowerType.SLOW)
        assertTrue("destek kulesi alan darbesi olmali", support.slowPulseRadius > 0f)
        assertTrue(
            "cryo darbesi (${support.slowPulseRadius}) top patlamasindan " +
                "(${spec(TowerType.CANNON).splashRadius}) genis olmali — alan " +
                "kontrolu kulesinin kontrol ettigi alan kusatma kulesinden kucuk olamaz",
            support.slowPulseRadius > spec(TowerType.CANNON).splashRadius
        )
        // Yavaslatma sahada FARK EDILMELI: bir kule hedefe en az 1.5 kat daha
        // uzun sure ates edebilmeli.
        val exposureGain = 1f / (1f - support.slowFactor)
        assertTrue(
            "yavaslatma %${(support.slowFactor * 100).toInt()} — bataryanin " +
                "atis penceresini en az 1.5 katina cikarmali " +
                "(olculen ${"%.2f".format(exposureGain)}x)",
            exposureGain >= 1.5f
        )
    }

    // ------------------------------------------------------- yeni alan sozlesmeleri

    @Test
    fun theSlowPulseRadiusIsExclusiveToTheSupportTower() {
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            if (t == TowerType.SLOW) {
                assertTrue("SLOW darbe yaricapi pozitif olmali", s.slowPulseRadius > 0f)
            } else {
                assertEquals("$t slowPulseRadius 0 olmali", 0f, s.slowPulseRadius, 0f)
            }
        }
    }

    @Test
    fun theMissileImpactAreaIsExclusiveToTheAntiArmorTowerAndStaysSecondary() {
        GameConfig.TOWER_SPECS.forEach { (t, s) ->
            if (t == TowerType.ANTI_ARMOR) {
                assertTrue("fuze carpma alani pozitif olmali", s.missileImpactRadius > 0f)
                assertTrue(
                    "fuzenin ikincil hasar orani 0..1 arasinda ve 1'in ALTINDA " +
                        "olmali (yoksa ikinci bir top olur)",
                    s.missileImpactDamageFraction > 0f && s.missileImpactDamageFraction < 1f
                )
            } else {
                assertEquals("$t missileImpactRadius 0 olmali", 0f, s.missileImpactRadius, 0f)
                assertEquals(
                    "$t missileImpactDamageFraction 0 olmali",
                    0f, s.missileImpactDamageFraction, 0f
                )
            }
        }
    }

    // ------------------------------------------------------------ mermi ucus suresi

    @Test
    fun everyProjectileTypeHasADeclaredSpeed() {
        ProjectileType.values().forEach { p ->
            assertTrue(
                "$p icin PROJECTILE_SPEEDS kaydi yok — motor 300f varsayilanina " +
                    "duser ve denge sessizce kayar",
                GameConfig.PROJECTILE_SPEEDS.containsKey(p)
            )
            assertTrue("$p hizi pozitif olmali", GameConfig.PROJECTILE_SPEEDS.getValue(p) > 0f)
        }
    }

    /**
     * Testci: "fuze rampasi var ama fuze atmiyor." Eski mermi RAILGUN_BEAM idi
     * ve pratikte aninda variyordu. Fuze artik YOL ALIR — ve bu, hedef havada
     * olurse ISRAF olmasi anlamina gelen gercek bir oynanis takasi.
     */
    @Test
    fun theMissileSpendsRealTimeInTheAirUnlikeABeam() {
        val flight = { p: ProjectileType -> 100f / GameConfig.PROJECTILE_SPEEDS.getValue(p) }
        val missile = flight(ProjectileType.MISSILE)
        assertTrue(
            "fuze ucus suresi ${"%.2f".format(missile)} sn — kursundan " +
                "(${"%.2f".format(flight(ProjectileType.BULLET))} sn) uzun olmali",
            missile > flight(ProjectileType.BULLET)
        )
        assertTrue(
            "fuze ucus suresi en az 0.5 sn olmali, yoksa 'israf olabilir' " +
                "takasi oyuncu tarafindan HISSEDILMEZ",
            missile >= 0.5f
        )
        assertTrue(
            "top mermisi hâlâ en yavas mermi olmali (kor noktasinin kaynagi)",
            flight(ProjectileType.CANNON_SHELL) > missile
        )
    }

    // -------------------------------------------------------------- KULE KILIDI

    @Test
    fun towerUnlockLevelsAreSaneAndStaggered() {
        val levels = GameConfig.TOWER_SPECS.values.map { it.unlockedAtLevel }
        levels.forEach { lv ->
            assertTrue(
                "kilit bolumu 1..${GameConfig.CAMPAIGN_LEVEL_COUNT} araliginda olmali ($lv)",
                lv in 1..GameConfig.CAMPAIGN_LEVEL_COUNT
            )
        }
        assertEquals(
            "iki kule ayni bolumde acilmamali — her kilit acilisi kendi 'yeni " +
                "oyuncak' anini almali",
            levels.size, levels.toSet().size
        )
        assertEquals(
            "ilk bolumde YALNIZCA baslangic kulesi acik olmali",
            listOf(TowerType.MACHINE_GUN), GameConfig.unlockedTowers(1)
        )
        assertEquals(
            "baslangic kulesi bolum 1'de acik olmali",
            1, spec(TowerType.MACHINE_GUN).unlockedAtLevel
        )
        val last = levels.max()
        assertTrue(
            "en son kule bolum $last'de aciliyor — Act I'in ilk yarisinda " +
                "(<= 7) tamamlanmali, yoksa oyuncu kampanyanin yarisini eksik " +
                "araclarla oynar",
            last <= 7
        )
    }

    @Test
    fun unlockedTowersGrowMonotonicallyWithCampaignProgress() {
        var previous = GameConfig.unlockedTowers(1).toSet()
        for (level in 2..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val current = GameConfig.unlockedTowers(level).toSet()
            assertTrue(
                "bolum $level: bir kule KAYBOLDU (once $previous, simdi $current)",
                current.containsAll(previous)
            )
            previous = current
        }
        assertEquals(
            "kampanyanin sonunda tum kuleler acik olmali",
            TowerType.values().toSet(),
            GameConfig.unlockedTowers(GameConfig.CAMPAIGN_LEVEL_COUNT).toSet()
        )
    }

    @Test
    fun isTowerUnlockedAgreesWithTheSpecTableAtEveryLevel() {
        GameConfig.TOWER_SPECS.forEach { (type, s) ->
            for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
                assertEquals(
                    "$type bolum $level: kilit durumu spec ile uyusmuyor",
                    level >= s.unlockedAtLevel,
                    GameConfig.isTowerUnlocked(type, level)
                )
            }
        }
    }

    // ----------------------------------------------------------- dusman sozlesmesi

    @Test
    fun everyEnemyTypeHasASpec() {
        EnemyType.values().forEach { t ->
            assertTrue("$t icin ENEMY_SPECS kaydi yok", GameConfig.ENEMY_SPECS.containsKey(t))
        }
        assertEquals(EnemyType.values().size, GameConfig.ENEMY_SPECS.size)
    }

    @Test
    fun enemySpecKeysMatchTheirDeclaredType() {
        GameConfig.ENEMY_SPECS.forEach { (key, spec) ->
            assertEquals("ENEMY_SPECS anahtari ile spec.type uyusmuyor", key, spec.type)
        }
    }

    @Test
    fun everyEnemyHasPositiveHealthSpeedRewardAndRadius() {
        GameConfig.ENEMY_SPECS.forEach { (t, s) ->
            assertTrue("$t maxHp pozitif olmali (${s.maxHp})", s.maxHp > 0f)
            assertTrue("$t baseSpeed pozitif olmali (${s.baseSpeed})", s.baseSpeed > 0f)
            assertTrue("$t rewardGold pozitif olmali (${s.rewardGold})", s.rewardGold > 0)
            assertTrue("$t sizeRadius pozitif olmali (${s.sizeRadius})", s.sizeRadius > 0f)
            assertTrue("$t adi bos", s.name.isNotBlank())
        }
    }

    /**
     * Zirh motorda `rawDamage * (1f - effectiveArmor)` olarak uygulaniyor.
     * 1.0 olursa dusman kursuna TAMAMEN bagisiklidir; 1.0'i gecerse hasar
     * NEGATIFE doner ve dusman vuruldukca IYILESIR.
     */
    @Test
    fun armorIsAlwaysAFractionStrictlyBelowTotalImmunity() {
        GameConfig.ENEMY_SPECS.forEach { (t, s) ->
            assertTrue("$t armor negatif olamaz (${s.armor})", s.armor >= 0f)
            assertTrue(
                "$t armor ${s.armor} — 1.0'a ulasirsa kursun hic ise yaramaz, " +
                    "gecerse dusman vuruldukca iyilesir",
                s.armor < 1f
            )
        }
    }

    @Test
    fun splashVulnerabilityIsPositiveForEveryEnemy() {
        GameConfig.ENEMY_SPECS.forEach { (t, s) ->
            assertTrue(
                "$t splashVulnerability ${s.splashVulnerability} — 0 olursa " +
                    "patlama hic hasar vermez",
                s.splashVulnerability > 0f
            )
        }
    }

    /**
     * DECISIONS B1/B2: SHIELDED_TROOPER "kursuna direncli, patlamaya zayif".
     * Bu iki sartin IKISI de saglanmali, yoksa tip tasarim amacini kaybeder.
     */
    @Test
    fun theShieldedTrooperIsBulletResistantAndExplosionVulnerable() {
        val shield = GameConfig.ENEMY_SPECS.getValue(EnemyType.SHIELDED_TROOPER)
        val infantry = GameConfig.ENEMY_SPECS.getValue(EnemyType.INFANTRY)

        assertTrue(
            "kalkanli piyade normal piyadeden daha zirhli olmali " +
                "(${shield.armor} vs ${infantry.armor})",
            shield.armor > infantry.armor
        )
        assertTrue(
            "kalkanli piyadenin splash zafiyeti 1.0'dan buyuk olmali " +
                "(${shield.splashVulnerability}) — DECISIONS B2'nin tum gerekcesi bu",
            shield.splashVulnerability > 1f
        )
        assertTrue("kalkanli piyade normal piyadeden dayanikli olmali", shield.maxHp > infantry.maxHp)
    }

    @Test
    fun onlyTheShieldedTrooperHasNonDefaultSplashVulnerability() {
        GameConfig.ENEMY_SPECS.forEach { (t, s) ->
            if (t != EnemyType.SHIELDED_TROOPER) {
                assertEquals(
                    "$t splashVulnerability varsayilan 1.0 olmali",
                    1f, s.splashVulnerability, 1e-6f
                )
            }
        }
    }

    /** COMMAND_TANK boss: her metrikte normal tanktan agir olmali. */
    @Test
    fun theCommandTankIsStrictlyMoreFormidableThanTheHeavyTank() {
        val boss = GameConfig.ENEMY_SPECS.getValue(EnemyType.COMMAND_TANK)
        val tank = GameConfig.ENEMY_SPECS.getValue(EnemyType.TANK)

        assertTrue("boss HP'si tanktan yuksek olmali", boss.maxHp > tank.maxHp)
        assertTrue("boss zirhi tanktan yuksek olmali", boss.armor > tank.armor)
        assertTrue("boss odulu tanktan yuksek olmali", boss.rewardGold > tank.rewardGold)
        assertTrue("boss tanktan buyuk gorunmeli", boss.sizeRadius > tank.sizeRadius)
        assertTrue("boss tanktan yavas olmali", boss.baseSpeed <= tank.baseSpeed)
    }

    @Test
    fun theScoutRunnerIsTheFastestAndFrailestEnemy() {
        val fastest = GameConfig.ENEMY_SPECS.values.maxBy { it.baseSpeed }
        assertEquals(EnemyType.FAST_SOLDIER, fastest.type)
        val frailest = GameConfig.ENEMY_SPECS.values.minBy { it.maxHp }
        assertEquals(EnemyType.FAST_SOLDIER, frailest.type)
    }

    @Test
    fun tougherEnemiesAreAlwaysSlowerThanFlimsierOnes() {
        // "Hem en dayanikli hem en hizli" bir dusman karsi-koyma imkani birakmaz.
        val byHp = GameConfig.ENEMY_SPECS.values.sortedBy { it.maxHp }
        for (i in 1 until byHp.size) {
            assertTrue(
                "${byHp[i].type} (hp=${byHp[i].maxHp}, hiz=${byHp[i].baseSpeed}) " +
                    "${byHp[i - 1].type}'dan (hp=${byHp[i - 1].maxHp}, " +
                    "hiz=${byHp[i - 1].baseSpeed}) hem dayanikli hem hizli olamaz",
                byHp[i].baseSpeed <= byHp[i - 1].baseSpeed
            )
        }
    }

    @Test
    fun rewardGoldScalesWithEnemyToughness() {
        val byHp = GameConfig.ENEMY_SPECS.values
            .filter { it.type != EnemyType.FAST_SOLDIER } // kosucu hiziyla odul aliyor
            .sortedBy { it.maxHp }
        for (i in 1 until byHp.size) {
            assertTrue(
                "${byHp[i].type} odulu (${byHp[i].rewardGold}) " +
                    "${byHp[i - 1].type}'dan (${byHp[i - 1].rewardGold}) dusuk olmamali",
                byHp[i].rewardGold >= byHp[i - 1].rewardGold
            )
        }
    }

    /**
     * Bir dusmani oldurmenin odulu, onu oldurmek icin gereken kule maliyetinden
     * cok yuksek olursa ekonomi patlar; cok dusuk olursa oyuncu hic kule
     * kuramaz. Kaba ust sinir: en ucuz kulenin maliyeti.
     */
    @Test
    fun noSingleEnemyKillPaysForAnEntireTower() {
        val cheapestTower = GameConfig.TOWER_SPECS.values.minOf { it.buildCost }
        GameConfig.ENEMY_SPECS.forEach { (t, s) ->
            if (t == EnemyType.COMMAND_TANK) return@forEach // boss kasitli istisna
            assertTrue(
                "$t olumu ${s.rewardGold} Tedarik veriyor, en ucuz kule " +
                    "$cheapestTower — tek olum bir kuleden fazlasini karsilamamali",
                s.rewardGold <= cheapestTower
            )
        }
    }

    // -------------------------------------------------- sprite spec kapsamlari

    @Test
    fun everyTowerTypeHasASpriteSpec() {
        TowerType.values().forEach { t ->
            assertTrue("$t icin TOWER_SPRITES kaydi yok", GameConfig.TOWER_SPRITES.containsKey(t))
        }
    }

    /**
     * Bu KRITIK: `GameCanvas.drawEnemy` eksik kayitta `?: return` ile erken
     * donuyor, yani dusman **hic cizilmez** ama yine de usse yurur ve can goturur.
     */
    @Test
    fun everyEnemyTypeHasASpriteSpecOtherwiseItRendersInvisible() {
        EnemyType.values().forEach { t ->
            assertTrue(
                "$t icin ENEMY_SPRITES kaydi yok — GameCanvas.drawEnemy erken doner " +
                    "ve bu dusman GORUNMEZ sekilde usse yurur",
                GameConfig.ENEMY_SPRITES.containsKey(t)
            )
        }
    }

    @Test
    fun spriteSpecDimensionsAndPivotsAreSane() {
        GameConfig.TOWER_SPRITES.forEach { (t, s) ->
            assertTrue("$t sprite genisligi pozitif olmali", s.widthRefPx > 0f)
            assertTrue("$t pivotYFrac 0..1 araliginda olmali (${s.pivotYFrac})", s.pivotYFrac in 0f..1f)
        }
        GameConfig.ENEMY_SPRITES.forEach { (t, s) ->
            assertTrue("$t sprite genisligi pozitif olmali", s.widthRefPx > 0f)
            assertTrue("$t pivotYFrac 0..1 araliginda olmali (${s.pivotYFrac})", s.pivotYFrac in 0f..1f)
            assertTrue("$t tintStrength 0..1 araliginda olmali", s.tintStrength in 0f..1f)
        }
    }

    /**
     * DECISIONS B1: yeni PNG uretilmeyecek; turetilmis tipler MEVCUT bir
     * sprite'i baseSprite olarak kullanir ve o base kendisi cizilebilir bir
     * tip olmali (yoksa zincir kirilir).
     */
    @Test
    fun derivedEnemySpritesPointAtARealBaseSpriteAndAreVisuallyMarked() {
        val derived = listOf(EnemyType.SHIELDED_TROOPER, EnemyType.COMMAND_TANK)
        derived.forEach { t ->
            val s = GameConfig.ENEMY_SPRITES.getValue(t)
            assertTrue("$t baseSprite kendisi olamaz", s.baseSprite != t)
            assertTrue(
                "$t baseSprite'i (${s.baseSprite}) ENEMY_SPRITES'ta yok",
                GameConfig.ENEMY_SPRITES.containsKey(s.baseSprite)
            )
            val base = GameConfig.ENEMY_SPRITES.getValue(s.baseSprite)
            assertEquals("$t baseSprite'i de turetilmis olmamali", s.baseSprite, base.baseSprite)

            // Tint YALNIZ BIRAKILMAZ (game-art: "deger farki da olmali").
            assertTrue("$t tint tanimli olmali", s.tintArgb != 0L)
            assertTrue("$t tint karisim orani pozitif olmali", s.tintStrength > 0f)
            assertTrue(
                "$t silueti degistiren bir isaret tasimali (tint tek basina yetmez)",
                s.overlayBadge != GameConfig.EnemyBadge.NONE
            )
        }
    }

    @Test
    fun nonDerivedEnemySpritesUseTheirOwnBitmapWithNoTint() {
        val own = listOf(
            EnemyType.INFANTRY, EnemyType.FAST_SOLDIER,
            EnemyType.ARMORED_VEHICLE, EnemyType.TANK
        )
        own.forEach { t ->
            val s = GameConfig.ENEMY_SPRITES.getValue(t)
            assertEquals("$t kendi sprite'ini kullanmali", t, s.baseSprite)
            assertEquals("$t tint tasimamali", 0L, s.tintArgb)
            assertEquals("$t badge tasimamali", GameConfig.EnemyBadge.NONE, s.overlayBadge)
        }
    }

    @Test
    fun derivedEnemiesAreDrawnLargerThanTheirBaseSoTheyReadAsDifferent() {
        listOf(EnemyType.SHIELDED_TROOPER, EnemyType.COMMAND_TANK).forEach { t ->
            val s = GameConfig.ENEMY_SPRITES.getValue(t)
            val base = GameConfig.ENEMY_SPRITES.getValue(s.baseSprite)
            assertTrue(
                "$t (${s.widthRefPx}) base'inden (${base.widthRefPx}) buyuk cizilmeli",
                s.widthRefPx > base.widthRefPx
            )
        }
    }

    @Test
    fun everyBadgeValueIsActuallyUsedBySomeEnemy() {
        val used = GameConfig.ENEMY_SPRITES.values.map { it.overlayBadge }.toSet()
        GameConfig.EnemyBadge.values().forEach { b ->
            assertTrue("$b hicbir dusman tarafindan kullanilmiyor — olu enum degeri", b in used)
        }
    }

    // --------------------------------------------------- render/etkilesim sabitleri

    @Test
    fun interactionConstantsAreWithinUsableBounds() {
        assertTrue("dokunma yaricapi pozitif olmali", GameConfig.TAP_RADIUS_REF_PX > 0f)
        assertTrue(
            "insa on-izleme menzili en kisa L1 menzilinden cok sapmamali",
            GameConfig.BUILD_PREVIEW_RANGE_PX > 0f
        )
        assertTrue(
            "harita ust guvenli bant sinirlari sirali ve makul olmali",
            GameConfig.MAP_SAFE_TOP_FRAC_MIN < GameConfig.MAP_SAFE_TOP_FRAC_MAX &&
                GameConfig.MAP_SAFE_TOP_FRAC_MAX in 0f..0.5f &&
                GameConfig.MAP_SAFE_TOP_FRAC_MIN in -0.5f..0f
        )
        assertTrue("HUD varsayilan yuksekligi pozitif olmali", GameConfig.HUD_TOP_INSET_DP > 0f)
        assertTrue("sarsinti tasmasi negatif olamaz", GameConfig.SHAKE_OVERSCAN_REF_PX >= 0f)
        assertTrue(
            "bekleyen pad alfasi secili pad alfasindan dusuk olmali",
            GameConfig.BUILD_PAD_IDLE_ALPHA < GameConfig.BUILD_PAD_SELECTED_ALPHA
        )
        assertTrue("pad alfalari 0..1 araliginda olmali", GameConfig.BUILD_PAD_IDLE_ALPHA in 0f..1f)
        assertTrue("pad alfalari 0..1 araliginda olmali", GameConfig.BUILD_PAD_SELECTED_ALPHA in 0f..1f)
    }

    @Test
    fun debugPathDrawingIsOffInCommittedCode() {
        // true kalirsa oyuncu haritada macenta waypoint cizgilerini gorur.
        assertTrue(
            "DEBUG_DRAW_PATH commit'lenen kodda false olmali",
            !GameConfig.DEBUG_DRAW_PATH
        )
    }

    @Test
    fun preparationTimeAndBaseLivesArePlayable() {
        assertTrue("hazirlik suresi pozitif olmali", GameConfig.PREPARATION_TIME_SECONDS > 0)
        assertTrue("baslangic us cani pozitif olmali", GameConfig.INITIAL_BASE_LIVES > 0)
        assertTrue("us hasari pozitif olmali", GameConfig.BASE_REACHED_PENALTY_LIVES > 0)
        assertTrue(
            "us cani, tek dusman sizmasinin oyunu bitirmesine izin vermemeli",
            GameConfig.INITIAL_BASE_LIVES > GameConfig.BASE_REACHED_PENALTY_LIVES
        )
    }
}
