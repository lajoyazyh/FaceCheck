import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/features/auth/logout_action.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:facecheck_app/shared/providers/session_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(currentSessionProvider);

    if (session == null) {
      return const Scaffold(
        body: Center(
          child: Text('当前没有登录会话'),
        ),
      );
    }

    return Scaffold(
      key: AppTestKeys.homePage,
      appBar: AppBar(
        title: const Text('首页'),
        actions: <Widget>[
          IconButton(
            key: AppTestKeys.logoutButton,
            tooltip: '退出登录',
            onPressed: () => LogoutAction.execute(context, ref),
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: <Widget>[
          Text(
            '欢迎回来，${session.username}',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          Text(
            '当前身份：${session.role.label}',
            style: Theme.of(context).textTheme.bodyLarge,
          ),
          const SizedBox(height: 24),
          _HomeActionCard(
            tileKey: AppTestKeys.anonymousCheckinEntryButton,
            title: '匿名签到',
            subtitle: '扫码进入场次，拍摄一张签到照片，并保持匿名流程与已登录功能隔离。',
            onTap: () => context.push(AppRoutePaths.publicSessionEntry),
          ),
          _HomeActionCard(
            title: '个人资料',
            subtitle: '在应用内安全修改用户名和密码。',
            onTap: () => context.push(AppRoutePaths.profile),
          ),
          _HomeActionCard(
            title: '人脸照片',
            subtitle: '查看照片处理状态，上传新照片，或替换已有照片。',
            onTap: () => context.push(AppRoutePaths.facePhotos),
          ),
          _HomeActionCard(
            title: '签到记录',
            subtitle: '仅查看你自己的签到记录和状态备注。',
            onTap: () => context.push(AppRoutePaths.attendanceRecords),
          ),
          if (session.isAdmin)
            _HomeActionCard(
              title: '管理工作台',
              subtitle: '仅管理员可进入用户、场次、记录与复核功能。',
              onTap: () => context.push(AppRoutePaths.admin),
            ),
        ],
      ),
    );
  }
}

class _HomeActionCard extends StatelessWidget {
  const _HomeActionCard({
    this.tileKey,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final Key? tileKey;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      child: ListTile(
        key: tileKey,
        title: Text(title),
        subtitle: Text(subtitle),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}
