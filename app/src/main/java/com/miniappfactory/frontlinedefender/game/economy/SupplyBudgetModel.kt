package com.miniappfactory.frontlinedefender.game.economy

/**
 * Faz 10 / 10.1 — SAVAS ICI **TEDARIK** BUTCE MODELI (ECONOMY_SPEC A).
 *
 * **Android importu YOK.** Saf sayilar + saf fonksiyonlar. Dalga tablosu ve kule
 * fiyatlari [CampaignFacts] uzerinden CANLI okunur (bkz. o dosyanin bası: elle
 * yazilmis ara tablolar iki kez bayatladi, ucuncusu olmayacak).
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
 *   (2) "2 tanesi yetiyor"            -> TEHDIT/DPS YETERSIZLIGI. Kule ajaninin isi.
 *
 * (2) Faz 10'da cozuldu (atis araligi x2, dusman cani x3,5, L1-L7 dalga
 * sikilastirmasi — `docs/TOWER_REBALANCE.md`). Bu dosya (1)'i olcer.
 *
 * ---------------------------------------------------------------------------------
 * OLCUT: SPI (Supply Pressure Index)
 * ---------------------------------------------------------------------------------
 *   SPI(L) = butce(L) / tasarlanan kadro maliyeti I(L)
 *
 * - SPI ~ 1,0  : kadro ancak kurulur, tek hata bolumu kaybettirir (fazla sert).
 * - SPI 1,5-2,6: kadro kurulur, bir hata affedilir, artan Tedarik anlamli
 *                (yukseltme mi 5. kule mi) — **HEDEF BANT**.
 * - SPI > 4    : artan Tedarigin harcanacak yeri yok, karar olmez.
 *
 * ---------------------------------------------------------------------------------
 * FAZ 10.1 — "SPI BANDIN USTUNE CIKTI" TESHISI
 * ---------------------------------------------------------------------------------
 * Kule ajani dalgalari sikilastirdiktan sonra SPI L1 3,17 · L2 2,82 · L3 2,98 ·
 * L4 2,93 olarak olculdu (L5-L7 banda icindeydi). Ilk refleks "gelir fazla, odulu
 * daha da kis" oldu. **Olcum bunu curuttu; sorun BOLEN tarafindaydi.**
 *
 * PAY TARAFI (gelir) icin mevcut butun kaldiraclar denendi ve hepsi yetersiz ya
 * da zararli cikti — bu tablo kararin gerekcesidir, silinmemeli:
 *
 * | senaryo | L1 | L4 | L7 | sonuc |
 * |---|---|---|---|---|
 * | bugun (eski bolen) | 3,17 | 2,93 | 1,70 | L1-L4 BANT USTU |
 * | baslangic Tedariki TABANA (L1 60 = tam bir Gatling) + ikramiye 18->12 | 2,89 | 2,79 | 1,63 | **hâlâ bant ustu** |
 * | ustune odul x1/4 (3/4/5/7/15/45) | 1,71 | 1,58 | **1,28** | 8 bolumun 5'i bant ALTI |
 *
 * Yani: baslangic Tedariki L1 butcesinin yalnizca **%14'u** (bu zaten Faz 10'da
 * olculmustu, bkz. `startingSupplyIsNotTheDominantLever`), dalga ikramiyesi %16'si;
 * ikisini birlikte tabana cekmek L1'i 3,17'den ancak 2,89'a indiriyor. Odul tablosu
 * ise **butun bolumlere ayni carpanla** vuruyor, dolayisiyla L1-L4'u banda sokarken
 * L2/L3/L5/L7/L8'i bandin ALTINA atiyor ve soft-lock riski uretiyor. Tek bir odul
 * tablosuyla bolum-yerel bir duzeltme MATEMATIKSEL OLARAK IMKANSIZ.
 *
 * BOLEN TARAFI ise gercekten bayatti: [DESIGNED_ROSTER_SIZE] yorumu asagida.
 * Duzeltildikten sonra L1-L8 hicbir gelir degisikligi OLMADAN banda giriyor
 * (2,28 / 1,85 / 1,88 / 2,03 / 1,70 / 2,22 / 1,62 / 1,76).
 *
 * KARAR: **`rewardGold`, `startingSupply` ve dalga ikramiyesi DEGISMEDI.** Bant
 * asimi bir ekonomi hatasi degil, bayat bir OLCUM hatasiydi. Gelirin ustune bir
 * kesinti daha binmesi bandin alt ucundaki bolumleri (L7 1,62) kirar.
 */
object SupplyBudgetModel {

