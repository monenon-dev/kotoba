# CLAUDE.md

이 레포(kotoba)에서 작업할 때 따르는 규칙입니다. **작업을 시작할 때마다 이 파일을 먼저 확인합니다.**

## 언어

- 모든 답변, 진행 중 설명, 결과 보고, 설계 문서의 설명은 **한국어**로 작성합니다.
- 작업이 길어져도 중간 설명을 영어로 바꾸지 않습니다.
- 명령 출력, 로그, 코드, 에러 메시지 원문은 그대로 보여주되, 그에 대한 설명은 한국어로 합니다.

## PowerShell 명령

- 이 환경의 셸은 Windows PowerShell 5.1입니다.
- 따옴표나 한글이 들어간 복잡한 명령은 한 줄로 실행하지 않고, **`.ps1` 파일로 저장한 뒤 실행**합니다. (따옴표 이스케이프가 꼬이거나 한글이 깨지는 문제를 피하기 위해서)
- BOM 없는 UTF-8 스크립트는 PowerShell 5.1이 ANSI로 읽어 한글 출력이 깨지므로, 스크립트 안의 출력 문구는 영문으로 쓰거나 결과를 별도로 해석합니다.
- 임시 스크립트는 레포가 아니라 세션 scratchpad 디렉터리에 둡니다.
- 커밋 메시지는 `-m` 대신 파일로 저장해 `git commit -F <파일>`로 커밋합니다.

## 작업 흐름

1. 작업을 시작할 때 이 파일(CLAUDE.md)을 확인합니다.
2. 작업을 수행하고, `backend`에서 전체 테스트(`.\gradlew.bat test`)를 실행해 결과를 확인합니다.
3. 작업을 마치면 커밋하고 `origin/main`에 push합니다.
4. push 후 GitHub Actions CI 결과(`h2`, `postgres` 작업)를 `gh run`으로 확인해서 보고합니다. 실패하면 원인을 분석해 함께 봅니다.

## 프로젝트 요약

- 구조: `backend/`(Spring Boot 4.1, Java 21, Gradle), `app/`(Flutter, 예정), `docs/design/`(단계별 설계 문서)
- 로컬 테스트: Docker가 없어서 H2(`dev` 프로필)로만 돕니다.
- PostgreSQL 검증: CI의 `postgres` 작업에서 `TEST_DB=postgres`(`pgtest` 프로필)로 같은 테스트 전체를 실행합니다.
- DB 스키마: Flyway(`backend/src/main/resources/db/migration`)로 관리합니다. 적용된 마이그레이션 파일은 수정하지 않고 새 버전 파일로 변경합니다.
- 설계가 필요한 단계는 코드보다 먼저 `docs/design/`에 설계 문서를 쓰고 확인을 받습니다.
