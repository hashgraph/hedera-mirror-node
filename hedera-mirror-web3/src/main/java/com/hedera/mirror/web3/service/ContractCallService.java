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

import static com.hedera.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Named;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacade;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;

@CustomLog
@Named
public class ContractCallService {

    private final MirrorEvmTxProcessorFacade mirrorEvmTxProcessorFacade;
    private final Map<CallType, Counter> gasPerSecondMetricMap;

    public ContractCallService(MirrorEvmTxProcessorFacade mirrorEvmTxProcessorFacade, MeterRegistry meterRegistry) {
        this.mirrorEvmTxProcessorFacade = mirrorEvmTxProcessorFacade;

        final var gasPerSecondMetricEnumMap = new EnumMap<CallType, Counter>(CallType.class);
        Arrays.stream(CallType.values()).forEach(type ->
                gasPerSecondMetricEnumMap.put(type, Counter.builder("hedera.mirror.web3.call.gas")
                        .description("The amount of gas consumed by the EVM")
                        .tag("type", type.toString())
                        .register(meterRegistry)));

        gasPerSecondMetricMap = Collections.unmodifiableMap(gasPerSecondMetricEnumMap);
    }

    public String processCall(final CallServiceParameters body) {
        var stopwatch = Stopwatch.createStarted();
        final var txnResult = doProcessCall(body);

        final var callResult = txnResult.getOutput() != null
                ? txnResult.getOutput() : Bytes.EMPTY;
        final var stringResult = callResult.toHexString();
        log.debug("Processed request {} in {}: {}", body, stopwatch, stringResult);
        return stringResult;
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

            if (!txnResult.isSuccessful()) {
                onComplete(CallType.ERROR, txnResult);

                var revertReason = txnResult.getRevertReason().orElse(Bytes.EMPTY);
                throw new InvalidTransactionException(getStatusOrDefault(txnResult),
                        maybeDecodeSolidityErrorStringToReadableMessage(revertReason), revertReason.toHexString());
            } else {
                onComplete(body.getCallType(), txnResult);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvalidTransactionException(e.getMessage(), StringUtils.EMPTY, StringUtils.EMPTY);
        }
        return txnResult;
    }

    private void onComplete(final CallType callType, final HederaEvmTransactionProcessingResult result) {
        final var counter = gasPerSecondMetricMap.get(callType);
        counter.increment(result.getGasUsed());
    }
}
