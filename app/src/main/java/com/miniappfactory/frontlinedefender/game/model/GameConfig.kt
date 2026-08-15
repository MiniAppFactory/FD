package com.miniappfactory.frontlinedefender.game.model

/**
 * Centralized game balance configuration.
 * All balance values, tower specs, enemy specs, and wave definitions are stored here.
 */
object GameConfig {
    const val INITIAL_BASE_LIVES = 20
    const val INITIAL_GOLD = 150

    // Game loop parameters
    const val PREPARATION_TIME_SECONDS = 10
    const val BASE_REACHED_PENALTY_LIVES = 1

    // ------------------------------------------------------------------------
    // Faz 3 — RENDER / ETKILESIM SABITLERI
    //
    // Bunlar DENGE degeri DEGILDIR. Hasar / menzil / HP / maliyet sayilarina
    // dokunulmadi. Buradaki her deger "ekranda ne kadar yer kaplar" ya da
    // "sprite hangi yone bakiyor" sorusunun cevabi ve renderer'a GOMULMEZ.
    //
    // Tum gorsel boyutlar 1920x1080 REFERANS TUVALINDE tanimlidir
    // (DECISIONS.md B3). Cizim aninda olcek = oynanis dikdortgeninin
    // genisligi / REFERENCE_WIDTH.
    // ------------------------------------------------------------------------
    const val REFERENCE_WIDTH = 1920f
    const val REFERENCE_HEIGHT = 1080f

    /** bg_level_01.webp 1920x1081 -> 1.7761. Letterbox hesabi bunu kullanir. */
    const val MAP_ASPECT_RATIO = 1920f / 1081f

    /**
     * Harita bitmap'i oynanis dikdortgeninden bu kadar px tasarak cizilir.
     * Sarsinti (maks ~+-5 px) sirasinda kenarda koyu serit gorunmesini onler.
     * Oynanis koordinatlari tasmayan gercek dikdortgene gore hesaplanir.
     */
    const val SHAKE_OVERSCAN_REF_PX = 10f

    /**
     * Haritanin UST %10'u dekoratiftir: orada ne cizili yol ne build pad var
     * (olculdu — en ustteki pad merkezi normY=0.1655, yolun en ustu 0.1933).
     * Bu bant bilincli olarak ust HUD seridinin ALTINA kaydirilir.
     *
     * Neden: HUD 56 dp opak bir serit ve cihazda ust iki pad'in yarisini
     * kapatiyordu (docs/device_evidence/21_faz3_harita.png). Alternatif olan
     * "oynanis alanini HUD'un altina sigdir" haritayi %14 kucultuyordu; bu
     * yontem yalnizca %4 kucultuyor. game-asset-draw skill'inin onerdigi
     * "harita kenarina oynanista kullanilmayan dolgu bandi" cozumu.
     */
    const val MAP_SAFE_TOP_FRAC = 0.10f

    /** HUD seridi olculemezse kullanilan varsayilan (HUDOverlay ~56 dp). */
    const val HUD_TOP_INSET_DP = 56f

    /** true yapilirsa waypoint cizgisi ve pad merkezleri debug icin cizilir. */
    const val DEBUG_DRAW_PATH = false

    /** Dokunma yakalama yaricapi (referans tuvalde). Denge degil, etkilesim. */
    const val TAP_RADIUS_REF_PX = 46f

    /** Bos build pad secilince gosterilen on-izleme menzili (canvas px). */
    const val BUILD_PREVIEW_RANGE_PX = 170f

    /** Letterbox / tuval zemini. */
    const val LETTERBOX_COLOR = 0xFF0B0E08

    /**
     * SPRITE YONU — olculdu, tahmin edilmedi (bkz. docs/ASSET_INTEGRATION.md).
     * atan2(dy, dx) ekran koordinatlarinda 0 derece = SAG, +90 derece = ASAGI.
     *
     * enemy_infantry / fast_soldier / jeep / tank: hepsi ASAGI (guney) bakiyor.
     * fx_*_tracer / _shell / _projectile / muzzle_flash: hepsi SAGA bakiyor.
     */
    const val ENEMY_SPRITE_BASE_ANGLE_DEG = 90f
    const val PROJECTILE_SPRITE_BASE_ANGLE_DEG = 0f

