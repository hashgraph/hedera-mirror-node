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

package com.hedera.mirror.web3.state.utils;

import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;

public class FilteredWritableStates extends com.hedera.node.app.spi.state.FilteredWritableStates {
    /**
     * Create a new instance.
     *
     * @param delegate  The instance to delegate to
     * @param stateKeys The set of keys in {@code delegate} to expose
     */
    public FilteredWritableStates(@NonNull WritableStates delegate, @NonNull Set<String> stateKeys) {
        super(delegate, stateKeys);
    }

    @NonNull
    @Override
    public <T> WritableSingletonState<T> getSingleton(@NonNull String stateKey) {
        Objects.requireNonNull(stateKey);
        if (!contains(stateKey)) {
            return new WritableSingletonStateBase<>(stateKey, () -> null, c -> {});
        }

        return getDelegate().getSingleton(stateKey);
    }
}
