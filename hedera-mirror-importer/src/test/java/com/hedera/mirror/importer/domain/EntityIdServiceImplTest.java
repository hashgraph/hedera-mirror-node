package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.EntityRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityIdServiceImplTest extends IntegrationTest {

    // in the form 'shard.realm.num'
    private static final byte[] PARSABLE_EVM_ADDRESS = new byte[] {
            0, 0, 0, 0, // shard
            0, 0, 0, 0, 0, 0, 0, 0, // realm
            0, 0, 0, 0, 0, 0, 0, 100, // num
    };

    private final EntityRepository entityRepository;
    private final EntityIdService entityIdService;

    @Test
    void cache() {
        Entity contract = domainBuilder.entity().customize(e -> e.alias(null).type(CONTRACT)).persist();
        ContractID contractId = getProtoContractId(contract);
        EntityId expected = contract.toEntityId();

        // db query and cache put
        assertThat(entityIdService.lookup(contractId)).isEqualTo(expected);

        // mark it as deleted
        contract.setDeleted(true);
        entityRepository.save(contract);

        // cache hit
        assertThat(entityIdService.lookup(contractId)).isEqualTo(expected);

        entityRepository.deleteById(contract.getId());
        assertThat(entityIdService.lookup(contractId)).isEqualTo(expected);

        // cache miss
        reset();
        var contractIdProto = getProtoContractId(contract);
        assertThat(entityIdService.lookup(contractIdProto)).isNull();
    }

    @Test
    void lookupAccountNum() {
        AccountID accountId = AccountID.newBuilder().setAccountNum(100).build();
        assertThat(entityIdService.lookup(accountId)).isEqualTo(EntityId.of(100, ACCOUNT));
    }

    @Test
    void lookupAccountAlias() {
        Entity account = domainBuilder.entity().persist();
        assertThat(entityIdService.lookup(getProtoAccountId(account))).isEqualTo(account.toEntityId());
    }

    @Test
    void lookupAccountAliasNoMatch() {
        Entity account = domainBuilder.entity().get();
        var accountId = getProtoContractId(account);
        assertThat(entityIdService.lookup(accountId)).isNull();
    }

    @Test
    void lookupAccountAliasDeleted() {
        Entity account = domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        var accountId = getProtoContractId(account);
        assertThat(entityIdService.lookup(accountId)).isNull();
    }

    @Test
    void lookupAccountDefaultInstance() {
        assertThat(entityIdService.lookup(AccountID.getDefaultInstance())).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupAccountNull() {
        assertThat(entityIdService.lookup((AccountID) null)).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupAccountNotFound() {
        AccountID accountId = AccountID.newBuilder().setRealmNum(1).build();
        assertThat(entityIdService.lookup(accountId)).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupAccounts() {
        AccountID nullAccountId = null;
        AccountID accountId = AccountID.newBuilder().setAccountNum(100).build();
        AccountID accountIdInvalid = AccountID.newBuilder().setRealmNum(1).build();
        Entity accountDeleted = domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        EntityId entityId = entityIdService.lookup(nullAccountId,
                AccountID.getDefaultInstance(),
                getProtoAccountId(accountDeleted),
                accountIdInvalid,
                accountId);

        assertThat(entityId).isEqualTo(EntityId.of(accountId));
    }

    @Test
    void lookupAccountsReturnsFirst() {
        AccountID accountId1 = AccountID.newBuilder().setAccountNum(100).build();
        AccountID accountId2 = AccountID.newBuilder().setAccountNum(101).build();
        EntityId entityId = entityIdService.lookup(accountId1, accountId2);
        assertThat(entityId).isEqualTo(EntityId.of(accountId1));
    }

    @Test
    void lookupAccountsEntityIdNotFound() {
        Entity account1 = domainBuilder.entity().get();
        Entity account2 = domainBuilder.entity().customize(e -> e.alias(null)).get();
        var accountId1 = getProtoContractId(account1);
        var accountId2 = getProtoContractId(account2);
        assertThat(entityIdService.lookup(accountId1, accountId2)).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupContractNum() {
        ContractID contractId = ContractID.newBuilder().setContractNum(100).build();
        assertThat(entityIdService.lookup(contractId)).isEqualTo(EntityId.of(100, CONTRACT));
    }

    @Test
    void lookupContractEvmAddress() {
        Entity contract = domainBuilder.entity().customize(e -> e.alias(null).type(CONTRACT)).persist();
        assertThat(entityIdService.lookup(getProtoContractId(contract))).isEqualTo(contract.toEntityId());
    }

    @Test
    void lookupContractEvmAddressSpecific() {
        var contractId = ContractID.newBuilder().setEvmAddress(DomainUtils.fromBytes(PARSABLE_EVM_ADDRESS)).build();
        assertThat(entityIdService.lookup(contractId)).isEqualTo(EntityId.of(100, CONTRACT));
    }

    @Test
    void lookupContractEvmAddressNoMatch() {
        Entity contract = domainBuilder.entity().customize(e -> e.alias(null).type(CONTRACT)).get();
        var contractId = getProtoContractId(contract);
        assertThat(entityIdService.lookup(contractId)).isNull();
    }

    @Test
    void lookupContractEvmAddressDeleted() {
        Entity contract = domainBuilder.entity().customize(e -> e.alias(null).deleted(true).type(CONTRACT)).persist();
        var contractId = getProtoContractId(contract);
        assertThat(entityIdService.lookup(contractId)).isNull();
    }

    @Test
    void lookupContractEvmAddressShardRealmMismatch() {
        ContractID contractId = ContractID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setEvmAddress(DomainUtils.fromBytes(PARSABLE_EVM_ADDRESS))
                .build();
        assertThat(entityIdService.lookup(contractId)).isNull();
    }

    @Test
    void lookupContractDefaultInstance() {
        assertThat(entityIdService.lookup(ContractID.getDefaultInstance())).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupContractNull() {
        assertThat(entityIdService.lookup((ContractID) null)).isEqualTo(EntityId.EMPTY);
    }

    @Test
    void lookupContracts() {
        ContractID nullContractId = null;
        ContractID contractId = ContractID.newBuilder().setContractNum(100).build();
        ContractID contractIdInvalid = ContractID.newBuilder().setRealmNum(1).build();
        Entity contractDeleted = domainBuilder.entity().customize(e -> e.alias(null).deleted(true).type(CONTRACT))
                .persist();

        EntityId entityId = entityIdService.lookup(nullContractId,
                ContractID.getDefaultInstance(),
                getProtoContractId(contractDeleted),
                contractIdInvalid,
                contractId);

        assertThat(entityId).isEqualTo(EntityId.of(contractId));
    }

    @Test
    void lookupContractsReturnsFirst() {
        ContractID contractId1 = ContractID.newBuilder().setContractNum(100).build();
        ContractID contractId2 = ContractID.newBuilder().setContractNum(101).build();
        EntityId entityId = entityIdService.lookup(contractId1, contractId2);
        assertThat(entityId).isEqualTo(EntityId.of(contractId1));
    }

    @Test
    void lookupContractsEntityIdNotFound() {
        Entity contract1 = domainBuilder.entity().customize(e -> e.alias(null).type(CONTRACT)).get();
        Entity contract2 = domainBuilder.entity().customize(c -> c.alias(null).evmAddress(null).type(CONTRACT)).get();
        var contractId1 = getProtoContractId(contract1);
        var contractId2 = getProtoContractId(contract2);
        assertThat(entityIdService.lookup(contractId1, contractId2)).isEqualTo(contract2.toEntityId());
    }

    @ParameterizedTest
    @CsvSource(value = {"false", ","})
    void storeAccount(Boolean deleted) {
        Entity account = domainBuilder.entity().customize(e -> e.deleted(deleted)).get();
        entityIdService.notify(account);
        assertThat(entityIdService.lookup(getProtoAccountId(account))).isEqualTo(account.toEntityId());
    }

    @Test
    void storeAccountDeleted() {
        Entity account = domainBuilder.entity().customize(e -> e.deleted(true)).get();
        entityIdService.notify(account);
        var accountId = getProtoContractId(account);
        assertThat(entityIdService.lookup(accountId)).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {"false", ","})
    void storeContract(Boolean deleted) {
        Entity contract = domainBuilder.entity().customize(c -> c.alias(null).deleted(deleted).type(CONTRACT)).get();
        entityIdService.notify(contract);
        assertThat(entityIdService.lookup(getProtoContractId(contract))).isEqualTo(contract.toEntityId());
    }

    @Test
    void storeContractDeleted() {
        Entity contract = domainBuilder.entity().customize(c -> c.alias(null).deleted(true).type(CONTRACT)).get();
        entityIdService.notify(contract);
        var contractId = getProtoContractId(contract);
        assertThat(entityIdService.lookup(contractId)).isNull();
    }

    @Test
    void storeNull() {
        assertDoesNotThrow(() -> entityIdService.notify(null));
    }

    @Test
    void lookupAccountWithEvmAddress() {
        AccountID accountId = AccountID.newBuilder()
                .setAlias(DomainUtils.fromBytes(PARSABLE_EVM_ADDRESS))
                .build();
        assertThat(entityIdService.lookup(accountId)).isEqualTo(EntityId.of(100, CONTRACT));
    }

    private AccountID getProtoAccountId(Entity account) {
        var accountId = AccountID.newBuilder()
                .setShardNum(account.getShard())
                .setRealmNum(account.getRealm());
        if (account.getAlias() == null) {
            accountId.setAccountNum(account.getNum());
        } else {
            accountId.setAlias(DomainUtils.fromBytes(account.getAlias()));
        }
        return accountId.build();
    }

    private ContractID getProtoContractId(Entity contract) {
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
