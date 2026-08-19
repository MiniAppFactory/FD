package com.miniappfactory.frontlinedefender.progression

import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.SupplyBudgetModel
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VERI BUTUNLUGU — `GameConfig.CAMPAIGN` (bolum <-> harita eslemesi).
 *
 * Bu tablo "TEK BIR YERDE" durmak zorunda (GameConfig KDoc); motor, bolum
 * secme ekrani ve dalga tanimlari hepsi buradan okuyor.
 */
class CampaignTableTest {

    private val campaign get() = GameConfig.CAMPAIGN

    @Test
    fun campaignHasExactlyFiftyFiveLevels() {
        // CAMPAIGN_55.md K1: 5 perde x 11 bolum. 11 harita x 5 biyom eslemesinin
        // tek dogal bolunmesi ve harita tekrar araligini maksimize eden sayi.
        assertEquals(55, campaign.size)
        assertEquals(55, GameConfig.CAMPAIGN_LEVEL_COUNT)
        assertEquals(5, campaign.map { it.act }.distinct().size)
        campaign.groupBy { it.act }.forEach { (act, levels) ->
            assertEquals("perde $act 11 bolum olmali", 11, levels.size)
        }
    }

    @Test
    fun levelIdsAreUniqueContiguousAndInAscendingOrder() {
        val ids = campaign.map { it.levelId }
        assertEquals("levelId'ler tekil olmali", ids.size, ids.toSet().size)
        assertEquals((1..55).toList(), ids)
    }

    @Test
    fun everyMapIdIsWithinTheMeasuredGeometryRange() {
        campaign.forEach { spec ->
            assertTrue(
                "bolum ${spec.levelId} mapId=${spec.mapId} — 1..11 disinda",
                spec.mapId in 1..11
            )
        }
    }

    @Test
    fun everyMapIdResolvesToRealMeasuredGeometry() {
        campaign.forEach { spec ->
            val geo = LevelData.forMapId(spec.mapId)
            assertEquals(
                "bolum ${spec.levelId} mapId=${spec.mapId} geometri cozumlemesi yanlis haritaya gitti",
                spec.mapId, geo.levelId
            )
            assertTrue("bolum ${spec.levelId} haritasinda pad yok", geo.buildSpots.isNotEmpty())
            assertTrue("bolum ${spec.levelId} haritasinda rota yok", geo.waypoints.size >= 2)
        }
    }

    @Test
    fun everyLevelHasWaveDefinitions() {
        campaign.forEach { spec ->
            val waves = WaveDefinitions.wavesFor(spec.levelId)
            assertTrue("bolum ${spec.levelId} icin dalga yok", waves.isNotEmpty())
            assertEquals(
                "LevelSpec.waveCount ile WaveDefinitions uyusmuyor",
                waves.size, spec.waveCount
            )
        }
    }

    @Test
    fun allElevenMapsAreUsedByTheCampaign() {
        assertEquals(
            "11 olculmus haritanin hepsi kullanilmali",
            (1..11).toSet(), campaign.map { it.mapId }.toSet()
        )
    }

