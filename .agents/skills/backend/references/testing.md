# Backend Testing with Testcontainers

Reference for the `backend` skill. Integration tests are how the project proves its financial
guarantees; they are not optional.

## 1. Two suites, two plugins

| Suite | File pattern | Plugin | Binds to |
|---|---|---|---|
| Unit | `*Test.java` | surefire | Plain JUnit - runs in `test` phase |
| Integration | `*IT.java` | failsafe | Testcontainers - runs in `integration-test`/`verify` |

`./mvnw verify` runs both. CI runs `verify`; never `-DskipTests` in CI.

## 2. Shared container setup

```java
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

    @Container
    static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0")).withReuse(true);

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
```

- Contribute only the containers a service needs (the ledger does not need Kafka).
- Share containers across a class with a `static` field so the JVM starts them once.
- Docker must be available where the tests run - it is in CI runners by default.

## 3. Required money-path tests

### Ledger (`account-ledger-service`)
```java
@Test void transfer_isBalanced() {          // Debit + Credit == 0
@Test void snapshot_matches_entrySum() {    // account_balances == SUM(ledger_entries)
@Test void insufficientFunds_rejects() {    // 422, no entries written
@Test void sameTransactionId_doesNotDoublePost() {  // replay is a no-op
@Test void concurrentOppositeTransfers_doNotDeadlock() {
    // two threads: A->B and B->A at the same time; both must complete
}
```

### Transaction engine
```java
@Test void sameIdempotencyKey_returnsStoredResult_andPostsOnce() {
@Test void sameKeyDifferentPayload_conflicts() {          // 409 IDEMPOTENCY_CONFLICT
@Test void ledgerDown_returns503_andMovesNoMoney() {      // circuit breaker
@Test void velocityRule_blocksAfterThreshold() {          // 422 FRAUD_BLOCKED
@Test void outboxRow_writtenInSameTransaction_asStateChange() {
```

### Payment gateway
```java
@Test void webhookReplay_doesNotCreditTwice() {           // duplicate: true
@Test void invalidProviderSignature_isRejected() {
```

## 4. Concurrency test shape

```java
@Test
void concurrentOppositeTransfers_doNotDeadlock() throws Exception {
    UUID a = seedAccount(1_000_000);
    UUID b = seedAccount(1_000_000);

    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    Callable<Void> ab = () -> { start.await(); ledger.postTransfer(UUID.randomUUID(), a, b, 100_000); return null; };
    Callable<Void> ba = () -> { start.await(); ledger.postTransfer(UUID.randomUUID(), b, a, 100_000); return null; };

    var f1 = pool.submit(ab);
    var f2 = pool.submit(ba);
    start.countDown();

    assertThatCode(() -> { f1.get(10, SECONDS); f2.get(10, SECONDS); }).doesNotThrowAnyException();
    assertThat(totalOfAllEntries()).isZero();
}
```

Failure mode to watch: `PSQLException: deadlock detected`. It means the lock ordering rule in
`references/ledger-invariants.md` was broken.

## 5. Assert the invariants, not just the response

An endpoint returning `201` proves nothing about the money. Every integration test on the money
path should also assert at the database level:

```java
assertThat(entryRepo.sumByTransactionId(txId)).isZero();
assertThat(balanceRepo.balanceOf(accountId))
    .isEqualTo(entryRepo.sumByAccountId(accountId));
```

## 6. Test data

- Use `@Transactional` rollback for read-only tests; do **not** wrap concurrency tests in
  `@Transactional` (the transaction hides the race).
- Seed system accounts in `@BeforeEach` through the real service, not raw SQL, so the seed path
  is itself exercised.
- Never depend on test execution order.

## 7. Commands

```bash
./mvnw -pl account-ledger-service -am verify                    # everything
./mvnw -pl account-ledger-service verify -Dit.test=LedgerPostingIT
./mvnw -pl account-ledger-service surefire:test                 # unit only
./mvnw -pl . -am verify -Dtestcontainers.reuse.enable=true      # reuse containers locally
```
