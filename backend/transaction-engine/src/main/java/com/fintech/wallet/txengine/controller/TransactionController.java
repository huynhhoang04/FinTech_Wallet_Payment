package com.fintech.wallet.txengine.controller;

import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.dto.PageResponse;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.txengine.domain.FraudAlert;
import com.fintech.wallet.txengine.domain.Transaction;
import com.fintech.wallet.txengine.dto.DepositRequest;
import com.fintech.wallet.txengine.dto.TransactionResponse;
import com.fintech.wallet.txengine.dto.TransferRequest;
import com.fintech.wallet.txengine.dto.WithdrawRequest;
import com.fintech.wallet.txengine.grpc.LedgerClient;
import com.fintech.wallet.txengine.repository.FraudAlertRepository;
import com.fintech.wallet.txengine.repository.TransactionRepository;
import com.fintech.wallet.txengine.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

	private static final int MAX_PAGE_SIZE = 100;

	private final TransactionService transactions;
	private final TransactionRepository repository;
	private final FraudAlertRepository fraudAlerts;
	private final LedgerClient ledger;

	public TransactionController(TransactionService transactions, TransactionRepository repository,
			FraudAlertRepository fraudAlerts, LedgerClient ledger) {
		this.transactions = transactions;
		this.repository = repository;
		this.fraudAlerts = fraudAlerts;
		this.ledger = ledger;
	}

	@PostMapping("/transfer")
	public ResponseEntity<TransactionResponse> transfer(
			@RequestHeader(value = HeaderNames.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
			@Valid @RequestBody TransferRequest request) {
		return created(transactions.transfer(requireIdempotencyKey(idempotencyKey), request));
	}

	@PostMapping("/deposit")
	public ResponseEntity<TransactionResponse> deposit(
			@RequestHeader(value = HeaderNames.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
			@Valid @RequestBody DepositRequest request) {
		return created(transactions.deposit(requireIdempotencyKey(idempotencyKey), request));
	}

	@PostMapping("/withdraw")
	public ResponseEntity<TransactionResponse> withdraw(
			@RequestHeader(value = HeaderNames.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
			@Valid @RequestBody WithdrawRequest request) {
		return created(transactions.withdraw(requireIdempotencyKey(idempotencyKey), request));
	}

	@GetMapping("/{transactionId}")
	public TransactionResponse get(@PathVariable UUID transactionId) {
		return TransactionResponse.of(requireTransaction(transactionId));
	}

	@GetMapping("/{transactionId}/ledger-entries")
	public LedgerClient.LedgerEntriesInfo ledgerEntries(@PathVariable UUID transactionId) {
		Transaction transaction = requireTransaction(transactionId);
		if (transaction.getLedgerTransactionId() == null) {
			return new LedgerClient.LedgerEntriesInfo(transactionId, List.of(), 0L);
		}
		return ledger.ledgerEntries(transaction.getLedgerTransactionId());
	}

	@GetMapping("/{transactionId}/fraud-alerts")
	public List<FraudAlertView> fraudAlerts(@PathVariable UUID transactionId) {
		return fraudAlerts.findByTransactionId(transactionId).stream().map(FraudAlertView::of).toList();
	}

	@GetMapping
	public PageResponse<TransactionResponse> list(@RequestParam UUID accountId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Page<Transaction> found = repository.findBySourceAccountIdOrTargetAccountId(accountId, accountId,
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
		return PageResponse.of(found.getContent().stream().map(TransactionResponse::of).toList(),
				found.getNumber(), found.getSize(), found.getTotalElements());
	}

	private Transaction requireTransaction(UUID transactionId) {
		return repository.findById(transactionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch"));
	}

	private static String requireIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu " + HeaderNames.IDEMPOTENCY_KEY);
		}
		return idempotencyKey;
	}

	private static ResponseEntity<TransactionResponse> created(TransactionService.Result result) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(
				result.replayed() ? HttpStatus.OK : HttpStatus.CREATED);
		if (result.replayed()) {
			builder.header(HeaderNames.IDEMPOTENT_REPLAY, "true");
		}
		return builder.body(result.response());
	}

	public record FraudAlertView(String ruleCode, String severity, String decision, String details) {

		static FraudAlertView of(FraudAlert alert) {
			return new FraudAlertView(alert.getRuleCode(), alert.getSeverity().name(), alert.getDecision().name(),
					alert.getDetails());
		}
	}
}
