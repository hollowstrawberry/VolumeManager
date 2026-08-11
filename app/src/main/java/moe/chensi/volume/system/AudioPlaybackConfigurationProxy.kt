package moe.chensi.volume.system

import android.media.AudioAttributes
import android.media.AudioPlaybackConfiguration
import android.os.DeadObjectException
import org.joor.Reflect
import org.joor.ReflectException
import java.lang.reflect.InvocationTargetException

class AudioPlaybackConfigurationProxy(private val raw: AudioPlaybackConfiguration) {
    enum class PlayerState(val value: Int) {
        Unknown(-1), Released(0), Idle(1), Started(2), Paused(3), Stopped(4);
    }

    /**
     * Which virtual master volume (if any) this player's gain should be composed with. Derived
     * from the public [AudioAttributes.getUsage] rather than the OS stream type, since that's all
     * that's actually available per-player.
     *
     * Only `Media` gets a master gain (see [moe.chensi.volume.Manager.mediaVolume]). Everything
     * else, including `USAGE_VOICE_COMMUNICATION` (VoIP call audio, e.g. Discord/WhatsApp/Meet)
     * and genuine cellular/VoLTE calls (which never show up here at all — that audio is routed
     * straight from the modem through the HAL), falls into `Other` and gets no master gain, so
     * calls behave exactly as they did before this app existed. The per-app 0-100 slider still
     * works for VoIP call audio regardless of category — see [moe.chensi.volume.data.App].
     */
    enum class Category {
        Media, Other
    }

    val category: Category
        get() = when (raw.audioAttributes.usage) {
            AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_GAME, AudioAttributes.USAGE_UNKNOWN -> Category.Media
            else -> Category.Other
        }

    fun Int.toPlayerState(): PlayerState {
        for (state in PlayerState.entries) {
            if (state.value == this) {
                return state
            }
        }
        return PlayerState.Unknown
    }

    companion object {
        val classReflect: Reflect = Reflect.onClass(AudioPlaybackConfiguration::class.java)
    }

    private val reflect = Reflect.on(raw)

    private val player = reflect.call("getIPlayer")

    val hasPlayer
        get() = player.get<Any>() != null

    val clientPid: Int = reflect.get("mClientPid")

    val playerType: Int = reflect.get("mPlayerType")

    val playerTypeName: String by lazy {
        classReflect.call("toLogFriendlyPlayerType", playerType).get()
    }

    val playerState
        get() = reflect.get<Int>("mPlayerState").toPlayerState()

    val playerStateName: String by lazy {
        classReflect.call("playerStateToString", playerState.value).get()
    }

    val isPlaying: Boolean
        get() {
            if (playerType == 3) {
                return true
            }

            return playerState == PlayerState.Started
        }

    fun setVolume(value: Float): Boolean {
        return try {
            player.call("setVolume", value)
            true
        } catch (e: ReflectException) {
            val cause = e.cause
            if (cause is InvocationTargetException && cause.cause is DeadObjectException) {
                false
            } else {
                throw e
            }
        }
    }
}
