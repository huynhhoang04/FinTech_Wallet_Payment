package com.fintech.wallet.notification.repository;

import com.fintech.wallet.notification.domain.DeadLetterEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {

	List<DeadLetterEvent> findTop50ByOrderByCreatedAtDesc();
}
