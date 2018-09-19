package models

import com.rnett.eve.ligraph.sde.mapregion
import com.rnett.eve.ligraph.sde.mapregions
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class FleetMap(var region: mapregion? = null) {
    var regionName: String
        get() = region?.regionName ?: ""
        set(v) {
            val rr = transaction { mapregions.select { mapregions.regionName eq v }.firstOrNull() }

            region = if (rr == null)
                null
            else
                transaction { mapregion.wrapRow(rr) }
        }
}