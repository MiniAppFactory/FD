package com.miniappfactory.frontlinedefender.game.economy

/**
 * Faz 10 — GUCLENDIRICILER (savas ici tek kullanimlik).
 *
 * **Android importu YOK.** Saf veri + saf fonksiyonlar; JVM'de Robolectric olmadan
 * kosar. Tum sayilar [EconomyConfig]'ten gelir.
 *
 * ---------------------------------------------------------------------------------
 * 1. TASARIM AKSIYOMU — "REKLAM YERINE GECMEZ, OTESINI ACAR"
 * ---------------------------------------------------------------------------------
 * Ayni sonucu veren reklamsiz bir yol varsa oyuncu daima onu secer ve reklam olur.
 * Bu paket arbitraji **tanim geregi** imkansiz kilar:
 *
 *   Ucretli yol savas basina kullanim #1'i alir.
 *   Reklam yolu YALNIZCA kullanim #2'yi alir ve #2 hicbir fiyata satilmaz.
 *
 * Yani iki yol **ikame degil, siralidir**. [boosterAllowed] bunu sert kural olarak
 * uygular: ucretli yolu olan bir guclendiricide `viaAd = true` cagrisi, ucretli
 * kullanim tukenmeden [BoosterDecision.PaidPathNotExhausted] doner.
 *
 * Istisna, ucretli yolu HIC OLMAYAN tiplerdir ([BoosterCurrency.AD_ONLY]):
 * [BoosterType.EMERGENCY_SUPPLY] ve — 2026-08-21'den beri —
 * [BoosterType.AIR_SUPPORT]. Ikame edecek bir yol olmadigi icin arbitraj bu
 * tiplerde yapisal olarak imkansizdir; kalkanin uzerinde calistigi TEK
 * guclendirici artik [BoosterType.BASE_REPAIR]'dir.
 *
 * ---------------------------------------------------------------------------------
 * 2. PARA BIRIMI KARARLARI (gerekceli)
 * ---------------------------------------------------------------------------------
 * - [BoosterType.AIR_SUPPORT] -> **YALNIZCA REKLAM** (2026-08-21'den beri).
 *   Eskiden Tedarikle fiyatlanmisti ve "3. kule mi, hava destegi mi" savas ici
 *   taktik karariydi. O karar KALKTI: guclendirici artik Tedarik harcamiyor,
 *   bedeli para degil ZAMAN (reklam izleme + 45 sn bekleme). Pay-to-win kalkani
 *   korunuyor, hatta guclendi — hicbir bakiye bu guclendiriciyi satin alamaz.
 *
 *   ⚠ BAYAT VERI: [boosterPrice] hâlâ AIR_SUPPORT icin sifir olmayan bir Tedarik
 *   fiyati donduruyor ve kendi sozlesmesiyle ("AD_ONLY tipler icin 0") celisiyor.
 *   Bugun ULASILAMAZ (ucretli yol [BoosterDecision.PaidPathUnavailable] ile
 *   kapali) ve ulasilamazlik `adOnlyBoostersCanNeverChargeAnyCurrency` testiyle
 *   kilitli. Fiyati 0'a cekmek ayri bir urun karari.
 *
 * - [BoosterType.BASE_REPAIR] -> **COIN**. Etkisi savas ici Tedarik dongusunun
 *   disindadir (us cani), dolayisiyla Tedarikle fiyatlamak anlamsiz olurdu; ayrica
 *   coin agacina (13.900) karsi gercek bir firsat maliyeti yaratir. **Konuslanma
 *   Rezervi'ne tabidir** — rezerv coini tamire harcanamaz, yoksa soft-lock garantisi
 *   kirilir.
 *
 * - [BoosterType.EMERGENCY_SUPPLY] -> **YALNIZCA REKLAM**. Coin ile satilamaz, cunku
 *   coin -> Tedarik donusumu DECISIONS/GDD D.4 tarafindan YASAKTIR. Tedarikle de
 *   satilamaz (dairesel). Geriye tek mesru yol kalir: rewarded. Bu bir kisitlama
 *   degil avantaj: ilk 6 bolumde hic coini olmayan oyuncuya "neden reklam izleyeyim"
 *   sorusunun cevabini verir ve GDD D.4'u ihlal etmez (yeni bir KAYNAK, donusum degil).
 *   Kill switch: [EconomyConfig.EMERGENCY_SUPPLY_ENABLED].
 *
 * ---------------------------------------------------------------------------------
 * 3. NEDEN ENVANTER DEGIL
 * ---------------------------------------------------------------------------------
 * Guclendiriciler **savasa kapsamlidir**: satin alinir, o savasta kullanilir, savas
 * bitince kaybolur. Stoklanabilir envanter olsaydi (a) yeni `SaveManager` alani ve
 * migrasyon gerekirdi, (b) "20 tane biriktir, son bolumu ez" farming'i acilirdi,
 * (c) uygulamayi yeniden baslatarak sayac sifirlama exploit'i dogar. Savasa kapsamli
 * model bunlarin ucunu de yapisal olarak kapatir ve testcinin istedigi seyi
 * ("zor durumda kalinca 1 kereye mahsus") birebir karsilar.
 *
 * Savaslar arasi tasinan TEK sayac [BoosterState.adViewsToday]'dir ve **coin
 * odemedigi icin** kaybolmasi ekonomi exploit'i DEGILDIR (yalnizca oyuncu daha fazla
 * reklam izleyebilir — gelir yonu olumlu). Kaliciligi opsiyoneldir; bkz.
 * ECONOMY_SPEC 9 devir listesi.
 *
 * ---------------------------------------------------------------------------------
 * 4. PAY-TO-WIN YOK
 * ---------------------------------------------------------------------------------
 * - Her bolum **sifir guclendiriciyle** gecilebilir olmali; guclendiriciler
 *   `LEVEL_DESIGN` zorluk hesabina GIRMEZ.
 * - [EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION] < 1,0 — hava destegi tam canli hicbir
 *   dusmani tek basina oldurmez, dolayisiyla "dalga temizleme butonu" degildir.
 * - [BoosterType.BASE_REPAIR] **yildiz notrdur**: yildiz, savas boyunca gorulen EN
 *   DUSUK cana gore hesaplanir ([effectiveStarHealth]), yani tamir hayatta kalma
 *   satin alir, ASLA yildiz/coin satin almaz. Bu olmadan "tamir et -> yildiz atla ->
 *   tamirden fazla coin kazan" arbitraji acilirdi.
 */

