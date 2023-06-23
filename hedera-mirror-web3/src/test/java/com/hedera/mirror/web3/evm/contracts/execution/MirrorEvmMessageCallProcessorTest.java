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

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.FAILURE_DURING_LAZY_ACCOUNT_CREATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.TraceProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MirrorEvmMessageCallProcessorTest {
    @Mock
    private AbstractAutoCreationLogic autoCreationLogic;

    @Mock
    private EntityAddressSequencer entityAddressSequencer;

    @Mock
    private MirrorEvmContractAliases mirrorEvmContractAliases;

    @Mock
    private EVM evm;

    @Mock
    private PrecompileContractRegistry precompiles;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private TraceProperties traceProperties;

    @Mock
    private HederaEvmStackedWorldStateUpdater updater;

    @Mock
    private Store store;

    @Mock
    private OperationTracer operationTracer;

    @SuppressWarnings("unchecked")
    private final Map<String, PrecompiledContract> hederaPrecompileList = Collections.EMPTY_MAP;

    private MirrorEvmMessageCallProcessor subject;

    @BeforeEach
    void setUp() {
        subject = new MirrorEvmMessageCallProcessor(
                autoCreationLogic,
                entityAddressSequencer,
                mirrorEvmContractAliases,
                evm,
                precompiles,
                hederaPrecompileList);

        when(messageFrame.getWorldUpdater()).thenReturn(updater);
        when(updater.getStore()).thenReturn(store);
        when(messageFrame.getRecipientAddress())
                .thenReturn(Address.fromHexString("0x00a94f5374fce5edbc8e2a8697c15331677e6ebf"));
        when(messageFrame.getBlockValues()).thenReturn(new HederaBlockValues(0L, 0L, Instant.EPOCH));
    }

    @Test
    void executeLazyCreateFailsWithHaltReason() {
        when(autoCreationLogic.create(any(), any(), any(), any(), any())).thenReturn(Pair.of(NOT_SUPPORTED, 0L));

        subject.executeLazyCreate(messageFrame, operationTracer);

        verify(messageFrame, times(1)).setState(EXCEPTIONAL_HALT);
        verify(messageFrame, times(1)).decrementRemainingGas(0L);
        verify(operationTracer, times(1))
                .traceAccountCreationResult(messageFrame, Optional.of(FAILURE_DURING_LAZY_ACCOUNT_CREATE));
    }

    @Test
    void executeLazyCreateFailsWithInsuffiientGas() {
        when(autoCreationLogic.create(any(), any(), any(), any(), any())).thenReturn(Pair.of(OK, 1000L));
        when(messageFrame.getRemainingGas()).thenReturn(0L);
        when(messageFrame.getGasPrice()).thenReturn(Wei.ONE);
        subject.executeLazyCreate(messageFrame, operationTracer);

        verify(messageFrame, times(1)).setState(EXCEPTIONAL_HALT);
        verify(messageFrame, times(1)).decrementRemainingGas(0L);
        verify(operationTracer, times(1))
                .traceAccountCreationResult(messageFrame, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }
}
