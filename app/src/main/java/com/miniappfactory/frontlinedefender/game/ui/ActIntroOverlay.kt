package com.miniappfactory.frontlinedefender.game.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.ui.theme.SleekGold

/**
 * PERDE ACILIS KARTI — kampanyanin tek "sayfa" anlati yuzeyi.
 *
 * Tasarim: `docs/STORY.md` §4. Kart bes perdenin ILK bolumune (L1 / L12 /
 * L23 / L34 / L45) girerken **bir kez** cizilir, **atlanabilir** ve
 * **oynanisi DURDURMAZ**.
 *
 * ---------------------------------------------------------------------------
 * NEDEN SAVAS ICINDE DEGIL, BOLUM SECIMDE
 * ---------------------------------------------------------------------------
 * `STORY.md` §0 Y2 yasagi: anlati hicbir yerde savasi duraklatmaz, dalga
 * zamanlamasina veya hazirlik sayacina degmez. Kart, bolum kartina
 * dokunuldugu anda — savas HENUZ BASLAMADAN — cizilir; onaylandiginda
 * `onPlayLevel` cagrilir. Boylece "duraklatmayan kart" bir davranis sozu
 * degil, YAPISAL bir garantidir: kartin gorundugu anda calisan bir oyun
 * dongusu yoktur.
 *
 * ---------------------------------------------------------------------------
 * KALICILIK — SaveManager DEGISTIRILMEDI
 * ---------------------------------------------------------------------------
 * `SaveManager` bu turda kapaliydi. Gerek de kalmadi: dosyada zaten genel
 * amacli, DIZEYLE anahtarlanan tek atislik bir bayrak API'si var
 * (`isHintSeen` / `markHintSeen`, anahtar oneki `hint_seen_`). Kart bunu
 * `act_intro_1` … `act_intro_5` kimlikleriyle kullanir.
 *
 * O API'nin bu is icin dogru olmasinin sebepleri (hepsi `SaveManager`in kendi
 * gerekcesi): ayrik anahtar (bit maskesi degil) -> perde eklenince eski
 * kayitlarda YANLIS perde "gorulmus" isaretlenemez; tohumlama yok -> mevcut
 * kayitlar da karti gorur; bos kimlikte `true` -> cagirandaki bir hata karti
 * en kotu ihtimalle SUSTURUR, oyuncuyu her acilista ayni karti gormeye
 * mahkum ETMEZ.
 *
 * Bilinen yan etki (kabul edildi, `STORY.md` §6): Ayarlar > "Ipuclarini
 * sifirla" onek taramasi yaptigi icin perde kartlarini da sifirlar. Bu
 * istenir — QA kartlari tek dugmeyle geri getirebilir.
 */

// ---------------------------------------------------------------------------
// Perde aritmetigi — 5 perde x 11 bolum
// ---------------------------------------------------------------------------

/**
 * Kampanyanin perde yapisi.
 *
 * `GameConfig.LevelSpec.act` ile AYNI formulu kullanir (`(levelId - 1) / 11 + 1`)
 * ama `GameConfig`e BAGIMLI DEGILDIR: bu nesne saf aritmetiktir, Android
 * kaynagi da okumaz, bu yuzden Robolectric'siz birim testiyle kilitlenebilir.
 * Iki tarafin ayni cevabi verdigi `ActIntroTest` icinde ayrica dogrulanir.
 */
object ActIntro {

    /** Perde basina bolum sayisi (CAMPAIGN_55.md K1). */
    const val LEVELS_PER_ACT = 11

    /** Kampanyadaki perde sayisi. */
    const val ACT_COUNT = 5

    /** Bolumun perdesi. Aralik disi bolum id'si en yakin gecerli perdeye kirpilir. */
    fun actOf(levelId: Int): Int =
        ((levelId - 1) / LEVELS_PER_ACT + 1).coerceIn(1, ACT_COUNT)

    /**
     * Bu bolum bir perdenin ILK bolumu mu? (1, 12, 23, 34, 45)
     *
     * Kampanya disindaki bir id icin `false` — kart yalnizca gercek bir perde
     * acilisinda cikar.
     */
    fun isActOpener(levelId: Int): Boolean =
        levelId >= 1 &&
            levelId <= ACT_COUNT * LEVELS_PER_ACT &&
            (levelId - 1) % LEVELS_PER_ACT == 0

    /** Perdenin ilk bolumu. */
    fun firstLevelOf(act: Int): Int =
        (act.coerceIn(1, ACT_COUNT) - 1) * LEVELS_PER_ACT + 1

    /** Perdenin son bolumu. */
    fun lastLevelOf(act: Int): Int = firstLevelOf(act) + LEVELS_PER_ACT - 1

    /** `SaveManager` ipucu bayragi kimligi. Kalici; DEGISTIRILEMEZ. */
    fun hintId(act: Int): String = "act_intro_${act.coerceIn(1, ACT_COUNT)}"
}

// ---------------------------------------------------------------------------
// Kalicilik yuzeyi
// ---------------------------------------------------------------------------

/**
 * "Bu perdenin karti gosterildi mi?" — tek atislik bayrak.
 *
 * Arayuz ayri tutuldu ki testler `SaveManager`a (dolayisiyla Android
 * `Context`ine) ihtiyac duymadan kartin bir-kez-gosterim davranisini
 * dogrulayabilsin.
 */
