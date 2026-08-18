package com.miniappfactory.frontlinedefender.entities

import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.UpgradeLine
import com.miniappfactory.frontlinedefender.game.model.EnemyEntity
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import com.miniappfactory.frontlinedefender.game.model.SlowStatus
import com.miniappfactory.frontlinedefender.game.model.TowerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SAF FONKSIYON TESTLERI — entity turetilmis ozellikleri.
 *
 * `TowerEntity` / `EnemyEntity` saf Kotlin veri siniflari; Android bagimliligi
 * yok, bu yuzden dogrudan test edilebiliyorlar. Hasar/menzil/satis degeri
 * hesabinin tamami buradan geciyor.
 */
class EntityDerivedStatsTest {

    private fun tower(
        type: TowerType,
        level: Int = 1,
        tierCap: Int = Int.MAX_VALUE
    ): TowerEntity {
        val spec = GameConfig.TOWER_SPECS.getValue(type)
        return TowerEntity(
            type = type,
            buildSpotId = 1,
            posX = 0f,
            posY = 0f,
            level = level,
            totalInvestedGold = spec.buildCost,
            tierCap = tierCap
        )
    }

    private fun enemy(type: EnemyType): EnemyEntity {
        val spec = GameConfig.ENEMY_SPECS.getValue(type)
        return EnemyEntity(
            type = type,
            posX = 0f,
            posY = 0f,
            hp = spec.maxHp,
            maxHp = spec.maxHp,
            baseSpeed = spec.baseSpeed,
            armor = spec.armor,
            rewardGold = spec.rewardGold,
            radius = spec.sizeRadius
        )
    }

    // ------------------------------------------------------------------- kule

    /**
     * `GameEngine.upgradeSelectedTower` KARAR kisminin aynasi (GameEngine.kt).
     * Motor Android'e bagli oldugu icin dogrudan cagrilamiyor — ayni yaklasim
     * hasar formulu icin de kullaniliyor (bkz. dosyanin sonu).
     *
     * @return yukseltme gerceklestiyse kalan Tedarik, reddedildiyse `null`.
     */
    private fun engineUpgrade(t: TowerEntity, supply: Int): Int? {
        val cost = t.upgradeCost ?: return null
        if (supply < cost) return null
        t.level += 1
        t.totalInvestedGold += cost
        return supply - cost
    }

    @Test
    fun levelOneTowerReportsItsLevelOneStats() {
        TowerType.values().forEach { type ->
            val spec = GameConfig.TOWER_SPECS.getValue(type)
            val t = tower(type, level = 1)
            assertEquals("$type menzili", spec.level1Range, t.range, 0f)
            assertEquals("$type hasari", spec.level1Damage, t.damage, 0f)
            assertEquals("$type ates araligi", spec.level1FireRate, t.fireRate, 0f)
        }
    }

    @Test
    fun levelTwoTowerReportsItsLevelTwoStats() {
        TowerType.values().forEach { type ->
            val spec = GameConfig.TOWER_SPECS.getValue(type)
            val t = tower(type, level = 2)
            assertEquals("$type menzili", spec.level2Range, t.range, 0f)
            assertEquals("$type hasari", spec.level2Damage, t.damage, 0f)
            assertEquals("$type ates araligi", spec.level2FireRate, t.fireRate, 0f)
        }
    }

    @Test
    fun everyTierExceptTheLastOffersTheNextTiersPrice() {
        TowerType.values().forEach { type ->
            val spec = GameConfig.TOWER_SPECS.getValue(type)
            for (tier in 1 until spec.maxTier) {
                assertEquals(
                    "$type kademe $tier, kademe ${tier + 1} bedelini gostermeli",
                    spec.tier(tier + 1).upgradeCost, tower(type, tier).upgradeCost
                )
            }
        }
    }

    /**
     * Son kademede yukseltme YOKTUR. Bu, kademe sayisindan (2 veya 3, ileride
     * 4) BAGIMSIZ olarak dogru olmali: sabit bir sayiya bakan bir kontrol
     * kademe eklendigi gun sessizce oyuncuya ikinci kez odeme yaptirir.
     */
    @Test
    fun theLastTierHasNoUpgradeAndTheEngineRefusesToChargeForOne() {
        TowerType.values().forEach { type ->
            val spec = GameConfig.TOWER_SPECS.getValue(type)
            for (tier in 1 until spec.maxTier) {
                assertNotNull("$type kademe $tier yukseltilebilmeli", tower(type, tier).upgradeCost)
            }

            val maxed = tower(type, spec.maxTier)
            assertNull(
                "$type son kademede (${spec.maxTier}) yukseltme maliyeti OLMAMALI — " +
                    "null olmazsa oyuncu ayni kule icin ikinci kez odeme yapar",
                maxed.upgradeCost
            )

            val investedBefore = maxed.totalInvestedGold
            assertNull(
                "$type: motor son kademedeki kuleyi yukseltmeyi REDDETMELI",
                engineUpgrade(maxed, supply = Int.MAX_VALUE)
            )
            assertEquals("$type: reddedilen yukseltme yatirimi degistirmemeli", investedBefore, maxed.totalInvestedGold)
            assertEquals("$type: reddedilen yukseltme kademeyi degistirmemeli", spec.maxTier, maxed.level)
        }
    }

