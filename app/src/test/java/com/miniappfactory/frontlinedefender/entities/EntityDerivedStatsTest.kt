package com.miniappfactory.frontlinedefender.entities

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

    private fun tower(type: TowerType, level: Int = 1): TowerEntity {
        val spec = GameConfig.TOWER_SPECS.getValue(type)
        return TowerEntity(
            type = type,
            buildSpotId = 1,
            posX = 0f,
            posY = 0f,
            level = level,
            totalInvestedGold = spec.buildCost
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
    fun upgradeCostIsOfferedAtLevelOneAndWithheldAtLevelTwo() {
        TowerType.values().forEach { type ->
            val spec = GameConfig.TOWER_SPECS.getValue(type)
            assertEquals(
                "$type kademe 1'de yukseltme maliyeti gostermeli",
                spec.level2UpgradeCost, tower(type, 1).upgradeCost
            )
            assertNull(
                "$type kademe 2'de yukseltme maliyeti OLMAMALI (maks kademe) — " +
                    "null olmazsa oyuncu ayni kule icin ikinci kez odeme yapar",
                tower(type, 2).upgradeCost
            )
        }
    }

    @Test
    fun maxTowerLevelIsTwoInThisRelease() {
        // DECISIONS B5: kademe 3 ONAYLANDI ama ilk APK'yi bloklamiyor.
        // Kademe 3 geldiginde bu test kirilir ve UI/motor birlikte guncellenir.
        TowerType.values().forEach { type ->
            assertNotNull(tower(type, 1).upgradeCost)
            assertNull(tower(type, 2).upgradeCost)
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
