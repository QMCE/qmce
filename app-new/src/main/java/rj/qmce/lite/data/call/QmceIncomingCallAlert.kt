package rj.qmce.lite.data.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator

internal object QmceIncomingCallAlert {
    private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1_000L
    private val vibrationPattern = longArrayOf(100L, 1_500L, 1_500L, 1_500L, 1_500L)

    private var audioManager: AudioManager? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun start(context: Context) {
        if (ringtone != null || wakeLock?.isHeld == true) return
        val appContext = context.applicationContext
        runCatching { acquireWakeLock(appContext) }
        runCatching { requestAudioFocus(appContext) }
        runCatching { startRingtone(appContext) }
        runCatching { startVibration(appContext) }
    }

    @Synchronized
    fun stop() {
        runCatching { ringtone?.stop() }
        ringtone = null
        audioManager?.let { manager ->
            @Suppress("DEPRECATION")
            runCatching { manager.abandonAudioFocus(null) }
        }
        audioManager = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
            }
        }
        wakeLock = null
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock(context: Context) {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "qmce:incoming-call",
        ).also { lock ->
            lock.setReferenceCounted(false)
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun startRingtone(context: Context) {
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        ringtone = RingtoneManager.getRingtone(context, ringtoneUri)?.also { tone ->
            tone.audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                tone.isLooping = true
            }
            tone.play()
        }
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus(context: Context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        manager.mode = AudioManager.MODE_NORMAL
        manager.requestAudioFocus(
            null,
            AudioManager.STREAM_RING,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        )
        audioManager = manager
    }

    @Suppress("DEPRECATION")
    private fun startVibration(context: Context) {
        val currentVibrator =
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!currentVibrator.hasVibrator()) return
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            currentVibrator.vibrate(
                VibrationEffect.createWaveform(vibrationPattern, 0),
                attributes,
            )
        } else {
            currentVibrator.vibrate(vibrationPattern, 0)
        }
        vibrator = currentVibrator
    }
}
