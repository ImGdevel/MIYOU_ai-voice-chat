# 모니터링 시스템 구현 로드맵

## 📅 구현 일정 및 우선순위

### 전체 일정 요약

| Phase | 소요 시간 | 우선순위 | 주요 산출물 |
|-------|----------|---------|----------|
| Phase 1A | 1-2일 | CRITICAL | 파이프라인 병목 메트릭 + 대시보드 |
| Phase 1B | 2-3일 | CRITICAL | RAG 품질 메트릭 + 대시보드 |
| Phase 1C | 1-2일 | HIGH | LLM/대화 메트릭 + 로그 대시보드 |
| Phase 2 | 2-3일 | MEDIUM | 비용/UX 메트릭 + 대시보드 |
| Phase 3 | 3-5일 | LOW | 안정성 메트릭 + MongoDB 통합 |

**총 소요 예상**: 9-15일

---

## 🔴 Phase 1A: 파이프라인 병목 분석 (CRITICAL)

### 목표
사용자 요구사항 #1, #2, #3 충족:
- ✅ 각 파이프라인 Stage간의 데이터 흐름 속도 및 처리량
- ✅ Stage별 메모리 사용량
- ✅ 파이프라인 병목 현상 탐지

### Day 1 (4-6시간)

#### AM: 코드 구현
1. **DialoguePipelineTracker.java 수정** (30분)
   - `calculateStageGaps()` 메서드 추가
   - 테스트 코드 작성

2. **PipelineMetricsConfiguration.java 생성** (1.5시간)
   - Stage Gap Timer
   - Pipeline Duration Timer
   - Stage Duration Timer
   - Execution Counter
   - Active Count Gauge
   - Sentence Buffer Gauge
   - Data Size Gauge

3. **PersistentPipelineMetricsReporter.java 수정** (1.5시간)
   - Micrometer 메트릭 등록 로직 통합
   - Gap 계산 및 등록
   - Data Size 추정 및 등록

#### PM: TTS 및 검증
4. **LoadBalancedSupertoneTtsAdapter.java 수정** (1시간)
   - Queue Size Gauge
   - Wait Time Timer

5. **로컬 테스트** (1시간)
   - 애플리케이션 재시작
   - `/actuator/prometheus` 엔드포인트 확인
   - 메트릭 노출 검증
   - 테스트 요청 발생시켜 메트릭 변화 확인

### Day 2 (3-4시간)

#### AM: 대시보드 생성
6. **miyou-pipeline-bottleneck.json 작성** (2-3시간)
   - Row 1: 파이프라인 KPI (4 Stat 패널)
   - Row 2: Stage별 실행 시간 (Heatmap + TimeSeries)
   - Row 3: Stage Gap 분석 (Bar Gauge + TimeSeries)
   - Row 4: Backpressure 지표 (3 Gauge)
   - Row 5: 메모리 사용량 (2 TimeSeries)

#### PM: 검증 및 튜닝
7. **Grafana 대시보드 검증** (1시간)
   - 패널 데이터 로딩 확인
   - 쿼리 성능 확인
   - 임계값 조정
   - 색상 및 레이아웃 정리

### 산출물
- ✅ `PipelineMetricsConfiguration.java` (신규)
- ✅ `DialoguePipelineTracker.java` (수정)
- ✅ `PersistentPipelineMetricsReporter.java` (수정)
- ✅ `LoadBalancedSupertoneTtsAdapter.java` (수정)
- ✅ `miyou-pipeline-bottleneck.json` (신규 대시보드)

### 검증 기준
```bash
# 메트릭 확인
curl http://localhost:8080/actuator/prometheus | grep "pipeline_stage_gap"
# 예상 출력: pipeline_stage_gap_duration_bucket{from_stage="MEMORY_RETRIEVAL",to_stage="RETRIEVAL",...}

# 대시보드 확인
# - 모든 패널 데이터 표시 OK
# - Gap 분석에서 병목 Stage 식별 가능
```

---

## 🔍 Phase 1B: RAG 품질 모니터링 (CRITICAL)

### 목표
사용자 요구사항 #4, #5 충족:
- ✅ RAG(VectorDB) 검색 결과 품질 및 내용
- ✅ 메모리 추출 품질 및 결과 내용

### Day 3 (5-6시간)

#### AM: Vector 검색 품질
1. **ScoredMemory 클래스 생성** (20분)
   - Memory + similarity score 래퍼 클래스

