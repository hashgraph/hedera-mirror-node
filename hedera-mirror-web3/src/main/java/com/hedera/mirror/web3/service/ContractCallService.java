package com.hedera.mirror.web3.service;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.util.ResponseCodeUtil.getStatusOrDefault;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessorFacade;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.services.evm.contracts.execution.HederaEvmTransactionProcessingResult;

@Named
@RequiredArgsConstructor
public class ContractCallService {
    private final MirrorEvmTxProcessorFacade mirrorEvmTxProcessor;

    public String processCall(CallServiceParameters body) {
        final var processResult = doProcessCall(body);

        return processResult.getOutput().toHexString();
    }

    private HederaEvmTransactionProcessingResult doProcessCall(CallServiceParameters body) {
        final var processingResult =
                mirrorEvmTxProcessor.execute(
                        body.getSender(),
                        body.getReceiver(),
                        body.getProvidedGasLimit(),
                        body.getValue(),
                        body.getCallData(),
                        body.isStatic());

        final var status = getStatusOrDefault(processingResult);
        if (status != SUCCESS) {
            revertWith(status);
        }

        return processingResult;
    }

    public void revertWith(ResponseCodeEnum errorStatus) {
        throw new InvalidTransactionException(errorStatus);
    }
}
