# 6-2단계 설계: Render 배포 준비

> 상태: **6-2 준비 완료, 실제 배포 전** (2026-11-05). 다음은 사용자가 5절대로 Render를 설정합니다. 검토 때 확정 사항: 시드는 API 스크립트, 헬스체크 경로 `/api/health`, 리전 싱가포르, `sslmode=require` 사용, 임시 브랜치 검증과 추가 측정 진행.
> 범위는 문서와 파일 준비까지입니다. Render 계정 생성, 로그인, 클라우드 CLI 사용은 하지 않았습니다. 가입과 설정은 사용자가 직접 합니다. **실제 Render 환경에서는 아직 아무것도 검증하지 않았습니다.**
>
> **출처:** 이 문서의 Render 내용은 공식 문서의 **마크다운 원문**(`https://render.com/docs/<페이지>.md`)을 내려받아 직접 읽고 확인했습니다. 가격 페이지와 약관은 HTML 원문에서 확인했습니다. 확인 날짜는 모두 **2026-11-05**입니다. 문서 내용은 바뀔 수 있고, 실제 화면 문구는 다를 수 있습니다.

## 1. 결론 요약

| 질문 | 답 |
|---|---|
| 가입 때 카드가 필요한가 | **공식 문서 기준 불필요.** 다만 가입 화면에서 실제로 요구하지 않는지는 **미확인**입니다 (3절). |
| 기동 시간 2~4분이 제한과 충돌하는가 | 문서에 수치가 있는 제한(15분)과는 **충돌하지 않습니다.** 포트 스캔 제한 시간은 문서에 수치가 없어 **미확인**이므로 첫 배포에서 확인합니다 (4절). |
| 앱이 `PORT`를 읽는가 | 읽지 않아서 **고쳤고**, CI로 검증했습니다 (6.1절, 10절). |
| 가장 큰 새 위험 | Render 문서가 **JVM 앱에는 `1c-2g` 이상을 권장**합니다. 무료 플랜(0.1 CPU, 512 MB)은 권장 사양보다 훨씬 작습니다. CI 측정으로 기동은 확인했지만, Render에서 실제로 안정적으로 도는지는 해 봐야 압니다 (2.3절). |
| 시연 데이터 | **API 스크립트 방식으로 구현**했습니다 (8절). |
| 요청이 몰리면 헬스체크가 실패하는가 | CI의 0.1 CPU 측정에서는 동시 요청 16개에도 실패가 없었고, 가장 느린 응답은 3.9초였습니다. 제한(5초)까지 여유가 크지 않습니다 (10.2절). |

## 2. 무료 조건 재확인 (`06-deploy.md`와 비교)

### 2.1 달라지지 않은 것

아래 값은 요약 도구로 읽었던 `06-deploy.md`의 내용과 원문이 일치합니다.

