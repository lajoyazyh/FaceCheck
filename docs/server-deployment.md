# 服务器部署说明

## 1. 部署形态

- 后端通过 Docker Compose 部署到服务器。
- Spring Boot 容器仅监听 `127.0.0.1:${FACECHECK_BACKEND_HOST_PORT:-18080}`。
- nginx 对外监听 `443` HTTPS，并将 `80`/`8080` 重定向到 HTTPS；访问入口仍仅放行南京大学校园网与明确加入的开发网段白名单。
- HTTPS 使用服务器本地生成的自签名证书，证书文件位于 `/etc/facecheck/tls/`，不会写入仓库。默认 SAN 为 `IP:115.120.241.220`，如服务器 IP 或域名变化，需要在 `/etc/facecheck/facecheck.env` 中调整 `FACECHECK_TLS_SUBJECT_ALT_NAME` 后重新生成证书。
- GitHub Actions 在 `main` 分支推送后通过 SSH 登录服务器，拉取对应提交并执行 `deploy/facecheck/scripts/deploy.sh`。

## 2. 服务器一次性准备

1. 创建目录：

```bash
mkdir -p /etc/facecheck /opt/facecheck
```

2. 可选：提前基于 `deploy/facecheck/facecheck.env.example` 创建生产环境文件：

```bash
cp /opt/facecheck/deploy/facecheck/facecheck.env.example /etc/facecheck/facecheck.env
chmod 600 /etc/facecheck/facecheck.env
```

如果跳过这一步，首次执行 `deploy/facecheck/scripts/deploy.sh` 时会自动创建
`/etc/facecheck/facecheck.env`，并为 PostgreSQL、Redis、RabbitMQ 与 JWT
生成随机密钥。自动生成后仍建议人工打开该文件核对业务配置。

3. 可选：提前安装 nginx 站点：

```bash
cp /opt/facecheck/deploy/facecheck/nginx/facecheck-campus.conf /etc/nginx/sites-available/facecheck-campus
ln -sf /etc/nginx/sites-available/facecheck-campus /etc/nginx/sites-enabled/facecheck-campus
nginx -t
systemctl reload nginx
```

如果跳过这一步，GitHub Actions 部署阶段会在服务器上自动安装 `nginx`、
同步 `deploy/facecheck/nginx/facecheck-campus.conf`，并执行 `enable --now`
与 `reload`。

## 3. 校园网白名单

当前配置放行以下网段：

- `202.119.32.0/19`
- `2001:da8:1007::/48`
- `121.236.0.0/16`
- `58.192.0.0/16`

这些值是根据 2026-06-16 当天的公开解析与 RIPE Stat 前缀查询推断得到，用于满足“仅南京大学校园网访问”的最小闭环。若校方出口网段后续调整，应同步更新 `deploy/facecheck/nginx/facecheck-campus.conf`。

自签名证书不会被系统默认信任。浏览器测试需要手动接受证书风险；Android 真机或模拟器测试需要安装并信任 `/etc/facecheck/tls/facecheck-selfsigned.crt` 对应证书，或通过 `FACECHECK_BASE_URL` 切换回本地 HTTP 调试地址。

## 4. GitHub Actions Secrets

工作流 `/.github/workflows/deploy-backend.yml` 需要以下仓库 secrets：

- `FACECHECK_DEPLOY_HOST`
- `FACECHECK_DEPLOY_PORT`
- `FACECHECK_DEPLOY_SSH_KEY`
- `FACECHECK_DEPLOY_USER`

当前工作流使用公网 IP + SSH 密码方式登录服务器。

推荐配置：

- `FACECHECK_DEPLOY_HOST=115.120.241.220`
- `FACECHECK_DEPLOY_PORT=22`
- `FACECHECK_DEPLOY_USER=root`
- `FACECHECK_DEPLOY_SSH_KEY=<服务器公网 SSH 密码>`

注意：为了兼容当前已有的 secret 名称，工作流里复用了 `FACECHECK_DEPLOY_SSH_KEY` 这个名字来存放“SSH 密码”，它当前并不是私钥内容。

如果后续切回更安全的密钥部署方式，再把这个 secret 恢复成真实私钥，并把工作流调整回私钥登录。

## 5. 当前限制

- 华为云 FRS 生产适配器已经接入 Huawei Cloud Java SDK。若服务器 `/etc/facecheck/facecheck.env` 已配置 `FRS_AK`、`FRS_SK`、`FRS_PROJECT_ID`、`FRS_REGION`、`FRS_ENDPOINT`、`FRS_FACE_SET_NAME`，且对应人脸库已在华为云 FRS 中创建，可以设置 `HUAWEI_CLOUD_ENABLED=true` 做真实识别验证。
- OBS 生产适配器已经接入 Huawei OBS SDK。若服务器 `/etc/facecheck/facecheck.env` 已配置 `FRS_AK`、`FRS_SK`、`OBS_ENDPOINT`、`OBS_REGION`、`OBS_BUCKET`，可以单独设置 `OBS_ENABLED=true` 做真实对象存储验证。
- 当前项目 OBS 非敏感配置为 `OBS_BUCKET=yunjisuan-zyh`、`OBS_REGION=cn-east-3`、`OBS_ENDPOINT=https://obs.cn-east-3.myhuaweicloud.com`；真实 `FRS_AK` / `FRS_SK` 仍需在服务器本地配置。
- 真实 AK/SK、JWT_SECRET、数据库密码只应写入服务器本地 `/etc/facecheck/facecheck.env` 或部署平台 secret，不要写入 git 跟踪文件，也不要直接发到聊天记录里。
- 开启 `HUAWEI_CLOUD_ENABLED=true` 前，建议先用小范围人工流程验证：上传 1 张人脸照片、等待注册为 `ACTIVE`、创建 1 个场次、执行 1 次匿名签到。
- 首次自动生成 `/etc/facecheck/facecheck.env` 后，建议核对 `FACECHECK_BACKEND_HOST_PORT`、
  `DB_URL`、`REDIS_HOST`、`RABBITMQ_HOST` 和 JWT/数据库密码是否符合当前服务器规划。
- 登录接口默认启用 Redis 限流：`LOGIN_RATE_LIMIT_MAX_FAILED_ATTEMPTS=5`、
  `LOGIN_RATE_LIMIT_WINDOW_SECONDS=300`。连续错误登录达到阈值后会返回 `RATE_LIMITED`。
