import 'package:facecheck_app/features/auth/access_policy.dart';
import 'package:facecheck_app/shared/widgets/app_back_button.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class AdminNavigationItem {
  const AdminNavigationItem({
    required this.label,
    required this.path,
    required this.icon,
    this.extensionStage = false,
  });

  final String label;
  final String path;
  final IconData icon;
  final bool extensionStage;
}

class AdminNavigation extends StatelessWidget {
  const AdminNavigation({
    super.key,
    required this.title,
    required this.selectedPath,
    required this.body,
    this.pageKey,
    this.actions = const <Widget>[],
    this.floatingActionButton,
  });

  final String title;
  final String selectedPath;
  final Widget body;
  final Key? pageKey;
  final List<Widget> actions;
  final Widget? floatingActionButton;

  static final List<AdminNavigationItem> items = <AdminNavigationItem>[
    const AdminNavigationItem(
      label: '管理总览',
      path: AppRoutePaths.admin,
      icon: Icons.dashboard_outlined,
    ),
    const AdminNavigationItem(
      label: '用户管理',
      path: AppRoutePaths.adminUsers,
      icon: Icons.manage_accounts_outlined,
    ),
    const AdminNavigationItem(
      label: '场次管理',
      path: AppRoutePaths.adminSessions,
      icon: Icons.event_note_outlined,
    ),
    const AdminNavigationItem(
      label: '全局记录',
      path: AppRoutePaths.adminRecords,
      icon: Icons.fact_check_outlined,
    ),
    const AdminNavigationItem(
      label: '异常复核',
      path: AppRoutePaths.adminReview,
      icon: Icons.rule_folder_outlined,
    ),
    const AdminNavigationItem(
      label: '系统状态',
      path: AppRoutePaths.adminSystemState,
      icon: Icons.monitor_heart_outlined,
      extensionStage: true,
    ),
    const AdminNavigationItem(
      label: '系统配置',
      path: AppRoutePaths.adminSystemConfig,
      icon: Icons.tune_outlined,
      extensionStage: true,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final navigationItems = items;
    final selectedIndex = navigationItems.indexWhere(
      (AdminNavigationItem item) => _matches(item.path, selectedPath),
    );

    final appBar = AppBar(
      title: Text(title),
      actions: <Widget>[
        const AppBackButton(fallbackLocation: AppRoutePaths.home),
        IconButton(
          tooltip: '返回首页',
          onPressed: () => context.go(AppRoutePaths.home),
          icon: const Icon(Icons.home_outlined),
        ),
        ...actions,
      ],
    );

    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        if (constraints.maxWidth < 960) {
          return Scaffold(
            key: pageKey,
            appBar: appBar,
            drawer: Drawer(
              child: SafeArea(
                child: _AdminDrawer(
                  selectedPath: selectedPath,
                  items: navigationItems,
                ),
              ),
            ),
            floatingActionButton: floatingActionButton,
            body: body,
          );
        }

        return Scaffold(
          key: pageKey,
          appBar: appBar,
          floatingActionButton: floatingActionButton,
          body: Row(
            children: <Widget>[
              NavigationRail(
                selectedIndex: selectedIndex < 0 ? 0 : selectedIndex,
                labelType: NavigationRailLabelType.all,
                onDestinationSelected: (int index) {
                  context.go(navigationItems[index].path);
                },
                destinations: navigationItems
                    .map(
                      (AdminNavigationItem item) => NavigationRailDestination(
                        icon: Icon(item.icon),
                        label: Text(
                          item.extensionStage
                              ? '${item.label}\n(扩展)'
                              : item.label,
                          textAlign: TextAlign.center,
                        ),
                      ),
                    )
                    .toList(),
              ),
              const VerticalDivider(width: 1),
              Expanded(child: body),
            ],
          ),
        );
      },
    );
  }

  static bool _matches(String itemPath, String currentPath) {
    if (itemPath == AppRoutePaths.admin) {
      return currentPath == AppRoutePaths.admin;
    }
    return currentPath == itemPath || currentPath.startsWith('$itemPath/');
  }
}

class _AdminDrawer extends StatelessWidget {
  const _AdminDrawer({
    required this.selectedPath,
    required this.items,
  });

  final String selectedPath;
  final List<AdminNavigationItem> items;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: EdgeInsets.zero,
      children: <Widget>[
        const DrawerHeader(
          child: Align(
            alignment: Alignment.bottomLeft,
            child: Text(
              '管理员工作台',
              style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ),
        for (final item in items)
          ListTile(
            leading: Icon(item.icon),
            title: Text(
              item.extensionStage ? '${item.label}（扩展）' : item.label,
            ),
            selected: AdminNavigation._matches(item.path, selectedPath),
            onTap: () {
              Navigator.of(context).pop();
              context.go(item.path);
            },
          ),
      ],
    );
  }
}
