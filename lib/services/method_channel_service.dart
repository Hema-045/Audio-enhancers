import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

class MethodChannelService {
  static const _channel = MethodChannel('ai_hearing_assistant/audio');

  Future<bool> startService() async {
    try {
      final res = await _channel.invokeMethod<bool>('startService');
      return res ?? true;
    } catch (e) {
      return false;
    }
  }

  Future<bool> stopService() async {
    try {
      final res = await _channel.invokeMethod<bool>('stopService');
      return res ?? true;
    } catch (e) {
      return false;
    }
  }

  Future<bool> openBluetoothSettings() async {
    try {
      final res = await _channel.invokeMethod<bool>('openBluetoothSettings');
      return res ?? false;
    } catch (e) {
      return false;
    }
  }

  Future<List<Map<String, dynamic>>> listDevices() async {
    try {
      final res = await _channel.invokeMethod<List>('listDevices');
      if (res == null) return [];
      return res.map<Map<String, dynamic>>((e) {
        if (e is Map) {
          final out = <String, dynamic>{};
          e.forEach((key, value) {
            out[key?.toString() ?? ''] = value;
          });
          return out;
        }
        return <String, dynamic>{};
      }).toList();
    } catch (e) {
      return [];
    }
  }

  Future<bool> pairDevice(String address) async {
    try {
      final res = await _channel.invokeMethod<bool>('pairDevice', {'address': address});
      return res ?? false;
    } catch (e) {
      return false;
    }
  }

  Future<List<String>> getConnectedDevices() async {
    try {
      final res = await _channel.invokeMethod<List>('getConnectedDevices');
      if (res == null) return [];
      return res.cast<String>();
    } catch (e) {
      return [];
    }
  }

  Future<String> getBluetoothStatus() async {
    try {
      final res = await _channel.invokeMethod<String>('getBluetoothStatus');
      return res ?? 'Unknown';
    } catch (e) {
      return 'Unknown';
    }
  }

  Future<bool> setNoiseReductionEnabled(bool enabled) async {
    try {
      final res = await _channel.invokeMethod<bool>('setNoiseReductionEnabled', {'enabled': enabled});
      return res ?? true;
    } catch (e) {
      return false;
    }
  }

  Future<bool> requestMicrophonePermission() async {
    final status = await Permission.microphone.request();
    return status.isGranted;
  }
}