2. **MemoryRetrievalService.java 수정** (1.5시간)
   - `searchCandidateMemories()` → `ScoredMemory` 반환
   - `rankAndLimit()` 메서드 수정
   - Pipeline Attributes에 점수 저장

3. **SpringAiVectorDbAdapter.java 수정** (40분)
   - `ScoredPoint.score` 노출
   - `ScoredMemory` 변환

#### PM: 메트릭 설정
4. **RagQualityMetricsConfiguration.java 생성** (1.5시간)
   - Similarity Score Histogram
   - Importance Histogram
   - Filtered Count Counter
   - Access Count Summary

5. **PersistentPipelineMetricsReporter.java 통합** (1시간)
   - RAG 품질 메트릭 등록 로직

### Day 4 (5-6시간)

#### AM: 메모리 추출 품질
6. **MemoryExtractionMetricsConfiguration.java 생성** (1시간)
   - Triggered Counter
   - Success/Failure Counter
   - Type Counter
   - Importance Histogram
   - Duration Timer

7. **MemoryExtractionService.java 수정** (1.5시간)
   - 트리거 메트릭 호출
   - 성공/실패 메트릭 호출
   - 타입 및 중요도 메트릭 호출

#### PM: 로컬 테스트
8. **로컬 테스트** (1.5시간)
   - 메트릭 노출 확인
   - Vector 검색 수행하여 유사도 점수 확인
   - 메모리 추출 트리거하여 메트릭 확인

### Day 5 (4-5시간)

#### AM: 대시보드 생성
9. **MongoDB 데이터소스 설정** (30분)
   - `datasources.yml`에 MongoDB 추가
   - 연결 테스트

#### AM-PM: 대시보드 작성
10. **miyou-rag-quality.json 작성** (3-4시간)
    - Row 1: RAG 품질 KPI (4 Stat)
    - Row 2: 유사도 분포 (Heatmap + Histogram)
    - Row 3: 중요도 및 타입 (TimeSeries + Gauge)
    - Row 4: 검색 내용 MongoDB Table
    - Row 5: 문서 검색 품질 (3 패널)
    - Row 6: 메모리 추출 품질 (3 패널)
    - Row 7: 추출 내용 MongoDB Table

#### PM: 검증
11. **Grafana 대시보드 검증** (30분)
    - MongoDB 쿼리 정상 작동 확인
    - 검색된 메모리 내용 Table 표시 확인
    - 추출된 메모리 내용 Table 표시 확인

### 산출물
- ✅ `ScoredMemory.java` (신규)
- ✅ `MemoryRetrievalService.java` (수정)
- ✅ `SpringAiVectorDbAdapter.java` (수정)
- ✅ `RagQualityMetricsConfiguration.java` (신규)
- ✅ `MemoryExtractionService.java` (수정)
- ✅ `MemoryExtractionMetricsConfiguration.java` (신규)
- ✅ `PersistentPipelineMetricsReporter.java` (추가 수정)
- ✅ `datasources.yml` (MongoDB 데이터소스 추가)
- ✅ `miyou-rag-quality.json` (신규 대시보드)

### 검증 기준
```bash
# 메트릭 확인
curl http://localhost:8080/actuator/prometheus | grep "rag_memory_similarity_score"
curl http://localhost:8080/actuator/prometheus | grep "memory_extraction_success"

# 대시보드 확인
# - 유사도 점수 Heatmap 정상 표시
# - MongoDB Table에서 검색된 메모리 내용 확인 가능
# - 추출된 메모리 내용 확인 가능
```

---

## 📊 Phase 1C: LLM/대화 메트릭 + 로그 대시보드 (HIGH)

### 목표
기존 계획 Phase 1.1, 1.2 완료

### Day 6 (4-5시간)

#### AM: LLM/대화 메트릭
1. **LlmMetricsConfiguration.java 생성** (1시간)
   - Prompt Token Counter
   - Completion Token Counter

2. **ConversationMetricsConfiguration.java 생성** (1.5시간)
   - Daily Count Gauge (Redis 연동)
   - Active Sessions Gauge

3. **LLM 클라이언트 통합** (1시간)
   - 토큰 카운터 호출 추가

#### PM: 로그 대시보드
4. **miyou-application-logs.json 생성** (1.5-2시간)
   - Row 1: 로그 레벨 분포
   - Row 2: 에러 추이
   - Row 3: Top 에러 메시지
   - Row 4: 파이프라인 로그
   - Row 5: 느린 요청 로그