    /**
     * Faz 13 / DECISIONS B5 — KAMPANYA KILIDI. Kademe 3 Act II'de acilir; Act
     * I'de kurulan bir kule icin panel MAKS gostermeli, yani `upgradeCost`
     * kademe 2'de null olmali.
     */
    @Test
    fun aTowerBuiltBeforeTheTierUnlockStopsAtTheTierItsCampaignLevelAllows() {
        val unlockLevel = GameConfig.TIER_THREE_UNLOCK_LEVEL
        TowerType.values().forEach { type ->
            val spec = GameConfig.TOWER_SPECS.getValue(type)

            val beforeUnlock = (unlockLevel - 1).coerceAtLeast(spec.unlockedAtLevel)
            val cappedTier = GameConfig.maxTowerTier(type, beforeUnlock)
            val capped = tower(type, cappedTier, tierCap = cappedTier)
            assertNull(
                "$type: bolum $beforeUnlock'de kademe $cappedTier son kademe olmali",
                capped.upgradeCost
            )

            val openTier = GameConfig.maxTowerTier(type, unlockLevel)
            assertEquals(
                "$type: kademe kilidi bolum $unlockLevel'de merdivenin tamamini acmali",
                spec.maxTier, openTier
            )
            assertNotNull(
                "$type: bolum $unlockLevel'de kademe $cappedTier hâlâ yukseltilebilmeli",
                tower(type, cappedTier, tierCap = openTier).upgradeCost
            )
        }
    }

    @Test
    fun sellValueRefundsSeventyPercentOfEverythingInvested() {
        TowerType.values().forEach { type ->
            val spec = GameConfig.TOWER_SPECS.getValue(type)
            val t = tower(type, level = 1)
            assertEquals(
                "$type kademe 1 satis degeri",
                (spec.buildCost * 0.70f).toInt(), t.sellValue
            )

            // Yukseltilmis kule: yatirim insa + yukseltme.
            val upgraded = tower(type, level = 2).also {
                it.totalInvestedGold = spec.buildCost + spec.level2UpgradeCost
            }
            assertEquals(
                "$type kademe 2 satis degeri",
                ((spec.buildCost + spec.level2UpgradeCost) * 0.70f).toInt(), upgraded.sellValue
            )
        }
    }

    /**
     * Satis degeri yatirimi ASLA gecmemeli, yoksa kur-sat dongusu sonsuz para
     * uretir ve ekonominin tamami coker.
     */
    @Test
    fun sellingATowerCanNeverBeProfitable() {
        TowerType.values().forEach { type ->
            for (invested in 1..500) {
                val t = tower(type).also { it.totalInvestedGold = invested }
                assertTrue(
                    "$type: $invested yatirim -> ${t.sellValue} geri odeme — " +
                        "kur/sat dongusu para uretiyor",
                    t.sellValue < invested
                )
            }
        }
    }

