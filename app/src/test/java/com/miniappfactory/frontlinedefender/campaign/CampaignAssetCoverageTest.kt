package com.miniappfactory.frontlinedefender.campaign

import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.ui.ActIntro
import com.miniappfactory.frontlinedefender.game.ui.levelThumbRes
import com.miniappfactory.frontlinedefender.game.ui.storyActBodyRes
import com.miniappfactory.frontlinedefender.game.ui.storyActLabelRes
import com.miniappfactory.frontlinedefender.game.ui.storyActTitleRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * KAMPANYA BUYURSE VARLIKLAR SESSIZCE YANLIS OLMASIN
 * ===========================================================================
 *
 * BULGU (2026-09-02, kampanya genisletme denetimi): kampanya uzunluguna bagli
 * birkac tablo `coerceIn` ile **SESSIZCE KIRPIYOR**. Bugun zararsiz — 55 bolum,
 * 55 kupur, 5 perde, 5 hikaye seti; hicbir kirpma tetiklenmiyor. Ama biri
 * [EconomyConfig.CAMPAIGN_LEVELS] veya [ActIntro.ACT_COUNT] degerini artirirsa:
 *
 *  · bolum 56 **sessizce L55'in kupurunu** gosterir,
 *  · perde 6 **sessizce Perde V'in hikayesini ve etiketini** gosterir,
 *
 * ve **hicbir sey kirilmaz**. Oyun yanlis icerikle CALISIR, testler yesil
 * yanar, yapi Play'e gider. Bu, bu deponun en pahali hata sinifidir: hata
 * degil, SESSIZ YANLISLIK.
 *
 * ⚠ NEDEN CALISMA ANINDA COKME EKLENMEDI. `coerceIn`i `error()`e cevirmek
 * cazip ama YANLIS olurdu: uretimde cokmek, yanlis bir kupur gostermekten
 * DAHA KOTUDUR. Kirpmalar savunma amaclidir ve oyle kalmali. Sozlesme
 * DERLEME ZAMANINDA kurulur — `ArtSurfaceContractTest`in oksuz varlik nobeti
 * ile ayni desen: veri ile tablo ayrisirsa BUILD kirmizi yanar, oyuncu hicbir
 * sey gormez.
 *
 * NOT — `thumb_level_*` `ArtSurfaceContractTest` SOZLESMESINDE DEGIL: o test
 * yalnizca `ui_` onekli varliklari tariyor. Yani bugune kadar kupurlerin
 * kampanyayi kapsadigini KONTROL EDEN HICBIR SEY YOKTU.
 */
class CampaignAssetCoverageTest {

    /**
     * Her bolumun KENDI kupuru olmali.
     *
     * Kirpma tetiklenirse son iki bolum ayni gorseli paylasir; test bunu
     * dogrudan yakalar (kaynak id'leri esitse kirpma olmustur).
     */
    @Test
    fun everyCampaignLevelHasItsOwnThumbnail() {
        val ids = (1..EconomyConfig.CAMPAIGN_LEVELS).map { levelThumbRes(it) }
        assertEquals(
            "kupur sayisi kampanya uzunluguyla ayni degil — `coerceIn` sessizce kirpiyor. " +
                "Yeni bolum eklendiyse `UiStrings.LEVEL_THUMBS` de buyumeli.",
            EconomyConfig.CAMPAIGN_LEVELS,
            ids.distinct().size,
        )
    }

    /**
     * SON BOLUM SINIRI — kirpmanin ilk goruldugu yer burasidir.
     *
     * Kampanya bir bolum buyudugunde ama kupur eklenmediginde,
     * `levelThumbRes(N)` ve `levelThumbRes(N-1)` ayni degeri doner. Ayri bir
     * test olarak duruyor cunku hata mesaji ozel: sorunun NEREDE oldugunu
     * soyluyor.
     */
    @Test
    fun theLastTwoLevelsDoNotShareArtwork() {
        val n = EconomyConfig.CAMPAIGN_LEVELS
        assertNotEquals(
            "L$n ve L${n - 1} ayni kupuru gosteriyor — kampanya buyudu ama " +
                "`UiStrings.LEVEL_THUMBS` buyumedi.",
            levelThumbRes(n - 1),
            levelThumbRes(n),
        )
    }

    /**
     * Her perdenin KENDI hikaye seti olmali (etiket, baslik, govde).
     *
     * `storyAct*Res` fonksiyonlari `coerceIn(1, ACT_COUNT)` ile kirpiyor ve
     * `else` dali her zaman BESINCI perdeyi donduruyor. ACT_COUNT artarsa
     * altinci perde sessizce besincinin metnini gosterir.
     */
    @Test
    fun everyActHasItsOwnStorySet() {
        val acts = 1..ActIntro.ACT_COUNT
        listOf(
            "etiket" to acts.map { storyActLabelRes(it) },
            "baslik" to acts.map { storyActTitleRes(it) },
            "govde" to acts.map { storyActBodyRes(it) },
        ).forEach { (isim, ids) ->
            assertEquals(
                "perde $isim dizeleri benzersiz degil — yeni perde eklendiyse " +
                    "`strings_story.xml` ve `ActIntroOverlay` esleme fonksiyonlari da buyumeli",
                ActIntro.ACT_COUNT,
                ids.distinct().size,
            )
        }
    }

    /**
     * Perde/bolum matematigi tutarli mi: perde sayisi x perde basina bolum,
     * kampanya uzunlugunu TAM olarak vermeli.
     *
     * Bu, yukaridaki iki kapsam kuralinin on kosulu. Uc sayidan biri elle
     * degistirilirse (ornegin CAMPAIGN_LEVELS 66 yapilip ACT_COUNT 5
     * birakilirsa) once burasi patlar ve sebep NET olur.
     */
    @Test
    fun actCountTimesLevelsPerActEqualsTheCampaignLength() {
        assertEquals(
            "ACT_COUNT x LEVELS_PER_ACT != CAMPAIGN_LEVELS — perde matematigi bozuk",
            EconomyConfig.CAMPAIGN_LEVELS,
            ActIntro.ACT_COUNT * ActIntro.LEVELS_PER_ACT,
        )
    }

    /**
     * Her bolum bir harita ve gecerli bir perde cozmeli — kampanya tablosunun
     * kendisi kirpma yapmiyor mu?
     */
    @Test
    fun everyCampaignLevelResolvesToARealMapAndAct() {
        (1..EconomyConfig.CAMPAIGN_LEVELS).forEach { lv ->
            val mapId = GameConfig.levelSpec(lv).mapId
            assertTrue("L$lv gecersiz harita $mapId", mapId in 1..11)
            val act = (lv - 1) / ActIntro.LEVELS_PER_ACT + 1
            assertTrue("L$lv gecersiz perde $act", act in 1..ActIntro.ACT_COUNT)
        }
    }
}
