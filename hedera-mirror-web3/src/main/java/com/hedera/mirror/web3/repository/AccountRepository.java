package com.hedera.mirror.web3.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityType;

public interface AccountRepository extends CrudRepository<Entity, Long> {
    @Query(value = "select treasury_account_id > 0 from token where treasury_account_id = ?1", nativeQuery = true)
    Optional<Boolean> isTokenTreasury(final Long accountNum);

    @Query(value = "select balance from entity where realm = ?1 and shard = ?2 and num = ?3 and deleted <> true",
            nativeQuery = true)
    Optional<Long> getBalance(final Long realmNum, final Long shardNum,
                              final Long accountNum);

    @Query(value = "select account_id > 0 from nft where account_id = ?1"
            , nativeQuery = true)
    Optional<Boolean> ownsNfts(final Long accountNum);

    @Query(value = "select type from entity where realm = ?1 and shard = ?2 and num = ?3 and deleted <> true"
            , nativeQuery = true)
    Optional<EntityType> getType(final Long realmNum, final Long shardNum,
                                 final Long accountNum);

    @Query(value = "select alias from entity where realm = ?1 and shard = ?2 and num = ?3 and deleted <> true",
            nativeQuery = true)
    byte[] getAlias(final Long realmNum, final Long shardNum,
                    final Long accountNum);
}
