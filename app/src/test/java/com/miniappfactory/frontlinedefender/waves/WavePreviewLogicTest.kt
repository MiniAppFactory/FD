package com.miniappfactory.frontlinedefender.waves

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.game.ui.WavePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SIRADAKI DALGA ONIZLEMESI — saf mantik.
 *
 * `WavePreview` bilincli olarak Compose'a bagimsiz bir `object`: butun turetme
 * ve sigdirma karari burada, composable'lar yalnizca ciziyor. Bu sayede
 * Robolectric OLMADAN, duz JVM testinde kosulabiliyor.
 */
class WavePreviewLogicTest {

    private val allLevels = 1..WaveDefinitions.CAMPAIGN_LEVEL_COUNT

    // =======================================================================
    // 1) VERI TURETME — serit WaveDefinitions ile birebir ayni seyi anlatmali
    // =======================================================================

    @Test
    fun everyWaveOfEveryLevelIsPreviewable() {
        for (level in allLevels) {
            val waves = WaveDefinitions.wavesFor(level)
            for (index in waves.indices) {
                assertNotNull(
                    "bolum $level dalga ${index + 1} onizlenemedi",
                    WavePreview.forWave(level, index)
                )
            }
        }
    }

    @Test
    fun perTypeCountsMatchWaveDefinitionsExactly() {
        for (level in allLevels) {
            val waves = WaveDefinitions.wavesFor(level)
            for (index in waves.indices) {
                val expected = waves[index].spawns
                    .groupingBy { it.enemyType }
                    .eachCount()
                val actual = WavePreview.forWave(level, index)!!
                    .entries
                    .associate { it.type to it.count }
                assertEquals("bolum $level dalga ${index + 1} tip/adet dagilimi", expected, actual)
            }
        }
    }

    @Test
    fun totalEnemyCountMatchesSpawnListSize() {
        for (level in allLevels) {
            val waves = WaveDefinitions.wavesFor(level)
            for (index in waves.indices) {
                assertEquals(
                    "bolum $level dalga ${index + 1} toplam dusman sayisi",
                    waves[index].spawns.size,
                    WavePreview.forWave(level, index)!!.totalEnemies
                )
            }
        }
    }

    @Test
    fun eachTypeAppearsAtMostOnce() {
        for (level in allLevels) {
            for (index in WaveDefinitions.wavesFor(level).indices) {
                val types = WavePreview.forWave(level, index)!!.entries.map { it.type }
                assertEquals(
                    "bolum $level dalga ${index + 1} ayni tip iki kart olmus",
                    types.size, types.toSet().size
                )
            }
        }
    }

    // =======================================================================
    // 2) HUD ILE HIZALAMA — serit ve "DALGA n/N" rozeti AYNI dalgayi anlatmali
    // =======================================================================

    @Test
    fun waveNumberAndTotalMatchTheHudBadge() {
        // Motor hazirlik fazina girerken `currentWaveIndex` zaten BASLAYACAK
        // dalgayi gosteriyor; HUD de `waveIndex + 1` basiyor. Serit ile rozet
        // arasinda bir kaydirma olursa oyuncu yanlis dalgaya hazirlanir.
        for (level in allLevels) {
            val waves = WaveDefinitions.wavesFor(level)
            for (index in waves.indices) {
                val info = WavePreview.forWave(level, index)!!
                assertEquals(index + 1, info.waveNumber)
                assertEquals(waves.size, info.totalWaves)
                assertEquals(level, info.levelId)
            }
        }
    }

    // =======================================================================
    // 3) SINIR DURUMLARI — cokme yok, sessiz yanlis veri yok
    // =======================================================================

    @Test
    fun lastWaveOfEveryLevelIsPreviewable() {
        // Motor son dalgayi bitirince indeksi ARTIRMAZ, dogrudan VICTORY'ye
        // gecer. Yani son hazirlik fazinda serit SON dalgayi gosterir.
        for (level in allLevels) {
            val waves = WaveDefinitions.wavesFor(level)
            val info = WavePreview.forWave(level, waves.lastIndex)
            assertNotNull("bolum $level son dalgasi onizlenemedi", info)
            assertEquals(waves.size, info!!.waveNumber)
            assertEquals(info.totalWaves, info.waveNumber)
        }
    }

