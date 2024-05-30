/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.rest.model.Key.TypeEnum;
import com.hedera.mirror.rest.model.Topic;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TopicMapperTest {

    private CommonMapper commonMapper;
    private DomainBuilder domainBuilder;
    private TopicMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        mapper = new TopicMapperImpl(commonMapper);
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        var key = domainBuilder.key(KeyCase.ED25519);
        var topic =
                domainBuilder.entity().customize(e -> e.key(key).submitKey(key)).get();

        assertThat(mapper.map(topic))
                .returns(TypeEnum.ED25519, t -> t.getAdminKey().getType())
                .returns(
                        Hex.encodeHexString(topic.getKey()),
                        t -> "1220" + t.getAdminKey().getKey())
                .returns(EntityId.of(topic.getAutoRenewAccountId()).toString(), Topic::getAutoRenewAccount)
                .returns(topic.getAutoRenewPeriod(), Topic::getAutoRenewPeriod)
                .returns(commonMapper.mapTimestamp(topic.getCreatedTimestamp()), Topic::getCreatedTimestamp)
                .returns(topic.getDeleted(), Topic::getDeleted)
                .returns(topic.getMemo(), Topic::getMemo)
                .returns(TypeEnum.ED25519, t -> t.getSubmitKey().getType())
                .returns(
                        Hex.encodeHexString(topic.getKey()),
                        t -> "1220" + t.getSubmitKey().getKey())
                .returns(commonMapper.mapTimestamp(topic.getTimestampLower()), t -> t.getTimestamp()
                        .getFrom())
                .returns(null, t -> t.getTimestamp().getTo())
                .returns(topic.toEntityId().toString(), Topic::getTopicId);
    }

    @Test
    void mapNulls() {
        var topic = new Entity();
        assertThat(mapper.map(topic))
                .returns(null, Topic::getAdminKey)
                .returns(null, Topic::getAutoRenewAccount)
                .returns(null, Topic::getAutoRenewPeriod)
                .returns(null, Topic::getCreatedTimestamp)
                .returns(null, Topic::getDeleted)
                .returns(null, Topic::getMemo)
                .returns(null, Topic::getSubmitKey)
                .returns(null, Topic::getTimestamp)
                .returns(null, Topic::getTopicId);
    }
}
