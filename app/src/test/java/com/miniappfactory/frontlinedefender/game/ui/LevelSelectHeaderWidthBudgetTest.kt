package com.miniappfactory.frontlinedefender.game.ui

import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BOLUM SECIM EKRANI BASLIK SATIRI — GENISLIK BUTCESI.
 *
 * ## Neden bu test var
 * Coin cipine odullu reklam rozeti eklendi (R1b / `RewardedPlacement.COIN_TOP_UP`)
 * ve rozet baslik satirindan **28 dp** yer aldi
 * (`CoinChipAdEntryTest.theBadgeCostsExactlyTheReservedTwentyEightDp`). Satirda
 * o 28 dp'nin gercekten bos oldugunu kanitlamak gerekiyor.
 *
 * ## Neden Compose testi bunu yapamiyor
 * Robolectric gercek font metrigi calistirmaz: `Paint.measureText` sahte deger
 * doner ve olculen "COIN" etiketi 4 dp cikar. Yani bir Compose bounds testi
 * metin tasmasi konusunda **hicbir sey kanitlamaz** — gecer ve yanlis guven
 * verir. Bu depoda tasma hatasi dort kez cikti; her seferinde "testler yesildi".
 *
 * Cozum `BoosterChipStringsTest`in deseni: genisligi karakter sayisindan
 * turetmek. Ustelik bu yolun bir avantaji var — **her iki dil dosyasini da**
 * okur, yani Turkce ceviri uzadiginda da kirilir. Robolectric testi tek bir
 * locale'de kosar.
 *
 * ## Butcenin turetilisi (740x360 dp — Galaxy S8 yatay, test cihazi)
 *   ekran genisligi          740 dp
 *   satir yatay dolgusu    -  32 dp (16 + 16)
 *   KULLANILABILIR         = 708 dp
 *
 * Satirdaki ogeler (soldan saga), her biri metin + kendi ic dolgusu:
 *   "< GERI"      13 sp kalin   + 20 dp dolgu
 *   aralik                        14 dp
 *   "KAMPANYA"    17 sp black   +  0 dp
 *   esnek bosluk                  (weight 1f — butcede 0 sayilir)
 *   "GOREVLER"    12 sp x-kalin + 24 dp dolgu + 23 dp rozet (alinabilir gorev varken)
 *   aralik                         8 dp
 *   "CEPHANELIK"  12 sp x-kalin + 24 dp dolgu
 *   aralik                         8 dp
 *   COIN cipi     11 sp + 15 sp + 24 dp dolgu + 8 dp ic aralik
 *   COIN rozeti                   28 dp (20 dp daire + 8 dp aralik)  <-- YENI
 *
 * Kalin Latin metinde ortalama karakter genisligi punto basina yaklasik
 * [DP_PER_SP_PER_CHAR] dp'dir (`BoosterChipStringsTest` ile ayni tahmin
 * ailesi; orada 9 sp -> ~5,5 dp, yani 0,61). Turkce buyuk harfler (Ğ, Ş, İ)
 * Latin genisliginden dar veya esittir, bu yuzden tahmin **iyimser degil**.
 */
class LevelSelectHeaderWidthBudgetTest {

    private companion object {
        val STRING_ENTRY = Regex(
            "<string name=\"([^\"]+)\"[^>]*>(.*?)</string>",
            RegexOption.DOT_MATCHES_ALL
        )

        val STRING_FILES = listOf(
            "src/main/res/values/strings.xml",
            "src/main/res/values-tr/strings.xml",
            "src/main/res/values/strings_missions.xml",
            "src/main/res/values-tr/strings_missions.xml",
        )

        /** Galaxy S8 yatay. */
        const val SCREEN_WIDTH_DP = 740f
        const val ROW_HORIZONTAL_PADDING_DP = 32f

        /** Kalin metinde 1 karakterin 1 sp punto basina dp genisligi. */
        const val DP_PER_SP_PER_CHAR = 0.61f

        /** Coin rozetinin olculen bedeli — `CoinChipAdEntryTest` kilitliyor. */
        const val COIN_BADGE_DP = 28f

        /**
         * Bakiyenin gosterebilecegi en buyuk basamak sayisi UYDURULMUYOR:
         * kampanyanin uretebilecegi en yuksek gelir (3 yildiz bandi, 88.000)
         * ustune gorev/reklam/basarim gelirleri binse bile 7 hane fazlasiyla
         * yeterli. Yine de en kotu durumu sabitliyoruz.
         */
        const val MAX_COIN_DIGITS = 7
    }

