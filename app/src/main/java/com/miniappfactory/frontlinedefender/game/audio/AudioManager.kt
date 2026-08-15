package com.miniappfactory.frontlinedefender.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.annotation.RawRes
import com.miniappfactory.frontlinedefender.R
import kotlin.random.Random

/**
 * Faz 3 — SoundPool + res/raw OGG.
 *
 * ONCEKI HALI VE NEDEN KALDIRILDI:
 * Sesler calisma aninda AudioTrack ile sentezleniyordu ve her calma
 * `Dispatchers.Default` uzerinde `Thread.sleep(sesUzunlugu)` yapiyordu. Bu,
 * ortak coroutine havuzunun is parcaciklarini ses suresi boyunca bloke eder;
 * makineli tufek saniyede ~6 kez ates ederken havuz doluyor ve kare suresi
 * bozuluyordu (Intake §8 bulgusu). Ayrica her calmada yeni AudioTrack
 * ayrilip release ediliyordu.
 *
 * Yeni hali: 15 OGG dosyasi (res/raw, ffmpeg ile -15 dB mean'e hizalandi,
 * libvorbis -q:a 4, toplam 105 KB) tek bir SoundPool'a yuklenir. Calma
 * senkron ve tahsissizdir.
 *
 * ZORUNLU KURALLAR (game-sound-create skill'i):
 *  - load() ASENKRONDUR; yuklenmeden play() sessizce hicbir sey yapmaz.
 *    OnLoadCompleteListener ile hazir ornekler takip edilir.
 *  - maxStreams 5: daha fazlasi telefon hoparlorunde camur, daha azi
 *    makineli tufek + patlama ust uste gelince patlamayi yutar.
 *  - Sik tekrar eden seslerde MINIMUM ARALIK var; yoksa kanal dolar ve
 *    patlama sesi kaybolur.
 *  - Pitch varyasyonu: ayni dosya saniyede 6 kez calinca sabit pitch yapay
 *    duyulur. Jingle'larda (zafer/yenilgi/dalga) varyasyon KAPALI.
 *  - release() ZORUNLU (GameScreen'deki DisposableEffect).
 *  - onPause/onResume ve gercek sessize alma: autoPause/autoResume.
 *    Reklam da bir lifecycle olayidir.
 */
class AudioManager(context: Context) {

    /**
     * @param gain mix seviyesi (dosya seviyesi ffmpeg ile hizalandi; bu carpan
     *   yalnizca ince ayar).
     * @param minIntervalMs ayni efekt icin iki calma arasi en az sure.
     * @param pitchVary dogallik icin 0.94..1.06 hiz sapmasi.
     */
    enum class SoundEffect(
        @RawRes val res: Int,
        val gain: Float = 1f,
        val minIntervalMs: Long = 0L,
        val pitchVary: Boolean = true
    ) {
        MACHINE_GUN(R.raw.sfx_machine_gun, gain = 0.9f, minIntervalMs = 70L),
        CANNON_BOOM(R.raw.sfx_cannon),
        MISSILE_LAUNCH(R.raw.sfx_missile_launch),
        FROST_PULSE(R.raw.sfx_energy_zap, gain = 0.8f, minIntervalMs = 90L),
        ENEMY_HIT(R.raw.sfx_enemy_hit, gain = 0.9f, minIntervalMs = 60L),
        EXPLOSION(R.raw.sfx_explosion_medium, minIntervalMs = 55L),
        EXPLOSION_HEAVY(R.raw.sfx_explosion_heavy, minIntervalMs = 90L),
        VEHICLE_DESTROYED(R.raw.sfx_vehicle_destroyed, minIntervalMs = 80L),
        COIN_EARNED(R.raw.sfx_coin, gain = 0.8f, minIntervalMs = 60L),
        TOWER_BUILD(R.raw.sfx_tower_place),
        TOWER_UPGRADE(R.raw.sfx_upgrade),
        TOWER_SELL(R.raw.sfx_coin, gain = 0.85f),
        WAVE_START(R.raw.sfx_wave_start, pitchVary = false),
        VICTORY(R.raw.sfx_victory, pitchVary = false),
        DEFEAT(R.raw.sfx_defeat, pitchVary = false),
        UI_CLICK(R.raw.sfx_ui_click, gain = 0.7f, minIntervalMs = 40L);
    }

    private companion object {
        const val MAX_STREAMS = 5
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /** resId -> sampleId. Ayni dosyayi paylasan efektler bir kez yuklenir. */
    private val sampleIds = HashMap<Int, Int>()
    private val ready = HashSet<Int>()
    private val lastPlayedAt = HashMap<SoundEffect, Long>()

    @Volatile
    private var released = false

    var isSoundEnabled: Boolean = true
        set(value) {
            field = value
            if (!value && !released) pool.autoPause()
        }

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(ready) { ready.add(sampleId) }
        }
        SoundEffect.entries.forEach { fx ->
            sampleIds.getOrPut(fx.res) { pool.load(context, fx.res, 1) }
        }
    }

    fun playSound(effect: SoundEffect) {
        if (released || !isSoundEnabled) return
        val sampleId = sampleIds[effect.res] ?: return
        // load() asenkron: henuz hazir degilse SESSIZ GEC (play() zaten calmaz).
        synchronized(ready) { if (sampleId !in ready) return }

        if (effect.minIntervalMs > 0L) {
            val now = SystemClock.uptimeMillis()
            val last = lastPlayedAt[effect] ?: 0L
            if (now - last < effect.minIntervalMs) return
            lastPlayedAt[effect] = now
        }

        val v = effect.gain.coerceIn(0f, 1f)
        val rate = if (effect.pitchVary) 0.94f + Random.nextFloat() * 0.12f else 1f
        pool.play(sampleId, v, v, 1, 0, rate)
    }

    /** Lifecycle ON_PAUSE ve reklam gosterimi. */
    fun onPause() {
        if (!released) pool.autoPause()
    }

    /** Lifecycle ON_RESUME ve reklam kapanisi. */
    fun onResume() {
        if (!released && isSoundEnabled) pool.autoResume()
    }

    /** ZORUNLU — cagrilmazsa SoundPool sizar. */
    fun release() {
        if (released) return
        released = true
        pool.release()
    }
}
