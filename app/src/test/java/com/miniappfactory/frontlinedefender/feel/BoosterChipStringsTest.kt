package com.miniappfactory.frontlinedefender.feel

import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * GUCLENDIRICI CIPI — OKUNABILIRLIK VE TASMA SOZLESMESI.
 *
 * ## Neden bu test var
 * Cihazda kullanici sag kenardaki gucledirici rayini gosterip *"bu sayac ne
 * yapiyor anlamadim"* dedi. Bekleme durumunda cip ciplak bir sayi gosteriyordu
 * ("34 sn"). Ayni cip yuvasinda Tedarik FIYATI da ciplak bir sayi olarak
 * cikiyor (`booster_chip_price_supply` = `%1$d`), yani "34" ile "125" ayirt
 * edilemiyordu: biri "simdi odeyebilirsin", digeri "odeyemezsin, bekle".
 *
 * ## Kilitlenen iki sey
 * 1. **Bekleme cipi bir FIIL tasir** ve ciplak sayi cipiyle yapisal olarak
 *    ayrisir — hem Ingilizce hem Turkce.
 * 2. **Cipe SIGAR.** Cip ic genisligi 44 dp ve `AutoShrinkText` 9 -> 7 sp
 *    arasinda kucultur; bu araligin altina inilirse metin KIRPILIR.
 *
 * ## Genislik butcesinin turetilisi (BoosterRail.BoosterButton)
 *   buton      : 48 dp (dar bant) — genis bantta 56 dp, yani dar bant en kotu durum
 *   cip        : butonun tam genisligi, iki yandan ~2 dp gorsel pay -> ~44 dp
 *   punto      : 9 sp kalin, tasarsa 0,5 sp adimlarla 7 sp'ye kadar iner
 * Kalin Latin metinde ortalama karakter genisligi 9 sp'de ~5,5 dp, 7 sp'de
 * ~4,3 dp. Bugunku en uzun aday "540 COIN" (8 karakter) 9 sp'de zaten payla
 * siginiyor. [CHIP_BUDGET_CHARS] = 10:
 *   9 sp'de 55 dp (sigmaz) -> AutoShrinkText devreye girer
 *   7 sp'de 43 dp (sigar)  -> kirpma OLUSAMAZ
 * Yani butce "tam puntoda sigar" degil, **hicbir dilde kirpilmaz** garantisi.
 */
class BoosterChipStringsTest {

    private companion object {
        val STRING_ENTRY = Regex(
            "<string name=\"([^\"]+)\"[^>]*>(.*?)</string>",
            RegexOption.DOT_MATCHES_ALL
        )

        const val EN_MAIN = "src/main/res/values/strings.xml"
        const val TR_MAIN = "src/main/res/values-tr/strings.xml"

        /** Turetilisi sinif KDoc'unda. */
        const val CHIP_BUDGET_CHARS = 10

        /**
         * Sayacin GERCEKTEN gosterebilecegi en buyuk deger — uydurulmuyor,
         * ekonomi sabitlerinden turetiliyor. Bugun 60 sn (Us Tamiri); bir
         * bekleme suresi buyutulurse bu sayi kendiliginden buyur ve
         * [everyChipLineFitsTheFixedWidthBudget] tasmayi ANINDA yakalar.
         */
        val SECONDS_SAMPLE: String = maxOf(
            EconomyConfig.AIR_SUPPORT_COOLDOWN_MS,
            EconomyConfig.BASE_REPAIR_COOLDOWN_MS,
            EconomyConfig.EMERGENCY_SUPPLY_COOLDOWN_MS
        ).let { ((it + 999) / 1000).toString() }

        /** Ileriye donuk pay: bekleme suresi uc haneye cikarsa da kirpma olmamali. */
        const val SECONDS_SAMPLE_FUTURE = "999"

        /** Cip yuvasini paylasan TUM anahtarlar — hepsi ayni 44 dp'ye sigmali. */
        val CHIP_KEYS = listOf(
            "booster_chip_ad",
            "booster_chip_cooldown",
            "booster_chip_confirm",
            "booster_chip_spent",
            "booster_chip_reserve",
            // NoEffect ayristirildi (cihaz raporu: "hava destegi butonu gelmiyor").
            // Ayni yuvayi paylastiklari icin genislik butcesi bunlara da isler.
            "booster_chip_no_target",
            "booster_chip_base_full",
            // Kilitli kutucuk artik ciziliyor; ayni yuvayi paylasiyor.
            "booster_chip_locked"
        )

        /**
         * Ciplak sayi gosteren cipler. Bekleme cipi bunlardan AYRISMAK
         * zorunda, cunku kok sebep tam olarak buydu.
         */
        const val PRICE_KEY = "booster_chip_price_supply"
        const val COOLDOWN_KEY = "booster_chip_cooldown"

        val FORMAT_ARG = Regex("%(\\d+)\\\$([sdf])")
    }

    /** Gradle birim testi modul kokunden kosar; CI/IDE farki icin birkac yol denenir. */
    private fun resFile(relative: String): File =
        listOf(File(relative), File("app/$relative"), File("../app/$relative"))
            .firstOrNull { it.isFile }
            ?: error("kaynak dosyasi bulunamadi: $relative (cwd=${File(".").absolutePath})")

    private fun readStrings(relative: String): Map<String, String> =
        STRING_ENTRY.findAll(resFile(relative).readText(Charsets.UTF_8))
            .associate { it.groupValues[1] to it.groupValues[2] }

