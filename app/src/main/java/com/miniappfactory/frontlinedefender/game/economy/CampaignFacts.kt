package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions

/**
 * Faz 10.1 — EKONOMI MODELININ **CANLI GIRDISI**.
 *
 * ---------------------------------------------------------------------------------
 * NEDEN BU DOSYA VAR (ve neden ONCE yoktu)
 * ---------------------------------------------------------------------------------
 * Faz 10'da [SupplyBudgetModel] oldurme gelirini `TIGHTENED_WAVE_KILL_SUPPLY`
 * adinda **elle yazilmis bir diziden** okuyordu. Dizi yazildigi gunun dalga
 * tablosundan turetilmisti. Sonra kule ajani dalgalari sikilastirdi (L1-L7
 * kompozisyon + kadans) ve dizi **sessizce yanlis** oldu: model L1 icin 284
 * Tedarik diyordu, oyun 400 veriyordu. SPI tablosu bu yuzden hedef bandin
 * ustune cikti ve bunu kimse fark etmedi — cunku hicbir test iki tarafi
 * karsilastirmiyordu.
 *
 * Bu hatayi PROJEDE IKINCI KEZ yaptik. Ilki `WaveMetrics.AEHP` elle yazilmisken
 * yasandi: dusman canlari degisti, tablo kaldi, butun zorluk egrisi sessizce
 * yaniltici hale geldi. Kule ajani onu `docs/tools/difficulty_audit.py`'yi
 * Kotlin'i PARSE edecek sekilde yeniden yazarak cozdu; bu dosya ayni cozumun
 * uygulama icindeki karsiligidir.
 *
 * ---------------------------------------------------------------------------------
 * KURAL
 * ---------------------------------------------------------------------------------
 * Ekonomi katmani bir denge sayisini **asla kopyalamaz**. Dalga kompozisyonu,
 * dusman odulu, kule fiyati ve kule kilidi icin tek dogruluk kaynagi
 * [GameConfig] + [WaveDefinitions]'dir. Ekonomi katmani yalnizca *kendi*
 * sayilarina (baslangic Tedariki, tasarlanan kadro adedi, hedef bant,
 * guclendirici fiyatlari) sahiptir.
 *
 * ---------------------------------------------------------------------------------
 * NEDEN ARADA BIR ARAYUZ VAR
 * ---------------------------------------------------------------------------------
 * [SupplyBudgetModel] `game/model`e dogrudan bagli olsa da derlenirdi (bagimlilik
 * yonu ekonomi -> model, ters yon YOK, dolayisiyla dongu de yok). Arayuz iki sey
 * icin duruyor:
 *
 *  1. **Test edilebilirlik:** bir testin "dalga tablosu X olsaydi SPI ne olurdu"
 *     sorusunu sormasi icin sahte bir [CampaignFacts] yeterli; `GameConfig`
 *     kopyalamak gerekmiyor.
 *  2. **Sinir netligi:** modelin dis dunyadan neye BAKTIGI tek bir yerde,
 *     sayilabilir halde duruyor. Yeni bir alan gerekiyorsa buraya eklenir ve
 *     gozden kacmaz.
 *
 * Model tarafi hâlâ Android'den ve `game/model`den BAGIMSIZDIR; yalnizca bu
 * dosya `game/model` importu tasir.
 */
interface CampaignFacts {

    /** Bu bolumdeki dalga sayisi. */
    fun waveCount(level: Int): Int

    /**
     * Bu bolumde **bolum boyunca toplam** kac tane hangi dusman geliyor.
     * Anahtar: `GameConfig.EnemyType` adi.
     */
    fun enemyCounts(level: Int): Map<String, Int>

    /** Dusman tipi adi -> oldurme Tedarik odulu (`EnemyStats.rewardGold`). */
    val enemySupplyReward: Map<String, Int>

    /** Dusman tipi adi -> maks can (`EnemyStats.maxHp`). Guclendirici denetimi kullanir. */
    val enemyMaxHp: Map<String, Double>

    /** Kule tipi adi -> insa bedeli (`TowerStats.buildCost`). */
    val towerBuildCost: Map<String, Int>

    /** Kule tipi adi -> kademe 2 yukseltme bedeli (`TowerStats.level2UpgradeCost`). */
    val towerUpgradeCost: Map<String, Int>

    /**
     * Kule tipi adi -> kademe 3 yukseltme bedeli (`upgradeCostFrom(2)`).
     *
     * Kademe 3 `GameConfig.TIER_THREE_UNLOCK_LEVEL`de (L12) bedava acilir ve
     * CAMPAIGN_55.md 9. tablosunun `kd3` kolonu Act II'den itibaren tasarlanan
     * kadronun bir kismini kademe 3'e cikarir. Bu olmadan gec bolumlerin
     * bolen tarafi (I(L)) gercekte odenen paranin ~%40 altinda kalirdi ve SPI
     * oldugundan yuksek olculurdu.
     */
    val towerTierThreeUpgradeCost: Map<String, Int>

    /** Kule tipi adi -> acildigi bolum (`TowerStats.unlockedAtLevel`). */
    val towerUnlockLevel: Map<String, Int>

    /**
     * Bu bolumun turunun (Act) odul carpani. Act II'de dusman canı ve odulu
     * birlikte olceklenir; ekonomi modeli bunu yok sayarsa Act II gelirini
     * %30 eksik olcer.
     */
    fun actRewardMultiplier(level: Int): Double

    /** Bu bolumun turunun can carpani. Guclendirici denetimi kullanir. */
    fun actHpMultiplier(level: Int): Double

