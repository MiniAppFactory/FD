package com.miniappfactory.frontlinedefender.game.billing

import com.miniappfactory.frontlinedefender.game.data.KeyValueStore
import com.miniappfactory.frontlinedefender.game.data.PurchaseGuard
import com.miniappfactory.frontlinedefender.game.data.StoreBatch
import com.miniappfactory.frontlinedefender.game.data.StoreOp

/**
 * IKI HARITALI SAHTE DEPO — Android `SharedPreferences`in iki tuzagini birebir uretir.
 *
 *  * [memory]: okumalarin gordugu harita (`SharedPreferencesImpl.mMap`).
 *  * [disk]: SUREC OLUNCE HAYATTA KALAN harita. [restart] yalnizca bundan yeni bir depo kurar
 *    (bellek de, [PurchaseGuard] da SIFIRDAN: yeni surec).
 *
 * Tuzak 1 — **basarisiz commit bellegi yine de gunceller** ([CommitMode.FAIL_KEEP_MEMORY]):
 * Android `commit()` once bellek haritasini gunceller, sonra diske yazar; disk yazimi
 * basarisiz olursa `false` doner AMA bellek yeni degerleri gosterir.
 *
 * Tuzak 2 — **ayni degerlerle ikinci commit, yeniden yazmadan `true` doner**: hicbir anahtar
 * degismediyse bu sahte `disk`i GUNCELLEMEZ. (Gercek Android'in bu noktadaki davranisi surume
 * gore degisebilir; sozlesme en kotu durumu varsayar — bkz. `PurchaseGrantLedger`.)
 *
 * `put*`/`remove` (uretimde `apply()`): bellegi gunceller ve TUM haritayi diske yazar — yani
 * bir basarisiz commit'in bellekte biraktigi kalinti, bir sonraki siradan yazimla diske INER
 * (bayat cuzdan geri yazimi senaryosu).
 */
class FailableKeyValueStore(initialDisk: Map<String, Any> = emptyMap()) : KeyValueStore {

    enum class CommitMode {
        /** Normal: bellek + disk, `true`. */
        OK,

        /** Android tuzagi: bellek GUNCELLENIR, disk DEGISMEZ, `false`. */
        FAIL_KEEP_MEMORY,

        /** Bellek ve disk DOKUNULMAZ, `false` (commit hic uygulanmadi). */
        REJECT_ENTIRELY,

        /** `commit()` istisna atar. */
        THROW,
    }

    val memory = LinkedHashMap<String, Any>(initialDisk)
    val disk = LinkedHashMap<String, Any>(initialDisk)

    /** Her `commit()` bunun basindan bir mod tuketir; bos ise [CommitMode.OK]. */
    val script = ArrayDeque<CommitMode>()

    var commitCalls = 0
        private set

    /** Gelen her `commit()` toplu yazimi (modundan bagimsiz) — atomiklik denetimi icin. */
    val batches = mutableListOf<StoreBatch>()

    /** Bu depoyla ayni DISKTEN yeni bir surec. Bellek ve guard sifirdan. */
    fun restart(): FailableKeyValueStore = FailableKeyValueStore(LinkedHashMap(disk))

    private fun flushAll() {
        disk.clear()
        disk.putAll(memory)
    }

    override fun commit(batch: StoreBatch): Boolean {
        commitCalls++
        batches += batch
        val mode = script.removeFirstOrNull() ?: CommitMode.OK
        when (mode) {
            CommitMode.THROW -> throw IllegalStateException("simulated commit failure")
            CommitMode.REJECT_ENTIRELY -> return false
            CommitMode.OK, CommitMode.FAIL_KEEP_MEMORY -> Unit
        }
        var changed = false
        for (op in batch.operations) {
            when (op) {
                is StoreOp.PutInt -> changed = put(op.key, op.value) || changed
                is StoreOp.PutLong -> changed = put(op.key, op.value) || changed
                is StoreOp.PutString -> changed = put(op.key, op.value) || changed
                is StoreOp.PutBoolean -> changed = put(op.key, op.value) || changed
                is StoreOp.Remove -> if (memory.remove(op.key) != null) changed = true
            }
        }
        if (mode == CommitMode.FAIL_KEEP_MEMORY) return false
        // Tuzak 2: degisiklik yoksa "basarili" ama yeniden YAZMA.
        if (!changed) return true
        flushAll()
        return true
    }

    private fun put(key: String, value: Any): Boolean {
        val old = memory.put(key, value)
        return old != value
    }

    override val purchaseGuard: PurchaseGuard = PurchaseGuard()

    // ---- okumalar: bellekten (Android'de de boyle) ----
    override fun getInt(key: String, default: Int): Int = memory[key] as? Int ?: default
    override fun getLong(key: String, default: Long): Long = memory[key] as? Long ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean = memory[key] as? Boolean ?: default
    override fun getString(key: String, default: String): String = memory[key] as? String ?: default
    override fun hasKey(key: String): Boolean = memory.containsKey(key)
    override fun keys(): Set<String> = memory.keys.toSet()

    // ---- apply() tarzi yazimlar: bellek + TUM harita diske ----
    override fun putInt(key: String, value: Int) { memory[key] = value; flushAll() }
    override fun putLong(key: String, value: Long) { memory[key] = value; flushAll() }
    override fun putBoolean(key: String, value: Boolean) { memory[key] = value; flushAll() }
    override fun putString(key: String, value: String) { memory[key] = value; flushAll() }
    override fun remove(key: String) { memory.remove(key); flushAll() }
}
