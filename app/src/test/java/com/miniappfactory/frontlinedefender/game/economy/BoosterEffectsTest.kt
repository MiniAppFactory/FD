package com.miniappfactory.frontlinedefender.game.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 10 — GUCLENDIRICI ETKILERININ OYNANISA UYGULANMASI.
 *
 * `BoosterEconomyTest` bir kullanimin izinli olup olmadigini kanitlar; bu sinif
 * IZINLI kullanimin savas kaynaklarina DOGRU islendigini kanitlar. Aradaki halka
 * ([applyBoosterToResources] / [airSupportDamage]) motorun icine yazilsaydi hicbir
 * test onu goremezdi: `GameEngine`in Android bagimliligi var, birim testi yok.
 *
 * Kilitlenen sert kurallar:
 * 1. Reddedilen aktivasyon **hicbir kaynagi** degistirmez.
 * 2. Us Tamiri can tavanini ASMAZ (asarsa yildiz notrlugu anlamsizlasir).
 * 3. Tedarik negatife DUSMEZ.
 * 4. Savas basina iki hava destegi tam canli bir dusmani OLDURMEZ — ECONOMY_SPEC'in
 *    pay-to-win kalkani.
 */
class BoosterEffectsTest {

    private val res = BattleResources(supply = 200, lives = 15)
    private val maxLives = EconomyConfig.BASE_MAX_HEALTH // 20

    /** Izinli bir aktivasyon uretir; alanlar cagiranin verdigi degerlerdir. */
    private fun allowed(
        type: BoosterType,
        viaAd: Boolean = false,
        supplyGranted: Int = 0,
        supplyCharged: Int = 0,
        healthRestored: Int = 0,
        airSupportDamageFraction: Double = 0.0,
    ) = BoosterActivation(
        type = type,
        decision = BoosterDecision.Allowed(
            price = supplyCharged,
            currency = type.currency,
            viaAd = viaAd,
        ),
        viaAd = viaAd,
        supplyGranted = supplyGranted,
        supplyCharged = supplyCharged,
        healthRestored = healthRestored,
        airSupportDamageFraction = airSupportDamageFraction,
    )

    // =================================================================================
    // 1. Reddedilen aktivasyon — yan etki YOK
    // =================================================================================

    @Test
    fun rejectedActivationLeavesBattleResourcesCompletelyUntouched() {
        BoosterType.entries.forEach { type ->
            val rejected = BoosterActivation(
                type = type,
                decision = BoosterDecision.PaidLimitReached,
                viaAd = false,
            )
            assertEquals("$type", res, applyBoosterToResources(res, rejected, maxLives))
        }
    }

    @Test
    fun rejectedActivationIsIgnoredEvenWhenItStillCarriesEffectValues() {
        // Motor "reddedilen kararda alanlar zaten sifirdir" varsayimina GUVENMEZ.
        // Ileride bilgi amacli dolu bir alan tasinirsa oynanis sessizce bozulmasin.
        val bogus = BoosterActivation(
            type = BoosterType.EMERGENCY_SUPPLY,
            decision = BoosterDecision.DailyAdLimitReached(EconomyConfig.BOOSTER_AD_VIEWS_PER_DAY),
            viaAd = true,
            supplyGranted = 9_999,
            healthRestored = 9_999,
            airSupportDamageFraction = 1.0,
        )
        assertSame(res, applyBoosterToResources(res, bogus, maxLives))
    }

    // =================================================================================
    // 2. Tedarik yonu — Acil Tedarik ekler, Hava Destegi harcar
    // =================================================================================

    @Test
    fun emergencySupplyIncreasesSupplyByExactlyTheGrantedAmount() {
        val granted = emergencySupplyAmount(level = 10)
        val out = applyBoosterToResources(
            res,
            allowed(BoosterType.EMERGENCY_SUPPLY, viaAd = true, supplyGranted = granted),
            maxLives,
        )
        assertEquals(res.supply + granted, out.supply)
        assertEquals("can degismemeli", res.lives, out.lives)
    }

    @Test
    fun airSupportDecreasesSupplyByExactlyItsPrice() {
        val price = boosterPrice(BoosterType.AIR_SUPPORT, level = 4)
        val rich = BattleResources(supply = price + 50, lives = 12)
        val out = applyBoosterToResources(
            rich,
            allowed(
                BoosterType.AIR_SUPPORT,
                supplyCharged = price,
                airSupportDamageFraction = EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION,
            ),
            maxLives,
        )
        assertEquals(50, out.supply)
        assertEquals("can degismemeli", rich.lives, out.lives)
    }

    @Test
    fun supplyNeverGoesNegativeNoMatterHowLargeTheCharge() {
        // Tedarik yetmezken zaten `BoosterDecision.InsufficientSupply` doner; bu test
        // ikinci savunma hattidir — negatif Tedarik HUD'da ve butun fiyat
        // karsilastirmalarinda anlamsizdir.
        val poor = BattleResources(supply = 10, lives = 5)
        val out = applyBoosterToResources(
            poor,
            allowed(BoosterType.AIR_SUPPORT, supplyCharged = 500),
            maxLives,
        )
        assertEquals(0, out.supply)
    }

