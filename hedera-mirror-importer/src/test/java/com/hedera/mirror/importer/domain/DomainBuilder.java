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
import static com.hedera.mirror.importer.domain.EntityTypeEnum.CONTRACT;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.FILE;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DomainBuilder {

    private final AtomicLong id = new AtomicLong(0L);
    private final Instant now = Instant.now();
    private final SecureRandom random = new SecureRandom();
    private final Map<Class<?>, CrudRepository<?, ?>> repositories;

    // Intended for use by unit tests that don't need persistence
    public DomainBuilder() {
        this(Collections.emptyList());
    }

    @Autowired
    public DomainBuilder(Collection<CrudRepository<?, ?>> crudRepositories) {
        repositories = new HashMap<>();

        for (CrudRepository<?, ?> crudRepository : crudRepositories) {
            try {
                Class<?> domainClass = GenericTypeResolver.resolveTypeArguments(crudRepository.getClass(),
                        CrudRepository.class)[0];
                repositories.put(domainClass, crudRepository);
            } catch (Exception e) {
                log.warn("Unable to map repository {} to domain class", crudRepository.getClass());
            }
        }
    }

    public DomainPersister<Contract, Contract.ContractBuilder> contract() {
        long id = id();
        byte[] key = Key.newBuilder().setEd25519(ByteString.copyFrom(bytes(32))).build().toByteArray();
        long timestamp = timestamp();

        Contract.ContractBuilder builder = Contract.builder()
                .autoRenewPeriod(1800L)
                .createdTimestamp(timestamp)
                .deleted(false)
                .expirationTimestamp(timestamp + 30_000_000L)
                .fileId(entityId(FILE))
                .id(id)
                .key(key)
                .memo("test")
                .obtainerId(entityId(CONTRACT))
                .parentId(entityId(CONTRACT))
                .proxyAccountId(entityId(ACCOUNT))
                .num(id)
                .realm(0L)
                .shard(0L)
                .timestampRange(Range.atLeast(timestamp))
                .type(CONTRACT.getId());

        return new DomainPersister<>(getRepository(Contract.class), builder, builder::build);
    }

    public DomainPersister<ContractLog, ContractLog.ContractLogBuilder> contractLog() {
        ContractLog.ContractLogBuilder builder = ContractLog.builder()
                .bloom(bytes(256))
                .consensusTimestamp(timestamp())
                .contractId(entityId(CONTRACT))
                .data(bytes(128))
                .index((int) id())
                .payerAccountId(entityId(ACCOUNT))
                .topic0("0x00")
                .topic1("0x01")
                .topic2("0x02")
                .topic3("0x03");
        return new DomainPersister<>(getRepository(ContractLog.class), builder, builder::build);
    }

    public DomainPersister<ContractResult, ContractResult.ContractResultBuilder> contractResult() {
        ContractResult.ContractResultBuilder builder = ContractResult.builder()
                .amount(1000L)
                .bloom(bytes(256))
                .callResult(bytes(512))
                .consensusTimestamp(timestamp())
                .contractId(entityId(CONTRACT))
                .createdContractIds(List.of(entityId(CONTRACT).getId()))
                .errorMessage("")
                .functionParameters(bytes(64))
                .functionResult(bytes(128))
                .gasLimit(200L)
                .gasUsed(100L)
                .payerAccountId(entityId(ACCOUNT));
        return new DomainPersister<>(getRepository(ContractResult.class), builder, builder::build);
    }

    public DomainPersister<Entity, Entity.EntityBuilder> entity() {
        long id = id();
        byte[] key = Key.newBuilder().setEd25519(ByteString.copyFrom(bytes(32))).build().toByteArray();
        long timestamp = timestamp();

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
                .type(ACCOUNT.getId());

        return new DomainPersister<>(getRepository(Entity.class), builder, builder::build);
    }

    // Helper methods
    private byte[] bytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private EntityId entityId(EntityTypeEnum type) {
        return EntityId.of(0L, 0L, id(), type);
    }

    private <T> CrudRepository<T, ?> getRepository(Class<T> domainClass) {
        return (CrudRepository<T, ?>) repositories.get(domainClass);
    }

    private long id() {
        return id.incrementAndGet();
    }

    private long timestamp() {
        return Utility.convertToNanosMax(now.getEpochSecond(), now.getNano()) + id();
    }
}
