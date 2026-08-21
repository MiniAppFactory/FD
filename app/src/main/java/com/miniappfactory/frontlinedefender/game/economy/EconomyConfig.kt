package com.miniappfactory.frontlinedefender.game.economy

/**
 * Faz 9 — EKONOMININ TEK SAYI KAYNAGI.
 *
 * TASARIM SOZLESMESI
 * ------------------
 * 1. Bu dosyada **hicbir Android importu yoktur.** `Context`, `SharedPreferences`,
 *    `SystemClock`, `Log` yasaktir. Saf JVM'de, Robolectric olmadan test edilir.
 * 2. **Ekonomi sayilari yalnizca burada durur.** `PlayerProgress`, `MissionSystem`,
 *    `CampaignProgressImpl`, `SaveManager` ve testler bu dosyayi okur; hicbiri
 *    kendi icinde ekonomi sabiti tanimlamaz.
 * 3. `docs/GDD.md` C/D/E/F bolumlerinde DONDURULMUS sayilar burada BIREBIR aynidir:
 *    R(L) = 140 + 30(L-1), yildiz carpani 1.0/1.3/1.6, D(L) tablosu, agac 13.900.
 *    Bu sayilar "iyilestirilmez" — degistirmek GDD karari gerektirir.
 * 4. GDD'de OLMAYAN her sabit, uzerinde `BAYRAK` etiketiyle isaretlidir ve tek
 *    satirla geri alinabilir bir anahtara baglidir. Gerekcesi
 *    `docs/ECONOMY_SPEC.md` 7. bolumdedir.
 *
 * IKI PARA BIRIMI (DECISIONS.md)
 * ------------------------------
 * Bu paket **yalnizca Coin** (meta) ekonomisini modeller. Savas ici **Tedarik**
 * `GameConfig`/`GameEngine` tarafindadir ve iki ekonomi arasinda hicbir donusum
 * fonksiyonu YOKTUR (GDD D.4). [MetaUpgrades.startingSupply] istisna degildir:
 * meta yukseltme savasin *baslangic* Tedarikini belirler, coin'i Tedarik'e cevirmez.
 */
object EconomyConfig {

    // =================================================================================
    // Kampanya (GDD B.2)
    // =================================================================================

    /**
     * Kampanya **55 bolum** (CAMPAIGN_55.md K1: 5 perde x 11 bolum).
     * `GameConfig.CAMPAIGN_LEVEL_COUNT` ile ayni olmali.
     */
    const val CAMPAIGN_LEVELS: Int = 55

    /** Yayinlanmis, elle kalibre edilmis ilk perde ciftinin son bolumu. */
    const val HANDWRITTEN_CAMPAIGN_LEVELS: Int = 22

    /** GDD C.1 kural 1 — bolum 1..6 tamamen bedava. Ilk ucretli bolum 7. */
    const val FIRST_PAID_LEVEL: Int = 7

    /** L23'ten itibaren kilit formulunun tabani ve adimi: `350 + 15 x (L - 22)`. */
    const val LATE_LOCK_BASE: Int = 350
    const val LATE_LOCK_STEP: Int = 15

    /**
     * Konuslanma bedelleri D(L), L = 7..55. **TEK SEFERLIK.**
     * `GameConfig.CAMPAIGN[L].deploymentCost` ile birebir ayni olmak ZORUNDA;
     * `EconomyGameConfigContractTest` bunu kilitler.
     *
     * ------------------------------------------------------------------
     * L23+ FORMULU NEDEN `350 + 15(L-22)`, `350 + 40(L-22)` DEGIL
     * ------------------------------------------------------------------
     * GDD C.3'un kurali: *bir bolumun odulu, bir sonrakinin kilidinin en az
     * 2,5 kati olmali.* Odul R(L) = 140 + 30(L-1) ADIMI 30'dur; kilit adimi
     * 40 olsaydi kilit odulden HIZLI buyur ve oran kacinilmaz olarak kirilirdi:
     *
     *   40 adimla:  L55 kilidi 1.670 · L54 odulu 1.730 -> oran **1,04**  ✗
     *   15 adimla:  L55 kilidi   845 · L54 odulu 1.730 -> oran **2,05**  ✓
     *
     * 22 bolumde bu gorunmuyordu cunku formul L22'de duruyordu. 55 bolumde
     * kilit, minimum gelirin %31'i yerine %44'unu tuketiyor — bu KABUL EDILDI
     * (coin'in kampanya boyunca degerli kalmasi icin gerekli), ama 2,5x kurali
     * kirilamaz: kirildigi anda oyuncu bir sonraki bolumu acamayacagi icin
     * kampanya soft-lock olur.
     */
    val LOCK_COSTS: IntArray = IntArray(CAMPAIGN_LEVELS - FIRST_PAID_LEVEL + 1) { i ->
        val level = FIRST_PAID_LEVEL + i
        when (level) {
            7 -> 100; 8 -> 110; 9 -> 120; 10 -> 130
            11 -> 140; 12 -> 150; 13 -> 165; 14 -> 180
            15 -> 195; 16 -> 210; 17 -> 225; 18 -> 240
            19 -> 255; 20 -> 270; 21 -> 300; 22 -> 350
            else -> LATE_LOCK_BASE + LATE_LOCK_STEP * (level - HANDWRITTEN_CAMPAIGN_LEVELS)
        }
    }

    /**
     * GDD C.3 — kampanyanin toplam kilit maliyeti. Regresyon kilidi.
     * 22 bolumde 3.140 idi; L23..L55 ekiyle **23.105**.
     */
    const val TOTAL_LOCK_COST: Int = 23_105

    /** 22 bolumluk kampanyanin toplam kilit maliyeti (karsilastirma tabani). */
    const val HANDWRITTEN_TOTAL_LOCK_COST: Int = 3_140

    // =================================================================================
    // Bolum odulu (GDD C.2)
    // =================================================================================

    /** R(L) = REWARD_BASE + REWARD_STEP * (L - 1). */
    const val REWARD_BASE: Int = 140
    const val REWARD_STEP: Int = 30

