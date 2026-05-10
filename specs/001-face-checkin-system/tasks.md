---

description: "Task list for 管理员场次人脸签到系统 implementation"
---

# Tasks: 管理员场次人脸签到系统

**Input**: Design documents from `/specs/001-face-checkin-system/`  
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [openapi.yaml](./contracts/openapi.yaml)

**Tests**: 核心业务测试是强制项。后端必须覆盖认证、权限、用户名唯一、个人资料接口、管理员用户管理、照片上传、OBS/FRS 适配、人脸注册任务、签到流程、限流、幂等、防重复签到、审计日志，以及 PostgreSQL/Redis/RabbitMQ 的容器化测试；Flutter 必须覆盖登录、路由、状态流转、扫码签到和关键页面测试；相机、扫码、拍照上传、网络访问和未登录签到链路必须在 Android 模拟器或真机验证。

**Organization**: 任务按用户要求的阶段拆分，并通过 `[US1]`、`[US2]`、`[US3]` 标签映射到对应用户故事，确保每个故事仍可独立追踪与验收。

## Format: `[ID] [P?] [Story?] Description with file path`

- **[P]**: 可并行执行，前提是不依赖尚未完成的任务且改动文件不冲突
- **[Story]**: 用户故事标签；`[US1]`=扫码免登录签到，`[US2]`=个人资料与人脸照片，`[US3]`=管理员场次与异常处理
- 每条任务都包含依赖说明和验收标准

## Path Conventions

- **Backend**: `backend/src/main/java/com/facecheck/`, `backend/src/main/resources/`, `backend/src/test/java/com/facecheck/`
- **Flutter app**: `app/lib/`, `app/test/`, `app/integration_test/`, `app/android/`
- **Docs & infra**: `docker-compose.yml`, `docs/`, `specs/001-face-checkin-system/`

## Phase 1: Setup (Stage 0, Shared Infrastructure)

**Purpose**: 建立后端与 Flutter 基础骨架、基础设施编排、统一响应和基础测试支撑。

- [X] T001 Create the Spring Boot backend skeleton in `backend/pom.xml`, `backend/src/main/java/com/facecheck/FaceCheckApplication.java`, and `backend/src/main/resources/application.yml` (Depends on: none; Accept: `cd backend && ./mvnw -q -DskipTests verify` can compile a placeholder application).
- [X] T002 [P] Create the Flutter app skeleton in `app/pubspec.yaml`, `app/lib/main.dart`, and `app/analysis_options.yaml` (Depends on: none; Accept: `flutter pub get` succeeds and `flutter analyze` can run on the scaffold).
- [X] T003 [P] Configure Docker Compose for PostgreSQL, Redis, and RabbitMQ in `docker-compose.yml` (Depends on: none; Accept: `docker compose up -d postgres redis rabbitmq` starts healthy services with predictable ports and credentials).
- [X] T004 Configure Spring profiles and environment binding in `backend/src/main/resources/application.yml`, `backend/src/main/resources/application-local.yml`, `backend/src/main/resources/application-test.yml`, and `backend/src/main/resources/application-prod.yml` (Depends on: T001; Accept: backend can boot under `local`, `test`, and `prod` profiles without exposing cloud credentials to the app).
- [X] T005 Configure the Flyway migration layout in `backend/src/main/resources/db/migration/` and disable `ddl-auto` in `backend/src/main/resources/application.yml` (Depends on: T001, T004; Accept: Flyway is the only schema management path and Hibernate auto DDL is off for non-test profiles).
- [X] T006 Implement a base health endpoint in `backend/src/main/java/com/facecheck/infrastructure/health/AppHealthController.java` and `backend/src/main/java/com/facecheck/infrastructure/health/DependencyHealthService.java` (Depends on: T001, T004; Accept: a health endpoint reports app readiness without leaking secrets).
- [X] T007 Implement `ApiResponse`, `ErrorCode`, `BusinessException`, and global exception mapping in `backend/src/main/java/com/facecheck/common/api/ApiResponse.java`, `backend/src/main/java/com/facecheck/common/error/ErrorCode.java`, `backend/src/main/java/com/facecheck/common/error/BusinessException.java`, and `backend/src/main/java/com/facecheck/common/error/GlobalExceptionHandler.java` (Depends on: T001, T004; Accept: success and failure responses share one JSON envelope with stable business error codes).
- [X] T008 Configure structured request logging and trace propagation in `backend/src/main/resources/logback-spring.xml` and `backend/src/main/java/com/facecheck/infrastructure/logging/RequestTraceFilter.java` (Depends on: T001, T004; Accept: every request writes a traceable request ID and basic latency fields to logs).
- [X] T009 Create PostgreSQL Testcontainers base support and Redis/RabbitMQ test configuration in `backend/src/test/java/com/facecheck/support/PostgresContainerSupport.java`, `backend/src/test/java/com/facecheck/support/RedisRabbitContainerSupport.java`, and `backend/src/test/resources/application-test.yml` (Depends on: T003, T004, T005; Accept: backend integration tests can boot against containerized PostgreSQL and optional containerized Redis/RabbitMQ).

---

## Phase 2: Foundational (Stages 1-2, Blocking Prerequisites)

**Purpose**: 完成认证、权限、身份模型和华为云适配基础；这些任务完成前，不允许推进核心业务故事。

**⚠️ CRITICAL**: No user story work may begin until every task in this phase is complete and tested.

