# kotoba

일본어 학습 앱 모노레포.

```
kotoba/
├── backend/   Spring Boot API 서버
├── app/       Flutter 앱 (추가 예정)
└── README.md
```

## 현재 상태

1단계: 백엔드 뼈대만 있습니다. 인증, 도메인 기능, AI 연동은 아직 없습니다.

## 기술 스택 (backend)

- Java 21 (LTS), Spring Boot 4.1.x, Gradle (Wrapper 포함)
- Spring Web MVC, Validation, Data JPA, Actuator
- DB: 개발/테스트 H2 (PostgreSQL 호환 모드), 운영 PostgreSQL

## 패키지 구조

```
com.kotoba.backend
├── controller   HTTP 요청/응답 (REST API)
├── service      비즈니스 로직, 트랜잭션 경계
├── repository   Spring Data JPA 리포지토리
└── domain       엔티티 등 도메인 모델
```

## 실행 방법

필요: JDK 21. Gradle은 Wrapper(`gradlew`)가 자동으로 받아옵니다.

```powershell
cd backend

# 테스트
.\gradlew.bat test

# 개발 서버 실행 (기본 프로필 dev = H2 인메모리)
.\gradlew.bat bootRun
```

macOS/Linux에서는 `./gradlew`를 사용합니다.

실행 후 확인:

| URL | 설명 |
|---|---|
| http://localhost:8080/api/health | 앱 헬스체크 → `{"status":"UP"}` |
| http://localhost:8080/actuator/health | Actuator 헬스체크 (DB 상태 포함) |
| http://localhost:8080/h2-console | H2 콘솔 (dev 전용). JDBC URL: `jdbc:h2:mem:kotoba`, 사용자 `sa`, 비밀번호 없음 |

## 프로필

| 프로필 | DB | 설정 파일 |
|---|---|---|
| `dev` (기본) | H2 인메모리, `MODE=PostgreSQL` | `application-dev.yml` |
| `prod` | PostgreSQL (환경변수로 접속) | `application-prod.yml` |

`prod` 실행에 필요한 환경변수는 [`backend/.env.example`](backend/.env.example)을 참고하세요.

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:postgresql://<host>:5432/kotoba"
$env:DB_USERNAME="..."
$env:DB_PASSWORD="..."
.\gradlew.bat bootRun
```

> 아직 실제 PostgreSQL에는 연결하지 않았습니다.

## 메모: PostgreSQL 전환 시 확인할 항목

H2의 PostgreSQL 호환 모드는 완전한 호환이 아닙니다. 실제 PostgreSQL로 옮길 때 아래를 확인합니다.

- [ ] **스키마 관리 도구 도입**: prod는 `ddl-auto: validate`라서 테이블을 직접 만들지 않습니다. Flyway 등으로 마이그레이션 스크립트를 관리해야 합니다.
- [ ] **ID 생성 전략**: `GenerationType.IDENTITY`와 `SEQUENCE`의 동작이 H2와 다를 수 있습니다. 시퀀스 이름과 증가값을 확인합니다.
- [ ] **PostgreSQL 전용 타입**: `jsonb`, 배열, `text`, `timestamptz`, enum 타입은 H2에서 다르게 동작하거나 지원되지 않습니다.
- [ ] **대소문자/예약어**: dev는 `DATABASE_TO_LOWER=TRUE`로 소문자 식별자를 쓰도록 맞췄습니다. `user`, `order` 같은 예약어를 테이블명으로 쓰지 않습니다.
- [ ] **문자열 비교/정렬**: PostgreSQL의 대소문자 구분, collation, `ILIKE`, 일본어 정렬 결과를 실제 DB에서 확인합니다.
- [ ] **네이티브 쿼리**: `@Query(nativeQuery = true)`는 PostgreSQL에서 다시 테스트합니다.
- [ ] **테스트 DB**: 가능하면 통합 테스트를 실제 PostgreSQL(예: Testcontainers, Docker 필요)로도 돌리는 방안을 검토합니다.
- [ ] **커넥션 풀 설정**: HikariCP `maximum-pool-size`, 타임아웃 값을 운영 환경에 맞게 조정합니다.
