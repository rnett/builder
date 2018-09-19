package models.blueprints

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.kizitonwose.time.Interval
import com.kizitonwose.time.milliseconds
import com.kizitonwose.time.minutes
import com.rnett.core.Cache
import com.rnett.core.ManualCache
import com.rnett.ligraph.eve.contracts.UpdateLog
import com.rnett.ligraph.eve.contracts.updatelogtable
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object ContractUpdateChecker {
    private val _events = mutableListOf<() -> Unit>()
    fun addHandler(function: () -> Unit) {
        _events.add(function)
    }

    fun removeHandler(function: () -> Unit) {
        _events.remove(function)
    }

    private var lastUpdate: Interval<*> = 0.milliseconds

    private var checker: Job? = null

    init {
        init()
    }

    fun init() {
        if (checker == null || checker!!.isActive) {

            checker = launch {
                while (true) {
                    val latestUpdate = transaction {
                        UpdateLog.wrapRows(updatelogtable.select { updatelogtable.contracts greater 0 and (updatelogtable.items greater 0) }
                                .orderBy(updatelogtable.time, false).limit(1)).firstOrNull()?.time
                                ?: 0.milliseconds
                    }

                    if (latestUpdate > lastUpdate && lastUpdate.longValue != 0.toLong()) {
                        lastUpdate = latestUpdate
                        println("Contract Update Caught!")
                        _events.forEach { it() }
                    }

                    delay(1.minutes.inMilliseconds.longValue)
                }
            }
        }
    }

}

object appraisalcache : IntIdTable(columnName = "keyhash") {
    val argsHash = integer("argshash")
    val runs = integer("runs")
    val appraisalJson = text("appraisal")
    val size = integer("size")
    val keyhash = integer("keyhash").primaryKey()
}

enum class CacheSize(val BPs: Int) {
    XS(20), Small(50), Medium(100), Large(200), XL(500), XXL(750);

    fun cutToSize(appraisal: BPAppraisal): List<BPCost> {
        val list = mutableSetOf<BPCost>()
        var runs = 0

        for (bpc in appraisal.matchesByPricePerRun) {
            if (!list.contains(bpc)) {
                runs += bpc.usedBPs.count()
                list.add(bpc)

                if (runs > BPs / 2.0)
                    break
            }
        }

        runs = 0
        for (bpc in appraisal.matchesByPrice) {
            if (!list.contains(bpc)) {
                runs += bpc.usedBPs.count()
                list.add(bpc)

                if (runs > BPs / 2.0)
                    break
            }
        }

        return list.toList()
    }

    fun nextLargest() = values()[ordinal + 1]
}

class CachedAppraisal(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CachedAppraisal>(appraisalcache) {

        fun keyHash(args: AppraisalArgs, size: CacheSize) = args.hashCode() * 13 + size.ordinal

        private val listCache = ManualCache<AppraisalArgs, MutableMap<CacheSize, Pair<List<BPCost>, Int>>>(sizeLimit = 100)


        init {
            ContractUpdateChecker.addHandler { listCache.clear(); appraisalJsonCache.clear() }
        }

        fun forArgs(args: AppraisalArgs, runs: Int): List<BPCost>? = transaction {

            listCache[args]?.apply {
                this.values.find { it.second >= runs }?.apply {
                    println("${args.bpType.typeName} x $runs Local Cached")
                    return@transaction this.first
                }
            }

            val appraisal = CachedAppraisal.wrapRows(
                    appraisalcache.select { appraisalcache.argsHash eq args.hashCode() and (appraisalcache.runs greaterEq runs) }
                            .orderBy(appraisalcache.size)).firstOrNull()

            if (appraisal != null) {

                val map = listCache.getOrDefault(args, mutableMapOf())
                map[appraisal.size] = Pair(appraisal.appraisal, appraisal.runs)
                listCache[args] = map

                println("${args.bpType.typeName} x $runs DB Cached")
            } else
                println("${args.bpType.typeName} x $runs Not Cached")

            return@transaction appraisal?.appraisal
        }

        fun addAppraisal(appraisal: BPAppraisal): Unit = transaction {

            val map = listCache.getOrDefault(appraisal.appraisalArgs, mutableMapOf())

            CacheSize.values().forEach {
                val list = it.cutToSize(appraisal)
                val hash = appraisal.appraisalArgs.hashCode()
                val runs = list.sumBy { it.totalRuns }
                val json = Gson().toJson(list)
                map[it] = Pair(list, runs)


                transaction { CachedAppraisal.findById(keyHash(appraisal.appraisalArgs, it)) }?.apply {
                    appraisalJson = json
                    _runs = runs
                    _size = it
                    argsHash = hash
                    return@forEach
                }

                new(keyHash(appraisal.appraisalArgs, it)) {
                    appraisalJson = json
                    _runs = runs
                    _size = it
                    argsHash = hash
                }
            }
            listCache[appraisal.appraisalArgs] = map
        }

        private val appraisalJsonCache = Cache<String, List<BPCost>>(sizeLimit = 100) { Gson().fromJson(it) }
    }

    private var argsHash by appraisalcache.argsHash

    private var _runs by appraisalcache.runs
    val runs get() = _runs

    private var appraisalJson by appraisalcache.appraisalJson
    val appraisal by lazy { appraisalJsonCache[appraisalJson] }

    private var _rawSize by appraisalcache.size
    private var _size
        get() = CacheSize.values()[_rawSize]
        set(s) {
            _rawSize = s.ordinal
        }
    val size get() = _size

    val keyhash by appraisalcache.keyhash
}
