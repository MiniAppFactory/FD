package com.miniappfactory.frontlinedefender.game.engine

import androidx.compose.ui.geometry.Offset
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import kotlin.random.Random

enum class GameState {
    MAIN_MENU,
    /** Faz 4: kampanya haritasi / bolum secme. Savas HUD'i cizilmez. */
    LEVEL_SELECT,
    PREPARATION,
    WAVE_RUNNING,
    PAUSED,
    VICTORY,
    DEFEAT
}

class GameEngine(
    val saveManager: SaveManager,
    val audioManager: AudioManager
) {
    private val _gameState = MutableStateFlow(GameState.MAIN_MENU)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _gold = MutableStateFlow(GameConfig.INITIAL_GOLD)
    val gold: StateFlow<Int> = _gold.asStateFlow()

    private val _lives = MutableStateFlow(GameConfig.INITIAL_BASE_LIVES)
    val lives: StateFlow<Int> = _lives.asStateFlow()

    private val _currentWaveIndex = MutableStateFlow(0)
    val currentWaveIndex: StateFlow<Int> = _currentWaveIndex.asStateFlow()

    private val _preparationTimer = MutableStateFlow(GameConfig.PREPARATION_TIME_SECONDS.toFloat())
    val preparationTimer: StateFlow<Float> = _preparationTimer.asStateFlow()

    private val _gameSpeed = MutableStateFlow(1.0f)
    val gameSpeed: StateFlow<Float> = _gameSpeed.asStateFlow()

    private val _selectedBuildSpot = MutableStateFlow<BuildSpot?>(null)
    val selectedBuildSpot: StateFlow<BuildSpot?> = _selectedBuildSpot.asStateFlow()

    private val _selectedTower = MutableStateFlow<TowerEntity?>(null)
    val selectedTower: StateFlow<TowerEntity?> = _selectedTower.asStateFlow()

    private val _screenShake = MutableStateFlow(Offset.Zero)
    val screenShake: StateFlow<Offset> = _screenShake.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    // ------------------------------------------------------------------------
    // Faz 4 — KAMPANYA DURUMU
    // ------------------------------------------------------------------------

    private val _currentLevelId = MutableStateFlow(1)
    val currentLevelId: StateFlow<Int> = _currentLevelId.asStateFlow()

    /**
     * Aktif bolumun dalga sayisi. HUD "WAVE n/N" icin bunu okumalidir;
     * `GameConfig.WAVES.size` artik yanlis cevabi verir (o liste 6 dalgalik eski
     * tek-bolum listesi, bolum basina dalga sayisi 6..18 arasinda degisiyor).
     */
    private val _totalWaves = MutableStateFlow(0)
    val totalWaves: StateFlow<Int> = _totalWaves.asStateFlow()

    /** Son kazanilan yildiz sayisi (0-3) — VICTORY modali icin. */
    private val _lastEarnedStars = MutableStateFlow(0)
    val lastEarnedStars: StateFlow<Int> = _lastEarnedStars.asStateFlow()

    /** Aktif bolumun konfigurasyonu. Tek kaynak: GameConfig.CAMPAIGN. */
    var levelSpec: GameConfig.LevelSpec = GameConfig.levelSpec(1)
        private set

    /**
     * CIZILEN haritanin id'si. `levelSpec.mapId`'den FARKLI olabilir: o haritanin
     * bitmap'i APK'da yoksa motor `GameConfig.MAP_FALLBACK_ID` geometrisine duser
     * ki oynanis ile cizili harita ayrismasin. Bkz. GameConfig.SHIPPED_MAP_IDS.
     */
    var activeMapId: Int = GameConfig.MAP_FALLBACK_ID
        private set

    /** Aktif bolumun OLCULMUS harita geometrisi (normalize koordinatlar). */
    private var activeLevel: LevelData = LevelData.forMapId(GameConfig.MAP_FALLBACK_ID)

    // ------------------------------------------------------------------------
    // META YUKSELTMELER — kalici ilerlemenin oynanisa YANSIDIGI yer.
    //
    // Bunlar olmadan yukseltme dukkanini acmak, calismayan bir sey satmak
    // olurdu. Bolum yuklenirken BIR KEZ okunur; savas icinde degismez.
    // ------------------------------------------------------------------------
    private var metaDamageMultiplier: Float = 1f
    private var metaRangeMultiplier: Float = 1f
    private var metaSalvageRate: Float = 0.70f
    private var metaSupplyBonus: Int = 0
    private var metaLivesBonus: Int = 0

    /** Aktif turun (Act) dusman carpanlari. */
    private var actHpMul: Float = 1f
    private var actRewardMul: Float = 1f

    /**
     * Etkiler `MetaUpgrades`'in KENDI turetilmis alanlarindan okunur, burada
     * yeniden hesaplanmaz. Ayni matematigi iki yerde tutmak tam olarak AEHP
     * hatasinin sebebiydi (tablo elle yaziliydi, karar degisti, tablo kaldi).
     */
    private fun refreshMetaUpgrades() {
        val m = saveManager.loadMetaUpgrades()
        metaDamageMultiplier = m.damageMultiplier.toFloat()
        metaRangeMultiplier = m.rangeMultiplier.toFloat()
        metaSalvageRate = m.salvageRatio.toFloat()
        // Taban degerler LevelSpec'ten geliyor; burada yalnizca EK kismi lazim.
        metaSupplyBonus = m.startingSupply - EconomyConfig.BASE_STARTING_SUPPLY
        metaLivesBonus = m.maxBaseHealth - EconomyConfig.BASE_MAX_HEALTH
    }

    /** Aktif bolumun dalga listesi — WaveDefinitions.CAMPAIGN'den. */
    private var levelWaves: List<GameConfig.WaveData> = emptyList()

    /** Aktif haritanin normalize rotalari (catallanan haritalarda 2 tane). */
    private var routes: List<List<PointF>> = emptyList()

    /**
     * Rota atamasi icin SEED'LI RNG. Render'dan ayri; yalnizca spawnEnemy
     * tuketir. Ayni bolum -> ayni rota dizisi (replay/test/denge dogrulanabilir).
     */
    private var routeRng: Random = Random(GameConfig.ROUTE_RNG_SEED_BASE)

    /**
     * PAUSED'a girmeden ONCEKI durum. Bu olmadan resume her zaman WAVE_RUNNING'e
     * donuyordu: hazirlik sirasinda pause/resume yapan oyuncu kalan hazirlik
     * suresini KAYBEDIYOR ve dalga aninda basliyordu.
     */
    private var stateBeforePause: GameState = GameState.PREPARATION

    // Level map dimension (in Canvas pixel units)
    var mapWidthPx: Float = 1280f
    var mapHeightPx: Float = 720f

    // ------------------------------------------------------------------------
    // Faz 3 — OYNANIS DIKDORTGENI (letterbox sonrasi).
    //
    // Harita 16:9 (1.776), telefonlar 19.5:9 / 20:9. Crop yapmak build pad'leri
    // ekran disina atar ve seviyeyi oynanamaz kilar (game-asset-draw skill'i,
    // "En-boy orani cakismasi"). Bu yuzden FIT + letterbox: harita en-boy
    // oranini koruyarak sigdirilir, artan yer koyu zemin kalir ve TUM oynanis
    // koordinatlari bu dikdortgene gore hesaplanir.
    // ------------------------------------------------------------------------
    var fieldLeftPx: Float = 0f
        private set
    var fieldTopPx: Float = 0f
        private set
    var fieldWidthPx: Float = 1280f
        private set
    var fieldHeightPx: Float = 720f
        private set

    /** Referans tuvalde (1920) tanimli gorsel boyutlari px'e cevirme carpani. */
    var renderScale: Float = 1f
        private set

    // Scaled pixel waypoints and build spot coordinates
    /**
     * BIRINCIL rotanin px koordinatlari. Geriye donuk uyum icin korunuyor
     * (GameCanvas.drawDebugPath bunu okuyor). Coklu rota icin `scaledRoutes`.
     */
    var scaledWaypoints = listOf<PointF>()
    /** Aktif haritanin TUM rotalari, px koordinatlarinda. En az 1 eleman. */
    var scaledRoutes: List<List<PointF>> = emptyList()
    var scaledBuildSpots = listOf<BuildSpot>()

    val towers = mutableListOf<TowerEntity>()
    val enemies = mutableListOf<EnemyEntity>()
    val projectiles = mutableListOf<ProjectileEntity>()
    val visualEffects = mutableListOf<VisualEffect>()

    // Current wave spawn queue
    private val pendingWaveSpawns = mutableListOf<GameConfig.WaveEnemySpawn>()
    private var timeUntilNextSpawn = 0f
    private var screenShakeDuration = 0f

    init {
        loadLevel(GameConfig.levelSpec(1))
    }

    /** Ust HUD seridinin px yuksekligi; oynanis alani bunun altinda baslar. */
    private var hudTopInsetPx: Float = 0f

    fun updateMapDimensions(width: Float, height: Float, topInsetPx: Float = hudTopInsetPx) {
        if (width <= 0f || height <= 0f) return
        if (topInsetPx != hudTopInsetPx) {
            hudTopInsetPx = topInsetPx
            // olcu degistiyse asagidaki "ayni ise cik" kisa devresini atla
            mapWidthPx = -1f
        }
        // Faz 2: bu fonksiyon GameCanvas'in draw lambda'sindan cagriliyor. Kare
        // invalidation'i duzeltildikten sonra draw saniyede 60 kez kosuyor; olcu
        // degismedigi halde her karede iki liste yeniden uretmek saf tahsis
        // baskisidir (GC duraklamasi = jank). Olcu ayniysa cik.
        if (width == mapWidthPx && height == mapHeightPx && scaledWaypoints.isNotEmpty()) return
        mapWidthPx = width
        mapHeightPx = height

        // FIT (crop DEGIL): en-boy orani korunur, haritanin oynanis iceren
        // bandi HUD'un altindaki serbest alana sigdirilir. Haritanin ust %10'u
        // dekoratif oldugu icin HUD'un altina kasitli olarak tasar.
        val safeFrac = 1f - GameConfig.MAP_SAFE_TOP_FRAC
        val availableHeight = (height - hudTopInsetPx).coerceAtLeast(1f)
        var fh = availableHeight / safeFrac
        var fw = fh * GameConfig.MAP_ASPECT_RATIO
        if (fw > width) {
            // Genislik sinirli (daha kare tuval / tablet) -> altta serit kalir.
            fw = width
            fh = width / GameConfig.MAP_ASPECT_RATIO
        }
        fieldWidthPx = fw
        fieldHeightPx = fh
        fieldLeftPx = (width - fw) / 2f
        fieldTopPx = hudTopInsetPx - GameConfig.MAP_SAFE_TOP_FRAC * fh
        renderScale = fieldWidthPx / GameConfig.REFERENCE_WIDTH

        // Faz 4: AKTIF bolumun geometrisi. Eskiden burada `LevelData.LEVEL_1`
        // sabit kodluydu, yani hangi bolum yuklenirse yuklensin ilk karede
        // harita 1'in yolu/pad'leri geri geliyordu.
        rescaleGeometry()

        // Update positions of existing towers
        towers.forEach { tower ->
            scaledBuildSpots.find { it.id == tower.buildSpotId }?.let { spot ->
                tower.posX = spot.normX
                tower.posY = spot.normY
            }
        }
    }

    /**
     * Normalize geometriyi (rotalar + pad'ler) mevcut oynanis dikdortgenine tasir.
     * Olcu degistiginde ve bolum yuklendiginde cagrilir; kare dongusunde DEGIL.
     */
    private fun rescaleGeometry() {
        scaledRoutes = routes.map { route ->
            route.map {
                PointF(fieldLeftPx + it.x * fieldWidthPx, fieldTopPx + it.y * fieldHeightPx)
            }
        }
        scaledWaypoints = scaledRoutes.firstOrNull() ?: emptyList()

        // Act II krater kisiti: devre disi pad'ler haritada HIC gorunmez.
        // Kisit yalnizca spec'in KENDI haritasi ciziliyorsa uygulanir; yedek
        // haritaya dusuldugunde pad id'leri baska bir haritaya ait olur ve
        // alakasiz pad'leri kapatirdi.
        val disabled = if (activeMapId == levelSpec.mapId) {
            levelSpec.disabledPadIds.toSet()
        } else {
            emptySet()
        }

        scaledBuildSpots = activeLevel.buildSpots
            .filter { it.id !in disabled }
            .map {
                BuildSpot(
                    it.id,
                    fieldLeftPx + it.normX * fieldWidthPx,
                    fieldTopPx + it.normY * fieldHeightPx
                )
            }

        // Update positions of existing towers
        towers.forEach { tower ->
            scaledBuildSpots.find { it.id == tower.buildSpotId }?.let { spot ->
                tower.posX = spot.normX
                tower.posY = spot.normY
            }
        }
    }

    /**
     * Bolumu yukler: harita geometrisi, rotalar, pad'ler, dalgalar ve baslangic
     * degerleri TAMAMEN spec'ten gelir.
     *
     * Yedek harita: spec'in haritasinin bitmap'i APK'da yoksa geometri
     * `MAP_FALLBACK_ID`'den alinir. Aksi halde dusmanlar boyali yolun disinda
     * yururdu ve bolum oynanamaz olurdu.
     */
    private fun loadLevel(spec: GameConfig.LevelSpec) {
        levelSpec = spec
        _currentLevelId.value = spec.levelId

        // Kalici ilerleme + tur olceklendirmesi bolum basinda okunur.
        refreshMetaUpgrades()
        actHpMul = GameConfig.actHpMultiplier(spec.act)
        actRewardMul = GameConfig.actRewardMultiplier(spec.act)

        activeMapId = if (spec.mapId in GameConfig.SHIPPED_MAP_IDS) {
            spec.mapId
        } else {
            GameConfig.MAP_FALLBACK_ID
        }

        activeLevel = LevelData.forMapId(activeMapId)
        routes = LevelData.routesForMapId(activeMapId)
        routeRng = Random(GameConfig.ROUTE_RNG_SEED_BASE + spec.levelId * 7919L)

        levelWaves = WaveDefinitions.wavesFor(spec.levelId)
        _totalWaves.value = levelWaves.size

        updateMapDimensions(mapWidthPx, mapHeightPx)
        rescaleGeometry()
    }

    /**
     * @param levelNo 1..22. Varsayilan degeri AKTIF bolum, yani parametresiz
     *   `startNewGame()` = "bu bolumu yeniden basla" (Retry / Restart).
     */
    fun startNewGame(levelNo: Int = _currentLevelId.value) {
        towers.clear()
        enemies.clear()
        projectiles.clear()
        visualEffects.clear()

        loadLevel(GameConfig.levelSpec(levelNo))

        // Meta yukseltmeler taban degerin USTUNE biner.
        _gold.value = levelSpec.startingSupply + metaSupplyBonus
        _lives.value = levelSpec.maxBaseLives + metaLivesBonus
        _score.value = 0
        _currentWaveIndex.value = 0
        _gameSpeed.value = 1.0f
        _selectedBuildSpot.value = null
        _selectedTower.value = null
        _lastEarnedStars.value = 0
        screenShakeDuration = 0f
        _screenShake.value = Offset.Zero

        setupWave(0)
        _gameState.value = GameState.PREPARATION
        stateBeforePause = GameState.PREPARATION
        _preparationTimer.value = GameConfig.PREPARATION_TIME_SECONDS.toFloat()
    }

    /**
     * Kampanya / bolum secme ekranina doner.
     *
     * Bu fonksiyon EKSIKTI: Pause ve Defeat modallerindeki "MAIN MENU" butonlari
     * `startNewGame()` cagiriyordu, yani ana menuye gitmek yerine ayni bolumu
     * bastan baslatiyordu.
     */
    fun returnToLevelSelect() {
        deselectAll()
        _gameSpeed.value = 1.0f
        _screenShake.value = Offset.Zero
        screenShakeDuration = 0f
        _gameState.value = GameState.LEVEL_SELECT
    }

    /** Uygulamanin ilk ekrani. */
    fun returnToMainMenu() {
        deselectAll()
        _gameSpeed.value = 1.0f
        _screenShake.value = Offset.Zero
        screenShakeDuration = 0f
        _gameState.value = GameState.MAIN_MENU
    }

    fun openLevelSelect() {
        _gameState.value = GameState.LEVEL_SELECT
    }

    private fun setupWave(waveIndex: Int) {
        pendingWaveSpawns.clear()
        if (waveIndex < levelWaves.size) {
            val wave = levelWaves[waveIndex]
            pendingWaveSpawns.addAll(wave.spawns)
            timeUntilNextSpawn = 0.5f
        }
    }

    fun startNextWaveNow() {
        if (_gameState.value == GameState.PREPARATION) {
            _gameState.value = GameState.WAVE_RUNNING
            audioManager.playSound(AudioManager.SoundEffect.WAVE_START)
        }
    }

    fun toggleGameSpeed() {
        _gameSpeed.value = if (_gameSpeed.value == 1.0f) 2.0f else 1.0f
    }

    /**
     * Savas alani (tuval) dokunus kabul ediyor mu? Modal aciken hayir.
     * Modallar pointer girdisini tuketmiyor ve dokunus alttaki tuvale
     * gecip kule insa ediyordu.
     */
    fun acceptsBattlefieldInput(): Boolean = when (_gameState.value) {
        GameState.PREPARATION, GameState.WAVE_RUNNING -> true
        else -> false
    }

    /**
     * Faz 3: uygulama arka plana gitti / reklam acildi. Simulasyon durur ve
     * oyuncu doner donmez ortasinda kalmis bir dalgayi kaybetmez; devam etmek
     * kasitli bir dokunus ister. Zaten PAUSED / MAIN_MENU / bitmis durumda
     * hicbir sey yapmaz, yani cift cagirma guvenlidir.
     */
    fun pauseForLifecycle() {
        when (val s = _gameState.value) {
            GameState.WAVE_RUNNING, GameState.PREPARATION -> {
                stateBeforePause = s
                _gameState.value = GameState.PAUSED
            }
            else -> {}
        }
    }

    /**
     * Duraklat / devam et.
     *
     * Devam ederken ONCEKI durum geri yuklenir. Eskiden her zaman WAVE_RUNNING'e
     * donuyordu: hazirlik asamasinda duraklatip devam eden oyuncu kalan hazirlik
     * suresini kaybediyor ve dalga aninda basliyordu — yani duraklatma tusu
     * oyuncuyu cezalandiriyordu.
     */
    fun togglePause() {
        when (val s = _gameState.value) {
            GameState.WAVE_RUNNING, GameState.PREPARATION -> {
                stateBeforePause = s
                _gameState.value = GameState.PAUSED
            }
            GameState.PAUSED -> _gameState.value = stateBeforePause
            else -> {}
        }
    }

    fun selectBuildSpot(spot: BuildSpot?) {
        _selectedTower.value = null
        _selectedBuildSpot.value = spot
        audioManager.playSound(AudioManager.SoundEffect.UI_CLICK)
    }

    fun selectTower(tower: TowerEntity?) {
        _selectedBuildSpot.value = null
        _selectedTower.value = tower
        audioManager.playSound(AudioManager.SoundEffect.UI_CLICK)
    }

    fun deselectAll() {
        _selectedBuildSpot.value = null
        _selectedTower.value = null
    }

    fun buildTower(type: GameConfig.TowerType): Boolean {
        if (!acceptsBattlefieldInput()) return false
        val spot = _selectedBuildSpot.value ?: return false
        val spec = GameConfig.TOWER_SPECS[type] ?: return false

        if (_gold.value < spec.buildCost) return false

        _gold.value -= spec.buildCost
        val newTower = TowerEntity(
            type = type,
            buildSpotId = spot.id,
            posX = spot.normX,
            posY = spot.normY,
            totalInvestedGold = spec.buildCost,
            damageMultiplier = metaDamageMultiplier,
            rangeMultiplier = metaRangeMultiplier,
            salvageRate = metaSalvageRate
        )
        towers.add(newTower)

        audioManager.playSound(AudioManager.SoundEffect.TOWER_BUILD)
        // Faz 3: insa geri bildirimi namlu alevi degil, TOZ bulutu.
        visualEffects.add(
            VisualEffect(
                type = EffectType.SMOKE_PUFF,
                posX = spot.normX,
                posY = spot.normY,
                maxAgeSeconds = 0.45f,
                scale = 1.3f
            )
        )

        _selectedBuildSpot.value = null
        _selectedTower.value = newTower
        return true
    }

    fun upgradeSelectedTower(): Boolean {
        if (!acceptsBattlefieldInput()) return false
        val tower = _selectedTower.value ?: return false
        val cost = tower.upgradeCost ?: return false

        if (_gold.value < cost) return false

        _gold.value -= cost
        tower.level = 2
        tower.totalInvestedGold += cost

        audioManager.playSound(AudioManager.SoundEffect.TOWER_UPGRADE)
        visualEffects.add(
            VisualEffect(
                type = EffectType.FROST_WAVE,
                posX = tower.posX,
                posY = tower.posY,
                maxAgeSeconds = 0.4f,
                scale = 1.5f
            )
        )

        _selectedTower.value = tower // Trigger StateFlow update
        return true
    }

    fun sellSelectedTower(): Boolean {
        if (!acceptsBattlefieldInput()) return false
        val tower = _selectedTower.value ?: return false

        val refund = tower.sellValue
        _gold.value += refund
        towers.remove(tower)

        audioManager.playSound(AudioManager.SoundEffect.TOWER_SELL)
        visualEffects.add(
            VisualEffect(
                type = EffectType.COIN_POPUP,
                posX = tower.posX,
                posY = tower.posY,
                maxAgeSeconds = 0.8f,
                text = "+$refund g"
            )
        )

        _selectedTower.value = null
        return true
    }

    fun cycleTargetingMode() {
        if (!acceptsBattlefieldInput()) return
        val tower = _selectedTower.value ?: return
        val modes = GameConfig.TargetingMode.values()
        val nextMode = modes[(tower.targetingMode.ordinal + 1) % modes.size]
        tower.targetingMode = nextMode
        _selectedTower.value = tower
        audioManager.playSound(AudioManager.SoundEffect.UI_CLICK)
    }

    // Core Frame Update Tick
    fun tick(deltaSeconds: Float) {
        // Beyaz liste: yeni bir GameState eklendiginde simulasyon KAZAYLA
        // kosmaya devam etmesin (LEVEL_SELECT eklenirken kara liste kirilgandi).
        when (_gameState.value) {
            GameState.PREPARATION, GameState.WAVE_RUNNING -> {}
            else -> return
        }

        val dt = deltaSeconds * _gameSpeed.value

        // Update Screen Shake
        if (screenShakeDuration > 0f) {
            screenShakeDuration -= dt
            val intensity = (screenShakeDuration * 15f).coerceAtMost(10f)
            _screenShake.value = Offset(
                Random.nextFloat() * intensity - intensity / 2f,
                Random.nextFloat() * intensity - intensity / 2f
            )
        } else {
            _screenShake.value = Offset.Zero
        }

        // Gorsel efektlerin yaslanmasi — HER FAZDA kosar.
        //
        // BUG (cihazda bulundu): bu blok eskiden "5. Visual Effects Updating"
        // adiminda, asagidaki PREPARATION erken-return'unun ALTINDAYDI. Dalganin
        // son patlamasi olusuyor, hemen ardindan dalga bitip state PREPARATION'a
        // geciyor ve efekt bir daha hic yaslanmadigi icin EKRANDA DONUP KALIYORDU
        // (sonraki dalga baslayana kadar). Efektler tamamen kozmetik; simulasyon
        // dursa da sonmeleri gerekir.
        val effectIterator = visualEffects.iterator()
        while (effectIterator.hasNext()) {
            val fx = effectIterator.next()
            fx.ageSeconds += dt
            if (fx.ageSeconds >= fx.maxAgeSeconds) {
                effectIterator.remove()
            }
        }

        // Preparation phase
        if (_gameState.value == GameState.PREPARATION) {
            _preparationTimer.value -= dt
            if (_preparationTimer.value <= 0f) {
                startNextWaveNow()
            }
            return
        }

        // 1. Wave Spawning logic
        if (pendingWaveSpawns.isNotEmpty()) {
            timeUntilNextSpawn -= dt
            if (timeUntilNextSpawn <= 0f) {
                val spawn = pendingWaveSpawns.removeAt(0)
                spawnEnemy(spawn.enemyType)
                timeUntilNextSpawn = spawn.delaySeconds
            }
        }

        // 2. Enemy Movement & Status Decay
        val enemyIterator = enemies.iterator()
        while (enemyIterator.hasNext()) {
            val enemy = enemyIterator.next()

            // Flash effect decay
            if (enemy.hitFlashTimerSeconds > 0f) {
                enemy.hitFlashTimerSeconds -= dt
            }

            // Slow decay
            enemy.activeSlow?.let { slow ->
                slow.durationRemainingSeconds -= dt
                if (slow.durationRemainingSeconds <= 0f) {
                    enemy.activeSlow = null
                }
            }

            // Move along this enemy's OWN route (catallanan haritalarda 2 rota).
            val route = routeFor(enemy)
            if (route.size > 1 && enemy.currentWayPointIndex < route.size - 1) {
                val targetPt = route[enemy.currentWayPointIndex + 1]
                val dx = targetPt.x - enemy.posX
                val dy = targetPt.y - enemy.posY
                val distToTarget = sqrt(dx * dx + dy * dy)

                enemy.rotationAngleRad = atan2(dy, dx)
                val moveDist = enemy.currentSpeed * dt

                if (distToTarget <= moveDist) {
                    // Reached waypoint
                    enemy.posX = targetPt.x
                    enemy.posY = targetPt.y
                    enemy.distanceTraveledPx += distToTarget
                    enemy.currentWayPointIndex++

                    // Check if reached final base
                    if (enemy.currentWayPointIndex >= route.size - 1) {
                        _lives.value -= GameConfig.BASE_REACHED_PENALTY_LIVES
                        // Faz 3: us hasari = agir patlama + sarsinti. Eskiden
                        // "dusman isabeti" sesi caliyordu, oyuncu can kaybini
                        // kendi vurusuyla karistiriyordu.
                        audioManager.playSound(AudioManager.SoundEffect.EXPLOSION_HEAVY)
                        triggerScreenShake(0.30f)
                        visualEffects.add(
                            VisualEffect(
                                type = EffectType.DAMAGE_TEXT,
                                posX = enemy.posX,
                                posY = enemy.posY - 10f,
                                maxAgeSeconds = 1.0f,
                                text = "-1 Life"
                            )
                        )
                        enemyIterator.remove()

                        if (_lives.value <= 0) {
                            _gameState.value = GameState.DEFEAT
                            audioManager.playSound(AudioManager.SoundEffect.DEFEAT)
                            return
                        }
                    }
                } else {
                    val ratio = moveDist / distToTarget
                    enemy.posX += dx * ratio
                    enemy.posY += dy * ratio
                    enemy.distanceTraveledPx += moveDist
                }
            }
        }

        // 3. Tower Targeting & Firing
        towers.forEach { tower ->
            if (tower.cooldownTimerSeconds > 0f) {
                tower.cooldownTimerSeconds -= dt
            }

            if (tower.recoilOffsetPx > 0f) {
                tower.recoilOffsetPx = (tower.recoilOffsetPx - dt * 25f).coerceAtLeast(0f)
            }

            // Target enemy
            val target = findTargetEnemyForTower(tower)
            if (target != null) {
                val dx = target.posX - tower.posX
                val dy = target.posY - tower.posY
                tower.targetAngleRad = atan2(dy, dx)

                // Rotate turret smoothly toward target
                var diff = tower.targetAngleRad - tower.currentAngleRad
                while (diff < -PI) diff += (PI * 2).toFloat()
                while (diff > PI) diff -= (PI * 2).toFloat()
                tower.currentAngleRad += diff * (dt * 12f).coerceAtMost(1f)

                // Fire if off cooldown
                if (tower.cooldownTimerSeconds <= 0f) {
                    tower.cooldownTimerSeconds = tower.fireRate
                    tower.recoilOffsetPx = 6f
                    fireTower(tower, target)
                }
            }
        }

        // 4. Projectile Updating & Collision Logic
        val projIterator = projectiles.iterator()
        while (projIterator.hasNext()) {
            val proj = projIterator.next()
            proj.progress += dt * proj.speed / 100f

            val dx = proj.targetX - proj.startX
            val dy = proj.targetY - proj.startY
            proj.posX = proj.startX + dx * proj.progress.coerceAtMost(1f)
            proj.posY = proj.startY + dy * proj.progress.coerceAtMost(1f)

            if (proj.progress >= 1f) {
                // Impact!
                onProjectileImpact(proj)
                projIterator.remove()
            }
        }

        // 5. Gorsel efektler YUKARIDA, PREPARATION erken-return'unun USTUNDE
        //    guncelleniyor (bkz. oradaki bug notu).

        // 6. Wave Completion Check
        if (pendingWaveSpawns.isEmpty() && enemies.isEmpty() && _gameState.value == GameState.WAVE_RUNNING) {
            val completedIdx = _currentWaveIndex.value
            if (completedIdx >= levelWaves.lastIndex) {
                // Victory!
                _gameState.value = GameState.VICTORY

                // Yildiz = kalan us cani YUZDESI (GDD B.3), mutlak can DEGIL.
                // Eskiden esikler 18/10 olarak sabit kodluydu; us cani bolume ve
                // meta yukseltmelere gore degistigi icin (20 -> meta ile 30) bu
                // yanlis yildiz veriyordu: 30 canli bir bolumu 18 canla bitirmek
                // %60 iken 3 yildiz sayiliyordu.
                val maxLives = levelSpec.maxBaseLives.coerceAtLeast(1)
                val livesFraction = _lives.value.toFloat() / maxLives
                val stars = when {
                    livesFraction >= GameConfig.STAR3_LIVES_FRACTION -> 3
                    livesFraction >= GameConfig.STAR2_LIVES_FRACTION -> 2
                    else -> 1
                }
                _lastEarnedStars.value = stars
                // Bolum ID'si de sabit `1` yaziliydi: hangi bolum bitirilirse
                // bitirilsin yildiz bolum 1'e kaydediliyordu.
                saveManager.setLevelStars(levelSpec.levelId, stars)
                saveManager.highScore = _score.value
                audioManager.playSound(AudioManager.SoundEffect.VICTORY)
            } else {
                // Next wave preparation
                _currentWaveIndex.value = completedIdx + 1
                _gold.value += 35 // Wave completion bonus
                setupWave(_currentWaveIndex.value)
                _gameState.value = GameState.PREPARATION
                _preparationTimer.value = GameConfig.PREPARATION_TIME_SECONDS.toFloat()
                audioManager.playSound(AudioManager.SoundEffect.COIN_EARNED)
            }
        }
    }

    /**
     * Bu dusmanin izledigi px rotasi. Rota indeksi gecersizse birincil rotaya
     * duser (bolum ortasinda harita degismez, ama savunmaci programlama).
     */
    private fun routeFor(enemy: EnemyEntity): List<PointF> =
        scaledRoutes.getOrNull(enemy.routeIndex) ?: scaledWaypoints

    private fun spawnEnemy(type: GameConfig.EnemyType) {
        if (scaledRoutes.isEmpty()) return
        val spec = GameConfig.ENEMY_SPECS[type] ?: return

        // Catallanan haritalarda rota atamasi: SEED'LI ve deterministik.
        // Seed bolume bagli (loadLevel), RNG render'dan tamamen ayri; ayni bolum
        // her oynanista ayni rota dizisini uretir.
        val routeIndex = if (scaledRoutes.size > 1) routeRng.nextInt(scaledRoutes.size) else 0
        val spawnPt = scaledRoutes[routeIndex].first()

        enemies.add(
            EnemyEntity(
                type = type,
                posX = spawnPt.x,
                posY = spawnPt.y,
                routeIndex = routeIndex,
                // ACT OLCEKLENDIRMESI: ikinci turda ayni harita tekrar
                // gorunuyor, dusman da guclenmeli yoksa tekrar gibi hissedilir.
                // HIZ olceklenmez (okunabilirlik + "sadece HP/hiz artirma"
                // yasagi); odul HP ile birlikte artar ki ekonomi tempo tutsun.
                hp = spec.maxHp * actHpMul,
                maxHp = spec.maxHp * actHpMul,
                baseSpeed = spec.baseSpeed,
                armor = spec.armor,
                rewardGold = (spec.rewardGold * actRewardMul).toInt().coerceAtLeast(1),
                radius = spec.sizeRadius
            )
        )
    }

    private fun findTargetEnemyForTower(tower: TowerEntity): EnemyEntity? {
        val range = tower.range
        val candidateEnemies = enemies.filter { enemy ->
            val dx = enemy.posX - tower.posX
            val dy = enemy.posY - tower.posY
            (dx * dx + dy * dy) <= range * range
        }

        if (candidateEnemies.isEmpty()) return null

        return when (tower.targetingMode) {
            GameConfig.TargetingMode.FIRST -> candidateEnemies.maxByOrNull { it.distanceTraveledPx }
            GameConfig.TargetingMode.LAST -> candidateEnemies.minByOrNull { it.distanceTraveledPx }
            GameConfig.TargetingMode.STRONGEST -> candidateEnemies.maxByOrNull { it.hp }
            GameConfig.TargetingMode.WEAKEST -> candidateEnemies.minByOrNull { it.hp }
        }
    }

    private fun fireTower(tower: TowerEntity, target: EnemyEntity) {
        val pType = when (tower.type) {
            GameConfig.TowerType.MACHINE_GUN -> ProjectileType.BULLET
            GameConfig.TowerType.CANNON -> ProjectileType.CANNON_SHELL
            GameConfig.TowerType.ANTI_ARMOR -> ProjectileType.RAILGUN_BEAM
            GameConfig.TowerType.SLOW -> ProjectileType.FROST_PULSE
        }

        val sound = when (tower.type) {
            GameConfig.TowerType.MACHINE_GUN -> AudioManager.SoundEffect.MACHINE_GUN
            GameConfig.TowerType.CANNON -> AudioManager.SoundEffect.CANNON_BOOM
            GameConfig.TowerType.ANTI_ARMOR -> AudioManager.SoundEffect.MISSILE_LAUNCH
            GameConfig.TowerType.SLOW -> AudioManager.SoundEffect.FROST_PULSE
        }
        // Ses, sarsinti ve gorsel efekt AYNI KAREDE tetiklenir.
        audioManager.playSound(sound)

        // Namlu alevi: YONLU sprite. Namlu ucu ofseti referans tuvalde tanimli.
        val muzzleOffset = GameConfig.MUZZLE_OFFSET_REF_PX * renderScale
        visualEffects.add(
            VisualEffect(
                type = EffectType.MUZZLE_FLASH,
                posX = tower.posX + cos(tower.currentAngleRad) * muzzleOffset,
                posY = tower.posY + sin(tower.currentAngleRad) * muzzleOffset,
                maxAgeSeconds = 0.13f,
                scale = if (tower.type == GameConfig.TowerType.CANNON) 1.5f else 1.0f,
                angleRad = tower.currentAngleRad
            )
        )

        val projSpeed = when (tower.type) {
            GameConfig.TowerType.MACHINE_GUN -> 280f
            GameConfig.TowerType.CANNON -> 160f
            GameConfig.TowerType.ANTI_ARMOR -> 450f
            GameConfig.TowerType.SLOW -> 300f
        }

        projectiles.add(
            ProjectileEntity(
                type = pType,
                posX = tower.posX,
                posY = tower.posY,
                startX = tower.posX,
                startY = tower.posY,
                targetEnemyId = target.id,
                targetX = target.posX,
                targetY = target.posY,
                damage = tower.damage,
                speed = projSpeed,
                splashRadius = tower.stats.splashRadius,
                armorPierce = tower.stats.armorPierce,
                slowFactor = tower.stats.slowFactor,
                slowDuration = tower.stats.slowDuration,
                towerType = tower.type
            )
        )
    }

    private fun onProjectileImpact(proj: ProjectileEntity) {
        if (proj.splashRadius > 0f) {
            // Area of effect explosion
            triggerScreenShake(0.25f)
            audioManager.playSound(AudioManager.SoundEffect.EXPLOSION)

            visualEffects.add(
                VisualEffect(
                    type = EffectType.CANNON_EXPLOSION,
                    posX = proj.targetX,
                    posY = proj.targetY,
                    maxAgeSeconds = 0.4f,
                    scale = proj.splashRadius / 35f
                )
            )

            // Damage all enemies in splash radius
            val splashSq = proj.splashRadius * proj.splashRadius
            val affected = enemies.filter { enemy ->
                val dx = enemy.posX - proj.targetX
                val dy = enemy.posY - proj.targetY
                (dx * dx + dy * dy) <= splashSq
            }

            affected.forEach { enemy ->
                applyDamageToEnemy(
                    enemy, proj.damage, proj.armorPierce,
                    proj.slowFactor, proj.slowDuration, isSplash = true
                )
            }
        } else {
            // Single target impact
            val target = enemies.find { it.id == proj.targetEnemyId } ?: enemies.minByOrNull {
                val dx = it.posX - proj.targetX
                val dy = it.posY - proj.targetY
                dx * dx + dy * dy
            }

            target?.let { enemy ->
                applyDamageToEnemy(enemy, proj.damage, proj.armorPierce, proj.slowFactor, proj.slowDuration)
            }

            // Faz 3: tek hedef isabetinde artik NAMLU ALEVI degil, isabet
            // kivilcimi cizilir. Namlu alevi yalnizca ates aninda kullanilir.
            val fxType = when (proj.towerType) {
                GameConfig.TowerType.ANTI_ARMOR -> EffectType.RAIL_BEAM_BURST
                GameConfig.TowerType.SLOW -> EffectType.FROST_WAVE
                else -> EffectType.HIT_SPARK
            }
            if (proj.towerType == GameConfig.TowerType.ANTI_ARMOR) {
                audioManager.playSound(AudioManager.SoundEffect.EXPLOSION)
            } else {
                audioManager.playSound(AudioManager.SoundEffect.ENEMY_HIT)
            }

            visualEffects.add(
                VisualEffect(
                    type = fxType,
                    posX = proj.targetX,
                    posY = proj.targetY,
                    maxAgeSeconds = 0.25f,
                    angleRad = atan2(proj.targetY - proj.startY, proj.targetX - proj.startX)
                )
            )
        }
    }

    /**
     * @param isSplash Bu hasar bir PATLAMA bilesenimi (DECISIONS B2)?
     *   Splash bileseni zirhi **BYPASS EDER** ve hedefin
     *   `splashVulnerability` carpaniyla olceklenir; dogrudan isabete bu carpan
     *   uygulanmaz. Boylece "kursuna direncli / patlamaya zayif" dusman
     *   (SHIELDED_TROOPER) tasarimi mumkun olur ve armorPierce'i 0 olan Cannon
     *   gec oyunda olu kalmaz.
     */
    private fun applyDamageToEnemy(
        enemy: EnemyEntity,
        rawDamage: Float,
        armorPierce: Float,
        slowFactor: Float,
        slowDuration: Float,
        isSplash: Boolean = false
    ) {
        val finalDamage = if (isSplash) {
            rawDamage * enemy.stats.splashVulnerability
        } else {
            val effectiveArmor = (enemy.armor * (1f - armorPierce)).coerceAtLeast(0f)
            rawDamage * (1f - effectiveArmor)
        }

        enemy.hp -= finalDamage
        enemy.hitFlashTimerSeconds = 0.12f

        // Apply slow if present
        if (slowFactor > 0f) {
            enemy.activeSlow = SlowStatus(slowFactor, slowDuration)
        }

        if (enemy.isDead) {
            onEnemyKilled(enemy)
        }
    }

    private fun onEnemyKilled(enemy: EnemyEntity) {
        enemies.remove(enemy)
        _gold.value += enemy.rewardGold
        _score.value += enemy.rewardGold * 10
        saveManager.addEnemiesKilled(1)

        // Faz 3: arac olumu piyade olumunden AYRI duyulur; asset pack'teki
        // 15_vehicle_destroyed ve 05_explosion_heavy boylece kullanilir.
        val isVehicle = enemy.type == GameConfig.EnemyType.ARMORED_VEHICLE ||
            enemy.type == GameConfig.EnemyType.TANK ||
            enemy.type == GameConfig.EnemyType.COMMAND_TANK
        if (isVehicle) {
            audioManager.playSound(AudioManager.SoundEffect.VEHICLE_DESTROYED)
            // Boss olumu en agir geri bildirimi alir; sarsinti her zaman
            // sonumlenerek sifira iner (surekli sarsinti okunabilirligi bozar).
            triggerScreenShake(
                when (enemy.type) {
                    GameConfig.EnemyType.COMMAND_TANK -> 0.38f
                    GameConfig.EnemyType.TANK -> 0.22f
                    else -> 0.14f
                }
            )
        }
        audioManager.playSound(AudioManager.SoundEffect.COIN_EARNED)

        // Death effect and coin popup
        visualEffects.add(
            VisualEffect(
                type = EffectType.ENEMY_DEATH,
                posX = enemy.posX,
                posY = enemy.posY,
                maxAgeSeconds = if (isVehicle) 0.60f else 0.45f,
                scale = enemy.radius / 15f
            )
        )

        visualEffects.add(
            VisualEffect(
                type = EffectType.COIN_POPUP,
                posX = enemy.posX,
                posY = enemy.posY,
                maxAgeSeconds = 0.9f,
                text = "+${enemy.rewardGold}g"
            )
        )
    }

    fun triggerScreenShake(durationSeconds: Float) {
        screenShakeDuration = durationSeconds
    }
}
