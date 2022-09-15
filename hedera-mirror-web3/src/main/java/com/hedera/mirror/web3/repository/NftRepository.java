package com.hedera.mirror.web3.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.token.Nft;

public interface NftRepository extends CrudRepository<Nft, Long> {

    @Query(value = "select serial_number from nft order by serial_number desc limit 1", nativeQuery=true)
    Optional<Long> findLatestSerialNumber();
}
