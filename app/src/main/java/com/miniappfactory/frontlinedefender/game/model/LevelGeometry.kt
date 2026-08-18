package com.miniappfactory.frontlinedefender.game.model

/**
 * OTOMATIK URETILDI - docs/level_geometry/extract_geometry.py
 *
 * 11 benzersiz harita (1672x941) icin OLCULMUS oynanis geometrisi.
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
            PointF(0.1497f, 0.4850f),
            PointF(0.1672f, 0.4847f),
            PointF(0.1844f, 0.4913f),
            PointF(0.2010f, 0.5039f),
            PointF(0.2165f, 0.5200f),
            PointF(0.2280f, 0.5444f),
            PointF(0.2345f, 0.5743f),
            PointF(0.2419f, 0.6035f),
            PointF(0.2519f, 0.6302f),
            PointF(0.2635f, 0.6548f),
            PointF(0.2778f, 0.6740f),
            PointF(0.2944f, 0.6868f),
            PointF(0.3119f, 0.6943f),
            PointF(0.3298f, 0.6963f),
            PointF(0.3478f, 0.6927f),
            PointF(0.3651f, 0.6840f),
            PointF(0.3810f, 0.6691f),
            PointF(0.3958f, 0.6507f),
            PointF(0.4102f, 0.6312f),
            PointF(0.4238f, 0.6102f),
            PointF(0.4370f, 0.5883f),
            PointF(0.4477f, 0.5634f),
            PointF(0.4615f, 0.5435f),
            PointF(0.4749f, 0.5222f),
            PointF(0.4891f, 0.5023f),
            PointF(0.5045f, 0.4859f),
            PointF(0.5205f, 0.4745f),
            PointF(0.5381f, 0.4725f),
            PointF(0.5540f, 0.4830f),
            PointF(0.5686f, 0.5013f),
            PointF(0.5791f, 0.5274f),
            PointF(0.5865f, 0.5565f),
            PointF(0.5907f, 0.5862f),
            PointF(0.5888f, 0.6173f),
            PointF(0.5946f, 0.6475f),
            PointF(0.6040f, 0.6748f),
            PointF(0.6165f, 0.6977f),
            PointF(0.6327f, 0.7113f),
            PointF(0.6505f, 0.7164f),
            PointF(0.6640f, 0.7084f),
            PointF(0.6793f, 0.6937f),
            PointF(0.6940f, 0.6955f),
            PointF(0.7108f, 0.6842f),
            PointF(0.7283f, 0.6765f),
            PointF(0.7461f, 0.6720f),
            PointF(0.7627f, 0.6793f),
            PointF(0.7792f, 0.6797f),
            PointF(0.7966f, 0.6883f),
            PointF(0.8138f, 0.6975f),
            PointF(0.8310f, 0.7073f),
            PointF(0.8426f, 0.6987f),
            PointF(0.8601f, 0.6951f),
            PointF(0.8751f, 0.6895f),
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
            PointF(0.1609f, 0.5009f),
            PointF(0.1785f, 0.5089f),
            PointF(0.1936f, 0.5260f),
            PointF(0.2038f, 0.5526f),
            PointF(0.2114f, 0.5819f),
            PointF(0.2148f, 0.6136f),
            PointF(0.2180f, 0.6453f),
            PointF(0.2272f, 0.6729f),
            PointF(0.2400f, 0.6958f),
            PointF(0.2556f, 0.7121f),
            PointF(0.2731f, 0.7203f),
            PointF(0.2911f, 0.7240f),
            PointF(0.3092f, 0.7218f),
            PointF(0.3272f, 0.7174f),
            PointF(0.3452f, 0.7207f),
            PointF(0.3625f, 0.7303f),
            PointF(0.3799f, 0.7394f),
            PointF(0.3973f, 0.7484f),
            PointF(0.4152f, 0.7535f),
            PointF(0.4327f, 0.7458f),
            PointF(0.4487f, 0.7305f),
            PointF(0.4647f, 0.7153f),
            PointF(0.4804f, 0.6992f),
            PointF(0.4968f, 0.6853f),
            PointF(0.5139f, 0.6746f),
            PointF(0.5317f, 0.6682f),
            PointF(0.5496f, 0.6722f),
            PointF(0.5662f, 0.6851f),
            PointF(0.5815f, 0.7025f),
            PointF(0.5938f, 0.7261f),
            PointF(0.6067f, 0.7487f),
            PointF(0.6233f, 0.7612f),
            PointF(0.6412f, 0.7664f),
            PointF(0.6594f, 0.7663f),
            PointF(0.6771f, 0.7598f),
            PointF(0.6946f, 0.7508f),
            PointF(0.7116f, 0.7396f),
            PointF(0.7286f, 0.7284f),
            PointF(0.7465f, 0.7242f),
            PointF(0.7647f, 0.7249f),
            PointF(0.7827f, 0.7220f),
            PointF(0.8005f, 0.7153f),
            PointF(0.8172f, 0.7032f),
            PointF(0.8302f, 0.6810f),
            PointF(0.8404f, 0.6543f),
            PointF(0.8531f, 0.6314f),
            PointF(0.8680f, 0.6129f),
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
            PointF(0.1316f, 0.5021f),  // bunker yol agzi
            PointF(0.1478f, 0.5036f),
            PointF(0.1581f, 0.4775f),
            PointF(0.1677f, 0.4595f),
            PointF(0.1853f, 0.4576f),
            PointF(0.2026f, 0.4578f),
            PointF(0.2160f, 0.4391f),
            PointF(0.2263f, 0.4135f),
            PointF(0.2361f, 0.3866f),
            PointF(0.2453f, 0.3589f),
            PointF(0.2529f, 0.3299f),
            PointF(0.2609f, 0.3011f),
            PointF(0.2732f, 0.2778f),
            PointF(0.2885f, 0.2610f),
            PointF(0.3057f, 0.2512f),
            PointF(0.3236f, 0.2533f),
            PointF(0.3403f, 0.2648f),
            PointF(0.3546f, 0.2843f),
            PointF(0.3611f, 0.3139f),
            PointF(0.3653f, 0.3451f),
            PointF(0.3763f, 0.3696f),
            PointF(0.3899f, 0.3876f),
            PointF(0.4064f, 0.3958f),
            PointF(0.4237f, 0.3877f),
            PointF(0.4315f, 0.3626f),
            PointF(0.4390f, 0.3346f),
            PointF(0.4518f, 0.3120f),
            PointF(0.4657f, 0.2915f),
            PointF(0.4815f, 0.2761f),
            PointF(0.4993f, 0.2720f),
            PointF(0.5173f, 0.2714f),
            PointF(0.5349f, 0.2650f),
            PointF(0.5501f, 0.2493f),
            PointF(0.5654f, 0.2322f),
            PointF(0.5796f, 0.2126f),
            PointF(0.5931f, 0.1915f),
            PointF(0.6094f, 0.1826f),
            PointF(0.6216f, 0.2058f),
            PointF(0.6306f, 0.2336f),
            PointF(0.6399f, 0.2611f),
            PointF(0.6474f, 0.2903f),
            PointF(0.6578f, 0.3162f),
            PointF(0.6744f, 0.3284f),
            PointF(0.6923f, 0.3318f),
            PointF(0.7103f, 0.3327f),
            PointF(0.7284f, 0.3314f),
            PointF(0.7452f, 0.3340f),
            PointF(0.7589f, 0.3257f),
            PointF(0.7738f, 0.3078f),
            PointF(0.7893f, 0.3012f),
            PointF(0.7978f, 0.3012f),
            PointF(0.8063f, 0.2853f),
            PointF(0.8221f, 0.2950f),
            PointF(0.8378f, 0.3045f),
            PointF(0.8551f, 0.3071f),
            PointF(0.8728f, 0.3047f),
            PointF(0.8876f, 0.3106f),  // us rampasi
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
            PointF(0.1411f, 0.4298f),  // bunker yol agzi
            PointF(0.1588f, 0.4312f),
            PointF(0.1770f, 0.4308f),
            PointF(0.1926f, 0.4343f),
            PointF(0.2048f, 0.4107f),
            PointF(0.2143f, 0.3844f),
            PointF(0.2193f, 0.3543f),
            PointF(0.2262f, 0.3244f),
            PointF(0.2344f, 0.2955f),
            PointF(0.2480f, 0.2744f),
            PointF(0.2649f, 0.2622f),
            PointF(0.2818f, 0.2504f),
            PointF(0.2969f, 0.2323f),
            PointF(0.3110f, 0.2118f),
            PointF(0.3263f, 0.1943f),
            PointF(0.3430f, 0.1813f),
            PointF(0.3605f, 0.1723f),
            PointF(0.3786f, 0.1707f),
            PointF(0.3964f, 0.1772f),
            PointF(0.4133f, 0.1896f),
            PointF(0.4275f, 0.2093f),
            PointF(0.4379f, 0.2359f),
            PointF(0.4498f, 0.2602f),
            PointF(0.4668f, 0.2715f),
            PointF(0.4847f, 0.2750f),
            PointF(0.5024f, 0.2678f),
            PointF(0.5173f, 0.2494f),
            PointF(0.5298f, 0.2259f),
            PointF(0.5424f, 0.2023f),
            PointF(0.5537f, 0.1769f),
            PointF(0.5657f, 0.1527f),
            PointF(0.5816f, 0.1372f),
            PointF(0.5993f, 0.1300f),
            PointF(0.6174f, 0.1312f),
            PointF(0.6322f, 0.1491f),
            PointF(0.6418f, 0.1766f),
            PointF(0.6494f, 0.2060f),
            PointF(0.6536f, 0.2374f),
            PointF(0.6560f, 0.2695f),
            PointF(0.6649f, 0.2970f),
            PointF(0.6813f, 0.3106f),
            PointF(0.6988f, 0.3190f),
            PointF(0.7156f, 0.3241f),
            PointF(0.7268f, 0.3475f),
            PointF(0.7413f, 0.3670f),
            PointF(0.7499f, 0.3950f),
            PointF(0.7474f, 0.4257f),
            PointF(0.7529f, 0.4546f),
            PointF(0.7680f, 0.4715f),
            PointF(0.7848f, 0.4839f),
            PointF(0.8024f, 0.4926f),
            PointF(0.8187f, 0.5037f),
            PointF(0.8310f, 0.5213f),
            PointF(0.8481f, 0.5141f),
            PointF(0.8584f, 0.5197f),
            PointF(0.8696f, 0.5203f),
            PointF(0.8870f, 0.5258f),
            PointF(0.9043f, 0.5277f),  // us rampasi
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.1712f, normY = 0.6093f),  // Z1  A-kolu 190 / B-kolu 120 ref-px
            BuildSpot(id = 2, normX = 0.1812f, normY = 0.2352f),  // Z1  A-kolu 124 / B-kolu 260 ref-px
            BuildSpot(id = 3, normX = 0.2579f, normY = 0.1385f),  // Z2  A-kolu 126 / B-kolu 401 ref-px
            BuildSpot(id = 4, normX = 0.2613f, normY = 0.7908f),  // Z2  A-kolu 407 / B-kolu  99 ref-px
            BuildSpot(id = 5, normX = 0.2781f, normY = 0.3497f),  // Z1  A-kolu  95 / B-kolu 222 ref-px
            BuildSpot(id = 6, normX = 0.2882f, normY = 0.5840f),  // Z1  A-kolu 246 / B-kolu 104 ref-px
            BuildSpot(id = 7, normX = 0.3741f, normY = 0.7475f),  // Z2  A-kolu 487 / B-kolu  97 ref-px
            BuildSpot(id = 8, normX = 0.4689f, normY = 0.1121f),  // Z2  A-kolu 131 / B-kolu 615 ref-px
            BuildSpot(id = 9, normX = 0.4941f, normY = 0.8435f),  // Z3  A-kolu 621 / B-kolu 124 ref-px
            BuildSpot(id = 10, normX = 0.5969f, normY = 0.2343f),  // Z3  A-kolu 100 / B-kolu 457 ref-px
            BuildSpot(id = 11, normX = 0.6864f, normY = 0.5992f),  // Z4  A-kolu 207 / B-kolu 109 ref-px
            BuildSpot(id = 12, normX = 0.6901f, normY = 0.7930f),  // Z4  A-kolu 383 / B-kolu  89 ref-px
            BuildSpot(id = 13, normX = 0.6964f, normY = 0.4256f),  // Z5  A-kolu  98 / B-kolu 175 ref-px
            BuildSpot(id = 14, normX = 0.7091f, normY = 0.2271f),  // Z4  A-kolu 100 / B-kolu 359 ref-px
            BuildSpot(id = 15, normX = 0.7958f, normY = 0.3124f),  // Z4  A-kolu 119 / B-kolu 236 ref-px
            BuildSpot(id = 16, normX = 0.8099f, normY = 0.6102f),  // Z5  A-kolu 118 / B-kolu  86 ref-px
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
            PointF(0.1663f, 0.4592f),
            PointF(0.1837f, 0.4578f),
            PointF(0.2002f, 0.4493f),
            PointF(0.2141f, 0.4321f),
            PointF(0.2307f, 0.4188f),
            PointF(0.2470f, 0.4044f),
            PointF(0.2638f, 0.3928f),
            PointF(0.2797f, 0.3835f),
            PointF(0.2937f, 0.3654f),
            PointF(0.3091f, 0.3483f),
            PointF(0.3229f, 0.3274f),
            PointF(0.3363f, 0.3053f),
            PointF(0.3501f, 0.2845f),
            PointF(0.3638f, 0.2633f),
            PointF(0.3780f, 0.2431f),
            PointF(0.3943f, 0.2290f),
            PointF(0.4124f, 0.2257f),
            PointF(0.4282f, 0.2362f),
            PointF(0.4430f, 0.2503f),
            PointF(0.4502f, 0.2691f),
            PointF(0.4504f, 0.2990f),
            PointF(0.4535f, 0.3271f),
            PointF(0.4511f, 0.3592f),
            PointF(0.4441f, 0.3874f),
            PointF(0.4398f, 0.4186f),
            PointF(0.4332f, 0.4488f),
            PointF(0.4257f, 0.4783f),
            PointF(0.4182f, 0.5078f),
            PointF(0.4140f, 0.5391f),
            PointF(0.4149f, 0.5713f),
            PointF(0.4179f, 0.6033f),
            PointF(0.4249f, 0.6311f),
            PointF(0.4388f, 0.6510f),
            PointF(0.4548f, 0.6663f),
            PointF(0.4720f, 0.6768f),
            PointF(0.4898f, 0.6836f),
            PointF(0.5080f, 0.6856f),
            PointF(0.5261f, 0.6838f),
            PointF(0.5442f, 0.6794f),
            PointF(0.5620f, 0.6728f),
            PointF(0.5794f, 0.6632f),
            PointF(0.5957f, 0.6489f),
            PointF(0.6104f, 0.6298f),
            PointF(0.6233f, 0.6071f),
            PointF(0.6332f, 0.5807f),
            PointF(0.6389f, 0.5503f),
            PointF(0.6398f, 0.5179f),
            PointF(0.6392f, 0.4856f),
            PointF(0.6351f, 0.4547f),
            PointF(0.6354f, 0.4232f),
            PointF(0.6332f, 0.3911f),
            PointF(0.6374f, 0.3600f),
            PointF(0.6472f, 0.3342f),
            PointF(0.6599f, 0.3130f),
            PointF(0.6777f, 0.3090f),
            PointF(0.6956f, 0.3140f),
            PointF(0.7125f, 0.3260f),
            PointF(0.7284f, 0.3416f),
            PointF(0.7387f, 0.3682f),
            PointF(0.7475f, 0.3965f),
            PointF(0.7512f, 0.4265f),
            PointF(0.7666f, 0.4375f),
            PointF(0.7842f, 0.4385f),
            PointF(0.7973f, 0.4585f),
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
            PointF(0.1292f, 0.5234f),  // bunker yol agzi
            PointF(0.1432f, 0.5069f),
            PointF(0.1512f, 0.4799f),
            PointF(0.1689f, 0.4762f),
            PointF(0.1855f, 0.4637f),
            PointF(0.1912f, 0.4340f),
            PointF(0.1889f, 0.4063f),
            PointF(0.1955f, 0.3766f),
            PointF(0.2060f, 0.3507f),
            PointF(0.2182f, 0.3271f),
            PointF(0.2334f, 0.3104f),
            PointF(0.2511f, 0.3128f),
            PointF(0.2673f, 0.3268f),
            PointF(0.2810f, 0.3475f),
            PointF(0.2882f, 0.3763f),
            PointF(0.2881f, 0.4083f),
            PointF(0.2868f, 0.4403f),
            PointF(0.2877f, 0.4722f),
            PointF(0.2891f, 0.5042f),
            PointF(0.2917f, 0.5359f),
            PointF(0.2984f, 0.5655f),
            PointF(0.3100f, 0.5900f),
            PointF(0.3241f, 0.6097f),
            PointF(0.3409f, 0.6210f),
            PointF(0.3583f, 0.6145f),
            PointF(0.3737f, 0.5980f),
            PointF(0.3866f, 0.5757f),
            PointF(0.3930f, 0.5463f),
            PointF(0.3932f, 0.5142f),
            PointF(0.3935f, 0.4822f),
            PointF(0.3991f, 0.4519f),
            PointF(0.4076f, 0.4237f),
            PointF(0.4189f, 0.3989f),
            PointF(0.4349f, 0.3852f),
            PointF(0.4527f, 0.3885f),
            PointF(0.4697f, 0.3991f),
            PointF(0.4856f, 0.4140f),
            PointF(0.4947f, 0.4390f),
            PointF(0.5012f, 0.4664f),
            PointF(0.5178f, 0.4787f),
            PointF(0.5346f, 0.4900f),
            PointF(0.5525f, 0.4945f),
            PointF(0.5701f, 0.4946f),
            PointF(0.5864f, 0.4965f),
            PointF(0.6036f, 0.5000f),
            PointF(0.6213f, 0.4987f),
            PointF(0.6386f, 0.4900f),
            PointF(0.6541f, 0.4737f),
            PointF(0.6677f, 0.4529f),
            PointF(0.6795f, 0.4286f),
            PointF(0.6868f, 0.3995f),
            PointF(0.6912f, 0.3684f),
            PointF(0.6974f, 0.3384f),
            PointF(0.7092f, 0.3143f),
            PointF(0.7234f, 0.2947f),
            PointF(0.7400f, 0.2825f),
            PointF(0.7557f, 0.2949f),
            PointF(0.7658f, 0.3214f),
            PointF(0.7737f, 0.3502f),
            PointF(0.7798f, 0.3802f),
            PointF(0.7816f, 0.4121f),
            PointF(0.7833f, 0.4440f),
            PointF(0.7923f, 0.4706f),
            PointF(0.8096f, 0.4786f),
            PointF(0.8272f, 0.4843f),
            PointF(0.8445f, 0.4809f),  // us rampasi
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
            PointF(0.1566f, 0.4986f),
            PointF(0.1736f, 0.4958f),
            PointF(0.1912f, 0.4893f),
            PointF(0.2035f, 0.4674f),
            PointF(0.2023f, 0.4360f),
            PointF(0.2074f, 0.4050f),
            PointF(0.2161f, 0.3766f),
            PointF(0.2260f, 0.3495f),
            PointF(0.2390f, 0.3271f),
            PointF(0.2561f, 0.3169f),
            PointF(0.2742f, 0.3161f),
            PointF(0.2917f, 0.3241f),
            PointF(0.3050f, 0.3457f),
            PointF(0.3142f, 0.3736f),
            PointF(0.3229f, 0.4020f),
            PointF(0.3302f, 0.4316f),
            PointF(0.3370f, 0.4615f),
            PointF(0.3482f, 0.4866f),
            PointF(0.3641f, 0.5022f),
            PointF(0.3816f, 0.5107f),
            PointF(0.3996f, 0.5091f),
            PointF(0.4153f, 0.4932f),
            PointF(0.4281f, 0.4703f),
            PointF(0.4377f, 0.4430f),
            PointF(0.4416f, 0.4115f),
            PointF(0.4437f, 0.3794f),
            PointF(0.4484f, 0.3482f),
            PointF(0.4544f, 0.3177f),
            PointF(0.4625f, 0.2888f),
            PointF(0.4754f, 0.2664f),
            PointF(0.4919f, 0.2533f),
            PointF(0.5097f, 0.2470f),
            PointF(0.5278f, 0.2486f),
            PointF(0.5450f, 0.2587f),
            PointF(0.5606f, 0.2752f),
            PointF(0.5731f, 0.2986f),
            PointF(0.5800f, 0.3284f),
            PointF(0.5845f, 0.3597f),
            PointF(0.5895f, 0.3907f),
            PointF(0.5931f, 0.4224f),
            PointF(0.5980f, 0.4535f),
            PointF(0.6081f, 0.4802f),
            PointF(0.6217f, 0.5014f),
            PointF(0.6378f, 0.5163f),
            PointF(0.6557f, 0.5168f),
            PointF(0.6721f, 0.5034f),
            PointF(0.6864f, 0.4835f),
            PointF(0.6965f, 0.4570f),
            PointF(0.7011f, 0.4258f),
            PointF(0.7062f, 0.3948f),
            PointF(0.7157f, 0.3673f),
            PointF(0.7272f, 0.3422f),
            PointF(0.7415f, 0.3226f),
            PointF(0.7592f, 0.3181f),
            PointF(0.7768f, 0.3259f),
            PointF(0.7935f, 0.3389f),
            PointF(0.8084f, 0.3570f),
            PointF(0.8195f, 0.3818f),
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
            PointF(0.1435f, 0.4596f),  // bunker yol agzi
            PointF(0.1614f, 0.4566f),
            PointF(0.1790f, 0.4539f),
            PointF(0.1947f, 0.4442f),
            PointF(0.2063f, 0.4204f),
            PointF(0.2140f, 0.3914f),
            PointF(0.2222f, 0.3629f),
            PointF(0.2350f, 0.3416f),
            PointF(0.2494f, 0.3271f),
            PointF(0.2657f, 0.3362f),
            PointF(0.2832f, 0.3429f),
            PointF(0.2955f, 0.3658f),
            PointF(0.3033f, 0.3948f),
            PointF(0.3101f, 0.4245f),
            PointF(0.3162f, 0.4546f),
            PointF(0.3208f, 0.4857f),
            PointF(0.3268f, 0.5159f),
            PointF(0.3366f, 0.5428f),
            PointF(0.3486f, 0.5668f),
            PointF(0.3623f, 0.5876f),
            PointF(0.3780f, 0.6031f),
            PointF(0.3956f, 0.6107f),
            PointF(0.4132f, 0.6177f),
            PointF(0.4292f, 0.6321f),
            PointF(0.4419f, 0.6548f),
            PointF(0.4536f, 0.6792f),
            PointF(0.4670f, 0.7005f),
            PointF(0.4836f, 0.7092f),
            PointF(0.4944f, 0.7313f),
            PointF(0.5114f, 0.7420f),
            PointF(0.5293f, 0.7449f),
            PointF(0.5473f, 0.7417f),
            PointF(0.5644f, 0.7319f),
            PointF(0.5786f, 0.7123f),
            PointF(0.5892f, 0.6864f),
            PointF(0.5981f, 0.6585f),
            PointF(0.6063f, 0.6299f),
            PointF(0.6126f, 0.5998f),
            PointF(0.6188f, 0.5697f),
            PointF(0.6270f, 0.5411f),
            PointF(0.6407f, 0.5234f),
            PointF(0.6497f, 0.4979f),
            PointF(0.6554f, 0.4723f),
            PointF(0.6723f, 0.4620f),
            PointF(0.6903f, 0.4600f),
            PointF(0.7083f, 0.4572f),
            PointF(0.7257f, 0.4488f),
            PointF(0.7428f, 0.4385f),
            PointF(0.7582f, 0.4222f),
            PointF(0.7677f, 0.3953f),
            PointF(0.7717f, 0.3641f),
            PointF(0.7749f, 0.3325f),
            PointF(0.7796f, 0.3015f),
            PointF(0.7837f, 0.2702f),
            PointF(0.7900f, 0.2403f),
            PointF(0.8027f, 0.2181f),
            PointF(0.8192f, 0.2050f),
            PointF(0.8368f, 0.1990f),
            PointF(0.8533f, 0.2091f),
            PointF(0.8616f, 0.2373f),
            PointF(0.8668f, 0.2681f),
            PointF(0.8722f, 0.2987f),
            PointF(0.8772f, 0.3296f),
            PointF(0.8813f, 0.3605f),
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
            PointF(0.1479f, 0.4311f),
            PointF(0.1655f, 0.4329f),
            PointF(0.1830f, 0.4363f),
            PointF(0.2007f, 0.4328f),
            PointF(0.2184f, 0.4277f),
            PointF(0.2361f, 0.4225f),
            PointF(0.2540f, 0.4210f),
            PointF(0.2719f, 0.4186f),
            PointF(0.2879f, 0.4052f),
            PointF(0.2982f, 0.3793f),
            PointF(0.3058f, 0.3504f),
            PointF(0.3134f, 0.3215f),
            PointF(0.3191f, 0.2912f),
            PointF(0.3271f, 0.2645f),
            PointF(0.3399f, 0.2423f),
            PointF(0.3574f, 0.2394f),
            PointF(0.3753f, 0.2395f),
            PointF(0.3931f, 0.2443f),
            PointF(0.4091f, 0.2553f),
            PointF(0.4172f, 0.2807f),
            PointF(0.4330f, 0.2865f),
            PointF(0.4423f, 0.3138f),
            PointF(0.4535f, 0.3386f),
            PointF(0.4682f, 0.3568f),
            PointF(0.4827f, 0.3535f),
            PointF(0.4940f, 0.3743f),
            PointF(0.5105f, 0.3687f),
            PointF(0.5253f, 0.3508f),
            PointF(0.5398f, 0.3321f),
            PointF(0.5547f, 0.3146f),
            PointF(0.5698f, 0.2972f),
            PointF(0.5864f, 0.2857f),
            PointF(0.6043f, 0.2854f),
            PointF(0.6218f, 0.2925f),
            PointF(0.6378f, 0.3066f),
            PointF(0.6509f, 0.3284f),
            PointF(0.6607f, 0.3549f),
            PointF(0.6655f, 0.3854f),
            PointF(0.6627f, 0.4168f),
            PointF(0.6570f, 0.4471f),
            PointF(0.6514f, 0.4774f),
            PointF(0.6442f, 0.5066f),
            PointF(0.6384f, 0.5368f),
            PointF(0.6407f, 0.5680f),
            PointF(0.6492f, 0.5961f),
            PointF(0.6626f, 0.6166f),
            PointF(0.6801f, 0.6133f),
            PointF(0.6962f, 0.5991f),
            PointF(0.7108f, 0.5806f),
            PointF(0.7213f, 0.5548f),
            PointF(0.7369f, 0.5461f),
            PointF(0.7372f, 0.5172f),
            PointF(0.7476f, 0.5032f),
            PointF(0.7647f, 0.4937f),
            PointF(0.7823f, 0.4868f),
            PointF(0.7998f, 0.4796f),
            PointF(0.8174f, 0.4738f),
            PointF(0.8349f, 0.4681f),  // us rampasi
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
            PointF(0.1292f, 0.4213f),  // bunker yol agzi
            PointF(0.1413f, 0.4438f),
            PointF(0.1588f, 0.4442f),
            PointF(0.1765f, 0.4375f),
            PointF(0.1938f, 0.4385f),
            PointF(0.2115f, 0.4454f),
            PointF(0.2285f, 0.4562f),
            PointF(0.2429f, 0.4752f),
            PointF(0.2526f, 0.4984f),
            PointF(0.2609f, 0.5253f),
            PointF(0.2678f, 0.5550f),
            PointF(0.2725f, 0.5860f),
            PointF(0.2751f, 0.6178f),
            PointF(0.2720f, 0.6452f),
            PointF(0.2693f, 0.6764f),
            PointF(0.2711f, 0.7075f),
            PointF(0.2779f, 0.7336f),
            PointF(0.2847f, 0.7628f),
            PointF(0.2985f, 0.7835f),
            PointF(0.3146f, 0.7980f),
            PointF(0.3318f, 0.8079f),
            PointF(0.3496f, 0.8129f),
            PointF(0.3674f, 0.8124f),
            PointF(0.3851f, 0.8062f),
            PointF(0.4010f, 0.7912f),
            PointF(0.4135f, 0.7681f),
            PointF(0.4280f, 0.7491f),
            PointF(0.4454f, 0.7412f),
            PointF(0.4603f, 0.7458f),
            PointF(0.4784f, 0.7464f),
            PointF(0.4949f, 0.7561f),
            PointF(0.5023f, 0.7658f),
            PointF(0.5097f, 0.7754f),
            PointF(0.5239f, 0.7954f),
            PointF(0.5398f, 0.8097f),
            PointF(0.5534f, 0.8239f),
            PointF(0.5644f, 0.7999f),
            PointF(0.5797f, 0.7870f),
            PointF(0.5876f, 0.7606f),
            PointF(0.6011f, 0.7398f),
            PointF(0.6035f, 0.7087f),
            PointF(0.6028f, 0.6771f),
            PointF(0.6017f, 0.6451f),
            PointF(0.5993f, 0.6132f),
            PointF(0.6010f, 0.5814f),
            PointF(0.6060f, 0.5523f),
            PointF(0.6179f, 0.5289f),
            PointF(0.6299f, 0.5110f),
            PointF(0.6458f, 0.4997f),
            PointF(0.6571f, 0.4762f),
            PointF(0.6735f, 0.4668f),
            PointF(0.6855f, 0.4431f),
            PointF(0.6914f, 0.4128f),
            PointF(0.6948f, 0.3812f),
            PointF(0.6991f, 0.3500f),
            PointF(0.7036f, 0.3189f),
            PointF(0.7082f, 0.2878f),
            PointF(0.7170f, 0.2601f),
            PointF(0.7328f, 0.2488f),
            PointF(0.7506f, 0.2457f),
            PointF(0.7646f, 0.2623f),
            PointF(0.7778f, 0.2832f),
            PointF(0.7836f, 0.3136f),
            PointF(0.7886f, 0.3445f),
            PointF(0.7986f, 0.3706f),
            PointF(0.8106f, 0.3926f),
            PointF(0.8242f, 0.4085f),
            PointF(0.8421f, 0.4085f),  // us rampasi
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
            PointF(0.1502f, 0.4684f),
            PointF(0.1677f, 0.4696f),
            PointF(0.1831f, 0.4854f),
            PointF(0.1921f, 0.5134f),
            PointF(0.1978f, 0.5441f),
            PointF(0.2013f, 0.5758f),
            PointF(0.2023f, 0.6081f),
            PointF(0.2034f, 0.6404f),
            PointF(0.2037f, 0.6728f),
            PointF(0.2055f, 0.7050f),
            PointF(0.2102f, 0.7362f),
            PointF(0.2172f, 0.7661f),
            PointF(0.2287f, 0.7907f),
            PointF(0.2451f, 0.8045f),
            PointF(0.2623f, 0.8153f),
            PointF(0.2786f, 0.8295f),
            PointF(0.2946f, 0.8400f),
            PointF(0.3086f, 0.8582f),
            PointF(0.3176f, 0.8829f),
            PointF(0.3311f, 0.9033f),
            PointF(0.3482f, 0.9116f),
            PointF(0.3660f, 0.9180f),
            PointF(0.3841f, 0.9192f),
            PointF(0.4021f, 0.9144f),
            PointF(0.4195f, 0.9050f),
            PointF(0.4369f, 0.8959f),
            PointF(0.4546f, 0.8877f),
            PointF(0.4717f, 0.8771f),
            PointF(0.4879f, 0.8623f),
            PointF(0.5029f, 0.8440f),
            PointF(0.5149f, 0.8200f),
            PointF(0.5217f, 0.7900f),
            PointF(0.5287f, 0.7603f),
            PointF(0.5404f, 0.7355f),
            PointF(0.5522f, 0.7118f),
            PointF(0.5688f, 0.7046f),
            PointF(0.5867f, 0.7085f),
            PointF(0.6042f, 0.7173f),
            PointF(0.6223f, 0.7202f),
            PointF(0.6405f, 0.7200f),
            PointF(0.6583f, 0.7143f),
            PointF(0.6749f, 0.7010f),
            PointF(0.6901f, 0.6832f),
            PointF(0.7038f, 0.6620f),
            PointF(0.7154f, 0.6372f),
            PointF(0.7287f, 0.6153f),
            PointF(0.7448f, 0.6001f),
            PointF(0.7620f, 0.5898f),
            PointF(0.7800f, 0.5863f),
            PointF(0.7981f, 0.5905f),
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
            PointF(0.1388f, 0.4340f),  // bunker yol agzi
            PointF(0.1517f, 0.4155f),
            PointF(0.1692f, 0.4155f),
            PointF(0.1874f, 0.4160f),
            PointF(0.1980f, 0.3909f),
            PointF(0.2035f, 0.3601f),
            PointF(0.2091f, 0.3292f),
            PointF(0.2182f, 0.3013f),
            PointF(0.2205f, 0.2723f),
            PointF(0.2295f, 0.2478f),
            PointF(0.2468f, 0.2462f),
            PointF(0.2648f, 0.2455f),
            PointF(0.2807f, 0.2585f),
            PointF(0.2978f, 0.2692f),
            PointF(0.3157f, 0.2755f),
            PointF(0.3337f, 0.2805f),
            PointF(0.3518f, 0.2799f),
            PointF(0.3688f, 0.2688f),
            PointF(0.3840f, 0.2509f),
            PointF(0.3988f, 0.2321f),
            PointF(0.4138f, 0.2138f),
            PointF(0.4284f, 0.1945f),
            PointF(0.4442f, 0.1786f),
            PointF(0.4619f, 0.1713f),
            PointF(0.4801f, 0.1704f),
            PointF(0.4979f, 0.1769f),
            PointF(0.5094f, 0.1990f),
            PointF(0.5203f, 0.2234f),
            PointF(0.5299f, 0.2509f),
            PointF(0.5448f, 0.2688f),
            PointF(0.5622f, 0.2781f),
            PointF(0.5803f, 0.2816f),
            PointF(0.5983f, 0.2787f),
            PointF(0.6153f, 0.2670f),
            PointF(0.6320f, 0.2541f),
            PointF(0.6496f, 0.2465f),
            PointF(0.6677f, 0.2431f),
            PointF(0.6859f, 0.2422f),
            PointF(0.7041f, 0.2428f),
            PointF(0.7224f, 0.2429f),
            PointF(0.7404f, 0.2464f),
            PointF(0.7565f, 0.2606f),
            PointF(0.7680f, 0.2856f),
            PointF(0.7756f, 0.3149f),
            PointF(0.7785f, 0.3467f),
            PointF(0.7731f, 0.3774f),
            PointF(0.7643f, 0.4057f),
            PointF(0.7564f, 0.4348f),
            PointF(0.7491f, 0.4645f),
            PointF(0.7424f, 0.4946f),
            PointF(0.7391f, 0.5263f),
            PointF(0.7425f, 0.5580f),
            PointF(0.7463f, 0.5888f),
            PointF(0.7441f, 0.6196f),
            PointF(0.7574f, 0.6381f),
            PointF(0.7720f, 0.6548f),
            PointF(0.7816f, 0.6813f),
            PointF(0.7971f, 0.6982f),
            PointF(0.8142f, 0.7093f),
            PointF(0.8322f, 0.7133f),
            PointF(0.8431f, 0.6973f),
            PointF(0.8612f, 0.6950f),
            PointF(0.8712f, 0.6821f),
            PointF(0.8804f, 0.6638f),  // us rampasi
        ),
        2 to listOf(
            PointF(0.1435f, 0.4553f),  // bunker yol agzi
            PointF(0.1563f, 0.4359f),
            PointF(0.1741f, 0.4382f),
            PointF(0.1917f, 0.4349f),
            PointF(0.2055f, 0.4148f),
            PointF(0.2161f, 0.3891f),
            PointF(0.2288f, 0.3669f),
            PointF(0.2459f, 0.3579f),
            PointF(0.2638f, 0.3574f),
            PointF(0.2815f, 0.3628f),
            PointF(0.2974f, 0.3770f),
            PointF(0.3109f, 0.3979f),
            PointF(0.3253f, 0.4168f),
            PointF(0.3423f, 0.4264f),
            PointF(0.3601f, 0.4305f),
            PointF(0.3779f, 0.4275f),
            PointF(0.3936f, 0.4127f),
            PointF(0.4057f, 0.3893f),
            PointF(0.4152f, 0.3622f),
            PointF(0.4213f, 0.3324f),
            PointF(0.4226f, 0.3006f),
            PointF(0.4237f, 0.2688f),
            PointF(0.4288f, 0.2382f),
            PointF(0.4357f, 0.2088f),
            PointF(0.4456f, 0.1824f),
            PointF(0.4616f, 0.1701f),
            PointF(0.4792f, 0.1749f),
            PointF(0.4961f, 0.1856f),
            PointF(0.5122f, 0.1995f),
            PointF(0.5259f, 0.2201f),
            PointF(0.5396f, 0.2407f),
            PointF(0.5562f, 0.2513f),
            PointF(0.5741f, 0.2483f),
            PointF(0.5916f, 0.2421f),
            PointF(0.6095f, 0.2407f),
            PointF(0.6274f, 0.2418f),
            PointF(0.6451f, 0.2465f),
            PointF(0.6607f, 0.2617f),
            PointF(0.6704f, 0.2883f),
            PointF(0.6767f, 0.3182f),
            PointF(0.6816f, 0.3489f),
            PointF(0.6839f, 0.3805f),
            PointF(0.6850f, 0.4123f),
            PointF(0.6871f, 0.4440f),
            PointF(0.6904f, 0.4753f),
            PointF(0.6945f, 0.5064f),
            PointF(0.6994f, 0.5371f),
            PointF(0.7047f, 0.5675f),
            PointF(0.7110f, 0.5974f),
            PointF(0.7196f, 0.6254f),
            PointF(0.7308f, 0.6503f),
            PointF(0.7362f, 0.6805f),
            PointF(0.7510f, 0.6964f),
            PointF(0.7669f, 0.7071f),
            PointF(0.7840f, 0.7145f),
            PointF(0.8016f, 0.7174f),
            PointF(0.8166f, 0.7005f),
            PointF(0.8291f, 0.6776f),
            PointF(0.8420f, 0.6555f),
            PointF(0.8558f, 0.6352f),
            PointF(0.8687f, 0.6134f),
            PointF(0.8828f, 0.5957f),  // us rampasi
        ),
        3 to listOf(
            PointF(0.1316f, 0.5021f),  // bunker yol agzi
            PointF(0.1484f, 0.5035f),
            PointF(0.1584f, 0.4766f),
            PointF(0.1693f, 0.4592f),
            PointF(0.1865f, 0.4586f),
            PointF(0.2034f, 0.4566f),
            PointF(0.2164f, 0.4368f),
            PointF(0.2253f, 0.4104f),
            PointF(0.2371f, 0.3859f),
            PointF(0.2475f, 0.3595f),
            PointF(0.2550f, 0.3302f),
            PointF(0.2623f, 0.3007f),
            PointF(0.2727f, 0.2744f),
            PointF(0.2888f, 0.2600f),
            PointF(0.3065f, 0.2538f),
            PointF(0.3246f, 0.2534f),
            PointF(0.3418f, 0.2628f),
            PointF(0.3477f, 0.2921f),
            PointF(0.3556f, 0.3181f),
            PointF(0.3569f, 0.3484f),
            PointF(0.3537f, 0.3799f),
            PointF(0.3499f, 0.4110f),
            PointF(0.3442f, 0.4405f),
            PointF(0.3427f, 0.4727f),
            PointF(0.3372f, 0.5032f),
            PointF(0.3281f, 0.5301f),
            PointF(0.3191f, 0.5581f),
            PointF(0.3135f, 0.5885f),
            PointF(0.3142f, 0.6207f),
            PointF(0.3184f, 0.6521f),
            PointF(0.3256f, 0.6816f),
            PointF(0.3368f, 0.7067f),
            PointF(0.3524f, 0.7229f),
            PointF(0.3698f, 0.7321f),
            PointF(0.3877f, 0.7362f),
            PointF(0.4059f, 0.7352f),
            PointF(0.4234f, 0.7275f),
            PointF(0.4401f, 0.7150f),
            PointF(0.4558f, 0.6988f),
            PointF(0.4705f, 0.6798f),
            PointF(0.4839f, 0.6582f),
            PointF(0.4959f, 0.6339f),
            PointF(0.5053f, 0.6064f),
            PointF(0.5094f, 0.5753f),
            PointF(0.5132f, 0.5442f),
            PointF(0.5063f, 0.5153f),
            PointF(0.5021f, 0.4847f),
            PointF(0.5016f, 0.4567f),
            PointF(0.5132f, 0.4318f),
            PointF(0.5173f, 0.4077f),
            PointF(0.5223f, 0.3902f),
            PointF(0.5244f, 0.3752f),
            PointF(0.5267f, 0.3599f),
            PointF(0.5301f, 0.3438f),
            PointF(0.5423f, 0.3308f),
            PointF(0.5416f, 0.2991f),
            PointF(0.5559f, 0.2811f),
            PointF(0.5658f, 0.2541f),
            PointF(0.5750f, 0.2263f),
            PointF(0.5849f, 0.1993f),
            PointF(0.5987f, 0.1795f),
            PointF(0.6126f, 0.1976f),
            PointF(0.6225f, 0.2241f),
            PointF(0.6308f, 0.2526f),
            PointF(0.6427f, 0.2768f),
            PointF(0.6542f, 0.3009f),
            PointF(0.6698f, 0.3161f),
            PointF(0.6865f, 0.3285f),
            PointF(0.7043f, 0.3331f),
            PointF(0.7222f, 0.3282f),
            PointF(0.7397f, 0.3304f),
            PointF(0.7533f, 0.3183f),
            PointF(0.7675f, 0.3036f),
            PointF(0.7797f, 0.3104f),
            PointF(0.7875f, 0.3020f),
            PointF(0.7970f, 0.3015f),
            PointF(0.8065f, 0.2852f),
            PointF(0.8202f, 0.2933f),
            PointF(0.8359f, 0.3042f),
            PointF(0.8540f, 0.3069f),
            PointF(0.8716f, 0.3050f),
            PointF(0.8876f, 0.3106f),  // us rampasi
        ),
        4 to listOf(
            PointF(0.1388f, 0.4638f),  // bunker yol agzi
            PointF(0.1520f, 0.4853f),
            PointF(0.1693f, 0.4860f),
            PointF(0.1865f, 0.4866f),
            PointF(0.2027f, 0.5010f),
            PointF(0.2178f, 0.5184f),
            PointF(0.2297f, 0.5423f),
            PointF(0.2226f, 0.5683f),
            PointF(0.2205f, 0.5981f),
            PointF(0.2281f, 0.6240f),
            PointF(0.2403f, 0.6438f),
            PointF(0.2523f, 0.6659f),
            PointF(0.2686f, 0.6790f),
            PointF(0.2827f, 0.6988f),
            PointF(0.2970f, 0.7180f),
            PointF(0.3085f, 0.7420f),
            PointF(0.3108f, 0.7633f),
            PointF(0.3167f, 0.7919f),
            PointF(0.3298f, 0.8069f),
            PointF(0.3411f, 0.8316f),
            PointF(0.3573f, 0.8453f),
            PointF(0.3750f, 0.8517f),
            PointF(0.3927f, 0.8475f),
            PointF(0.4068f, 0.8277f),
            PointF(0.4175f, 0.8021f),
            PointF(0.4270f, 0.7748f),
            PointF(0.4328f, 0.7444f),
            PointF(0.4376f, 0.7135f),
            PointF(0.4482f, 0.6884f),
            PointF(0.4651f, 0.6773f),
            PointF(0.4830f, 0.6754f),
            PointF(0.5000f, 0.6850f),
            PointF(0.5124f, 0.7081f),
            PointF(0.5233f, 0.7338f),
            PointF(0.5358f, 0.7570f),
            PointF(0.5486f, 0.7796f),
            PointF(0.5627f, 0.7996f),
            PointF(0.5797f, 0.8097f),
            PointF(0.5977f, 0.8097f),
            PointF(0.6153f, 0.8025f),
            PointF(0.6306f, 0.7864f),
            PointF(0.6414f, 0.7606f),
            PointF(0.6520f, 0.7346f),
            PointF(0.6669f, 0.7172f),
            PointF(0.6844f, 0.7093f),
            PointF(0.7019f, 0.7017f),
            PointF(0.7181f, 0.6876f),
            PointF(0.7337f, 0.6723f),
            PointF(0.7464f, 0.6513f),
            PointF(0.7512f, 0.6205f),
            PointF(0.7514f, 0.5884f),
            PointF(0.7533f, 0.5566f),
            PointF(0.7671f, 0.5381f),
            PointF(0.7825f, 0.5240f),
            PointF(0.7985f, 0.5103f),
            PointF(0.8162f, 0.5106f),
            PointF(0.8312f, 0.5218f),
            PointF(0.8420f, 0.5189f),
            PointF(0.8528f, 0.5160f),
            PointF(0.8690f, 0.5190f),
            PointF(0.8755f, 0.5260f),
            PointF(0.8870f, 0.5253f),
            PointF(0.9043f, 0.5277f),  // us rampasi
        ),
        11 to listOf(
            PointF(0.1292f, 0.4340f),  // bunker yol agzi
            PointF(0.1382f, 0.4064f),
            PointF(0.1549f, 0.4031f),
            PointF(0.1724f, 0.3984f),
            PointF(0.1861f, 0.3792f),
            PointF(0.1930f, 0.3496f),
            PointF(0.1980f, 0.3186f),
            PointF(0.2061f, 0.2900f),
            PointF(0.2189f, 0.2672f),
            PointF(0.2336f, 0.2484f),
            PointF(0.2502f, 0.2355f),
            PointF(0.2682f, 0.2328f),
            PointF(0.2860f, 0.2384f),
            PointF(0.3028f, 0.2504f),
            PointF(0.3169f, 0.2703f),
            PointF(0.3255f, 0.2985f),
            PointF(0.3303f, 0.3295f),
            PointF(0.3324f, 0.3615f),
            PointF(0.3303f, 0.3934f),
            PointF(0.3243f, 0.4238f),
            PointF(0.3184f, 0.4543f),
            PointF(0.3156f, 0.4861f),
            PointF(0.3143f, 0.5182f),
            PointF(0.3149f, 0.5504f),
            PointF(0.3203f, 0.5810f),
            PointF(0.3319f, 0.6056f),
            PointF(0.3465f, 0.6247f),
            PointF(0.3632f, 0.6369f),
            PointF(0.3811f, 0.6365f),
            PointF(0.3982f, 0.6260f),
            PointF(0.4139f, 0.6099f),
            PointF(0.4276f, 0.5888f),
            PointF(0.4374f, 0.5618f),
            PointF(0.4442f, 0.5320f),
            PointF(0.4497f, 0.5012f),
            PointF(0.4532f, 0.4696f),
            PointF(0.4546f, 0.4375f),
            PointF(0.4566f, 0.4054f),
            PointF(0.4612f, 0.3743f),
            PointF(0.4672f, 0.3439f),
            PointF(0.4750f, 0.3148f),
            PointF(0.4859f, 0.2892f),
            PointF(0.5003f, 0.2697f),
            PointF(0.5165f, 0.2552f),
            PointF(0.5334f, 0.2440f),
            PointF(0.5509f, 0.2356f),
            PointF(0.5689f, 0.2313f),
            PointF(0.5870f, 0.2293f),
            PointF(0.6051f, 0.2273f),
            PointF(0.6231f, 0.2240f),
            PointF(0.6412f, 0.2218f),
            PointF(0.6592f, 0.2244f),
            PointF(0.6765f, 0.2339f),
            PointF(0.6928f, 0.2479f),
            PointF(0.7075f, 0.2667f),
            PointF(0.7188f, 0.2918f),
            PointF(0.7249f, 0.3220f),
            PointF(0.7280f, 0.3538f),
            PointF(0.7296f, 0.3859f),
            PointF(0.7283f, 0.4180f),
            PointF(0.7245f, 0.4495f),
            PointF(0.7222f, 0.4814f),
            PointF(0.7286f, 0.5108f),
            PointF(0.7382f, 0.5372f),
            PointF(0.7500f, 0.5602f),
            PointF(0.7675f, 0.5673f),
            PointF(0.7838f, 0.5739f),
            PointF(0.8004f, 0.5852f),
            PointF(0.8158f, 0.5957f),  // us rampasi
        ),
    )

    val ALL_MAPS: List<LevelData> = listOf(
        MAP_01, MAP_02, MAP_03, MAP_04, MAP_05, MAP_06, MAP_07, MAP_08, MAP_09, MAP_10, MAP_11
    )
}