    private val english by lazy { readStrings(EN_MAIN) }
    private val turkish by lazy { readStrings(TR_MAIN) }

    /** Sayi yuvasi doldurulmus, ekranda GERCEKTEN gorunecek hali. */
    private fun rendered(template: String): String =
        FORMAT_ARG.replace(template) { SECONDS_SAMPLE }

    // =========================================================================
    // 1. IKI DILDE DE VAR — eksik ceviri lint'te HATA
    // =========================================================================

    @Test
    fun everyChipKeyExistsInBothLocales() {
        CHIP_KEYS.forEach { key ->
            assertNotNull("$key Ingilizce strings.xml'de yok", english[key])
            assertNotNull("$key Turkce strings.xml'de yok", turkish[key])
        }
    }

    // =========================================================================
    // 2. BEKLEME CIPI CIPLAK SAYI DEGIL — kullanicinin bildirdigi kok sebep
    // =========================================================================

    /**
     * Cip sablonu, sayi yuvasi disinda EN AZ bir harf tasimali. Ciplak sayi
     * ("34") oyuncuya hicbir sey anlatmiyordu.
     */
    @Test
    fun theCooldownChipCarriesAWordNotJustANumber() {
        listOf("en" to english, "tr" to turkish).forEach { (lang, strings) ->
            val template = strings.getValue(COOLDOWN_KEY)
            val withoutArgs = FORMAT_ARG.replace(template) { "" }.trim()
            assertTrue(
                "$lang / $COOLDOWN_KEY ciplak sayi olmamali (deger: '$template')",
                withoutArgs.any { it.isLetter() }
            )
        }
    }

    /**
     * Bekleme cipi ile FIYAT cipi ayni sablona sahip OLAMAZ. Ikisi de ciplak
     * sayi oldugu surece oyuncu "odenecek tutar" ile "kalan sure"yi ayirt
     * edemez — cihazda tam olarak bu oldu.
     */
    @Test
    fun theCooldownChipIsStructurallyDistinctFromThePriceChip() {
        val price = english.getValue(PRICE_KEY)
        listOf("en" to english, "tr" to turkish).forEach { (lang, strings) ->
            assertTrue(
                "$lang: bekleme cipi fiyat cipiyle ayni bicimde olmamali",
                strings.getValue(COOLDOWN_KEY) != price
            )
        }
    }

    // =========================================================================
    // 3. CIPE SIGAR — sabit genislikli yuzey, uzun TR metni klasik tuzak
    // =========================================================================

    @Test
    fun everyChipLineFitsTheFixedWidthBudget() {
        listOf("en" to english, "tr" to turkish).forEach { (lang, strings) ->
            CHIP_KEYS.forEach { key ->
                val line = rendered(strings.getValue(key))
                assertTrue(
                    "$lang / $key = '$line' (${line.length} karakter) cip butcesini " +
                        "($CHIP_BUDGET_CHARS) asiyor — 7 sp'de bile kirpilir",
                    line.length <= CHIP_BUDGET_CHARS
                )
            }
        }
    }

    /**
     * Ileriye donuk pay: bir bekleme suresi uc haneli saniyeye cikarsa da cip
     * kirpilmamali. Butce zaten bu ihtimali kapsayacak sekilde turetildi.
     */
    @Test
    fun aThreeDigitCountdownWouldStillFit() {
        listOf("en" to english, "tr" to turkish).forEach { (lang, strings) ->
            val line = FORMAT_ARG.replace(strings.getValue(COOLDOWN_KEY)) {
                SECONDS_SAMPLE_FUTURE
            }
            assertTrue(
                "$lang: uc haneli sayacta '$line' (${line.length}) butceyi asiyor",
                line.length <= CHIP_BUDGET_CHARS
            )
        }
    }

    /**
     * Bugunku en uzun cip adayi hala fiyat cipi olmali. Bekleme metni onu
     * gecerse butce turetilisi (sinif KDoc) yeniden yapilmali.
     */
    @Test
    fun theCooldownChipIsNotTheWidestCandidate() {
        val priceCoin = english.getValue("booster_chip_price_coin")
        val widestPrice = FORMAT_ARG.replace(priceCoin) { "540" }
        listOf("en" to english, "tr" to turkish).forEach { (lang, strings) ->
            val cooldown = rendered(strings.getValue(COOLDOWN_KEY))
            assertTrue(
                "$lang: bekleme cipi '$cooldown' bugunku en genis adaydan " +
                    "('$widestPrice') uzun; butce turetilisi guncellenmeli",
                cooldown.length <= widestPrice.length
            )
        }
    }

    // =========================================================================
    // 4. SAYAC ARGUMANI KORUNDU — metin degisirken sayi dusurulmemeli
    // =========================================================================

    @Test
    fun theCooldownChipStillShowsTheRemainingSeconds() {
        listOf("en" to english, "tr" to turkish).forEach { (lang, strings) ->
            val args = FORMAT_ARG.findAll(strings.getValue(COOLDOWN_KEY)).toList()
            assertEquals(
                "$lang: bekleme cipinde tam olarak bir tam sayi argumani olmali",
                1,
                args.size
            )
            assertEquals("$lang: arguman tam sayi (%1\$d) olmali", "d", args[0].groupValues[2])
        }
    }
}
