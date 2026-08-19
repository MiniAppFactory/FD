package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 10 / 10.1 — SAVAS ICI TEDARIK BUTCESI (ECONOMY_SPEC A).
 *
 * Testcinin sikayetini olculebilir hale getirir ve uygulanan sayilarin gercekten
 * cozdugunu kanitlar:
 *   *"6 tane kule yapacak para kazaniyorsun ama 2 tanesi yetiyor."*
 *
 * FAZ 10.1'DE DEGISEN SEY: model artik hicbir dalga/kule sayisini KOPYALAMIYOR;
 * gelir `WaveDefinitions` x `ENEMY_SPECS`, bolen ise `TOWER_SPECS`ten canli
 * turetiliyor ([CampaignFacts]). Bu yuzden bu dosyadaki beklenen degerler artik
 * "dokumandaki tablo" degil **bugunku oyunun olculmus gercegi**dir ve dalga tablosu
 * degistiginde kirilmalari ISTENEN davranistir — sessizce yaniltici olmalari degil.
 */
class SupplyBudgetTest {

    // =================================================================================
    // 1. TESHIS — bolluk sayiyla kanitlanir
    // =================================================================================

    /**
     * Faz 10 ONCESI ekonomi, BUGUNKU dalga tablosunda. SPI > 4 = artan Tedarigin
     * harcanacak yeri yok, karar olur.
     *
     * Iki tarafin AYNI dalga tablosunda karsilastirilmasi kasitli: eski dalga
     * tablosunun toplamlariyla karsilastirmak, dalga sikilastirmasinin getirdigi
     * geliri odul kesintisinin hanesine yazardi.
     */
    @Test
    fun theEconomyBeforeTighteningWasMeasurablyFarTooFlush() {
        // ESIK **BANDA BAGLANDI**, mutlak bir sayiya degil.
        //
        // Once `spi > 4.0` yaziyordu. O 4,0, o gunun bolum uzunluklarina (6-9
        // dalga) gomulu bir sayiydi; bolumler 5-7 dalgaya inince ESKI ekonominin
        // olculen SPI'si de dustu (L3 3,65) ve test, iddiasi hâlâ dogruyken
        // kirildi. Iddia sudur: **eski ekonomi kabul edilebilir bandin USTUNDEYDI**.
        // Bant (`SPI_TARGET_MAX`) dokumanin sahibi oldugu bir sayi, dolayisiyla
        // esik artik bolum uzunlugundan bagimsiz.
        for (level in 1..6) {
            val legacy = SupplyBudgetModel.legacySupplyPressureIndex(level)
            val today = SupplyBudgetModel.supplyPressureIndex(level)
            assertTrue(
                "L$level sikilastirma oncesi SPI %.2f — hedef bandin ustunde (%.1f) olmaliydi"
                    .format(legacy, SupplyBudgetModel.SPI_TARGET_MAX),
                legacy > SupplyBudgetModel.SPI_TARGET_MAX,
            )
            assertTrue(
                "L$level: eski ekonomi bugunkunun (%.2f) en az 1,8 katini vermeliydi, verdigi %.2f"
                    .format(today, legacy),
                legacy >= today * 1.8,
            )
        }
    }

    /**
     * TESHIS ARTIK TARIHTIR — bu test o teshisin GERI GELMEMESINI koruyor.
     *
     * Onceki hali (`aGatlingGunCostsOnlyFiveInfantryKillsToday`) bugunku bolluğu
     * belgeliyordu: Gatling 60 Tedarik, piyade 12 oduyor -> **5 oldurme = 1 kule**,
     * bir dalga 6-14 piyade getirdigi icin her dalga 1-3 kule finanse ediyordu.
     *
     * Odul tablosu x1/3 uygulandiginda (gameplay ajani, ECONOMY_SPEC 9 madde 2)
     * bu test KIRILDI — kirilmasi istenen bir testti. Yerine gecen sey teshisin
     * tersi: kule basi oldurme sayisi hedef banda GIRMIS olmali.
     */
    @Test
    fun aGatlingGunNowCostsAboutFifteenInfantryKills() {
        val gatling = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN).buildCost
        val infantry = GameConfig.ENEMY_SPECS.getValue(GameConfig.EnemyType.INFANTRY).rewardGold
        assertEquals(60, gatling)
        assertEquals("odul tablosu x1/3 uygulanmadi", 4, infantry)

