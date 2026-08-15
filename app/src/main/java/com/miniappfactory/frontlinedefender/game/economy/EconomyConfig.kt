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

    /** v1.0 kampanyasi 22 bolum. `GameConfig.CAMPAIGN_LEVEL_COUNT` ile ayni olmali. */
    const val CAMPAIGN_LEVELS: Int = 22

    /** GDD C.1 kural 1 — bolum 1..6 tamamen bedava. Ilk ucretli bolum 7. */
    const val FIRST_PAID_LEVEL: Int = 7

    /**
     * Konuslanma bedelleri D(L), L = 7..22. **TEK SEFERLIK.** Toplam 3.140 coin.
     * `GameConfig.CAMPAIGN[L].deploymentCost` ile birebir ayni olmak ZORUNDA;
     * `EconomyGameConfigContractTest` bunu kilitler.
     */
    val LOCK_COSTS: IntArray = intArrayOf(
        /* L7  */ 100, /* L8  */ 110, /* L9  */ 120, /* L10 */ 130,
        /* L11 */ 140, /* L12 */ 150, /* L13 */ 165, /* L14 */ 180,
        /* L15 */ 195, /* L16 */ 210, /* L17 */ 225, /* L18 */ 240,
        /* L19 */ 255, /* L20 */ 270, /* L21 */ 300, /* L22 */ 350,
    )

    /** GDD C.3 — kampanyanin toplam kilit maliyeti. Regresyon kilidi. */
    const val TOTAL_LOCK_COST: Int = 3_140

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