- [X] T010 Create the identity/auth schema migration in `backend/src/main/resources/db/migration/V1__identity_auth.sql` (Depends on: T005; Accept: PostgreSQL contains a unified `user_account` table, unique `username` constraints, status fields, and supporting enums/indexes required for auth and ownership checks without any separate `person` model).
- [X] T011 Implement `User` entity, status enum, and repository in `backend/src/main/java/com/facecheck/identity/model/User.java`, `backend/src/main/java/com/facecheck/identity/model/UserStatus.java`, and `backend/src/main/java/com/facecheck/identity/repo/UserRepository.java` (Depends on: T010; Accept: repositories can persist and query unified user identity data defined in `V1__identity_auth.sql`).
- [X] T012 Implement JWT generation, parsing, and password hashing in `backend/src/main/java/com/facecheck/auth/service/JwtService.java`, `backend/src/main/java/com/facecheck/auth/service/PasswordService.java`, and `backend/src/main/java/com/facecheck/auth/config/SecurityBeansConfig.java` (Depends on: T011; Accept: signed JWTs carry role plus stable user identity claims, and passwords are never stored in plain text).
- [X] T013 Implement the Spring Security filter chain and authenticated principal mapping in `backend/src/main/java/com/facecheck/auth/config/SecurityConfig.java` and `backend/src/main/java/com/facecheck/auth/filter/JwtAuthenticationFilter.java` (Depends on: T012; Accept: backend distinguishes public, user-only, and admin-only endpoints according to the plan).
- [X] T014 Implement `AuthService` and login/logout DTOs in `backend/src/main/java/com/facecheck/auth/service/AuthService.java`, `backend/src/main/java/com/facecheck/auth/dto/LoginRequest.java`, and `backend/src/main/java/com/facecheck/auth/dto/LoginResponse.java` (Depends on: T012, T013; Accept: one auth flow can issue JWTs for both ordinary users and admins without introducing a third role).
- [X] T015 Implement login/logout endpoints and Redis-backed token blacklist in `backend/src/main/java/com/facecheck/auth/api/AuthController.java` and `backend/src/main/java/com/facecheck/auth/service/TokenBlacklistService.java` (Depends on: T014, T009; Accept: logout blacklists JWT `jti` entries in Redis and protected routes reject blacklisted tokens).
- [X] T016 Implement self-access and admin authorization guards in `backend/src/main/java/com/facecheck/auth/security/CurrentUserAccess.java` and `backend/src/main/java/com/facecheck/auth/security/AdminOnly.java` (Depends on: T013, T015; Accept: ordinary users can only reach their own data and non-admin callers are blocked from admin APIs).
- [X] T017 Write authentication and permission tests in `backend/src/test/java/com/facecheck/auth/AuthControllerTest.java`, `backend/src/test/java/com/facecheck/auth/UserSelfAccessIntegrationTest.java`, and `backend/src/test/java/com/facecheck/auth/AdminAccessDeniedIntegrationTest.java` (Depends on: T015, T016, T009; Accept: tests cover ordinary-user login, admin login, blacklist logout, self-data-only access, and non-admin denial on admin endpoints).
- [X] T017A [P] Create contract and integration tests for `/api/me/profile` and admin user lifecycle APIs in `backend/src/test/java/com/facecheck/identity/api/MeProfileControllerContractTest.java`, `backend/src/test/java/com/facecheck/admin/api/AdminUserControllerContractTest.java`, and `backend/src/test/java/com/facecheck/identity/UserManagementIntegrationTest.java` (Depends on: T017; Accept: failing tests define username-only profile updates, unique-username rejection, and admin create/edit/disable user flows without any email or phone identifiers).
- [X] T017B Implement `UserProfileService`, `AdminUserService`, `MeProfileController`, and `AdminUserController` in `backend/src/main/java/com/facecheck/identity/service/UserProfileService.java`, `backend/src/main/java/com/facecheck/admin/service/AdminUserService.java`, `backend/src/main/java/com/facecheck/identity/api/MeProfileController.java`, and `backend/src/main/java/com/facecheck/admin/api/AdminUserController.java` (Depends on: T017A, T011, T016; Accept: authenticated users can update their own username/password, admins can create/edit/disable users, and username is the only business identifier exposed by the APIs).
- [X] T018 Create Huawei cloud configuration binding in `backend/src/main/java/com/facecheck/infrastructure/config/HuaweiCloudProperties.java` and `backend/src/main/java/com/facecheck/infrastructure/config/HuaweiCloudConfig.java` (Depends on: T004; Accept: AK/SK, endpoint, project ID, region, bucket, face set, and threshold values are injectable only from backend-safe config).
- [X] T019 Implement OBS storage abstraction in `backend/src/main/java/com/facecheck/storage/HuaweiObsStorageService.java`, `backend/src/main/java/com/facecheck/storage/HuaweiObsStorageServiceImpl.java`, and `backend/src/main/java/com/facecheck/storage/ObjectKeyStrategy.java` (Depends on: T018; Accept: backend can upload, delete, and mint temporary preview access without exposing OBS credentials to Flutter).
- [X] T020 Implement `FaceRecognitionProvider`, `HuaweiFrsClient`, `HuaweiCloudFrsFaceRecognitionProvider`, and `MockFaceRecognitionProvider` in `backend/src/main/java/com/facecheck/face/FaceRecognitionProvider.java`, `backend/src/main/java/com/facecheck/infrastructure/huawei/HuaweiFrsClient.java`, `backend/src/main/java/com/facecheck/face/HuaweiCloudFrsFaceRecognitionProvider.java`, and `backend/src/main/java/com/facecheck/face/MockFaceRecognitionProvider.java` (Depends on: T018; Accept: business code can call detect/enroll/search/compare/delete through a provider abstraction without knowing SDK or HTTP details).
- [X] T021 Configure Redis namespaces and RabbitMQ queues/retry/DLQ for face-photo tasks in `backend/src/main/java/com/facecheck/infrastructure/redis/RedisKeyFactory.java`, `backend/src/main/java/com/facecheck/infrastructure/messaging/RabbitTopologyConfig.java`, and `backend/src/main/resources/application-local.yml` (Depends on: T003, T018, T019, T020; Accept: `auth:blacklist:*`, `checkin:idempotency:*`, `session:qr:*`, `face.photo.register*`, and delete-compensation queues exist with retry and dead-letter support).
- [X] T022 Write mock adapter tests for OBS/FRS plus container-backed support tests in `backend/src/test/java/com/facecheck/storage/HuaweiObsStorageServiceTest.java`, `backend/src/test/java/com/facecheck/face/HuaweiCloudFrsFaceRecognitionProviderTest.java`, and `backend/src/test/java/com/facecheck/infrastructure/RedisRabbitSupportTest.java` (Depends on: T019, T020, T021, T009; Accept: automated tests prove cloud adapters and container wiring work without any real Huawei cloud calls).

**Checkpoint**: Identity, security, username-only profile/user management, cloud adapters, Redis, and RabbitMQ foundations are ready. Story work can now proceed.

---

## Phase 3: User Story 2 - Backend Face Photo Library (Stage 3)

**Goal**: 让普通用户和管理员通过后端管理用户人脸照片，图片落 OBS，FRS 注册通过 RabbitMQ 异步完成，并把状态与外部引用落 PostgreSQL。

**Independent Test**: 登录普通用户上传一张照片后，接口立即返回 `PENDING_REGISTER`；消费者使用 `MockFaceRecognitionProvider` 或 FRS stub 完成人脸检测和注册；照片列表展示状态；管理员可查看任意用户照片；无人脸、多人脸、低质量照片会失败且不会产生可用引用；第 6 张照片会被 App 和后端共同拒绝。

### Tests for User Story 2 ⚠️

