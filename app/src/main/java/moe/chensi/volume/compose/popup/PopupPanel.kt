package moe.chensi.volume.compose.popup

import android.media.AudioManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.chensi.volume.VirtualVolumeLevel
import moe.chensi.volume.data.App

/**
 * The redesigned system-overlay popup: a dark, left-anchored card containing a row of vertical,
 * icon-only volume bars (system streams followed by active apps). No text is shown anywhere in
 * the popup.
 */
@Composable
fun PopupPanel(
    audioManager: AudioManager,
    activeApps: List<App>,
    isSystemSliderVisible: (String) -> Boolean,
    hasActiveCall: Boolean,
    mediaVolume: VirtualVolumeLevel,
    onChange: (() -> Unit)? = null
) {
    Surface(
        color = PopupColors.Surface,
        contentColor = PopupColors.OnTrack,
        shape = RoundedCornerShape(32.dp)
    ) {
        LazyRow(
            horizontalArrangement = popupBarSpacing,
            contentPadding = PaddingValues(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            modifier = Modifier.height(380.dp)
        ) {
            popupSystemVolumeBars(
                audioManager = audioManager,
                isSliderVisible = isSystemSliderVisible,
                hasActiveCall = hasActiveCall,
                mediaVolume = mediaVolume,
                onChange = onChange
            )

            items(items = activeApps, key = { app -> app.packageName }) { app ->
                PopupAppVolumeBar(app = app, onChange = onChange)
            }
        }
    }
}
