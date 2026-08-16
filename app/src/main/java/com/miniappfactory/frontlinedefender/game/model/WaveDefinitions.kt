package com.miniappfactory.frontlinedefender.game.model

import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.GameConfig.WaveData
import com.miniappfactory.frontlinedefender.game.model.GameConfig.WaveEnemySpawn

/**
 * Frontline Defender — 22 bölümlük kampanyanın dalga tanımları.
 * Sahip: Level Designer.  Tasarım gerekçeleri: docs/LEVEL_DESIGN.md
 *
 * ---------------------------------------------------------------------------
 * ÖN KOŞUL (bu dosya bu patch olmadan DERLENMEZ)
 * ---------------------------------------------------------------------------
 * GameConfig.kt içine iki yeni düşman tipi eklenmelidir (GDD §A.1.3):
 *
 *   enum class EnemyType {
 *       INFANTRY, FAST_SOLDIER, ARMORED_VEHICLE, TANK,
 *       SHIELDED_TROOPER,   // <-- YENİ
 *       COMMAND_TANK        // <-- YENİ (boss)
 *   }
 *
 * ve ENEMY_SPECS haritasına:
 *
 *   EnemyType.SHIELDED_TROOPER to EnemyStats(
 *       type = EnemyType.SHIELDED_TROOPER, name = "Shielded Trooper",
 *       maxHp = 150f, baseSpeed = 58f, armor = 0.62f, rewardGold = 22, sizeRadius = 16f
 *   ),
 *   EnemyType.COMMAND_TANK to EnemyStats(
 *       type = EnemyType.COMMAND_TANK, name = "Command Tank",
 *       maxHp = 2600f, baseSpeed = 30f, armor = 0.72f, rewardGold = 180, sizeRadius = 38f
 *   )
 *
 * Ayrıca EnemyStats'a `splashVulnerability: Float = 1f` alanı eklenmeli
 * (SHIELDED_TROOPER için 1.6f) ve applyDamageToEnemy içinde splash mermileri
 * zırhı BYPASS edip bu çarpanı uygulamalı. Gerekçe ve alternatif:
 * LEVEL_DESIGN.md §0.2 / §D.5.
 *
 * ---------------------------------------------------------------------------
 * NOTLAR
 * ---------------------------------------------------------------------------
 * 1) `WaveData.title` KULLANICIYA GÖSTERİLMEZ. Bugünkü HUD yalnızca
 *    "WAVE n/N" basıyor. Buradaki etiketler İngilizce iç debug/telemetri
 *    etiketleridir. Bir gün UI'da gösterilecekse ayrı bir string-resource
 *    eşlemesi eklenmelidir (GDD §I: tek dilli sabit metin kullanıcıya çıkmaz).
 * 2) `WaveEnemySpawn.delaySeconds` motorda "bu spawn'dan SONRAKİ bekleme"dir
 *    (GameEngine.kt:283-288). Listenin son elemanının gap'i etkisizdir.
 * 3) Kampanya boyunca ENEMY_SPECS'te HİÇBİR global HP/hız çarpanı yoktur.
 *    Zorluk yalnızca kompozisyon + yoğunluk + pad kısıtından gelir.
 * 4) Sıfır RNG. Aynı bölüm her zaman aynı dalgaları üretir.
 */
object WaveDefinitions {

    // ======================================================================
    // Yardımcılar
    // ======================================================================

    /**
     * Kampanyanın ana dalga üreticisi. Deterministik:
     *  - hafif tipler (inf/fast/shield) ikili bloklar hâlinde round-robin dizilir
     *    (küçük kümeler oluşur; Cannon splash'ının değeri buradan gelir),
     *  - ağırlar (arm/tank) hafiflerin arasına EŞİT aralıkla serpiştirilir,
     *  - boss'lar dalganın SONUNA eklenir.
     * Aynı parametreler her zaman aynı listeyi verir; rastgelelik yok.
     */
    private fun mix(
        index: Int,
        label: String,
        inf: Int = 0,
        fast: Int = 0,
        shield: Int = 0,
        arm: Int = 0,
        tank: Int = 0,
        boss: Int = 0,
        lightGap: Float = 0.80f,
        heavyGap: Float = 1.40f,
        bossGap: Float = 3.00f
    ): WaveData {
        val light = buildLightSequence(inf, fast, shield)
        val heavy = List(arm) { EnemyType.ARMORED_VEHICLE } + List(tank) { EnemyType.TANK }
        val ordered = interleaveHeavies(light, heavy) + List(boss) { EnemyType.COMMAND_TANK }
        val spawns = ordered.map { type ->
            WaveEnemySpawn(type, gapFor(type, lightGap, heavyGap, bossGap))
        }
        return WaveData(waveIndex = index, title = label, spawns = spawns)
    }

    private fun gapFor(type: EnemyType, lightGap: Float, heavyGap: Float, bossGap: Float): Float =
        when (type) {
            EnemyType.INFANTRY,
            EnemyType.FAST_SOLDIER,
            EnemyType.SHIELDED_TROOPER -> lightGap
            EnemyType.ARMORED_VEHICLE,
            EnemyType.TANK -> heavyGap
            EnemyType.COMMAND_TANK -> bossGap
        }

    private fun buildLightSequence(inf: Int, fast: Int, shield: Int): List<EnemyType> {
        val out = ArrayList<EnemyType>(inf + fast + shield)
        var i = inf
        var f = fast
        var s = shield
        while (i > 0 || f > 0 || s > 0) {
            val ti = minOf(2, i); repeat(ti) { out.add(EnemyType.INFANTRY) }; i -= ti
            val tf = minOf(2, f); repeat(tf) { out.add(EnemyType.FAST_SOLDIER) }; f -= tf
            val ts = minOf(2, s); repeat(ts) { out.add(EnemyType.SHIELDED_TROOPER) }; s -= ts
        }
        return out
    }

    private fun interleaveHeavies(
        light: List<EnemyType>,
        heavy: List<EnemyType>
    ): List<EnemyType> {
        if (heavy.isEmpty()) return light
        if (light.isEmpty()) return heavy
        val out = ArrayList<EnemyType>(light.size + heavy.size)
        val step = light.size.toFloat() / (heavy.size + 1).toFloat()
        var placed = 0
        var nextAt = step
        light.forEachIndexed { i, type ->
            while (placed < heavy.size && i.toFloat() >= nextAt) {
                out.add(heavy[placed]); placed++; nextAt += step
            }
            out.add(type)
        }
        while (placed < heavy.size) { out.add(heavy[placed]); placed++ }
        return out
    }