    @Test
    fun everyActWalksTheElevenMapsInItsDocumentedOrder() {
        // CAMPAIGN_55.md K4: Act II'nin ters sirasi, perde basina BIR SLOT
        // kaydirilarak. Kaydirmanin bedeli minimum tekrar araliginin 11 yerine
        // 10 olmasi; karsiligi dort perde finalinin dort FARKLI haritaya
        // dusmesi (L22 harita 01 · L33 harita 11 · L44 harita 10 · L55 harita 09).
        val expected = mapOf(
            1 to (1..11).toList(),
            2 to listOf(11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1),
            3 to listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 11),
            4 to listOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 11, 10),
            5 to listOf(8, 7, 6, 5, 4, 3, 2, 1, 11, 10, 9)
        )
        expected.forEach { (act, order) ->
            val levels = campaign.filter { it.act == act }
            assertEquals("perde $act 11 bolum olmali", 11, levels.size)
            assertEquals("perde $act harita sirasi", order, levels.map { it.mapId })
        }
    }

    /** Her perde her haritayi TAM BIR KEZ kullanir — 11 x 5 = 55 tekil gecis. */
    @Test
    fun everyActUsesEveryMapExactlyOnce() {
        (1..5).forEach { act ->
            val maps = campaign.filter { it.act == act }.map { it.mapId }
            assertEquals("perde $act her haritayi bir kez kullanmali", (1..11).toSet(), maps.toSet())
            assertEquals("perde $act'te harita tekrari var", 11, maps.size)
        }
    }

    /**
     * TEKRAR ARALIGI — L23'ten itibaren ayni harita en az 10 bolum arayla.
     *
     * L1..L22 icin bu kural GECERSIZ ve bu BILINEN bir kusur: ters sira icin
     * harita m'nin araligi `23-2m`, yani harita 11 = **1** (L11 -> L12 arka
     * arkaya). Yayinlanmis icerik tek bir bitisiklik icin degistirilmez;
     * CAMPAIGN_55.md 3.3 bunun yerine uc telafi tanimliyor (arketip zitligi,
     * krater farki, L12'nin kisitlayicisi).
     */
    @Test
    fun fromLevelTwentyThreeOnwardNoMapRepeatsWithinTenLevels() {
        val tooClose = mutableListOf<String>()
        for (level in 23..55) {
            val mapId = GameConfig.levelSpec(level).mapId
            for (back in 1..9) {
                val other = level - back
                if (other >= 1 && GameConfig.levelSpec(other).mapId == mapId) {
                    tooClose += "L$level ve L$other ayni harita ($mapId), aralik $back"
                }
            }
        }
        assertTrue(
            "HARITA TEKRAR ARALIGI 10'un altina dustu:\n" + tooClose.joinToString("\n"),
            tooClose.isEmpty()
        )
    }

    @Test
    fun actNumbersRunFromOneToFiveAndAreElevenLevelBlocks() {
        campaign.forEach { spec ->
            assertTrue("bolum ${spec.levelId} act=${spec.act} — 1..5 olmali", spec.act in 1..5)
            assertEquals(
                "bolum ${spec.levelId}: perde 11'lik bloklar olmali (CAMPAIGN_55.md K1)",
                (spec.levelId - 1) / 11 + 1, spec.act
            )
        }
    }

    /**
     * Her perde TEK biyom (CAMPAIGN_55.md K3): biyomun isi tekrari maskelemek
     * degil, "yeni perde basladi" sinyali vermek. Ardisik perdeler arasindaki
     * gorsel mesafe BIOME_VARIANTS.md 3'un olculmus parametrelerinden secildi;
     * desert -> autumn en zayif gecis oldugu icin Act V ayrica DUSK perdesi alir.
     */
    @Test
    fun eachActHasExactlyOneBiomeAndTheDocumentedOverlay() {
        val expectedBiome = mapOf(
            1 to GameConfig.Biome.TEMPERATE,
            2 to GameConfig.Biome.NIGHT,
            3 to GameConfig.Biome.WINTER,
            4 to GameConfig.Biome.DESERT,
            5 to GameConfig.Biome.AUTUMN
        )
        val expectedOverlay = mapOf(
            1 to GameConfig.MapOverlay.NONE,
            2 to GameConfig.MapOverlay.NIGHT,
            3 to GameConfig.MapOverlay.NONE,
            4 to GameConfig.MapOverlay.NONE,
            5 to GameConfig.MapOverlay.DUSK
        )
        campaign.forEach { spec ->
            assertEquals("bolum ${spec.levelId} overlay", expectedOverlay[spec.act], spec.overlay)
            assertEquals("bolum ${spec.levelId} biome", expectedBiome[spec.act], spec.biome)
        }
        // Ardisik iki perde asla ayni biyomu kullanamaz.
        val perAct = (1..5).map { act -> campaign.first { it.act == act }.biome }
        assertEquals("5 perde -> 5 farkli biyom", 5, perAct.toSet().size)
    }

    // ------------------------------------------------------ kilit / deployment

    @Test
    fun theFirstSixLevelsAreFreeToEnter() {
        // Oyuncu ilk oturumda coin biriktirmeden 6 bolum oynayabilmeli.
        (1..6).forEach { lv ->
            assertEquals(
                "bolum $lv bastan acik olmali (deploymentCost 0)",
                0, GameConfig.levelSpec(lv).deploymentCost
            )
        }
    }

    /**
     * KILIT FORMULU L23+ — `350 + 15(L-22)`.
     *
     * Eski uzanti `350 + 40(L-22)` GDD C.3'un "odul >= 2,5 x sonraki kilit"
     * kuralini kiriyordu: odul adimi 30, kilit adimi 40 olunca kilit odulu
     * gecmeye basliyor ve L55'te oran 1,04'e dusuyordu.
     */
    @Test
    fun lateActDeploymentCostFollowsTheFifteenStepFormula() {
        for (lv in 23..55) {
            assertEquals(
                "bolum $lv kilit ucreti 350 + 15 x (L-22) olmali",
                350 + 15 * (lv - 22), GameConfig.levelSpec(lv).deploymentCost
            )
        }
        assertEquals(365, GameConfig.levelSpec(23).deploymentCost)
        assertEquals(515, GameConfig.levelSpec(33).deploymentCost)
        assertEquals(680, GameConfig.levelSpec(44).deploymentCost)
        assertEquals(845, GameConfig.levelSpec(55).deploymentCost)
    }

    /**
     * ODUL / SONRAKI KILIT ORANI.
     *
     * GDD C.3 "en az 2,5 kat" diyor. **Bu kural 22 bolumluk yayinlanmis tabloda
     * ZATEN kirik:** L20 -> L21 oran 2,37 · L21 -> L22 oran 2,11. Sebep yapisal
     * — odul adimi 30, kilit adimi Act II sonunda 30 ve 50; oran kacinilmaz
     * olarak daralir. Bu bir regresyon degil, kayit altina alinmasi gereken bir
     * gercek (orkestratore raporlandi).
     *
     * Yeni L23+ formulu (`350 + 15(L-22)`) orani **2,05'in altina dusurmuyor**;
     * eski `350 + 40(L-22)` uzantisi ise L55'te 1,04 uretiyordu, yani oyuncu
     * son bolumu tek turda ACAMAZDI. Test korudugumuz seyi olcuyor: oran
     * hicbir yerde 2,0'in altina inmemeli.
     */
    @Test
    fun everyLevelRewardComfortablyCoversTheNextLevelsLock() {
        var worst = Double.MAX_VALUE
        var worstAt = 0
        for (lv in 1 until 55) {
            val reward = EconomyConfig.REWARD_BASE + EconomyConfig.REWARD_STEP * (lv - 1)
            val nextLock = GameConfig.levelSpec(lv + 1).deploymentCost
            if (nextLock == 0) continue
            val ratio = reward.toDouble() / nextLock
            if (ratio < worst) { worst = ratio; worstAt = lv }
            assertTrue(
                "L$lv odulu $reward, L${lv + 1} kilidi $nextLock — oran " +
                    "${"%.2f".format(ratio)} < 2,0",
                ratio >= 2.0
            )
        }
        assertEquals(
            "en dar odul/kilit orani degisti (L$worstAt)",
            2.05, worst, 0.02
        )
    }

    @Test
    fun deploymentCostNeverDecreasesAsTheCampaignProgresses() {
        for (lv in 2..55) {
            val prev = GameConfig.levelSpec(lv - 1).deploymentCost
            val curr = GameConfig.levelSpec(lv).deploymentCost
            assertTrue(
                "bolum $lv kilit ucreti ($curr) onceki bolumden ($prev) dusuk",
                curr >= prev
            )
        }
    }

    @Test
    fun deploymentCostsAreNeverNegative() {
        campaign.forEach { spec ->
            assertTrue("bolum ${spec.levelId} negatif kilit ucreti", spec.deploymentCost >= 0)
        }
    }

    @Test
    fun everyLevelStartsWithAPositiveSupplyAndBaseLives() {
        campaign.forEach { spec ->
            assertTrue("bolum ${spec.levelId} baslangic tedariki pozitif olmali", spec.startingSupply > 0)
            assertTrue("bolum ${spec.levelId} us cani pozitif olmali", spec.maxBaseLives > 0)
        }
    }

    // ------------------------------------------------------- pad kisiti (Act II)

    /**
     * PAD GIZLEMENIN GEREKCESI ACT'E GORE FARKLIDIR.
     *
     * Eskiden kural "yalnizca Act II pad kapatir" idi. Bu kural, Act I'de
     * MENZIL DISI pad'lerin gorunur kalmasini zorunlu tutuyordu: bolum 1'de 10
     * pad'in 5'i aktif rotaya 194-444 ref-px uzaktaydi, Gatling menzili 150 ve
     * baslangic tedariki TEK kule. Yani kural, oyuncunun tum parasini hicbir
     * seye ates etmeyen bir kuleye yatirmasini KORUYORDU
     * (docs/PAD_COVERAGE_REPORT.md).
     *
     * Revize kural:
     *  · **Act I** — pad kapatmak SERBEST degil, tek mesru sebep menzil disi
     *    olmaktir. "Kapatilanlarin hepsi gercekten menzil disi mi" sorusu
     *    `PadReachabilityPerLevelTest.actOneHidesOnlyPadsThatAreActuallyOutOfRange`
     *    icinde zorlanir; burada yalnizca Act I'in KRATER bulmacasi kurmadigi,
     *    yani kapatmanin istisna oldugu kaydediliyor.
     *  · **Act II** — krater kisiti her bolumde ZORUNLU (degismedi).
     */
    @Test
    fun actOneHidesOnlyOutOfRangePadsWhileActTwoAlwaysHasTheCraterConstraint() {
        campaign.forEach { spec ->
            if (spec.act == 1) {
                val total = LevelData.forMapId(spec.mapId).buildSpots.size
                assertTrue(
                    "bolum ${spec.levelId} Act I ama pad'lerin yarisindan fazlasi kapali " +
                        "(${spec.disabledPadIds.size}/$total) — Act I'de krater bulmacasi YOK, " +
                        "yalnizca menzil disi pad gizlenir",
                    spec.disabledPadIds.size * 2 <= total
                )
            } else {
                assertTrue(
                    "bolum ${spec.levelId} Act II ama pad kisiti yok — krater kisiti eksik",
                    spec.disabledPadIds.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun everyDisabledPadIdActuallyExistsOnThatLevelsMap() {
        campaign.forEach { spec ->
            val realIds = LevelData.forMapId(spec.mapId).buildSpots.map { it.id }.toSet()
            spec.disabledPadIds.forEach { id ->
                assertTrue(
                    "bolum ${spec.levelId} (harita ${spec.mapId}) var olmayan pad $id'yi " +
                        "devre disi birakiyor — gecerli id'ler: $realIds",
                    id in realIds
                )
            }
        }
    }

    @Test
    fun disabledPadListsContainNoDuplicates() {
        campaign.forEach { spec ->
            assertEquals(
                "bolum ${spec.levelId} pad kisit listesinde tekrar var: ${spec.disabledPadIds}",
                spec.disabledPadIds.size, spec.disabledPadIds.toSet().size
            )
        }
    }

    /** LEVEL_DESIGN.md F.3 D1: pad'lerin %25-40'i devre disi. */
    @Test
    fun disabledPadFractionStaysWithinTheDesignBand() {
        campaign.filter { it.act == 2 }.forEach { spec ->
            val total = LevelData.forMapId(spec.mapId).buildSpots.size
            val fraction = spec.disabledPadIds.size.toFloat() / total
            assertTrue(
                "bolum ${spec.levelId}: ${spec.disabledPadIds.size}/$total pad kapali " +
                    "(%${"%.0f".format(fraction * 100)}) — %25-40 bandinin disinda",
                fraction in 0.24f..0.41f
            )
        }
    }

    /**
     * K-7 (CAMPAIGN_55.md 7.4) — Act III+ icin devre disi pad orani <= %42.
     *
     * Ust sinir Act II'nin %40'indan biraz genis: harita 08'in **olu** pad orani
     * tek basina %27 ve olu pad'ler gizlenmek ZORUNDA (K-6), yani eski %25-40
     * bandi bazi haritalarda tabanla cakisiyordu. Alt sinir YOK: gec perdelerde
     * tahtanin acilmasi (Act V'te 11-15 acik pad) tasarimin kendisi.
     */
    @Test
    fun lateActDisabledPadFractionStaysUnderTheCap() {
        campaign.filter { it.act >= 3 }.forEach { spec ->
            val total = LevelData.forMapId(spec.mapId).buildSpots.size
            val fraction = spec.disabledPadIds.size.toFloat() / total
            assertTrue(
                "bolum ${spec.levelId}: ${spec.disabledPadIds.size}/$total pad kapali " +
                    "(%${"%.0f".format(fraction * 100)}) — K-7 tavani %42",
                fraction <= 0.42f
            )
        }
    }

    /**
     * ACIK PAD SAYISI = CAMPAIGN_55.md 9. tablosunun `acik` kolonu.
     *
     * Bu kolon tasarimin OMURGASI: K-5 (`acik >= R + 2`) ve kadro buyuklugu R
     * ondan turetiliyor, yani bir bolumun acik pad sayisi degistiginde o bolumun
     * butcesi ve dalga kompozisyonu da sessizce yanlis olur. Tablo bu yuzden
     * burada BIREBIR pinlenmistir.
     */
    @Test
    fun openPadCountsMatchTheDesignTable() {
        val expected = mapOf(
            23 to 12, 24 to 9, 25 to 8, 26 to 8, 27 to 7, 28 to 9,
            // HARITA 11'IN GEC GECISLERI TAM ACIK (L33 ve L53: 8/9 -> 10).
            //
            // Harita 11'in yalnizca 10 pad'i var ve IKI KOLLU (catallanan).
            // Kapsama iki kola bolununce 8-9 pad yetmiyordu: L33 (perde finali +
            // boss) cozulebilirlik simulasyonunda hic gecilemiyordu, L53 ise
            // yalnizca TEK bir oynanis bicimiyle geciliyordu — yani bolum
            // cozulebilir degil ezberlenebilirdi. Haritada OLU pad yok, yani
            // gizlemelerin hepsi saf krater karariydi ve K-7'nin alt siniri da
            // yok. Krater kisiti bu iki bolumden kaldirildi.
            // HARITA 4 (L29 · L39 · L49) 12->10 · 13->11 · 14->12.
            // Catalin iki yanindaki dorder pad, iki kolu birden tutan iki
            // pad'e indirildi (16 -> 14 pad). Sayi dustu ama K-5 (acik >= R+2)
            // korunuyor; asagidaki test bunu ayrica dogruluyor.
            29 to 10, 30 to 9, 31 to 9, 32 to 8, 33 to 9,
            34 to 10, 35 to 8, 36 to 9, 37 to 8, 38 to 10, 39 to 11,
            40 to 10, 41 to 10, 42 to 8, 43 to 8, 44 to 13,
            45 to 8, 46 to 9, 47 to 8, 48 to 11, 49 to 12, 50 to 11,
            51 to 11, 52 to 9, 53 to 9, 54 to 15, 55 to 11
        )
        expected.forEach { (level, open) ->
            val spec = GameConfig.levelSpec(level)
            val actual = LevelData.forMapId(spec.mapId).buildSpots.count { it.id !in spec.disabledPadIds }
            assertEquals("bolum $level acik pad sayisi (harita ${spec.mapId})", open, actual)
        }
    }

    /**
     * K-5 — `acik pad >= R + 2`. Tasarlanan kadro yerlestirildikten sonra
     * oyuncunun elinde en az iki SECENEK kalmali; aksi halde "nereye kurayim"
     * karari yok olur ve bolum tek bir dogru cozume iner.
     */
    @Test
    fun everyLateLevelLeavesAtLeastTwoSpareOpenPads() {
        for (level in 23..55) {
            val spec = GameConfig.levelSpec(level)
            val open = LevelData.forMapId(spec.mapId).buildSpots.count { it.id !in spec.disabledPadIds }
            val roster = SupplyBudgetModel.DESIGNED_ROSTER_SIZE[level - 1]
            assertTrue(
                "bolum $level: $open acik pad, tasarlanan kadro $roster — K-5 `acik >= R+2` kirildi",
                open >= roster + 2
            )
        }
    }

    /**
     * K-6 — bir bolumde GORUNUR birakilan hicbir pad "olu" olamaz.
     *
     * Olu pad kumesi (tum kuleler acik, esik 270, ikinci kol devrede) haritaya
     * baglidir ve o haritayi kullanan HER bolumde gizlenmek zorundadir. Ters
     * yon (`gorunur pad rotaya erisiyor mu`) `PadReachabilityPerLevelTest`
     * icinde geometriden CANLI olculuyor; burada listenin kendisi pinleniyor ki
     * biri krater listesini "sadelestirirken" olu pad'i geri acmasin.
     */
    @Test
    fun everyLateLevelHidesAllOfItsMapsPermanentlyDeadPads() {
        val deadByMap = mapOf(
            1 to emptySet<Int>(), 2 to emptySet(), 3 to setOf(10), 4 to emptySet(),
            5 to setOf(3), 6 to setOf(4, 10), 7 to setOf(3, 6), 8 to setOf(6, 8, 11),
            9 to setOf(5), 10 to setOf(8, 17), 11 to emptySet()
        )
        for (level in 23..55) {
            val spec = GameConfig.levelSpec(level)
            val dead = deadByMap.getValue(spec.mapId)
            val visibleDead = dead - spec.disabledPadIds.toSet()
            assertTrue(
                "bolum $level (harita ${spec.mapId}) olu pad(ler)i GORUNUR birakiyor: $visibleDead",
                visibleDead.isEmpty()
            )
        }
    }

    /**
     * LEVEL_DESIGN.md F.3 D2: kalan pad sayisi >= max(6, toplamin %60'i).
     *
     * KAPSAM: **yalnizca Act II** — bu kural KRATER kisitinin kuralidir (test
     * adi da bunu soyluyor). Krater, calisan bir pad'i bilerek kapatir; oradaki
     * risk "oyuncudan gercek secenek calmak"tir, o yuzden yuksek bir taban
     * gerekir.
     *
     * Act I'de kapatilan pad'ler ise ZATEN kullanilamayan pad'lerdir: uzerine
     * kurulan kule hicbir seye ates edemez. Onlari saymak "10 secenegin var"
     * demektir, oysa gercekte 5 vardi — yani D2'yi Act I'e uygulamak oyuncuyu
     * korumaz, sadece tuzaklari gorunur tutar. Act I'in kendi tabani (>= 5
     * KULLANILABILIR pad) `PadReachabilityPerLevelTest` icinde zorlanir ve
     * gizleme sonrasi kalanlarin hepsinin GERCEKTEN calistigi da orada
     * kanitlanir — bu, D2'nin Act I'de verdiginden daha guclu bir garantidir.
     */
    @Test
    fun enoughPadsRemainPlayableAfterTheCraterConstraint() {
        campaign.filter { it.act == 2 }.forEach { spec ->
            val total = LevelData.forMapId(spec.mapId).buildSpots.size
            val remaining = total - spec.disabledPadIds.size
            val floor = maxOf(6, Math.round(total * 0.60f))
            assertTrue(
                "bolum ${spec.levelId}: $total pad'den yalnizca $remaining kaldi " +
                    "(en az $floor olmali)",
                remaining >= floor
            )
        }
    }

    // ------------------------------------------------------ isim / gorunum

    @Test
    fun everyMapHasAnEnglishDisplayName() {
        assertEquals((1..11).toSet(), GameConfig.MAP_NAMES_EN.keys)
        GameConfig.MAP_NAMES_EN.forEach { (id, name) ->
            assertTrue("harita $id adi bos", name.isNotBlank())
        }
        assertEquals(
            "harita adlari tekil olmali",
            GameConfig.MAP_NAMES_EN.size, GameConfig.MAP_NAMES_EN.values.toSet().size
        )
    }

    @Test
    fun displayNameNeverFallsBackToThePlaceholderForARealLevel() {
        campaign.forEach { spec ->
            assertTrue(
                "bolum ${spec.levelId} yer tutucu ad kullaniyor: ${spec.displayName}",
                !spec.displayName.startsWith("Sector ")
            )
        }
    }

    @Test
    fun theTwoActsReuseTheSameElevenMapNames() {
        // "Ters sira = harita SIRASI" (DECISIONS): Act II yeni harita sanati
        // getirmiyor, ayni 11 haritayi tersten oynatiyor.
        val actOneNames = campaign.filter { it.act == 1 }.map { it.displayName }.toSet()
        val actTwoNames = campaign.filter { it.act == 2 }.map { it.displayName }.toSet()
        assertEquals(actOneNames, actTwoNames)
    }

    // --------------------------------------------------- levelSpec() sozlesmesi

    @Test
    fun levelSpecReturnsTheRequestedLevelForEveryValidId() {
        for (lv in 1..55) {
            assertEquals(lv, GameConfig.levelSpec(lv).levelId)
        }
    }

    @Test
    fun levelSpecFallsBackToTheFirstLevelForOutOfRangeIds() {
        listOf(0, -5, 56, 999).forEach { bad ->
            assertEquals(
                "gecersiz bolum $bad icin bolum 1'e dusulmeli",
                1, GameConfig.levelSpec(bad).levelId
            )
        }
    }

    // ------------------------------------ APK'da bulunan harita bitmap'leri

    /**
     * `SHIPPED_MAP_IDS` motorun yedek harita dalini suruyor: bir bolumun
     * bitmap'i APK'da yoksa `MAP_FALLBACK_ID` geometrisine dusuyor.
     *
     * Su an YALNIZCA harita 1 gonderiliyor, yani 22 bolumun 20'si (harita 1'i
     * kullanan bolum 1 ve 22 haric) Meadow Pass olarak oynaniyor. Bu KASITLI
     * ve gecici; bu test hangi noktada oldugumuzu pinliyor ki 10 bitmap
     * eklendiginde test kirilip guncellenmeyi hatirlatsin.
     *
     * Oyuncuya gorunen sonuc ve bolum secme ekranindaki yanlis vaat:
     * docs/QA_REPORT.md B-05.
     */
    @Test
    fun shippedMapCoverageIsPinnedAndTheFallbackIsAlwaysShipped() {
        assertTrue(
            "MAP_FALLBACK_ID (${GameConfig.MAP_FALLBACK_ID}) SHIPPED_MAP_IDS icinde " +
                "OLMALI — yoksa yedek harita da cizilemez ve her bolum bozulur",
            GameConfig.MAP_FALLBACK_ID in GameConfig.SHIPPED_MAP_IDS
        )
        GameConfig.SHIPPED_MAP_IDS.forEach { id ->
            assertTrue("SHIPPED_MAP_IDS gecersiz harita $id iceriyor", id in 1..11)
        }

        // Faz 4b: 11 harita bitmap'inin TAMAMI pakete girdi
        // (bg_level_01..11.webp, 1920px WebP q80, toplam 3.34 MB).
        // Bu assertion bir GUARD: harita envanteri degisirse haber verir.
        assertEquals(
            "APK'daki harita bitmap sayisi degisti — res/drawable-nodpi ile " +
                "karsilastir ve docs/QA_REPORT.md B-05'i guncelle",
            (1..11).toSet(), GameConfig.SHIPPED_MAP_IDS
        )

        // Artik HICBIR bolum yedek haritaya dusmuyor: her bolum kendi
        // haritasinin bitmap'ini ciziyor. Yedek mekanizmasi yerinde kaliyor
        // ama tetiklenmemesi gerekiyor.
        val playedOnFallback = campaign.count { it.mapId !in GameConfig.SHIPPED_MAP_IDS }
        assertEquals(
            "yedek haritaya dusen bolum sayisi 0 OLMALI — bir bolum yedege " +
                "dusuyorsa o haritanin bitmap'i eksik",
            0, playedOnFallback
        )
    }

    @Test
    fun theRouteAssignmentSeedIsStableSoLevelsAreReplayable() {
        // Catallanan haritalarda rota atamasi seed'li olmali, yoksa ayni bolum
        // her oynanista farkli davranir ve denge dogrulanamaz.
        assertTrue("rota seed'i sifir olmamali", GameConfig.ROUTE_RNG_SEED_BASE != 0L)
    }

    @Test
    fun onlyTheDeprecatedSingleLevelWaveListHasSixWaves() {
        // GameConfig.WAVES eski 6 dalgalik tek-bolum listesi. HUD hâlâ bunu
        // okuyorsa "WAVE 9/6" gibi sacma bir sayac cikar (docs/QA_REPORT.md B-03).
        @Suppress("DEPRECATION")
        val legacy = GameConfig.WAVES.size
        assertEquals(6, legacy)
        assertTrue(
            "eski liste kampanyanin gercek dalga sayilariyla karistirilmamali: " +
                "bolum 22'de $legacy degil ${WaveDefinitions.waveCount(22)} dalga var",
            WaveDefinitions.waveCount(22) > legacy
        )
    }
}
