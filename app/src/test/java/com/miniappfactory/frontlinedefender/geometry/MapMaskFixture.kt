package com.miniappfactory.frontlinedefender.geometry

/**
 * ===========================================================================
 * PISIRILMIS HARITA MASKESI — "rota gercekten boyali yolun uzerinde mi?"
 * ===========================================================================
 *
 * `LevelGeometryDataTest` bugune kadar rotanin yalnizca **kendi icindeki**
 * ozelliklerini (nokta sayisi, 0..1 araligi, segment uzunlugu) dogruluyordu.
 * Bunlarin hicbiri "dusman cimenden mi yuruyor" sorusunu cevaplamaz: 21
 * noktali bir polyline de, 55 noktali bir polyline de bu testlerin hepsini
 * gecerken haritanin disindan gecebilir.
 *
 * Bu fixture o bosluğu kapatir. Harita PNG'lerinden olculen **yol** ve
 * **bitki** maskeleri `docs/level_geometry/bake_masks.js` ile bir kez uretilip
 * `src/test/resources/level_geometry/map_masks_v1.bin` icine yazildi. Test
 * her waypoint'i bu maskeye karsi YENIDEN degerlendirir; dondurulmus bir sayi
 * karsilastirmaz. Biri bir noktayi cimin uzerine tasirsa test kirilir.
 *
 * NEDEN PNG DEGIL DE PISIRILMIS MASKE: birim testinde PNG cozucu yok
 * (Robolectric istemiyoruz, Pillow bu makinede kurulu degil) ve 11 tam
 * cozunurluklu PNG'yi her kosuda okumak testi saniyelerce yavaslatirdi.
 *
 * Esikler `docs/level_geometry/GEOMETRY_REPORT.md` §1 ile BIREBIR aynidir —
 * 11 haritanin hepsinde tek set (HSV: yol H 17..33 / S 118..190 / V 68..225,
 * bitki H 34..72 / S >= 95). Node ile uretilen maskenin alani, Python
 * referansiyla haritada en fazla **%0,65** fark eder (olculdu).
 *
 * Cozunurluk 418x235 (kaynak 1672x941'in 1/4'u) -> 1 hucre = 4,59 ref-px.
 */
internal object MapMaskFixture {

    const val CLASS_OTHER = 0   // kopru, su, kaya, golge, us rampasi, spawn platformu
    const val CLASS_ROAD = 1    // toprak yol
    const val CLASS_VEGETATION = 2  // cim / bitki — dusmanin YURUMEMESI gereken zemin

    private const val RESOURCE = "/level_geometry/map_masks_v1.bin"
    private const val MAGIC = "FDMASK01"

    class Mask(val width: Int, val height: Int, private val cells: ByteArray) {
        /** Normalize (0..1) harita koordinatinin sinifi. */
        fun classAt(normX: Float, normY: Float): Int {
            val cx = (normX * width).toInt().coerceIn(0, width - 1)
            val cy = (normY * height).toInt().coerceIn(0, height - 1)
            return cells[cy * width + cx].toInt()
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