    // ======================================================================
    // ACT I — Bölüm 1-11 (haritalar 01 -> 11, ileri sıra)
    // ======================================================================

    /** L1 · Harita 01 · Öğretici-A · yalnızca MACHINE_GUN mevcut. */
    private val LEVEL_01: List<WaveData> = listOf(
        mix(1, "L01-W1 first-contact", inf = 6, lightGap = 1.20f),
        mix(2, "L01-W2 probing-patrol", inf = 9, lightGap = 1.00f),
        mix(3, "L01-W3 scout-trickle", fast = 7, lightGap = 0.90f),
        mix(4, "L01-W4 mixed-foot", inf = 8, fast = 3, lightGap = 0.90f),
        mix(5, "L01-W5 pressure-test", inf = 8, fast = 6, lightGap = 0.85f),
        mix(6, "L01-W6 first-push", inf = 10, fast = 8, lightGap = 0.75f)
    )

    /** L2 · Harita 02 · Öğretici-B · yükseltme öğretilir. */
    private val LEVEL_02: List<WaveData> = listOf(
        mix(1, "L02-W1 warmup", inf = 8, lightGap = 1.10f),
        mix(2, "L02-W2 runner-lesson", fast = 8, lightGap = 0.85f),
        mix(3, "L02-W3 combined-foot", inf = 10, fast = 4, lightGap = 0.90f),
        mix(4, "L02-W4 runner-rush", fast = 12, lightGap = 0.70f),
        mix(5, "L02-W5 upgrade-check", inf = 12, fast = 6, lightGap = 0.80f),
        mix(6, "L02-W6 sustained-foot", inf = 14, fast = 10, lightGap = 0.70f)
    )

    /** L3 · Harita 03 · Karşı-koyma: kalabalık -> splash · CANNON açılır. */
    private val LEVEL_03: List<WaveData> = listOf(
        mix(1, "L03-W1 opening", inf = 8, lightGap = 1.00f),
        mix(2, "L03-W2 tight-column", inf = 12, lightGap = 0.60f),
        mix(3, "L03-W3 scouts", fast = 10, lightGap = 0.80f),
        mix(4, "L03-W4 packed-advance", inf = 14, lightGap = 0.55f),
        mix(5, "L03-W5 mixed-press", inf = 10, fast = 8, lightGap = 0.70f),
        mix(6, "L03-W6 mass-column", inf = 16, lightGap = 0.50f),
        mix(7, "L03-W7 splash-exam", inf = 14, fast = 10, lightGap = 0.55f)
    )

    /** L4 · Harita 04 · Hız baskını (hafif) · satma/yeniden konumlandırma. */
    private val LEVEL_04: List<WaveData> = listOf(
        mix(1, "L04-W1 opening", inf = 8, lightGap = 1.00f),
        mix(2, "L04-W2 fast-probe", fast = 12, lightGap = 0.70f),
        mix(3, "L04-W3 mixed", inf = 10, fast = 6, lightGap = 0.80f),
        mix(4, "L04-W4 fast-rush", fast = 16, lightGap = 0.55f),
        mix(5, "L04-W5 mixed-press", inf = 12, fast = 8, lightGap = 0.70f),
        mix(6, "L04-W6 sprint-wave", fast = 20, lightGap = 0.45f),
        mix(7, "L04-W7 combined-run", inf = 14, fast = 12, lightGap = 0.60f),
        mix(8, "L04-W8 blitz", inf = 12, fast = 22, lightGap = 0.45f)
    )

    /** L5 · Harita 05 · Zırh sınavı (giriş) · ARMORED_VEHICLE + ANTI_ARMOR bedava. */
    private val LEVEL_05: List<WaveData> = listOf(
        mix(1, "L05-W1 opening", inf = 10, lightGap = 0.90f),
        mix(2, "L05-W2 scouts", fast = 12, lightGap = 0.70f),
        mix(3, "L05-W3 mixed", inf = 12, fast = 6, lightGap = 0.80f),
        mix(4, "L05-W4 mass-foot", inf = 16, lightGap = 0.60f),
        mix(5, "L05-W5 first-armor", inf = 8, arm = 2, lightGap = 0.80f, heavyGap = 1.60f),
        mix(6, "L05-W6 armor-and-runners", fast = 12, arm = 2, lightGap = 0.65f, heavyGap = 1.60f),
        mix(7, "L05-W7 combined-arms", inf = 12, fast = 8, arm = 3, lightGap = 0.70f, heavyGap = 1.50f),
        mix(8, "L05-W8 armored-column", inf = 10, fast = 10, arm = 4, lightGap = 0.65f, heavyGap = 1.40f)
    )

    /** L6 · Harita 06 · Kalabalık · hedefleme modları tanıtılır · ilk gerçek yenilgi riski. */
    private val LEVEL_06: List<WaveData> = listOf(
        mix(1, "L06-W1 opening", inf = 12, lightGap = 0.80f),
        mix(2, "L06-W2 scouts", fast = 14, lightGap = 0.60f),
        mix(3, "L06-W3 packed-foot", inf = 16, lightGap = 0.55f),
        mix(4, "L06-W4 combined", inf = 10, fast = 10, arm = 2, lightGap = 0.70f, heavyGap = 1.50f),
        mix(5, "L06-W5 swarm", inf = 20, lightGap = 0.45f),
        mix(6, "L06-W6 runner-swarm", fast = 20, arm = 2, lightGap = 0.50f, heavyGap = 1.50f),
        mix(7, "L06-W7 targeting-exam", inf = 14, fast = 8, arm = 3, lightGap = 0.55f, heavyGap = 1.40f),
        mix(8, "L06-W8 armor-press", inf = 12, fast = 8, arm = 4, lightGap = 0.60f, heavyGap = 1.30f),
        mix(9, "L06-W9 overrun-attempt", inf = 18, fast = 12, arm = 3, lightGap = 0.45f, heavyGap = 1.30f)
    )

