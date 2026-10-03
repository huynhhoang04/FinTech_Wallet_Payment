package com.fintech.wallet.audit.repository;

import com.fintech.wallet.audit.domain.ReconciliationReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, UUID> {

	Optional<ReconciliationReport> findByReportDate(LocalDate reportDate);

	List<ReconciliationReport> findTop30ByOrderByReportDateDesc();
}
