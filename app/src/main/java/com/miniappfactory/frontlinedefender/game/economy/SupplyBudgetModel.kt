package com.miniappfactory.frontlinedefender.game.economy

/**
 * Faz 10 — SAVAS ICI **TEDARIK** BUTCE MODELI (ECONOMY_SPEC A).
 *
 * **Android importu YOK.** Saf sayilar + saf fonksiyonlar.
 *
 * ---------------------------------------------------------------------------------
 * NEDEN BU DOSYA VAR
 * ---------------------------------------------------------------------------------
 * Testci geri bildirimi: *"Ilk 5 bolum asiri basitti. 6 tane kule yapacak para
 * kazaniyorsun ama 2 tanesi yetiyor."*
 *
 * Bu iki ayri sikayettir ve ikisinin sahibi ayni degildir:
 *
 *   (1) "6 kule parasi kazaniyorsun"  -> TEDARIK BOLLUGU. Bu dosyanin isi.
 *   (2) "2 tanesi yetiyor"            -> TEHDIT/DPS YETERSIZLIGI. Level Designer ve
 *                                        gameplay tarafinin isi (ECONOMY_SPEC A.5).
 *
 * (2) cozulmeden (1)'i cozmek yetmez: butce yarilansa bile 2 kule yetiyorsa oyuncu
 * 2 kule kurup kalani biriktirir — yalnizca biriktirdigi sayi kuculur. Bu dosya
 * (1)'i olculebilir sekilde cozer ve (2) icin **sayisal bir hedef** teslim eder.
 *
 * ---------------------------------------------------------------------------------
 * TESHIS TEK SAYIDA: "BIR GATLING KAC OLDURME EDER?"
 * ---------------------------------------------------------------------------------
 * Gatling Gun 60 Tedarik. Piyade 12 Tedarik oduyor. Yani **5 piyade oldurmek bir kule
 * oduyor.** Bir dalga 6-14 piyade getiriyor, yani her dalga 1-3 kule finanse ediyor.
 * Bolluk buradan geliyor; baslangic Tedariki (150) L1 butcesinin yalnizca %13'u,
 * L6'nin %5'i — yani **baslangic Tedariki tek basina baskin kaldirac DEGILDIR.**
 *
 * Hedef: **bir kule ~15 oldurme.** [SUPPLY_REWARD_SCALE] bunu saglar.
 *
 * ---------------------------------------------------------------------------------
 * OLCUT: SPI (Supply Pressure Index)
 * ---------------------------------------------------------------------------------
 *   SPI(L) = butce(L) / tasarlanan kadro maliyeti(L)
 *
 * - SPI ~ 1,0  : kadro ancak kurulur, tek hata bolumu kaybettirir (fazla sert).
 * - SPI 1,5-2,5: kadro kurulur, bir hata affedilir, artan Tedarik anlamli
 *                (yukseltme mi 5. kule mi) — **HEDEF BANT**.
 * - SPI > 4    : artan Tedarigin harcanacak yeri yok, karar olmez -> bugunku durum.
 *
 * Bugun SPI 5,1-6,5. Bu dosyanin sayilariyla 2,0-2,5.
 *
 * ---------------------------------------------------------------------------------
 * NEDEN 2,0 VE DAHA SIKI DEGIL
 * ---------------------------------------------------------------------------------
 * Kule DPS'i ile dusman HP'sinin gercek sahadaki (menzil x yol uzunlugu) etkilesimini
 * bu katman ORCEMEZ; dolayisiyla SPI 1,5'e tek adimda inmek "bolum gecilemez hale
 * geldi" riskini olcmeden almak olur. Hard Rule: *ekonomi degisiklikleri simulasyon
 * veya olculebilir varsayim olmadan yapilmaz.* Bu yuzden:
 *
 *   Faz 1 (bu commit): SPI 5,1-6,5 -> 2,0-2,5. Cihazda oynanis testi.
 *   Faz 2 (kosullu)  : [SUPPLY_REWARD_SCALE] 0,375 -> 0,28 ile SPI ~1,6.
 *                      YALNIZCA Faz 1 testinde bolum 1-6 hala 1 yildizla
 *                      gecilebiliyorsa.
 */
object SupplyBudgetModel {

    /** Modelin kapsadigi bolum sayisi (Act I'in olculmus kismi). */
    const val MODELLED_LEVELS: Int = 8

    // =================================================================================
    // Bugunku (ASIRI BOL) taban — 2026-08-16 tarihli OLCUM
    // =================================================================================

