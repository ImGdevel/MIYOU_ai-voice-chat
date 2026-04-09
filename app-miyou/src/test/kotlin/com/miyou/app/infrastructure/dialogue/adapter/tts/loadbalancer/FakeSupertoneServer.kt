package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class FakeSupertoneServer {

	private val endpointBehaviors = ConcurrentHashMap<String, ServerBehavior>()
	private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()

	fun setEndpointBehavior(apiKey: String, behavior: ServerBehavior) {
		endpointBehaviors[apiKey] = behavior
		requestCounts.putIfAbsent(apiKey, AtomicInteger(0))
	}

	fun getRequestCount(apiKey: String): Int =
		requestCounts.getOrDefault(apiKey, AtomicInteger(0)).get()

	fun resetRequestCounts() {
		requestCounts.clear()
	}

	fun routes(): RouterFunction<ServerResponse> =
		RouterFunctions.route()
			.POST("/v1/text-to-speech/{voice_id}/stream", this::handleTtsRequest)
			.HEAD("/**", this::handleHealthCheck)
			.build()

	private fun handleTtsRequest(request: ServerRequest): Mono<ServerResponse> {
		val apiKey = request.headers().firstHeader("x-sup-api-key")
			?: return ServerResponse.status(HttpStatus.UNAUTHORIZED).bodyValue("Missing API key")

		requestCounts.computeIfAbsent(apiKey) { AtomicInteger(0) }.incrementAndGet()

		val behavior = endpointBehaviors.getOrDefault(apiKey, ServerBehavior.success())

		return request.bodyToMono(Map::class.java).flatMap { body ->
			val text = body["text"] as? String

			when {
				text.isNullOrEmpty() ->
					ServerResponse.status(HttpStatus.BAD_REQUEST).bodyValue("Missing text field")

				text.length > 300 ->
					ServerResponse.status(HttpStatus.BAD_REQUEST)
						.bodyValue("Text exceeds 300 characters")

				else -> behavior.apply(request, text)
			}
		}
	}

	private fun handleHealthCheck(request: ServerRequest): Mono<ServerResponse> =
		ServerResponse.ok().build()

	fun interface ServerBehavior {
		fun apply(request: ServerRequest, text: String): Mono<ServerResponse>

		companion object {
			@JvmStatic
			fun success(): ServerBehavior = ServerBehavior { _, _ ->
				val fakeAudio = ByteArray(1024) { index -> (index % 256).toByte() }
				ServerResponse.ok()
					.contentType(MediaType.parseMediaType("audio/wav"))
					.bodyValue(fakeAudio)
			}

			@JvmStatic
			fun error(status: HttpStatus): ServerBehavior = ServerBehavior { _, _ ->
				ServerResponse.status(status).bodyValue("Error: ${status.reasonPhrase}")
			}

			@JvmStatic
			fun delayed(delayMillis: Long): ServerBehavior = ServerBehavior { request, text ->
				Mono.delay(Duration.ofMillis(delayMillis)).then(success().apply(request, text))
			}

			@JvmStatic
			fun timeout(): ServerBehavior = ServerBehavior { _, _ ->
				ServerResponse.ok()
					.contentType(MediaType.parseMediaType("audio/wav"))
					.body(Flux.error<ByteArray>(TimeoutException("Request timeout")), ByteArray::class.java)
			}
		}
	}
}
