package pages

import com.kizitonwose.time.seconds
import com.rnett.eve.ligraph.sde.fromName
import com.rnett.eve.ligraph.sde.imageURL
import com.rnett.eve.ligraph.sde.invtype
import com.rnett.eve.ligraph.sde.invtypes
import com.rnett.kframe.dom.*
import com.rnett.kframe.element.*
import com.rnett.kframe.materalize.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.header
import io.ktor.client.request.post
import kotlinx.coroutines.experimental.launch
import main.format
import main.format0s
import main.getStationName
import models.blueprints.BPAppraisal
import models.blueprints.MutableBPFilter
import models.blueprints.blueprint
import models.blueprints.contractModal
import org.jetbrains.exposed.sql.transactions.transaction
import pages.ProductTypes.productTypes
import views.NavBar

class BpBrowserModel {

    var type: invtype? = null
    val filter: MutableBPFilter = MutableBPFilter()

    var appraisal: BPAppraisal? = null

    fun appraise() {
        if (type != null && type?.blueprint() != null)
            appraisal = BPAppraisal.appraise(type!!.blueprint()!!, filter)
    }

    internal fun urlString(value: String? = null): String {

        var name = type?.typeName ?: ""

        if (value != null && invtypes.fromName(value) != null)
            name = value

        return "/blueprints${if (name != "") "/type/$name" else ""}/filter/$filter"
    }

}

val bpBrowserPage: DocumentBuilder = {
    val model = BpBrowserModel()
    if (urlParams.containsKey("type")) {
        val t = invtypes.fromName(urlParams["type"]!!)
        if (t != null)
            model.type = t
    }

    if (urlParams.containsKey("filter")) {
        model.filter.loadFromString(urlParams["filter"]!!)
    }

    head {

        title {
            +"Blueprint Browser"
        }
        script {
            +"""${'$'}(document).ready(function(){
    ${'$'}('select').formSelect();
  });"""
        }
        +commonScripts
    }

    body {
        klass = "grey lighten-2"

        -NavBar()

        lateinit var loader: AnyElement

        container().section {
            style.width = 100.percent
            style.maxWidth = 100.percent
            row {

                col(3) {
                    style.marginLeft = 0.px
                    style.marginTop = 4.rem
                    style.width = 25.rem

                    selectFromAutocomplete(model::type, productTypes, {
                        it?.typeName ?: ""
                    })
                }

                col(8) {
                    style.marginLeft = 30.px
                    -(model.filter)
                }

            }
        }

        loader = container().centerAlignThis()() {
            style.marginTop = 1.rem
            circleSpinnerMulticolor(size = "big").active()
        }


        data(model::filter, model::type) {
            script {
                +"window.history.pushState(\"\", \"Blueprint Browser\", \"${model.urlString()}\");"
            }

            loader.displayed = true
            page.updateInterval = 1.seconds
            model.appraisal = null

            launch {
                model.appraise()

                loader.displayed = false
                page.updateInterval = page.defaultUpdateInterval
            }

        }

        //TODO isn't getting updated correctly on load, having to wait for another update
        section {
            style.width = 100.percent
            style.maxWidth = 100.percent
            data(model::appraisal) {

                val modals = model.appraisal?.matchesByPrice?.map { Pair(it.contract.contractId, contractModal(it.contract)) }?.toMap()
                        ?: emptyMap()

                script { +"\$('.modal').modal();" }

                table().striped()() {
                    thead {
                        tr {
                            th()
                            if (model.filter.isBPC) {
                                th { +"Runs" }
                            }
                            th { +"ME" }
                            th { +"TE" }
                            th { +"Price" }

                            if (model.filter.isBPC)
                                th { +"Price Per Run" }

                            th { +"Other Items" }

                            th { +"Station" }
                            th { +"Contract" }
                            th { +"Open In EVE" }
                        }
                    }
                    tbody {
                        if (model.appraisal != null && model.appraisal!!.matchesByPrice.count() > 0) {

                            val pic = model.appraisal!!.matchesByPrice.first().usedBPs.first().blueprint.bpType.imageURL(32)

                            model.appraisal!!.matchesByPricePerRun.forEach {

                                val contract = it.contract

                                tr().hoverable()() {
                                    td {
                                        responsiveImage(pic)
                                    }
                                    if (model.filter.isBPC)
                                        td { +it.totalRuns.format() }

                                    td { +it.averageME.format() }
                                    td { +it.averageTE.format() }
                                    td { +it.price.format0s() }

                                    if (model.filter.isBPC)
                                        td { +it.pricePerRun.format0s() }

                                    td { +(if (transaction { it.contract.items.count() } == 1) "No" else "Yes") }

                                    td { +getStationName(it.contract.endLocationId) }
                                    td { +it.contract.title }
                                    val contractUrl = "https://esi.evetech.net/latest/ui/openwindow/contract/?contract_id=${it.contract.contractId}&datasource=tranquility"
                                    td {
                                        a("", "btn grey darken-3") {

                                            if (!isCharLogedIn)
                                                classes.add("disabled")

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
                                }.onDblClick {
                                    modals[contract.contractId]?.open()
                                    ""
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