package de.asdfghasdfgh.ie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import de.asdfghasdfgh.ie.R
import de.asdfghasdfgh.ie.data.Connection
import de.asdfghasdfgh.ie.data.Failure
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one form: where Home Assistant lives and the token that lets this phone in. The
 * connection is checked before it is kept, so a wrong token is said here rather than on the
 * list afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    connection: Connection?,
    onBack: () -> Unit,
    onConnect: suspend (address: String, token: String, serverAddress: String) -> Failure?,
    onSelectDevices: () -> Unit,
    selectedCount: Int,
    totalCount: Int,
    onDisconnect: () -> Unit,
) {
    var address by remember(connection) { mutableStateOf(connection?.baseUrl.orEmpty()) }
    var token by remember(connection) { mutableStateOf(connection?.token.orEmpty()) }
    var serverAddress by remember(connection) { mutableStateOf(connection?.serverAddress.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Failure?>(null) }
    var armed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(armed) {
        if (!armed) return@LaunchedEffect
        delay(6_000)
        armed = false
    }

    fun connect() {
        if (saving) return
        saving = true
        failure = null
        scope.launch {
            val result = onConnect(address, token, serverAddress)
            saving = false
            if (result == null) onBack() else failure = result
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.settings_title)) },
                navigationIcon = { BarText(stringResource(R.string.back), onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Heading(stringResource(R.string.setup_heading))
            TextMMD(
                text = stringResource(R.string.setup_help),
                style = MaterialTheme.typography.bodyMedium,
            )

            Field(
                label = stringResource(R.string.address_label),
                value = address,
                onValue = { address = it },
                hint = stringResource(R.string.address_hint),
                url = true,
            )
            Field(
                label = stringResource(R.string.server_address_label),
                value = serverAddress,
                onValue = { serverAddress = it },
                hint = stringResource(R.string.server_address_hint),
            )
            TextMMD(
                text = stringResource(R.string.server_address_help),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            Field(
                label = stringResource(R.string.token_label),
                value = token,
                onValue = { token = it },
                hint = stringResource(R.string.token_hint),
                secret = true,
            )
            TextMMD(
                text = stringResource(R.string.token_help),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            failure?.let {
                TextMMD(
                    text = failureText(it),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            HouseButton(
                label = stringResource(if (saving) R.string.connecting else R.string.connect),
                onClick = ::connect,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                on = true,
                enabled = !saving,
            )

            if (connection != null) {
                HouseButton(
                    label = stringResource(R.string.select_devices_title),
                    note = if (totalCount > 0) {
                        stringResource(R.string.selected_count, selectedCount, totalCount)
                    } else {
                        null
                    },
                    onClick = onSelectDevices,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )

                HouseButton(
                    label = stringResource(if (armed) R.string.disconnect_armed else R.string.disconnect),
                    onClick = {
                        if (armed) {
                            armed = false
                            onDisconnect()
                        } else {
                            armed = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    on = armed,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
