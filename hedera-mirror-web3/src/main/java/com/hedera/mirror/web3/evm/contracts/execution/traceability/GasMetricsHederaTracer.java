package com.hedera.mirror.web3.evm.contracts.execution.traceability;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;

import org.apache.commons.lang3.time.StopWatch;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

public class GasMetricsHederaTracer implements HederaEvmOperationTracer {

    private long preExecutionRemainingGas = 0L;
    private long postExecutionRemainingGas = 0L;
    private long gasUsedForOperation = 0L;
    private static long accumulatedGasUsed = 0L;

    private static StopWatch watch;
    private static long startTime;

    static {
        watch = StopWatch.create();
        watch.start();
        startTime = watch.getStartTime();
    }

    @Override
    public void tracePreExecution(final MessageFrame frame) {
        preExecutionRemainingGas = frame.getRemainingGas();
    }

    @Override
    public void tracePostExecution(
            final MessageFrame currentFrame, final OperationResult operationResult) {
        watch.stop();
        long currentTime = watch.getStopTime();

        if (currentTime - startTime >= 1000) {
            accumulatedGasUsed = 0;
            startTime = currentTime;
        }

        watch.reset();
        watch.start();

        postExecutionRemainingGas = currentFrame.getRemainingGas();

        gasUsedForOperation = preExecutionRemainingGas - postExecutionRemainingGas;

        accumulatedGasUsed += gasUsedForOperation;
    }

    public long getGasUsed() {
        return accumulatedGasUsed;
    }
}

