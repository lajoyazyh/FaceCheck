# 华为云配置说明

## 1. 本地默认模式

本地默认使用：

- `HUAWEI_CLOUD_ENABLED=false`
- `OBS_ENABLED=false`
- `MockFaceRecognitionProvider`
- `InMemoryHuaweiObsStorageService`
- 后端受控边界，不向 Flutter App 暴露任何 AK/SK

这意味着：

- 本地联调、自动化测试、Android smoke 不依赖真实华为云账号
- 真实华为云验证应在功能完成后做少量人工核验

## 2. 环境变量

本地 Docker Compose 可参考仓库根目录的 `.env.example`；服务器部署可参考 `deploy/facecheck/facecheck.env.example`，实际服务器配置文件路径是 `/etc/facecheck/facecheck.env`。

### 2.1 只验证真实 OBS

当前 FRS 真实接入尚未完成。如果只验证真实对象存储，请保持 FRS 总开关关闭，只打开 OBS：

```text
HUAWEI_CLOUD_ENABLED=false
OBS_ENABLED=true

# 当前后端沿用 FRS_AK / FRS_SK 作为华为云 AK/SK 配置来源。
FRS_AK=<your-huawei-cloud-ak>
FRS_SK=<your-huawei-cloud-sk>

OBS_ENDPOINT=<your-obs-endpoint>
OBS_REGION=<your-obs-region>
OBS_BUCKET=<your-obs-bucket>
```

只验证 OBS 时暂时不需要填写 `FRS_PROJECT_ID`、`FRS_REGION`、`FRS_ENDPOINT`、`FRS_FACE_SET_NAME`。

### 2.2 后续完整 FRS + OBS 链路

真实 FRS 接入完成后，再打开 FRS 总开关。此时后端会同时要求 FRS 与 OBS 配置完整：

```text
HUAWEI_CLOUD_ENABLED=true
OBS_ENABLED=true

FRS_AK=<your-huawei-cloud-ak>
FRS_SK=<your-huawei-cloud-sk>
FRS_PROJECT_ID=<your-project-id>
FRS_REGION=<your-frs-region>
FRS_ENDPOINT=<your-frs-endpoint>
FRS_FACE_SET_NAME=facecheck-default
FRS_SIMILARITY_THRESHOLD=85

OBS_ENDPOINT=<your-obs-endpoint>
OBS_REGION=<your-obs-region>
OBS_BUCKET=<your-obs-bucket>
```

### 2.3 字段说明

| 变量 | 当前是否必填 | 用途 | 示例格式 |
|------|--------------|------|----------|
| `HUAWEI_CLOUD_ENABLED` | 是 | FRS 总开关；真实 FRS 未完成前保持 `false` | `false` |
| `OBS_ENABLED` | 是 | OBS 独立开关；只验证真实 OBS 时设为 `true` | `true` |
| `FRS_AK` | 开启 OBS 或 FRS 时必填 | 华为云访问密钥 AK；当前 OBS 也复用该字段 | 不写入文档或 git |
| `FRS_SK` | 开启 OBS 或 FRS 时必填 | 华为云访问密钥 SK；当前 OBS 也复用该字段 | 不写入文档或 git |
| `OBS_ENDPOINT` | 开启 OBS 或 FRS 时必填 | OBS endpoint | `https://obs.cn-east-3.myhuaweicloud.com` |
| `OBS_REGION` | 开启 OBS 或 FRS 时必填 | OBS bucket 所在 region | `cn-east-3` |
| `OBS_BUCKET` | 开启 OBS 或 FRS 时必填 | 存放人脸照片和签到照片的 bucket 名称 | `facecheck-prod` |
| `FRS_PROJECT_ID` | 仅开启 FRS 时必填 | 华为云项目 ID | `<project-id>` |
| `FRS_REGION` | 仅开启 FRS 时必填 | FRS 服务所在 region | `cn-east-3` |
| `FRS_ENDPOINT` | 仅开启 FRS 时必填 | FRS endpoint | `<frs-endpoint>` |
| `FRS_FACE_SET_NAME` | 仅开启 FRS 时必填 | FRS 人脸库名称 | `facecheck-default` |
| `FRS_SIMILARITY_THRESHOLD` | 否 | 签到识别相似度阈值 | `85` |

不要把真实 AK/SK、JWT_SECRET、数据库密码写进 git 跟踪文件，也不要直接发到聊天里。需要我帮你检查时，可以只发脱敏后的配置形状，例如 `FRS_AK=***abcd`、`OBS_BUCKET=facecheck-prod`。

## 3. 安全边界

- Flutter App 只访问 Spring Boot 后端 API
- Flutter App 不直接访问 FRS、OBS、PostgreSQL、Redis、RabbitMQ 或文件系统
- 华为云凭证只放在后端运行环境中

## 4. 建议的真实验证范围

只做小范围人工验证，不把真实云依赖当默认自动化门禁：

1. 上传 1 张普通用户照片，确认可进入 `ACTIVE`
2. 用管理员创建 1 个已发布场次
3. 用匿名签到链路提交 1 次真实照片
4. 确认 PostgreSQL 中保留 `faceSetName`、`frsFaceId`、`externalRequestId` 等外部引用
5. 确认系统状态页可看到 FRS / OBS 健康摘要

## 5. 常见注意事项

- 若只做本地 UI / API / Android smoke，不要开启 `HUAWEI_CLOUD_ENABLED=true` 或 `OBS_ENABLED=true`
- 若只验证真实 OBS，可以只开启 `OBS_ENABLED=true`，并继续保持 `HUAWEI_CLOUD_ENABLED=false`
- 若真实 FRS 限流或超时，应该得到可追踪失败或人工复核结果，不能静默丢请求
- OBS 只存对象，不是业务事实源；业务事实源仍然是 PostgreSQL
