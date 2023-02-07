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

import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;

import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacade;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;

@Named
@RequiredArgsConstructor
public class ContractCallService {
    private final MirrorEvmTxProcessorFacade mirrorEvmTxProcessorFacade;

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

            if (!txnResult.isSuccessful()) {
                throw new InvalidTransactionException(getStatusOrDefault(txnResult));
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvalidTransactionException(e.getMessage());
        }
        return txnResult;
    }
}
