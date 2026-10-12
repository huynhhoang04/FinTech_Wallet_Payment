package com.fintech.wallet.notification.service;

import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Resolves the wallet accounts a user owns. Notification rows are keyed by the account the balance
 * event belongs to, while the API caller is identified by the identity user id, so the two have to
 * be bridged through the ledger (the source of truth for ownership).
 */
@Component
public class LedgerAccountsClient {

	private final RestClient client;
	private final String internalToken;

	public LedgerAccountsClient(
			@Qualifier("ledgerRestClientBuilder") RestClient.Builder builder,
			@Value("${app.ledger.base-url}") String baseUrl,
			@Value("${app.internal.token}") String internalToken) {
		this.client = builder.baseUrl(baseUrl).build();
		this.internalToken = internalToken;
	}

	public List<UUID> accountIdsOf(String ownerUserId) {
		try {
			OwnedAccounts owned = client.get()
					.uri("/internal/accounts/owner/{ownerUserId}", ownerUserId)
					.header(HeaderNames.INTERNAL_TOKEN, internalToken)
					.retrieve()
					.body(OwnedAccounts.class);
			return owned == null || owned.accountIds() == null ? List.of() : owned.accountIds();
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, "Ledger không phản hồi: " + e);
		}
	}

	record OwnedAccounts(String ownerUserId, List<UUID> accountIds) {
	}
}
