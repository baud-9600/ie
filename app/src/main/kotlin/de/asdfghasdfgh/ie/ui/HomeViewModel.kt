package de.asdfghasdfgh.ie.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.asdfghasdfgh.ie.data.Connection
import de.asdfghasdfgh.ie.data.Device
import de.asdfghasdfgh.ie.data.Failure
import de.asdfghasdfgh.ie.data.HaException
import de.asdfghasdfgh.ie.data.HomeAssistant
import de.asdfghasdfgh.ie.data.MediaCommand
import de.asdfghasdfgh.ie.data.Prefs
import de.asdfghasdfgh.ie.data.isValidBaseUrl
import de.asdfghasdfgh.ie.data.isValidServerAddress
import de.asdfghasdfgh.ie.data.normalizeBaseUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class HomeUiState(
    val connection: Connection? = null,
    val devices: List<Device> = emptyList(),
    val loading: Boolean = false,
    val failure: Failure? = null,
    val updatedAt: Long? = null,
    /** The chosen entity ids; [hasSelection] is false while every supported one is shown. */
    val selectedIds: Set<String> = emptySet(),
    val hasSelection: Boolean = false,
) {
    /** What the one screen shows: all supported devices, or only those chosen. */
    val visibleDevices: List<Device>
        get() = if (hasSelection) devices.filter { it.entityId in selectedIds } else devices
}

/** One screen's worth of state, and the handful of calls that change it. */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val start = Prefs.load(application).takeIf { it.usable }
    private val savedSelection = Prefs.selection(application)
    private val _state = MutableStateFlow(
        HomeUiState(
            connection = start,
            selectedIds = savedSelection.orEmpty(),
            hasSelection = savedSelection != null,
        ),
    )
    val state = _state.asStateFlow()

    /** One request at a time: a poll and a press must not race each other. */
    private val gate = Mutex()

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch { fetch(showSpinner = !silent) }
    }

    /**
     * Checks the address and token against Home Assistant, and keeps them only if it answers.
     * Returns the failure to show in the form, or null when the connection is good.
     */
    suspend fun connect(address: String, token: String, serverAddress: String): Failure? {
        val baseUrl = normalizeBaseUrl(address)
        if (!isValidBaseUrl(baseUrl)) return Failure.InvalidAddress
        val secret = token.trim()
        if (secret.isEmpty()) return Failure.MissingToken
        val pin = serverAddress.trim()
        if (pin.isNotEmpty() && !isValidServerAddress(pin)) return Failure.InvalidServerAddress

        return try {
            HomeAssistant(baseUrl, secret, pin).ping()
            val connection = Connection(baseUrl, secret, pin)
            Prefs.save(getApplication(), connection)
            _state.update {
                it.copy(
                    connection = connection,
                    devices = emptyList(),
                    loading = true,
                    failure = null,
                    updatedAt = null,
                )
            }
            fetch(showSpinner = false)
            null
        } catch (e: Exception) {
            failureOf(e)
        }
    }

    fun disconnect() {
        Prefs.clearConnection(getApplication())
        // The choice of entities is kept: signing in again to the same house restores it.
        _state.update {
            it.copy(
                connection = null,
                devices = emptyList(),
                loading = false,
                failure = null,
                updatedAt = null,
            )
        }
    }

    /** Keeps the chosen entities and shows only those on the one screen from now on. */
    fun saveSelection(entityIds: Set<String>) {
        Prefs.saveSelection(getApplication(), entityIds)
        _state.update { it.copy(selectedIds = entityIds, hasSelection = true) }
    }

    /** A switch, light, fan or input_boolean: immediately the other way, then told to be. */
    fun toggle(device: Device) {
        val connection = _state.value.connection ?: return
        val before = device.state
        val after = if (before == "on") "off" else "on"
        replaceState(device.entityId, after)

        viewModelScope.launch {
            try {
                HomeAssistant(connection.baseUrl, connection.token, connection.serverAddress)
                    .callService(device.domain, "toggle", device.entityId)
                delay(450)
                fetch(showSpinner = false)
            } catch (e: Exception) {
                replaceState(device.entityId, before)
                _state.update { it.copy(failure = failureOf(e)) }
            }
        }
    }

    fun media(device: Device, command: MediaCommand) {
        val connection = _state.value.connection ?: return
        val before = device.state
        // Play, pause and stop can be shown at once; volume is left to Home Assistant, which
        // knows the step its player takes.
        val optimistic = when (command) {
            MediaCommand.PLAY -> "playing"
            MediaCommand.PAUSE -> "paused"
            MediaCommand.STOP -> "idle"
            MediaCommand.QUIETER, MediaCommand.LOUDER -> null
        }
        if (optimistic != null) replaceState(device.entityId, optimistic)

        viewModelScope.launch {
            try {
                HomeAssistant(connection.baseUrl, connection.token, connection.serverAddress)
                    .callService("media_player", command.service, device.entityId)
                delay(400)
                fetch(showSpinner = false)
            } catch (e: Exception) {
                if (optimistic != null) replaceState(device.entityId, before)
                _state.update { it.copy(failure = failureOf(e)) }
            }
        }
    }

    private suspend fun fetch(showSpinner: Boolean) {
        val connection = _state.value.connection ?: return
        gate.withLock {
            if (showSpinner) _state.update { it.copy(loading = true) }
            try {
                val devices = HomeAssistant(connection.baseUrl, connection.token, connection.serverAddress).devices()
                _state.update {
                    it.copy(
                        devices = devices,
                        loading = false,
                        failure = null,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, failure = failureOf(e)) }
            }
        }
    }

    private fun replaceState(entityId: String, state: String) {
        _state.update { current ->
            current.copy(
                devices = current.devices.map {
                    if (it.entityId == entityId) it.copy(state = state) else it
                },
            )
        }
    }

    private fun failureOf(e: Throwable): Failure = when (e) {
        is HaException -> e.failure
        is IllegalArgumentException -> Failure.InvalidAddress
        is IOException -> Failure.Unreachable
        else -> Failure.Unexpected
    }
}
