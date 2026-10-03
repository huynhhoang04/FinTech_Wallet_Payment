package com.fintech.wallet.audit.controller;

import com.fintech.wallet.audit.domain.AuditLog;
import com.fintech.wallet.audit.domain.ReconciliationReport;
import com.fintech.wallet.audit.repository.AuditLogRepository;
import com.fintech.wallet.audit.repository.ReconciliationReportRepository;
import com.fintech.wallet.audit.service.AuditChainService;
import com.fintech.wallet.audit.service.ReconciliationService;
import com.fintech.wallet.common.dto.PageResponse;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
public class AuditController {

	private static final int MAX_PAGE_SIZE = 100;

	private final AuditLogRepository logs;
	private final ReconciliationReportRepository reports;
	private final AuditChainService chain;
	private final ReconciliationService reconciliation;

	public AuditController(AuditLogRepository logs, ReconciliationReportRepository reports, AuditChainService chain,
			ReconciliationService reconciliation) {
		this.logs = logs;
		this.reports = reports;
		this.chain = chain;
		this.reconciliation = reconciliation;
	}

	@GetMapping("/api/v1/audit/logs")
	public PageResponse<AuditLogView> logs(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Page<AuditLog> found = logs.findAllByOrderByRecordedAtDesc(
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
		return PageResponse.of(found.getContent().stream().map(AuditLogView::of).toList(),
				found.getNumber(), found.getSize(), found.getTotalElements());
	}

	@GetMapping("/api/v1/audit/logs/{id}")
	public AuditLogView log(@PathVariable UUID id) {
		return AuditLogView.of(logs.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy bản ghi audit")));
	}

	@GetMapping("/api/v1/audit/verify-chain")
	public AuditChainService.ChainVerification verifyChain() {
		return chain.verifyChain();
	}

	@GetMapping("/api/v1/reconciliation/reports")
	public List<ReportView> reports() {
		return reports.findTop30ByOrderByReportDateDesc().stream().map(ReportView::of).toList();
	}

	@GetMapping("/api/v1/reconciliation/reports/{reportDate}")
	public ReportView report(
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
		return ReportView.of(reports.findByReportDate(reportDate)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Chưa có báo cáo đối chiếu")));
	}

	@PostMapping("/api/v1/reconciliation/run")
	public ReportView run(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ReportView.of(reconciliation.run(date == null ? LocalDate.now(ZoneOffset.UTC) : date));
	}

	public record AuditLogView(UUID id, UUID eventId, String eventType, UUID aggregateId, String actor,
			String prevHash, String hash, String occurredAt, String recordedAt) {

		static AuditLogView of(AuditLog entry) {
			return new AuditLogView(entry.getId(), entry.getEventId(), entry.getEventType(), entry.getAggregateId(),
					entry.getActor(), entry.getPrevHash(), entry.getHash(), String.valueOf(entry.getOccurredAt()),
					String.valueOf(entry.getRecordedAt()));
		}
	}

	public record ReportView(String reportDate, long entryCount, long totalDebit, long totalCredit, long difference,
			String status) {

		static ReportView of(ReconciliationReport report) {
			return new ReportView(report.getReportDate().toString(), report.getEntryCount(), report.getTotalDebit(),
					report.getTotalCredit(), report.getDifference(), report.getStatus().name());
		}
	}
}