    /** Yildiz carpanlari 1/2/3 yildiz. Sonuc en yakin 10'a yuvarlanir. */
    const val STAR_MULT_1: Double = 1.0
    const val STAR_MULT_2: Double = 1.3
    const val STAR_MULT_3: Double = 1.6

    /** GDD B.3 — "Kusursuz Savunma" madalyasi, bolum basina TEK SEFERLIK. */
    const val PERFECT_DEFENSE_BONUS: Int = 80

    /** GDD C.2 / C.4 katman 2 — tekrar oynama ham orani. */
    const val REPLAY_RATIO: Double = 0.20

    /** GDD C.4 katman 2 — tekrar odulu **ASLA 0 olmaz**. Sert taban. */
    const val REPLAY_FLOOR: Int = 25

    /**
     * BAYRAK F-5 (ECONOMY_SPEC 5.1) — "boost'lu" tekrar hakki GUNLUK ve GLOBAL'dir,
     * bolum basina degil. GDD "gunde ilk 3 tekrar" diyor ama kapsamini soylemiyor;
     * bolum basina yorumlanirsa 22 bolum x 3 = 66 boost'lu tekrar/gun olur ve
     * farming ilerlemeyi geride birakir.
     */
    const val BOOSTED_REPLAYS_PER_DAY: Int = 3

    /**
     * BAYRAK F-3 (ECONOMY_SPEC 7) — yildiz iyilestirme farki.
     *
     * GDD D.2 kaynak tablosunda YOK. Eklenmesinin gerekcesi: bir bolumu erken 1
     * yildizla gecen oyuncu, sonradan 3 yildiz yapsa bile arasindaki coin farkini
     * asla goremezse "erken oynamak cezalandirildi" hissi olusur ve tekrar oynama
     * degersizlesir. Fark **bir kez** odenir (tek seferlik, sinirsiz farming DEGIL).
     *
     * **Ust sinir 55 bolum icin yeniden olculdu (ECONOMY_AUDIT_2 6.4).** Eski
     * yorum "16.020 - 10.010 = 6.010 coin" diyordu; bu 22 bolumun sayisiydi ve
     * kampanya uzatilirken guncellenmedi. 55 bolumde gercek tavan
     * **83.600 - 52.250 = 31.350 coin**tir. Bu NET-YENI gelir degildir: 1 yildiz
     * bandindaki oyuncuyu 3 yildiz bandina tasir, iki bandin uzerine BINMEZ
     * (`EconomySimulationTest.starImprovementCeilingForWholeCampaignIsBounded`).
     *
     * Kapatmak icin tek satir: `= false`.
     */
    const val STAR_IMPROVEMENT_BONUS_ENABLED: Boolean = true

    // =================================================================================
    // Yildiz esikleri (GDD B.3) — YUZDE, mutlak can DEGIL
    // =================================================================================

    /**
     * Us Tahkimi maks cani 20'den 30'a cikardigi icin esikler **yuzde** olmak
     * ZORUNDA. `GameConfig.STAR3_LIVES_FRACTION` ile ayni deger olmali.
     */
    const val STAR3_HEALTH_RATIO: Double = 0.90
    const val STAR2_HEALTH_RATIO: Double = 0.50

    // =================================================================================
    // Meta yukseltme agaci (GDD F — 5 hat, 18 rank, toplam 13.900)
    // =================================================================================

