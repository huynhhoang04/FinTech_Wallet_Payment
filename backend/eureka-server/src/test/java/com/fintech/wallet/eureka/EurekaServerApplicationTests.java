package com.fintech.wallet.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The base configuration has no secret defaults on purpose (docs 07 SEC-06), so the context test
 * runs with the local profile, which is where the dev credentials live.
 */
@SpringBootTest
@ActiveProfiles("local")
class EurekaServerApplicationTests {

	@Test
	void contextLoads() {
	}
}
