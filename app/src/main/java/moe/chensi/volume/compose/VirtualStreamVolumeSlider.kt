package moe.chensi.volume.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import moe.chensi.volume.VirtualVolumeLevel

/**
 * The main-activity counterpart to [moe.chensi.volume.compose.popup.PopupVirtualVolumeBar]: a
 * horizontal slider bound to a [VirtualVolumeLevel] rather than a real OS
 * [android.media.AudioManager] stream, mirroring [StreamVolumeSlider]'s layout. Used to manually
 * control the fine-grained "fake step" media/VoIP-call master volume from the app's main screen.
 *
 * No broadcast-observing is needed here, unlike [StreamVolumeSlider]: [VirtualVolumeLevel] is
 * backed by Compose state directly, so this recomposes on its own — including when the level is
 * changed from a hardware volume key press (handled in [moe.chensi.volume.Service]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualStreamVolumeSlider(
    level: VirtualVolumeLevel,
    icon: ImageVector,
    name: String,
    footer: (@Composable () -> Unit)? = null,
    onChange: (() -> Unit)? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackSlider(
            modifier = Modifier.weight(1f),
            cornerRadius = 20.dp,
            value = level.level.toFloat(),
            valueRange = 0f..level.maxLevel.toFloat(),
            onValueChange = { value ->
                val target = value.toInt()
                if (level.level == target) {
                    return@TrackSlider
                }

                level.setLevel(target)
                onChange?.invoke()
            },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp, 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    modifier = Modifier.size(32.dp),
                )
                StreamSliderTextContent(
                    name = name,
                    valueText = "${level.level}/${level.maxLevel}"
                )
            }
        }

        footer?.invoke()
    }
}
