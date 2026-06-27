import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/features/checkin/checkin_repository.dart';
import 'package:facecheck_app/features/checkin/checkin_result_controller.dart';
import 'package:facecheck_app/shared/widgets/app_back_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class CheckinResultPage extends ConsumerStatefulWidget {
  const CheckinResultPage({
    super.key,
    required this.attemptId,
    required this.qrToken,
  });

  final String attemptId;
  final String qrToken;

  @override
  ConsumerState<CheckinResultPage> createState() => _CheckinResultPageState();
}

class _CheckinResultPageState extends ConsumerState<CheckinResultPage> {
  @override
  void initState() {
    super.initState();
    Future.microtask(
      () => ref
          .read(checkinResultControllerProvider(_lookup).notifier)
          .start(attemptId: widget.attemptId, qrToken: widget.qrToken),
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(checkinResultControllerProvider(_lookup));
    final controller = ref.read(
      checkinResultControllerProvider(_lookup).notifier,
    );

    return Scaffold(
      appBar: AppBar(
        leading: const AppBackButton(
          fallbackLocation: AppRoutePaths.publicSessionEntry,
        ),
        title: const Text('签到结果'),
      ),
      body: state.isLoading && state.attempt == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(24),
              children: <Widget>[
                if (state.errorMessage != null)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text(
                            '无法加载本次签到结果',
                            style: Theme.of(context).textTheme.headlineSmall,
                          ),
                          const SizedBox(height: 12),
                          Text(state.errorMessage!),
                          const SizedBox(height: 16),
                          Wrap(
                            spacing: 12,
                            runSpacing: 12,
                            children: <Widget>[
                              FilledButton(
                                onPressed: controller.refresh,
                                child: const Text('重试'),
                              ),
                              OutlinedButton(
                                onPressed: () => context
                                    .go(AppRoutePaths.publicSessionEntry),
                                child: const Text('重新扫码'),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                if (state.attempt != null)
                  _ResultSummaryCard(
                    attempt: state.attempt!,
                    onRefresh: controller.refresh,
                  ),
              ],
            ),
    );
  }

  CheckinResultLookup get _lookup => CheckinResultLookup(
        attemptId: widget.attemptId,
        qrToken: widget.qrToken,
      );
}

class _ResultSummaryCard extends StatelessWidget {
  const _ResultSummaryCard({
    required this.attempt,
    required this.onRefresh,
  });

  final CheckinAttemptSummary attempt;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    final presentation = CheckinResultPresentation.fromAttempt(attempt);
    final colorScheme = Theme.of(context).colorScheme;
    final color = switch (presentation.tone) {
      ResultTone.success => Colors.green.shade700,
      ResultTone.warning => Colors.orange.shade700,
      ResultTone.processing => colorScheme.primary,
      ResultTone.failure => colorScheme.error,
    };

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Icon(presentation.icon, size: 36, color: color),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        presentation.title,
                        style: Theme.of(context).textTheme.headlineSmall,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        presentation.message,
                        style: TextStyle(color: color),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            Text('场次：${attempt.sessionName}'),
            Text('尝试编号：${attempt.attemptId}'),
            Text(
                '处理状态：${CheckinResultPresentation.statusLabel(attempt.status)}'),
            Text('结果：${CheckinResultPresentation.resultCodeLabel(attempt)}'),
            if (attempt.checkinTime != null)
              Text('签到时间：${_formatDateTime(attempt.checkinTime!)}'),
            if (attempt.maskedUsername != null &&
                attempt.maskedUsername!.isNotEmpty)
              Text('匹配用户：${attempt.maskedUsername}'),
            const SizedBox(height: 16),
            const Text(
              '匿名流程不会开放个人资料、人脸照片或个人签到记录。',
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: <Widget>[
                if (attempt.isProcessing)
                  FilledButton(
                    onPressed: onRefresh,
                    child: const Text('立即刷新'),
                  ),
                OutlinedButton(
                  onPressed: () => context.go(AppRoutePaths.publicSessionEntry),
                  child: const Text('重新扫码'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

enum ResultTone { success, warning, processing, failure }

class CheckinResultPresentation {
  const CheckinResultPresentation({
    required this.icon,
    required this.title,
    required this.message,
    required this.tone,
  });

  final IconData icon;
  final String title;
  final String message;
  final ResultTone tone;

  factory CheckinResultPresentation.fromAttempt(CheckinAttemptSummary attempt) {
    if (attempt.status == 'PROCESSING') {
      return CheckinResultPresentation(
        icon: Icons.hourglass_top,
        title: '仍在处理中',
        message: attempt.resultMessage.isEmpty
            ? '你的匿名签到仍在处理中。'
            : attempt.resultMessage,
        tone: ResultTone.processing,
      );
    }

    if (attempt.status == 'SUCCESS') {
      return CheckinResultPresentation(
        icon: Icons.verified_outlined,
        title: '签到成功',
        message: attempt.resultMessage.isEmpty
            ? '已成功确认你的签到结果。'
            : attempt.resultMessage,
        tone: ResultTone.success,
      );
    }

    if (attempt.status == 'DUPLICATE_CHECKIN' ||
        attempt.resultCode == 'DUPLICATE_CHECKIN') {
      return CheckinResultPresentation(
        icon: Icons.assignment_turned_in_outlined,
        title: '已完成签到',
        message: attempt.resultMessage.isEmpty
            ? '当前用户已经完成本场次签到。'
            : attempt.resultMessage,
        tone: ResultTone.warning,
      );
    }

    return CheckinResultPresentation(
      icon: Icons.error_outline,
      title: _failureTitleFor(attempt.resultCode),
      message: attempt.resultMessage.isEmpty
          ? _failureMessageFor(attempt.resultCode)
          : attempt.resultMessage,
      tone: ResultTone.failure,
    );
  }

  static String _failureTitleFor(String resultCode) {
    return switch (resultCode) {
      'SESSION_NOT_STARTED' => '场次未开始',
      'EXPIRED_SESSION' => '场次已过期',
      'SESSION_CLOSED' => '场次已关闭',
      'SESSION_CANCELED' => '场次已取消',
      'INVALID_QR_TOKEN' => '二维码无效',
      'RATE_LIMITED' => '尝试过于频繁',
      'NO_FACE' => '未检测到人脸',
      'MULTIPLE_FACES' => '检测到多张人脸',
      'LOW_CONFIDENCE' => '人脸匹配度过低',
      'INVALID_IMAGE' => '图片无效',
      'FRS_TIMEOUT' => '识别超时',
      'FRS_RATE_LIMITED' => '识别限流',
      'FRS_ERROR' => '识别服务不可用',
      _ => '签到失败',
    };
  }

  static String _failureMessageFor(String resultCode) {
    return switch (resultCode) {
      'SESSION_NOT_STARTED' => '该场次尚未开放，请到开始时间后再试。',
      'EXPIRED_SESSION' => '该场次已经超过结束时间。',
      'SESSION_CLOSED' => '管理员已经关闭该场次。',
      'SESSION_CANCELED' => '该场次已被取消，不再接受匿名签到。',
      'INVALID_QR_TOKEN' => '二维码无效或已过期，请获取新的场次二维码。',
      'RATE_LIMITED' => '匿名签到提交过于频繁，请稍后再试。',
      'NO_FACE' => '未检测到清晰人脸，请重新拍摄并确保画面中只有一人。',
      'MULTIPLE_FACES' => '检测到多张人脸，请确保画面中只有一人。',
      'LOW_CONFIDENCE' => '人脸匹配度过低，请尝试更清晰的照片。',
      'INVALID_IMAGE' => '上传的图片无法使用，请更换照片。',
      'FRS_TIMEOUT' => '人脸识别请求超时，请重试一次。',
      'FRS_RATE_LIMITED' => '识别服务正在限流，请稍后重试。',
      'FRS_ERROR' => '识别服务返回错误，请稍后再试。',
      _ => '匿名签到未能完成。',
    };
  }

  static String statusLabel(String status) {
    return switch (status.toUpperCase()) {
      'PROCESSING' => '处理中',
      'SUCCESS' => '成功',
      'FAILED' => '失败',
      'DUPLICATE_CHECKIN' => '重复签到',
      _ => status,
    };
  }

  static String resultCodeLabel(CheckinAttemptSummary attempt) {
    if (attempt.status == 'PROCESSING') {
      return '处理中';
    }
    if (attempt.status == 'SUCCESS') {
      return '签到成功';
    }
    if (attempt.status == 'DUPLICATE_CHECKIN' ||
        attempt.resultCode == 'DUPLICATE_CHECKIN') {
      return '重复签到';
    }
    return _failureTitleFor(attempt.resultCode);
  }
}

String _formatDateTime(DateTime value) {
  final local = value.toLocal();
  String two(int n) => n.toString().padLeft(2, '0');
  return '${local.year}-${two(local.month)}-${two(local.day)} '
      '${two(local.hour)}:${two(local.minute)}';
}
