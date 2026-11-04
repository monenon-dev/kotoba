package com.kotoba.backend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kotoba.backend.domain.ReviewLog;
import com.kotoba.backend.srs.Rating;

/**
 * 통계 쿼리는 모두 (member_id, review_date) 인덱스 범위에서 집계한다. 로그 엔티티를 메모리에 올리지 않는다.
 * 날짜 경계는 Java 에서 계산한 LocalDate 를 파라미터로 넘기므로, DB 의 날짜/시간대 함수를 쓰지 않는다.
 */
public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

	long countByMemberIdAndReviewDate(Long memberId, LocalDate reviewDate);

	// 오늘 시작한 신규 카드 수 = 오늘 로그 중 첫 복습(first_review = true)인 것
	long countByMemberIdAndReviewDateAndFirstReviewTrue(Long memberId, LocalDate reviewDate);

	// 연속 학습일 계산용. 로그가 아니라 날짜 값만, 기간 안에서 최대 (기간 일수)개 가져온다.
	@Query("""
			select distinct l.reviewDate from ReviewLog l
			where l.member.id = :memberId and l.reviewDate between :from and :to
			order by l.reviewDate desc
			""")
	List<LocalDate> findReviewDates(@Param("memberId") Long memberId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	// 기간의 전체 복습 수와 정답(AGAIN 이 아닌) 수
	@Query("""
			select count(l) as reviews,
			       coalesce(sum(case when l.rating <> :again then 1 else 0 end), 0) as correct
			from ReviewLog l
			where l.member.id = :memberId and l.reviewDate between :from and :to
			""")
	ReviewCounts countReviews(@Param("memberId") Long memberId, @Param("from") LocalDate from,
			@Param("to") LocalDate to, @Param("again") Rating again);

	@Query("""
			select l.reviewDate as reviewDate, count(l) as reviews
			from ReviewLog l
			where l.member.id = :memberId and l.reviewDate between :from and :to
			group by l.reviewDate
			order by l.reviewDate
			""")
	List<DailyReviewCount> countDaily(@Param("memberId") Long memberId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	interface ReviewCounts {

		long getReviews();

		long getCorrect();

	}

	interface DailyReviewCount {

		LocalDate getReviewDate();

		long getReviews();

	}

}
