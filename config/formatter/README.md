# Java Code Formatting Configuration

Spotless를 사용한 커스텀 Java 코드 포맷팅 설정입니다.

## 주요 스타일 규칙

### 들여쓰기
- **Tab 사용** (4 spaces 폭)
- Continuation indentation: 1 tab

### Import 순서
1. `java|javax` 패키지
2. `lombok` 패키지
3. `org.springframework` 패키지
4. 빈 줄
5. 프로젝트 패키지 (`com.study.webflux.*`)
6. 빈 줄
7. Static imports

### 중괄호 스타일
- 같은 줄에 여는 중괄호 배치 (end_of_line)
- 클래스, 메서드, 블록 모두 동일

### 메서드 파라미터
- 긴 파라미터 목록: 각 파라미터를 새 줄에 배치
- 짧은 경우: 한 줄로 유지
- 닫는 괄호: 별도 줄에 배치

### 메서드 체이닝
- 라인 길이 제한: 100자
- 각 체이닝은 1 tab 들여쓰기
- 긴 체인은 자동으로 줄바꿈되지만, 짧은 체인은 수동 조정 필요

**권장 스타일 (수동 조정이 필요한 경우):**
```java
return conversationRepository.findRecent(topK * 10)
	.collectList()
	.map(turns -> turns.stream()
		.map(turn -> {
			int score = calculateSimilarity(query, turn.query());
			return RetrievalDocument.of(turn.query(), score);
		})
		.filter(doc -> doc.score().isRelevant())
		.sorted((a, b) -> Integer.compare(b.score().value(), a.score().value()))
		.limit(topK)
		.toList()
	)
```

### Record 스타일
- 각 컴포넌트를 새 줄에 배치
- 닫는 괄호는 별도 줄에 배치
- Compact constructor 사용

```java
public record RagDialogueRequest(
	@NotBlank String text,
	@NotNull Instant requestedAt
) {
}
```

### Enum 스타일
- 각 상수를 새 줄에 배치
- 생성자가 있는 경우도 동일

### 공백 라인
- Package 선언 후: 1줄
- Import 전: 0줄
- Import 후: 1줄
- Import 그룹 사이: 0줄
- 메서드 사이: 1줄
- 필드 사이: 0줄

## Gradle 명령어

### 포맷 체크
```bash
./gradlew spotlessCheck
./gradlew :webflux-dialogue:spotlessCheck
```

### 자동 포맷 적용
```bash
./gradlew spotlessApply
./gradlew :webflux-dialogue:spotlessApply
```

### 빌드 시 자동 체크
```bash
./gradlew build
```

## 설정 파일

- `build.gradle`: Spotless 플러그인 설정
- `config/formatter/eclipse-formatter.xml`: Eclipse 포맷터 상세 설정

## Git Hooks (Husky)

프로젝트에는 자동 코드 품질 관리를 위한 Git 훅이 설정되어 있습니다.

### Pre-commit Hook
커밋 시 자동으로 다음 작업을 수행합니다:
- 코드 포맷팅 (`spotlessApply`)
- Import 정리
- 포맷팅된 파일을 staging area에 자동 추가

```bash
# .husky/pre-commit
./gradlew spotlessApply
git add -u
```

### Pre-push Hook
Push 시 자동으로 다음 작업을 수행합니다:
- 모든 테스트 실행
- 테스트 실패 시 push 취소

```bash
# .husky/pre-push
./gradlew test
```

### Hook 비활성화
특별한 경우 hook을 건너뛰려면:
```bash
git commit --no-verify -m "message"
git push --no-verify
```

## 특정 코드 블록 포맷 제외

포맷을 적용하지 않으려는 코드가 있다면:

```java
// spotless:off
// 이 사이의 코드는 포맷되지 않습니다
// spotless:on
```

## 변경 이력

- 2025-12-16: `io.spring.javaformat`에서 Spotless로 마이그레이션
- 2025-12-16: Git hooks 설정 (pre-commit: 포맷팅, pre-push: 테스트)
