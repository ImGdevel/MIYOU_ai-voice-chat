package com.miyou.app.config.annotation

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.core.annotation.AliasFor
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@WebFluxTest
@Tag("controller")
annotation class ControllerWebFluxTest(
    @get:AliasFor(annotation = WebFluxTest::class, attribute = "controllers")
    val value: Array<KClass<*>> = [],
)
