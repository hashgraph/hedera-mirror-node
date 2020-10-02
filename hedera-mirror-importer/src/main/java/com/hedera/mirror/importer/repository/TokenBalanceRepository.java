package com.hedera.mirror.importer.repository;

import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.domain.TokenBalance;

public interface TokenBalanceRepository extends CrudRepository<TokenBalance, TokenBalance.Id> {
}