    /**
     * Faz 13 / DECISIONS B5 — **"YUKSELT SONRA SAT" ARBITRAJI OLMAMALI.**
     *
     * Kademe 3 yukseltme bedellerini iki katina cikardi. Eger geri odeme orani
     * bir gun 1.0'a yaklastirilirsa "kur -> maksa cikar -> sat" dongusu para
     * uretir ve gec oyun ekonomisi coker — ustelik bunu yalnizca kademe 3'u
     * olan oyuncu yapabildigi icin sorun tam da bu fazda dogar.
     *
     * Test butun kule tiplerini, butun kademe basamaklarini ve butun SALVAGE
     * meta rank'larini tarar. Sayilarin hicbiri elle yazilmadi.
     */
    @Test
    fun upgradingThenSellingIsNeverMoreProfitableThanSellingRightAway() {
        val ranks = 0..UpgradeLine.SALVAGE.maxRank
        TowerType.values().forEach { type ->
            val spec = GameConfig.TOWER_SPECS.getValue(type)
            ranks.forEach { rank ->
                val rate = (EconomyConfig.BASE_SALVAGE_RATIO +
                    EconomyConfig.SALVAGE_PER_RANK * rank).toFloat()
                assertTrue("salvage rank $rank orani 1.0'i gecemez", rate < 1f)

                var invested = spec.buildCost
                for (tier in 1 until spec.maxTier) {
                    val cost = spec.tier(tier + 1).upgradeCost
                    val sellNow = TowerEntity(
                        type = type, buildSpotId = 1, posX = 0f, posY = 0f,
                        level = tier, totalInvestedGold = invested, salvageRate = rate
                    ).sellValue
                    val sellAfterUpgrade = TowerEntity(
                        type = type, buildSpotId = 1, posX = 0f, posY = 0f,
                        level = tier + 1, totalInvestedGold = invested + cost, salvageRate = rate
                    ).sellValue

                    assertTrue(
                        "$type kademe $tier -> ${tier + 1} (salvage rank $rank): " +
                            "$cost ode, geri odeme $sellNow -> $sellAfterUpgrade — " +
                            "yukseltip satmak para URETIYOR",
                        sellAfterUpgrade - cost < sellNow
                    )
                    invested += cost
                }

                // Merdivenin tamami: tam yatirimin geri odemesi yatirimin altinda.
                val maxed = TowerEntity(
                    type = type, buildSpotId = 1, posX = 0f, posY = 0f,
                    level = spec.maxTier, totalInvestedGold = invested, salvageRate = rate
                )
                assertTrue(
                    "$type maks kademe (yatirim $invested, salvage rank $rank): " +
                        "geri odeme ${maxed.sellValue} yatirimin altinda olmali",
                    maxed.sellValue < invested
                )
            }
        }
    }

    /**
     * Motorun muhasebesi: her yukseltme `totalInvestedGold`e EKLENIR, uzerine
     * yazilmaz. Aksi halde kademe 3'e cikan bir kule satildiginda oyuncu
     * kademe 2 yatirimini kaybederdi (ya da tersi: iki kez geri alirdi).
     */
    @Test
    fun climbingTheWholeTierLadderAccumulatesEveryPaymentIntoTheSellValue() {
        TowerType.values().forEach { type ->
            val spec = GameConfig.TOWER_SPECS.getValue(type)
            val t = tower(type)
            var paid = spec.buildCost
            while (t.upgradeCost != null) {
                val cost = t.upgradeCost!!
                assertNotNull("$type: yukseltme kabul edilmeli", engineUpgrade(t, supply = cost))
                paid += cost
                assertEquals("$type kademe ${t.level}: birikmis yatirim", paid, t.totalInvestedGold)
            }
            assertEquals("$type merdivenin sonuna cikmali", spec.maxTier, t.level)
            assertEquals(
                "$type: satis degeri TUM yatirimdan hesaplanmali",
                (paid * t.salvageRate).toInt(), t.sellValue
            )
        }
    }

    @Test
    fun sellValueIsNeverNegative() {
        TowerType.values().forEach { type ->
            val t = tower(type).also { it.totalInvestedGold = 0 }
            assertTrue("$type negatif geri odeme", t.sellValue >= 0)
        }
    }

    @Test
    fun towersStartUnfiredFacingTheirDefaultTargetingMode() {
        TowerType.values().forEach { type ->
            val t = tower(type)
            assertEquals(
                "yeni kule varsayilan olarak FIRST modunda olmali",
                GameConfig.TargetingMode.FIRST, t.targetingMode
            )
            assertEquals("yeni kule bekleme suresi 0 olmali", 0f, t.cooldownTimerSeconds, 0f)
            assertEquals("yeni kule geri tepmesi 0 olmali", 0f, t.recoilOffsetPx, 0f)
            assertEquals("yeni kule kill sayaci 0 olmali", 0, t.killsCount)
        }
    }

    @Test
    fun towerIdsAreUniquePerInstance() {
        val ids = (1..200).map { tower(TowerType.MACHINE_GUN).id }
        assertEquals("kule id'leri tekil olmali", ids.size, ids.toSet().size)
    }

