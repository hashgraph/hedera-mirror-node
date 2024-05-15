/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.apache.tuweni.bytes.Bytes.EMPTY;

import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.ContractCallRequest;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.util.StringUtils;

/**
 * Provides abstractions for building {@link CallServiceParameters} objects.
 *
 * @author vyanev
 */
public interface CallServiceParametersBuilder {

    /**
     * @param transactionIdOrHash the {@link TransactionIdOrHashParameter}
     * @return the {@link CallServiceParameters} for the given transaction id or hash
     */
    CallServiceParameters buildFromTransaction(@NonNull TransactionIdOrHashParameter transactionIdOrHash);

    /**
     * @param request the {@link ContractCallRequest}
     * @return the {@link CallServiceParameters} for the given contract call request
     */
    static CallServiceParameters buildFromContractCallRequest(@NonNull ContractCallRequest request) {
        // In case of an empty "to" field, we set a default value of the zero address
        // to avoid any potential NullPointerExceptions throughout the process.
        final Address sender = Optional.ofNullable(request.getFrom())
                .filter(StringUtils::hasText)
                .map(Address::fromHexString)
                .orElse(Address.ZERO);

        final Address receiver = Optional.ofNullable(request.getTo())
                .filter(StringUtils::hasText)
                .map(Address::fromHexString)
                .orElse(Address.ZERO);

        final Bytes callData;
        try {
            callData = request.getData() != null ? Bytes.fromHexString(request.getData()) : EMPTY;
        } catch (Exception e) {
            throw new InvalidParametersException(
                    "data field '%s' contains invalid odd length characters".formatted(request.getData()));
        }

        return CallServiceParameters.builder()
                .sender(new HederaEvmAccount(sender))
                .receiver(receiver)
                .callData(callData)
                .gas(request.getGas())
                .value(request.getValue())
                .isStatic(false)
                .callType(request.isEstimate() ? ETH_ESTIMATE_GAS : ETH_CALL)
                .isEstimate(request.isEstimate())
                .block(request.getBlock())
                .build();
    }
}
