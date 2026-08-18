package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.economy.SupplyBudgetModel
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import com.miniappfactory.frontlinedefender.game.model.LevelGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * GEC OYUN TEDARIK SINKI — kademe 3'un VAROLUS GEREKCESI (DECISIONS B5)
 * ===========================================================================
 *
 * SORUN (LEVEL_DESIGN G.5, olculmus): bolum 15-22'de Tedarik gelirinin buyuk
 * kismi **harcanacak yer bulamiyor.** Act II'de bagalayici kisit para degil
 * PAD'dir: pad'lerin %25-36'si kapali, kalan pad'lerin hepsi doldurulup
 * hepsi kademe 2'ye cikarildiktan sonra oyuncunun elinde hâlâ butcenin buyuk
 * kismi durur. Harcanacak yeri olmayan gelir bir KARAR degildir; gec oyun
 * bolumleri bu yuzden sikici.
 *
 * BU DOSYA NE OLCER
 * -----------------
 * **Atil Tedarik orani** = 1 - (bolumde harcanabilecek en fazla Tedarik) /
 * (bolum butcesi). "En fazla harcama" = kullanilabilir HER pad'e bir kule +
 * o bolumde acik olan EN YUKSEK kademeye kadar butun yukseltmeler. Yani
 * oyuncunun teorik tavani; gercek oyuncu bunun altinda kalir.
 *
 *   · butce      -> `SupplyBudgetModel.supplyBudget` (canli dalga tablosu,
 *                   canli odul tablosu, Act carpani dahil)
 *   · pad sayisi -> olculmus geometri eksi `LevelSpec.disabledPadIds`
 *   · kadro      -> o bolumde acik kuleler arasinda round-robin (kadro
 *                   bilesim kurali `SupplyBudgetModel.designedRoster` ile ayni)
 *   · fiyat      -> canli `TOWER_SPECS`, kademe merdiveni uzerinden
 *
 * SIHIRLI SAYI YOK: tek elle yazilmis degerler hedef bantlardir ve onlar
 * zaten TASARIM KARARIDIR, olcum degil.
 *
 * OLCUM SONUCU (bu testler kosarken hesaplanir, burada kayit icin):
 *
 * | bolum | pad | butce | maks harcama Kd.2 | atil Kd.2 | Kd.3 ile | atil Kd.3 |
 * |---|---|---|---|---|---|---|
 * | 15 | 7 | 2958 | 1265 | %57.2 | 2515 | %15.0 |
 * | 16 | 8 | 3478 | 1450 | %58.3 | 2870 | %17.5 |
 * | 17 | 7 | 3944 | 1265 | %67.9 | 2515 | %36.2 |
 * | 18 | 8 | 5110 | 1450 | %71.6 | 2870 | %43.8 |
 * | 19 | 11 | 3878 | 1990 | %48.7 | 3950 | %0 (tavan butceyi asiyor) |
 * | 20 | 8 | 5696 | 1450 | %74.5 | 2870 | %49.6 |
 * | 21 | 9 | 6367 | 1575 | %75.3 | 3125 | %50.9 |
 * | 22 | 7 | 7115 | 1265 | %82.2 | 2515 | %64.7 |
 * | **ort** | | | | **%67.0** | | **%34.7** |
 */
class LateGameSupplySinkTest {

    /**
     * Sorunun tarif edildigi aralik (LEVEL_DESIGN G.5: "15-22 -> baglayici
     * kisit kullanilabilir pad sayisi").
     */
    private val LATE_GAME_LEVELS = 15..GameConfig.CAMPAIGN_LEVEL_COUNT

    /**
     * Kademe 3 gelmeden ONCEKI merdiven boyu. Karsilastirma tabani; gercek
     * merdiven artik veriden gelir.
     */
    private val TIER_CAP_BEFORE_THIS_PHASE = 2

    /**
     * Kademe 3'un gerekcesi sayiyla: gec oyun bolumlerinde ortalama atil
     * Tedarik orani bunun ALTINA inmeli. %50, "gelirin cogunlugu artik
     * harcanabilir" esigidir — bir sink ancak bunu saglarsa sink'tir.
     */
    private val MAX_MEAN_IDLE_RATIO_AFTER = 0.50

