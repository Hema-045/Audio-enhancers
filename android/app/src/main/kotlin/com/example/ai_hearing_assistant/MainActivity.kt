package com.example.ai_hearing_assistant

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import androidx.core.content.ContextCompat
import android.content.Intent
import android.provider.Settings
import android.bluetooth.BluetoothDevice
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioManager
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : FlutterActivity() {
	private val CHANNEL = "ai_hearing_assistant/audio"

	override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
		super.configureFlutterEngine(flutterEngine)

		MethodChannel(
			flutterEngine.dartExecutor.binaryMessenger,
			CHANNEL
		).setMethodCallHandler { call, result ->
			when (call.method) {
				"getBluetoothStatus" -> result.success(getBluetoothStatus())
				"openBluetoothSettings" -> {
					try {
						val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
						startActivity(intent)
						result.success(true)
					} catch (e: Exception) {
						result.success(false)
					}
				}
				"listDevices" -> result.success(listDevices())
				"pairDevice" -> {
					val addr = call.argument<String>("address")
					if (addr == null) result.success(false) else result.success(pairDevice(addr))
				}
				"getConnectedDevices" -> result.success(getConnectedDeviceAddresses())
					"startService" -> {
						val intent = android.content.Intent(this, ForegroundAudioService::class.java)
						intent.action = ForegroundAudioService.ACTION_START
						if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
							startForegroundService(intent)
						} else {
							startService(intent)
						}
						result.success(true)
					}
					"stopService" -> {
						val intent = android.content.Intent(this, ForegroundAudioService::class.java)
						intent.action = ForegroundAudioService.ACTION_STOP
						startService(intent)
						result.success(true)
					}
				else -> result.notImplemented()
			}
		}
	}

	private fun getBluetoothStatus(): String {
		val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
		if (adapter == null) return "Unavailable"

		// On Android 12+ apps need BLUETOOTH_CONNECT to query connection state.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			val perm = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
			if (perm != PackageManager.PERMISSION_GRANTED) {
				return "PermissionRequired"
			}
		}

		return try {
			val headsetState = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
			val a2dpState = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
			if (headsetState == BluetoothProfile.STATE_CONNECTED || a2dpState == BluetoothProfile.STATE_CONNECTED) {
				"Connected"
			} else {
				"Disconnected"
			}
		} catch (e: SecurityException) {
			"PermissionRequired"
		} catch (e: Exception) {
			"Unknown"
		}
	}

	private fun listDevices(): List<Map<String, Any>> {
		val adapter = BluetoothAdapter.getDefaultAdapter() ?: return listOf()
		val paired = adapter.bondedDevices
		val connected = getConnectedDeviceAddresses()
		val list = mutableListOf<Map<String, Any>>()
		for (d in paired) {
			list.add(mapOf(
				"name" to (d.name ?: ""),
				"address" to d.address,
				"bonded" to (d.bondState == BluetoothDevice.BOND_BONDED),
				"connected" to connected.contains(d.address)
			))
		}
		return list
	}

	private var streamingThread: Thread? = null
	private val streaming = AtomicBoolean(false)

	private fun startAudioStreaming(): Boolean {
		val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			val perm = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
			if (perm != PackageManager.PERMISSION_GRANTED) return false
		}
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false

		val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
		try {
			audioManager.stopBluetoothSco()
		} catch (e: Exception) {}
		try {
			audioManager.setBluetoothScoOn(false)
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
						android.util.Log.d("MainActivity", "Audio chunk read=$read latency_ms=$latencyMs")
					}
				}
			} catch (e: Exception) {
			} finally {
				try { record.stop(); record.release() } catch (e: Exception) {}
				try { track.stop(); track.release() } catch (e: Exception) {}
			}
		}
		streamingThread?.start()
		return true
	}

	private fun stopAudioStreaming() {
		streaming.set(false)
		try { streamingThread?.join(500) } catch (e: Exception) {}
		val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
		audioManager.stopBluetoothSco()
		audioManager.mode = AudioManager.MODE_NORMAL
		streamingThread = null
	}

	private fun pairDevice(address: String): Boolean {
		val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
		try {
			val device = adapter.getRemoteDevice(address)
			// createBond requires BLUETOOTH_CONNECT on Android S+
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				val perm = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
				if (perm != PackageManager.PERMISSION_GRANTED) return false
			}
			return device.createBond()
		} catch (e: Exception) {
			return false
		}
	}

	private fun getConnectedDeviceAddresses(): List<String> {
		val adapter = BluetoothAdapter.getDefaultAdapter() ?: return listOf()
		val result = mutableSetOf<String>()
		try {
			val latch = java.util.concurrent.CountDownLatch(2)
			// HEADSET
			adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
				override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
					try {
						for (d in proxy.connectedDevices) result.add(d.address)
					} catch (e: Exception) {}
					adapter.closeProfileProxy(profile, proxy)
					latch.countDown()
				}
				override fun onServiceDisconnected(profile: Int) { latch.countDown() }
			}, BluetoothProfile.HEADSET)
			// A2DP
			adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
				override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
					try {
						for (d in proxy.connectedDevices) result.add(d.address)
					} catch (e: Exception) {}
					adapter.closeProfileProxy(profile, proxy)
					latch.countDown()
				}
				override fun onServiceDisconnected(profile: Int) { latch.countDown() }
			}, BluetoothProfile.A2DP)
			latch.await(700, java.util.concurrent.TimeUnit.MILLISECONDS)
		} catch (e: Exception) {
		}
		return result.toList()
	}
}
