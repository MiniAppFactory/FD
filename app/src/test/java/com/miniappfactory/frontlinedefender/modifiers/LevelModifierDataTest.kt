package com.miniappfactory.frontlinedefender.modifiers

import com.miniappfactory.frontlinedefender.balance.CampaignSimulator
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.LevelModifiers
import com.miniappfactory.frontlinedefender.game.model.GameConfig.TowerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * BOLUM DEGISTIRICILERI — VERI VE DENGE KAPISI
 * ===========================================================================
 *
 * CAMPAIGN_55.md'nin kendi tespiti: kampanya 55 bolum ama yalnizca 11 savas
 * alani var, ve **cesitlilik renkten degil KURAL DEGISIKLIGINDEN gelmeli**.
 * Bu tur uc degistiriciyi ayaga kaldirir:
 *
 *  · **KISITLI KADRO** (`allowedTowers`) — bu harekatta bazi kule tipleri yok.
 *  · **MEVZI TAVANI** (`maxTowers`) — ayni anda en fazla N kule.
 *  · **DONMUS MEVZI** (`buildLockedDuringWave`) — dalga basladiktan sonra yeni
 *    kule kurulamaz.
 *
 * Bu dosya UC seyi olcer:
 *  a) tablonun tasarim kurallarina uydugunu (ogretici bandi dokunulmamis,
 *     kisit gercekten BAGLAYICI, tek bolumde tek kural),
 *  b) degistirici TASIMAYAN bolumlerin davranisinin BIREBIR degismedigini,
 *  c) degistirici tasiyan bolumlerin hâlâ GECILEBILIR oldugunu — ve
 *     `CampaignSimulator`in kisiti GERCEKTEN tanidigini. Simulator kisiti
 *     gormezse `CampaignSolvabilityAllLevelsTest` yalan soyler: 55/55 yesil
 *     kalir ama olctugu sey oyuncunun oynadigi bolum degildir.
 */
class LevelModifierDataTest {

    private val levels = 1..GameConfig.CAMPAIGN_LEVEL_COUNT

    // =======================================================================
    // a) TABLO — tasarim kurallari
    // =======================================================================

    /**
     * Degistirici tasiyan bolumler kumesi TABLODAN ibaret. Baska bir bolume
     * kural sizmasi (orn. varsayilan degerin yanlislikla degismesi) 49 bolumun
     * davranisini sessizce degistirirdi.
     */
    @Test
    fun `yalnizca tabloda listelenen bolumler degistirici tasir`() {
        val declared = GameConfig.LEVEL_MODIFIERS.keys
        val carrying = levels.filter { !GameConfig.levelSpec(it).modifiers.isEmpty }.toSet()
        assertEquals(
            "degistirici tasiyan bolumler tablodan farkli",
            declared,
            carrying
        )
    }

    /** Bu turda alti bolum: sistemi ayaga kaldirmak, kampanyaya yaymak DEGIL. */
    @Test
    fun `bu turda degistirici AZ SAYIDA bolume kondu`() {
        val n = GameConfig.LEVEL_MODIFIERS.size
        assertTrue("degistirici sayisi 4-6 bandinin disinda: $n", n in 4..6)
    }

    /**
     * **OGRETICI BANDI (L1-L8) DOKUNULMAZ.** Cekirdek dongu once oturur;
     * oyuncu kule kurmayi, satmayi ve rolleri ogrenmeden uzerine kural kisiti
     * binmez (LEVEL_DESIGN D: "oyuncunun ogrenmedigi mekanik zorunlu basari
     * kosulu olamaz").
     */
    @Test
    fun `ogretici bandi L1 L8 degistirici tasimaz`() {
        val touched = (1..8).filter { !GameConfig.levelSpec(it).modifiers.isEmpty }
        assertTrue("ogretici bandinda degistirici var: $touched", touched.isEmpty())
    }

    /**
     * **BIR BOLUM, BIR KURAL.** Bu turda kombinasyon YOK: iki yeni kural ayni
     * anda ogretilmez ve tek degisken degistirilerek etkisi olculebilir kalir.
     */
    @Test
    fun `hicbir bolum ayni anda birden fazla degistirici tasimaz`() {
        val combos = GameConfig.LEVEL_MODIFIERS.filterValues { m ->
            listOf(
                m.allowedTowers != null,
                m.maxTowers != null,
                m.buildLockedDuringWave
            ).count { it } > 1
        }.keys
        assertTrue("bu turda kombinasyon olmamali: $combos", combos.isEmpty())
    }