### 산출물
- ✅ `LlmMetricsConfiguration.java` (신규)
- ✅ `ConversationMetricsConfiguration.java` (신규)
- ✅ LLM 클라이언트 (수정)
- ✅ `miyou-application-logs.json` (신규 대시보드)

### 검증 기준
```bash
# 메트릭 확인
curl http://localhost:8080/actuator/prometheus | grep "llm_tokens_prompt"
curl http://localhost:8080/actuator/prometheus | grep "conversation_daily_count"

# 대시보드 확인
# - Loki 로그 패널 정상 표시
# - 로그 레벨 분포 확인
```

---

## 💰 Phase 2: 비용 추적 + UX 메트릭 (MEDIUM)

### 목표
운영 효율성 지표 추가

### Day 7-8 (4-6시간)

#### 비용 추적
1. **CostTrackingMetricsConfiguration.java 생성** (1.5시간)
   - LLM Cost Counter (토큰 기반 계산)
   - TTS Cost Counter

2. **miyou-cost-usage.json 생성** (2시간)
   - Row 1: 비용 KPI (3 패널)
   - Row 2: 비용 추이 (Stacked Area)
   - Row 3: 토큰 사용량 (2 패널)
   - Row 4: 사용자별 비용 (Table + Pie Chart)

#### UX 메트릭
3. **UxMetricsConfiguration.java 생성** (1시간)
   - Response Latency Histogram
   - Error Rate Counter

4. **miyou-ux.json 생성** (1.5시간)
   - Row 1: UX KPI (3 Stat)
   - Row 2: 응답 시간 분포 (Heatmap)
   - Row 3: 에러 분석 (TimeSeries + Table)

### 산출물
- ✅ `CostTrackingMetricsConfiguration.java` (신규)
- ✅ `UxMetricsConfiguration.java` (신규)
- ✅ `miyou-cost-usage.json` (신규 대시보드)
- ✅ `miyou-ux.json` (신규 대시보드)

---

## 🔒 Phase 3: 시스템 안정성 + MongoDB 통합 (LOW)

### 목표
고급 안정성 메트릭 및 MongoDB Exporter

### Day 9-12 (6-8시간)

#### 안정성 메트릭
1. **StabilityMetricsConfiguration.java 생성** (1.5시간)
   - Circuit Breaker State Gauge
   - Retry Attempts/Success Counter
   - Timeout Counter

2. **miyou-stability.json 생성** (1.5시간)
   - Row 1: Circuit Breaker 상태
   - Row 2: Retry 성공률
   - Row 3: Timeout 분석

#### MongoDB 통합
3. **docker-compose.monitoring.yml 수정** (1시간)
   - MongoDB Exporter 서비스 추가

4. **prometheus.yml 수정** (30분)
   - MongoDB Exporter 타겟 추가

5. **검증** (1-2시간)
   - MongoDB 메트릭 수집 확인
   - 성능 테스트

### 산출물
- ✅ `StabilityMetricsConfiguration.java` (신규)
- ✅ `miyou-stability.json` (신규 대시보드)
- ✅ `docker-compose.monitoring.yml` (수정)
- ✅ `prometheus.yml` (수정)

---

## 📋 일일 체크리스트

### 매일 시작 시
- [ ] Git pull 받아 최신 코드 동기화
- [ ] 로컬 모니터링 스택 실행 (`docker-compose -f docker-compose.monitoring.yml up -d`)
- [ ] 애플리케이션 실행 확인

### 매일 종료 시
- [ ] 작성한 코드 Git commit
- [ ] 메트릭 노출 확인 (`/actuator/prometheus`)
- [ ] Grafana 대시보드 저장 확인
- [ ] 진행 상황 문서화

---

## 🧪 통합 테스트 계획

### Phase 1 완료 후 (Day 6 종료 시)

#### 1. 메트릭 수집 검증
```bash
# 모든 Phase 1 메트릭 노출 확인
curl http://localhost:8080/actuator/prometheus | grep -E "pipeline|rag|llm|conversation|memory" > metrics.txt
wc -l metrics.txt  # 예상: 100+ 라인
```

