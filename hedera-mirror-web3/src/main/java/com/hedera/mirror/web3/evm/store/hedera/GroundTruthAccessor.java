/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.store.hedera;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/** Interface for a database accessor to get some domain type V with primary key K from the database */
public interface GroundTruthAccessor<K, V> {
    @NonNull
    Class<K> getKClass();

    @NonNull
    Class<V> getVClass();

    // Given address return an account record from the DB
    @NonNull
    Optional<V> get(@NonNull final K key);
}
