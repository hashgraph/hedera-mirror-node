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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.Web3IntegrationTest;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRepositoryTest extends Web3IntegrationTest {

    private final Instant now = Instant.now();

    private Long entityId = 78L;

    private static final byte[] KEY = new byte[33];

    private final EntityRepository entityRepository;

    @Test
    void findByIdAndDeletedIsFalseSuccessfulCall() {
        Entity entity = entity();
        entityRepository.save(entity);
        final var result = entityRepository.findByIdAndDeletedIsFalse(entityId);
        assertThat(result).get().isEqualTo(entity);
    }

    @Test
    void findByAliasAndDeletedIsFalseSuccessfulCall() {
        Entity entity = entity();
        entityRepository.save(entity);
        final var result = entityRepository.findByAliasAndDeletedIsFalse(KEY);
        assertThat(result).get().isEqualTo(entity);
    }

    private Entity entity() {
        Entity entity = new Entity();
        entity.setId(++entityId);
        entity.setDeleted(false);
        entity.setAlias(KEY);
        entity.setMemo("entity");
        entity.setNum(entityId);
        entity.setDeclineReward(false);
        entity.setRealm(0L);
        entity.setShard(0L);
        entity.setTimestampRange(Range.atLeast(DomainUtils.convertToNanosMax(now.getEpochSecond(), now.getNano()) + entityId));
        entity.setType(ACCOUNT);
        return entity;
    }
}
