package com.miniappfactory.frontlinedefender.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * KILIT ACILMA IPUCLARI — METIN BUTCESI VE TASMA KONTROLU.
 *
 * Bu ekipte tekrar eden hata kaynagi, sabit olculu bir yuzeye SIGMAYAN uzun
 * ceviri metnidir. Ipucu seridi tek satirliktir ve ayni satiri iki baska
 * dugumle paylasir (ZIRH/YENI rozeti ve "ANLADIM" cipi), yani risk
 * ogretici seridinden bile YUKSEK. Butce kod tarafinda kilitleniyor, gozle
 * degil.
 *
 * ---------------------------------------------------------------------------
 * TASMA OLCUMU GERCEK ADLARLA YAPILIR
 * ---------------------------------------------------------------------------
 * Ipucu satirlarinin buyuk kismi ARGUMANDIR: kule ve dusman adlari calisma
 * aninda `strings.xml`den gelir. Bu yuzden sablonun kendi uzunlugunu olcmek
 * anlamsiz olurdu — test her `%n$s` yerine o dildeki **EN UZUN** kule/dusman
 * adini koyar ve en kotu durumu olcer. Turkce adlar ("Füze Rampası",
 * "Kalkanlı Er") Ingilizce muadillerinden farkli uzunlukta oldugu icin iki dil
 * ayri ayri olculur.
 *
 * ---------------------------------------------------------------------------
 * BUTCENIN TURETILISI — serit yerlesimi (TutorialOverlay.UnlockHintStrip)
 * ---------------------------------------------------------------------------
 *   satir       : HUD'un altinda, yatay 16 dp padding (iki yan)  -> W - 32 dp
 *   ANLADIM cipi: en az 48 dp; "ANLADIM" 13 sp + 18 dp x 2       -> ~92 dp
 *   arada       : 12 dp
 *   serit ici   : 14 dp x 2 padding                              -> 28 dp
 *   rozet       : ZIRH / YENI, 10 sp + 6 dp x 2 padding + 8 dp   -> ~62 dp
 * En dar desteklenen yatay ekran 640 dp kabul edildi:
 *   640 - 32 - 92 - 12 - 28 - 62 = 414 dp metin genisligi.
 * Kalin Latin metinde ortalama karakter genisligi 13 sp'de ~7,7 dp, 10 sp'de
 * ~5,9 dp. [BODY_BUDGET_CHARS] = 68 karakter:
 *   13 sp'de 524 dp (sigmaz)  ->  AutoShrinkText devreye girer
 *   10 sp'de 401 dp (sigar)   ->  kirpma OLUSAMAZ
 * Yani butce "tam puntoda sigar" degil, **hicbir dilde kirpilmaz** garantisidir.
 * Butceyi buyutmek isteyen once seridin yerlesimini degistirmelidir; 414 dp
 * sinirinda 10 sp'de sigan karakter sayisi 70'tir, yani 68 zaten tavana yakin.
 *
 * OLCULEN EN KOTU DURUM (bu testin uretip dogruladigi satirlar):
 *   en / hint_tower_matchup -> 65 karakter
 *   en / hint_armor_intro   -> 67 karakter  ("Shielded Trooper" + "Infantry Squad")
 *   tr / hint_frost_role    -> 60 karakter
 * Sayilar sablonlar veya `strings.xml` adlari degistiginde kayar; kilit
 * [BODY_BUDGET_CHARS] degeridir, bu liste yalnizca bugunku olcumdur.
 */
class UnlockHintStringsTest {

