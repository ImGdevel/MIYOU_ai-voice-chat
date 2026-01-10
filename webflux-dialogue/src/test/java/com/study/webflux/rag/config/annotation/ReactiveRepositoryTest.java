package com.study.webflux.rag.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;

import org.junit.jupiter.api.Tag;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataMongoTest
@ActiveProfiles("test")
@Tag("repository")
public @interface ReactiveRepositoryTest {

}
