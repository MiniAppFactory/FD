package com.miniappfactory.frontlinedefender.game.model

import java.util.UUID

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
    var totalDamageDealt: Float = 0f
) {
    val stats: GameConfig.TowerStats get() = GameConfig.TOWER_SPECS[type]!!

    val range: Float get() = if (level == 1) stats.level1Range else stats.level2Range
    val damage: Float get() = if (level == 1) stats.level1Damage else stats.level2Damage
    val fireRate: Float get() = if (level == 1) stats.level1FireRate else stats.level2FireRate
    val upgradeCost: Int? get() = if (level == 1) stats.level2UpgradeCost else null
    val sellValue: Int get() = (totalInvestedGold * 0.70f).toInt()
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
    RAILGUN_BEAM,
    FROST_PULSE
}

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
    var progress: Float = 0f // 0.0 to 1.0 for interpolation
)

enum class EffectType {
    /** Namlu alevi — YONLU (fx_muzzle_flash_short, saga bakan sprite). */
    MUZZLE_FLASH,
    CANNON_EXPLOSION,
    RAIL_BEAM_BURST,
    FROST_WAVE,
    ENEMY_DEATH,
    /** Faz 3: kursun/isabet kivilcimi (fx_hit_spark) — eskiden MUZZLE_FLASH idi. */
    HIT_SPARK,
    /** Faz 3: toz/duman (fx_smoke_puff) — kule insasi, arac olumu. */
    SMOKE_PUFF,
    COIN_POPUP,
    DAMAGE_TEXT
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
     * Faz 3: yonlu sprite'lar (namlu alevi) icin bakis acisi. Ekran
     * koordinatlarinda atan2(dy, dx); 0 = sag. Radyal efektlerde kullanilmaz.
     */
    val angleRad: Float = 0f
)
