import 'package:facecheck_app/features/admin/admin_navigation.dart';
import 'package:facecheck_app/features/admin/admin_repository.dart';
import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:facecheck_app/shared/models/backend_api_exception.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class SessionRecordsPage extends ConsumerStatefulWidget {
  const SessionRecordsPage({
    super.key,
    required this.sessionId,
    required this.sessionName,
  });

  final String sessionId;
  final String sessionName;

  @override
  ConsumerState<SessionRecordsPage> createState() => _SessionRecordsPageState();
}

class _SessionRecordsPageState extends ConsumerState<SessionRecordsPage> {
  String _statusFilter = '';
  bool _isLoading = false;
  String? _errorMessage;
  List<AdminAttendanceRecord> _records = const <AdminAttendanceRecord>[];

  @override
  void initState() {
    super.initState();
    Future.microtask(_loadRecords);
  }

  Future<void> _loadRecords() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final records =
          await ref.read(adminRepositoryProvider).fetchSessionRecords(
                widget.sessionId,
                status: _statusFilter,
              );
      if (!mounted) {
        return;
      }
      setState(() {
        _records = records;
        _isLoading = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _errorMessage = _readableMessage(error);
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AdminNavigation(
      pageKey: AppTestKeys.adminSessionRecordsPage,
      title: '场次签到记录',
      selectedPath: AppRoutePaths.adminSessions,
      body: RefreshIndicator(
        onRefresh: _loadRecords,
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
                      widget.sessionName.isEmpty ? '场次记录' : widget.sessionName,
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 8),
                    Text('场次 ID：${widget.sessionId}'),
                    const SizedBox(height: 16),
                    DropdownButtonFormField<String>(
                      value: _statusFilter,
                      decoration: const InputDecoration(
                        labelText: '状态',
                        border: OutlineInputBorder(),
                      ),
                      items: const <DropdownMenuItem<String>>[
                        DropdownMenuItem(value: '', child: Text('全部')),
                        DropdownMenuItem(value: 'VALID', child: Text('有效')),
                        DropdownMenuItem(
                          value: 'MANUAL_CONFIRMED',
                          child: Text('人工确认'),
                        ),
                      ],
                      onChanged: (String? value) {
                        setState(() {
                          _statusFilter = value ?? '';
                        });
                      },
                    ),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: _loadRecords,
                      child: const Text('刷新场次记录'),
                    ),
                  ],
                ),
              ),
            ),
            if (_errorMessage != null)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Text(
                  _errorMessage!,
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.error,
                  ),
                ),
              ),
            if (_isLoading && _records.isEmpty)
              const Padding(
                padding: EdgeInsets.only(top: 48),
                child: Center(child: CircularProgressIndicator()),
              ),
            if (!_isLoading && _records.isEmpty)
              const Padding(
                padding: EdgeInsets.only(top: 16),
                child: Card(
                  child: Padding(
                    padding: EdgeInsets.all(20),
                    child: Text('该场次目前没有签到记录。'),
                  ),
                ),
              ),
            for (final record in _records) ...<Widget>[
              const SizedBox(height: 16),
              _RecordCard(record: record),
            ],
          ],
        ),
      ),
    );
  }
}

class _RecordCard extends StatelessWidget {
  const _RecordCard({required this.record});

  final AdminAttendanceRecord record;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(
              record.maskedUsername,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: <Widget>[
                Chip(label: Text('状态：${_statusLabel(record.status)}')),
                if (record.similarity != null)
                  Chip(
                      label:
                          Text('相似度：${record.similarity!.toStringAsFixed(1)}')),
              ],
            ),
            const SizedBox(height: 8),
            Text('签到时间：${_formatDateTime(record.checkinTime)}'),
            Text('记录 ID：${record.recordId}'),
          ],
        ),
      ),
    );
  }
}

String _statusLabel(String value) {
  return switch (value.toUpperCase()) {
    'MANUAL_CONFIRMED' => '人工确认',
    _ => '有效',
  };
}

String _formatDateTime(DateTime value) {
  final local = value.toLocal();
  String two(int number) => number.toString().padLeft(2, '0');
  return '${local.year}-${two(local.month)}-${two(local.day)} '
      '${two(local.hour)}:${two(local.minute)}';
}

String _readableMessage(Object error) {
  if (error is BackendApiException) {
    return error.message;
  }
  return '当前无法读取场次记录，请稍后重试。';
}
