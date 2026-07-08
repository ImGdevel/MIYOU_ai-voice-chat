package com.miyou.app.domain.mission.model

data class Mission(
    val missionId: MissionId,
    val type: MissionType,
    val name: String,
    val description: String?,
    val rewardAmount: Long,
    val repeatable: Boolean,
) {
    init {
        require(name.isNotBlank()) { "미션 이름은 비어 있을 수 없습니다." }
        require(rewardAmount > 0) { "보상 금액은 양수여야 합니다." }
    }

    companion object {
        @JvmStatic
        fun create(
            type: MissionType,
            name: String,
            description: String?,
            rewardAmount: Long,
            repeatable: Boolean,
        ): Mission = Mission(MissionId.generate(), type, name, description, rewardAmount, repeatable)
    }

    fun missionId(): MissionId = missionId

    fun type(): MissionType = type

    fun name(): String = name

    fun description(): String? = description

    fun rewardAmount(): Long = rewardAmount

    fun repeatable(): Boolean = repeatable
}
