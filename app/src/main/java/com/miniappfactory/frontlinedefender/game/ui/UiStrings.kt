package com.miniappfactory.frontlinedefender.game.ui

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
