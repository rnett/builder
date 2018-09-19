package models

import com.rnett.eve.ligraph.sde.ItemList
import com.rnett.eve.ligraph.sde.MutableItemList

class OreModel {
    var inputString: String = ""
    var mats: MutableItemList = MutableItemList()
    var ores: ItemList = ItemList()
    var price: Double = 0.0
    var refineRate: Double = 92.39

    fun setMats(m: TypeMaterials) {
        mats = m.baseMats.toMutableItemList()
    }

    fun optimize() {
        val solution = OreOptimizer.optimize(mats.toItemList(), refineRate)

        price = solution.price
        ores = solution.ores
    }
}

