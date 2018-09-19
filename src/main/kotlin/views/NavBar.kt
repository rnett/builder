package views

import com.rnett.kframe.dom.a
import com.rnett.kframe.dom.classes.AnyDisplayElementBuilder
import com.rnett.kframe.dom.classes.DisplayElement
import com.rnett.kframe.dom.img
import com.rnett.kframe.dom.li
import com.rnett.kframe.dom.onClick
import com.rnett.kframe.element.View
import com.rnett.kframe.element.px
import com.rnett.kframe.materalize.*
import pages.AuthData
import pages.isCharLogedIn
import pages.logout

class NavBar : View<DisplayElement<*>> {
    override fun makeElements(): AnyDisplayElementBuilder = {
        navbar("grey darken-1") {
            leftNavList("hide-on-med-and-down") {

                pages.forEach { (url, title) ->
                    li {
                        a("/$url") { +title }

                        if (page.pageURL == url)
                            classes.add("active")
                    }
                }
            }

            if (!isCharLogedIn) {
                btn(href = AuthData.auth.loginURL(url)).right()() {
                    style.width = 178.px
                    style.height = 30.px
                    style.padding = 0.px

                    style.marginTop = 16.px
                    style.marginRight = 20.px

                    img("/css/EVE_SSO_Login_Buttons_Large_Black.png", klass = "responsive-img")
                }
            } else {
                btn().right().color("grey", -3)() {
                    style.width = 178.px
                    style.height = 30.px

                    style.marginTop = 20.px
                    style.marginRight = 20.px

                    +"Log Out"
                    onClick {
                        logout()
                        ""
                    }
                }
            }
        }
    }

    companion object {
        val pages: MutableMap<String, String> = mutableMapOf()
        fun addPage(url: String, title: String) = pages.put(url, title)
        fun addPages(vararg pageTitles: Pair<String, String>) = pages.putAll(pageTitles)
    }

}


fun DisplayElement<*>.navbar() = NavBar()