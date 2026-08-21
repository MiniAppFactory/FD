package com.miniappfactory.frontlinedefender.geometry

/**
 * ===========================================================================
 * PISIRILMIS HARITA MASKESI — "rota gercekten boyali yolun uzerinde mi?"
 * ===========================================================================
 *
 * `LevelGeometryDataTest` rotanin yalnizca **kendi icindeki** ozelliklerini
 * (nokta sayisi, 0..1 araligi, segment uzunlugu) dogrular. Bunlarin hicbiri
 * "dusman cimenden mi yuruyor" sorusunu cevaplamaz: 21 noktali bir polyline
 * de, 55 noktali bir polyline de bu testlerin hepsini gecerken haritanin
 * disindan gecebilir. Bu fixture o boslugu kapatir.
 *
 * v1 NEDEN YALAN SOYLUYORDU (2026-08-21, cihaz geri bildirimi)
 * -----------------------------------------------------------
 * Oyuncu: *"hala yol olmayan yerlerden geciyorlar, bu level 3 ornegin."*
 * v1 UC sinif tasiyordu — yol / bitki / DIGER — ve KAYA, kopru, us rampasi,
 * spawn platformu hepsi ayni "diger" kovasindaydi. Testler yalnizca BITKIYI
 * yasakliyordu. Harita 3'un rotasi sag ucta boyali yolu birakip kayaliktan
 * duz kesiyordu; maske "%0,00 cim" diyor, test yesil yaniyor, oyuncu ekranda
 * kayadan yuruyen asker goruyordu. Uc olcum de dogruydu, cunku hicbiri
 * "asker kayanin ustunde mi" diye SORMUYORDU.
 *
 * v2'de kaya kendi sinifidir ([CLASS_ROCK]). Bicim ayni, sihir degisti
 * (FDMASK01 -> FDMASK02): eski dosya yeni anlamla sessizce okunamasin.
 *
 * KAYNAK DA DEGISTI: v1, asset-pack'teki 1672x941 PNG'lerden pisirilmisti;
 * uygulama ise `res/drawable-nodpi/bg_level_XX.webp` (1920x1081) yukluyor.
 * Iki goruntunun cercevesi ayni cikti (olculdu: en iyi kaydirma dx=0 dy=0,
 * ortusme %82-92), yani v1 KAYIK degildi — EKSIKTI. v2 dogrudan uygulamanin
 * yukledigi webp'ten uretilir: `node docs/level_geometry/bake_masks.js`.
 *
 * ESIK NEREDEN GELIYOR: ayirt edici olcut hue degil, (R-G)/V ("sicaklik").
 * Olculen kume merkezleri — harita 3 yol (155,119,67) ve (117,98,47), cim
 * (87,80,34) ve (58,57,23); harita 4 yol (173,135,73) ve (143,115,56), cim
 * (114,115,31) ve (89,94,25). Sicaklik yolda 0,16-0,23; cimde 0,08'in
 * altinda. Esik 0,13 tam ortada. Kaya doygunlukla ayrilir (S < 85).
 * Ayrintili gerekce: docs/level_geometry/lib_classify.js.
 *
 * NEDEN WEBP DEGIL DE PISIRILMIS MASKE: birim testinde goruntu cozucu yok
 * (Robolectric istemiyoruz) ve 11 tam cozunurluklu resmi her kosuda cozmek
 * testi saniyelerce yavaslatirdi.
 *
 * Cozunurluk 418x235 -> 1 hucre = 4,59 ref-px.
 */
internal object MapMaskFixture {

    const val CLASS_OTHER = 0   // kopru, su, us rampasi, spawn platformu, yapilar
    const val CLASS_ROAD = 1    // toprak yol
    const val CLASS_VEGETATION = 2  // cim / agac — dusmanin YURUMEMESI gereken zemin
    const val CLASS_ROCK = 3    // kayalik — yurunemez; v1'de "diger" icinde saklaniyordu

    /** Maskede gecen sinif sayisi (0..3). */
    const val CLASS_COUNT = 4

    private const val RESOURCE = "/level_geometry/map_masks_v2.bin"
    private const val MAGIC = "FDMASK02"