    private companion object {
        /** Serit govdesi. Turetilisi sinif KDoc'unda. */
        const val BODY_BUDGET_CHARS = 68

        /** Rozet ve cip kisa kalmali; uzarlarsa govdenin butcesini yerler. */
        const val CHIP_BUDGET_CHARS = 10

        /** Ondalikli sayi argumaninin ekrandaki ornek genisligi. */
        const val FLOAT_SAMPLE = "43,8"

        /** Tam sayi argumaninin ornek genisligi. */
        const val INT_SAMPLE = "42"

        /** `%1$s` / `%2$.1f` / `%3$d` — konumlu bicimlendirme argumanlari. */
        val FORMAT_ARG = Regex("%(\\d+)\\\$(\\.\\d+)?([sdf])")

        val STRING_ENTRY = Regex(
            "<string name=\"([^\"]+)\"[^>]*>(.*?)</string>",
            RegexOption.DOT_MATCHES_ALL
        )

        val XML_COMMENT = Regex("<!--(.*?)-->", RegexOption.DOT_MATCHES_ALL)

        const val EN_HINTS = "src/main/res/values/strings_hints.xml"
        const val TR_HINTS = "src/main/res/values-tr/strings_hints.xml"
        const val EN_MAIN = "src/main/res/values/strings.xml"
        const val TR_MAIN = "src/main/res/values-tr/strings.xml"

        /**
         * Serit govdesini tasiyan anahtarlar (rozet ve cip haric) ve her
         * anahtarda hangi `%n$s` yuvasinin KULE adi aldigi.
         *
         * Bu esleme `TutorialOverlay.hintBody`'nin aynasidir ve olcumu GERCEKCI
         * kilar: her yuvaya ayrimsiz "en uzun ad"i koymak, kule yuvasina bir
         * dusman adi sikistirdigi icin gercekte olusamayacak bir satir uretir
         * ve testi sahte kirmiziya dusururdu. Yuvaya dogru turun EN UZUN adi
         * konur; bu, olusabilecek en kotu gercek satirdir.
         *
         * Esleme kayarsa (ornegin sablona yeni bir arguman eklenirse)
         * [everyStripLineFitsTheSingleLineBudget] yanlis olcer; bu yuzden
         * [argumentCountsMatchTheSlotMap] eslemenin eksiksizligini de kilitler.
         */
        val TOWER_SLOTS: Map<String, Set<String>> = mapOf(
            // %1$s kule, %2$s ve %4$s dusman
            "hint_armor_intro" to setOf("1"),
            // %1$s dusman, %2$s ve %4$s kule
            "hint_tower_matchup" to setOf("2", "4"),
            // %1$s kule
            "hint_frost_role" to setOf("1")
        )

        val BODY_KEYS: Set<String> = TOWER_SLOTS.keys

        /** Kisa kalmasi gereken anahtarlar. */
        val CHIP_KEYS = setOf("hint_tag_armor", "hint_tag_new", "hint_dismiss")

        /** Ad kaynaklarinin `strings.xml` icindeki onekleri. */
        const val TOWER_PREFIX = "tower_"
        const val ENEMY_PREFIX = "enemy_"
    }

    /** Gradle birim testi modul kokunden kosar; CI/IDE farki icin birkac yol denenir. */
    private fun resFile(relative: String): File =
        listOf(File(relative), File("app/$relative"), File("../app/$relative"))
            .firstOrNull { it.isFile }
            ?: error("kaynak dosyasi bulunamadi: $relative (cwd=${File(".").absolutePath})")

    private fun readRaw(relative: String): String = resFile(relative).readText(Charsets.UTF_8)

    private fun readStrings(relative: String): Map<String, String> =
        STRING_ENTRY.findAll(readRaw(relative))
            .associate { it.groupValues[1] to it.groupValues[2] }

    private val english by lazy { readStrings(EN_HINTS) }
    private val turkish by lazy { readStrings(TR_HINTS) }

    /** O dildeki EN UZUN kule ve dusman adi — tasma olcumunun en kotu durumu. */
    private data class LongestNames(val tower: String, val enemy: String)

