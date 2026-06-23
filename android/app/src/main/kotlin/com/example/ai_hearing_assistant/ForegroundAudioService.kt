package com.example.ai_hearing_assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean

class ForegroundAudioService : Service() {
    private val TAG = "ForegroundAudioService"
    private val CHANNEL_ID = "hear_lens_channel"
    private val NOTIF_ID = 4242

    private var streamingThread: Thread? = null
    private val streaming = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification())
                startAudioStreaming()
            }
            ACTION_STOP -> {
                stopAudioStreaming()
                stopForeground(true)
                stopSelf()
            }
            else -> {
                // default start
                startForeground(NOTIF_ID, buildNotification())
                startAudioStreaming()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        stopAudioStreaming()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "HearLens Service", NotificationManager.IMPORTANCE_LOW)
            chan.setSound(null, null)
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(chan)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ForegroundAudioService::class.java)
        stopIntent.action = ACTION_STOP
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HearLens Active")
            .setContentText("Hearing assistance is running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
            .build()
    }

    private fun startAudioStreaming() {
        if (streaming.get()) return

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        try {
            audioManager.stopBluetoothSco()
        } catch (e: Exception) {}
        try {
            audioManager.isBluetoothScoOn = false
        } catch (e: Exception) {}
        audioManager.mode = AudioManager.MODE_NORMAL
        try { audioManager.isSpeakerphoneOn = false } catch (e: Exception) {}

        val sampleRate = 48000
        val channelIn = AudioFormat.CHANNEL_IN_MONO
        val channelOut = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        var minIn = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
        if (minIn == AudioRecord.ERROR || minIn == AudioRecord.ERROR_BAD_VALUE) minIn = 2048
        var minOut = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)
        if (minOut == AudioTrack.ERROR || minOut == AudioTrack.ERROR_BAD_VALUE) minOut = 2048

        // Log runtime buffer configuration reported by Android
        try {
            val bytesPerFrame = 2 // PCM 16-bit mono = 2 bytes per frame
            val recordBufferBytes = minIn
            val trackBufferBytes = minOut
            val recordBufferMs = recordBufferBytes.toDouble() / bytesPerFrame / sampleRate.toDouble() * 1000.0
            val trackBufferMs = trackBufferBytes.toDouble() / bytesPerFrame / sampleRate.toDouble() * 1000.0
            Log.d(TAG, "BUFFER_INFO: sampleRate=$sampleRate bytesPerFrame=$bytesPerFrame minIn(bytes)=$recordBufferBytes minOut(bytes)=$trackBufferBytes recordBuf_ms=$recordBufferMs trackBuf_ms=$trackBufferMs")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute buffer info", e)
        }

        val record = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelIn, encoding, minIn)

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val audioFormat = AudioFormat.Builder()
            .setEncoding(encoding)
            .setChannelMask(channelOut)
            .setSampleRate(sampleRate)
            .build()

        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(audioAttrs)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minOut)
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        val track = trackBuilder.build()

        // After constructing AudioRecord/AudioTrack, log actual instance buffer sizes (frames/bytes/ms)
        try {
            val bytesPerFrame = 2 // PCM16 mono
            // record instance
            var recordBufferBytesInstance = minIn
            var recordBufferFramesInstance: Int? = null
            try {
                recordBufferFramesInstance = record.bufferSizeInFrames
                recordBufferBytesInstance = recordBufferFramesInstance * bytesPerFrame
            } catch (e: Exception) {
                // API may not expose bufferSizeInFrames; fallback to minIn
            }
            val recordBufferMsInstance = recordBufferBytesInstance.toDouble() / bytesPerFrame / sampleRate.toDouble() * 1000.0

            // track instance
            var trackBufferBytesInstance = minOut
            var trackBufferFramesInstance: Int? = null
            try {
                trackBufferFramesInstance = track.bufferSizeInFrames
                if (trackBufferFramesInstance != 0) trackBufferBytesInstance = trackBufferFramesInstance * bytesPerFrame
            } catch (e: Exception) {
                // fallback to minOut
            }
            val trackBufferMsInstance = trackBufferBytesInstance.toDouble() / bytesPerFrame / sampleRate.toDouble() * 1000.0

            // app-level chunk
            val appChunkSamples = 128
            val appChunkMs = appChunkSamples.toDouble() / sampleRate.toDouble() * 1000.0

            val estimatedSoftwareLatencyMs = recordBufferMsInstance + trackBufferMsInstance + appChunkMs

            Log.d(TAG, "BUFFER_INSTANCE: recordBytes=$recordBufferBytesInstance recordFrames=${recordBufferFramesInstance ?: -1} recordMs=$recordBufferMsInstance")
            Log.d(TAG, "BUFFER_INSTANCE: trackBytes=$trackBufferBytesInstance trackFrames=${trackBufferFramesInstance ?: -1} trackMs=$trackBufferMsInstance")
            Log.d(TAG, "BUFFER_INSTANCE: appChunkSamples=$appChunkSamples appChunkMs=$appChunkMs estimated_software_latency_ms=$estimatedSoftwareLatencyMs")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log buffer instance info", e)
        }

        streaming.set(true)
        streamingThread = Thread {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                record.startRecording()
                track.play()
                val buffer = ShortArray(128)
                while (streaming.get()) {
                    val read = record.read(buffer, 0, buffer.size)
                    val tRead = System.nanoTime()
                    if (read > 0) {
                        track.write(buffer, 0, read)
                        val tWrite = System.nanoTime()
                        val latencyMs = (tWrite - tRead) / 1_000_000.0
                        Log.d(TAG, "Audio chunk read=$read latency_ms=$latencyMs read_ts=$tRead write_ts=$tWrite")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio stream error", e)
            } finally {
                try { record.stop(); record.release() } catch (e: Exception) {}
                try { track.stop(); track.release() } catch (e: Exception) {}
            }
        }
        streamingThread?.start()
    }

    private fun stopAudioStreaming() {
        streaming.set(false)
        try { streamingThread?.join(500) } catch (e: Exception) {}
        streamingThread = null
    }

    companion object {
        const val ACTION_START = "com.example.ai_hearing_assistant.action.START"
        const val ACTION_STOP = "com.example.ai_hearing_assistant.action.STOP"
    }
}
