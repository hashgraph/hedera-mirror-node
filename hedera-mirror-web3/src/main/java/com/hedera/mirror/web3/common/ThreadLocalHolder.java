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

import com.hedera.mirror.web3.evm.store.CachingStateFrame;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import org.hyperledger.besu.datatypes.Address;

@Named
public class ThreadLocalHolder {

    /** Boolean flag which determines whether we should make a contract call or contract create transaction simulation */
    @NonNull
    public static final ThreadLocal<Boolean> isCreate = ThreadLocal.withInitial(() -> false);

    public static final ThreadLocal<Map<Address, Address>> aliases = ThreadLocal.withInitial(HashMap::new);
    public static final ThreadLocal<Map<Address, Address>> pendingAliases = ThreadLocal.withInitial(HashMap::new);
    public static final ThreadLocal<Set<Address>> pendingRemovals = ThreadLocal.withInitial(HashSet::new);
    /** Current top of stack (which is all linked together) */
    @NonNull
    public static final ThreadLocal<CachingStateFrame<Object>> stack = ThreadLocal.withInitial(() -> null);

    /** Fixed "base" of stack: a R/O cache frame on top of the DB-backed cache frame */
    @NonNull
    public static final ThreadLocal<CachingStateFrame<Object>> stackBase = ThreadLocal.withInitial(() -> null);

    private ThreadLocalHolder() {}

    public static void cleanThread() {
        stack.remove();
        stackBase.remove();
        isCreate.remove();
        aliases.remove();
        pendingAliases.remove();
        pendingRemovals.remove();
    }

    public static void cleanStackBase() {
        stackBase.remove();
    }
}