    /**
     * @param widthRefPx 1920 referans tuvalde sprite'in cizilecek genisligi.
     *   Haritadaki cizili build pad ~92 px oldugu icin kule 112 px: pad'i
     *   tamamen kapatir, boylece donen sekizgen taban ile sabit sekizgen pad
     *   arasinda aci uyusmazligi gorunmez.
     * @param pivotYFrac Sprite yuksekliginin hangi orani kulenin oturma /
     *   donme eksenidir. Tuvalin merkezi DEGIL.
     * @param rotates Sprite yonlu top-down bir taret mi? machine_gun ve
     *   heavy_cannon namlusu asagi bakan top-down cizim -> doner.
     *   missile_launcher (dikey ramp) ve energy_slow (radyal cukur anten)
     *   3/4 on gorunum -> dondurmek tabani ters cevirir, DONMEZ. Bu kulelerde
     *   nisan alma hissi namlu alevinin hedef yonunde cikmasiyla verilir.
     */
    data class TowerSpriteSpec(
        val widthRefPx: Float,
        val pivotYFrac: Float,
        val rotates: Boolean,
        val baseAngleDeg: Float
    )

    val TOWER_SPRITES: Map<TowerType, TowerSpriteSpec> = mapOf(
        TowerType.MACHINE_GUN to TowerSpriteSpec(112f, 0.47f, rotates = true, baseAngleDeg = 90f),
        TowerType.CANNON to TowerSpriteSpec(112f, 0.48f, rotates = true, baseAngleDeg = 90f),
        TowerType.ANTI_ARMOR to TowerSpriteSpec(112f, 0.72f, rotates = false, baseAngleDeg = 0f),
        TowerType.SLOW to TowerSpriteSpec(116f, 0.63f, rotates = false, baseAngleDeg = 0f)
    )

    /**
     * Faz 4 / DECISIONS B1: yeni dusman tipleri icin YENI PNG URETILMEZ. Iki yeni
     * tip mevcut sprite'lari yeniden kullanir ve **renk + eklenti isaretiyle**
     * ayrisir. Tint tek basina birakilmaz (game-art: "deger farki da olmali"),
     * `overlayBadge` sekil ayrimini saglar.
     *
     * @param widthRefPx 1920 referans tuvalde dusman sprite genisligi.
     * @param baseSprite Hangi mevcut bitmap cizilir. Kendisi olan tipler icin
     *   kendi degeri; turetilmis tipler icin kaynak tip.
     * @param tintArgb 0 = tint yok. GameCanvas bunu ColorFilter olarak uygular.
     * @param tintStrength 0..1 karisim orani.
     * @param overlayBadge Silueti degistiren ek cizim.
     */
    data class EnemySpriteSpec(
        val widthRefPx: Float,
        val pivotYFrac: Float,
        val baseSprite: EnemyType,
        val tintArgb: Long = 0L,
        val tintStrength: Float = 0f,
        val overlayBadge: EnemyBadge = EnemyBadge.NONE
    )

    /** Dusman sprite'inin uzerine cizilen ayirt edici isaret (bkz. EnemySpriteSpec). */
    enum class EnemyBadge {
        NONE,
        /** SHIELDED_TROOPER: govdeyi saran celik halka. */
        SHIELD_RING,
        /** COMMAND_TANK: taretten cikan komuta flamasi. */
        COMMAND_PENNANT
    }

    val ENEMY_SPRITES: Map<EnemyType, EnemySpriteSpec> = mapOf(
        EnemyType.INFANTRY to EnemySpriteSpec(46f, 0.44f, EnemyType.INFANTRY),
        EnemyType.FAST_SOLDIER to EnemySpriteSpec(42f, 0.47f, EnemyType.FAST_SOLDIER),
        EnemyType.ARMORED_VEHICLE to EnemySpriteSpec(58f, 0.50f, EnemyType.ARMORED_VEHICLE),
        EnemyType.TANK to EnemySpriteSpec(72f, 0.50f, EnemyType.TANK),
        // Celik-mavi tint + kalkan halkasi, piyade sprite'i uzerine.
        EnemyType.SHIELDED_TROOPER to EnemySpriteSpec(
            widthRefPx = 50f,
            pivotYFrac = 0.44f,
            baseSprite = EnemyType.INFANTRY,
            tintArgb = 0xFF6E8FB5,
            tintStrength = 0.38f,
            overlayBadge = EnemyBadge.SHIELD_RING
        ),
        // Altin/kizil tint + komuta flamasi, tank sprite'i uzerine.
        EnemyType.COMMAND_TANK to EnemySpriteSpec(
            widthRefPx = 96f,
            pivotYFrac = 0.50f,
            baseSprite = EnemyType.TANK,
            tintArgb = 0xFFC98A2E,
            tintStrength = 0.32f,
            overlayBadge = EnemyBadge.COMMAND_PENNANT
        )
    )

