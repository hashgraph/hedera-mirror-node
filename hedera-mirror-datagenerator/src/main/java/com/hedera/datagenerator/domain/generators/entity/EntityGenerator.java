package com.hedera.datagenerator.domain.generators.entity;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.importer.domain.EntityTypeEnum.FILE;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOPIC;

import com.google.common.base.Stopwatch;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;

import com.hedera.datagenerator.common.EntityManager;
import com.hedera.datagenerator.domain.writer.DomainWriter;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

/**
 * Generates entities. Only types 'account' and 'files' are supported currently.
 */
@Log4j2
@Named
public class EntityGenerator {
    private final byte[] fixedKey;

    public EntityGenerator() {
        try {
            fixedKey = Key.newBuilder()
                    .setEd25519(ByteString.copyFrom(
                            Hex.decodeHex("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f")))
                    .build()
                    .toByteArray();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void generateAndWriteEntityType(EntityManager.EntitySet entitySet, DomainWriter domainWriter,
                                            EntityTypeEnum type) {
        int count = 0;
        int deleted = 0;
        for (Long entityId = entitySet.getStartEntityNum(); entityId < entitySet.getEntityNum(); entityId++) {
            Entities entity = createEntity(entityId, type);
            if (entitySet.getDeleted().contains(entityId)) {
                entity.setDeleted(true);
                deleted++;
            }
            count++;
            domainWriter.onEntity(entity);
        }
        log.info("Wrote {} entities, containing {} marked deleted", count, deleted);
    }

    /**
     * Generate entities and write them out to the DomainWriter.
     */
    public void generateAndWriteEntities(EntityManager entityManager, DomainWriter domainWriter) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        generateAndWriteEntityType(entityManager.getAccounts(), domainWriter, ACCOUNT);
        generateAndWriteEntityType(entityManager.getFiles(), domainWriter, FILE);
        generateAndWriteEntityType(entityManager.getTopics(), domainWriter, TOPIC);
        log.info("Generated all entities in {}", stopwatch);
    }

    private Entities createEntity(Long id, EntityTypeEnum type) {
        Entities entity = EntityId.of(0L, 0L, id, type).toEntity();
        entity.setKey(fixedKey);
        return entity;
    }
}
