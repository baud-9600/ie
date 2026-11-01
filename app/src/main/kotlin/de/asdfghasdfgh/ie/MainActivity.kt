package de.asdfghasdfgh.ie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import com.mudita.mmd.ThemeMMD
import de.asdfghasdfgh.ie.ui.ZuhauseApp
import de.asdfghasdfgh.ie.ui.monochrome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // A steady cursor: a blinking one is a repaint twice a second on e-ink for as
            // long as a field has focus.
            CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                ThemeMMD(colorScheme = monochrome) {
                    ZuhauseApp()
                }
            }
        }
    }
}
