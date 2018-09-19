package views

import com.rnett.eve.ligraph.sde.*
import com.rnett.kframe.dom.*
import com.rnett.kframe.dom.classes.AnyDisplayElement
import com.rnett.kframe.dom.classes.AnyDisplayElementBuilder
import com.rnett.kframe.element.Style
import com.rnett.kframe.element.View
import com.rnett.kframe.element.px
import com.rnett.kframe.element.rem
import com.rnett.kframe.materalize.*
import main.format0s
import models.tdStyle

class AmountTable(val amounts: ItemList, var price: Double, appraise: Boolean = true) : View<AnyDisplayElement> {

    init {
        if (price == 0.0 && appraise && amounts.isNotEmpty()) {
            val appraisal = amounts.appraise()

            price = appraisal.totalPrice.sell
        }
    }

    //TODO seperate mineral price and full price
    override fun makeElements(): AnyDisplayElementBuilder =
            {

                data(this@AmountTable::amounts) {
                    table().striped().highlight()() {
                        thead {
                            trListHeader(elements = listOf("Item", "Amount"))
                        }
                        tbody {
                            for (mat in amounts.entries.asSequence().toList().asSequence().sortedBy { it.key.typeID - (if (it.key.isMineral) 10000 else -10000) }.toList())
                                tr {

                                    if (mat.key.isMineral)
                                        style.borderRaw = "1px solid green"

                                    td(attrs = *arrayOf("style" to tdStyle)) {
                                        klass = "valign-wrapper"
                                        responsiveImage(mat.key.imageURL(32), attrs = *arrayOf("style" to Style("margin-right" to 12.px)))
                                        +mat.key.typeName
                                    }
                                    td(attrs = *arrayOf("style" to tdStyle)) { +mat.value.toString() }
                                }
                        }
                    }
                }

                container().centerAlignThis()() {
                    p {
                        +"<b>"
                        +"Total Sell Price: "
                        data(this@AmountTable::price) {
                            if (price != 0.0) {
                                +(price.format0s() + " ISK")
                            } else {
                                +"Unknown"
                            }
                        }
                        +"</b>"
                    }
                    valAlign {
                        style.displayRaw = "inline-block"
                        style.marginTop = 1.rem

                        btn().wavesLight()() {
                            span().valAlignThis()() {
                                responsiveImage("https://evepraisal.com/static/favicon.ico") { style.marginRight = 10.px; style.width = 32.px }
                                +"Evepraisal"
                            }
                        }.onClick(postCallJS = """
                                    if(response[0] != "!")
                                                    window.open("https://evepraisal.com/a/" + response[0]);""".trimIndent()) {

                            if (amounts.isEmpty())
                                "!"
                            else
                                amounts.evePraisal()
                        }
                        btn(size = "small").wavesLight()() {
                            style.marginLeft = 2.px
                            style.backgroundColorRaw = "white"
                            title = "Copy to Clipboard"
                            i {
                                klass = "material-icons"
                                style.colorRaw = "black"
                                +"content_copy"
                            }
                        }.onClick(postCallJS = """
                            if(response[0] != ""){
                                console.log(response[0]);
                                console.log(copyToClipboard(response[0]));
                            }
                        """.trimIndent()) {
                            if (this@AmountTable.amounts.isEmpty())
                                ""
                            else
                                this@AmountTable.amounts.entries.joinToString("\n") { it.key.typeName + "\t" + it.value }
                        }
                    }

                }
            }
}

