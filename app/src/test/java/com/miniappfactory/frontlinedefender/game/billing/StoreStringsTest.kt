package com.miniappfactory.frontlinedefender.game.billing

import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MAGAZA METINLERI — rutbe adlari, olay/hata metinleri, ceviri esligi ve FIYAT YASAGI.
 *
 * Kural: uygulamada gorunen fiyat YALNIZCA Play'den gelir (`formattedPrice`). Kodda ve metin
 * kaynaklarinda fiyat ya da fiyattan turetilmis yuzde YOKTUR; paketleri ayirt eden tek sey coin
 * MIKTARIDIR (satirda gosterilir). Bu test o yasagi `strings_store.xml` (values ve values-tr) icin kilitler.
 *
 * Dosya ayristirmasi (Robolectric degil): `StoryStringsTest` ile ayni desen.
 */
class StoreStringsTest {

    private companion object {
        const val EN = "src/main/res/values/strings_store.xml"
        const val TR = "src/main/res/values-tr/strings_store.xml"
        const val MAIN_EN = "src/main/res/values/strings.xml"

        val COMMENT = Regex("<!--(.*?)-->", RegexOption.DOT_MATCHES_ALL)
        val ENTRY = Regex("<string name=\"([^\"]+)\"([^>]*)>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        val FORMAT_ARG = Regex("%\\d+\\\$[sd]")
    }

    private data class Entry(val name: String, val translatable: Boolean, val text: String)

    private fun resFile(relative: String): File =
        listOf(File(relative), File("app/$relative"), File("../app/$relative"))
            .firstOrNull { it.isFile }
            ?: error("kaynak dosyasi bulunamadi: $relative (cwd=${File(".").absolutePath})")

    private fun read(relative: String): List<Entry> =
        ENTRY.findAll(COMMENT.replace(resFile(relative).readText(Charsets.UTF_8), ""))
            .map { Entry(it.groupValues[1], !it.groupValues[2].contains("translatable=\"false\""), it.groupValues[3]) }
            .toList()

    private val en by lazy { read(EN) }
    private val tr by lazy { read(TR) }

    private fun byName(entries: List<Entry>) = entries.associate { it.name to it.text }

    /** `R.string.<isim>` alan adi: kimlik -> isim (yansitma). */
    private fun resourceName(id: Int): String =
        R.string::class.java.fields.firstOrNull { it.getInt(null) == id }?.name
            ?: error("R.string icinde $id yok")

    @Test
    fun bothLocalesCarryTheSameTranslatableNames() {
        val enNames = en.filter { it.translatable }.map { it.name }.toSet()
        val trNames = tr.map { it.name }.toSet()
        assertEquals("EN'de olup TR'de olmayan / tersi", enNames, trNames)
        assertTrue("TR dosyasi debug (translatable=false) metin tasimamali", tr.all { it.translatable })
    }

    @Test
    fun namesDoNotCollideWithTheMainStringsFile() {
        val main = read(MAIN_EN).map { it.name }.toSet()
        val mine = en.map { it.name }.toSet()
        assertTrue("strings.xml ile cakisan adlar: ${main intersect mine}", (main intersect mine).isEmpty())
    }

    @Test
    fun everyTierHasADistinctNameInBothLanguages() {
        listOf("en" to byName(en), "tr" to byName(tr)).forEach { (lang, names) ->
            val display = StoreCatalog.PackTier.entries.map { tier ->
                val key = "store_tier_${tier.code.lowercase()}"
                val text = names[key]
                assertNotNull("$lang: $key yok", text)
                assertEquals("$lang: nameRes() $key'e cozulmeli", key, resourceName(tier.nameRes()))
                assertTrue("$lang: $key bos", text!!.isNotBlank())
                text
            }
            assertEquals("$lang: iki rutbe ayni adi tasiyor", display.size, display.toSet().size)
        }
        assertEquals(
            listOf("Recruit", "Sergeant", "Captain", "Colonel", "General"),
            StoreCatalog.PackTier.entries.map { byName(en)["store_tier_${it.code.lowercase()}"] },
        )
        assertEquals(
            listOf("Acemi Er", "Çavuş", "Yüzbaşı", "Albay", "General"),
            StoreCatalog.PackTier.entries.map { byName(tr)["store_tier_${it.code.lowercase()}"] },
        )
    }

    @Test
    fun everyFailureReasonAndEventHasATextInBothLanguages() {
        val enNames = byName(en)
        val trNames = byName(tr)
        FailureReason.entries.forEach { reason ->
            val name = resourceName(reason.messageRes())
            assertTrue("EN'de $name yok ($reason)", name in enNames && enNames.getValue(name).isNotBlank())
            assertTrue("TR'de $name yok ($reason)", name in trNames && trNames.getValue(name).isNotBlank())
        }
        listOf(
            StoreEvent.Granted("p", 1),
            StoreEvent.AlreadyCredited("p"),
            StoreEvent.Pending("p"),
            StoreEvent.Cancelled,
            StoreEvent.Failed(FailureReason.SAVE_FAILED, "x"),
        ).forEach { event ->
            val name = resourceName(event.messageRes())
            assertTrue("EN'de $name yok ($event)", name in enNames)
            assertTrue("TR'de $name yok ($event)", name in trNames)
        }
    }

    @Test
    fun theGrantedTextTakesExactlyTheCoinAmountArgument() {
        listOf(byName(en), byName(tr)).forEach { names ->
            val granted = names.getValue("store_event_granted")
            assertEquals(listOf("%1\$s"), FORMAT_ARG.findAll(granted).map { it.value }.toList())
            val row = names.getValue("store_pack_tier_amount")
            assertEquals(listOf("%1\$s", "%2\$s"), FORMAT_ARG.findAll(row).map { it.value }.toList())
        }
    }

    /**
     * FIYAT YASAGI: para birimi simgesi/kodu, yuzde, indirim dili ve GOMULU RAKAM yok. Bicim
     * argumanlari (%1$s) cikarildiktan sonra hicbir metinde rakam kalmaz — coin miktari ve fiyat
     * kodda/metinde degil, sirasiyla katalogdan ve Play'den gelir.
     */
    @Test
    fun noStoreStringCarriesAPriceAPercentageOrEmbeddedDigits() {
        // "TRY" (Turk lirasi kodu) BILEREK yok: "Try again" / "TRY AGAIN" metinleriyle cakisir. Rakam
        // yasagi zaten "TRY 49" gibi bir fiyati yakalar.
        val currency = Regex("[\$€£₺¥]|\\b(USD|EUR|TL|GBP)\\b", RegexOption.IGNORE_CASE)
        val percent = Regex("\\d\\s*%|%\\s*\\d|percent|yuzde|yüzde")
        val discount = Regex("discount|indirim|cheaper|ucuz|savings?|tasarruf|% off|\\bfree\\b|bedava", RegexOption.IGNORE_CASE)
        // Debug paneli metinleri (translatable=false) yalnizca gelistirici etiketidir; oyuncuya gorunmez.
        (en + tr).filter { it.translatable }.forEach { e ->
            val plain = FORMAT_ARG.replace(e.text, "")
            assertFalse("${e.name}: para birimi", currency.containsMatchIn(plain))
            assertFalse("${e.name}: yuzde", percent.containsMatchIn(plain))
            assertFalse("${e.name}: indirim dili", discount.containsMatchIn(plain))
            assertFalse("${e.name}: gomulu rakam ('$plain')", plain.any { it.isDigit() })
        }
    }

    @Test
    fun debugScenarioStringsAreNotTranslatableAndNeverInTurkish() {
        val debug = en.filter { it.name.startsWith("store_debug_sc_") }
        assertEquals("her senaryonun bir dugme etiketi olmali", FakeBilling.Scenario.entries.size, debug.size)
        assertTrue(debug.none { it.translatable })
    }
}
