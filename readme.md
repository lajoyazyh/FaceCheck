# FaceCheck

这是南京大学智软学院大三下云计算选修课的课程项目，目标是实现一个
基于 Spring Boot 单体后端与 Flutter 移动端 App 的人脸签到系统。

## 项目定位

系统面向普通用户和管理员两类角色。
核心流程是：移动端拍照或选择图片上传，后端统一封装华为云 FRS 执行人脸检测、人脸入库、
人脸搜索、人脸比对和身份识别，识别成功后由本系统生成签到记录，并向前端返回
签到结果。

## 核心能力

- 用户登录
- 管理员登录
- 人员管理
- 人脸照片上传与管理
- 调用华为云 FRS 进行人脸检测与照片入库
- 拍照签到
- 调用华为云 FRS 进行人脸搜索、比对与身份识别
- 签到记录生成
- 用户查看个人签到记录
- 管理员查看全局签到记录
- 异常签到处理
- 限流、幂等和防重复签到控制

## 固定技术栈

- 后端：Spring Boot、Spring Data JPA、PostgreSQL、Redis、RabbitMQ、JWT、
  Flyway、Docker Compose、Testcontainers、华为云 FRS、华为云 FRS SDK 或统一
  REST Client 封装
- App：Flutter、Dart、Dio 或统一 HTTP Client、Riverpod、go_router 或统一路由、
  Flutter 相机或图片选择能力、本地安全存储

## 架构与安全边界

- 后端必须保持单体架构，不拆分微服务。
- PostgreSQL 是唯一业务事实源。
- Redis 仅用于缓存、限流、幂等控制、防重复提交和会话辅助。
- RabbitMQ 仅用于异步人脸入库、签到识别削峰、失败重试和死信处理。
- 第一版必须由 Spring Boot 后端统一封装华为云 FRS，Flutter App 不允许直接调用 FRS。
- 业务流程必须依赖 `FaceRecognitionProvider`，第一版生产实现为
  `HuaweiCloudFrsFaceRecognitionProvider`。
- 普通用户和管理员接口按宪法要求执行 JWT 鉴权。
- 签到上传接口可以免登录，但必须进行限流、防刷、防重复和人脸识别校验。
- 前端只能通过后端 API 访问业务数据，不能直连数据库、中间件或服务器文件系统。

## 交付与验证

- Flutter App 以 Android 为主要交付与测试目标。
- Windows 桌面调试仅用于普通页面、状态流转和接口调用。
- 涉及摄像头、权限申请、图片读取、文件上传、网络访问和签到拍照流程的功能，
  必须在 Android 真机或 Android 模拟器中验证。

## Windows 本地后端启动诊断

优先顺序：

```powershell
docker compose up -d postgres redis rabbitmq
cd backend
.\mvnw.cmd -v
.\mvnw.cmd spring-boot:run
```

也可以先跑本地诊断脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\doctor-local.ps1
```

如果 `.\mvnw.cmd -v` 输出 `StatusCode`、`RawContentLength` 或 `Content {80, 75, 3, 4...}`，说明 Maven Wrapper 下载链路异常。先修 wrapper 或临时使用系统 Maven，不要先排查 `/api/health`。

如果启动日志出现 `SQL State 28P01` 和 `用户 "facecheck" Password 认证失败`，说明 Spring Boot 已经开始启动，但 Flyway 连不上本地 PostgreSQL。优先检查 Docker Postgres 容器、数据库用户名密码、`docker compose ps` 里 PostgreSQL 是否真的映射了 `0.0.0.0:5432->5432/tcp`，以及旧 volume 是否还保留着旧密码。

- Windows 浏览器或 PowerShell 访问健康检查：`http://localhost:8080/api/health`
- Android Emulator 访问 Windows 后端：`http://10.0.2.2:8080`
- Android 真机访问 Windows 后端：改成 Windows 主机的局域网 IP

更完整说明见 [docs/local-dev-windows.md](docs/local-dev-windows.md)。
