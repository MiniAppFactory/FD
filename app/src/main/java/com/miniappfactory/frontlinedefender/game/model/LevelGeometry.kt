package com.miniappfactory.frontlinedefender.game.model

/**
 * ROTALAR OTOMATIK URETILDI - docs/level_geometry/build_routes.js  (v5)
 * Build pad'ler ve metinler ELLE bakimlidir; uretici onlara DOKUNMAZ.
 *
 * KAYNAK: `app/src/main/res/drawable-nodpi/bg_level_XX.webp` — yani
 * uygulamanin GERCEKTEN yukledigi resmin kendisi (1920x1081). v1-v4
 * geometrisi asset-pack'teki 1672x941 PNG kopyalarindan turetilmisti; iki
 * goruntunun cercevesi ayni cikti (olculdu: en iyi kaydirma dx=0 dy=0) ama
 * kaynagin uygulamayla ayni dosya olmasi artik bir sozlesmedir.
 *
 * NEDEN v5 (cihaz geri bildirimi): *"hala yol olmayan yerlerden geciyorlar,
 * bu level 3 ornegin."* Harita 3'un rotasi sag ucta boyali yolu birakip
 * kayaliktan ussun KUZEYINE kesiyordu; rampa ucu 318 ref-px yanlis yerdeydi.
 * Testler yesildi cunku eski maske kayayi "diger" kovasina koyuyor ve
 * testler yalnizca cimi yasakliyordu (bkz. MapMaskFixture v2).
 *
 * v5 rotalari yol koridorunun UZAKLIK DONUSUMU uzerinde A* ile kurulur:
 * koridorun ortasi ucuz, kenari karesel pahali. Piyade sprite'i 46 ref-px
 * genis oldugu icin kenardan gecen bir rota "yol maskesi icinde" olsa bile
 * govdenin yarisini disari tasirir.
 *
 * 11 benzersiz harita icin OLCULMUS oynanis geometrisi.
 * Koordinatlar normalize (0.0-1.0); referans tuval 1920x1080 (DECISIONS §B3).
 * Yapilar source/.../game/model/LevelData.kt icindeki PointF / BuildSpot /
 * LevelData ile birebir aynidir - o dosya DEGISTIRILMEDI.
 *
 * Rota sayisi: bazi haritalarda yol CATALLANIR. `waypoints` birincil rota
 * (en kisa + en merkezi) olup LevelData sozlesmesini bozmaz; ikinci kol
 * `ALT_ROUTES[levelId]` icindedir. Her iki kol da ayni spawn ve ayni us
 * noktasinda baslar/biter.
 *
 * PAD MESAFELERI: "A-kolu" = `waypoints` (bolum 1-8'de TEK aktif rota),
 * "B-kolu" = `ALT_ROUTES[mapId]` (yalnizca bolum >= ALT_ROUTE_FIRST_LEVEL).
 * Harita 3'te kanonik rota `ALT_ROUTES[3]`'tur; oradaki yorumlar ona gore.
 * Bir pad'in KULLANILABILIR olmasi icin O BOLUMDE AKTIF olan kola menzil
 * icinde olmasi gerekir — iki kolun minimumuna bakmak yaniltir. Yorumlar
 * daha once tam bu hatayi yapiyordu (harita 1 pad 3 icin "121" yaziyordu,
 * bolum 1'de gercek deger 444); olcum: docs/PAD_COVERAGE_REPORT.md.
 *
 * EKRAN DISI UC YOK. Her rotanin ilk noktasi cikis bunkerinin YOL AGZI, son
 * noktasi hedef ussun RAMPASIDIR - ikisi de olculmus, ikisi de 0.0..1.0
 * icinde. (v1'de uclar x=-0.05 / x=1.05'e yatay uzatiliyordu; dusman ekran
 * disinda doguyor, bunkerin USTUNDEN geciyor ve usse girmeden ekrandan disari
 * yuruyordu. `extract_geometry.py::extend_ends` devre disi birakildi.)
 *
 * v5'te uclar SANATTAN turetilir: yol koridorunun sol ucu = bunker yol agzi,
 * sag ucu = us rampasi. Mevcut uc bu noktaya 45 ref-px'ten yakinsa (yol yarim
 * genisligi 37-49) DOKUNULMAZ — harita 1'in gozle dogrulanmis uclari boylece
 * birebir korundu. Yalnizca 3, 4, 6, 9 ve 10'un uclari tasindi; en buyugu
 * harita 3'un rampasi (318 ref-px).
 */
object LevelGeometry {

    // ---- m00  kaynak: asset-pack/maps/level_01_battlefield_map.png
    //      rota=2  pad=10  yol=1823 ref-px
    val MAP_01 = LevelData(
        levelId = 1,
        name = "Cayir Gecidi / Meadow Pass",
        description = "Yesil cayirda ciftlenmis toprak yol; yol spawn'da catallanip usse birlesir.",
        waypoints = listOf(
            PointF(0.1388f, 0.4638f),  // bunker yol agzi
            PointF(0.1433f, 0.4834f),
            PointF(0.1587f, 0.4872f),
            PointF(0.1741f, 0.4892f),
            PointF(0.1885f, 0.4989f),
            PointF(0.2018f, 0.5131f),
            PointF(0.2147f, 0.5283f),
            PointF(0.2256f, 0.5477f),
            PointF(0.2347f, 0.5699f),
            PointF(0.2426f, 0.5937f),
            PointF(0.2508f, 0.6170f),
            PointF(0.2602f, 0.6389f),
            PointF(0.2709f, 0.6587f),
            PointF(0.2832f, 0.6754f),
            PointF(0.2969f, 0.6882f),
            PointF(0.3116f, 0.6965f),
            PointF(0.3270f, 0.6986f),
            PointF(0.3425f, 0.6985f),
            PointF(0.3578f, 0.6946f),
            PointF(0.3725f, 0.6861f),
            PointF(0.3864f, 0.6738f),
            PointF(0.3994f, 0.6589f),
            PointF(0.4118f, 0.6423f),
            PointF(0.4236f, 0.6247f),
            PointF(0.4352f, 0.6064f),
            PointF(0.4463f, 0.5871f),
            PointF(0.4573f, 0.5678f),
            PointF(0.4688f, 0.5494f),
            PointF(0.4803f, 0.5310f),
            PointF(0.4910f, 0.5111f),
            PointF(0.5045f, 0.4987f),
            PointF(0.5196f, 0.4925f),
            PointF(0.5349f, 0.4885f),
            PointF(0.5501f, 0.4930f),
            PointF(0.5637f, 0.5058f),
            PointF(0.5749f, 0.5247f),
            PointF(0.5838f, 0.5472f),
            PointF(0.5899f, 0.5725f),
            PointF(0.5937f, 0.5992f),
            PointF(0.5969f, 0.6261f),
            PointF(0.6024f, 0.6518f),
            PointF(0.6105f, 0.6752f),
            PointF(0.6215f, 0.6945f),
            PointF(0.6346f, 0.7090f),
            PointF(0.6495f, 0.7161f),
            PointF(0.6650f, 0.7146f),
            PointF(0.6801f, 0.7088f),
            PointF(0.6951f, 0.7022f),
            PointF(0.7101f, 0.6956f),
            PointF(0.7252f, 0.6888f),
            PointF(0.7405f, 0.6850f),
            PointF(0.7559f, 0.6871f),
            PointF(0.7714f, 0.6883f),
            PointF(0.7866f, 0.6925f),
            PointF(0.8018f, 0.6979f),
            PointF(0.8170f, 0.7035f),
            PointF(0.8324f, 0.7068f),
            PointF(0.8478f, 0.7051f),
            PointF(0.8625f, 0.6966f),
            PointF(0.8751f, 0.6809f),
            PointF(0.8804f, 0.6638f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2772f, normY = 0.3777f),  // Z1  A-kolu 195 / B-kolu 124 ref-px
            BuildSpot(id = 2, normX = 0.3146f, normY = 0.5760f),  // Z1  A-kolu 121 / B-kolu 298 ref-px
            BuildSpot(id = 3, normX = 0.3322f, normY = 0.1674f),  // Z2  A-kolu 444 / B-kolu 116 ref-px
            BuildSpot(id = 4, normX = 0.4312f, normY = 0.7528f),  // Z2  A-kolu 131 / B-kolu 533 ref-px
            BuildSpot(id = 5, normX = 0.4653f, normY = 0.3024f),  // Z3  A-kolu 213 / B-kolu 132 ref-px
            BuildSpot(id = 6, normX = 0.5243f, normY = 0.6010f),  // Z4  A-kolu 122 / B-kolu 364 ref-px
            BuildSpot(id = 7, normX = 0.5787f, normY = 0.1679f),  // Z3  A-kolu 346 / B-kolu 115 ref-px
            BuildSpot(id = 8, normX = 0.6654f, normY = 0.5809f),  // Z5  A-kolu 135 / B-kolu 153 ref-px
            BuildSpot(id = 9, normX = 0.6932f, normY = 0.3508f),  // Z3  A-kolu 296 / B-kolu 116 ref-px
            BuildSpot(id = 10, normX = 0.7641f, normY = 0.8011f),  // Z5  A-kolu 132 / B-kolu 133 ref-px
        )
    )

