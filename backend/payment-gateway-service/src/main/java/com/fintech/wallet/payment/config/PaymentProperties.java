package com.fintech.wallet.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(Map<String, String> providerSecrets, boolean publishEnabled, long publishIntervalMs) {
}