    @Test
    fun chargeAndGrantAreSettledInTheSameCall() {
        val out = applyBoosterToResources(
            res,
            allowed(BoosterType.AIR_SUPPORT, supplyCharged = 120, supplyGranted = 60),
            maxLives,
        )
        assertEquals(200 - 120 + 60, out.supply)
    }

    // =================================================================================
    // 3. Us Tamiri — can tavani
    // =================================================================================

    @Test
    fun baseRepairRestoresHealthWithoutTouchingSupply() {
        val restored = baseRepairAmount(currentHealth = 12, maxHealth = maxLives)
        assertTrue("test anlamsiz olurdu", restored > 0)
        val hurt = BattleResources(supply = 200, lives = 12)
        val out = applyBoosterToResources(
            hurt,
            allowed(BoosterType.BASE_REPAIR, healthRestored = restored),
            maxLives,
        )
        assertEquals(12 + restored, out.lives)
        assertEquals("tamir COIN oder, Tedarik DEGIL", hurt.supply, out.supply)
    }

    @Test
    fun baseRepairNeverExceedsMaxLives() {
        // Tavan asilirsa `effectiveStarHealth` ile kurulan yildiz notrlugu anlamsizlasir:
        // oyuncu savasa girdiginden FAZLA canla bitirir ve tamir yildiz satin alir.
        val out = applyBoosterToResources(
            BattleResources(supply = 0, lives = maxLives - 1),
            allowed(BoosterType.BASE_REPAIR, healthRestored = 999),
            maxLives,
        )
        assertEquals(maxLives, out.lives)
    }

    @Test
    fun repairRespectsTheMetaUpgradedCeilingNotTheBaseOne() {
        // Meta yukseltmeli oyuncunun tavani BASE_MAX_HEALTH'ten yuksektir; motor
        // `levelSpec.maxBaseLives + metaLivesBonus` gonderir.
        val upgradedCeiling = maxLives + 10
        val out = applyBoosterToResources(
            BattleResources(supply = 0, lives = maxLives),
            allowed(BoosterType.BASE_REPAIR, healthRestored = 999),
            upgradedCeiling,
        )
        assertEquals(upgradedCeiling, out.lives)
    }

    @Test
    fun livesNeverGoNegative() {
        val out = applyBoosterToResources(
            BattleResources(supply = 0, lives = 0),
            allowed(BoosterType.BASE_REPAIR, healthRestored = -5),
            maxLives,
        )
        assertEquals(0, out.lives)
    }

    // =================================================================================
    // 4. Hava Destegi hasari — PAY-TO-WIN KALKANI
    // =================================================================================

    @Test
    fun twoAirSupportUsesNeverKillAFullHealthEnemy() {
        // ECONOMY_SPEC SERT KISITI. Sabit ELLE YAZILMAZ: oran degisirse bu test
        // kendiliginden yeni degeri denetlemeli, sessizce eski degeri onaylamamali.
        val fraction = EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION
        val uses = BoosterType.AIR_SUPPORT.maxUsesPerBattle
        listOf(60f, 240f, 1_500f, 42_000f).forEach { maxHp ->
            val total = airSupportDamage(maxHp, fraction) * uses
            assertTrue(
                "maxHp=$maxHp: savas basina $uses kullanim tam canli dusmani oldurmemeli",
                total < maxHp,
            )
        }
        assertTrue("oran x kullanim < 1,0 olmali", fraction * uses < 1.0)
    }

    @Test
    fun airSupportDamageIsExactlyTheFractionOfMaxHealth() {
        assertEquals(45f, airSupportDamage(100f, 0.45), 1e-4f)
        assertEquals(450f, airSupportDamage(1_000f, 0.45), 1e-3f)
    }

    @Test
    fun airSupportDamageScalesWithEnemyHealthSoItNeverGoesStale() {
        // Oran (sabit hasar degil) olmasi kasitli: dusman cani x3,5 kalibre
        // edildiginde hava destegi kendiliginden olcegi tutar.
        val small = airSupportDamage(100f, EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION)
        val big = airSupportDamage(350f, EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION)
        assertEquals(3.5f, big / small, 1e-4f)
    }

    @Test
    fun nonPositiveFractionDealsNoDamageAtAll() {
        // Acil Tedarik ve Us Tamiri 0,0 oran tasir; motor tek bir `> 0` daliyla
        // hasar dongusunu tamamen atlar.
        assertEquals(0f, airSupportDamage(500f, 0.0), 0f)
        assertEquals(0f, airSupportDamage(500f, -0.3), 0f)
    }

    @Test
    fun onlyAirSupportCarriesADamageFraction() {
        // Etkinin hangi guclendiriciye ait oldugu `type`tan degil ALANDAN okunur;
        // motor tip listesi buyudugunde de dogru kalir.
        assertEquals(0.0, allowed(BoosterType.EMERGENCY_SUPPLY).airSupportDamageFraction, 0.0)
        assertEquals(0.0, allowed(BoosterType.BASE_REPAIR).airSupportDamageFraction, 0.0)
    }
}
