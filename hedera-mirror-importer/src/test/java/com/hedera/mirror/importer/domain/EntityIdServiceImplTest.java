package com.hedera.mirror.importer.domain;

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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_MANAGER_ALIAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.ContractID;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.repository.ContractRepository;

class EntityIdServiceImplTest extends IntegrationTest {

    // in the form 'shard.realm.num'
    private static final byte[] PARSABLE_EVM_ADDRESS = new byte[] {
            0, 0, 0, 0, // shard
            0, 0, 0, 0, 0, 0, 0, 0, // realm
            0, 0, 0, 0, 0, 0, 0, 100, // num
    };

    @Resource(name = CACHE_MANAGER_ALIAS)
    private CacheManager cacheManager;

    @Resource
    private ContractRepository contractRepository;

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private EntityIdService entityIdService;

    @Test
    void cache() {
        Contract contract = domainBuilder.contract().persist();
        ContractID contractId = getProtoContractId(contract);
        EntityId expected = contract.toEntityId();

        // db query and cache put
        assertThat(entityIdService.lookup(contractId)).isEqualTo(expected);

        // mark it as deleted
        contract.setDeleted(true);
        contractRepository.save(contract);

        // cache hit
        assertThat(entityIdService.lookup(contractId)).isEqualTo(expected);

        // cache miss
        clearCache();
        assertThat(entityIdService.lookup(contractId)).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupContractNum() {
        ContractID contractId = ContractID.newBuilder().setContractNum(100).build();
        assertThat(entityIdService.lookup(contractId)).isEqualTo(EntityId.of(100, CONTRACT));
    }

    @Test
    void lookupCreate2EvmAddress() {
        Contract contract = domainBuilder.contract().persist();
        ContractID contractId = ContractID.newBuilder()
                .setShardNum(contract.getShard())
                .setRealmNum(contract.getRealm())
                .setEvmAddress(DomainUtils.fromBytes(contract.getEvmAddress()))
                .build();
        assertThat(entityIdService.lookup(contractId)).isEqualTo(contract.toEntityId());
    }

    @Test
    void lookupCreate2EvmAddressNoMatch() {
        Contract contract = domainBuilder.contract().get();
        assertThat(entityIdService.lookup(getProtoContractId(contract))).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupCreate2EvmAddressDeleted() {
        Contract contract = domainBuilder.contract().customize((b) -> b.deleted(true)).persist();
        assertThat(entityIdService.lookup(getProtoContractId(contract))).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupDefaultInstance() {
        assertThat(entityIdService.lookup(ContractID.getDefaultInstance())).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupNull() {
        assertThat(entityIdService.lookup(null)).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupParsableEvmAddress() {
        var contractId = ContractID.newBuilder().setEvmAddress(DomainUtils.fromBytes(PARSABLE_EVM_ADDRESS)).build();
        assertThat(entityIdService.lookup(contractId)).isEqualTo(EntityId.of(100, CONTRACT));
    }

    @Test
    void lookupParsableEvmAddressShardRealmMismatch() {
        ContractID contractId = ContractID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setEvmAddress(DomainUtils.fromBytes(PARSABLE_EVM_ADDRESS))
                .build();
        assertThat(entityIdService.lookup(contractId)).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupThrows() {
        ContractID contractId = ContractID.newBuilder().setRealmNum(1).build();
        assertThrows(InvalidDatasetException.class, () -> entityIdService.lookup(contractId));
    }

    @ParameterizedTest
    @CsvSource(value = {"false", ","})
    void storeContract(Boolean deleted) {
        Contract contract = domainBuilder.contract().customize(c -> c.deleted(deleted)).get();
        entityIdService.store(contract);
        assertThat(entityIdService.lookup(getProtoContractId(contract))).isEqualTo(contract.toEntityId());
    }

    @Test
    void storeContractDeleted() {
        Contract contract = domainBuilder.contract().customize(c -> c.deleted(true)).get();
        entityIdService.store(contract);
        assertThat(entityIdService.lookup(getProtoContractId(contract))).isEqualTo(EntityId.EMPTY);
    }

    private void clearCache() {
        cacheManager.getCacheNames().stream().map(cacheManager::getCache).forEach(Cache::clear);
    }

    private ContractID getProtoContractId(Contract contract) {
        var contractId = ContractID.newBuilder()
                .setShardNum(contract.getShard())
                .setRealmNum(contract.getRealm());
        if (contract.getEvmAddress() == null) {
            contractId.setContractNum(contract.getNum());
        } else {
            contractId.setEvmAddress(DomainUtils.fromBytes(contract.getEvmAddress()));
        }
        return contractId.build();
    }
}