    @Test
    fun indexPastTheLastWaveReturnsNullInsteadOfCrashing() {
        for (level in allLevels) {
            val size = WaveDefinitions.wavesFor(level).size
            assertNull("bolum $level: son dalganin OTESI null olmali", WavePreview.forWave(level, size))
            assertNull(WavePreview.forWave(level, size + 1))
            assertNull(WavePreview.forWave(level, 9_999))
        }
    }

    @Test
    fun negativeIndexReturnsNull() {
        assertNull(WavePreview.forWave(1, -1))
        assertNull(WavePreview.forWave(1, Int.MIN_VALUE))
    }

    @Test
    fun unknownLevelReturnsNull() {
        assertNull(WavePreview.forWave(0, 0))
        assertNull(WavePreview.forWave(-3, 0))
        assertNull(WavePreview.forWave(WaveDefinitions.CAMPAIGN_LEVEL_COUNT + 1, 0))
    }

    @Test
    fun noCampaignWaveIsEmpty() {
        // `forWave` bos dalgada null doner (serit cizilmez). Bu testin isi o
        // korumanin uretimde ASLA tetiklenmemesi gerektigini kilitlemek:
        // bos bir dalga zaten oynanamaz bir bolum demektir.
        for (level in allLevels) {
            WaveDefinitions.wavesFor(level).forEachIndexed { index, wave ->
                assertTrue(
                    "bolum $level dalga ${index + 1} BOS",
                    wave.spawns.isNotEmpty()
                )
            }
        }
    }

    // =======================================================================
    // 4) ZIRH VE BOSS ISARETLERI — elle liste degil, ENEMY_SPECS'ten turetme
    // =======================================================================

    @Test
    fun armorFlagIsDerivedFromEnemySpecs() {
        for (level in allLevels) {
            for (index in WaveDefinitions.wavesFor(level).indices) {
                WavePreview.forWave(level, index)!!.entries.forEach { entry ->
                    val armor = GameConfig.ENEMY_SPECS[entry.type]!!.armor
                    assertEquals(
                        "bolum $level dalga ${index + 1} ${entry.type} zirh rozeti",
                        armor > 0f, entry.armored
                    )
                }
            }
        }
    }

    @Test
    fun unarmoredTypesNeverGetTheArmorBadge() {
        val info = WavePreview.forWave(1, 3)!! // L01-W4: piyade + kesif eri
        assertTrue(info.entries.isNotEmpty())
        info.entries.forEach { assertFalse(it.armored) }
        assertFalse(info.hasArmor)
        assertFalse(info.hasBoss)
    }

    @Test
    fun everyArmoredEnemyTypeInTheGameIsFlagged() {
        // Zirhli olmasi gereken dort tip. Biri ENEMY_SPECS'te 0 zirha duserse
        // hem denge hem de bu rozet sessizce kaybolurdu.
        val armored = listOf(
            EnemyType.SHIELDED_TROOPER,
            EnemyType.ARMORED_VEHICLE,
            EnemyType.TANK,
            EnemyType.COMMAND_TANK
        )
        armored.forEach {
            assertTrue("$it zirhsiz gorunuyor", GameConfig.ENEMY_SPECS[it]!!.armor > 0f)
        }
        listOf(EnemyType.INFANTRY, EnemyType.FAST_SOLDIER).forEach {
            assertEquals("$it zirhli olmamali", 0f, GameConfig.ENEMY_SPECS[it]!!.armor, 0f)
        }
    }

    @Test
    fun bossFlagIsSetExactlyOnCommandTankWaves() {
        for (level in allLevels) {
            val waves = WaveDefinitions.wavesFor(level)
            waves.forEachIndexed { index, wave ->
                val expected = wave.spawns.any { it.enemyType == EnemyType.COMMAND_TANK }
                val info = WavePreview.forWave(level, index)!!
                assertEquals(
                    "bolum $level dalga ${index + 1} boss isareti",
                    expected, info.hasBoss
                )
                info.entries.forEach { entry ->
                    assertEquals(entry.type == WavePreview.BOSS_TYPE, entry.isBoss)
                }
            }
        }
    }

