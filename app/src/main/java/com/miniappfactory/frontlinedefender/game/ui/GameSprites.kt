package com.miniappfactory.frontlinedefender.game.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.model.Biome
import com.miniappfactory.frontlinedefender.game.model.BiomeRecolor
import com.miniappfactory.frontlinedefender.game.model.BiomeSlotCache
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val towers: Map<GameConfig.TowerType, ImageBitmap>,
    val enemies: Map<GameConfig.EnemyType, ImageBitmap>,
    val muzzleFlash: ImageBitmap,
    val tracer: ImageBitmap,
    val cannonShell: ImageBitmap,
    val missile: ImageBitmap,
    val hitSpark: ImageBitmap,
    val smallExplosion: ImageBitmap,
    val largeExplosion: ImageBitmap,
    /**
     * HAVA TAARRUZU UCAGI. Sprite BURNU +X'e bakacak sekilde hazirlandi,
     * yani cizerken taban aci duzeltmesi GEREKMEZ — dogrudan ucus acisi
     * verilir. Kaynak gorsel tepeden bakis ve burnu asagi bakiyordu; 90
     * derece cevrilip patlama/nisangah kompozisyonundan ayiklandi.
     */
    val airStrikeJet: ImageBitmap,
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

        /**
         * Faz 4b: 11 harita arkaplani. Bir harita 1920x1081 ARGB_8888 = **8.3 MB
         * bellek**, 11'i birden tutmak 91 MB eder ve API 24 cihazda OOM olur.
         * Bu yuzden harita GameSprites'a DAHIL DEGIL: `rememberMapBitmap()` ile
         * yalnizca aktif bolumun haritasi decode edilir, bolum degisince eskisi
         * cop toplamaya birakilir.
         */
        @DrawableRes
        fun mapResFor(mapId: Int): Int = when (mapId.coerceIn(GameConfig.MAP_ID_MIN, GameConfig.MAP_ID_MAX)) {
            1 -> R.drawable.bg_level_01
            2 -> R.drawable.bg_level_02
            3 -> R.drawable.bg_level_03
            4 -> R.drawable.bg_level_04
            5 -> R.drawable.bg_level_05
            6 -> R.drawable.bg_level_06
            7 -> R.drawable.bg_level_07
            8 -> R.drawable.bg_level_08
            9 -> R.drawable.bg_level_09
            10 -> R.drawable.bg_level_10
            else -> R.drawable.bg_level_11
        }

        /**
         * Faz 11 — BIYOM VARYANTI (docs/BIOME_VARIANTS.md).
         *
         * Taban haritayi decode eder ve gerekiyorsa bolume ait biyomu YERINDE
         * uygular. `Biome.ORIGINAL` icin tek bir piksele bile dokunulmaz.
         *
         * BLOKLAMAZ demek DEGIL: bu fonksiyon senkron ve pahalidir (~2.08M
         * piksel). Cagiran taraf `Dispatchers.Default` uzerinde calistirmak
         * ZORUNDADIR — `rememberMapBitmap` bunu yapiyor.
         *
         * `inMutable = true`: recolor sonucunu ayni bitmap'e geri yaziyoruz,
         * boylece ikinci bir 8.3 MB'lik bitmap tahsis edilmiyor. Bazi decoder
         * yollarinda `inMutable` yok sayilabildigi icin `isMutable` kontrolu
         * ve yedek yol var.
         */
        internal fun loadBackground(res: Resources, mapId: Int, biome: Biome): ImageBitmap {
            BiomeBackgroundCache.get(mapId, biome)?.let { return it }

            // ----------------------------------------------------------------
            // TEPE BELLEK DUZELTMESI (olculdu, bkz. docs/PERFORMANCE_REPORT.md)
            //
            // Onbellek TEK YUVALI ve `put` en SONDA yapiliyordu. Yani eski
            // bolumun 8.302.080 baytlik bitmap'i, YENI bitmap decode edilip
            // recolor edilirken hâlâ erisilebilir kaliyordu. Bolum girisindeki
            // ayni-an tepesi soyleydi:
            //
            //   eski bitmap   8.302.080
            //   yeni bitmap   8.302.080
            //   getPixels     8.302.080   (IntArray)
            //   bitki maskesi 2.075.520   (ByteArray)
            //   blur tamponu  2.075.520   (ByteArray)
            //   ------------------------------------
            //   TEPE         29.057.280 bayt = 27,71 MiB
            //
            // Yuva burada bosaltilinca tepe 20.755.200 bayt = 19,79 MiB'ye
            // duser (-%28,6). minSdk 24 oldugu icin bu KRITIK: Android 8.0'dan
            // ONCE bitmap pikselleri **Java heap'inde** tutulur, yani bu
            // sayilar dogrudan ART heap limitine yazilir.
            //
            // ISABET ORANI DEGISMEZ: yuva zaten bu `put` ile uzerine
            // yazilacakti. Degisen tek sey eskisinin BIRAKILMA ANI. Ayni
            // (mapId, biome) icin gelinseydi yukaridaki erken donus calisir ve
            // buraya hic inilmezdi.
            // ----------------------------------------------------------------
            BiomeBackgroundCache.clear()

            val t0 = SystemClock.elapsedRealtimeNanos()
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = !biome.isIdentity
            }
            val decoded = BitmapFactory.decodeResource(res, mapResFor(mapId), opts)
            val tDecode = SystemClock.elapsedRealtimeNanos()

            val result = if (biome.isIdentity) decoded else recolor(decoded, biome)
            val tDone = SystemClock.elapsedRealtimeNanos()

            // CIHAZDA OLCUM ICIN: sure + bitmap boyutu + o andaki Java heap
            // kullanimi tek satirda. `adb logcat -s BiomeBackground` bu satiri
            // 55 bolumun tamami icin toplayabilir; heap sutunu uzun oturumda
            // birikme olup olmadigini gosterir (bkz. PERFORMANCE_REPORT).
            val rt = Runtime.getRuntime()
            val heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576f
            val heapMaxMb = rt.maxMemory() / 1_048_576f
            Log.i(
                BG_TAG,
                "map=$mapId biome=$biome ${result.width}x${result.height} " +
                    "decode=${(tDecode - t0) / 1_000_000}ms " +
                    "recolor=${(tDone - tDecode) / 1_000_000}ms " +
                    "toplam=${(tDone - t0) / 1_000_000}ms " +
                    "bitmapKB=${result.width * result.height * 4 / 1024} " +
                    "javaHeap=${"%.1f".format(heapUsedMb)}/${"%.0f".format(heapMaxMb)}MB"
            )
            return result.asImageBitmap().also { BiomeBackgroundCache.put(mapId, biome, it) }
        }

        private fun recolor(src: Bitmap, biome: Biome): Bitmap {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            BiomeRecolor.apply(biome, px, w, h)
            return if (src.isMutable) {
                src.setPixels(px, 0, w, 0, 0, w, h)
                src
            } else {
                // Yedek yol: decoder inMutable'i yok saydi. Gecici olarak iki
                // bitmap bellekte olur; eskisi hemen birakilir.
                Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            }
        }

        private const val BG_TAG = "BiomeBackground"

        /**
         * SPRITE DECODE OLCUMU — cihazda.
         *
         * `rememberGameSprites()` bunu `remember(res)` icinde, yani ILK
         * KOMPOZISYONDA ve ANA THREAD'de cagirir. 20+ WebP dosyasinin decode
         * suresi dogrudan "savasa girerken takildi" hissi olarak gorunur ve
         * bugune kadar hic olculmedi.
         *
         * Bu satir olcumu tek komuta indirir:
         *   adb logcat -s FDPerf:I BiomeBackground:I
         */
        private fun logSpriteBudget(startNanos: Long, sprites: GameSprites) {
            var bytes = 0L
            var count = 0
            fun add(b: ImageBitmap) {
                bytes += b.width.toLong() * b.height * 4
                count++
            }
            sprites.towers.values.forEach(::add)
            // Iki dusman tipi ayni bitmap'i PAYLASIR; benzersiz sayim icin
            // referansa gore tekillestirilir, yoksa bellek iki kez sayilirdi.
            sprites.enemies.values.distinct().forEach(::add)
            listOf(
                sprites.muzzleFlash, sprites.tracer, sprites.cannonShell, sprites.missile,
                sprites.hitSpark, sprites.smallExplosion, sprites.largeExplosion,
                sprites.smokePuff, sprites.buildPad, sprites.rangeBlue, sprites.rangeGreen
            ).forEach(::add)

            val rt = Runtime.getRuntime()
            Log.i(
                PERF_TAG,
                "sprite decode: $count bitmap, ${bytes / 1024} KB bellek, " +
                    "${(SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000}ms " +
                    "(ANA THREAD, ilk kompozisyon) javaHeap=" +
                    "${"%.1f".format((rt.totalMemory() - rt.freeMemory()) / 1_048_576f)}MB"
            )
        }

        private const val PERF_TAG = "FDPerf"

        fun create(res: Resources): GameSprites {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val sprites = createInternal(res)
            logSpriteBudget(t0, sprites)
            return sprites
        }

        private fun createInternal(res: Resources): GameSprites = GameSprites(
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
                // FAZ 15: bu iki tip artik KENDI sprite'ini kullaniyor.
                //
                // Eskiden ikisi de piyade/tank bitmap'ini PAYLASIYORDU (Faz 4 /
                // DECISIONS B1: "yeni PNG yok"). Bu kozmetik bir eksiklik
                // degildi, oyunun cekirdek karari gorunmez oluyordu: zirhsiz
                // hedefte Gatling 43,8 DPS / Cannon 18,4, TANKTA Gatling 6,1 /
                // Fuze 26,6 — yani 7 kat fark. Oyuncu 165 canli zirhliyi 82
                // canli piyadeden, 2.860 canli BOSS'u 638 canli normal tanktan
                // ayirt edemedigi surece dogru kuleyi secmesi imkansizdi.
                //
                // Sprite'lar 2026-08-18'de uretildi. Varyant secimi 1024 px'te
                // degil OYUNCUNUN GORDUGU boyutta (46-96 px) gercek harita
                // uzerinde yapildi; secilenler kalkanin gumus/simetrik oldugu
                // (yesil cimde kamufle olmayan) ve boss'un normal tanktan
                // FARKLI BIR SASI olarak okundugu varyantlar.
                // Reddedilenler: asset-pack/visuals/_variants/
                // ------------------------------------------------------------
                GameConfig.EnemyType.SHIELDED_TROOPER to load(res, R.drawable.spr_enemy_shielded_trooper),
                GameConfig.EnemyType.COMMAND_TANK to load(res, R.drawable.spr_enemy_command_tank)
            ),
            muzzleFlash = load(res, R.drawable.spr_fx_muzzle_flash),
            tracer = load(res, R.drawable.spr_fx_tracer),
            cannonShell = load(res, R.drawable.spr_fx_cannon_shell),
            missile = load(res, R.drawable.spr_fx_missile),
            hitSpark = load(res, R.drawable.spr_fx_hit_spark),
            smallExplosion = load(res, R.drawable.spr_fx_small_explosion),
            largeExplosion = load(res, R.drawable.spr_fx_large_explosion),
            airStrikeJet = load(res, R.drawable.spr_fx_air_strike_jet),
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

