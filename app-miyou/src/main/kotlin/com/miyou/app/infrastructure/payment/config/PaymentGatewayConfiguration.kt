package com.miyou.app.infrastructure.payment.config

import com.miyou.app.infrastructure.payment.port.PaymentGatewayPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PaymentGatewayConfiguration {
    @Bean
    fun paymentGatewayMap(gateways: List<PaymentGatewayPort>): Map<String, PaymentGatewayPort> =
        gateways.associateBy(PaymentGatewayPort::getProviderName)
}