    @Test
    fun theCampaignActuallyContainsBossWaves() {
        // Boss isareti gorunmez bir ozellik olmasin: kampanyada gercekten boss
        // dalgasi VAR ve ilki Act I finalinde (bolum 11).
        val bossLevels = allLevels.filter { level ->
            WaveDefinitions.wavesFor(level).indices.any { WavePreview.forWave(level, it)!!.hasBoss }
        }
        assertTrue("kampanyada hic boss dalgasi yok", bossLevels.isNotEmpty())
        assertEquals("ilk boss dalgasi bolum 11'de olmali", 11, bossLevels.first())
        assertTrue(
            "kampanya finalinde (22) boss olmali",
            bossLevels.contains(WaveDefinitions.CAMPAIGN_LEVEL_COUNT)
        )
    }

    @Test
    fun entriesAreOrderedLightToHeavySoArmoredChipsGroupOnTheRight() {
        for (level in allLevels) {
            for (index in WaveDefinitions.wavesFor(level).indices) {
                val entries = WavePreview.forWave(level, index)!!.entries
                val armors = entries.map { GameConfig.ENEMY_SPECS[it.type]!!.armor }
                assertEquals(
                    "bolum $level dalga ${index + 1} zirha gore sirali degil",
                    armors.sorted(), armors
                )
                // Boss varsa her zaman EN SAGDA (en yuksek zirh) olmali.
                if (entries.any { it.isBoss }) {
                    assertTrue(
                        "bolum $level dalga ${index + 1} boss karti sonda degil",
                        entries.last().isBoss
                    )
                }
            }
        }
    }

    // =======================================================================
    // 5) YERLESIM SOZLESMESI — HUD yuksekligi DEGISMEMELI
    // =======================================================================

    @Test
    fun stripIsShorterThanTheTallestHudRowControl() {
        // KRITIK: HUD'un olculen yuksekligi `GameScreen` -> `GameCanvas`
        // -> `GameEngine.updateMapDimensions` zinciriyle oynanis
        // dikdortgenini belirliyor. Serit satirin en uzun cocugunu (36 dp
        // IconButton) gecerse HUD uzar ve savas alani her dalgada zipplar.
        assertTrue(
            "serit HUD satirini uzatiyor: ${WavePreview.STRIP_HEIGHT_DP} dp",
            WavePreview.STRIP_HEIGHT_DP < WavePreview.HUD_ROW_CONTENT_HEIGHT_DP
        )
    }

    // =======================================================================
    // 6) SIGDIRMA (fit) — dar ekranda zarifce kucul, ogretici bilgiyi koru
    // =======================================================================

    private fun entriesOf(level: Int, waveIndex: Int) =
        WavePreview.forWave(level, waveIndex)!!.entries

    /**
     * KAMPANYANIN EN COK TIP ICEREN DALGASI — sabit bir (bolum, indeks)
     * ciftiyle degil, **olcumle** bulunur.
     *
     * Onceden burada `entriesOf(22, 17)` yaziliyordu, yani "L22'nin 18 dalgasi
     * var" varsayimi ALTI ayri teste gomuluydu. Bolum sekli 5-7 dalgaya inince
     * `forWave(22, 17)` null dondu ve `!!` yuzunden testler kirilmadi, **COKTU**
     * (NPE) — yani kirmizinin sebebini soylemediler.
     *
     * URETIM YOLU ETKILENMEDI, kontrol edildi: `WavePreview.forWave` gecersiz
     * indekste `null` doner ve `WavePreviewBar` cagri yerinde `?: return`
     * yapar. Kirilgan olan yalnizca testin sabit varsayimiydi; artik yok.
     */
    private val richestWaveEntries: List<WavePreview.Entry> by lazy {
        allLevels.flatMap { level ->
            WaveDefinitions.wavesFor(level).indices.mapNotNull { WavePreview.forWave(level, it) }
        }.maxByOrNull { it.entries.size }!!.entries
    }

    private fun widthFor(chips: Int, withLabel: Boolean): Int {
        val chipSpace = chips * (WavePreview.CHIP_WIDTH_DP + WavePreview.CHIP_GAP_DP)
        return if (withLabel) {
            WavePreview.LABEL_WIDTH_DP + WavePreview.CHIP_GAP_DP + chipSpace
        } else {
            chipSpace
        }
    }

