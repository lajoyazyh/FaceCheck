import 'package:flutter/foundation.dart';

const String _publicBackendBaseUrl = 'https://115.120.241.220';

const String _baseUrlOverride = String.fromEnvironment(
  'FACECHECK_BASE_URL',
  defaultValue: '',
);
const String _localBackendHostsOverride = String.fromEnvironment(
  'FACECHECK_LOCAL_BACKEND_HOSTS',
  defaultValue: '',
);

class AppEnv {
  const AppEnv({
    required this.baseUrl,
    required this.localBackendHosts,
  });

  final String baseUrl;
  final List<String> localBackendHosts;

  factory AppEnv.current() {
    return AppEnv.resolve(
      isWeb: kIsWeb,
      platform: defaultTargetPlatform,
      baseUrlOverride: _baseUrlOverride,
      localBackendHostsOverride: _localBackendHostsOverride,
    );
  }

  factory AppEnv.resolve({
    required bool isWeb,
    required TargetPlatform platform,
    String baseUrlOverride = '',
    String localBackendHostsOverride = '',
  }) {
    final normalizedBaseUrl = baseUrlOverride.trim();
    if (normalizedBaseUrl.isNotEmpty) {
      return AppEnv(
        baseUrl: normalizedBaseUrl,
        localBackendHosts: _collectHosts(
          normalizedBaseUrl,
          localBackendHostsOverride,
        ),
      );
    }

    if (isWeb) {
      return const AppEnv(
        baseUrl: 'http://localhost:8080',
        localBackendHosts: <String>['localhost'],
      );
    }

    switch (platform) {
      case TargetPlatform.android:
        return const AppEnv(
          baseUrl: _publicBackendBaseUrl,
          localBackendHosts: <String>['115.120.241.220'],
        );
      default:
        return const AppEnv(
          baseUrl: _publicBackendBaseUrl,
          localBackendHosts: <String>['115.120.241.220'],
        );
    }
  }

  static List<String> _collectHosts(
    String baseUrl,
    String localBackendHostsOverride,
  ) {
    final hosts = <String>{};
    final uri = Uri.tryParse(baseUrl);
    final parsedHost = uri?.host.trim() ?? '';
    if (parsedHost.isNotEmpty) {
      hosts.add(parsedHost);
    }
    for (final host in localBackendHostsOverride.split(',')) {
      final normalized = host.trim();
      if (normalized.isNotEmpty) {
        hosts.add(normalized);
      }
    }
    if (hosts.isEmpty) {
      hosts.addAll(<String>['127.0.0.1', 'localhost']);
    }
    return hosts.toList(growable: false);
  }
}
