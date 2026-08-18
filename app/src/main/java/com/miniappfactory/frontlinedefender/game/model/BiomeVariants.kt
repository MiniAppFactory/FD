package com.miniappfactory.frontlinedefender.game.model

import androidx.annotation.StringRes
import com.miniappfactory.frontlinedefender.R

/**
 * =============================================================================
 * BIYOM VARYANTLARI - 11 taban harita x 5 biyom = 55 gorsel olarak farkli bolum
 * =============================================================================
 *
 * Kaynak plan: `docs/BIOME_VARIANTS.md` (2026-08-15, "teknik dogrulandi").
 * Bu dosya o dokumanin Kotlin karsiligidir. Buradaki sayilarin hicbiri tahmin
 * DEGILDIR - hepsi `map (1).png` uzerinde PIL ile OLCULDU ve 11 haritalik bir
 * kontak sayfasinda gozle dogrulandi (`docs/biome_previews/00_KONTAK_SAYFASI.png`).
 * Parametre degistiren, o kontak sayfasini yeniden uretip gozle bakmak
 * zorundadir; ek olarak `BiomeReadabilityTest` sayisal koruma saglar.
 *
 * NEDEN VAR
 *   Yeni harita sanati uretme imkani yok. 11 benzersiz harita var. Varyantlari
 *   dosya olarak paketlemek REDDEDILDI (55 x ~300 KB = ~16 MB, boyut butcesini
 *   tek basina asiyor). Bunun yerine varyant CIHAZDA, bolum yuklenirken TEK
 *   piksel gecisiyle uretilir -> APK maliyeti 0 bayt.
 *
 * BU DOSYA `GameConfig`'E BAGIMLI DEGILDIR - bilerek.
 *   Kampanya bugun 22 bolum. Asagidaki tablo 55'e hazir; kampanya
 *   genisletildiginde burada TEK SATIR bile degismez. `BiomeCampaignTableTest`
 *   tablonun bugunku `GameConfig.CAMPAIGN` ile 1..22 arasinda birebir
 *   ortustugunu KILITLER.
 *
 * NOT - `GameConfig.Biome` ile iliski:
 *   `GameConfig` icinde de bir `Biome` enum'u var (TEMPERATE/NIGHT/WINTER/DESERT).
 *   O, recolor uygulanmadan onceki "niyet beyani"ydi ve `LevelSpec.biome`
 *   alaninda yasiyor. Cizim yolunun TEK KAYNAGI artik buradaki `Biome`'dur.
 *   Ikisi cakismaz: `GameConfig.Biome` ic ice tanimli oldugu icin GameConfig'in
 *   kendi govdesinde onceligi vardir, disaridan `Biome` bu dosyadakine cozulur.
 */
enum class Biome(@StringRes val labelRes: Int) {
    /** Taban bitmap AYNEN kullanilir - hicbir piksele dokunulmaz. */
    ORIGINAL(R.string.biome_original),
    NIGHT(R.string.biome_night),
    WINTER(R.string.biome_winter),
    DESERT(R.string.biome_desert),
    AUTUMN(R.string.biome_autumn);

    /** true ise piksel gecisi HIC calistirilmaz; decode edilen bitmap dogrudan cizilir. */
    val isIdentity: Boolean get() = this == ORIGINAL
}

object BiomeVariants {

    // =========================================================================
    // 1. KAMPANYA TABLOSU - (bolum) -> (taban harita, biyom)
    // =========================================================================

    /** Benzersiz taban harita sayisi. `LevelGeometry.ALL_MAPS` ile ayni. */
    const val MAPS_PER_ACT = 11

    /** Biyom sayisi = act sayisi. */
    const val ACT_COUNT = 5

    /** 11 x 5 = 55. Bu tablonun cozebildigi en buyuk bolum numarasi. */
    const val TOTAL_LEVELS = MAPS_PER_ACT * ACT_COUNT

    /**
     * Act sirasi. `docs/BIOME_VARIANTS.md` 7. bolum 44 bolum icin
     * "orijinal / gece / kis / sonbahar-col karisik" demisti; 55'e cikinca
     * karisim gerekmiyor, dordu ayri act oldu.
     *
     * Sicaklik siralamasi bilincli: kis (soguk) -> col (sicak) -> sonbahar
     * (ilik gecis). Komsu act'lar arasinda en buyuk gorsel sicrama boylece
     * elde edilir; kis'tan sonra sonbahar gelseydi ikisi de dusuk doygunluklu
     * oldugundan gecis zayif hissedilirdi.
     */
    private val ACT_BIOMES = arrayOf(
        Biome.ORIGINAL, Biome.NIGHT, Biome.WINTER, Biome.DESERT, Biome.AUTUMN
    )

