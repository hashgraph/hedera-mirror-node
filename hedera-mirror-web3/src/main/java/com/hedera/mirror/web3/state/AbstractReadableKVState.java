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

package com.hedera.mirror.web3.state;

import com.swirlds.state.spi.ReadableKVStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;

public abstract class AbstractReadableKVState<K, V> extends ReadableKVStateBase<K, V> {

    protected AbstractReadableKVState(@NonNull String stateKey) {
        super(stateKey);
    }

    @Nullable
    @Override
    public V get(@NonNull K key) {
        return readFromDataSource(key);
    }

    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }
}
