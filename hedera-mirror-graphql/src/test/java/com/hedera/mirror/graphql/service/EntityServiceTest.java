package com.hedera.mirror.graphql.service;

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
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.graphql.repository.EntityRepository;

@ExtendWith(MockitoExtension.class)
class EntityServiceTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private EntityRepository entityRepository;

    @InjectMocks
    private EntityServiceImpl entityService;

    @Test
    void getByIdAndTypeMissing() {
        var entity = domainBuilder.entity().get();
        assertThat(entityService.getByIdAndType(entity.toEntityId(), entity.getType())).isEmpty();
    }

    @Test
    void getByIdAndTypeMismatch() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByIdAndType(entity.toEntityId(), EntityType.CONTRACT)).isEmpty();
    }

    @Test
    void getByIdAndTypeFound() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByIdAndType(entity.toEntityId(), entity.getType())).get().isEqualTo(entity);
    }
}
