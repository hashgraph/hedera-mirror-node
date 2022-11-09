package com.hedera.mirror.web3.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityType;

public interface EntityAccessRepository extends CrudRepository<Entity, Long> {
    @Query(value = "select balance from entity where num = ?1 and deleted <> true",
            nativeQuery = true)
    Optional<Long> getBalance(final Long accountNum);

    @Query(value = "select type from entity where num = ?1 and deleted <> true"
            , nativeQuery = true)
    Optional<EntityType> getType(final Long accountNum);

    @Query(value = "select runtime_bytecode from contract where id = ?1",
            nativeQuery = true)
    Optional<byte[]> fetchContractCode(final Long accountNum);

    @Query(value = "select value_written from contract_state_change where contract_id = ?1 and slot =?2",
            nativeQuery = true)
    Optional<byte[]> getStorage(final Long accountNum, final byte[] key);

    @Query(value = "select alias from entity where num = ?1 and deleted <> true",
            nativeQuery = true)
    Optional<byte[]> getAlias(final Long accountNum);
}