    private fun longestNames(mainStrings: String): LongestNames {
        fun longest(prefix: String): String =
            STRING_ENTRY.findAll(mainStrings)
                .filter { it.groupValues[1].startsWith(prefix) }
                .filter { it.groupValues[1].endsWith("_name") }
                .map { it.groupValues[2] }
                .maxByOrNull { it.length }
                ?: error("strings.xml icinde '$prefix*_name' bulunamadi")
        return LongestNames(tower = longest(TOWER_PREFIX), enemy = longest(ENEMY_PREFIX))
    }

    private val englishNames by lazy { longestNames(readRaw(EN_MAIN)) }
    private val turkishNames by lazy { longestNames(readRaw(TR_MAIN)) }

    /**
     * Ekranda gorunecek en kotu hali: her `%n$s` yuvasina TURUNE uygun en uzun
     * ad, sayi yuvalarina en genis ornek deger konur.
     */
    private fun rendered(key: String, value: String, names: LongestNames): String =
        FORMAT_ARG.replace(value) { match ->
            val position = match.groupValues[1]
            when (match.groupValues[3]) {
                "s" ->
                    if (position in (TOWER_SLOTS[key] ?: emptySet())) names.tower else names.enemy
                "f" -> FLOAT_SAMPLE
                else -> INT_SAMPLE
            }
        }.replace("%%", "%")
            .replace("&#8212;", "—")
            .replace("&#183;", "·")
            .replace("&#215;", "×")

    private fun locales() = listOf(
        Triple("en", english, englishNames),
        Triple("tr", turkish, turkishNames)
    )

    // =======================================================================
    // 1) Iki dil ayni anahtarlari tasimali
    // =======================================================================

    @Test
    fun bothLocalesDefineTheSameKeys() {
        assertEquals(
            "ipucu anahtarlari iki dilde ayni olmali",
            english.keys.sorted(),
            turkish.keys.sorted()
        )
        assertTrue("ipucu metinleri bos olamaz", english.isNotEmpty())
    }

    /** Sablonun kod tarafiyla sozlesmesi: her anahtar var olmali. */
    @Test
    fun everyStripTemplateExists() {
        for (key in BODY_KEYS + CHIP_KEYS + setOf("hint_dismiss_desc")) {
            assertTrue("eksik anahtar: $key", key in english)
        }
    }

    // =======================================================================
    // 2) Bicimlendirme argumanlari birebir eslesmeli
    // =======================================================================

    /**
     * Ayni anahtarin iki dildeki arguman KUMESI birebir ayni olmali. Sirasi
     * degisebilir (Turkce cumle yapisi farkli) ama bir arguman EKSILIRSE
     * `String.format` calisma aninda `MissingFormatArgumentException` atar —
     * yani bu test bir cokme onleyicisidir, kozmetik degil.
     */
    @Test
    fun formatArgumentsMatchAcrossLocales() {
        for (key in english.keys) {
            val en = FORMAT_ARG.findAll(english.getValue(key))
                .map { it.groupValues[1] to it.groupValues[3] }.toSet()
            val tr = FORMAT_ARG.findAll(turkish.getValue(key))
                .map { it.groupValues[1] to it.groupValues[3] }.toSet()
            assertEquals("$key: bicimlendirme argumanlari iki dilde ayni olmali", en, tr)
        }
    }

    /** Sayilar ondalikli bicimde gelmeli: "26,6" / "26.6" cihazin diline kalir. */
    @Test
    fun dpsValuesAreFormattedAsDecimals() {
        for ((locale, strings, _) in locales()) {
            for (key in BODY_KEYS) {
                val value = strings.getValue(key)
                assertTrue(
                    "$locale/$key: DPS ve carpan degerleri %.1f ile bicimlenmeli " +
                        "(ondalik ayraci elle yazilamaz)",
                    FORMAT_ARG.findAll(value).any { it.groupValues[3] == "f" }
                )
            }
        }
    }

    // =======================================================================
    // 3) TASMA — gercek adlarla, en kotu durumda
    // =======================================================================