    /**
     * Modelin SPI olcebildigi bolum sayisi.
     *
     * Sinir keyfi degil: SPI'nin bolen tarafi [DESIGNED_ROSTER_SIZE], kule ajaninin
     * `docs/tools/difficulty_audit.py` ile OLCTUGU "gereken kule" sayisina dayanir ve
     * o olcum Act I'in ilk 8 bolumu icin yapildi. L9+ icin olculmus bir kadro
     * olmadigindan SPI **uydurulmaz**; gelir tarafi ([waveKillSupply]) yine 22 bolum
     * icin canli hesaplanir.
     */
    const val MODELLED_LEVELS: Int = 8

    // =================================================================================
    // Karsilastirma tabani — Faz 10 ONCESI ekonomi
    // =================================================================================

    /**
     * Faz 10 oncesi duz baslangic Tedariki (22 bolumun tamami 150 ile basliyordu).
     */
    const val LEGACY_STARTING_SUPPLY: Int = EconomyConfig.BASE_STARTING_SUPPLY

    /** Faz 10 oncesi dalga-temizleme ikramiyesi (`GameEngine`de ciplak sayiydi). */
    const val LEGACY_WAVE_CLEAR_BONUS: Int = 35

    /**
     * Faz 10 oncesi `rewardGold` tablosu.
     *
     * **Dizi degil harita olmasi kasitli:** bu tablo bir SNAPSHOT degil, bir
     * DONUSUM tabanidir. [legacyWaveKillSupply] bunu BUGUNKU dalga tablosuna
     * uygular, yani "eski odullerle bugunku dalgalar ne kadar oderdi" sorusunu
     * cevaplar. Eski dalga tablosunun toplamlarini saklamak isimize yaramaz:
     * dalga tablosu kule ajaninin alani ve degismeye devam edecek.
     */
    val LEGACY_ENEMY_SUPPLY_REWARD: Map<String, Int> = mapOf(
        "INFANTRY" to 12,
        "FAST_SOLDIER" to 15,
        "SHIELDED_TROOPER" to 22,
        "ARMORED_VEHICLE" to 28,
        "TANK" to 60,
        "COMMAND_TANK" to 180,
    )

    // =================================================================================
    // Ekonomi katmaninin SAHIP OLDUGU sayilar
    // =================================================================================

    /**
     * **`GameConfig.ENEMY_SPECS.rewardGold` SOZLESMESI** (uygulandi, Faz 10).
     *
     * Kalibrasyon cakmasi: **piyade 12 -> 4**, yani Gatling Gun 5 oldurme yerine
     * **15 oldurme** eder. Oranlar korunur (zirhli ~2,25 x piyade), dolayisiyla kule
     * kimlikleri ve hedef secimi DEGISMEZ — yalnizca akis hizi duser.
     *
     * `BalanceConsistencyTest` bunu `GameConfig`e karsi kilitler; yani bu harita
     * artik bir "oneri" degil, iki katmani birbirine baglayan sozlesmedir.
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
     * Hesapta kullanilmaz; [effectiveRewardScale] gercekten olusan olcegi olcer.
     */
    const val SUPPLY_REWARD_SCALE: Double = 1.0 / 3.0

    /**
     * Dalga-temizleme ikramiyesi. `GameConfig.WAVE_CLEAR_SUPPLY_BONUS` ile ayni olmasi
     * `BalanceConsistencyTest`te kilitli.
     *
     * 35 -> 18 (Faz 10). Faz 10.1'de **degistirilmedi**: 12'ye cekmek L1'i 3,17'den
     * 3,06'ya indiriyor (dosya basi tablosu) ama L7'yi 1,70'ten 1,55'e itiyor — yani
     * bandin bol ucundan neredeyse hicbir sey almadan sert ucundan pay aliyor.
     */
    const val WAVE_CLEAR_SUPPLY_BONUS: Int = 18

    /**
     * **`LevelSpec.startingSupply`, bolum 1..6** (uygulandi, Faz 10).
     *
     * Tasarim niyeti bolum bolum:
     *  L1  80 — bir Gatling (60) + 20 artik. "Tek silahini NEREYE koyacaksin" karari.
     *  L2  90 — hala tek kule acilisi; ikinci kule dalga geliriyle gelir.
     *  L3 110 — Heavy Cannon (95) acilir: "iki Gatling mi, bir Cannon mi" ilk gercek
     *           kule-kimligi karari.
     *  L4 120 — iki Gatling TAM butce. Sifir artik.
     *  L5 140 — Frost Field (100) acilir; 140 ile Frost + hicbir sey ya da iki Gatling.
     *  L6 150 — taban degere geri doner; buradan sonrasini dalga geliri tasir.
     *
     * L7..L22 [EconomyConfig.BASE_STARTING_SUPPLY] (150) kalir.
     *
     * Meta yukseltme (STARTING_SUPPLY, +25/rank) bunun **UZERINE** biner; motor
     * `levelSpec.startingSupply + (meta - 150)` isletiyor. Sikilastirma yeni oyuncuyu
     * vurur, yatirim yapmis oyuncuyu odullendirir — istenen sey tam olarak bu.
     */
    val EARLY_STARTING_SUPPLY: IntArray = intArrayOf(80, 90, 110, 120, 140, 150)

