package com.study.webflux.rag.infrastructure.adapter.tts.loadbalancer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class FakeSupertoneServer {

	private final Map<String, ServerBehavior> endpointBehaviors = new ConcurrentHashMap<>();
	private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

	public void setEndpointBehavior(String apiKey, ServerBehavior behavior) {
		endpointBehaviors.put(apiKey, behavior);
		requestCounts.putIfAbsent(apiKey, new AtomicInteger(0));
	}

	public int getRequestCount(String apiKey) {
		return requestCounts.getOrDefault(apiKey, new AtomicInteger(0)).get();
	}

	public void resetRequestCounts() {
		requestCounts.clear();
	}

	public RouterFunction<ServerResponse> routes() {
		return RouterFunctions.route()
			.POST("/v1/text-to-speech/{voice_id}/stream", this::handleTtsRequest)
			.HEAD("/**", this::handleHealthCheck).build();
	}

	private Mono<ServerResponse> handleTtsRequest(ServerRequest request) {
		String apiKey = request.headers().firstHeader("x-sup-api-key");
		if (apiKey == null) {
			return ServerResponse.status(HttpStatus.UNAUTHORIZED).bodyValue("Missing API key");
		}

		requestCounts.computeIfAbsent(apiKey, k -> new AtomicInteger(0)).incrementAndGet();

		ServerBehavior behavior = endpointBehaviors.getOrDefault(apiKey, ServerBehavior.success());

		return request.bodyToMono(Map.class).flatMap(body -> {
			String text = (String) body.get("text");
			if (text == null || text.isEmpty()) {
				return ServerResponse.status(HttpStatus.BAD_REQUEST)
					.bodyValue("Missing text field");
			}

			if (text.length() > 300) {
				return ServerResponse.status(HttpStatus.BAD_REQUEST)
					.bodyValue("Text exceeds 300 characters");
			}

			return behavior.apply(request, text);
		});
	}

	private Mono<ServerResponse> handleHealthCheck(ServerRequest request) {
		return ServerResponse.ok().build();
	}

	@FunctionalInterface
	public interface ServerBehavior {
		Mono<ServerResponse> apply(ServerRequest request, String text);

		static ServerBehavior success() {
			return (request, text) -> {
				byte[] fakeAudio = new byte[1024];
				for (int i = 0; i < fakeAudio.length; i++) {
					fakeAudio[i] = (byte) (i % 256);
				}
				return ServerResponse.ok().contentType(MediaType.parseMediaType("audio/wav"))
					.bodyValue(fakeAudio);
			};
		}

		static ServerBehavior error(HttpStatus status) {
			return (request, text) -> ServerResponse.status(status)
				.bodyValue("Error: " + status.getReasonPhrase());
		}

		static ServerBehavior delayed(long delayMillis) {
			return (request, text) -> Mono.delay(java.time.Duration.ofMillis(delayMillis))
				.then(success().apply(request, text));
		}

		static ServerBehavior timeout() {
			return (request, text) -> ServerResponse.ok()
				.contentType(MediaType.parseMediaType("audio/wav"))
				.body(Flux.<byte[]>error(new TimeoutException("Request timeout")), byte[].class);
		}
	}
}
