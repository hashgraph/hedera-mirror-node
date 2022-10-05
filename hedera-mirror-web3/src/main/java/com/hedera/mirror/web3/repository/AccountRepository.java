package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.entity.Entity;

import com.hedera.mirror.common.domain.entity.EntityType;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import java.math.BigInteger;
import java.util.Optional;

public interface AccountRepository extends CrudRepository<Entity, BigInteger> {
    @Query(value = "select treasury_account_id > 0 from token where treasury_account_id = ?1", nativeQuery = true)
    Optional<Boolean> isTokenTreasury(final BigInteger accountNum);

    @Query(value = "select balance from entity where realm = ?1 and shard = ?2 and num = ?3 and deleted <> true",
            nativeQuery = true)
    Optional<BigInteger> getBalance(final BigInteger realmNum, final BigInteger shardNum,
                                    final BigInteger accountNum);

    @Query(value = "select account_id > 0 from nft where account_id = ?1"
            , nativeQuery = true)
    Optional<Boolean> ownsNfts(final BigInteger accountNum);

    @Query(value = "select type from entity where realm = ?1 and shard = ?2 and num = ?3 and deleted <> true"
            , nativeQuery = true)
    Optional<EntityType> getType(final BigInteger realmNum, final BigInteger shardNum,
                                 final BigInteger accountNum);

    @Query(value = "select alias from entity where realm = ?1 and shard = ?2 and num = ?3 and deleted <> true",
            nativeQuery = true)
    byte[] getAlias(final BigInteger realmNum, final BigInteger shardNum,
                    final BigInteger accountNum);
}
