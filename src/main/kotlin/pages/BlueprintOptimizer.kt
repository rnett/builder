package pages

import com.kizitonwose.time.seconds
import com.rnett.eve.ligraph.sde.fromName
import com.rnett.eve.ligraph.sde.invtype
import com.rnett.eve.ligraph.sde.invtypes
import com.rnett.kframe.dom.*
import com.rnett.kframe.dom.classes.dataText
import com.rnett.kframe.element.*
import com.rnett.kframe.materalize.*
import kotlinx.coroutines.experimental.launch
import main.format
import main.format0ks
import main.format0s
import models.Facility
import models.blueprints.BPCPackAppraisal
import models.blueprints.BPCPackOptimizer
import models.blueprints.BPDisplays
import models.blueprints.MutableBPFilter
import pages.ProductTypes.productTypes
import views.AmountTable
import views.NavBar

class BpOptimizerModel {

    var type: invtype? = null

    val facility: Facility = Facility.basic

    val mainFilter: MutableBPFilter = MutableBPFilter(minME = 5)
    val componentFilter: MutableBPFilter = MutableBPFilter(minME = 10, minTE = 10)

    var selctedPack: BPCPackAppraisal? = null

    var optimizer: BPCPackOptimizer? = null

    val displays = mutableMapOf<BPCPackAppraisal, Pair<AnyElement, AnyElement>>()

    fun optimize() {
        if (type != null) {
            println("Starting BPC Optimization")
            optimizer = BPCPackOptimizer.optimize(type!!, facility, mainFilter, componentFilter)

            selctedPack = optimizer!!.bestPack
            println("Done with BPC Optimization")
        }
    }

    internal fun urlString(value: String? = null): String {

        var name = type?.typeName ?: ""

        if (value != null && invtypes.fromName(value) != null)
            name = value

        return "/bpcoptimizer${if (name != "") "/type/$name" else ""}/mainfilter/$mainFilter/componentfilter/$componentFilter" +
                if (facility != Facility.basic) "/facility/" + facility.toString() else ""
    }

}

internal fun AnyElement.optimize(loader: AnyElement, model: BpOptimizerModel) {
    loader.style.displayRaw = "block"
    page.updateInterval = 1.seconds
    model.optimizer = null

    launch {
        model.optimize()

        loader.style.displayRaw = "none"
        page.updateInterval = page.defaultUpdateInterval
    }
}

//TODO Overly long load time (between optimiziation finish and page load).  Load pack list, then add another spinner for loading packs?
//TODO load first, the load others in the background?  Would have to have all of them client side
//TODO facility url not updating
//TODO updates from client (styles) isn't working
//TODO re-add modals
//TODO tab gets reset on first timer update

