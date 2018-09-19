package models.blueprints

import com.github.salomonbrys.kotson.get
import com.google.gson.JsonParser
import com.rnett.eve.ligraph.sde.imageURL
import com.rnett.eve.ligraph.sde.invtype
import com.rnett.kframe.dom.*
import com.rnett.kframe.dom.classes.*
import com.rnett.kframe.element.*
import com.rnett.kframe.materalize.*
import com.rnett.ligraph.eve.contracts.Contract
import com.rnett.ligraph.eve.contracts.ContractType
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import main.*
import models.tdStyle
import org.joda.time.DateTime
import org.joda.time.Duration
import pages.isCharLogedIn
import pages.logedInCharacter
import java.net.URLEncoder

class BPCAggregate(private var _bpcs: List<BPCRunCounter>, val single: Boolean = false) {
    constructor(vararg bpcs: BPCRunCounter, single: Boolean = false) : this(bpcs.toList(), single)
    constructor(vararg bpcs: BPContracted, single: Boolean = false) : this(bpcs.map { BPCRunCounter(it, 0) }.toList(), single)

    val bpcs get() = _bpcs.toList()

    val usedRuns: Int
    val extraRuns: Int
    val totalRuns get() = usedRuns + extraRuns

    val averageME: Double
    val averageTE: Double

    val productType: invtype
    val bpType: invtype

    init {

        bpType = bpcs.first().bpc.bpType
        productType = bpcs.first().bpc.productType

        _bpcs = _bpcs.asSequence().filter { it.bpc.bpType == bpType }.sortedByDescending { it.bpc.me }.toList()

        usedRuns = _bpcs.sumBy { it.runsUsed }
        extraRuns = _bpcs.sumBy { it.runsLeft }

        if (usedRuns != 0 && !single) {
            averageME = _bpcs.sumByDouble { it.bpc.me.toDouble() * it.runsUsed } / usedRuns
            averageTE = _bpcs.sumByDouble { it.bpc.te.toDouble() * it.runsUsed } / usedRuns
        } else {
            averageME = _bpcs.sumByDouble { it.bpc.me.toDouble() * it.bpc.runs } / totalRuns
            averageTE = _bpcs.sumByDouble { it.bpc.te.toDouble() * it.bpc.runs } / totalRuns
        }
    }
}

object BPDisplays {

    /*
        Hi-light used bpcs
        have contracts expand when clicked
        show runs as used/total

        might want to re-calc mats based on listBPCs() base mats.  shouldn't need to, but need to check

        Do with tabs?
     */

    fun aggregateTree(bpcAppraisal: BPCPackAppraisal): AnyDisplayElementBuilder = {
        table().striped()() {
            thead {
                trListHeader(elements = listOf("", "Runs Used (Extra)", "Average ME", "Average TE"))
            }
            tbody {

                -BPDisplayTree(bpcAppraisal.aggregateBPCsByType[bpcAppraisal.productBPC.blueprint.productType]!!, 1, {
                    val list = it.productType.materials().mapNotNull { bpcAppraisal.aggregateBPCsByType[it.key] }
                    if (list.isEmpty() && !it.single)
                        it.bpcs.asSequence().map { BPCAggregate(it, single = true) }.sortedByDescending { it.usedRuns }.toList()
                    else
                        list

                }, {
                    td(attrs = *arrayOf("style" to tdStyle)) {
                        klass = "valign-wrapper"
                        responsiveImage(it.value.bpType.imageURL(32), attrs = *arrayOf("style" to
                                Style("margin-right" to 7.px, "margin-left" to (it.level * 50).px)))
                        +(it.value.productType.typeName + "\t")
                    }

                    tdString("${it.value.usedRuns}" + if (it.value.extraRuns > 0) " (${it.value.extraRuns})" else "", attrs = *arrayOf("style" to tdStyle))
                    tdString(if (!it.value.single) it.value.averageME.format0s() else it.value.averageME.format(), attrs = *arrayOf("style" to tdStyle))
                    tdString(if (!it.value.single) it.value.averageTE.format0s() else it.value.averageTE.format(), attrs = *arrayOf("style" to tdStyle))
                })
            }
        }
    }

