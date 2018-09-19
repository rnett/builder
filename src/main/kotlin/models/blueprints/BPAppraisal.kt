package models.blueprints

import com.google.gson.annotations.JsonAdapter
import com.rnett.core.Cache
import com.rnett.eve.ligraph.sde.fromName
import com.rnett.eve.ligraph.sde.invtype
import com.rnett.eve.ligraph.sde.invtypes
import com.rnett.ligraph.eve.contracts.Contract
import com.rnett.ligraph.eve.contracts.ContractItem
import com.rnett.ligraph.eve.contracts.ContractType
import com.rnett.ligraph.eve.contracts.blueprints.BPType
import com.rnett.ligraph.eve.contracts.blueprints.Blueprint
import com.rnett.ligraph.eve.contracts.blueprints.BlueprintAdapter
import com.rnett.ligraph.eve.contracts.contractitems
import main.connect
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.min

// runs = -1 => don't care.  runs = 0 => original
// me/te = -1 => don't care


fun main(args: Array<String>) {
    connect()
    //ContractUpdater.updateContractsForRegion()

    val thany = transaction { invtypes.fromName("Capital Ship Maintenance Bay")!!.invtype_industryactivityrecipes_productType.first().type }
    val tester = BPAppraisal.appraise(thany, BPFilter(BPType.BPO))
}


data class AppraisalArgs(val bpType: invtype, val filter: BPFilter, val bpcOnly: Boolean) {
    override fun hashCode(): Int {
        var result = bpType.hashCode()
        result = 31 * result + filter.hashCode()
        result = 31 * result + bpcOnly.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppraisalArgs) return false

        if (bpType != other.bpType) return false
        if (filter != other.filter) return false
        if (bpcOnly != other.bpcOnly) return false

        return true
    }
}

data class BestBPCArgs(val bpType: invtype, val runs: Int, val filter: BPFilter)

class BPAppraisal private constructor(val bpType: invtype, val filter: BPFilter, val bpcOnly: Boolean = false) {
    private constructor(args: AppraisalArgs) : this(args.bpType, args.filter, args.bpcOnly)

    val appraisalArgs get() = AppraisalArgs(bpType, filter, bpcOnly)

    fun matches(bp: Blueprint): Boolean {
        return (!bpcOnly || bp.type == BPType.BPC) &&
                bp.bpType == bpType &&
                filter.matches(bp)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BPAppraisal) return false