- [X] T023 [P] [US2] Create contract tests for ordinary-user and admin face-photo APIs in `backend/src/test/java/com/facecheck/face/api/FacePhotoControllerContractTest.java` (Depends on: T022; Accept: failing tests define upload/list/delete response contracts for `/api/me/face-photos` and admin user face-photo endpoints, including photo-limit rejection responses).
- [X] T024 [P] [US2] Create integration tests for upload ownership rules, five-photo limits, and list/status behavior in `backend/src/test/java/com/facecheck/face/FacePhotoFlowIntegrationTest.java` (Depends on: T022; Accept: failing tests prove ordinary users can only manage their own photos, admins can inspect all users, and the 6th photo is rejected).
- [X] T025 [P] [US2] Create async registration tests for DetectFace/AddFaces success and failure branches in `backend/src/test/java/com/facecheck/face/messaging/FacePhotoRegisterConsumerTest.java` (Depends on: T022; Accept: failing tests cover success, no-face, multiple-face, low-quality, timeout, and retry-to-DLQ outcomes).

### Implementation for User Story 2

- [X] T026 [US2] Create face-photo schema migration in `backend/src/main/resources/db/migration/V2__face_photo_refs.sql` (Depends on: T022; Accept: `face_photo` and `huawei_face_ref` tables, indexes, user foreign keys, and status columns match the approved data model).
- [X] T027 [P] [US2] Implement `FacePhoto` and `HuaweiFaceRef` entities plus repositories in `backend/src/main/java/com/facecheck/face/model/FacePhoto.java`, `backend/src/main/java/com/facecheck/face/model/HuaweiFaceRef.java`, `backend/src/main/java/com/facecheck/face/repo/FacePhotoRepository.java`, and `backend/src/main/java/com/facecheck/face/repo/HuaweiFaceRefRepository.java` (Depends on: T026; Accept: repositories support current-photo lookup, status updates, per-user photo counts, and FRS reference mapping by `frsFaceId` or external fields).
- [X] T028 [P] [US2] Implement image decoding, MIME, extension, size, and SHA-256 validation in `backend/src/main/java/com/facecheck/face/service/FaceImageValidationService.java` and `backend/src/main/java/com/facecheck/face/service/ImageHashService.java` (Depends on: T019, T026; Accept: invalid payloads are rejected before OBS upload and every accepted image produces a stable checksum).
- [X] T029 [US2] Implement `FacePhotoService` upload workflow in `backend/src/main/java/com/facecheck/face/service/FacePhotoService.java` (Depends on: T027, T028, T019; Accept: ordinary-user and admin uploads store the image in OBS, reject the 6th non-deleted photo on the backend, persist `FacePhoto(PENDING_REGISTER)`, and never expose OBS internals to the client).
- [X] T030 [US2] Implement ordinary-user and admin face-photo controllers in `backend/src/main/java/com/facecheck/face/api/FacePhotoController.java` and `backend/src/main/java/com/facecheck/face/api/AdminFacePhotoController.java` (Depends on: T029, T016; Accept: ordinary users only reach their own photos and admins can upload/list by `userId`).
- [X] T031 [US2] Implement `FacePhotoRegisterTask` publisher in `backend/src/main/java/com/facecheck/face/messaging/FacePhotoRegisterTask.java` and `backend/src/main/java/com/facecheck/face/messaging/FacePhotoRegisterPublisher.java` (Depends on: T029, T021; Accept: every successful upload emits one RabbitMQ registration task with `photoId` and `userId`).
- [X] T032 [US2] Implement the async face-photo registration consumer in `backend/src/main/java/com/facecheck/face/messaging/FacePhotoRegisterConsumer.java` (Depends on: T031, T020, T027; Accept: consumer runs DetectFace then AddFaces, writes `frsFaceId`/`faceSetName`/external fields with `userId` to PostgreSQL, and records failures without creating active references).
- [X] T033 [US2] Implement photo list/status query and preview URL generation in `backend/src/main/java/com/facecheck/face/service/FacePhotoQueryService.java` and `backend/src/main/java/com/facecheck/storage/PreviewUrlService.java` (Depends on: T027, T019; Accept: clients can fetch status, failure reason, and temporary preview URLs without permanent public URLs).
- [X] T034 [US2] Implement photo delete and compensation workflow in `backend/src/main/java/com/facecheck/face/service/FacePhotoDeletionService.java` and `backend/src/main/java/com/facecheck/face/messaging/FacePhotoDeleteCompensationConsumer.java` (Depends on: T027, T019, T020, T021; Accept: deleting a photo soft-disables it in PostgreSQL, attempts OBS+FRS deletion, and records retryable failure states instead of breaking history).

**Checkpoint**: Ordinary-user and admin photo management is backend-complete and independently testable.

---

## Phase 4: User Story 3 - Attendance Sessions and QR Entry (Stage 4)

**Goal**: 让管理员创建、编辑、发布、关闭和取消签到场次，并通过不可猜测 `qrToken` 让 Flutter App 进入公开场次确认接口。

**Independent Test**: 管理员登录后创建并发布场次，系统生成二维码 token；公开接口能基于 `qrToken` 返回场次信息；场次未开始、已截止、已关闭、已取消时，公开接口能返回正确拒绝原因。

### Tests for User Story 3 ⚠️

- [ ] T035 [P] [US3] Create contract tests for admin session APIs and the public session-entry API in `backend/src/test/java/com/facecheck/session/api/AttendanceSessionControllerContractTest.java` (Depends on: T022; Accept: failing tests define create/edit/publish/close/cancel/list and public QR resolution contracts).
- [ ] T036 [P] [US3] Create integration tests for session lifecycle and QR invalidation in `backend/src/test/java/com/facecheck/session/AttendanceSessionIntegrationTest.java` (Depends on: T022; Accept: failing tests cover lifecycle transitions, invalid token rejection, and stale token invalidation after reset).

### Implementation for User Story 3

- [ ] T037 [US3] Create attendance-session schema migration in `backend/src/main/resources/db/migration/V3__attendance_session.sql` (Depends on: T022; Accept: `attendance_session` table stores name, description, start/end time, optional `lateAfterTime`, status, creator, and unique `qrToken`).
- [ ] T038 [P] [US3] Implement `AttendanceSession` entity, repository, and status enum in `backend/src/main/java/com/facecheck/session/model/AttendanceSession.java`, `backend/src/main/java/com/facecheck/session/model/AttendanceSessionStatus.java`, and `backend/src/main/java/com/facecheck/session/repo/AttendanceSessionRepository.java` (Depends on: T037; Accept: session persistence matches the migration and supports status/time-window lookups).
- [ ] T039 [US3] Implement `AttendanceSessionService` for create/edit/publish/close/cancel rules in `backend/src/main/java/com/facecheck/session/service/AttendanceSessionService.java` (Depends on: T038, T016; Accept: only admins can mutate sessions and invalid time windows or status jumps are rejected with stable business errors).
- [ ] T040 [US3] Implement secure QR token generation and cache-backed resolution in `backend/src/main/java/com/facecheck/session/service/QrTokenService.java` (Depends on: T038, T021; Accept: every token is high-entropy, rotation invalidates the old token, and Redis only caches derived lookup results).
- [ ] T041 [US3] Implement the admin session controller in `backend/src/main/java/com/facecheck/session/api/AdminAttendanceSessionController.java` (Depends on: T039, T040; Accept: admins can create/edit/publish/close/cancel/list sessions and retrieve current QR payload information).
- [ ] T042 [US3] Implement the public session-entry controller in `backend/src/main/java/com/facecheck/session/api/PublicSessionEntryController.java` (Depends on: T039, T040; Accept: anonymous callers can resolve `qrToken` into minimal session info plus precise refusal codes when the session is unavailable).

