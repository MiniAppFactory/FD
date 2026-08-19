package com.miniappfactory.frontlinedefender.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * KAMPANYA ANLATISI — METIN BUTCESI, SIRA SOZLESMESI VE CEVIRI ESLIGI.
 *
 * `docs/STORY.md` §3/§4. Bu testin kilitledigi uc sey:
 *
 * 1. **Sira sozlesmesi.** `story_objectives` dizisinin indeksi `levelId - 1`
 *    demektir. Bir ogenin dusmesi ya da fazladan bir ogenin girmesi SESSIZ
 *    bir hatadir — oyun calisir, sadece 30 bolumde yanlis emir yazar.
 *    Derleyici bunu yakalayamaz; sayi burada kilitlenir.
 *
 * 2. **Tasma.** Bolum karti SABIT 126 dp genisliktedir (yatay seritteki
 *    gorsel ritim icin) ve durum satiri o kartin en dar dugumudur. Butcenin
 *    turetilisi `res/values/strings_story.xml` basliginda; ozeti:
 *      kart ic genisligi 126 - 2x10 = 106 dp
 *      satir 9 sp kalin, ~5,3 dp/karakter        -> ~20 karakter
 *      AutoShrinkText 7 sp'ye iner               -> ~25 karakter
 *    [OBJECTIVE_BUDGET_CHARS] = 24 -> **hicbir dilde kirpilma olusamaz.**
 *    Butceyi buyutmek isteyen once kartin genisligini degistirmelidir.
 *
 * 3. **Ton.** `STORY.md` §0: telsiz konusmasi, destan degil. Testin
 *    zorlayabildigi kismi mekaniktir — satirlar KISA, cumle biter (nokta ya
 *    da emir), ve bicimlendirme argumani TASIMAZ (durum satiri hicbir
 *    calisma-ani degeri almaz; alsaydi butce olculemezdi).
 *
 * Neden Robolectric degil de dosya ayristirmasi: `UnlockHintStringsTest` ve
 * `TutorialStringsTest` ile ayni desen. Butce sorusu METNIN kendisiyle
 * ilgilidir, `Resources` yuklemesiyle degil; dosyayi okumak testi hem hizli
 * hem de iki dili yan yana koyabilir kilar.
 */
class StoryStringsTest {

    private companion object {
        const val EN_STORY = "src/main/res/values/strings_story.xml"
        const val TR_STORY = "src/main/res/values-tr/strings_story.xml"

        /** Kampanya bolum sayisi (`GameConfig.CAMPAIGN_LEVEL_COUNT`). */
        const val LEVEL_COUNT = 55

        /** Perde sayisi (`CAMPAIGN_55.md` K1). */
        const val ACT_COUNT = 5

        /** Bolum durum satiri. Turetilisi sinif KDoc'unda. */
        const val OBJECTIVE_BUDGET_CHARS = 24

        /** Perde karti govdesi: 420 dp genislik, 13 sp, en fazla ~6 satir. */
        const val ACT_BODY_BUDGET_CHARS = 220

        /** Kart basligi tek satir, 24 sp; 16 sp'ye inse bile 22 karakter sigar. */
        const val ACT_TITLE_BUDGET_CHARS = 22

        /** Serit ayraci ("KISIM III") ve onay cipi ("ANLASILDI"). */
        const val ACT_LABEL_BUDGET_CHARS = 10
        const val ACK_BUDGET_CHARS = 12

        val STRING_ENTRY = Regex(
            "<string name=\"([^\"]+)\"[^>]*>(.*?)</string>",
            RegexOption.DOT_MATCHES_ALL
        )

        val ARRAY_BLOCK = Regex(
            "<string-array name=\"story_objectives\"[^>]*>(.*?)</string-array>",
            RegexOption.DOT_MATCHES_ALL
        )

        val ARRAY_ITEM = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)

        val XML_COMMENT = Regex("<!--(.*?)-->", RegexOption.DOT_MATCHES_ALL)

