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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.exception.AliasNotFoundException;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.repository.ContractRepository;

class EntityIdServiceImplTest extends IntegrationTest {

    // in the form 'shard.realm.num'
    private static final byte[] PARSABLE_EVM_ADDRESS = new byte[] {
            0, 0, 0, 0, // shard
            0, 0, 0, 0, 0, 0, 0, 0, // realm
            0, 0, 0, 0, 0, 0, 0, 100, // num
    };

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

        contractRepository.deleteById(contract.getId());
        assertThat(entityIdService.lookup(contractId)).isEqualTo(expected);

        // cache miss
        reset();
        assertThrows(AliasNotFoundException.class, () -> entityIdService.lookup(getProtoContractId(contract)));
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
        assertThrows(AliasNotFoundException.class, () -> entityIdService.lookup(getProtoAccountId(account)));
    }

    @Test
    void lookupAccountAliasDeleted() {
        Entity account = domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        assertThrows(AliasNotFoundException.class, () -> entityIdService.lookup(getProtoAccountId(account)));
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
    void lookupAccountThrows() {
        AccountID accountId = AccountID.newBuilder().setRealmNum(1).build();
        assertThrows(InvalidDatasetException.class, () -> entityIdService.lookup(accountId));
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
    void lookupAccountsAliasNotFoundActionError() {
        Entity account1 = domainBuilder.entity().get();
        Entity account2 = domainBuilder.entity().customize(e -> e.alias(null)).get();
        assertThrows(AliasNotFoundException.class, () -> entityIdService.lookup(AliasNotFoundAction.ERROR,
                getProtoAccountId(account1), getProtoAccountId(account2)));
    }

    @Test
    void lookupContractNum() {
        ContractID contractId = ContractID.newBuilder().setContractNum(100).build();
        assertThat(entityIdService.lookup(contractId)).isEqualTo(EntityId.of(100, CONTRACT));
    }

    @Test
    void lookupContractCreate2EvmAddress() {
        Contract contract = domainBuilder.contract().persist();
        assertThat(entityIdService.lookup(getProtoContractId(contract))).isEqualTo(contract.toEntityId());
    }

    @Test
    void lookupContractCreate2EvmAddressNoMatch() {
        Contract contract = domainBuilder.contract().get();
        assertThrows(AliasNotFoundException.class, () -> entityIdService.lookup(getProtoContractId(contract)));
    }

    @Test
    void lookupContractCreate2EvmAddressDeleted() {
        Contract contract = domainBuilder.contract().customize((b) -> b.deleted(true)).persist();
        assertThrows(AliasNotFoundException.class, () -> entityIdService.lookup(getProtoContractId(contract)));
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
        assertThrows(AliasNotFoundException.class, () -> entityIdService.lookup(contractId));
    }

    @Test
    void lookupContractThrows() {
        ContractID contractId = ContractID.newBuilder().setRealmNum(1).build();
        assertThrows(InvalidDatasetException.class, () -> entityIdService.lookup(contractId));
    }

    @Test
    void lookupContracts() {
        ContractID nullContractId = null;
        ContractID contractId = ContractID.newBuilder().setContractNum(100).build();
        ContractID contractIdInvalid = ContractID.newBuilder().setRealmNum(1).build();
        Contract contractDeleted = domainBuilder.contract().customize(e -> e.deleted(true)).persist();

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
    void lookupContractsAliasNotFoundActionError() {
        Contract contract1 = domainBuilder.contract().get();
        Contract contract2 = domainBuilder.contract().customize(c -> c.evmAddress(null)).get();
        assertThrows(AliasNotFoundException.class, () -> entityIdService.lookup(AliasNotFoundAction.ERROR,
                getProtoContractId(contract1), getProtoContractId(contract2)));
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
        assertThrows(AliasNotFoundException.class, () -> entityIdService.lookup(getProtoAccountId(account)));
    }

    @ParameterizedTest
    @CsvSource(value = {"false", ","})
    void storeContract(Boolean deleted) {
        Contract contract = domainBuilder.contract().customize(c -> c.deleted(deleted)).get();
        entityIdService.notify(contract);
        assertThat(entityIdService.lookup(getProtoContractId(contract))).isEqualTo(contract.toEntityId());
    }

    @Test
    void storeContractDeleted() {
        Contract contract = domainBuilder.contract().customize(c -> c.deleted(true)).get();
        entityIdService.notify(contract);
        assertThrows(AliasNotFoundException.class, () -> entityIdService.lookup(getProtoContractId(contract)));
    }

    @Test
    void storeNull() {
        assertDoesNotThrow(() -> entityIdService.notify(null));
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
