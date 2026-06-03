import 'package:facecheck_app/features/admin/admin_repository.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:facecheck_app/shared/models/backend_api_exception.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class AdminUserFormPage extends ConsumerStatefulWidget {
  const AdminUserFormPage({
    super.key,
    this.userId,
    this.initialUsername,
    this.initialRole,
    this.initialStatus,
  });

  final String? userId;
  final String? initialUsername;
  final String? initialRole;
  final String? initialStatus;

  bool get isEditing => userId != null;

  @override
  ConsumerState<AdminUserFormPage> createState() => _AdminUserFormPageState();
}

class _AdminUserFormPageState extends ConsumerState<AdminUserFormPage> {
  late final TextEditingController _usernameController;
  late final TextEditingController _passwordController;
  late String _role;
  late String _status;
  bool _isSaving = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _usernameController = TextEditingController(
      text: widget.initialUsername ?? '',
    );
    _passwordController = TextEditingController();
    _role = widget.initialRole ?? 'USER';
    _status = widget.initialStatus ?? 'ACTIVE';
  }

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_usernameController.text.trim().isEmpty) {
      setState(() {
        _errorMessage = '用户名不能为空。';
      });
      return;
    }
    if (!widget.isEditing && _passwordController.text.trim().length < 8) {
      setState(() {
        _errorMessage = '新建用户时密码至少需要 8 位。';
      });
      return;
    }

    setState(() {
      _isSaving = true;
      _errorMessage = null;
    });

    final repository = ref.read(adminRepositoryProvider);
    try {
      if (widget.isEditing) {
        await repository.updateUser(
          userId: widget.userId!,
          username: _usernameController.text,
          password: _passwordController.text,
          role: _role,
          status: _status,
        );
      } else {
        await repository.createUser(
          username: _usernameController.text,
          password: _passwordController.text,
          role: _role,
        );
      }
      if (!mounted) {
        return;
      }
      Navigator.of(context).pop(true);
    } catch (error) {
      setState(() {
        _isSaving = false;
        _errorMessage = _readableMessage(error);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      key: AppTestKeys.adminUserFormPage,
      appBar: AppBar(
        title: Text(widget.isEditing ? '编辑用户' : '新增用户'),
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
                    widget.isEditing ? '更新用户信息' : '创建新用户',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    widget.isEditing
                        ? '用户名仍是唯一业务标识；留空密码表示保持当前密码不变。'
                        : '当前阶段仅维护用户名、密码和角色，不引入邮箱或手机号。',
                  ),
                  const SizedBox(height: 24),
                  TextField(
                    controller: _usernameController,
                    decoration: const InputDecoration(
                      labelText: '用户名',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: _passwordController,
                    obscureText: true,
                    decoration: InputDecoration(
                      labelText: widget.isEditing ? '新密码（可选）' : '初始密码',
                      border: const OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 16),
                  DropdownButtonFormField<String>(
                    value: _role,
                    decoration: const InputDecoration(
                      labelText: '角色',
                      border: OutlineInputBorder(),
                    ),
                    items: const <DropdownMenuItem<String>>[
                      DropdownMenuItem(value: 'USER', child: Text('普通用户')),
                      DropdownMenuItem(value: 'ADMIN', child: Text('管理员')),
                    ],
                    onChanged: (String? value) {
                      if (value == null) {
                        return;
                      }
                      setState(() {
                        _role = value;
                      });
                    },
                  ),
                  if (widget.isEditing) ...<Widget>[
                    const SizedBox(height: 16),
                    DropdownButtonFormField<String>(
                      value: _status,
                      decoration: const InputDecoration(
                        labelText: '状态',
                        border: OutlineInputBorder(),
                      ),
                      items: const <DropdownMenuItem<String>>[
                        DropdownMenuItem(value: 'ACTIVE', child: Text('启用')),
                        DropdownMenuItem(
                          value: 'DISABLED',
                          child: Text('停用'),
                        ),
                        DropdownMenuItem(value: 'LOCKED', child: Text('锁定')),
                      ],
                      onChanged: (String? value) {
                        if (value == null) {
                          return;
                        }
                        setState(() {
                          _status = value;
                        });
                      },
                    ),
                  ],
                  if (_errorMessage != null) ...<Widget>[
                    const SizedBox(height: 16),
                    Text(
                      _errorMessage!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ],
                  const SizedBox(height: 24),
                  Row(
                    children: <Widget>[
                      OutlinedButton(
                        onPressed: _isSaving
                            ? null
                            : () => Navigator.of(context).maybePop(),
                        child: const Text('取消'),
                      ),
                      const SizedBox(width: 12),
                      FilledButton(
                        onPressed: _isSaving ? null : _save,
                        child: Text(_isSaving ? '保存中...' : '保存'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

String _readableMessage(Object error) {
  if (error is BackendApiException) {
    return error.message;
  }
  return '当前无法保存用户，请稍后重试。';
}
