package com.hedera.mirror.web3.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;

public interface NftRepository extends CrudRepository<Nft, NftId> {

    @Query(value = "select spender from nft where token_id = ?1, serialNumber = ?2",
            nativeQuery = true)
    Optional<EntityId> findSpender(final Long tokenId, final Long serialNo);

    @Query(value = "select account_id from nft where token_id = ?1, serialNumber = ?2",
            nativeQuery = true)
    Optional<EntityId> findOwner(final Long tokenId, final Long serialNo);

    @Query(value = "select metadata from nft where token_id = ?1, serialNumber = ?2",
            nativeQuery = true)
    Optional<byte[]> findMetadata(final Long tokenId, final Long serialNo);
}
