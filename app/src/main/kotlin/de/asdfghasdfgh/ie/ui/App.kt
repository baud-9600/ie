package de.asdfghasdfgh.ie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import de.asdfghasdfgh.ie.R
import de.asdfghasdfgh.ie.data.Device
import de.asdfghasdfgh.ie.data.DeviceKind
import de.asdfghasdfgh.ie.data.Failure
import de.asdfghasdfgh.ie.data.MediaCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ZuhauseApp(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(state.connection == null) }
    var selecting by rememberSaveable { mutableStateOf(false) }

    // Home Assistant is asked again while the screen is on; only a change in the answers
    // reaches the panel.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.connection) {
        if (state.connection == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var first = true
            while (isActive) {
                viewModel.refresh(silent = !first)
                first = false
                delay(POLL_MS)
            }
        }
    }

    BackHandler(enabled = settings || selecting) {
        when {
            selecting -> selecting = false
            else -> settings = false
        }
    }

    when {
        selecting -> SelectionScreen(
            state = state,
            onBack = { selecting = false },
            onSave = { ids ->
                viewModel.saveSelection(ids)
                selecting = false
                settings = false
            },
            onRefresh = { viewModel.refresh() },
        )
        settings -> SettingsScreen(
            connection = state.connection,
            onBack = { settings = false },
            onConnect = { address, token, serverAddress ->
                viewModel.connect(address, token, serverAddress)
            },
            onSelectDevices = { selecting = true },
            selectedCount = state.visibleDevices.size,
            totalCount = state.devices.size,
            onDisconnect = { viewModel.disconnect() },
        )
        else -> HomeScreen(
            state = state,
            onSettings = { settings = true },
            onSelectDevices = { selecting = true },
            onRefresh = { viewModel.refresh() },
            onToggle = viewModel::toggle,
            onMedia = viewModel::media,
        )
    }
}

/** How often the states are read again while the app is on screen. */
private const val POLL_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: HomeUiState,
    onSettings: () -> Unit,
    onSelectDevices: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (Device) -> Unit,
    onMedia: (Device, MediaCommand) -> Unit,
) {
    val visible = state.visibleDevices
    val switches = visible.filter { it.kind != DeviceKind.MEDIA }
    val media = visible.filter { it.kind == DeviceKind.MEDIA }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.app_name)) },
                actions = { BarText(stringResource(R.string.settings_title), onSettings) },
            )
        },
    ) { padding ->
        LazyColumnMMD(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            isScrollbarVisible = false,
        ) {
            if (state.connection == null) {
                item {
                    Heading(stringResource(R.string.not_connected_title))
                    TextMMD(text = stringResource(R.string.not_connected_body), style = MaterialTheme.typography.bodyMedium)
                    HouseButton(
                        label = stringResource(R.string.connect),
                        onClick = onSettings,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        on = true,
                    )
                }
                return@LazyColumnMMD
            }

            if (state.loading && state.devices.isEmpty()) {
                item {
                    TextMMD(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            state.failure?.let { failure ->
                item(key = "failure") {
                    FailureBlock(failure, onRefresh)
                }
            }

            if (switches.isNotEmpty()) {
                item(key = "devices-heading") { Heading(stringResource(R.string.devices_heading)) }
                items(switches, key = { it.entityId }) { device ->
                    SwitchRow(device, onToggle)
                }
            }

            if (media.isNotEmpty()) {
                item(key = "media-heading") { Heading(stringResource(R.string.media_heading)) }
                items(media, key = { it.entityId }) { device ->
                    MediaRow(device, onMedia)
                }
            }

            if (!state.loading && state.failure == null && state.devices.isEmpty()) {
                item(key = "empty") {
                    TextMMD(
                        text = stringResource(R.string.empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else if (!state.loading && state.failure == null && state.hasSelection && visible.isEmpty()) {
                item(key = "nothing-chosen") {
                    Column(Modifier.padding(vertical = 18.dp)) {
                        TextMMD(
                            text = stringResource(R.string.no_devices_selected),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        HouseButton(
                            label = stringResource(R.string.select_devices_title),
                            onClick = onSelectDevices,
                            modifier = Modifier.padding(top = 10.dp),
                            compact = true,
                        )
                    }
                }
            }

            state.updatedAt?.let { updatedAt ->
                item(key = "updated") {
                    val time = remember(updatedAt) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(updatedAt))
                    }
                    TextMMD(
                        text = stringResource(R.string.last_updated, time),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 14.dp, bottom = 14.dp),
                    )
                }
            }

            item(key = "foot") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun FailureBlock(failure: Failure, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        TextMMD(text = failureText(failure), style = MaterialTheme.typography.bodyMedium)
        HouseButton(
            label = stringResource(R.string.retry),
            onClick = onRetry,
            modifier = Modifier.padding(top = 8.dp),
            compact = true,
        )
    }
}

/** A switch, light, fan or helper: the whole row is the press, the switch says which way. */
@Composable
private fun SwitchRow(device: Device, onToggle: (Device) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = device.available) { onToggle(device) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextMMD(text = deviceStateText(device), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(12.dp))
        SwitchMMD(
            checked = device.isOn,
            onCheckedChange = null,
            enabled = device.available,
        )
    }
    HorizontalDividerMMD()
}

/** A media player: what it is doing, then the five presses this app offers. */
@Composable
private fun MediaRow(device: Device, onMedia: (Device, MediaCommand) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        TextMMD(
            text = device.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextMMD(text = mediaDetail(device), style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(HOUSE_GAP)) {
            HouseButton(
                label = stringResource(R.string.quieter),
                onClick = { onMedia(device, MediaCommand.QUIETER) },
                modifier = Modifier.weight(1f),
                compact = true,
                enabled = device.available,
            )
            HouseButton(
                label = stringResource(R.string.louder),
                onClick = { onMedia(device, MediaCommand.LOUDER) },
                modifier = Modifier.weight(1f),
                compact = true,
                enabled = device.available,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(HOUSE_GAP)) {
            HouseButton(
                label = stringResource(R.string.play),
                onClick = { onMedia(device, MediaCommand.PLAY) },
                modifier = Modifier.weight(1f),
                on = device.state == "playing",
                compact = true,
                enabled = device.available,
            )
            HouseButton(
                label = stringResource(R.string.pause),
                onClick = { onMedia(device, MediaCommand.PAUSE) },
                modifier = Modifier.weight(1f),
                on = device.state == "paused",
                compact = true,
                enabled = device.available,
            )
            HouseButton(
                label = stringResource(R.string.stop),
                onClick = { onMedia(device, MediaCommand.STOP) },
                modifier = Modifier.weight(1f),
                compact = true,
                enabled = device.available,
            )
        }
    }
    HorizontalDividerMMD()
}
