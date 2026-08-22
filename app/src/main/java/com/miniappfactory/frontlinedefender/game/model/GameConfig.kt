package com.miniappfactory.frontlinedefender.game.model

import kotlin.math.hypot

/**
 * Centralized game balance configuration.
 * All balance values, tower specs, enemy specs, and wave definitions are stored here.
 */
object GameConfig {
    const val INITIAL_BASE_LIVES = 20
    const val INITIAL_GOLD = 150

    // Game loop parameters
    const val PREPARATION_TIME_SECONDS = 10
    const val BASE_REACHED_PENALTY_LIVES = 1

    // ------------------------------------------------------------------------
    // Faz 3 — RENDER / ETKILESIM SABITLERI
    //
    // Bunlar DENGE degeri DEGILDIR. Hasar / menzil / HP / maliyet sayilarina
    // dokunulmadi. Buradaki her deger "ekranda ne kadar yer kaplar" ya da
    // "sprite hangi yone bakiyor" sorusunun cevabi ve renderer'a GOMULMEZ.
    //
    // Tum gorsel boyutlar 1920x1080 REFERANS TUVALINDE tanimlidir
    // (DECISIONS.md B3). Cizim aninda olcek = oynanis dikdortgeninin
    // genisligi / REFERENCE_WIDTH.
    // ------------------------------------------------------------------------
    const val REFERENCE_WIDTH = 1920f
    const val REFERENCE_HEIGHT = 1080f

    /** bg_level_01.webp 1920x1081 -> 1.7761. Letterbox hesabi bunu kullanir. */
    const val MAP_ASPECT_RATIO = 1920f / 1081f

    // ------------------------------------------------------------------------
    // SANAT ILE OYNANIS AYNI KOORDINAT UZAYINDA — P0 DUZELTMESI
    //
    // ESKI DAVRANIS (`SHAKE_OVERSCAN_REF_PX = 10`): harita bitmap'i oynanis
    // dikdortgeninden 10 ref-px TASIRILARAK, yani GERILEREK ciziliyordu:
    //
    //   sanatX = (sol - tasma) + nx * (genislik + 2*tasma)
    //          = sol + nx*genislik + tasma * (2*nx - 1)
    //
    // Yani boyali yol ile dusmanin YURUDUGU rota arasinda merkezde 0, sol/sag
    // kenarda 10 ref-px, kosede 10*sqrt(2) = 14,1 ref-px SISTEMATIK kayma
    // vardi (yol yarim genisliginin ~%22'si). Kullanicinin cihazda gordugu
    // "yoldan gelmeyen askerler" bulgusunun kalan payi buradan geliyordu:
    // rota dogru yerdeydi, HARITA yanlis yerde ciziliyordu. Ve kayma
    // SUREKLIYDI — sarsinti olmayan karelerde de.
    //
    // YENI DAVRANIS: harita TAM oynanis dikdortgenine cizilir ([mapArtRect]),
    // kayma sifirdir. Sarsintida kenarin acilmamasi icin gereken tasma artik
    // GERME ile degil, bitmap'in en dis piksel siralarinin disari dogru
    // SIVANMASI ile saglanir ([mapEdgeBleedPx], GameCanvas.drawMapEdgeBleed).
    // Sivama sanat icerigini KAYDIRMAZ: dikdortgenin ICI birebir yerinde
    // kalir, yalnizca disina 1 piksellik kenar rengi uzatilir.
    // ------------------------------------------------------------------------

    /**
     * Oynanis dikdortgeninin DISINA sivanan kenar bandinin genisligi
     * (referans tuval px). Sarsintida letterbox/ekran kenarinda koyu serit
     * acilmasini onler.
     */
    const val MAP_EDGE_BLEED_REF_PX = 10f

    /**
     * EKRAN SARSINTISI — motorun ve cizimin ORTAK sabitleri.
     *
     * Buraya tasindi cunku kenar sivamasinin YETERLI olmasi sarsintinin en
     * buyuk genligine baglidir; iki sayi ayri yerlerde dururken "sivama
     * sarsintiyi kapatiyor mu" sorusu test edilemezdi
     * (bkz. `MapArtAlignmentTest.edgeBleedCoversTheWorstCaseShake`).
     *
     * Genlik HAM px'tir (renderScale ile carpilmaz) — bu bilincli DEGIL,
     * mevcut davranisin oldugu gibi korunmasidir; degistirmek tum cihazlarda
     * oyun hissini degistirir ve ayri bir karardir.
     */
    const val SHAKE_INTENSITY_RAMP_PER_SECOND = 15f
    const val SHAKE_MAX_INTENSITY_PX = 10f

    /** Sarsintinin bir eksende ulasabilecegi EN BUYUK sapma (px). */
    const val SHAKE_MAX_AMPLITUDE_PX = SHAKE_MAX_INTENSITY_PX / 2f

    /**
     * Sarsinti genligine gore emniyet payi. Tam esitlik (1,0) matematiksel
     * olarak yeterli olurdu ama float yuvarlamasi kenarda bir piksellik kil
     * cizgi birakabilir; sivama band genisligi ucuz, kil cizgi degil.
     */
    const val MAP_EDGE_BLEED_SAFETY = 1.5f

    /**
     * Kenar sivamasinin px genisligi. Referans tuvale gore olceklenir ama
     * HICBIR ZAMAN sarsintinin en buyuk genliginin altina inmez — kucuk
     * ekranlarda (renderScale ~0,30) yalniz olcekli deger 3 px kalir ve
     * 5 px'lik sarsinti bandi acardi.
     */
    fun mapEdgeBleedPx(renderScale: Float): Float = maxOf(
        MAP_EDGE_BLEED_REF_PX * renderScale,
        SHAKE_MAX_AMPLITUDE_PX * MAP_EDGE_BLEED_SAFETY
    )

    /**
     * ------------------------------------------------------------------------
     * UST HUD SERIDI ILE HARITANIN ILISKISI — **OLCULUR, VARSAYILMAZ**
     * ------------------------------------------------------------------------
     * Eskiden burada tek bir sabit vardi (`MAP_SAFE_TOP_FRAC = 0.10`) ve KDoc
     * "haritanin ust %10'u dekoratiftir, en ustteki pad normY=0.1655" diyordu.
     * **Iki iddia da yanlisti.** 11 haritanin gercek olcumu (bkz. bu dosyadaki
     * [mapSafeTopFrac] ve `MapLayoutSafetyTest`):
     *
     *   harita 10 -> en ust pad normY = 0,0691
     *   harita 06 -> en ust pad normY = 0,0878
     *   harita 04 -> en ust pad normY = 0,1121   (kullanicinin oynadigi harita)
     *
     * Yani "ust %10 bostur" varsayimi haritanin YARISI icin yanlis, ve harita
     * 10'da pad merkezi bandin BILE ustunde. Sonuc cihazda goruldu (Galaxy S8,
     * 740x360 dp, HUD 56 dp): harita 4'te ust siradaki pad'lerin dokunma
     * dairesi opak HUD seridinin altinda kaliyor ve **dokunus almiyordu.**
     *
     * ESKI FORMULUN HATASI: kaydirma miktari SABIT bir orandi
     * (`fieldTop = hudInset - 0.10 * fh`), yani HUD'in olculen yuksekligi ile
     * haritanin gercek geometrisi hicbir yerde karsilastirilmiyordu.
     * 360 dp'lik ekranda 0,10 * 338 = 34 dp kaydirma vs 56 dp HUD -> 22 dp
     * ortulme.
     *
     * YENI KURAL — kaydirma HARITANIN KENDI GEOMETRISINDEN turer:
     *   kaydirma <= (en ust pad normY) - (pad temizlik payi)
     *   kaydirma <= (yolun en ust normY) - (dusman temizlik payi)
     * ve deger **NEGATIF olabilir**: o zaman harita HUD'in tamamen altina
     * sigacak sekilde KUCULTULUR (letterbox). Kucultmek gorseldir;
     * ulasilamayan pad OYNANAMAZ.
     */
    /** Kaydirmanin ust siniri — bu kadarindan fazlasi haritayi anlamsiz buyutur. */
    const val MAP_SAFE_TOP_FRAC_MAX = 0.20f

    /** Alt sinir: harita en fazla bu kadar KUCULTULUR (letterbox emniyeti). */
    const val MAP_SAFE_TOP_FRAC_MIN = -0.25f

    /** HUD seridi olculemezse kullanilan varsayilan (HUDOverlay ~56 dp). */
    const val HUD_TOP_INSET_DP = 56f

    // ------------------------------------------------------------------------
    // ALT CEKMECE ORTMESI — "bastigim pad'i acilan panel yuttu"
    //
    // OLCUM (`MapLayoutSafetyTest.bottomDrawerOcclusionIsMeasuredAndReported`):
    // TowerBuildBar (63 dp) acilinca 740x360 dp'de harita 2'de 4 pad, harita
    // 4'te 3 pad cekmecenin altinda kaliyor. Oyuncu pad'e basiyor, acilan
    // panel hem pad'i hem BIRAKMA ONIZLEMESINI kapatiyor — yani neye
    // kurdugunu goremiyor. Klasik "parmak/panel hedefi ortuyor" durumu.
    //
    // NEDEN ALANI KUCULTMUYORUZ: 63 dp daha kucultmek 740x360'ta yuksekligin
    // %20'sini goturur ve bu bedel KALICI olurdu — cekmece ise GECICI, sadece
    // secim varken acik. Bunun yerine secilen hedef cekmecenin USTUNDE
    // tekrarlanir (ofsetli hayalet + bagli cizgi), yani bilgi tasindi, alan
    // degil.
    //
    // Yukseklikler burada cunku CIZIM tarafinin (GameCanvas) cekmecenin ne
    // kadar yer kapladigini bilmesi gerekiyor ve iki taraf ayni sayiyi
    // okumali. TowerBuildBar / SelectedTowerInspector de bu sabitlere
    // baglanmali (ayri ajan kapsaminda — bkz. gorev raporu).
    // ------------------------------------------------------------------------

    /** TowerBuildBar'in olculen yuksekligi (dp). */
    const val BUILD_DRAWER_HEIGHT_DP = 63f

    /** SelectedTowerInspector'in olculen yuksekligi (dp). */
    const val INSPECTOR_DRAWER_HEIGHT_DP = 56f

    /** Hayalet gostergenin yaricapi (referans tuval px). */
    const val OCCLUSION_CALLOUT_RADIUS_REF_PX = 62f

    /** Hayalet ile cekmecenin ust kenari arasinda birakilan bosluk (ref px). */
    const val OCCLUSION_CALLOUT_GAP_REF_PX = 24f

    /**
     * Verilen ekran-y'sindeki hedef (pad ya da kule) alt cekmecenin altinda mi
     * kaliyor? Yaricap dokunma dairesidir: merkez gorunse bile dairenin alti
     * panelin altina giriyorsa oyuncu hedefi "yarim" gorur.
     */
    fun isOccludedByBottomDrawer(
        targetScreenY: Float,
        targetRadiusPx: Float,
        screenHeightPx: Float,
        drawerHeightPx: Float
    ): Boolean = targetScreenY + targetRadiusPx > screenHeightPx - drawerHeightPx

    /**
     * Hayalet gostergenin merkez y'si: cekmecenin TAM ustunde, HUD'in altinda.
     *
     * Deger her zaman ortulen hedefin YUKARISINDA kalir (bagli cizgi bu yuzden
     * daima yukari gosterir): ortulme sarti `y > ekran - cekmece - dokunmaR`,
     * capa ise `ekran - cekmece - bosluk - yaricap` ve
     * `bosluk + yaricap (86 ref) > dokunmaR (46 ref)`.
     */
    fun occlusionCalloutY(
        screenHeightPx: Float,
        drawerHeightPx: Float,
        hudTopInsetPx: Float,
        renderScale: Float
    ): Float {
        val r = OCCLUSION_CALLOUT_RADIUS_REF_PX * renderScale
        val gap = OCCLUSION_CALLOUT_GAP_REF_PX * renderScale
        return (screenHeightPx - drawerHeightPx - gap - r).coerceAtLeast(hudTopInsetPx + r)
    }

    /** true yapilirsa waypoint cizgisi ve pad merkezleri debug icin cizilir. */
    const val DEBUG_DRAW_PATH = false

    /** Dokunma yakalama yaricapi (referans tuvalde). Denge degil, etkilesim. */
    const val TAP_RADIUS_REF_PX = 46f

    /**
     * Bos build pad secilince, HENUZ bir kule karti secilmemisken gosterilen
     * notr on-izleme menzili (REFERANS tuvalde; cizimde renderScale ile carpilir).
     *
     * Faz 10: kule menzilleri artik 150 ile 270 ref-px arasinda degisiyor, yani
     * TEK bir sabit halka artik yalan soyluyor — oyuncu Frost Field'in kapsama
     * alanini ancak kuleyi kurup satarak ogrenebiliyordu. Bu yuzden build
     * cubugundaki bir kart BASILI tutuldugunda `GameEngine.previewTowerType`
     * doluyor ve halka O KULENIN gercek menzilini gosteriyor
     * (bkz. GameCanvas: birakma onizlemesi her zaman gorunur olmali).
     */
    /**
     * FAZ 13 DUZELTMESI: 170 -> 150.
     *
     * Bu halka, hicbir kart basili DEGILKEN bos bir pad secildiginde ciziliyor,
     * yani oyuncunun "buraya kursam nereye yetisir" sorusuna verilen ILK cevap.
     * 170, oyundaki EN KISA kademe-1 menzilinden (Gatling 150) buyuktu — ve
     * bolum 1-2'de acik olan TEK kule Gatling. Yani halka her zaman gercekte
     * ulasilamayan bir alani gosteriyordu.
     *
     * Bu, olu build pad hatasiyla birleserek oyuncuyu aktif olarak yaniltiyordu:
     * bolum 1'de yoldan 195 ve 213 ref-px uzaktaki pad'ler 170'lik halkada
     * "neredeyse yola degiyor" gorunuyor, kurulan kule hicbir seye ates
     * etmiyordu. Olu pad'ler artik gizlendi; bu satir da halkanin bir daha
     * fazla soz vermemesini garantiler.
     *
     * 150 secildi cunku oyundaki en kisa kademe-1 menzilidir: notr halka artik
     * HICBIR bolumde gercekten kurulabilecek bir kulenin menzilini ASMAZ.
     * Kart basili tutuldugunda halka zaten o kulenin gercek menzilini gosterir
     * (`GameEngine.previewRangeRef`), yani uzun menzilli kuleler bundan
     * etkilenmez.
     */
    const val BUILD_PREVIEW_RANGE_PX = 150f

    /** Letterbox / tuval zemini. */
    const val LETTERBOX_COLOR = 0xFF0B0E08

    /**
     * SPRITE YONU — olculdu, tahmin edilmedi (olcum sonucu hemen asagida).
     * atan2(dy, dx) ekran koordinatlarinda 0 derece = SAG, +90 derece = ASAGI.
     *
     * enemy_infantry / fast_soldier / jeep / tank: hepsi ASAGI (guney) bakiyor.
     * fx_*_tracer / _shell / _projectile / muzzle_flash: hepsi SAGA bakiyor.
     */
    const val ENEMY_SPRITE_BASE_ANGLE_DEG = 90f
    const val PROJECTILE_SPRITE_BASE_ANGLE_DEG = 0f

    /**
     * @param widthRefPx 1920 referans tuvalde sprite'in cizilecek genisligi.
     *   Haritadaki cizili build pad ~92 px oldugu icin kule 112 px: pad'i
     *   tamamen kapatir, boylece donen sekizgen taban ile sabit sekizgen pad
     *   arasinda aci uyusmazligi gorunmez.
     * @param pivotYFrac Sprite yuksekliginin hangi orani kulenin oturma /
     *   donme eksenidir. Tuvalin merkezi DEGIL.
     * @param rotates Sprite yonlu top-down bir taret mi? machine_gun ve
     *   heavy_cannon namlusu asagi bakan top-down cizim -> doner.
     *   missile_launcher (dikey ramp) ve energy_slow (radyal cukur anten)
     *   3/4 on gorunum -> dondurmek tabani ters cevirir, DONMEZ. Bu kulelerde
     *   nisan alma hissi namlu alevinin hedef yonunde cikmasiyla verilir.
     */
    data class TowerSpriteSpec(
        val widthRefPx: Float,
        val pivotYFrac: Float,
        val rotates: Boolean,
        val baseAngleDeg: Float
    )

    val TOWER_SPRITES: Map<TowerType, TowerSpriteSpec> = mapOf(
        TowerType.MACHINE_GUN to TowerSpriteSpec(112f, 0.47f, rotates = true, baseAngleDeg = 90f),
        TowerType.CANNON to TowerSpriteSpec(112f, 0.48f, rotates = true, baseAngleDeg = 90f),
        TowerType.ANTI_ARMOR to TowerSpriteSpec(112f, 0.72f, rotates = false, baseAngleDeg = 0f),
        TowerType.SLOW to TowerSpriteSpec(116f, 0.63f, rotates = false, baseAngleDeg = 0f)
    )

    /**
     * Faz 4 / DECISIONS B1: yeni dusman tipleri icin YENI PNG URETILMEZ. Iki yeni
     * tip mevcut sprite'lari yeniden kullanir ve **renk + eklenti isaretiyle**
     * ayrisir. Tint tek basina birakilmaz (game-art: "deger farki da olmali"),
     * `overlayBadge` sekil ayrimini saglar.
     *
     * @param widthRefPx 1920 referans tuvalde dusman sprite genisligi.
     * @param baseSprite Hangi mevcut bitmap cizilir. Kendisi olan tipler icin
     *   kendi degeri; turetilmis tipler icin kaynak tip.
     * @param tintArgb 0 = tint yok. GameCanvas bunu ColorFilter olarak uygular.
     * @param tintStrength 0..1 karisim orani.
     * @param overlayBadge Silueti degistiren ek cizim.
     */
    data class EnemySpriteSpec(
        val widthRefPx: Float,
        val pivotYFrac: Float,
        val baseSprite: EnemyType,
        val tintArgb: Long = 0L,
        val tintStrength: Float = 0f,
        val overlayBadge: EnemyBadge = EnemyBadge.NONE
    )

    /** Dusman sprite'inin uzerine cizilen ayirt edici isaret (bkz. EnemySpriteSpec). */
    enum class EnemyBadge {
        NONE,
        /** SHIELDED_TROOPER: govdeyi saran celik halka. */
        SHIELD_RING,
        /** COMMAND_TANK: taretten cikan komuta flamasi. */
        COMMAND_PENNANT
    }

