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
 *
 * ---------------------------------------------------------------------------
 * 2026-08-20 — DORT YENI DERS
 * ---------------------------------------------------------------------------
 * Yukseltme, hedefleme, guclendirici ve satis satirlari eklendi. Iki yapisal
 * degisiklik gerekti:
 *
 * 1. Yuva turleri **ikiden altiya** cikti. Eski olcum her `%n$s`i "kule ya da
 *    dusman" varsayiyordu; hedefleme satiri bir MOD adi, guclendirici satiri
 *    bir GUCLENDIRICI adi ve iki PARCA metin tasiyor. Yanlis tur en kotu
 *    durumu yanlis olcerdi (bkz. [Slot]).
 * 2. "Her govde bir `%.1f` icermeli" kurali daraltildi — gerekce
 *    [DECIMAL_KEYS] KDoc'unda.
 */
/**
 * Bir `%n$s` yuvasina calisma aninda NE geliyor.
 *
 * Tur ayrimi olcumu GERCEKCI kilar: her yuvaya ayrimsiz "en uzun ad"i koymak,
 * kule yuvasina bir dusman adi sikistirdigi icin gercekte olusamayacak bir
 * satir uretir ve testi sahte kirmiziya dusururdu. Yuvaya dogru turun EN UZUN
 * degeri konur; bu, olusabilecek en kotu GERCEK satirdir.
 */
private enum class Slot { TOWER, ENEMY, MODE, BOOSTER, EFFECT, COST }

class UnlockHintStringsTest {

