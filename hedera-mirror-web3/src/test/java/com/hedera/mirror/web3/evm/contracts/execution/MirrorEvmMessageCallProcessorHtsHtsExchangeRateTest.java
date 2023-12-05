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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.config.PrecompilesHolderHtsErcExchangeRate;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class MirrorEvmMessageCallProcessorHtsHtsExchangeRateTest extends MirrorEvmMessageCallProcessorBaseTest {

    @Mock
    private PrecompilesHolderHtsErcExchangeRate precompilesHolderHtsErcExchangeRate;

    private MirrorEvmMessageCallProcessorHtsErcExchangeRate subject;

    @BeforeEach
    void setUp() {
        given(precompilesHolderHtsErcExchangeRate.getHederaPrecompiles()).willReturn(hederaPrecompileList);
        subject = new MirrorEvmMessageCallProcessorHtsErcExchangeRate(
                autoCreationLogic,
                entityAddressSequencer,
                evm,
                precompiles,
                precompilesHolderHtsErcExchangeRate,
                gasCalculatorHederaV22);

        when(messageFrame.getWorldUpdater()).thenReturn(updater);
        when(updater.getStore()).thenReturn(store);
        when(messageFrame.getRecipientAddress())
                .thenReturn(Address.fromHexString("0x00a94f5374fce5edbc8e2a8697c15331677e6ebf"));
        when(messageFrame.getBlockValues()).thenReturn(new HederaBlockValues(0L, 0L, Instant.EPOCH));
    }

    @Test
    void executeLazyCreateFailsWithHaltReason() {
        when(autoCreationLogic.create(any(), any(), any(), any())).thenReturn(Pair.of(NOT_SUPPORTED, 0L));

        subject.executeLazyCreate(messageFrame, operationTracer);

        verify(messageFrame, times(1)).setState(EXCEPTIONAL_HALT);
        verify(messageFrame, times(1)).decrementRemainingGas(0L);
        verify(operationTracer, times(1))
                .traceAccountCreationResult(messageFrame, Optional.of(FAILURE_DURING_LAZY_ACCOUNT_CREATE));
    }

    @Test
    void executeLazyCreateFailsWithInsuffiientGas() {
        when(autoCreationLogic.create(any(), any(), any(), any())).thenReturn(Pair.of(OK, 1000L));
        when(messageFrame.getRemainingGas()).thenReturn(0L);
        when(messageFrame.getGasPrice()).thenReturn(Wei.ONE);
        subject.executeLazyCreate(messageFrame, operationTracer);

        verify(messageFrame, times(1)).setState(EXCEPTIONAL_HALT);
        verify(messageFrame, times(1)).decrementRemainingGas(0L);
        verify(operationTracer, times(1))
                .traceAccountCreationResult(messageFrame, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }
}
