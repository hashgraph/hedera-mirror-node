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

package com.hedera.mirror.web3.evm.contracts.execution;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.MirrorOperationTracer;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import jakarta.inject.Named;
import java.time.Instant;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@Named
@SuppressWarnings("java:S107")
public class MirrorEvmTxProcessorFacadeImpl implements MirrorEvmTxProcessorFacade {

    private final StoreImpl store;
    private final MirrorOperationTracer mirrorOperationTracer;
    private final MirrorEvmTxProcessor processor;

    @SuppressWarnings("java:S107")
    public MirrorEvmTxProcessorFacadeImpl(
            final StoreImpl store,
            final MirrorOperationTracer mirrorOperationTracer,
            final MirrorEvmTxProcessor processor) {
        this.store = store;
        this.mirrorOperationTracer = mirrorOperationTracer;
        this.processor = processor;
    }

    @Override
    public HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData,
            final Instant consensusTimestamp,
            final boolean isStatic) {
        processor.setOperationTracer(mirrorOperationTracer);
        processor.setIsCreate(Address.ZERO.equals(receiver));

        final var result =
                processor.execute(sender, receiver, providedGasLimit, value, callData, consensusTimestamp, isStatic);

        // clean threads
        store.cleanThread();
        MirrorEvmTxProcessor.cleanThread();
        MirrorEvmContractAliases.cleanThread();

        return result;
    }
}
