package com.hedera.mirror.importer.parser.record.entity.staking;

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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcOperations;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.repository.EntityStakeRepository;

@ExtendWith(MockitoExtension.class)
class EntityStakeCalculatorImplTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private EntityStakeRepository entityStakeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private JdbcOperations jdbcOperations;

    @Mock
    RecordStreamFileListener recordStreamFileListener;

    private EntityStakeCalculatorImpl entityStakeCalculator;

    @BeforeEach
    void setup() {
        entityStakeCalculator = new EntityStakeCalculatorImpl(entityStakeRepository, eventPublisher, jdbcOperations,
                recordStreamFileListener);
    }

    @Test
    void onNodeStakes() {
        var inOrder = Mockito.inOrder(recordStreamFileListener, jdbcOperations, eventPublisher);
        entityStakeCalculator.onNodeStakes(List.of(domainBuilder.nodeStake().get()));
        inOrder.verify(recordStreamFileListener).onFlush();
        inOrder.verify(jdbcOperations).update("refresh materialized view entity_state_start");
        inOrder.verify(eventPublisher).publishEvent(ArgumentMatchers.isA(NodeStakeUpdateEvent.class));
    }

    @Test
    void onNodeStakesWhenNodeStakesIsEmpty() {
        entityStakeCalculator.onNodeStakes(Collections.emptyList());
        verifyNoInteractions(recordStreamFileListener, jdbcOperations, eventPublisher);
    }

    @Test
    void update() {
        when(entityStakeRepository.updateEntityStake()).thenReturn(1);
        entityStakeCalculator.update();
        verify(entityStakeRepository).updateEntityStake();
    }
}
