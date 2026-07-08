package com.miyou.app.domain.mission.exception

class MissionAlreadyCompletedException(
    val missionId: String,
) : RuntimeException("Mission already completed: $missionId")
