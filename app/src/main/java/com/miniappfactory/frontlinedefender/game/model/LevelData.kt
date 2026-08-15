package com.miniappfactory.frontlinedefender.game.model

/**
 * Normalized 2D Level definition.
 *
 * Waypoint ve build pad koordinatlari HARITA BITMAP'inin kendi normalize
 * uzayindadir (0.0f..1.0f), tuvalin degil. Cizim/simulasyon bunlari haritanin
 * gercek cizim dikdortgenine (letterbox sonrasi) tasir; bkz.
 * GameEngine.updateMapDimensions.
 *
 * ---------------------------------------------------------------------------
 * Faz 4: BU DOSYADA ARTIK ELLE YAZILMIS SEVIYE YOK.
 *
 * Eski `LEVEL_1` / `LANE_UPPER` / `LANE_LOWER` sabitleri kaldirildi. Tek
 * geometri kaynagi `LevelGeometry.kt`: 11 haritanin OLCULMUS (renk maskesi +
 * uzaklik donusumu + Dijkstra) verisi, docs/level_geometry/extract_geometry.py
 * tarafindan uretilir. Elle duzeltme yapilmaz — betik yeniden kosturulur.
 * ---------------------------------------------------------------------------
 */
data class PointF(val x: Float, val y: Float)

data class BuildSpot(
    val id: Int,
    val normX: Float,
    val normY: Float
)

data class LevelData(
    val levelId: Int,
    val name: String,
    val description: String,
    /** Dusmanlarin yurudugu birincil rota. */
    val waypoints: List<PointF>,
    /**
     * Haritada cizili IKINCI serit. `LevelGeometry` bunu doldurmaz; ikinci kol
     * `LevelGeometry.ALT_ROUTES[mapId]` icindedir ve motora
     * `LevelData.routesForMapId()` uzerinden gelir.
     */
    val alternateWaypoints: List<PointF> = emptyList(),
    val buildSpots: List<BuildSpot>
) {
    companion object {
        /**
         * Catallanan haritalarda ikinci kolun GERCEK bir tasarim catallanmasi
         * oldugu haritalar. Harita 3 kasitli olarak DISARIDA: GEOMETRY_REPORT.md
         * 4.1'e gore oradaki "iki rota" serpantin U donuslerinin sanatta birlesik
         * olmasinin sonucu, tasarim catallanmasi degil. Kisa kol koca bir ilmegi
         * atlayip 4 pad'i olu birakiyor.
         */
        private val TRUE_FORK_MAP_IDS = setOf(1, 2, 4, 11)

        /**
         * Harita 3'un KANONIK rotasi `ALT_ROUTES[3]`'tur (uzun kol), `waypoints`
         * degil (DECISIONS + GEOMETRY_REPORT.md 4.1).
         */
        private const val ALT_IS_CANONICAL_MAP_ID = 3

        /** mapId (1..11) -> olculmus harita geometrisi. */
        fun forMapId(mapId: Int): LevelData =
            LevelGeometry.ALL_MAPS.firstOrNull { it.levelId == mapId }
                ?: LevelGeometry.ALL_MAPS.first()

        /**
         * Bir haritada dusmanlarin kullanabilecegi TUM rotalar.
         *
         * - Gercek catallanma (1, 2, 4, 11): iki kol da dondurulur; motor
         *   seed'li deterministik atama yapar.
         * - Harita 3: yalnizca uzun kol (kanonik) dondurulur.
         * - Diger 6 harita: tek rota.
         *
         * Hicbir durumda bos liste donmez.
         */
        fun routesForMapId(mapId: Int): List<List<PointF>> {
            val primary = forMapId(mapId).waypoints
            val alt = LevelGeometry.ALT_ROUTES[mapId]

            return when {
                mapId == ALT_IS_CANONICAL_MAP_ID && alt != null && alt.size > 1 -> listOf(alt)
                mapId in TRUE_FORK_MAP_IDS && alt != null && alt.size > 1 -> listOf(primary, alt)
                else -> listOf(primary)
            }.filter { it.size > 1 }.ifEmpty { listOf(primary) }
        }
    }
}
