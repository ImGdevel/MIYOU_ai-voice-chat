package com.miyou.app.api.mission.dto

import com.miyou.app.domain.mission.model.Mission
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "미션 정보 응답 DTO")
data class MissionResponse(
    @field:Schema(description = "미션 고유 ID", example = "mission_daily_checkin")
    val missionId: String,
    @field:Schema(description = "미션 구분 유형 (DAILY: 일일 미션, ONETIME: 1회성 업적)", example = "DAILY")
    val type: String,
    @field:Schema(description = "미션 이름", example = "일일 출석 체크")
    val name: String,
    @field:Schema(description = "미션 세부 설명", example = "하루에 한 번 대화방에 방문하여 출석을 완료하세요.")
    val description: String?,
    @field:Schema(description = "완료 시 획득할 보상 크레딧 금액", example = "50")
    val rewardAmount: Long,
    @field:Schema(description = "반복 수행 가능 여부", example = "true")
    val repeatable: Boolean,
) {
    companion object {
        fun from(mission: Mission): MissionResponse =
            MissionResponse(
                mission.missionId.value,
                mission.type.name,
                mission.name,
                mission.description,
                mission.rewardAmount,
                mission.repeatable,
            )
    }
}
