package com.miyou.app.fixture

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryType
import java.time.Instant

object MemoryFixture {
    const val DEFAULT_CONTENT = "사용자는 러닝을 좋아한다"
    const val DEFAULT_IMPORTANCE = 0.8f

    @JvmStatic
    fun createExperiential(): Memory =
        Memory.create(
            ConversationSessionFixture.createId(),
            MemoryType.EXPERIENTIAL,
            DEFAULT_CONTENT,
            DEFAULT_IMPORTANCE,
        )

    @JvmStatic
    fun createExperiential(sessionId: ConversationSessionId): Memory =
        Memory.create(sessionId, MemoryType.EXPERIENTIAL, DEFAULT_CONTENT, DEFAULT_IMPORTANCE)

    @JvmStatic
    fun createFactual(): Memory =
        Memory.create(
            ConversationSessionFixture.createId(),
            MemoryType.FACTUAL,
            "사용자의 직업은 개발자다",
            0.9f,
        )

    @JvmStatic
    fun createFactual(sessionId: ConversationSessionId): Memory =
        Memory.create(sessionId, MemoryType.FACTUAL, "사용자의 직업은 개발자다", 0.9f)

    @JvmStatic
    fun createWithId(
        id: String,
        type: MemoryType,
    ): Memory =
        Memory(
            id,
            ConversationSessionFixture.createId(),
            type,
            DEFAULT_CONTENT,
            DEFAULT_IMPORTANCE,
            Instant.now(),
            Instant.now(),
            1,
        )

    @JvmStatic
    fun createWithId(
        id: String,
        sessionId: ConversationSessionId,
        type: MemoryType,
    ): Memory =
        Memory(
            id,
            sessionId,
            type,
            DEFAULT_CONTENT,
            DEFAULT_IMPORTANCE,
            Instant.now(),
            Instant.now(),
            1,
        )
}