// =====================================================================================
// Tipler
// =====================================================================================

/** Bir guclendiricinin ucretli yolunun hangi para biriminde oldugu. */
enum class BoosterCurrency {
    /** Savas ici Tedarik (bolum sonu sifirlanir). */
    SUPPLY,

    /** Meta Coin (kalici). Konuslanma Rezervi'ne tabidir. */
    COIN,

    /** Ucretli yol YOK — yalnizca rewarded reklamla alinir. */
    AD_ONLY,
}

/**
 * Savas ici tek kullanimlik guclendiriciler.
 *
 * @param unlockLevel bu guclendiricinin **oynanan bolum** bu degere ulastiginda gorunur.
 * @param currency ucretli yolun para birimi ([BoosterCurrency.AD_ONLY] = ucretli yol yok).
 * @param paidUsesPerBattle ucretli yolla savas basina en fazla kac kullanim.
 * @param adUsesPerBattle rewarded reklamla savas basina en fazla kac EK kullanim.
 */
enum class BoosterType(
    val unlockLevel: Int,
    val currency: BoosterCurrency,
    val paidUsesPerBattle: Int,
    val adUsesPerBattle: Int,
) {
    /**
     * **Acil Tedarik** — aninda Tedarik enjeksiyonu.
     * Ucretli yolu yok (GDD D.4: coin -> Tedarik donusumu yasak). Ilk 6 bolumde
     * reklamin tek anlamli degeri budur.
     */
    EMERGENCY_SUPPLY(
        unlockLevel = EconomyConfig.EMERGENCY_SUPPLY_UNLOCK_LEVEL,
        currency = BoosterCurrency.AD_ONLY,
        paidUsesPerBattle = 0,
        adUsesPerBattle = 1,
    ),

    /**
     * **Hava Destegi** — ekrandaki tum dusmanlara agir hasar, uzun bekleme.
     * Her bolume DEAKTIF baslar; yalnizca rewarded reklamla aktiflesir ve bir
     * bolumde IKI kez cagrilabilir.
     *
     * ⚠ 2026-08-21 — TAMAMEN REKLAM YOLUNA CEVRILDI (kullanici karari).
     *
     * Eskiden Tedarikle alinirdi (paid 1 + reklam 1). Yeni kural: her bolume
     * DEAKTIF baslar, reklam izleyince aktiflesir, ve bir bolumde IKI kez
     * izlenip iki kez cagrilabilir.
     *
     * Gerekce kullanicinin kendi sozu: *"ad izlemeye tesvik etmeliyiz."*
     * Tedarik yolu acikken oyuncunun reklam izlemek icin sebebi yoktu —
     * guclendirici gelir getirmiyordu, sadece Tedarik yakiyordu.
     *
     * TASARIM ETKISI acikca soylenmeli: hava destegi artik "3. kule mi, hava
     * destegi mi" TAKTIK KARARI OLMAKTAN CIKTI. Tedarik harcanmadigi icin
     * savas ici ekonomiye dokunmuyor; bedeli para degil ZAMAN.
     */
    AIR_SUPPORT(
        unlockLevel = EconomyConfig.AIR_SUPPORT_UNLOCK_LEVEL,
        currency = BoosterCurrency.AD_ONLY,
        paidUsesPerBattle = 0,
        adUsesPerBattle = 2,
    ),

    /**
     * **Us Tamiri** — kaybedilen us caninin bir kismini geri verir.
     * Coinle alinir, rezerve dokunmaz, **yildiz notrdur** ([effectiveStarHealth]).
     */
    BASE_REPAIR(
        unlockLevel = EconomyConfig.BASE_REPAIR_UNLOCK_LEVEL,
        currency = BoosterCurrency.COIN,
        paidUsesPerBattle = 1,
        adUsesPerBattle = 1,
    ),
    ;

    /** Savas basina toplam kullanim tavani (ucretli + reklam). */
    val maxUsesPerBattle: Int get() = paidUsesPerBattle + adUsesPerBattle

    /** Ucretli bir yolu var mi? */
    val hasPaidPath: Boolean get() = currency != BoosterCurrency.AD_ONLY

    /** Bu guclendirici bu build'de acik mi (kill switch). */
    val enabled: Boolean
        get() = when (this) {
            EMERGENCY_SUPPLY -> EconomyConfig.EMERGENCY_SUPPLY_ENABLED
            AIR_SUPPORT -> EconomyConfig.AIR_SUPPORT_ENABLED
            BASE_REPAIR -> EconomyConfig.BASE_REPAIR_ENABLED
        }
}