    /**
     * Bu bolumde KAPSAMA KIT mi — iki kollu harita ya da dar tahta?
     *
     * Ekonomi bunu bilmek ZORUNDA: her iki durumda da bir pad'in gordugu yol
     * payi yetmez ve oyuncu ayni tehdide karsi daha fazla cephe kurar.
     * Baslangic sermayesi bunu karsilamazsa bolum acilis dalgasinda kaybedilir
     * (olculdu — bkz. `SupplyBudgetModel.COVERAGE_SCARCITY_SUPPLY_FACTOR`).
     */
    fun hasScarceCoverage(level: Int): Boolean

}

// =====================================================================================
// TURETILMIS HESAPLAR — ARAYUZ UYESI DEGIL, EXTENSION. BUNU DEGISTIRMEYIN.
// =====================================================================================
//
// Bu iki hesap once arayuzun *default metodu* idi ve `designedLoadoutIsPricedFrom
// LiveTowerSpecs` testi onlari kirmizi yakaladi. Sebep Kotlin'in **arayuz delegasyonu
// tuzagi**:
//
//     val pricier = object : CampaignFacts by GameConfigCampaignFacts {
//         override val towerBuildCost = ... // iki katina cikarilmis
//     }
//     pricier.tierTwoCost("MACHINE_GUN")    // <-- ESKI FIYATI dondururdu
//
// `by` derleyiciye arayuzun HER uyesi icin — govdesi olanlar dahil — delege nesneye
// giden bir kopru urettiriyor. Yani `tierTwoCost` cagrisi `GameConfigCampaignFacts`
// ustunde calisiyor ve o da KENDI `towerBuildCost`unu okuyordu; override hic devreye
// girmiyordu. Bir sahte tablo, modelin gercekten ona baktigini kanitlayamaz hale
// geliyordu.
//
// Extension olarak `this` statik olarak `CampaignFacts` tipindedir ve icerideki
// `towerBuildCost` normal bir SANAL property erisimidir -> override'a duser.
// Ayni sebeple bunlari tekrar arayuz uyesi yapmayin: dosyanin en basindaki
// "ekonomi katmani bir denge sayisini asla kopyalamaz" kuralini sessizce delerler.

/**
 * Bu bolumde insa edilebilen kule tipleri, **acilis sirasinda** (once acilan
 * once; ayni bolumde acilan iki kule varsa ucuz olan once).
 *
 * Siralama deterministik olmak ZORUNDA: tasarlanan kadro bilesimi bundan
 * turetiliyor, yani sira degisirse SPI bolen tarafi sessizce degisir.
 */
fun CampaignFacts.unlockedTowersInOrder(level: Int): List<String> {
    val byName = towerUnlockLevel.filterValues { it <= level }.keys
    return byName.sortedWith(
        compareBy({ towerUnlockLevel.getValue(it) }, { towerBuildCost.getValue(it) }, { it }),
    )
}

/** Kademe 2'ye kadar bir kulenin tam maliyeti (insa + yukseltme). */
fun CampaignFacts.tierTwoCost(tower: String): Int =
    towerBuildCost.getValue(tower) + towerUpgradeCost.getValue(tower)

/** Kademe 2'den kademe 3'e cikmanin bedeli. */
fun CampaignFacts.tierThreeStep(tower: String): Int =
    towerTierThreeUpgradeCost.getValue(tower)

/**
 * Gercek oyunun sayilari. Uretimdeki tek [CampaignFacts] uygulamasi.
 *
 * Tum haritalar `by lazy` degil dogrudan `map` ile kuruluyor: `GameConfig` bir
 * `object` ve bu da bir `object`, ilk erisimde bir kez donusturulur.
 */
object GameConfigCampaignFacts : CampaignFacts {

    override fun waveCount(level: Int): Int = WaveDefinitions.wavesFor(level).size

    override fun enemyCounts(level: Int): Map<String, Int> {
        val out = HashMap<String, Int>()
        WaveDefinitions.wavesFor(level).forEach { wave ->
            wave.spawns.forEach { spawn ->
                val key = spawn.enemyType.name
                out[key] = (out[key] ?: 0) + 1
            }
        }
        return out
    }

    override val enemySupplyReward: Map<String, Int> =
        GameConfig.ENEMY_SPECS.entries.associate { (type, stats) -> type.name to stats.rewardGold }

    override val enemyMaxHp: Map<String, Double> =
        GameConfig.ENEMY_SPECS.entries.associate { (type, stats) -> type.name to stats.maxHp.toDouble() }

    override val towerBuildCost: Map<String, Int> =
        GameConfig.TOWER_SPECS.entries.associate { (type, stats) -> type.name to stats.buildCost }

    override val towerUpgradeCost: Map<String, Int> =
        GameConfig.TOWER_SPECS.entries.associate { (type, stats) -> type.name to stats.level2UpgradeCost }

    override val towerTierThreeUpgradeCost: Map<String, Int> =
        GameConfig.TOWER_SPECS.entries.associate { (type, stats) ->
            type.name to (stats.upgradeCostFrom(2) ?: 0)
        }

    override val towerUnlockLevel: Map<String, Int> =
        GameConfig.TOWER_SPECS.entries.associate { (type, stats) -> type.name to stats.unlockedAtLevel }

    override fun actRewardMultiplier(level: Int): Double =
        GameConfig.actRewardMultiplier(GameConfig.levelSpec(level).act).toDouble()

    override fun actHpMultiplier(level: Int): Double =
        GameConfig.actHpMultiplier(GameConfig.levelSpec(level).act).toDouble()

    override fun hasScarceCoverage(level: Int): Boolean = GameConfig.hasScarceCoverage(level)
}
