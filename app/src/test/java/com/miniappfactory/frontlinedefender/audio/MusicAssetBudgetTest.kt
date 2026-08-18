package com.miniappfactory.frontlinedefender.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Faz 14 — MUZIK VARLIK BUTCESI.
 *
 * Neden test: muzik dosyalari APK'nin en kolay sisen kalemi. WAV birakmak ya
 * da kaliteyi yukseltmek tek satirlik bir degisiklik ama APK'yi megabaytlarca
 * buyutur ve bunu kimse fark etmez. Bu test bu isi derleme zamanina getirir.
 *
 * Butce: muzik icin toplam 1.5 MB. Mevcut kullanim ~713 KB, yani yeni bir
 * parca eklemek icin hala yer var.
 *
 * Kaynaklara dosya sistemi uzerinden bakilir; `R.raw` uzerinden okumak
 * Robolectric ve tam bir kaynak paketi gerektirirdi, oysa sorulan sey dosya
 * BOYUTU ve BICIMI.
 */
class MusicAssetBudgetTest {

    private companion object {
        const val MUSIC_BUDGET_BYTES = 1_536_000L // 1.5 MB
        val EXPECTED_TRACKS = listOf("music_battle.ogg", "music_menu.ogg")

        /** Faz 14'te eklenen efektler. Efekt butcesi ayri ve cok daha kucuk. */
        val NEW_EFFECTS = listOf(
            "sfx_combo_up_1.ogg",
            "sfx_combo_up_2.ogg",
            "sfx_combo_up_3.ogg",
            "sfx_combo_up_4.ogg",
            "sfx_wave_cleared.ogg",
        )

        /**
         * Efektler icin toplam tavan. Mevcut 20 efekt ~144 KB; tavan bilincli
         * olarak dar tutuldu ki biri yanlislikla WAV ya da uzun bir parca
         * birakmasin.
         */
        const val SFX_BUDGET_BYTES = 262_144L // 256 KB
    }

    /**
     * Gradle birim testlerinin calisma dizini modul koku (`app/`) olur, ama
     * bazi kosucularda proje koku olabiliyor. Ikisi de denenir.
     */
    private fun rawDir(): File {
        val candidates = listOf(
            File("src/main/res/raw"),
            File("app/src/main/res/raw"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error(
                "res/raw bulunamadi. Denenen yollar: " +
                    candidates.joinToString { it.absolutePath }
            )
    }

    @Test
    fun `muzik dosyalari mevcut`() {
        val raw = rawDir()
        EXPECTED_TRACKS.forEach { name ->
            val f = File(raw, name)
            assertTrue("eksik muzik dosyasi: ${f.absolutePath}", f.isFile)
            assertTrue("bos muzik dosyasi: $name", f.length() > 0)
        }
    }

    @Test
    fun `muzik toplam butceyi asmiyor`() {
        val raw = rawDir()
        val total = EXPECTED_TRACKS.sumOf { File(raw, it).length() }
        assertTrue(
            "muzik butcesi asildi: ${total / 1024} KB > ${MUSIC_BUDGET_BYTES / 1024} KB",
            total <= MUSIC_BUDGET_BYTES
        )
    }

    @Test
    fun `tum ses varliklari OGG olarak saklaniyor`() {
        val raw = rawDir()
        val all = raw.listFiles { f -> f.isFile } ?: emptyArray()
        assertTrue("res/raw bos", all.isNotEmpty())
        all.forEach { f ->
            val header = ByteArray(4)
            f.inputStream().use { it.read(header) }
            assertEquals(
                "${f.name} Ogg kapsayicisi degil; WAV birakmak APK'yi ~10x buyutur",
                "OggS",
                String(header, Charsets.US_ASCII)
            )
        }
    }

    @Test
    fun `yeni efektler mevcut ve efekt butcesi asilmiyor`() {
        val raw = rawDir()
        NEW_EFFECTS.forEach { name ->
            val f = File(raw, name)
            assertTrue("eksik efekt: ${f.absolutePath}", f.isFile)
            assertTrue("bos efekt: $name", f.length() > 0)
        }

        val sfxTotal = raw.listFiles { f -> f.name.startsWith("sfx_") }
            ?.sumOf { it.length() } ?: 0L
        assertTrue(
            "efekt butcesi asildi: ${sfxTotal / 1024} KB > ${SFX_BUDGET_BYTES / 1024} KB",
            sfxTotal <= SFX_BUDGET_BYTES
        )
    }

    @Test
    fun `zincir riserlari birbirinden farkli dosyalar`() {
        // Ayni dosyanin dort kez kopyalanmasi testlerden kacar ama oyuncuya
        // duz bir merdiven olarak gelir. Icerik karsilastirmasi bunu yakalar.
        val raw = rawDir()
        val digests = NEW_EFFECTS.filter { it.startsWith("sfx_combo_up_") }
            .map { File(raw, it).readBytes().toList() }
        assertEquals("dort riser da farkli icerik olmali", 4, digests.distinct().size)
    }

    @Test
    fun `her muzik parcasi efektlerden belirgin sekilde uzun`() {
        // Dolayli ama ucuz bir saglik kontrolu: bir dongu parcasi en kisa
        // efektten cok daha buyuk olmali. Yanlislikla bir SFX'i muzik diye
        // koymak bu testte yakalanir.
        val raw = rawDir()
        val largestSfx = raw.listFiles { f -> f.name.startsWith("sfx_") }
            ?.maxOfOrNull { it.length() } ?: 0L
        assertTrue("sfx dosyalari bulunamadi", largestSfx > 0)

        EXPECTED_TRACKS.forEach { name ->
            val size = File(raw, name).length()
            assertTrue(
                "$name ($size B) en buyuk efektten ($largestSfx B) buyuk olmali",
                size > largestSfx * 4
            )
        }
    }
}
