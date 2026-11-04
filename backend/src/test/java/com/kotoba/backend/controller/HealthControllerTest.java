package com.kotoba.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.kotoba.backend.support.IntegrationTestSupport;

// 시큐리티 설정까지 포함해 토큰 없이 접근 가능한지 확인하려고 전체 컨텍스트로 띄운다.
class HealthControllerTest extends IntegrationTestSupport {

	@Test
	void healthReturnsUpWithoutToken() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

}
