package com.hedera.mirror.web3.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.CustomFee.Id;

public interface CustomFeeRepository extends CrudRepository<CustomFee, Id> {

    @Query(value = "select * from custom_fee where token_id = ?1",
            nativeQuery = true)
    Optional<List<CustomFee>> findCustomFees(final Long tokenId);
}
