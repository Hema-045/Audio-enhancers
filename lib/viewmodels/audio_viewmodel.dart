import 'dart:io';

import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import '../models/audio_state.dart';
import '../services/method_channel_service.dart';

class AudioViewModel extends ChangeNotifier {
  final _mc = MethodChannelService();
  AudioState _state = AudioState.idle;
  String _bluetooth = 'Unknown';
  String? _errorMessage;
  bool _noiseReductionEnabled = false;
  bool _amplificationEnabled = false;
  double _amplificationGain = 2.0;

  AudioViewModel() {
    refreshStatus();
  }

  String get bluetoothStatus => _bluetooth;
  String get streamingStatus => _state.name;
  String? get errorMessage => _errorMessage;
  bool get noiseReductionEnabled => _noiseReductionEnabled;
  bool get amplificationEnabled => _amplificationEnabled;
  double get amplificationGain => _amplificationGain;

  Future<void> refreshStatus() async {
    var bt = await _mc.getBluetoothStatus();
    if (bt == 'PermissionRequired' && Platform.isAndroid) {
      final status = await Permission.bluetoothConnect.request();
      if (status.isGranted) {
        bt = await _mc.getBluetoothStatus();
      }
    }
    _bluetooth = bt;
    // debug
    // ignore: avoid_print
    print('Bluetooth status: $_bluetooth');
    notifyListeners();
  }

  Future<void> start() async {
    _errorMessage = null;
    final granted = await _mc.requestMicrophonePermission();
    if (!granted) {
      _state = AudioState.permissionDenied;
      _errorMessage = 'Microphone permission denied';
      notifyListeners();
      return;
    }
    final bt = await _mc.getBluetoothStatus();
    _bluetooth = bt;
    // debug
    // ignore: avoid_print
    print('Start: initial bluetooth status: $_bluetooth');
    if (bt != 'Connected') {
      if (bt == 'PermissionRequired' && Platform.isAndroid) {
        final status = await Permission.bluetoothConnect.request();
        if (status.isGranted) {
          final bt2 = await _mc.getBluetoothStatus();
          _bluetooth = bt2;
          if (bt2 == 'Connected') {
            // proceed
          } else {
            _state = AudioState.bluetoothDisconnected;
            _errorMessage = 'Bluetooth headset not connected';
            notifyListeners();
            return;
          }
        } else {
          _state = AudioState.bluetoothDisconnected;
          _errorMessage = 'Bluetooth permission denied';
          notifyListeners();
          return;
        }
      }
      _state = AudioState.bluetoothDisconnected;
      _errorMessage = 'Bluetooth headset not connected';
      notifyListeners();
      return;
    }
    // On Android 13+ we must request POST_NOTIFICATIONS to show foreground notification
    if (Platform.isAndroid) {
      try {
        final notif = await Permission.notification.request();
        // proceed regardless of notification permission, but logging if denied
        if (!notif.isGranted) {
          // ignore: avoid_print
          print('Notification permission not granted; service notification may be blocked');
        }
      } catch (e) {
      }
    }

    final ok = await _mc.startService();
    if (ok) {
      _state = AudioState.streaming;
    } else {
      _state = AudioState.error;
      _errorMessage = 'Failed to start service';
    }
    notifyListeners();
  }

  Future<void> setNoiseReductionEnabled(bool enabled) async {
    final ok = await _mc.setNoiseReductionEnabled(enabled);
    if (ok) {
      _noiseReductionEnabled = enabled;
      notifyListeners();
    }
  }

  void setAmplificationEnabled(bool enabled) {
    _amplificationEnabled = enabled;
    notifyListeners();
    _sendAmplificationState();
  }

  void setAmplificationGain(double gain) {
    _amplificationGain = gain;
    notifyListeners();
    _sendAmplificationState();
  }

  void _sendAmplificationState() {
    _mc.setAmplification(_amplificationEnabled, _amplificationGain);
  }

  Future<void> stop() async {
    await _mc.stopService();
    _state = AudioState.idle;
    notifyListeners();
  }

  Future<void> connectBluetooth() async {
    _errorMessage = null;
    final ok = await _mc.openBluetoothSettings();
    if (!ok) {
      _errorMessage = 'Could not open Bluetooth settings';
      notifyListeners();
      return;
    }
    // give user a moment to connect, then refresh status
    await Future.delayed(const Duration(seconds: 1));
    await refreshStatus();
  }
}