    // Efekt / mermi sprite boyutlari (referans tuvalde, px)
    const val FX_MUZZLE_FLASH_REF_PX = 56f
    const val FX_TRACER_REF_PX = 64f
    const val FX_CANNON_SHELL_REF_PX = 44f
    const val FX_MISSILE_REF_PX = 50f
    const val FX_HIT_SPARK_REF_PX = 52f
    const val FX_SMALL_EXPLOSION_REF_PX = 96f
    const val FX_LARGE_EXPLOSION_REF_PX = 150f
    const val FX_SMOKE_PUFF_REF_PX = 78f
    const val FX_BUILD_PAD_REF_PX = 98f
    const val FX_FROST_RING_REF_PX = 96f

    /** Namlu alevinin kule merkezinden namlu ucuna ofseti (referans tuvalde). */
    const val MUZZLE_OFFSET_REF_PX = 44f

    /** Bos build pad isaretinin bekleme / secili alfasi. */
    const val BUILD_PAD_IDLE_ALPHA = 0.55f
    const val BUILD_PAD_SELECTED_ALPHA = 1.0f

    enum class TowerType {
        MACHINE_GUN,
        CANNON,
        ANTI_ARMOR,
        SLOW
    }

    enum class EnemyType {
        INFANTRY,
        FAST_SOLDIER,
        ARMORED_VEHICLE,
        TANK,
        /** Faz 4 / DECISIONS B1: kursuna dirençli, patlamaya zayif piyade. */
        SHIELDED_TROOPER,
        /** Faz 4 / DECISIONS B1: boss. Act I finalinde (L11) ilk kez cikar. */
        COMMAND_TANK
    }

    enum class TargetingMode {
        FIRST,      // Closest to base
        LAST,       // Furthest from base
        STRONGEST,  // Highest current HP
        WEAKEST     // Lowest current HP
    }

    data class TowerStats(
        val type: TowerType,
        val name: String,
        val description: String,
        val buildCost: Int,
        val level1Range: Float,       // In game units (pixels relative to map scale)
        val level1Damage: Float,
        val level1FireRate: Float,    // Seconds between shots
        val level2UpgradeCost: Int,
        val level2Range: Float,
        val level2Damage: Float,
        val level2FireRate: Float,
        val splashRadius: Float = 0f,  // > 0 for Cannon
        val armorPierce: Float = 0f,   // 0.0 to 1.0 for Anti-Armor
        val slowFactor: Float = 0f,    // 0.0 to 1.0 (e.g. 0.5 = 50% speed) for Slow Tower
        val slowDuration: Float = 0f   // Duration of slow in seconds
    )

    data class EnemyStats(
        val type: EnemyType,
        val name: String,
        val maxHp: Float,
        val baseSpeed: Float,         // Speed along path
        val armor: Float,             // Damage mitigation factor (0.0 = no armor, 0.6 = 60% reduction to normal ammo)
        val rewardGold: Int,
        val sizeRadius: Float,
        /**
         * Faz 4 / DECISIONS B2. **YALNIZCA splash hasarina** uygulanan carpan;
         * dogrudan isabete uygulanmaz. Splash bileseni ayrica zirhi BYPASS eder
         * (GameEngine.applyDamageToEnemy, tek `if`).
         *
         * Gerekce: Cannon'in armorPierce'i 0 oldugu icin zirhli hedefe karsi en
         * kotu secenekti; bu da "kursuna direncli / patlamaya zayif" dusman
         * tasarimini imkansiz kiliyor ve Cannon'i gec oyunda olu birakiyordu.
         */
        val splashVulnerability: Float = 1f
    )

    data class WaveEnemySpawn(
        val enemyType: EnemyType,
        val delaySeconds: Float
    )

    data class WaveData(
        val waveIndex: Int,
        val title: String,
        val spawns: List<WaveEnemySpawn>
    )

