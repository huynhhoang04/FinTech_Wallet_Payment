package com.fintech.wallet.txengine.repository;

import com.fintech.wallet.txengine.domain.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {

	List<FraudAlert> findByTransactionId(UUID transactionId);
}
