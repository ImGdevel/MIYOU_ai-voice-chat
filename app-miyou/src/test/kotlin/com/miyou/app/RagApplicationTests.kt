package com.miyou.app

import com.miyou.app.support.ContainerizedIntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [RagApplication::class])
@ActiveProfiles("test")
class RagApplicationTests : ContainerizedIntegrationTestSupport() {
    @Test
    fun contextLoads() {
    }
}