| 항목 | 원문 내용 | 근거 |
|---|---|---|
| 인스턴스 시간 | 워크스페이스당 월 750시간. 소진 시 다음 달까지 모든 무료 웹 서비스 정지 | [free](https://render.com/docs/free) |
| 잠들기 | 인바운드 트래픽 없이 15분이면 잠듦, 깨어나는 데 약 1분 | [free](https://render.com/docs/free) |
| 사양 | 웹 서비스 0.1 CPU, 512 MB | [compute-plans](https://render.com/docs/compute-plans) |
| 무료 DB | 1 GB, 1개만, 생성 후 30일 만료, 14일 유예 후 삭제, 백업 없음, 관리형 커넥션 풀링 없음 | [free](https://render.com/docs/free) |
| 롤백 | 직전 두 배포까지 | [free](https://render.com/docs/free) |
| 헬스체크 | 5초 안에 응답. 새 배포는 15분 안에 통과. 실행 중에는 15초 실패 시 트래픽 중단, 60초 실패 시 재시작 | [health-checks](https://render.com/docs/health-checks) |
| 시작 명령 제한 | 15분 | [deploys](https://render.com/docs/deploys) |
| `PORT` 기본값 | `10000` | [web-services](https://render.com/docs/web-services) |
| DB 외부 접속 | 기본 `0.0.0.0/0` 허용. 내부 URL은 허용 목록과 무관 | [postgresql](https://render.com/docs/postgresql-creating-connecting) |

### 2.2 `06-deploy.md`에서 미확인이었다가 확인된 것

| 항목 | 이전 | 원문 내용 | 근거 |
|---|---|---|---|
| 기본 주소의 HTTPS | 미확인 | **확인.** 모든 웹 서비스에 `onrender.com` 서브도메인 제공. 로드밸런서가 TLS를 종료하고, HTTP는 HTTPS로 리디렉션 | [web-services](https://render.com/docs/web-services) |
| 무료 서비스의 사용자 도메인 | 미확인 | **확인.** 무료 웹 서비스 지원 기능에 사용자 도메인과 관리형 TLS 포함 | [free](https://render.com/docs/free) |
| 무료 DB 접속 수와 사양 | 미확인 | **확인.** 0.1 CPU, 256 MB, 최대 100 연결. 앱의 커넥션 풀 기본값(10개)보다 충분히 큽니다 | [compute-plans](https://render.com/docs/compute-plans) |

**여전히 미확인:** 무료 서비스의 헬스체크 경로 지원입니다. 헬스체크 문서에 무료 플랜 제외 언급이 없고 무료 제한 목록에도 없어 쓸 수 있어 보이지만, 지원한다고 명시한 문장은 찾지 못했습니다. Dockerfile의 `HEALTHCHECK` 사용 여부도 문서에 언급이 없습니다.

### 2.3 새로 발견한 것 (`06-deploy.md`에 없음)

| 항목 | 원문 내용 | 영향 | 근거 |
|---|---|---|---|
| **JVM 권장 사양** | Docker로 배포한 JVM 앱은 메모리를 많이 쓰므로 `1c-2g` 이상으로 올리기를 권장 | **무료 플랜이 권장보다 작습니다.** 30일 시연용으로만 보는 이유가 하나 더 늘었습니다 | [troubleshooting](https://render.com/docs/troubleshooting-deploys) |
| **대역폭** | Hobby 워크스페이스 월 5 GB 포함. 초과 시 결제 수단이 없으면 그달 남은 기간 동안 무료 서비스 정지 | JSON API라 시연 규모에서는 넉넉해 보이지만, 실제 사용량은 대시보드에서 확인합니다 | [가격](https://render.com/pricing), [free](https://render.com/docs/free) |
| **빌드 시간** | Hobby 월 500분. 결제 수단이 없으면 소진 시 새 빌드 중단 (실행 중 서비스는 유지) | push마다 빌드하지 않도록 자동 배포 범위를 좁힙니다 (5.4절) | [build-pipeline](https://render.com/docs/build-pipeline), [free](https://render.com/docs/free) |
| **요청 제한** | 무료 서비스의 인바운드 요청 제한이 "상당히 낮음". 수치 없음 (**미확인**) | 시드 스크립트는 요청을 하나씩 순서대로 보냅니다 | [troubleshooting](https://render.com/docs/troubleshooting-deploys) |
| **잠들기 중 응답** | 깨어나는 동안 브라우저에는 로딩 페이지를 보여 줌. API 클라이언트가 무엇을 받는지는 **미확인** | Flutter 앱이 HTML 응답을 받을 수 있으므로 7단계 전에 확인합니다 | [free](https://render.com/docs/free) |
| **DB 유지보수** | 무료 DB는 언제든 유지보수나 재시작 가능 | 일시적인 DB 오류가 정상일 수 있습니다 | [free](https://render.com/docs/free) |
| **리전** | 오리건, 오하이오, 버지니아, 프랑크푸르트, 싱가포르. 나중에 변경 불가 | 한국과 지리적으로 가까운 **싱가포르**를 제안합니다. 실제 지연은 측정하지 않았습니다 | [regions](https://render.com/docs/regions) |
| **환경변수의 빌드 인자 변환** | Docker 서비스의 환경변수는 빌드 인자로도 전달됨. Dockerfile에서 비밀값을 참조하면 이미지에 남을 수 있음 | 현재 Dockerfile에는 `ARG`가 없어 안전합니다. **앞으로도 `ARG JWT_SECRET` 같은 줄을 넣으면 안 됩니다** | [docker](https://render.com/docs/docker) |
| **DB 백업 방법** | 무료 DB는 로컬에서 `pg_dump`로 외부 URL에 접속해 받는 것이 가능 | `06-deploy.md` 5.3절의 방법이 공식 안내와 일치합니다 | [postgresql-backups](https://render.com/docs/postgresql-backups) |

### 2.4 `06-deploy.md`에서 고친 것

- **`PORT=8080` 설정 방침을 폐기했습니다.** 11절의 체크 항목과 6.1절 표의 포트 설명을 "앱이 `PORT`를 읽는다"로 바꾸고, 이 문서로 연결했습니다.

## 3. 가입 때 카드 등록

**결론: 공식 문서 기준으로는 필요 없습니다.** 실제 가입 화면에서 요구하는지는 계정을 만들어 보지 않았으므로 **미확인**입니다.

**필요 없다는 근거**

| 출처 | 내용 |
|---|---|
| [첫 배포 튜토리얼](https://render.com/docs/your-first-deploy) | "This tutorial uses free Render resources. No payment is required." |
| [Render 글 (2026-04-23)](https://render.com/articles/platforms-with-a-real-free-tier-for-developers-in-2026) | "No credit card is required." |
| [free](https://render.com/docs/free) | "If you haven't added a payment method, Render instead suspends..." (결제 수단이 없는 무료 사용자를 전제로 설명) |
| [FAQ](https://render.com/docs/faq) | 결제 수단이 없는데 요금이 발생할 상황이면 청구 대신 서비스를 중단 |
| [약관](https://render.com/terms) | 유효한 결제 수단은 "paid aspects of the Service"를 쓸 때 필요 |

**남은 불확실성**

- 위 문서 중 **"가입 절차에 카드 입력이 없다"고 직접 말한 문장은 없습니다.** 가장 가까운 것이 튜토리얼과 Render 글입니다.
- 커뮤니티 포럼 검색 결과에 카드 확인을 요구받았다는 글이 있지만, 포럼 주소(`render.discourse.group`)에 접속이 되지 않아 **원문을 읽지 못했습니다.** 공식 정책인지, 지금도 그런지 판단할 수 없습니다.
- 약관에 따르면 카드를 등록하면 최대 1달러의 임시 승인이 걸릴 수 있습니다.

**요구받으면:** 입력하지 말고 멈춰서 다시 논의합니다. 또한 **카드를 등록하면 무료 서비스도 대역폭이나 빌드 시간 초과분이 청구될 수 있습니다** ([FAQ](https://render.com/docs/faq)).

## 4. 포트 제한과 기동 시간

### 4.1 제한

| 제한 | 문서의 값 | 우리 앱 (0.1 CPU, CI 측정 125~256초) | 판단 |
|---|---|---|---|
| 시작 명령 ([deploys](https://render.com/docs/deploys)) | 15분 | 약 4분 | 충돌 없음 |
| 새 배포 헬스체크 통과 ([health-checks](https://render.com/docs/health-checks)) | 15분 | 약 4분 | 충돌 없음 |
| **포트 스캔** ([troubleshooting](https://render.com/docs/troubleshooting-deploys)) | **제한이 있으나 수치 없음** | 약 4분 | **미확인** |
| 잠든 뒤 깨어날 때의 제한 | 문서에 없음 | 약 4분 | **미확인** |

- **포트 스캔 제한은 실제로 있습니다.** `Port scan timeout reached` 오류의 원인으로 "포트를 열기 전에 스캔이 시간 초과"가 나옵니다. 문서는 포트를 최대한 일찍 열라고 권합니다.
- **수치:** 원문에서 찾지 못했습니다. 검색 요약에 한 사용자의 로그에서 약 15분 만에 실패했다는 내용이 있지만, 원문을 읽지 못한 단일 사례이므로 근거로 쓰지 않습니다.

### 4.2 우리 앱은 포트를 늦게 엽니다

CI 로그(실행 [42004948035](https://github.com/monenon-dev/kotoba/actions/runs/42004948035))를 보면 `Tomcat started`와 `Started BackendApplication`의 차이가 약 8밀리초입니다. Spring Boot는 Flyway와 Hibernate를 포함한 초기화가 끝난 뒤에 포트를 엽니다. 즉, **기동 시간 2~4분 내내 포트가 닫혀 있습니다.**

### 4.3 판단

**문서에 수치가 있는 제한과는 충돌하지 않습니다. 첫 배포에서 포트 스캔을 통과하는지 확인합니다.** 지금 기동 방식을 바꾸지는 않습니다.

- **첫 배포에서 볼 것:** 로그의 `Tomcat started on port`까지 걸린 시간. 측정값은 CI 값이므로 Render에서는 더 빠르거나 느릴 수 있습니다.
- **`Port scan timeout reached`가 나면 시도할 순서**
  1. 로그에서 포트 번호가 `PORT` 값과 맞는지 확인 (설정 문제부터 배제)
  2. `JAVA_TOOL_OPTIONS`에 `-XX:TieredStopAtLevel=1` 추가. CI 측정 1회뿐이라 효과는 불확실
  3. 그래도 실패하면 무료 플랜으로 이 앱을 띄우기 어렵다고 보고 다시 논의
- **쓰지 않을 방법:** `spring.main.lazy-initialization=true`는 기동을 줄일 수 있지만, 초기화를 첫 요청으로 미룹니다. 설정 오류를 기동 시점에 잡는 지금의 원칙(`JWT_SECRET` 검증, Hibernate `validate`)이 약해질 수 있어 먼저 고르지 않습니다.

## 5. Render 클릭 체크리스트

사용자가 직접 진행합니다. **화면 문구는 문서 기준이라 다를 수 있습니다.** 다르면 그 화면에서 멈추고 물어봐 주세요. 비밀값과 서비스 주소는 채팅에 붙여 넣지 마세요.

### 5.0 가입 직전 확인 (가장 먼저)

- [ ] **월 대역폭과 빌드 시간 한도를 공식 문서 원문으로 다시 확인합니다.** 이 문서의 값(Hobby 대역폭 5 GB, 빌드 500분)은 2026-11-05 기준이고 바뀔 수 있습니다. [가격 페이지](https://render.com/pricing), [build-pipeline](https://render.com/docs/build-pipeline), [outbound-bandwidth](https://render.com/docs/outbound-bandwidth)를 봅니다.
- [ ] 같은 김에 [free](https://render.com/docs/free)의 무료 조건(15분 잠들기, DB 30일 만료, 인스턴스 750시간)도 달라지지 않았는지 봅니다. 달라졌으면 진행 전에 알려 주세요.

### 5.1 가입

- [ ] [dashboard.render.com/register](https://dashboard.render.com/register)에서 가입합니다. GitHub 로그인과 이메일 가입 모두 가능합니다 ([문서](https://render.com/docs/git-provider)).
- [ ] **카드 입력을 요구하면 멈춥니다** (3절).
- [ ] 워크스페이스 플랜이 **Hobby**인지 확인합니다.

### 5.2 GitHub 연동: kotoba 레포만 허용

Render는 GitHub 앱으로 연동하며, 접근할 레포를 고를 수 있습니다 ([문서](https://render.com/docs/git-provider)).

- [ ] Render의 **Account Settings > Account Security > Git Deployment Credentials > Add credential**에서 GitHub를 선택합니다.
- [ ] GitHub 화면의 **Repository access**에서 **Only select repositories**를 고르고, **`kotoba`만** 선택합니다. "All repositories"를 고르지 않습니다.
- [ ] 확인: GitHub의 **Settings > Applications > Installed GitHub Apps > Render > Configure**에 `kotoba`만 있는지 봅니다. [이 주소](https://github.com/apps/render/installations/new)에서도 설정을 바꿀 수 있습니다.

`Only select repositories`와 GitHub 설정 메뉴는 GitHub 화면의 문구입니다. Render 문서에는 "Repository access 섹션"까지만 나옵니다.

### 5.3 DB 먼저 만들기

웹 서비스의 환경변수에 DB 접속 정보가 필요하므로 **DB를 먼저** 만듭니다 ([문서](https://render.com/docs/postgresql-creating-connecting)).

> **DB와 웹 서비스는 반드시 같은 리전(Singapore)에 만듭니다.** 내부 URL은 같은 계정, 같은 리전일 때만 쓸 수 있습니다 ([문서](https://render.com/docs/postgresql-creating-connecting)). 리전은 나중에 바꿀 수 없어서, 다르게 만들면 새로 만들어야 합니다 ([문서](https://render.com/docs/regions)).

- [ ] **+ New > Postgres**
- [ ] **Name:** 알아보기 쉬운 이름. **Database, User:** 원하는 값이나 비워 두기 (**나중에 못 바꿉니다**)
- [ ] **Region: Singapore**
- [ ] **PostgreSQL Version: 17** (CI에서 검증한 버전)
- [ ] **Plan: Free**, 저장소 1 GB, **Create Database**
- [ ] 상태가 **Available**이 되면 **생성 날짜를 달력에 적습니다.** 30일 만료의 기준입니다.
- [ ] **Info > Networking**에서 기본값 `0.0.0.0/0`을 지워 외부 접속을 막습니다. CLI에는 완전 차단 옵션이 있지만, 대시보드에서 목록을 비울 수 있는지는 **미확인**입니다. 안 되면 멈추고 알려 주세요.
- [ ] **Info**의 **내부(Internal)** 접속 정보를 확인합니다. 값은 어디에도 복사해 두지 말고, 다음 단계에서 바로 입력합니다.

### 5.4 웹 서비스 만들기

- [ ] **+ New > Web Service > Git Provider**에서 `kotoba`를 고르고 **Connect**
- [ ] **Name:** 원하는 이름. `onrender.com` 주소에 들어갑니다.
- [ ] **Region: Singapore. DB와 같은 리전인지 다시 확인합니다** (5.3절). 다르면 내부 URL로 접속할 수 없습니다.
- [ ] **Branch:** `main`
- [ ] **Root Directory:** `backend`. Dockerfile 경로와 빌드 컨텍스트가 이 폴더 기준이 되고, 폴더 밖 변경(문서 등)으로는 자동 배포되지 않습니다 ([문서](https://render.com/docs/monorepo-support)).
- [ ] **Language: Docker**. **Dockerfile Path**는 `backend` 기준이므로 `Dockerfile`입니다. Docker Command는 비워 둡니다.
- [ ] **Compute: Free**
- [ ] **Advanced > Environment Variables:** 5.5절의 값을 입력
- [ ] **Advanced > Health Check Path:** `/api/health` (5.6절)
- [ ] 배포를 시작하고 **Deploys** 페이지에서 로그를 봅니다.
- [ ] 첫 배포 성공 후 **Settings > Auto-Deploy**를 **After CI Checks Pass**로 바꿉니다. `h2`, `postgres`, `container`가 모두 통과한 커밋만 배포됩니다 ([문서](https://render.com/docs/deploys)).

### 5.5 환경변수

| 이름 | 필수 | 설명 | 값을 얻는 곳 |
|---|---|---|---|
| `DB_URL` | 필수 | JDBC 접속 주소 | 6.2절 |
| `DB_USERNAME` | 필수 | DB 사용자 | DB **Info**의 내부 접속 정보 |
| `DB_PASSWORD` | 필수 | DB 비밀번호 | DB **Info**의 내부 접속 정보 |
| `JWT_SECRET` | 필수 | JWT 서명 키, 32바이트 이상 | 직접 생성 (6.3절) |

- **`SPRING_PROFILES_ACTIVE`:** 이미지 기본값이 `prod`라서 넣지 않아도 됩니다.
- **`PORT`:** Render가 `10000`으로 넘깁니다. 넣지 않습니다.
- **`JAVA_TOOL_OPTIONS`:** 넣지 않습니다. 이미지 기본값(`MaxRAMPercentage=40`)을 덮어쓰게 되어, 넣을 때는 전체를 다시 적어야 합니다.

### 5.6 헬스체크 경로: `/api/health`

Render 문서는 DB 쿼리 같은 검사를 권하지만, **`/actuator/health`는 쓰지 않을 것을 제안합니다.**

| 경로 | DB가 끊겼을 때 | 판단 |
|---|---|---|
| `/actuator/health` | 실패 응답 → 60초 뒤 앱 **재시작** → 다시 2~4분 기동 | 무료 DB는 수시로 재시작될 수 있고 30일 뒤 만료됩니다. 재시작해도 DB 문제가 풀리지 않는데 앱만 반복해서 다시 뜹니다. |
| **`/api/health` (제안)** | 앱은 그대로 살아 있고, 요청만 오류 응답 | DB가 돌아오면 재시작 없이 바로 회복합니다. |

**대가:** 헬스체크가 통과해도 DB가 끊겼을 수 있습니다. DB 상태는 `/actuator/health`로 직접 확인합니다 (9절).

## 6. 코드와 설정 변경 (구현 완료)

### 6.1 `PORT` 환경변수

**앱이 `PORT`를 읽지 않았습니다.** 설정 파일에 `server.port`가 없어 항상 8080을 썼습니다. Render가 다른 포트를 "대개" 감지하지만 ([문서](https://render.com/docs/web-services)), 권장 방식대로 고쳤습니다.

| 파일 | 변경 |
|---|---|
| `application-prod.yml` | `server.port: ${PORT:8080}` 추가. 없으면 지금처럼 8080 |
| `backend/Dockerfile` | `HEALTHCHECK`가 8080 고정이라 `${PORT:-8080}`으로 변경 |
| `.github/workflows/ci.yml` | 스모크 테스트에 `PORT=10000`으로 띄워 그 포트로 응답하는지 검사를 추가 |
| `docs/design/06-deploy.md` | 2.4절의 내용 수정 |

- **Compose와 로컬은 그대로입니다.** `PORT`가 없으면 8080입니다.
- **`dev` 프로필에는 넣지 않았습니다.** 개발 중 다른 도구의 `PORT` 환경변수에 영향받지 않게 하기 위해서입니다.
- **접속 주소:** Spring Boot 기본 설정은 모든 주소에서 받으므로 `0.0.0.0` 요구를 따로 설정하지 않습니다. IPv4로 받는지는 첫 배포에서 확인합니다.

### 6.2 DB 접속 URL 옮기기

Render는 내부 URL을 아래 형식으로 보여 줍니다 ([문서](https://render.com/docs/postgresql-creating-connecting)). **이 값을 그대로 `DB_URL`에 넣으면 안 됩니다.** 사용자와 비밀번호를 빼고 앞에 `jdbc:`를 붙입니다.

```
Render 내부 URL:  postgresql://USER:PASSWORD@INTERNAL_HOST:PORT/DATABASE

DB_URL       =  jdbc:postgresql://INTERNAL_HOST:PORT/DATABASE?sslmode=require
DB_USERNAME  =  USER
DB_PASSWORD  =  PASSWORD
```

- **`:PORT` 부분:** URL에 없으면 그대로 빼도 됩니다. 기본 포트 5432를 쓰기 때문입니다.
- **외부 URL을 쓰지 않습니다.** 내부 URL은 같은 리전의 사설망을 씁니다.
- **`sslmode=require`:** 내부 접속은 자체 서명 인증서의 TLS를 지원하고, Render 문서가 평문 전환을 막는 값으로 안내합니다. 그래서 `verify-ca`, `verify-full`은 쓸 수 없습니다. **CI의 PostgreSQL에는 TLS가 없어 이 옵션은 미검증입니다.** 이 부분에서 접속이 실패하면 옵션을 빼고 시도해 원인을 가릅니다.

### 6.3 `JWT_SECRET` 만들기

PowerShell에서 아래 한 줄을 실행합니다. **값을 화면에 출력하지 않고 클립보드로 복사합니다** (48바이트 난수, Base64 64자. Windows PowerShell 5.1에서 길이 확인함).

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b) | Set-Clipboard; Remove-Variable b
```

바로 Render의 `JWT_SECRET` 입력칸에 붙여 넣습니다.

**지켜 주세요**

- 이 값을 **레포, 채팅, 메모, 스크린샷에 남기지 마세요.** 이 대화에도 붙여 넣지 마세요. 저는 값을 볼 필요가 없습니다.
- 붙여 넣은 뒤 다른 글자를 복사해 클립보드를 덮어쓰세요. Windows 클립보드 기록(`Win + V`)을 쓴다면 거기서도 지우세요.
- 다른 환경과 같은 값을 쓰지 마세요. `.env.example`의 값이나 CI의 `ci-only-...` 값도 쓰면 안 됩니다.
- 값을 잃어버려도 괜찮습니다. 새로 만들어 넣으면 됩니다. 대신 **기존 로그인 토큰이 모두 무효**가 되어 다시 로그인해야 합니다.

### 6.4 README

- **환경변수 표:** 이름과 설명만 적고 값은 쓰지 않습니다 (5.5절의 앞 세 열).
- **배포 안내:** 이 문서 링크, 잠들기와 30일 만료 안내, 알려진 제한.
- **공개 API 제한(새로 추가):** 배포하면 회원가입 API가 인터넷에 공개되고, 요청 제한이 없습니다. 0.1 CPU에서 BCrypt 요청이 몰리면 앱이 느려져 헬스체크가 실패할 수 있습니다.

## 7. 첫 배포 실패 시 체크리스트

**기동에 2~4분이 걸릴 수 있습니다.** 로그가 잠시 멈춰 있어도 바로 실패로 보지 말고, 상태가 **Failed**가 될 때까지 기다립니다.

**로그를 보는 곳** (Render 대시보드의 웹 서비스 화면)

| 메뉴 | 보는 내용 |
|---|---|
| **Deploys** | 배포별 상태(Live, Failed)와 빌드·시작 로그. **먼저 봅니다** |
| **Logs** | 앱이 실행되면서 남긴 로그 |
| **Events** | `Instance Failed`, 헬스체크 실패, 재시작 이벤트 |
| **Metrics** | 메모리와 CPU 사용량 |

**시도할 순서**

한 번에 **하나만** 고치고 다시 배포합니다. 환경변수를 고치면 저장할 때 재배포를 고르거나, **Deploys > Manual Deploy > Deploy latest commit**을 씁니다 ([문서](https://render.com/docs/deploys)).

- [ ] **1. 어느 단계에서 실패했는지 봅니다.** Deploys 로그에 앱의 `Starting BackendApplication`이 나오기 전이면 **빌드 실패**(2번), 나온 뒤면 **기동 실패**(3번)입니다.
- [ ] **2. 빌드 실패:** 로그의 `ERROR:` 줄을 봅니다.
  - `Dockerfile` 또는 `not found`: **Root Directory**가 `backend`인지, **Language**가 Docker인지, Dockerfile 경로가 맞는지 확인합니다.
  - `Build Minutes Exhausted`: 이달 빌드 시간을 다 쓴 것입니다.
- [ ] **3. 기동 실패:** 아래 표를 **위에서부터** 찾습니다. 앞쪽일수록 설정 문제라 고치기 쉽습니다.

| 순서 | 로그에서 찾을 문구 | 원인 | 조치 |
|---|---|---|---|
| 1 | `JWT_SECRET`, `WeakKeyException` | `JWT_SECRET`이 없거나 짧음 | 6.3절로 다시 만들어 입력 |
| 2 | `Could not resolve placeholder`, `DB_URL` | 환경변수 이름 오타, 누락 | 5.5절의 표와 비교 |
| 3 | `claims to not accept`, `Driver` | `DB_URL`에 `jdbc:`가 없음 | 6.2절 |
| 4 | `UnknownHost`, `Connection refused`, `Connection attempt failed` | 외부 주소를 넣었거나 **리전이 다름** | 내부 주소인지, 두 서비스의 리전이 같은지 확인 |
| 5 | `password authentication failed` | 사용자, 비밀번호 불일치 | DB **Info**의 내부 접속 정보와 비교 |
| 6 | `SSL`, `sslmode` | `sslmode=require` 호환 문제 (미검증 항목) | `DB_URL`에서 옵션을 빼고 시도해 원인을 가림. 결과를 알려 주세요 |
| 7 | `Flyway`, `Schema validation`, `FlywayException` | 스키마 문제. DB가 비어 있지 않을 수 있음 | 알려 주세요 |
| 8 | `Port scan timeout reached` | `Tomcat started on port`가 나왔으면 포트 불일치, 안 나왔으면 기동이 너무 느림 | 4.3절 |
| 9 | `Out of memory`, 종료 코드 `137` | 메모리 초과 (2.3절의 JVM 권장 사양 문제) | 알려 주세요. 값을 임의로 올리지 마세요 |
| 10 | `Timed out`, `HTTP health check failed` | CPU 부족, 또는 헬스체크 경로 오류 | 경로가 `/api/health`인지 확인 후 다시 시도 |

- [ ] **4. 정상 기동 표시를 확인합니다.** `Successfully applied 1 migration` → `Tomcat started on port 10000` → `Started BackendApplication` → `Your service is live` 순서로 나와야 합니다. 어디까지 나왔는지가 원인을 좁혀 줍니다.
- [ ] **5. 그래도 안 되면** 실패한 문구와 그 앞뒤 몇 줄을 알려 주세요. **붙여 넣기 전에 주소, 비밀번호, 토큰이 없는지 확인하고 있으면 가려 주세요.**
- [ ] **6. 포기 기준:** 메모리 초과나 포트 스캔 실패가 설정을 고쳐도 반복되면, 무료 플랜으로 이 앱을 띄우기 어렵다는 뜻입니다. 카드를 등록하거나 유료로 올리기 전에 다시 논의합니다.

## 8. 시연 데이터 넣기

서버가 켜진 뒤 데모 계정, 덱, 카드를 넣는 방법입니다. **30일마다 DB가 사라지므로 다시 넣기 쉬워야 합니다.**

| 기준 | A. 레포에 시딩 코드 (`SEED_DEMO=true`일 때 앱 기동 시 실행) | B. API로 넣는 스크립트 (로컬에서 직접 실행) |
|---|---|---|
| **새 DB에서 복구** | ✅ 환경변수만 켜 두면 자동 | 스크립트를 다시 실행 |
| **운영 코드 영향** | ❌ 시연 전용 코드가 운영 이미지에 들어감. 프로필과 환경변수로 막아야 함 | ✅ 앱 코드 변경 없음 |
| **비밀번호** | 환경변수로 받아야 함. 켜 둔 채 재기동할 때의 처리도 필요 | ✅ 실행할 때 입력받음. 어디에도 저장되지 않음 |
| **검증 경로** | 서비스나 리포지토리를 직접 호출. API 검증과 어긋날 수 있음 | ✅ 실제 API를 그대로 호출해 **배포 확인까지 겸함** |
| **기동 시간** | ❌ 이미 2~4분인 기동이 더 길어짐. BCrypt 포함. 포트가 더 늦게 열림 | ✅ 기동에 영향 없음 |
| **실패 진단** | 기동 로그를 봐야 함. 시드 실패 시 앱 기동 실패 위험 | ✅ 터미널에서 바로 보임 |
| **Render 무료 제약** | 셸 접속과 one-off job이 없어 재기동으로만 실행 가능 | ✅ HTTP만 쓰므로 제약 없음 |
| **실행 환경** | 없음 | ❌ 로컬 PowerShell과 네트워크 필요. 수동 실행 |
| **속도** | 빠름 | ❌ 잠든 서버를 깨우고 요청마다 기다림 (CI 측정: 회원가입과 로그인 8회에 12~23초) |
| **테스트** | 통합 테스트로 가능 | CI의 Compose 환경을 대상으로 실행 가능 (`pwsh`, 5.1과 차이 확인 필요) |

**선택: B. API 스크립트.** 30일마다 한 번 수동으로 실행하는 부담은 작습니다. 반면 A는 시연 때문에 운영 코드와 기동 시간에 영구적인 비용을 더하고, 포트 스캔 위험까지 키웁니다.

**구현 내용**

- **파일:** `scripts/seed-demo.ps1`, `scripts/seed-demo-data.json` (일본어 단어 10개. 개인정보 없음)
- **실행:** 레포 루트에서 아래처럼 실행합니다. 서버 주소는 레포에 넣지 않으므로 직접 입력합니다.

  ```powershell
  powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1 -BaseUrl <서버 주소>
  ```
- **비밀번호:** 실행할 때 입력받고 어디에도 저장하지 않습니다. 기본 이메일은 `demo@example.com`이고 `-Email`로 바꿀 수 있습니다.
- **동작:** `/api/health`로 서버를 깨워 최대 7분 기다림 → 회원가입(이미 있으면 로그인) → 없는 덱과 카드만 생성. 다시 실행해도 중복되지 않습니다.
- **대기 판정:** 깨어나는 동안의 응답 형식을 모르므로, 200을 받아도 JSON의 `status`가 `UP`일 때만 준비된 것으로 봅니다.
- **안전장치:** `localhost`가 아닌 평문 `http://` 주소는 거부합니다. 비밀번호와 토큰은 출력하지 않습니다.
- **요청 방식:** 무료 인스턴스의 CPU와 요청 제한을 고려해 한 번에 하나씩 보내고, 429와 503에만 15초 뒤 재시도합니다 (최대 4번 시도).
- **Windows PowerShell 5.1 대응:** 요청과 응답을 UTF-8 바이트로 직접 처리합니다. 5.1 기본 동작으로는 일본어가 깨질 수 있기 때문입니다. TLS 1.2도 명시합니다.

**검증한 것** (실제 Render에서는 아직 실행하지 않았습니다)

| 환경 | 결과 |
|---|---|
| 로컬 Windows PowerShell 5.1 + `dev` 서버(H2) | 첫 실행 카드 10개 생성, 두 번째 0개. 틀린 비밀번호는 401로 실패. 평문 `http` 주소 거부. 일본어와 한글 원본 일치 |
| CI PowerShell 7 + Compose(PostgreSQL) | 첫 실행 10개, 두 번째 0개. 저장된 일본어가 원본과 일치. `container` 작업에서 매번 실행 |

**HTTPS 접속은 검증하지 못했습니다.** 위 두 환경은 모두 `http://127.0.0.1`입니다. Render에서 첫 실행할 때 확인합니다.

## 9. 배포 후 확인 체크리스트

사용자가 직접 실행합니다. **서버 주소, 토큰, 비밀번호는 레포와 채팅에 남기지 마세요.** 결과를 공유할 때는 상태 코드와 시간만 알려 주시면 됩니다.

시드 스크립트가 성공하면 9.2의 회원가입과 로그인, 9.3의 덱과 카드 생성까지 확인된 것입니다. 스크립트 실행은 9.1을 통과한 뒤에 합니다.

### 9.1 헬스체크

- [ ] `GET /api/health`가 `{"status":"UP"}`
- [ ] `GET /actuator/health`가 `{"status":"UP"}` (DB 연결 포함)
- [ ] 로그에 `Successfully applied 1 migration`과 `The following 1 profile is active: "prod"`
- [ ] 로그의 `Tomcat started on port`가 `10000`
- [ ] `http://`로 접속하면 `https://`로 넘어가는지

### 9.2 로그인

- [ ] `POST /api/auth/signup`이 201
- [ ] `POST /api/auth/login`이 200, 토큰 쌍 반환
- [ ] `GET /api/users/me`: 토큰 없이 401, 토큰으로 200
- [ ] 틀린 비밀번호로 로그인하면 401

### 9.3 복습

- [ ] `POST /api/decks`가 201
- [ ] `POST /api/decks/{deckId}/cards`가 201
- [ ] `GET /api/decks/{deckId}/study/due`에 새 카드가 나옴
- [ ] `POST /api/decks/{deckId}/cards/{cardId}/reviews`에 `{"rating":"GOOD"}`을 보내면 200, 다음 복습 시각 반환
- [ ] `GET /api/stats/summary`의 오늘 복습 수가 늘어남

### 9.4 실제 환경 측정 (`06-deploy.md` 9절의 CI 값과 비교)

- [ ] 첫 배포에서 `Tomcat started`까지 걸린 시간
- [ ] 15분 넘게 쉰 뒤 첫 요청의 응답 시간과 응답 내용 (HTML 로딩 페이지인지 JSON인지)
- [ ] 시드 스크립트가 `https://` 주소에서 성공하는지
- [ ] **Metrics**의 메모리 사용량. 512 MB에 얼마나 가까운지
- [ ] 며칠 동안 `Instance Failed`, `Out of memory` 이벤트가 있는지

### 9.5 보안

- [ ] 내 컴퓨터에서 DB 외부 URL로 접속이 **거부**되는지
- [ ] GitHub의 Render 앱이 `kotoba`만 접근하는지
- [ ] 레포에 서비스 주소나 비밀값이 들어가지 않았는지

### 9.6 30일 만료 대비

> **Render 무료 환경은 30일 시연용이며 데이터가 초기화될 수 있습니다.**

- [ ] DB 생성일 기준 **25일째**에 달력 알림을 걸어 둠. Render도 만료 전에 이메일로 알려 줍니다 ([문서](https://render.com/docs/free))
- [ ] 남길 데이터가 있으면 만료 전에 백업: IP 허용 목록에 내 IP만 잠시 추가 → 로컬에서 `pg_dump` → 허용 목록을 다시 비움 ([문서](https://render.com/docs/postgresql-backups))
- [ ] **만료 후 복구 절차:** 만료된 DB 삭제 → 5.3절로 새 DB 생성 → 웹 서비스의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 변경 → 재배포 (Flyway가 스키마 생성) → 시드 다시 실행 → 9.1~9.3 확인
- [ ] 유예 기간(14일) 안에 유료로 올리지 않으면 삭제됩니다. 계속 쓸지 25일째에 결정합니다.

## 10. CI 검증과 측정 결과 (2026-11-05)

임시 브랜치 `ci-check/render`에서 진행했습니다. 기준 실행은 [42010632495](https://github.com/monenon-dev/kotoba/actions/runs/42010632495)이고, `h2`, `postgres`, `container` 모두 통과했습니다.

### 10.1 실험

`06-deploy.md` 8절의 X1~X7에 이어 번호를 붙였습니다. 기준 커밋으로 되돌린 상태에서 한 번에 하나만 바꿨고, 실패 이유는 로그로 확인했습니다.

| 실험 | 바꾼 것 | 실행 | 실패한 단계 | 로그에서 확인한 이유 |
|---|---|---|---|---|
| **X8** | `application-prod.yml`의 `server.port` 삭제 | [42011021264](https://github.com/monenon-dev/kotoba/actions/runs/42011021264) | `Check app honors PORT` | `PORT=10000`을 줘도 `Tomcat started on port 8080`. 헬스체크는 10000을 호출해 `starting`에 머묾 |
| **X9** | `HEALTHCHECK` 포트를 8080으로 고정 | [42011844308](https://github.com/monenon-dev/kotoba/actions/runs/42011844308) | `Check app honors PORT` | `docker health: starting`. 기본 포트로 띄우는 앞 단계는 통과 |
| **X10** | 시드 스크립트의 중복 검사 삭제 | [42012655937](https://github.com/monenon-dev/kotoba/actions/runs/42012655937) | `Check demo seed script` | 두 번째 실행이 `cards=20 created=10` |

세 실험 모두 `h2`, `postgres` 작업은 통과했습니다. **이 문제들은 `container` 작업만 잡습니다.**

**정리:** 임시 브랜치를 로컬과 원격에서 삭제했습니다. `git ls-remote --heads origin`에는 `refs/heads/main`만 남았고, GitHub API 조회는 `Branch not found`입니다. 측정용 임시 워크플로도 브랜치와 함께 사라졌고, 실험 커밋은 main에 들어가지 않았습니다.

### 10.2 0.1 CPU에서 헬스체크 응답 시간

Render는 헬스체크가 5초 안에 응답하지 않으면 실패로 보고, 60초 연속 실패하면 재시작합니다. 그래서 요청을 보내는 동안 `/api/health`를 3초 간격, 제한 5초로 호출했습니다.

- **실행:** [42008769983](https://github.com/monenon-dev/kotoba/actions/runs/42008769983) (2회)
- **조건:** `--cpus=0.1 --memory=512m`, 이미지 기본 JVM 설정, `PORT=10000`

| 구간 | 부하 | 프로브 실패 | 가장 느린 응답 (1회차, 2회차) |
|---|---|---|---|
| 대기 | 없음, 60초 | 0 | 100 ms, 190 ms |
| 순차 | 회원가입과 로그인 1쌍씩, 120초 | 0 | 924 ms, 696 ms |
| 동시 4 | 회원가입과 로그인 4쌍씩, 120초 | 0 | 1,022 ms, 1,189 ms |
| **동시 16** | 회원가입과 로그인 16쌍씩, 120초 | 0 | **2,292 ms, 3,890 ms** |
| 쿨다운 | 없음, 45초 | 0 | 343 ms, 147 ms |

- **기동 시간:** 첫 응답까지 230초, 172초. 6-1 측정(125~256초) 범위 안입니다.
- **메모리:** 최고 약 316~319 MiB. 강제 종료 없음.
- **처리 결과:** 모든 요청이 201과 200으로 끝났습니다.

**판단:** 시연 규모에서는 헬스체크 실패로 재시작될 위험이 낮아 보입니다. 다만 동시 16개에서 3.9초까지 올라가 제한인 5초에 가까워졌습니다. 재시작은 5초 초과가 60초 동안 이어져야 일어나므로, 일시적으로 느려지는 것만으로는 일어나지 않습니다. **이 값은 GitHub 러너에서 잰 참고값이고, Render의 프로브는 같은 방식이 아닐 수 있습니다.**

## 11. 작업 순서

1. 설계 확인 (완료)
2. 6절과 8절 구현, 임시 브랜치에서 CI 검증과 측정 (완료)
3. 임시 브랜치 삭제 확인, 결과 기록 (완료)
4. 전체 테스트와 비밀값 검사, 커밋될 파일 목록 보고 (완료)
5. **승인 후** 커밋과 push, CI 결과 확인 (승인받아 진행)
6. 사용자가 5절대로 Render 설정, 9절로 확인. 실패하면 7절

## 12. 알려진 미확인 항목

실제 배포 전에는 확인할 수 없는 것들입니다. 첫 배포에서 하나씩 확인합니다.

| 항목 | 확인 방법 |
|---|---|
| 가입 때 카드 요구 여부 | 가입 화면 (5.1절) |
| 포트 스캔 제한 시간 | 첫 배포 (4.3절) |
| 잠든 뒤 깨어날 때의 제한 시간과 실제 소요 시간 | 9.4절 |
| 깨어나는 동안 API 클라이언트가 받는 응답 형식 | 9.4절 |
| 무료 서비스의 헬스체크 경로 지원, Docker `HEALTHCHECK` 사용 여부 | 5.4절의 설정 화면, Events |
| 무료 플랜의 인바운드 요청 제한 수치 | 문서에 없음 |
| 대시보드에서 DB 외부 접속을 완전히 막을 수 있는지 | 5.3절 |
| `sslmode=require` 접속, 시드 스크립트의 HTTPS 접속 | 첫 배포, 첫 시드 |
