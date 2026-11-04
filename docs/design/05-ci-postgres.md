# 5단계 설계: GitHub Actions CI 와 PostgreSQL 전환 (Flyway)

> 상태: **구현 완료, CI 검증 대기**. 배포(EC2)와 Flutter 는 이 단계 범위가 아닙니다.
> 검토 때 확정한 사항: 확인 요청 6개는 모두 제안대로 진행. 실험 후 로컬과 원격의 검증 브랜치를 모두 삭제하고 결과를 이 문서에 기록. `validate`가 varchar 길이를 못 잡으면 `SchemaConsistencyTest`에 길이 검사를 둠. 검사 범위는 인덱스, UNIQUE, FK, NOT NULL, 길이로 한정.
>
> 구현하면서 확인한 것:
> - **V1 작성 기준:** Hibernate가 PostgreSQL 방언으로 생성한 DDL을 기준으로 V1을 썼습니다. 이름만 명시하고 순서만 정리했습니다.
> - **길이 검사:** 설계상 E3 결과와 상관없이 필요하다고 판단해 `SchemaConsistencyTest`에 미리 포함했습니다. E3 실험은 `validate`만으로 길이 차이를 잡는지 기록하는 용도로 진행합니다.
> - **`gradlew` 실행 권한:** Windows에서 커밋해 실행 권한(100755)이 빠져 있어서, git 인덱스에 기록했습니다.

## 1. 목표와 제약

- **목표 1:** push와 PR마다 빌드와 전체 테스트를 자동으로 실행합니다.
- **목표 2:** 운영 DB(PostgreSQL)의 스키마는 Flyway 마이그레이션 SQL이 만들고, Hibernate는 그 스키마가 엔티티와 맞는지 **검증만** 합니다.
- **제약:** 로컬에 Docker가 없어서 PostgreSQL을 직접 띄울 수 없습니다. PostgreSQL 검증은 **GitHub Actions의 서비스 컨테이너**에서만 합니다.
- **유지할 것:** 로컬에서는 지금처럼 Docker 없이 `gradlew test`가 H2로 돌아야 합니다.

## 2. 역할 분담: 개발(H2)과 CI(PostgreSQL)

| | 로컬 개발 / 로컬 테스트 | CI: `h2` 작업 | CI: `postgres` 작업 | 운영 (prod) |
|---|---|---|---|---|
| DB | H2 인메모리 (PostgreSQL 호환 모드) | H2 | **PostgreSQL 17** 서비스 컨테이너 | PostgreSQL |
| 스키마 생성 | Hibernate `create-drop` | Hibernate `create-drop` | **Flyway** (`db/migration`) | **Flyway** |
| Hibernate | 스키마 생성 | 스키마 생성 | `ddl-auto: validate` | `ddl-auto: validate` |
| 프로필 | `dev` | `dev` | `pgtest` (테스트 전용) | `prod` |
| 실행 테스트 | 전체 | 전체 | **전체** + PostgreSQL 전용 스키마 검사 | - |

- **H2 쪽 역할: 빠른 피드백입니다.** Docker 없이 몇십 초 만에 비즈니스 로직 회귀를 잡습니다.
- **PostgreSQL 쪽 역할: 운영과 같은 조건의 최종 확인입니다.** 같은 테스트 전체를 운영과 같은 DB에서 다시 돌립니다. 확인하는 것은 Flyway 스키마, 실제 SQL 방언, 실제 동시성 동작입니다.
- **dev(H2)에서는 Flyway를 끄고 Hibernate가 스키마를 만듭니다.** 마이그레이션 SQL은 PostgreSQL 문법으로만 작성하고, H2 호환성은 신경 쓰지 않기 위해서입니다. 다른 방법은 Flyway를 H2에서도 돌리는 것입니다. 로컬에서도 마이그레이션 오타를 잡을 수 있지만, 앞으로 PostgreSQL 전용 문법(부분 인덱스, `jsonb` 등)을 쓸 때마다 H2와 충돌합니다. 그래서 채택하지 않았습니다.

### 두 DB 사이에 어긋날 수 있는 부분 (한계)

