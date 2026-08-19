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

    // ----------------------------------------------------------------------
    // Faz 10 — L1..L6 SIKILASTIRMASI
    //
    // Testci: "ilk 5 bolum asiri basitti", "6 kule parasi kazaniyorsun ama 2
    // yetiyor". Ekonomi ajani ikinci sikayeti OLCTU ve topu buraya atti:
    // L1'de toplam dusman cani 4.155 AEHP, bir Gatling 87.5 DPS -> tek kule
    // bolumun 4 katindan fazla hasar veriyor. Yani Tedarik ne kadar kisilsa da
    // bu can egrisiyle ucuncu kule ZORUNLU olmuyor.
    //
    // OLCUT: **BASKI = dalga AEHP'si / spawn penceresi (AEHP/sn).**
    // Toplam can degil, BIRIM ZAMANDAKI can. Sebep kuyruk teorisi kadar basit:
    // bir kulenin oldurme hizi sabittir (DPS/dusman cani); dusmanlar bundan
    // hizli GELIYORSA fark birikir ve sizar — dalganin toplam cani ne olursa
    // olsun. AEHP zaten "referans batarya hasari" biriminde tanimli
    // (%50 kursun / %25 patlama / %25 delici), dolayisiyla baskiyi dogrudan
    // kule DPS'ine bolebiliyoruz ve cikan sayi "kac kule gerekir"dir.
    //
    // TEPE BASKI (kule esdegeri) — once -> sonra:
    //   L1 1.26 -> 1.82 | L2 1.29 -> 2.00 | L3 1.96 -> 2.26
    //   L4 1.62 -> 2.25 | L5 1.79 -> 2.48 | L6 2.09 -> 2.53   (L7 zaten 2.62)
    // 1.3 "iki kule fazlasiyla yeter" demekti; 2.3-2.5 ucuncu ve dorduncu
    // kuleyi ZORUNLU kilar. Ustelik zirh 0.78/0.86 oldugu icin bu DPS'in
    // dogru MUHIMMATTAN gelmesi gerekiyor (4 Gatling zirhli araca karsi
    // nominal DPS'inin %22'sini verir) — yani cevap "ayni kuleden 4 tane" degil.
    //
    // NASIL: neredeyse tamamen SPAWN KADANSI (lightGap 0.55 -> 0.34..0.42) ve
    // kompozisyon. Toplam can yalnizca %6-16 artti; HP sismesi YOK
    // (LEVEL_DESIGN E). Sikisik kolon ayni zamanda Cannon'i odullendirir.
    //
    // KORUNAN KISITLAR (hepsi WaveDefinitionsDataTest'te):
    //   · dalga sayilari degismedi · zirh L5 / tank L7 tanitimi ayni
    //   · hicbir dalga o ana kadarki en agir dalganin 2.5 katini gecmiyor
    //   · spawn araligi >= 0.25 sn, pencere 8..17 sn
    //   · L1 hâlâ kampanyanin en hafif bolumu, L6 kasitli olarak L7'nin ALTINDA
    //     (19.321 < 19.403): L7 hem TANK'i hem fuze rampasini getiriyor.
    //
    // KULE KILIDI ile tutarlilik (GameConfig.TOWER_SPECS.unlockedAtLevel):
    //   L1 Gatling · L3 Heavy Cannon · L5 Frost Field · L7 Missile Battery.
    // ARMORED_VEHICLE L5'te giriyor ve zirhi 0.78: kursun ise yaramaz. Cevabi
    // bolum 3'te acilan CANNON'dir (splash zirhi bypass eder, DECISIONS B2) ve
    // ayni bolumde acilan FROST FIELD hedefi kule menzilinde daha uzun tutar.
    // Yani zorunlu mekanik oyuncunun ELINDE. Bunu yorum degil test garanti eder:
    // WaveDefinitionsDataTest.noArmouredEnemyBecomesMandatoryBeforeItsCounterIsUnlocked
    // ----------------------------------------------------------------------

    /** L1 · Harita 01 · Öğretici-A · yalnızca MACHINE_GUN mevcut. */
    private val LEVEL_01: List<WaveData> = listOf(
        // W1-W3 ogretici temposunda BIRAKILDI: ilk temas hâlâ nazik olmali
        // (baski 0.7-1.0 kule = tek Gatling yetisir, ki L1 Tedariki 80 ile
        // oyuncunun elinde tam bir kule var).
        mix(1, "L01-W1 first-contact", inf = 6, lightGap = 1.15f),
        mix(2, "L01-W2 probing-patrol", inf = 10, lightGap = 0.90f),
        mix(3, "L01-W3 scout-trickle", fast = 10, lightGap = 0.75f),
        // W4'ten itibaren tek kule matematiksel olarak yetmez (baski > 1.0).
        mix(4, "L01-W4 mixed-foot", inf = 12, fast = 4, lightGap = 0.62f),
        mix(5, "L01-W5 pressure-test", inf = 14, fast = 8, lightGap = 0.50f),
        mix(6, "L01-W6 first-push", inf = 18, fast = 10, lightGap = 0.40f)
    )

    /** L2 · Harita 02 · Öğretici-B · yükseltme öğretilir. */
    private val LEVEL_02: List<WaveData> = listOf(
        mix(1, "L02-W1 warmup", inf = 10, lightGap = 1.00f),
        mix(2, "L02-W2 runner-lesson", fast = 12, lightGap = 0.70f),
        mix(3, "L02-W3 combined-foot", inf = 12, fast = 6, lightGap = 0.65f),
        mix(4, "L02-W4 runner-rush", fast = 18, lightGap = 0.50f),
        // Yukseltme SINAVI: iki kademe-1 kule bu baskiyi (1.54) tutamaz,
        // yukseltme ya da ucuncu kule sart.
        mix(5, "L02-W5 upgrade-check", inf = 16, fast = 8, lightGap = 0.48f),
        mix(6, "L02-W6 sustained-foot", inf = 20, fast = 12, lightGap = 0.36f)
    )

    /** L3 · Harita 03 · Karşı-koyma: kalabalık -> splash · CANNON açılır. */
    private val LEVEL_03: List<WaveData> = listOf(
        mix(1, "L03-W1 opening", inf = 10, lightGap = 0.95f),
        // Sikisik kolonlar Cannon'in DERSI: 0.33-0.55 gap ile dusmanlar 78
        // ref-px'lik patlamanin icine girecek kadar birbirine yakin dogar.
        mix(2, "L03-W2 tight-column", inf = 16, lightGap = 0.55f),
        mix(3, "L03-W3 scouts", fast = 14, lightGap = 0.65f),
        mix(4, "L03-W4 packed-advance", inf = 20, lightGap = 0.45f),
        mix(5, "L03-W5 mixed-press", inf = 14, fast = 10, lightGap = 0.50f),
        mix(6, "L03-W6 mass-column", inf = 24, lightGap = 0.38f),
        mix(7, "L03-W7 splash-exam", inf = 24, fast = 10, lightGap = 0.33f)
    )

    /** L4 · Harita 04 · Hız baskını (hafif) · satma/yeniden konumlandırma. */
    private val LEVEL_04: List<WaveData> = listOf(
        mix(1, "L04-W1 opening", inf = 12, lightGap = 0.90f),
        mix(2, "L04-W2 fast-probe", fast = 18, lightGap = 0.60f),
        mix(3, "L04-W3 mixed", inf = 14, fast = 8, lightGap = 0.60f),
        mix(4, "L04-W4 fast-rush", fast = 26, lightGap = 0.45f),
        mix(5, "L04-W5 mixed-press", inf = 18, fast = 10, lightGap = 0.45f),
        // Kosucu selleri Cannon'in KOR NOKTASI: mermi 0.91 sn ucar, Scout
        // Runner o surede patlama alanindan cikar. Bolumun dersi bu.
        mix(6, "L04-W6 sprint-wave", fast = 34, lightGap = 0.32f),
        mix(7, "L04-W7 combined-run", inf = 20, fast = 16, lightGap = 0.35f),
        mix(8, "L04-W8 blitz", inf = 22, fast = 22, lightGap = 0.30f)
    )

    /**
     * L5 · Harita 05 · Zırh sınavı (giriş) · ARMORED_VEHICLE tanıtılır.
     * Aynı bölümde FROST FIELD açılır (GameConfig.unlockedAtLevel = 5): zırhlı
     * araç yavaşken Cannon'ın yavaş mermisi ve kısa menzilli Gatling ona çok
     * daha uzun süre ateş eder — destek kulesinin dersi burada veriliyor.
     */
    private val LEVEL_05: List<WaveData> = listOf(
        mix(1, "L05-W1 opening", inf = 12, lightGap = 0.85f),
        mix(2, "L05-W2 scouts", fast = 16, lightGap = 0.62f),
        mix(3, "L05-W3 mixed", inf = 14, fast = 8, lightGap = 0.60f),
        mix(4, "L05-W4 mass-foot", inf = 20, lightGap = 0.45f),
        mix(5, "L05-W5 first-armor", inf = 12, arm = 2, lightGap = 0.70f, heavyGap = 1.60f),
        mix(6, "L05-W6 armor-and-runners", fast = 18, arm = 2, lightGap = 0.55f, heavyGap = 1.50f),
        mix(7, "L05-W7 combined-arms", inf = 16, fast = 10, arm = 3, lightGap = 0.47f, heavyGap = 1.40f),
        // Zirhli sayisi 4 -> 5: zirhlinin AEHP'si 379, yani agir birim baskiyi
        // hafif dusmandan cok daha verimli yukseltir ve DOGRU muhimmat ister.
        mix(8, "L05-W8 armored-column", inf = 16, fast = 12, arm = 5, lightGap = 0.38f, heavyGap = 1.20f)
    )

    /** L6 · Harita 06 · Kalabalık · hedefleme modları tanıtılır · ilk gerçek yenilgi riski. */
    private val LEVEL_06: List<WaveData> = listOf(
        mix(1, "L06-W1 opening", inf = 14, lightGap = 0.75f),
        mix(2, "L06-W2 scouts", fast = 20, lightGap = 0.55f),
        mix(3, "L06-W3 packed-foot", inf = 20, lightGap = 0.42f),
        mix(4, "L06-W4 combined", inf = 14, fast = 10, arm = 2, lightGap = 0.55f, heavyGap = 1.45f),
        mix(5, "L06-W5 swarm", inf = 24, lightGap = 0.36f),
        mix(6, "L06-W6 runner-swarm", fast = 26, arm = 2, lightGap = 0.42f, heavyGap = 1.45f),
        mix(7, "L06-W7 targeting-exam", inf = 18, fast = 10, arm = 3, lightGap = 0.45f, heavyGap = 1.35f),
        mix(8, "L06-W8 armor-press", inf = 14, fast = 10, arm = 5, lightGap = 0.46f, heavyGap = 1.30f),
        // Kampanyanin ilk gercek sinavi: 2.53 kule esdegeri baski + zirh.
        mix(9, "L06-W9 overrun-attempt", inf = 20, fast = 12, arm = 4, lightGap = 0.34f, heavyGap = 1.25f)
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
    // PERDE III / IV / V — bölüm 23..55  (docs/CAMPAIGN_55.md §9)
    //
    // NEDEN BU BLOK ÜRETİCİ, ELLE YAZILMIŞ 224 SATIR DEĞİL
    // ----------------------------------------------------
    // Act I/II'nin 259 dalgası elle kalibre edildi ve ÖYLE KALIYOR. Yeni 33
    // bölümün 224 dalgası ise tek bir sayıya kilitli: **öldürme geliri**.
    // CAMPAIGN_55.md §8.1 SPI bandını bir KONTROL değil bir ÜRETİM KURALI
    // yapıyor:
    //
    //     R (kadro) -> I(L) = round-robin kadro maliyeti (CANLI TOWER_SPECS)
    //     -> bütçe = 2,00 x I(L)   (bandın tam ortası)
    //     -> hedef öldürme geliri = bütçe - başlangıç Tedariki - 18 x (N-1)
    //     -> dalga kompozisyonu bu geliri sağlayacak şekilde yazılır
    //
    // Bu zincir elle yazıldığında **kaçınılmaz olarak bayatlar**: `rewardGold`
    // ya da kule fiyatı bir kez değiştiğinde 224 satırın tamamı sessizce yanlış
    // bütçeyi üretir. Bu hatayı projede zaten iki kez yaşadık
    // (`WaveMetrics.AEHP` ve `TIGHTENED_WAVE_KILL_SUPPLY`; bkz. CampaignFacts.kt
    // dosya başı). Bu yüzden burada NİYET donduruldu ([LATE_PLAN]: dalga sayısı,
    // kadro, arketip, boss planı) ve KOMPOZİSYON deterministik olarak türetiliyor.
    //
    // SIFIR RNG: `latePlanWaves` saf bir fonksiyondur; aynı girdi her zaman aynı
    // dalga listesini verir (`waveGeneratorIsDeterministic` kilitler).
    //
    // KISITLAR (CAMPAIGN_55.md §7.4) üretici tarafından ZORLANIR:
    //   K-2  bölüm başına 5-7 dalga (perde finali 8)      -> [LATE_PLAN]
    //   K-3  W1 <= en ağır dalganın %45'i, tank=0, boss=0 -> `waveWeights`
    //   K-4  dalga başına <= 56 gövde                     -> `densifyToBodyCap`
    // ======================================================================

    /** K-4 — bir dalgada doğabilecek en fazla GÖVDE sayısı. */
    const val MAX_BODIES_PER_WAVE: Int = 56

    /**
     * Bir geç-perde bölümünün DONDURULMUŞ niyeti (CAMPAIGN_55.md §9 tablosu).
     *
     * @param waves K-2 dalga sayısı.
     * @param roster `R` — tasarlanan kadro büyüklüğü (kaç kule).
     * @param tierThree `kd3` — kadronun kademe 3'e çıkan kule sayısı.
     * @param archetype istenen KARAR: K kalabalık · S hız · Z zırh ·
     *   C karşı-koyma (kalkanlı) · M karma · B boss.
     * @param breath `+N` — nefes bölümü: daha düz rampa, daha hafif karışım.
     * @param bosses dalga no (1 tabanlı) -> o dalgadaki COMMAND_TANK sayısı.
     */
    private class LatePlan(
        val level: Int,
        val waves: Int,
        val roster: Int,
        val tierThree: Int,
        val archetype: Char,
        val breath: Boolean = false,
        val bosses: Map<Int, Int> = emptyMap()
    )

    /**
     * 33 bölümün tamamı. Sütunlar CAMPAIGN_55.md §9 tablosundan BİREBİR.
     *
     * Boss dağılımı (CAMPAIGN_55.md §4.4 — hiçbiri diğerinin tekrarı değil):
     *   L33 son dalgada 1 boss (harita 11 çatallı: eşzamanlı ikinci kol)
     *   L44 son dalgada 2 boss AYNI ANDA (tek hedef modu çöker)
     *   L48 son dalgada 1 boss (Kuşatma Emri ile eşleşecek — Dilim B)
     *   L55 W4'te 1 + son dalgada 3 (kampanyanın toplam sınavı)
     */
    private val LATE_PLAN: List<LatePlan> = listOf(
        //        L   N  R kd3 ark
        LatePlan(23, 6, 5, 2, 'K', breath = true),
        LatePlan(24, 6, 5, 3, 'S'),
        LatePlan(25, 7, 5, 3, 'Z'),
        LatePlan(26, 7, 6, 3, 'M'),
        LatePlan(27, 6, 5, 3, 'C', breath = true),
        LatePlan(28, 7, 6, 3, 'K'),
        LatePlan(29, 7, 6, 4, 'C'),
        LatePlan(30, 7, 6, 4, 'M'),
        LatePlan(31, 6, 5, 3, 'Z', breath = true),
        LatePlan(32, 7, 6, 4, 'S'),
        // kd3 4 -> 3: L33 harita 11'de (10 pad, IKI KOLLU, biri kraterli) perde
        // finali + boss ile birlikte kapsama-farkindali simulasyonda HIC
        // gecilemiyordu. Kadro SAYISI korundu — sermaye kadronun kademe-1
        // maliyetinden turedigi icin dusurmek sermayeyi de keserdi; yalnizca
        // kademe-3 derinligi azaltildi, bu hedef oldurme gelirini (-%18) ve
        // dolayisiyla dalga agirligini dusurur.
        LatePlan(33, 7, 6, 3, 'B', bosses = mapOf(7 to 1)),
        LatePlan(34, 6, 6, 3, 'M', breath = true),
        LatePlan(35, 7, 6, 4, 'C'),
        LatePlan(36, 7, 6, 4, 'S'),
        LatePlan(37, 7, 6, 4, 'Z'),
        LatePlan(38, 6, 6, 4, 'M'),
        LatePlan(39, 7, 6, 4, 'K', breath = true),
        LatePlan(40, 7, 6, 5, 'Z'),
        LatePlan(41, 7, 6, 5, 'C'),
        LatePlan(42, 6, 6, 4, 'M', breath = true),
        LatePlan(43, 7, 6, 5, 'Z'),
        LatePlan(44, 8, 7, 5, 'B', bosses = mapOf(8 to 2)),
        LatePlan(45, 7, 6, 4, 'S', breath = true),
        LatePlan(46, 7, 6, 4, 'C'),
        LatePlan(47, 7, 6, 5, 'S'),
        LatePlan(48, 7, 7, 5, 'B', bosses = mapOf(7 to 1)),
        LatePlan(49, 6, 6, 4, 'M', breath = true),
        LatePlan(50, 7, 7, 5, 'S'),
        LatePlan(51, 7, 7, 5, 'K'),
        LatePlan(52, 7, 7, 6, 'Z'),
        // L53 IKI DEGISIKLIK ALDI (kd3 5 -> 3, arketip 'C' -> 'M').
        //
        // Harita 11 oyunun EN DAR tahtasi: yalnizca 10 pad ve IKI KOLLU, yani
        // kapsama hem az hem bolunmus. Uzerine Act V agirligi ve 'C' (kalkanli
        // yogun) arketipi binince bolum ALTI oyuncu davranisindan yalnizca
        // BIRIYLE geciliyordu — cozulebilir degil ezberlenebilir. Kalkanli zirhi
        // 0,62 oldugu icin dar tahtada kursun neredeyse bir sey yapmiyor ve
        // bolum tek bir dogru kadroya kilitleniyordu.
        //
        // 'M' (karma) ayni tehdidi daha genis bir cevap kumesine acar; kalkanli
        // payi %38 -> %21. Harita 11'de 'M' ikinci kez kullaniliyor (L12 ile) ama
        // aralarinda 41 bolum var — CAMPAIGN_55.md YR-1' zaten bu haritada bir
        // arketip tekrarini kabul ediyor (orada 'B' iki kez).
        LatePlan(53, 7, 6, 3, 'M', breath = true),
        // L54 ARKETIP 'S' -> 'C' (2026-08-19).
        //
        // HATA: L54 ile L50 BIREBIR AYNI GIRDIYI tasiyordu
        // (7 dalga, 7 kadro, 5 kademe-3, 'S'). Uretici saf bir fonksiyon
        // oldugu icin ayni girdi ayni dalga tablosunu veriyor — yani oyuncu
        // 55 bolumluk kampanyada AYNI bolumu iki kez oynuyordu.
        // Ekip bu hata sinifini L53'te zaten yakalayip elle duzeltmisti;
        // bu cift kacmisti. Artik `noTwoLevelsGenerateTheSameWaveTable`
        // testi ucuncu kez kacmasini engelliyor.
        //
        // 'C' (karsi-koyma) secildi: L54 boss'tan ONCEKI son bolum, yani
        // finalin provasi olmali — "belirli bir tehdide dogru cevabi bul"
        // tam olarak bu arketipin sordugu soru. Onceki 'C' L46'da, yani
        // sekiz bolum arayla; Act V'teki digerlerinden ('Z' L52, 'K' L51)
        // ise daha uzak.
        LatePlan(54, 7, 7, 5, 'C'),
        LatePlan(55, 7, 7, 6, 'B', bosses = mapOf(4 to 1, 7 to 3))
    )

    private val LATE_PLAN_BY_LEVEL: Map<Int, LatePlan> = LATE_PLAN.associateBy { it.level }

    /** Geç-perde bölümü mü (üretilmiş dalga tablosu). */
    fun isGeneratedLevel(levelId: Int): Boolean = LATE_PLAN_BY_LEVEL.containsKey(levelId)

    /** Bu bölümün tasarlanan kadro büyüklüğü `R`. 22'nin altında tanımsız (0). */
    fun designedRosterSize(levelId: Int): Int = LATE_PLAN_BY_LEVEL[levelId]?.roster ?: 0

    /** Bu bölümün kadrosunda kademe 3'e çıkan kule sayısı `kd3`. */
    fun designedTierThreeCount(levelId: Int): Int = LATE_PLAN_BY_LEVEL[levelId]?.tierThree ?: 0

    // ---------------------------------------------------------------- bütçe

    /** Kule açılış sırası — `CampaignFacts.unlockedTowersInOrder` ile AYNI kural. */
    private fun unlockOrderAt(level: Int): List<GameConfig.TowerType> =
        GameConfig.TowerType.values()
            .filter { GameConfig.TOWER_SPECS.getValue(it).unlockedAtLevel <= level }
            .sortedWith(
                compareBy(
                    { GameConfig.TOWER_SPECS.getValue(it).unlockedAtLevel },
                    { GameConfig.TOWER_SPECS.getValue(it).buildCost },
                    { it.name }
                )
            )

    /**
     * **I(L) — TASARLANAN KADRO MALİYETİ.** Round-robin: açık kule tipleri
     * açılış sırasında tekrar tekrar seçilir (Gatling omurga kalır, her yeni
     * kilit kadroda bir yer alır). Kadronun tamamı kademe 2, ilk `kd3` üyesi
     * kademe 3. Fiyatlar CANLI `TOWER_SPECS`ten okunur — bu sayı bayatlayamaz.
     */
    internal fun designedLoadoutCost(level: Int): Int {
        val plan = LATE_PLAN_BY_LEVEL[level] ?: return 0
        val order = unlockOrderAt(level)
        var total = 0
        for (i in 0 until plan.roster) {
            val spec = GameConfig.TOWER_SPECS.getValue(order[i % order.size])
            total += spec.buildCost + spec.level2UpgradeCost
            if (i < plan.tierThree) total += spec.upgradeCostFrom(2) ?: 0
        }
        return total
    }

    /**
     * Bu bölümün dalgalarından gelmesi GEREKEN öldürme geliri:
     * `2,00 x I(L) - başlangıç Tedariki - 18 x (N-1)` (CAMPAIGN_55.md §8.1).
     */
    internal fun targetKillSupply(level: Int): Int {
        val plan = LATE_PLAN_BY_LEVEL[level] ?: return 0
        val budget = 2 * designedLoadoutCost(level)
        val starting = GameConfig.levelSpec(level).startingSupply
        return budget - starting - GameConfig.WAVE_CLEAR_SUPPLY_BONUS * (plan.waves - 1)
    }

    // ------------------------------------------------------------ karışım

    private val BODY_ORDER = listOf(
        EnemyType.INFANTRY, EnemyType.FAST_SOLDIER, EnemyType.SHIELDED_TROOPER,
        EnemyType.ARMORED_VEHICLE, EnemyType.TANK
    )

    /** Motorun ödediği Tedarik: ödül x tur çarpanı, `toInt()` ile kırpılır, >= 1. */
    private fun killSupplyOf(type: EnemyType, level: Int): Int {
        val nominal = GameConfig.ENEMY_SPECS.getValue(type).rewardGold
        val mul = GameConfig.actRewardMultiplier(GameConfig.levelSpec(level).act)
        return (nominal * mul).toInt().coerceAtLeast(1)
    }

    /**
     * ARKETİP KARIŞIMLARI — gövde yüzdeleri (piyade, koşucu, kalkanlı, zırhlı, tank).
     *
     * Bir haritanın beş geçişi beş farklı arketip taşır (CAMPAIGN_55.md §6.2);
     * karışım, o geçişin "istenen KARAR"ını görünür kılan şeydir. Perde
     * ilerledikçe ağırlık hafiften ağıra kayar (`actShift`): mutlak tehdit
     * Act III -> Act V arasında belirgin artar ve artışın TAMAMI karışımdan
     * gelir — `actHpMultiplier` 1,55'te DONDURULDU (GameConfig K5).
     */
    private fun archetypeProfile(archetype: Char, act: Int, breath: Boolean): DoubleArray {
        val base = when (archetype) {
            'K' -> doubleArrayOf(0.41, 0.27, 0.215, 0.075, 0.030)  // kalabalık
            'S' -> doubleArrayOf(0.23, 0.48, 0.160, 0.090, 0.040)  // hız
            'Z' -> doubleArrayOf(0.28, 0.36, 0.180, 0.130, 0.050)  // zırh
            'C' -> doubleArrayOf(0.24, 0.28, 0.380, 0.070, 0.030)  // karşı-koyma
            'M' -> doubleArrayOf(0.30, 0.36, 0.210, 0.090, 0.040)  // karma
            else -> doubleArrayOf(0.28, 0.32, 0.240, 0.110, 0.050) // boss bölümü
        }
        val actShift = when (act) {
            3 -> 0.00
            4 -> 0.03
            else -> 0.06
        }
        val shift = actShift + (if (breath) -0.02 else 0.0)
        base[1] -= shift
        base[3] += shift * 0.6
        base[4] += shift * 0.4
        return normalised(base)
    }

    private fun normalised(p: DoubleArray): DoubleArray {
        for (i in p.indices) if (p[i] < 0.0) p[i] = 0.0
        val sum = p.sum()
        for (i in p.indices) p[i] /= sum
        return p
    }

    /** Bu karışımın GÖVDE BAŞINA ürettiği Tedarik. */
    private fun supplyPerBody(p: DoubleArray, level: Int): Double {
        var s = 0.0
        BODY_ORDER.forEachIndexed { i, type -> s += p[i] * killSupplyOf(type, level) }
        return s
    }

    /**
     * K-4'Ü SAĞLAYAN TEK MEKANİZMA: bir dalga 56 gövdeyi aşacaksa karışım
     * AĞIRLAŞIR (piyade/koşucu payı kalkanlı-zırhlı-tank'a kayar); dalga
     * KALABALIKLAŞMAZ.
     *
     * Bu bilinçli bir oynanış kararıdır. Bugün L20/21/22 tek dalgada 88/89/91
     * gövde yolluyor; endless spec'in kendi eşzamanlılık tavanı 60. Ekranda 90
     * hedef hem kare süresi hem OKUNABİLİRLİK sorunudur — oyuncu neyin geldiğini
     * göremez, dolayısıyla hedef seçimi diye bir karar kalmaz. Aynı tehdidi daha
     * AZ ama daha AĞIR gövdeyle vermek kararı geri getirir (önce hangi tank?)
     * ve dalgayı okunur tutar.
     */
    private fun densifyToBodyCap(profile: DoubleArray, targetSupply: Int, cap: Int, level: Int): DoubleArray {
        val p = profile.copyOf()
        var guard = 0
        while (targetSupply / supplyPerBody(p, level) > cap && guard < 200) {
            val takeInf = minOf(p[0], 0.012)
            val takeFast = minOf(p[1], 0.012)
            val moved = takeInf + takeFast
            if (moved <= 1e-9) break
            p[0] -= takeInf
            p[1] -= takeFast
            // AGIRLIGIN COGU KALKANLIYA GIDER — bu bir denge karari:
            // Tedarik basina AEHP (yani "ayni para kac can satin aliyor")
            // kalkanlida 22, zirhlida 38, tankta 46. Yogunlastirmayi zirh/tank
            // uzerinden yapmak ayni butceyle %70 daha agir bir dalga uretirdi ve
            // bolum SPI 2,00 iken sayisal olarak gecilemez hale gelirdi.
            // Kalkanli ayrica DOGRU cevabi da ogretiyor: patlama zirhi bypass
            // eder ve kalkanliya 1,6x vurur (DECISIONS B2), yani kalabalik
            // kalkanli dalgasi "Heavy Cannon getir" der, "sansin varsa gec" demez.
            // Zırhlı da BIRAKILIR: Tedarik başına AEHP zırhlıda 38, kalkanlıda
            // 22, tankta 46 — yani zırhlı yoğunlaştırma için hiçbir zaman
            // verimli seçim değil (aynı parayla %70 daha fazla can satar).
            // Zırhlının kimliği TABAN karışımda korunur, tavan zorlarken değil.
            val fromArm = minOf(p[3], 0.004)
            p[3] -= fromArm
            p[2] += (moved + fromArm) * 0.90
            p[4] += (moved + fromArm) * 0.10
            normalised(p)
            guard++
        }
        return p
    }

    /** En büyük kalan yöntemi: yüzdeleri TAM `bodies` adede çevirir. */
    private fun countsFor(profile: DoubleArray, bodies: Int): IntArray {
        val exact = DoubleArray(5) { profile[it] * bodies }
        val counts = IntArray(5) { exact[it].toInt() }
        var remaining = bodies - counts.sum()
        val order = (0..4).sortedByDescending { exact[it] - counts[it] }
        var i = 0
        while (remaining > 0) {
            counts[order[i % 5]]++
            remaining--
            i++
        }
        return counts
    }

    private fun totalSupplyOf(counts: Array<IntArray>, level: Int): Int {
        var s = 0
        counts.forEach { w -> BODY_ORDER.forEachIndexed { i, t -> s += w[i] * killSupplyOf(t, level) } }
        return s
    }

    /**
     * Toplam geliri hedefe TAM oturtur.
     *
     * Act II+ çarpanıyla gelir değerleri 5/6/9/11/26 olduğundan her tamsayı fark
     * kapatılabilir: 5'lik adımlar piyadeyle, 1-4'lük artık ise piyade -> koşucu
     * (+1) veya piyade -> kalkanlı (+4) takasıyla. Düzeltme her zaman o anki EN
     * KALABALIK dalgaya uygulanır, böylece açılış dalgası (K-3) bozulmaz.
     */
    private fun correctSupply(counts: Array<IntArray>, level: Int, target: Int, caps: IntArray) {
        val value = IntArray(5) { killSupplyOf(BODY_ORDER[it], level) }
        // Gövde SAYISINI değiştirmeden geliri kaydıran dönüşümler (kaynak, hedef).
        // Sıra önemli: en büyük adım önce denenir, en küçük (+1) her artığı kapatır.
        val swaps = listOf(0 to 4, 0 to 3, 0 to 2, 2 to 3, 3 to 4, 0 to 1)
            .sortedByDescending { (from, to) -> value[to] - value[from] }

        // Açılış dalgası (K-3) düzeltmeden MUAF: oraya tank/kalabalık eklemek
        // "kadromu kurduğum dalga" sözleşmesini bozar.
        val editable = counts.indices.filter { it > 0 }.ifEmpty { counts.indices.toList() }

        var guard = 0
        while (guard++ < 20_000) {
            val diff = target - totalSupplyOf(counts, level)
            if (diff == 0) break
            var moved = false
            if (diff >= value[0]) {
                val w = editable.filter { counts[it].sum() < caps[it] }
                    .maxByOrNull { caps[it] - counts[it].sum() }
                if (w != null) { counts[w][0]++; moved = true }
            } else if (diff <= -value[0]) {
                val w = editable.filter { counts[it][0] > 1 }.maxByOrNull { counts[it].sum() }
                if (w != null) { counts[w][0]--; moved = true }
            }
            if (!moved) {
                // Kapasite yok (K-4 tavanı) -> gövde SAYISI sabit, gövde AĞIRLIĞI değişir.
                for ((from, to) in swaps) {
                    val step = value[to] - value[from]
                    if (diff > 0 && step <= diff) {
                        val w = editable.lastOrNull { counts[it][from] > 1 }
                        if (w != null) { counts[w][from]--; counts[w][to]++; moved = true; break }
                    }
                    if (diff < 0 && -step >= diff) {
                        val w = editable.lastOrNull { counts[it][to] > 0 }
                        if (w != null) { counts[w][to]--; counts[w][from]++; moved = true; break }
                    }
                }
            }
            if (!moved) break
        }
    }

    /**
     * Dalga ağırlık rampası. Açılış payı bilinçli olarak düşük: K-3 "W1, en ağır
     * dalganın en fazla %45'i" kuralı AS-1'in mutlak 2.900 AEHP tavanının
     * ölçekle çalışan hâlidir (CAMPAIGN_55.md §2.3) — oyuncunun ilk dalgası hâlâ
     * "kadromu kurduğum" dalgadır.
     */
    private fun waveWeights(plan: LatePlan): DoubleArray {
        val n = plan.waves
        val lo = if (plan.breath) 0.66 else 0.62
        val hi = if (plan.breath) 1.26 else 1.36
        // DIŞBÜKEY rampa (üs 1,25), doğrusal değil. Sebep ölçülmüş: K-4 tavanına
        // dayanan dalgalar yoğunlaştığı için AEHP'leri gelirlerinden HIZLI büyür.
        // Doğrusal gelir rampası bu yüzden W1 -> W2 arasında 2,6x'lik bir AEHP
        // sıçraması üretiyordu ("oyuncu hazırlıksız bir duvara çarpıyor" kuralı).
        // Dışbükey rampa erken adımları küçültür, ağırlığı finale toplar.
        return DoubleArray(n) { lo + (hi - lo) * Math.pow(it / (n - 1).toDouble(), 1.25) }
    }

    /**
     * AÇILIŞ DALGASINI İKİ KURALIN ARASINA OTURTUR.
     *
     * İki bağımsız kural aynı dalgayı sıkıştırıyor:
     *  · **K-3** — W1, bölümün en ağır dalgasının en fazla %45'i olmalı
     *    (oyuncu W1'de hâlâ kadrosunu kuruyor).
     *  · **Duvar kuralı** — hiçbir dalga, o ana kadar görülen en ağır dalganın
     *    2,5 katından fazla olamaz; W2 için "o ana kadar görülen" = W1'dir.
     *    Yani W1 aynı zamanda **yeterince ağır** olmak zorunda.
     *
     * Pencere her zaman boş değildir: W2 <= tepe olduğu için W2/2,4 <= 0,417 x
     * tepe < 0,44 x tepe. Ayar tek bir eksende yapılır (zırhlı <-> kalkanlı, o
     * bitince kalkanlı <-> piyade): gövde sayısı sabit kalır, yalnızca ağırlık
     * kayar. Tedarik farkını [correctSupply] kapatır.
     */
    private fun tuneOpeningWave(counts: Array<IntArray>, plan: LatePlan) {
        if (plan.waves < 2) return
        fun aehp(c: IntArray): Double =
            BODY_ORDER.indices.sumOf { c[it] * WaveMetrics.AEHP.getValue(BODY_ORDER[it]).toDouble() }

        val bossAehp = WaveMetrics.AEHP.getValue(EnemyType.COMMAND_TANK).toDouble()
        val waveAehp = { i: Int -> aehp(counts[i]) + (plan.bosses[i + 1] ?: 0) * bossAehp }
        val peak = (1 until plan.waves).maxOf { waveAehp(it) }
        val floor = waveAehp(1) / 2.4
        val ceiling = peak * 0.44

        var guard = 0
        while (guard++ < 400) {
            val current = aehp(counts[0])
            when {
                current > ceiling && counts[0][3] > 0 -> { counts[0][3]--; counts[0][2]++ }
                current > ceiling && counts[0][2] > 0 -> { counts[0][2]--; counts[0][0]++ }
                current < floor && counts[0][2] > 0 -> { counts[0][2]--; counts[0][3]++ }
                current < floor && counts[0][0] > 1 -> { counts[0][0]--; counts[0][2]++ }
                else -> return
            }
        }
    }

    /** Bir geç-perde bölümünün dalgalarını üretir. Saf ve deterministik. */
    private fun latePlanWaves(plan: LatePlan): List<WaveData> {
        val level = plan.level
        val act = GameConfig.levelSpec(level).act
        val bossSupply = killSupplyOf(EnemyType.COMMAND_TANK, level)
        val bodySupplyTarget = targetKillSupply(level) - plan.bosses.values.sum() * bossSupply

        val weights = waveWeights(plan)
        val weightSum = weights.sum()
        val base = archetypeProfile(plan.archetype, act, plan.breath)

        val perWave = IntArray(plan.waves) { (bodySupplyTarget * weights[it] / weightSum).toInt() }
        perWave[perWave.size - 1] += bodySupplyTarget - perWave.sum()

        // K-4 tavanı BOSS'LARI DA SAYAR: ekranda kaç gövde olduğu sorusu
        // "boss hariç kaç gövde" diye sorulmaz.
        val caps = IntArray(plan.waves) { MAX_BODIES_PER_WAVE - (plan.bosses[it + 1] ?: 0) }

        val counts = Array(plan.waves) { w ->
            val profile = densifyToBodyCap(base, perWave[w], caps[w], level)
            val bodies = (perWave[w] / supplyPerBody(profile, level))
                .toInt().coerceIn(8, caps[w])
            countsFor(profile, bodies)
        }

        // K-3: açılış dalgasında tank YOK — her tank İKİ zırhlıya çevrilir.
        // 1:1 çevirmek açılış dalgasının AEHP'sini çökertiyordu (tank 1.186,
        // zırhlı 417 AEHP) ve W1 -> W2 arasında yapay bir duvar üretiyordu.
        counts[0][3] += 2 * counts[0][4]
        counts[0][4] = 0

        // Once geliri hedefe oturt, sonra acilis dalgasini iki kuralin arasina
        // tasi, sonra geliri TEKRAR oturt: tasima W1 Tedarikini +-2 kaydirir.
        correctSupply(counts, level, bodySupplyTarget, caps)
        tuneOpeningWave(counts, plan)
        correctSupply(counts, level, bodySupplyTarget, caps)

        // Kadans: spawn aralığı perde ilerledikçe daralır, nefes bölümünde açılır.
        // K-1 (bölüm <= 6,5 dk) doğrudan buradan gelir: 56 gövdelik bir dalganın
        // süresi neredeyse tamamen spawn aralığıdır.
        val lightGap = (
            when (act) {
                3 -> 0.44f
                4 -> 0.40f
                else -> 0.36f
            }
            ) + (if (plan.breath) 0.05f else 0f)
        val heavyGap = when (act) {
            3 -> 1.05f
            4 -> 0.95f
            else -> 0.85f
        }

        return (0 until plan.waves).map { w ->
            val c = counts[w]
            mix(
                index = w + 1,
                label = "L$level-W${w + 1} ${lateWaveLabel(plan, w + 1)}",
                inf = c[0], fast = c[1], shield = c[2], arm = c[3], tank = c[4],
                boss = plan.bosses[w + 1] ?: 0,
                lightGap = lightGap, heavyGap = heavyGap, bossGap = 3.00f
            )
        }
    }

    /** İç debug/telemetri etiketi — KULLANICIYA GÖSTERİLMEZ (dosya notu 1). */
    private fun lateWaveLabel(plan: LatePlan, wave: Int): String {
        val bossCount = plan.bosses[wave]
        if (bossCount != null) return if (bossCount > 1) "BOSS-x$bossCount" else "BOSS"
        if (wave == 1) return "opening"
        if (wave == plan.waves) return "finale"
        return when (plan.archetype) {
            'K' -> "swarm-$wave"
            'S' -> "blitz-$wave"
            'Z' -> "armor-$wave"
            'C' -> "shields-$wave"
            'M' -> "combined-$wave"
            else -> "escalation-$wave"
        }
    }

    // ======================================================================
    // Kampanya kaydı
    // ======================================================================

    /**
     * Bölüm no -> dalga listesi. 55 bölüm (CAMPAIGN_55.md K1: 5 perde x 11).
     *
     * L1..L22 elle kalibre edilmiş, YAYINLANMIŞ tablodur ve değişmez.
     * L23..L55 [LATE_PLAN]'dan deterministik olarak üretilir.
     *
     * `by lazy` ZORUNLU: üretici `GameConfig.TOWER_SPECS` ve
     * `GameConfig.levelSpec` okuyor; erken (object başlatma sırasında)
     * değerlendirilirse iki `object` birbirini yarı kurulmuş halde görebilir.
     */
    // ======================================================================
    // L1..L22 — TEK RITME TASINMA (CAMPAIGN_55.md K-2 / K-4)
    //
    // SORUN (olculdu): kampanya IKI FARKLI RITIMDE calisiyordu.
    //   · L1-22 (elle yazilmis): 6-18 dalga · L22 = 18 dalga / 16,1 dk ·
    //     L18/20/21/22 tek dalgada 77/88/89/91 govde yolluyor.
    //   · L23-55 (uretilmis): 5-7 dalga · <= 6,9 dk · <= 56 govde.
    // Ayni oyunun iki yarisi ayni oturum sozlesmesini paylasmiyordu; yenilgi
    // L22'de 16 dakikayi cope atarken L23'te 5 dakikayi atiyordu.
    //
    // COZUM: elle kalibre edilmis 259 dalganin KOMPOZISYONU korunur, yalnizca
    // BOLUM SEKLI degisir — CAMPAIGN_55.md 2.3'un "mevcut dalgalar yeniden
    // bolusturulur, kompozisyonlar korunur" karari. Iki saf donusum:
    //
    //   1) [pickWaves]  — dalga listesi 5-7'ye SEYRELTILIR. Ilk ve SON dalga
    //      her zaman korunur (acilis hâlâ en hafif, final hâlâ finaldir),
    //      arasi esit araliklarla ornekleir. Rampa sekli bozulmaz.
    //   2) [capBodies]  — K-4: bir dalga 56 govdeyi asamaz. Fazlasi esit
    //      araliklarla SEYRELTILIR, yani tip karisimi ve SIRA korunur;
    //      dalga hafifler ama kimligi degismez.
    //
    // Bu, dalgalari yeniden YAZMAKTAN farkli ve kasitli: L1-22'nin kompozisyon
    // kalibrasyonu (kadans, tip karisimi, tanitim sirasi) tek tek olculmustu
    // ve o is korunuyor. Degisen tek sey OTURUM SEKLI.
    // ======================================================================

    /**
     * CAMPAIGN_55.md 9. tablosunun `N` kolonu, L1..L22. K-2: 5-7 dalga
     * (perde finali 8'e kadar). Toplam 133 dalga; L23..L55'in 224'u ile
     * birlikte kampanya **357 dalga** eder — dokumanin hedef rakami.
     */
    private val HANDWRITTEN_TARGET_WAVES: IntArray = intArrayOf(
        // L7/L8 dokumanin 6'si yerine 7: Fuze Bataryasi L7'de aciliyor ve
        // kadroya giriyor (I(L) 495 -> 725). Alti dalgalik gelir o kadroyu
        // SPI bandinin ALTINDA birakiyordu (1,41); yedinci dalga bandi geri
        // getiriyor (1,58) ve K-2'nin 5-7 araligini bozmuyor.
        /* L1..L11  */ 5, 5, 5, 5, 6, 6, 7, 7, 6, 6, 7,
        /* L12..L22 */ 6, 6, 6, 6, 6, 6, 7, 7, 6, 7, 7
    )

    /**
     * Dalga listesini `target` uzunluguna SEYRELTIR.
     *
     * Ilk ve son dalga daima secilir; aradakiler esit araliklarla ornekleir.
     * Ilk dalganin korunmasi K-3 icin zorunlu (o bolumun EN HAFIF dalgasidir,
     * oyuncu orada kadrosunu kurar); son dalganin korunmasi finali (boss
     * dalgalari dahil) yerinde tutar.
     */
    private fun pickWaves(source: List<WaveData>, target: Int): List<WaveData> {
        if (source.size <= target || target < 2) return source
        val n = source.size
        return (0 until target).map { k ->
            source[Math.round(k.toDouble() * (n - 1) / (target - 1)).toInt()]
        }
    }

    /** K-4: dalga basina en fazla [MAX_BODIES_PER_WAVE] govde. */
    private fun capBodies(wave: WaveData): WaveData {
        val n = wave.spawns.size
        if (n <= MAX_BODIES_PER_WAVE) return wave
        val kept = (0 until MAX_BODIES_PER_WAVE).map {
            wave.spawns[(it.toLong() * n / MAX_BODIES_PER_WAVE).toInt()]
        }
        return wave.copy(spawns = kept)
    }

    /** Elle yazilmis bir bolumu yeni oturum sekline tasir. Saf ve deterministik. */
    private fun reshapeHandwritten(level: Int, source: List<WaveData>): List<WaveData> =
        pickWaves(source, HANDWRITTEN_TARGET_WAVES[level - 1])
            .mapIndexed { i, w -> capBodies(w).copy(waveIndex = i + 1) }

    val CAMPAIGN: Map<Int, List<WaveData>> by lazy {
        buildMap {
            val handwritten = listOf(
                LEVEL_01, LEVEL_02, LEVEL_03, LEVEL_04, LEVEL_05, LEVEL_06,
                LEVEL_07, LEVEL_08, LEVEL_09, LEVEL_10, LEVEL_11, LEVEL_12,
                LEVEL_13, LEVEL_14, LEVEL_15, LEVEL_16, LEVEL_17, LEVEL_18,
                LEVEL_19, LEVEL_20, LEVEL_21, LEVEL_22
            )
            handwritten.forEachIndexed { i, waves -> put(i + 1, reshapeHandwritten(i + 1, waves)) }
            LATE_PLAN.forEach { put(it.level, latePlanWaves(it)) }
        }
    }

    const val CAMPAIGN_LEVEL_COUNT: Int = 55

    /** L1..L22 — elle kalibre edilmiş, yayınlanmış dalga tablosu. */
    const val HANDWRITTEN_LEVEL_COUNT: Int = 22

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

    // ======================================================================
    // Faz 10 — BASKI (sustained pressure)
    //
    // Toplam AEHP "bu bolum ne kadar uzun" sorusunu olcer; BASKI "kac kule
    // gerekir" sorusunu olcer. Ikincisi olmadigi icin bir bolum toplamda agir
    // gorunurken pratikte iki kuleyle gecilebiliyordu (testcinin sikayeti).
    //
    // Bir kulenin oldurme hizi sabittir: DPS / dusman cani. Dusmanlar bundan
    // hizli geliyorsa fark BIRIKIR ve sizar — dalganin toplami ne olursa olsun.
    // AEHP zaten referans batarya hasari biriminde oldugu icin (bkz. yukari)
    // AEHP/sn dogrudan "gereken referans DPS"tir.
    // ======================================================================

    /** Dalganin surdurulebilir baskisi: AEHP / spawn penceresi (AEHP/sn). */
    fun wavePressure(wave: WaveData): Float = waveAehp(wave) / spawnWindowSeconds(wave)

    /** Bolumun en agir dalgasinin baskisi. */
    fun peakPressure(waves: List<WaveData>): Float = waves.maxOf { wavePressure(it) }

    /**
     * Referans kule DPS'i = MACHINE_GUN kademe 1 (hasar / atis araligi).
     *
     * AEHP'nin referans bataryasi %50 kursun oldugu icin karsilastirma birimi
     * olarak makinelinin DPS'i dogru secim. Kule tablosundan TURETILIR: atis
     * araligi ya da hasar degistiginde olcut otomatik takip eder.
     */
    val referenceTowerDps: Float
        get() = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN).level1Dps

    /**
     * Baski / referans kule DPS'i.
     *
     * **BU MUTLAK BIR "KAC KULE GEREKIR" SAYISI DEGILDIR** ve oyle okunmamali:
     * spawn penceresini kullanir, yani dusmanlarin spawn bittikten sonra yolda
     * gecirdigi sureyi (kulenin ates etmeye devam ettigi sure) SAYMAZ. Mutlak
     * arz/talep orani `docs/tools/difficulty_audit.py` icinde hesaplanir; o
     * model penceye yol suresini de ekler ve kadroyu isimlendirir.
     *
     * Buradaki sayinin isi KARSILASTIRMA: bolumler arasi rampa, dalga ici
     * siralama ve "bu bolum eski hâline geri dondu mu" kontrolu. Kadans ya da
     * kompozisyon degisirse bu sayi degisir; olcek degisikligi (or. tum canlar
     * x3.5) TUM bolumleri ayni oranda etkiler ve rampayi bozmaz.
     */
    fun peakPressureRatio(waves: List<WaveData>): Float =
        peakPressure(waves) / referenceTowerDps
}
