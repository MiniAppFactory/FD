package com.miniappfactory.frontlinedefender.tutorial

import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.ui.HintCopy
import com.miniappfactory.frontlinedefender.game.ui.HintFacts
import com.miniappfactory.frontlinedefender.game.ui.HintFlow
import com.miniappfactory.frontlinedefender.game.ui.HintSignals
import com.miniappfactory.frontlinedefender.game.ui.UnlockHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KILIT ACILMA IPUCLARI — SAYILARIN DOGRULUGU.
 *
 * Ipuclari **sifat degil sayi** gosterdigi icin ("Fuze zirha karsi guclu"
 * DEGIL, "Ağır Tank: Gatling 6,1 DPS · Füze Rampası 26,6") sayilar yanlissa
 * ipucu dogrudan YALAN SOYLER. Bu dosya iki seyi kilitler:
 *
 *  1. [HintFacts.dps] motorun hasar formulunun aynasidir
 *     (`GameEngine.applyDamageToEnemy` + `BalanceConsistencyTest.effectiveDps`).
 *  2. Her ipucunun IDDIASI mevcut dengede hâlâ DOGRUDUR.
 *
 * ---------------------------------------------------------------------------
 * NEDEN MUTLAK SAYI KILITLENMIYOR
 * ---------------------------------------------------------------------------
 * "Gatling tanka 6,1 DPS" gibi bir esitlik yazmak cazip ama YANLIS olurdu:
 * denge tablosu su anda baska bir is kolunun elinde ve her ayarlama bu dosyayi
 * kirmis olurdu. Ipucunun sozu **sayinin kendisi degil, sayilarin ILISKISI**:
 * "yeni kule bu hedefte Gatling'den daha iyi". Testler o iliskiyi dogruluyor,
 * yani denge degistiginde ipucu sessizce yalan soylemeye BASLAYAMAZ ama mesru
 * bir yeniden dengeleme de bosuna kirmiz uretmez.
 */
class UnlockHintFactsTest {

    private fun spec(t: GameConfig.TowerType) = GameConfig.TOWER_SPECS.getValue(t)
    private fun enemy(t: GameConfig.EnemyType) = GameConfig.ENEMY_SPECS.getValue(t)

    private val gatling = GameConfig.TowerType.MACHINE_GUN
    private val cannon = GameConfig.TowerType.CANNON
    private val missile = GameConfig.TowerType.ANTI_ARMOR
    private val frost = GameConfig.TowerType.SLOW

    /** Zirhi olan her dusman tipi — `ENEMY_SPECS`'ten TURETILIR. */
    private val armouredTypes = GameConfig.EnemyType.values().filter { HintFacts.isArmored(it) }

    // =======================================================================
    // 1) Formul motorun aynasi mi
    // =======================================================================

    /**
     * Zirhsiz hedefte hicbir azaltma yoktur: DPS ham `hasar / atisAraligi`
     * olmali. `TowerTier.dps` zaten bu tanimi tasiyor, yani iki taraf ayni
     * yerden okundugunda birebir esit cikmali.
     */
    @Test
    fun againstUnarmouredTargetsDpsIsTheRawTierDps() {
        val soft = GameConfig.EnemyType.INFANTRY
        assertEquals(0f, enemy(soft).armor, 0f)

        for (tower in listOf(gatling, missile)) {
            assertEquals(
                "$tower zirhsiz hedefte ham kademe DPS'ini vermeli",
                spec(tower).tier(HintFacts.REFERENCE_TIER).dps,
                HintFacts.dps(tower, soft),
                0.01f
            )
        }
    }

    /**
     * DECISIONS B2: **splash zirhi BYPASS eder.** Cannon'in zirhli hedefteki
     * DPS'i zirh yuzunden DUSMEZ; yalnizca `splashVulnerability` ile carpilir.
     * Ipucunun "Top'un cevabi zirh" iddiasinin motor tarafi budur.
     */
    @Test
    fun cannonIgnoresArmourExactlyAsTheEngineDoes() {
        val soft = GameConfig.EnemyType.INFANTRY
        val base = HintFacts.dps(cannon, soft) / enemy(soft).splashVulnerability

        for (type in armouredTypes) {
            assertEquals(
                "$type: Top'un DPS'i zirhtan DEGIL, yalnizca splash carpanindan etkilenmeli",
                base * enemy(type).splashVulnerability,
                HintFacts.dps(cannon, type),
                0.01f
            )
        }
    }

