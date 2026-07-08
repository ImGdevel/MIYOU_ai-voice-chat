package com.miyou.app.infrastructure.auth.security

/**
 * OAuth 제공자별 attributes 맵에서 값을 안전하게 꺼내기 위한 공통 헬퍼.
 * 제공자마다 attributes 구조(중첩 깊이, 키 이름)가 달라 추출 로직 자체는
 * 공통화하지 않고, 반복되던 null 처리 방식만 통일한다.
 */
internal fun Map<String, Any?>.stringOf(key: String): String? = this[key] as? String

internal fun Map<String, Any?>.requiredIdOf(key: String): String = this[key]?.toString() ?: ""
