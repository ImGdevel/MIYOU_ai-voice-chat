package com.study.webflux.rag.infrastructure.credit.adapter;

import com.study.webflux.rag.domain.credit.model.UserCredit;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.fixture.UserCreditFixture;
import com.study.webflux.rag.fixture.UserIdFixture;
import com.study.webflux.rag.infrastructure.credit.document.UserCreditDocument;
import com.study.webflux.rag.infrastructure.credit.repository.UserCreditMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserCreditMongoAdapter")
class UserCreditMongoAdapterTest {

	@Mock
	private UserCreditMongoRepository mongoRepository;

	private UserCreditMongoAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new UserCreditMongoAdapter(mongoRepository);
	}

	@Nested
	@DisplayName("findByUserId()")
	class FindByUserId {

		@Test
		@DisplayName("존재하는 userId 조회 시 도메인 객체로 변환하여 반환한다")
		void findByUserId_existing_returnsDomain() {
			UserId userId = UserIdFixture.create();
			UserCreditDocument doc = UserCreditDocument.fromDomain(
				UserCreditFixture.create(userId, 3000L));
			when(mongoRepository.findByUserId(userId.value())).thenReturn(Mono.just(doc));

			StepVerifier.create(adapter.findByUserId(userId))
				.assertNext(credit -> {
					assertThat(credit.userId()).isEqualTo(userId);
					assertThat(credit.balance()).isEqualTo(3000L);
				})
				.verifyComplete();
		}

		@Test
		@DisplayName("존재하지 않는 userId 조회 시 빈 Mono를 반환한다")
		void findByUserId_notFound_returnsEmpty() {
			UserId userId = UserIdFixture.create();
			when(mongoRepository.findByUserId(userId.value())).thenReturn(Mono.empty());

			StepVerifier.create(adapter.findByUserId(userId))
				.verifyComplete();
		}

		@Test
		@DisplayName("MongoDB 오류는 그대로 전파된다")
		void findByUserId_error_propagates() {
			UserId userId = UserIdFixture.create();
			when(mongoRepository.findByUserId(userId.value()))
				.thenReturn(Mono.error(new RuntimeException("DB 연결 실패")));

			StepVerifier.create(adapter.findByUserId(userId))
				.expectErrorMessage("DB 연결 실패")
				.verify();
		}
	}

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("도메인 객체를 Document로 변환하여 저장하고 다시 도메인으로 반환한다")
		void save_convertsAndPersists() {
			UserId userId = UserIdFixture.create();
			UserCredit credit = UserCreditFixture.create(userId, 5000L);
			ArgumentCaptor<UserCreditDocument> captor = ArgumentCaptor.forClass(UserCreditDocument.class);
			when(mongoRepository.save(captor.capture()))
				.thenAnswer(inv -> Mono.just(inv.getArgument(0)));

			StepVerifier.create(adapter.save(credit))
				.assertNext(saved -> {
					assertThat(saved.userId()).isEqualTo(userId);
					assertThat(saved.balance()).isEqualTo(5000L);
				})
				.verifyComplete();

			UserCreditDocument captured = captor.getValue();
			assertThat(captured.userId()).isEqualTo(userId.value());
			assertThat(captured.balance()).isEqualTo(5000L);
		}

		@Test
		@DisplayName("저장 실패 시 에러가 전파된다")
		void save_error_propagates() {
			UserCredit credit = UserCreditFixture.create();
			when(mongoRepository.save(any()))
				.thenReturn(Mono.error(new RuntimeException("저장 실패")));

			StepVerifier.create(adapter.save(credit))
				.expectErrorMessage("저장 실패")
				.verify();
		}
	}
}
