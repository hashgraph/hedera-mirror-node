package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.google.common.collect.Range;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DomainBuilder {

    private final AtomicLong entityId = new AtomicLong(0L);
    private final EntityRepository entityRepository;
    private final Instant now = Instant.now();

    public DomainPersister<Entity, Entity.EntityBuilder> entity() {
        long timestamp = Utility.convertToNanosMax(now.getEpochSecond(), now.getNano());
        Entity.EntityBuilder builder = Entity.builder()
                .createdTimestamp(timestamp)
                .deleted(false)
                .id(entityId.incrementAndGet())
                .memo("test")
                .num(0L)
                .realm(0L)
                .shard(0L)
                .timestampRange(Range.atLeast(timestamp + 1))
                .type(EntityTypeEnum.ACCOUNT.getId());

        return new DomainPersister<>(entityRepository, builder, builder::build);
    }
}
