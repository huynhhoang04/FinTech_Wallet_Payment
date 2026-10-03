package com.fintech.wallet.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Batch is in the tech stack (docs 02 section 8.2) but the schedule cut list (docs 06
 * section 3) allows a scheduled job instead, which is what the reconciliation uses here.
 */
@EnableDiscoveryClient
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.fintech.wallet")
public class AuditApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditApplication.class, args);
	}
}
