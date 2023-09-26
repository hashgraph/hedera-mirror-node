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

import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.store.CachingStateFrame;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import org.hyperledger.besu.datatypes.Address;

@Named
public class ThreadLocalHolder {
    public static final ThreadLocal<ContractCallContext> contractCallContextWrapper =
            ThreadLocal.withInitial(() -> null);

    private ThreadLocalHolder() {}

    /** Chop the stack back to its base. This keeps the most-upstream-layer which connects to the database, and the
     * `ROCachingStateFrame` on top of it.  Therefore, everything already read from the database is still present,
     * unchanged, in the stacked cache.  (Usage case is the multiple calls to `eth_estimateGas` in order to "binary
     * search" to the closest gas approximation for a given contract call: The _first_ call is the only one that actually
     * hits the database (via the database accessors), all subsequent executions will fetch the same values
     * (required!) from the RO-cache without touching the database again - if you cut back the stack between executions
     * using this method.)
     */
    public static void resetToBase() {
        ContractCallContext contractCallContext = contractCallContextWrapper.get();
        contractCallContext.stack = contractCallContext.stackBase;
    }

    public static void cleanThread() {
        resetState();
        ContractCallContext contractCallContext = contractCallContextWrapper.get();
        contractCallContext.stack = null;
        contractCallContext.stackBase = null;
    }

    public static void resetState() {
        resetToBase();
        ContractCallContext contractCallContext = contractCallContextWrapper.get();
        contractCallContext.isCreate = false;
        contractCallContext.isEstimate = false;
        contractCallContext.aliases.clear();
        contractCallContext.pendingAliases.clear();
        contractCallContext.pendingRemovals.clear();
        contractCallContext.blockTimestamp = ContractCallContext.UNSET_TIMESTAMP;
    }

    public static void startThread(final StackedStateFrames stackedStateFrames) {
        ContractCallContext contractCallContext = new ContractCallContext();
        contractCallContextWrapper.set(contractCallContext);
        if (contractCallContext.stackBase == null) {
            contractCallContext.stackBase = stackedStateFrames.getEmptyStackBase();
        }

        contractCallContext.stack = contractCallContext.stackBase;
    }

    public static void initContractCallContext() {
        contractCallContextWrapper.set(new ContractCallContext());
    }

    public static void setStack(CachingStateFrame<Object> stack) {
        ContractCallContext contractCallContext = contractCallContextWrapper.get();
        contractCallContext.stack = stack;
    }

    public static CachingStateFrame<Object> getStack() {
        return contractCallContextWrapper.get().stack;
    }

    public static void setStackBase(CachingStateFrame<Object> stackBase) {
        ContractCallContext contractCallContext = contractCallContextWrapper.get();
        contractCallContext.stackBase = stackBase;
    }

    public static CachingStateFrame<Object> getStackBase() {
        return contractCallContextWrapper.get().stackBase;
    }

    public static boolean isCreate() {
        return contractCallContextWrapper.get().isCreate;
    }

    public static void setIsCreate(boolean isCreate) {
        ContractCallContext contractCallContext = contractCallContextWrapper.get();
        contractCallContext.isCreate = isCreate;
    }

    public static boolean isEstimate() {
        return contractCallContextWrapper.get().isEstimate;
    }

    public static void setIsEstimate(boolean isEstimate) {
        ContractCallContext contractCallContext = contractCallContextWrapper.get();
        contractCallContext.isEstimate = isEstimate;
    }

    public static void setMirrorEvmTxProcessor(MirrorEvmTxProcessor mirrorEvmTxProcessor) {
        ContractCallContext contractCallContext = contractCallContextWrapper.get();
        contractCallContext.mirrorEvmTxProcessor = mirrorEvmTxProcessor;
    }

    public static MirrorEvmTxProcessor getMirrorEvmTxProcessor() {
        return contractCallContextWrapper.get().mirrorEvmTxProcessor;
    }

    public static Map<Address, Address> getAliases() {
        return contractCallContextWrapper.get().aliases;
    }

    public static Map<Address, Address> getPendingAliases() {
        return contractCallContextWrapper.get().pendingAliases;
    }

    public static Set<Address> getPendingRemovals() {
        return contractCallContextWrapper.get().pendingRemovals;
    }

    private static class ContractCallContext {

        /**
         * Constant for representing an unset or disabled timestamp for filtering.
         */
        public static final long UNSET_TIMESTAMP = -1L;

        /**
         * Long value which stores the block timestamp used for filtering of historical data.
         * A value of UNSET_TIMESTAMP indicates that the timestamp is unset or disabled for filtering.
         * Any value other than UNSET_TIMESTAMP that is a valid timestamp should be considered for filtering operations.
         */
        private long blockTimestamp = UNSET_TIMESTAMP;

        /** Boolean flag which determines whether the transaction is estimate gas or not*/
        private boolean isEstimate = false;

        /** Boolean flag which determines whether we should make a contract call or contract create transaction simulation */
        private boolean isCreate = false;

        /** Map of account aliases that were committed */
        @NonNull
        private final Map<Address, Address> aliases = new HashMap<>();

        @NonNull
        /** Map of account aliases that are added by the current frame and are not yet committed */
        private final Map<Address, Address> pendingAliases = new HashMap<>();

        @NonNull
        /** Set of account aliases that are deleted by the current frame and are not yet committed */
        private final Set<Address> pendingRemovals = new HashSet<>();

        /** Current top of stack (which is all linked together) */
        private CachingStateFrame<Object> stack;

        /** Fixed "base" of stack: a R/O cache frame on top of the DB-backed cache frame */
        private CachingStateFrame<Object> stackBase;

        private MirrorEvmTxProcessor mirrorEvmTxProcessor;
    }
}
