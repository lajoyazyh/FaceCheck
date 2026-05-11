<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read
`specs/001-face-checkin-system/plan.md`.
For current feature scope, acceptance rules, and task coverage, also consult
`specs/001-face-checkin-system/spec.md` and
`specs/001-face-checkin-system/tasks.md` when editing the feature documents.
<!-- SPECKIT END -->

## FaceCheck Agent Execution Rules

### 1. Project Context

This project uses Spec Kit workflow.

Before implementing any task, the agent must read:

- AGENTS.md
- constitution.md
- specs/001-face-checkin-system/spec.md
- specs/001-face-checkin-system/plan.md
- specs/001-face-checkin-system/tasks.md

The default feature directory is:

- specs/001-face-checkin-system/

If multiple feature directories exist, the agent must list all detected feature directories and their tasks.md paths before choosing the current feature.

---

### 2. /speckit.implement Execution Rules

When running /speckit.implement, the agent must follow these rules:

1. First list the detected feature directory and tasks.md path.
2. First list the phase list found in tasks.md.
3. Find the first incomplete phase.
4. Implement only that phase.
5. Do not implement the next phase.
6. After each task is completed, update the corresponding task status in tasks.md.
7. After the current phase implementation is complete, run relevant tests for this phase first.
8. If tests fail, analyze the failure reason and fix it first.
9. After fixing, rerun the relevant tests.
10. Only after the current phase tests pass, or after clearly explaining why tests cannot run because of environment limitations, proceed to code review.
11. Code review must be performed by the main agent. Do not use a subagent for code review.
12. Code review must inspect the current git diff, test results, and modified files.
13. If code review finds blocking issues, fix them first.
14. After fixing review issues, rerun affected tests.
15. If environment variables, Huawei Cloud credentials, OBS bucket, FRS endpoint, Android emulator, Docker service, or manual confirmation are required, stop and list what the user must configure.
16. After this phase passes tests and code review, stop. Do not continue to the next phase.

---

### 3. Hard Architecture Constraints

The agent must obey these constraints at all times:

1. The backend must remain a Spring Boot monolith.
2. Do not split the backend into microservices.
3. Do not use Kafka.
4. Do not use local OpenCV.
5. Do not use local ONNX.
6. Do not use local embedding retrieval.
7. Face recognition must be adapted through the backend face module targeting Huawei Cloud FRS.
8. Image storage must be adapted through the backend storage module targeting Huawei Cloud OBS.
9. Flutter must not directly access OBS, FRS, PostgreSQL, Redis, RabbitMQ, or the server filesystem.
10. PostgreSQL is the only business source of truth.
11. Flyway must manage all database schema changes.
12. Redis may only be used for rate limiting, idempotency, caching, token blacklist, and other auxiliary capabilities.
13. RabbitMQ may only be used for asynchronous face registration, retry, dead-letter handling, and optional traffic smoothing.
14. AttendanceRecord must have a unique constraint on sessionId + personId.
15. Anonymous QR-code check-in may only submit a check-in photo.
16. Anonymous QR-code check-in must not access personal profiles, personal photo libraries, personal history records, or admin features.
17. Do not write real AK/SK, JWT_SECRET, database passwords, or other secrets into code, tests, README examples, or git-tracked files.
18. Only provide safe example configuration through .env.example or application-local.example.yml.

---

### 4. Test-First Rules

Backend phases should prefer backend-related tests based on the actual build tool.

For the current Maven backend project, prefer:

- cd backend && ./mvnw -q -DskipTests verify
- cd backend && ./mvnw -q test

Do not run backend Maven commands from the repository root unless using an explicit backend pom path.

Acceptable equivalent backend command:

- ./backend/mvnw -f backend/pom.xml -q -DskipTests verify

Flutter phases should prefer:

- cd app && flutter analyze
- cd app && flutter test

If Docker Compose or Testcontainers tests exist, check whether Docker is available before running them.

If tests cannot run because Docker, PostgreSQL, Redis, RabbitMQ, Huawei Cloud credentials, Android emulator, Flutter SDK, or other external services are missing, clearly report the reason.

Do not enter the final report while tests are failing and unexplained.

Do not use -Dmaven.test.skip=true as final passing evidence, because it skips test compilation.

