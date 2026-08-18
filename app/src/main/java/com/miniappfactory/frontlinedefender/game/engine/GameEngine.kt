package com.miniappfactory.frontlinedefender.game.engine

import androidx.compose.ui.geometry.Offset
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.audio.HapticsFeedback
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.BattleResources
import com.miniappfactory.frontlinedefender.game.economy.BoosterActivation
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.airSupportDamage
import com.miniappfactory.frontlinedefender.game.economy.applyBoosterToResources
import com.miniappfactory.frontlinedefender.game.economy.starHealthFromLeaks
import com.miniappfactory.frontlinedefender.game.economy.starsFor
import com.miniappfactory.frontlinedefender.game.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import kotlin.random.Random

/**
 * Faz 14 - ZINCIR (kill-streak) takibi.
 *
 * NEDEN AYRI VE SAF BIR SINIF: `GameEngine` yapicisinda `SaveManager(Context)`
 * ve `AudioManager(Context)` istedigi icin saf JUnit'te ornek uretilemiyor
 * (bkz. StarRatingTest'teki test-edilebilirlik borcu notu). Zincir kurallari
 * motorun icine gomulseydi ESIKLER hic test edilemezdi. Burada Android
 * bagimliligi YOK, yani ComboTrackerTest gercek nesneyi surer.
 *
 * OYNANISA ETKISI SIFIR: zincir ne altin, ne hasar, ne hiz verir. Yalnizca
 * geri bildirimin (olcek / renk / ses / hit stop) siddetini surer. Bu bilincli
 * bir karar: denge tablolari (WaveDefinitions, EconomyConfig) baska ajanlarin
 * sahipliginde ve bir "combo bonusu" onlarin cozdugu tedarik egrisini sessizce
 * bozardi.
 *
 * ZAMAN TABANI: [age] SIMULASYON dt'si ile beslenir (oyun hizi carpani DAHIL).
 * Gercek zaman kullanilsaydi 2x hizda ayni dalga daha uzun bir zincir uretirdi:
 * ayni oynanis, farkli geri bildirim.
 */
class ComboTracker(
    private val windowSeconds: Float = COMBO_WINDOW_SECONDS,
    private val thresholds: List<Int> = COMBO_TIER_THRESHOLDS
) {
    /** Aktif zincirdeki oldurme sayisi. Zincir yoksa 0. */
    var count: Int = 0
        private set

    /** 0 = zincir yok. 1..thresholds.size arasi kademe. */
    var tier: Int = 0
        private set

    /** Zincirin kopmasina kalan SIMULASYON suresi. */
    var timeRemainingSeconds: Float = 0f
        private set

    val isActive: Boolean get() = timeRemainingSeconds > 0f

    /** Bu bolumde ulasilan en yuksek kademe (istatistik/test). */
    var peakTier: Int = 0
        private set

    /**
     * Bir oldurme kaydet.
     *
     * @return zincir bu oldurmede bir kademe TIRMANDIYSA yeni kademe,
     *   tirmanmadiysa 0. Cagiran yalnizca sifirdan farkli donuste patlama
     *   uretir, her oldurmede degil.
     */
    fun registerKill(): Int {
        count = if (isActive) count + 1 else 1
        timeRemainingSeconds = windowSeconds
        val newTier = tierFor(count)
        val climbed = newTier > tier
        tier = newTier
        if (newTier > peakTier) peakTier = newTier
        return if (climbed) newTier else 0
    }

    /** Zincir penceresini yaslandirir; pencere dolunca zincir KOPAR. */
    fun age(dt: Float) {
        if (timeRemainingSeconds <= 0f) return
        timeRemainingSeconds -= dt
        if (timeRemainingSeconds <= 0f) reset()
    }

    /** Zinciri koparir. [peakTier] KORUNUR. */
    fun reset() {
        count = 0
        tier = 0
        timeRemainingSeconds = 0f
    }

    /** Bolum sinirinda tam sifirlama. */
    fun resetAll() {
        reset()
        peakTier = 0
    }

    fun tierFor(kills: Int): Int {
        var t = 0
        for (i in thresholds.indices) if (kills >= thresholds[i]) t = i + 1
        return t
    }

    companion object {
        /**
         * Zincir penceresi. 2.6 sn bilincli: makineli tufek ve top birlikte
         * calisirken oldurmeler 0.6-1.2 sn araliga duser, yani calisan bir
         * savunma hatti zincir KURAR; tek kuleli erken bolumlerde zincir
         * kendiliginden kopar ve tirmanma bir odul olarak kalir.
         */
        const val COMBO_WINDOW_SECONDS = 2.6f

        /** Kademe esikleri (oldurme sayisi). 4 kademe, 4 tirmanma ani. */
        val COMBO_TIER_THRESHOLDS: List<Int> = listOf(3, 6, 10, 16)

        /** Yuzen "+4g" yazisina "x7" eklenmeye baslanan esik. */
        const val COMBO_LABEL_MIN_KILLS = 3

        val MAX_TIER: Int get() = COMBO_TIER_THRESHOLDS.size
    }
}

/**
 * Faz 14 - HIT STOP ve gorsel efekt butcesi sabitleri.
 *
 * GameConfig'e KONULMADI: GameConfig kampanya/denge ajaninin sahipliginde ve
 * bunlarin hicbiri bir denge degeri degil. Simulasyonun ilerleyisini
 * DURDURURLAR ama hiz, hasar, altin ya da can uretmezler.
 */
internal object GameFeel {
    /**
     * Hit stop tavani. 80 ms uzeri "oyun takildi" olarak okunur; altinda
     * kalan degerler "agir vurus" olarak okunur (2-5 kare).
     */
    const val HIT_STOP_MAX_SECONDS = 0.08f

    const val HIT_STOP_BOSS_KILL = 0.075f
    const val HIT_STOP_TANK_KILL = 0.055f
    const val HIT_STOP_VEHICLE_KILL = 0.038f
    const val HIT_STOP_BASE_LEAK = 0.060f
    const val HIT_STOP_COMBO_TIER = 0.030f

    /**
     * Ekranda ayni anda yasayabilecek gorsel efekt sayisi tavani.
     *
     * Neden gerekli: 18 dusmanlik bir dalgada 4 kule ates ederken namlu alevi,
     * isabet kivilcimi, olum patlamasi ve coin yazisi ayni karede birikebilir.
     * Ustten sinir yoksa cizim maliyeti ve tahsis baskisi dogrusal artar.
     * Tavana gelindiginde EN ESKI efekt dusurulur: yeni geri bildirim HER
     * ZAMAN gorunur, kaybolan sey zaten sonmek uzere olandir.
     */
    const val MAX_VISUAL_EFFECTS = 96

    // ------------------------------------------------------------------------
    // HAVA TAARRUZU — Hava Destegi guclendiricisinin geri bildirim zinciri
    //
    // BURADAKI HICBIR SAYI BIR DENGE DEGERI DEGIL. Hasar orani
    // (EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION = 0,45), fiyat ve savas basi
    // kullanim hakki EKONOMI katmanindadir ve DEGISMEDI: iki kullanim x 0,45
    // = 0,90 < 1,0 garantisi ("hava destegi tek basina hicbir dusmani
    // olduremez", yani dalga temizleme butonu degildir) ekonomi testleriyle
    // kilitli. Asagidakiler yalnizca o hasarin GORUNUR olmasini saglar.
    // ------------------------------------------------------------------------

    /**
     * Ucagin sahayi bastan basa gecme suresi. Patlamalar bu pencereye yayilir.
     *
     * NEDEN 0,42 sn: hasar TEK KAREDE uygulanir (simulasyon degismedi), yalniz
     * gorsel zincir yayilir. Pencere buyudukce en uzaktaki dusmanin can barinin
     * dusmesi ile ustundeki patlama arasindaki fark buyur ve olay "gecikmis"
     * hissedilir. 0,42 sn hem bir SIRA olarak okunur (~7 kare arayla inen
     * bombalar) hem de o farki tek bir "kosu" izlenimi icinde tutar.
     */
    const val AIR_STRIKE_RUN_SECONDS = 0.42f

    /** Ucagin sahadan cikisi + duman izinin sonme kuyrugu. */
    const val AIR_STRIKE_TAIL_SECONDS = 0.30f

    /** Tek bir bomba patlamasinin omru. */
    const val AIR_STRIKE_BLAST_SECONDS = 0.50f

    /** Hasar sayisinin ekranda kalma suresi — okunacak kadar uzun. */
    const val AIR_STRIKE_DAMAGE_TEXT_SECONDS = 0.90f

    /**
     * Hasar sayisi patlamadan bu kadar SONRA cikar: sayi patlamanin parlak
     * karesinin altinda kaybolmasin, once vurus gorunsun sonra olcu.
     */
    const val AIR_STRIKE_DAMAGE_TEXT_LAG_SECONDS = 0.07f

    /**
     * Taarruz sarsintisi. Onceki deger 0,30 idi ve top atisiyla (0,25) neredeyse
     * ayniydi — savasin en pahali tek girdisi, siradan bir top mermisi kadar
     * agir hissediyordu. Yine de tavana yakin degil: sarsinti okunabilirligi
     * bozmamali ve HER ZAMAN sonumlenerek sifira iner.
     */
    const val AIR_STRIKE_SHAKE_SECONDS = 0.55f

    /** Ekran flasinin omru. 0,2 sn ustu "ekran beyazladi" olarak okunur. */
    const val AIR_STRIKE_FLASH_SECONDS = 0.16f

    /**
     * Flasin TEPE saydamligi. 0,22 bilincli olarak dusuk: flas bir vurgu
     * isaretidir, savas alanini gizleyen bir perde degil. Sonumleme karesel
     * (easeInCubic'e yakin) oldugu icin ilk kareden sonra hizla siliniyor.
     */
    const val AIR_STRIKE_FLASH_PEAK_ALPHA = 0.22f

    /**
     * Ustunde hasar SAYISI cikacak en fazla hedef.
     *
     * Iki gerekce, ikisi de gorsel:
     *  1) 20'den fazla yuzen sayi ust uste biner ve hicbiri okunmaz — sayinin
     *     tek isi olcuyu bildirmek, kalabalik onu yok eder.
     *  2) Efekt butcesi: taarruz en kotu durumda 1 (kosu) + N (patlama) + 20
     *     (sayi) slot ister; N = 75'e kadar tavan asilmaz, yani taarruz
     *     KENDI efektlerini dusurmez.
     * Sayi alan hedefler rota uzerinde EN ILERIDEKILER, yani oyuncunun o an
     * en cok onemsedikleri. PATLAMA HER HEDEFTE cikar, sinir yalnizca yazidir.
     */
    const val AIR_STRIKE_MAX_DAMAGE_TEXTS = 20

    /** Patlama capinin dusman yaricapina orani — bomba dusmandan buyuk gorunur. */
    const val AIR_STRIKE_BLAST_RADIUS_FACTOR = 1.7f
}

/**
 * ENTITY KIMLIGI — kare yolundaki tahsis kaynagi.
 *
 * `GameEntities`in varsayilani `UUID.randomUUID().toString()`. Olculdu
 * (`FramePathAllocationTest`, ayni JVM, ayni kosu):
 *
 *     UUID.randomUUID().toString()  = 384 bayt, ~600 ns / cagri
 *     "e" + artan sayac             =  48 bayt, ~100 ns / cagri
 *
 * Bu bir mikro-optimizasyon degil, kare yolundadir: her ATIS bir mermi, her
 * SPAWN bir dusman uretir. Son kademe Gatling'in atis araligi 0,20 sn; 11
 * pad'li bir Act II haritasinda saniyede ~35 mermi olusur. Ustelik
 * `UUID.randomUUID()` KRIPTOGRAFIK bir `SecureRandom` cagrisidir ve o uretec
 * process genelinde PAYLASILIR/KILITLIDIR — mermi kimliginin tahmin edilemez
 * olmasi gereken hicbir sebep yok.
 *
 * NEDEN `GameEntities.kt` DEGISTIRILMEDI: o dosya kampanya ajaninin
 * sahipliginde. Varsayilan orada duruyor; kimlik burada, URETIM NOKTASINDA
 * aciktan veriliyor. Sonuc ayni, catisma yok.
 *
 * IS PARCACIGI: yalnizca oyun dongusu (ana thread) cagirir. Senkronizasyon
 * BILINCLI olarak yok — kilit almak tam da kacinilan maliyeti geri getirirdi.
 */
internal object EntityIds {
    private var counter: Long = 0L

    /** Process omru boyunca tekrarlamayan, ucuz kimlik. */
    fun next(): String = "e" + (counter++)
}

/**
 * KARE SURESI OLCERI — cihazda calisan enstrumantasyon.
 *
 * NEDEN VAR: bu oyunun performansi bugune kadar hic GERCEK CIHAZDA
 * olculmedi ve JVM sayilari cihazi temsil etmez. Kullanici cihaz testini
 * kendisi yapiyor; elinde ekstra bir arac olmadan tek satirlik bir
 * `adb logcat -s FDPerf` ile kare butcesini gorebilmeli.
 *
 * NE RAPORLAR — ORTALAMA DEGIL YUZDELIK: ortalama kare suresi jank'i tam
 * olarak gizleyen sayidir. p50 = 14 ms, p99 = 45 ms olan bir oyun "takiliyor"
 * diye sikayet alir; ortalamasi hâlâ guzeldir.
 *
 *   frame = iki `withFrameNanos` geri cagrisi arasindaki sure, yani GERCEK
 *           kare araligi (dusen kare buraya 33 ms olarak yansir).
 *   sim   = `tick` govdesinin suresi. Kalan sure = Compose recomposition +
 *           cizim + sistem. Ikisini ayirmak sart: "yavas" cevabi cizimde mi
 *           simulasyonda mi sorusuna cevap vermez.
 *   jank  = 16,6 ms'yi asan kare yuzdesi. Hedef < %5.
 *
 * TAHSISI: pencere tamponlari BIR KEZ ayrilir, siralama YERINDE yapilir.
 * Kare basina tahsis SIFIR; yalnizca 300 karede bir tek log satiri uretilir.
 */