    @Test
    fun wideStripShowsLabelAndEveryType() {
        val entries = richestWaveEntries
        val plan = WavePreview.fit(widthFor(entries.size, withLabel = true), entries)!!
        assertTrue(plan.showLabel)
        assertEquals(entries, plan.visibleEntries)
        assertEquals(0, plan.hiddenTypeCount)
    }

    @Test
    fun mediumStripDropsTheLabelFirstAndKeepsEveryType() {
        val entries = richestWaveEntries
        val plan = WavePreview.fit(widthFor(entries.size, withLabel = false), entries)!!
        assertFalse("etiket once feda edilmeliydi", plan.showLabel)
        assertEquals(entries, plan.visibleEntries)
        assertEquals(0, plan.hiddenTypeCount)
    }

    @Test
    fun narrowStripHidesTheLightestTypesAndKeepsTheBoss() {
        val entries = richestWaveEntries
        assertTrue("bu test 3'ten fazla tip bekliyor", entries.size > 3)
        // Yalnizca 3 kart siginca 1 yuva "+k" kartina gider -> 2 tip gorunur.
        val plan = WavePreview.fit(widthFor(3, withLabel = false), entries)!!
        assertFalse(plan.showLabel)
        assertEquals(2, plan.visibleEntries.size)
        assertEquals(entries.size - 2, plan.hiddenTypeCount)
        // Korunanlar listenin SONU, yani en agirlar + boss.
        assertEquals(entries.takeLast(2), plan.visibleEntries)
        assertTrue("boss karti gizlenmis", plan.visibleEntries.last().isBoss)
    }

    @Test
    fun hiddenPlusVisibleAlwaysAccountsForEveryType() {
        val entries = richestWaveEntries
        for (width in 0..400) {
            val plan = WavePreview.fit(width, entries) ?: continue
            assertEquals(
                "genislik $width: kayip tip var",
                entries.size,
                plan.visibleEntries.size + plan.hiddenTypeCount
            )
            assertTrue("genislik $width: hic kart yok", plan.visibleEntries.isNotEmpty())
        }
    }

    @Test
    fun tooNarrowToTeachAnythingRendersNothing() {
        val entries = richestWaveEntries
        // Tek kart siginca o kart "+k" olmak zorunda kalirdi ve hicbir sey
        // anlatmazdi; yarim serit yerine HIC serit.
        assertNull(WavePreview.fit(widthFor(1, withLabel = false), entries))
        assertNull(WavePreview.fit(0, entries))
        assertNull(WavePreview.fit(-100, entries))
    }

    @Test
    fun emptyEntryListNeverProducesAStrip() {
        assertNull(WavePreview.fit(1_000, emptyList()))
    }

    @Test
    fun singleTypeWaveFitsInATinyStrip() {
        val entries = entriesOf(1, 0) // L01-W1: yalnizca piyade
        assertEquals(1, entries.size)
        val plan = WavePreview.fit(widthFor(1, withLabel = false), entries)!!
        assertEquals(1, plan.visibleEntries.size)
        assertEquals(0, plan.hiddenTypeCount)
    }

    @Test
    fun worstCaseWaveHasSixTypesAndStillFitsAReferenceLandscapeSlot() {
        // Kampanyanin en kotu durumu olculur, varsayilmaz.
        val worst = allLevels.flatMap { level ->
            WaveDefinitions.wavesFor(level).indices.map { WavePreview.forWave(level, it)!! }
        }.maxOf { it.entries.size }
        assertEquals("en kalabalik dalgadaki tip sayisi degismis", 6, worst)

        // 640 dp genisligindeki bir yatay telefonda HUD'un iki yan grubundan
        // sonra serit icin ~120 dp kalir. Orada bile bir seyler gostermeli.
        val entries = richestWaveEntries
        val plan = WavePreview.fit(120, entries)
        assertNotNull("dar yatay ekranda serit tamamen kayboluyor", plan)
        assertTrue(plan!!.visibleEntries.isNotEmpty())
        assertTrue("gizlenenler bildirilmemis", plan.hiddenTypeCount > 0)
    }
}
