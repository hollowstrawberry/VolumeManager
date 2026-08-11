package moe.chensi.volume

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Config for the "fake more volume steps" media master volume. Only media has one — see
 * [moe.chensi.volume.Manager] and the class docs on [moe.chensi.volume.system.AudioPlaybackConfigurationProxy.Category]
 * for why VoIP call audio and genuine cellular calls don't.
 */
object VirtualVolumeConfig {
    // The level ranges 0..MEDIA_MAX_LEVEL inclusive (33 positions), matching how the real OS
    // stream's own max works (e.g. a real max of 16 means positions 0..16, 17 total) rather than
    // being a step *count*. Roughly double a typical real media max (15-16 on stock AOSP), which
    // is also a clean, easy-to-reason-about ratio for the real/virtual mirroring in Manager.
    const val MEDIA_MAX_LEVEL = 32
}

/**
 * The perceptual (roughly constant-dB-per-step) volume curve used both by [VirtualVolumeLevel]
 * (the fine-grained master media volume) and by the per-app 0-100 slider (see
 * [moe.chensi.volume.data.App]), replacing the naive linear-amplitude mapping that made the old
 * per-app slider's lower steps sound wildly different from each other while its higher steps all
 * sounded similar. 0 is always exact silence; 1 is always unity gain (no attenuation).
 *
 * [RANGE_DB] is the total dynamic range covered between the lowest non-zero position and the top.
 * 50dB is a reasonable "useful" range for a consumer volume control — below that, the difference
 * is barely audible on most devices/media anyway, so spending resolution on it would waste it
 * where it's actually needed.
 */
object VirtualVolumeCurve {
    private const val RANGE_DB = 50f

    /** Maps a continuous position in 0f..1f (e.g. the per-app slider) to a linear gain multiplier. */
    fun fractionToGain(fraction: Float): Float {
        val f = fraction.coerceIn(0f, 1f)
        if (f <= 0f) {
            return 0f
        }
        if (f >= 1f) {
            return 1f
        }

        val db = -RANGE_DB * (1f - f)
        return Math.pow(10.0, (db / 20.0)).toFloat()
    }

    /** Maps a discrete step in 0..maxStep (e.g. [VirtualVolumeLevel]) to a linear gain multiplier. */
    fun stepToGain(step: Int, maxStep: Int): Float {
        if (maxStep <= 0) {
            return 1f
        }

        return fractionToGain(step.toFloat() / maxStep.toFloat())
    }

    /**
     * Inverse of [fractionToGain]: given a target linear gain in 0f..1f, finds the fraction in
     * 0f..1f whose curve position produces (approximately) that gain. Used to approximate what
     * position on the *real* OS stream (which is assumed to follow roughly the same kind of
     * perceptual taper) corresponds to a given target gain — see [gainToStep].
     */
    fun gainToFraction(gain: Float): Float {
        val g = gain.coerceIn(0f, 1f)
        if (g <= 0f) {
            return 0f
        }
        if (g >= 1f) {
            return 1f
        }

        val db = 20f * kotlin.math.log10(g)
        return (1f + db / RANGE_DB).coerceIn(0f, 1f)
    }

    /** Inverse of [stepToGain]: finds the nearest step in 0..maxStep for a target linear gain. */
    fun gainToStep(gain: Float, maxStep: Int): Int {
        if (maxStep <= 0) {
            return 0
        }

        return (gainToFraction(gain) * maxStep).roundToInt().coerceIn(0, maxStep)
    }

    /**
     * Converts a position on a 0..fromMax scale to the equivalent position on a 0..toMax scale,
     * by matching fractions (e.g. real stream index <-> virtual level). Since both
     * [stepToGain]/[gainToStep] only depend on the *fraction* of the max, not the max itself, this
     * proportional mapping is also exactly what keeps the perceptual gain consistent across the
     * two scales — see [moe.chensi.volume.Manager]'s real/virtual media volume mirroring.
     */
    fun mapStep(step: Int, fromMax: Int, toMax: Int): Int {
        if (fromMax <= 0) {
            return 0
        }

        return (step.toFloat() / fromMax.toFloat() * toMax).roundToInt().coerceIn(0, toMax)
    }
}

/**
 * A virtual, app-owned volume level ranging from `0` (silence) to [maxLevel] (unity gain),
 * completely decoupled from the OS's real stream volume indices — but see
 * [moe.chensi.volume.Manager] for how it's still kept in sync (approximately) with the real media
 * stream, so other apps see it change too.
 *
 * This mirrors the same trick [moe.chensi.volume.data.App] already uses for its per-app 0-100
 * slider: rather than asking the OS for more steps than it actually has, the resulting gain is
 * applied directly to each active player via `IPlayer.setVolume()`.
 *
 * Backed by Compose state so that both the popup and the main-activity sliders update live
 * without any extra plumbing (unlike the real stream sliders, which need to observe
 * `ACTION_VOLUME_CHANGED` broadcasts since they go through the OS instead).
 */
class VirtualVolumeLevel(
    val maxLevel: Int,
    initialLevel: Int,
    private val onLevelChanged: (Int) -> Unit
) {
    private var _level by mutableIntStateOf(initialLevel.coerceIn(0, maxLevel))
    val level: Int
        get() = _level

    val gain: Float
        get() = VirtualVolumeCurve.stepToGain(_level, maxLevel)

    fun setLevel(value: Int) {
        val coerced = value.coerceIn(0, maxLevel)
        if (coerced == _level) {
            return
        }

        _level = coerced
        onLevelChanged(coerced)
    }

    /**
     * Moves by exactly one step in the given direction. [direction] mirrors
     * [android.media.AudioManager.ADJUST_RAISE] (positive) / [android.media.AudioManager.ADJUST_LOWER]
     * (negative), so callers coming from a volume key event can pass the same value through
     * unchanged.
     */
    fun adjust(direction: Int) {
        if (direction == 0) {
            return
        }

        setLevel(_level + if (direction > 0) 1 else -1)
    }
}
