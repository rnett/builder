package models.blueprints

import com.rnett.kframe.dom.*
import com.rnett.kframe.dom.classes.AnyDisplayElementBuilder
import com.rnett.kframe.dom.classes.DisplayElement
import com.rnett.kframe.element.Style
import com.rnett.kframe.element.View
import com.rnett.kframe.element.px
import com.rnett.kframe.element.rem
import com.rnett.ligraph.eve.contracts.blueprints.BPType
import com.rnett.ligraph.eve.contracts.blueprints.Blueprint
import kotlin.reflect.KMutableProperty

open class BPFilter(open val type: BPType = BPType.BPC, open val minRuns: Int = -1, open val maxRuns: Int = -1,
                    open val minME: Int = -1, open val maxME: Int = -1, open val minTE: Int = -1, open val maxTE: Int = -1) {
    constructor(type: BPType = BPType.BPC, runs: Int = -1, me: Int = -1, te: Int = -1) : this(type, runs, runs, me, me, te, te)
    constructor(string: String) : this(
            BPType.valueOf(string.split(':')[0]),
            string.split(':')[1].toInt(),
            string.split(':')[2].toInt(),
            string.split(':')[3].toInt(),
            string.split(':')[4].toInt(),
            string.split(':')[5].toInt(),
            string.split(':')[6].toInt())

    fun matches(bp: Blueprint): Boolean {
        return bp.type == type &&
                (bp.type == BPType.BPO || minRuns < 0 || minRuns <= bp.runs) &&
                (bp.type == BPType.BPO || maxRuns < 0 || maxRuns >= bp.runs) &&
                (minME < 0 || minME <= bp.me) &&
                (maxME < 0 || maxME >= bp.me) &&
                (minTE < 0 || minTE <= bp.te) &&
                (maxTE < 0 || maxTE >= bp.te)
    }

    open val isBPC get() = type == BPType.BPC

    open fun copy(): BPFilter = BPFilter(type, minRuns, maxRuns, minME, maxME, minTE, maxTE)

    override fun toString(): String {
        return "$type:$minRuns:$maxRuns:$minME:$maxME:$minTE:$maxTE"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BPFilter) return false

        if (type != other.type) return false
        if (minRuns != other.minRuns) return false
        if (maxRuns != other.maxRuns) return false
        if (minME != other.minME) return false
        if (maxME != other.maxME) return false
        if (minTE != other.minTE) return false
        if (maxTE != other.maxTE) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.ordinal
        result = 31 * result + minRuns
        result = 31 * result + maxRuns
        result = 31 * result + minME
        result = 31 * result + maxME
        result = 31 * result + minTE
        result = 31 * result + maxTE
        return result
    }
}


class MutableBPFilter(@Transient override var type: BPType = BPType.BPC,
                      @Transient override var minRuns: Int = -1, @Transient override var maxRuns: Int = -1,
                      @Transient override var minME: Int = -1, @Transient override var maxME: Int = -1,
                      @Transient override var minTE: Int = -1, @Transient override var maxTE: Int = -1)
    : BPFilter(type, minRuns, maxRuns, minME, maxME, minTE, maxTE), View<DisplayElement<*>> {

    constructor(type: BPType = BPType.BPC, runs: Int = -1, me: Int = -1, te: Int = -1) : this(type, runs, runs, me, me, te, te)
    constructor(string: String) : this(
            BPType.valueOf(string.split(':')[0]),
            string.split(':')[1].toInt(),
            string.split(':')[2].toInt(),
            string.split(':')[3].toInt(),
            string.split(':')[4].toInt(),
            string.split(':')[5].toInt(),
            string.split(':')[6].toInt())


    override fun copy(): MutableBPFilter = MutableBPFilter(type, minRuns, maxRuns, minME, maxME, minTE, maxTE)

    fun loadFromString(string: String) {
        val other = BPFilter(string)

        type = other.type
        minRuns = other.minRuns
        maxRuns = other.maxRuns
        minME = other.minME
        maxME = other.maxME
        minTE = other.minTE
        maxTE = other.maxTE
    }

    override var isBPC: Boolean
        get() = super.isBPC
        set(value) {
            type = if (value)
                BPType.BPC
            else
                BPType.BPO
        }

    override fun makeElements(): AnyDisplayElementBuilder = {

        val top = this

        div {
            data(this@MutableBPFilter::type) {
                table {

                    thead {
                        if (this@MutableBPFilter.type == BPType.BPC)
                            trList(elements = *arrayOf("BPC", "Runs", "(min, max)", "ME", "(min, max)", "TE", "(min, max)"), tdStyle = Style("padding-top" to 0.px))
                        else
                            trList(elements = *arrayOf("BPC", "ME", "(min, max)", "TE", "(min, max)"))
                    }

                    tbody {
                        tr {

                            td {
                                p {
                                    label {
                                        attributes["for"] = "bpcCheckbox"
                                        boolInput(this@MutableBPFilter::isBPC, attrs = "id" to "bpcCheckbox") {
                                            klass = "filled-in"
                                        }
                                        span { +"" }
                                    }
                                }
                            }

                            if (this@MutableBPFilter.type == BPType.BPC) {
                                td { inputHelper(this@MutableBPFilter::minRuns) }
                                td { inputHelper(this@MutableBPFilter::maxRuns) }
                            }

                            td { inputHelper(this@MutableBPFilter::minME) }
                            td { inputHelper(this@MutableBPFilter::maxME) }
                            td { inputHelper(this@MutableBPFilter::minTE) }
                            td { inputHelper(this@MutableBPFilter::maxTE) }
                        }

                    }
                }
            }
        }
    }

    fun makeElementMETEOnly(): AnyDisplayElementBuilder = {

        val top = this

        div {
            table {
                thead {
                    trList(elements = *arrayOf("Min ME", "Min TE"))
                }

                tbody {
                    tr {

                        td { inputHelper(this@MutableBPFilter::minME) }
                        td { inputHelper(this@MutableBPFilter::minTE) }
                    }

                }
            }
        }
    }

    fun makeBPCTarget(): AnyDisplayElementBuilder = {

        val top = this

        div {
            table {
                thead {
                    trList(elements = *arrayOf("Runs", "Min ME", "Min TE"))
                }

                tbody {
                    tr {
                        td { inputHelper(this@MutableBPFilter::minRuns) }
                        td { inputHelper(this@MutableBPFilter::minME) }
                        td { inputHelper(this@MutableBPFilter::minTE) }
                    }

                }
            }
        }
    }
}

internal fun DisplayElement<*>.inputHelper(prop: KMutableProperty<Int>) =
        input(prop, { if (it == "") -1 else it.toInt() }, { if (it == -1) "" else it.toString() }) {
            klass = "white"; style.height = 2.rem; style.width = 2.5.rem
        }