    /**
     * -----------------------------------------------------------------------------
     * GRANULARITE DUZELTMESI (FUN_AUDIT TOP-10 madde 6) — TOPLAM GUC AYNI, ADIM BUYUK
     * -----------------------------------------------------------------------------
     * Sikayet olculdu ve DOGRULANDI: Ates Gucu rank 1, Gatling kd.1'in DPS'ini
     * 43,8 -> 45,1 yapiyordu, yani **0,03 kule kadar** ek guc. 150 coin odeyip
     * fark edilemeyecek bir sey almak "odul" degil muhasebedir.
     *
     * Ayni olcum agacin TOPLAMININ dogru kalibre oldugunu da gosterdi
     * (`MetaImpactReportTool`, dikkatli oyuncu, gercek motor aynasi):
     *
     *     tam agac -> L22 sizinti 13 -> 4 · L34 10 -> 0 · L55 7 -> 0
     *
     * Yani sorun agacin GUCU degil GRANULARITESI. Cozum bu yuzden enflasyon
     * DEGIL yeniden bolumleme: **hat toplamlari (4.000 / 2.750 / 2.700 / 2.750 /
     * 1.700), agac toplami (13.900) ve maks etkiler (hasar +%24, menzil +%15,
     * sermaye 300, can 30) BIREBIR korundu**; yalnizca ayni toplam daha az ve
     * daha buyuk adima bolundu:
     *
     *     Ates Gucu  8 x +%3  ->  4 x +%6   (rank 1 = 0,03 kule -> 0,06 kule)
     *     Menzil     5 x +%3  ->  3 x +%5   (rank 1 = +5 ref-px -> +8 ref-px)
     *
     * Toplam degismedigi icin `maxedMetaDoesNotMakePeakLevelsThreeStarTrivial`
     * (etkin verim 1,426) ve GDD H.6 tablosu aynen gecerli kalir; 55 bolumun
     * cozulebilirligi zaten meta 0 ile olculdugu icin hic etkilenmez.
     *
     * -----------------------------------------------------------------------------
     * IKINCI GECIS — BASLANGIC TEDARIKI (ayni madde, ayni hastalik, baska hat)
     * -----------------------------------------------------------------------------
     * Ilk gecis Baslangic Tedarikini "+25 = L1 sermayesinin %31'i, demek ki
     * hissediliyor" diye ELLEMEDI. Bu yanlisti, cunku **yuzde yanlis birim**.
     *
     * ## Olculen
     * `MetaImpactReportTool` sizinti olcumu (8 olcum bolumu, dikkatli oyuncu):
     *
     *     r0=21  r1=12  r2=8  r3=8  r4=8  r5=6  r6=3
     *
     * Rank 3 ve rank 4 **OLU**: oyuncu 400 + 500 = 900 coin odeyip olculebilir
     * hicbir sey almiyor. Butun 55 bolume yayilan genis olcumde de en zayif iki
     * adim bunlar (80 -> 68 -> 65; +100'un getirisi 3 sizinti / 500 coin).
     *
     * ## Neden: TEDARIK'IN BIRIMI KULEDIR
     * Bu hattin parasi Tedarik'tir ve Tedarik ile yapilabilecek EN KUCUK sey en
     * ucuz kuleyi (Gatling, 60 Tedarik) dikmektir. 60'in ALTINDAKI bir adim
     * tahtada yeni bir kule GARANTI EDEMEZ; yalnizca HUD'daki sayiyi buyutur.
     * Yani "+25 Tedarik", Ates Gucu'ndeki "+%3 = 0,03 kule" hatasinin Tedarik
     * birimindeki tipatip aynisidir. Olculen tablo bunu dogruluyor — acilista
     * alinabilen Gatling sayisi L1'de 1,1,2,2,3,3,3 diye gidiyor: alti rankin
     * ucu tahtada hicbir sey degistirmiyor.
     *
     * ## Cozum: 6 x +25 -> **2 x +75**
     * 75 > 60 oldugu icin `floor((taban+75)/60) >= floor(taban/60) + 1` HER
     * TABAN icin gecerlidir: her rank, her bolumde en az bir kule daha. Olculen
     * karsilik r0=21 -> r1=8 -> r2=3 (genis olcumde 131 -> 68 -> 37), yani olu
     * rank kalmadi.
     *
     * Onerilen 3 x +50 **reddedildi ve gerekcesi olculdu**: 50 < 60 oldugu icin
     * garanti vermiyor ve olu rank fiilen KALIYOR (+50 -> 8, +100 -> 8; ayni
     * sifir fark, sadece bir rank saga kaymis hali).
     *
     * ## Ne DEGISMEDI
     * Hat toplami 2.700, adim toplami +150, maks sermaye 300, agac toplami
     * 13.900 — hepsi birebir ayni. Fiyat/etki egrisi de yukselmedi: eski
     * r1+r2+r3 = 200+300+400 = 900 = yeni r1, eski alti rank = 2.700 = yeni iki
     * rank (`supplyStepsDidNotRaiseThePricePerSupplyPoint`).
     *
     * ## Oynanis karsiligi (asil kazanc)
     * L1 sermayesi 80 -> **155**: ilk meta satin alma artik "tek kule yerine IKI
     * kuleyle basla" demek. Cihaz kaniti (2026-08-18) L1'i tek Gatling ile
     * kaybettirdi; olcum de tek kulenin L5'ten itibaren kaybettigini soyluyor.
     * Ikinci kule oyunun ilk gercek karari, ilk satin alma tam onu aciyor.
     *
     * Us Tahkimi DEGISMEDI: +2 can, tolere edilen sizintinin %10'u ve tolerans
     * ekseninde rank rank olculebilir (`fortificationBuysSurvivalMarginNotFewerLeaks`).
     */
    val FIREPOWER_COSTS: IntArray = intArrayOf(400, 800, 1200, 1600)                    // 4.000
    val OPTICS_COSTS: IntArray = intArrayOf(550, 900, 1300)                             // 2.750
    val STARTING_SUPPLY_COSTS: IntArray = intArrayOf(900, 1800)                         // 2.700
    val FORTIFICATION_COSTS: IntArray =
        intArrayOf(250, 400, 550, 700, 850, 1000, 1150, 1300, 1450)                     // 7.650
    val SALVAGE_COSTS: IntArray = intArrayOf(200, 350, 500, 650, 800)                   // 2.500

    /**
     * **AGAC TOPLAMI — 13.900 -> 19.600 (ECONOMY_AUDIT_2 P0'in kapatilmasi).**
     *
     * ## Kapatilan hata: sink, kampanya uzatilirken olcuye alinmadi
     * 22 -> 55 bolum genislemesinde kaynak x5,2, kilit x7,4 buyudu, **agac x1,0
     * kaldi**. 22 bolumde bu sayilar gercekten kitti (bakiye 6.870, agac 13.900);
     * 55 bolumde en KOTU oyuncu bile kampanyayi ve agaci odedikten sonra
     * +15.245 coin ile bitiriyordu ve agac 3 yildizli oyuncuda **L22'de**
     * tukeniyordu — sonraki 33 bolum hicbir sey satin alamayan bir para oduyor.
     *
     * ## Neden fazla YAPISALDIR (ayar hatasi degil)
     * Soft-lock guvenlik kurali `R(L) >= 2,0 x D(L+1)` (GDD C.3) toplamda
     * `toplamR ~ 2,26 x toplamD` demektir: kilit egrisi ne kadar sikilirsa
     * sikilsin kampanya gelirinin **yarisi bedavadir**. 55 bolumde bu yari
     * 29.145 coin. Yani tek seferlik agac, uzunlukla buyuyen bir kaynagi ancak
     * KENDISI de uzunlukla buyurse emebilir. 13.900, 22 bolumluk kampanyanin
     * sayisiydi ve orada dogruydu.
     *
     * ## Neden 19.600 ve neden BU iki hat
     * Agaci buyutmenin tek yolu rank eklemektir, rank eklemek de oyuncuyu
     * guclendirir — ve uc eksende TAVAN vardir:
     * - **Ates Gucu / Menzil:** [MetaUpgrades.effectiveThroughput] = hasar x
     *   menzil = 1,426 zorluk egrisinin kilididir; tek bir rank daha L11 ve L22'yi
     *   tam metayla 3 yildiza cevirir
     *   (`EconomySimulationTest.maxedMetaDoesNotMakePeakLevelsThreeStarTrivial`).
     *   **Genisletilemez.**
     * - **Hurda Degeri:** 0,70 + 5 x %5 = **0,95**; bir rank daha 1,00 eder ve
     *   "kur-sat" dongusu para basmaya baslar. Tavan +1 rank.
     * - **Us Tahkimi:** yildiz yalnizca sizinti sayisina baktigi icin
     *   ([starHealthFromLeaks], `MetaNeutralStarTest`) bu hat yildiz SATIN ALMAZ,
     *   yalnizca dayaniklilik marji satar; [MetaUpgrades.effectiveThroughput]
     *   disindadir. Emici hat budur. Ust siniri **38 can**: 40'ta L11 tam metayla
     *   3 yildiz olur.
     *
     * Sonuc: Tahkimat 5 -> 9 rank (20 -> 38 can), Hurda 4 -> 5 rank (%70 -> %95).
     * Agac 13.900 -> **19.600**, rank 18 -> 23. Ates Gucu, Menzil ve Baslangic
     * Tedariki'ne **dokunulmadi**.
     *
     * ## Fiyat/etki egrisi YUKSELMEDI (Hard Rule)
     * Iki hattin da etki adimi ve fiyat adimi degismedi: Tahkimat +2 can / +150
     * coin, Hurda +%5 iade / +150 coin. Yeni rank'lar var olan aritmetik dizinin
     * DEVAMIDIR; hicbir mevcut rank'in fiyati veya etkisi degismedi, yani ayni
     * gucu almanin bedeli hicbir noktada artmadi.
     * `MetaUpgradeImpactTest.treeExtensionContinuedTheExistingPriceLadder` kilitler.
     *
     * ## Olculen sonuc (`CoinLedgerTest`)
     * Kampanya sonu fazla: 1 yildiz 15.245 -> **9.545**, 2 yildiz 30.945 ->
     * **25.245**, 3 yildiz 50.995 -> **45.295**. Agacin tamamlanma bolumu
     * ([RANK_GATE_CLEARED_LEVEL] ile birlikte) 3 yildiz L22 -> **L50**,
     * 1 yildiz L37 -> **L50**.
     */
    const val TREE_TOTAL_COST: Int = 19_600

