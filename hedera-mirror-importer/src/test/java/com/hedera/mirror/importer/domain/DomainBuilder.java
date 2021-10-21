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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;

import com.google.common.collect.Range;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
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

    private final EntityRepository entityRepository;
    private final AtomicLong id = new AtomicLong(0L);
    private final Instant now = Instant.now();

    private EntityId entityId(EntityTypeEnum type) {
        return EntityId.of(0L, 0L, id(), type);
    }

    private long id() {
        return id.incrementAndGet();
    }

    public DomainPersister<Entity, Entity.EntityBuilder> entity() {
        long id = id();
        byte[] key = Key.newBuilder().setEd25519(ByteString.copyFrom(Longs.toByteArray(id))).build().toByteArray();
        long timestamp = Utility.convertToNanosMax(now.getEpochSecond(), now.getNano()) + id;

        Entity.EntityBuilder builder = Entity.builder()
                .autoRenewAccountId(entityId(ACCOUNT))
                .autoRenewPeriod(1800L)
                .createdTimestamp(timestamp)
                .deleted(false)
                .expirationTimestamp(timestamp + 30_000_000L)
                .id(id)
                .key(key)
                .maxAutomaticTokenAssociations(0)
                .memo("test")
                .proxyAccountId(entityId(ACCOUNT))
                .num(id)
                .realm(0L)
                .receiverSigRequired(false)
                .shard(0L)
                .submitKey(key)
                .timestampRange(Range.atLeast(timestamp))
                .type(ACCOUNT);

        return new DomainPersister<>(entityRepository, builder, builder::build);
    }
}
