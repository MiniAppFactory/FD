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

    /**
     * Bos build pad secilince, HENUZ bir kule karti secilmemisken gosterilen
     * notr on-izleme menzili (REFERANS tuvalde; cizimde renderScale ile carpilir).
     *
     * Faz 10: kule menzilleri artik 150 ile 270 ref-px arasinda degisiyor, yani
     * TEK bir sabit halka artik yalan soyluyor — oyuncu Frost Field'in kapsama
     * alanini ancak kuleyi kurup satarak ogrenebiliyordu. Bu yuzden build
     * cubugundaki bir kart BASILI tutuldugunda `GameEngine.previewTowerType`
     * doluyor ve halka O KULENIN gercek menzilini gosteriyor
     * (bkz. GameCanvas: birakma onizlemesi her zaman gorunur olmali).
     */
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

    /**
     * ------------------------------------------------------------------------
     * Faz 10 — KULE UZMANLASMASI (testci: "bir de her kule hepsinde ise yariyor")
     * ------------------------------------------------------------------------
     * Rol bir ETIKET degil, MAKINEYLE DOGRULANAN bir sozlesme: her rol tam bir
     * kule tarafindan doldurulur ve `BalanceConsistencyTest` her rolun kendi
     * hedef sinifinda EN IYI, baska bir sinifta BELIRGIN SEKILDE EN KOTU
     * oldugunu sayisal olarak kontrol eder. Boylece ileride biri "Gatling'i
     * biraz guclendirelim" derse ve kule her seye yeterli hale gelirse test
     * kirilir — regresyon sessizce geri gelemez.
     */
    enum class TowerRole {
        /** Kalabalik/hizli piyade. Zirha karsi neredeyse ise yaramaz. */
        CROWD,
        /** Kumelenmis ve YAVAS hedef; patlama zirhi bypass eder. Tek hizli hedefe kotu. */
        SIEGE,
        /** Tek agir zirhli hedef. Kalabaliga karsi verimsiz (cok yavas atis). */
        ANTI_TANK,
        /** Hasar vermez, ALAN KONTROLU yapar: digerlerinin penceresini acar. */
        SUPPORT
    }

    /**
     * @param role Kulenin oynanistaki kimligi (bkz. TowerRole).
     * @param unlockedAtLevel Bu kule kacinci KAMPANYA bolumunden itibaren insa
     *   edilebilir. Testci: "ilk bolumden itibaren her seyi acmak dogru degil."
     *   LEVEL_DESIGN kurali: oyuncunun henuz sahip olmadigi mekanik zorunlu
     *   basari kosulu OLAMAZ — bu yuzden her yeni dusman tipinin cevabi, o tip
     *   ilk gorundugu bolumde ZATEN acik olmali. Kilit tablosunun dalga
     *   tanimlariyla tutarliligi `WaveDefinitionsDataTest` tarafindan
     *   dogrulanir, yorumla degil.
     * @param level1Range Menzil **1920 REFERANS TUVALINDE** (DECISIONS B3), ham
     *   canvas px DEGIL. Motor `TowerEntity.rangePx(renderScale)` ile cevirir;
     *   aksi halde tablet ile telefon ayni oyunu oynamaz.
     * @param splashRadius > 0 YALNIZCA Cannon'da (referans tuvalde). Splash
     *   bileseni zirhi bypass eder (DECISIONS B2).
     * @param armorPierce 0..1, YALNIZCA Anti-Armor.
     * @param slowPulseRadius > 0 YALNIZCA Slow'da (referans tuvalde).
     *   **Bu alan olmadan Frost Field bir DESTEK kulesi degildi**: cryo darbesi
     *   yalnizca hedeflenen TEK dusmani yavaslatiyordu, yani 20 kisilik bir
     *   suruye karsi 0.65 sn'de bir 1 dusman -> oyuncunun "kullanmanin anlami
     *   yok" demesinin gercek sebebi buydu. Artik darbe bu yaricaptaki HERKESI
     *   soguturr.
     * @param missileImpactRadius > 0 YALNIZCA Anti-Armor (referans tuvalde).
     *   Fuzenin carpma noktasindaki KUCUK alan hasari. Kasitli olarak
     *   Cannon'in splash yaricapindan cok kucuk ve hasari kesirli, ustelik
     *   zirhi bypass ETMEZ (delici muhimmat olarak hesaplanir) — boylece
     *   Cannon'in "kalabalik/kalkanli" kimligini golgelemez.
     * @param missileImpactDamageFraction Ikincil hedeflere giden hasar orani.
     */
    data class TowerStats(
        val type: TowerType,
        val role: TowerRole,
        val unlockedAtLevel: Int,
        val name: String,
        val description: String,
        val buildCost: Int,
        val level1Range: Float,       // 1920 referans tuvalde ref-px
        val level1Damage: Float,
        val level1FireRate: Float,    // Seconds between shots
        val level2UpgradeCost: Int,
        val level2Range: Float,
        val level2Damage: Float,
        val level2FireRate: Float,
        val splashRadius: Float = 0f,  // > 0 for Cannon
        val armorPierce: Float = 0f,   // 0.0 to 1.0 for Anti-Armor
        val slowFactor: Float = 0f,    // 0.0 to 1.0 (e.g. 0.5 = 50% speed) for Slow Tower
        val slowDuration: Float = 0f,  // Duration of slow in seconds
        val slowPulseRadius: Float = 0f,          // > 0 for Slow
        val missileImpactRadius: Float = 0f,      // > 0 for Anti-Armor
        val missileImpactDamageFraction: Float = 0f
    ) {
        /** Sürekli hasar (hasar/sn) — rol karsilastirmalarinin olcum birimi. */
        val level1Dps: Float get() = level1Damage / level1FireRate
        val level2Dps: Float get() = level2Damage / level2FireRate
    }

    /** Bu kule bu bolumde insa edilebilir mi? Tek karar noktasi. */
    fun isTowerUnlocked(type: TowerType, levelId: Int): Boolean =
        levelId >= (TOWER_SPECS[type]?.unlockedAtLevel ?: 1)

    /** Bolum secme/insa cubugu icin: bu bolumde acik olan kuleler. */
    fun unlockedTowers(levelId: Int): List<TowerType> =
        TowerType.values().filter { isTowerUnlocked(it, levelId) }

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

    // ========================================================================
    // Faz 10 — KULE TABLOSU
    //
    // Her sayinin GEREKCESI docs/TOWER_REBALANCE.md'de tablo halinde duruyor.
    // Ozet (etkin DPS = hasar/sn x zirh carpani):
    //
    //                zirhsiz  zirhli(0.78)  tank(0.86)  5'li kume   maliyet
    //   MACHINE_GUN    43.8        9.6          6.1        43.8        60
    //   CANNON         18.4       18.4*        18.4*       92.0*       95
    //   ANTI_ARMOR     30.6       27.0         26.6        ~50        115
    //   SLOW            2.3        0.5          0.3        ~12        100
    //   (*) splash zirhi BYPASS eder (DECISIONS B2) ve TUM kumeye vurur.
    //   (DPS'ler atis araligi x2 sonrasi; ORANLAR degismedi, yani rol
    //    uzmanlasmasi birebir korunuyor — bkz. asagidaki tempo blogu.)
    //
    // Yani: kalabalikta MACHINE_GUN, kumede CANNON, zirhta ANTI_ARMOR acik ara
    // onde; her kulenin bariz bir KOR NOKTASI var. Bu tablo elle okunmak icin
    // degil test icin yazildi: BalanceConsistencyTest ayni oranlari yeniden
    // hesaplayip zorunlu kiliyor.
    //
    // ------------------------------------------------------------------------
    // ATIS TEMPOSU + TABAN KALIBRASYONU
    //
    // Iki ayri sebep ayni yerde birlesiyor:
    //
    // (1) Kullanici: "vurus hizlari %50 dusurulsun, cok hizli ates ediyorlar."
    //     `fireRate` SANIYE CINSINDEN ARALIKTIR, hiz degil -> aralik x2:
    //       MACHINE_GUN 0.16 -> 0.32 · 0.12 -> 0.24
    //       CANNON      1.25 -> 2.50 · 1.10 -> 2.20
    //       SLOW        0.65 -> 1.30 · 0.55 -> 1.10
    //       ANTI_ARMOR  1.80 -> 3.60 · 1.55 -> 3.10
    //
    // (2) ATIS BASINA HASAR **DEGISMEDI**, yani her kulenin DPS'i KASTEN
    //     yarilandi. Once hasari x2 ile telafi etmeyi denedik; yanlisti, cunku
    //     DPS'i korumak sorunun tamamini korur:
    //
    //     docs/tools/difficulty_audit.py olcumu (bolum 7, oyuncu LEHINE ust
    //     sinir): iki kulenin teslim edebilecegi hasar / dalgalarin toplam cani
    //     = **8.23**. Yani iki kule gerekenin sekiz katini veriyordu; "her kule
    //     hepsinde ise yariyor" ve "2 kule yetiyor" sikayetlerinin ortak kok
    //     sebebi buydu. Gatling Kd.2 piyadeye 216 DPS veriyor, piyade 75 canli:
    //     tek kule saniyede 2.9 piyade siliyor, dalga saniyede 0.4 dusman
    //     gonderiyor.
    //
    //     Duzeltme iki carpandan olusuyor: atis araligi x2 (bu blok) ve dusman
    //     cani x3.5 (bkz. ENEMY_SPECS). 8.23 / 2 / 3.5 = **1.18** -> hedef
    //     bant 1.15..1.40.
    //
    // UZMANLASMA KORUNUYOR (dogrulandi): Gatling Kd.2 tanka atis basina 3.64
    // hasar veriyor (26 x 0.14), 0.24 sn araliktan 15.2 DPS; 2030 canli tanki
    // tek basina **134 saniyede** olduruyor. Yani hâlâ tamamen ise yaramaz ve
    // Agir Top / Fuze zorunlu kaliyor.
    //
    // Yan fayda: makineli tufek sesinin minIntervalMs'i 70 ms. 0.16 sn (160 ms)
    // araliginda ses neredeyse surekli bir gurultuydu; 0.32 sn'de her atis
    // ayri duyulur, namlu alevi (0.13 sn) ile de artik ortusmuyor.
    // ------------------------------------------------------------------------
    val TOWER_SPECS: Map<TowerType, TowerStats> = mapOf(
        // KALABALIK. Menzil 160 -> 150: en kisa menzilli kule olmasi kimliginin
        // parcasi (bogaza yerlestir, uzaktan tarama yok) ve destek kulesinin
        // menzil ustunlugunu okunur kiliyor. Hasar/atis hizi DEGISMEDI: zirha
        // karsi ise yaramazligi kule zayiflatilarak degil DUSMAN ZIRHI
        // yukseltilerek saglandi (bkz. ENEMY_SPECS) — boylece piyadeye karsi
        // hissedilen guc aynen korunuyor.
        TowerType.MACHINE_GUN to TowerStats(
            type = TowerType.MACHINE_GUN,
            role = TowerRole.CROWD,
            unlockedAtLevel = 1,
            name = "Gatling Gun",
            description = "Shreds infantry and runners. Bullets barely scratch armour.",
            buildCost = 60,
            level1Range = 150f,
            level1Damage = 14f,      // DEGISMEDI
            level1FireRate = 0.32f,  // 0.16 x2 -> DPS 87.5 -> 43.8 (yarilandi, KASITLI)
            level2UpgradeCost = 65,
            level2Range = 180f,
            level2Damage = 26f,      // DEGISMEDI
            level2FireRate = 0.24f   // 0.12 x2 -> DPS 216.7 -> 108.3
        ),
        // KUSATMA. Tek hedef DPS'i 45.5 -> 36.8 dusuruldu ama splash yaricapi
        // 65 -> 78 buyudu: kimlik "tek hedefe vuran top" degil "kumeyi silen
        // top". Mermi hizi 160 -> 110 (ucus 0.63 -> 0.91 sn): Scout Runner o
        // surede 105 ref-px yol alir, yani 78'lik patlamanin DISINA cikar ->
        // top hizli hedefi ISKALAR. Bu bilincli zayiflik ayni zamanda oyuna
        // gercek bir kombo veriyor: Frost Field'in yavaslattigi kosucu 61
        // ref-px yol alir ve top ONU VURUR.
        TowerType.CANNON to TowerStats(
            type = TowerType.CANNON,
            role = TowerRole.SIEGE,
            unlockedAtLevel = 3,
            name = "Heavy Cannon",
            description = "Slow shell, wide blast. Ignores armour, misses fast movers.",
            buildCost = 95,
            level1Range = 175f,
            level1Damage = 46f,      // DEGISMEDI
            level1FireRate = 2.50f,  // 1.25 x2 -> DPS 36.8 -> 18.4
            level2UpgradeCost = 90,
            level2Range = 205f,
            level2Damage = 88f,      // DEGISMEDI
            level2FireRate = 2.20f,  // 1.10 x2 -> DPS 80.0 -> 40.0
            splashRadius = 78f
        ),
        // ZIRH KIRICI. Atis araligi 1.4 -> 1.8 sn (kalabaliga karsi kasitli
        // verimsizlik: saniyede 0.55 atis) ve atis basina hasar 85 -> 110
        // (tek agir hedefe yikici). Artik gercekten FUZE atiyor: mermi yol
        // alir, hedef havadayken olurse fuze BOSA gider (yonlendirme yok) —
        // testcinin "fuze rampasi var ama fuze atmiyor" maddesi bu.
        TowerType.ANTI_ARMOR to TowerStats(
            type = TowerType.ANTI_ARMOR,
            role = TowerRole.ANTI_TANK,
            unlockedAtLevel = 7,
            name = "Missile Battery",
            description = "Armour-piercing missile. Devastating on heavies, wasted on swarms.",
            buildCost = 115,
            level1Range = 250f,
            level1Damage = 110f,     // DEGISMEDI
            level1FireRate = 3.60f,  // 1.80 x2 -> DPS 61.1 -> 30.6
            level2UpgradeCost = 115,
            level2Range = 290f,
            level2Damage = 205f,     // DEGISMEDI
            level2FireRate = 3.10f,  // 1.55 x2 -> DPS 132.3 -> 66.1
            armorPierce = 0.85f,
            missileImpactRadius = 40f,
            missileImpactDamageFraction = 0.35f
        ),
        // DESTEK. Menzil 150 -> 270 (+%80): testci hakliydi, destek kulesinin
        // menzili MACHINE_GUN'dan kisaydi, yani sahada hicbir sey degistirmiyor
        // gibi duruyordu. Karsiligi odendi: hasar 6 -> 3 (artik gercekten hasar
        // vermez) ve yavaslatma %50 -> %42. Buna ragmen kule cok daha guclu,
        // cunku asil duzeltme MENZIL DEGIL: cryo darbesi artik 105 ref-px
        // yaricapindaki HERKESI sogutuyor (onceden yalnizca hedeflenen tek
        // dusmani). Fiyat 80 -> 100: hicbir hasar vermeyen ama bataryanin
        // penceresini 1.7 katina cikaran bir kule ucuz olamaz.
        TowerType.SLOW to TowerStats(
            type = TowerType.SLOW,
            role = TowerRole.SUPPORT,
            unlockedAtLevel = 5,
            name = "Frost Field",
            description = "Wide cryo pulses chill every enemy in the blast. Deals almost no damage.",
            buildCost = 100,
            level1Range = 270f,
            level1Damage = 3f,       // DEGISMEDI
            level1FireRate = 1.30f,  // 0.65 x2 -> DPS 4.6 -> 2.3
            level2UpgradeCost = 85,
            level2Range = 320f,
            level2Damage = 7f,       // DEGISMEDI
            level2FireRate = 1.10f,  // 0.55 x2 -> DPS 12.7 -> 6.4
            slowFactor = 0.42f,
            slowDuration = 2.2f,
            slowPulseRadius = 105f
        )
    )

    /**
     * Mermi "hizi". Motor `progress += dt * speed / 100f` isletiyor, yani
     * **ucus suresi = 100 / speed saniye ve MESAFEDEN BAGIMSIZ**. Bu yuzden bu
     * sayilar px/sn DEGIL; asagidaki yorumlarda gercek anlami olan ucus suresi
     * yazili. Renderer'a gomulmezler, denge burada durur.
     */
    val PROJECTILE_SPEEDS: Map<ProjectileType, Float> = mapOf(
        ProjectileType.BULLET to 300f,        // 0.33 sn
        ProjectileType.CANNON_SHELL to 110f,  // 0.91 sn — hizli hedefi iskalamasinin sebebi
        ProjectileType.MISSILE to 145f,       // 0.69 sn — "yol alir", israf olabilir
        ProjectileType.FROST_PULSE to 260f    // 0.38 sn
    )

    /**
     * Tek hedefli mermi carptiginda hedefi olmusse: isabet noktasinin bu kadar
     * REF-px yakinindaki en yakin dusmana yonlenir.
     *
     * Onceden sinir YOKTU (`enemies.minByOrNull { mesafe }`): olen hedefe giden
     * bir kursun haritanin obur ucundaki dusmana hasar tasiyordu. Fuze bu
     * yonlendirmeyi HIC kullanmaz (bkz. GameEngine.onProjectileImpact) — israf
     * olmasi kimliginin parcasi.
     */
    const val PROJECTILE_REDIRECT_TOLERANCE_REF_PX = 45f

    /**
     * Dalga temizleme ikramiyesi (Tedarik).
     *
     * Faz 10: motorda ciplak `35` olarak duruyordu — ekonominin tek-kaynak
     * kuralini ihlal ediyordu ve bolum uzunluguyla sessizce buyuyordu (L1'de
     * 175, L8'de 315). Ekonomi ajaninin olcumune gore 18'e cekildi
     * (SupplyBudgetModel.WAVE_CLEAR_SUPPLY_BONUS ile ayni olmasi
     * BalanceConsistencyTest'te kilitli).
     */
    const val WAVE_CLEAR_SUPPLY_BONUS = 18

    // ========================================================================
    // Faz 10 — DUSMAN ODULLERI x1/3 (ECONOMY_SPEC 9 madde 2)
    //
    // Ekonomi ajaninin teshisi: baskin kaldirac baslangic Tedariki DEGIL,
    // oldurme geliriydi. Gatling 60 Tedarik, piyade 12 oduyordu -> **5 oldurme
    // = 1 kule**; bir dalga 6-14 piyade getirdigi icin her dalga 1-3 kule
    // finanse ediyordu. Yeni tabloda bir kule ~15 oldurme eder.
    //
    // Olcek TAM OLARAK 1/3 secildi (0.375 degil): boylece her odul 3'e tam ya
    // da tama yakin bolunur ve dusmanlarin BIRBIRINE GORELI degeri korunur
    // (piyade 1.00 / kosucu 1.25 / tank 5.00 birebir ayni). 0.375 zirhliyi
    // piyadeye gore %18 degerlendirip hedef secimini sessizce bozardi.
    //
    // Kule kimlikleri ve hedef secimi DEGISMEZ; yalnizca akis hizi duser.
    //
    // ------------------------------------------------------------------------
    // Faz 10 — DUSMAN CANI x3.5 (TABAN KALIBRASYONU)
    //
    // INFANTRY 75->260 · FAST 45->160 · SHIELDED 150->525
    // ARMORED 220->770 · TANK 580->2030 · COMMAND_TANK 2600->9100
    //
    // "ZORLUGU HP SISIREREK YUKSELTME" YASAGIYLA CELISMIYOR — ve bu ayrim
    // onemli oldugu icin burada duruyor:
    //
    // LEVEL_DESIGN E'nin yasakladigi sey **bolumler arasi** artisi HP ile
    // yapmaktir: 8. bolumu 7'den zor yapmak icin ayni dusmani sismanlatmak
    // tembelliktir, cunku oyuncuya yeni bir problem vermez. O kural aynen
    // gecerli ve kampanya egrisi hâlâ KOMPOZISYONLA yurutuluyor (bkz.
    // WaveDefinitions: tanitim sirasi, kadans, zirh karisimi).
    //
    // Buradaki sorun bambaska: **taban olcek yanlis kalibre edilmisti.** Kule
    // DPS'i ile dusman cani arasinda ~7 katlik uyumsuzluk olculdu
    // (docs/tools/difficulty_audit.py: bolum 7 arz/talep = 8.23). Bu bir zorluk
    // egrisi karari degil, olcu birimi hatasi; TEK SEFERLIK ve TUM dusmanlara
    // AYNI carpanla uygulanan bir duzeltme. Uniform oldugu icin:
    //   · dusmanlarin birbirine goreli tehdidi DEGISMEZ (AEHP siralamasi ayni),
    //   · kule uzmanlasmasi DEGISMEZ (oranlar korunur),
    //   · bolumler arasi egri DEGISMEZ (hepsi ayni carpanla olcekleniyor).
    //
    // Zirh, hiz, odul ve boyut DEGISMEDI.
    // ------------------------------------------------------------------------
    // ========================================================================
    val ENEMY_SPECS: Map<EnemyType, EnemyStats> = mapOf(
        EnemyType.INFANTRY to EnemyStats(
            type = EnemyType.INFANTRY,
            name = "Infantry Squad",
            maxHp = 260f,   // 75 x3.5 (kalibrasyon)
            baseSpeed = 65f,
            armor = 0.0f,
            rewardGold = 4,   // 12 -> 4 (x1/3, ECONOMY_SPEC 9.2)
            sizeRadius = 14f
        ),
        EnemyType.FAST_SOLDIER to EnemyStats(
            type = EnemyType.FAST_SOLDIER,
            name = "Scout Runner",
            maxHp = 160f,   // 45 x3.5 (asagi yuvarlandi)
            baseSpeed = 115f,
            armor = 0.0f,
            rewardGold = 5,   // 15 -> 5 (hiz primi korunuyor: piyadenin 1.25 kati)
            sizeRadius = 12f
        ),
        // --------------------------------------------------------------------
        // Faz 10 — ZIRH YUKSELTILDI (0.55 -> 0.78 ve 0.70 -> 0.86).
        //
        // Testci: "her kule hepsinde ise yariyor." Olculen sebep buydu: eski
        // zirhla Gatling zirhli araca 39 DPS veriyordu ve **altin basina** en
        // iyi secenek olmaya devam ediyordu (0.66 DPS/altin, fuzenin 0.51'ine
        // karsi) — yani zirhli dusman bir KARSI-KOYMA degil sadece daha kalin
        // bir piyadeydi. Yeni degerlerle Gatling 19.3 DPS'e duser (0.32/altin),
        // fuze 54.0'a cikar (0.47/altin): oyuncu can barinin kursun altinda
        // KIMILDAMADIGINI gorur ve muhimmat degistirir.
        //
        // maxHp ve hiz DEGISMEDI: zorluk "HP sismesi" ile degil kompozisyon
        // zorunlulugu ile artiyor (LEVEL_DESIGN E). Cannon splash'i zirhi
        // bypass ettigi icin (DECISIONS B2) bolum 3'te acilan Cannon, bolum
        // 5'te gelen zirhli araca gecerli bir cevap olarak KALIR — kilit
        // tablosunun tutarliligi bunun uzerine kurulu.
        // --------------------------------------------------------------------
        EnemyType.ARMORED_VEHICLE to EnemyStats(
            type = EnemyType.ARMORED_VEHICLE,
            name = "Armored Car",
            maxHp = 770f,   // 220 x3.5
            baseSpeed = 50f,
            armor = 0.78f, // kursun %22'sini gecirir -> Gatling'e karsi duvar
            rewardGold = 9,   // 28 -> 9 (piyadenin ~2.25 kati)
            sizeRadius = 20f
        ),
        EnemyType.TANK to EnemyStats(
            type = EnemyType.TANK,
            name = "Heavy Tank",
            maxHp = 2030f,  // 580 x3.5
            baseSpeed = 32f,
            armor = 0.86f, // kursun %14 -> yalnizca patlama/delici ise yarar
            rewardGold = 20,  // 60 -> 20 (piyadenin 5 kati, oran birebir korundu)
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
            maxHp = 525f,   // 150 x3.5
            baseSpeed = 58f,
            armor = 0.62f,             // kursun neredeyse ise yaramaz
            rewardGold = 7,            // 22 -> 7 (x1/3, asagi yuvarlandi)
            sizeRadius = 16f,
            splashVulnerability = 1.6f // ...ama patlama zirhi bypass eder ve 1.6x vurur
        ),
        EnemyType.COMMAND_TANK to EnemyStats(
            type = EnemyType.COMMAND_TANK,
            name = "Command Tank",
            maxHp = 9100f,  // 2600 x3.5
            baseSpeed = 30f,
            // Boss zirhi TANK'in USTUNDE kalmak zorunda (BalanceConsistencyTest),
            // tank 0.86'ya cikinca bu da 0.88'e cikti.
            armor = 0.88f,
            rewardGold = 60,  // 180 -> 60 (x1/3)
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

    // ========================================================================
    // ACT (TUR) OLCEKLENDIRMESI
    //
    // Kampanya 11 haritayi IKI TUR oynatir (22 bolum). Ikinci turda ayni harita
    // tekrar gorunur; sadece gece overlay'i ve kapali pad'lerle degil, DUSMANIN
    // KENDISI de guclenmelidir, yoksa Act II bir tekrar gibi hissedilir.
    //
    // Yalnizca HP ve ODUL olceklenir. Hiz olceklenmez: hiz artisi hem
    // okunabilirligi bozar hem de "zorlugu sadece HP/hiz artirarak yukseltme"
    // yasagini ihlal eder (LEVEL_DESIGN E). Odul HP ile birlikte artar ki
    // Act II'de ekonomi ayni tempoda kalsin.
    //
    // AEHP olcumleri TABAN degerlerden hesaplanir; buradaki carpan olcume
    // girmez, yalnizca calisma aninda uygulanir.
    // ========================================================================
    fun actHpMultiplier(act: Int): Float = when {
        act <= 1 -> 1.0f
        act == 2 -> 1.55f
        else -> 1.55f + 0.45f * (act - 2)   // Act III+ (v1.1) icin hazir
    }

    fun actRewardMultiplier(act: Int): Float = when {
        act <= 1 -> 1.0f
        act == 2 -> 1.30f
        else -> 1.30f + 0.20f * (act - 2)
    }


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
     * IKINCI KOL (catallanma) kacinci bolumden itibaren KULLANILIR.
     *
     * BULGU (beklenenin tersi): ikinci kol "kampanyada kullanilmiyor" degildi —
     * `LevelData.routesForMapId` gercek catallanan haritalar icin (1, 2, 4, 11)
     * iki rotayi da donduruyor ve motor spawn basina seed'li secim yapiyordu.
     * Yani ikinci kol ZATEN acikti, ustelik **ogretici bolumlerde de** (harita
     * 1, 2, 4 = bolum 1, 2, 4).
     *
     * Bu yanlisti: iki koldan eszamanli akis, tek kulenin kapsamasinin fiziksel
     * olarak yetmedigi durum — yani bir BECERI sinavi. Oyuncu daha tek kolu
     * savunmayi ogrenmeden bu sinava sokulmamali; ustelik ilk bolumlerde
     * Tedarik yalnizca bir kuleye yetiyor, dolayisiyla ikinci kol "ogret" degil
     * "cezalandir" oluyordu.
     *
     * Bu yuzden ilk 8 bolum TEK KOL (ogretme dilimi), 9. bolumden itibaren
     * catallanma devreye girer. Secim hâlâ seed'li ve deterministik: RNG tek
     * basina bir yenilgi sebebi olamaz, ayni bolum her oynanista ayni dizidir.
     */
    const val ALT_ROUTE_FIRST_LEVEL = 9

    /** Bu bolumde catallanma (ikinci kol) devrede mi? */
    fun usesAlternateRoutes(levelId: Int): Boolean = levelId >= ALT_ROUTE_FIRST_LEVEL

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

    /**
     * Faz 10 — BOLUM BAZINDA BASLANGIC TEDARIKI (ECONOMY_SPEC 9 madde 1).
     *
     * Eskiden 22 bolum de 150 ile basliyordu. Ilk bolumlerde bu, oyuncuya
     * hazirlik fazinda **iki-uc kule birden** kurma imkani veriyordu; yani
     * "hangi kuleyi once kurayim" karari hic olusmuyordu. Yeni tabloda L1'de
     * tam bir Gatling parasi var: ikinci kule KAZANILIR.
     *
     * Ilk 6 bolum dısında (L7+) taban 150 olarak kalir — o noktada oyuncu meta
     * yukseltmeleri ve daha pahali kadrolarla oynuyor.
     *
     * `SupplyBudgetModel.startingSupply(level)` ile ayni olmasi
     * BalanceConsistencyTest ve SupplyBudgetTest'te KILITLI.
     */
    private val EARLY_STARTING_SUPPLY = listOf(80, 90, 110, 120, 140, 150)

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
                    deploymentCost = actIDeploymentCost[lv - 1],
                    startingSupply = EARLY_STARTING_SUPPLY.getOrElse(lv - 1) { INITIAL_GOLD }
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