    /**
     * **TASARLANAN KADRO ADEDI**, L1..L8 — SPI'nin bolen tarafinin *niyet* kismi.
     *
     * ---------------------------------------------------------------------------
     * FAZ 10.1'DE DUZELTILEN SEY BURASI
     * ---------------------------------------------------------------------------
     * Faz 10'da bolen elle yazilmis bir **Tedarik tutari** dizisiydi
     * (180/245/275/430/440/590/690/720) ve icerigi "3-4 kule + 1-2 yukseltme"
     * demekti — yani **kademe 1 kuleler**. O sayilar Faz 10 kalibrasyonundan
     * ONCE yazildi.
     *
     * Kalibrasyon sonrasi kademe 1 bir kule artik bir bolumu **tasiyamaz**:
     * atis araligi x2 ve dusman cani x3,5 ile kule basi oldurme gucu ~7 kat
     * dustu. Tek Gatling Kd.1 bir piyadeyi 260/43,8 = **5,9 saniyede** olduruyor;
     * L1'in son dalgasi 28 dusmani 0,40 sn araliklarla gonderiyor. Yani
     * "3 x Gatling Kd.1 = tasarlanan L1 savunmasi" cumlesi artik dogru degil.
     * Kule ajaninin kendi denetimi de bunu boyle olcuyor: `difficulty_audit.py`
     * butun kadrolari **kademe 2** varsayar.
     *
     * Bu, `TIGHTENED_WAVE_KILL_SUPPLY` ile TAM OLARAK AYNI hata sinifidir: elle
     * yazilmis bir ara deger, altindaki karar degisince sessizce yaniltici oldu.
     * Cozum de ayni: **adet** burada niyet olarak durur, **fiyat** canli
     * `TOWER_SPECS`ten gelir ([designedLoadoutCost]).
     *
     * ---------------------------------------------------------------------------
     * ADETLER NEREDEN GELIYOR
     * ---------------------------------------------------------------------------
     * `difficulty_audit.py`'nin OLCTUGU "oran 1,25 icin gereken kule" (kademe 2,
     * canli dalga tablosu): L1 1 · L2 2 · L3 2 · L4 3 · L5 2 · L6 3 · L7 3 · L8 3.
     *
     * Kural: **kadro = olculen gereken + 1 yedek, geriye gitmeyecek sekilde
     * (kosan maksimum).** -> 2 · 3 · 3 · 4 · 4 · 4 · 4 · 4
     *
     * "+1 yedek" keyfi degil: denetimin olcumu oyuncu LEHINE bir ust sinirdir
     * (%100 doluluk, her dusmana en uygun kule bakiyor, atis penceresi spawn
     * penceresi + en yavas dusmanin tam yol suresi). Gercek oyuncu yerlesim,
     * menzil ve muhimmat hatalari yapar; SPI bandinin "bir hata affedilir"
     * anlami bu yedekle degil, bandin KENDISIYLE saglanir — yedek olcum
     * iyimserligini duzeltir.
     *
     * Kadro geriye gitmez (kosan maksimum) cunku oyuncu bir bolumde kurdugu
     * savunmayi sonraki bolumde "sokup" oynamaz; tasarlanan kadro monoton olmali.
     * L5'in olculen ihtiyaci 2'ye dusuyor (zirh geliyor ama sayi azaliyor); kadro
     * L4'un 4'unde kalir.
     */
    val DESIGNED_ROSTER_SIZE: IntArray = intArrayOf(2, 3, 3, 4, 4, 4, 4, 4)

    /** SPI'nin kabul edilebilir bandi. Disina cikan bolum tasarim hatasidir. */
    const val SPI_TARGET_MIN: Double = 1.5
    const val SPI_TARGET_MAX: Double = 2.6

    // =================================================================================
    // Turetilmis fonksiyonlar — hepsi CANLI [CampaignFacts] uzerinden
    // =================================================================================

