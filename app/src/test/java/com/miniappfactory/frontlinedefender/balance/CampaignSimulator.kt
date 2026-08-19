package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.MetaUpgrades
import com.miniappfactory.frontlinedefender.game.economy.starHealthFromLeaks
import com.miniappfactory.frontlinedefender.game.economy.starsFor
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.PointF
import com.miniappfactory.frontlinedefender.game.model.ProjectileType
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * ===========================================================================
 * COK KULELI MOTOR AYNASI — 55 bolumun tamami icin cozulebilirlik olcumu
 * ===========================================================================
 *
 * NEDEN VAR
 * ---------
 * `CampaignSolvabilityTest` menzil kapisini dogru modelledi ama **tek kule
 * tipiyle** (Gatling) calisiyordu. Gercek oynanista oyuncu KARISIK kadro kurar
 * ve oyunun butun tasarimi buna dayali: zirhsiz hedefte Gatling 43,8 DPS /
 * Fuze 30,6 iken TANKTA Gatling 6,1 / Fuze 26,6 — dort kattan fazla fark.
 * Tek tipli bir simulator gec bolumleri sistematik olarak YANLIS olcer, cunku
 * oyuncunun en onemli karari (kule TIPI) modelde hic yoktur.
 *
 * BU DOSYA NE MODELLER (hepsi `GameEngine`den TURETILDI, tahmin yok)
 * -----------------------------------------------------------------
 *  · SERT MENZIL KAPISI — `findTargetEnemyForTower`: kule yalnizca yaricapinin
 *    icindeki dusmani hedefler, TEK hedef, `TargetingMode.FIRST` (en ilerideki).
 *  · MERMI UCUSU — `progress += dt * speed / 100f`, yani ucus suresi
 *    100/speed sn ve MESAFEDEN BAGIMSIZ. Top mermisi 0,91 sn havada kalir ve
 *    hedef o sirada patlama yaricapinin disina cikabilir: **top hizli hedefi
 *    ISKALAR** (Faz 10 §1'in kasitli zayifligi). Isabet noktasi ates anindaki
 *    hedef konumudur.
 *  · DORT CARPMA DAVRANISI — `onProjectileImpact` dallari birebir:
 *      Cannon  -> `splashRadius` icindeki HERKES, zirh BYPASS + splashVulnerability
 *      Slow    -> `slowPulseRadius` icindeki HERKES soguturr (zirh normal)
 *      Missile -> yalnizca BIRINCIL hedef (olduyse fuze ISRAF olur) + kucuk
 *                 carpma alani; carpma alani zirhi bypass ETMEZ
 *      Bullet  -> birincil hedef; olduyse 45 ref-px toleransinda en yakina
 *  · KADEME KILIDI — `GameConfig.maxTowerTier(type, levelId)`; kademe 3
 *    L12'den once ALINAMAZ.
 *  · KULE KILIDI — `GameConfig.unlockedTowers(levelId)`.
 *  · PAD GERCEGI — yalnizca GORUNUR pad'ler (`disabledPadIds` cikarilmis).
 *  · ROTA GERCEGI — `usesAlternateRoutes(level)` ve motorun SEED'LI rota
 *    atamasi (`ROUTE_RNG_SEED_BASE + levelId * 7919`) birebir.
 *  · ACT OLCEKLERI — `hp * actHpMultiplier`, odul
 *    `(gold * actRewardMul).toInt()` **Float aritmetigiyle** (motorda oyle;
 *    Double kullanmak bolum basina 30-50 Tedarik sapma yaratir — daha once
 *    bulunan gercek hata tam olarak buydu).
 *  · DALGA AKISI — `delaySeconds` = "bu spawn'dan SONRAKI bekleme", ilk spawn
 *    t=0,5'te; dalga ancak son dusman olunce/sizinca biter; sonra +18 Tedarik.
 *
 * BILINCLI SADELESTIRMELER (hepsi kucuk ve YONU BELLI)
 * ----------------------------------------------------
 *  · dt = 50 ms (motor ~16,7 ms). Atis sayaci BIRIKIMLI (`cooldown += interval`)
 *    tutulur, boylece uzun vadeli atis hizi TAM dogru olur; motorun kare
 *    kuantalamasi oyuncu aleyhine ~%2,5 kayip verir, yani buradaki model
 *    oyuncu LEHINE ~%2,5 iyimserdir.
 *  · Dusman yay uzerinde ilerler; motorun waypoint'e "yapisma" karesinde
 *    kaybettigi ~%0,6 mesafe modellenmez (yine oyuncu lehine).
 *  · Guclendirici YOK, reklam odulu YOK, kule SATISI yok. Meta yukseltme
 *    VARSAYILAN OLARAK SIFIR; [play]/[bestOutcome] bir `MetaUpgrades` alir,
 *    yani "meta 0 ile ne oluyor, tam rank ile ne oluyor" AYNI motorla olculur.
 *    Kule satisi modellenmedigi icin Hurda Degeri hattinin etkisi burada 0'dir.
 *
 * Model iyimser oldugu icin **KIRMIZI kesin bir hatadir**; yesil "rahat"
 * demek degil "matematiksel olarak mumkun" demektir.
 */
object CampaignSimulator {

    /** Simulasyon adimi (sn). */
    const val DT: Float = 0.05f

    /** Rota ornekleme adimi, REFERANS tuvalde (1920x1080) px. */
    private const val ARC_STEP: Float = 2f

    /** Bir dalganin kosabilecegi en uzun sure; sonsuz donguye karsi emniyet. */
    private const val WAVE_TIME_LIMIT: Float = 1200f

    // =======================================================================
    // Geometri
    // =======================================================================

    /** Rotanin ARC_STEP araliklarla yeniden orneklenmis hâli (referans tuval). */
    class SampledRoute(route: List<PointF>) {
        private val xs: FloatArray
        private val ys: FloatArray
        val length: Float

        init {
            val px = FloatArray(route.size) { route[it].x * GameConfig.REFERENCE_WIDTH }
            val py = FloatArray(route.size) { route[it].y * GameConfig.REFERENCE_HEIGHT }
            val segLen = FloatArray(max(1, route.size - 1))
            var total = 0f
            for (i in 0 until route.size - 1) {
                val d = hypot((px[i + 1] - px[i]).toDouble(), (py[i + 1] - py[i]).toDouble()).toFloat()
                segLen[i] = d
                total += d
            }
            length = total
            val n = max(1, (total / ARC_STEP).toInt())
            xs = FloatArray(n + 1)
            ys = FloatArray(n + 1)
            var seg = 0
            var segStart = 0f
            for (i in 0..n) {
                val target = min(total, i * ARC_STEP)
                while (seg < segLen.size - 1 && segStart + segLen[seg] < target) {
                    segStart += segLen[seg]
                    seg++
                }
                val t = if (segLen[seg] <= 0f) 0f else ((target - segStart) / segLen[seg]).coerceIn(0f, 1f)
                xs[i] = px[seg] + (px[seg + 1] - px[seg]) * t
                ys[i] = py[seg] + (py[seg + 1] - py[seg]) * t
            }
        }

        private fun idx(arc: Float): Int = (arc / ARC_STEP).toInt().coerceIn(0, xs.size - 1)

        fun xAt(arc: Float): Float = xs[idx(arc)]
        fun yAt(arc: Float): Float = ys[idx(arc)]

        /** Bu noktanin `range` yaricapiyla GORDUGU yol uzunlugu (ref-px). */
        fun coveredLength(px: Float, py: Float, range: Float): Float {
            val r2 = range * range
            var covered = 0f
            for (i in xs.indices) {
                val dx = xs[i] - px
                val dy = ys[i] - py
                if (dx * dx + dy * dy <= r2) covered += ARC_STEP
            }
            return min(covered, length)
        }
    }

    data class Pad(val id: Int, val x: Float, val y: Float)

    // =======================================================================
    // Bolum modeli — her deger GameConfig / WaveDefinitions / LevelGeometry'den
    // =======================================================================

    /**
     * @param modifiersOverride yalnizca "NE OLURDU" olcumleri icin. `null` ise
     *   bolumun GERCEK degistiricileri kullanilir (kapi testlerinin kullandigi
     *   tek dogru deger). Ayni bolumu kisitli/kisitsiz kosturup farki olcmek,
     *   "simulator kisiti gercekten taniyor mu" sorusunun tek durust cevabi.
     */
    class LevelModel(
        val levelId: Int,
        modifiersOverride: GameConfig.LevelModifiers? = null
    ) {
        val spec: GameConfig.LevelSpec = GameConfig.levelSpec(levelId)

        /** Motor eksik bitmap'te yedege duser; bugun 1..11 hepsi pakette. */
        val mapId: Int =
            if (spec.mapId in GameConfig.SHIPPED_MAP_IDS) spec.mapId else GameConfig.MAP_FALLBACK_ID

        private val data = LevelData.forMapId(mapId)

        /** Motor: ikinci kol yalnizca `ALT_ROUTE_FIRST_LEVEL`den itibaren acik. */
        val routes: List<SampledRoute> = LevelData.routesForMapId(mapId)
            .let { if (GameConfig.usesAlternateRoutes(levelId)) it else listOf(it.first()) }
            .map { SampledRoute(it) }

        /** GORUNUR pad'ler: devre disi birakilanlar haritada HIC yoktur. */
        val pads: List<Pad> = data.buildSpots
            .filter { it.id !in spec.disabledPadIds.toSet() }
            .map {
                Pad(
                    it.id,
                    it.normX * GameConfig.REFERENCE_WIDTH,
                    it.normY * GameConfig.REFERENCE_HEIGHT
                )
            }

        val waves: List<GameConfig.WaveData> = WaveDefinitions.wavesFor(levelId)
        val actHpMul: Float = GameConfig.actHpMultiplier(spec.act)
        val actRewardMul: Float = GameConfig.actRewardMultiplier(spec.act)

        /**
         * BOLUM DEGISTIRICILERI (`GameConfig.LevelModifiers`).
         *
         * Simulator kisiti TANIMAK ZORUNDA: `CampaignSolvabilityAllLevelsTest`
         * "bu bolum gecilebilir" diyorsa, olctugu sey oyuncunun GERCEKTEN
         * oynayacagi bolum olmali. Kisiti modellemeden yesil kalan bir test
         * YALAN SOYLER — kisitin dengeyi bozdugu bolumu tam da kapinin
         * yakalamasi gereken yerde gozden kacirir.
         */
        val modifiers: GameConfig.LevelModifiers = modifiersOverride ?: spec.modifiers

        /**
         * KISITLI KADRO (M1): kule kilidi (bolum bazli acilma) VE harekat
         * kadrosu birlikte. Motorun `buildRejectionFor` kapisiyla ayni kural,
         * ayni sirayla.
         */
        val unlockedTowers: List<TowerType> =
            GameConfig.unlockedTowers(levelId).filter { modifiers.allows(it) }

        /** MEVZI TAVANI (M2): ayni anda tutulabilen azami kule; null = tavan yok. */
        val maxTowers: Int? = modifiers.maxTowers

        /** DONMUS MEVZI (M3): dalga basladiktan sonra YENI kule kurulamaz. */
        val buildLockedDuringWave: Boolean = modifiers.buildLockedDuringWave

        fun maxTier(type: TowerType): Int = GameConfig.maxTowerTier(type, levelId)

        private val coverCache = HashMap<Long, Float>()

        /** Pad'in TUM aktif rotalar uzerinde gordugu ORTALAMA yol (ref-px). */
        fun cover(pad: Pad, range: Float): Float {
            val key = pad.id.toLong() * 100_000L + (range * 10f).toLong()
            return coverCache.getOrPut(key) {
                routes.map { it.coveredLength(pad.x, pad.y, range) }.average().toFloat()
            }
        }

        /** Bolumde gorunen her dusman tipi ve KAC TANE geldigi. */
        val threatCounts: Map<EnemyType, Int> = run {
            val c = HashMap<EnemyType, Int>()
            waves.forEach { w -> w.spawns.forEach { c[it.enemyType] = (c[it.enemyType] ?: 0) + 1 } }
            c
        }

        /** Sahada gecerli can (act carpani DAHIL). */
        fun fieldHp(type: EnemyType): Float =
            GameConfig.ENEMY_SPECS.getValue(type).maxHp * actHpMul

        /** Bolumun toplam spawn penceresi (sn) — varis hizinin paydasi. */
        val spawnSeconds: Float = waves.sumOf { w ->
            (0.5f + w.spawns.dropLast(1).sumOf { it.delaySeconds.toDouble() }).toDouble()
        }.toFloat().coerceAtLeast(1f)

        /** Bu kule tipinin BU BOLUMUN karisimina karsi ortalama hasar carpani. */
        fun mixMultiplier(type: TowerType): Float {
            val totalHp = threatCounts.entries.sumOf { (it.value * fieldHp(it.key)).toDouble() }
                .coerceAtLeast(1.0)
            return threatCounts.entries.fold(0.0) { acc, e ->
                acc + (e.value * fieldHp(e.key)) * damageMultiplier(type, e.key)
            }.div(totalHp).toFloat()
        }

        /**
         * Bir pad'in menzilinde AYNI ANDA beklenen dusman sayisi.
         *
         * Bu terim olmadan model "20 piyade ayni anda menzilde" durumunu tek
         * hedefe kilitlenmis bir kule sanir ve her piyadeye tam DPS yazar; o
         * zaman acgozlu oyuncu tahtayi tek tip kuleyle doldurur ve simulator
         * bunu yeterli sanir. Varis hizi x menzilde gecirilen sure = anlik
         * doluluk; kulenin ilgisi bu sayiya BOLUNUR.
         */
        fun occupancy(pad: Pad, range: Float): Float {
            val cover = cover(pad, range)
            if (cover <= 0f) return 0f
            return threatCounts.entries.fold(0f) { acc, e ->
                val speed = GameConfig.ENEMY_SPECS.getValue(e.key).baseSpeed
                acc + (e.value / spawnSeconds) * (cover / speed)
            }
        }
    }

    /** Motorun `applyDamageToEnemy` carpani (splash zirhi bypass eder — DECISIONS B2). */
    fun damageMultiplier(type: TowerType, enemy: EnemyType): Float {
        val tower = GameConfig.TOWER_SPECS.getValue(type)
        val spec = GameConfig.ENEMY_SPECS.getValue(enemy)
        return if (tower.splashRadius > 0f) spec.splashVulnerability
        else 1f - (spec.armor * (1f - tower.armorPierce)).coerceAtLeast(0f)
    }

    // =======================================================================
    // Calisma zamani nesneleri
    // =======================================================================

    /**
     * @param damageMul meta Ates Gucu carpani ([MetaUpgrades.damageMultiplier]).
     * @param rangeMul meta Menzil carpani ([MetaUpgrades.rangeMultiplier]).
     *
     * Motorla BIREBIR: `TowerEntity` de carpani getter'in ICINDE uygular,
     * kullanim yerinde degil (bkz. `GameEntities.kt` meta blogu).
     */
    private class SimTower(
        val pad: Pad,
        val type: TowerType,
        val damageMul: Float = 1f,
        val rangeMul: Float = 1f
    ) {
        var tier: Int = 1
        var cooldown: Float = 0f
        val stats: GameConfig.TowerStats get() = GameConfig.TOWER_SPECS.getValue(type)
        val range: Float get() = stats.tier(tier).range * rangeMul
        val damage: Float get() = stats.tier(tier).damage * damageMul
        val interval: Float get() = stats.tier(tier).fireRate
    }

    private class SimEnemy(val type: EnemyType, val routeIndex: Int, hp0: Float, val reward: Int) {
        var arc: Float = 0f
        var hp: Float = hp0
        var slowFactor: Float = 0f
        var slowLeft: Float = 0f
        val spec: GameConfig.EnemyStats get() = GameConfig.ENEMY_SPECS.getValue(type)
    }

    private class SimProjectile(
        val kind: ProjectileType,
        val target: SimEnemy?,
        val tx: Float,
        val ty: Float,
        val damage: Float,
        val splashRadius: Float,
        val armorPierce: Float,
        val slowFactor: Float,
        val slowDuration: Float,
        val impactRadius: Float,
        val impactDamageFraction: Float,
        val slowPulseRadius: Float,
        var remaining: Float
    )

    // =======================================================================
    // Oyuncu modelleri
    // =======================================================================

    /**
     * Bes deterministik "dikkatli oyuncu" davranisi. Cozulebilirlik VAROLUSSAL
     * bir iddiadir ("makul bir oynanis bu bolumu geciriyor mu"), bu yuzden bir
     * bolum icin bunlardan HERHANGI birinin gecmesi yeterlidir. Hicbiri
     * gecmiyorsa bolum bir tasarim hatasidir.
     */
    enum class Playstyle(val forcedSlowTowers: Int = 0, val mono: Boolean = false) {
        /** Her an, Tedarik basina MARJINAL degeri en yuksek ALINABILIR hamle. */
        MARGINAL,

        /** En iyi hamleyi bekler: alinamiyorsa para BIRIKTIRIR (fuze/kademe 3). */
        MARGINAL_PATIENT,

        /** Once tahtayi doldurur (yatay buyume), sonra yukseltir (dikey). */
        MARGINAL_FILL,

        /** Bir Frost Field (alan kontrolu) acar, sonra marjinal acgozlu. */
        MARGINAL_SLOW1(forcedSlowTowers = 1),

        /** Iki Frost Field — yavaslatma kombosuna yaslanan oyuncu. */
        MARGINAL_SLOW2(forcedSlowTowers = 2),

        /** Karisima en uygun TEK kule tipini secip yalnizca onu kurar/yukseltir. */
        MONO_BEST(mono = true),

        /**
         * EKONOMI KATMANININ "TASARLANAN KADRO"SU — acik kule tipleri arasinda
         * acilis sirasinda ROUND-ROBIN (`SupplyBudgetModel.designedRoster` ile
         * ayni kural), her tip kendi menzili icin en iyi pad'e. Tahta dolunca
         * en ucuz yukseltmeden baslayarak derinlesir.
         *
         * Neden gerekli: marjinal acgozlu model, hafif siniflar doyduktan sonra
         * ham hasari maksimize ettigi icin zaman zaman TEK TIP (hep Gatling)
         * kadro kuruyordu ve zirh agirlikli gec bolumlerde takiliyordu. Bu
         * davranis "oyuncu her kule tipinden alir" varsayimini temsil eder ve
         * ekonominin SPI boleniyle AYNI kadroyu kurar.
         */
        DESIGNED_ROSTER,

        /** Hicbir sey kurmaz — "hicbir sey yapan KAYBETMELI" kontrolu. */
        IDLE,

        /**
         * **TAM BIR KULE** — en iyi pad, karisima en uygun tip, son kademeye
         * kadar yukseltilmis; baska hicbir sey kurulmaz.
         *
         * "Tek kule akisi gecemez" kuralinin DOGRUDAN olcumu. Onceki kilit bunu
         * `oldurme suresi >= en dar spawn araligi` ile temsil ediyordu; o oran
         * taban can kalibrasyonu x3,5'ten x1,1'e inince 6-7 kat zayifladi ve
         * pratikte hicbir seyi engellemez oldu. Bu davranis ayni iddiayi
         * oynayarak sinar: tek kule kadar guclu bir savunma bolumu GECEMEMELI.
         */
        SINGLE_TOWER
    }

    data class Outcome(
        val levelId: Int,
        val style: Playstyle,
        val cleared: Boolean,
        val livesLeft: Int,
        val maxLives: Int,
        val leaked: Int,
        /** Bolumun TABAN us cani (meta Tahkimat bonusu HARIC). */
        val baseMaxLives: Int,
        val wavesCleared: Int,
        val totalWaves: Int,
        val roster: String,
        val leftoverSupply: Int,
        val elapsedSeconds: Float,
        /** Dalga dalga "sizinti/kule sayisi/eldeki Tedarik" izi — teshis icin. */
        val trace: List<String> = emptyList()
    ) {
        /**
         * **META-NOTR** yildiz — motorun `starHealthFromLeaks` doktrini ile
         * BIREBIR: yildiz yalnizca SIZINTI SAYISINA bakar, Tahkimat'in verdigi
         * fazladan can yildiz satin almaz. Meta 0'da bu, eski
         * `livesLeft / maxLives` hesabiyla ozdestir (livesLeft = taban - sizinti).
         */
        val stars: Int
            get() = if (!cleared) {
                0
            } else {
                starsFor(starHealthFromLeaks(baseMaxLives, leaked), baseMaxLives)
            }
    }

    // =======================================================================
    // Bolumu bastan sona oyna
    // =======================================================================

    /**
     * @param startingSupplyOverride yalnizca "NE OLURDU" olcumleri icin. `null`
     *   ise bolumun gercek baslangic Tedariki kullanilir (kapi testlerinin
     *   kullandigi tek dogru deger).
     */
    fun play(
        model: LevelModel,
        style: Playstyle,
        startingSupplyOverride: Int? = null,
        meta: MetaUpgrades = MetaUpgrades()
    ): Outcome {
        // Meta etkileri `MetaUpgrades`in KENDI turetilmis alanlarindan okunur;
        // simulator kendi yukseltme matematigini YAZMAZ (GameEngine ile ayni kural).
        val damageMul = meta.damageMultiplier.toFloat()
        val rangeMul = meta.rangeMultiplier.toFloat()
        // Motor: `_gold = levelSpec.startingSupply + (meta - BASE)` (GameEngine:895).
        val supplyBonus = meta.startingSupply - EconomyConfig.BASE_STARTING_SUPPLY
        // Motor: `_lives = levelSpec.maxBaseLives + (meta - BASE)` (GameEngine:896).
        val livesBonus = meta.maxBaseHealth - EconomyConfig.BASE_MAX_HEALTH
        val effectiveMaxLives = model.spec.maxBaseLives + livesBonus

        var supply = startingSupplyOverride ?: (model.spec.startingSupply + supplyBonus)
        var lives = effectiveMaxLives
        var leaked = 0
        var elapsed = 0f
        val towers = ArrayList<SimTower>()
        val occupied = HashSet<Int>()
        val rng = Random(GameConfig.ROUTE_RNG_SEED_BASE + model.levelId * 7919L)

        // -------------------------------------------------------------------
        // OYUNCU MODELI — DOYUMA UGRAYAN MARJINAL DEGER
        //
        // Onceki (naif) model her kuleye "DPS x kapsama" yaziyordu ve iki
        // sistematik hata uretiyordu:
        //   · Frost Field'in 270'lik menzili en buyuk kapsamayi verdigi icin
        //     acgozlu oyuncu tahtayi HASAR VERMEYEN kulelerle dolduruyordu,
        //   · ortalama zirh carpani tanklari gizliyordu: kalabalik kulesi
        //     "ortalamada iyi" gorunup tanka HIC ise yaramiyordu.
        //
        // Yeni model sinif bazinda DOYUM kullanir. Her dusman sinifi icin
        // "bir govdeye teslim edilebilen toplam hasar" biriktirilir ve
        // sinifin degeri `min(1, teslim/can)` ile SINIRLANIR. Sonuc:
        //   · piyade doyduktan sonra bir Gatling daha SIFIR deger uretir,
        //   · tanka hicbir sey vurmuyorsa fuze aniden en degerli hamle olur,
        //   · [LevelModel.occupancy] terimi kalabalik pad'lerde tek hedefli
        //     kulenin ilgisini boler, yani "bir kule surunun tamamini keser"
        //     yanilgisi olusmaz.
        // Frost Field bu modele GIRMEZ (hasar vermez); yavaslatmaya yaslanan
        // oyunculari `MARGINAL_SLOW1/2` davranislari temsil eder.
        // -------------------------------------------------------------------
        val classes: List<EnemyType> = model.threatCounts.keys.toList()
        val classHp = FloatArray(classes.size) { model.fieldHp(classes[it]).coerceAtLeast(1f) }
        val classCount = FloatArray(classes.size) { model.threatCounts.getValue(classes[it]).toFloat() }
        val classSpeed = FloatArray(classes.size) {
            GameConfig.ENEMY_SPECS.getValue(classes[it]).baseSpeed
        }

        val attackTypes = model.unlockedTowers.filter { it != TowerType.SLOW }
        val monoType: TowerType? = if (style.mono) {
            attackTypes.maxByOrNull {
                val s = GameConfig.TOWER_SPECS.getValue(it)
                s.tier(1).dps * model.mixMultiplier(it) / s.buildCost
            }
        } else null
        val buildTypes: List<TowerType> =
            if (monoType != null) listOf(monoType) else attackTypes

        /**
         * Bu kulenin, o sinifin TEK bir govdesine teslim edebildigi hasar:
         * DPS x muhimmat carpani x (gordugu yol / dusmanin hizi).
         *
         * Kapsama BOLUNMEZ (bir zamanlar [LevelModel.occupancy] ile bolunuyordu):
         * bolen, buyuk kapsamali pad'i kucuk kapsamali pad ile ESITLIYOR ve
         * acgozlu oyuncu yolu gormeyen pad'lere kule kuruyordu — olculdu,
         * bolum 2'de kadro tek kuleye dusuyordu. Kalabaligin tek hedefli kuleyi
         * bolmesi [overkillMargin] ile temsil edilir.
         */
        fun perBodyDamage(pad: Pad, type: TowerType, tier: Int, ci: Int): Float {
            val row = GameConfig.TOWER_SPECS.getValue(type).tier(tier)
            val cover = model.cover(pad, row.range * rangeMul)
            if (cover <= 0f) return 0f
            return row.dps * damageMul * damageMultiplier(type, classes[ci]) *
                (cover / classSpeed[ci])
        }

        /** Kadronun sinif bazinda birikmis teslim gucu. */
        val delivered = FloatArray(classes.size)

        /**
         * DOYUM ESIGI — "govde basina canin kac kati teslim edilebiliyor".
         *
         * 1,0 KULLANILAMAZ: [perBodyDamage] bolum ORTALAMASI uzerinden calisir,
         * gercek dalga ise kumeli gelir. Esik 1,0 iken oyuncu "her sinif
         * doydu" deyip ALISVERISI BIRAKIYORDU — olculdu: L34'te 3.030
         * Tedariklik butcenin yalnizca iki kulesi kuruluyordu ve bolum ilk
         * dalgada kaybediliyordu. 3,0 = tepe dalga ortalamanin ~3 katina
         * cikabilir varsayimi; ayrica doyduktan SONRA da alisveris devam eder
         * (asagidaki ham hasar esitlik bozucusu).
         */
        val overkillMargin = 3f

        fun valueOf(d: FloatArray): Float {
            var v = 0f
            for (i in classes.indices) {
                val need = classHp[i] * overkillMargin
                // Doyum terimi (asil sinyal) + cok kucuk bir HAM hasar terimi.
                // Ikincisi olmadan doyduktan sonra her hamlenin degeri 0 olur ve
                // oyuncu elindeki parayi HIC harcamaz; gercek oyuncu harcar.
                v += classCount[i] * (classHp[i] * min(1f, d[i] / need) + 0.001f * d[i])
            }
            return v
        }

        class Move(
            val gain: Float,
            val cost: Int,
            val isBuild: Boolean,
            val delta: FloatArray,
            val apply: () -> Unit
        ) {
            val ratio: Float get() = gain / cost
        }

        /**
         * DONMUS MEVZI (M3) penceresi. Motorda `buildRejectionFor` yalnizca
         * `WAVE_RUNNING` durumunda reddeder; simulatorde de tam olarak dalga
         * dongusunun ICINDE kapanir, dalgalar ARASINDAKI hazirlik fazinda
         * (motor her dalgadan once PREPARATION'a doner) yeniden acilir.
         */
        var buildWindowOpen = true

        /** Motorun `EMPLACEMENT_CAP` + `WAVE_IN_PROGRESS` kapilariyla ayni kural. */
        fun canBuildNow(): Boolean {
            if (model.buildLockedDuringWave && !buildWindowOpen) return false
            val cap = model.maxTowers ?: return true
            return towers.size < cap
        }

        fun candidates(): List<Move> {
            val out = ArrayList<Move>()
            val base = valueOf(delivered)
            val scratch = FloatArray(classes.size)
            // Kisit YUKSELTMEYI etkilemez: motorda da yalnizca YENI kule
            // reddedilir (`buildTower`), `upgradeSelectedTower` serbest kalir.
            val buildsAllowed = canBuildNow()
            for (pad in if (buildsAllowed) model.pads else emptyList()) {
                if (pad.id in occupied) continue
                for (type in buildTypes) {
                    val stats = GameConfig.TOWER_SPECS.getValue(type)
                    val delta = FloatArray(classes.size) { perBodyDamage(pad, type, 1, it) }
                    if (delta.all { it <= 0f }) continue
                    for (i in classes.indices) scratch[i] = delivered[i] + delta[i]
                    val gain = valueOf(scratch) - base
                    if (gain <= 0f) continue
                    out.add(
                        Move(gain, stats.buildCost, true, delta) {
                            occupied.add(pad.id)
                            towers.add(SimTower(pad, type, damageMul, rangeMul))
                        }
                    )
                }
            }
            for (tw in towers) {
                if (tw.type == TowerType.SLOW) continue
                val cap = min(model.maxTier(tw.type), tw.stats.maxTier)
                if (tw.tier >= cap) continue
                val cost = tw.stats.upgradeCostFrom(tw.tier) ?: continue
                val delta = FloatArray(classes.size) {
                    perBodyDamage(tw.pad, tw.type, tw.tier + 1, it) -
                        perBodyDamage(tw.pad, tw.type, tw.tier, it)
                }
                for (i in classes.indices) scratch[i] = delivered[i] + delta[i]
                val gain = valueOf(scratch) - base
                if (gain <= 0f) continue
                out.add(Move(gain, cost, false, delta) { tw.tier++ })
            }
            return out
        }

        var slowBuilt = 0

        // Acilis sirasina gore acik kule tipleri (ekonomi katmaniyla AYNI kural).
        val unlockOrder = model.unlockedTowers.sortedWith(
            compareBy(
                { GameConfig.TOWER_SPECS.getValue(it).unlockedAtLevel },
                { GameConfig.TOWER_SPECS.getValue(it).buildCost },
                { it.name }
            )
        )

        /** [Playstyle.DESIGNED_ROSTER]: round-robin kur, sonra ucuzdan derinles. */
        fun spendDesigned() {
            var guard = 0
            while (guard++ < 400) {
                val nextType = unlockOrder[towers.size % unlockOrder.size]
                val stats = GameConfig.TOWER_SPECS.getValue(nextType)
                val range = stats.tier(1).range * rangeMul
                val pad = if (!canBuildNow()) null else model.pads.filter { it.id !in occupied }
                    .filter { model.cover(it, range) > 1f }
                    .maxByOrNull { model.cover(it, range) }
                if (pad != null) {
                    if (supply < stats.buildCost) return
                    supply -= stats.buildCost
                    occupied.add(pad.id)
                    towers.add(SimTower(pad, nextType, damageMul, rangeMul))
                    continue
                }
                // Tahta doldu -> en ucuz yukseltme.
                val upgrade = towers
                    .filter { it.tier < min(model.maxTier(it.type), it.stats.maxTier) }
                    .minByOrNull { it.stats.upgradeCostFrom(it.tier) ?: Int.MAX_VALUE }
                    ?: return
                val cost = upgrade.stats.upgradeCostFrom(upgrade.tier) ?: return
                if (supply < cost) return
                supply -= cost
                upgrade.tier++
            }
        }

        /** [Playstyle.SINGLE_TOWER]: tek kule, en iyi pad, son kademeye kadar. */
        fun spendSingleTower() {
            if (towers.isEmpty() && canBuildNow()) {
                val best = attackTypes.flatMap { type ->
                    val range = GameConfig.TOWER_SPECS.getValue(type).tier(1).range * rangeMul
                    model.pads.map { pad ->
                        Triple(type, pad, model.cover(pad, range) * model.mixMultiplier(type) *
                            GameConfig.TOWER_SPECS.getValue(type).tier(1).dps)
                    }
                }.maxByOrNull { it.third } ?: return
                val stats = GameConfig.TOWER_SPECS.getValue(best.first)
                if (supply < stats.buildCost) return
                supply -= stats.buildCost
                occupied.add(best.second.id)
                towers.add(SimTower(best.second, best.first, damageMul, rangeMul))
            }
            // Insa penceresi kapaliyken hic kule kurulamamis olabilir; bu
            // davranis o bolumu kaybeder ve KAYBETMESI dogrudur.
            val only = towers.firstOrNull() ?: return
            while (only.tier < min(model.maxTier(only.type), only.stats.maxTier)) {
                val cost = only.stats.upgradeCostFrom(only.tier) ?: return
                if (supply < cost) return
                supply -= cost
                only.tier++
            }
        }

        fun spend() {
            if (style == Playstyle.IDLE) return
            if (style == Playstyle.SINGLE_TOWER) return spendSingleTower()
            if (style == Playstyle.DESIGNED_ROSTER) return spendDesigned()
            var guard = 0
            while (guard++ < 400) {
                // Destek acilisi: Frost Field'lari yolu en cok goren pad'lere.
                // Kademe DEGISTIRMEZ (`slowFactor`/`slowPulseRadius` kademeden
                // bagimsiz), o yuzden kd.1'de birakilir.
                if (slowBuilt < style.forcedSlowTowers &&
                    TowerType.SLOW in model.unlockedTowers &&
                    canBuildNow()
                ) {
                    val slowStats = GameConfig.TOWER_SPECS.getValue(TowerType.SLOW)
                    val slowRange = slowStats.tier(1).range * rangeMul
                    val best = model.pads.filter { it.id !in occupied }
                        .maxByOrNull { model.cover(it, slowRange) }
                    if (best != null && model.cover(best, slowRange) > 1f) {
                        if (supply < slowStats.buildCost) return
                        supply -= slowStats.buildCost
                        occupied.add(best.id)
                        towers.add(SimTower(best, TowerType.SLOW, damageMul, rangeMul))
                        slowBuilt++
                        continue
                    }
                    slowBuilt = style.forcedSlowTowers
                }

                val all = candidates()
                if (all.isEmpty()) return
                val move: Move = when (style) {
                    // Sabirli oyuncu GLOBAL en iyiyi bekler; alinamiyorsa biriktirir.
                    Playstyle.MARGINAL_PATIENT -> all.maxByOrNull { it.ratio }!!
                    // Tahtayi once doldurur: bos pad varken yukseltme YAPMAZ.
                    Playstyle.MARGINAL_FILL -> {
                        val builds = all.filter { it.isBuild }
                        val pool = if (builds.isNotEmpty()) builds else all
                        pool.filter { it.cost <= supply }.maxByOrNull { it.ratio } ?: return
                    }
                    else -> all.filter { it.cost <= supply }.maxByOrNull { it.ratio } ?: return
                }
                if (move.cost > supply) return
                supply -= move.cost
                move.apply()
                for (i in classes.indices) delivered[i] += move.delta[i]
            }
        }

        spend()   // 10 sn'lik PREPARATION penceresi (bolum basi)

        val enemies = ArrayList<SimEnemy>()
        val projectiles = ArrayList<SimProjectile>()

        fun enemiesWithin(x: Float, y: Float, r: Float): List<SimEnemy> {
            val r2 = r * r
            return enemies.filter {
                val dx = model.routes[it.routeIndex].xAt(it.arc) - x
                val dy = model.routes[it.routeIndex].yAt(it.arc) - y
                dx * dx + dy * dy <= r2
            }
        }

        fun applyDamage(
            e: SimEnemy,
            raw: Float,
            armorPierce: Float,
            slowFactor: Float,
            slowDuration: Float,
            isSplash: Boolean
        ) {
            val final = if (isSplash) {
                raw * e.spec.splashVulnerability
            } else {
                raw * (1f - (e.spec.armor * (1f - armorPierce)).coerceAtLeast(0f))
            }
            e.hp -= final
            if (slowFactor > 0f) {
                if (e.slowLeft <= 0f) {
                    e.slowFactor = slowFactor
                    e.slowLeft = slowDuration
                } else {
                    e.slowFactor = max(e.slowFactor, slowFactor)
                    e.slowLeft = max(e.slowLeft, slowDuration)
                }
            }
        }

        fun impact(p: SimProjectile) {
            when {
                // CANNON — patlama: yaricaptaki HERKES, zirh BYPASS.
                p.splashRadius > 0f ->
                    enemiesWithin(p.tx, p.ty, p.splashRadius).forEach {
                        applyDamage(it, p.damage, p.armorPierce, p.slowFactor, p.slowDuration, true)
                    }
                // SLOW — cryo darbesi: yaricaptaki HERKES soguturr.
                p.slowPulseRadius > 0f ->
                    enemiesWithin(p.tx, p.ty, p.slowPulseRadius).forEach {
                        applyDamage(it, p.damage, p.armorPierce, p.slowFactor, p.slowDuration, false)
                    }
                // MISSILE — hedef olduyse fuze ISRAF; yalnizca carpma alani kalir.
                p.kind == ProjectileType.MISSILE -> {
                    val primary = p.target?.takeIf { t -> enemies.any { it === t } }
                    primary?.let {
                        applyDamage(it, p.damage, p.armorPierce, p.slowFactor, p.slowDuration, false)
                    }
                    if (p.impactRadius > 0f && p.impactDamageFraction > 0f) {
                        val splash = p.damage * p.impactDamageFraction
                        enemiesWithin(p.tx, p.ty, p.impactRadius).forEach {
                            if (it !== primary) applyDamage(it, splash, p.armorPierce, 0f, 0f, false)
                        }
                    }
                }
                // BULLET — birincil; olduyse 45 ref-px toleransinda en yakina.
                else -> {
                    val victim = p.target?.takeIf { t -> enemies.any { it === t } }
                        ?: enemiesWithin(
                            p.tx, p.ty, GameConfig.PROJECTILE_REDIRECT_TOLERANCE_REF_PX
                        ).minByOrNull {
                            val dx = model.routes[it.routeIndex].xAt(it.arc) - p.tx
                            val dy = model.routes[it.routeIndex].yAt(it.arc) - p.ty
                            dx * dx + dy * dy
                        }
                    victim?.let {
                        applyDamage(it, p.damage, p.armorPierce, p.slowFactor, p.slowDuration, false)
                    }
                }
            }
        }

        var wavesCleared = 0
        val trace = ArrayList<String>()
        for ((waveIdx, wave) in model.waves.withIndex()) {
            val leakedBefore = leaked
            val pending = ArrayList(wave.spawns)
            var timeUntilNextSpawn = 0.5f     // motor: setupWave
            enemies.clear()
            projectiles.clear()
            var t = 0f
            // Motorun hazirlik penceresi: bolum suresine sayilir, simulasyona degil.
            elapsed += GameConfig.PREPARATION_TIME_SECONDS.toFloat()

            // DONMUS MEVZI: dalga basliyor, insa penceresi KAPANIR. Motorda bu
            // an `PREPARATION -> WAVE_RUNNING` gecisidir (`startNextWaveNow`).
            buildWindowOpen = false

            while ((pending.isNotEmpty() || enemies.isNotEmpty()) && lives > 0 && t < WAVE_TIME_LIMIT) {
                // 1) SPAWN
                if (pending.isNotEmpty()) {
                    timeUntilNextSpawn -= DT
                    if (timeUntilNextSpawn <= 0f) {
                        val s = pending.removeAt(0)
                        val spec = GameConfig.ENEMY_SPECS.getValue(s.enemyType)
                        val routeIndex =
                            if (model.routes.size > 1) rng.nextInt(model.routes.size) else 0
                        enemies.add(
                            SimEnemy(
                                type = s.enemyType,
                                routeIndex = routeIndex,
                                hp0 = spec.maxHp * model.actHpMul,
                                // MOTORLA BIREBIR: Int * Float -> toInt (Double DEGIL).
                                reward = (spec.rewardGold * model.actRewardMul).toInt()
                                    .coerceAtLeast(1)
                            )
                        )
                        timeUntilNextSpawn = s.delaySeconds
                    }
                }

                // 2) HAREKET + yavaslatma sonumu + sizinti
                var i = 0
                while (i < enemies.size) {
                    val e = enemies[i]
                    if (e.slowLeft > 0f) {
                        e.slowLeft -= DT
                        if (e.slowLeft <= 0f) { e.slowLeft = 0f; e.slowFactor = 0f }
                    }
                    // Motor: currentSpeed = baseSpeed * (1 - slowFactor).
                    e.arc += e.spec.baseSpeed * (1f - e.slowFactor) * DT
                    if (e.arc >= model.routes[e.routeIndex].length) {
                        enemies.removeAt(i)
                        leaked++
                        lives -= GameConfig.BASE_REACHED_PENALTY_LIVES
                        if (lives <= 0) break
                    } else i++
                }
                if (lives <= 0) break

                // 3) HEDEFLEME + ATIS (sert menzil kapisi, tek hedef, FIRST)
                for (tw in towers) {
                    if (tw.cooldown > 0f) tw.cooldown -= DT
                    if (tw.cooldown > 0f) continue
                    val r2 = tw.range * tw.range
                    var target: SimEnemy? = null
                    for (e in enemies) {
                        val dx = model.routes[e.routeIndex].xAt(e.arc) - tw.pad.x
                        val dy = model.routes[e.routeIndex].yAt(e.arc) - tw.pad.y
                        if (dx * dx + dy * dy > r2) continue
                        val cur = target
                        if (cur == null || e.arc > cur.arc) target = e
                    }
                    val victim = target ?: continue
                    // Birikimli sayac: uzun vadeli atis hizi TAM dogru kalir.
                    tw.cooldown += tw.interval
                    val stats = tw.stats
                    val kind = when (tw.type) {
                        TowerType.MACHINE_GUN -> ProjectileType.BULLET
                        TowerType.CANNON -> ProjectileType.CANNON_SHELL
                        TowerType.ANTI_ARMOR -> ProjectileType.MISSILE
                        TowerType.SLOW -> ProjectileType.FROST_PULSE
                    }
                    val speed = GameConfig.PROJECTILE_SPEEDS[kind] ?: 300f
                    projectiles.add(
                        SimProjectile(
                            kind = kind,
                            target = victim,
                            tx = model.routes[victim.routeIndex].xAt(victim.arc),
                            ty = model.routes[victim.routeIndex].yAt(victim.arc),
                            damage = tw.damage,
                            splashRadius = stats.splashRadius,
                            armorPierce = stats.armorPierce,
                            slowFactor = stats.slowFactor,
                            slowDuration = stats.slowDuration,
                            impactRadius = stats.missileImpactRadius,
                            impactDamageFraction = stats.missileImpactDamageFraction,
                            slowPulseRadius = stats.slowPulseRadius,
                            // Motor: progress += dt*speed/100 -> ucus = 100/speed sn.
                            remaining = 100f / speed
                        )
                    )
                }

                // 4) MERMILER
                var p = 0
                while (p < projectiles.size) {
                    val proj = projectiles[p]
                    proj.remaining -= DT
                    if (proj.remaining <= 0f) {
                        projectiles.removeAt(p)
                        impact(proj)
                    } else p++
                }

                // 5) OLUMLER -> Tedarik
                var gained = 0
                var k = 0
                while (k < enemies.size) {
                    if (enemies[k].hp <= 0f) {
                        gained += enemies[k].reward
                        enemies.removeAt(k)
                    } else k++
                }
                if (gained > 0) {
                    supply += gained
                    spend()
                }

                t += DT
            }

            elapsed += t
            trace.add(
                "W${waveIdx + 1}[govde=${wave.spawns.size} sizinti=${leaked - leakedBefore} " +
                    "can=$lives kule=${towers.size} tedarik=$supply]"
            )
            if (lives <= 0) {
                return Outcome(
                    model.levelId, style, false, 0, effectiveMaxLives, leaked,
                    model.spec.maxBaseLives,
                    wavesCleared, model.waves.size, rosterOf(towers), supply, elapsed, trace
                )
            }
            wavesCleared++
            if (waveIdx < model.waves.lastIndex) supply += GameConfig.WAVE_CLEAR_SUPPLY_BONUS
            // Motor dalga bitince PREPARATION'a doner: insa penceresi ACILIR.
            buildWindowOpen = true
            spend()
        }

        return Outcome(
            model.levelId, style, true, lives, effectiveMaxLives, leaked,
            model.spec.maxBaseLives,
            wavesCleared, model.waves.size, rosterOf(towers), supply, elapsed, trace
        )
    }

    private fun rosterOf(towers: List<SimTower>): String {
        if (towers.isEmpty()) return "-"
        val short = mapOf(
            TowerType.MACHINE_GUN to "MG",
            TowerType.CANNON to "CN",
            TowerType.ANTI_ARMOR to "AA",
            TowerType.SLOW to "FR"
        )
        return towers.groupingBy { "${short[it.type]}${it.tier}" }.eachCount()
            .entries.sortedBy { it.key }
            .joinToString("+") { "${it.value}x${it.key}" }
    }

    /** Denenen "dikkatli oyuncu" davranislari, sirayla. */
    val CAREFUL_STYLES: List<Playstyle> = listOf(
        Playstyle.MARGINAL,
        Playstyle.DESIGNED_ROSTER,
        Playstyle.MARGINAL_FILL,
        Playstyle.MARGINAL_PATIENT,
        Playstyle.MARGINAL_SLOW1,
        Playstyle.MARGINAL_SLOW2,
        Playstyle.MONO_BEST
    )

    /** ILK gecen davranisi dondurur; hicbiri gecemezse en uzaga giden kosuyu. */
    fun bestOutcome(
        levelId: Int,
        startingSupplyOverride: Int? = null,
        meta: MetaUpgrades = MetaUpgrades()
    ): Outcome {
        val model = LevelModel(levelId)
        var best: Outcome? = null
        for (style in CAREFUL_STYLES) {
            val outcome = play(model, style, startingSupplyOverride, meta)
            if (outcome.cleared) return outcome
            val b = best
            if (b == null || outcome.wavesCleared > b.wavesCleared || outcome.leaked < b.leaked) {
                best = outcome
            }
        }
        return best!!
    }

    /** Ayni bolumu TUM davranislarla kosar (rapor/olcum icin). */
    fun allOutcomes(levelId: Int, meta: MetaUpgrades = MetaUpgrades()): List<Outcome> {
        val model = LevelModel(levelId)
        return CAREFUL_STYLES.map { play(model, it, null, meta) }
    }
}
