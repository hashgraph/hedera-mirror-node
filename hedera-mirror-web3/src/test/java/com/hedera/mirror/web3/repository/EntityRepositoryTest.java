/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.web3.Web3IntegrationTest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRepositoryTest extends Web3IntegrationTest {

    private final EntityRepository entityRepository;

    @Test
    void findByIdAndDeletedIsFalseSuccessfulCall() {
        Entity entity = domainBuilder.entity().persist();
        assertThat(entityRepository.findByIdAndDeletedIsFalse(entity.getId()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByIdAndDeletedIsFalseFailCall() {
        Entity entity = domainBuilder.entity().persist();
        long id = entity.getId();
        assertThat(entityRepository.findByIdAndDeletedIsFalse(++id)).isEmpty();
    }

    @Test
    void findByIdAndDeletedTrueCall() {
        Entity entity = domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        assertThat(entityRepository.findByIdAndDeletedIsFalse(entity.getId())).isEmpty();
    }

    @Test
    void findByEvmAddressAndDeletedIsFalseSuccessfulCall() {
        Entity entity = domainBuilder.entity().persist();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalse(entity.getEvmAddress()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressAndDeletedIsFalseFailCall() {
        domainBuilder.entity().persist();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalse(new byte[32]))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndDeletedTrueCall() {
        Entity entity = domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalse(entity.getEvmAddress()))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndCreatedTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findByEvmAddressAndTimestampAndDeletedIsFalse(
                        entity.getEvmAddress(), entity.getCreatedTimestamp()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressAndCreatedTimestampAndDeletedTrueCall() {
        Entity entity = domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findByEvmAddressAndTimestampAndDeletedIsFalse(
                        entity.getEvmAddress(), entity.getCreatedTimestamp()))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressAndCreatedTimestampAndDeletedIsFalseCall() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();

        Optional<Entity> queryResult = entityRepository.findByEvmAddressAndTimestampAndDeletedIsFalse(
                entityHistory.getEvmAddress(), entityHistory.getCreatedTimestamp());
        assertEntityFields(entityHistory, queryResult);
    }

    @Test
    void findHistoricalEntityByEvmAddressAndCreatedTimestampAndDeletedTrueCall() {
        EntityHistory entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findByEvmAddressAndTimestampAndDeletedIsFalse(
                        entityHistory.getEvmAddress(), entityHistory.getCreatedTimestamp()))
                .isEmpty();
    }

    @Test
    void findByIdAndCreatedTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findByIdAndTimestampAndDeletedIsFalse(entity.getId(), entity.getCreatedTimestamp()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByIdAndCreatedTimestampAndDeletedTrueCall() {
        Entity entity = domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findByIdAndTimestampAndDeletedIsFalse(entity.getId(), entity.getId()))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByIdAndCreatedTimestampAndDeletedIsFalseCall() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();

        Optional<Entity> queryResult = entityRepository.findByIdAndTimestampAndDeletedIsFalse(
                entityHistory.getId(), entityHistory.getCreatedTimestamp());
        assertEntityFields(entityHistory, queryResult);
    }

    @Test
    void findHistoricalEntityByIdAndCreatedTimestampAndDeletedTrueCall() {
        EntityHistory entityHistory = domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findByIdAndTimestampAndDeletedIsFalse(entityHistory.getId(), entityHistory.getCreatedTimestamp()))
                        .isEmpty();
    }

    private void assertEntityFields(EntityHistory entityHistory, Optional<Entity> queryResult) {
        assertThat(queryResult)
                .get()
                .returns(entityHistory.getAlias(), Entity::getAlias)
                .returns(entityHistory.getAutoRenewAccountId(), Entity::getAutoRenewAccountId)
                .returns(entityHistory.getAutoRenewPeriod(), Entity::getAutoRenewPeriod)
                .returns(entityHistory.getBalance(), Entity::getBalance)
                .returns(entityHistory.getBalanceTimestamp(), Entity::getBalanceTimestamp)
                .returns(entityHistory.getCreatedTimestamp(), Entity::getCreatedTimestamp)
                .returns(entityHistory.getDeclineReward(), Entity::getDeclineReward)
                .returns(entityHistory.getDeleted(), Entity::getDeleted)
                .returns(entityHistory.getEthereumNonce(), Entity::getEthereumNonce)
                .returns(entityHistory.getEvmAddress(), Entity::getEvmAddress)
                .returns(entityHistory.getExpirationTimestamp(), Entity::getExpirationTimestamp)
                .returns(entityHistory.getId(), Entity::getId)
                .returns(entityHistory.getKey(), Entity::getKey)
                .returns(entityHistory.getMaxAutomaticTokenAssociations(), Entity::getMaxAutomaticTokenAssociations)
                .returns(entityHistory.getMemo(), Entity::getMemo)
                .returns(entityHistory.getNum(), Entity::getNum)
                .returns(entityHistory.getObtainerId(), Entity::getObtainerId)
                .returns(entityHistory.getPermanentRemoval(), Entity::getPermanentRemoval)
                .returns(entityHistory.getProxyAccountId(), Entity::getProxyAccountId)
                .returns(entityHistory.getPublicKey(), Entity::getPublicKey)
                .returns(entityHistory.getRealm(), Entity::getRealm)
                .returns(entityHistory.getReceiverSigRequired(), Entity::getReceiverSigRequired)
                .returns(entityHistory.getShard(), Entity::getShard)
                .returns(entityHistory.getStakedAccountId(), Entity::getStakedAccountId)
                .returns(entityHistory.getStakedNodeId(), Entity::getStakedNodeId)
                .returns(entityHistory.getStakePeriodStart(), Entity::getStakePeriodStart)
                .returns(entityHistory.getSubmitKey(), Entity::getSubmitKey)
                .returns(entityHistory.getTimestampRange(), Entity::getTimestampRange)
                .returns(entityHistory.getType(), Entity::getType);
    }
}