    /** L7 · Harita 07 · Zırh sınavı · TANK tanıtımı (W6). */
    private val LEVEL_07: List<WaveData> = listOf(
        mix(1, "L07-W1 opening", inf = 12, fast = 6, lightGap = 0.80f),
        mix(2, "L07-W2 armor-recall", inf = 10, arm = 2, lightGap = 0.80f, heavyGap = 1.50f),
        mix(3, "L07-W3 scouts", fast = 16, lightGap = 0.60f),
        mix(4, "L07-W4 armor-press", inf = 14, arm = 3, lightGap = 0.70f, heavyGap = 1.40f),
        mix(5, "L07-W5 combined", inf = 12, fast = 12, arm = 2, lightGap = 0.65f, heavyGap = 1.40f),
        mix(6, "L07-W6 first-tank", inf = 10, arm = 2, tank = 1, lightGap = 0.70f, heavyGap = 1.50f),
        mix(7, "L07-W7 runner-armor", fast = 16, arm = 3, lightGap = 0.55f, heavyGap = 1.40f),
        mix(8, "L07-W8 heavy-column", inf = 12, fast = 8, arm = 3, tank = 1, lightGap = 0.60f, heavyGap = 1.30f),
        mix(9, "L07-W9 breakthrough", inf = 10, fast = 8, arm = 4, tank = 1, lightGap = 0.60f, heavyGap = 1.30f)
    )

    /** L8 · Harita 08 · NEFES / Tahkim · SLOW (Frost Field) bedava açılır. */
    private val LEVEL_08: List<WaveData> = listOf(
        mix(1, "L08-W1 opening", inf = 12, lightGap = 0.80f),
        mix(2, "L08-W2 scouts", fast = 14, lightGap = 0.65f),
        mix(3, "L08-W3 light-armor", inf = 14, arm = 2, lightGap = 0.75f, heavyGap = 1.60f),
        mix(4, "L08-W4 frost-showcase", fast = 18, lightGap = 0.55f),
        mix(5, "L08-W5 mixed", inf = 16, fast = 8, lightGap = 0.70f),
        mix(6, "L08-W6 armor-press", inf = 12, arm = 3, lightGap = 0.75f, heavyGap = 1.50f),
        mix(7, "L08-W7 runner-flood", fast = 22, lightGap = 0.50f),
        mix(8, "L08-W8 combined", inf = 14, fast = 12, arm = 2, lightGap = 0.65f, heavyGap = 1.50f),
        mix(9, "L08-W9 tank-return", inf = 12, arm = 3, tank = 1, lightGap = 0.70f, heavyGap = 1.50f),
        mix(10, "L08-W10 consolidation", inf = 16, fast = 14, arm = 3, lightGap = 0.60f, heavyGap = 1.40f)
    )

    /** L9 · Harita 09 · Karşı-koyma: SHIELDED_TROOPER (W4). */
    private val LEVEL_09: List<WaveData> = listOf(
        mix(1, "L09-W1 opening", inf = 14, fast = 8, lightGap = 0.75f),
        mix(2, "L09-W2 armor", inf = 12, arm = 3, lightGap = 0.75f, heavyGap = 1.50f),
        mix(3, "L09-W3 runners", fast = 20, lightGap = 0.55f),
        mix(4, "L09-W4 first-shields", inf = 10, shield = 4, lightGap = 0.70f),
        mix(5, "L09-W5 shield-lesson", inf = 12, shield = 6, lightGap = 0.70f),
        mix(6, "L09-W6 shields-and-armor", fast = 16, shield = 5, arm = 2, lightGap = 0.60f, heavyGap = 1.45f),
        mix(7, "L09-W7 shield-press", inf = 14, shield = 8, lightGap = 0.65f),
        mix(8, "L09-W8 heavy-column", inf = 12, fast = 10, arm = 3, tank = 1, lightGap = 0.65f, heavyGap = 1.40f),
        mix(9, "L09-W9 shield-wall", inf = 10, shield = 10, arm = 2, lightGap = 0.60f, heavyGap = 1.40f),
        mix(10, "L09-W10 combined-exam", inf = 14, fast = 10, shield = 8, arm = 3, lightGap = 0.60f, heavyGap = 1.40f)
    )

    /** L10 · Harita 10 · Ağır zırh sınavı (nehir/köprü, iki koldan akış varsa). */
    private val LEVEL_10: List<WaveData> = listOf(
        mix(1, "L10-W1 opening", inf = 14, fast = 10, lightGap = 0.70f),
        mix(2, "L10-W2 shields", inf = 12, shield = 6, lightGap = 0.70f),
        mix(3, "L10-W3 armor-column", inf = 12, arm = 4, lightGap = 0.70f, heavyGap = 1.40f),
        mix(4, "L10-W4 runner-shield", fast = 22, shield = 4, lightGap = 0.50f),
        mix(5, "L10-W5 tank-escort", inf = 14, arm = 4, tank = 1, lightGap = 0.70f, heavyGap = 1.40f),
        mix(6, "L10-W6 broad-front", inf = 16, fast = 12, shield = 6, lightGap = 0.60f),
        mix(7, "L10-W7 armor-mass", inf = 12, arm = 5, tank = 1, lightGap = 0.65f, heavyGap = 1.30f),
        mix(8, "L10-W8 shielded-runners", fast = 20, shield = 8, arm = 2, lightGap = 0.50f, heavyGap = 1.40f),
        mix(9, "L10-W9 twin-tanks", inf = 14, arm = 4, tank = 2, lightGap = 0.65f, heavyGap = 1.30f),
        mix(10, "L10-W10 all-arms", inf = 16, fast = 14, shield = 6, arm = 3, lightGap = 0.55f, heavyGap = 1.30f),
        mix(11, "L10-W11 bridge-assault", inf = 12, shield = 8, arm = 5, tank = 1, lightGap = 0.60f, heavyGap = 1.25f)
    )