    val ENEMY_SPRITES: Map<EnemyType, EnemySpriteSpec> = mapOf(
        EnemyType.INFANTRY to EnemySpriteSpec(46f, 0.44f, EnemyType.INFANTRY),
        EnemyType.FAST_SOLDIER to EnemySpriteSpec(42f, 0.47f, EnemyType.FAST_SOLDIER),
        EnemyType.ARMORED_VEHICLE to EnemySpriteSpec(58f, 0.50f, EnemyType.ARMORED_VEHICLE),
        EnemyType.TANK to EnemySpriteSpec(72f, 0.50f, EnemyType.TANK),
        // Celik-mavi tint + kalkan halkasi, piyade sprite'i uzerine.
        EnemyType.SHIELDED_TROOPER to EnemySpriteSpec(
            widthRefPx = 50f,
            pivotYFrac = 0.44f,
            baseSprite = EnemyType.INFANTRY,
            tintArgb = 0xFF6E8FB5,
            tintStrength = 0.38f,
            overlayBadge = EnemyBadge.SHIELD_RING
        ),
        // Altin/kizil tint + komuta flamasi, tank sprite'i uzerine.
        EnemyType.COMMAND_TANK to EnemySpriteSpec(
            // 96 -> 80 (2026-08-19, kullanici karari).
            //
            // OLCUM: yolun yarim genisligi haritalara gore 37-49 ref-px, yani
            // tam genislik 74-98. 96 ref-px'lik boss HICBIR rotaya tam
            // sigmiyordu — palet kenarlari surekli cimde yuruyordu ve
            // "dusmanlar yoldan cikiyor" sikayetinin bir parcasi buydu.
            //
            // 80 secildi: normal TANK'tan (72) %11 daha genis, yani boss
            // hala belirgin sekilde daha agir okunuyor; yari genisligi 40+
            // olan rotalara TAM sigiyor. En dar rotada (37) taraf basina
            // ~3 ref-px tasma kaliyor — kabul edildi, cunku tam sigdirmak
            // 74'e inmek yani normal tankla ayni gorunmek demekti.
            //
            // ⚠ ASIL KUSUR EN'DE DEGIL ORANDA: uretilen boss sprite'i
            // 188x190 (neredeyse KARE), normal tank 140x207 (0,68). Tank
            // uzun ve dar olmali; kare bir tank yoldan tasmaya mahkumdur.
            // Kalici cozum, dogru oranli (uzun-dar) bir boss sprite'i.
            widthRefPx = 80f,
            pivotYFrac = 0.50f,
            baseSprite = EnemyType.TANK,
            tintArgb = 0xFFC98A2E,
            tintStrength = 0.32f,
            overlayBadge = EnemyBadge.COMMAND_PENNANT
        )
    )

    // Efekt / mermi sprite boyutlari (referans tuvalde, px)
    const val FX_MUZZLE_FLASH_REF_PX = 56f
    const val FX_TRACER_REF_PX = 64f
    const val FX_CANNON_SHELL_REF_PX = 44f
    const val FX_MISSILE_REF_PX = 50f
    const val FX_HIT_SPARK_REF_PX = 52f
    const val FX_SMALL_EXPLOSION_REF_PX = 96f
    const val FX_LARGE_EXPLOSION_REF_PX = 150f
    const val FX_SMOKE_PUFF_REF_PX = 78f
    const val FX_BUILD_PAD_REF_PX = 98f
    const val FX_FROST_RING_REF_PX = 96f

    // ------------------------------------------------------------------------
    // HUD TEMIZLIK PAYLARI — hepsi mevcut sprite tablolarindan TURETILIR.
    // Elle sabit yazilmaz: bir sprite buyurse pay da buyur, yerlesim otomatik
    // olarak daha az kaydirir.
    // ------------------------------------------------------------------------

    /**
     * Bir build pad'in MERKEZININ ustunde bos kalmasi gereken alan (ref-px).
     *
     * Uc aday vardir, en buyugu kazanir:
     *  · [TAP_RADIUS_REF_PX] = 46 — dokunma dairesinin yaricapi. Bu saglanmazsa
     *    pad'e DOKUNULAMAZ (P0 hatasi buydu).
     *  · secili pad sprite'inin yarisi = [FX_BUILD_PAD_REF_PX] * 1,08 / 2.
     *  · en "uzun" kule sprite'inin pivot ustundeki kismi
     *    (`widthRefPx * pivotYFrac`; ANTI_ARMOR 112 * 0,72 = 80,6 en buyugu).
     *    Bu olmadan kurulan kulenin namlusu HUD'in altinda kalirdi.
     */
    val PAD_TOP_CLEARANCE_REF_PX: Float = maxOf(
        TAP_RADIUS_REF_PX,
        FX_BUILD_PAD_REF_PX * 1.08f / 2f,
        TOWER_SPRITES.values.maxOf { it.widthRefPx * it.pivotYFrac }
    )

    /**
     * Yolun uzerindeki bir dusmanin MERKEZININ ustunde bos kalmasi gereken
     * alan (ref-px) — en buyuk dusman sprite'inin pivot ustu (COMMAND_TANK
     * 96 * 0,50 = 48). Yolun ust ucu HUD'in altinda kalirsa oyuncu dusmanin
     * NEREDEN geldigini goremez.
     */
    val PATH_TOP_CLEARANCE_REF_PX: Float =
        ENEMY_SPRITES.values.maxOf { it.widthRefPx * it.pivotYFrac }

    /**
     * Referans tuval px'ini, oynanis dikdortgeninin YUKSEKLIGINE oranina cevirir.
     *
     * Neden bu carpan: cizimde `renderScale = fw / REFERENCE_WIDTH`, yani
     * `px = ref * fw / 1920`. Yuksekligin orani `px / fh` ve `fw / fh` HER
     * ZAMAN [MAP_ASPECT_RATIO]'dur (letterbox en-boy oranini korur), dolayisiyla
     * oran EKRANDAN BAGIMSIZ bir sabittir. Yerlesim guvenligi bu yuzden tek bir
     * cihazda degil TUM cihazlarda ayni esikle dogrulanabilir.
     */
    fun refPxToHeightFrac(refPx: Float): Float =
        refPx * MAP_ASPECT_RATIO / REFERENCE_WIDTH

    /** [mapSafeTopFrac] sonuclari — olcum sabit, harita basina bir kez yapilir. */
    private val safeTopFracCache = HashMap<Int, Float>()

    /**
     * Haritanin ust HUD seridinin ALTINA kaydirilabilecek DEKORATIF bant orani.
     *
     * POZITIF deger: harita bu kadar yukari kaydirilir (kazanc — oynanis alani
     * buyur). NEGATIF deger: harita HUD'un altina sigmiyor demektir ve bu kadar
     * KUCULTULUR (letterbox). Her iki durumda da `GameEngine` ayni formulu
     * kullanir:
     *
     *   fh        = (ekranYuksekligi - hudInset) / (1 - s)
     *   fieldTop  = hudInset - s * fh
     *
     * Bu formulle bir pad'in ust kenari `fieldTop + (padY - payOrani) * fh`
     * olur ve sartimiz `>= hudInset`, yani `s <= padY - payOrani`. **fh sadelesir**
     * — guvenlik ekran boyutundan BAGIMSIZDIR, bu yuzden 740x360 dp'de dogru
     * olan tablette de dogrudur.
     *
     * Olcum haritanin TUM pad'lerini ve TUM rotalarini (ikinci kol dahil)
     * kullanir; o bolumde gizlenmis ya da kapali olsalar bile. Boylece harita
     * boyutu bolumden bolume DEGISMEZ ve `disabledPadIds` degisince yerlesim
     * sessizce bozulmaz.
     */
    fun mapSafeTopFrac(mapId: Int): Float = safeTopFracCache.getOrPut(mapId) {
        val data = LevelData.forMapId(mapId)
        val minPadY = data.buildSpots.minOfOrNull { it.normY } ?: 1f
        val minPathY = LevelData.routesForMapId(mapId)
            .flatten()
            .minOfOrNull { it.y } ?: 1f

        val byPad = minPadY - refPxToHeightFrac(PAD_TOP_CLEARANCE_REF_PX)
        val byPath = minPathY - refPxToHeightFrac(PATH_TOP_CLEARANCE_REF_PX)
        minOf(byPad, byPath).coerceIn(MAP_SAFE_TOP_FRAC_MIN, MAP_SAFE_TOP_FRAC_MAX)
    }

