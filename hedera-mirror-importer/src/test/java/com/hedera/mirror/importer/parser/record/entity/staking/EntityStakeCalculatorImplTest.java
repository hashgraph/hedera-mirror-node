/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity.staking;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.EntityStakeRepository;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityStakeCalculatorImplTest {

    private EntityProperties entityProperties;

    @Mock
    private EntityRepository entityRepository;

    @Mock(strictness = LENIENT)
    private EntityStakeRepository entityStakeRepository;

    private EntityStakeCalculatorImpl entityStakeCalculator;

    @BeforeEach
    void setup() {
        entityProperties = new EntityProperties();
        entityStakeCalculator =
                new EntityStakeCalculatorImpl(entityProperties, entityRepository, entityStakeRepository);
        when(entityStakeRepository.updated()).thenReturn(false);
    }

    @Test
    void calculate() {
        var inorder = inOrder(entityRepository, entityStakeRepository);
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated();
        inorder.verify(entityRepository).refreshEntityStateStart();
        inorder.verify(entityStakeRepository).updateEntityStake();
        inorder.verifyNoMoreInteractions();
    }

    @Test
    void calculateWhenPendingRewardDisabled() {
        entityProperties.getPersist().setPendingReward(false);
        entityStakeCalculator.calculate();
        verifyNoInteractions(entityRepository, entityStakeRepository);
    }

    @Test
    void calculateWhenUpdated() {
        when(entityStakeRepository.updated()).thenReturn(true);
        entityStakeCalculator.calculate();
        verify(entityStakeRepository).updated();
        verify(entityRepository, never()).refreshEntityStateStart();
        verify(entityStakeRepository, never()).updateEntityStake();
    }

    @Test
    void calculateWhenExceptionThrown() {
        when(entityStakeRepository.updated()).thenThrow(new RuntimeException());
        assertThrows(RuntimeException.class, () -> entityStakeCalculator.calculate());
        verify(entityStakeRepository).updated();
        verify(entityRepository, never()).refreshEntityStateStart();
        verify(entityStakeRepository, never()).updateEntityStake();

        // calculate again
        reset(entityStakeRepository);
        var inorder = inOrder(entityRepository, entityStakeRepository);
        when(entityStakeRepository.updated()).thenReturn(false);
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated();
        inorder.verify(entityRepository).refreshEntityStateStart();
        inorder.verify(entityStakeRepository).updateEntityStake();
        inorder.verifyNoMoreInteractions();
    }

    @Test
    void concurrentCalculate() {
        // given
        var pool = Executors.newFixedThreadPool(2);
        var semaphore = new Semaphore(0);
        when(entityStakeRepository.updated()).thenAnswer(invocation -> {
            semaphore.acquire();
            return false;
        });

        // when
        var task1 = pool.submit(() -> entityStakeCalculator.calculate());
        var task2 = pool.submit(() -> entityStakeCalculator.calculate());

        // then
        // verify that only one task is done
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.TWO_SECONDS)
                .until(() -> (task1.isDone() || task2.isDone()) && (task1.isDone() != task2.isDone()));
        // unblock the remaining task
        semaphore.release();

        // verify that both tasks are done
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.TWO_SECONDS)
                .until(() -> task1.isDone() && task2.isDone());
        var inorder = inOrder(entityRepository, entityStakeRepository);
        inorder.verify(entityStakeRepository).updated();
        inorder.verify(entityRepository).refreshEntityStateStart();
        inorder.verify(entityStakeRepository).updateEntityStake();
        inorder.verifyNoMoreInteractions();
        pool.shutdown();
    }
}
