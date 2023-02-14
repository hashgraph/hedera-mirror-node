package com.hedera.mirror.web3.service;

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

import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Named;
import org.apache.tuweni.bytes.Bytes;

import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacade;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;

@Named
public class ContractCallService {
    private final MirrorEvmTxProcessorFacade mirrorEvmTxProcessorFacade;
    private final Map<CallType, Counter> gasPerSecondMetricMap;

    public ContractCallService(final MirrorEvmTxProcessorFacade mirrorEvmTxProcessorFacade, final MeterRegistry meterRegistry) {
        this.mirrorEvmTxProcessorFacade = mirrorEvmTxProcessorFacade;

        gasPerSecondMetricMap = new EnumMap<>(CallType.class);
        Arrays.stream(CallType.values()).toList().forEach(type ->
                gasPerSecondMetricMap.put(type, Counter.builder("hedera.mirror.web3.call.gas")
                .description("The amount of gas consumed by the EVM")
                .tag("type", type.toString())
                .register(meterRegistry)));
    }

    public String processCall(final CallServiceParameters body) {
        final var txnResult = doProcessCall(body);

        final var callResult = txnResult.getOutput() != null
                ? txnResult.getOutput() : Bytes.EMPTY;

        return callResult.toHexString();
    }

    private HederaEvmTransactionProcessingResult doProcessCall(final CallServiceParameters body) {
        HederaEvmTransactionProcessingResult txnResult;

        try {
            txnResult =
                    mirrorEvmTxProcessorFacade.execute(
                            body.getSender(),
                            body.getReceiver(),
                            body.getProvidedGasLimit(),
                            body.getValue(),
                            body.getCallData(),
                            body.isStatic());

            if(!txnResult.isSuccessful()) {
                body.setCallType(CallType.ERROR);
                onComplete(body, txnResult);

                throw new InvalidTransactionException(getStatusOrDefault(txnResult));
            } else {
                onComplete(body, txnResult);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvalidTransactionException(e.getMessage());
        }
        return txnResult;
    }

    private void onComplete(final CallServiceParameters body, final HederaEvmTransactionProcessingResult result) {
        final var counter = gasPerSecondMetricMap.get(body.getCallType());
        counter.increment(result.getGasUsed());
    }
}
