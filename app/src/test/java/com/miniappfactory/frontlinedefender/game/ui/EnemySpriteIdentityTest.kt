package com.miniappfactory.frontlinedefender.game.ui

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * DUSMAN SILUET KIMLIGI + YENI GORSEL VARLIK SOZLESMESI.
 *
 * NEDEN TEST - bu kozmetik degil, oynanisin temeli:
 *   - SHIELDED_TROOPER (165 can) Gatling'e neredeyse bagisik, Cannon onu
 *     yarilar. INFANTRY (82 can) icin tam tersi gecerli. Oyuncu bu ikisini
 *     YOLDA, ~50 px genisliginde ayirt edemezse kule secimi kumara doner.
 *   - COMMAND_TANK (2.860 can) kampanyanin doruk ani. Normal tankla ayni
 *     silueti tasirsa oyuncu bossun geldigini goremez.
 *
 * Bir dusman tipinin BASKA bir tipin sprite'ini kullanmasi Kotlin acisindan
 * kusursuz derlenir. Enum uzerindeki when dallari zaten derleme zamaninda tam
 * kapsamlidir - yani "eksik dal" bir DERLEME hatasidir, testin yakalayacagi
 * bir sey degil. Testin yakaladigi gercek hata YANLIS/PAYLASILAN esleme: iki
 * tipin ayni glife baglanmasi sessizce derlenir ve ancak cihazda, oyuncu
 * yanlis kuleyi kurunca fark edilir. Bu yuzden burada dogrulanan sey dallarin
 * varligi degil, cikan sprite'larin BIRBIRINDEN FARKLI olmasi.
 *
 * KAPSAM NOTU: burasi UI/onizleme eslemesini (UiSprites.enemySpriteRes)
 * dogrular. Oynanis cizimindeki esleme GameSprites.createInternal icinde ayri
 * durur ve bu gorev sirasinda baska bir ajanin elindeydi; o dosya guncellenince
 * ayni benzersizlik iddiasi orasi icin de eklenmelidir.
 */
class EnemySpriteIdentityTest {

    private companion object {
        /**
         * Gradle birim testlerinin calisma dizini modul koku (app/) olur, ama
         * bazi kosucularda proje koku olabiliyor - MusicAssetBudgetTest ile
         * ayni gerekce ve ayni cozum.
         */
        fun drawableDir(): File {
            val candidates = listOf(
                File("src/main/res/drawable-nodpi"),
                File("app/src/main/res/drawable-nodpi"),
            )
            return candidates.firstOrNull { it.isDirectory }
                ?: error(
                    "res/drawable-nodpi bulunamadi. Denenen yollar: " +
                        candidates.joinToString { it.absolutePath }
                )
        }

        /** Bu gorevde pakete eklenen dosyalar ve beklenen genislikleri. */
        val NEW_SPRITES = mapOf(
            "spr_enemy_shielded_trooper" to 104,
            "spr_enemy_command_tank" to 188,
            "spr_ic_supply_crate" to 96,
            "spr_ic_booster_supply_drop" to 96,
            "spr_ic_booster_base_repair" to 96,
        )

        /**
         * Yeni varliklarin toplam APK maliyeti tavani. Bugunku gercek toplam
         * ~40.5 KB; tavan bilincli olarak dar ki biri kaynagi 1254 px olarak
         * ya da kayipsiz olarak paketlemeye kalkarsa test dussun (kayipsiz
         * karsiliklari ~92 KB idi).
         */
        const val NEW_SPRITE_BUDGET_BYTES = 65_536L
    }

    // =====================================================================
    // SILUET KIMLIGI
    // =====================================================================

    @Test
    fun hicbirDusmanTipiBaskaTipleGlifPaylasmaz() {
        val byType = GameConfig.EnemyType.entries.associateWith { enemySpriteRes(it) }

        byType.forEach { (type, res) ->
            assertTrue("$type icin sprite kaynagi tanimsiz (0)", res != 0)
        }

        val shared = byType.entries
            .groupBy({ it.value }, { it.key })
            .filterValues { it.size > 1 }

        assertTrue(
            "Su dusman tipleri AYNI sprite'i paylasiyor, yani oyuncu onlari " +
                "yolda ayirt edemez: " + shared.values,
            shared.isEmpty()
        )
        assertEquals(
            "Her dusman tipi icin benzersiz bir sprite beklenir",
            GameConfig.EnemyType.entries.size,
            byType.values.toSet().size
        )
    }

    @Test
    fun zirhliAskerPiyadeSpriteiniKullanmaz() {
        // Gatling zirhliya islemez, piyadeyi bicer. Ayni glif = kumar.
        assertTrue(
            "SHIELDED_TROOPER hala INFANTRY sprite'ini kullaniyor",
            enemySpriteRes(GameConfig.EnemyType.SHIELDED_TROOPER) !=
                enemySpriteRes(GameConfig.EnemyType.INFANTRY)
        )
    }

