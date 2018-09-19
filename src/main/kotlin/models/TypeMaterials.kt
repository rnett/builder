package models

import com.rnett.eve.ligraph.sde.ItemList
import com.rnett.eve.ligraph.sde.imageURL
import com.rnett.eve.ligraph.sde.invtype
import com.rnett.eve.ligraph.sde.toItemList
import com.rnett.kframe.dom.*
import com.rnett.kframe.dom.classes.TRElement
import com.rnett.kframe.dom.classes.TableElement
import com.rnett.kframe.dom.classes.dataText
import com.rnett.kframe.element.*
import com.rnett.kframe.materalize.hoverable
import com.rnett.kframe.materalize.responsiveImage
import com.rnett.kframe.materalize.valAlignThis
import main.format
import models.blueprints.materials
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.ceil

val tdStyle = Style("padding" to "3px 5px 3px 3px".cssStyle)

class TypeMaterials(val parent: TypeMaterials?, val type: invtype, val originalAmount: Int, var bpME: Int, var bpTE: Int, val facility: Facility) : View<TableElement> {

    constructor(type: invtype, facility: Facility) : this(null, type, 1, facility)

    constructor(parent: TypeMaterials?, type: invtype, originalAmount: Int, facility: Facility) : this(parent, type, originalAmount, 0, 0, facility) {
        if (transaction { type.group.category.categoryName } != "Ship") {
            bpME = 10
            bpTE = 20
        }
    }

    val children: MutableList<TypeMaterials> = mutableListOf()

    val baseMats: ItemList
        get() {
            val myBases = children.asSequence().filter { it.baseMat }.map { Pair(it.type, it.totalAmount) }.toList().toMap().toMutableMap()
            children.forEach {
                it.baseMats.forEach {
                    myBases[it.key] = (myBases[it.key] ?: 0) + it.value
                }
            }
            return myBases.toItemList()
        }

    val level: Int

    override fun equals(other: Any?): Boolean {
        if (other == null)
            return false

        if (other !is TypeMaterials)
            return false

        other

        return this.type == other.type &&
                this.bpME == other.bpME &&
                this.bpTE == other.bpTE &&
                this.totalAmount == other.totalAmount &&
                this.children.zip(other.children).all { it.first == it.second } &&
                this.facility == other.facility
    }

    init {
        parent?.children?.add(this)
        level = if (parent != null)
            parent.level + 1
        else
            0

        transaction {
            type.materials().forEach { TypeMaterials(this@TypeMaterials, it.key, it.value.toInt(), facility) }
        }

    }

    val facilityME get() = facility.getMEBonus(type)
    val facilityTE get() = facility.getTEBonus(type)

    val ME
        get() = facility.getMEBonus(type).add(bpME.toDouble())
    val TE
        get() = facility.getTEBonus(type).add(bpTE.toDouble())


    val singleAmount: Int
        get() = ceil(originalAmount.toDouble() * (parent?.ME?.multiplier ?: 1.0)).toInt()

    val totalAmount: Long
        get() = singleAmount * (parent?.totalAmount ?: 1)

    val baseMat
        get() = children.size == 0

    var tr: TRElement? = null

    fun hide() {
        tr?.displayed = false

        children.forEach {
            it.hide()
        }
    }

    override fun makeElements(): ElementBuilder<TableElement> = {
        this as TableElement
        this@TypeMaterials.tr = tr {

            attributes["parentMaterial"] = this@TypeMaterials.parent?.tr?.elementID ?: 0
            displayed = !this@TypeMaterials.baseMat || this@TypeMaterials.level <= 1

            hoverable()
            td(attrs = *arrayOf("style" to tdStyle)).valAlignThis()() {
                klass = "valign-wrapper"
                responsiveImage(this@TypeMaterials.type.imageURL(32), attrs = *arrayOf("style" to Style("margin-right" to 7.px, "margin-left" to (level * 50).px)))
                +(this@TypeMaterials.type.typeName + "\t")

            }
            td(attrs = *arrayOf("style" to tdStyle)) {
                dataText(this@TypeMaterials::singleAmount)
            }

            td(attrs = *arrayOf("style" to tdStyle)) {
                dataText(this@TypeMaterials::totalAmount)
            }

            if (!this@TypeMaterials.baseMat) {
                if (level > 0) {
                    td(attrs = *arrayOf("style" to tdStyle)) {
                        intInput(this@TypeMaterials::bpME) { klass = "white"; style.height = 2.rem }
                        style.width = 3.rem
                    }
                    td(attrs = *arrayOf("style" to tdStyle)) {
                        intInput(this@TypeMaterials::bpTE) { klass = "white"; style.height = 2.rem }
                        style.width = 3.rem
                    }
                } else {
                    td(attrs = *arrayOf("style" to tdStyle)) {
                        dataText(this@TypeMaterials::bpME, { it.format() })
                    }
                    td(attrs = *arrayOf("style" to tdStyle)) {
                        dataText(this@TypeMaterials::bpTE, { it.format() })
                    }
                }
                td(attrs = *arrayOf("style" to tdStyle)) {
                    dataText(this@TypeMaterials::facilityME, { it.totalBonus.format() })
                }
                td(attrs = *arrayOf("style" to tdStyle)) {
                    dataText(this@TypeMaterials::facilityTE, { it.totalBonus.format() })
                }
                td(attrs = *arrayOf("style" to tdStyle)) {
                    dataText(this@TypeMaterials::ME, { it.totalBonus.format() })
                }
                td(attrs = *arrayOf("style" to tdStyle)) {
                    dataText(this@TypeMaterials::TE, { it.totalBonus.format() })
                }
            } else {
                tdString(data = "-")
                tdString(data = "-")
                tdString(data = "-")
                tdString(data = "-")
                tdString(data = "-")
                tdString(data = "-")
            }

            onClickJS(
                    """
if(event.target.tagName != "INPUT"){
    var element = $(event.currentTarget);

    var subs = $("[parentMaterial='${elementID}']")

    if(subs.first().css("display") != "none")
        subs.css("display", "none");
    else
        subs.css("display", "table-row");
}
                """
            )

        }

        this@TypeMaterials.children.forEach {
            -it
        }

    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + originalAmount
        result = 31 * result + bpME
        result = 31 * result + bpTE
        result = 31 * result + facility.hashCode()
        return result
    }
}