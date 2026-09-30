package com.miniappfactory.frontlinedefender.game.billing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `BillingManager` KAYNAK SOZLESMESI — DAVRANIS TESTI DEGIL, NOBETCI.
 *
 * `BillingManager` Play SDK'sine baglidir; gercek Play'e/cihaza KARSI JVM'de calistirilamaz. Onun
 * icindeki KARARLAR (PurchasePolicy/PurchaseFulfiller/defter/StoreSession) davranis testleriyle
 * kilitlidir. Burada yalnizca SDK yapistiricisinin, denetimde bulunan iki hatayi geri getirecek
 * SEKILLERI kaynakta aranir. Bu bir metin taramasidir; yeniden adlandirmayla kirilabilir ve
 * kirilirsa gercek bir gerilemeyi degil TESTI guncellemek gerektigini gosterebilir — ama sessiz
 * gerilemeden iyidir (bu depoda HudRecompositionScopeTest ile ayni desen).
 */
class BillingManagerSourceContractTest {

    private val source: String by lazy {
        val relative = "src/main/java/com/miniappfactory/frontlinedefender/game/billing/BillingManager.kt"
        val file = listOf(File(relative), File("app/$relative"), File("../app/$relative"))
            .firstOrNull { it.isFile } ?: error("BillingManager.kt bulunamadi (cwd=${File(".").absolutePath})")
        // Yorumlar cikarilir: KDoc'taki "false yazilmaz" gibi cumleler kod sanilmasin.
        file.readText(Charsets.UTF_8)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\n]*"), "")
    }

    /** F4: geri yukleme yapiskan eski-alici isaretini ASLA silmez. */
    @Test
    fun theRemoveAdsCallbackIsOnlyEverCalledWithTrue() {
        assertFalse(
            "onRemoveAdsChanged(false) yazilmamali (yapiskan isaret)",
            Regex("onRemoveAdsChanged\\s*\\(\\s*false").containsMatchIn(source),
        )
        assertFalse(
            "reklamsiz hakkini Play sorgusundan turetip yazmak yok",
            Regex("onRemoveAdsChanged\\s*\\(\\s*(?!true)[^)]").containsMatchIn(source),
        )
        assertTrue(
            "eski alici onayi hala isaret yaziyor",
            Regex("onRemoveAdsChanged\\s*\\(\\s*true").containsMatchIn(source),
        )
    }

    /** F5: tek baglanti ucusu — `startConnection` tek yerde ve tek-ucus kilidinin ardindan. */
    @Test
    fun startConnectionIsCalledOnceAndOnlyAfterTheSingleFlightGate() {
        assertEquals(1, Regex("startConnection\\(").findAll(source).count())
        val gate = source.indexOf("session.beginConnect()")
        val start = source.indexOf("client.startConnection(")
        assertTrue("beginConnect kapisi bulunamadi", gate >= 0)
        assertTrue("startConnection, tek-ucus kapisindan ONCE", gate < start)
    }

    /** F5: ITEM_ALREADY_OWNED kurtarmaya baglanmis (dinleyicide ve akis sonucunda). */
    @Test
    fun itemAlreadyOwnedIsRoutedToRecovery() {
        val sites = Regex("ITEM_ALREADY_OWNED").findAll(source).map { it.range.first }.toList()
        assertTrue("iki yerde ele alinmali (dinleyici + launchBillingFlow sonucu)", sites.size >= 2)
        sites.forEach { at ->
            val tail = source.substring(at, minOf(source.length, at + 260))
            assertTrue("ITEM_ALREADY_OWNED sonrasi recover() cagrilmali: $tail", tail.contains("recover("))
        }
    }

    /** Tuketme/onay TEK yerde (gercek uygulayici): karar hatti disina kacan bir consume yok. */
    @Test
    fun consumeAndAcknowledgeLiveOnlyInsideTheFinisher() {
        assertEquals(1, Regex("consumeAsync\\(").findAll(source).count())
        assertEquals(1, Regex("acknowledgePurchase\\(").findAll(source).count())
        assertTrue(source.contains("PurchaseFulfiller("))
        assertFalse("eski kosulsuz consume(purchase) dali geri gelmemeli", source.contains("consume(purchase)"))
    }

    /** Urun sorgusu her yenilemede ve magaza acilisinda (ensureProducts) yeniden denenebilir. */
    @Test
    fun refreshAndConnectRetryTheProductQuery() {
        val refresh = source.substringAfter("fun refresh()").substringBefore("fun launchPurchase")
        assertTrue("refresh() ensureProducts() cagirmali", refresh.contains("ensureProducts()"))
        assertTrue("baglanti kurulunca da sorulmali", source.substringAfter("onBillingSetupFinished").contains("ensureProducts()"))
        assertTrue("ensureProducts public olmali (magaza acilisi cagirir)", source.contains("fun ensureProducts()"))
    }

    /** Debug hatti yalnizca debug'da ve GERCEK fulfiller kurulusuyla. */
    @Test
    fun theDebugPathIsGatedAndUsesTheSharedFulfillerFactory() {
        assertTrue(source.substringAfter("fun debugSubmit").substringBefore("}").contains("FakeBilling.enabled"))
        assertTrue(source.substringAfter("fun debugRun").substringBefore("}").contains("FakeBilling.enabled"))
        assertTrue(source.contains("FakeBilling.newDebugFulfiller("))
    }
}