    /** 4 + 3 + 2 + 9 + 5. Granularite duzeltmelerinden once 28, sonra 22, sonra 18 idi. */
    const val TREE_TOTAL_RANKS: Int = 23

    /**
     * **HISSEDILIRLIK ESIGI — rank basina en kucuk anlamli adim.**
     *
     * Bir yukseltme, oyuncunun sahada FARK EDEBILECEGI kadar degistirmelidir;
     * aksi halde satin alma bir odul degil bir makbuzdur. Yuzde tabanli hatlarda
     * esik **%5**: bunun altindaki adim tek bir dalganin dagilim gurultusunun
     * icinde kaybolur (Gatling kd.1 icin %3 = atis basina 0,4 hasar, yani ayni
     * sayida atisla ayni sayida olum).
     *
     * `MetaUpgradeImpactTest` bu esigi butun hatlar icin kilitler; sayi tabanli
     * hatlarda karsiligi "tabanin en az %10'u" olarak olculur.
     *
     * **Yuzde esigi GEREK sarttir, YETER sart degildir.** Baslangic Tedariki
     * +25/rank ile bu esigi rahatca geciyordu (%16,7) ama sahada olu rank
     * uretiyordu, cunku Tedarik'in gercek birimi yuzde degil KULEDIR
     * ([MIN_SUPPLY_STEP_IS_ONE_TOWER]). Sayi tabanli her hat, kendi ekseninde
     * ayrica olculur; nihai kapi `everyRankIsMeasurablyAliveAcrossTheCampaign`.
     */
    const val MIN_PERCEPTIBLE_STEP: Double = 0.05

    /**
     * **TEDARIK ADIMININ TABAN BIRIMI — en ucuz kulenin insa bedeli.**
     *
     * Baslangic Tedariki rank'i, oyuncunun acilista dikebilecegi kule sayisini
     * GARANTILI olarak buyutmelidir. Bu ancak adim en ucuz kule kadar buyukse
     * saglanir: `floor((taban + adim)/60) >= floor(taban/60) + 1` yalnizca
     * `adim >= 60` icin her tabanda dogrudur.
     *
     * `GameConfig.TOWER_SPECS` icindeki en dusuk `buildCost` ile ayni olmali;
     * `MetaUpgradeImpactTest.supplyStepBuysAtLeastOneMoreTowerAtEveryLevel`
     * ikisini birbirine baglar (elle yazilmis sabite degil, spec'in kendisine).
     */
    const val MIN_SUPPLY_STEP_IS_ONE_TOWER: Int = 60

    // Rank basina oynanis etkisi (GDD F tablosu; toplamlar korunmus, adim buyutulmus).
    const val FIREPOWER_DAMAGE_PER_RANK: Double = 0.06      // tum kule hasari +%6, maks +%24
    const val OPTICS_RANGE_PER_RANK: Double = 0.05          // tum kule menzili +%5, maks +%15
    const val STARTING_SUPPLY_PER_RANK: Int = 75            // baslangic Tedariki +75 (>= 1 kule)
    const val FORTIFICATION_HEALTH_PER_RANK: Int = 2        // maks us cani +2
    const val SALVAGE_PER_RANK: Double = 0.05               // satis iadesi +%5, %70 -> %90

    /** Tabanlar — `GameConfig.INITIAL_BASE_LIVES` / `INITIAL_GOLD` ile ayni olmali. */
    const val BASE_MAX_HEALTH: Int = 20
    const val BASE_STARTING_SUPPLY: Int = 150

