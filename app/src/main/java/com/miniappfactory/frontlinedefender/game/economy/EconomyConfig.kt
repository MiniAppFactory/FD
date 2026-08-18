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
     * degersizlesir. Fark **bir kez** odenir; kampanya boyunca ust sinir
     * 16.020 - 10.010 = 6.010 coin (tek seferlik, sinirsiz farming DEGIL).
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
    // Meta yukseltme agaci (GDD F — 5 hat, 28 rank, toplam 13.900)
    // =================================================================================

    val FIREPOWER_COSTS: IntArray = intArrayOf(150, 250, 350, 450, 550, 650, 750, 850) // 4.000
    val OPTICS_COSTS: IntArray = intArrayOf(250, 400, 550, 700, 850)                    // 2.750
    val STARTING_SUPPLY_COSTS: IntArray = intArrayOf(200, 300, 400, 500, 600, 700)      // 2.700
    val FORTIFICATION_COSTS: IntArray = intArrayOf(250, 400, 550, 700, 850)             // 2.750
    val SALVAGE_COSTS: IntArray = intArrayOf(200, 350, 500, 650)                        // 1.700

    const val TREE_TOTAL_COST: Int = 13_900
    const val TREE_TOTAL_RANKS: Int = 28

    // Rank basina oynanis etkisi (GDD F tablosu).
    const val FIREPOWER_DAMAGE_PER_RANK: Double = 0.03      // tum kule hasari +%3
    const val OPTICS_RANGE_PER_RANK: Double = 0.03          // tum kule menzili +%3
    const val STARTING_SUPPLY_PER_RANK: Int = 25            // baslangic Tedariki +25
    const val FORTIFICATION_HEALTH_PER_RANK: Int = 2        // maks us cani +2
    const val SALVAGE_PER_RANK: Double = 0.05               // satis iadesi +%5

    /** Tabanlar — `GameConfig.INITIAL_BASE_LIVES` / `INITIAL_GOLD` ile ayni olmali. */
    const val BASE_MAX_HEALTH: Int = 20
    const val BASE_STARTING_SUPPLY: Int = 150
    const val BASE_SALVAGE_RATIO: Double = 0.50

    /**
     * BAYRAK F-1 (ECONOMY_SPEC 7) — **KAMPANYA ILERLEMESI KAPISI. VARSAYILAN KAPALI.**
     *
     * Fiyatlar degismez (13.900 sabit); yalnizca *ne zaman* satin alinabildigi
     * kampanya ilerlemesine baglanir. Gerekce: tam maksli meta, LEVEL_DESIGN E.3'un
     * beklenen can kaybi (ELL) sayilarini ~%30 kucultur ve orta oyunun beklenen
     * yildizini 2'den 3'e cikarir (ECONOMY_SPEC 6.2 tablosu).
     *
     * **Varsayilan `false`, cunku bu GDD F'te olmayan bir kisittir ve oyuncuyu
     * kisitlayan bir mekanik PO/Game Director karari olmadan varsayilan olamaz.**
     * Ayrica gelir egrisi kapisiz halde de agacin kampanya ortasinda bitmesine
     * izin vermiyor (ECONOMY_SPEC 2.6). Acmak icin tek satir: `= true`;
     * davranis zaten testli.
     */
    const val META_RANK_GATES_ENABLED: Boolean = false

    /** Rank -> o rank'i acmak icin TEMIZLENMIS olmasi gereken bolum (0 = kapi yok). */
    val RANK_GATE_CLEARED_LEVEL: IntArray = intArrayOf(
        /* rank 1 */ 0, /* rank 2 */ 0, /* rank 3 */ 5, /* rank 4 */ 9,
        /* rank 5 */ 13, /* rank 6 */ 16, /* rank 7 */ 19, /* rank 8 */ 21,
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
    const val BASE_REPAIR_HEALTH_RATIO: Double = 0.40

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