    @Test
    fun everyStripLineFitsTheSingleLineBudget() {
        for ((locale, strings, names) in locales()) {
            for (key in BODY_KEYS) {
                val shown = rendered(key, strings.getValue(key), names)
                assertTrue(
                    "$locale/$key butceyi asiyor (${shown.length} > $BODY_BUDGET_CHARS). " +
                        "En uzun adlarla olculdu (kule \"${names.tower}\", " +
                        "dusman \"${names.enemy}\"): \"$shown\"",
                    shown.length <= BODY_BUDGET_CHARS
                )
            }
        }
    }

    /**
     * Yuva eslemesi ([TOWER_SLOTS]) sablonlarla ayni sayida `%n$s` tanimlamali.
     * Sablona yeni bir ad argumani eklenip esleme guncellenmezse tasma olcumu
     * sessizce YANLIS olurdu; bu test o sessizligi bozar.
     */
    @Test
    fun argumentCountsMatchTheSlotMap() {
        for ((locale, strings, _) in locales()) {
            for (key in BODY_KEYS) {
                val stringSlots = FORMAT_ARG.findAll(strings.getValue(key))
                    .filter { it.groupValues[3] == "s" }
                    .map { it.groupValues[1] }
                    .toSet()
                val towerSlots = TOWER_SLOTS.getValue(key)
                assertTrue(
                    "$locale/$key: TOWER_SLOTS ($towerSlots) sablondaki ad yuvalarinin " +
                        "($stringSlots) alt kumesi olmali",
                    stringSlots.containsAll(towerSlots)
                )
                assertTrue("$locale/$key: en az bir ad argumani olmali", stringSlots.isNotEmpty())
            }
        }
    }

    @Test
    fun tagsAndTheDismissChipStayShort() {
        for ((locale, strings, _) in locales()) {
            for (key in CHIP_KEYS) {
                val shown = strings.getValue(key)
                assertTrue(
                    "$locale/$key kisa kalmali (${shown.length} > $CHIP_BUDGET_CHARS): \"$shown\"",
                    shown.length <= CHIP_BUDGET_CHARS
                )
            }
        }
    }

    /** Serit TEK SATIR. Satir sonu koymak yerlesimi sessizce bozar. */
    @Test
    fun noStripLineContainsALineBreak() {
        for ((locale, strings, _) in locales()) {
            for ((key, value) in strings) {
                assertFalse(
                    "$locale/$key satir sonu icermemeli — serit tek satirlik",
                    value.contains('\n') || value.contains("\\n")
                )
            }
        }
    }

    // =======================================================================
    // 4) Dosya hijyeni
    // =======================================================================

    /**
     * XML yorumlarinda `--` gecemez (XML spesifikasyonu). Android Gradle
     * eklentisi bunu okunmasi zor bir ayristirma hatasiyla bildirir; burada
     * anlasilir bir mesajla yakalaniyor.
     */
    @Test
    fun xmlCommentsAreWellFormed() {
        for (path in listOf(EN_HINTS, TR_HINTS)) {
            for (comment in XML_COMMENT.findAll(readRaw(path))) {
                assertFalse(
                    "$path: XML yorumu icinde '--' kullanilamaz",
                    comment.groupValues[1].contains("--")
                )
            }
        }
    }

    /**
     * Ipucu metinleri KENDI dosyalarinda durmali. `strings.xml` ve
     * `strings_tutorial.xml` baska is kollarinin elinde; oraya sizan bir
     * anahtar birlestirme catismasi uretir.
     */
    @Test
    fun hintKeysLiveOnlyInTheirOwnFile() {
        for (path in listOf(EN_MAIN, TR_MAIN, "src/main/res/values/strings_tutorial.xml")) {
            val foreign = readStrings(path).keys.filter { it.startsWith("hint_") }
            assertTrue("$path icinde ipucu anahtari olmamali: $foreign", foreign.isEmpty())
        }
    }
}
