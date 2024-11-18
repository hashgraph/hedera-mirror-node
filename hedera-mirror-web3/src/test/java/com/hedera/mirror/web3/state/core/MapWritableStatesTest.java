/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.WritableQueueStateBase;
import com.swirlds.state.spi.WritableSingletonStateBase;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapWritableStatesTest {

    private MapWritableStates states;

    @Mock
    private WritableKVStateBase<String, String> kvStateMock;

    @Mock
    private WritableSingletonStateBase<String> singletonStateMock;

    @Mock
    private WritableQueueStateBase<String> queueStateMock;

    private static final String KV_STATE_KEY = "kvState";
    private static final String SINGLETON_KEY = "singleton";
    private static final String QUEUE_KEY = "queue";

    @BeforeEach
    void setup() {
        states = new MapWritableStates(
                Map.of(KV_STATE_KEY, kvStateMock, SINGLETON_KEY, singletonStateMock, QUEUE_KEY, queueStateMock));
    }

    @Test
    void testGetState() {
        assertThat(states.get(KV_STATE_KEY)).isEqualTo(kvStateMock);
    }

    @Test
    void testGetStateNotFound() {
        assertThatThrownBy(() -> states.get("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetStateNotCorrectType() {
        assertThatThrownBy(() -> states.get(SINGLETON_KEY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetSingletonState() {
        assertThat(states.getSingleton(SINGLETON_KEY)).isEqualTo(singletonStateMock);
    }

    @Test
    void testGetSingletonStateNotFound() {
        assertThatThrownBy(() -> states.getSingleton("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetSingletonStateNotCorrectType() {
        assertThatThrownBy(() -> states.getSingleton(QUEUE_KEY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetQueueState() {
        assertThat(states.getQueue(QUEUE_KEY)).isEqualTo(queueStateMock);
    }

    @Test
    void testGetQueueStateNotFound() {
        assertThatThrownBy(() -> states.getQueue("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetQueueStateNotCorrectType() {
        assertThatThrownBy(() -> states.getQueue(KV_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testContains() {
        assertThat(states.contains(KV_STATE_KEY)).isTrue();
        assertThat(states.contains(SINGLETON_KEY)).isTrue();
        assertThat(states.contains(QUEUE_KEY)).isTrue();
        assertThat(states.contains("unknown")).isFalse();
    }

    @Test
    void testStateKeysReturnsCorrectSet() {
        assertThat(states.stateKeys()).isEqualTo(Set.of(KV_STATE_KEY, SINGLETON_KEY, QUEUE_KEY));
    }

    @Test
    void testStateKeysReturnsUnmodifiableSet() {
        Set<String> keys = states.stateKeys();
        assertThatThrownBy(() -> keys.add("newKey")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testEqualsSameInstance() {
        assertThat(states).isEqualTo(states);
    }

    @Test
    void testEqualsDifferentClass() {
        assertThat(states).isNotEqualTo("other");
    }

    @Test
    void testEqualsWithNull() {
        assertThat(states).isNotEqualTo(null);
    }

    @Test
    void testHashCode() {
        MapWritableStates other = new MapWritableStates(
                Map.of(KV_STATE_KEY, kvStateMock, SINGLETON_KEY, singletonStateMock, QUEUE_KEY, queueStateMock));
        assertThat(states).hasSameHashCodeAs(other);
    }

    @Test
    void testCommit() {
        final var state = new MapWritableStates(
                Map.of(KV_STATE_KEY, kvStateMock, SINGLETON_KEY, singletonStateMock, QUEUE_KEY, queueStateMock));
        state.commit();
        verify(kvStateMock, times(1)).commit();
        verify(singletonStateMock, times(1)).commit();
        verify(queueStateMock, times(1)).commit();
    }

    @Test
    void testCommitWithListener() {
        final Runnable onCommit = mock(Runnable.class);
        final var state = new MapWritableStates(Map.of(KV_STATE_KEY, kvStateMock), onCommit);
        state.commit();
        verify(kvStateMock, times(1)).commit();
        verify(onCommit, times(1)).run();
    }

    @Test
    void testCommitUnknownValue() {
        final var state = new MapWritableStates(Map.of("other", new Object()));
        assertThatThrownBy(state::commit).isInstanceOf(IllegalStateException.class);
    }
}
