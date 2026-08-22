package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * OLU BUILD PAD KILIDI — "uzerine kurdugum kule bir seye ates edebiliyor mu?"
 * ===========================================================================
 *
 * NEDEN VAR
 * ---------
 * Cihaz geri bildirimi: *"level 1'de bile gelen adamlari olduremedim."*
 * Olcum sebebi buldu: bolum 1'de 10 build pad'in 5'i (1, 3, 5, 7, 9) o
 * bolumde AKTIF olan rotaya 194-444 ref-px uzaktaydi, Gatling menzili ise
 * 150. Baslangic tedariki 80 = TEK kule. Yani oyuncu %50 ihtimalle butun
 * parasini hicbir seye ates etmeyen bir kuleye yatiriyordu; geri kazanma yolu
 * yok, ve bunu ogreten hicbir sey yok. Toplam 34 olu pad-bolum ornegi vardi,
 * 24'u ilk 10 bolumde.
 *
 * BU HATA NEDEN 22 BOLUM BOYUNCA TESTTEN KACTI (iki hata ust uste)
 * ----------------------------------------------------------------
 *  1. Eski `GeometryTestSupport.padToNearestRoute` mesafeyi TUM rotalarin
 *     minimumu olarak oluyordu. Harita 1 pad 3 icin ikinci kolun 116'sini
 *     goruyor, bolum 1'de gecerli olan 444'u HIC gormuyordu — oysa ikinci kol
 *     bolum < [GameConfig.ALT_ROUTE_FIRST_LEVEL] iken motorda hic kullanilmaz.
 *  2. Esik olarak 320 (SLOW kademe-2 menzili) kullanilmisti. Bu deger HICBIR
 *     bolumde gecerli degil: oyuncu kuleyi kurmadan yukseltemez, dolayisiyla
 *     kurulum aninda erisilebilen en genis menzil kademe-1'inkidir.
 *
 * Bu dosya iki hatayi da kapatir: mesafe AKTIF rotaya, esik O BOLUMDE KILIDI
 * ACIK kulelerin EN BUYUK KADEME-1 menziline gore olculur. Iki deger de
 * `GameConfig`ten TURETILIR; burada sabit menzil yazili degildir.
 *
 * Olcum ve karar gerekcesi: docs/PAD_COVERAGE_REPORT.md.
 */
class PadReachabilityPerLevelTest {

