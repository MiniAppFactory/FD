package com.miniappfactory.frontlinedefender.waves

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.game.model.WaveMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ARKETIP ETIKETI SAHADA OKUNABILIR OLMALI.
 *
 * ---------------------------------------------------------------------------
 * NEDEN BU TEST VAR — olculen hata
 * ---------------------------------------------------------------------------
 * `LATE_PLAN` her gec-perde bolumune bir KARAR harfi verir (K kalabalik,
 * S hiz, Z zirh, C karsi-koyma, M karma, B boss). 2026-08-20 olcumu bu
 * harflerin NOMINAL oldugunu gosterdi: Act IV/V'in FINAL dalgalari, etiket ne
 * olursa olsun, %76-88 oraninda Kalkanli Er'den olusuyordu.
 *
 *   L51 'K' (kalabalik)   finali %88 kalkanli
 *   L50 'S' (hiz)         finali %86 kalkanli
 *   L52 'Z' (zirh)        finali %79 kalkanli
 *   L54 'C' (karsi-koyma) finali %88 kalkanli   <- yalnizca bu DOGRUYDU
 *
 * Suclu `densifyToBodyCap`ti: K-4 govde tavanina uyarken hafiflerden aldigi
 * payin %90'ini KOSULSUZ Kalkanli Er'e yukluyordu ve bir tavan yoktu, yani
 * tavana ne kadar cok dayanilirsa profil o kadar saf kalkanliya yakinsiyordu.
 * Bolum TOPLAMINDA bu gorunmuyordu (en yuksek %54) — cunku cokme yalnizca EN
 * AGIR 1-3 dalgada oluyor, yani oyuncunun gercekten zorlandigi ve hatirladigi
 * dalgalarda. Bu yuzden asil kapi (D-1) dalga bazinda olcer, bolum bazinda degil.
 *
 * ---------------------------------------------------------------------------
 * TEST ELLE YAZILMIS TABLODAN DEGIL, URETILMIS TABLODAN BESLENIR
 * ---------------------------------------------------------------------------
 * Bu depoda elle yazilmis sayi dizileri defalarca sessizce bayatladi
 * (`WaveMetrics.AEHP`, `TIGHTENED_WAVE_KILL_SUPPLY`). Bu yuzden burada tek bir
 * sabit dusman sayisi yok: her sey `WaveDefinitions.wavesFor` ciktisindan
 * SAYILIR, esikler ise KURALDIR.
 */
class ArchetypeReadabilityTest {

    /** Govde tipleri — boss (COMMAND_TANK) kasitli olarak disarida. */
    private val bodyTypes = listOf(
        EnemyType.INFANTRY, EnemyType.FAST_SOLDIER, EnemyType.SHIELDED_TROOPER,
        EnemyType.ARMORED_VEHICLE, EnemyType.TANK
    )

    private val heavyTypes = listOf(EnemyType.ARMORED_VEHICLE, EnemyType.TANK)

    private fun generatedLevels(): List<Int> =
        (1..GameConfig.CAMPAIGN_LEVEL_COUNT).filter { WaveDefinitions.archetypeOfOrNull(it) != null }

    /** Bolumun govde paylari (boss haric), tip -> oran. */
    private fun bodyShares(level: Int): Map<EnemyType, Double> {
        val counts = bodyTypes.associateWith { t ->
            WaveDefinitions.wavesFor(level).sumOf { w -> w.spawns.count { it.enemyType == t } }
        }
        val total = counts.values.sum().toDouble()
        return counts.mapValues { (_, c) -> c / total }
    }

    /** Bolumun AEHP paylari — "tehdit agirligi" ekseni. Boss DAHIL. */
    private fun aehpShares(level: Int): Map<EnemyType, Double> {
        val sums = EnemyType.values().associateWith { t ->
            WaveDefinitions.wavesFor(level).sumOf { w ->
                w.spawns.count { it.enemyType == t } * WaveMetrics.AEHP.getValue(t).toDouble()
            }
        }
        val total = sums.values.sum()
        return sums.mapValues { (_, v) -> v / total }
    }

    // =====================================================================
    // D-1 — DALGA BAZINDA TEK-TIP TAVANI  (asil kapi)
    // =====================================================================

