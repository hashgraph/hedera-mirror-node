/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.web3.service.CallServiceParametersBuilder.buildFromContractCallRequest;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.viewmodel.ContractCallRequest;
import com.hedera.mirror.web3.viewmodel.ContractCallResponse;
import io.github.bucket4j.Bucket;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@RestController
class ContractController {
    private final ContractCallService contractCallService;
    private final Bucket rateLimitBucket;
    private final Bucket gasLimitBucket;
    private final MirrorNodeEvmProperties evmProperties;

    @CrossOrigin(origins = "*")
    @PostMapping(value = "/call")
    ContractCallResponse call(@RequestBody @Valid ContractCallRequest request) {

        if (!rateLimitBucket.tryConsume(1) || !gasLimitBucket.tryConsume(request.getGas())) {
            throw new RateLimitException("Rate limit exceeded.");
        }

        try {
            validateContractData(request);
            validateContractMaxGasLimit(request);

            final var params = buildFromContractCallRequest(request);
            final var result = contractCallService.processCall(params);
            return new ContractCallResponse(result);
        } catch (InvalidParametersException e) {
            // The validation failed but no processing was made - restore the consumed gas back to the bucket.
            gasLimitBucket.addTokens(request.getGas());
            throw e;
        }
    }

    /*
     * Contract data is represented as hexadecimal digits defined as characters in
     * a String. So, it takes two characters to represent one byte, and the configured max
     * data size in bytes is doubled for validation of the data length within the request object.
     */
    private void validateContractData(final ContractCallRequest request) {
        var data = request.getData();
        if (data != null
                && !evmProperties.getDataValidatorPattern().matcher(data).find()) {
            throw new InvalidParametersException(
                    "data field of size %d contains invalid hexadecimal characters or exceeds %d characters"
                            .formatted(
                                    data.length(),
                                    evmProperties.getMaxDataSize().toBytes() * 2L));
        }
    }

    private void validateContractMaxGasLimit(ContractCallRequest request) {
        if (request.getGas() > evmProperties.getMaxGasLimit()) {
            throw new InvalidParametersException(
                    "gas field must be less than or equal to %d".formatted(evmProperties.getMaxGasLimit()));
        }
    }
}