    /** L11 · Harita 11 · ACT I FİNALİ · COMMAND_TANK (boss) ilk kez, son dalgada. */
    private val LEVEL_11: List<WaveData> = listOf(
        mix(1, "L11-W1 opening", inf = 16, fast = 10, lightGap = 0.70f),
        mix(2, "L11-W2 shields", inf = 14, shield = 6, lightGap = 0.70f),
        mix(3, "L11-W3 armor", inf = 12, arm = 4, lightGap = 0.70f, heavyGap = 1.40f),
        mix(4, "L11-W4 runner-flood", fast = 24, shield = 5, lightGap = 0.50f),
        mix(5, "L11-W5 tank-escort", inf = 14, arm = 4, tank = 1, lightGap = 0.70f, heavyGap = 1.40f),
        mix(6, "L11-W6 broad-front", inf = 18, fast = 14, shield = 6, lightGap = 0.60f),
        mix(7, "L11-W7 shield-wall", inf = 12, shield = 10, arm = 3, lightGap = 0.60f, heavyGap = 1.40f),
        mix(8, "L11-W8 twin-tanks", inf = 14, arm = 5, tank = 2, lightGap = 0.65f, heavyGap = 1.30f),
        mix(9, "L11-W9 blitz", fast = 26, shield = 8, arm = 2, lightGap = 0.45f, heavyGap = 1.40f),
        mix(10, "L11-W10 all-arms", inf = 16, fast = 12, shield = 8, arm = 4, lightGap = 0.55f, heavyGap = 1.30f),
        mix(11, "L11-W11 armor-spearhead", inf = 12, arm = 5, tank = 3, lightGap = 0.60f, heavyGap = 1.25f),
        mix(
            12, "L11-W12 BOSS-command-tank",
            inf = 10, fast = 8, shield = 4, arm = 2, tank = 1, boss = 1,
            lightGap = 0.60f, heavyGap = 1.30f, bossGap = 3.00f
        )
    )

    // ======================================================================
    // ACT II — Bölüm 12-22 (haritalar 11 -> 01, TERS SIRA = harita sırası)
    // Gece overlay yalnızca görsel. Pad'lerin %25-40'ı devre dışı.
    // ======================================================================

    /** L12 · Harita 11 (gece) · NEFES / Tahkim · pad kısıtı ilk kez. */
    private val LEVEL_12: List<WaveData> = listOf(
        mix(1, "L12-W1 counterattack-open", inf = 14, fast = 10, lightGap = 0.75f),
        mix(2, "L12-W2 shields", inf = 12, shield = 5, lightGap = 0.75f),
        mix(3, "L12-W3 runners", fast = 20, lightGap = 0.55f),
        mix(4, "L12-W4 armor", inf = 14, arm = 3, lightGap = 0.75f, heavyGap = 1.50f),
        mix(5, "L12-W5 mixed", inf = 16, fast = 12, lightGap = 0.65f),
        mix(6, "L12-W6 shield-press", inf = 12, shield = 8, lightGap = 0.65f),
        mix(7, "L12-W7 tank-escort", inf = 14, arm = 4, tank = 1, lightGap = 0.70f, heavyGap = 1.40f),
        mix(8, "L12-W8 runner-flood", fast = 24, shield = 5, lightGap = 0.50f),
        mix(9, "L12-W9 broad-front", inf = 16, fast = 14, shield = 6, lightGap = 0.60f),
        mix(10, "L12-W10 armor-mass", inf = 12, arm = 5, lightGap = 0.65f, heavyGap = 1.35f),
        mix(11, "L12-W11 all-arms", inf = 14, fast = 12, shield = 8, arm = 2, lightGap = 0.60f, heavyGap = 1.40f),
        mix(12, "L12-W12 night-push", inf = 12, shield = 8, arm = 4, tank = 1, lightGap = 0.60f, heavyGap = 1.30f)
    )

    /** L13 · Harita 10 (gece) · Karma kolon. */
    private val LEVEL_13: List<WaveData> = listOf(
        mix(1, "L13-W1 opening", inf = 16, fast = 12, lightGap = 0.70f),
        mix(2, "L13-W2 armor", inf = 14, arm = 3, lightGap = 0.70f, heavyGap = 1.45f),
        mix(3, "L13-W3 runner-shield", fast = 24, shield = 5, lightGap = 0.50f),
        mix(4, "L13-W4 shield-press", inf = 14, shield = 8, lightGap = 0.65f),
        mix(5, "L13-W5 armor-mass", inf = 12, arm = 5, lightGap = 0.65f, heavyGap = 1.35f),
        mix(6, "L13-W6 broad-front", inf = 16, fast = 14, shield = 6, lightGap = 0.60f),
        mix(7, "L13-W7 tank-escort", inf = 14, arm = 4, tank = 1, lightGap = 0.65f, heavyGap = 1.35f),
        mix(8, "L13-W8 blitz", fast = 26, shield = 8, lightGap = 0.45f),
        mix(9, "L13-W9 shield-wall", inf = 14, shield = 10, arm = 3, lightGap = 0.60f, heavyGap = 1.35f),
        mix(10, "L13-W10 twin-tanks", inf = 12, arm = 5, tank = 2, lightGap = 0.60f, heavyGap = 1.25f),
        mix(11, "L13-W11 all-arms", inf = 18, fast = 16, shield = 8, arm = 2, lightGap = 0.55f, heavyGap = 1.30f),
        mix(12, "L13-W12 heavy-mix", inf = 14, shield = 8, arm = 4, tank = 1, lightGap = 0.60f, heavyGap = 1.30f),
        mix(13, "L13-W13 split-column", inf = 12, fast = 12, shield = 6, arm = 4, tank = 2, lightGap = 0.55f, heavyGap = 1.20f)
    )