/**
 * Bir savasin guclendirici durumu. **Savasa kapsamlidir**: [startBattle] ile dogar,
 * savas bitince atilir. Tek istisna [adViewsToday].
 *
 * @param level oynanan bolum. Fiyat ve etki bundan turetilir.
 * @param paidUses guclendirici -> bu savasta ucretli yolla yapilan kullanim sayisi.
 * @param adUses guclendirici -> bu savasta reklamla yapilan kullanim sayisi.
 * @param adViewsToday **gun boyunca** guclendirici icin izlenen dolu reklam sayisi.
 *   Coin odemez, bu yuzden kaybolmasi ekonomi exploit'i degildir (bkz. dosya basi 3).
 * @param repairedHealth [BoosterType.BASE_REPAIR] ile geri verilen toplam us cani.
 *   Yildiz hesabindan DUSULUR ([effectiveStarHealth]) — tamir yildiz satin almaz.
 * @param lastUseMs guclendirici -> son kullanim ani (motor saati). Bekleme kontrolu.
 */
data class BoosterState(
    val level: Int,
    val paidUses: Map<BoosterType, Int> = emptyMap(),
    val adUses: Map<BoosterType, Int> = emptyMap(),
    val adViewsToday: Int = 0,
    val repairedHealth: Int = 0,
    val lastUseMs: Map<BoosterType, Long> = emptyMap(),
) {
    init {
        require(level >= 1) { "level >= 1 olmali: $level" }
        require(adViewsToday >= 0) { "negatif gunluk reklam sayaci: $adViewsToday" }
        require(repairedHealth >= 0) { "negatif tamir: $repairedHealth" }
        BoosterType.entries.forEach { type ->
            val paid = paidUses[type] ?: 0
            val ad = adUses[type] ?: 0
            require(paid in 0..type.paidUsesPerBattle) { "$type ucretli kullanim $paid aralik disi" }
            require(ad in 0..type.adUsesPerBattle) { "$type reklam kullanimi $ad aralik disi" }
        }
    }

    fun paidUsesOf(type: BoosterType): Int = paidUses[type] ?: 0
    fun adUsesOf(type: BoosterType): Int = adUses[type] ?: 0
    fun usesOf(type: BoosterType): Int = paidUsesOf(type) + adUsesOf(type)

    /** Bu gun guclendirici reklami icin kalan hak. */
    val adViewsLeftToday: Int
        get() = (EconomyConfig.BOOSTER_AD_VIEWS_PER_DAY - adViewsToday).coerceAtLeast(0)

    companion object {
        /** Yeni savas. Gunluk reklam sayaci tasinir, kullanim sayaclari sifirlanir. */
        fun startBattle(level: Int, adViewsToday: Int = 0): BoosterState =
            BoosterState(level = level, adViewsToday = adViewsToday.coerceAtLeast(0))
    }
}

