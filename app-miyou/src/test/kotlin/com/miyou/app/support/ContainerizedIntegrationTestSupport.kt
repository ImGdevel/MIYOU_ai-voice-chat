package com.miyou.app.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

abstract class ContainerizedIntegrationTestSupport {

	companion object {
		private val mongoContainer: MongoDBContainer by lazy {
			MongoDBContainer(DockerImageName.parse("mongo:7.0")).apply {
				start()
			}
		}

		private val redisContainer: GenericContainer<Nothing> by lazy {
			GenericContainer<Nothing>(DockerImageName.parse("redis:7.2-alpine"))
				.withExposedPorts(6379)
				.apply {
					start()
				}
		}

		@JvmStatic
		@DynamicPropertySource
		fun registerProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.data.mongodb.uri") { mongoContainer.replicaSetUrl }
			registry.add("spring.data.redis.host") { redisContainer.host }
			registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379) }
		}
	}
}
