package com.kotoba.backend.support;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * 통합 테스트가 어느 DB 로 돌지 정한다.
 * 환경변수 TEST_DB=postgres 이면 pgtest(PostgreSQL + Flyway + validate), 아니면 dev(H2).
 * 로컬은 설정 없이 H2 로 돌고, CI 의 postgres 작업만 TEST_DB=postgres 를 준다.
 */
public class TestDatabaseProfileResolver implements ActiveProfilesResolver {

	public static boolean isPostgres() {
		return "postgres".equalsIgnoreCase(System.getenv("TEST_DB"));
	}

	@Override
	public String[] resolve(Class<?> testClass) {
		return new String[] { isPostgres() ? "pgtest" : "dev" };
	}

}
