package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer;

/**
 * TTS 엔드포인트 가중치 계산기
 *
 * <p>
 * 두 가지 요소를 곱하여 최종 가중치를 산출합니다.
 *
 * <h3>W_credit (크레딧 가중치)</h3>
 * <ul>
 * <li>credits &le; 10 → 0.0 (안전 가드)</li>
 * <li>credits &gt; 10 → (maxCredits - credits) / (maxCredits - 10) — 선형, 크레딧이 적을수록 높음</li>
 * </ul>
 *
 * <h3>W_traffic (트래픽 가중치)</h3>
 * <ul>
 * <li>W_traffic = 1.0 - (activeRequests / maxConcurrentRequests)^4</li>
 * <li>낮은 구간(0~67%)에서 완만하게 감소, 최대값 근처(83~100%)에서 급격히 감소</li>
 * </ul>
 *
 * <h3>최종 가중치</h3>
 * W_total = W_credit × W_traffic
 */
public class EndpointWeightCalculator {

	private static final double CREDIT_THRESHOLD = 10.0;
	private static final int TRAFFIC_EXPONENT = 4;

	/**
	 * 엔드포인트의 최종 가중치를 계산합니다.
	 *
	 * @param endpoint
	 *            가중치를 계산할 엔드포인트
	 * @param maxCreditsInPool
	 *            풀 내 모든 HEALTHY 엔드포인트 중 최대 크레딧값 (정규화 기준)
	 * @return 0.0 이상의 가중치 (0.0이면 선택 불가)
	 */
	public double calculate(TtsEndpoint endpoint, double maxCreditsInPool) {
		double wCredit = calculateCreditWeight(endpoint.getCredits(), maxCreditsInPool);
		if (wCredit <= 0.0) {
			return 0.0;
		}

		double wTraffic = calculateTrafficWeight(
			endpoint.getActiveRequests(),
			endpoint.getMaxConcurrentRequests());

		return wCredit * wTraffic;
	}

	/**
	 * 크레딧 가중치 계산 (선형)
	 *
	 * <p>
	 * credits &le; 10 이면 0을 반환합니다 (TtsCreditMonitor의 비활성화 처리에 대한 이중 안전 가드).
	 */
	private double calculateCreditWeight(double credits, double maxCreditsInPool) {
		if (credits <= CREDIT_THRESHOLD) {
			return 0.0;
		}

		double denominator = maxCreditsInPool - CREDIT_THRESHOLD;
		if (denominator <= 0.0) {
			// 모든 엔드포인트가 동일 크레딧 → 균등 취급
			return 1.0;
		}

		double w = (maxCreditsInPool - credits) / denominator;
		return Math.max(0.0, Math.min(1.0, w));
	}

	/**
	 * 트래픽 가중치 계산 (지수 감소)
	 *
	 * <p>
	 * W_traffic = 1.0 - r^4 (r = activeRequests / maxConcurrentRequests)
	 *
	 * <p>
	 * 예시 (max=30):
	 * <ul>
	 * <li>active=10 → 0.988 (완만)</li>
	 * <li>active=20 → 0.802 (완만)</li>
	 * <li>active=25 → 0.518 (감소 시작)</li>
	 * <li>active=28 → 0.271 (급감)</li>
	 * <li>active=30 → 0.000 (차단)</li>
	 * </ul>
	 */
	private double calculateTrafficWeight(int activeRequests, int maxConcurrentRequests) {
		if (maxConcurrentRequests <= 0) {
			return 0.0;
		}

		double r = (double) activeRequests / maxConcurrentRequests;
		return Math.max(0.0, 1.0 - Math.pow(r, TRAFFIC_EXPONENT));
	}
}