    private fun readUp(relative: String): String? =
        listOf(File(relative), File("app/$relative"), File("../app/$relative"), File("../$relative"))
            .firstOrNull { it.isFile }
            ?.readText()

    private val strings: Map<String, Map<String, String>> by lazy {
        STRING_FILES.mapNotNull { path ->
            readUp(path)?.let { text ->
                path to STRING_ENTRY.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
            }
        }.toMap()
    }

    /** Bir anahtarin TUM dillerdeki en uzun karsiligi. */
    private fun longest(key: String): String {
        val all = strings.values.mapNotNull { it[key] }
        assertTrue("string kaynagi bulunamadi: $key (okunan dosyalar: ${strings.keys})", all.isNotEmpty())
        return all.maxBy { it.length }
    }

    private fun textDp(key: String, sp: Float): Float =
        longest(key).length * sp * DP_PER_SP_PER_CHAR

    @Test
    fun theHeaderRowFitsOnTheNarrowLandscapeDeviceWithTheAdBadgeDrawn() {
        val available = SCREEN_WIDTH_DP - ROW_HORIZONTAL_PADDING_DP

        val back = textDp("level_back", 13f) + 20f
        val gapAfterBack = 14f
        val title = textDp("level_campaign_title", 17f)
        val missions = textDp("mission_open", 12f) + 24f + 23f // rozet dahil (en kotu)
        val gap1 = 8f
        val armory = textDp("shop_open", 12f) + 24f
        val gap2 = 8f
        val coinLabel = textDp("level_coin_label", 11f)
        val coinAmount = MAX_COIN_DIGITS * 15f * DP_PER_SP_PER_CHAR
        val coinChip = coinLabel + 8f + coinAmount + 24f + COIN_BADGE_DP

        val used = back + gapAfterBack + title + missions + gap1 + armory + gap2 + coinChip

        assertTrue(
            "baslik satiri 740 dp yatayda tasiyor: kullanilan ${"%.1f".format(used)} dp / " +
                "$available dp (geri $back, baslik $title, gorevler $missions, " +
                "cephanelik $armory, coin cipi $coinChip)",
            used <= available,
        )

        // Esnek bosluk gercekten KALMALI: satir "tam sigiyor" degil, RAHAT
        // sigmali. Aksi halde bir sonraki ceviri veya bir sonraki cip
        // kacinilmaz olarak tasirir. Pay en az rozetin iki kati olsun.
        val slack = available - used
        assertTrue(
            "baslik satirinda yalnizca ${"%.1f".format(slack)} dp pay kaldi — " +
                "bir sonraki ceviri tasirir",
            slack >= 2 * COIN_BADGE_DP,
        )
    }

    @Test
    fun theAdBadgeStringIsASingleUntranslatedCharacter() {
        // Rozet cevrilebilir olursa uzun bir ceviri butceyi patlatir ve bu
        // hesabin girdisi sessizce yanlislasir.
        val en = strings["src/main/res/values/strings.xml"]?.get("level_coin_topup_badge")
        assertNotNull("level_coin_topup_badge tanimli olmali", en)
        assertTrue("rozet tek karakter olmali, bulundu: '$en'", en!!.length == 1)

        val tr = strings["src/main/res/values-tr/strings.xml"]?.get("level_coin_topup_badge")
        assertTrue(
            "rozet translatable=false olmali; values-tr icinde bir karsiligi var: '$tr'",
            tr == null,
        )
    }

    @Test
    fun theOfferSheetTextsAreSharedWithTheSupplyBarSoTheyStayInSync() {
        // Coin cipi ve serit AYNI teklif metinlerini kullanir. Ayri metinler
        // olsaydi biri "150 coin" digeri "200 coin" diyebilir ve ekonominin tek
        // sayisi ile ekranda yazan sayi ayrisirdi — bu depoda `applySupplyDrop`
        // KDoc'unda belgelenmis bir hata.
        val body = longest("ad_sheet_supply_body")
        assertTrue(
            "teklif metni odul miktarini bicimlendirici ile almali, gomulu sayiyla degil",
            body.contains("%1\$d") && body.contains("%2\$d"),
        )
        assertTrue(
            "teklif metnindeki dolu odul argumani ekonomiden gelmeli",
            EconomyConfig.R1_REWARD_FILLED > 0 && EconomyConfig.R1_REWARD_FALLBACK > 0,
        )
    }
}