#### 2. 대시보드 기능 테스트
```bash
# Grafana 접속
http://localhost:3000/admin/monitoring/grafana/

# 테스트 시나리오:
1. 파이프라인 병목 대시보드 열기
   - Stage Gap이 가장 긴 전환 확인
   - Backpressure Queue 크기 확인

2. RAG 품질 대시보드 열기
   - 유사도 점수 분포 확인
   - MongoDB Table에서 검색된 메모리 내용 확인
   - 추출된 메모리 목록 확인

3. Application Logs 대시보드 열기
   - 에러 로그 필터링 확인
   - Top 에러 메시지 확인
```

#### 3. 부하 테스트
```bash
# Apache Bench로 부하 발생
ab -n 100 -c 10 http://localhost:8080/api/dialogue

# 대시보드에서 실시간 반영 확인:
- Pipeline 처리량 증가
- Stage Gap 변화
- TTS Queue 크기 증가
- 메모리 사용량 변화
```

---

## 📈 성공 지표 (Phase 1 완료 기준)

### 메트릭 수집
- [ ] Prometheus에 50개 이상의 신규 메트릭 노출
- [ ] 모든 메트릭에 적절한 Tags 설정
- [ ] Histogram Buckets 적절하게 설정

### 대시보드
- [ ] 총 3개 신규 대시보드 생성
  - [ ] Pipeline 병목 분석 (12 패널)
  - [ ] RAG 품질 모니터링 (15 패널)
  - [ ] Application Logs (8 패널)
- [ ] MongoDB 데이터소스 연동 성공
- [ ] 모든 패널 데이터 정상 로딩

### 사용자 요구사항 충족
- [ ] ✅ Stage 간 데이터 흐름 속도 시각화
- [ ] ✅ 파이프라인 병목 지점 탐지 가능
- [ ] ✅ Stage별 메모리 사용량 추정 제공
- [ ] ✅ RAG 검색 결과 내용 확인 가능
- [ ] ✅ 메모리 추출 결과 내용 확인 가능

---

## 🚨 리스크 및 대응

### 리스크 1: MongoDB 쿼리 성능 저하
**증상**: Grafana 대시보드 로딩 느림 (> 5초)

**대응**:
1. MongoDB 인덱스 추가
   ```javascript
   db.performance_metrics.createIndex({ startedAt: -1 })
   db.extracted_memories.createIndex({ createdAt: -1 })
   ```
2. 쿼리 시간 범위 제한 (최근 1시간)
3. Pagination 적용

### 리스크 2: Prometheus Cardinality 폭발
**증상**: Prometheus 메모리 사용량 급증

**대응**:
1. Tag 개수 제한 (Stage 이름, Status만 사용)
2. Dynamic Tag 제거 (User ID 등)
3. Metric Relabeling 설정

### 리스크 3: Micrometer 메트릭 누락
**증상**: `/actuator/prometheus`에 메트릭 미노출

**대응**:
1. `@Configuration` 어노테이션 확인
2. MeterRegistry Bean 주입 확인
3. Application 재시작
4. Actuator 의존성 확인

---

## 📚 구현 가이드라인

### 코드 품질
- [ ] 모든 메트릭에 명확한 Description 작성
- [ ] Tag 이름은 Snake Case 사용
- [ ] 메트릭 이름은 Prometheus 네이밍 컨벤션 준수
- [ ] 단위 테스트 작성 (메트릭 등록 검증)

### 대시보드 품질
- [ ] 모든 패널에 Description 작성 (기존 대시보드처럼)
- [ ] 임계값 색상 일관성 유지
- [ ] Refresh 간격 15초 설정
- [ ] Time Range 기본값: 1시간

### 문서화
- [ ] MONITORING_IMPLEMENTATION.md 최신화
- [ ] 메트릭 목록 정리
- [ ] 대시보드 스크린샷 추가
- [ ] Troubleshooting 가이드 작성

---

## 🎯 다음 단계 (Phase 1 완료 후)

1. **데이터 수집 기간 (1-2일)**
   - 실제 운영 환경에서 메트릭 수집
   - 이상치 확인 및 튜닝

2. **Alert 규칙 추가**
   - Pipeline Gap > 2초 알림
   - RAG 유사도 < 0.5 알림
   - 메모리 추출 실패율 > 10% 알림

3. **대시보드 최적화**
   - 쿼리 성능 개선
   - 불필요한 패널 제거
   - 사용자 피드백 반영

4. **Phase 2 준비**
   - LLM 비용 계산 로직 검증
   - TTS 비용 정책 확인
   - 사용자별 비용 추적 방안 검토
