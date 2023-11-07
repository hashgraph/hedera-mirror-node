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

package com.hedera.mirror.web3.common;

import com.hedera.mirror.web3.evm.store.CachingStateFrame;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hyperledger.besu.datatypes.Address;

@Getter
public class ContractCallContext implements AutoCloseable {

    private static final ThreadLocal<ContractCallContext> THREAD_LOCAL = ThreadLocal.withInitial(() -> null);

    /**
     * Constant for representing an unset or disabled timestamp for filtering.
     */
    public static final long UNSET_TIMESTAMP = -1L;

    /** Map of account aliases that were committed */
    private final Map<Address, Address> aliases = new HashMap<>();

    /** Map of account aliases that are added by the current frame and are not yet committed */
    private final Map<Address, Address> pendingAliases = new HashMap<>();

    /** Set of account aliases that are deleted by the current frame and are not yet committed */
    private final Set<Address> pendingRemovals = new HashSet<>();

    /**
     * Long value which stores the block timestamp used for filtering of historical data.
     * A value of UNSET_TIMESTAMP indicates that the timestamp is unset or disabled for filtering.
     * Any value other than UNSET_TIMESTAMP that is a valid timestamp should be considered for filtering operations.
     */
    @Setter
    private long blockTimestamp = UNSET_TIMESTAMP;

    /** Boolean flag which determines whether we should make a contract call or contract init transaction simulation */
    @Setter
    private boolean create = false;

    /** Boolean flag which determines whether the transaction is estimate gas or not */
    @Setter
    private boolean estimate = false;

    /** Current top of stack (which is all linked together) */
    private CachingStateFrame<Object> stack;

    /** Fixed "base" of stack: a R/O cache frame on top of the DB-backed cache frame */
    private CachingStateFrame<Object> stackBase;

    private ContractCallContext() {}

    public static ContractCallContext get() {
        return THREAD_LOCAL.get();
    }

    /**
     * Chop the stack back to its base. This keeps the most-upstream-layer which connects to the database, and the
     * `ROCachingStateFrame` on top of it.  Therefore, everything already read from the database is still present,
     * unchanged, in the stacked cache.  (Usage case is the multiple calls to `eth_estimateGas` in order to "binary
     * search" to the closest gas approximation for a given contract call: The _first_ call is the only one that
     * actually hits the database (via the database accessors), all subsequent executions will fetch the same values
     * (required!) from the RO-cache without touching the database again - if you cut back the stack between executions
     * using this method.)
     */
    public static ContractCallContext init(final StackedStateFrames stackedStateFrames) {
        var context = new ContractCallContext();
        if (stackedStateFrames != null) {
            context.stackBase = context.stack = stackedStateFrames.getInitializedStackBase();
        }
        THREAD_LOCAL.set(context);
        return context;
    }

    public boolean containsAlias(final Address address) {
        return aliases.containsKey(address) && !pendingRemovals.contains(address)
                || pendingAliases.containsKey(address);
    }

    public void reset() {
        aliases.clear();
        create = false;
        estimate = false;
        pendingAliases.clear();
        pendingRemovals.clear();
        blockTimestamp = UNSET_TIMESTAMP;
        stack = stackBase;
    }

    @Override
    public void close() {
        THREAD_LOCAL.remove();
    }

    public int getStackHeight() {
        return stack.height() - stackBase.height();
    }

    public CachingStateFrame<Object> getStack() {
        return stack;
    }

    public void setStack(CachingStateFrame<Object> stack) {
        this.stack = stack;
        if (stackBase == null) {
            stackBase = stack;
        }
    }

    public void updateStackFromUpstream() {
        if (stack == stackBase) {
            throw new EmptyStackException();
        }
        setStack(stack.getUpstream().orElseThrow(EmptyStackException::new));
    }
}
