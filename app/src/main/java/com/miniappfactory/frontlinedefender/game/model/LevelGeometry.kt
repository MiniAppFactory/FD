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
 * Her rotanin ilk noktasi x=-0.05 (ekran disi spawn), son noktasi x=1.05
 * (ekran disi us) olacak sekilde YATAY uzatilmistir.
 */
object LevelGeometry {

    // ---- m00  kaynak: asset-pack/maps/level_01_battlefield_map.png
    //      rota=2  pad=10  yol=1823 ref-px
    val MAP_01 = LevelData(
        levelId = 1,
        name = "Cayir Gecidi / Meadow Pass",
        description = "Yesil cayirda ciftlenmis toprak yol; yol spawn'da catallanip usse birlesir.",
        waypoints = listOf(
            PointF(-0.0500f, 0.4638f),  // spawn (ekran disi)
            PointF(0.1388f, 0.4638f),
            PointF(0.1818f, 0.4893f),
            PointF(0.2220f, 0.5287f),
            PointF(0.2419f, 0.6034f),
            PointF(0.2718f, 0.6673f),
            PointF(0.3159f, 0.6953f),
            PointF(0.3624f, 0.6858f),
            PointF(0.4025f, 0.6418f),
            PointF(0.4381f, 0.5865f),
            PointF(0.4717f, 0.5269f),
            PointF(0.5107f, 0.4821f),
            PointF(0.5579f, 0.4861f),
            PointF(0.5855f, 0.5512f),
            PointF(0.5910f, 0.6333f),
            PointF(0.6191f, 0.7009f),
            PointF(0.6640f, 0.7160f),
            PointF(0.7079f, 0.6856f),
            PointF(0.7537f, 0.6717f),
            PointF(0.7999f, 0.6897f),
            PointF(0.8439f, 0.7083f),
            PointF(0.8804f, 0.6638f),
            PointF(1.0500f, 0.6638f),  // us (ekran disi)
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2772f, normY = 0.3777f),  // Z1  yola 124 ref-px
            BuildSpot(id = 2, normX = 0.3146f, normY = 0.5760f),  // Z1  yola 125 ref-px
            BuildSpot(id = 3, normX = 0.3322f, normY = 0.1674f),  // Z2  yola 121 ref-px
            BuildSpot(id = 4, normX = 0.4312f, normY = 0.7528f),  // Z2  yola 127 ref-px
            BuildSpot(id = 5, normX = 0.4653f, normY = 0.3024f),  // Z3  yola 128 ref-px
            BuildSpot(id = 6, normX = 0.5243f, normY = 0.6010f),  // Z4  yola 119 ref-px
            BuildSpot(id = 7, normX = 0.5787f, normY = 0.1679f),  // Z3  yola 124 ref-px
            BuildSpot(id = 8, normX = 0.6654f, normY = 0.5809f),  // Z5  yola 133 ref-px
            BuildSpot(id = 9, normX = 0.6932f, normY = 0.3508f),  // Z3  yola 117 ref-px
            BuildSpot(id = 10, normX = 0.7641f, normY = 0.8011f),  // Z5  yola 133 ref-px
        )
    )

    // ---- m01  kaynak: copied items/map (1).png
    //      rota=2  pad=13  yol=1697 ref-px
    val MAP_02 = LevelData(
        levelId = 2,
        name = "Selale Ormani / Waterfall Woods",
        description = "Orman icinde selale ve gol; yol iki kola ayrilip usse birlesir.",
        waypoints = listOf(
            PointF(-0.0500f, 0.5021f),  // spawn (ekran disi)
            PointF(0.1435f, 0.5021f),
            PointF(0.1865f, 0.5151f),
            PointF(0.2105f, 0.5780f),
            PointF(0.2201f, 0.6538f),
            PointF(0.2487f, 0.7063f),
            PointF(0.2917f, 0.7241f),
            PointF(0.3348f, 0.7175f),
            PointF(0.3767f, 0.7379f),
            PointF(0.4189f, 0.7531f),
            PointF(0.4581f, 0.7214f),
            PointF(0.4968f, 0.6853f),
            PointF(0.5387f, 0.6683f),
            PointF(0.5780f, 0.6978f),
            PointF(0.6096f, 0.7519f),
            PointF(0.6522f, 0.7671f),
            PointF(0.6944f, 0.7509f),
            PointF(0.7342f, 0.7260f),
            PointF(0.7779f, 0.7234f),
            PointF(0.8195f, 0.7007f),
            PointF(0.8472f, 0.6404f),
            PointF(0.8828f, 0.5957f),
            PointF(1.0500f, 0.5957f),  // us (ekran disi)
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2104f, normY = 0.8193f),  // Z1  yola 141 ref-px
            BuildSpot(id = 2, normX = 0.2726f, normY = 0.5081f),  // Z1  yola 136 ref-px
            BuildSpot(id = 3, normX = 0.2874f, normY = 0.2212f),  // Z1  yola 148 ref-px
            BuildSpot(id = 4, normX = 0.3599f, normY = 0.2952f),  // Z2  yola 119 ref-px
            BuildSpot(id = 5, normX = 0.3953f, normY = 0.6314f),  // Z2  yola 117 ref-px
            BuildSpot(id = 6, normX = 0.5226f, normY = 0.7688f),  // Z3  yola 101 ref-px
            BuildSpot(id = 7, normX = 0.5569f, normY = 0.1302f),  // Z3  yola 118 ref-px
            BuildSpot(id = 8, normX = 0.6225f, normY = 0.3458f),  // Z4  yola 109 ref-px
            BuildSpot(id = 9, normX = 0.6559f, normY = 0.6318f),  // Z4  yola 115 ref-px
            BuildSpot(id = 10, normX = 0.7237f, normY = 0.1687f),  // Z4  yola 154 ref-px
            BuildSpot(id = 11, normX = 0.7456f, normY = 0.8241f),  // Z5  yola 104 ref-px
            BuildSpot(id = 12, normX = 0.7470f, normY = 0.4622f),  // Z4  yola 116 ref-px
            BuildSpot(id = 13, normX = 0.8716f, normY = 0.7690f),  // Z5  yola 121 ref-px
        )
    )

    // ---- m02  kaynak: copied items/map (2).png
    //      rota=2  pad=12  yol=1901 ref-px
    val MAP_03 = LevelData(
        levelId = 3,
        name = "Karanlik Bogaz / Dark Ravine",
        description = "Kayalik karanlik bogaz; genis toprak zemin, komsu seritler sanatta birlesik.",
        waypoints = listOf(
            PointF(-0.0500f, 0.5021f),  // spawn (ekran disi)
            PointF(0.1316f, 0.5021f),
            PointF(0.1727f, 0.4605f),
            PointF(0.2165f, 0.4314f),
            PointF(0.2438f, 0.3635f),
            PointF(0.2674f, 0.2866f),
            PointF(0.3102f, 0.2505f),
            PointF(0.3533f, 0.2816f),
            PointF(0.3710f, 0.3622f),
            PointF(0.4150f, 0.3872f),
            PointF(0.4415f, 0.3297f),
            PointF(0.4797f, 0.2773f),
            PointF(0.5278f, 0.2680f),
            PointF(0.5700f, 0.2271f),
            PointF(0.6067f, 0.1811f),
            PointF(0.6356f, 0.2480f),
            PointF(0.6593f, 0.3181f),
            PointF(0.7077f, 0.3326f),
            PointF(0.7516f, 0.3247f),
            PointF(0.7933f, 0.3019f),
            PointF(0.8398f, 0.3021f),
            PointF(0.8876f, 0.3106f),
            PointF(1.0500f, 0.3106f),  // us (ekran disi)
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2868f, normY = 0.4342f),  // Z1  yola 101 ref-px
            BuildSpot(id = 2, normX = 0.3039f, normY = 0.7635f),  // Z3  yola 86 ref-px
            BuildSpot(id = 3, normX = 0.3049f, normY = 0.1542f),  // Z2  yola 104 ref-px
            BuildSpot(id = 4, normX = 0.3574f, normY = 0.6175f),  // Z2  yola 88 ref-px
            BuildSpot(id = 5, normX = 0.4697f, normY = 0.5378f),  // Z3  yola 88 ref-px
            BuildSpot(id = 6, normX = 0.4862f, normY = 0.1721f),  // Z3  yola 111 ref-px
            BuildSpot(id = 7, normX = 0.4944f, normY = 0.3592f),  // Z4  yola 59 ref-px
            BuildSpot(id = 8, normX = 0.5509f, normY = 0.6648f),  // Z3  yola 111 ref-px
            BuildSpot(id = 9, normX = 0.6562f, normY = 0.4229f),  // Z4  yola 112 ref-px
            BuildSpot(id = 10, normX = 0.6923f, normY = 0.6347f),  // Z5  yola 320 ref-px
            BuildSpot(id = 11, normX = 0.6925f, normY = 0.2218f),  // Z4  yola 113 ref-px
            BuildSpot(id = 12, normX = 0.7994f, normY = 0.3265f),  // Z5  yola 28 ref-px
        )
    )

    // ---- m03  kaynak: copied items/map (3).png
    //      rota=2  pad=16  yol=1957 ref-px
    val MAP_04 = LevelData(
        levelId = 4,
        name = "Gol Kusagi / Lakeside Ring",
        description = "Golun etrafini saran halka yol; iki ahsap kopru golu ortadan keser.",
        waypoints = listOf(
            PointF(-0.0500f, 0.4298f),  // spawn (ekran disi)
            PointF(0.1411f, 0.4298f),
            PointF(0.1910f, 0.4360f),
            PointF(0.2166f, 0.3637f),
            PointF(0.2392f, 0.2854f),
            PointF(0.2851f, 0.2472f),
            PointF(0.3250f, 0.1955f),
            PointF(0.3727f, 0.1702f),
            PointF(0.4197f, 0.1963f),
            PointF(0.4529f, 0.2635f),
            PointF(0.5009f, 0.2688f),
            PointF(0.5388f, 0.2090f),
            PointF(0.5734f, 0.1433f),
            PointF(0.6222f, 0.1342f),
            PointF(0.6496f, 0.2069f),
            PointF(0.6615f, 0.2913f),
            PointF(0.7071f, 0.3271f),
            PointF(0.7451f, 0.3763f),
            PointF(0.7613f, 0.4620f),
            PointF(0.8075f, 0.4955f),
            PointF(0.8541f, 0.5191f),
            PointF(0.9043f, 0.5277f),
            PointF(1.0500f, 0.5277f),  // us (ekran disi)
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.1712f, normY = 0.6093f),  // Z1  yola 122 ref-px
            BuildSpot(id = 2, normX = 0.1812f, normY = 0.2352f),  // Z1  yola 118 ref-px
            BuildSpot(id = 3, normX = 0.2579f, normY = 0.1385f),  // Z2  yola 119 ref-px
            BuildSpot(id = 4, normX = 0.2613f, normY = 0.7908f),  // Z2  yola 107 ref-px
            BuildSpot(id = 5, normX = 0.2781f, normY = 0.3497f),  // Z1  yola 98 ref-px
            BuildSpot(id = 6, normX = 0.2882f, normY = 0.5840f),  // Z1  yola 101 ref-px
            BuildSpot(id = 7, normX = 0.3741f, normY = 0.7475f),  // Z2  yola 103 ref-px
            BuildSpot(id = 8, normX = 0.4689f, normY = 0.1121f),  // Z2  yola 131 ref-px
            BuildSpot(id = 9, normX = 0.4941f, normY = 0.8435f),  // Z3  yola 120 ref-px
            BuildSpot(id = 10, normX = 0.5969f, normY = 0.2343f),  // Z3  yola 103 ref-px
            BuildSpot(id = 11, normX = 0.6864f, normY = 0.5992f),  // Z4  yola 112 ref-px
            BuildSpot(id = 12, normX = 0.6901f, normY = 0.7930f),  // Z4  yola 95 ref-px
            BuildSpot(id = 13, normX = 0.6964f, normY = 0.4256f),  // Z5  yola 104 ref-px
            BuildSpot(id = 14, normX = 0.7091f, normY = 0.2271f),  // Z4  yola 104 ref-px
            BuildSpot(id = 15, normX = 0.7958f, normY = 0.3124f),  // Z4  yola 115 ref-px
            BuildSpot(id = 16, normX = 0.8099f, normY = 0.6102f),  // Z5  yola 81 ref-px
        )
    )

    // ---- m04  kaynak: copied items/map (4).png
    //      rota=1  pad=12  yol=2265 ref-px
    val MAP_05 = LevelData(
        levelId = 5,
        name = "Batakli Ova / Marshland",
        description = "Sisli bataklik; tek serpantin yol, sol altta kor sapak.",
        waypoints = listOf(
            PointF(-0.0500f, 0.4553f),  // spawn (ekran disi)
            PointF(0.1483f, 0.4553f),
            PointF(0.2036f, 0.4387f),
            PointF(0.2521f, 0.4000f),
            PointF(0.3033f, 0.3556f),
            PointF(0.3479f, 0.2876f),
            PointF(0.3951f, 0.2286f),
            PointF(0.4480f, 0.2451f),
            PointF(0.4522f, 0.3475f),
            PointF(0.4341f, 0.4447f),
            PointF(0.4139f, 0.5405f),
            PointF(0.4296f, 0.6365f),
            PointF(0.4810f, 0.6808f),
            PointF(0.5395f, 0.6807f),
            PointF(0.5953f, 0.6493f),
            PointF(0.6351f, 0.5749f),
            PointF(0.6391f, 0.4720f),
            PointF(0.6352f, 0.3682f),
            PointF(0.6713f, 0.3102f),
            PointF(0.7257f, 0.3399f),
            PointF(0.7603f, 0.4235f),
            PointF(0.8134f, 0.4468f),
            PointF(1.0500f, 0.4468f),  // us (ekran disi)
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
            PointF(-0.0500f, 0.5234f),  // spawn (ekran disi)
            PointF(0.1292f, 0.5234f),
            PointF(0.1766f, 0.4681f),
            PointF(0.1960f, 0.3752f),
            PointF(0.2366f, 0.3092f),
            PointF(0.2844f, 0.3555f),
            PointF(0.2871f, 0.4605f),
            PointF(0.2979f, 0.5640f),
            PointF(0.3422f, 0.6212f),
            PointF(0.3890f, 0.5696f),
            PointF(0.3958f, 0.4653f),
            PointF(0.4291f, 0.3874f),
            PointF(0.4841f, 0.4122f),
            PointF(0.5201f, 0.4809f),
            PointF(0.5776f, 0.4967f),
            PointF(0.6340f, 0.4934f),
            PointF(0.6795f, 0.4286f),
            PointF(0.7005f, 0.3304f),
            PointF(0.7454f, 0.2827f),
            PointF(0.7773f, 0.3648f),
            PointF(0.7894f, 0.4667f),
            PointF(0.8445f, 0.4809f),
            PointF(1.0500f, 0.4809f),  // us (ekran disi)
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
            PointF(-0.0500f, 0.5021f),  // spawn (ekran disi)
            PointF(0.1388f, 0.5021f),
            PointF(0.1910f, 0.4854f),
            PointF(0.2088f, 0.3998f),
            PointF(0.2418f, 0.3243f),
            PointF(0.2927f, 0.3250f),
            PointF(0.3221f, 0.3996f),
            PointF(0.3466f, 0.4844f),
            PointF(0.3968f, 0.5104f),
            PointF(0.4352f, 0.4522f),
            PointF(0.4465f, 0.3583f),
            PointF(0.4703f, 0.2729f),
            PointF(0.5215f, 0.2469f),
            PointF(0.5690f, 0.2887f),
            PointF(0.5875f, 0.3779f),
            PointF(0.6028f, 0.4691f),
            PointF(0.6460f, 0.5191f),
            PointF(0.6908f, 0.4753f),
            PointF(0.7088f, 0.3858f),
            PointF(0.7461f, 0.3194f),
            PointF(0.7961f, 0.3413f),
            PointF(0.8325f, 0.4000f),
            PointF(1.0500f, 0.4000f),  // us (ekran disi)
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
            PointF(-0.0500f, 0.4596f),  // spawn (ekran disi)
            PointF(0.1435f, 0.4596f),
            PointF(0.1985f, 0.4364f),
            PointF(0.2288f, 0.3516f),
            PointF(0.2833f, 0.3430f),
            PointF(0.3111f, 0.4286f),
            PointF(0.3297f, 0.5252f),
            PointF(0.3697f, 0.5963f),
            PointF(0.4243f, 0.6261f),
            PointF(0.4644f, 0.6971f),
            PointF(0.5131f, 0.7426f),
            PointF(0.5678f, 0.7286f),
            PointF(0.6015f, 0.6476f),
            PointF(0.6233f, 0.5533f),
            PointF(0.6567f, 0.4708f),
            PointF(0.7128f, 0.4553f),
            PointF(0.7612f, 0.4169f),
            PointF(0.7767f, 0.3206f),
            PointF(0.7956f, 0.2274f),
            PointF(0.8484f, 0.2023f),
            PointF(0.8709f, 0.2922f),
            PointF(0.8852f, 0.3872f),
            PointF(1.0500f, 0.3872f),  // us (ekran disi)
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
            PointF(-0.0500f, 0.4128f),  // spawn (ekran disi)
            PointF(0.1364f, 0.4128f),
            PointF(0.1842f, 0.4340f),
            PointF(0.2350f, 0.4227f),
            PointF(0.2842f, 0.4105f),
            PointF(0.3102f, 0.3344f),
            PointF(0.3312f, 0.2535f),
            PointF(0.3779f, 0.2399f),
            PointF(0.4234f, 0.2680f),
            PointF(0.4543f, 0.3399f),
            PointF(0.4999f, 0.3744f),
            PointF(0.5430f, 0.3280f),
            PointF(0.5874f, 0.2854f),
            PointF(0.6366f, 0.3051f),
            PointF(0.6650f, 0.3764f),
            PointF(0.6539f, 0.4645f),
            PointF(0.6382f, 0.5509f),
            PointF(0.6630f, 0.6168f),
            PointF(0.7085f, 0.5841f),
            PointF(0.7387f, 0.5121f),
            PointF(0.7857f, 0.4853f),
            PointF(0.8349f, 0.4681f),
            PointF(1.0500f, 0.4681f),  // us (ekran disi)
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
            PointF(-0.0500f, 0.4213f),  // spawn (ekran disi)
            PointF(0.1292f, 0.4213f),
            PointF(0.1831f, 0.4345f),
            PointF(0.2371f, 0.4657f),
            PointF(0.2676f, 0.5539f),
            PointF(0.2774f, 0.6544f),
            PointF(0.2822f, 0.7566f),
            PointF(0.3306f, 0.8074f),
            PointF(0.3866f, 0.8053f),
            PointF(0.4316f, 0.7466f),
            PointF(0.4873f, 0.7491f),
            PointF(0.5358f, 0.8042f),
            PointF(0.5835f, 0.7783f),
            PointF(0.6024f, 0.6845f),
            PointF(0.6009f, 0.5818f),
            PointF(0.6360f, 0.5083f),
            PointF(0.6830f, 0.4504f),
            PointF(0.6987f, 0.3524f),
            PointF(0.7193f, 0.2570f),
            PointF(0.7719f, 0.2699f),
            PointF(0.7950f, 0.3645f),
            PointF(0.8421f, 0.4085f),
            PointF(1.0500f, 0.4085f),  // us (ekran disi)
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
            PointF(-0.0500f, 0.4596f),  // spawn (ekran disi)
            PointF(0.1340f, 0.4596f),
            PointF(0.1789f, 0.4782f),
            PointF(0.1985f, 0.5483f),
            PointF(0.2030f, 0.6281f),
            PointF(0.2061f, 0.7097f),
            PointF(0.2247f, 0.7846f),
            PointF(0.2663f, 0.8186f),
            PointF(0.3051f, 0.8560f),
            PointF(0.3403f, 0.9064f),
            PointF(0.3858f, 0.9191f),
            PointF(0.4302f, 0.8991f),
            PointF(0.4745f, 0.8748f),
            PointF(0.5115f, 0.8290f),
            PointF(0.5310f, 0.7544f),
            PointF(0.5666f, 0.7053f),
            PointF(0.6115f, 0.7192f),
            PointF(0.6572f, 0.7149f),
            PointF(0.6961f, 0.6750f),
            PointF(0.7282f, 0.6159f),
            PointF(0.7710f, 0.5869f),
            PointF(0.8158f, 0.5957f),
            PointF(1.0500f, 0.5957f),  // us (ekran disi)
        ),
        buildSpots = listOf(
            BuildSpot(id = 1, normX = 0.2437f, normY = 0.1383f),  // Z1  yola 100 ref-px
            BuildSpot(id = 2, normX = 0.2645f, normY = 0.3422f),  // Z1  yola 116 ref-px
            BuildSpot(id = 3, normX = 0.2752f, normY = 0.7044f),  // Z2  yola 120 ref-px
            BuildSpot(id = 4, normX = 0.3815f, normY = 0.5029f),  // Z2  yola 128 ref-px
            BuildSpot(id = 5, normX = 0.3896f, normY = 0.2101f),  // Z2  yola 149 ref-px
            BuildSpot(id = 6, normX = 0.5423f, normY = 0.6082f),  // Z4  yola 114 ref-px
            BuildSpot(id = 7, normX = 0.6433f, normY = 0.6146f),  // Z4  yola 109 ref-px
            BuildSpot(id = 8, normX = 0.6453f, normY = 0.3175f),  // Z4  yola 96 ref-px
            BuildSpot(id = 9, normX = 0.7574f, normY = 0.7648f),  // Z5  yola 149 ref-px
            BuildSpot(id = 10, normX = 0.8128f, normY = 0.1908f),  // Z5  yola 210 ref-px
        )
    )

    /** Catallanan haritalarin IKINCI kolu. levelId -> waypoint listesi. */
    val ALT_ROUTES: Map<Int, List<PointF>> = mapOf(
        1 to listOf(
            PointF(-0.0500f, 0.4340f),
            PointF(0.1388f, 0.4340f),
            PointF(0.1908f, 0.4095f),
            PointF(0.2121f, 0.3188f),
            PointF(0.2487f, 0.2450f),
            PointF(0.2987f, 0.2695f),
            PointF(0.3544f, 0.2790f),
            PointF(0.4020f, 0.2284f),
            PointF(0.4497f, 0.1754f),
            PointF(0.5026f, 0.1807f),
            PointF(0.5357f, 0.2603f),
            PointF(0.5893f, 0.2814f),
            PointF(0.6417f, 0.2490f),
            PointF(0.6978f, 0.2426f),
            PointF(0.7530f, 0.2556f),
            PointF(0.7786f, 0.3413f),
            PointF(0.7568f, 0.4328f),
            PointF(0.7391f, 0.5264f),
            PointF(0.7552f, 0.6072f),
            PointF(0.7823f, 0.6823f),
            PointF(0.8352f, 0.7129f),
            PointF(0.8804f, 0.6638f),
            PointF(1.0500f, 0.6638f),
        ),
        2 to listOf(
            PointF(-0.0500f, 0.4553f),
            PointF(0.1435f, 0.4553f),
            PointF(0.1948f, 0.4320f),
            PointF(0.2314f, 0.3645f),
            PointF(0.2846f, 0.3644f),
            PointF(0.3300f, 0.4204f),
            PointF(0.3837f, 0.4240f),
            PointF(0.4184f, 0.3503f),
            PointF(0.4258f, 0.2531f),
            PointF(0.4526f, 0.1733f),
            PointF(0.5052f, 0.1924f),
            PointF(0.5498f, 0.2494f),
            PointF(0.6042f, 0.2404f),
            PointF(0.6574f, 0.2566f),
            PointF(0.6804f, 0.3408f),
            PointF(0.6866f, 0.4386f),
            PointF(0.6985f, 0.5320f),
            PointF(0.7189f, 0.6237f),
            PointF(0.7528f, 0.6826f),
            PointF(0.7991f, 0.7184f),
            PointF(0.8408f, 0.6572f),
            PointF(0.8828f, 0.5957f),
            PointF(1.0500f, 0.5957f),
        ),
        3 to listOf(
            PointF(-0.0500f, 0.5021f),
            PointF(0.1316f, 0.5021f),
            PointF(0.1938f, 0.4593f),
            PointF(0.2417f, 0.3757f),
            PointF(0.2769f, 0.2688f),
            PointF(0.3428f, 0.2643f),
            PointF(0.3432f, 0.3674f),
            PointF(0.3417f, 0.4817f),
            PointF(0.3133f, 0.5916f),
            PointF(0.3353f, 0.7044f),
            PointF(0.4006f, 0.7362f),
            PointF(0.4637f, 0.6891f),
            PointF(0.5079f, 0.5945f),
            PointF(0.5023f, 0.4805f),
            PointF(0.5248f, 0.3737f),
            PointF(0.5598f, 0.2711f),
            PointF(0.5999f, 0.1793f),
            PointF(0.6398f, 0.2722f),
            PointF(0.6937f, 0.3319f),
            PointF(0.7539f, 0.3127f),
            PointF(0.8200f, 0.2900f),
            PointF(0.8876f, 0.3106f),
            PointF(1.0500f, 0.3106f),
        ),
        4 to listOf(
            PointF(-0.0500f, 0.4638f),
            PointF(0.1388f, 0.4638f),
            PointF(0.1883f, 0.4881f),
            PointF(0.2292f, 0.5405f),
            PointF(0.2360f, 0.6337f),
            PointF(0.2769f, 0.6899f),
            PointF(0.3136f, 0.7525f),
            PointF(0.3408f, 0.8312f),
            PointF(0.3906f, 0.8490f),
            PointF(0.4255f, 0.7796f),
            PointF(0.4455f, 0.6917f),
            PointF(0.4938f, 0.6791f),
            PointF(0.5307f, 0.7483f),
            PointF(0.5720f, 0.8070f),
            PointF(0.6235f, 0.7962f),
            PointF(0.6585f, 0.7245f),
            PointF(0.7088f, 0.6966f),
            PointF(0.7479f, 0.6421f),
            PointF(0.7561f, 0.5484f),
            PointF(0.8024f, 0.5315f),
            PointF(0.8511f, 0.5191f),
            PointF(0.9043f, 0.5277f),
            PointF(1.0500f, 0.5277f),
        ),
        11 to listOf(
            PointF(-0.0500f, 0.4340f),
            PointF(0.1292f, 0.4340f),
            PointF(0.1827f, 0.3870f),
            PointF(0.2063f, 0.2896f),
            PointF(0.2560f, 0.2333f),
            PointF(0.3140f, 0.2646f),
            PointF(0.3325f, 0.3660f),
            PointF(0.3164f, 0.4725f),
            PointF(0.3201f, 0.5803f),
            PointF(0.3680f, 0.6383f),
            PointF(0.4246f, 0.5945f),
            PointF(0.4501f, 0.4986f),
            PointF(0.4587f, 0.3891f),
            PointF(0.4849f, 0.2909f),
            PointF(0.5396f, 0.2405f),
            PointF(0.6010f, 0.2279f),
            PointF(0.6625f, 0.2257f),
            PointF(0.7151f, 0.2814f),
            PointF(0.7297f, 0.3875f),
            PointF(0.7240f, 0.4978f),
            PointF(0.7613f, 0.5508f),
            PointF(0.8158f, 0.5957f),
            PointF(1.0500f, 0.5957f),
        ),
    )

    val ALL_MAPS: List<LevelData> = listOf(
        MAP_01, MAP_02, MAP_03, MAP_04, MAP_05, MAP_06, MAP_07, MAP_08, MAP_09, MAP_10, MAP_11
    )
}