    /**
     * **HURDA DEGERI TABANI — `TowerEntity.salvageRate` VARSAYILANI ILE AYNI OLMAK
     * ZORUNDA (0,70).**
     *
     * ## Duzeltilen hata: GIZLI VERGI
     * Oyunun kule satis iadesi her zaman **%70** idi (`GameEntities.salvageRate`
     * varsayilani, `EntityDerivedStatsTest.sellValueRefundsSeventyPercentOfEverythingInvested`).
     * Faz 9'da Hurda Degeri meta hatti eklenirken bu taban **0,50'ye indirildi** ve
     * aradaki 20 puan 1.700 coinlik bir hat olarak SATILDI. Motor kule kurarken
     * `salvageRate = metaSalvageRate` (GameEngine:574) verdigi icin varsayilan
     * 0,70 hicbir zaman sahaya cikmiyordu: rank 0 oyuncu **%50** ile oynuyordu.
     *
     * Yani hat bir yukseltme degil bir vergiydi — oyuncu 1.700 coin odeyip
     * ZATEN SAHIP OLDUGU seyi geri satin aliyordu ve rank 4'te ancak baslangic
     * noktasina donuyordu.
     *
     * ## Kural
     * Bir meta hattinin rank 0 degeri, o yukseltme HIC ALINMAMISKEN oyuncunun
     * sahip oldugu degerdir; yukseltme bunun **USTUNE** cikar. Taban 0,70'e geri
     * alindi, rank basina +%5 korundu, yani hat artik %70 -> **%90** goturuyor:
     * kd.3 Gatling satisi 178 -> 229 Tedarik. Ust sinir 1,0'in ALTINDA kalir,
     * yani "kur-sat" ve "yukselt-sat" dongusu hâlâ para uretmez
     * (`EntityDerivedStatsTest` butun kademeleri ve butun rank'lari tarar).
     */
    const val BASE_SALVAGE_RATIO: Double = 0.70

    /**
     * BAYRAK F-1 (ECONOMY_SPEC 7) — **KAMPANYA ILERLEMESI KAPISI. ACIK.**
     *
     * Fiyatlar degismez ([TREE_TOTAL_COST] sabittir); yalnizca *ne zaman* satin
     * alinabildigi kampanya ilerlemesine baglanir.
     *
     * ## Neden acildi (ECONOMY_AUDIT_2 P0'in PACING yarisi)
     * Fazlanin iki belirtisi vardi ve ikisi ayri sorundur:
     * 1. **Miktar** — kampanya sonunda harcanamayan coin. Bunu agacin buyumesi
     *    kapatir (bkz. [TREE_TOTAL_COST]).
     * 2. **Zamanlama** — agac 3 yildizli oyuncuda **L22'de**, 1 yildizlida
     *    L37'de TAMAMEN bitiyordu. Agaci buyutmek bunu tek basina cozmez: 19.600
     *    coin'lik agac bile 3 yildizli oyuncuda ~L27'de biterdi, cunku o oyuncu
     *    parayi orta oyunda zaten kazaniyor. Harcamayi kampanyaya YAYAN sey
     *    kapilardir.
     *
     * ## Kapisiz vs kapili olcum (`CoinLedgerTest.treeCompletionLevel`)
     *     BAND       KAPISIZ (agac 19.600)   KAPILI
     *     1 yildiz          L45                L50
     *     3 yildiz          L27                L50
     *
     * ## Neden oyuncuyu HAKSIZ kisitlamiyor
     * - Her hattin **ilk iki ranki kapisizdir**: yeni oyuncunun ilk satin alma
     *   karari hic gecikmez, erken oyunu **para** paceler (L10'da 1 yildiz
     *   bakiyesi 2.290, kapisiz tavan 6.550 — yani kapi degil cuzdan sinirliyor).
     * - Hicbir bolum meta yukseltme GEREKTIRMEZ: `CampaignSolvabilityAllLevelsTest`
     *   55/55'i meta 0 ile gecer. Kapi bir GUCLENME takvimidir, bir duvar degil.
     * - Kapi asla ulasilamaz olamaz: en yuksek kapi L50 < 55
     *   (`everyGateIsSatisfiableWithinTheCampaign`).
     */
    const val META_RANK_GATES_ENABLED: Boolean = true

    /**
     * Rank -> o rank'i acmak icin TEMIZLENMIS olmasi gereken bolum (0 = kapi yok).
     *
     * ## Eski tablo neden atildi
     * `[0, 0, 5, 9, 13, 16, 19, 21]` **22 bolumluk kampanya ve 28 rank'lik agac**
     * icin yazilmisti. Bugun kampanya 55 bolum; eski tablonun en yuksek kapisi
     * L21 idi, yani bayrak acilsa bile agac yine kampanyanin %38'inde acilmis
     * olurdu — kapilar hicbir sey yaymazdi.
     *
     * ## Yeni tablo nasil turetildi
     * Dizinin uzunlugu **en derin hattin rank sayisidir** (Us Tahkimi, 9). Agacin
     * rank katmani basina bedeli ve o katmanda kac hattin secenek sundugu:
     *
     *     KATMAN  SECENEK  BEDEL   KAPI   NEDEN
     *     r1         5     2.300     -    ilk karar gecikmez
     *     r2         5     4.250     -    ilk karar gecikmez
     *     r3         4     3.550    L10   Act I sonu; 1y bakiyesi 2.290 -> para paceler
     *     r4         3     2.950    L20   Act II ortasi
     *     r5         2     1.650    L30   Act III
     *     r6         1     1.000    L37   Act IV
     *     r7         1     1.150    L42
     *     r8         1     1.300    L46
     *     r9         1     1.450    L50   son satin alma, kampanya sonuna 5 bolum
     *
     * Cok secenekli katmanlar one, tek secenekli katmanlar (yalnizca Us Tahkimi)
     * sona kondu: boylece gec kampanyada her ~4-5 bolumde bir satin alma dusuyor
     * ve L50'ye kadar dukkan bos kalmiyor.
     *
     * `purchasableTreeCeiling(0)` = 6.550, agacin **%33'u** — kapilar erken oyunda
     * gercekten kit (`rankGateCeilingIsMonotonicAndScarceEarly` <%50 ister).
     */
    val RANK_GATE_CLEARED_LEVEL: IntArray = intArrayOf(
        /* rank 1 */ 0, /* rank 2 */ 0, /* rank 3 */ 10, /* rank 4 */ 20,
        /* rank 5 */ 30, /* rank 6 */ 37, /* rank 7 */ 42, /* rank 8 */ 46,
        /* rank 9 */ 50,
    )

    // =================================================================================
    // Gunluk gorevler (GDD E.1)
    // =================================================================================

    const val DAILY_REWARD_PARTICIPATION: Int = 60
    const val DAILY_REWARD_VOLUME: Int = 80
    const val DAILY_REWARD_SKILL: Int = 120
    const val DAILY_ALL_COMPLETE_BONUS: Int = 100