    /**
     * Yalnizca URETILMIS bolumler (L23-55) kapsamda: L1-22 elle kalibre
     * edilmis ve YAYINLANMIS tablodur; oradaki %100 tek-tipli dalgalar
     * (L1-W1 = 6 piyade) kasitli ogretici dalgalaridir, hata degil.
     */
    @Test
    fun noSingleEnemyTypeDominatesAGeneratedWave() {
        val violations = ArrayList<String>()
        for (level in generatedLevels()) {
            WaveDefinitions.wavesFor(level).forEach { wave ->
                val bodies = wave.spawns.count { it.enemyType in bodyTypes }
                if (bodies == 0) return@forEach
                bodyTypes.forEach { t ->
                    val share = wave.spawns.count { it.enemyType == t } / bodies.toDouble()
                    if (share > WaveDefinitions.MAX_TYPE_SHARE_PER_WAVE) {
                        violations += "L$level-W${wave.waveIndex} ${t.name} %.1f".format(share * 100)
                    }
                }
            }
        }
        assertTrue(
            "Tek bir dusman tipi bir dalganin govdelerinin " +
                "%${(WaveDefinitions.MAX_TYPE_SHARE_PER_WAVE * 100).toInt()}'inden fazlasini " +
                "olusturuyor — o dalgada arketip etiketi okunmaz ve cevap tek bir kuleye " +
                "kilitlenir. Suclu genellikle `densifyToBodyCap`: govde tavanina uyarken " +
                "agirligi tek tipe yigiyor. Ihlaller (bolum-dalga tip pay%): " +
                violations.joinToString("; "),
            violations.isEmpty()
        )
    }

    // =====================================================================
    // D-2 — ETIKET YALAN SOYLEYEMEZ
    // =====================================================================

    /**
     * Her arketipin IMZA olcutu bolum genelinde gercekten one cikmali.
     *
     * Eksen secimi arketipe gore degisiyor ve bu KASITLI:
     *  · K/S/C hafif-govde kimlikleridir -> GOVDE PAYI ile olculur.
     *  · Z bir AGIRLIK kimligidir -> AEHP PAYI ile olculur. Zirhli/tank pahali
     *    oldugu icin govde SAYISI olarak asla en kalabalik tip olamaz; "zirh
     *    bolumu" demek "tehdidin agirligi zirhtan gelir" demektir.
     *  · M/B kasitli olarak DENGELIdir -> hicbir tipin one cikmamasi ile olculur.
     *
     * Esikler kural, ezber degil: imza tipi hem en yuksek pay olmali hem de
     * bir tabandan yuksek olmali; aksi halde "en yuksek" bir yuvarlama kazasi
     * olabilir.
     */
    @Test
    fun everyArchetypeLabelMatchesTheGeneratedDistribution() {
        val problems = ArrayList<String>()
        for (level in generatedLevels()) {
            val ark = WaveDefinitions.archetypeOfOrNull(level)!!
            val body = bodyShares(level)
            val aehp = aehpShares(level)
            val topBody = bodyTypes.maxByOrNull { body.getValue(it) }!!

            fun requireTop(t: EnemyType, floor: Double, adi: String) {
                if (topBody != t) {
                    problems += "L$level '$ark' ($adi): en kalabalik tip ${topBody.name} " +
                        "(%.1f%%) ama imza tipi ${t.name} yalnizca %.1f%%"
                            .format(body.getValue(topBody) * 100, body.getValue(t) * 100)
                }
                if (body.getValue(t) < floor) {
                    problems += "L$level '$ark' ($adi): imza tipi ${t.name} %.1f%% — taban %.0f%%"
                        .format(body.getValue(t) * 100, floor * 100)
                }
            }

            when (ark) {
                'K' -> requireTop(EnemyType.INFANTRY, 0.30, "kalabalik")
                'S' -> requireTop(EnemyType.FAST_SOLDIER, 0.28, "hiz")
                'C' -> requireTop(EnemyType.SHIELDED_TROOPER, 0.35, "karsi-koyma")
                'Z' -> {
                    val heavy = heavyTypes.sumOf { aehp.getValue(it) }
                    val shield = aehp.getValue(EnemyType.SHIELDED_TROOPER)
                    if (heavy < 0.55 || heavy - shield < 0.15) {
                        problems += "L$level 'Z' (zirh): agir aile AEHP payi %.1f%%, kalkanli %.1f%%"
                            .format(heavy * 100, shield * 100) +
                            " — beklenen agir >= %55 ve kalkanliya en az 15 puan fark"
                    }
                }
                else -> {
                    // M (karma) ve B (boss): hicbir tip one cikmamali. %40 esigi
                    // "esit dagilim" (%20) ile sert tavan (%55) arasinin ortasi.
                    if (body.getValue(topBody) > 0.40) {
                        problems += "L$level '$ark' (dengeli olmali): ${topBody.name} " +
                            "govdelerin %.1f%%'i".format(body.getValue(topBody) * 100)
                    }
                }
            }
        }
        assertTrue(
            "Arketip etiketi ile uretilen dagilim uyusmuyor. Etiket bir SUS degil, " +
                "bolumun sordugu sorudur; uyusmuyorsa duzeltilecek olan `LATE_PLAN` " +
                "satiri ya da `densifyBlend` karisimidir, bu test DEGIL. " +
                problems.joinToString(" | "),
            problems.isEmpty()
        )
    }

    // =====================================================================
    // D-3 — ETIKETLER BIRBIRINDEN AYRISMALI
    // =====================================================================

