package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * REKLAMSIZ + SIFIR SATIN ALMA EKONOMI DENETIMI — olcum araci, iddia degil (her test yesildir;
 * yalnizca sert degismezleri assert eder, tablolari stdout'a basar).
 *
 * Soru: butun reklamlar kalkmis ve hicbir IAP yapilmamisken (a) ekonomi saglam mi, (b) yeni
 * COIN fiyatli haklar (devam, acil tedarik, hava destegi, tamir 1./2. kullanim) erken (L1-11),
 * orta (L12-33) ve gec (L34-55) donemde erisilebilir mi ve (c) bunlar kampanya ilerlemesini
 * tehlikeye atiyor mu?
 *
 * ## Iki oyuncu tanimi (ikisi de `resolveLevelClear`/`applyLevelClear` gercek zafer yolundan gecer)
 *  - **HOARD (sifir harcama)**: her bolumu ac, gec, HICBIR SEY satin alma; gorev/reklam/tekrar/
 *    basarim geliri YOK. Kod, `CoinLedgerTest.runCampaign` (:91-105) ile ayni dongudur (KOPYALANDI,
 *    dosya baska muhendisin elinde ve derlenmiyor).
 *  - **GREEDY (acgozlu)**: HOARD + her bolum temizlendikten sonra `purchaseAllowed` izin verdigi
 *    surece EN UCUZ meta rank'i al. Dongu `CoinLedgerTest.treeCompletionLevel` (:276-300) ve
 *    `EconomySimulationTest.greedyUpgradeSpenderNeverSoftLocks` (:442-453) ile ayni (KOPYALANDI).
 *  - **GREEDY+PRESTIGE**: GREEDY + agac bittikten/karsilanamadiktan sonra `prestigeAllowed`
 *    izin verdigi surece prestij nisani (saf sink; en dusuk savas-ici bakiye profili).
 *
 * "Savas sirasindaki cuzdan" = bolum L acildiktan SONRA (kilit bedeli odenmis), zaferden ONCE.
 * Harcanabilir bakiye = `spendableBalance(wallet)` = coin - `reserveFor(wallet)` (kodun kendisi).
 * Yildiz bandi 1/2/3: onceki tum bolumlerin o yildizla temizlendigi cuzdan (5/12/20 can).
 *
 * Yalnizca JVM; `CampaignProgressImpl` bolumu (B) SaveManager(InMemoryKeyValueStore) uzerinden
 * gercek kodu kosar. stdout: app/build/test-results/testDebugUnitTest/TEST-<fqcn>.xml <system-out>.
 */
class AdsOffEconomyAuditTool {

    // =================================================================================
    // Ortak yardimcilar (harness'tan kopyalanan mantik)
    // =================================================================================

    private val maxLives = 20

    /** CoinLedgerTest.livesForStars (:73-77) — yildiz esikleri yuzde: %90 -> 3, %50 -> 2. */
    private fun livesForStars(stars: Int): Int = when (stars) {
        3 -> 20
        2 -> 12
        else -> 5
    }

    private enum class Profile { HOARD, GREEDY, GREEDY_PRESTIGE }

    private class Walk(
        val profile: Profile,
        val stars: Int,
        /** index = level-1: bolum acildiktan sonra, zaferden once. */
        val battle: List<PlayerWallet>,
        /** index = level-1: zaferden (ve profil harcamalarindan) sonra, siradaki kilit ACILMADAN. */
        val afterClear: List<PlayerWallet>,
        val finalWallet: PlayerWallet,
        val finalUpgrades: MetaUpgrades,
        val prestigeRank: Int,
    )

    private fun walk(profile: Profile, stars: Int): Walk {
        var wallet = PlayerWallet()
        var upgrades = MetaUpgrades()
        var prestige = 0
        val battle = mutableListOf<PlayerWallet>()
        val after = mutableListOf<PlayerWallet>()
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            if (level !in wallet.unlockedLevels) {
                assertTrue("${profile} ${stars}* L$level acilamadi — SOFT-LOCK", canUnlock(wallet, level))
                wallet = applyUnlock(wallet, level)
            }
            assertEquals(
                "${profile} ${stars}* L$level: otomatik hibe tetiklenirdi",
                0, autoGrantShortfall(wallet),
            )
            battle += wallet
            wallet = applyLevelClear(wallet, resolveLevelClear(wallet, level, livesForStars(stars), maxLives))
            if (profile != Profile.HOARD) {
                // CoinLedgerTest.treeCompletionLevel :286-296 — en ucuz alinabilir rank, alinabildigi surece.
                while (true) {
                    val best = UpgradeLine.entries
                        .mapNotNull { line ->
                            val d = purchaseAllowed(wallet, upgrades, line)
                            if (d is PurchaseDecision.Allowed) line to d.price else null
                        }
                        .minByOrNull { it.second } ?: break
                    val (w, u) = applyPurchase(wallet, upgrades, best.first)
                    wallet = w
                    upgrades = u
                }
                if (profile == Profile.GREEDY_PRESTIGE) {
                    while (prestigeAllowed(wallet, prestige) is PrestigeDecision.Allowed) {
                        val (w, r) = applyPrestige(wallet, prestige)
                        wallet = w
                        prestige = r
                    }
                }
            }
            after += wallet
        }
        assertEquals(55, wallet.clearedLevels.size)
        return Walk(profile, stars, battle, after, wallet, upgrades, prestige)
    }

    private class Cell(val name: String, val price: Int, val unlocked: Boolean, val spendable: Int) {
        val affordable: Boolean get() = unlocked && spendable >= price
        val ratio: Double
            get() = if (spendable == 0) Double.POSITIVE_INFINITY else price.toDouble() / spendable
    }

    /** Bes hak; her hucre GERCEK karar fonksiyonlariyla capraz dogrulanir. */
    private fun cells(level: Int, wallet: PlayerWallet): List<Cell> {
        val spendable = spendableBalance(wallet)
        val st = BoosterState.startBattle(level)
        val st2 = BoosterState(level = level, paidUses = mapOf(BoosterType.BASE_REPAIR to 1))
        val cont = Cell("CONT", continuePrice(level), true, spendable)
        val emer = Cell(
            "EMER", boosterPrice(BoosterType.EMERGENCY_SUPPLY, level),
            boosterUnlocked(BoosterType.EMERGENCY_SUPPLY, level), spendable,
        )
        val air = Cell(
            "AIR", boosterPrice(BoosterType.AIR_SUPPORT, level),
            boosterUnlocked(BoosterType.AIR_SUPPORT, level), spendable,
        )
        val rep1 = Cell(
            "REP1", boosterPrice(BoosterType.BASE_REPAIR, level, 1),
            boosterUnlocked(BoosterType.BASE_REPAIR, level), spendable,
        )
        val rep2 = Cell(
            "REP2", boosterPrice(BoosterType.BASE_REPAIR, level, 2),
            boosterUnlocked(BoosterType.BASE_REPAIR, level), spendable,
        )
        // Capraz dogrulama: tablo tanimi (harcanabilir >= fiyat) = kodun gercek karari.
        assertEquals("CONT L$level", cont.affordable, continueAllowed(wallet, level, 0).isAllowed)
        assertEquals(
            "EMER L$level", emer.affordable,
            boosterAllowed(st, BoosterType.EMERGENCY_SUPPLY, wallet).isAllowed,
        )
        assertEquals(
            "AIR L$level", air.affordable,
            boosterAllowed(st, BoosterType.AIR_SUPPORT, wallet, enemiesOnField = 5).isAllowed,
        )
        assertEquals(
            "REP1 L$level", rep1.affordable,
            boosterAllowed(st, BoosterType.BASE_REPAIR, wallet, baseHealth = 8, maxBaseHealth = 20).isAllowed,
        )
        assertEquals(
            "REP2 L$level", rep2.affordable,
            boosterAllowed(st2, BoosterType.BASE_REPAIR, wallet, baseHealth = 8, maxBaseHealth = 20).isAllowed,
        )
        return listOf(cont, emer, air, rep1, rep2)
    }

    private fun f2(r: Double): String = if (r.isInfinite()) "inf" else String.format(Locale.ROOT, "%.2f", r)

    private fun cellText(c: Cell): String = when {
        !c.unlocked -> String.format(Locale.ROOT, "%s    -           ", c.name)
        else -> String.format(
            Locale.ROOT, "%s %5d %5s %s", c.name, c.price, f2(c.ratio), if (c.affordable) "Y" else "n",
        )
    }

    private fun stageOf(level: Int): String = when {
        level <= 11 -> "EARLY L1-11"
        level <= 33 -> "MID   L12-33"
        else -> "LATE  L34-55"
    }

    private val stages = listOf("EARLY L1-11" to (1..11), "MID   L12-33" to (12..33), "LATE  L34-55" to (34..55))

    // =================================================================================
    // (A) Erisilebilirlik tablolari
    // =================================================================================

    @Test
    fun affordabilityTablesForHoarderAndGreedy() {
        println("==== (A) ERISILEBILIRLIK — ${EconomyConfig.CAMPAIGN_LEVELS} bolum, reklam yok, IAP yok ====")
        println("Satir: L | W=savas cuzdani | res=rezerv | sp=harcanabilir | HAK fiyat fiyat/sp Y=karsilanir n=karsilanmaz")
        println("Profil tanimlari: bkz. sinif KDoc'u (HOARD/GREEDY/GREEDY+PRESTIGE).")

        // Hard invariant: fiyat merdiveni.
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            val r1 = levelReward(level, 1)
            assertEquals("L$level devam = elit bilet (%50)", eliteTicketPrice(level), continuePrice(level))
            assertTrue("L$level devam < R1", continuePrice(level) < r1)
            assertTrue("L$level acil tedarik < R1", boosterPrice(BoosterType.EMERGENCY_SUPPLY, level) < r1)
            assertTrue("L$level hava < R1", boosterPrice(BoosterType.AIR_SUPPORT, level) < r1)
            assertTrue("L$level tamir1 < R1", boosterPrice(BoosterType.BASE_REPAIR, level, 1) < r1)
            assertTrue(
                "L$level hava > tamir1",
                boosterPrice(BoosterType.AIR_SUPPORT, level) > boosterPrice(BoosterType.BASE_REPAIR, level, 1),
            )
        }

        println()
        println("---- FIYAT MERDIVENI (fiyat ve R(L,1)'e orani) ----")
        println("  L    R1  elit   CONT  %R1   EMER  %R1    AIR  %R1   REP1  %R1   REP2  %R1  (REP2>R1?)")
        var rep2AboveR1 = 0
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            val r1 = levelReward(level, 1)
            val c = continuePrice(level)
            val e = boosterPrice(BoosterType.EMERGENCY_SUPPLY, level)
            val a = boosterPrice(BoosterType.AIR_SUPPORT, level)
            val p1 = boosterPrice(BoosterType.BASE_REPAIR, level, 1)
            val p2 = boosterPrice(BoosterType.BASE_REPAIR, level, 2)
            if (level >= BoosterType.BASE_REPAIR.unlockLevel && p2 > r1) rep2AboveR1++
            fun pct(x: Int) = String.format(Locale.ROOT, "%3d%%", Math.round(100.0 * x / r1))
            println(
                String.format(
                    Locale.ROOT, "%3d %5d %5d  %5d %s %5d %s %5d %s %5d %s %5d %s  %s",
                    level, r1, eliteTicketPrice(level), c, pct(c),
                    e, if (level >= BoosterType.EMERGENCY_SUPPLY.unlockLevel) pct(e) else "  - ",
                    a, if (level >= BoosterType.AIR_SUPPORT.unlockLevel) pct(a) else "  - ",
                    p1, if (level >= BoosterType.BASE_REPAIR.unlockLevel) pct(p1) else "  - ",
                    p2, if (level >= BoosterType.BASE_REPAIR.unlockLevel) pct(p2) else "  - ",
                    if (level >= BoosterType.BASE_REPAIR.unlockLevel && p2 > r1) "REP2>R1" else "",
                )
            )
        }
        println(
            "REP2 fiyati R(L,1)'i asiyor: $rep2AboveR1 / ${EconomyConfig.CAMPAIGN_LEVELS - BoosterType.BASE_REPAIR.unlockLevel + 1} " +
                "acik bolum (L${BoosterType.BASE_REPAIR.unlockLevel}..55)"
        )

        val walks = Profile.entries.flatMap { p -> (1..3).map { s -> walk(p, s) } }

        for (w in walks) {
            val full = w.profile != Profile.GREEDY_PRESTIGE
            println()
            println("---- ${w.profile} ${w.stars}-yildiz bandi: savas cuzdani ve hak fiyatlari ----")
            if (full) {
                println("  L        W  res       sp | CONT fiyat oran a | EMER ... | AIR ... | REP1 ... | REP2 ...")
            }
            for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
                val wallet = w.battle[level - 1]
                val cs = cells(level, wallet)
                if (full) {
                    println(
                        String.format(
                            Locale.ROOT, "%3d %8d %4d %8d | %s",
                            level, wallet.coins, reserveFor(wallet), spendableBalance(wallet),
                            cs.joinToString(" | ") { cellText(it) },
                        )
                    )
                }
            }
            // Donem ozeti.
            println("  -- donem ozeti (${w.profile} ${w.stars}*): karsilanan bolum / hak-acik bolum ; en kucuk-en buyuk fiyat/sp orani --")
            for ((label, range) in stages) {
                val parts = mutableListOf<String>()
                for (name in listOf("CONT", "EMER", "AIR", "REP1", "REP2")) {
                    var open = 0
                    var ok = 0
                    var mn = Double.POSITIVE_INFINITY
                    var mx = 0.0
                    for (level in range) {
                        val c = cells(level, w.battle[level - 1]).first { it.name == name }
                        if (!c.unlocked) continue
                        open++
                        if (c.affordable) ok++
                        val r = c.ratio
                        if (r < mn) mn = r
                        if (r > mx) mx = r
                    }
                    parts += if (open == 0) "$name -" else
                        "$name $ok/$open [${f2(mn)}..${f2(mx)}]"
                }
                println("   $label: ${parts.joinToString("  ")}")
            }
        }

        // Defter capraz dogrulamasi: reklamsiz ledger, CoinLedgerTest'in (reklamsiz) sayilariyla ayni.
        val hoard = walks.filter { it.profile == Profile.HOARD }.associateBy { it.stars }
        val tree = EconomyConfig.TREE_TOTAL_COST
        assertEquals(29_145, hoard.getValue(1).finalWallet.coins)
        assertEquals(44_845, hoard.getValue(2).finalWallet.coins)
        assertEquals(64_895, hoard.getValue(3).finalWallet.coins)
        assertEquals(9_545, hoard.getValue(1).finalWallet.coins - tree)
        assertEquals(25_245, hoard.getValue(2).finalWallet.coins - tree)
        assertEquals(45_295, hoard.getValue(3).finalWallet.coins - tree)
    }

    // =================================================================================
    // (A2) Her seyi kullanirsan: bolum basina toplam opsiyonel harcama vs ilk temizleme odulu
    // =================================================================================

    private class UseAll(
        val sticker: Int,
        val r1: Int,
        val spent: Int,
        val count: Int,
        val possible: Int,
        val endWallet: PlayerWallet,
        val continued: Boolean,
    )

    /**
     * Sirali "HER SEYI KULLAN": acil tedarik -> hava -> hava -> tamir -> tamir -> (yenilgi) devam.
     * Her adim GERCEK karar fonksiyonundan gecer; karsilanmayan/rezerve takilan adim atlanir.
     * Bekleme suresi (45/60 sn) her adimda 100 sn ilerletilerek gecilir.
     */
    private fun useEverything(level: Int, start: PlayerWallet, replay: Boolean): UseAll {
        var w = start
        var st = BoosterState.startBattle(level)
        var now = 1_000_000L
        var spent = 0
        var n = 0
        var possible = 0
        val order = listOf(
            BoosterType.EMERGENCY_SUPPLY, BoosterType.AIR_SUPPORT, BoosterType.AIR_SUPPORT,
            BoosterType.BASE_REPAIR, BoosterType.BASE_REPAIR,
        )
        for (type in order) {
            if (boosterUnlocked(type, level)) possible++
            val d = boosterAllowed(st, type, w, baseHealth = 8, maxBaseHealth = 20, enemiesOnField = 5, nowMs = now)
            if (d is BoosterDecision.Allowed) {
                val restored = if (type == BoosterType.BASE_REPAIR) {
                    baseRepairAmount(8, 20, st.paidUsesOf(type) + 1)
                } else 0
                w = payForBooster(w, d)
                st = useBooster(st, type, d, now, restored)
                spent += d.price
                n++
                if (replay) {
                    assertTrue("replay L$level: bakiye rezervin altina indi", w.coins >= reserveFor(w))
                }
                assertEquals("L$level: otomatik hibe tetiklenirdi", 0, autoGrantShortfall(w))
                now += 100_000L
            }
        }
        possible++ // devam
        var continued = false
        val cd = continueAllowed(w, level, 0)
        if (cd is ContinueDecision.Allowed) {
            w = w.debited(cd.price)
            spent += cd.price
            n++
            continued = true
            if (replay) {
                assertTrue("replay L$level: devam sonrasi bakiye rezervin altina indi", w.coins >= reserveFor(w))
            }
            assertEquals("L$level: otomatik hibe tetiklenirdi (devam)", 0, autoGrantShortfall(w))
        }
        val sticker = maxBoosterCoinSpend(level) + continuePrice(level)
        return UseAll(sticker, levelReward(level, 1), spent, n, possible, w, continued)
    }

    @Test
    fun useEverythingAtEachLevelVersusFirstClearReward() {
        println("==== (A2) HER SEYI KULLAN: bolum basina toplam opsiyonel harcama vs ilk temizleme odulu ====")
        println("sticker = maxBoosterCoinSpend(L) + continuePrice(L) (etiket toplami, cuzdan yetmese de).")
        println("net = R(L,1) - sticker: hak kullanan bir oyuncu tamir/devam yuzunden en fazla 1 yildiz alir, yani en kotu gelir R(L,1).")
        println("seq = sirali gercek karar zincirinden fiilen odenen; afterWin = seq sonrasi 1* zafer bakiyesi; nextLock = D(L+1).")
        for (profile in listOf(Profile.HOARD, Profile.GREEDY)) {
            for (stars in 1..3) {
                val w = walk(profile, stars)
                println()
                println("---- $profile ${stars}* ----")
                println("  L      W    sticker     R1     net(sticker)  seqSpent n/of  afterWin nextLock  netSeq(R1-seq)")
                var negNet = 0
                var negSeq = 0
                for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
                    val start = w.battle[level - 1]
                    val u = useEverything(level, start, replay = false)
                    val afterWin = applyLevelClear(u.endWallet, resolveLevelClear(u.endWallet, level, 1, maxLives))
                    val nextLock = if (level < EconomyConfig.CAMPAIGN_LEVELS) lockCost(level + 1) else 0
                    // SERT DEGISMEZLER: hak kullanimi siradaki kilidi odemeyi ASLA bozmaz.
                    assertEquals("$profile ${stars}* L$level: zafer sonrasi otomatik hibe", 0, autoGrantShortfall(afterWin))
                    assertTrue(
                        "$profile ${stars}* L$level: zafer sonrasi bakiye ${afterWin.coins} < siradaki kilit $nextLock",
                        afterWin.coins >= nextLock,
                    )
                    val net = u.r1 - u.sticker
                    val netSeq = u.r1 - u.spent
                    if (net < 0) negNet++
                    if (netSeq < 0) negSeq++
                    println(
                        String.format(
                            Locale.ROOT, "%3d %8d %8d %6d %12d %10d %2d/%2d %10d %8d %14d",
                            level, start.coins, u.sticker, u.r1, net, u.spent, u.count, u.possible,
                            afterWin.coins, nextLock, netSeq,
                        )
                    )
                }
                println("  -> net(sticker) < 0 olan bolum: $negNet/55 ; netSeq < 0 olan bolum: $negSeq/55")
            }
        }
    }

    @Test
    fun replayAtTheFrontierNeverDipsBelowTheNextLock() {
        println("==== (A3) FRONTIER TEKRAR: siradaki kilit ACILMAMISKEN temizlenmis bolumu tekrar oynayip her seyi kullan ====")
        println("Bu durumda tekrar geliri 0 ve rezerv = TAM D(L+1). Bakiye hicbir adimda rezervin altina inmemeli.")
        for (profile in Profile.entries) {
            for (stars in intArrayOf(1, 3)) {
                val w = walk(profile, stars)
                var worstCushion = Int.MAX_VALUE
                var worstLevel = -1
                var continueBlocked = 0
                val blockedLevels = mutableListOf<Int>()
                var frontierLevels = 0
                for (level in 1 until EconomyConfig.CAMPAIGN_LEVELS) {
                    val start = w.afterClear[level - 1]
                    if ((level + 1) in start.unlockedLevels && level + 1 < EconomyConfig.FIRST_PAID_LEVEL) continue
                    frontierLevels++
                    val u = useEverything(level, start, replay = true)
                    val cushion = u.endWallet.coins - reserveFor(u.endWallet)
                    if (cushion < worstCushion) { worstCushion = cushion; worstLevel = level }
                    if (!u.continued) { continueBlocked++; blockedLevels += level }
                    assertEquals("$profile ${stars}* replay L$level otomatik hibe", 0, autoGrantShortfall(u.endWallet))
                    // Siradaki kilit hala odenebilir:
                    if ((level + 1) !in u.endWallet.unlockedLevels) {
                        assertTrue(
                            "$profile ${stars}* replay L$level: bakiye ${u.endWallet.coins} < D(${level + 1})",
                            u.endWallet.coins >= lockCost(level + 1),
                        )
                    }
                }
                println(
                    "  $profile ${stars}*: frontier bolum sayisi=$frontierLevels ; devam alinamayan=$continueBlocked${if (continueBlocked in 1..6) " $blockedLevels" else ""} ; " +
                        "en dar minder(bakiye-rezerv, hepsi harcandiktan sonra)=$worstCushion @L$worstLevel"
                )
            }
        }
    }

    // =================================================================================
    // (B) Gercek kodla yenilgi/tekrar dizileri (CampaignProgressImpl)
    // =================================================================================

    private class FixedClock(private val day: Long = 20_500L) : ClockProvider {
        override fun sample(): ClockSample = ClockSample(
            epochDay = day,
            wallClockMs = day * EconomyConfig.MS_PER_DAY,
            elapsedRealtimeMs = 1_000L,
            bootId = 1L,
        )
    }

    private class Rig(seed: PlayerWallet) {
        val events = mutableListOf<String>()
        val save = SaveManager(InMemoryKeyValueStore()).also { it.saveWallet(seed) }
        val progress = CampaignProgressImpl(save, FixedClock()) { e, _ -> events += e }
        fun wallet(): PlayerWallet = save.loadWallet()
        val autoGrants: Int get() = events.count { it == "auto_grant_fired" }
    }

    @Test
    fun defeatAndRetrySequencesWithTheRealProgressClass() {
        println("==== (B) YENILGI / TEKRAR DIZILERI (CampaignProgressImpl, gercek kod) ====")
        println("A = yenilgi -> devam SATIN AL -> zafer (devam yildiz-notr: 1*)")
        println("B1 = yenilgi -> BEDAVA tekrar -> 1* zafer ; B3 = yenilgi -> bedava tekrar -> 3* kusursuz zafer")
        println("C = yenilgi -> devam -> ikinci yenilgi (AlreadyUsed, bedava tekrar) -> tekrarda YENIDEN devam -> zafer")
        println("net = final - baslangic; hepsi 'hoarder' savas cuzdanindan.")
        val levels = intArrayOf(1, 2, 3, 4, 7, 11, 22, 34, 45, 55)
        for ((profile, stars) in listOf(Profile.HOARD to 1, Profile.HOARD to 3, Profile.GREEDY to 1)) {
            val w = walk(profile, stars)
            println()
            println("---- $profile ${stars}* ----")
            println("  L  start  cPrice | A: paid  end   net | B1 net | B3 net(R3+80) | C: 2nd-continue  paid  net | D: kisa-10 sonuc   | R1")
            for (level in levels) {
                val seed = w.battle[level - 1]
                val price = continuePrice(level)
                val r1 = levelReward(level, 1)

                // ---- A ----
                val a = Rig(seed)
                a.progress.beginBattle(level)
                val aDecision = a.progress.continueDecision(level)
                val aRes = a.progress.continueAfterDefeat(level) { it }
                var aPaid = 0
                val aEnd: Int
                if (aRes is ContinueResult.Continued) {
                    aPaid = aRes.price
                    assertEquals("A L$level tek sefer borc", seed.coins - price, a.wallet().coins)
                    assertEquals(price, aRes.price)
                    assertEquals(EconomyConfig.CONTINUE_LIVES, aRes.lives)
                    val clear = a.progress.onLevelCleared(level, livesLeft = EconomyConfig.CONTINUE_LIVES, maxLives = maxLives)
                    assertEquals("A L$level devam yildiz-notr", 1, clear.stars)
                    assertEquals("A L$level odul = R(L,1)", r1, clear.total)
                    aEnd = a.wallet().coins
                    assertEquals("A L$level net = R1 - fiyat", seed.coins + r1 - price, aEnd)
                } else {
                    assertTrue("A L$level: reddedildi ama aciklama yok: $aRes", aRes is ContinueResult.Declined)
                    assertEquals("A L$level reddedilen devam cuzdana dokunmaz", seed.coins, a.wallet().coins)
                    a.progress.beginBattle(level) // bedava tekrar
                    a.progress.onLevelCleared(level, livesLeft = 5, maxLives = maxLives)
                    aEnd = a.wallet().coins
                }
                assertEquals("A L$level: sessiz otomatik hibe", 0, a.autoGrants)
                assertEquals("A L$level: sessiz hibe (lifetimeEarned)", seed.lifetimeEarned + r1, a.wallet().lifetimeEarned)
                assertEquals(0, autoGrantShortfall(a.wallet()))

                // ---- B1 / B3 ----
                val b1 = Rig(seed)
                b1.progress.beginBattle(level)
                b1.progress.beginBattle(level) // bedava TEKRAR DENE
                b1.progress.onLevelCleared(level, livesLeft = 5, maxLives = maxLives)
                val b1Net = b1.wallet().coins - seed.coins
                assertEquals("B1 L$level", r1, b1Net)

                val b3 = Rig(seed)
                b3.progress.beginBattle(level)
                b3.progress.beginBattle(level)
                b3.progress.onLevelCleared(level, livesLeft = maxLives, maxLives = maxLives)
                val b3Net = b3.wallet().coins - seed.coins
                assertEquals("B3 L$level = R(L,3)+80", levelReward(level, 3) + EconomyConfig.PERFECT_DEFENSE_BONUS, b3Net)
                // Devam bedava tekrari coinde ASLA gecemez: en iyi durumda R1 - fiyat < R1 <= B1.
                if (aPaid > 0) {
                    assertTrue("devam bedava tekrari gecti", (aEnd - seed.coins) < b1Net)
                    assertEquals("devam farki tam fiyat kadar", b1Net - price, aEnd - seed.coins)
                } else {
                    assertEquals("devam alinamadiysa yol bedava tekrardir", b1Net, aEnd - seed.coins)
                }
                assertTrue("B3 >= B1", b3Net >= b1Net)

                // ---- C ----
                val c = Rig(seed)
                c.progress.beginBattle(level)
                val c1 = c.progress.continueAfterDefeat(level) { it }
                var cText = "-"
                var cNet = 0
                if (c1 is ContinueResult.Continued) {
                    val afterFirst = c.wallet().coins
                    // ikinci yenilgi: ayni savasta hak bitti; cift dokunus da hicbir sey degistirmez
                    val c2 = c.progress.continueAfterDefeat(level) { it }
                    assertTrue("C L$level ikinci devam AlreadyUsed olmali: $c2",
                        c2 is ContinueResult.Declined && c2.decision is ContinueDecision.AlreadyUsed)
                    assertEquals(afterFirst, c.wallet().coins)
                    // motor reddederse (DEFEAT degil): hicbir sey degismez
                    // bedava TEKRAR DENE -> yeni savas -> hak SIFIRLANIR
                    c.progress.beginBattle(level)
                    val c3 = c.progress.continueAfterDefeat(level) { it }
                    if (c3 is ContinueResult.Continued) {
                        c.progress.onLevelCleared(level, livesLeft = EconomyConfig.CONTINUE_LIVES, maxLives = maxLives)
                        cNet = c.wallet().coins - seed.coins
                        assertEquals("C L$level net = R1 - 2 x fiyat", r1 - 2 * price, cNet)
                        cText = "Continued  2x$price"
                    } else {
                        c.progress.beginBattle(level)
                        c.progress.onLevelCleared(level, livesLeft = 5, maxLives = maxLives)
                        cNet = c.wallet().coins - seed.coins
                        cText = "2nd declined: ${(c3 as ContinueResult.Declined).decision::class.simpleName}"
                    }
                }
                assertEquals("C L$level: sessiz otomatik hibe", 0, c.autoGrants)
                assertEquals(0, autoGrantShortfall(c.wallet()))

                // ---- D: fiyatin 10 eksigi ----
                val dSeed = seed.copy(coins = (price - 10).coerceAtLeast(0))
                val d = Rig(dSeed)
                d.progress.beginBattle(level)
                val dRes = d.progress.continueAfterDefeat(level) { it }
                assertTrue("D L$level: yetmeyen devam reddedilmeli: $dRes", dRes is ContinueResult.Declined)
                assertTrue(
                    (dRes as ContinueResult.Declined).decision is ContinueDecision.InsufficientCoins ||
                        dRes.decision is ContinueDecision.ReserveLocked,
                )
                assertEquals("D L$level: reddedilen devam cuzdana dokunmaz", dSeed.coins, d.wallet().coins)
                // cift dokunus
                d.progress.continueAfterDefeat(level) { it }
                assertEquals(dSeed.coins, d.wallet().coins)
                // motor reddi: bakiye yeterli ama motor DEFEAT'te degil -> hicbir sey degismez
                val e = Rig(seed)
                e.progress.beginBattle(level)
                val eRes = e.progress.continueAfterDefeat(level) { 0 }
                if (aDecision is ContinueDecision.Allowed) {
                    assertTrue("E L$level: motor reddi EngineRefused olmali", eRes is ContinueResult.EngineRefused)
                }
                assertEquals("E L$level: motor reddi cuzdana dokunmaz", seed.coins, e.wallet().coins)

                println(
                    String.format(
                        Locale.ROOT, "%3d %6d %6d | %5d %6d %6d | %6d | %6d %12s | %-30s %6d | %-24s | %5d",
                        level, seed.coins, price,
                        aPaid, aEnd, aEnd - seed.coins,
                        b1Net, b3Net, "",
                        cText, cNet,
                        if (dRes.decision is ContinueDecision.InsufficientCoins) "Insufficient"
                        else dRes.decision::class.simpleName,
                        r1,
                    )
                )
            }
        }
    }

    @Test
    fun reserveGuardsTheContinueOnAReplayWithTheNextLockStillClosed() {
        println("==== (B2) REZERV KORUMASI: siradaki kilit acilmamis, bolum tekrar; devam yalnizca (bakiye - fiyat >= D(L+1)) ise ====")
        for (level in intArrayOf(6, 7, 11, 22, 34, 45, 54)) {
            val w = walk(Profile.HOARD, 1).afterClear[level - 1]
            val next = lockCost(level + 1)
            val price = continuePrice(level)
            check((level + 1) !in w.unlockedLevels)

            // (a) bir on eksik: rezerv kilitler
            val short = Rig(w.copy(coins = next + price - 10))
            short.progress.beginBattle(level)
            val dShort = short.progress.continueDecision(level)
            val rShort = short.progress.continueAfterDefeat(level) { it }
            assertTrue("L$level: rezerv kilitlemeli: $dShort", dShort is ContinueDecision.ReserveLocked)
            assertTrue(rShort is ContinueResult.Declined)
            assertEquals(next + price - 10, short.wallet().coins)

            // (b) tam esik: izin verir, bakiye tam D(L+1)'e iner, invariant tetiklenmez
            val exact = Rig(w.copy(coins = next + price))
            exact.progress.beginBattle(level)
            val rExact = exact.progress.continueAfterDefeat(level) { it }
            assertTrue("L$level: esikte devam izinli: $rExact", rExact is ContinueResult.Continued)
            assertEquals(next, exact.wallet().coins)
            assertEquals(0, exact.progress.enforceUnlockInvariant())
            assertEquals(0, exact.autoGrants)
            println(
                "  L$level: D(L+1)=$next continue=$price -> (a) ${next + price - 10} coin: ${dShort::class.simpleName} ; " +
                    "(b) ${next + price} coin: Continued, kalan $next ; auto_grant=0"
            )
        }
    }

    // =================================================================================
    // (B3) Fiyat rasyonelligi: can basina fiyat, yildiz arbitraji, erken acilis alternatifleri
    // =================================================================================

    @Test
    fun priceRationalityPerLifeStarArbitrageAndEarlyUnlockAlternatives() {
        println("==== (B3) FIYAT RASYONELLIGI ====")

        // 1) Can basina fiyat
        println("---- CAN BASINA FIYAT: devam (5 can) vs tamir1 (4 can) vs tamir2 (7 can) ----")
        for (level in intArrayOf(7, 11, 22, 34, 55)) {
            val c = continuePrice(level).toDouble() / EconomyConfig.CONTINUE_LIVES
            val p1 = boosterPrice(BoosterType.BASE_REPAIR, level, 1).toDouble() / EconomyConfig.BASE_REPAIR_LIVES_FIRST_USE
            val p2 = boosterPrice(BoosterType.BASE_REPAIR, level, 2).toDouble() / EconomyConfig.BASE_REPAIR_LIVES_SECOND_USE
            println(
                String.format(
                    Locale.ROOT, "  L%-2d continue %.1f/can  repair1 %.1f/can  repair2 %.1f/can  (repair1/continue = %.2fx)",
                    level, c, p1, p2, p1 / c,
                )
            )
            assertTrue("repair1 can basina devamdan ucuz olmamali (dominasyon)", p1 >= c)
        }

        // 2) Yildiz arbitraji: yildiz-notr OLMAYAN haklar (acil tedarik, hava) icin 1* -> 3* sicrama degeri
        println("---- YILDIZ ARBITRAJI: 1*->3* sicramasi (R3-R1) [+80 kusursuz] vs YILDIZ-NOTR OLMAYAN hak fiyati ----")
        println("  (varsayim: hak 1*'i 3*'a CEVIRIRSE; bu KOSULLU bir ust sinirdir, cevirme olasiligi OLCULMEDI = tahmin)")
        var emerBelowJump = 0
        var emerBelowJumpPerfect = 0
        var airBelowJump = 0
        var airBelowJumpPerfect = 0
        var emerExposure = 0
        var emerExposurePerfect = 0
        var airExposurePerfect = 0
        val rows = mutableListOf<String>()
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            val jump = levelReward(level, 3) - levelReward(level, 1)
            val jumpP = jump + EconomyConfig.PERFECT_DEFENSE_BONUS
            val e = boosterPrice(BoosterType.EMERGENCY_SUPPLY, level)
            val a = boosterPrice(BoosterType.AIR_SUPPORT, level)
            if (boosterUnlocked(BoosterType.EMERGENCY_SUPPLY, level)) {
                if (e < jump) { emerBelowJump++; emerExposure += jump - e }
                if (e < jumpP) { emerBelowJumpPerfect++; emerExposurePerfect += jumpP - e }
            }
            if (boosterUnlocked(BoosterType.AIR_SUPPORT, level)) {
                if (a < jump) airBelowJump++
                if (a < jumpP) { airBelowJumpPerfect++; airExposurePerfect += jumpP - a }
            }
            if (boosterUnlocked(BoosterType.BASE_REPAIR, level)) {
                // Mevcut kural (BoosterEconomyTest:1442-1455): tamir fiyati >= en buyuk yildiz sicramasi.
                assertTrue("L$level tamir1 >= R3-R1", boosterPrice(BoosterType.BASE_REPAIR, level, 1) >= jump)
            }
            if (level in intArrayOf(2, 4, 8, 11, 22, 34, 55)) {
                rows += String.format(
                    Locale.ROOT, "  L%-2d R3-R1=%4d (+80=%4d)  EMER=%4d %s   AIR=%4d %s",
                    level, jump, jumpP, e, if (e < jump) "< jump" else if (e < jumpP) "< jump+80" else ">= jump+80",
                    a, if (a < jump) "< jump" else if (a < jumpP) "< jump+80" else ">= jump+80",
                )
            }
        }
        rows.forEach { println(it) }
        println(
            "  EMER (L2..55, 54 bolum): fiyat < R3-R1 olan bolum=$emerBelowJump ; fiyat < R3-R1+80 olan bolum=$emerBelowJumpPerfect ; " +
                "kosullu ust sinir (her bolumde 1*->3* cevirse) = $emerExposure (kusursuzlu: $emerExposurePerfect)"
        )
        println(
            "  AIR  (L4..55, 52 bolum): fiyat < R3-R1 olan bolum=$airBelowJump ; fiyat < R3-R1+80 olan bolum=$airBelowJumpPerfect ; " +
                "kusursuzlu kosullu ust sinir = $airExposurePerfect"
        )
        val allJump = (1..55).sumOf { levelReward(it, 3) - levelReward(it, 1) }
        println("  Referans: tum kampanyada 1*->3* toplam sicrama R3-R1 = $allJump (zaten STAR_IMPROVEMENT tavaninin parcasi; bolum basina TEK SEFER, sonsuz cift degil)")

        // 3) Acil Tedarik erken acilis alternatifleri (hoarder 1*, greedy 1*)
        println("---- ACIL TEDARIK ACILIS BOLUMU ALTERNATIFLERI (1* bandi; fiyat = 2 x Tedarik) ----")
        val hoard = walk(Profile.HOARD, 1)
        val greedy = walk(Profile.GREEDY, 1)
        println("  L  fiyat  R1  fiyat/R1   HOARD: W  fiyat/sp | GREEDY: W  fiyat/sp")
        for (level in 2..8) {
            val price = boosterPrice(BoosterType.EMERGENCY_SUPPLY, level)
            val hw = hoard.battle[level - 1]
            val gw = greedy.battle[level - 1]
            println(
                String.format(
                    Locale.ROOT, "  %d %5d %4d %7s   %8d %6s |  %8d %6s",
                    level, price, levelReward(level, 1), f2(price.toDouble() / levelReward(level, 1)),
                    hw.coins, f2(price.toDouble() / spendableBalance(hw).coerceAtLeast(1)),
                    gw.coins, f2(price.toDouble() / spendableBalance(gw).coerceAtLeast(1)),
                )
            )
        }
        // L2'de acil tedarik alirsan ilk KALICI rank ne zaman gelir?
        fun firstRankLevel(buyEmergencyAtL2: Boolean): Int {
            var w = PlayerWallet()
            val u = MetaUpgrades()
            for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
                if (level !in w.unlockedLevels) w = applyUnlock(w, level)
                if (level == 2 && buyEmergencyAtL2) {
                    val d = boosterAllowed(BoosterState.startBattle(2), BoosterType.EMERGENCY_SUPPLY, w)
                    if (d is BoosterDecision.Allowed) w = payForBooster(w, d)
                }
                w = applyLevelClear(w, resolveLevelClear(w, level, livesForStars(1), maxLives))
                if (UpgradeLine.entries.any { purchaseAllowed(w, u, it) is PurchaseDecision.Allowed }) return level
            }
            return 0
        }
        val without = firstRankLevel(false)
        val with = firstRankLevel(true)
        println("  1* oyuncu ilk KALICI rank'i (en ucuz 200) L$without sonrasi alabilir; L2'de acil tedarik alirsa L$with sonrasi.")
        assertTrue("acil tedarik ilk rank'i geciktirmemeli veya yalniz geciktirmeli", with >= without)

        // 4) Devam L1'de: cuzdan 0 (butun bantlar)
        println("---- DEVAM L1: her yildiz bandinda savas cuzdani = ${hoard.battle[0].coins} ; fiyat = ${continuePrice(1)} -> ilk yenilgide daima InsufficientCoins ----")
        for (s in 1..3) assertEquals(0, walk(Profile.HOARD, s).battle[0].coins)
    }

    /**
     * Acil Tedarik yildiz-notr DEGIL (Tedarik gercek savunma). Simulatorle OLCUM: meta 0, guclendiricisiz
     * mukemmel oyun (iyimser), her bolumde tum CAREFUL_STYLES icinde en iyi sonuc; ikinci kosuda acil
     * tedarik miktari BASTA eklenir (`startingSupplyOverride`; savas ortasi enjeksiyondan ayni veya daha
     * IYIDIR, yani cevirme sayisi UST SINIRDIR). Simulator insan hatasini modellemez.
     */
    @Test
    fun emergencySupplyStarFlipProxyFromTheCampaignSimulator() {
        println("==== (B4) ACIL TEDARIK YILDIZ CEVIRME PROXY'SI (CampaignSimulator, meta 0) ====")
        println("  L  taban(sizinti/yildiz)  +acil(sizinti/yildiz)  acil-tedarik  R3-R1(+80)  fiyat")
        var flips13 = 0
        var flips12 = 0
        var flips23 = 0
        var gainWhenFlipped = 0
        var costWhenFlipped = 0
        var changed = 0
        for (level in 2..EconomyConfig.CAMPAIGN_LEVELS) {
            val model = com.miniappfactory.frontlinedefender.balance.CampaignSimulator.LevelModel(level)
            val base = model.spec.startingSupply + model.modifiers.startingSupplyBonus
            val amount = emergencySupplyAmount(level)
            fun best(override: Int?): Pair<Int, Int> { // (min sizinti, max yildiz); gecemezse (99, 0)
                var leaks = 99
                var stars = 0
                for (s in com.miniappfactory.frontlinedefender.balance.CampaignSimulator.CAREFUL_STYLES) {
                    val o = com.miniappfactory.frontlinedefender.balance.CampaignSimulator.play(model, s, override)
                    if (o.cleared) {
                        if (o.leaked < leaks) leaks = o.leaked
                        if (o.stars > stars) stars = o.stars
                    }
                }
                return leaks to stars
            }
            val (l0, s0) = best(null)
            val (l1, s1) = best(base + amount)
            if (s1 > s0) {
                changed++
                if (s0 <= 1 && s1 == 3) flips13++
                if (s0 <= 1 && s1 == 2) flips12++
                if (s0 == 2 && s1 == 3) flips23++
                val g = levelReward(level, s1) - levelReward(level, maxOf(s0, 1)) +
                    (if (s1 == 3 && l1 == 0) EconomyConfig.PERFECT_DEFENSE_BONUS else 0)
                gainWhenFlipped += g
                costWhenFlipped += boosterPrice(BoosterType.EMERGENCY_SUPPLY, level)
                println(
                    String.format(
                        Locale.ROOT, "  %-3d %2d/%d*                %2d/%d*               +%-4d  %5d(+80)  %5d   <-- yildiz artti",
                        level, l0, s0, l1, s1, amount, levelReward(level, 3) - levelReward(level, 1),
                        boosterPrice(BoosterType.EMERGENCY_SUPPLY, level),
                    )
                )
            } else if (level in intArrayOf(2, 5, 8, 11, 14, 22, 29, 34, 45, 55)) {
                println(
                    String.format(
                        Locale.ROOT, "  %-3d %2d/%d*                %2d/%d*               +%-4d  %5d(+80)  %5d",
                        level, l0, s0, l1, s1, amount, levelReward(level, 3) - levelReward(level, 1),
                        boosterPrice(BoosterType.EMERGENCY_SUPPLY, level),
                    )
                )
            }
        }
        println(
            "  OZET: acil tedarik basta eklenince yildizi ARTAN bolum = $changed / 54 ; 1*->3*=$flips13, 1*->2*=$flips12, 2*->3*=$flips23 ; " +
                "bu bolumlerde ek odul toplami=$gainWhenFlipped, odenen fiyat toplami=$costWhenFlipped (ilk temizlemede TEK SEFER; iyimser ust sinir)"
        )
    }

    // =================================================================================
    // (C) Sifir satin alma yasayabilirligi + kayip ucretsiz gelir
    // =================================================================================

    @Test
    fun zeroPurchaseViabilityAndLostFreeIncome() {
        println("==== (C) SIFIR SATIN ALMA YASAYABILIRLIGI ====")

        // 1) Haklari MAKSIMUM kullanan oyuncu (her bolumde her hak zinciri + 1* zafer, agac YOK): kampanya yine 55/55 mi?
        var wallet = PlayerWallet()
        var totalSpent = 0
        var minMarginToNext = Int.MAX_VALUE
        var minMarginLevel = -1
        var usesTotal = 0
        val trajectory = mutableListOf<String>()
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            if (level !in wallet.unlockedLevels) {
                assertTrue("HAK-MAKSIMIZE L$level acilamadi — SOFT-LOCK", canUnlock(wallet, level))
                wallet = applyUnlock(wallet, level)
            }
            val u = useEverything(level, wallet, replay = false)
            totalSpent += u.spent
            usesTotal += u.count
            wallet = applyLevelClear(u.endWallet, resolveLevelClear(u.endWallet, level, 1, maxLives))
            assertEquals("HAK-MAKSIMIZE L$level otomatik hibe", 0, autoGrantShortfall(wallet))
            if (level < EconomyConfig.CAMPAIGN_LEVELS && lockCost(level + 1) > 0) {
                val margin = wallet.coins - lockCost(level + 1)
                assertTrue("HAK-MAKSIMIZE L$level: bakiye siradaki kilidin altinda", margin >= 0)
                if (margin < minMarginToNext) { minMarginToNext = margin; minMarginLevel = level }
            }
            if (level in intArrayOf(1, 2, 4, 7, 11, 22, 34, 45, 55)) {
                trajectory += "L$level: harcanan=${u.spent} (n=${u.count}/${u.possible}) sonrasi bakiye=${wallet.coins}"
            }
        }
        assertEquals(55, wallet.clearedLevels.size)
        println("HAK-MAKSIMIZE (agac yok, 1* zafer, her bolumde emergency+air+air+repair+repair+continue zinciri, yetdigince):")
        trajectory.forEach { println("   $it") }
        println(
            "   toplam gelir=${wallet.lifetimeEarned} ; hak harcamasi=$totalSpent ; kilit=${wallet.lifetimeSpent - totalSpent} ; " +
                "kampanya sonu bakiye=${wallet.coins} ; hak kullanim sayisi=$usesTotal ; " +
                "zafer sonrasi en dar (bakiye - siradaki kilit)=$minMarginToNext @L$minMarginLevel ; 55/55 ACILDI"
        )

        // 2) Kampanya toplami hak kapasitesi (TEKRARLAYAN; oneTimeSinkTotal'a EKLENMEZ)
        val stickerAll = (1..55).sumOf { maxBoosterCoinSpend(it) + continuePrice(it) }
        val contAll = (1..55).sumOf { continuePrice(it) }
        val r1All = (1..55).sumOf { levelReward(it, 1) }
        println(
            "TEKRARLAYAN kapasite (55 bolumun HER birinde her hak): sticker toplami=$stickerAll ; yalniz devam toplami=$contAll ; " +
                "toplam R(L,1)=$r1All ; devam/R1=${f2(contAll.toDouble() / r1All)}"
        )

        // 3) Kayip ucretsiz gelir (reklamlar kalkti)
        val weeklyMissionIncome = 7 * EconomyConfig.DAILY_MAX_TOTAL + EconomyConfig.WEEKLY_BUDGET
        // SILINEN degerler (commit a094d0e EconomyConfig.kt:601-625): R1_REWARD_FILLED=150, R1_VIEWS_PER_DAY=3.
        val removedR1PerDay = 150 * 3
        val removedR1PerWeek = 7 * removedR1PerDay
        println("SERBEST GELIR: gorevler kalan = ${EconomyConfig.DAILY_MAX_TOTAL}/gun x 7 + ${EconomyConfig.WEEKLY_BUDGET} = $weeklyMissionIncome /hafta " +
            "(${String.format(Locale.ROOT, "%.1f", weeklyMissionIncome / 7.0)}/gun ust sinir)")
        println("   silinen R1 = 3 x 150 = $removedR1PerDay/gun = $removedR1PerWeek/hafta ; onceki toplam = ${weeklyMissionIncome + removedR1PerWeek}/hafta ; dusus = " +
            String.format(Locale.ROOT, "%.1f%%", 100.0 * removedR1PerWeek / (weeklyMissionIncome + removedR1PerWeek)))
        println("   silinen R3 (cift odul) tavani: 1 yildiz kampanya x2 -> +${r1All} coin omur boyu (her temizlemede reklam izleyerek)")
        println("   NOT: gorev geliri ust sinirdir (tum gunluk+haftalik tamamlanirsa); gercek tamamlama orani OLCULMEDI (tahmin).")

        // 4) Kampanya sonrasi: hak basina "kac gunluk tam gorev"
        println("KAMPANYA SONRASI (tekrar geliri 0, yalniz gorev ${String.format(Locale.ROOT, "%.0f", weeklyMissionIncome / 7.0)}/gun):")
        for (level in intArrayOf(4, 7, 11, 22, 34, 55)) {
            val perDay = weeklyMissionIncome / 7.0
            val parts = listOf(
                "CONT" to continuePrice(level),
                "EMER" to (if (boosterUnlocked(BoosterType.EMERGENCY_SUPPLY, level)) boosterPrice(BoosterType.EMERGENCY_SUPPLY, level) else 0),
                "AIR" to (if (boosterUnlocked(BoosterType.AIR_SUPPORT, level)) boosterPrice(BoosterType.AIR_SUPPORT, level) else 0),
                "REP1" to (if (boosterUnlocked(BoosterType.BASE_REPAIR, level)) boosterPrice(BoosterType.BASE_REPAIR, level, 1) else 0),
            ).filter { it.second > 0 }
            println(
                "   L$level: " + parts.joinToString("  ") { (n, p) ->
                    "$n $p = ${String.format(Locale.ROOT, "%.2f", p / perDay)} gun"
                }
            )
        }
    }

    // =================================================================================
    // Kaynak / sink defteri (tek seferlik ve tekrarlayan AYRI)
    // =================================================================================

    @Test
    fun sourcesAndSinksLedgerOneTimeAndRecurringSeparately() {
        println("==== KAYNAK / SINK DEFTERI ====")
        val locks = EconomyConfig.TOTAL_LOCK_COST
        val tree = EconomyConfig.TREE_TOTAL_COST
        val prestige = EconomyConfig.PRESTIGE_TOTAL_COST
        val oneTime = StoreCatalog.oneTimeSinkTotal
        assertEquals(locks + tree + prestige, oneTime)
        assertEquals(81_505, oneTime)
        assertEquals(23_105, (7..55).sumOf { lockCost(it) })
        println("TEK SEFERLIK sinkler: kilit $locks + agac $tree + prestij $prestige (${EconomyConfig.PRESTIGE_MAX} nisan) = $oneTime")
        println("   en buyuk paket 7000 / $oneTime = ${String.format(Locale.ROOT, "%.2f%%", 100.0 * 7000 / oneTime)} (aritmetik; StoreCatalog bu snapshot'ta hala ESKI paketleri tasiyor)")
        for (stars in 1..3) {
            val w = walk(Profile.HOARD, stars)
            println(
                "   ${stars}* HOARD: gelir(lifetimeEarned)=${w.finalWallet.lifetimeEarned}, kilit harcamasi=${w.finalWallet.lifetimeSpent}, " +
                    "kampanya sonu bakiye=${w.finalWallet.coins}, agac sonrasi olu fazla=${w.finalWallet.coins - tree}, " +
                    "prestij sonrasi=${w.finalWallet.coins - tree - prestige}"
            )
        }
        val gr = walk(Profile.GREEDY, 1)
        println(
            "   1* GREEDY: agac harcamasi=${gr.finalUpgrades.spentCoins()} ; kalan bakiye=${gr.finalWallet.coins} ; " +
                "agac tamam=${gr.finalUpgrades.isMaxed()}"
        )
        // TEKRARLAYAN, ayri
        println("TEKRARLAYAN (paydaya EKLENMEZ): elit bilet toplami (55 bolum, bir kez) = ${(1..55).sumOf { eliteTicketPrice(it) }}")
        println("   devam toplami (55 bolum, bir kez) = ${(1..55).sumOf { continuePrice(it) }} (elit biletle ayni formul)")
        println("   guclendirici tavani toplami (her bolumde tum haklar) = ${(1..55).sumOf { maxBoosterCoinSpend(it) }}")
        println("   ACHIEVEMENT_TOTAL=${EconomyConfig.ACHIEVEMENT_TOTAL} kodda YOK (hayalet gelir; hicbir yerden odenmiyor)")
        println("   R(L,1) >= 2 x D(L+1) en dusuk oran: " + (1 until 55).minOf { levelReward(it, 1).toDouble() / lockCost(it + 1).coerceAtLeast(1) }
            .let { String.format(Locale.ROOT, "%.3f", it) })
        // Sert degismez
        for (level in 1 until 55) {
            if (lockCost(level + 1) > 0) assertTrue(levelReward(level, 1) >= 2 * lockCost(level + 1))
        }
        assertFalse(replayReward(3, 3) > 0)
    }
}
