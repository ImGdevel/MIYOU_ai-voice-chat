package com.miyou.app.domain.mission.exception

class MissionNotFoundException(
    val missionId: String,
) : RuntimeException("Mission not found: $missionId")
