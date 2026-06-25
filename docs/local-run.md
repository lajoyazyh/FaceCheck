# 本地联调运行说明

## 1. Windows 侧前提

- 后端路径：`C:\Users\alvinding\Desktop\FaceCheck\backend`
- Flutter App 路径：`C:\Users\alvinding\Desktop\FaceCheck\app`
- Docker Desktop 已启动
- Flutter stable、Android SDK、一个 Android 模拟器或真机可用

## 2. 启动基础设施

在仓库根目录执行：

```powershell
docker compose up -d postgres redis rabbitmq
docker compose ps
```

本地默认账号与端口：

- PostgreSQL：`facecheck/facecheck@localhost:5432/facecheck`
- Redis：`localhost:26379`
- RabbitMQ：`facecheck/facecheck@localhost:5672`

## 3. 启动后端

推荐先做一次本地诊断：

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\doctor-local.ps1
```

然后在 Windows PowerShell 中启动后端：

```powershell
cd C:\Users\alvinding\Desktop\FaceCheck\backend
.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

本地 profile 现在会自动：

- 使用 `facecheck/facecheck` 连接 PostgreSQL、Redis、RabbitMQ
- 启用本地 RabbitMQ listener
- 在 `classpath:db/migration` 之外额外加载 `classpath:db/local`
- 仅在 `local` profile 下执行 `db/local/V6__seed_local_test_data.sql`

不要依赖“未指定 profile 时自动进入 local”这类隐式行为；Phase 11 风险收口后，本地联调需要显式传入 `local` profile。

健康检查：

```powershell
curl.exe http://localhost:8080/api/health
```

## 4. 本地种子账号

`local` profile 会自动创建以下调试数据：

- 管理员：`admin / Admin123!`
- 普通用户：`alice / user123!!`
- 一个已发布场次：`本地调试示例场次`
- 一个可见成功签到记录
- 一个待复核异常尝试

这些数据仅在 `local` profile 启用，不会在默认生产配置中自动写入。

如果你之前已经在本地开发库中跑过旧版 Phase 11 seed，当前补丁不需要额外生产迁移；如需从零重建本地 seed 数据，请重建本地开发库后再启动：

```powershell
docker compose down -v
docker compose up -d postgres redis rabbitmq
```

## 5. 启动 Flutter App

### 默认远程后端

当前 Flutter App 默认连接已部署后端 `https://115.120.241.220`。该入口使用服务器自签名证书；Android 真机或模拟器需要安装并信任对应证书后才能直接访问。只验证本地后端时继续使用下面的 HTTP `--dart-define`：

```powershell
cd C:\Users\alvinding\Desktop\FaceCheck\app
C:\Users\alvinding\flutter\bin\flutter.bat devices
C:\Users\alvinding\flutter\bin\flutter.bat run -d <emulatorDeviceId>
```

### Android 模拟器连接本地后端

如果要让 Android 模拟器访问 Windows 本机后端，请显式覆盖 base URL：

```powershell
cd C:\Users\alvinding\Desktop\FaceCheck\app
C:\Users\alvinding\flutter\bin\flutter.bat run `
  -d <emulatorDeviceId> `
  --dart-define=FACECHECK_BASE_URL=http://10.0.2.2:8080 `
  --dart-define=FACECHECK_LOCAL_BACKEND_HOSTS=10.0.2.2,127.0.0.1,localhost
```

如果模拟器访问 `10.0.2.2:8080` 出现 `Connection closed before full header was received`、连接被重置，或 Android smoke 在预检阶段拿不到完整 HTTP 响应，再改用 `adb reverse` fallback：

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s <emulatorDeviceId> reverse tcp:8080 tcp:8080
C:\Users\alvinding\flutter\bin\flutter.bat run `
  -d <emulatorDeviceId> `
  --dart-define=FACECHECK_BASE_URL=http://127.0.0.1:8080 `
  --dart-define=FACECHECK_LOCAL_BACKEND_HOSTS=127.0.0.1,10.0.2.2,localhost
```

### Android 真机连接本地后端

真机不能使用 `127.0.0.1` 或 `10.0.2.2`，必须改为 Windows 主机局域网地址，但不需要改源码，直接用 `--dart-define`：

```powershell
cd C:\Users\alvinding\Desktop\FaceCheck\app
C:\Users\alvinding\flutter\bin\flutter.bat run `
  -d <physicalDeviceId> `
  --dart-define=FACECHECK_BASE_URL=http://<Windows-LAN-IP>:8080 `
  --dart-define=FACECHECK_LOCAL_BACKEND_HOSTS=<Windows-LAN-IP>
```

示例：

```powershell
C:\Users\alvinding\flutter\bin\flutter.bat run `
  -d R58MXXXXXXX `
  --dart-define=FACECHECK_BASE_URL=http://192.168.1.23:8080 `
  --dart-define=FACECHECK_LOCAL_BACKEND_HOSTS=192.168.1.23
```

## 6. Android Smoke

匿名签到 smoke：

```powershell
cd C:\Users\alvinding\Desktop\FaceCheck\app
C:\Users\alvinding\flutter\bin\flutter.bat test integration_test/anonymous_checkin_android_test.dart -d <androidDeviceId>
```

管理员 smoke：

```powershell
cd C:\Users\alvinding\Desktop\FaceCheck\app
C:\Users\alvinding\flutter\bin\flutter.bat test integration_test/admin_android_smoke_test.dart -d <androidDeviceId>
```

如果 smoke 要连接本地后端，匿名和管理员两条命令都追加对应 `--dart-define=FACECHECK_BASE_URL=...`。如果模拟器 smoke 需要走 `adb reverse` fallback，则追加 `--dart-define=FACECHECK_BASE_URL=http://127.0.0.1:8080`，并确保已执行 `adb reverse tcp:8080 tcp:8080`。

## 7. 推荐联调顺序

1. `docker compose up -d postgres redis rabbitmq`
2. `.\mvnw.cmd spring-boot:run`
3. `curl.exe http://localhost:8080/api/health`
4. Android 模拟器启动 App；默认连接 `https://115.120.241.220`
5. 用 `admin / Admin123!` 登录并跑管理员 smoke
6. 用 `alice / user123!!` 登录并验证普通用户权限边界