    /** 60 + 80 + 120 + 100. Gunluk gorev tavani. */
    const val DAILY_MAX_TOTAL: Int = 360

    const val DAILY_MISSION_SLOTS: Int = 3
    const val DAILY_REROLLS_PER_DAY: Int = 1

    // =================================================================================
    // Haftalik gorevler (GDD E.2) — 2 gorev, toplam 1.100
    // =================================================================================

    /** "Uzun Sefer": bu hafta 12 bolum tamamla (tekrarlar sayilir). */
    const val WEEKLY_LONG_PATROL_TARGET: Int = 12
    const val WEEKLY_LONG_PATROL_REWARD: Int = 500

    /** "Elit Operator": bu hafta 15 YENI yildiz kazan. */
    const val WEEKLY_ELITE_TARGET: Int = 15
    const val WEEKLY_ELITE_REWARD: Int = 600

    /**
     * "Elit Operator"in ARZ TUKENDIGINDE gectigi ikame hedefi: bu hafta 24 bolum
     * tamamla. Gerekce ve kurallar `MissionPools.weekly` dosya icinde.
     *
     * Odul DEGISMEZ ([WEEKLY_ELITE_REWARD]), yani [WEEKLY_BUDGET] 1.100'de kalir —
     * bu bir enflasyon degil, zaten vaat edilmis butcenin teslimidir.
     * 24 = 2 x [WEEKLY_LONG_PATROL_TARGET]: ikame gorev uzun seferin devamidir ve
     * ondan daha hafif olamaz.
     */
    const val WEEKLY_ELITE_RESERVE_TARGET: Int = 24

    /**
     * Kampanyanin TOPLAM yildiz arzi. Yildiz best-of'tur ve asla dusmez, yani bu
     * bir tavandir: haftalik "yeni yildiz" gorevinin omur boyu uretebilecegi
     * toplam ilerleme tam olarak bu kadardir.
     */
    const val TOTAL_CAMPAIGN_STARS: Int = CAMPAIGN_LEVELS * 3

    const val WEEKLY_MISSION_COUNT: Int = 2
    const val WEEKLY_BUDGET: Int = 1_100

    // =================================================================================
    // Basarimlar (GDD E.3) — bu fazda yalnizca butce; katalog Faz 10
    // =================================================================================

    const val ACHIEVEMENT_COUNT: Int = 24
    const val ACHIEVEMENT_TOTAL: Int = 5_200

    // =================================================================================
    // Rewarded reklam ekonomisi (GDD G.3 / G.4)
    // =================================================================================

    /** R1 "Tedarik Talebi" — dolu reklam odulu. */
    const val R1_REWARD_FILLED: Int = 150

    /** R1 no-fill / kapatma / hata / 5 sn timeout odulu. Gunluk *hak* tuketilmez. */
    const val R1_REWARD_FALLBACK: Int = 50

    /** GDD G.3 — gunde 3 dolu gosterim hakki. */
    const val R1_VIEWS_PER_DAY: Int = 3

    /**
     * BAYRAK F-6 (ECONOMY_SPEC 5.4) — **R1 GUNLUK COIN BUTCESI.**
     *
     * GDD G.4 "no-fill'de hak tuketilmez, cooldown yok" diyor. Bu haliyle ucak
     * modunda **sonsuz +50 dongusu** demektir (bolum 7 kilidi 100 coin; iki dokunus
     * kilidi acar ve butun coin duvari matematigi anlamsizlasir).
     *
     * Butce, doktrini BOZMADAN dongüyu sonlandirir: hak tuketilmez, cooldown yok,
     * buton her zaman tiklanabilir, hicbir ilerleme yolu kapanmaz — yalnizca gunluk
     * coin tavani var. 3 x 150 = 450, yani R1'in GDD'de zaten hedeflenen gunluk
     * degeri. Yan etki OLUMLU: sansli ve sanssiz oyuncu ayni gunluk toplami alir.
     *
     * `AdPolicyConfig.SUPPLY_DROP_FALLBACK_DAILY_CAP` (reklam katmani, 3 adet)
     * bunun sayi tarafindaki tamamlayicisidir: 3 x 50 = 150 <= 450, yani iki kural
     * hicbir zaman celismez. Coin miktarlarinda yetkili katman BU dosyadir.
     */
    const val R1_COIN_BUDGET_PER_DAY: Int = R1_REWARD_FILLED * R1_VIEWS_PER_DAY

    /**
     * BAYRAK F-11 (ECONOMY_SPEC C.4) — **R1 ODULUNUN SIRADAKI RANK'A GORE
     * OLCEKLENMESI. VARSAYILAN KAPALI.**
     *
     * Soru: "150 coin, 13.900'luk agacta gec oyunda hicbir sey ifade etmez."
     * Analiz (ECONOMY_SPEC C.4) bunun **olcum hatasi** oldugunu gosteriyor: dogru
     * kiyas 13.900 (tum agac) degil, o anki SIRADAKI rank'in fiyatidir. 450 coin/gun,
     * en pahali rank'in (850) %53'udur — anlamsiz degil.
     *
     * Yine de olceklenme tanimlanmistir: dolu odul =
     * `clamp(round10(siradakiRank x R1_SCALE_OF_NEXT_RANK), MIN, MAX)`.
     *
     * **Neden varsayilan KAPALI:** [R1_COIN_BUDGET_PER_DAY] sabit (enflasyon yasagi).
     * Odul/gosterim buyudugunde gunluk gosterim sayisi kacinilmaz olarak duser
     * (850 rank -> 210+210+30), yani ucuncu gosterim 30 coin oder. Bu, hem oyuncu icin
     * kotu bir his hem de reklam gosterim sayisinda dusustur. Gec oyunda rewarded'i
     * degerli tutan dogru araclar R3 "Cift Odeme" (L22'de 1.230 coin, kendiliginden
     * olcekleniyor) ve guclendirici reklamlaridir (coin odemez, enflasyon 0).
     *
     * Acmak icin tek satir: `= true`; davranis zaten testli.
     */
    const val R1_ADAPTIVE_REWARD_ENABLED: Boolean = false