internal class FramePerfMonitor(
    private val windowFrames: Int = 300,
    private val logger: (String) -> Unit = { android.util.Log.i(TAG, it) }
) {
    private val frameNanos = LongArray(windowFrames)
    private val simNanos = LongArray(windowFrames)
    private val scratch = LongArray(windowFrames)
    private var index = 0
    private var windowsReported = 0

    fun record(frameDeltaSeconds: Float, simDurationNanos: Long) {
        frameNanos[index] = (frameDeltaSeconds * 1_000_000_000.0).toLong()
        simNanos[index] = simDurationNanos
        index++
        if (index < windowFrames) return
        index = 0
        windowsReported++
        emit()
    }

    private fun emit() {
        var jank = 0
        for (v in frameNanos) if (v > JANK_THRESHOLD_NANOS) jank++
        val jankPct = jank * 100f / windowFrames

        System.arraycopy(frameNanos, 0, scratch, 0, windowFrames)
        java.util.Arrays.sort(scratch)
        val f50 = ms(scratch[pct(50)])
        val f95 = ms(scratch[pct(95)])
        val f99 = ms(scratch[pct(99)])
        val fMax = ms(scratch[windowFrames - 1])

        System.arraycopy(simNanos, 0, scratch, 0, windowFrames)
        java.util.Arrays.sort(scratch)
        val s50 = ms(scratch[pct(50)])
        val s95 = ms(scratch[pct(95)])
        val s99 = ms(scratch[pct(99)])

        logger(
            "pencere#$windowsReported n=$windowFrames | " +
                "kare p50=${fmt(f50)} p95=${fmt(f95)} p99=${fmt(f99)} max=${fmt(fMax)} ms | " +
                "sim p50=${fmt(s50)} p95=${fmt(s95)} p99=${fmt(s99)} ms | " +
                "jank=${fmt(jankPct.toDouble())}% (hedef <5%)"
        )
    }

    private fun pct(p: Int): Int =
        ((p / 100.0) * (windowFrames - 1)).toInt().coerceIn(0, windowFrames - 1)

    private fun ms(nanos: Long): Double = nanos / 1_000_000.0

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

    companion object {
        const val TAG = "FDPerf"

        /** 60 FPS butcesi. 90 Hz panelde 11,1 ms olurdu. */
        const val JANK_THRESHOLD_NANOS = 16_666_667L
    }
}

enum class GameState {
    MAIN_MENU,
    /** Faz 4: kampanya haritasi / bolum secme. Savas HUD'i cizilmez. */
    LEVEL_SELECT,
    PREPARATION,
    WAVE_RUNNING,
    PAUSED,
    VICTORY,
    DEFEAT
}

