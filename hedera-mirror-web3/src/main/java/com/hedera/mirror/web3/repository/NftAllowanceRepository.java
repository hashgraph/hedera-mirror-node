package com.hedera.mirror.web3.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.entity.AbstractNftAllowance.Id;
import com.hedera.mirror.common.domain.entity.NftAllowance;

public interface NftAllowanceRepository extends CrudRepository<NftAllowance, Id> {

    @Query(value = "select appoved_for_all from nft_allowance where token_id = ?1, owner = ?2, spender = ?3",
            nativeQuery = true)
    boolean isSpenderAnOperator(final Long tokenId, final Long ownerId, final Long spenderId);
}
