package moe.chensi.volume.compose.popup

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import moe.chensi.volume.compose.VolumeChangeObserver

/**
 * A vertical, icon-labelled bar bound to a single [AudioManager] stream. This is the popup's
 * counterpart to [moe.chensi.volume.compose.StreamVolumeSlider], which remains unchanged and is
 * still used by the main activity.
 *
 * The icon is rendered below the slider (not overlaid on top of it), sized to the full width of
 * the bar.
 */
@Composable
fun PopupStreamVolumeBar(
    streamType: Int,
    icon: ImageVector,
    contentDescription: String,
    audioManager: AudioManager,
    modifier: Modifier = Modifier,
    onChange: (() -> Unit)? = null
) {
    val context: Context = LocalContext.current
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(streamType)) }
    var maxVolume by remember {
        mutableFloatStateOf(audioManager.getStreamMaxVolume(streamType).toFloat())
    }

    DisposableEffect(context) {
        VolumeChangeObserver.startObserving(context)
        onDispose {
            VolumeChangeObserver.stopObserving()
        }
    }

    val volumeChangedCount = VolumeChangeObserver.volumeChangedCount
    LaunchedEffect(volumeChangedCount) {
        volume = audioManager.getStreamVolume(streamType)
        maxVolume = audioManager.getStreamMaxVolume(streamType).toFloat()
    }

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
            value = volume.toFloat(),
            valueRange = 0f..maxVolume,
            cornerRadius = 14.dp,
            onValueChange = { value ->
                val target = value.toInt()
                if (volume == target) {
                    return@VerticalTrackSlider
                }

                volume = target
                audioManager.setStreamVolume(streamType, target, 0)
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
