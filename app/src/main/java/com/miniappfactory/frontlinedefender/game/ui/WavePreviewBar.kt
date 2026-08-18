package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.ui.theme.SleekBorderLight
import com.miniappfactory.frontlinedefender.ui.theme.SleekRedBorder
import com.miniappfactory.frontlinedefender.ui.theme.SleekRedText
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceCard
import com.miniappfactory.frontlinedefender.ui.theme.SleekTextAccent

/**
 * ===========================================================================
 * SIRADAKI DALGA ONIZLEMESI
 * ===========================================================================
 *
 * NEDEN VAR (FUN_AUDIT): oyunun 4 kulesi gercekten 4 ayri rol — zirhsiz
 * hedefte Gatling 43,8 DPS'e karsi Top 18,4; tankta Gatling 6,1'e karsi Fuze
 * 26,6 (7 kat fark). Ama oyuncu bir sonraki dalgada NE GELECEGINI hicbir yerde
 * goremedigi icin bu karsi-koyma tasarimi gorunmezdi ve hazirlik fazi bos bir
 * geri sayimdi. Serit, hazirlik fazini bir KARAR anina cevirir: "3 zirhli arac
 * geliyor, kursun ise yaramaz" bilgisi dalga BASLAMADAN once verilir.
 *
 * ---------------------------------------------------------------------------
 * YERLESIM KARARI — neden HUD SERIDININ ORTASI, neden baska hicbir yer degil
 * ---------------------------------------------------------------------------
 * Oyun `sensorLandscape` (AndroidManifest). Yatay ekranda ust HUD satirinin
 * ORTASI bugun tamamen bostur (solda dalga+tedarik rozetleri, sagda can /
 * BASLAT / hiz / duraklat). Serit oraya konur. Elenen alternatifler:
 *
 *  - **HUD'un ALTINA ayri bir serit.** Reddedildi: `GameScreen` HUD'un olculen
 *    yuksekligini `GameCanvas`'a `topInsetPx` olarak veriyor ve motor oynanis
 *    dikdortgenini (`GameEngine.updateMapDimensions`) buna gore kuruyor. HUD
 *    yalnizca hazirlik fazinda uzasaydi savas alani her dalga basinda ASAGI
 *    KAYAR, dalga bitince geri ziplardi — pad'ler ve kuleler dahil.
 *  - **HUD'un altina TASAN (olculmeyen) katman.** Reddedildi: ust build pad'ler
 *    HUD'un hemen altinda (harita 2 pad 7 normY=0.1302, yani serbest alanin
 *    ~%3'u kadar asagida). Opak bir serit onlari orterdi — bu tam olarak
 *    `MAP_SAFE_TOP_FRAC` ile bir kez cozulmus olan hatanin geri gelmesi olurdu.
 *    (Bu hata cihazda goruldu ve `GameConfig.MAP_SAFE_TOP_FRAC` KDoc'unda
 *    anlatiliyor. Eskiden burada bir ekran goruntusune atif vardi;
 *    **o dosya diskte hic yoktu** — `docs/` git disinda oldugu icin kopuk atif
 *    yillarca fark edilmeden durabiliyor. Bkz. `docs/COMMIT_PLAN.md`.)
 *  - **BottomCenter.** Orada `TowerBuildBar` (63 dp) ve `SelectedTowerInspector`
 *    (56 dp) var; ikisi de hazirlik fazinda ACIK olabilir.
 *  - **Sag kenar.** `BoosterRail` orada (alta 72 dp capali, 56/48 dp genis).
 *
 * CAKISMA KONTROLU: serit HUD satirinin ucuncu cocugu olarak, sol ve sag
 * gruplarin ARASINA `weight(1f)` ile girer. Agirlikli olmayan cocuklar Row'da
 * ONCE olculdugu icin sol/sag gruplar dogal boyutlarini korur; serit yalnizca
 * ARTAN yeri alir ve hicbir kosulda onlari ekrandan itemez. Yer kalmazsa
 * icerik "+k" ile kucultulur, hic sigmiyorsa serit cizilmez
 * (bkz. [WavePreview.fit]).
 *
 * YUKSEKLIK SABITI (KRITIK): serit [WavePreview.STRIP_HEIGHT_DP] = 34 dp ile
 * SABIT yuksekliktedir ve bu deger HUD satirinin en uzun cocugundan (36 dp'lik
 * IconButton'lar) KUCUKTUR. Boylece serit gorunse de gorunmese de HUD'un
 * olculen yuksekligi DEGISMEZ, yani `topInsetPx` ve oynanis dikdortgeni sabit
 * kalir. Bu iliski `WavePreviewLogicTest` ile kilitlidir — 34 dp'yi buyutmek
 * isteyen once HUD'un 36 dp'lik kontrol yuksekligini buyutmeli.
 *
 * DOKUNMA: seritte HIC dokunma modifier'i YOK. Hazirlik fazinda BASLAT
 * butonunun hemen solunda duruyor; tiklanabilir olsaydi kazara basma riski
 * ucretsiz gelirdi, karsiliginda hicbir sey vermezdi.
 *
 * PERFORMANS: [WavePreviewBar] kendi akislarini KENDISI toplar, yani kendi
 * recompose kapsamidir. HUD'un her karede degisen `preparationTimer`
 * okumasindan etkilenmez. Dalga bilesimi `remember(levelId, waveIndex)` ile
 * onbelleklenir: kare dongusunde HIC is yapilmaz, hesap yalnizca dalga (veya
 * bolum) degisince bir kez kosar.
 */
