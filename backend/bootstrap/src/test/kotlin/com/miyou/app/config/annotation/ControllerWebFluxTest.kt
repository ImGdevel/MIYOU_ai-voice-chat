package com.miyou.app.config.annotation

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.core.annotation.AliasFor
import kotlin.reflect.KClass

/**
 * WebFlux 컨트롤러 테스트 시 중복 설정을 방지하고 일관된 태깅을 적용하기 위한 커스텀 어노테이션.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@WebFluxTest
@Tag("controller")
annotation class ControllerWebFluxTest(
    @get:AliasFor(annotation = WebFluxTest::class, attribute = "controllers")
    val value: Array<KClass<*>> = [],
)
