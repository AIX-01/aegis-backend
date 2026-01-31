# AEGIS Backend

> Agent 기반 안전 모니터링 시스템 - 백엔드

## 개요

AEGIS Backend는 CCTV 모니터링 시스템의 REST API, 인증/인가, 실시간 알림, 미디어 서버 연동을 담당하는 Spring Boot 애플리케이션입니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Framework | Spring Boot 3.5.9 |
| Language | Java 21 |
| Database | PostgreSQL, Spring Data JPA |
| Cache | Redis, Spring Data Redis |
| Security | Spring Security, JWT (jjwt 0.12) |
| Storage | AWS S3 SDK v2 (MinIO 호환) |
| Docs | SpringDoc OpenAPI 2.8 |
| Build | Gradle 8.x |

## 프로젝트 구조

```
src/main/java/com/aegis/aegisbackend/
├── AegisBackendApplication.java    # 메인 클래스
├── domain/                         # 도메인 계층
│   ├── auth/                       # 인증
│   │   ├── controller/AuthController.java
│   │   ├── dto/AuthDto.java
│   │   └── service/AuthService.java
│   ├── camera/                     # 카메라
│   │   ├── controller/CameraController.java
│   │   ├── dto/CameraDto.java
│   │   ├── entity/Camera.java
│   │   ├── entity/UserCamera.java
│   │   ├── repository/CameraRepository.java
│   │   ├── repository/UserCameraRepository.java
│   │   └── service/CameraService.java
│   ├── event/                      # 이벤트
│   │   ├── controller/EventController.java
│   │   ├── dto/EventDto.java
│   │   ├── entity/Event.java
│   │   ├── repository/EventRepository.java
│   │   └── service/EventService.java
│   ├── notification/               # 알림
│   │   ├── controller/NotificationController.java
│   │   ├── dto/NotificationDto.java
│   │   ├── entity/Notification.java
│   │   ├── repository/NotificationRepository.java
│   │   ├── service/NotificationService.java
│   │   └── service/SseEmitterService.java
│   ├── stats/                      # 통계
│   │   ├── controller/StatsController.java
│   │   ├── dto/StatsDto.java
│   │   └── service/StatsService.java
│   ├── stream/                     # 스트림 (DTO만)
│   │   └── dto/StreamDto.java
│   └── user/                       # 사용자
│       ├── controller/UserController.java
│       ├── dto/UserDto.java
│       ├── entity/User.java
│       ├── repository/UserRepository.java
│       └── service/UserService.java
├── global/                         # 전역 설정
│   ├── common/
│   │   ├── dto/PageResponse.java
│   │   └── enums/
│   │       ├── EventRisk.java      # NORMAL, SUSPICIOUS, ABNORMAL
│   │       ├── EventStatus.java    # PROCESSING, ANALYZED
│   │       ├── EventType.java      # ASSAULT, BURGLARY, DUMP, SWOON, VANDALISM
│   │       ├── NotificationType.java # ALERT, WARNING, INFO, SUCCESS
│   │       └── UserRole.java       # USER, ADMIN
│   ├── config/
│   │   ├── AsyncConfig.java        # 비동기 설정
│   │   ├── DataInitializer.java    # 초기 Admin 생성
│   │   ├── OpenApiConfig.java      # Swagger 설정
│   │   ├── RedisConfig.java        # Redis 설정
│   │   ├── S3Config.java           # S3 설정
│   │   └── SecurityConfig.java     # Spring Security 설정
│   ├── exception/
│   │   ├── BusinessException.java
│   │   ├── ErrorCode.java
│   │   └── GlobalExceptionHandler.java
│   └── security/
│       ├── CustomUserDetailsService.java
│       ├── JwtAuthenticationFilter.java
│       └── JwtTokenProvider.java
└── infra/                          # 인프라 계층
    ├── agent/                      # AI Agent 연동
    │   ├── AgentWebhookController.java
    │   └── dto/
    │       ├── AnalysisResultRequest.java
    │       └── CreateEventRequest.java
    ├── mediamtx/                   # MediaMTX 연동
    │   ├── ClipExtractionService.java
    │   ├── MediaMTXSyncService.java
    │   └── MediaMTXWebhookController.java
    ├── redis/
    │   └── RedisTokenService.java
    └── s3/
        └── S3Service.java
```

## 설치 및 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun

# 테스트
./gradlew test
```

## 환경 변수

`application.properties` 또는 환경 변수로 설정:

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `DB_URL` | PostgreSQL URL | `jdbc:postgresql://localhost:5432/aegis` |
| `DB_USERNAME` | DB 사용자 | - |
| `DB_PASSWORD` | DB 비밀번호 | - |
| `REDIS_HOST` | Redis 호스트 | `localhost` |
| `REDIS_PORT` | Redis 포트 | `6379` |
| `REDIS_PASSWORD` | Redis 비밀번호 | - |
| `AWS_S3_ACCESS_KEY` | S3 Access Key | - |
| `AWS_S3_SECRET_KEY` | S3 Secret Key | - |
| `AWS_S3_REGION` | S3 리전 | `ap-northeast-2` |
| `AWS_S3_BUCKET` | S3 버킷 | `aegis-clips` |
| `AWS_S3_ENDPOINT` | S3 엔드포인트 (MinIO용) | - |
| `JWT_SECRET` | JWT 서명 키 (256bit 이상) | - |
| `JWT_ACCESS_EXPIRATION` | Access Token 만료 (ms) | `900000` (15분) |
| `JWT_REFRESH_EXPIRATION` | Refresh Token 만료 (ms) | `604800000` (7일) |
| `CORS_ALLOWED_ORIGINS` | CORS 허용 Origin | - |
| `MEDIAMTX_API_URL` | MediaMTX API URL | `http://localhost:9997` |
| `MEDIAMTX_SRT_USER` | SRT 인증 사용자 | - |
| `MEDIAMTX_SRT_PASSWORD` | SRT 인증 비밀번호 | - |
| `ADMIN_EMAIL` | 초기 Admin 이메일 | - |
| `ADMIN_PASSWORD` | 초기 Admin 비밀번호 | - |
| `ADMIN_NAME` | 초기 Admin 이름 | - |

## API 명세

### Auth API (`/api/auth`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/signup` | 회원가입 |
| POST | `/login` | 로그인 |
| POST | `/logout` | 로그아웃 |
| POST | `/refresh` | 토큰 갱신 |
| GET | `/me` | 내 정보 조회 |
| PATCH | `/me` | 프로필 수정 |
| PATCH | `/password` | 비밀번호 변경 |
| DELETE | `/me` | 회원 탈퇴 |

### Camera API (`/api/cameras`)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/` | 카메라 목록 (페이지네이션, 기본 size=6) |
| GET | `/all` | 카메라 전체 목록 |
| GET | `/{id}` | 카메라 상세 |
| PATCH | `/{id}` | 카메라 수정 |

**정렬 순서**: `connected DESC` → `enabled DESC` → `location ASC`

### Event API (`/api/events`)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/` | 이벤트 목록 (페이지네이션, 기본 size=20) |
| GET | `/{id}` | 이벤트 상세 |
| DELETE | `/{id}` | 이벤트 삭제 (Admin) |
| GET | `/{id}/clip` | 클립 다운로드 |
| GET | `/{id}/clip/stream` | 클립 스트리밍 |

### Notification API (`/api/notifications`)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/stream` | SSE 연결 |
| GET | `/` | 알림 목록 |
| DELETE | `/` | 전체 삭제 |

### Stats API (`/api/stats`)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/?type=daily` | 일별 통계 (주간) |
| GET | `/?type=event-types` | 유형별 통계 |
| GET | `/?type=monthly` | 월별 통계 |

### User API (`/api/users`) - Admin 전용

| Method | Path | 설명 |
|--------|------|------|
| GET | `/` | 사용자 목록 (페이지네이션) |
| GET | `/{id}` | 사용자 상세 |
| PATCH | `/{id}` | 사용자 수정 |
| DELETE | `/{id}` | 사용자 삭제 |
| PATCH | `/{id}/approve` | 사용자 승인 |

### Internal API (내부망 전용)

#### Agent Webhook (`/internal/agent`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/events` | 이벤트 생성 |
| PATCH | `/events/{id}/analysis` | 분석 결과 추가 |
| GET | `/test/clip/{cameraName}` | 클립 추출 테스트 |

#### MediaMTX Webhook (`/internal/mediamtx`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/sync` | 카메라 동기화 트리거 |
| POST | `/auth` | 스트림 인증 (MediaMTX 호출) |

## 데이터 모델

### User

| 필드 | 타입 | 설명 |
|------|------|------|
| id | UUID | PK |
| email | String | 이메일 (unique) |
| password | String | 암호화된 비밀번호 |
| name | String | 이름 |
| role | UserRole | USER, ADMIN |
| approved | Boolean | 승인 여부 |
| deleted | Boolean | 탈퇴 여부 |
| createdAt | LocalDateTime | 가입일 |

### Camera

| 필드 | 타입 | 설명 |
|------|------|------|
| id | UUID | PK |
| name | String | 미디어서버 원본 이름 |
| location | String | 장소 |
| connected | Boolean | 연결 상태 |
| enabled | Boolean | 활성화 여부 |
| analysisEnabled | Boolean | AI 분석 활성화 |
| createdAt | LocalDateTime | 생성일 |

### UserCamera (다대다 관계)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| user | User | FK |
| camera | Camera | FK |

### Event