    //TODO as different tables.  e.g. have one for the contract, then another for the bpcs, with different column sizes/titles
    fun byContract(bpcAppraisal: BPCPackAppraisal): AnyDisplayElementBuilder = {
        //val modals = bpcAppraisal.allContracts.map { Pair(it.contractId, contractModal(it)) }.toMap()

        //script{+"\$('.modal').modal();"}

        table().striped()() {
            thead {
                tr {
                    th {
                        attributes["nowrap"] = true
                        style.whiteSpaceRaw = "nowrap"
                        style.paddingRight = 25.px
                        style.paddingLeft = 10.px

                        p { +"Name" }.style.floatRaw = "left"
                        p {
                            +"Price"
                            style.paddingLeft = 1.rem
                            style.floatRaw = "right"
                        }

                        style.minWidth = 12.rem

                    }

                    th { +"BPCs"; attributes["nowrap"] = true }
                    th { +"Station"; style.width = 90.percent; attributes["nowrap"] = true }
                    th { +"Open"; attributes["nowrap"] = true }
                }
                tr {
                    listOf("", "Runs Used(Extra)", "Average ME", "Average TE").forEach {
                        th { +it;attributes["nowrap"] = true }
                    }
                }
            }
            tbody {
                bpcAppraisal.usedBPCsByContract.forEach {
                    val tree = BPDisplayTree<Any>(it, 0, {
                        when (it) {
                            is Map.Entry<*, *> -> it.value as List<out Any>
                            else -> emptyList()
                        }
                    }, {
                        when (it.value) {

                            is Map.Entry<*, *> -> {
                                val contract = it.value.key as Contract
                                td(attrs = *arrayOf("style" to tdStyle)) {
                                    style.paddingRight = 40.px
                                    style.paddingLeft = 10.px

                                    style.minWidth = 12.rem

                                    p { +if (contract.title.isNotBlank()) contract.title else "[No Title]" }.style.floatRaw = "left"

                                    p { +contract.price.format0ks() }.style.floatRaw = "right"

                                }
                                tdString("${contract.bpcs.count()}", attrs = *arrayOf("style" to tdStyle))
                                tdString(getStationName(contract.endLocationId), attrs = *arrayOf("style" to tdStyle))
                                val contractUrl = "https://esi.evetech.net/latest/ui/openwindow/contract/?contract_id=${contract.contractId}&datasource=tranquility"
                                td {
                                    attributes["contractId"] = contract.contractId.toString()

                                    btn("disabled").color("grey")() {
                                        +"View"
                                    }.onClick {
                                        //modals[contract.contractId]?.open()
                                        ""
                                    }

                                    btn().color("grey", -1)() {

                                        if (!isCharLogedIn)
                                            addClass("disabled")

                                        +"Open"
                                        onClick {
                                            try {
                                                launch {
                                                    HttpClient(Apache).post<String>(contractUrl) {
                                                        header("Authorization", "Bearer " + logedInCharacter.accessToken)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                            }
                                            ""
                                        }
                                    }
                                }
                            }

                            //TODO do I want to show all contracts?  I think so
                            is BPCRunCounter -> {
                                td(attrs = *arrayOf("style" to tdStyle)).valAlignThis()() {
                                    attributes["nowrap"] = true
                                    style.paddingRight = 45.px

                                    responsiveImage(it.value.bpc.bpType.imageURL(32), attrs = *arrayOf("style" to
                                            Style("margin-right" to 7.px, "margin-left" to (it.level * 50).px)))
                                    +(it.value.bpc.productType.typeName + "\t")
                                }

                                tdString("${it.value.runsUsed}" + if (it.value.runsLeft > 0) " (${it.value.runsLeft})" else "", attrs = *arrayOf("style" to tdStyle))
                                tdString(it.value.bpc.me.format(), attrs = *arrayOf("style" to tdStyle))
                                tdString(it.value.bpc.te.format(), attrs = *arrayOf("style" to tdStyle))
                            }

                            else -> {

                            }

                        }
                    })

                    -tree

                    tree.trElement?.displayed = true
                }
            }
        }
    }
}

class BPDisplayTree<T> private constructor(val parent: BPDisplayTree<T>?, val value: T, val displayLevel: Int = 1, val makeChildren: (T) -> List<T>, val makeTDs: TRElement.(BPDisplayTree<T>) -> Unit) : View<TableElement> {
    constructor(value: T, displayLevel: Int = 1, makeChildren: (T) -> List<T>, makeTDs: TRElement.(BPDisplayTree<T>) -> Unit) : this(null, value, displayLevel, makeChildren, makeTDs)


    val children = mutableListOf<BPDisplayTree<T>>()

    val level: Int = (parent?.level ?: -1) + 1

    var trElement: TRElement? = null

    init {
        parent?.children?.add(this)
        makeChildren(value).forEach {
            BPDisplayTree(this, it, displayLevel, makeChildren, makeTDs)
        }
    }

    override fun makeElements(): ElementBuilder<TableElement> = {
        this as TableElement

        this@BPDisplayTree.trElement = tr {

            displayed = this@BPDisplayTree.level <= displayLevel
            attributes["parentID"] = this@BPDisplayTree.parent?.trElement?.elementID ?: 0

            klass = "hoverable"

            makeTDs(this@BPDisplayTree)
            /*
            onClick {
                if (it.target(this.page)!!.tag != "input" && it.target(this.page)!!.tag != "a") {

                    this@BPDisplayTree.children.forEach {
                        if (it.trElement != null) {
                            it.trElement!!.displayed = !it.trElement!!.displayed

                            if (!it.trElement!!.displayed)
                                it.hide()
                        }
                    }
                }
                ""
            }*/

            onClickJS(
                    """
if(event.target.tagName != "INPUT"){
    var element = $(event.currentTarget);

    var subs = $("[parentID='${elementID}']")

    if(subs.first().css("display") != "none")
        subs.css("display", "none");
    else
        subs.css("display", "table-row");
}
                """
            )
        }

        this@BPDisplayTree.children.forEach {
            -it
        }
    }
}

fun DisplayElement<*>.getCharName(id: Int): A {
    val url = "https://evewho.com/api.php?type=character&id=$id"
    val data = runBlocking { HttpClient(Apache).get<String>(url) }
    val name = JsonParser().parse(data)["info"]["name"].asString
    val eveWho = "https://evewho.com/pilot/${URLEncoder.encode(name, "utf-8")}"

    return a(eveWho) {
        attributes["target"] = "_blank"
        +name
    }
}

fun DisplayElement<*>.getCorpName(id: Int): A {
    val url = "https://evewho.com/api.php?type=corporation&id=$id"
    val data = runBlocking { HttpClient(Apache).get<String>(url) }
    val name = JsonParser().parse(data)["info"]["name"].asString
    val eveWho = "https://evewho.com/corp/${URLEncoder.encode(name, "utf-8")}"

    return a(eveWho) {
        attributes["target"] = "_blank"
        +name
    }
}

//TODO load modals on demand instead of loading list (only for bpcBrowser?) (use Cache?)

@KFrameElementDSL
fun DisplayElement<*>.contractModal(contract: Contract): Modal = modal(true).color("grey", 1)() {
    style.boxSizingRaw = "border-box"

    content().section {
        row().col(12).centerAlignThis()() { b { +contract.type.displayName } }
        row().col(12).centerAlignThis()() { h(6) { +contract.title } }

        if (contract.type == ContractType.Courier) {
            row {
                col(6) { b().left()() { +"Start:" }; +getStationName(contract.startLocationId) }
                col(6) { b().left()() { +"End:" }; +getStationName(contract.endLocationId) }
            }.centerAlignThis()
            row {
                col(6) { b().left()() { +"Reward:" }; +contract.reward.format0ks() }
                col(6) { b().left()() { +"Collateral:" }; +contract.collateral.format0ks() }
            }.centerAlignThis()
        } else if (contract.type == ContractType.ItemExchange) {
            row {
                col(6) { b().left()() { +"Location:" }; +getStationName(contract.endLocationId) }
                col(6) { b().left()() { +"Price:" }; +contract.price.format0ks() }
            }.centerAlignThis()
        }

        row {
            col(6) { b().left()() { +"Issued by:" }; +if (contract.forCorporation) getCorpName(contract.issuerCorporationId) else getCharName(contract.issuerId) }
            if (contract.type == ContractType.Courier)
                col(6) { b().left()() { +"Days to Complete:" }; +contract.daysToComplete.format() }
        }.centerAlignThis()

        row {
            val expiresIn = Duration(contract.dateExpired, DateTime.now())

            col(6) { b().left()() { +"Issued:" }; +contract.dateIssued.formatUTC() }
            col(6) { b().left()() { +"Expires:" }; +"${contract.dateExpired.formatUTC()} (${expiresIn.standardDays} days ${expiresIn.standardHours} hours)" }
        }.centerAlignThis()

        row {
            col(6) { b().left()() { +"Items:" }; +contract.items.count().format() }
            col(6) { b().left()() { +"Volume:" }; +contract.volume.format0s() }
        }.centerAlignThis()

        row {
            col(12) {
                //TODO items table
                +"Item Table TODO"
            }
        }

    }
    val modal = this
    footer {

        val contractUrl = "https://esi.evetech.net/latest/ui/openwindow/contract/?contract_id=${contract.contractId}&datasource=tranquility"

        btn().color("grey", -1)() {
            if (!isCharLogedIn)
                classes.add("disabled")

            +"Open Ingame"
            onClick {
                try {
                    launch {
                        HttpClient(Apache).post<String>(contractUrl) {
                            header("Authorization", "Bearer " + logedInCharacter.accessToken)
                        }
                    }
                } catch (e: Exception) {
                }
                ""
            }
        }

        closeModalFlatBtn(modal) { +"Close" }
    }
}