package com.miyou.app.api.mission.dto

import com.miyou.app.domain.mission.model.UserMission
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "사용자별 미션 수행 상태 응답 DTO")
data class UserMissionResponse(
    @field:Schema(description = "사용자 ID", example = "user-123")
    val userId: String,
    @field:Schema(description = "대상 미션 ID", example = "mission_daily_checkin")
    val missionId: String,
    @field:Schema(
        description = "수행 상태 (IN_PROGRESS: 진행 중, COMPLETED: 완료 및 보상 대기, REWARDED: 보상 지급 완료)",
        example = "COMPLETED"
    )
    val status: String,
    @field:Schema(description = "미션 완료 시각", example = "2026-07-08T16:30:00Z")
    val completedAt: Instant?,
    @field:Schema(description = "보상 지급 시각", example = "2026-07-08T16:31:00Z")
    val rewardedAt: Instant?,
) {
    companion object {
        fun from(userMission: UserMission): UserMissionResponse =
            UserMissionResponse(
                userMission.userId,
                userMission.missionId.value,
                userMission.status.name,
                userMission.completedAt,
                userMission.rewardedAt,
            )
    }
}
