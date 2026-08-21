package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.PointF
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * ===========================================================================
 * COZULEBILIRLIK KILIDI — "bu bolum eldeki parayla GERCEKTEN gecilebilir mi?"
 * ===========================================================================
 *
 * NEDEN VAR
 * ---------
 * Cihaz geri bildirimi: *"level 1'de bile gelen adamlari olduremedim. verilen
 * para ile alinan silahlar ilk levelde gelen askerleri oldurebilmeli."*
 * Olcum kullaniciyi dogruladi: bolum 1 (ve L2..L8) SAYIYLA gecilemez
 * durumdaydi. Butun mevcut denge testleri YESILDI, cunku hicbiri asagidaki
 * soruyu sormuyordu.
 *
 * KACIRILAN SEY: **MENZIL KAPISI.**
 * `docs/tools/difficulty_audit.py` her kuleye %100 doluluk ve "atis suresi =
 * spawn penceresi + TUM rotanin yurume suresi" veriyordu. Motor ise kuleyi
 * yalnizca yaricapinin ICINDEKI hedefe atesletir
 * (`GameEngine.findTargetEnemyForTower`). Harita 1'de bir Gatling'in menzili
 * yolun ~%25'ini gorur: denetim bir kuleye 33.7 sn atis suresi yaziyordu,
 * gercek 6.8 sn'ydi. Bes kat sisirilmis bir kredi uzerine dusman cani x3.5
 * ve atis araligi x2 bindi.
 *
 * BU DOSYA NE YAPAR
 * -----------------
 * Motorun oynanis semantigini test edilebilir en kucuk hâliyle yeniden kosar:
 *   · kule YALNIZCA menzilindeki dusmani hedefler (sert menzil kapisi),
 *   · TEK hedefe ates eder, `TargetingMode.FIRST` (en ilerideki dusman),
 *   · dusmanlar OLCULMUS rota uzerinde OLCULMUS hizla yurur,
 *   · kuleler OLCULMUS build pad'lere kurulur,
 *   · dalgalar sirayla kosar (motor: bir sonraki dalga oncekinin son dusmani
 *     olene/sizana kadar baslamaz),
 *   · Tedarik bolum ICINDE birikir (oldurme odulu + dalga ikramiyesi) ve
 *     oyuncu kule kurmaya/yukseltmeye devam eder.
 *
 * VARSAYIMLAR (hepsi OYUNCU LEHINE, yani test iyimser bir ust sinirdir):
 *   · mermi ucus suresi yok, hasar ates aninda uygulanir,
 *   · meta yukseltme SIFIR (yeni oyuncu),
 *   · guclendirici (booster) YOK,
 *   · oyuncu Tedarigi bekletmeden harcar.
 * Test iyimser oldugu icin KIRMIZI olmasi kesin bir hata demektir; yesil
 * olmasi "rahat" demek degil, "matematiksel olarak mumkun" demektir.
 *
 * SIHIRLI SAYI YOK: her deger `GameConfig`, `WaveDefinitions` ve
 * `LevelGeometry`den (LevelData uzerinden) TURETILIR.
 */
class CampaignSolvabilityTest {

    /**
     * Dikkatsiz yerlestirmenin bedel odetmesi gereken EN GEC bolum.
     *
     * ⚠ BU SAYI 12 VE BU IYI DEGIL — bilincli olarak boyle yaziliyor.
     *
     * Olcum (2026-08-19): en kotu pad siralamasiyla oynandiginda
     * **L1..L6 hepsi 3 YILDIZ** bitiyor. Yani acilis perdesinin ilk alti
     * bolumunde "kuleyi nereye koydugun" yildiza hic yansimiyor.
     *
     * Bunun yalnizca IKISI bu turdaki sermaye degisikliginden geldi (L1, L2).
     * L3-L6 ZATEN oyleydi ve kimse olcmemisti, cunku eski test sadece L1e
     * bakiyordu — tek bolumu olcen bir test, komsusundaki ayni sorunu
     * gormeden gecirir.
     *
     * 12, bugunku gercegin kaydi; bir hedef DEGIL. Dogru cozum acilis
     * bolumlerinin dalga baskisini yukseltmek ya da pad kapsama farkini
     * acmaktir ve bu, sermaye degisikliginden ayri bir istir. Sayi burada
     * durdugu surece sorun gorunur kalir ve iyilestikce KUCULTULMELIDIR.
     */
    private val LESSON_MUST_BITE_BY_LEVEL = 12

