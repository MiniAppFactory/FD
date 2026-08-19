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
     * Modelin SPI olcebildigi bolum sayisi — **8 -> 55** (CAMPAIGN_55.md 8.1).
     *
     * Eskiden 8'de duruyordu, cunku bolen ([DESIGNED_ROSTER_SIZE]) yalnizca Act
     * I'in ilk 8 bolumu icin OLCULMUSTU. CAMPAIGN_55.md tasarimi bu iliskiyi
     * TERSINE cevirdi: SPI artik bir KONTROL degil bir URETIM KURALI —
     * hedef bant -> butce -> dalga kompozisyonu. Yani L23..L55'in kadrosu
     * (9. tablonun `R` ve `kd3` kolonlari) tasarimin GIRDISIDIR, olcumu degil;
     * dolayisiyla bolen artik 55 bolumun tamami icin tanimli.
     *
     * **BANT ISTISNASI KALKTI.** Eskiden L9..L22 SPI bandindan MUAFTI
     * (`BAND_EXEMPT_LEVELS = 9..22`): o bolumler 10-18 dalga uzunlugundaydi,
     * butce tahtanin kapasitesini asiyordu (L22 SPI 5,37) ve muafiyet bunu
     * "kanit olarak" pinliyordu. Bolum sekli tek ritme (5-7 dalga) tasininca
     * muafiyetin sebebi ortadan kalkti; 55 bolumun 55'i banda giriyor
     * (olculen aralik 1,54..2,52) ve `SupplyBudgetTest` bunu istisnasiz
     * dogruluyor.
     */
    const val MODELLED_LEVELS: Int = 55

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
     * **OGRETICI SERMAYE — yalnizca L1 ve L2.**
     *
     *  L1  80 — bir Gatling (60) + 20 artik. "Tek silahini NEREYE koyacaksin".
     *  L2  90 — hâlâ tek kule acilisi; ikinci kule dalga geliriyle KAZANILIR.
     *
     * L3'ten itibaren sermaye [startingSupply]in kadro kuralindan gelir. Eski
     * tablo (110/120/140/150 ve L7..L22 duz 150) bolumler 5-7 dalgaya inince
     * gelirin yarisini goturdu ve SPI'yi bandin ALTINA dusurdu (L4 1,33 ·
     * L5 1,31 · L7 1,41). Ayni tablo gec bolumlerde acilis dalgasini
     * gecilemez yapiyordu (270 Tedarik = 2 kule, W1 = 40 govde).
     *
     * Meta yukseltme (STARTING_SUPPLY, +25/rank) bunun **UZERINE** biner; motor
     * `levelSpec.startingSupply + (meta - 150)` isletiyor.
     */
    /**
     * ⚠ 2026-08-19 — L1/L2 SERMAYESI 80/90 -> 120/130.
     *
     * Eski tasarim gerekcesi suydu: L1 = 80 = TAM BIR GATLING, "ikinci kuleyi
     * KAZAN". Niyet dogruydu ama olcum yanlis yerden geliyordu:
     * `CampaignSolvabilityAllLevelsTest` bolumun cozulebilir oldugunu
     * gosteriyor — ancak simulator MUKEMMEL oynar (dogru pad, dogru an,
     * hedeflemeyi bilir). Bolumun ILK KEZ oynayan biri tarafindan
     * kazanilabilir oldugunu HICBIR sey olcmuyordu.
     *
     * Cihaz kanit: oyuncu L1de tek Gatling ile kaybediyor. Bir tower defense
     * oyununun BIRINCI bolumu, kurallari daha yeni ogrenen birine kaybettirmez;
     * ilk bolum dersin kendisidir, sinav degil.
     *
     * 120 = tam IKI Gatling (buildCost 60). Ders kaybolmuyor, YER DEGISTIRIYOR:
     * artik soru "tek silahini nereye koyacaksin" degil, "iki silahini yolun
     * neresinde kesistireceksin" — ve bu, oyunun gercek core loopu.
     *
     * L2 DE BIRLIKTE TASINDI (90 -> 130). Istenmemisti ama 120den 90a dusmek
     * oyuncuyu iki kuleden birbucuga indirirdi; ikinci bolum birincisinden
     * daha fakir baslayamaz. Ayni +40 farki korunuyor.
     */
    val EARLY_STARTING_SUPPLY: IntArray = intArrayOf(120, 130)

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
    val DESIGNED_ROSTER_SIZE: IntArray = intArrayOf(
        // ---- L1..L22: YENIDEN OLCULDU. Bolumler 6-18 dalgadan 5-7'ye inince
        // ---- gelir yariya dustu; eski kadro (5 kule) SPI'yi bandin ALTINA
        // ---- itiyordu (L4 1,33 · L5 1,31 · L7 1,41). Kadro artik hem SAYI hem
        // ---- PAHALILIK ile buyuyor: L7'de Fuze acilir (kademe-2 495 -> 725),
        // ---- L17'de bes kuleye cikar. `GameConfig.DESIGNED_ROSTER_SIZE` ile
        // ---- birebir ayni olmasi `BalanceConsistencyTest`te kilitli.
        2, 3, 3, 3, 3, 3, 4, 4,
        4, 4, 4,
        4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5,
        // ---- L23..L33 (Act III / kis)
        5, 5, 5, 6, 5, 6, 6, 6, 5, 6, 6,
        // ---- L34..L44 (Act IV / col)
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7,
        // ---- L45..L55 (Act V / sonbahar)
        6, 6, 6, 7, 6, 7, 7, 7, 6, 7, 7,
    )

    /**
     * **KADRONUN KADEME 3'E CIKAN UYE SAYISI** (`kd3`, CAMPAIGN_55.md 9. tablo).
     *
     * NEDEN AYRI BIR KOLON: kademe 3 `GameConfig.TIER_THREE_UNLOCK_LEVEL`de
     * (L12) **bedava** acilir, ama YUKSELTMESI Tedarik ister (130-230). Bolen
     * yalnizca kademe 2'yi sayarsa gec bolumlerde oyuncunun gercekten odedigi
     * paranin ~%40'i modelin disinda kalir ve SPI oldugundan yuksek olculur —
     * yani "para bol" teshisi bir OLCUM hatasi olur. Bu, projede ikinci kez
     * yasanan hata sinifi (bkz. dosya basi); bu kolon onu kapatiyor.
     *
     * L1..L11 icin 0: kademe 3 henuz acik degil.
     */
    val DESIGNED_TIER_THREE_COUNT: IntArray = intArrayOf(
        // L1..L11
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        // L12..L22 (kadro 5'ten 4'e indigi icin kd3 de yeniden olculdu)
        1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3,
        // L23..L33 (L33 4 -> 3, bkz. WaveDefinitions.LATE_PLAN gerekcesi)
        2, 3, 3, 3, 3, 3, 4, 4, 3, 4, 3,
        // L34..L44
        3, 4, 4, 4, 4, 4, 5, 5, 4, 5, 5,
        // L45..L55 (L53 5 -> 3, bkz. WaveDefinitions.LATE_PLAN gerekcesi)
        4, 4, 5, 5, 4, 5, 5, 6, 3, 5, 6,
    )

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
        // ⚠ CARPMA **FLOAT**'TA YAPILIR — motor `actRewardMul: Float` tutuyor
        // (`GameEngine.kt:1150`). Double'da yapmak sessiz bir 1 Tedarik farki
        // uretiyordu: 1,30f gercekte 1,2999999523... oldugu icin Double'da
        // 20 x 1,3 -> 25,999999 -> **25**, Float'ta ayni carpim 26,0f -> **26**;
        // boss'ta ise ters yonde (77 / 78). Tank ve boss agirlikli gec
        // bolumlerde bu bolum basina 30-50 Tedarik hatasi demek ve modelin
        // "motorun aritmetigini birebir aynalar" iddiasini bozar.
        val actMul = facts.actRewardMultiplier(level).toFloat()
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

    /**
     * Bolum basi baslangic Tedariki (meta yukseltme HARIC).
     *
     * L1..L6 [EARLY_STARTING_SUPPLY] · L7..L22 taban 150 · L23+ perde rampasi
     * (Act III 220 · IV 270 · V 320, CAMPAIGN_55.md 8.2).
     *
     * Perde rampasi bir ZORLUK KOLU DEGIL, bolum bolme kararinin zorunlu
     * sonucudur: 6-7 dalgalik bir bolumde oldurme geliri kadroyu kurmaya
     * yetismez; ya bolum uzar (K-2'ye aykiri) ya sermaye artar.
     *
     * Tek dogruluk kaynagi `GameConfig.CAMPAIGN[L].startingSupply`; buradaki
     * hesabin onunla ayni olmasi `SupplyBudgetTest` icinde kilitli.
     */
    fun startingSupply(
        level: Int,
        facts: CampaignFacts = GameConfigCampaignFacts,
    ): Int {
        requireCampaign(level)
        if (level <= EARLY_STARTING_SUPPLY.size) return EARLY_STARTING_SUPPLY[level - 1]
        // Kadronun KADEME-1 maliyeti: oyuncu bolume savunmasini KURABILECEK
        // sermaye ile girer, yukseltmeleri kazanir. Fiyatlar canli TOWER_SPECS'ten.
        val order = facts.unlockedTowersInOrder(level)
        val roster = DESIGNED_ROSTER_SIZE[level - 1]
        val base = (0 until roster).sumOf { facts.towerBuildCost.getValue(order[it % order.size]) }
        return if (facts.hasScarceCoverage(level)) {
            Math.round(base * COVERAGE_SCARCITY_SUPPLY_FACTOR)
        } else {
            base
        }
    }

    /**
     * KAPSAMA KITLIGI SERMAYE CARPANI — `GameConfig.COVERAGE_SCARCITY_SUPPLY_FACTOR`
     * ile ayni olmasi `BalanceConsistencyTest` icinde kilitli.
     *
     * Iki sebep ayni sonucu verir: (a) catallanan harita (1, 2, 4, 11) — pad'in
     * gordugu yol PAYI yariya iner; (b) dar tahta — acik pad, kadronun ancak
     * bir fazlasi kadardir, yani yerlestirme secenegi yoktur.
     *
     * Olcum: sermaye kurali kadro kademe-1 maliyetine cikarildiginda gecilemeyen
     * bolum 38 -> 11 dustu ve kalan 11'in 11'i de catallanan haritalardaydi;
     * carpan 1,5 ile 11 -> 2. Dar tahta kolu ise "yalnizca TEK bir oynanis
     * bicimiyle gecilebilen bolum" olcumunden geldi (L33 / L46 / L53).
     */
    const val COVERAGE_SCARCITY_SUPPLY_FACTOR: Float = 1.5f

    // KALDIRILDI: perde basina duz sermaye (`LATE_ACT_STARTING_SUPPLY =
    // [220, 270, 320]`, CAMPAIGN_55.md 8.2). Bolum 5-7 dalgaya inince yetmedi;
    // sermaye artik perdeden degil KADRODAN turer — bkz. [startingSupply].

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
    fun designedLoadoutCost(level: Int, facts: CampaignFacts = GameConfigCampaignFacts): Int {
        val roster = designedRoster(level, facts)
        val tierThree = DESIGNED_TIER_THREE_COUNT[level - 1]
        return roster.sumOf { facts.tierTwoCost(it) } +
            roster.take(tierThree).sumOf { facts.tierThreeStep(it) }
    }

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