H2 테스트가 통과해도 PostgreSQL에서 다를 수 있는 부분입니다. 그래서 PostgreSQL 작업을 **필수 검사**로 둡니다.

| 영역 | 어긋날 수 있는 이유 | 대응 |
|---|---|---|
| 스키마 | 로컬은 Hibernate가 만든 스키마, 운영은 Flyway SQL. 엔티티를 바꾸고 마이그레이션을 빠뜨리면 로컬은 통과함 | `postgres` 작업의 `validate`와 스키마 검사 테스트가 실패 |
| 제약·인덱스 | Hibernate `validate`는 **테이블, 컬럼, 타입만** 검사하고, 인덱스·UNIQUE·FK는 검사하지 않음 | 엔티티 어노테이션과 PostgreSQL 카탈로그를 비교하는 테스트를 추가 (5절) |
| 동시성 | 잠금 대기, 격리 수준, UPDATE 재평가 방식이 DB마다 다름 | 동시 복습 테스트를 PostgreSQL에서도 실행 |
| 시각 정밀도 | PostgreSQL은 마이크로초로 **반올림**, H2와 Java(나노초)는 다를 수 있음 | 9절의 flaky 평가에서 다룸 |
| 문자열 정렬·비교 | collation, 대소문자 규칙이 다름 | 현재 정렬은 시각과 id 기준이라 영향 없음. 일본어 정렬이 생기면 PostgreSQL에서 확인 |
| 에러 메시지, 예외 종류 | UNIQUE 위반 등의 SQLState와 드라이버 예외가 다름 | Spring이 `DataIntegrityViolationException`으로 통일하는지 PostgreSQL에서 확인 (409 테스트) |
| 성능, 실행 계획 | 테스트 데이터가 작아서 실행 계획이 운영과 다름 | 인덱스가 **존재**하는지는 검사함. 대용량에서 실제로 쓰이는지는 범위 밖 (README 체크리스트에 남김) |

## 3. GitHub Actions 워크플로 구조

파일: `.github/workflows/ci.yml`

```
on:  push (모든 브랜치), pull_request, workflow_dispatch
permissions: contents: read          ← 최소 권한
concurrency: 같은 브랜치의 이전 실행은 취소

jobs:
  h2        (ubuntu-latest)
    checkout → JDK 21 (Temurin) → Gradle 설정(캐시, wrapper 검증) → ./gradlew build
    → 테스트 리포트 업로드 (실패해도 업로드)
  postgres  (ubuntu-latest, h2 와 병렬)
    services: postgres:17 (헬스체크 후 시작)
    env: TEST_DB=postgres, DB_URL/USERNAME/PASSWORD, JWT_SECRET  ← 전부 CI 전용 가짜 값
    checkout → JDK 21 → Gradle 설정 → ./gradlew test
    → 테스트 리포트 업로드
```

