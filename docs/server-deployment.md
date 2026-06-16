# 服务器部署说明

## 1. 部署形态

- 后端通过 Docker Compose 部署到服务器。
- Spring Boot 容器仅监听 `127.0.0.1:${FACECHECK_BACKEND_HOST_PORT:-18080}`。
- nginx 对外监听 `8080`，并仅放行南京大学校园网白名单。
- GitHub Actions 在 `main` 分支推送后通过 SSH 登录服务器，拉取对应提交并执行 `deploy/facecheck/scripts/deploy.sh`。

## 2. 服务器一次性准备

1. 创建目录：

```bash
mkdir -p /etc/facecheck /opt/facecheck
```

2. 基于 `deploy/facecheck/facecheck.env.example` 创建生产环境文件：

```bash
cp /opt/facecheck/deploy/facecheck/facecheck.env.example /etc/facecheck/facecheck.env
chmod 600 /etc/facecheck/facecheck.env
```

3. 安装 nginx 站点：

```bash
cp /opt/facecheck/deploy/facecheck/nginx/facecheck-campus.conf /etc/nginx/sites-available/facecheck-campus
ln -sf /etc/nginx/sites-available/facecheck-campus /etc/nginx/sites-enabled/facecheck-campus
nginx -t
systemctl reload nginx
```

## 3. 校园网白名单

当前配置初始放行以下网段：

- `202.119.32.0/19`
- `2001:da8:1007::/48`

这些值是根据 2026-06-16 当天的公开解析与 RIPE Stat 前缀查询推断得到，用于满足“仅南京大学校园网访问”的最小闭环。若校方出口网段后续调整，应同步更新 `deploy/facecheck/nginx/facecheck-campus.conf`。

## 4. GitHub Actions Secrets

工作流 `/.github/workflows/deploy-backend.yml` 需要以下仓库 secrets：

- `FACECHECK_DEPLOY_HOST`
- `FACECHECK_DEPLOY_PORT`
- `FACECHECK_DEPLOY_USER`
- `FACECHECK_DEPLOY_SSH_KEY`

推荐为 GitHub Actions 单独生成一把部署私钥，把公钥加入服务器用户的 `~/.ssh/authorized_keys`。

## 5. 当前限制

- 当前仓库里的华为云 FRS/OBS 生产适配器仍是占位或 mock 形态，所以 `HUAWEI_CLOUD_ENABLED` 暂时应保持 `false`。
- 一旦未来补齐真实生产适配器，再把 `/etc/facecheck/facecheck.env` 里的华为云相关配置打开。