| 필드 | 타입 | 설명 |
|------|------|------|
| id | UUID | PK |
| camera | Camera | FK |
| type | EventType | 이벤트 유형 |
| risk | EventRisk | 위험 수준 |
| status | EventStatus | 처리 상태 |
| occurredAt | LocalDateTime | 발생 시각 |
| clipUrl | String | S3 클립 URL |
| summary | String | AI 요약 |
| report | String | 상세 보고서 |
| riskScore | String | 위험 점수 |
| actions | JSON | 권장 조치 |
| ragReferences | JSON | RAG 참조 |

### Notification

| 필드 | 타입 | 설명 |
|------|------|------|
| id | UUID | PK |
| user | User | FK |
| event | Event | FK (nullable) |
| type | NotificationType | 알림 유형 |
| title | String | 제목 |
| message | String | 메시지 |
| createdAt | LocalDateTime | 생성일 |

## 인증/인가

### JWT 토큰

- **Access Token**: 15분, Authorization 헤더
- **Refresh Token**: 7일, HttpOnly Cookie, Redis 저장

### Spring Security FilterChain

1. `JwtAuthenticationFilter`: JWT 검증 및 SecurityContext 설정
2. 인증 예외 경로: `/api/auth/login`, `/api/auth/signup`, `/api/auth/refresh`, `/internal/**`

### 권한

- `USER`: 기본 사용자 (할당된 카메라만 접근)
- `ADMIN`: 모든 카메라 접근, 사용자 관리

## SSE 알림 시스템

### SseEmitterService

- 사용자별 SSE 연결 관리
- 타임아웃: 30분
- 30초마다 heartbeat 전송

### 이벤트 타입

| 이벤트 | 설명 |
|--------|------|
| `connect` | 연결 성공 |
| `notification` | 새 알림 |
| `camera` | 카메라 상태 변경 |
| `event` | 이벤트 생성/수정 |
| `event-deleted` | 이벤트 삭제 |
| `member` | 멤버 변경 |

## 에러 코드

### Auth

| 코드 | HTTP | 메시지 |
|------|------|--------|
| EMAIL_NOT_FOUND | 401 | 등록되지 않은 이메일입니다. |
| INVALID_PASSWORD | 401 | 비밀번호가 일치하지 않습니다. |
| USER_NOT_APPROVED | 403 | 관리자 승인 대기 중입니다. |
| DUPLICATE_EMAIL | 400 | 이미 등록된 이메일입니다. |
| REFRESH_TOKEN_NOT_FOUND | 401 | Refresh token이 없습니다. |
| INVALID_REFRESH_TOKEN | 401 | 유효하지 않은 refresh token입니다. |
| CURRENT_PASSWORD_MISMATCH | 400 | 현재 비밀번호가 일치하지 않습니다. |
| PASSWORD_TOO_SHORT | 400 | 새 비밀번호는 6자 이상이어야 합니다. |
| USER_DELETED | 403 | 탈퇴한 계정입니다. |

### Camera

| 코드 | HTTP | 메시지 |
|------|------|--------|
| CAMERA_NOT_FOUND | 404 | 카메라를 찾을 수 없습니다. |
| CAMERA_ACCESS_DENIED | 403 | 해당 카메라에 대한 접근 권한이 없습니다. |
| CAMERA_NOT_CONNECTED | 400 | 카메라가 연결되어 있지 않습니다. |

### Event

| 코드 | HTTP | 메시지 |
|------|------|--------|
| EVENT_NOT_FOUND | 404 | 이벤트를 찾을 수 없습니다. |

### S3

| 코드 | HTTP | 메시지 |
|------|------|--------|
| S3_UPLOAD_FAILED | 500 | S3 업로드에 실패했습니다. |
| S3_DOWNLOAD_FAILED | 500 | S3 다운로드에 실패했습니다. |
| S3_DELETE_FAILED | 500 | S3 삭제에 실패했습니다. |

## 외부 연동

### MediaMTX

- **카메라 동기화**: `/internal/mediamtx/sync` 호출 시 MediaMTX API에서 스트림 목록 조회 후 DB 동기화
- **인증 위임**: MediaMTX → `/internal/mediamtx/auth` 호출
  - WebRTC read: JWT 검증
  - SRT publish: ID/PW 검증
  - RTSP/HLS read: 인증 없음 (내부망)

### S3 (MinIO)

- 클립 저장/조회/삭제
- 버킷: `aegis-clips`
- 키 형식: `clips/{eventId}.ts`

### Redis

- **Refresh Token**: `refresh_token:{token}` → `userId` (TTL: 7일)
- **동기화 잠금**: `mediamtx:sync:lock` (TTL: 1초)
- **분석 카메라 목록**: `analysis:cameras` → JSON 배열
- **Pub/Sub 채널**: `camera:analysis:update` (Python Agent 알림)

## 빌드 및 배포

### JAR 빌드

```bash
./gradlew bootJar
java -jar build/libs/aegis-backend-1.0.0.jar
```

### Docker 배포

Caddy 리버스 프록시를 통해 `/api/*` 경로로 서비스됩니다.
- 내부 포트: 8080
- 외부 접근: `https://localhost/api/*`
