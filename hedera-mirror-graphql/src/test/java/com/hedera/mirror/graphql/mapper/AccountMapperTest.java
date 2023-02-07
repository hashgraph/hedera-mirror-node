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

import com.google.protobuf.ByteString;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.graphql.viewmodel.Account;
import com.hedera.mirror.graphql.viewmodel.EntityId;
import com.hedera.mirror.graphql.viewmodel.EntityType;
import com.hedera.mirror.graphql.viewmodel.TimestampRange;

import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountMapperTest {

    private AccountMapper accountMapper;
    private DomainBuilder domainBuilder;

    @BeforeEach
    void setup() {
        accountMapper = new AccountMapperImpl(new CommonMapperImpl());
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        var bytes = ByteString.copyFrom(new byte[] {0, 1, 2});
        var key = Key.newBuilder().setECDSASecp256K1(bytes).build().toByteArray();
        var entity = domainBuilder.entity().customize(e -> e.key(key)).get();

        assertThat(accountMapper.map(entity))
                .returns(Hex.encodeHexString(entity.getAlias()), Account::getAlias)
                .returns(Duration.ofSeconds(entity.getAutoRenewPeriod()), Account::getAutoRenewPeriod)
                .returns(entity.getBalance(), Account::getBalance)
                .returns(Instant.ofEpochSecond(0L, entity.getCreatedTimestamp()), Account::getCreatedTimestamp)
                .returns(entity.getDeclineReward(), Account::getDeclineReward)
                .returns(entity.getDeleted(), Account::getDeleted)
                .returns(Instant.ofEpochSecond(0L, entity.getExpirationTimestamp()), Account::getExpirationTimestamp)
                .returns(Map.of("ECDSA_SECP256K1", "AAEC"), Account::getKey)
                .returns(entity.getMaxAutomaticTokenAssociations(), Account::getMaxAutomaticTokenAssociations)
                .returns(entity.getMemo(), Account::getMemo)
                .returns(entity.getEthereumNonce(), Account::getNonce)
                .returns(entity.getReceiverSigRequired(), Account::getReceiverSigRequired)
                .returns(Instant.ofEpochSecond(0L, entity.getStakePeriodStart()), Account::getStakePeriodStart)
                .returns(EntityType.valueOf(entity.getType().toString()), Account::getType)
                .satisfies(a -> assertThat(a.getEntityId())
                        .returns(entity.getShard(), EntityId::getShard)
                        .returns(entity.getRealm(), EntityId::getRealm)
                        .returns(entity.getNum(), EntityId::getNum))
                .satisfies(a -> assertThat(a.getTimestamp())
                        .returns(Instant.ofEpochSecond(0L, entity.getTimestampLower()), TimestampRange::getFrom)
                        .returns(null, TimestampRange::getTo));
    }

    @Test
    void mapNulls() {
        var entity = new Entity();

        assertThat(accountMapper.map(entity))
                .returns(null, Account::getAlias)
                .returns(null, Account::getAutoRenewPeriod)
                .returns(null, Account::getBalance)
                .returns(null, Account::getCreatedTimestamp)
                .returns(null, Account::getDeclineReward)
                .returns(null, Account::getDeleted)
                .returns(null, Account::getEntityId)
                .returns(null, Account::getExpirationTimestamp)
                .returns(null, Account::getId)
                .returns(null, Account::getKey)
                .returns(null, Account::getMaxAutomaticTokenAssociations)
                .returns(null, Account::getMemo)
                .returns(null, Account::getNonce)
                .returns(null, Account::getPendingReward)
                .returns(null, Account::getReceiverSigRequired)
                .returns(null, Account::getStakePeriodStart)
                .returns(null, Account::getTimestamp)
                .returns(null, Account::getType);
    }
}
