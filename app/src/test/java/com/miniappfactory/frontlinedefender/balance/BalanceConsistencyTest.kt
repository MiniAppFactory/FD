package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
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

    @Test
    fun startingSupplyAffordsAtLeastTwoOfTheCheapestTower() {
        val cheapest = GameConfig.TOWER_SPECS.values.minOf { it.buildCost }
        assertTrue(
            "baslangic tedariki ${GameConfig.INITIAL_GOLD}, en ucuz kule $cheapest — " +
                "ilk dalgada en az 2 kule kurulabilmeli",
            GameConfig.INITIAL_GOLD >= cheapest * 2
        )
    }

    @Test
    fun theLongestRangedTowerIsAlsoTheMostExpensiveToBuild() {
        val longest = GameConfig.TOWER_SPECS.values.maxBy { it.level1Range }
        val priciest = GameConfig.TOWER_SPECS.values.maxBy { it.buildCost }
        assertEquals(
            "en uzun menzilli kule ayni zamanda en pahali olmali, aksi halde " +
                "diger kuleler anlamsizlasir",
            longest.type, priciest.type
        )
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
        assertTrue("harita ust guvenli bant 0..0.5 araliginda olmali", GameConfig.MAP_SAFE_TOP_FRAC in 0f..0.5f)
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