    /**
     * Bugunku duz baslangic Tedariki. `GameConfig.INITIAL_GOLD` ile ayni; 22 bolumun
     * TAMAMI bununla basliyor (`LevelSpec.startingSupply` alani var ama hicbir bolumde
     * override edilmiyor).
     */
    const val LEGACY_STARTING_SUPPLY: Int = EconomyConfig.BASE_STARTING_SUPPLY

    /** Bugunku dalga-temizleme ikramiyesi. `GameEngine`de sabit sayi olarak gomulu. */
    const val LEGACY_WAVE_CLEAR_BONUS: Int = 35

    /** Bolum basi dalga sayisi, L1..L8. `WaveDefinitions` olcumu. */
    val WAVE_COUNT: IntArray = intArrayOf(6, 6, 7, 8, 8, 9, 9, 10)

    /**
     * Bugunku **oldurme** Tedarik geliri (tum dusmanlar olduruldugunde), L1..L8.
     * `WaveDefinitions` x `GameConfig.ENEMY_SPECS.rewardGold` uzerinden hesaplandi.
     *
     * NOT: bu bir SNAPSHOT'tir, canli sozlesme degil. Dalga tablosu degisirse burada
     * yeniden olculmesi gerekir; testler bu diziye *turetilmis oran* olarak bakar,
     * mutlak esitlik dayatmaz.
     */
    val LEGACY_WAVE_KILL_SUPPLY: IntArray =
        intArrayOf(852, 1128, 1308, 2112, 1844, 2696, 2662, 2896)

    // =================================================================================
    // ONERILEN (SIKILASTIRILMIS) sayilar
    // =================================================================================

    /**
     * **ONERILEN `GameConfig.ENEMY_SPECS.rewardGold` TABLOSU** (uygulama baska ajanin
     * isi, ECONOMY_SPEC 9 madde 2). Anahtarlar dusman tipi adlaridir; bu paket
     * `game/model`e BAGIMLI DEGILDIR (ekonomi katmani standalone kalir), sozlesme
     * `EconomyGameConfigContractTest`te kurulur.
     *
     * Kalibrasyon cakmasi: **piyade 12 -> 4**, yani Gatling Gun 5 oldurme yerine
     * **15 oldurme** eder. Oranlar korunur (zirhli ~2,75 x piyade), dolayisiyla kule
     * kimlikleri ve hedef secimi DEGISMEZ — yalnizca akis hizi duser.
     */
    val TIGHTENED_ENEMY_SUPPLY_REWARD: Map<String, Int> = mapOf(
        "INFANTRY" to 4,          // 12  (/3)
        "FAST_SOLDIER" to 5,      // 15  (/3)
        "SHIELDED_TROOPER" to 7,  // 22  (/3, asagi)
        "ARMORED_VEHICLE" to 9,   // 28  (/3, asagi)
        "TANK" to 20,             // 60  (/3)
        "COMMAND_TANK" to 60,     // 180 (/3)
    )

    /**
     * Nominal olcek: **tam olarak 1/3**.
     *
     * Ucte bir secilmesinin nedeni yalnizca hedef SPI degil, **yuvarlama disiplini**:
     * her odul 3'e tam ya da tama yakin bolundugu icin dusmanlarin birbirine goreli
     * degeri korunuyor (piyade 1,00 / kosucu 1,25 / tank 5,00 birebir ayni kaliyor).
     * 0,375 gibi bir olcek piyadeyi asagi (4,5 -> 4), zirhliyi yukari (10,5 -> 11)
     * yuvarlayarak zirhliyi piyadeye gore %18 DEGERLENDIRIRDI ve hedef secimini
     * sessizce degistirirdi.
     *
     * Hesapta kullanilmaz — gercek sayilar [TIGHTENED_WAVE_KILL_SUPPLY]'dedir;
     * [effectiveRewardScale] sapmayi olcer.
     */
    const val SUPPLY_REWARD_SCALE: Double = 1.0 / 3.0

    /**
     * [TIGHTENED_ENEMY_SUPPLY_REWARD] tablosunun dalga tablosuna uygulanmasiyla cikan
     * **oldurme** geliri, L1..L8. Dalga kompozisyonlari tek tek sayilarak turetildi;
     * [effectiveRewardScale] her bolumde tutarliligi dogrular.
     */
    val TIGHTENED_WAVE_KILL_SUPPLY: IntArray =
        intArrayOf(284, 376, 436, 704, 611, 894, 881, 961)

