package pages

import com.rnett.eve.ligraph.sde.ItemList
import com.rnett.eve.ligraph.sde.parse
import com.rnett.kframe.dom.*
import com.rnett.kframe.element.*
import com.rnett.kframe.materalize.*
import models.OreModel
import views.AmountTable
import views.NavBar

val orePage: DocumentBuilder = {

    val model = OreModel()

    if (urlParams.containsKey("raw")) {
        val rawMins = urlParams["raw"]
        model.mats.parse(rawMins!!)
        model.optimize()
    }


    head {
        +commonScripts
        title {
            +"Ore Optimizer"
        }
    }

    body {
        klass = "grey lighten-2"
        -NavBar()

        container {
            style.width = 85.percent
            style.maxWidth = 85.percent
            row().col(12) {
                style.width = 100.percent

                section()

                section().row {
                    style.height = 60.percent
                    col(3).inputFieldThis()() {
                        style.marginTop = 0.px
                        stringInputMultiline(model::inputString, attrs = *arrayOf("style" to Style("background-color" to "white".cssStyle, "width" to 100.percent, "height" to 100.percent)))
                    }
                    col(1).valAlign {
                        style.height = 100.percent
                        container().centerAlignThis()() {
                            btn().wavesLight()() {
                                +"Parse"
                            }.onClick {

                                model.mats.parse(model.inputString)

                                model.ores = ItemList()
                                model.price = 0.0

                                ""
                            }
                        }
                    }
                    col(3) {
                        data(model::mats) {
                            -AmountTable(model.mats.toItemList(), 0.0, true)
                        }
                    }

                    col(1).valAlign {
                        style.height = 100.percent
                        container().centerAlignThis()() {
                            div {
                                klass = "input-field"
                                doubleInput(model::refineRate, attrs = *arrayOf("id" to "refineRate", "placeholder" to ""))
                                label(attrs = *arrayOf("for" to "refineRate")) {
                                    klass = "active"
                                    +"Refine Rate"
                                }
                            }
                            btn().wavesLight()() {
                                +"Optimize"
                            }.onClick {

                                model.optimize()

                                ""
                            }
                        }
                    }
                    col(3) {
                        data(model::ores) {
                            -AmountTable(model.ores, model.price, false)
                        }
                    }


                }
            }
        }

        scriptFrom("/js/materialize.min.js")
    }
}
