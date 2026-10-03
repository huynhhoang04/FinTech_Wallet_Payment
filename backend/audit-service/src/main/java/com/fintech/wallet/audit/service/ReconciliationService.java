package com.fintech.wallet.audit.service;

import com.fintech.wallet.audit.domain.ReconciliationReport;
import com.fintech.wallet.audit.repository.ReconciliationReportRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reconciles the ledger through its internal API rather than by reading its schema - services never
 * read each other's database (docs 01 section 3). A non-zero difference is the most important alert
 * in the project, so it is both logged at ERROR and exposed as a metric (docs 02 section 8.1).
 */
@Slf4j
@Service
public class ReconciliationService {

	private final ReconciliationReportRepository reports;
	private final RestClient ledger;
	private final AtomicLong imbalance = new AtomicLong();
	private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();

	public ReconciliationService(ReconciliationReportRepository reports, MeterRegistry meterRegistry,
			RestClient.Builder restClientBuilder, @Value("${app.ledger.base-url}") String ledgerBaseUrl) {
		this.reports = reports;
		this.ledger = restClientBuilder.baseUrl(ledgerBaseUrl).build();
		Gauge.builder("ledger_imbalance_total", imbalance, AtomicLong::get)
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
					.retrieve()
					.body(LedgerIntegrity.class);
			LedgerSummary summary = ledger.get()
					.uri("/internal/ledger/summary?date={date}", reportDate)
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
		report.setDetails("{\"unbalancedTransactions\":" + (integrity == null ? -1 : integrity.unbalancedTransactions())
				+ ",\"ledgerValid\":" + (integrity != null && integrity.valid()) + "}");
		return reports.save(report);
	}

	public record LedgerIntegrity(boolean valid, long checkedTransactions, long unbalancedTransactions,
			long totalAmount) {
	}

	public record LedgerSummary(String date, long totalDebit, long totalCredit, long difference) {
	}
}