    /**
     * ------------------------------------------------------------------------
     * HARITA ATAMASI BURADA **YOK** — ve olmamali.
     * ------------------------------------------------------------------------
     * Bu dosyada bir zamanlar `baseMapIdFor(levelId)` vardi ve Act III+ icin
     * kaydirmasiz `11..1` sirasini kullaniyordu; `GameConfig.CAMPAIGN` ise
     * CAMPAIGN_55.md K4'un her perdede bir slot kaydiran sirasini. Iki tablo
     * ayni soruya iki farkli cevap veriyordu (ornek: L23 burada harita 11,
     * kampanya tablosunda harita 10) ve celiski bir testle "isimlendirilerek"
     * yasatiliyordu.
     *
     * Bugun gorsel bir hata URETMIYORDU — cizim yolu harita id'sini motordan,
     * yani `GameConfig.levelSpec(level).mapId`'den okuyor (`GameCanvas`:
     * `gameEngine.activeMapId`) ve `baseMapIdFor` yalnizca testlerde
     * cagriliyordu. Ama iki paralel dogruluk kaynagi, ileride birinin yanlisini
     * kullanacak bir tuzaktir.
     *
     * KARAR: **harita atamasinin tek sahibi `GameConfig.CAMPAIGN`'dir.** Bu
     * dosyanin isi RECOLOR, yani `levelId -> Biome`. Harita id'sine ihtiyaci
     * olan (test dahil) `GameConfig.levelSpec(levelId).mapId` okur. Boylece
     * `BiomeVariants`in GameConfig'e bagimli OLMAMA ozelligi de korunur:
     * burada harita bilgisi hic yoktur, kopyasi da yoktur.
     *
     * Biyom/act eslemesi (11'lik bloklar) iki tarafta ZATEN aynidir ve
     * `BiomeCampaignTableTest.biomePerActAgreesWithTheCampaignTable...`
     * bunu 55 bolumun tamami icin dogrular.
     */

    /** `levelId` -> 0..54 arasi tablo indeksi. 55'in otesi bassa doner (asla cokmez). */
    private fun tableIndex(levelId: Int): Int {
        val zeroBased = levelId - 1
        if (zeroBased < 0) return 0
        return zeroBased % TOTAL_LEVELS
    }

    /** Bolumun biyomu. Deterministik ve saf: ayni girdi HER ZAMAN ayni cikti. */
    fun biomeFor(levelId: Int): Biome = ACT_BIOMES[tableIndex(levelId) / MAPS_PER_ACT]

    /** 1 tabanli act numarasi (1..5). */
    fun actFor(levelId: Int): Int = tableIndex(levelId) / MAPS_PER_ACT + 1

    // =========================================================================
    // 2. OLCULMUS ESIKLER - bitki ortusu maskesi
    //
    // docs/BIOME_VARIANTS.md 2. bolum. PIL HSV olcegi: H, S, V hepsi 0..255.
    //
    // KRITIK: yol (H 24-28) ile cimen (H 37-42) yalnizca ~10 hue birimi ayri.
    // Global hue kaydirma DENENDI ve yol pembeye dondu -> reddedildi. Ayrim
    // IKI KANALLA yapilir:
    //     bitki  <=>  34 <= H <= 72  VE  S >= 95
    //   - yol dusuk hue ile disarida kalir
    //   - build pad (S~44), kaya (S~72), us (S~69) DUSUK DOYGUNLUKLA disarida kalir
    //   - su yuksek hue (138) ile disarida kalir
    // Bu, "oynanis okunabilirligi atmosferden once gelir" ilkesinin uygulamasidir.
    // =========================================================================

    const val VEG_HUE_MIN = 34
    const val VEG_HUE_MAX = 72
    const val VEG_SAT_MIN = 95

