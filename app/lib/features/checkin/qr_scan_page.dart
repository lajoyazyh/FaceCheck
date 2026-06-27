import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/features/checkin/qr_scan_controller.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:facecheck_app/shared/widgets/app_back_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

typedef QrScannerSurfaceBuilder = Widget Function(
  BuildContext context,
  ValueChanged<String> onScan,
);

final qrScannerSurfaceBuilderProvider = Provider<QrScannerSurfaceBuilder>(
  (Ref ref) => (BuildContext context, ValueChanged<String> onScan) {
    return _LiveQrScannerSurface(onScan: onScan);
  },
);

class QrScanPage extends ConsumerStatefulWidget {
  const QrScanPage({
    super.key,
    this.initialQrToken,
  });

  final String? initialQrToken;

  @override
  ConsumerState<QrScanPage> createState() => _QrScanPageState();
}

class _QrScanPageState extends ConsumerState<QrScanPage> {
  late final TextEditingController _manualController;
  bool _hasNavigated = false;

  @override
  void initState() {
    super.initState();
    _manualController =
        TextEditingController(text: widget.initialQrToken ?? '');

    final initialQrToken = widget.initialQrToken;
    if (initialQrToken != null && initialQrToken.trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _goToSessionConfirm(initialQrToken.trim());
      });
    }
  }

  @override
  void dispose() {
    _manualController.dispose();
    super.dispose();
  }

  Future<void> _handlePayload(String rawPayload) async {
    final qrToken =
        ref.read(qrScanControllerProvider.notifier).resolvePayload(rawPayload);
    if (qrToken == null || !mounted) {
      return;
    }
    _goToSessionConfirm(qrToken);
  }

  void _goToSessionConfirm(String qrToken) {
    if (_hasNavigated || !mounted) {
      return;
    }
    _hasNavigated = true;
    final encodedToken = Uri.encodeQueryComponent(qrToken);
    context.push('${AppRoutePaths.publicSessionConfirm}?qrToken=$encodedToken');
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(qrScanControllerProvider);
    final scannerSurface = ref.watch(qrScannerSurfaceBuilderProvider);

    return Scaffold(
      key: AppTestKeys.anonymousCheckinEntryPage,
      appBar: AppBar(
        leading: const AppBackButton(fallbackLocation: AppRoutePaths.login),
        title: const Text('场次入口'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: <Widget>[
          Text(
            '扫码签到',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          const Text(
            '此流程保持匿名，只允许进入单个场次、提交一张签到照片，并查看本次尝试结果。',
          ),
          const SizedBox(height: 24),
          ClipRRect(
            borderRadius: BorderRadius.circular(20),
            child: SizedBox(
              key: AppTestKeys.scanQrButton,
              height: 320,
              child: scannerSurface(context, _handlePayload),
            ),
          ),
          const SizedBox(height: 16),
          const Text(
            '如果模拟器相机不可用，可在下方粘贴二维码内容或 qrToken。',
          ),
          const SizedBox(height: 12),
          TextField(
            key: AppTestKeys.sessionEntryInput,
            controller: _manualController,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: '请输入场次码',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: () => _handlePayload(_manualController.text),
            icon: const Icon(Icons.arrow_forward),
            label: const Text('进入场次'),
          ),
          if (state.errorMessage != null) ...<Widget>[
            const SizedBox(height: 16),
            Text(
              state.errorMessage!,
              style: TextStyle(
                color: Theme.of(context).colorScheme.error,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _LiveQrScannerSurface extends StatefulWidget {
  const _LiveQrScannerSurface({
    required this.onScan,
  });

  final ValueChanged<String> onScan;

  @override
  State<_LiveQrScannerSurface> createState() => _LiveQrScannerSurfaceState();
}

class _LiveQrScannerSurfaceState extends State<_LiveQrScannerSurface> {
  bool _handledDetection = false;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        MobileScanner(
          fit: BoxFit.cover,
          onDetect: (BarcodeCapture capture) {
            if (_handledDetection) {
              return;
            }
            final rawValue = capture.barcodes
                .map((Barcode barcode) => barcode.rawValue)
                .whereType<String>()
                .cast<String?>()
                .firstWhere(
                  (String? value) => value != null && value.trim().isNotEmpty,
                  orElse: () => null,
                );
            if (rawValue == null) {
              return;
            }
            _handledDetection = true;
            widget.onScan(rawValue);
          },
        ),
        DecoratedBox(
          decoration: BoxDecoration(
            border: Border.all(color: Colors.white70, width: 2),
          ),
          child: const Center(
            child: Icon(
              Icons.qr_code_scanner,
              size: 72,
              color: Colors.white70,
            ),
          ),
        ),
      ],
    );
  }
}
