# Quickstart: 管理员场次人脸签到系统

## 1. Prerequisites

- Java 21 baseline
- Flutter stable + Dart 3.x
- Docker Desktop
- Windows 端 PowerShell
- Android Emulator 或真机

## 2. Local Defaults

本地默认配置与 [.env.example](/mnt/c/Users/alvinding/desktop/FaceCheck/.env.example:1) 保持一致：

- PostgreSQL：`facecheck/facecheck@localhost:5432/facecheck`
- Redis：`localhost:26379`
- RabbitMQ：`facecheck/facecheck@localhost:5672`
- JWT：本地示例 secret
- `HUAWEI_CLOUD_ENABLED=false`

`local` profile 还会自动：

- 启用本地 RabbitMQ listener
- 额外加载 `classpath:db/local`
- 仅在 `local` profile 写入本地 seed 数据
- 提供 `admin / Admin123!`、`alice / user123!!`

## 3. Start Local Infra

```powershell
docker compose up -d postgres redis rabbitmq
docker compose ps
```

## 4. Start Backend

```powershell
cd C:\Users\alvinding\Desktop\FaceCheck\backend
.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
curl.exe http://localhost:8080/api/health
```

如果需要从零重建本地 seed 数据，先重置开发库，再重新启动后端：

```powershell
docker compose down -v
docker compose up -d postgres redis rabbitmq
```

## 5. Start Flutter App

Flutter App 默认连接已部署后端 `http://115.120.241.220:8080`：

```powershell
cd C:\Users\alvinding\Desktop\FaceCheck\app
C:\Users\alvinding\flutter\bin\flutter.bat run -d <emulatorDeviceId>
```

如果要让 Android Emulator 连接本机后端，显式覆盖 base URL：

```powershell
C:\Users\alvinding\flutter\bin\flutter.bat run `
  -d <emulatorDeviceId> `
  --dart-define=FACECHECK_BASE_URL=http://10.0.2.2:8080 `
  --dart-define=FACECHECK_LOCAL_BACKEND_HOSTS=10.0.2.2,127.0.0.1,localhost
```

如果模拟器直连 `10.0.2.2:8080` 不稳定，或 Android smoke 预检出现 `Connection closed before full header was received`，可切到 Phase 11 验证过的 fallback：

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s <emulatorDeviceId> reverse tcp:8080 tcp:8080
C:\Users\alvinding\flutter\bin\flutter.bat run `
  -d <emulatorDeviceId> `
  --dart-define=FACECHECK_BASE_URL=http://127.0.0.1:8080 `
  --dart-define=FACECHECK_LOCAL_BACKEND_HOSTS=127.0.0.1,10.0.2.2,localhost
```

Android 真机如果要连接本地后端，必须使用 Windows 主机局域网 IP，通过 `--dart-define` 切换 base URL，不改源码：

```powershell
C:\Users\alvinding\flutter\bin\flutter.bat run `
  -d <physicalDeviceId> `
  --dart-define=FACECHECK_BASE_URL=http://<Windows-LAN-IP>:8080 `
  --dart-define=FACECHECK_LOCAL_BACKEND_HOSTS=<Windows-LAN-IP>
```

## 6. Recommended Validation

1. `flutter analyze`
2. `flutter test`
3. `cd backend && ./mvnw -q -DskipTests verify`
4. `cd backend && ./mvnw -q test`
5. 如有需要，执行 `adb reverse tcp:8080 tcp:8080`
6. Android 运行 `anonymous_checkin_android_test.dart`
7. Android 运行 `admin_android_smoke_test.dart`

## 7. Runbooks

- 本地联调说明：[docs/local-run.md](/mnt/c/Users/alvinding/desktop/FaceCheck/docs/local-run.md:1)
- 华为云配置说明：[docs/huawei-cloud-config.md](/mnt/c/Users/alvinding/desktop/FaceCheck/docs/huawei-cloud-config.md:1)
- 发布前检查：[docs/release-readiness.md](/mnt/c/Users/alvinding/desktop/FaceCheck/docs/release-readiness.md:1)
