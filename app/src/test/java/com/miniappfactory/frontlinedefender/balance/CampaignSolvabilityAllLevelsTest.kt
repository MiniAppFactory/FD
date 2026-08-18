package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * COZULEBILIRLIK KAPISI — **55 BOLUMUN TAMAMI** (yayin kapisi)
 * ===========================================================================
 *
 * `docs/CAMPAIGN_55.md` V4 ve `docs/LEVEL_DESIGN.md` §G.4 bunu acikca yayin
 * kapisi olarak isaretliyor: *"masa basi hesabi YETMEZ"*. Onceki durumda
 * `CampaignSolvabilityTest` yalnizca L1..L4'u kosuyordu, yani 55 bolumun
 * 51'inin gercekten gecilebilir oldugu HIC dogrulanmamisti. Gecilemeyen bir
 * bolum kampanyayi orada bitirir.
 *
 * Olcum [CampaignSimulator] ile yapilir: cok kuleli kadro, gercek fiyatlar,
 * gercek menziller, gercek kademe/kule kilitleri, gercek pad kisiti, gercek
 * rota catallanmasi, mermi ucus suresi ve dort ayri carpma davranisi.
 * Meta yukseltme SIFIR, guclendirici YOK.
 *
 * Testin cikardigi tablo `System.out`a basilir ve
 * `app/build/test-results/testDebugUnitTest` altindaki XML raporda
 * `<system-out>` olarak durur — rapor kaniti oradan okunur.
 */
class CampaignSolvabilityAllLevelsTest {

    private fun table(): List<CampaignSimulator.Outcome> =
        (1..GameConfig.CAMPAIGN_LEVEL_COUNT).map { CampaignSimulator.bestOutcome(it) }

    /**
     * **ANA KAPI.** Dikkatli oynayan, meta yukseltmesi olmayan, guclendirici
     * kullanmayan bir oyuncu 55 bolumun HEPSINI gecebilmeli.
     *
     * Bir bolum kirmizi ise testi GEVSETMEK yasak: `GameConfig.CAMPAIGN` ya da
     * `WaveDefinitions` duzeltilir.
     */
    @Test
    fun everyCampaignLevelIsClearableWithZeroMetaUpgradesAndNoBoosters() {
        val outcomes = table()
        println(renderTable(outcomes))
        val failed = outcomes.filterNot { it.cleared }
        assertTrue(
            "Gecilemeyen bolum(ler): " + failed.joinToString(" · ") {
                "L${it.levelId} (${it.wavesCleared}/${it.totalWaves} dalga, " +
                    "${it.leaked} sizinti, kadro ${it.roster})"
            },
            failed.isEmpty()
        )
    }

    /** Hicbir sey yapmayan oyuncu HER bolumu kaybetmeli: bolumun bir oynanisi olmali. */
    @Test
    fun doingNothingLosesEveryCampaignLevel() {
        val survived = (1..GameConfig.CAMPAIGN_LEVEL_COUNT).filter { level ->
            CampaignSimulator.play(
                CampaignSimulator.LevelModel(level),
                CampaignSimulator.Playstyle.IDLE
            ).cleared
        }
        assertTrue(
            "hic kule kurulmadan gecilen bolum(ler): $survived — bu bolumlerin " +
                "bir oynanisi yok",
            survived.isEmpty()
        )
    }

    /**
     * Her bolumde YOLU GOREN en az bir pad bulunmali. Aksi halde bolum
     * kurulamaz: oyuncu Tedarigi hicbir seye ates etmeyen bir kuleye yatirir.
     */
    @Test
    fun everyLevelHasPadsThatActuallyReachTheActiveRoute() {
        val broken = ArrayList<String>()
        for (level in 1..GameConfig.CAMPAIGN_LEVEL_COUNT) {
            val model = CampaignSimulator.LevelModel(level)
            val maxRange = model.unlockedTowers
                .maxOf { GameConfig.TOWER_SPECS.getValue(it).tier(1).range }
            val effective = model.pads.count { model.cover(it, maxRange) > 1f }
            if (effective < 3) broken.add("L$level -> $effective etkin pad")
        }
        assertTrue(
            "bolumlerin etkin pad sayisi 3'un altinda: $broken",
            broken.isEmpty()
        )
    }

    /**
     * **HER BOLUMUN BIRDEN FAZLA CEVABI OLMALI.**
     *
     * Bir bolum yalnizca TEK bir oynanis bicimiyle gecilebiliyorsa o bolum
     * cozulebilir degil EZBERLENEBILIRdir: oyuncu dogru kadroyu bulana kadar
     * korlemesine dener. [CampaignSimulator.CAREFUL_STYLES] alti farkli
     * makul oyuncu davranisi; en az ikisinin gecmesi, bolumde gercek bir
     * KARAR alani oldugunun kanitidir.
     *
     * NOT — burada KASTEN kalan can karsilastirmasi YAPILMIYOR. Denenen sey
     * "portfoydeki en iyi kosu" oldugu icin kalan can, bolumun zorlugundan cok
     * hangi davranisin o tahtaya denk geldigine bagli dalgalanir; bolum bolum
     * bir zorluk egrisi kapisi olarak kullanmak yanlis pozitif uretir. Zorluk
     * egrisi `CampaignShapeReportTool` tablosunda OLCULUR, burada kapilanmaz.
     */
    @Test
    fun everyLevelHasMoreThanOneViableAnswer() {
        val fragile = (1..GameConfig.CAMPAIGN_LEVEL_COUNT).mapNotNull { level ->
            val clearing = CampaignSimulator.allOutcomes(level).count { it.cleared }
            if (clearing < 2) "L$level ($clearing davranis)" else null
        }
        assertTrue(
            "yalnizca tek bir oynanis bicimiyle gecilebilen bolum(ler): $fragile — " +
                "bu bolumler cozulebilir degil ezberlenebilir",
            fragile.isEmpty()
        )
    }

    // =======================================================================
    // Rapor
    // =======================================================================

    private fun renderTable(outcomes: List<CampaignSimulator.Outcome>): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== 55 BOLUM COZULEBILIRLIK TABLOSU (meta 0, guclendirici yok) ===")
        sb.appendLine(
            "L    act map dalga  sure   pad  gecti can/maks yildiz sizinti  " +
                "davranis        kadro"
        )
        for (o in outcomes) {
            val spec = GameConfig.levelSpec(o.levelId)
            val model = CampaignSimulator.LevelModel(o.levelId)
            sb.appendLine(
                "%-4d %-3d %-3d %-6d %-6s %-4d %-6s %-8s %-6d %-8d %-15s %s".format(
                    o.levelId,
                    spec.act,
                    spec.mapId,
                    o.totalWaves,
                    "%.1fdk".format(o.elapsedSeconds / 60f),
                    model.pads.size,
                    if (o.cleared) "EVET" else "HAYIR",
                    "${o.livesLeft}/${o.maxLives}",
                    o.stars,
                    o.leaked,
                    o.style.name,
                    o.roster
                )
            )
        }
        val cleared = outcomes.count { it.cleared }
        sb.appendLine(
            "--- gecilen $cleared/${outcomes.size} · toplam dalga " +
                "${(1..GameConfig.CAMPAIGN_LEVEL_COUNT).sumOf { WaveDefinitions.waveCount(it) }}" +
                " · toplam sure %.0f dk".format(outcomes.sumOf { it.elapsedSeconds.toDouble() } / 60.0)
        )
        return sb.toString()
    }
}
