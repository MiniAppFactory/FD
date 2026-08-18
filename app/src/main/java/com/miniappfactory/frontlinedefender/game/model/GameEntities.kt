package com.miniappfactory.frontlinedefender.game.model

import java.util.UUID

/**
 * Isabet parlamasinin suresi, saniye.
 *
 * TEK KAYNAK: motor bu degeri `EnemyEntity.hitFlashTimerSeconds`e yazar,
 * renderer AYNI degerle 1'e normalize eder. Onceden iki dosyada da ciplak
 * `0.12f` yaziyordu; birini degistirip digerini unutmak parlamayi ya erken
 * kesilmis ya da hic sonmemis gosterirdi.
 */
const val HIT_FLASH_DURATION_SECONDS = 0.12f

data class TowerEntity(
    val id: String = UUID.randomUUID().toString(),
    val type: GameConfig.TowerType,
    val buildSpotId: Int,
    var posX: Float,
    var posY: Float,
    var level: Int = 1,
    var targetAngleRad: Float = 0f,
    var currentAngleRad: Float = 0f,
    var cooldownTimerSeconds: Float = 0f,
    var totalInvestedGold: Int,
    var targetingMode: GameConfig.TargetingMode = GameConfig.TargetingMode.FIRST,
    var recoilOffsetPx: Float = 0f,
    var killsCount: Int = 0,
    var totalDamageDealt: Float = 0f,

    // ------------------------------------------------------------------------
    // META YUKSELTME CARPANLARI (kalici ilerleme).
    //
    // Kule insa edilirken motor tarafindan BIR KEZ verilir. Carpanlari
    // getter'larin ICINE koymak bilincli: boylece hem simulasyon hem de kule
    // paneli (SelectedTowerInspector) AYNI degeri okur. Kullanim yerlerinde
    // carpmak, oyuncuya panelde 14 hasar gosterip aslinda 17 vurmak gibi
    // sessiz tutarsizliklar uretirdi.
    //
    // Varsayilan 1.0 / 0.70: yukseltme yoksa davranis oncekiyle BIREBIR ayni.
    // ------------------------------------------------------------------------
    val damageMultiplier: Float = 1f,
    val rangeMultiplier: Float = 1f,
    val salvageRate: Float = 0.70f,

    /**
     * Faz 13 / DECISIONS B5 — bu kulenin BU BOLUMDE ulasabilecegi en yuksek
     * kademe (kampanya kilidi, `GameConfig.maxTowerTier`).
     *
     * Meta carpanlari gibi motor tarafindan insa aninda BIR KEZ verilir. Neden
     * entity'de duruyor: panel "yukselt" butonunu bu kuleye BAKARAK ciziyor.
     * Kilit yalnizca motorda olsaydi panel kademe 3 butonunu bolum 5'te de
     * gosterir, oyuncu basar ve motor sessizce reddederdi — "teknik olarak
     * dogru ama tatminsiz" tam olarak bu.
     *
     * Varsayilan sinirsiz: kilidi bilmeyen bir cagiran (test, arac) kulenin
     * TAM merdivenini gorur, uydurma bir tavan gormez.
     */
    val tierCap: Int = Int.MAX_VALUE
) {
    val stats: GameConfig.TowerStats get() = GameConfig.TOWER_SPECS[type]!!

    /**
     * Bu kulenin cikabilecegi son kademe: veri merdiveni ile kampanya kilidinin
     * KUCUGU. "Son kademe mi" sorusunun TEK kaynagi — sabit bir 2 ya da 3 yok.
     */
    val maxTier: Int get() = minOf(stats.maxTier, tierCap)

    /** Bu kulenin su anki kademesinin denge satiri. */
    val currentTier: GameConfig.TowerTier get() = stats.tier(level)

    /**
     * Menzil **1920 REFERANS TUVALINDE** (DECISIONS B3) — ham canvas px DEGIL.
     * Karsilastirma/panel/test icin dogru deger budur; oynanista mesafe
     * kiyaslamak icin [rangePx] kullanilir.
     */
    val range: Float
        get() = currentTier.range * rangeMultiplier

    /**
     * Menzil CANVAS px cinsinden.
     *
     * Faz 10 duzeltmesi: menzil ve dusman hizi eskiden dogrudan canvas px olarak
     * kullaniliyordu. Oynanis dikdortgeni cihaza gore 1800 px (Galaxy S8) ile
     * 2560 px (tablet) arasinda degistigi icin **ayni kule tablette haritanin
     * %30 daha kucuk bir bolumunu kapatiyordu**: tablet ve telefon ayni oyunu
     * oynamiyordu. Artik denge degerleri referans tuvalde tanimli ve cizim/
     * simulasyon aninda `renderScale` ile olceklenir.
     */
    fun rangePx(renderScale: Float): Float = range * renderScale

    val damage: Float
        get() = currentTier.damage * damageMultiplier
    val fireRate: Float get() = currentTier.fireRate

    /**
     * Bir sonraki kademenin bedeli; SON kademede `null`.
     *
     * `null` iki ayri sebeple gelebilir ve ikisi de ayni cevabi hak eder
     * ("MAKS"): merdivenin sonuna gelinmistir, ya da kampanya kilidi
     * ([tierCap]) o kademeyi henuz acmamistir.
     */
    val upgradeCost: Int? get() = if (level >= maxTier) null else stats.upgradeCostFrom(level)

    /**
     * Satis geri odemesi. **Yatirimin tamamindan** hesaplanir, kademeden degil:
     * her yukseltme `totalInvestedGold`e eklendigi icin kademe 3 muhasebeye
     * kendiliginden dahil olur. `salvageRate` her zaman 1.0'in altinda kaldigi
     * surece (meta tavani 0.70) "yukselt sonra sat" ASLA kar etmez —
     * `EntityDerivedStatsTest` bunu her kademe ve her salvage rank'i icin
     * kilitler.
     */
    val sellValue: Int get() = (totalInvestedGold * salvageRate).toInt()
}

