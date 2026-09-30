package com.miniappfactory.frontlinedefender.game.economy

/**
 * Faz 10 — GUCLENDIRICILER (savas ici, coin fiyatli).
 *
 * **Android importu YOK.** Saf veri + saf fonksiyonlar; JVM'de Robolectric olmadan
 * kosar. Tum sayilar [EconomyConfig]'ten gelir.
 *
 * ---------------------------------------------------------------------------------
 * 1. TEK ODEME YOLU: COIN (2026-09-30)
 * ---------------------------------------------------------------------------------
 * Oyunda reklam YOKTUR. Eskiden reklamla alinan haklar (Acil Tedarik, Hava Destegi'nin
 * birincil yolu, Us Tamiri'nin 2. kullanimi) artik coinle alinir; hepsi ayni kuralla:
 *
 *  - bedel eylemden ONCE gosterilir (BoosterRail'in "N COIN" cipi),
 *  - reddedilen kullanim HICBIR seyi degistirmez (cuzdan, sayac, bekleme),
 *  - bedel Konuslanma Rezervi'ne tabidir (rezerv coini guclendiriciye harcanamaz),
 *  - savas basina kullanim tavani ve bekleme suresi korunur.
 *
 * Bu, GDD/DECISIONS D.4'un ("coin -> Tedarik donusumu YASAK") Acil Tedarik icin BILINCLI
 * GERI ALINMASIDIR (kullanici karari, 2026-09-30: "tum eski reklam haklari coinle
 * fiyatlanacak, acil tedarik dahil"). Donusum sabit ve seffaftir:
 * [EconomyConfig.EMERGENCY_SUPPLY_COIN_PER_SUPPLY] coin = 1 Tedarik, savas basina 1 kez,
 * miktar en fazla bolum butcesinin ~%5,7-11'i.
 *
 * ---------------------------------------------------------------------------------
 * 2. PARA BIRIMI VE FIYATLAR
 * ---------------------------------------------------------------------------------
 * - [BoosterType.AIR_SUPPORT]: coin, savas basina [EconomyConfig.AIR_SUPPORT_USES_PER_BATTLE]
 *   kullanim, iki kullanim ayni fiyat. Hava destegi Tedarik HARCAMAZ. Pay-to-win kalkani:
 *   `maxUsesPerBattle x AIR_SUPPORT_DAMAGE_FRACTION < 1,0` (bkz. EconomyConfig).
 * - [BoosterType.BASE_REPAIR]: coin, 2 kullanim. 1. kullanim [EconomyConfig.BASE_REPAIR_LIVES_FIRST_USE]
 *   can; 2. kullanim [EconomyConfig.BASE_REPAIR_LIVES_SECOND_USE] can ve CAN BASINA AYNI FIYAT
 *   ([boosterPrice]) — eskiden reklamla bedava olan 7 canlik hak artik acikca fiyatli.
 *   Yildiz notrdur ([effectiveStarHealth]): tamir hayatta kalma satin alir, yildiz/coin asla.
 * - [BoosterType.EMERGENCY_SUPPLY]: coin, 1 kullanim; fiyat = verdigi Tedarik x
 *   [EconomyConfig.EMERGENCY_SUPPLY_COIN_PER_SUPPLY]. Eski "fiyat >= en ucuz kalici rank (200)"
 *   tabani bu kalem icin UYGULANMAZ: L2-L3'te R(L,1)=170/200 oldugundan "tabandan buyuk ve
 *   R(L,1)'den kucuk" araligi bos.
 *
 * ---------------------------------------------------------------------------------
 * 3. NEDEN ENVANTER DEGIL
 * ---------------------------------------------------------------------------------
 * Guclendiriciler **savasa kapsamlidir**: satin alinir, o savasta kullanilir, savas bitince
 * kaybolur. Stoklanabilir envanter yeni kayit alani, "20 tane biriktir" farming'i ve
 * uygulamayi yeniden baslatarak sayac sifirlama exploit'i getirirdi.
 *
 * ---------------------------------------------------------------------------------
 * 4. PAY-TO-WIN YOK
 * ---------------------------------------------------------------------------------
 * - Her bolum **sifir guclendiriciyle** gecilebilir olmali; guclendiriciler zorluk hesabina
 *   GIRMEZ.
 * - Hava destegi tam canli hicbir dusmani tek basina oldurmez.
 * - [BoosterType.BASE_REPAIR] yildiz notrdur, bkz. [effectiveStarHealth].
 */

// =====================================================================================
// Tipler
// =====================================================================================

/** Bir guclendiricinin odeme yolunun hangi para biriminde oldugu. */
enum class BoosterCurrency {
    /** Savas ici Tedarik (bolum sonu sifirlanir). Bugun hicbir guclendirici bunu kullanmiyor. */
    SUPPLY,