    // Tower Definitions
    val TOWER_SPECS: Map<TowerType, TowerStats> = mapOf(
        TowerType.MACHINE_GUN to TowerStats(
            type = TowerType.MACHINE_GUN,
            name = "Gatling Gun",
            description = "Rapid firing, low-medium damage. Excellent against infantry.",
            buildCost = 60,
            level1Range = 160f,
            level1Damage = 14f,
            level1FireRate = 0.16f,
            level2UpgradeCost = 65,
            level2Range = 190f,
            level2Damage = 26f,
            level2FireRate = 0.12f
        ),
        TowerType.CANNON to TowerStats(
            type = TowerType.CANNON,
            name = "Heavy Cannon",
            description = "High splash damage explosion. Destroys grouped enemies.",
            buildCost = 90,
            level1Range = 180f,
            level1Damage = 50f,
            level1FireRate = 1.1f,
            level2UpgradeCost = 90,
            level2Range = 210f,
            level2Damage = 95f,
            level2FireRate = 0.95f,
            splashRadius = 65f
        ),
        TowerType.ANTI_ARMOR to TowerStats(
            type = TowerType.ANTI_ARMOR,
            name = "Railgun",
            description = "Long range, high-velocity armor penetrating beam.",
            buildCost = 110,
            level1Range = 240f,
            level1Damage = 85f,
            level1FireRate = 1.4f,
            level2UpgradeCost = 110,
            level2Range = 280f,
            level2Damage = 160f,
            level2FireRate = 1.2f,
            armorPierce = 0.85f
        ),
        TowerType.SLOW to TowerStats(
            type = TowerType.SLOW,
            name = "Frost Field",
            description = "Emits cryo pulses that slow enemy movement by up to 50%.",
            buildCost = 80,
            level1Range = 150f,
            level1Damage = 6f,
            level1FireRate = 0.7f,
            level2UpgradeCost = 75,
            level2Range = 180f,
            level2Damage = 12f,
            level2FireRate = 0.55f,
            slowFactor = 0.50f,
            slowDuration = 2.5f
        )
    )

    // Enemy Definitions
    val ENEMY_SPECS: Map<EnemyType, EnemyStats> = mapOf(
        EnemyType.INFANTRY to EnemyStats(
            type = EnemyType.INFANTRY,
            name = "Infantry Squad",
            maxHp = 75f,
            baseSpeed = 65f,
            armor = 0.0f,
            rewardGold = 12,
            sizeRadius = 14f
        ),
        EnemyType.FAST_SOLDIER to EnemyStats(
            type = EnemyType.FAST_SOLDIER,
            name = "Scout Runner",
            maxHp = 45f,
            baseSpeed = 115f,
            armor = 0.0f,
            rewardGold = 15,
            sizeRadius = 12f
        ),
        EnemyType.ARMORED_VEHICLE to EnemyStats(
            type = EnemyType.ARMORED_VEHICLE,
            name = "Armored Car",
            maxHp = 220f,
            baseSpeed = 50f,
            armor = 0.55f, // 55% normal damage mitigation
            rewardGold = 28,
            sizeRadius = 20f
        ),
        EnemyType.TANK to EnemyStats(
            type = EnemyType.TANK,
            name = "Heavy Tank",
            maxHp = 580f,
            baseSpeed = 32f,
            armor = 0.70f, // 70% normal damage mitigation
            rewardGold = 60,
            sizeRadius = 26f
        ),
        // --------------------------------------------------------------------
        // Faz 4 — DECISIONS B1. Degerler LEVEL_DESIGN.md C.1 on kosul patch'inden
        // AYNEN alindi; WaveMetrics.AEHP tablosu bu sayilara gore turetilmistir,
        // degistirilirse WaveDefinitionsTest kirilir.
        // --------------------------------------------------------------------
        EnemyType.SHIELDED_TROOPER to EnemyStats(
            type = EnemyType.SHIELDED_TROOPER,
            name = "Shielded Trooper",
            maxHp = 150f,
            baseSpeed = 58f,
            armor = 0.62f,             // kursun neredeyse ise yaramaz
            rewardGold = 22,
            sizeRadius = 16f,
            splashVulnerability = 1.6f // ...ama patlama zirhi bypass eder ve 1.6x vurur
        ),
        EnemyType.COMMAND_TANK to EnemyStats(
            type = EnemyType.COMMAND_TANK,
            name = "Command Tank",
            maxHp = 2600f,
            baseSpeed = 30f,
            armor = 0.72f,
            rewardGold = 180,
            sizeRadius = 38f
        )
    )

