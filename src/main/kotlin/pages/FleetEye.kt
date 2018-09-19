package pages

import com.rnett.eve.ligraph.sde.mapregion
import com.rnett.eve.ligraph.sde.renderURL
import com.rnett.kframe.dom.*
import com.rnett.kframe.dom.classes.AnyDisplayElement
import com.rnett.kframe.dom.classes.AnyDisplayElementBuilder
import com.rnett.kframe.element.*
import com.rnett.kframe.materalize.Row
import com.rnett.kframe.materalize.col
import com.rnett.kframe.materalize.container
import com.rnett.kframe.materalize.row
import com.rnett.ligraph.eve.dotlanmaps.kframe.dotlanMap
import com.rnett.ligraph.eve.fleeteye.Group
import com.rnett.ligraph.eve.fleeteye.GroupMember
import main.format0s
import models.FleetMap
import org.jetbrains.exposed.sql.transactions.transaction
import views.NavBar

//TODO sending too many events at a time crashes

val regions = transaction { mapregion.all().toList() }.filter { it.regionName.matches(Regex("[A-z ]*")) }
val regionsByName = regions.map { Pair(it.regionName, it) }.toMap()

val fleeteyePage: DocumentBuilder = {

    val model = FleetMap()

    if (urlParams.contains("region")) {
        val regionName = urlParams["region"]
        if (regionsByName.containsKey(regionName))
            model.regionName = regionName!!
    }

    head {
        script {
            +"""${'$'}(document).ready(function(){
    ${'$'}('select').formSelect();
  });"""
        }
        +commonScripts
        title {
            +"Fleet Eye"
        }
    }

    body {
        klass = "grey lighten-2"

        -NavBar()

        div {
            style.width = 210.px
            style.marginRight = 20.px
            style.floatRaw = "right"
            //klass = "input-field"
            selectOf(model::region, regions, {
                it?.regionName?.trim() ?: ""
            }, attrs = "id" to "typeNameIn").onChangeData(postCallJS = """
                            console.log(response);
                            ${'$'}('select').formSelect();
                            if(response[0] != ""){
                                window.history.pushState("", "Fleeteye region: " + response[0], "/fleeteye/region/" + response[0]);
                            }
                        """.trimIndent(), useData = "value") { event, data ->
                try {
                    if (data["value"] != null && data["value"]!!.toInt() >= 0 && data["value"]!!.toInt() < regions.size) {
                        regions[data["value"]!!.toInt()].regionName
                    } else
                        ""
                } catch (e: Exception) {
                    ""
                }
            }
        }

        container {

            style.width = 100.percent
            style.maxWidth = 100.percent

            row().col(16) {
                style.width = 100.percent

                row {

                    data(model::region) {
                        if (model.region != null)
                            dotlanMap(model.region!!.regionID)
                    }

                }

            }
        }
    }
    scriptFrom("/js/materialize.min.js")
}

class GroupWraper(val group: Group) : View<Row> {
    override fun makeElements(): ElementBuilder<Row> = {
        col(2) {
            style.marginTop = 0.px
            group.members.forEach {
                -PilotWraper(it)
            }
        }
    }
}

class PilotWraper(val pilot: GroupMember) : View<AnyDisplayElement> {
    override fun makeElements(): AnyDisplayElementBuilder = {
        div {
            style.marginBottom = 10.px
            img(transaction { pilot.pilot.ship.renderURL(256) }) {
                klass = "responsive-img"
                style.maxWidth = 56.px
            }
            p {
                +"${transaction { pilot.pilot.charname }} : ${pilot.strength.format0s()}"
                br()
                +(transaction { pilot.pilot.alliance?.alliancename } ?: "No Alliance")
            }
        }
    }
}