    private companion object {
        /** Serit govdesi. Turetilisi sinif KDoc'unda. */
        const val BODY_BUDGET_CHARS = 68

        /** "ANLADIM" cipi. Genisligi govdenin butcesinden DUSULMUS durumda. */
        const val CHIP_BUDGET_CHARS = 10

        /**
         * Rozet (ZIRH / YENI / YUKSELT ...). Govde butcesinin turetilisinde
         * rozete 54 dp ayrildi; 10 sp kalin metinde bu 7 karaktere denk gelir.
         * Sekizinci karakter govdenin butcesini yer, bu yuzden rozetler cipten
         * DAHA DAR bir esikle olculur.
         */
        const val TAG_BUDGET_CHARS = 7

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
         * Serit govdesini tasiyan anahtarlar ve her `%n$s` yuvasinin turu.
         *
         * Bu esleme `TutorialOverlay.hintBody`'nin aynasidir. Esleme kayarsa
         * (ornegin sablona yeni bir arguman eklenirse)
         * [everyStripLineFitsTheSingleLineBudget] yanlis olcer; bu yuzden
         * [argumentCountsMatchTheSlotMap] eslemenin eksiksizligini de kilitler.
         */
        val SLOT_KINDS: Map<String, Map<String, Slot>> = mapOf(
            "hint_armor_intro" to mapOf(
                "1" to Slot.TOWER, "2" to Slot.ENEMY, "4" to Slot.ENEMY
            ),
            "hint_tower_matchup" to mapOf(
                "1" to Slot.ENEMY, "2" to Slot.TOWER, "4" to Slot.TOWER
            ),
            "hint_frost_role" to mapOf("1" to Slot.TOWER),
            "hint_upgrade_intro" to mapOf("1" to Slot.TOWER),
            // Son yuva HEDEFLEME MODU adi (inspector_target_*), dusman degil.
            "hint_targeting_intro" to mapOf(
                "1" to Slot.ENEMY, "3" to Slot.ENEMY, "5" to Slot.MODE
            ),
            // Guclendirici satiri PARCALI: govde, ad + etki metni + bedel metni
            // alir ve iki parca da bu dosyanin KENDI anahtarlarindan gelir.
            "hint_booster_intro" to mapOf(
                "1" to Slot.BOOSTER, "2" to Slot.EFFECT, "3" to Slot.COST
            ),
            "hint_sell_intro" to mapOf("1" to Slot.TOWER)
        )

        val BODY_KEYS: Set<String> = SLOT_KINDS.keys

        /**
         * Ondalikli deger TASIYAN govdeler.
         *
         * ⚠ SOZLESME 2026-08-20'DE DARALTILDI. Eskiden kural "HER govde bir
         * `%.1f` icermeli" idi ve bu, gercek kisitin degil o gunku icerigin
         * kilitlenmesiydi: butun ipuclari DPS konusuyordu. Gercek kisit
         * "ondalik ayraci ELLE yazilamaz"dir (Turkce virgul, Ingilizce nokta)
         * ve yalnizca ondalikli bir deger gosteren satirlar icin anlamlidir.
         * Satis ve guclendirici dersleri yalnizca TAM SAYI gosterir; onlara
         * uydurma bir ondalik eklemek metni uzatirdi, dogruluk katmazdi.
         */
        val DECIMAL_KEYS = setOf(
            "hint_armor_intro",
            "hint_tower_matchup",
            "hint_frost_role",
            "hint_upgrade_intro"
        )

        /** Rozetler — en dar esik. */
        val TAG_KEYS = setOf(
            "hint_tag_armor",
            "hint_tag_new",
            "hint_tag_upgrade",
            "hint_tag_target",
            "hint_tag_booster",
            "hint_tag_sell"
        )

        /** Kisa kalmasi gereken diger anahtarlar. */
        val CHIP_KEYS = setOf("hint_dismiss")

        /** Ad kaynaklarinin `strings.xml` icindeki onekleri. */
        const val TOWER_PREFIX = "tower_"
        const val ENEMY_PREFIX = "enemy_"
        const val BOOSTER_PREFIX = "booster_"

        /**
         * Hedefleme modu etiketleri. Onek kullanilamaz: `inspector_target`
         * oneki `inspector_targeting_icon_desc` erisilebilirlik metnini de
         * yakalar ve o metin ekranda ASLA bu yuvaya girmez.
         */
        val MODE_KEYS = setOf(
            "inspector_target_first",
            "inspector_target_last",
            "inspector_target_strongest",
            "inspector_target_weakest"
        )

        /** Guclendirici satirinin parcalari; ikisi de bu dosyanin icinde. */
        val EFFECT_KEYS = setOf(
            "hint_booster_effect_supply",
            "hint_booster_effect_damage",
            "hint_booster_effect_repair"
        )
        val COST_KEYS = setOf(
            "hint_booster_cost_ad",
            "hint_booster_cost_supply",
            "hint_booster_cost_coin"
        )
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

    /** O dildeki her yuva turu icin EN UZUN aday — tasma olcumunun en kotu durumu. */
    private fun longestPerSlot(mainStrings: String, hints: Map<String, String>): Map<Slot, String> {
        fun longestNamed(prefix: String): String =
            STRING_ENTRY.findAll(mainStrings)
                .filter { it.groupValues[1].startsWith(prefix) }
                .filter { it.groupValues[1].endsWith("_name") }
                .map { it.groupValues[2] }
                .maxByOrNull { it.length }
                ?: error("strings.xml icinde '$prefix*_name' bulunamadi")

        fun longestOf(keys: Set<String>, source: Map<String, String>): String =
            keys.map { key ->
                // Parca metinlerin KENDI sayi argumanlari var; onlar da
                // doldurulmali, yoksa "%1$d" ham hali olculur.
                plain(FORMAT_ARG.replace(source.getValue(key)) { INT_SAMPLE })
            }.maxByOrNull { it.length } ?: error("bos aday listesi")

        val modeStrings = STRING_ENTRY.findAll(mainStrings)
            .filter { it.groupValues[1] in MODE_KEYS }
            .associate { it.groupValues[1] to it.groupValues[2] }

        return mapOf(
            Slot.TOWER to longestNamed(TOWER_PREFIX),
            Slot.ENEMY to longestNamed(ENEMY_PREFIX),
            Slot.BOOSTER to longestNamed(BOOSTER_PREFIX),
            Slot.MODE to longestOf(MODE_KEYS, modeStrings),
            Slot.EFFECT to longestOf(EFFECT_KEYS, hints),
            Slot.COST to longestOf(COST_KEYS, hints)
        )
    }

    private val englishNames by lazy { longestPerSlot(readRaw(EN_MAIN), english) }
    private val turkishNames by lazy { longestPerSlot(readRaw(TR_MAIN), turkish) }

    /** XML varliklarini ekranda gorunen karakterlere cevirir. */
    private fun plain(value: String): String = value
        .replace("%%", "%")
        .replace("&#8212;", "—")
        .replace("&#8594;", "→")
        .replace("&#183;", "·")
        .replace("&#215;", "×")

    /**
     * Ekranda gorunecek en kotu hali: her `%n$s` yuvasina TURUNE uygun en uzun
     * deger, sayi yuvalarina en genis ornek deger konur.
     */
    private fun rendered(key: String, value: String, names: Map<Slot, String>): String =
        plain(
            FORMAT_ARG.replace(value) { match ->
                val position = match.groupValues[1]
                when (match.groupValues[3]) {
                    "s" -> {
                        val slot = SLOT_KINDS[key]?.get(position)
                            ?: error("$key: %$position\$s yuvasinin turu SLOT_KINDS icinde yok")
                        names.getValue(slot)
                    }
                    "f" -> FLOAT_SAMPLE
                    else -> INT_SAMPLE
                }
            }
        )

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
        val required = BODY_KEYS + TAG_KEYS + CHIP_KEYS + EFFECT_KEYS + COST_KEYS +
            setOf("hint_dismiss_desc")
        for (key in required) {
            assertTrue("eksik anahtar: $key", key in english)
        }
    }

