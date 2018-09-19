package main

import com.rnett.kframe.dom.body
import com.rnett.kframe.dom.head
import com.rnett.kframe.element.AnyElement
import com.rnett.kframe.element.DocumentBuilder
import com.rnett.kframe.hosts.web.WebsiteDef
import com.rnett.kframe.hosts.web.site
import io.ktor.application.Application
import models.blueprints.ContractUpdateChecker
import pages.*
import pages.AuthData.Companion.auth
import views.NavBar
import java.util.*

fun initalize() {
    var add = true
    val usrPathsField = ClassLoader::class.java.getDeclaredField("usr_paths")
    usrPathsField.isAccessible = true

    //get array of paths
    val paths = usrPathsField.get(null) as Array<String>

    //check if the path to add is already present
    for (path in paths) {
        if (path == "or-tools/lib") {
            add = false
        }
    }

    //add the new path
    if (add) {
        val newPaths = Arrays.copyOf(paths, paths.size + 1)
        newPaths[newPaths.size - 1] = "or-tools/lib"
        usrPathsField.set(null, newPaths)
    }

    System.loadLibrary("jniortools")

    System.err.println("Libraries loaded")

    connect()

    System.err.println("DB Connection Made")

    AuthData.defaultAuth = { MyAuth.getAuth() }
    ContractUpdateChecker.init()
}

const val AUTHED_CHARACTER_KEY = "authedCharacter"

fun Application.main() {

    initalize()

    //transaction{ kill.all().map{it.zKill}}.map { Group(it) }

    //TODO mutaplasmids

    val site = site {

        page("") {
            head {
                +"""<meta http-equiv="refresh" content="0; url=/${NavBar.pages.keys.firstOrNull() ?: "materials"}" />"""
            }
            body {}
        }

        addPage("materials", "Material Calculator", materialsPage, "type")

        addPage("ore", "Ore Optimizer", orePage, "raw")

        addPage("blueprints", "Blueprint Browser", bpBrowserPage, "type", "filter")

        addPage("blueprintfinder", "BPC Finder", bpFinderPage, "type", "filter")

        addPage("bpcoptimizer", "BPC Build Optimizer", bpOptimizerPage, "type", "mainfilter", "componentfilter", "facility")

        //addPage("fleeteye", "FleetEye", fleeteyePage, "region")
    }

    authorizePage(auth, site)

}

fun AnyElement.getAuthedCharacter(): AuthedCharacter = this.page.session[AUTHED_CHARACTER_KEY] as AuthedCharacter?
        ?: AuthedCharacter.UNAUTHED

fun WebsiteDef.addPage(pageUrl: String, pageName: String, builder: DocumentBuilder, vararg urlParams: String) {
    NavBar.addPage(pageUrl, pageName)
    page(pageUrl, *urlParams, builder = builder)
}