        if (bpType != other.bpType) return false
        if (filter != other.filter) return false
        if (bpcOnly != other.bpcOnly) return false
        if (matchesByPrice != other.matchesByPrice) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bpType.hashCode()
        result = 31 * result + filter.hashCode()
        result = 31 * result + bpcOnly.hashCode()
        result = 31 * result + matchesByPrice.hashCode()
        return result
    }

    companion object {

        fun getDBCachedAppraisal(args: AppraisalArgs, runs: Int): List<BPCost> {
            val cached = CachedAppraisal.forArgs(args, runs)

            if (cached == null) {
                val appraisal = BPAppraisal(args)
                try {
                    CachedAppraisal.addAppraisal(appraisal)
                    return CachedAppraisal.forArgs(args, runs) ?: appraisal.matchesByPricePerRun
                } catch (e: Exception) {
                    return appraisal.matchesByPrice
                }
            } else {
                return cached
            }
        }

        init {
            ContractUpdateChecker.addHandler { appraisalCache.clear(); bestBPCsCache.clear() }
        }


        private val appraisalCache = Cache<AppraisalArgs, BPAppraisal>(sizeLimit = 100) {
            val appraisal = BPAppraisal(it)
            try {
                CachedAppraisal.addAppraisal(appraisal)
            } catch (e: Exception) {
            }
            appraisal
        }

        fun appraise(bpType: invtype, filter: BPFilter, bpcOnly: Boolean = false) =
                appraisalCache[AppraisalArgs(bpType, filter, bpcOnly)]

        fun getBestBPCsList(bpType: invtype, filter: BPFilter, runs: Int, bpcOnly: Boolean) =
                getDBCachedAppraisal(AppraisalArgs(bpType, filter, bpcOnly), runs)

        private val bestBPCsCache = Cache<BestBPCArgs, List<BPCRunCounter>?>(sizeLimit = 100) {

            val runs = it.runs
            val appraisal = getBestBPCsList(it.bpType, it.filter, it.runs, true).sortedBy { it.price / min(runs, it.totalRuns) * 100 + it.averageME }

            var filled = 0
            var i = 0
            val order = mutableListOf<BPCRunCounter>()

            while (filled < it.runs) {

                if (i > appraisal.lastIndex)
                    return@Cache null

                if (appraisal[i].totalRuns >= it.runs) { // its best to just use this contract
                    filled = 0
                    order.clear()
                }

                filled += appraisal[i].totalRuns

                val list = appraisal[i].asBPCRunCounters

                var j = list.lastIndex
                var extra = filled - it.runs
                while (extra > 0) {

                    val offed = min(extra, list[j].runsUsed)

                    list[j].runsLeft = offed
                    extra -= offed
                    j--
                }

                order.addAll(list)
                i++
            }

            return@Cache order

        }

        private fun getBestBPCs(bpType: invtype, runs: Int, filter: BPFilter): List<BPCRunCounter>? = bestBPCsCache[BestBPCArgs(bpType, runs, filter)]

        fun getBestBPCsForProduct(productType: invtype, runs: Int, filter: BPFilter) =
                getBestBPCs(productType.blueprint()!!, runs, filter)
    }

    val matchesByPrice: List<BPCost> = transaction {
        ContractItem.wrapRows(contractitems.select { contractitems.typeId eq bpType.typeID })
                .asSequence().filter { matches(it.toBlueprint()) }.map { it.contract }.filter { it.type == ContractType.ItemExchange }.distinct().map { BPCost(it, bpType, filter, bpcOnly) }.toList().sortedBy { it.price * 100 + it.averageME }
    }

    val matchesByPricePerRun = matchesByPrice.sortedBy { it.pricePerRun * 100 + it.averageME }

    val bestContract get() = matchesByPrice.first().contract
    val bestPrice get() = matchesByPrice.first().price
    val bestPricePerRun get() = matchesByPrice.first().pricePerRun

    val totalRuns get() = matchesByPrice.sumBy { it.totalRuns }


    //bottom 5 percent of blueprint packs, by runs
    /*
    val bottom5Runs: Int
    val bottom5Cost: Double
    val bottom5ME: Double
    val bottom5TE: Double
    init{
        bottom5Runs = ceil(0.05 * matchesByPrice.sumBy { it.totalRuns }).toInt()

        var b5Cost = 0.0
        var b5ME = 0.0
        var b5TE = 0.0

        var count = bottom5Runs
        var i = 0
        while(count > 0){
            val use = min(count, matchesByPrice[i].totalRuns)
            count -= use

            b5Cost += matchesByPrice[i].pricePerRun * use
            b5ME += matchesByPrice[i].averageME * use
            b5TE += matchesByPrice[i].averageTE * use

            i++
        }

        bottom5Cost = b5Cost / bottom5Runs
        bottom5ME = b5ME / bottom5Runs
        bottom5TE = b5TE / bottom5Runs
    }
    */

}

class BPCost(val contract: Contract, val bpType: invtype, filter: BPFilter, bpcOnly: Boolean = false) {
    val usedBPs = contract.blueprints.asSequence().filter { filter.matches(it) && it.bpType == bpType && (!bpcOnly || it.type == BPType.BPC) }
            .map { BPContracted(contract, it) }.toList()

    val price get() = contract.price
    val totalRuns get() = usedBPs.sumBy { it.blueprint.runs }
    val pricePerRun get() = if (totalRuns != 0) price / totalRuns else Double.POSITIVE_INFINITY

    val averageME: Double get() = usedBPs.sumBy { it.blueprint.me } / usedBPs.count().toDouble()
    val averageTE: Double get() = usedBPs.sumBy { it.blueprint.te } / usedBPs.count().toDouble()

    val asBPCRunCounters get() = usedBPs.map { BPCRunCounter(it, 0) }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BPCost) return false

        if (contract != other.contract) return false
        if (bpType != other.bpType) return false
        if (usedBPs != other.usedBPs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contract.hashCode()
        result = 31 * result + bpType.hashCode()
        result = 31 * result + usedBPs.hashCode()
        return result
    }
}

data class BPContracted(val contract: Contract, @JsonAdapter(BlueprintAdapter::class) val blueprint: Blueprint)

fun invtype.blueprint() = transaction { this@blueprint.invtype_industryactivityrecipes_productType.firstOrNull()?.type }