    @Test
    fun cyclingTargetingModesVisitsEveryModeAndReturnsToTheStart() {
        // GameEngine.cycleTargetingMode mantigi: (ordinal + 1) % size
        val modes = GameConfig.TargetingMode.values()
        var current = GameConfig.TargetingMode.FIRST
        val visited = mutableListOf(current)
        repeat(modes.size - 1) {
            current = modes[(current.ordinal + 1) % modes.size]
            visited.add(current)
        }
        assertEquals("her hedefleme modu tam bir kez ziyaret edilmeli", modes.toList(), visited)

        val wrapped = modes[(current.ordinal + 1) % modes.size]
        assertEquals("son moddan sonra basa donmeli", GameConfig.TargetingMode.FIRST, wrapped)
    }

    // ----------------------------------------------------------------- dusman

    @Test
    fun aFreshEnemyIsAtFullHealthAndNotDead() {
        EnemyType.values().forEach { type ->
            val e = enemy(type)
            assertEquals("$type tam canla dogmali", e.maxHp, e.hp, 0f)
            assertFalse("$type dogar dogmaz olu olamaz", e.isDead)
            assertEquals("$type baslangicta yol almamis olmali", 0f, e.distanceTraveledPx, 0f)
            assertEquals("$type ilk waypointten baslamali", 0, e.currentWayPointIndex)
            assertNull("$type yavaslatilmamis dogmali", e.activeSlow)
        }
    }

    @Test
    fun anEnemyIsDeadOnlyOnceHealthReachesZeroOrBelow() {
        val e = enemy(EnemyType.INFANTRY)
        e.hp = 0.01f
        assertFalse("0.01 HP hâlâ canli", e.isDead)
        e.hp = 0f
        assertTrue("0 HP olu sayilmali", e.isDead)
        e.hp = -50f
        assertTrue("negatif HP olu sayilmali", e.isDead)
    }

    @Test
    fun anUnslowedEnemyMovesAtItsBaseSpeed() {
        EnemyType.values().forEach { type ->
            val e = enemy(type)
            assertEquals("$type yavaslatilmamis hizi", e.baseSpeed, e.currentSpeed, 0f)
        }
    }

    @Test
    fun theSlowTowerReducesSpeedByExactlyItsDeclaredFactor() {
        val slowSpec = GameConfig.TOWER_SPECS.getValue(TowerType.SLOW)
        EnemyType.values().forEach { type ->
            val e = enemy(type)
            e.activeSlow = SlowStatus(slowSpec.slowFactor, slowSpec.slowDuration)
            val expected = e.baseSpeed * (1f - slowSpec.slowFactor)
            assertEquals("$type yavaslatilmis hizi", expected, e.currentSpeed, 1e-4f)
            assertTrue("$type yavaslatilinca hizi dusmeli", e.currentSpeed < e.baseSpeed)
        }
    }

    @Test
    fun aSlowedEnemyNeverStopsCompletelyOrWalksBackwards() {
        // Hiz 0 olursa dalga hic bitmez, negatif olursa dusman spawn'a doner.
        val slowSpec = GameConfig.TOWER_SPECS.getValue(TowerType.SLOW)
        EnemyType.values().forEach { type ->
            val e = enemy(type)
            e.activeSlow = SlowStatus(slowSpec.slowFactor, slowSpec.slowDuration)
            assertTrue(
                "$type yavaslatilinca hizi ${e.currentSpeed} — pozitif kalmali",
                e.currentSpeed > 0f
            )
        }
    }

    @Test
    fun enemyStatsLookupResolvesForEveryType() {
        EnemyType.values().forEach { type ->
            val e = enemy(type)
            assertEquals("$type stats cozumlemesi yanlis tipe gitti", type, e.stats.type)
            assertEquals("$type splash zafiyeti", GameConfig.ENEMY_SPECS.getValue(type).splashVulnerability, e.stats.splashVulnerability, 0f)
        }
    }

    @Test
    fun towerStatsLookupResolvesForEveryType() {
        TowerType.values().forEach { type ->
            assertEquals("$type stats cozumlemesi yanlis tipe gitti", type, tower(type).stats.type)
        }
    }

    // ------------------------------------------------- hasar formulu (motor aynasi)

    /**
     * `GameEngine.applyDamageToEnemy` formulunun aynasi (GameEngine.kt:906-932).
     * Motor Android'e bagli oldugu icin dogrudan cagrilamiyor — bkz.
     * docs/QA_REPORT.md B-01 test edilebilirlik notu.
     */
    private fun damageTo(e: EnemyEntity, raw: Float, pierce: Float, isSplash: Boolean): Float =
        if (isSplash) {
            raw * e.stats.splashVulnerability
        } else {
            val effectiveArmor = (e.armor * (1f - pierce)).coerceAtLeast(0f)
            raw * (1f - effectiveArmor)
        }

