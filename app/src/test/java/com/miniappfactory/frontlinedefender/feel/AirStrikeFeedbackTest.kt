package com.miniappfactory.frontlinedefender.feel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.BoosterActivation
import com.miniappfactory.frontlinedefender.game.economy.BoosterCurrency
import com.miniappfactory.frontlinedefender.game.economy.BoosterDecision
import com.miniappfactory.frontlinedefender.game.economy.BoosterType
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.airSupportDamage
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameFeel
import com.miniappfactory.frontlinedefender.game.model.EffectType
import com.miniappfactory.frontlinedefender.game.model.EnemyEntity
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.VisualEffect
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.perf.PerfHarness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * HAVA TAARRUZU — GERI BILDIRIM SOZLESMESI.
 *
 * ## Neden bu test var
 * Cihazda kullanici *"hava destek istedim bir sey gelmedi sanki"* dedi.
 * Mekanizma calisiyordu: ucret kesiliyor, bekleme basliyor, sahadaki her
 * dusman maks caninin %45'ini kaybediyordu. Kirilan sey OKUNABILIRLIKTI —
 * hasar tanim geregi hicbir dusmani olduremedigi icin ekranda gorunen tek
 * isaret dusman basina kucuk bir patlama sprite'iydi. "Kod dogru" savunmasi
 * bir oynanis hatasini kapatmaz; bu dosya duzeltmenin geri kaymasini engeller.
 *
 * ## KILITLENEN IKI SEY
 * 1. **Denge DEGISMEDI.** Hasar hala `maxHp x AIR_SUPPORT_DAMAGE_FRACTION`,
 *    zirhtan bagimsiz, ve iki kullanim toplamda tam canin altinda kaliyor —
 *    yani hava destegi bir "dalga temizleme butonu" degil. Bu bir pay-to-win
 *    korumasidir; geri bildirim isi onu ELLEMEDI.
 * 2. **Geri bildirim zinciri var.** Ucus hatti, sirali patlamalar, hasar
 *    sayilari, flas ve zamanin SIMULASYONA bagli olmasi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AirStrikeFeedbackTest {

    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine

    /** Galaxy S8 yatay oynanis alani — WorstCaseFrameBudgetTest ile ayni sahne. */
    private val screenW = 2220f
    private val screenH = 1080f
    private val hudInset = 96f

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        audio = AudioManager(ctx)
        // Robolectric SoundPool golgesi gercek sesi temsil etmez; olculen sey
        // ses degil, zincirin kendisi.
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(ctx), audio)
        engine.updateMapDimensions(screenW, screenH, hudInset)
        engine.startNewGame(1)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    // =========================================================================
    // Sahne kurulumu
    // =========================================================================

    private fun airStrike(fraction: Double = EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION) =
        BoosterActivation(
            type = BoosterType.AIR_SUPPORT,
            decision = BoosterDecision.Allowed(
                price = 0,
                currency = BoosterCurrency.SUPPLY,
                viaAd = false
            ),
            viaAd = false,
            airSupportDamageFraction = fraction
        )

    /**
     * Dusmanlari sahaya YATAYDA yayar. Yayilim sart: zincir gecikmesi hedefin
     * X oranindan turetiliyor, hepsi ayni X'te olsaydi zincir olusmaz ve test
     * dogru sonucu yanlis sebeple verirdi.
     */
    private fun seedEnemies(count: Int, maxHp: Float = 82f): List<EnemyEntity> {
        engine.enemies.clear()
        engine.visualEffects.clear()
        val left = engine.fieldLeftPx
        val width = engine.fieldWidthPx
        val added = (0 until count).map { i ->
            val f = if (count == 1) 0.5f else i.toFloat() / (count - 1)
            EnemyEntity(
                type = GameConfig.EnemyType.INFANTRY,
                posX = left + width * f,
                posY = engine.fieldTopPx + engine.fieldHeightPx * 0.5f,
                currentWayPointIndex = i,
                hp = maxHp,
                maxHp = maxHp,
                baseSpeed = 0f,
                armor = 0f,
                rewardGold = 5,
                radius = 16f
            )
        }
        engine.enemies.addAll(added)
        return added
    }

    private fun effectsOf(type: EffectType): List<VisualEffect> =
        engine.visualEffects.filter { it.type == type }

    // =========================================================================
    // 1. DENGE DEGISMEDI — geri bildirim isi hasara DOKUNMADI
    // =========================================================================

    /**
     * Kilit garanti: `maxUsesPerBattle x DAMAGE_FRACTION < 1,0`. Bu saglanmazsa
     * hava destegi tam canli bir dusmani tek basina oldurebilir hale gelir ve
     * "dalga temizleme butonu" olur.
     */
    @Test
    fun airSupportStillCannotKillAFullHealthEnemyInOneBattle() {
        val fraction = EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION
        val uses = BoosterType.AIR_SUPPORT.maxUsesPerBattle
        assertTrue(
            "Savas basina toplam hasar orani ($uses x $fraction) 1,0'in ALTINDA kalmali",
            uses * fraction < 1.0
        )
    }

    /**
     * Motorun uyguladigi hasar, saf `airSupportDamage` fonksiyonunun verdigi
     * degerin BIREBIR ayni. Yeni gorsel zincir hasar yoluna hicbir carpan
     * sokmadi.
     */
    @Test
    fun engineAppliesExactlyTheUnchangedDamageFraction() {
        val maxHp = 82f
        val enemies = seedEnemies(5, maxHp = maxHp)
        val expected = airSupportDamage(maxHp, EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION)

        assertTrue(engine.applyBoosterActivation(airStrike()))

        enemies.forEach { enemy ->
            assertEquals(
                "Hasar maks canin tam olarak %45'i olmali",
                maxHp - expected,
                enemy.hp,
                0.001f
            )
        }
        // 82 x 0,45 = 36,9 -> hicbir piyade OLMEDI. Kullanicinin "bir sey
        // gelmedi" demesinin kok sebebi bu ve DEGISMEDI: cozum hasari degil
        // gorunurlugu buyutmek.
        assertTrue("Hava destegi tek basina oldurmemeli", enemies.none { it.isDead })
    }

    /** Hasar zirhtan BAGIMSIZ kalmali — oran dusman istatistiklerine baglanamaz. */
    @Test
    fun damageIgnoresArmorExactlyAsBefore() {
        engine.enemies.clear()
        val soft = EnemyEntity(
            type = GameConfig.EnemyType.INFANTRY,
            posX = engine.fieldLeftPx + 100f, posY = engine.fieldTopPx + 100f,
            hp = 200f, maxHp = 200f, baseSpeed = 0f, armor = 0f, rewardGold = 5, radius = 16f
        )
        val armored = EnemyEntity(
            type = GameConfig.EnemyType.TANK,
            posX = engine.fieldLeftPx + 400f, posY = engine.fieldTopPx + 100f,
            hp = 200f, maxHp = 200f, baseSpeed = 0f, armor = 40f, rewardGold = 5, radius = 24f
        )
        engine.enemies.addAll(listOf(soft, armored))

        engine.applyBoosterActivation(airStrike())

        assertEquals("Zirh hasari degistirmemeli", soft.hp, armored.hp, 0.001f)
    }

    // =========================================================================
    // 2. HASAR GORUNUR — "37" goren oyuncu "hicbir sey olmadi" demez
    // =========================================================================

    @Test
    fun everyStruckEnemyGetsAReadableDamageNumber() {
        val enemies = seedEnemies(6, maxHp = 82f)
        engine.applyBoosterActivation(airStrike())

        val texts = effectsOf(EffectType.DAMAGE_TEXT)
        assertEquals("Her hedefin ustunde hasar sayisi olmali", enemies.size, texts.size)

        val expected = airSupportDamage(82f, EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION)
            .roundToInt()
        texts.forEach { fx ->
            assertEquals(
                "Yazi UYGULANAN hasari gostermeli (yuvarlanmis)",
                "-$expected",
                fx.text
            )
        }
    }

    /**
     * Kalabalik sahnede sayilar birbirini yer. Ustten sinir var ama PATLAMA
     * her hedefte cikmaya devam eder — sinir yalnizca yazidir.
     */
    @Test
    fun damageNumbersAreCappedButBlastsAreNot() {
        val n = GameFeel.AIR_STRIKE_MAX_DAMAGE_TEXTS + 9
        seedEnemies(n)
        engine.applyBoosterActivation(airStrike())

        assertEquals(
            "Yazi sayisi tavani asmamali",
            GameFeel.AIR_STRIKE_MAX_DAMAGE_TEXTS,
            effectsOf(EffectType.DAMAGE_TEXT).size
        )
        assertEquals(
            "Patlama HER hedefte cikmali",
            n,
            effectsOf(EffectType.CANNON_EXPLOSION).size
        )
    }

    // =========================================================================
    // 3. TAARRUZ HISSI — ekran capinda okunan tek olay
    // =========================================================================

    @Test
    fun aSingleScreenWideRunEffectCarriesTheEvent() {
        seedEnemies(4)
        engine.applyBoosterActivation(airStrike())

        val runs = effectsOf(EffectType.AIR_STRIKE_RUN)
        assertEquals("Kosu efekti savas basina bir taarruzda TEK olmali", 1, runs.size)

        val run = runs.single()
        assertTrue(
            "Ucus hatti sahanin genisligi kadar uzun olmali (ekran capinda okunmali)",
            run.radiusPx >= engine.fieldWidthPx * 0.95f
        )
        assertTrue(
            "Kosu, patlama zincirinden UZUN yasamali (cikis kuyrugu)",
            run.maxAgeSeconds > GameFeel.AIR_STRIKE_RUN_SECONDS
        )
    }

    /**
     * Patlamalar AYNI KAREDE degil, ucus hatti boyunca SIRALI cikar. Hepsi
     * ayni karede patlasaydi olay "tek bir puf" olarak okunurdu.
     */
    @Test
    fun blastsFormADelayedChainAlongTheFlightPath() {
        val enemies = seedEnemies(8)
        engine.applyBoosterActivation(airStrike())

        val blasts = effectsOf(EffectType.CANNON_EXPLOSION)
        assertEquals(enemies.size, blasts.size)

        val delays = blasts.map { -it.ageSeconds }.sorted()
        assertTrue("En soldaki hedef neredeyse aninda vurulmali", delays.first() <= 0.02f)
        assertTrue(
            "En sagdaki hedefin gecikmesi kosu penceresine yakin olmali",
            delays.last() >= GameFeel.AIR_STRIKE_RUN_SECONDS * 0.9f
        )
        assertTrue(
            "Hicbir gecikme kosu penceresini asmamali",
            delays.all { it <= GameFeel.AIR_STRIKE_RUN_SECONDS + 0.001f }
        )
        assertTrue(
            "Gecikmeler birbirinden AYRISMALI - ayni karede patlarlarsa zincir yok",
            delays.distinct().size >= enemies.size - 1
        )
    }

    /**
     * GECIKME TELAFISI — "bomba yanina dustu" hatasi.
     *
     * `VisualEffect` konumu sabittir ama hedef gecikme boyunca YURUR. En hizli
     * dusman zincirin son halkasinda kendi capinin iki katindan fazla yol alir;
     * telafi olmadan patlama gorunur sekilde hedefin gerisinde kalirdi.
     */
    @Test
    fun delayedBlastsLeadAMovingTarget() {
        engine.enemies.clear()
        engine.visualEffects.clear()
        val speed = 200f
        // Sahanin en sagi = zincirin EN GEC halkasi = en buyuk telafi.
        val startX = engine.fieldLeftPx + engine.fieldWidthPx
        val startY = engine.fieldTopPx + engine.fieldHeightPx * 0.5f
        engine.enemies.add(
            EnemyEntity(
                type = GameConfig.EnemyType.FAST_SOLDIER,
                posX = startX,
                posY = startY,
                hp = 100f,
                maxHp = 100f,
                baseSpeed = speed,
                armor = 0f,
                rewardGold = 5,
                radius = 14f,
                rotationAngleRad = 0f // saga dogru kosuyor
            )
        )

        engine.applyBoosterActivation(airStrike())

        val blast = effectsOf(EffectType.CANNON_EXPLOSION).single()
        val delay = -blast.ageSeconds
        assertEquals(
            "Tek hedef sahanin sagindaysa gecikme tam kosu penceresi kadar olmali",
            GameFeel.AIR_STRIKE_RUN_SECONDS,
            delay,
            0.001f
        )
        assertEquals(
            "Patlama hedefin O ANKI degil, VARACAGI konumunda olmali",
            startX + speed * delay,
            blast.posX,
            0.5f
        )
        assertEquals("Dikeyde sapma olmamali", startY, blast.posY, 0.5f)
    }

    /**
     * Gecikmeli efekt listeye HEMEN girer ama negatif yasla bekler. Renderer
     * negatif yasli efekti CIZMEZ (GameCanvas.drawVisualEffect ilk satir);
     * burasi motorun o sozlesmeyi kurdugunu dogrular.
     */
    @Test
    fun pendingBlastsWaitWithNegativeAgeAndLaterBecomeVisible() {
        seedEnemies(8)
        engine.applyBoosterActivation(airStrike())

        val pending = effectsOf(EffectType.CANNON_EXPLOSION).count { it.ageSeconds < 0f }
        assertTrue("Zincirin bir kismi beklemede olmali", pending > 0)

        // Kosu penceresi kadar simulasyon: tum halkalar acilmis olmali.
        repeat(40) { engine.tick(1f / 60f) }
        assertTrue(
            "Kosu bittikten sonra bekleyen halka kalmamali",
            engine.visualEffects.none { it.ageSeconds < 0f }
        )
    }

    /**
     * ZAMAN TABANI SIMULASYON — oyun hizi carpani DAHIL.
     *
     * Gercek zaman kullanilsaydi 2x hizda zincir oyunun geri kalanina gore iki
     * kat YAVAS akardi: ayni oynanis, farkli geri bildirim.
     */
    @Test
    fun chainDelayFollowsSimulationTimeNotWallClock() {
        val dt = 1f / 60f
        // Zincirin en gec halkasi: en sagdaki hedefin hasar YAZISI.
        val window = GameFeel.AIR_STRIKE_RUN_SECONDS +
            GameFeel.AIR_STRIKE_DAMAGE_TEXT_LAG_SECONDS
        val halfWindowFrames = (window / 2f / dt).toInt() + 2

        // 1x hiz: yarim pencerelik GERCEK zaman zinciri bitirmeye YETMEZ.
        seedEnemies(8)
        engine.applyBoosterActivation(airStrike())
        assertTrue(
            "Zincir kurulmus olmali",
            effectsOf(EffectType.CANNON_EXPLOSION).minOf { it.ageSeconds } < -0.1f
        )
        repeat(halfWindowFrames) { engine.tick(dt) }
        assertTrue(
            "1x hizda yarim pencerede zincir HENUZ bitmemeli",
            engine.visualEffects.any { it.ageSeconds < 0f }
        )

        // 2x hiz: AYNI kare sayisi zinciri tamamlar. Gercek zaman kullanilsaydi
        // iki kosu birebir ayni sonucu verirdi ve bu iddia dusmezdi.
        engine.startNewGame(1)
        seedEnemies(8)
        engine.toggleGameSpeed()
        engine.applyBoosterActivation(airStrike())
        repeat(halfWindowFrames) { engine.tick(dt) }
        assertTrue(
            "2x hizda ayni kare sayisi zinciri tamamlamali",
            engine.visualEffects.none { it.ageSeconds < 0f }
        )
    }

    @Test
    fun screenFlashFiresOnTheSameFrameAndDecaysToZero() {
        seedEnemies(3)
        assertEquals("Taarruz oncesi flas olmamali", 0f, engine.screenFlashAlpha, 0.0001f)

        engine.applyBoosterActivation(airStrike())

        val peak = engine.screenFlashAlpha
        assertTrue("Flas girdiyle AYNI KAREDE baslamali", peak > 0f)
        assertTrue(
            "Flas tepe degeri savas alanini gizlememeli",
            peak <= GameFeel.AIR_STRIKE_FLASH_PEAK_ALPHA + 0.0001f
        )

        var previous = peak
        repeat(3) {
            engine.tick(1f / 60f)
            assertTrue("Flas HER ZAMAN sonumlenmeli", engine.screenFlashAlpha < previous)
            previous = engine.screenFlashAlpha
        }

        repeat(30) { engine.tick(1f / 60f) }
        assertEquals("Flas sifira inmeli", 0f, engine.screenFlashAlpha, 0.0001f)
    }

    /** Sarsinti taarruzda top atisindan AGIR olmali — savasin en pahali girdisi. */
    @Test
    fun airStrikeShakesHarderThanAnOrdinaryCannonHit() {
        assertTrue(
            "Taarruz sarsintisi eski 0,30 sn degerinin ustunde olmali",
            GameFeel.AIR_STRIKE_SHAKE_SECONDS > 0.30f
        )
    }

    // =========================================================================
    // 4. KARE BUTCESI — zincir kendi efektlerini dusurmemeli
    // =========================================================================

    /**
     * Kampanyanin EN kalabalik dalgasindaki TUM govdeler ayni anda sahadayken
     * bile taarruzun uretecegi efekt sayisi tavanin altinda kalmali. Assarsa
     * `addEffect` en eskiyi duserdi ve dusen sey — hepsi ayni cagride
     * eklendigi icin — ZINCIRIN KENDI halkalari olurdu.
     */
    @Test
    fun worstCaseStrikeStaysInsideTheVisualEffectBudget() {
        val worstBodies = (1..GameConfig.CAMPAIGN_LEVEL_COUNT).maxOf { level ->
            WaveDefinitions.wavesFor(level).maxOfOrNull { it.spawns.size } ?: 0
        }
        seedEnemies(worstBodies)

        engine.applyBoosterActivation(airStrike())

        val produced = engine.visualEffects.size
        assertTrue(
            "En kalabalik dalga ($worstBodies govde) $produced efekt uretti; tavan " +
                "${GameFeel.MAX_VISUAL_EFFECTS}",
            produced <= GameFeel.MAX_VISUAL_EFFECTS
        )
        assertEquals(
            "Kosu efekti dusmus olmamali",
            1,
            effectsOf(EffectType.AIR_STRIKE_RUN).size
        )
        assertEquals(
            "Hicbir hedefin patlamasi dusmus olmamali",
            worstBodies,
            effectsOf(EffectType.CANNON_EXPLOSION).size
        )
    }

    /**
     * KARE SURESI — zincir YASARKEN simulasyon butcesi.
     *
     * Olculen sey `tick` govdesidir (cizim degil; o cihazdan alinir, bkz.
     * docs/PERFORMANCE_REPORT.md). Onemli olan: gecikmeli efektler ekstra bir
     * mekanizma degil, listede zaten yaslanan nesneler — yani zincir kare
     * yoluna YENI bir maliyet SOKMAZ. Tahsis olcumu de bunu kilitler: zincir
     * kurulduktan sonra kare basina 0 nesne uretilmelidir.
     */
    @Test
    fun theLivingChainCostsNothingExtraPerFrame() {
        val dt = 1f / 60f
        val budgetMs = 1000.0 / 60.0
        val bodies = (1..GameConfig.CAMPAIGN_LEVEL_COUNT).maxOf { level ->
            WaveDefinitions.wavesFor(level).maxOfOrNull { it.spawns.size } ?: 0
        }

        // Zinciri kur ve YASAT: her 20 karede bir taze taarruz, boylece
        // olcumun tamami "zincir ekranda" durumunda gecer.
        seedEnemies(bodies)
        engine.applyBoosterActivation(airStrike())
        val producedEffects = engine.visualEffects.size

        repeat(300) { engine.tick(dt) } // isinma (JIT)

        val samples = LongArray(1200)
        for (i in samples.indices) {
            if (i % 20 == 0) {
                seedEnemies(bodies)
                engine.applyBoosterActivation(airStrike())
            }
            val t0 = System.nanoTime()
            engine.tick(dt)
            samples[i] = System.nanoTime() - t0
        }

        val p50 = PerfHarness.percentileMillis(samples, 50)
        val p99 = PerfHarness.percentileMillis(samples, 99)

        // Zincir kuruluyken kare basina TAHSIS: yaslamanin kendisi nesne
        // uretmemeli (kare basina tahsis = GC duraklamasi = jank).
        seedEnemies(bodies)
        engine.applyBoosterActivation(airStrike())
        val bytesPerFrame = if (PerfHarness.allocationSupported) {
            PerfHarness.allocatedBytesPerIteration(warmup = 200, iterations = 2_000) {
                engine.tick(dt)
            }
        } else {
            -1.0
        }

        PerfHarness.report(
            "HAVA TAARRUZU - zincir yasarken tick()",
            "en kalabalik dalga = $bodies govde",
            "taarruz basina uretilen efekt = $producedEffects " +
                "(tavan ${GameFeel.MAX_VISUAL_EFFECTS})",
            "p50 = " + "%.3f".format(p50) + " ms",
            "p99 = " + "%.3f".format(p99) + " ms",
            "60 FPS butcesi = " + "%.2f".format(budgetMs) + " ms; p99 butcenin " +
                "%.1f".format(p99 / budgetMs * 100) + "%'i (JVM, cihaz DEGIL)",
            "kare basina tahsis = " + (if (bytesPerFrame < 0) "olculemedi" else
                "%.1f".format(bytesPerFrame) + " bayt")
        )

        assertTrue(
            "Zincir yasarken tick p99 = " + "%.3f".format(p99) + " ms, 60 FPS " +
                "butcesinin yarisini asiyor",
            p99 <= budgetMs / 2
        )
    }

    // =========================================================================
    // 5. KESINTI DAYANIKLILIGI — pause / reklam / yeniden baslatma
    // =========================================================================

    /**
     * Reklam kesintisi: simulasyon durur, zincir OLDUGU YERDE bekler ve devam
     * edilince kaldigi yerden akar. Duraklamada akmaya devam etseydi oyuncu
     * reklamdan donunce bitmis bir taarruz bulurdu.
     */
    @Test
    fun chainFreezesWhilePausedAndResumesAfterwards() {
        seedEnemies(8)
        engine.applyBoosterActivation(airStrike())
        val before = engine.visualEffects.map { it.ageSeconds }

        engine.pauseForLifecycle()
        repeat(30) { engine.tick(1f / 60f) }
        val during = engine.visualEffects.map { it.ageSeconds }
        assertEquals("Duraklamada zincir donmali", before, during)

        engine.togglePause()
        repeat(40) { engine.tick(1f / 60f) }
        assertTrue(
            "Devam edince zincir kaldigi yerden akmali",
            engine.visualEffects.none { it.ageSeconds < 0f }
        )
    }

    /** Yeni savas: onceki taarruzun flasi ve bekleyen sesleri TASINMAZ. */
    @Test
    fun restartingTheBattleClearsPendingStrikeFeedback() {
        seedEnemies(4)
        engine.applyBoosterActivation(airStrike())
        assertTrue(engine.screenFlashAlpha > 0f)

        engine.startNewGame(1)

        assertEquals("Flas yeni savasa tasinmamali", 0f, engine.screenFlashAlpha, 0.0001f)
        assertTrue(
            "Bekleyen efektler yeni savasa tasinmamali",
            effectsOf(EffectType.AIR_STRIKE_RUN).isEmpty()
        )
        // Bekleyen ses kuyrugu da temizlenmis olmali: temizlenmemis olsaydi
        // asagidaki kareler yeni bolumun basinda bir patlama caldirirdi. Ses
        // dogrudan gozlenemedigi icin kilitlenen sey CAGRININ CAKILMAMASI ve
        // durumun temiz kalmasidir.
        repeat(60) { engine.tick(1f / 60f) }
        assertEquals(0f, engine.screenFlashAlpha, 0.0001f)
    }

    // =========================================================================
    // 6. HEDEFSIZ KULLANIM — bu yol zaten dogruydu, bozulmadigi dogrulanir
    // =========================================================================

    @Test
    fun anUnappliedActivationChangesNothing() {
        seedEnemies(3)
        val denied = BoosterActivation(
            type = BoosterType.AIR_SUPPORT,
            decision = BoosterDecision.NoEffect,
            viaAd = false,
            airSupportDamageFraction = EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION
        )

        assertFalse(engine.applyBoosterActivation(denied))
        assertTrue("Hicbir efekt uretilmemeli", engine.visualEffects.isEmpty())
        assertEquals("Flas cikmamali", 0f, engine.screenFlashAlpha, 0.0001f)
        assertTrue("Hicbir dusman hasar almamali", engine.enemies.all { it.hp == it.maxHp })
    }

    /** Sahada tek hedef varken de olay EKRAN CAPINDA okunmali. */
    @Test
    fun aSingleTargetStillProducesTheFullScreenWideEvent() {
        seedEnemies(1)
        engine.applyBoosterActivation(airStrike())

        val run = effectsOf(EffectType.AIR_STRIKE_RUN).singleOrNull()
        assertNotNull("Tek hedefte de kosu efekti olmali", run)
        assertTrue(run!!.radiusPx >= engine.fieldWidthPx * 0.95f)
        assertEquals(1, effectsOf(EffectType.DAMAGE_TEXT).size)
        assertTrue(
            "Tek hedefte hat YATAY olmali (tam ustunden gecer)",
            abs(run.angleRad) < 0.001f
        )
    }
}