    /** L14 · Harita 09 (gece) · Yoğun SHIELDED çekirdek + tank. */
    private val LEVEL_14: List<WaveData> = listOf(
        mix(1, "L14-W1 opening", inf = 16, shield = 6, lightGap = 0.70f),
        mix(2, "L14-W2 mixed-foot", inf = 14, fast = 14, lightGap = 0.65f),
        mix(3, "L14-W3 shield-press", inf = 12, shield = 10, lightGap = 0.65f),
        mix(4, "L14-W4 armor", inf = 14, arm = 4, lightGap = 0.70f, heavyGap = 1.40f),
        mix(5, "L14-W5 shield-mass", inf = 12, shield = 12, lightGap = 0.60f),
        mix(6, "L14-W6 blitz", fast = 26, shield = 6, lightGap = 0.45f),
        mix(7, "L14-W7 shield-armor", inf = 14, shield = 10, arm = 3, lightGap = 0.60f, heavyGap = 1.35f),
        mix(8, "L14-W8 tank-escort", inf = 12, arm = 5, tank = 1, lightGap = 0.65f, heavyGap = 1.30f),
        mix(9, "L14-W9 shield-wall", inf = 14, shield = 14, lightGap = 0.55f),
        mix(10, "L14-W10 broad-front", inf = 16, fast = 16, shield = 8, lightGap = 0.55f),
        mix(11, "L14-W11 shield-armor-mass", inf = 12, shield = 12, arm = 4, lightGap = 0.55f, heavyGap = 1.30f),
        mix(12, "L14-W12 heavy-mix", inf = 14, shield = 10, arm = 3, tank = 1, lightGap = 0.60f, heavyGap = 1.25f),
        mix(13, "L14-W13 shielded-spearhead", inf = 14, shield = 14, arm = 4, tank = 2, lightGap = 0.55f, heavyGap = 1.25f)
    )

    /** L15 · Harita 08 (gece) · NEFES-ARA · kalabalık odaklı, PW düşük. */
    private val LEVEL_15: List<WaveData> = listOf(
        mix(1, "L15-W1 opening", inf = 18, fast = 14, lightGap = 0.65f),
        mix(2, "L15-W2 mass-foot", inf = 20, lightGap = 0.55f),
        mix(3, "L15-W3 runner-swarm", fast = 28, lightGap = 0.45f),
        mix(4, "L15-W4 shields", inf = 16, shield = 6, lightGap = 0.60f),
        mix(5, "L15-W5 packed-column", inf = 24, lightGap = 0.50f),
        mix(6, "L15-W6 armor", inf = 14, arm = 4, lightGap = 0.65f, heavyGap = 1.40f),
        mix(7, "L15-W7 sprint-flood", fast = 32, lightGap = 0.40f),
        mix(8, "L15-W8 broad-swarm", inf = 20, fast = 16, lightGap = 0.50f),
        mix(9, "L15-W9 shield-press", inf = 14, shield = 10, lightGap = 0.60f),
        mix(10, "L15-W10 tank-escort", inf = 16, arm = 4, tank = 1, lightGap = 0.60f, heavyGap = 1.35f),
        mix(11, "L15-W11 mass-swarm", inf = 26, fast = 20, lightGap = 0.45f),
        mix(12, "L15-W12 shield-mass", inf = 14, shield = 12, arm = 2, lightGap = 0.55f, heavyGap = 1.35f),
        mix(13, "L15-W13 all-light", inf = 22, fast = 18, shield = 8, lightGap = 0.45f),
        mix(14, "L15-W14 swarm-finale", inf = 18, fast = 16, shield = 8, arm = 4, tank = 1, lightGap = 0.50f, heavyGap = 1.25f)
    )

    /** L16 · Harita 07 (gece) · ÇİFT BOSS · iki ayrı boss dalgası (W10 ve W14). */
    private val LEVEL_16: List<WaveData> = listOf(
        mix(1, "L16-W1 opening", inf = 18, fast = 14, lightGap = 0.65f),
        mix(2, "L16-W2 shields", inf = 14, shield = 8, lightGap = 0.65f),
        mix(3, "L16-W3 armor", inf = 14, arm = 4, lightGap = 0.65f, heavyGap = 1.40f),
        mix(4, "L16-W4 blitz", fast = 28, shield = 6, lightGap = 0.45f),
        mix(5, "L16-W5 shield-press", inf = 16, shield = 10, lightGap = 0.60f),
        mix(6, "L16-W6 tank-escort", inf = 14, arm = 5, tank = 1, lightGap = 0.65f, heavyGap = 1.30f),
        mix(7, "L16-W7 broad-front", inf = 20, fast = 18, shield = 8, lightGap = 0.50f),
        mix(8, "L16-W8 shield-armor", inf = 14, shield = 12, arm = 3, lightGap = 0.55f, heavyGap = 1.30f),
        mix(9, "L16-W9 twin-tanks", inf = 12, arm = 5, tank = 2, lightGap = 0.60f, heavyGap = 1.25f),
        mix(
            10, "L16-W10 BOSS-1",
            inf = 12, fast = 10, shield = 6, arm = 2, boss = 1,
            lightGap = 0.60f, heavyGap = 1.30f, bossGap = 3.00f
        ),
        mix(11, "L16-W11 recovery-swarm", inf = 20, fast = 20, shield = 10, lightGap = 0.45f),
        mix(12, "L16-W12 heavy-mix", inf = 14, shield = 12, arm = 4, tank = 1, lightGap = 0.55f, heavyGap = 1.30f),
        mix(13, "L16-W13 all-arms", inf = 18, fast = 16, shield = 10, arm = 3, lightGap = 0.50f, heavyGap = 1.30f),
        mix(
            14, "L16-W14 BOSS-2",
            inf = 12, fast = 10, shield = 8, arm = 3, tank = 1, boss = 1,
            lightGap = 0.55f, heavyGap = 1.25f, bossGap = 3.00f
        )
    )

    /** L17 · Harita 06 (gece) · Ağır karma kolon. */
    private val LEVEL_17: List<WaveData> = listOf(
        mix(1, "L17-W1 opening", inf = 18, fast = 16, lightGap = 0.60f),
        mix(2, "L17-W2 shields", inf = 16, shield = 8, lightGap = 0.60f),
        mix(3, "L17-W3 armor", inf = 14, arm = 4, lightGap = 0.65f, heavyGap = 1.35f),
        mix(4, "L17-W4 blitz", fast = 30, shield = 6, lightGap = 0.42f),
        mix(5, "L17-W5 shield-mass", inf = 16, shield = 12, lightGap = 0.55f),
        mix(6, "L17-W6 tank-escort", inf = 14, arm = 5, tank = 1, lightGap = 0.60f, heavyGap = 1.30f),
        mix(7, "L17-W7 broad-front", inf = 22, fast = 20, shield = 8, lightGap = 0.45f),
        mix(8, "L17-W8 shield-armor", inf = 14, shield = 12, arm = 4, lightGap = 0.55f, heavyGap = 1.30f),
        mix(9, "L17-W9 armor-wall", inf = 12, arm = 6, tank = 2, lightGap = 0.60f, heavyGap = 1.20f),
        mix(10, "L17-W10 swarm", inf = 20, fast = 22, shield = 10, lightGap = 0.42f),
        mix(11, "L17-W11 shield-wall", inf = 16, shield = 14, arm = 3, lightGap = 0.50f, heavyGap = 1.30f),
        mix(12, "L17-W12 tank-trio", inf = 14, arm = 5, tank = 3, lightGap = 0.55f, heavyGap = 1.20f),
        mix(13, "L17-W13 all-light", inf = 22, fast = 20, shield = 12, arm = 2, lightGap = 0.45f, heavyGap = 1.30f),
        mix(14, "L17-W14 heavy-mix", inf = 16, shield = 14, arm = 5, tank = 1, lightGap = 0.50f, heavyGap = 1.25f),
        mix(15, "L17-W15 combined-finale", inf = 18, fast = 18, shield = 12, arm = 5, tank = 2, lightGap = 0.48f, heavyGap = 1.20f)
    )