object WavePreview {

    // ---- Olcu sabitleri (dp). `fit` bunlarla saf aritmetik yapar. ----------

    /** Tek dusman kartinin genisligi: 17 dp sprite + bosluk + "x34" sayaci. */
    const val CHIP_WIDTH_DP = 38

    /** Kartlar ve etiket arasindaki bosluk. */
    const val CHIP_GAP_DP = 3

    /** "GELIYOR" etiketinin sabit genisligi. Yer yoksa etiket ILK feda edilir. */
    const val LABEL_WIDTH_DP = 52

    /**
     * Seridin SABIT yuksekligi. [HUD_ROW_CONTENT_HEIGHT_DP]'den KUCUK olmak
     * ZORUNDA — gerekcesi dosya basligindaki "YUKSEKLIK SABITI" notunda.
     */
    const val STRIP_HEIGHT_DP = 34

    /**
     * HUD satirinin en uzun cocugu (`HUDOverlay.kt` icindeki hiz ve duraklat
     * IconButton'lari 36 dp). HUD'un olculen yuksekligini BU belirler.
     */
    const val HUD_ROW_CONTENT_HEIGHT_DP = 36

    /** Boss. Dalga bunu iceriyorsa serit boss dalgasi olarak isaretlenir. */
    val BOSS_TYPE: GameConfig.EnemyType = GameConfig.EnemyType.COMMAND_TANK

    /**
     * Onizlemede tek bir dusman TIPI.
     *
     * @param armored `ENEMY_SPECS`'ten TURETILIR (armor > 0), elle listelenmez:
     *   zirh degeri degisirse rozet kendiliginden takip eder.
     */
    data class Entry(
        val type: GameConfig.EnemyType,
        val count: Int,
        val armored: Boolean,
        val isBoss: Boolean
    )

    /** Bir sonraki dalganin tam onizleme verisi. */
    data class Info(
        val levelId: Int,
        /** 1 tabanli — HUD'daki "DALGA n/N" ile AYNI n. */
        val waveNumber: Int,
        val totalWaves: Int,
        val entries: List<Entry>,
        val hasBoss: Boolean,
        val hasArmor: Boolean
    ) {
        val totalEnemies: Int get() = entries.sumOf { it.count }
    }

    /** Verilen genislige ne sigdigi. `null` = hic sigmiyor, serit cizilmez. */
    data class FitPlan(
        val showLabel: Boolean,
        val visibleEntries: List<Entry>,
        /** Yer yetmedigi icin GIZLENEN tip sayisi ("+3" karti). */
        val hiddenTypeCount: Int
    )

    /**
     * Motora DOKUNMADAN, yalnizca public `WaveDefinitions.CAMPAIGN` uzerinden
     * bir sonraki dalganin bilesimi.
     *
     * `waveIndex` semantigi: motor hazirlik fazina girerken `_currentWaveIndex`
     * zaten BASLAYACAK dalgayi gosteriyor (`GameEngine.kt`:
     * `_currentWaveIndex.value = completedIdx + 1` -> `setupWave` ->
     * `PREPARATION`). Yani serit ile HUD'un "DALGA n/N" rozeti AYNI dalgayi
     * anlatir; ikisi arasinda bir kaydirma YOKTUR.
     *
     * SON DALGA: motor son dalga bitince indeksi ARTIRMAZ, dogrudan `VICTORY`
     * durumuna gecer (`completedIdx >= levelWaves.lastIndex`). Dolayisiyla son
     * hazirlik fazinda burasi son dalgayi gosterir ve bolum bittikten sonra
     * serit zaten cizilmez (durum PREPARATION degil). Yine de indeks tasmasi
     * savunmali olarak `null` doner — cokme yok.
     *
     * @return tanimsiz bolum, gecersiz indeks veya BOS dalga icin `null`.
     */
    fun forWave(levelId: Int, waveIndex: Int): Info? {
        val waves = WaveDefinitions.CAMPAIGN[levelId] ?: return null
        if (waveIndex < 0 || waveIndex >= waves.size) return null
        val spawns = waves[waveIndex].spawns
        if (spawns.isEmpty()) return null

        val counts = LinkedHashMap<GameConfig.EnemyType, Int>()
        for (spawn in spawns) {
            counts[spawn.enemyType] = (counts[spawn.enemyType] ?: 0) + 1
        }

        // SIRALAMA: zirh artan, esitlikte can artan. Yani hafif -> agir -> boss.
        // Bu sadece estetik degil: zirh rozetli kartlar SAGDA bitisik bir kume
        // olusturur, "sagdakiler kursun yemiyor" desenini serit kendiliginden
        // ogretir. Enum sirasi kullanilsaydi SHIELDED_TROOPER tankin SAGINDA
        // kalirdi (enum'a sonradan eklendi) ve desen bozulurdu.
        val entries = counts.entries
            .sortedWith(compareBy({ armorOf(it.key) }, { maxHpOf(it.key) }))
            .map { (type, count) ->
                Entry(
                    type = type,
                    count = count,
                    armored = armorOf(type) > 0f,
                    isBoss = type == BOSS_TYPE
                )
            }

        return Info(
            levelId = levelId,
            waveNumber = waveIndex + 1,
            totalWaves = waves.size,
            entries = entries,
            hasBoss = entries.any { it.isBoss },
            hasArmor = entries.any { it.armored }
        )
    }

