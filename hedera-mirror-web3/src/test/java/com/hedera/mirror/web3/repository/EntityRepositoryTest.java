package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.Web3IntegrationTest;

import java.time.Instant;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

class EntityRepositoryTest extends Web3IntegrationTest {

    private final Instant now = Instant.now();

    private Long entityId = 78L;

    private static final byte[] KEY = new byte[33];

    @Resource
    private EntityRepository entityRepository;

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
