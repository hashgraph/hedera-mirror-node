package com.hedera.mirror.web3.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;

public interface TokenAllowanceRepository extends CrudRepository<TokenAllowance, AbstractTokenAllowance.Id> {

    @Query(value = "select amount from token_allowance where token_id = ?1, owner = ?2, spender = ?3",
            nativeQuery = true)
    Optional<Long> findAllowance(final Long tokenId, final Long ownerId, final Long spenderId);
}
