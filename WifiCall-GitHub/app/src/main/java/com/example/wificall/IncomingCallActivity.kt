package com.example.wificall

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.wificall.databinding.ActivityIncomingCallBinding

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var vibrator: Vibrator
    private lateinit var audioManager: AudioManager
    private var previousAlarmVolume: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.callerNameText.text = intent.getStringExtra(Constants.EXTRA_CALLER_NAME) ?: "Unknown"

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        startRinging()

        binding.btnStop.setOnClickListener {
            stopRinging()
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startRinging() {
        // STREAM_ALARM bypasses silent/vibrate ringer mode on most devices.
        previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        val ringUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@IncomingCallActivity, ringUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            mediaPlayer = null
        }

        val pattern = longArrayOf(0, 800, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopRinging() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) { /* ignore */ }
        mediaPlayer = null
        vibrator.cancel()
        if (previousAlarmVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
            previousAlarmVolume = -1
        }
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}
