package pages

import com.kizitonwose.time.seconds
import com.rnett.eve.ligraph.sde.fromName
import com.rnett.eve.ligraph.sde.imageURL
import com.rnett.eve.ligraph.sde.invtype
import com.rnett.eve.ligraph.sde.invtypes
import com.rnett.kframe.dom.*
import com.rnett.kframe.element.*
import com.rnett.kframe.materalize.*
import com.rnett.ligraph.eve.contracts.blueprints.BPType
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.header
import io.ktor.client.request.post
import kotlinx.coroutines.experimental.launch
import main.format
import main.format0ks
import main.getStationName
import models.blueprints.*
import models.tdStyle
import org.jetbrains.exposed.sql.transactions.transaction
import pages.ProductTypes.productTypes
import views.NavBar

class BpFinderModel {

    var type: invtype? = null
    val filter: MutableBPFilter = MutableBPFilter(minME = 10, minTE = 20)

    var appraisal: List<BPCRunCounter>? = null

    val runs get() = filter.minRuns

    fun appraise() {
        if (type != null && type?.blueprint() != null && filter.minRuns > 0) {
            appraisal = BPAppraisal.getBestBPCsForProduct(type!!, runs, BPFilter(BPType.BPC, minME = filter.minME, minTE = filter.minTE))
        }
    }

    internal fun urlString(value: String? = null): String {

        var name = type?.typeName ?: ""

        if (value != null && invtypes.fromName(value) != null)
            name = value

        return "/blueprintfinder${if (name != "") "/type/$name" else ""}/filter/$filter"
    }

}

val bpFinderPage: DocumentBuilder = {
    val model = BpFinderModel()
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
            +"BPC Finder"
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

                col(4) {
                    style.marginLeft = 30.px
                    +model.filter.makeBPCTarget()
                }

            }
        }

        loader = container().centerAlignThis()() {
            style.marginTop = 1.rem
            circleSpinnerMulticolor(size = "big").active()
        }

        data(model::filter, model::type) {
            this runJS """window.history.pushState("", "BPC Finder", "${model.urlString()}");"""

            loader.displayed = true
            page.updateInterval = 1.seconds
            model.appraisal = null

            launch {
                println("Appraising")
                model.appraise()
                println("Appraising Done")

                loader.displayed = false
                page.updateInterval = page.defaultUpdateInterval
            }

        }

        section {
            style.width = 100.percent
            style.maxWidth = 100.percent
            data(model::appraisal) {

                if (model.appraisal != null) {
                    h(5) {
                        style.marginLeft = 2.rem
                        +"Total Price: ${model.appraisal!!.sumByDouble { it.bpContracted.contract.price }.format0ks()}"
                    }
                }

                val modals = model.appraisal?.map { Pair(it.bpContracted.contract.contractId, contractModal(it.bpContracted.contract)) }?.toMap()
                        ?: emptyMap()

                script { +"\$('.modal').modal();" }

                table().striped()() {
                    thead {
                        tr {
                            th()
                            th { +"Runs" }
                            th { +"ME" }
                            th { +"TE" }
                            th { +"Price" }

                            th { +"Price Per Run Used" }
                            th { +"Price Per Run" }

                            th { +"Other Items" }

                            th { +"Station" }
                            th { +"Contract" }
                            th { +"Open In EVE" }
                        }
                    }
                    tbody {
                        if (model.appraisal != null) {

                            val pic = model.appraisal!!.first().bpc.bpType.imageURL(32)

                            model.appraisal!!.forEach {

                                val contract = it.bpContracted.contract

                                tr().hoverable()() {
                                    td {
                                        responsiveImage(pic)
                                    }

                                    tdString("${it.runsUsed}" + if (it.runsLeft > 0) " (${it.runsLeft})" else "", attrs = *arrayOf("style" to tdStyle))
                                    tdString(it.bpc.me.format(), attrs = *arrayOf("style" to tdStyle))
                                    tdString(it.bpc.te.format(), attrs = *arrayOf("style" to tdStyle))

                                    tdString(contract.price.format0ks(), attrs = *arrayOf("style" to tdStyle))

                                    tdString((contract.price / it.runsUsed).format0ks(), attrs = *arrayOf("style" to tdStyle))

                                    tdString((contract.price / it.totalRuns).format0ks(), attrs = *arrayOf("style" to tdStyle))

                                    tdString((if (transaction { contract.items.count() } == 1) "No" else "Yes"), attrs = *arrayOf("style" to tdStyle))

                                    tdString(getStationName(contract.endLocationId), attrs = *arrayOf("style" to tdStyle))
                                    tdString(contract.title, attrs = *arrayOf("style" to tdStyle))

                                    val contractUrl = "https://esi.evetech.net/latest/ui/openwindow/contract/?contract_id=${contract.contractId}&datasource=tranquility"
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

        scriptFrom("/js/materialize.min.js")
    }
}