        val kills = SupplyBudgetModel.killsPerTower(gatling, infantry)
        assertEquals(15.0, kills, 1e-9)
        assertTrue(
            "kule basi $kills oldurme — 12'nin altina inerse eski bolluk geri gelir",
            kills >= 12.0,
        )
    }

    @Test
    fun tightenedRewardMakesATowerCostAboutFifteenKills() {
        // HEDEF: bir kule ~15 oldurme. Odul tablosu 12 -> 4 olunca tam olarak bu cikar.
        val gatling = 60
        val tightenedInfantry = SupplyBudgetModel.TIGHTENED_ENEMY_SUPPLY_REWARD.getValue("INFANTRY")
        assertEquals(4, tightenedInfantry)
        assertEquals(15.0, SupplyBudgetModel.killsPerTower(gatling, tightenedInfantry), 1e-9)
        assertTrue(
            "kule basi oldurme sayisi 12'nin altinda kalirsa bolluk cozulmemis olur",
            SupplyBudgetModel.killsPerTower(gatling, tightenedInfantry) >= 12.0,
        )
    }

    @Test
    fun tightenedRewardTablePreservesEnemyValueRatios() {
        // Kule kimlikleri ve hedef secimi DEGISMEMELI: her dusmanin piyadeye gore
        // goreli degeri eski tabloyla ayni kalmali (+-%15).
        val legacy = SupplyBudgetModel.LEGACY_ENEMY_SUPPLY_REWARD
        val tightened = SupplyBudgetModel.TIGHTENED_ENEMY_SUPPLY_REWARD
        assertEquals(legacy.keys, tightened.keys)
        legacy.forEach { (name, old) ->
            val oldRatio = old.toDouble() / legacy.getValue("INFANTRY")
            val newRatio = tightened.getValue(name).toDouble() / tightened.getValue("INFANTRY")
            assertEquals("$name goreli degeri kaydi", oldRatio, newRatio, oldRatio * 0.15)
        }
    }

    @Test
    fun integerRoundingKeepsTheEffectiveScaleTight() {
        // Tamsayi yuvarlama bolum bazinda olcegi kaydirir; kayma bandinin disina
        // cikarsa bir bolum sessizce daha bol/kit olur.
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val scale = SupplyBudgetModel.effectiveRewardScale(level)
            assertTrue(
                // BANT 0,32..0,36 -> 0,31..0,34 (Faz 13, iki sebep):
                //  1. Model artik 8 degil 55 bolumu kapsiyor; L9+ ilk kez olculuyor.
                //  2. Odul carpani artik motorla AYNI aritmetikte (Float) hesaplaniyor;
                //     Double'da 20 x 1,3 -> 25 cikiyordu, motorda 26. Tank/boss
                //     agirlikli bolumlerde bu olcegi kaydiriyordu.
                // Olculen aralik: 0,319 (L18) .. 0,333 (L1). Nominal hedef 1/3.
                "L$level etkin olcek %.3f — 0,31..0,34 bandi disinda".format(scale),
                scale in 0.31..0.34,
            )
        }
    }

    @Test
    fun startingSupplyIsNotTheDominantLever() {
        // Onemli bulgu: baslangic Tedariki L1 butcesinin %13'u, L6'nin %5'i.
        // Yalnizca onu dusurmek sikayeti COZMEZ; asil kaldirac dalga geliri.
        for (level in 1..6) {
            val share = 100.0 * SupplyBudgetModel.LEGACY_STARTING_SUPPLY /
                SupplyBudgetModel.legacySupplyBudget(level)
            assertTrue(
                "L$level: baslangic Tedariki butcenin %%%.1f'i — beklenen <%%20".format(share),
                share < 20.0,
            )
        }
    }

    // =================================================================================
    // 2. ONERILEN SAYILAR — ECONOMY_SPEC A tablosu birebir
    // =================================================================================

    /**
     * BASLANGIC TEDARIKI ARTIK BIR **KURAL**, TABLO DEGIL.
     *
     * Eski tablo (80/90/110/120/140/150, sonra L7..L22 duz 150, Act III/IV/V
     * 220/270/320) bolumler 5-7 dalgaya inince iki ayri yerden kirildi:
     *  · SPI bandin ALTINA dustu (L4 1,33 · L5 1,31 · L7 1,41), cunku gelirin
     *    yarisi dalga sayisiyla birlikte gitti;
     *  · ve gec bolumler ACILIS DALGASINDA kaybedilir oldu — 270 Tedarik iki
     *    kule alirken W1 kirk govde gonderiyordu (olcum: dalga izi,
     *    `CampaignSolvabilityAllLevelsTest`).
     *
     * Yeni kural: **L3'ten itibaren sermaye = tasarlanan kadronun kademe-1
     * maliyeti**, catallanan haritada x1,5. L1/L2 ogretici istisnasidir.
     * Bu test kuralin ta kendisini yeniden hesaplayarak dogrular; sabit bir
     * sayi dizisi tutmaz, yani kule fiyati degisirse bayatlamaz.
     */
    @Test
    fun startingSupplyIsDerivedFromTheDesignedRosterNotAFrozenTable() {
        assertEquals("L1 ogretici sermayesi", 80, startingSupplyFor(1))
        assertEquals("L2 ogretici sermayesi", 90, startingSupplyFor(2))

        for (level in 3..EconomyConfig.CAMPAIGN_LEVELS) {
            val order = GameConfigCampaignFacts.unlockedTowersInOrder(level)
            val roster = SupplyBudgetModel.DESIGNED_ROSTER_SIZE[level - 1]
            val tierOneCost = (0 until roster).sumOf {
                GameConfigCampaignFacts.towerBuildCost.getValue(order[it % order.size])
            }
            val expected = if (GameConfigCampaignFacts.hasScarceCoverage(level)) {
                Math.round(tierOneCost * SupplyBudgetModel.COVERAGE_SCARCITY_SUPPLY_FACTOR)
            } else {
                tierOneCost
            }
            assertEquals(
                "L$level sermayesi kadronun kademe-1 maliyetinden sapti",
                expected, startingSupplyFor(level),
            )
        }

        // Kural GERCEKTEN calisiyor mu: kapsamasi KIT bir bolum, ayni kadroyu
        // tasiyan bol kapsamali bir bolumden PAHALI olmali.
        val sameRoster = (3..EconomyConfig.CAMPAIGN_LEVELS).groupBy {
            SupplyBudgetModel.DESIGNED_ROSTER_SIZE[it - 1] to
                GameConfigCampaignFacts.unlockedTowersInOrder(it).size
        }
        val demonstrated = sameRoster.values.any { group ->
            val scarce = group.filter { GameConfigCampaignFacts.hasScarceCoverage(it) }
            val roomy = group.filter { !GameConfigCampaignFacts.hasScarceCoverage(it) }
            scarce.isNotEmpty() && roomy.isNotEmpty() &&
                startingSupplyFor(scarce.first()) > startingSupplyFor(roomy.first())
        }
        assertTrue(
            "ayni kadroyu tasiyan kit/bol kapsamali bir bolum cifti bulunamadi ya da " +
                "kit olan pahali degil — kapsama primi olcum edilemiyor",
            demonstrated,
        )
    }

    /**
     * MODEL ILE TABLO AYNI SAYIYI SOYLEMELI.
     * `GameConfig.CAMPAIGN[L].startingSupply` motorun gercekten kullandigi deger;
     * model onu KOPYALAMAZ, kendi kuralindan uretir. Ikisi ayrismamali.
     */
    @Test
    fun gameConfigStartingSupplyMatchesTheBudgetModelForAllFiftyFiveLevels() {
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            assertEquals(
                "L$level baslangic Tedariki GameConfig ile model arasinda ayristi",
                GameConfig.levelSpec(level).startingSupply,
                startingSupplyFor(level),
            )
        }
    }

    /**
     * ONCEKI HALI (`recommendedBudgetTableMatchesEconomySpec`) 454/556/654/950/877/1188
     * bekliyordu. O sayilar Faz 10'un dalga tablosundan turetilmisti ve kule ajani
     * dalgalari sikilastirinca **sessizce yanlis** oldular — ama test yesil kalmaya
     * devam etti, cunku model de ayni bayat diziyi okuyordu. Iki taraf ayni yanlisi
     * paylastigi icin hata ancak elle olculdugunde goruldu.
     *
     * Faz 10.1'de model canli dalga tablosunu okuyor; bu yuzden buradaki sayilar
     * BUGUNKU olcumdur ve dalga tablosu degistiginde bu test **kirilmalidir**.
     * Kirildiginda yapilacak sey esigi guncellemek degil, SPI'ye bakmaktir.
     */
    @Test
    fun budgetTableMatchesTodaysMeasuredWaveTable() {
        // Yeniden olculdu: bolumler 5-7 dalgaya indi (gelir dustu) ve sermaye
        // kadrodan turetilir oldu (L3+ arttı). Net etki asagida.
        val expected = mapOf(
            // L4: 921 -> 1029. Catallanma esigi 9 -> 3 indi, L4 artik iki kollu ve
            // kapsama telafisi (x1,5) devreye girdi: baslangic 215 -> 323.
            1 to 502, 2 to 596, 3 to 719, 4 to 1029,
            5 to 929, 6 to 1078, 7 to 1172, 8 to 1131,
        )
        expected.forEach { (level, budget) ->
            assertEquals("L$level butce", budget, SupplyBudgetModel.supplyBudget(level))
        }
    }

    @Test
    fun everyModelledLevelLandsInsideTheTargetPressureBand() {
        // SERT KURAL: hedef bant 1,5..2,6. Disina cikan bolum tasarim hatasidir.
        //
        // ISTISNA KALDIRILDI. Eskiden L9..L22 bu banttan MUAFTI
        // (`BAND_EXEMPT_LEVELS`), cunku o bolumler 10-18 dalga uzunlugundaydi ve
        // butce tahtanin kapasitesini asiyordu (L22 SPI 5,37 — oyuncu butun
        // pad'leri doldurup hepsini kademe 3'e cikarsa bile parayi bitiremiyordu).
        // Tek ritme gecisle birlikte muafiyetin sebebi ortadan kalkti; artik
        // 55 bolumun 55'i banda giriyor (olculen aralik 1,54..2,52).
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val spi = SupplyBudgetModel.supplyPressureIndex(level)
            assertTrue(
                "L$level SPI %.2f, hedef bant %.1f..%.1f".format(
                    spi, SupplyBudgetModel.SPI_TARGET_MIN, SupplyBudgetModel.SPI_TARGET_MAX,
                ),
                spi >= SupplyBudgetModel.SPI_TARGET_MIN && spi <= SupplyBudgetModel.SPI_TARGET_MAX,
            )
        }
    }

    /**
     * L23..L55 SPI'si **tam 2,00** — cunku dalgalar ondan TURETILDI.
     *
     * CAMPAIGN_55.md 8.1 bandi bir kontrol degil bir URETIM KURALI yapiyor:
     * `butce = 2,00 x I(L)` -> `hedef oldurme geliri` -> dalga kompozisyonu.
     * Bu test zincirin kapali oldugunu kanitlar: dalga tablosunun urettigi gelir
     * ile modelin bekledigi gelir BIREBIR ayni olmali. Bir kurus sapma,
     * kompozisyon ureticisinin motorun odul aritmetiginden ayristigi anlamina
     * gelir (bu proje bu hatayi iki kez yasadi).
     */
    @Test
    fun everyGeneratedLevelSitsExactlyAtTheMiddleOfTheBand() {
        for (level in 23..EconomyConfig.CAMPAIGN_LEVELS) {
            assertEquals(
                "L$level: dalga tablosunun urettigi oldurme geliri hedeften sapti",
                WaveDefinitions.targetKillSupply(level),
                SupplyBudgetModel.waveKillSupply(level),
            )
            assertEquals(
                "L$level SPI tam 2,00 olmali",
                2.00, SupplyBudgetModel.supplyPressureIndex(level), 0.005,
            )
            assertEquals(
                "L$level: I(L) uretici ile model arasinda ayristi",
                WaveDefinitions.designedLoadoutCost(level),
                SupplyBudgetModel.designedLoadoutCost(level),
            )
        }
    }

    /**
     * BANT KILIDI — bandin icinde olmak yetmez, NEREDE oldugu da kilitli.
     *
     * Sebep: Faz 10'da SPI bandin ustune cikti ve bunu **hicbir test yakalamadi**,
     * cunku bant testi bayat bir diziyi okuyordu. Bant testi tek basina yeterli
     * degil; bir sonraki dalga degisikliginin bolum bolum ne yaptigini gormek
     * istiyoruz. Bu test kirildiginda mesaj dogrudan yeni SPI'yi yazar.
     *
     * Bu sayilari guncellemek MESRU bir islemdir — esigi gevsetmek degildir —
     * yeter ki [everyModelledLevelLandsInsideTheTargetPressureBand] yesil kalsin.
     */
    @Test
    fun theSupplyPressureIndexIsLockedLevelByLevel() {
        // YENIDEN OLCULDU (tek ritim + kadrodan turetilen sermaye).
        // Once: 2,28 / 1,85 / 1,88 / 2,03 / 1,70 / 2,22 / 1,62 / 1,76.
        val expected = mapOf(
            // L4: 2,12 -> 2,37. Sebep yukaridaki butce satiri; bant 1,5-2,6 KORUNDU.
            1 to 2.01, 2 to 1.59, 3 to 1.65, 4 to 2.37,
            5 to 1.88, 6 to 2.18, 7 to 1.62, 8 to 1.56,
        )
        expected.forEach { (level, spi) ->
            assertEquals(
                "L$level SPI kaydi (butce ${SupplyBudgetModel.supplyBudget(level)}, " +
                    "kadro ${SupplyBudgetModel.designedRoster(level)} = " +
                    "${SupplyBudgetModel.designedLoadoutCost(level)})",
                spi,
                SupplyBudgetModel.supplyPressureIndex(level),
                0.02,
            )
        }
    }

    // =================================================================================
    // 2b. BAYATLAMA KALKANI — model dalga/kule tablosunu KOPYALAMIYOR
    // =================================================================================

    /**
     * Bu proje ayni hatayi iki kez yapti: `WaveMetrics.AEHP` ve
     * `SupplyBudgetModel.TIGHTENED_WAVE_KILL_SUPPLY` elle yazilmisti, altindaki karar
     * degisti, tablolar kaldi. Asagidaki uc test ucuncusunu yapisal olarak engeller.
     */
    @Test
    fun killIncomeIsRecomputedFromTheLiveWaveTable() {
        // Testin kendisi toplami BAGIMSIZ olarak yeniden hesaplar. Model bir yerde
        // onceden hesaplanmis toplam saklıyorsa bu test onu yakalar.
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            val expected = WaveDefinitions.wavesFor(level).sumOf { wave ->
                wave.spawns.sumOf { spawn ->
                    GameConfig.ENEMY_SPECS.getValue(spawn.enemyType).rewardGold
                }
            }
            val actMul = GameConfig.actRewardMultiplier(GameConfig.levelSpec(level).act)
            if (actMul == 1f) {
                assertEquals(
                    "L$level oldurme geliri canli dalga tablosuyla uyusmuyor",
                    expected,
                    SupplyBudgetModel.waveKillSupply(level),
                )
            }
        }
        assertEquals(
            "L1 dalga sayisi canli tablodan gelmiyor",
            WaveDefinitions.wavesFor(1).size,
            SupplyBudgetModel.waveCount(1),
        )
    }

    @Test
    fun killIncomeFollowsAChangedWaveTable() {
        // Dalga tablosu degistiginde gelirin GERCEKTEN degistigini kanitlar: sahte
        // bir tabloda dusman sayisi yarilanirsa gelir de yarilanmali.
        val halved = object : CampaignFacts by GameConfigCampaignFacts {
            override fun enemyCounts(level: Int): Map<String, Int> =
                GameConfigCampaignFacts.enemyCounts(level).mapValues { (_, n) -> n / 2 }
        }
        val full = SupplyBudgetModel.waveKillSupply(1)
        val half = SupplyBudgetModel.waveKillSupply(1, halved)
        assertEquals("gelir dalga tablosunu takip etmiyor", full / 2.0, half.toDouble(), 2.0)
        assertTrue("model bir ara toplam sakliyor olmali", half < full)
    }

    @Test
    fun designedLoadoutIsPricedFromLiveTowerSpecs() {
        // Bolen de bayatlamamali: kule fiyatlari degisirse tasarlanan kadro maliyeti
        // kendiliginden takip etmeli (Faz 10'da CANNON 90->95, SLOW 80->100 oldu ve
        // elle yazilmis bolen bunu takip etmiyordu).
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val roster = SupplyBudgetModel.designedRoster(level)
            val tierThree = SupplyBudgetModel.DESIGNED_TIER_THREE_COUNT[level - 1]
            val expected = roster.sumOf { name ->
                val spec = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.valueOf(name))
                spec.buildCost + spec.level2UpgradeCost
            } + roster.take(tierThree).sumOf { name ->
                // Kademe 3 L12'de BEDAVA acilir ama yukseltmesi Tedarik ister;
                // saymamak gec bolumlerde bolen tarafini ~%40 eksik olcerdi.
                GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.valueOf(name))
                    .upgradeCostFrom(2) ?: 0
            }
            assertEquals(
                "L$level tasarlanan kadro maliyeti canli TOWER_SPECS ile uyusmuyor",
                expected,
                SupplyBudgetModel.designedLoadoutCost(level),
            )
        }

        val pricier = object : CampaignFacts by GameConfigCampaignFacts {
            override val towerBuildCost: Map<String, Int> =
                GameConfigCampaignFacts.towerBuildCost.mapValues { (_, c) -> c * 2 }
        }
        assertTrue(
            "kule fiyati iki katina cikti, bolen degismedi — bolen kopyalanmis",
            SupplyBudgetModel.designedLoadoutCost(1, pricier) >
                SupplyBudgetModel.designedLoadoutCost(1),
        )
    }

    @Test
    fun theDesignedRosterOnlyEverContainsUnlockedTowers() {
        // "Oyuncunun sahip olmadigi mekanik zorunlu basari kosulu olamaz" kuralinin
        // ekonomi tarafi: tasarlanan kadro o bolumde kurulamayan bir kule iceremez,
        // yoksa SPI'nin boleni oyuncunun ERISEMEDIGI bir savunmayi fiyatlar.
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            SupplyBudgetModel.designedRoster(level).forEach { name ->
                val spec = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.valueOf(name))
                assertTrue(
                    "L$level kadrosunda $name var ama L${spec.unlockedAtLevel}'de aciliyor",
                    spec.unlockedAtLevel <= level,
                )
            }
        }
    }

    /**
     * KADRO GERIYE GITMEZ — ama "nefes" bolumleri bu kuralin ISTISNASIDIR.
     *
     * L1..L11'de kural katidir: ogretme rampasinda kadro her bolumde en az
     * onceki kadar olmali, yoksa oyuncu kurdugu savunmayi "sokuyor" gibi
     * hisseder.
     *
     * L12'den itibaren kadro `R` iki seye birden bagli: (a) o haritanin ACIK pad
     * sayisi (K-5: `acik >= R+2`) ve (b) bolumun nefes olup olmadigi. 11 farkli
     * harita 5 kez donuyor; harita 06'nin 7 acik pad'i ile harita 10'un 15'i
     * ayni kadroyu tasiyamaz. Bu yuzden kural WINDOW'a cevrildi: **3 bolumluk
     * kayan maksimum geriye gitmez.** Niyet ayni (genel gidis yukari), tasarimin
     * kasitli nefeslerini cezalandirmiyor.
     */
    @Test
    fun theDesignedRosterNeverShrinksAsTheCampaignProgresses() {
        for (level in 2..11) {
            assertTrue(
                "L$level tasarlanan kadrosu L${level - 1}'den ucuz — oyuncu kule sokmuyor",
                SupplyBudgetModel.designedLoadoutCost(level) >=
                    SupplyBudgetModel.designedLoadoutCost(level - 1),
            )
        }
        // PERDE ICINDE: 3 bolumluk kayan maksimum geriye gitmez.
        // PERDE SINIRINDA kural UYGULANMAZ — perde gecisi kampanyanin en buyuk
        // nefesidir (CAMPAIGN_55.md 4.2, DS -%33..-38): yeni biyom + dusuk
        // zorluk + yeni mekanik ayni bolumde bulusur. L44 (perde finali, kadro
        // 2.060) -> L45 (perde acilisi, 1.745) tam olarak bu tasarimdir.
        fun windowMax(level: Int, actStart: Int) =
            (maxOf(actStart, level - 2)..level).maxOf { SupplyBudgetModel.designedLoadoutCost(it) }
        for (act in 2..5) {
            val actStart = 11 * (act - 1) + 1
            for (level in actStart + 2..11 * act) {
                assertTrue(
                    "L$level: perde $act icinde 3 bolumluk kadro maksimumu " +
                        "(${windowMax(level, actStart)}) geriye gitti " +
                        "(onceki ${windowMax(level - 1, actStart)})",
                    windowMax(level, actStart) >= windowMax(level - 1, actStart),
                )
            }
        }
        // PERDELER ARASINDA: her perdenin kadro tavani bir oncekini asmali.
        val actCeilings = (1..5).map { act ->
            (11 * (act - 1) + 1..11 * act).maxOf { SupplyBudgetModel.designedLoadoutCost(it) }
        }
        for (i in 1 until actCeilings.size) {
            assertTrue(
                "perde ${i + 1} kadro tavani (${actCeilings[i]}) perde $i'den " +
                    "(${actCeilings[i - 1]}) dusuk — kampanya derinlesmiyor",
                actCeilings[i] > actCeilings[i - 1],
            )
        }
        // Act I tavani 850 -> 725: bolumler 5-7 dalgaya inince gelir yarilandi ve
        // bes kulelik bir Act I kadrosu SPI'yi bandin altina itiyordu (L7 1,41).
        // Act I artik dort kuleyle bitiyor; L7'de acilan Fuze (kademe-2 230) o
        // dort kulenin pahalilasmasini tek basina tasiyor.
        assertEquals(
            "perde kadro tavanlari",
            listOf(725, 1330, 1745, 2060, 2240), actCeilings,
        )
    }

    /**
     * BOLENDEN BAGIMSIZ BOLLUK OLCUMU.
     *
     * SPI'nin boleni Faz 10.1'de degisti; "bant asimini boleni buyuterek cozduk"
     * itirazina kapali bir olcut gerekiyor. Testcinin cumlesi zaten boyle bir olcut:
     * *"her dalga 1-3 kule finanse ediyor."* Bu test onu dogrudan olcer ve hicbir
     * tasarim niyetine bakmaz — yalnizca canli dalga tablosu ve canli kule fiyati.
     */
    @Test
    fun noSingleWaveFinancesAWholeUpgradedTower() {
        val tierTwoGatling = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN)
            .let { it.buildCost + it.level2UpgradeCost }
        assertEquals(125, tierTwoGatling)

        // TAVAN BOLUM UZUNLUGUYLA OLCEKLENIR — sabit 1,1 yalnizca ilk 8 bolum
        // icin dogruydu. Bir dalganin finanse ettigi kule sayisi, bolumun kac
        // dalgaya bolundugune ve o bolumun kadro buyuklugune baglidir: 6 dalgada
        // 7 kule + 6 kademe-3 kurmasi beklenen bir bolumde dalga basina 1,1 kule
        // ile hicbir sey kurulamazdi. Dogru degismez olcut **kadronun kendisi**:
        // tek bir dalga, o bolumun tasarlanan kadrosunun 1/3'unden fazlasini
        // finanse edemez — yani kadro EN AZ uc dalgada kurulur, "kur -> yukselt
        // -> genislet" arki bolume sigar.
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val perWave = SupplyBudgetModel.waveKillSupply(level).toDouble() /
                SupplyBudgetModel.waveCount(level)
            val towersPerWave = perWave / tierTwoGatling
            val rosterShare = perWave / SupplyBudgetModel.designedLoadoutCost(level)
            assertTrue(
                "L$level: bir dalga tasarlanan kadronun %%%.0f'ini finanse ediyor "
                    .format(rosterShare * 100) +
                    "(%.2f kademe-2 Gatling) — tavan %%33".format(towersPerWave),
                rosterShare <= 0.34,
            )
            if (level <= 8) {
                assertTrue(
                    "L$level: bir dalga %.2f kademe-2 Gatling finanse ediyor — 1,1'i gecerse "
                        .format(towersPerWave) + "eski bolluk geri gelir",
                    towersPerWave <= 1.1,
                )
            }
            // Faz 10 oncesiyle karsilastirma: kesinti gercekten calisti mi.
            val legacyPerWave = SupplyBudgetModel.legacyWaveKillSupply(level).toDouble() /
                SupplyBudgetModel.waveCount(level) / tierTwoGatling
            assertTrue(
                "L$level: dalga basi kule finansmani %.2f -> %.2f, iki katindan az "
                    .format(legacyPerWave, towersPerWave) + "iyilesme yetersiz",
                legacyPerWave >= towersPerWave * 2,
            )
        }
    }

    @Test
    fun tighteningIsAtLeastATwoFoldReductionOnEveryEarlyLevel() {
        for (level in 1..6) {
            val before = SupplyBudgetModel.legacySupplyBudget(level)
            val after = SupplyBudgetModel.supplyBudget(level)
            assertTrue(
                "L$level: $before -> $after, iki katindan az sikilasma yetersiz",
                after * 2 <= before,
            )
        }
    }

    @Test
    fun budgetStillCoversTheDesignedLoadoutOnEveryLevel() {
        // SOFT-LOCK KALKANI: sikilastirma bolumu gecilemez YAPMAMALI. Butce, tasarlanan
        // kadronun tamamini HER bolumde karsilamali (uzerine de pay kalmali).
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            assertTrue(
                "L$level: butce ${SupplyBudgetModel.supplyBudget(level)} < " +
                    "tasarlanan kadro ${SupplyBudgetModel.designedLoadoutCost(level)}",
                SupplyBudgetModel.supplyBudget(level) > SupplyBudgetModel.designedLoadoutCost(level),
            )
        }
    }

    @Test
    fun openingSupplyBuysAtLeastOneTowerAndAtMostTwoOnEveryEarlyLevel() {
        // Testcinin "her kurusu saysin" istegi ACILISTA baslar.
        //
        // ESKI IFADE: "acilista en fazla 2 kule". O kural, sermaye duz bir tablo
        // (80/90/110/120/140/150) iken dogruydu. Sermaye artik KADRODAN turer:
        // 5-7 dalgalik bir bolumde oyuncu kadrosunu kurabilmeli, yoksa acilis
        // dalgasinda kaybeder (olculdu). Yani "kac kule" artik yanlis soru.
        //
        // DOGRU SORU: acilista savunma KURULABILIR ama YUKSELTILEMEZ. Sermaye
        // kadronun kademe-2 maliyetini (I(L)) asarsa oyuncu bolume tam guclu
        // baslar ve dalga geliri anlamsizlasir.
        val gatling = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN).buildCost
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            val opening = startingSupplyFor(level)
            assertTrue("L$level acilista tek kule bile kurulamiyor", opening >= gatling)
            assertTrue(
                "L$level acilis sermayesi ($opening) tasarlanan kadronun kademe-2 " +
                    "maliyetini (${SupplyBudgetModel.designedLoadoutCost(level)}) asiyor — " +
                    "bolum tam guclu basliyor, dalga geliri karar olmaktan cikiyor",
                opening < SupplyBudgetModel.designedLoadoutCost(level),
            )
        }
        // OGRETME DILIMI (L1-L2) ayrica katidir: en fazla IKI Gatling.
        for (level in 1..2) {
            assertTrue(
                "L$level acilista ${startingSupplyFor(level) / gatling} kule — " +
                    "ogretici dilimde en fazla 2",
                startingSupplyFor(level) / gatling <= 2,
            )
        }
    }

    /**
     * SERMAYE TABANI GERIYE GITMEZ — ama CATALLANMA PRIMI monotonlugu bozar.
     *
     * Once bu test ham `startingSupplyFor` uzerinde monotonluk ariyordu. Sermaye
     * artik iki carpandan olusuyor: **kadro tabani** (monoton) ve **catallanan
     * harita primi x1,5** (haritaya bagli, dolayisiyla dalgali). Ornek: L12
     * harita 11'de catallidir (555), L13 harita 10'da degildir (370) — bu bir
     * gerileme degil, o bolumde iki cephe kurulmadigi icin gerekmeyen paranin
     * verilmemesidir. Prim verilmeye devam edilseydi catalsiz bolumler
     * kolaylasirdi.
     *
     * Kilit bu yuzden TABANA uygulanir; prim ayrica dogrulanir.
     */
    @Test
    fun theStartingSupplyBaselineNeverDecreasesAndTheForkPremiumIsAlwaysPositive() {
        fun baseline(level: Int): Int {
            val order = GameConfigCampaignFacts.unlockedTowersInOrder(level)
            val roster = SupplyBudgetModel.DESIGNED_ROSTER_SIZE[level - 1]
            return (0 until roster).sumOf {
                GameConfigCampaignFacts.towerBuildCost.getValue(order[it % order.size])
            }
        }
        // OGRETME RAMPASI (L3..L22) katidir: taban geriye gitmez.
        for (level in 4..22) {
            assertTrue(
                "L$level sermaye tabani (${baseline(level)}) L${level - 1}'den " +
                    "(${baseline(level - 1)}) dusuk",
                baseline(level) >= baseline(level - 1),
            )
        }
        // L23+ icin kural WINDOW'a cevrilir — `designedLoadoutCost` ile ayni
        // gerekce: 11 harita bes kez donuyor, harita 06'nin 7 acik pad'i ile
        // harita 10'un 15'i ayni kadroyu tasiyamaz ve NEFES bolumleri kadroyu
        // bilerek kuculttur. 3 bolumluk kayan maksimum geriye gitmemeli.
        fun windowMax(level: Int, actStart: Int) =
            (maxOf(actStart, level - 2)..level).maxOf { baseline(it) }
        for (act in 3..5) {
            val actStart = 11 * (act - 1) + 1
            for (level in actStart + 2..11 * act) {
                assertTrue(
                    "L$level sermaye tabaninin 3 bolumluk maksimumu geriye gitti",
                    windowMax(level, actStart) >= windowMax(level - 1, actStart),
                )
            }
        }
        val scarce = (1..EconomyConfig.CAMPAIGN_LEVELS)
            .filter { GameConfigCampaignFacts.hasScarceCoverage(it) }
        assertTrue("kapsamasi kit bolum bulunamadi — kural olu", scarce.isNotEmpty())
        // L1/L2 ogretici sabitleridir ve kurala girmez (bkz. EARLY_STARTING_SUPPLY).
        scarce.filter { it > 2 }.forEach { level ->
            assertTrue(
                "L$level kapsamasi kit ama sermayesi (${startingSupplyFor(level)}) " +
                    "tabandan (${baseline(level)}) buyuk degil",
                startingSupplyFor(level) > baseline(level),
            )
        }
        // Kural iki koldan da BESLENMELI: hem catallanan hem dar-tahta ornegi
        // bulunmali, yoksa ikinci kol olu kod demektir.
        assertTrue(
            "dar tahta kolu hicbir bolumde tetiklenmiyor",
            scarce.any { !GameConfig.usesAlternateRoutes(it) },
        )
    }

    @Test
    fun waveClearBonusIsNoLongerTheHiddenBulkOfTheBudget() {
        // Eski ikramiye 35 ve bolum uzunluguyla sessizce buyuyor. Tek ritimden
        // sonra L1 5 dalga (35x4 = 140), L8 7 dalga (35x6 = 210) — sayilar
        // dalga sayisindan TURETILIR, elle yazilmaz.
        assertEquals(
            35 * (SupplyBudgetModel.waveCount(1) - 1),
            SupplyBudgetModel.waveClearBonusTotal(1, SupplyBudgetModel.LEGACY_WAVE_CLEAR_BONUS),
        )
        assertEquals(
            35 * (SupplyBudgetModel.waveCount(8) - 1),
            SupplyBudgetModel.waveClearBonusTotal(8, SupplyBudgetModel.LEGACY_WAVE_CLEAR_BONUS),
        )
        // Onerilen 18 ile ikramiye hicbir bolumde butcenin %25'ini gecmez.
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val share = 100 * SupplyBudgetModel.waveClearBonusTotal(level) /
                SupplyBudgetModel.supplyBudget(level)
            assertTrue("L$level dalga ikramiyesi butcenin %%$share'i", share <= 25)
        }
    }

    // =================================================================================
    // 3. META YUKSELTME SIKILASTIRMAYI KIRMAZ (ama odullendirir)
    // =================================================================================

    @Test
    fun metaSupplyUpgradeRewardsInvestmentWithoutRestoringTheOldAbundance() {
        // Maksli STARTING_SUPPLY (+150) L1'de 80 -> 230 yapar. Bu kasitli bir oduldur;
        // ama L1 butcesi hala eski 1.177'nin cok altinda kalmali.
        val metaBonus = MetaUpgrades(startingSupplyRank = 2).startingSupply -
            EconomyConfig.BASE_STARTING_SUPPLY
        assertEquals(150, metaBonus)

        for (level in 1..6) {
            val maxedBudget = SupplyBudgetModel.supplyBudget(level) + metaBonus
            assertTrue(
                "L$level: maksli meta ile butce $maxedBudget, eski bol butce " +
                    "${SupplyBudgetModel.legacySupplyBudget(level)} — bolluk geri geldi",
                maxedBudget < SupplyBudgetModel.legacySupplyBudget(level),
            )
        }
    }

    @Test
    fun metaBaselineStaysCompatibleWithTheGameConfigContract() {
        // META TABANI ile BOLUM SERMAYESI ARTIK AYRI SEYLER.
        //
        // Once burada `startingSupplyFor(L7) == INITIAL_GOLD` yaziyordu, yani
        // meta hattinin tabani (150) ile L7'nin bolum sermayesi ayni sayiya
        // sabitlenmisti. Sermaye kadrodan turetilince bu tesaduf bozuldu — ama
        // KIRILAN sey bir sozlesme degil, iki farkli seyin ayni sayi olmasiydi.
        // Motorun formulu `levelSpec.startingSupply + (meta - 150)` oldugu icin
        // gercek sozlesme sudur: **meta hattinin TABANI, yukseltme yapmamis
        // oyuncuya SIFIR bonus vermeli.**
        assertEquals(EconomyConfig.BASE_STARTING_SUPPLY, MetaUpgrades().startingSupply)
        assertEquals(GameConfig.INITIAL_GOLD, EconomyConfig.BASE_STARTING_SUPPLY)
        assertEquals(
            "yukseltme yapmamis oyuncunun meta bonusu sifir olmali",
            0, MetaUpgrades().startingSupply - EconomyConfig.BASE_STARTING_SUPPLY,
        )
        // ...ve bolum sermayesi meta tabanindan BAGIMSIZ olarak L7'de artik
        // ogretici degerlerin uzerinde (kadro kurulabilmeli).
        assertTrue(
            "L${EconomyConfig.FIRST_PAID_LEVEL} sermayesi ogretici seviyede kalmis",
            startingSupplyFor(EconomyConfig.FIRST_PAID_LEVEL) > startingSupplyFor(1),
        )
    }

    // =================================================================================
    // 4. COIN EKONOMISI ETKILENMEZ (enflasyon/deflasyon yok)
    // =================================================================================

    @Test
    fun tighteningTouchesOnlySupplyAndNeverCoinRewards() {
        // Tedarik ve Coin arasinda donusum YOK (GDD D.4): sikilastirma bolum odullerini,
        // kilit bedellerini ve agac fiyatlarini DEGISTIRMEZ.
        // (Agac 13.900 -> 19.600 oldu ama bunu Tedarik sikilastirmasi degil
        // ECONOMY_AUDIT_2 P0 yapti; bu test yalnizca sikilastirmanin coin'e
        // dokunmadigini kilitler.)
        assertEquals(23_105, EconomyConfig.TOTAL_LOCK_COST)
        assertEquals(19_600, EconomyConfig.TREE_TOTAL_COST)
        assertEquals(450, EconomyConfig.R1_COIN_BUDGET_PER_DAY)
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            assertEquals(140 + 30 * (level - 1), baseLevelReward(level))
        }
    }

    @Test
    fun everyLevelIsStillClearableWithoutAnyBoosterAfterTightening() {
        // PAY-TO-WIN KALKANI: sikilastirma "guclendirici almazsan gecemezsin" durumu
        // yaratmamali. Guclendiricisiz butce tasarlanan kadroyu karsiliyor mu?
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val withoutBoosters = SupplyBudgetModel.supplyBudget(level)
            assertTrue(
                "L$level guclendiricisiz butce kadroyu karsilamiyor",
                withoutBoosters >= SupplyBudgetModel.designedLoadoutCost(level),
            )
            // Hava Destegi Tedarik HARCAR; onu almak kadroyu imkansiz kilmamali.
            if (boosterUnlocked(BoosterType.AIR_SUPPORT, level)) {
                assertTrue(
                    "L$level: hava destegi alan oyuncu kadroyu kuramaz hale geliyor",
                    withoutBoosters - boosterPrice(BoosterType.AIR_SUPPORT, level) >=
                        SupplyBudgetModel.designedLoadoutCost(level) * 80 / 100,
                )
            }
        }
    }

    // =================================================================================
    // 5. DEVIR TAMAMLANDI — sozlesme kilidi
    // =================================================================================

    /**
     * Onceki hali (`gameConfigStillUsesTheLegacyFlatSupply`) bugunku gercegi
     * belgeliyordu: `LevelSpec.startingSupply` alani vardi ama hicbir bolumde
     * override edilmiyordu, 22 bolum de 150 ile basliyordu. **Kirilmasi ISTENEN**
     * bir testti ve gameplay ajani ECONOMY_SPEC 9 madde 1'i uygulayinca kirildi.
     *
     * Yerine gecen sey artik bir "yapilacaklar" notu degil, iki katmani birbirine
     * baglayan KILIT: model ile GameConfig ayrisirsa bolum secme ekrani bir butce
     * gosterip savas baska butceyle baslar.
     */
    @Test
    fun gameConfigStartingSupplyMatchesTheBudgetModel() {
        GameConfig.CAMPAIGN.forEach { spec ->
            assertEquals(
                "L${spec.levelId} baslangic Tedariki model ile uyusmuyor",
                startingSupplyFor(spec.levelId),
                spec.startingSupply,
            )
        }
        // Sermaye artik DUZ DEGIL: kurallilik kaniti (aksi halde yukaridaki
        // assert duz bir tabloda da gecerdi). L1/L2 ogretici sabitleri, L3+
        // kadronun kademe-1 maliyeti.
        val early = (1..6).map { GameConfig.levelSpec(it).startingSupply }
        // L4 = 323: kadro tabani 215 x kapsama telafisi 1,5 (bolum iki kollu).
        // L5-L6 tek kollu oldugu icin telafi almaz; monotonluk TABAN uzerinde
        // olculur (bkz. GameConfig.startingSupplyBaseFor).
        assertEquals("ilk alti bolum butcesi", listOf(80, 90, 215, 323, 255, 255), early)
        // L7'de Fuze Bataryasi aciliyor ve tasarlanan kadroya giriyor; sermaye
        // dort kulelik kademe-1 maliyetini (370) karsilar. Eskiden burada
        // "L7+ taban Tedarige (150) donmeli" yaziyordu — o taban, kadro bes
        // kuleye ve bolum 5-7 dalgaya inince acilis dalgasini gecilemez
        // yapiyordu (olcum: dalga izi).
        assertEquals(370, GameConfig.levelSpec(7).startingSupply)
    }
}
