package com.study.webflux.rag.infrastructure.payment.config;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.study.webflux.rag.infrastructure.payment.port.PaymentGatewayPort;

/**
 * PG사 어댑터를 등록하고 providerName으로 조회 가능한 Map을 제공합니다.
 * 새 PG사 추가 시 {@link PaymentGatewayPort} 구현체를 빈으로 등록하면 자동으로 여기에 포함됩니다.
 */
@Configuration
public class PaymentGatewayConfiguration {

	@Bean
	public Map<String, PaymentGatewayPort> paymentGatewayMap(List<PaymentGatewayPort> gateways) {
		return gateways.stream()
			.collect(Collectors.toMap(PaymentGatewayPort::getProviderName, Function.identity()));
	}
}