    // ========================================================================
    // Faz 4 — KAMPANYA TABLOSU
    //
    // Bolum <-> harita eslemesi TEK BIR YERDE burada durur. LEVEL_DESIGN.md B
    // tablosu revize edilirse yalnizca `CAMPAIGN` guncellenir; motor, bolum
    // secme ekrani ve testler bunu okur.
    // ========================================================================

    /** Harita uzerine binen tamamen KOZMETIK katman (DECISIONS: oynanisa sifir etki). */
    enum class MapOverlay { NONE, NIGHT }

    /**
     * Biyom varyanti (docs/BIOME_VARIANTS.md). Alan simdi TANIMLI ama recolor
     * uygulamasi sonraki faza kaldi; `TEMPERATE` disindaki degerler su an yalnizca
     * `overlay` ile birlikte niyet beyanidir.
     */
    enum class Biome { TEMPERATE, NIGHT, WINTER, DESERT }

    /**
     * Bir kampanya bolumunun dalga DISI konfigurasyonu (LEVEL_DESIGN.md C.4).
     *
     * @param mapId 1..11 — `LevelGeometry.ALL_MAPS` icindeki OLCULMUS geometri.
     * @param deploymentCost Bolumu acmak icin gereken Coin (meta para birimi).
     *   0 = bastan acik. GDD B.2 tablosu.
     * @param disabledPadIds Act II krater kisiti. **DONDURULMUS** liste
     *   (LEVEL_DESIGN.md F.4): calisma aninda algoritma KOSMAZ, cunku geometri
     *   degisirse sessizce farkli bir bulmaca uretir ve oyuncunun ogrendigi bolum
     *   bozulur. Uretim: docs/CAMPAIGN_INTEGRATION.md.
     */
    data class LevelSpec(
        val levelId: Int,
        val mapId: Int,
        val act: Int,
        val deploymentCost: Int,
        val startingSupply: Int = INITIAL_GOLD,
        val maxBaseLives: Int = INITIAL_BASE_LIVES,
        val disabledPadIds: List<Int> = emptyList(),
        val overlay: MapOverlay = MapOverlay.NONE,
        val biome: Biome = Biome.TEMPERATE
    ) {
        /** Dalga sayisi TEK KAYNAKTAN gelir; burada kopyalanmaz. Getter = lazy. */
        val waveCount: Int get() = WaveDefinitions.waveCount(levelId)

        /** Bolum secme ekraninda gorunen ad. Ingilizce (lokalizasyon ajani alacak). */
        val displayName: String
            get() = MAP_NAMES_EN[mapId] ?: "Sector $mapId"
    }

    /** Olculmus 11 haritanin Ingilizce adlari (GEOMETRY_REPORT.md 0 tablosu). */
    val MAP_NAMES_EN: Map<Int, String> = mapOf(
        1 to "Meadow Pass",
        2 to "Waterfall Woods",
        3 to "Dark Ravine",
        4 to "Lakeside Ring",
        5 to "Marshland",
        6 to "Ravine Crossing",
        7 to "Open Plain",
        8 to "Deep Forest",
        9 to "Rampart Slope",
        10 to "River Fork",
        11 to "Village Outskirts"
    )

    /**
     * ---------------------------------------------------------------------
     * APK'DA GERCEKTEN BULUNAN harita bitmap'leri.
     *
     * Su an `res/drawable-nodpi/` altinda YALNIZCA `bg_level_01.webp` var;
     * diger 10 harita hâlâ kaynak PNG olarak `copied items/` icinde bekliyor.
     * Bir bolum kendi bitmap'i olmadan yuklenirse geometri ile CIZILI harita
     * ayrisir (dusmanlar boyali yolun disinda yurur, pad'ler boslukta durur) —
     * yani oynanamaz. Bu yuzden motor eksik bitmap'te `MAP_FALLBACK_ID`
     * geometrisine duser: harita ile oynanis HER ZAMAN tutarli kalir.
     *
     * 10 bitmap `drawable-nodpi`'ye girdiginde burasi `(1..11).toSet()` olur,
     * motorda baska degisiklik GEREKMEZ.
     * Detay + donusum komutu: docs/CAMPAIGN_INTEGRATION.md.
     * ---------------------------------------------------------------------
     */
    // Faz 4b: 11 harita bitmap'i de `drawable-nodpi/bg_level_01..11.webp` olarak
    // pakete girdi (1920 px, WebP q80, toplam 3.34 MB). Yedek harita mekanizmasi
    // yerinde kaliyor: ileride bir bitmap eksik kalirsa oynanis ile cizili harita
    // ayrismaz, sadece o bolum yedege duser.
    val SHIPPED_MAP_IDS: Set<Int> = (1..11).toSet()
    const val MAP_FALLBACK_ID = 1

