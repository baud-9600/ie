package de.asdfghasdfgh.ie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD

/** The shortest a house button is, tall and wide: the 48 dp touch rule. */
internal val HOUSE_MIN = 48.dp

/** The gap between buttons side by side. */
internal val HOUSE_GAP = 8.dp

/**
 * One press in the shop's own button: filled black with white words when [on], white with a
 * black rim when not. At least 48 dp tall and wide whatever the words.
 */
@Composable
fun HouseButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    note: String? = null,
    maxLines: Int = 1,
) {
    val m = modifier.heightIn(min = HOUSE_MIN).widthIn(min = HOUSE_MIN)
    val pad = PaddingValues(horizontal = if (compact) 6.dp else 14.dp, vertical = 8.dp)
    val words: @Composable RowScope.() -> Unit = {
        if (note == null) {
            TextMMD(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = maxLines,
                textAlign = TextAlign.Center,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextMMD(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = maxLines,
                    textAlign = TextAlign.Center,
                )
                TextMMD(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    if (on) {
        ButtonMMD(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            contentPadding = pad,
            content = words,
        )
    } else {
        OutlinedButtonMMD(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            contentPadding = pad,
            content = words,
        )
    }
}