    private fun requireModelled(level: Int) {
        require(level in 1..MODELLED_LEVELS) { "SPI modeli yalnizca L1..$MODELLED_LEVELS: $level" }
    }

    private fun requireCampaign(level: Int) {
        require(level in 1..EconomyConfig.CAMPAIGN_LEVELS) { "level $level aralik disi" }
    }

    /** Bolum basi dalga sayisi (canli `WaveDefinitions`). */
    fun waveCount(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Int {
        requireCampaign(level)
        return facts.waveCount(level)
    }

    /**
     * Dalga-temizleme ikramiyesi toplami. **Son dalganin ikramiyesi YOKTUR** — o dalga
     * ZAFER dalina gider — dolayisiyla (N-1) x ikramiye.
     */
    fun waveClearBonusTotal(
        level: Int,
        bonusPerWave: Int = WAVE_CLEAR_SUPPLY_BONUS,
        facts: CampaignFacts = GameConfigCampaignFacts,
    ): Int = (waveCount(level, facts) - 1) * bonusPerWave

    /**
     * Bir odul tablosunun bu bolumde uretecegi **oldurme** Tedarik geliri.
     *
     * Motorun aritmetigini birebir aynalar (`GameEngine.kt:905`): odul once tur
     * carpaniyla carpilir, `toInt()` ile ASAGI kirpilir, sonra en az 1'e cekilir.
     * Aynalamak zorunlu — Act II'de carpan 1,30 ve kirpma bolum basina onlarca
     * Tedarik fark eder.
     */
    private fun killSupplyWith(
        level: Int,
        rewards: Map<String, Int>,
        facts: CampaignFacts,
    ): Int {
        requireCampaign(level)
        val actMul = facts.actRewardMultiplier(level)
        return facts.enemyCounts(level).entries.sumOf { (enemy, count) ->
            val nominal = rewards[enemy]
                ?: error("odul tablosunda '$enemy' yok — dalga tablosu yeni bir dusman tipi getirdi")
            val perKill = (nominal * actMul).toInt().coerceAtLeast(1)
            perKill * count
        }
    }

    /** Bugunku **oldurme** geliri: canli dalga tablosu x canli `rewardGold`. */
    fun waveKillSupply(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Int =
        killSupplyWith(level, facts.enemySupplyReward, facts)

    /**
     * "Eski odul tablosu BUGUNKU dalgalara uygulansaydi" geliri.
     *
     * Sikilastirmanin gercek etkisini olcmenin tek dogru yolu bu: iki tarafi AYNI
     * dalga tablosunda karsilastirmak. Eski dalga tablosunun toplamlariyla
     * karsilastirmak dalga sikilastirmasinin etkisini odul kesintisine mal ederdi.
     */
    fun legacyWaveKillSupply(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Int =
        killSupplyWith(level, LEGACY_ENEMY_SUPPLY_REWARD, facts)

    /**
     * Odul tablosunun BU bolumde gercekten urettigi olcek (tamsayi yuvarlama dahil).
     * Nominal hedef [SUPPLY_REWARD_SCALE] = 0,333; sapma dalga kompozisyonundan gelir
     * (hangi dusman tipi ne agirlikta).
     */
    fun effectiveRewardScale(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Double =
        waveKillSupply(level, facts).toDouble() / legacyWaveKillSupply(level, facts)

    /** Dalga geliri = oldurme + dalga ikramiyesi (baslangic Tedariki HARIC). */
    fun waveSupplyIncome(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Int =
        waveKillSupply(level, facts) + waveClearBonusTotal(level, facts = facts)

    /** Faz 10 oncesi dalga geliri (eski odul + eski ikramiye, bugunku dalgalarda). */
    fun legacyWaveSupplyIncome(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Int =
        legacyWaveKillSupply(level, facts) +
            waveClearBonusTotal(level, LEGACY_WAVE_CLEAR_BONUS, facts)

    /** Bolum basi baslangic Tedariki (meta yukseltme HARIC). L7..L22 icin taban 150. */
    fun startingSupply(level: Int): Int {
        requireCampaign(level)
        return if (level <= EARLY_STARTING_SUPPLY.size) {
            EARLY_STARTING_SUPPLY[level - 1]
        } else {
            EconomyConfig.BASE_STARTING_SUPPLY
        }
    }

    /** TAM bolum butcesi: baslangic + dalga geliri. */
    fun supplyBudget(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Int =
        startingSupply(level) + waveSupplyIncome(level, facts)

    /** Faz 10 oncesi TAM bolum butcesi (bugunku dalgalarda). */
    fun legacySupplyBudget(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Int =
        LEGACY_STARTING_SUPPLY + legacyWaveSupplyIncome(level, facts)

    /**
     * **TASARLANAN KADRO**: bu bolumde oyuncunun elinde olmasini istedigimiz kule
     * listesi, [DESIGNED_ROSTER_SIZE] adedinde.
     *
     * Bilesim kurali (deterministik, elle secim YOK): o bolumde acik olan kule
     * tipleri arasinda **round-robin**, acilis sirasinda. Yani Gatling omurga kalir,
     * her yeni kilit acildikca kadroda bir yer alir. Kule ajaninin denetim betigi
     * kadroyu ayni sekilde kuruyor, dolayisiyla SPI'nin boleni ile zorluk oraninin
     * kadrosu AYNI modeli kullaniyor.
     */
    fun designedRoster(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): List<String> {
        requireModelled(level)
        val available = facts.unlockedTowersInOrder(level)
        require(available.isNotEmpty()) { "L$level: acik kule yok" }
        val size = DESIGNED_ROSTER_SIZE[level - 1]
        return List(size) { available[it % available.size] }
    }

    /**
     * **TASARLANAN KADRO MALIYETI I(L)** — SPI'nin boleni.
     *
     * Kadronun tamami **kademe 2**'dir: Faz 10 kalibrasyonundan sonra kademe 1 bir
     * kule savunmanin son hali olamaz (bkz. [DESIGNED_ROSTER_SIZE]). Fiyatlar canli
     * `TOWER_SPECS`ten gelir, yani kule fiyati degisirse bolen kendiliginden takip
     * eder ve bu dizi bir daha bayatlamaz.
     */
    fun designedLoadoutCost(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Int =
        designedRoster(level, facts).sumOf { facts.tierTwoCost(it) }

    /** SPI(L) = butce / tasarlanan kadro. Hedef bant [SPI_TARGET_MIN]..[SPI_TARGET_MAX]. */
    fun supplyPressureIndex(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Double {
        requireModelled(level)
        return supplyBudget(level, facts).toDouble() / designedLoadoutCost(level, facts)
    }

    /** Faz 10 oncesi SPI (bugunku dalgalarda) — sikilastirmanin gerekliliginin kaniti. */
    fun legacySupplyPressureIndex(
        level: Int,
        facts: CampaignFacts = GameConfigCampaignFacts,
    ): Double {
        requireModelled(level)
        return legacySupplyBudget(level, facts).toDouble() / designedLoadoutCost(level, facts)
    }

    /**
     * "Bir Gatling Gun kac temel dusman oldurme eder" — teshisin tek sayili hali.
     * Faz 10 oncesi 5, bugun 15.
     *
     * @param towerCost Gatling Gun insa bedeli (`GameConfig` = 60).
     * @param basicEnemyReward piyadenin Tedarik odulu.
     */
    fun killsPerTower(towerCost: Int, basicEnemyReward: Int): Double =
        towerCost.toDouble() / basicEnemyReward

    /**
     * Bolum butcesinin kac tane **kademe 2** temel kule aldigi.
     *
     * Testcinin cumlesinin ("6 kule parasi kazaniyorsun") dogrudan olcumu; SPI'den
     * bagimsizdir cunku bolene hic bakmaz. Bant asimini boleni degistirerek
     * "cozdugumuz" izlenimini engellemek icin ayri bir olcut olarak duruyor.
     */
    fun budgetInBasicTowers(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Double {
        requireCampaign(level)
        val cheapest = facts.unlockedTowersInOrder(level).minOf { facts.tierTwoCost(it) }
        return supplyBudget(level, facts).toDouble() / cheapest
    }
}

// =====================================================================================
// Ust duzey takma adlar — testler ve motor devri bunlari kullanir
// =====================================================================================

/**
 * Bu bolumun baslangic Tedariki (meta yukseltme HARIC).
 *
 * `GameConfig.CAMPAIGN[L].startingSupply` bu fonksiyonun dondurdugu degere esittir;
 * `SupplyBudgetTest.gameConfigStartingSupplyMatchesTheBudgetModel` kilitler.
 */
fun startingSupplyFor(level: Int): Int = SupplyBudgetModel.startingSupply(level)

fun waveSupplyIncome(level: Int): Int = SupplyBudgetModel.waveSupplyIncome(level)

fun legacyWaveSupplyIncome(level: Int): Int = SupplyBudgetModel.legacyWaveSupplyIncome(level)

/** Faz 10 oncesi duz baslangic Tedariki (karsilastirma tabani). */
const val LEGACY_STARTING_SUPPLY: Int = SupplyBudgetModel.LEGACY_STARTING_SUPPLY