**Checkpoint**: Session creation and QR-based public entry are backend-complete and independently testable.

---

## Phase 5: User Story 1 - Backend Anonymous QR Check-in (Stage 5) 🎯 MVP

**Goal**: 支持扫码后无需登录即可拍照签到，通过 FRS 搜索候选并做人脸比对确认，使用 Redis 做限流与幂等，使用 PostgreSQL 唯一约束防重复签到。

**Independent Test**: 在已有管理员场次和已注册照片的前提下，匿名上传签到照片可得到成功、失败、处理中或重复签到结果；相同 `idempotencyKey` 重试返回首次结果；同用户同场次不会产生第二条有效签到记录；FRS timeout/rate-limit 分支会写可追踪 attempt。

### Tests for User Story 1 ⚠️

- [ ] T043 [P] [US1] Create contract tests for public check-in submit and query endpoints in `backend/src/test/java/com/facecheck/checkin/api/PublicCheckinControllerContractTest.java` (Depends on: T042, T034; Accept: failing tests define multipart submit, result query, and anonymous response field boundaries).
- [ ] T044 [P] [US1] Create a success-path integration test for anonymous QR check-in in `backend/src/test/java/com/facecheck/checkin/AnonymousCheckinSuccessIntegrationTest.java` (Depends on: T042, T034; Accept: failing test proves one valid attempt can create exactly one `AttendanceRecord` when the search candidate passes compare confirmation above threshold).
- [ ] T045 [P] [US1] Create failure-path integration tests for no-face, low-confidence, duplicate sign-in, and idempotent replay in `backend/src/test/java/com/facecheck/checkin/AnonymousCheckinFailureIntegrationTest.java` (Depends on: T042, T034; Accept: failing tests cover duplicate prevention, failure result codes, and no double-write on retries).
- [ ] T046 [P] [US1] Create rate-limit and uniqueness integration tests in `backend/src/test/java/com/facecheck/checkin/CheckinRateLimitIntegrationTest.java` and `backend/src/test/java/com/facecheck/checkin/AttendanceRecordUniquenessTest.java` (Depends on: T042, T022; Accept: failing tests prove Redis throttling plus PostgreSQL uniqueness cooperate correctly under repeated submissions).

### Implementation for User Story 1

- [ ] T047 [US1] Create the check-in schema migration in `backend/src/main/resources/db/migration/V4__checkin_attempt_record.sql` (Depends on: T042; Accept: `attendance_checkin_attempt` and `attendance_record` tables exist with `(session_id, idempotency_key)` and `(session_id, user_id)` uniqueness constraints).
- [ ] T048 [P] [US1] Implement `AttendanceCheckinAttempt` and `AttendanceRecord` entities plus repositories in `backend/src/main/java/com/facecheck/checkin/model/AttendanceCheckinAttempt.java`, `backend/src/main/java/com/facecheck/checkin/model/AttendanceRecord.java`, `backend/src/main/java/com/facecheck/checkin/repo/AttendanceCheckinAttemptRepository.java`, and `backend/src/main/java/com/facecheck/checkin/repo/AttendanceRecordRepository.java` (Depends on: T047; Accept: repositories can persist attempts, query by `attemptId`, and enforce record uniqueness via the database).
- [ ] T049 [US1] Implement Redis-backed anonymous rate-limiting in `backend/src/main/java/com/facecheck/checkin/service/CheckinRateLimitService.java` (Depends on: T021, T048; Accept: the service can throttle by `sessionId + clientIp` and `sessionId + deviceId` without storing any business facts in Redis).
- [ ] T050 [US1] Implement Redis-backed idempotency result caching in `backend/src/main/java/com/facecheck/checkin/service/CheckinIdempotencyService.java` (Depends on: T021, T048; Accept: repeated `idempotencyKey` submissions can return the original result without re-running FRS calls).
- [ ] T051 [US1] Implement check-in image storage and attempt creation in `backend/src/main/java/com/facecheck/checkin/service/CheckinAttemptService.java` (Depends on: T048, T019, T028; Accept: each submission stores the image in OBS under the approved object path and creates one persistent attempt before recognition runs).
- [ ] T052 [US1] Implement FRS detect/search/compare orchestration and user mapping in `backend/src/main/java/com/facecheck/checkin/service/CheckinRecognitionService.java` (Depends on: T048, T020, T027; Accept: one-face validation, threshold checks, candidate search, compare confirmation, and `frsFaceId`/external-field mapping all happen behind the `FaceRecognitionProvider` abstraction).
- [ ] T053 [US1] Implement valid-record persistence and duplicate translation in `backend/src/main/java/com/facecheck/checkin/service/AttendanceRecordService.java` (Depends on: T048; Accept: the first valid match creates one record and any uniqueness conflict becomes `DUPLICATE_CHECKIN` instead of a new success row).
- [ ] T054 [US1] Implement `CheckinService` as the anonymous orchestration entry point in `backend/src/main/java/com/facecheck/checkin/service/CheckinService.java` (Depends on: T039, T040, T049, T050, T051, T052, T053; Accept: the service validates QR/session availability, applies rate-limit/idempotency, updates attempts, and returns only allowed anonymous result fields).
- [ ] T055 [US1] Implement the public check-in controller in `backend/src/main/java/com/facecheck/checkin/api/PublicCheckinController.java` (Depends on: T054; Accept: anonymous clients can submit `qrToken`, `idempotencyKey`, `deviceId`, and photo, then query final results by `attemptId`).
- [ ] T056 [US1] Implement `PROCESSING` extension scaffolding and future queue contract in `backend/src/main/java/com/facecheck/checkin/messaging/CheckinProcessTask.java` and `backend/src/main/java/com/facecheck/checkin/service/CheckinAsyncPolicy.java` (Depends on: T048, T021; Accept: Phase 1 keeps the synchronous FRS path, while the current API can already express `PROCESSING` and future RabbitMQ peak-shaving can be enabled without changing external contracts).
- [ ] T057 [US1] Implement external-call audit capture and manual-review result mapping in `backend/src/main/java/com/facecheck/checkin/service/CheckinResultMapper.java` and `backend/src/main/java/com/facecheck/admin/service/ExternalCallAuditService.java` (Depends on: T052, T054; Accept: FRS timeout, rate-limit, and ambiguous mapping paths create traceable attempt results instead of silent drops).