    /**
     * Her degistirici ILK gorundugu bolumde YALNIZ durur ve ikinci gorunumu en
     * az bes bolum sonra gelir — ayni kural arka arkaya iki bolumde "yeni sey"
     * gibi hissettirilmez.
     */
    @Test
    fun `ayni degistiricinin iki gorunumu arasinda en az bes bolum var`() {
        fun gapsOf(predicate: (LevelModifiers) -> Boolean): List<Int> {
            val ids = GameConfig.LEVEL_MODIFIERS.filterValues(predicate).keys.sorted()
            return ids.zipWithNext { a, b -> b - a }
        }
        val tight = buildList {
            addAll(gapsOf { it.allowedTowers != null })
            addAll(gapsOf { it.maxTowers != null })
            addAll(gapsOf { it.buildLockedDuringWave })
        }.filter { it < 5 }
        assertTrue("ayni kural bes bolumden yakin tekrar ediyor: $tight", tight.isEmpty())
    }

    /**
     * **KISIT GERCEKTEN DARALTMALI.** `allowedTowers` o bolumde ZATEN acik olan
     * kulelerin bir ALT kumesi olmali: kilitli bir kuleyi "izinli" ilan etmek
     * kimseye bir sey vermez ama tabloya yanlis bir niyet yazar. Ustelik kisit
     * en az bir tipi GERCEKTEN kaldirmali, yoksa dekoratif olur.
     */
    @Test
    fun `kisitli kadro acik kule kumesini gercekten daraltir`() {
        GameConfig.LEVEL_MODIFIERS.forEach { (level, mods) ->
            val allowed = mods.allowedTowers ?: return@forEach
            val unlocked = GameConfig.unlockedTowers(level).toSet()
            assertTrue(
                "L$level: izinli kume acik kulelerin alt kumesi degil " +
                    "(izinli=$allowed acik=$unlocked)",
                unlocked.containsAll(allowed)
            )
            assertTrue(
                "L$level: kisit hicbir kuleyi kaldirmiyor, yani dekoratif",
                allowed.size < unlocked.size
            )
        }
    }

    /**
     * Kisit ne kadar daraltirsa daraltsin, oyuncunun elinde **en az iki HASAR
     * VEREN** tip kalmali. Tek saldiri tipine dusen bir bolum karar degil
     * ezber uretir (`everyLevelHasMoreThanOneViableAnswer` ile ayni doktrin).
     */
    @Test
    fun `kisitli kadroda en az iki saldiri kulesi kalir`() {
        GameConfig.LEVEL_MODIFIERS.keys.forEach { level ->
            val attack = GameConfig.buildableTowers(level).filter { it != TowerType.SLOW }
            assertTrue(
                "L$level: geriye $attack kaldi — tek saldiri tipi ezber uretir",
                attack.size >= 2
            )
        }
    }

    /**
     * **TAVAN BAGLAYICI OLMALI.** Tavan, o bolumdeki ACIK PAD sayisindan kucuk
     * olmali; degilse hicbir sey degistirmez ve oyuncuya bos yere bir rozet
     * gosterilir. Alt sinir da var: tasarlanan kadro buyuklugunun cok altina
     * inen bir tavan bolumu cozulemez yapar.
     */
    @Test
    fun `mevzi tavani acik pad sayisindan kucuk ve makul`() {
        GameConfig.LEVEL_MODIFIERS.forEach { (level, mods) ->
            val cap = mods.maxTowers ?: return@forEach
            val open = GameConfig.openPadCount(level)
            assertTrue(
                "L$level: tavan $cap, acik pad $open — tavan hicbir seyi kisitlamiyor",
                cap < open
            )
            assertTrue("L$level: tavan $cap fazla dusuk", cap >= 4)
        }
    }

    // =======================================================================
    // b) REGRESYON — degistirici TASIMAYAN bolumler
    // =======================================================================