- **Gradle 캐시:** `gradle/actions/setup-gradle`을 씁니다. Gradle 의존성과 wrapper를 캐시하고, `gradle-wrapper.jar`가 공식 배포본인지도 자동으로 검증합니다.
- **Actions 버전:** 공식 액션을 **주 버전 태그**로 고정합니다. 구현 시점(2026-11)의 최신 주 버전은 `actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, `actions/upload-artifact@v7`입니다. 공급망 공격을 더 강하게 막으려면 커밋 SHA로 고정하는 방법도 있습니다. 개선 후보로 남깁니다.
- **비밀값:** `secrets.*`를 쓰지 않습니다. PostgreSQL 비밀번호와 `JWT_SECRET`은 서비스 컨테이너와 테스트 안에서만 쓰는 가짜 값입니다. 워크플로 파일에 그대로 적고, 값 자체에 `ci-only`를 넣어 가짜임을 드러냅니다.
- **권한:** `contents: read`만 줍니다. 아티팩트 업로드에는 추가 권한이 필요 없습니다.
- **리포트:** `backend/build/reports/tests/test`(HTML)와 `build/test-results/test`(XML)를 `if: always()`로 업로드하고, 7일 보관합니다.
- **소요 시간 예상:** 각 작업 2~4분이고, 두 작업은 병렬로 돕니다.

## 4. 프로필 구성

| 파일 | 위치 | 내용 |
|---|---|---|
| `application.yml` | main | 공통. `spring.flyway.enabled: true`(기본), `locations: classpath:db/migration` |
| `application-dev.yml` | main | H2, `ddl-auto: create-drop`, **`spring.flyway.enabled: false`** |
| `application-prod.yml` | main | PostgreSQL(환경변수), `ddl-auto: validate`, Flyway 사용, `hibernate.jdbc.time_zone: UTC` |
| `application-pgtest.yml` | **test** | prod와 같은 DB 설정(Flyway + validate), 접속 정보와 `JWT_SECRET`은 환경변수. 테스트 클래스패스에만 있어서 운영 jar에 포함되지 않음 |

**테스트가 어느 DB로 돌지 선택하는 방법**
- `IntegrationTestSupport`의 `@ActiveProfiles("dev")`를 `@ActiveProfiles(resolver = TestDatabaseProfileResolver.class)`로 바꿉니다.
- 리졸버는 환경변수 `TEST_DB=postgres`이면 `pgtest`를, 아니면 `dev`를 돌려줍니다.
- 로컬에서는 아무 설정 없이 지금처럼 H2로 돕니다. CI의 `postgres` 작업만 `TEST_DB=postgres`를 줍니다.
- 테스트 코드는 바꾸지 않고, 같은 테스트 전체를 두 DB에서 실행합니다.

**Flyway 의존성:** Spring Boot 4의 Flyway 스타터와 `flyway-database-postgresql`(Flyway 10부터 DB별 모듈 분리)을 추가합니다. 정확한 아티팩트 이름은 구현할 때 확인합니다.

## 5. 엔티티와 스키마가 맞는지 검사하는 방법

검사는 두 겹입니다.

1. **Hibernate `ddl-auto: validate`:** 엔티티의 모든 테이블, 컬럼, 타입이 Flyway 스키마에 있는지 확인합니다. 하나라도 없거나 타입이 다르면 **컨텍스트 기동이 실패**합니다. 그러면 `postgres` 작업의 모든 통합 테스트가 실패합니다.
2. **`SchemaConsistencyTest` (PostgreSQL에서만 실행):** `validate`가 보지 않는 것을 검사합니다.
   - **기대값:** `@Entity` 클래스의 어노테이션을 리플렉션으로 읽습니다.
     - `@Table(indexes)`: 인덱스 이름과 컬럼
     - `@Table(uniqueConstraints)`: UNIQUE 제약 이름과 컬럼
     - `@ManyToOne`/`@OneToOne`의 `@JoinColumn`: FK 컬럼과 참조 테이블
     - `@Version`: `version` 컬럼이 NOT NULL인지
     - `nullable = false` 컬럼: NOT NULL인지
   - **실제값:** PostgreSQL 카탈로그(`pg_indexes`, `information_schema.table_constraints` 등)를 조회합니다.
   - 둘을 비교해서, 어노테이션에 있는데 DB에 없으면 실패합니다.
   - 기대값을 엔티티에서 읽으므로, 엔티티에 인덱스를 추가하고 마이그레이션을 빠뜨리면 바로 실패합니다.
   - **반대 방향 검사:** 엔티티와 무관하게 DB에만 있는 FK를 금지합니다. 예를 들어 `review_logs.card_id`에는 FK가 **없어야** 합니다(기록 보존 설계). 이렇게 의도적으로 없는 FK도 검사합니다.

## 6. V1 마이그레이션 (`V1__init_schema.sql`)

현재 엔티티 6개를 PostgreSQL 문법으로 옮깁니다.

| 엔티티 필드 타입 | PostgreSQL 타입 | 이유 |
|---|---|---|
| `Long id` (IDENTITY) | `bigint generated by default as identity` | `GenerationType.IDENTITY`와 일치 |
| `Instant` | `timestamp(6) with time zone` | Hibernate 6+가 `Instant`를 UTC timestamptz로 매핑 |
| `LocalDate` | `date` | |
| `String` (length = n) | `varchar(n)` | 엔티티의 `length`와 같은 길이 |
| `int` / `Integer` | `integer` | |
| `boolean` | `boolean` | |
| enum (`EnumType.STRING`) | `varchar(n)` + `CHECK (값 IN (...))` | PostgreSQL enum 타입은 값 추가가 번거로움. Hibernate가 H2에 만드는 CHECK와 같은 효과 |
| `@Version Long` | `bigint not null` | |

- **이름:** 제약과 인덱스 이름은 엔티티에 적은 이름을 그대로 씁니다(`uk_members_email`, `idx_review_states_deck_due` 등). Hibernate가 자동으로 이름을 붙이는 FK는 `fk_<테이블>_<컬럼>`으로 명시합니다.
- **FK:** 모두 `ON DELETE` 없이 기본 동작(NO ACTION)입니다. 삭제 순서는 서비스가 책임지는 3·4단계 설계를 따릅니다.
- **의도적으로 FK를 두지 않는 곳:** `review_states.deck_id`/`member_id`(비정규화 컬럼), `review_logs.deck_id`/`card_id`(기록 보존)입니다.

## 7. Flyway 마이그레이션 규칙

1. **한 번 적용된(main에 머지된) 마이그레이션 파일은 절대 수정하지 않습니다.** Flyway는 적용한 파일의 체크섬을 기록합니다. 내용이 바뀌면 `validate`에서 기동이 실패합니다. 고칠 것이 있으면 새 버전 파일로 고칩니다.
2. **파일 이름:** `V{번호}__{설명}.sql`입니다. 예: `V2__add_card_tags.sql`. 번호는 1씩 늘리고, 설명은 영문 소문자와 밑줄로 씁니다.
3. **엔티티를 바꾸는 PR에는 마이그레이션도 함께 넣습니다.** 빠뜨리면 CI `postgres` 작업이 실패합니다.
4. **PostgreSQL 문법으로 씁니다.** H2에서는 실행하지 않습니다.
5. **운영에서 `flyway clean` 금지:** Spring Boot 기본값이 `clean-disabled: true`이고, 그대로 둡니다.
6. **되돌리기는 새 마이그레이션으로 합니다.** Flyway 무료판은 undo가 없으므로, 이전 상태로 가는 `V{n+1}`을 작성합니다.
7. **데이터가 있는 테이블의 변경**(NOT NULL 컬럼 추가, 컬럼 이름 변경)은 여러 단계로 나눕니다. 예: nullable로 추가 → 값 채우기 → NOT NULL 설정. 그러면 배포 중에도 앱이 깨지지 않습니다.
8. **운영 DB는 아직 없으므로** `baseline`은 쓰지 않습니다. 빈 DB에 V1부터 적용합니다.

## 8. CI가 진짜로 문제를 잡는지 검증하는 방법

로컬에는 PostgreSQL이 없어서, 검증용 브랜치에 일부러 어긋난 커밋을 push합니다. main에는 넣지 않습니다.

| 실험 | 예상 결과 |
|---|---|
| E1 엔티티에 컬럼을 추가하고 마이그레이션은 그대로 둠 | `postgres` 작업 실패 (`validate`가 컬럼 없음을 감지). `h2` 작업은 통과 |
| E2 마이그레이션에서 인덱스 하나를 빼고 엔티티는 그대로 둠 | `postgres` 작업의 `SchemaConsistencyTest` 실패. `h2` 작업은 통과 |
| E3 마이그레이션의 `varchar` 길이를 엔티티와 다르게 함 | `validate`가 길이를 검사하지 않으면 통과할 수 있음. **결과를 그대로 기록합니다** |

- 실험 순서: 브랜치 `ci-check/schema-drift`에 E1·E2를 넣고 push → `gh run watch`로 결과 확인 → 브랜치 삭제.
- 이 실험은 원격에 브랜치를 push하는 일이므로, **실행 전에 다시 승인을 받습니다.**

## 9. 타이밍에 민감한 테스트의 flaky 평가

| 테스트 | 위험 | 평가 |
|---|---|---|
| `parallelReviewsNeverLoseUpdates` | 스레드 8개가 같은 행을 갱신. 몇 개가 409가 될지는 실행마다 다름 | **낮음.** 409 개수가 아니라 "200 수 = 반영 수 = 로그 수"라는 불변식만 검사. 최초 커밋자는 항상 성공하므로 `ok ≥ 1`도 항상 참. PostgreSQL에서는 뒤 요청이 행 잠금을 기다렸다가 `WHERE version = ?`를 다시 평가해 0행 → 409. 남은 위험은 커넥션 풀 고갈(스레드 8 < 풀 10)과 잠금 대기 시간 초과(PostgreSQL 기본은 무제한, `Future.get` 30초) |
| `concurrentReviewOfSameCardConflicts` | 두 트랜잭션의 순서 | **없음.** "A 읽기 → B 커밋 → A 쓰기"를 코드로 순서대로 실행하는 결정적 테스트. A는 읽기만 해서 잠금이 없으므로 B가 기다리지 않음 |
| 시각 기반 테스트 | 실제 시간 흐름 | **없음.** 모두 `MutableClock`으로 고정 |
| **시각 정밀도** | `IntegrationTestSupport`가 Clock을 `Instant.now()`로 초기화함. Linux JDK는 **나노초**까지 줄 수 있는데 PostgreSQL은 마이크로초로 **반올림**함. DB에서 읽은 시각과 Clock 값을 정확히 비교하는 테스트가 있으면 드물게 1µs 차이로 실패할 수 있음 | **중간 (예방 필요).** 현재 핵심 시각 테스트는 `setSeoulTime`(초 단위)을 써서 안전함. 그래도 기본 초기화를 `Instant.now().truncatedTo(MILLIS)`로 바꿔 원인을 없앰 |
| 쿼리 수 테스트 | Hibernate 통계가 DB에 따라 달라지는지 | **낮음.** 같은 JPQL이면 SQL 문 수가 같음. 두 크기에서 "같은지"를 비교하므로 절대값 차이에 강함. 다만 `≤ 4` 같은 상한이 PostgreSQL에서 다르면 실패하므로 첫 CI 실행에서 확인 |
| 테스트 간 데이터 | 테스트끼리 DB를 공유 | **낮음.** 매 테스트 전 모든 테이블 삭제. 존재하지 않는 id로 `999_999`를 쓰는데, PostgreSQL 시퀀스가 테스트 동안 그 값에 도달할 가능성은 없음 |
| JVM 기본 시간대 | CI 러너는 UTC, 로컬은 KST | **낮음.** 코드가 `ZoneId.systemDefault()`를 쓰지 않음(설정한 `Asia/Seoul`을 씀). 로컬과 CI 두 환경에서 모두 돌리는 것 자체가 이 위험을 검증함 |

**대응 원칙**
- **자동 재시도(retry)는 넣지 않습니다.** 재시도는 진짜 동시성 버그를 가립니다.
- flaky가 실제로 관찰되면, 원인을 찾을 때까지 해당 테스트만 `@Tag("flaky")`로 분리해 따로 실행합니다. 결과는 기록합니다.
- 동시성 테스트가 실패하면 원인을 알 수 있게, 실패 메시지에 응답 상태 목록을 포함합니다.

## 10. README 체크리스트 검증 계획

CI `postgres` 작업이 통과한 뒤 항목별로 표시합니다.
- ✅ **CI가 검증함:** 스키마, timestamptz, boolean/varchar 매핑, 낙관적 락, UNIQUE 위반 → 409, 인덱스 존재, 날짜 계산 방식, 네이티브 쿼리 없음
- ⏳ **아직 검증할 수 없음:** 실제 운영 데이터 규모의 실행 계획, 커넥션 풀 크기, 일본어 정렬, `refresh_tokens` 정리 배치
- 이 두 가지를 구분해 표시하고, 근거(어느 테스트나 단계인지)를 함께 적습니다.

README 상단에 CI 배지를 추가합니다: `https://github.com/monenon-dev/kotoba/actions/workflows/ci.yml/badge.svg`