    /**
     * Her ipucunun bir rozeti olmali.
     *
     * `TutorialOverlay.hintTagRes` her [HintCopy] bicimine bir rozet atar ve
     * Kotlin'in `when`'i o tarafi zaten eksiksiz tutar. Buradaki kilit metin
     * tarafinda: kod bir rozet ISTEYIP kaynak tarafinda karsiligi olmazsa
     * derleme degil, CALISMA ANI hatasi olurdu.
     */
    @Test
    fun everyHintKindHasATag() {
        assertEquals(
            "rozet sayisi ipucu bicimi sayisiyla ayni gitmeli",
            TAG_KEYS.size,
            english.keys.count { it.startsWith("hint_tag_") }
        )
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
            for (key in DECIMAL_KEYS) {
                val value = strings.getValue(key)
                assertTrue(
                    "$locale/$key: DPS ve carpan degerleri %.1f ile bicimlenmeli " +
                        "(ondalik ayraci elle yazilamaz)",
                    FORMAT_ARG.findAll(value).any { it.groupValues[3] == "f" }
                )
            }
        }
    }

    /**
     * Hicbir satirda ELLE yazilmis ondalik ayraci olmamali.
     *
     * [dpsValuesAreFormattedAsDecimals] ondalikli degerin DOGRU bicimlendigini
     * kilitler; bu test ise yanlis olani yasaklar. Ikisi birlikte, eski
     * "her govde bir %.1f icermeli" kuralinin gercek amacini tam olarak
     * karsilar: sayilarin kaynagi kod, ayracin kaynagi cihazin dilidir.
     */
    @Test
    fun noTemplateHardCodesADecimalNumber() {
        val hardCoded = Regex("(?<!\\$)\\d+[.,]\\d")
        for ((locale, strings, _) in locales()) {
            for (key in BODY_KEYS + EFFECT_KEYS + COST_KEYS) {
                val value = strings.getValue(key)
                assertFalse(
                    "$locale/$key icinde elle yazilmis ondalikli sayi var: \"$value\"",
                    hardCoded.containsMatchIn(value)
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
                        "Her yuvaya turunun EN UZUN degeri konarak olculdu " +
                        "($names): \"$shown\"",
                    shown.length <= BODY_BUDGET_CHARS
                )
            }
        }
    }

    /**
     * Yuva eslemesi ([SLOT_KINDS]) sablonlardaki HER `%n$s` yuvasini
     * tanimlamali. Sablona yeni bir ad argumani eklenip esleme guncellenmezse
     * tasma olcumu sessizce YANLIS olurdu; bu test o sessizligi bozar.
     */
    @Test
    fun argumentCountsMatchTheSlotMap() {
        for ((locale, strings, _) in locales()) {
            for (key in BODY_KEYS) {
                val stringSlots = FORMAT_ARG.findAll(strings.getValue(key))
                    .filter { it.groupValues[3] == "s" }
                    .map { it.groupValues[1] }
                    .toSet()
                val declared = SLOT_KINDS.getValue(key).keys
                assertEquals(
                    "$locale/$key: SLOT_KINDS sablondaki ad yuvalariyla BIREBIR ayni olmali",
                    stringSlots,
                    declared
                )
                assertTrue("$locale/$key: en az bir ad argumani olmali", stringSlots.isNotEmpty())
            }
        }
    }

    @Test
    fun tagsAndTheDismissChipStayShort() {
        for ((locale, strings, _) in locales()) {
            for (key in TAG_KEYS) {
                val shown = strings.getValue(key)
                assertTrue(
                    "$locale/$key rozet butcesini asiyor " +
                        "(${shown.length} > $TAG_BUDGET_CHARS): \"$shown\"",
                    shown.length <= TAG_BUDGET_CHARS
                )
            }
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