    // ---- m01  kaynak: copied items/map (1).png
    //      rota=2  pad=13  yol=1697 ref-px
    val MAP_02 = LevelData(
        levelId = 2,
        name = "Selale Ormani / Waterfall Woods",
        description = "Orman icinde selale ve gol; yol iki kola ayrilip usse birlesir.",
        waypoints = listOf(
            PointF(0.1435f, 0.5021f),  // bunker yol agzi
            PointF(0.1571f, 0.5034f),
            PointF(0.1719f, 0.5121f),
            PointF(0.1850f, 0.5271f),
            PointF(0.1968f, 0.5451f),
            PointF(0.2065f, 0.5667f),
            PointF(0.2137f, 0.5912f),
            PointF(0.2190f, 0.6173f),
            PointF(0.2242f, 0.6434f),
            PointF(0.2316f, 0.6678f),
            PointF(0.2416f, 0.6889f),
            PointF(0.2537f, 0.7064f),
            PointF(0.2676f, 0.7188f),
            PointF(0.2826f, 0.7259f),
            PointF(0.2981f, 0.7291f),
            PointF(0.3136f, 0.7308f),
            PointF(0.3292f, 0.7319f),
            PointF(0.3448f, 0.7323f),
            PointF(0.3602f, 0.7361f),
            PointF(0.3754f, 0.7425f),
            PointF(0.3905f, 0.7494f),
            PointF(0.4059f, 0.7537f),
            PointF(0.4214f, 0.7538f),
            PointF(0.4368f, 0.7491f),
            PointF(0.4513f, 0.7393f),
            PointF(0.4654f, 0.7273f),
            PointF(0.4793f, 0.7147f),
            PointF(0.4933f, 0.7028f),
            PointF(0.5078f, 0.6924f),
            PointF(0.5225f, 0.6835f),
            PointF(0.5378f, 0.6810f),
            PointF(0.5531f, 0.6867f),
            PointF(0.5678f, 0.6959f),
            PointF(0.5812f, 0.7099f),
            PointF(0.5939f, 0.7259f),
            PointF(0.6066f, 0.7421f),
            PointF(0.6200f, 0.7563f),
            PointF(0.6344f, 0.7664f),
            PointF(0.6499f, 0.7694f),
            PointF(0.6655f, 0.7682f),
            PointF(0.6810f, 0.7663f),
            PointF(0.6962f, 0.7600f),
            PointF(0.7107f, 0.7501f),
            PointF(0.7247f, 0.7384f),
            PointF(0.7402f, 0.7372f),
            PointF(0.7557f, 0.7344f),
            PointF(0.7712f, 0.7313f),
            PointF(0.7867f, 0.7277f),
            PointF(0.8017f, 0.7207f),
            PointF(0.8157f, 0.7086f),
            PointF(0.8285f, 0.6927f),
            PointF(0.8401f, 0.6742f),
            PointF(0.8507f, 0.6540f),
            PointF(0.8598f, 0.6315f),
            PointF(0.8698f, 0.6104f),
            PointF(0.8828f, 0.5957f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2104f, normY = 0.8193f),  // Z1  A-kolu 143 / B-kolu 413 ref-px
            BuildSpot(id = 2, normX = 0.2726f, normY = 0.5081f),  // Z1  A-kolu 141 / B-kolu 141 ref-px
            BuildSpot(id = 3, normX = 0.2874f, normY = 0.2212f),  // Z1  A-kolu 372 / B-kolu 155 ref-px
            BuildSpot(id = 4, normX = 0.3599f, normY = 0.2952f),  // Z2  A-kolu 409 / B-kolu 119 ref-px
            BuildSpot(id = 5, normX = 0.3953f, normY = 0.6314f),  // Z2  A-kolu 120 / B-kolu 225 ref-px
            BuildSpot(id = 6, normX = 0.5226f, normY = 0.7688f),  // Z3  A-kolu  99 / B-kolu 408 ref-px
            BuildSpot(id = 7, normX = 0.5569f, normY = 0.1302f),  // Z3  A-kolu 583 / B-kolu 113 ref-px
            BuildSpot(id = 8, normX = 0.6225f, normY = 0.3458f),  // Z4  A-kolu 384 / B-kolu 102 ref-px
            BuildSpot(id = 9, normX = 0.6559f, normY = 0.6318f),  // Z4  A-kolu 141 / B-kolu 116 ref-px
            BuildSpot(id = 10, normX = 0.7237f, normY = 0.1687f),  // Z4  A-kolu 554 / B-kolu 159 ref-px
            BuildSpot(id = 11, normX = 0.7456f, normY = 0.8241f),  // Z5  A-kolu 107 / B-kolu 146 ref-px
            BuildSpot(id = 12, normX = 0.7470f, normY = 0.4622f),  // Z4  A-kolu 272 / B-kolu 107 ref-px
            BuildSpot(id = 13, normX = 0.8716f, normY = 0.7690f),  // Z5  A-kolu 124 / B-kolu 131 ref-px
        )
    )

    // ---- m02  kaynak: copied items/map (2).png
    //      rota=2  pad=12  yol=1901 ref-px
    val MAP_03 = LevelData(
        levelId = 3,
        name = "Karanlik Bogaz / Dark Ravine",
        description = "Kayalik karanlik bogaz; genis toprak zemin, komsu seritler sanatta birlesik.",
        waypoints = listOf(
            PointF(0.1292f, 0.5064f),  // bunker yol agzi
            PointF(0.1447f, 0.5043f),
            PointF(0.1599f, 0.5015f),
            PointF(0.1728f, 0.4863f),
            PointF(0.1855f, 0.4704f),
            PointF(0.1974f, 0.4527f),
            PointF(0.2111f, 0.4404f),
            PointF(0.2245f, 0.4267f),
            PointF(0.2353f, 0.4070f),
            PointF(0.2441f, 0.3843f),
            PointF(0.2515f, 0.3601f),
            PointF(0.2594f, 0.3364f),
            PointF(0.2679f, 0.3134f),
            PointF(0.2765f, 0.2904f),
            PointF(0.2882f, 0.2727f),
            PointF(0.3031f, 0.2654f),
            PointF(0.3184f, 0.2610f),
            PointF(0.3330f, 0.2682f),
            PointF(0.3455f, 0.2843f),
            PointF(0.3566f, 0.3035f),
            PointF(0.3652f, 0.3263f),
            PointF(0.3730f, 0.3501f),
            PointF(0.3794f, 0.3752f),
            PointF(0.3884f, 0.3974f),
            PointF(0.4035f, 0.4036f),
            PointF(0.4186f, 0.4079f),
            PointF(0.4301f, 0.3903f),
            PointF(0.4379f, 0.3666f),
            PointF(0.4473f, 0.3447f),
            PointF(0.4568f, 0.3229f),
            PointF(0.4674f, 0.3031f),
            PointF(0.4820f, 0.2944f),
            PointF(0.4968f, 0.2857f),
            PointF(0.5119f, 0.2802f),
            PointF(0.5271f, 0.2745f),
            PointF(0.5419f, 0.2662f),
            PointF(0.5559f, 0.2545f),
            PointF(0.5689f, 0.2396f),
            PointF(0.5807f, 0.2217f),
            PointF(0.5909f, 0.2011f),
            PointF(0.6054f, 0.1944f),
            PointF(0.6177f, 0.2107f),
            PointF(0.6282f, 0.2309f),
            PointF(0.6379f, 0.2523f),
            PointF(0.6457f, 0.2761f),
            PointF(0.6527f, 0.3005f),
            PointF(0.6652f, 0.3164f),
            PointF(0.6790f, 0.3290f),
            PointF(0.6939f, 0.3367f),
            PointF(0.7092f, 0.3404f),
            PointF(0.7246f, 0.3441f),
            PointF(0.7391f, 0.3533f),
            PointF(0.7509f, 0.3709f),
            PointF(0.7587f, 0.3946f),
            PointF(0.7616f, 0.4215f),
            PointF(0.7617f, 0.4491f),
            PointF(0.7617f, 0.4766f),
            PointF(0.7605f, 0.5041f),
            PointF(0.7612f, 0.5316f),
            PointF(0.7591f, 0.5589f),
            PointF(0.7614f, 0.5858f),
            PointF(0.7648f, 0.6126f),
            PointF(0.7756f, 0.6312f),
            PointF(0.7907f, 0.6374f),
            PointF(0.8043f, 0.6503f),
            PointF(0.8192f, 0.6561f),
            PointF(0.8332f, 0.6449f),
            PointF(0.8473f, 0.6337f),
            PointF(0.8624f, 0.6277f),
            PointF(0.8778f, 0.6247f),
            PointF(0.8931f, 0.6204f),
            PointF(0.9069f, 0.6084f),
            PointF(0.9211f, 0.5986f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2868f, normY = 0.4342f),  // Z1  kanonik rotaya 107 ref-px
            BuildSpot(id = 2, normX = 0.3039f, normY = 0.7635f),  // Z3  kanonik rotaya  88 ref-px
            BuildSpot(id = 3, normX = 0.3049f, normY = 0.1542f),  // Z2  kanonik rotaya 122 ref-px
            BuildSpot(id = 4, normX = 0.3574f, normY = 0.6175f),  // Z2  kanonik rotaya  71 ref-px
            BuildSpot(id = 5, normX = 0.4697f, normY = 0.5378f),  // Z3  kanonik rotaya  68 ref-px
            BuildSpot(id = 6, normX = 0.4862f, normY = 0.1721f),  // Z3  kanonik rotaya 177 ref-px
            BuildSpot(id = 7, normX = 0.4944f, normY = 0.3592f),  // Z4  kanonik rotaya  60 ref-px
            BuildSpot(id = 8, normX = 0.5509f, normY = 0.6648f),  // Z3  kanonik rotaya 112 ref-px
            BuildSpot(id = 9, normX = 0.6562f, normY = 0.4229f),  // Z4  kanonik rotaya 122 ref-px
            BuildSpot(id = 10, normX = 0.6923f, normY = 0.6347f),  // Z5  kanonik rotaya 327 ref-px
            BuildSpot(id = 11, normX = 0.6925f, normY = 0.2218f),  // Z4  kanonik rotaya 100 ref-px
            BuildSpot(id = 12, normX = 0.7994f, normY = 0.3265f),  // Z5  kanonik rotaya  31 ref-px
        )
    )

