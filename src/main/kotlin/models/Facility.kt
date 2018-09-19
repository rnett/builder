package models

import com.rnett.eve.ligraph.sde.*
import com.rnett.kframe.dom.classes.AnyDisplayElementBuilder
import com.rnett.kframe.dom.classes.DisplayElement
import com.rnett.kframe.dom.data
import com.rnett.kframe.dom.div
import com.rnett.kframe.dom.onClick
import com.rnett.kframe.dom.selectOf
import com.rnett.kframe.element.Style
import com.rnett.kframe.element.View
import com.rnett.kframe.element.cssStyle
import com.rnett.kframe.element.percent
import com.rnett.kframe.materalize.btn
import com.rnett.kframe.materalize.col
import com.rnett.kframe.materalize.color
import com.rnett.kframe.materalize.row
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.absoluteValue
import kotlin.properties.Delegates

enum class FacilityType(val size: Size, val meBonus: Double = 0.0, val teBonus: Double = 0.0) {

    Astrahus(Size.Medium), Fortizar(Size.Large), Keepstar(Size.XL),
    Raitaru(Size.Medium, 1.0, 15.0), Azbel(Size.Large, 1.0, 20.0), Sotiyo(Size.XL, 1.0, 30.0);

    val type: invtype = invtypes.fromName(this.name)!!

    enum class Size {
        Medium, Large, XL;

        companion object {
            val validRigs = mapOf(
                    FacilityType.Size.Medium to transaction { invmarketgroup[2347].invmarketgroup_invtypes_marketGroup.map { Rig[it] }.toList() },
                    FacilityType.Size.Large to transaction { invmarketgroup[2348].invmarketgroup_invtypes_marketGroup.map { Rig[it] }.toList() },
                    FacilityType.Size.XL to transaction { invmarketgroup[2349].invmarketgroup_invtypes_marketGroup.map { Rig[it] }.toList() }
            )
        }

    }

    val validRigs: List<Rig> = Size.validRigs[size]!!
}

class EfficiencyBonus(vararg bonuses: Double) {
    val bonuses: MutableList<Double> = bonuses.map { it.absoluteValue }.toMutableList()

    val multiplier: Double
        get() = bonuses.asSequence().map { (100.0 - it) / 100.0 }.reduce { a, b -> a * b }

    val totalBonus: Double
        get() = (1.0 - multiplier) * 100.0

    override fun toString(): String {
        return bonuses.joinToString(", ") { it.toString() } + " ==> $totalBonus : $multiplier"
    }

    fun add(bonus: Double): EfficiencyBonus {
        if (bonus != 0.0)
            bonuses.add(bonus)

        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EfficiencyBonus

        if (bonuses != other.bonuses) return false

        return true
    }

    override fun hashCode(): Int {
        return bonuses.hashCode()
    }


}

class Facility(facilityType: FacilityType, var security: Security, var rig1: Rig? = null, var rig2: Rig? = null, var rig3: Rig? = null) : View<DisplayElement<*>>, Cloneable {

    override fun toString(): String = toString(true)

