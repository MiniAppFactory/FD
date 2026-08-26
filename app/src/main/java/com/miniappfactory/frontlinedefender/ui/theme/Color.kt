package com.miniappfactory.frontlinedefender.ui.theme

import androidx.compose.ui.graphics.Color

val SleekDarkBg = Color(0xFF1B1C17)
val SleekSurfaceHeader = Color(0xFF24261E)
val SleekSurfaceCard = Color(0xFF2D3126)
val SleekBorderLight = Color(0xFF494D3F)
val SleekBorderDark = Color(0xFF3E4236)

val SleekPrimaryGreen = Color(0xFF386A20)
val SleekTextAccent = Color(0xFFD9E7CB)

val SleekGold = Color(0xFFFFD700)
val SleekGoldBg = Color(0xFF432A00)
val SleekGoldBorder = Color(0xFF6B4400)

val SleekRed = Color(0xFFBA1A1A)
val SleekRedBg = Color(0xFF410E0B)
val SleekRedBorder = Color(0xFF930006)
val SleekRedText = Color(0xFFFFDAD6)


// -----------------------------------------------------------------------------
// UI ART PACK v2 TOKEN'LARI
//
// Kaynak: asset-pack/Frontline_Defender_assets_individual_full/tokens/ui_tokens.json
//
// Yukaridaki `Sleek*` paleti YERINDE KALIYOR ve degistirilmedi: oynanis HUD'i,
// kartlar ve diyaloglar onu kullaniyor ve calisan bir tasarimi yeni sanat
// geldi diye elden gecirmek istemiyoruz. Bu grup yalnizca YENI sanat
// yuzeylerinin (plaka/buton/panel) uzerine cizilen metin ve vurgu renkleridir;
// sanatin kendi renk sicakligiyla uyumlu olmasi icin ayri tutuldu.
// -----------------------------------------------------------------------------

/** Sanat plakalari uzerindeki birincil yazi — sicak acik gri. */
val ArtTextPrimary = Color(0xFFE8E3D2)

/** Ikincil / aciklama yazisi — sonuk zeytin gri. */
val ArtTextSecondary = Color(0xFFA7AF92)

/** En sonuk yazi (ipucu, birim etiketi). */
val ArtTextDim = Color(0xFF7C826D)

/** Altin vurgu — odul, bolum numarasi, perde etiketi. */
val ArtAccentGold = Color(0xFFD8A52A)
val ArtAccentGoldGlow = Color(0xFFF1C95D)

/** Yesil vurgu — olumlu durum, tamamlanan bolum. */
val ArtAccentGreenGlow = Color(0xFFA7D94E)

/** Kirmizi vurgu — yenilgi, yikici eylem. */
val ArtAccentRedGlow = Color(0xFFDB5C48)

// ⚠ `ArtOnBright` (koyu yazi rengi) KALDIRILDI — dayandigi varsayim CIHAZDA
// YANLIS CIKTI.
//
// Varsayim: "birincil buton ve secili segment sanati parlak sari-yesil, oraya
// koyu yazi gerekir". Galaxy S8'de olculen gercek: sanatin parlak kismi
// yalnizca KENAR ISILTISI; yazinin oturdugu ic alan koyu zeytin.
//     birincil buton ic alani  RGB(44, 62, 7)  -> koyu yaziyla **1,58:1**
//     secili segment ic alani  RGB(70, 85, 7)  -> koyu yaziyla **2,31:1**
// Ikisi de WCAG AA tabanini (4,5:1) acik farkla ihlal ediyordu.
//
// Ayni alanlarda [ArtTextPrimary] 6,6-9,1:1 veriyor. Secili/secili degil
// ayrimi renk PARLAKLIGIYLA degil, sanatin kendi kenar isiltisiyla ve
// [ArtTextPrimary] / [ArtTextSecondary] farkiyla tasiniyor.
//
// Ders: kontrast, sanata BAKARAK degil, CIZILEN PIKSELDEN olculur.