**Checkpoint**: Backend anonymous QR check-in flow is MVP-complete and independently testable.

---

## Phase 6: User Story 2 & User Story 3 - Records, Exception Review, and Audit (Stage 6)

**Goal**: 支持普通用户查看个人签到记录，管理员查看全局/场次记录与异常尝试，并为关键操作生成审计日志；系统状态和系统配置后端能力作为第二阶段扩展保留，不阻塞当前管理员主线交付。

**Independent Test**: 普通用户只能查看自己的记录；管理员能按场次、用户、时间、状态查询记录与异常尝试；管理员处理异常时会留下审计日志；若启用第二阶段扩展，则系统配置变更可持久化。

### Tests for User Story 2 & User Story 3 ⚠️

- [ ] T058 [P] [US2] Create contract tests for `/api/me/attendance-records` in `backend/src/test/java/com/facecheck/checkin/api/MyAttendanceRecordControllerContractTest.java` (Depends on: T057; Accept: failing tests define self-only history responses and verify no admin-only fields leak to ordinary users).
- [ ] T059 [P] [US3] Create integration tests for admin record queries and exception review in `backend/src/test/java/com/facecheck/admin/AdminCheckinReviewIntegrationTest.java` (Depends on: T057; Accept: failing tests cover global records, session records, abnormal attempts, retry, and review-note persistence).
- [ ] T060 [P] [US3] Create audit-log and system-config integration tests in `backend/src/test/java/com/facecheck/admin/AuditLogIntegrationTest.java` and `backend/src/test/java/com/facecheck/admin/SystemConfigIntegrationTest.java` (Depends on: T057; Accept: failing tests prove admin actions emit auditable rows and configuration writes persist correctly).

### Implementation for User Story 2 & User Story 3

- [ ] T061 [US3] Create audit/config schema migration in `backend/src/main/resources/db/migration/V5__audit_config_external_logs.sql` (Depends on: T057; Accept: `audit_log`, `system_config`, and `external_service_call_log` tables exist with indexes defined in the plan).
- [ ] T062 [P] [US3] Implement `AuditLog`, `SystemConfig`, and `ExternalServiceCallLog` entities plus repositories in `backend/src/main/java/com/facecheck/admin/model/AuditLog.java`, `backend/src/main/java/com/facecheck/admin/model/SystemConfig.java`, `backend/src/main/java/com/facecheck/admin/model/ExternalServiceCallLog.java`, and matching repositories (Depends on: T061; Accept: admin and infrastructure services can persist and query all three models).
- [ ] T063 [US2] Implement personal attendance-record query service and controller in `backend/src/main/java/com/facecheck/checkin/service/MyAttendanceRecordQueryService.java` and `backend/src/main/java/com/facecheck/checkin/api/MyAttendanceRecordController.java` (Depends on: T048, T016, T061; Accept: authenticated users can only retrieve their own history with minimal result fields).
- [ ] T064 [US3] Implement admin attendance-record query service and controller in `backend/src/main/java/com/facecheck/admin/service/AdminAttendanceRecordQueryService.java` and `backend/src/main/java/com/facecheck/admin/api/AdminAttendanceRecordController.java` (Depends on: T048, T062; Accept: admins can filter global or session-scoped records by session, user, time range, and status).
- [ ] T065 [US3] Implement abnormal-attempt review service and controller in `backend/src/main/java/com/facecheck/admin/service/CheckinReviewService.java` and `backend/src/main/java/com/facecheck/admin/api/AdminCheckinReviewController.java` (Depends on: T048, T057, T062; Accept: admins can list abnormal attempts, append review notes, mark items reviewed, and trigger safe retries without forging new valid records).
- [ ] T066 [US3] Implement system-config service and controller in `backend/src/main/java/com/facecheck/admin/service/SystemConfigService.java` and `backend/src/main/java/com/facecheck/admin/api/SystemConfigController.java` (Depends on: T062; Accept: only whitelisted keys such as threshold, rate-limit, and file-size settings are viewable and editable by admins).
- [ ] T067 [US3] Implement system-state query service and controller in `backend/src/main/java/com/facecheck/admin/service/SystemStateService.java` and `backend/src/main/java/com/facecheck/admin/api/SystemStateController.java` (Depends on: T018, T019, T020, T021, T062; Accept: admins can inspect database, Redis, RabbitMQ, FRS, and OBS health summaries from one backend endpoint).
- [ ] T068 [US3] Wire `AuditLogService` into session, face-photo, review, and config flows in `backend/src/main/java/com/facecheck/admin/service/AuditLogService.java` and the calling services from earlier tasks (Depends on: T062, T039, T034, T065, T066; Accept: admin-triggered mutations write durable audit rows with actor, target, action, and summary details).

**Checkpoint**: Record query, admin exception handling, and audit logging are backend-complete and independently testable; system state/config endpoints remain second-stage extensions.

---

## Phase 7: App Foundation (Stage 7, Shared Mobile Infrastructure)

**Purpose**: 在认证和公开签到契约稳定后，建立 Flutter App 的 API、路由、状态管理、安全存储和登录壳层。

- [ ] T069 Create the shared Flutter feature/router/service structure in `app/lib/features/`, `app/lib/router/app_router.dart`, `app/lib/services/`, and `app/lib/shared/` (Depends on: T002, T017, T042; Accept: the app compiles with placeholder public/user/admin route groups that match the stabilized auth and public session-entry contracts).
- [ ] T070 Configure the Dio API client and auth interceptor in `app/lib/services/api_client.dart` and `app/lib/services/auth_interceptor.dart` (Depends on: T069; Accept: API calls share one base client, inject JWT automatically when present, and normalize backend error envelopes).
- [ ] T071 Configure Riverpod application state in `app/lib/shared/providers/app_providers.dart`, `app/lib/features/auth/auth_state_notifier.dart`, and `app/lib/shared/providers/session_providers.dart` (Depends on: T070; Accept: auth/session/base-URL state is centrally managed and testable).
- [ ] T072 Configure `go_router` route groups and access guards in `app/lib/router/app_router.dart` and `app/lib/features/auth/access_policy.dart` (Depends on: T071; Accept: anonymous, ordinary-user, and admin navigation boundaries are enforced in-app without relying on hidden routes).
- [ ] T073 Configure secure token storage and session restoration in `app/lib/services/secure_storage_service.dart` and `app/lib/features/auth/session_restore_service.dart` (Depends on: T071; Accept: JWTs survive app restart, can be cleared on logout, and never leave secure storage).
- [ ] T074 Implement the login page, shared home shell, and logout handling in `app/lib/features/auth/login_page.dart`, `app/lib/features/home/home_page.dart`, and `app/lib/features/auth/logout_action.dart` (Depends on: T070, T071, T072, T073; Accept: both ordinary-user and admin accounts can log in and land on the correct shell experience).
- [ ] T075 [P] Write API client, router, and auth-state tests in `app/test/services/api_client_test.dart`, `app/test/router/app_router_test.dart`, and `app/test/features/auth/auth_state_test.dart` (Depends on: T070, T071, T072, T073, T074; Accept: tests cover token restore, logout invalidation, route guards, and backend error mapping).
- [ ] T076 Configure Android permissions and local environment access in `app/android/app/src/main/AndroidManifest.xml`, `app/android/app/src/main/res/xml/network_security_config.xml`, and `app/lib/shared/config/app_env.dart` (Depends on: T069; Accept: emulator/device builds have camera, internet, and local-backend network configuration in place).

