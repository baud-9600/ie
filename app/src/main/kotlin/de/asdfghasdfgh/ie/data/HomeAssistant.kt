package de.asdfghasdfgh.ie.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** What this app knows how to draw and press. */
enum class DeviceKind { SWITCH, LIGHT, MEDIA }

/** One entity from Home Assistant, reduced to what the one screen needs. */
data class Device(
    val entityId: String,
    val name: String,
    val kind: DeviceKind,
    val state: String,
    val volume: Float? = null,
    val muted: Boolean = false,
) {
    val domain: String get() = entityId.substringBefore('.')

    val available: Boolean get() = state != "unavailable" && state != "unknown"

    /** A switch, light or fan that is on. Media players report their own states. */
    val isOn: Boolean get() = state == "on"

    val volumePercent: Int? get() = volume?.let { (it * 100).roundToInt().coerceIn(0, 100) }
}

/** The five presses a media player gets here. */
enum class MediaCommand(val service: String) {
    PLAY("media_play"),
    PAUSE("media_pause"),
    STOP("media_stop"),
    QUIETER("volume_down"),
    LOUDER("volume_up"),
}

/** Enough to say what went wrong without putting German words in the network layer. */
sealed interface Failure {
    data object Unreachable : Failure
    data object Unauthorized : Failure
    data object NotHomeAssistant : Failure
    data class Http(val code: Int) : Failure
    data object InvalidAddress : Failure
    data object InvalidServerAddress : Failure
    data object MissingToken : Failure
    data object Unexpected : Failure
}

class HaException(val failure: Failure) : Exception()

/** `homeassistant.local:8123` becomes `http://homeassistant.local:8123`. */
fun normalizeBaseUrl(address: String): String {
    var value = address.trim()
    if (value.isEmpty()) return value
    if (!value.contains("://")) value = "http://$value"
    return value.trimEnd('/')
}

fun isValidBaseUrl(address: String): Boolean {
    val value = normalizeBaseUrl(address)
    return try {
        val uri = URI(value)
        (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.rawAuthority.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

/**
 * An optional address to connect to instead of what the phone's DNS says for the name above.
 * The name is still the one in the URL, so its certificate is still checked: only the socket
 * is sent somewhere else. This is for a name that resolves differently inside and outside the
 * house, or for a Home Assistant reached by a local address while its public tunnel is down.
 */
fun isValidServerAddress(address: String): Boolean {
    val value = address.trim()
    if (value.isEmpty()) return false
    return runCatching {
        InetAddress.getByName(value)
        true
    }.getOrDefault(false)
}

/** The handful of REST calls this app makes. */
class HomeAssistant(
    baseUrl: String,
    private val token: String,
    serverAddress: String = "",
) {

    private val baseUrl = normalizeBaseUrl(baseUrl)
    private val targetHost = runCatching { URI(this.baseUrl).host }.getOrNull()
    private val pinnedAddress = serverAddress.trim()
        .takeIf { it.isNotEmpty() }
        ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .apply {
            if (pinnedAddress != null) {
                dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        if (hostname.equals(targetHost, ignoreCase = true)) {
                            listOf(pinnedAddress)
                        } else {
                            Dns.SYSTEM.lookup(hostname)
                        }
                })
            }
        }
        .build()

    /** Asks Home Assistant who it is, before the address and token are kept. */
    suspend fun ping() {
        request("api/")
    }

    suspend fun devices(): List<Device> = parseDevices(request("api/states"))

    suspend fun callService(domain: String, service: String, entityId: String) {
        val body = JSONObject().put("entity_id", entityId).toString()
        request("api/services/$domain/$service", body)
    }

    private suspend fun request(path: String, body: String? = null): String = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) throw HaException(Failure.InvalidAddress)
        val builder = try {
            Request.Builder()
                .url("$baseUrl/$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
        } catch (_: IllegalArgumentException) {
            throw HaException(Failure.InvalidAddress)
        }
        if (body == null) {
            builder.get()
        } else {
            builder.post(body.toRequestBody(JSON))
        }

        try {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HaException(
                        when (response.code) {
                            401, 403 -> Failure.Unauthorized
                            404 -> Failure.NotHomeAssistant
                            else -> Failure.Http(response.code)
                        },
                    )
                }
                response.body?.string().orEmpty()
            }
        } catch (e: HaException) {
            throw e
        } catch (_: IOException) {
            throw HaException(Failure.Unreachable)
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/**
 * The states Home Assistant publishes, keeping the four kinds of thing this app can use and
 * leaving the rest (sensors, scripts, automations, the phone it runs on) out of the list.
 */
fun parseDevices(json: String): List<Device> {
    val array = JSONArray(json)
    val devices = ArrayList<Device>(array.length())
    for (i in 0 until array.length()) {
        val entity = array.optJSONObject(i) ?: continue
        val id = entity.optString("entity_id")
        if (id.isBlank() || '.' !in id) continue

        val domain = id.substringBefore('.')
        val kind = when (domain) {
            "light" -> DeviceKind.LIGHT
            "switch", "input_boolean", "fan" -> DeviceKind.SWITCH
            "media_player" -> DeviceKind.MEDIA
            else -> continue
        }

        val attributes = entity.optJSONObject("attributes")
        val name = attributes?.optString("friendly_name")?.takeIf { it.isNotBlank() } ?: id
        val state = entity.optString("state", "unknown")
        val volume = if (kind == DeviceKind.MEDIA) {
            attributes?.optDouble("volume_level", Double.NaN)?.takeUnless { it.isNaN() }?.toFloat()
        } else {
            null
        }
        val muted = attributes?.optBoolean("is_volume_muted", false) ?: false

        devices += Device(
            entityId = id,
            name = name,
            kind = kind,
            state = state,
            volume = volume,
            muted = muted,
        )
    }
    return devices.sortedBy { it.name.lowercase(Locale.GERMAN) }
}
