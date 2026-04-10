package com.miyou.app.config.annotation

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.test.context.ActiveProfiles

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@DataMongoTest
@ActiveProfiles("test")
@Tag("repository")
annotation class ReactiveRepositoryTest
