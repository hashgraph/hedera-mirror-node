/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"unchecked"})
@ExtendWith(MockitoExtension.class)
class ListWritableQueueStateTest {

    private Queue<Object> backingStore;

    @BeforeEach
    void setup() {
        backingStore = new ConcurrentLinkedDeque<>();
    }

    @Test
    void testAddToDatasource() {
        final var elem = new Object();
        final var queueState = new ListWritableQueueState<>("KEY", backingStore);
        queueState.addToDataSource(elem);
        assertThat(backingStore).contains(elem);
    }

    @Test
    void testRemoveFromDatasource() {
        final var elem = new Object();
        backingStore.add(elem);
        final var queueState = new ListWritableQueueState<>("KEY", backingStore);
        queueState.removeFromDataSource();
        assertThat(backingStore).isEmpty();
    }

    @Test
    void testIterateOnDataSource() {
        final var elem = new Object();
        backingStore.add(elem);
        final var iterator = backingStore.iterator();
        final var queueState = new ListWritableQueueState<>("KEY", backingStore);
        assertThat(queueState.iterateOnDataSource().next()).isEqualTo(iterator.next());
    }
}