    /** Fuze zirhin yalnizca `(1 - armorPierce)` kadarini yer. */
    @Test
    fun missilePiercesArmourExactlyAsTheEngineDoes() {
        for (type in armouredTypes) {
            val armorLeft = enemy(type).armor * (1f - spec(missile).armorPierce)
            val expected = spec(missile).tier(HintFacts.REFERENCE_TIER).dps * (1f - armorLeft)
            assertEquals(
                "$type: fuze hasari zirhin delinmeyen kismindan etkilenmeli",
                expected,
                HintFacts.dps(missile, type),
                0.01f
            )
        }
    }

    /** Frost'un cryo darbesi zirha TAM tabidir (delici degil, splash degil). */
    @Test
    fun frostDamageIsFullyReducedByArmour() {
        for (type in armouredTypes) {
            val expected = spec(frost).tier(HintFacts.REFERENCE_TIER).dps * (1f - enemy(type).armor)
            assertEquals(expected, HintFacts.dps(frost, type), 0.01f)
        }
    }

    /** Bozuk/tanimsiz girdi cokme degil SIFIR uretir; ipucu o karede cizilmez. */
    @Test
    fun theFormulaNeverThrows() {
        for (tower in GameConfig.TowerType.values()) {
            for (type in GameConfig.EnemyType.values()) {
                val value = HintFacts.dps(tower, type)
                assertTrue("$tower/$type: DPS negatif olamaz", value >= 0f)
                assertTrue("$tower/$type: DPS sonlu olmali", value.isFinite())
            }
        }
        // Aralik disi kademe kirpilir, istisna atmaz.
        assertTrue(HintFacts.dps(gatling, GameConfig.EnemyType.INFANTRY, tier = 99) > 0f)
        assertTrue(HintFacts.dps(gatling, GameConfig.EnemyType.INFANTRY, tier = -3) > 0f)
    }

    // =======================================================================
    // 2) Ipuclarinin IDDIASI hâlâ dogru mu
    // =======================================================================

    /**
     * ZIRH UYARISININ iddiasi: "kursun zirha islemez." Somut karsiligi,
     * Gatling'in zirhli hedefteki DPS'inin zirhsiz hedefin belirgin ALTINDA
     * olmasidir. Esik %50: bunun ustune ciktigi gun zirh bir karsi-koyma
     * olmaktan cikar ve uyari anlamsizlasir.
     */
    @Test
    fun theArmourWarningIsStillTrue() {
        val soft = HintFacts.dps(gatling, GameConfig.EnemyType.INFANTRY)
        assertTrue("zirhli dusman tanimlanmis olmali", armouredTypes.isNotEmpty())

        for (type in armouredTypes) {
            val hard = HintFacts.dps(gatling, type)
            assertTrue(
                "$type: kursun zirha islemiyor demek icin DPS zirhsizin yarisindan az olmali " +
                    "(zirhsiz $soft, zirhli $hard)",
                hard < soft * 0.5f
            )
        }
    }

    /**
     * ROL IPUCLARININ iddiasi: yeni acilan kule, gosterilen hedefte
     * Gatling'den DAHA IYI. Ipucu iki sayiyi yan yana koydugu icin bu iliski
     * bozulursa serit kendi kendini curutur.
     *
     * Test butun ORNEK ADAYLARI uzerinden kosar — hangi bolumde hangi aday
     * secilirse secilsin iddia gecerli kalmali.
     */
    @Test
    fun everyTowerMatchupClaimIsStillTrue() {
        for (hint in listOf(UnlockHint.MISSILE_ROLE, UnlockHint.CANNON_ROLE)) {
            for (type in GameConfig.EnemyType.values()) {
                val copy = HintFlow.copyFor(hint, signalsWith(hint, type))
                    as? HintCopy.TowerMatchup ?: continue
                if (copy.enemy != type) continue // bu tip aday listesinde degil

                assertTrue(
                    "$hint / $type: ipucu yeni kuleyi ${copy.newDps} DPS ile gosteriyor ama " +
                        "Gatling ${copy.oldDps} DPS yapiyor — serit YALAN SOYLER",
                    copy.newDps > copy.oldDps
                )
            }
        }
    }

