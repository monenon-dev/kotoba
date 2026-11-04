package com.kotoba.backend.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kotoba.backend.domain.Member;
import com.kotoba.backend.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	// 탈취 의심 시 해당 회원의 살아 있는 토큰을 한 번에 폐기한다.
	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now where t.member = :member and t.revokedAt is null")
	int revokeAllByMember(@Param("member") Member member, @Param("now") Instant now);

}
