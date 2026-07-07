package com.miyou.app.application.mission.service

import com.miyou.app.application.mission.usecase.MissionCompletionUseCase
import com.miyou.app.application.mission.usecase.MissionQueryUseCase
import com.miyou.app.domain.mission.model.Mission
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.MissionStatus
import com.miyou.app.domain.mission.model.UserMission
import com.miyou.app.domain.mission.port.CreditChargingPort
import com.miyou.app.domain.mission.port.CreditRewardCommand
import com.miyou.app.domain.mission.port.MissionRepository
import com.miyou.app.domain.mission.port.UserMissionRepository
import com.miyou.app.exception.MissionErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * 미션 관리 서비스.
 *
 * 미션 조회 → 완료 처리 → 보상(크레딧) 지급.
 * 일회성/반복 미션 구분 처리.
 */
@Service
class MissionApplicationService(
    private val missionRepository: MissionRepository,
    private val userMissionRepository: UserMissionRepository,
    private val creditChargingPort: CreditChargingPort,
) : MissionQueryUseCase,
    MissionCompletionUseCase {
    /**
     * 전체 미션 목록 조회.
     *
     * @return 모든 미션
     */
    override fun getAllMissions(): Flux<Mission> = missionRepository.findAll()

    /**
     * 사용자 미션 현황 조회.
     *
     * @param userId 사용자 ID
     * @return 사용자의 미션 목록 (상태 포함)
     */
    override fun getUserMissions(userId: String): Flux<UserMission> = userMissionRepository.findByUserId(userId)

    /**
     * 미션 완료 처리.
     *
     * @param userId 사용자 ID
     * @param missionId 미션 ID
     * @return 완료된 미션 (보상 지급 완료)
     * @throws ResponseStatusException 미션 없음 또는 이미 완료된 일회성 미션
     */
    @Transactional
    override fun completeMission(
        userId: String,
        missionId: MissionId,
    ): Mono<UserMission> =
        missionRepository
            .findById(missionId)
            .switchIfEmpty(
                Mono.error(
                    ResponseStatusException(
                        MissionErrorCode.MISSION_NOT_FOUND.httpStatus,
                        MissionErrorCode.MISSION_NOT_FOUND.message,
                    ),
                ),
            ).flatMap { mission ->
                userMissionRepository
                    .findByUserIdAndMissionId(userId, missionId)
                    .defaultIfEmpty(UserMission.start(userId, missionId))
                    .flatMap { userMission -> validateAndComplete(userMission, mission, userId) }
            }

    private fun validateAndComplete(
        userMission: UserMission,
        mission: Mission,
        userId: String,
    ): Mono<UserMission> {
        if (userMission.status == MissionStatus.REWARDED && !mission.repeatable) {
            return Mono.error(
                ResponseStatusException(
                    MissionErrorCode.MISSION_ALREADY_COMPLETED.httpStatus,
                    MissionErrorCode.MISSION_ALREADY_COMPLETED.message,
                ),
            )
        }
        val completed = userMission.complete()
        val rewarded = completed.reward()
        return userMissionRepository
            .save(rewarded)
            .flatMap { saved ->
                creditChargingPort
                    .grantReward(
                        CreditRewardCommand(
                            userId,
                            mission.missionId,
                            mission.rewardAmount,
                            mission.type.name,
                        ),
                    ).thenReturn(saved)
            }
    }
}
