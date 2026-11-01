package de.asdfghasdfgh.ie.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread

class HomeAssistantTest {

    @Test
    fun `an address without a scheme is taken as http`() {
        assertEquals(
            "http://homeassistant.local:8123",
            normalizeBaseUrl(" homeassistant.local:8123/ "),
        )
    }

    @Test
    fun `a path kept as part of the address stays`() {
        assertEquals("https://example.com/home-assistant", normalizeBaseUrl("https://example.com/home-assistant/"))
    }

    @Test
    fun `any http or https address is allowed`() {
        assertTrue(isValidBaseUrl("http://192.168.1.10:8123"))
        assertTrue(isValidBaseUrl("https://ha.example.com/anything"))
        assertTrue(isValidBaseUrl("home_assistant.local:8123"))
    }

    @Test
    fun `addresses that are not http or https are refused`() {
        assertFalse(isValidBaseUrl("ftp://example.com"))
        assertFalse(isValidBaseUrl("not an address"))
        assertFalse(isValidBaseUrl(""))
    }

    @Test
    fun `a server address is an IP or a host name`() {
        assertTrue(isValidServerAddress("192.168.1.10"))
        assertTrue(isValidServerAddress("::1"))
        assertFalse(isValidServerAddress("not an address"))
        assertFalse(isValidServerAddress(""))
    }

    @Test
    fun `a pinned server address is used while the URL host stays the same`() = runBlocking {
        val hits = Collections.synchronizedList(mutableListOf<Triple<String, String?, String?>>())
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                        var authorization: String? = null
                        var host: String? = null
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            when {
                                line.startsWith("Authorization:", ignoreCase = true) ->
                                    authorization = line.substringAfter(':').trim()
                                line.startsWith("Host:", ignoreCase = true) ->
                                    host = line.substringAfter(':').trim()
                            }
                        }
                        hits += Triple(path, authorization, host)

                        val body = if (path == "/api/states") {
                            """[{"entity_id":"light.kitchen","state":"on","attributes":{"friendly_name":"Küche"}}]"""
                        } else {
                            "API running."
                        }
                        val bytes = body.toByteArray()
                        val out = socket.getOutputStream()
                        out.write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: application/json\r\n" +
                                    "Content-Length: ${bytes.size}\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(),
                        )
                        out.write(bytes)
                        out.flush()
                    }
                } catch (_: Exception) {
                    if (server.isClosed) break
                }
            }
        }
        try {
            val port = server.localPort
            val client = HomeAssistant("http://home.invalid:$port", "secret", "127.0.0.1")
            client.ping()
            val devices = client.devices()

            assertEquals(1, devices.size)
            assertTrue(
                hits.any {
                    it.first == "/api/" &&
                        it.second == "Bearer secret" &&
                        it.third == "home.invalid:$port"
                },
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `only the supported domains are kept, with their attributes`() {
        val devices = parseDevices(
            """
            [
              {"entity_id":"light.kitchen","state":"on","attributes":{"friendly_name":"Küche"}},
              {"entity_id":"switch.washer","state":"off","attributes":{"friendly_name":"Waschmaschine"}},
              {"entity_id":"media_player.living","state":"playing","attributes":{"friendly_name":"Wohnzimmer","volume_level":0.35,"is_volume_muted":true}},
              {"entity_id":"sensor.temperature","state":"21.5","attributes":{"friendly_name":"Temperatur"}},
              {"entity_id":"fan.office","state":"unavailable","attributes":{}},
              {"entity_id":"input_boolean.guest","state":"on","attributes":{"friendly_name":"Gast"}}
            ]
            """.trimIndent(),
        )

        assertEquals(
            listOf("fan.office", "input_boolean.guest", "light.kitchen", "switch.washer", "media_player.living"),
            devices.map { it.entityId },
        )
        assertEquals(DeviceKind.SWITCH, devices.first { it.entityId == "fan.office" }.kind)
        assertFalse(devices.first { it.entityId == "fan.office" }.available)

        val media = devices.first { it.entityId == "media_player.living" }
        assertEquals(DeviceKind.MEDIA, media.kind)
        assertEquals(35, media.volumePercent)
        assertTrue(media.muted)
        assertEquals("media_player", media.domain)
    }

    @Test
    fun `a media player without volume has no percentage`() {
        val media = parseDevices(
            """[{"entity_id":"media_player.radio","state":"idle","attributes":{}}]""",
        ).single()
        assertNull(media.volume)
        assertNull(media.volumePercent)
        assertEquals("media_player.radio", media.name)
    }
}
