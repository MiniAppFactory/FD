package com.miniappfactory.frontlinedefender.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ILK OTURUM OGRETICISI — METIN BUTCESI VE TASMA KONTROLU.
 *
 * Bu ekipte tekrar eden hata kaynagi, sabit olculu bir yuzeye SIGMAYAN uzun
 * ceviri metnidir. Ogretici seridi tek satirlik ve baska bir dugumle
 * (GEC cipi) ayni satiri paylastigi icin risk yuksek; bu yuzden butce
 * KOD tarafinda kilitleniyor, gozle degil.
 *
 * XML dosyalari diskten okunuyor cunku duz JVM birim testi `R` kaynaklarini
 * goremez (Robolectric YOK). Okuma deterministik, ag/emulator gerektirmez.
 *
 * ---------------------------------------------------------------------------
 * BUTCENIN TURETILISI — serit yerlesimi (TutorialOverlay.kt)
 * ---------------------------------------------------------------------------
 *   satir      : HUD'un altinda, yatay 16 dp padding (iki yan)  -> W - 32 dp
 *   GEC cipi   : en az 48 dp, 18 dp x 2 yatay padding           -> ~86 dp
 *   arada      : 12 dp
 *   serit ici  : 14 dp x 2 padding                              -> 28 dp
 * En dar desteklenen yatay ekran 640 dp kabul edildi:
 *   640 - 32 - 86 - 12 - 28 = 482 dp metin genisligi.
 * 14 sp kalin Latin metinde ortalama karakter ~7,7 dp:
 *   482 / 7,7 ~= 62 karakter, TAM PUNTODA. Ustune `AutoShrinkText` 10 sp'ye
 *   kadar kucultuyor, yani kirpma hicbir dilde olusamaz.
 *
 * Butce bilincli olarak 34 karakter: gercek sinirin yaklasik yarisi. Amac
 * "sigar mi" degil, satirin TEK EYLEM olarak kalmasi — 34 karakteri asan
 * bir yonerge artik cumle degil paragraftir ve okunmaz.
 */
class TutorialStringsTest {

    private companion object {
        /** Bkz. sinif KDoc'undaki turetme. */
        const val CAPTION_BUDGET_CHARS = 34

        /** Bicimlendirme argumaninin ornek genisligi. */
        const val FORMAT_ARG_SAMPLE = "60"

        /** strings.xml bicimlendirme argumanlari: yuzde, sira no, dolar, tur harfi. */
        val FORMAT_ARG = Regex("%\\d+\\\$[sd]")

        /**
         * `name` disinda baska nitelik de tasiyabilir (ornegin
         * `tools:ignore`), bu yuzden acilis etiketinin kalani serbest.
         */
        val STRING_ENTRY = Regex(
            "<string name=\"([^\"]+)\"[^>]*>(.*?)</string>",
            RegexOption.DOT_MATCHES_ALL
        )

        val XML_COMMENT = Regex("<!--(.*?)-->", RegexOption.DOT_MATCHES_ALL)

        const val EN_PATH = "src/main/res/values/strings_tutorial.xml"
        const val TR_PATH = "src/main/res/values-tr/strings_tutorial.xml"
    }

    /** Gradle birim testi modul kokunden kosar; CI/IDE farki icin birkac yol denenir. */
    private fun resFile(relative: String): File =
        listOf(File(relative), File("app/$relative"), File("../app/$relative"))
            .firstOrNull { it.isFile }
            ?: error("kaynak dosyasi bulunamadi: $relative (cwd=${File(".").absolutePath})")

    private fun readStrings(relative: String): Map<String, String> =
        STRING_ENTRY.findAll(resFile(relative).readText(Charsets.UTF_8))
            .associate { it.groupValues[1] to it.groupValues[2] }

    private val english by lazy { readStrings(EN_PATH) }
    private val turkish by lazy { readStrings(TR_PATH) }

    /** Bicimlendirildikten sonra ekranda gorunecek hali. */
    private fun rendered(value: String): String = value.replace(FORMAT_ARG, FORMAT_ARG_SAMPLE)

    private fun locales() = listOf("en" to english, "tr" to turkish)

    private fun captionKeys() = english.keys.filter { it.startsWith("tutorial_step_") }

    // =======================================================================
    // 1) CEVIRI PARITESI
    // =======================================================================

    @Test
    fun everyTutorialStringIsTranslated() {
        assertTrue("Ingilizce ogretici metinleri bos olamaz", english.isNotEmpty())
        assertEquals(
            "eksik/fazla Turkce karsilik (lint MissingTranslation bunu HATA sayar)",
            english.keys.sorted(),
            turkish.keys.sorted()
        )
    }