    /**
     * Onerilen dalga-temizleme ikramiyesi: 35 -> 18.
     *
     * Ayrica **sabite tasinmali**: bugun motorda cikplak sayi olarak duruyor, yani
     * ekonomi tek-kaynak kuralini ihlal ediyor ve bolum uzunluguyla sessizce
     * buyuyor (L1 175, L8 315).
     */
    const val WAVE_CLEAR_SUPPLY_BONUS: Int = 18

    /**
     * **ONERILEN `LevelSpec.startingSupply`, bolum 1..6.**
     *
     * Tasarim niyeti bolum bolum:
     *  L1  80 — bir Gatling (60) + 20 artik. "Tek silahini NEREYE koyacaksin" karari.
     *  L2  90 — hala tek kule acilisi; ikinci kule dalga geliriyle gelir.
     *  L3 110 — Heavy Cannon (95) acilir: "iki Gatling mi, bir Cannon mi" ilk gercek
     *           kule-kimligi karari, ikisi de tam butceyi yer.
     *  L4 120 — iki Gatling TAM butce. Sifir artik.
     *  L5 140 — Frost Field (100) acilir; 140 ile Frost + hicbir sey ya da iki Gatling.
     *  L6 150 — taban degere geri doner; buradan sonra sikilastirmayi dalga geliri tasir.
     *
     * L7..L22 [EconomyConfig.BASE_STARTING_SUPPLY] (150) kalir: o bolumlerde baslangic
     * Tedariki butcenin ~%12'si oldugundan anlamli bir kaldirac degil ve 150'yi korumak
     * `EconomyGameConfigContractTest`in `INITIAL_GOLD` sozlesmesini bozmadan birakir.
     *
     * Meta yukseltme (STARTING_SUPPLY, +25/rank) bunun **UZERINE** biner; motor
     * `levelSpec.startingSupply + (meta - 150)` isletiyor, yani agaci yatirim yapan
     * oyuncu L1'e 80 yerine 230'a kadar girebilir. Sikilastirma yeni oyuncuyu vurur,
     * yatirim yapmis oyuncuyu odullendirir — istenen sey tam olarak bu.
     */
    val EARLY_STARTING_SUPPLY: IntArray = intArrayOf(80, 90, 110, 120, 140, 150)

    /**
     * **TASARLANAN KADRO MALIYETI I(L)**, L1..L8 — SPI'nin bolen tarafi.
     *
     * "Bu bolumde oyuncunun kurmasini ISTEDIGIMIZ savunma" (kule + yukseltme):
     *  L1 180 = 3 x Gatling
     *  L2 245 = 3 x Gatling + 1 yukseltme
     *  L3 275 = 3 x Gatling + 1 Cannon
     *  L4 430 = 3 x Gatling + 1 Cannon + 2 yukseltme
     *  L5 440 = 3 x Gatling + 1 Cannon + 1 Frost + 1 yukseltme
     *  L6 590 = 4 x Gatling + 1 Cannon + 1 Frost + 2 yukseltme
     *  L7 690 = + Missile Battery (L7'de acilir)
     *  L8 720
     *
     * Yani hedef **3-4 kule** (testcinin istegi), bolum 6'dan sonra 5-6.
     */
    val DESIGNED_LOADOUT_COST: IntArray =
        intArrayOf(180, 245, 275, 430, 440, 590, 690, 720)

    /** SPI'nin kabul edilebilir bandi. Disina cikan bolum tasarim hatasidir. */
    const val SPI_TARGET_MIN: Double = 1.5
    const val SPI_TARGET_MAX: Double = 2.6

    // =================================================================================
    // Turetilmis fonksiyonlar
    // =================================================================================

    private fun requireModelled(level: Int) {
        require(level in 1..MODELLED_LEVELS) { "SPI modeli yalnizca L1..$MODELLED_LEVELS: $level" }
    }

    /** Bolum basi dalga sayisi. */
    fun waveCount(level: Int): Int {
        requireModelled(level)
        return WAVE_COUNT[level - 1]
    }

    /**
     * Dalga-temizleme ikramiyesi toplami. **Son dalganin ikramiyesi YOKTUR** — o dalga
     * ZAFER dalina gider — dolayisiyla (N-1) x ikramiye.
     */
    fun waveClearBonusTotal(level: Int, bonusPerWave: Int = WAVE_CLEAR_SUPPLY_BONUS): Int {
        requireModelled(level)
        return (waveCount(level) - 1) * bonusPerWave
    }

