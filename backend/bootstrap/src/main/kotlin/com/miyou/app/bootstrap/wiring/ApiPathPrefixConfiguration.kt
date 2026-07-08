package com.miyou.app.bootstrap.wiring

import com.miyou.app.api.monitoring.DashboardController
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.reactive.config.PathMatchConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * app.api.prefix(/api/v1)를 @RestController 핸들러에만 적용한다.
 *
 * spring.webflux.base-path는 전체 디스패처(actuator, 정적 리소스, Spring Security의
 * OAuth2 로그인/콜백 필터까지)를 그 경로 밑으로 옮겨버려서 프론트 정적 서빙과 OAuth
 * redirect URI가 같이 깨진다 - 실측으로 확인된 문제. 대신 addPathPrefix로 컨트롤러
 * 핸들러 매핑에만 접두사를 건다.
 *
 * DashboardController(/dashboard, /monitoring)는 사람이 브라우저 주소창에 직접 치는
 * 운영용 단축 리다이렉트라 API prefix 대상에서 제외한다.
 *
 * api 모듈의 컨트롤러 클래스를 직접 참조해야 해서 bootstrap(전체 조립 지점)에 둔다 -
 * infrastructure 모듈은 api 모듈을 모른다.
 */
@Configuration
class ApiPathPrefixConfiguration(
    @Value("\${app.api.prefix}") private val apiPrefix: String,
) : WebFluxConfigurer {
    override fun configurePathMatching(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(apiPrefix) { controllerType ->
            HandlerTypePredicate.forAnnotation(RestController::class.java).test(controllerType) &&
                controllerType != DashboardController::class.java
        }
    }
}
