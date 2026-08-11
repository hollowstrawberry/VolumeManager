package moe.chensi.volume

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import moe.chensi.volume.data.App
import moe.chensi.volume.data.AppPreferencesStore
import moe.chensi.volume.system.AudioPlaybackConfigurationProxy
import moe.chensi.volume.system.NotificationManagerProxy
import moe.chensi.volume.system.PackageManagerProxy
import org.joor.Reflect
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

@SuppressLint("PrivateApi")
class Manager(val context: Context, dataStore: DataStore<Preferences>) {
    companion object {
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    }

    enum class ShizukuStatus {
        Uninstalled, Disconnected, PermissionDenied, Connected
    }

    private var _shizukuStatus by mutableStateOf(ShizukuStatus.Disconnected)
    val shizukuStatus
        get() = _shizukuStatus

    /**
     * Whether there's currently an active call, from *any* source: a native telephony call
     * (reflected by [AudioManager.getMode]), or any app's audio session tagged as VoIP/call
     * audio (`AudioAttributes.USAGE_VOICE_COMMUNICATION`) — which covers Discord and most other
     * VoIP apps, since those typically don't flip the system-wide audio mode the way the
     * telephony stack does.
     *
     * Both kinds of call are treated identically everywhere in this app (Service.onKeyEvent,
     * PopupSystemVolumeBars): calls always use the OS's real, native volume — never the fine
     * media master volume — so calls work exactly as they would without this app installed.
     */
    private var _hasActiveCall by mutableStateOf(false)
    val hasActiveCall: Boolean
        get() = _hasActiveCall

    private fun isTelephonyCallMode(mode: Int): Boolean {
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    private var lastPlaybackConfigurations: List<AudioPlaybackConfiguration> = emptyList()

    private fun updateHasActiveCall() {
        val hasVoipCallAudio = lastPlaybackConfigurations.any { config ->
            // isActive() is a hidden/@SystemApi method, so we go through the same reflection
            // proxy used elsewhere in this class to read the player state; getAudioAttributes()
            // is public API and safe to call directly.
            AudioPlaybackConfigurationProxy(config).isPlaying &&
                config.audioAttributes.usage == AudioAttributes.USAGE_VOICE_COMMUNICATION
        }

        _hasActiveCall = hasVoipCallAudio || isTelephonyCallMode(audioManager.mode)
    }

    val audioManager = context.getSystemService(AudioManager::class.java)!!.apply {
        Reflect.onClass(AudioManager::class.java).call("getService").get<Any>()
            .apply { ToggleableBinderProxy.wrap(this) }
    }

    val activityManager = context.getSystemService(ActivityManager::class.java)!!.apply {
        Reflect.onClass(ActivityManager::class.java).call("getService").get<Any>()
            .apply { ToggleableBinderProxy.wrap(this) }
    }
    private val packageManager by lazy { PackageManagerProxy.get(context) }
    val notificationManagerProxy = NotificationManagerProxy(context)

    private val appPreferencesStore = AppPreferencesStore(dataStore)

    // The "fake more volume steps" media master control. Real per-app volume (App.volume, 0-100)
    // is multiplied by this (for Media-category players — see
    // AudioPlaybackConfigurationProxy.Category) before being pushed to that player's IPlayer —
    // exactly the same setVolume() mechanism the per-app slider already uses, just applied
    // master-wide instead of per-app. This is what actually delivers the extra steps: the real
    // OS-level media stream index (15 steps on stock AOSP) is hardcoded inside system_server and
    // out of reach even with Shizuku's privileges, so this level never touches it directly —
    // instead, see the real/virtual mirroring below.
    val mediaVolume = VirtualVolumeLevel(
        maxLevel = VirtualVolumeConfig.MEDIA_MAX_LEVEL,
        initialLevel = VirtualVolumeConfig.MEDIA_MAX_LEVEL
    ) { level ->
        appPreferencesStore.mediaVirtualLevel = level

        // Keep the real OS stream roughly in sync so other apps (and the system volume UI, if
        // ever shown) see the change too — but only when this update didn't itself originate
        // from a real-stream change (see onRealMediaVolumeChanged), or we'd loop.
        if (!syncingMediaFromRealStream) {
            pushMediaLevelToRealStream(level)
        }

        // Must run *after* the real-stream push above: mediaEffectiveGain() reads the real
        // stream's *current* index live to compute the fine-grained correction on top of it, so
        // it needs the just-pushed value already in place to land on the right total gain.
        reapplyMasterGain()
    }

    private var pendingSelfRealVolumeIndex: Int? = null
    private var syncingMediaFromRealStream = false

    private fun pushMediaLevelToRealStream(level: Int) {
        val realMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (realMax <= 0) {
            return
        }

        val targetRealIndex = VirtualVolumeCurve.mapStep(level, mediaVolume.maxLevel, realMax)
        val currentRealIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (targetRealIndex == currentRealIndex) {
            return
        }

        pendingSelfRealVolumeIndex = targetRealIndex
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetRealIndex, 0)
    }