/**
 * Bir guclendirici kullanma denemesinin sonucu.
 *
 * Siralama **analytics sozlesmesidir**: [ReserveLocked] yalnizca oyuncu parayi
 * gercekten karsilayabildigi halde rezerv engellediginde doner (`reserve_lock_blocked`
 * metrigi), bakiye zaten yetmiyorsa [InsufficientCoins] doner.
 */
sealed class BoosterDecision {
    /**
     * @param price odenecek miktar (AD_ONLY ve reklam yolunda 0).
     * @param currency hangi para biriminden dusulecek.
     * @param viaAd reklam yoluyla mi.
     */
    data class Allowed(
        val price: Int,
        val currency: BoosterCurrency,
        val viaAd: Boolean,
    ) : BoosterDecision()

    /** Bu build'de kapali (kill switch). */
    data object Disabled : BoosterDecision()

    /** Bolum henuz bu guclendiriciyi acmadi. */
    data class Locked(val requiredLevel: Int) : BoosterDecision()

    /** Ucretli kullanim hakki bu savasta tukendi. */
    data object PaidLimitReached : BoosterDecision()

    /** Reklam kullanim hakki bu savasta tukendi. */
    data object AdLimitReached : BoosterDecision()

    /** Gunluk guclendirici-reklam hakki tukendi. */
    data class DailyAdLimitReached(val perDay: Int) : BoosterDecision()

    /**
     * **ARBITRAJ KALKANI.** Reklam yolu ucretli yolun YERINE gecemez; once ucretli
     * kullanim tuketilmeli. Bu dal olmadan oyuncu 120 Tedarik yerine bedava reklam
     * secer ve Tedarik fiyatlamasi anlamsizlasir.
     */
    data class PaidPathNotExhausted(val paidUsesLeft: Int) : BoosterDecision()

    /** Bu guclendiricinin ucretli yolu YOK (yalnizca reklam). */
    data object PaidPathUnavailable : BoosterDecision()

    /** Savas ici Tedarik yetmiyor. */
    data class InsufficientSupply(val price: Int, val shortfall: Int) : BoosterDecision()

    /** Coin bakiyesi fiyati hic karsilamiyor. */
    data class InsufficientCoins(val price: Int, val shortfall: Int) : BoosterDecision()

    /** Bakiye yetiyor ama alim Konuslanma Rezervi'ni ihlal ediyor. */
    data class ReserveLocked(val price: Int, val reserve: Int, val shortfall: Int) : BoosterDecision()

    /** Bekleme suresi dolmadi. */
    data class Cooldown(val remainingMs: Long) : BoosterDecision()

    /** Etkisi olmayacak (ornek: us cani zaten tam, tamire gerek yok). */
    data object NoEffect : BoosterDecision()