    /**
     * Dar ekranda ZARIFCE kucul. Saf aritmetik, Compose'a bagimli degil.
     *
     * Neden gerekli: kampanyanin en kalabalik dalgasi 6 AYRI tip iceriyor
     * (L22-W18: piyade + kesif + kalkanli + zirhli + tank + 3 boss). 6 kart
     * 246 dp; 640 dp genisligindeki bir yatay telefonda HUD'un iki yan
     * grubundan sonra bu kadar yer kalmaz.
     *
     * Feda sirasi: (1) etiket, (2) EN HAFIF tipler. Boss ve agirlar EN SONA
     * kadar korunur — cunku seridin butun ogretme degeri onlarda. Gizlenen
     * tipler "+k" karti ile SOLDA bildirilir, sessizce yutulmaz.
     *
     * @return hic kart sigmiyorsa `null` (yarim bir serit cizmektense hic cizme).
     */
    fun fit(availableWidthDp: Int, entries: List<Entry>): FitPlan? {
        if (entries.isEmpty() || availableWidthDp <= 0) return null
        val perChip = CHIP_WIDTH_DP + CHIP_GAP_DP
        val allChips = entries.size * perChip
        if (availableWidthDp >= LABEL_WIDTH_DP + CHIP_GAP_DP + allChips) {
            return FitPlan(showLabel = true, visibleEntries = entries, hiddenTypeCount = 0)
        }
        if (availableWidthDp >= allChips) {
            return FitPlan(showLabel = false, visibleEntries = entries, hiddenTypeCount = 0)
        }
        val fittingChips = availableWidthDp / perChip
        // 1 kart sigiyorsa o kart "+k" olmak zorunda kalir ve hicbir sey
        // anlatmaz; 0 ise zaten yer yok.
        if (fittingChips <= 1) return null
        val visible = fittingChips - 1 // bir yuva "+k" kartina ayrilir
        return FitPlan(
            showLabel = false,
            visibleEntries = entries.takeLast(visible),
            hiddenTypeCount = entries.size - visible
        )
    }

    private fun armorOf(type: GameConfig.EnemyType): Float =
        GameConfig.ENEMY_SPECS[type]?.armor ?: 0f

    private fun maxHpOf(type: GameConfig.EnemyType): Float =
        GameConfig.ENEMY_SPECS[type]?.maxHp ?: 0f
}

// ---------------------------------------------------------------------------
// Renkler — yalnizca bu serit kullaniyor, temaya EKLENMEDI.
// ---------------------------------------------------------------------------

/** Celik mavisi: "zirh plakasi". Kirmizi (boss) ve altin (tedarik) ile karismaz. */
private val ArmorPlate = Color(0xFF9FC0E0)
private val ArmorPlateEdge = Color(0xFF1B2733)
private val StripBg = Color(0x66161A12)
private val BossStripBg = Color(0x66410E0B)

@Composable
fun WavePreviewBar(
    gameEngine: GameEngine,
    modifier: Modifier = Modifier
) {
    val gameState by gameEngine.gameState.collectAsState()
    // Dalga basladi -> serit YOK. Hicbir dugum uretilmez, yani HUD satiri
    // bugunku SpaceBetween davranisina birebir doner.
    if (gameState != GameState.PREPARATION) return

    val levelId by gameEngine.currentLevelId.collectAsState()
    val waveIndex by gameEngine.currentWaveIndex.collectAsState()

    // Kare dongusunde is yok: yalnizca bolum veya dalga degisince hesaplanir.
    val info = remember(levelId, waveIndex) { WavePreview.forWave(levelId, waveIndex) }
        ?: return

    WavePreviewStrip(info = info, modifier = modifier)
}