    /**
     * Called whenever `ACTION_VOLUME_CHANGED` fires for the media stream, from *any* source —
     * another app, the system volume UI, a Bluetooth remote, or an echo of our own
     * [pushMediaLevelToRealStream] call above. Approximately mirrors the real index onto
     * [mediaVolume] by matching fractions of each scale's max (see [VirtualVolumeCurve.mapStep]),
     * without pushing back to the real stream in the process (which would create a feedback
     * loop) — [syncingMediaFromRealStream] guards that.
     */
    private fun onRealMediaVolumeChanged() {
        val realIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        if (pendingSelfRealVolumeIndex == realIndex) {
            // Echo of our own change above; nothing more to do, we already applied its effects
            // synchronously at the time.
            pendingSelfRealVolumeIndex = null
            return
        }
        pendingSelfRealVolumeIndex = null

        val realMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (realMax <= 0) {
            return
        }

        val newVirtualLevel = VirtualVolumeCurve.mapStep(realIndex, realMax, mediaVolume.maxLevel)

        syncingMediaFromRealStream = true
        try {
            mediaVolume.setLevel(newVirtualLevel)
        } finally {
            syncingMediaFromRealStream = false
        }

        // setLevel() above is a no-op (and so never calls reapplyMasterGain) if the mapped level
        // happens to match the current one — but the real index (and therefore the fine
        // correction mediaEffectiveGain() computes against it) has still changed, so make sure
        // it's recomputed regardless.
        reapplyMasterGain()
    }

    /**
     * The actual per-player gain for Media-category players: the fine target from [mediaVolume],
     * divided by the real stream's own approximate contribution (assuming it follows roughly the
     * same perceptual curve — see [VirtualVolumeCurve]) at its *current* live index. Since the
     * real index is kept mirrored close to [mediaVolume] already (see
     * [pushMediaLevelToRealStream]), this correction factor normally stays close to 1 — just
     * enough to make up for the real stream's much coarser resolution — rather than being a full,
     * independent attenuation stacked on top of the real stream's own. That's what avoids the
     * volume effectively getting applied (and moving) twice.
     */
    private fun mediaEffectiveGain(): Float {
        val realMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetGain = mediaVolume.gain
        if (realMax <= 0) {
            return targetGain
        }

        val realIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val realGainApprox = VirtualVolumeCurve.stepToGain(realIndex, realMax)
        if (realGainApprox <= 0.0001f) {
            // Real stream is (effectively) muted — no per-player gain can make it audible again,
            // and dividing by ~0 would blow up. The real mute already delivers silence regardless
            // of what we return here.
            return targetGain
        }

        // Never boost above unity: if the real stream ends up lower than the virtual target
        // (e.g. it was just changed externally and our mirroring hasn't caught up yet), fall
        // short of the target rather than risk clipping/distortion from a >1 player gain.
        return (targetGain / realGainApprox).coerceIn(0f, 1f)
    }

    private fun masterGainFor(category: AudioPlaybackConfigurationProxy.Category): Float {
        return when (category) {
            AudioPlaybackConfigurationProxy.Category.Media -> mediaEffectiveGain()
            AudioPlaybackConfigurationProxy.Category.Other -> 1f
        }
    }

    private fun reapplyMasterGain() {
        for (app in apps.values) {
            app.reapplyMasterGain()
        }
    }

    private val _systemSliderVisibility = mutableStateMapOf<String, Boolean>()
    val systemSliderVisibility: Map<String, Boolean>
        get() = _systemSliderVisibility

    fun isSystemSliderVisible(id: String): Boolean {
        return _systemSliderVisibility[id] ?: true
    }

