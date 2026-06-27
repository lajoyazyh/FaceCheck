import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/features/checkin/anonymous_checkin_controller.dart';
import 'package:facecheck_app/features/face/face_photo_capture_service.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:facecheck_app/shared/widgets/app_back_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class CheckinCapturePage extends ConsumerWidget {
  const CheckinCapturePage({
    super.key,
    required this.qrToken,
    required this.sessionName,
  });

  final String qrToken;
  final String sessionName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(anonymousCheckinControllerProvider);
    final controller = ref.read(anonymousCheckinControllerProvider.notifier);

    return Scaffold(
      key: AppTestKeys.anonymousCheckinCapturePage,
      appBar: AppBar(
        leading: AppBackButton(
          fallbackLocation:
              '${AppRoutePaths.publicSessionConfirm}?qrToken=${Uri.encodeQueryComponent(qrToken)}',
        ),
        title: const Text('拍摄签到照片'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: <Widget>[
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    sessionName,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    '此处仍然保持匿名：拍摄一张清晰的人脸照片，提交一次，然后只查看本次结果。',
                  ),
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: <Widget>[
                      FilledButton.icon(
                        onPressed: state.isPicking || state.isSubmitting
                            ? null
                            : () => controller.pickPhoto(
                                  PhotoCaptureSource.camera,
                                ),
                        icon: const Icon(Icons.photo_camera_outlined),
                        label: const Text('拍照'),
                      ),
                      OutlinedButton.icon(
                        onPressed: state.isPicking || state.isSubmitting
                            ? null
                            : () => controller.pickPhoto(
                                  PhotoCaptureSource.gallery,
                                ),
                        icon: const Icon(Icons.photo_library_outlined),
                        label: const Text('从相册选择'),
                      ),
                      if (state.photo != null)
                        TextButton(
                          onPressed:
                              state.isSubmitting ? null : controller.clearPhoto,
                          child: const Text('移除预览'),
                        ),
                    ],
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
            ),
          ),
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: state.photo == null
                  ? const Text(
                      '尚未选择照片。请使用相机或相册，并确认画面中只有一张清晰人脸。',
                    )
                  : Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(
                          '预览：${state.photo!.fileName}',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 12),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(16),
                          child: Image.memory(
                            state.photo!.bytes,
                            height: 240,
                            width: double.infinity,
                            fit: BoxFit.cover,
                          ),
                        ),
                      ],
                    ),
            ),
          ),
          const SizedBox(height: 16),
          FilledButton(
            key: AppTestKeys.anonymousCheckinSubmitButton,
            onPressed: state.isSubmitting
                ? null
                : () => _submit(context, ref, qrToken),
            child: Text(
              state.isSubmitting ? '提交中...' : '提交匿名签到',
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _submit(
    BuildContext context,
    WidgetRef ref,
    String qrToken,
  ) async {
    final result = await ref
        .read(anonymousCheckinControllerProvider.notifier)
        .submit(qrToken: qrToken);
    if (result == null || !context.mounted) {
      return;
    }

    final encodedAttemptId = Uri.encodeQueryComponent(result.attemptId);
    final encodedQrToken = Uri.encodeQueryComponent(qrToken);
    context.go(
      '${AppRoutePaths.publicCheckinResult}?attemptId=$encodedAttemptId&qrToken=$encodedQrToken',
    );
  }
}
