package com.miyou.app.application.dialogue.service

import com.miyou.app.application.dialogue.policy.SttPolicy
import com.miyou.app.domain.dialogue.exception.AudioTooShortException
import com.miyou.app.domain.dialogue.exception.InvalidAudioFileException
import com.miyou.app.domain.dialogue.port.SttPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class DialogueSpeechServiceTest {
    @Test
    @DisplayName("오디오가 아닌 파일 업로드는 400 오류로 거부한다")
    fun transcribe_shouldRejectNonAudioFile() {
        val sttPort = mock(SttPort::class.java)
        val service =
            DialogueSpeechService(
                sttPort,
                SttPolicy(25L * 1024L * 1024L, "ko"),
            )
        val textFile = createFilePart("test.txt", MediaType.TEXT_PLAIN, "text".toByteArray())

        StepVerifier
            .create(service.transcribe(textFile, "ko"))
            .expectErrorSatisfies { error ->
                assertThat(error).isInstanceOf(InvalidAudioFileException::class.java)
                val ex = error as InvalidAudioFileException
                assertThat(ex.reason).contains("contentType is not audio")
                assertThat(ex.details["reason"]).isEqualTo("contentType is not audio: text/plain")
            }.verify()
    }

    @Test
    @DisplayName("너무 짧은 오디오 업로드는 400 오류로 거부한다")
    fun transcribe_shouldRejectTooShortAudioFile() {
        val sttPort = mock(SttPort::class.java)
        val service =
            DialogueSpeechService(
                sttPort,
                SttPolicy(25L * 1024L * 1024L, "ko"),
            )
        val audioFile = createFilePart("short.webm", MediaType.parseMediaType("audio/webm"), ByteArray(310) { 1 })

        StepVerifier
            .create(service.transcribe(audioFile, "ko"))
            .expectErrorSatisfies { error ->
                assertThat(error).isInstanceOf(AudioTooShortException::class.java)
                val ex = error as AudioTooShortException
                assertThat(ex.size).isEqualTo(310)
                assertThat(ex.minSize).isEqualTo(1024)
                assertThat(ex.details["size"]).isEqualTo(310)
                assertThat(ex.details["minSize"]).isEqualTo(1024)
            }.verify()
    }

    @Test
    @DisplayName("파일명이 빈 오디오 업로드는 400 오류로 거부한다 (500 아님)")
    fun transcribe_shouldRejectBlankFilename() {
        val sttPort = mock(SttPort::class.java)
        val service =
            DialogueSpeechService(
                sttPort,
                SttPolicy(25L * 1024L * 1024L, "ko"),
            )
        val audioFile = createFilePart("", MediaType.parseMediaType("audio/webm"), ByteArray(2048) { 1 })

        StepVerifier
            .create(service.transcribe(audioFile, "ko"))
            .expectErrorSatisfies { error ->
                assertThat(error).isInstanceOf(InvalidAudioFileException::class.java)
                val ex = error as InvalidAudioFileException
                assertThat(ex.reason).isEqualTo("filename is blank")
                assertThat(ex.details["reason"]).isEqualTo("filename is blank")
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