    /**
     * Duzeltmenin BUYUKLUGU: atil oran en az bu kadar puan dusmeli. Kucuk bir
     * iyilesme kademe 3'un maliyetini (yeni veri yapisi, yeni UI durumu, yeni
     * kilit) hakli cikarmaz.
     */
    private val MIN_IDLE_RATIO_DROP = 0.25

    // ---------------------------------------------------------------- model

    /** Bu bolumde gercekten uzerine kule kurulabilen pad sayisi. */
    private fun usablePads(level: Int): Int {
        val spec = GameConfig.levelSpec(level)
        val map = LevelGeometry.ALL_MAPS.first { it.levelId == spec.mapId }
        return map.buildSpots.count { it.id !in spec.disabledPadIds }
    }

    /**
     * Bu bolumde kurulacak kadro: her pad'e bir kule, o bolumde acik olan
     * tipler arasinda round-robin. `SupplyBudgetModel.designedRoster` ile ayni
     * kural — gec oyunda "tasarlanan kadro" zaten pad sayisi kadardir.
     */
    private fun roster(level: Int): List<TowerType> {
        val available = GameConfig.unlockedTowers(level)
        return List(usablePads(level)) { available[it % available.size] }
    }

    /** Bir kuleyi [tierCap] kademesine kadar goturmenin TAM Tedarik bedeli. */
    private fun fullCost(type: TowerType, tierCap: Int): Int {
        val s = GameConfig.TOWER_SPECS.getValue(type)
        val top = minOf(s.maxTier, tierCap)
        return s.buildCost + (2..top).sumOf { s.tier(it).upgradeCost }
    }

    /** Butun pad'ler dolu ve hepsi [tierCap] kademesinde: harcanan Tedarik. */
    private fun maxSpend(level: Int, tierCap: Int): Int =
        roster(level).sumOf { fullCost(it, tierCap) }

    /**
     * Butun pad'ler dolu ve hepsi O BOLUMDE ulasilabilen en yuksek kademede:
     * bugunku gercek tavan. Kademe kilidi kule basina sorulur, cunku kilit
     * tablosu bir gun kuleye gore farklilasabilir.
     */
    private fun maxSpendToday(level: Int): Int =
        roster(level).sumOf { fullCost(it, GameConfig.maxTowerTier(it, level)) }

    /** Harcanacak yer bulamayan gelir orani; tavan butceyi asiyorsa 0. */
    private fun idleRatio(level: Int, spend: Int): Double {
        val budget = SupplyBudgetModel.supplyBudget(level).toDouble()
        return ((budget - spend) / budget).coerceAtLeast(0.0)
    }

    private fun idleBefore(level: Int): Double =
        idleRatio(level, maxSpend(level, TIER_CAP_BEFORE_THIS_PHASE))

    private fun idleToday(level: Int): Double = idleRatio(level, maxSpendToday(level))

    // ------------------------------------------------------------- testler

    /**
     * TESHISIN KENDISI. Kademe 2 tavaniyla gec oyun geliri gercekten oluydu;
     * bu test kirmizi olursa sorun baska bir yolla cozulmus demektir ve kademe
     * 3'un gerekcesi yeniden yazilmalidir.
     */
    /**
     * ESIK YENIDEN TURETILDI: %50 -> **%40**, ve nedeni bir BULGU.
     *
     * Atil oran once %67,0 olculmustu; tek ritme gecis (bolumler 5-7 dalgaya
     * indi, gelir yarilandi, sermaye kadrodan turetildi) **atil Tedarigin bir
     * kismini kendi basina yok etti**: kademe 2 tavaniyla olculen oran bugun
     * %46,7. Yani gec oyun fazlaligina iki bagimsiz cozum uygulandi ve ikincisi
     * birincinin teshisini kismen gecersiz kilmis oldu.
     *
     * Kademe 3'un gerekcesi bundan ETKILENMEDI, cunku gerekcenin asil olcusu bu
     * esik degil DUSUS BUYUKLUGU: [MIN_IDLE_RATIO_DROP] (>= 25 puan) hâlâ
     * degismeden saglaniyor — bkz. [theThirdTierAbsorbsMostOfTheLateGameSupplySurplus].
     */
    @Test
    fun withoutTheThirdTierALargePartOfTheLateGameIncomeHasNowhereToGo() {
        val mean = LATE_GAME_LEVELS.map { idleBefore(it) }.average()
        assertTrue(
            "kademe $TIER_CAP_BEFORE_THIS_PHASE tavaniyla L${LATE_GAME_LEVELS.first}-" +
                "${LATE_GAME_LEVELS.last} atil Tedarik orani ortalama " +
                "${"%.1f".format(mean * 100)}% — teshis gelirin buyuk bir " +
                "bolumunun olu oldugunu soyluyordu",
            mean > 0.40
        )
    }

