import 'package:facecheck_app/features/admin/admin_navigation.dart';
import 'package:facecheck_app/features/admin/admin_repository.dart';
import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:facecheck_app/shared/models/backend_api_exception.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class GlobalRecordsPage extends ConsumerStatefulWidget {
  const GlobalRecordsPage({super.key});

  @override
  ConsumerState<GlobalRecordsPage> createState() => _GlobalRecordsPageState();
}

class _GlobalRecordsPageState extends ConsumerState<GlobalRecordsPage> {
  late final TextEditingController _sessionIdController;
  late final TextEditingController _userIdController;
  String _statusFilter = '';
  bool _isLoading = false;
  String? _errorMessage;
  List<AdminAttendanceRecord> _records = const <AdminAttendanceRecord>[];

  @override
  void initState() {
    super.initState();
    _sessionIdController = TextEditingController();
    _userIdController = TextEditingController();
    Future.microtask(_loadRecords);
  }

  @override
  void dispose() {
    _sessionIdController.dispose();
    _userIdController.dispose();
    super.dispose();
  }

  Future<void> _loadRecords() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final records =
          await ref.read(adminRepositoryProvider).fetchGlobalRecords(
                sessionId: _sessionIdController.text,
                userId: _userIdController.text,
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
      pageKey: AppTestKeys.adminGlobalRecordsPage,
      title: '全局签到记录',
      selectedPath: AppRoutePaths.adminRecords,
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
                      '全局记录筛选',
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 8),
                    const Text('可按场次、用户和状态过滤已产生的签到记录。'),
                    const SizedBox(height: 16),
                    TextField(
                      controller: _sessionIdController,
                      decoration: const InputDecoration(
                        labelText: '场次 ID（可选）',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: _userIdController,
                      decoration: const InputDecoration(
                        labelText: '用户 ID（可选）',
                        border: OutlineInputBorder(),
                      ),
                    ),
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
                      child: const Text('筛选记录'),
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
                    child: Text('当前筛选条件下没有签到记录。'),
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
              record.sessionName,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: <Widget>[
                Chip(label: Text('用户：${record.maskedUsername}')),
                Chip(label: Text('状态：${_statusLabel(record.status)}')),
              ],
            ),
            const SizedBox(height: 8),
            Text('签到时间：${_formatDateTime(record.checkinTime)}'),
            Text('用户 ID：${record.userId}'),
            if (record.similarity != null)
              Text('相似度：${record.similarity!.toStringAsFixed(1)}'),
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
  return '当前无法读取签到记录，请稍后重试。';
}
