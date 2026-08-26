package com.miniappfactory.frontlinedefender.game.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.model.GameConfig

/**
 * Faz 6 — LOKALIZASYON KOPRUSU.
 *
 * `GameConfig`'e DOKUNULMADI. Oradaki Ingilizce `name` / `description` alanlari
 * kullanilmayan fallback olarak durur; oyuncunun gordugu her metin buradaki
 * eslemeler uzerinden `strings.xml` / `values-tr/strings.xml`'den gelir.
 *
 * `when` dallarinda **bilincli olarak `else` YOKTUR**: `TowerType` veya
 * `EnemyType`'a yeni bir deger eklenirse derleme KIRILIR ve ceviri unutulamaz.
 * (Simdiki durum: 4 kule, 6 dusman, 4 hedefleme modu.)
 *
 * Dosya ayrica `AutoShrinkText`'i barindirir — tasma cozumu icin gereken tek
 * yardimci ve Faz 6 kapsaminda yeni dosya acmaya izinli tek yer burasi.
 */

// ---------------------------------------------------------------------------
// Kule
// ---------------------------------------------------------------------------

@StringRes
fun GameConfig.TowerType.nameRes(): Int = when (this) {
    GameConfig.TowerType.MACHINE_GUN -> R.string.tower_machine_gun_name
    GameConfig.TowerType.CANNON -> R.string.tower_cannon_name
    // KIMLIK: sprite spr_tower_missile_launcher + ses sfx_missile_launch ->
    // gorunen ad "Missile Battery" / "Fuze Rampasi". Enum adi ANTI_ARMOR kalir.
    GameConfig.TowerType.ANTI_ARMOR -> R.string.tower_anti_armor_name
    GameConfig.TowerType.SLOW -> R.string.tower_slow_name
}

@StringRes
fun GameConfig.TowerType.descRes(): Int = when (this) {
    GameConfig.TowerType.MACHINE_GUN -> R.string.tower_machine_gun_desc
    GameConfig.TowerType.CANNON -> R.string.tower_cannon_desc
    GameConfig.TowerType.ANTI_ARMOR -> R.string.tower_anti_armor_desc
    GameConfig.TowerType.SLOW -> R.string.tower_slow_desc
}

// ---------------------------------------------------------------------------
// Dusman
// ---------------------------------------------------------------------------

@StringRes
fun GameConfig.EnemyType.nameRes(): Int = when (this) {
    GameConfig.EnemyType.INFANTRY -> R.string.enemy_infantry_name
    GameConfig.EnemyType.FAST_SOLDIER -> R.string.enemy_fast_soldier_name
    GameConfig.EnemyType.ARMORED_VEHICLE -> R.string.enemy_armored_vehicle_name
    GameConfig.EnemyType.TANK -> R.string.enemy_tank_name
    GameConfig.EnemyType.SHIELDED_TROOPER -> R.string.enemy_shielded_trooper_name
    GameConfig.EnemyType.COMMAND_TANK -> R.string.enemy_command_tank_name
}

// ---------------------------------------------------------------------------
// Hedefleme modu
// ---------------------------------------------------------------------------

@StringRes
fun GameConfig.TargetingMode.labelRes(): Int = when (this) {
    GameConfig.TargetingMode.FIRST -> R.string.inspector_target_first
    GameConfig.TargetingMode.LAST -> R.string.inspector_target_last
    GameConfig.TargetingMode.STRONGEST -> R.string.inspector_target_strongest
    GameConfig.TargetingMode.WEAKEST -> R.string.inspector_target_weakest
}

// ---------------------------------------------------------------------------
// Harita adlari
//
// `LevelGeometry.kt` icindeki adlar uretilmis dosyada ASCII-bozuk Turkce
// ("Cayir Gecidi / Meadow Pass") ve oyle KALIYOR. Oyuncunun gordugu ad
// buradan gelir. `mapId` bir Int oldugu icin `else` zorunlu; eksik bitmap'te
// motorun dustugu `GameConfig.MAP_FALLBACK_ID` ile ayni haritaya duser.
// ---------------------------------------------------------------------------

@StringRes
fun mapNameRes(mapId: Int): Int = when (mapId) {
    1 -> R.string.map_name_01
    2 -> R.string.map_name_02
    3 -> R.string.map_name_03
    4 -> R.string.map_name_04
    5 -> R.string.map_name_05
    6 -> R.string.map_name_06
    7 -> R.string.map_name_07
    8 -> R.string.map_name_08
    9 -> R.string.map_name_09
    10 -> R.string.map_name_10
    11 -> R.string.map_name_11
    else -> R.string.map_name_01
}