    /**
     * Maske kenari yumusatmasi. Sert maske gozde "kesik" gorunur.
     * Referans sigma 1.6 px; cihazda 3 ardisik kutu bulanikligi ile
     * yaklasilir (Pillow'un GaussianBlur'u da tam olarak boyle calisir).
     * 3 x yaricap-1 kutu => sigma = sqrt(3 * 1*2/3) = 1.41 px, referanstan
     * 0.19 px sapma - 1920 px genislikte gorunmez.
     */
    const val MASK_BLUR_BOX_PASSES = 3
    const val MASK_BLUR_BOX_RADIUS = 1
}

/**
 * =============================================================================
 * TEK YUVALI (harita, biyom) ONBELLEGI
 * =============================================================================
 *
 * Neden LRU degil de TEK yuva:
 *   Bir arkaplan 1920x1081 ARGB_8888 = **8.3 MB**. 55 varyantin hepsini —
 *   hatta birkacini — tutmak minSdk 24 cihazinda OOM demektir. Bu yuzden
 *   onbellek yalnizca **bir** (harita, biyom) ciftini tutar; yeni bir cift
 *   geldiginde eskisine olan tek referans birakilir ve cop toplayiciya kalir.
 *   `docs/BIOME_VARIANTS.md` 5. bolum "ayni anda yalnizca BIR bolum arkaplani
 *   bellekte tutulur" kuralinin uygulamasi budur.
 *
 * Ne kazandiriyor:
 *   Yenilgi -> TEKRAR DENE dongusu ayni bolume donuyor. Compose `remember`'i
 *   bunu yakalayamaz (Composable ekrandan cikip geri geliyor); process
 *   seviyesindeki bu yuva sayesinde tekrar denemede ne decode ne de recolor
 *   tekrar calisir. Oyuncunun en sik yaptigi gecis budur.
 *
 * Generic olmasinin tek sebebi TEST EDILEBILIRLIK: `ImageBitmap` JVM birim
 * testinde uretilemez, bu sinif `String` ile test edilir.
 */
class BiomeSlotCache<T : Any> {

    private var slotKey: Int = NO_KEY
    private var slotValue: T? = null

    @Synchronized
    fun get(mapId: Int, biome: Biome): T? =
        if (slotValue != null && slotKey == keyOf(mapId, biome)) slotValue else null

    @Synchronized
    fun put(mapId: Int, biome: Biome, value: T) {
        slotKey = keyOf(mapId, biome)
        slotValue = value
    }

    /** Bellek baskisinda / testler arasinda yuvayi bosalt. */
    @Synchronized
    fun clear() {
        slotKey = NO_KEY
        slotValue = null
    }

    /** Tutulan oge sayisi — sozlesme geregi her zaman 0 ya da 1. */
    val size: Int
        @Synchronized get() = if (slotValue == null) 0 else 1

    @Synchronized
    fun holds(mapId: Int, biome: Biome): Boolean = get(mapId, biome) != null

    private companion object {
        const val NO_KEY = -1
        fun keyOf(mapId: Int, biome: Biome) = mapId * 100 + biome.ordinal
    }
}

/**
 * Bir biyomun DOGRULANMIS piksel parametreleri (docs/BIOME_VARIANTS.md 3. bolum).
 *
 * `hueDelta` / `satMul` / `satAdd` / `valMul` / `valAdd` PIL'in
 * `Image.point()` LUT semantigiyle birebir ayni uygulanir:
 *     H' = (H + hueDelta) and 255
 *     S' = clamp(int(S * satMul + satAdd))
 *     V' = clamp(int(V * valMul + valAdd))
 *
 * `veilR/G/B` + `veilRatio`: donusum sonrasi TUM sahneye uygulanan ince perde.
 * `contrast`: yalnizca kis icin 1.05 (PIL `ImageEnhance.Contrast` semantigi).
 */
internal data class BiomeParams(
    val hueDelta: Int = 0,
    val satMul: Float = 1f,
    val satAdd: Float = 0f,
    val valMul: Float = 1f,
    val valAdd: Float = 0f,
    val veilR: Int = 0,
    val veilG: Int = 0,
    val veilB: Int = 0,
    val veilRatio: Float = 0f,
    val contrast: Float = 1f
)

