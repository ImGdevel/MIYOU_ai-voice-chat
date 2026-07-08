package com.miyou.app.exception

/**
 * API 에러 코드 정의.
 *
 * HttpStatus를 직접 들고 있지 않는다 - "이 에러가 무슨 의미냐"(category)와
 * "REST에서 어떤 status로 노출되냐"를 분리해, 순수 Java/Kotlin으로만 구성되어야 하는
 * domain 모듈이 Spring을 알지 않아도 되게 한다. HTTP status 변환은 api 모듈의
 * ErrorCategoryHttpStatusMapping이 전담한다.
 */
interface ErrorCode {
    val code: String
    val category: ErrorCategory
    val message: String
}
