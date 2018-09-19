package main

import com.github.salomonbrys.kotson.get
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.kizitonwose.time.days
import com.kizitonwose.time.milliseconds
import com.rnett.core.Cache
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import kotlinx.coroutines.experimental.runBlocking
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import pages.AuthedCharacter
import java.text.DecimalFormat
import java.util.*

val stationNameCache = Cache<Long, String>(1.days) {
    val client = HttpClient(Apache)
    runBlocking {
        try {
            val res = client.post<String>("https://esi.evetech.net/latest/universe/names/?datasource=tranquility") {
                headers.append("accept", "application/json")
                body = "[ $it ]"
            }

            JsonParser().parse(res).asJsonArray.first()["name"].asString

        } catch (e: Exception) {
            try {
                val structure = makeEsiCall("https://esi.evetech.net/latest/universe/structures/5/?datasource=tranquility")
                structure!!["name"].asString
            } catch (e: Exception) {
                "Unknown (possibly Citadel)"
            }

        }
    }
}

fun makeEsiCall(url: String): JsonElement? {
    val client = HttpClient(Apache)
    val parser = JsonParser()

    for (char in AuthedCharacter.all()) {
        char.refresh()
        char.storeWrittenValues()
        try {
            val resp = runBlocking {
                client.get<String>(url) {
                    header("Authorization", "Bearer " + char.accessToken)
                }
            }
            val json = parser.parse(resp)
            if (json.asJsonObject.has("error"))
                continue

            return json
        } catch (e: Exception) {
        }
    }
    return null
}

fun now() = Calendar.getInstance().timeInMillis.milliseconds

fun getStationName(id: Number) = stationNameCache[id.toLong()]


fun Number.format0s(zeros: Int = 2) = DecimalFormat("#,##0.${"0".repeat(zeros)}").format(this)
fun Number.format0ks(zeros: Int = 2) = when {
    this.toDouble() >= 1_000_000_000 -> (this.toDouble() / 1_000_000_000.00).format0s() + " B"
    this.toDouble() >= 1_000_000 -> (this.toDouble() / 1_000_000.00).format0s() + " M"
    this.toDouble() >= 1_000 -> (this.toDouble() / 1_000.00).format0s() + " K"
    else -> this.format0s()
}

fun Number.format(zeros: Int = 3) = DecimalFormat("#,###.${"#".repeat(zeros)}").format(this)

fun DateTime.formatUTC() = this.toDateTime(DateTimeZone.UTC).toString("yyyy.MM.dd HH:mm")