    @Test
    fun komutaTankiNormalTankSpriteiniKullanmaz() {
        assertTrue(
            "COMMAND_TANK hala TANK sprite'ini kullaniyor",
            enemySpriteRes(GameConfig.EnemyType.COMMAND_TANK) !=
                enemySpriteRes(GameConfig.EnemyType.TANK)
        )
    }

    @Test
    fun herKuleTipiKendiSpriteiniKullanir() {
        val byType = GameConfig.TowerType.entries.associateWith { towerSpriteRes(it) }
        assertEquals(
            "Kule sprite'lari benzersiz olmali",
            GameConfig.TowerType.entries.size,
            byType.values.toSet().size
        )
    }

    // =====================================================================
    // VARLIK SOZLESMESI - dosyalar gercekten var mi, alfa korundu mu
    // =====================================================================

    @Test
    fun yeniDrawableDosyalariDisketeVar() {
        val dir = drawableDir()
        NEW_SPRITES.keys.forEach { name ->
            val f = File(dir, name + ".webp")
            assertTrue("Eksik drawable: " + f.absolutePath, f.isFile)
            assertTrue("Bos drawable: " + name, f.length() > 0)
        }
    }

    /**
     * ALFA KORUNDU MU - bu testin varlik sebebi.
     *
     * Sprite'in alfasi kaybolursa dosya hala gecerli bir WebP olur, hala
     * derlenir, hala cizilir; sadece her dusman opak bir dikdortgen icinde
     * yuruur. Yani bu hata derleyiciden ve gozden kolayca kacar. WebP
     * konteynerinde alfa bir BAYRAK: VP8X govdesinin ilk baytinda 0x10.
     * Burada dosya baytlari dogrudan okunur - Android calisma zamani ya da
     * bir goruntu cozucu gerekmez.
     */
    @Test
    fun yeniSpritelarAlfaTasiyorVeOlcekAilesineOturuyor() {
        val dir = drawableDir()
        NEW_SPRITES.forEach { (name, expectedWidth) ->
            val bytes = File(dir, name + ".webp").readBytes()

            assertTrue(name + ": dosya WebP basligi icin fazla kisa", bytes.size > 30)
            assertEquals(name + ": RIFF imzasi yok", "RIFF", bytes.ascii(0, 4))
            assertEquals(name + ": WEBP imzasi yok", "WEBP", bytes.ascii(8, 4))
            assertEquals(name + ": VP8X genisletilmis blok yok", "VP8X", bytes.ascii(12, 4))

            val flags = bytes[20].toInt() and 0xFF
            assertTrue(
                name + ": WebP ALFA BAYRAGI yok (flags=0x" + flags.toString(16) +
                    "). Sprite opak bir kutu olarak cizilir.",
                flags and 0x10 != 0
            )

            assertEquals(name + ": beklenen genislik", expectedWidth, bytes.u24(24) + 1)
            assertTrue(name + ": yukseklik sifir", bytes.u24(27) + 1 > 0)
        }
    }

    @Test
    fun yeniSpritelarApkButcesiniAsmiyor() {
        val dir = drawableDir()
        val total = NEW_SPRITES.keys.sumOf { File(dir, it + ".webp").length() }
        assertTrue(
            "Yeni gorsel varliklar " + (total / 1024) + " KB yer kapliyor, tavan " +
                (NEW_SPRITE_BUDGET_BYTES / 1024) + " KB",
            total <= NEW_SPRITE_BUDGET_BYTES
        )
    }

    /**
     * Komuta tanki NORMAL tanktan daha yuksek cozunurluklu, zirhli asker de
     * piyadeden daha genis olmali. Kaynak cozunurlugu oynanis olceginin
     * (GameConfig.ENEMY_SPRITES[..].widthRefPx) yaklasik 2 kati olacak
     * sekilde hazirlanir; bu oran bozulursa boss yuksek yogunluklu ekranda
     * normal tanktan BULANIK cikar - yani en buyuk dusman en kotu gorunur.
     */
    @Test
    fun bossVeZirhliSpritelariAkrabalarindanYuksekCozunurluklu() {
        val dir = drawableDir()
        fun width(n: String) = File(dir, n + ".webp").readBytes().u24(24) + 1

        assertTrue(
            "Komuta tanki (" + width("spr_enemy_command_tank") + " px) normal " +
                "tanktan (" + width("spr_enemy_tank") + " px) daha genis olmali",
            width("spr_enemy_command_tank") > width("spr_enemy_tank")
        )
        assertTrue(
            "Zirhli asker (" + width("spr_enemy_shielded_trooper") + " px) " +
                "piyadeden (" + width("spr_enemy_infantry") + " px) daha genis olmali",
            width("spr_enemy_shielded_trooper") > width("spr_enemy_infantry")
        )
    }
}

/** WebP basliklarindaki ASCII fourcc alanlari. */
private fun ByteArray.ascii(offset: Int, length: Int): String =
    String(this, offset, length, Charsets.US_ASCII)

/** WebP VP8X tuval olculeri 24 bit little-endian ve "gercek deger - 1" tutulur. */
private fun ByteArray.u24(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16)