    fun toString(useShorts: Boolean = true): String {
        if (this == best && useShorts)
            return "best"

        if (this == basic && useShorts)
            return "basic"

        return "$facilityType:$security:${rig1?.type?.typeID ?: "-1"}:${rig2?.type?.typeID ?: "-1"}:${rig3?.type?.typeID
                ?: "-1"}"
    }

    fun loadFromString(string: String) {

        if (string == "best")
            loadFrom(best)
        else if (string == "basic")
            loadFrom(basic)
        else {
            val new = Facility.fromString(string)
            facilityType = new.facilityType
            security = new.security
            rig1 = new.rig1
            rig2 = new.rig2
            rig3 = new.rig3
        }
    }

    fun loadFrom(facility: Facility) = loadFromString(facility.toString(false))

    companion object {
        val basic get() = Facility(FacilityType.Astrahus, Security.HighSec)

        val best
            get() =
                Facility(FacilityType.Sotiyo, Security.NullSecWH, Rig(invtypes.fromID(37179)!!), Rig(invtypes.fromID(37181)!!), Rig(invtypes.fromID(43705)!!))

        fun fromString(string: String): Facility =
                try {
                    val vals = string.split(":")

                    val rig1 = transaction { invtypes.fromID(vals[2].toInt()) }
                    val rig2 = transaction { invtypes.fromID(vals[3].toInt()) }
                    val rig3 = transaction { invtypes.fromID(vals[4].toInt()) }

                    Facility(FacilityType.valueOf(vals[0]), Security.valueOf(vals[1]),
                            if (rig1 != null) Rig(rig1) else null,
                            if (rig2 != null) Rig(rig2) else null,
                            if (rig3 != null) Rig(rig3) else null)
                } catch (e: Exception) {
                    basic
                }
    }

    var facilityType: FacilityType by Delegates.observable(facilityType) { _, _, newValue ->
        if (!newValue.validRigs.contains(rig1))
            rig1 = null
        if (!newValue.validRigs.contains(rig2))
            rig2 = null
        if (!newValue.validRigs.contains(rig3))
            rig3 = null
    }

    val rigs: List<Rig?>
        get() = listOf(rig1, rig2, rig3)

    fun getMEBonus(type: invtype): EfficiencyBonus {
        val eb = EfficiencyBonus(facilityType.meBonus)
        rigs.filterNotNull().forEach {
            if (it.appliesTo(type))
                eb.add(it.meBonus(security))
        }

        return eb
    }

    fun getTEBonus(type: invtype): EfficiencyBonus {
        val eb = EfficiencyBonus(facilityType.meBonus)
        rigs.filterNotNull().forEach {
            if (it.appliesTo(type))
                eb.add(it.teBonus(security))
        }

        return eb
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Facility)
            return false

        return facilityType == other.facilityType && security == other.security && rigs == other.rigs
    }

    override fun makeElements(): AnyDisplayElementBuilder = {
        div {
            id = "facilityDiv"

            row {
                col(6) {
                    btn().color("grey")() {
                        style.width = 100.percent
                        +"Best Facility"
                    }.onClick {
                        loadFrom(best)

                        ""
                    }
                }
                col(6) {
                    btn().color("grey")() {
                        style.width = 100.percent
                        +"Basic Facility"
                    }.onClick {
                        loadFrom(basic)

                        ""
                    }

                }
            }

            selectOf(this@Facility::facilityType, FacilityType.values().toList(), {
                it.type.typeName
            }, klass = "browser-default", attrs = *arrayOf("style" to Style("display" to "initial".cssStyle)))


            selectOf(this@Facility::security, Security.values().toList(), {
                when (it) {
                    Security.HighSec -> "High Sec"
                    Security.NullSecWH -> "Null Sec or Wormhole"
                    Security.LowSec -> "Low Sec"
                }
            }, klass = "browser-default", attrs = *arrayOf("style" to Style("display" to "initial".cssStyle)))


            data(this@Facility::facilityType) {
                selectOf(this@Facility::rig1, facilityType.validRigs + listOf(null), {
                    it?.type?.typeName ?: "None"
                }, klass = "browser-default", attrs = *arrayOf("style" to Style("display" to "initial".cssStyle)))


                selectOf(this@Facility::rig2, facilityType.validRigs + listOf(null), {
                    it?.type?.typeName ?: "None"
                }, klass = "browser-default", attrs = *arrayOf("style" to Style("display" to "initial".cssStyle)))


                selectOf(this@Facility::rig3, facilityType.validRigs + listOf(null), {
                    it?.type?.typeName ?: "None"
                }, klass = "browser-default", attrs = *arrayOf("style" to Style("display" to "initial".cssStyle)))
            }

        }
    }

    override fun hashCode(): Int {
        var result = security.hashCode()
        result = 31 * result + (rig1?.hashCode() ?: 0)
        result = 31 * result + (rig2?.hashCode() ?: 0)
        result = 31 * result + (rig3?.hashCode() ?: 0)
        result = 31 * result + facilityType.hashCode()
        return result
    }

}