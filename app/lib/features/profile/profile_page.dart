import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/features/profile/profile_controller.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:facecheck_app/shared/widgets/app_back_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class ProfilePage extends ConsumerStatefulWidget {
  const ProfilePage({super.key});

  @override
  ConsumerState<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends ConsumerState<ProfilePage> {
  late final TextEditingController _usernameController;
  late final TextEditingController _passwordController;
  bool _didSeedUsername = false;

  @override
  void initState() {
    super.initState();
    _usernameController = TextEditingController();
    _passwordController = TextEditingController();
    Future.microtask(
      () => ref.read(profileControllerProvider.notifier).loadProfile(),
    );
  }

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final success =
        await ref.read(profileControllerProvider.notifier).saveProfile(
              username: _usernameController.text,
              password: _passwordController.text,
            );
    if (success && mounted) {
      _passwordController.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(profileControllerProvider);
    final profile = state.profile;

    if (!_didSeedUsername && profile != null) {
      _usernameController.text = profile.username;
      _didSeedUsername = true;
    }

    return Scaffold(
      key: AppTestKeys.userProfilePage,
      appBar: AppBar(
        leading: const AppBackButton(fallbackLocation: AppRoutePaths.home),
        title: const Text('个人资料'),
      ),
      body: state.isLoading && profile == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(24),
              children: <Widget>[
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(
                          '账户设置',
                          style: Theme.of(context).textTheme.headlineSmall,
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          '当前阶段仅支持修改用户名和密码，不展示额外资料字段。',
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
                          decoration: const InputDecoration(
                            labelText: '新密码',
                            hintText: '留空则保持当前密码不变',
                            border: OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 16),
                        if (profile != null)
                          Wrap(
                            spacing: 12,
                            runSpacing: 12,
                            children: <Widget>[
                              Chip(
                                  label:
                                      Text('角色：${_roleLabel(profile.role)}')),
                              Chip(
                                  label: Text(
                                      '状态：${_statusLabel(profile.status)}')),
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
                        const SizedBox(height: 24),
                        FilledButton(
                          onPressed: state.isSaving ? null : _save,
                          child: Text(
                            state.isSaving ? '保存中...' : '保存',
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Card(
                  child: Column(
                    children: <Widget>[
                      ListTile(
                        title: const Text('人脸照片'),
                        subtitle: const Text(
                          '管理最多五张启用的人脸照片，并查看处理状态。',
                        ),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => context.push(AppRoutePaths.facePhotos),
                      ),
                      const Divider(height: 1),
                      ListTile(
                        title: const Text('签到记录'),
                        subtitle: const Text(
                          '仅查看你自己的签到记录和每个场次的结果备注。',
                        ),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () =>
                            context.push(AppRoutePaths.attendanceRecords),
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  String _roleLabel(String value) {
    return switch (value.toUpperCase()) {
      'ADMIN' => '管理员',
      'USER' => '用户',
      _ => value,
    };
  }

  String _statusLabel(String value) {
    return switch (value.toUpperCase()) {
      'ACTIVE' => '启用',
      'DISABLED' => '停用',
      'LOCKED' => '锁定',
      _ => value,
    };
  }
}