    /**
     * Denetimin manseti: **fuze tanka Gatling'in katbekat ustunde vurur.**
     * Sayinin kendisi kilitli degil (bkz. sinif KDoc'u) ama BUYUKLUK MERTEBESI
     * kilitli: 3 katin altina duserse "7 kat fark" anlatisi coker ve ipucunun
     * ogretecek bir seyi kalmaz.
     */
    @Test
    fun theHeadlineAntiTankGapSurvives() {
        val tank = GameConfig.EnemyType.TANK
        val ratio = HintFacts.dps(missile, tank) / HintFacts.dps(gatling, tank).coerceAtLeast(0.01f)
        assertTrue(
            "tankta fuze/Gatling orani en az 3 kat olmali, olculen: $ratio",
            ratio >= 3f
        )
    }

    /**
     * Top'un kimligi: KALKANLI hedefte (splash zayifligi 1.6) hem Gatling'den
     * hem fuzeden iyi olmali. "Zirhlinin cevabi Fuze degil Cannon" cumlesinin
     * sayisal karsiligi budur.
     */
    @Test
    fun theCannonKeepsItsShieldedTrooperNiche() {
        val shielded = GameConfig.EnemyType.SHIELDED_TROOPER
        assertTrue(
            "Top kalkanliya karsi Gatling'den iyi olmali",
            HintFacts.dps(cannon, shielded) > HintFacts.dps(gatling, shielded)
        )
        assertTrue(
            "kalkanlinin patlamaya zayifligi korunmali (DECISIONS B1)",
            enemy(shielded).splashVulnerability > 1f
        )
    }

    /**
     * FROST IPUCUNUN iddiasi: "hasar vermez, yavaslatir." Iki tarafi da olculur.
     */
    @Test
    fun theFrostClaimIsStillTrue() {
        val copy = HintFlow.copyFor(
            UnlockHint.FROST_ROLE,
            HintSignals(
                gameState = GameState.PREPARATION,
                levelId = spec(frost).unlockedAtLevel,
                waveIndex = 0,
                tutorialArmed = false
            )
        ) as HintCopy.SupportRole

        val gatlingDps = HintFacts.dps(gatling, GameConfig.EnemyType.INFANTRY)
        assertTrue(
            "Frost 'hasar vermez' diyorsa DPS'i Gatling'in %20'sinin altinda kalmali " +
                "(Frost ${copy.dps}, Gatling $gatlingDps)",
            copy.dps < gatlingDps * 0.2f
        )
        assertTrue("yavaslatma yuzdesi anlamli olmali", copy.slowPercent in 1..99)
        assertTrue(
            "yavaslatilan dusman menzilde DAHA UZUN kalmali, olculen carpan: " +
                copy.rangeTimeMultiplier,
            copy.rangeTimeMultiplier > 1f
        )

        // Carpan yavaslatma yuzdesinin dogrudan sonucu: 1 / (1 - yavaslatma).
        val expected = 1f / (1f - spec(frost).slowFactor)
        assertEquals(expected, copy.rangeTimeMultiplier, 0.01f)
    }

    /** Frost'un yavaslatmasi yoksa ipucu sifira bolmez. */
    @Test
    fun theSlowMathIsSafeForTowersWithoutSlow() {
        assertEquals(0, HintFacts.slowPercent(gatling))
        assertEquals(1f, HintFacts.rangeTimeMultiplier(gatling), 0.001f)
    }

    private fun signalsWith(hint: UnlockHint, type: GameConfig.EnemyType) = HintSignals(
        gameState = GameState.PREPARATION,
        levelId = GameConfig.TOWER_SPECS.getValue(requireNotNull(hint.unlockTower)).unlockedAtLevel,
        waveIndex = 0,
        tutorialArmed = false,
        levelEnemyTypes = setOf(type)
    )
}