    /** Meta Coin (kalici). Konuslanma Rezervi'ne tabidir. */
    COIN,
}

/**
 * Savas ici guclendiriciler.
 *
 * @param unlockLevel bu guclendiricinin **oynanan bolum** bu degere ulastiginda gorunur.
 * @param currency odeme yolunun para birimi.
 * @param paidUsesPerBattle savas basina en fazla kac kullanim.
 */
enum class BoosterType(
    val unlockLevel: Int,
    val currency: BoosterCurrency,
    val paidUsesPerBattle: Int,
) {
    /** **Acil Tedarik** — aninda Tedarik enjeksiyonu. */
    EMERGENCY_SUPPLY(
        unlockLevel = EconomyConfig.EMERGENCY_SUPPLY_UNLOCK_LEVEL,
        currency = BoosterCurrency.COIN,
        paidUsesPerBattle = EconomyConfig.EMERGENCY_SUPPLY_USES_PER_BATTLE,
    ),

    /** **Hava Destegi** — ekrandaki tum dusmanlara agir hasar, uzun bekleme. */
    AIR_SUPPORT(
        unlockLevel = EconomyConfig.AIR_SUPPORT_UNLOCK_LEVEL,
        currency = BoosterCurrency.COIN,
        paidUsesPerBattle = EconomyConfig.AIR_SUPPORT_USES_PER_BATTLE,
    ),

    /** **Us Tamiri** — kaybedilen us caninin bir kismini geri verir; yildiz notrdur. */
    BASE_REPAIR(
        unlockLevel = EconomyConfig.BASE_REPAIR_UNLOCK_LEVEL,
        currency = BoosterCurrency.COIN,
        paidUsesPerBattle = EconomyConfig.BASE_REPAIR_USES_PER_BATTLE,
    ),
    ;

    /** Savas basina toplam kullanim tavani. */
    val maxUsesPerBattle: Int get() = paidUsesPerBattle

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
 * savas bitince atilir.
 *
 * @param level oynanan bolum. Fiyat ve etki bundan turetilir.
 * @param paidUses guclendirici -> bu savasta yapilan kullanim sayisi.
 * @param repairedHealth [BoosterType.BASE_REPAIR] ile geri verilen toplam us cani.
 *   Yildiz hesabindan DUSULUR ([effectiveStarHealth]) — tamir yildiz satin almaz.
 * @param lastUseMs guclendirici -> son kullanim ani (motor saati). Bekleme kontrolu.
 */
data class BoosterState(
    val level: Int,
    val paidUses: Map<BoosterType, Int> = emptyMap(),
    val repairedHealth: Int = 0,
    val lastUseMs: Map<BoosterType, Long> = emptyMap(),
) {
    init {
        require(level >= 1) { "level >= 1 olmali: $level" }
        require(repairedHealth >= 0) { "negatif tamir: $repairedHealth" }
        BoosterType.entries.forEach { type ->
            val used = paidUses[type] ?: 0
            require(used in 0..type.paidUsesPerBattle) { "$type kullanim $used aralik disi" }
        }
    }

    fun paidUsesOf(type: BoosterType): Int = paidUses[type] ?: 0
    fun usesOf(type: BoosterType): Int = paidUsesOf(type)

    companion object {
        /** Yeni savas: tum kullanim sayaclari sifirlanir. */
        fun startBattle(level: Int): BoosterState = BoosterState(level = level)
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
     * @param price odenecek miktar.
     * @param currency hangi para biriminden dusulecek.
     */
    data class Allowed(
        val price: Int,
        val currency: BoosterCurrency,
    ) : BoosterDecision()

    /** Bu build'de kapali (kill switch). */
    data object Disabled : BoosterDecision()

    /** Bolum henuz bu guclendiriciyi acmadi. */
    data class Locked(val requiredLevel: Int) : BoosterDecision()

    /** Kullanim hakki bu savasta tukendi. */
    data object PaidLimitReached : BoosterDecision()

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
 * Bu bolumde bu guclendiricinin [useNumber]. kullaniminin coin fiyati (1'den baslar).
 *
 * Fiyat bolumle dogrusal buyur ki gec oyunda "anlamsiz derecede ucuz" olmasin.
 * Us Tamiri'nin 2. kullanimi CAN BASINA 1. kullanimla ayni fiyattir
 * (`round10(fiyat1 x can2 / can1)`): eski reklam hakki bedavaydi, artik indirimsiz fiyatli.
 */
fun boosterPrice(type: BoosterType, level: Int, useNumber: Int = 1): Int {
    require(level in 1..EconomyConfig.CAMPAIGN_LEVELS) { "level $level aralik disi" }
    require(useNumber in 1..type.paidUsesPerBattle) { "$type icin useNumber $useNumber aralik disi" }
    return when (type) {
        BoosterType.EMERGENCY_SUPPLY ->
            emergencySupplyAmount(level) * EconomyConfig.EMERGENCY_SUPPLY_COIN_PER_SUPPLY
        BoosterType.AIR_SUPPORT ->
            EconomyConfig.AIR_SUPPORT_COIN_BASE + EconomyConfig.AIR_SUPPORT_COIN_STEP * (level - 1)
        BoosterType.BASE_REPAIR -> {
            val first = EconomyConfig.BASE_REPAIR_COIN_BASE + EconomyConfig.BASE_REPAIR_COIN_STEP * (level - 1)
            if (useNumber == 1) {
                first
            } else {
                roundToTen(
                    first.toDouble() * EconomyConfig.BASE_REPAIR_LIVES_SECOND_USE /
                        EconomyConfig.BASE_REPAIR_LIVES_FIRST_USE
                )
            }
        }
    }
}

/**
 * Acil Tedarik'in verdigi Tedarik miktari.
 *
 * SERT KISIT (ECONOMY_SPEC A.4): bu miktar, sikilastirilmis bolum butcesine
 * eklendiginde eski (fazla bol) butceyi **asmamalidir**.
 */
fun emergencySupplyAmount(level: Int): Int {
    require(level in 1..EconomyConfig.CAMPAIGN_LEVELS) { "level $level aralik disi" }
    return roundToTen(
        (EconomyConfig.EMERGENCY_SUPPLY_BASE +
            EconomyConfig.EMERGENCY_SUPPLY_STEP * (level - 1)).toDouble()
    )
}

/**
 * Us Tamiri'nin [useNumber]. kullanimda geri verecegi can. Kaybedilmis candan fazlasini
 * vermez. Us cani zaten tamsa 0 doner ve [boosterAllowed] [BoosterDecision.NoEffect] verir.
 */
fun baseRepairAmount(currentHealth: Int, maxHealth: Int, useNumber: Int = 1): Int {
    require(maxHealth > 0) { "maxHealth > 0 olmali" }
    val lost = (maxHealth - currentHealth).coerceAtLeast(0)
    if (lost == 0) return 0
    val nominal =
        if (useNumber >= 2) EconomyConfig.BASE_REPAIR_LIVES_SECOND_USE else EconomyConfig.BASE_REPAIR_LIVES_FIRST_USE
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
 * kill switch -> bolum kilidi -> kullanim tavani -> bekleme -> etki -> odeme gucu -> rezerv.
 *
 * "ETKI" adimi guclendiricinin **hicbir sey yapmayacagi** durumlari yakalar ve
 * [BoosterDecision.NoEffect] doner. Bu adim odeme gucu kontrolunden ONCE gelir:
 * etkisiz bir kullanim ucret kesmeden, kullanim hakki yakmadan ve bekleme
 * baslatmadan reddedilmelidir. Iki durum vardir:
 *   - [BoosterType.BASE_REPAIR] ve us cani zaten tam,
 *   - [BoosterType.AIR_SUPPORT] ve sahada hedef yok ([enemiesOnField] == 0).
 *
 * @param supplyOnHand savas ici mevcut Tedarik (yalnizca SUPPLY fiyatli tipler icin).
 * @param baseHealth / [maxBaseHealth] yalnizca [BoosterType.BASE_REPAIR] icin anlamli.
 * @param enemiesOnField su an sahada bulunan dusman sayisi; yalnizca
 *   [BoosterType.AIR_SUPPORT] icin anlamli. [ENEMY_COUNT_UNKNOWN] (varsayilan) =
 *   cagiran sahayi bildirmiyor, hedef kontrolu atlanir.
 */
fun boosterAllowed(
    state: BoosterState,
    type: BoosterType,
    wallet: PlayerWallet,
    supplyOnHand: Int = 0,
    baseHealth: Int = 0,
    maxBaseHealth: Int = EconomyConfig.BASE_MAX_HEALTH,
    enemiesOnField: Int = ENEMY_COUNT_UNKNOWN,
    nowMs: Long = Long.MAX_VALUE / 2,
): BoosterDecision {
    if (!type.enabled) return BoosterDecision.Disabled
    if (state.level < type.unlockLevel) return BoosterDecision.Locked(type.unlockLevel)

    val usedSoFar = state.paidUsesOf(type)
    if (usedSoFar >= type.paidUsesPerBattle) return BoosterDecision.PaidLimitReached

    val cooldown = boosterCooldownMs(type)
    val last = state.lastUseMs[type]
    if (last != null && cooldown > 0L && nowMs - last < cooldown) {
        return BoosterDecision.Cooldown(cooldown - (nowMs - last))
    }

    // ETKI. Ucret kesilmeden, kullanim hakki yakilmadan ve bekleme baslatilmadan
    // once "bu kullanim hicbir sey yapmayacak" durumlari elenir. Kapi ekonomi
    // katmanindadir (UI'da degil): yeni bir cagiran eklendiginde sessizce atlanmaz.
    if (type == BoosterType.BASE_REPAIR && baseRepairAmount(baseHealth, maxBaseHealth) == 0) {
        return BoosterDecision.NoEffect
    }
    if (type == BoosterType.AIR_SUPPORT && enemiesOnField == 0) {
        return BoosterDecision.NoEffect
    }

    val price = boosterPrice(type, state.level, useNumber = usedSoFar + 1)
    if (price == 0) return BoosterDecision.Allowed(0, type.currency)

    return when (type.currency) {
        BoosterCurrency.SUPPLY -> {
            if (supplyOnHand < price) {
                BoosterDecision.InsufficientSupply(price, price - supplyOnHand)
            } else {
                BoosterDecision.Allowed(price, BoosterCurrency.SUPPLY)
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
                    BoosterDecision.Allowed(price, BoosterCurrency.COIN)
                }
            }
        }
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
    decision: BoosterDecision,
    nowMs: Long = Long.MAX_VALUE / 2,
    repairedHealth: Int = 0,
): BoosterState {
    if (decision !is BoosterDecision.Allowed) return state
    return state.copy(
        paidUses = state.paidUses + (type to state.paidUsesOf(type) + 1),
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
 * @param supplyCharged savas ici Tedarikten DUSULECEK miktar.
 * @param healthRestored us canina eklenecek can (Us Tamiri).
 * @param airSupportDamageFraction ekrandaki her dusmandan silinecek maks can orani.
 * @param coinsSpent cuzdandan dusulen coin (ekonomi katmani zaten dusmustur).
 */
data class BoosterActivation(
    val type: BoosterType,
    val decision: BoosterDecision,
    val supplyGranted: Int = 0,
    val supplyCharged: Int = 0,
    val healthRestored: Int = 0,
    val airSupportDamageFraction: Double = 0.0,
    val coinsSpent: Int = 0,
) {
    val applied: Boolean get() = decision.isAllowed
}

// =====================================================================================
// Yildiz notrlugu — Us Tamiri / Devam arbitrajinin kapatilmasi
// =====================================================================================

/**
 * Yildiz hesabina girecek "etkin can".
 *
 * SORUN: yildiz kalan us canina bakiyor ([starsFor]) ve yildiz coin oduyor. Us Tamiri ve
 * Devam cani geri verdigi icin, ucret < yildiz atlamanin coin degeri oldugu her
 * durumda "tamir et, yildiz atla, kar et" arbitraji dogar.
 *
 * COZUM: geri verilen can yildiz hesabindan DUSULUR. Boylece yildiz her zaman savas
 * boyunca gorulen en kotu duruma gore verilir; tamir/devam **hayatta kalma** satin alir,
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
 * Bir bolumde guclendiricilerin oynanisa katabilecegi **en fazla** Tedarik. Yalnizca
 * Acil Tedarik Tedarik ekler (Hava Destegi Tedarik harcamaz).
 */
fun maxBoosterSupplyInjection(level: Int): Int =
    if (boosterUnlocked(BoosterType.EMERGENCY_SUPPLY, level)) {
        emergencySupplyAmount(level) * BoosterType.EMERGENCY_SUPPLY.paidUsesPerBattle
    } else {
        0
    }

/**
 * Bir bolumde guclendiricilere harcanabilecek **en fazla** coin (her kullanim kendi
 * fiyatiyla, [boosterPrice]). Coin para birimli TUM guclendiriciler taranir; elle liste
 * yok, boylece yeni bir coin kalemi eklendiginde tavan kendiliginden dogru kalir.
 *
 * Bu, TEKRARLAYAN (savas basina) harcama tavanidir; TEK SEFERLIK cikislar
 * (kilitler, agac, prestij) `StoreCatalog.oneTimeSinkTotal` icindedir ve ikisi
 * ayri raporlanir.
 */
fun maxBoosterCoinSpend(level: Int): Int =
    BoosterType.entries
        .filter { it.currency == BoosterCurrency.COIN && boosterUnlocked(it, level) }
        .sumOf { type -> (1..type.paidUsesPerBattle).sumOf { use -> boosterPrice(type, level, use) } }
