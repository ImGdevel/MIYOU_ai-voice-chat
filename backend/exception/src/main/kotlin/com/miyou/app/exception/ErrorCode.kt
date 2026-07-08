package com.miyou.app.exception

/**
 * API 에러 코드 정의. HttpStatus는 직접 안 들고 category만 노출한다 (domain이 Spring
 * 몰라도 되게 하기 위함). HTTP 매핑은 api 모듈 ErrorCategoryHttpStatusMapping 전담.
 */
interface ErrorCode {
    val code: String
    val category: ErrorCategory
    val message: String
}
