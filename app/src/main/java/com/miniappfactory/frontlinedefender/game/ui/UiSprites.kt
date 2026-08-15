package com.miniappfactory.frontlinedefender.game.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.model.GameConfig

/**
 * Faz 3 — asset pack UI ikonlari. Material vektor ikonlarinin yerine oyunun
 * kendi sanat diliyle cizilmis raster ikonlar kullanilir; boylece HUD ile
 * savas alani ayni gorsel dile ait olur.
 *
 * Ikonlar `drawable-nodpi` altinda 96 px (yildiz/kurukafa 128 px) genisliginde
 * hazirlandi; burada yalnizca dp cinsinden olceklenirler.
 */
@Composable
fun SpriteIcon(
    @DrawableRes id: Int,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}

/** Kule tipinin oynanista gorunen sprite'i — insa kartinda da AYNISI gosterilir. */
@DrawableRes
fun towerSpriteRes(type: GameConfig.TowerType): Int = when (type) {
    GameConfig.TowerType.MACHINE_GUN -> R.drawable.spr_tower_machine_gun
    GameConfig.TowerType.CANNON -> R.drawable.spr_tower_heavy_cannon
    GameConfig.TowerType.ANTI_ARMOR -> R.drawable.spr_tower_missile_launcher
    GameConfig.TowerType.SLOW -> R.drawable.spr_tower_energy_slow
}
