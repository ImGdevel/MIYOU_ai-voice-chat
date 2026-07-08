# MIYOU Plan Index

이 디렉터리는 MIYOU 프로젝트의 실행 계획 문서를 모아두는 인덱스다.

## Documents

- [2026-07-07_1429_refactoring-candidates.md](2026-07-07_1429_refactoring-candidates.md) — 전체 계층(domain/application/api/infrastructure/frontend) 리팩토링 후보 전수 조사, GitHub 이슈 11건 매핑.
- [2026-07-08_1432_domain-validation-error-boundary.md](2026-07-08_1432_domain-validation-error-boundary.md) — 도메인 require()/check() 예외의 400/500 경계 설계, DTO-도메인 검증 갭 감사 결과.
- [2026-07-08_1700_error-category-http-status-separation.md](2026-07-08_1700_error-category-http-status-separation.md) — ErrorCode에서 HttpStatus 분리, ErrorCategory 도입 배경/설계, Google AIP-193·Stripe 사례 참조.
- [2026-07-08_1830_prompt-engineering-strategy.md](2026-07-08_1830_prompt-engineering-strategy.md) — 시스템 프롬프트 조립 구조 평가, 템플릿 인코딩 버그 수정, 입력부/출력부 정렬 전략 계획.
- [2026-07-08_1830_prompt-engineering-strategy_v2.md](2026-07-08_1830_prompt-engineering-strategy_v2.md) — v1 평가에서 나온 6개 문제(지침 중복/퓨샷 편중/길이 제약 없음/컨텍스트 프레이밍/검증 부재/모델 부조화) 재설계.

## Rule

- 계획 문서는 `YYYY-MM-DD_HHMM_<slug>.md` 형식을 기본으로 사용한다.
- 같은 주제 후속 계획이면 `_v2`, `_v3`로 버전을 올린다.
