package com.miyou.app.infrastructure.credit.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_credits")
public record UserCreditDocument(
	@Id String id,
	@Indexed(unique = true) String userId,
	long balance,
	@Version long version,
	Instant updatedAt) {
	public static UserCreditDocument fromDomain(
		com.miyou.app.domain.credit.model.UserCredit credit) {
		return new UserCreditDocument(
			credit.userId().value(),
			credit.userId().value(),
			credit.balance(),
			credit.version(),
			Instant.now());
	}

	public com.miyou.app.domain.credit.model.UserCredit toDomain() {
		return new com.miyou.app.domain.credit.model.UserCredit(
			com.miyou.app.domain.dialogue.model.UserId.of(userId),
			balance,
			version);
	}
}