    /** `bg_level_XX.webp` kaynak adindaki XX icin. Bkz. GameSprites.mapResFor(). */
    const val MAP_ID_MIN = 1
    const val MAP_ID_MAX = 11

    /**
     * Yildiz esikleri **YUZDE** cinsindendir, mutlak can degil. Us cani bolume ve
     * meta yukseltmelere gore degistigi icin (`LevelSpec.maxBaseLives`, GDD: 20 ->
     * meta ile 30) mutlak esik yanlis yildiz verirdi. GDD B.3: yildiz = kalan us
     * cani yuzdesi.
     */
    const val STAR3_LIVES_FRACTION = 0.90f
    const val STAR2_LIVES_FRACTION = 0.50f

    /**
     * Catallanan haritalarda (1, 2, 3, 4, 11) rota atamasinin seed'i.
     * RNG render'dan AYRI ve seed'li: ayni bolum her zaman ayni rota dizisini
     * uretir, yani replay/test/denge dogrulamasi mumkun kalir.
     */
    const val ROUTE_RNG_SEED_BASE = 0x5F1D3A7L

    /**
     * Act II krater kisiti — LEVEL_DESIGN.md F.3 algoritmasi geometri uzerinde
     * BIR KEZ kosturuldu ve F.4 uyarinca donduruldu. Uretici betik ve dogrulama
     * tablosu: docs/CAMPAIGN_INTEGRATION.md.
     *
     * Hepsi D1 (%25-40 devre disi), D2 (kalan >= max(6, 0.60x toplam)),
     * D3 (her dolu bolgede >=1), D4 (900 ref-px'lik kapsamasiz yol parcasi yok),
     * D6 (Z5'te >=2, Z5 toplami >=2 ise) kisitlarini SAGLIYOR.
     *
     * DIKKAT: `CAMPAIGN` bunu initializer'inda okuyor. Kotlin object govdesi
     * yukaridan asagi kosar, bu yuzden burasi CAMPAIGN'DEN ONCE durmak ZORUNDA.
     */
    private val FROZEN_DISABLED_PADS: Map<Int, List<Int>> = mapOf(
        12 to listOf(1, 4, 5),            // harita 11 · 10 pad -> 7 kalir (%30)
        13 to listOf(3, 6, 9, 12, 15),    // harita 10 · 17 pad -> 12 kalir (%29)
        14 to listOf(1, 3, 4),            // harita 09 · 12 pad ->  9 kalir (%25)
        15 to listOf(1, 4, 7, 10),        // harita 08 · 11 pad ->  7 kalir (%36)
        16 to listOf(3, 6, 9),            // harita 07 · 11 pad ->  8 kalir (%27)
        17 to listOf(2, 5, 8),            // harita 06 · 10 pad ->  7 kalir (%30)
        18 to listOf(1, 4, 7, 10),        // harita 05 · 12 pad ->  8 kalir (%33)
        19 to listOf(3, 6, 9, 12, 15),    // harita 04 · 16 pad -> 11 kalir (%31)
        20 to listOf(2, 5, 8, 11),        // harita 03 · 12 pad ->  8 kalir (%33)
        21 to listOf(1, 4, 7, 10),        // harita 02 · 13 pad ->  9 kalir (%31)
        22 to listOf(3, 5, 9)             // harita 01 · 10 pad ->  7 kalir (%30)
    )