interface ActIntroStore {
    fun isSeen(act: Int): Boolean
    fun markSeen(act: Int)
}

/** Uretim implementasyonu — mevcut ipucu bayrak API'sinin uzerine oturur. */
class SaveManagerActIntroStore(
    private val saveManager: SaveManager
) : ActIntroStore {
    override fun isSeen(act: Int): Boolean = saveManager.isHintSeen(ActIntro.hintId(act))
    override fun markSeen(act: Int) = saveManager.markHintSeen(ActIntro.hintId(act))
}

// ---------------------------------------------------------------------------
// Metin eslemeleri
// ---------------------------------------------------------------------------

/**
 * Perde seridi etiketi (KISIM I … KISIM V).
 *
 * `strings.xml`deki `level_act_1` / `level_act_2` yalnizca IKI perde tanidigi
 * icin Act III-V hepsi "KISIM II" olarak ciziliyordu — 55 bolumun 33'u yanlis
 * etiketliydi. Bes etiketin tek kaynagi artik `strings_story.xml`.
 */
@StringRes
fun storyActLabelRes(act: Int): Int = when (act.coerceIn(1, ActIntro.ACT_COUNT)) {
    1 -> R.string.story_act_label_1
    2 -> R.string.story_act_label_2
    3 -> R.string.story_act_label_3
    4 -> R.string.story_act_label_4
    else -> R.string.story_act_label_5
}

@StringRes
fun storyActTitleRes(act: Int): Int = when (act.coerceIn(1, ActIntro.ACT_COUNT)) {
    1 -> R.string.story_act_1_title
    2 -> R.string.story_act_2_title
    3 -> R.string.story_act_3_title
    4 -> R.string.story_act_4_title
    else -> R.string.story_act_5_title
}

@StringRes
fun storyActBodyRes(act: Int): Int = when (act.coerceIn(1, ActIntro.ACT_COUNT)) {
    1 -> R.string.story_act_1_body
    2 -> R.string.story_act_2_body
    3 -> R.string.story_act_3_body
    4 -> R.string.story_act_4_body
    else -> R.string.story_act_5_body
}

/**
 * Bolum durum satiri ("Gecidi tut.") — bolum kartinda adin ALTINDA tek satir.
 *
 * Dizi indeksi `levelId - 1`. Kampanya disindaki bir id (veya diziden kisa
 * bir cevirinin sizmasi) bos dize dondurur; cagiran taraf bos satiri
 * CIZMEZ. Boylece eksik bir ceviri kartin yerlesimini bozmak yerine
 * yalnizca bir satiri yok eder.
 */
@Composable
fun levelObjectiveOrEmpty(levelId: Int): String {
    val lines = stringArrayResource(R.array.story_objectives)
    val index = levelId - 1
    return if (index in lines.indices) lines[index] else ""
}

// ---------------------------------------------------------------------------
// Kart
// ---------------------------------------------------------------------------

/**
 * Perde acilis karti.
 *
 * Iki kapanis yolu: onay cipi ("ANLASILDI") ve scrim'e dokunma. Ikisi de
 * [onDismiss] cagirir — kart bir KAPI degil, bir GECIS ekranidir; oyuncuyu
 * hicbir kosulda savasa girmekten alikoymaz.
 *
 * Scrim tiklamasi `indication = null` ile kurulur: tam ekran bir yuzeyde
 * dalgalanma (ripple) efekti ekranin tamamini yalayip gecer ve "yanlisliga
 * bastim" hissi verir.
 */
@Composable
fun ActIntroOverlay(
    act: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeAct = act.coerceIn(1, ActIntro.ACT_COUNT)
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF20A0E07))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss
            )
            .testTag("act_intro_overlay")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1B2416))
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            // KISIM III — serit ayracindaki etiketin ta kendisi; oyuncu
            // karti bolum seridiyle ayni kelimeyle esler.
            Text(
                text = stringResource(storyActLabelRes(safeAct)),
                color = Color(0xFF8FA87A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )

            Spacer(Modifier.height(4.dp))

            AutoShrinkText(
                text = stringResource(storyActTitleRes(safeAct)),
                color = SleekGold,
                fontWeight = FontWeight.Black,
                maxFontSize = 24.sp,
                minFontSize = 16.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = stringResource(
                    R.string.story_act_range,
                    ActIntro.firstLevelOf(safeAct),
                    ActIntro.lastLevelOf(safeAct)
                ),
                color = Color(0x99C5D6B4),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(Modifier.height(12.dp))

            // Govde. `maxLines` YOK: metin bir cihazda 6 satira tastiginda
            // kirpmak, telsiz emrinin son cumlesini — yani perdenin GOREVINI
            // — yok eder. Butce `StoryStringsTest` ile metin tarafinda
            // kilitli (220 karakter); burada kirpma yerine akmasina izin
            // verilir.
            Text(
                text = stringResource(storyActBodyRes(safeAct)),
                color = Color(0xFFDCE8CC),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.story_ack),
                color = Color(0xFF14200C),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SleekGold)
                    // Dokunma hedefi: 13 sp metin + 2x11 dp dikey padding
                    // ~ 48 dp. Erisilebilirlik tabani korunur.
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 28.dp, vertical = 11.dp)
                    .testTag("act_intro_ack")
            )
        }
    }
}