/**
 * =============================================================================
 * TEK GECISLI HSV RECOLOR - saf Kotlin, Android bagimliligi YOK
 * =============================================================================
 *
 * Neden saf Kotlin: bu kodun tek gercek riski HSV donusumunun yanlis olcekte
 * olmasi (0..360 vs 0..255 -> `hueDelta = +118` bambaska bir renk uretir).
 * Saf `IntArray` API'si sayesinde JVM birim testi gercek pikselleri
 * dogrulayabiliyor; Robolectric/cihaz gerekmiyor.
 *
 * PERFORMANS SOZLESMESI
 *   - `apply` bolum YUKLENIRKEN bir kez cagrilir, kare dongusunde ASLA.
 *   - Cagiran taraf `Dispatchers.Default` uzerinde calistirmakla yukumludur
 *     (bkz. `GameSprites.loadBackground`).
 *   - Gecis O(n): 1 maske gecisi + 6 kutu-bulanikligi supurmesi + 1 renk gecisi
 *     (kis'ta +1 kontrast gecisi). 1920x1081 = 2.08M piksel.
 */
internal object BiomeRecolor {

    // -- docs/BIOME_VARIANTS.md 3. bolum tablosu, birebir --------------------
    //
    // GECE: TEK maskesiz biyom - gece butun sahneyi etkiler. Iki asamali ay
    //   isigi modeli (bkz. `applyNight`).
    // COL: yoldan ayrisma hue ile DEGIL value (+76) ve yarilanmis doygunlukla
    //   saglanir. Hue'yu yola dogru kaydirmak (ilk denemede -42) yolu YOK EDER.
    private val NIGHT = BiomeParams(
        hueDelta = 14, satMul = 0.66f, valMul = 0.66f,
        veilR = 18, veilG = 30, veilB = 68, veilRatio = 0.14f
    )
    private val WINTER = BiomeParams(
        hueDelta = 118, satMul = 0.12f, valMul = 0.58f, valAdd = 126f,
        veilR = 228, veilG = 238, veilB = 248, veilRatio = 0.09f, contrast = 1.05f
    )
    private val DESERT = BiomeParams(
        hueDelta = -6, satMul = 0.46f, satAdd = 6f, valMul = 0.72f, valAdd = 76f,
        veilR = 212, veilG = 186, veilB = 132, veilRatio = 0.08f
    )
    private val AUTUMN = BiomeParams(
        hueDelta = -24, satMul = 1.05f, satAdd = 14f, valMul = 0.94f, valAdd = 20f,
        veilR = 196, veilG = 126, veilB = 60, veilRatio = 0.07f
    )

    /**
     * GECE ikinci asamasi: bitki maskesinin TERSI (yol, build pad, us, kaya)
     * bu kadar geri aydinlatilir. Ay isigi yolu aydinlatir, cimen koyu kalir -
     * hem atmosferik hem OKUNUR.
     *
     * Ilk deneme `valMul = 0.48` + %20 perde idi ve REDDEDILDI: 11 haritanin
     * bir kisminda yol okunamiyordu.
     *
     * -------------------------------------------------------------------------
     * DOKUMANDAN SAPMA — 26'dan 40'a cikarildi. Gerekce OLCUM:
     *
     *   `BiomeReadabilityTest` yol ile cimen arasindaki CIE Lab dE76 mesafesini
     *   olcuyor. Orijinal (oynanabilirligi kanitlanmis) harita 20.47 veriyor.
     *   Belgelenen 26 degeriyle gece **9.69**'a dusuyordu — orijinalin yalnizca
     *   %47'si ve "acikca farkli renk" esiginin (10) altinda. Yani yol ile cimen
     *   gecede neredeyse ayni renge geliyordu.
     *
     *   26 -> 40 ile dE76 **14.8** (orijinalin ~%72'si), WCAG luminans orani
     *   1.35 -> 1.66 (orijinal 1.40'in USTUNDE).
     *
     *   Neden dokumandaki degeri korumadik: dokumanin kendi ilkesi
     *   "oynanis okunabilirligi atmosferden once gelir" ve dokuman bu parametreyi
     *   zaten AYNI sebeple bir kez yukseltmis (val 0.48 -> 0.66). Buradaki
     *   degisiklik o kararin devami, aksi degil. Atmosfer kaybi yok: sahnenin
     *   %52'si bitki ortusudur ve bitki bu parametreden HIC etkilenmez
     *   (aydinlatma bitki maskesinin TERSINE uygulanir).
     *
     *   ACIK MADDE: `docs/biome_previews/` altindaki Python kontak sayfasi bu
     *   yeni degerle YENIDEN URETILMELI (bu makinede Python kurulu degil).
     *   Sayisal koruma `BiomeReadabilityTest` icinde; gozle dogrulama bekliyor.
     * -------------------------------------------------------------------------
     */
    private const val NIGHT_ROAD_RELIGHT = 40