    // ---- m03  kaynak: copied items/map (3).png
    //      rota=2  pad=16  yol=1957 ref-px
    val MAP_04 = LevelData(
        levelId = 4,
        name = "Gol Kusagi / Lakeside Ring",
        description = "Golun etrafini saran halka yol; iki ahsap kopru golu ortadan keser.",
        waypoints = listOf(
            PointF(0.1445f, 0.4792f),  // bunker yol agzi
            PointF(0.1586f, 0.4858f),
            PointF(0.1740f, 0.4841f),
            PointF(0.1867f, 0.4699f),
            PointF(0.1918f, 0.4448f),
            PointF(0.2003f, 0.4225f),
            PointF(0.2115f, 0.4034f),
            PointF(0.2200f, 0.3804f),
            PointF(0.2269f, 0.3557f),
            PointF(0.2332f, 0.3306f),
            PointF(0.2397f, 0.3057f),
            PointF(0.2506f, 0.2863f),
            PointF(0.2643f, 0.2737f),
            PointF(0.2786f, 0.2632f),
            PointF(0.2921f, 0.2497f),
            PointF(0.3045f, 0.2331f),
            PointF(0.3160f, 0.2145f),
            PointF(0.3296f, 0.2014f),
            PointF(0.3443f, 0.1930f),
            PointF(0.3595f, 0.1884f),
            PointF(0.3748f, 0.1837f),
            PointF(0.3900f, 0.1804f),
            PointF(0.4050f, 0.1873f),
            PointF(0.4177f, 0.2029f),
            PointF(0.4296f, 0.2206f),
            PointF(0.4411f, 0.2391f),
            PointF(0.4533f, 0.2560f),
            PointF(0.4663f, 0.2712f),
            PointF(0.4810f, 0.2787f),
            PointF(0.4963f, 0.2745f),
            PointF(0.5108f, 0.2648f),
            PointF(0.5236f, 0.2495f),
            PointF(0.5350f, 0.2309f),
            PointF(0.5450f, 0.2098f),
            PointF(0.5563f, 0.1910f),
            PointF(0.5675f, 0.1719f),
            PointF(0.5798f, 0.1552f),
            PointF(0.5941f, 0.1449f),
            PointF(0.6096f, 0.1441f),
            PointF(0.6247f, 0.1503f),
            PointF(0.6361f, 0.1681f),
            PointF(0.6449f, 0.1908f),
            PointF(0.6520f, 0.2153f),
            PointF(0.6570f, 0.2414f),
            PointF(0.6615f, 0.2676f),
            PointF(0.6711f, 0.2891f),
            PointF(0.6824f, 0.3079f),
            PointF(0.6958f, 0.3219f),
            PointF(0.7098f, 0.3338f),
            PointF(0.7234f, 0.3469f),
            PointF(0.7366f, 0.3613f),
            PointF(0.7448f, 0.3845f),
            PointF(0.7508f, 0.4098f),
            PointF(0.7570f, 0.4351f),
            PointF(0.7655f, 0.4581f),
            PointF(0.7770f, 0.4765f),
            PointF(0.7909f, 0.4885f),
            PointF(0.8061f, 0.4936f),
            PointF(0.8211f, 0.4875f),  // us rampasi
        ),
                // ------------------------------------------------------------------
        // CATAL PAD BIRLESTIRME (2026-08-19) — 16 pad -> 14
        // ------------------------------------------------------------------
        // OLCUM once sorunu gosterdi: eski pad 5 A-kolunun 300 ref-pxini
        // kapsiyordu ve B-kolundan SIFIR; pad 6 tam tersi (0 / 310). Ayni
        // sey sagda: pad 13 (316 / 0) ve pad 11 (0 / 324). Yani catalin her
        // iki yaninda da oyuncu, iki koldan yalnizca BIRINI tutabilen iki
        // ayri mevzi goruyordu — iki kol birden ates altina alinamiyordu ve
        // acilis bolumlerinde kule butcesi buna yetmiyor.
        //
        // Her cift, iki kolu da menzilde tutan TEK pade indirildi:
        //   pad  5 + pad  6  ->  yeni pad  3   (kapsama 242 / 244 ref-px)
        //   pad 13 + pad 11  ->  yeni pad 12   (kapsama 244 / 240 ref-px)
        //
        // TAKAS ACIK: tek kol kapsamasi ~310dan ~242ye iniyor (%22 az), ama
        // toplam kapsanan yol 310ten 486ya cikiyor cunku artik iki kol da
        // tutuluyor. Catalin sordugu soru "hangi kolu savunayim" degil
        // "kesisme noktasini bul" haline geliyor.
        //
        // KONUM SECIMI kapsamayi maksimize eder, orta noktayi DEGIL: olcut
        // min(A-kapsama, B-kapsama) — yani zayif kol. Tam orta noktada sol
        // pad A-koluna 150 ref-px, yani menzilin TAM SINIRINDA kalip o kolun
        // tek bir noktasina degiyordu. Ikisi de kullanicinin isaretledigi
        // bolgenin icinde.
        //
        // Mesafeler bu turda TEK YONTEMLE yeniden olculdu (rota 2 ref-px
        // adimlarla orneklenip nokta-polyline mesafesi); eski yorumlardaki
        // birkac px fark bundan geliyor, geometri degismedi.
        //
        // En kisa pad-pad mesafesi 177 ref-px; taban plakasi capi 138
        // (FriendlyPlate.RADIUS_FRAC) -> komsu plakalar cakismiyor.
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.1712f, normY = 0.6093f),   // Z1  A 193 / B  95
            BuildSpot(id = 2, normX = 0.1812f, normY = 0.2352f),   // Z1  A 121 / B 260
            BuildSpot(id = 3, normX = 0.2255f, normY = 0.4530f),   // A  60 / B  67  <- IKI KOL (eski 5+6)
            BuildSpot(id = 4, normX = 0.2579f, normY = 0.1385f),   // Z2  A 126 / B 400
            BuildSpot(id = 5, normX = 0.2613f, normY = 0.7908f),   // Z2  A 407 / B 100
            BuildSpot(id = 6, normX = 0.3741f, normY = 0.7475f),   // Z2  A 486 / B 102
            BuildSpot(id = 7, normX = 0.4689f, normY = 0.1121f),   // Z2  A 132 / B 609
            BuildSpot(id = 8, normX = 0.4941f, normY = 0.8435f),   // Z3  A 614 / B 123
            BuildSpot(id = 9, normX = 0.5969f, normY = 0.2343f),   // Z3  A 103 / B 460
            BuildSpot(id = 10, normX = 0.6901f, normY = 0.7930f),  // Z4  A 378 / B  90
            BuildSpot(id = 11, normX = 0.7091f, normY = 0.2271f),  // Z4  A 101 / B 350
            BuildSpot(id = 12, normX = 0.7394f, normY = 0.5052f),  // A  60 / B  60  <- IKI KOL (eski 13+11)
            BuildSpot(id = 13, normX = 0.7958f, normY = 0.3124f),  // Z4  A 120 / B 214
            BuildSpot(id = 14, normX = 0.8099f, normY = 0.6102f),  // Z5  A 104 / B 104  <- IKI KOL (zaten oyleydi)
        )
    )

    // ---- m04  kaynak: copied items/map (4).png
    //      rota=1  pad=12  yol=2265 ref-px
    val MAP_05 = LevelData(
        levelId = 5,
        name = "Batakli Ova / Marshland",
        description = "Sisli bataklik; tek serpantin yol, sol altta kor sapak.",
        waypoints = listOf(
            PointF(0.1483f, 0.4553f),  // bunker yol agzi
            PointF(0.1537f, 0.4493f),
            PointF(0.1676f, 0.4569f),
            PointF(0.1830f, 0.4588f),
            PointF(0.1983f, 0.4556f),
            PointF(0.2129f, 0.4466f),
            PointF(0.2270f, 0.4352f),
            PointF(0.2409f, 0.4232f),
            PointF(0.2551f, 0.4121f),
            PointF(0.2692f, 0.4008f),
            PointF(0.2830f, 0.3885f),
            PointF(0.2964f, 0.3747f),
            PointF(0.3092f, 0.3593f),
            PointF(0.3213f, 0.3421f),
            PointF(0.3327f, 0.3234f),
            PointF(0.3434f, 0.3036f),
            PointF(0.3547f, 0.2848f),
            PointF(0.3662f, 0.2664f),
            PointF(0.3785f, 0.2497f),
            PointF(0.3923f, 0.2376f),
            PointF(0.4066f, 0.2269f),
            PointF(0.4218f, 0.2249f),
            PointF(0.4367f, 0.2324f),
            PointF(0.4493f, 0.2471f),
            PointF(0.4538f, 0.2723f),
            PointF(0.4539f, 0.2998f),
            PointF(0.4539f, 0.3273f),
            PointF(0.4538f, 0.3548f),
            PointF(0.4516f, 0.3820f),
            PointF(0.4472f, 0.4083f),
            PointF(0.4422f, 0.4344f),
            PointF(0.4344f, 0.4580f),
            PointF(0.4257f, 0.4807f),
            PointF(0.4193f, 0.5057f),
            PointF(0.4157f, 0.5325f),
            PointF(0.4148f, 0.5599f),
            PointF(0.4149f, 0.5874f),
            PointF(0.4201f, 0.6127f),
            PointF(0.4303f, 0.6334f),
            PointF(0.4420f, 0.6512f),
            PointF(0.4549f, 0.6664f),
            PointF(0.4691f, 0.6772f),
            PointF(0.4840f, 0.6843f),
            PointF(0.4992f, 0.6892f),
            PointF(0.5147f, 0.6903f),
            PointF(0.5301f, 0.6888f),
            PointF(0.5456f, 0.6868f),
            PointF(0.5607f, 0.6816f),
            PointF(0.5756f, 0.6739f),
            PointF(0.5899f, 0.6633f),
            PointF(0.6031f, 0.6491f),
            PointF(0.6149f, 0.6314f),
            PointF(0.6257f, 0.6116f),
            PointF(0.6343f, 0.5888f),
            PointF(0.6389f, 0.5627f),
            PointF(0.6398f, 0.5353f),
            PointF(0.6398f, 0.5078f),
            PointF(0.6398f, 0.4803f),
            PointF(0.6398f, 0.4528f),
            PointF(0.6389f, 0.4253f),
            PointF(0.6383f, 0.3978f),
            PointF(0.6390f, 0.3704f),
            PointF(0.6438f, 0.3443f),
            PointF(0.6518f, 0.3214f),
            PointF(0.6666f, 0.3153f),
            PointF(0.6821f, 0.3154f),
            PointF(0.6974f, 0.3188f),
            PointF(0.7118f, 0.3285f),
            PointF(0.7243f, 0.3446f),
            PointF(0.7355f, 0.3636f),
            PointF(0.7449f, 0.3854f),
            PointF(0.7537f, 0.4080f),
            PointF(0.7644f, 0.4278f),
            PointF(0.7769f, 0.4440f),
            PointF(0.7900f, 0.4586f),
            PointF(0.8029f, 0.4738f),
            PointF(0.8121f, 0.4742f),
            PointF(0.8134f, 0.4468f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2351f, normY = 0.3033f),  // Z1  yola 108 ref-px
            BuildSpot(id = 2, normX = 0.2810f, normY = 0.5378f),  // Z1  yola 153 ref-px
            BuildSpot(id = 3, normX = 0.2884f, normY = 0.6803f),  // Z3  yola 259 ref-px
            BuildSpot(id = 4, normX = 0.2885f, normY = 0.2057f),  // Z1  yola 140 ref-px
            BuildSpot(id = 5, normX = 0.3977f, normY = 0.3328f),  // Z2  yola 102 ref-px
            BuildSpot(id = 6, normX = 0.4729f, normY = 0.5808f),  // Z3  yola 101 ref-px
            BuildSpot(id = 7, normX = 0.5038f, normY = 0.2694f),  // Z2  yola 96 ref-px
            BuildSpot(id = 8, normX = 0.5774f, normY = 0.5593f),  // Z4  yola 101 ref-px
            BuildSpot(id = 9, normX = 0.6330f, normY = 0.7214f),  // Z4  yola 103 ref-px
            BuildSpot(id = 10, normX = 0.6963f, normY = 0.4028f),  // Z5  yola 88 ref-px
            BuildSpot(id = 11, normX = 0.8193f, normY = 0.6516f),  // Z5  yola 216 ref-px
            BuildSpot(id = 12, normX = 0.8237f, normY = 0.2520f),  // Z5  yola 202 ref-px
        )
    )

    // ---- m05  kaynak: copied items/map (5).png
    //      rota=1  pad=10  yol=2283 ref-px
    val MAP_06 = LevelData(
        levelId = 6,
        name = "Ucurum Gecidi / Ravine Crossing",
        description = "Derin nehir yarigi; yol tas kopruden gecer, sag altta kor sapak.",
        waypoints = listOf(
            PointF(0.1398f, 0.4347f),  // bunker yol agzi
            PointF(0.1528f, 0.4494f),
            PointF(0.1671f, 0.4588f),
            PointF(0.1814f, 0.4496f),
            PointF(0.1904f, 0.4274f),
            PointF(0.1985f, 0.4039f),
            PointF(0.2045f, 0.3785f),
            PointF(0.2144f, 0.3577f),
            PointF(0.2266f, 0.3407f),
            PointF(0.2407f, 0.3302f),
            PointF(0.2561f, 0.3318f),
            PointF(0.2698f, 0.3444f),
            PointF(0.2809f, 0.3635f),
            PointF(0.2882f, 0.3877f),
            PointF(0.2912f, 0.4147f),
            PointF(0.2925f, 0.4421f),
            PointF(0.2930f, 0.4697f),
            PointF(0.2944f, 0.4971f),
            PointF(0.2968f, 0.5243f),
            PointF(0.3011f, 0.5507f),
            PointF(0.3085f, 0.5749f),
            PointF(0.3188f, 0.5954f),
            PointF(0.3316f, 0.6108f),
            PointF(0.3465f, 0.6176f),
            PointF(0.3618f, 0.6139f),
            PointF(0.3753f, 0.6006f),
            PointF(0.3860f, 0.5808f),
            PointF(0.3937f, 0.5570f),
            PointF(0.3974f, 0.5303f),
            PointF(0.4000f, 0.5031f),
            PointF(0.4035f, 0.4763f),
            PointF(0.4091f, 0.4507f),
            PointF(0.4155f, 0.4256f),
            PointF(0.4257f, 0.4054f),
            PointF(0.4408f, 0.4014f),
            PointF(0.4563f, 0.4014f),
            PointF(0.4714f, 0.4062f),
            PointF(0.4841f, 0.4219f),
            PointF(0.4962f, 0.4391f),
            PointF(0.5079f, 0.4502f),
            PointF(0.5164f, 0.4543f),
            PointF(0.5249f, 0.4584f),
            PointF(0.5397f, 0.4653f),
            PointF(0.5545f, 0.4724f),
            PointF(0.5666f, 0.4854f),
            PointF(0.5804f, 0.4973f),
            PointF(0.5957f, 0.5023f),
            PointF(0.6110f, 0.5060f),
            PointF(0.6262f, 0.5007f),
            PointF(0.6411f, 0.4937f),
            PointF(0.6548f, 0.4809f),
            PointF(0.6670f, 0.4638f),
            PointF(0.6775f, 0.4436f),
            PointF(0.6859f, 0.4205f),
            PointF(0.6926f, 0.3957f),
            PointF(0.6990f, 0.3706f),
            PointF(0.7055f, 0.3456f),
            PointF(0.7116f, 0.3204f),
            PointF(0.7226f, 0.3012f),
            PointF(0.7375f, 0.2989f),
            PointF(0.7525f, 0.3057f),
            PointF(0.7644f, 0.3226f),
            PointF(0.7726f, 0.3460f),
            PointF(0.7789f, 0.3711f),
            PointF(0.7829f, 0.3977f),
            PointF(0.7851f, 0.4249f),
            PointF(0.7930f, 0.4485f),
            PointF(0.8033f, 0.4691f),
            PointF(0.8157f, 0.4855f),
            PointF(0.8291f, 0.4994f),
            PointF(0.8425f, 0.5133f),
            PointF(0.8539f, 0.5319f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.1283f, normY = 0.6658f),  // Z1  yola 154 ref-px
            BuildSpot(id = 2, normX = 0.2451f, normY = 0.2057f),  // Z1  yola 109 ref-px
            BuildSpot(id = 3, normX = 0.3409f, normY = 0.5073f),  // Z2  yola 93 ref-px
            BuildSpot(id = 4, normX = 0.4296f, normY = 0.0878f),  // Z3  yola 323 ref-px
            BuildSpot(id = 5, normX = 0.4417f, normY = 0.2863f),  // Z3  yola 109 ref-px
            BuildSpot(id = 6, normX = 0.4510f, normY = 0.5773f),  // Z3  yola 117 ref-px
            BuildSpot(id = 7, normX = 0.6280f, normY = 0.3885f),  // Z4  yola 102 ref-px
            BuildSpot(id = 8, normX = 0.6805f, normY = 0.6142f),  // Z4  yola 155 ref-px
            BuildSpot(id = 9, normX = 0.7273f, normY = 0.1835f),  // Z5  yola 106 ref-px
            BuildSpot(id = 10, normX = 0.8193f, normY = 0.8068f),  // Z5  yola 343 ref-px
        )
    )

    // ---- m06  kaynak: copied items/map (6).png
    //      rota=1  pad=11  yol=2080 ref-px
    val MAP_07 = LevelData(
        levelId = 7,
        name = "Acik Ova / Open Plain",
        description = "Parlak acik cayir; uzun ve temiz tek serpantin.",
        waypoints = listOf(
            PointF(0.1388f, 0.5021f),  // bunker yol agzi
            PointF(0.1464f, 0.4909f),
            PointF(0.1601f, 0.4967f),
            PointF(0.1755f, 0.4957f),
            PointF(0.1906f, 0.4908f),
            PointF(0.2019f, 0.4724f),
            PointF(0.2079f, 0.4471f),
            PointF(0.2133f, 0.4214f),
            PointF(0.2198f, 0.3965f),
            PointF(0.2283f, 0.3736f),
            PointF(0.2390f, 0.3538f),
            PointF(0.2519f, 0.3389f),
            PointF(0.2668f, 0.3324f),
            PointF(0.2822f, 0.3335f),
            PointF(0.2962f, 0.3445f),
            PointF(0.3080f, 0.3621f),
            PointF(0.3177f, 0.3835f),
            PointF(0.3258f, 0.4069f),
            PointF(0.3331f, 0.4311f),
            PointF(0.3407f, 0.4550f),
            PointF(0.3499f, 0.4770f),
            PointF(0.3614f, 0.4952f),
            PointF(0.3752f, 0.5074f),
            PointF(0.3903f, 0.5119f),
            PointF(0.4054f, 0.5064f),
            PointF(0.4187f, 0.4928f),
            PointF(0.4299f, 0.4738f),
            PointF(0.4384f, 0.4510f),
            PointF(0.4440f, 0.4255f),
            PointF(0.4478f, 0.3989f),
            PointF(0.4517f, 0.3723f),
            PointF(0.4566f, 0.3463f),
            PointF(0.4631f, 0.3214f),
            PointF(0.4720f, 0.2989f),
            PointF(0.4836f, 0.2809f),
            PointF(0.4973f, 0.2683f),
            PointF(0.5121f, 0.2608f),
            PointF(0.5274f, 0.2626f),
            PointF(0.5425f, 0.2684f),
            PointF(0.5563f, 0.2806f),
            PointF(0.5678f, 0.2988f),
            PointF(0.5771f, 0.3206f),
            PointF(0.5843f, 0.3449f),
            PointF(0.5894f, 0.3708f),
            PointF(0.5936f, 0.3972f),
            PointF(0.5977f, 0.4237f),
            PointF(0.6019f, 0.4501f),
            PointF(0.6105f, 0.4727f),
            PointF(0.6212f, 0.4925f),
            PointF(0.6338f, 0.5082f),
            PointF(0.6485f, 0.5165f),
            PointF(0.6637f, 0.5141f),
            PointF(0.6776f, 0.5023f),
            PointF(0.6892f, 0.4843f),
            PointF(0.6982f, 0.4621f),
            PointF(0.7051f, 0.4375f),
            PointF(0.7115f, 0.4125f),
            PointF(0.7190f, 0.3886f),
            PointF(0.7285f, 0.3669f),
            PointF(0.7401f, 0.3489f),
            PointF(0.7538f, 0.3366f),
            PointF(0.7692f, 0.3354f),
            PointF(0.7842f, 0.3416f),
            PointF(0.7982f, 0.3531f),
            PointF(0.8114f, 0.3675f),
            PointF(0.8246f, 0.3818f),
            PointF(0.8325f, 0.4000f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.1746f, normY = 0.2501f),  // Z1  yola 150 ref-px
            BuildSpot(id = 2, normX = 0.2225f, normY = 0.5784f),  // Z1  yola 117 ref-px
            BuildSpot(id = 3, normX = 0.2643f, normY = 0.7422f),  // Z1  yola 310 ref-px
            BuildSpot(id = 4, normX = 0.3831f, normY = 0.3994f),  // Z2  yola 105 ref-px
            BuildSpot(id = 5, normX = 0.4399f, normY = 0.1579f),  // Z3  yola 137 ref-px
            BuildSpot(id = 6, normX = 0.4658f, normY = 0.8069f),  // Z2  yola 344 ref-px
            BuildSpot(id = 7, normX = 0.5041f, normY = 0.6153f),  // Z2  yola 206 ref-px
            BuildSpot(id = 8, normX = 0.6465f, normY = 0.3974f),  // Z4  yola 108 ref-px
            BuildSpot(id = 9, normX = 0.7079f, normY = 0.7345f),  // Z4  yola 252 ref-px
            BuildSpot(id = 10, normX = 0.7192f, normY = 0.2113f),  // Z5  yola 127 ref-px
            BuildSpot(id = 11, normX = 0.7602f, normY = 0.4414f),  // Z5  yola 109 ref-px
        )
    )

    // ---- m07  kaynak: copied items/map (7).png
    //      rota=1  pad=11  yol=2220 ref-px
    val MAP_08 = LevelData(
        levelId = 8,
        name = "Derin Orman / Deep Forest",
        description = "Yogun cam ormani; yol usse KUZEYDEN girer.",
        waypoints = listOf(
            PointF(0.1477f, 0.5042f),  // bunker yol agzi
            PointF(0.1512f, 0.4780f),
            PointF(0.1610f, 0.4602f),
            PointF(0.1765f, 0.4573f),
            PointF(0.1918f, 0.4550f),
            PointF(0.2030f, 0.4358f),
            PointF(0.2131f, 0.4148f),
            PointF(0.2223f, 0.3924f),
            PointF(0.2311f, 0.3697f),
            PointF(0.2430f, 0.3527f),
            PointF(0.2582f, 0.3467f),
            PointF(0.2734f, 0.3515f),
            PointF(0.2872f, 0.3641f),
            PointF(0.2983f, 0.3834f),
            PointF(0.3069f, 0.4063f),
            PointF(0.3137f, 0.4312f),
            PointF(0.3192f, 0.4570f),
            PointF(0.3242f, 0.4832f),
            PointF(0.3303f, 0.5087f),
            PointF(0.3369f, 0.5337f),
            PointF(0.3458f, 0.5562f),
            PointF(0.3571f, 0.5753f),
            PointF(0.3695f, 0.5919f),
            PointF(0.3829f, 0.6058f),
            PointF(0.3972f, 0.6169f),
            PointF(0.4117f, 0.6266f),
            PointF(0.4260f, 0.6377f),
            PointF(0.4386f, 0.6538f),
            PointF(0.4505f, 0.6716f),
            PointF(0.4623f, 0.6896f),
            PointF(0.4748f, 0.7061f),
            PointF(0.4877f, 0.7215f),
            PointF(0.5015f, 0.7344f),
            PointF(0.5162f, 0.7431f),
            PointF(0.5316f, 0.7458f),
            PointF(0.5472f, 0.7450f),
            PointF(0.5620f, 0.7372f),
            PointF(0.5752f, 0.7226f),
            PointF(0.5866f, 0.7038f),
            PointF(0.5962f, 0.6820f),
            PointF(0.6041f, 0.6583f),
            PointF(0.6110f, 0.6335f),
            PointF(0.6172f, 0.6082f),
            PointF(0.6225f, 0.5822f),
            PointF(0.6300f, 0.5580f),
            PointF(0.6366f, 0.5331f),
            PointF(0.6457f, 0.5107f),
            PointF(0.6563f, 0.4905f),
            PointF(0.6682f, 0.4732f),
            PointF(0.6836f, 0.4704f),
            PointF(0.6990f, 0.4669f),
            PointF(0.7144f, 0.4623f),
            PointF(0.7294f, 0.4554f),
            PointF(0.7436f, 0.4441f),
            PointF(0.7560f, 0.4276f),
            PointF(0.7663f, 0.4069f),
            PointF(0.7731f, 0.3821f),
            PointF(0.7778f, 0.3557f),
            PointF(0.7818f, 0.3291f),
            PointF(0.7848f, 0.3019f),
            PointF(0.7870f, 0.2748f),
            PointF(0.7964f, 0.2529f),
            PointF(0.8071f, 0.2328f),
            PointF(0.8195f, 0.2163f),
            PointF(0.8344f, 0.2102f),
            PointF(0.8487f, 0.2198f),
            PointF(0.8598f, 0.2390f),
            PointF(0.8673f, 0.2632f),
            PointF(0.8733f, 0.2887f),
            PointF(0.8784f, 0.3149f),
            PointF(0.8823f, 0.3416f),
            PointF(0.8901f, 0.3640f),
            PointF(0.8852f, 0.3872f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2665f, normY = 0.4793f),  // Z2  yola 97 ref-px
            BuildSpot(id = 2, normX = 0.2934f, normY = 0.2215f),  // Z1  yola 131 ref-px
            BuildSpot(id = 3, normX = 0.3146f, normY = 0.7518f),  // Z2  yola 198 ref-px
            BuildSpot(id = 4, normX = 0.4221f, normY = 0.3759f),  // Z2  yola 220 ref-px
            BuildSpot(id = 5, normX = 0.4757f, normY = 0.8294f),  // Z3  yola 105 ref-px
            BuildSpot(id = 6, normX = 0.5225f, normY = 0.2714f),  // Z4  yola 333 ref-px
            BuildSpot(id = 7, normX = 0.5416f, normY = 0.5392f),  // Z3  yola 147 ref-px
            BuildSpot(id = 8, normX = 0.6307f, normY = 0.1777f),  // Z5  yola 306 ref-px
            BuildSpot(id = 9, normX = 0.7165f, normY = 0.3302f),  // Z4  yola 114 ref-px
            BuildSpot(id = 10, normX = 0.7640f, normY = 0.5246f),  // Z4  yola 90 ref-px
            BuildSpot(id = 11, normX = 0.7956f, normY = 0.7048f),  // Z4  yola 294 ref-px
        )
    )

    // ---- m08  kaynak: copied items/map (8).png
    //      rota=1  pad=12  yol=1970 ref-px
    val MAP_09 = LevelData(
        levelId = 9,
        name = "Sur Yamaci / Rampart Slope",
        description = "Tas duvarlar ve gozetleme kuleleri; ortada buyuk kor sapak ilmegi.",
        waypoints = listOf(
            PointF(0.1364f, 0.4128f),  // bunker yol agzi
            PointF(0.1372f, 0.4383f),
            PointF(0.1511f, 0.4311f),
            PointF(0.1664f, 0.4324f),
            PointF(0.1818f, 0.4331f),
            PointF(0.1971f, 0.4328f),
            PointF(0.2125f, 0.4347f),
            PointF(0.2279f, 0.4345f),
            PointF(0.2432f, 0.4317f),
            PointF(0.2584f, 0.4278f),
            PointF(0.2732f, 0.4204f),
            PointF(0.2866f, 0.4072f),
            PointF(0.2980f, 0.3888f),
            PointF(0.3069f, 0.3666f),
            PointF(0.3136f, 0.3421f),
            PointF(0.3190f, 0.3165f),
            PointF(0.3214f, 0.2895f),
            PointF(0.3283f, 0.2660f),
            PointF(0.3388f, 0.2461f),
            PointF(0.3531f, 0.2388f),
            PointF(0.3677f, 0.2471f),
            PointF(0.3828f, 0.2520f),
            PointF(0.3977f, 0.2588f),
            PointF(0.4115f, 0.2708f),
            PointF(0.4242f, 0.2862f),
            PointF(0.4360f, 0.3036f),
            PointF(0.4476f, 0.3216f),
            PointF(0.4595f, 0.3390f),
            PointF(0.4724f, 0.3537f),
            PointF(0.4865f, 0.3646f),
            PointF(0.5017f, 0.3690f),
            PointF(0.5169f, 0.3656f),
            PointF(0.5310f, 0.3551f),
            PointF(0.5439f, 0.3402f),
            PointF(0.5570f, 0.3260f),
            PointF(0.5708f, 0.3139f),
            PointF(0.5852f, 0.3044f),
            PointF(0.6004f, 0.3014f),
            PointF(0.6158f, 0.3028f),
            PointF(0.6303f, 0.3117f),
            PointF(0.6431f, 0.3266f),
            PointF(0.6541f, 0.3457f),
            PointF(0.6618f, 0.3693f),
            PointF(0.6647f, 0.3960f),
            PointF(0.6647f, 0.4233f),
            PointF(0.6620f, 0.4502f),
            PointF(0.6573f, 0.4763f),
            PointF(0.6525f, 0.5022f),
            PointF(0.6487f, 0.5287f),
            PointF(0.6481f, 0.5559f),
            PointF(0.6531f, 0.5816f),
            PointF(0.6633f, 0.6019f),
            PointF(0.6775f, 0.6116f),
            PointF(0.6926f, 0.6088f),
            PointF(0.7061f, 0.5958f),
            PointF(0.7180f, 0.5784f),
            PointF(0.7282f, 0.5581f),
            PointF(0.7370f, 0.5356f),
            PointF(0.7491f, 0.5191f),
            PointF(0.7637f, 0.5104f),
            PointF(0.7781f, 0.5010f),
            PointF(0.7932f, 0.4963f),
            PointF(0.8086f, 0.4967f),
            PointF(0.8231f, 0.5055f),
            PointF(0.8367f, 0.5181f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2300f, normY = 0.5604f),  // Z1  yola 146 ref-px
            BuildSpot(id = 2, normX = 0.2356f, normY = 0.2784f),  // Z1  yola 152 ref-px
            BuildSpot(id = 3, normX = 0.3116f, normY = 0.1571f),  // Z2  yola 104 ref-px
            BuildSpot(id = 4, normX = 0.3832f, normY = 0.3922f),  // Z2  yola 143 ref-px
            BuildSpot(id = 5, normX = 0.4226f, normY = 0.6383f),  // Z3  yola 309 ref-px
            BuildSpot(id = 6, normX = 0.4978f, normY = 0.2331f),  // Z2  yola 132 ref-px
            BuildSpot(id = 7, normX = 0.5423f, normY = 0.5720f),  // Z4  yola 182 ref-px
            BuildSpot(id = 8, normX = 0.5995f, normY = 0.4098f),  // Z4  yola 113 ref-px
            BuildSpot(id = 9, normX = 0.6280f, normY = 0.7597f),  // Z4  yola 166 ref-px
            BuildSpot(id = 10, normX = 0.6710f, normY = 0.1288f),  // Z3  yola 198 ref-px
            BuildSpot(id = 11, normX = 0.7688f, normY = 0.2893f),  // Z5  yola 214 ref-px
            BuildSpot(id = 12, normX = 0.7739f, normY = 0.5810f),  // Z5  yola 99 ref-px
        )
    )

    // ---- m09  kaynak: copied items/map (9).png
    //      rota=1  pad=17  yol=2248 ref-px
    val MAP_10 = LevelData(
        levelId = 10,
        name = "Nehir Kollari / River Fork",
        description = "Cok kollu nehir ve ahsap kopruler; maske nehirlerde parcali.",
        waypoints = listOf(
            PointF(0.1398f, 0.4847f),  // bunker yol agzi
            PointF(0.1427f, 0.4588f),
            PointF(0.1565f, 0.4487f),
            PointF(0.1713f, 0.4408f),
            PointF(0.1866f, 0.4395f),
            PointF(0.2013f, 0.4474f),
            PointF(0.2157f, 0.4576f),
            PointF(0.2298f, 0.4686f),
            PointF(0.2424f, 0.4846f),
            PointF(0.2532f, 0.5042f),
            PointF(0.2618f, 0.5270f),
            PointF(0.2686f, 0.5517f),
            PointF(0.2732f, 0.5779f),
            PointF(0.2760f, 0.6050f),
            PointF(0.2773f, 0.6323f),
            PointF(0.2774f, 0.6598f),
            PointF(0.2787f, 0.6872f),
            PointF(0.2796f, 0.7147f),
            PointF(0.2849f, 0.7404f),
            PointF(0.2931f, 0.7637f),
            PointF(0.3037f, 0.7836f),
            PointF(0.3161f, 0.8000f),
            PointF(0.3302f, 0.8111f),
            PointF(0.3455f, 0.8150f),
            PointF(0.3609f, 0.8177f),
            PointF(0.3763f, 0.8148f),
            PointF(0.3912f, 0.8075f),
            PointF(0.4049f, 0.7949f),
            PointF(0.4177f, 0.7794f),
            PointF(0.4297f, 0.7622f),
            PointF(0.4403f, 0.7421f),
            PointF(0.4500f, 0.7207f),
            PointF(0.4599f, 0.6996f),
            PointF(0.4712f, 0.6810f),
            PointF(0.4823f, 0.6618f),
            PointF(0.4918f, 0.6403f),
            PointF(0.4981f, 0.6158f),
            PointF(0.5127f, 0.6176f),
            PointF(0.5279f, 0.6208f),
            PointF(0.5434f, 0.6223f),
            PointF(0.5582f, 0.6296f),
            PointF(0.5731f, 0.6361f),
            PointF(0.5879f, 0.6290f),
            PointF(0.5983f, 0.6091f),
            PointF(0.6030f, 0.5830f),
            PointF(0.6044f, 0.5556f),
            PointF(0.6140f, 0.5352f),
            PointF(0.6256f, 0.5169f),
            PointF(0.6394f, 0.5057f),
            PointF(0.6512f, 0.4881f),
            PointF(0.6635f, 0.4730f),
            PointF(0.6771f, 0.4606f),
            PointF(0.6878f, 0.4408f),
            PointF(0.6948f, 0.4164f),
            PointF(0.6993f, 0.3901f),
            PointF(0.7014f, 0.3629f),
            PointF(0.7040f, 0.3358f),
            PointF(0.7098f, 0.3103f),
            PointF(0.7128f, 0.2834f),
            PointF(0.7220f, 0.2618f),
            PointF(0.7360f, 0.2517f),
            PointF(0.7514f, 0.2529f),
            PointF(0.7647f, 0.2662f),
            PointF(0.7748f, 0.2870f),
            PointF(0.7836f, 0.3096f),
            PointF(0.7909f, 0.3339f),
            PointF(0.7975f, 0.3587f),
            PointF(0.8063f, 0.3812f),
            PointF(0.8162f, 0.4024f),
            PointF(0.8292f, 0.4167f),
            PointF(0.8426f, 0.4085f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.1797f, normY = 0.3294f),  // Z1  yola 114 ref-px
            BuildSpot(id = 2, normX = 0.2217f, normY = 0.5959f),  // Z1  yola 93 ref-px
            BuildSpot(id = 3, normX = 0.2223f, normY = 0.7431f),  // Z2  yola 107 ref-px
            BuildSpot(id = 4, normX = 0.2776f, normY = 0.2352f),  // Z1  yola 253 ref-px
            BuildSpot(id = 5, normX = 0.3586f, normY = 0.7033f),  // Z2  yola 122 ref-px
            BuildSpot(id = 6, normX = 0.3593f, normY = 0.3465f),  // Z1  yola 267 ref-px
            BuildSpot(id = 7, normX = 0.3694f, normY = 0.4987f),  // Z1  yola 203 ref-px
            BuildSpot(id = 8, normX = 0.4616f, normY = 0.0691f),  // Z5  yola 528 ref-px
            BuildSpot(id = 9, normX = 0.4732f, normY = 0.8800f),  // Z3  yola 141 ref-px
            BuildSpot(id = 10, normX = 0.5430f, normY = 0.6783f),  // Z4  yola 111 ref-px
            BuildSpot(id = 11, normX = 0.5451f, normY = 0.4730f),  // Z4  yola 141 ref-px
            BuildSpot(id = 12, normX = 0.6125f, normY = 0.1973f),  // Z5  yola 207 ref-px
            BuildSpot(id = 13, normX = 0.6434f, normY = 0.3748f),  // Z4  yola 104 ref-px
            BuildSpot(id = 14, normX = 0.7150f, normY = 0.6861f),  // Z3  yola 216 ref-px
            BuildSpot(id = 15, normX = 0.7414f, normY = 0.4897f),  // Z4  yola 117 ref-px
            BuildSpot(id = 16, normX = 0.8530f, normY = 0.2507f),  // Z5  yola 151 ref-px
            BuildSpot(id = 17, normX = 0.8636f, normY = 0.6989f),  // Z5  yola 316 ref-px
        )
    )

    // ---- m10  kaynak: copied items/map (10).png
    //      rota=2  pad=10  yol=1785 ref-px
    val MAP_11 = LevelData(
        levelId = 11,
        name = "Koy Siniri / Village Outskirts",
        description = "Ciftlik binalari ve citler; yol spawn'da catallanip usse birlesir.",
        waypoints = listOf(
            PointF(0.1340f, 0.4596f),  // bunker yol agzi
            PointF(0.1430f, 0.4691f),
            PointF(0.1582f, 0.4749f),
            PointF(0.1724f, 0.4862f),
            PointF(0.1844f, 0.5038f),
            PointF(0.1940f, 0.5256f),
            PointF(0.2001f, 0.5511f),
            PointF(0.2039f, 0.5780f),
            PointF(0.2059f, 0.6055f),
            PointF(0.2070f, 0.6331f),
            PointF(0.2081f, 0.6608f),
            PointF(0.2096f, 0.6884f),
            PointF(0.2123f, 0.7157f),
            PointF(0.2171f, 0.7421f),
            PointF(0.2245f, 0.7664f),
            PointF(0.2345f, 0.7876f),
            PointF(0.2466f, 0.8051f),
            PointF(0.2601f, 0.8190f),
            PointF(0.2742f, 0.8308f),
            PointF(0.2881f, 0.8434f),
            PointF(0.3016f, 0.8575f),
            PointF(0.3139f, 0.8743f),
            PointF(0.3257f, 0.8926f),
            PointF(0.3389f, 0.9067f),
            PointF(0.3535f, 0.9165f),
            PointF(0.3688f, 0.9207f),
            PointF(0.3844f, 0.9208f),
            PointF(0.4000f, 0.9206f),
            PointF(0.4155f, 0.9168f),
            PointF(0.4306f, 0.9099f),
            PointF(0.4455f, 0.9021f),
            PointF(0.4605f, 0.8939f),
            PointF(0.4749f, 0.8837f),
            PointF(0.4884f, 0.8699f),
            PointF(0.5008f, 0.8530f),
            PointF(0.5119f, 0.8335f),
            PointF(0.5214f, 0.8115f),
            PointF(0.5298f, 0.7881f),
            PointF(0.5385f, 0.7651f),
            PointF(0.5489f, 0.7445f),
            PointF(0.5617f, 0.7287f),
            PointF(0.5767f, 0.7215f),
            PointF(0.5923f, 0.7208f),
            PointF(0.6079f, 0.7208f),
            PointF(0.6235f, 0.7208f),
            PointF(0.6391f, 0.7208f),
            PointF(0.6546f, 0.7199f),
            PointF(0.6698f, 0.7133f),
            PointF(0.6838f, 0.7011f),
            PointF(0.6965f, 0.6852f),
            PointF(0.7085f, 0.6673f),
            PointF(0.7202f, 0.6490f),
            PointF(0.7322f, 0.6314f),
            PointF(0.7453f, 0.6165f),
            PointF(0.7598f, 0.6060f),
            PointF(0.7750f, 0.6000f),
            PointF(0.7899f, 0.5923f),
            PointF(0.8051f, 0.5942f),
            PointF(0.8203f, 0.5986f),
            PointF(0.8158f, 0.5957f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2437f, normY = 0.1383f),  // Z1  A-kolu 388 / B-kolu 105 ref-px
            BuildSpot(id = 2, normX = 0.2645f, normY = 0.3422f),  // Z1  A-kolu 221 / B-kolu 108 ref-px
            BuildSpot(id = 3, normX = 0.2752f, normY = 0.7044f),  // Z2  A-kolu 119 / B-kolu 159 ref-px
            BuildSpot(id = 4, normX = 0.3815f, normY = 0.5029f),  // Z2  A-kolu 355 / B-kolu 117 ref-px
            BuildSpot(id = 5, normX = 0.3896f, normY = 0.2101f),  // Z2  A-kolu 498 / B-kolu 157 ref-px
            BuildSpot(id = 6, normX = 0.5423f, normY = 0.6082f),  // Z4  A-kolu 115 / B-kolu 211 ref-px
            BuildSpot(id = 7, normX = 0.6433f, normY = 0.6146f),  // Z4  A-kolu 107 / B-kolu 200 ref-px
            BuildSpot(id = 8, normX = 0.6453f, normY = 0.3175f),  // Z4  A-kolu 360 / B-kolu  99 ref-px
            BuildSpot(id = 9, normX = 0.7574f, normY = 0.7648f),  // Z5  A-kolu 152 / B-kolu 213 ref-px
            BuildSpot(id = 10, normX = 0.8128f, normY = 0.1908f),  // Z5  A-kolu 434 / B-kolu 212 ref-px
        )
    )

    /** Catallanan haritalarin IKINCI kolu. levelId -> waypoint listesi. */
    val ALT_ROUTES: Map<Int, List<PointF>> = mapOf(
        1 to listOf(
            PointF(0.1388f, 0.4638f),  // bunker yol agzi
            PointF(0.1433f, 0.4832f),
            PointF(0.1588f, 0.4847f),
            PointF(0.1743f, 0.4847f),
            PointF(0.1893f, 0.4817f),
            PointF(0.2003f, 0.4623f),
            PointF(0.2013f, 0.4359f),
            PointF(0.1969f, 0.4096f),
            PointF(0.2023f, 0.3842f),
            PointF(0.2097f, 0.3601f),
            PointF(0.2156f, 0.3347f),
            PointF(0.2219f, 0.3096f),
            PointF(0.2303f, 0.2865f),
            PointF(0.2419f, 0.2686f),
            PointF(0.2564f, 0.2596f),
            PointF(0.2718f, 0.2588f),
            PointF(0.2865f, 0.2670f),
            PointF(0.3014f, 0.2747f),
            PointF(0.3165f, 0.2804f),
            PointF(0.3319f, 0.2819f),
            PointF(0.3474f, 0.2819f),
            PointF(0.3627f, 0.2790f),
            PointF(0.3772f, 0.2695f),
            PointF(0.3906f, 0.2557f),
            PointF(0.4036f, 0.2409f),
            PointF(0.4165f, 0.2257f),
            PointF(0.4296f, 0.2109f),
            PointF(0.4432f, 0.1979f),
            PointF(0.4571f, 0.1859f),
            PointF(0.4717f, 0.1772f),
            PointF(0.4870f, 0.1792f),
            PointF(0.5001f, 0.1936f),
            PointF(0.5121f, 0.2111f),
            PointF(0.5232f, 0.2302f),
            PointF(0.5343f, 0.2493f),
            PointF(0.5466f, 0.2660f),
            PointF(0.5606f, 0.2776f),
            PointF(0.5758f, 0.2818f),
            PointF(0.5913f, 0.2819f),
            PointF(0.6067f, 0.2804f),
            PointF(0.6216f, 0.2733f),
            PointF(0.6364f, 0.2652f),
            PointF(0.6514f, 0.2587f),
            PointF(0.6667f, 0.2546f),
            PointF(0.6822f, 0.2529f),
            PointF(0.6976f, 0.2514f),
            PointF(0.7131f, 0.2516f),
            PointF(0.7285f, 0.2546f),
            PointF(0.7433f, 0.2619f),
            PointF(0.7566f, 0.2758f),
            PointF(0.7679f, 0.2945f),
            PointF(0.7755f, 0.3183f),
            PointF(0.7787f, 0.3452f),
            PointF(0.7773f, 0.3725f),
            PointF(0.7728f, 0.3988f),
            PointF(0.7668f, 0.4241f),
            PointF(0.7604f, 0.4491f),
            PointF(0.7545f, 0.4746f),
            PointF(0.7490f, 0.5003f),
            PointF(0.7453f, 0.5270f),
            PointF(0.7432f, 0.5542f),
            PointF(0.7432f, 0.5817f),
            PointF(0.7465f, 0.6084f),
            PointF(0.7560f, 0.6300f),
            PointF(0.7669f, 0.6495f),
            PointF(0.7782f, 0.6684f),
            PointF(0.7902f, 0.6858f),
            PointF(0.8032f, 0.7005f),
            PointF(0.8177f, 0.7102f),
            PointF(0.8330f, 0.7121f),
            PointF(0.8482f, 0.7070f),
            PointF(0.8625f, 0.6967f),
            PointF(0.8751f, 0.6808f),
            PointF(0.8804f, 0.6638f),  // us rampasi
        ),
        2 to listOf(
            PointF(0.1435f, 0.5021f),  // bunker yol agzi
            PointF(0.1572f, 0.5007f),
            PointF(0.1703f, 0.4903f),
            PointF(0.1741f, 0.4640f),
            PointF(0.1785f, 0.4394f),
            PointF(0.1935f, 0.4332f),
            PointF(0.2070f, 0.4202f),
            PointF(0.2192f, 0.4035f),
            PointF(0.2316f, 0.3871f),
            PointF(0.2448f, 0.3731f),
            PointF(0.2601f, 0.3708f),
            PointF(0.2755f, 0.3725f),
            PointF(0.2902f, 0.3805f),
            PointF(0.3040f, 0.3926f),
            PointF(0.3174f, 0.4063f),
            PointF(0.3309f, 0.4194f),
            PointF(0.3454f, 0.4288f),
            PointF(0.3607f, 0.4319f),
            PointF(0.3761f, 0.4305f),
            PointF(0.3904f, 0.4205f),
            PointF(0.4024f, 0.4036f),
            PointF(0.4124f, 0.3827f),
            PointF(0.4199f, 0.3588f),
            PointF(0.4243f, 0.3325f),
            PointF(0.4272f, 0.3056f),
            PointF(0.4304f, 0.2788f),
            PointF(0.4342f, 0.2522f),
            PointF(0.4385f, 0.2261f),
            PointF(0.4486f, 0.2054f),
            PointF(0.4610f, 0.1895f),
            PointF(0.4763f, 0.1877f),
            PointF(0.4916f, 0.1914f),
            PointF(0.5057f, 0.2020f),
            PointF(0.5192f, 0.2154f),
            PointF(0.5324f, 0.2295f),
            PointF(0.5460f, 0.2424f),
            PointF(0.5607f, 0.2504f),
            PointF(0.5761f, 0.2517f),
            PointF(0.5915f, 0.2540f),
            PointF(0.6069f, 0.2527f),
            PointF(0.6223f, 0.2514f),
            PointF(0.6375f, 0.2552f),
            PointF(0.6516f, 0.2663f),
            PointF(0.6637f, 0.2832f),
            PointF(0.6735f, 0.3043f),
            PointF(0.6799f, 0.3291f),
            PointF(0.6846f, 0.3552f),
            PointF(0.6874f, 0.3822f),
            PointF(0.6893f, 0.4094f),
            PointF(0.6913f, 0.4366f),
            PointF(0.6935f, 0.4638f),
            PointF(0.6962f, 0.4908f),
            PointF(0.7004f, 0.5171f),
            PointF(0.7044f, 0.5436f),
            PointF(0.7092f, 0.5697f),
            PointF(0.7149f, 0.5952f),
            PointF(0.7214f, 0.6200f),
            PointF(0.7276f, 0.6451f),
            PointF(0.7331f, 0.6705f),
            PointF(0.7435f, 0.6906f),
            PointF(0.7571f, 0.7032f),
            PointF(0.7717f, 0.7116f),
            PointF(0.7868f, 0.7175f),
            PointF(0.8021f, 0.7163f),
            PointF(0.8166f, 0.7069f),
            PointF(0.8293f, 0.6916f),
            PointF(0.8410f, 0.6737f),
            PointF(0.8519f, 0.6543f),
            PointF(0.8601f, 0.6313f),
            PointF(0.8699f, 0.6102f),
            PointF(0.8828f, 0.5957f),  // us rampasi
        ),
        3 to listOf(
            PointF(0.1292f, 0.5064f),  // bunker yol agzi
            PointF(0.1448f, 0.5043f),
            PointF(0.1602f, 0.5013f),
            PointF(0.1732f, 0.4860f),
            PointF(0.1859f, 0.4699f),
            PointF(0.1978f, 0.4520f),
            PointF(0.2117f, 0.4395f),
            PointF(0.2251f, 0.4256f),
            PointF(0.2357f, 0.4051f),
            PointF(0.2450f, 0.3828f),
            PointF(0.2533f, 0.3594f),
            PointF(0.2611f, 0.3353f),
            PointF(0.2693f, 0.3116f),
            PointF(0.2776f, 0.2882f),
            PointF(0.2898f, 0.2713f),
            PointF(0.3050f, 0.2650f),
            PointF(0.3204f, 0.2608f),
            PointF(0.3345f, 0.2710f),
            PointF(0.3454f, 0.2908f),
            PointF(0.3547f, 0.3131f),
            PointF(0.3581f, 0.3396f),
            PointF(0.3570f, 0.3673f),
            PointF(0.3567f, 0.3951f),
            PointF(0.3537f, 0.4223f),
            PointF(0.3501f, 0.4493f),
            PointF(0.3461f, 0.4762f),
            PointF(0.3391f, 0.5008f),
            PointF(0.3282f, 0.5207f),
            PointF(0.3227f, 0.5459f),
            PointF(0.3213f, 0.5736f),
            PointF(0.3193f, 0.6011f),
            PointF(0.3185f, 0.6287f),
            PointF(0.3222f, 0.6554f),
            PointF(0.3310f, 0.6784f),
            PointF(0.3408f, 0.6999f),
            PointF(0.3526f, 0.7181f),
            PointF(0.3664f, 0.7311f),
            PointF(0.3816f, 0.7369f),
            PointF(0.3972f, 0.7375f),
            PointF(0.4128f, 0.7372f),
            PointF(0.4281f, 0.7319f),
            PointF(0.4426f, 0.7217f),
            PointF(0.4564f, 0.7085f),
            PointF(0.4692f, 0.6927f),
            PointF(0.4811f, 0.6747f),
            PointF(0.4919f, 0.6546f),
            PointF(0.5010f, 0.6322f),
            PointF(0.5080f, 0.6074f),
            PointF(0.5125f, 0.5808f),
            PointF(0.5156f, 0.5536f),
            PointF(0.5139f, 0.5263f),
            PointF(0.5104f, 0.4994f),
            PointF(0.5102f, 0.4716f),
            PointF(0.5094f, 0.4439f),
            PointF(0.5131f, 0.4180f),
            PointF(0.5211f, 0.3948f),
            PointF(0.5240f, 0.3677f),
            PointF(0.5260f, 0.3405f),
            PointF(0.5353f, 0.3183f),
            PointF(0.5441f, 0.2954f),
            PointF(0.5562f, 0.2781f),
            PointF(0.5663f, 0.2569f),
            PointF(0.5754f, 0.2344f),
            PointF(0.5852f, 0.2126f),
            PointF(0.5969f, 0.1950f),
            PointF(0.6117f, 0.2001f),
            PointF(0.6217f, 0.2213f),
            PointF(0.6305f, 0.2441f),
            PointF(0.6402f, 0.2660f),
            PointF(0.6484f, 0.2895f),
            PointF(0.6590f, 0.3093f),
            PointF(0.6735f, 0.3197f),
            PointF(0.6881f, 0.3297f),
            PointF(0.7032f, 0.3361f),
            PointF(0.7185f, 0.3420f),
            PointF(0.7330f, 0.3518f),
            PointF(0.7450f, 0.3695f),
            PointF(0.7541f, 0.3920f),
            PointF(0.7595f, 0.4180f),
            PointF(0.7617f, 0.4454f),
            PointF(0.7617f, 0.4732f),
            PointF(0.7606f, 0.5009f),
            PointF(0.7612f, 0.5286f),
            PointF(0.7592f, 0.5560f),
            PointF(0.7609f, 0.5833f),
            PointF(0.7642f, 0.6104f),
            PointF(0.7745f, 0.6303f),
            PointF(0.7897f, 0.6366f),
            PointF(0.8035f, 0.6491f),
            PointF(0.8183f, 0.6561f),
            PointF(0.8325f, 0.6451f),
            PointF(0.8467f, 0.6339f),
            PointF(0.8619f, 0.6278f),
            PointF(0.8775f, 0.6251f),
            PointF(0.8929f, 0.6208f),
            PointF(0.9068f, 0.6085f),
            PointF(0.9211f, 0.5986f),  // us rampasi
        ),
        4 to listOf(
            PointF(0.1445f, 0.4792f),  // bunker yol agzi
            PointF(0.1587f, 0.4863f),
            PointF(0.1739f, 0.4904f),
            PointF(0.1885f, 0.5001f),
            PointF(0.2029f, 0.5105f),
            PointF(0.2151f, 0.5278f),
            PointF(0.2236f, 0.5508f),
            PointF(0.2279f, 0.5774f),
            PointF(0.2313f, 0.6045f),
            PointF(0.2379f, 0.6296f),
            PointF(0.2472f, 0.6517f),
            PointF(0.2591f, 0.6697f),
            PointF(0.2715f, 0.6864f),
            PointF(0.2836f, 0.7040f),
            PointF(0.2952f, 0.7225f),
            PointF(0.3057f, 0.7431f),
            PointF(0.3146f, 0.7658f),
            PointF(0.3232f, 0.7891f),
            PointF(0.3329f, 0.8106f),
            PointF(0.3447f, 0.8289f),
            PointF(0.3580f, 0.8433f),
            PointF(0.3729f, 0.8508f),
            PointF(0.3884f, 0.8492f),
            PointF(0.4026f, 0.8380f),
            PointF(0.4146f, 0.8202f),
            PointF(0.4240f, 0.7981f),
            PointF(0.4318f, 0.7741f),
            PointF(0.4387f, 0.7492f),
            PointF(0.4430f, 0.7227f),
            PointF(0.4501f, 0.6988f),
            PointF(0.4624f, 0.6817f),
            PointF(0.4770f, 0.6743f),
            PointF(0.4908f, 0.6861f),
            PointF(0.5035f, 0.7022f),
            PointF(0.5159f, 0.7190f),
            PointF(0.5267f, 0.7390f),
            PointF(0.5377f, 0.7586f),
            PointF(0.5493f, 0.7772f),
            PointF(0.5619f, 0.7936f),
            PointF(0.5760f, 0.8052f),
            PointF(0.5914f, 0.8096f),
            PointF(0.6070f, 0.8085f),
            PointF(0.6218f, 0.8003f),
            PointF(0.6350f, 0.7854f),
            PointF(0.6449f, 0.7642f),
            PointF(0.6552f, 0.7440f),
            PointF(0.6686f, 0.7297f),
            PointF(0.6815f, 0.7143f),
            PointF(0.6969f, 0.7113f),
            PointF(0.7117f, 0.7025f),
            PointF(0.7255f, 0.6895f),
            PointF(0.7379f, 0.6728f),
            PointF(0.7470f, 0.6504f),
            PointF(0.7535f, 0.6252f),
            PointF(0.7581f, 0.5987f),
            PointF(0.7643f, 0.5733f),
            PointF(0.7715f, 0.5486f),
            PointF(0.7815f, 0.5275f),
            PointF(0.7932f, 0.5093f),
            PointF(0.8082f, 0.5026f),
            PointF(0.8211f, 0.4875f),  // us rampasi
        ),
        11 to listOf(
            PointF(0.1340f, 0.4596f),  // bunker yol agzi
            PointF(0.1407f, 0.4613f),
            PointF(0.1399f, 0.4337f),
            PointF(0.1445f, 0.4085f),
            PointF(0.1593f, 0.4040f),
            PointF(0.1741f, 0.3971f),
            PointF(0.1861f, 0.3796f),
            PointF(0.1955f, 0.3576f),
            PointF(0.2029f, 0.3334f),
            PointF(0.2110f, 0.3098f),
            PointF(0.2208f, 0.2884f),
            PointF(0.2324f, 0.2700f),
            PointF(0.2458f, 0.2563f),
            PointF(0.2608f, 0.2495f),
            PointF(0.2763f, 0.2487f),
            PointF(0.2916f, 0.2532f),
            PointF(0.3054f, 0.2658f),
            PointF(0.3170f, 0.2840f),
            PointF(0.3261f, 0.3064f),
            PointF(0.3319f, 0.3319f),
            PointF(0.3336f, 0.3593f),
            PointF(0.3335f, 0.3870f),
            PointF(0.3315f, 0.4143f),
            PointF(0.3278f, 0.4411f),
            PointF(0.3243f, 0.4680f),
            PointF(0.3222f, 0.4954f),
            PointF(0.3212f, 0.5229f),
            PointF(0.3229f, 0.5504f),
            PointF(0.3274f, 0.5767f),
            PointF(0.3356f, 0.6001f),
            PointF(0.3469f, 0.6189f),
            PointF(0.3606f, 0.6319f),
            PointF(0.3758f, 0.6372f),
            PointF(0.3912f, 0.6357f),
            PointF(0.4057f, 0.6259f),
            PointF(0.4187f, 0.6109f),
            PointF(0.4300f, 0.5920f),
            PointF(0.4390f, 0.5694f),
            PointF(0.4461f, 0.5449f),
            PointF(0.4514f, 0.5189f),
            PointF(0.4551f, 0.4921f),
            PointF(0.4579f, 0.4649f),
            PointF(0.4603f, 0.4376f),
            PointF(0.4629f, 0.4104f),
            PointF(0.4667f, 0.3836f),
            PointF(0.4717f, 0.3575f),
            PointF(0.4778f, 0.3322f),
            PointF(0.4877f, 0.3110f),
            PointF(0.4991f, 0.2922f),
            PointF(0.5118f, 0.2764f),
            PointF(0.5257f, 0.2640f),
            PointF(0.5402f, 0.2541f),
            PointF(0.5552f, 0.2469f),
            PointF(0.5705f, 0.2421f),
            PointF(0.5859f, 0.2386f),
            PointF(0.6014f, 0.2373f),
            PointF(0.6169f, 0.2350f),
            PointF(0.6324f, 0.2347f),
            PointF(0.6480f, 0.2348f),
            PointF(0.6634f, 0.2379f),
            PointF(0.6784f, 0.2451f),
            PointF(0.6924f, 0.2568f),
            PointF(0.7052f, 0.2725f),
            PointF(0.7157f, 0.2929f),
            PointF(0.7238f, 0.3163f),
            PointF(0.7286f, 0.3426f),
            PointF(0.7310f, 0.3698f),
            PointF(0.7320f, 0.3974f),
            PointF(0.7320f, 0.4250f),
            PointF(0.7320f, 0.4526f),
            PointF(0.7322f, 0.4803f),
            PointF(0.7342f, 0.5076f),
            PointF(0.7373f, 0.5345f),
            PointF(0.7478f, 0.5544f),
            PointF(0.7614f, 0.5677f),
            PointF(0.7761f, 0.5767f),
            PointF(0.7908f, 0.5855f),
            PointF(0.8054f, 0.5939f),
            PointF(0.8204f, 0.5986f),
            PointF(0.8158f, 0.5957f),  // us rampasi
        ),
    )

    val ALL_MAPS: List<LevelData> = listOf(
        MAP_01, MAP_02, MAP_03, MAP_04, MAP_05, MAP_06, MAP_07, MAP_08, MAP_09, MAP_10, MAP_11
    )
}
