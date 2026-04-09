package com.miyou.app.infrastructure.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableTransactionManagement
class MongoTransactionConfiguration {
    @Bean
    fun transactionManager(factory: ReactiveMongoDatabaseFactory): ReactiveTransactionManager =
        ReactiveMongoTransactionManager(factory)
}
