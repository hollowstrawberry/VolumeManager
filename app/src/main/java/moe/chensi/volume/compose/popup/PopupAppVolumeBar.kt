package moe.chensi.volume.compose.popup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import moe.chensi.volume.data.App

/**
 * A vertical, icon-labelled bar bound to a single app's volume. This is the popup's counterpart
 * to [moe.chensi.volume.compose.AppVolumeSlider], which remains unchanged and is still used by
 * the main activity.
 *
 * The app icon is rendered below the slider (not overlaid on top of it), sized to the full width
 * of the bar.
 */
@Composable
fun PopupAppVolumeBar(
    app: App,
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
            value = app.volume,
            cornerRadius = 14.dp,
            onValueChange = { value ->
                app.volume = value
                onChange?.invoke()
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon!!,
                    contentDescription = app.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(PopupColors.Muted)
                )
            }
        }
    }
}
