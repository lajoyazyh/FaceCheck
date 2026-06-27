import 'package:facecheck_app/features/admin/admin_navigation.dart';
import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/shared/config/app_test_keys.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class AdminHomePage extends StatelessWidget {
  const AdminHomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return AdminNavigation(
      pageKey: AppTestKeys.adminWorkspacePage,
      title: '管理工作台',
      selectedPath: AppRoutePaths.admin,
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: <Widget>[
          Text(
            '管理员第一阶段能力',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          const Text(
            '可管理用户、签到场次、二维码、全局记录和异常复核；系统状态与系统配置作为第二阶段扩展入口保留在这里。',
          ),
          const SizedBox(height: 24),
          _AdminQuickAction(
            title: '用户管理',
            subtitle: '新增、编辑、停用用户，并保持用户名作为唯一业务标识。',
            onTap: () => context.push(AppRoutePaths.adminUsers),
          ),
          _AdminQuickAction(
            title: '场次管理',
            subtitle: '创建、编辑、发布、关闭、取消场次，并查看二维码入口。',
            onTap: () => context.push(AppRoutePaths.adminSessions),
          ),
          _AdminQuickAction(
            title: '全局记录',
            subtitle: '按场次、用户和状态查看已签到记录。',
            onTap: () => context.push(AppRoutePaths.adminRecords),
          ),
          _AdminQuickAction(
            title: '异常复核',
            subtitle: '查看异常签到尝试、补充复核备注，并触发安全重试。',
            onTap: () => context.push(AppRoutePaths.adminReview),
          ),
          const SizedBox(height: 8),
          Text(
            '第二阶段扩展',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 12),
          _AdminQuickAction(
            title: '系统状态',
            subtitle: '查看数据库、Redis、RabbitMQ、FRS 和 OBS 的健康摘要。',
            onTap: () => context.push(AppRoutePaths.adminSystemState),
          ),
          _AdminQuickAction(
            title: '系统配置',
            subtitle: '维护白名单配置项，例如阈值、限流和文件大小限制。',
            onTap: () => context.push(AppRoutePaths.adminSystemConfig),
          ),
        ],
      ),
    );
  }
}

class _AdminQuickAction extends StatelessWidget {
  const _AdminQuickAction({
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      child: ListTile(
        title: Text(title),
        subtitle: Text(subtitle),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}