    @Test
    fun formatArgumentsMatchAcrossLocales() {
        // Uyusmayan arguman calisma aninda IllegalFormatException demek.
        for ((key, value) in english) {
            assertEquals(
                "'$key' anahtarinda bicimlendirme argumanlari uyusmuyor",
                FORMAT_ARG.findAll(value).map { it.value }.toList(),
                FORMAT_ARG.findAll(turkish[key].orEmpty()).map { it.value }.toList()
            )
        }
    }

    // =======================================================================
    // 2) TASMA BUTCESI — her iki dilde
    // =======================================================================

    @Test
    fun everyCaptionFitsTheSingleLineBudgetInBothLocales() {
        assertEquals("ogretici 5 adim yonergesi bekliyor", 5, captionKeys().size)

        for (key in captionKeys()) {
            for ((locale, table) in locales()) {
                val shown = rendered(table.getValue(key))
                assertTrue(
                    "[$locale] '$key' cok uzun: ${shown.length} karakter " +
                        "(butce $CAPTION_BUDGET_CHARS) -> \"$shown\"",
                    shown.length <= CAPTION_BUDGET_CHARS
                )
            }
        }
    }

    @Test
    fun turkishIsMeasuredAgainstEnglishSoRegressionsAreVisible() {
        // Turkce metin Ingilizceden ~%20-35 uzun. Fark buyurse butce degil,
        // KARSILIK gozden gecirilmeli; bu test o ani gorunur kilar.
        for (key in captionKeys()) {
            val en = rendered(english.getValue(key)).length
            val tr = rendered(turkish.getValue(key)).length
            assertTrue(
                "'$key' Turkce karsiligi Ingilizcenin 1,6 katini asiyor (en=$en, tr=$tr)",
                tr <= (en * 1.6f).toInt() + 4
            )
        }
    }

    @Test
    fun captionsStayOnASingleLineAndCarryOneActionEach() {
        for ((locale, table) in locales()) {
            for ((key, value) in table.filterKeys { it.startsWith("tutorial_step_") }) {
                assertFalse(
                    "[$locale] '$key' satir sonu iceremez: serit TEK satir",
                    value.contains("\\n") || value.contains("\n")
                )
                // Iki cumlelik yonerge = iki eylem. Ogretici adim basina TEK
                // eylem gosterir; ikincisi ayri bir ADIM olmali.
                assertEquals(
                    "[$locale] '$key' birden fazla cumle iceriyor: \"$value\"",
                    0,
                    value.count { it == '.' }
                )
            }
        }
    }

    @Test
    fun skipLabelStaysShortEnoughForTheChip() {
        // Cip en az 48 dp genisliginde ve 18 dp x 2 padding tasiyor; etiket
        // buyudugunde serit metnini sikistirir.
        for ((locale, table) in locales()) {
            val label = table.getValue("tutorial_skip")
            assertTrue("[$locale] GEC etiketi cok uzun: \"$label\"", label.length <= 8)
        }
    }

    // =======================================================================
    // 3) XML SAGLIGI
    // =======================================================================

    @Test
    fun commentsContainNoDoubleHyphenWhichBreaksResourceMerging() {
        // "--" bir XML yorumunun ICINDE gecersizdir ve mergeDebugResources'i
        // kirar. Bu hata bu projede bir kez yasandi.
        for (path in listOf(EN_PATH, TR_PATH)) {
            val text = resFile(path).readText(Charsets.UTF_8)
            for (comment in XML_COMMENT.findAll(text)) {
                assertFalse(
                    "$path yorumunda '--' var, mergeDebugResources kirilir",
                    comment.groupValues[1].contains("--")
                )
            }
        }
    }

    @Test
    fun tutorialStringsLiveInTheirOwnFileAwayFromTheSharedTable() {
        // Ayri dosya bilincli bir karar: strings.xml birden fazla ajan
        // tarafindan ayni anda duzenleniyor. Anahtarlar oraya sizarsa
        // birlestirme catismasi geri gelir.
        val shared = listOf(
            "src/main/res/values/strings.xml",
            "src/main/res/values-tr/strings.xml"
        )
        for (path in shared) {
            val text = resFile(path).readText(Charsets.UTF_8)
            assertFalse(
                "$path icine ogretici anahtari sizmis",
                text.contains("name=\"tutorial_")
            )
        }
    }
}
