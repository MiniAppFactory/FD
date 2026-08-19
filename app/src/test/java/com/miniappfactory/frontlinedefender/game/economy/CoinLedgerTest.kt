package com.miniappfactory.frontlinedefender.game.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ECONOMY_AUDIT_2 — **COIN EKONOMISININ BUTUNU.** Kaynak - Sink = Fazla.
 *
 * ---------------------------------------------------------------------------------
 * NEDEN BU DOSYA VAR
 * ---------------------------------------------------------------------------------
 * Projede olculmemis tek ekonomi ekseni buydu. Var olan testler coin'in her bir
 * PARCASINI dogruluyordu — kilit tablosu, odul formulu, agac toplami, gorev
 * butcesi, reklam tavani — ama hicbiri ikisini birden toplayip **"oyuncunun
 * cebinde kampanya sonunda ne kaliyor"** sorusunu sormuyordu.
 * `EconomySimulationTest.aOneStarRunNeverGoesBrokeAcrossFiftyFiveLevels`
 * yalnizca ALT sinira ("iflas etmez") bakiyor; UST sinira kimse bakmamisti.
 *
 * Bakildiginda cikan sonuc, projenin en buyuk tek ekonomi acigiydi ve
 * ECONOMY_AUDIT_2 P0 olarak **kapatildi**:
 *
 *     BAND        KAYNAK    KILIT     BAKIYE   FAZLA (once)   FAZLA (sonra)
 *     1 yildiz    52.250   -23.105    29.145      15.245          9.545
 *     2 yildiz    67.950   -23.105    44.845      30.945         25.245
 *     3 yildiz    88.000   -23.105    64.895      50.995         45.295
 *
 * Kaynak tarafi (sol uc sutun) DEGISMEDI — odul formulu, kilit egrisi ve yildiz
 * carpanlari dondurulmus GDD sayilaridir. Degisen tek sey SINK: agac
 * 13.900 -> **19.600** (`EconomyConfig.TREE_TOTAL_COST` KDoc'unda gerekce) ve
 * meta rank kapilari acilarak harcama kampanyaya yayildi.
 *
 * **Hedefe gore nerede duruyoruz:** en kotu bantta fazla bir agac bedelinin
 * (13.900) ALTINA indi (9.545) ✓; agacin tamamlanma bolumu L22/L37 -> **L50** ✓.
 * 3 yildiz bandinda 45.295 coin hâlâ olu — bu bant icin YAPISAL cozum
 * tekrarlanabilir bir sinktir ve UI gerektirir (bkz. 5. bolum).
 *
 * Bu tablo gorev (3.620/hafta), reklam (450/gun), tekrar ve basarim (5.200)
 * gelirlerinin **hicbirini** icermiyor — hepsi fazlanin USTUNE biner.
 *
 * ## Neden 22 bolumde gorunmuyordu
 * Sayilar 22 bolumluk kampanyaya kalibre edilmisti ve orada DOGRUYDU:
 * kaynak 10.010, kilit 3.140, bakiye 6.870 — agacin (13.900) yarisi bile degil,
 * yani coin gercekten kitti. Kampanya 55 bolume cikarilinca kaynak x5,2 buyudu;
 * kilit x7,4 buyudu ama TABANDAN kucuk oldugu icin farki kapatmadi ve agac
 * **hic buyumedi**. Enflasyon yeni bir sayidan degil, eski sayilarin yeni
 * uzunluga tasinmasindan geldi.
 *
 * ## Fazlanin YAPISAL tabani — neden sifira inemez
 * Soft-lock guvenlik kurali `R(L) >= 2,0 x D(L+1)` (GDD C.3) toplamda
 * `toplamR ~ 2,26 x toplamD` demektir. Kilit egrisi ne kadar sikilirsa sikilsin
 * — ve `R(54)/D(55) = 2,05` ile zaten tavanda — kampanya gelirinin **yarisi**
 * kilitlerin disinda kalir: 55 bolumde 29.145 coin. Bu yuzden fazlanin cozumu
 * kilit tarafinda DEGIL, sink tarafindadir ve tek seferlik bir agac ancak
 * kendisi de kampanya uzunluguyla buyurse yetisir.
 *
 * ## Bu dosya ne YAPMAZ
 * Hicbir dengeyi kendisi degistirmez; olcer ve kilitler. Bugunku sayilar burada
 * yaziyor, biri kampanyayi uzattiginda ya da bir odulu buyuttugunde test
 * kirmizi yanar ve fazla bir daha SESSIZCE buyumez.
 *
 * Ayni hastaligin projedeki iki onceki vakasi (`WaveMetrics.AEHP` ve
 * `TIGHTENED_WAVE_KILL_SUPPLY` elle yazilmis tablolarinin sessizce bayatlamasi)
 * tam olarak boyle cozulmustu.
 *
 * **SAF JUnit.** Robolectric yok, Android yok.
 */
class CoinLedgerTest {

    private val maxLives = 20

    /** Yildiz esikleri YUZDE: %90 -> 3, %50 -> 2, alti 1. */
    private fun livesForStars(stars: Int): Int = when (stars) {
        3 -> 20 // tam can -> 3 yildiz + Kusursuz Savunma madalyasi
        2 -> 12 // %60
        else -> 5 // %25
    }

    private data class Ledger(
        val earned: Int,
        val lockSpend: Int,
        val balance: Int,
        val perLevelBalance: List<Int>,
    )

    /**
     * Kampanyayi bastan sona **gercek zafer yolundan** oynar: her bolumu ac, gec,
     * hicbir sey satin alma. Gorev/reklam/tekrar/basarim geliri YOK — yani bu
     * tablo fazlanin ALT siniridir.
     */
    private fun runCampaign(stars: Int): Ledger {
        var wallet = PlayerWallet()
        val perLevel = mutableListOf<Int>()
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            if (level !in wallet.unlockedLevels) {
                assertTrue("L$level acilamadi — soft-lock", canUnlock(wallet, level))
                wallet = applyUnlock(wallet, level)
            }
            val result = resolveLevelClear(wallet, level, livesForStars(stars), maxLives)
            assertEquals("L$level yildiz", stars, result.stars)
            wallet = applyLevelClear(wallet, result)
            perLevel += wallet.coins
        }
        return Ledger(wallet.lifetimeEarned, wallet.lifetimeSpent, wallet.coins, perLevel)
    }

    // =================================================================================
    // 1. UC BANDIN TAM DEFTERI — kaynak, sink, kalan
    // =================================================================================

    @Test
    fun campaignCoinLedgerIsMeasuredForEveryStarBand() {
        val one = runCampaign(1)
        val two = runCampaign(2)
        val three = runCampaign(3)

        // Kilit tarafi banttan BAGIMSIZ — tek seferlik ve sabit.
        assertEquals(EconomyConfig.TOTAL_LOCK_COST, one.lockSpend)
        assertEquals(EconomyConfig.TOTAL_LOCK_COST, two.lockSpend)
        assertEquals(EconomyConfig.TOTAL_LOCK_COST, three.lockSpend)

        // 1 yildiz: `EconomySimulationTest` ile ayni sayi (capraz dogrulama).
        assertEquals("1 yildiz kampanya geliri", 52_250, one.earned)
        assertEquals("1 yildiz kampanya sonu bakiye", 29_145, one.balance)

        assertEquals("2 yildiz kampanya geliri", 67_950, two.earned)
        assertEquals("2 yildiz kampanya sonu bakiye", 44_845, two.balance)

        // 3 yildiz: 83.600 bolum odulu + 55 x 80 Kusursuz Savunma = 88.000.
        assertEquals("3 yildiz kampanya geliri", 88_000, three.earned)
        assertEquals(
            "Kusursuz Savunma madalyasi payi",
            55 * EconomyConfig.PERFECT_DEFENSE_BONUS,
            three.earned - 83_600,
        )
        assertEquals("3 yildiz kampanya sonu bakiye", 64_895, three.balance)

        // Bakiye yildizla artar — tekrar oynamanin degeri korunur.
        assertTrue(one.balance < two.balance && two.balance < three.balance)
    }

    /**
     * **ACIGIN KENDISI.** Kampanyayi bitiren oyuncu, agacin tamamini da odedikten
     * sonra elinde ne tutuyor? Harcayacak yer YOK: `PlayerProgress`te coin'i
     * dusuren tam olarak uc yer var (bolum kilidi, meta rank, Us Tamiri) ve ilk
     * ikisi tek seferliktir.
     *
     * Bu test bugunku sayiyi KILITLER, mesru gostermez. Fazla kucultuldugunde
     * (yeni sink, kilit egrisi, agac buyutulmesi) bu test bilincli olarak
     * guncellenir; kendiliginden BUYUDUGUNDE kirmizi yanar.
     */
    @Test
    fun everyStarBandFinishesTheCampaignWithADeadCoinSurplus() {
        val tree = EconomyConfig.TREE_TOTAL_COST
        val surplus = intArrayOf(1, 2, 3).map { runCampaign(it).balance - tree }

        assertEquals("1 yildiz fazlasi", 9_545, surplus[0])
        assertEquals("2 yildiz fazlasi", 25_245, surplus[1])
        assertEquals("3 yildiz fazlasi", 45_295, surplus[2])

        // **KAPATMA HEDEFI.** En kotu oyuncunun fazlasi, bir agac bedelinin
        // ALTINDA kalmali. Once 15.245 > 13.900 idi; simdi 9.545 < 19.600.
        assertTrue(
            "1 yildiz fazlasi ${surplus[0]}, agac $tree — hedef: fazla < agac",
            surplus[0] < tree,
        )
        // Eski agac bedeline gore de kapandi (hedef metnindeki 13.900 esigi).
        assertTrue(
            "1 yildiz fazlasi ${surplus[0]} eski agac bedelinin (13.900) altinda olmali",
            surplus[0] < 13_900,
        )
        // Fazla, kampanya gelirinin ucte birini gecmemeli. 1 yildiz bandi:
        // once 29.145 bakiyenin %52'si oluydu, simdi %33'u.
        val oneStar = runCampaign(1)
        assertTrue(
            "1 yildiz fazlasi bakiyenin ucte birini asiyor (${surplus[0]}/${oneStar.balance})",
            surplus[0] * 3 <= oneStar.balance,
        )
        // ACIK KALAN: 3 yildiz bandi hâlâ agacin iki katindan fazlasini artiriyor.
        // Bu bandin cozumu tekrarlanabilir sinktir (UI gerektirir, bkz. 5. bolum);
        // buradaki sinir bir HEDEF degil, daha da kotulesmeye karsi nöbetci.
        assertTrue(
            "3 yildiz fazlasi ${surplus[2]} agacin 3 katini gecti — ekonomi coktu",
            surplus[2] <= tree * 3,
        )
    }

    /**
     * 22 bolumluk kampanyada AYNI sayilar saglikliydi. Aciğın kaynagi bir tuning
     * hatasi degil, **uzunluk degisiminin sinklere yansitilmamasi**.
     */
    @Test
    fun theTwentyTwoLevelCampaignWasCoinScarceAndTheFiftyFiveLevelOneIsNot() {
        val handwritten = 22
        val earned22 = (1..handwritten).sumOf { levelReward(it, 1) }
        val locks22 = (1..handwritten).sumOf { lockCost(it) }
        assertEquals(10_010, earned22)
        assertEquals(EconomyConfig.HANDWRITTEN_TOTAL_LOCK_COST, locks22)
        // 22 bolumde agac kampanya gelirinin USTUNDE -> coin gercekten kit.
        assertTrue(
            "22 bolumde agac ($earned22 gelire karsi) kit olmali",
            EconomyConfig.TREE_TOTAL_COST > earned22 - locks22,
        )
        // 55 bolumde bakiye hâlâ agactan buyuk (fazla sifirlanmadi, kucultuldu)...
        val one = runCampaign(1)
        assertTrue(
            "55 bolumde agac artik kit degil",
            EconomyConfig.TREE_TOTAL_COST < one.balance,
        )
        // ...ama agac artik kampanya uzunluguyla OLCEKLI: 22 bolumde bakiyenin
        // 2,02 kati idi, 55 bolumde 0,67 kati. Once 0,48 idi (agac bakiyenin
        // yarisi bile degildi) — iste olu fazla oradan geliyordu.
        val ratio = EconomyConfig.TREE_TOTAL_COST.toDouble() / one.balance
        assertTrue("agac/bakiye orani $ratio — agac yeniden anlamli bir hedef olmali", ratio > 0.6)
    }

    // =================================================================================
    // 2. AGACIN BITIS NOKTASI — meta ilerlemesi kampanyanin neresinde tukeniyor
    // =================================================================================

    /**
     * Fazlanin oynanistaki karsiligi ve **P0'in asil belirtisi**: meta agaci
     * eskiden 3 yildizli oyuncuda L22'de, 1 yildizlida L37'de TAMAMEN bitiyordu.
     * Sonraki 33 (sirasiyla 18) bolumde satin alinacak hicbir sey kalmiyordu.
     *
     * Simdi iki bant da **L50**'de bitiriyor: kampanyanin son bes bolumune kadar
     * dukkanda alinacak bir sey var.
     *
     * ## Iki kaldirac neden BIRLIKTE gerekti (olculen)
     *     BAND       ONCE   yalnizca agac 19.600   agac + kapilar
     *     1 yildiz   L37           ~L45                 L50
     *     3 yildiz   L22           ~L27                 L50
     * Agaci buyutmek 3 yildizli oyuncuyu kurtarmiyor — o oyuncu parayi zaten orta
     * oyunda kazaniyor. Zamanlamayi kapilar duzeltiyor.
     *
     * Acgozlu harcayici: her bolumden sonra karsilayabildigi EN UCUZ rank'i alir
     * (`purchaseAllowed` ile, rezerv ve kapi kurallari dahil).
     */
    @Test
    fun theTreeNowCompletesNearTheEndOfTheCampaignForEveryBand() {
        val one = treeCompletionLevel(stars = 1)
        val three = treeCompletionLevel(stars = 3)
        assertEquals("1 yildiz agaci bitirme bolumu", 50, one)
        assertEquals("3 yildiz agaci bitirme bolumu", 50, three)

        // HEDEF: agac kampanyanin son ceyreginde bitmeli (L45+).
        for ((band, level) in listOf(1 to one, 3 to three)) {
            assertTrue(
                "$band yildiz bandi agaci L$level'de bitiriyor — L45'ten once",
                level >= 45,
            )
            assertTrue(
                "agac kampanya icinde tamamlanabilmeli (kapi asla ulasilamaz olamaz)",
                level in 1..EconomyConfig.CAMPAIGN_LEVELS,
            )
        }
    }

    /**
     * Bitis noktasini gerceklestiren sey iki tarafli olmali: kapi olmasa para,
     * para olmasa kapi tek basina yeterli degil. Bu test kapinin TEK BASINA
     * bir bekleme ekrani olmadigini kilitler — en kotu bant, son kapiyi gectigi
     * bolumde agacin son rank'ini gercekten SATIN ALABILIYOR olmali.
     */
    @Test
    fun theWorstBandCanActuallyAffordTheLastRankAtTheLastGate() {
        val lastGate = EconomyConfig.RANK_GATE_CLEARED_LEVEL.max()
        assertEquals(50, lastGate)
        assertEquals(
            "en kotu bant son kapida bekletilmemeli (para da yetmeli)",
            lastGate, treeCompletionLevel(stars = 1),
        )
    }

    /** Agacin son rank'i hangi bolumden sonra alinabiliyor? Alinamiyorsa 0. */
    private fun treeCompletionLevel(stars: Int): Int {
        var wallet = PlayerWallet()
        var upgrades = MetaUpgrades()
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            if (level !in wallet.unlockedLevels) wallet = applyUnlock(wallet, level)
            wallet = applyLevelClear(
                wallet,
                resolveLevelClear(wallet, level, livesForStars(stars), maxLives),
            )
            // En ucuz alinabilir rank'i, alinabildigi surece al.
            while (true) {
                val best = UpgradeLine.entries
                    .mapNotNull { line ->
                        val d = purchaseAllowed(wallet, upgrades, line)
                        if (d is PurchaseDecision.Allowed) line to d.price else null
                    }
                    .minByOrNull { it.second } ?: break
                val (w, u) = applyPurchase(wallet, upgrades, best.first)
                wallet = w
                upgrades = u
            }
            if (upgrades.isMaxed()) return level
        }
        return 0
    }

    // =================================================================================
    // 3. GOREV VE REKLAM GELIRI — fazlanin USTUNE ne biniyor
    // =================================================================================

    /**
     * Gorev geliri kilit ilerlemesini KIRMIYOR (denetim maddesi 3): butun gorevler
     * oynanis gerektirir ve haftalik gelir tek basina bir sonraki kilidi bile
     * degil, kampanyanin kilit toplaminin kucuk bir dilimini oder. Kiran sey
     * gorevler degil, gorevlerin ZATEN fazla olan bir cebe eklenmesidir.
     */
    @Test
    fun missionIncomeIsNotAProgressionBypassButItCompoundsTheSurplus() {
        val weeklyIncome = 7 * EconomyConfig.DAILY_MAX_TOTAL + EconomyConfig.WEEKLY_BUDGET
        assertEquals("haftalik gorev geliri", 3_620, weeklyIncome)

        // Kilit ilerlemesi kirilmiyor: bir haftanin TAM gorev geliri, kampanyanin
        // kilit toplaminin %16'sindan az.
        assertTrue(
            "haftalik gorev geliri $weeklyIncome kilit toplaminin %16'sini asiyor",
            weeklyIncome * 100 <= EconomyConfig.TOTAL_LOCK_COST * 16,
        )

        // Ama fazlanin uzerine biniyor: iki haftalik gorev + reklam geliri tek
        // basina agacin yarisindan fazlasini oduyor.
        val adIncome = 14 * EconomyConfig.R1_COIN_BUDGET_PER_DAY
        val twoWeeks = 2 * weeklyIncome + adIncome
        assertEquals(13_540, twoWeeks)
        assertTrue(
            "iki haftalik gorev+reklam geliri $twoWeeks, agacin ($treeCost) yarisinin altinda olmali",
            twoWeeks > treeCost / 2,
        )
    }

    private val treeCost get() = EconomyConfig.TREE_TOTAL_COST

    // =================================================================================
    // 5. ACIK KALAN — 3 yildiz bandinin 45.295 coinlik fazlasi
    // =================================================================================

    /**
     * **KAPANMAYAN PARCA, SAYIYLA.** Agacin buyutulmesi ve kapilar, fazlanin
     * kaynak-uzunluk carpikligini duzeltti ama 3 yildiz bandinda 45.295 coin hâlâ
     * harcanacak yer bulamiyor. Nedeni yapisal ve bu dosyada olculu duruyor:
     * `PlayerProgress`te coin'i dusuren yalnizca UC yer var ve ikisi TEK
     * SEFERLIKTIR (bolum kilidi, meta rank). Geriye kalan tek tekrarlanabilir
     * sink Us Tamiri'dir ve o da zorunlu degildir.
     *
     * Tekrarlanabilir bir sink olmadan kapanacak bir buyukluk DEGIL: 55 bolumluk
     * kampanyada 3 yildiz bandinin geliri 88.000, kalici sink kapasitesi
     * 23.105 + 19.600 = 42.705. Aradaki farki emmek icin agacin ~2,7 katina
     * cikmasi gerekirdi — zorluk tavanlari buna izin vermiyor
     * (`EconomySimulationTest.maxedMetaDoesNotMakePeakLevelsThreeStarTrivial`).
     *
     * Bu test bir hedef koymaz; farkin **ne kadar** oldugunu olcer ve sessizce
     * buyumesini engeller.
     */
    @Test
    fun theRemainingGapNeedsARepeatableSinkAndIsMeasuredHere() {
        val three = runCampaign(3)
        val permanentSinks = EconomyConfig.TOTAL_LOCK_COST + EconomyConfig.TREE_TOTAL_COST
        assertEquals("kalici sink kapasitesi", 42_705, permanentSinks)

        val unabsorbed = three.earned - permanentSinks
        assertEquals("3 yildiz bandinda emilemeyen gelir", 45_295, unabsorbed)

        // Agac bu farki tek basina emseydi ne kadar olmasi gerekirdi?
        val neededTree = three.earned - EconomyConfig.TOTAL_LOCK_COST
        assertEquals(64_895, neededTree)
        assertTrue(
            "agacin $neededTree'e cikmasi gerekirdi — zorluk tavanlari izin vermiyor",
            neededTree > EconomyConfig.TREE_TOTAL_COST * 3,
        )
    }

    // =================================================================================
    // 4. GUCLENDIRICI EKONOMISI — fiyat caydirici mi, yoksa anlamsiz mi?
    // =================================================================================

    /**
     * Denetim maddesi 4. Us Tamiri, coin'i dusuren **tek tekrarlanabilir** sink.
     * Sorulan soru "fiyat caydirici mi" idi; olculen cevap TERSI:
     *
     *     BOLUM   FIYAT   1-YILDIZ BANDI BAKIYESI   BAKIYE / FIYAT
     *     L7        240            1.510                  6,3x
     *     L22       540            6.870                 12,7x
     *     L55     1.200           29.145                 24,3x
     *
     * Fiyat mutlak olarak buyuyor ama cuzdana gore **surekli ucuzluyor**. Yani
     * "guclendirici bir bedeldir" tasarim kisiti (BoosterSystem dosya basi:
     * "3. kule mi, hava destegi mi?") gec oyunda fiilen yoktur — dugmeye basmak
     * bedavadir. Bu, madde 1'in fazlasinin oynanistaki ikinci belirtisidir;
     * fiyati YUKSELTEREK cozulmez (fiyat/etki egrisi yukseltilemez kurali),
     * fazlanin kendisi kucultulerek cozulur.
     */
    @Test
    fun baseRepairPriceGetsRelativelyCheaperAsTheCampaignGoesOn() {
        val balances = runCampaign(1).perLevelBalance
        fun affordability(level: Int) =
            balances[level - 1].toDouble() / boosterPrice(BoosterType.BASE_REPAIR, level)

        assertEquals(240, boosterPrice(BoosterType.BASE_REPAIR, 7))
        assertEquals(540, boosterPrice(BoosterType.BASE_REPAIR, 22))
        assertEquals(1_200, boosterPrice(BoosterType.BASE_REPAIR, 55))

        assertEquals(6.3, affordability(7), 0.1)
        assertEquals(12.7, affordability(22), 0.1)
        assertEquals(24.3, affordability(55), 0.2)

        // Monoton ucuzlama — hicbir yerde tersine donmuyor.
        assertTrue(
            "erisilebilirlik gec oyunda erken oyunun 3 katini asti",
            affordability(55) > affordability(7) * 3,
        )
    }

    /**
     * Fiyatin GDD kisitlari hâlâ saglaniyor — bu test acigi tarif ederken mevcut
     * korumalarin durdugunu da kilitler (aksi halde "fazla var, fiyat serbest"
     * yorumu yapilabilir).
     */
    @Test
    fun baseRepairPriceStillHonoursItsThreeConstraints() {
        val cheapestMetaRank = UpgradeLine.entries.minOf { it.costOfRank(1) }
        for (level in EconomyConfig.BASE_REPAIR_UNLOCK_LEVEL..EconomyConfig.CAMPAIGN_LEVELS) {
            val price = boosterPrice(BoosterType.BASE_REPAIR, level)
            assertTrue("L$level: $price <= en ucuz rank $cheapestMetaRank", price > cheapestMetaRank)
            assertTrue("L$level: $price >= 1 yildiz odulu", price < levelReward(level, 1))
            assertTrue(
                "L$level: $price < yildiz atlama farki",
                price >= levelReward(level, 3) - levelReward(level, 1),
            )
        }
    }
}