    /** Adaptif odulun siradaki rank fiyatina orani. */
    const val R1_SCALE_OF_NEXT_RANK: Double = 0.25

    /** Adaptif odul taban ve tavani. Taban = sabit odul, yani olceklenme ASLA azaltmaz. */
    const val R1_REWARD_FILLED_MIN: Int = R1_REWARD_FILLED
    const val R1_REWARD_FILLED_MAX: Int = 250

    /** R2 "Takviye" — yenilgide us canini bu degere getirir, savas devam eder. */
    const val R2_REVIVE_HEALTH: Int = 5
    const val R2_USES_PER_BATTLE: Int = 1

    /** R3 "Cift Odeme" — o temizligin coin odulu x2. Savas basina 1. */
    const val R3_MULTIPLIER: Int = 2

    /**
     * BAYRAK F-7 (ECONOMY_SPEC 5.2) — R3, **taban tekrar oduluna (25) uygulanmaz.**
     * Uygulanirsa "L1'i 2x hizda tekrar oyna + reklam izle" dongusu dakika basina
     * ilerlemekten daha karli hale gelir ve reklam spam'ini tesvik eder.
     */
    const val R3_APPLIES_TO_REPLAY_FLOOR: Boolean = false

    /** "Reklamlari Kaldir" IAP hediyesi (GDD D.2). v1.0'da IAP YOK; sabit hazir bekler. */
    const val REMOVE_ADS_GIFT: Int = 500

    // =================================================================================
    // Faz 10 — GUCLENDIRICILER (savas ici tek kullanimlik)
    //
    // Tasarim, gerekce ve arbitraj analizi: `BoosterSystem.kt` dosya basi + ECONOMY_SPEC B.
    // Hepsi BAYRAK: GDD'de yoktur, tek satirla kapatilir, oynanis dengesine GIRMEZ
    // (her bolum sifir guclendiriciyle gecilebilir olmali).
    // =================================================================================

    /** BAYRAK F-8 — Acil Tedarik (yalnizca rewarded). Kapatmak icin `= false`. */
    const val EMERGENCY_SUPPLY_ENABLED: Boolean = true

    /** BAYRAK F-9 — Hava Destegi (Tedarik fiyatli). */
    const val AIR_SUPPORT_ENABLED: Boolean = true

    /**
     * BAYRAK F-10 — Us Tamiri (Coin fiyatli).
     *
     * **ON KOSUL:** motor yildizi `effectiveStarHealth(...)` uzerinden hesaplamalidir.
     * Bu devir yapilmadan `true` kalmasi "tamir et -> yildiz atla -> kar et" arbitrajini
     * acar. Bkz. ECONOMY_SPEC 9 devir listesi maddesi 4.
     */
    const val BASE_REPAIR_ENABLED: Boolean = true

    // ---- Acilma bolumleri --------------------------------------------------------
    //
    // Acil Tedarik en once acilir CUNKU ilk 6 bolumde oyuncunun hic coini yoktur ve
    // rewarded'in tek anlamli degeri odur (ECONOMY_SPEC C.1). Us Tamiri ilk UCRETLI
    // bolumle (7) eslenir: oradan once us kaybi zaten nadir ve oyuncunun coini yok.

    const val EMERGENCY_SUPPLY_UNLOCK_LEVEL: Int = 2
    const val AIR_SUPPORT_UNLOCK_LEVEL: Int = 4
    const val BASE_REPAIR_UNLOCK_LEVEL: Int = 7

    // ---- Hava Destegi ------------------------------------------------------------

    /**
     * Tedarik fiyati = BASE + STEP x (L - 1). L4 = **125**, L22 = 269, L55 = 533.
     *
     * ---------------------------------------------------------------------
     * KALIBRASYON ANKRAJI DEGISTI: "acilis Tedariki" -> "TAM BIR KULE"
     * ---------------------------------------------------------------------
     * Eski gerekce fiyati **baslangic Tedarikinin %85-100'u** olarak tarif
     * ediyordu ("bir acilis kulesinden vazgecmek"). O ifade, acilis Tedariki
     * bir-iki kule alirken dogruydu. Sermaye kadrodan turetilir olunca
     * (bkz. `SupplyBudgetModel.startingSupply`) acilis purse'u 3-7 kule aldi ve
     * ayni yuzde artik "bir kule" demiyor.
     *
     * Ankraj bu yuzden purse'a degil KULEYE baglandi: **acilista fiyat, tam
     * yukseltilmis bir Gatling'e (insa 60 + kademe 2 65 = 125) esittir.** BASE
     * 96 -> 101 yalnizca bu esitligi TAM saglamak icin (101 + 8x3 = 125).
     * Guclendirici hicbir bolumde bir kuleden ucuz olamaz; aksi halde en iyi
     * strateji kule kurmak yerine dugmeye basmak olur.
     */
    const val AIR_SUPPORT_SUPPLY_BASE: Int = 101
    const val AIR_SUPPORT_SUPPLY_STEP: Int = 8

    /**
     * Ekrandaki her dusmandan silinen MAKS CAN orani.
     *
     * **SERT KISIT — SAVAS BASINA TOPLAM, tek kullanim degil:**
     * `AIR_SUPPORT.maxUsesPerBattle x AIR_SUPPORT_DAMAGE_FRACTION < 1,0`.
     *
     * FAZ 10.1 — 0,60 -> 0,45. Duzeltilen sey bir mantik hatasi, bir tuning
     * karari degil: kisit "tek kullanim tam canli dusmani oldurmez" diye
     * yazilmisti, ama Hava Destegi'nin savas basina **iki** kullanimi var
     * (1 ucretli + 1 rewarded) ve bekleme suresi 45 sn, yani ikisi ayni uzun
     * dalgada kullanilabiliyor. 2 x 0,60 = 1,20 > 1,0 -> iki kullanim ekrandaki
     * HER dusmani, boss dahil, **dogrudan oldurur**. Yani kalkanin korumasi
     * gereken sey (pay-to-win / dalga temizleme butonu) aslinda aciktı.
     *
     * 2 x 0,45 = 0,90 -> hicbir dusman yalnizca guclendiriciyle olmez; son %10
     * her zaman kulelerden gelmek zorunda.
     *
     * ORANIN (yuzde, sabit sayi degil) OLMASI KASITLI: dusman cani x3,5
     * kalibre edildiginde (Faz 10) hava destegi kendiliginden olcegi tuttu.
     * Sabit hasar olsaydi ayni gun degersizlesirdi. Ayni sebeple zirhtan da
     * bagimsizdir — zirh 0,55 -> 0,78/0,86 cikarken kurtarma degeri korundu ve
     * "tank sizdi, fuze rampam yok" durumunun cevabi olmaya devam etti.
     */
    const val AIR_SUPPORT_DAMAGE_FRACTION: Double = 0.45

