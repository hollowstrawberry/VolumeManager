package moe.chensi.volume.compose.popup

import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.unit.dp
import moe.chensi.volume.VirtualVolumeLevel
import moe.chensi.volume.compose.SystemSliderIds

/**
 * Adds the visible system stream bars (Call, Media, Ring, Alarm, Notification) as items in a
 * horizontally-scrolling [LazyListScope], each rendered as a vertical, icon-only bar. This is the
 * popup's counterpart to [moe.chensi.volume.compose.SystemVolumePanel], which remains unchanged
 * and is still used by the main activity.
 *
 * The Call bar's visibility is driven entirely by [hasActiveCall], which [Manager] computes from
 * both native telephony call state and any app's VoIP call audio (see
 * [Manager.updateHasActiveCall]) — this is the single source of truth, so there's no
 * mode-tracking duplicated here. Calls (of either kind) always use the real, native-step
 * [PopupStreamVolumeBar], so they behave exactly as they would without this app installed.
 *
 * Media is bound to [mediaVolume] — the fine-grained "fake step" virtual master volume — instead
 * of the real OS stream, via [PopupVirtualVolumeBar].
 */
fun LazyListScope.popupSystemVolumeBars(
    audioManager: AudioManager,
    isSliderVisible: (String) -> Boolean,
    hasActiveCall: Boolean,
    mediaVolume: VirtualVolumeLevel,
    onChange: (() -> Unit)? = null
) {
    if (hasActiveCall && isSliderVisible(SystemSliderIds.Call)) {
        item(SystemSliderIds.Call) {
            PopupStreamVolumeBar(
                streamType = AudioManager.STREAM_VOICE_CALL,
                icon = Icons.Default.PhoneInTalk,
                contentDescription = "Call volume",
                audioManager = audioManager,
                onChange = onChange
            )
        }
    }

    if (isSliderVisible(SystemSliderIds.Media)) {
        item(SystemSliderIds.Media) {
            PopupVirtualVolumeBar(
                level = mediaVolume,
                icon = Icons.Default.VolumeUp,
                contentDescription = "Media volume",
                onChange = onChange
            )
        }
    }

    if (isSliderVisible(SystemSliderIds.Ring)) {
        item(SystemSliderIds.Ring) {
            PopupStreamVolumeBar(
                streamType = AudioManager.STREAM_RING,
                icon = Icons.Default.RingVolume,
                contentDescription = "Ring volume",
                audioManager = audioManager,
                onChange = onChange
            )
        }
    }

    if (isSliderVisible(SystemSliderIds.Alarm)) {
        item(SystemSliderIds.Alarm) {
            PopupStreamVolumeBar(
                streamType = AudioManager.STREAM_ALARM,
                icon = Icons.Default.Alarm,
                contentDescription = "Alarm volume",
                audioManager = audioManager,
                onChange = onChange
            )
        }
    }

    if (isSliderVisible(SystemSliderIds.Notification)) {
        item(SystemSliderIds.Notification) {
            PopupStreamVolumeBar(
                streamType = AudioManager.STREAM_NOTIFICATION,
                icon = Icons.Default.NotificationsNone,
                contentDescription = "Notification volume",
                audioManager = audioManager,
                onChange = onChange
            )
        }
    }
}

internal val popupBarSpacing = Arrangement.spacedBy(10.dp)
