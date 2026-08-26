package com.miniappfactory.frontlinedefender.visual

import com.miniappfactory.frontlinedefender.game.ui.Art
import com.miniappfactory.frontlinedefender.game.ui.ArtInset
import com.miniappfactory.frontlinedefender.game.ui.ArtSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * UI ART PACK v2 — SOZLESME TESTI
 * =============================================================================
 *
 * `ArtSurfaces.kt` icindeki [Art] tablosu, `res/drawable-nodpi/` altindaki
 * GERCEK dosyalarin olculerini KOPYALAR. Kopyalanan her sayi bayatlamaya
 * adaydir ve bu deponun en sik hata sinifi tam olarak budur:
 * *"ayni deger iki yerde, biri bayatlar"* (HANDOVER §5, sinif 2).
 *
 * Bu test sayilari SAYMAZ, KURAL yazar:
 *  1. beyan edilen en-boy orani, dosyanin GERCEK pikselinden hesaplananla
 *     ayni midir,
 *  2. ic alan kesirleri kullanilabilir bir kutu birakiyor mu,
 *  3. tabloda adi gecen her dosya diskte VAR MI ve diskteki her `ui_*` dosyasi
 *     tabloda kullaniliyor mu (oksuz varlik = APK'da olu agirlik).
 *
 * WebP basligi ELLE cozuluyor cunku bu bir SAF JVM testi: Robolectric'in
 * `BitmapFactory` golgesi varsayilan `graphicsMode` altinda gercek boyut
 * dondurmez (HANDOVER teknik borc §9), yani Robolectric ile olculen "boyut"
 * bu soruyu cevaplamazdi.
 */
class ArtSurfaceContractTest {

    private val drawableDir: File = run {
        // Birim testleri modul dizininde (`app/`) kosar. Iki aday da denenir ki
        // kok dizinden kosturuldugunda da bulunsun.
        val candidates = listOf(
            File("src/main/res/drawable-nodpi"),
            File("app/src/main/res/drawable-nodpi")
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("drawable-nodpi bulunamadi. Denenen: ${candidates.map { it.absolutePath }}")
    }

    /** Tabloda tanimli her yuzey: ad -> (spec, dosya adi). */
    private val declared: List<Triple<String, ArtSpec, String>> = listOf(
        Triple("HeaderPlate", Art.HeaderPlate, "ui_plate_header.webp"),
        Triple("ActBanner", Art.ActBanner, "ui_plate_act_banner.webp"),
        Triple("PrimaryButton", Art.PrimaryButton, "ui_btn_primary.webp"),
        Triple("SecondaryButton", Art.SecondaryButton, "ui_btn_secondary.webp"),
        Triple("ModalPanel", Art.ModalPanel, "ui_panel_modal.webp"),
        Triple("SegmentedSelector", Art.SegmentedSelector, "ui_selector_dual.webp"),
        Triple("GearIcon", Art.GearIcon, "ui_ic_gear.webp")
    )

    // =========================================================================
    // 1. Beyan edilen oran, dosyanin gercek oraniyla ayni mi
    // =========================================================================

    @Test
    fun declaredAspectMatchesTheActualImageFile() {
        declared.forEach { (name, spec, fileName) ->
            val file = File(drawableDir, fileName)
            assertTrue("$name: dosya yok -> ${file.absolutePath}", file.isFile)

            val (w, h) = readWebPSize(file)
            val actual = w.toFloat() / h.toFloat()

            // Tolerans 0,02: beyan edilen deger iki ondalik basamaga yuvarlaniyor
            // (or. 900/257 = 3,5019 -> 3,50). Gercek bir uyumsuzluk her zaman
            // bundan buyuk cikar; sanat degistiginde oran %1'den fazla oynar.
            assertEquals(
                "$name beyan edilen oran ${spec.aspect} ama $fileName ${w}x$h = $actual. " +
                    "Sanat degistiyse tools/ui_art_pipeline.py yeniden kosturulup " +
                    "Art tablosundaki oran guncellenmeli.",
                actual.toDouble(), spec.aspect.toDouble(), 0.02
            )
        }
    }

    // =========================================================================
    // 2. Ic alan kesirleri kullanilabilir bir kutu birakiyor mu
    // =========================================================================

    @Test
    fun everyInsetLeavesAUsableContentBox() {
        declared.forEach { (name, spec, _) ->
            val i: ArtInset = spec.inset
            listOf("left" to i.left, "top" to i.top, "right" to i.right, "bottom" to i.bottom)
                .forEach { (edge, value) ->
                    assertTrue(
                        "$name.$edge = $value — kesir [0,1) araliginda olmali",
                        value >= 0f && value < 1f
                    )
                }
            // Karsilikli kenarlarin toplami 1'i gecerse icerik kutusu NEGATIF
            // genislige duser ve Compose padding'i cakisir (metin kaybolur).
            // Ust sinir 0,90: geriye en az %10 kullanilabilir alan kalmali,
            // yoksa oraya konan metin zaten okunamaz.
            assertTrue(
                "$name: sol+sag = ${i.left + i.right} — kullanilabilir genislik %10'un altina dusuyor",
                i.left + i.right <= 0.90f
            )
            assertTrue(
                "$name: ust+alt = ${i.top + i.bottom} — kullanilabilir yukseklik %10'un altina dusuyor",
                i.top + i.bottom <= 0.90f
            )
        }
    }

    // =========================================================================
    // 3. Oksuz varlik yok, eksik varlik yok
    // =========================================================================

    @Test
    fun everyUiArtFileOnDiskIsActuallyUsed() {
        val onDisk = drawableDir.listFiles { f -> f.name.startsWith("ui_") && f.extension == "webp" }
            ?.map { it.name }?.toSortedSet() ?: sortedSetOf()

        // Ac/kapa anahtari [Art] tablosunda ArtSpec olarak DEGIL, dogrudan
        // `R.drawable` ile cagriliyor (yalnizca oran gerekiyor, ic alan degil).
        // 55-KART PAKETI (v50) de dogrudan cagrilir: durum sablonlari
        // `LevelCard` icinde `when` ile secilir, yildiz overlay'i sabit
        // bindirmedir — ikisi de ArtSpec'in ic-alan mekanigini kullanmaz.
        val usedDirectly = setOf(
            "ui_toggle_on.webp", "ui_toggle_off.webp",
            "ui_card_active.webp", "ui_card_available.webp",
            "ui_card_completed.webp", "ui_card_locked.webp",
            "ui_card_star.webp",
            // v52: cephanelik sablonu (available'in yildizsiz turevi),
            // UpgradeShopScreen dogrudan cagirir.
            "ui_card_shop.webp"
        )
        val used = (declared.map { it.third } + usedDirectly).toSortedSet()

        val orphans = onDisk - used
        assertTrue(
            "APK'ya giren ama hicbir yerde kullanilmayan sanat dosyasi var: $orphans. " +
                "Ya bir cagri yeri ekleyin ya da dosyayi ve tools/ui_art_pipeline.py " +
                "icindeki uretim satirini silin.",
            orphans.isEmpty()
        )

        val missing = used - onDisk
        assertTrue("Kodun bekledigi ama diskte olmayan sanat dosyasi: $missing", missing.isEmpty())
    }

    /**
     * ⛔ SILINMIS VARLIK NOBETI.
     *
     * `ui_plate_nameplate.webp` bilincli olarak KALDIRILDI: konulabilecegi tek
     * yer olan `UnlockConfirmOverlay`'de, cihazda (Galaxy S8 / API 24)
     * pencerenin iki `Surface` butonunu METINSIZ birakiyordu — yani 120 coin
     * harcayan onay butonu gorunmez oluyordu. Dort ayri bicimde denendi, dordu
     * de ayni sonucu verdi; ayrinti `LevelSelectScreen.kt` icindeki yorum
     * blogunda ve `docs/device_evidence/ui_art_pack_v2/` altinda.
     *
     * Bu test dosyanin GERI GELMESINI engeller. Geri getirilecekse once
     * yukaridaki cihaz kaniti yeniden uretilmeli.
     */
    @Test
    fun theNameplateArtStaysRemovedUntilTheDeviceBugIsUnderstood() {
        val file = File(drawableDir, "ui_plate_nameplate.webp")
        assertTrue(
            "ui_plate_nameplate.webp geri gelmis. Bu varlik cihazda kilit acma " +
                "penceresinin butonlarini gorunmez yapiyordu; geri koymadan once " +
                "docs/device_evidence/ui_art_pack_v2/09_unlock_BROKEN_with_art.png " +
                "kaniti yeniden uretilmeli.",
            !file.exists()
        )
    }

    // =========================================================================
    // WebP basligi — yalnizca boyut
    // =========================================================================

    /**
     * WebP kapsayicisindan (genislik, yukseklik) okur.
     *
     * Iki bicim destekleniyor, ikisi de bu depoda kullaniliyor:
     *  · `VP8X` — alfa tasiyan genisletilmis kapsayici (butun `ui_*` dosyalari),
     *  · `VP8 ` — duz kayipli bicim (`bg_camo`).
     */
    private fun readWebPSize(file: File): Pair<Int, Int> {
        val b = file.readBytes()
        require(b.size > 30) { "${file.name}: dosya cok kisa" }
        require(String(b, 0, 4, Charsets.US_ASCII) == "RIFF") { "${file.name}: RIFF degil" }
        require(String(b, 8, 4, Charsets.US_ASCII) == "WEBP") { "${file.name}: WEBP degil" }

        return when (val chunk = String(b, 12, 4, Charsets.US_ASCII)) {
            "VP8X" -> {
                // 24 bit little-endian, "1 eksik" olarak saklanir.
                val w = (u(b, 24) or (u(b, 25) shl 8) or (u(b, 26) shl 16)) + 1
                val h = (u(b, 27) or (u(b, 28) shl 8) or (u(b, 29) shl 16)) + 1
                w to h
            }
            "VP8 " -> {
                // Anahtar kare basligi: 3 bayt etiket + 3 bayt senkron (9d 01 2a),
                // sonra 14 bit genislik ve 14 bit yukseklik.
                val o = 20 + 3 + 3
                val w = (u(b, o) or (u(b, o + 1) shl 8)) and 0x3FFF
                val h = (u(b, o + 2) or (u(b, o + 3) shl 8)) and 0x3FFF
                w to h
            }
            else -> error("${file.name}: desteklenmeyen WebP parcasi '$chunk'")
        }
    }

    private fun u(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF
}
