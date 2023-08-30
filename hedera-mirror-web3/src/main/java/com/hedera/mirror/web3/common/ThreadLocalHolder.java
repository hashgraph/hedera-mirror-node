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

package com.hedera.mirror.web3.common;

import jakarta.inject.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import org.hyperledger.besu.datatypes.Address;

@Named
public class ThreadLocalHolder {

    @NonNull
    public static final ThreadLocal<Map<Object, Object>> original =
            ThreadLocal.withInitial(HashMap::new); // "missing" denoted by null values here

    @NonNull
    public static final ThreadLocal<Map<Object, Object>> current =
            ThreadLocal.withInitial(HashMap::new); // "deleted" denoted by null values here

    @NonNull
    public static final ThreadLocal<Boolean> isCreate = ThreadLocal.withInitial(() -> false);

    public static final ThreadLocal<Map<Address, Address>> aliases = ThreadLocal.withInitial(HashMap::new);
    public static final ThreadLocal<Map<Address, Address>> pendingAliases = ThreadLocal.withInitial(HashMap::new);
    public static final ThreadLocal<Set<Address>> pendingRemovals = ThreadLocal.withInitial(HashSet::new);

    private ThreadLocalHolder() {}

    public static void cleanThread() {
        isCreate.remove();

        original.remove();
        current.remove();

        aliases.remove();
        pendingAliases.remove();
        pendingRemovals.remove();
    }
}
