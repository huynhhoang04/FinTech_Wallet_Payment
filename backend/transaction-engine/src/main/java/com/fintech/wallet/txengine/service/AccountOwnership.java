package com.fintech.wallet.txengine.service;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.txengine.grpc.LedgerClient;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ownership lives with identity, in the ledger's {@code accounts.owner_user_id}; this service can
 * only ask. Answers 404 rather than 403 so a probe cannot tell an account that exists from one that
 * does not (docs 07 SEC-04).
 */
@Service
public class AccountOwnership {

	private final LedgerClient ledger;

	public AccountOwnership(LedgerClient ledger) {
		this.ledger = ledger;
	}

	public Set<UUID> accountIdsOf(String userId) {
		return ledger.accountsByOwner(UUID.fromString(userId)).stream()
				.map(LedgerClient.AccountInfo::accountId)
				.collect(Collectors.toSet());
	}

	public void requireOwned(String userId, UUID accountId) {
		if (!accountIdsOf(userId).contains(accountId)) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản");
		}
	}

	/** A transaction is visible if either of its sides belongs to the caller. */
	public void requireOwnsEitherSide(String userId, UUID sourceAccountId, UUID targetAccountId) {
		Set<UUID> owned = accountIdsOf(userId);
		boolean visible = (sourceAccountId != null && owned.contains(sourceAccountId))
				|| (targetAccountId != null && owned.contains(targetAccountId));
		if (!visible) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch");
		}
	}
}