val bpOptimizerPage: DocumentBuilder = {

    page addUpdateJS "window.setTimeout(function(){ M.AutoInit();}, 200); "

    val model = BpOptimizerModel()
    if (urlParams.containsKey("type")) {
        val t = invtypes.fromName(urlParams["type"]!!)
        if (t != null)
            model.type = t
    }

    if (urlParams.containsKey("mainfilter")) {
        model.mainFilter.loadFromString(urlParams["mainfilter"]!!)
    }

    if (urlParams.containsKey("componentfilter")) {
        model.componentFilter.loadFromString(urlParams["componentfilter"]!!)
    }

    if (urlParams.containsKey("facility")) {
        model.facility.loadFromString(urlParams["facility"]!!)
    }

    head {


        +commonScripts


        stylesheet(
                ".indicator" to Style(
                        "background-color" to "#1565C0 !important".cssStyle
                ),
                ".tabs .tab a:focus, .tabs .tab a:focus.active" to Style(
                        "background-color" to "rgba(38,166,154,0.26)".cssStyle
                ),
                ".loader" to Style(
                        "border" to "16px solid #f3f3f3".cssStyle,
                        "border-top" to "16px solid #3498db".cssStyle,
                        "border-radius" to 50.percent,
                        "width" to 120.px,
                        "height" to 120.px,
                        "animation" to "spin 2s linear infinite".cssStyle
                )).invoke {
            +"""@keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
}"""
        }
        title {
            dataText(model::type) {
                if (model.type == null)
                    "BPC Optimizer"
                else
                    "BPC Optimizer: ${model.type?.typeName}"
            }
        }
    }

    lateinit var loader: AnyElement


    body {
        klass = "grey lighten-2"

        -NavBar()

        section {
            style.marginTop = 0.px
            style.paddingTop = 0.px
            container {
                style.width = 100.percent
                style.maxWidth = 100.percent

                row {
                    col(3) {
                        style.marginLeft = 0.px
                        style.marginTop = 3.rem
                        style.width = 25.rem

                        div {
                            style.displayRaw = "inline-block"
                            div {
                                style.width = 20.rem
                                style.floatRaw = "left"

                                selectFromAutocomplete(model::type, productTypes, {
                                    it?.typeName ?: ""
                                })
                            }

                            data(model::optimizer) {
                                flatBtn().color("transparent")() {
                                    style.floatRaw = "left"
                                    style.paddingRaw = "0"
                                    style.marginTop = 0.5.rem
                                    style.marginLeft = 12.px

                                    fontSizedIcon("forward", 30).style.colorRaw = "black"
                                }.onClick {
                                    optimize(loader, model)
                                    println("Optimize button clicked")
                                    ""
                                }
                            }

                        }

                        loader = container().centerAlignThis()() {
                            style.marginTop = 1.rem
                            circleSpinnerMulticolor(size = "big").active()
                        }

                        data(model::facility) {
                            script {
                                +"window.history.pushState(\"\", \"BPC Optimizer\", \"${model.urlString()}\");"
                            }
                        }

                        data(model::type, model::componentFilter, model::mainFilter) {

                            script {
                                +"window.history.pushState(\"\", \"BPC Optimizer\", \"${model.urlString()}\");"
                            }

                            optimize(loader, model)
                        }

                        div {
                            style.marginTop = 2.rem
                            data(model::optimizer, model::selctedPack) {
                                if (model.optimizer != null) {
                                    data(model::selctedPack) {
                                        div {
                                            style.width = 100.percent
                                            ul {
                                                if (model.optimizer!!.packs.count() == 0) {
                                                    li {
                                                        style.marginTop = 20.px

                                                        card().hoverable().color("red", 4)() {
                                                            style.borderRaw = "1px solid lightred"
                                                            cardContent().textColor("black").valAlignThis()() {
                                                                style.width = 95.percent
                                                                style.paddingRaw = "12px 8px"
                                                                style.displayRaw = "inline-block"

                                                                p { +"No Packs" }
                                                            }
                                                        }
                                                    }
                                                }

                                                model.optimizer!!.packs.forEach {
                                                    val pack = it
                                                    li {
                                                        style.marginTop = 20.px

                                                        card().hoverable()() {
                                                            if (model.selctedPack == pack) {
                                                                color("blue-grey", 3)
                                                            } else {
                                                                color("blue-grey", 4)
                                                            }

                                                            style.borderRaw = "1px solid lightslategrey"

                                                            cardContent().textColor("black").hoverable()() {
                                                                style.width = 95.percent
                                                                style.paddingRaw = "12px 8px"
                                                                style.displayRaw = "inline-block"

                                                                row {
                                                                    style.marginBottom = 0.px
                                                                    col(12) {
                                                                        style.displayRaw = "inline-block"

                                                                        div().left()() {
                                                                            style.displayRaw = "inline-block"
                                                                            b { +(it.bpPrice.format0ks() + " ISK for BPs") }
                                                                        }
                                                                        div().right()() {
                                                                            style.displayRaw = "inline-block"
                                                                            style.marginRight = 0.px
                                                                            b { +(it.allContracts.count().format() + " Contracts") }
                                                                        }
                                                                    }
                                                                }

                                                                row {
                                                                    style.marginBottom = 0.px
                                                                    col(12).centerAlignThis()() {
                                                                        style.displayRaw = "inline-block"
                                                                        b { +("ME: ${it.productBPC.blueprint.me.format()}") }

                                                                    }
                                                                }
                                                                row {
                                                                    style.marginBottom = 0.px

                                                                    col(12) {
                                                                        style.displayRaw = "inline-block"

                                                                        div().left()() {
                                                                            style.displayRaw = "inline-block"
                                                                            style.marginTop = 5.px
                                                                            b { +(it.totalPrice.format0ks() + " ISK Total") }
                                                                        }

                                                                        div().right()() {
                                                                            style.displayRaw = "inline-block"
                                                                            style.marginTop = 5.px
                                                                            b { +(it.packBPCs.count().format() + " BPCs In Pack") }
                                                                        }
                                                                    }
                                                                }

                                                            }
                                                        }
                                                    }.onClick {
                                                        model.selctedPack = pack
                                                        ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    col(5) {
                        style.marginLeft = 30.px
                        style.marginRight = 30.px

                        // center top
                        section {
                            style.paddingBottom = 0.px
                            style.displayRaw = "inline-block"
                            div {
                                style.displayRaw = "inline-block"

                                p {
                                    style.marginBottom = 0.px
                                    style.marginTop = 5.px
                                    b { +"Main BPC" }
                                }
                                +model.mainFilter.makeElementMETEOnly()
                            }

                            div {
                                style.displayRaw = "inline-block"
                                style.marginLeft = 20.px

                                p {
                                    style.marginBottom = 0.px
                                    style.marginTop = 5.px
                                    b { +"Component BPCs" }
                                }
                                +model.componentFilter.makeElementMETEOnly()

                            }
                        }

                        // center bottom

                        section {
                            style.paddingTop = 0.px
                            style.marginTop = 20.px

                            val allDisplays =
                                    data(model::selctedPack) {
                                        if (model.selctedPack != null) {
                                            div("center-align") {
                                                h(5) { +"Pack Stats" }
                                            }
                                            table().striped()() {
                                                thead {
                                                    trListHeader(elements = listOf(
                                                            "${model.selctedPack!!.totalPrice.format0s()} Total ISK",
                                                            "${model.selctedPack!!.bpPrice.format0s()} in BPCs",
                                                            "${model.selctedPack!!.matsPrice.sell.format0s()} in Mats"
                                                    ), thStyle = Style("padding" to "0".cssStyle))
                                                    trListHeader(elements = listOf(
                                                            "${model.selctedPack!!.allContracts.count()} Contracts",
                                                            "${model.selctedPack!!.packBPCs.count()} BPCs in Pack",
                                                            "${model.selctedPack!!.allUsedBPCs.count()} BPCs Total"
                                                    ), thStyle = Style("padding" to "6px 4px".cssStyle))
                                                }
                                            }

                                            div {
                                                style.marginTop = 2.rem

                                                row {
                                                    //script{+"\$('.tabs').tabs(); console.log(\"tabbed!\"); "}

                                                    lateinit var tabs: Tabs

                                                    col(12) {
                                                        style.paddingRaw = "0"

                                                        tabs = tabs(true).textColor("blue", -3)
                                                    }

                                                    tab(tabs, "s12", "s6 blue-text text-darken-3", {
                                                        textColor("blue", -3)
                                                        +"BPC Tree"
                                                    }) {
                                                        +BPDisplays.aggregateTree(model.selctedPack!!); style.paddingRaw = "0"
                                                    }.color("grey", 3)

                                                    tab(tabs, "s12", "s6 blue-text text-darken-3", {
                                                        textColor("blue", -3)
                                                        +"Contracts"
                                                    }) { +BPDisplays.byContract(model.selctedPack!!); style.paddingRaw = "0" }.color("grey", 3)

                                                }
                                            }

                                        }
                                    }

                        }
                    }

                    // facility picker
                    col(4).right()() {
                        style.marginRight = 5.px
                        section {
                            -model.facility
                        }
                        section {
                            data(model::selctedPack) {
                                if (model.selctedPack != null) {
                                    -AmountTable(model.selctedPack!!.neededOptimizedBaseMats, model.selctedPack!!.matsPrice.sell, false)
                                    divider().color("grey", -3)() {
                                        style.marginTop = 20.px
                                        style.marginBottom = 20.px
                                    }
                                    -AmountTable(model.selctedPack!!.neededRawBaseMats, 0.0)
                                }
                            }
                        }
                    }

                }

            }

        }


        scriptFrom("/js/materialize.min.js")
    }
}