@Composable
private fun WavePreviewStrip(
    info: WavePreview.Info,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.height(WavePreview.STRIP_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center
    ) {
        val availableDp = maxWidth.value.toInt()
        val plan = remember(availableDp, info) { WavePreview.fit(availableDp, info.entries) }
            ?: return@BoxWithConstraints

        val shape = RoundedCornerShape(9.dp)
        Row(
            modifier = Modifier
                .clip(shape)
                .background(if (info.hasBoss) BossStripBg else StripBg)
                .border(1.dp, if (info.hasBoss) SleekRedBorder else SleekBorderLight, shape)
                .padding(horizontal = 4.dp)
                .testTag("wave_preview_strip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WavePreview.CHIP_GAP_DP.dp)
        ) {
            if (plan.showLabel) {
                AutoShrinkText(
                    text = stringResource(R.string.hud_next_wave),
                    color = if (info.hasBoss) SleekRedText else SleekTextAccent,
                    maxFontSize = 9.sp,
                    minFontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(WavePreview.LABEL_WIDTH_DP.dp)
                )
            }
            if (plan.hiddenTypeCount > 0) {
                OverflowChip(hiddenTypeCount = plan.hiddenTypeCount)
            }
            plan.visibleEntries.forEach { entry ->
                EnemyChip(entry = entry)
            }
        }
    }
}

@Composable
private fun EnemyChip(entry: WavePreview.Entry) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .width(WavePreview.CHIP_WIDTH_DP.dp)
            .height(26.dp)
            .clip(shape)
            .background(SleekSurfaceCard)
            .border(1.dp, if (entry.isBoss) SleekRedBorder else SleekBorderLight, shape)
            .padding(horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(17.dp)) {
            SpriteIcon(
                id = enemySpriteRes(entry.type),
                size = 17.dp,
                contentDescription = stringResource(
                    R.string.hud_wave_preview_enemy_desc,
                    stringResource(entry.type.nameRes()),
                    entry.count
                )
            )
            // BOSS ISARETI: kurukafa + kartin kirmizi cercevesi + seridin
            // kirmizi zemini. Uc sinyal ayni anda; tek basina RENGE dayanan
            // bir isaret birakilmadi.
            if (entry.isBoss) {
                SpriteIcon(
                    id = R.drawable.spr_ic_defeat_skull,
                    size = 10.dp,
                    contentDescription = stringResource(R.string.hud_wave_preview_boss_desc),
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
            // ZIRH ISARETI: kalkan rozeti. Boss'ta da cizilir (boss zirhi 0.88)
            // — kural istisnasiz olsun ki oyuncu "rozet = kursun ise yaramaz"
            // baglantisini kurabilsin.
            if (entry.armored) {
                ArmorBadge(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(9.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(1.dp))
        AutoShrinkText(
            text = stringResource(R.string.hud_wave_preview_count, entry.count),
            color = if (entry.isBoss) SleekRedText else SleekTextAccent,
            maxFontSize = 9.sp,
            minFontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OverflowChip(hiddenTypeCount: Int) {
    val shape = RoundedCornerShape(6.dp)
    val description = pluralStringResource(
        R.plurals.hud_wave_preview_more_desc,
        hiddenTypeCount,
        hiddenTypeCount
    )
    Row(
        modifier = Modifier
            .width(WavePreview.CHIP_WIDTH_DP.dp)
            .height(26.dp)
            .clip(shape)
            .background(SleekSurfaceCard)
            .border(1.dp, SleekBorderLight, shape)
            // Ekran okuyucu "+3" yerine "3 dusman tipi daha" duysun.
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoShrinkText(
            text = stringResource(R.string.hud_wave_preview_more, hiddenTypeCount),
            color = SleekTextAccent,
            maxFontSize = 10.sp,
            minFontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Zirh rozeti — kalkan silueti. Yeni bir drawable EKLENMEDI: 9 dp'lik bir
 * raster ikon bu boyutta bulanik olurdu, vektor cizim her yogunlukta net.
 */
@Composable
private fun ArmorBadge(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.24f)
            lineTo(w, h * 0.56f)
            cubicTo(w, h * 0.82f, w * 0.74f, h * 0.95f, w * 0.5f, h)
            cubicTo(w * 0.26f, h * 0.95f, 0f, h * 0.82f, 0f, h * 0.56f)
            lineTo(0f, h * 0.24f)
            close()
        }
        drawPath(shield, ArmorPlate)
        drawPath(shield, ArmorPlateEdge, style = Stroke(width = w * 0.16f))
    }
}