    val isAllowed: Boolean get() = this is Allowed
}

// =====================================================================================
// Fiyat ve etki — saf fonksiyonlar
// =====================================================================================

/**
 * Bu bolumde bu guclendiricinin **ucretli** fiyati.
 *
 * AD_ONLY tipler ve reklam yolu icin 0. Fiyat bolumle dogrusal buyur ki gec oyunda
 * "anlamsiz derecede ucuz" olmasin (ECONOMY_SPEC B fiyat tablosu).
 */
fun boosterPrice(type: BoosterType, level: Int): Int {
    require(level in 1..EconomyConfig.CAMPAIGN_LEVELS) { "level $level aralik disi" }
    return when (type) {
        BoosterType.EMERGENCY_SUPPLY -> 0
        BoosterType.AIR_SUPPORT ->
            EconomyConfig.AIR_SUPPORT_SUPPLY_BASE +
                EconomyConfig.AIR_SUPPORT_SUPPLY_STEP * (level - 1)
        BoosterType.BASE_REPAIR ->
            EconomyConfig.BASE_REPAIR_COIN_BASE +
                EconomyConfig.BASE_REPAIR_COIN_STEP * (level - 1)
    }
}

/**
 * Acil Tedarik'in verdigi Tedarik miktari.
 *
 * SERT KISIT (ECONOMY_SPEC A.4): bu miktar, sikilastirilmis bolum butcesine
 * eklendiginde eski (fazla bol) butceyi **asmamalidir**. Yani reklam izleyen oyuncu
 * bile bugunku bol ekonomiden daha rahat olmaz — reklam sikilastirmayi geri almaz.
 */
fun emergencySupplyAmount(level: Int): Int {
    require(level in 1..EconomyConfig.CAMPAIGN_LEVELS) { "level $level aralik disi" }
    return roundToTen(
        (EconomyConfig.EMERGENCY_SUPPLY_BASE +
            EconomyConfig.EMERGENCY_SUPPLY_STEP * (level - 1)).toDouble()
    )
}

/**
 * Us Tamiri'nin geri verecegi can. Kaybedilmis candan fazlasini vermez, maks cani
 * asmaz. Us cani zaten tamsa 0 doner ve [boosterAllowed] [BoosterDecision.NoEffect] verir.
 */
fun baseRepairAmount(currentHealth: Int, maxHealth: Int, viaAd: Boolean = false): Int {
    require(maxHealth > 0) { "maxHealth > 0 olmali" }
    val lost = (maxHealth - currentHealth).coerceAtLeast(0)
    if (lost == 0) return 0
    // Iki yol, iki sayi: coin 4 / reklam 7. Kayip candan FAZLASI verilmez —
    // "tam cana tamamla" bir tavan degil, sadece israfi engelleyen sinir.
    val nominal =
        if (viaAd) EconomyConfig.BASE_REPAIR_LIVES_AD else EconomyConfig.BASE_REPAIR_LIVES_PAID
    return minOf(nominal, lost)
}

/** Bu guclendiricinin iki kullanimi arasindaki zorunlu bekleme. */
fun boosterCooldownMs(type: BoosterType): Long = when (type) {
    BoosterType.AIR_SUPPORT -> EconomyConfig.AIR_SUPPORT_COOLDOWN_MS
    BoosterType.EMERGENCY_SUPPLY -> EconomyConfig.EMERGENCY_SUPPLY_COOLDOWN_MS
    BoosterType.BASE_REPAIR -> EconomyConfig.BASE_REPAIR_COOLDOWN_MS
}

/** Bu bolumde bu guclendirici gorunur mu? */
fun boosterUnlocked(type: BoosterType, level: Int): Boolean =
    type.enabled && level >= type.unlockLevel

/** Bu bolumde gorunen guclendiriciler, HUD siralamasinda. */
fun boostersAvailableAt(level: Int): List<BoosterType> =
    BoosterType.entries.filter { boosterUnlocked(it, level) }

// =====================================================================================
// Izin karari
// =====================================================================================

