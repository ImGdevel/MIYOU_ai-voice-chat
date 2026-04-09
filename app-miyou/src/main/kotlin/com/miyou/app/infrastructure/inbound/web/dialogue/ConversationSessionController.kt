package com.miyou.app.infrastructure.inbound.web.dialogue

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.CreateSessionRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/rag/session")
class ConversationSessionController(
    private val sessionRepository: ConversationSessionRepository,
    private val creditChargeUseCase: CreditChargeUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createSession(
        @Valid @RequestBody request: CreateSessionRequest,
    ): Mono<ConversationSession> {
        val userId = UserId.of(request.userId)
        val session =
            ConversationSession.create(
                PersonaId.of(request.personaId),
                userId,
            )
        return sessionRepository
            .save(session)
            .flatMap { saved ->
                creditChargeUseCase.initializeIfAbsent(userId).thenReturn(saved)
            }
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSession(
        @PathVariable sessionId: String,
    ): Mono<Void> = sessionRepository.softDelete(ConversationSessionId.of(sessionId)).then()

    @GetMapping
    fun getSessions(
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) personaId: String?,
    ): Flux<ConversationSession> {
        if (personaId != null && userId != null) {
            return sessionRepository.findByPersonaIdAndUserId(
                PersonaId.of(personaId),
                UserId.of(userId),
            )
        }
        if (personaId != null) {
            return sessionRepository.findByPersonaId(PersonaId.of(personaId))
        }
        if (userId != null) {
            return sessionRepository.findByUserId(UserId.of(userId))
        }
        return Flux.empty()
    }
}
