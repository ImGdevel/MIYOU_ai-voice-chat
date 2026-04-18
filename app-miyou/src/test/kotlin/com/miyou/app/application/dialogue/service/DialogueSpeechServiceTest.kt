package com.miyou.app.application.dialogue.service

import com.miyou.app.application.dialogue.policy.SttPolicy
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.domain.dialogue.port.SttPort
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.support.anyValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
    @DisplayName("음성을 변환한 텍스트와 결합된 응답을 함께 반환한다")
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
                ByteArray(2048) { 1 },
            )
        val session = ConversationSessionFixture.create()

        `when`(sttPort.transcribe(anyValue())).thenReturn(Mono.just("what is on the schedule?"))
        `when`(dialoguePipelineUseCase.executeTextOnly(anyValue(), anyValue()))
            .thenReturn(Flux.just("Today the meeting is ", "at 2pm."))

        StepVerifier
            .create(service.transcribeAndRespond(session, audioFile, null))
            .expectNextMatches { response ->
                response.transcription == "what is on the schedule?" &&
                    response.response == "Today the meeting is at 2pm."
            }.verifyComplete()
    }

    @Test
    @DisplayName("오디오가 아닌 파일 업로드는 400 오류로 거부한다")
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
                assertThat(error).isInstanceOf(ResponseStatusException::class.java)
                assertThat((error as ResponseStatusException).statusCode.is4xxClientError).isTrue()
            }.verify()
    }

    @Test
    @DisplayName("너무 짧은 오디오 업로드는 400 오류로 거부한다")
    fun transcribe_shouldRejectTooShortAudioFile() {
        val sttPort = mock(SttPort::class.java)
        val dialoguePipelineUseCase = mock(DialoguePipelineUseCase::class.java)
        val service =
            DialogueSpeechService(
                sttPort,
                dialoguePipelineUseCase,
                SttPolicy(25L * 1024L * 1024L, "ko"),
            )
        val audioFile = createFilePart("short.webm", MediaType.parseMediaType("audio/webm"), ByteArray(310) { 1 })

        StepVerifier
            .create(service.transcribe(audioFile, "ko"))
            .expectErrorSatisfies { error ->
                assertThat(error).isInstanceOf(ResponseStatusException::class.java)
                assertThat((error as ResponseStatusException).statusCode.value()).isEqualTo(400)
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
