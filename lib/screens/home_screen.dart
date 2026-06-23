import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../viewmodels/audio_viewmodel.dart';
import '../widgets/status_card.dart';
import '../services/method_channel_service.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final vm = Provider.of<AudioViewModel>(context);
    return Scaffold(
      appBar: AppBar(title: const Text('AI Hearing Assistant')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            StatusCard(title: 'Bluetooth', value: vm.bluetoothStatus),
            const SizedBox(height: 8),
            StatusCard(title: 'Status', value: vm.streamingStatus),
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ElevatedButton(
                  onPressed: vm.start,
                  child: const Text('START'),
                ),
                ElevatedButton(
                  onPressed: vm.connectBluetooth,
                  child: const Text('CONNECT'),
                ),
                ElevatedButton(
                  onPressed: () async {
                    final svc = MethodChannelService();
                    final devices = await svc.listDevices();
                    await showDialog<void>(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        title: const Text('Paired devices'),
                        content: SizedBox(
                          width: double.maxFinite,
                          child: ListView.builder(
                            shrinkWrap: true,
                            itemCount: devices.length,
                            itemBuilder: (c, i) {
                              final d = devices[i];
                              final name = (d['name'] ?? '').toString();
                              final address = (d['address'] ?? '').toString();
                              final bonded = d['bonded'] == true;
                              return ListTile(
                                title: Text(name),
                                subtitle: Text(address),
                                trailing: ElevatedButton(
                                  child: Text(bonded ? 'PAIRED' : 'PAIR'),
                                  onPressed: () async {
                                    final ok = await svc.pairDevice(address);
                                    Navigator.of(ctx).pop();
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      SnackBar(content: Text(ok ? 'Pairing started' : 'Pair failed')),
                                    );
                                  },
                                ),
                              );
                            },
                          ),
                        ),
                        actions: [TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('Close'))],
                      ),
                    );
                  },
                  child: const Text('DEVICES'),
                ),
                ElevatedButton(
                  onPressed: vm.stop,
                  child: const Text('STOP'),
                ),
              ],
            ),
            const SizedBox(height: 20),
            if (vm.errorMessage != null)
              Text(vm.errorMessage!, style: const TextStyle(color: Colors.red)),
          ],
        ),
      ),
    );
  }
}