    internal fun paramsFor(biome: Biome): BiomeParams? = when (biome) {
        Biome.ORIGINAL -> null
        Biome.NIGHT -> NIGHT
        Biome.WINTER -> WINTER
        Biome.DESERT -> DESERT
        Biome.AUTUMN -> AUTUMN
    }

    /**
     * `pixels` YERINDE donusturulur. Format: packed ARGB_8888 (alpha korunur).
     *
     * `Biome.ORIGINAL` icin hicbir sey yapilmaz - bu bir optimizasyon degil,
     * DOGRULUK gereksinimidir: 8-bit HSV gidis-donusu kayiplidir, kimlik
     * donusumu bile pikselleri +-1 kaydirirdi.
     */
    fun apply(biome: Biome, pixels: IntArray, width: Int, height: Int) {
        val p = paramsFor(biome) ?: return
        require(width > 0 && height > 0 && pixels.size >= width * height) {
            "gecersiz bitmap boyutu ${width}x$height / ${pixels.size}"
        }
        val mask = buildVegetationMask(pixels, width, height)
        if (biome == Biome.NIGHT) applyNight(pixels, mask, p) else applyMasked(pixels, mask, p)
    }

    // =========================================================================
    // Bitki ortusu maskesi
    // =========================================================================

    /** 0 = dokunma (yol/pad/kaya/su/us), 255 = tam bitki. Kenarlar bulanik. */
    internal fun buildVegetationMask(pixels: IntArray, width: Int, height: Int): ByteArray {
        val n = width * height
        val mask = ByteArray(n)
        for (i in 0 until n) {
            val c = pixels[i]
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            val maxc = if (r > g) (if (r > b) r else b) else (if (g > b) g else b)
            val minc = if (r < g) (if (r < b) r else b) else (if (g < b) g else b)
            if (maxc == minc) continue // gri -> S = 0 -> bitki degil
            val hsv = rgbToHsv(r, g, b)
            if ((hsv shr 8 and 0xFF) < BiomeVariants.VEG_SAT_MIN) continue
            val h = hsv ushr 16
            if (h in BiomeVariants.VEG_HUE_MIN..BiomeVariants.VEG_HUE_MAX) {
                mask[i] = 255.toByte()
            }
        }
        blurMask(mask, width, height)
        return mask
    }

    /** Ayrilabilir kutu bulanikligi: once yatay N gecis, sonra dikey N gecis. */
    private fun blurMask(mask: ByteArray, width: Int, height: Int) {
        val tmp = ByteArray(mask.size)
        var src = mask
        var dst = tmp
        repeat(BiomeVariants.MASK_BLUR_BOX_PASSES) {
            boxBlurHorizontal(src, dst, width, height, BiomeVariants.MASK_BLUR_BOX_RADIUS)
            val t = src; src = dst; dst = t
        }
        repeat(BiomeVariants.MASK_BLUR_BOX_PASSES) {
            boxBlurVertical(src, dst, width, height, BiomeVariants.MASK_BLUR_BOX_RADIUS)
            val t = src; src = dst; dst = t
        }
        // Tek sayida takas olduysa sonuc `tmp`'de kaldi; geri kopyala.
        if (src !== mask) System.arraycopy(src, 0, mask, 0, mask.size)
    }