    /**
     * KURAL: Her kampanya bolumunde, o bolumde GORUNUR olan her build pad, o
     * bolumde KILIDI ACIK kulelerin EN BUYUK kademe-1 menzili icinde, o
     * bolumde AKTIF olan rotalardan en az birine erisebilmelidir.
     *
     * Bu test kirilirsa yapilacak sey pad'i gizlemek DEGIL, once soruyu
     * sormaktir: menzil mi degisti, geometri mi, yoksa bir bolum baska bir
     * haritaya mi tasindi? Gizleme (`GameConfig.OUT_OF_RANGE_PADS`) ancak
     * pad'in gercekten kullanilamaz oldugu dogrulandiktan sonra gelir.
     */
    @Test
    fun everyVisiblePadCanReachAnActiveRouteWithAnUnlockedTower() {
        val failures = mutableListOf<String>()
        GameConfig.CAMPAIGN.forEach { spec ->
            val maxRange = GeometryTestSupport.maxUnlockedLevel1Range(spec.levelId)
            LevelData.forMapId(spec.mapId).buildSpots
                .filter { it.id !in spec.disabledPadIds }
                .forEach { pad ->
                    val d = GeometryTestSupport.padToActiveRoute(
                        pad.normX, pad.normY, spec.mapId, spec.levelId
                    )
                    if (d > maxRange) {
                        failures += "blm ${spec.levelId} (harita ${spec.mapId}) pad ${pad.id}: " +
                            "aktif rotaya ${d.toInt()} ref-px, en uzun acik menzil ${maxRange.toInt()}"
                    }
                }
        }
        assertTrue(
            "MENZIL DISI GORUNUR PAD — uzerine kurulan kule hicbir seye ates edemez:\n" +
                failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    /**
     * TERS YON: gizlenen her pad GERCEKTEN menzil disi olmali mi? Hayir —
     * Act II'de krater kisiti (LEVEL_DESIGN.md F.3) calisan pad'leri de
     * bilerek kapatir, o bir BULMACA. Ama **Act I'de gizlemenin tek mesru
     * gerekcesi menzil disi olmaktir**: orada calisan bir pad'i saklamak
     * oyuncudan sebepsiz secenek calmaktir.
     */
    @Test
    fun actOneHidesOnlyPadsThatAreActuallyOutOfRange() {
        val wrongful = mutableListOf<String>()
        GameConfig.CAMPAIGN.filter { it.act == 1 }.forEach { spec ->
            val maxRange = GeometryTestSupport.maxUnlockedLevel1Range(spec.levelId)
            LevelData.forMapId(spec.mapId).buildSpots
                .filter { it.id in spec.disabledPadIds }
                .forEach { pad ->
                    val d = GeometryTestSupport.padToActiveRoute(
                        pad.normX, pad.normY, spec.mapId, spec.levelId
                    )
                    if (d <= maxRange) {
                        wrongful += "blm ${spec.levelId} (harita ${spec.mapId}) pad ${pad.id}: " +
                            "aktif rotaya ${d.toInt()} ref-px <= menzil ${maxRange.toInt()} " +
                            "ama gizlenmis"
                    }
                }
        }
        assertTrue(
            "ACT I'DE CALISAN PAD GIZLENMIS — Act I'de gizlemenin tek gerekcesi " +
                "menzil disi olmaktir:\n" + wrongful.joinToString("\n"),
            wrongful.isEmpty()
        )
    }

    /**
     * Act I'de oyuncuya kalan secenek sayisinin tabani.
     *
     * Bolum 1'de 10 pad'in 5'i gizlenince geriye 5 secenek kaliyor. Bu
     * KASITLI: 5 gercek secenek bir ogretici icin fazlasiyla yeterli, 5 tuzak
     * ise ogreticiyi kumara cevirir. Karar da gercek kaliyor — kalan pad'ler
     * rotanin farkli evrelerini goruyor (kapsama %12-24.8, en iyi ile en kotu
     * arasinda ~2 kat fark). Taban 5'in ALTINA inerse secim anlamini yitirir
     * ve bu bir tasarim karari olarak yeniden ele alinmalidir.
     *
     * Act II'nin krater kisiti kendi D1/D2 kurallarina tabi
     * (`CampaignTableTest`); burada Act I icin ayri taban tutuluyor cunku
     * sebep de, kabul edilebilir minimum da farkli.
     */
    @Test
    fun actOneAlwaysLeavesAtLeastFiveUsablePads() {
        GameConfig.CAMPAIGN.filter { it.act == 1 }.forEach { spec ->
            val remaining = LevelData.forMapId(spec.mapId).buildSpots
                .count { it.id !in spec.disabledPadIds }
            assertTrue(
                "bolum ${spec.levelId} (harita ${spec.mapId}): gizleme sonrasi " +
                    "yalnizca $remaining pad kaldi — Act I tabani 5",
                remaining >= 5
            )
        }
        // Ogretici bolumun tam sayisi ayrica pinlenir: bu deger sessizce
        // degisirse ilk oturum deneyimi degismis demektir.
        val level1Remaining = LevelData.forMapId(1).buildSpots
            .count { it.id !in GameConfig.levelSpec(1).disabledPadIds }
        assertEquals("bolum 1'de kalan kullanilabilir pad sayisi", 5, level1Remaining)
    }

    /**
     * YALNIZ-DESTEK PAD'LER — yasak degil, IZLENIYOR.
     *
     * Bu pad'ler aktif rotaya erisiyor ama yalnizca Frost Field'in (SLOW)
     * menzilinde. Frost Field 3 hasar verir, yani oraya kurulan kule dusmani
     * yavaslatir fakat pratikte OLDURMEZ. Bu gecerli bir tasarim durumu
     * (destek mevzisi) ama sessizce YAYILMAMALI: liste donduruldu, buyurse
     * test kirilir ve karar yeniden gozden gecirilir.
     *
     * Esik `GameConfig`ten turetilir: SLOW haric acik kulelerin en buyuk
     * kademe-1 menzili (L1-2: 150, L3-6: 175, L7+: 250).
     */
    @Test
    fun supportOnlyPadsMatchTheFrozenList() {
        val expected: Map<Int, Set<Int>> = mapOf(
            // v4 rota merkezlemesi pad 3'u 274 -> 262 ref-px'e getirdi: artik
            // gizli degil ama yalnizca Frost Field menzilinde -> destek mevzisi.
            5 to setOf(3, 11, 12),// harita 05 · 175 < d <= 270
            // v5 (2026-08-21): harita 06'nin rotasi sanattan yeniden uretildi.
            // Eski rota bunker agzindan 98 ref-px sapmis bir noktadan basliyor
            // ve spawn cevresinde cimin ustunden geciyordu; yeni rota yol
            // agzindan cikip koridorun ortasindan gidiyor. Pad 1 bu yuzden
            // hasar veren menzilin (175) disina, Frost Field bandina (270)
            // kaydi: 175 < d <= 270. Gecerli bir DESTEK mevzisi, tuzak degil —
            // ustune kurulan kule dusmani yavaslatir. Kume bir eleman buyudu.
            6 to setOf(1)         // harita 06 · 175 < d <= 270
            // ---- 2026-08-22: `250 < d <= 270` BANDI TAMAMEN BOSALDI ----
            //
            // Silinen girdiler: 7=[9], 10=[4,6], 13=[4], 23=[6], 26=[9],
            // 36=[9], 44=[4,6], 46=[9], 54=[4,6] — hepsi UC fiziksel pad'e
            // dayaniyordu (harita 05 pad 3, harita 07 pad 9, harita 10 pad 4/6)
            // ve ayni pad'ler farkli bolumlerde tekrar tekrar sayiliyordu.
            //
            // O pad'ler HIC BIR bolumde hasar veren bir kule kabul etmiyordu:
            // en uzun hasar menzili Fuze'nin 250'si, mesafeler 252,7 / 262,6 /
            // 265,5 / 267,7 idi. Yukaridaki eski yorum bunlari "gecerli DESTEK
            // mevzisi, tuzak degil" diye savunuyordu; kullanici 2026-08-22'de
            // bunun KAZA oldugunu soyledi. Dort pad en yakin rota noktasina
            // dogru 7,7-22,7 ref-px kaydirildi -> hepsi 245,0 (Fuze esiginin
            // 5 px altinda). Maske sinifi degismedi (3 = kayalik).
            //
            // KALAN IKI GIRDI KAZA DEGIL ve silinmemeli: onlar `175 < d <= 270`
            // bandinda ve sebebi mesafe degil KILIT — Fuze Rampasi L7'de
            // aciliyor, L5/L6'da en uzun hasar menzili Agir Top'un 175'i. Ayni
            // pad'ler L7'den itibaren hasar veren bir kule kabul ediyor, yani
            // bunlar gecici bir durum; kalici bir olu mevzi degil.
        )

        val measured = sortedMapOf<Int, Set<Int>>()
        GameConfig.CAMPAIGN.forEach { spec ->
            val anyRange = GeometryTestSupport.maxUnlockedLevel1Range(spec.levelId)
            val damagingRange = GeometryTestSupport.maxUnlockedDamagingLevel1Range(spec.levelId)
            val pads = LevelData.forMapId(spec.mapId).buildSpots
                .filter { it.id !in spec.disabledPadIds }
                .filter { pad ->
                    val d = GeometryTestSupport.padToActiveRoute(
                        pad.normX, pad.normY, spec.mapId, spec.levelId
                    )
                    d > damagingRange && d <= anyRange
                }
                .map { it.id }
                .toSet()
            if (pads.isNotEmpty()) measured[spec.levelId] = pads
        }

        assertEquals(
            "yalniz-destek pad kumesi degisti (bolum -> pad id). Bu pad'lere kurulan " +
                "kulelerden yalnizca Frost Field yola erisir ve o da 3 hasar verir. " +
                "docs/PAD_COVERAGE_REPORT.md 2. tabloyu guncelle.",
            expected, measured.toMap()
        )
    }
}