---

### 5. Code Review Self-Check

After tests pass, or after test blockers are clearly explained, the main agent must review:

1. Whether the changes comply with constitution.md, spec.md, plan.md, and tasks.md.
2. Whether the backend remains a Spring Boot monolith.
3. Whether microservices, Kafka, local OpenCV, local ONNX, or local embedding retrieval were incorrectly introduced.
4. Whether Huawei Cloud FRS remains the target for face recognition adaptation.
5. Whether Huawei Cloud OBS remains the target for image object storage adaptation.
6. Whether Flutter avoids direct access to OBS, FRS, PostgreSQL, Redis, RabbitMQ, and the server filesystem.
7. Whether PostgreSQL remains the only business source of truth.
8. Whether Redis is only used for rate limiting, idempotency, caching, token blacklist, or other auxiliary capabilities.
9. Whether RabbitMQ is only used for asynchronous face registration, retry, dead-letter handling, and optional traffic smoothing.
10. Whether all database schema changes are managed through Flyway.
11. Whether necessary indexes, foreign keys, and unique constraints are missing.
12. Whether Huawei Cloud AK/SK, JWT_SECRET, database passwords, and other secrets are protected.
13. Whether necessary tests for the current phase were added or updated.
14. Whether unrelated large refactoring was introduced.
15. Whether any environment variables or external services still require user configuration.

---

### 6. Final Phase Report Format

The final phase report must be written in Simplified Chinese and must use this exact structure:

## 阶段实现报告

### 1. 阶段信息
- 当前阶段：
- 完成任务编号：
- 是否进入下一阶段：否，等待用户确认

### 2. 修改文件
- 新增文件：
- 修改文件：
- 删除文件：

### 3. 数据库变更
- 新增 Flyway migration：
- 修改表：
- 新增索引/唯一约束：

### 4. 接口变更
- 新增接口：
- 修改接口：
- 权限要求：

### 5. 测试与修复结果
- 首次执行测试命令：
- 首次测试结果：
- 发现的问题：
- 已修复的问题：
- 复测命令：
- 复测结果：
- 未运行测试及原因：

### 6. Code Review 自检结果
- 结论：PASS 或 FAIL
- 阻塞问题：
- 非阻塞问题：
- 测试缺口：
- 已修复问题：

### 7. 环境变量和外部服务
- 需要用户配置的环境变量：
- 需要启动的服务：
- 需要真实华为云账号验证的项目：

### 8. 剩余风险
- 风险：
- 建议：

### 9. 建议提交信息
- git commit message：

---

### 7. Forbidden Behaviors

The agent must not:

1. Continue to the next phase without user confirmation.
2. Report success while tests are failing and unexplained.
3. Use a subagent for code review.
4. Delete important tests just to pass the build.
5. Use -Dmaven.test.skip=true as final passing evidence.
6. Introduce unrelated large refactoring.
7. Leak real AK/SK, JWT_SECRET, database passwords, or other secrets.
8. Change the project from a Spring Boot monolith to microservices.
9. Add Kafka.
10. Add local OpenCV, local ONNX, or local embedding retrieval.



### Testcontainers Docker API Compatibility Note

Testcontainers should normally rely on standard Docker auto-discovery.

Do not hardcode Docker host, socket path, provider strategy, or Docker API version unless a specific environment requires it.

Current local compatibility note:

- In the current WSL + Docker Desktop environment, Testcontainers required `api.version=1.41` through Maven Surefire system properties to complete the Docker handshake reliably.
- This is an environment compatibility workaround, not a business requirement.
- If CI or another developer machine runs `cd backend && ./mvnw -q test` successfully without this setting, prefer removing or environment-gating this workaround.
- If Testcontainers fails with Docker handshake errors, first run:
  - `docker version`
  - `docker info`
  - `docker ps`
  - `docker context ls`
  - `echo "DOCKER_HOST=$DOCKER_HOST"`
  - `ls -l /var/run/docker.sock /var/run/docker-cli.sock 2>/dev/null || true`
- If Docker daemon is available but Testcontainers Java process fails to negotiate with Docker, then check whether the `api.version=1.41` Surefire property is required for the current environment.
