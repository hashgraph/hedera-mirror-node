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

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.evm.store.CachingStateFrame;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import java.util.EmptyStackException;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

@Getter
public class ContractCallContext implements AutoCloseable {

    public static final String CONTEXT_NAME = "ContractCallContext";
    private static final ThreadLocal<ContractCallContext> THREAD_LOCAL = ThreadLocal.withInitial(() -> null);

    /**
     * Record file which stores the block timestamp and other historical block details used for filtering of historical data.
     */
    @Setter
    private RecordFile recordFile;

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
    public static ContractCallContext init() {
        var context = new ContractCallContext();
        THREAD_LOCAL.set(context);
        return context;
    }

    public void reset() {
        recordFile = null;
        stack = stackBase;
    }

    @Override
    public void close() {
        THREAD_LOCAL.remove();
    }

    public int getStackHeight() {
        return stack.height() - stackBase.height();
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

    /**
     * Chop the stack back to its base. This keeps the most-upstream-layer which connects to the database, and the
     * `ROCachingStateFrame` on top of it.  Therefore, everything already read from the database is still present,
     * unchanged, in the stacked cache.  (Usage case is the multiple calls to `eth_estimateGas` in order to "binary
     * search" to the closest gas approximation for a given contract call: The _first_ call is the only one that
     * actually hits the database (via the database accessors), all subsequent executions will fetch the same values
     * (required!) from the RO-cache without touching the database again - if you cut back the stack between executions
     * using this method.)
     */
    public void initializeStackFrames(final StackedStateFrames stackedStateFrames) {
        if (stackedStateFrames != null) {
            final var timestamp = Optional.ofNullable(recordFile).map(RecordFile::getConsensusEnd);
            stackBase = stack = stackedStateFrames.getInitializedStackBase(timestamp);
        }
    }

    public boolean useHistorical() {
        return recordFile != null;
    }
}
