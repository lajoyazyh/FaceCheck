# 发布准备说明

## 1. 自动化基线

发布前至少确认以下命令通过：

```bash
cd app && flutter analyze
cd app && flutter test
cd backend && ./mvnw -q -DskipTests verify
cd backend && ./mvnw -q test
```

## 2. Android 验收

至少执行一次：

- `integration_test/anonymous_checkin_android_test.dart`
- `integration_test/admin_android_smoke_test.dart`

覆盖点：

- 管理员登录和管理员路由访问
- 普通用户 / 匿名用户管理员访问重定向
- 用户列表、场次、二维码、记录、异常复核、系统状态、系统配置页面打开
- 中文文案和移动端布局无明显异常
- 匿名扫码、拍照、上传链路在 Android 环境可验证

## 3. 本地 seed 账户

- 管理员：`admin / Admin123!`
- 普通用户：`alice / user123!!`

seed 数据仅用于 `local` profile 调试，不作为生产初始化方案。
- 默认正式 Flyway 路径仍然只有 `classpath:db/migration`，不会加载 `db/local/V6__seed_local_test_data.sql`。
- `db/local/V6__seed_local_test_data.sql` 只会在 `local` profile 额外启用 `classpath:db/local` 时执行。
- 本地后端启动也必须显式传入 `local` profile，例如 `.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run`，不要依赖未指定 profile 时的隐式行为。
- 如需重建本地 seed 数据，请清空本地开发库或执行 `docker compose down -v` 后重新启动容器，再重新启动后端。

## 4. 真实华为云最小人工验证

只做小范围验证：

1. 配置真实 `FRS_*` 与 `OBS_*` 环境变量
2. 上传 1 张人脸照片并确认状态转为 `ACTIVE`
3. 创建 1 个已发布场次并生成二维码
4. 执行 1 次匿名签到
5. 检查 PostgreSQL、审计日志、系统状态页中的 FRS / OBS 记录

## 5. 当前残余风险

- Windows 本地运行脚本仍以 Java 21 为基线说明；若开发机默认 JDK 不是 21，需要用项目约定终端启动
- Android 真机联调依赖局域网 IP 和 Windows 防火墙放行
- Flutter App 当前默认连接已部署后端 `https://115.120.241.220`；该入口使用自签名证书，Android Emulator/真机需要先安装并信任证书。如需验证本地后端，Android Emulator 使用 `FACECHECK_BASE_URL=http://10.0.2.2:8080`
- Android Emulator 连接本地后端时如果 `10.0.2.2:8080` 直连不稳定，需要 `adb reverse tcp:8080 tcp:8080` 并把 `FACECHECK_BASE_URL` 切到 `http://127.0.0.1:8080`
- 真实华为云限流、超时和区域配置仍需少量人工确认
