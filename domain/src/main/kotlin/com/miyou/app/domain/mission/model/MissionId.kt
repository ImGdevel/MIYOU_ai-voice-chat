package com.miyou.app.domain.mission.model

import java.util.UUID

data class MissionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "missionId cannot be null or blank" }
    }

    companion object {
        @JvmStatic
        fun of(value: String): MissionId = MissionId(value)

        @JvmStatic
        fun generate(): MissionId = MissionId(UUID.randomUUID().toString())
    }

    fun value(): String = value
}