    /** L18 · Harita 05 (gece) · Elit hız baskını: koşucular + bataryayı meşgul eden ağırlar. */
    private val LEVEL_18: List<WaveData> = listOf(
        mix(1, "L18-W1 sprint-open", fast = 30, lightGap = 0.45f),
        mix(2, "L18-W2 mixed-run", inf = 18, fast = 20, lightGap = 0.55f),
        mix(3, "L18-W3 sprint-shield", fast = 36, shield = 4, lightGap = 0.38f),
        mix(4, "L18-W4 shield-armor", inf = 16, shield = 10, arm = 2, lightGap = 0.55f, heavyGap = 1.35f),
        mix(5, "L18-W5 runner-screen", fast = 34, shield = 8, arm = 2, lightGap = 0.40f, heavyGap = 1.30f),
        mix(6, "L18-W6 armor-mass", inf = 14, arm = 6, lightGap = 0.60f, heavyGap = 1.30f),
        mix(7, "L18-W7 sprint-flood", fast = 40, shield = 6, arm = 2, lightGap = 0.35f, heavyGap = 1.30f),
        mix(8, "L18-W8 broad-run", inf = 20, fast = 26, shield = 10, arm = 3, lightGap = 0.45f, heavyGap = 1.30f),
        mix(9, "L18-W9 twin-tanks", inf = 14, arm = 6, tank = 2, lightGap = 0.60f, heavyGap = 1.20f),
        mix(10, "L18-W10 saturation-run", fast = 44, shield = 10, arm = 4, lightGap = 0.32f, heavyGap = 1.30f),
        mix(11, "L18-W11 all-light", inf = 18, fast = 30, shield = 12, arm = 3, lightGap = 0.40f, heavyGap = 1.30f),
        mix(12, "L18-W12 shield-wall", inf = 14, shield = 14, arm = 5, tank = 1, lightGap = 0.50f, heavyGap = 1.25f),
        mix(13, "L18-W13 max-sprint", fast = 48, shield = 12, arm = 4, tank = 1, lightGap = 0.30f, heavyGap = 1.25f),
        mix(14, "L18-W14 combined-run", inf = 16, fast = 32, shield = 12, arm = 5, tank = 1, lightGap = 0.40f, heavyGap = 1.20f),
        mix(15, "L18-W15 blitz-finale", inf = 18, fast = 38, shield = 14, arm = 5, tank = 2, lightGap = 0.32f, heavyGap = 1.20f)
    )

    /** L19 · Harita 04 (gece) · NEFES-ARA · zırh sınavı, PW düşük, dalga sayısı yüksek. */
    private val LEVEL_19: List<WaveData> = listOf(
        mix(1, "L19-W1 opening", inf = 18, fast = 14, lightGap = 0.60f),
        mix(2, "L19-W2 armor", inf = 16, arm = 4, lightGap = 0.65f, heavyGap = 1.40f),
        mix(3, "L19-W3 shields", inf = 14, shield = 10, lightGap = 0.60f),
        mix(4, "L19-W4 armor-mass", inf = 16, arm = 5, lightGap = 0.65f, heavyGap = 1.35f),
        mix(5, "L19-W5 blitz", fast = 30, shield = 6, lightGap = 0.45f),
        mix(6, "L19-W6 tank-escort", inf = 14, arm = 5, tank = 1, lightGap = 0.60f, heavyGap = 1.30f),
        mix(7, "L19-W7 broad-front", inf = 20, fast = 18, shield = 8, lightGap = 0.50f),
        mix(8, "L19-W8 armor-wall", inf = 14, arm = 6, lightGap = 0.60f, heavyGap = 1.30f),
        mix(9, "L19-W9 shield-armor", inf = 16, shield = 12, arm = 3, lightGap = 0.55f, heavyGap = 1.35f),
        mix(10, "L19-W10 twin-tanks", inf = 14, arm = 5, tank = 2, lightGap = 0.60f, heavyGap = 1.25f),
        mix(11, "L19-W11 swarm", inf = 22, fast = 20, shield = 8, lightGap = 0.45f),
        mix(12, "L19-W12 armor-column", inf = 14, arm = 7, lightGap = 0.60f, heavyGap = 1.25f),
        mix(13, "L19-W13 heavy-mix", inf = 16, shield = 12, arm = 4, tank = 1, lightGap = 0.55f, heavyGap = 1.30f),
        mix(14, "L19-W14 all-arms", inf = 20, fast = 22, shield = 10, arm = 3, lightGap = 0.45f, heavyGap = 1.30f),
        mix(15, "L19-W15 tank-pair", inf = 14, arm = 6, tank = 2, lightGap = 0.55f, heavyGap = 1.20f),
        mix(16, "L19-W16 armored-finale", inf = 18, fast = 18, shield = 12, arm = 6, tank = 2, lightGap = 0.50f, heavyGap = 1.20f)
    )

