package com.miyou.app.application.mission.service

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.mission.usecase.MissionCompletionUseCase
import com.miyou.app.application.mission.usecase.MissionQueryUseCase
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.mission.model.Mission
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.MissionStatus
import com.miyou.app.domain.mission.model.UserMission
import com.miyou.app.domain.mission.port.MissionRepository
import com.miyou.app.domain.mission.port.UserMissionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class MissionApplicationService(
    private val missionRepository: MissionRepository,
    private val userMissionRepository: UserMissionRepository,
    private val creditChargeUseCase: CreditChargeUseCase,
) : MissionQueryUseCase,
    MissionCompletionUseCase {
    override fun getAllMissions(): Flux<Mission> = missionRepository.findAll()

    override fun getUserMissions(userId: UserId): Flux<UserMission> = userMissionRepository.findByUserId(userId)

    @Transactional
    override fun completeMission(
        userId: UserId,
        missionId: MissionId,
    ): Mono<UserMission> =
        missionRepository
            .findById(missionId)
            .switchIfEmpty(
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "미션을 찾을 수 없습니다: ${missionId.value}",
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
        userId: UserId,
    ): Mono<UserMission> {
        if (userMission.status == MissionStatus.REWARDED && !mission.repeatable) {
            return Mono.error(
                ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 완료된 미션입니다: ${mission.missionId.value}",
                ),
            )
        }
        val completed = userMission.complete()
        val rewarded = completed.reward()
        return userMissionRepository
            .save(rewarded)
            .flatMap { saved ->
                creditChargeUseCase
                    .grantMissionReward(
                        userId,
                        mission.missionId,
                        mission.rewardAmount,
                        mission.type.name,
                    ).thenReturn(saved)
            }
    }
}
