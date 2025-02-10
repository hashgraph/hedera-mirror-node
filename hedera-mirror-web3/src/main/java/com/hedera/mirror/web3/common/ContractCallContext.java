/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.Opcode;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracer;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.store.CachingStateFrame;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
public class ContractCallContext {

    public static final String CONTEXT_NAME = "ContractCallContext";
    private static final ScopedValue<ContractCallContext> SCOPED_VALUE = ScopedValue.newInstance();

    @Setter
    private List<ContractAction> contractActions = List.of();

    /**
     * This is used to determine the contract action index of the current frame. It starts from {@code -1} because when
     * the tracer receives the initial frame, it will increment this immediately inside
     * {@link OpcodeTracer#traceContextEnter}.
     */
    @Setter
    private int contractActionIndexOfCurrentFrame = -1;

    @Setter
    private OpcodeTracerOptions opcodeTracerOptions;

    @Setter
    private List<Opcode> opcodes = new ArrayList<>();

    @Setter
    private CallServiceParameters callServiceParameters;

    /**
     * Record file which stores the block timestamp and other historical block details used for filtering of historical
     * data.
     */
    @Setter
    private RecordFile recordFile;

    /** Current top of stack (which is all linked together) */
    private CachingStateFrame<Object> stack;

    /** Fixed "base" of stack: a R/O cache frame on top of the DB-backed cache frame */
    private CachingStateFrame<Object> stackBase;

    @Getter(AccessLevel.NONE)
    private Map<String, Map<Object, Object>> readCache = new HashMap<>();

    @Getter(AccessLevel.NONE)
    private Map<String, Map<Object, Object>> modifications = new HashMap<>();

    /**
     * The timestamp used to fetch the state from the stackedStateFrames.
     */
    @Setter
    private Optional<Long> timestamp = Optional.empty();

    private ContractCallContext() {}

    public static ContractCallContext get() {
        return SCOPED_VALUE.get();
    }

    public static <T> T run(Function<ContractCallContext, T> function) {
        return ScopedValue.getWhere(SCOPED_VALUE, new ContractCallContext(), () -> function.apply(SCOPED_VALUE.get()));
    }

    public void reset() {
        stack = stackBase;
        modifications.clear();
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

    public void addOpcodes(Opcode opcode) {
        opcodes.add(opcode);
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
            final var stateTimestamp = getTimestampOrDefaultFromRecordFile();
            stackBase = stack = stackedStateFrames.getInitializedStackBase(stateTimestamp);
        }
    }

    public boolean useHistorical() {
        if (callServiceParameters != null) {
            return callServiceParameters.getBlock() != BlockType.LATEST;
        }
        return recordFile != null; // Remove recordFile comparison after mono code deletion
    }

    public void incrementContractActionsCounter() {
        this.contractActionIndexOfCurrentFrame++;
    }

    /**
     * Returns the set timestamp or the consensus end timestamp from the set record file only if we are in a historical context. If not - an empty optional is returned.
     * */
    public Optional<Long> getTimestamp() {
        if (useHistorical()) {
            return getTimestampOrDefaultFromRecordFile();
        }
        return Optional.empty();
    }

    private Optional<Long> getTimestampOrDefaultFromRecordFile() {
        return timestamp.or(() -> Optional.ofNullable(recordFile).map(RecordFile::getConsensusEnd));
    }

    public Map<Object, Object> getReadCacheState(final String stateKey) {
        return readCache.computeIfAbsent(stateKey, k -> new HashMap<>());
    }

    public Map<Object, Object> getModificationsState(final String stateKey) {
        return modifications.computeIfAbsent(stateKey, k -> new HashMap<>());
    }
}
