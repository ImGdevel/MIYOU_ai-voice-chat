package com.miyou.app.infrastructure.credit.adapter

import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.fixture.UserCreditFixture
import com.miyou.app.fixture.UserIdFixture
import com.miyou.app.infrastructure.credit.document.UserCreditDocument
import com.miyou.app.infrastructure.credit.repository.UserCreditMongoRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
@DisplayName("UserCreditMongoAdapter")
class UserCreditMongoAdapterTest {

	@Mock
	private lateinit var mongoRepository: UserCreditMongoRepository

	private lateinit var adapter: UserCreditMongoAdapter

	@BeforeEach
	fun setUp() {
		adapter = UserCreditMongoAdapter(mongoRepository)
	}

	@Nested
	@DisplayName("findByUserId()")
	inner class FindByUserId {

		@Test
		@DisplayName("존재하는 userId 조회 시 도메인 객체로 변환하여 반환한다")
		fun findByUserId_existing_returnsDomain() {
			val userId = UserIdFixture.create()
			val doc = UserCreditDocument.fromDomain(UserCreditFixture.create(userId, 3000L))
			`when`(mongoRepository.findByUserId(userId.value())).thenReturn(Mono.just(doc))

			StepVerifier.create(adapter.findByUserId(userId))
				.assertNext { credit ->
					assertThat(credit.userId()).isEqualTo(userId)
					assertThat(credit.balance()).isEqualTo(3000L)
				}
				.verifyComplete()
		}

		@Test
		@DisplayName("존재하지 않는 userId 조회 시 빈 Mono를 반환한다")
		fun findByUserId_notFound_returnsEmpty() {
			val userId = UserIdFixture.create()
			`when`(mongoRepository.findByUserId(userId.value())).thenReturn(Mono.empty())

			StepVerifier.create(adapter.findByUserId(userId))
				.verifyComplete()
		}

		@Test
		@DisplayName("MongoDB 오류는 그대로 전파한다")
		fun findByUserId_error_propagates() {
			val userId = UserIdFixture.create()
			`when`(mongoRepository.findByUserId(userId.value()))
				.thenReturn(Mono.error(RuntimeException("DB 연결 실패")))

			StepVerifier.create(adapter.findByUserId(userId))
				.expectErrorMessage("DB 연결 실패")
				.verify()
		}
	}

	@Nested
	@DisplayName("save()")
	inner class Save {

		@Test
		@DisplayName("도메인 객체를 Document로 변환해 저장하고 다시 도메인으로 반환한다")
		fun save_convertsAndPersists() {
			val userId = UserIdFixture.create()
			val credit: UserCredit = UserCreditFixture.create(userId, 5000L)
			val captor = ArgumentCaptor.forClass(UserCreditDocument::class.java)
			`when`(mongoRepository.save(captor.capture()))
				.thenAnswer { invocation: InvocationOnMock ->
					Mono.just(invocation.getArgument<UserCreditDocument>(0))
				}

			StepVerifier.create(adapter.save(credit))
				.assertNext { saved ->
					assertThat(saved.userId()).isEqualTo(userId)
					assertThat(saved.balance()).isEqualTo(5000L)
				}
				.verifyComplete()

			val captured = captor.value
			assertThat(captured.userId()).isEqualTo(userId.value())
			assertThat(captured.balance()).isEqualTo(5000L)
		}

		@Test
		@DisplayName("저장 실패 시 에러가 전파된다")
		fun save_error_propagates() {
			val credit = UserCreditFixture.create()
			`when`(mongoRepository.save(any()))
				.thenReturn(Mono.error(RuntimeException("저장 실패")))

			StepVerifier.create(adapter.save(credit))
				.expectErrorMessage("저장 실패")
				.verify()
		}
	}
}
