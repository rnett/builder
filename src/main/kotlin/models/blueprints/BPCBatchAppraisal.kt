package models.blueprints

import com.rnett.core.Cache
import com.rnett.eve.ligraph.sde.*
import com.rnett.ligraph.eve.contracts.Contract
import com.rnett.ligraph.eve.contracts.blueprints.BPC
import com.rnett.ligraph.eve.contracts.blueprints.Blueprint
import kotlinx.coroutines.experimental.*
import main.initalize
import models.Facility
import models.OreOptimizer
import models.blueprints.BPAppraisal.Companion.appraise
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

fun main(args: Array<String>) {
    initalize()

    val type = transaction { invtypes.fromName("Chimera")!! }

    val optimized = BPCPackOptimizer(type, Facility.basic, BPFilter(minME = 5, minTE = 8), BPFilter())
}


//TODO use better ME bps to replace pack ones if better (low priority)
fun Blueprint.getMats(facility: Facility, customME: Double = me.toDouble()): ItemList = productType.materials()
        .mapValues { ceil(it.value * facility.getMEBonus(this.productType).add(customME).multiplier) }.toItemList()

fun BPCAggregate.getMats(facility: Facility): ItemList = productType.materials()
        .mapValues { ceil(it.value.toDouble() * facility.getMEBonus(this.productType).add(this.averageME).multiplier).toInt() }.toItemList() * this.usedRuns

class BPCPackOptimizer(val product: invtype, val facility: Facility, val mainFilter: BPFilter, val componentFilter: BPFilter) {
    // optimize for each product bp grouped by me
    // need to do nested recursively somehow

    companion object {

        init {
            ContractUpdateChecker.addHandler { bpcOptimizerCache.clear() }
        }

        val bpcOptimizerCache = Cache<Pair<Pair<invtype, Facility>, Pair<BPFilter, BPFilter>>, BPCPackOptimizer>(sizeLimit = 50) {
            BPCPackOptimizer(it.first.first, it.first.second, it.second.first, it.second.second)
        }

        fun optimize(product: invtype, facility: Facility, mainFilter: BPFilter, componentFilter: BPFilter) =
                bpcOptimizerCache[Pair(Pair(product, facility), Pair(mainFilter, componentFilter))]

        fun getDistinctBPContractsProduct(product: invtype, mainFilter: BPFilter): Set<Contract> =
                getDistinctBPContracts(product.blueprint()!!, mainFilter)

        fun getDistinctBPContracts(bpType: invtype, mainFilter: BPFilter): Set<Contract> {
            val appraisal = appraise(bpType, mainFilter, true)

            val singles = appraisal.matchesByPrice
                    .asSequence()
                    .filter { it.contract.bpcs.count() == 1 }
                    .groupBy { it.contract.bpcs.first().me }
                    .mapValues { it.value.minBy { it.price }!! }

            val packs = appraisal.matchesByPrice
                    .asSequence()
                    .filter { it.contract.bpcs.count() > 1 }
                    .groupBy { it.averageME.roundToInt() }
                    .mapValues { it.value.minBy { it.price }!! }

            return singles.map { it.value.contract }.toSet() + packs.map { it.value.contract }
        }

        suspend fun initCaches(product: invtype, componentFilter: BPFilter) {
            product.materials().flatMap { listOf(it, *(it.key.materials() * it.value).entries.toTypedArray()) }
                    .flatMap { listOf(it, *(it.key.materials() * it.value).entries.toTypedArray()) }
                    .asSequence().filter { !it.key.isBaseMat }
                    .map { Pair(it.key.blueprint(), it.value) }.filter { it.first != null }.map { Pair(it.first!!, it.second) }
                    .toItemList()
                    .map { launch { BPAppraisal.getDBCachedAppraisal(AppraisalArgs(it.key, componentFilter, true), it.value.toInt()) } }
                    .toList().joinAll()
        }
        //TODO return url isn't quite working
    }

    val packs: List<BPCPackAppraisal>

