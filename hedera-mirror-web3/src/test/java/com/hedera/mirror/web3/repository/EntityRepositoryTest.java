package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.Web3IntegrationTest;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

public class EntityRepositoryTest extends Web3IntegrationTest {

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
        return entity;
    }
}
