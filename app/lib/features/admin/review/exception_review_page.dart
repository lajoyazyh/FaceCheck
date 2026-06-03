import 'package:facecheck_app/features/admin/admin_navigation.dart';
import 'package:facecheck_app/features/admin/admin_repository.dart';
import 'package:facecheck_app/features/admin/review/exception_review_controller.dart';
import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ExceptionReviewPage extends ConsumerStatefulWidget {
  const ExceptionReviewPage({super.key});

  @override
  ConsumerState<ExceptionReviewPage> createState() =>
      _ExceptionReviewPageState();
}

class _ExceptionReviewPageState extends ConsumerState<ExceptionReviewPage> {
  late final TextEditingController _resultCodeController;
  late final TextEditingController _sessionIdController;
  String _statusFilter = '';
  String _reviewedFilter = '';

  @override
  void initState() {
    super.initState();
    _resultCodeController = TextEditingController();
    _sessionIdController = TextEditingController();
    Future.microtask(_loadAttempts);
  }

  @override
  void dispose() {
    _resultCodeController.dispose();
    _sessionIdController.dispose();
    super.dispose();
  }

  Future<void> _loadAttempts() {
    return ref.read(exceptionReviewControllerProvider.notifier).loadAttempts(
          status: _statusFilter,
          resultCode: _resultCodeController.text,
          sessionId: _sessionIdController.text,
          reviewed: switch (_reviewedFilter) {
            'true' => true,
            'false' => false,
            _ => null,
          },
        );
  }

  Future<void> _openReviewDialog(AdminCheckinAttempt attempt) async {
    final noteController = TextEditingController(text: attempt.reviewNote);
    bool reviewed = attempt.reviewed;

    final submitted = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('复核异常尝试'),
          content: StatefulBuilder(
            builder: (BuildContext context, StateSetter setDialogState) {
              return SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    TextField(
                      controller: noteController,
                      minLines: 2,
                      maxLines: 4,
                      decoration: const InputDecoration(
                        labelText: '复核备注',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 16),
                    CheckboxListTile(
                      value: reviewed,
                      contentPadding: EdgeInsets.zero,
                      title: const Text('标记为已复核'),
                      onChanged: (bool? value) {
                        setDialogState(() {
                          reviewed = value ?? false;
                        });
                      },
                    ),
                  ],
                ),
              );
            },
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('保存'),
            ),
          ],
        );
      },
    );

    if (submitted == true) {
      await ref.read(exceptionReviewControllerProvider.notifier).reviewAttempt(
            attemptId: attempt.attemptId,
            note: noteController.text,
            reviewed: reviewed,
          );
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(exceptionReviewControllerProvider);

    return AdminNavigation(
      pageKey: AppTestKeys.adminExceptionReviewPage,
      title: '异常复核',
      selectedPath: AppRoutePaths.adminReview,
      body: RefreshIndicator(
        onRefresh: _loadAttempts,
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
                      '异常尝试筛选',
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      '可按状态、结果码、场次和是否已复核筛选异常签到尝试。',
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: _resultCodeController,
                      decoration: const InputDecoration(
                        labelText: '结果码（可选）',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: _sessionIdController,
                      decoration: const InputDecoration(
                        labelText: '场次 ID（可选）',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 16),
                    DropdownButtonFormField<String>(
                      value: _statusFilter,
                      decoration: const InputDecoration(
                        labelText: '主状态',
                        border: OutlineInputBorder(),
                      ),
                      items: const <DropdownMenuItem<String>>[
                        DropdownMenuItem(value: '', child: Text('全部')),
                        DropdownMenuItem(
                          value: 'FAILED',
                          child: Text('失败'),
                        ),
                        DropdownMenuItem(
                          value: 'DUPLICATE_CHECKIN',
                          child: Text('重复签到'),
                        ),
                        DropdownMenuItem(
                          value: 'PROCESSING',
                          child: Text('处理中'),
                        ),
                      ],
                      onChanged: (String? value) {
                        setState(() {
                          _statusFilter = value ?? '';
                        });
                      },
                    ),
                    const SizedBox(height: 16),
                    DropdownButtonFormField<String>(
                      value: _reviewedFilter,
                      decoration: const InputDecoration(
                        labelText: '复核状态',
                        border: OutlineInputBorder(),
                      ),
                      items: const <DropdownMenuItem<String>>[
                        DropdownMenuItem(value: '', child: Text('全部')),
                        DropdownMenuItem(value: 'false', child: Text('未复核')),
                        DropdownMenuItem(value: 'true', child: Text('已复核')),
                      ],
                      onChanged: (String? value) {
                        setState(() {
                          _reviewedFilter = value ?? '';
                        });
                      },
                    ),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: _loadAttempts,
                      child: const Text('刷新异常列表'),
                    ),
                  ],
                ),
              ),
            ),
            if (state.errorMessage != null)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Text(
                  state.errorMessage!,
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.error,
                  ),
                ),
              ),
            if (state.isLoading && state.attempts.isEmpty)
              const Padding(
                padding: EdgeInsets.only(top: 48),
                child: Center(child: CircularProgressIndicator()),
              ),
            if (!state.isLoading && state.attempts.isEmpty)
              const Padding(
                padding: EdgeInsets.only(top: 16),
                child: Card(
                  child: Padding(
                    padding: EdgeInsets.all(20),
                    child: Text('当前筛选条件下没有异常尝试。'),
                  ),
                ),
              ),
            for (final attempt in state.attempts) ...<Widget>[
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        attempt.sessionName,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: <Widget>[
                          Chip(
                              label:
                                  Text('状态：${_statusLabel(attempt.status)}')),
                          Chip(label: Text('结果码：${attempt.resultCode}')),
                          Chip(label: Text(attempt.reviewed ? '已复核' : '未复核')),
                        ],
                      ),
                      const SizedBox(height: 8),
                      if ((attempt.maskedUsername ?? '').isNotEmpty)
                        Text('候选用户：${attempt.maskedUsername}'),
                      Text('提交时间：${_formatDateTime(attempt.createdAt)}'),
                      if (attempt.resultMessage.isNotEmpty)
                        Text('说明：${attempt.resultMessage}'),
                      if (attempt.reviewNote.isNotEmpty)
                        Text('复核备注：${attempt.reviewNote}'),
                      Text('重试次数：${attempt.retryCount}'),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 12,
                        runSpacing: 12,
                        children: <Widget>[
                          OutlinedButton(
                            onPressed: state.isSaving
                                ? null
                                : () => _openReviewDialog(attempt),
                            child: const Text('复核'),
                          ),
                          FilledButton.tonal(
                            onPressed: state.isSaving
                                ? null
                                : () => ref
                                    .read(
                                      exceptionReviewControllerProvider
                                          .notifier,
                                    )
                                    .retryAttempt(attempt.attemptId),
                            child: const Text('重试'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _statusLabel(String value) {
    return switch (value.toUpperCase()) {
      'PROCESSING' => '处理中',
      'DUPLICATE_CHECKIN' => '重复签到',
      'SUCCESS' => '成功',
      _ => '失败',
    };
  }

  String _formatDateTime(DateTime value) {
    final local = value.toLocal();
    String two(int number) => number.toString().padLeft(2, '0');
    return '${local.year}-${two(local.month)}-${two(local.day)} '
        '${two(local.hour)}:${two(local.minute)}';
  }
}