data class SlowStatus(
    val factor: Float,
    var durationRemainingSeconds: Float
)

data class EnemyEntity(
    val id: String = UUID.randomUUID().toString(),
    val type: GameConfig.EnemyType,
    var posX: Float,
    var posY: Float,
    /**
     * Faz 4: catallanan haritalarda (1, 2, 4, 11) bu dusmanin izledigi rotanin
     * indeksi — `GameEngine.scaledRoutes` icine bakar. Tek rotali haritalarda
     * her zaman 0. Atama SEED'LI ve deterministiktir (GameConfig.ROUTE_RNG_SEED_BASE).
     */
    val routeIndex: Int = 0,
    var currentWayPointIndex: Int = 0,
    var distanceTraveledPx: Float = 0f,
    var hp: Float,
    val maxHp: Float,
    val baseSpeed: Float,
    val armor: Float,
    val rewardGold: Int,
    val radius: Float,
    var activeSlow: SlowStatus? = null,
    var hitFlashTimerSeconds: Float = 0f,
    var rotationAngleRad: Float = 0f
) {
    val stats: GameConfig.EnemyStats get() = GameConfig.ENEMY_SPECS[type]!!
    val currentSpeed: Float get() = activeSlow?.let { baseSpeed * (1f - it.factor) } ?: baseSpeed
    val isDead: Boolean get() = hp <= 0f
}

enum class ProjectileType {
    BULLET,
    CANNON_SHELL,
    /**
     * Faz 10: eski `RAILGUN_BEAM` (aninda varan camgobegi isin) FUZE oldu.
     * Sprite `spr_fx_missile` zaten paketteydi ve kule sprite'i bir fuze
     * rampasi, sesi `sfx_missile_launch` — mermi tek uyumsuz parcaydi.
     *
     * Oynanis farki KOZMETIK DEGIL: isin aninda varirdi, fuze YOL ALIR
     * (0.69 sn). Hedef fuze havadayken olurse fuze BOSA GIDER — hicbir
     * yonlendirme yapilmaz. Bu, ANTI_TANK rolunun bilincli zayifligi.
     */
    MISSILE,
    FROST_PULSE
}

