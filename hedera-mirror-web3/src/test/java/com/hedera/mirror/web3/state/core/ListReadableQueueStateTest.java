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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"unchecked"})
@ExtendWith(MockitoExtension.class)
class ListReadableQueueStateTest {

    @Mock
    private Queue<Object> backingStore;

    @Test
    void testIterateOnDataSource() {
        final var iterator = mock(Iterator.class);
        when(backingStore.iterator()).thenReturn(iterator);
        final var queueState = new ListReadableQueueState<>("KEY", backingStore);
        assertThat(queueState.iterateOnDataSource()).isEqualTo(iterator);
    }

    @Test
    void testPeekOnDataSource() {
        final var firstElem = new Object();
        when((backingStore.peek())).thenReturn(firstElem);
        final var queueState = new ListReadableQueueState<>("KEY", backingStore);
        assertThat(queueState.peekOnDataSource()).isEqualTo(firstElem);
    }
}
