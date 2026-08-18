package com.miniappfactory.frontlinedefender.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import java.util.concurrent.Executors
import android.os.SystemClock
import android.util.Log
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

    private val appContext: Context = context.applicationContext

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
        UI_CLICK(R.raw.sfx_ui_click, gain = 0.7f, minIntervalMs = 40L),

        /**
         * Faz 14 — DALGA TEMIZLENDI + bonus tedarik.
         *
         * Onceden `TOWER_BUILD` calisiyordu; oyuncu "dalga bitti" ile "kule
         * kuruldu" olaylarini kulakla ayirt edemiyordu. Ayrica `COIN_EARNED`
         * cift gorev yapiyordu: oldurme basina coin sesi saniyede birkac kez
         * calarken dalga sonu TEK ve daha buyuk bir olay — ayni ses ikisini de
         * temsil edemez.
         *
         * Varyasyon KAPALI: dalga sonu bir jingle'dir, her seferinde ayni
         * duyulmali (zafer/yenilgi ile ayni kural).
         */
        WAVE_CLEARED(R.raw.sfx_wave_cleared, pitchVary = false),

        /**
         * Faz 14 — OLDURME ZINCIRI kademe atlama riser'i (esikler 3/6/10/16).
         *
         * DORT AYRI ORNEK; tek ornegi pitch'lemek DEGIL. `SoundPool` rate
         * araligi (0.5-2.0) dort kademe icin yeterli olurdu ama pitch kaydirma
         * suresi de kisaltir (rate 1.41'de 250 ms -> 177 ms) ve ust kademelere
         * shimmer/vurgu katmani ekleyemez. Tirmanma perde + parlaklik + katman
         * olarak UC kanaldan birden okunur; toplam maliyet ~29 KB.
         *
         * Kazanc ASCENDING GAIN ile de desteklenir: ust kademe yalnizca daha
         * tiz degil biraz daha onde. Varyasyon KAPALI — tirmanma basamaklari
         * rastgele pitch'lenirse merdiven duyulmaz.
         */
        COMBO_UP_1(R.raw.sfx_combo_up_1, gain = 0.80f, pitchVary = false),
        COMBO_UP_2(R.raw.sfx_combo_up_2, gain = 0.88f, pitchVary = false),
        COMBO_UP_3(R.raw.sfx_combo_up_3, gain = 0.95f, pitchVary = false),
        COMBO_UP_4(R.raw.sfx_combo_up_4, gain = 1.0f, pitchVary = false);
    }

    /**
     * Faz 14 — MUZIK KANALI.
     *
     * Iki parca da `docs/tools/music_synth.mjs` ile prosedurel uretildi (bu
     * makinede Python yok; sentez Node ile, kodlama ffmpeg/libvorbis -q:a 4
     * ile yapildi — SFX'lerle ayni kalite ayari).
     *
     * Ikisi de ayni tonal merkezden (Re minor) turer, bu yuzden menu -> savas
     * gecisi ton kaymasi gibi duyulmaz. Ikisi de -24.0 LUFS'e hizalandi; SFX
     * yatagi -15 dB mean oldugu icin muzik 9 dB ALTTA kalir ve efektleri
     * maskelemez ([MUSIC_VOLUME] bunun uzerine ince ayar).
     */
    enum class MusicTrack(@RawRes val res: Int) {
        /** Hazirlik, menu ve sonuc ekranlari. Vurmali yok, 76 BPM, 25.26 sn. */
        MENU(R.raw.music_menu),

        /** Dalga surerken. Askeri nabiz + ostinato, 100 BPM, 38.40 sn. */
        BATTLE(R.raw.music_battle)
    }

    companion object {
        private const val MAX_STREAMS = 5
        private const val TAG = "AudioManager"

        /**
         * Muzik mix seviyesi. Dosyalar zaten -24 LUFS'e hizali; bu carpan
         * yalnizca oynanis sirasinda yatagi biraz daha geri cekmek icin.
         * 1.0'a cikarmak SFX'i BASTIRMAZ ama HUD tiklarini yumusatir.
         */
        private const val MUSIC_VOLUME = 0.55f


        /**
         * Zincir kademesi -> riser ornegi. Kademe 1 tabanlidir (ilk esik = 1).
         *
         * KIRPILIR, sarmaz: kademe 4'un uzerinde bir esik eklenirse en ust
         * riser calmaya devam eder. Modulo alsaydik 5. kademe en HAFIF sesi
         * verir ve tirmanma cokerdi — sessizce yanlis bir oynanis hissi.
         */
        internal fun comboEffectFor(tier: Int): SoundEffect = when {
            tier <= 1 -> SoundEffect.COMBO_UP_1
            tier == 2 -> SoundEffect.COMBO_UP_2
            tier == 3 -> SoundEffect.COMBO_UP_3
            else -> SoundEffect.COMBO_UP_4
        }
    }

    /**
     * Zincir kademesi atlandi. Cagri yeri hangi ORNEGIN calacagini bilmez,
     * yalnizca kacinci kademeye ciktigini bilir.
     */
    fun playComboTier(tier: Int) = playSound(comboEffectFor(tier))

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

    /**
     * ANA SES ANAHTARI. Kapatildiginda muzik de susar: bugun ayarlarda TEK bir
     * "Ses" anahtari var ve oyuncunun onu kapatinca muzigin calmaya devam
     * etmesi hata gibi hissettirir. Muzigi AYRI kapatmak icin
     * [isMusicEnabled] kullanilir; efektif durum ikisinin VE'sidir.
     */
    var isSoundEnabled: Boolean = true
        set(value) {
            field = value
            if (!value && !released) pool.autoPause()
            synchronized(musicLock) { reconcileMusicLocked() }
        }

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(ready) { ready.add(sampleId) }
        }
        SoundEffect.entries.forEach { fx ->
            sampleIds.getOrPut(fx.res) { pool.load(context, fx.res, 1) }
        }
    }

    // =====================================================================
    // MUZIK
    // =====================================================================

    private val musicLock = Any()

    /**
     * `MediaPlayer.create` KAYNAK COZER VE PREPARE EDER; 480 KB'lik bir Vorbis
     * icin bu ana is parcaciginda olculebilir bir takilma demektir ve parca
     * degisimi tam da dalga baslangicina denk gelir. Bu yuzden hazirlama arka
     * planda yapilir. Tek is parcacigi: hazirlama sirasi korunur.
     */
    private val musicLoader = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fd-music-loader").apply { isDaemon = true }
    }

    /** Calmasi ISTENEN parca. Gercek durum [reconcileMusicLocked] ile yakalanir. */
    private var desiredTrack: MusicTrack? = null

    /** Su an yuklu olan parca. `desiredTrack`ten farkliysa yeniden yuklenir. */
    private var loadedTrack: MusicTrack? = null

    /**
     * Ping-pong cifti. TEK bir `MediaPlayer` + `isLooping = true` YETMEZ:
     * framework dongu basinda 0'a seek eder ve onlarca ms'lik duyulur bir
     * bosluk birakir. Dosyalarin dikissiz uretilmis olmasi (bkz.
     * `docs/tools/loop_verify.mjs`) bu bosluk yuzunden bosa giderdi.
     * `setNextMediaPlayer` ile ikinci calici birincinin bittigi karede devralir.
     */
    private var players: Array<MediaPlayer>? = null
    private var activeIndex = 0

    /** Eskimis arka plan yuklemelerini elemek icin. */
    private var loadGeneration = 0

    /** Lifecycle ON_RESUME/ON_PAUSE. Reklam da bir lifecycle olayidir. */
    private var lifecycleResumed = true

    /**
     * Muzik ayri kapatilabilir. Kalici tercih [FeedbackPrefs] icindedir —
     * `SaveManager`in kayit dosyasindan AYRI, boylece "ilerlemeyi sifirla"
     * bir AYARI silmez.
     */
    private var musicEnabledBacking: Boolean = FeedbackPrefs.isMusicEnabled(appContext)

    var isMusicEnabled: Boolean
        get() = musicEnabledBacking
        set(value) {
            musicEnabledBacking = value
            FeedbackPrefs.setMusicEnabled(appContext, value)
            synchronized(musicLock) { reconcileMusicLocked() }
        }

    /**
     * Muzik alanlari `init` blogundan SONRA bildirildigi icin acilis parcasi da
     * burada secilir. Kotlin ozellikleri bildirim sirasinda ilklendirir; yukaridaki
     * `init` icinden `setMusicScene` cagrilsaydi `musicLock` henuz null olurdu.
     *
     * Acilis MENU ile baslar: hazirlik asamasinda oyuncu kule yerlestirirken
     * sakin ton calar, ilk dalgayla birlikte BATTLE devralir.
     */
    init {
        setMusicScene(MusicTrack.MENU)
    }

    /**
     * Calan muzik yatagini secer; `null` muzigi susturur. Ayni parca tekrar
     * verilirse calan parca BASA SARMAZ — bu yuzden her durum degisiminde
     * kosulsuz cagrilabilir.
     *
     * ---------------------------------------------------------------------
     * NEDEN SES EFEKTINDEN DEGIL, OYUN DURUMUNDAN SURULUR
     * ---------------------------------------------------------------------
     * Ilk uygulamada gecis [playSound] icinden cikarsaniyordu
     * (WAVE_START -> BATTLE, VICTORY/DEFEAT -> MENU). Acilis sessizligi
     * DEGILDI - asagidaki `init` zaten MENU ile basliyor - ama sinyal
     * YANLISTI: muzik bir ses efektinin yan urunu degil, ekran/durum
     * degisiminin sonucudur. Somut kayip savasi BITIRMEDEN cikan oyuncuydu:
     * duraklama menusunden bolum secmeye donuldugunde ne VICTORY ne DEFEAT
     * calar, dolayisiyla SAVAS MUZIGI HARITA EKRANINDA CALMAYA DEVAM ederdi.
     * Ayni sekilde "yeniden basla" sonrasi hazirlik asamasi savas muzigiyle
     * aciliyordu.
     *
     * Artik tek surucu `GameState`; esleme `GameScreen.musicSceneFor`
     * icindedir ve orada birim testi vardir.
     */
    fun setMusicScene(track: MusicTrack?) {
        synchronized(musicLock) {
            if (desiredTrack == track) return
            desiredTrack = track
            reconcileMusicLocked()
        }
    }

    /** Istenen durum ile gercek durumu esitler. TEK karar noktasi. */
    private fun reconcileMusicLocked() {
        if (released) return
        val want = desiredTrack
        val shouldPlay = want != null && isMusicEnabled && isSoundEnabled && lifecycleResumed

        if (!shouldPlay) {
            // DURDURMA DEGIL DURAKLATMA: reklam kapaninca ayni noktadan devam
            // etmesi gerekiyor.
            players?.getOrNull(activeIndex)?.let { p ->
                runCatching { if (p.isPlaying) p.pause() }
            }
            return
        }

        if (loadedTrack != want) {
            startMusicLoadLocked(want!!)
            return
        }
        players?.getOrNull(activeIndex)?.let { p ->
            runCatching { if (!p.isPlaying) p.start() }
        }
    }

    private fun startMusicLoadLocked(track: MusicTrack) {
        val generation = ++loadGeneration
        releasePlayersLocked()
        loadedTrack = null

        musicLoader.execute {
            val a = runCatching { MediaPlayer.create(appContext, track.res) }.getOrNull()
            val b = runCatching { MediaPlayer.create(appContext, track.res) }.getOrNull()
            synchronized(musicLock) {
                // Bu yukleme eskidiyse (parca degisti veya release edildi) at.
                if (released || generation != loadGeneration || a == null || b == null) {
                    runCatching { a?.release() }
                    runCatching { b?.release() }
                    return@synchronized
                }
                val pair = arrayOf(a, b)
                pair.forEach { p ->
                    runCatching { p.setVolume(MUSIC_VOLUME, MUSIC_VOLUME) }
                    p.setOnCompletionListener { done -> onMusicSegmentComplete(done) }
                }
                // Zincir kurulamazsa TEK calici + isLooping'e dus: bosluklu da
                // olsa muzik CALAR. Sessiz basarisizlik yok, sebep loglanir.
                val chained = runCatching { a.setNextMediaPlayer(b) }.isSuccess
                if (!chained) {
                    Log.w(TAG, "setNextMediaPlayer reddedildi; bosluklu donguye dusuluyor")
                    runCatching { b.release() }
                    runCatching { a.isLooping = true }
                    a.setOnCompletionListener(null)
                    players = arrayOf(a)
                } else {
                    players = pair
                }
                activeIndex = 0
                loadedTrack = track
                reconcileMusicLocked()
            }
        }
    }

    /**
     * Bir segment bitti: sirradaki calici ZATEN caliyor. Biteni basa sarip
     * bir sonraki devir icin zincire tekrar takariz. Boylece iki calici
     * sonsuza kadar birbirini devralir ve hicbir noktada prepare beklenmez.
     */
    private fun onMusicSegmentComplete(finished: MediaPlayer) {
        synchronized(musicLock) {
            val ps = players ?: return
            if (ps.size < 2) return
            val idx = ps.indexOf(finished)
            if (idx < 0) return
            activeIndex = 1 - idx
            runCatching { finished.seekTo(0) }
            runCatching { ps[activeIndex].setNextMediaPlayer(finished) }
                .onFailure { Log.w(TAG, "zincir yenilenemedi: ${it.message}") }
        }
    }

    private fun releasePlayersLocked() {
        players?.forEach { p ->
            runCatching { p.setOnCompletionListener(null) }
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.release() }
        }
        players = null
        activeIndex = 0
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

    /** Lifecycle ON_PAUSE ve reklam gosterimi. Muzik DURAKLAR, sifirlanmaz. */
    fun onPause() {
        if (released) return
        pool.autoPause()
        synchronized(musicLock) {
            lifecycleResumed = false
            reconcileMusicLocked()
        }
    }

    /** Lifecycle ON_RESUME ve reklam kapanisi. Muzik AYNI NOKTADAN devam eder. */
    fun onResume() {
        if (released) return
        if (isSoundEnabled) pool.autoResume()
        synchronized(musicLock) {
            lifecycleResumed = true
            reconcileMusicLocked()
        }
    }

    /** ZORUNLU — cagrilmazsa SoundPool ve MediaPlayer'lar sizar. */
    fun release() {
        if (released) return
        released = true
        pool.release()
        synchronized(musicLock) {
            loadGeneration++      // ucusan arka plan yuklemesi varsa gecersiz kil
            desiredTrack = null
            loadedTrack = null
            releasePlayersLocked()
        }
        musicLoader.shutdown()
    }
}