**Checkpoint**: Flutter foundation is ready; feature pages can now be implemented in parallel.

---

## Phase 8: User Story 2 - Flutter User Features (Stage 8)

**Goal**: 完成普通用户个人资料、人脸照片管理和个人签到记录页面，并展示照片处理状态与五张上限提示。

**Independent Test**: 登录普通用户后，个人资料页可编辑本人用户名和密码；人脸照片页能上传、删除、替换照片并显示处理状态；第 6 张照片在上传前就会被拦截；个人记录页只显示本人的历史签到记录。

### Tests for User Story 2 ⚠️

- [ ] T077 [P] [US2] Write widget and state tests for profile, face-photo, and personal-record pages in `app/test/features/profile/profile_page_test.dart`, `app/test/features/face/face_photo_page_test.dart`, and `app/test/features/records/personal_records_page_test.dart` (Depends on: T076; Accept: failing tests define username/password profile behavior, five-photo guard behavior, and ownership-only access expectations).

### Implementation for User Story 2

- [ ] T078 [US2] Implement the personal profile page and controller in `app/lib/features/profile/profile_page.dart` and `app/lib/features/profile/profile_controller.dart` (Depends on: T074, T070, T071; Accept: ordinary users can view and edit only their own username/password fields returned by `/api/me/profile`).
- [ ] T079 [US2] Implement the face-photo management page and repository in `app/lib/features/face/face_photo_page.dart` and `app/lib/features/face/face_photo_repository.dart` (Depends on: T078, T070; Accept: the page can load the current user’s face-photo list, render status/failure information, and surface current photo-count usage).
- [ ] T080 [US2] Implement image selection or camera-based photo upload in `app/lib/features/face/face_photo_capture_service.dart` and `app/lib/features/face/face_photo_upload_controller.dart` (Depends on: T079, T076; Accept: ordinary users can choose or capture an image and upload it only through backend APIs, and the client blocks the 6th photo before submission).
- [ ] T081 [US2] Implement photo delete/replace actions and status cards in `app/lib/features/face/widgets/face_photo_status_card.dart` and `app/lib/features/face/face_photo_actions.dart` (Depends on: T079, T080; Accept: active, pending, failed, and deleted states are visible and actionable where allowed).
- [ ] T082 [US2] Implement the personal attendance-record page and controller in `app/lib/features/records/personal_records_page.dart` and `app/lib/features/records/personal_records_controller.dart` (Depends on: T078, T070; Accept: the page shows only the current user’s record history with minimal result fields).
- [ ] T083 [US2] Harden signed-in user route guards for profile, face photos, and records in `app/lib/router/app_router.dart` and `app/lib/features/auth/access_policy.dart` (Depends on: T072, T078, T079, T082; Accept: anonymous users cannot open personal pages without first logging in).

**Checkpoint**: Ordinary-user Flutter flows are independently testable end to end against the stabilized backend contracts.

---

## Phase 9: User Story 1 - Flutter Anonymous QR Check-in (Stage 9)

**Goal**: 完成未登录扫码签到闭环，包括扫码、场次确认、拍照、上传和结果展示，并在 Android 环境验证硬件能力。

**Independent Test**: 未登录用户扫描二维码后能看到场次确认页、拍照并提交签到照片、看到成功/失败/处理中/已签到结果；流程结束后仍无法直接访问个人中心或个人历史记录。

### Tests for User Story 1 ⚠️

- [ ] T084 [P] [US1] Write widget and state tests for QR scan, session confirm, upload, and result mapping in `app/test/features/checkin/qr_scan_flow_test.dart` and `app/test/features/checkin/checkin_result_page_test.dart` (Depends on: T076, T042, T055; Accept: failing tests define the anonymous scan-to-result state machine before UI implementation).

### Implementation for User Story 1

- [ ] T085 [US1] Implement the QR scan page and controller in `app/lib/features/checkin/qr_scan_page.dart` and `app/lib/features/checkin/qr_scan_controller.dart` (Depends on: T070, T071, T076; Accept: the app can scan a QR code and extract the backend `qrToken` or deep-link payload for public session lookup).
- [ ] T086 [US1] Implement the session-entry repository and confirmation page in `app/lib/features/checkin/session_entry_repository.dart` and `app/lib/features/checkin/session_confirm_page.dart` (Depends on: T085, T070; Accept: anonymous callers can fetch session info and see refusal reasons for unavailable sessions).
- [ ] T087 [US1] Implement anonymous camera capture, preview, and `idempotencyKey` generation in `app/lib/features/checkin/checkin_capture_page.dart` and `app/lib/features/checkin/anonymous_checkin_controller.dart` (Depends on: T086, T076; Accept: the app can capture or preview a sign-in photo and generate a unique `idempotencyKey` per submission).
- [ ] T088 [US1] Implement the public check-in repository and result-polling logic in `app/lib/features/checkin/checkin_repository.dart` and `app/lib/features/checkin/checkin_result_controller.dart` (Depends on: T087, T070; Accept: the app can submit anonymous check-in photos, poll when `PROCESSING` is returned, and stop replaying once the backend returns a final result).
- [ ] T089 [US1] Implement the result page in `app/lib/features/checkin/checkin_result_page.dart` (Depends on: T088; Accept: success, failure, processing, duplicate, not-started, expired, closed, and canceled states each render correct user-facing messages without exposing private data).
- [ ] T090 [US1] Enforce anonymous-flow-only access after scan completion in `app/lib/router/app_router.dart` and `app/lib/features/checkin/anonymous_access_policy.dart` (Depends on: T072, T089; Accept: anonymous check-in completion does not grant access to profile, photos, or personal history unless a real login occurs).
- [ ] T091 [US1] Run Android emulator or real-device validation for camera permission, QR scan, photo upload, network access, and anonymous sign-in in `app/integration_test/anonymous_checkin_android_test.dart` (Depends on: T085, T086, T087, T088, T089, T090; Accept: documented Android validation covers the full anonymous sign-in path with hardware permissions enabled).