    init {
        compressedOres.map { it.type }.getPrices()

        runBlocking { initCaches(product, componentFilter) }

        println("Caching Done")

        packs = runBlocking {
            getDistinctBPContractsProduct(product, mainFilter).map {
                async { BPCPackAppraisal(it, product, facility, mainFilter, componentFilter) }
            }.awaitAll().asSequence().filter { !it.incomplete }.sortedBy { it.totalPrice }.toList()
        }

    }

    val bestPack by lazy { packs.firstOrNull() }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BPCPackOptimizer) return false

        if (product != other.product) return false
        if (facility != other.facility) return false
        if (mainFilter != other.mainFilter) return false
        if (componentFilter != other.componentFilter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = product.hashCode()
        result = 31 * result + facility.hashCode()
        result = 31 * result + mainFilter.hashCode()
        return result
    }


}

class BPCRunCounter(val bpContracted: BPContracted, var runsLeft: Int = bpContracted.blueprint.runs) {
    var runsUsed
        get() = bpc.runs - runsLeft
        set(value) {
            runsLeft = bpc.runs - value
        }

    val totalRuns = bpc.runs

    val bpc get() = bpContracted.blueprint as BPC

    override fun toString(): String = "${bpc.bpType.typeName} (${bpc.me}, ${bpc.te}) x ($runsUsed Used, $runsLeft Left) [BPC]"
}