/**
 * [boosterAllowed] `enemiesOnField` parametresinin "BILINMIYOR" degeri.
 *
 * NEDEN NEGATIF BIR NOBET DEGERI: sifir gecerli ve ANLAMLI bir sayidir ("saha bos"),
 * dolayisiyla varsayilan olamaz — varsayilani 0 yapmak, dusman sayisini bildirmeyen
 * her cagirani (ekonomi simulasyonu, birim testleri, ileride eklenecek tutorial veya
 * otomatik oynatma) sessizce "hedef yok" dalina dusururdu. Negatif deger ise savas
 * alaninda ASLA uretilemez, bu yuzden "bu cagiran sahayi bilmiyor" bilgisini gecerli
 * sayimlardan kesin olarak ayirir ve kontrol atlanir: bilgi yoksa ekonomi katmani
 * oyuncuyu cezalandirmaz.
 */
const val ENEMY_COUNT_UNKNOWN: Int = -1

/**
 * Bir guclendiriciyi kullanmaya izin var mi?
 *
 * KONTROL SIRASI (analytics ve UI mesaji bu siraya baglidir):
 * kill switch -> bolum kilidi -> kullanim tavani -> **arbitraj kalkani** -> gunluk
 * reklam hakki -> bekleme -> etki -> odeme gucu -> rezerv.
 *
 * "ETKI" adimi guclendiricinin **hicbir sey yapmayacagi** durumlari yakalar ve
 * [BoosterDecision.NoEffect] doner. Bu adim odeme gucu kontrolunden ONCE gelir:
 * etkisiz bir kullanim ucret kesmeden, kullanim hakki yakmadan ve bekleme
 * baslatmadan reddedilmelidir. Iki durum vardir:
 *   - [BoosterType.BASE_REPAIR] ve us cani zaten tam,
 *   - [BoosterType.AIR_SUPPORT] ve sahada hedef yok ([enemiesOnField] == 0).
 *
 * @param viaAd rewarded reklam yolu mu deneniyor.
 * @param supplyOnHand savas ici mevcut Tedarik (yalnizca SUPPLY fiyatli tipler icin).
 * @param baseHealth / [maxBaseHealth] yalnizca [BoosterType.BASE_REPAIR] icin anlamli.
 * @param enemiesOnField su an sahada bulunan dusman sayisi; yalnizca
 *   [BoosterType.AIR_SUPPORT] icin anlamli. [ENEMY_COUNT_UNKNOWN] (varsayilan) =
 *   cagiran sahayi bildirmiyor, hedef kontrolu atlanir.
 */
