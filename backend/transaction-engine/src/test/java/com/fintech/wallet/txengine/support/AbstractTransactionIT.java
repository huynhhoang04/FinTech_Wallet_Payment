package com.fintech.wallet.txengine.support;

import com.fintech.wallet.txengine.grpc.LedgerClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractTransactionIT {

	/** Singleton containers: Spring caches one context across the test classes of this module,
	 *  so the datasource url must keep pointing at a container that outlives a single class.
	 *  A per-class @Container would stop the database under the cached context. */
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

	static {
		POSTGRES.start();
		REDIS.start();
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
	}

	/** The ledger is stubbed: these tests are about the engine, not its own gRPC server. */
	@MockitoBean
	protected LedgerClient ledger;

	@Autowired
	protected com.fintech.wallet.txengine.repository.TransactionRepository transactions;

	@Autowired
	protected com.fintech.wallet.txengine.repository.OutboxEventRepository outbox;

	@Autowired
	protected com.fintech.wallet.txengine.repository.FraudAlertRepository fraudAlerts;

	@Autowired
	protected com.fintech.wallet.txengine.service.TransactionService transactionService;

	/** One schema and one set of static account ids back every test, and the fraud rules
	 *  count over the whole table - so each test must start from an empty state. */
	@BeforeEach
	void cleanTransactionState() {
		fraudAlerts.deleteAll();
		outbox.deleteAll();
		transactions.deleteAll();
	}
}