    /**
     * Letterbox sonrasi OYNANIS DIKDORTGENI. Tum oynanis koordinatlari buna
     * gore hesaplanir.
     *
     * @param left Sol kenar, ekranin kendi px uzayinda.
     * @param top Ust kenar. HUD'in ALTINDA olmak zorunda DEGIL: haritanin ust
     *   bandi dekoratifse ([mapSafeTopFrac] > 0) bilincli olarak seridin
     *   arkasina kayar.
     */
    data class MapFieldRect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float
    ) {
        val right: Float get() = left + width
        val bottom: Float get() = top + height
        /** Referans tuvalde tanimli gorsel boyutlari px'e cevirme carpani. */
        val renderScale: Float get() = width / REFERENCE_WIDTH
    }

    /**
     * ------------------------------------------------------------------------
     * OYNANIS DIKDORTGENI — TEK KAYNAK
     * ------------------------------------------------------------------------
     * Motor (`GameEngine.updateMapDimensions`) bu fonksiyonu cagirir; formul
     * renderer'a ya da motora GOMULU DEGILDIR. Boylece "hicbir pad HUD'in
     * altinda kalmiyor" iddiasi 11 harita x N ekran orani icin Robolectric'e
     * ihtiyac duymadan, saf birim testiyle dogrulanabilir
     * (`MapLayoutSafetyTest`).
     *
     * FIT, crop DEGIL: crop build pad'leri ekran disina atar ve bolumu
     * oynanamaz kilar.
     *
     * @param hudTopInsetPx ust HUD seridinin OLCULEN yuksekligi (GameScreen
     *   `onSizeChanged` ile verir). Sabit varsayilmaz.
     */
    fun computeFieldRect(
        mapId: Int,
        screenWidth: Float,
        screenHeight: Float,
        hudTopInsetPx: Float
    ): MapFieldRect {
        val s = mapSafeTopFrac(mapId)
        val availableHeight = (screenHeight - hudTopInsetPx).coerceAtLeast(1f)
        var fh = availableHeight / (1f - s)
        var fw = fh * MAP_ASPECT_RATIO
        if (fw > screenWidth) {
            // Genislik sinirli (daha kare tuval / tablet) -> altta serit kalir.
            fw = screenWidth
            fh = screenWidth / MAP_ASPECT_RATIO
        }
        return MapFieldRect(
            left = (screenWidth - fw) / 2f,
            top = hudTopInsetPx - s * fh,
            width = fw,
            height = fh
        )
    }

    // ------------------------------------------------------------------------
    // NORMALIZE -> EKRAN: TEK FORMUL
    //
    // Hem motor (rota/pad olcekleme, `GameEngine.rescaleGeometry`) hem cizim
    // (harita bitmap'inin dikdortgeni, `GameCanvas`) BU iki fonksiyondan
    // gecer. Ayri formuller yazildigi surece "sanat ile oynanis ayni yerde mi"
    // sorusu ancak gozle kontrol edilebilirdi; simdi testle kilitli
    // (`MapArtAlignmentTest`).
    // ------------------------------------------------------------------------

    fun normXToScreen(field: MapFieldRect, normX: Float): Float =
        field.left + normX * field.width

    fun normYToScreen(field: MapFieldRect, normY: Float): Float =
        field.top + normY * field.height

    /**
     * Harita bitmap'inin CIZILECEGI dikdortgen.
     *
     * **SANAT = OYNANIS.** Birebir [field] doner; bu fonksiyon var cunku
     * kimlik olmasi bir KARARDIR ve karar tek bir yerde durmali. Buraya bir
     * gun yeniden tasma eklenirse (`MAP_EDGE_BLEED_REF_PX` ile gerdirme gibi)
     * `MapArtAlignmentTest` DUSER: normalize bir koordinat hem harita
     * ciziminde hem oynanis hesabinda ayni ekran noktasina dusmek zorunda —
     * merkezde de kosede de.
     *
     * Sarsintida kenarin acilmamasi ayri bir mekanizmadir ve icerigi
     * kaydirmaz: bkz. [mapEdgeBleedPx].
     */
    fun mapArtRect(field: MapFieldRect): MapFieldRect = field

    /** Namlu alevinin kule merkezinden namlu ucuna ofseti (referans tuvalde). */
    const val MUZZLE_OFFSET_REF_PX = 44f

    /** Bos build pad isaretinin bekleme / secili alfasi. */
    const val BUILD_PAD_IDLE_ALPHA = 0.55f
    const val BUILD_PAD_SELECTED_ALPHA = 1.0f

    /**
     * ATES HATTI OLMAYAN pad'in alfasi — bkz. [coversRoute].
     *
     * Bekleme alfasindan (0,55) belirgin sekilde asagida: pad GIZLENMEZ ama
     * GERI CEKILIR. Renk tek ayrim kanali olamaz, o yuzden isaret ayrica bir
     * SEKIL tasir (halka + capraz cizgi; `GameCanvas.drawNoLineOfFireMark`).
     * 0,22 secildi cunku pad'in sekli hala secilebilir kaliyor — oyuncu oraya
     * yine dokunabilmeli ve yine kurabilmeli: menzil yukseltmesiyle (Gatling
     * kd.1 150 -> kd.2 180 -> kd.3 210) o mevzi ileride acilir.
     */
    const val BUILD_PAD_NO_REACH_ALPHA = 0.22f

    // ------------------------------------------------------------------------
    // ATES HATTI — "bu kule buradan yola yetisiyor mu?"
    //
    // NEDEN VAR: cihazdan gelen sikayet bolum 8 / pad 7'ydi. Gatling (kd.1
    // menzil 150) o mevziden yola 151 ref-px uzakta kaliyor ve HICBIR SEYE
    // ates edemiyor. Pad cop DEGIL, yanlis kule icin secilmis: ayni pad'den
    // Fuze Rampasi (menzil 250) haritanin en iyi ikinci kapsamasini veriyor.
    // Yani kusur pad'in yerinde degil, KURMADAN ONCE VERILMEYEN SINYALDE.
    //
    // NEDEN TEK YERDE: ayni mesafe hesabi iki dosyaya yazilirsa bu depoda
    // kesinlikle ayrisiyor — "olu build pad" hatasi tam olarak boyle 22 bolum
    // boyunca testten kacti (bkz. PadReachabilityPerLevelTest KDoc'u). Cizim
    // (`GameCanvas`), panel (`TowerBuildBar`) ve testler
    // (`GeometryTestSupport`) hepsi asagidaki iki fonksiyondan gecer.
    //
    // BIRIM SOZLESMESI: fonksiyonlar BIRIMSIZDIR. Nokta, rota ve menzil AYNI
    // uzayda verilmek ZORUNDADIR. Oynanista bu EKRAN pikselidir
    // (`GameEngine.scaledRoutes` + `previewRangeRef(t) * renderScale`),
    // testlerde 1920x1080 REFERANS tuvali. Karistirmak sessizce yanlis cevap
    // uretir, bu yuzden her cagri yerinde birim yorumla yazilidir.
    //
    // TAHSIS: cizim yolundan cagriliyor. Indeksli dongu kullanilir; `minOf` /
    // `forEach` gibi lambda ve iterator YOK — kare basina cop uretmez.
    // ------------------------------------------------------------------------

    /** Bir noktanin [ax],[ay]-[bx],[by] dogru PARCASINA (sonsuz dogruya degil) uzakligi. */
    fun pointToSegmentDistance(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        // Dejenere parca (ust uste dusen iki waypoint): noktaya uzaklik.
        if (len2 == 0f) return hypot(px - ax, py - ay)
        var t = ((px - ax) * dx + (py - ay) * dy) / len2
        if (t < 0f) t = 0f else if (t > 1f) t = 1f
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /**
     * Noktanin, VERILEN rota kumesindeki en yakin yere uzakligi.
     *
     * Rota kumesi bos ya da tum rotalar tek noktalik ise [Float.MAX_VALUE]
     * doner — "hicbir yola yakin degil". Cizim yolu bolum yuklenmeden once de
     * kosabildigi icin bu bir istisna DEGIL, normal bir cevap olmali.
     */
    fun distanceToRoutes(px: Float, py: Float, routes: List<List<PointF>>): Float {
        var best = Float.MAX_VALUE
        for (r in routes.indices) {
            val route = routes[r]
            for (i in 0 until route.size - 1) {
                val a = route[i]
                val b = route[i + 1]
                val d = pointToSegmentDistance(px, py, a.x, a.y, b.x, b.y)
                if (d < best) best = d
            }
        }
        return best
    }

    /**
     * Bu noktaya kurulan [rangePx] menzilli bir kule rotanin herhangi bir
     * yerini gorur mu?
     *
     * SINIR DAHIL (`<=`): menzil mesafeye TAM esitse motor da hedefi vurur
     * (`GameEngine` menzil karsilastirmasi `<=`). Burada `<` yazmak paneli
     * oynanistan bir ref-px kadar ayirirdi — bu depoda tam olarak bu buyuklukte
     * bir sapma (151 vs 150) sikayete donustu.
     */
    fun coversRoute(px: Float, py: Float, rangePx: Float, routes: List<List<PointF>>): Boolean =
        rangePx > 0f && distanceToRoutes(px, py, routes) <= rangePx

    enum class TowerType {
        MACHINE_GUN,
        CANNON,
        ANTI_ARMOR,
        SLOW
    }

    enum class EnemyType {
        INFANTRY,
        FAST_SOLDIER,
        ARMORED_VEHICLE,
        TANK,
        /** Faz 4 / DECISIONS B1: kursuna dirençli, patlamaya zayif piyade. */
        SHIELDED_TROOPER,
        /** Faz 4 / DECISIONS B1: boss. Act I finalinde (L11) ilk kez cikar. */
        COMMAND_TANK
    }

    enum class TargetingMode {
        FIRST,      // Closest to base
        LAST,       // Furthest from base
        STRONGEST,  // Highest current HP
        WEAKEST     // Lowest current HP
    }

    /**
     * ------------------------------------------------------------------------
     * Faz 10 — KULE UZMANLASMASI (testci: "bir de her kule hepsinde ise yariyor")
     * ------------------------------------------------------------------------
     * Rol bir ETIKET degil, MAKINEYLE DOGRULANAN bir sozlesme: her rol tam bir
     * kule tarafindan doldurulur ve `BalanceConsistencyTest` her rolun kendi
     * hedef sinifinda EN IYI, baska bir sinifta BELIRGIN SEKILDE EN KOTU
     * oldugunu sayisal olarak kontrol eder. Boylece ileride biri "Gatling'i
     * biraz guclendirelim" derse ve kule her seye yeterli hale gelirse test
     * kirilir — regresyon sessizce geri gelemez.
     */
    enum class TowerRole {
        /** Kalabalik/hizli piyade. Zirha karsi neredeyse ise yaramaz. */
        CROWD,
        /** Kumelenmis ve YAVAS hedef; patlama zirhi bypass eder. Tek hizli hedefe kotu. */
        SIEGE,
        /** Tek agir zirhli hedef. Kalabaliga karsi verimsiz (cok yavas atis). */
        ANTI_TANK,
        /** Hasar vermez, ALAN KONTROLU yapar: digerlerinin penceresini acar. */
        SUPPORT
    }

    /**
     * ------------------------------------------------------------------------
     * Faz 13 — KULE KADEMESI ARTIK **VERI** (DECISIONS B5)
     * ------------------------------------------------------------------------
     * Onceki sekil `level1Range/level1Damage/... level2Range/...` seklinde DUZ
     * ALANLARDI. Bu, kademe sayisini KODA gomuyordu: motor `tower.level = 2`
     * yaziyor, panel `if (level == 1)` diye soruyor, ve "son kademe mi" sorusu
     * hicbir yerde VERIDEN turemiyordu. Kademe 3 eklemek bu yuzden bir denge
     * isi degil bir refactor isi haline gelmisti (B5'in "ilk APK'yi bloklamaz"
     * gerekcesi tam olarak buydu).
     *
     * Simdi kademe listesi verinin kendisi. **Kademe 4 bir gun gerekirse bu
     * listeye bir satir eklemek yeterlidir**; motor ve UI "son kademe mi"
     * sorusunu [TowerStats.maxTier] uzerinden sorar, sabit bir 2 veya 3'e degil.
     *
     * @param tier 1-tabanli kademe numarasi. Liste sirasiyla tutarli olmak
     *   ZORUNDA (`TowerTierDataTest` kilitler) — mesajlarda ve testlerde
     *   kademeyi indeksten yeniden turetmemek icin duruyor.
     * @param range Menzil **1920 REFERANS TUVALINDE** (DECISIONS B3), ham canvas
     *   px DEGIL. Motor `TowerEntity.rangePx(renderScale)` ile cevirir; aksi
     *   halde tablet ile telefon ayni oyunu oynamaz.
     * @param fireRate Atislar arasi sure, SANIYE. Kucuk = hizli.
     * @param upgradeCost Bu kademeye YUKSELMENIN Tedarik bedeli. Kademe 1 icin
     *   daima 0'dir: o kademeye yukselerek degil [TowerStats.buildCost] odeyerek
     *   gelinir.
     * @param unlockedAtLevel Bu kademenin acildigi KAMPANYA bolumu. Kademe 1-2
     *   kulenin kendi kilidini takip eder; kademe 3 [TIER_THREE_UNLOCK_LEVEL].
     */
    data class TowerTier(
        val tier: Int,
        val range: Float,
        val damage: Float,
        val fireRate: Float,
        val upgradeCost: Int,
        val unlockedAtLevel: Int,
        /**
         * KADEMEYE OZEL zirh delme. `null` = kulenin kendi
         * [TowerStats.armorPierce] degeri kullanilir (eski davranis).
         *
         * ⚠ 2026-08-22 — NEDEN EKLENDI (gercek insan oynanisi):
         * Kullanici *"dusman araba cok gucsuz"* dedi. Olcum sucluyu buldu ve
         * suclu ARACIN KENDISI DEGILDI:
         *
         *   Zirhli Araca karsi etkin DPS (kademe 2)
         *     Gatling    23,8   <- zirh CALISIYOR, %78 kesiyor
         *     Agir Top   40,0   <- patlama zirhi tamamen bypass eder
         *     Fuze       58,4   <- zirh yalnizca %12 kesiyor
         *
         * Sebep: `armorPierce = 0,85` KULE seviyesinde tanimliydi, yani Fuze
         * daha ILK kademesinde zirhin %85'ini deliyordu. Zirhli Aracin 0,78'lik
         * zirhi, fuzeye karsi etkin 0,78 x 0,15 = 0,117'ye dusuyordu.
         *
         * En kotusu ZAMANLAMASI: Fuze Rampasi `unlockedAtLevel = 7`, yani
         * zirhin "duvar" kimligi tam da kullanicinin sikayet ettigi bolumde
         * cokuyordu. Tank bile (zirh 0,86) fuzeden neredeyse zirhsiz hasar
         * aliyordu (66,1 -> 57,6), oysa kodun kendi yorumu "yalnizca
         * patlama/delici ise yarar" diyordu.
         */
        val armorPierce: Float? = null
    ) {
        /** Sürekli hasar (hasar/sn) — rol karsilastirmalarinin olcum birimi. */
        val dps: Float get() = damage / fireRate
    }

    /**
     * **KADEME 3'UN ACILDIGI BOLUM = ACT II'NIN ILK BOLUMU.**
     *
     * Neden 12, neden daha erken degil:
     * - Act I'in doruk noktasi L11'dir (ilk boss; LEVEL_DESIGN H.1 o bolumde
     *   %40-55 yenilgi orani BEKLIYOR). Kademe 3 Act I icinde acilsaydi o
     *   doruk sayisal olarak dagilirdi — "erken acilirsa erken bolumler
     *   trivial olur" itirazi tam olarak burada gecerli.
     * - Act II'yi Act I'den ayiran sey pad KISITIDIR: L12-22'de pad'lerin
     *   %25-36'si kapali (`FROZEN_DISABLED_PADS`) ve dusman cani x1.55
     *   (`actHpMultiplier`). Yani oyuncunun elinden YATAY buyume (daha cok
     *   kule) alinmistir. Kademe 3 bunun dogal cevabidir: **az pad, derin
     *   pad.** Kilidi Act sinirina koymak mekanigi tek bir anlatiya baglar.
     * - Olcum tarafi: atil Tedarik orani L11'den itibaren pozitife doner ve
     *   L12'de %42'ye ciker (bkz. `LateGameSupplySinkTest`), yani sinkin
     *   gerektigi ilk bolum de L12'dir.
     */
    const val TIER_THREE_UNLOCK_LEVEL: Int = 12

    /**
     * @param role Kulenin oynanistaki kimligi (bkz. TowerRole).
     * @param unlockedAtLevel Bu kule kacinci KAMPANYA bolumunden itibaren insa
     *   edilebilir. Testci: "ilk bolumden itibaren her seyi acmak dogru degil."
     *   LEVEL_DESIGN kurali: oyuncunun henuz sahip olmadigi mekanik zorunlu
     *   basari kosulu OLAMAZ — bu yuzden her yeni dusman tipinin cevabi, o tip
     *   ilk gorundugu bolumde ZATEN acik olmali. Kilit tablosunun dalga
     *   tanimlariyla tutarliligi `WaveDefinitionsDataTest` tarafindan
     *   dogrulanir, yorumla degil.
     * @param tiers Kademe merdiveni, kademe 1'den baslayarak. En az bir eleman.
     *   Kademe SAYISI buradan okunur; hicbir yerde sabit kodlanmaz.
     * @param splashRadius > 0 YALNIZCA Cannon'da (referans tuvalde). Splash
     *   bileseni zirhi bypass eder (DECISIONS B2).
     * @param armorPierce 0..1, YALNIZCA Anti-Armor.
     * @param slowPulseRadius > 0 YALNIZCA Slow'da (referans tuvalde).
     *   **Bu alan olmadan Frost Field bir DESTEK kulesi degildi**: cryo darbesi
     *   yalnizca hedeflenen TEK dusmani yavaslatiyordu, yani 20 kisilik bir
     *   suruye karsi 0.65 sn'de bir 1 dusman -> oyuncunun "kullanmanin anlami
     *   yok" demesinin gercek sebebi buydu. Artik darbe bu yaricaptaki HERKESI
     *   soguturr.
     * @param missileImpactRadius > 0 YALNIZCA Anti-Armor (referans tuvalde).
     *   Fuzenin carpma noktasindaki KUCUK alan hasari. Kasitli olarak
     *   Cannon'in splash yaricapindan cok kucuk ve hasari kesirli, ustelik
     *   zirhi bypass ETMEZ (delici muhimmat olarak hesaplanir) — boylece
     *   Cannon'in "kalabalik/kalkanli" kimligini golgelemez.
     * @param missileImpactDamageFraction Ikincil hedeflere giden hasar orani.
     *
     * NOT: Rol alanlari (splash / pierce / slow / missile) KADEMEDEN BAGIMSIZ
     * kalir. Bu bilincli: kademe 3 bir kulenin ne YAPTIGINI degistirmez,
     * yalnizca ne kadar iyi yaptigini degistirir. Aksi halde ucuncu kademe
     * dort kuleyi ayni sise donusturur ve Faz 10'da kazanilan uzmanlasma
     * gec oyunda sessizce kaybolurdu.
     */
    data class TowerStats(
        val type: TowerType,
        val role: TowerRole,
        val unlockedAtLevel: Int,
        val name: String,
        val description: String,
        val buildCost: Int,
        val tiers: List<TowerTier>,
        val splashRadius: Float = 0f,  // > 0 for Cannon
        val armorPierce: Float = 0f,   // 0.0 to 1.0 for Anti-Armor
        val slowFactor: Float = 0f,    // 0.0 to 1.0 (e.g. 0.5 = 50% speed) for Slow Tower
        val slowDuration: Float = 0f,  // Duration of slow in seconds
        val slowPulseRadius: Float = 0f,          // > 0 for Slow
        val missileImpactRadius: Float = 0f,      // > 0 for Anti-Armor
        val missileImpactDamageFraction: Float = 0f
    ) {
        init {
            require(tiers.isNotEmpty()) { "$type: kademe listesi bos olamaz" }
        }

        /** Bu kulenin sahip oldugu kademe sayisi. "Son kademe mi" sorusunun kaynagi. */
        val maxTier: Int get() = tiers.size

        /** 1-tabanli kademe erisimi; aralik disi degerler siniri asmaz (motor asla cokmez). */
        fun tier(tier: Int): TowerTier = tiers[(tier - 1).coerceIn(0, tiers.lastIndex)]

        /**
         * Bu kademede gecerli zirh delme.
         *
         * Kademe kendi degerini bildirmisse o, aksi halde kulenin nominal
         * [armorPierce] degeri. Boylece zirh delmesi OLMAYAN kuleler
         * (Gatling/Agir Top/Buz, hepsi 0) hicbir sey yazmadan eski
         * davranislarini surdurur; yalnizca Fuze Rampasi kademeye yayilmis
         * degerler tasir (bkz. [TowerTier.armorPierce]).
         */
        fun armorPierceAt(tier: Int): Float =
            tier(tier).armorPierce ?: armorPierce

        /**
         * [tier] kademesindeki bir kuleyi BIR SONRAKI kademeye yukseltmenin
         * bedeli; son kademede `null`.
         */
        fun upgradeCostFrom(tier: Int): Int? = tiers.getOrNull(tier)?.upgradeCost

        /**
         * Bu bolumde bu kulenin ulasabilecegi EN YUKSEK kademe.
         *
         * Kademe kilitleri artan sirada oldugu icin (test kilitler) sayarak
         * bulunur. Kule henuz hic acilmamissa bile 1 doner — insa kilidi ayri
         * bir karar ([isTowerUnlocked]) ve iki kilidi birbirine karistirmak
         * paneli "0. kademe" gostermeye iterdi.
         */
        fun maxTierAt(levelId: Int): Int =
            tiers.count { levelId >= it.unlockedAtLevel }.coerceAtLeast(1)

        // --------------------------------------------------------------------
        // ISIMLE kademe 1/2 kisayollari.
        //
        // Bunlar bir "kademe sayisi 2'dir" iddiasi DEGILDIR: adlarinda hangi
        // kademeye baktiklari yazili ve denge dokumanlarinin tamami (TOWER_
        // REBALANCE §1-§5, ECONOMY_SPEC) bu iki kademeye ISIMLE atif yapiyor.
        // Kademe-genel karar veren hicbir yer (motor, panel, ekonomi) bunlari
        // kullanmaz; kullanan tek yerler kademe 1/2'ye kasten bakan denge
        // testleri ve `referenceTowerDps` kilididir.
        // --------------------------------------------------------------------
        val level1Range: Float get() = tier(1).range
        val level1Damage: Float get() = tier(1).damage
        val level1FireRate: Float get() = tier(1).fireRate
        val level1Dps: Float get() = tier(1).dps
        val level2UpgradeCost: Int get() = tier(2).upgradeCost
        val level2Range: Float get() = tier(2).range
        val level2Damage: Float get() = tier(2).damage
        val level2FireRate: Float get() = tier(2).fireRate
        val level2Dps: Float get() = tier(2).dps
    }

    /** Bu kule bu bolumde insa edilebilir mi? Tek karar noktasi. */
    fun isTowerUnlocked(type: TowerType, levelId: Int): Boolean =
        levelId >= (TOWER_SPECS[type]?.unlockedAtLevel ?: 1)

    /**
     * Bu kulenin bu BOLUMDE ulasabilecegi en yuksek kademe. Motor kuleyi insa
     * ederken bunu bir kez okur ve [TowerEntity.tierCap] olarak tasir; boylece
     * panel ile motor AYNI cevabi verir ve oyuncuya odeyemeyecegi bir yukseltme
     * butonu gosterilmez.
     */
    fun maxTowerTier(type: TowerType, levelId: Int): Int =
        TOWER_SPECS[type]?.maxTierAt(levelId) ?: 1

    /** Bolum secme/insa cubugu icin: bu bolumde acik olan kuleler. */
    fun unlockedTowers(levelId: Int): List<TowerType> =
        TowerType.values().filter { isTowerUnlocked(it, levelId) }

    data class EnemyStats(
        val type: EnemyType,
        val name: String,
        val maxHp: Float,
        val baseSpeed: Float,         // Speed along path
        val armor: Float,             // Damage mitigation factor (0.0 = no armor, 0.6 = 60% reduction to normal ammo)
        val rewardGold: Int,
        val sizeRadius: Float,
        /**
         * Faz 4 / DECISIONS B2. **YALNIZCA splash hasarina** uygulanan carpan;
         * dogrudan isabete uygulanmaz. Splash bileseni ayrica zirhi BYPASS eder
         * (GameEngine.applyDamageToEnemy, tek `if`).
         *
         * Gerekce: Cannon'in armorPierce'i 0 oldugu icin zirhli hedefe karsi en
         * kotu secenekti; bu da "kursuna direncli / patlamaya zayif" dusman
         * tasarimini imkansiz kiliyor ve Cannon'i gec oyunda olu birakiyordu.
         */
        val splashVulnerability: Float = 1f
    )

    data class WaveEnemySpawn(
        val enemyType: EnemyType,
        val delaySeconds: Float
    )

    data class WaveData(
        val waveIndex: Int,
        val title: String,
        val spawns: List<WaveEnemySpawn>
    )

    // ========================================================================
    // Faz 10 — KULE TABLOSU
    //
    // Her sayinin GEREKCESI docs/TOWER_REBALANCE.md'de tablo halinde duruyor.
    // Ozet (etkin DPS = hasar/sn x zirh carpani):
    //
    //                zirhsiz  zirhli(0.78)  tank(0.86)  5'li kume   maliyet
    //   MACHINE_GUN    43.8        9.6          6.1        43.8        60
    //   CANNON         18.4       18.4*        18.4*       92.0*       95
    //   ANTI_ARMOR     30.6       27.0         26.6        ~50        115
    //   SLOW            2.3        0.5          0.3        ~12        100
    //   (*) splash zirhi BYPASS eder (DECISIONS B2) ve TUM kumeye vurur.
    //   (DPS'ler atis araligi x2 sonrasi; ORANLAR degismedi, yani rol
    //    uzmanlasmasi birebir korunuyor — bkz. asagidaki tempo blogu.)
    //
    // Yani: kalabalikta MACHINE_GUN, kumede CANNON, zirhta ANTI_ARMOR acik ara
    // onde; her kulenin bariz bir KOR NOKTASI var. Bu tablo elle okunmak icin
    // degil test icin yazildi: BalanceConsistencyTest ayni oranlari yeniden
    // hesaplayip zorunlu kiliyor.
    //
    // ------------------------------------------------------------------------
    // ATIS TEMPOSU + TABAN KALIBRASYONU
    //
    // Iki ayri sebep ayni yerde birlesiyor:
    //
    // (1) Kullanici: "vurus hizlari %50 dusurulsun, cok hizli ates ediyorlar."
    //     `fireRate` SANIYE CINSINDEN ARALIKTIR, hiz degil -> aralik x2:
    //       MACHINE_GUN 0.16 -> 0.32 · 0.12 -> 0.24
    //       CANNON      1.25 -> 2.50 · 1.10 -> 2.20
    //       SLOW        0.65 -> 1.30 · 0.55 -> 1.10
    //       ANTI_ARMOR  1.80 -> 3.60 · 1.55 -> 3.10
    //
    // (2) ATIS BASINA HASAR **DEGISMEDI**, yani her kulenin DPS'i KASTEN
    //     yarilandi. Once hasari x2 ile telafi etmeyi denedik; yanlisti, cunku
    //     DPS'i korumak sorunun tamamini korur:
    //
    //     docs/tools/difficulty_audit.py olcumu (bolum 7, oyuncu LEHINE ust
    //     sinir): iki kulenin teslim edebilecegi hasar / dalgalarin toplam cani
    //     = **8.23**. Yani iki kule gerekenin sekiz katini veriyordu; "her kule
    //     hepsinde ise yariyor" ve "2 kule yetiyor" sikayetlerinin ortak kok
    //     sebebi buydu. Gatling Kd.2 piyadeye 216 DPS veriyor, piyade 75 canli:
    //     tek kule saniyede 2.9 piyade siliyor, dalga saniyede 0.4 dusman
    //     gonderiyor.
    //
    //     Duzeltme iki carpandan olusuyor: atis araligi x2 (bu blok) ve dusman
    //     cani x3.5 (bkz. ENEMY_SPECS). 8.23 / 2 / 3.5 = **1.18** -> hedef
    //     bant 1.15..1.40.
    //
    // UZMANLASMA KORUNUYOR (dogrulandi): Gatling Kd.2 tanka atis basina 3.64
    // hasar veriyor (26 x 0.14), 0.24 sn araliktan 15.2 DPS; 2030 canli tanki
    // tek basina **134 saniyede** olduruyor. Yani hâlâ tamamen ise yaramaz ve
    // Agir Top / Fuze zorunlu kaliyor.
    //
    // Yan fayda: makineli tufek sesinin minIntervalMs'i 70 ms. 0.16 sn (160 ms)
    // araliginda ses neredeyse surekli bir gurultuydu; 0.32 sn'de her atis
    // ayri duyulur, namlu alevi (0.13 sn) ile de artik ortusmuyor.
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Faz 13 — KADEME 3 (DECISIONS B5). Kademe 1 ve 2 degerleri BIREBIR AYNI.
    //
    // Bu bir yeniden dengeleme DEGIL, bir EKLEME. `referenceTowerDps = 43.75`
    // kilidi, Faz 12.1'in cozulebilirlik testleri ve SPI tablosu kademe 1-2
    // uzerine kurulu ve hepsi degismeden gecerli kaliyor.
    //
    // NEDEN GEREKLI (olculmus): L15-22'de Tedarik gelirinin ortalama **%67'si**
    // harcanacak yer bulamiyordu — butun pad'ler dolu, hepsi kademe 2, para
    // yiginla duruyor. Karar bosluguna dusen bolum sikici bolumdur
    // (LEVEL_DESIGN G.5). Olcum ve once/sonra: `LateGameSupplySinkTest`.
    //
    // FIYAT KURALI: **kademe 3 bedeli = kademe 2 bedelinin 2 kati.**
    //   Gatling 65 -> 130 · Top 90 -> 180 · Fuze 115 -> 230 · Buz 85 -> 170
    //
    // Neden 2x, neden uydurma degil:
    //  1. SINK OLCUSU. Bir kulenin tam maliyeti (insa + tum yukseltmeler) tam
    //     olarak ~2 katina cikiyor (Gatling 125 -> 255, Top 185 -> 365, Fuze
    //     230 -> 460, Buz 185 -> 355). Butun pad'ler doldurulup maks kademeye
    //     cikarildiginda atil Tedarik orani L15-22'de **%67.0 -> %34.7**'e
    //     duser; yani gec oyun gelirinin yarisindan fazlasi harcanabilir hale
    //     gelir. Daha ucuz bir kademe 3 sorunu cozmez, daha pahalisi bolumleri
    //     ulasilamaz bir tavana baglar.
    //  2. AZALAN GETIRI. Tedarik-basina-DPS kademe 3'te kademe 2'den DAHA KOTU:
    //     Gatling 1.01 -> 1.07 · Top 4.17 -> 4.39 · Fuze 3.23 -> 3.42 Tedarik
    //     / DPS. Yani kademe 3 otomatik alinan bir sey degil, kit olan kaynak
    //     (Act II'de PAD) icin odenen bilincli bir prim. Yatay buyume mumkun
    //     oldugu surece hâlâ daha ucuz — bu yuzden Act I'de acilmiyor.
    // ------------------------------------------------------------------------
    val TOWER_SPECS: Map<TowerType, TowerStats> = mapOf(
        // KALABALIK. Menzil 160 -> 150: en kisa menzilli kule olmasi kimliginin
        // parcasi (bogaza yerlestir, uzaktan tarama yok) ve destek kulesinin
        // menzil ustunlugunu okunur kiliyor. Hasar/atis hizi DEGISMEDI: zirha
        // karsi ise yaramazligi kule zayiflatilarak degil DUSMAN ZIRHI
        // yukseltilerek saglandi (bkz. ENEMY_SPECS) — boylece piyadeye karsi
        // hissedilen guc aynen korunuyor.
        TowerType.MACHINE_GUN to TowerStats(
            type = TowerType.MACHINE_GUN,
            role = TowerRole.CROWD,
            unlockedAtLevel = 1,
            name = "Gatling Gun",
            description = "Shreds infantry and runners. Bullets barely scratch armour.",
            buildCost = 60,
            tiers = listOf(
                TowerTier(1, range = 150f, damage = 14f, fireRate = 0.32f, upgradeCost = 0, unlockedAtLevel = 1),
                TowerTier(2, range = 180f, damage = 26f, fireRate = 0.24f, upgradeCost = 65, unlockedAtLevel = 1),
                // Kd.3 (Faz 13): DPS 108.3 -> 220.0 (x2.03; Kd.1->Kd.2 x2.48'in
                // ALTINDA = azalan getiri). Menzil +30, Kd.1->Kd.2 ile ayni adim.
                // Zirha karsi ise yaramazlik KORUNUR: tanka atis basina
                // 44 x 0.14 = 6.2, yani 30.8 DPS — fuzenin ayni kademedeki
                // 116.1 DPS'inin dortte biri. Kalabalik kulesi kalabalik
                // kulesi kaliyor (oran Kd.2'deki 1.27 ile ayni bantta).
                TowerTier(3, range = 210f, damage = 44f, fireRate = 0.20f, upgradeCost = 130, unlockedAtLevel = TIER_THREE_UNLOCK_LEVEL)
            )
        ),
        // KUSATMA. Tek hedef DPS'i 45.5 -> 36.8 dusuruldu ama splash yaricapi
        // 65 -> 78 buyudu: kimlik "tek hedefe vuran top" degil "kumeyi silen
        // top". Mermi hizi 160 -> 110 (ucus 0.63 -> 0.91 sn): Scout Runner o
        // surede 105 ref-px yol alir, yani 78'lik patlamanin DISINA cikar ->
        // top hizli hedefi ISKALAR. Bu bilincli zayiflik ayni zamanda oyuna
        // gercek bir kombo veriyor: Frost Field'in yavaslattigi kosucu 61
        // ref-px yol alir ve top ONU VURUR.
        TowerType.CANNON to TowerStats(
            type = TowerType.CANNON,
            role = TowerRole.SIEGE,
            unlockedAtLevel = 3,
            name = "Heavy Cannon",
            description = "Slow shell, wide blast. Ignores armour, misses fast movers.",
            buildCost = 95,
            tiers = listOf(
                TowerTier(1, range = 175f, damage = 46f, fireRate = 2.50f, upgradeCost = 0, unlockedAtLevel = 3),
                TowerTier(2, range = 205f, damage = 88f, fireRate = 2.20f, upgradeCost = 90, unlockedAtLevel = 3),
                // Kd.3 (Faz 13): DPS 40.0 -> 81.0 (x2.03; Kd.1->Kd.2 x2.17'nin
                // altinda). `splashRadius` ve mermi hizi KADEMEDEN BAGIMSIZ:
                // top hâlâ yavas mermi atar ve hizli hedefi iskalar (Faz 10 §1
                // komboları aynen gecerli). Kademe 3 topu "her seye vuran" bir
                // kule yapmaz, kumeye vurdugunda daha sert vurdurur.
                TowerTier(3, range = 235f, damage = 158f, fireRate = 1.95f, upgradeCost = 180, unlockedAtLevel = TIER_THREE_UNLOCK_LEVEL)
            ),
            splashRadius = 78f
        ),
        // ZIRH KIRICI. Atis araligi 1.4 -> 1.8 sn (kalabaliga karsi kasitli
        // verimsizlik: saniyede 0.55 atis) ve atis basina hasar 85 -> 110
        // (tek agir hedefe yikici). Artik gercekten FUZE atiyor: mermi yol
        // alir, hedef havadayken olurse fuze BOSA gider (yonlendirme yok) —
        // testcinin "fuze rampasi var ama fuze atmiyor" maddesi bu.
        TowerType.ANTI_ARMOR to TowerStats(
            type = TowerType.ANTI_ARMOR,
            role = TowerRole.ANTI_TANK,
            unlockedAtLevel = 7,
            name = "Missile Battery",
            description = "Armour-piercing missile. Devastating on heavies, wasted on swarms.",
            buildCost = 115,
            tiers = listOf(
                // ZIRH DELME ARTIK KADEMEYE YAYILDI (0,35 / 0,55 / 0,85).
                // Eskiden uc kademede de 0,85 idi ve zirhli birimlerin kimligi
                // Fuze'nin acildigi bolumde (L7) cokuyordu. Kademe 3
                // TIER_THREE_UNLOCK_LEVEL = 12'de aciliyor, yani "tam delici"
                // rolu Perde II'ye tasindi: L7-L11'de zirh gercek bir duvar,
                // L12'den sonra fuze eski gucune kavusuyor.
                TowerTier(1, range = 250f, damage = 110f, fireRate = 3.60f, upgradeCost = 0, unlockedAtLevel = 7, armorPierce = 0.35f),
                TowerTier(2, range = 290f, damage = 205f, fireRate = 3.10f, upgradeCost = 115, unlockedAtLevel = 7, armorPierce = 0.55f),
                // Kd.3 (Faz 13): DPS 66.1 -> 133.3 (x2.02). Atis basina hasar
                // (360) butun kademelerin en yukseği olmaya devam eder ve atis
                // araligi (2.70 sn) hâlâ en yavasi: kalabaliga karsi bilincli
                // verimsizlik kademe 3'te de duruyor. `armorPierce` ve carpma
                // alani degismedi — fuze hâlâ havada yol alir ve hedefi olurse
                // ISRAF olur (ANTI_TANK rolunun bedeli).
                TowerTier(3, range = 335f, damage = 360f, fireRate = 2.70f, upgradeCost = 230, unlockedAtLevel = TIER_THREE_UNLOCK_LEVEL, armorPierce = 0.85f)
            ),
            armorPierce = 0.85f,
            missileImpactRadius = 40f,
            missileImpactDamageFraction = 0.35f
        ),
        // DESTEK. Menzil 150 -> 270 (+%80): testci hakliydi, destek kulesinin
        // menzili MACHINE_GUN'dan kisaydi, yani sahada hicbir sey degistirmiyor
        // gibi duruyordu. Karsiligi odendi: hasar 6 -> 3 (artik gercekten hasar
        // vermez) ve yavaslatma %50 -> %42. Buna ragmen kule cok daha guclu,
        // cunku asil duzeltme MENZIL DEGIL: cryo darbesi artik 105 ref-px
        // yaricapindaki HERKESI sogutuyor (onceden yalnizca hedeflenen tek
        // dusmani). Fiyat 80 -> 100: hicbir hasar vermeyen ama bataryanin
        // penceresini 1.7 katina cikaran bir kule ucuz olamaz.
        TowerType.SLOW to TowerStats(
            type = TowerType.SLOW,
            role = TowerRole.SUPPORT,
            unlockedAtLevel = 5,
            name = "Frost Field",
            description = "Wide cryo pulses chill every enemy in the blast. Deals almost no damage.",
            buildCost = 100,
            tiers = listOf(
                // ⚠ HASAR +%50 (2026-08-21, kullanici karari): 3/7/13 -> 4,5/10,5/19,5.
                // Kimlik DEGISMEDI, yalnizca destek kulesinin cizik atma payi
                // buyudu; asagidaki %20 tavani hala saglaniyor (bkz. kademe 3).
                TowerTier(1, range = 270f, damage = 4.5f, fireRate = 1.30f, upgradeCost = 0, unlockedAtLevel = 5),
                TowerTier(2, range = 320f, damage = 10.5f, fireRate = 1.10f, upgradeCost = 85, unlockedAtLevel = 5),
                // Kd.3 (Faz 13): DESTEK kulesinin yukseltmesi hasar DEGIL, ALAN
                // ve TEMPO. Menzil 320 -> 370 (hâlâ en genis kapsama) ve darbe
                // araligi 1.10 -> 0.95 sn, yani sogutma penceresi daralmadan
                // daha sik yenilenir.
                //
                // DPS (hasar +%50 sonrasi): 19,5 / 0,95 = 20,5. Gatling Kd.3'un
                // 230 DPS'ine orani ~%9 — "hasar vermez" kimligi ve
                // BalanceConsistencyTest'in %20 tavani kademe 3'te hala gecerli.
                // Eski yorum "DPS 6,4 -> 13,7 / %6" diyordu ve artik bayat;
                // sayiyi burada guncel tutuyorum cunku bu depoda bayat yorum
                // defalarca yanlis karar urettirdi.
                // `slowFactor`/`slowPulseRadius` kademeden bagimsiz.
                TowerTier(3, range = 370f, damage = 19.5f, fireRate = 0.95f, upgradeCost = 170, unlockedAtLevel = TIER_THREE_UNLOCK_LEVEL)
            ),
            slowFactor = 0.42f,
            slowDuration = 2.2f,
            slowPulseRadius = 105f
        )
    )

    /**
     * Mermi "hizi". Motor `progress += dt * speed / 100f` isletiyor, yani
     * **ucus suresi = 100 / speed saniye ve MESAFEDEN BAGIMSIZ**. Bu yuzden bu
     * sayilar px/sn DEGIL; asagidaki yorumlarda gercek anlami olan ucus suresi
     * yazili. Renderer'a gomulmezler, denge burada durur.
     */
    val PROJECTILE_SPEEDS: Map<ProjectileType, Float> = mapOf(
        ProjectileType.BULLET to 300f,        // 0.33 sn
        ProjectileType.CANNON_SHELL to 110f,  // 0.91 sn — hizli hedefi iskalamasinin sebebi
        ProjectileType.MISSILE to 145f,       // 0.69 sn — "yol alir", israf olabilir
        ProjectileType.FROST_PULSE to 260f    // 0.38 sn
    )

    /**
     * Tek hedefli mermi carptiginda hedefi olmusse: isabet noktasinin bu kadar
     * REF-px yakinindaki en yakin dusmana yonlenir.
     *
     * Onceden sinir YOKTU (`enemies.minByOrNull { mesafe }`): olen hedefe giden
     * bir kursun haritanin obur ucundaki dusmana hasar tasiyordu. Fuze bu
     * yonlendirmeyi HIC kullanmaz (bkz. GameEngine.onProjectileImpact) — israf
     * olmasi kimliginin parcasi.
     */
    const val PROJECTILE_REDIRECT_TOLERANCE_REF_PX = 45f

    /**
     * Dalga temizleme ikramiyesi (Tedarik).
     *
     * Faz 10: motorda ciplak `35` olarak duruyordu — ekonominin tek-kaynak
     * kuralini ihlal ediyordu ve bolum uzunluguyla sessizce buyuyordu (L1'de
     * 175, L8'de 315). Ekonomi ajaninin olcumune gore 18'e cekildi
     * (SupplyBudgetModel.WAVE_CLEAR_SUPPLY_BONUS ile ayni olmasi
     * BalanceConsistencyTest'te kilitli).
     */
    const val WAVE_CLEAR_SUPPLY_BONUS = 12

    // ========================================================================
    // Faz 10 — DUSMAN ODULLERI x1/3 (ECONOMY_SPEC 9 madde 2)
    //
    // Ekonomi ajaninin teshisi: baskin kaldirac baslangic Tedariki DEGIL,
    // oldurme geliriydi. Gatling 60 Tedarik, piyade 12 oduyordu -> **5 oldurme
    // = 1 kule**; bir dalga 6-14 piyade getirdigi icin her dalga 1-3 kule
    // finanse ediyordu. Yeni tabloda bir kule ~15 oldurme eder.
    //
    // Olcek TAM OLARAK 1/3 secildi (0.375 degil): boylece her odul 3'e tam ya
    // da tama yakin bolunur ve dusmanlarin BIRBIRINE GORELI degeri korunur
    // (piyade 1.00 / kosucu 1.25 / tank 5.00 birebir ayni). 0.375 zirhliyi
    // piyadeye gore %18 degerlendirip hedef secimini sessizce bozardi.
    //
    // Kule kimlikleri ve hedef secimi DEGISMEZ; yalnizca akis hizi duser.
    //
    // ------------------------------------------------------------------------
    // Faz 12.1 — TABAN KALIBRASYONU YENIDEN OLCULDU: x3.5 -> **x1.1**
    //
    // INFANTRY 260->82 · FAST 160->50 · SHIELDED 525->165
    // ARMORED 770->242 · TANK 2030->638 · COMMAND_TANK 9100->2860
    // (hepsi Faz 10 oncesi taban tablonun 1.1 kati: 75/45/150/220/580/2600)
    //
    // NEDEN GERI ALINDI — CIHAZDA OLCULEN SEY
    // ---------------------------------------
    // Kullanici: *"level 1'de bile gelen adamlari olduremedim."* Dogru soylemis;
    // bolum 1 SAYIYLA gecilemez durumdaydi ve L2..L8 de oyle:
    //
    //   L1 baslangic Tedariki 80 = TAM BIR Gatling (60).
    //   Harita 1'de bir Gatling'in menzili (150 ref-px) yolun 1784 ref-px'inin
    //   yalnizca **442'sini** gorur. Piyade 65 ref-px/sn ile o pencereyi
    //   6.8 sn'de gecer; kule 43.75 DPS ile toplam 298 hasar teslim eder.
    //   Piyadenin cani ise **260**'ti. Yani tek kule tek piyadeyi ancak
    //   pencerenin SONUNDA olduruyordu ve W1 alti piyadeyi 1.15 sn arayla
    //   gonderiyordu: kapsama-farkindali simulasyonda W1'de **6 dusmanin 5'i
    //   sizdi**, bolum W3'te 20 canin tamami bitmis oluyordu. Butun kadro
    //   butcesi harcansa bile (4 kule Kd.1) bolum kaybediliyordu.
    //
    // KOK SEBEP: x3.5'i onaylayan olcum aracinin kendisi yanlisti.
    // `docs/tools/difficulty_audit.py` her kuleye **%100 doluluk** ve "ates
    // suresi = spawn penceresi + TUM rotanin yurume suresi" veriyor, yani
    // MENZIL KAPISI YOK. Motorda ise kule yalnizca yaricapinin ICINDEKI hedefe
    // ates eder (GameEngine.findTargetEnemyForTower). Bolum 1'de denetim bir
    // kuleye 33.7 sn atis suresi yaziyordu, gercek 6.8 sn: **5.0 kat** fazla
    // kredi. "Bolum 7 arz/talep 8.23" teshisi bu sisirilmis kredinin uzerine
    // kuruldu ve uzerine bir de atis araligi x2 (DPS yarilandi) bindi;
    // 2 x 3.5 = 7 katlik toplam sikilastirmanin ~5 kati olcum hatasiydi.
    //
    // YENI OLCUM: kapsama-farkindali simulasyon (menzil kapisi + tek hedef +
    // olculmus pad/rota geometrisi + bolum ici Tedarik dongusu). Meta yukseltme
    // SIFIR, guclendirici YOK. Sonuc, taban carpani x1.1'de:
    //   L1 dikkatli oyuncu 20/20 can (3 yildiz) · rastgele yerlestiren oyuncu
    //   medyan 15/20 (2 yildiz), 200 tohumun %100'u gecti · hic kule kurmayan
    //   KAYBEDER. L2..L8'in tamami dikkatli oyuncuyla gecilebilir.
    // Ayni olcum x1.2'de L1'i 11/20'ye dusuruyor, x1.75'te bolum yine
    // gecilemez oluyor; 1.1 bandin GUVENLI ucunda secildi.
    //
    // ATIS TEMPOSUNA DOKUNULMADI. Kullanici daha once "cok hizli ates
    // ediyorlar" demisti; `fireRate` ve atis basina hasar AYNEN duruyor
    // (bkz. TOWER_SPECS). Duzeltme tamamen bu tablodan yapildi.
    //
    // "ZORLUGU HP SISIREREK YUKSELTME" YASAGI (LEVEL_DESIGN E) IHLAL EDILMIYOR:
    // yasak **bolumler arasi** artisi HP ile yapmaya dairdir. Bu carpan TUM
    // dusmanlara AYNI oranla uygulanan bir OLCU BIRIMI duzeltmesidir, yani
    //   · dusmanlarin birbirine goreli tehdidi DEGISMEZ (AEHP siralamasi ayni),
    //   · kule uzmanlasmasi DEGISMEZ (oranlar korunur),
    //   · bolumler arasi egri DEGISMEZ (hepsi ayni carpanla olcekleniyor).
    // Kampanya egrisi hâlâ KOMPOZISYONLA yurutuluyor (WaveDefinitions).
    //
    // EKONOMIYE ETKISI YOK: `rewardGold`, `startingSupply`, dalga ikramiyesi ve
    // kule fiyatlari DEGISMEDI, dolayisiyla SPI tablosu (L1..L8 =
    // 2.28/1.85/1.88/2.03/1.70/2.22/1.62/1.76) BIREBIR AYNI kalir.
    //
    // Zirh, hiz, odul ve boyut DEGISMEDI.
    // Kilit: CampaignSolvabilityTest (kapsama-farkindali cozulebilirlik).
    // ------------------------------------------------------------------------
    // ========================================================================
    val ENEMY_SPECS: Map<EnemyType, EnemyStats> = mapOf(
        EnemyType.INFANTRY to EnemyStats(
            type = EnemyType.INFANTRY,
            name = "Infantry Squad",
            maxHp = 82f,    // 75 x1.1 (Faz 12.1 yeniden kalibrasyon; onceki 260 = x3.5)
            baseSpeed = 65f,
            armor = 0.0f,
            rewardGold = 4,   // 12 -> 4 (x1/3, ECONOMY_SPEC 9.2)
            sizeRadius = 14f
        ),
        EnemyType.FAST_SOLDIER to EnemyStats(
            type = EnemyType.FAST_SOLDIER,
            name = "Scout Runner",
            maxHp = 50f,    // 45 x1.1 (yukari yuvarlandi; onceki 160)
            baseSpeed = 115f,
            armor = 0.0f,
            rewardGold = 5,   // 15 -> 5 (hiz primi korunuyor: piyadenin 1.25 kati)
            sizeRadius = 12f
        ),
        // --------------------------------------------------------------------
        // Faz 10 — ZIRH YUKSELTILDI (0.55 -> 0.78 ve 0.70 -> 0.86).
        //
        // Testci: "her kule hepsinde ise yariyor." Olculen sebep buydu: eski
        // zirhla Gatling zirhli araca 39 DPS veriyordu ve **altin basina** en
        // iyi secenek olmaya devam ediyordu (0.66 DPS/altin, fuzenin 0.51'ine
        // karsi) — yani zirhli dusman bir KARSI-KOYMA degil sadece daha kalin
        // bir piyadeydi. Yeni degerlerle Gatling 19.3 DPS'e duser (0.32/altin),
        // fuze 54.0'a cikar (0.47/altin): oyuncu can barinin kursun altinda
        // KIMILDAMADIGINI gorur ve muhimmat degistirir.
        //
        // maxHp ve hiz DEGISMEDI: zorluk "HP sismesi" ile degil kompozisyon
        // zorunlulugu ile artiyor (LEVEL_DESIGN E). Cannon splash'i zirhi
        // bypass ettigi icin (DECISIONS B2) bolum 3'te acilan Cannon, bolum
        // 5'te gelen zirhli araca gecerli bir cevap olarak KALIR — kilit
        // tablosunun tutarliligi bunun uzerine kurulu.
        // --------------------------------------------------------------------
        EnemyType.ARMORED_VEHICLE to EnemyStats(
            type = EnemyType.ARMORED_VEHICLE,
            name = "Armored Car",
            maxHp = 242f,   // 220 x1.1 (onceki 770)
            baseSpeed = 50f,
            armor = 0.78f, // kursun %22'sini gecirir -> Gatling'e karsi duvar
            rewardGold = 9,   // 28 -> 9 (piyadenin ~2.25 kati)
            sizeRadius = 20f
        ),
        EnemyType.TANK to EnemyStats(
            type = EnemyType.TANK,
            name = "Heavy Tank",
            maxHp = 638f,   // 580 x1.1 (onceki 2030)
            baseSpeed = 32f,
            armor = 0.86f, // kursun %14 -> yalnizca patlama/delici ise yarar
            rewardGold = 20,  // 60 -> 20 (piyadenin 5 kati, oran birebir korundu)
            sizeRadius = 26f
        ),
        // --------------------------------------------------------------------
        // Faz 4 — DECISIONS B1. Degerler LEVEL_DESIGN.md C.1 on kosul patch'inden
        // AYNEN alindi; WaveMetrics.AEHP tablosu bu sayilara gore turetilmistir,
        // degistirilirse WaveDefinitionsTest kirilir.
        // --------------------------------------------------------------------
        EnemyType.SHIELDED_TROOPER to EnemyStats(
            type = EnemyType.SHIELDED_TROOPER,
            name = "Shielded Trooper",
            maxHp = 165f,   // 150 x1.1 (onceki 525)
            baseSpeed = 58f,
            armor = 0.62f,             // kursun neredeyse ise yaramaz
            rewardGold = 7,            // 22 -> 7 (x1/3, asagi yuvarlandi)
            sizeRadius = 16f,
            splashVulnerability = 1.6f // ...ama patlama zirhi bypass eder ve 1.6x vurur
        ),
        EnemyType.COMMAND_TANK to EnemyStats(
            type = EnemyType.COMMAND_TANK,
            name = "Command Tank",
            maxHp = 2860f,  // 2600 x1.1 (onceki 9100)
            baseSpeed = 30f,
            // Boss zirhi TANK'in USTUNDE kalmak zorunda (BalanceConsistencyTest),
            // tank 0.86'ya cikinca bu da 0.88'e cikti.
            armor = 0.88f,
            rewardGold = 60,  // 180 -> 60 (x1/3)
            sizeRadius = 38f
        )
    )

    // ========================================================================
    // Faz 4 — KAMPANYA TABLOSU
    //
    // Bolum <-> harita eslemesi TEK BIR YERDE burada durur. LEVEL_DESIGN.md B
    // tablosu revize edilirse yalnizca `CAMPAIGN` guncellenir; motor, bolum
    // secme ekrani ve testler bunu okur.
    // ========================================================================

    /**
     * Harita uzerine binen tamamen KOZMETIK katman (DECISIONS: oynanisa sifir etki).
     *
     * `DUSK` — CAMPAIGN_55.md 3.4: olculen en zayif biyom gecisi `desert -> autumn`
     * (ikisi de sicak/doygun). Act V'e autumn recolor'in USTUNE dusuk alfali sicak
     * bir perde eklenir; mekanizma `NIGHT` ile birebir ayni, tek yeni enum degeri.
     */
    enum class MapOverlay { NONE, NIGHT, DUSK }

    /**
     * Biyom varyanti (docs/BIOME_VARIANTS.md). Perde (act) basina bir biyom:
     * I temperate · II night · III winter · IV desert · V autumn.
     *
     * DIKKAT: bu enum yalnizca kampanya tablosunun NIYET beyanidir. Recolor
     * uygulamasinin kendi enum'u `game.model.Biome`'dur (BiomeVariants.kt) ve
     * `GameCanvas` cizim yolunda ONU okur; ikisi `BiomeCampaignTableTest` ile
     * eslesir.
     */
    enum class Biome { TEMPERATE, NIGHT, WINTER, DESERT, AUTUMN }

    /**
     * Bir kampanya bolumunun dalga DISI konfigurasyonu (LEVEL_DESIGN.md C.4).
     *
     * @param mapId 1..11 — `LevelGeometry.ALL_MAPS` icindeki OLCULMUS geometri.
     * @param deploymentCost Bolumu acmak icin gereken Coin (meta para birimi).
     *   0 = bastan acik. GDD B.2 tablosu.
     * @param disabledPadIds Bu bolumde GIZLENEN build pad'ler. Iki ayri sebep
     *   ayni alani besler:
     *   · **Act I** — [OUT_OF_RANGE_PADS]: pad, o bolumde kilidi acik kulelerin
     *     en buyuk kademe-1 menzili icinde AKTIF rotaya erisemiyor. Kule kurulsa
     *     hicbir seye ates edemezdi, yani secenek degil TUZAK.
     *   · **Act II** — [FROZEN_DISABLED_PADS]: krater kisiti (LEVEL_DESIGN.md
     *     F.3), menzil disi pad'ler dahil.
     *   Her ikisi de **DONDURULMUS** listedir (LEVEL_DESIGN.md F.4): calisma
     *   aninda algoritma KOSMAZ, cunku geometri degisirse sessizce farkli bir
     *   bulmaca uretir ve oyuncunun ogrendigi bolum bozulur. Uretim:
     *   docs/LEVEL_DESIGN.md §F.3 (uretim algoritmasi) ve §F.4 (dondurma
 *   yukumlulugu); olcum: docs/PAD_COVERAGE_REPORT.md.
     */
    data class LevelSpec(
        val levelId: Int,
        val mapId: Int,
        val act: Int,
        val deploymentCost: Int,
        val startingSupply: Int = INITIAL_GOLD,
        val maxBaseLives: Int = INITIAL_BASE_LIVES,
        val disabledPadIds: List<Int> = emptyList(),
        val overlay: MapOverlay = MapOverlay.NONE,
        val biome: Biome = Biome.TEMPERATE,
        /**
         * BOLUM DEGISTIRICILERI (CAMPAIGN_55.md 5.3 / K7). Varsayilani
         * [LevelModifiers.NONE], yani degistirici TASIMAYAN bir bolumun
         * davranisi BIREBIR eskisi gibidir. Tek kaynak [LEVEL_MODIFIERS].
         */
        val modifiers: LevelModifiers = LevelModifiers.NONE
    ) {
        /** Dalga sayisi TEK KAYNAKTAN gelir; burada kopyalanmaz. Getter = lazy. */
        val waveCount: Int get() = WaveDefinitions.waveCount(levelId)

        /** Bolum secme ekraninda gorunen ad. Ingilizce (lokalizasyon ajani alacak). */
        val displayName: String
            get() = MAP_NAMES_EN[mapId] ?: "Sector $mapId"

        /** KISITLI KADRO: bu kule tipi BU HAREKATTA kurulabiliyor mu? */
        fun allowsTowerType(type: TowerType): Boolean = modifiers.allows(type)

        /** MEVZI TAVANI: ayni anda tutulabilen azami kule (null = tavan yok). */
        val maxTowers: Int? get() = modifiers.maxTowers

        /** DONMUS MEVZI: dalga basladiktan sonra YENI kule kurulamaz mi? */
        val buildLockedDuringWave: Boolean get() = modifiers.buildLockedDuringWave
    }

    /**
     * ------------------------------------------------------------------------
     * BOLUM DEGISTIRICILERI — CAMPAIGN_55.md 5.3 (M1 / M2 / M3 varyanti)
     * ------------------------------------------------------------------------
     * Kampanya 55 bolum ama yalnizca 11 savas alani var: her harita bes kez
     * oynaniyor. Biyom yalnizca RENK degistirir; cesitlilik KURAL
     * DEGISIKLIGINDEN gelmek zorunda (dokumanin kendi tespiti). Ayni layout,
     * farkli kural = farkli bulmaca.
     *
     * TASARIM SOZLESMESI — bu tip **SAF VERI**dir:
     *  · Motor yalnizca kurali okur, BOLUM NUMARASI BILMEZ. `GameEngine` icinde
     *    hicbir yerde "eger bolum 19 ise" yazmaz.
     *  · Varsayilan [NONE], yani degistirici tasimayan 49 bolumun davranisi
     *    degismez — bu bir REGRESYON KAPISI, `LevelModifierTest` olcer.
     *  · Denge tarafi da ayni veriyi okur: `CampaignSimulator.LevelModel`
     *    kisiti TANIR, aksi halde cozulebilirlik testi yalan soylerdi.
     *
     * @param allowedTowers **KISITLI KADRO** (M1). `null` = kisit yok. Dolu ise
     *   yalnizca bu tipler kurulabilir; kule KILIDI (bolum bazli acilma) bunun
     *   USTUNE biner, yani kisit acik kuleleri DARALTIR, hic genisletmez.
     * @param maxTowers **MEVZI TAVANI** (M2). `null` = tavan yok. Ayni anda
     *   sahada tutulabilen azami kule sayisi; pad sayisindan BAGIMSIZ. Satis
     *   yer acar (tavan bir STOK kisitidir, kota degil).
     * @param buildLockedDuringWave **DONMUS MEVZI** (M3 varyanti). `true` ise
     *   dalga BASLADIKTAN sonra YENI kule kurulamaz. Yukseltme ve satis SERBEST
     *   kalir; gerekce: kural "catisma sirasinda tepki verme" degil "hattini
     *   dalga baslamadan kur" der. Yukseltmeyi de kapatmak, dalga icinde
     *   kazanilan Tedarigi OLU PARAYA cevirir (sessiz bir ekonomi vergisi) ve
     *   L4'te ogretilen satma/yeniden konumlandirma dersini kullanilamaz
     *   kilardi.
     */
    data class LevelModifiers(
        val allowedTowers: Set<TowerType>? = null,
        val maxTowers: Int? = null,
        val buildLockedDuringWave: Boolean = false,
        /**
         * KUSATMA EMRI (CAMPAIGN_55.md M7) — oldurme gelirine bolum bazli
         * carpan. `1f` = degisiklik yok.
         *
         * Motor odul yolunda ZATEN bir carpan isletiyor (`actRewardMultiplier`);
         * bu, ayni yere ikinci bir katsayi eklemek. Iki katsayi motorda TEK bir
         * `Float` degerinde birlestiriliyor — ayri ayri carpilsalardi kayan
         * nokta sirasi yuzunden motor ile `SupplyBudgetModel` bir Tedarik
         * ayrisabilirdi ve o hata bu depoda daha once cikti.
         *
         * Sordugu soru: "Butce tek seferlik. Yanlis kule = geri donusu yok."
         * Telafisi [startingSupplyBonus]: gelir kisilirken sermaye yukselir,
         * yani bolum FAKIRLESMEZ, gelirin ZAMANI degisir.
         */
        /**
         * HAZIRLIKSIZ DALGA (CAMPAIGN_55.md M5) — bu dalgalar hazirlik fazi
         * OLMADAN, bir oncekinin hemen ustune baslar. 1 tabanli dalga indeksi.
         *
         * Sordugu soru: "Bu iki dalgayi tek savunmayla mi karsilayacagim?"
         * Gercek zaman baskisi, hiz carpani olmadan.
         *
         * ILK DALGA HIC ISARETLENMEZ: oyuncunun bolume girerken savunmasini
         * kurmasi gerekiyor, aksi halde kural "bolum kaybedilmis basliyor"
         * demek olurdu. [prepSecondsFor] bunu kosulsuz uyguluyor.
         */
        val noPrepWaveIndices: Set<Int> = emptySet(),
        val supplyRewardMultiplier: Float = 1f,
        /**
         * Baslangic Tedarikine bolum bazli ek. [supplyRewardMultiplier]'in
         * telafisi; tek basina kullanilmasi tasarlanmadi.
         *
         * ⚠ BU EK `SupplyBudgetModel`E GIRMEZ ve bu BILINCLI bir ayrim.
         *
         * Ilk denemede ek modelin `startingSupply` fonksiyonuna konuldu ve
         * kampanya coktu: **dalga URETICISI o degeri okuyor**. L35'e 2.594
         * eklemek o bolumun DALGALARINI buyuttu, buyuyen dalgalar geliri
         * degistirdi, gelir butceyi degistirdi — dairesel bir bagimlilik ve
         * sekiz test birden kirildi.
         *
         * Dogru ayrim su: `SupplyBudgetModel` TASARIM katmanidir ve bolumun
         * degistiricisiz halini olcer; degistirici ONUN USTUNE, calisma
         * zamaninda biner. Model ile motorun "birebir ayni aritmetik" sozu bu
         * yuzden degistirici tasiyan bolumlerde KASITLI olarak ayrisir —
         * modelin olctugu sey zaten baska bir sey.
         */
        val startingSupplyBonus: Int = 0
    ) {
        /** Bu bolum HERHANGI bir degistirici tasiyor mu (UI rozetleri icin). */
        val isEmpty: Boolean
            get() = allowedTowers == null && maxTowers == null && !buildLockedDuringWave &&
                supplyRewardMultiplier == 1f && startingSupplyBonus == 0 &&
                noPrepWaveIndices.isEmpty()

        fun allows(type: TowerType): Boolean = allowedTowers?.contains(type) ?: true

        companion object {
            val NONE = LevelModifiers()
        }
    }

    /**
     * ------------------------------------------------------------------------
     * DEGISTIRICI TABLOSU — hangi bolumde hangi kural, ve NEDEN
     * ------------------------------------------------------------------------
     * Bu tur SISTEMI AYAGA KALDIRIR, kampanyaya YAYMAZ: alti bolum, hicbirinde
     * ikili kombinasyon yok. Yayilim ayri bir tur (CAMPAIGN_55.md 5.2'nin
     * L32/L42/L47/L52 kombinasyonlari).
     *
     * SECIM KURALLARI (hepsi CAMPAIGN_55.md ve LEVEL_DESIGN.md'den):
     *  · **Ogretici bandi (L1-L8) DOKUNULMAZ.** Cekirdek dongu once oturur.
     *  · **Bir degistirici ILK gorundugu bolumde YALNIZ durur.** Iki yeni kural
     *    ayni anda ogretilmez; bu yuzden perde acilislari (L12 krater + kademe 3,
     *    L23 · L34 · L45 perde gecisleri) ve boss bolumleri
     *    (L11/L16/L22/L33/L44/L55) KULLANILMAZ.
     *  · Ikinci gorunum ilkinden en az 5 bolum sonra ve FARKLI bir tonda gelir.
     *  · **ILK gorunum OLCULEREK secildi, tahminle degil.** Aday bolum/kural
     *    ciftleri `LevelModifierImpactReportTool` ile kisitli ve kisitsiz
     *    kosturuldu; ilk tanitimlar 7/7 davranisin gectigi ciftlerden secildi.
     *    (Olcum sirasinda elenen adaylar: L13'te Gatling'i kaldirmak 7/7'yi
     *    3/7'ye dusuruyordu — ilk ders icin fazla sert; Agir Top'u kaldirmak ise
     *    hicbir davranisi degistirmiyordu — dekoratif.)
     *
     * Bolum 15 · harita 08 · KADRO: **Gatling YOK**. Ilk kisit. Kaldirilan sey
     *   oyuncunun L1'den beri yaslandigi UCUZ IS ATI (60 Tedarik). Soru: "pahali
     *   kadroyla ayni hatti nasil kurarim". Olcum: 7/7 -> 7/7, yani kural
     *   ogretilir ama kimseyi kampanyada durdurmaz; degisen sey artan Tedarik
     *   ve kadro bilesimi.
     *
     * Bolum 19 · harita 04 · TAVAN **7 mevzi** (10 acik pad). Lakeside Ring
     *   kampanyanin EN GENIS tahtasi. Tavan dersini ("genislemek mi,
     *   derinlesmek mi") en genis tahtada vermek dersi en gorunur kilar: bos pad
     *   her yerde duruyor ama insa hakki bitmis. Tavan 5 DENENDI ve reddedildi:
     *   olcumde 830-1345 Tedarik harcanamadan kaliyordu, yani bolumun ikinci
     *   yarisinda hicbir ekonomi karari kalmiyordu. 7'de artan 81-813'e iner.
     *
     *   ⚠ SAYILAR 2026-08-19'DA KAYDI ve bu yorum bir gun bayat kaldi: harita
     *   04'un catal pad'leri birlestirilince 16 -> 14 pad, L19'un krater
     *   listesi de 5 -> 4 oldu, yani **11 acik pad 10'a indi**. Tavan 7'de
     *   BIRAKILDI: ders "bos pad goruyorum ama hakkim bitti" ve 10 acik pad ile
     *   7 tavan hâlâ UC bos pad birakiyor, yani ders gorunur kalmaya devam
     *   ediyor. 7'yi de dusurmek, olcumle reddedilmis olan 5'e dogru kaymak
     *   olurdu — o yon zaten kapali.
     *
     * Bolum 24 · harita 09 · DONMUS MEVZI. Kis perdesinin mekanigi
     *   (CAMPAIGN_55.md 5.2 bunu L23'e koyuyordu; L23 perde ACILISI oldugu icin
     *   bir bolum otelendi). Hazirlik fazi ilk kez GERCEK bir planlama ani olur.
     *
     * Bolum 32 · harita 01 · KADRO: **Fuze Rampasi YOK**. Ikinci kisit, FARKLI
     *   bir tondan: bu kez zirh cevabi gider ve oyuncu zirhi patlama ile
     *   (Agir Top, zirhi BYPASS eder) cozmek zorunda kalir. Olcum: 6/7 -> 5/7.
     *
     * Bolum 36 · harita 07 · TAVAN **6 mevzi** (9 acik pad). Ikinci tavan, DAR
     *   tahtada — ayni kural, bambaska bir his: L19'da bos pad bollugu vardi,
     *   burada zaten kit olan yerin uzerine bir de hak kisiti biner.
     *
     * Bolum 46 · harita 07 · DONMUS MEVZI. Ikinci tekrar. **L36 ile AYNI
     *   HARITA**: bu, dokumanin 6.1'deki 2. ekseninin ("ayni harita, farkli
     *   kisitlayici") ilk somut ornegi — harita 07 kampanyada bes kez oynaniyor
     *   ve bu iki gecis birbirinden RENKLE degil KURALLA ayriliyor.
     *
     * Kalan 49 bolum [LevelModifiers.NONE] tasir ve BIREBIR eski davranisi
     * gosterir.
     */
    val LEVEL_MODIFIERS: Map<Int, LevelModifiers> = mapOf(
        15 to LevelModifiers(
            allowedTowers = setOf(TowerType.CANNON, TowerType.ANTI_ARMOR, TowerType.SLOW)
        ),
        19 to LevelModifiers(maxTowers = 7),
        24 to LevelModifiers(buildLockedDuringWave = true),
        32 to LevelModifiers(
            allowedTowers = setOf(TowerType.MACHINE_GUN, TowerType.CANNON, TowerType.SLOW)
        ),
        // Bolum 35 · harita 06 · KUSATMA EMRI (CAMPAIGN_55.md M7). Dorduncu
        //   kural ve ilk kez EKONOMIYE dokunan kural: oldurme geliri SIFIR,
        //   telafisi baslangic Tedarikine biniyor.
        //
        //   SAYI OLCULEREK SECILDI: L35 bugun 788 sermaye + 2.594 oldurme
        //   geliri = 3.382 tasiyor. Telafi TAM O GELIR KADAR (2.594); x0,25 DENENDI
        //   VE OLCUMLE REDDEDILDI: odul tam sayiya kirpildigi icin kucuk
        //   oduller (5 -> 1) coktu ve gelir 2.594 yerine 132 cikti — yani
        //   x0,25 pratikte "%25 gelir" degil, dusman tipine gore ONGORULEMEZ
        //   bir kirpinti. CAMPAIGN_55.md zaten "(veya x0)" diyor; sifir hem
        //   tasarima sadik hem OLCULEBILIR.
        //   Bolum FAKIRLESMIYOR — gelirin ZAMANI degisiyor.
        //   Toplam butce sabit kaldigi icin SPI de bantta kaliyor; kural bir
        //   zorluk artisi degil, bir KARAR degisikligi.
        //
        //   Sordugu soru: "Butce tek seferlik. Yanlis kule = geri donusu yok."
        //   Oyuncu 2.734 Tedarikle basliyor ve dalgalar boyunca neredeyse hic
        //   gelir gormuyor; hazirlik fazi ilk kez bir SATIN ALMA PLANI oluyor.
        //
        //   YERI KURALLARA UYUYOR: L34 perde acilisi (kullanilmaz), L33 ve L44
        //   boss (kullanilmaz), L35 bos. Yanindaki L36 kadro tavani tasiyor ama
        //   o bir TEKRAR, yeni kural degil — "iki yeni kural ayni anda
        //   ogretilmez" kurali kirilmiyor.
        35 to LevelModifiers(
            supplyRewardMultiplier = 0f,
            startingSupplyBonus = 2594
        ),
        36 to LevelModifiers(maxTowers = 6),
        46 to LevelModifiers(buildLockedDuringWave = true)
    )

    /**
     * Bolumun degistiricileri. Tablo disi her bolum icin [LevelModifiers.NONE].
     *
     * Cagirma yeri TEK: [CAMPAIGN] kurulumu. Motor, UI ve simulator degistiriciyi
     * `LevelSpec.modifiers` uzerinden okur — kimse bu haritaya bolum numarasiyla
     * ikinci kez sormaz.
     */
    fun modifiersFor(levelId: Int): LevelModifiers =
        LEVEL_MODIFIERS[levelId] ?: LevelModifiers.NONE

    /**
     * KISITLI KADRO + KULE KILIDI birlikte: bu bolumde GERCEKTEN kurulabilir
     * tipler. Kisit acik kule kumesini yalnizca DARALTIR.
     */
    fun buildableTowers(levelId: Int): List<TowerType> {
        val mods = modifiersFor(levelId)
        return unlockedTowers(levelId).filter { mods.allows(it) }
    }

    /** Olculmus 11 haritanin Ingilizce adlari (GEOMETRY_REPORT.md 0 tablosu). */
    val MAP_NAMES_EN: Map<Int, String> = mapOf(
        1 to "Meadow Pass",
        2 to "Waterfall Woods",
        3 to "Dark Ravine",
        4 to "Lakeside Ring",
        5 to "Marshland",
        6 to "Ravine Crossing",
        7 to "Open Plain",
        8 to "Deep Forest",
        9 to "Rampart Slope",
        10 to "River Fork",
        11 to "Village Outskirts"
    )

    /**
     * ---------------------------------------------------------------------
     * APK'DA GERCEKTEN BULUNAN harita bitmap'leri.
     *
     * Su an `res/drawable-nodpi/` altinda YALNIZCA `bg_level_01.webp` var;
     * diger 10 harita hâlâ kaynak PNG olarak `copied items/` icinde bekliyor.
     * Bir bolum kendi bitmap'i olmadan yuklenirse geometri ile CIZILI harita
     * ayrisir (dusmanlar boyali yolun disinda yurur, pad'ler boslukta durur) —
     * yani oynanamaz. Bu yuzden motor eksik bitmap'te `MAP_FALLBACK_ID`
     * geometrisine duser: harita ile oynanis HER ZAMAN tutarli kalir.
     *
     * 10 bitmap `drawable-nodpi`'ye girdiginde burasi `(1..11).toSet()` olur,
     * motorda baska degisiklik GEREKMEZ.
     * (Not: bu KDoc bloguna dikkat — 11 haritanin TAMAMI pakette, yorumun
 * "yalnizca bg_level_01" varsayimi bayat.)
     * ---------------------------------------------------------------------
     */
    // Faz 4b: 11 harita bitmap'i de `drawable-nodpi/bg_level_01..11.webp` olarak
    // pakete girdi (1920 px, WebP q80, toplam 3.34 MB). Yedek harita mekanizmasi
    // yerinde kaliyor: ileride bir bitmap eksik kalirsa oynanis ile cizili harita
    // ayrismaz, sadece o bolum yedege duser.
    val SHIPPED_MAP_IDS: Set<Int> = (1..11).toSet()
    const val MAP_FALLBACK_ID = 1

    /** `bg_level_XX.webp` kaynak adindaki XX icin. Bkz. GameSprites.mapResFor(). */
    const val MAP_ID_MIN = 1
    const val MAP_ID_MAX = 11

    // ========================================================================
    // ACT (TUR) OLCEKLENDIRMESI
    //
    // Kampanya 11 haritayi IKI TUR oynatir (22 bolum). Ikinci turda ayni harita
    // tekrar gorunur; sadece gece overlay'i ve kapali pad'lerle degil, DUSMANIN
    // KENDISI de guclenmelidir, yoksa Act II bir tekrar gibi hissedilir.
    //
    // Yalnizca HP ve ODUL olceklenir. Hiz olceklenmez: hiz artisi hem
    // okunabilirligi bozar hem de "zorlugu sadece HP/hiz artirarak yukseltme"
    // yasagini ihlal eder (LEVEL_DESIGN E). Odul HP ile birlikte artar ki
    // Act II'de ekonomi ayni tempoda kalsin.
    //
    // AEHP olcumleri TABAN degerlerden hesaplanir; buradaki carpan olcume
    // girmez, yalnizca calisma aninda uygulanir.
    // ========================================================================
    // ------------------------------------------------------------------------
    // CAMPAIGN_55.md K5 — CARPAN 1,55'TE DONDURULDU (5 perde icin)
    //
    // ESKI FORMUL: `act >= 3 -> 1.55 + 0.45 * (act - 2)`. 22 bolumluk kampanyada
    // hic calismiyordu (act hep 1 veya 2 idi), ama kampanya 55 bolume cikinca
    // Act III 2,00 · Act IV 2,45 · **Act V 2,90** uretecekti. Bu su demek:
    // L55'te bir piyade 238 can tasir, L1'dekiyle GORSEL OLARAK BIREBIR AYNIDIR
    // ve oyuncuya hicbir yerde soylenmez. LEVEL_DESIGN E.5'in kendi yasak knob
    // listesi ("dusman HP'sine dokunmak") ve D'nin degismez kurali ("oyuncunun
    // ogrenmedigi mekanik zorunlu basari kosulu olamaz") ayni anda ihlal olurdu.
    //
    // Ikinci, olculmus zarar: carpan L11 -> L12 arasindaki TASARLANAN nefesi
    // (taban -%34) sahada +%1,8'e ceviriyordu. Ayni hata 55 bolumde DORT perde
    // gecisinde tekrar edecekti; dondurmak nefesleri geri getirir.
    //
    // Perde III-V agirligi nereden geliyor? KOMPOZISYONDAN: taban AEHP Act II ->
    // Act V arasinda ~1,9 kat artiyor ve artisin tamami dusman KARISIMINDAN
    // geliyor (govde basina ortalama AEHP yukseliyor). Carpan gerekmiyor.
    //
    // Gorunurluk borcu (bu gorevin kapsaminda DEGIL, sanat/UI ajanina devredildi):
    // 1,55 carpani "Veteran birlik" kimligiyle gorunur kilinacak — Act II+
    // dusmanlarina celik tonu tint + "Veteran ..." adi + L12 brifing satiri.
    // ------------------------------------------------------------------------
    fun actHpMultiplier(act: Int): Float = if (act <= 1) 1.0f else 1.55f

    fun actRewardMultiplier(act: Int): Float = if (act <= 1) 1.0f else 1.30f


    /**
     * Yildiz esikleri **YUZDE** cinsindendir, mutlak can degil. Us cani bolume ve
     * meta yukseltmelere gore degistigi icin (`LevelSpec.maxBaseLives`, GDD: 20 ->
     * meta ile 30) mutlak esik yanlis yildiz verirdi. GDD B.3: yildiz = kalan us
     * cani yuzdesi.
     */
    const val STAR3_LIVES_FRACTION = 0.90f
    const val STAR2_LIVES_FRACTION = 0.50f

    /**
     * Catallanan haritalarda (1, 2, 3, 4, 11) rota atamasinin seed'i.
     * RNG render'dan AYRI ve seed'li: ayni bolum her zaman ayni rota dizisini
     * uretir, yani replay/test/denge dogrulamasi mumkun kalir.
     */
    const val ROUTE_RNG_SEED_BASE = 0x5F1D3A7L

    /**
     * IKINCI KOL (catallanma) kacinci bolumden itibaren KULLANILIR.
     *
     * BULGU (beklenenin tersi): ikinci kol "kampanyada kullanilmiyor" degildi —
     * `LevelData.routesForMapId` gercek catallanan haritalar icin (1, 2, 4, 11)
     * iki rotayi da donduruyor ve motor spawn basina seed'li secim yapiyordu.
     * Yani ikinci kol ZATEN acikti, ustelik **ogretici bolumlerde de** (harita
     * 1, 2, 4 = bolum 1, 2, 4).
     *
     * Bu yanlisti: iki koldan eszamanli akis, tek kulenin kapsamasinin fiziksel
     * olarak yetmedigi durum — yani bir BECERI sinavi. Oyuncu daha tek kolu
     * savunmayi ogrenmeden bu sinava sokulmamali; ustelik ilk bolumlerde
     * Tedarik yalnizca bir kuleye yetiyor, dolayisiyla ikinci kol "ogret" degil
     * "cezalandir" oluyordu.
     *
     * Bu yuzden ogretme dilimi TEK KOL kalir; catallanma sonra devreye girer.
     * Secim hâlâ seed'li ve deterministik: RNG tek basina bir yenilgi sebebi
     * olamaz, ayni bolum her oynanista ayni dizidir.
     *
     * -----------------------------------------------------------------------
     * DUZELTME — ESIK 9'DAN 3'E INDI (cihaz geri bildirimi)
     * -----------------------------------------------------------------------
     * Kullanici: *"gelecekleri yol belli oluyor, bilerek mi boyle? bence tumu
     * aktif olmali ki gelecekleri yol belli olmasin."*
     *
     * Kok neden pad RENKLERI degildi, esikti. Esik 9 iken bolum 1, 2 ve 4'te
     * (harita 1, 2, 4 — hepsi gercek catal) haritada IKI yol cizili ama
     * yalnizca biri kullaniliyordu. [OUT_OF_RANGE_PADS] olu koldaki pad'leri
     * gizledigi icin **kalan yesil pad'ler kullanilan kolu ciziyordu**: oyuncu
     * daha dalga baslamadan dusmanin hangi koldan gelecegini goruyordu.
     * Yani haritanin yarisi dekoratifti ve gizleme bunu ele veriyordu.
     *
     * OLCUM (`ForkThresholdProbe` verisi, menzil = o bolumun en uzun kademe-1'i):
     *
     *   bolum | tek kol olu pad | IKI kol olu pad | iki kolu goren pad | tedarik
     *   ------|-----------------|-----------------|--------------------|--------
     *     1   | 1,3,5,7,9  (5)  | — (0)           | 10          (1)    |  80
     *     2   | 3,4,7,8,10,12(6)| 3,10 (2)        | 2,9,11,13   (4)    |  90
     *     4   | 1,4,6,7,9,11,12 | — (0)           | 16          (1)    | 215
     *
     * Yani catal acilinca gizleme neredeyse tamamen ORTADAN KALKIYOR (L1 ve
     * L4'te hic olu pad kalmiyor) — sizinti kaynagi kuruyor.
     *
     * NEDEN 3, NEDEN 1 DEGIL: L1'de Tedarik 80 = **tek** Gatling ve iki kolu
     * ayni anda goren tek pad var (id 10). Iki kolu birden acmak bolum 1'i
     * "tek dogru cevabi bul" bulmacasina cevirirdi ve RNG dogrudan yenilgi
     * sebebi olurdu — bu, duzeltilmeye calisilan hatanin ta kendisi. L2'de de
     * Tedarik hâlâ tek kule (90).
     *
     * 3, **baslangic Tedariginin birden fazla kule aldigi ILK bolumdur**
     * (L3 = 215 >= 2 x 60). Sayi elle secilmedi, [EARLY_STARTING_SUPPLY]
     * tablosunun bittigi yerdir ve `BalanceConsistencyTest` bu kurali kilitler.
     * Ogretme dilimi (L1-L2) tek kol kalir; ucuncu bolumden itibaren oyuncu
     * iki cepheyi kurabilecek sermayeyle girer ve
     * [COVERAGE_SCARCITY_SUPPLY_FACTOR] catallanan bolumlerde sermayeyi
     * otomatik olarak 1,5 ile carpar.
     *
     * KAMPANYADA NEYI DEGISTIRIR: yalnizca **bolum 4**. Bolum 3, 5-8'in
     * haritalarinda gercek catal yok (harita 3'un kanonik rotasi tektir),
     * bolum 9+ zaten catalliydi.
     */
    const val ALT_ROUTE_FIRST_LEVEL = 3

    /** Bu bolumde catallanma (ikinci kol) devrede mi? */
    fun usesAlternateRoutes(levelId: Int): Boolean = levelId >= ALT_ROUTE_FIRST_LEVEL

    /**
     * MENZIL DISI PAD KISITI (Act I). Bir pad, o bolumde KILIDI ACIK kulelerin
     * en buyuk kademe-1 menzili icinde AKTIF rotaya erisemiyorsa oyuncuya
     * gosterilmez: aksi halde kurulan kule hicbir seye ates edemez ve tedarik
     * bosa gider. Esik bolume gore degisir (MACHINE_GUN 150 -> CANNON 175 ->
     * SLOW 270). Olcum: docs/PAD_COVERAGE_REPORT.md tablo 2.
     *
     * NEDEN GEREKLI (cihaz geri bildirimi "level 1'de bile olduremedim"):
     * bolum 1'de 10 pad'in 5'i (1, 3, 5, 7, 9) aktif rotaya 194-444 ref-px
     * uzakta, Gatling menzili ise 150. Baslangic tedariki 80 = TEK kule, yani
     * bilgisiz oyuncu %50 ihtimalle 60 tedarigi hicbir seye ates etmeyen bir
     * kuleye yatiriyordu. Bu, ogretilmemis bir bilginin zorunlu basari kosulu
     * yapilmasidir.
     *
     * NEDEN "AKTIF ROTA": bolum < [ALT_ROUTE_FIRST_LEVEL] iken ikinci kol
     * motorda HIC kullanilmaz (GameEngine: `routes = listOf(allRoutes.first())`).
     * Iki kolun minimumuna bakmak pad'i olmadigi kadar iyi gosterir — hatanin
     * 22 bolum boyunca testten kacmasinin sebebi tam olarak buydu.
     *
     * NEDEN "kademe 1": oyuncu kuleyi once KURAR, sonra yukseltir. Kademe-2
     * menziliyle olcmek erisilemeyen bir gelecegi bugunun gecerliligi sayar.
     *
     * DIKKAT: `CAMPAIGN` bunu initializer'inda okuyor — CAMPAIGN'DEN ONCE
     * durmak ZORUNDA.
     */
    private val OUT_OF_RANGE_PADS: Map<Int, List<Int>> = mapOf(
        1 to listOf(1, 3, 5, 7, 9),         // harita 01 · esik 150 · 10 -> 5 pad
        2 to listOf(3, 4, 7, 8, 10, 12),    // harita 02 · esik 150 · 13 -> 7 pad
        // harita 03 · esik 175 · v5'te rota SANATTAN yeniden uretildi ve
        // ONEMLI OLCUDE YER DEGISTIRDI: eski rota sag ucta boyali yolu birakip
        // kayaliktan ussun KUZEYINE kesiyordu (rampa ucu 318 ref-px yanlis
        // yerdeydi). Rota artik serpantini sonuna kadar takip edip ussun guney
        // kapisina variyor ve koridorun ORTASINDAN geciyor. Sonuc: pad 10
        // 327 -> 145 ref-px'e dustu, pad 6 ise 173'te kaldi. 12 pad'in
        // 12'si de menzil icinde, yani gizlemenin tek mesru gerekcesi kalmadi.
        3 to emptyList(),                   // harita 03 · esik 175 · 12 -> 12 pad
        // harita 04 · esik 175 · IKI KOL DA AKTIF (esik 3'e indi) -> olu pad YOK.
        // Eski liste (1,4,6,7,9,11,12) B-kolunun pad'leriydi; o kol artik
        // gercekten kullaniliyor, dolayisiyla gizlemenin tek mesru gerekcesi
        // (menzil disi olmak) ortadan kalkti. 14 pad'in 14'u acik.
        4 to emptyList(),
        // harita 05 · v4 rota merkezlemesi pad 3'u 274 -> 262 ref-px'e getirdi
        // (esik 270) — artik calisan bir pad, gizlenmesi oyuncudan secenek calar.
        5 to emptyList(),                   // harita 05 · esik 270 · 12 -> 12 pad
        6 to listOf(4, 10),                 // harita 06 · esik 270 · 10 -> 8 pad
        7 to listOf(3, 6),                  // harita 07 · esik 270 · 11 -> 9 pad
        8 to listOf(6, 8, 11),              // harita 08 · esik 270 · 11 -> 8 pad
        9 to listOf(5),                     // harita 09 · esik 270 · 12 -> 11 pad
        10 to listOf(8, 17)                 // harita 10 · esik 270 · 17 -> 15 pad
        // 11: olu pad yok (ikinci kol acik, esik 270)
    )

    /**
     * Act II krater kisiti — LEVEL_DESIGN.md F.3 algoritmasi geometri uzerinde
     * BIR KEZ kosturuldu ve F.4 uyarinca donduruldu. Uretici betik ve dogrulama
     * tablosu: docs/PAD_COVERAGE_REPORT.md §5.2.
     *
     * Hepsi D1 (%25-40 devre disi), D2 (kalan >= max(6, 0.60x toplam)),
     * D3 (her dolu bolgede >=1 CANLI pad), D4 (900 ref-px'lik kapsamasiz yol
     * parcasi yok), D6 (Z5'te >=2, Z5 toplami >=2 ise) kisitlarini SAGLIYOR.
     *
     * DUZELTME (docs/PAD_COVERAGE_REPORT.md 5.2): ilk donmus liste, uretilirken
     * `min(birincil, ikinci kol)` mesafesi kullanildigi icin 9 MENZIL DISI pad'i
     * acik birakmis ve yerine CALISAN pad'leri kapatmisti. Liste AYNI BOYUTTA
     * yenilendi (D1/D2 otomatik korunur) ve artik her menzil disi pad'i iceriyor.
     * Degisen bolumler: 13, 14, 15, 17, 18, 20.
     *
     * BILINEN ISTISNA: harita 8'in Z5 bolgesi TEK pad'den (id 8, aktif rotaya
     * 316 ref-px) ibaret ve o pad her bolumde menzil disi. Gizlenmesi Z5'i
     * tamamen bosaltir; pad'i sanat uzerinde tasimak bir TASARIM isidir
     * (PAD_COVERAGE_REPORT 8. bolum) ve bu degisiklige dahil DEGILDIR.
     *
     * DIKKAT: `CAMPAIGN` bunu initializer'inda okuyor. Kotlin object govdesi
     * yukaridan asagi kosar, bu yuzden burasi CAMPAIGN'DEN ONCE durmak ZORUNDA.
     */
    private val FROZEN_DISABLED_PADS: Map<Int, List<Int>> = mapOf(
        12 to listOf(1, 4, 5),            // harita 11 · 10 -> 7 (%30) · degismedi
        13 to listOf(6, 8, 9, 15, 17),    // harita 10 · 17 -> 12 (%29) · +8,+17 olu; -3,-12
        14 to listOf(1, 4, 5),            // harita 09 · 12 ->  9 (%25) · +5 olu; -3
        15 to listOf(4, 6, 8, 11),        // harita 08 · 11 ->  7 (%36) · +6,+8,+11 olu; -1,-7,-10
        16 to listOf(3, 6, 9),            // harita 07 · 11 ->  8 (%27) · degismedi (3,6 zaten olu)
        17 to listOf(4, 8, 10),           // harita 06 · 10 ->  7 (%30) · +4,+10 olu; -2,-5
        18 to listOf(1, 3, 4, 7),         // harita 05 · 12 ->  8 (%33) · +3 olu; -10
        // harita 04 · 14 -> 10 (%29) · PAD BIRLESTIRMESIYLE YENIDEN NUMARALANDI.
        // Eski liste (3, 6, 9, 12, 15) eski id uzayindaydi. Esleme:
        //   3 -> 4 · 9 -> 8 · 12 -> 10 · 15 -> 13 · 6 ise yeni pad 3'e KAYNADI.
        // Yeni pad 3 KAPATILMADI: catalin iki kolunu birden tutan iki pad'den
        // biri o, ve bu bolumde kapatmak degisikligin amacini tam olarak iptal
        // ederdi. Dort pad %29 eder — D1'in (%25-40) icinde; kalan 10 pad
        // D2'yi (>= max(6, 0.60 x 14 = 8,4)) saglar.
        19 to listOf(4, 8, 10, 13),
        20 to listOf(2, 8, 10, 11),       // harita 03 · 12 ->  8 (%33) · +10 olu; -5
        21 to listOf(1, 4, 7, 10),        // harita 02 · 13 ->  9 (%31) · degismedi
        22 to listOf(3, 5, 9)             // harita 01 · 10 ->  7 (%30) · degismedi
    )

    /**
     * Faz 10 — BOLUM BAZINDA BASLANGIC TEDARIKI (ECONOMY_SPEC 9 madde 1).
     *
     * Eskiden 22 bolum de 150 ile basliyordu. Ilk bolumlerde bu, oyuncuya
     * hazirlik fazinda **iki-uc kule birden** kurma imkani veriyordu; yani
     * "hangi kuleyi once kurayim" karari hic olusmuyordu. Yeni tabloda L1'de
     * tam bir Gatling parasi var: ikinci kule KAZANILIR.
     *
     * Ilk 6 bolum dısında (L7+) taban 150 olarak kalir — o noktada oyuncu meta
     * yukseltmeleri ve daha pahali kadrolarla oynuyor.
     *
     * `SupplyBudgetModel.startingSupply(level)` ile ayni olmasi
     * BalanceConsistencyTest ve SupplyBudgetTest'te KILITLI.
     */
    /**
     * OGRETICI SERMAYE — yalnizca L1 ve L2.
     *
     * Bu iki bolumde kisitin KENDISI derstir: "tek silahini nereye koyacaksin"
     * (L1 = 80 = tam bir Gatling) ve "ikinci kuleyi KAZAN" (L2 = 90).
     * L3'ten itibaren sermaye [startingSupplyFor]in kadro kuralindan gelir;
     * eski 110/120/140/150 satirlari bolumler 8 dalgadan 5'e inince gelirin
     * yarisini goturdu ve SPI'yi bandin altina dusurdu (L4 1,33 · L5 1,31).
     */
    /**
     * ⚠ 2026-08-19 — L1/L2 SERMAYESI 80/90 -> 120/130.
     *
     * Eski tasarim gerekcesi suydu: L1 = 80 = TAM BIR GATLING, "ikinci kuleyi
     * KAZAN". Niyet dogruydu ama olcum yanlis yerden geliyordu:
     * `CampaignSolvabilityAllLevelsTest` bolumun cozulebilir oldugunu
     * gosteriyor — ancak simulator MUKEMMEL oynar (dogru pad, dogru an,
     * hedeflemeyi bilir). Bolumun ILK KEZ oynayan biri tarafindan
     * kazanilabilir oldugunu HICBIR sey olcmuyordu.
     *
     * Cihaz kanit: oyuncu L1de tek Gatling ile kaybediyor. Bir tower defense
     * oyununun BIRINCI bolumu, kurallari daha yeni ogrenen birine kaybettirmez;
     * ilk bolum dersin kendisidir, sinav degil.
     *
     * 120 = tam IKI Gatling (buildCost 60). Ders kaybolmuyor, YER DEGISTIRIYOR:
     * artik soru "tek silahini nereye koyacaksin" degil, "iki silahini yolun
     * neresinde kesistireceksin" — ve bu, oyunun gercek core loopu.
     *
     * L2 DE BIRLIKTE TASINDI (90 -> 130). Istenmemisti ama 120den 90a dusmek
     * oyuncuyu iki kuleden birbucuga indirirdi; ikinci bolum birincisinden
     * daha fakir baslayamaz. Ayni +40 farki korunuyor.
     */
    private val EARLY_STARTING_SUPPLY = listOf(120, 130)

    /**
     * ------------------------------------------------------------------------
     * TASARLANAN KADRO BUYUKLUGU `R` — bolumde elde olmasi BEKLENEN kule sayisi
     * ------------------------------------------------------------------------
     * CAMPAIGN_55.md 9. tablosunun `R` kolonu. Ekonomi katmanindaki
     * `SupplyBudgetModel.DESIGNED_ROSTER_SIZE` ile BIREBIR ayni olmasi
     * `BalanceConsistencyTest` icinde kilitlidir; iki katman ayni sayiyi
     * bagimsiz olarak turetir, bu yuzden ikisi de duruyor.
     */
    private val DESIGNED_ROSTER_SIZE: IntArray = intArrayOf(
        // L1..L22 YENIDEN OLCULDU (bolum 5-7 dalgaya inince). Kadro artik hem
        // KULE SAYISI hem KULE PAHALILIGI ile buyuyor: L7'de Fuze acilinca
        // kadro 4'e cikar (kademe-2 maliyeti 495 -> 725), L17'de 5'e.
        /* L1..L11  */ 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4,
        /* L12..L22 */ 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5,
        /* L23..L33 */ 5, 5, 5, 6, 5, 6, 6, 6, 5, 6, 6,
        /* L34..L44 */ 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7,
        /* L45..L55 */ 6, 6, 6, 7, 6, 7, 7, 7, 6, 7, 7
    )

    /** Kule acilis sirasi — `SupplyBudgetModel.designedRoster` ile AYNI kural. */
    private fun unlockOrderAt(levelId: Int): List<TowerType> =
        TowerType.values()
            .filter { TOWER_SPECS.getValue(it).unlockedAtLevel <= levelId }
            .sortedWith(
                compareBy(
                    { TOWER_SPECS.getValue(it).unlockedAtLevel },
                    { TOWER_SPECS.getValue(it).buildCost },
                    { it.name }
                )
            )

    /**
     * **KAPSAMA KITLIGI SERMAYE CARPANI — 1,5.**
     *
     * Iki farkli sebep AYNI oynanis sorununu uretir: bir pad'in gordugu yol
     * payinin yetmemesi.
     *
     *  · **Catallanan harita** (1, 2, 4, 11): ikinci kol
     *    `ALT_ROUTE_FIRST_LEVEL`den itibaren devrededir ve motor her dusmani
     *    seed'li olarak iki koldan birine atar. Oyuncu ayni tehdide karsi IKI
     *    cephe kurar. LEVEL_DESIGN §G.4 bunu kampanyanin "en kritik acik
     *    maddesi" olarak isaretlemisti.
     *  · **Dar tahta** ([TIGHT_BOARD_SPARE_PADS]): acik pad sayisi, tasarlanan
     *    kadronun ancak bir fazlasi kadarsa oyuncunun YERLESTIRME secenegi
     *    yoktur; yanlis pad'i secmenin telafisi de yoktur.
     *
     * OLCUM (kapsama-farkindali simulasyon, `CampaignSolvabilityAllLevelsTest`):
     * sermaye kurali kadro kademe-1 maliyetine yukseltildiginde gecilemeyen
     * bolum sayisi 38'den 11'e dustu ve **kalan 11'in 11'i de catallanan
     * haritalardaydi** (L29/31/32/33/39/41/42/43/49/51/53 -> harita 4/2/1/11).
     * Carpan 1,5 uygulandiginda 11 -> 2. Dar tahta kolu ise "yalnizca TEK bir
     * oynanis bicimiyle gecilebilen bolum" olcumunden geldi (L33/L46/L53).
     */
    private const val COVERAGE_SCARCITY_SUPPLY_FACTOR = 1.5f

    /**
     * ERKEN BOLUMLER ICIN AYRI (ve daha kucuk) kapsama carpani.
     *
     * ⚠ 2026-08-22, GERCEK INSAN OYNANISI (bu depoda ilk kez; simulasyon
     * degil): *"1-5 level fazla kolay... 4. levelde onlarin base onune iki
     * gatling koydum, levellerini max yaptim, ki armory'den yukseltme
     * yapmadim daha. Kendi baselerinden bile cikamadan oldüler."*
     *
     * OLCUM: L4 catallanan bir harita oldugu icin sermayesi 215 x 1,5 = **323**
     * Tedarik. Bir maksimum (kademe-2) Gatling 60 + 65 = 125; **ikisi 250**.
     * 323 >= 250, yani oyuncu ILK DALGA BASLAMADAN, hic reklam izlemeden ve
     * hic oldurme geliri kazanmadan iki tam yukseltilmis kule dikebiliyordu.
     * Tasarlanan kadro L4 icin 3 kule/kademe-1 idi.
     *
     * NEDEN GLOBAL CARPAN DUSURULMEDI: 1,5 degeri gec oyunda **gerekli
     * asgari** olarak olculmus — kapsama-farkindali simulasyonda gecilemeyen
     * bolum sayisini 11'den 2'ye indiren sey o (L29/31/32/33/39/41/42/43/49/
     * 51/53, hepsi catallanan harita). Global dusurmek gec oyunda soft-lock
     * riskini geri getirirdi. Bu yuzden ayrim BOLUM ESIGIYLE yapildi.
     *
     * ⚠ DURUST SINIR: bu carpan TEK BASINA sorunu cozmez. L4 sermayesi
     * 323 -> 258 olur ve 258 hala 250'nin ustundedir, yani iki maks Gatling
     * teorik olarak hala alinabilir. Asil kilit [UPGRADE_UNLOCK_AFTER_WAVES]:
     * yukseltme ilk dalga temizlenene kadar kapali oldugu icin "aninda maks"
     * yolu kapaniyor. Bu carpan yalnizca fazla sermayeyi kirpar — ikisi
     * BIRLIKTE calisir, ayri ayri degil.
     */
    private const val EARLY_COVERAGE_SCARCITY_SUPPLY_FACTOR = 1.15f

    /** [EARLY_COVERAGE_SCARCITY_SUPPLY_FACTOR] bu bolume kadar (dahil) gecerli. */
    private const val EARLY_COVERAGE_SCARCITY_MAX_LEVEL = 11

    /**
     * Kule yukseltmesi kac dalga temizlendikten SONRA acilir.
     *
     * 1 = "ilk dalgayi gormeden yukseltemezsin". Gerekce ve olcum
     * `GameEngine.upgradeLockedUntilFirstWave` KDoc'unda.
     *
     * 0 yazilirsa kural TAMAMEN kapanir ve davranis 2026-08-22 oncesine doner
     * — yumusatmak gerekirse once burasi denenmeli, kural silinmeden.
     */
    const val UPGRADE_UNLOCK_AFTER_WAVES: Int = 1

    /**
     * Tahta "dar" sayilir: acik pad <= kadro + bu kadar yedek.
     *
     * K-5 zaten `acik >= R + 2` istiyor; 3, o tabanin hemen ustudur, yani
     * kural yalnizca **bir tek yedek pad'i olan** tahtalara uygulanir.
     */
    private const val TIGHT_BOARD_SPARE_PADS = 3

    /**
     * ------------------------------------------------------------------------
     * BOLUM BASLANGIC TEDARIKI — **TEK KURAL**
     * ------------------------------------------------------------------------
     *  · L1..L6  : yayinlanmis ogretici rampasi (80/90/110/120/140/150). Burada
     *    kisitin KENDISI ders: "tek silahini nereye koyacaksin".
     *  · L7+     : **tasarlanan kadronun KADEME-1 maliyeti** — yani oyuncu
     *    bolume kadrosunu KURABILECEK sermaye ile girer, yukseltmeleri kazanir.
     *  · Kapsamanin KIT oldugu bolumde (catallanan harita ya da dar tahta)
     *    [COVERAGE_SCARCITY_SUPPLY_FACTOR] ile carpilir.
     *
     * NEDEN DEGISTI (olculdu, tahmin degil): eski tabloda L7..L22 duz 150,
     * Act III/IV/V 220/270/320 idi. Ayni anda kadro 5-7 kuleye ve bolum 5-7
     * dalgaya inmisti — yani oyuncunun kadrosunu kurmak icin ne parasi ne
     * dalgasi vardi. Simulasyonun dalga izi bunu tek satirda gosteriyordu:
     *
     *   L35  W1[govde=40 sizinti=20 can=0 kule=2 tedarik=...]
     *
     * 270 Tedarik IKI kule alir; acilis dalgasi 40 govde gonderir; 20 canin
     * tamami ILK DALGADA biter. Bu bir zorluk degil, bir aritmetik hatasidir ve
     * cihazda "level 1'de bile olduremedim" sikayetinin gec-oyun karsiligidir.
     *
     * SPI'YE ETKISI KONTROLLU: uretilmis bolumlerde hedef oldurme geliri
     * `2 x I(L) - baslangic - 18 x (N-1)` oldugu icin sermaye artisi dogrudan
     * dalga agirligindan DUSULUR — SPI 2,00'de kalir, bolum hafifler.
     */
    fun startingSupplyFor(levelId: Int): Int {
        val base = startingSupplyBaseFor(levelId)
        val early = levelId <= EARLY_STARTING_SUPPLY.size
        if (early || !hasScarceCoverage(levelId)) return base
        // Erken bolumlerde daha kucuk carpan — gerekcesi
        // EARLY_COVERAGE_SCARCITY_SUPPLY_FACTOR KDoc'unda (cihaz raporu).
        val factor = if (levelId <= EARLY_COVERAGE_SCARCITY_MAX_LEVEL) {
            EARLY_COVERAGE_SCARCITY_SUPPLY_FACTOR
        } else {
            COVERAGE_SCARCITY_SUPPLY_FACTOR
        }
        return Math.round(base * factor)
    }

    /**
     * [startingSupplyFor]in KAPSAMA DUZELTMESI UYGULANMAMIS hali — yani "bu
     * bolumun kadrosu kac eder" sorusunun saf cevabi.
     *
     * NEDEN AYRI: kampanya butcesinin bolum bolum ARTMASI beklenir, ama
     * [COVERAGE_SCARCITY_SUPPLY_FACTOR] bir zorluk rampasi degil bir
     * TELAFIDIR: catallanan ya da dar tahtali bolumde ayni kadroyu kurmak
     * daha pahaliya gelir. Bu yuzden monotonluk TABAN uzerinde olculmelidir;
     * aksi halde "bolum 4 catal oldu" gibi bir tasarim karari, ilgisiz bir
     * monotonluk testini kirar ve karar sayilarla degil testin sinirlariyla
     * verilmeye baslanir. (Tam olarak bu oldu: esik 9 -> 3 degisikliginde
     * L4 323, L5 255 cikti.)
     */
    fun startingSupplyBaseFor(levelId: Int): Int {
        if (levelId <= EARLY_STARTING_SUPPLY.size) {
            return EARLY_STARTING_SUPPLY.getOrElse(levelId - 1) { INITIAL_GOLD }
        }
        val order = unlockOrderAt(levelId)
        val roster = DESIGNED_ROSTER_SIZE.getOrElse(levelId - 1) { 5 }
        return (0 until roster).sumOf { TOWER_SPECS.getValue(order[it % order.size]).buildCost }
    }

    /** Bu bolumde gorunur (uzerine kule kurulabilen) pad sayisi. */
    fun openPadCount(levelId: Int): Int {
        val mapId = mapIdForLevel(levelId)
        val disabled = disabledPadIdsFor(levelId).toSet()
        return LevelData.forMapId(mapId).buildSpots.count { it.id !in disabled }
    }

    /**
     * Kapsama KIT mi? Iki sebep de ayni sonucu verir (bkz.
     * [COVERAGE_SCARCITY_SUPPLY_FACTOR]): iki kollu harita ya da dar tahta.
     */
    fun hasScarceCoverage(levelId: Int): Boolean {
        val forked = usesAlternateRoutes(levelId) &&
            LevelData.routesForMapId(mapIdForLevel(levelId)).size > 1
        if (forked) return true
        val roster = DESIGNED_ROSTER_SIZE.getOrElse(levelId - 1) { 5 }
        return openPadCount(levelId) <= roster + TIGHT_BOARD_SPARE_PADS
    }

    /**
     * Bu bolumde GIZLENEN pad id'leri. [CAMPAIGN] insa edilirken de kullanilan
     * tek kaynak; ayri bir fonksiyon olmasinin sebebi [startingSupplyFor] —
     * sermaye kurali tahtanin darligini bilmek zorunda ama `levelSpec()` ile
     * soramaz (CAMPAIGN daha kuruluyor).
     */
    private fun disabledPadIdsFor(levelId: Int): List<Int> = when {
        levelId <= 11 -> OUT_OF_RANGE_PADS[levelId] ?: emptyList()
        levelId <= 22 -> FROZEN_DISABLED_PADS[levelId] ?: emptyList()
        else -> LATE_ACT_DISABLED_PADS[levelId] ?: emptyList()
    }

    /**
     * Bolumun harita id'si — [CAMPAIGN] insa edilirken de KULLANILAN tek kaynak.
     *
     * Ayri bir fonksiyon olmasinin sebebi [startingSupplyFor]: sermaye kurali
     * haritanin CATALLANIP catallanmadigini bilmek zorunda, ama o soruyu
     * `levelSpec()` ile sormak imkansiz — `CAMPAIGN` daha kurulurken cagriliyor.
     */
    private fun mapIdForLevel(levelId: Int): Int = when {
        levelId <= 11 -> levelId
        levelId <= 22 -> 11 - (levelId - 12)
        else -> {
            val act = (levelId - 1) / 11 + 1
            LATE_ACT_MAP_ORDER.getValue(act)[(levelId - 1) % 11]
        }
    }

    // ========================================================================
    // CAMPAIGN_55.md — PERDE III/IV/V (bolum 23..55)
    // ========================================================================

    /**
     * PERDE ICI HARITA SIRASI (CAMPAIGN_55.md K4 / 3.2).
     *
     * Act II'nin ters sirasi (11..1), her perdede **bir slot kaydirilarak**:
     *   Act III (rot 1): 10 9 8 7 6 5 4 3 2 1 11
     *   Act IV  (rot 2):  9 8 7 6 5 4 3 2 1 11 10
     *   Act V   (rot 3):  8 7 6 5 4 3 2 1 11 10 9
     *
     * Sonuc: L23'ten sonra olculen MINIMUM harita tekrar araligi tam **10 bolum**
     * (teorik maksimum 11) ve perde finalleri DORT FARKLI haritaya duser
     * (L22 harita 01 · L33 harita 11 · L44 harita 10 · L55 harita 09).
     *
     * HARITA ATAMASININ **TEK SAHIBI** BU TABLODUR
     * (`GameEngine.loadLevel` -> `LevelSpec.mapId`, cizimde
     * `GameCanvas` -> `gameEngine.activeMapId`).
     *
     * `BiomeVariants` bir zamanlar `baseMapIdFor` ile PARALEL bir harita
     * tablosu tutuyordu (Act III+ icin kaydirmasiz 11..1) ve iki tablo ayni
     * soruya farkli cevap veriyordu. Celiski cizime yansimiyordu ama ileride
     * birinin yanlisini kullanacak bir tuzakti; `baseMapIdFor` KALDIRILDI ve
     * `BiomeVariants` artik yalnizca `levelId -> Biome` uretir. Kaydirmali
     * siranin bedeli (minimum tekrar araligi 11 yerine 10) ve karsiligi
     * (perde finalleri dort AYRI haritada) `BiomeCampaignTableTest`
     * icinde olculerek kilitlidir.
     */
    private val LATE_ACT_MAP_ORDER: Map<Int, List<Int>> = mapOf(
        3 to listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 11),
        4 to listOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 11, 10),
        5 to listOf(8, 7, 6, 5, 4, 3, 2, 1, 11, 10, 9)
    )

    /**
     * PERDE BASINA BASLANGIC TEDARIKI (CAMPAIGN_55.md 8.2).
     *
     * Bu bir zorluk kolu DEGIL, bolum bolme kararinin zorunlu sonucudur: 6-7
     * dalgalik bir bolumde oldurme geliri kadroyu kurmaya yetismez, ya bolum
     * uzar (K-2'ye aykiri) ya sermaye artar. Meta yukseltme bunun USTUNE biner
     * (`levelSpec.startingSupply + (meta - 150)`), maks rank oyuncu Act V'e 470
     * ile girer ve SPI 2,00 -> 2,29 olur: hâlâ bandin (1,5-2,6) icinde.
     */
    // KALDIRILDI: `LATE_ACT_STARTING_SUPPLY = {3: 220, 4: 270, 5: 320}`.
    // Perde basina duz bir sayi, bolum 5-7 dalgaya inince yetmedi: 270 Tedarik
    // IKI kule alirken acilis dalgasi 40 govde gonderiyordu ve 20 canin tamami
    // ILK dalgada bitiyordu (olcum: CampaignSolvabilityAllLevelsTest dalga izi).
    // Sermaye artik perdeden degil KADRODAN turer — bkz. [startingSupplyFor].

    /**
     * PERDE III-V KRATER / OLU PAD LISTESI — bolum -> gizlenen pad id'leri.
     *
     * Iki kural birden:
     *  · **K-6 (zorunlu):** listede o haritanin OLU pad'lerinin hepsi bulunmak
     *    ZORUNDA. Olu pad = tum kuleler acikken (esik 270, ikinci kol devrede)
     *    aktif rotaya erisemeyen pad; uzerine kurulan kule hicbir seye ates
     *    edemez, yani secenek degil TUZAK. Olculen olu kume
     *    (docs/PAD_COVERAGE_REPORT.md 2): harita 03 {10} · 05 {3} · 06 {4,10} ·
     *    07 {3,6} · 08 {6,8,11} · 09 {5} · 10 {8,17}; harita 01/02/04/11'de olu
     *    pad YOK. Toplam 134 pad'in 12'si kalici olu.
     *  · **Krater (tasarim):** kalan gizlemeler CALISAN pad'leri bilerek kapatir
     *    ve tahtayi her geciste farkli bir bulmacaya cevirir (CAMPAIGN_55.md 6.1
     *    eksen 3). Kalan acik pad sayisi 9. bolumdeki `acik` kolonuyla BIREBIR.
     *
     * Liste DONDURULMUSTUR (LEVEL_DESIGN F.4): calisma aninda algoritma kosmaz,
     * cunku geometri degisirse sessizce farkli bir bulmaca uretir. Dogrulama
     * uc testte: `PadReachabilityPerLevelTest` (gorunur her pad rotaya erisiyor
     * mu), `CampaignTableTest` (acik pad sayisi = 9. bolum tablosu, oran <= %42),
     * `LateActPadCoverageTest` (900 ref-px'den uzun kapsamasiz yol parcasi yok).
     *
     * DIKKAT: `CAMPAIGN` bunu initializer'inda okuyor — CAMPAIGN'DEN ONCE
     * durmak ZORUNDA.
     */
    private val LATE_ACT_DISABLED_PADS: Map<Int, List<Int>> = mapOf(
        // ---- Act III (kis) ----
        23 to listOf(4, 8, 11, 14, 17),   // harita 10 · 17 -> 12 (%29) · olu 8,17
        24 to listOf(2, 5, 9),            // harita 09 · 12 ->  9 (%25) · olu 5
        25 to listOf(6, 8, 11),           // harita 08 · 11 ->  8 (%27) · olu 6,8,11
        26 to listOf(3, 6, 10),           // harita 07 · 11 ->  8 (%27) · olu 3,6
        27 to listOf(2, 4, 10),           // harita 06 · 10 ->  7 (%30) · olu 4,10
        28 to listOf(3, 6, 9),            // harita 05 · 12 ->  9 (%25) · olu 3
        29 to listOf(2, 7, 10, 14),       // harita 04 · 16 -> 12 (%25) · olu yok
        30 to listOf(4, 7, 10),           // harita 03 · 12 ->  9 (%25) · olu 10
        31 to listOf(2, 6, 9, 12),        // harita 02 · 13 ->  9 (%31) · olu yok
        32 to listOf(4, 8),               // harita 01 · 10 ->  8 (%20) · olu yok
        // Harita 11'de OLU pad yok, yani buradaki her gizleme saf KRATER
        // karari. L33 iki gizlemeyle (8 acik pad) simulasyonda gecilemiyordu:
        // perde finali + boss + IKI KOLLU harita ucu bir arada, ve 8 pad'in
        // kapsamasi iki kola bolununce yetmiyor. Ayni haritanin catallanan
        // diger geciseleri L43 (8 pad) ve L53 (9 pad) boss'suz oldugu icin
        // geciyor. Kraterlerden biri acildi: 9 acik pad (%10), K-5 (acik >= R+2
        // = 8) hâlâ saglaniyor.
        // v4 TELAFISI (2026-08-18): gizlenen pad 7 -> 9. Rota merkezlenince
        // (LevelGeometry v4) harita 11'in yay konumlari birkac ref-px kaydi ve
        // L33 TEK gecerli oynanis bicimine dustu — CampaignSolvabilityAllLevelsTest
        // buna "cozulebilir degil ezberlenebilir" der. Olcum: pad 7 bu haritada
        // benzersiz sekilde guclu (kapatildiginda 1/7 davranis geciyor, acildiginda
        // 6/7). Gizlenen pad 9 yapilinca 5/7 gecer — komsu bolumlerle ayni bant
        // (L31 5/7, L32 6/7, L34 4/7) ve MONO_BEST hala BASARISIZ, yani "karisik
        // kadro kur" dersi korunuyor. Gizli pad SAYISI degismedi (1).
        33 to listOf(9),                  // harita 11 · 10 ->  9 (%10) · olu yok
        // ---- Act IV (col) ----
        34 to listOf(5, 8),               // harita 09 · 12 -> 10 (%17) · olu 5
        35 to listOf(6, 8, 11),           // harita 08 · 11 ->  8 (%27) · olu 6,8,11 (zorunlu kume)
        36 to listOf(3, 6),               // harita 07 · 11 ->  9 (%18) · olu 3,6 (zorunlu kume)
        37 to listOf(4, 10),              // harita 06 · 10 ->  8 (%20) · olu 4,10 (zorunlu kume)
        38 to listOf(3, 11),              // harita 05 · 12 -> 10 (%17) · olu 3
        39 to listOf(5, 8, 13),           // harita 04 · 16 -> 13 (%19) · olu yok
        40 to listOf(2, 10),              // harita 03 · 12 -> 10 (%17) · olu 10
        41 to listOf(3, 8, 11),           // harita 02 · 13 -> 10 (%23) · olu yok
        42 to listOf(2, 6),               // harita 01 · 10 ->  8 (%20) · olu yok
        43 to listOf(2, 9),               // harita 11 · 10 ->  8 (%20) · olu yok
        44 to listOf(2, 8, 13, 17),       // harita 10 · 17 -> 13 (%24) · olu 8,17
        // ---- Act V (sonbahar) ----
        45 to listOf(6, 8, 11),           // harita 08 · 11 ->  8 (%27) · olu 6,8,11 (zorunlu kume)
        46 to listOf(3, 6),               // harita 07 · 11 ->  9 (%18) · olu 3,6 (zorunlu kume)
        47 to listOf(4, 10),              // harita 06 · 10 ->  8 (%20) · olu 4,10 (zorunlu kume)
        48 to listOf(3),                  // harita 05 · 12 -> 11 ( %8) · olu 3 (zorunlu kume)
        49 to listOf(4, 11),              // harita 04 · 16 -> 14 (%13) · olu yok
        50 to listOf(10),                 // harita 03 · 12 -> 11 ( %8) · olu 10 (zorunlu kume)
        51 to listOf(5, 10),              // harita 02 · 13 -> 11 (%15) · olu yok
        52 to listOf(7),                  // harita 01 · 10 ->  9 (%10) · olu yok
        53 to listOf(6),                  // harita 11 · 10 ->  9 (%10) · olu yok
        54 to listOf(8, 17),              // harita 10 · 17 -> 15 (%12) · olu 8,17 (zorunlu kume)
        55 to listOf(5)                   // harita 09 · 12 -> 11 ( %8) · olu 5 (zorunlu kume)
    )

    /**
     * KONUSLANMA BEDELI D(L), L >= 23 — **`350 + 15 x (L - 22)`**.
     *
     * ESKI uzanti `350 + 40(L-22)` idi ve 55 bolumde KIRILIYORDU: GDD C.3 kurali
     * "bir bolumun odulu, bir sonrakinin kilidinin en az 2,5 kati" iken
     * L55 kilidi 1.670, L54 odulu 1.730 -> oran **1,04**. Yeni formulde
     * L33 = 515 · L44 = 680 · L55 = 845, oran L54 -> L55 = 1730/845 = **2,05**.
     *
     * En kotu durum (hepsi 1 yildiz · gorev yok · reklam yok · tekrar yok):
     * toplam kilit 23.105, toplam 1* odul 52.250, kampanya sonu bakiye 29.145,
     * minimum guvenlik payi 12,9x, negatif bakiye YOK.
     * `EconomySimulationTest.aOneStarRunNeverGoesBrokeAcrossFiftyFiveLevels` kanit.
     */
    private fun lateActDeploymentCost(levelId: Int): Int = 350 + 15 * (levelId - 22)

    /** Bolum no -> konfigurasyon. Sira LEVEL_DESIGN.md B tablosu ile birebir. */
    val CAMPAIGN: List<LevelSpec> = buildList {
        // ---- ACT I: harita 01 -> 11, MENZIL DISI pad gizli, gunduz ----------
        // Act I'de krater kisiti YOK; kapatilan tek sey uzerine kurulan kulenin
        // hicbir seye ates edemeyecegi pad'lerdir (bkz. OUT_OF_RANGE_PADS).
        val actIDeploymentCost = listOf(0, 0, 0, 0, 0, 0, 100, 110, 120, 130, 140)
        for (lv in 1..11) {
            add(
                LevelSpec(
                    levelId = lv,
                    mapId = mapIdForLevel(lv),
                    act = 1,
                    deploymentCost = actIDeploymentCost[lv - 1],
                    startingSupply = startingSupplyFor(lv),
                    disabledPadIds = OUT_OF_RANGE_PADS[lv] ?: emptyList(),
                    modifiers = modifiersFor(lv)
                )
            )
        }
        // ---- ACT II: harita 11 -> 01 TERS SIRA, gece overlay, pad kisiti ----
        // "Ters sira" = harita SIRASI (DECISIONS). Spawn/us/yol yonu Act I ile
        // birebir ayni; gece overlay tamamen kozmetik.
        val actIIDeploymentCost = listOf(150, 165, 180, 195, 210, 225, 240, 255, 270, 300, 350)
        for (i in 0..10) {
            val lv = 12 + i
            add(
                LevelSpec(
                    levelId = lv,
                    mapId = mapIdForLevel(lv),
                    act = 2,
                    deploymentCost = actIIDeploymentCost[i],
                    startingSupply = startingSupplyFor(lv),
                    disabledPadIds = FROZEN_DISABLED_PADS[lv] ?: emptyList(),
                    overlay = MapOverlay.NIGHT,
                    biome = Biome.NIGHT,
                    modifiers = modifiersFor(lv)
                )
            )
        }
        // ---- ACT III/IV/V: 5 perde x 11 bolum = 55 (CAMPAIGN_55.md K1) -------
        // Her perde TEK biyom, her haritayi TAM BIR KEZ. Harita sirasi
        // [LATE_ACT_MAP_ORDER], sermaye [startingSupplyFor], pad kisiti
        // [LATE_ACT_DISABLED_PADS], kilit [lateActDeploymentCost].
        for (act in 3..5) {
            val order = LATE_ACT_MAP_ORDER.getValue(act)
            val biome = when (act) {
                3 -> Biome.WINTER
                4 -> Biome.DESERT
                else -> Biome.AUTUMN
            }
            // Perde V'e autumn recolor'in USTUNE alacakaranlik perdesi biner:
            // desert -> autumn olculen en zayif gorsel gecis (CAMPAIGN_55.md 3.4).
            val overlay = if (act == 5) MapOverlay.DUSK else MapOverlay.NONE
            for (i in 0..10) {
                val lv = 11 * (act - 1) + 1 + i
                add(
                    LevelSpec(
                        levelId = lv,
                        mapId = order[i],
                        act = act,
                        deploymentCost = lateActDeploymentCost(lv),
                        startingSupply = startingSupplyFor(lv),
                        disabledPadIds = LATE_ACT_DISABLED_PADS[lv] ?: emptyList(),
                        overlay = overlay,
                        biome = biome,
                        modifiers = modifiersFor(lv)
                    )
                )
            }
        }
    }

    /**
     * 5 perde x 11 bolum (CAMPAIGN_55.md K1). 22 -> 55.
     *
     * `EconomyConfig.CAMPAIGN_LEVELS` ve `WaveDefinitions.CAMPAIGN_LEVEL_COUNT`
     * ile ayni olmasi `EconomyGameConfigContractTest` / `WaveDefinitionsDataTest`
     * icinde kilitli.
     */
    const val CAMPAIGN_LEVEL_COUNT = 55

    fun levelSpec(levelId: Int): LevelSpec =
        CAMPAIGN.firstOrNull { it.levelId == levelId } ?: CAMPAIGN.first()

    /**
     * ESKI 6 dalgalik tek-bolum listesi.
     *
     * Faz 4'ten itibaren dalgalarin kaynagi `WaveDefinitions.CAMPAIGN`'dir
     * (bugun **55 bolum x 359 dalga**; bu yorum 22x259 diyordu, bayatlamisti).
     *
     * Bu liste eskiden SILINEMIYORDU cunku `HUDOverlay` `GameConfig.WAVES.size`
     * okuyordu ve kampanyada dalga sayisi bolume gore degistigi icin "DALGA
     * 9/6" gibi imkansiz degerler cikiyordu. **O goc TAMAMLANDI**: HUD artik
     * `gameEngine.totalWaves` akisini okuyor. Liste yalnizca geriye donuk
     * uyumluluk icin duruyor ve `@Deprecated`.
     */
    @Deprecated(
        message = "Bolum basina dalga icin WaveDefinitions.wavesFor(levelId) / " +
            "GameEngine.totalWaves kullan.",
        level = DeprecationLevel.WARNING
    )
    val WAVES: List<WaveData> = listOf(
        // Wave 1: Basic infantry introduction
        WaveData(
            waveIndex = 1,
            title = "Infiltration",
            spawns = List(8) { WaveEnemySpawn(EnemyType.INFANTRY, 1.2f) }
        ),
        // Wave 2: More infantry
        WaveData(
            waveIndex = 2,
            title = "Infantry Rush",
            spawns = List(14) { WaveEnemySpawn(EnemyType.INFANTRY, 0.9f) }
        ),
        // Wave 3: Fast soldiers introduced
        WaveData(
            waveIndex = 3,
            title = "Fast Recon",
            spawns = List(10) { WaveEnemySpawn(EnemyType.FAST_SOLDIER, 0.8f) }
        ),
        // Wave 4: Mixed infantry and fast soldiers
        WaveData(
            waveIndex = 4,
            title = "Combined Assault",
            spawns = buildList {
                repeat(6) {
                    add(WaveEnemySpawn(EnemyType.INFANTRY, 0.8f))
                    add(WaveEnemySpawn(EnemyType.FAST_SOLDIER, 0.6f))
                }
            }
        ),
        // Wave 5: Armored vehicles introduced
        WaveData(
            waveIndex = 5,
            title = "Armored Column",
            spawns = buildList {
                add(WaveEnemySpawn(EnemyType.ARMORED_VEHICLE, 1.5f))
                add(WaveEnemySpawn(EnemyType.INFANTRY, 0.8f))
                add(WaveEnemySpawn(EnemyType.ARMORED_VEHICLE, 1.5f))
                add(WaveEnemySpawn(EnemyType.FAST_SOLDIER, 0.7f))
                add(WaveEnemySpawn(EnemyType.ARMORED_VEHICLE, 1.5f))
                add(WaveEnemySpawn(EnemyType.ARMORED_VEHICLE, 1.5f))
            }
        ),
        // Wave 6: Large mixed wave ending with heavy tanks
        WaveData(
            waveIndex = 6,
            title = "Final Vanguard",
            spawns = buildList {
                repeat(6) { add(WaveEnemySpawn(EnemyType.FAST_SOLDIER, 0.5f)) }
                repeat(4) { add(WaveEnemySpawn(EnemyType.ARMORED_VEHICLE, 1.2f)) }
                add(WaveEnemySpawn(EnemyType.TANK, 2.0f))
                repeat(4) { add(WaveEnemySpawn(EnemyType.INFANTRY, 0.7f)) }
                add(WaveEnemySpawn(EnemyType.TANK, 2.5f))
                add(WaveEnemySpawn(EnemyType.TANK, 3.0f))
            }
        )
    )
}
