package de.asdfghasdfgh.ie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import de.asdfghasdfgh.ie.R
import de.asdfghasdfgh.ie.data.DeviceKind

/**
 * Which of Home Assistant's switches, lights and players the one screen shows. Unticking an
 * entity here only hides it; its state and every other entity stay untouched in Home Assistant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onRefresh: () -> Unit,
) {
    var chosen by remember { mutableStateOf<Set<String>?>(null) }

    // Seed the ticks from what is shown now, once the first list has arrived. A later refresh
    // must not undo what has been ticked in the meantime.
    LaunchedEffect(state.devices, state.hasSelection, state.selectedIds) {
        if (chosen == null && state.devices.isNotEmpty()) {
            chosen = if (state.hasSelection) {
                state.selectedIds
            } else {
                state.devices.map { it.entityId }.toSet()
            }
        }
    }
    LaunchedEffect(Unit) {
        if (state.devices.isEmpty()) onRefresh()
    }

    val switches = state.devices.filter { it.kind != DeviceKind.MEDIA }
    val media = state.devices.filter { it.kind == DeviceKind.MEDIA }
    val allIds = state.devices.map { it.entityId }.toSet()
    val save = chosen

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.select_devices_title)) },
                navigationIcon = { BarText(stringResource(R.string.back), onBack) },
                actions = {
                    if (save != null) {
                        BarText(stringResource(R.string.save)) { onSave(save) }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumnMMD(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            isScrollbarVisible = false,
        ) {
            item(key = "help") {
                TextMMD(
                    text = stringResource(R.string.selection_help),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            state.failure?.let { failure ->
                item(key = "failure") {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        TextMMD(text = failureText(failure), style = MaterialTheme.typography.bodyMedium)
                        HouseButton(
                            label = stringResource(R.string.retry),
                            onClick = onRefresh,
                            modifier = Modifier.padding(top = 8.dp),
                            compact = true,
                        )
                    }
                }
            }

            if (save == null) {
                item(key = "waiting") {
                    TextMMD(
                        text = stringResource(if (state.loading) R.string.loading_devices else R.string.empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                return@LazyColumnMMD
            }

            item(key = "all-or-none") {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(HOUSE_GAP),
                ) {
                    HouseButton(
                        label = stringResource(R.string.select_all),
                        onClick = { chosen = allIds },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                    HouseButton(
                        label = stringResource(R.string.select_none),
                        onClick = { chosen = emptySet() },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                }
            }

            if (switches.isNotEmpty()) {
                item(key = "devices-heading") { Heading(stringResource(R.string.devices_heading)) }
                items(switches, key = { "d-${it.entityId}" }) { device ->
                    CheckRow(
                        title = device.name,
                        note = device.entityId,
                        checked = device.entityId in save,
                        onChange = { on ->
                            chosen = chosen.orEmpty().let {
                                if (on) it + device.entityId else it - device.entityId
                            }
                        },
                    )
                    HorizontalDividerMMD()
                }
            }

            if (media.isNotEmpty()) {
                item(key = "media-heading") { Heading(stringResource(R.string.media_heading)) }
                items(media, key = { "m-${it.entityId}" }) { device ->
                    CheckRow(
                        title = device.name,
                        note = device.entityId,
                        checked = device.entityId in save,
                        onChange = { on ->
                            chosen = chosen.orEmpty().let {
                                if (on) it + device.entityId else it - device.entityId
                            }
                        },
                    )
                    HorizontalDividerMMD()
                }
            }

            item(key = "foot") { Spacer(Modifier.height(24.dp)) }
        }
    }
}