class GameEngine(
    val saveManager: SaveManager,
    val audioManager: AudioManager,
    /**
     * Faz 14 — DOKUNSAL GERI BILDIRIM DIKISI.
     *
     * SAF KOTLIN arayuz ([HapticsFeedback]): motor `Vibrator`i da,
     * `View`i da, Compose'u da TANIMAZ. `null` varsayilani bugunku
     * davranistir, yani mevcut cagri yerleri ve testler etkilenmez.
     *
     * Akis yerine dogrudan cagri olmasinin gerekcesi arayuzun KDoc'unda:
     * `StateFlow` ayni degeri tekrar yaymaz (arka arkaya iki kademe-2
     * tirmanisi TEK titresim uretirdi) ve akis toplama bir kare gecikir —
     * oysa asagidaki zincir dalinin kendi kurali "uc kanal AYNI KAREDE".
     */
    private val haptics: HapticsFeedback? = null
) {
    private val _gameState = MutableStateFlow(GameState.MAIN_MENU)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _gold = MutableStateFlow(GameConfig.INITIAL_GOLD)
    val gold: StateFlow<Int> = _gold.asStateFlow()

    private val _lives = MutableStateFlow(GameConfig.INITIAL_BASE_LIVES)
    val lives: StateFlow<Int> = _lives.asStateFlow()

    private val _currentWaveIndex = MutableStateFlow(0)
    val currentWaveIndex: StateFlow<Int> = _currentWaveIndex.asStateFlow()

    private val _preparationTimer = MutableStateFlow(GameConfig.PREPARATION_TIME_SECONDS.toFloat())
    val preparationTimer: StateFlow<Float> = _preparationTimer.asStateFlow()

    private val _gameSpeed = MutableStateFlow(1.0f)
    val gameSpeed: StateFlow<Float> = _gameSpeed.asStateFlow()

    private val _selectedBuildSpot = MutableStateFlow<BuildSpot?>(null)
    val selectedBuildSpot: StateFlow<BuildSpot?> = _selectedBuildSpot.asStateFlow()

    private val _selectedTower = MutableStateFlow<TowerEntity?>(null)
    val selectedTower: StateFlow<TowerEntity?> = _selectedTower.asStateFlow()

    /**
     * BIRAKMA ONIZLEMESI. Build cubugunda bir kart basili tutuldugunda o kule
     * tipi buraya yazilir ve `GameCanvas` secili pad'in etrafina O KULENIN
     * gercek menzil halkasini cizer.
     *
     * Neden gerekli: menziller artik 150 (Gatling) ile 270 (Frost Field) ref-px
     * arasinda degisiyor. Sabit 170'lik notr halka Frost Field icin acikca
     * yanlis bilgi verirdi ve oyuncu kulenin kapsama alanini yalnizca kurup
     * satarak ogrenebilirdi — "birakma sonucu her zaman gorunur olmali" kurali.
     */
    private val _previewTowerType = MutableStateFlow<GameConfig.TowerType?>(null)
    val previewTowerType: StateFlow<GameConfig.TowerType?> = _previewTowerType.asStateFlow()

    /** Kart basildi/birakildi. `null` = notr onizlemeye don. */
    fun setPreviewTowerType(type: GameConfig.TowerType?) {
        _previewTowerType.value = type
    }

    /**
     * Onizleme halkasinin REFERANS menzili. META YUKSELTME DAHIL: panelde bir
     * menzil gosterip sahada baskasini kullanmak, "14 hasar gosterip 17 vurmak"
     * ile ayni sessiz tutarsizlik olurdu.
     */
    fun previewRangeRef(type: GameConfig.TowerType?): Float {
        val spec = type?.let { GameConfig.TOWER_SPECS[it] } ?: return GameConfig.BUILD_PREVIEW_RANGE_PX
        // Onizleme YENI kurulacak kuleyi gosterir, yani daima ilk kademeyi.
        return spec.tier(1).range * metaRangeMultiplier
    }

    private val _screenShake = MutableStateFlow(Offset.Zero)
    val screenShake: StateFlow<Offset> = _screenShake.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    // ------------------------------------------------------------------------
    // Faz 4 — KAMPANYA DURUMU
    // ------------------------------------------------------------------------

    private val _currentLevelId = MutableStateFlow(1)
    val currentLevelId: StateFlow<Int> = _currentLevelId.asStateFlow()

    /**
     * Aktif bolumun dalga sayisi. HUD "WAVE n/N" icin bunu okumalidir;
     * `GameConfig.WAVES.size` artik yanlis cevabi verir (o liste 6 dalgalik eski
     * tek-bolum listesi, bolum basina dalga sayisi 6..18 arasinda degisiyor).
     */
    private val _totalWaves = MutableStateFlow(0)
    val totalWaves: StateFlow<Int> = _totalWaves.asStateFlow()

    /** Son kazanilan yildiz sayisi (0-3) — VICTORY modali icin. */
    private val _lastEarnedStars = MutableStateFlow(0)
    val lastEarnedStars: StateFlow<Int> = _lastEarnedStars.asStateFlow()

    /**
     * SAVAS DONGUSU KIMLIGI. Her [startNewGame] cagrisinda BIR ARTAR ve baska
     * hicbir yerde degismez.
     *
     * Neden ayri bir sayac: guclendiriciler savasa kapsamlidir, yani yeni savas
     * baslarken HUD sayaclarinin ve `CampaignProgressImpl.beginBattle` cagrisinin
     * tam o anda tetiklenmesi gerekir. `gameState`e bakmak bunun icin YETMEZ:
     * PAUSED -> "yeniden basla" ve DEFEAT -> "tekrar dene" akislarinin ikisi de
     * PREPARATION uretir, ama PREPARATION zaten bir onceki savasin degeriyse
     * StateFlow ayni degeri iki kez yaymaz ve UI yeni savasin basladigini
     * KACIRIR — oyuncu bir onceki savastan kalmis "kullanildi" rozetleriyle
     * yeni savasa girer.
     */
    private val _battleEpoch = MutableStateFlow(0)
    val battleEpoch: StateFlow<Int> = _battleEpoch.asStateFlow()

    /** Aktif bolumun konfigurasyonu. Tek kaynak: GameConfig.CAMPAIGN. */
    var levelSpec: GameConfig.LevelSpec = GameConfig.levelSpec(1)
        private set

    /**
     * Bu bolumdeki us caninin TAVANI — taban + meta yukseltme bonusu; yani
     * [startNewGame] icinde `_lives`e yazilan degerin ta kendisi.
     *
     * Us Tamiri'nin asamayacagi sinir budur. Formulun iki yerde ayri ayri
     * yazilmamasi kasitli: taban ile bonusun toplandigi tek bir yer olmazsa
     * "tamir 20'yi asamaz ama savas 24 canla basliyor" turu sessiz bir
     * uyusmazlik dogar.
     */
    val maxLives: Int get() = levelSpec.maxBaseLives + metaLivesBonus

    /**
     * Bu savasta usse SIZAN dusman sayisi (kaybedilen can).
     *
     * Meta bonusundan bagimsizdir: bonus hem baslangic canina hem [maxLives]'a
     * ayni miktarda girdigi icin farkta sadelesir. Yildiz hesabinin ve zafer
     * ekranindaki "kac sizinti daha az" ipucunun tek dogru girdisi budur.
     */
    val livesLost: Int get() = (maxLives - _lives.value).coerceAtLeast(0)

    /**
     * ZAFER aninda yildiz ve "Kusursuz Savunma" hesabina giren can —
     * **meta yukseltmeden ARINDIRILMIS**.
     *
     * ## Neden meta yukseltme yildiza girmiyor
     * "Tahkimat" (maks us cani) rank 5'te cani 20'den 30'a cikariyor. Pay meta
     * DAHIL, payda taban 20 iken oyuncu 12 sizinti verip 3 yildiz, 10 sizinti
     * verip Kusursuz Savunma madalyasi (+80 coin) aliyordu: meta yukseltme bir
     * yildiz hilesine donusmustu.
     *
     * Iki cozum vardi. Paydayi [maxLives] yapmak matematigi duzeltirdi ama
     * Tahkimat'i bir CEZAYA cevirirdi — 30 canli oyuncunun 3 yildiz icin 3
     * sizintiya, 20 canlinin 2 sizintiya hakki olurdu; oyuncu satin aldigi
     * seyle zorlastirilmis olurdu. Secilen cozum: **yildizi meta bonusundan
     * tamamen bagimsiz kilmak.** Pay da payda da taban can uzerinden gider,
     * yani her rankta 3 yildiz AYNI sizinti sayisini gerektirir. Tahkimat
     * ne yildiz kazandirir ne de zorlastirir; aldigi sey hayatta kalma
     * marjidir — `effectiveStarHealth` deseninin ta kendisi ("geri verilen /
     * fazladan can, yildiz degil dayaniklilik satin alir").
     *
     * En az 1: meta sayesinde ayakta kalan oyuncuya 0 yildiz demek zaferi
     * iptal etmek olurdu ve `resolveLevelClear` (stars > 0 sartli) patlardi.
     * **Yalnizca zafer dalinda okunur**; yenilgide anlami yoktur.
     */
    val victoryStarHealth: Int
        get() = starHealthFromLeaks(levelSpec.maxBaseLives.coerceAtLeast(1), livesLost)

    /**
     * Son zaferde yildiz hesabina GERCEKTEN giren can (guclendirici/takviye
     * duzeltmesi uygulanmis hali). Zafer ekraninin "3 yildiza ne kadar kaldi"
     * ipucu bunu okur — `lastEarnedStars` ile ayni anda yazilir.
     */
    var lastStarHealth: Int = 0
        private set

    /**
     * CIZILEN haritanin id'si. `levelSpec.mapId`'den FARKLI olabilir: o haritanin
     * bitmap'i APK'da yoksa motor `GameConfig.MAP_FALLBACK_ID` geometrisine duser
     * ki oynanis ile cizili harita ayrismasin. Bkz. GameConfig.SHIPPED_MAP_IDS.
     */
    var activeMapId: Int = GameConfig.MAP_FALLBACK_ID
        private set

    /** Aktif bolumun OLCULMUS harita geometrisi (normalize koordinatlar). */
    private var activeLevel: LevelData = LevelData.forMapId(GameConfig.MAP_FALLBACK_ID)

    // ------------------------------------------------------------------------
    // META YUKSELTMELER — kalici ilerlemenin oynanisa YANSIDIGI yer.
    //
    // Bunlar olmadan yukseltme dukkanini acmak, calismayan bir sey satmak
    // olurdu. Bolum yuklenirken BIR KEZ okunur; savas icinde degismez.
    // ------------------------------------------------------------------------
    private var metaDamageMultiplier: Float = 1f
    private var metaRangeMultiplier: Float = 1f
    private var metaSalvageRate: Float = 0.70f
    private var metaSupplyBonus: Int = 0
    private var metaLivesBonus: Int = 0

    /** Aktif turun (Act) dusman carpanlari. */
    private var actHpMul: Float = 1f
    private var actRewardMul: Float = 1f

    /**
     * Yildiz hesabina girecek can. Varsayilan KIMLIK fonksiyonu, yani baglanmazsa
     * davranis oncekiyle birebir ayni.
     *
     * Ekonomi katmani (`CampaignProgressImpl.starHealthFor`) Us Tamiri ile geri
     * verilen cani dusmek zorunda — tamir hayatta kalma satin alir, yildiz ve
     * coin ASLA. O bilgi ekonomi nesnesinde; motor onu tanimaz. Bu dikisi
     * `GameScreen` tek satirla baglar:
     *
     *     gameEngine.starHealthAdjuster = campaignProgress::starHealthFor
     *
     * (GameScreen.kt bu ajanin dosyasi degil — bkz. docs/TOWER_REBALANCE.md
     * "Baglanmasi gereken dikis".)
     */
    var starHealthAdjuster: (Int) -> Int = { it }

    /**
     * Etkiler `MetaUpgrades`'in KENDI turetilmis alanlarindan okunur, burada
     * yeniden hesaplanmaz. Ayni matematigi iki yerde tutmak tam olarak AEHP
     * hatasinin sebebiydi (tablo elle yaziliydi, karar degisti, tablo kaldi).
     */
    private fun refreshMetaUpgrades() {
        val m = saveManager.loadMetaUpgrades()
        metaDamageMultiplier = m.damageMultiplier.toFloat()
        metaRangeMultiplier = m.rangeMultiplier.toFloat()
        metaSalvageRate = m.salvageRatio.toFloat()
        // Taban degerler LevelSpec'ten geliyor; burada yalnizca EK kismi lazim.
        metaSupplyBonus = m.startingSupply - EconomyConfig.BASE_STARTING_SUPPLY
        metaLivesBonus = m.maxBaseHealth - EconomyConfig.BASE_MAX_HEALTH
    }

    /** Aktif bolumun dalga listesi — WaveDefinitions.CAMPAIGN'den. */
    private var levelWaves: List<GameConfig.WaveData> = emptyList()

    /** Aktif haritanin normalize rotalari (catallanan haritalarda 2 tane). */
    private var routes: List<List<PointF>> = emptyList()

    /**
     * Rota atamasi icin SEED'LI RNG. Render'dan ayri; yalnizca spawnEnemy
     * tuketir. Ayni bolum -> ayni rota dizisi (replay/test/denge dogrulanabilir).
     */
    private var routeRng: Random = Random(GameConfig.ROUTE_RNG_SEED_BASE)

    /**
     * PAUSED'a girmeden ONCEKI durum. Bu olmadan resume her zaman WAVE_RUNNING'e
     * donuyordu: hazirlik sirasinda pause/resume yapan oyuncu kalan hazirlik
     * suresini KAYBEDIYOR ve dalga aninda basliyordu.
     */
    private var stateBeforePause: GameState = GameState.PREPARATION

    // Level map dimension (in Canvas pixel units)
    var mapWidthPx: Float = 1280f
    var mapHeightPx: Float = 720f

    // ------------------------------------------------------------------------
    // Faz 3 — OYNANIS DIKDORTGENI (letterbox sonrasi).
    //
    // Harita 16:9 (1.776), telefonlar 19.5:9 / 20:9. Crop yapmak build pad'leri
    // ekran disina atar ve seviyeyi oynanamaz kilar (game-asset-draw skill'i,
    // "En-boy orani cakismasi"). Bu yuzden FIT + letterbox: harita en-boy
    // oranini koruyarak sigdirilir, artan yer koyu zemin kalir ve TUM oynanis
    // koordinatlari bu dikdortgene gore hesaplanir.
    // ------------------------------------------------------------------------
    var fieldLeftPx: Float = 0f
        private set
    var fieldTopPx: Float = 0f
        private set
    var fieldWidthPx: Float = 1280f
        private set
    var fieldHeightPx: Float = 720f
        private set

    /** Referans tuvalde (1920) tanimli gorsel boyutlari px'e cevirme carpani. */
    var renderScale: Float = 1f
        private set

    // Scaled pixel waypoints and build spot coordinates
    /**
     * BIRINCIL rotanin px koordinatlari. Geriye donuk uyum icin korunuyor
     * (GameCanvas.drawDebugPath bunu okuyor). Coklu rota icin `scaledRoutes`.
     */
    var scaledWaypoints = listOf<PointF>()
    /** Aktif haritanin TUM rotalari, px koordinatlarinda. En az 1 eleman. */
    var scaledRoutes: List<List<PointF>> = emptyList()
    var scaledBuildSpots = listOf<BuildSpot>()

    val towers = mutableListOf<TowerEntity>()
    val enemies = mutableListOf<EnemyEntity>()
    val projectiles = mutableListOf<ProjectileEntity>()
    val visualEffects = mutableListOf<VisualEffect>()

    // Current wave spawn queue
    private val pendingWaveSpawns = mutableListOf<GameConfig.WaveEnemySpawn>()
    private var timeUntilNextSpawn = 0f
    private var screenShakeDuration = 0f

    /**
     * Faz 14 - ZINCIR. Motorun disindan (renderer/test) OKUNUR, yazilmaz.
     * Compose snapshot state DEGIL: GameCanvas bunu draw lambda'sinin ICINDE
     * okur ve kare zaten `frameTick` ile gecersiz kilindigi icin ekstra bir
     * gozlemlenebilir gerekmiyor. StateFlow olsaydi her oldurmede tum HUD
     * recompose olurdu.
     */
    val combo = ComboTracker()

    /**
     * Faz 14 - HIT STOP: sifirdan buyukken SIMULASYON durur.
     *
     * DENGEYE ETKISI YOK: duran sey tum simulasyondur, bir parcasi degil.
     * Dusman da kule de mermi de AYNI kareyi kaybeder, yani hasar/hiz/menzil
     * oranlari birebir korunur. Kaybedilen tek sey duvar saati zamanidir ve
     * bolumler sure ile degil DALGA ile kazaniliyor.
     */
    private var hitStopRemainingSeconds = 0f

    // ------------------------------------------------------------------------
    // EKRAN FLASI — tek karelik "buyuk olay" isareti (bugun yalniz hava taarruzu)
    //
    // StateFlow DEGIL, `combo` ile ayni gerekce: `GameCanvas` bunu draw
    // lambda'sinin ICINDE okur ve kare zaten `frameTick` ile gecersiz kilinir.
    // StateFlow olsaydi flasin her karesi TUM HUD'u recompose ederdi.
    // ------------------------------------------------------------------------
    private var screenFlashRemaining = 0f
    private var screenFlashDuration = 0f

    /**
     * Cizim icin flas saydamligi, 0 = flas yok.
     *
     * Sonumleme KARESEL: ilk kare tam siddette, sonrasi hizla siliniyor
     * (lineer sonumleme "ekran yavasca karariyor" gibi okunur, vurus gibi
     * degil). Sarsinti ile ayni kural: her zaman sifira iner.
     */
    val screenFlashAlpha: Float
        get() {
            if (screenFlashRemaining <= 0f || screenFlashDuration <= 0f) return 0f
            val f = (screenFlashRemaining / screenFlashDuration).coerceIn(0f, 1f)
            return GameFeel.AIR_STRIKE_FLASH_PEAK_ALPHA * f * f
        }

    /**
     * GECIKMELI SES KUYRUGU — "yuvarlanan bombardiman" icin.
     *
     * Neden gerekli: hava taarruzu ekranda 0,42 saniyeye yayilan bir ZINCIR,
     * kulakta ise tek bir patlamaydi. Goz sirali patlama gorurken kulagin tek
     * atis duymasi olayi kucultuyordu.
     *
     * Zaman tabani `dt`, yani OYUN HIZI CARPANI DAHIL ve duraklamada (PAUSED /
     * reklam) akmaz: gorsel zincirle ses ayni saatte yurur. Kuyruk savas
     * sifirlanmasinda temizlenir, yoksa bir onceki savasin patlamasi yeni
     * bolumun ilk karesinde calardi.
     *
     * Tahsis: savas basina en fazla iki taarruz x 2 eleman. Kare yolunda
     * DEGIL — `ageCosmetics` yalnizca var olan elemanlari yaslar.
     */
    private class PendingSound(var delaySeconds: Float, val sound: AudioManager.SoundEffect)

    private val pendingSounds = mutableListOf<PendingSound>()

    init {
        loadLevel(GameConfig.levelSpec(1))
    }

    /** Ust HUD seridinin px yuksekligi; oynanis alani bunun altinda baslar. */
    private var hudTopInsetPx: Float = 0f

    /**
     * Yerlesimin hangi HARITA icin hesaplandigi. Kaydirma orani artik haritaya
     * gore degistigi icin ("ust bant ne kadar bos") olcuyu kisa devre yapan
     * kontrolun bunu da bilmesi gerekir: ayni ekranda baska bir haritaya gecmek
     * yerlesimi DEGISTIRIR.
     */
    private var dimsMapId: Int = -1

    fun updateMapDimensions(width: Float, height: Float, topInsetPx: Float = hudTopInsetPx) {
        if (width <= 0f || height <= 0f) return
        if (topInsetPx != hudTopInsetPx) {
            hudTopInsetPx = topInsetPx
            // olcu degistiyse asagidaki "ayni ise cik" kisa devresini atla
            mapWidthPx = -1f
        }
        // Faz 2: bu fonksiyon GameCanvas'in draw lambda'sindan cagriliyor. Kare
        // invalidation'i duzeltildikten sonra draw saniyede 60 kez kosuyor; olcu
        // degismedigi halde her karede iki liste yeniden uretmek saf tahsis
        // baskisidir (GC duraklamasi = jank). Olcu ayniysa cik.
        if (width == mapWidthPx && height == mapHeightPx &&
            dimsMapId == activeMapId && scaledWaypoints.isNotEmpty()
        ) {
            return
        }
        mapWidthPx = width
        mapHeightPx = height
        dimsMapId = activeMapId

        // FIT (crop DEGIL): en-boy orani korunur.
        //
        // KAYDIRMA ARTIK OLCULU (P0 duzeltmesi). Eskiden burada sabit
        // `MAP_SAFE_TOP_FRAC = 0.10` vardi ve HUD'in OLCULEN yuksekligi ile
        // haritanin GERCEK geometrisi hicbir yerde karsilastirilmiyordu:
        // 360 dp ekranda 0,10 * 338 dp = 34 dp kaydirma, 56 dp HUD -> ust
        // pad'ler seridin altinda kaliyor ve dokunus almiyordu (cihazda
        // goruldu, harita 4).
        //
        // `GameConfig.mapSafeTopFrac` bu orani haritanin en ust pad'i ve yolun
        // en ust noktasindan TURETIR ve gerekirse NEGATIF doner; negatif deger
        // "HUD'un altina sigmiyor, kucult" demektir (letterbox). Bu yuzden
        // asagidaki iki satirin disinda hicbir sey degismedi: formul ayni,
        // orani artik veri veriyor.
        val rect = GameConfig.computeFieldRect(activeMapId, width, height, hudTopInsetPx)
        fieldWidthPx = rect.width
        fieldHeightPx = rect.height
        fieldLeftPx = rect.left
        fieldTopPx = rect.top
        renderScale = rect.renderScale

        // Faz 4: AKTIF bolumun geometrisi. Eskiden burada `LevelData.LEVEL_1`
        // sabit kodluydu, yani hangi bolum yuklenirse yuklensin ilk karede
        // harita 1'in yolu/pad'leri geri geliyordu.
        rescaleGeometry()

        // Update positions of existing towers
        towers.forEach { tower ->
            scaledBuildSpots.find { it.id == tower.buildSpotId }?.let { spot ->
                tower.posX = spot.normX
                tower.posY = spot.normY
            }
        }
    }

    /**
     * Normalize geometriyi (rotalar + pad'ler) mevcut oynanis dikdortgenine tasir.
     * Olcu degistiginde ve bolum yuklendiginde cagrilir; kare dongusunde DEGIL.
     */
    private fun rescaleGeometry() {
        scaledRoutes = routes.map { route ->
            route.map {
                PointF(fieldLeftPx + it.x * fieldWidthPx, fieldTopPx + it.y * fieldHeightPx)
            }
        }
        scaledWaypoints = scaledRoutes.firstOrNull() ?: emptyList()

        // Devre disi pad'ler haritada HIC gorunmez — Act I'de MENZIL DISI
        // olduklari (uzerlerine kurulan kule hicbir seye ates edemez), Act
        // II'de ayrica krater kisiti geregi. Gorunur birakip insaati blokemek
        // yanlis olurdu: oyuncu neden secemedigini anlamaz.
        // Kisit yalnizca spec'in KENDI haritasi ciziliyorsa uygulanir; yedek
        // haritaya dusuldugunde pad id'leri baska bir haritaya ait olur ve
        // alakasiz pad'leri kapatirdi.
        val disabled = if (activeMapId == levelSpec.mapId) {
            levelSpec.disabledPadIds.toSet()
        } else {
            emptySet()
        }

        scaledBuildSpots = activeLevel.buildSpots
            .filter { it.id !in disabled }
            .map {
                BuildSpot(
                    it.id,
                    fieldLeftPx + it.normX * fieldWidthPx,
                    fieldTopPx + it.normY * fieldHeightPx
                )
            }

        // Update positions of existing towers
        towers.forEach { tower ->
            scaledBuildSpots.find { it.id == tower.buildSpotId }?.let { spot ->
                tower.posX = spot.normX
                tower.posY = spot.normY
            }
        }
    }

    /**
     * Bolumu yukler: harita geometrisi, rotalar, pad'ler, dalgalar ve baslangic
     * degerleri TAMAMEN spec'ten gelir.
     *
     * Yedek harita: spec'in haritasinin bitmap'i APK'da yoksa geometri
     * `MAP_FALLBACK_ID`'den alinir. Aksi halde dusmanlar boyali yolun disinda
     * yururdu ve bolum oynanamaz olurdu.
     */
    private fun loadLevel(spec: GameConfig.LevelSpec) {
        levelSpec = spec
        _currentLevelId.value = spec.levelId

        // Kalici ilerleme + tur olceklendirmesi bolum basinda okunur.
        refreshMetaUpgrades()
        actHpMul = GameConfig.actHpMultiplier(spec.act)
        actRewardMul = GameConfig.actRewardMultiplier(spec.act)

        activeMapId = if (spec.mapId in GameConfig.SHIPPED_MAP_IDS) {
            spec.mapId
        } else {
            GameConfig.MAP_FALLBACK_ID
        }

        activeLevel = LevelData.forMapId(activeMapId)
        // IKINCI KOL yalnizca ogretme dilimi bittikten sonra (bolum 9+).
        // Once burada kosulsuz `routesForMapId` cagriliyordu, yani harita 1/2/4
        // catallanmasi OGRETICI bolumlerde de acikti — bkz.
        // GameConfig.ALT_ROUTE_FIRST_LEVEL gerekcesi.
        val allRoutes = LevelData.routesForMapId(activeMapId)
        routes = if (GameConfig.usesAlternateRoutes(spec.levelId)) {
            allRoutes
        } else {
            listOf(allRoutes.first())
        }
        routeRng = Random(GameConfig.ROUTE_RNG_SEED_BASE + spec.levelId * 7919L)

        levelWaves = WaveDefinitions.wavesFor(spec.levelId)
        _totalWaves.value = levelWaves.size

        updateMapDimensions(mapWidthPx, mapHeightPx)
        rescaleGeometry()
    }

    /**
     * @param levelNo 1..22. Varsayilan degeri AKTIF bolum, yani parametresiz
     *   `startNewGame()` = "bu bolumu yeniden basla" (Retry / Restart).
     */
    fun startNewGame(levelNo: Int = _currentLevelId.value) {
        towers.clear()
        enemies.clear()
        projectiles.clear()
        visualEffects.clear()

        loadLevel(GameConfig.levelSpec(levelNo))

        // Meta yukseltmeler taban degerin USTUNE biner.
        _gold.value = levelSpec.startingSupply + metaSupplyBonus
        _lives.value = levelSpec.maxBaseLives + metaLivesBonus
        _score.value = 0
        _currentWaveIndex.value = 0
        _gameSpeed.value = 1.0f
        _selectedBuildSpot.value = null
        _selectedTower.value = null
        _lastEarnedStars.value = 0
        screenShakeDuration = 0f
        _screenShake.value = Offset.Zero
        hitStopRemainingSeconds = 0f
        clearScreenFlashAndCues()
        combo.resetAll()

        setupWave(0)
        _gameState.value = GameState.PREPARATION
        stateBeforePause = GameState.PREPARATION
        _preparationTimer.value = GameConfig.PREPARATION_TIME_SECONDS.toFloat()

        // EN SONDA: epoch degistiginde savas kurulumu TAMAMLANMIS olmali, yoksa
        // dinleyici yarim kurulmus bir savasi okur.
        _battleEpoch.value += 1
    }

    /**
     * Kampanya / bolum secme ekranina doner.
     *
     * Bu fonksiyon EKSIKTI: Pause ve Defeat modallerindeki "MAIN MENU" butonlari
     * `startNewGame()` cagiriyordu, yani ana menuye gitmek yerine ayni bolumu
     * bastan baslatiyordu.
     */
    fun returnToLevelSelect() {
        deselectAll()
        _gameSpeed.value = 1.0f
        _screenShake.value = Offset.Zero
        screenShakeDuration = 0f
        clearScreenFlashAndCues()
        _gameState.value = GameState.LEVEL_SELECT
    }

    /** Uygulamanin ilk ekrani. */
    fun returnToMainMenu() {
        deselectAll()
        _gameSpeed.value = 1.0f
        _screenShake.value = Offset.Zero
        screenShakeDuration = 0f
        clearScreenFlashAndCues()
        _gameState.value = GameState.MAIN_MENU
    }

    fun openLevelSelect() {
        _gameState.value = GameState.LEVEL_SELECT
    }

    /**
     * Faz 13 — R2 TAKVIYE: yenilgiden cikip savasi **kaldigi dalgadan** surdurur.
     *
     * Bu API eksikti ve `AdRewardBridge.reinforcementSupported` tam da bu yuzden
     * `false` idi: DEFEAT terminaldi, disari cikan tek yol `startNewGame()` yani
     * savasi BASTAN baslatmakti. "Reklam izle, savasa devam et" teklifi ancak
     * gercek bir devam varsa mesrudur.
     *
     * ## Ne yapar
     * 1. Us cani [lives] degerine getirilir ([maxLives] tavaniyla kirpilir).
     * 2. **Sahadaki dusmanlar ve mermiler temizlenir.** Bu sart: yenilgi ani,
     *    ussun dibinde birden fazla dusmanin oldugu andir; temizlemeden devam
     *    edilirse takviye ilk saniyede tukenir ve oyuncu reklami bosa izlemis
     *    olur — "calismayan odul"un ta kendisi.
     * 3. Kaldigi dalga BASTAN kurulur ve oyun PREPARATION'a doner: oyuncu
     *    savunmasini duzeltmek icin hazirlik suresi bulur.
     * 4. **Kuleler, Tedarik ve skor KORUNUR** — oyuncunun o ana kadarki
     *    yatirimi silinmez, silinseydi bu bir "devam" degil "yeniden basla"
     *    olurdu.
     *
     * ## Ne YAPMAZ
     * - [_battleEpoch] ARTMAZ: bu ayni savasin devamidir, yeni savas degil.
     *   Artsaydi savas-basi sayaclar (guclendirici haklari, rewarded savas
     *   hakki) sifirlanir ve sinirsiz takviye acilirdi.
     * - Yildiza dokunmaz. Geri verilen can ekonomi tarafinda yildiz hesabindan
     *   dusulur (`CampaignProgressImpl.noteReinforcement`), yani takviye
     *   hayatta kalma satin alir; yildiz ve coin ASLA.
     *
     * Cagiran taraf oyunu teklif ekrani kapanana kadar duraklatmalidir; bu
     * fonksiyon PREPARATION'a doner ve hazirlik sayaci akmaya baslar.
     *
     * @param lives us caninin getirilecegi deger.
     * @return gercekten uygulanan can; **0 = uygulanmadi** (durum DEFEAT degil
     *   veya gecersiz girdi) ve bu durumda hicbir sey degismemistir.
     */
    fun reinforceAfterDefeat(lives: Int): Int {
        if (_gameState.value != GameState.DEFEAT) return 0
        if (lives <= 0) return 0

        val restored = lives.coerceAtMost(maxLives)
        enemies.clear()
        projectiles.clear()
        visualEffects.clear()
        _lives.value = restored
        _selectedBuildSpot.value = null
        _selectedTower.value = null
        screenShakeDuration = 0f
        _screenShake.value = Offset.Zero
        hitStopRemainingSeconds = 0f
        clearScreenFlashAndCues()
        combo.resetAll()

        setupWave(_currentWaveIndex.value)
        _gameState.value = GameState.PREPARATION
        stateBeforePause = GameState.PREPARATION
        _preparationTimer.value = GameConfig.PREPARATION_TIME_SECONDS.toFloat()
        return restored
    }

    private fun setupWave(waveIndex: Int) {
        pendingWaveSpawns.clear()
        if (waveIndex < levelWaves.size) {
            val wave = levelWaves[waveIndex]
            pendingWaveSpawns.addAll(wave.spawns)
            timeUntilNextSpawn = 0.5f
        }
    }

    fun startNextWaveNow() {
        if (_gameState.value == GameState.PREPARATION) {
            _gameState.value = GameState.WAVE_RUNNING
            audioManager.playSound(AudioManager.SoundEffect.WAVE_START)
        }
    }

    fun toggleGameSpeed() {
        _gameSpeed.value = if (_gameSpeed.value == 1.0f) 2.0f else 1.0f
    }

    /**
     * Savas alani (tuval) dokunus kabul ediyor mu? Modal aciken hayir.
     * Modallar pointer girdisini tuketmiyor ve dokunus alttaki tuvale
     * gecip kule insa ediyordu.
     */
    fun acceptsBattlefieldInput(): Boolean = when (_gameState.value) {
        GameState.PREPARATION, GameState.WAVE_RUNNING -> true
        else -> false
    }

    /**
     * Faz 3: uygulama arka plana gitti / reklam acildi. Simulasyon durur ve
     * oyuncu doner donmez ortasinda kalmis bir dalgayi kaybetmez; devam etmek
     * kasitli bir dokunus ister. Zaten PAUSED / MAIN_MENU / bitmis durumda
     * hicbir sey yapmaz, yani cift cagirma guvenlidir.
     */
    fun pauseForLifecycle() {
        when (val s = _gameState.value) {
            GameState.WAVE_RUNNING, GameState.PREPARATION -> {
                stateBeforePause = s
                _gameState.value = GameState.PAUSED
            }
            else -> {}
        }
    }

    /**
     * Duraklat / devam et.
     *
     * Devam ederken ONCEKI durum geri yuklenir. Eskiden her zaman WAVE_RUNNING'e
     * donuyordu: hazirlik asamasinda duraklatip devam eden oyuncu kalan hazirlik
     * suresini kaybediyor ve dalga aninda basliyordu — yani duraklatma tusu
     * oyuncuyu cezalandiriyordu.
     */
    fun togglePause() {
        when (val s = _gameState.value) {
            GameState.WAVE_RUNNING, GameState.PREPARATION -> {
                stateBeforePause = s
                _gameState.value = GameState.PAUSED
            }
            GameState.PAUSED -> _gameState.value = stateBeforePause
            else -> {}
        }
    }

    fun selectBuildSpot(spot: BuildSpot?) {
        _selectedTower.value = null
        _selectedBuildSpot.value = spot
        // Onizleme YALNIZCA secili bir pad varken anlamli; eski secimden kalan
        // tip yeni pad'in etrafinda yanlis halka cizdirirdi.
        _previewTowerType.value = null
        audioManager.playSound(AudioManager.SoundEffect.UI_CLICK)
    }

    fun selectTower(tower: TowerEntity?) {
        _selectedBuildSpot.value = null
        _selectedTower.value = tower
        _previewTowerType.value = null
        audioManager.playSound(AudioManager.SoundEffect.UI_CLICK)
    }

    fun deselectAll() {
        _selectedBuildSpot.value = null
        _selectedTower.value = null
        _previewTowerType.value = null
    }

    /** Bu kule AKTIF bolumde acik mi (GameConfig.TOWER_SPECS.unlockedAtLevel)? */
    fun isTowerUnlocked(type: GameConfig.TowerType): Boolean =
        GameConfig.isTowerUnlocked(type, levelSpec.levelId)

    fun buildTower(type: GameConfig.TowerType): Boolean {
        if (!acceptsBattlefieldInput()) return false
        val spot = _selectedBuildSpot.value ?: return false
        val spec = GameConfig.TOWER_SPECS[type] ?: return false

        // KILIT: UI pasif kart cizse de motor son sozu soyler. Aksi halde bir
        // gun baska bir cagiran (tutorial, test, ileride surukle-birak) kilidi
        // sessizce atlar ve bolum 1'de fuze rampasi kurulabilirdi.
        if (!isTowerUnlocked(type)) return false

        if (_gold.value < spec.buildCost) return false

        _gold.value -= spec.buildCost
        val newTower = TowerEntity(
            type = type,
            buildSpotId = spot.id,
            posX = spot.normX,
            posY = spot.normY,
            totalInvestedGold = spec.buildCost,
            damageMultiplier = metaDamageMultiplier,
            rangeMultiplier = metaRangeMultiplier,
            salvageRate = metaSalvageRate,
            // Faz 13: kademe kilidi de meta carpanlari gibi INSA ANINDA verilir.
            // Boylece panel ve motor ayni cevabi verir; oyuncuya bu bolumde
            // odeyemeyecegi bir yukseltme butonu gosterilmez.
            tierCap = GameConfig.maxTowerTier(type, levelSpec.levelId)
        )
        towers.add(newTower)

        audioManager.playSound(AudioManager.SoundEffect.TOWER_BUILD)
        // Faz 3: insa geri bildirimi namlu alevi degil, TOZ bulutu.
        addEffect(
            VisualEffect(
                type = EffectType.SMOKE_PUFF,
                posX = spot.normX,
                posY = spot.normY,
                maxAgeSeconds = 0.45f,
                scale = 1.3f
            )
        )

        _selectedBuildSpot.value = null
        _selectedTower.value = newTower
        _previewTowerType.value = null
        return true
    }

    fun upgradeSelectedTower(): Boolean {
        if (!acceptsBattlefieldInput()) return false
        val tower = _selectedTower.value ?: return false
        // Faz 13: `upgradeCost` null ise BU KULE ICIN yukseltme YOKTUR — ya
        // merdivenin sonundadir ya da kademe bu bolumde henuz acilmamistir.
        // Motor son sozu soyler: UI bir sekilde butonu cizse bile buradan doner.
        val cost = tower.upgradeCost ?: return false

        if (_gold.value < cost) return false

        _gold.value -= cost
        // Sabit "2" yok: merdivende BIR basamak yukari. Kademe 4 bir gun
        // eklenirse bu satir degismez.
        tower.level += 1
        tower.totalInvestedGold += cost

        audioManager.playSound(AudioManager.SoundEffect.TOWER_UPGRADE)
        addEffect(
            VisualEffect(
                type = EffectType.FROST_WAVE,
                posX = tower.posX,
                posY = tower.posY,
                maxAgeSeconds = 0.4f,
                scale = 1.5f
            )
        )

        _selectedTower.value = tower // Trigger StateFlow update
        return true
    }

    fun sellSelectedTower(): Boolean {
        if (!acceptsBattlefieldInput()) return false
        val tower = _selectedTower.value ?: return false

        val refund = tower.sellValue
        _gold.value += refund
        towers.remove(tower)

        audioManager.playSound(AudioManager.SoundEffect.TOWER_SELL)
        addEffect(
            VisualEffect(
                type = EffectType.COIN_POPUP,
                posX = tower.posX,
                posY = tower.posY,
                maxAgeSeconds = 0.8f,
                text = "+$refund g"
            )
        )

        _selectedTower.value = null
        return true
    }

    /**
     * Faz 10 — GUCLENDIRICININ OYNANISA UYGULANDIGI TEK YER.
     *
     * Ekonomi katmani ([com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl.activateBooster])
     * izni verir, coini duser ve etkiyi SAYIYA cevirir; motor yalnizca o sayiyi
     * savas alanina isler. Karar mantigi burada TEKRARLANMAZ.
     *
     * Kaynak matematigi bilincli olarak saf katmandadir
     * ([applyBoosterToResources]) — motorun birim testi yok, o fonksiyonun var.
     *
     * @return etki gercekten uygulandiysa `true`. `false` donerse **hicbir sey
     *   degismemistir**; cagiran taraf ses/animasyon oynatmamalidir.
     */
    fun applyBoosterActivation(activation: BoosterActivation): Boolean {
        // Modal aciksa, oyun duraklamissa veya savas bittiyse guclendirici
        // islenmez. Ekonomi tarafi kullanimi ZATEN harcamis olur; bu yuzden
        // cagiran taraf `activateBooster`i bu kontrolden GECTIKTEN sonra
        // cagirmalidir.
        //
        // !!! REWARDED REKLAM TUZAGI (UI tarafinin cozmesi gereken dikis) !!!
        // Rewarded reklam acilinca ON_PAUSE -> `pauseForLifecycle()` -> PAUSED
        // gelir ve oyun BILINCLI olarak PAUSED kalir (GameScreen: "oyuncu devam
        // tusuna kendi basar"). Reklam odulu callback'inde dogrudan buraya
        // gelinirse durum hala PAUSED'dir, `false` doner ve oyuncu reklami
        // izleyip HICBIR SEY alamaz. Odul akisi ya once oyunu devam ettirmeli
        // ya da aktivasyonu oyun devam edene kadar bekletmelidir.
        if (!acceptsBattlefieldInput()) return false
        if (!activation.applied) return false

        val updated = applyBoosterToResources(
            res = BattleResources(supply = _gold.value, lives = _lives.value),
            act = activation,
            maxLives = maxLives,
        )
        _gold.value = updated.supply
        _lives.value = updated.lives

        val fraction = activation.airSupportDamageFraction
        if (fraction > 0.0) {
            // SNAPSHOT ZORUNLU: asagidaki `onEnemyKilled` olen dusmani `enemies`
            // listesinden SILER. Canli liste uzerinde donmek
            // ConcurrentModificationException verirdi ve hata ancak hava destegi
            // gercekten bir sey oldurdugunde — yani en kotu anda — patlardi.
            runAirStrike(enemies.toList(), fraction)
        }

        // Ses, sarsinti ve gorsel AYNI KAREDE tetiklenir; girdi ile geri bildirim
        // arasinda bir kare bile gecikme "gec kalan kontrol" olarak hissedilir.
        when {
            // Hava taarruzunun ses/sarsinti/flas zinciri `runAirStrike` icinde,
            // hasarla AYNI KAREDE baslar; burada tekrar tetiklenmez.
            fraction > 0.0 -> Unit

            activation.healthRestored > 0 ->
                audioManager.playSound(AudioManager.SoundEffect.TOWER_UPGRADE)

            activation.supplyGranted > 0 ->
                audioManager.playSound(AudioManager.SoundEffect.COIN_EARNED)

            else -> audioManager.playSound(AudioManager.SoundEffect.UI_CLICK)
        }

        return true
    }

    /**
     * HAVA TAARRUZU — hasarin GORUNUR hale getirildigi yer.
     *
     * ## Neden yeniden yazildi (cihazda kullanici bulgusu)
     * *"hava destek istedim bir sey gelmedi sanki."* Mekanizma calisiyordu:
     * ucret kesiliyor, bekleme basliyor, her dusman maks caninin %45'ini
     * kaybediyordu. Sorun OKUNABILIRLIKTI — hasar tanim geregi hicbir dusmani
     * oldurmedigi icin (bkz. asagidaki kisit) ekranda "bir sey oldu" diyen tek
     * isaret dusman basina 0,45 saniyelik kucuk bir patlama sprite'iydi.
     *
     * ## DEGISMEYEN KISIT
     * Hasar hesabi HALA [airSupportDamage] — zirhi ve patlama zafiyetini
     * bilerek yok sayar, orani [EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION].
     * "Savas basina 2 kullanim x 0,45 = 0,90 < 1,0, yani hava destegi tam canli
     * hicbir dusmani tek basina olduremez" garantisi bir pay-to-win korumasidir
     * ve ekonomi testleriyle kilitlidir. Bu fonksiyon HASARA DOKUNMAZ; yalnizca
     * ayni hasari gorunur, duyulur ve hissedilir kilar.
     *
     * ## Zincir
     * 1. Sahayi bastan basa kesen bir UCUS HATTI ([EffectType.AIR_STRIKE_RUN]) —
     *    tek efekt nesnesi, tum ekran.
     * 2. Hat boyunca SIRALI patlamalar: her hedefin patlamasi, ucak onun
     *    uzerinden gecerken cikar. Gecikme `VisualEffect.ageSeconds`'in negatif
     *    baslangici ile kurulur, yani SIMULASYON ZAMANINA baglidir — 2x oyun
     *    hizinda zincir de iki kat hizli akar, duraklamada bekler.
     * 3. Her hedefin ustunde HASAR SAYISI. "37" goren oyuncu "hicbir sey
     *    olmadi" demez; olcuyu gorur ve neyi satin aldigini anlar.
     * 4. Ekran flasi + artirilmis sarsinti + yuvarlanan bombardiman sesi.
     *
     * ## Hasar ANINDA uygulanir, gorsel yayilir
     * Zincir yalnizca GORSELDIR. Hasari da yaymak simulasyonu degistirirdi:
     * bomba havadayken usse ulasan bir dusman, oyuncunun bu karede odedigi
     * hasari yemeden can goturebilirdi. Gorsel pencere ([GameFeel.AIR_STRIKE_RUN_SECONDS])
     * tam da bu yuzden kisa tutuldu.
     *
     * @param targets savas alanindaki dusmanlarin ANLIK KOPYASI. Kopya sart:
     *   asagidaki `onEnemyKilled` olen dusmani `enemies` listesinden siler.
     */
    private fun runAirStrike(targets: List<EnemyEntity>, fraction: Double) {
        val live = targets.filter { !it.isDead }

        val left = fieldLeftPx
        val width = fieldWidthPx.coerceAtLeast(1f)

        // Ucus hatti hedef yayilimini GERCEKTEN ozler: giris yuksekligi en
        // soldaki, cikis yuksekligi en sagdaki hedefin yuksekligidir. Tek
        // hedefte hat yatay olur ve tam onun uzerinden gecer. Hedef yoksa
        // (ekonomi katmani bunu zaten reddeder, burasi savunma amacli) hat
        // sahanin ortasindan gecer.
        val midY = fieldTopPx + fieldHeightPx * 0.5f
        val entryY = live.minByOrNull { it.posX }?.posY ?: midY
        val exitY = live.maxByOrNull { it.posX }?.posY ?: midY

        val runSeconds = GameFeel.AIR_STRIKE_RUN_SECONDS
        val life = runSeconds + GameFeel.AIR_STRIKE_TAIL_SECONDS

        addEffect(
            VisualEffect(
                type = EffectType.AIR_STRIKE_RUN,
                posX = left,
                posY = entryY,
                maxAgeSeconds = life,
                radiusPx = hypot(width, exitY - entryY),
                angleRad = atan2(exitY - entryY, width),
                scale = runSeconds / life
            )
        )

        // Hasar sayisi alacak hedefler: rota uzerinde EN ILERIDEKILER, yani
        // oyuncunun o an en cok onemsedikleri (bkz. AIR_STRIKE_MAX_DAMAGE_TEXTS).
        val textTargets: Set<String> =
            if (live.size <= GameFeel.AIR_STRIKE_MAX_DAMAGE_TEXTS) {
                live.mapTo(HashSet(live.size.coerceAtLeast(1))) { it.id }
            } else {
                live.sortedByDescending { it.currentWayPointIndex }
                    .take(GameFeel.AIR_STRIKE_MAX_DAMAGE_TEXTS)
                    .mapTo(HashSet(GameFeel.AIR_STRIKE_MAX_DAMAGE_TEXTS)) { it.id }
            }

        live.forEach { enemy ->
            // Ucagin X'i zamanla dogrusal ilerledigi icin hedefin X orani
            // DOGRUDAN bombasinin dusme anidir: patlama, ucak tam ustundeyken.
            val alongRun = ((enemy.posX - left) / width).coerceIn(0f, 1f)
            val delay = alongRun * runSeconds

            val damage = airSupportDamage(enemy.maxHp, fraction)
            enemy.hp -= damage
            enemy.hitFlashTimerSeconds = HIT_FLASH_DURATION_SECONDS

            // GECIKME TELAFISI (yoksa "bomba yanina dustu" gorunur).
            // `VisualEffect` konumu SABIT, ama hedef gecikme boyunca yuruyor:
            // en hizli dusman (115 ref-px/sn) 0,42 sn'de kendi capinin iki
            // katindan fazla yol alir. Efekt hedefin O ANDAKI degil, patlama
            // ANINDAKI konumuna kurulur. Yon her karede guncellenen
            // `rotationAngleRad`; virajda hata kalir ama buyuklugu ayni
            // (bir dusman capi mertebesinde) sinirla cevrilidir.
            val heading = enemy.rotationAngleRad
            val leadX = cos(heading) * enemy.currentSpeed
            val leadY = sin(heading) * enemy.currentSpeed

            addEffect(
                VisualEffect(
                    type = EffectType.CANNON_EXPLOSION,
                    posX = enemy.posX + leadX * delay,
                    posY = enemy.posY + leadY * delay,
                    ageSeconds = -delay,
                    maxAgeSeconds = GameFeel.AIR_STRIKE_BLAST_SECONDS,
                    radiusPx = enemy.radius * GameFeel.AIR_STRIKE_BLAST_RADIUS_FACTOR
                )
            )

            if (enemy.id in textTargets) {
                val textDelay = delay + GameFeel.AIR_STRIKE_DAMAGE_TEXT_LAG_SECONDS
                addEffect(
                    VisualEffect(
                        type = EffectType.DAMAGE_TEXT,
                        posX = enemy.posX + leadX * textDelay,
                        posY = enemy.posY + leadY * textDelay,
                        ageSeconds = -textDelay,
                        maxAgeSeconds = GameFeel.AIR_STRIKE_DAMAGE_TEXT_SECONDS,
                        text = "-" + damage.roundToInt()
                    )
                )
            }

            // Var olan olum yolu: odul, skor, kayit sayaci, ses ve efekt
            // guclendiriciyle olen dusmanda da AYNEN calisir.
            if (enemy.isDead) onEnemyKilled(enemy)
        }

        // ---------------------------------------------------------------------
        // SES / SARSINTI / FLAS — girdi ile AYNI KAREDE baslar.
        //
        // Ses tek atis DEGIL: goz 0,42 saniyeye yayilan sirali patlamalar
        // gorurken kulagin tek "bum" duymasi olayi kucultuyordu. Yuvarlanan
        // bombardiman MEVCUT uc ses dosyasindan kuruluyor (yeni asset YOK):
        // fuze kalkisi -> agir patlama -> orta patlama -> agir patlama.
        // Iki agir patlama arasi ~310 ms, yani EXPLOSION_HEAVY'nin 90 ms'lik
        // minimum araligini rahatca asiyor; ses kirpilmaz.
        // ---------------------------------------------------------------------
        audioManager.playSound(AudioManager.SoundEffect.MISSILE_LAUNCH)
        scheduleSound(AudioManager.SoundEffect.EXPLOSION_HEAVY, runSeconds * 0.18f)
        scheduleSound(AudioManager.SoundEffect.EXPLOSION, runSeconds * 0.55f)
        scheduleSound(AudioManager.SoundEffect.EXPLOSION_HEAVY, runSeconds * 0.92f)

        triggerScreenShake(GameFeel.AIR_STRIKE_SHAKE_SECONDS)
        triggerScreenFlash(GameFeel.AIR_STRIKE_FLASH_SECONDS)
    }

    fun cycleTargetingMode() {
        if (!acceptsBattlefieldInput()) return
        val tower = _selectedTower.value ?: return
        val modes = GameConfig.TargetingMode.values()
        val nextMode = modes[(tower.targetingMode.ordinal + 1) % modes.size]
        tower.targetingMode = nextMode
        _selectedTower.value = tower
        audioManager.playSound(AudioManager.SoundEffect.UI_CLICK)
    }

    /**
     * Kozmetik yaslanma: sarsinti sonumlemesi + gorsel efektler.
     *
     * SIMULASYONDAN AYRI TUTULMASININ SEBEBI GERCEK BIR BUG (cihazda kullanici
     * tarafindan bulundu): *"son vurusta +28g yazan yer takili kaliyor."*
     *
     * Kok sebep: `tick` en basta bir DURUM BEYAZ LISTESI ile erken donuyordu
     * (yalnizca PREPARATION / WAVE_RUNNING). Bolumun son dusmani olunce ayni
     * karede once `onEnemyKilled` COIN_POPUP efektini uretiyor, hemen ardindan
     * dalga tamamlama kontrolu state'i VICTORY yapiyor. Sonraki karede tick
     * beyaz listede takilip donuyor, yani efekt BIR DAHA HIC YASLANMIYOR ve
     * "+4g" yazisi zafer modalinin arkasinda KALICI olarak duruyor. Ayni sey
     * DEFEAT'te de oluyordu.
     *
     * Bu yuzden kozmetik yaslanma VICTORY / DEFEAT'te de kosar — efektler
     * oynanisi etkilemez, sonmeleri gerekir. PAUSED bilincli olarak HARIC:
     * duraklatilmis oyunda her seyin donmasi oyuncunun bekledigi davranistir
     * (devam edince efekt kaldigi yerden soner).
     */
    private fun ageCosmetics(dt: Float) {
        updateScreenShake(dt)
        updateScreenFlash(dt)
        ageEffects(dt)
        agePendingSounds(dt)
        combo.age(dt)
    }

    private fun updateScreenFlash(dt: Float) {
        if (screenFlashRemaining <= 0f) return
        screenFlashRemaining -= dt
        if (screenFlashRemaining <= 0f) {
            screenFlashRemaining = 0f
            screenFlashDuration = 0f
        }
    }

    /**
     * Gecikmeli sesleri calar. Geriye dogru dolasilir: cagri sirasinda listeden
     * eleman SILINIYOR ve ileri giden bir dongu bir sonraki elemani atlardi.
     */
    private fun agePendingSounds(dt: Float) {
        if (pendingSounds.isEmpty()) return
        // Ciplak `while`: `indices.reversed()` KARE YOLUNDA iki `IntRange`
        // tahsis ederdi (bkz. EntityIds notu — bu dosyada kural budur).
        var i = pendingSounds.size - 1
        while (i >= 0) {
            val cue = pendingSounds[i]
            cue.delaySeconds -= dt
            if (cue.delaySeconds <= 0f) {
                pendingSounds.removeAt(i)
                audioManager.playSound(cue.sound)
            }
            i--
        }
    }

    /**
     * Faz 14 - sarsinti AYRI bir fonksiyona alindi cunku HIT STOP sirasinda
     * TEK calisan sey odur.
     *
     * Donma + sarsinti birlikte "crunch" hissi verir; donma sirasinda sarsinti
     * da dursaydi oyuncu 60 ms boyunca tamamen olu bir ekran gorurdu ve bunu
     * "oyun takildi" diye okurdu. Sarsinti her zaman SONUMLENEREK sifira iner.
     */
    private fun updateScreenShake(dt: Float) {
        if (screenShakeDuration > 0f) {
            screenShakeDuration -= dt
            val intensity = (screenShakeDuration * 15f).coerceAtMost(10f)
            _screenShake.value = Offset(
                Random.nextFloat() * intensity - intensity / 2f,
                Random.nextFloat() * intensity - intensity / 2f
            )
        } else {
            _screenShake.value = Offset.Zero
        }
    }

    private fun ageEffects(dt: Float) {
        val effectIterator = visualEffects.iterator()
        while (effectIterator.hasNext()) {
            val fx = effectIterator.next()
            fx.ageSeconds += dt
            if (fx.ageSeconds >= fx.maxAgeSeconds) {
                effectIterator.remove()
            }
        }
    }

    // Core Frame Update Tick
    /**
     * Kare olceri. `tick` HER karede cagrilir (menude bile), yani gecen sure
     * gercek vsync araligidir ve dusen kareler buraya yansir.
     *
     * Maliyeti kare basina iki `System.nanoTime()` cagrisi (~50 ns) ve 300
     * karede bir tek log satiri. Oynanisa etkisi yok, olcum degeri buyuk:
     * cihazda `adb logcat -s FDPerf` ile p50/p95/p99 ve jank yuzdesi cikar.
     */
    private val framePerf = FramePerfMonitor()

    fun tick(deltaSeconds: Float) {
        val simStart = System.nanoTime()
        try {
            tickSimulation(deltaSeconds)
        } finally {
            framePerf.record(deltaSeconds, System.nanoTime() - simStart)
        }
    }

    private fun tickSimulation(deltaSeconds: Float) {
        val dt = deltaSeconds * _gameSpeed.value

        // Beyaz liste: yeni bir GameState eklendiginde simulasyon KAZAYLA
        // kosmaya devam etmesin (LEVEL_SELECT eklenirken kara liste kirilgandi).
        when (_gameState.value) {
            GameState.PREPARATION, GameState.WAVE_RUNNING -> {}
            // Bolum bitti: SIMULASYON durur ama kozmetik efektler soner
            // (bkz. ageCosmetics — takili kalan "+4g" bugunun kok sebebi).
            GameState.VICTORY, GameState.DEFEAT -> {
                // Bolum bitti: bekleyen bir donma zafer/yenilgi modalinin
                // arkasinda ASILI KALMAMALI.
                hitStopRemainingSeconds = 0f
                combo.reset()
                ageCosmetics(dt)
                return
            }
            else -> return
        }

        // --------------------------------------------------------------------
        // HIT STOP - agir vurusta simulasyon 30-80 ms DURUR.
        //
        // Neden burada, `tick`in en basinda: donmanin ANLAMI simulasyonun
        // ilerlememesi. Efekt yaslanmasi da donar (patlama karesi ekranda
        // ASILI kalir, vurusu "agir" yapan sey budur); yalnizca sarsinti
        // calismaya devam eder.
        //
        // Zaman tabani `dt`, yani OYUN HIZI CARPANI DAHIL: 2x hizda donma da
        // yariya iner. Gercek zaman kullanilsaydi 2x hizda hit stop oyunun
        // geri kalanina gore iki kat uzun hissedilirdi.
        // --------------------------------------------------------------------
        if (hitStopRemainingSeconds > 0f) {
            hitStopRemainingSeconds -= dt
            updateScreenShake(dt)
            return
        }

        ageCosmetics(dt)

        // Preparation phase
        if (_gameState.value == GameState.PREPARATION) {
            _preparationTimer.value -= dt
            if (_preparationTimer.value <= 0f) {
                startNextWaveNow()
            }
            return
        }

        // 1. Wave Spawning logic
        if (pendingWaveSpawns.isNotEmpty()) {
            timeUntilNextSpawn -= dt
            if (timeUntilNextSpawn <= 0f) {
                val spawn = pendingWaveSpawns.removeAt(0)
                spawnEnemy(spawn.enemyType)
                timeUntilNextSpawn = spawn.delaySeconds
            }
        }

        // 2. Enemy Movement & Status Decay
        val enemyIterator = enemies.iterator()
        while (enemyIterator.hasNext()) {
            val enemy = enemyIterator.next()

            // Flash effect decay
            if (enemy.hitFlashTimerSeconds > 0f) {
                enemy.hitFlashTimerSeconds -= dt
            }

            // Slow decay
            enemy.activeSlow?.let { slow ->
                slow.durationRemainingSeconds -= dt
                if (slow.durationRemainingSeconds <= 0f) {
                    enemy.activeSlow = null
                }
            }

            // Move along this enemy's OWN route (catallanan haritalarda 2 rota).
            val route = routeFor(enemy)
            if (route.size > 1 && enemy.currentWayPointIndex < route.size - 1) {
                val targetPt = route[enemy.currentWayPointIndex + 1]
                val dx = targetPt.x - enemy.posX
                val dy = targetPt.y - enemy.posY
                val distToTarget = sqrt(dx * dx + dy * dy)

                enemy.rotationAngleRad = atan2(dy, dx)
                val moveDist = enemy.currentSpeed * dt

                if (distToTarget <= moveDist) {
                    // Reached waypoint
                    enemy.posX = targetPt.x
                    enemy.posY = targetPt.y
                    enemy.distanceTraveledPx += distToTarget
                    enemy.currentWayPointIndex++

                    // Check if reached final base
                    if (enemy.currentWayPointIndex >= route.size - 1) {
                        _lives.value -= GameConfig.BASE_REACHED_PENALTY_LIVES
                        // Faz 3: us hasari = agir patlama + sarsinti. Eskiden
                        // "dusman isabeti" sesi caliyordu, oyuncu can kaybini
                        // kendi vurusuyla karistiriyordu.
                        audioManager.playSound(AudioManager.SoundEffect.EXPLOSION_HEAVY)
                        triggerScreenShake(0.30f)
                        // Faz 14: can kaybi oyuncunun HISSETMESI gereken tek
                        // olumsuz olay. Donma burada "kotu bir sey oldu"
                        // vurgusu; ayrica zincir kopar (savunma delindi).
                        triggerHitStop(GameFeel.HIT_STOP_BASE_LEAK)
                        // Ekrana bakmiyor olabilir: can kaybi dokunsal olarak da
                        // bildirilir. Cift darbe deseni bilincli — olumlu
                        // olaylarin tek darbesinden AYIRT EDILEBILIR olmali.
                        haptics?.onBaseHit()
                        combo.reset()
                        addEffect(
                            VisualEffect(
                                type = EffectType.DAMAGE_TEXT,
                                posX = enemy.posX,
                                posY = enemy.posY - 10f,
                                maxAgeSeconds = 1.0f,
                                text = "-1 Life"
                            )
                        )
                        enemyIterator.remove()

                        if (_lives.value <= 0) {
                            _gameState.value = GameState.DEFEAT
                            audioManager.playSound(AudioManager.SoundEffect.DEFEAT)
                            return
                        }
                    }
                } else {
                    val ratio = moveDist / distToTarget
                    enemy.posX += dx * ratio
                    enemy.posY += dy * ratio
                    enemy.distanceTraveledPx += moveDist
                }
            }
        }

        // 3. Tower Targeting & Firing
        towers.forEach { tower ->
            if (tower.cooldownTimerSeconds > 0f) {
                tower.cooldownTimerSeconds -= dt
            }

            if (tower.recoilOffsetPx > 0f) {
                tower.recoilOffsetPx = (tower.recoilOffsetPx - dt * 25f).coerceAtLeast(0f)
            }

            // Target enemy
            val target = findTargetEnemyForTower(tower)
            if (target != null) {
                val dx = target.posX - tower.posX
                val dy = target.posY - tower.posY
                tower.targetAngleRad = atan2(dy, dx)

                // Rotate turret smoothly toward target
                var diff = tower.targetAngleRad - tower.currentAngleRad
                while (diff < -PI) diff += (PI * 2).toFloat()
                while (diff > PI) diff -= (PI * 2).toFloat()
                tower.currentAngleRad += diff * (dt * 12f).coerceAtMost(1f)

                // Fire if off cooldown
                if (tower.cooldownTimerSeconds <= 0f) {
                    tower.cooldownTimerSeconds = tower.fireRate
                    tower.recoilOffsetPx = 6f
                    fireTower(tower, target)
                }
            }
        }

        // 4. Projectile Updating & Collision Logic
        val projIterator = projectiles.iterator()
        while (projIterator.hasNext()) {
            val proj = projIterator.next()
            proj.progress += dt * proj.speed / 100f

            val dx = proj.targetX - proj.startX
            val dy = proj.targetY - proj.startY
            proj.posX = proj.startX + dx * proj.progress.coerceAtMost(1f)
            proj.posY = proj.startY + dy * proj.progress.coerceAtMost(1f)

            if (proj.progress >= 1f) {
                // Impact!
                onProjectileImpact(proj)
                projIterator.remove()
            }
        }

        // 5. Gorsel efektler YUKARIDA, PREPARATION erken-return'unun USTUNDE
        //    guncelleniyor (bkz. oradaki bug notu).

        // 6. Wave Completion Check
        if (pendingWaveSpawns.isEmpty() && enemies.isEmpty() && _gameState.value == GameState.WAVE_RUNNING) {
            val completedIdx = _currentWaveIndex.value
            if (completedIdx >= levelWaves.lastIndex) {
                // Victory!
                _gameState.value = GameState.VICTORY

                // Yildiz = kalan us cani YUZDESI (GDD B.3), mutlak can DEGIL.
                // Eskiden esikler 18/10 olarak sabit kodluydu; us cani bolume ve
                // meta yukseltmelere gore degistigi icin (20 -> meta ile 30) bu
                // yanlis yildiz veriyordu: 30 canli bir bolumu 18 canla bitirmek
                // %60 iken 3 yildiz sayiliyordu.
                //
                // Faz 13 — PAYDA/PAY UYUSMAZLIGI KAPATILDI. Pay `_lives.value`
                // (meta DAHIL, Tahkimat rank 5'te 30'a kadar), payda ise taban
                // `maxBaseLives` (20) idi: Tahkimat'li oyuncu 12 sizinti verip
                // 3 yildiz, 10 sizinti verip "Kusursuz Savunma" +80 coin
                // aliyordu. Cozum [victoryStarHealth]: ikisi de TABAN can
                // uzerinden, yani yildiz artik yalnizca SIZINTI SAYISINA bakar.
                val starDenominator = levelSpec.maxBaseLives.coerceAtLeast(1)
                // Faz 10: yildiz formulu ARTIK KOPYALANMIYOR. Motor ekonomi
                // katmanindaki tek gercek fonksiyonu (`starsFor`) cagirir; iki
                // yerde ayni esikleri tutmak zaten AEHP hatasinin sebebiydi.
                //
                // `starHealthAdjuster`: Us Tamiri guclendiricisi ve R2 Takviye
                // ile geri alinan can yildiza SAYILMAMALI (ekonomi: ikisi de
                // hayatta kalma satin alir, yildiz ve coin ASLA). Duzeltmeyi
                // ekonomi katmani biliyor (CampaignProgressImpl.starHealthFor),
                // motor bilmez — bu yuzden burada bir DIKIS var; varsayilani
                // kimlik oldugu icin baglanmasa da davranis bozulmaz.
                val starHealth = starHealthAdjuster(victoryStarHealth).coerceAtLeast(1)
                val stars = starsFor(starHealth, starDenominator)
                // Zafer ekranindaki "N sizinti daha az = 3 yildiz" ipucu bu
                // sayiyi kullanir. Ipucunun yildiz sayisiyla ayni girdiden
                // uretilmesi sart: modal kendi hesabini yapsaydi guclendirici
                // duzeltmesini (Us Tamiri / R2 Takviye) goremez ve oyuncuya
                // ulasilamayan bir hedef gosterirdi.
                lastStarHealth = starHealth
                _lastEarnedStars.value = stars
                // Bolum ID'si de sabit `1` yaziliydi: hangi bolum bitirilirse
                // bitirilsin yildiz bolum 1'e kaydediliyordu.
                saveManager.setLevelStars(levelSpec.levelId, stars)
                saveManager.highScore = _score.value
                audioManager.playSound(AudioManager.SoundEffect.VICTORY)
            } else {
                // Next wave preparation
                _currentWaveIndex.value = completedIdx + 1
                // Faz 10: ciplak 35 kaldirildi (ekonomi tek-kaynak kurali).
                _gold.value += GameConfig.WAVE_CLEAR_SUPPLY_BONUS
                setupWave(_currentWaveIndex.value)
                _gameState.value = GameState.PREPARATION
                _preparationTimer.value = GameConfig.PREPARATION_TIME_SECONDS.toFloat()
                // Faz 14 SES AYRIMI TAMAMLANDI. Once COIN_EARNED caliyordu
                // ("bir dusman oldu" ile "dalga temizlendi" kulakta AYNI
                // olaydi), sonra gecici olarak TOWER_BUILD kullanildi. Artik
                // dalga sonuna OZEL, yukselen uc notali `sfx_wave_cleared`
                // calar: tek ve daha uzun bir olay, coin tinisiyla
                // karistirilamaz.
                audioManager.playSound(AudioManager.SoundEffect.WAVE_CLEARED)
                combo.resetAll()
            }
        }
    }

    /**
     * Bu dusmanin izledigi px rotasi. Rota indeksi gecersizse birincil rotaya
     * duser (bolum ortasinda harita degismez, ama savunmaci programlama).
     */
    private fun routeFor(enemy: EnemyEntity): List<PointF> =
        scaledRoutes.getOrNull(enemy.routeIndex) ?: scaledWaypoints

    private fun spawnEnemy(type: GameConfig.EnemyType) {
        if (scaledRoutes.isEmpty()) return
        val spec = GameConfig.ENEMY_SPECS[type] ?: return

        // Catallanan haritalarda rota atamasi: SEED'LI ve deterministik.
        // Seed bolume bagli (loadLevel), RNG render'dan tamamen ayri; ayni bolum
        // her oynanista ayni rota dizisini uretir.
        val routeIndex = if (scaledRoutes.size > 1) routeRng.nextInt(scaledRoutes.size) else 0
        val spawnPt = scaledRoutes[routeIndex].first()

        enemies.add(
            EnemyEntity(
                // Kimlik UUID DEGIL (bkz. EntityIds): kare yolunda cagri
                // basina 384 -> 48 bayt, 600 -> 100 ns.
                id = EntityIds.next(),
                type = type,
                posX = spawnPt.x,
                posY = spawnPt.y,
                routeIndex = routeIndex,
                // ACT OLCEKLENDIRMESI: ikinci turda ayni harita tekrar
                // gorunuyor, dusman da guclenmeli yoksa tekrar gibi hissedilir.
                // HIZ olceklenmez (okunabilirlik + "sadece HP/hiz artirma"
                // yasagi); odul HP ile birlikte artar ki ekonomi tempo tutsun.
                hp = spec.maxHp * actHpMul,
                maxHp = spec.maxHp * actHpMul,
                // HIZ da REFERANS tuvalde tanimli bir denge degeridir. Ham px
                // olarak kullanildiginda yol tablette 2560 px, telefonda 1800 px
                // uzunlugundaydi ama dusman ayni px/sn ile yuruyordu: tablette
                // her dusman %42 daha uzun sure sahada kaliyor, yani ayni bolum
                // farkli bir oyun oluyordu.
                baseSpeed = spec.baseSpeed * renderScale,
                armor = spec.armor,
                rewardGold = (spec.rewardGold * actRewardMul).toInt().coerceAtLeast(1),
                radius = spec.sizeRadius
            )
        )
    }

    /**
     * Hedef secimi - TAHSISSIZ tek gecis.
     *
     * FAZ 14 KARE BUTCESI DUZELTMESI: burasi eskiden `enemies.filter { }` ile
     * her KULE icin her KAREDE yeni bir liste uretiyordu. 11 kuleli bir Act II
     * bolumunde bu saniyede 660 kisa omurlu liste demek, yani saf GC baskisi
     * ve gorunur jank. Artik aday listesi hic olusturulmuyor.
     *
     * DAVRANIS BIREBIR KORUNDU: `maxByOrNull`/`minByOrNull` esitlikte ILK
     * elemani dondurur, bu yuzden karsilastirmalar KESIN (`>` / `<`) ve
     * dusmanlar liste sirasinda taraniyor. Gevsek karsilastirma (`>=`) esit
     * skorlu iki dusmanda hedefi sessizce degistirir, yani kule titrer.
     */
    private fun findTargetEnemyForTower(tower: TowerEntity): EnemyEntity? {
        // Menzil REFERANS tuvalde tanimli; mesafe kiyasi canvas px'te yapilir.
        val range = tower.rangePx(renderScale)
        val rangeSq = range * range

        var best: EnemyEntity? = null
        var bestScore = 0f
        val wantsMax = when (tower.targetingMode) {
            GameConfig.TargetingMode.FIRST, GameConfig.TargetingMode.STRONGEST -> true
            GameConfig.TargetingMode.LAST, GameConfig.TargetingMode.WEAKEST -> false
        }

        for (i in enemies.indices) {
            val enemy = enemies[i]
            val dx = enemy.posX - tower.posX
            val dy = enemy.posY - tower.posY
            if (dx * dx + dy * dy > rangeSq) continue

            val score = when (tower.targetingMode) {
                GameConfig.TargetingMode.FIRST,
                GameConfig.TargetingMode.LAST -> enemy.distanceTraveledPx
                GameConfig.TargetingMode.STRONGEST,
                GameConfig.TargetingMode.WEAKEST -> enemy.hp
            }

            if (best == null || (if (wantsMax) score > bestScore else score < bestScore)) {
                best = enemy
                bestScore = score
            }
        }
        return best
    }

    private fun fireTower(tower: TowerEntity, target: EnemyEntity) {
        val pType = when (tower.type) {
            GameConfig.TowerType.MACHINE_GUN -> ProjectileType.BULLET
            GameConfig.TowerType.CANNON -> ProjectileType.CANNON_SHELL
            GameConfig.TowerType.ANTI_ARMOR -> ProjectileType.MISSILE
            GameConfig.TowerType.SLOW -> ProjectileType.FROST_PULSE
        }

        val sound = when (tower.type) {
            GameConfig.TowerType.MACHINE_GUN -> AudioManager.SoundEffect.MACHINE_GUN
            GameConfig.TowerType.CANNON -> AudioManager.SoundEffect.CANNON_BOOM
            GameConfig.TowerType.ANTI_ARMOR -> AudioManager.SoundEffect.MISSILE_LAUNCH
            GameConfig.TowerType.SLOW -> AudioManager.SoundEffect.FROST_PULSE
        }
        // Ses, sarsinti ve gorsel efekt AYNI KAREDE tetiklenir.
        audioManager.playSound(sound)

        // Namlu alevi: YONLU sprite. Namlu ucu ofseti referans tuvalde tanimli.
        val muzzleOffset = GameConfig.MUZZLE_OFFSET_REF_PX * renderScale
        addEffect(
            VisualEffect(
                type = EffectType.MUZZLE_FLASH,
                posX = tower.posX + cos(tower.currentAngleRad) * muzzleOffset,
                posY = tower.posY + sin(tower.currentAngleRad) * muzzleOffset,
                maxAgeSeconds = 0.13f,
                scale = if (tower.type == GameConfig.TowerType.CANNON) 1.5f else 1.0f,
                angleRad = tower.currentAngleRad
            )
        )

        // Mermi hizlari GameConfig'te; renderer'a ya da motora gomulmezler.
        // DIKKAT: motorun ilerletme formulu `progress += dt * speed / 100f`
        // oldugu icin bu deger px/sn DEGIL, ucus suresi = 100/speed sn.
        val projSpeed = GameConfig.PROJECTILE_SPEEDS[pType] ?: 300f

        // Referans tuvalde tanimli yaricaplar burada BIR KEZ canvas px'e cevrilir.
        val s = renderScale
        projectiles.add(
            ProjectileEntity(
                // Kimlik UUID DEGIL (bkz. EntityIds). Mermi kare yolundaki EN
                // sik uretilen nesnedir: son kademe Gatling 0,20 sn'de bir
                // ates eder, 11 pad'li haritada saniyede ~35 mermi.
                id = EntityIds.next(),
                type = pType,
                posX = tower.posX,
                posY = tower.posY,
                startX = tower.posX,
                startY = tower.posY,
                targetEnemyId = target.id,
                targetX = target.posX,
                targetY = target.posY,
                damage = tower.damage,
                speed = projSpeed,
                splashRadius = tower.stats.splashRadius * s,
                armorPierce = tower.stats.armorPierce,
                slowFactor = tower.stats.slowFactor,
                slowDuration = tower.stats.slowDuration,
                towerType = tower.type,
                impactRadius = tower.stats.missileImpactRadius * s,
                impactDamageFraction = tower.stats.missileImpactDamageFraction,
                slowPulseRadius = tower.stats.slowPulseRadius * s
            )
        )
    }

    /**
     * Carpma. Dort ayri kimlik, dort ayri dal — hepsi ayni karede ses + gorsel
     * + (varsa) sarsinti tetikler.
     */
    private fun onProjectileImpact(proj: ProjectileEntity) {
        when {
            proj.splashRadius > 0f -> impactCannonShell(proj)
            proj.slowPulseRadius > 0f -> impactFrostPulse(proj)
            proj.type == ProjectileType.MISSILE -> impactMissile(proj)
            else -> impactSingleShot(proj)
        }
    }

    /** Yakinda bulunan dusmanlar (canvas px yaricap). */
    private fun enemiesWithin(x: Float, y: Float, radiusPx: Float): List<EnemyEntity> {
        val rSq = radiusPx * radiusPx
        return enemies.filter {
            val dx = it.posX - x
            val dy = it.posY - y
            (dx * dx + dy * dy) <= rSq
        }
    }

    /** CANNON — genis patlama, zirhi bypass eder (DECISIONS B2). */
    private fun impactCannonShell(proj: ProjectileEntity) {
        triggerScreenShake(0.25f)
        audioManager.playSound(AudioManager.SoundEffect.EXPLOSION)

        addEffect(
            VisualEffect(
                type = EffectType.CANNON_EXPLOSION,
                posX = proj.targetX,
                posY = proj.targetY,
                maxAgeSeconds = 0.4f,
                // Patlama artik GERCEK etki alanina gore cizilir: oyuncu top
                // mermisinin nereyi kapsadigini gorselden ogrenebilir.
                radiusPx = proj.splashRadius
            )
        )

        enemiesWithin(proj.targetX, proj.targetY, proj.splashRadius).forEach { enemy ->
            applyDamageToEnemy(
                enemy, proj.damage, proj.armorPierce,
                proj.slowFactor, proj.slowDuration, isSplash = true
            )
        }
    }

    /**
     * SLOW — ALAN kontrolu.
     *
     * Faz 10 duzeltmesi: cryo darbesi eskiden `applyDamageToEnemy` ile YALNIZCA
     * hedeflenen tek dusmani yavaslatiyordu. 20 kisilik bir suruye karsi
     * 0.65 sn'de bir tek dusman = pratikte hicbir sey; testcinin "kullanmanin
     * anlami olmuyor" demesinin gercek sebebi menzil kadar bu da.
     */
    private fun impactFrostPulse(proj: ProjectileEntity) {
        // Ses BURADA CALINMAZ: cryo sesi atis aninda (fireTower) caliyor. Iki
        // yerde calmak 0.38 sn arayla ayni ornegi ust uste bindirir ve alan
        // darbesi "cift tetiklenmis" gibi duyulur.
        addEffect(
            VisualEffect(
                type = EffectType.FROST_PULSE_RING,
                posX = proj.targetX,
                posY = proj.targetY,
                maxAgeSeconds = 0.35f,
                radiusPx = proj.slowPulseRadius
            )
        )
        enemiesWithin(proj.targetX, proj.targetY, proj.slowPulseRadius).forEach { enemy ->
            applyDamageToEnemy(
                enemy, proj.damage, proj.armorPierce,
                proj.slowFactor, proj.slowDuration
            )
        }
    }

    /**
     * ANTI_ARMOR — FUZE.
     *
     * Isin aninda varirdi; fuze yol alir. Hedef fuze havadayken oldu ise fuze
     * BOSA GIDER: hicbir yeniden yonlendirme yapilmaz, yalnizca carpma
     * noktasindaki kucuk alan hasari (delici muhimmat olarak, zirhi BYPASS
     * ETMEZ) yakindakileri yakalayabilir. Bu, ANTI_TANK rolunun bedeli.
     */
    private fun impactMissile(proj: ProjectileEntity) {
        triggerScreenShake(0.12f)
        audioManager.playSound(AudioManager.SoundEffect.EXPLOSION)
        addEffect(
            VisualEffect(
                type = EffectType.MISSILE_IMPACT,
                posX = proj.targetX,
                posY = proj.targetY,
                maxAgeSeconds = 0.35f,
                scale = 0.85f
            )
        )

        val primary = enemies.find { it.id == proj.targetEnemyId }
        primary?.let {
            applyDamageToEnemy(it, proj.damage, proj.armorPierce, proj.slowFactor, proj.slowDuration)
        }

        if (proj.impactRadius > 0f && proj.impactDamageFraction > 0f) {
            val splashDamage = proj.damage * proj.impactDamageFraction
            enemiesWithin(proj.targetX, proj.targetY, proj.impactRadius).forEach { enemy ->
                if (enemy.id == primary?.id) return@forEach
                applyDamageToEnemy(enemy, splashDamage, proj.armorPierce, 0f, 0f)
            }
        }
    }

    /** MACHINE_GUN — tek hedef, isabet kivilcimi. */
    private fun impactSingleShot(proj: ProjectileEntity) {
        // Hedef olduyse: SINIRLI tolerans icinde en yakin dusmana yonlenir.
        // Onceden sinir yoktu ve olen hedefe giden kursun haritanin obur
        // ucundaki dusmana hasar tasiyordu.
        val tolerance = GameConfig.PROJECTILE_REDIRECT_TOLERANCE_REF_PX * renderScale
        val target = enemies.find { it.id == proj.targetEnemyId }
            ?: enemiesWithin(proj.targetX, proj.targetY, tolerance).minByOrNull {
                val dx = it.posX - proj.targetX
                val dy = it.posY - proj.targetY
                dx * dx + dy * dy
            }

        // ISABET YOKSA GERI BILDIRIM DE YOK. Eskiden hedef bulunamasa bile
        // isabet sesi ve kivilcim ciziliyordu: oyuncu vurdugunu sanip can
        // barinin kimildamadigini goruyordu — yanlis geri bildirim.
        val enemy = target ?: return
        applyDamageToEnemy(enemy, proj.damage, proj.armorPierce, proj.slowFactor, proj.slowDuration)

        // Faz 3: tek hedef isabetinde artik NAMLU ALEVI degil, isabet
        // kivilcimi cizilir. Namlu alevi yalnizca ates aninda kullanilir.
        audioManager.playSound(AudioManager.SoundEffect.ENEMY_HIT)
        addEffect(
            VisualEffect(
                type = EffectType.HIT_SPARK,
                posX = proj.targetX,
                posY = proj.targetY,
                maxAgeSeconds = 0.25f,
                angleRad = atan2(proj.targetY - proj.startY, proj.targetX - proj.startX)
            )
        )
    }

    /**
     * @param isSplash Bu hasar bir PATLAMA bilesenimi (DECISIONS B2)?
     *   Splash bileseni zirhi **BYPASS EDER** ve hedefin
     *   `splashVulnerability` carpaniyla olceklenir; dogrudan isabete bu carpan
     *   uygulanmaz. Boylece "kursuna direncli / patlamaya zayif" dusman
     *   (SHIELDED_TROOPER) tasarimi mumkun olur ve armorPierce'i 0 olan Cannon
     *   gec oyunda olu kalmaz.
     */
    private fun applyDamageToEnemy(
        enemy: EnemyEntity,
        rawDamage: Float,
        armorPierce: Float,
        slowFactor: Float,
        slowDuration: Float,
        isSplash: Boolean = false
    ) {
        val finalDamage = if (isSplash) {
            rawDamage * enemy.stats.splashVulnerability
        } else {
            val effectiveArmor = (enemy.armor * (1f - armorPierce)).coerceAtLeast(0f)
            rawDamage * (1f - effectiveArmor)
        }

        enemy.hp -= finalDamage
        enemy.hitFlashTimerSeconds = 0.12f

        // Yavaslatmayi tazele. Mevcut etki DAHA GUCLU ise korunur: alan darbesi
        // artik ayni dusmani ust uste vurdugu icin, kor bir atama ileride ikinci
        // bir yavaslatma kaynagi eklendiginde guclu etkiyi zayifla ezerdi.
        if (slowFactor > 0f) {
            val existing = enemy.activeSlow
            enemy.activeSlow = if (existing == null) {
                SlowStatus(slowFactor, slowDuration)
            } else {
                SlowStatus(
                    factor = max(existing.factor, slowFactor),
                    durationRemainingSeconds = max(existing.durationRemainingSeconds, slowDuration)
                )
            }
        }

        if (enemy.isDead) {
            onEnemyKilled(enemy)
        }
    }

    private fun onEnemyKilled(enemy: EnemyEntity) {
        enemies.remove(enemy)
        _gold.value += enemy.rewardGold
        _score.value += enemy.rewardGold * 10
        saveManager.addEnemiesKilled(1)

        // Faz 3: arac olumu piyade olumunden AYRI duyulur; asset pack'teki
        // 15_vehicle_destroyed ve 05_explosion_heavy boylece kullanilir.
        val isVehicle = enemy.type == GameConfig.EnemyType.ARMORED_VEHICLE ||
            enemy.type == GameConfig.EnemyType.TANK ||
            enemy.type == GameConfig.EnemyType.COMMAND_TANK
        if (isVehicle) {
            audioManager.playSound(AudioManager.SoundEffect.VEHICLE_DESTROYED)
            // Boss olumu en agir geri bildirimi alir; sarsinti her zaman
            // sonumlenerek sifira iner (surekli sarsinti okunabilirligi bozar).
            triggerScreenShake(
                when (enemy.type) {
                    GameConfig.EnemyType.COMMAND_TANK -> 0.38f
                    GameConfig.EnemyType.TANK -> 0.22f
                    else -> 0.14f
                }
            )
            // Faz 14 - HIT STOP. Yalnizca ARACLARDA: piyade olumu saniyede
            // 4-6 kez oluyor ve her birinde donmak oyunu kekeme yapardi.
            // Agirlik hissi agir hedefe ait olmali.
            triggerHitStop(
                when (enemy.type) {
                    GameConfig.EnemyType.COMMAND_TANK -> GameFeel.HIT_STOP_BOSS_KILL
                    GameConfig.EnemyType.TANK -> GameFeel.HIT_STOP_TANK_KILL
                    else -> GameFeel.HIT_STOP_VEHICLE_KILL
                }
            )
        }
        audioManager.playSound(AudioManager.SoundEffect.COIN_EARNED)

        // --------------------------------------------------------------------
        // ZINCIR (kill-streak).
        //
        // Denetimin tespiti: 18 dusmanlik bir dalgada 18 kez AYNI ses ve AYNI
        // "+4g" yazisi cikiyordu, hicbir sey tirmanmiyordu. Artik ayni yuzen
        // yazi zincir sayacini TASIYOR (yeni bir nesne degil, ayni nesne) ve
        // kademe atlandiginda tek seferlik bir patlama uretiliyor.
        //
        // EKONOMIYE DOKUNULMADI: altin `enemy.rewardGold` olarak kaliyor.
        // --------------------------------------------------------------------
        val climbedTier = combo.registerKill()
        val comboLabel = if (combo.count >= ComboTracker.COMBO_LABEL_MIN_KILLS) {
            "  x${combo.count}"
        } else {
            ""
        }

        // Death effect and coin popup
        addEffect(
            VisualEffect(
                type = EffectType.ENEMY_DEATH,
                posX = enemy.posX,
                posY = enemy.posY,
                maxAgeSeconds = if (isVehicle) 0.60f else 0.45f,
                scale = enemy.radius / 15f
            )
        )

        addEffect(
            VisualEffect(
                type = EffectType.COIN_POPUP,
                posX = enemy.posX,
                posY = enemy.posY,
                maxAgeSeconds = 0.9f,
                text = "+${enemy.rewardGold}g$comboLabel",
                tier = combo.tier
            )
        )

        if (climbedTier > 0) {
            // Uc kanal AYNI KAREDE: gorsel patlama + ses + donma/sarsinti.
            // Ses bir kare gecikirse oyuncu bunu "gecikmeli kontrol" olarak
            // algilar.
            //
            // Faz 14: gecici `TOWER_UPGRADE` KALDIRILDI. Artik kademeye gore
            // yukselen `sfx_combo_up_1..4` seti calar; tirmanma perde,
            // parlaklik ve mix seviyesi olarak birlikte duyulur.
            audioManager.playComboTier(climbedTier)
            // DORDUNCU KANAL: dokunsal. Sesle AYNI satirda, ayni karede.
            haptics?.onComboTierUp(climbedTier)
            triggerScreenShake(0.10f + 0.03f * climbedTier)
            triggerHitStop(GameFeel.HIT_STOP_COMBO_TIER)
            addEffect(
                VisualEffect(
                    type = EffectType.COMBO_BURST,
                    posX = enemy.posX,
                    posY = enemy.posY,
                    maxAgeSeconds = 0.55f,
                    text = "x${combo.count}",
                    tier = climbedTier
                )
            )
        }
    }

    fun triggerScreenShake(durationSeconds: Float) {
        screenShakeDuration = durationSeconds
    }

    /**
     * Ekran flasi. Ust uste BINMEZ, en uzun/en yeni kazanir — sarsinti ile
     * ayni kural. Sifir ya da negatif sure flasi kapatir.
     */
    private fun triggerScreenFlash(durationSeconds: Float) {
        if (durationSeconds <= 0f) return
        screenFlashRemaining = durationSeconds
        screenFlashDuration = durationSeconds
    }

    /** Gecikmeli ses isareti kuyruga alinir; sifir gecikme ANINDA calar. */
    private fun scheduleSound(sound: AudioManager.SoundEffect, delaySeconds: Float) {
        if (delaySeconds <= 0f) {
            audioManager.playSound(sound)
            return
        }
        pendingSounds.add(PendingSound(delaySeconds, sound))
    }

    /**
     * Savas sifirlanmasi: bekleyen flas ve ses isaretleri DUSURULUR.
     *
     * Kuyruk temizlenmezse bir onceki savasta baslamis bir bombardiman, yeni
     * bolumun ilk karesinde ya da ana menude calardi.
     */
    private fun clearScreenFlashAndCues() {
        screenFlashRemaining = 0f
        screenFlashDuration = 0f
        pendingSounds.clear()
    }

    /**
     * Faz 14 - hit stop tetikleyici.
     *
     * Ust uste BINMEZ, EN UZUNU kazanir: ayni karede boss olumu ve top
     * patlamasi birlikte gelirse sureler toplansaydi oyun yarim saniye
     * donardi. Tavan [GameFeel.HIT_STOP_MAX_SECONDS].
     */
    fun triggerHitStop(seconds: Float) {
        if (seconds <= 0f) return
        hitStopRemainingSeconds =
            max(hitStopRemainingSeconds, seconds).coerceAtMost(GameFeel.HIT_STOP_MAX_SECONDS)
    }

    /** Test/teshis: simulasyon su an hit stop yuzunden donmus mu. */
    val isHitStopped: Boolean get() = hitStopRemainingSeconds > 0f

    /**
     * Gorsel efekt ekleme - TEK GIRIS NOKTASI.
     *
     * `visualEffects.add(...)` dogrudan cagrilmaz; butce kontrolu tek yerde
     * dursun (bkz. [GameFeel.MAX_VISUAL_EFFECTS]).
     */
    private fun addEffect(effect: VisualEffect) {
        if (visualEffects.size >= GameFeel.MAX_VISUAL_EFFECTS) {
            visualEffects.removeAt(0)
        }
        visualEffects.add(effect)
    }
}