    class Mask(val width: Int, val height: Int, private val cells: ByteArray) {
        /** Normalize (0..1) harita koordinatinin sinifi. */
        fun classAt(normX: Float, normY: Float): Int = cells[cellOf(normX, normY)].toInt()

        /** Normalize koordinatin duz hucre indeksi. */
        fun cellOf(normX: Float, normY: Float): Int {
            val cx = (normX * width).toInt().coerceIn(0, width - 1)
            val cy = (normY * height).toInt().coerceIn(0, height - 1)
            return cy * width + cx
        }

        /**
         * Iki hucre arasinda YALNIZCA yol hucrelerinden gecen bir baglanti
         * var mi?
         *
         * NEDEN VAR: "rota burada yoldan cikmis" iddiasini dondurulmus bir
         * istisna listesine degil haritanin kendisine dayandirir. Baglanti
         * varsa rota kestirme yapmistir; yoksa boyali yol gercekten kopuktur
         * (harita 6'nin tas koprusu, harita 10'un nehir gecisi) ve rota
         * gecmek zorundadir.
         *
         * 8-komsu KASITLI: mumkun olan en musamahakar baglanti kullanilir,
         * yani "yol kopuk" sonucu en zor sekilde elde edilir. 4-komsu
         * kullanmak diyagonal temaslari kopuk sayar ve testi gevsetirdi.
         */
        fun roadConnects(fromCell: Int, toCell: Int): Boolean {
            if (cells[fromCell].toInt() != CLASS_ROAD || cells[toCell].toInt() != CLASS_ROAD) return false
            if (fromCell == toCell) return true
            val seen = BooleanArray(cells.size)
            val stack = ArrayDeque<Int>()
            stack.addLast(fromCell)
            seen[fromCell] = true
            while (stack.isNotEmpty()) {
                val k = stack.removeLast()
                if (k == toCell) return true
                val x = k % width
                val y = k / width
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        val nk = ny * width + nx
                        if (seen[nk] || cells[nk].toInt() != CLASS_ROAD) continue
                        seen[nk] = true
                        stack.addLast(nk)
                    }
                }
            }
            return false
        }
    }

    /** mapId (1..11) -> maske. */
    val masks: Map<Int, Mask> by lazy { load() }

    fun maskFor(mapId: Int): Mask =
        masks[mapId] ?: error("harita $mapId icin pisirilmis maske yok — bake_masks.js kosturuldu mu?")

    private fun load(): Map<Int, Mask> {
        val stream = MapMaskFixture::class.java.getResourceAsStream(RESOURCE)
            ?: error(
                "$RESOURCE bulunamadi. Uretmek icin: node docs/level_geometry/bake_masks.js"
            )
        // NOT: v1 dosyasi silindi. Sihir kontrolu asagida; eski bir kopya
        // geri konursa test yesil vermez, PATLAR — bu kasitli.
        val bytes = stream.use { it.readBytes() }
        require(bytes.size > 9) { "maske dosyasi cok kisa (${bytes.size} bayt)" }
        val magic = String(bytes, 0, 8, Charsets.ISO_8859_1)
        require(magic == MAGIC) { "maske dosyasi sihri '$magic', beklenen '$MAGIC'" }

        var off = 8
        val mapCount = bytes[off++].toInt() and 0xFF
        val out = LinkedHashMap<Int, Mask>(mapCount)
        repeat(mapCount) {
            val mapId = bytes[off].toInt() and 0xFF
            val w = u16(bytes, off + 1)
            val h = u16(bytes, off + 3)
            val rleLen = u32(bytes, off + 5)
            off += 9

            val cells = ByteArray(w * h)
            var p = off
            var k = 0
            while (p < off + rleLen) {
                val cls = bytes[p++]
                var run = 0
                var shift = 0
                while (true) {
                    val b = bytes[p++].toInt() and 0xFF
                    run = run or ((b and 0x7F) shl shift)
                    shift += 7
                    if (b and 0x80 == 0) break
                }
                java.util.Arrays.fill(cells, k, k + run, cls)
                k += run
            }
            require(k == w * h) { "harita $mapId RLE uzunlugu $k != ${w * h}" }
            out[mapId] = Mask(w, h, cells)
            off += rleLen
        }
        return out
    }

    private fun u16(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)
}