    /**
     * **KISITSIZ BOLUMLERDE DAVRANIS BIREBIR AYNI.** 49 bolumun spec'i
     * [LevelModifiers.NONE] tasir, yani `allowsTowerType` her tipe evet der,
     * tavan yok ve insa penceresi hic kapanmaz.
     */
    @Test
    fun `degistiricisiz bolumlerde kural yuzeyi tamamen notr`() {
        levels.filter { it !in GameConfig.LEVEL_MODIFIERS }.forEach { level ->
            val spec = GameConfig.levelSpec(level)
            assertEquals("L$level bos degistirici bekleniyordu", LevelModifiers.NONE, spec.modifiers)
            assertNull("L$level tavan tasiyor", spec.maxTowers)
            assertTrue("L$level insa penceresi kapaniyor", !spec.buildLockedDuringWave)
            TowerType.values().forEach {
                assertTrue("L$level $it tipini reddediyor", spec.allowsTowerType(it))
            }
            assertEquals(
                "L$level kurulabilir kule kumesi kilit kumesinden farkli",
                GameConfig.unlockedTowers(level),
                GameConfig.buildableTowers(level)
            )
        }
    }

    /**
     * Simulator tarafinin ayni regresyon kapisi: degistiricisiz bir bolumu
     * "acikca NONE" ile kosmak, gercek spec ile kosmakla BIREBIR ayni sonucu
     * vermeli. Sonuc esitligi (gecti/sizinti/kadro/kalan Tedarik) modelin
     * kisit kodundan hic etkilenmedigini gosterir.
     */
    @Test
    fun `degistiricisiz bolumlerde simulator sonucu BIREBIR ayni`() {
        val sample = listOf(9, 14, 20, 26, 33, 40, 47, 54)
        sample.forEach { level ->
            CampaignSimulator.CAREFUL_STYLES.forEach { style ->
                val real = CampaignSimulator.play(CampaignSimulator.LevelModel(level), style)
                val neutral = CampaignSimulator.play(
                    CampaignSimulator.LevelModel(level, LevelModifiers.NONE),
                    style
                )
                assertEquals("L$level/$style gecti", real.cleared, neutral.cleared)
                assertEquals("L$level/$style sizinti", real.leaked, neutral.leaked)
                assertEquals("L$level/$style kadro", real.roster, neutral.roster)
                assertEquals("L$level/$style kalan Tedarik", real.leftoverSupply, neutral.leftoverSupply)
            }
        }
    }

    // =======================================================================
    // c) SIMULATOR KISITI TANIYOR MU + bolum hâlâ gecilebilir mi
    // =======================================================================

    /** Model kisiti spec'ten OKUYOR mu — sessizce dusurulmus bir alan olmasin. */
    @Test
    fun `simulator modeli degistiricileri spec'ten okur`() {
        GameConfig.LEVEL_MODIFIERS.forEach { (level, mods) ->
            val model = CampaignSimulator.LevelModel(level)
            assertEquals("L$level tavan", mods.maxTowers, model.maxTowers)
            assertEquals(
                "L$level insa kilidi",
                mods.buildLockedDuringWave,
                model.buildLockedDuringWave
            )
            assertEquals(
                "L$level kurulabilir kule kumesi",
                GameConfig.buildableTowers(level),
                model.unlockedTowers
            )
        }
    }

    /**
     * KISITLI KADRO simulasyonda da BAGLAYICI: yasakli tip hicbir davranisin
     * kadrosunda gorunmemeli. Kadro dizesi ("2xAA3+5xMG3") kule tipini kisa
     * kodla tasir.
     */
    @Test
    fun `kisitli bolumlerde yasakli kule simulasyonda hic kurulmaz`() {
        val short = mapOf(
            TowerType.MACHINE_GUN to "MG",
            TowerType.CANNON to "CN",
            TowerType.ANTI_ARMOR to "AA",
            TowerType.SLOW to "FR"
        )
        GameConfig.LEVEL_MODIFIERS.forEach { (level, mods) ->
            val allowed = mods.allowedTowers ?: return@forEach
            val banned = TowerType.values().filter { it !in allowed }
            CampaignSimulator.allOutcomes(level).forEach { outcome ->
                banned.forEach { type ->
                    assertTrue(
                        "L$level/${outcome.style}: yasakli ${type.name} kadroda " +
                            "(${outcome.roster})",
                        !outcome.roster.contains(short.getValue(type))
                    )
                }
            }
        }
    }