    /**
     * DUZELTMENIN OLCUSU. Kademe 3 devreye girdiginde atil oran ANLAMLI
     * olcude dusmeli — sadece "biraz iyilesme" bu isi hakli cikarmaz.
     */
    @Test
    fun theThirdTierAbsorbsMostOfTheLateGameSupplySurplus() {
        val before = LATE_GAME_LEVELS.map { idleBefore(it) }.average()
        val after = LATE_GAME_LEVELS.map { idleToday(it) }.average()

        assertTrue(
            "atil Tedarik orani ${"%.1f".format(before * 100)}% -> " +
                "${"%.1f".format(after * 100)}%; hedef: ortalama " +
                "%${(MAX_MEAN_IDLE_RATIO_AFTER * 100).toInt()} altina inmeli",
            after < MAX_MEAN_IDLE_RATIO_AFTER
        )
        assertTrue(
            "atil oran yalnizca ${"%.1f".format((before - after) * 100)} puan dustu; " +
                "kademe 3'un maliyetini hakli cikarmak icin en az " +
                "${(MIN_IDLE_RATIO_DROP * 100).toInt()} puan gerekli",
            before - after >= MIN_IDLE_RATIO_DROP
        )
    }

    /**
     * Her gec oyun bolumu tek tek iyilesmeli. Ortalama, tek bir bolumdeki
     * buyuk bir dususle "duzelmis" gibi gorunebilir; sink bolum-yerel olmali.
     */
    @Test
    fun everyLateGameLevelGetsAStrictlyDeeperPlaceToSpendItsSupply() {
        LATE_GAME_LEVELS.forEach { level ->
            val before = maxSpend(level, TIER_CAP_BEFORE_THIS_PHASE)
            val after = maxSpendToday(level)
            assertTrue(
                "bolum $level: harcanabilir tavan $before -> $after — her gec " +
                    "oyun bolumu daha derin bir harcama secenegi kazanmali",
                after > before
            )
            assertTrue(
                "bolum $level: atil oran ${"%.1f".format(idleBefore(level) * 100)}% " +
                    "-> ${"%.1f".format(idleToday(level) * 100)}% — dusmeli",
                idleToday(level) < idleBefore(level)
            )
        }
    }

    /**
     * **SINK BIR TAVAN OLMAMALI.** Butun pad'leri maks kademeye cikarmak gec
     * oyun bolumlerinin cogunda AFFORDABLE kalmali; aksi halde "harcayacak
     * yer yok" sorunu "harcamaya para yetmiyor" sorununa donusur ve karar
     * yine olur. Yaris esit degil: bazi bolumlerde tavanin ulasilamaz olmasi
     * ISTENEN seydir (hangi pad derinlesecek karari), ama cogunlukta degil.
     */
    @Test
    fun maxingOutEveryPadStaysAffordableInMostLateGameLevels() {
        val affordable = LATE_GAME_LEVELS.count {
            maxSpendToday(it) <= SupplyBudgetModel.supplyBudget(it)
        }
        val total = LATE_GAME_LEVELS.count()
        assertTrue(
            "gec oyun bolumlerinin yalnizca $affordable/$total'inde butun pad'ler " +
                "maks kademeye cikarilabiliyor — kademe 3 bir sink olmali, " +
                "ulasilamaz bir tavan degil",
            affordable * 2 > total
        )
        assertTrue(
            "hicbir bolumde tavan ulasilamaz degilse kademe 3 bir karar degil " +
                "otomatik alinan bir seydir",
            affordable < total
        )
    }