class BPCPackAppraisal(val contract: Contract, val product: invtype, val facility: Facility,
                       val mainFilter: BPFilter = BPFilter(), val componentFilter: BPFilter = BPFilter(),
                       val refineRate: Double = 92.39) {
    val packBPCs = contract.bpcs.map { BPContracted(contract, it) }
    val packPrice: Double = contract.price
    val productBPC = packBPCs.find { it.blueprint.productType == product }!!

    val neededRawBaseMats: ItemList
    val neededOptimizedBaseMats: ItemList
    val missingComponents: ItemList

    val usedPackBPs: Set<BPCRunCounter>
    val leftoverPackBPs: Set<BPCRunCounter>

    val fillerBPCs: List<BPCRunCounter>
    val fillerBPCContracts: Set<Contract>

    private var _incomplete = false
    val incomplete get() = _incomplete

    val bpPrice: Double
    val matsPrice: Price

    val allContracts: Set<Contract>
    val allUsedBPCs: List<BPCRunCounter>

    val aggregateBPCsByType: Map<invtype, BPCAggregate>

    init {
        val canMake = packBPCs.groupBy { it.blueprint.productType }.mapValues {
            it.value.asSequence().map { BPCRunCounter(it) }.sortedByDescending { it.bpc.me }.toMutableList()
        }
        val fullyUsed = mutableListOf<BPCRunCounter>()

        val matsQueue = LinkedList<invtype>()

        val neededMats = MutableItemList()

        neededMats.add(product, 1.toLong())
        matsQueue.add(product)

        while (matsQueue.count() > 0) {
            val type = matsQueue.pop()
            val amount = neededMats[type]

            val bpcCounter = canMake[type]?.firstOrNull() ?: continue

            var used = 0
            if (bpcCounter.runsLeft > amount) { // makes all the mats
                neededMats.remove(type)
                bpcCounter.runsLeft -= amount.toInt()

                used = amount.toInt()
            } else { // used all of BPC
                canMake[type]?.removeAt(0)
                neededMats[type] -= bpcCounter.runsLeft.toLong()

                used = bpcCounter.runsLeft
                bpcCounter.runsLeft = 0
                fullyUsed.add(bpcCounter)
                matsQueue.push(type)
            }
            val mats = bpcCounter.bpc.getMats(facility) * used

            neededMats.removeNegatives()

            mats.forEach {
                neededMats.add(it.key, it.value)
                if (!it.key.isBaseMat)
                    matsQueue.push(it.key)
            }
        }

        usedPackBPs = (canMake.values.flatMap { it } + fullyUsed).toSet()
        leftoverPackBPs = (canMake.values.flatMap { it }.filter { it.runsLeft > 0 }).toSet()
        missingComponents = neededMats.filter { !it.key.isBaseMat }.toItemList()

        //TODO really should do a batch match for the leftovers
        val fBPCs = mutableListOf<BPCRunCounter>()
        val boughtContracts = mutableSetOf<Contract>()

        //TODO need to do a batch fetch to try to pick up packs.
        missingComponents.forEach { (product, amount) ->
            //TODO deal with a nested missing bpContracted.  e.g. nomad missing fenrir

            var stillNeeded = amount

            val boughtBPCs = boughtContracts.flatMap { it.bpcs.map { bpc -> BPContracted(it, bpc) } }.asSequence().toSet()
                    .asSequence().filter { it.blueprint.productType == product && componentFilter.matches(it.blueprint) }.sortedByDescending { it.blueprint.me }.toList()
            stillNeeded -= boughtBPCs.sumBy { it.blueprint.runs }

            if (stillNeeded > 0) {

                fBPCs.addAll(boughtBPCs.map { BPCRunCounter(it, 0) })

                val bpcs = BPAppraisal.getBestBPCsForProduct(product, stillNeeded.toInt(), componentFilter)

                if (bpcs == null) {
                    _incomplete = true
                    return@forEach
                }

                val averageME = bpcs.asSequence().map { it.bpc.me.toDouble() * it.bpc.runs }.sum() / bpcs.sumBy { it.bpc.runs }

                fBPCs.addAll(bpcs)
                boughtContracts.addAll(bpcs.map { it.bpContracted.contract })
            } else {
                var averageME: Double = 0.0
                var meCount: Int = 0
                var needed = amount.toInt()
                var i = 0

                while (needed > 0) {

                    val bpc = boughtBPCs[i]
                    averageME += bpc.blueprint.me

                    fBPCs.add(BPCRunCounter(bpc, bpc.blueprint.runs - min(needed, bpc.blueprint.runs)))

                    meCount += min(needed, bpc.blueprint.runs)
                    needed -= bpc.blueprint.runs
                    i++
                }

                averageME /= meCount
            }
        }

        fillerBPCs = fBPCs
        fillerBPCContracts = fillerBPCs.asSequence().map { it.bpContracted.contract }.toSet()

        allContracts = fillerBPCContracts + contract
        allUsedBPCs = fillerBPCs + usedPackBPs

        aggregateBPCsByType = allUsedBPCs.groupBy { it.bpc.productType }.mapValues { BPCAggregate(it.value) }

        neededRawBaseMats = aggregateBPCsByType.values.map { it.getMats(facility) }.reduce { l1, l2 -> l1 + l2 }.filter { it.key.isBaseMat }.toItemList()

        bpPrice = packPrice + fillerBPCContracts.asSequence().map { it.price }.sum()

        val optimized = OreOptimizer.optimize(neededRawBaseMats.filter { it.key.isMineral }.toItemList(), refineRate)

        neededOptimizedBaseMats = neededRawBaseMats.filter { !it.key.isMineral }.toItemList() +
                optimized.ores

        matsPrice = neededOptimizedBaseMats.appraise().totalPrice


    }

    val usedBPCsByContract = allUsedBPCs.groupBy { it.bpContracted.contract }
    val totalPrice: Double = bpPrice + matsPrice.sell


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BPCPackAppraisal) return false

        if (contract != other.contract) return false
        if (product != other.product) return false
        if (facility != other.facility) return false
        if (mainFilter != other.mainFilter) return false
        if (componentFilter != other.componentFilter) return false
        if (refineRate != other.refineRate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contract.hashCode()
        result = 31 * result + product.hashCode()
        result = 31 * result + facility.hashCode()
        result = 31 * result + mainFilter.hashCode()
        result = 31 * result + componentFilter.hashCode()
        result = 31 * result + refineRate.hashCode()
        return result
    }


}

val materialsCache = Cache<invtype, ItemList>(sizeLimit = 200) {
    transaction {
        it.invtype_industryactivityrecipes_productType.asSequence().filter { it.activityID == 1 }.map { Pair(it.materialType, it.materialQuantity) }.toItemList()
    }
}

fun invtype.materials() = materialsCache[this]

val isBaseMatCache = Cache<invtype, Boolean> { it.materials().count() == 0 }
val invtype.isBaseMat get() = isBaseMatCache[this]

