package com.miniappfactory.frontlinedefender.game.ui

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.model.GameConfig

/**
 * Faz 3 — sprite deposu.
 *
 * BELLEK KURALI (game-asset-draw skill'i): sprite'lar zaten `drawable-nodpi`
 * altinda OYNANIS BOYUTUNDA hazirlanmis WebP dosyalari. Burada tek is onlari
 * BIR KEZ decode etmek. Kare dongusu icinde decode YAPILMAZ.
 *
 * `inScaled = false`: nodpi klasoru zaten yogunluk olceklemesini kapatir, bu
 * bayrak ayni garantiyi decode tarafinda da verir. Aksi halde sprite mdpi
 * varsayilip xxxhdpi cihazda 4x buyutulur ve hem bulaniklasir hem 16x bellek
 * yer.
 *
 * Olculen toplam bitmap bellegi: sprite'lar ~3.5 MB + harita ~7.9 MB.
 */
class GameSprites private constructor(
    val map: ImageBitmap,
    val towers: Map<GameConfig.TowerType, ImageBitmap>,
    val enemies: Map<GameConfig.EnemyType, ImageBitmap>,
    val muzzleFlash: ImageBitmap,
    val tracer: ImageBitmap,
    val cannonShell: ImageBitmap,
    val missile: ImageBitmap,
    val hitSpark: ImageBitmap,
    val smallExplosion: ImageBitmap,
    val largeExplosion: ImageBitmap,
    val smokePuff: ImageBitmap,
    val buildPad: ImageBitmap,
    val rangeBlue: ImageBitmap,
    val rangeGreen: ImageBitmap
) {
    companion object {
        private fun load(res: Resources, @DrawableRes id: Int): ImageBitmap {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeResource(res, id, opts).asImageBitmap()
        }

        fun create(res: Resources): GameSprites = GameSprites(
            map = load(res, R.drawable.bg_level_01),
            towers = mapOf(
                GameConfig.TowerType.MACHINE_GUN to load(res, R.drawable.spr_tower_machine_gun),
                GameConfig.TowerType.CANNON to load(res, R.drawable.spr_tower_heavy_cannon),
                GameConfig.TowerType.ANTI_ARMOR to load(res, R.drawable.spr_tower_missile_launcher),
                GameConfig.TowerType.SLOW to load(res, R.drawable.spr_tower_energy_slow)
            ),
            enemies = mapOf(
                GameConfig.EnemyType.INFANTRY to load(res, R.drawable.spr_enemy_infantry),
                GameConfig.EnemyType.FAST_SOLDIER to load(res, R.drawable.spr_enemy_fast_soldier),
                GameConfig.EnemyType.ARMORED_VEHICLE to load(res, R.drawable.spr_enemy_jeep),
                GameConfig.EnemyType.TANK to load(res, R.drawable.spr_enemy_tank),
                // ------------------------------------------------------------
                // Faz 4 / DECISIONS B1: YENI PNG YOK. Iki yeni tip mevcut
                // bitmap'leri PAYLASIR (decode edilmis bitmap tekrar
                // kullanildigi icin ek bellek maliyeti yok).
                //
                // Bu iki satir olmadan GameCanvas.drawEnemy'deki
                // `sprites.enemies[enemy.type] ?: return` erken donuyor ve
                // SHIELDED_TROOPER / COMMAND_TANK **hic cizilmiyor** — dusman
                // gorunmez sekilde usse yuruyor. L9'dan sonraki her bolum
                // oynanamaz hale gelirdi.
                //
                // Tint + kalkan halkasi / komuta flamasi cizimi hâlâ eksik;
                // verisi GameConfig.ENEMY_SPRITES icinde hazir
                // (baseSprite / tintArgb / overlayBadge).
                // Bkz. docs/CAMPAIGN_INTEGRATION.md "GameCanvas'ta yapilacak".
                // ------------------------------------------------------------
                GameConfig.EnemyType.SHIELDED_TROOPER to load(res, R.drawable.spr_enemy_infantry),
                GameConfig.EnemyType.COMMAND_TANK to load(res, R.drawable.spr_enemy_tank)
            ),
            muzzleFlash = load(res, R.drawable.spr_fx_muzzle_flash),
            tracer = load(res, R.drawable.spr_fx_tracer),
            cannonShell = load(res, R.drawable.spr_fx_cannon_shell),
            missile = load(res, R.drawable.spr_fx_missile),
            hitSpark = load(res, R.drawable.spr_fx_hit_spark),
            smallExplosion = load(res, R.drawable.spr_fx_small_explosion),
            largeExplosion = load(res, R.drawable.spr_fx_large_explosion),
            smokePuff = load(res, R.drawable.spr_fx_smoke_puff),
            buildPad = load(res, R.drawable.spr_fx_build_pad),
            rangeBlue = load(res, R.drawable.spr_fx_range_blue),
            rangeGreen = load(res, R.drawable.spr_fx_range_green)
        )
    }
}

/** Decode BIR KEZ; composition boyunca ayni ornek kullanilir. */
@Composable
fun rememberGameSprites(): GameSprites {
    val res = LocalContext.current.resources
    return remember(res) { GameSprites.create(res) }
}