    /**
     * "Her bolum kendi esigini gecti" yetmez: oyuncu bolumleri BIRBIRIYLE
     * karsilastirir. Bir perde icinde 'M' bolumu 'C' bolumunden daha kalkanli
     * ise 'C' etiketi ters donmus demektir — olculen hata TAM OLARAK buydu
     * (Act IV'te L38 'M' %44,2 kalkanli iken L35 'C' %43,4 idi).
     *
     * Ayrim yalnizca KALKANLI ekseninde zorlanir: 'C' bu tipin bolumudur ve
     * cokmenin yasandigi eksen budur. Agir eksende 'M' ile 'Z'nin ortusmesi
     * mesrudur — "karma" tanimi geregi agir tasiyabilir.
     */
    @Test
    fun counterPlayLevelsAreTheMostShieldHeavyOfTheirAct() {
        val byAct = generatedLevels().groupBy { GameConfig.levelSpec(it).act }
        val problems = ArrayList<String>()
        byAct.forEach { (act, levels) ->
            val shield = levels.associateWith { bodyShares(it).getValue(EnemyType.SHIELDED_TROOPER) }
            val counter = levels.filter { WaveDefinitions.archetypeOfOrNull(it) == 'C' }
            if (counter.isEmpty()) return@forEach
            val weakestCounter = counter.minByOrNull { shield.getValue(it) }!!
            val others = levels.filter { it !in counter }
            val strongestOther = others.maxByOrNull { shield.getValue(it) } ?: return@forEach
            if (shield.getValue(strongestOther) >= shield.getValue(weakestCounter)) {
                problems += "Perde $act: L$strongestOther " +
                    "'${WaveDefinitions.archetypeOfOrNull(strongestOther)}' %.1f%% kalkanli, "
                        .format(shield.getValue(strongestOther) * 100) +
                    "ama 'C' bolumu L$weakestCounter yalnizca %.1f%%"
                        .format(shield.getValue(weakestCounter) * 100)
            }
        }
        assertTrue(
            "Bir perde icinde 'C' olmayan bir bolum 'C' bolumunden daha kalkanli — " +
                "etiket ters donmus. " + problems.joinToString(" | "),
            problems.isEmpty()
        )
    }

    // =====================================================================
    // Kapsam + yontem kaniti
    // =====================================================================

    /**
     * KAPSAM: 55 bolumun tamami siniflandirilmis olmali. Bir bolum ne elle
     * yazilmis ne uretilmis sayilirsa yukaridaki kapilarin hicbiri onu gormez
     * ve test SESSIZCE bosalir.
     */
    @Test
    fun allFiftyFiveLevelsAreClassifiedAsHandwrittenOrGenerated() {
        val generated = generatedLevels()
        val handwritten = (1..GameConfig.CAMPAIGN_LEVEL_COUNT).filter { it !in generated }
        assertEquals(
            "Elle yazilmis bolumler tam olarak L1..L${WaveDefinitions.HANDWRITTEN_LEVEL_COUNT} olmali",
            (1..WaveDefinitions.HANDWRITTEN_LEVEL_COUNT).toList(),
            handwritten
        )
        assertEquals(
            "Uretilmis bolum sayisi",
            GameConfig.CAMPAIGN_LEVEL_COUNT - WaveDefinitions.HANDWRITTEN_LEVEL_COUNT,
            generated.size
        )
        assertTrue(
            "Her uretilmis bolumun arketipi bilinen bir harf olmali",
            generated.all {
                WaveDefinitions.archetypeOfOrNull(it) in listOf('K', 'S', 'Z', 'C', 'M', 'B')
            }
        )
    }

    /**
     * YONTEM KANITI. Bos bir test "ihlal yok" derken kendisi kirilmis olabilir.
     * Bu test esigin GERCEKTEN ayirt ettigini gosterir: bilerek carpik bir
     * dagilim yakalanmali, dengeli olan yakalanmamali.
     */
    @Test
    fun theDominanceThresholdActuallySeparatesSkewedFromBalanced() {
        val skewed = List(50) { EnemyType.SHIELDED_TROOPER } + List(6) { EnemyType.INFANTRY }
        assertTrue(
            "Esik carpik dalgayi (%89 kalkanli) yakalayamiyor — olcut anlamsiz",
            skewed.count { it == EnemyType.SHIELDED_TROOPER } / skewed.size.toDouble() >
                WaveDefinitions.MAX_TYPE_SHARE_PER_WAVE
        )
        val balanced = List(20) { EnemyType.SHIELDED_TROOPER } + List(36) { EnemyType.INFANTRY }
        assertTrue(
            "Esik dengeli dalgayi da ihlal sayiyor — olcut fazla dar",
            balanced.count { it == EnemyType.SHIELDED_TROOPER } / balanced.size.toDouble() <=
                WaveDefinitions.MAX_TYPE_SHARE_PER_WAVE
        )
    }
}