fun boosterAllowed(
    state: BoosterState,
    type: BoosterType,
    viaAd: Boolean,
    wallet: PlayerWallet,
    supplyOnHand: Int = 0,
    baseHealth: Int = 0,
    maxBaseHealth: Int = EconomyConfig.BASE_MAX_HEALTH,
    enemiesOnField: Int = ENEMY_COUNT_UNKNOWN,
    nowMs: Long = Long.MAX_VALUE / 2,
): BoosterDecision {
    if (!type.enabled) return BoosterDecision.Disabled
    if (state.level < type.unlockLevel) return BoosterDecision.Locked(type.unlockLevel)

    if (viaAd) {
        if (state.adUsesOf(type) >= type.adUsesPerBattle) return BoosterDecision.AdLimitReached
        // ARBITRAJ KALKANI: ucretli yolu olan bir guclendiricide reklam, ucretli
        // kullanim tukenmeden acilmaz. Reklam ikame degil, uzantidir.
        if (type.hasPaidPath) {
            val left = type.paidUsesPerBattle - state.paidUsesOf(type)
            if (left > 0) return BoosterDecision.PaidPathNotExhausted(left)
        }
        if (state.adViewsLeftToday <= 0) {
            return BoosterDecision.DailyAdLimitReached(EconomyConfig.BOOSTER_AD_VIEWS_PER_DAY)
        }
    } else {
        if (!type.hasPaidPath) return BoosterDecision.PaidPathUnavailable
        if (state.paidUsesOf(type) >= type.paidUsesPerBattle) return BoosterDecision.PaidLimitReached
    }

    val cooldown = boosterCooldownMs(type)
    val last = state.lastUseMs[type]
    if (last != null && cooldown > 0L && nowMs - last < cooldown) {
        return BoosterDecision.Cooldown(cooldown - (nowMs - last))
    }

    // ETKI. Ucret kesilmeden, kullanim hakki yakilmadan ve bekleme baslatilmadan
    // once "bu kullanim hicbir sey yapmayacak" durumlari elenir.
    if (type == BoosterType.BASE_REPAIR && baseRepairAmount(baseHealth, maxBaseHealth) == 0) {
        return BoosterDecision.NoEffect
    }
    // Hava Destegi ekrandaki dusmanlara vurur; hedef yoksa savas basina IKI reklam
    // hakkindan biri ve gunluk reklam butcesinden bir gosterim HICBIR SEYE yanar,
    // ustune 45 sn bekleme baslar. Geri alma yok. (2026-08-21 oncesinde yanan sey
    // 96-264 Tedarik'ti; reklam-only modelde bedel Tedarik degil, oyuncunun
    // izledigi reklam — yani kapinin sebebi ZAYIFLAMADI, guclendi.) Kapi bu yuzden
    // ekonomi katmanindadir (UI'da degil): yeni bir cagiran eklendiginde sessizce
    // atlanmaz.
    if (type == BoosterType.AIR_SUPPORT && enemiesOnField == 0) {
        return BoosterDecision.NoEffect
    }

    // Reklam yolu hicbir para birimi odemez.
    val price = if (viaAd) 0 else boosterPrice(type, state.level)
    if (viaAd || price == 0) {
        return BoosterDecision.Allowed(0, type.currency, viaAd)
    }

    return when (type.currency) {
        BoosterCurrency.SUPPLY -> {
            if (supplyOnHand < price) {
                BoosterDecision.InsufficientSupply(price, price - supplyOnHand)
            } else {
                BoosterDecision.Allowed(price, BoosterCurrency.SUPPLY, false)
            }
        }

        BoosterCurrency.COIN -> {
            if (wallet.coins < price) {
                BoosterDecision.InsufficientCoins(price, price - wallet.coins)
            } else {
                // Rezerv coini guclendiriciye HARCANAMAZ; soft-lock garantisi (GDD C.4/1)
                // guclendiriciler yuzunden kirilmaz.
                val reserve = reserveFor(wallet)
                if (wallet.coins - price < reserve) {
                    BoosterDecision.ReserveLocked(price, reserve, reserve - (wallet.coins - price))
                } else {
                    BoosterDecision.Allowed(price, BoosterCurrency.COIN, false)
                }
            }
        }

        BoosterCurrency.AD_ONLY -> BoosterDecision.PaidPathUnavailable
    }
}

/**
 * Kullanimi durum uzerine isler. **Reddedilen kullanimda durum DEGISMEZ.**
 *
 * Coin dususu burada YAPILMAZ — cuzdan mutasyonu [payForBooster] ile ayridir ki
 * motor Tedarik dususunu kendi tarafinda, coin dususunu ekonomi tarafinda yapabilsin.
 */
fun useBooster(
    state: BoosterState,
    type: BoosterType,
    viaAd: Boolean,
    decision: BoosterDecision,
    nowMs: Long = Long.MAX_VALUE / 2,
    repairedHealth: Int = 0,
): BoosterState {
    if (decision !is BoosterDecision.Allowed) return state
    return state.copy(
        paidUses = if (viaAd) state.paidUses else state.paidUses + (type to state.paidUsesOf(type) + 1),
        adUses = if (viaAd) state.adUses + (type to state.adUsesOf(type) + 1) else state.adUses,
        adViewsToday = if (viaAd) state.adViewsToday + 1 else state.adViewsToday,
        repairedHealth = state.repairedHealth +
            if (type == BoosterType.BASE_REPAIR) repairedHealth.coerceAtLeast(0) else 0,
        lastUseMs = state.lastUseMs + (type to nowMs),
    )
}

/** Coin fiyatli bir guclendiricinin bedelini cuzdandan duser. Izin yoksa cuzdan degismez. */
fun payForBooster(wallet: PlayerWallet, decision: BoosterDecision): PlayerWallet {
    if (decision !is BoosterDecision.Allowed) return wallet
    if (decision.currency != BoosterCurrency.COIN || decision.price <= 0) return wallet
    return wallet.debited(decision.price)
}

