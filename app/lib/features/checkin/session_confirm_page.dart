import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/features/checkin/session_entry_repository.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:facecheck_app/shared/widgets/app_back_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class SessionConfirmPage extends ConsumerStatefulWidget {
  const SessionConfirmPage({
    super.key,
    required this.qrToken,
  });

  final String qrToken;

  @override
  ConsumerState<SessionConfirmPage> createState() => _SessionConfirmPageState();
}

class _SessionConfirmPageState extends ConsumerState<SessionConfirmPage> {
  @override
  void initState() {
    super.initState();
    Future.microtask(
      () => ref
          .read(sessionEntryControllerProvider(widget.qrToken).notifier)
          .loadSession(widget.qrToken),
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(sessionEntryControllerProvider(widget.qrToken));

    return Scaffold(
      key: AppTestKeys.sessionConfirmPage,
      appBar: AppBar(
        leading: const AppBackButton(
          fallbackLocation: AppRoutePaths.publicSessionEntry,
        ),
        title: const Text('确认场次'),
      ),
      body: state.isLoading && state.session == null
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: () => ref
                  .read(sessionEntryControllerProvider(widget.qrToken).notifier)
                  .loadSession(widget.qrToken),
              child: ListView(
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
                              '场次不可用',
                              style: Theme.of(context).textTheme.headlineSmall,
                            ),
                            const SizedBox(height: 12),
                            Text(state.errorMessage!),
                            const SizedBox(height: 16),
                            FilledButton(
                              onPressed: () =>
                                  context.go(AppRoutePaths.publicSessionEntry),
                              child: const Text('重新扫码'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  if (state.session != null) ...<Widget>[
                    _SessionSummaryCard(session: state.session!),
                    const SizedBox(height: 16),
                    if (state.session!.canCheckin)
                      FilledButton.icon(
                        key: AppTestKeys.anonymousCheckinStartButton,
                        onPressed: () => _continueToCapture(state.session!),
                        icon: const Icon(Icons.photo_camera_outlined),
                        label: const Text('开始匿名签到'),
                      )
                    else
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(20),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: <Widget>[
                              Text(
                                '当前无法签到',
                                style: Theme.of(context).textTheme.titleLarge,
                              ),
                              const SizedBox(height: 8),
                              Text(
                                state.session!.refusalReason ??
                                    _fallbackRefusalMessage(
                                      state.session!.refusalCode,
                                    ),
                              ),
                              const SizedBox(height: 16),
                              OutlinedButton(
                                onPressed: () => context
                                    .go(AppRoutePaths.publicSessionEntry),
                                child: const Text('返回扫码'),
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

  void _continueToCapture(SessionEntryDetails session) {
    final encodedToken = Uri.encodeQueryComponent(widget.qrToken);
    final encodedName = Uri.encodeQueryComponent(session.name);
    context.push(
      '${AppRoutePaths.publicCheckinCapture}?qrToken=$encodedToken&sessionName=$encodedName',
    );
  }

  String _fallbackRefusalMessage(String? refusalCode) {
    return switch (refusalCode) {
      'SESSION_NOT_STARTED' => '该场次尚未开始。',
      'EXPIRED_SESSION' => '该场次已经超过结束时间。',
      'SESSION_CLOSED' => '该场次已经关闭。',
      'SESSION_CANCELED' => '该场次已被管理员取消。',
      _ => '当前场次暂时无法进行匿名签到。',
    };
  }
}

class _SessionSummaryCard extends StatelessWidget {
  const _SessionSummaryCard({
    required this.session,
  });

  final SessionEntryDetails session;

  @override
  Widget build(BuildContext context) {
    final statusColor = session.canCheckin
        ? Theme.of(context).colorScheme.primary
        : Theme.of(context).colorScheme.error;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(
              session.name,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            if (session.description != null &&
                session.description!.isNotEmpty) ...<Widget>[
              const SizedBox(height: 8),
              Text(session.description!),
            ],
            const SizedBox(height: 16),
            Text('状态：${_statusLabel(session.status)}'),
            const SizedBox(height: 4),
            Text('开始时间：${_formatDateTime(session.startTime)}'),
            const SizedBox(height: 4),
            Text('结束时间：${_formatDateTime(session.endTime)}'),
            const SizedBox(height: 16),
            Text(
              session.canCheckin
                  ? '可以继续进入匿名拍照步骤。'
                  : (session.refusalReason ?? '当前场次未开放匿名签到。'),
              style: TextStyle(color: statusColor),
            ),
          ],
        ),
      ),
    );
  }

  String _statusLabel(String status) {
    return switch (status.toUpperCase()) {
      'DRAFT' => '草稿',
      'PUBLISHED' => '已发布',
      'CLOSED' => '已关闭',
      'CANCELED' => '已取消',
      _ => status,
    };
  }

  String _formatDateTime(DateTime value) {
    final local = value.toLocal();
    String two(int n) => n.toString().padLeft(2, '0');
    return '${local.year}-${two(local.month)}-${two(local.day)} '
        '${two(local.hour)}:${two(local.minute)}';
  }
}