    /** Sikilastirilmis oldurme geliri (onerilen tamsayi odul tablosundan turetildi). */
    fun waveKillSupply(level: Int): Int {
        requireModelled(level)
        return TIGHTENED_WAVE_KILL_SUPPLY[level - 1]
    }

    /** Bir bolumde tamsayi tablosunun urettigi ETKIN odul olcegi. */
    fun effectiveRewardScale(level: Int): Double {
        requireModelled(level)
        return TIGHTENED_WAVE_KILL_SUPPLY[level - 1].toDouble() / LEGACY_WAVE_KILL_SUPPLY[level - 1]
    }

    /** Sikilastirilmis dalga geliri = oldurme + dalga ikramiyesi (baslangic HARIC). */
    fun waveSupplyIncome(level: Int): Int =
        waveKillSupply(level) + waveClearBonusTotal(level)

    /** Bugunku dalga geliri. */
    fun legacyWaveSupplyIncome(level: Int): Int {
        requireModelled(level)
        return LEGACY_WAVE_KILL_SUPPLY[level - 1] +
            waveClearBonusTotal(level, LEGACY_WAVE_CLEAR_BONUS)
    }

    /**
     * Onerilen bolum basi baslangic Tedariki (meta yukseltme HARIC).
     * L7..L22 icin taban 150.
     */
    fun startingSupply(level: Int): Int {
        require(level in 1..EconomyConfig.CAMPAIGN_LEVELS) { "level $level aralik disi" }
        return if (level <= EARLY_STARTING_SUPPLY.size) {
            EARLY_STARTING_SUPPLY[level - 1]
        } else {
            EconomyConfig.BASE_STARTING_SUPPLY
        }
    }

    /** Onerilen TAM bolum butcesi: baslangic + dalga geliri. */
    fun supplyBudget(level: Int): Int = startingSupply(level) + waveSupplyIncome(level)

    /** Bugunku TAM bolum butcesi. */
    fun legacySupplyBudget(level: Int): Int =
        LEGACY_STARTING_SUPPLY + legacyWaveSupplyIncome(level)

    /** Tasarlanan kadro maliyeti I(L). */
    fun designedLoadoutCost(level: Int): Int {
        requireModelled(level)
        return DESIGNED_LOADOUT_COST[level - 1]
    }

    /** SPI(L) = butce / tasarlanan kadro. Hedef bant [SPI_TARGET_MIN]..[SPI_TARGET_MAX]. */
    fun supplyPressureIndex(level: Int): Double =
        supplyBudget(level).toDouble() / designedLoadoutCost(level)

    /** Bugunku SPI — sikilastirmanin ne kadar gerekli oldugunun kaniti. */
    fun legacySupplyPressureIndex(level: Int): Double =
        legacySupplyBudget(level).toDouble() / designedLoadoutCost(level)

    /**
     * "Bir Gatling Gun kac temel dusman oldurme eder" — teshisin tek sayili hali.
     * Bugun 5, hedef ~15.
     *
     * @param towerCost Gatling Gun insa bedeli (`GameConfig` = 60).
     * @param basicEnemyReward piyadenin Tedarik odulu.
     */
    fun killsPerTower(towerCost: Int, basicEnemyReward: Int): Double =
        towerCost.toDouble() / basicEnemyReward
}

// =====================================================================================
// Ust duzey takma adlar — testler ve motor devri bunlari kullanir
// =====================================================================================

/**
 * Bu bolumun baslangic Tedariki (meta yukseltme HARIC).
 *
 * MOTOR DEVRI: `GameConfig.CAMPAIGN[L].startingSupply` bu fonksiyonun dondurdugu
 * degere esit olmalidir. Bkz. ECONOMY_SPEC 9 madde 1.
 */
fun startingSupplyFor(level: Int): Int = SupplyBudgetModel.startingSupply(level)

fun waveSupplyIncome(level: Int): Int = SupplyBudgetModel.waveSupplyIncome(level)

fun legacyWaveSupplyIncome(level: Int): Int = SupplyBudgetModel.legacyWaveSupplyIncome(level)

/** Bugunku duz baslangic Tedariki (karsilastirma tabani). */
const val LEGACY_STARTING_SUPPLY: Int = SupplyBudgetModel.LEGACY_STARTING_SUPPLY
