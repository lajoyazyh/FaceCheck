import 'package:facecheck_app/shared/config/app_env.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AppEnv.resolve', () {
    test('uses public backend by default on Android', () {
      final env = AppEnv.resolve(
        isWeb: false,
        platform: TargetPlatform.android,
      );

      expect(env.baseUrl, 'https://115.120.241.220');
      expect(env.localBackendHosts, contains('115.120.241.220'));
    });

    test('uses localhost by default on web', () {
      final env = AppEnv.resolve(
        isWeb: true,
        platform: TargetPlatform.android,
      );

      expect(env.baseUrl, 'http://localhost:8080');
      expect(env.localBackendHosts, contains('localhost'));
    });

    test('accepts an explicit backend override for real devices', () {
      final env = AppEnv.resolve(
        isWeb: false,
        platform: TargetPlatform.android,
        baseUrlOverride: 'http://192.168.1.23:8080',
        localBackendHostsOverride: '192.168.1.23,host.docker.internal',
      );

      expect(env.baseUrl, 'http://192.168.1.23:8080');
      expect(
          env.localBackendHosts,
          containsAll(<String>[
            '192.168.1.23',
            'host.docker.internal',
          ]));
    });
  });
}
