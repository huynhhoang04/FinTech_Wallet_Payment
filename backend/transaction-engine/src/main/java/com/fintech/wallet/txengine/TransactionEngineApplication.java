package com.fintech.wallet.txengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.fintech.wallet")
public class TransactionEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransactionEngineApplication.class, args);
	}
}
