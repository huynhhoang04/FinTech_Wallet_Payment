package com.fintech.wallet.ledger.service;

import com.fintech.wallet.ledger.domain.LedgerEntry;

import java.util.List;

public record StatementView(List<LedgerEntry> entries, long openingBalance, long closingBalance) {
}