    /** L20 · Harita 03 (gece) · Elit kalabalık: kalkanlı sürü. */
    private val LEVEL_20: List<WaveData> = listOf(
        mix(1, "L20-W1 opening", inf = 22, fast = 18, lightGap = 0.55f),
        mix(2, "L20-W2 shields", inf = 18, shield = 10, lightGap = 0.55f),
        mix(3, "L20-W3 swarm", inf = 26, fast = 22, lightGap = 0.45f),
        mix(4, "L20-W4 shield-mass", inf = 16, shield = 14, lightGap = 0.50f),
        mix(5, "L20-W5 armor", inf = 16, arm = 5, lightGap = 0.60f, heavyGap = 1.30f),
        mix(6, "L20-W6 broad-swarm", inf = 30, fast = 26, shield = 8, lightGap = 0.40f),
        mix(7, "L20-W7 shield-wall", inf = 16, shield = 16, arm = 3, lightGap = 0.50f, heavyGap = 1.30f),
        mix(8, "L20-W8 twin-tanks", inf = 14, arm = 6, tank = 2, lightGap = 0.60f, heavyGap = 1.20f),
        mix(9, "L20-W9 mass-swarm", inf = 34, fast = 30, shield = 10, lightGap = 0.35f),
        mix(10, "L20-W10 shield-armor", inf = 18, shield = 18, arm = 4, lightGap = 0.45f, heavyGap = 1.30f),
        mix(11, "L20-W11 all-light", inf = 26, fast = 26, shield = 12, arm = 3, lightGap = 0.40f, heavyGap = 1.30f),
        mix(12, "L20-W12 heavy-shield", inf = 16, shield = 16, arm = 5, tank = 1, lightGap = 0.45f, heavyGap = 1.25f),
        mix(13, "L20-W13 saturation", inf = 36, fast = 32, shield = 14, lightGap = 0.32f),
        mix(14, "L20-W14 shield-spear", inf = 18, shield = 20, arm = 5, tank = 1, lightGap = 0.42f, heavyGap = 1.25f),
        mix(15, "L20-W15 combined", inf = 28, fast = 28, shield = 16, arm = 4, tank = 1, lightGap = 0.35f, heavyGap = 1.20f),
        mix(16, "L20-W16 swarm-finale", inf = 32, fast = 30, shield = 18, arm = 6, tank = 2, lightGap = 0.32f, heavyGap = 1.15f)
    )

    /** L21 · Harita 02 (gece) · Uzman karma kolon. */
    private val LEVEL_21: List<WaveData> = listOf(
        mix(1, "L21-W1 opening", inf = 22, fast = 20, lightGap = 0.50f),
        mix(2, "L21-W2 shields", inf = 18, shield = 12, lightGap = 0.50f),
        mix(3, "L21-W3 armor", inf = 16, arm = 6, lightGap = 0.60f, heavyGap = 1.30f),
        mix(4, "L21-W4 blitz", fast = 34, shield = 10, lightGap = 0.40f),
        mix(5, "L21-W5 shield-mass", inf = 18, shield = 16, lightGap = 0.48f),
        mix(6, "L21-W6 tank-pair", inf = 16, arm = 6, tank = 2, lightGap = 0.55f, heavyGap = 1.25f),
        mix(7, "L21-W7 broad-swarm", inf = 30, fast = 28, shield = 10, lightGap = 0.38f),
        mix(8, "L21-W8 shield-armor", inf = 18, shield = 18, arm = 4, lightGap = 0.45f, heavyGap = 1.30f),
        mix(9, "L21-W9 armor-wall", inf = 16, arm = 7, tank = 2, lightGap = 0.55f, heavyGap = 1.20f),
        mix(10, "L21-W10 all-light", inf = 32, fast = 32, shield = 12, arm = 3, lightGap = 0.35f, heavyGap = 1.25f),
        mix(11, "L21-W11 shield-spear", inf = 18, shield = 20, arm = 5, tank = 1, lightGap = 0.42f, heavyGap = 1.25f),
        mix(12, "L21-W12 tank-trio", inf = 16, arm = 8, tank = 3, lightGap = 0.50f, heavyGap = 1.15f),
        mix(13, "L21-W13 saturation", inf = 34, fast = 34, shield = 16, arm = 4, lightGap = 0.32f, heavyGap = 1.20f),
        mix(14, "L21-W14 heavy-shield", inf = 20, shield = 22, arm = 6, tank = 2, lightGap = 0.40f, heavyGap = 1.20f),
        mix(15, "L21-W15 combined", inf = 30, fast = 30, shield = 18, arm = 5, tank = 1, lightGap = 0.35f, heavyGap = 1.20f),
        mix(16, "L21-W16 armor-storm", inf = 18, shield = 20, arm = 8, tank = 3, lightGap = 0.40f, heavyGap = 1.15f),
        mix(17, "L21-W17 expert-finale", inf = 28, fast = 32, shield = 20, arm = 6, tank = 3, lightGap = 0.32f, heavyGap = 1.15f)
    )

    /** L22 · Harita 01 (gece) · KAMPANYA FİNALİ · W10 ve W14 tek boss, W18'de 3x COMMAND_TANK. */
    private val LEVEL_22: List<WaveData> = listOf(
        mix(1, "L22-W1 homeland-open", inf = 24, fast = 20, lightGap = 0.50f),
        mix(2, "L22-W2 shields", inf = 18, shield = 14, lightGap = 0.48f),
        mix(3, "L22-W3 armor", inf = 16, arm = 6, lightGap = 0.55f, heavyGap = 1.30f),
        mix(4, "L22-W4 blitz", fast = 36, shield = 10, lightGap = 0.38f),
        mix(5, "L22-W5 shield-mass", inf = 18, shield = 18, lightGap = 0.45f),
        mix(6, "L22-W6 tank-pair", inf = 16, arm = 7, tank = 2, lightGap = 0.55f, heavyGap = 1.20f),
        mix(7, "L22-W7 broad-swarm", inf = 32, fast = 30, shield = 12, lightGap = 0.35f),
        mix(8, "L22-W8 shield-armor", inf = 18, shield = 20, arm = 5, lightGap = 0.42f, heavyGap = 1.25f),
        mix(9, "L22-W9 tank-trio", inf = 16, arm = 8, tank = 3, lightGap = 0.50f, heavyGap = 1.15f),
        mix(
            10, "L22-W10 BOSS-1",
            inf = 14, fast = 12, shield = 8, arm = 3, boss = 1,
            lightGap = 0.55f, heavyGap = 1.25f, bossGap = 3.00f
        ),
        mix(11, "L22-W11 saturation", inf = 34, fast = 34, shield = 16, arm = 4, lightGap = 0.32f, heavyGap = 1.20f),
        mix(12, "L22-W12 heavy-shield", inf = 18, shield = 22, arm = 6, tank = 2, lightGap = 0.40f, heavyGap = 1.20f),
        mix(13, "L22-W13 armor-storm", inf = 16, arm = 8, tank = 4, lightGap = 0.50f, heavyGap = 1.15f),
        mix(
            14, "L22-W14 BOSS-2",
            inf = 14, fast = 14, shield = 10, arm = 4, tank = 1, boss = 1,
            lightGap = 0.50f, heavyGap = 1.20f, bossGap = 3.00f
        ),
        mix(15, "L22-W15 combined", inf = 32, fast = 34, shield = 20, arm = 5, lightGap = 0.32f, heavyGap = 1.20f),
        mix(16, "L22-W16 shield-storm", inf = 18, shield = 24, arm = 8, tank = 3, lightGap = 0.38f, heavyGap = 1.15f),
        mix(17, "L22-W17 last-stand-prelude", inf = 30, fast = 32, shield = 20, arm = 6, tank = 3, lightGap = 0.32f, heavyGap = 1.15f),
        mix(
            18, "L22-W18 FINALE-triple-command",
            inf = 16, fast = 14, shield = 12, arm = 4, tank = 2, boss = 3,
            lightGap = 0.45f, heavyGap = 1.20f, bossGap = 3.00f
        )
    )

