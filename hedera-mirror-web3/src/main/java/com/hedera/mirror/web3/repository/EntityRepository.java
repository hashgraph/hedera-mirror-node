package com.hedera.mirror.web3.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.mirror.common.domain.entity.Entity;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityRepository extends CrudRepository<Entity, Long> {

    //FUTURE WORK Coffeine cache to be added
    @Query(value = "select ethereum_nonce from entity where evm_address = ?1 and deleted <> true", nativeQuery = true)
    Optional<Long> findAccountNonceByAddress(byte[] evmAddress);

    @Query(value = "select * from entity where evm_address = ?1 and deleted <> true", nativeQuery = true)
    Optional<Entity> findAccountByAddress(byte[] evmAddress);

    @Query(value = "select * from entity where alias = ?1 and deleted <> true", nativeQuery = true)
    Optional<Entity> findAccountByAlias(byte[] alias);

    @Query(value = "select * from entity where public_key = ?1 and deleted <> true", nativeQuery = true)
    Optional<Entity> findAccountByPublicKey(String publicKey);

    @Query(value = "select id from entity where evm_address = ?1 and deleted <> true", nativeQuery = true)
    Optional<Long> findAccountIdByAddress(byte[] evmAddress);

    @Query(value = "select evm_address from entity where num = ?1 and deleted <> true", nativeQuery = true)
    Optional<byte[]> findAddressByNum(long num);

    @Query(value = "select balance from entity where evm_address = ?1 and type in ('ACCOUNT', 'CONTRACT') and deleted is not true", nativeQuery = true)
    Optional<Long> findAccountBalanceByAddress(byte[] evmAddress);
}
