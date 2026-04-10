package com.miyou.app.infrastructure.memory.config

import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * Prepare Qdrant collection if needed.
 */
@Component
class QdrantCollectionInitializer(
    private val properties: RagDialogueProperties,
    webClientBuilder: WebClient.Builder,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(QdrantCollectionInitializer::class.java)
    private val webClient: WebClient

    init {
        val qdrant = properties.qdrant
        var builder = webClientBuilder.baseUrl(trimTrailingSlash(qdrant.url))
        if (StringUtils.hasText(qdrant.apiKey)) {
            builder = builder.defaultHeader("api-key", qdrant.apiKey)
        }
        webClient = builder.build()
    }

    override fun run(args: ApplicationArguments) {
        val qdrant = properties.qdrant
        if (!qdrant.autoCreateCollection) {
            log.info(
                "Qdrant auto-create is disabled. Skip collection init. collection={}",
                qdrant.collectionName,
            )
            return
        }

        if (collectionExists(qdrant.collectionName)) {
            log.info("Qdrant collection already exists. collection={}", qdrant.collectionName)
            return
        }

        createCollection(qdrant.collectionName, qdrant.vectorDimension)
    }

    private fun collectionExists(collectionName: String): Boolean {
        val exists =
            webClient
                .get()
                .uri("/collections/{collectionName}", collectionName)
                .exchangeToMono { response ->
                    val status: HttpStatusCode = response.statusCode()
                    when {
                        status.is2xxSuccessful -> {
                            Mono.just(true)
                        }

                        status.value() == 404 -> {
                            Mono.just(false)
                        }

                        else -> {
                            response
                                .bodyToMono(String::class.java)
                                .defaultIfEmpty("")
                                .flatMap { body ->
                                    Mono.error<Boolean>(
                                        IllegalStateException(
                                            "Qdrant collection lookup failed status=${status.value()} body=$body"
                                        )
                                    )
                                }
                        }
                    }
                }

        return exists.blockOptional(REQUEST_TIMEOUT).orElse(false)
    }

    private fun createCollection(
        collectionName: String,
        vectorDimension: Int,
    ) {
        val payload: Map<String, Map<String, Any>> =
            mapOf(
                "vectors" to
                    mapOf(
                        "size" to vectorDimension,
                        "distance" to "Cosine",
                    ),
            )

        webClient
            .put()
            .uri("/collections/{collectionName}", collectionName)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .exchangeToMono { response ->
                val status = response.statusCode()
                when {
                    status.is2xxSuccessful || status.value() == 409 -> {
                        Mono.empty<Void>()
                    }

                    else -> {
                        response
                            .bodyToMono(String::class.java)
                            .defaultIfEmpty("")
                            .flatMap { body ->
                                Mono.error<Void>(
                                    IllegalStateException(
                                        "Qdrant collection create failed status=${status.value()} body=$body"
                                    )
                                )
                            }
                    }
                }
            }.block(REQUEST_TIMEOUT)

        log.info(
            "Qdrant collection is ready. collection={}, vectorDimension={}",
            collectionName,
            vectorDimension,
        )
    }

    private fun trimTrailingSlash(url: String): String {
        if (!StringUtils.hasText(url)) {
            return ""
        }
        if (url.endsWith("/")) {
            return url.substring(0, url.length - 1)
        }
        return url
    }

    companion object {
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