    fun setSystemSliderVisible(id: String, visible: Boolean) {
        if ((_systemSliderVisibility[id] ?: true) == visible) {
            return
        }

        _systemSliderVisibility[id] = visible
        appPreferencesStore.setSystemSliderVisible(id, visible)
    }

    val apps = mutableStateMapOf<String, App>()

    private fun reloadApps() {
        for (packageInfo in packageManager.getInstalledPackagesForAllUsers()) {
            val appInfo = packageInfo.applicationInfo ?: continue
            if (!apps.containsKey(packageInfo.packageName)) {
                apps[packageInfo.packageName] = App(
                    packageManager,
                    packageInfo,
                    packageManager.loadLabel(appInfo),
                    appPreferencesStore.getOrCreate(packageInfo.packageName),
                    appPreferencesStore::save,
                    ::masterGainFor
                )
            }
        }
    }

    private fun getApp(packageName: String): App? {
        val app = apps[packageName]
        if (app != null) {
            return app
        }

        // Maybe just installed?
        reloadApps()
        return apps[packageName]
    }

    @EnableBinderProxy
    private fun initialize() {
        reloadApps()

        val playbackConfigurations = audioManager.activePlaybackConfigurations
        processAudioPlaybackConfigurations(playbackConfigurations)

        audioManager.registerAudioPlaybackCallback(
            object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                    for (app in apps.values) {
                        app.clearPlayers()
                    }
                    processAudioPlaybackConfigurations(configs)
                }
            }, null
        )

        // Playback-config changes don't fire when a native telephony call starts/ends without
        // any accompanying app audio, so also listen for audio mode changes directly.
        audioManager.addOnModeChangedListener(ContextCompat.getMainExecutor(context)) {
            updateHasActiveCall()
        }

        // Real <-> virtual media volume mirroring (see mediaVolume above). This is the same
        // broadcast action (and re-query-on-any-event pattern) VolumeChangeObserver already uses
        // for the real stream sliders' live UI updates; here it drives the actual gain
        // computation instead, so it needs to keep running for the process's whole lifetime
        // rather than only while a slider happens to be on screen.
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    onRealMediaVolumeChanged()
                }
            },
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    @SuppressLint("DiscouragedPrivateApi")
    @EnableBinderProxy
    fun processAudioPlaybackConfigurations(configs: List<AudioPlaybackConfiguration>) {
        lastPlaybackConfigurations = configs
        updateHasActiveCall()

        val runningProcesses = activityManager.runningAppProcesses

        for (config in configs) {
            val proxy = AudioPlaybackConfigurationProxy(config)

            val pid = proxy.clientPid
            val process = runningProcesses.find { process -> process.pid == pid } ?: continue

            val packageName = process.pkgList[0] ?: continue
            val app = getApp(packageName) ?: continue

            app.addPlayer(proxy)
        }
    }

    init {
        val isShizukuInstalled = try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        if (!isShizukuInstalled) {
            _shizukuStatus = ShizukuStatus.Uninstalled
        } else if (!Shizuku.pingBinder()) {
            _shizukuStatus = ShizukuStatus.Disconnected
        }

        Shizuku.addBinderReceivedListenerSticky {
            if (Shizuku.isPreV11()) {
                return@addBinderReceivedListenerSticky
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _shizukuStatus = ShizukuStatus.Connected
                start()
            } else {
                _shizukuStatus = ShizukuStatus.PermissionDenied
            }
        }

        Shizuku.addBinderDeadListener {
            _shizukuStatus = ShizukuStatus.Disconnected
        }

        Shizuku.addRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                _shizukuStatus = ShizukuStatus.Connected
                start()
            }
        }

        ShizukuProvider.requestBinderForNonProviderProcess(context)
    }

    private fun start() {
        appPreferencesStore.track { first ->
            for ((packageName, index) in appPreferencesStore.indices) {
                if (!first) {
                    // Replace with new reference
                    getApp(packageName)?.setPreferences(appPreferencesStore.values[index])
                }
            }

            _systemSliderVisibility.clear()
            _systemSliderVisibility.putAll(appPreferencesStore.systemSliderVisibility)

            // Sync the virtual media level from the persisted store. setLevel() is a no-op if the
            // value hasn't actually changed, so this is safe to run on every emission (including
            // the initial default-state one before the real persisted data loads).
            mediaVolume.setLevel(appPreferencesStore.mediaVirtualLevel)

            if (first) {
                initialize()
            }
        }
    }
}