/**
 * Bir guclendirici denemesinin motora donen tam sonucu.
 *
 * Motor yalnizca bu nesneye bakar: [decision] izin vermiyorsa hicbir alan sifir
 * degildir diye varsaymaz, [applied] bayragina bakar.
 *
 * @param supplyGranted savas ici Tedarige EKLENECEK miktar (Acil Tedarik).
 * @param supplyCharged savas ici Tedarikten DUSULECEK miktar (Hava Destegi).
 * @param healthRestored us canina eklenecek can (Us Tamiri).
 * @param airSupportDamageFraction ekrandaki her dusmandan silinecek maks can orani.
 * @param coinsSpent cuzdandan dusulen coin (ekonomi katmani zaten dusmustur).
 */
data class BoosterActivation(
    val type: BoosterType,
    val decision: BoosterDecision,
    val viaAd: Boolean,
    val supplyGranted: Int = 0,
    val supplyCharged: Int = 0,
    val healthRestored: Int = 0,
    val airSupportDamageFraction: Double = 0.0,
    val coinsSpent: Int = 0,
) {
    val applied: Boolean get() = decision.isAllowed
}

// =====================================================================================
// Yildiz notrlugu — Us Tamiri arbitrajinin kapatilmasi
// =====================================================================================

/**
 * Yildiz hesabina girecek "etkin can".
 *
 * SORUN: yildiz kalan us canina bakiyor ([starsFor]) ve yildiz coin oduyor. Us Tamiri
 * cani geri verdigi icin, tamir ucreti < yildiz atlamanin coin degeri oldugu her
 * durumda "tamir et, yildiz atla, kar et" arbitraji dogar. L22'de 2 -> 3 yildiz farki
 * 230 coin; tamir 435 coin oldugundan tek atlamada karli degil, ama Kusursuz Savunma
 * madalyasi ve cift atlamalar hesabi bozabilir.
 *
 * COZUM: tamir edilen can yildiz hesabindan DUSULUR. Boylece yildiz her zaman savas
 * boyunca gorulen en kotu duruma gore verilir; tamir **hayatta kalma** satin alir,
 * yildiz ve coin ASLA. Arbitraj fiyatlamaya bagli olmaktan cikar, yapisal olarak yok
 * olur.
 *
 * MOTOR DEVRI: zafer aninda `starsFor(effectiveStarHealth(livesLeft, boosterState),
 * maxLives)` cagrilmali. Bkz. ECONOMY_SPEC 9.
 */
fun effectiveStarHealth(finalHealth: Int, repairedHealth: Int): Int =
    (finalHealth - repairedHealth.coerceAtLeast(0)).coerceAtLeast(0)

fun effectiveStarHealth(finalHealth: Int, state: BoosterState): Int =
    effectiveStarHealth(finalHealth, state.repairedHealth)

// =====================================================================================
// Ekonomi denetim yardimcilari (testler ve ECONOMY_SPEC tablolari bunlari kullanir)
// =====================================================================================

/**
 * Bir bolumde guclendiricilerin oynanisa katabilecegi **en fazla** Tedarik.
 * Hava Destegi Tedarik HARCAR, bu yuzden yalnizca Acil Tedarik sayilir.
 */
fun maxBoosterSupplyInjection(level: Int): Int =
    if (boosterUnlocked(BoosterType.EMERGENCY_SUPPLY, level)) {
        emergencySupplyAmount(level) * BoosterType.EMERGENCY_SUPPLY.adUsesPerBattle
    } else {
        0
    }

/**
 * Bir bolumde guclendiricilere harcanabilecek **en fazla** coin.
 * Gunluk coin cikisinin ust sinirini olcmek icin ECONOMY_SPEC B tablosunda kullanilir.
 */
fun maxBoosterCoinSpend(level: Int): Int =
    if (boosterUnlocked(BoosterType.BASE_REPAIR, level)) {
        boosterPrice(BoosterType.BASE_REPAIR, level) * BoosterType.BASE_REPAIR.paidUsesPerBattle
    } else {
        0
    }