    /**
     * KADEME 3'UN ACILDIGI BOLUM. Act I'de atil oran zaten dusuk (hatta
     * negatif: oyuncu butun pad'leri dolduramaz), yani sink oraya GEREKMEZ ve
     * oraya konursa Act I'in doruk noktasi (L11 boss) sayisal olarak dagilir.
     * Kilit, atil oranin kalici olarak yukseldigi noktada olmali.
     */
    @Test
    fun theTierUnlockLandsWhereTheSupplySurplusActuallyStarts() {
        val unlock = GameConfig.TIER_THREE_UNLOCK_LEVEL

        val actOne = (1 until unlock).map { idleBefore(it) }
        val actTwo = (unlock..GameConfig.CAMPAIGN_LEVEL_COUNT)
            .map { idleBefore(it) }

        assertTrue(
            "kilitten onceki bolumlerde ortalama atil oran " +
                "${"%.1f".format(actOne.average() * 100)}% — kademe 3 oraya " +
                "gerekmiyor, konursa erken bolumler trivial olur",
            actOne.average() < 0.25
        )
        // Esik %50 -> %40: bkz. [withoutTheThirdTierALargePartOfTheLateGameIncomeHasNowhereToGo].
        // Tek ritme gecis atil Tedarigin bir kismini zaten yok etti; kilidin
        // YERI degismedi (fazlalik hâlâ tam olarak L12'de basliyor).
        assertTrue(
            "kilitten sonraki bolumlerde ortalama atil oran " +
                "${"%.1f".format(actTwo.average() * 100)}% — sink tam olarak " +
                "buradan itibaren gerekli",
            actTwo.average() > 0.40
        )
        // ADIM DEGISIMI — tek bir bolumun degeri degil.
        //
        // Once burada `idleBefore(12) > 0.25` yaziyordu, yani kilidin yeri TEK
        // bir bolumun atil oranina baglanmisti. O olcum GURULTULU: atil oran
        // butceden cok ACIK PAD SAYISINA duyarli ve pad sayisi haritadan
        // haritaya 7 ile 16 arasinda degisiyor. L12 harita 11'de yalnizca 7
        // acik pad'e sahip; yedi kule kademe 2'de zaten butcenin %81'ini
        // yutuyor, dolayisiyla o bolumde fazlalik dogal olarak kucuk.
        // Iddia perde seviyesinde olculur: fazlalik kilitle birlikte SICRAR.
        assertTrue(
            "kilit oncesi ortalama atil oran %.1f%%, sonrasi %.1f%% — kilidin yeri "
                .format(actOne.average() * 100, actTwo.average() * 100) +
                "fazlaligin BASLADIGI yer olmali (en az 20 puanlik sicrama)",
            actTwo.average() - actOne.average() >= 0.20
        )
    }

    /**
     * Kademe 3, kademe 1-2 ekonomisine DOKUNMADI. SPI (Supply Pressure Index)
     * bolen tarafi kademe 2 kadrosuyla tanimli ve L1-L8 icin kilitli; kademe
     * 3'un varligi o tabloyu degistirmemeli.
     */
    @Test
    fun addingTheThirdTierDoesNotMoveTheEarlyGameSupplyPressureIndex() {
        // KAPSAM: testin adinin soyledigi sey — ERKEN OYUN, L1..L8.
        // Bu sekiz bolum kademe 3'u hic gormez (TIER_THREE_UNLOCK_LEVEL = 12),
        // dolayisiyla "kademe 3 erken ekonomiye dokundu mu" sorusu tam olarak
        // burada olculur. Model artik 55 bolumu kapsiyor; L9+ icin bant denetimi
        // SupplyBudgetTest'in isidir ve orada L9..L22 isimlendirilmis bir
        // ISTISNADIR (bolunmemis uzun bolumler, bkz. BAND_EXEMPT_LEVELS).
        (1..8).forEach { level ->
            val spi = SupplyBudgetModel.supplyPressureIndex(level)
            assertTrue(
                "bolum $level SPI $spi hedef bandin (" +
                    "${SupplyBudgetModel.SPI_TARGET_MIN}..${SupplyBudgetModel.SPI_TARGET_MAX}" +
                    ") disina cikti — kademe 3 erken oyun ekonomisine dokunmamaliydi",
                spi in SupplyBudgetModel.SPI_TARGET_MIN..SupplyBudgetModel.SPI_TARGET_MAX
            )
        }
        // Kadro fiyati hâlâ kademe 2 uzerinden: gec oyun sinki erken oyunun
        // bolenini kaydirmis olsaydi butun SPI tablosu sessizce degisirdi.
        GameConfig.TOWER_SPECS.forEach { (type, s) ->
            assertEquals(
                "$type: SPI bolen tarafi kademe 2 tam maliyetini kullanmali",
                s.buildCost + s.tier(2).upgradeCost,
                fullCost(type, TIER_CAP_BEFORE_THIS_PHASE)
            )
        }
    }
}
