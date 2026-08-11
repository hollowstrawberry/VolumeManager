package moe.chensi.volume.compose.popup

import androidx.compose.ui.graphics.Color

/**
 * Colors used exclusively by the redesigned system-overlay popup. Kept separate from the app's
 * Material theme (see [moe.chensi.volume.ui.theme.Theme]) so the main activity's look is
 * unaffected by this redesign.
 */
internal object PopupColors {
    /** Popup card background. */
    val Surface = Color(0xFF161618)

    /** Unfilled portion of a volume bar. */
    val Track = Color(0xFF2C2C2E)

    /** Icon color over the unfilled portion of a bar. */
    val OnTrack = Color(0xFFEDEDED)

    /** Filled portion of a volume bar. */
    val Accent = Color(0xFFD1D1D6)

    /** Icon color over the filled portion of a bar. */
    val OnAccent = Color(0xFF1C1C1E)

    /** Muted/zero-volume fill color. */
    val Muted = Color(0xFF48484A)
}
