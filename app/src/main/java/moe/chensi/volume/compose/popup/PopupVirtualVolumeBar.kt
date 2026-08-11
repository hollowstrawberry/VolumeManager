package moe.chensi.volume.compose.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import moe.chensi.volume.VirtualVolumeLevel

/**
 * A vertical, icon-labelled bar bound to a [VirtualVolumeLevel] instead of a real OS
 * [android.media.AudioManager] stream. This is the popup's counterpart to
 * [PopupStreamVolumeBar], used for the fine-grained "fake step" media/VoIP-call volumes instead
 * of the real, native-step-count streams.
 *
 * Unlike [PopupStreamVolumeBar], no [moe.chensi.volume.compose.VolumeChangeObserver]/broadcast
 * plumbing is needed here: [VirtualVolumeLevel] is backed by Compose state directly, so this
 * recomposes on its own whenever the level changes (including from a hardware volume key press
 * handled elsewhere in [moe.chensi.volume.Service]).
 */
@Composable
fun PopupVirtualVolumeBar(
    level: VirtualVolumeLevel,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onChange: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        VerticalTrackSlider(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            value = level.level.toFloat(),
            valueRange = 0f..level.maxLevel.toFloat(),
            cornerRadius = 14.dp,
            onValueChange = { value ->
                val target = value.toInt()
                if (level.level == target) {
                    return@VerticalTrackSlider
                }

                level.setLevel(target)
                onChange?.invoke()
            }
        )

        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = PopupColors.OnTrack,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
    }
}
