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

import com.swirlds.state.spi.ReadableQueueStateBase;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;

public class ListReadableQueueState<E> extends ReadableQueueStateBase<E> {

    /** Represents the backing storage for this state */
    private final Queue<E> backingStore;

    /**
     * Create an instance using the given Queue as the backing store. This is useful when you want to
     * pre-populate the queue, or if you want to use Mockito to mock it or cause it to throw
     * exceptions when certain keys are accessed, etc.
     *
     * @param stateKey The state key for this state
     * @param backingStore The backing store to use
     */
    public ListReadableQueueState(@Nonnull final String stateKey, @Nonnull final Queue<E> backingStore) {
        super(stateKey);
        this.backingStore = Objects.requireNonNull(backingStore);
    }

    @Nullable
    @Override
    protected E peekOnDataSource() {
        return backingStore.peek();
    }

    @Nonnull
    @Override
    protected Iterator<E> iterateOnDataSource() {
        return backingStore.iterator();
    }
}
