package com.fintech.wallet.notification.repository;

import com.fintech.wallet.notification.domain.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

	Page<NotificationLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	boolean existsByTemplateCodeAndRelatedTransactionId(String templateCode, UUID relatedTransactionId);
}