    /** Bolum no -> konfigurasyon. Sira LEVEL_DESIGN.md B tablosu ile birebir. */
    val CAMPAIGN: List<LevelSpec> = buildList {
        // ---- ACT I: harita 01 -> 11, kisitsiz pad, gunduz -------------------
        val actIDeploymentCost = listOf(0, 0, 0, 0, 0, 0, 100, 110, 120, 130, 140)
        for (lv in 1..11) {
            add(
                LevelSpec(
                    levelId = lv,
                    mapId = lv,
                    act = 1,
                    deploymentCost = actIDeploymentCost[lv - 1]
                )
            )
        }
        // ---- ACT II: harita 11 -> 01 TERS SIRA, gece overlay, pad kisiti ----
        // "Ters sira" = harita SIRASI (DECISIONS). Spawn/us/yol yonu Act I ile
        // birebir ayni; gece overlay tamamen kozmetik.
        val actIIDeploymentCost = listOf(150, 165, 180, 195, 210, 225, 240, 255, 270, 300, 350)
        for (i in 0..10) {
            val lv = 12 + i
            add(
                LevelSpec(
                    levelId = lv,
                    mapId = 11 - i,
                    act = 2,
                    deploymentCost = actIIDeploymentCost[i],
                    disabledPadIds = FROZEN_DISABLED_PADS[lv] ?: emptyList(),
                    overlay = MapOverlay.NIGHT,
                    biome = Biome.NIGHT
                )
            )
        }
    }

    const val CAMPAIGN_LEVEL_COUNT = 22

    fun levelSpec(levelId: Int): LevelSpec =
        CAMPAIGN.firstOrNull { it.levelId == levelId } ?: CAMPAIGN.first()

    /**
     * ESKI 6 dalgalik tek-bolum listesi.
     *
     * Faz 4'ten itibaren dalgalarin kaynagi `WaveDefinitions.CAMPAIGN`'dir
     * (22 bolum x 259 dalga). Bu liste SILINMEDI cunku `HUDOverlay.kt` hâlâ
     * `GameConfig.WAVES.size` okuyor (bkz. docs/CAMPAIGN_INTEGRATION.md — HUD
     * `gameEngine.totalWaves` akisina gecirilecek).
     */
    @Deprecated(
        message = "Bolum basina dalga icin WaveDefinitions.wavesFor(levelId) / " +
            "GameEngine.totalWaves kullan.",
        level = DeprecationLevel.WARNING
    )
    val WAVES: List<WaveData> = listOf(
        // Wave 1: Basic infantry introduction
        WaveData(
            waveIndex = 1,
            title = "Infiltration",
            spawns = List(8) { WaveEnemySpawn(EnemyType.INFANTRY, 1.2f) }
        ),
        // Wave 2: More infantry
        WaveData(
            waveIndex = 2,
            title = "Infantry Rush",
            spawns = List(14) { WaveEnemySpawn(EnemyType.INFANTRY, 0.9f) }
        ),
        // Wave 3: Fast soldiers introduced
        WaveData(
            waveIndex = 3,
            title = "Fast Recon",
            spawns = List(10) { WaveEnemySpawn(EnemyType.FAST_SOLDIER, 0.8f) }
        ),
        // Wave 4: Mixed infantry and fast soldiers
        WaveData(
            waveIndex = 4,
            title = "Combined Assault",
            spawns = buildList {
                repeat(6) {
                    add(WaveEnemySpawn(EnemyType.INFANTRY, 0.8f))
                    add(WaveEnemySpawn(EnemyType.FAST_SOLDIER, 0.6f))
                }
            }
        ),
        // Wave 5: Armored vehicles introduced
        WaveData(
            waveIndex = 5,
            title = "Armored Column",
            spawns = buildList {
                add(WaveEnemySpawn(EnemyType.ARMORED_VEHICLE, 1.5f))
                add(WaveEnemySpawn(EnemyType.INFANTRY, 0.8f))
                add(WaveEnemySpawn(EnemyType.ARMORED_VEHICLE, 1.5f))
                add(WaveEnemySpawn(EnemyType.FAST_SOLDIER, 0.7f))
                add(WaveEnemySpawn(EnemyType.ARMORED_VEHICLE, 1.5f))
                add(WaveEnemySpawn(EnemyType.ARMORED_VEHICLE, 1.5f))
            }
        ),
        // Wave 6: Large mixed wave ending with heavy tanks
        WaveData(
            waveIndex = 6,
            title = "Final Vanguard",
            spawns = buildList {
                repeat(6) { add(WaveEnemySpawn(EnemyType.FAST_SOLDIER, 0.5f)) }
                repeat(4) { add(WaveEnemySpawn(EnemyType.ARMORED_VEHICLE, 1.2f)) }
                add(WaveEnemySpawn(EnemyType.TANK, 2.0f))
                repeat(4) { add(WaveEnemySpawn(EnemyType.INFANTRY, 0.7f)) }
                add(WaveEnemySpawn(EnemyType.TANK, 2.5f))
                add(WaveEnemySpawn(EnemyType.TANK, 3.0f))
            }
        )
    )
}