/**
 * Arkaplan onbellegi — process seviyesinde TEK yuva (8.3 MB tavan).
 * Politika ve gerekcesi `BiomeSlotCache` icinde; burasi yalnizca uygulama
 * genelinde tek bir ornek oldugunu garanti eder.
 */
object BiomeBackgroundCache {
    @VisibleForTesting
    internal val slot = BiomeSlotCache<ImageBitmap>()

    fun get(mapId: Int, biome: Biome): ImageBitmap? = slot.get(mapId, biome)
    fun put(mapId: Int, biome: Biome, bitmap: ImageBitmap) = slot.put(mapId, biome, bitmap)

    /** `onTrimMemory` / bolum disina cikis icin. */
    fun clear() = slot.clear()

    /** Sozlesme geregi her zaman 0 ya da 1. */
    val size: Int get() = slot.size
}

/**
 * Aktif bolumun harita arkaplani — biyom uygulanmis halde.
 *
 * NEDEN NULLABLE / NEDEN ASENKRON
 *   Decode (~8.3 MB) + recolor (~2.08M piksel) birlikte yuzlerce ms surer.
 *   Bunu ana thread'de yapmak bolum girisinde ANR sinirina yaklasan bir donma
 *   uretir. Is `Dispatchers.Default`'a taşındi; hazir olana kadar `null`
 *   doner ve `GameCanvas` yalnizca harita katmanini atlar (letterbox zemini
 *   ve HUD cizilmeye devam eder, girdi bloklanmaz).
 *
 *   Bu bosluk oynanisi bozmaz: motor bolume `PREPARATION` fazinda
 *   (`GameConfig.PREPARATION_TIME_SECONDS = 10`) giriyor, yani ilk dusman
 *   spawn olmadan once saniyeler var.
 *
 * `remember(mapId, biome)` ANAHTARLI: bolum degisince state ANINDA null'a
 * doner. `produceState` burada YANLIS olurdu — onun ic state'i anahtarsiz
 * `remember` ile tutulur ve bolum degistiginde bir sure ONCEKI bolumun
 * haritasini gosterirdi.
 */
@Composable
fun rememberMapBitmap(mapId: Int, biome: Biome): ImageBitmap? {
    val res = LocalContext.current.resources
    val state = remember(res, mapId, biome) {
        // Onbellekte hazirsa ilk karede senkron olarak gelir — tekrar denemede
        // hic bosluk gorunmez.
        mutableStateOf(BiomeBackgroundCache.get(mapId, biome))
    }
    LaunchedEffect(res, mapId, biome) {
        if (state.value == null) {
            state.value = withContext(Dispatchers.Default) {
                GameSprites.loadBackground(res, mapId, biome)
            }
        }
    }
    return state.value
}