/**
 * @param splashRadius Cannon patlamasi — **CANVAS px** (fire aninda referans
 *   tuvalden cevrilir). > 0 ise mermi splash mantigini kullanir ve zirhi
 *   bypass eder (DECISIONS B2).
 * @param impactRadius Fuze carpma alani — CANVAS px. Yalnizca ANTI_ARMOR.
 * @param impactDamageFraction Fuzenin ikincil hedeflere gecen hasar orani.
 * @param slowPulseRadius Cryo darbesinin sogutma yaricapi — CANVAS px.
 *   Yalnizca SLOW. Darbe bu yaricaptaki HERKESI yavaslatir.
 */
data class ProjectileEntity(
    val id: String = UUID.randomUUID().toString(),
    val type: ProjectileType,
    var posX: Float,
    var posY: Float,
    val startX: Float,
    val startY: Float,
    val targetEnemyId: String?,
    val targetX: Float,
    val targetY: Float,
    val damage: Float,
    val speed: Float,
    val splashRadius: Float,
    val armorPierce: Float,
    val slowFactor: Float,
    val slowDuration: Float,
    val towerType: GameConfig.TowerType,
    val impactRadius: Float = 0f,
    val impactDamageFraction: Float = 0f,
    val slowPulseRadius: Float = 0f,
    var progress: Float = 0f // 0.0 to 1.0 for interpolation
)

enum class EffectType {
    /** Namlu alevi — YONLU (fx_muzzle_flash_short, saga bakan sprite). */
    MUZZLE_FLASH,
    CANNON_EXPLOSION,
    /** Faz 10: fuze carpmasi (eskiden RAIL_BEAM_BURST — isin patlamasi). */
    MISSILE_IMPACT,
    FROST_WAVE,
    ENEMY_DEATH,
    /** Faz 3: kursun/isabet kivilcimi (fx_hit_spark) — eskiden MUZZLE_FLASH idi. */
    HIT_SPARK,
    /** Faz 3: toz/duman (fx_smoke_puff) — kule insasi, arac olumu. */
    SMOKE_PUFF,
    /**
     * Faz 10: cryo darbesinin ALAN halkasi. FROST_WAVE'den ayri, cunku o
     * yukseltme suslemesi olarak sabit boyutta kullaniliyor; bu halka
     * `radiusPx` ile GERCEK sogutma alanini cizer.
     */
    FROST_PULSE_RING,
    COIN_POPUP,
    DAMAGE_TEXT,

    /**
     * Faz 14 - ZINCIR (kill-streak) kademe atlama patlamasi.
     *
     * Her oldurmede DEGIL, yalnizca zincir bir kademe TIRMANDIGINDA uretilir
     * (bkz. ComboTracker). Boylece 18 dusmanlik bir dalgada ekrana 18 degil
     * en fazla 4 tane cikar: tirmanma hissi verir, ekrani bogmaz.
     *
     * Renk/olcek [VisualEffect.tier] uzerinden surulur; ses, sarsinti ve
     * hit stop ile AYNI KAREDE tetiklenir.
     */
    COMBO_BURST
}

data class VisualEffect(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType,
    val posX: Float,
    val posY: Float,
    var ageSeconds: Float = 0f,
    val maxAgeSeconds: Float,
    val text: String? = null,
    val scale: Float = 1f,
    /**
     * Faz 10: efektin GERCEK oynanis yaricapi, CANVAS px. 0 = sprite kendi
     * nominal boyutunu kullanir.
     *
     * Neden gerekli: patlama ve cryo darbesi artik gercek etki alanina gore
     * cizilir. Onceden `scale = splashRadius / 35f` gibi bir sihirli sayi
     * vardi, yani splash yaricapi degistiginde gorsel ile hasar alani sessizce
     * ayrisiyordu — oyuncu patlamanin nereye vurdugunu GORSELDEN ogrenemezdi.
     */
    val radiusPx: Float = 0f,
    /**
     * Faz 3: yonlu sprite'lar (namlu alevi) icin bakis acisi. Ekran
     * koordinatlarinda atan2(dy, dx); 0 = sag. Radyal efektlerde kullanilmaz.
     */
    val angleRad: Float = 0f,
    /**
     * Faz 14 - zincir kademesi (0 = zincir yok). Yuzen yazinin RENGI ve
     * OLCEGI bundan turer: kademe yukseldikce yazi buyur ve rengi soguktan
     * sicaga kayar. Uc kanal (olcek / renk / ses) birlikte tirmanir.
     */
    val tier: Int = 0
)
