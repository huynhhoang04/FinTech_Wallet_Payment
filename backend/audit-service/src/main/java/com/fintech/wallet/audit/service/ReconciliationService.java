package com.fintech.wallet.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.audit.domain.ReconciliationReport;
import com.fintech.wallet.audit.repository.ReconciliationReportRepository;
import com.fintech.wallet.common.constant.HeaderNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reconciles the ledger through its internal API rather than by reading its schema - services never
 * read each other's database (docs 01 section 3). The call carries the internal token, because
 * {@code /internal/**} is no longer public (docs 07 SEC-01). A non-zero difference is the most
 * important alert in the project, so it is logged at ERROR and exposed as a metric.
 */
@Slf4j
@Service
public class ReconciliationService {

	private final ReconciliationReportRepository reports;
	private final RestClient ledger;
	private final ObjectMapper objectMapper;
	private final String internalToken;
	private final AtomicLong imbalance = new AtomicLong();
	private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();

	public ReconciliationService(ReconciliationReportRepository reports, MeterRegistry meterRegistry,
			@Qualifier("ledgerRestClientBuilder") RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			@Value("${app.ledger.base-url}") String ledgerBaseUrl,
			@Value("${app.internal.token}") String internalToken) {
		this.reports = reports;
		this.objectMapper = objectMapper;
		this.internalToken = internalToken;
		this.ledger = restClientBuilder.baseUrl(ledgerBaseUrl).build();
		// Named ledger_imbalance, not ledger_imbalance_total: Micrometer strips a trailing "_total"
		// when exporting to Prometheus (that suffix belongs to counters), so the alert rule and the
		// Grafana panel have to query the name it is actually exported under.
		Gauge.builder("ledger_imbalance", imbalance, AtomicLong::get)
				.description("Total of the whole ledger; must stay 0")
				.register(meterRegistry);
		Gauge.builder("reconciliation_last_success_timestamp_seconds", lastSuccessEpochSeconds, AtomicLong::get)
				.register(meterRegistry);
	}

	@Scheduled(cron = "${app.reconciliation.cron:0 5 0 * * *}")
	public void runScheduled() {
		run(LocalDate.now(ZoneOffset.UTC));
	}

	@Transactional
	public ReconciliationReport run(LocalDate reportDate) {
		try {
			LedgerIntegrity integrity = ledger.get()
					.uri("/internal/ledger/verify-chain")
					.header(HeaderNames.INTERNAL_TOKEN, internalToken)
					.retrieve()
					.body(LedgerIntegrity.class);
			LedgerSummary summary = ledger.get()
					.uri("/internal/ledger/summary?date={date}", reportDate)
					.header(HeaderNames.INTERNAL_TOKEN, internalToken)
					.retrieve()
					.body(LedgerSummary.class);

			long difference = integrity == null ? 0 : integrity.totalAmount();
			boolean balanced = difference == 0 && integrity != null && integrity.unbalancedTransactions() == 0;
			imbalance.set(difference);
			if (balanced) {
				lastSuccessEpochSeconds.set(Instant.now().getEpochSecond());
			} else {
				log.error("Ledger imbalance detected on {}: difference={} unbalancedTransactions={}", reportDate,
						difference, integrity == null ? -1 : integrity.unbalancedTransactions());
			}
			return save(reportDate, integrity, summary, difference,
					balanced ? ReconciliationReport.ReconStatus.BALANCED : ReconciliationReport.ReconStatus.IMBALANCED);
		} catch (Exception e) {
			log.error("Reconciliation for {} failed: {}", reportDate, e.toString());
			return save(reportDate, null, null, 0, ReconciliationReport.ReconStatus.FAILED);
		}
	}

	private ReconciliationReport save(LocalDate reportDate, LedgerIntegrity integrity, LedgerSummary summary,
			long difference, ReconciliationReport.ReconStatus status) {
		ReconciliationReport report = reports.findByReportDate(reportDate).orElseGet(ReconciliationReport::new);
		report.setReportDate(reportDate);
		report.setEntryCount(integrity == null ? 0 : integrity.checkedTransactions());
		report.setTotalDebit(summary == null ? 0L : summary.totalDebit());
		report.setTotalCredit(summary == null ? 0L : summary.totalCredit());
		report.setDifference(difference);
		report.setStatus(status);
		report.setDetails(details(integrity));
		return reports.save(report);
	}

	private String details(LedgerIntegrity integrity) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("unbalancedTransactions", integrity == null ? -1 : integrity.unbalancedTransactions());
		details.put("ledgerValid", integrity != null && integrity.valid());
		try {
			return objectMapper.writeValueAsString(details);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialise reconciliation details", e);
		}
	}

	public record LedgerIntegrity(boolean valid, long checkedTransactions, long unbalancedTransactions,
			long totalAmount) {
	}

	public record LedgerSummary(String date, long totalDebit, long totalCredit, long difference) {
	}
}
