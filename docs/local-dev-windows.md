# Windows Local Backend Startup Diagnostics

This note is for local Windows startup and DevEx debugging only. It does not change backend business logic.

## Recommended Order

1. Start local infrastructure:

```powershell
docker compose up -d postgres redis rabbitmq
```

2. Verify Maven Wrapper before starting Spring Boot:

```powershell
cd backend
.\mvnw.cmd -v
```

3. Start the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

4. Verify the health endpoint:

```powershell
curl.exe http://localhost:8080/api/health
```

## Helper Scripts

Run the doctor script first when the machine state is unknown:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\doctor-local.ps1
```

Start the backend with local-only environment variables:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\dev-start-local.ps1
```

If Maven Wrapper is still broken but system Maven is available, use the explicit fallback switch:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\dev-start-local.ps1 -UseSystemMavenFallback
```

The start script sets these variables only for that PowerShell process:

- `SPRING_PROFILES_ACTIVE=local`
- `DB_URL=jdbc:postgresql://localhost:5432/facecheck`
- `DB_USERNAME=facecheck`
- `DB_PASSWORD=facecheck`
- `REDIS_HOST=localhost`
- `REDIS_PORT=26379`
- `RABBITMQ_HOST=localhost`
- `RABBITMQ_PORT=5672`
- `RABBITMQ_USERNAME=facecheck`
- `RABBITMQ_PASSWORD=facecheck`
- `JWT_SECRET=change-this-to-a-long-random-local-secret`
- `HUAWEI_CLOUD_ENABLED=false`
- `SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP=true`

## If `.\mvnw.cmd -v` Prints `StatusCode` or `RawContentLength`

If `.\mvnw.cmd -v` prints output like:

- `StatusCode        : 200`
- `RawContentLength  : ...`
- `Content           : {80, 75, 3, 4...}`

then Maven Wrapper downloaded a ZIP file but did not actually save, extract, or execute Maven correctly.

In that case:

1. Do not troubleshoot `http://localhost:8080/api/health` yet.
2. Fix Maven Wrapper first.
3. If you need a temporary workaround, use system Maven explicitly:

```powershell
mvn spring-boot:run
```

The repository now pins Maven Wrapper to Apache Maven `3.9.15` with the official `only-script` wrapper and a Windows-safe ZIP distribution URL.

## If the Backend Fails with `SQL State 28P01`

If the log contains:

- `SQL State  : 28P01`
- `用户 "facecheck" Password 认证失败`

then Spring Boot already started its boot sequence, but Flyway could not authenticate to PostgreSQL, so the app stops before `/api/health` is usable.

Check these items first:

1. Docker is up and `docker compose ps` shows `postgres`, `redis`, and `rabbitmq`.
2. The PostgreSQL container exists, usually `facecheck-postgres`.
3. Local defaults stay aligned:

```text
POSTGRES_USER=facecheck
POSTGRES_PASSWORD=facecheck
POSTGRES_DB=facecheck
```

4. Old Docker volumes are not keeping an older database password.
5. `docker compose ps` really shows PostgreSQL published to the host, for example `0.0.0.0:5432->5432/tcp`. If it only shows `5432/tcp`, Spring Boot on Windows is not reaching this container through `localhost:5432`.

Useful checks:

```powershell
docker compose ps
docker exec -i facecheck-postgres psql -U facecheck -d facecheck -c "select 1;"
docker exec -e PGPASSWORD=facecheck -i facecheck-postgres psql -h localhost -U facecheck -d facecheck -c "select 1;"
```

The second command proves the container and role exist. The third command is the closer match to what Spring Boot and Flyway need: TCP password authentication.

## Fast PostgreSQL Repair Options

If the container can be recreated:

```powershell
docker compose down
docker compose up -d postgres redis rabbitmq
```

Warning: whether old data is kept depends on the `volumes` configuration in `docker-compose.yml`.

If you want to repair the current container password in place:

```powershell
docker exec -it facecheck-postgres sh
psql -U facecheck -d postgres
ALTER USER facecheck WITH PASSWORD 'facecheck';
```

If `-U facecheck` is not the right superuser inside your container, try `psql -U postgres -d postgres` instead.

If `docker-compose.yml` already says `facecheck/facecheck/facecheck` but authentication still fails, the most likely cause is an old volume. Changing `POSTGRES_PASSWORD` in Compose does not automatically update an existing database user's password.

If `docker compose ps` shows only `5432/tcp` for `facecheck-postgres`, recreate the container so the current port mapping is applied:

```powershell
docker compose down
docker compose up -d postgres redis rabbitmq
```

## Maven Settings Warning

If Maven prints a warning like:

```text
[WARNING] Unrecognised tag: 'repositories'
```

for `C:\Users\<you>\.m2\settings.xml`, that file is malformed. Do not commit the user file into this project.

Use one of these fixes instead:

1. Move `<repositories>` into `<profiles><profile>...</profile></profiles>`.
2. Prefer `<mirrors>` when the goal is to select a repository mirror.
3. Compare against [`docs/maven-settings-example.xml`](./maven-settings-example.xml).

The example file uses Maven Central by default and keeps an optional HTTPS Aliyun mirror as a mirror example, not as a required dependency.

## Health Check Addresses

- Default Flutter app backend: `http://115.120.241.220:8080`
- Windows browser or PowerShell: `http://localhost:8080/api/health`
- Android Emulator to local backend: override with `FACECHECK_BASE_URL=http://10.0.2.2:8080`
- Android real device to local backend: use the Windows host LAN IP instead of `localhost`
