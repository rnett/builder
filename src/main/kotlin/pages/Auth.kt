package pages

import com.github.salomonbrys.kotson.get
import com.google.gson.JsonParser
import com.kizitonwose.time.milliseconds
import com.kizitonwose.time.minutes
import com.rnett.kframe.element.AnyElement
import com.rnett.kframe.element.Pages
import com.rnett.kframe.hosts.web.Website
import com.rnett.kframe.hosts.web.getCurrentPages
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import kotlinx.coroutines.experimental.*
import main.AUTHED_CHARACTER_KEY
import main.now
import org.apache.commons.codec.binary.Base64
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.transactions.transaction
import pages.AuthData.Companion.auth
import java.net.URLEncoder

class AuthData(val clientId: String, clientSecret: String, val callbackUrl: String, val genState: () -> String, val scopes: List<String>) {
    fun loginURL(endURL: String) = "https://login.eveonline.com/oauth/authorize/?&response_type=code&redirect_uri=$callbackUrl%3FendUrl%3D$endURL" +
            "&client_id=$clientId&scope=${URLEncoder.encode(scopes.joinToString(" ") { it }, "UTF-8")}&state=${genState()}"

    val basicAuthHeader = String(Base64.encodeBase64("$clientId:$clientSecret".toByteArray()))

    companion object {
        lateinit var defaultAuth: () -> AuthData
        val auth get() = defaultAuth()
    }

}

fun Application.authorizePage(auth: AuthData, site: Website) {
    routing {
        get("/auth") {
            // auth code (1st callback)
            if (call.request.queryParameters.contains("code")) {
                val authCode = call.request.queryParameters["code"]

                val raw = HttpClient(Apache).post<String>("https://login.eveonline.com/oauth/token?grant_type=authorization_code&code=$authCode") {
                    body = "grant_type=authorization_code&code=$authCode"
                    header("User-Agent", "Ligraph/jnett96@gmail.com")
                    header("Authorization", "Basic ${auth.basicAuthHeader}")
                }

                var response = JsonParser().parse(raw)

                val refreshToken = response["refresh_token"].asString
                val accessToken = response["access_token"].asString

                response = JsonParser().parse(HttpClient(Apache).get<String>("https://esi.tech.ccp.is/verify/") {
                    header("User-Agent", "Ligraph/jnett96@gmail.com")
                    header("Authorization", "Bearer $accessToken")
                })

                val characterID = response["CharacterID"].asInt

                getCurrentPages(site).session[AUTHED_CHARACTER_KEY] = AuthedCharacter.new(characterID, refreshToken)

                val endUrl = call.request.queryParameters["endUrl"] ?: "/"

                call.respondText("""
                    <html>
                        <head>
                            <meta http-equiv="refresh" content="0; url=$endUrl" />
                        </head>
                        <body></body>
                    </html>
                """.trimIndent(), ContentType.Text.Html)

            }
        }
    }
}

fun logout(pages: Pages) = pages.session.remove(AUTHED_CHARACTER_KEY)
fun AnyElement.logout() {
    if (page.pages != null) {
        logout(page.pages)
        val redirStr = "<meta http-equiv=\"refresh\" content=\"0; url=/${page.pageURL}\" />"
        this.page.document.headElement.apply {
            +redirStr
        }
    }
}

fun isCharLogedIn(pages: Pages): Boolean {
    return if (pages.session.containsKey(AUTHED_CHARACTER_KEY)) {
        try {
            if (pages.session[AUTHED_CHARACTER_KEY] != null) {
                val authed = pages.session[AUTHED_CHARACTER_KEY] as AuthedCharacter
                authed.isAuthed
            } else false
        } catch (e: Exception) {
            false
        }
    } else
        false
}

val AnyElement.isCharLogedIn
    get() =
        if (page.pages != null)
            isCharLogedIn(page.pages)
        else
            false

fun logedInCharacter(pages: Pages): AuthedCharacter {
    return if (pages.session.containsKey(AUTHED_CHARACTER_KEY)) {
        try {
            if (pages.session[AUTHED_CHARACTER_KEY] != null) {
                pages.session[AUTHED_CHARACTER_KEY] as AuthedCharacter
            } else null
        } catch (e: Exception) {
            null
        } ?: AuthedCharacter.UNAUTHED
    } else
        AuthedCharacter.UNAUTHED
}

val AnyElement.logedInCharacter
    get() =
        if (page.pages != null)
            logedInCharacter(page.pages)
        else
            AuthedCharacter.UNAUTHED


class AuthedCharacter(id: EntityID<Int>) : IntEntity(id) {

    companion object : IntEntityClass<AuthedCharacter>(charactertokens) {
        fun new(characterId: Int, refreshToken: String): AuthedCharacter {
            return transaction {
                if (findById(characterId) == null) {
                    new(characterId) {
                        this.refreshToken = refreshToken
                        refresh()
                    }
                } else {
                    val tok = findById(characterId)!!

                    tok.refreshToken = refreshToken
                    tok.refresh()

                    tok
                }
            }
        }

        val UNAUTHED by lazy { transaction { AuthedCharacter.new(0, "") } }

    }

    // DB columns

    val characterId by charactertokens.characterId
    var refreshToken by charactertokens.refreshToken
    var accessToken by charactertokens.accessToken
    var updated by charactertokens.updated

    val expires get() = (updated?.milliseconds ?: (-20).minutes) + 20.minutes

    val expired get() = expires < now()


    val isAuthed get() = characterId != 0 && !refreshToken.isBlank()

    private var refreshJob: Job? = null

    fun getRefreshJob() = launch {
        genAccessToken()
        delay(18.minutes.inMilliseconds.longValue)
    }

    val refreshing = refreshJob != null

    fun startRefresh() {
        if (refreshing)
            return

        refreshJob = getRefreshJob()
    }

    fun stopRefreshing() {
        if (!refreshing)
            return

        runBlocking { refreshJob?.cancelAndJoin() }
        refreshJob = null
    }

    fun refresh(force: Boolean = false) = if (force || expired) runBlocking { genAccessToken() } else accessToken

    suspend fun genAccessToken(): String {
        val raw = HttpClient(Apache).post<String>("https://login.eveonline.com/oauth/token?grant_type=refresh_token&refresh_token=$refreshToken") {
            body = "grant_type=refresh_token&refresh_token=$refreshToken"

            header("User-Agent", "Ligraph/jnett96@gmail.com")
            header("Authorization", "Basic ${auth.basicAuthHeader}")
        }

        val response = JsonParser().parse(raw)

        refreshToken = response["refresh_token"].asString
        accessToken = response["access_token"].asString
        updated = (now() - 2.minutes).inMilliseconds.longValue
        return response["access_token"].asString
    }
}

object charactertokens : IntIdTable(columnName = "characterid") {

    val characterId = integer("characterid").primaryKey()
    val refreshToken = varchar("refreshtoken", 200)
    val accessToken = varchar("accesstoken", 200).nullable()
    val updated = long("updated").nullable()
}



