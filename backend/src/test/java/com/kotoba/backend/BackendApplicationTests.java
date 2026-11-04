package com.kotoba.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 전체 컨텍스트(JPA 포함)가 H2(dev 프로필)로 뜨는지 확인한다.
@SpringBootTest
@ActiveProfiles("dev")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