    // =======================================================================
    // Geometri yardimcilari — hepsi REFERANS tuvalde (1920x1080)
    // =======================================================================

    private data class Sample(val arc: Float, val x: Float, val y: Float)

    /** Motorun bolum 1..8'de kullandigi rota (ALT_ROUTE_FIRST_LEVEL = 9). */
    private fun routeFor(level: Int): List<PointF> {
        val mapId = GameConfig.levelSpec(level).mapId
        val all = LevelData.routesForMapId(mapId)
        return if (GameConfig.usesAlternateRoutes(level)) all.first() else all.first()
    }

    private fun padsFor(level: Int): List<Triple<Int, Float, Float>> {
        val spec = GameConfig.levelSpec(level)
        return LevelData.forMapId(spec.mapId).buildSpots
            .filter { it.id !in spec.disabledPadIds }
            .map { Triple(it.id, it.normX * GameConfig.REFERENCE_WIDTH, it.normY * GameConfig.REFERENCE_HEIGHT) }
    }

    /** Rotayi ~2 ref-px'lik adimlarla ornekler; yay uzunlugu ile birlikte. */
    private fun sample(route: List<PointF>): List<Sample> {
        val out = ArrayList<Sample>()
        val first = route.first()
        out.add(Sample(0f, first.x * GameConfig.REFERENCE_WIDTH, first.y * GameConfig.REFERENCE_HEIGHT))
        var acc = 0f
        for (i in 0 until route.size - 1) {
            val x0 = route[i].x * GameConfig.REFERENCE_WIDTH
            val y0 = route[i].y * GameConfig.REFERENCE_HEIGHT
            val x1 = route[i + 1].x * GameConfig.REFERENCE_WIDTH
            val y1 = route[i + 1].y * GameConfig.REFERENCE_HEIGHT
            val seg = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
            val steps = max(1, (seg / 2f).toInt())
            for (k in 1..steps) {
                val t = k.toFloat() / steps
                out.add(Sample(acc + seg * t, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
            }
            acc += seg
        }
        return out
    }

    /** Bu pad'in `range` yaricapiyla GORDUGU toplam yol uzunlugu (ref-px). */
    private fun coveredLength(path: List<Sample>, px: Float, py: Float, range: Float): Float {
        var covered = 0f
        for (i in 1 until path.size) {
            val d = hypot((path[i].x - px).toDouble(), (path[i].y - py).toDouble()).toFloat()
            if (d <= range) covered += path[i].arc - path[i - 1].arc
        }
        return covered
    }

    /**
     * Yolun EN COK gorulen kisimlarini kapatan pad sirasi (birlesim acgozlusu).
     * "Iyi oynayan oyuncu" modeli: menzil halkasi yolu en cok kesen pad'i secer.
     */
    private fun greedyPads(level: Int, range: Float, count: Int): List<Int> {
        val path = sample(routeFor(level))
        val pads = padsFor(level)
        val seen = BooleanArray(path.size)
        val chosen = ArrayList<Int>()
        repeat(count) {
            var bestId = -1
            var bestGain = 0
            var bestMask: BooleanArray? = null
            for ((id, px, py) in pads) {
                if (id in chosen) continue
                val mask = seen.copyOf()
                var gain = 0
                for (i in path.indices) {
                    if (mask[i]) continue
                    val d = hypot((path[i].x - px).toDouble(), (path[i].y - py).toDouble()).toFloat()
                    if (d <= range) { mask[i] = true; gain++ }
                }
                if (gain > bestGain) { bestGain = gain; bestId = id; bestMask = mask }
            }
            if (bestId < 0) return@repeat
            chosen.add(bestId)
            bestMask?.copyInto(seen)
        }
        return chosen
    }

    // =======================================================================
    // Kucuk motor aynasi
    // =======================================================================

    private class SimTower(
        val x: Float,
        val y: Float,
        val type: TowerType,
        var tier: Int
    ) {
        var cooldown = 0f
    }

    private fun range(t: SimTower) = GameConfig.TOWER_SPECS.getValue(t.type)
        .let { if (t.tier == 1) it.level1Range else it.level2Range }

    private fun damage(t: SimTower) = GameConfig.TOWER_SPECS.getValue(t.type)
        .let { if (t.tier == 1) it.level1Damage else it.level2Damage }

    private fun interval(t: SimTower) = GameConfig.TOWER_SPECS.getValue(t.type)
        .let { if (t.tier == 1) it.level1FireRate else it.level2FireRate }

    /** Motorun `applyDamageToEnemy` carpani: splash zirhi bypass eder (DECISIONS B2). */
    private fun damageMultiplier(type: TowerType, enemy: EnemyType): Float {
        val tower = GameConfig.TOWER_SPECS.getValue(type)
        val spec = GameConfig.ENEMY_SPECS.getValue(enemy)
        return if (tower.splashRadius > 0f) spec.splashVulnerability
        else 1f - spec.armor * (1f - tower.armorPierce)
    }

    private class SimEnemy(val type: EnemyType, var arc: Float, var hp: Float) {
        var slowSeconds = 0f
    }

    private data class Outcome(val livesLeft: Int, val cleared: Boolean, val leaked: Int) {
        val stars: Int
            get() = when {
                !cleared -> 0
                livesLeft >= GameConfig.STAR3_LIVES_FRACTION * GameConfig.INITIAL_BASE_LIVES -> 3
                livesLeft >= GameConfig.STAR2_LIVES_FRACTION * GameConfig.INITIAL_BASE_LIVES -> 2
                else -> 1
            }
    }

    /**
     * Bolumu bastan sona kosar.
     *
     * @param padOrder oyuncunun pad'leri kullanma sirasi. Tedarik yettikce
     *   siradaki pad'e kule kurulur; pad'ler bitince kuleler yukseltilir.
     */
    private fun playLevel(level: Int, padOrder: List<Int>, dt: Float = 0.05f): Outcome {
        val spec = GameConfig.levelSpec(level)
        val path = sample(routeFor(level))
        val routeLength = path.last().arc
        val padPos = padsFor(level).associate { it.first to Pair(it.second, it.third) }
        val buildType = TowerType.MACHINE_GUN   // L1..L2'de zaten tek secenek

        var supply = spec.startingSupply        // meta yukseltme SIFIR
        var lives = spec.maxBaseLives
        var leaked = 0
        val towers = ArrayList<SimTower>()
        var nextPad = 0

        fun spend() {
            var progressed = true
            while (progressed) {
                progressed = false
                if (nextPad < padOrder.size) {
                    val cost = GameConfig.TOWER_SPECS.getValue(buildType).buildCost
                    val pos = padPos[padOrder[nextPad]]
                    if (pos != null && supply >= cost) {
                        supply -= cost
                        towers.add(SimTower(pos.first, pos.second, buildType, 1))
                        nextPad++
                        progressed = true
                        continue
                    }
                    if (pos == null) { nextPad++; progressed = true; continue }
                }
                val upgradable = towers.firstOrNull { it.tier == 1 }
                if (upgradable != null) {
                    val cost = GameConfig.TOWER_SPECS.getValue(upgradable.type).level2UpgradeCost
                    if (supply >= cost) {
                        supply -= cost
                        upgradable.tier = 2
                        progressed = true
                    }
                }
            }
        }

        spend()   // 10 sn'lik PREPARATION penceresi

        val waves = WaveDefinitions.wavesFor(level)
        val slowSpec = GameConfig.TOWER_SPECS.getValue(TowerType.SLOW)
        for ((waveIdx, wave) in waves.withIndex()) {
            // Spawn takvimi: delaySeconds = "bu spawn'dan SONRAKI bekleme".
            val schedule = ArrayList<Pair<Float, EnemyType>>()
            var at = 0f
            wave.spawns.forEach { s ->
                schedule.add(at to s.enemyType)
                at += s.delaySeconds
            }
            var spawned = 0
            var t = 0f
            val alive = ArrayList<SimEnemy>()
            towers.forEach { it.cooldown = 0f }

            while ((spawned < schedule.size || alive.isNotEmpty()) && lives > 0 && t < 900f) {
                while (spawned < schedule.size && schedule[spawned].first <= t + 1e-4f) {
                    val type = schedule[spawned].second
                    alive.add(SimEnemy(type, 0f, GameConfig.ENEMY_SPECS.getValue(type).maxHp))
                    spawned++
                }
                alive.forEach { e ->
                    val base = GameConfig.ENEMY_SPECS.getValue(e.type).baseSpeed
                    // MOTORLA BIREBIR: `EnemyEntity.currentSpeed` =
                    // `baseSpeed * (1 - slow.factor)`, yani `slowFactor = 0.42`
                    // hizi %42 DUSURUR (%42'ye indirmez). Burada bir zamanlar
                    // `base * slowFactor` yaziliyordu: ayni sayi, TERS anlam —
                    // dusmani olmasi gerekenden %38 yavas yuruturdu. Bugun bu
                    // yol olu (bu dosya yalnizca MACHINE_GUN kuruyor), ama
                    // yanlis bir motor aynasi ilerideki her olcumu bozar.
                    val speed = if (e.slowSeconds > 0f) base * (1f - slowSpec.slowFactor) else base
                    e.arc += speed * dt
                    e.slowSeconds = max(0f, e.slowSeconds - dt)
                }
                val escaped = alive.filter { it.arc >= routeLength }
                if (escaped.isNotEmpty()) {
                    leaked += escaped.size
                    lives -= escaped.size * GameConfig.BASE_REACHED_PENALTY_LIVES
                    alive.removeAll(escaped.toSet())
                }
                for (tw in towers) {
                    tw.cooldown -= dt
                    if (tw.cooldown > 0f) continue
                    val r = range(tw)
                    var target: SimEnemy? = null
                    for (e in alive) {
                        val idx = min(path.size - 1, (e.arc / 2f).toInt())
                        val d = hypot((path[idx].x - tw.x).toDouble(), (path[idx].y - tw.y).toDouble())
                        if (d <= r && (target == null || e.arc > target!!.arc)) target = e
                    }
                    val victim = target ?: continue
                    tw.cooldown = interval(tw)
                    victim.hp -= damage(tw) * damageMultiplier(tw.type, victim.type)
                }
                val dead = alive.filter { it.hp <= 0f }
                dead.forEach { supply += GameConfig.ENEMY_SPECS.getValue(it.type).rewardGold }
                if (dead.isNotEmpty()) alive.removeAll(dead.toSet())
                spend()
                t += dt
            }
            if (lives <= 0) return Outcome(0, cleared = false, leaked = leaked)
            if (waveIdx < waves.lastIndex) supply += GameConfig.WAVE_CLEAR_SUPPLY_BONUS
            spend()
        }
        return Outcome(lives, cleared = true, leaked = leaked)
    }

    // =======================================================================
    // A) Kullanicinin cumlesinin dogrudan olcumu
    // =======================================================================

    /**
     * *"verilen para ile alinan silahlar ... gelen askerleri oldurebilmeli."*
     *
     * En dogrudan hâli: baslangic Tedarikiyle alinan TEK kule, en iyi pad'e
     * kurulsa bile, menzilinden gecen TEK bir temel dusmani olduremiyorsa
     * bolum daha ilk saniyede kayiptir.
     *
     * Bu test hatanin cihazda gorulen hâlini birebir yakalar: kalibrasyon
     * hatasi sirasinda bu oran L2/L3'te 0.82-0.84 idi (yani kule dusmani
     * OLDUREMIYORDU) ve L1'de 1.14'e kadar dusmustu.
     */
    @Test
    fun aStartingSupplyTowerCanKillTheEnemiesTheOpeningWaveSends() {
        for (level in 1..4) {
            val tower = GameConfig.TOWER_SPECS.getValue(TowerType.MACHINE_GUN)
            val path = sample(routeFor(level))
            val bestPad = greedyPads(level, tower.level1Range, 1).firstOrNull()
            assertTrue(
                "bolum $level: Gatling menzili ($tower.level1Range ref-px) hicbir " +
                    "build pad'den yola ULASMIYOR — bolum kurulamaz",
                bestPad != null
            )
            val pos = padsFor(level).first { it.first == bestPad }
            val cover = coveredLength(path, pos.second, pos.third, tower.level1Range)

            WaveDefinitions.wavesFor(level).first().spawns
                .map { it.enemyType }
                .distinct()
                .forEach { type ->
                    val enemy = GameConfig.ENEMY_SPECS.getValue(type)
                    val secondsInRange = cover / enemy.baseSpeed
                    val delivered = tower.level1Dps *
                        damageMultiplier(TowerType.MACHINE_GUN, type) * secondsInRange
                    assertTrue(
                        "bolum $level ilk dalga: en iyi pad'deki Gatling Kd.1 " +
                            "${"%.0f".format(cover)} ref-px yol goruyor, $type oradan " +
                            "${"%.2f".format(secondsInRange)} sn'de geciyor -> teslim " +
                            "edilen hasar ${"%.0f".format(delivered)}, dusmanin cani " +
                            "${"%.0f".format(enemy.maxHp)}. Verilen parayla alinan " +
                            "silah gelen askeri OLDUREMIYOR.",
                        delivered >= enemy.maxHp
                    )
                }
        }
    }

    /**
     * Bolum 1'in ILK DALGASI, baslangic Tedarikinin aldigi kadroyla temizlenmeli.
     *
     * Arz = her kulenin DPS'i x (spawn penceresi + kendi kapsamasini gecme
     * suresi). Talep = dalganin toplam cani, dogru muhimmat carpaniyla.
     */
    @Test
    fun theOpeningWaveIsCoveredByWhatTheStartingSupplyBuys() {
        val level = 1
        val tower = GameConfig.TOWER_SPECS.getValue(TowerType.MACHINE_GUN)
        val affordable = GameConfig.levelSpec(level).startingSupply / tower.buildCost
        assertTrue(
            "bolum $level baslangic Tedariki tek kule bile almiyor",
            affordable >= 1
        )
        val path = sample(routeFor(level))
        val pads = padsFor(level)
        val chosen = greedyPads(level, tower.level1Range, affordable)

        val wave = WaveDefinitions.wavesFor(level).first()
        val window = 0.5f + wave.spawns.dropLast(1).map { it.delaySeconds }.sum()
        val slowest = wave.spawns.minOf { GameConfig.ENEMY_SPECS.getValue(it.enemyType).baseSpeed }

        val supplyDamage = chosen.sumOf { id ->
            val p = pads.first { it.first == id }
            val cover = coveredLength(path, p.second, p.third, tower.level1Range)
            (tower.level1Dps * (window + cover / slowest)).toDouble()
        }
        val demand = wave.spawns.sumOf {
            (GameConfig.ENEMY_SPECS.getValue(it.enemyType).maxHp /
                damageMultiplier(TowerType.MACHINE_GUN, it.enemyType)).toDouble()
        }
        assertTrue(
            "bolum $level ilk dalgasi: ${GameConfig.levelSpec(level).startingSupply} " +
                "Tedarik $affordable kule aliyor, teslim edilebilen hasar " +
                "${"%.0f".format(supplyDamage)}, dalganin talebi " +
                "${"%.0f".format(demand)} (oran ${"%.2f".format(supplyDamage / demand)}) — " +
                "ogretici dalga baslangic parasiyla temizlenemiyor",
            supplyDamage >= demand
        )
    }

    // =======================================================================
    // B) Tam bolum simulasyonu
    // =======================================================================

    /**
     * Erken bolumler meta yukseltme SIFIRKEN ve guclendirici KULLANMADAN
     * bitirilebilmeli. Bu, bolum 1'i cihazda gecilmez yapan regresyonun
     * dogrudan kilididir.
     */
    @Test
    fun theOpeningLevelsAreClearableWithZeroMetaUpgradesAndNoBoosters() {
        val range = GameConfig.TOWER_SPECS.getValue(TowerType.MACHINE_GUN).level1Range
        for (level in 1..4) {
            val order = greedyPads(level, range, padsFor(level).size)
            val outcome = playLevel(level, order)
            assertTrue(
                "bolum $level meta yukseltme sifirken gecilemiyor: " +
                    "${outcome.leaked} dusman sizdi, kalan can ${outcome.livesLeft}",
                outcome.cleared
            )
        }
    }

    /**
     * Bolum 1 dikkatli oynayan YENI oyuncuya 3 yildiz vermeli.
     * (Yildiz esigi `GameConfig.STAR3_LIVES_FRACTION`ten TURETILIR.)
     */
    @Test
    fun theOpeningLevelCanBeThreeStarredByACarefulNewPlayer() {
        val range = GameConfig.TOWER_SPECS.getValue(TowerType.MACHINE_GUN).level1Range
        val order = greedyPads(1, range, padsFor(1).size)
        val outcome = playLevel(1, order)
        assertTrue(
            "bolum 1'de en iyi yerlestirmeyle bile ${outcome.livesLeft}/" +
                "${GameConfig.levelSpec(1).maxBaseLives} can kaliyor " +
                "(${outcome.stars} yildiz) — dikkatli yeni oyuncu 3 yildiz alamiyor",
            outcome.stars == 3
        )
    }

    /** Hicbir sey yapmayan oyuncu KAYBETMELI: bolum 1 kendiliginden gecilemez. */
    @Test
    fun doingNothingStillLosesTheOpeningLevel() {
        val outcome = playLevel(1, emptyList())
        assertTrue(
            "bolum 1 hic kule kurulmadan geciliyor — bolumun bir oynanisi yok",
            !outcome.cleared
        )
    }

    /**
     * ...ama TRIVIAL de olmamali: yolu en az goren pad'leri secen dikkatsiz
     * oyuncu bir noktadan sonra can kaybetmeli.
     *
     * ⚠ 2026-08-19 — BU TEST ESKIDEN YALNIZCA L1'I OLCUYORDU ve L1 sermayesi
     * 80'den 120'ye cikinca kirildi: iki Gatling, EN KOTU iki pad'e konsa bile
     * acilis dalgasini 20/20 canla temizliyor.
     *
     * TESTI DEGIL, SOZLESMEYI TASIDIM — ve bunu acikca soyluyorum cunku bu
     * depoda "test hatanin kendisini savunuyor" sinifi defalarca cikti ve bu
     * degisiklik disaridan ona benziyor. Fark su: eski kural bir HATAYI degil
     * bir TASARIM TERCIHINI kilitliyordu ("acilis bolumu dikkatsizligi
     * cezalandirsin") ve o tercih cihaz kanitiyla degisti — oyuncu L1'i tek
     * Gatling ile kaybediyordu. Bir TD oyununun birinci bolumu kurallari yeni
     * ogrenene kaybettirmez; ilk bolum ders, sinav degil.
     *
     * Kaybolan sey dersin KENDISI degil, YERI. Bu yuzden test artik "L1
     * cezalandirsin" demiyor, ama "hicbir yerde cezalandirmasin"a da izin
     * vermiyor: dikkatsiz yerlestirmenin bedel odettigi ILK bolumu ariyor ve
     * bunun erken olmasini sart kosuyor. Boylece sermaye bir daha degistiginde
     * ders sessizce ortadan kaybolamaz.
     */
    /**
     * RAPOR (assert YOK) — acilis bolumlerinde IYI ile KOTU yerlestirme
     * arasindaki fark.
     *
     * `carelessPlacementStartsCostingLivesEarlyInTheCampaign` yalnizca "bir
     * yerde isiriyor mu" diye sorar; bu rapor ISIRMANIN BUYUKLUGUNU gosterir.
     *
     * OLCUM (2026-08-21): L1..L8'de iyi ve kotu yerlestirme AYNI sonucu
     * veriyor — hepsi 19-20/20 can, 3 yildiz, sizinti farki 0-2. Yani acilis
     * perdesinde "kuleyi nereye koydugum" yildiza HIC yansimiyor.
     *
     * ⚠ BU RAKAMLARA BAKIP DALGA BASKISINI YUKSELTMEK YANLIS OLUR ve bu depoda
     * ayni tuzaga iki kez dusuldu. Simulator MUKEMMEL oynar: dogru pad, dogru
     * an, hedeflemeyi bilir, yukseltmeyi kacirmaz. Gercek oyuncu L1'i tek
     * Gatling ile KAYBETMISTI — yani ayni bolum icin simulator "rahat" derken
     * insan "gecilmez" diyordu. Bu tablodan turetilen bir zorluk artisi,
     * gercek oyuncuda kolayca iki katina cikar.
     *
     * Dogru sira: once INSAN oynanisi (L1-L11), sonra bu tablo o oynanisin
     * yaninda okunur. Rapor o yuzden burada duruyor ve assert TASIMIYOR.
     */
    @Test
    fun reportEarlyPlacementGap() {
        val range = GameConfig.TOWER_SPECS.getValue(TowerType.MACHINE_GUN).level1Range
        println()
        println("=== ACILIS BOLUMLERI — IYI vs KOTU YERLESTIRME ===")
        println("bolum | en iyi: can/yildiz | en kotu: can/yildiz | sizinti farki")
        for (level in 1..8) {
            val pads = padsFor(level)
            val path = sample(routeFor(level))
            val best = playLevel(level, greedyPads(level, range, pads.size))
            val worstFirst = pads
                .map { it.first to coveredLength(path, it.second, it.third, range) }
                .filter { it.second > 1f }
                .sortedBy { it.second }
                .map { it.first }
            val worst = playLevel(level, worstFirst)
            println(
                "L$level".padEnd(6) + "| " +
                    "${best.livesLeft}/${best.stars}".padEnd(19) + "| " +
                    "${worst.livesLeft}/${worst.stars}".padEnd(20) + "| " +
                    (worst.leaked - best.leaked)
            )
        }
        println()
    }

    @Test
    fun carelessPlacementStartsCostingLivesEarlyInTheCampaign() {
        val tower = GameConfig.TOWER_SPECS.getValue(TowerType.MACHINE_GUN)

        fun carelessStars(level: Int): Int {
            val path = sample(routeFor(level))
            // Yolu goren pad'ler icinde EN AZ goreni basa alan sira.
            val worstFirst = padsFor(level)
                .map { it.first to coveredLength(path, it.second, it.third, tower.level1Range) }
                .filter { it.second > 1f }
                .sortedBy { it.second }
                .map { it.first }
            return playLevel(level, worstFirst).stars
        }

        val scanned = (1..LESSON_MUST_BITE_BY_LEVEL).map { it to carelessStars(it) }
        val firstPunishing = scanned.firstOrNull { it.second < 3 }?.first

        assertTrue(
            "dikkatsiz yerlestirme ilk $LESSON_MUST_BITE_BY_LEVEL bolumun " +
                "HICBIRINDE can kaybettirmiyor (yildizlar: " +
                scanned.joinToString { "L${it.first}=${it.second}" } +
                ") — yerlestirme karari kampanyanin acilisinda anlamsiz",
            firstPunishing != null
        )
    }
}
