package com.miyou.app.infrastructure.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MongoDB Replica Set 환경에서 multi-document 트랜잭션을 활성화합니다.
 *
 * @Transactional 애노테이션이 붙은 reactive 메서드에서 잔액 업데이트와 트랜잭션 로그 삽입을 단일 원자적 연산으로 처리합니다.
 *
 *                전제 조건: MongoDB가 Replica Set으로 구성되어 있어야 합니다.
 */
@Configuration
@EnableTransactionManagement
public class MongoTransactionConfiguration {

	@Bean
	public ReactiveTransactionManager transactionManager(ReactiveMongoDatabaseFactory factory) {
		return new ReactiveMongoTransactionManager(factory);
	}
}
