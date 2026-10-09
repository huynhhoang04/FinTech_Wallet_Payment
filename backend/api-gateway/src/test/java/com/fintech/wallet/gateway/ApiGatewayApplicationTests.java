package com.fintech.wallet.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs with the local profile: the base configuration deliberately carries no secret defaults, so
 * an environment without them must fail fast (docs 07 SEC-06).
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@ActiveProfiles("local")
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}
}
