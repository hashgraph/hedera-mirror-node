package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.token.Token;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenRepository extends CrudRepository<Token, Long> {

    @Query(value = "select * from token where token_id = (select id from entity where evm_address = ?1 "
            + "and deleted <> true)", nativeQuery=true)
    Optional<Token> findByAddress(final byte[] address);

}
