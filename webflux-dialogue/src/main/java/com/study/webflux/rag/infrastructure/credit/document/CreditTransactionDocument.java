package com.study.webflux.rag.infrastructure.credit.document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.study.webflux.rag.domain.credit.model.CreditSource;
import com.study.webflux.rag.domain.credit.model.CreditTransaction;
import com.study.webflux.rag.domain.credit.model.CreditTransactionId;
import com.study.webflux.rag.domain.credit.model.CreditTransactionType;
import com.study.webflux.rag.domain.credit.model.ConversationDeduction;
import com.study.webflux.rag.domain.credit.model.MissionReward;
import com.study.webflux.rag.domain.credit.model.PaymentCharge;
import com.study.webflux.rag.domain.credit.model.SignupBonus;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.mission.model.MissionId;

@Document(collection = "credit_transactions")
@CompoundIndexes({
	@CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}")
})
public record CreditTransactionDocument(
	@Id String id,
	@Indexed String userId,
	String type,
	String sourceType,
	Map<String, String> sourceData,
	long amount,
	long balanceBefore,
	long balanceAfter,
	String referenceId,
	Instant createdAt
) {
	public static CreditTransactionDocument fromDomain(CreditTransaction tx) {
		return new CreditTransactionDocument(
			tx.transactionId().value(),
			tx.userId().value(),
			tx.type().name(),
			tx.source().sourceType().name(),
			serializeSource(tx.source()),
			tx.amount(),
			tx.balanceBefore(),
			tx.balanceAfter(),
			tx.referenceId(),
			tx.createdAt());
	}

	public CreditTransaction toDomain() {
		return new CreditTransaction(
			CreditTransactionId.of(id),
			UserId.of(userId),
			CreditTransactionType.valueOf(type),
			deserializeSource(sourceType, sourceData),
			amount,
			balanceBefore,
			balanceAfter,
			referenceId,
			createdAt);
	}

	private static Map<String, String> serializeSource(CreditSource source) {
		Map<String, String> data = new HashMap<>();
		switch (source) {
			case ConversationDeduction d -> data.put("sessionId", d.sessionId().value());
			case PaymentCharge p -> {
				data.put("paymentId", p.paymentId());
				data.put("pgProvider", p.pgProvider());
			}
			case MissionReward m -> {
				data.put("missionId", m.missionId().value());
				data.put("missionType", m.missionType());
			}
			case SignupBonus ignored -> {
			}
		}
		return data;
	}

	private static CreditSource deserializeSource(String sourceType,
		Map<String, String> sourceData) {
		return switch (sourceType) {
			case "CONVERSATION_DEDUCTION" ->
				new ConversationDeduction(ConversationSessionId.of(sourceData.get("sessionId")));
			case "SIGNUP_BONUS" -> new SignupBonus();
			case "PAYMENT_CHARGE" ->
				new PaymentCharge(sourceData.get("paymentId"), sourceData.get("pgProvider"));
			case "MISSION_REWARD" ->
				new MissionReward(MissionId.of(sourceData.get("missionId")),
					sourceData.get("missionType"));
			default -> throw new IllegalArgumentException("알 수 없는 CreditSourceType: " + sourceType);
		};
	}
}
