package com.miniappfactory.frontlinedefender.ads

import com.miniappfactory.frontlinedefender.game.ads.AdIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * REKLAM KIMLIKLERI — kod, manifest ve build dosyasi AYNI SEYI SOYLEMELI.
 *
 * AdMob App ID'si UC ayri yerde yasiyor: `AdIds` sabitleri, `AndroidManifest`
 * meta-data'si ve `build.gradle.kts` placeholder'i. Manifest degeri DERLEME
 * ZAMANINDA sabitlendigi icin calisma zamaninda dallanamaz; ucu elle hizali
 * tutulmak zorunda.
 *
 * Bu depoda "ayni deger iki yerde, biri bayatlar" hatasi defalarca cikti
 * (actLabelRes, BOSS_LEVEL_IDS, L19 pad sayisi, guclendirici cipi, hava
 * taarruzu tavani). Reklam kimliginde bedeli daha agir: yanlis App ID ile
 * release cikmak reklamlarin HIC dolmamasi, ters yonu ise gelistiricinin kendi
 * canli reklamini gormesi demek.
 */
class AdIdsConsistencyTest {

    private val publisher = "ca-app-pub-8582550349019790"

    private fun readUp(relative: String): String =
        listOf(File(relative), File("app/$relative"), File("../app/$relative"), File("../$relative"))
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("$relative bulunamadi (cwd=${File(".").absolutePath})")

    /**
     * BIRIM TESTLERI DEBUG VARYANTINDA KOSAR, yani burada `USE_TEST_ADS` true
     * olmali. False cikarsa cihazda test eden kisi GERCEK reklam gorur — AdMob
     * bunu gecersiz trafik sayar ve yaptirimi hesap askiya almaya kadar gider.
     */
    @Test
    fun debugBuildsAlwaysUseGoogleTestAds() {
        assertTrue(
            "debug varyantinda USE_TEST_ADS false — gelistirici kendi canli " +
                "reklamina tiklayabilir, bu bir HESAP RISKIDIR",
            AdIds.USE_TEST_ADS
        )
        assertTrue(
            "debug'da yayinlanan interstitial kimligi dondu: ${AdIds.interstitialAdUnitId()}",
            AdIds.interstitialAdUnitId().startsWith("ca-app-pub-3940256099942544")
        )
        assertTrue(
            "debug'da yayinlanan rewarded kimligi dondu: ${AdIds.rewardedAdUnitId()}",
            AdIds.rewardedAdUnitId().startsWith("ca-app-pub-3940256099942544")
        )
    }

    @Test
    fun productionIdsAreWellFormedAndBelongToTheSamePublisher() {
        mapOf(
            "app" to AdIds.PRODUCTION_APP_ID,
            "interstitial" to AdIds.PRODUCTION_INTERSTITIAL_AD_UNIT_ID,
            "rewarded" to AdIds.PRODUCTION_REWARDED_AD_UNIT_ID
        ).forEach { (name, id) ->
            assertTrue("$name kimligi bos", id.isNotBlank())
            assertTrue("$name kimligi baska bir yayinciya ait: $id", id.startsWith(publisher))
        }
        // App ID '~', birim kimlikleri '/' ayirici kullanir. Karistirmak
        // AdMob'da SESSIZ bir no-fill uretir — hata degil, bos reklam.
        assertTrue(
            "App ID '~' tasimali: ${AdIds.PRODUCTION_APP_ID}",
            AdIds.PRODUCTION_APP_ID.contains('~')
        )
        listOf(
            AdIds.PRODUCTION_INTERSTITIAL_AD_UNIT_ID,
            AdIds.PRODUCTION_REWARDED_AD_UNIT_ID
        ).forEach {
            assertTrue("birim kimligi '/' tasimali: $it", it.contains('/'))
        }
        assertTrue(
            "interstitial ve rewarded AYNI birim — iki yerlesimin performansi tek " +
                "ortalamanin arkasinda kaybolur",
            AdIds.PRODUCTION_INTERSTITIAL_AD_UNIT_ID != AdIds.PRODUCTION_REWARDED_AD_UNIT_ID
        )
    }

    @Test
    fun theManifestPlaceholderIsFedTheSameAppIdsAsTheCode() {
        assertTrue(
            "manifest artik placeholder kullanmali, sabit deger DEGIL",
            readUp("src/main/AndroidManifest.xml").contains("android:value=\"\${admobAppId}\"")
        )
        val gradle = readUp("build.gradle.kts")
        assertTrue(
            "release placeholder'i AdIds.PRODUCTION_APP_ID ile ayni olmali " +
                "(${AdIds.PRODUCTION_APP_ID})",
            gradle.contains(AdIds.PRODUCTION_APP_ID)
        )
        assertTrue(
            "debug placeholder'i TEST_APP_ID ile ayni olmali (${AdIds.TEST_APP_ID})",
            gradle.contains(AdIds.TEST_APP_ID)
        )
    }

    /** Koddaki manifest kopyasi da ayni anahtardan gecmeli. */
    @Test
    fun manifestAppIdMirrorFollowsTheSameSwitch() {
        assertEquals(AdIds.TEST_APP_ID, AdIds.manifestAppId)
    }
}
