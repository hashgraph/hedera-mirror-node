package com.hedera.mirror.graphql.mapper;

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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.graphql.viewmodel.Account;
import com.hedera.mirror.graphql.viewmodel.EntityId;
import com.hedera.mirror.graphql.viewmodel.EntityType;
import com.hedera.mirror.graphql.viewmodel.TimestampRange;

class AccountMapperTest {

    private AccountMapper accountMapper;
    private DomainBuilder domainBuilder;

    @BeforeEach
    void setup() {
        accountMapper = Mappers.getMapper(AccountMapper.class);
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        var entity = domainBuilder.entity().get();
        var entityId = new EntityId();
        entityId.setShard(entity.getShard());
        entityId.setRealm(entity.getRealm());
        entityId.setNum(entity.getNum());
        var timestampRange = new TimestampRange();
        timestampRange.setFrom(Instant.ofEpochSecond(0L, entity.getTimestampLower()));

        assertThat(accountMapper.map(entity))
                .returns(Base64.getEncoder().encodeToString(entity.getAlias()), Account::getAlias)
                .returns(Duration.ofSeconds(entity.getAutoRenewPeriod()), Account::getAutoRenewPeriod)
                .returns(entity.getBalance(), Account::getBalance)
                .returns(Instant.ofEpochSecond(0L, entity.getCreatedTimestamp()), Account::getCreatedTimestamp)
                .returns(entity.getDeclineReward(), Account::getDeclineReward)
                .returns(entity.getDeleted(), Account::getDeleted)
                .returns(entityId, Account::getEntityId)
                .returns(Instant.ofEpochSecond(0L, entity.getExpirationTimestamp()), Account::getExpirationTimestamp)
                .returns(Map.of(), Account::getKey)
                .returns(entity.getMaxAutomaticTokenAssociations(), Account::getMaxAutomaticTokenAssociations)
                .returns(entity.getMemo(), Account::getMemo)
                .returns(entity.getEthereumNonce(), Account::getNonce)
                .returns(entity.getReceiverSigRequired(), Account::getReceiverSigRequired)
                .returns(Instant.ofEpochSecond(0L, entity.getStakePeriodStart()), Account::getStakePeriodStart)
                .returns(timestampRange, Account::getTimestamp)
                .returns(EntityType.valueOf(entity.getType().toString()), Account::getType);
    }
}
