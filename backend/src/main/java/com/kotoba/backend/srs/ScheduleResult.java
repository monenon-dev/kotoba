package com.kotoba.backend.srs;

import java.time.Instant;

/**
 * 한 번의 복습 결과.
 *
 * @param schedule 갱신된 복습 상태
 * @param reviewedAt 복습한 시각 (스케줄러의 Clock 기준)
 * @param dueAt 다음 복습 시각
 */
public record ScheduleResult(ReviewSchedule schedule, Instant reviewedAt, Instant dueAt) {
}