/**
 * Bolum kartinda gosterilen HARITA KUPURU.
 *
 * Savas alani arka planinin (`bg_level_NN`, 1920x1081) KENDISI DEGIL, ondan
 * uretilmis 320x132'lik kupur. Sebep bellek: bolum seridi `horizontalScroll`
 * (lazy degil), yani 55 kart ayni anda besteleniyor ve 11 benzersiz bitmap'in
 * tamami bellege giriyor. Tam boy kullanilsaydi 11 x 7,92 MB = **87 MB**;
 * kupurle **1,86 MB**. Galaxy S8'de aradaki fark OOM ile calisan uygulama
 * arasindaki fark.
 *
 * Uretici: `tools/ui_art_pipeline.py` (`build_level_thumbs`).
 *
 * Biyom recolor'i kupurlere UYGULANMAZ: kart zaten "GECE" rozetiyle biyomu
 * soyluyor ve 11 kupur yerine 55 kupur uretmek bellek kazancini geri verirdi.
 */
@DrawableRes
fun levelThumbRes(levelId: Int): Int {
    // 55-KART PAKETI (2026-08-26): kupurler artik LEVELID bazli ve BIYOMLU —
    // ayni harita kista karli, colde kumlu goruntusuyle gelir (55/55 benzersiz,
    // md5 ile dogrulandi). Eski 11'lik harita-bazli uretim pipeline'da devre
    // disi birakildi.
    //
    // when tablosu YOK: 55 satirlik elle yazilmis tablo bayatlamaya davetiye
    // olurdu. Ad, kimlikten TURETILIR; kaynak adlari uretici betikle ayni
    // kaliptan (thumb_level_NN) geldigi icin kopukluk derleme zamaninda degil
    // calisma zamaninda da olusamaz — getIdentifier degil, sabit dizi.
    val safe = levelId.coerceIn(1, LEVEL_THUMBS.size)
    return LEVEL_THUMBS[safe - 1]
}

private val LEVEL_THUMBS = intArrayOf(
    R.drawable.thumb_level_01, R.drawable.thumb_level_02, R.drawable.thumb_level_03,
    R.drawable.thumb_level_04, R.drawable.thumb_level_05, R.drawable.thumb_level_06,
    R.drawable.thumb_level_07, R.drawable.thumb_level_08, R.drawable.thumb_level_09,
    R.drawable.thumb_level_10, R.drawable.thumb_level_11, R.drawable.thumb_level_12,
    R.drawable.thumb_level_13, R.drawable.thumb_level_14, R.drawable.thumb_level_15,
    R.drawable.thumb_level_16, R.drawable.thumb_level_17, R.drawable.thumb_level_18,
    R.drawable.thumb_level_19, R.drawable.thumb_level_20, R.drawable.thumb_level_21,
    R.drawable.thumb_level_22, R.drawable.thumb_level_23, R.drawable.thumb_level_24,
    R.drawable.thumb_level_25, R.drawable.thumb_level_26, R.drawable.thumb_level_27,
    R.drawable.thumb_level_28, R.drawable.thumb_level_29, R.drawable.thumb_level_30,
    R.drawable.thumb_level_31, R.drawable.thumb_level_32, R.drawable.thumb_level_33,
    R.drawable.thumb_level_34, R.drawable.thumb_level_35, R.drawable.thumb_level_36,
    R.drawable.thumb_level_37, R.drawable.thumb_level_38, R.drawable.thumb_level_39,
    R.drawable.thumb_level_40, R.drawable.thumb_level_41, R.drawable.thumb_level_42,
    R.drawable.thumb_level_43, R.drawable.thumb_level_44, R.drawable.thumb_level_45,
    R.drawable.thumb_level_46, R.drawable.thumb_level_47, R.drawable.thumb_level_48,
    R.drawable.thumb_level_49, R.drawable.thumb_level_50, R.drawable.thumb_level_51,
    R.drawable.thumb_level_52, R.drawable.thumb_level_53, R.drawable.thumb_level_54,
    R.drawable.thumb_level_55
)

/** Kupurun en-boy orani (320/132). Kart yuksekligi bundan turer. */
const val LEVEL_THUMB_ASPECT = 2.424f