    // ======================================================================
    // Kampanya kaydı
    // ======================================================================

    /** Bölüm no -> dalga listesi. Bölüm sırası GDD §B.2 ile birebir aynıdır. */
    val CAMPAIGN: Map<Int, List<WaveData>> = mapOf(
        1 to LEVEL_01, 2 to LEVEL_02, 3 to LEVEL_03, 4 to LEVEL_04,
        5 to LEVEL_05, 6 to LEVEL_06, 7 to LEVEL_07, 8 to LEVEL_08,
        9 to LEVEL_09, 10 to LEVEL_10, 11 to LEVEL_11, 12 to LEVEL_12,
        13 to LEVEL_13, 14 to LEVEL_14, 15 to LEVEL_15, 16 to LEVEL_16,
        17 to LEVEL_17, 18 to LEVEL_18, 19 to LEVEL_19, 20 to LEVEL_20,
        21 to LEVEL_21, 22 to LEVEL_22
    )

    const val CAMPAIGN_LEVEL_COUNT: Int = 22

    fun wavesFor(levelId: Int): List<WaveData> =
        CAMPAIGN[levelId] ?: error("Frontline Defender: level $levelId icin dalga seti tanimli degil")

    fun waveCount(levelId: Int): Int = wavesFor(levelId).size
}

/**
 * Dalga ağırlığı ölçüm yardımcıları — testler ve denge doğrulaması için.
 *
 * AEHP ("armor-effective HP") = maxHp / m, burada m referans batarya
 * kompozisyonunun (%50 kurşun / %25 patlama / %25 delici DPS) o düşmana karşı
 * ortalama hasar çarpanıdır. Türetme: docs/LEVEL_DESIGN.md §E.1.
 *
 * Bu tablo BALANS DEĞİL, ÖLÇÜM ARACIdır. ENEMY_SPECS değişirse burası da
 * güncellenmeli; WaveDefinitionsTest bu tutarlılığı zorunlu kılar.
 */
object WaveMetrics {

    /**
     * Referans bataryanin hasar dagilimi (docs/LEVEL_DESIGN.md E.1):
     * %50 kursun (MG), %25 patlama (Cannon), %25 delici (Fuze).
     */
    private const val W_BULLET = 0.50f
    private const val W_EXPLOSIVE = 0.25f
    private const val W_PIERCING = 0.25f

    /**
     * Zorluk olcumu icin ETKIN HP (Armor-Effective HP).
     *
     * ELLE YAZILMIYOR — `ENEMY_SPECS`'ten TURETILIYOR. Sebep gercek bir hata:
     * tablo elle yazildiginda `ARMORED_VEHICLE` icin 388 diyordu; bu deger
     * DECISIONS B2'den (patlama zirhi BYPASS eder) ONCE hesaplanmisti. B2 top
     * mermisini zirha karsi etkili yapinca dogru deger 312.3'e dustu, ama tablo
     * guncellenmedi ve `LEVEL_DESIGN.md`'nin butun zorluk egrisi zirhli
     * dusmanlari oldugundan **%24 daha agir** sandi.
     *
     * Turetilmis oldugu icin artik `ENEMY_SPECS` degistiginde otomatik takip
     * eder ve bir daha sessizce kayamaz.
     */
    val AEHP: Map<EnemyType, Float> by lazy {
        val pierce = GameConfig.TOWER_SPECS
            .getValue(GameConfig.TowerType.ANTI_ARMOR).armorPierce
        EnemyType.values().associateWith { type ->
            val spec = GameConfig.ENEMY_SPECS.getValue(type)
            val bullet = 1f - spec.armor                        // zirh tam etkili
            val explosive = spec.splashVulnerability            // zirhi bypass eder (B2)
            val piercing = 1f - spec.armor * (1f - pierce)      // zirhin %85'i asilir
            val multiplier = W_BULLET * bullet +
                W_EXPLOSIVE * explosive +
                W_PIERCING * piercing
            spec.maxHp / multiplier
        }
    }

    fun waveAehp(wave: WaveData): Float =
        wave.spawns.sumOf { (AEHP[it.enemyType] ?: 0f).toDouble() }.toFloat()

    fun levelAehp(waves: List<WaveData>): Float = waves.sumOf { waveAehp(it).toDouble() }.toFloat()

    fun peakWaveAehp(waves: List<WaveData>): Float = waves.maxOf { waveAehp(it) }

    /** Bir dalganın spawn penceresi (sn): son elemanin gap'i sayilmaz. */
    fun spawnWindowSeconds(wave: WaveData): Float =
        0.5f + wave.spawns.dropLast(1).sumOf { it.delaySeconds.toDouble() }.toFloat()

    fun spawnCount(wave: WaveData): Int = wave.spawns.size
}