        /** `%1$s` / `%1$d` — durum satirlarinda OLMAMASI gereken sey. */
        val FORMAT_ARG = Regex("%(\\d+)\\\$[sdf]")
    }

    /** Gradle birim testi modul kokunden kosar; CI/IDE farki icin birkac yol denenir. */
    private fun resFile(relative: String): File =
        listOf(File(relative), File("app/$relative"), File("../app/$relative"))
            .firstOrNull { it.isFile }
            ?: error("kaynak dosyasi bulunamadi: $relative (cwd=${File(".").absolutePath})")

    private fun readRaw(relative: String): String =
        XML_COMMENT.replace(resFile(relative).readText(Charsets.UTF_8), "")

    private fun readStrings(relative: String): Map<String, String> =
        STRING_ENTRY.findAll(readRaw(relative))
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun readObjectives(relative: String): List<String> {
        val block = ARRAY_BLOCK.find(readRaw(relative))
            ?: error("story_objectives dizisi yok: $relative")
        return ARRAY_ITEM.findAll(block.groupValues[1]).map { it.groupValues[1] }.toList()
    }

    private val english by lazy { readStrings(EN_STORY) }
    private val turkish by lazy { readStrings(TR_STORY) }
    private val englishObjectives by lazy { readObjectives(EN_STORY) }
    private val turkishObjectives by lazy { readObjectives(TR_STORY) }

    private fun byLanguage(): List<Pair<String, Map<String, String>>> =
        listOf("en" to english, "tr" to turkish)

    private fun objectivesByLanguage(): List<Pair<String, List<String>>> =
        listOf("en" to englishObjectives, "tr" to turkishObjectives)

    // =====================================================================
    // SIRA SOZLESMESI — indeks = levelId - 1
    // =====================================================================

    @Test
    fun bothLanguagesCarryExactlyOneObjectivePerCampaignLevel() {
        objectivesByLanguage().forEach { (lang, lines) ->
            assertEquals(
                "$lang/story_objectives kampanya bolum sayisi kadar oge tasimali " +
                    "(indeks = levelId - 1); sapma sessizce YANLIS bolumde YANLIS " +
                    "emir yazar",
                LEVEL_COUNT,
                lines.size
            )
        }
    }

    @Test
    fun noObjectiveIsBlank() {
        objectivesByLanguage().forEach { (lang, lines) ->
            lines.forEachIndexed { index, line ->
                assertTrue(
                    "$lang/story_objectives[$index] (bolum ${index + 1}) bos — bos " +
                        "satir cizilmez ve o bolum anlatisiz kalir",
                    line.isNotBlank()
                )
            }
        }
    }

    /**
     * Iki dilde de AYNI satirin yazilmasi bir sey ifade etmez ama bir dilde
     * kaydirilmis bir dizi digeriyle **ayni uzunlukta** kalabilir. Bu yuzden
     * ayrica perde sinirlarinin dolu oldugu kontrol edilir: her perdenin ilk
     * ve son bolumu (1/11, 12/22, 23/33, 34/44, 45/55) iki dilde de dolu
     * olmalidir.
     */
    @Test
    fun everyActBoundaryLevelHasAnObjectiveInBothLanguages() {
        val boundaries = (1..ACT_COUNT).flatMap { act ->
            listOf((act - 1) * 11 + 1, act * 11)
        }
        objectivesByLanguage().forEach { (lang, lines) ->
            boundaries.forEach { levelId ->
                assertTrue(
                    "$lang: perde siniri bolum $levelId icin durum satiri yok",
                    lines[levelId - 1].isNotBlank()
                )
            }
        }
    }

    // =====================================================================
    // TASMA BUTCELERI
    // =====================================================================

    @Test
    fun everyObjectiveFitsTheCardBudget() {
        objectivesByLanguage().forEach { (lang, lines) ->
            lines.forEachIndexed { index, line ->
                assertTrue(
                    "$lang bolum ${index + 1} durum satiri ${line.length} karakter " +
                        "(butce $OBJECTIVE_BUDGET_CHARS): \"$line\" — 126 dp kartta " +
                        "7 sp'de bile kirpilir",
                    line.length <= OBJECTIVE_BUDGET_CHARS
                )
            }
        }
    }

    @Test
    fun everyActCardTextFitsItsBudget() {
        byLanguage().forEach { (lang, strings) ->
            (1..ACT_COUNT).forEach { act ->
                val title = strings.getValue("story_act_${act}_title")
                val body = strings.getValue("story_act_${act}_body")
                val label = strings.getValue("story_act_label_$act")

                assertTrue(
                    "$lang/story_act_${act}_title ${title.length} karakter " +
                        "(butce $ACT_TITLE_BUDGET_CHARS): \"$title\"",
                    title.length <= ACT_TITLE_BUDGET_CHARS
                )
                assertTrue(
                    "$lang/story_act_${act}_body ${body.length} karakter " +
                        "(butce $ACT_BODY_BUDGET_CHARS) — mobil TD oyuncusu roman okumaz",
                    body.length <= ACT_BODY_BUDGET_CHARS
                )
                assertTrue(
                    "$lang/story_act_label_$act ${label.length} karakter " +
                        "(butce $ACT_LABEL_BUDGET_CHARS): \"$label\"",
                    label.length <= ACT_LABEL_BUDGET_CHARS
                )
            }
            val ack = strings.getValue("story_ack")
            assertTrue(
                "$lang/story_ack ${ack.length} karakter (butce $ACK_BUDGET_CHARS): \"$ack\"",
                ack.length <= ACK_BUDGET_CHARS
            )
        }
    }

    // =====================================================================
    // CEVIRI ESLIGI — lint MissingTranslation'i BEKLEMEDEN
    // =====================================================================

    @Test
    fun turkishCarriesEveryEnglishKey() {
        val missing = english.keys - turkish.keys
        assertTrue("values-tr/strings_story.xml eksik anahtar: $missing", missing.isEmpty())

        val extra = turkish.keys - english.keys
        assertTrue(
            "values-tr/strings_story.xml fazladan anahtar (varsayilan dilde yok): $extra",
            extra.isEmpty()
        )
    }

    @Test
    fun actRangeTemplateKeepsBothPositionalArguments() {
        byLanguage().forEach { (lang, strings) ->
            val template = strings.getValue("story_act_range")
            val args = FORMAT_ARG.findAll(template).map { it.groupValues[1] }.toSet()
            assertEquals(
                "$lang/story_act_range iki konumlu arguman tasimali " +
                    "(ilk ve son bolum): \"$template\"",
                setOf("1", "2"),
                args
            )
        }
    }

    // =====================================================================
    // TON — testin zorlayabildigi kismi
    // =====================================================================

    /**
     * Durum satiri hicbir calisma-ani degeri almaz. Alsaydi butce olculemezdi
     * (arguman uzunlugu bilinmez) ve satir "5 dalga" gibi HUD'in zaten
     * soyledigi bir seyi tekrarlamaya baslardi.
     */
    @Test
    fun objectivesCarryNoFormatArguments() {
        objectivesByLanguage().forEach { (lang, lines) ->
            lines.forEachIndexed { index, line ->
                assertFalse(
                    "$lang bolum ${index + 1} durum satirinda bicimlendirme argumani " +
                        "var: \"$line\" — durum satiri sayi TASIMAZ",
                    FORMAT_ARG.containsMatchIn(line)
                )
            }
        }
    }

    /** Telsiz emri biter. Yarim kalan satir ("Gecidi tut" gibi) ton disidir. */
    @Test
    fun everyObjectiveEndsAsAFinishedLine() {
        objectivesByLanguage().forEach { (lang, lines) ->
            lines.forEachIndexed { index, line ->
                assertTrue(
                    "$lang bolum ${index + 1}: \"$line\" nokta ile bitmiyor",
                    line.trimEnd().endsWith(".")
                )
            }
        }
    }
}
