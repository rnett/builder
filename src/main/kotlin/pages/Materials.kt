package pages

import com.rnett.eve.ligraph.sde.*
import com.rnett.kframe.dom.*
import com.rnett.kframe.dom.classes.dataText
import com.rnett.kframe.element.DocumentBuilder
import com.rnett.kframe.element.percent
import com.rnett.kframe.element.px
import com.rnett.kframe.element.rem
import com.rnett.kframe.materalize.*
import main.connect
import models.TypeMaterials
import models.tdStyle
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import pages.ProductTypes.productTypes
import views.AmountTable
import views.NavBar
import kotlin.properties.Delegates

object ProductTypes {
    val productIDs: List<Int>

    val types: Map<Int, invtype>

    val productTypes: List<invtype>

    init {
        connect()

        productIDs = transaction {
            industryactivityrecipes
                    .slice(industryactivityrecipes.productTypeID, industryactivityrecipes.activityID)
                    .select { industryactivityrecipes.activityID eq 1 }
                    .distinctBy { it[industryactivityrecipes.productTypeID] }
                    .map { it[industryactivityrecipes.productTypeID] }
        }

        types = transaction {
            invtypes.selectAll().map { Pair(it[invtypes.typeID], invtype.wrapRow(it)) }
        }.toMap()

        productTypes = productIDs.asSequence().map { types[it] }.filter { it != null }.map { it!! }.toList()

    }

}

class MatsModel {

    val facility = models.Facility.basic

    var base: TypeMaterials? = null

    var type: invtype? by Delegates.observable(null as invtype?) { property, oldValue, newValue ->
        transaction {
            if (type != null)
                base = TypeMaterials(newValue!!, facility)
        }
    }

    init {
        //type = invtypes.fromName("Thanatos")
    }

}


val materialsPage: DocumentBuilder = {
    val model = MatsModel()
    if (urlParams.containsKey("type")) {
        val t = invtypes.fromName(urlParams["type"]!!)
        if (t != null)
            model.type = t
    }

    //TODO more url params (facility, main bp me/te, types?)
    //TODO integrate ore optimizer?  its so fast now
    //TODO use linop for contract optimisation?  use getBestBPCs then price per for each?
    /*
    add all contracts, with +bpc multipliers per bought.
    seek target bpcs.
    me/te?
    mats?
     */
    //TODO show all bps in contract view
    //TODO contract detail popup
    //TODO make login redirect to the same url, not just page
    //TODO add buttons to Facility view for min (Astra in HS) and max (Sot in Null w/ rigs)
    //TODO give java more processor power (it was running at ~2% cpus when optimizing slowly)
    //TODO clear pack view when optimizing new one
    //TODO optimiziations are hanging.  They finish server-side but aren't updated (possibly b/c I refreshed in the middle)
    //TODO user tracking

    head {
        +commonScripts
        title {
            dataText(model::type) {
                if (model.type == null)
                    "Material Calculator"
                else
                    " Materials: ${model.type?.typeName}"
            }
        }
    }

    body {
        klass = "grey lighten-2"

        -NavBar()

        container {

            style.width = 85.percent
            style.maxWidth = 85.percent

            row().col(16) {
                style.width = 100.percent

                section()

                section().row().valAlignThis()() {
                    col(3) {
                        style.marginLeft = 0.px

                        selectFromAutocomplete(model::type, productTypes, {
                            it?.typeName ?: ""
                        }, attrs = "id" to "typeNameIn").onChangeData(postCallJS = """
                            if(response[0] != ""){
                                window.history.pushState("", "Materials: " + response[0], "/materials/type/" + response[0]);
                            }
                        """.trimIndent(), useData = "value") { event, data ->
                            if (invtypes.fromName(data["value"]!!) != null)
                                data["value"]
                            else
                                ""
                        }
                        br()
                        +"Type ID: "
                        data(model::type) {
                            +(model.type?.typeID ?: 0).toString()
                        }
                    }

                    col(2) {
                        data(model::base) {
                            if (model.base != null) {
                                table {
                                    tbody {
                                        tr {
                                            style.borderRaw = "none"
                                            thString("BP ME")
                                            td {
                                                style.width = 3.rem
                                                intInput(model.base!!::bpME, attrs = "style" to tdStyle) { klass = "white"; style.height = 2.rem }
                                            }
                                        }
                                        tr {
                                            style.borderRaw = "none"
                                            thString("BP TE")
                                            td {
                                                style.width = 3.rem
                                                intInput(model.base!!::bpTE, attrs = "style" to tdStyle) { klass = "white"; style.height = 2.rem }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    col(2).valAlignThis()() {
                        style.whiteSpaceRaw = "nowrap"
                        div {
                            style.width = 0.px
                            style.paddingBottom = 100.percent
                            style.displayRaw = "inline-block"
                        }
                        data(model::type) {
                            if (model.type != null) {
                                if (transaction { model.type!!.group.category.categoryName == "Ship" || model.type!!.group.category.categoryName == "Drone" })
                                    responsiveImage(model.type!!.renderURL(256))
                                else
                                    responsiveImage(model.type!!.imageURL(64))

                            }

                        }
                    }
                    col(5) {
                        -model.facility
                    }
                }


                divider().color("grey, 1")


                section().row {
                    col(7) {
                        data(model::base, model::facility) {
                            style.width = 60.percent
                            table().striped().highlight()() {
                                thead {
                                    trListHeader(elements = listOf("Item", "Amount Per", "Total Amount", "BP ME", "BP TE", "Fac. ME", "Fac. TE", "Total ME", "Total TE"), thStyle = tdStyle)
                                }
                                tbody {
                                    if (model.base != null)
                                        -model.base!!

                                }
                            }
                        }
                    }
                    col(4).offset(1)() {

                        container().centerAlignThis()() {
                            data(model::base, model::facility) {
                                if (model.base != null) {
                                    data(model.base!!::baseMats) {
                                        btn(href = "/ore/raw/${model.base!!.baseMats.entries.joinToString("%0A") { it.key.typeName + "%09" + it.value.toString() }}") {
                                            wavesLight()
                                            style.marginTop = 1.rem
                                            style.marginLeft = 1.rem
                                            +"Optimize Ore"

                                        }
                                    }
                                }
                            }
                        }

                        data(model::base, model::facility) {
                            if (model.base != null) {
                                data(model.base!!::baseMats) {
                                    -AmountTable(model.base!!.baseMats, 0.0)
                                }
                            }
                        }
                    }


                }
            }
        }
    }
    scriptFrom("/js/materialize.min.js")
}