**Checkpoint**: Anonymous Flutter QR sign-in is MVP-complete and independently testable on Android.

---

## Phase 10: User Story 3 - Flutter Admin Features (Stage 10)

**Goal**: 完成管理员端用户管理、场次管理、二维码展示、记录查询和异常处理；系统状态和系统配置页面保留为第二阶段扩展。

**Independent Test**: 管理员登录后可以新增、编辑、停用用户，管理场次、查看二维码、查看场次记录与全局记录、处理异常；普通用户无法访问任何第一阶段管理员页面；第二阶段再补系统状态与配置页面。

### Tests for User Story 3 ⚠️

- [ ] T092 [P] [US3] Write widget and state tests for admin user management, session management, records, and exception review pages in `app/test/features/admin/user_management_test.dart`, `app/test/features/admin/session_management_test.dart`, `app/test/features/admin/admin_records_test.dart`, and `app/test/features/admin/exception_review_test.dart` (Depends on: T076, T041, T064, T065; Accept: failing tests define admin-only page behavior for user create/edit/disable flows and first-stage list/detail flows without waiting for second-stage status/config pages).

### Implementation for User Story 3

- [ ] T093 [US3] Implement the admin home and navigation shell in `app/lib/features/admin/admin_home_page.dart` and `app/lib/features/admin/admin_navigation.dart` (Depends on: T074, T072; Accept: admin users land on a dedicated shell with links to first-stage user management, session, record, and review features, while reserving a place for later-stage status/config entries).
- [ ] T093A [US3] Implement the admin user-management list and form pages in `app/lib/features/admin/users/admin_user_list_page.dart` and `app/lib/features/admin/users/admin_user_form_page.dart` (Depends on: T093, T070; Accept: admins can create, edit, and disable users from the app using username as the only business identifier).
- [ ] T094 [US3] Implement the session management list and create/edit page in `app/lib/features/admin/sessions/admin_session_list_page.dart` and `app/lib/features/admin/sessions/admin_session_form_page.dart` (Depends on: T093, T070; Accept: admins can create, edit, publish, close, and cancel sessions from the app).
- [ ] T095 [US3] Implement the QR display/reset page in `app/lib/features/admin/sessions/session_qr_page.dart` (Depends on: T094; Accept: admins can view the current QR payload and trigger token rotation when needed).
- [ ] T096 [US3] Implement the session-record and global-record pages in `app/lib/features/admin/records/session_records_page.dart` and `app/lib/features/admin/records/global_records_page.dart` (Depends on: T093, T070; Accept: admins can browse records by session or globally with filters supported by the backend).
- [ ] T097 [US3] Implement the exception review page and controller in `app/lib/features/admin/review/exception_review_page.dart` and `app/lib/features/admin/review/exception_review_controller.dart` (Depends on: T093, T070; Accept: admins can view abnormal attempts, add notes, and trigger review actions exposed by the backend).
- [ ] T098 [US3] Implement the second-stage system-state and system-config pages in `app/lib/features/admin/system/system_state_page.dart` and `app/lib/features/admin/system/system_config_page.dart` (Depends on: T093, T070; Accept: admins can inspect dependency health and update whitelisted configuration keys after MVP delivery).
- [ ] T099 [US3] Enforce admin-only route guards and unauthorized redirects in `app/lib/router/app_router.dart` and `app/lib/features/auth/access_policy.dart` (Depends on: T093, T093A, T094, T095, T096, T097; Accept: ordinary users and anonymous callers are redirected away from every first-stage admin route, and second-stage status/config routes inherit the same guard when they are enabled later).

**Checkpoint**: Admin Flutter features are independently testable and role-isolated.

---

## Phase 11: Polish & Deployment Readiness (Stage 11)

**Purpose**: 完成端到端验收、本地启动说明、测试数据、云配置说明和剩余风险记录。

- [ ] T100 Refine local backend startup defaults in `docker-compose.yml`, `backend/src/main/resources/application-local.yml`, and `backend/src/main/resources/application-test.yml` (Depends on: T068; Accept: one documented local path boots PostgreSQL, Redis, RabbitMQ, and the backend without hidden manual edits).
- [ ] T101 Configure Flutter environment switching for emulator and device runs in `app/lib/shared/config/app_env.dart` and `app/android/app/src/main/AndroidManifest.xml` (Depends on: T076, T099; Accept: developers can switch backend base URLs without touching feature code).
- [ ] T102 Update operator and developer runbooks in `specs/001-face-checkin-system/quickstart.md`, `docs/local-run.md`, and `docs/huawei-cloud-config.md` (Depends on: T100, T101; Accept: docs cover Docker Compose startup, backend/app launch, and safe FRS/OBS credential setup).
- [ ] T103 Create local seed/test-data initialization in `backend/src/main/resources/db/migration/V6__seed_local_test_data.sql` and `backend/src/test/java/com/facecheck/support/TestDataFactory.java` (Depends on: T061, T068; Accept: local and test environments can provision at least one admin, one ordinary user, one active photo, and one published session).
- [ ] T104 Run and stabilize backend unit, integration, and container tests by updating `backend/pom.xml` and the suites under `backend/src/test/java/com/facecheck/` (Depends on: T068, T103; Accept: auth, adapter, photo, session, check-in, audit, PostgreSQL, Redis, and RabbitMQ tests all pass together).
- [ ] T105 Run and stabilize Flutter test suites by updating `app/pubspec.yaml` and the suites under `app/test/` (Depends on: T083, T091, T099; Accept: login, route, profile, photo, QR check-in, and admin page tests pass together).
- [ ] T106 Run Android acceptance for anonymous check-in and admin smoke flows in `app/integration_test/anonymous_checkin_android_test.dart` and `app/integration_test/admin_android_smoke_test.dart` (Depends on: T091, T099, T101; Accept: emulator or device evidence shows camera, scan, upload, network, and role-gated admin paths working end to end).
- [ ] T107 Record release-readiness notes and a small-scope real Huawei cloud manual validation checklist in `docs/release-readiness.md` (Depends on: T104, T105, T106; Accept: docs describe how to perform a small amount of post-development real FRS/OBS validation outside the automated and load-test paths).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Stage 0)** must complete first; it establishes the backend/app skeleton, Docker Compose, Flyway baseline, shared response/error model, logging, and container-test support.
- **Phase 2 (Stages 1-2)** depends on Phase 1 and blocks all later work; it delivers authentication, authorization, Huawei config, OBS/FRS abstractions, Redis namespaces, and RabbitMQ topology.
- **Phase 3 (Stage 3)** depends on Phase 2; user-photo upload and FRS registration must exist before real user recognition can work.
- **Phase 4 (Stage 4)** depends on Phase 2; session creation and QR resolution must exist before anonymous QR check-in can start.
- **Phase 5 (Stage 5)** depends on Phases 3 and 4; real check-in recognition requires registered photos and published sessions.
- **Phase 6 (Stage 6)** depends on Phase 5; record queries and abnormal-attempt review are meaningless until real attempts and records exist.
- **Phase 7 (Stage 7)** may start as soon as the auth and public-entry contracts from Phases 2 and 4 are stable, and can absorb later anonymous check-in or admin APIs incrementally as Phases 5-6 land.
- **Phase 8 (Stage 8)** depends on Phase 7 and backend photo/profile/query endpoints from Phases 2, 3, and 6.
- **Phase 9 (Stage 9)** depends on Phase 7 and backend QR/session/check-in APIs from Phases 4 and 5.
- **Phase 10 (Stage 10)** depends on Phase 7 and backend admin/session/review APIs from Phases 4 and 6; second-stage status/config pages additionally depend on the optional config/state endpoints from Phase 6.
- **Phase 11 (Stage 11)** depends on all desired backend and Flutter phases being complete.

