package com.miyou.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class RagApplication

fun main(args: Array<String>) {
    runApplication<RagApplication>(*args)
}