    private fun boxBlurHorizontal(src: ByteArray, dst: ByteArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)].toInt() and 0xFF
            for (x in 0 until w) {
                dst[row + x] = (sum / div).toByte()
                sum += (src[row + (x + r + 1).coerceIn(0, w - 1)].toInt() and 0xFF) -
                    (src[row + (x - r).coerceIn(0, w - 1)].toInt() and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(src: ByteArray, dst: ByteArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (k in -r..r) sum += src[k.coerceIn(0, h - 1) * w + x].toInt() and 0xFF
            for (y in 0 until h) {
                dst[y * w + x] = (sum / div).toByte()
                sum += (src[(y + r + 1).coerceIn(0, h - 1) * w + x].toInt() and 0xFF) -
                    (src[(y - r).coerceIn(0, h - 1) * w + x].toInt() and 0xFF)
            }
        }
    }

    // =========================================================================
    // Biyom uygulamasi
    // =========================================================================

    /**
     * ------------------------------------------------------------------------
     * PIKSEL DONUSUMUNU SATIR BANTLARINA BOLUP PARALEL KOSTURUR
     * ------------------------------------------------------------------------
     * OLCUM (performans ajani, uc bagimsiz kosu): NIGHT biyomu 137/209/314 ms,
     * digerleri 64-108 ms — yani **2,0-2,4 kat** ve belgelenen 87-135 ms
     * bandinin USTUNDE. Sebep [applyNight]'in maskesiz olmasi: HSV gidis-donusu
     * HER piksele uygulaniyor, maskeli biyomlar ise piksellerin bir kismini
     * atliyor. Is bolum YUKLENIRKEN yapiliyor ve tam o anda sprite decode +
     * ilk kompozisyon da kosuyor; ustelik Act II'nin **11 bolumunun tamami**
     * gece. Galaxy S8 / API 24'te JVM'deki 314 ms belirgin sekilde daha uzun.
     *
     * CIKTI BAYT BAYT AYNI: her piksel yalnizca KENDI degerinden turer
     * (komsu okuma yok, sirali durum yok), dolayisiyla indeks araligini bolmek
     * sonucu degistirmez. `BiomeRecolorTest`in determinizm ve LUT kilitleri
     * aynen gecerli kalir.
     *
     * @param body bandi isler ve (varsa) kismi toplamini dondurur.
     * @return bantlarin toplami — [applyMasked]'in luma birikimi icin.
     */
    private fun forEachBand(n: Int, body: (Int, Int) -> Long): Long {
        val forced = forcedBandCount
        val bands = forced
            ?: minOf(Runtime.getRuntime().availableProcessors(), MAX_RECOLOR_BANDS)
        if (bands <= 1 || (forced == null && n < MIN_PIXELS_FOR_PARALLEL)) return body(0, n)

        val step = (n + bands - 1) / bands
        val partial = LongArray(bands)
        val workers = ArrayList<Thread>(bands - 1)
        for (b in 1 until bands) {
            val from = b * step
            if (from >= n) break
            val to = minOf(n, from + step)
            val worker = Thread { partial[b] = body(from, to) }
            workers.add(worker)
            worker.start()
        }
        partial[0] = body(0, minOf(n, step))
        workers.forEach { it.join() }
        return partial.sum()
    }

    /**
     * Kucuk goruntude is parcalama maliyeti kazanci yer. 1920x1081 = 2,07 M
     * piksel; esik onun cok altinda ama testlerdeki minik goruntuleri (birkac
     * yuz piksel) tek is parcaciginda birakiyor.
     */
    private const val MIN_PIXELS_FOR_PARALLEL = 100_000

    /**
     * En fazla dort bant. Galaxy S8 big.LITTLE: sekiz cekirdegin dordu kucuk
     * ve bolum yuklenirken zaten decode/kompozisyon kosuyor — sekiz is
     * parcacigi ile asiri abone olmak kazanci geri veriyor.
     */
    private const val MAX_RECOLOR_BANDS = 4

    /**
     * YALNIZCA TEST ICIN: bant sayisini sabitler (`null` = uretim davranisi).
     *
     * "Cikti bayt bayt ayni" iddiasi ancak ayni goruntu 1 bant ve N bant ile
     * kosturulup KARSILASTIRILARAK kanitlanabilir; uretim yolu cekirdek
     * sayisina bagli oldugu icin test onu secemez. Bkz.
     * `BiomeRecolorTest.bandingProducesByteIdenticalOutput`.
     */
    internal var forcedBandCount: Int? = null

    /** Kis / col / sonbahar: donusum YALNIZCA bitki maskesi icinde. */
    private fun applyMasked(px: IntArray, mask: ByteArray, p: BiomeParams) {
        val hueLut = hueLut(p.hueDelta)
        val satLut = channelLut(p.satMul, p.satAdd)
        val valLut = channelLut(p.valMul, p.valAdd)
        val n = mask.size
        val lumaSum = forEachBand(n) { from, to -> maskedBand(px, mask, p, hueLut, satLut, valLut, from, to) }
        if (p.contrast != 1f) {
            applyContrast(px, n, p.contrast, (lumaSum.toDouble() / n + 0.5).toInt())
        }
    }

    private fun maskedBand(
        px: IntArray,
        mask: ByteArray,
        p: BiomeParams,
        hueLut: IntArray,
        satLut: IntArray,
        valLut: IntArray,
        from: Int,
        to: Int
    ): Long {
        var lumaSum = 0L
        for (i in from until to) {
            val c = px[i]
            val a = c and -0x1000000
            val r0 = (c ushr 16) and 0xFF
            val g0 = (c ushr 8) and 0xFF
            val b0 = c and 0xFF

            val m = mask[i].toInt() and 0xFF
            var r = r0
            var g = g0
            var b = b0
            if (m != 0) {
                val hsv = rgbToHsv(r0, g0, b0)
                val rgb = hsvToRgb(
                    hueLut[hsv ushr 16],
                    satLut[(hsv ushr 8) and 0xFF],
                    valLut[hsv and 0xFF]
                )
                // composite(donusmus, orijinal, maske)
                r = blendMask((rgb ushr 16) and 0xFF, r0, m)
                g = blendMask((rgb ushr 8) and 0xFF, g0, m)
                b = blendMask(rgb and 0xFF, b0, m)
            }
            // perde: TUM sahneye, maske disi dahil
            r = veil(r, p.veilR, p.veilRatio)
            g = veil(g, p.veilG, p.veilRatio)
            b = veil(b, p.veilB, p.veilRatio)

            px[i] = a or (r shl 16) or (g shl 8) or b
            if (p.contrast != 1f) lumaSum += (r * 299 + g * 587 + b * 114 + 500) / 1000
        }
        return lumaSum
    }

    /**
     * GECE - iki asamali ay isigi modeli.
     *   1) TUM sahne koyulur (maske yok) + mavi perde.
     *   2) Bitki maskesinin TERSI (yol, pad, us, kaya) `V + 26` ile geri
     *      aydinlatilir. Sonuc: yol ay isiginda parlar, bitki koyu kalir.
     */
    private fun applyNight(px: IntArray, mask: ByteArray, p: BiomeParams) {
        val hueLut = hueLut(p.hueDelta)
        val satLut = channelLut(p.satMul, p.satAdd)
        val valLut = channelLut(p.valMul, p.valAdd)
        forEachBand(mask.size) { from, to ->
            nightBand(px, mask, p, hueLut, satLut, valLut, from, to)
        }
    }

    private fun nightBand(
        px: IntArray,
        mask: ByteArray,
        p: BiomeParams,
        hueLut: IntArray,
        satLut: IntArray,
        valLut: IntArray,
        from: Int,
        to: Int
    ): Long {
        for (i in from until to) {
            val c = px[i]
            val a = c and -0x1000000
            val hsv = rgbToHsv((c ushr 16) and 0xFF, (c ushr 8) and 0xFF, c and 0xFF)
            val darkRgb = hsvToRgb(
                hueLut[hsv ushr 16],
                satLut[(hsv ushr 8) and 0xFF],
                valLut[hsv and 0xFF]
            )
            var dr = veil((darkRgb ushr 16) and 0xFF, p.veilR, p.veilRatio)
            var dg = veil((darkRgb ushr 8) and 0xFF, p.veilG, p.veilRatio)
            var db = veil(darkRgb and 0xFF, p.veilB, p.veilRatio)

            val roadWeight = 255 - (mask[i].toInt() and 0xFF)
            if (roadWeight != 0) {
                // "H ve S sabit, V += 40" ifadesi RGB'de saf bir OLCEKLEMEDIR:
                // HSV->RGB'de p, q, t ve v'nin hepsi V ile dogru orantili, yani
                // V'yi V' yapmak ucunu de V'/V ile carpar. Referans betik burada
                // tam bir HSV gidis-donusu yapiyor; onu oldugu gibi tasimak
                // piksel basina fazladan bir rgbToHsv + bir hsvToRgb demekti ve
                // gece biyomunu digerlerinden %35 yavas yapiyordu. Olcekleme hem
                // daha hizli hem daha DOGRU (8-bit HSV kuantalama kaybi yok).
                val v = if (dr > dg) (if (dr > db) dr else db) else (if (dg > db) dg else db)
                if (v > 0) {
                    val lifted = (v + NIGHT_ROAD_RELIGHT).coerceAtMost(255)
                    dr = blendMask((dr * lifted / v).coerceAtMost(255), dr, roadWeight)
                    dg = blendMask((dg * lifted / v).coerceAtMost(255), dg, roadWeight)
                    db = blendMask((db * lifted / v).coerceAtMost(255), db, roadWeight)
                }
            }
            px[i] = a or (dr shl 16) or (dg shl 8) or db
        }
        return 0L
    }

    /** PIL `ImageEnhance.Contrast`: gri ortalamaya gore blend. */
    private fun applyContrast(px: IntArray, n: Int, factor: Float, mean: Int) {
        val lut = IntArray(256) { (mean + factor * (it - mean)).toInt().coerceIn(0, 255) }
        for (i in 0 until n) {
            val c = px[i]
            px[i] = (c and -0x1000000) or
                (lut[(c ushr 16) and 0xFF] shl 16) or
                (lut[(c ushr 8) and 0xFF] shl 8) or
                lut[c and 0xFF]
        }
    }

    // =========================================================================
    // Yardimcilar - PIL semantigi
    // =========================================================================

    /** PIL: `h.point([(i + d) and 255 ...])` - hue dairesel, tasma sarar. */
    internal fun hueLut(delta: Int) = IntArray(256) { (it + delta) and 255 }

    /** PIL: `s.point([max(0, min(255, int(i * k + add))) ...])` - int() KESER. */
    internal fun channelLut(mul: Float, add: Float) =
        IntArray(256) { (it * mul + add).toInt().coerceIn(0, 255) }

    /** PIL `Image.blend(im, sabit_renk, oran)`. */
    private fun veil(c: Int, target: Int, ratio: Float): Int =
        if (ratio == 0f) c else (c + ratio * (target - c)).toInt().coerceIn(0, 255)

    /** PIL `Image.composite(a, b, mask)` - mask=255 iken a, 0 iken b. */
    private fun blendMask(a: Int, b: Int, m: Int): Int = (a * m + b * (255 - m) + 127) / 255

    /**
     * RGB -> HSV, PIL olceginde: H, S, V hepsi 0..255.
     *
     * OLCEK KRITIK: `hueDelta = +118` (kis) 0..255 olceginde ~166 dereceye,
     * yani yesilden maviye karsilik gelir. Ayni sayi 0..360 olceginde bambaska
     * bir renk uretir ve kis biyomu kar yerine mor olurdu.
     *
     * @return packed (h shl 16) or (s shl 8) or v
     */
    internal fun rgbToHsv(r: Int, g: Int, b: Int): Int {
        val maxc = if (r > g) (if (r > b) r else b) else (if (g > b) g else b)
        val minc = if (r < g) (if (r < b) r else b) else (if (g < b) g else b)
        if (maxc == minc) return maxc // h = 0, s = 0
        val cr = (maxc - minc).toFloat()
        val s = (255f * cr / maxc).toInt().coerceIn(0, 255)
        val rc = (maxc - r) / cr
        val gc = (maxc - g) / cr
        val bc = (maxc - b) / cr
        var hf = when (maxc) {
            r -> bc - gc
            g -> 2f + rc - bc
            else -> 4f + gc - rc
        } / 6f
        if (hf < 0f) hf += 1f
        val h = (hf * 255f).toInt().coerceIn(0, 255)
        return (h shl 16) or (s shl 8) or maxc
    }

    /** HSV (0..255 x3) -> packed RGB. `rgbToHsv`'nin tersi. */
    internal fun hsvToRgb(h: Int, s: Int, v: Int): Int {
        if (s == 0) return (v shl 16) or (v shl 8) or v
        val h6 = h * 6f / 255f
        var sector = h6.toInt()
        if (sector > 5) sector = 5
        val f = h6 - sector
        val p = (v * (255 - s) / 255f).toInt().coerceIn(0, 255)
        val q = (v * (255 - s * f) / 255f).toInt().coerceIn(0, 255)
        val t = (v * (255 - s * (1f - f)) / 255f).toInt().coerceIn(0, 255)
        return when (sector) {
            0 -> (v shl 16) or (t shl 8) or p
            1 -> (q shl 16) or (v shl 8) or p
            2 -> (p shl 16) or (v shl 8) or t
            3 -> (p shl 16) or (q shl 8) or v
            4 -> (t shl 16) or (p shl 8) or v
            else -> (v shl 16) or (p shl 8) or q
        }
    }
}