    /** Iki hava destegi arasinda zorunlu bekleme. "Uzun bekleme" gereksinimi. */
    const val AIR_SUPPORT_COOLDOWN_MS: Long = 45_000L

    // ---- Acil Tedarik ------------------------------------------------------------

    /**
     * Verilen Tedarik = round10(BASE + STEP x (L - 1)). L1 = 60, L6 = 80, L22 = 140.
     *
     * Kalibrasyon: her zaman **yaklasik bir temel kule** kadar. Daha fazlasi
     * ECONOMY_SPEC A sikilastirmasini geri alir; daha azi butona basmaya deger olmaz.
     */
    const val EMERGENCY_SUPPLY_BASE: Int = 60
    const val EMERGENCY_SUPPLY_STEP: Int = 4

    const val EMERGENCY_SUPPLY_COOLDOWN_MS: Long = 0L

    // ---- Us Tamiri ---------------------------------------------------------------

    /**
     * Coin fiyati = BASE + STEP x (L - 1). L7 = 240, L22 = 540.
     *
     * UC KISIT BIRDEN (hepsi testli):
     * 1. `> 150` (en ucuz meta rank) — yoksa agac yerine tamir farming'i cazip olur.
     * 2. `< R(L)` (1 yildiz odulu) — yoksa yenilgiyi zafere cevirmek NET ZARAR olur ve
     *    guclendirici olu yatirim haline gelir.
     * 3. `>= R(L,3) - R(L,1)` (kampanyadaki en buyuk yildiz atlama farki) — yildiz
     *    notrlugu ([effectiveStarHealth]) bir gun kaldirilsa BILE tamirle yildiz
     *    satin almak karli olmasin. STEP 15 yerine 20 secilmesinin nedeni budur:
     *    yildiz farki 18/bolum buyudugu icin 15 adimla L14'ten sonra kisit kirilir.
     */
    const val BASE_REPAIR_COIN_BASE: Int = 120
    const val BASE_REPAIR_COIN_STEP: Int = 20

    /** Geri verilen can = ceil(maksCan x oran), kaybedilen candan fazla degil. 20 -> 8. */
    /**
     * ⚠ 2026-08-21: ORAN yerine DUZ CAN (kullanici karari).
     *
     * Eskiden maks canin %40'i geri verilirdi — 20 canlik bir usste 8 can, ve
     * meta Tahkimat ile maks can buyudukce sessizce daha da buyuyordu. Oyuncu
     * ne alacagini ONCEDEN bilemiyordu.
     *
     * Artik iki YOL, IKI FARKLI sayi: coinle 4 can, reklamla 7. Reklam yolunun
     * daha comert olmasi bilincli — oyuncu parayi degil ZAMANI odedigi icin
     * karsiligini gormeli, yoksa reklam yolu olu kalir.
     */
    const val BASE_REPAIR_LIVES_PAID: Int = 4
    const val BASE_REPAIR_LIVES_AD: Int = 7

    const val BASE_REPAIR_COOLDOWN_MS: Long = 60_000L

    // ---- Guclendirici rewarded butcesi -------------------------------------------

    /**
     * Gunluk guclendirici-reklam hakki (tum tipler ORTAK havuz), savas basina en fazla
     * her tipten [BoosterType.adUsesPerBattle].
     *
     * **Coin butcesi YOK ve gerekmiyor:** guclendirici reklami hicbir coin odemez,
     * yalnizca savas ici etki verir. Bu yuzden R1'in [R1_COIN_BUDGET_PER_DAY]
     * enflasyon korumasi burada gereksizdir — enflasyona katkisi tam olarak 0'dir.
     * Sinirlayan sey oynanis (savas basina 1 kullanim), para degil.
     */
    const val BOOSTER_AD_VIEWS_PER_DAY: Int = 4

    // =================================================================================
    // Saat manipulasyonu (GDD E.4)
    // =================================================================================

    const val MS_PER_HOUR: Long = 3_600_000L
    const val MS_PER_DAY: Long = 86_400_000L

    /** Sahte ileri atlama tespiti: duvar saati bu kadar ilerlediyse elapsed ile capraz kontrol. */
    const val CLOCK_FORWARD_SUSPECT_HOURS: Long = 20

    /** Geri alma toleransi — kucuk NTP duzeltmeleri supheye yol acmaz. */
    const val CLOCK_BACKWARD_TOLERANCE_MS: Long = 60_000L

    /** GDD E.4 kural 4 — kaba tavan (7 GERCEK gun icinde en fazla 8 sifirlama). */
    const val MAX_DAILY_RESETS_PER_7_DAYS: Int = 8

    /** GDD E.4 kural 3 — supheli dongude yalnizca GOREV odulu carpani. */
    const val SUSPECT_REWARD_MULTIPLIER: Double = 0.50

    /** GDD E.4 — ileri atlama **en fazla BIR** sifirlama sayilir. 30 gun atlama 30 odul vermez. */
    const val MAX_RESETS_PER_EVALUATION: Int = 1

    // =================================================================================
    // Kayit surumu (SaveManager)
    // =================================================================================

    /**
     * Kayit semasi surumu. Ekonomi alanlarinin anlami degistiginde artirilir ve
     * `SaveManager.migrateIfNeeded` bir dal ekler. Faz 9 = surum 2
     * (surum 1 = Faz 1-8'in yalnizca yildiz/highScore/ses tutan semasi).
     */
    const val SAVE_VERSION: Int = 2
}