    /** MEVZI TAVANI simulasyonda da BAGLAYICI: hicbir kadro tavani asmamali. */
    @Test
    fun `tavanli bolumlerde simulasyon kadrosu tavani asmaz`() {
        GameConfig.LEVEL_MODIFIERS.forEach { (level, mods) ->
            val cap = mods.maxTowers ?: return@forEach
            CampaignSimulator.allOutcomes(level).forEach { outcome ->
                val built = towerCountOf(outcome.roster)
                assertTrue(
                    "L$level/${outcome.style}: $built kule kuruldu, tavan $cap " +
                        "(${outcome.roster})",
                    built <= cap
                )
            }
        }
    }

    /**
     * **DONMUS MEVZI GERCEKTEN ISIRIYOR MU?** Tavan ve kadro kisitlari kadroya
     * bakarak dogrulanabilir, insa penceresi ise dogrulanamaz — bu yuzden ayni
     * bolum kisitli ve kisitsiz kosturulup FARK olculur. Fark yoksa kural
     * kodda var ama oynanista yok demektir.
     */
    @Test
    fun `donmus mevzi bolumlerinde kisitli kosu kisitsizdan FARKLI`() {
        val locked = GameConfig.LEVEL_MODIFIERS.filterValues { it.buildLockedDuringWave }.keys
        assertTrue("donmus mevzi bolumu yok", locked.isNotEmpty())
        locked.forEach { level ->
            val differs = CampaignSimulator.CAREFUL_STYLES.any { style ->
                val withLock = CampaignSimulator.play(CampaignSimulator.LevelModel(level), style)
                val without = CampaignSimulator.play(
                    CampaignSimulator.LevelModel(level, LevelModifiers.NONE),
                    style
                )
                withLock.roster != without.roster || withLock.leaked != without.leaked
            }
            assertTrue(
                "L$level: insa kilidi hicbir davranisin oynanisini degistirmiyor — " +
                    "kural kodda var, sahada yok",
                differs
            )
        }
    }

    /**
     * **COZULEBILIRLIK.** Degistirici tasiyan her bolum, meta yukseltme SIFIR
     * ve guclendirici YOKken hâlâ gecilebilmeli; ustelik TEK bir oynanis
     * bicimiyle degil (aksi halde bolum cozulebilir degil EZBERLENEBILIR olur —
     * `CampaignSolvabilityAllLevelsTest` ile ayni doktrin).
     */
    @Test
    fun `degistiricili her bolum hâlâ birden fazla oynanis bicimiyle gecilir`() {
        GameConfig.LEVEL_MODIFIERS.keys.sorted().forEach { level ->
            val outcomes = CampaignSimulator.allOutcomes(level)
            val clearing = outcomes.filter { it.cleared }
            assertTrue(
                "L$level degistiriciyle gecilemiyor (en iyi kosu " +
                    "${outcomes.maxOf { it.wavesCleared }}/${outcomes.first().totalWaves} dalga)",
                clearing.isNotEmpty()
            )
            assertTrue(
                "L$level yalnizca ${clearing.size} davranisla geciliyor — ezberlenebilir",
                clearing.size >= 2
            )
        }
    }

    /**
     * Degistirici bir bolumu bedava YAPMAMALI: hicbir sey kurmayan oyuncu
     * degistiricili bolumleri de kaybetmeli.
     */
    @Test
    fun `degistiricili bolumler hicbir sey yapmadan gecilemez`() {
        GameConfig.LEVEL_MODIFIERS.keys.forEach { level ->
            val idle = CampaignSimulator.play(
                CampaignSimulator.LevelModel(level),
                CampaignSimulator.Playstyle.IDLE
            )
            assertTrue("L$level hic kule kurulmadan geciliyor", !idle.cleared)
        }
    }

    /** "2xAA3+5xMG3" -> 7. Kadro dizesindeki toplam kule sayisi. */
    private fun towerCountOf(roster: String): Int {
        if (roster == "-") return 0
        return roster.split("+").sumOf { part ->
            val n = part.substringBefore('x').toIntOrNull()
            assertNotNull("kadro dizesi cozulemedi: $roster", n)
            n ?: 0
        }
    }
}
