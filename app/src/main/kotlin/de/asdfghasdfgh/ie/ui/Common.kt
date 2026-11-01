package de.asdfghasdfgh.ie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import de.asdfghasdfgh.ie.R
import de.asdfghasdfgh.ie.data.Device
import de.asdfghasdfgh.ie.data.DeviceKind
import de.asdfghasdfgh.ie.data.Failure

/** Words in a top bar, with a 48 dp target. */
@Composable
fun BarText(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

/** A small heading over a group of rows. */
@Composable
fun Heading(text: String, modifier: Modifier = Modifier) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

/** One entity ticked in place: the row takes the press, the box says which way. */
@Composable
fun CheckRow(
    title: String,
    note: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckboxMMD(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
            if (note != null) TextMMD(text = note, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** A text field with its label over it, the way a form reads on paper. */
@Composable
fun Field(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    secret: Boolean = false,
    url: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        TextMMD(text = label, style = MaterialTheme.typography.labelSmall)
        TextFieldMMD(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            placeholder = { TextMMD(text = hint, style = MaterialTheme.typography.labelSmall) },
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    secret -> KeyboardType.Password
                    url -> KeyboardType.Uri
                    else -> KeyboardType.Text
                },
            ),
        )
    }
}

/** What a switch or light says it is doing. */
@Composable
fun deviceStateText(device: Device): String = when {
    !device.available -> stringResource(R.string.state_unavailable)
    device.kind == DeviceKind.MEDIA -> mediaStateText(device)
    device.isOn -> stringResource(R.string.state_on)
    else -> stringResource(R.string.state_off)
}

/** What a media player says it is doing. */
@Composable
fun mediaStateText(device: Device): String = when (device.state) {
    "playing" -> stringResource(R.string.media_playing)
    "paused" -> stringResource(R.string.media_paused)
    "idle" -> stringResource(R.string.media_idle)
    "off" -> stringResource(R.string.media_off)
    "standby" -> stringResource(R.string.media_standby)
    "on" -> stringResource(R.string.state_on)
    "unavailable", "unknown" -> stringResource(R.string.state_unavailable)
    else -> device.state
}

/** The sentence under a media player: what it is doing, how loud, whether it is muted. */
@Composable
fun mediaDetail(device: Device): String {
    val volume = device.volumePercent?.let { stringResource(R.string.media_volume, it) }
        ?: stringResource(R.string.media_volume_unknown)
    val muted = if (device.muted) stringResource(R.string.media_muted) else null
    return listOfNotNull(mediaStateText(device), volume, muted).joinToString(" · ")
}

/** One sentence for a failed call, in words that say what to do about it. */
@Composable
fun failureText(failure: Failure): String = when (failure) {
    Failure.Unreachable -> stringResource(R.string.error_unreachable)
    Failure.Unauthorized -> stringResource(R.string.error_unauthorized)
    Failure.NotHomeAssistant -> stringResource(R.string.error_not_home_assistant)
    is Failure.Http -> stringResource(R.string.error_http, failure.code)
    Failure.InvalidAddress -> stringResource(R.string.error_invalid_address)
    Failure.InvalidServerAddress -> stringResource(R.string.error_invalid_server_address)
    Failure.MissingToken -> stringResource(R.string.error_missing_token)
    Failure.Unexpected -> stringResource(R.string.error_unexpected)
}
