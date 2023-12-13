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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
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
    void findByEvmAddressAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCall() {
        Entity entity = domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeAndDeletedTrueCall() {
        EntityHistory entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entityHistory.getEvmAddress(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalse() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();
        Entity entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findEntityByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalse() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();

        // Both entity and entity history will be queried in union but entity record is the latest valid
        Entity entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalse() {
        Entity entity = domainBuilder.entity().persist();
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        EntityHistory entityHistory = domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entityHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findByIdAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower() + 1))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByIdAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByIdAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampRangeAndDeletedTrueCall() {
        Entity entity = domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower()))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();

        // persist older entity in entity history
        domainBuilder
                .entityHistory()
                .customize(e -> e.timestampRange(
                        Range.closedOpen(entityHistory.getTimestampLower() - 10, entityHistory.getTimestampLower())))
                .persist();

        // verify that we get the latest valid entity from entity history
        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getTimestampLower() + 1))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCall() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeAndDeletedTrueCall() {
        EntityHistory entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getCreatedTimestamp()))
                .isEmpty();
    }
}
