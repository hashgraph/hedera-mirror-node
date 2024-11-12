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

import com.swirlds.state.spi.ReadableStates;
import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

abstract class AbstractMapReadableState implements ReadableStates {

    protected final Map<String, ?> states;

    protected AbstractMapReadableState(@Nonnull final Map<String, ?> states) {
        this.states = Objects.requireNonNull(states);
    }

    @Override
    public boolean contains(@Nonnull String stateKey) {
        return states.containsKey(stateKey);
    }

    @Nonnull
    @Override
    public Set<String> stateKeys() {
        return Collections.unmodifiableSet(states.keySet());
    }
}