### User Story Dependencies

- **US1 (P1, 扫码免登录签到)** is the MVP, but implementation depends on backend enablers from Stage 3 and Stage 4: registered face photos plus published attendance sessions.
- **US2 (P2, 个人资料与人脸照片)** depends on authentication and cloud-adapter foundations; its backend portion must exist before US1 can recognize a user.
- **US3 (P3, 管理员场次与异常处理)** depends on authentication and cloud-adapter foundations; its session-management subset must exist before US1 can accept QR entry.

### Mandatory Test Gates

- 每个阶段结束后都必须运行该阶段对应测试，核心业务不得“先实现、后补测试”继续推进。
- Backend story phases require contract/integration tests to fail before implementation and pass before moving on.
- Flutter feature phases require widget/state tests before story sign-off.
- Android-specific hardware flows require emulator or device verification before final acceptance.

### User-Requested Dependency Summary

1. 阶段 0 必须最先完成。
2. 阶段 1 必须在用户照片库和管理员功能前完成。
3. 阶段 2 必须在人脸照片注册和扫码识别前完成。
4. 阶段 3 必须在真实扫码签到识别前完成。
5. 阶段 4 必须在扫码签到前完成。
6. 阶段 5 必须在记录查询和异常处理前完成。
7. Flutter 阶段可以在后端接口契约稳定后并行开发。
8. 每个阶段完成后都必须运行对应测试，不允许无测试继续推进核心业务。

---

## Parallel Opportunities

- T002 and T003 can run in parallel once the repository root exists.
- T018, T019, and T021 can proceed in parallel after profile wiring is stable because they touch separate Huawei/OBS/queue concerns.
- In Phase 3, T023-T025 can be authored in parallel as failing tests, and T027-T028 can proceed in parallel after the schema migration lands.
- In Phase 5, T043-T046 can be written in parallel as failing tests, and T049-T050 can proceed in parallel after check-in entities exist.
- Phase 7 app foundation allows T070-T073 to be split across separate mobile contributors after T069.
- Phases 8, 9, and 10 can be staffed in parallel after Phase 7 plus the corresponding backend API phases are stable.

---

## Parallel Example: User Story 1

```bash
# Backend tests for anonymous check-in
Task: T043 Public check-in contract tests
Task: T044 Success-path anonymous check-in integration test
Task: T045 Failure-path anonymous check-in integration tests
Task: T046 Rate-limit and uniqueness integration tests

# Backend implementation after schema is ready
Task: T049 CheckinRateLimitService
Task: T050 CheckinIdempotencyService
Task: T052 CheckinRecognitionService
Task: T053 AttendanceRecordService

# Flutter anonymous flow after app foundation is stable
Task: T085 QR scan page
Task: T086 Session confirmation page
Task: T087 Capture and preview flow
```

## Parallel Example: User Story 2

```bash
# Backend story tests
Task: T023 Face-photo contract tests
Task: T024 Face-photo ownership integration tests
Task: T025 Face-photo registration consumer tests

# Flutter user feature work after Phase 7
Task: T078 Profile page
Task: T079 Face-photo management page
Task: T082 Personal attendance-record page
```

## Parallel Example: User Story 3

```bash
# Session lifecycle and admin backend work
Task: T035 Session contract tests
Task: T036 Session lifecycle integration tests
Task: T039 AttendanceSessionService
Task: T040 QrTokenService

# First-stage admin Flutter pages after backend review endpoints exist
Task: T094 Session management page
Task: T096 Record pages
Task: T097 Exception review page

# Second-stage admin extension after backend status/config endpoints exist
Task: T098 System state/config pages
```

---

## Implementation Strategy

### MVP First

1. Complete Phase 1 (Stage 0)
2. Complete Phase 2 (Stages 1-2)
3. Complete Phase 3 (Stage 3) and Phase 4 (Stage 4)
4. Complete Phase 5 (Stage 5) and Phase 9 (Stage 9)
5. **STOP and VALIDATE**: verify anonymous QR sign-in end to end with tests and Android hardware checks

### Incremental Delivery

1. Setup + foundational cloud/security stack
2. User photo library and async FRS registration
3. Admin session management and QR entry
4. Anonymous QR check-in MVP
5. Record queries, exception handling, and audit
6. Flutter user features
7. Flutter admin features
8. Second-stage system status/config pages plus final hardening, documentation, and release-readiness review

### Suggested MVP Scope

- Backend: T001-T057
- Flutter: T069-T091
- Acceptance: T100-T106

That scope delivers the smallest end-to-end value slice: admin creates a session, a prepared user scans the QR code, signs in without login, and sees the result on Android.

---

## Notes

- `[P]` means the task can be executed independently in parallel if its listed dependencies are already satisfied.
- No task introduces OpenCV, ONNX, local embeddings, Kafka, microservices, or an independent remote self-built face-recognition service.
- No task lets Flutter call PostgreSQL, Redis, RabbitMQ, OBS, FRS, or the server filesystem directly; every upload and every recognition step stays behind the Spring Boot backend boundary.
- No task relies on Hibernate `ddl-auto` for production schema management; Flyway is the authoritative path.
- Every task references concrete files so an agent can execute it without needing extra repository discovery first.
