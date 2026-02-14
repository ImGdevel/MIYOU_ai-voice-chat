package com.study.webflux.rag.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.core.annotation.AliasFor;

import org.junit.jupiter.api.Tag;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebFluxTest
@Tag("controller")
public @interface ControllerWebFluxTest {

	@AliasFor(annotation = WebFluxTest.class, attribute = "controllers")
	Class<?>[] value() default {};

}
