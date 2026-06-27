import 'package:facecheck_app/features/face/face_photo_capture_service.dart';
import 'package:facecheck_app/features/face/face_photo_upload_controller.dart';
import 'package:facecheck_app/features/face/widgets/face_photo_status_card.dart';
import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:facecheck_app/shared/widgets/app_back_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class FacePhotoPage extends ConsumerStatefulWidget {
  const FacePhotoPage({super.key});

  @override
  ConsumerState<FacePhotoPage> createState() => _FacePhotoPageState();
}

class _FacePhotoPageState extends ConsumerState<FacePhotoPage> {
  @override
  void initState() {
    super.initState();
    Future.microtask(
      () => ref.read(facePhotoUploadControllerProvider.notifier).loadPhotos(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(facePhotoUploadControllerProvider);
    final controller = ref.read(facePhotoUploadControllerProvider.notifier);

    return Scaffold(
      key: AppTestKeys.facePhotoPage,
      appBar: AppBar(
        leading: const AppBackButton(fallbackLocation: AppRoutePaths.home),
        title: const Text('人脸照片'),
      ),
      body: state.isLoading && state.photos.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: controller.loadPhotos,
              child: ListView(
                padding: const EdgeInsets.all(24),
                children: <Widget>[
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text(
                            '我的识别照片',
                            style: Theme.of(context).textTheme.headlineSmall,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            '${state.photos.length} / ${FacePhotoUploadState.maxPhotos} 张已占用',
                          ),
                          const SizedBox(height: 4),
                          Text('${state.remainingSlots} 个可上传名额'),
                          const SizedBox(height: 16),
                          Wrap(
                            spacing: 12,
                            runSpacing: 12,
                            children: <Widget>[
                              FilledButton.icon(
                                onPressed: state.isSubmitting
                                    ? null
                                    : () => controller.uploadFromSource(
                                          PhotoCaptureSource.gallery,
                                        ),
                                icon: const Icon(Icons.photo_library_outlined),
                                label: const Text('从相册上传'),
                              ),
                              OutlinedButton.icon(
                                onPressed: state.isSubmitting
                                    ? null
                                    : () => controller.uploadFromSource(
                                          PhotoCaptureSource.camera,
                                        ),
                                icon: const Icon(Icons.photo_camera_outlined),
                                label: const Text('拍照上传'),
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
                          if (state.successMessage != null) ...<Widget>[
                            const SizedBox(height: 16),
                            Text(
                              state.successMessage!,
                              style: TextStyle(
                                color: Theme.of(context).colorScheme.primary,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (state.photos.isEmpty)
                    const Card(
                      child: Padding(
                        padding: EdgeInsets.all(20),
                        child: Text(
                          '还没有人脸照片。可从相册或相机上传，开始注册流程。',
                        ),
                      ),
                    ),
                  for (final photo in state.photos) ...<Widget>[
                    FacePhotoStatusCard(
                      photo: photo,
                      isSubmitting: state.isSubmitting,
                      onDelete: () => controller.deletePhoto(photo.photoId),
                      onReplace: (PhotoCaptureSource source) =>
                          controller.replacePhoto(photo.photoId, source),
                    ),
                    const SizedBox(height: 12),
                  ],
                ],
              ),
            ),
    );
  }
}
