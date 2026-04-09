package com.miyou.app.application.dialogue.service

import com.miyou.app.application.dialogue.policy.SttPolicy
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.domain.dialogue.port.SttPort
import com.miyou.app.fixture.ConversationSessionFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class DialogueSpeechServiceTest {
    @Test
    @DisplayName("음성 파일을 전사하고 텍스트 응답까지 생성한다")
    fun transcribeAndRespond_shouldReturnTranscriptionAndResponse() {
        val sttPort = mock(SttPort::class.java)
        val dialoguePipelineUseCase = mock(DialoguePipelineUseCase::class.java)
        val service =
            DialogueSpeechService(
                sttPort,
                dialoguePipelineUseCase,
                SttPolicy(25L * 1024L * 1024L, "ko"),
            )
        val audioFile =
            createFilePart(
                "voice.mp3",
                MediaType.parseMediaType("audio/mpeg"),
                "dummy-audio".toByteArray(),
            )
        val session = ConversationSessionFixture.create()

        `when`(sttPort.transcribe(any())).thenReturn(Mono.just("회의 일정 알려줘"))
        `when`(dialoguePipelineUseCase.executeTextOnly(any(), any()))
            .thenReturn(Flux.just("오늘 회의는 ", "오후 2시입니다."))

        val result = service.transcribeAndRespond(session, audioFile, null)

        StepVerifier
            .create(result)
            .expectNextMatches { response ->
                response.transcription() == "회의 일정 알려줘" &&
                    response.response() == "오늘 회의는 오후 2시입니다."
            }.verifyComplete()
    }

    @Test
    @DisplayName("오디오가 아닌 파일 업로드는 400 예외를 반환한다")
    fun transcribe_shouldRejectNonAudioFile() {
        val sttPort = mock(SttPort::class.java)
        val dialoguePipelineUseCase = mock(DialoguePipelineUseCase::class.java)
        val service =
            DialogueSpeechService(
                sttPort,
                dialoguePipelineUseCase,
                SttPolicy(25L * 1024L * 1024L, "ko"),
            )
        val textFile = createFilePart("test.txt", MediaType.TEXT_PLAIN, "text".toByteArray())

        StepVerifier
            .create(service.transcribe(textFile, "ko"))
            .expectErrorSatisfies { error ->
                check(error is ResponseStatusException) {
                    "ResponseStatusException이어야 합니다"
                }
                check(error.statusCode.is4xxClientError) {
                    "4xx 오류여야 합니다"
                }
            }.verify()
    }

    private fun createFilePart(
        filename: String,
        contentType: MediaType,
        bytes: ByteArray,
    ): FilePart {
        val filePart = mock(FilePart::class.java)
        val headers = HttpHeaders()
        headers.contentType = contentType

        `when`(filePart.filename()).thenReturn(filename)
        `when`(filePart.headers()).thenReturn(headers)
        `when`(filePart.content()).thenReturn(Flux.just(DefaultDataBufferFactory().wrap(bytes)))

        return filePart
    }
}