    @Test
    fun armourReducesDirectHitsButNeverHealsTheTarget() {
        EnemyType.values().forEach { type ->
            val e = enemy(type)
            val dealt = damageTo(e, raw = 100f, pierce = 0f, isSplash = false)
            assertTrue("$type: hasar negatif olamaz (dusman iyilesir)", dealt >= 0f)
            assertTrue("$type: hasar ham hasari gecemez", dealt <= 100f)
            if (e.armor > 0f) {
                assertTrue("$type zirhli, hasar azalmali", dealt < 100f)
            }
        }
    }

    @Test
    fun armourPiercingAlwaysBeatsPlainAmmoAgainstArmouredTargets() {
        val pierce = GameConfig.TOWER_SPECS.getValue(TowerType.ANTI_ARMOR).armorPierce
        EnemyType.values()
            .map { enemy(it) }
            .filter { it.armor > 0f }
            .forEach { e ->
                val plain = damageTo(e, 100f, 0f, isSplash = false)
                val piercing = damageTo(e, 100f, pierce, isSplash = false)
                assertTrue(
                    "${e.type}: delici ($piercing) normal muhimmattan ($plain) iyi olmali",
                    piercing > plain
                )
            }
    }

    /**
     * DECISIONS B2'nin tum amaci: Cannon'in zirhli hedefe karsi EN KOTU secenek
     * olmasini engellemek. Splash zirhi bypass eder.
     */
    @Test
    fun splashBypassesArmourEntirelyAsDecidedInB2() {
        EnemyType.values().forEach { type ->
            val e = enemy(type)
            val splash = damageTo(e, 100f, 0f, isSplash = true)
            val expected = 100f * e.stats.splashVulnerability
            assertEquals(
                "${type}: splash hasari zirhtan ETKILENMEMELI",
                expected, splash, 1e-3f
            )
        }
    }

    @Test
    fun splashOutDamagesPlainBulletsAgainstEveryArmouredEnemy() {
        EnemyType.values()
            .map { enemy(it) }
            .filter { it.armor > 0f }
            .forEach { e ->
                val bullets = damageTo(e, 100f, 0f, isSplash = false)
                val splash = damageTo(e, 100f, 0f, isSplash = true)
                assertTrue(
                    "${e.type} (zirh ${e.armor}): splash ($splash) kursundan ($bullets) " +
                        "iyi olmali — yoksa Cannon gec oyunda olu kalir",
                    splash > bullets
                )
            }
    }

    @Test
    fun theShieldedTrooperTakesMoreFromExplosionsThanFromAnythingElse() {
        val pierce = GameConfig.TOWER_SPECS.getValue(TowerType.ANTI_ARMOR).armorPierce
        val e = enemy(EnemyType.SHIELDED_TROOPER)

        val bullets = damageTo(e, 100f, 0f, isSplash = false)
        val piercing = damageTo(e, 100f, pierce, isSplash = false)
        val splash = damageTo(e, 100f, 0f, isSplash = true)

        assertTrue("kalkanli piyade kursuna direncli olmali", bullets < 50f)
        assertTrue("patlama kursundan cok daha iyi olmali", splash > bullets * 2f)
        assertTrue("patlama delici muhimmattan da iyi olmali", splash > piercing)
    }

    @Test
    fun fullArmourPiercingIgnoresArmourCompletely() {
        EnemyType.values().forEach { type ->
            val e = enemy(type)
            assertEquals(
                "$type: %100 delici tam ham hasari vermeli",
                100f, damageTo(e, 100f, 1f, isSplash = false), 1e-3f
            )
        }
    }

    @Test
    fun killingAnEnemyTakesAFiniteNumberOfShotsForEveryTowerEnemyPairing() {
        // Hicbir kule/dusman esleşmesi "sonsuz vurus" olmamali: en azindan
        // Cannon splash'i ya da Railgun her dusmani sonlu vurusla oldurebilmeli.
        EnemyType.values().forEach { type ->
            val e = enemy(type)
            val best = TowerType.values().maxOf { tt ->
                val spec = GameConfig.TOWER_SPECS.getValue(tt)
                val isSplash = spec.splashRadius > 0f
                damageTo(e, spec.level2Damage, spec.armorPierce, isSplash)
            }
            assertTrue("$type'a hicbir kule hasar veremiyor", best > 0f)
            val shots = Math.ceil((e.maxHp / best).toDouble()).toInt()
            assertTrue(
                "$type icin en iyi kule bile $shots vurus istiyor — bu bir duvar",
                shots <= 60
            )
        }
    }
}
