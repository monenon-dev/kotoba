package com.kotoba.backend;

import org.junit.jupiter.api.Test;

import com.kotoba.backend.support.IntegrationTestSupport;

// 전체 컨텍스트(JPA 포함)가 H2(dev 프로필)로 뜨는지 확인한다.
class BackendApplicationTests extends IntegrationTestSupport {

	@Test
	void contextLoads() {
	}

}