/**
 * Perde etiketi (KISIM I … KISIM V).
 *
 * ⛔ ESKI HALI IKI PERDE TANIYORDU (`if (act <= 1) level_act_1 else level_act_2`)
 * ve kampanya 22'den 55 bolume cikinca **Act III, IV ve V hepsi "KISIM II"**
 * olarak cizilmeye basladi — 55 bolumun 33'unde yanlis etiket. Bes etiketin
 * tek kaynagi artik `strings_story.xml`; esleme `ActIntroOverlay.kt` icinde,
 * perde acilis kartiyla AYNI dizeleri kullanir ki serit ile kart ayni kelimeyi
 * soylesin.
 *
 * `strings.xml`deki `level_act_1` / `level_act_2` bilincli olarak YERINDE
 * birakildi (o dosya bu turda kapaliydi); artik kullanilmiyorlar.
 */
@StringRes
fun actLabelRes(act: Int): Int = storyActLabelRes(act)

// ---------------------------------------------------------------------------
// TASMA COZUMU
// ---------------------------------------------------------------------------

/**
 * Sabit boyutlu kap icinde **kesilmeden** sigan metin.
 *
 * Neden `maxLines` + `TextOverflow.Ellipsis` yetmiyor: Turkce karsiliklar
 * Ingilizceden %20-35 uzun ve bu ekranlardaki metinlerin cogu 6-12 karakterlik
 * ETIKET ("KILIDI AC 350", "EN GUCLU", "Fuze Rampasi"). Ellipsis bu uzunlukta
 * kelimenin yarisini yiyor -> "KILIDI A…" anlamsiz. Cozum: metni kismak yerine
 * **olcegi** kucultmek.
 *
 * Calisma sekli: metin `maxFontSize` ile cizilir; `onTextLayout` tasma
 * bildirdikce punto `stepSp` kadar dusurulur ve `minFontSize`'da durur. Metin
 * sonradan UZARSA (ornegin imha sayaci 9 -> 100) dongu yeniden tetiklenir;
 * punto monoton azalir.
 *
 * CIHAZDA OGRENILEN IKI TUZAK (docs/LOCALIZATION.md) — ikisi de burada
 * bilincli olarak cozuldu, "duzeltme" diye geri alinmamali:
 *
 * 1. **`softWrap = false` KULLANILMAZ.** Compose'un `TextDelegate`'i softWrap
 *    kapaliyken paragrafi SINIRSIZ genislikte olcer; `didOverflowWidth` o
 *    zaman metin sigsa bile true doner ve etiket gereksizce en kucuk puntoya
 *    iner. softWrap acikken tek satir siniri `didExceedMaxLines` uzerinden
 *    `didOverflowHeight`'a yansir — dogru ve guvenilir sinyal budur.
 *
 * 2. **Olcum bitene kadar cizimi bastirmak (drawWithContent kapisi) YASAK.**
 *    `onTextLayout` LAYOUT fazinda kosar; oradan yazilan bir state'i DRAW
 *    fazinda okumak, bolum secme gibi kendiliginden kare uretmeyen DURAGAN
 *    ekranlarda son gecersiz kilmanin kaybolmasina yol acti ve metin kalici
 *    olarak GORUNMEZ kaldi (yer ayrildi, piksel cizilmedi — bkz.
 *    docs/device_evidence/faz6_tr_01_mainmenu.png ilk cekim). Cizim her zaman
 *    yapilir; en kotu durum kucultme oncesi tek karelik buyuk gorunumdur.
 *
 * @param resetKey punto hesabinin sifirlanma anahtari. Varsayilan `text`
 *   (statik etiketler icin dogru). Icinde surekli degisen sayi olan metinlerde
 *   her karede sifirlanmamasi icin kararli bir deger gecin (or. kule tipi).
 */
@Composable
fun AutoShrinkText(
    text: String,
    color: Color,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = maxFontSize * 0.70f,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    resetKey: Any? = text,
    stepSp: Float = 0.5f
) {
    var fontSize by remember(resetKey, maxFontSize) { mutableStateOf(maxFontSize) }

    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = maxLines,
        textAlign = textAlign,
        // Ellipsis DEGIL: tasmayi punto ile cozuyoruz, kirparak degil.
        overflow = TextOverflow.Clip,
        modifier = modifier,
        onTextLayout = { result ->
            val overflows = result.didOverflowWidth || result.didOverflowHeight
            if (overflows && fontSize.value - stepSp >= minFontSize.value) {
                fontSize = (fontSize.value - stepSp).sp
            }
        }
    )
}
